package window.timing;

import config.window.TemporalWindowConfig;
import model.snapshot.SystemSnapshot;
import window.dynamicity.DynamicityBreakdown;
import window.dynamicity.DynamicityLevel;

import java.util.Objects;

/**
 * Controller della finestra adattiva.
 *
 * <p>Non modifica il GA. Decide solo la durata della prossima finestra.</p>
 */
public final class AdaptiveWindowController {

    private final TemporalWindowConfig config;
    private final TemporalWindowBoundsCalculator boundsCalculator;

    public AdaptiveWindowController(
            TemporalWindowConfig config,
            TemporalWindowBoundsCalculator boundsCalculator
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.boundsCalculator = Objects.requireNonNull(
                boundsCalculator,
                "boundsCalculator must not be null."
        );
    }

    public AdaptiveWindowDecision decideNextWindow(
            double currentWindowSeconds,
            DynamicityBreakdown dynamicityBreakdown,
            SystemSnapshot currentSnapshot,
            TemporalOperationalMetrics operationalMetrics
    ) {
        validatePositive("currentWindowSeconds", currentWindowSeconds);
        Objects.requireNonNull(
                dynamicityBreakdown,
                "dynamicityBreakdown must not be null."
        );
        Objects.requireNonNull(currentSnapshot, "currentSnapshot must not be null.");
        Objects.requireNonNull(
                operationalMetrics,
                "operationalMetrics must not be null."
        );

        TemporalWindowBounds bounds = boundsCalculator.compute(
                currentSnapshot,
                operationalMetrics,
                currentWindowSeconds
        );

        DynamicityLevel level = dynamicityBreakdown.getDynamicityLevel();

        if (level == DynamicityLevel.UNKNOWN) {
            return new AdaptiveWindowDecision(
                    currentWindowSeconds,
                    bounds.clamp(currentWindowSeconds),
                    bounds,
                    level,
                    AdaptiveWindowDecision.Action.FIRST_RUN,
                    "First run: keep the initial window inside the computed bounds."
            );
        }

        double candidate;
        AdaptiveWindowDecision.Action action;
        String reason;

        if (level == DynamicityLevel.STABLE) {
            candidate = currentWindowSeconds + config.getEtaUp();
            action = AdaptiveWindowDecision.Action.INCREASE;
            reason = "Stable dynamicity: increase the next window additively.";
        } else if (level == DynamicityLevel.HIGH || hasSevereComponentSpike(dynamicityBreakdown)) {
            candidate = currentWindowSeconds - config.getEtaDown();
            action = AdaptiveWindowDecision.Action.DECREASE;
            reason = "High dynamicity or severe component spike: decrease the next window.";
        } else {
            candidate = currentWindowSeconds;
            action = AdaptiveWindowDecision.Action.KEEP;
            reason = "Moderate dynamicity: keep the current duration within bounds.";
        }

        double clamped = bounds.clamp(candidate);

        if (Math.abs(clamped - candidate) > config.getEpsilonT()) {
            action = AdaptiveWindowDecision.Action.CLAMP_TO_BOUNDS;
            reason = reason + " The candidate value was clamped to temporal bounds.";
        }

        return new AdaptiveWindowDecision(
                currentWindowSeconds,
                clamped,
                bounds,
                level,
                action,
                reason
        );
    }

    private boolean hasSevereComponentSpike(DynamicityBreakdown breakdown) {
        return breakdown.getTaskVariation() >= 0.70
                || breakdown.getLinkVariation() >= 0.75;
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
