package window.population;

import config.window.TemporalWindowConfig;
import ga.core.MaGaResult;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import window.dynamicity.DynamicityBreakdown;

import java.util.List;
import java.util.Objects;

/**
 * Policy temporale per correggere la modalità di riuso della popolazione.
 *
 * <p>La dinamicità formalizzata resta il punto di partenza. La policy non
 * modifica il GA, la fitness o il cromosoma. Decide solo quanto riutilizzare
 * della popolazione precedente.</p>
 *
 * <p>Correzione principale: se una finestra mostra uno spike congiunto forte
 * su task e link, il sistema non resta più in PARTIAL_RESTART solo perché
 * D(k) aggregato è ancora sotto thetaHigh. In quel caso la popolazione
 * precedente è considerata poco rappresentativa.</p>
 */
public final class PopulationReuseDecisionPolicy {

    private static final double DEADLINE_RATE_GOOD_MAX = 0.03;
    private static final double DEADLINE_RATE_WARNING_MIN = 0.10;
    private static final double DEADLINE_RATE_BAD_MIN = 0.25;

    private static final double COVERAGE_RATE_WARNING_MIN = 0.02;
    private static final double COVERAGE_RATE_BAD_MIN = 0.05;

    private static final double SATURATION_THRESHOLD_PERCENT = 95.0;
    private static final int SATURATION_WARNING_COUNT = 8;

    private static final double TASK_SPIKE_THRESHOLD = 0.70;
    private static final double LINK_SPIKE_THRESHOLD = 0.75;
    private static final double RESOURCE_SPIKE_THRESHOLD = 0.65;
    private static final double VEHICLE_SPIKE_THRESHOLD = 0.55;

    /**
     * Soglia leggermente più prudente per riconoscere lo spike task+link.
     *
     * <p>Nel report calibrato la finestra 6 aveva Dt=0.726 e Dl=0.755.
     * Il valore aggregato D restava moderato, ma le componenti critiche
     * indicavano un cambio forte nella natura del problema.</p>
     */
    private static final double SEVERE_TASK_SPIKE_THRESHOLD = 0.70;
    private static final double SEVERE_LINK_SPIKE_THRESHOLD = 0.74;

    private static final double VERY_HIGH_SINGLE_COMPONENT_SPIKE = 0.85;
    private static final double MODERATE_LOW_DYNAMICITY_FOR_WARM = 0.42;

    private final TemporalWindowConfig config;

    public PopulationReuseDecisionPolicy() {
        this(TemporalWindowConfig.defaultConfig());
    }

    public PopulationReuseDecisionPolicy(TemporalWindowConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
    }

