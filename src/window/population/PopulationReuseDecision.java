package window.population;

/**
 * Decisione finale di riuso della popolazione.
 */
public final class PopulationReuseDecision {

    private final PopulationReuseMode suggestedMode;
    private final PopulationReuseMode appliedMode;
    private final WindowPerformanceSignal previousPerformanceSignal;
    private final boolean componentSpike;
    private final boolean severeComponentSpike;
    private final String reason;

    public PopulationReuseDecision(
            PopulationReuseMode suggestedMode,
            PopulationReuseMode appliedMode,
            WindowPerformanceSignal previousPerformanceSignal,
            boolean componentSpike,
            boolean severeComponentSpike,
            String reason
    ) {
        if (suggestedMode == null) {
            throw new IllegalArgumentException("suggestedMode must not be null.");
        }
        if (appliedMode == null) {
            throw new IllegalArgumentException("appliedMode must not be null.");
        }
        if (previousPerformanceSignal == null) {
            throw new IllegalArgumentException(
                    "previousPerformanceSignal must not be null."
            );
        }
        this.suggestedMode = suggestedMode;
        this.appliedMode = appliedMode;
        this.previousPerformanceSignal = previousPerformanceSignal;
        this.componentSpike = componentSpike;
        this.severeComponentSpike = severeComponentSpike;
        this.reason = reason == null || reason.isBlank()
                ? "No reason provided."
                : reason;
    }

    public PopulationReuseMode getSuggestedMode() {
        return suggestedMode;
    }

    public PopulationReuseMode getAppliedMode() {
        return appliedMode;
    }

    public WindowPerformanceSignal getPreviousPerformanceSignal() {
        return previousPerformanceSignal;
    }

    public boolean hasComponentSpike() {
        return componentSpike;
    }

    public boolean hasSevereComponentSpike() {
        return severeComponentSpike;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "PopulationReuseDecision{" +
                "suggestedMode=" + suggestedMode +
                ", appliedMode=" + appliedMode +
                ", previousPerformanceSignal=" + previousPerformanceSignal +
                ", componentSpike=" + componentSpike +
                ", severeComponentSpike=" + severeComponentSpike +
                ", reason='" + reason + '\'' +
                '}';
    }
}
