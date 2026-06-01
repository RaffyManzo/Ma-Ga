package io.snapshot.dto;

/**
 * DTO grezzo di un candidato di esecuzione nello snapshot di input.
 *
 * <p>Il campo JSON {@code baseLatencySeconds} viene mantenuto per compatibilità
 * con gli snapshot già prodotti. Semanticamente rappresenta {@code tau_n}:
 * il ritardo end-to-end aggregato del percorso remoto, conteggiato una sola
 * volta per ogni scelta remota.</p>
 */
public final class NodeCandidateInputDto {
    public String candidateId;
    public String sourceVehicleId;
    public String executionNodeId;
    public String type;
    public Double availableCpu;
    public Double availableBandwidth;

    /**
     * Nome storico del campo JSON. Semanticamente rappresenta {@code tau_n}.
     */
    public Double baseLatencySeconds;

    public Double nodeX;
    public Double nodeY;
    public Double coverageRadiusMeters;
}
