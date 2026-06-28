package org.eclipse.mosaic.app.maga.liveruntime;

import model.snapshot.SystemSnapshot;
import window.state.TemporalWindowState;

final class LiveGaJob {
    private final String jobId;
    private final int windowIndex;
    private final long submissionSimulationTimeNs;
    private final long submissionWallClockNs;
    private final String triggerType;
    private final SystemSnapshot snapshot;
    private final TemporalWindowState stateAtSubmission;
    private final double temporalMaximumAtSubmissionSeconds;
    private final double gaWallClockBudgetAtSubmissionSeconds;
    private final double maxSnapshotAgeSimulationSeconds;
    private final long cooperativeStopDeadlineNs;
    private final long wallClockDeadlineNs;
    private final LiveAdaptiveDeltaTMaxEstimator.Snapshot estimatorSnapshotAtSubmission;
    private boolean timeoutDetectedBeforeCompletion;
    private long waitCapDetectedWallClockNs;
    private long waitCapDetectedSimulationTimeNs;

    LiveGaJob(
            String jobId, int windowIndex, long submissionSimulationTimeNs,
            long submissionWallClockNs, String triggerType, SystemSnapshot snapshot,
            TemporalWindowState stateAtSubmission,
            double temporalMaximumAtSubmissionSeconds,
            double gaWallClockBudgetAtSubmissionSeconds,
            double maxSnapshotAgeSimulationSeconds,
            long wallClockDeadlineNs,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot estimatorSnapshotAtSubmission
    ) {
        this(jobId, windowIndex, submissionSimulationTimeNs, submissionWallClockNs,
                triggerType, snapshot, stateAtSubmission,
                temporalMaximumAtSubmissionSeconds,
                gaWallClockBudgetAtSubmissionSeconds,
                maxSnapshotAgeSimulationSeconds,
                wallClockDeadlineNs, wallClockDeadlineNs,
                estimatorSnapshotAtSubmission);
    }

    LiveGaJob(
            String jobId, int windowIndex, long submissionSimulationTimeNs,
            long submissionWallClockNs, String triggerType, SystemSnapshot snapshot,
            TemporalWindowState stateAtSubmission,
            double temporalMaximumAtSubmissionSeconds,
            double gaWallClockBudgetAtSubmissionSeconds,
            double maxSnapshotAgeSimulationSeconds,
            long cooperativeStopDeadlineNs,
            long wallClockDeadlineNs,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot estimatorSnapshotAtSubmission
    ) {
        this.jobId = jobId;
        this.windowIndex = windowIndex;
        this.submissionSimulationTimeNs = submissionSimulationTimeNs;
        this.submissionWallClockNs = submissionWallClockNs;
        this.triggerType = triggerType;
        this.snapshot = snapshot;
        this.stateAtSubmission = stateAtSubmission;
        this.temporalMaximumAtSubmissionSeconds = temporalMaximumAtSubmissionSeconds;
        this.gaWallClockBudgetAtSubmissionSeconds = gaWallClockBudgetAtSubmissionSeconds;
        this.maxSnapshotAgeSimulationSeconds = maxSnapshotAgeSimulationSeconds;
        this.cooperativeStopDeadlineNs = cooperativeStopDeadlineNs;
        this.wallClockDeadlineNs = wallClockDeadlineNs;
        this.estimatorSnapshotAtSubmission = estimatorSnapshotAtSubmission;
    }

    /** Legacy constructor: V3-B treated the same number as both limits. */
    LiveGaJob(
            String jobId, int windowIndex, long submissionSimulationTimeNs,
            long submissionWallClockNs, String triggerType, SystemSnapshot snapshot,
            TemporalWindowState stateAtSubmission, double deltaTMaxAtSubmissionSeconds,
            long wallClockDeadlineNs,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot deltaTMaxSnapshotAtSubmission
    ) {
        this(jobId, windowIndex, submissionSimulationTimeNs, submissionWallClockNs,
                triggerType, snapshot, stateAtSubmission,
                deltaTMaxAtSubmissionSeconds, deltaTMaxAtSubmissionSeconds,
                deltaTMaxAtSubmissionSeconds, wallClockDeadlineNs,
                deltaTMaxSnapshotAtSubmission);
    }

    String getJobId() { return jobId; }
    int getWindowIndex() { return windowIndex; }
    long getSubmissionSimulationTimeNs() { return submissionSimulationTimeNs; }
    long getSubmissionWallClockNs() { return submissionWallClockNs; }
    String getTriggerType() { return triggerType; }
    SystemSnapshot getSnapshot() { return snapshot; }
    TemporalWindowState getStateAtSubmission() { return stateAtSubmission; }
    double getTemporalMaximumAtSubmissionSeconds() { return temporalMaximumAtSubmissionSeconds; }
    double getGaWallClockBudgetAtSubmissionSeconds() { return gaWallClockBudgetAtSubmissionSeconds; }
    double getMaxSnapshotAgeSimulationSeconds() { return maxSnapshotAgeSimulationSeconds; }
    double getDeltaTMaxAtSubmissionSeconds() { return temporalMaximumAtSubmissionSeconds; }
    long getCooperativeStopDeadlineNs() { return cooperativeStopDeadlineNs; }
    long getWallClockDeadlineNs() { return wallClockDeadlineNs; }
    LiveAdaptiveDeltaTMaxEstimator.Snapshot getDeltaTMaxSnapshotAtSubmission() {
        return estimatorSnapshotAtSubmission;
    }
    boolean isTimeoutDetectedBeforeCompletion() { return timeoutDetectedBeforeCompletion; }
    long getWaitCapDetectedWallClockNs() { return waitCapDetectedWallClockNs; }
    long getWaitCapDetectedSimulationTimeNs() { return waitCapDetectedSimulationTimeNs; }

    boolean detectTimeoutIfDeadlineReached(long wallClockNowNs, long simulationTimeNs) {
        if (timeoutDetectedBeforeCompletion || wallClockNowNs <= wallClockDeadlineNs) {
            return false;
        }
        timeoutDetectedBeforeCompletion = true;
        waitCapDetectedWallClockNs = wallClockNowNs;
        waitCapDetectedSimulationTimeNs = simulationTimeNs;
        return true;
    }
}
