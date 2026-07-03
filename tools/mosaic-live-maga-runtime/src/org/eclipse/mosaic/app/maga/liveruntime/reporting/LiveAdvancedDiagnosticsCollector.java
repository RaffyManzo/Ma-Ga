package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import model.node.NodeCandidate;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.SystemSnapshot;
import org.eclipse.mosaic.app.maga.liveruntime.LiveAppliedStrategy;
import org.eclipse.mosaic.app.maga.liveruntime.LiveAssignmentDecision;
import org.eclipse.mosaic.app.maga.liveruntime.LiveStaleReason;
import window.state.TemporalStepResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Transition-only collector for the G04 advanced mobility diagnostics.
 *
 * <p>The collector never invokes fitness, repair, selection, TWM or snapshot
 * construction. It observes immutable values after they have already been
 * produced by the live pipeline. No value is returned to the decision path.</p>
 */
public final class LiveAdvancedDiagnosticsCollector implements AutoCloseable {
    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000.0;

    private final String runId;
    private final Long seed;
    private final LiveAdvancedDiagnosticsWriter writer;

    private Map<String, VehicleObservation> previousVehicles = Collections.emptyMap();
    private Map<String, LiveAssignmentDecision> previousAppliedAssignments = Collections.emptyMap();
    private Map<String, String> previousAppliedGateways = Collections.emptyMap();
    private boolean snapshotBaselineEstablished;
    private boolean appliedBaselineEstablished;
    private boolean closed;
    private boolean failed;
    private String failureType;
    private String failureMessage;

    private long snapshotObservationCalls;
    private long appliedStrategyCalls;
    private long staleClassificationCalls;
    private long mobilityStateEvents;
    private long appliedAssignmentEvents;
    private long staleClassificationEvents;
    private long totalProcessingNs;
    private long maximumProcessingNs;

    public LiveAdvancedDiagnosticsCollector(
            Path reportingDir,
            String runId,
            Long seed,
            int flushBatchSize
    ) throws IOException {
        this.runId = normalizeRunId(runId);
        this.seed = seed;
        this.writer = new LiveAdvancedDiagnosticsWriter(reportingDir, flushBatchSize);
    }

    public void observeSnapshot(SystemSnapshot snapshot, long observationSimulationTimeNs) {
        long startedNs = System.nanoTime();
        try {
            if (!isOperational() || snapshot == null) {
                return;
            }
            snapshotObservationCalls++;
            Map<String, VehicleObservation> current = buildVehicleObservations(snapshot);

            for (Map.Entry<String, VehicleObservation> entry : current.entrySet()) {
                String vehicleId = entry.getKey();
                VehicleObservation now = entry.getValue();
                VehicleObservation before = previousVehicles.get(vehicleId);

                if (before == null) {
                    writeMobilityEvent(
                            snapshotBaselineEstablished
                                    ? "VEHICLE_APPEARED"
                                    : "MOBILITY_STATE_INITIALIZED",
                            List.of(snapshotBaselineEstablished
                                    ? "VEHICLE_APPEARED"
                                    : "INITIAL_BASELINE"),
                            vehicleId,
                            null,
                            now,
                            snapshot,
                            observationSimulationTimeNs,
                            null
                    );
                    continue;
                }

                List<String> reasons = transitionReasons(before, now);
                if (!reasons.isEmpty()) {
                    writeMobilityEvent(
                            "MOBILITY_STATE_TRANSITION",
                            reasons,
                            vehicleId,
                            before,
                            now,
                            snapshot,
                            observationSimulationTimeNs,
                            now.note
                    );
                }
            }

            if (snapshotBaselineEstablished) {
                for (Map.Entry<String, VehicleObservation> entry : previousVehicles.entrySet()) {
                    if (current.containsKey(entry.getKey())) {
                        continue;
                    }
                    writeMobilityEvent(
                            "VEHICLE_DISAPPEARED",
                            List.of("VEHICLE_DISAPPEARED"),
                            entry.getKey(),
                            entry.getValue(),
                            null,
                            snapshot,
                            observationSimulationTimeNs,
                            "vehicleMissingFromCurrentSnapshot"
                    );
                }
            }

            previousVehicles = current;
            snapshotBaselineEstablished = true;
        } catch (Exception error) {
            failSoft(error);
        } finally {
            recordProcessingTime(startedNs);
        }
    }

