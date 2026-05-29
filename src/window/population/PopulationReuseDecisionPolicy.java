package window.population;

import ga.core.MaGaResult;
import window.dynamicity.DynamicityBreakdown;

import java.util.Objects;

/**
 * Policy che corregge la modalità di riuso suggerita dalla sola dinamicità.
 *
 * <p>La dinamicità resta il segnale principale. La policy evita però che
 * {@code PARTIAL_RESTART} diventi la scelta quasi automatica quando singole
 * componenti cambiano molto o quando la finestra precedente era debole.</p>
 */
public final class PopulationReuseDecisionPolicy {

    private static final double COMPONENT_SPIKE_THRESHOLD = 0.65;
    private static final double SEVERE_TASK_SPIKE_THRESHOLD = 0.70;
    private static final double SEVERE_LINK_SPIKE_THRESHOLD = 0.75;

    public PopulationReuseDecision decide(
            DynamicityBreakdown dynamicityBreakdown,
            MaGaResult previousResult,
            boolean hasReusablePopulation,
            boolean criticalEvent
    ) {
        Objects.requireNonNull(
                dynamicityBreakdown,
                "dynamicityBreakdown must not be null."
        );

        PopulationReuseMode suggested = dynamicityBreakdown.getSuggestedReuseMode();
        WindowPerformanceSignal signal = WindowPerformanceSignal.from(previousResult);

        boolean componentSpike = hasComponentSpike(dynamicityBreakdown);
        boolean severeSpike = hasSevereComponentSpike(dynamicityBreakdown);

        if (suggested == PopulationReuseMode.FIRST_RUN) {
            return decision(
                    suggested,
                    PopulationReuseMode.FIRST_RUN,
                    signal,
                    componentSpike,
                    severeSpike,
                    "First execution: no previous population exists."
            );
        }

        if (criticalEvent) {
            return decision(
                    suggested,
                    PopulationReuseMode.COLD_START,
                    signal,
                    componentSpike,
                    severeSpike,
                    "Critical event: discard previous population."
            );
        }

        if (!hasReusablePopulation) {
            return decision(
                    suggested,
                    PopulationReuseMode.COLD_START,
                    signal,
                    componentSpike,
                    severeSpike,
                    "No reusable population is available."
            );
        }

        if (suggested == PopulationReuseMode.COLD_START) {
            return decision(
                    suggested,
                    PopulationReuseMode.COLD_START,
                    signal,
                    componentSpike,
                    severeSpike,
                    "Dynamicity evaluator requested cold start."
            );
        }

        if (severeSpike && signal.isBadOrWarning()) {
            return decision(
                    suggested,
                    PopulationReuseMode.COLD_START,
                    signal,
                    componentSpike,
                    severeSpike,
                    "Severe component spike and weak previous performance."
            );
        }

        if (suggested == PopulationReuseMode.PARTIAL_RESTART
                && signal.isGood()
                && !componentSpike) {
            return decision(
                    suggested,
                    PopulationReuseMode.WARM_START,
                    signal,
                    componentSpike,
                    severeSpike,
                    "Moderate global dynamicity, good previous performance and no spike."
            );
        }

        return decision(
                suggested,
                suggested,
                signal,
                componentSpike,
                severeSpike,
                "Use the reuse mode suggested by dynamicity."
        );
    }

    private boolean hasComponentSpike(DynamicityBreakdown breakdown) {
        return breakdown.getVehicleVariation() >= COMPONENT_SPIKE_THRESHOLD
                || breakdown.getTaskVariation() >= COMPONENT_SPIKE_THRESHOLD
                || breakdown.getResourceVariation() >= COMPONENT_SPIKE_THRESHOLD
                || breakdown.getLinkVariation() >= COMPONENT_SPIKE_THRESHOLD;
    }

    private boolean hasSevereComponentSpike(DynamicityBreakdown breakdown) {
        return breakdown.getTaskVariation() >= SEVERE_TASK_SPIKE_THRESHOLD
                || breakdown.getLinkVariation() >= SEVERE_LINK_SPIKE_THRESHOLD;
    }

    private PopulationReuseDecision decision(
            PopulationReuseMode suggested,
            PopulationReuseMode applied,
            WindowPerformanceSignal signal,
            boolean componentSpike,
            boolean severeSpike,
            String reason
    ) {
        return new PopulationReuseDecision(
                suggested,
                applied,
                signal,
                componentSpike,
                severeSpike,
                reason
        );
    }
}
