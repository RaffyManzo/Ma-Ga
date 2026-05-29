package window.timing;

import config.window.TemporalMaximumBoundMode;
import config.window.TemporalMinimumBoundMode;
import config.window.TemporalWindowConfig;
import model.snapshot.SystemSnapshot;

import java.util.Objects;

/**
 * Calcola DeltaT_min(k) e DeltaT_max(k).
 *
 * <p>La formula resta quella formalizzata:</p>
 *
 * <pre>
 * DeltaT_min(k) = T_s(k) + T_GA_est(k) + T_apply(k) + epsilon_T
 * DeltaT_max(k) = alpha_T * T_coverage_ref(k)
 * </pre>
 *
 * <p>Le modalità servono solo a decidere se T_GA_est(k) e il limite massimo
 * vengono stimati da valori configurati o da valori adattivi. Il calcolo di
 * T_coverage_ref(k) non viene cambiato.</p>
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
        validatePositive("fallbackWindowSeconds", fallbackWindowSeconds);

        TemporalOperationalMetrics metricsForMinimum = selectMetricsForMinimum(
                operationalMetrics
        );

        double minimum = Math.max(
                metricsForMinimum.getMinimumWindowSeconds(),
                config.getEpsilonT()
        );

        double referenceCoverage = coverageReferenceCalculator
                .computeReferenceCoverageSeconds(snapshot);
        boolean hasReferenceCoverage = referenceCoverage > 0.0;

        double adaptiveMaximum = hasReferenceCoverage
                ? config.getAlphaT() * referenceCoverage
                : fallbackWindowSeconds;

        double configuredMaximum = config.getConfiguredMaxWindowSeconds();
        double selectedMaximum = selectMaximum(
                adaptiveMaximum,
                configuredMaximum,
                fallbackWindowSeconds,
                hasReferenceCoverage
        );

        boolean raisedToMinimum = selectedMaximum < minimum;
        double maximum = Math.max(selectedMaximum, minimum);

        return new TemporalWindowBounds(
                minimum,
                maximum,
                referenceCoverage,
                hasReferenceCoverage,
                adaptiveMaximum,
                configuredMaximum,
                metricsForMinimum.getGaRuntimeEstimateSeconds(),
                operationalMetrics.getObservedGaRuntimeSeconds(),
                config.getMinimumBoundMode(),
                config.getMaximumBoundMode(),
                raisedToMinimum
        );
    }

    private TemporalOperationalMetrics selectMetricsForMinimum(
            TemporalOperationalMetrics operationalMetrics
    ) {
        if (config.getMinimumBoundMode()
                == TemporalMinimumBoundMode.OBSERVED_GA_RUNTIME) {
            return operationalMetrics.withGaRuntimeEstimateSeconds(
                    operationalMetrics.getObservedGaRuntimeSeconds()
            );
        }

        return operationalMetrics.withGaRuntimeEstimateSeconds(
                config.getDefaultGaRuntimeEstimateSeconds()
        );
    }

    private double selectMaximum(
            double adaptiveMaximum,
            double configuredMaximum,
            double fallbackWindowSeconds,
            boolean hasReferenceCoverage
    ) {
        if (config.getMaximumBoundMode()
                == TemporalMaximumBoundMode.CONFIGURED_MAX) {
            return configuredMaximum;
        }

        if (hasReferenceCoverage) {
            return adaptiveMaximum;
        }

        return Math.min(configuredMaximum, fallbackWindowSeconds);
    }

    private static void validatePositive(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be > 0.");
        }
    }
}
