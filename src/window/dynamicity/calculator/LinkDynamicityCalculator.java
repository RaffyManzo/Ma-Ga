package window.dynamicity.calculator;

import config.mobility.MobilityConfig;
import model.mobility.AccessLinkMetrics;
import model.mobility.AccessLinkMetricsEstimator;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;
import window.dynamicity.compare.MetricMapComparator;
import window.dynamicity.math.DynamicityMath;
import window.dynamicity.metrics.LinkMetrics;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Calcola Dl(k), cioè la variazione della qualità dei collegamenti radio di
 * accesso dei veicoli presenti in due finestre consecutive.
 *
 * <p>La qualità del collegamento di riferimento del veicolo v è:</p>
 *
 * <pre>
 * q_v(k) = 1 - phi_link(v, gatewayAttivo)
 * </pre>
 *
 * <p>dove phi_link è la proxy geometrica già prodotta da
 * {@link AccessLinkMetricsEstimator}. Dl(k) è la media delle differenze
 * assolute sui soli veicoli comuni:</p>
 *
 * <pre>
 * Dl(k) = average_v |q_v(k) - q_v(k-1)|, v in V(k) intersection V(k-1)
 * </pre>
 *
 * <p>L'aggiunta o la rimozione di candidati computazionali non influenza più
 * Dl(k), purché il collegamento radio di accesso resti invariato.</p>
 */
public final class LinkDynamicityCalculator {
    private final AccessLinkMetricsEstimator accessLinkMetricsEstimator;

    /**
     * Costruttore storico. Usa la configurazione di mobilità di default.
     * Preferire {@link #LinkDynamicityCalculator(MobilityConfig)} nel wiring
     * applicativo.
     */
    public LinkDynamicityCalculator() {
        this(MobilityConfig.defaultConfig());
    }

    /**
     * Costruisce il calculator gateway-aware.
     *
     * @param mobilityConfig configurazione condivisa con il modello di mobilità
     */
    public LinkDynamicityCalculator(MobilityConfig mobilityConfig) {
        this(new AccessLinkMetricsEstimator(mobilityConfig));
    }

    /**
     * Costruttore utile per test con estimator esplicito.
     *
     * @param accessLinkMetricsEstimator estimator delle metriche di accesso
     */
    public LinkDynamicityCalculator(
            AccessLinkMetricsEstimator accessLinkMetricsEstimator
    ) {
        this.accessLinkMetricsEstimator = Objects.requireNonNull(
                accessLinkMetricsEstimator,
                "accessLinkMetricsEstimator must not be null."
        );
    }

    /**
     * Overload storico mantenuto per compatibilità sorgente.
     *
     * <p>Il vecchio comparator sui candidateId non viene più usato perché
     * descriveva la variazione dei candidati computazionali, non la qualità
     * dell'access link radio richiesta dalla formalizzazione.</p>
     *
     * @param ignoredComparator comparator storico
     */
    @Deprecated
    public LinkDynamicityCalculator(
            MetricMapComparator<LinkMetrics> ignoredComparator
    ) {
        this(MobilityConfig.defaultConfig());
        Objects.requireNonNull(
                ignoredComparator,
                "ignoredComparator must not be null."
        );
    }

    /**
     * Calcola Dl(k) in [0, 1].
     *
     * @param previousSnapshot snapshot precedente
     * @param currentSnapshot snapshot corrente
     * @return variazione media della qualità degli access link
     */
    public double compute(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        Objects.requireNonNull(
                previousSnapshot,
                "previousSnapshot must not be null."
        );
        Objects.requireNonNull(
                currentSnapshot,
                "currentSnapshot must not be null."
        );

        Set<String> commonVehicleIds = vehicleIds(previousSnapshot);
        commonVehicleIds.retainAll(vehicleIds(currentSnapshot));

        if (commonVehicleIds.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (String vehicleId : commonVehicleIds) {
            double previousQuality = quality(previousSnapshot, vehicleId);
            double currentQuality = quality(currentSnapshot, vehicleId);
            sum += Math.abs(currentQuality - previousQuality);
        }

        return DynamicityMath.clamp01(sum / commonVehicleIds.size());
    }

    /**
     * Restituisce q_v(k) in [0, 1]. Un access link indisponibile ha qualità 0.
     */
    public double quality(SystemSnapshot snapshot, String vehicleId) {
        AccessLinkMetrics metrics = accessLinkMetricsEstimator.estimateActiveLink(
                snapshot,
                vehicleId
        );
        if (!metrics.isAvailable()) {
            return 0.0;
        }
        return DynamicityMath.clamp01(1.0 - metrics.getLinkInstability());
    }

    /** Espone l'estimator per i report diagnostici. */
    public AccessLinkMetricsEstimator getAccessLinkMetricsEstimator() {
        return accessLinkMetricsEstimator;
    }

    private Set<String> vehicleIds(SystemSnapshot snapshot) {
        Set<String> result = new HashSet<>();
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            result.add(vehicle.getVehicleId());
        }
        return result;
    }
}