    public PopulationReuseDecision decide(
            DynamicityBreakdown dynamicityBreakdown,
            MaGaResult previousResult,
            boolean hasReusablePopulation,
            boolean criticalEventTrigger
    ) {
        Objects.requireNonNull(
                dynamicityBreakdown,
                "dynamicityBreakdown must not be null."
        );

        PopulationReuseMode baseMode = dynamicityBreakdown.getSuggestedReuseMode();
        WindowPerformanceSignal previousSignal = classifyPreviousPerformance(previousResult);
        boolean spike = hasComponentSpike(dynamicityBreakdown);
        boolean severeSpike = hasSevereComponentSpike(dynamicityBreakdown);
        boolean criticalTaskLinkSpike = hasCriticalTaskLinkSpike(dynamicityBreakdown);

        if (baseMode == PopulationReuseMode.FIRST_RUN) {
            return PopulationReuseDecision.unchanged(
                    baseMode,
                    WindowPerformanceSignal.UNKNOWN,
                    false,
                    false,
                    "First run: no previous population exists."
            );
        }

        if (!hasReusablePopulation) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.COLD_START,
                    previousSignal,
                    spike,
                    severeSpike,
                    "No reusable previous population is available."
            );
        }

        if (criticalEventTrigger) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.COLD_START,
                    previousSignal,
                    spike,
                    severeSpike,
                    "Critical event trigger: previous population is considered unreliable."
            );
        }

        if (baseMode == PopulationReuseMode.COLD_START) {
            return PopulationReuseDecision.unchanged(
                    baseMode,
                    previousSignal,
                    spike,
                    severeSpike,
                    "Global dynamicity is above thetaHigh."
            );
        }

        if (baseMode == PopulationReuseMode.WARM_START) {
            if (previousSignal == WindowPerformanceSignal.BAD) {
                return new PopulationReuseDecision(
                        baseMode,
                        PopulationReuseMode.PARTIAL_RESTART,
                        previousSignal,
                        spike,
                        severeSpike,
                        "Scenario is stable, but previous solution quality was bad."
                );
            }

            return PopulationReuseDecision.unchanged(
                    baseMode,
                    previousSignal,
                    spike,
                    severeSpike,
                    "Scenario is stable and previous population can be reused."
            );
        }

        /*
         * Caso principale: MODERATE -> PARTIAL_RESTART.
         * Qui si evita che il valore aggregato D(k) nasconda uno spike forte
         * nelle componenti più critiche per l'offloading: task e link.
         */
        if (criticalTaskLinkSpike) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.COLD_START,
                    previousSignal,
                    spike,
                    true,
                    "Critical task-link spike: Dt and Dl are both high, so the previous population is not reliable enough."
            );
        }

        if (previousSignal == WindowPerformanceSignal.BAD && spike) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.COLD_START,
                    previousSignal,
                    spike,
                    severeSpike,
                    "Previous solution quality was bad and at least one dynamicity component has a spike."
            );
        }

        if (previousSignal == WindowPerformanceSignal.BAD
                && dynamicityBreakdown.getGlobalDynamicity() >= midpointTheta()) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.COLD_START,
                    previousSignal,
                    spike,
                    severeSpike,
                    "Previous solution quality was bad and global dynamicity is above the midpoint threshold."
            );
        }

        if (severeSpike
                && previousSignal != WindowPerformanceSignal.GOOD
                && dynamicityBreakdown.getGlobalDynamicity() >= midpointTheta()) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.COLD_START,
                    previousSignal,
                    spike,
                    severeSpike,
                    "Severe component spike with non-good previous performance."
            );
        }

        if (previousSignal == WindowPerformanceSignal.GOOD
                && dynamicityBreakdown.getGlobalDynamicity() <= MODERATE_LOW_DYNAMICITY_FOR_WARM
                && !spike) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.WARM_START,
                    previousSignal,
                    spike,
                    severeSpike,
                    "Moderate-low dynamicity and previous solution quality was good."
            );
        }

        if (previousSignal == WindowPerformanceSignal.GOOD
                && dynamicityBreakdown.getGlobalDynamicity() < midpointTheta()
                && !spike) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.WARM_START,
                    previousSignal,
                    spike,
                    severeSpike,
                    "Previous solution quality was good and no component spike was detected."
            );
        }

        return PopulationReuseDecision.unchanged(
                baseMode,
                previousSignal,
                spike,
                severeSpike,
                "Partial restart confirmed: no critical task-link spike and no performance condition requires correction."
        );
    }

    private double midpointTheta() {
        return (config.getThetaLow() + config.getThetaHigh()) / 2.0;
    }

    private boolean hasComponentSpike(DynamicityBreakdown breakdown) {
        return breakdown.getVehicleVariation() >= VEHICLE_SPIKE_THRESHOLD
                || breakdown.getTaskVariation() >= TASK_SPIKE_THRESHOLD
                || breakdown.getResourceVariation() >= RESOURCE_SPIKE_THRESHOLD
                || breakdown.getLinkVariation() >= LINK_SPIKE_THRESHOLD;
    }

    private boolean hasCriticalTaskLinkSpike(DynamicityBreakdown breakdown) {
        return breakdown.getTaskVariation() >= SEVERE_TASK_SPIKE_THRESHOLD
                && breakdown.getLinkVariation() >= SEVERE_LINK_SPIKE_THRESHOLD;
    }

    private boolean hasSevereComponentSpike(DynamicityBreakdown breakdown) {
        boolean taskAndLinkSpike = hasCriticalTaskLinkSpike(breakdown);

        boolean resourceAndTaskSpike = breakdown.getResourceVariation() >= RESOURCE_SPIKE_THRESHOLD
                && breakdown.getTaskVariation() >= TASK_SPIKE_THRESHOLD;

        boolean veryHighSingleSpike = Math.max(
                Math.max(breakdown.getVehicleVariation(), breakdown.getTaskVariation()),
                Math.max(breakdown.getResourceVariation(), breakdown.getLinkVariation())
        ) >= VERY_HIGH_SINGLE_COMPONENT_SPIKE;

        return taskAndLinkSpike || resourceAndTaskSpike || veryHighSingleSpike;
    }

    private WindowPerformanceSignal classifyPreviousPerformance(MaGaResult previousResult) {
        if (previousResult == null || previousResult.getBestEvaluation() == null) {
            return WindowPerformanceSignal.UNKNOWN;
        }

        EvaluationBreakdown evaluation = previousResult.getBestEvaluation();
        List<?> geneBreakdowns = evaluation.getGeneBreakdowns();

        int totalGenes = geneBreakdowns == null ? 0 : geneBreakdowns.size();

        if (totalGenes <= 0) {
            return WindowPerformanceSignal.UNKNOWN;
        }

        int deadlineViolations = 0;
        int coverageInsufficient = 0;

        for (Object item : geneBreakdowns) {
            if (!(item instanceof GeneEvaluationBreakdown gene)) {
                continue;
            }

            if (!gene.isDeadlineRespected()) {
                deadlineViolations++;
            }

            if (!gene.isCoverageSufficient()) {
                coverageInsufficient++;
            }
        }

        double deadlineRate = (double) deadlineViolations / totalGenes;
        double coverageRate = (double) coverageInsufficient / totalGenes;

        int cpuViolations = countCpuViolations(evaluation.getExecutionNodeResourceUsageBreakdowns());
        int bandwidthViolations = countBandwidthViolations(evaluation.getLinkBandwidthUsageBreakdowns());
        int saturatedResources = countSaturatedResources(evaluation);

        if (cpuViolations > 0 || bandwidthViolations > 0) {
            return WindowPerformanceSignal.BAD;
        }

        if (deadlineRate >= DEADLINE_RATE_BAD_MIN || coverageRate >= COVERAGE_RATE_BAD_MIN) {
            return WindowPerformanceSignal.BAD;
        }

        if (deadlineRate >= DEADLINE_RATE_WARNING_MIN
                || coverageRate >= COVERAGE_RATE_WARNING_MIN
                || saturatedResources >= SATURATION_WARNING_COUNT) {
            return WindowPerformanceSignal.WARNING;
        }

        if (deadlineRate <= DEADLINE_RATE_GOOD_MAX && coverageInsufficient == 0) {
            return WindowPerformanceSignal.GOOD;
        }

        return WindowPerformanceSignal.WARNING;
    }

    private int countCpuViolations(List<?> usageBreakdowns) {
        if (usageBreakdowns == null) {
            return 0;
        }

        int count = 0;
        for (Object item : usageBreakdowns) {
            if (item instanceof ExecutionNodeResourceUsageBreakdown usage
                    && usage.hasCpuViolation()) {
                count++;
            }
        }
        return count;
    }

    private int countBandwidthViolations(List<?> usageBreakdowns) {
        if (usageBreakdowns == null) {
            return 0;
        }

        int count = 0;
        for (Object item : usageBreakdowns) {
            if (item instanceof LinkBandwidthUsageBreakdown usage
                    && usage.hasBandwidthViolation()) {
                count++;
            }
        }
        return count;
    }

    private int countSaturatedResources(EvaluationBreakdown evaluation) {
        int count = 0;

        List<?> cpuUsage = evaluation.getExecutionNodeResourceUsageBreakdowns();
        if (cpuUsage != null) {
            for (Object item : cpuUsage) {
                if (item instanceof ExecutionNodeResourceUsageBreakdown usage
                        && usage.isCpuSaturated(SATURATION_THRESHOLD_PERCENT)) {
                    count++;
                }
            }
        }

        List<?> bandwidthUsage = evaluation.getLinkBandwidthUsageBreakdowns();
        if (bandwidthUsage != null) {
            for (Object item : bandwidthUsage) {
                if (item instanceof LinkBandwidthUsageBreakdown usage
                        && usage.isBandwidthSaturated(SATURATION_THRESHOLD_PERCENT)) {
                    count++;
                }
            }
        }

        return count;
    }
}
