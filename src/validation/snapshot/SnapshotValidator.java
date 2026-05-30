package validation.snapshot;

import io.snapshot.dto.NodeCandidateInputDto;
import io.snapshot.dto.SnapshotInputDto;
import io.snapshot.dto.TaskInputDto;
import io.snapshot.dto.VehicleInputDto;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Punto unico di validazione degli snapshot.
 *
 * <p>La validazione puo' avvenire prima del mapping, sui DTO grezzi letti dal
 * JSON, oppure sul domain model per compatibilita' con i chiamanti esistenti.
 * Le regole applicate restano le stesse: campi obbligatori, valori numerici,
 * unicita' degli ID, riferimenti e vincoli per tipo di candidato.</p>
 */
public final class SnapshotValidator {

    /**
     * Valida lo snapshot grezzo prima della costruzione del domain model.
     *
     * @param dto snapshot letto dal JSON o da un adapter esterno
     */
    public void validate(SnapshotInputDto dto) {
        Objects.requireNonNull(dto, "snapshot input must not be null.");

        validateSnapshot(
                dto.snapshotId,
                dto.timeSeconds,
                toVehicleDataFromDto(dto.vehicles),
                toTaskDataFromDto(dto.tasks),
                toCandidateDataFromDto(dto.candidateNodes)
        );
    }

    /**
     * Valida uno snapshot gia' costruito.
     *
     * @param snapshot snapshot da controllare
     */
    public void validate(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        validateSnapshot(
                snapshot.getSnapshotId(),
                snapshot.getTimeSeconds(),
                toVehicleDataFromModel(snapshot.getVehicles()),
                toTaskDataFromModel(snapshot.getTasks()),
                toCandidateDataFromModel(snapshot.getCandidateNodes())
        );
    }

    private void validateSnapshot(
            String snapshotId,
            Double timeSeconds,
            List<VehicleData> vehicles,
            List<TaskData> tasks,
            List<CandidateData> candidates
    ) {
        requireText(snapshotId, "snapshotId");
        requireNonNegativeFinite(timeSeconds, "timeSeconds", snapshotId);

        validateVehicleIds(vehicles);
        validateTaskIds(tasks);
        validateCandidateIds(candidates);
        validateCandidateTypes(candidates);

        validateVehicleNumericFields(vehicles);
        validateTaskRequiredFields(tasks);
        validateTaskNumericFields(tasks);
        validateCandidateRequiredFields(candidates);
        validateCandidateNumericFields(candidates);

        validateTasksReferenceExistingVehicles(tasks, vehicles);
        validateCandidatesReferenceExistingSourceVehicles(candidates, vehicles);
        validateCandidateSemanticRules(candidates, vehicles);
        validateEachTaskHasValidCandidates(tasks, candidates);
    }

    private List<VehicleData> toVehicleDataFromDto(
            List<VehicleInputDto> vehicles
    ) {
        List<VehicleInputDto> safeVehicles = requireList(vehicles, "vehicles");
        List<VehicleData> result = new ArrayList<>();

        for (int i = 0; i < safeVehicles.size(); i++) {
            VehicleInputDto vehicle = requireElement(
                    safeVehicles.get(i),
                    "vehicles",
                    i
            );

            result.add(
                    new VehicleData(
                            vehicle.vehicleId,
                            vehicle.x,
                            vehicle.y,
                            vehicle.speed,
                            vehicle.localCpu
                    )
            );
        }

        return result;
    }

    private List<VehicleData> toVehicleDataFromModel(
            List<VehicleSnapshot> vehicles
    ) {
        List<VehicleSnapshot> safeVehicles = requireList(vehicles, "vehicles");
        List<VehicleData> result = new ArrayList<>();

        for (int i = 0; i < safeVehicles.size(); i++) {
            VehicleSnapshot vehicle = requireElement(
                    safeVehicles.get(i),
                    "vehicles",
                    i
            );

            result.add(
                    new VehicleData(
                            vehicle.getVehicleId(),
                            vehicle.getX(),
                            vehicle.getY(),
                            vehicle.getSpeed(),
                            vehicle.getLocalCpu()
                    )
            );
        }

        return result;
    }

