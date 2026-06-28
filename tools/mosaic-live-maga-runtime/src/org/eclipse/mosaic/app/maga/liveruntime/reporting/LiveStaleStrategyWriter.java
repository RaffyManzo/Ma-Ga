package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import com.google.gson.Gson;
import org.eclipse.mosaic.app.maga.liveruntime.LiveAppliedStrategy;
import org.eclipse.mosaic.app.maga.liveruntime.LiveAssignmentDecision;
import org.eclipse.mosaic.app.maga.liveruntime.LiveStaleReason;
import window.state.TemporalStepResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writes full stale strategies and a per-task comparison with the active strategy. */
public final class LiveStaleStrategyWriter implements AutoCloseable {
    private static final double NS_PER_SECOND = 1_000_000_000.0;
    private final Gson gson = new Gson();
    private final BufferedWriter decisions;
    private final BufferedWriter distributions;
    private final BufferedWriter summaries;
    private final BufferedWriter comparisons;
    private final BufferedWriter transitionMatrix;

    public LiveStaleStrategyWriter(Path reportingDir) throws IOException {
        Files.createDirectories(reportingDir);
        decisions = open(reportingDir.resolve("live_stale_assignment_decisions.csv"));
        distributions = open(reportingDir.resolve("live_stale_assignment_distribution.csv"));
        summaries = open(reportingDir.resolve("live_stale_strategy_summary.jsonl"));
        comparisons = open(reportingDir.resolve("live_stale_vs_active_strategy.csv"));
        transitionMatrix = open(reportingDir.resolve("live_stale_vs_active_transition_matrix.csv"));
        decisions.write("jobId,staleReason,snapshotId,snapshotTimeSeconds,classificationSimulationTimeSeconds,snapshotAgeSimulationSeconds,wallClockRuntimeSeconds,gaWallClockBudgetSeconds,temporalMaximumSeconds,freshnessCapSeconds,taskId,sourceVehicleId,nodeType,selectedCandidateId,executionNodeId,decisionType,offloadingRatio,allocatedCpu,allocatedBandwidth,completionTimeSeconds,communicationLatencySeconds,mobilityPenalty,constraintPenalty,deadlineSeconds,deadlineRespected,coverageTimeSeconds,coverageSufficient,coverageRisk,linkInstability,handoverRisk\n");
        distributions.write("jobId,staleReason,snapshotId,totalAssignments,localAssignments,vehicleAssignments,edgeAssignments,cloudAssignments,localPercentage,vehiclePercentage,edgePercentage,cloudPercentage,activeStrategyPresent,activeSnapshotId,activeSnapshotAgeSimulationSeconds\n");
        comparisons.write("jobId,staleReason,taskId,comparisonStatus,transition,staleNodeType,staleCandidateId,activeNodeType,activeCandidateId\n");
        transitionMatrix.write("jobId,staleReason,transition,count,percentageOfComparedTasks\n");
    }

