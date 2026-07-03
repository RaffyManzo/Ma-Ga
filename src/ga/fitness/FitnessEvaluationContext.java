package ga.fitness;

import model.bandwidth.BandwidthPoolResolver;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contesto immutabile di valutazione costruito una sola volta per snapshot.
 *
 * <p>Contiene le viste e gli indici che dipendono esclusivamente dallo
 * snapshot. Le strutture dipendenti dal cromosoma restano invece locali a ogni
 * valutazione. Il contesto non memorizza risultati di fitness e non cambia la
 * formula valutativa.</p>
 */
public final class FitnessEvaluationContext {
    private final SystemSnapshot snapshot;
    private final List<TaskInstance> tasks;
    private final List<VehicleSnapshot> vehicles;
    private final List<NodeCandidate> candidates;
    private final Map<String, TaskInstance> taskById;
    private final Map<String, VehicleSnapshot> vehicleById;
    private final Map<String, NodeCandidate> candidateById;
    private final Map<String, Double> executionNodeAvailableCpuById;
    private final Map<String, Double> candidateAvailableBandwidthById;
    private final Map<String, Double> poolAvailableBandwidthById;
    private final Map<String, BandwidthPoolSnapshot> poolByCandidateId;

    private FitnessEvaluationContext(SystemSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(
                snapshot,
                "snapshot must not be null."
        );

        this.tasks = immutableCopy(
                requireList(snapshot.getTasks(), "snapshot.tasks")
        );
        this.vehicles = immutableCopy(
                requireList(snapshot.getVehicles(), "snapshot.vehicles")
        );
        this.candidates = immutableCopy(
                requireList(
                        snapshot.getCandidateNodes(),
                        "snapshot.candidateNodes"
                )
        );

        this.taskById = Collections.unmodifiableMap(indexTasks(tasks));
        this.vehicleById = Collections.unmodifiableMap(
                indexVehicles(vehicles)
        );
        this.candidateById = Collections.unmodifiableMap(
                indexCandidates(candidates)
        );
        this.executionNodeAvailableCpuById = Collections.unmodifiableMap(
                indexExecutionNodeCpu(candidates)
        );
        this.candidateAvailableBandwidthById = Collections.unmodifiableMap(
                indexCandidateBandwidth(candidates)
        );
        this.poolAvailableBandwidthById = Collections.unmodifiableMap(
                indexPoolBandwidth(
                        requireList(
                                snapshot.getBandwidthPools(),
                                "snapshot.bandwidthPools"
                        )
                )
        );
        this.poolByCandidateId = Collections.unmodifiableMap(
                indexCandidatePools(snapshot, candidates)
        );
    }

    /** Costruisce un contesto scoped allo snapshot indicato. */
    public static FitnessEvaluationContext from(SystemSnapshot snapshot) {
        return new FitnessEvaluationContext(snapshot);
    }

    /** Snapshot usato per i calcoli mobility-aware e per i pool di banda. */
    public SystemSnapshot getSnapshot() {
        return snapshot;
    }

    List<TaskInstance> getTasks() {
        return tasks;
    }

    List<VehicleSnapshot> getVehicles() {
        return vehicles;
    }

    List<NodeCandidate> getCandidates() {
        return candidates;
    }

    Map<String, TaskInstance> getTaskById() {
        return taskById;
    }

    Map<String, VehicleSnapshot> getVehicleById() {
        return vehicleById;
    }

    Map<String, NodeCandidate> getCandidateById() {
        return candidateById;
    }

    Map<String, Double> getExecutionNodeAvailableCpuById() {
        return executionNodeAvailableCpuById;
    }

    Map<String, Double> getCandidateAvailableBandwidthById() {
        return candidateAvailableBandwidthById;
    }

    Map<String, Double> getPoolAvailableBandwidthById() {
        return poolAvailableBandwidthById;
    }

    BandwidthPoolSnapshot getPoolByCandidateId(String candidateId) {
        return poolByCandidateId.get(candidateId);
    }

    private static Map<String, TaskInstance> indexTasks(
            List<TaskInstance> tasks
    ) {
        Map<String, TaskInstance> result = new HashMap<>();
        for (TaskInstance task : tasks) {
            result.put(task.getTaskId(), task);
        }
        return result;
    }

    private static Map<String, VehicleSnapshot> indexVehicles(
            List<VehicleSnapshot> vehicles
    ) {
        Map<String, VehicleSnapshot> result = new HashMap<>();
        for (VehicleSnapshot vehicle : vehicles) {
            result.put(vehicle.getVehicleId(), vehicle);
        }
        return result;
    }

    private static Map<String, NodeCandidate> indexCandidates(
            List<NodeCandidate> candidates
    ) {
        Map<String, NodeCandidate> result = new HashMap<>();
        for (NodeCandidate candidate : candidates) {
            result.put(candidate.getCandidateId(), candidate);
        }
        return result;
    }

    private static Map<String, Double> indexExecutionNodeCpu(
            List<NodeCandidate> candidates
    ) {
        Map<String, Double> result = new HashMap<>();
        for (NodeCandidate candidate : candidates) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }
            result.putIfAbsent(
                    candidate.getExecutionNodeId(),
                    candidate.getAvailableCpu()
            );
        }
        return result;
    }

    private static Map<String, Double> indexCandidateBandwidth(
            List<NodeCandidate> candidates
    ) {
        Map<String, Double> result = new HashMap<>();
        for (NodeCandidate candidate : candidates) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }
            result.put(
                    candidate.getCandidateId(),
                    candidate.getAvailableBandwidth()
            );
        }
        return result;
    }

    private static Map<String, Double> indexPoolBandwidth(
            List<BandwidthPoolSnapshot> pools
    ) {
        Map<String, Double> result = new HashMap<>();
        for (BandwidthPoolSnapshot pool : pools) {
            result.put(pool.getPoolId(), pool.getAvailableBandwidth());
        }
        return result;
    }

    private static Map<String, BandwidthPoolSnapshot> indexCandidatePools(
            SystemSnapshot snapshot,
            List<NodeCandidate> candidates
    ) {
        BandwidthPoolResolver resolver = new BandwidthPoolResolver();
        Map<String, BandwidthPoolSnapshot> result = new HashMap<>();

        for (NodeCandidate candidate : candidates) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }

            try {
                result.put(
                        candidate.getCandidateId(),
                        resolver.resolve(snapshot, candidate)
                );
            } catch (IllegalArgumentException ignored) {
                // La fitness applica la penalita' di soluzione invalida.
            }
        }
        return result;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static <T> List<T> requireList(List<T> list, String name) {
        if (list == null) {
            throw new IllegalArgumentException(name + " must not be null.");
        }
        return list;
    }
}
