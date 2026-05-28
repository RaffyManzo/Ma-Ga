package io.reporting;

import config.MaGaConfig;
import config.ga.GaParameterScalingResult;
import config.ga.GeneticAlgorithmConfig;
import ga.core.GenerationStat;
import ga.fitness.DecisionType;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import ga.fitness.breakdown.LocalResourceUsageBreakdown;
import model.node.NodeType;
import window.dynamicity.DynamicityBreakdown;
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
 * Printer diagnostico esteso per stress test temporali MA-GA.
 *
 * Questo printer è volutamente dettagliato. Non nasce per produrre un report
 * breve, ma per capire da dove nascono i problemi del GA in una sequenza
 * temporale gestita dal TemporalWindowManager.
 *
 * Stampa informazioni provenienti da:
 *
 * - TemporalWindowResult;
 * - TemporalStepResult;
 * - DynamicityBreakdown;
 * - MaGaResult;
 * - GenerationStat;
 * - EvaluationBreakdown;
 * - GeneEvaluationBreakdown;
 * - resource usage breakdown.
 *
 * Uso consigliato:
 *
 * DeepTemporalWindowStressPrinter printer =
 *         new DeepTemporalWindowStressPrinter(config);
 *
 * printer.print(result);
 */
public final class DeepTemporalWindowStressPrinter {

    private static final double SATURATION_THRESHOLD_PERCENT = 95.0;
    private static final int DEFAULT_TOP_LIMIT = 10;

    private final MaGaConfig config;
    private final PrintStream out;
    private final int topLimit;
    private final boolean printAllGeneRows;
    private final boolean printAllResourceRows;
    private final boolean printGenerationHistory;

    /**
     * Costruttore senza configurazione.
     *
     * Non stampa lo scaling GA perché non ha accesso a MaGaConfig.
     */
    public DeepTemporalWindowStressPrinter() {
        this(null, System.out, DEFAULT_TOP_LIMIT, true, true, true);
    }

    /**
     * Costruttore consigliato.
     *
     * @param config configurazione MA-GA usata nel test
     */
    public DeepTemporalWindowStressPrinter(MaGaConfig config) {
        this(config, System.out, DEFAULT_TOP_LIMIT, true, true, true);
    }

    /**
     * Costruttore completo.
     *
     * @param config configurazione MA-GA, può essere null
     * @param out stream di output
     * @param topLimit numero massimo per le sezioni top-N
     * @param printAllGeneRows true per stampare ogni gene/task
     * @param printAllResourceRows true per stampare ogni risorsa
     * @param printGenerationHistory true per stampare andamento generazionale
     */
    public DeepTemporalWindowStressPrinter(
            MaGaConfig config,
            PrintStream out,
            int topLimit,
            boolean printAllGeneRows,
            boolean printAllResourceRows,
            boolean printGenerationHistory
    ) {
        this.config = config;
        this.out = Objects.requireNonNull(out, "out must not be null.");

        if (topLimit <= 0) {
            throw new IllegalArgumentException("topLimit must be > 0.");
        }

        this.topLimit = topLimit;
        this.printAllGeneRows = printAllGeneRows;
        this.printAllResourceRows = printAllResourceRows;
        this.printGenerationHistory = printGenerationHistory;
    }

    /**
     * Stampa il report diagnostico completo.
     *
     * @param result risultato temporale prodotto da TemporalWindowManager
     */
    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        List<TemporalStepResult> steps = result.getSteps();

