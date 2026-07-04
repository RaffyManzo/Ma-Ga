package org.eclipse.mosaic.app.maga.liveruntime;

import window.state.TemporalStepResult;

final class LiveGaCompletion {
    private final LiveGaJob job;
    private final TemporalStepResult stepResult;
    private final Throwable error;
    private final long completionWallClockNs;
    private final long completionWallClockEpochMillis;
    private final double wallClockRuntimeSeconds;
    private final double threadCpuMillis;
    private final double deltaTMaxSeconds;
    private final double deltaTMaxMismatchSeconds;

    private LiveGaCompletion(
            LiveGaJob job,
            TemporalStepResult stepResult,
            Throwable error,
            long completionWallClockNs,
            long completionWallClockEpochMillis,
            double wallClockRuntimeSeconds,
            double threadCpuMillis,
            double deltaTMaxSeconds,
            double deltaTMaxMismatchSeconds
    ) {
        this.job = job;
        this.stepResult = stepResult;
        this.error = error;
        this.completionWallClockNs = completionWallClockNs;
        this.completionWallClockEpochMillis = completionWallClockEpochMillis;
        this.wallClockRuntimeSeconds = wallClockRuntimeSeconds;
        this.threadCpuMillis = threadCpuMillis;
        this.deltaTMaxSeconds = deltaTMaxSeconds;
        this.deltaTMaxMismatchSeconds = deltaTMaxMismatchSeconds;
    }

    static LiveGaCompletion success(
            LiveGaJob job,
            TemporalStepResult stepResult,
            long completionWallClockNs,
            long completionWallClockEpochMillis,
            double wallClockRuntimeSeconds,
            double threadCpuMillis
    ) {
        double deltaTMax = stepResult == null || stepResult.getAdaptiveWindowDecision() == null
                ? 0.0
                : stepResult.getAdaptiveWindowDecision().getBounds().getMaximumWindowSeconds();
        double mismatch = Math.abs(job.getDeltaTMaxAtSubmissionSeconds() - deltaTMax);
        return new LiveGaCompletion(
                job,
                stepResult,
                null,
                completionWallClockNs,
                completionWallClockEpochMillis,
                wallClockRuntimeSeconds,
                threadCpuMillis,
                deltaTMax,
                mismatch
        );
    }

    static LiveGaCompletion failure(
            LiveGaJob job,
            Throwable error,
            long completionWallClockNs,
            long completionWallClockEpochMillis,
            double wallClockRuntimeSeconds,
            double threadCpuMillis
    ) {
        return new LiveGaCompletion(
                job,
                null,
                error,
                completionWallClockNs,
                completionWallClockEpochMillis,
                wallClockRuntimeSeconds,
                threadCpuMillis,
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

    long getCompletionWallClockNs() {
        return completionWallClockNs;
    }

    long getCompletionWallClockEpochMillis() {
        return completionWallClockEpochMillis;
    }

    double getWallClockRuntimeSeconds() {
        return wallClockRuntimeSeconds;
    }

    double getThreadCpuMillis() {
        return threadCpuMillis;
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
