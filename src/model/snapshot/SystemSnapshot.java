package model.snapshot;

import model.node.NodeCandidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fotografia del sistema osservato in una finestra temporale.
 *
 * <p>Oltre a veicoli, task e candidati computazionali, lo snapshot espone
 * gateway e collegamenti radio di accesso. Queste strutture descrivono come
 * un veicolo raggiunge l'infrastruttura e non introducono nuove variabili
 * decisionali nel cromosoma.</p>
 */
public class SystemSnapshot {
    private String snapshotId;
    private double timeSeconds;
    private List<VehicleSnapshot> vehicles;
    private List<TaskInstance> tasks;
    private List<NodeCandidate> candidateNodes;
    private List<AccessGatewaySnapshot> accessGateways;
    private List<AccessLinkSnapshot> accessLinks;

    public SystemSnapshot() {
        this.vehicles = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.candidateNodes = new ArrayList<>();
        this.accessGateways = new ArrayList<>();
        this.accessLinks = new ArrayList<>();
    }

    /**
     * Costruttore storico mantenuto per compatibilità con chiamanti legacy.
     *
     * <p>Uno snapshot costruito così non contiene access link. Può essere usato
     * soltanto in test privi di candidati CLOUD oppure deve essere migrato prima
     * della validazione gateway-aware.</p>
     */
    public SystemSnapshot(
            String snapshotId,
            double timeSeconds,
            List<VehicleSnapshot> vehicles,
            List<TaskInstance> tasks,
            List<NodeCandidate> candidateNodes
    ) {
        this(
                snapshotId,
                timeSeconds,
                vehicles,
                tasks,
                candidateNodes,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    public SystemSnapshot(
            String snapshotId,
            double timeSeconds,
            List<VehicleSnapshot> vehicles,
            List<TaskInstance> tasks,
            List<NodeCandidate> candidateNodes,
            List<AccessGatewaySnapshot> accessGateways,
            List<AccessLinkSnapshot> accessLinks
    ) {
        this.snapshotId = snapshotId;
        this.timeSeconds = timeSeconds;
        this.vehicles = copyOrEmpty(vehicles);
        this.tasks = copyOrEmpty(tasks);
        this.candidateNodes = copyOrEmpty(candidateNodes);
        this.accessGateways = copyOrEmpty(accessGateways);
        this.accessLinks = copyOrEmpty(accessLinks);
    }

    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }
    public double getTimeSeconds() { return timeSeconds; }
    public void setTimeSeconds(double timeSeconds) { this.timeSeconds = timeSeconds; }
    public List<VehicleSnapshot> getVehicles() { return vehicles; }
    public void setVehicles(List<VehicleSnapshot> vehicles) { this.vehicles = copyOrEmpty(vehicles); }
    public List<TaskInstance> getTasks() { return tasks; }
    public void setTasks(List<TaskInstance> tasks) { this.tasks = copyOrEmpty(tasks); }
    public List<NodeCandidate> getCandidateNodes() { return candidateNodes; }
    public void setCandidateNodes(List<NodeCandidate> candidateNodes) { this.candidateNodes = copyOrEmpty(candidateNodes); }
    public List<AccessGatewaySnapshot> getAccessGateways() { return accessGateways; }
    public void setAccessGateways(List<AccessGatewaySnapshot> accessGateways) { this.accessGateways = copyOrEmpty(accessGateways); }
    public List<AccessLinkSnapshot> getAccessLinks() { return accessLinks; }
    public void setAccessLinks(List<AccessLinkSnapshot> accessLinks) { this.accessLinks = copyOrEmpty(accessLinks); }

    private static <T> List<T> copyOrEmpty(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    @Override
    public String toString() {
        return "SystemSnapshot{" +
                "snapshotId='" + snapshotId + '\'' +
                ", timeSeconds=" + timeSeconds +
                ", vehicles=" + vehicles +
                ", tasks=" + tasks +
                ", candidateNodes=" + candidateNodes +
                ", accessGateways=" + accessGateways +
                ", accessLinks=" + accessLinks +
                '}';
    }
}
