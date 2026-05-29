package window.timing;

/**
 * Tempi operativi usati per calcolare il limite minimo della finestra.
 *
 * <p>Formalmente:</p>
 *
 * <pre>
 * DeltaT_min(k) = T_s(k) + T_GA_est(k) + T_apply(k) + epsilonT
 * </pre>
 */
public final class TemporalOperationalMetrics {

    private final double dataCollectionSeconds;
    private final double gaRuntimeEstimateSeconds;
    private final double strategyApplicationSeconds;
    private final double epsilonSeconds;

    public TemporalOperationalMetrics(
            double dataCollectionSeconds,
            double gaRuntimeEstimateSeconds,
            double strategyApplicationSeconds,
            double epsilonSeconds
    ) {
        this.dataCollectionSeconds = validateFiniteAndNonNegative(
                "dataCollectionSeconds",
                dataCollectionSeconds
        );
        this.gaRuntimeEstimateSeconds = validateFiniteAndNonNegative(
                "gaRuntimeEstimateSeconds",
                gaRuntimeEstimateSeconds
        );
        this.strategyApplicationSeconds = validateFiniteAndNonNegative(
                "strategyApplicationSeconds",
                strategyApplicationSeconds
        );
        this.epsilonSeconds = validateFiniteAndNonNegative(
                "epsilonSeconds",
                epsilonSeconds
        );
    }

    public static TemporalOperationalMetrics estimated(
            double dataCollectionSeconds,
            double defaultGaRuntimeEstimateSeconds,
            double strategyApplicationSeconds,
            double epsilonSeconds
    ) {
        return new TemporalOperationalMetrics(
                dataCollectionSeconds,
                defaultGaRuntimeEstimateSeconds,
                strategyApplicationSeconds,
                epsilonSeconds
        );
    }

    public static TemporalOperationalMetrics observed(
            double dataCollectionSeconds,
            double observedGaRuntimeSeconds,
            double strategyApplicationSeconds,
            double epsilonSeconds
    ) {
        return new TemporalOperationalMetrics(
                dataCollectionSeconds,
                observedGaRuntimeSeconds,
                strategyApplicationSeconds,
                epsilonSeconds
        );
    }

    public double getDataCollectionSeconds() {
        return dataCollectionSeconds;
    }

    public double getGaRuntimeEstimateSeconds() {
        return gaRuntimeEstimateSeconds;
    }

    public double getStrategyApplicationSeconds() {
        return strategyApplicationSeconds;
    }

    public double getEpsilonSeconds() {
        return epsilonSeconds;
    }

    public double getMinimumWindowSeconds() {
        return dataCollectionSeconds
                + gaRuntimeEstimateSeconds
                + strategyApplicationSeconds
                + epsilonSeconds;
    }

    private static double validateFiniteAndNonNegative(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
        return value;
    }

    @Override
    public String toString() {
        return "TemporalOperationalMetrics{" +
                "dataCollectionSeconds=" + dataCollectionSeconds +
                ", gaRuntimeEstimateSeconds=" + gaRuntimeEstimateSeconds +
                ", strategyApplicationSeconds=" + strategyApplicationSeconds +
                ", epsilonSeconds=" + epsilonSeconds +
                ", minimumWindowSeconds=" + getMinimumWindowSeconds() +
                '}';
    }
}
