package app;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import config.window.TemporalWindowConfig;
import ga.core.MaGaOptimizer;
import io.reporting.AdaptiveWindowDiagnosticPrinter;
import io.reporting.CandidateFilteringPrinter;
import io.reporting.DeepTemporalWindowDiagnosticPrinter;
import io.reporting.SystemStateSourceDiagnosticPrinter;
import io.reporting.TemporalTimingDiagnosticPrinter;
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
import window.source.JsonSnapshotFolderLoader;
import window.source.SequentialSnapshotReplaySource;
import window.source.SystemStateSource;
import window.source.SystemStateSourceFactory;
import window.timing.AdaptiveWindowController;
import window.timing.CoverageReferenceCalculator;
import window.timing.TemporalWindowBoundsCalculator;
import window.state.TemporalWindowResult;

import java.util.List;
import java.util.Random;

/**
 * Main dedicato alla finestra adattiva con sorgente dati astratta.
 *
 * <p>Modalità supportate per ora:</p>
 *
 * <pre>
 * JSON_SEQUENCE  data/snapshots/window/stress/realistic_scenarios/urban_realistic_dynamic_calibrated  8
 * JSON_TIME      data/snapshots/window/stress/realistic_scenarios/urban_realistic_dynamic_calibrated  8
 * </pre>
 *
 * <p>JSON_SEQUENCE è la modalità consigliata per i test offline. Legge tutti
 * gli snapshot in ordine e calcola comunque la decisione della finestra
 * adattiva. JSON_TIME usa invece il tempo richiesto dal manager e può saltare
 * snapshot se la durata adattiva non coincide con i tempi dei file.</p>
 */
public final class AdaptiveWindowSourceMain {

    private static final String DEFAULT_MODE = "JSON_SEQUENCE";
    private static final String DEFAULT_SNAPSHOT_FOLDER =
            SnapshotPaths.TEMPORAL_WINDOW_URBAN_CALIBRATED_FOLDER;
    private static final double START_TIME_SECONDS = 0.0;

    private AdaptiveWindowSourceMain() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : DEFAULT_MODE;
        String folderPath = args.length > 1 ? args[1] : DEFAULT_SNAPSHOT_FOLDER;

        List<SystemSnapshot> snapshots = new JsonSnapshotFolderLoader().load(folderPath);
        int maxSteps = args.length > 2
                ? Integer.parseInt(args[2])
                : snapshots.size();

        MaGaConfig maGaConfig = MaGaConfig.defaultConfig(
                GaParameterScalingMode.ADAPTIVE
        );
        TemporalWindowConfig windowConfig = TemporalWindowConfig.defaultConfig();

        CandidatePrefilterConfig prefilterConfig = CandidatePrefilterConfig.defaultConfig();
        CandidatePrefilter prefilter = new CandidatePrefilter(prefilterConfig);

        SystemStateSource rawSource = buildSource(mode, folderPath, snapshots);
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
                new TemporalWindowBoundsCalculator(
                        windowConfig,
                        coverageReferenceCalculator
                );
        AdaptiveWindowController adaptiveWindowController =
                new AdaptiveWindowController(windowConfig, boundsCalculator);

        TemporalWindowManager manager = new TemporalWindowManager(
                windowConfig,
                new MaGaOptimizer(maGaConfig),
                new DynamicityEvaluator(windowConfig),
                new PopulationAdapter(
                        windowConfig,
                        new Random(
                                maGaConfig
                                        .getGeneticAlgorithmConfig()
                                        .getRandomSeed()
                        )
                ),
                new PopulationReuseDecisionPolicy(),
                adaptiveWindowController,
                StaticCriticalEventDetector.empty(),
                filteredSource,
                targetPopulationSize
        );

        TemporalWindowResult result = manager.run(
                START_TIME_SECONDS,
                maxSteps
        );

        printReport(maGaConfig, result, filteredSource);
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

        if ("MOSAIC".equals(normalizedMode)
                || "MOSAIC_LIVE".equals(normalizedMode)) {
            throw new UnsupportedOperationException(
                    "MOSAIC mode requires a MosaicSnapshotBridge implementation. "
                            + "Use MosaicSystemStateSource when the bridge is available."
            );
        }

        throw new IllegalArgumentException("Unsupported source mode: " + mode);
    }

    private static void printReport(
            MaGaConfig maGaConfig,
            TemporalWindowResult result,
            FilteringSystemStateSource filteredSource
    ) {
        DeepTemporalWindowDiagnosticPrinter diagnosticPrinter =
                new DeepTemporalWindowDiagnosticPrinter(maGaConfig);
        diagnosticPrinter.print(result);

        AdaptiveWindowDiagnosticPrinter adaptivePrinter =
                new AdaptiveWindowDiagnosticPrinter();
        adaptivePrinter.print(result);

        TemporalTimingDiagnosticPrinter timingPrinter =
                new TemporalTimingDiagnosticPrinter();
        timingPrinter.print(result);

        SystemStateSourceDiagnosticPrinter sourcePrinter =
                new SystemStateSourceDiagnosticPrinter();
        sourcePrinter.print(result);

        CandidateFilteringPrinter filteringPrinter = new CandidateFilteringPrinter();
        filteringPrinter.print(filteredSource.getFilteringResults());
    }
}
