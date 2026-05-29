package window.timing;

import window.dynamicity.DynamicityLevel;

/**
 * Decisione prodotta dal controller della finestra adattiva.
 */
public final class AdaptiveWindowDecision {

    public enum Action {
        FIRST_RUN,
        INCREASE,
        KEEP,
        DECREASE,
        CLAMP_TO_BOUNDS
    }

    private final double currentWindowSeconds;
    private final double nextWindowSeconds;
    private final TemporalWindowBounds bounds;
    private final DynamicityLevel dynamicityLevel;
    private final Action action;
    private final String reason;

    public AdaptiveWindowDecision(
            double currentWindowSeconds,
            double nextWindowSeconds,
            TemporalWindowBounds bounds,
            DynamicityLevel dynamicityLevel,
            Action action,
            String reason
    ) {
        this.currentWindowSeconds = validatePositive(
                "currentWindowSeconds",
                currentWindowSeconds
        );
        this.nextWindowSeconds = validatePositive(
                "nextWindowSeconds",
                nextWindowSeconds
        );
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null.");
        }
        if (dynamicityLevel == null) {
            throw new IllegalArgumentException("dynamicityLevel must not be null.");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null.");
        }
        this.bounds = bounds;
        this.dynamicityLevel = dynamicityLevel;
        this.action = action;
        this.reason = reason == null || reason.isBlank()
                ? "No reason provided."
                : reason;
    }

    public static AdaptiveWindowDecision fixed(
            double windowSeconds,
            TemporalWindowBounds bounds,
            DynamicityLevel dynamicityLevel,
            String reason
    ) {
        return new AdaptiveWindowDecision(
                windowSeconds,
                bounds.clamp(windowSeconds),
                bounds,
                dynamicityLevel,
                Action.KEEP,
                reason
        );
    }

    public double getCurrentWindowSeconds() {
        return currentWindowSeconds;
    }

    public double getNextWindowSeconds() {
        return nextWindowSeconds;
    }

    public double getNextWindowDurationSeconds() {
        return nextWindowSeconds;
    }

    public TemporalWindowBounds getBounds() {
        return bounds;
    }

    public DynamicityLevel getDynamicityLevel() {
        return dynamicityLevel;
    }

    public Action getAction() {
        return action;
    }

    public String getReason() {
        return reason;
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
        return "AdaptiveWindowDecision{" +
                "currentWindowSeconds=" + currentWindowSeconds +
                ", nextWindowSeconds=" + nextWindowSeconds +
                ", bounds=" + bounds +
                ", dynamicityLevel=" + dynamicityLevel +
                ", action=" + action +
                ", reason='" + reason + '\'' +
                '}';
    }
}
