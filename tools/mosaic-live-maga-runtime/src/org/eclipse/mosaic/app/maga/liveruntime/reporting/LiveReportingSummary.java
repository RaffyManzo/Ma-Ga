package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import config.MaGaConfig;
import config.fitness.FitnessWeights;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LiveReportingSummary {
    public final String scenarioName;
    public final String profile;
    public final String experimentalVariant;
    public final Map<String, Double> effectiveFitnessWeights;
    public final String bridgeDescription;
    public final String sourceMode;
    public final String optimizationSourceDescription;
    public final String populationReusePolicyDescription;
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
    public final int localTaskPortions;
    public final int localContentionVehicleWindows;
    public final int localCpuOverflowVehicleWindows;
    public final int windowsWithLocalContention;
    public final int windowsWithLocalCpuOverflow;
    public final int localDeadlineViolations;
    public final double maxIndependentLocalExecutionTimeSeconds;
    public final double maxContendedLocalCompletionTimeSeconds;
    public final double maxLocalContentionDelaySeconds;
    public final double maxLocalDemandRatio;
    public final double maxLocalCpuOverflowRatio;
    public final Map<String, Object> wallClockTiming;
    public final List<LiveGaJobRecord> jobRecords;
    public final List<LiveWindowSummary> appliedWindowSummaries;
    public final List<LiveWindowSummary> discardedWindowSummaries;
    public final Map<String, String> artifacts;

    private LiveReportingSummary(
            String scenarioName,
            String profile,
            String experimentalVariant,
            Map<String, Double> effectiveFitnessWeights,
            String bridgeDescription,
            String sourceMode,
            String optimizationSourceDescription,
            String populationReusePolicyDescription,
            String configuredCellProfile,
            String runtimeAccountingSource,
            List<LiveGaJobRecord> records,
            List<LiveWindowSummary> appliedWindowSummaries,
            List<LiveWindowSummary> discardedWindowSummaries,
            Map<String, String> artifacts
    ) {
        this.scenarioName = scenarioName;
        this.profile = profile;
        this.experimentalVariant = experimentalVariant;
        this.effectiveFitnessWeights = Map.copyOf(effectiveFitnessWeights);
        this.bridgeDescription = bridgeDescription;
        this.sourceMode = sourceMode;
        this.optimizationSourceDescription = optimizationSourceDescription;
        this.populationReusePolicyDescription = populationReusePolicyDescription;
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
        int localPortions = 0;
        int contentionVehicleWindows = 0;
        int overflowVehicleWindows = 0;
        int contentionWindows = 0;
        int overflowWindows = 0;
        int deadlineViolations = 0;
        double maxIndependentLocalTime = 0.0;
        double maxContendedLocalTime = 0.0;
        double maxContentionDelay = 0.0;
        double maxDemandRatio = 0.0;
        double maxOverflowRatio = 0.0;

        for (LiveWindowSummary summary : appliedWindowSummaries) {
            local += summary.localAssignments;
            vehicle += summary.vehicleAssignments;
            edge += summary.edgeAssignments;
            cloud += summary.cloudAssignments;
            localPortions += summary.localTaskPortions;
            contentionVehicleWindows += summary.vehiclesWithLocalContention;
            overflowVehicleWindows += summary.vehiclesWithLocalCpuOverflow;
            deadlineViolations += summary.localDeadlineViolations;

            if (summary.vehiclesWithLocalContention > 0) {
                contentionWindows++;
            }
            if (summary.vehiclesWithLocalCpuOverflow > 0) {
                overflowWindows++;
            }

            maxIndependentLocalTime = Math.max(
                    maxIndependentLocalTime,
                    summary.maxIndependentLocalExecutionTimeSeconds
            );
            maxContendedLocalTime = Math.max(
                    maxContendedLocalTime,
                    summary.maxContendedLocalCompletionTimeSeconds
            );
            maxContentionDelay = Math.max(
                    maxContentionDelay,
                    summary.maxLocalContentionDelaySeconds
            );
            maxDemandRatio = Math.max(
                    maxDemandRatio,
                    summary.maxLocalDemandRatio
            );
            maxOverflowRatio = Math.max(
                    maxOverflowRatio,
                    summary.maxLocalCpuOverflowRatio
            );
        }

        this.localAssignments = local;
        this.vehicleAssignments = vehicle;
        this.edgeAssignments = edge;
        this.cloudAssignments = cloud;
        this.localTaskPortions = localPortions;
        this.localContentionVehicleWindows = contentionVehicleWindows;
        this.localCpuOverflowVehicleWindows = overflowVehicleWindows;
        this.windowsWithLocalContention = contentionWindows;
        this.windowsWithLocalCpuOverflow = overflowWindows;
        this.localDeadlineViolations = deadlineViolations;
        this.maxIndependentLocalExecutionTimeSeconds = maxIndependentLocalTime;
        this.maxContendedLocalCompletionTimeSeconds = maxContendedLocalTime;
        this.maxLocalContentionDelaySeconds = maxContentionDelay;
        this.maxLocalDemandRatio = maxDemandRatio;
        this.maxLocalCpuOverflowRatio = maxOverflowRatio;
        this.wallClockTiming = timing(records);
    }

    static LiveReportingSummary from(
            String scenarioName,
            String profile,
            String experimentalVariant,
            String optimizationSourceDescription,
            String populationReusePolicyDescription,
            String bridgeDescription,
            String sourceMode,
            String configuredCellProfile,
            String runtimeAccountingSource,
            List<LiveGaJobRecord> records,
            List<LiveWindowSummary> appliedWindowSummaries,
            List<LiveWindowSummary> discardedWindowSummaries,
            MaGaConfig maGaConfig,
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
                experimentalVariant,
                fitnessWeights(maGaConfig),
                bridgeDescription,
                sourceMode,
                optimizationSourceDescription,
                populationReusePolicyDescription,
                configuredCellProfile,
                runtimeAccountingSource,
                records,
                appliedWindowSummaries,
                discardedWindowSummaries,
                artifacts
        );
    }

    private static Map<String, Double> fitnessWeights(MaGaConfig maGaConfig) {
        FitnessWeights weights = maGaConfig.getFitnessWeights();
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("wT", weights.getCompletionTimeWeight());
        values.put("wL", weights.getCommunicationLatencyWeight());
        values.put("wM", weights.getMobilityPenaltyWeight());
        values.put("wR", weights.getResourcePenaltyWeight());
        return values;
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
        public final int localTaskPortions;
        public final int vehiclesWithLocalWorkload;
        public final int vehiclesWithLocalContention;
        public final int vehiclesWithLocalCpuOverflow;
        public final int localDeadlineViolations;
        public final double maxIndependentLocalExecutionTimeSeconds;
        public final double maxContendedLocalCompletionTimeSeconds;
        public final double maxLocalContentionDelaySeconds;
        public final double maxLocalDemandRatio;
        public final double maxLocalCpuOverflowRatio;
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
            this(
                    jobId,
                    windowIndex,
                    snapshotId,
                    snapshotTimeSeconds,
                    fitness,
                    localAssignments,
                    vehicleAssignments,
                    edgeAssignments,
                    cloudAssignments,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    status
            );
        }

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
