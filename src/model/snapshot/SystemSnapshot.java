package model.snapshot;

import model.node.NodeCandidate;

import java.util.List;

/**
 * Fotografia dello stato del sistema in un istante simulato.
 *
 * <p>Contiene veicoli, task attivi e candidati di esecuzione disponibili per
 * quello specifico tempo. La classe resta mutabile per supportare la
 * deserializzazione e la composizione degli snapshot filtrati.</p>
 */
public class SystemSnapshot {

    private String snapshotId;
    private double timeSeconds;
    private List<VehicleSnapshot> vehicles;
    private List<TaskInstance> tasks;
    private List<NodeCandidate> candidateNodes;

    public SystemSnapshot() {
    }

    /**
     * Crea uno snapshot completo.
     *
     * @param snapshotId identificativo dello snapshot
     * @param timeSeconds tempo simulato associato allo snapshot
     * @param vehicles veicoli presenti nello scenario
     * @param tasks task attivi nello scenario
     * @param candidateNodes candidati di esecuzione disponibili
     */
    public SystemSnapshot(
            String snapshotId,
            double timeSeconds,
            List<VehicleSnapshot> vehicles,
            List<TaskInstance> tasks,
            List<NodeCandidate> candidateNodes
    ) {
        this.snapshotId = snapshotId;
        this.timeSeconds = timeSeconds;
        this.vehicles = vehicles;
        this.tasks = tasks;
        this.candidateNodes = candidateNodes;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public double getTimeSeconds() {
        return timeSeconds;
    }

    public void setTimeSeconds(double timeSeconds) {
        this.timeSeconds = timeSeconds;
    }

    public List<VehicleSnapshot> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<VehicleSnapshot> vehicles) {
        this.vehicles = vehicles;
    }

    public List<TaskInstance> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskInstance> tasks) {
        this.tasks = tasks;
    }

    public List<NodeCandidate> getCandidateNodes() {
        return candidateNodes;
    }

    public void setCandidateNodes(List<NodeCandidate> candidateNodes) {
        this.candidateNodes = candidateNodes;
    }

    @Override
    public String toString() {
        return "SystemSnapshot{" +
                "snapshotId='" + snapshotId + '\'' +
                ", timeSeconds=" + timeSeconds +
                ", vehicles=" + vehicles +
                ", tasks=" + tasks +
                ", candidateNodes=" + candidateNodes +
                '}';
    }
}