    public void recordAppliedStrategy(
            String jobId,
            TemporalStepResult step,
            LiveAppliedStrategy applied,
            long classificationSimulationTimeNs
    ) {
        long startedNs = System.nanoTime();
        try {
            if (!isOperational() || applied == null || step == null) {
                return;
            }
            appliedStrategyCalls++;
            Map<String, LiveAssignmentDecision> current = applied.getAssignments();
            Map<String, String> currentGateways = gatewaysByTask(step);
            Set<String> taskIds = new TreeSet<>();
            taskIds.addAll(previousAppliedAssignments.keySet());
            taskIds.addAll(current.keySet());

            for (String taskId : taskIds) {
                LiveAssignmentDecision before = previousAppliedAssignments.get(taskId);
                LiveAssignmentDecision now = current.get(taskId);
                String eventType;

                if (before == null && now != null) {
                    eventType = appliedBaselineEstablished
                            ? "APPLIED_ASSIGNMENT_ADDED"
                            : "APPLIED_ASSIGNMENT_INITIAL";
                } else if (before != null && now == null) {
                    eventType = "APPLIED_ASSIGNMENT_REMOVED";
                } else if (before != null && now != null && !before.samePlacement(now)) {
                    eventType = "APPLIED_ASSIGNMENT_CHANGED";
                } else {
                    continue;
                }

                LiveAdvancedDiagnosticsEvent event = baseEvent(eventType)
                        .put("jobId", jobId)
                        .put("resultStatus", "APPLIED")
                        .put("classificationSimulationTimeNs", classificationSimulationTimeNs)
                        .put("classificationSimulationTimeSeconds",
                                classificationSimulationTimeNs / NANOSECONDS_PER_SECOND)
                        .put("snapshotId", applied.getSnapshotId())
                        .put("snapshotTimeSeconds", applied.getSnapshotTimeSeconds())
                        .put("snapshotAgeSeconds", Math.max(
                                0.0,
                                classificationSimulationTimeNs / NANOSECONDS_PER_SECOND
                                        - applied.getSnapshotTimeSeconds()
                        ))
                        .put("taskId", taskId)
                        .put("vehicleId", now != null
                                ? now.getSourceVehicleId()
                                : before == null ? null : before.getSourceVehicleId())
                        .put("previousSelectedCandidateId", candidateId(before))
                        .put("currentSelectedCandidateId", candidateId(now))
                        .put("previousExecutionNodeId", executionNodeId(before))
                        .put("currentExecutionNodeId", executionNodeId(now))
                        .put("previousTier", tier(before))
                        .put("currentTier", tier(now))
                        .put("previousGatewayId", previousAppliedGateways.get(taskId))
                        .put("currentGatewayId", currentGateways.get(taskId))
                        .put("previousCoverageTimeSeconds", coverageTime(before))
                        .put("currentCoverageTimeSeconds", coverageTime(now))
                        .put("previousCoverageRisk", coverageRisk(before))
                        .put("currentCoverageRisk", coverageRisk(now))
                        .put("previousLinkInstability", linkInstability(before))
                        .put("currentLinkInstability", linkInstability(now))
                        .put("previousHandoverRisk", handoverRisk(before))
                        .put("currentHandoverRisk", handoverRisk(now))
                        .put("previousMobilityPenalty", mobilityPenalty(before))
                        .put("currentMobilityPenalty", mobilityPenalty(now))
                        .put("detectionDelaySeconds", null)
                        .put("detectionDelayUpperBoundSeconds", null)
                        .put("routeFamily", null)
                        .put("notes", null);
                writer.write(event);
                appliedAssignmentEvents++;
            }

            previousAppliedAssignments = new LinkedHashMap<>(current);
            previousAppliedGateways = new LinkedHashMap<>(currentGateways);
            appliedBaselineEstablished = true;
        } catch (Exception error) {
            failSoft(error);
        } finally {
            recordProcessingTime(startedNs);
        }
    }

