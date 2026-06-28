package org.eclipse.mosaic.app.maga.liveruntime;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import config.window.TemporalWindowConfig;
import ga.core.GaExecutionBudget;
import ga.core.MaGaOptimizer;
import ga.core.MaGaResult;
import ga.core.StopReason;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;
import org.eclipse.mosaic.app.maga.liveruntime.reporting.LiveNativeReportingCollector;
import org.eclipse.mosaic.app.maga.liveruntime.reporting.LiveStaleStrategyWriter;
import window.dynamicity.DynamicityBreakdown;
import window.population.PopulationReuseMode;
import window.state.TemporalStepResult;
import window.state.TemporalWindowState;
import window.trigger.ReoptimizationTrigger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class V3CFreshnessBudgetHarness {
    private V3CFreshnessBudgetHarness() { }

    public static void main(String[] args) throws Exception {
        Path outputRoot = args.length > 0
                ? Path.of(args[0])
                : Files.createTempDirectory("v3c-freshness-budget-harness-");
        Files.createDirectories(outputRoot);
        testEstimatorExcludesZeroTaskAndUsesTwentySampleP95();
        testTemporalAndWallClockDomainsAreSeparated();
        testFreshnessCapIsIndependentFromTemporalMaximum();
        testDistinctStaleReasons();
        testWallClockBoundarySemantics();
        testCooperativeBestSoFar();
        testFullStaleStrategyReporting(outputRoot.resolve("stale-reporting"));
        testCanonicalMainReporting(outputRoot.resolve("canonical-reporting"));
        System.out.println("V3C_FRESHNESS_BUDGET_HARNESS_PASSED");
    }

    private static void testEstimatorExcludesZeroTaskAndUsesTwentySampleP95() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator();
        var rejected = estimator.recordCompletedRuntime(0.01, 0);
        require(!rejected.isSampleAccepted(), "zero-task sample rejected");
        require(estimator.getSampleCount() == 0, "zero-task history unchanged");
        require(LiveAdaptiveDeltaTMaxEstimator.FALLBACK_ZERO_TASK_SAMPLE_EXCLUDED
                .equals(rejected.getFallbackReason()), "zero-task reason");
        for (int i = 1; i <= 20; i++) {
            estimator.recordCompletedRuntime(i / 100.0, 1);
        }
        var snapshot = estimator.getLastSnapshot();
        require(snapshot.getSampleCount() == 20, "history size 20");
        requireClose(snapshot.getP95Seconds(), 0.19, "nearest-rank P95");
        require(snapshot.getP95Seconds() < 0.20, "P95 is not the maximum");
    }

    private static void testTemporalAndWallClockDomainsAreSeparated() {
        TemporalWindowConfig temporal = TemporalWindowConfig.liveRuntimeFreshnessAware(
                1.0, 0.10, 8.0
        );
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator();
        LiveGaOverrunDeadlinePolicy policy = new LiveGaOverrunDeadlinePolicy(
                temporal,
                MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC)
                        .getMobilityConfig(),
                LiveDeltaTMaxMode.LIVE_ADAPTIVE,
                estimator,
                0.40,
                0.50,
                0.10
        );
        TemporalWindowState state = TemporalWindowState.initial(
                0.0, 1.0, policy.initialOperationalMetrics()
        );
        var deadline = policy.computeDeadline(snapshot("policy", 1.0), state, 1_000L);
        requireClose(deadline.getGaWallClockBudgetAtSubmissionSeconds(), 0.20,
                "initial adaptive budget");
        requireClose(deadline.getTemporalMaximumAtSubmissionSeconds(), 1.0,
                "simulation window maximum");
        requireClose(deadline.getMaxSnapshotAgeSimulationSeconds(), 0.50,
                "freshness cap");
        require(deadline.getCooperativeStopDeadlineNs()
                        < deadline.getWallClockDeadlineNs(),
                "cooperative stop reserves finalization time");
        require(deadline.getWallClockDeadlineNs()
                        - deadline.getCooperativeStopDeadlineNs() == 100_000_000L,
                "configured safety margin reserved");
        require(deadline.getTemporalMaximumAtSubmissionSeconds()
                != deadline.getGaWallClockBudgetAtSubmissionSeconds(),
                "temporal and wall-clock values differ");
        require(deadline.getMetricsAtSubmission().getGaRuntimeEstimateSeconds()
                == deadline.getGaWallClockBudgetAtSubmissionSeconds(),
                "same robust estimate feeds DeltaT_min");
    }

    private static void testFreshnessCapIsIndependentFromTemporalMaximum() {
        TemporalWindowConfig temporal = TemporalWindowConfig.liveRuntimeFreshnessAware(
                1.0, 0.10, 8.0
        );
        LiveGaOverrunDeadlinePolicy policy = new LiveGaOverrunDeadlinePolicy(
                temporal,
                MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC)
                        .getMobilityConfig(),
                LiveDeltaTMaxMode.CONFIGURED_STATIC,
                null,
                0.40,
                2.0,
                0.10
        );
        TemporalWindowState state = TemporalWindowState.initial(
                0.0, 1.0, policy.initialOperationalMetrics()
        );
        SystemSnapshot sourceSnapshot = snapshot("freshness-independent", 1.0);
        var deadline = policy.computeDeadline(sourceSnapshot, state, 0L);

        requireClose(deadline.getTemporalMaximumAtSubmissionSeconds(), 1.0,
                "temporal maximum remains one second");
        requireClose(deadline.getMaxSnapshotAgeSimulationSeconds(), 2.0,
                "configured freshness cap remains independent");

        LiveGaJob job = new LiveGaJob(
                "freshness-independent", 0, 1_000_000_000L, 0L, "HARNESS",
                sourceSnapshot, state,
                deadline.getTemporalMaximumAtSubmissionSeconds(),
                deadline.getGaWallClockBudgetAtSubmissionSeconds(),
                deadline.getMaxSnapshotAgeSimulationSeconds(),
                deadline.getCooperativeStopDeadlineNs(),
                deadline.getWallClockDeadlineNs(),
                deadline.getDeltaTMaxSnapshot()
        );
        LiveGaCompletion completion = LiveGaCompletion.success(
                job, null, 100_000_000L, 0.10
        );

        require(completion.snapshotAgeAtSimulationTime(2_500_000_000L)
                        > deadline.getTemporalMaximumAtSubmissionSeconds(),
                "test age exceeds temporal maximum");
        require(completion.snapshotAgeAtSimulationTime(2_500_000_000L)
                        < deadline.getMaxSnapshotAgeSimulationSeconds(),
                "test age remains below freshness cap");
        require(completion.classify(2_500_000_000L) == LiveStaleReason.NONE,
                "age between temporal maximum and freshness cap remains fresh");
        require(completion.classify(3_000_000_000L) == LiveStaleReason.NONE,
                "age exactly equal to freshness cap remains admissible");
        require(completion.classify(3_100_000_000L)
                        == LiveStaleReason.SIMULATION_AGE,
                "age above configured freshness cap is stale");
    }


    private static void testDistinctStaleReasons() {
        LiveAdaptiveDeltaTMaxEstimator.Snapshot estimate =
                LiveAdaptiveDeltaTMaxEstimator.Snapshot.configuredStatic(0.5);
        LiveGaJob job = new LiveGaJob(
                "job", 0, 1_000_000_000L, 0L, "HARNESS",
                snapshot("stale", 1.0), TemporalWindowState.initial(0.0, 1.0),
                8.0, 0.5, 0.5, 500_000_000L, estimate
        );
        LiveGaCompletion fresh = LiveGaCompletion.success(job, null, 400_000_000L, 0.4);
        require(fresh.classify(1_400_000_000L) == LiveStaleReason.NONE, "fresh");
        LiveGaCompletion wall = LiveGaCompletion.success(job, null, 600_000_000L, 0.6);
        require(wall.classify(1_400_000_000L) == LiveStaleReason.WALL_CLOCK, "wall stale");
        require(fresh.classify(1_700_000_000L) == LiveStaleReason.SIMULATION_AGE,
                "simulation stale");
        require(wall.classify(1_700_000_000L)
                == LiveStaleReason.WALL_CLOCK_AND_SIMULATION_AGE, "both stale");
    }


    private static void testWallClockBoundarySemantics() {
        LiveAdaptiveDeltaTMaxEstimator.Snapshot estimate =
                LiveAdaptiveDeltaTMaxEstimator.Snapshot.configuredStatic(0.5);

        LiveGaJob below = boundaryJob("below", estimate);
        require(!below.detectTimeoutIfDeadlineReached(499_999_999L, 0L),
                "deadline not reached below limit");

        LiveGaJob exact = boundaryJob("exact", estimate);
        require(!exact.detectTimeoutIfDeadlineReached(500_000_000L, 0L),
                "exact deadline remains admissible");
        LiveGaCompletion exactCompletion = LiveGaCompletion.success(
                exact, null, 500_000_000L, 0.5
        );
        require(!exactCompletion.isWallClockStale(),
                "runtime exactly equal to budget is fresh");

        LiveGaJob above = boundaryJob("above", estimate);
        require(above.detectTimeoutIfDeadlineReached(500_000_001L, 0L),
                "deadline exceeded above limit");
        LiveGaCompletion aboveCompletion = LiveGaCompletion.success(
                above, null, 500_000_001L, 0.500000001
        );
        require(aboveCompletion.isWallClockStale(),
                "runtime above budget is stale");
    }

    private static LiveGaJob boundaryJob(
            String id,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot estimate
    ) {
        return new LiveGaJob(
                id, 0, 0L, 0L, "HARNESS", snapshot(id, 0.0),
                TemporalWindowState.initial(0.0, 1.0),
                1.0, 0.5, 1.0, 500_000_000L, estimate
        );
    }

    private static void testCooperativeBestSoFar() {
        MaGaOptimizer optimizer = new MaGaOptimizer(
                MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC)
        );
        AtomicInteger checks = new AtomicInteger();
        GaExecutionBudget budget = () -> checks.incrementAndGet() >= 2;
        MaGaResult result = optimizer.optimizeDetailed(snapshot("budget", 1.0), null, budget);
        require(result.getStopReason() == StopReason.TIME_BUDGET_BEST_SO_FAR,
                "cooperative stop reason");
        require(result.getBestChromosome() != null, "best-so-far present");
        require(Double.isFinite(result.getFinalBestFitness()), "fitness finite");
        require(!result.getFinalPopulation().isEmpty(), "final population present");
    }


    private static void testFullStaleStrategyReporting(Path outputRoot) throws IOException {
        SystemSnapshot snapshot = snapshot("stale-report", 1.0);
        MaGaResult result = new MaGaOptimizer(
                MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC)
        ).optimizeDetailed(snapshot);
        TemporalStepResult step = step(snapshot, result);

        Map<String, LiveAssignmentDecision> activeAssignments = new LinkedHashMap<>();
        int local = 0;
        int vehicle = 0;
        int edge = 0;
        int cloud = 0;
        for (var gene : result.getBestEvaluation().getGeneBreakdowns()) {
            LiveAssignmentDecision decision = LiveAssignmentDecision.from(gene);
            activeAssignments.put(decision.getTaskId(), decision);
            switch (decision.getNodeType()) {
                case LOCAL: local++; break;
                case VEHICLE: vehicle++; break;
                case EDGE: edge++; break;
                case CLOUD: cloud++; break;
                default: break;
            }
        }
        LiveAppliedStrategy active = new LiveAppliedStrategy(
                2_000_000_000L, "active-snapshot", 1.5,
                result.getFinalBestFitness(), activeAssignments,
                local, vehicle, edge, cloud
        );

        try (LiveStaleStrategyWriter writer =
                     new LiveStaleStrategyWriter(outputRoot)) {
            writer.writeStale(
                    "job-report-no-active", step,
                    LiveStaleReason.SIMULATION_AGE, 3_000_000_000L,
                    0.20, 0.20, 1.0, 0.50, null
            );
            writer.writeStale(
                    "job-report-active", step,
                    LiveStaleReason.WALL_CLOCK_AND_SIMULATION_AGE,
                    3_000_000_000L, 0.30, 0.20, 1.0, 0.50, active
            );
        }
        String decisions = Files.readString(
                outputRoot.resolve("live_stale_assignment_decisions.csv"),
                StandardCharsets.UTF_8
        );
        String distribution = Files.readString(
                outputRoot.resolve("live_stale_assignment_distribution.csv"),
                StandardCharsets.UTF_8
        );
        String comparison = Files.readString(
                outputRoot.resolve("live_stale_vs_active_strategy.csv"),
                StandardCharsets.UTF_8
        );
        String matrix = Files.readString(
                outputRoot.resolve("live_stale_vs_active_transition_matrix.csv"),
                StandardCharsets.UTF_8
        );
        String summary = Files.readString(
                outputRoot.resolve("live_stale_strategy_summary.jsonl"),
                StandardCharsets.UTF_8
        );
        require(decisions.contains("task_0"), "full stale assignment retained");
        require(decisions.contains("SIMULATION_AGE"), "stale reason retained");
        require(distribution.contains("localPercentage"),
                "distribution percentages retained");
        require(distribution.contains("activeStrategyPresent"),
                "active presence retained");
        require(comparison.contains("UNASSIGNED_BY_ACTIVE_STRATEGY"),
                "uncovered task classified explicitly");
        require(matrix.contains("UNASSIGNED_BY_ACTIVE_STRATEGY->LOCAL"),
                "stale-only transition aggregated");
        require(matrix.contains("SAME_LOCAL"),
                "same placement transition aggregated");
        require(summary.contains("transitionMatrix"),
                "transition matrix retained in summary");
        require(summary.contains("\"activeStrategyPresent\":false"),
                "missing active strategy is explicit");
        require(!summary.contains("\"activeStrategyPresent\":false,\"activeSnapshotId\":\"\",\"activeSnapshotAgeSimulationSeconds\":0.0"),
                "missing active strategy is not represented as age zero");
    }

    private static void testCanonicalMainReporting(Path outputRoot) throws IOException {
        SystemSnapshot snapshot = snapshot("canonical", 1.0);
        MaGaResult result = new MaGaOptimizer(
                MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC)
        ).optimizeDetailed(snapshot);
        TemporalStepResult step = step(snapshot, result);
        try (LiveNativeReportingCollector collector =
                     new LiveNativeReportingCollector(
                             outputRoot, "scenario", "profile", "variant",
                             "bridge", "LIVE", "source", "reuse",
                             "cell", "runtime"
                     )) {
            collector.recordSubmitted(
                    "canonical-job", 0, "HARNESS", 1_000_000_000L, 0L,
                    snapshot.getSnapshotId(), snapshot.getTimeSeconds(),
                    snapshot.getTasks().size(), snapshot.getCandidateNodes().size(),
                    1.0, 0.2, 2.0, 200_000_000L,
                    "LIVE_ADAPTIVE", 0.2, 3, 0.18, 0.2,
                    0.2, 0.2, 0.2, "NONE"
            );
            collector.recordStaleDiscarded(
                    "canonical-job", step, 2_000_000_000L,
                    300_000_000L, 0.3, 1.0, 0.0,
                    LiveStaleReason.WALL_CLOCK_AND_SIMULATION_AGE
            );
        }
        Path reporting = outputRoot.resolve("live-reporting");
        String events = Files.readString(
                reporting.resolve("live_ga_job_events.jsonl"),
                StandardCharsets.UTF_8
        );
        String discarded = Files.readString(
                reporting.resolve("live_discarded_window_records.csv"),
                StandardCharsets.UTF_8
        );
        require(events.contains("STALE_WALL_CLOCK_AND_SIMULATION_AGE"),
                "main event reporting distinguishes stale reason");
        require(events.contains("gaWallClockBudgetAtSubmissionSeconds"),
                "main event reporting exposes wall-clock budget");
        require(events.contains("temporalMaximumAtSubmissionSeconds"),
                "main event reporting exposes temporal maximum");
        require(events.contains("maxSnapshotAgeSimulationSeconds"),
                "main event reporting exposes freshness cap");
        require(events.contains("\"maxSnapshotAgeSimulationSeconds\":2.0"),
                "main event reporting preserves configured freshness cap");
        require(events.contains("snapshotAgeAtClassificationSeconds"),
                "main event reporting exposes classification age");
        require(discarded.contains("finalClassification"),
                "discarded CSV exposes final classification");
        require(discarded.contains("STALE_WALL_CLOCK_AND_SIMULATION_AGE"),
                "discarded CSV distinguishes combined stale reason");
    }

    private static TemporalStepResult step(
            SystemSnapshot snapshot,
            MaGaResult result
    ) {
        return new TemporalStepResult(
                0, ReoptimizationTrigger.firstRun(1.0), 0.0, 1.0,
                snapshot,
                DynamicityBreakdown.firstRun(
                        snapshot.getSnapshotId(), snapshot.getTimeSeconds()
                ),
                PopulationReuseMode.FIRST_RUN,
                result.getFinalPopulation().size(),
                result.getFinalPopulation().size(),
                result
        );
    }

    private static LiveAdaptiveDeltaTMaxEstimator estimator() {
        return new LiveAdaptiveDeltaTMaxEstimator(
                new LiveAdaptiveDeltaTMaxEstimator.Config(
                        0.20, 0.10, 1.50, 20, 3,
                        0.10, 0.25, 0.10
                )
        );
    }

    private static SystemSnapshot snapshot(String id, double time) {
        List<VehicleSnapshot> vehicles = List.of(
                new VehicleSnapshot("veh_0", 0.0, 0.0, 0.0, 1.0E9)
        );
        List<TaskInstance> tasks = List.of(
                new TaskInstance("task_0", "veh_0", 160_000.0,
                        20_000.0, 2.0E8, 1.0)
        );
        List<NodeCandidate> nodes = new ArrayList<>();
        nodes.add(new NodeCandidate(
                "local_0", "veh_0", "veh_0", NodeType.LOCAL,
                1.0E9, 0.0, 0.0, null, null, null
        ));
        return new SystemSnapshot(id, time, vehicles, tasks, nodes);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException("FAILED: " + message); }
    }
    private static void requireClose(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 1.0E-9) {
            throw new IllegalStateException(
                    "FAILED: " + message + " actual=" + actual + " expected=" + expected
            );
        }
    }
}
