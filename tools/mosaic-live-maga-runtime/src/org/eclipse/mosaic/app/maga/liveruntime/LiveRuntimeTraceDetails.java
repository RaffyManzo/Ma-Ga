package org.eclipse.mosaic.app.maga.liveruntime;

final class LiveRuntimeTraceDetails {
    static final LiveRuntimeTraceDetails EMPTY = new LiveRuntimeTraceDetails(
            0.0,
            0.0,
            0.0,
            0L,
            false,
            0L,
            0L,
            0,
            0,
            null,
            null
    );

    final double deltaTMaxAtSubmissionSeconds;
    final double deltaTMaxFromCompletedStepSeconds;
    final double deltaTMaxMismatchSeconds;
    final long wallClockDeadlineNs;
    final boolean timeoutDetectedBeforeCompletion;
    final long waitCapDetectedWallClockNs;
    final long waitCapDetectedSimulationTimeNs;
    final int invalidPoolBandwidthViolations;
    final int futurePoolViolations;
    final LiveAdaptiveDeltaTMaxEstimator.Snapshot submissionDeltaTMaxSnapshot;
    final LiveAdaptiveDeltaTMaxEstimator.Snapshot postCompletionDeltaTMaxSnapshot;

    LiveRuntimeTraceDetails(
            double deltaTMaxAtSubmissionSeconds,
            double deltaTMaxFromCompletedStepSeconds,
            double deltaTMaxMismatchSeconds,
            long wallClockDeadlineNs,
            boolean timeoutDetectedBeforeCompletion,
            long waitCapDetectedWallClockNs,
            long waitCapDetectedSimulationTimeNs,
            int invalidPoolBandwidthViolations,
            int futurePoolViolations,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot submissionDeltaTMaxSnapshot
    ) {
        this(
                deltaTMaxAtSubmissionSeconds,
                deltaTMaxFromCompletedStepSeconds,
                deltaTMaxMismatchSeconds,
                wallClockDeadlineNs,
                timeoutDetectedBeforeCompletion,
                waitCapDetectedWallClockNs,
                waitCapDetectedSimulationTimeNs,
                invalidPoolBandwidthViolations,
                futurePoolViolations,
                submissionDeltaTMaxSnapshot,
                null
        );
    }

    LiveRuntimeTraceDetails(
            double deltaTMaxAtSubmissionSeconds,
            double deltaTMaxFromCompletedStepSeconds,
            double deltaTMaxMismatchSeconds,
            long wallClockDeadlineNs,
            boolean timeoutDetectedBeforeCompletion,
            long waitCapDetectedWallClockNs,
            long waitCapDetectedSimulationTimeNs,
            int invalidPoolBandwidthViolations,
            int futurePoolViolations,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot submissionDeltaTMaxSnapshot,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot postCompletionDeltaTMaxSnapshot
    ) {
        this.deltaTMaxAtSubmissionSeconds = deltaTMaxAtSubmissionSeconds;
        this.deltaTMaxFromCompletedStepSeconds = deltaTMaxFromCompletedStepSeconds;
        this.deltaTMaxMismatchSeconds = deltaTMaxMismatchSeconds;
        this.wallClockDeadlineNs = wallClockDeadlineNs;
        this.timeoutDetectedBeforeCompletion = timeoutDetectedBeforeCompletion;
        this.waitCapDetectedWallClockNs = waitCapDetectedWallClockNs;
        this.waitCapDetectedSimulationTimeNs = waitCapDetectedSimulationTimeNs;
        this.invalidPoolBandwidthViolations = invalidPoolBandwidthViolations;
        this.futurePoolViolations = futurePoolViolations;
        this.submissionDeltaTMaxSnapshot = submissionDeltaTMaxSnapshot;
        this.postCompletionDeltaTMaxSnapshot = postCompletionDeltaTMaxSnapshot;
    }

    String deltaTMaxMode() {
        return submissionAdaptiveDeltaTMaxMode();
    }

    double adaptiveDeltaTMaxEstimateSeconds() {
        return submissionAdaptiveDeltaTMaxEstimateSeconds();
    }

    int adaptiveDeltaTMaxSampleCount() {
        return submissionAdaptiveDeltaTMaxSampleCount();
    }

    double adaptiveDeltaTMaxP95Seconds() {
        return submissionAdaptiveDeltaTMaxP95Seconds();
    }

