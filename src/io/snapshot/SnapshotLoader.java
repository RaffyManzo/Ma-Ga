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
 * Il nuovo formato dei candidati è source-aware.
 * Ogni NodeCandidate indica:
 *
 * - per quale veicolo sorgente è valido;
 * - quale nodo fisico esegue il task;
 * - quali metriche di link e copertura sono stimate per quella relazione.
 */
public final class SnapshotLoader {

    private final ObjectMapper objectMapper;

    /**
     * Costruisce il loader.
     */
    public SnapshotLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Carica uno snapshot da file JSON.
     *
     * Parametri in ingresso:
     * - filePath: percorso del file JSON.
     *
     * Output:
     * - SystemSnapshot costruito dal file.
     *
     * Eccezioni:
     * - IOException se il file non è leggibile o il JSON non è valido.
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
            vehicles.add(new VehicleSnapshot(
                    dto.vehicleId,
                    dto.x,
                    dto.y,
                    dto.speed,
                    dto.localCpu
            ));
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
            tasks.add(new TaskInstance(
                    dto.taskId,
                    dto.sourceVehicleId,
                    dto.inputSizeBits,
                    dto.outputSizeBits,
                    dto.cpuCycles,
                    dto.deadlineSeconds
            ));
        }

        return tasks;
    }

    /**
     * Converte i candidati JSON in NodeCandidate source-aware.
     */
    private List<NodeCandidate> toCandidateNodes(List<NodeDto> nodeDtos) {
        List<NodeCandidate> candidateNodes = new ArrayList<>();

        if (nodeDtos == null) {
            return candidateNodes;
        }

        for (NodeDto dto : nodeDtos) {
            candidateNodes.add(new NodeCandidate(
                    dto.candidateId,
                    dto.sourceVehicleId,
                    dto.executionNodeId,
                    NodeType.valueOf(dto.type.toUpperCase()),
                    dto.availableCpu,
                    dto.availableBandwidth,
                    dto.baseLatencySeconds,
                    dto.coverageTimeSeconds
            ));
        }

        return candidateNodes;
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
        public double coverageTimeSeconds;
    }
}

