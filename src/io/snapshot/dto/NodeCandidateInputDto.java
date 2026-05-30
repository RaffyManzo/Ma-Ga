package io.snapshot.dto;

/**
 * DTO grezzo di un candidato di esecuzione nello snapshot di input.
 */
public final class NodeCandidateInputDto {

    public String candidateId;
    public String sourceVehicleId;
    public String executionNodeId;
    public String type;

    public Double availableCpu;
    public Double availableBandwidth;
    public Double baseLatencySeconds;

    public Double nodeX;
    public Double nodeY;
    public Double coverageRadiusMeters;
}