    double adaptiveDeltaTMaxTargetSeconds() {
        return submissionAdaptiveDeltaTMaxTargetSeconds();
    }

    double adaptiveDeltaTMaxClampedSeconds() {
        return submissionAdaptiveDeltaTMaxClampedSeconds();
    }

    double adaptiveDeltaTMaxPreviousSeconds() {
        return submissionAdaptiveDeltaTMaxPreviousSeconds();
    }

    double adaptiveDeltaTMaxUpdatedSeconds() {
        return submissionAdaptiveDeltaTMaxSelectedSeconds();
    }

    String adaptiveDeltaTMaxFallbackReason() {
        return submissionAdaptiveDeltaTMaxFallbackReason();
    }

    String submissionAdaptiveDeltaTMaxMode() {
        return submissionDeltaTMaxSnapshot == null
                ? ""
                : submissionDeltaTMaxSnapshot.getMode().name();
    }

    double submissionAdaptiveDeltaTMaxEstimateSeconds() {
        return submissionDeltaTMaxSnapshot == null
                ? 0.0
                : submissionDeltaTMaxSnapshot.getEstimateSeconds();
    }

    int submissionAdaptiveDeltaTMaxSampleCount() {
        return submissionDeltaTMaxSnapshot == null
                ? 0
                : submissionDeltaTMaxSnapshot.getSampleCount();
    }

    double submissionAdaptiveDeltaTMaxP95Seconds() {
        return submissionDeltaTMaxSnapshot == null
                ? 0.0
                : submissionDeltaTMaxSnapshot.getP95Seconds();
    }

    double submissionAdaptiveDeltaTMaxTargetSeconds() {
        return submissionDeltaTMaxSnapshot == null
                ? 0.0
                : submissionDeltaTMaxSnapshot.getTargetSeconds();
    }

    double submissionAdaptiveDeltaTMaxClampedSeconds() {
        return submissionDeltaTMaxSnapshot == null
                ? 0.0
                : submissionDeltaTMaxSnapshot.getClampedSeconds();
    }

    double submissionAdaptiveDeltaTMaxPreviousSeconds() {
        return submissionDeltaTMaxSnapshot == null
                ? 0.0
                : submissionDeltaTMaxSnapshot.getPreviousSeconds();
    }

    double submissionAdaptiveDeltaTMaxSelectedSeconds() {
        return submissionDeltaTMaxSnapshot == null
                ? 0.0
                : submissionDeltaTMaxSnapshot.getUpdatedSeconds();
    }

    String submissionAdaptiveDeltaTMaxFallbackReason() {
        return submissionDeltaTMaxSnapshot == null
                ? ""
                : submissionDeltaTMaxSnapshot.getFallbackReason();
    }

    boolean postCompletionAdaptiveDeltaTMaxSampleAccepted() {
        return postCompletionDeltaTMaxSnapshot != null
                && postCompletionDeltaTMaxSnapshot.isSampleAccepted();
    }

    double postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds() {
        return postCompletionDeltaTMaxSnapshot == null
                ? 0.0
                : postCompletionDeltaTMaxSnapshot.getSampleRuntimeSeconds();
    }

    int postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion() {
        return postCompletionDeltaTMaxSnapshot == null
                ? 0
                : postCompletionDeltaTMaxSnapshot.getSampleCount();
    }

    double postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds() {
        return postCompletionDeltaTMaxSnapshot == null
                ? 0.0
                : postCompletionDeltaTMaxSnapshot.getP95Seconds();
    }

    double postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds() {
        return postCompletionDeltaTMaxSnapshot == null
                ? 0.0
                : postCompletionDeltaTMaxSnapshot.getTargetSeconds();
    }

    double postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds() {
        return postCompletionDeltaTMaxSnapshot == null
                ? 0.0
                : postCompletionDeltaTMaxSnapshot.getClampedSeconds();
    }

    double postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds() {
        return postCompletionDeltaTMaxSnapshot == null
                ? 0.0
                : postCompletionDeltaTMaxSnapshot.getPreviousSeconds();
    }

    double postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds() {
        return postCompletionDeltaTMaxSnapshot == null
                ? 0.0
                : postCompletionDeltaTMaxSnapshot.getUpdatedSeconds();
    }

    String postCompletionAdaptiveDeltaTMaxFallbackReason() {
        return postCompletionDeltaTMaxSnapshot == null
                ? ""
                : postCompletionDeltaTMaxSnapshot.getFallbackReason();
    }
}
