package org.eclipse.mosaic.app.maga.liveruntime;

import window.state.TemporalStepResult;

final class LiveGaCompletion {
    private final LiveGaJob job;
    private final TemporalStepResult stepResult;
    private final Throwable error;
    private final double wallClockRuntimeSeconds;
    private final double deltaTMaxSeconds;
    private final double deltaTMaxMismatchSeconds;

    private LiveGaCompletion(
            LiveGaJob job,
            TemporalStepResult stepResult,
            Throwable error,
            double wallClockRuntimeSeconds,
            double deltaTMaxSeconds,
            double deltaTMaxMismatchSeconds
    ) {
        this.job = job;
        this.stepResult = stepResult;
        this.error = error;
        this.wallClockRuntimeSeconds = wallClockRuntimeSeconds;
        this.deltaTMaxSeconds = deltaTMaxSeconds;
        this.deltaTMaxMismatchSeconds = deltaTMaxMismatchSeconds;
    }

    static LiveGaCompletion success(
            LiveGaJob job,
            TemporalStepResult stepResult,
            double wallClockRuntimeSeconds
    ) {
        double deltaTMax = stepResult == null || stepResult.getAdaptiveWindowDecision() == null
                ? 0.0
                : stepResult.getAdaptiveWindowDecision().getBounds().getMaximumWindowSeconds();
        double mismatch = Math.abs(job.getDeltaTMaxAtSubmissionSeconds() - deltaTMax);
        return new LiveGaCompletion(job, stepResult, null, wallClockRuntimeSeconds, deltaTMax, mismatch);
    }

    static LiveGaCompletion failure(
            LiveGaJob job,
            Throwable error,
            double wallClockRuntimeSeconds
    ) {
        return new LiveGaCompletion(
                job,
                null,
                error,
                wallClockRuntimeSeconds,
                0.0,
                job == null ? 0.0 : job.getDeltaTMaxAtSubmissionSeconds()
        );
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

    double getDeltaTMaxMismatchSeconds() {
        return deltaTMaxMismatchSeconds;
    }

    boolean hasError() {
        return error != null;
    }

    boolean isStale() {
        return job.isTimeoutDetectedBeforeCompletion()
                || (job.getDeltaTMaxAtSubmissionSeconds() > 0.0
                && wallClockRuntimeSeconds > job.getDeltaTMaxAtSubmissionSeconds());
    }
}