    public void recordStaleClassification(
            String jobId,
            TemporalStepResult step,
            LiveStaleReason staleReason,
            long classificationSimulationTimeNs,
            double wallClockRuntimeSeconds,
            double gaWallClockBudgetSeconds,
            double temporalMaximumSeconds,
            double freshnessCapSeconds,
            LiveAppliedStrategy activeStrategy
    ) {
        long startedNs = System.nanoTime();
        try {
            if (!isOperational() || step == null) {
                return;
            }
            staleClassificationCalls++;
            double classificationSeconds =
                    classificationSimulationTimeNs / NANOSECONDS_PER_SECOND;
            double snapshotAge = Math.max(
                    0.0,
                    classificationSeconds - step.getSnapshot().getTimeSeconds()
            );
            Double activeAge = activeStrategy == null
                    ? null
                    : Math.max(
                            0.0,
                            classificationSeconds - activeStrategy.getSnapshotTimeSeconds()
                    );

            LiveAdvancedDiagnosticsEvent event = baseEvent("STALE_RESULT_CLASSIFIED")
                    .put("jobId", jobId)
                    .put("resultStatus", "STALE_DISCARDED")
                    .put("staleReason", staleReason == null ? null : staleReason.name())
                    .put("classificationSimulationTimeNs", classificationSimulationTimeNs)
                    .put("classificationSimulationTimeSeconds", classificationSeconds)
                    .put("snapshotId", step.getSnapshot().getSnapshotId())
                    .put("snapshotTimeSeconds", step.getSnapshot().getTimeSeconds())
                    .put("snapshotAgeSeconds", snapshotAge)
                    .put("wallClockRuntimeSeconds", wallClockRuntimeSeconds)
                    .put("gaWallClockBudgetSeconds", gaWallClockBudgetSeconds)
                    .put("temporalMaximumSeconds", temporalMaximumSeconds)
                    .put("freshnessCapSeconds", freshnessCapSeconds)
                    .put("activeStrategyPresent", activeStrategy != null)
                    .put("activeSnapshotId", activeStrategy == null
                            ? null : activeStrategy.getSnapshotId())
                    .put("activeSnapshotAgeSeconds", activeAge)
                    .put("assignmentCount", step.getMaGaResult()
                            .getBestEvaluation().getGeneBreakdowns().size())
                    .put("detectionDelaySeconds", null)
                    .put("detectionDelayUpperBoundSeconds", null)
                    .put("routeFamily", null)
                    .put("notes", "Full stale assignments remain in live_stale_assignment_decisions.csv");
            writer.write(event);
            staleClassificationEvents++;
        } catch (Exception error) {
            failSoft(error);
        } finally {
            recordProcessingTime(startedNs);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runId", runId);
        summary.put("seed", seed);
        summary.put("mode", "OBSERVABILITY_ONLY_TRANSITION_EVENTS");
        summary.put("snapshotObservationCalls", snapshotObservationCalls);
        summary.put("appliedStrategyCalls", appliedStrategyCalls);
        summary.put("staleClassificationCalls", staleClassificationCalls);
        summary.put("mobilityStateEvents", mobilityStateEvents);
        summary.put("appliedAssignmentEvents", appliedAssignmentEvents);
        summary.put("staleClassificationEvents", staleClassificationEvents);
        summary.put("eventCount", writer.getEventCount());
        summary.put("flushCountBeforeSummary", writer.getFlushCount());
        summary.put("flushBatchSize", writer.getFlushBatchSize());
        summary.put("totalDiagnosticProcessingNs", totalProcessingNs);
        summary.put("maximumDiagnosticCallProcessingNs", maximumProcessingNs);
        summary.put("totalDiagnosticProcessingSeconds",
                totalProcessingNs / NANOSECONDS_PER_SECOND);
        summary.put("decisionPathModified", false);
        summary.put("fitnessReevaluated", false);
        summary.put("snapshotRebuilt", false);
        summary.put("routeFamilyInferredPerVehicle", false);
        summary.put("failed", failed);
        summary.put("failureType", failureType);
        summary.put("failureMessage", failureMessage);
        try {
            writer.writeSummary(summary);
        } catch (Exception error) {
            failSoft(error);
        } finally {
            closed = true;
            try {
                writer.close();
            } catch (Exception error) {
                if (!failed) {
                    failed = true;
                    failureType = error.getClass().getName();
                    failureMessage = String.valueOf(error.getMessage());
                }
            }
        }
    }

    private Map<String, VehicleObservation> buildVehicleObservations(SystemSnapshot snapshot) {
        Map<String, MutableVehicleObservation> mutable = new HashMap<>();

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            MutableVehicleObservation state = mutable.computeIfAbsent(
                    candidate.getSourceVehicleId(),
                    ignored -> new MutableVehicleObservation()
            );
            state.candidateIds.add(candidate.getCandidateId());
            state.candidateTiers.put(candidate.getCandidateId(), candidate.getType().name());
        }

        for (AccessLinkSnapshot link : snapshot.getAccessLinks()) {
            MutableVehicleObservation state = mutable.computeIfAbsent(
                    link.getVehicleId(),
                    ignored -> new MutableVehicleObservation()
            );
            if (link.isAvailable()) {
                state.availableGatewayIds.add(link.getGatewayId());
            }
            if (link.isActive()) {
                state.activeGatewayIds.add(link.getGatewayId());
            }
        }

        Map<String, VehicleObservation> result = new TreeMap<>();
        for (Map.Entry<String, MutableVehicleObservation> entry : mutable.entrySet()) {
            MutableVehicleObservation source = entry.getValue();
            boolean coverageAvailable = !source.activeGatewayIds.isEmpty();
            String gatewayId = source.activeGatewayIds.size() == 1
                    ? source.activeGatewayIds.iterator().next()
                    : null;
            String note = source.activeGatewayIds.size() > 1
                    ? "multipleActiveGateways=" + sorted(source.activeGatewayIds)
                    : !coverageAvailable && !source.availableGatewayIds.isEmpty()
                            ? "declaredAvailableWithoutActiveGateway="
                                    + sorted(source.availableGatewayIds)
                            : null;
            result.put(
                    entry.getKey(),
                    new VehicleObservation(
                            snapshot.getSnapshotId(),
                            snapshot.getTimeSeconds(),
                            gatewayId,
                            coverageAvailable,
                            new HashSet<>(source.candidateIds),
                            new TreeMap<>(source.candidateTiers),
                            note
                    )
            );
        }
        return result;
    }

