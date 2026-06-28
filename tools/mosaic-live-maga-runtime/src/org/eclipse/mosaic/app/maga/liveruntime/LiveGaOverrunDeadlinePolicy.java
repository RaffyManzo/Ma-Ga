package org.eclipse.mosaic.app.maga.liveruntime;

import config.mobility.MobilityConfig;
import config.window.TemporalWindowConfig;
import model.snapshot.SystemSnapshot;
import window.state.TemporalWindowState;
import window.timing.CoverageReferenceCalculator;
import window.timing.TemporalOperationalMetrics;
import window.timing.TemporalWindowBounds;
import window.timing.TemporalWindowBoundsCalculator;

/** Separates temporal-window bounds from the GA wall-clock budget. */
final class LiveGaOverrunDeadlinePolicy {

    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000.0;

    private final TemporalWindowConfig windowConfig;
    private final TemporalWindowBoundsCalculator boundsCalculator;
    private final LiveDeltaTMaxMode wallClockBudgetMode;
    private final LiveAdaptiveDeltaTMaxEstimator adaptiveEstimator;
    private final double configuredStaticWallClockBudgetSeconds;
    private final double configuredMaxSnapshotAgeSimulationSeconds;
    private final double cooperativeFinalizationReserveSeconds;

    LiveGaOverrunDeadlinePolicy(
            TemporalWindowConfig windowConfig,
            MobilityConfig mobilityConfig
    ) {
        this(windowConfig, mobilityConfig, LiveDeltaTMaxMode.CONFIGURED_STATIC,
                null, windowConfig.getConfiguredMaxWindowSeconds(),
                windowConfig.getConfiguredMaxWindowSeconds(), 0.0);
    }

    /** Legacy constructor retained for old harnesses. */
    LiveGaOverrunDeadlinePolicy(
            TemporalWindowConfig windowConfig,
            MobilityConfig mobilityConfig,
            LiveDeltaTMaxMode mode,
            LiveAdaptiveDeltaTMaxEstimator estimator
    ) {
        this(windowConfig, mobilityConfig, mode, estimator,
                windowConfig.getConfiguredMaxWindowSeconds(),
                windowConfig.getConfiguredMaxWindowSeconds(), 0.0);
    }

    LiveGaOverrunDeadlinePolicy(
            TemporalWindowConfig windowConfig,
            MobilityConfig mobilityConfig,
            LiveDeltaTMaxMode mode,
            LiveAdaptiveDeltaTMaxEstimator estimator,
            double configuredStaticWallClockBudgetSeconds,
            double configuredMaxSnapshotAgeSimulationSeconds
    ) {
        this(windowConfig, mobilityConfig, mode, estimator,
                configuredStaticWallClockBudgetSeconds,
                configuredMaxSnapshotAgeSimulationSeconds, 0.0);
    }

    LiveGaOverrunDeadlinePolicy(
            TemporalWindowConfig windowConfig,
            MobilityConfig mobilityConfig,
            LiveDeltaTMaxMode mode,
            LiveAdaptiveDeltaTMaxEstimator estimator,
            double configuredStaticWallClockBudgetSeconds,
            double configuredMaxSnapshotAgeSimulationSeconds,
            double cooperativeFinalizationReserveSeconds
    ) {
        this.windowConfig = windowConfig;
        this.boundsCalculator = new TemporalWindowBoundsCalculator(
                windowConfig, new CoverageReferenceCalculator(mobilityConfig)
        );
        this.wallClockBudgetMode = mode == null
                ? LiveDeltaTMaxMode.CONFIGURED_STATIC : mode;
        if (this.wallClockBudgetMode == LiveDeltaTMaxMode.LIVE_ADAPTIVE
                && estimator == null) {
            throw new IllegalArgumentException(
                    "adaptiveEstimator must be provided in LIVE_ADAPTIVE mode."
            );
        }
        validatePositive("configuredStaticWallClockBudgetSeconds",
                configuredStaticWallClockBudgetSeconds);
        validatePositive("configuredMaxSnapshotAgeSimulationSeconds",
                configuredMaxSnapshotAgeSimulationSeconds);
        validateFiniteAndNonNegative("cooperativeFinalizationReserveSeconds",
                cooperativeFinalizationReserveSeconds);
        this.adaptiveEstimator = estimator;
        this.configuredStaticWallClockBudgetSeconds =
                configuredStaticWallClockBudgetSeconds;
        this.configuredMaxSnapshotAgeSimulationSeconds =
                configuredMaxSnapshotAgeSimulationSeconds;
        this.cooperativeFinalizationReserveSeconds =
                cooperativeFinalizationReserveSeconds;
    }

    TemporalOperationalMetrics initialOperationalMetrics() {
        return TemporalOperationalMetrics.estimated(
                windowConfig.getDataCollectionDelaySeconds(),
                windowConfig.getDefaultGaRuntimeEstimateSeconds(),
                windowConfig.getStrategyApplicationSeconds(),
                windowConfig.getEpsilonT()
        );
    }