    private List<TaskData> toTaskDataFromDto(List<TaskInputDto> tasks) {
        List<TaskInputDto> safeTasks = requireList(tasks, "tasks");
        List<TaskData> result = new ArrayList<>();

        for (int i = 0; i < safeTasks.size(); i++) {
            TaskInputDto task = requireElement(safeTasks.get(i), "tasks", i);

            result.add(
                    new TaskData(
                            task.taskId,
                            task.sourceVehicleId,
                            task.inputSizeBits,
                            task.outputSizeBits,
                            task.cpuCycles,
                            task.deadlineSeconds
                    )
            );
        }

        return result;
    }

    private List<TaskData> toTaskDataFromModel(List<TaskInstance> tasks) {
        List<TaskInstance> safeTasks = requireList(tasks, "tasks");
        List<TaskData> result = new ArrayList<>();

        for (int i = 0; i < safeTasks.size(); i++) {
            TaskInstance task = requireElement(safeTasks.get(i), "tasks", i);

            result.add(
                    new TaskData(
                            task.getTaskId(),
                            task.getSourceVehicleId(),
                            task.getInputSizeBits(),
                            task.getOutputSizeBits(),
                            task.getCpuCycles(),
                            task.getDeadlineSeconds()
                    )
            );
        }

        return result;
    }

    private List<CandidateData> toCandidateDataFromDto(
            List<NodeCandidateInputDto> candidates
    ) {
        List<NodeCandidateInputDto> safeCandidates =
                requireList(candidates, "candidateNodes");

        List<CandidateData> result = new ArrayList<>();

        for (int i = 0; i < safeCandidates.size(); i++) {
            NodeCandidateInputDto candidate = requireElement(
                    safeCandidates.get(i),
                    "candidateNodes",
                    i
            );

            result.add(
                    CandidateData.fromInput(
                            candidate.candidateId,
                            candidate.sourceVehicleId,
                            candidate.executionNodeId,
                            candidate.type,
                            candidate.availableCpu,
                            candidate.availableBandwidth,
                            candidate.baseLatencySeconds,
                            candidate.nodeX,
                            candidate.nodeY,
                            candidate.coverageRadiusMeters
                    )
            );
        }

        return result;
    }

    private List<CandidateData> toCandidateDataFromModel(
            List<NodeCandidate> candidates
    ) {
        List<NodeCandidate> safeCandidates =
                requireList(candidates, "candidateNodes");

        List<CandidateData> result = new ArrayList<>();

        for (int i = 0; i < safeCandidates.size(); i++) {
            NodeCandidate candidate = requireElement(
                    safeCandidates.get(i),
                    "candidateNodes",
                    i
            );

            result.add(
                    CandidateData.fromModel(
                            candidate.getCandidateId(),
                            candidate.getSourceVehicleId(),
                            candidate.getExecutionNodeId(),
                            candidate.getType(),
                            candidate.getAvailableCpu(),
                            candidate.getAvailableBandwidth(),
                            candidate.getBaseLatencySeconds(),
                            candidate.getNodeX(),
                            candidate.getNodeY(),
                            candidate.getCoverageRadiusMeters()
                    )
            );
        }

        return result;
    }

    private <T> List<T> requireList(List<T> list, String name) {
        if (list == null) {
            throw new IllegalArgumentException("snapshot." + name + " must not be null.");
        }

        return list;
    }

    private <T> T requireElement(T element, String listName, int index) {
        if (element == null) {
            throw new IllegalArgumentException(
                    "snapshot." + listName + "[" + index + "] must not be null."
            );
        }

        return element;
    }

    private void validateVehicleIds(List<VehicleData> vehicles) {
        Set<String> ids = new HashSet<>();

        for (VehicleData vehicle : vehicles) {
            requireText(vehicle.vehicleId, "vehicleId");

            if (!ids.add(vehicle.vehicleId)) {
                throw new IllegalArgumentException(
                        "Duplicated vehicleId: " + vehicle.vehicleId
                );
            }
        }
    }

    private void validateTaskIds(List<TaskData> tasks) {
        Set<String> ids = new HashSet<>();

        for (TaskData task : tasks) {
            requireText(task.taskId, "taskId");

            if (!ids.add(task.taskId)) {
                throw new IllegalArgumentException(
                        "Duplicated taskId: " + task.taskId
                );
            }
        }
    }

