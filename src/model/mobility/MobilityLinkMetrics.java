package model.mobility;

import model.node.NodeType;

import java.util.Objects;

/**
 * Metriche geometriche e cinematiche usate per valutare un collegamento.
 *
 * <p>L'oggetto separa i dati grezzi del modello di mobilità dalla fitness.
 * In questo modo il report può spiegare come sono stati ottenuti tempo di
 * copertura e instabilità del collegamento senza ricalcolarli a posteriori.</p>
 */
public final class MobilityLinkMetrics {

    /**
     * Modalità usata per stimare le metriche del collegamento.
     */
    public enum ModelMode {
        LOCAL_CONVENTIONAL,
        EDGE_GEOMETRIC,
        V2V_SCALAR_RELATIVE_SPEED,
        CLOUD_STABLE_PLACEHOLDER,
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
        this.nodeType = Objects.requireNonNull(nodeType, "nodeType must not be null.");
        this.modelMode = Objects.requireNonNull(modelMode, "modelMode must not be null.");
        this.distanceMeters = distanceMeters;
        this.coverageRadiusMeters = coverageRadiusMeters;
        this.sourceSpeedMetersPerSecond = sourceSpeedMetersPerSecond;
        this.relativeSpeedMetersPerSecond = relativeSpeedMetersPerSecond;
        this.coverageTimeSeconds = finiteOrZero(coverageTimeSeconds);
        this.linkInstability = clamp01(linkInstability);
    }

    public static MobilityLinkMetrics local(double coverageTimeSeconds) {
        return new MobilityLinkMetrics(
                NodeType.LOCAL,
                ModelMode.LOCAL_CONVENTIONAL,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                coverageTimeSeconds,
                0.0
        );
    }

    public static MobilityLinkMetrics cloud(double coverageTimeSeconds) {
        return new MobilityLinkMetrics(
                NodeType.CLOUD,
                ModelMode.CLOUD_STABLE_PLACEHOLDER,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                coverageTimeSeconds,
                0.0
        );
    }

    public static MobilityLinkMetrics legacy(
            NodeType nodeType,
            double coverageTimeSeconds
    ) {
        return new MobilityLinkMetrics(
                nodeType,
                ModelMode.LEGACY_UNAVAILABLE,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                coverageTimeSeconds,
                0.0
        );
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public ModelMode getModelMode() {
        return modelMode;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public double getCoverageRadiusMeters() {
        return coverageRadiusMeters;
    }

    public double getSourceSpeedMetersPerSecond() {
        return sourceSpeedMetersPerSecond;
    }

    public double getRelativeSpeedMetersPerSecond() {
        return relativeSpeedMetersPerSecond;
    }

    public double getCoverageTimeSeconds() {
        return coverageTimeSeconds;
    }

    public double getLinkInstability() {
        return linkInstability;
    }

    public boolean isCloudStablePlaceholder() {
        return modelMode == ModelMode.CLOUD_STABLE_PLACEHOLDER;
    }

    public boolean hasGeometricDistance() {
        return Double.isFinite(distanceMeters);
    }

    public boolean hasCoverageRadius() {
        return Double.isFinite(coverageRadiusMeters);
    }

    public boolean hasRelativeSpeed() {
        return Double.isFinite(relativeSpeedMetersPerSecond);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
