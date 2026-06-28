package org.eclipse.mosaic.app.maga.liveruntime;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import config.window.TemporalMaximumBoundMode;
import config.window.TemporalMinimumBoundMode;
import config.window.TemporalWindowConfig;
import ga.core.MaGaResult;
import ga.core.StopReason;
import ga.fitness.breakdown.EvaluationBreakdown;
import model.genetic.Chromosome;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;
import org.eclipse.mosaic.app.maga.liveruntime.reporting.LiveNativeReportingCollector;
import window.dynamicity.DynamicityBreakdown;
import window.population.PopulationReuseDecision;
import window.population.PopulationReuseMode;
import window.population.WindowPerformanceSignal;
import window.state.TemporalStepResult;
import window.state.TemporalWindowState;
import window.timing.AdaptiveWindowDecision;
import window.timing.CoverageReferenceCalculator;
import window.timing.TemporalOperationalMetrics;
import window.timing.TemporalWindowBounds;
import window.timing.TemporalWindowBoundsCalculator;
import window.trigger.ReoptimizationTrigger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LiveAdaptiveDeltaTMaxHarness {
    private static int assertions;

    private LiveAdaptiveDeltaTMaxHarness() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: LiveAdaptiveDeltaTMaxHarness <estimator|integration|core-live-consistency|bound-conflict> [outputDir]"
            );
        }
        String mode = args[0];
        Path output = args.length == 2
                ? Path.of(args[1]).toAbsolutePath().normalize()
                : Path.of("tmp/v3b-adaptive-deltatmax/harness-" + mode)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(output);

        if ("estimator".equals(mode)) {
            runEstimatorHarness(output);
            System.out.println(
                    "V3B_ADAPTIVE_DELTATMAX_ESTIMATOR_HARNESS_PASSED assertions="
                            + assertions
            );
            return;
        }
        if ("integration".equals(mode)) {
            runIntegrationHarness(output);
            System.out.println(
                    "V3B_ADAPTIVE_DELTATMAX_INTEGRATION_HARNESS_PASSED assertions="
                            + assertions
            );
            return;
        }
        if ("core-live-consistency".equals(mode)) {
            runCoreLiveConsistencyHarness(output);
            System.out.println(
                    "V3B_ADAPTIVE_DELTATMAX_CORE_LIVE_CONSISTENCY_HARNESS_PASSED assertions="
                            + assertions
            );
            return;
        }
        if ("bound-conflict".equals(mode)) {
            runBoundConflictHarness(output);
            System.out.println(
                    "V3B_ADAPTIVE_DELTATMAX_BOUND_CONFLICT_HARNESS_PASSED assertions="
                            + assertions
            );
            return;
        }
        throw new IllegalArgumentException("Unknown harness mode: " + mode);
    }

    private static void runEstimatorHarness(Path output) throws Exception {
        verifyStaticConfigWithoutNewFields(output);
        verifyStaticConfigWithConfiguredMax(output);
        verifyAdaptiveConfigValidation(output);
        verifyNoSamples();
        verifyOneSampleWarmup();
        verifyWarmupComplete();
        verifyConstantSamples();
        verifyGradualGrowth();
        verifyGradualDecrease();
        verifySingleOutlier();
        verifyMinimumClamp();
        verifyMaximumClamp();
        verifyInvalidSamples();
        verifyEstimatorRuntimeBoundConflict();
        verifyHistoryRollover();
        verifyNearestRankP95();
        verifyStepUpLimit();
        verifyStepDownLimit();
        verifyReplayStaticUnchanged();
    }

    private static void runIntegrationHarness(Path output) throws Exception {
        verifyDeadlinePolicyStaticStartup();
        verifyDeadlinePolicyAdaptiveStartupAndNextSubmission();
        verifyAppliedAndStaleCompletionOrder();
        verifyIncompleteTimeoutDoesNotUpdate();
        verifyTraceTelemetry(output);
    }

    private static void runCoreLiveConsistencyHarness(Path output) throws Exception {
        verifyCoreLiveDeltaTMaxConsistency();
        verifyCoreLiveIntentionalMismatchDetection();
        verifyNextSubmissionUsesPostCompletionUpdate();
        verifyTraceTelemetrySeparated(output);
    }

    private static void runBoundConflictHarness(Path output) throws Exception {
        verifyBoundConflictConfigValidation(output);
        verifyEstimatorRuntimeBoundConflict();
        verifyDeadlinePolicyBlocksSubmissionOnBoundConflict();
    }

    private static void verifyStaticConfigWithoutNewFields(Path output) throws Exception {
        MaGaLiveRuntimeConfig config = loadConfig(output, "static-missing", "");
        require(
                config.getDeltaTMaxMode() == LiveDeltaTMaxMode.CONFIGURED_STATIC,
                "missing deltaTMaxMode defaults to CONFIGURED_STATIC"
        );
    }

    private static void verifyStaticConfigWithConfiguredMax(Path output) throws Exception {
        MaGaLiveRuntimeConfig config = loadConfig(
                output,
                "static-explicit",
                "\"deltaTMaxMode\": \"CONFIGURED_STATIC\","
        );
        require(
                config.getDeltaTMaxMode() == LiveDeltaTMaxMode.CONFIGURED_STATIC,
                "explicit CONFIGURED_STATIC mode"
        );
        requireClose(
                config.getConfiguredMaxWindowSeconds(),
                0.2,
                "configured static max preserved"
        );
    }

    private static void verifyAdaptiveConfigValidation(Path output) throws Exception {
        MaGaLiveRuntimeConfig config = loadConfig(
                output,
                "adaptive-valid",
                adaptiveJsonFields(0.2, 0.05, 1.0, 8, 3, 0.05, 0.2, 0.1)
        );
        require(
                config.getDeltaTMaxMode() == LiveDeltaTMaxMode.LIVE_ADAPTIVE,
                "LIVE_ADAPTIVE parses explicitly"
        );
        config.getAdaptiveDeltaTMaxConfig();

        expectInvalidConfig(
                output,
                "adaptive-missing-field",
                "\"deltaTMaxMode\": \"LIVE_ADAPTIVE\",",
                "configuredInitialDeltaTMaxSeconds"
        );
        expectInvalidConfig(
                output,
                "adaptive-bad-warmup",
                adaptiveJsonFields(0.2, 0.05, 1.0, 4, 5, 0.05, 0.2, 0.1),
                "adaptiveDeltaTMaxWarmupSamples"
        );
        expectInvalidConfig(
                output,
                "adaptive-bad-step",
                adaptiveJsonFields(0.2, 0.05, 1.0, 4, 2, 0.05, 0.0, 0.1),
                "adaptiveDeltaTMaxMaximumStepUpSeconds"
        );
    }

    private static void verifyNoSamples() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 1.0, 5, 3, 0.05, 0.2, 0.1);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.estimateForSubmission(0.06);
        requireClose(snapshot.getUpdatedSeconds(), 0.2, "no samples keeps initial");
        require(snapshot.getSampleCount() == 0, "no samples count");
        require(
                LiveAdaptiveDeltaTMaxEstimator.FALLBACK_NO_SAMPLES.equals(snapshot.getFallbackReason()),
                "no samples fallback"
        );
    }

    private static void verifyOneSampleWarmup() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 1.0, 5, 3, 0.05, 0.2, 0.1);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.recordCompletedRuntime(0.4, 0.06);
        requireClose(snapshot.getUpdatedSeconds(), 0.2, "one sample remains warmup");
        require(snapshot.getSampleCount() == 1, "one sample count");
        require(
                LiveAdaptiveDeltaTMaxEstimator.FALLBACK_WARMUP.equals(snapshot.getFallbackReason()),
                "one sample warmup fallback"
        );
    }

    private static void verifyWarmupComplete() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 1.0, 5, 3, 0.05, 0.2, 0.1);
        estimator.recordCompletedRuntime(0.3, 0.06);
        estimator.recordCompletedRuntime(0.3, 0.06);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.recordCompletedRuntime(0.3, 0.06);
        requireClose(snapshot.getP95Seconds(), 0.3, "warmup complete p95");
        requireClose(snapshot.getTargetSeconds(), 0.35, "warmup complete target");
        requireClose(snapshot.getUpdatedSeconds(), 0.35, "warmup complete adapts");
    }

    private static void verifyConstantSamples() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 1.0, 5, 2, 0.05, 0.2, 0.1);
        estimator.recordCompletedRuntime(0.25, 0.06);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.recordCompletedRuntime(0.25, 0.06);
        requireClose(snapshot.getUpdatedSeconds(), 0.3, "constant samples margin");
    }

    private static void verifyGradualGrowth() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 2.0, 5, 1, 0.0, 0.2, 0.2);
        estimator.recordCompletedRuntime(0.3, 0.06);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.recordCompletedRuntime(0.7, 0.06);
        requireClose(snapshot.getUpdatedSeconds(), 0.5, "gradual growth step-limited");
    }

    private static void verifyGradualDecrease() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(1.0, 0.05, 2.0, 5, 1, 0.0, 0.4, 0.2);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.recordCompletedRuntime(0.2, 0.06);
        requireClose(snapshot.getUpdatedSeconds(), 0.8, "gradual decrease step-limited");
    }

    private static void verifySingleOutlier() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 10.0, 20, 20, 0.0, 10.0, 10.0);
        for (int i = 0; i < 19; i++) {
            estimator.recordCompletedRuntime(0.2, 0.06);
        }
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.recordCompletedRuntime(5.0, 0.06);
        requireClose(snapshot.getP95Seconds(), 0.2, "single high outlier excluded by nearest-rank P95");
        requireClose(snapshot.getUpdatedSeconds(), 0.2, "single high outlier does not widen");
    }

    private static void verifyMinimumClamp() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 1.0, 5, 3, 0.0, 0.2, 0.1);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.estimateForSubmission(0.5);
        requireClose(snapshot.getUpdatedSeconds(), 0.5, "deltaTMin raises live estimate");
    }

    private static void verifyMaximumClamp() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 0.6, 5, 1, 0.2, 2.0, 2.0);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.recordCompletedRuntime(2.0, 0.06);
        requireClose(snapshot.getClampedSeconds(), 0.6, "maximum clamp raw");
        requireClose(snapshot.getUpdatedSeconds(), 0.6, "maximum clamp updated");
    }

    private static void verifyInvalidSamples() {
        double[] invalid = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                0.0,
                -0.1
        };
        for (double value : invalid) {
            LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 1.0, 5, 1, 0.0, 0.2, 0.1);
            LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                    estimator.recordCompletedRuntime(value, 0.06);
            require(snapshot.getSampleCount() == 0, "invalid sample excluded: " + value);
            requireClose(snapshot.getUpdatedSeconds(), 0.2, "invalid sample preserves estimate");
        }
    }

    private static void verifyBoundConflictConfigValidation(Path output) throws Exception {
        expectInvalidConfig(
                output,
                "adaptive-min-above-max",
                adaptiveJsonFields(0.9, 1.1, 1.0, 4, 2, 0.0, 0.2, 0.2),
                "BOUND_CONFLICT"
        );
        expectInvalidConfig(
                output,
                "adaptive-initial-above-max",
                adaptiveJsonFields(1.1, 0.05, 1.0, 4, 2, 0.0, 0.2, 0.2),
                "configuredInitialDeltaTMaxSeconds must be <= adaptiveDeltaTMaxMaximumSeconds"
        );
        expectInvalidConfig(
                output,
                "adaptive-initial-below-min",
                adaptiveJsonFields(0.05, 0.1, 1.0, 4, 2, 0.0, 0.2, 0.2),
                "configuredInitialDeltaTMaxSeconds must be >= adaptiveDeltaTMaxMinimumSeconds"
        );
        expectInvalidConfig(
                output,
                "adaptive-configured-min-above-max",
                adaptiveJsonFieldsWithRuntimeEstimate(
                        0.9,
                        0.05,
                        1.0,
                        4,
                        2,
                        0.0,
                        0.2,
                        0.2,
                        1.1
                ),
                "BOUND_CONFLICT"
        );

        expectInvalidEstimatorConfig(
                0.9,
                1.1,
                1.0,
                "BOUND_CONFLICT"
        );
        expectInvalidEstimatorConfig(
                1.1,
                0.05,
                1.0,
                "configuredInitialDeltaTMaxSeconds must be <= adaptiveMaximumSeconds"
        );
        expectInvalidEstimatorConfig(
                0.05,
                0.1,
                1.0,
                "configuredInitialDeltaTMaxSeconds must be >= adaptiveMinimumSeconds"
        );
    }

    private static void verifyEstimatorRuntimeBoundConflict() {
        LiveAdaptiveDeltaTMaxEstimator equal = estimator(
                1.0,
                0.05,
                1.0,
                4,
                1,
                0.0,
                0.2,
                0.2
        );
        requireClose(
                equal.estimateForSubmission(1.0).getUpdatedSeconds(),
                1.0,
                "deltaTMin equal to maximum is valid"
        );

        LiveAdaptiveDeltaTMaxEstimator below = estimator(
                0.9,
                0.05,
                1.0,
                4,
                1,
                0.0,
                0.2,
                0.2
        );
        requireClose(
                below.estimateForSubmission(0.999999).getUpdatedSeconds(),
                0.999999,
                "deltaTMin just below maximum is valid"
        );

        expectRuntimeBoundConflict(
                estimator(0.9, 0.05, 1.0, 4, 1, 0.0, 0.2, 0.2),
                1.000001,
                "deltaTMin just above maximum conflicts"
        );
        expectRuntimeBoundConflict(
                estimator(0.9, 0.05, 1.0, 4, 1, 0.0, 0.2, 0.2),
                Double.NaN,
                "NaN deltaTMin rejected"
        );
        expectRuntimeBoundConflict(
                estimator(0.9, 0.05, 1.0, 4, 1, 0.0, 0.2, 0.2),
                Double.POSITIVE_INFINITY,
                "positive infinity deltaTMin rejected"
        );
        expectRuntimeBoundConflict(
                estimator(0.9, 0.05, 1.0, 4, 1, 0.0, 0.2, 0.2),
                Double.NEGATIVE_INFINITY,
                "negative infinity deltaTMin rejected"
        );
    }

    private static void verifyDeadlinePolicyBlocksSubmissionOnBoundConflict() {
        TemporalWindowConfig temporal = TemporalWindowConfig.liveRuntimeDeltaTMaxOverride(
                1.0,
                1.1,
                1.0
        );
        LiveGaOverrunDeadlinePolicy policy = adaptivePolicy(
                temporal,
                estimator(0.9, 0.05, 1.0, 4, 1, 0.0, 0.2, 0.2)
        );
        TemporalWindowState state = TemporalWindowState.initial(
                0.0,
                1.0,
                policy.initialOperationalMetrics()
        );
        boolean submitted = false;
        try {
            policy.computeDeadline(fixtureSnapshot("conflict", 1.0), state, 100_000_000L);
            submitted = true;
        } catch (LiveAdaptiveDeltaTMaxEstimator.BoundConflictException e) {
            require(
                    e.getMessage().contains("BOUND_CONFLICT")
                            && e.getMessage().contains("adaptiveMaximumSeconds"),
                    "deadline conflict message includes values and context"
            );
        }
        require(!submitted, "no job submitted when deadline bound conflicts");
    }

    private static void verifyHistoryRollover() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 1.0, 3, 1, 0.0, 1.0, 1.0);
        estimator.recordCompletedRuntime(0.1, 0.06);
        estimator.recordCompletedRuntime(0.2, 0.06);
        estimator.recordCompletedRuntime(0.3, 0.06);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.recordCompletedRuntime(0.4, 0.06);
        require(snapshot.getSampleCount() == 3, "history rollover keeps configured size");
        requireClose(snapshot.getP95Seconds(), 0.4, "history rollover drops oldest");
    }

    private static void verifyNearestRankP95() {
        List<Double> values = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            values.add((double) i);
        }
        requireClose(
                LiveAdaptiveDeltaTMaxEstimator.nearestRankP95(values),
                19.0,
                "nearest-rank P95 uses ceil(0.95*n)"
        );
    }

    private static void verifyStepUpLimit() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 2.0, 5, 1, 0.0, 0.1, 0.1);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.recordCompletedRuntime(1.0, 0.06);
        requireClose(snapshot.getUpdatedSeconds(), 0.3, "step-up limit");
    }

    private static void verifyStepDownLimit() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(1.0, 0.05, 2.0, 5, 1, 0.0, 0.3, 0.1);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                estimator.recordCompletedRuntime(0.2, 0.06);
        requireClose(snapshot.getUpdatedSeconds(), 0.9, "step-down limit");
    }

    private static void verifyReplayStaticUnchanged() {
        TemporalWindowConfig replay = TemporalWindowConfig.configuredBoundsForReplay(
                1.0,
                0.01,
                0.2
        );
        require(
                replay.getMaximumBoundMode() == TemporalMaximumBoundMode.CONFIGURED_MAX,
                "configuredBoundsForReplay remains CONFIGURED_MAX"
        );
        requireClose(
                replay.getConfiguredMaxWindowSeconds(),
                0.2,
                "configuredBoundsForReplay max unchanged"
        );
    }

    private static void verifyDeadlinePolicyStaticStartup() {
        TemporalWindowConfig temporal = TemporalWindowConfig.configuredBoundsForReplay(
                1.0,
                0.01,
                0.2
        );
        LiveGaOverrunDeadlinePolicy policy = new LiveGaOverrunDeadlinePolicy(
                temporal,
                MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC).getMobilityConfig()
        );
        TemporalWindowState state = TemporalWindowState.initial(
                0.0,
                1.0,
                policy.initialOperationalMetrics()
        );
        LiveGaOverrunDeadlinePolicy.LiveGaDeadline deadline =
                policy.computeDeadline(fixtureSnapshot("static", 1.0), state, 1_000L);
        requireClose(deadline.getDeltaTMaxAtSubmissionSeconds(), 0.2, "static deadline uses configured max");
        require(
                deadline.getDeltaTMaxSnapshot().getMode() == LiveDeltaTMaxMode.CONFIGURED_STATIC,
                "static deadline telemetry mode"
        );
    }

    private static void verifyDeadlinePolicyAdaptiveStartupAndNextSubmission() {
        TemporalWindowConfig temporal = TemporalWindowConfig.liveRuntimeDeltaTMaxOverride(
                1.0,
                0.01,
                1.0
        );
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 1.0, 5, 2, 0.1, 0.2, 0.1);
        LiveGaOverrunDeadlinePolicy policy = adaptivePolicy(temporal, estimator);
        TemporalWindowState state = TemporalWindowState.initial(
                0.0,
                1.0,
                policy.initialOperationalMetrics()
        );

        LiveGaOverrunDeadlinePolicy.LiveGaDeadline initial =
                policy.computeDeadline(fixtureSnapshot("adaptive_initial", 1.0), state, 10_000L);
        requireClose(initial.getDeltaTMaxAtSubmissionSeconds(), 0.2, "adaptive startup initial");
        require(
                initial.getDeltaTMaxSnapshot().getMode() == LiveDeltaTMaxMode.LIVE_ADAPTIVE,
                "adaptive startup telemetry mode"
        );

        policy.recordCompletedRuntime(0.1, 0.06);
        LiveGaOverrunDeadlinePolicy.LiveGaDeadline warmup =
                policy.computeDeadline(fixtureSnapshot("adaptive_warmup", 2.0), state, 20_000L);
        requireClose(warmup.getDeltaTMaxAtSubmissionSeconds(), 0.2, "warmup still initial");

        policy.recordCompletedRuntime(0.5, 0.06);
        LiveGaOverrunDeadlinePolicy.LiveGaDeadline adapted =
                policy.computeDeadline(fixtureSnapshot("adaptive_next", 3.0), state, 30_000L);
        requireClose(adapted.getDeltaTMaxAtSubmissionSeconds(), 0.4, "next submission uses updated estimator");
    }

    private static void verifyAppliedAndStaleCompletionOrder() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 1.0, 5, 2, 0.1, 0.2, 0.1);
        LiveGaOverrunDeadlinePolicy policy = adaptivePolicy(
                TemporalWindowConfig.configuredBoundsForReplay(1.0, 0.01, 0.2),
                estimator
        );

        LiveGaJob appliedJob = job("applied", 0.2, 1_000L, 201_000_000L,
                LiveAdaptiveDeltaTMaxEstimator.Snapshot.liveAdaptive(
                        0.2, 0, 0.0, 0.0, 0.2, 0.2, 0.2,
                        LiveAdaptiveDeltaTMaxEstimator.FALLBACK_NO_SAMPLES
                ));
        LiveGaCompletion applied = LiveGaCompletion.success(
                appliedJob,
                step("applied_snapshot", 1.0, 0, 0.06, 0.2),
                150_000_000L,
                0.1
        );
        require(!applied.isStale(), "applied completion within submission bound");
        policy.recordCompletedRuntime(applied.getWallClockRuntimeSeconds(), 0.06);
        requireClose(appliedJob.getDeltaTMaxAtSubmissionSeconds(), 0.2, "applied job delta remains immutable");

        LiveGaJob staleJob = job("stale", 0.2, 200_000_000L, 400_000_000L,
                appliedJob.getDeltaTMaxSnapshotAtSubmission());
        LiveGaCompletion stale = LiveGaCompletion.success(
                staleJob,
                step("stale_snapshot", 2.0, 1, 0.06, 0.2),
                500_000_000L,
                0.5
        );
        require(stale.isStale(), "completed stale result classified against submission delta");
        policy.recordCompletedRuntime(stale.getWallClockRuntimeSeconds(), 0.06);

        TemporalWindowState state = TemporalWindowState.initial(
                0.0,
                1.0,
                policy.initialOperationalMetrics()
        );
        LiveGaOverrunDeadlinePolicy.LiveGaDeadline next =
                policy.computeDeadline(fixtureSnapshot("after_stale", 3.0), state, 600_000_000L);
        requireClose(next.getDeltaTMaxAtSubmissionSeconds(), 0.4, "stale completed sample included after classification");
        requireClose(staleJob.getDeltaTMaxAtSubmissionSeconds(), 0.2, "stale job delta remains immutable");
        require(stale.getDeltaTMaxMismatchSeconds() == 0.0, "adaptive completion mismatch remains zero");
    }

    private static void verifyIncompleteTimeoutDoesNotUpdate() {
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(0.2, 0.05, 1.0, 5, 1, 0.0, 0.2, 0.1);
        LiveGaJob job = job(
                "timeout",
                0.2,
                1_000L,
                201_000_000L,
                estimator.estimateForSubmission(0.06)
        );
        boolean detected = job.detectTimeoutIfDeadlineReached(201_000_000L, 2_000_000_000L);
        require(detected, "timeout detected while running");
        require(estimator.getSampleCount() == 0, "incomplete timeout excluded before terminal runtime");
        requireClose(job.getDeltaTMaxAtSubmissionSeconds(), 0.2, "timeout job delta immutable");
    }

    private static void verifyTraceTelemetry(Path output) throws Exception {
        Path runDir = output.resolve("trace-run");
        Files.createDirectories(runDir);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                LiveAdaptiveDeltaTMaxEstimator.Snapshot.liveAdaptive(
                        0.4,
                        2,
                        0.5,
                        0.6,
                        0.6,
                        0.2,
                        0.4,
                        LiveAdaptiveDeltaTMaxEstimator.FALLBACK_LIVE_ADAPTIVE
                );
        try (LiveRuntimeTraceWriter writer = new LiveRuntimeTraceWriter(
                runDir,
                "harness",
                1
        )) {
            writer.writeRuntime(
                    1_000_000_000L,
                    1,
                    LiveGaExecutionState.RESULT_READY_WITHIN_BOUND,
                    "HARNESS",
                    "snapshot_trace",
                    1.0,
                    1,
                    1,
                    false,
                    true,
                    0.5,
                    0.4,
                    false,
                    false,
                    "",
                    "",
                    new LiveRuntimeTraceDetails(
                            0.2,
                            0.4,
                            0.0,
                            1_400_000_000L,
                            false,
                            0L,
                            0L,
                            0,
                            0,
                            snapshot
                    )
            );
        }
        String csv = Files.readString(
                runDir.resolve("live-maga-runtime/live_ga_runtime_trace.csv"),
                StandardCharsets.UTF_8
        );
        require(csv.contains("deltaTMaxMode"), "runtime trace deltaTMaxMode header");
        require(csv.contains("adaptiveDeltaTMaxP95Seconds"), "runtime trace P95 header");
        require(csv.contains("LIVE_ADAPTIVE"), "runtime trace adaptive mode row");
        require(csv.contains("LIVE_ADAPTIVE,"), "runtime trace fallback row");
    }

    private static void verifyCoreLiveDeltaTMaxConsistency() {
        TemporalWindowConfig temporal = TemporalWindowConfig.liveRuntimeDeltaTMaxOverride(
                1.0,
                0.01,
                1.0
        );
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(
                0.4,
                0.05,
                1.0,
                8,
                1,
                0.0,
                0.2,
                0.2
        );
        LiveGaOverrunDeadlinePolicy policy = adaptivePolicy(temporal, estimator);
        TemporalWindowState state = TemporalWindowState.initial(
                0.0,
                1.0,
                policy.initialOperationalMetrics()
        );
        SystemSnapshot snapshot = fixtureSnapshot("core_live", 1.0);
        LiveGaOverrunDeadlinePolicy.LiveGaDeadline deadline =
                policy.computeDeadline(snapshot, state, 100_000_000L);
        requireClose(
                deadline.getDeltaTMaxAtSubmissionSeconds(),
                0.4,
                "job submission stores adaptive value"
        );

        TemporalWindowState stateAtSubmission = stateWithMetrics(
                state,
                deadline.getMetricsAtSubmission()
        );
        TemporalWindowBounds coreBounds = coreBounds(
                temporal,
                snapshot,
                stateAtSubmission
        );
        requireClose(
                coreBounds.getMaximumWindowSeconds(),
                0.4,
                "core temporal maximum uses submission override"
        );

        LiveGaJob job = new LiveGaJob(
                "core_live_job",
                stateAtSubmission.getWindowIndex(),
                1_000_000_000L,
                100_000_000L,
                "HARNESS",
                snapshot,
                stateAtSubmission,
                deadline.getDeltaTMaxAtSubmissionSeconds(),
                deadline.getWallClockDeadlineNs(),
                deadline.getDeltaTMaxSnapshot()
        );
        TemporalStepResult completedStep = step(
                "core_live_completed",
                1.0,
                0,
                coreBounds.getMinimumWindowSeconds(),
                coreBounds.getMaximumWindowSeconds()
        );
        LiveGaCompletion completion = LiveGaCompletion.success(
                job,
                completedStep,
                200_000_000L,
                0.1
        );
        requireClose(
                completedStep.getAdaptiveWindowDecision()
                        .getBounds()
                        .getMaximumWindowSeconds(),
                0.4,
                "completed step reports core maximum"
        );
        requireClose(
                completion.getDeltaTMaxSeconds(),
                0.4,
                "completion observes core maximum"
        );
        requireClose(
                completion.getDeltaTMaxMismatchSeconds(),
                0.0,
                "matching core/submission values produce zero mismatch"
        );
    }

    private static void verifyCoreLiveIntentionalMismatchDetection() {
        LiveAdaptiveDeltaTMaxEstimator.Snapshot submissionSnapshot =
                LiveAdaptiveDeltaTMaxEstimator.Snapshot.liveAdaptive(
                        0.4,
                        3,
                        0.35,
                        0.4,
                        0.4,
                        0.4,
                        0.4,
                        LiveAdaptiveDeltaTMaxEstimator.FALLBACK_LIVE_ADAPTIVE
                );
        LiveGaJob job = job("mismatch", 0.4, 100_000_000L, 500_000_000L, submissionSnapshot);
        TemporalStepResult mismatchedStep = step(
                "mismatch_completed",
                1.0,
                0,
                0.06,
                0.35
        );
        LiveGaCompletion completion = LiveGaCompletion.success(
                job,
                mismatchedStep,
                200_000_000L,
                0.1
        );
        requireClose(
                completion.getDeltaTMaxMismatchSeconds(),
                0.05,
                "intentional mismatch is detected"
        );
    }

    private static void verifyNextSubmissionUsesPostCompletionUpdate() {
        TemporalWindowConfig temporal = TemporalWindowConfig.liveRuntimeDeltaTMaxOverride(
                1.0,
                0.01,
                1.0
        );
        LiveAdaptiveDeltaTMaxEstimator estimator = estimator(
                0.4,
                0.05,
                1.0,
                8,
                1,
                0.0,
                0.2,
                0.2
        );
        LiveGaOverrunDeadlinePolicy policy = adaptivePolicy(temporal, estimator);
        TemporalWindowState state = TemporalWindowState.initial(
                0.0,
                1.0,
                policy.initialOperationalMetrics()
        );
        LiveGaOverrunDeadlinePolicy.LiveGaDeadline first =
                policy.computeDeadline(fixtureSnapshot("first", 1.0), state, 100_000_000L);
        LiveGaJob firstJob = job(
                "first",
                first.getDeltaTMaxAtSubmissionSeconds(),
                100_000_000L,
                first.getWallClockDeadlineNs(),
                first.getDeltaTMaxSnapshot()
        );
        LiveAdaptiveDeltaTMaxEstimator.Snapshot postUpdate =
                policy.recordCompletedRuntime(0.6, 0.06);
        require(postUpdate.isSampleAccepted(), "terminal runtime sample accepted");
        requireClose(
                postUpdate.getUpdatedSeconds(),
                0.6,
                "post-completion update prepares next submission"
        );
        requireClose(
                firstJob.getDeltaTMaxAtSubmissionSeconds(),
                0.4,
                "completed job keeps submission delta"
        );

        LiveGaOverrunDeadlinePolicy.LiveGaDeadline second =
                policy.computeDeadline(fixtureSnapshot("second", 2.0), state, 200_000_000L);
        requireClose(
                second.getDeltaTMaxAtSubmissionSeconds(),
                0.6,
                "next submission uses post-completion update"
        );
    }

    private static void verifyTraceTelemetrySeparated(Path output) throws Exception {
        Path runDir = output.resolve("trace-separated-run");
        Files.createDirectories(runDir);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot submission =
                LiveAdaptiveDeltaTMaxEstimator.Snapshot.liveAdaptive(
                        0.4,
                        2,
                        0.35,
                        0.4,
                        0.4,
                        0.4,
                        0.4,
                        LiveAdaptiveDeltaTMaxEstimator.FALLBACK_LIVE_ADAPTIVE
                );
        LiveAdaptiveDeltaTMaxEstimator.Snapshot postCompletion =
                LiveAdaptiveDeltaTMaxEstimator.Snapshot.liveAdaptive(
                        0.6,
                        3,
                        0.6,
                        0.6,
                        0.6,
                        0.4,
                        0.6,
                        LiveAdaptiveDeltaTMaxEstimator.FALLBACK_LIVE_ADAPTIVE,
                        true,
                        0.6
                );
        try (LiveRuntimeTraceWriter writer = new LiveRuntimeTraceWriter(
                runDir,
                "harness",
                1
        )) {
            writer.writeRuntime(
                    1_000_000_000L,
                    1,
                    LiveGaExecutionState.RESULT_READY_WITHIN_BOUND,
                    "HARNESS",
                    "snapshot_trace_separated",
                    1.0,
                    1,
                    1,
                    false,
                    true,
                    0.6,
                    0.4,
                    false,
                    false,
                    "",
                    "",
                    new LiveRuntimeTraceDetails(
                            0.4,
                            0.4,
                            0.0,
                            1_400_000_000L,
                            false,
                            0L,
                            0L,
                            0,
                            0,
                            submission,
                            postCompletion
                    )
            );
        }
        String csv = Files.readString(
                runDir.resolve("live-maga-runtime/live_ga_runtime_trace.csv"),
                StandardCharsets.UTF_8
        );
        require(
                csv.contains("submissionAdaptiveDeltaTMaxSelectedSeconds"),
                "runtime trace submission snapshot header"
        );
        require(
                csv.contains("postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds"),
                "runtime trace post-completion header"
        );
        require(
                csv.contains("0.400000000") && csv.contains("0.600000000"),
                "runtime trace contains distinct submission and next-submission values"
        );

        Path reportingDir = output.resolve("reporting-separated-run");
        TemporalStepResult step = step("reporting_step", 1.0, 0, 0.06, 0.4);
        try (LiveNativeReportingCollector collector = new LiveNativeReportingCollector(
                reportingDir,
                "HarnessScenario",
                "harness",
                "STANDARD",
                "bridge",
                "synthetic",
                "optimization",
                "reuse",
                "cell",
                "wall-clock"
        )) {
            String jobId = collector.nextJobId();
            collector.recordSubmitted(
                    jobId,
                    0,
                    "HARNESS",
                    1_000_000_000L,
                    100_000_000L,
                    "reporting_step",
                    1.0,
                    1,
                    1,
                    0.4,
                    500_000_000L,
                    submission.getMode().name(),
                    submission.getEstimateSeconds(),
                    submission.getSampleCount(),
                    submission.getP95Seconds(),
                    submission.getTargetSeconds(),
                    submission.getClampedSeconds(),
                    submission.getPreviousSeconds(),
                    submission.getUpdatedSeconds(),
                    submission.getFallbackReason()
            );
            collector.recordPostCompletionDeltaTMaxTelemetry(
                    jobId,
                    postCompletion.isSampleAccepted(),
                    postCompletion.getSampleRuntimeSeconds(),
                    postCompletion.getSampleCount(),
                    postCompletion.getP95Seconds(),
                    postCompletion.getTargetSeconds(),
                    postCompletion.getClampedSeconds(),
                    postCompletion.getPreviousSeconds(),
                    postCompletion.getUpdatedSeconds(),
                    postCompletion.getFallbackReason()
            );
            collector.recordCompletedWithinBound(
                    jobId,
                    step,
                    200_000_000L,
                    0.6,
                    0.4,
                    0.0
            );
            collector.recordApplied(jobId, step, 1_000_000_000L);
        }
        String jsonl = Files.readString(
                reportingDir.resolve("live-reporting/live_ga_job_events.jsonl"),
                StandardCharsets.UTF_8
        );
        String appliedCsv = Files.readString(
                reportingDir.resolve("live-reporting/live_applied_window_records.csv"),
                StandardCharsets.UTF_8
        );
        require(
                jsonl.contains("submissionAdaptiveDeltaTMaxSelectedSeconds"),
                "JSONL contains submission adaptive snapshot"
        );
        require(
                jsonl.contains("postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds"),
                "JSONL contains post-completion adaptive update"
        );
        require(
                appliedCsv.contains("submissionAdaptiveDeltaTMaxSelectedSeconds")
                        && appliedCsv.contains("postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds"),
                "window CSV contains both adaptive telemetry moments"
        );
    }

    private static LiveAdaptiveDeltaTMaxEstimator estimator(
            double initial,
            double minimum,
            double maximum,
            int historySize,
            int warmupSamples,
            double margin,
            double stepUp,
            double stepDown
    ) {
        return new LiveAdaptiveDeltaTMaxEstimator(
                new LiveAdaptiveDeltaTMaxEstimator.Config(
                        initial,
                        minimum,
                        maximum,
                        historySize,
                        warmupSamples,
                        margin,
                        stepUp,
                        stepDown
                )
        );
    }

    private static LiveGaOverrunDeadlinePolicy adaptivePolicy(
            TemporalWindowConfig temporal,
            LiveAdaptiveDeltaTMaxEstimator estimator
    ) {
        return new LiveGaOverrunDeadlinePolicy(
                temporal,
                MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC).getMobilityConfig(),
                LiveDeltaTMaxMode.LIVE_ADAPTIVE,
                estimator
        );
    }

    private static TemporalWindowState stateWithMetrics(
            TemporalWindowState state,
            TemporalOperationalMetrics metrics
    ) {
        return new TemporalWindowState(
                state.getWindowIndex(),
                state.getCurrentTimeSeconds(),
                state.getNextScheduledTimeSeconds(),
                state.getCurrentWindowDurationSeconds(),
                state.getLastSnapshot(),
                state.getLastResult(),
                metrics,
                state.getLastFinalPopulation()
        );
    }

    private static TemporalWindowBounds coreBounds(
            TemporalWindowConfig temporal,
            SystemSnapshot snapshot,
            TemporalWindowState state
    ) {
        return new TemporalWindowBoundsCalculator(
                temporal,
                new CoverageReferenceCalculator(
                        MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC)
                                .getMobilityConfig()
                )
        ).compute(
                snapshot,
                state.getLastOperationalMetrics(),
                state.getCurrentWindowDurationSeconds()
        );
    }

    private static LiveGaJob job(
            String id,
            double deltaTMax,
            long submissionNs,
            long deadlineNs,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot
    ) {
        return new LiveGaJob(
                id,
                0,
                0L,
                submissionNs,
                "HARNESS",
                fixtureSnapshot(id + "_snapshot", 1.0),
                TemporalWindowState.initial(0.0, 1.0),
                deltaTMax,
                deadlineNs,
                snapshot
        );
    }

    private static TemporalStepResult step(
            String snapshotId,
            double timeSeconds,
            int windowIndex,
            double deltaTMinSeconds,
            double deltaTMaxSeconds
    ) {
        SystemSnapshot snapshot = fixtureSnapshot(snapshotId, timeSeconds);
        EvaluationBreakdown evaluation = new EvaluationBreakdown(
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        MaGaResult result = new MaGaResult(
                snapshotId,
                timeSeconds,
                new Chromosome(List.of(), 1.0),
                evaluation,
                1,
                StopReason.EMPTY_TASK_SET,
                1.0,
                1.0,
                List.of(),
                List.of(new Chromosome(List.of(), 1.0))
        );
        DynamicityBreakdown dynamicity = DynamicityBreakdown.firstRun(
                snapshotId,
                timeSeconds
        );
        TemporalWindowBounds bounds = new TemporalWindowBounds(
                deltaTMinSeconds,
                deltaTMaxSeconds,
                0.0,
                false,
                deltaTMaxSeconds,
                deltaTMaxSeconds,
                0.01,
                0.0,
                TemporalMinimumBoundMode.CONFIGURED_GA_ESTIMATE,
                TemporalMaximumBoundMode.LIVE_RUNTIME_OVERRIDE,
                false
        );
        PopulationReuseDecision reuseDecision = new PopulationReuseDecision(
                dynamicity.getSuggestedReuseMode(),
                PopulationReuseMode.FIRST_RUN,
                WindowPerformanceSignal.UNKNOWN,
                false,
                false,
                "Harness fixed reuse."
        );
        return new TemporalStepResult(
                windowIndex,
                windowIndex == 0
                        ? ReoptimizationTrigger.firstRun(timeSeconds)
                        : ReoptimizationTrigger.scheduledExpiration(timeSeconds),
                0.0,
                timeSeconds,
                snapshot,
                dynamicity,
                reuseDecision,
                AdaptiveWindowDecision.fixed(
                        1.0,
                        bounds,
                        dynamicity.getDynamicityLevel(),
                        "Harness fixed bounds."
                ),
                TemporalOperationalMetrics.estimated(0.0, 0.01, 0.05, 1.0E-6)
                        .withMaximumWindowOverrideSeconds(deltaTMaxSeconds),
                0,
                0,
                result
        );
    }

    private static SystemSnapshot fixtureSnapshot(String snapshotId, double timeSeconds) {
        return new SystemSnapshot(
                snapshotId,
                timeSeconds,
                List.of(new VehicleSnapshot(
                        "veh_0",
                        0.0,
                        0.0,
                        0.0,
                        1_000_000_000.0
                )),
                List.of(),
                List.of(new NodeCandidate(
                        "local_for_veh_0",
                        "veh_0",
                        "veh_0",
                        NodeType.LOCAL,
                        1_000_000_000.0,
                        0.0,
                        0.0,
                        null,
                        null,
                        null,
                        null
                ))
        );
    }

    private static MaGaLiveRuntimeConfig loadConfig(
            Path output,
            String name,
            String extraFields
    ) throws Exception {
        Path dir = output.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve(MaGaLiveRuntimeConfig.CONFIG_FILE_NAME),
                configJson(extraFields),
                StandardCharsets.UTF_8
        );
        return MaGaLiveRuntimeConfig.load(dir.toFile());
    }

    private static void expectInvalidConfig(
            Path output,
            String name,
            String extraFields,
            String expectedMessagePart
    ) throws Exception {
        try {
            loadConfig(output, name, extraFields);
            throw new AssertionError("Expected invalid config: " + name);
        } catch (IllegalArgumentException e) {
            require(
                    e.getMessage().contains(expectedMessagePart),
                    "invalid config message mentions " + expectedMessagePart
            );
        }
    }

    private static void expectInvalidEstimatorConfig(
            double initial,
            double minimum,
            double maximum,
            String expectedMessagePart
    ) {
        try {
            estimator(initial, minimum, maximum, 4, 1, 0.0, 0.2, 0.2);
            throw new AssertionError(
                    "Expected invalid estimator config: " + expectedMessagePart
            );
        } catch (IllegalArgumentException e) {
            require(
                    e.getMessage().contains(expectedMessagePart),
                    "invalid estimator config message mentions " + expectedMessagePart
            );
        }
    }

    private static void expectRuntimeBoundConflict(
            LiveAdaptiveDeltaTMaxEstimator estimator,
            double deltaTMinSeconds,
            String message
    ) {
        try {
            estimator.estimateForSubmission(deltaTMinSeconds);
            throw new AssertionError("Expected runtime bound conflict: " + message);
        } catch (LiveAdaptiveDeltaTMaxEstimator.BoundConflictException e) {
            require(
                    e.getMessage().contains("BOUND_CONFLICT"),
                    message + " reports BOUND_CONFLICT"
            );
        } catch (IllegalArgumentException e) {
            require(
                    e.getMessage().contains("deltaTMinSeconds"),
                    message + " reports invalid deltaTMin"
            );
        }
    }

    private static String adaptiveJsonFields(
            double initial,
            double minimum,
            double maximum,
            int historySize,
            int warmupSamples,
            double margin,
            double stepUp,
            double stepDown
    ) {
        return "\"deltaTMaxMode\": \"LIVE_ADAPTIVE\","
                + "\"configuredInitialDeltaTMaxSeconds\": " + initial + ","
                + "\"adaptiveDeltaTMaxMinimumSeconds\": " + minimum + ","
                + "\"adaptiveDeltaTMaxMaximumSeconds\": " + maximum + ","
                + "\"adaptiveDeltaTMaxHistorySize\": " + historySize + ","
                + "\"adaptiveDeltaTMaxWarmupSamples\": " + warmupSamples + ","
                + "\"adaptiveDeltaTMaxSafetyMarginSeconds\": " + margin + ","
                + "\"adaptiveDeltaTMaxMaximumStepUpSeconds\": " + stepUp + ","
                + "\"adaptiveDeltaTMaxMaximumStepDownSeconds\": " + stepDown + ",";
    }

    private static String adaptiveJsonFieldsWithRuntimeEstimate(
            double initial,
            double minimum,
            double maximum,
            int historySize,
            int warmupSamples,
            double margin,
            double stepUp,
            double stepDown,
            double configuredGaRuntimeEstimateSeconds
    ) {
        return "\"configuredGaRuntimeEstimateSeconds\": " + configuredGaRuntimeEstimateSeconds + ","
                + adaptiveJsonFields(
                initial,
                minimum,
                maximum,
                historySize,
                warmupSamples,
                margin,
                stepUp,
                stepDown
        );
    }

    private static String configJson(String extraFields) {
        String optional = extraFields == null || extraFields.isBlank()
                ? ""
                : extraFields + "\n";
        return "{\n"
                + "  \"scenarioName\": \"HarnessScenario\",\n"
                + "  \"coordinatorTickIntervalMs\": 100,\n"
                + "  \"initialOptimizationDelayMs\": 100,\n"
                + "  \"gaPollingIntervalMs\": 50,\n"
                + "  \"singleInFlightGaOnly\": true,\n"
                + "  \"discardLateResult\": true,\n"
                + "  \"keepLastAppliedStrategyWhileRunning\": true,\n"
                + "  \"freshReoptimizationAfterTimeout\": true,\n"
                + "  \"runtimeTraceEnabled\": false,\n"
                + "  \"diagnosticArtificialGaDelayMs\": 0,\n"
                + "  \"temporalInitialWindowSeconds\": 1.0,\n"
                + "  \"configuredGaRuntimeEstimateSeconds\": 0.01,\n"
                + "  \"configuredMaxWindowSeconds\": 0.2,\n"
                + "  \"deltaTMaxComparisonEpsilonSeconds\": 0.001,\n"
                + "  \"publishedSnapshotCopyLimit\": 1,\n"
                + "  \"nativeLiveDetailedReportingEnabled\": true,\n"
                + "  \"nativeLiveDetailedReportPrintToConsole\": false,\n"
                + "  " + optional
                + "  \"gaParameterScalingMode\": \"STATIC\"\n"
                + "}\n";
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double actual, double expected, String message) {
        require(
                Math.abs(actual - expected) <= 1.0E-9,
                message + " expected=" + expected + " actual=" + actual
        );
    }
}
