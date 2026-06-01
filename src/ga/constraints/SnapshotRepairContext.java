package ga.constraints;

import model.mobility.CoverageEstimator;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Indici e cache riutilizzabili durante il repair di uno stesso snapshot.
 *
 * <p>Durante una singola esecuzione del GA lo snapshot resta invariato. Questa
 * classe evita quindi di scandire ripetutamente le liste di task, veicoli e
 * candidati per ogni gene, figlio e generazione.</p>
 *
 * <p>Il contesto non introduce nuove decisioni e non modifica la
 * formalizzazione: indicizza soltanto dati già osservati nello snapshot.</p>
 */
public final class SnapshotRepairContext {
    private final SystemSnapshot snapshot;
    private final List<TaskInstance> tasks;
    private final Map<String, TaskInstance> taskById;
    private final Map<String, VehicleSnapshot> vehicleById;
    private final Map<String, NodeCandidate> candidateById;
    private final Map<String, List<NodeCandidate>> candidatesBySourceVehicleId;
    private final Map<String, NodeCandidate> localCandidateBySourceVehicleId;
    private final Map<String, Double> availableCpuByExecutionNodeId;
    private final Map<CoverageKey, Double> coverageTimeByTaskAndCandidate;

    public SnapshotRepairContext(SystemSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null.");
        this.tasks = immutableCopy(snapshot.getTasks());
        this.taskById = indexTasks(tasks);
        this.vehicleById = indexVehicles(snapshot.getVehicles());
        this.candidateById = indexCandidates(snapshot.getCandidateNodes());
        this.candidatesBySourceVehicleId = indexCandidatesBySourceVehicle(
                snapshot.getCandidateNodes()
        );
        this.localCandidateBySourceVehicleId = indexLocalCandidates(
                snapshot.getCandidateNodes()
        );
        this.availableCpuByExecutionNodeId = indexAvailableCpuByExecutionNode(
                snapshot.getCandidateNodes()
        );
        this.coverageTimeByTaskAndCandidate = new HashMap<>();
    }

    public boolean isFor(SystemSnapshot candidateSnapshot) {
        return snapshot == candidateSnapshot;
    }

    public SystemSnapshot getSnapshot() {
        return snapshot;
    }

    public List<TaskInstance> getTasks() {
        return tasks;
    }

    public TaskInstance getTaskById(String taskId) {
        return taskId == null ? null : taskById.get(taskId);
    }

    public VehicleSnapshot getVehicleById(String vehicleId) {
        return vehicleId == null ? null : vehicleById.get(vehicleId);
    }

    public NodeCandidate getCandidateById(String candidateId) {
        return candidateId == null ? null : candidateById.get(candidateId);
    }

    public List<NodeCandidate> getCandidatesForSourceVehicle(String sourceVehicleId) {
        List<NodeCandidate> candidates = candidatesBySourceVehicleId.get(sourceVehicleId);
        return candidates == null ? List.of() : candidates;
    }

    public List<NodeCandidate> getCandidatesForTask(TaskInstance task) {
        Objects.requireNonNull(task, "task must not be null.");
        return getCandidatesForSourceVehicle(task.getSourceVehicleId());
    }

    public NodeCandidate getLocalCandidateForTask(TaskInstance task) {
        Objects.requireNonNull(task, "task must not be null.");
        return localCandidateBySourceVehicleId.get(task.getSourceVehicleId());
    }

    public Map<String, Double> getAvailableCpuByExecutionNodeId() {
        return availableCpuByExecutionNodeId;
    }

    public NodeCandidate requireLocalCandidateForTask(TaskInstance task) {
        NodeCandidate candidate = getLocalCandidateForTask(task);
        if (candidate == null) {
            throw new IllegalArgumentException(
                    "No LOCAL candidate found for task "
                            + task.getTaskId()
                            + " and source vehicle "
                            + task.getSourceVehicleId()
            );
        }
        return candidate;
    }

