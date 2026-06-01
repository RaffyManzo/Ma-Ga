package app;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import config.window.TemporalRuntimeProfile;
import config.window.TemporalWindowConfig;
import ga.core.MaGaOptimizer;
import io.reporting.AdaptiveWindowReportPrinter;
import io.snapshot.JsonSnapshotFolderLoader;
import io.snapshot.SnapshotPaths;
import model.snapshot.SystemSnapshot;
import window.core.TemporalWindowManager;
import window.dynamicity.DynamicityEvaluator;
import window.event.StaticCriticalEventDetector;
import window.population.PopulationAdapter;
import window.population.PopulationReuseDecisionPolicy;
import window.prefilter.CandidatePrefilter;
import window.prefilter.CandidatePrefilterConfig;
import window.source.FilteringSystemStateSource;
import window.source.SequentialSnapshotReplaySource;
import window.source.SystemStateSource;
import window.source.SystemStateSourceFactory;
import window.state.TemporalWindowResult;
import window.timing.AdaptiveWindowController;
import window.timing.CoverageReferenceCalculator;
import window.timing.TemporalWindowBoundsCalculator;

import java.util.List;
import java.util.Random;

/**
 * Esegue il ciclo temporale completo del MA-GA su una cartella di snapshot consecutivi.
 *
 * <p>Questo è l'unico entry point applicativo per la finestra adattiva. Compone sorgente
 * dati, prefilter, gestore temporale, riuso della popolazione, bounds adattivi e report.</p>
 *
 * <p>La modalità sorgente predefinita è {@code JSON_TIME}: il manager richiede lo
 * snapshot coerente con il proprio tempo logico. {@code JSON_SEQUENCE} resta disponibile
 * come replay diagnostico ordinale.</p>
 *
 * <p>Il profilo temporale predefinito è {@code OBSERVED_RUNTIME}: dopo la prima finestra,
 * il bound minimo usa il runtime del GA osservato nella finestra precedente. Il profilo
 * {@code CONFIGURED_RUNTIME} resta disponibile per replay algoritmici astratti e
 * riproducibili.</p>
 *
 * <p>Argomenti supportati:</p>
 * <pre>
 * AdaptiveWindowMain
 * AdaptiveWindowMain data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated
 * AdaptiveWindowMain data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8
 * AdaptiveWindowMain JSON_TIME OBSERVED_RUNTIME data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8
 * AdaptiveWindowMain JSON_SEQUENCE CONFIGURED_RUNTIME data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8
 * </pre>
 */
public final class AdaptiveWindowMain {
    private static final String DEFAULT_SOURCE_MODE = "JSON_TIME";
    private static final TemporalRuntimeProfile DEFAULT_RUNTIME_PROFILE =
            TemporalRuntimeProfile.OBSERVED_RUNTIME;
    private static final double START_TIME_SECONDS = 0.0;

    private AdaptiveWindowMain() {
    }

    public static void main(String[] args) throws Exception {
        RunArguments runArguments = RunArguments.parse(args);

        List<SystemSnapshot> snapshots = new JsonSnapshotFolderLoader()
                .load(runArguments.folderPath());
        int maxSteps = runArguments.maxSteps() == null
                ? snapshots.size()
                : runArguments.maxSteps();

        MaGaConfig maGaConfig = MaGaConfig.defaultConfig(GaParameterScalingMode.ADAPTIVE);
        TemporalWindowConfig windowConfig = runArguments.runtimeProfile()
                .createWindowConfig();
        CandidatePrefilter prefilter = new CandidatePrefilter(
                CandidatePrefilterConfig.defaultConfig()
        );

        SystemStateSource rawSource = buildSource(
                runArguments.sourceMode(),
                runArguments.folderPath(),
                snapshots
        );
        FilteringSystemStateSource filteredSource = new FilteringSystemStateSource(
                rawSource,
                prefilter
        );

        SystemSnapshot firstFilteredSnapshot = prefilter
                .filter(snapshots.get(0))
                .getFilteredSnapshot();
        int targetPopulationSize = maGaConfig
                .resolveGeneticAlgorithmConfig(firstFilteredSnapshot)
                .getPopulationSize();

        CoverageReferenceCalculator coverageReferenceCalculator =
                new CoverageReferenceCalculator(maGaConfig.getMobilityConfig());
        TemporalWindowBoundsCalculator boundsCalculator =
                new TemporalWindowBoundsCalculator(windowConfig, coverageReferenceCalculator);
        AdaptiveWindowController adaptiveWindowController =
                new AdaptiveWindowController(windowConfig, boundsCalculator);

        TemporalWindowManager manager = new TemporalWindowManager(
                windowConfig,
                new MaGaOptimizer(maGaConfig),
                new DynamicityEvaluator(windowConfig),
                new PopulationAdapter(
                        windowConfig,
                        maGaConfig,
                        new Random(maGaConfig.getGeneticAlgorithmConfig().getRandomSeed())
                ),
                new PopulationReuseDecisionPolicy(windowConfig),
                adaptiveWindowController,
                StaticCriticalEventDetector.empty(),
                filteredSource,
                targetPopulationSize
        );

        TemporalWindowResult result = manager.run(START_TIME_SECONDS, maxSteps);
        new AdaptiveWindowReportPrinter(maGaConfig)
                .print(
                        runArguments.sourceMode(),
                        runArguments.runtimeProfile(),
                        runArguments.folderPath(),
                        result,
                        filteredSource
                );
    }

