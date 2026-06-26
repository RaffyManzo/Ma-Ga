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
                + "maxLocalCpuOverflowRatio,status\n";
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
        }
    }
}
