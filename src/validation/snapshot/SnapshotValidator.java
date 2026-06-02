package validation.snapshot;

import io.snapshot.dto.AccessGatewayInputDto;
import io.snapshot.dto.AccessLinkInputDto;
import io.snapshot.dto.NodeCandidateInputDto;
import io.snapshot.dto.SnapshotInputDto;
import io.snapshot.dto.TaskInputDto;
import io.snapshot.dto.VehicleInputDto;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Punto unico di validazione degli snapshot gateway-aware.
 *
 * <p>La modalità ufficiale è rigida: gateway e access link devono essere
 * espliciti. Ogni veicolo osservato possiede esattamente un access link attivo.
 * Il link può essere momentaneamente non disponibile: in quel caso il prefilter
 * elimina i candidati CLOUD, senza trasformare la fragilità in errore strutturale.</p>
 */
public final class SnapshotValidator {

    public void validate(SnapshotInputDto dto) {
        Objects.requireNonNull(dto, "snapshot input must not be null.");
        requireText(dto.snapshotId, "snapshotId");
        requireNonNegative(dto.timeSeconds, "timeSeconds", dto.snapshotId);
        requireList(dto.vehicles, "vehicles");
        requireList(dto.tasks, "tasks");
        requireList(dto.candidateNodes, "candidateNodes");
        requireList(dto.accessGateways, "accessGateways");
        requireList(dto.accessLinks, "accessLinks");

        Set<String> vehicleIds = new HashSet<>();
        for (VehicleInputDto vehicle : dto.vehicles) {
            requireNonNull(vehicle, "vehicles element");
            requireUnique(vehicle.vehicleId, "vehicleId", vehicleIds);
            requireFinite(vehicle.x, "x", vehicle.vehicleId);
            requireFinite(vehicle.y, "y", vehicle.vehicleId);
            requireNonNegative(vehicle.speed, "speed", vehicle.vehicleId);
            requirePositive(vehicle.localCpu, "localCpu", vehicle.vehicleId);
        }

        Set<String> gatewayIds = new HashSet<>();
        for (AccessGatewayInputDto gateway : dto.accessGateways) {
            requireNonNull(gateway, "accessGateways element");
            requireUnique(gateway.gatewayId, "gatewayId", gatewayIds);
            requireText(gateway.gatewayType, "gatewayType of " + gateway.gatewayId);
            requireFinite(gateway.x, "x", gateway.gatewayId);
            requireFinite(gateway.y, "y", gateway.gatewayId);
            requirePositive(gateway.coverageRadiusMeters, "coverageRadiusMeters", gateway.gatewayId);
        }

        Set<String> linkIds = new HashSet<>();
        Map<String, Integer> activeByVehicle = new HashMap<>();
        for (AccessLinkInputDto link : dto.accessLinks) {
            requireNonNull(link, "accessLinks element");
            requireUnique(link.accessLinkId, "accessLinkId", linkIds);
            requireText(link.vehicleId, "vehicleId of " + link.accessLinkId);
            requireText(link.gatewayId, "gatewayId of " + link.accessLinkId);
            requireNonNull(link.active, "active of " + link.accessLinkId);
            requireNonNull(link.available, "available of " + link.accessLinkId);
            requireReference(vehicleIds, link.vehicleId, "Access link " + link.accessLinkId + " references missing vehicle: ");
            requireReference(gatewayIds, link.gatewayId, "Access link " + link.accessLinkId + " references missing gateway: ");
            if (Boolean.TRUE.equals(link.active)) {
                activeByVehicle.merge(link.vehicleId, 1, Integer::sum);
            }
        }
        validateExactlyOneActiveLinkPerVehicle(vehicleIds, activeByVehicle);

        Set<String> taskIds = new HashSet<>();
        for (TaskInputDto task : dto.tasks) {
            requireNonNull(task, "tasks element");
            requireUnique(task.taskId, "taskId", taskIds);
            requireText(task.sourceVehicleId, "sourceVehicleId of " + task.taskId);
            requireReference(vehicleIds, task.sourceVehicleId, "Task " + task.taskId + " references missing source vehicle: ");
            requireNonNegative(task.inputSizeBits, "inputSizeBits", task.taskId);
            requireNonNegative(task.outputSizeBits, "outputSizeBits", task.taskId);
            requirePositive(task.cpuCycles, "cpuCycles", task.taskId);
            requirePositive(task.deadlineSeconds, "deadlineSeconds", task.taskId);
        }

        Set<String> candidateIds = new HashSet<>();
        Set<String> localSources = new HashSet<>();
        for (NodeCandidateInputDto candidate : dto.candidateNodes) {
            requireNonNull(candidate, "candidateNodes element");
            requireUnique(candidate.candidateId, "candidateId", candidateIds);
            requireText(candidate.sourceVehicleId, "sourceVehicleId of " + candidate.candidateId);
            requireText(candidate.executionNodeId, "executionNodeId of " + candidate.candidateId);
            requireReference(vehicleIds, candidate.sourceVehicleId, "Candidate " + candidate.candidateId + " references missing source vehicle: ");
            NodeType type = parseNodeType(candidate.type, candidate.candidateId);
            validateCandidateNumbers(candidate);
            validateCandidateRules(candidate, type, vehicleIds);
            if (type == NodeType.LOCAL) {
                localSources.add(candidate.sourceVehicleId);
            }
        }
        for (TaskInputDto task : dto.tasks) {
            if (!localSources.contains(task.sourceVehicleId)) {
                throw new IllegalArgumentException(
                        "Task " + task.taskId + " has no LOCAL candidate for source vehicle " + task.sourceVehicleId
                );
            }
        }
    }

