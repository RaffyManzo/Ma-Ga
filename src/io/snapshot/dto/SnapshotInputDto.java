package io.snapshot.dto;

import java.util.List;

/**
 * DTO grezzo dello snapshot letto da JSON o da un adapter esterno.
 *
 * <p>Non contiene logica di dominio: rappresenta solo la forma dell'input.
 * La validazione avviene in {@code SnapshotValidator} prima del mapping verso
 * il modello interno.</p>
 */
public final class SnapshotInputDto {

    public String snapshotId;
    public Double timeSeconds;
    public List<VehicleInputDto> vehicles;
    public List<TaskInputDto> tasks;
    public List<NodeCandidateInputDto> candidateNodes;
}
