package io.reporting;

import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import model.node.NodeType;
import window.dynamicity.DynamicityBreakdown;
import window.population.PopulationReuseMode;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Printer diagnostico per stress test eseguiti tramite TemporalWindowManager.
 *
 * Questa classe non esegue il MA-GA e non gestisce gli snapshot.
 * Legge un TemporalWindowResult già prodotto dal package window e produce
 * una vista compatta ma sufficientemente informativa per capire:
 *
 * - andamento temporale della fitness;
 * - trigger e tempi di osservazione;
 * - dinamicità e modalità di riuso;
 * - effetto del warm start / partial restart / cold start;
 * - deadline violate;
 * - saturazioni e violazioni CPU/banda;
 * - mobilità e copertura;
 * - finestre peggiori.
 */
public final class TemporalWindowStressPrinter {

    private static final double SATURATION_THRESHOLD_PERCENT = 95.0;
    private static final int TOP_LIMIT = 5;

    private final PrintStream out;

    public TemporalWindowStressPrinter() {
        this(System.out);
    }

    public TemporalWindowStressPrinter(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    /**
     * Stampa il report completo del risultato temporale.
     *
     * @param result risultato prodotto da TemporalWindowManager.run(...)
     */
    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        List<TemporalStepResult> steps = result.getSteps();

        printHeader();
        printGlobalSummary(result);
        printTemporalTable(steps);
        printDynamicityAndReuseTrend(steps);
        printDecisionTrend(steps);
        printDeadlineTrend(steps);
        printResourceTrend(steps);
        printMobilityTrend(steps);
        printWorstWindows(steps);
        printInterpretationHints(steps);
    }

    private void printHeader() {
        out.println("============================================================");
        out.println("MA-GA TEMPORAL WINDOW STRESS REPORT");
        out.println("============================================================");
        out.println();
    }

    private void printGlobalSummary(TemporalWindowResult result) {
        printSection("1. GLOBAL TEMPORAL SUMMARY");

        List<TemporalStepResult> steps = result.getSteps();

        if (steps.isEmpty()) {
            out.println("No temporal step executed.");
            out.println();
            return;
        }

        TemporalStepResult first = steps.get(0);
        TemporalStepResult last = steps.get(steps.size() - 1);

        int totalTasks = 0;
        int totalDeadlineViolations = 0;
        int totalCpuViolations = 0;
        int totalBandwidthViolations = 0;
        int totalCoverageInsufficient = 0;

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);