        printHeader();
        printGlobalSummary(result);
        printTemporalSummaryTable(steps);
        printDynamicityTable(steps);
        printGaScalingTable(steps);
        printGenerationDiagnostics(steps);
        printFitnessTable(steps);
        printDecisionTable(steps);
        printDeadlineTable(steps);
        printMobilityTable(steps);
        printResourceTable(steps);
        printWorstWindowSummary(steps);
        printDeepStepDetails(steps);
        printFinalDiagnosis(steps);
    }

    private void printHeader() {
        out.println("============================================================");
        out.println("MA-GA DEEP TEMPORAL WINDOW STRESS REPORT");
        out.println("============================================================");
        out.println();
    }

    private void printGlobalSummary(TemporalWindowResult result) {
        printSection("1. GLOBAL RESULT SUMMARY");

        List<TemporalStepResult> steps = result.getSteps();

        if (steps.isEmpty()) {
            out.println("No temporal steps available.");
            out.println();
            return;
        }

        AggregatedStats aggregated = AggregatedStats.from(steps);

        out.println("Executed windows: " + result.getStepCount());
        out.println("Critical-event windows: " + result.countCriticalEventSteps());
        out.println("Population-reuse windows: " + result.countPopulationReuseSteps());
        out.println("First observation time: "
                + formatSeconds(steps.get(0).getObservationTimeSeconds()));
        out.println("Last observation time: "
                + formatSeconds(steps.get(steps.size() - 1).getObservationTimeSeconds()));
        out.println("Best final fitness: "
                + format(result.getBestFinalFitness().orElse(Double.NaN)));

        out.println();
        out.println("Aggregated task/resource status:");
        out.println("- task evaluations: " + aggregated.taskEvaluations);
        out.println("- deadline violations: " + aggregated.deadlineViolations);
        out.println("- CPU violations: " + aggregated.cpuViolations);
        out.println("- bandwidth violations: " + aggregated.bandwidthViolations);
        out.println("- coverage insufficient: " + aggregated.coverageInsufficient);
        out.println("- CPU saturated >= " + format(SATURATION_THRESHOLD_PERCENT)
                + "%: " + aggregated.cpuSaturated);
        out.println("- bandwidth saturated >= "
                + format(SATURATION_THRESHOLD_PERCENT)
                + "%: " + aggregated.bandwidthSaturated);
        out.println();
    }

    private void printTemporalSummaryTable(List<TemporalStepResult> steps) {
        printSection("2. TEMPORAL STEP SUMMARY");

        out.println("idx | trigger | trigTime | obsTime | snapshot | reuse | reusedPrev | initPop | finalPop | gen | stop | initBest | finalBest | gain%");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);

            out.println(step.getWindowIndex()
                    + " | " + triggerLabel(step)
                    + " | " + formatSeconds(step.getTriggerTimeSeconds())
                    + " | " + formatSeconds(step.getObservationTimeSeconds())
                    + " | " + step.getSnapshot().getSnapshotId()
                    + " | " + step.getReuseMode()
                    + " | " + step.reusedPreviousPopulation()
                    + " | " + step.getInitialPopulationSize()
                    + " | " + step.getFinalPopulationSize()
                    + " | " + step.getMaGaResult().getGenerationsExecuted()
                    + " | " + step.getMaGaResult().getStopReason()
                    + " | " + format(step.getMaGaResult().getInitialBestFitness())
                    + " | " + format(step.getMaGaResult().getFinalBestFitness())
                    + " | " + formatPercent(stats.improvementRatio));
        }

        out.println();
    }

    private void printDynamicityTable(List<TemporalStepResult> steps) {
        printSection("3. DYNAMICITY AND REUSE DETAILS");

        out.println("idx | level | D | Dv | Dt | Dr | Dl | suggested | applied | reusedPrev");

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
                    + " | " + step.reusedPreviousPopulation());
        }

        out.println();
    }

    private void printGaScalingTable(List<TemporalStepResult> steps) {
        printSection("4. GA PARAMETER SCALING");

        if (config == null) {
            out.println("MaGaConfig not provided. Scaling diagnostics unavailable.");
            out.println();
            return;
        }

        out.println("idx | mode | vehicles | tasks | candidates | avgCandTask | basePop | effPop | baseGen | effGen | baseElite | effElite | baseStall | effStall | changed");

        for (TemporalStepResult step : steps) {
            GaParameterScalingResult scaling =
                    config.resolveGaParameterScaling(step.getSnapshot());

            GeneticAlgorithmConfig baseConfig = scaling.getBaseConfig();
            GeneticAlgorithmConfig effectiveConfig = scaling.getScaledConfig();

            out.println(step.getWindowIndex()
                    + " | " + scaling.getMode()
                    + " | " + scaling.getVehicleCount()
                    + " | " + scaling.getActiveTaskCount()
                    + " | " + scaling.getCandidateCount()
                    + " | " + format(scaling.getAverageCandidatesPerTask())
                    + " | " + baseConfig.getPopulationSize()
                    + " | " + effectiveConfig.getPopulationSize()
                    + " | " + baseConfig.getMaxGenerations()
                    + " | " + effectiveConfig.getMaxGenerations()
                    + " | " + baseConfig.getElitismCount()
                    + " | " + effectiveConfig.getElitismCount()
                    + " | " + baseConfig.getStallGenerations()
                    + " | " + effectiveConfig.getStallGenerations()
                    + " | " + scaling.hasChanged());
        }

        out.println();
    }

    private void printGenerationDiagnostics(List<TemporalStepResult> steps) {
        printSection("5. GA GENERATION DIAGNOSTICS");

        if (!printGenerationHistory) {
            out.println("Generation history printing disabled.");
            out.println();
            return;
        }

        for (TemporalStepResult step : steps) {
            List<GenerationStat> history =
                    step.getMaGaResult().getGenerationHistory();

            out.println("Window " + step.getWindowIndex()
                    + " | snapshot=" + step.getSnapshot().getSnapshotId());

            if (history == null || history.isEmpty()) {
                out.println("- generation history unavailable");
                out.println();
                continue;
            }

            GenerationStat first = history.get(0);
            GenerationStat last = history.get(history.size() - 1);

            out.println("- historySize: " + history.size());
            out.println("- firstBest: " + format(first.getBestFitness()));
            out.println("- lastBest: " + format(last.getBestFitness()));
            out.println("- lastAverage: " + format(last.getAverageFitness()));
            out.println("- lastWorst: " + format(last.getWorstFitness()));
            out.println("- improvementLast10: "
                    + format(improvementOverLast(history, 10)));
            out.println("- improvementLast50: "
                    + format(improvementOverLast(history, 50)));

            out.println("- sampled history:");
            for (GenerationStat stat : sampleHistory(history)) {
                out.println("  gen=" + stat.getGenerationIndex()
                        + " best=" + format(stat.getBestFitness())
                        + " avg=" + format(stat.getAverageFitness())
                        + " worst=" + format(stat.getWorstFitness()));
            }

            out.println();
        }
    }

    private void printFitnessTable(List<TemporalStepResult> steps) {
        printSection("6. FITNESS BREAKDOWN");

        out.println("idx | J | T | L | Pmob | Pres | Tnorm | Lnorm | PmobNorm | PresNorm");

        for (TemporalStepResult step : steps) {
            EvaluationBreakdown e = step.getMaGaResult().getBestEvaluation();

            out.println(step.getWindowIndex()
                    + " | " + format(e.getFitness())
                    + " | " + formatSeconds(e.getCompletionTimeSeconds())
                    + " | " + formatSeconds(e.getCommunicationLatencySeconds())
                    + " | " + format(e.getMobilityPenalty())
                    + " | " + format(e.getResourcePenalty())
                    + " | " + format(e.getNormalizedCompletionTime())
                    + " | " + format(e.getNormalizedCommunicationLatency())
                    + " | " + format(e.getNormalizedMobilityPenalty())
                    + " | " + format(e.getNormalizedResourcePenalty()));
        }

        out.println();
    }

    private void printDecisionTable(List<TemporalStepResult> steps) {
        printSection("7. DECISION AND OFFLOADING DISTRIBUTION");

        out.println("idx | LOCAL | EDGE | CLOUD | VEHICLE | localExec | partial | full | avgP | p=0 | 0<p<=.25 | .25<p<=.50 | .50<p<=.75 | .75<p<1 | p=1");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);

            out.println(step.getWindowIndex()
                    + " | " + stats.nodeTypeCounts.getOrDefault(NodeType.LOCAL, 0)
                    + " | " + stats.nodeTypeCounts.getOrDefault(NodeType.EDGE, 0)
                    + " | " + stats.nodeTypeCounts.getOrDefault(NodeType.CLOUD, 0)
                    + " | " + stats.nodeTypeCounts.getOrDefault(NodeType.VEHICLE, 0)
                    + " | " + stats.decisionTypeCounts.getOrDefault(DecisionType.LOCAL_EXECUTION, 0)
                    + " | " + stats.decisionTypeCounts.getOrDefault(DecisionType.PARTIAL_OFFLOADING, 0)
                    + " | " + stats.decisionTypeCounts.getOrDefault(DecisionType.FULL_OFFLOADING, 0)
                    + " | " + format(stats.averageOffloadingRatio)
                    + " | " + stats.offloadingRatioBuckets.zero
                    + " | " + stats.offloadingRatioBuckets.low
                    + " | " + stats.offloadingRatioBuckets.midLow
                    + " | " + stats.offloadingRatioBuckets.midHigh
                    + " | " + stats.offloadingRatioBuckets.high
                    + " | " + stats.offloadingRatioBuckets.one);
        }

        out.println();
    }

    private void printDeadlineTable(List<TemporalStepResult> steps) {
        printSection("8. DEADLINE DIAGNOSTICS");

        out.println("idx | tasks | respected | violated | violationRate | worstTask | worstType | completion | deadline | ratio | constraintPenalty");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);
            GeneEvaluationBreakdown worst = stats.worstDeadlineViolation;

            if (worst == null) {
                out.println(step.getWindowIndex()
                        + " | " + stats.taskCount
                        + " | " + stats.taskCount
                        + " | 0 | 0.000000% | none | - | - | - | - | -");
                continue;
            }

            out.println(step.getWindowIndex()
                    + " | " + stats.taskCount
                    + " | " + (stats.taskCount - stats.deadlineViolationCount)
                    + " | " + stats.deadlineViolationCount
                    + " | " + formatPercent(stats.deadlineViolationRate)
                    + " | " + worst.getTaskId()
                    + " | " + worst.getNodeType()
                    + " | " + formatSeconds(worst.getCompletionTimeSeconds())
                    + " | " + formatSeconds(worst.getDeadlineSeconds())
                    + " | " + format(deadlineViolationRatio(worst))
                    + " | " + format(worst.getConstraintPenalty()));
        }

        out.println();
    }

    private void printMobilityTable(List<TemporalStepResult> steps) {
        printSection("9. MOBILITY AND COVERAGE DIAGNOSTICS");

        out.println("idx | Pmob | avgPmob | coverageInsuff | nearCritical | worstMobTask | type | PmobTask | coverage | completion | coverageRatio");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);
            GeneEvaluationBreakdown worst = stats.worstMobilityPenalty;

            if (worst == null) {
                out.println(step.getWindowIndex()
                        + " | " + format(stats.mobilityPenalty)
                        + " | " + format(stats.averageMobilityPenalty)
                        + " | " + stats.coverageInsufficientCount
                        + " | " + stats.nearCriticalCoverageCount
                        + " | none | - | - | - | - | -");
                continue;
            }

            out.println(step.getWindowIndex()
                    + " | " + format(stats.mobilityPenalty)
                    + " | " + format(stats.averageMobilityPenalty)
                    + " | " + stats.coverageInsufficientCount
                    + " | " + stats.nearCriticalCoverageCount
                    + " | " + worst.getTaskId()
                    + " | " + worst.getNodeType()
                    + " | " + format(worst.getMobilityPenalty())
                    + " | " + formatSeconds(worst.getCoverageTimeSeconds())
                    + " | " + formatSeconds(worst.getCompletionTimeSeconds())
                    + " | " + format(coverageRatio(worst)));
        }

        out.println();
    }

    private void printResourceTable(List<TemporalStepResult> steps) {
        printSection("10. RESOURCE DIAGNOSTICS");

        out.println("idx | CPUviol | CPUsat>=95 | BWviol | BWsat>=95 | worstCpuNode | worstCpu% | worstBwLink | worstBw%");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step);

            out.println(step.getWindowIndex()
                    + " | " + stats.cpuViolationCount
                    + " | " + stats.cpuSaturationCount
                    + " | " + stats.bandwidthViolationCount
                    + " | " + stats.bandwidthSaturationCount
                    + " | " + safeCpuId(stats.worstCpuUsage)
                    + " | " + safeCpuUsage(stats.worstCpuUsage)
                    + " | " + safeBandwidthId(stats.worstBandwidthUsage)
                    + " | " + safeBandwidthUsage(stats.worstBandwidthUsage));
        }

        out.println();
    }

    private void printWorstWindowSummary(List<TemporalStepResult> steps) {
        printSection("11. WORST WINDOWS SUMMARY");

        printWorstBy(
                "Worst by final fitness",
                steps,
                Comparator.comparingDouble(
                        (TemporalStepResult step) ->
                                step.getMaGaResult().getFinalBestFitness()
                ).reversed()
        );

        printWorstBy(
                "Worst by deadline violations",
                steps,
                Comparator.comparingInt(
                        (TemporalStepResult step) ->
                                StepStats.from(step).deadlineViolationCount
                ).reversed()
        );

        printWorstBy(
                "Worst by mobility penalty",
                steps,
                Comparator.comparingDouble(
                        (TemporalStepResult step) ->
                                StepStats.from(step).mobilityPenalty
                ).reversed()
        );

        printWorstBy(
                "Worst by resource penalty",
                steps,
                Comparator.comparingDouble(
                        (TemporalStepResult step) ->
                                StepStats.from(step).resourcePenalty
                ).reversed()
        );

        out.println();
    }

    private void printWorstBy(
            String title,
            List<TemporalStepResult> steps,
            Comparator<TemporalStepResult> comparator
    ) {
        List<TemporalStepResult> copy = new ArrayList<>(steps);
        copy.sort(comparator);

        out.println(title + ":");

        for (TemporalStepResult step : limit(copy, topLimit)) {
            StepStats stats = StepStats.from(step);

            out.println("- idx=" + step.getWindowIndex()
                    + " snapshot=" + step.getSnapshot().getSnapshotId()
                    + " J=" + format(step.getMaGaResult().getFinalBestFitness())
                    + " D=" + format(step.getDynamicityBreakdown().getGlobalDynamicity())
                    + " reuse=" + step.getReuseMode()
                    + " deadlineViol=" + stats.deadlineViolationCount
                    + " CPUviol=" + stats.cpuViolationCount
                    + " BWviol=" + stats.bandwidthViolationCount
                    + " coverageInsuff=" + stats.coverageInsufficientCount
                    + " Pres=" + format(stats.resourcePenalty)
                    + " Pmob=" + format(stats.mobilityPenalty));
        }

        out.println();
    }

    private void printDeepStepDetails(List<TemporalStepResult> steps) {
        printSection("12. PER-WINDOW DEEP DETAILS");

        for (TemporalStepResult step : steps) {
            printStepHeader(step);
            printStepDynamicity(step);
            printStepGaAndFitness(step);
            printStepDeadlineTop(step);
            printStepMobilityTop(step);

            if (printAllGeneRows) {
                printAllGenes(step);
            }

            if (printAllResourceRows) {
                printAllResources(step);
            }
        }
    }

    private void printStepHeader(TemporalStepResult step) {
        out.println("------------------------------------------------------------");
        out.println("WINDOW " + step.getWindowIndex()
                + " | snapshot=" + step.getSnapshot().getSnapshotId());
        out.println("------------------------------------------------------------");
        out.println("trigger=" + triggerLabel(step)
                + ", triggerTime=" + formatSeconds(step.getTriggerTimeSeconds())
                + ", observationTime=" + formatSeconds(step.getObservationTimeSeconds())
                + ", reuseMode=" + step.getReuseMode()
                + ", reusedPreviousPopulation=" + step.reusedPreviousPopulation()
                + ", initialPopulation=" + step.getInitialPopulationSize()
                + ", finalPopulation=" + step.getFinalPopulationSize());
        out.println("vehicles=" + step.getSnapshot().getVehicles().size()
                + ", tasks=" + step.getSnapshot().getTasks().size()
                + ", candidates=" + step.getSnapshot().getCandidateNodes().size());
        out.println();
    }

    private void printStepDynamicity(TemporalStepResult step) {
        DynamicityBreakdown d = step.getDynamicityBreakdown();

        out.println("Dynamicity:");
        out.println("- level=" + d.getDynamicityLevel());
        out.println("- suggestedReuseMode=" + d.getSuggestedReuseMode());
        out.println("- D=" + format(d.getGlobalDynamicity()));
        out.println("- Dv=" + format(d.getVehicleVariation()));
        out.println("- Dt=" + format(d.getTaskVariation()));
        out.println("- Dr=" + format(d.getResourceVariation()));
        out.println("- Dl=" + format(d.getLinkVariation()));
        out.println();
    }

    private void printStepGaAndFitness(TemporalStepResult step) {
        EvaluationBreakdown e = step.getMaGaResult().getBestEvaluation();

        out.println("GA result:");
        out.println("- generationsExecuted="
                + step.getMaGaResult().getGenerationsExecuted());
        out.println("- stopReason=" + step.getMaGaResult().getStopReason());
        out.println("- initialBestFitness="
                + format(step.getMaGaResult().getInitialBestFitness()));
        out.println("- finalBestFitness="
                + format(step.getMaGaResult().getFinalBestFitness()));
        out.println("- improvementRatio="
                + formatPercent(StepStats.from(step).improvementRatio));

        if (config != null) {
            GaParameterScalingResult scaling =
                    config.resolveGaParameterScaling(step.getSnapshot());

            out.println("- scalingMode=" + scaling.getMode());
            out.println("- effectivePopulation="
                    + scaling.getScaledConfig().getPopulationSize());
            out.println("- effectiveMaxGenerations="
                    + scaling.getScaledConfig().getMaxGenerations());
        }

        out.println();
        out.println("Fitness:");
        out.println("- J=" + format(e.getFitness()));
        out.println("- T=" + formatSeconds(e.getCompletionTimeSeconds()));
        out.println("- L=" + formatSeconds(e.getCommunicationLatencySeconds()));
        out.println("- Pmob=" + format(e.getMobilityPenalty()));
        out.println("- Pres=" + format(e.getResourcePenalty()));
        out.println("- Tnorm=" + format(e.getNormalizedCompletionTime()));
        out.println("- Lnorm=" + format(e.getNormalizedCommunicationLatency()));
        out.println("- PmobNorm=" + format(e.getNormalizedMobilityPenalty()));
        out.println("- PresNorm=" + format(e.getNormalizedResourcePenalty()));
        out.println();
    }

    private void printStepDeadlineTop(TemporalStepResult step) {
        List<GeneEvaluationBreakdown> violations =
                step.getMaGaResult().getBestEvaluation().getGeneBreakdowns()
                        .stream()
                        .filter(gene -> !gene.isDeadlineRespected())
                        .sorted(Comparator.comparingDouble(
                                DeepTemporalWindowStressPrinter::deadlineViolationRatio
                        ).reversed())
                        .toList();

        out.println("Top deadline violations:");

        if (violations.isEmpty()) {
            out.println("- none");
            out.println();
            return;
        }

        printGeneHeader();
        for (GeneEvaluationBreakdown gene : limit(violations, topLimit)) {
            printGeneRow(gene);
        }
        out.println();
    }

    private void printStepMobilityTop(TemporalStepResult step) {
        List<GeneEvaluationBreakdown> mobility =
                step.getMaGaResult().getBestEvaluation().getGeneBreakdowns()
                        .stream()
                        .filter(gene -> gene.getMobilityPenalty() > 0.0)
                        .sorted(Comparator.comparingDouble(
                                GeneEvaluationBreakdown::getMobilityPenalty
                        ).reversed())
                        .toList();

        out.println("Top mobility penalties:");

        if (mobility.isEmpty()) {
            out.println("- none");
            out.println();
            return;
        }

        printGeneHeader();
        for (GeneEvaluationBreakdown gene : limit(mobility, topLimit)) {
            printGeneRow(gene);
        }
        out.println();
    }

    private void printAllGenes(TemporalStepResult step) {
        out.println("All task/gene evaluations:");
        printGeneHeader();

        for (GeneEvaluationBreakdown gene
                : step.getMaGaResult()
                .getBestEvaluation()
                .getGeneBreakdowns()) {
            printGeneRow(gene);
        }

        out.println();
    }

    private void printAllResources(TemporalStepResult step) {
        EvaluationBreakdown e = step.getMaGaResult().getBestEvaluation();

        out.println("CPU execution-node usage:");
        out.println("executionNode | type | usedCpu | availableCpu | usage% | overflow | violation");

        for (ExecutionNodeResourceUsageBreakdown usage
                : e.getExecutionNodeResourceUsageBreakdowns()) {
            out.println(usage.getExecutionNodeId()
                    + " | " + usage.getNodeType()
                    + " | " + format(usage.getUsedCpu())
                    + " | " + format(usage.getAvailableCpu())
                    + " | " + format(usage.getCpuUsagePercent())
                    + " | " + format(usage.getCpuOverflowRatio())
                    + " | " + usage.hasCpuViolation());
        }

        out.println();

        out.println("Bandwidth candidate/link usage:");
        out.println("candidate | source | execution | type | usedBw | availableBw | usage% | overflow | violation");

        for (LinkBandwidthUsageBreakdown usage
                : e.getLinkBandwidthUsageBreakdowns()) {
            out.println(usage.getCandidateId()
                    + " | " + usage.getSourceVehicleId()
                    + " | " + usage.getExecutionNodeId()
                    + " | " + usage.getNodeType()
                    + " | " + format(usage.getUsedBandwidth())
                    + " | " + format(usage.getAvailableBandwidth())
                    + " | " + format(usage.getBandwidthUsagePercent())
                    + " | " + format(usage.getBandwidthOverflowRatio())
                    + " | " + usage.hasBandwidthViolation());
        }

        out.println();

        out.println("Local workload usage:");
        out.println("vehicle | localCpu | localCpuCycles | maxLocalTime | hasWorkload");

        for (LocalResourceUsageBreakdown usage
                : e.getLocalResourceUsageBreakdowns()) {
            out.println(usage.getVehicleId()
                    + " | " + format(usage.getLocalCpu())
                    + " | " + format(usage.getLocalCpuCycles())
                    + " | " + formatSeconds(usage.getMaxLocalExecutionTimeSeconds())
                    + " | " + usage.hasLocalWorkload());
        }

        out.println();
    }

    private void printFinalDiagnosis(List<TemporalStepResult> steps) {
        printSection("13. FINAL DIAGNOSIS");

        AggregatedStats aggregated = AggregatedStats.from(steps);

        out.println("Observed facts:");
        out.println("- CPU violations: " + aggregated.cpuViolations);
        out.println("- bandwidth violations: " + aggregated.bandwidthViolations);
        out.println("- deadline violations: " + aggregated.deadlineViolations);
        out.println("- coverage insufficient: " + aggregated.coverageInsufficient);

        out.println();

        if (aggregated.cpuViolations == 0) {
            out.println("CPU aggregate repair appears effective: no CPU overflow remains.");
        } else {
            out.println("CPU violations persist: inspect CPU repair and executionNodeId grouping.");
        }

        if (aggregated.bandwidthViolations == 0) {
            out.println("Bandwidth repair can remain OpenIssue for now: no bandwidth overflow was observed.");
        } else {
            out.println("Bandwidth violations exist: OpenIssue should be promoted to active repair/design task.");
        }

        if (aggregated.deadlineViolations > 0) {
            out.println("Deadline violations remain the main diagnostic target.");
            out.println("Inspect section 12 task rows to identify whether violations come from local time, upload, remote execution, download, latency, or coverage.");
        }

        if (aggregated.coverageInsufficient > 0) {
            out.println("Some remote decisions have insufficient coverage.");
            out.println("Check whether mobility penalty is strong enough or whether candidate filtering should exclude zero-coverage candidates.");
        }

        out.println();
    }

    private void printGeneHeader() {
        out.println("task | source | candidate | execNode | nodeType | decision | p | cpu | bw | localCycles | localT | uploadT | remoteT | downloadT | baseLat | remotePart | completion | commLat | deadline | deadlineOk | deadlineRatio | coverage | coverageOk | coverageRatio | Pmob | Pconstraint");
    }

    private void printGeneRow(GeneEvaluationBreakdown gene) {
        out.println(gene.getTaskId()
                + " | " + gene.getSourceVehicleId()
                + " | " + gene.getSelectedCandidateId()
                + " | " + gene.getExecutionNodeId()
                + " | " + gene.getNodeType()
                + " | " + gene.getDecisionType()
                + " | " + format(gene.getOffloadingRatio())
                + " | " + format(gene.getAllocatedCpu())
                + " | " + format(gene.getAllocatedBandwidth())
                + " | " + format(gene.getLocalCpuCycles())
                + " | " + formatSeconds(gene.getLocalExecutionTimeSeconds())
                + " | " + formatSeconds(gene.getUploadTimeSeconds())
                + " | " + formatSeconds(gene.getRemoteExecutionTimeSeconds())
                + " | " + formatSeconds(gene.getDownloadTimeSeconds())
                + " | " + formatSeconds(gene.getBaseLatencySeconds())
                + " | " + formatSeconds(gene.getRemotePartTimeSeconds())
                + " | " + formatSeconds(gene.getCompletionTimeSeconds())
                + " | " + formatSeconds(gene.getCommunicationLatencySeconds())
                + " | " + formatSeconds(gene.getDeadlineSeconds())
                + " | " + gene.isDeadlineRespected()
                + " | " + format(deadlineViolationRatio(gene))
                + " | " + formatSeconds(gene.getCoverageTimeSeconds())
                + " | " + gene.isCoverageSufficient()
                + " | " + format(coverageRatio(gene))
                + " | " + format(gene.getMobilityPenalty())
                + " | " + format(gene.getConstraintPenalty()));
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

    private double improvementOverLast(
            List<GenerationStat> history,
            int window
    ) {
        if (history == null || history.size() < 2) {
            return 0.0;
        }

        int lastIndex = history.size() - 1;
        int previousIndex = Math.max(0, lastIndex - window);

        double previous = history.get(previousIndex).getBestFitness();
        double current = history.get(lastIndex).getBestFitness();

        return previous - current;
    }

    private List<GenerationStat> sampleHistory(List<GenerationStat> history) {
        if (history.size() <= 12) {
            return history;
        }

        List<GenerationStat> sampled = new ArrayList<>();
        sampled.add(history.get(0));

        int step = Math.max(1, history.size() / 10);

        for (int i = step; i < history.size() - 1; i += step) {
            sampled.add(history.get(i));
        }

        sampled.add(history.get(history.size() - 1));

        return sampled;
    }

    private <T> List<T> limit(List<T> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }

        return values.subList(0, limit);
    }

    private String safeCpuId(ExecutionNodeResourceUsageBreakdown usage) {
        return usage == null ? "none" : usage.getExecutionNodeId();
    }

    private String safeCpuUsage(ExecutionNodeResourceUsageBreakdown usage) {
        return usage == null ? "-" : format(usage.getCpuUsagePercent()) + "%";
    }

    private String safeBandwidthId(LinkBandwidthUsageBreakdown usage) {
        return usage == null ? "none" : usage.getCandidateId();
    }

    private String safeBandwidthUsage(LinkBandwidthUsageBreakdown usage) {
        return usage == null
                ? "-"
                : format(usage.getBandwidthUsagePercent()) + "%";
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

    /**
     * Statistiche aggregate di una singola finestra.
     */
    private static final class StepStats {

        private static final double NEAR_COVERAGE_RATIO = 1.25;

        private final int taskCount;
        private final Map<NodeType, Integer> nodeTypeCounts;
        private final Map<DecisionType, Integer> decisionTypeCounts;
        private final OffloadingRatioBuckets offloadingRatioBuckets;

        private final double averageOffloadingRatio;
        private final double mobilityPenalty;
        private final double averageMobilityPenalty;
        private final double resourcePenalty;

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
                Map<DecisionType, Integer> decisionTypeCounts,
                OffloadingRatioBuckets offloadingRatioBuckets,
                double averageOffloadingRatio,
                double mobilityPenalty,
                double averageMobilityPenalty,
                double resourcePenalty,
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
            this.decisionTypeCounts = decisionTypeCounts;
            this.offloadingRatioBuckets = offloadingRatioBuckets;
            this.averageOffloadingRatio = averageOffloadingRatio;
            this.mobilityPenalty = mobilityPenalty;
            this.averageMobilityPenalty = averageMobilityPenalty;
            this.resourcePenalty = resourcePenalty;
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
            EvaluationBreakdown evaluation =
                    step.getMaGaResult().getBestEvaluation();

            List<GeneEvaluationBreakdown> genes =
                    evaluation.getGeneBreakdowns();

            Map<NodeType, Integer> nodeTypeCounts =
                    new EnumMap<>(NodeType.class);

            Map<DecisionType, Integer> decisionTypeCounts =
                    new EnumMap<>(DecisionType.class);

            OffloadingRatioBuckets buckets = new OffloadingRatioBuckets();

            double offloadSum = 0.0;
            double mobilitySum = 0.0;

            int deadlineViolations = 0;
            int coverageInsufficient = 0;
            int nearCriticalCoverage = 0;

            GeneEvaluationBreakdown worstDeadline = null;
            GeneEvaluationBreakdown worstMobility = null;

            for (GeneEvaluationBreakdown gene : genes) {
                nodeTypeCounts.merge(gene.getNodeType(), 1, Integer::sum);
                decisionTypeCounts.merge(gene.getDecisionType(), 1, Integer::sum);

                offloadSum += gene.getOffloadingRatio();
                mobilitySum += gene.getMobilityPenalty();
                buckets.add(gene.getOffloadingRatio());

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
                        nearCriticalCoverage++;
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
                    : evaluation.getExecutionNodeResourceUsageBreakdowns()) {
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
                    : evaluation.getLinkBandwidthUsageBreakdowns()) {
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

            double improvementRatio =
                    step.getMaGaResult().getInitialBestFitness() == 0.0
                            ? 0.0
                            : (step.getMaGaResult().getInitialBestFitness()
                            - step.getMaGaResult().getFinalBestFitness())
                            / step.getMaGaResult().getInitialBestFitness();

            return new StepStats(
                    genes.size(),
                    nodeTypeCounts,
                    decisionTypeCounts,
                    buckets,
                    genes.isEmpty() ? 0.0 : offloadSum / genes.size(),
                    evaluation.getMobilityPenalty(),
                    genes.isEmpty() ? 0.0 : mobilitySum / genes.size(),
                    evaluation.getResourcePenalty(),
                    deadlineViolations,
                    genes.isEmpty()
                            ? 0.0
                            : (double) deadlineViolations / genes.size(),
                    worstDeadline,
                    coverageInsufficient,
                    nearCriticalCoverage,
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

    /**
     * Bucket diagnostici per la quota di offloading p_i.
     */
    private static final class OffloadingRatioBuckets {

        private static final double EPSILON = 1.0E-9;

        private int zero;
        private int low;
        private int midLow;
        private int midHigh;
        private int high;
        private int one;

        private void add(double p) {
            if (p <= EPSILON) {
                zero++;
            } else if (p >= 1.0 - EPSILON) {
                one++;
            } else if (p <= 0.25) {
                low++;
            } else if (p <= 0.50) {
                midLow++;
            } else if (p <= 0.75) {
                midHigh++;
            } else {
                high++;
            }
        }
    }

    /**
     * Statistiche aggregate sull'intero risultato temporale.
     */
    private static final class AggregatedStats {

        private final int taskEvaluations;
        private final int deadlineViolations;
        private final int cpuViolations;
        private final int bandwidthViolations;
        private final int coverageInsufficient;
        private final int cpuSaturated;
        private final int bandwidthSaturated;

        private AggregatedStats(
                int taskEvaluations,
                int deadlineViolations,
                int cpuViolations,
                int bandwidthViolations,
                int coverageInsufficient,
                int cpuSaturated,
                int bandwidthSaturated
        ) {
            this.taskEvaluations = taskEvaluations;
            this.deadlineViolations = deadlineViolations;
            this.cpuViolations = cpuViolations;
            this.bandwidthViolations = bandwidthViolations;
            this.coverageInsufficient = coverageInsufficient;
            this.cpuSaturated = cpuSaturated;
            this.bandwidthSaturated = bandwidthSaturated;
        }

        private static AggregatedStats from(List<TemporalStepResult> steps) {
            int taskEvaluations = 0;
            int deadlineViolations = 0;
            int cpuViolations = 0;
            int bandwidthViolations = 0;
            int coverageInsufficient = 0;
            int cpuSaturated = 0;
            int bandwidthSaturated = 0;

            for (TemporalStepResult step : steps) {
                StepStats stats = StepStats.from(step);

                taskEvaluations += stats.taskCount;
                deadlineViolations += stats.deadlineViolationCount;
                cpuViolations += stats.cpuViolationCount;
                bandwidthViolations += stats.bandwidthViolationCount;
                coverageInsufficient += stats.coverageInsufficientCount;
                cpuSaturated += stats.cpuSaturationCount;
                bandwidthSaturated += stats.bandwidthSaturationCount;
            }

            return new AggregatedStats(
                    taskEvaluations,
                    deadlineViolations,
                    cpuViolations,
                    bandwidthViolations,
                    coverageInsufficient,
                    cpuSaturated,
                    bandwidthSaturated
            );
        }
    }
}
