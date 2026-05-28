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
import io.reporting.diagnostics.deadline.DeadlineViolationAnalyzer;
import io.reporting.diagnostics.deadline.DeadlineViolationCause;
import io.reporting.diagnostics.deadline.DeadlineViolationDiagnosis;
import io.reporting.diagnostics.deadline.DeadlineViolationWindowSummary;
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
 * Printer diagnostico mirato per stress test temporali MA-GA.
 *
 * Questa versione non stampa ogni riga disponibile del sistema.
 * Si concentra sul problema attuale emerso dai report:
 *
 * - deadline non rispettate;
 * - cause delle deadline violate;
 * - uso troppo conservativo dell'offloading;
 * - candidati remoti senza copertura sufficiente;
 * - pressione su CPU e banda senza entrare nel dettaglio completo di ogni risorsa;
 * - andamento del GA sufficiente a capire se sta ancora migliorando.
 *
 * Non modifica fitness, GA, repair o temporal manager.
 */
public final class DeepTemporalWindowDiagnosticPrinter {

    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final double SATURATION_THRESHOLD_PERCENT = 95.0;

    private final MaGaConfig config;
    private final PrintStream out;
    private final int topLimit;
    private final DeadlineViolationAnalyzer deadlineAnalyzer;

    /**
     * Costruisce il printer senza diagnostica di scaling GA.
     */
    public DeepTemporalWindowDiagnosticPrinter() {
        this(null, System.out, DEFAULT_TOP_LIMIT);
    }

    /**
     * Costruisce il printer con configurazione MA-GA.
     *
     * @param config configurazione usata nel test
     */
    public DeepTemporalWindowDiagnosticPrinter(MaGaConfig config) {
        this(config, System.out, DEFAULT_TOP_LIMIT);
    }

    /**
     * Costruisce il printer completo.
     *
     * @param config configurazione usata nel test, può essere null
     * @param out stream di output
     * @param topLimit numero massimo di task mostrati nelle sezioni top-N
     */
    public DeepTemporalWindowDiagnosticPrinter(
            MaGaConfig config,
            PrintStream out,
            int topLimit
    ) {
        this.config = config;
        this.out = Objects.requireNonNull(out, "out must not be null.");

        if (topLimit <= 0) {
            throw new IllegalArgumentException("topLimit must be > 0.");
        }

        this.topLimit = topLimit;
        this.deadlineAnalyzer = new DeadlineViolationAnalyzer();
    }

    /**
     * Stampa il report diagnostico mirato.
     *
     * @param result risultato prodotto da TemporalWindowManager
     */
    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        List<TemporalStepResult> steps = result.getSteps();

