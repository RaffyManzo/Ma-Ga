package io.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.snapshot.dto.NodeCandidateInputDto;
import io.snapshot.dto.SnapshotInputDto;
import io.snapshot.dto.TaskInputDto;
import io.snapshot.dto.VehicleInputDto;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;
import validation.snapshot.LocalCandidateInvariantValidator;
import validation.snapshot.SnapshotValidator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Carica uno snapshot statico del sistema da file JSON.
 *
 * <p>Il loader separa quattro passaggi: lettura del JSON in DTO grezzi,
 * validazione centralizzata in {@link SnapshotValidator}, mapping verso il
 * modello interno e verifica dell'invariante locale necessario al repair.</p>
 */
public final class SnapshotLoader {
    private final ObjectMapper objectMapper;
    private final SnapshotValidator snapshotValidator;
    private final LocalCandidateInvariantValidator localCandidateInvariantValidator;

    /**
     * Costruisce un loader basato su Jackson e sui validator standard.
     */
    public SnapshotLoader() {
        this(new SnapshotValidator(), new LocalCandidateInvariantValidator());
    }

    /**
     * Costruisce un loader mantenendo compatibilità con i chiamanti esistenti.
     *
     * @param snapshotValidator validator da usare prima del mapping
     */
    public SnapshotLoader(SnapshotValidator snapshotValidator) {
        this(snapshotValidator, new LocalCandidateInvariantValidator());
    }

    /**
     * Costruisce un loader con validator espliciti.
     *
     * @param snapshotValidator              validator dei DTO grezzi
     * @param localCandidateInvariantValidator validator del fallback locale
     */
    public SnapshotLoader(
            SnapshotValidator snapshotValidator,
            LocalCandidateInvariantValidator localCandidateInvariantValidator
    ) {
        if (snapshotValidator == null) {
            throw new IllegalArgumentException("snapshotValidator must not be null.");
        }
        if (localCandidateInvariantValidator == null) {
            throw new IllegalArgumentException(
                    "localCandidateInvariantValidator must not be null."
            );
        }

        this.objectMapper = new ObjectMapper();
        this.snapshotValidator = snapshotValidator;
        this.localCandidateInvariantValidator = localCandidateInvariantValidator;
    }

    /**
     * Carica uno snapshot da file JSON.
     *
     * @param filePath percorso del file JSON
     * @return snapshot convertito nel modello interno
     * @throws IOException se il file non e' leggibile o il JSON non e' valido
     */
    public SystemSnapshot load(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be null or blank.");
        }

        SnapshotInputDto snapshotDto = objectMapper.readValue(
                new File(filePath),
                SnapshotInputDto.class
        );

        snapshotValidator.validate(snapshotDto);
        SystemSnapshot snapshot = toSystemSnapshot(snapshotDto);
        localCandidateInvariantValidator.validate(snapshot);
        return snapshot;
    }

    /**
     * Converte il DTO principale in {@link SystemSnapshot}.
     */
    private SystemSnapshot toSystemSnapshot(SnapshotInputDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("snapshot JSON is empty or invalid.");
        }

        return new SystemSnapshot(
                dto.snapshotId,
                dto.timeSeconds,
                toVehicles(dto.vehicles),
                toTasks(dto.tasks),
                toCandidateNodes(dto.candidateNodes)
        );
    }

    /**
     * Converte i veicoli JSON in {@link VehicleSnapshot}.
     */
    private List<VehicleSnapshot> toVehicles(List<VehicleInputDto> vehicleDtos) {
        List<VehicleSnapshot> vehicles = new ArrayList<>();
        if (vehicleDtos == null) {
            return vehicles;
        }

        for (VehicleInputDto dto : vehicleDtos) {
            vehicles.add(
                    new VehicleSnapshot(
                            dto.vehicleId,
                            dto.x,
                            dto.y,
                            dto.speed,
                            dto.localCpu
                    )
            );
        }
        return vehicles;
    }

    /**
     * Converte i task JSON in {@link TaskInstance}.
     */
    private List<TaskInstance> toTasks(List<TaskInputDto> taskDtos) {
        List<TaskInstance> tasks = new ArrayList<>();
        if (taskDtos == null) {
            return tasks;
        }

        for (TaskInputDto dto : taskDtos) {
            tasks.add(
                    new TaskInstance(
                            dto.taskId,
                            dto.sourceVehicleId,
                            dto.inputSizeBits,
                            dto.outputSizeBits,
                            dto.cpuCycles,
                            dto.deadlineSeconds
                    )
            );
        }
        return tasks;
    }

    /**
     * Converte i candidati JSON in {@link NodeCandidate}.
     */
    private List<NodeCandidate> toCandidateNodes(List<NodeCandidateInputDto> nodeDtos) {
        List<NodeCandidate> candidateNodes = new ArrayList<>();
        if (nodeDtos == null) {
            return candidateNodes;
        }

        for (NodeCandidateInputDto dto : nodeDtos) {
            candidateNodes.add(
                    new NodeCandidate(
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
                    )
            );
        }
        return candidateNodes;
    }

    /**
     * Converte il tipo del nodo da stringa JSON a enum.
     */
    private NodeType parseNodeType(String value) {
        return NodeType.valueOf(value.trim().toUpperCase());
    }
}
