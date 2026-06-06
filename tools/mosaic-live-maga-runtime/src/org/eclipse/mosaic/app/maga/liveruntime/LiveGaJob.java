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
    private final double deltaTMaxAtSubmissionSeconds;
    private final long wallClockDeadlineNs;
    private boolean timeoutDetectedBeforeCompletion;
    private long waitCapDetectedWallClockNs;
    private long waitCapDetectedSimulationTimeNs;

    LiveGaJob(
            String jobId,
            int windowIndex,
            long submissionSimulationTimeNs,
            long submissionWallClockNs,
            String triggerType,
            SystemSnapshot snapshot,
            TemporalWindowState stateAtSubmission,
            double deltaTMaxAtSubmissionSeconds,
            long wallClockDeadlineNs
    ) {
        this.jobId = jobId;
        this.windowIndex = windowIndex;
        this.submissionSimulationTimeNs = submissionSimulationTimeNs;
        this.submissionWallClockNs = submissionWallClockNs;
        this.triggerType = triggerType;
        this.snapshot = snapshot;
        this.stateAtSubmission = stateAtSubmission;
        this.deltaTMaxAtSubmissionSeconds = deltaTMaxAtSubmissionSeconds;
        this.wallClockDeadlineNs = wallClockDeadlineNs;
    }

    String getJobId() {
        return jobId;
    }

    int getWindowIndex() {
        return windowIndex;
    }

    long getSubmissionSimulationTimeNs() {
        return submissionSimulationTimeNs;
    }

    long getSubmissionWallClockNs() {
        return submissionWallClockNs;
    }

    String getTriggerType() {
        return triggerType;
    }

    SystemSnapshot getSnapshot() {
        return snapshot;
    }

    TemporalWindowState getStateAtSubmission() {
        return stateAtSubmission;
    }

    double getDeltaTMaxAtSubmissionSeconds() {
        return deltaTMaxAtSubmissionSeconds;
    }

    long getWallClockDeadlineNs() {
        return wallClockDeadlineNs;
    }

    boolean isTimeoutDetectedBeforeCompletion() {
        return timeoutDetectedBeforeCompletion;
    }

    long getWaitCapDetectedWallClockNs() {
        return waitCapDetectedWallClockNs;
    }

    long getWaitCapDetectedSimulationTimeNs() {
        return waitCapDetectedSimulationTimeNs;
    }

    boolean detectTimeoutIfDeadlineReached(long wallClockNowNs, long simulationTimeNs) {
        if (timeoutDetectedBeforeCompletion || wallClockNowNs < wallClockDeadlineNs) {
            return false;
        }
        timeoutDetectedBeforeCompletion = true;
        waitCapDetectedWallClockNs = wallClockNowNs;
        waitCapDetectedSimulationTimeNs = simulationTimeNs;
        return true;
    }
}
