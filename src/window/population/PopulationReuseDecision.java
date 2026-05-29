package window.population;

import java.util.Objects;

/**
 * Decisione finale sul riuso della popolazione genetica tra due finestre.
 *
 * <p>La dinamicità produce una decisione di base. Questa classe conserva anche
 * l'eventuale correzione introdotta dalla policy temporale.</p>
 */
public final class PopulationReuseDecision {

    private final PopulationReuseMode baseReuseMode;
    private final PopulationReuseMode appliedReuseMode;
    private final WindowPerformanceSignal previousPerformanceSignal;
    private final boolean componentSpikeDetected;
    private final boolean severeComponentSpikeDetected;
    private final String reason;

    public PopulationReuseDecision(
            PopulationReuseMode baseReuseMode,
            PopulationReuseMode appliedReuseMode,
            WindowPerformanceSignal previousPerformanceSignal,
            boolean componentSpikeDetected,
            String reason
    ) {
        this(
                baseReuseMode,
                appliedReuseMode,
                previousPerformanceSignal,
                componentSpikeDetected,
                false,
                reason
        );
    }

    public PopulationReuseDecision(
            PopulationReuseMode baseReuseMode,
            PopulationReuseMode appliedReuseMode,
            WindowPerformanceSignal previousPerformanceSignal,
            boolean componentSpikeDetected,
            boolean severeComponentSpikeDetected,
            String reason
    ) {
        this.baseReuseMode = Objects.requireNonNull(
                baseReuseMode,
                "baseReuseMode must not be null."
        );
        this.appliedReuseMode = Objects.requireNonNull(
                appliedReuseMode,
                "appliedReuseMode must not be null."
        );
        this.previousPerformanceSignal = previousPerformanceSignal == null
                ? WindowPerformanceSignal.UNKNOWN
                : previousPerformanceSignal;
        this.componentSpikeDetected = componentSpikeDetected;
        this.severeComponentSpikeDetected = severeComponentSpikeDetected;
        this.reason = reason == null || reason.isBlank()
                ? "No explicit reason."
                : reason;
    }

    public static PopulationReuseDecision unchanged(
            PopulationReuseMode mode,
            WindowPerformanceSignal signal,
            boolean componentSpikeDetected,
            String reason
    ) {
        return new PopulationReuseDecision(
                mode,
                mode,
                signal,
                componentSpikeDetected,
                false,
                reason
        );
    }

    public static PopulationReuseDecision unchanged(
            PopulationReuseMode mode,
            WindowPerformanceSignal signal,
            boolean componentSpikeDetected,
            boolean severeComponentSpikeDetected,
            String reason
    ) {
        return new PopulationReuseDecision(
                mode,
                mode,
                signal,
                componentSpikeDetected,
                severeComponentSpikeDetected,
                reason
        );
    }

    public PopulationReuseMode getBaseReuseMode() {
        return baseReuseMode;
    }

    public PopulationReuseMode getAppliedReuseMode() {
        return appliedReuseMode;
    }

    /**
     * Alias usato dalle versioni più recenti del TemporalWindowManager.
     */
    public PopulationReuseMode getAppliedMode() {
        return appliedReuseMode;
    }

    public WindowPerformanceSignal getPreviousPerformanceSignal() {
        return previousPerformanceSignal;
    }

    public boolean isComponentSpikeDetected() {
        return componentSpikeDetected;
    }

    public boolean isSevereComponentSpikeDetected() {
        return severeComponentSpikeDetected;
    }

    public boolean isCorrected() {
        return baseReuseMode != appliedReuseMode;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "PopulationReuseDecision{" +
                "baseReuseMode=" + baseReuseMode +
                ", appliedReuseMode=" + appliedReuseMode +
                ", previousPerformanceSignal=" + previousPerformanceSignal +
                ", componentSpikeDetected=" + componentSpikeDetected +
                ", severeComponentSpikeDetected=" + severeComponentSpikeDetected +
                ", reason='" + reason + '\'' +
                '}';
    }
}