    LiveGaDeadline computeDeadline(
            SystemSnapshot snapshot,
            TemporalWindowState state,
            long submissionWallClockNs
    ) {
        TemporalOperationalMetrics metrics = state.getLastOperationalMetrics() == null
                ? initialOperationalMetrics() : state.getLastOperationalMetrics();

        LiveAdaptiveDeltaTMaxEstimator.Snapshot estimatorSnapshot;
        double wallClockBudgetSeconds;
        if (wallClockBudgetMode == LiveDeltaTMaxMode.LIVE_ADAPTIVE) {
            estimatorSnapshot = adaptiveEstimator.estimateForSubmission();
            wallClockBudgetSeconds = estimatorSnapshot.getUpdatedSeconds();
        } else {
            wallClockBudgetSeconds = configuredStaticWallClockBudgetSeconds;
            estimatorSnapshot = LiveAdaptiveDeltaTMaxEstimator.Snapshot
                    .configuredStatic(wallClockBudgetSeconds);
        }

        // One robust estimate feeds T_GA_est in DeltaT_min and the wall budget.
        TemporalOperationalMetrics metricsAtSubmission =
                metrics.withGaRuntimeEstimateSeconds(wallClockBudgetSeconds);
        TemporalWindowBounds bounds = boundsCalculator.compute(
                snapshot, metricsAtSubmission,
                state.getCurrentWindowDurationSeconds()
        );
        double temporalMaximumSeconds = bounds.getMaximumWindowSeconds();
        double freshnessCapSeconds = Math.min(
                configuredMaxSnapshotAgeSimulationSeconds,
                temporalMaximumSeconds
        );
        long deadlineNs = submissionWallClockNs
                + Math.round(wallClockBudgetSeconds * NANOSECONDS_PER_SECOND);
        double effectiveReserveSeconds = Math.min(
                cooperativeFinalizationReserveSeconds,
                wallClockBudgetSeconds * 0.50
        );
        double cooperativeSearchSeconds = Math.max(
                0.001,
                wallClockBudgetSeconds - effectiveReserveSeconds
        );
        long cooperativeDeadlineNs = submissionWallClockNs
                + Math.round(cooperativeSearchSeconds * NANOSECONDS_PER_SECOND);

        return new LiveGaDeadline(
                temporalMaximumSeconds,
                wallClockBudgetSeconds,
                freshnessCapSeconds,
                cooperativeDeadlineNs,
                deadlineNs,
                estimatorSnapshot,
                metricsAtSubmission
        );
    }

    LiveAdaptiveDeltaTMaxEstimator.Snapshot recordCompletedRuntime(
            double runtimeSeconds,
            int taskCount
    ) {
        if (wallClockBudgetMode != LiveDeltaTMaxMode.LIVE_ADAPTIVE) { return null; }
        return adaptiveEstimator.recordCompletedRuntime(runtimeSeconds, taskCount);
    }

    /** Legacy V3-B API. */
    LiveAdaptiveDeltaTMaxEstimator.Snapshot recordCompletedRuntime(
            double runtimeSeconds,
            double deltaTMinSeconds
    ) {
        if (wallClockBudgetMode != LiveDeltaTMaxMode.LIVE_ADAPTIVE) { return null; }
        return adaptiveEstimator.recordCompletedRuntime(runtimeSeconds, deltaTMinSeconds);
    }

    private static void validateFiniteAndNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0.");
        }
    }

    private static void validatePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and > 0.");
        }
    }

    static final class LiveGaDeadline {
        private final double temporalMaximumAtSubmissionSeconds;
        private final double gaWallClockBudgetAtSubmissionSeconds;
        private final double maxSnapshotAgeSimulationSeconds;
        private final long cooperativeStopDeadlineNs;
        private final long wallClockDeadlineNs;
        private final LiveAdaptiveDeltaTMaxEstimator.Snapshot estimatorSnapshot;
        private final TemporalOperationalMetrics metricsAtSubmission;

        LiveGaDeadline(
                double temporalMaximumAtSubmissionSeconds,
                double gaWallClockBudgetAtSubmissionSeconds,
                double maxSnapshotAgeSimulationSeconds,
                long cooperativeStopDeadlineNs,
                long wallClockDeadlineNs,
                LiveAdaptiveDeltaTMaxEstimator.Snapshot estimatorSnapshot,
                TemporalOperationalMetrics metricsAtSubmission
        ) {
            this.temporalMaximumAtSubmissionSeconds = temporalMaximumAtSubmissionSeconds;
            this.gaWallClockBudgetAtSubmissionSeconds = gaWallClockBudgetAtSubmissionSeconds;
            this.maxSnapshotAgeSimulationSeconds = maxSnapshotAgeSimulationSeconds;
            this.cooperativeStopDeadlineNs = cooperativeStopDeadlineNs;
            this.wallClockDeadlineNs = wallClockDeadlineNs;
            this.estimatorSnapshot = estimatorSnapshot;
            this.metricsAtSubmission = metricsAtSubmission;
        }

        double getDeltaTMaxAtSubmissionSeconds() {
            return temporalMaximumAtSubmissionSeconds;
        }
        double getTemporalMaximumAtSubmissionSeconds() {
            return temporalMaximumAtSubmissionSeconds;
        }
        double getGaWallClockBudgetAtSubmissionSeconds() {
            return gaWallClockBudgetAtSubmissionSeconds;
        }
        double getMaxSnapshotAgeSimulationSeconds() {
            return maxSnapshotAgeSimulationSeconds;
        }
        long getCooperativeStopDeadlineNs() { return cooperativeStopDeadlineNs; }
        long getWallClockDeadlineNs() { return wallClockDeadlineNs; }
        LiveAdaptiveDeltaTMaxEstimator.Snapshot getDeltaTMaxSnapshot() {
            return estimatorSnapshot;
        }
        TemporalOperationalMetrics getMetricsAtSubmission() {
            return metricsAtSubmission;
        }
    }
}