        printHeader();
        printExecutiveSummary(result, steps);
        printTemporalAndDynamicitySummary(steps);
        printGaConvergenceSummary(steps);
        printDecisionAndOffloadingSummary(steps);
        printDeadlineCauseSummary(steps);
        printTopDeadlineViolations(steps);
        printCoverageProblemSummary(steps);
        printResourcePressureSummary(steps);
        printWorstWindows(steps);
        printDiagnosis(steps);
    }

    private void printHeader() {
        out.println("============================================================");
        out.println("MA-GA TEMPORAL DIAGNOSTIC REPORT");
        out.println("============================================================");
        out.println();
    }

    private void printExecutiveSummary(
            TemporalWindowResult result,
            List<TemporalStepResult> steps
    ) {
        printSection("1. EXECUTIVE SUMMARY");

        if (steps.isEmpty()) {
            out.println("No temporal step available.");
            out.println();
            return;
        }

        GlobalStats global = GlobalStats.from(steps, deadlineAnalyzer);

        out.println("Executed windows: " + result.getStepCount());
        out.println("Critical-event windows: " + result.countCriticalEventSteps());
        out.println("Population-reuse windows: " + result.countPopulationReuseSteps());
        out.println("Best final fitness: "
                + format(result.getBestFinalFitness().orElse(Double.NaN)));

        out.println();
        out.println("Main diagnostic counters:");
        out.println("- task evaluations: " + global.totalTasks);
        out.println("- deadline violations: " + global.deadlineViolations
                + " (" + formatPercent(global.deadlineViolationRate()) + ")");
        out.println("- coverage insufficient: " + global.coverageInsufficient);
        out.println("- CPU violations: " + global.cpuViolations);
        out.println("- bandwidth violations: " + global.bandwidthViolations);
        out.println("- CPU saturated >= " + format(SATURATION_THRESHOLD_PERCENT)
                + "%: " + global.cpuSaturated);
        out.println("- bandwidth saturated >= "
                + format(SATURATION_THRESHOLD_PERCENT)
                + "%: " + global.bandwidthSaturated);

        out.println();
        out.println("Dominant deadline causes:");
        printCauseCounters(global.causeCounters);
        out.println();
    }

    private void printTemporalAndDynamicitySummary(
            List<TemporalStepResult> steps
    ) {
        printSection("2. TEMPORAL / DYNAMICITY SUMMARY");

        out.println("idx | trigger | obsTime | snapshot | D | Dv | Dt | Dr | Dl | level | suggested | applied | initPop | finalPop");

        for (TemporalStepResult step : steps) {
            DynamicityBreakdown dynamicity = step.getDynamicityBreakdown();

            out.println(step.getWindowIndex()
                    + " | " + triggerLabel(step)
                    + " | " + formatSeconds(step.getObservationTimeSeconds())
                    + " | " + step.getSnapshot().getSnapshotId()
                    + " | " + format(dynamicity.getGlobalDynamicity())
                    + " | " + format(dynamicity.getVehicleVariation())
                    + " | " + format(dynamicity.getTaskVariation())
                    + " | " + format(dynamicity.getResourceVariation())
                    + " | " + format(dynamicity.getLinkVariation())
                    + " | " + dynamicity.getDynamicityLevel()
                    + " | " + dynamicity.getSuggestedReuseMode()
                    + " | " + step.getReuseMode()
                    + " | " + step.getInitialPopulationSize()
                    + " | " + step.getFinalPopulationSize());
        }

        out.println();
    }

    private void printGaConvergenceSummary(List<TemporalStepResult> steps) {
        printSection("3. GA CONVERGENCE SUMMARY");

        out.println("idx | pop | maxGen | genRun | stop | initBest | finalBest | gain% | last10 | last50");

        for (TemporalStepResult step : steps) {
            GaRunStats stats = GaRunStats.from(step);

            GeneticAlgorithmConfig effectiveConfig =
                    resolveEffectiveConfig(step);

            String pop = effectiveConfig == null
                    ? "-"
                    : String.valueOf(effectiveConfig.getPopulationSize());

            String maxGen = effectiveConfig == null
                    ? "-"
                    : String.valueOf(effectiveConfig.getMaxGenerations());

            out.println(step.getWindowIndex()
                    + " | " + pop
                    + " | " + maxGen
                    + " | " + step.getMaGaResult().getGenerationsExecuted()
                    + " | " + step.getMaGaResult().getStopReason()
                    + " | " + format(step.getMaGaResult().getInitialBestFitness())
                    + " | " + format(step.getMaGaResult().getFinalBestFitness())
                    + " | " + formatPercent(stats.improvementRatio)
                    + " | " + format(stats.improvementLast10)
                    + " | " + format(stats.improvementLast50));
        }

        out.println();
    }

    private void printDecisionAndOffloadingSummary(
            List<TemporalStepResult> steps
    ) {
        printSection("4. DECISION / OFFLOADING SUMMARY");

        out.println("idx | LOCAL | EDGE | CLOUD | VEHICLE | localExec | partial | full | avgP | p=0 | low | midLow | midHigh | high | p=1");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step, deadlineAnalyzer);

            out.println(step.getWindowIndex()
                    + " | " + stats.nodeTypeCounters.getOrDefault(NodeType.LOCAL, 0)
                    + " | " + stats.nodeTypeCounters.getOrDefault(NodeType.EDGE, 0)
                    + " | " + stats.nodeTypeCounters.getOrDefault(NodeType.CLOUD, 0)
                    + " | " + stats.nodeTypeCounters.getOrDefault(NodeType.VEHICLE, 0)
                    + " | " + stats.decisionTypeCounters.getOrDefault(DecisionType.LOCAL_EXECUTION, 0)
                    + " | " + stats.decisionTypeCounters.getOrDefault(DecisionType.PARTIAL_OFFLOADING, 0)
                    + " | " + stats.decisionTypeCounters.getOrDefault(DecisionType.FULL_OFFLOADING, 0)
                    + " | " + format(stats.averageOffloadingRatio)
                    + " | " + stats.buckets.zero
                    + " | " + stats.buckets.low
                    + " | " + stats.buckets.midLow
                    + " | " + stats.buckets.midHigh
                    + " | " + stats.buckets.high
                    + " | " + stats.buckets.one);
        }

        out.println();
    }

    private void printDeadlineCauseSummary(List<TemporalStepResult> steps) {
        printSection("5. DEADLINE CAUSE SUMMARY");

        out.println("idx | violated | rate | local | upload | remoteExec | download | baseLat | mixedLocalRemote | mixedRemote | coverage | unknown");

        for (TemporalStepResult step : steps) {
            DeadlineViolationWindowSummary summary =
                    deadlineSummary(step);

            Map<DeadlineViolationCause, Integer> causes =
                    summary.getCountByCause();

            out.println(step.getWindowIndex()
                    + " | " + summary.getViolatedTasks()
                    + " | " + formatPercent(summary.getViolationRate())
                    + " | " + causes.getOrDefault(DeadlineViolationCause.LOCAL_EXECUTION_BOTTLENECK, 0)
                    + " | " + causes.getOrDefault(DeadlineViolationCause.UPLOAD_BOTTLENECK, 0)
                    + " | " + causes.getOrDefault(DeadlineViolationCause.REMOTE_EXECUTION_BOTTLENECK, 0)
                    + " | " + causes.getOrDefault(DeadlineViolationCause.DOWNLOAD_BOTTLENECK, 0)
                    + " | " + causes.getOrDefault(DeadlineViolationCause.BASE_LATENCY_BOTTLENECK, 0)
                    + " | " + causes.getOrDefault(DeadlineViolationCause.MIXED_LOCAL_REMOTE_BOTTLENECK, 0)
                    + " | " + causes.getOrDefault(DeadlineViolationCause.MIXED_REMOTE_PIPELINE_BOTTLENECK, 0)
                    + " | " + causes.getOrDefault(DeadlineViolationCause.COVERAGE_INSUFFICIENT, 0)
                    + " | " + causes.getOrDefault(DeadlineViolationCause.UNKNOWN, 0));
        }

        out.println();
    }

    private void printTopDeadlineViolations(List<TemporalStepResult> steps) {
        printSection("6. TOP DEADLINE VIOLATIONS BY WINDOW");

        for (TemporalStepResult step : steps) {
            DeadlineViolationWindowSummary summary =
                    deadlineSummary(step);

            out.println("Window " + step.getWindowIndex()
                    + " | snapshot=" + step.getSnapshot().getSnapshotId()
                    + " | violated=" + summary.getViolatedTasks());

            if (summary.getViolationsBySeverity().isEmpty()) {
                out.println("- no deadline violations");
                out.println();
                continue;
            }

            out.println("task | source | node | decision | p | completion | deadline | ratio | cause | secondary | dominant | coverage | note");

            for (DeadlineViolationDiagnosis diagnosis
                    : limit(summary.getViolationsBySeverity(), topLimit)) {
                out.println(diagnosis.getTaskId()
                        + " | " + diagnosis.getSourceVehicleId()
                        + " | " + diagnosis.getNodeType()
                        + " | " + diagnosis.getDecisionType()
                        + " | " + format(diagnosis.getOffloadingRatio())
                        + " | " + formatSeconds(diagnosis.getCompletionTimeSeconds())
                        + " | " + formatSeconds(diagnosis.getDeadlineSeconds())
                        + " | " + format(diagnosis.getViolationRatio())
                        + " | " + diagnosis.getPrimaryCause()
                        + " | " + diagnosis.getSecondaryCause()
                        + " | " + diagnosis.getDominantComponentName()
                        + "=" + formatSeconds(diagnosis.getDominantComponentSeconds())
                        + " | " + formatSeconds(diagnosis.getCoverageTimeSeconds())
                        + " | " + diagnosis.getNote());
            }

            out.println();
        }
    }

    private void printCoverageProblemSummary(List<TemporalStepResult> steps) {
        printSection("7. COVERAGE PROBLEM SUMMARY");

        out.println("idx | insufficient | worstTask | node | completion | coverage | cause");

        for (TemporalStepResult step : steps) {
            List<DeadlineViolationDiagnosis> coverageProblems =
                    deadlineSummary(step)
                            .getDiagnoses()
                            .stream()
                            .filter(DeadlineViolationDiagnosis::hasCoverageProblem)
                            .sorted(
                                    Comparator
                                            .comparingDouble(
                                                    DeadlineViolationDiagnosis::getViolationRatio
                                            )
                                            .reversed()
                            )
                            .toList();

            if (coverageProblems.isEmpty()) {
                out.println(step.getWindowIndex()
                        + " | 0 | none | - | - | - | -");
                continue;
            }

            DeadlineViolationDiagnosis worst = coverageProblems.get(0);

            out.println(step.getWindowIndex()
                    + " | " + coverageProblems.size()
                    + " | " + worst.getTaskId()
                    + " | " + worst.getNodeType()
                    + " | " + formatSeconds(worst.getCompletionTimeSeconds())
                    + " | " + formatSeconds(worst.getCoverageTimeSeconds())
                    + " | " + worst.getPrimaryCause());
        }

        out.println();
    }

    private void printResourcePressureSummary(List<TemporalStepResult> steps) {
        printSection("8. RESOURCE PRESSURE SUMMARY");

        out.println("idx | CPUviol | CPUsat | worstCpuNode | worstCpu% | BWviol | BWsat | worstBwLink | worstBw%");

        for (TemporalStepResult step : steps) {
            StepStats stats = StepStats.from(step, deadlineAnalyzer);

            out.println(step.getWindowIndex()
                    + " | " + stats.cpuViolations
                    + " | " + stats.cpuSaturated
                    + " | " + stats.worstCpuNode
                    + " | " + stats.worstCpuUsagePercent
                    + " | " + stats.bandwidthViolations
                    + " | " + stats.bandwidthSaturated
                    + " | " + stats.worstBandwidthLink
                    + " | " + stats.worstBandwidthUsagePercent);
        }

        out.println();
    }

    private void printWorstWindows(List<TemporalStepResult> steps) {
        printSection("9. WORST WINDOWS");

        printWorstByFitness(steps);
        printWorstByDeadline(steps);
        printWorstByCoverage(steps);
        out.println();
    }

    private void printWorstByFitness(List<TemporalStepResult> steps) {
        List<TemporalStepResult> copy = new ArrayList<>(steps);
        copy.sort(
                Comparator
                        .comparingDouble(
                                (TemporalStepResult step) ->
                                        step.getMaGaResult()
                                                .getFinalBestFitness()
                        )
                        .reversed()
        );

        out.println("Worst by final fitness:");
        for (TemporalStepResult step : limit(copy, topLimit)) {
            StepStats stats = StepStats.from(step, deadlineAnalyzer);

            out.println("- idx=" + step.getWindowIndex()
                    + " J=" + format(step.getMaGaResult().getFinalBestFitness())
                    + " Pres=" + format(stats.resourcePenalty)
                    + " violated=" + stats.deadlineViolations
                    + " coverage=" + stats.coverageInsufficient
                    + " reuse=" + step.getReuseMode());
        }
    }

    private void printWorstByDeadline(List<TemporalStepResult> steps) {
        List<TemporalStepResult> copy = new ArrayList<>(steps);
        copy.sort(
                Comparator
                        .comparingInt(
                                (TemporalStepResult step) ->
                                        StepStats.from(
                                                step,
                                                deadlineAnalyzer
                                        ).deadlineViolations
                        )
                        .reversed()
        );

        out.println("Worst by deadline violations:");
        for (TemporalStepResult step : limit(copy, topLimit)) {
            StepStats stats = StepStats.from(step, deadlineAnalyzer);

            out.println("- idx=" + step.getWindowIndex()
                    + " violated=" + stats.deadlineViolations
                    + " rate=" + formatPercent(stats.deadlineViolationRate)
                    + " J=" + format(step.getMaGaResult().getFinalBestFitness())
                    + " mainCause=" + stats.mainDeadlineCause);
        }
    }

    private void printWorstByCoverage(List<TemporalStepResult> steps) {
        List<TemporalStepResult> copy = new ArrayList<>(steps);
        copy.sort(
                Comparator
                        .comparingInt(
                                (TemporalStepResult step) ->
                                        StepStats.from(
                                                step,
                                                deadlineAnalyzer
                                        ).coverageInsufficient
                        )
                        .reversed()
        );

        out.println("Worst by coverage problems:");
        for (TemporalStepResult step : limit(copy, topLimit)) {
            StepStats stats = StepStats.from(step, deadlineAnalyzer);

            out.println("- idx=" + step.getWindowIndex()
                    + " coverageInsufficient=" + stats.coverageInsufficient
                    + " violated=" + stats.deadlineViolations
                    + " J=" + format(step.getMaGaResult().getFinalBestFitness()));
        }
    }

    private void printDiagnosis(List<TemporalStepResult> steps) {
        printSection("10. DIAGNOSIS");

        GlobalStats global = GlobalStats.from(steps, deadlineAnalyzer);

        if (global.cpuViolations == 0) {
            out.println("- CPU aggregate repair appears effective: no CPU violations were observed.");
        } else {
            out.println("- CPU violations persist: inspect aggregate CPU repair and executionNodeId grouping.");
        }

        if (global.bandwidthViolations == 0) {
            out.println("- Bandwidth repair can remain OpenIssue for now: no bandwidth violations were observed.");
        } else {
            out.println("- Bandwidth violations exist: promote bandwidth repair from OpenIssue to implementation task.");
        }

        if (global.deadlineViolations > 0) {
            out.println("- Deadline violations remain the main problem.");
            out.println("- Use sections 5 and 6 to identify whether the dominant cause is local execution, upload, remote execution, latency, mixed execution, or coverage.");
        }

        int coverageCauseCount =
                global.causeCounters.getOrDefault(
                        DeadlineViolationCause.COVERAGE_INSUFFICIENT,
                        0
                );

        if (coverageCauseCount > 0) {
            out.println("- Some deadline violations are primarily caused by insufficient coverage.");
            out.println("- This supports introducing a candidate prefilter/repair for remote candidates with bad coverage.");
        }

        boolean noFullOffloading = steps
                .stream()
                .allMatch(step ->
                        StepStats.from(
                                step,
                                deadlineAnalyzer
                        ).decisionTypeCounters.getOrDefault(
                                DecisionType.FULL_OFFLOADING,
                                0
                        ) == 0
                );

        if (noFullOffloading) {
            out.println("- FULL_OFFLOADING never appears.");
            out.println("- Check initialization, mutation and repair of offloadingRatio before changing the fitness.");
        }

        out.println();
    }

    private DeadlineViolationWindowSummary deadlineSummary(
            TemporalStepResult step
    ) {
        return deadlineAnalyzer.summarize(
                step.getMaGaResult()
                        .getBestEvaluation()
                        .getGeneBreakdowns()
        );
    }

    private GeneticAlgorithmConfig resolveEffectiveConfig(
            TemporalStepResult step
    ) {
        if (config == null) {
            return null;
        }

        GaParameterScalingResult scaling =
                config.resolveGaParameterScaling(step.getSnapshot());

        return scaling.getScaledConfig();
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

    private <T> List<T> limit(List<T> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }

        return values.subList(0, limit);
    }

    private void printCauseCounters(
            Map<DeadlineViolationCause, Integer> counters
    ) {
        for (DeadlineViolationCause cause : DeadlineViolationCause.values()) {
            int count = counters.getOrDefault(cause, 0);

            if (count > 0 && cause != DeadlineViolationCause.DEADLINE_RESPECTED) {
                out.println("- " + cause + ": " + count);
            }
        }
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

    private static final class GaRunStats {

        private final double improvementRatio;
        private final double improvementLast10;
        private final double improvementLast50;

        private GaRunStats(
                double improvementRatio,
                double improvementLast10,
                double improvementLast50
        ) {
            this.improvementRatio = improvementRatio;
            this.improvementLast10 = improvementLast10;
            this.improvementLast50 = improvementLast50;
        }

        private static GaRunStats from(TemporalStepResult step) {
            double initial = step.getMaGaResult().getInitialBestFitness();
            double finalBest = step.getMaGaResult().getFinalBestFitness();

            double ratio = initial == 0.0
                    ? 0.0
                    : (initial - finalBest) / initial;

            List<GenerationStat> history =
                    step.getMaGaResult().getGenerationHistory();

            return new GaRunStats(
                    ratio,
                    improvementOverLast(history, 10),
                    improvementOverLast(history, 50)
            );
        }

        private static double improvementOverLast(
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
    }

    private static final class StepStats {

        private final Map<NodeType, Integer> nodeTypeCounters;
        private final Map<DecisionType, Integer> decisionTypeCounters;
        private final OffloadingBuckets buckets;

        private final double averageOffloadingRatio;
        private final int deadlineViolations;
        private final double deadlineViolationRate;
        private final int coverageInsufficient;

        private final int cpuViolations;
        private final int cpuSaturated;
        private final String worstCpuNode;
        private final String worstCpuUsagePercent;

        private final int bandwidthViolations;
        private final int bandwidthSaturated;
        private final String worstBandwidthLink;
        private final String worstBandwidthUsagePercent;

        private final double resourcePenalty;
        private final DeadlineViolationCause mainDeadlineCause;

        private StepStats(
                Map<NodeType, Integer> nodeTypeCounters,
                Map<DecisionType, Integer> decisionTypeCounters,
                OffloadingBuckets buckets,
                double averageOffloadingRatio,
                int deadlineViolations,
                double deadlineViolationRate,
                int coverageInsufficient,
                int cpuViolations,
                int cpuSaturated,
                String worstCpuNode,
                String worstCpuUsagePercent,
                int bandwidthViolations,
                int bandwidthSaturated,
                String worstBandwidthLink,
                String worstBandwidthUsagePercent,
                double resourcePenalty,
                DeadlineViolationCause mainDeadlineCause
        ) {
            this.nodeTypeCounters = nodeTypeCounters;
            this.decisionTypeCounters = decisionTypeCounters;
            this.buckets = buckets;
            this.averageOffloadingRatio = averageOffloadingRatio;
            this.deadlineViolations = deadlineViolations;
            this.deadlineViolationRate = deadlineViolationRate;
            this.coverageInsufficient = coverageInsufficient;
            this.cpuViolations = cpuViolations;
            this.cpuSaturated = cpuSaturated;
            this.worstCpuNode = worstCpuNode;
            this.worstCpuUsagePercent = worstCpuUsagePercent;
            this.bandwidthViolations = bandwidthViolations;
            this.bandwidthSaturated = bandwidthSaturated;
            this.worstBandwidthLink = worstBandwidthLink;
            this.worstBandwidthUsagePercent = worstBandwidthUsagePercent;
            this.resourcePenalty = resourcePenalty;
            this.mainDeadlineCause = mainDeadlineCause;
        }

        private static StepStats from(
                TemporalStepResult step,
                DeadlineViolationAnalyzer analyzer
        ) {
            EvaluationBreakdown evaluation =
                    step.getMaGaResult().getBestEvaluation();

            Map<NodeType, Integer> nodeTypeCounters =
                    new EnumMap<>(NodeType.class);

            Map<DecisionType, Integer> decisionTypeCounters =
                    new EnumMap<>(DecisionType.class);

            OffloadingBuckets buckets = new OffloadingBuckets();

            double pSum = 0.0;

            for (GeneEvaluationBreakdown gene
                    : evaluation.getGeneBreakdowns()) {
                nodeTypeCounters.merge(
                        gene.getNodeType(),
                        1,
                        Integer::sum
                );

                decisionTypeCounters.merge(
                        gene.getDecisionType(),
                        1,
                        Integer::sum
                );

                pSum += gene.getOffloadingRatio();
                buckets.add(gene.getOffloadingRatio());
            }

            int geneCount = evaluation.getGeneBreakdowns().size();
            double avgP = geneCount == 0 ? 0.0 : pSum / geneCount;

            DeadlineViolationWindowSummary deadlineSummary =
                    analyzer.summarize(evaluation.getGeneBreakdowns());

            ResourceStats resourceStats =
                    ResourceStats.from(evaluation);

            return new StepStats(
                    nodeTypeCounters,
                    decisionTypeCounters,
                    buckets,
                    avgP,
                    deadlineSummary.getViolatedTasks(),
                    deadlineSummary.getViolationRate(),
                    countCoverageInsufficient(deadlineSummary),
                    resourceStats.cpuViolations,
                    resourceStats.cpuSaturated,
                    resourceStats.worstCpuNode,
                    resourceStats.worstCpuUsagePercent,
                    resourceStats.bandwidthViolations,
                    resourceStats.bandwidthSaturated,
                    resourceStats.worstBandwidthLink,
                    resourceStats.worstBandwidthUsagePercent,
                    evaluation.getResourcePenalty(),
                    dominantCause(deadlineSummary)
            );
        }

        private static int countCoverageInsufficient(
                DeadlineViolationWindowSummary summary
        ) {
            int count = 0;

            for (DeadlineViolationDiagnosis diagnosis
                    : summary.getDiagnoses()) {
                if (diagnosis.hasCoverageProblem()) {
                    count++;
                }
            }

            return count;
        }

        private static DeadlineViolationCause dominantCause(
                DeadlineViolationWindowSummary summary
        ) {
            DeadlineViolationCause best = DeadlineViolationCause.UNKNOWN;
            int bestCount = -1;

            for (Map.Entry<DeadlineViolationCause, Integer> entry
                    : summary.getCountByCause().entrySet()) {
                if (entry.getKey()
                        == DeadlineViolationCause.DEADLINE_RESPECTED) {
                    continue;
                }

                if (entry.getValue() > bestCount) {
                    best = entry.getKey();
                    bestCount = entry.getValue();
                }
            }

            return best;
        }
    }

    private static final class ResourceStats {

        private final int cpuViolations;
        private final int cpuSaturated;
        private final String worstCpuNode;
        private final String worstCpuUsagePercent;

        private final int bandwidthViolations;
        private final int bandwidthSaturated;
        private final String worstBandwidthLink;
        private final String worstBandwidthUsagePercent;

        private ResourceStats(
                int cpuViolations,
                int cpuSaturated,
                String worstCpuNode,
                String worstCpuUsagePercent,
                int bandwidthViolations,
                int bandwidthSaturated,
                String worstBandwidthLink,
                String worstBandwidthUsagePercent
        ) {
            this.cpuViolations = cpuViolations;
            this.cpuSaturated = cpuSaturated;
            this.worstCpuNode = worstCpuNode;
            this.worstCpuUsagePercent = worstCpuUsagePercent;
            this.bandwidthViolations = bandwidthViolations;
            this.bandwidthSaturated = bandwidthSaturated;
            this.worstBandwidthLink = worstBandwidthLink;
            this.worstBandwidthUsagePercent = worstBandwidthUsagePercent;
        }

        private static ResourceStats from(EvaluationBreakdown evaluation) {
            int cpuViolations = 0;
            int cpuSaturated = 0;
            ExecutionNodeResourceUsageBreakdown worstCpu = null;

            for (ExecutionNodeResourceUsageBreakdown usage
                    : evaluation.getExecutionNodeResourceUsageBreakdowns()) {
                if (usage.hasCpuViolation()) {
                    cpuViolations++;
                } else if (usage.getCpuUsagePercent()
                        >= SATURATION_THRESHOLD_PERCENT) {
                    cpuSaturated++;
                }

                if (worstCpu == null
                        || usage.getCpuUsagePercent()
                        > worstCpu.getCpuUsagePercent()) {
                    worstCpu = usage;
                }
            }

            int bandwidthViolations = 0;
            int bandwidthSaturated = 0;
            LinkBandwidthUsageBreakdown worstBandwidth = null;

            for (LinkBandwidthUsageBreakdown usage
                    : evaluation.getLinkBandwidthUsageBreakdowns()) {
                if (usage.hasBandwidthViolation()) {
                    bandwidthViolations++;
                } else if (usage.getBandwidthUsagePercent()
                        >= SATURATION_THRESHOLD_PERCENT) {
                    bandwidthSaturated++;
                }

                if (worstBandwidth == null
                        || usage.getBandwidthUsagePercent()
                        > worstBandwidth.getBandwidthUsagePercent()) {
                    worstBandwidth = usage;
                }
            }

            return new ResourceStats(
                    cpuViolations,
                    cpuSaturated,
                    worstCpu == null ? "none" : worstCpu.getExecutionNodeId(),
                    worstCpu == null
                            ? "-"
                            : String.format("%.6f%%", worstCpu.getCpuUsagePercent()),
                    bandwidthViolations,
                    bandwidthSaturated,
                    worstBandwidth == null
                            ? "none"
                            : worstBandwidth.getCandidateId(),
                    worstBandwidth == null
                            ? "-"
                            : String.format("%.6f%%", worstBandwidth.getBandwidthUsagePercent())
            );
        }
    }

    private static final class OffloadingBuckets {

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

    private static final class GlobalStats {

        private final int totalTasks;
        private final int deadlineViolations;
        private final int coverageInsufficient;
        private final int cpuViolations;
        private final int bandwidthViolations;
        private final int cpuSaturated;
        private final int bandwidthSaturated;
        private final Map<DeadlineViolationCause, Integer> causeCounters;

        private GlobalStats(
                int totalTasks,
                int deadlineViolations,
                int coverageInsufficient,
                int cpuViolations,
                int bandwidthViolations,
                int cpuSaturated,
                int bandwidthSaturated,
                Map<DeadlineViolationCause, Integer> causeCounters
        ) {
            this.totalTasks = totalTasks;
            this.deadlineViolations = deadlineViolations;
            this.coverageInsufficient = coverageInsufficient;
            this.cpuViolations = cpuViolations;
            this.bandwidthViolations = bandwidthViolations;
            this.cpuSaturated = cpuSaturated;
            this.bandwidthSaturated = bandwidthSaturated;
            this.causeCounters = causeCounters;
        }

        private double deadlineViolationRate() {
            return totalTasks == 0
                    ? 0.0
                    : (double) deadlineViolations / totalTasks;
        }

        private static GlobalStats from(
                List<TemporalStepResult> steps,
                DeadlineViolationAnalyzer analyzer
        ) {
            int totalTasks = 0;
            int deadlineViolations = 0;
            int coverageInsufficient = 0;
            int cpuViolations = 0;
            int bandwidthViolations = 0;
            int cpuSaturated = 0;
            int bandwidthSaturated = 0;

            Map<DeadlineViolationCause, Integer> causes =
                    new EnumMap<>(DeadlineViolationCause.class);

            for (TemporalStepResult step : steps) {
                EvaluationBreakdown evaluation =
                        step.getMaGaResult().getBestEvaluation();

                DeadlineViolationWindowSummary summary =
                        analyzer.summarize(evaluation.getGeneBreakdowns());

                totalTasks += summary.getTotalTasks();
                deadlineViolations += summary.getViolatedTasks();

                for (DeadlineViolationDiagnosis diagnosis
                        : summary.getDiagnoses()) {
                    if (diagnosis.hasCoverageProblem()) {
                        coverageInsufficient++;
                    }
                }

                for (Map.Entry<DeadlineViolationCause, Integer> entry
                        : summary.getCountByCause().entrySet()) {
                    causes.merge(
                            entry.getKey(),
                            entry.getValue(),
                            Integer::sum
                    );
                }

                ResourceStats resourceStats =
                        ResourceStats.from(evaluation);

                cpuViolations += resourceStats.cpuViolations;
                bandwidthViolations += resourceStats.bandwidthViolations;
                cpuSaturated += resourceStats.cpuSaturated;
                bandwidthSaturated += resourceStats.bandwidthSaturated;
            }

            return new GlobalStats(
                    totalTasks,
                    deadlineViolations,
                    coverageInsufficient,
                    cpuViolations,
                    bandwidthViolations,
                    cpuSaturated,
                    bandwidthSaturated,
                    causes
            );
        }
    }
}