    public void validate(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        requireText(snapshot.getSnapshotId(), "snapshotId");
        requireNonNegative(snapshot.getTimeSeconds(), "timeSeconds", snapshot.getSnapshotId());
        requireList(snapshot.getVehicles(), "vehicles");
        requireList(snapshot.getTasks(), "tasks");
        requireList(snapshot.getCandidateNodes(), "candidateNodes");
        requireList(snapshot.getAccessGateways(), "accessGateways");
        requireList(snapshot.getAccessLinks(), "accessLinks");

        Set<String> vehicleIds = new HashSet<>();
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            requireNonNull(vehicle, "vehicles element");
            requireUnique(vehicle.getVehicleId(), "vehicleId", vehicleIds);
            requireFinite(vehicle.getX(), "x", vehicle.getVehicleId());
            requireFinite(vehicle.getY(), "y", vehicle.getVehicleId());
            requireNonNegative(vehicle.getSpeed(), "speed", vehicle.getVehicleId());
            requirePositive(vehicle.getLocalCpu(), "localCpu", vehicle.getVehicleId());
        }

        Set<String> gatewayIds = new HashSet<>();
        for (AccessGatewaySnapshot gateway : snapshot.getAccessGateways()) {
            requireNonNull(gateway, "accessGateways element");
            requireUnique(gateway.getGatewayId(), "gatewayId", gatewayIds);
            requireText(gateway.getGatewayType(), "gatewayType of " + gateway.getGatewayId());
            requireFinite(gateway.getX(), "x", gateway.getGatewayId());
            requireFinite(gateway.getY(), "y", gateway.getGatewayId());
            requirePositive(gateway.getCoverageRadiusMeters(), "coverageRadiusMeters", gateway.getGatewayId());
        }

        Set<String> linkIds = new HashSet<>();
        Map<String, Integer> activeByVehicle = new HashMap<>();
        for (AccessLinkSnapshot link : snapshot.getAccessLinks()) {
            requireNonNull(link, "accessLinks element");
            requireUnique(link.getAccessLinkId(), "accessLinkId", linkIds);
            requireReference(vehicleIds, link.getVehicleId(), "Access link " + link.getAccessLinkId() + " references missing vehicle: ");
            requireReference(gatewayIds, link.getGatewayId(), "Access link " + link.getAccessLinkId() + " references missing gateway: ");
            if (link.isActive()) {
                activeByVehicle.merge(link.getVehicleId(), 1, Integer::sum);
            }
        }
        validateExactlyOneActiveLinkPerVehicle(vehicleIds, activeByVehicle);

        Set<String> taskSources = new HashSet<>();
        Set<String> taskIds = new HashSet<>();
        for (TaskInstance task : snapshot.getTasks()) {
            requireNonNull(task, "tasks element");
            requireUnique(task.getTaskId(), "taskId", taskIds);
            requireReference(vehicleIds, task.getSourceVehicleId(), "Task " + task.getTaskId() + " references missing source vehicle: ");
            requireNonNegative(task.getInputSizeBits(), "inputSizeBits", task.getTaskId());
            requireNonNegative(task.getOutputSizeBits(), "outputSizeBits", task.getTaskId());
            requirePositive(task.getCpuCycles(), "cpuCycles", task.getTaskId());
            requirePositive(task.getDeadlineSeconds(), "deadlineSeconds", task.getTaskId());
            taskSources.add(task.getSourceVehicleId());
        }