    public synchronized void writeStale(
            String jobId,
            TemporalStepResult step,
            LiveStaleReason staleReason,
            long classificationSimulationTimeNs,
            double wallClockRuntimeSeconds,
            double gaWallClockBudgetSeconds,
            double temporalMaximumSeconds,
            double freshnessCapSeconds,
            LiveAppliedStrategy active
    ) throws IOException {
        double classificationSeconds = classificationSimulationTimeNs / NS_PER_SECOND;
        double age = Math.max(0.0, classificationSeconds - step.getSnapshot().getTimeSeconds());
        Map<String, LiveAssignmentDecision> stale = new LinkedHashMap<>();
        int local = 0;
        int vehicle = 0;
        int edge = 0;
        int cloud = 0;
        for (var gene : step.getMaGaResult().getBestEvaluation().getGeneBreakdowns()) {
            LiveAssignmentDecision d = LiveAssignmentDecision.from(gene);
            stale.put(d.getTaskId(), d);
            switch (d.getNodeType()) {
                case LOCAL: local++; break;
                case VEHICLE: vehicle++; break;
                case EDGE: edge++; break;
                case CLOUD: cloud++; break;
                default: break;
            }
            decisions.write(csv(jobId, staleReason.name(), step.getSnapshot().getSnapshotId(),
                    step.getSnapshot().getTimeSeconds(), classificationSeconds, age,
                    wallClockRuntimeSeconds, gaWallClockBudgetSeconds,
                    temporalMaximumSeconds, freshnessCapSeconds, d.getTaskId(),
                    d.getSourceVehicleId(), d.getNodeType().name(),
                    d.getSelectedCandidateId(), d.getExecutionNodeId(),
                    d.getDecisionType(), d.getOffloadingRatio(), d.getAllocatedCpu(),
                    d.getAllocatedBandwidth(), d.getCompletionTimeSeconds(),
                    d.getCommunicationLatencySeconds(), d.getMobilityPenalty(),
                    d.getConstraintPenalty(), d.getDeadlineSeconds(),
                    d.isDeadlineRespected(), d.getCoverageTimeSeconds(),
                    d.isCoverageSufficient(), d.getCoverageRisk(),
                    d.getLinkInstability(), d.getHandoverRisk()));
            decisions.newLine();
        }

        int totalAssignments = stale.size();
        boolean activePresent = active != null;
        Double activeAge = activePresent
                ? Math.max(0.0, classificationSeconds - active.getSnapshotTimeSeconds())
                : null;
        distributions.write(csv(
                jobId, staleReason.name(), step.getSnapshot().getSnapshotId(),
                totalAssignments, local, vehicle, edge, cloud,
                percentage(local, totalAssignments),
                percentage(vehicle, totalAssignments),
                percentage(edge, totalAssignments),
                percentage(cloud, totalAssignments),
                activePresent,
                activePresent ? active.getSnapshotId() : "",
                activeAge
        ));
        distributions.newLine();

        Map<String, LiveAssignmentDecision> activeMap = activePresent
                ? active.getAssignments() : Map.of();
        Set<String> tasks = new LinkedHashSet<>();
        tasks.addAll(stale.keySet());
        tasks.addAll(activeMap.keySet());
        int same = 0;
        int changed = 0;
        int staleOnly = 0;
        int activeOnly = 0;
        Map<String, Integer> transitions = new LinkedHashMap<>();
        for (String taskId : tasks) {
            LiveAssignmentDecision s = stale.get(taskId);
            LiveAssignmentDecision a = activeMap.get(taskId);
            String status;
            String transition;
            if (s != null && a != null) {
                if (s.samePlacement(a)) {
                    status = "COMMON_SAME_ASSIGNMENT";
                    transition = "SAME_" + s.getNodeType().name();
                    same++;
                } else {
                    status = "COMMON_CHANGED_ASSIGNMENT";
                    transition = a.getNodeType().name() + "->" + s.getNodeType().name();
                    changed++;
                }
            } else if (s != null) {
                status = "UNASSIGNED_BY_ACTIVE_STRATEGY";
                transition = "UNASSIGNED_BY_ACTIVE_STRATEGY->" + s.getNodeType().name();
                staleOnly++;
            } else {
                status = "ACTIVE_ONLY_NOT_IN_STALE_SNAPSHOT";
                transition = a.getNodeType().name() + "->ACTIVE_ONLY_NOT_IN_STALE_SNAPSHOT";
                activeOnly++;
            }
            transitions.merge(transition, 1, Integer::sum);
            comparisons.write(csv(
                    jobId, staleReason.name(), taskId, status, transition,
                    s == null ? "" : s.getNodeType().name(),
                    s == null ? "" : s.getSelectedCandidateId(),
                    a == null ? "" : a.getNodeType().name(),
                    a == null ? "" : a.getSelectedCandidateId()
            ));
            comparisons.newLine();
        }

        int comparedTasks = tasks.size();
        for (Map.Entry<String, Integer> entry : transitions.entrySet()) {
            transitionMatrix.write(csv(
                    jobId,
                    staleReason.name(),
                    entry.getKey(),
                    entry.getValue(),
                    percentage(entry.getValue(), comparedTasks)
            ));
            transitionMatrix.newLine();
        }

        Map<String, Object> distribution = new LinkedHashMap<>();
        distribution.put("LOCAL", assignmentBucket(local, totalAssignments));
        distribution.put("VEHICLE", assignmentBucket(vehicle, totalAssignments));
        distribution.put("EDGE", assignmentBucket(edge, totalAssignments));
        distribution.put("CLOUD", assignmentBucket(cloud, totalAssignments));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("jobId", jobId);
        summary.put("staleReason", staleReason.name());
        summary.put("snapshotId", step.getSnapshot().getSnapshotId());
        summary.put("snapshotTimeSeconds", step.getSnapshot().getTimeSeconds());
        summary.put("classificationSimulationTimeSeconds", classificationSeconds);
        summary.put("snapshotAgeSimulationSeconds", age);
        summary.put("wallClockRuntimeSeconds", wallClockRuntimeSeconds);
        summary.put("gaWallClockBudgetSeconds", gaWallClockBudgetSeconds);
        summary.put("temporalMaximumSeconds", temporalMaximumSeconds);
        summary.put("freshnessCapSeconds", freshnessCapSeconds);
        summary.put("fitness", step.getMaGaResult().getFinalBestFitness());
        summary.put("stopReason", step.getMaGaResult().getStopReason().name());
        summary.put("distribution", distribution);
        summary.put("activeStrategyPresent", activePresent);
        summary.put("activeSnapshotId", activePresent ? active.getSnapshotId() : "");
        if (activePresent) {
            summary.put("activeSnapshotAgeSimulationSeconds", activeAge);
        }
        summary.put("comparison", Map.of(
                "same", same,
                "changed", changed,
                "staleOnly", staleOnly,
                "activeOnly", activeOnly
        ));
        summary.put("transitionMatrix", transitions);
        summaries.write(gson.toJson(summary));
        summaries.newLine();
        decisions.flush();
        distributions.flush();
        comparisons.flush();
        transitionMatrix.flush();
        summaries.flush();
    }

    private static Map<String, Object> assignmentBucket(int count, int total) {
        Map<String, Object> bucket = new LinkedHashMap<>();
        bucket.put("count", count);
        bucket.put("percentage", percentage(count, total));
        return bucket;
    }

    private static double percentage(int count, int total) {
        return total <= 0 ? 0.0 : (100.0 * count) / total;
    }

    private static BufferedWriter open(Path path) throws IOException {
        return Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private static String csv(Object... values) {
        List<String> out = new ArrayList<>();
        for (Object value : values) {
            String s = value == null ? "" : String.valueOf(value);
            out.add("\"" + s.replace("\"", "\"\"") + "\"");
        }
        return String.join(",", out);
    }

    @Override
    public synchronized void close() throws IOException {
        IOException failure = null;
        for (BufferedWriter writer : List.of(
                decisions,
                distributions,
                summaries,
                comparisons,
                transitionMatrix
        )) {
            try {
                writer.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
