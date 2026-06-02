package io.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import validation.snapshot.LocalCandidateInvariantValidator;
import validation.snapshot.SnapshotValidator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Carica uno snapshot statico del sistema da file JSON. */
public final class SnapshotLoader {
    private final ObjectMapper objectMapper;
    private final SnapshotValidator snapshotValidator;
    private final LocalCandidateInvariantValidator localCandidateInvariantValidator;

    public SnapshotLoader() {
        this(new SnapshotValidator(), new LocalCandidateInvariantValidator());
    }

    public SnapshotLoader(SnapshotValidator snapshotValidator) {
        this(snapshotValidator, new LocalCandidateInvariantValidator());
    }

    public SnapshotLoader(
            SnapshotValidator snapshotValidator,
            LocalCandidateInvariantValidator localCandidateInvariantValidator
    ) {
        if (snapshotValidator == null) {
            throw new IllegalArgumentException("snapshotValidator must not be null.");
        }
        if (localCandidateInvariantValidator == null) {
            throw new IllegalArgumentException("localCandidateInvariantValidator must not be null.");
        }
        this.objectMapper = new ObjectMapper();
        this.snapshotValidator = snapshotValidator;
        this.localCandidateInvariantValidator = localCandidateInvariantValidator;
    }

    public SystemSnapshot load(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be null or blank.");
        }
        SnapshotInputDto dto = objectMapper.readValue(new File(filePath), SnapshotInputDto.class);
        snapshotValidator.validate(dto);
        SystemSnapshot snapshot = toSystemSnapshot(dto);
        snapshotValidator.validate(snapshot);
        localCandidateInvariantValidator.validate(snapshot);
        return snapshot;
    }

    private SystemSnapshot toSystemSnapshot(SnapshotInputDto dto) {
        return new SystemSnapshot(
                dto.snapshotId,
                dto.timeSeconds,
                toVehicles(dto.vehicles),
                toTasks(dto.tasks),
                toCandidateNodes(dto.candidateNodes),
                toAccessGateways(dto.accessGateways),
                toAccessLinks(dto.accessLinks)
        );
    }

    private List<VehicleSnapshot> toVehicles(List<VehicleInputDto> dtos) {
        List<VehicleSnapshot> result = new ArrayList<>();
        for (VehicleInputDto dto : dtos) {
            result.add(new VehicleSnapshot(dto.vehicleId, dto.x, dto.y, dto.speed, dto.localCpu));
        }
        return result;
    }

    private List<TaskInstance> toTasks(List<TaskInputDto> dtos) {
        List<TaskInstance> result = new ArrayList<>();
        for (TaskInputDto dto : dtos) {
            result.add(new TaskInstance(
                    dto.taskId, dto.sourceVehicleId,
                    dto.inputSizeBits, dto.outputSizeBits,
                    dto.cpuCycles, dto.deadlineSeconds
            ));
        }
        return result;
    }

    private List<NodeCandidate> toCandidateNodes(List<NodeCandidateInputDto> dtos) {
        List<NodeCandidate> result = new ArrayList<>();
        for (NodeCandidateInputDto dto : dtos) {
            result.add(new NodeCandidate(
                    dto.candidateId,
                    dto.sourceVehicleId,
                    dto.executionNodeId,
                    parseNodeType(dto.type),
                    dto.availableCpu,
                    dto.availableBandwidth,
                    dto.baseLatencySeconds,
                    dto.nodeX,
                    dto.nodeY,
                    dto.coverageRadiusMeters
            ));
        }
        return result;
    }

    private List<AccessGatewaySnapshot> toAccessGateways(List<AccessGatewayInputDto> dtos) {
        List<AccessGatewaySnapshot> result = new ArrayList<>();
        for (AccessGatewayInputDto dto : dtos) {
            result.add(new AccessGatewaySnapshot(
                    dto.gatewayId,
                    dto.gatewayType,
                    dto.x,
                    dto.y,
                    dto.coverageRadiusMeters
            ));
        }
        return result;
    }

    private List<AccessLinkSnapshot> toAccessLinks(List<AccessLinkInputDto> dtos) {
        List<AccessLinkSnapshot> result = new ArrayList<>();
        for (AccessLinkInputDto dto : dtos) {
            result.add(new AccessLinkSnapshot(
                    dto.accessLinkId,
                    dto.vehicleId,
                    dto.gatewayId,
                    dto.active,
                    dto.available
            ));
        }
        return result;
    }

    private NodeType parseNodeType(String value) {
        return NodeType.valueOf(value.trim().toUpperCase());
    }
}