    private void validateCandidateIds(List<CandidateData> candidates) {
        Set<String> ids = new HashSet<>();

        for (CandidateData candidate : candidates) {
            requireText(candidate.candidateId, "candidateId");

            if (!ids.add(candidate.candidateId)) {
                throw new IllegalArgumentException(
                        "Duplicated candidateId: " + candidate.candidateId
                );
            }
        }
    }

    private void validateCandidateTypes(List<CandidateData> candidates) {
        for (CandidateData candidate : candidates) {
            if (candidate.type != null) {
                continue;
            }

            candidate.type = parseNodeType(
                    candidate.rawType,
                    candidate.candidateId
            );
        }
    }

    private void validateVehicleNumericFields(List<VehicleData> vehicles) {
        for (VehicleData vehicle : vehicles) {
            requireFinite(vehicle.x, "x", vehicle.vehicleId);
            requireFinite(vehicle.y, "y", vehicle.vehicleId);
            requireNonNegativeFinite(vehicle.speed, "speed", vehicle.vehicleId);
            requirePositiveFinite(vehicle.localCpu, "localCpu", vehicle.vehicleId);
        }
    }

    private void validateTaskRequiredFields(List<TaskData> tasks) {
        for (TaskData task : tasks) {
            requireText(task.sourceVehicleId, "sourceVehicleId of " + task.taskId);
        }
    }

    private void validateTaskNumericFields(List<TaskData> tasks) {
        for (TaskData task : tasks) {
            requireNonNegativeFinite(
                    task.inputSizeBits,
                    "inputSizeBits",
                    task.taskId
            );
            requireNonNegativeFinite(
                    task.outputSizeBits,
                    "outputSizeBits",
                    task.taskId
            );
            requirePositiveFinite(task.cpuCycles, "cpuCycles", task.taskId);
            requirePositiveFinite(
                    task.deadlineSeconds,
                    "deadlineSeconds",
                    task.taskId
            );
        }
    }

    private void validateCandidateRequiredFields(
            List<CandidateData> candidates
    ) {
        for (CandidateData candidate : candidates) {
            requireText(
                    candidate.sourceVehicleId,
                    "sourceVehicleId of " + candidate.candidateId
            );
            requireText(
                    candidate.executionNodeId,
                    "executionNodeId of " + candidate.candidateId
            );
        }
    }

    private void validateCandidateNumericFields(
            List<CandidateData> candidates
    ) {
        for (CandidateData candidate : candidates) {
            requirePositiveFinite(
                    candidate.availableCpu,
                    "availableCpu",
                    candidate.candidateId
            );
            requireNonNegativeFinite(
                    candidate.availableBandwidth,
                    "availableBandwidth",
                    candidate.candidateId
            );
            requireNonNegativeFinite(
                    candidate.baseLatencySeconds,
                    "baseLatencySeconds",
                    candidate.candidateId
            );
            validateOptionalFinite(
                    candidate.nodeX,
                    "nodeX",
                    candidate.candidateId
            );
            validateOptionalFinite(
                    candidate.nodeY,
                    "nodeY",
                    candidate.candidateId
            );
            validateOptionalPositive(
                    candidate.coverageRadiusMeters,
                    "coverageRadiusMeters",
                    candidate.candidateId
            );
        }
    }

    private void validateTasksReferenceExistingVehicles(
            List<TaskData> tasks,
            List<VehicleData> vehicles
    ) {
        Set<String> vehicleIds = collectVehicleIds(vehicles);

        for (TaskData task : tasks) {
            if (!vehicleIds.contains(task.sourceVehicleId)) {
                throw new IllegalArgumentException(
                        "Task " + task.taskId
                                + " references missing source vehicle: "
                                + task.sourceVehicleId
                );
            }
        }
    }

    private void validateCandidatesReferenceExistingSourceVehicles(
            List<CandidateData> candidates,
            List<VehicleData> vehicles
    ) {
        Set<String> vehicleIds = collectVehicleIds(vehicles);

        for (CandidateData candidate : candidates) {
            if (!vehicleIds.contains(candidate.sourceVehicleId)) {
                throw new IllegalArgumentException(
                        "Candidate " + candidate.candidateId
                                + " references missing source vehicle: "
                                + candidate.sourceVehicleId
                );
            }
        }
    }

