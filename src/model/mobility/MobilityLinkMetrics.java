package model.mobility;

import model.node.NodeType;

import java.util.Objects;

/**
 * Metriche geometriche e cinematiche usate per valutare un collegamento.
 */
public final class MobilityLinkMetrics {
    public enum ModelMode {
        LOCAL_CONVENTIONAL,
        EDGE_GEOMETRIC,
        V2V_SCALAR_RELATIVE_SPEED,
        CLOUD_GATEWAY_GEOMETRIC,
        /** @deprecated mantenuto per compatibilità diagnostica con vecchi report. */
        @Deprecated CLOUD_STABLE_PLACEHOLDER,
        LEGACY_UNAVAILABLE
    }

    private final NodeType nodeType;
    private final ModelMode modelMode;
    private final double distanceMeters;
    private final double coverageRadiusMeters;
    private final double sourceSpeedMetersPerSecond;
    private final double relativeSpeedMetersPerSecond;
    private final double coverageTimeSeconds;
    private final double linkInstability;
    private final String referenceAccessGatewayId;

    public MobilityLinkMetrics(
            NodeType nodeType,
            ModelMode modelMode,
            double distanceMeters,
            double coverageRadiusMeters,
            double sourceSpeedMetersPerSecond,
            double relativeSpeedMetersPerSecond,
            double coverageTimeSeconds,
            double linkInstability
    ) {
        this(
                nodeType,
                modelMode,
                distanceMeters,
                coverageRadiusMeters,
                sourceSpeedMetersPerSecond,
                relativeSpeedMetersPerSecond,
                coverageTimeSeconds,
                linkInstability,
                null
        );
    }

    public MobilityLinkMetrics(
            NodeType nodeType,
            ModelMode modelMode,
            double distanceMeters,
            double coverageRadiusMeters,
            double sourceSpeedMetersPerSecond,
            double relativeSpeedMetersPerSecond,
            double coverageTimeSeconds,
            double linkInstability,
            String referenceAccessGatewayId
    ) {
        this.nodeType = Objects.requireNonNull(nodeType, "nodeType must not be null.");
        this.modelMode = Objects.requireNonNull(modelMode, "modelMode must not be null.");
        this.distanceMeters = distanceMeters;
        this.coverageRadiusMeters = coverageRadiusMeters;
        this.sourceSpeedMetersPerSecond = sourceSpeedMetersPerSecond;
        this.relativeSpeedMetersPerSecond = relativeSpeedMetersPerSecond;
        this.coverageTimeSeconds = finiteOrZero(coverageTimeSeconds);
        this.linkInstability = clamp01(linkInstability);
        this.referenceAccessGatewayId = referenceAccessGatewayId;
    }

    public static MobilityLinkMetrics local(double coverageTimeSeconds) {
        return new MobilityLinkMetrics(
                NodeType.LOCAL,
                ModelMode.LOCAL_CONVENTIONAL,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                coverageTimeSeconds,
                0.0
        );
    }

    /** Crea metriche cloud derivate dal gateway radio attivo. */
    public static MobilityLinkMetrics cloudGateway(AccessLinkMetrics access) {
        Objects.requireNonNull(access, "access must not be null.");
        return new MobilityLinkMetrics(
                NodeType.CLOUD,
                ModelMode.CLOUD_GATEWAY_GEOMETRIC,
                access.getDistanceMeters(),
                access.getCoverageRadiusMeters(),
                access.getSourceSpeedMetersPerSecond(),
                Double.NaN,
                access.getCoverageTimeSeconds(),
                access.getLinkInstability(),
                access.getGatewayId()
        );
    }

    /** @deprecated il cloud gateway-aware non usa più il placeholder stabile. */
    @Deprecated
    public static MobilityLinkMetrics cloud(double coverageTimeSeconds) {
        return new MobilityLinkMetrics(
                NodeType.CLOUD,
                ModelMode.CLOUD_STABLE_PLACEHOLDER,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                coverageTimeSeconds,
                0.0
        );
    }

    public static MobilityLinkMetrics legacy(NodeType nodeType, double coverageTimeSeconds) {
        return new MobilityLinkMetrics(
                nodeType,
                ModelMode.LEGACY_UNAVAILABLE,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                coverageTimeSeconds,
                0.0
        );
    }

    public NodeType getNodeType() { return nodeType; }
    public ModelMode getModelMode() { return modelMode; }
    public double getDistanceMeters() { return distanceMeters; }
    public double getCoverageRadiusMeters() { return coverageRadiusMeters; }
    public double getSourceSpeedMetersPerSecond() { return sourceSpeedMetersPerSecond; }
    public double getRelativeSpeedMetersPerSecond() { return relativeSpeedMetersPerSecond; }
    public double getCoverageTimeSeconds() { return coverageTimeSeconds; }
    public double getLinkInstability() { return linkInstability; }
    public String getReferenceAccessGatewayId() { return referenceAccessGatewayId; }
    public boolean isCloudStablePlaceholder() { return modelMode == ModelMode.CLOUD_STABLE_PLACEHOLDER; }
    public boolean isCloudGatewayAware() { return modelMode == ModelMode.CLOUD_GATEWAY_GEOMETRIC; }
    public boolean hasGeometricDistance() { return Double.isFinite(distanceMeters); }
    public boolean hasCoverageRadius() { return Double.isFinite(coverageRadiusMeters); }
    public boolean hasRelativeSpeed() { return Double.isFinite(relativeSpeedMetersPerSecond); }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) { return 0.0; }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
