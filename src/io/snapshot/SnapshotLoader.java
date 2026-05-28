package io.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Carica uno snapshot statico del sistema da file JSON.
 *
 * Il loader converte il formato JSON in oggetti del modello interno.
 * Non valida la correttezza dello scenario: la validazione è responsabilità
 * di SnapshotValidator.
 */
public final class SnapshotLoader {

    private final ObjectMapper objectMapper;

    /**
     * Costruisce un loader basato su Jackson.
     */
    public SnapshotLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Carica uno snapshot da file JSON.
     *
     * @param filePath percorso del file JSON
     * @return snapshot convertito nel modello interno
     * @throws IOException se il file non è leggibile o il JSON non è valido
     */
    public SystemSnapshot load(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be null or blank.");
        }

        SnapshotDto snapshotDto = objectMapper.readValue(
                new File(filePath),
                SnapshotDto.class
        );

        return toSystemSnapshot(snapshotDto);
    }

    /**
     * Converte il DTO principale in SystemSnapshot.
     */
    private SystemSnapshot toSystemSnapshot(SnapshotDto dto) {
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
     * Converte i veicoli JSON in VehicleSnapshot.
     */
    private List<VehicleSnapshot> toVehicles(List<VehicleDto> vehicleDtos) {
        List<VehicleSnapshot> vehicles = new ArrayList<>();

        if (vehicleDtos == null) {
            return vehicles;
        }

        for (VehicleDto dto : vehicleDtos) {
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
     * Converte i task JSON in TaskInstance.
     */
    private List<TaskInstance> toTasks(List<TaskDto> taskDtos) {
        List<TaskInstance> tasks = new ArrayList<>();

        if (taskDtos == null) {
            return tasks;
        }

        for (TaskDto dto : taskDtos) {
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
     * Converte i candidati JSON in NodeCandidate source-aware.
     *
     * Il JSON non contiene più coverageTimeSeconds.
     * Per EDGE/RSU deve contenere nodeX, nodeY e coverageRadiusMeters.
     * Per LOCAL, CLOUD e VEHICLE questi campi possono essere assenti.
     */
    private List<NodeCandidate> toCandidateNodes(List<NodeDto> nodeDtos) {
        List<NodeCandidate> candidateNodes = new ArrayList<>();

        if (nodeDtos == null) {
            return candidateNodes;
        }

        for (NodeDto dto : nodeDtos) {
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
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("candidate type must not be null or blank.");
        }

        return NodeType.valueOf(value.trim().toUpperCase());
    }

    private static final class SnapshotDto {
        public String snapshotId;
        public double timeSeconds;
        public List<VehicleDto> vehicles;
        public List<TaskDto> tasks;
        public List<NodeDto> candidateNodes;
    }

    private static final class VehicleDto {
        public String vehicleId;
        public double x;
        public double y;
        public double speed;
        public double localCpu;
    }

    private static final class TaskDto {
        public String taskId;
        public String sourceVehicleId;
        public double inputSizeBits;
        public double outputSizeBits;
        public double cpuCycles;
        public double deadlineSeconds;
    }

    private static final class NodeDto {
        public String candidateId;
        public String sourceVehicleId;
        public String executionNodeId;
        public String type;

        public double availableCpu;
        public double availableBandwidth;
        public double baseLatencySeconds;

        public Double nodeX;
        public Double nodeY;
        public Double coverageRadiusMeters;
    }
}