    private void writeMobilityEvent(
            String eventType,
            List<String> reasons,
            String vehicleId,
            VehicleObservation before,
            VehicleObservation now,
            SystemSnapshot snapshot,
            long observationSimulationTimeNs,
            String note
    ) throws IOException {
        Set<String> previousCandidates = before == null
                ? Collections.emptySet() : before.candidateIds;
        Set<String> currentCandidates = now == null
                ? Collections.emptySet() : now.candidateIds;
        Set<String> added = difference(currentCandidates, previousCandidates);
        Set<String> removed = difference(previousCandidates, currentCandidates);
        Double previousTime = before == null ? null : before.observationTimeSeconds;
        Double currentTime = now == null
                ? snapshot.getTimeSeconds() : now.observationTimeSeconds;
        Double delayUpperBound = previousTime == null || currentTime == null
                ? null : Math.max(0.0, currentTime - previousTime);

        LiveAdvancedDiagnosticsEvent event = baseEvent(eventType)
                .put("transitionReasons", reasons)
                .put("vehicleId", vehicleId)
                .put("taskId", null)
                .put("observationSimulationTimeNs", observationSimulationTimeNs)
                .put("observationSimulationTimeSeconds",
                        observationSimulationTimeNs / NANOSECONDS_PER_SECOND)
                .put("snapshotId", snapshot.getSnapshotId())
                .put("previousObservationTimeSeconds", previousTime)
                .put("currentObservationTimeSeconds", currentTime)
                .put("previousGatewayId", before == null ? null : before.gatewayId)
                .put("currentGatewayId", now == null ? null : now.gatewayId)
                .put("previousCoverageAvailable",
                        before == null ? null : before.coverageAvailable)
                .put("currentCoverageAvailable",
                        now == null ? null : now.coverageAvailable)
                .put("previousCandidateIds", sorted(previousCandidates))
                .put("currentCandidateIds", sorted(currentCandidates))
                .put("addedCandidateIds", sorted(added))
                .put("removedCandidateIds", sorted(removed))
                .put("previousCandidateTiers",
                        before == null ? null : before.candidateTiers)
                .put("currentCandidateTiers",
                        now == null ? null : now.candidateTiers)
                .put("detectionDelaySeconds", null)
                .put("detectionDelayUpperBoundSeconds", delayUpperBound)
                .put("coverageTimeSeconds", null)
                .put("coverageRisk", null)
                .put("linkInstability", null)
                .put("handoverRisk", null)
                .put("mobilityPenalty", null)
                .put("routeFamily", null)
                .put("notes", note);
        writer.write(event);
        mobilityStateEvents++;
    }

    private LiveAdvancedDiagnosticsEvent baseEvent(String eventType) {
        return new LiveAdvancedDiagnosticsEvent(eventType, runId, seed);
    }

