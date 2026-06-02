package model.mobility;

/**
 * Metriche geometriche derivate dal collegamento radio attivo di un veicolo.
 */
public final class AccessLinkMetrics {
    private final String accessLinkId;
    private final String gatewayId;
    private final String gatewayType;
    private final double distanceMeters;
    private final double coverageRadiusMeters;
    private final double sourceSpeedMetersPerSecond;
    private final double coverageTimeSeconds;
    private final double linkInstability;
    private final boolean available;

    public AccessLinkMetrics(
            String accessLinkId,
            String gatewayId,
            String gatewayType,
            double distanceMeters,
            double coverageRadiusMeters,
            double sourceSpeedMetersPerSecond,
            double coverageTimeSeconds,
            double linkInstability,
            boolean available
    ) {
        this.accessLinkId = accessLinkId;
        this.gatewayId = gatewayId;
        this.gatewayType = gatewayType;
        this.distanceMeters = distanceMeters;
        this.coverageRadiusMeters = coverageRadiusMeters;
        this.sourceSpeedMetersPerSecond = sourceSpeedMetersPerSecond;
        this.coverageTimeSeconds = finiteNonNegative(coverageTimeSeconds);
        this.linkInstability = clamp01(linkInstability);
        this.available = available;
    }

    public String getAccessLinkId() { return accessLinkId; }
    public String getGatewayId() { return gatewayId; }
    public String getGatewayType() { return gatewayType; }
    public double getDistanceMeters() { return distanceMeters; }
    public double getCoverageRadiusMeters() { return coverageRadiusMeters; }
    public double getSourceSpeedMetersPerSecond() { return sourceSpeedMetersPerSecond; }
    public double getCoverageTimeSeconds() { return coverageTimeSeconds; }
    public double getLinkInstability() { return linkInstability; }
    public boolean isAvailable() { return available; }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) { return 1.0; }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
