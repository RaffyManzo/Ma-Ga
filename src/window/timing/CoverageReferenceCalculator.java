package window.timing;

import config.mobility.MobilityConfig;
import model.mobility.AccessLinkMetrics;
import model.mobility.AccessLinkMetricsEstimator;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;

import java.util.Objects;

/**
 * Calcola il tempo di copertura di riferimento usando gli access link attivi.
 */
public final class CoverageReferenceCalculator {
    private final AccessLinkMetricsEstimator estimator;

    public CoverageReferenceCalculator(MobilityConfig mobilityConfig) {
        this.estimator = new AccessLinkMetricsEstimator(
                Objects.requireNonNull(mobilityConfig, "mobilityConfig must not be null.")
        );
    }

    /**
     * Ogni veicolo osservato contribuisce una sola volta alla media.
     * Il numero di candidati EDGE, VEHICLE o CLOUD non modifica artificialmente
     * il riferimento temporale.
     */
    public double computeReferenceCoverageSeconds(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        if (snapshot.getVehicles().isEmpty()) { return 0.0; }
        double total = 0.0;
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            AccessLinkMetrics metrics = estimator.estimateActiveLink(snapshot, vehicle.getVehicleId());
            total += metrics.getCoverageTimeSeconds();
        }
        return total / snapshot.getVehicles().size();
    }

    public boolean hasReferenceCoverage(SystemSnapshot snapshot) {
        return computeReferenceCoverageSeconds(snapshot) > 0.0;
    }
}
