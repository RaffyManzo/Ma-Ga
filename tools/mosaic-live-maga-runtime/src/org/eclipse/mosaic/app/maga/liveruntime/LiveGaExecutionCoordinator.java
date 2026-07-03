package org.eclipse.mosaic.app.maga.liveruntime;

import ga.core.GaExecutionBudget;
import model.snapshot.SystemSnapshot;
import org.eclipse.mosaic.app.maga.liveruntime.reporting.LiveAdvancedDiagnosticsCollector;
import org.eclipse.mosaic.app.maga.liveruntime.reporting.LiveNativeReportingCollector;
import org.eclipse.mosaic.app.maga.liveruntime.reporting.LiveStaleStrategyWriter;
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
    private final LiveStaleStrategyWriter staleStrategyWriter;
    private final LiveAdvancedDiagnosticsCollector advancedDiagnosticsCollector;
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
            LiveNativeReportingCollector reportingCollector,
            LiveStaleStrategyWriter staleStrategyWriter,
            LiveAdvancedDiagnosticsCollector advancedDiagnosticsCollector
    ) {
        this.config = config;
        this.manager = manager;
        this.bridge = bridge;
        this.strategyApplier = strategyApplier;
        this.traceWriter = traceWriter;
        this.deadlinePolicy = deadlinePolicy;
        this.reportingCollector = reportingCollector;
        this.staleStrategyWriter = staleStrategyWriter;
        this.advancedDiagnosticsCollector = advancedDiagnosticsCollector;
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
        temporalState = alignTemporalStateToLiveSnapshot(snapshot);
        long submissionWallClockNs = System.nanoTime();
        LiveGaOverrunDeadlinePolicy.LiveGaDeadline deadline =
                deadlinePolicy.computeDeadline(snapshot, temporalState, submissionWallClockNs);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot submissionDeltaTMaxSnapshot =
                deadline.getDeltaTMaxSnapshot();
        TemporalWindowState stateAtSubmission = temporalStateWithOperationalMetrics(
                temporalState,
                deadline.getMetricsAtSubmission()
        );
        LiveGaJob job = new LiveGaJob(
                reportingCollector == null ? "" : reportingCollector.nextJobId(),
                temporalState.getWindowIndex(),
                simulationTimeNs,
                submissionWallClockNs,
                triggerType,
                snapshot,
                stateAtSubmission,
                deadline.getTemporalMaximumAtSubmissionSeconds(),
                deadline.getGaWallClockBudgetAtSubmissionSeconds(),
                deadline.getMaxSnapshotAgeSimulationSeconds(),
                deadline.getCooperativeStopDeadlineNs(),
                deadline.getWallClockDeadlineNs(),
                submissionDeltaTMaxSnapshot
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
                    job.getTemporalMaximumAtSubmissionSeconds(),
                    job.getGaWallClockBudgetAtSubmissionSeconds(),
                    job.getMaxSnapshotAgeSimulationSeconds(),
                    job.getWallClockDeadlineNs(),
                    job.getDeltaTMaxSnapshotAtSubmission().getMode().name(),
                    job.getDeltaTMaxSnapshotAtSubmission().getEstimateSeconds(),
                    job.getDeltaTMaxSnapshotAtSubmission().getSampleCount(),
                    job.getDeltaTMaxSnapshotAtSubmission().getP95Seconds(),
                    job.getDeltaTMaxSnapshotAtSubmission().getTargetSeconds(),
                    job.getDeltaTMaxSnapshotAtSubmission().getClampedSeconds(),
                    job.getDeltaTMaxSnapshotAtSubmission().getPreviousSeconds(),
                    job.getDeltaTMaxSnapshotAtSubmission().getUpdatedSeconds(),
                    job.getDeltaTMaxSnapshotAtSubmission().getFallbackReason()
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
                details(
                        job,
                        0.0,
                        0.0,
                        job.getDeltaTMaxSnapshotAtSubmission(),
                        null
                )
        );
    }


    /**
     * Riallinea lo stato temporale operativo al più recente snapshot MOSAIC disponibile.
     *
     * <p>Nel runtime live non dobbiamo recuperare ordinatamente finestre logiche ormai
     * superate mentre MOSAIC continua ad avanzare. Manteniamo quindi l'ultimo risultato
     * valido, le metriche osservate e la popolazione genetica riutilizzabile, ma facciamo
     * in modo che il prossimo step del manager osservi lo snapshot live corrente.</p>
     */
    private TemporalWindowState alignTemporalStateToLiveSnapshot(SystemSnapshot snapshot) {
        double liveTriggerTimeSeconds = Math.max(
                0.0,
                snapshot.getTimeSeconds()
                        - manager.getWindowConfig().getDataCollectionDelaySeconds()
        );

        if (temporalState == null) {
            return TemporalWindowState.initial(
                    liveTriggerTimeSeconds,
                    config.getTemporalInitialWindowSeconds(),
                    deadlinePolicy.initialOperationalMetrics()
            );
        }

        return new TemporalWindowState(
                temporalState.getWindowIndex(),
                liveTriggerTimeSeconds,
                liveTriggerTimeSeconds,
                temporalState.getCurrentWindowDurationSeconds(),
                temporalState.getLastSnapshot(),
                temporalState.getLastResult(),
                temporalState.getLastOperationalMetrics(),
                temporalState.getLastFinalPopulation()
        );
    }

    private TemporalWindowState temporalStateWithOperationalMetrics(
            TemporalWindowState source,
            TemporalOperationalMetrics operationalMetrics
    ) {
        return new TemporalWindowState(
                source.getWindowIndex(),
                source.getCurrentTimeSeconds(),
                source.getNextScheduledTimeSeconds(),
                source.getCurrentWindowDurationSeconds(),
                source.getLastSnapshot(),
                source.getLastResult(),
                operationalMetrics,
                source.getLastFinalPopulation()
        );
    }

    private LiveGaCompletion executeJob(LiveGaJob job) {
        try {
            if (config.getDiagnosticArtificialGaDelayMs() > 0) {
                Thread.sleep(config.getDiagnosticArtificialGaDelayMs());
            }
            GaExecutionBudget budget = config.isCooperativeGaBudgetStopEnabled()
                    ? GaExecutionBudget.deadlineNanoTime(job.getCooperativeStopDeadlineNs())
                    : GaExecutionBudget.unlimited();
            TemporalStepResult stepResult = manager.executeNextStepOrNull(
                    job.getStateAtSubmission(), budget
            );
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
                    details(completion, null)
            );
            return;
        }

        if (completion.getDeltaTMaxMismatchSeconds()
                > config.getDeltaTMaxComparisonEpsilonSeconds()) {
            deltaTMaxMismatchViolations++;
        }
        LiveStaleReason staleReason = completion.classify(simulationTimeNs);
        LiveAdaptiveDeltaTMaxEstimator.Snapshot postCompletionDeltaTMaxSnapshot =
                updateAdaptiveDeltaTMaxAfterTerminalClassification(completion);
        if (staleReason.isStale()) {
            markStale(
                    simulationTimeNs,
                    completion,
                    postCompletionDeltaTMaxSnapshot,
                    staleReason
            );
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
                details(completion, postCompletionDeltaTMaxSnapshot)
        );
        LiveAppliedStrategy applied = strategyApplier.apply(completion.getStepResult(), simulationTimeNs);
        if (advancedDiagnosticsCollector != null) {
            advancedDiagnosticsCollector.recordAppliedStrategy(
                    completion.getJob().getJobId(),
                    completion.getStepResult(),
                    applied,
                    simulationTimeNs
            );
        }
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
                details(completion, postCompletionDeltaTMaxSnapshot)
        );
        temporalState = temporalState.afterStep(completion.getStepResult());
        runtimeState = LiveGaExecutionState.IDLE;
    }

    private void markStale(
            long simulationTimeNs,
            LiveGaCompletion completion,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot postCompletionDeltaTMaxSnapshot,
            LiveStaleReason staleReason
    ) throws IOException {
        if (staleReason.includesWallClock()
                && !completion.getJob().isTimeoutDetectedBeforeCompletion()) {
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
                    details(completion, postCompletionDeltaTMaxSnapshot)
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
                    staleReason.name(),
                    details(completion, postCompletionDeltaTMaxSnapshot)
            );
        }

        staleResultDiscardedObserved = true;
        gaJobsDiscardedAsStale++;
        if (advancedDiagnosticsCollector != null) {
            advancedDiagnosticsCollector.recordStaleClassification(
                    completion.getJob().getJobId(),
                    completion.getStepResult(),
                    staleReason,
                    simulationTimeNs,
                    completion.getWallClockRuntimeSeconds(),
                    completion.getJob().getGaWallClockBudgetAtSubmissionSeconds(),
                    completion.getJob().getTemporalMaximumAtSubmissionSeconds(),
                    completion.getJob().getMaxSnapshotAgeSimulationSeconds(),
                    strategyApplier.getLastAppliedStrategy()
            );
        }
        if (staleStrategyWriter != null) {
            staleStrategyWriter.writeStale(
                    completion.getJob().getJobId(),
                    completion.getStepResult(),
                    staleReason,
                    simulationTimeNs,
                    completion.getWallClockRuntimeSeconds(),
                    completion.getJob().getGaWallClockBudgetAtSubmissionSeconds(),
                    completion.getJob().getTemporalMaximumAtSubmissionSeconds(),
                    completion.getJob().getMaxSnapshotAgeSimulationSeconds(),
                    strategyApplier.getLastAppliedStrategy()
            );
        }
        if (reportingCollector != null) {
            reportingCollector.recordStaleDiscarded(
                    completion.getJob().getJobId(),
                    completion.getStepResult(),
                    simulationTimeNs,
                    completion.getCompletionWallClockNs(),
                    completion.getWallClockRuntimeSeconds(),
                    completion.getDeltaTMaxSeconds(),
                    completion.getDeltaTMaxMismatchSeconds(),
                    staleReason
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
                details(completion, postCompletionDeltaTMaxSnapshot)
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
                details(completion, postCompletionDeltaTMaxSnapshot)
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
                details(completion, postCompletionDeltaTMaxSnapshot)
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
                details(
                        job,
                        0.0,
                        0.0,
                        job.getDeltaTMaxSnapshotAtSubmission(),
                        null
                )
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
                details(
                        job,
                        0.0,
                        0.0,
                        job.getDeltaTMaxSnapshotAtSubmission(),
                        null
                )
        );
    }

    private LiveAdaptiveDeltaTMaxEstimator.Snapshot updateAdaptiveDeltaTMaxAfterTerminalClassification(
            LiveGaCompletion completion
    ) throws IOException {
        if (completion.hasError() || completion.getStepResult() == null) {
            return null;
        }
        LiveAdaptiveDeltaTMaxEstimator.Snapshot snapshot =
                deadlinePolicy.recordCompletedRuntime(
                        completion.getWallClockRuntimeSeconds(),
                        completion.getStepResult().getSnapshot().getTasks().size()
                );
        if (snapshot == null) {
            return null;
        }
        if (reportingCollector != null) {
            reportingCollector.recordPostCompletionDeltaTMaxTelemetry(
                    completion.getJob().getJobId(),
                    snapshot.isSampleAccepted(),
                    snapshot.getSampleRuntimeSeconds(),
                    snapshot.getSampleCount(),
                    snapshot.getP95Seconds(),
                    snapshot.getTargetSeconds(),
                    snapshot.getClampedSeconds(),
                    snapshot.getPreviousSeconds(),
                    snapshot.getUpdatedSeconds(),
                    snapshot.getFallbackReason()
            );
        }
        return snapshot;
    }

    private LiveRuntimeTraceDetails details(
            LiveGaCompletion completion,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot postCompletionDeltaTMaxSnapshot
    ) {
        return details(
                completion.getJob(),
                completion.getDeltaTMaxSeconds(),
                completion.getDeltaTMaxMismatchSeconds(),
                completion.getJob().getDeltaTMaxSnapshotAtSubmission(),
                postCompletionDeltaTMaxSnapshot
        );
    }

    private LiveRuntimeTraceDetails details(
            LiveGaJob job,
            double deltaTMaxFromCompletedStepSeconds,
            double deltaTMaxMismatchSeconds
    ) {
        return details(
                job,
                deltaTMaxFromCompletedStepSeconds,
                deltaTMaxMismatchSeconds,
                job == null ? null : job.getDeltaTMaxSnapshotAtSubmission(),
                null
        );
    }

    private LiveRuntimeTraceDetails details(
            LiveGaJob job,
            double deltaTMaxFromCompletedStepSeconds,
            double deltaTMaxMismatchSeconds,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot submissionDeltaTMaxSnapshot,
            LiveAdaptiveDeltaTMaxEstimator.Snapshot postCompletionDeltaTMaxSnapshot
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
                bridge.getFuturePoolViolations(),
                submissionDeltaTMaxSnapshot == null
                        ? job.getDeltaTMaxSnapshotAtSubmission()
                        : submissionDeltaTMaxSnapshot,
                postCompletionDeltaTMaxSnapshot
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
