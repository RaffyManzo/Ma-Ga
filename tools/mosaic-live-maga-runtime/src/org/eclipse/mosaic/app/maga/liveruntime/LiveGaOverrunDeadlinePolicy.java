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

    LiveGaOverrunDeadlinePolicy(
            TemporalWindowConfig windowConfig,
            MobilityConfig mobilityConfig
    ) {
        this.windowConfig = windowConfig;
        this.boundsCalculator = new TemporalWindowBoundsCalculator(
                windowConfig,
                new CoverageReferenceCalculator(mobilityConfig)
        );
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
        TemporalWindowBounds bounds = boundsCalculator.compute(
                snapshot,
                metrics,
                state.getCurrentWindowDurationSeconds()
        );
        double deltaTMax = bounds.getMaximumWindowSeconds();
        long deadlineNs = submissionWallClockNs + Math.round(deltaTMax * NANOSECONDS_PER_SECOND);
        return new LiveGaDeadline(deltaTMax, deadlineNs);
    }

    static final class LiveGaDeadline {
        private final double deltaTMaxAtSubmissionSeconds;
        private final long wallClockDeadlineNs;

        LiveGaDeadline(double deltaTMaxAtSubmissionSeconds, long wallClockDeadlineNs) {
            this.deltaTMaxAtSubmissionSeconds = deltaTMaxAtSubmissionSeconds;
            this.wallClockDeadlineNs = wallClockDeadlineNs;
        }

        double getDeltaTMaxAtSubmissionSeconds() {
            return deltaTMaxAtSubmissionSeconds;
        }

        long getWallClockDeadlineNs() {
            return wallClockDeadlineNs;
        }
    }
}
