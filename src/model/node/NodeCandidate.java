package model.node;

import java.util.Objects;

/** Possibile opzione source-aware di esecuzione per un task. */
public final class NodeCandidate {
    private final String candidateId;
    private final String sourceVehicleId;
    private final String executionNodeId;
    private final NodeType type;
    private final double availableCpu;
    private final double availableBandwidth;
    private final double propagationDelaySeconds;
    private final Double nodeX;
    private final Double nodeY;
    private final Double coverageRadiusMeters;
    private final String bandwidthPoolId;

    /** Costruttore storico. */
    public NodeCandidate(String candidateId,String sourceVehicleId,String executionNodeId,NodeType type,double availableCpu,double availableBandwidth,double propagationDelaySeconds,Double nodeX,Double nodeY,Double coverageRadiusMeters) {
        this(candidateId,sourceVehicleId,executionNodeId,type,availableCpu,availableBandwidth,propagationDelaySeconds,nodeX,nodeY,coverageRadiusMeters,null);
    }

    /** Costruttore completo. bandwidthPoolId è usato soprattutto per V2V diretto. */
    public NodeCandidate(String candidateId,String sourceVehicleId,String executionNodeId,NodeType type,double availableCpu,double availableBandwidth,double propagationDelaySeconds,Double nodeX,Double nodeY,Double coverageRadiusMeters,String bandwidthPoolId) {
        this.candidateId=requireText(candidateId,"candidateId"); this.sourceVehicleId=requireText(sourceVehicleId,"sourceVehicleId"); this.executionNodeId=requireText(executionNodeId,"executionNodeId"); this.type=Objects.requireNonNull(type,"type must not be null.");
        this.availableCpu=finiteNonNegative(availableCpu,"availableCpu"); this.availableBandwidth=finiteNonNegative(availableBandwidth,"availableBandwidth"); this.propagationDelaySeconds=finiteNonNegative(propagationDelaySeconds,"propagationDelaySeconds");
        this.nodeX=optionalFinite(nodeX,"nodeX"); this.nodeY=optionalFinite(nodeY,"nodeY"); this.coverageRadiusMeters=optionalPositive(coverageRadiusMeters,"coverageRadiusMeters"); this.bandwidthPoolId=normalizeOptional(bandwidthPoolId);
    }
    public String getCandidateId(){return candidateId;} public String getNodeId(){return candidateId;} public String getSourceVehicleId(){return sourceVehicleId;} public String getExecutionNodeId(){return executionNodeId;} public NodeType getType(){return type;} public double getAvailableCpu(){return availableCpu;} public double getAvailableBandwidth(){return availableBandwidth;} public double getPropagationDelaySeconds(){return propagationDelaySeconds;} @Deprecated public double getBaseLatencySeconds(){return propagationDelaySeconds;} public Double getNodeX(){return nodeX;} public Double getNodeY(){return nodeY;} public Double getCoverageRadiusMeters(){return coverageRadiusMeters;} public String getBandwidthPoolId(){return bandwidthPoolId;} public boolean isLocal(){return type==NodeType.LOCAL;} public boolean isVehicle(){return type==NodeType.VEHICLE;} public boolean isEdge(){return type==NodeType.EDGE;} public boolean isCloud(){return type==NodeType.CLOUD;} public boolean isRemote(){return type!=NodeType.LOCAL;} public boolean isInfrastructureCandidate(){return type==NodeType.EDGE;} public boolean hasCoverageGeometry(){return nodeX!=null&&nodeY!=null&&coverageRadiusMeters!=null;} public boolean isValidForSourceVehicle(String id){return sourceVehicleId.equals(id);}
    private static String normalizeOptional(String v){return v==null||v.isBlank()?null:v;} private static String requireText(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" must not be null or blank.");return v;} private static double finiteNonNegative(double v,String f){if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException(f+" must be finite and >= 0.");return v;} private static Double optionalFinite(Double v,String f){if(v!=null&&!Double.isFinite(v))throw new IllegalArgumentException(f+" must be finite.");return v;} private static Double optionalPositive(Double v,String f){if(v!=null&&(!Double.isFinite(v)||v<=0))throw new IllegalArgumentException(f+" must be finite and > 0.");return v;}
}
