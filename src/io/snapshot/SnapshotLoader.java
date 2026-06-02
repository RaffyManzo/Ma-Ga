package io.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.snapshot.dto.AccessGatewayInputDto;
import io.snapshot.dto.AccessLinkInputDto;
import io.snapshot.dto.BandwidthPoolInputDto;
import io.snapshot.dto.NodeCandidateInputDto;
import io.snapshot.dto.SnapshotInputDto;
import io.snapshot.dto.TaskInputDto;
import io.snapshot.dto.VehicleInputDto;
import model.bandwidth.BandwidthPoolType;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;
import validation.snapshot.LocalCandidateInvariantValidator;
import validation.snapshot.SnapshotValidator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Carica uno snapshot gateway-aware con pool radio globali. */
public final class SnapshotLoader {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SnapshotValidator snapshotValidator;
    private final LocalCandidateInvariantValidator localCandidateInvariantValidator;

    public SnapshotLoader() { this(new SnapshotValidator(), new LocalCandidateInvariantValidator()); }
    public SnapshotLoader(SnapshotValidator snapshotValidator) { this(snapshotValidator, new LocalCandidateInvariantValidator()); }
    public SnapshotLoader(SnapshotValidator snapshotValidator, LocalCandidateInvariantValidator localCandidateInvariantValidator) {
        if (snapshotValidator == null || localCandidateInvariantValidator == null) {
            throw new IllegalArgumentException("validators must not be null.");
        }
        this.snapshotValidator = snapshotValidator;
        this.localCandidateInvariantValidator = localCandidateInvariantValidator;
    }

    public SystemSnapshot load(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) { throw new IllegalArgumentException("filePath must not be null or blank."); }
        SnapshotInputDto dto = objectMapper.readValue(new File(filePath), SnapshotInputDto.class);
        SystemSnapshot snapshot = toSystemSnapshot(dto);
        snapshotValidator.validate(snapshot);
        localCandidateInvariantValidator.validate(snapshot);
        return snapshot;
    }

    private SystemSnapshot toSystemSnapshot(SnapshotInputDto dto) {
        if (dto == null) { throw new IllegalArgumentException("snapshot JSON is empty or invalid."); }
        return new SystemSnapshot(dto.snapshotId, dto.timeSeconds,
                toVehicles(dto.vehicles), toTasks(dto.tasks), toCandidateNodes(dto.candidateNodes),
                toAccessGateways(dto.accessGateways), toAccessLinks(dto.accessLinks), toBandwidthPools(dto.bandwidthPools));
    }

    private List<VehicleSnapshot> toVehicles(List<VehicleInputDto> dtos) {
        List<VehicleSnapshot> result = new ArrayList<>();
        if (dtos != null) for (VehicleInputDto d : dtos) result.add(new VehicleSnapshot(d.vehicleId, d.x, d.y, d.speed, d.localCpu));
        return result;
    }
    private List<TaskInstance> toTasks(List<TaskInputDto> dtos) {
        List<TaskInstance> result = new ArrayList<>();
        if (dtos != null) for (TaskInputDto d : dtos) result.add(new TaskInstance(d.taskId, d.sourceVehicleId, d.inputSizeBits, d.outputSizeBits, d.cpuCycles, d.deadlineSeconds));
        return result;
    }
    private List<NodeCandidate> toCandidateNodes(List<NodeCandidateInputDto> dtos) {
        List<NodeCandidate> result = new ArrayList<>();
        if (dtos != null) for (NodeCandidateInputDto d : dtos) result.add(new NodeCandidate(d.candidateId, d.sourceVehicleId, d.executionNodeId,
                NodeType.valueOf(d.type.trim().toUpperCase()), d.availableCpu, d.availableBandwidth, d.baseLatencySeconds,
                d.nodeX, d.nodeY, d.coverageRadiusMeters));
        return result;
    }
    private List<AccessGatewaySnapshot> toAccessGateways(List<AccessGatewayInputDto> dtos) {
        List<AccessGatewaySnapshot> result = new ArrayList<>();
        if (dtos != null) for (AccessGatewayInputDto d : dtos) result.add(new AccessGatewaySnapshot(d.gatewayId, d.gatewayType, d.x, d.y, d.coverageRadiusMeters));
        return result;
    }
    private List<AccessLinkSnapshot> toAccessLinks(List<AccessLinkInputDto> dtos) {
        List<AccessLinkSnapshot> result = new ArrayList<>();
        if (dtos != null) for (AccessLinkInputDto d : dtos) result.add(new AccessLinkSnapshot(d.accessLinkId, d.vehicleId, d.gatewayId, d.active, d.available));
        return result;
    }
    private List<BandwidthPoolSnapshot> toBandwidthPools(List<BandwidthPoolInputDto> dtos) {
        List<BandwidthPoolSnapshot> result = new ArrayList<>();
        if (dtos != null) for (BandwidthPoolInputDto d : dtos) result.add(new BandwidthPoolSnapshot(d.poolId,
                BandwidthPoolType.valueOf(d.poolType.trim().toUpperCase()), d.availableBandwidth));
        return result;
    }
}
