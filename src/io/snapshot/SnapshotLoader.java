package io.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.snapshot.dto.*;
import model.bandwidth.BandwidthPoolType;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.*;
import validation.snapshot.LocalCandidateInvariantValidator;
import validation.snapshot.SnapshotValidator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Carica snapshot con pool globali, gateway e V2V. */
public final class SnapshotLoader {
    private final ObjectMapper objectMapper=new ObjectMapper(); private final SnapshotValidator validator; private final LocalCandidateInvariantValidator localValidator;
    public SnapshotLoader(){this(new SnapshotValidator(),new LocalCandidateInvariantValidator());} public SnapshotLoader(SnapshotValidator v){this(v,new LocalCandidateInvariantValidator());} public SnapshotLoader(SnapshotValidator v,LocalCandidateInvariantValidator l){if(v==null||l==null)throw new IllegalArgumentException("validators must not be null.");validator=v;localValidator=l;}
    public SystemSnapshot load(String path)throws IOException{if(path==null||path.isBlank())throw new IllegalArgumentException("filePath must not be null or blank.");SnapshotInputDto d=objectMapper.readValue(new File(path),SnapshotInputDto.class);SystemSnapshot s=map(d);validator.validate(s);localValidator.validate(s);return s;}
    private SystemSnapshot map(SnapshotInputDto d){if(d==null)throw new IllegalArgumentException("snapshot JSON is empty or invalid.");return new SystemSnapshot(d.snapshotId,d.timeSeconds,vehicles(d.vehicles),tasks(d.tasks),candidates(d.candidateNodes),gateways(d.accessGateways),links(d.accessLinks),pools(d.bandwidthPools));}
    private List<VehicleSnapshot> vehicles(List<VehicleInputDto>x){List<VehicleSnapshot>r=new ArrayList<>();if(x!=null)for(VehicleInputDto d:x)r.add(new VehicleSnapshot(d.vehicleId,d.x,d.y,d.speed,d.localCpu));return r;}
    private List<TaskInstance> tasks(List<TaskInputDto>x){List<TaskInstance>r=new ArrayList<>();if(x!=null)for(TaskInputDto d:x)r.add(new TaskInstance(d.taskId,d.sourceVehicleId,d.inputSizeBits,d.outputSizeBits,d.cpuCycles,d.deadlineSeconds));return r;}
    private List<NodeCandidate> candidates(List<NodeCandidateInputDto>x){List<NodeCandidate>r=new ArrayList<>();if(x!=null)for(NodeCandidateInputDto d:x)r.add(new NodeCandidate(d.candidateId,d.sourceVehicleId,d.executionNodeId,NodeType.valueOf(d.type.trim().toUpperCase()),d.availableCpu,d.availableBandwidth,d.baseLatencySeconds,d.nodeX,d.nodeY,d.coverageRadiusMeters,d.bandwidthPoolId));return r;}
    private List<AccessGatewaySnapshot> gateways(List<AccessGatewayInputDto>x){List<AccessGatewaySnapshot>r=new ArrayList<>();if(x!=null)for(AccessGatewayInputDto d:x)r.add(new AccessGatewaySnapshot(d.gatewayId,d.gatewayType,d.x,d.y,d.coverageRadiusMeters,d.bandwidthPoolId));return r;}
    private List<AccessLinkSnapshot> links(List<AccessLinkInputDto>x){List<AccessLinkSnapshot>r=new ArrayList<>();if(x!=null)for(AccessLinkInputDto d:x)r.add(new AccessLinkSnapshot(d.accessLinkId,d.vehicleId,d.gatewayId,d.active,d.available));return r;}
    private List<BandwidthPoolSnapshot> pools(List<BandwidthPoolInputDto>x){List<BandwidthPoolSnapshot>r=new ArrayList<>();if(x!=null)for(BandwidthPoolInputDto d:x)r.add(new BandwidthPoolSnapshot(d.poolId,BandwidthPoolType.valueOf(d.poolType.trim().toUpperCase()),d.availableBandwidth));return r;}
}