    /**
     * Restituisce il tempo di copertura, calcolandolo una sola volta per coppia
     * task-candidato nello snapshot corrente.
     */
    public double estimateCoverageTimeSeconds(
            TaskInstance task,
            NodeCandidate candidate,
            CoverageEstimator coverageEstimator
    ) {
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");
        Objects.requireNonNull(coverageEstimator, "coverageEstimator must not be null.");
        CoverageKey key = new CoverageKey(task.getTaskId(), candidate.getCandidateId());
        return coverageTimeByTaskAndCandidate.computeIfAbsent(
                key,
                ignored -> computeCoverageTimeSeconds(task, candidate, coverageEstimator)
        );
    }

    private double computeCoverageTimeSeconds(
            TaskInstance task,
            NodeCandidate candidate,
            CoverageEstimator coverageEstimator
    ) {
        try {
            return coverageEstimator.estimateCoverageTimeSeconds(
                    snapshot,
                    task,
                    candidate
            );
        } catch (IllegalArgumentException ex) {
            return 0.0;
        }
    }

    private Map<String, TaskInstance> indexTasks(List<TaskInstance> source) {
        Map<String, TaskInstance> result = new LinkedHashMap<>();
        for (TaskInstance task : source) {
            if (task != null && task.getTaskId() != null) {
                result.put(task.getTaskId(), task);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, VehicleSnapshot> indexVehicles(List<VehicleSnapshot> source) {
        Map<String, VehicleSnapshot> result = new LinkedHashMap<>();
        if (source != null) {
            for (VehicleSnapshot vehicle : source) {
                if (vehicle != null && vehicle.getVehicleId() != null) {
                    result.put(vehicle.getVehicleId(), vehicle);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, NodeCandidate> indexCandidates(List<NodeCandidate> source) {
        Map<String, NodeCandidate> result = new LinkedHashMap<>();
        if (source != null) {
            for (NodeCandidate candidate : source) {
                if (candidate != null && candidate.getCandidateId() != null) {
                    result.put(candidate.getCandidateId(), candidate);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, List<NodeCandidate>> indexCandidatesBySourceVehicle(
            List<NodeCandidate> source
    ) {
        Map<String, List<NodeCandidate>> mutable = new LinkedHashMap<>();
        if (source != null) {
            for (NodeCandidate candidate : source) {
                if (candidate == null || candidate.getSourceVehicleId() == null) {
                    continue;
                }
                mutable.computeIfAbsent(
                        candidate.getSourceVehicleId(),
                        ignored -> new ArrayList<>()
                ).add(candidate);
            }
        }
        Map<String, List<NodeCandidate>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<NodeCandidate>> entry : mutable.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, Double> indexAvailableCpuByExecutionNode(
            List<NodeCandidate> source
    ) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (source != null) {
            for (NodeCandidate candidate : source) {
                if (candidate == null || candidate.getType() == NodeType.LOCAL) {
                    continue;
                }
                result.putIfAbsent(
                        candidate.getExecutionNodeId(),
                        candidate.getAvailableCpu()
                );
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, NodeCandidate> indexLocalCandidates(List<NodeCandidate> source) {
        Map<String, NodeCandidate> result = new LinkedHashMap<>();
        if (source != null) {
            for (NodeCandidate candidate : source) {
                if (candidate == null || candidate.getType() != NodeType.LOCAL) {
                    continue;
                }
                result.putIfAbsent(candidate.getSourceVehicleId(), candidate);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private <T> List<T> immutableCopy(List<T> source) {
        if (source == null) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static final class CoverageKey {
        private final String taskId;
        private final String candidateId;

        private CoverageKey(String taskId, String candidateId) {
            this.taskId = taskId;
            this.candidateId = candidateId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CoverageKey)) {
                return false;
            }
            CoverageKey key = (CoverageKey) other;
            return Objects.equals(taskId, key.taskId)
                    && Objects.equals(candidateId, key.candidateId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskId, candidateId);
        }
    }
}
