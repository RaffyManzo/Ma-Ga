package org.eclipse.mosaic.app.maga.liveruntime;

import window.state.TemporalStepResult;

final class LiveGaCompletion {
    private final LiveGaJob job;
    private final TemporalStepResult stepResult;
    private final Throwable error;
    private final double wallClockRuntimeSeconds;
    private final double deltaTMaxSeconds;

    private LiveGaCompletion(
            LiveGaJob job,
            TemporalStepResult stepResult,
            Throwable error,
            double wallClockRuntimeSeconds,
            double deltaTMaxSeconds
    ) {
        this.job = job;
        this.stepResult = stepResult;
        this.error = error;
        this.wallClockRuntimeSeconds = wallClockRuntimeSeconds;
        this.deltaTMaxSeconds = deltaTMaxSeconds;
    }

    static LiveGaCompletion success(
            LiveGaJob job,
            TemporalStepResult stepResult,
            double wallClockRuntimeSeconds
    ) {
        double deltaTMax = stepResult == null || stepResult.getAdaptiveWindowDecision() == null
                ? 0.0
                : stepResult.getAdaptiveWindowDecision().getBounds().getMaximumWindowSeconds();
        return new LiveGaCompletion(job, stepResult, null, wallClockRuntimeSeconds, deltaTMax);
    }

    static LiveGaCompletion failure(
            LiveGaJob job,
            Throwable error,
            double wallClockRuntimeSeconds
    ) {
        return new LiveGaCompletion(job, null, error, wallClockRuntimeSeconds, 0.0);
    }

    LiveGaJob getJob() {
        return job;
    }

    TemporalStepResult getStepResult() {
        return stepResult;
    }

    Throwable getError() {
        return error;
    }

    double getWallClockRuntimeSeconds() {
        return wallClockRuntimeSeconds;
    }

    double getDeltaTMaxSeconds() {
        return deltaTMaxSeconds;
    }

    boolean hasError() {
        return error != null;
    }

    boolean isStale() {
        return deltaTMaxSeconds > 0.0 && wallClockRuntimeSeconds > deltaTMaxSeconds;
    }
}
