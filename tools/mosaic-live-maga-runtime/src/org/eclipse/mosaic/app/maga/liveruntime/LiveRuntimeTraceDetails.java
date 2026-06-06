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
            0
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

    LiveRuntimeTraceDetails(
            double deltaTMaxAtSubmissionSeconds,
            double deltaTMaxFromCompletedStepSeconds,
            double deltaTMaxMismatchSeconds,
            long wallClockDeadlineNs,
            boolean timeoutDetectedBeforeCompletion,
            long waitCapDetectedWallClockNs,
            long waitCapDetectedSimulationTimeNs,
            int invalidPoolBandwidthViolations,
            int futurePoolViolations
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
    }
}
