package validation;

import model.NodeCandidate;
import model.NodeType;
import model.SystemSnapshot;
import model.TaskInstance;
import model.VehicleSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Valida uno SystemSnapshot prima che venga passato al MA-GA.
 *
 * Questa classe serve a intercettare errori strutturali nello snapshot:
 *
 * - veicoli duplicati;
 * - task senza veicolo sorgente valido;
 * - candidati duplicati;
 * - candidati senza sourceVehicleId valido;
 * - candidati LOCAL incoerenti;
 * - candidati VEHICLE incoerenti;
 * - task senza candidati validi;
 * - task senza candidato LOCAL.
 *
 * È particolarmente importante in vista di MOSAIC, perché gli snapshot
 * saranno generati automaticamente da dati simulativi.
 */
public final class SnapshotValidator {

    /**
     * Valida uno snapshot completo.
     *
     * Parametri in ingresso:
     * - snapshot: snapshot da validare.
     *
     * Output:
     * - nessun valore restituito.
     *
     * Eccezioni:
     * - IllegalArgumentException se lo snapshot non è valido.
     */
    public void validate(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        List<VehicleSnapshot> vehicles = requireList(snapshot.getVehicles(), "vehicles");
        List<TaskInstance> tasks = requireList(snapshot.getTasks(), "tasks");
        List<NodeCandidate> candidates = requireList(snapshot.getCandidateNodes(), "candidateNodes");

        validateVehicleIds(vehicles);
        validateTaskIds(tasks);
        validateCandidateIds(candidates);
        validateTasksReferenceExistingVehicles(tasks, vehicles);
        validateCandidatesReferenceExistingSourceVehicles(candidates, vehicles);
        validateCandidateSemanticRules(candidates);
        validateEachTaskHasValidCandidates(tasks, candidates);
        validateNumericTaskFields(tasks);
        validateNumericCandidateFields(candidates);
    }

    /**
     * Verifica che la lista non sia nulla.
     */
    private <T> List<T> requireList(List<T> list, String name) {
        if (list == null) {
            throw new IllegalArgumentException("snapshot." + name + " must not be null.");
        }

        return list;
    }

    /**
     * Verifica che i vehicleId siano univoci.
     */
    private void validateVehicleIds(List<VehicleSnapshot> vehicles) {
        Set<String> ids = new HashSet<>();

        for (VehicleSnapshot vehicle : vehicles) {
            if (vehicle.getVehicleId() == null || vehicle.getVehicleId().isBlank()) {
                throw new IllegalArgumentException("vehicleId must not be null or blank.");
            }

            if (!ids.add(vehicle.getVehicleId())) {
                throw new IllegalArgumentException(
                        "Duplicated vehicleId: " + vehicle.getVehicleId()
                );
            }
        }
    }

    /**
     * Verifica che i taskId siano univoci.
     */
    private void validateTaskIds(List<TaskInstance> tasks) {
        Set<String> ids = new HashSet<>();

        for (TaskInstance task : tasks) {
            if (task.getTaskId() == null || task.getTaskId().isBlank()) {
                throw new IllegalArgumentException("taskId must not be null or blank.");
            }

            if (!ids.add(task.getTaskId())) {
                throw new IllegalArgumentException(
                        "Duplicated taskId: " + task.getTaskId()
                );
            }
        }
    }

    /**
     * Verifica che i candidateId siano univoci.
     */
    private void validateCandidateIds(List<NodeCandidate> candidates) {
        Set<String> ids = new HashSet<>();

        for (NodeCandidate candidate : candidates) {
            if (candidate.getCandidateId() == null || candidate.getCandidateId().isBlank()) {
                throw new IllegalArgumentException("candidateId must not be null or blank.");
            }

            if (!ids.add(candidate.getCandidateId())) {
                throw new IllegalArgumentException(
                        "Duplicated candidateId: " + candidate.getCandidateId()
                );
            }
        }
    }

    /**
     * Verifica che ogni task punti a un veicolo sorgente esistente.
     */
    private void validateTasksReferenceExistingVehicles(
            List<TaskInstance> tasks,
            List<VehicleSnapshot> vehicles
    ) {
        Set<String> vehicleIds = collectVehicleIds(vehicles);

        for (TaskInstance task : tasks) {
            if (!vehicleIds.contains(task.getSourceVehicleId())) {
                throw new IllegalArgumentException(
                        "Task " + task.getTaskId()
                                + " references missing source vehicle: "
                                + task.getSourceVehicleId()
                );
            }
        }
    }

