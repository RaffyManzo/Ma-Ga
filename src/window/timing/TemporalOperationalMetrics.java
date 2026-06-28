package window.timing;

/**
 * Tempi operativi usati per calcolare il limite minimo della finestra.
 *
 * <p>Formalmente:</p>
 *
 * <pre>
 * DeltaT_min(k) = T_s(k) + T_GA_est(k) + T_apply(k) + epsilon_T
 * </pre>
 *
 * <p>Questa classe conserva il valore di T_GA_est usato per il calcolo.
 * Il tempo osservato reale del GA viene mantenuto separato quando serve
 * nella diagnostica.</p>
 */
public final class TemporalOperationalMetrics {

    private final double dataCollectionSeconds;
    private final double gaRuntimeEstimateSeconds;
    private final double strategyApplicationSeconds;
    private final double epsilonSeconds;
    private final double observedGaRuntimeSeconds;
    private final Double maximumWindowOverrideSeconds;

    public TemporalOperationalMetrics(
            double dataCollectionSeconds,
            double gaRuntimeEstimateSeconds,
            double strategyApplicationSeconds,
            double epsilonSeconds
    ) {
        this(
                dataCollectionSeconds,
                gaRuntimeEstimateSeconds,
                strategyApplicationSeconds,
                epsilonSeconds,
                gaRuntimeEstimateSeconds,
                null
        );
    }

    public TemporalOperationalMetrics(
            double dataCollectionSeconds,
            double gaRuntimeEstimateSeconds,
            double strategyApplicationSeconds,
            double epsilonSeconds,
            double observedGaRuntimeSeconds
    ) {
        this(
                dataCollectionSeconds,
                gaRuntimeEstimateSeconds,
                strategyApplicationSeconds,
                epsilonSeconds,
                observedGaRuntimeSeconds,
                null
        );
    }

    private TemporalOperationalMetrics(
            double dataCollectionSeconds,
            double gaRuntimeEstimateSeconds,
            double strategyApplicationSeconds,
            double epsilonSeconds,
            double observedGaRuntimeSeconds,
            Double maximumWindowOverrideSeconds
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
        this.observedGaRuntimeSeconds = validateFiniteAndNonNegative(
                "observedGaRuntimeSeconds",
                observedGaRuntimeSeconds
        );
        if (maximumWindowOverrideSeconds != null) {
            validatePositive(
                    "maximumWindowOverrideSeconds",
                    maximumWindowOverrideSeconds
            );
        }
        this.maximumWindowOverrideSeconds = maximumWindowOverrideSeconds;
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
                epsilonSeconds,
                defaultGaRuntimeEstimateSeconds,
                null
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
                epsilonSeconds,
                observedGaRuntimeSeconds,
                null
        );
    }

    public TemporalOperationalMetrics withGaRuntimeEstimateSeconds(
            double newGaRuntimeEstimateSeconds
    ) {
        return new TemporalOperationalMetrics(
                dataCollectionSeconds,
                newGaRuntimeEstimateSeconds,
                strategyApplicationSeconds,
                epsilonSeconds,
                observedGaRuntimeSeconds,
                maximumWindowOverrideSeconds
        );
    }

    public TemporalOperationalMetrics withMaximumWindowOverrideSeconds(
            double newMaximumWindowOverrideSeconds
    ) {
        return new TemporalOperationalMetrics(
                dataCollectionSeconds,
                gaRuntimeEstimateSeconds,
                strategyApplicationSeconds,
                epsilonSeconds,
                observedGaRuntimeSeconds,
                newMaximumWindowOverrideSeconds
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

    public double getObservedGaRuntimeSeconds() {
        return observedGaRuntimeSeconds;
    }

    public boolean hasMaximumWindowOverrideSeconds() {
        return maximumWindowOverrideSeconds != null;
    }

    public double getMaximumWindowOverrideSeconds() {
        if (maximumWindowOverrideSeconds == null) {
            throw new IllegalStateException(
                    "maximumWindowOverrideSeconds is not available."
            );
        }
        return maximumWindowOverrideSeconds;
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

    private static double validatePositive(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be > 0.");
        }
        return value;
    }

    @Override
    public String toString() {
        return "TemporalOperationalMetrics{" +
                "dataCollectionSeconds=" + dataCollectionSeconds +
                ", gaRuntimeEstimateSeconds=" + gaRuntimeEstimateSeconds +
                ", observedGaRuntimeSeconds=" + observedGaRuntimeSeconds +
                ", maximumWindowOverrideSeconds=" + maximumWindowOverrideSeconds +
                ", strategyApplicationSeconds=" + strategyApplicationSeconds +
                ", epsilonSeconds=" + epsilonSeconds +
                ", minimumWindowSeconds=" + getMinimumWindowSeconds() +
                '}';
    }
}
