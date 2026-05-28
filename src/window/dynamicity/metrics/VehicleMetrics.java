package window.dynamicity.metrics;

import window.dynamicity.math.DynamicityMath;

/**
 * Stato confrontabile di un veicolo.
 */
public final class VehicleMetrics
        implements ComparableMetric<VehicleMetrics> {

    /*
     * Riferimento spaziale usato solo per normalizzare lo spostamento.
     * In futuro può essere spostato in una configurazione dedicata.
     */
    private static final double POSITION_REFERENCE_METERS = 250.0;

    private final double x;
    private final double y;
    private final double speed;
    private final double localCpu;

    /**
     * Costruisce la metrica veicolo.
     *
     * @param x coordinata X
     * @param y coordinata Y
     * @param speed velocità
     * @param localCpu CPU locale
     */
    public VehicleMetrics(
            double x,
            double y,
            double speed,
            double localCpu
    ) {
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.localCpu = localCpu;
    }

    @Override
    public double relativeVariation(VehicleMetrics other) {
        double positionVariation = positionVariation(other);

        double speedVariation = DynamicityMath.relativeDifference(
                speed,
                other.speed
        );

        double localCpuVariation = DynamicityMath.relativeDifference(
                localCpu,
                other.localCpu
        );

        return DynamicityMath.clamp01(
                0.50 * positionVariation
                        + 0.25 * speedVariation
                        + 0.25 * localCpuVariation
        );
    }

    /**
     * Calcola la variazione spaziale normalizzata.
     */
    private double positionVariation(VehicleMetrics other) {
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(other.x)
                || !Double.isFinite(other.y)) {
            return 1.0;
        }

        double dx = x - other.x;
        double dy = y - other.y;
        double distance = Math.sqrt(dx * dx + dy * dy);

        return DynamicityMath.clamp01(
                distance / POSITION_REFERENCE_METERS
        );
    }
}
