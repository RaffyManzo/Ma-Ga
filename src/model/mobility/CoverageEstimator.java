package model.mobility;

import config.mobility.MobilityConfig;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * Stima le grandezze mobility-aware di un candidato rispetto a un task.
 *
 * <p>La classe usa lo snapshot corrente, il task e il candidato selezionato.
 * Il tempo di copertura e l'instabilità del collegamento non vengono letti
 * da {@code NodeCandidate}: vengono calcolati usando la geometria osservata
 * nello snapshot corrente.</p>
 */
public final class CoverageEstimator {

    private final MobilityConfig mobilityConfig;

    /**
     * Costruisce lo stimatore usando la configurazione di mobilità.
     *
     * @param mobilityConfig configurazione dei parametri di copertura
     */
    public CoverageEstimator(MobilityConfig mobilityConfig) {
        this.mobilityConfig = Objects.requireNonNull(
                mobilityConfig,
                "mobilityConfig must not be null."
        );
    }

    /**
     * Stima il tempo di copertura per una scelta task-candidato.
     *
     * @param snapshot stato corrente del sistema
     * @param task task da eseguire
     * @param candidate candidato scelto per il task
     * @return tempo di copertura stimato in secondi
     */
    public double estimateCoverageTimeSeconds(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");

        validateCandidateForTask(task, candidate);

        if (candidate.getType() == NodeType.LOCAL) {
            return mobilityConfig.getLocalCoverageTimeSeconds();
        }

        if (candidate.getType() == NodeType.CLOUD) {
            return mobilityConfig.getCloudCoverageTimeSeconds();
        }

        if (candidate.getType() == NodeType.EDGE) {
            return estimateInfrastructureCoverage(snapshot, task, candidate);
        }

        if (candidate.getType() == NodeType.VEHICLE) {
            return estimateV2vCoverage(snapshot, task, candidate);
        }

        throw new IllegalArgumentException(
                "Unsupported candidate type: " + candidate.getType()
        );
    }

    /**
     * Stima l'instabilità del collegamento secondo la formalizzazione:
     * phi_link(i, n) = d(i, n) / R(n).
     *
     * <p>Il valore viene limitato all'intervallo [0, 1]. Per il nodo locale
     * vale sempre zero. Per il cloud viene mantenuta temporaneamente
     * l'assunzione standalone di collegamento stabile: il prototipo non
     * modella ancora il gateway radio usato per raggiungere il cloud.</p>
     *
     * @param snapshot stato corrente del sistema
     * @param task task da eseguire
     * @param candidate candidato scelto per il task
     * @return instabilità normalizzata del collegamento nell'intervallo [0, 1]
     */
    public double estimateLinkInstability(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");

        validateCandidateForTask(task, candidate);

        if (candidate.getType() == NodeType.LOCAL) {
            return 0.0;
        }

        if (candidate.getType() == NodeType.CLOUD) {
            // Assunzione provvisoria esplicita del prototipo standalone:
            // finché non viene modellato il gateway di accesso radio,
            // il collegamento verso il cloud viene considerato stabile.
            return 0.0;
        }

        if (candidate.getType() == NodeType.EDGE) {
            return estimateInfrastructureLinkInstability(snapshot, task, candidate);
        }

        if (candidate.getType() == NodeType.VEHICLE) {
            return estimateV2vLinkInstability(snapshot, task, candidate);
        }

        throw new IllegalArgumentException(
                "Unsupported candidate type: " + candidate.getType()
        );
    }

    /**
     * Stima la copertura verso un nodo infrastrutturale, ad esempio EDGE/RSU.
     */
    private double estimateInfrastructureCoverage(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate
    ) {
        VehicleSnapshot sourceVehicle = findVehicleById(
                snapshot,
                task.getSourceVehicleId()
        );

        requireInfrastructureGeometry(candidate);

        double distanceMeters = distance(
                sourceVehicle.getX(),
                sourceVehicle.getY(),
                candidate.getNodeX(),
                candidate.getNodeY()
        );

        double remainingDistanceMeters =
                candidate.getCoverageRadiusMeters() - distanceMeters;

        if (remainingDistanceMeters <= 0.0) {
            return 0.0;
        }

        double speed = Math.max(
                sourceVehicle.getSpeed(),
                mobilityConfig.getEpsilonSpeedMetersPerSecond()
        );

        double coverageTime = remainingDistanceMeters / speed;
        return mobilityConfig.clampCoverageTime(coverageTime);
    }

