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

/** Entry point della finestra adattiva gateway-aware. */
public final class AdaptiveWindowMain {
    private static final String DEFAULT_SOURCE_MODE = "JSON_TIME";
    private static final TemporalRuntimeProfile DEFAULT_RUNTIME_PROFILE = TemporalRuntimeProfile.OBSERVED_RUNTIME;
    private static final double START_TIME_SECONDS = 0.0;
    private AdaptiveWindowMain() { }

    public static void main(String[] args) throws Exception {
        RunArguments run = RunArguments.parse(args);
        List<SystemSnapshot> snapshots = new JsonSnapshotFolderLoader().load(run.folderPath());
        int maxSteps = run.maxSteps() == null ? snapshots.size() : run.maxSteps();
        MaGaConfig maGaConfig = MaGaConfig.defaultConfig(GaParameterScalingMode.ADAPTIVE);
        TemporalWindowConfig windowConfig = run.runtimeProfile().createWindowConfig();
        CandidatePrefilter prefilter = new CandidatePrefilter(
                CandidatePrefilterConfig.defaultConfig(),
                maGaConfig.getMobilityConfig()
        );
        SystemStateSource raw = buildSource(run.sourceMode(), run.folderPath(), snapshots);
        FilteringSystemStateSource source = new FilteringSystemStateSource(raw, prefilter);
        SystemSnapshot firstFiltered = prefilter.filter(snapshots.get(0)).getFilteredSnapshot();
        int targetPopulationSize = maGaConfig.resolveGeneticAlgorithmConfig(firstFiltered).getPopulationSize();
        CoverageReferenceCalculator coverageReference = new CoverageReferenceCalculator(maGaConfig.getMobilityConfig());
        TemporalWindowBoundsCalculator bounds = new TemporalWindowBoundsCalculator(windowConfig, coverageReference);
        AdaptiveWindowController controller = new AdaptiveWindowController(windowConfig, bounds);
        TemporalWindowManager manager = new TemporalWindowManager(
                windowConfig,
                new MaGaOptimizer(maGaConfig),
                new DynamicityEvaluator(windowConfig),
                new PopulationAdapter(windowConfig, maGaConfig, new Random(maGaConfig.getGeneticAlgorithmConfig().getRandomSeed())),
                new PopulationReuseDecisionPolicy(windowConfig),
                controller,
                StaticCriticalEventDetector.empty(),
                source,
                targetPopulationSize
        );
        TemporalWindowResult result = manager.run(START_TIME_SECONDS, maxSteps);
        new AdaptiveWindowReportPrinter(maGaConfig).print(run.sourceMode(), run.runtimeProfile(), run.folderPath(), result, source);
    }

    private static SystemStateSource buildSource(String mode, String folder, List<SystemSnapshot> snapshots) throws Exception {
        String normalized = SystemStateSourceFactory.normalizeMode(mode);
        if ("JSON_SEQUENCE".equals(normalized) || "JSON_SEQUENTIAL".equals(normalized) || "SEQUENTIAL".equals(normalized)) {
            return new SequentialSnapshotReplaySource(snapshots, "sequential JSON replay from " + folder);
        }
        if ("JSON_TIME".equals(normalized) || "JSON_TIME_INDEXED".equals(normalized) || "TIME_INDEXED".equals(normalized)) {
            return SystemStateSourceFactory.fromJsonFolder(mode, folder);
        }
        if ("MOSAIC".equals(normalized) || "MOSAIC_LIVE".equals(normalized)) {
            throw new UnsupportedOperationException("MOSAIC mode requires a MosaicSnapshotBridge implementation.");
        }
        throw new IllegalArgumentException("Unsupported source mode: " + mode);
    }

    private static boolean isSupportedSourceMode(String value) {
        String normalized = SystemStateSourceFactory.normalizeMode(value);
        return "JSON_SEQUENCE".equals(normalized) || "JSON_SEQUENTIAL".equals(normalized) || "SEQUENTIAL".equals(normalized)
                || "JSON_TIME".equals(normalized) || "JSON_TIME_INDEXED".equals(normalized) || "TIME_INDEXED".equals(normalized)
                || "MOSAIC".equals(normalized) || "MOSAIC_LIVE".equals(normalized);
    }

    private record RunArguments(String sourceMode, TemporalRuntimeProfile runtimeProfile, String folderPath, Integer maxSteps) {
        private static RunArguments parse(String[] args) {
            String sourceMode = DEFAULT_SOURCE_MODE; TemporalRuntimeProfile runtimeProfile = DEFAULT_RUNTIME_PROFILE;
            String folder = SnapshotPaths.TEMPORAL_DEFAULT_SCENARIO_FOLDER; Integer maxSteps = null; int index = 0;
            if (index < args.length && isSupportedSourceMode(args[index])) { sourceMode = args[index++]; }
            if (index < args.length && TemporalRuntimeProfile.isSupported(args[index])) { runtimeProfile = TemporalRuntimeProfile.parse(args[index++]); }
            if (index < args.length) { folder = args[index++]; }
            if (index < args.length) { maxSteps = Integer.parseInt(args[index++]); }
            if (index != args.length) { throw new IllegalArgumentException("Usage: AdaptiveWindowMain [sourceMode] [runtimeProfile] [folder] [maxSteps]"); }
            return new RunArguments(sourceMode, runtimeProfile, folder, maxSteps);
        }
    }
}
