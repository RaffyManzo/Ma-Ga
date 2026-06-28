package org.eclipse.mosaic.app.maga.liveruntime;

import config.mobility.MobilityConfig;
import config.window.TemporalWindowConfig;
import model.snapshot.SystemSnapshot;
import window.state.TemporalWindowState;
import window.timing.CoverageReferenceCalculator;
import window.timing.TemporalOperationalMetrics;
import window.timing.TemporalWindowBounds;
import window.timing.TemporalWindowBoundsCalculator;

final class LiveGaOverrunDeadlinePolicy {

    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000.0;

    private final TemporalWindowConfig windowConfig;
    private final TemporalWindowBoundsCalculator boundsCalculator;
    private final LiveDeltaTMaxMode deltaTMaxMode;
    private final LiveAdaptiveDeltaTMaxEstimator adaptiveEstimator;

    LiveGaOverrunDeadlinePolicy(
            TemporalWindowConfig windowConfig,
            MobilityConfig mobilityConfig
    ) {
        this(
                windowConfig,
                mobilityConfig,
                LiveDeltaTMaxMode.CONFIGURED_STATIC,
                null
        );
    }

    LiveGaOverrunDeadlinePolicy(
            TemporalWindowConfig windowConfig,
            MobilityConfig mobilityConfig,
            LiveDeltaTMaxMode deltaTMaxMode,
            LiveAdaptiveDeltaTMaxEstimator adaptiveEstimator
    ) {
        this.windowConfig = windowConfig;
        this.boundsCalculator = new TemporalWindowBoundsCalculator(
                windowConfig,
                new CoverageReferenceCalculator(mobilityConfig)
        );
        this.deltaTMaxMode = deltaTMaxMode == null
                ? LiveDeltaTMaxMode.CONFIGURED_STATIC
                : deltaTMaxMode;
        if (this.deltaTMaxMode == LiveDeltaTMaxMode.LIVE_ADAPTIVE
                && adaptiveEstimator == null) {
            throw new IllegalArgumentException(
                    "adaptiveEstimator must be provided in LIVE_ADAPTIVE mode."
            );
        }
        this.adaptiveEstimator = adaptiveEstimator;
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
                ? initialOperationalMetrics()
                : state.getLastOperationalMetrics();
        TemporalWindowBounds bounds = null;
        LiveAdaptiveDeltaTMaxEstimator.Snapshot deltaTMaxSnapshot;
        TemporalOperationalMetrics metricsAtSubmission = metrics;

        if (deltaTMaxMode == LiveDeltaTMaxMode.LIVE_ADAPTIVE) {
            double deltaTMinSeconds = boundsCalculator.computeMinimumWindowSeconds(
                    metrics
            );
            deltaTMaxSnapshot = adaptiveEstimator.estimateForSubmission(
                    deltaTMinSeconds
            );
            metricsAtSubmission = metrics.withMaximumWindowOverrideSeconds(
                    deltaTMaxSnapshot.getUpdatedSeconds()
            );
        } else {
            bounds = boundsCalculator.compute(
                    snapshot,
                    metrics,
                    state.getCurrentWindowDurationSeconds()
            );
            deltaTMaxSnapshot = LiveAdaptiveDeltaTMaxEstimator.Snapshot
                    .configuredStatic(bounds.getMaximumWindowSeconds());
        }

        double deltaTMax = deltaTMaxSnapshot.getUpdatedSeconds();
        long deadlineNs = submissionWallClockNs + Math.round(deltaTMax * NANOSECONDS_PER_SECOND);
        return new LiveGaDeadline(
                deltaTMax,
                deadlineNs,
                deltaTMaxSnapshot,
                metricsAtSubmission
        );
    }

    LiveAdaptiveDeltaTMaxEstimator.Snapshot recordCompletedRuntime(
            double runtimeSeconds,
            double deltaTMinSeconds
    ) {
        if (deltaTMaxMode != LiveDeltaTMaxMode.LIVE_ADAPTIVE) {
            return null;
        }
        return adaptiveEstimator.recordCompletedRuntime(
                runtimeSeconds,
                deltaTMinSeconds
        );
    }

    static final class LiveGaDeadline {
        private final double deltaTMaxAtSubmissionSeconds;
        private final long wallClockDeadlineNs;
        private final LiveAdaptiveDeltaTMaxEstimator.Snapshot deltaTMaxSnapshot;
        private final TemporalOperationalMetrics metricsAtSubmission;

        LiveGaDeadline(
                double deltaTMaxAtSubmissionSeconds,
                long wallClockDeadlineNs,
                LiveAdaptiveDeltaTMaxEstimator.Snapshot deltaTMaxSnapshot,
                TemporalOperationalMetrics metricsAtSubmission
        ) {
            this.deltaTMaxAtSubmissionSeconds = deltaTMaxAtSubmissionSeconds;
            this.wallClockDeadlineNs = wallClockDeadlineNs;
            this.deltaTMaxSnapshot = deltaTMaxSnapshot;
            this.metricsAtSubmission = metricsAtSubmission;
        }

        double getDeltaTMaxAtSubmissionSeconds() {
            return deltaTMaxAtSubmissionSeconds;
        }

        long getWallClockDeadlineNs() {
            return wallClockDeadlineNs;
        }

        LiveAdaptiveDeltaTMaxEstimator.Snapshot getDeltaTMaxSnapshot() {
            return deltaTMaxSnapshot;
        }

        TemporalOperationalMetrics getMetricsAtSubmission() {
            return metricsAtSubmission;
        }
    }
}
