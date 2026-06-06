package org.eclipse.mosaic.app.maga.liveruntime;

import model.snapshot.SystemSnapshot;
import window.state.TemporalWindowState;

final class LiveGaJob {
    private final int windowIndex;
    private final long submissionSimulationTimeNs;
    private final long submissionWallClockNs;
    private final String triggerType;
    private final SystemSnapshot snapshot;
    private final TemporalWindowState stateAtSubmission;

    LiveGaJob(
            int windowIndex,
            long submissionSimulationTimeNs,
            long submissionWallClockNs,
            String triggerType,
            SystemSnapshot snapshot,
            TemporalWindowState stateAtSubmission
    ) {
        this.windowIndex = windowIndex;
        this.submissionSimulationTimeNs = submissionSimulationTimeNs;
        this.submissionWallClockNs = submissionWallClockNs;
        this.triggerType = triggerType;
        this.snapshot = snapshot;
        this.stateAtSubmission = stateAtSubmission;
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
}
