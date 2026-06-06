package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LiveReportingSummary {
    public final String scenarioName;
    public final String profile;
    public final String bridgeDescription;
    public final String sourceMode;
    public final String configuredCellProfile;
    public final String runtimeAccountingSource;
    public final int submitted;
    public final int completed;
    public final int applied;
    public final int staleDiscarded;
    public final int failed;
    public final int nullResults;
    public final int shutdownInFlight;
    public final int localAssignments;
    public final int vehicleAssignments;
    public final int edgeAssignments;
    public final int cloudAssignments;
    public final Map<String, Object> wallClockTiming;
    public final List<LiveGaJobRecord> jobRecords;
    public final List<LiveWindowSummary> appliedWindowSummaries;
    public final List<LiveWindowSummary> discardedWindowSummaries;
    public final Map<String, String> artifacts;

    private LiveReportingSummary(
            String scenarioName,
            String profile,
            String bridgeDescription,
            String sourceMode,
            String configuredCellProfile,
            String runtimeAccountingSource,
            List<LiveGaJobRecord> records,
            List<LiveWindowSummary> appliedWindowSummaries,
            List<LiveWindowSummary> discardedWindowSummaries,
            Map<String, String> artifacts
    ) {
        this.scenarioName = scenarioName;
        this.profile = profile;
        this.bridgeDescription = bridgeDescription;
        this.sourceMode = sourceMode;
        this.configuredCellProfile = configuredCellProfile;
        this.runtimeAccountingSource = runtimeAccountingSource;
        this.jobRecords = List.copyOf(records);
        this.appliedWindowSummaries = List.copyOf(appliedWindowSummaries);
        this.discardedWindowSummaries = List.copyOf(discardedWindowSummaries);
        this.artifacts = Map.copyOf(artifacts);

        int submittedCount = records.size();
        int completedCount = 0;
        int appliedCount = 0;
        int staleCount = 0;
        int failedCount = 0;
        int nullCount = 0;
        int shutdownCount = 0;
        for (LiveGaJobRecord record : records) {
            if (record.completionWallClockNs > 0L) {
                completedCount++;
            }
            if ("APPLIED".equals(record.finalStatus)) {
                appliedCount++;
            } else if ("STALE_DISCARDED".equals(record.finalStatus)) {
                staleCount++;
            } else if ("FAILED".equals(record.finalStatus)) {
                failedCount++;
            } else if ("NULL_STEP_RESULT".equals(record.finalStatus)) {
                nullCount++;
            } else if ("SHUTDOWN_IN_FLIGHT".equals(record.finalStatus)) {
                shutdownCount++;
            }
        }
        this.submitted = submittedCount;
        this.completed = completedCount;
        this.applied = appliedCount;
        this.staleDiscarded = staleCount;
        this.failed = failedCount;
        this.nullResults = nullCount;
        this.shutdownInFlight = shutdownCount;

        int local = 0;
        int vehicle = 0;
        int edge = 0;
        int cloud = 0;
        for (LiveWindowSummary summary : appliedWindowSummaries) {
            local += summary.localAssignments;
            vehicle += summary.vehicleAssignments;
            edge += summary.edgeAssignments;
            cloud += summary.cloudAssignments;
        }
        this.localAssignments = local;
        this.vehicleAssignments = vehicle;
        this.edgeAssignments = edge;
        this.cloudAssignments = cloud;
        this.wallClockTiming = timing(records);
    }

    static LiveReportingSummary from(
            String scenarioName,
            String profile,
            String bridgeDescription,
            String sourceMode,
            String configuredCellProfile,
            String runtimeAccountingSource,
            List<LiveGaJobRecord> records,
            List<LiveWindowSummary> appliedWindowSummaries,
            List<LiveWindowSummary> discardedWindowSummaries,
            Path reportingDir
    ) {
        Map<String, String> artifacts = new LinkedHashMap<>();
        artifacts.put("eventsJsonl", reportingDir.resolve("live_ga_job_events.jsonl").toString());
        artifacts.put("stepRecordsJsonl", reportingDir.resolve("live_temporal_step_records.jsonl").toString());
        artifacts.put("appliedCsv", reportingDir.resolve("live_applied_window_records.csv").toString());
        artifacts.put("discardedCsv", reportingDir.resolve("live_discarded_window_records.csv").toString());
        artifacts.put("txt", reportingDir.resolve("live_detailed_execution_report.txt").toString());
        artifacts.put("markdown", reportingDir.resolve("live_detailed_execution_report.md").toString());
        artifacts.put("json", reportingDir.resolve("live_detailed_execution_report.json").toString());
        return new LiveReportingSummary(
                scenarioName,
                profile,
                bridgeDescription,
                sourceMode,
                configuredCellProfile,
                runtimeAccountingSource,
                records,
                appliedWindowSummaries,
                discardedWindowSummaries,
                artifacts
        );
    }

    private static Map<String, Object> timing(List<LiveGaJobRecord> records) {
        List<Double> values = new ArrayList<>();
        for (LiveGaJobRecord record : records) {
            if (record.gaRuntimeWallClockSeconds > 0.0) {
                values.add(record.gaRuntimeWallClockSeconds);
            }
        }
        values.sort(Comparator.naturalOrder());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("count", values.size());
        row.put("min", values.isEmpty() ? null : values.get(0));
        row.put("max", values.isEmpty() ? null : values.get(values.size() - 1));
        row.put("mean", mean(values));
        row.put("median", percentile(values, 0.50));
        row.put("p95", percentile(values, 0.95));
        return row;
    }

    private static Double mean(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static Double percentile(List<Double> values, double p) {
        if (values.isEmpty()) {
            return null;
        }
        int index = (int) Math.ceil(p * values.size()) - 1;
        index = Math.max(0, Math.min(values.size() - 1, index));
        return values.get(index);
    }

    public static final class LiveWindowSummary {
        public final String jobId;
        public final int windowIndex;
        public final String snapshotId;
        public final double snapshotTimeSeconds;
        public final double fitness;
        public final int localAssignments;
        public final int vehicleAssignments;
        public final int edgeAssignments;
        public final int cloudAssignments;
        public final String status;

        public LiveWindowSummary(
                String jobId,
                int windowIndex,
                String snapshotId,
                double snapshotTimeSeconds,
                double fitness,
                int localAssignments,
                int vehicleAssignments,
                int edgeAssignments,
                int cloudAssignments,
                String status
        ) {
            this.jobId = jobId;
            this.windowIndex = windowIndex;
            this.snapshotId = snapshotId;
            this.snapshotTimeSeconds = snapshotTimeSeconds;
            this.fitness = fitness;
            this.localAssignments = localAssignments;
            this.vehicleAssignments = vehicleAssignments;
            this.edgeAssignments = edgeAssignments;
            this.cloudAssignments = cloudAssignments;
            this.status = status;
        }
    }
}
