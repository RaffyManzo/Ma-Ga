package validation.snapshot;

import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Valida uno SystemSnapshot prima dell'esecuzione del MA-GA.
 *
 * Controlla coerenza strutturale, riferimenti tra task/veicoli/candidati
 * e vincoli minimi dei diversi tipi di NodeCandidate.
 */
public final class SnapshotValidator {

    /**
     * Valida uno snapshot completo.
     *
     * @param snapshot snapshot da controllare
     */
    public void validate(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        validateSnapshotHeader(snapshot);

        List<VehicleSnapshot> vehicles = requireList(
                snapshot.getVehicles(),
                "vehicles"
        );

        List<TaskInstance> tasks = requireList(
                snapshot.getTasks(),
                "tasks"
        );

        List<NodeCandidate> candidates = requireList(
                snapshot.getCandidateNodes(),
                "candidateNodes"
        );

        validateVehicleIds(vehicles);
        validateTaskIds(tasks);
        validateCandidateIds(candidates);

        validateNumericVehicleFields(vehicles);
        validateNumericTaskFields(tasks);
        validateNumericCandidateFields(candidates);

        validateTasksReferenceExistingVehicles(tasks, vehicles);
        validateCandidatesReferenceExistingSourceVehicles(candidates, vehicles);
        validateCandidateSemanticRules(candidates, vehicles);
        validateEachTaskHasValidCandidates(tasks, candidates);
    }

    /**
     * Verifica i dati base dello snapshot.
     */
    private void validateSnapshotHeader(SystemSnapshot snapshot) {
        if (snapshot.getSnapshotId() == null || snapshot.getSnapshotId().isBlank()) {
            throw new IllegalArgumentException("snapshotId must not be null or blank.");
        }

        requireNonNegativeFinite(
                snapshot.getTimeSeconds(),
                "timeSeconds",
                snapshot.getSnapshotId()
        );
    }

    /**
     * Verifica che una lista dello snapshot non sia nulla.
     */
    private <T> List<T> requireList(List<T> list, String name) {
        if (list == null) {
            throw new IllegalArgumentException("snapshot." + name + " must not be null.");
        }

        return list;
    }

    /**
     * Verifica unicità e presenza dei vehicleId.
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
     * Verifica unicità e presenza dei taskId.
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
     * Verifica unicità e presenza dei candidateId.
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
     * Verifica che i campi numerici dei veicoli siano validi.
     */
    private void validateNumericVehicleFields(List<VehicleSnapshot> vehicles) {
        for (VehicleSnapshot vehicle : vehicles) {
            requireFinite(vehicle.getX(), "x", vehicle.getVehicleId());
            requireFinite(vehicle.getY(), "y", vehicle.getVehicleId());
            requireNonNegativeFinite(vehicle.getSpeed(), "speed", vehicle.getVehicleId());
            requirePositiveFinite(vehicle.getLocalCpu(), "localCpu", vehicle.getVehicleId());
        }
    }

    /**
     * Verifica che i campi numerici dei task siano validi.
     */
    private void validateNumericTaskFields(List<TaskInstance> tasks) {
        for (TaskInstance task : tasks) {
            requireNonNegativeFinite(
                    task.getInputSizeBits(),
                    "inputSizeBits",
                    task.getTaskId()
            );

            requireNonNegativeFinite(
                    task.getOutputSizeBits(),
                    "outputSizeBits",
                    task.getTaskId()
            );

            requirePositiveFinite(
                    task.getCpuCycles(),
                    "cpuCycles",
                    task.getTaskId()
            );

            requirePositiveFinite(
                    task.getDeadlineSeconds(),
                    "deadlineSeconds",
                    task.getTaskId()
            );
        }
    }