    /**
     * Calcola phi_link per un nodo EDGE/RSU.
     */
    private double estimateInfrastructureLinkInstability(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate
    ) {
        VehicleSnapshot sourceVehicle = findVehicleById(
                snapshot,
                task.getSourceVehicleId()
        );

        requireInfrastructureGeometry(candidate);

        double distanceMeters = distance(
                sourceVehicle.getX(),
                sourceVehicle.getY(),
                candidate.getNodeX(),
                candidate.getNodeY()
        );

        return clamp01(distanceMeters / candidate.getCoverageRadiusMeters());
    }

    /**
     * Stima la copertura di un collegamento V2V.
     */
    private double estimateV2vCoverage(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate
    ) {
        VehicleSnapshot sourceVehicle = findVehicleById(
                snapshot,
                task.getSourceVehicleId()
        );

        VehicleSnapshot targetVehicle = findVehicleById(
                snapshot,
                candidate.getExecutionNodeId()
        );

        double distanceMeters = distance(
                sourceVehicle.getX(),
                sourceVehicle.getY(),
                targetVehicle.getX(),
                targetVehicle.getY()
        );

        double remainingDistanceMeters =
                mobilityConfig.getV2vCommunicationRadiusMeters() - distanceMeters;

        if (remainingDistanceMeters <= 0.0) {
            return 0.0;
        }

        double relativeSpeed = Math.abs(
                sourceVehicle.getSpeed() - targetVehicle.getSpeed()
        );

        double safeRelativeSpeed = Math.max(
                relativeSpeed,
                mobilityConfig.getEpsilonSpeedMetersPerSecond()
        );

        double coverageTime = remainingDistanceMeters / safeRelativeSpeed;
        return mobilityConfig.clampCoverageTime(coverageTime);
    }

    /**
     * Calcola phi_link per un collegamento V2V.
     */
    private double estimateV2vLinkInstability(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate
    ) {
        VehicleSnapshot sourceVehicle = findVehicleById(
                snapshot,
                task.getSourceVehicleId()
        );

        VehicleSnapshot targetVehicle = findVehicleById(
                snapshot,
                candidate.getExecutionNodeId()
        );

        double distanceMeters = distance(
                sourceVehicle.getX(),
                sourceVehicle.getY(),
                targetVehicle.getX(),
                targetVehicle.getY()
        );

        return clamp01(
                distanceMeters / mobilityConfig.getV2vCommunicationRadiusMeters()
        );
    }

    private void requireInfrastructureGeometry(NodeCandidate candidate) {
        if (!candidate.hasCoverageGeometry()) {
            throw new IllegalArgumentException(
                    "Infrastructure candidate "
                            + candidate.getCandidateId()
                            + " must define nodeX, nodeY and coverageRadiusMeters."
            );
        }
    }

    /**
     * Recupera un veicolo dallo snapshot.
     */
    private VehicleSnapshot findVehicleById(
            SystemSnapshot snapshot,
            String vehicleId
    ) {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException(
                    "vehicleId must not be null or blank."
            );
        }

        List<VehicleSnapshot> vehicles = snapshot.getVehicles();
        if (vehicles == null) {
            throw new IllegalArgumentException(
                    "snapshot.vehicles must not be null."
            );
        }

        for (Object item : vehicles) {
            if (!(item instanceof VehicleSnapshot)) {
                throw new IllegalArgumentException(
                        "snapshot.vehicles contains an invalid element: " + item
                );
            }

            VehicleSnapshot vehicle = (VehicleSnapshot) item;
            if (vehicleId.equals(vehicle.getVehicleId())) {
                return vehicle;
            }
        }

        throw new IllegalArgumentException(
                "Vehicle not found in snapshot: " + vehicleId
        );
    }

    /**
     * Verifica che il candidato appartenga al veicolo sorgente del task.
     */
    private void validateCandidateForTask(
            TaskInstance task,
            NodeCandidate candidate
    ) {
        if (!candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
            throw new IllegalArgumentException(
                    "Candidate "
                            + candidate.getCandidateId()
                            + " is not valid for task source vehicle "
                            + task.getSourceVehicleId()
            );
        }
    }

    /**
     * Calcola la distanza euclidea tra due punti.
     */
    private double distance(
            double x1,
            double y1,
            double x2,
            double y2
    ) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
