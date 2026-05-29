package window.timing;

/**
 * Limiti della prossima finestra temporale.
 */
public final class TemporalWindowBounds {

    private final double minimumWindowSeconds;
    private final double maximumWindowSeconds;
    private final double coverageReferenceSeconds;
    private final boolean coverageReferenceAvailable;

    public TemporalWindowBounds(
            double minimumWindowSeconds,
            double maximumWindowSeconds,
            double coverageReferenceSeconds,
            boolean coverageReferenceAvailable
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
                '}';
    }
}
