package app;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
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
 * <p>La modalità predefinita è {@code JSON_TIME}: il manager richiede lo snapshot
 * coerente con il proprio tempo logico. {@code JSON_SEQUENCE} resta disponibile come
 * replay diagnostico ordinale, utile per consumare tutti i file in sequenza anche quando
 * la durata adattiva diverge dai timestamp salvati nei JSON.</p>
 *
 * <p>Argomenti supportati:</p>
 * <pre>
 *   AdaptiveWindowMain
 *   AdaptiveWindowMain data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated
 *   AdaptiveWindowMain data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8
 *   AdaptiveWindowMain JSON_SEQUENCE data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8
 * </pre>
 */
public final class AdaptiveWindowMain {
    private static final String DEFAULT_MODE = "JSON_TIME";
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
        TemporalWindowConfig windowConfig = TemporalWindowConfig.defaultConfig();
        CandidatePrefilter prefilter = new CandidatePrefilter(
                CandidatePrefilterConfig.defaultConfig()
        );

        SystemStateSource rawSource = buildSource(
                runArguments.mode(),
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
                .print(runArguments.mode(), runArguments.folderPath(), result, filteredSource);
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

    private record RunArguments(String mode, String folderPath, Integer maxSteps) {
        private static RunArguments parse(String[] args) {
            if (args.length == 0) {
                return new RunArguments(
                        DEFAULT_MODE,
                        SnapshotPaths.TEMPORAL_DEFAULT_SCENARIO_FOLDER,
                        null
                );
            }
            if (args.length == 1) {
                return new RunArguments(DEFAULT_MODE, args[0], null);
            }
            if (args.length == 2) {
                return new RunArguments(DEFAULT_MODE, args[0], Integer.parseInt(args[1]));
            }
            if (args.length == 3) {
                return new RunArguments(args[0], args[1], Integer.parseInt(args[2]));
            }
            throw new IllegalArgumentException(
                    "Usage: AdaptiveWindowMain [folder] [maxSteps] "
                            + "or AdaptiveWindowMain [mode] [folder] [maxSteps]"
            );
        }
    }
}
