package io.snapshot.dto;

import java.util.List;

/** DTO grezzo dello snapshot letto da JSON o da un adapter esterno. */
public final class SnapshotInputDto {
    public String snapshotId;
    public Double timeSeconds;
    public List<VehicleInputDto> vehicles;
    public List<TaskInputDto> tasks;
    public List<NodeCandidateInputDto> candidateNodes;
    public List<AccessGatewayInputDto> accessGateways;
    public List<AccessLinkInputDto> accessLinks;
    public List<BandwidthPoolInputDto> bandwidthPools;
}