    private void validateCandidateSemanticRules(
            List<CandidateData> candidates,
            List<VehicleData> vehicles
    ) {
        Set<String> vehicleIds = collectVehicleIds(vehicles);

        for (CandidateData candidate : candidates) {
            validateLocalCandidate(candidate);
            validateVehicleCandidate(candidate, vehicleIds);
            validateEdgeCandidate(candidate);
            validateCloudCandidate(candidate);
        }
    }

    private void validateLocalCandidate(CandidateData candidate) {
        if (candidate.type != NodeType.LOCAL) {
            return;
        }

        if (!candidate.sourceVehicleId.equals(candidate.executionNodeId)) {
            throw new IllegalArgumentException(
                    "LOCAL candidate " + candidate.candidateId
                            + " must have sourceVehicleId == executionNodeId."
            );
        }

        if (candidate.availableBandwidth != 0.0) {
            throw new IllegalArgumentException(
                    "LOCAL candidate " + candidate.candidateId
                            + " must have availableBandwidth == 0."
            );
        }

        if (candidate.baseLatencySeconds != 0.0) {
            throw new IllegalArgumentException(
                    "LOCAL candidate " + candidate.candidateId
                            + " must have baseLatencySeconds == 0."
            );
        }
    }

    private void validateVehicleCandidate(
            CandidateData candidate,
            Set<String> vehicleIds
    ) {
        if (candidate.type != NodeType.VEHICLE) {
            return;
        }

        if (candidate.sourceVehicleId.equals(candidate.executionNodeId)) {
            throw new IllegalArgumentException(
                    "VEHICLE candidate " + candidate.candidateId
                            + " must have sourceVehicleId != executionNodeId."
            );
        }

        if (!vehicleIds.contains(candidate.executionNodeId)) {
            throw new IllegalArgumentException(
                    "VEHICLE candidate " + candidate.candidateId
                            + " references missing execution vehicle: "
                            + candidate.executionNodeId
            );
        }

        if (candidate.availableBandwidth <= 0.0) {
            throw new IllegalArgumentException(
                    "VEHICLE candidate " + candidate.candidateId
                            + " must have availableBandwidth > 0."
            );
        }
    }

    private void validateEdgeCandidate(CandidateData candidate) {
        if (candidate.type != NodeType.EDGE) {
            return;
        }

        if (!candidate.hasCoverageGeometry()) {
            throw new IllegalArgumentException(
                    "EDGE candidate " + candidate.candidateId
                            + " must define nodeX, nodeY and coverageRadiusMeters."
            );
        }

        if (candidate.availableBandwidth <= 0.0) {
            throw new IllegalArgumentException(
                    "EDGE candidate " + candidate.candidateId
                            + " must have availableBandwidth > 0."
            );
        }
    }

    private void validateCloudCandidate(CandidateData candidate) {
        if (candidate.type != NodeType.CLOUD) {
            return;
        }

        if (candidate.availableBandwidth <= 0.0) {
            throw new IllegalArgumentException(
                    "CLOUD candidate " + candidate.candidateId
                            + " must have availableBandwidth > 0."
            );
        }
    }

    private void validateEachTaskHasValidCandidates(
            List<TaskData> tasks,
            List<CandidateData> candidates
    ) {
        for (TaskData task : tasks) {
            boolean hasAnyCandidate = false;
            boolean hasLocalCandidate = false;

            for (CandidateData candidate : candidates) {
                if (!candidate.sourceVehicleId.equals(task.sourceVehicleId)) {
                    continue;
                }

                hasAnyCandidate = true;

                if (candidate.type == NodeType.LOCAL) {
                    hasLocalCandidate = true;
                }
            }

            if (!hasAnyCandidate) {
                throw new IllegalArgumentException(
                        "Task " + task.taskId
                                + " has no valid execution candidate."
                );
            }

            if (!hasLocalCandidate) {
                throw new IllegalArgumentException(
                        "Task " + task.taskId
                                + " has no LOCAL candidate for source vehicle "
                                + task.sourceVehicleId
                );
            }
        }
    }

    private Set<String> collectVehicleIds(List<VehicleData> vehicles) {
        Set<String> ids = new HashSet<>();

        for (VehicleData vehicle : vehicles) {
            ids.add(vehicle.vehicleId);
        }

        return ids;
    }

