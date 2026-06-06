package org.eclipse.mosaic.app.maga.liveruntime.reporting;

public final class LiveGaJobRecord {
    public final String jobId;
    public final int windowIndex;
    public final String triggerType;
    public final long submissionSimulationTimeNs;
    public final long submissionWallClockNs;
    public final String snapshotId;
    public final double snapshotTimeSeconds;
    public final int taskCount;
    public final int candidateCount;
    public final double deltaTMaxAtSubmissionSeconds;
    public final long wallClockDeadlineNs;

    public boolean timeoutDetectedBeforeCompletion;
    public long waitCapDetectedWallClockNs;
    public long waitCapDetectedSimulationTimeNs;
    public long completionWallClockNs;
    public double gaRuntimeWallClockSeconds;
    public double deltaTMaxFromCompletedStepSeconds;
    public double deltaTMaxMismatchSeconds;
    public String finalStatus = "";
    public long appliedAtSimulationTimeNs;
    public String errorType = "";
    public String errorMessage = "";

    public LiveGaJobRecord(
            String jobId,
            int windowIndex,
            String triggerType,
            long submissionSimulationTimeNs,
            long submissionWallClockNs,
            String snapshotId,
            double snapshotTimeSeconds,
            int taskCount,
            int candidateCount,
            double deltaTMaxAtSubmissionSeconds,
            long wallClockDeadlineNs
    ) {
        this.jobId = jobId;
        this.windowIndex = windowIndex;
        this.triggerType = triggerType;
        this.submissionSimulationTimeNs = submissionSimulationTimeNs;
        this.submissionWallClockNs = submissionWallClockNs;
        this.snapshotId = snapshotId;
        this.snapshotTimeSeconds = snapshotTimeSeconds;
        this.taskCount = taskCount;
        this.candidateCount = candidateCount;
        this.deltaTMaxAtSubmissionSeconds = deltaTMaxAtSubmissionSeconds;
        this.wallClockDeadlineNs = wallClockDeadlineNs;
    }

    LiveGaJobRecord copy() {
        LiveGaJobRecord copy = new LiveGaJobRecord(
                jobId,
                windowIndex,
                triggerType,
                submissionSimulationTimeNs,
                submissionWallClockNs,
                snapshotId,
                snapshotTimeSeconds,
                taskCount,
                candidateCount,
                deltaTMaxAtSubmissionSeconds,
                wallClockDeadlineNs
        );
        copy.timeoutDetectedBeforeCompletion = timeoutDetectedBeforeCompletion;
        copy.waitCapDetectedWallClockNs = waitCapDetectedWallClockNs;
        copy.waitCapDetectedSimulationTimeNs = waitCapDetectedSimulationTimeNs;
        copy.completionWallClockNs = completionWallClockNs;
        copy.gaRuntimeWallClockSeconds = gaRuntimeWallClockSeconds;
        copy.deltaTMaxFromCompletedStepSeconds = deltaTMaxFromCompletedStepSeconds;
        copy.deltaTMaxMismatchSeconds = deltaTMaxMismatchSeconds;
        copy.finalStatus = finalStatus;
        copy.appliedAtSimulationTimeNs = appliedAtSimulationTimeNs;
        copy.errorType = errorType;
        copy.errorMessage = errorMessage;
        return copy;
    }
}