    private static List<String> transitionReasons(
            VehicleObservation before,
            VehicleObservation now
    ) {
        List<String> reasons = new ArrayList<>();
        if (!before.coverageAvailable && now.coverageAvailable) {
            reasons.add("COVERAGE_ENTERED");
        } else if (before.coverageAvailable && !now.coverageAvailable) {
            reasons.add("COVERAGE_EXITED");
        }
        if (!Objects.equals(before.gatewayId, now.gatewayId)
                && before.gatewayId != null && now.gatewayId != null) {
            reasons.add("ACTIVE_GATEWAY_CHANGED");
        }
        if (!before.candidateIds.equals(now.candidateIds)) {
            reasons.add("CANDIDATE_SET_CHANGED");
        }
        if (!Objects.equals(before.note, now.note)) {
            reasons.add("ACCESS_LINK_STATE_ANOMALY_CHANGED");
        }
        return reasons;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static List<String> sorted(Set<String> values) {
        List<String> result = new ArrayList<>(values);
        Collections.sort(result);
        return result;
    }


    private static Map<String, String> gatewaysByTask(TemporalStepResult step) {
        Map<String, String> result = new HashMap<>();
        step.getMaGaResult().getBestEvaluation().getGeneBreakdowns().forEach(gene ->
                result.put(
                        gene.getTaskId(),
                        gene.getMobilityBreakdown()
                                .getLinkMetrics()
                                .getReferenceAccessGatewayId()
                )
        );
        return result;
    }

    private static String candidateId(LiveAssignmentDecision value) {
        return value == null ? null : value.getSelectedCandidateId();
    }

    private static String executionNodeId(LiveAssignmentDecision value) {
        return value == null ? null : value.getExecutionNodeId();
    }

    private static String tier(LiveAssignmentDecision value) {
        return value == null || value.getNodeType() == null
                ? null : value.getNodeType().name();
    }

    private static Double coverageTime(LiveAssignmentDecision value) {
        return value == null ? null : value.getCoverageTimeSeconds();
    }

    private static Double coverageRisk(LiveAssignmentDecision value) {
        return value == null ? null : value.getCoverageRisk();
    }

    private static Double linkInstability(LiveAssignmentDecision value) {
        return value == null ? null : value.getLinkInstability();
    }

    private static Double handoverRisk(LiveAssignmentDecision value) {
        return value == null ? null : value.getHandoverRisk();
    }

    private static Double mobilityPenalty(LiveAssignmentDecision value) {
        return value == null ? null : value.getMobilityPenalty();
    }

    private void recordProcessingTime(long startedNs) {
        long elapsed = Math.max(0L, System.nanoTime() - startedNs);
        totalProcessingNs += elapsed;
        maximumProcessingNs = Math.max(maximumProcessingNs, elapsed);
    }

    private boolean isOperational() {
        return !closed && !failed;
    }

    private void failSoft(Exception error) {
        if (!failed) {
            failed = true;
            failureType = error == null ? null : error.getClass().getName();
            failureMessage = error == null ? null : String.valueOf(error.getMessage());
            System.err.println(
                    "G04_ADVANCED_DIAGNOSTICS_DISABLED_FAIL_SOFT"
                            + "|type=" + failureType
                            + "|message=" + failureMessage
            );
        }
    }

    private static String normalizeRunId(String value) {
        return value == null || value.isBlank() ? "UNSPECIFIED_RUN" : value.trim();
    }

    private static final class MutableVehicleObservation {
        private final Set<String> availableGatewayIds = new HashSet<>();
        private final Set<String> activeGatewayIds = new HashSet<>();
        private final Set<String> candidateIds = new HashSet<>();
        private final Map<String, String> candidateTiers = new HashMap<>();
    }

    private static final class VehicleObservation {
        private final String snapshotId;
        private final double observationTimeSeconds;
        private final String gatewayId;
        private final boolean coverageAvailable;
        private final Set<String> candidateIds;
        private final Map<String, String> candidateTiers;
        private final String note;

        private VehicleObservation(
                String snapshotId,
                double observationTimeSeconds,
                String gatewayId,
                boolean coverageAvailable,
                Set<String> candidateIds,
                Map<String, String> candidateTiers,
                String note
        ) {
            this.snapshotId = snapshotId;
            this.observationTimeSeconds = observationTimeSeconds;
            this.gatewayId = gatewayId;
            this.coverageAvailable = coverageAvailable;
            this.candidateIds = Collections.unmodifiableSet(candidateIds);
            this.candidateTiers = Collections.unmodifiableMap(candidateTiers);
            this.note = note;
        }
    }
}