    private NodeType parseNodeType(String rawType, String candidateId) {
        if (rawType == null || rawType.isBlank()) {
            throw new IllegalArgumentException(
                    "type of " + candidateId + " must not be null or blank."
            );
        }

        try {
            return NodeType.valueOf(rawType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "type of " + candidateId + " is not supported: " + rawType,
                    ex
            );
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be null or blank."
            );
        }
    }

    private void requireFinite(
            Double value,
            String fieldName,
            String ownerId
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " of " + ownerId + " must be present."
            );
        }

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " of " + ownerId + " must be finite."
            );
        }
    }

    private void requireNonNegativeFinite(
            Double value,
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
            Double value,
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

    private static final class VehicleData {

        private final String vehicleId;
        private final Double x;
        private final Double y;
        private final Double speed;
        private final Double localCpu;

        private VehicleData(
                String vehicleId,
                Double x,
                Double y,
                Double speed,
                Double localCpu
        ) {
            this.vehicleId = vehicleId;
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.localCpu = localCpu;
        }
    }

    private static final class TaskData {

        private final String taskId;
        private final String sourceVehicleId;
        private final Double inputSizeBits;
        private final Double outputSizeBits;
        private final Double cpuCycles;
        private final Double deadlineSeconds;

        private TaskData(
                String taskId,
                String sourceVehicleId,
                Double inputSizeBits,
                Double outputSizeBits,
                Double cpuCycles,
                Double deadlineSeconds
        ) {
            this.taskId = taskId;
            this.sourceVehicleId = sourceVehicleId;
            this.inputSizeBits = inputSizeBits;
            this.outputSizeBits = outputSizeBits;
            this.cpuCycles = cpuCycles;
            this.deadlineSeconds = deadlineSeconds;
        }
    }

    private static final class CandidateData {

        private final String candidateId;
        private final String sourceVehicleId;
        private final String executionNodeId;
        private final String rawType;
        private NodeType type;
        private final Double availableCpu;
        private final Double availableBandwidth;
        private final Double baseLatencySeconds;
        private final Double nodeX;
        private final Double nodeY;
        private final Double coverageRadiusMeters;

        private CandidateData(
                String candidateId,
                String sourceVehicleId,
                String executionNodeId,
                String rawType,
                NodeType type,
                Double availableCpu,
                Double availableBandwidth,
                Double baseLatencySeconds,
                Double nodeX,
                Double nodeY,
                Double coverageRadiusMeters
        ) {
            this.candidateId = candidateId;
            this.sourceVehicleId = sourceVehicleId;
            this.executionNodeId = executionNodeId;
            this.rawType = rawType;
            this.type = type;
            this.availableCpu = availableCpu;
            this.availableBandwidth = availableBandwidth;
            this.baseLatencySeconds = baseLatencySeconds;
            this.nodeX = nodeX;
            this.nodeY = nodeY;
            this.coverageRadiusMeters = coverageRadiusMeters;
        }

        private static CandidateData fromInput(
                String candidateId,
                String sourceVehicleId,
                String executionNodeId,
                String rawType,
                Double availableCpu,
                Double availableBandwidth,
                Double baseLatencySeconds,
                Double nodeX,
                Double nodeY,
                Double coverageRadiusMeters
        ) {
            return new CandidateData(
                    candidateId,
                    sourceVehicleId,
                    executionNodeId,
                    rawType,
                    null,
                    availableCpu,
                    availableBandwidth,
                    baseLatencySeconds,
                    nodeX,
                    nodeY,
                    coverageRadiusMeters
            );
        }

        private static CandidateData fromModel(
                String candidateId,
                String sourceVehicleId,
                String executionNodeId,
                NodeType type,
                Double availableCpu,
                Double availableBandwidth,
                Double baseLatencySeconds,
                Double nodeX,
                Double nodeY,
                Double coverageRadiusMeters
        ) {
            return new CandidateData(
                    candidateId,
                    sourceVehicleId,
                    executionNodeId,
                    null,
                    type,
                    availableCpu,
                    availableBandwidth,
                    baseLatencySeconds,
                    nodeX,
                    nodeY,
                    coverageRadiusMeters
            );
        }

        private boolean hasCoverageGeometry() {
            return nodeX != null
                    && nodeY != null
                    && coverageRadiusMeters != null;
        }
    }
}
