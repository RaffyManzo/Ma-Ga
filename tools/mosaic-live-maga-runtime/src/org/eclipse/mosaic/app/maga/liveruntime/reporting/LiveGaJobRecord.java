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
    public String deltaTMaxMode = "CONFIGURED_STATIC";
    public double adaptiveDeltaTMaxEstimateSeconds;
    public int adaptiveDeltaTMaxSampleCount;
    public double adaptiveDeltaTMaxP95Seconds;
    public double adaptiveDeltaTMaxTargetSeconds;
    public double adaptiveDeltaTMaxClampedSeconds;
    public double adaptiveDeltaTMaxPreviousSeconds;
    public double adaptiveDeltaTMaxUpdatedSeconds;
    public String adaptiveDeltaTMaxFallbackReason = "CONFIGURED_STATIC";
    public String submissionAdaptiveDeltaTMaxMode = "CONFIGURED_STATIC";
    public double submissionAdaptiveDeltaTMaxEstimateSeconds;
    public int submissionAdaptiveDeltaTMaxSampleCount;
    public double submissionAdaptiveDeltaTMaxP95Seconds;
    public double submissionAdaptiveDeltaTMaxTargetSeconds;
    public double submissionAdaptiveDeltaTMaxClampedSeconds;
    public double submissionAdaptiveDeltaTMaxPreviousSeconds;
    public double submissionAdaptiveDeltaTMaxSelectedSeconds;
    public String submissionAdaptiveDeltaTMaxFallbackReason = "CONFIGURED_STATIC";
    public boolean postCompletionAdaptiveDeltaTMaxSampleAccepted;
    public double postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds;
    public int postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion;
    public double postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds;
    public double postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds;
    public double postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds;
    public double postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds;
    public double postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds;
    public String postCompletionAdaptiveDeltaTMaxFallbackReason = "";
    public String finalStatus = "";
    public long appliedAtSimulationTimeNs;
    public double appliedSnapshotAgeSimulationSeconds;
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
        applySubmissionDeltaTMaxTelemetry(
                "CONFIGURED_STATIC",
                deltaTMaxAtSubmissionSeconds,
                0,
                0.0,
                deltaTMaxAtSubmissionSeconds,
                deltaTMaxAtSubmissionSeconds,
                deltaTMaxAtSubmissionSeconds,
                deltaTMaxAtSubmissionSeconds,
                "CONFIGURED_STATIC"
        );
    }

    public void applySubmissionDeltaTMaxTelemetry(
            String deltaTMaxMode,
            double adaptiveDeltaTMaxEstimateSeconds,
            int adaptiveDeltaTMaxSampleCount,
            double adaptiveDeltaTMaxP95Seconds,
            double adaptiveDeltaTMaxTargetSeconds,
            double adaptiveDeltaTMaxClampedSeconds,
            double adaptiveDeltaTMaxPreviousSeconds,
            double adaptiveDeltaTMaxUpdatedSeconds,
            String adaptiveDeltaTMaxFallbackReason
    ) {
        this.submissionAdaptiveDeltaTMaxMode =
                deltaTMaxMode == null ? "" : deltaTMaxMode;
        this.submissionAdaptiveDeltaTMaxEstimateSeconds =
                adaptiveDeltaTMaxEstimateSeconds;
        this.submissionAdaptiveDeltaTMaxSampleCount =
                adaptiveDeltaTMaxSampleCount;
        this.submissionAdaptiveDeltaTMaxP95Seconds =
                adaptiveDeltaTMaxP95Seconds;
        this.submissionAdaptiveDeltaTMaxTargetSeconds =
                adaptiveDeltaTMaxTargetSeconds;
        this.submissionAdaptiveDeltaTMaxClampedSeconds =
                adaptiveDeltaTMaxClampedSeconds;
        this.submissionAdaptiveDeltaTMaxPreviousSeconds =
                adaptiveDeltaTMaxPreviousSeconds;
        this.submissionAdaptiveDeltaTMaxSelectedSeconds =
                adaptiveDeltaTMaxUpdatedSeconds;
        this.submissionAdaptiveDeltaTMaxFallbackReason =
                adaptiveDeltaTMaxFallbackReason == null
                        ? ""
                        : adaptiveDeltaTMaxFallbackReason;

        this.deltaTMaxMode = this.submissionAdaptiveDeltaTMaxMode;
        this.adaptiveDeltaTMaxEstimateSeconds = adaptiveDeltaTMaxEstimateSeconds;
        this.adaptiveDeltaTMaxSampleCount = adaptiveDeltaTMaxSampleCount;
        this.adaptiveDeltaTMaxP95Seconds = adaptiveDeltaTMaxP95Seconds;
        this.adaptiveDeltaTMaxTargetSeconds = adaptiveDeltaTMaxTargetSeconds;
        this.adaptiveDeltaTMaxClampedSeconds = adaptiveDeltaTMaxClampedSeconds;
        this.adaptiveDeltaTMaxPreviousSeconds = adaptiveDeltaTMaxPreviousSeconds;
        this.adaptiveDeltaTMaxUpdatedSeconds = adaptiveDeltaTMaxUpdatedSeconds;
        this.adaptiveDeltaTMaxFallbackReason =
                adaptiveDeltaTMaxFallbackReason == null
                        ? ""
                        : adaptiveDeltaTMaxFallbackReason;
    }

    public void applyPostCompletionDeltaTMaxTelemetry(
            boolean sampleAccepted,
            double sampleRuntimeSeconds,
            int sampleCountAfterCompletion,
            double p95AfterCompletionSeconds,
            double targetAfterCompletionSeconds,
            double clampedAfterCompletionSeconds,
            double previousBeforeUpdateSeconds,
            double updatedForNextSubmissionSeconds,
            String fallbackReason
    ) {
        this.postCompletionAdaptiveDeltaTMaxSampleAccepted = sampleAccepted;
        this.postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds =
                sampleRuntimeSeconds;
        this.postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion =
                sampleCountAfterCompletion;
        this.postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds =
                p95AfterCompletionSeconds;
        this.postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds =
                targetAfterCompletionSeconds;
        this.postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds =
                clampedAfterCompletionSeconds;
        this.postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds =
                previousBeforeUpdateSeconds;
        this.postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds =
                updatedForNextSubmissionSeconds;
        this.postCompletionAdaptiveDeltaTMaxFallbackReason =
                fallbackReason == null ? "" : fallbackReason;
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
        copy.applySubmissionDeltaTMaxTelemetry(
                submissionAdaptiveDeltaTMaxMode,
                submissionAdaptiveDeltaTMaxEstimateSeconds,
                submissionAdaptiveDeltaTMaxSampleCount,
                submissionAdaptiveDeltaTMaxP95Seconds,
                submissionAdaptiveDeltaTMaxTargetSeconds,
                submissionAdaptiveDeltaTMaxClampedSeconds,
                submissionAdaptiveDeltaTMaxPreviousSeconds,
                submissionAdaptiveDeltaTMaxSelectedSeconds,
                submissionAdaptiveDeltaTMaxFallbackReason
        );
        copy.applyPostCompletionDeltaTMaxTelemetry(
                postCompletionAdaptiveDeltaTMaxSampleAccepted,
                postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds,
                postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion,
                postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds,
                postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds,
                postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds,
                postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds,
                postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds,
                postCompletionAdaptiveDeltaTMaxFallbackReason
        );
        copy.finalStatus = finalStatus;
        copy.appliedAtSimulationTimeNs = appliedAtSimulationTimeNs;
        copy.appliedSnapshotAgeSimulationSeconds =
                appliedSnapshotAgeSimulationSeconds;
        copy.errorType = errorType;
        copy.errorMessage = errorMessage;
        return copy;
    }
}