        Set<String> candidateIds = new HashSet<>();
        Set<String> localSources = new HashSet<>();
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            requireNonNull(candidate, "candidateNodes element");
            requireUnique(candidate.getCandidateId(), "candidateId", candidateIds);
            requireReference(vehicleIds, candidate.getSourceVehicleId(), "Candidate " + candidate.getCandidateId() + " references missing source vehicle: ");
            validateCandidateRules(candidate, vehicleIds);
            if (candidate.getType() == NodeType.LOCAL) {
                localSources.add(candidate.getSourceVehicleId());
            }
        }
        for (String taskSource : taskSources) {
            if (!localSources.contains(taskSource)) {
                throw new IllegalArgumentException("Missing LOCAL candidate for source vehicle " + taskSource);
            }
        }
    }

    private void validateCandidateNumbers(NodeCandidateInputDto c) {
        requirePositive(c.availableCpu, "availableCpu", c.candidateId);
        requireNonNegative(c.availableBandwidth, "availableBandwidth", c.candidateId);
        requireNonNegative(c.baseLatencySeconds, "baseLatencySeconds", c.candidateId);
        optionalFinite(c.nodeX, "nodeX", c.candidateId);
        optionalFinite(c.nodeY, "nodeY", c.candidateId);
        optionalPositive(c.coverageRadiusMeters, "coverageRadiusMeters", c.candidateId);
    }

    private void validateCandidateRules(NodeCandidateInputDto c, NodeType type, Set<String> vehicleIds) {
        if (type == NodeType.LOCAL) {
            if (!c.sourceVehicleId.equals(c.executionNodeId) || c.availableBandwidth != 0.0 || c.baseLatencySeconds != 0.0) {
                throw new IllegalArgumentException("Invalid LOCAL candidate: " + c.candidateId);
            }
        } else if (type == NodeType.VEHICLE) {
            if (c.sourceVehicleId.equals(c.executionNodeId) || !vehicleIds.contains(c.executionNodeId) || c.availableBandwidth <= 0.0) {
                throw new IllegalArgumentException("Invalid VEHICLE candidate: " + c.candidateId);
            }
        } else if (type == NodeType.EDGE) {
            if (c.nodeX == null || c.nodeY == null || c.coverageRadiusMeters == null || c.availableBandwidth <= 0.0) {
                throw new IllegalArgumentException("Invalid EDGE candidate: " + c.candidateId);
            }
        } else if (type == NodeType.CLOUD && c.availableBandwidth <= 0.0) {
            throw new IllegalArgumentException("Invalid CLOUD candidate: " + c.candidateId);
        }
    }

    private void validateCandidateRules(NodeCandidate c, Set<String> vehicleIds) {
        if (c.getType() == NodeType.LOCAL) {
            if (!c.getSourceVehicleId().equals(c.getExecutionNodeId()) || c.getAvailableBandwidth() != 0.0 || c.getBaseLatencySeconds() != 0.0) {
                throw new IllegalArgumentException("Invalid LOCAL candidate: " + c.getCandidateId());
            }
        } else if (c.getType() == NodeType.VEHICLE) {
            if (c.getSourceVehicleId().equals(c.getExecutionNodeId()) || !vehicleIds.contains(c.getExecutionNodeId()) || c.getAvailableBandwidth() <= 0.0) {
                throw new IllegalArgumentException("Invalid VEHICLE candidate: " + c.getCandidateId());
            }
        } else if (c.getType() == NodeType.EDGE) {
            if (!c.hasCoverageGeometry() || c.getAvailableBandwidth() <= 0.0) {
                throw new IllegalArgumentException("Invalid EDGE candidate: " + c.getCandidateId());
            }
        } else if (c.getType() == NodeType.CLOUD && c.getAvailableBandwidth() <= 0.0) {
            throw new IllegalArgumentException("Invalid CLOUD candidate: " + c.getCandidateId());
        }
    }

    private void validateExactlyOneActiveLinkPerVehicle(Set<String> vehicleIds, Map<String, Integer> activeByVehicle) {
        for (String vehicleId : vehicleIds) {
            int count = activeByVehicle.getOrDefault(vehicleId, 0);
            if (count != 1) {
                throw new IllegalArgumentException(
                        "Vehicle " + vehicleId + " must have exactly one active access link, found " + count + "."
                );
            }
        }
    }

    private NodeType parseNodeType(String value, String candidateId) {
        requireText(value, "type of " + candidateId);
        try {
            return NodeType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported type for " + candidateId + ": " + value, ex);
        }
    }

    private static <T> T requireNonNull(T value, String label) {
        if (value == null) { throw new IllegalArgumentException(label + " must not be null."); }
        return value;
    }
    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(label + " must not be null or blank."); }
    }
    private static void requireUnique(String value, String label, Set<String> ids) {
        requireText(value, label);
        if (!ids.add(value)) { throw new IllegalArgumentException("Duplicated " + label + ": " + value); }
    }
    private static void requireReference(Set<String> ids, String value, String prefix) {
        requireText(value, "reference");
        if (!ids.contains(value)) { throw new IllegalArgumentException(prefix + value); }
    }
    private static void requireList(List<?> list, String label) {
        if (list == null) { throw new IllegalArgumentException("snapshot." + label + " must not be null."); }
    }
    private static void requireFinite(Double value, String field, String owner) {
        if (value == null || !Double.isFinite(value)) { throw new IllegalArgumentException(field + " of " + owner + " must be finite."); }
    }
    private static void requireNonNegative(Double value, String field, String owner) {
        requireFinite(value, field, owner);
        if (value < 0.0) { throw new IllegalArgumentException(field + " of " + owner + " must be >= 0."); }
    }
    private static void requirePositive(Double value, String field, String owner) {
        requireFinite(value, field, owner);
        if (value <= 0.0) { throw new IllegalArgumentException(field + " of " + owner + " must be > 0."); }
    }
    private static void optionalFinite(Double value, String field, String owner) {
        if (value != null) { requireFinite(value, field, owner); }
    }
    private static void optionalPositive(Double value, String field, String owner) {
        if (value != null) { requirePositive(value, field, owner); }
    }
}
