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
 * <p>La formalizzazione richiede una sola stima per ciascun veicolo osservato,
 * non una stima per ogni candidato computazionale. Nella versione standalone
 * non esiste ancora un modello esplicito del collegamento di accesso radio.
 * Come correzione minima, questa classe usa quindi il migliore candidato EDGE
 * raggiungibile di ciascun veicolo come proxy del relativo nodo infrastrutturale
 * o di accesso.</p>
 *
 * <p>I candidati VEHICLE sono esclusi: descrivono alternative V2V utili al GA,
 * ma non il collegamento infrastrutturale di riferimento del veicolo. LOCAL e
 * CLOUD restano esclusi perché usano tempi convenzionali e falserebbero il bound
 * massimo della finestra.</p>
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
     * Calcola la media dei migliori tempi di copertura EDGE, una sola volta per
     * ciascun veicolo per il quale è disponibile una proxy infrastrutturale.
     *
     * <p>Se un veicolo dispone di più candidati EDGE, viene usato quello con
     * maggiore copertura residua. In questo modo il numero delle alternative
     * computazionali non modifica artificialmente il peso del veicolo nella
     * media.</p>
     */
    public double computeReferenceCoverageSeconds(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        Map<String, VehicleSnapshot> vehiclesById = indexVehicles(snapshot);
        Map<String, Double> bestEdgeCoverageByVehicleId = new HashMap<>();

        if (snapshot.getCandidateNodes() == null) {
            return 0.0;
        }

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate == null || candidate.getType() != NodeType.EDGE) {
                continue;
            }

            VehicleSnapshot source = vehiclesById.get(candidate.getSourceVehicleId());
            if (source == null) {
                continue;
            }

            double coverage = estimateEdgeCoverage(candidate, source);
            if (!Double.isFinite(coverage) || coverage <= 0.0) {
                continue;
            }

            double clampedCoverage = mobilityConfig.clampCoverageTime(coverage);
            bestEdgeCoverageByVehicleId.merge(
                    candidate.getSourceVehicleId(),
                    clampedCoverage,
                    Math::max
            );
        }

        if (bestEdgeCoverageByVehicleId.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (double coverage : bestEdgeCoverageByVehicleId.values()) {
            sum += coverage;
        }
        return sum / bestEdgeCoverageByVehicleId.size();
    }

    public boolean hasReferenceCoverage(SystemSnapshot snapshot) {
        return computeReferenceCoverageSeconds(snapshot) > 0.0;
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
