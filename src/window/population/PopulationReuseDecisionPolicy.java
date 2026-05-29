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
 * <p>La dinamicità formalizzata resta il punto di partenza:</p>
 *
 * <pre>
 * D(k) = lambdaVehicles Dv(k)
 *      + lambdaTasks Dt(k)
 *      + lambdaResources Dr(k)
 *      + lambdaLinks Dl(k)
 * </pre>
 *
 * <p>La policy non modifica il GA e non modifica la fitness. Decide soltanto se
 * la modalità suggerita dalla dinamicità deve essere confermata o corretta usando
 * due informazioni operative:</p>
 *
 * <ul>
 *     <li>la qualità della soluzione prodotta nella finestra precedente;</li>
 *     <li>la presenza di spike in singole componenti della dinamicità.</li>
 * </ul>
 *
 * <p>Obiettivo: evitare che il sistema resti sempre in {@code PARTIAL_RESTART}
 * anche quando sarebbe più coerente un {@code WARM_START} o un {@code COLD_START}.</p>
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

    private static final double MODERATE_LOW_DYNAMICITY_FOR_WARM = 0.42;
    private static final double MODERATE_HIGH_DYNAMICITY_FOR_COLD = 0.52;

    private final TemporalWindowConfig config;

    public PopulationReuseDecisionPolicy(TemporalWindowConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
    }

    /**
     * Decide la modalità effettiva di riuso della popolazione.
     *
     * @param dynamicityBreakdown dinamicità tra snapshot precedente e corrente
     * @param previousResult risultato MA-GA della finestra precedente
     * @param hasReusablePopulation true se esiste una popolazione precedente non vuota
     * @param criticalEventTrigger true se la riesecuzione è causata da evento critico
     * @return decisione finale, con motivazione diagnostica
     */
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

        if (baseMode == PopulationReuseMode.FIRST_RUN) {
            return PopulationReuseDecision.unchanged(
                    baseMode,
                    WindowPerformanceSignal.UNKNOWN,
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
                    "No reusable previous population is available."
            );
        }

        if (criticalEventTrigger) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.COLD_START,
                    previousSignal,
                    spike,
                    "Critical event trigger: previous population is considered unreliable."
            );
        }

        if (baseMode == PopulationReuseMode.COLD_START) {
            return PopulationReuseDecision.unchanged(
                    baseMode,
                    previousSignal,
                    spike,
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
                        "Scenario is stable, but previous solution quality was bad."
                );
            }

            return PopulationReuseDecision.unchanged(
                    baseMode,
                    previousSignal,
                    spike,
                    "Scenario is stable and previous population can be reused."
            );
        }

        /*
         * Da qui in poi il caso base è MODERATE -> PARTIAL_RESTART.
         * Qui si correggono i due casi osservati nei report:
         * - partial restart troppo conservativo quando lo scenario è moderato ma stabile in prestazioni;
         * - partial restart troppo permissivo quando una componente critica ha uno spike forte.
         */
        if (previousSignal == WindowPerformanceSignal.GOOD
                && dynamicityBreakdown.getGlobalDynamicity() <= MODERATE_LOW_DYNAMICITY_FOR_WARM
                && !severeSpike) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.WARM_START,
                    previousSignal,
                    spike,
                    "Moderate-low dynamicity and previous solution quality was good."
            );
        }

        if (severeSpike
                && dynamicityBreakdown.getGlobalDynamicity() >= MODERATE_HIGH_DYNAMICITY_FOR_COLD) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.COLD_START,
                    previousSignal,
                    spike,
                    "Severe component spike with high-moderate global dynamicity."
            );
        }

        if (previousSignal == WindowPerformanceSignal.BAD
                && (spike || dynamicityBreakdown.getGlobalDynamicity() >= midpointTheta())) {
            return new PopulationReuseDecision(
                    baseMode,
                    PopulationReuseMode.COLD_START,
                    previousSignal,
                    spike,
                    "Previous solution quality was bad and the current scenario changed significantly."
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
                    "Previous solution quality was good and no component spike was detected."
            );
        }

        return PopulationReuseDecision.unchanged(
                baseMode,
                previousSignal,
                spike,
                "Partial restart confirmed: dynamicity or previous performance does not justify warm/cold correction."
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

    private boolean hasSevereComponentSpike(DynamicityBreakdown breakdown) {
        boolean taskAndLinkSpike = breakdown.getTaskVariation() >= TASK_SPIKE_THRESHOLD
                && breakdown.getLinkVariation() >= LINK_SPIKE_THRESHOLD;

        boolean resourceAndTaskSpike = breakdown.getResourceVariation() >= RESOURCE_SPIKE_THRESHOLD
                && breakdown.getTaskVariation() >= TASK_SPIKE_THRESHOLD;

        boolean veryHighSingleSpike = Math.max(
                Math.max(breakdown.getVehicleVariation(), breakdown.getTaskVariation()),
                Math.max(breakdown.getResourceVariation(), breakdown.getLinkVariation())
        ) >= 0.85;

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