            totalTasks += stats.taskCount;
            totalDeadlineViolations += stats.deadlineViolationCount;
            totalCpuViolations += stats.cpuViolationCount;
            totalBandwidthViolations += stats.bandwidthViolationCount;
            totalCoverageInsufficient += stats.coverageInsufficientCount;
        }

        out.println("Executed windows: " + result.getStepCount());
        out.println("Critical-event windows: " + result.countCriticalEventSteps());
        out.println("Population-reuse windows: " + result.countPopulationReuseSteps());
        out.println("First observation time: " + formatSeconds(first.getObservationTimeSeconds()));
        out.println("Last observation time: " + formatSeconds(last.getObservationTimeSeconds()));
        out.println("Best final fitness in sequence: "
                + format(result.getBestFinalFitness().orElse(Double.NaN)));

        out.println();
        out.println("Aggregated violations:");
        out.println("- total task evaluations: " + totalTasks);
        out.println("- deadline violations: " + totalDeadlineViolations);
        out.println("- CPU violations: " + totalCpuViolations);
        out.println("- bandwidth violations: " + totalBandwidthViolations);
        out.println("- coverage insufficient: " + totalCoverageInsufficient);
        out.println();
    }

    private void printTemporalTable(List<TemporalStepResult> steps) {
        printSection("2. TEMPORAL WINDOW SUMMARY");

        out.println("idx | trigger | triggerTime | obsTime | snapshot | initPop | finalPop | reuse | gen | stop | initBest | finalBest | gain% | T | L | Pmob | Pres");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);

            out.println(step.getWindowIndex()
                    + " | " + triggerLabel(step)
                    + " | " + formatSeconds(step.getTriggerTimeSeconds())
                    + " | " + formatSeconds(step.getObservationTimeSeconds())
                    + " | " + step.getSnapshot().getSnapshotId()
                    + " | " + step.getInitialPopulationSize()
                    + " | " + step.getFinalPopulationSize()
                    + " | " + step.getReuseMode()
                    + " | " + step.getMaGaResult().getGenerationsExecuted()
                    + " | " + step.getMaGaResult().getStopReason()
                    + " | " + format(step.getMaGaResult().getInitialBestFitness())
                    + " | " + format(step.getMaGaResult().getFinalBestFitness())
                    + " | " + formatPercent(stats.improvementRatio)
                    + " | " + formatSeconds(stats.completionTimeSeconds)
                    + " | " + formatSeconds(stats.communicationLatencySeconds)
                    + " | " + format(stats.mobilityPenalty)
                    + " | " + format(stats.resourcePenalty));
        }

        out.println();
    }

    private void printDynamicityAndReuseTrend(List<TemporalStepResult> steps) {
        printSection("3. DYNAMICITY AND REUSE TREND");

        out.println("idx | dynLevel | D | Dv | Dt | Dr | Dl | suggested | applied | reusedPrevious | initPop");

        for (TemporalStepResult step : steps) {
            DynamicityBreakdown d = step.getDynamicityBreakdown();

            out.println(step.getWindowIndex()
                    + " | " + d.getDynamicityLevel()
                    + " | " + format(d.getGlobalDynamicity())
                    + " | " + format(d.getVehicleVariation())
                    + " | " + format(d.getTaskVariation())
                    + " | " + format(d.getResourceVariation())
                    + " | " + format(d.getLinkVariation())
                    + " | " + d.getSuggestedReuseMode()
                    + " | " + step.getReuseMode()
                    + " | " + step.reusedPreviousPopulation()
                    + " | " + step.getInitialPopulationSize());
        }

        out.println();
    }

    private void printDecisionTrend(List<TemporalStepResult> steps) {
        printSection("4. DECISION TREND");

        out.println("idx | LOCAL | EDGE | CLOUD | VEHICLE | avgOffRatio | partial | full | localExec");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);

            out.println(step.getWindowIndex()
                    + " | " + stats.nodeTypeCounts.getOrDefault(NodeType.LOCAL, 0)
                    + " | " + stats.nodeTypeCounts.getOrDefault(NodeType.EDGE, 0)
                    + " | " + stats.nodeTypeCounts.getOrDefault(NodeType.CLOUD, 0)
                    + " | " + stats.nodeTypeCounts.getOrDefault(NodeType.VEHICLE, 0)
                    + " | " + format(stats.averageOffloadingRatio)
                    + " | " + stats.partialOffloadingCount
                    + " | " + stats.fullOffloadingCount
                    + " | " + stats.localExecutionCount);
        }

        out.println();
    }

    private void printDeadlineTrend(List<TemporalStepResult> steps) {
        printSection("5. DEADLINE TREND");

        out.println("idx | tasks | respected | violated | violationRate | worstTask | completion | deadline | ratio");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);

            if (stats.worstDeadlineViolation == null) {
                out.println(step.getWindowIndex()
                        + " | " + stats.taskCount
                        + " | " + stats.taskCount
                        + " | 0 | 0.000000% | none | - | - | -");
                continue;
            }

            GeneEvaluationBreakdown worst = stats.worstDeadlineViolation;

            out.println(step.getWindowIndex()
                    + " | " + stats.taskCount
                    + " | " + (stats.taskCount - stats.deadlineViolationCount)
                    + " | " + stats.deadlineViolationCount
                    + " | " + formatPercent(stats.deadlineViolationRate)
                    + " | " + worst.getTaskId()
                    + " | " + formatSeconds(worst.getCompletionTimeSeconds())
                    + " | " + formatSeconds(worst.getDeadlineSeconds())
                    + " | " + format(deadlineViolationRatio(worst)));
        }

        out.println();
    }

    private void printResourceTrend(List<TemporalStepResult> steps) {
        printSection("6. RESOURCE TREND");

        out.println("idx | CPUviol | CPUsat>=95 | BWviol | BWsat>=95 | worstCpuNode | worstCpu% | worstBwLink | worstBw%");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);

            out.println(step.getWindowIndex()
                    + " | " + stats.cpuViolationCount
                    + " | " + stats.cpuSaturationCount
                    + " | " + stats.bandwidthViolationCount
                    + " | " + stats.bandwidthSaturationCount
                    + " | " + (stats.worstCpuUsage == null ? "none" : stats.worstCpuUsage.getExecutionNodeId())
                    + " | " + (stats.worstCpuUsage == null ? "-" : format(stats.worstCpuUsage.getCpuUsagePercent()) + "%")
                    + " | " + (stats.worstBandwidthUsage == null ? "none" : stats.worstBandwidthUsage.getCandidateId())
                    + " | " + (stats.worstBandwidthUsage == null ? "-" : format(stats.worstBandwidthUsage.getBandwidthUsagePercent()) + "%"));
        }

        out.println();
    }

    private void printMobilityTrend(List<TemporalStepResult> steps) {
        printSection("7. MOBILITY TREND");

        out.println("idx | Pmob | avgPmob | coverageInsuff | nearCritical | worstMobTask | worstPmob | coverage | completion");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);

            GeneEvaluationBreakdown worst = stats.worstMobilityPenalty;

            out.println(step.getWindowIndex()
                    + " | " + format(stats.mobilityPenalty)
                    + " | " + format(stats.averageMobilityPenalty)
                    + " | " + stats.coverageInsufficientCount
                    + " | " + stats.nearCriticalCoverageCount
                    + " | " + (worst == null ? "none" : worst.getTaskId())
                    + " | " + (worst == null ? "-" : format(worst.getMobilityPenalty()))
                    + " | " + (worst == null ? "-" : formatSeconds(worst.getCoverageTimeSeconds()))
                    + " | " + (worst == null ? "-" : formatSeconds(worst.getCompletionTimeSeconds())));
        }

        out.println();
    }

    private void printWorstWindows(List<TemporalStepResult> steps) {
        printSection("8. WORST WINDOWS");

        List<TemporalStepResult> byFitness = new ArrayList<>(steps);

        byFitness.sort(
                Comparator
                        .comparingDouble(
                                (TemporalStepResult step) ->
                                        step.getMaGaResult().getFinalBestFitness()
                        )
                        .reversed()
        );

        out.println("Worst windows by final fitness:");
        for (TemporalStepResult step : limit(byFitness, TOP_LIMIT)) {
            StepStats stats = StepStats.from(step);

            out.println("- idx=" + step.getWindowIndex()
                    + " snapshot=" + step.getSnapshot().getSnapshotId()
                    + " finalFitness="
                    + format(step.getMaGaResult().getFinalBestFitness())
                    + " D="
                    + format(step.getDynamicityBreakdown().getGlobalDynamicity())
                    + " reuse=" + step.getReuseMode()
                    + " Pres=" + format(stats.resourcePenalty)
                    + " deadlineViol=" + stats.deadlineViolationCount
                    + " cpuViol=" + stats.cpuViolationCount);
        }

        out.println();

        List<TemporalStepResult> byDynamicity = new ArrayList<>(steps);

        byDynamicity.sort(
                Comparator
                        .comparingDouble(
                                (TemporalStepResult step) ->
                                        step.getDynamicityBreakdown()
                                                .getGlobalDynamicity()
                        )
                        .reversed()
        );

        out.println("Most dynamic windows:");
        for (TemporalStepResult step : limit(byDynamicity, TOP_LIMIT)) {
            DynamicityBreakdown d = step.getDynamicityBreakdown();

            out.println("- idx=" + step.getWindowIndex()
                    + " snapshot=" + step.getSnapshot().getSnapshotId()
                    + " D=" + format(d.getGlobalDynamicity())
                    + " level=" + d.getDynamicityLevel()
                    + " reuse=" + step.getReuseMode()
                    + " Dv=" + format(d.getVehicleVariation())
                    + " Dt=" + format(d.getTaskVariation())
                    + " Dr=" + format(d.getResourceVariation())
                    + " Dl=" + format(d.getLinkVariation()));
        }

        out.println();
    }

    private void printInterpretationHints(List<TemporalStepResult> steps) {
        printSection("9. INTERPRETATION HINTS");

        boolean hasCritical = steps.stream().anyMatch(TemporalStepResult::wasTriggeredByCriticalEvent);
        boolean hasCpuViolation = steps.stream().anyMatch(step -> StepStats.from(step).cpuViolationCount > 0);
        boolean hasBandwidthViolation = steps.stream().anyMatch(step -> StepStats.from(step).bandwidthViolationCount > 0);
        boolean hasDeadlineViolation = steps.stream().anyMatch(step -> StepStats.from(step).deadlineViolationCount > 0);
        boolean hasNoReuseAfterFirst = steps.stream()
                .filter(step -> step.getWindowIndex() > 0)
                .noneMatch(TemporalStepResult::reusedPreviousPopulation);

        out.println("- This report is connected to TemporalWindowManager.");
        out.println("- Trigger, observation time, dynamicity and reuse mode are read from TemporalStepResult.");
        out.println("- Static stress means scheduled windows are used unless critical events are configured.");
        out.println("- Bandwidth repair is intentionally still an OpenIssue.");
        out.println("- CPU aggregate repair is recommended if CPU violations persist.");

        out.println();

        if (!hasCritical) {
            out.println("DIAGNOSIS: no critical events were used. The run is driven by scheduled window expiration.");
        }

        if (hasNoReuseAfterFirst && steps.size() > 1) {
            out.println("DIAGNOSIS: no population reuse after the first step.");
            out.println("Action: inspect DynamicityBreakdown and thresholds thetaLow/thetaHigh.");
        }

        if (hasDeadlineViolation) {
            out.println("DIAGNOSIS: deadline violations occur in at least one window.");
            out.println("Action: compare deadline trend with CPU saturation and reuse mode.");
        }

        if (hasCpuViolation) {
            out.println("DIAGNOSIS: CPU violations occur in at least one window.");
            out.println("Action: introduce aggregate CPU repair in the GA repair pipeline.");
        }

        if (hasBandwidthViolation) {
            out.println("DIAGNOSIS: bandwidth violations occur in at least one window.");
            out.println("Action: keep as OpenIssue or introduce a later bandwidth repair policy.");
        }

        out.println();
    }

    private String triggerLabel(TemporalStepResult step) {
        if (step.wasTriggeredByCriticalEvent()) {
            return "CRITICAL_EVENT";
        }

        if (step.getWindowIndex() == 0) {
            return "FIRST_RUN";
        }

        return "SCHEDULED";
    }

    private static <T> List<T> limit(List<T> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }

        return values.subList(0, limit);
    }

    private void printSection(String title) {
        out.println("------------------------------------------------------------");
        out.println(title);
        out.println("------------------------------------------------------------");
    }

    private String format(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }

        if (Double.isInfinite(value)) {
            return "Infinity";
        }

        return String.format("%.6f", value);
    }

    private String formatSeconds(double value) {
        return format(value) + " s";
    }

    private String formatPercent(double value) {
        return format(value * 100.0) + "%";
    }

    private static double deadlineViolationRatio(GeneEvaluationBreakdown gene) {
        if (gene.getDeadlineSeconds() <= 0.0) {
            return 0.0;
        }

        return Math.max(
                0.0,
                (gene.getCompletionTimeSeconds() - gene.getDeadlineSeconds())
                        / gene.getDeadlineSeconds()
        );
    }

    private static double coverageRatio(GeneEvaluationBreakdown gene) {
        if (gene.getCompletionTimeSeconds() <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }

        return gene.getCoverageTimeSeconds()
                / gene.getCompletionTimeSeconds();
    }

    /**
     * Statistiche derivate da una finestra.
     */
    private static final class StepStats {

        private static final double NEAR_COVERAGE_RATIO = 1.25;

        private final int taskCount;
        private final Map<NodeType, Integer> nodeTypeCounts;
        private final int localExecutionCount;
        private final int partialOffloadingCount;
        private final int fullOffloadingCount;
        private final double averageOffloadingRatio;

        private final double completionTimeSeconds;
        private final double communicationLatencySeconds;
        private final double mobilityPenalty;
        private final double resourcePenalty;
        private final double averageMobilityPenalty;

        private final int deadlineViolationCount;
        private final double deadlineViolationRate;
        private final GeneEvaluationBreakdown worstDeadlineViolation;

        private final int coverageInsufficientCount;
        private final int nearCriticalCoverageCount;
        private final GeneEvaluationBreakdown worstMobilityPenalty;

        private final int cpuViolationCount;
        private final int cpuSaturationCount;
        private final ExecutionNodeResourceUsageBreakdown worstCpuUsage;

        private final int bandwidthViolationCount;
        private final int bandwidthSaturationCount;
        private final LinkBandwidthUsageBreakdown worstBandwidthUsage;

        private final double improvementRatio;

        private StepStats(
                int taskCount,
                Map<NodeType, Integer> nodeTypeCounts,
                int localExecutionCount,
                int partialOffloadingCount,
                int fullOffloadingCount,
                double averageOffloadingRatio,
                double completionTimeSeconds,
                double communicationLatencySeconds,
                double mobilityPenalty,
                double resourcePenalty,
                double averageMobilityPenalty,
                int deadlineViolationCount,
                double deadlineViolationRate,
                GeneEvaluationBreakdown worstDeadlineViolation,
                int coverageInsufficientCount,
                int nearCriticalCoverageCount,
                GeneEvaluationBreakdown worstMobilityPenalty,
                int cpuViolationCount,
                int cpuSaturationCount,
                ExecutionNodeResourceUsageBreakdown worstCpuUsage,
                int bandwidthViolationCount,
                int bandwidthSaturationCount,
                LinkBandwidthUsageBreakdown worstBandwidthUsage,
                double improvementRatio
        ) {
            this.taskCount = taskCount;
            this.nodeTypeCounts = nodeTypeCounts;
            this.localExecutionCount = localExecutionCount;
            this.partialOffloadingCount = partialOffloadingCount;
            this.fullOffloadingCount = fullOffloadingCount;
            this.averageOffloadingRatio = averageOffloadingRatio;
            this.completionTimeSeconds = completionTimeSeconds;
            this.communicationLatencySeconds = communicationLatencySeconds;
            this.mobilityPenalty = mobilityPenalty;
            this.resourcePenalty = resourcePenalty;
            this.averageMobilityPenalty = averageMobilityPenalty;
            this.deadlineViolationCount = deadlineViolationCount;
            this.deadlineViolationRate = deadlineViolationRate;
            this.worstDeadlineViolation = worstDeadlineViolation;
            this.coverageInsufficientCount = coverageInsufficientCount;
            this.nearCriticalCoverageCount = nearCriticalCoverageCount;
            this.worstMobilityPenalty = worstMobilityPenalty;
            this.cpuViolationCount = cpuViolationCount;
            this.cpuSaturationCount = cpuSaturationCount;
            this.worstCpuUsage = worstCpuUsage;
            this.bandwidthViolationCount = bandwidthViolationCount;
            this.bandwidthSaturationCount = bandwidthSaturationCount;
            this.worstBandwidthUsage = worstBandwidthUsage;
            this.improvementRatio = improvementRatio;
        }

        private static StepStats from(TemporalStepResult step) {
            EvaluationBreakdown e = step.getMaGaResult().getBestEvaluation();
            List<GeneEvaluationBreakdown> genes = e.getGeneBreakdowns();

            Map<NodeType, Integer> nodeTypeCounts = new EnumMap<>(NodeType.class);
            int localExecution = 0;
            int partial = 0;
            int full = 0;
            double offloadSum = 0.0;
            double mobilitySum = 0.0;
            int deadlineViolations = 0;
            int coverageInsufficient = 0;
            int nearCritical = 0;

            GeneEvaluationBreakdown worstDeadline = null;
            GeneEvaluationBreakdown worstMobility = null;

            for (GeneEvaluationBreakdown gene : genes) {
                nodeTypeCounts.merge(gene.getNodeType(), 1, Integer::sum);
                offloadSum += gene.getOffloadingRatio();
                mobilitySum += gene.getMobilityPenalty();

                switch (gene.getDecisionType()) {
                    case LOCAL_EXECUTION -> localExecution++;
                    case PARTIAL_OFFLOADING -> partial++;
                    case FULL_OFFLOADING -> full++;
                    default -> {
                        // Non fa nulla: eventuali enum futuri restano conteggiati nel tipo nodo.
                    }
                }

                if (!gene.isDeadlineRespected()) {
                    deadlineViolations++;
                    if (worstDeadline == null
                            || deadlineViolationRatio(gene)
                            > deadlineViolationRatio(worstDeadline)) {
                        worstDeadline = gene;
                    }
                }

                if (gene.getNodeType() != NodeType.LOCAL) {
                    if (!gene.isCoverageSufficient()) {
                        coverageInsufficient++;
                    } else if (coverageRatio(gene) <= NEAR_COVERAGE_RATIO) {
                        nearCritical++;
                    }
                }

                if (gene.getMobilityPenalty() > 0.0
                        && (worstMobility == null
                        || gene.getMobilityPenalty()
                        > worstMobility.getMobilityPenalty())) {
                    worstMobility = gene;
                }
            }

            int cpuViolations = 0;
            int cpuSaturations = 0;
            ExecutionNodeResourceUsageBreakdown worstCpu = null;

            for (ExecutionNodeResourceUsageBreakdown usage
                    : e.getExecutionNodeResourceUsageBreakdowns()) {
                if (usage.hasCpuViolation()) {
                    cpuViolations++;
                } else if (usage.getCpuUsagePercent()
                        >= SATURATION_THRESHOLD_PERCENT) {
                    cpuSaturations++;
                }

                if (worstCpu == null
                        || usage.getCpuUsagePercent()
                        > worstCpu.getCpuUsagePercent()) {
                    worstCpu = usage;
                }
            }

            int bandwidthViolations = 0;
            int bandwidthSaturations = 0;
            LinkBandwidthUsageBreakdown worstBandwidth = null;

            for (LinkBandwidthUsageBreakdown usage
                    : e.getLinkBandwidthUsageBreakdowns()) {
                if (usage.hasBandwidthViolation()) {
                    bandwidthViolations++;
                } else if (usage.getBandwidthUsagePercent()
                        >= SATURATION_THRESHOLD_PERCENT) {
                    bandwidthSaturations++;
                }

                if (worstBandwidth == null
                        || usage.getBandwidthUsagePercent()
                        > worstBandwidth.getBandwidthUsagePercent()) {
                    worstBandwidth = usage;
                }
            }

            double improvementRatio = step.getMaGaResult().getInitialBestFitness() == 0.0
                    ? 0.0
                    : (step.getMaGaResult().getInitialBestFitness()
                    - step.getMaGaResult().getFinalBestFitness())
                    / step.getMaGaResult().getInitialBestFitness();

            return new StepStats(
                    genes.size(),
                    nodeTypeCounts,
                    localExecution,
                    partial,
                    full,
                    genes.isEmpty() ? 0.0 : offloadSum / genes.size(),
                    e.getCompletionTimeSeconds(),
                    e.getCommunicationLatencySeconds(),
                    e.getMobilityPenalty(),
                    e.getResourcePenalty(),
                    genes.isEmpty() ? 0.0 : mobilitySum / genes.size(),
                    deadlineViolations,
                    genes.isEmpty() ? 0.0 : (double) deadlineViolations / genes.size(),
                    worstDeadline,
                    coverageInsufficient,
                    nearCritical,
                    worstMobility,
                    cpuViolations,
                    cpuSaturations,
                    worstCpu,
                    bandwidthViolations,
                    bandwidthSaturations,
                    worstBandwidth,
                    improvementRatio
            );
        }
    }
}
