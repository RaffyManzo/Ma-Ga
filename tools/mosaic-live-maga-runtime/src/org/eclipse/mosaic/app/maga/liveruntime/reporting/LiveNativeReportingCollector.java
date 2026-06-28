package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import config.MaGaConfig;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LocalResourceUsageBreakdown;
import model.node.NodeType;
import window.state.TemporalStepResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LiveNativeReportingCollector implements AutoCloseable {
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_STALE_DISCARDED = "STALE_DISCARDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_NULL_STEP_RESULT = "NULL_STEP_RESULT";
    public static final String STATUS_SHUTDOWN_IN_FLIGHT = "SHUTDOWN_IN_FLIGHT";

    private final String scenarioName;
    private final String profile;
    private final String experimentalVariant;
    private final String bridgeDescription;
    private final String sourceMode;
    private final String optimizationSourceDescription;
    private final String populationReusePolicyDescription;
    private final String configuredCellProfile;
    private final String runtimeAccountingSource;
    private final Path reportingDir;
    private final LiveReportingJsonlWriter incrementalWriter;
    private final Map<String, LiveGaJobRecord> recordsByJobId = new LinkedHashMap<>();
    private final List<TemporalStepResult> appliedSteps = new ArrayList<>();
    private final List<TemporalStepResult> staleDiscardedSteps = new ArrayList<>();
    private final List<LiveReportingSummary.LiveWindowSummary> appliedWindowSummaries = new ArrayList<>();
    private final List<LiveReportingSummary.LiveWindowSummary> discardedWindowSummaries = new ArrayList<>();
    private int nextJobSequence = 1;

    public LiveNativeReportingCollector(
            Path runtimeOutputDir,
            String scenarioName,
            String profile,
            String experimentalVariant,
            String bridgeDescription,
            String sourceMode,
            String optimizationSourceDescription,
            String populationReusePolicyDescription,
            String configuredCellProfile,
            String runtimeAccountingSource
    ) throws IOException {
        this.scenarioName = scenarioName;
        this.profile = profile;
        this.experimentalVariant = experimentalVariant;
        this.bridgeDescription = bridgeDescription;
        this.sourceMode = sourceMode;
        this.optimizationSourceDescription = optimizationSourceDescription;
        this.populationReusePolicyDescription = populationReusePolicyDescription;
        this.configuredCellProfile = configuredCellProfile;
        this.runtimeAccountingSource = runtimeAccountingSource;
        this.reportingDir = runtimeOutputDir.resolve("live-reporting");
        this.incrementalWriter = new LiveReportingJsonlWriter(reportingDir);
    }

    public synchronized String nextJobId() {
        return String.format("live_ga_job_%06d", nextJobSequence++);
    }

    public synchronized void recordSubmitted(
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
    ) throws IOException {
        recordSubmitted(
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
                wallClockDeadlineNs,
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

    public synchronized void recordSubmitted(
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
            long wallClockDeadlineNs,
            String deltaTMaxMode,
            double adaptiveDeltaTMaxEstimateSeconds,
            int adaptiveDeltaTMaxSampleCount,
            double adaptiveDeltaTMaxP95Seconds,
            double adaptiveDeltaTMaxTargetSeconds,
            double adaptiveDeltaTMaxClampedSeconds,
            double adaptiveDeltaTMaxPreviousSeconds,
            double adaptiveDeltaTMaxUpdatedSeconds,
            String adaptiveDeltaTMaxFallbackReason
    ) throws IOException {
        LiveGaJobRecord record = new LiveGaJobRecord(
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
        record.applySubmissionDeltaTMaxTelemetry(
                deltaTMaxMode,
                adaptiveDeltaTMaxEstimateSeconds,
                adaptiveDeltaTMaxSampleCount,
                adaptiveDeltaTMaxP95Seconds,
                adaptiveDeltaTMaxTargetSeconds,
                adaptiveDeltaTMaxClampedSeconds,
                adaptiveDeltaTMaxPreviousSeconds,
                adaptiveDeltaTMaxUpdatedSeconds,
                adaptiveDeltaTMaxFallbackReason
        );
        recordsByJobId.put(jobId, record);
        incrementalWriter.writeEvent("SUBMITTED", record, submissionSimulationTimeNs);
    }

    public synchronized void recordPostCompletionDeltaTMaxTelemetry(
            String jobId,
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
        require(jobId).applyPostCompletionDeltaTMaxTelemetry(
                sampleAccepted,
                sampleRuntimeSeconds,
                sampleCountAfterCompletion,
                p95AfterCompletionSeconds,
                targetAfterCompletionSeconds,
                clampedAfterCompletionSeconds,
                previousBeforeUpdateSeconds,
                updatedForNextSubmissionSeconds,
                fallbackReason
        );
    }

    public synchronized void recordWaitCapReached(
            String jobId,
            long simulationTimeNs,
            long wallClockNs
    ) throws IOException {
        LiveGaJobRecord record = require(jobId);
        record.timeoutDetectedBeforeCompletion = true;
        record.waitCapDetectedSimulationTimeNs = simulationTimeNs;
        record.waitCapDetectedWallClockNs = wallClockNs;
        incrementalWriter.writeEvent("WAIT_CAP_REACHED", record, simulationTimeNs);
    }

    public synchronized void recordCompletedWithinBound(
            String jobId,
            TemporalStepResult step,
            long completionWallClockNs,
            double wallClockRuntimeSeconds,
            double deltaTMaxFromCompletedStepSeconds,
            double deltaTMaxMismatchSeconds
    ) throws IOException {
        LiveGaJobRecord record = require(jobId);
        record.completionWallClockNs = completionWallClockNs;
        record.gaRuntimeWallClockSeconds = wallClockRuntimeSeconds;
        record.deltaTMaxFromCompletedStepSeconds = deltaTMaxFromCompletedStepSeconds;
        record.deltaTMaxMismatchSeconds = deltaTMaxMismatchSeconds;
        incrementalWriter.writeEvent("COMPLETED_WITHIN_BOUND", record, stepTimeNs(step));
    }

    public synchronized void recordApplied(
            String jobId,
            TemporalStepResult step,
            long appliedAtSimulationTimeNs
    ) throws IOException {
        LiveGaJobRecord record = require(jobId);
        record.finalStatus = STATUS_APPLIED;
        record.appliedAtSimulationTimeNs = appliedAtSimulationTimeNs;
        record.appliedSnapshotAgeSimulationSeconds =
                (appliedAtSimulationTimeNs / 1000000000.0)
                        - record.snapshotTimeSeconds;
        appliedSteps.add(step);
        LiveReportingSummary.LiveWindowSummary summary = windowSummary(jobId, step, STATUS_APPLIED);
        appliedWindowSummaries.add(summary);
        incrementalWriter.writeStep(LiveTemporalStepRecord.from(jobId, STATUS_APPLIED, step));
        incrementalWriter.writeWindowCsv(csv(record, summary), true);
        incrementalWriter.writeEvent("APPLIED", record, appliedAtSimulationTimeNs);
    }

    public synchronized void recordStaleDiscarded(
            String jobId,
            TemporalStepResult step,
            long simulationTimeNs,
            long completionWallClockNs,
            double wallClockRuntimeSeconds,
            double deltaTMaxFromCompletedStepSeconds,
            double deltaTMaxMismatchSeconds
    ) throws IOException {
        LiveGaJobRecord record = require(jobId);
        record.finalStatus = STATUS_STALE_DISCARDED;
        record.completionWallClockNs = completionWallClockNs;
        record.gaRuntimeWallClockSeconds = wallClockRuntimeSeconds;
        record.deltaTMaxFromCompletedStepSeconds = deltaTMaxFromCompletedStepSeconds;
        record.deltaTMaxMismatchSeconds = deltaTMaxMismatchSeconds;
        staleDiscardedSteps.add(step);
        LiveReportingSummary.LiveWindowSummary summary = windowSummary(jobId, step, STATUS_STALE_DISCARDED);
        discardedWindowSummaries.add(summary);
        incrementalWriter.writeStep(LiveTemporalStepRecord.from(jobId, STATUS_STALE_DISCARDED, step));
        incrementalWriter.writeWindowCsv(csv(record, summary), false);
        incrementalWriter.writeEvent("STALE_DISCARDED", record, simulationTimeNs);
    }

    public synchronized void recordFailed(
            String jobId,
            long simulationTimeNs,
            long completionWallClockNs,
            double wallClockRuntimeSeconds,
            Throwable error
    ) throws IOException {
        LiveGaJobRecord record = require(jobId);
        record.finalStatus = STATUS_FAILED;
        record.completionWallClockNs = completionWallClockNs;
        record.gaRuntimeWallClockSeconds = wallClockRuntimeSeconds;
        record.errorType = error == null ? "" : error.getClass().getName();
        record.errorMessage = error == null ? "" : String.valueOf(error.getMessage());
        incrementalWriter.writeEvent("FAILED", record, simulationTimeNs);
    }

    public synchronized void recordNullStepResult(
            String jobId,
            long simulationTimeNs,
            long completionWallClockNs,
            double wallClockRuntimeSeconds
    ) throws IOException {
        LiveGaJobRecord record = require(jobId);
        record.finalStatus = STATUS_NULL_STEP_RESULT;
        record.completionWallClockNs = completionWallClockNs;
        record.gaRuntimeWallClockSeconds = wallClockRuntimeSeconds;
        incrementalWriter.writeEvent("NULL_STEP_RESULT", record, simulationTimeNs);
    }

    public synchronized void recordFreshReoptimizationRequested(
            String jobId,
            long simulationTimeNs
    ) throws IOException {
        incrementalWriter.writeEvent("FRESH_REOPTIMIZATION_REQUESTED", require(jobId), simulationTimeNs);
    }

    public synchronized void recordShutdownInFlight(
            String jobId,
            long simulationTimeNs,
            long wallClockNs
    ) throws IOException {
        LiveGaJobRecord record = require(jobId);
        if (record.finalStatus == null || record.finalStatus.isBlank()) {
            record.finalStatus = STATUS_SHUTDOWN_IN_FLIGHT;
        }
        record.completionWallClockNs = wallClockNs;
        incrementalWriter.writeEvent("SHUTDOWN_IN_FLIGHT", record, simulationTimeNs);
    }

    public synchronized LiveDetailedReportWriter.LiveDetailedReportArtifacts writeFinalReports(
            MaGaConfig maGaConfig
    ) throws IOException {
        LiveReportingSummary summary = LiveReportingSummary.from(
                scenarioName,
                profile,
                experimentalVariant,
                optimizationSourceDescription,
                populationReusePolicyDescription,
                bridgeDescription,
                sourceMode,
                configuredCellProfile,
                runtimeAccountingSource,
                snapshotRecords(),
                appliedWindowSummaries,
                discardedWindowSummaries,
                maGaConfig,
                reportingDir
        );
        return new LiveDetailedReportWriter().write(
                reportingDir,
                summary,
                appliedSteps,
                maGaConfig
        );
    }

    public synchronized int getAppliedStepCount() {
        return appliedSteps.size();
    }

    public synchronized int getStaleDiscardedStepCount() {
        return staleDiscardedSteps.size();
    }

    public synchronized int getFailedJobCount() {
        int count = 0;
        for (LiveGaJobRecord record : recordsByJobId.values()) {
            if (STATUS_FAILED.equals(record.finalStatus)) {
                count++;
            }
        }
        return count;
    }

    public Path getReportingDir() {
        return reportingDir;
    }

    @Override
    public synchronized void close() throws IOException {
        incrementalWriter.close();
    }

    private LiveGaJobRecord require(String jobId) {
        LiveGaJobRecord record = recordsByJobId.get(jobId);
        if (record == null) {
            throw new IllegalArgumentException("Unknown live GA job id: " + jobId);
        }
        return record;
    }

    private List<LiveGaJobRecord> snapshotRecords() {
        List<LiveGaJobRecord> rows = new ArrayList<>();
        for (LiveGaJobRecord record : recordsByJobId.values()) {
            rows.add(record.copy());
        }
        return rows;
    }

    private static long stepTimeNs(TemporalStepResult step) {
        return Math.round(step.getSnapshotTimeSeconds() * 1_000_000_000.0);
    }

    private static LiveReportingSummary.LiveWindowSummary windowSummary(
            String jobId,
            TemporalStepResult step,
            String status
    ) {
        int local = 0;
        int vehicle = 0;
        int edge = 0;
        int cloud = 0;
        for (GeneEvaluationBreakdown gene : step
                .getMaGaResult()
                .getBestEvaluation()
                .getGeneBreakdowns()) {
            if (gene.getNodeType() == NodeType.LOCAL) {
                local++;
            } else if (gene.getNodeType() == NodeType.VEHICLE) {
                vehicle++;
            } else if (gene.getNodeType() == NodeType.EDGE) {
                edge++;
            } else if (gene.getNodeType() == NodeType.CLOUD) {
                cloud++;
            }
        }

        int localTaskPortions = 0;
        int vehiclesWithLocalWorkload = 0;
        int vehiclesWithLocalContention = 0;
        int vehiclesWithLocalCpuOverflow = 0;
        int localDeadlineViolations = 0;
        double maxIndependentLocalExecutionTimeSeconds = 0.0;
        double maxContendedLocalCompletionTimeSeconds = 0.0;
        double maxLocalContentionDelaySeconds = 0.0;
        double maxLocalDemandRatio = 0.0;
        double maxLocalCpuOverflowRatio = 0.0;

        for (LocalResourceUsageBreakdown usage : step
                .getMaGaResult()
                .getBestEvaluation()
                .getLocalResourceUsageBreakdowns()) {
            if (!usage.hasLocalWorkload()) {
                continue;
            }

            vehiclesWithLocalWorkload++;
            localTaskPortions += usage.getLocalTaskCount();
            localDeadlineViolations += usage.getDeadlineViolationCount();

            if (usage.hasContention()) {
                vehiclesWithLocalContention++;
            }
            if (usage.hasCpuViolation()) {
                vehiclesWithLocalCpuOverflow++;
            }

            maxIndependentLocalExecutionTimeSeconds = Math.max(
                    maxIndependentLocalExecutionTimeSeconds,
                    usage.getMaxIndependentLocalExecutionTimeSeconds()
            );
            maxContendedLocalCompletionTimeSeconds = Math.max(
                    maxContendedLocalCompletionTimeSeconds,
                    usage.getMaxLocalExecutionTimeSeconds()
            );
            maxLocalContentionDelaySeconds = Math.max(
                    maxLocalContentionDelaySeconds,
                    usage.getMaxContentionDelaySeconds()
            );
            maxLocalDemandRatio = Math.max(
                    maxLocalDemandRatio,
                    usage.getMaxLocalDemandRatio()
            );
            maxLocalCpuOverflowRatio = Math.max(
                    maxLocalCpuOverflowRatio,
                    usage.getCpuOverflowRatio()
            );
        }

        return new LiveReportingSummary.LiveWindowSummary(
                jobId,
                step.getWindowIndex(),
                step.getSnapshot().getSnapshotId(),
                step.getSnapshotTimeSeconds(),
                step.getMaGaResult().getFinalBestFitness(),
                local,
                vehicle,
                edge,
                cloud,
                localTaskPortions,
                vehiclesWithLocalWorkload,
                vehiclesWithLocalContention,
                vehiclesWithLocalCpuOverflow,
                localDeadlineViolations,
                maxIndependentLocalExecutionTimeSeconds,
                maxContendedLocalCompletionTimeSeconds,
                maxLocalContentionDelaySeconds,
                maxLocalDemandRatio,
                maxLocalCpuOverflowRatio,
                status
        );
    }

    private static LiveReportingJsonlWriter.LiveWindowCsvRecord csv(
            LiveGaJobRecord record,
            LiveReportingSummary.LiveWindowSummary summary
    ) {
        return new LiveReportingJsonlWriter.LiveWindowCsvRecord(
                summary.jobId,
                summary.windowIndex,
                summary.snapshotId,
                summary.snapshotTimeSeconds,
                record.submissionSimulationTimeNs,
                record.gaRuntimeWallClockSeconds,
                record.deltaTMaxAtSubmissionSeconds,
                summary.fitness,
                summary.localAssignments,
                summary.vehicleAssignments,
                summary.edgeAssignments,
                summary.cloudAssignments,
                summary.localTaskPortions,
                summary.vehiclesWithLocalWorkload,
                summary.vehiclesWithLocalContention,
                summary.vehiclesWithLocalCpuOverflow,
                summary.localDeadlineViolations,
                summary.maxIndependentLocalExecutionTimeSeconds,
                summary.maxContendedLocalCompletionTimeSeconds,
                summary.maxLocalContentionDelaySeconds,
                summary.maxLocalDemandRatio,
                summary.maxLocalCpuOverflowRatio,
                summary.status,
                record.deltaTMaxMode,
                record.adaptiveDeltaTMaxEstimateSeconds,
                record.adaptiveDeltaTMaxSampleCount,
                record.adaptiveDeltaTMaxP95Seconds,
                record.adaptiveDeltaTMaxTargetSeconds,
                record.adaptiveDeltaTMaxClampedSeconds,
                record.adaptiveDeltaTMaxPreviousSeconds,
                record.adaptiveDeltaTMaxUpdatedSeconds,
                record.adaptiveDeltaTMaxFallbackReason,
                record.submissionAdaptiveDeltaTMaxMode,
                record.submissionAdaptiveDeltaTMaxEstimateSeconds,
                record.submissionAdaptiveDeltaTMaxSampleCount,
                record.submissionAdaptiveDeltaTMaxP95Seconds,
                record.submissionAdaptiveDeltaTMaxTargetSeconds,
                record.submissionAdaptiveDeltaTMaxClampedSeconds,
                record.submissionAdaptiveDeltaTMaxPreviousSeconds,
                record.submissionAdaptiveDeltaTMaxSelectedSeconds,
                record.submissionAdaptiveDeltaTMaxFallbackReason,
                record.postCompletionAdaptiveDeltaTMaxSampleAccepted,
                record.postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds,
                record.postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion,
                record.postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds,
                record.postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds,
                record.postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds,
                record.postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds,
                record.postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds,
                record.postCompletionAdaptiveDeltaTMaxFallbackReason
        );
    }
}
