package window.timing;

import config.mobility.MobilityConfig;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Calcola il tempo di copertura di riferimento della finestra corrente.
 *
 * <p>Il valore viene calcolato solo sui candidati remoti con copertura fisica:
 * EDGE e VEHICLE. LOCAL e CLOUD sono esclusi perché avrebbero tempi
 * convenzionali troppo alti e falserebbero il limite massimo della finestra.</p>
 */
public final class CoverageReferenceCalculator {

    private final MobilityConfig mobilityConfig;

    public CoverageReferenceCalculator(MobilityConfig mobilityConfig) {
        this.mobilityConfig = Objects.requireNonNull(
                mobilityConfig,
                "mobilityConfig must not be null."
        );
    }

    /**
     * Calcola la media dei tempi di copertura positivi e finiti.
     *
     * <p>La media è meno aggressiva del minimo. È adatta a questa fase perché
     * vogliamo una finestra adattiva prudente, ma non troppo instabile.</p>
     */
    public double computeReferenceCoverageSeconds(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        Map<String, VehicleSnapshot> vehiclesById = indexVehicles(snapshot);
        double sum = 0.0;
        int count = 0;

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate == null || !candidate.isRemote()) {
                continue;
            }

            if (candidate.getType() == NodeType.CLOUD) {
                continue;
            }

            double coverage = estimateCoverageSeconds(candidate, vehiclesById);

            if (Double.isFinite(coverage) && coverage > 0.0) {
                sum += mobilityConfig.clampCoverageTime(coverage);
                count++;
            }
        }

        if (count == 0) {
            return 0.0;
        }

        return sum / count;
    }

    public boolean hasReferenceCoverage(SystemSnapshot snapshot) {
        return computeReferenceCoverageSeconds(snapshot) > 0.0;
    }

    private double estimateCoverageSeconds(
            NodeCandidate candidate,
            Map<String, VehicleSnapshot> vehiclesById
    ) {
        VehicleSnapshot source = vehiclesById.get(candidate.getSourceVehicleId());

        if (source == null) {
            return 0.0;
        }

        if (candidate.getType() == NodeType.EDGE) {
            return estimateEdgeCoverage(candidate, source);
        }

        if (candidate.getType() == NodeType.VEHICLE) {
            VehicleSnapshot target = vehiclesById.get(candidate.getExecutionNodeId());
            return estimateV2vCoverage(source, target);
        }

        return 0.0;
    }

    private double estimateEdgeCoverage(
            NodeCandidate candidate,
            VehicleSnapshot source
    ) {
        if (!candidate.hasCoverageGeometry()) {
            return 0.0;
        }

        double distance = euclideanDistance(
                source.getX(),
                source.getY(),
                candidate.getNodeX(),
                candidate.getNodeY()
        );

        double radius = candidate.getCoverageRadiusMeters();
        double remainingDistance = radius - distance;

        if (remainingDistance <= 0.0) {
            return 0.0;
        }

        double speed = Math.max(
                Math.abs(source.getSpeed()),
                mobilityConfig.getEpsilonSpeedMetersPerSecond()
        );

        return remainingDistance / speed;
    }

    private double estimateV2vCoverage(
            VehicleSnapshot source,
            VehicleSnapshot target
    ) {
        if (target == null) {
            return 0.0;
        }

        double distance = euclideanDistance(
                source.getX(),
                source.getY(),
                target.getX(),
                target.getY()
        );

        double radius = mobilityConfig.getV2vCommunicationRadiusMeters();
        double remainingDistance = radius - distance;

        if (remainingDistance <= 0.0) {
            return 0.0;
        }

        double relativeSpeed = Math.abs(source.getSpeed() - target.getSpeed());
        double safeRelativeSpeed = Math.max(
                relativeSpeed,
                mobilityConfig.getEpsilonSpeedMetersPerSecond()
        );

        return remainingDistance / safeRelativeSpeed;
    }

    private Map<String, VehicleSnapshot> indexVehicles(SystemSnapshot snapshot) {
        Map<String, VehicleSnapshot> result = new HashMap<>();

        if (snapshot.getVehicles() == null) {
            return result;
        }

        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            if (vehicle != null && vehicle.getVehicleId() != null) {
                result.put(vehicle.getVehicleId(), vehicle);
            }
        }

        return result;
    }

    private double euclideanDistance(
            double x1,
            double y1,
            double x2,
            double y2
    ) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