    /**
     * Verifica che ogni candidato abbia un sourceVehicleId esistente.
     */
    private void validateCandidatesReferenceExistingSourceVehicles(
            List<NodeCandidate> candidates,
            List<VehicleSnapshot> vehicles
    ) {
        Set<String> vehicleIds = collectVehicleIds(vehicles);

        for (NodeCandidate candidate : candidates) {
            if (!vehicleIds.contains(candidate.getSourceVehicleId())) {
                throw new IllegalArgumentException(
                        "Candidate " + candidate.getCandidateId()
                                + " references missing source vehicle: "
                                + candidate.getSourceVehicleId()
                );
            }
        }
    }

    /**
     * Verifica le regole semantiche dei candidati source-aware.
     */
    private void validateCandidateSemanticRules(List<NodeCandidate> candidates) {
        for (NodeCandidate candidate : candidates) {
            if (candidate.getType() == NodeType.LOCAL
                    && !candidate.getSourceVehicleId().equals(candidate.getExecutionNodeId())) {
                throw new IllegalArgumentException(
                        "LOCAL candidate " + candidate.getCandidateId()
                                + " must have sourceVehicleId == executionNodeId."
                );
            }

            if (candidate.getType() == NodeType.VEHICLE
                    && candidate.getSourceVehicleId().equals(candidate.getExecutionNodeId())) {
                throw new IllegalArgumentException(
                        "VEHICLE candidate " + candidate.getCandidateId()
                                + " must have sourceVehicleId != executionNodeId."
                );
            }
        }
    }

    /**
     * Verifica che ogni task abbia candidati validi e almeno un candidato LOCAL.
     */
    private void validateEachTaskHasValidCandidates(
            List<TaskInstance> tasks,
            List<NodeCandidate> candidates
    ) {
        for (TaskInstance task : tasks) {
            boolean hasAnyCandidate = false;
            boolean hasLocalCandidate = false;

            for (NodeCandidate candidate : candidates) {
                if (!candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
                    continue;
                }

                hasAnyCandidate = true;

                if (candidate.getType() == NodeType.LOCAL) {
                    hasLocalCandidate = true;
                }
            }

            if (!hasAnyCandidate) {
                throw new IllegalArgumentException(
                        "Task " + task.getTaskId()
                                + " has no valid execution candidate."
                );
            }

            if (!hasLocalCandidate) {
                throw new IllegalArgumentException(
                        "Task " + task.getTaskId()
                                + " has no LOCAL candidate for source vehicle "
                                + task.getSourceVehicleId()
                );
            }
        }
    }

    /**
     * Verifica i valori numerici dei task.
     */
    private void validateNumericTaskFields(List<TaskInstance> tasks) {
        for (TaskInstance task : tasks) {
            requireNonNegativeFinite(task.getInputSizeBits(), "inputSizeBits", task.getTaskId());
            requireNonNegativeFinite(task.getOutputSizeBits(), "outputSizeBits", task.getTaskId());
            requireNonNegativeFinite(task.getCpuCycles(), "cpuCycles", task.getTaskId());
            requireNonNegativeFinite(task.getDeadlineSeconds(), "deadlineSeconds", task.getTaskId());
        }
    }

    /**
     * Verifica i valori numerici dei candidati.
     */
    private void validateNumericCandidateFields(List<NodeCandidate> candidates) {
        for (NodeCandidate candidate : candidates) {
            requireNonNegativeFinite(
                    candidate.getAvailableCpu(),
                    "availableCpu",
                    candidate.getCandidateId()
            );

            requireNonNegativeFinite(
                    candidate.getAvailableBandwidth(),
                    "availableBandwidth",
                    candidate.getCandidateId()
            );

            requireNonNegativeFinite(
                    candidate.getBaseLatencySeconds(),
                    "baseLatencySeconds",
                    candidate.getCandidateId()
            );

            requireNonNegativeFinite(
                    candidate.getCoverageTimeSeconds(),
                    "coverageTimeSeconds",
                    candidate.getCandidateId()
            );
        }
    }

    /**
     * Colleziona gli id dei veicoli.
     */
    private Set<String> collectVehicleIds(List<VehicleSnapshot> vehicles) {
        Set<String> ids = new HashSet<>();

        for (VehicleSnapshot vehicle : vehicles) {
            ids.add(vehicle.getVehicleId());
        }

        return ids;
    }

    /**
     * Verifica che un numero sia finito e non negativo.
     */
    private void requireNonNegativeFinite(
            double value,
            String fieldName,
            String ownerId
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " of " + ownerId + " must be finite."
            );
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(
                    fieldName + " of " + ownerId + " must be >= 0."
            );
        }
    }
}