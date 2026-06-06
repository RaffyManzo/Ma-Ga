package org.eclipse.mosaic.app.maga.liveruntime;

import model.snapshot.SystemSnapshot;
import org.eclipse.mosaic.app.maga.liveruntime.reporting.LiveNativeReportingCollector;
import window.core.TemporalWindowManager;
import window.state.TemporalStepResult;
import window.state.TemporalWindowState;
import window.timing.TemporalOperationalMetrics;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class LiveGaExecutionCoordinator implements AutoCloseable {

    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000.0;

    private final MaGaLiveRuntimeConfig config;
    private final TemporalWindowManager manager;
    private final MaGaLiveMosaicSnapshotBridge bridge;
    private final LiveStrategyApplier strategyApplier;
    private final LiveRuntimeTraceWriter traceWriter;
    private final LiveGaOverrunDeadlinePolicy deadlinePolicy;
    private final LiveNativeReportingCollector reportingCollector;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TemporalWindowState temporalState;
    private Future<LiveGaCompletion> inFlightFuture;
    private LiveGaJob inFlightJob;
    private LiveGaExecutionState runtimeState = LiveGaExecutionState.IDLE;
    private boolean freshReoptimizationRequested;
    private boolean lastAppliedStrategyPreservedWhileRunning;
    private int gaJobsSubmitted;
    private int gaJobsCompleted;
    private int gaJobsApplied;
    private int gaJobsDiscardedAsStale;
    private int parallelGaViolations;
    private int deltaTMaxMismatchViolations;
    private boolean waitCapReachedObserved;
    private boolean staleResultDiscardedObserved;
    private boolean freshReoptimizationRequestedObserved;

    LiveGaExecutionCoordinator(
            MaGaLiveRuntimeConfig config,
            TemporalWindowManager manager,
            MaGaLiveMosaicSnapshotBridge bridge,
            LiveStrategyApplier strategyApplier,
            LiveRuntimeTraceWriter traceWriter,
            LiveGaOverrunDeadlinePolicy deadlinePolicy,
            LiveNativeReportingCollector reportingCollector
    ) {
        this.config = config;
        this.manager = manager;
        this.bridge = bridge;
        this.strategyApplier = strategyApplier;
        this.traceWriter = traceWriter;
        this.deadlinePolicy = deadlinePolicy;
        this.reportingCollector = reportingCollector;
    }

    void onTick(
            long simulationTimeNs,
            Optional<SystemSnapshot> maybeSnapshot
    ) throws IOException {
        if (inFlightFuture != null && !inFlightFuture.isDone()) {
            detectTimeoutWhileRunning(simulationTimeNs);
            if (strategyApplier.hasLastAppliedStrategy()) {
                lastAppliedStrategyPreservedWhileRunning = true;
            }
            writeRunningTrace(simulationTimeNs);
            return;
        }
        pollCompletion(simulationTimeNs);
        if (maybeSnapshot.isEmpty()) {
            return;
        }
        SystemSnapshot snapshot = maybeSnapshot.get();
        if (!shouldSubmit(simulationTimeNs, snapshot)) {
            return;
        }
        submit(simulationTimeNs, snapshot, triggerType(simulationTimeNs));
    }

    private boolean shouldSubmit(long simulationTimeNs, SystemSnapshot snapshot) {
        if (inFlightFuture != null && !inFlightFuture.isDone()) {
            parallelGaViolations++;
            return false;
        }
        if (freshReoptimizationRequested) {
            return true;
        }
        if (temporalState == null) {
            return simulationTimeNs >= config.getInitialOptimizationDelayNs();
        }
        long nextScheduledNs = Math.round(temporalState.getNextScheduledTimeSeconds() * NANOSECONDS_PER_SECOND);
        return simulationTimeNs >= nextScheduledNs
                && snapshot.getTimeSeconds() + 1.0E-9 >= temporalState.getNextScheduledTimeSeconds();
    }

    private String triggerType(long simulationTimeNs) {
        if (freshReoptimizationRequested) {
            return "FRESH_REOPTIMIZATION_REQUESTED";
        }
        if (temporalState == null) {
            return "FIRST_RUN";
        }
        return "SCHEDULED_WINDOW_EXPIRATION";
    }

    private void submit(long simulationTimeNs, SystemSnapshot snapshot, String triggerType) throws IOException {
        if (inFlightFuture != null && !inFlightFuture.isDone()) {
            parallelGaViolations++;
            return;
        }
        if (temporalState == null) {
            temporalState = TemporalWindowState.initial(
                    snapshot.getTimeSeconds(),
                    config.getTemporalInitialWindowSeconds(),
                    deadlinePolicy.initialOperationalMetrics()
            );
        }
        long submissionWallClockNs = System.nanoTime();
        LiveGaOverrunDeadlinePolicy.LiveGaDeadline deadline =
                deadlinePolicy.computeDeadline(snapshot, temporalState, submissionWallClockNs);
        LiveGaJob job = new LiveGaJob(
                reportingCollector == null ? "" : reportingCollector.nextJobId(),
                temporalState.getWindowIndex(),
                simulationTimeNs,
                submissionWallClockNs,
                triggerType,
                snapshot,
                temporalState,
                deadline.getDeltaTMaxAtSubmissionSeconds(),
                deadline.getWallClockDeadlineNs()
        );
        inFlightJob = job;
        runtimeState = LiveGaExecutionState.GA_RUNNING;
        freshReoptimizationRequested = false;
        gaJobsSubmitted++;
        inFlightFuture = executor.submit(() -> executeJob(job));
        if (reportingCollector != null) {
            reportingCollector.recordSubmitted(
                    job.getJobId(),
                    job.getWindowIndex(),
                    job.getTriggerType(),
                    job.getSubmissionSimulationTimeNs(),
                    job.getSubmissionWallClockNs(),
                    snapshot.getSnapshotId(),
                    snapshot.getTimeSeconds(),
                    snapshot.getTasks().size(),
                    snapshot.getCandidateNodes().size(),
                    job.getDeltaTMaxAtSubmissionSeconds(),
                    job.getWallClockDeadlineNs()
            );
        }
        traceWriter.writeRuntime(
                simulationTimeNs,
                job.getWindowIndex(),
                runtimeState,
                triggerType,
                snapshot.getSnapshotId(),
                snapshot.getTimeSeconds(),
                snapshot.getTasks().size(),
                snapshot.getCandidateNodes().size(),
                true,
                false,
                0.0,
                job.getDeltaTMaxAtSubmissionSeconds(),
                false,
                false,
                lastAppliedSnapshotId(),
                "",
                details(job, 0.0, 0.0)
        );
    }

    private LiveGaCompletion executeJob(LiveGaJob job) {
        try {
            if (config.getDiagnosticArtificialGaDelayMs() > 0) {
                Thread.sleep(config.getDiagnosticArtificialGaDelayMs());
            }
            TemporalStepResult stepResult = manager.executeNextStepOrNull(job.getStateAtSubmission());
            long completionWallClockNs = System.nanoTime();
            double runtimeSeconds =
                    (completionWallClockNs - job.getSubmissionWallClockNs()) / NANOSECONDS_PER_SECOND;
            return LiveGaCompletion.success(job, stepResult, completionWallClockNs, runtimeSeconds);
        } catch (Throwable error) {
            long completionWallClockNs = System.nanoTime();
            double runtimeSeconds =
                    (completionWallClockNs - job.getSubmissionWallClockNs()) / NANOSECONDS_PER_SECOND;
            return LiveGaCompletion.failure(job, error, completionWallClockNs, runtimeSeconds);
        }
    }

    private void pollCompletion(long simulationTimeNs) throws IOException {
        if (inFlightFuture == null || !inFlightFuture.isDone()) {
            return;
        }
        LiveGaCompletion completion;
        try {
            completion = inFlightFuture.get();
        } catch (Exception e) {
            completion = LiveGaCompletion.failure(inFlightJob, e, System.nanoTime(), 0.0);
        }
        inFlightFuture = null;
        inFlightJob = null;
        gaJobsCompleted++;

        if (completion.hasError() || completion.getStepResult() == null) {
            if (reportingCollector != null && completion.getJob() != null) {
                if (completion.hasError()) {
                    reportingCollector.recordFailed(
                            completion.getJob().getJobId(),
                            simulationTimeNs,
                            completion.getCompletionWallClockNs(),
                            completion.getWallClockRuntimeSeconds(),
                            completion.getError()
                    );
                } else {
                    reportingCollector.recordNullStepResult(
                            completion.getJob().getJobId(),
                            simulationTimeNs,
                            completion.getCompletionWallClockNs(),
                            completion.getWallClockRuntimeSeconds()
                    );
                }
            }
            runtimeState = LiveGaExecutionState.IDLE;
            traceWriter.writeRuntime(
                    simulationTimeNs,
                    completion.getJob().getWindowIndex(),
                    runtimeState,
                    completion.getJob().getTriggerType(),
                    completion.getJob().getSnapshot().getSnapshotId(),
                    completion.getJob().getSnapshot().getTimeSeconds(),
                    completion.getJob().getSnapshot().getTasks().size(),
                    completion.getJob().getSnapshot().getCandidateNodes().size(),
                    false,
                    true,
                    completion.getWallClockRuntimeSeconds(),
                    completion.getDeltaTMaxSeconds(),
                    false,
                    false,
                    lastAppliedSnapshotId(),
                    completion.hasError() ? completion.getError().getMessage() : "TemporalWindowManager returned null",
                    details(completion)
            );
            return;
        }

        if (completion.getDeltaTMaxMismatchSeconds()
                > config.getDeltaTMaxComparisonEpsilonSeconds()) {
            deltaTMaxMismatchViolations++;
        }
        if (completion.isStale()) {
            markStale(simulationTimeNs, completion);
            return;
        }
        runtimeState = LiveGaExecutionState.RESULT_READY_WITHIN_BOUND;
        if (reportingCollector != null) {
            reportingCollector.recordCompletedWithinBound(
                    completion.getJob().getJobId(),
                    completion.getStepResult(),
                    completion.getCompletionWallClockNs(),
                    completion.getWallClockRuntimeSeconds(),
                    completion.getDeltaTMaxSeconds(),
                    completion.getDeltaTMaxMismatchSeconds()
            );
        }
        traceWriter.writeRuntime(
                simulationTimeNs,
                completion.getJob().getWindowIndex(),
                runtimeState,
                completion.getJob().getTriggerType(),
                completion.getStepResult().getSnapshot().getSnapshotId(),
                completion.getStepResult().getSnapshot().getTimeSeconds(),
                completion.getStepResult().getSnapshot().getTasks().size(),
                completion.getStepResult().getSnapshot().getCandidateNodes().size(),
                false,
                true,
                completion.getWallClockRuntimeSeconds(),
                completion.getDeltaTMaxSeconds(),
                false,
                false,
                lastAppliedSnapshotId(),
                "",
                details(completion)
        );
        LiveAppliedStrategy applied = strategyApplier.apply(completion.getStepResult(), simulationTimeNs);
        if (reportingCollector != null) {
            reportingCollector.recordApplied(
                    completion.getJob().getJobId(),
                    completion.getStepResult(),
                    simulationTimeNs
            );
        }
        gaJobsApplied++;
        runtimeState = LiveGaExecutionState.RESULT_APPLIED;
        traceWriter.writeStrategy(simulationTimeNs, completion.getJob().getWindowIndex(), applied);
        traceWriter.writeRuntime(
                simulationTimeNs,
                completion.getJob().getWindowIndex(),
                runtimeState,
                completion.getJob().getTriggerType(),
                completion.getStepResult().getSnapshot().getSnapshotId(),
                completion.getStepResult().getSnapshot().getTimeSeconds(),
                completion.getStepResult().getSnapshot().getTasks().size(),
                completion.getStepResult().getSnapshot().getCandidateNodes().size(),
                false,
                true,
                completion.getWallClockRuntimeSeconds(),
                completion.getDeltaTMaxSeconds(),
                true,
                false,
                lastAppliedSnapshotId(),
                "",
                details(completion)
        );
        temporalState = temporalState.afterStep(completion.getStepResult());
        runtimeState = LiveGaExecutionState.IDLE;
    }

    private void markStale(long simulationTimeNs, LiveGaCompletion completion) throws IOException {
        if (!completion.getJob().isTimeoutDetectedBeforeCompletion()) {
            waitCapReachedObserved = true;
            runtimeState = LiveGaExecutionState.WAIT_CAP_REACHED;
            if (reportingCollector != null) {
                reportingCollector.recordWaitCapReached(
                        completion.getJob().getJobId(),
                        simulationTimeNs,
                        completion.getCompletionWallClockNs()
                );
            }
            traceWriter.writeOverrun(
                    simulationTimeNs,
                    completion.getJob().getWindowIndex(),
                    runtimeState,
                    completion.getStepResult().getSnapshot().getSnapshotId(),
                    completion.getWallClockRuntimeSeconds(),
                    completion.getDeltaTMaxSeconds(),
                    false,
                    details(completion)
            );
            traceWriter.writeRuntime(
                    simulationTimeNs,
                    completion.getJob().getWindowIndex(),
                    runtimeState,
                    completion.getJob().getTriggerType(),
                    completion.getStepResult().getSnapshot().getSnapshotId(),
                    completion.getStepResult().getSnapshot().getTimeSeconds(),
                    completion.getStepResult().getSnapshot().getTasks().size(),
                    completion.getStepResult().getSnapshot().getCandidateNodes().size(),
                    false,
                    true,
                    completion.getWallClockRuntimeSeconds(),
                    completion.getDeltaTMaxSeconds(),
                    false,
                    false,
                    lastAppliedSnapshotId(),
                    "",
                    details(completion)
            );
        }

        staleResultDiscardedObserved = true;
        gaJobsDiscardedAsStale++;
        if (reportingCollector != null) {
            reportingCollector.recordStaleDiscarded(
                    completion.getJob().getJobId(),
                    completion.getStepResult(),
                    simulationTimeNs,
                    completion.getCompletionWallClockNs(),
                    completion.getWallClockRuntimeSeconds(),
                    completion.getDeltaTMaxSeconds(),
                    completion.getDeltaTMaxMismatchSeconds()
            );
        }
        runtimeState = LiveGaExecutionState.STALE_RESULT_DISCARDED;
        traceWriter.writeOverrun(
                simulationTimeNs,
                completion.getJob().getWindowIndex(),
                runtimeState,
                completion.getStepResult().getSnapshot().getSnapshotId(),
                completion.getWallClockRuntimeSeconds(),
                completion.getDeltaTMaxSeconds(),
                true,
                details(completion)
        );
        traceWriter.writeRuntime(
                simulationTimeNs,
                completion.getJob().getWindowIndex(),
                runtimeState,
                completion.getJob().getTriggerType(),
                completion.getStepResult().getSnapshot().getSnapshotId(),
                completion.getStepResult().getSnapshot().getTimeSeconds(),
                completion.getStepResult().getSnapshot().getTasks().size(),
                completion.getStepResult().getSnapshot().getCandidateNodes().size(),
                false,
                true,
                completion.getWallClockRuntimeSeconds(),
                completion.getDeltaTMaxSeconds(),
                false,
                true,
                lastAppliedSnapshotId(),
                "",
                details(completion)
        );

        freshReoptimizationRequestedObserved = true;
        freshReoptimizationRequested = true;
        runtimeState = LiveGaExecutionState.FRESH_REOPTIMIZATION_REQUESTED;
        if (reportingCollector != null) {
            reportingCollector.recordFreshReoptimizationRequested(
                    completion.getJob().getJobId(),
                    simulationTimeNs
            );
        }
        traceWriter.writeRuntime(
                simulationTimeNs,
                completion.getJob().getWindowIndex(),
                runtimeState,
                completion.getJob().getTriggerType(),
                completion.getStepResult().getSnapshot().getSnapshotId(),
                completion.getStepResult().getSnapshot().getTimeSeconds(),
                completion.getStepResult().getSnapshot().getTasks().size(),
                completion.getStepResult().getSnapshot().getCandidateNodes().size(),
                false,
                true,
                completion.getWallClockRuntimeSeconds(),
                completion.getDeltaTMaxSeconds(),
                false,
                true,
                lastAppliedSnapshotId(),
                "",
                details(completion)
        );
    }

    private void writeRunningTrace(long simulationTimeNs) throws IOException {
        LiveGaJob job = inFlightJob;
        if (job == null) {
            return;
        }
        traceWriter.writeRuntime(
                simulationTimeNs,
                job.getWindowIndex(),
                runtimeState,
                job.getTriggerType(),
                job.getSnapshot().getSnapshotId(),
                job.getSnapshot().getTimeSeconds(),
                job.getSnapshot().getTasks().size(),
                job.getSnapshot().getCandidateNodes().size(),
                false,
                false,
                0.0,
                job.getDeltaTMaxAtSubmissionSeconds(),
                false,
                false,
                lastAppliedSnapshotId(),
                "",
                details(job, 0.0, 0.0)
        );
    }

    private void detectTimeoutWhileRunning(long simulationTimeNs) throws IOException {
        LiveGaJob job = inFlightJob;
        if (job == null || job.isTimeoutDetectedBeforeCompletion()) {
            return;
        }
        if (!job.detectTimeoutIfDeadlineReached(System.nanoTime(), simulationTimeNs)) {
            return;
        }
        if (reportingCollector != null) {
            reportingCollector.recordWaitCapReached(
                    job.getJobId(),
                    simulationTimeNs,
                    job.getWaitCapDetectedWallClockNs()
            );
        }
        waitCapReachedObserved = true;
        runtimeState = LiveGaExecutionState.WAIT_CAP_REACHED;
        traceWriter.writeOverrun(
                simulationTimeNs,
                job.getWindowIndex(),
                runtimeState,
                job.getSnapshot().getSnapshotId(),
                0.0,
                job.getDeltaTMaxAtSubmissionSeconds(),
                false,
                details(job, 0.0, 0.0)
        );
        traceWriter.writeRuntime(
                simulationTimeNs,
                job.getWindowIndex(),
                runtimeState,
                job.getTriggerType(),
                job.getSnapshot().getSnapshotId(),
                job.getSnapshot().getTimeSeconds(),
                job.getSnapshot().getTasks().size(),
                job.getSnapshot().getCandidateNodes().size(),
                false,
                false,
                0.0,
                job.getDeltaTMaxAtSubmissionSeconds(),
                false,
                false,
                lastAppliedSnapshotId(),
                "",
                details(job, 0.0, 0.0)
        );
    }

    private LiveRuntimeTraceDetails details(LiveGaCompletion completion) {
        return details(
                completion.getJob(),
                completion.getDeltaTMaxSeconds(),
                completion.getDeltaTMaxMismatchSeconds()
        );
    }

    private LiveRuntimeTraceDetails details(
            LiveGaJob job,
            double deltaTMaxFromCompletedStepSeconds,
            double deltaTMaxMismatchSeconds
    ) {
        if (job == null) {
            return LiveRuntimeTraceDetails.EMPTY;
        }
        return new LiveRuntimeTraceDetails(
                job.getDeltaTMaxAtSubmissionSeconds(),
                deltaTMaxFromCompletedStepSeconds,
                deltaTMaxMismatchSeconds,
                job.getWallClockDeadlineNs(),
                job.isTimeoutDetectedBeforeCompletion(),
                job.getWaitCapDetectedWallClockNs(),
                job.getWaitCapDetectedSimulationTimeNs(),
                bridge.getInvalidPoolBandwidthViolations(),
                bridge.getFuturePoolViolations()
        );
    }

    private String lastAppliedSnapshotId() {
        LiveAppliedStrategy strategy = strategyApplier.getLastAppliedStrategy();
        return strategy == null ? "" : strategy.getSnapshotId();
    }

    int getGaJobsSubmitted() {
        return gaJobsSubmitted;
    }

    int getGaJobsCompleted() {
        return gaJobsCompleted;
    }

    int getGaJobsApplied() {
        return gaJobsApplied;
    }

    int getGaJobsDiscardedAsStale() {
        return gaJobsDiscardedAsStale;
    }

    int getParallelGaViolations() {
        return parallelGaViolations;
    }

    boolean isLastAppliedStrategyPreservedWhileRunning() {
        return lastAppliedStrategyPreservedWhileRunning;
    }

    boolean isWaitCapReachedObserved() {
        return waitCapReachedObserved;
    }

    boolean isStaleResultDiscardedObserved() {
        return staleResultDiscardedObserved;
    }

    boolean isFreshReoptimizationRequestedObserved() {
        return freshReoptimizationRequestedObserved;
    }

    int getDeltaTMaxMismatchViolations() {
        return deltaTMaxMismatchViolations;
    }

    void finishOnShutdown(long simulationTimeNs) throws IOException {
        if (inFlightFuture != null && !inFlightFuture.isDone()) {
            detectTimeoutWhileRunning(simulationTimeNs);
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (inFlightFuture != null && !inFlightFuture.isDone()
                && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (inFlightFuture != null && !inFlightFuture.isDone()
                && inFlightJob != null && reportingCollector != null) {
            reportingCollector.recordShutdownInFlight(
                    inFlightJob.getJobId(),
                    simulationTimeNs,
                    System.nanoTime()
            );
        }
        pollCompletion(simulationTimeNs);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
