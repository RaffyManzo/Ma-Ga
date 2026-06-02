package validation.snapshot;

import model.bandwidth.BandwidthPoolType;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validazione strutturale dello snapshot nel livello 18.2. */
public final class SnapshotValidator {
    public void validate(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        requireText(snapshot.getSnapshotId(), "snapshotId");
        List<VehicleSnapshot> vehicles = requireList(snapshot.getVehicles(), "vehicles");
        List<TaskInstance> tasks = requireList(snapshot.getTasks(), "tasks");
        List<NodeCandidate> candidates = requireList(snapshot.getCandidateNodes(), "candidateNodes");
        List<AccessGatewaySnapshot> gateways = requireList(snapshot.getAccessGateways(), "accessGateways");
        List<AccessLinkSnapshot> links = requireList(snapshot.getAccessLinks(), "accessLinks");
        List<BandwidthPoolSnapshot> pools = requireList(snapshot.getBandwidthPools(), "bandwidthPools");

        Set<String> vehicleIds = uniqueVehicleIds(vehicles);
        uniqueTaskIds(tasks);
        uniqueCandidateIds(candidates);
        Set<String> gatewayIds = uniqueGatewayIds(gateways);
        Set<String> poolIds = uniquePoolIds(pools);
        validateSingleGlobalPool(pools);
        validateAccessLinks(links, vehicleIds, gatewayIds);
        validateTasks(tasks, vehicleIds, candidates);
        validateCandidates(candidates, vehicleIds);
        if (poolIds.isEmpty()) { throw new IllegalArgumentException("At least one bandwidth pool is required."); }
    }

    private void validateSingleGlobalPool(List<BandwidthPoolSnapshot> pools) {
        long count = pools.stream().filter(p -> p.getPoolType() == BandwidthPoolType.GLOBAL).count();
        if (count != 1 || pools.size() != 1) {
            throw new IllegalArgumentException("Level 18.2 requires exactly one GLOBAL bandwidth pool.");
        }
    }
    private Set<String> uniqueVehicleIds(List<VehicleSnapshot> values) { Set<String> s=new HashSet<>(); for (VehicleSnapshot v:values) addUnique(s,v.getVehicleId(),"vehicleId"); return s; }
    private void uniqueTaskIds(List<TaskInstance> values) { Set<String>s=new HashSet<>(); for(TaskInstance v:values)addUnique(s,v.getTaskId(),"taskId"); }
    private void uniqueCandidateIds(List<NodeCandidate> values) { Set<String>s=new HashSet<>(); for(NodeCandidate v:values)addUnique(s,v.getCandidateId(),"candidateId"); }
    private Set<String> uniqueGatewayIds(List<AccessGatewaySnapshot> values) { Set<String>s=new HashSet<>(); for(AccessGatewaySnapshot v:values)addUnique(s,v.getGatewayId(),"gatewayId"); return s; }
    private Set<String> uniquePoolIds(List<BandwidthPoolSnapshot> values) { Set<String>s=new HashSet<>(); for(BandwidthPoolSnapshot v:values)addUnique(s,v.getPoolId(),"poolId"); return s; }
    private void validateAccessLinks(List<AccessLinkSnapshot> links, Set<String> vehicles, Set<String> gateways) {
        Set<String> ids=new HashSet<>(); Map<String,Integer> active=new HashMap<>();
        for(AccessLinkSnapshot l:links){ addUnique(ids,l.getAccessLinkId(),"accessLinkId"); requireRef(vehicles,l.getVehicleId(),"vehicle"); requireRef(gateways,l.getGatewayId(),"gateway"); if(l.isActive())active.merge(l.getVehicleId(),1,Integer::sum); }
        for(String vehicle:vehicles) if(active.getOrDefault(vehicle,0)!=1) throw new IllegalArgumentException("Vehicle "+vehicle+" must have exactly one active access link.");
    }
    private void validateTasks(List<TaskInstance> tasks, Set<String> vehicles, List<NodeCandidate> candidates) {
        for(TaskInstance t:tasks){ requireRef(vehicles,t.getSourceVehicleId(),"task source vehicle"); boolean local=false; for(NodeCandidate c:candidates) if(c.isValidForSourceVehicle(t.getSourceVehicleId())&&c.getType()==NodeType.LOCAL)local=true; if(!local)throw new IllegalArgumentException("Task "+t.getTaskId()+" has no LOCAL candidate."); }
    }
    private void validateCandidates(List<NodeCandidate> candidates, Set<String> vehicles) {
        for(NodeCandidate c:candidates){ requireRef(vehicles,c.getSourceVehicleId(),"candidate source vehicle"); if(c.getType()==NodeType.LOCAL&&!c.getSourceVehicleId().equals(c.getExecutionNodeId()))throw new IllegalArgumentException("Invalid LOCAL candidate: "+c.getCandidateId()); if(c.getType()==NodeType.VEHICLE&&c.getSourceVehicleId().equals(c.getExecutionNodeId()))throw new IllegalArgumentException("Invalid VEHICLE candidate: "+c.getCandidateId()); }
    }
    private <T> List<T> requireList(List<T> list,String name){ if(list==null)throw new IllegalArgumentException("snapshot."+name+" must not be null."); return list; }
    private void addUnique(Set<String>s,String value,String field){ requireText(value,field); if(!s.add(value))throw new IllegalArgumentException("Duplicated "+field+": "+value); }
    private void requireRef(Set<String>s,String value,String label){ requireText(value,label); if(!s.contains(value))throw new IllegalArgumentException("Missing "+label+": "+value); }
    private void requireText(String value,String field){ if(value==null||value.isBlank())throw new IllegalArgumentException(field+" must not be null or blank."); }
}
