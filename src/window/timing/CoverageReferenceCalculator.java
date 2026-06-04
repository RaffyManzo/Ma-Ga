package window.timing;

import config.mobility.MobilityConfig;
import model.mobility.AccessLinkMetrics;
import model.mobility.AccessLinkMetricsEstimator;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;

import java.util.Objects;
import java.util.Optional;

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
     * Ogni veicolo con access link attivo contribuisce una sola volta alla media.
     * I veicoli osservati ma senza gateway attivo non vengono inseriti nel
     * denominatore e non ricevono una copertura artificiale pari a zero.
     *
     * <p>Il numero di candidati EDGE, VEHICLE o CLOUD non modifica
     * artificialmente il riferimento temporale.</p>
     */
    public double computeReferenceCoverageSeconds(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        if (snapshot.getVehicles().isEmpty()) { return 0.0; }
        double total = 0.0;
        int activeLinkVehicles = 0;
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            Optional<AccessLinkMetrics> maybeMetrics = estimator.estimateActiveLinkIfPresent(
                    snapshot,
                    vehicle.getVehicleId()
            );
            if (maybeMetrics.isEmpty()) {
                continue;
            }

            AccessLinkMetrics metrics = maybeMetrics.get();
            total += metrics.getCoverageTimeSeconds();
            activeLinkVehicles++;
        }
        if (activeLinkVehicles == 0) {
            return 0.0;
        }
        return total / activeLinkVehicles;
    }

    /**
     * Indica se lo snapshot contiene almeno un riferimento di copertura positivo.
     *
     * <p>Quando nessun veicolo ha access link attivo, il riferimento e' assente
     * e il sistema puo' usare la policy temporale di fallback gia' esistente.</p>
     */
    public boolean hasReferenceCoverage(SystemSnapshot snapshot) {
        return computeReferenceCoverageSeconds(snapshot) > 0.0;
    }
}
