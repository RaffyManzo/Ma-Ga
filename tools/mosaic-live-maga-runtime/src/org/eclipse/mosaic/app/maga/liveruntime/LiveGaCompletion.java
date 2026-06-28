package org.eclipse.mosaic.app.maga.liveruntime;

import window.state.TemporalStepResult;

final class LiveGaCompletion {
    private static final double NS_PER_SECOND = 1_000_000_000.0;

    private final LiveGaJob job;
    private final TemporalStepResult stepResult;
    private final Throwable error;
    private final long completionWallClockNs;
    private final double wallClockRuntimeSeconds;
    private final double temporalMaximumSeconds;
    private final double temporalMaximumMismatchSeconds;

    private LiveGaCompletion(
            LiveGaJob job, TemporalStepResult stepResult, Throwable error,
            long completionWallClockNs, double wallClockRuntimeSeconds,
            double temporalMaximumSeconds, double temporalMaximumMismatchSeconds
    ) {
        this.job = job;
        this.stepResult = stepResult;
        this.error = error;
        this.completionWallClockNs = completionWallClockNs;
        this.wallClockRuntimeSeconds = wallClockRuntimeSeconds;
        this.temporalMaximumSeconds = temporalMaximumSeconds;
        this.temporalMaximumMismatchSeconds = temporalMaximumMismatchSeconds;
    }

    static LiveGaCompletion success(
            LiveGaJob job, TemporalStepResult stepResult,
            long completionWallClockNs, double wallClockRuntimeSeconds
    ) {
        double maximum = stepResult == null || stepResult.getAdaptiveWindowDecision() == null
                ? 0.0
                : stepResult.getAdaptiveWindowDecision().getBounds().getMaximumWindowSeconds();
        return new LiveGaCompletion(
                job, stepResult, null, completionWallClockNs,
                wallClockRuntimeSeconds, maximum,
                Math.abs(job.getTemporalMaximumAtSubmissionSeconds() - maximum)
        );
    }

    static LiveGaCompletion failure(
            LiveGaJob job, Throwable error, long completionWallClockNs,
            double wallClockRuntimeSeconds
    ) {
        return new LiveGaCompletion(
                job, null, error, completionWallClockNs,
                wallClockRuntimeSeconds, 0.0,
                job == null ? 0.0 : job.getTemporalMaximumAtSubmissionSeconds()
        );
    }

    LiveGaJob getJob() { return job; }
    TemporalStepResult getStepResult() { return stepResult; }
    Throwable getError() { return error; }
    long getCompletionWallClockNs() { return completionWallClockNs; }
    double getWallClockRuntimeSeconds() { return wallClockRuntimeSeconds; }
    double getDeltaTMaxSeconds() { return temporalMaximumSeconds; }
    double getDeltaTMaxMismatchSeconds() { return temporalMaximumMismatchSeconds; }
    boolean hasError() { return error != null; }

    boolean isWallClockStale() {
        return job.isTimeoutDetectedBeforeCompletion()
                || (job.getGaWallClockBudgetAtSubmissionSeconds() > 0.0
                && wallClockRuntimeSeconds > job.getGaWallClockBudgetAtSubmissionSeconds());
    }

    double snapshotAgeAtSimulationTime(long simulationTimeNs) {
        if (job == null || job.getSnapshot() == null) { return 0.0; }
        return Math.max(0.0,
                simulationTimeNs / NS_PER_SECOND - job.getSnapshot().getTimeSeconds());
    }

    boolean isSimulationAgeStale(long simulationTimeNs) {
        return snapshotAgeAtSimulationTime(simulationTimeNs)
                > job.getMaxSnapshotAgeSimulationSeconds() + 1.0E-9;
    }

    LiveStaleReason classify(long simulationTimeNs) {
        return LiveStaleReason.of(
                isWallClockStale(), isSimulationAgeStale(simulationTimeNs)
        );
    }

    /** Legacy V3-B check retained for old harnesses. */
    boolean isStale() { return isWallClockStale(); }
}
