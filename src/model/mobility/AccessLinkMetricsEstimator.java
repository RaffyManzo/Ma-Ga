package model.mobility;

import config.mobility.MobilityConfig;
import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * Calcola copertura e instabilità del collegamento radio attivo.
 *
 * <p>La prima versione usa una proxy geometrica compatibile con gli snapshot
 * standalone e con dati ottenibili da MOSAIC/SUMO: posizione del veicolo,
 * posizione del gateway e raggio di copertura configurato.</p>
 */
public final class AccessLinkMetricsEstimator {
    private final MobilityConfig mobilityConfig;
    private final AccessLinkResolver resolver;

    public AccessLinkMetricsEstimator(MobilityConfig mobilityConfig) {
        this(mobilityConfig, new AccessLinkResolver());
    }

    public AccessLinkMetricsEstimator(
            MobilityConfig mobilityConfig,
            AccessLinkResolver resolver
    ) {
        this.mobilityConfig = Objects.requireNonNull(
                mobilityConfig,
                "mobilityConfig must not be null."
        );
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null.");
    }

    public AccessLinkMetrics estimateActiveLink(SystemSnapshot snapshot, String vehicleId) {
        return estimateActiveLinkIfPresent(snapshot, vehicleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vehicle " + vehicleId + " has no active access link."
                ));
    }

    /**
     * Stima le metriche dell'access link attivo se il veicolo ne possiede uno.
     *
     * <p>Zero link attivi producono {@link Optional#empty()}; piu' di un link
     * attivo resta un errore strutturale propagato dal resolver. Non vengono
     * create metriche sintetiche per gateway mancanti.</p>
     *
     * @param snapshot snapshot da interrogare
     * @param vehicleId veicolo sorgente
     * @return metriche del link attivo, se presente
     */
    public Optional<AccessLinkMetrics> estimateActiveLinkIfPresent(
            SystemSnapshot snapshot,
            String vehicleId
    ) {
        Optional<AccessLinkSnapshot> activeLink = resolver.findActiveAccessLink(
                snapshot,
                vehicleId
        );
        if (activeLink.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(estimate(snapshot, vehicleId, activeLink.get()));
    }

    private AccessLinkMetrics estimate(
            SystemSnapshot snapshot,
            String vehicleId,
            AccessLinkSnapshot link
    ) {
        AccessGatewaySnapshot gateway = resolver.requireGateway(snapshot, link.getGatewayId());
        VehicleSnapshot vehicle = resolver.requireVehicle(snapshot, vehicleId);

        double distance = distance(vehicle.getX(), vehicle.getY(), gateway.getX(), gateway.getY());
        double radius = gateway.getCoverageRadiusMeters();
        boolean geometricallyAvailable = Double.isFinite(distance) && distance < radius;
        boolean available = link.isAvailable() && geometricallyAvailable;
        double remainingDistance = Math.max(0.0, radius - distance);
        double safeSpeed = Math.max(
                Math.abs(vehicle.getSpeed()),
                mobilityConfig.getEpsilonSpeedMetersPerSecond()
        );
        double coverageTime = available
                ? mobilityConfig.clampCoverageTime(remainingDistance / safeSpeed)
                : 0.0;
        double linkInstability = clamp01(distance / radius);

        return new AccessLinkMetrics(
                link.getAccessLinkId(),
                gateway.getGatewayId(),
                gateway.getGatewayType(),
                distance,
                radius,
                vehicle.getSpeed(),
                coverageTime,
                linkInstability,
                available
        );
    }

    private double distance(double x1, double y1, double x2, double y2) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1)
                || !Double.isFinite(x2) || !Double.isFinite(y2)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double clamp01(double value) {
        if (!Double.isFinite(value)) { return 1.0; }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
