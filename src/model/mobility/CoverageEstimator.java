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
 * Il tempo di copertura e l'instabilità del collegamento vengono ricavati
 * da un'unica stima, così fitness e report osservano gli stessi valori.</p>
 */
public final class CoverageEstimator {

    private final MobilityConfig mobilityConfig;

    public CoverageEstimator(MobilityConfig mobilityConfig) {
        this.mobilityConfig = Objects.requireNonNull(
                mobilityConfig,
                "mobilityConfig must not be null."
        );
    }

    /**
     * Calcola tutte le metriche mobility-aware grezze del collegamento.
     */
    public MobilityLinkMetrics estimateLinkMetrics(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");

        validateCandidateForTask(task, candidate);

        if (candidate.getType() == NodeType.LOCAL) {
            return MobilityLinkMetrics.local(
                    mobilityConfig.getLocalCoverageTimeSeconds()
            );
        }

        if (candidate.getType() == NodeType.CLOUD) {
            // Assunzione provvisoria del prototipo standalone:
            // il gateway radio usato per raggiungere il cloud non è ancora modellato.
            return MobilityLinkMetrics.cloud(
                    mobilityConfig.getCloudCoverageTimeSeconds()
            );
        }

        if (candidate.getType() == NodeType.EDGE) {
            return estimateInfrastructureMetrics(snapshot, task, candidate);
        }

        if (candidate.getType() == NodeType.VEHICLE) {
            return estimateV2vMetrics(snapshot, task, candidate);
        }

        throw new IllegalArgumentException(
                "Unsupported candidate type: " + candidate.getType()
        );
    }

    public double estimateCoverageTimeSeconds(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate
    ) {
        return estimateLinkMetrics(snapshot, task, candidate)
                .getCoverageTimeSeconds();
    }

    public double estimateLinkInstability(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate
    ) {
        return estimateLinkMetrics(snapshot, task, candidate)
                .getLinkInstability();
    }

    private MobilityLinkMetrics estimateInfrastructureMetrics(
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

        double radiusMeters = candidate.getCoverageRadiusMeters();
        double remainingDistanceMeters = radiusMeters - distanceMeters;
        double safeSpeed = Math.max(
                sourceVehicle.getSpeed(),
                mobilityConfig.getEpsilonSpeedMetersPerSecond()
        );

        double coverageTime = remainingDistanceMeters <= 0.0
                ? 0.0
                : mobilityConfig.clampCoverageTime(
                        remainingDistanceMeters / safeSpeed
                );

        return new MobilityLinkMetrics(
                candidate.getType(),
                MobilityLinkMetrics.ModelMode.EDGE_GEOMETRIC,
                distanceMeters,
                radiusMeters,
                sourceVehicle.getSpeed(),
                Double.NaN,
                coverageTime,
                clamp01(distanceMeters / radiusMeters)
        );
    }

    private MobilityLinkMetrics estimateV2vMetrics(
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

        double radiusMeters = mobilityConfig.getV2vCommunicationRadiusMeters();
        double remainingDistanceMeters = radiusMeters - distanceMeters;
        double relativeSpeed = Math.abs(
                sourceVehicle.getSpeed() - targetVehicle.getSpeed()
        );
        double safeRelativeSpeed = Math.max(
                relativeSpeed,
                mobilityConfig.getEpsilonSpeedMetersPerSecond()
        );

        double coverageTime = remainingDistanceMeters <= 0.0
                ? 0.0
                : mobilityConfig.clampCoverageTime(
                        remainingDistanceMeters / safeRelativeSpeed
                );

        return new MobilityLinkMetrics(
                candidate.getType(),
                MobilityLinkMetrics.ModelMode.V2V_SCALAR_RELATIVE_SPEED,
                distanceMeters,
                radiusMeters,
                sourceVehicle.getSpeed(),
                relativeSpeed,
                coverageTime,
                clamp01(distanceMeters / radiusMeters)
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
