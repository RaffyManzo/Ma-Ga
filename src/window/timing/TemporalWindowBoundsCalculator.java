package window.timing;

import config.window.TemporalWindowConfig;
import model.snapshot.SystemSnapshot;

import java.util.Objects;

/**
 * Calcola DeltaT_min(k) e DeltaT_max(k).
 */
public final class TemporalWindowBoundsCalculator {

    private final TemporalWindowConfig config;
    private final CoverageReferenceCalculator coverageReferenceCalculator;

    public TemporalWindowBoundsCalculator(
            TemporalWindowConfig config,
            CoverageReferenceCalculator coverageReferenceCalculator
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.coverageReferenceCalculator = Objects.requireNonNull(
                coverageReferenceCalculator,
                "coverageReferenceCalculator must not be null."
        );
    }

    public TemporalWindowBounds compute(
            SystemSnapshot snapshot,
            TemporalOperationalMetrics operationalMetrics,
            double fallbackWindowSeconds
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(
                operationalMetrics,
                "operationalMetrics must not be null."
        );

        double minimum = operationalMetrics.getMinimumWindowSeconds();
        double referenceCoverage = coverageReferenceCalculator
                .computeReferenceCoverageSeconds(snapshot);
        boolean hasReferenceCoverage = referenceCoverage > 0.0;

        double maximum;

        if (hasReferenceCoverage) {
            maximum = config.getAlphaT() * referenceCoverage;
        } else {
            maximum = fallbackWindowSeconds;
        }

        maximum = Math.max(maximum, minimum);

        return new TemporalWindowBounds(
                Math.max(minimum, config.getEpsilonT()),
                maximum,
                referenceCoverage,
                hasReferenceCoverage
        );
    }
}
