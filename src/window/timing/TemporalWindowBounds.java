package window.timing;

import config.window.TemporalMaximumBoundMode;
import config.window.TemporalMinimumBoundMode;

/**
 * Limiti della prossima finestra temporale.
 */
public final class TemporalWindowBounds {

    private final double minimumWindowSeconds;
    private final double maximumWindowSeconds;
    private final double coverageReferenceSeconds;
    private final boolean coverageReferenceAvailable;
    private final double adaptiveMaximumWindowSeconds;
    private final double configuredMaximumWindowSeconds;
    private final double gaRuntimeEstimateUsedSeconds;
    private final double observedGaRuntimeSeconds;
    private final TemporalMinimumBoundMode minimumBoundMode;
    private final TemporalMaximumBoundMode maximumBoundMode;
    private final boolean maximumRaisedToMinimum;

    public TemporalWindowBounds(
            double minimumWindowSeconds,
            double maximumWindowSeconds,
            double coverageReferenceSeconds,
            boolean coverageReferenceAvailable
    ) {
        this(
                minimumWindowSeconds,
                maximumWindowSeconds,
                coverageReferenceSeconds,
                coverageReferenceAvailable,
                maximumWindowSeconds,
                maximumWindowSeconds,
                0.0,
                0.0,
                TemporalMinimumBoundMode.CONFIGURED_GA_ESTIMATE,
                TemporalMaximumBoundMode.COVERAGE_ADAPTIVE,
                maximumWindowSeconds < minimumWindowSeconds
        );
    }

    public TemporalWindowBounds(
            double minimumWindowSeconds,
            double maximumWindowSeconds,
            double coverageReferenceSeconds,
            boolean coverageReferenceAvailable,
            double adaptiveMaximumWindowSeconds,
            double configuredMaximumWindowSeconds,
            double gaRuntimeEstimateUsedSeconds,
            double observedGaRuntimeSeconds,
            TemporalMinimumBoundMode minimumBoundMode,
            TemporalMaximumBoundMode maximumBoundMode,
            boolean maximumRaisedToMinimum
    ) {
        this.minimumWindowSeconds = validatePositive(
                "minimumWindowSeconds",
                minimumWindowSeconds
        );
        this.maximumWindowSeconds = validatePositive(
                "maximumWindowSeconds",
                Math.max(maximumWindowSeconds, minimumWindowSeconds)
        );
        this.coverageReferenceSeconds = validateFiniteAndNonNegative(
                "coverageReferenceSeconds",
                coverageReferenceSeconds
        );
        this.coverageReferenceAvailable = coverageReferenceAvailable;
        this.adaptiveMaximumWindowSeconds = validateFiniteAndNonNegative(
                "adaptiveMaximumWindowSeconds",
                adaptiveMaximumWindowSeconds
        );
        this.configuredMaximumWindowSeconds = validatePositive(
                "configuredMaximumWindowSeconds",
                configuredMaximumWindowSeconds
        );
        this.gaRuntimeEstimateUsedSeconds = validateFiniteAndNonNegative(
                "gaRuntimeEstimateUsedSeconds",
                gaRuntimeEstimateUsedSeconds
        );
        this.observedGaRuntimeSeconds = validateFiniteAndNonNegative(
                "observedGaRuntimeSeconds",
                observedGaRuntimeSeconds
        );
        if (minimumBoundMode == null) {
            throw new IllegalArgumentException("minimumBoundMode must not be null.");
        }
        if (maximumBoundMode == null) {
            throw new IllegalArgumentException("maximumBoundMode must not be null.");
        }
        this.minimumBoundMode = minimumBoundMode;
        this.maximumBoundMode = maximumBoundMode;
        this.maximumRaisedToMinimum = maximumRaisedToMinimum;
    }

    public double getMinimumWindowSeconds() {
        return minimumWindowSeconds;
    }

    public double getMaximumWindowSeconds() {
        return maximumWindowSeconds;
    }

    public double getCoverageReferenceSeconds() {
        return coverageReferenceSeconds;
    }

    public boolean isCoverageReferenceAvailable() {
        return coverageReferenceAvailable;
    }

    public double getAdaptiveMaximumWindowSeconds() {
        return adaptiveMaximumWindowSeconds;
    }

    public double getConfiguredMaximumWindowSeconds() {
        return configuredMaximumWindowSeconds;
    }

    public double getGaRuntimeEstimateUsedSeconds() {
        return gaRuntimeEstimateUsedSeconds;
    }

    public double getObservedGaRuntimeSeconds() {
        return observedGaRuntimeSeconds;
    }

    public TemporalMinimumBoundMode getMinimumBoundMode() {
        return minimumBoundMode;
    }

    public TemporalMaximumBoundMode getMaximumBoundMode() {
        return maximumBoundMode;
    }

    public boolean isMaximumRaisedToMinimum() {
        return maximumRaisedToMinimum;
    }

    public double clamp(double value) {
        if (!Double.isFinite(value)) {
            return minimumWindowSeconds;
        }
        return Math.max(
                minimumWindowSeconds,
                Math.min(maximumWindowSeconds, value)
        );
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
        return "TemporalWindowBounds{" +
                "minimumWindowSeconds=" + minimumWindowSeconds +
                ", maximumWindowSeconds=" + maximumWindowSeconds +
                ", coverageReferenceSeconds=" + coverageReferenceSeconds +
                ", coverageReferenceAvailable=" + coverageReferenceAvailable +
                ", adaptiveMaximumWindowSeconds=" + adaptiveMaximumWindowSeconds +
                ", configuredMaximumWindowSeconds=" + configuredMaximumWindowSeconds +
                ", gaRuntimeEstimateUsedSeconds=" + gaRuntimeEstimateUsedSeconds +
                ", observedGaRuntimeSeconds=" + observedGaRuntimeSeconds +
                ", minimumBoundMode=" + minimumBoundMode +
                ", maximumBoundMode=" + maximumBoundMode +
                ", maximumRaisedToMinimum=" + maximumRaisedToMinimum +
                '}';
    }
}
