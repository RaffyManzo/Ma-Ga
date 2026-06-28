package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class LiveReportingJsonlWriter implements AutoCloseable {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final BufferedWriter eventWriter;
    private final BufferedWriter stepWriter;
    private final BufferedWriter appliedCsvWriter;
    private final BufferedWriter discardedCsvWriter;

    LiveReportingJsonlWriter(Path reportingDir) throws IOException {
        Files.createDirectories(reportingDir);
        eventWriter = Files.newBufferedWriter(
                reportingDir.resolve("live_ga_job_events.jsonl"),
                StandardCharsets.UTF_8
        );
        stepWriter = Files.newBufferedWriter(
                reportingDir.resolve("live_temporal_step_records.jsonl"),
                StandardCharsets.UTF_8
        );
        appliedCsvWriter = Files.newBufferedWriter(
                reportingDir.resolve("live_applied_window_records.csv"),
                StandardCharsets.UTF_8
        );
        discardedCsvWriter = Files.newBufferedWriter(
                reportingDir.resolve("live_discarded_window_records.csv"),
                StandardCharsets.UTF_8
        );
        String header = "jobId,windowIndex,snapshotId,snapshotTimeSeconds,"
                + "submissionSimulationTimeNs,gaRuntimeWallClockSeconds,"
                + "deltaTMaxSeconds,fitness,localAssignments,"
                + "vehicleAssignments,edgeAssignments,cloudAssignments,"
                + "localTaskPortions,vehiclesWithLocalWorkload,"
                + "vehiclesWithLocalContention,vehiclesWithLocalCpuOverflow,"
                + "localDeadlineViolations,"
                + "maxIndependentLocalExecutionTimeSeconds,"
                + "maxContendedLocalCompletionTimeSeconds,"
                + "maxLocalContentionDelaySeconds,maxLocalDemandRatio,"
                + "maxLocalCpuOverflowRatio,status,deltaTMaxMode,"
                + "adaptiveDeltaTMaxEstimateSeconds,"
                + "adaptiveDeltaTMaxSampleCount,"
                + "adaptiveDeltaTMaxP95Seconds,"
                + "adaptiveDeltaTMaxTargetSeconds,"
                + "adaptiveDeltaTMaxClampedSeconds,"
                + "adaptiveDeltaTMaxPreviousSeconds,"
                + "adaptiveDeltaTMaxUpdatedSeconds,"
                + "adaptiveDeltaTMaxFallbackReason,"
                + "submissionAdaptiveDeltaTMaxMode,"
                + "submissionAdaptiveDeltaTMaxEstimateSeconds,"
                + "submissionAdaptiveDeltaTMaxSampleCount,"
                + "submissionAdaptiveDeltaTMaxP95Seconds,"
                + "submissionAdaptiveDeltaTMaxTargetSeconds,"
                + "submissionAdaptiveDeltaTMaxClampedSeconds,"
                + "submissionAdaptiveDeltaTMaxPreviousSeconds,"
                + "submissionAdaptiveDeltaTMaxSelectedSeconds,"
                + "submissionAdaptiveDeltaTMaxFallbackReason,"
                + "postCompletionAdaptiveDeltaTMaxSampleAccepted,"
                + "postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds,"
                + "postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion,"
                + "postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds,"
                + "postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds,"
                + "postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds,"
                + "postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds,"
                + "postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds,"
                + "postCompletionAdaptiveDeltaTMaxFallbackReason\n";
        appliedCsvWriter.write(header);
        discardedCsvWriter.write(header);
        appliedCsvWriter.flush();
        discardedCsvWriter.flush();
    }

    void writeEvent(
            String eventType,
            LiveGaJobRecord record,
            long eventSimulationTimeNs
    ) throws IOException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("eventType", eventType);
        row.put("eventSimulationTimeNs", eventSimulationTimeNs);
        row.put("jobId", record.jobId);
        row.put("windowIndex", record.windowIndex);
        row.put("triggerType", record.triggerType);
        row.put("snapshotId", record.snapshotId);
        row.put("snapshotTimeSeconds", record.snapshotTimeSeconds);
        row.put("finalStatus", record.finalStatus);
        row.put(
                "appliedAtSimulationTimeNs",
                record.appliedAtSimulationTimeNs
        );
        row.put(
                "appliedSnapshotAgeSimulationSeconds",
                record.appliedSnapshotAgeSimulationSeconds
        );
        row.put(
                "timeoutDetectedBeforeCompletion",
                record.timeoutDetectedBeforeCompletion
        );
        row.put(
                "waitCapDetectedWallClockNs",
                record.waitCapDetectedWallClockNs
        );
        row.put(
                "waitCapDetectedSimulationTimeNs",
                record.waitCapDetectedSimulationTimeNs
        );
        row.put("completionWallClockNs", record.completionWallClockNs);
        row.put(
                "gaRuntimeWallClockSeconds",
                record.gaRuntimeWallClockSeconds
        );
        row.put(
                "deltaTMaxAtSubmissionSeconds",
                record.deltaTMaxAtSubmissionSeconds
        );
        row.put(
                "deltaTMaxFromCompletedStepSeconds",
                record.deltaTMaxFromCompletedStepSeconds
        );
        row.put(
                "deltaTMaxMismatchSeconds",
                record.deltaTMaxMismatchSeconds
        );
        row.put("deltaTMaxMode", record.deltaTMaxMode);
        row.put(
                "adaptiveDeltaTMaxEstimateSeconds",
                record.adaptiveDeltaTMaxEstimateSeconds
        );
        row.put(
                "adaptiveDeltaTMaxSampleCount",
                record.adaptiveDeltaTMaxSampleCount
        );
        row.put(
                "adaptiveDeltaTMaxP95Seconds",
                record.adaptiveDeltaTMaxP95Seconds
        );
        row.put(
                "adaptiveDeltaTMaxTargetSeconds",
                record.adaptiveDeltaTMaxTargetSeconds
        );
        row.put(
                "adaptiveDeltaTMaxClampedSeconds",
                record.adaptiveDeltaTMaxClampedSeconds
        );
        row.put(
                "adaptiveDeltaTMaxPreviousSeconds",
                record.adaptiveDeltaTMaxPreviousSeconds
        );
        row.put(
                "adaptiveDeltaTMaxUpdatedSeconds",
                record.adaptiveDeltaTMaxUpdatedSeconds
        );
        row.put(
                "adaptiveDeltaTMaxFallbackReason",
                record.adaptiveDeltaTMaxFallbackReason
        );
        row.put(
                "submissionAdaptiveDeltaTMaxMode",
                record.submissionAdaptiveDeltaTMaxMode
        );
        row.put(
                "submissionAdaptiveDeltaTMaxEstimateSeconds",
                record.submissionAdaptiveDeltaTMaxEstimateSeconds
        );
        row.put(
                "submissionAdaptiveDeltaTMaxSampleCount",
                record.submissionAdaptiveDeltaTMaxSampleCount
        );
        row.put(
                "submissionAdaptiveDeltaTMaxP95Seconds",
                record.submissionAdaptiveDeltaTMaxP95Seconds
        );
        row.put(
                "submissionAdaptiveDeltaTMaxTargetSeconds",
                record.submissionAdaptiveDeltaTMaxTargetSeconds
        );
        row.put(
                "submissionAdaptiveDeltaTMaxClampedSeconds",
                record.submissionAdaptiveDeltaTMaxClampedSeconds
        );
        row.put(
                "submissionAdaptiveDeltaTMaxPreviousSeconds",
                record.submissionAdaptiveDeltaTMaxPreviousSeconds
        );
        row.put(
                "submissionAdaptiveDeltaTMaxSelectedSeconds",
                record.submissionAdaptiveDeltaTMaxSelectedSeconds
        );
        row.put(
                "submissionAdaptiveDeltaTMaxFallbackReason",
                record.submissionAdaptiveDeltaTMaxFallbackReason
        );
        row.put(
                "postCompletionAdaptiveDeltaTMaxSampleAccepted",
                record.postCompletionAdaptiveDeltaTMaxSampleAccepted
        );
        row.put(
                "postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds",
                record.postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds
        );
        row.put(
                "postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion",
                record.postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion
        );
        row.put(
                "postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds",
                record.postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds
        );
        row.put(
                "postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds",
                record.postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds
        );
        row.put(
                "postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds",
                record.postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds
        );
        row.put(
                "postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds",
                record.postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds
        );
        row.put(
                "postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds",
                record.postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds
        );
        row.put(
                "postCompletionAdaptiveDeltaTMaxFallbackReason",
                record.postCompletionAdaptiveDeltaTMaxFallbackReason
        );
        row.put("errorType", record.errorType);
        row.put("errorMessage", record.errorMessage);
        eventWriter.write(gson.toJson(row));
        eventWriter.write('\n');
        eventWriter.flush();
    }

    void writeStep(LiveTemporalStepRecord record) throws IOException {
        stepWriter.write(gson.toJson(record));
        stepWriter.write('\n');
        stepWriter.flush();
    }

    void writeWindowCsv(
            LiveWindowCsvRecord record,
            boolean applied
    ) throws IOException {
        BufferedWriter writer = applied
                ? appliedCsvWriter
                : discardedCsvWriter;
        writer.write(record.jobId
                + "," + record.windowIndex
                + "," + safe(record.snapshotId)
                + "," + format(record.snapshotTimeSeconds)
                + "," + record.submissionSimulationTimeNs
                + "," + format(record.gaRuntimeWallClockSeconds)
                + "," + format(record.deltaTMaxSeconds)
                + "," + format(record.fitness)
                + "," + record.localAssignments
                + "," + record.vehicleAssignments
                + "," + record.edgeAssignments
                + "," + record.cloudAssignments
                + "," + record.localTaskPortions
                + "," + record.vehiclesWithLocalWorkload
                + "," + record.vehiclesWithLocalContention
                + "," + record.vehiclesWithLocalCpuOverflow
                + "," + record.localDeadlineViolations
                + "," + format(
                        record.maxIndependentLocalExecutionTimeSeconds
                )
                + "," + format(
                        record.maxContendedLocalCompletionTimeSeconds
                )
                + "," + format(record.maxLocalContentionDelaySeconds)
                + "," + format(record.maxLocalDemandRatio)
                + "," + format(record.maxLocalCpuOverflowRatio)
                + "," + record.status
                + "," + safe(record.deltaTMaxMode)
                + "," + format(record.adaptiveDeltaTMaxEstimateSeconds)
                + "," + record.adaptiveDeltaTMaxSampleCount
                + "," + format(record.adaptiveDeltaTMaxP95Seconds)
                + "," + format(record.adaptiveDeltaTMaxTargetSeconds)
                + "," + format(record.adaptiveDeltaTMaxClampedSeconds)
                + "," + format(record.adaptiveDeltaTMaxPreviousSeconds)
                + "," + format(record.adaptiveDeltaTMaxUpdatedSeconds)
                + "," + safe(record.adaptiveDeltaTMaxFallbackReason)
                + "," + safe(record.submissionAdaptiveDeltaTMaxMode)
                + "," + format(record.submissionAdaptiveDeltaTMaxEstimateSeconds)
                + "," + record.submissionAdaptiveDeltaTMaxSampleCount
                + "," + format(record.submissionAdaptiveDeltaTMaxP95Seconds)
                + "," + format(record.submissionAdaptiveDeltaTMaxTargetSeconds)
                + "," + format(record.submissionAdaptiveDeltaTMaxClampedSeconds)
                + "," + format(record.submissionAdaptiveDeltaTMaxPreviousSeconds)
                + "," + format(record.submissionAdaptiveDeltaTMaxSelectedSeconds)
                + "," + safe(record.submissionAdaptiveDeltaTMaxFallbackReason)
                + "," + record.postCompletionAdaptiveDeltaTMaxSampleAccepted
                + "," + format(record.postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds)
                + "," + record.postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion
                + "," + format(record.postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds)
                + "," + format(record.postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds)
                + "," + format(record.postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds)
                + "," + format(record.postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds)
                + "," + format(record.postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds)
                + "," + safe(record.postCompletionAdaptiveDeltaTMaxFallbackReason)
                + "\n");
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        eventWriter.close();
        stepWriter.close();
        appliedCsvWriter.close();
        discardedCsvWriter.close();
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace(',', ';');
    }

    private static String format(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.9f", value)
                : "";
    }

    static final class LiveWindowCsvRecord {
        final String jobId;
        final int windowIndex;
        final String snapshotId;
        final double snapshotTimeSeconds;
        final long submissionSimulationTimeNs;
        final double gaRuntimeWallClockSeconds;
        final double deltaTMaxSeconds;
        final double fitness;
        final int localAssignments;
        final int vehicleAssignments;
        final int edgeAssignments;
        final int cloudAssignments;
        final int localTaskPortions;
        final int vehiclesWithLocalWorkload;
        final int vehiclesWithLocalContention;
        final int vehiclesWithLocalCpuOverflow;
        final int localDeadlineViolations;
        final double maxIndependentLocalExecutionTimeSeconds;
        final double maxContendedLocalCompletionTimeSeconds;
        final double maxLocalContentionDelaySeconds;
        final double maxLocalDemandRatio;
        final double maxLocalCpuOverflowRatio;
        final String status;
        final String deltaTMaxMode;
        final double adaptiveDeltaTMaxEstimateSeconds;
        final int adaptiveDeltaTMaxSampleCount;
        final double adaptiveDeltaTMaxP95Seconds;
        final double adaptiveDeltaTMaxTargetSeconds;
        final double adaptiveDeltaTMaxClampedSeconds;
        final double adaptiveDeltaTMaxPreviousSeconds;
        final double adaptiveDeltaTMaxUpdatedSeconds;
        final String adaptiveDeltaTMaxFallbackReason;
        final String submissionAdaptiveDeltaTMaxMode;
        final double submissionAdaptiveDeltaTMaxEstimateSeconds;
        final int submissionAdaptiveDeltaTMaxSampleCount;
        final double submissionAdaptiveDeltaTMaxP95Seconds;
        final double submissionAdaptiveDeltaTMaxTargetSeconds;
        final double submissionAdaptiveDeltaTMaxClampedSeconds;
        final double submissionAdaptiveDeltaTMaxPreviousSeconds;
        final double submissionAdaptiveDeltaTMaxSelectedSeconds;
        final String submissionAdaptiveDeltaTMaxFallbackReason;
        final boolean postCompletionAdaptiveDeltaTMaxSampleAccepted;
        final double postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds;
        final int postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion;
        final double postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds;
        final double postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds;
        final double postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds;
        final double postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds;
        final double postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds;
        final String postCompletionAdaptiveDeltaTMaxFallbackReason;

        LiveWindowCsvRecord(
                String jobId,
                int windowIndex,
                String snapshotId,
                double snapshotTimeSeconds,
                long submissionSimulationTimeNs,
                double gaRuntimeWallClockSeconds,
                double deltaTMaxSeconds,
                double fitness,
                int localAssignments,
                int vehicleAssignments,
                int edgeAssignments,
                int cloudAssignments,
                int localTaskPortions,
                int vehiclesWithLocalWorkload,
                int vehiclesWithLocalContention,
                int vehiclesWithLocalCpuOverflow,
                int localDeadlineViolations,
                double maxIndependentLocalExecutionTimeSeconds,
                double maxContendedLocalCompletionTimeSeconds,
                double maxLocalContentionDelaySeconds,
                double maxLocalDemandRatio,
                double maxLocalCpuOverflowRatio,
                String status
        ) {
            this(
                    jobId,
                    windowIndex,
                    snapshotId,
                    snapshotTimeSeconds,
                    submissionSimulationTimeNs,
                    gaRuntimeWallClockSeconds,
                    deltaTMaxSeconds,
                    fitness,
                    localAssignments,
                    vehicleAssignments,
                    edgeAssignments,
                    cloudAssignments,
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
                    status,
                    "CONFIGURED_STATIC",
                    deltaTMaxSeconds,
                    0,
                    0.0,
                    deltaTMaxSeconds,
                    deltaTMaxSeconds,
                    deltaTMaxSeconds,
                    deltaTMaxSeconds,
                    "CONFIGURED_STATIC",
                    "CONFIGURED_STATIC",
                    deltaTMaxSeconds,
                    0,
                    0.0,
                    deltaTMaxSeconds,
                    deltaTMaxSeconds,
                    deltaTMaxSeconds,
                    deltaTMaxSeconds,
                    "CONFIGURED_STATIC",
                    false,
                    0.0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    ""
            );
        }

        LiveWindowCsvRecord(
                String jobId,
                int windowIndex,
                String snapshotId,
                double snapshotTimeSeconds,
                long submissionSimulationTimeNs,
                double gaRuntimeWallClockSeconds,
                double deltaTMaxSeconds,
                double fitness,
                int localAssignments,
                int vehicleAssignments,
                int edgeAssignments,
                int cloudAssignments,
                int localTaskPortions,
                int vehiclesWithLocalWorkload,
                int vehiclesWithLocalContention,
                int vehiclesWithLocalCpuOverflow,
                int localDeadlineViolations,
                double maxIndependentLocalExecutionTimeSeconds,
                double maxContendedLocalCompletionTimeSeconds,
                double maxLocalContentionDelaySeconds,
                double maxLocalDemandRatio,
                double maxLocalCpuOverflowRatio,
                String status,
                String deltaTMaxMode,
                double adaptiveDeltaTMaxEstimateSeconds,
                int adaptiveDeltaTMaxSampleCount,
                double adaptiveDeltaTMaxP95Seconds,
                double adaptiveDeltaTMaxTargetSeconds,
                double adaptiveDeltaTMaxClampedSeconds,
                double adaptiveDeltaTMaxPreviousSeconds,
                double adaptiveDeltaTMaxUpdatedSeconds,
                String adaptiveDeltaTMaxFallbackReason,
                String submissionAdaptiveDeltaTMaxMode,
                double submissionAdaptiveDeltaTMaxEstimateSeconds,
                int submissionAdaptiveDeltaTMaxSampleCount,
                double submissionAdaptiveDeltaTMaxP95Seconds,
                double submissionAdaptiveDeltaTMaxTargetSeconds,
                double submissionAdaptiveDeltaTMaxClampedSeconds,
                double submissionAdaptiveDeltaTMaxPreviousSeconds,
                double submissionAdaptiveDeltaTMaxSelectedSeconds,
                String submissionAdaptiveDeltaTMaxFallbackReason,
                boolean postCompletionAdaptiveDeltaTMaxSampleAccepted,
                double postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds,
                int postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion,
                double postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds,
                double postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds,
                double postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds,
                double postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds,
                double postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds,
                String postCompletionAdaptiveDeltaTMaxFallbackReason
        ) {
            this.jobId = jobId;
            this.windowIndex = windowIndex;
            this.snapshotId = snapshotId;
            this.snapshotTimeSeconds = snapshotTimeSeconds;
            this.submissionSimulationTimeNs = submissionSimulationTimeNs;
            this.gaRuntimeWallClockSeconds = gaRuntimeWallClockSeconds;
            this.deltaTMaxSeconds = deltaTMaxSeconds;
            this.fitness = fitness;
            this.localAssignments = localAssignments;
            this.vehicleAssignments = vehicleAssignments;
            this.edgeAssignments = edgeAssignments;
            this.cloudAssignments = cloudAssignments;
            this.localTaskPortions = localTaskPortions;
            this.vehiclesWithLocalWorkload = vehiclesWithLocalWorkload;
            this.vehiclesWithLocalContention = vehiclesWithLocalContention;
            this.vehiclesWithLocalCpuOverflow = vehiclesWithLocalCpuOverflow;
            this.localDeadlineViolations = localDeadlineViolations;
            this.maxIndependentLocalExecutionTimeSeconds =
                    maxIndependentLocalExecutionTimeSeconds;
            this.maxContendedLocalCompletionTimeSeconds =
                    maxContendedLocalCompletionTimeSeconds;
            this.maxLocalContentionDelaySeconds =
                    maxLocalContentionDelaySeconds;
            this.maxLocalDemandRatio = maxLocalDemandRatio;
            this.maxLocalCpuOverflowRatio = maxLocalCpuOverflowRatio;
            this.status = status;
            this.deltaTMaxMode = deltaTMaxMode;
            this.adaptiveDeltaTMaxEstimateSeconds =
                    adaptiveDeltaTMaxEstimateSeconds;
            this.adaptiveDeltaTMaxSampleCount = adaptiveDeltaTMaxSampleCount;
            this.adaptiveDeltaTMaxP95Seconds = adaptiveDeltaTMaxP95Seconds;
            this.adaptiveDeltaTMaxTargetSeconds =
                    adaptiveDeltaTMaxTargetSeconds;
            this.adaptiveDeltaTMaxClampedSeconds =
                    adaptiveDeltaTMaxClampedSeconds;
            this.adaptiveDeltaTMaxPreviousSeconds =
                    adaptiveDeltaTMaxPreviousSeconds;
            this.adaptiveDeltaTMaxUpdatedSeconds =
                    adaptiveDeltaTMaxUpdatedSeconds;
            this.adaptiveDeltaTMaxFallbackReason =
                    adaptiveDeltaTMaxFallbackReason;
            this.submissionAdaptiveDeltaTMaxMode =
                    submissionAdaptiveDeltaTMaxMode;
            this.submissionAdaptiveDeltaTMaxEstimateSeconds =
                    submissionAdaptiveDeltaTMaxEstimateSeconds;
            this.submissionAdaptiveDeltaTMaxSampleCount =
                    submissionAdaptiveDeltaTMaxSampleCount;
            this.submissionAdaptiveDeltaTMaxP95Seconds =
                    submissionAdaptiveDeltaTMaxP95Seconds;
            this.submissionAdaptiveDeltaTMaxTargetSeconds =
                    submissionAdaptiveDeltaTMaxTargetSeconds;
            this.submissionAdaptiveDeltaTMaxClampedSeconds =
                    submissionAdaptiveDeltaTMaxClampedSeconds;
            this.submissionAdaptiveDeltaTMaxPreviousSeconds =
                    submissionAdaptiveDeltaTMaxPreviousSeconds;
            this.submissionAdaptiveDeltaTMaxSelectedSeconds =
                    submissionAdaptiveDeltaTMaxSelectedSeconds;
            this.submissionAdaptiveDeltaTMaxFallbackReason =
                    submissionAdaptiveDeltaTMaxFallbackReason;
            this.postCompletionAdaptiveDeltaTMaxSampleAccepted =
                    postCompletionAdaptiveDeltaTMaxSampleAccepted;
            this.postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds =
                    postCompletionAdaptiveDeltaTMaxSampleRuntimeSeconds;
            this.postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion =
                    postCompletionAdaptiveDeltaTMaxSampleCountAfterCompletion;
            this.postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds =
                    postCompletionAdaptiveDeltaTMaxP95AfterCompletionSeconds;
            this.postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds =
                    postCompletionAdaptiveDeltaTMaxTargetAfterCompletionSeconds;
            this.postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds =
                    postCompletionAdaptiveDeltaTMaxClampedAfterCompletionSeconds;
            this.postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds =
                    postCompletionAdaptiveDeltaTMaxPreviousBeforeUpdateSeconds;
            this.postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds =
                    postCompletionAdaptiveDeltaTMaxUpdatedForNextSubmissionSeconds;
            this.postCompletionAdaptiveDeltaTMaxFallbackReason =
                    postCompletionAdaptiveDeltaTMaxFallbackReason;
        }
    }
}