    private static SystemStateSource buildSource(
            String mode,
            String folderPath,
            List<SystemSnapshot> snapshots
    ) throws Exception {
        String normalizedMode = SystemStateSourceFactory.normalizeMode(mode);

        if ("JSON_SEQUENCE".equals(normalizedMode)
                || "JSON_SEQUENTIAL".equals(normalizedMode)
                || "SEQUENTIAL".equals(normalizedMode)) {
            return new SequentialSnapshotReplaySource(
                    snapshots,
                    "sequential JSON replay from " + folderPath
            );
        }

        if ("JSON_TIME".equals(normalizedMode)
                || "JSON_TIME_INDEXED".equals(normalizedMode)
                || "TIME_INDEXED".equals(normalizedMode)) {
            return SystemStateSourceFactory.fromJsonFolder(mode, folderPath);
        }

        if ("MOSAIC".equals(normalizedMode) || "MOSAIC_LIVE".equals(normalizedMode)) {
            throw new UnsupportedOperationException(
                    "MOSAIC mode requires a MosaicSnapshotBridge implementation. "
                            + "Use MosaicSystemStateSource when the bridge is available."
            );
        }

        throw new IllegalArgumentException("Unsupported source mode: " + mode);
    }

    private static boolean isSupportedSourceMode(String value) {
        String normalized = SystemStateSourceFactory.normalizeMode(value);
        return "JSON_SEQUENCE".equals(normalized)
                || "JSON_SEQUENTIAL".equals(normalized)
                || "SEQUENTIAL".equals(normalized)
                || "JSON_TIME".equals(normalized)
                || "JSON_TIME_INDEXED".equals(normalized)
                || "TIME_INDEXED".equals(normalized)
                || "MOSAIC".equals(normalized)
                || "MOSAIC_LIVE".equals(normalized);
    }

    private record RunArguments(
            String sourceMode,
            TemporalRuntimeProfile runtimeProfile,
            String folderPath,
            Integer maxSteps
    ) {
        private static RunArguments parse(String[] args) {
            String sourceMode = DEFAULT_SOURCE_MODE;
            TemporalRuntimeProfile runtimeProfile = DEFAULT_RUNTIME_PROFILE;
            String folderPath = SnapshotPaths.TEMPORAL_DEFAULT_SCENARIO_FOLDER;
            Integer maxSteps = null;

            int index = 0;
            if (index < args.length && isSupportedSourceMode(args[index])) {
                sourceMode = args[index++];
            }
            if (index < args.length && TemporalRuntimeProfile.isSupported(args[index])) {
                runtimeProfile = TemporalRuntimeProfile.parse(args[index++]);
            }
            if (index < args.length) {
                folderPath = args[index++];
            }
            if (index < args.length) {
                maxSteps = Integer.parseInt(args[index++]);
            }
            if (index != args.length) {
                throw new IllegalArgumentException(usage());
            }

            return new RunArguments(sourceMode, runtimeProfile, folderPath, maxSteps);
        }

        private static String usage() {
            return "Usage: AdaptiveWindowMain [sourceMode] [runtimeProfile] [folder] [maxSteps]. "
                    + "sourceMode: JSON_TIME or JSON_SEQUENCE. "
                    + "runtimeProfile: OBSERVED_RUNTIME or CONFIGURED_RUNTIME.";
        }
    }
}