    /**
     * Verifica i valori numerici comuni dei candidati.
     */
    private void validateNumericCandidateFields(List<NodeCandidate> candidates) {
        for (NodeCandidate candidate : candidates) {
            requirePositiveFinite(
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

            validateOptionalFinite(
                    candidate.getNodeX(),
                    "nodeX",
                    candidate.getCandidateId()
            );

            validateOptionalFinite(
                    candidate.getNodeY(),
                    "nodeY",
                    candidate.getCandidateId()
            );

            validateOptionalPositive(
                    candidate.getCoverageRadiusMeters(),
                    "coverageRadiusMeters",
                    candidate.getCandidateId()
            );
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
     * Verifica le regole specifiche dei tipi di candidato.
     */
    private void validateCandidateSemanticRules(
            List<NodeCandidate> candidates,
            List<VehicleSnapshot> vehicles
    ) {
        Set<String> vehicleIds = collectVehicleIds(vehicles);

        for (NodeCandidate candidate : candidates) {
            validateLocalCandidate(candidate);
            validateVehicleCandidate(candidate, vehicleIds);
            validateEdgeCandidate(candidate);
            validateCloudCandidate(candidate);
        }
    }

    /**
     * Verifica i candidati LOCAL.
     */
    private void validateLocalCandidate(NodeCandidate candidate) {
        if (candidate.getType() != NodeType.LOCAL) {
            return;
        }

        if (!candidate.getSourceVehicleId().equals(candidate.getExecutionNodeId())) {
            throw new IllegalArgumentException(
                    "LOCAL candidate " + candidate.getCandidateId()
                            + " must have sourceVehicleId == executionNodeId."
            );
        }

        if (candidate.getAvailableBandwidth() != 0.0) {
            throw new IllegalArgumentException(
                    "LOCAL candidate " + candidate.getCandidateId()
                            + " must have availableBandwidth == 0."
            );
        }

        if (candidate.getBaseLatencySeconds() != 0.0) {
            throw new IllegalArgumentException(
                    "LOCAL candidate " + candidate.getCandidateId()
                            + " must have baseLatencySeconds == 0."
            );
        }
    }

    /**
     * Verifica i candidati VEHICLE usati per V2V.
     */
    private void validateVehicleCandidate(
            NodeCandidate candidate,
            Set<String> vehicleIds
    ) {
        if (candidate.getType() != NodeType.VEHICLE) {
            return;
        }

        if (candidate.getSourceVehicleId().equals(candidate.getExecutionNodeId())) {
            throw new IllegalArgumentException(
                    "VEHICLE candidate " + candidate.getCandidateId()
                            + " must have sourceVehicleId != executionNodeId."
            );
        }

        if (!vehicleIds.contains(candidate.getExecutionNodeId())) {
            throw new IllegalArgumentException(
                    "VEHICLE candidate " + candidate.getCandidateId()
                            + " references missing execution vehicle: "
                            + candidate.getExecutionNodeId()
            );
        }

        if (candidate.getAvailableBandwidth() <= 0.0) {
            throw new IllegalArgumentException(
                    "VEHICLE candidate " + candidate.getCandidateId()
                            + " must have availableBandwidth > 0."
            );
        }
    }

    /**
     * Verifica i candidati EDGE/RSU.
     */
    private void validateEdgeCandidate(NodeCandidate candidate) {
        if (candidate.getType() != NodeType.EDGE) {
            return;
        }

        if (!candidate.hasCoverageGeometry()) {
            throw new IllegalArgumentException(
                    "EDGE candidate " + candidate.getCandidateId()
                            + " must define nodeX, nodeY and coverageRadiusMeters."
            );
        }

        if (candidate.getAvailableBandwidth() <= 0.0) {
            throw new IllegalArgumentException(
                    "EDGE candidate " + candidate.getCandidateId()
                            + " must have availableBandwidth > 0."
            );
        }
    }

    /**
     * Verifica i candidati CLOUD.
     */
    private void validateCloudCandidate(NodeCandidate candidate) {
        if (candidate.getType() != NodeType.CLOUD) {
            return;
        }

        if (candidate.getAvailableBandwidth() <= 0.0) {
            throw new IllegalArgumentException(
                    "CLOUD candidate " + candidate.getCandidateId()
                            + " must have availableBandwidth > 0."
            );
        }
    }

    /**
     * Verifica che ogni task abbia candidati compatibili e almeno un LOCAL.
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
     * Colleziona gli identificativi dei veicoli.
     */
    private Set<String> collectVehicleIds(List<VehicleSnapshot> vehicles) {
        Set<String> ids = new HashSet<>();

        for (VehicleSnapshot vehicle : vehicles) {
            ids.add(vehicle.getVehicleId());
        }

        return ids;
    }

    private void requireFinite(
            double value,
            String fieldName,
            String ownerId
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " of " + ownerId + " must be finite."
            );
        }
    }

    private void requireNonNegativeFinite(
            double value,
            String fieldName,
            String ownerId
    ) {
        requireFinite(value, fieldName, ownerId);

        if (value < 0.0) {
            throw new IllegalArgumentException(
                    fieldName + " of " + ownerId + " must be >= 0."
            );
        }
    }

    private void requirePositiveFinite(
            double value,
            String fieldName,
            String ownerId
    ) {
        requireFinite(value, fieldName, ownerId);

        if (value <= 0.0) {
            throw new IllegalArgumentException(
                    fieldName + " of " + ownerId + " must be > 0."
            );
        }
    }

    private void validateOptionalFinite(
            Double value,
            String fieldName,
            String ownerId
    ) {
        if (value == null) {
            return;
        }

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " of " + ownerId + " must be finite."
            );
        }
    }

    private void validateOptionalPositive(
            Double value,
            String fieldName,
            String ownerId
    ) {
        if (value == null) {
            return;
        }

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " of " + ownerId + " must be finite."
            );
        }

        if (value <= 0.0) {
            throw new IllegalArgumentException(
                    fieldName + " of " + ownerId + " must be > 0."
            );
        }
    }
}