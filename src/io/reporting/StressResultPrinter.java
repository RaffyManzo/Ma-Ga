package io.reporting;

import config.MaGaConfig;
import config.fitness.FitnessWeights;
import config.ga.GaParameterScalingResult;
import config.ga.GeneticAlgorithmConfig;
import ga.core.GenerationStat;
import ga.core.MaGaResult;
import ga.fitness.DecisionType;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import ga.fitness.breakdown.LocalResourceUsageBreakdown;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Printer diagnostico per scenari MA-GA di grandi dimensioni.
 *
 * A differenza di ResultPrinter, non stampa ogni dettaglio dello scenario.
 * Riassume le informazioni importanti per capire:
 *
 * - quanto il GA è migliorato;
 * - quali tipi di decisione sono stati scelti;
 * - quali task violano deadline o copertura;
 * - quali risorse CPU o banda sono violate/sature;
 * - quali task contribuiscono maggiormente ai problemi della soluzione.
 */
public final class StressResultPrinter {

    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final double SATURATION_THRESHOLD_PERCENT = 95.0;
    private static final double NEAR_COVERAGE_THRESHOLD_RATIO = 1.25;

    private final MaGaConfig config;
    private final PrintStream out;
    private final int topLimit;

    /**
     * Costruisce il printer usando System.out e top 10 diagnostici.
     *
     * @param config configurazione MA-GA usata nell'esecuzione
     */
    public StressResultPrinter(MaGaConfig config) {
        this(config, System.out, DEFAULT_TOP_LIMIT);
    }

    /**
     * Costruisce il printer con stream personalizzato.
     *
     * @param config configurazione MA-GA usata nell'esecuzione
     * @param out stream di output
     */
    public StressResultPrinter(MaGaConfig config, PrintStream out) {
        this(config, out, DEFAULT_TOP_LIMIT);
    }

    /**
     * Costruisce il printer con stream e numero massimo di righe top-N.
     *
     * @param config configurazione MA-GA usata nell'esecuzione
     * @param out stream di output
     * @param topLimit numero massimo di elementi nelle classifiche diagnostiche
     */
    public StressResultPrinter(
            MaGaConfig config,
            PrintStream out,
            int topLimit
    ) {
        this.config = Objects.requireNonNull(
                config,
                "config must not be null."
        );

        this.out = Objects.requireNonNull(
                out,
                "out must not be null."
        );

        if (topLimit <= 0) {
            throw new IllegalArgumentException("topLimit must be > 0.");
        }

        this.topLimit = topLimit;
    }

    /**
     * Stampa il report diagnostico dello stress test.
     *
     * @param snapshot snapshot usato come input del MA-GA
     * @param result risultato prodotto da MaGaOptimizer
     */
    public void printStressReport(
            SystemSnapshot snapshot,
            MaGaResult result
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(result, "result must not be null.");

        EvaluationBreakdown evaluation = result.getBestEvaluation();

        printHeader();
        printScenarioSummary(snapshot);
        printGaSummary(snapshot, result);
        printFitnessSummary(evaluation);
        printDecisionDistribution(evaluation);
        printDeadlineSummary(evaluation);
        printResourceSummary(evaluation);
        printMobilitySummary(evaluation);
        printTopProblematicTasks(evaluation);
        printGenerationTrend(result);
        printInterpretationHints(snapshot, result);
    }

    private void printHeader() {
        out.println("============================================================");
        out.println("MA-GA STRESS DIAGNOSTIC REPORT");
        out.println("============================================================");
        out.println();
    }

    private void printScenarioSummary(SystemSnapshot snapshot) {
        printSection("1. SCENARIO SUMMARY");

        Map<NodeType, Integer> candidateCountByType = countCandidatesByType(
                snapshot.getCandidateNodes()
        );

        int taskCount = snapshot.getTasks().size();
        int vehicleCount = snapshot.getVehicles().size();
        double requestRate = vehicleCount == 0
                ? 0.0
                : (double) taskCount / vehicleCount;

        out.println("Snapshot ID: " + snapshot.getSnapshotId());
        out.println("Simulation time: " + formatSeconds(snapshot.getTimeSeconds()));
        out.println("Vehicles: " + vehicleCount);
        out.println("Active tasks: " + taskCount);
        out.println("Task request rate: " + formatPercent(requestRate));
        out.println("Execution candidates: " + snapshot.getCandidateNodes().size());

        out.println();
        out.println("Candidates by type:");
        for (NodeType type : NodeType.values()) {
            out.println("- " + type + ": "
                    + candidateCountByType.getOrDefault(type, 0));
        }

        out.println();
        out.println("Physical execution nodes detected: "
                + countPhysicalExecutionNodes(snapshot.getCandidateNodes()));
        out.println();
    }

    private void printGaSummary(
            SystemSnapshot snapshot,
            MaGaResult result
    ) {
        printSection("2. GA EXECUTION SUMMARY");

        GaParameterScalingResult scalingResult =
                config.resolveGaParameterScaling(snapshot);

        GeneticAlgorithmConfig baseGaConfig =
                scalingResult.getBaseConfig();

        GeneticAlgorithmConfig effectiveGaConfig =
                scalingResult.getScaledConfig();

        double improvementAbsolute =
                result.getInitialBestFitness() - result.getFinalBestFitness();

        double improvementPercent = result.getInitialBestFitness() == 0.0
                ? 0.0
                : improvementAbsolute / result.getInitialBestFitness();

        out.println("Generations executed: " + result.getGenerationsExecuted());
        out.println("Stop reason: " + result.getStopReason());
        out.println("Initial best fitness: " + format(result.getInitialBestFitness()));
        out.println("Final best fitness: " + format(result.getFinalBestFitness()));
        out.println("Absolute improvement: " + format(improvementAbsolute));
        out.println("Relative improvement: " + formatPercent(improvementPercent));
        out.println("Final population size: " + result.getFinalPopulation().size());
        out.println();

        out.println("GA parameter scaling:");
        out.println("- scaling mode: " + scalingResult.getMode());
        out.println("- reason: " + scalingResult.getReason());
        out.println("- vehicles observed: " + scalingResult.getVehicleCount());
        out.println("- active tasks: " + scalingResult.getActiveTaskCount());
        out.println("- candidates: " + scalingResult.getCandidateCount());
        out.println("- average candidates per task: "
                + format(scalingResult.getAverageCandidatesPerTask()));
        out.println("- config changed: " + scalingResult.hasChanged());
        out.println();

        out.println("GA configuration:");
        out.println("- base populationSize: "
                + baseGaConfig.getPopulationSize());
        out.println("- effective populationSize: "
                + effectiveGaConfig.getPopulationSize());

        out.println("- base maxGenerations: "
                + baseGaConfig.getMaxGenerations());
        out.println("- effective maxGenerations: "
                + effectiveGaConfig.getMaxGenerations());

        out.println("- base elitismCount: "
                + baseGaConfig.getElitismCount());
        out.println("- effective elitismCount: "
                + effectiveGaConfig.getElitismCount());

        out.println("- base stallGenerations: "
                + baseGaConfig.getStallGenerations());
        out.println("- effective stallGenerations: "
                + effectiveGaConfig.getStallGenerations());

        out.println("- crossoverRate: "
                + format(effectiveGaConfig.getCrossoverRate()));
        out.println("- mutationRate: "
                + format(effectiveGaConfig.getMutationRate()));
        out.println("- fitnessImprovementEpsilon: "
                + format(effectiveGaConfig.getFitnessImprovementEpsilon()));
        out.println("- randomSeed: "
                + effectiveGaConfig.getRandomSeed());
        out.println();
    }

    private void printFitnessSummary(EvaluationBreakdown evaluation) {
        printSection("3. FITNESS BREAKDOWN");

        FitnessWeights weights = config.getFitnessWeights();

        double weightedT = weights.getWT()
                * evaluation.getNormalizedCompletionTime();

        double weightedL = weights.getWL()
                * evaluation.getNormalizedCommunicationLatency();

        double weightedMobility = weights.getWM()
                * evaluation.getNormalizedMobilityPenalty();

        double weightedResources = weights.getWR()
                * evaluation.getNormalizedResourcePenalty();

        out.println("Raw terms:");
        out.println("- T(C): " + formatSeconds(evaluation.getCompletionTimeSeconds()));
        out.println("- L(C): " + formatSeconds(evaluation.getCommunicationLatencySeconds()));
        out.println("- Pmob(C): " + format(evaluation.getMobilityPenalty()));
        out.println("- Pres(C): " + format(evaluation.getResourcePenalty()));

        out.println();
        out.println("Normalized terms:");
        out.println("- T_norm: " + format(evaluation.getNormalizedCompletionTime()));
        out.println("- L_norm: " + format(evaluation.getNormalizedCommunicationLatency()));
        out.println("- Pmob_norm: " + format(evaluation.getNormalizedMobilityPenalty()));
        out.println("- Pres_norm: " + format(evaluation.getNormalizedResourcePenalty()));

        out.println();
        out.println("Weighted contributions:");
        out.println("- wT*T_norm: " + format(weightedT));
        out.println("- wL*L_norm: " + format(weightedL));
        out.println("- wM*Pmob_norm: " + format(weightedMobility));
        out.println("- wR*Pres_norm: " + format(weightedResources));

        out.println();
        out.println("Final fitness J(C): " + format(evaluation.getFitness()));
        out.println("Dominant term: " + detectDominantTerm(
                weightedT,
                weightedL,
                weightedMobility,
                weightedResources
        ));
        out.println();
    }

    private void printDecisionDistribution(EvaluationBreakdown evaluation) {
        printSection("4. DECISION DISTRIBUTION");

        Map<NodeType, Integer> nodeTypeCounts = new EnumMap<>(NodeType.class);
        Map<DecisionType, Integer> decisionTypeCounts =
                new EnumMap<>(DecisionType.class);

        double offloadingRatioSum = 0.0;
        int offloadingRatioCount = 0;

        for (GeneEvaluationBreakdown gene : evaluation.getGeneBreakdowns()) {
            nodeTypeCounts.merge(gene.getNodeType(), 1, Integer::sum);
            decisionTypeCounts.merge(gene.getDecisionType(), 1, Integer::sum);

            offloadingRatioSum += gene.getOffloadingRatio();
            offloadingRatioCount++;
        }

        out.println("Selected candidates by node type:");
        for (NodeType type : NodeType.values()) {
            out.println("- " + type + ": "
                    + nodeTypeCounts.getOrDefault(type, 0));
        }

        out.println();
        out.println("Decision types:");
        for (DecisionType type : DecisionType.values()) {
            out.println("- " + type + ": "
                    + decisionTypeCounts.getOrDefault(type, 0));
        }

        out.println();
        out.println("Average offloading ratio: "
                + format(offloadingRatioCount == 0
                ? 0.0
                : offloadingRatioSum / offloadingRatioCount));
        out.println();
    }

    private void printDeadlineSummary(EvaluationBreakdown evaluation) {
        printSection("5. DEADLINE ANALYSIS");

        List<GeneEvaluationBreakdown> violations = evaluation.getGeneBreakdowns()
                .stream()
                .filter(gene -> !gene.isDeadlineRespected())
                .sorted(Comparator.comparingDouble(
                        this::deadlineViolationRatio
                ).reversed())
                .toList();

        int total = evaluation.getGeneBreakdowns().size();
        int violated = violations.size();

        out.println("Tasks evaluated: " + total);
        out.println("Deadlines respected: " + (total - violated));
        out.println("Deadlines violated: " + violated);
        out.println("Deadline violation rate: "
                + formatPercent(total == 0 ? 0.0 : (double) violated / total));
        out.println();

        if (violations.isEmpty()) {
            out.println("No deadline violations detected.");
            out.println();
            return;
        }

        out.println("Top deadline violations:");
        printGeneTableHeader();

        for (GeneEvaluationBreakdown gene : limit(violations)) {
            printGeneTableRow(
                    gene,
                    "deadlineRatio=" + format(deadlineViolationRatio(gene))
            );
        }

        out.println();
    }

    private void printResourceSummary(EvaluationBreakdown evaluation) {
        printSection("6. RESOURCE ANALYSIS");

        List<ExecutionNodeResourceUsageBreakdown> cpuViolations =
                evaluation.getExecutionNodeResourceUsageBreakdowns()
                        .stream()
                        .filter(ExecutionNodeResourceUsageBreakdown::hasCpuViolation)
                        .sorted(Comparator.comparingDouble(
                                ExecutionNodeResourceUsageBreakdown::getCpuUsagePercent
                        ).reversed())
                        .toList();

        List<ExecutionNodeResourceUsageBreakdown> cpuSaturated =
                evaluation.getExecutionNodeResourceUsageBreakdowns()
                        .stream()
                        .filter(usage -> !usage.hasCpuViolation())
                        .filter(usage -> usage.getCpuUsagePercent()
                                >= SATURATION_THRESHOLD_PERCENT)
                        .sorted(Comparator.comparingDouble(
                                ExecutionNodeResourceUsageBreakdown::getCpuUsagePercent
                        ).reversed())
                        .toList();

        List<LinkBandwidthUsageBreakdown> bandwidthViolations =
                evaluation.getLinkBandwidthUsageBreakdowns()
                        .stream()
                        .filter(LinkBandwidthUsageBreakdown::hasBandwidthViolation)
                        .sorted(Comparator.comparingDouble(
                                LinkBandwidthUsageBreakdown::getBandwidthUsagePercent
                        ).reversed())
                        .toList();

        List<LinkBandwidthUsageBreakdown> bandwidthSaturated =
                evaluation.getLinkBandwidthUsageBreakdowns()
                        .stream()
                        .filter(usage -> !usage.hasBandwidthViolation())
                        .filter(usage -> usage.getBandwidthUsagePercent()
                                >= SATURATION_THRESHOLD_PERCENT)
                        .sorted(Comparator.comparingDouble(
                                LinkBandwidthUsageBreakdown::getBandwidthUsagePercent
                        ).reversed())
                        .toList();

        out.println("CPU physical nodes tracked: "
                + evaluation.getExecutionNodeResourceUsageBreakdowns().size());
        out.println("CPU violations: " + cpuViolations.size());
        out.println("CPU saturated >= " + format(SATURATION_THRESHOLD_PERCENT)
                + "%: " + cpuSaturated.size());
        out.println("Bandwidth links tracked: "
                + evaluation.getLinkBandwidthUsageBreakdowns().size());
        out.println("Bandwidth violations: " + bandwidthViolations.size());
        out.println("Bandwidth saturated >= "
                + format(SATURATION_THRESHOLD_PERCENT)
                + "%: " + bandwidthSaturated.size());

        out.println();
        printTopCpuResources("Top CPU violations", cpuViolations);
        printTopCpuResources("Top CPU saturated resources", cpuSaturated);
        printTopBandwidthResources("Top bandwidth violations", bandwidthViolations);
        printTopBandwidthResources("Top bandwidth saturated links", bandwidthSaturated);

        out.println("Local workload summary:");
        printLocalWorkloadSummary(evaluation.getLocalResourceUsageBreakdowns());
        out.println();
    }

    private void printMobilitySummary(EvaluationBreakdown evaluation) {
        printSection("7. MOBILITY ANALYSIS");

        List<GeneEvaluationBreakdown> insufficientCoverage =
                evaluation.getGeneBreakdowns()
                        .stream()
                        .filter(gene -> gene.getNodeType() != NodeType.LOCAL)
                        .filter(gene -> !gene.isCoverageSufficient())
                        .sorted(Comparator.comparingDouble(
                                GeneEvaluationBreakdown::getMobilityPenalty
                        ).reversed())
                        .toList();

        List<GeneEvaluationBreakdown> nearCriticalCoverage =
                evaluation.getGeneBreakdowns()
                        .stream()
                        .filter(gene -> gene.getNodeType() != NodeType.LOCAL)
                        .filter(GeneEvaluationBreakdown::isCoverageSufficient)
                        .filter(this::isNearCriticalCoverage)
                        .sorted(Comparator.comparingDouble(
                                this::coverageMarginRatio
                        ))
                        .toList();

        List<GeneEvaluationBreakdown> highestMobilityPenalty =
                evaluation.getGeneBreakdowns()
                        .stream()
                        .filter(gene -> gene.getMobilityPenalty() > 0.0)
                        .sorted(Comparator.comparingDouble(
                                GeneEvaluationBreakdown::getMobilityPenalty
                        ).reversed())
                        .toList();

        double averageMobilityPenalty = evaluation.getGeneBreakdowns().isEmpty()
                ? 0.0
                : evaluation.getGeneBreakdowns()
                .stream()
                .mapToDouble(GeneEvaluationBreakdown::getMobilityPenalty)
                .average()
                .orElse(0.0);

        out.println("Total mobility penalty: "
                + format(evaluation.getMobilityPenalty()));
        out.println("Average mobility penalty per task: "
                + format(averageMobilityPenalty));
        out.println("Coverage insufficient tasks: "
                + insufficientCoverage.size());
        out.println("Coverage near-critical tasks: "
                + nearCriticalCoverage.size());
        out.println();

        if (!insufficientCoverage.isEmpty()) {
            out.println("Top insufficient coverage tasks:");
            printGeneTableHeader();
            for (GeneEvaluationBreakdown gene : limit(insufficientCoverage)) {
                printGeneTableRow(gene, "coverageRatio="
                        + format(coverageMarginRatio(gene)));
            }
            out.println();
        }

        if (!nearCriticalCoverage.isEmpty()) {
            out.println("Top near-critical coverage tasks:");
            printGeneTableHeader();
            for (GeneEvaluationBreakdown gene : limit(nearCriticalCoverage)) {
                printGeneTableRow(gene, "coverageRatio="
                        + format(coverageMarginRatio(gene)));
            }
            out.println();
        }

        if (!highestMobilityPenalty.isEmpty()) {
            out.println("Top mobility penalty tasks:");
            printGeneTableHeader();
            for (GeneEvaluationBreakdown gene : limit(highestMobilityPenalty)) {
                printGeneTableRow(gene, "mobilityPenalty="
                        + format(gene.getMobilityPenalty()));
            }
            out.println();
        }
    }

    private void printTopProblematicTasks(EvaluationBreakdown evaluation) {
        printSection("8. TOP PROBLEMATIC TASKS");

        List<GeneEvaluationBreakdown> byConstraintPenalty =
                evaluation.getGeneBreakdowns()
                        .stream()
                        .filter(gene -> gene.getConstraintPenalty() > 0.0)
                        .sorted(Comparator.comparingDouble(
                                GeneEvaluationBreakdown::getConstraintPenalty
                        ).reversed())
                        .toList();

        List<GeneEvaluationBreakdown> byCompletionTime =
                evaluation.getGeneBreakdowns()
                        .stream()
                        .sorted(Comparator.comparingDouble(
                                GeneEvaluationBreakdown::getCompletionTimeSeconds
                        ).reversed())
                        .toList();

        out.println("Top tasks by constraint penalty:");
        if (byConstraintPenalty.isEmpty()) {
            out.println("- none");
        } else {
            printGeneTableHeader();
            for (GeneEvaluationBreakdown gene : limit(byConstraintPenalty)) {
                printGeneTableRow(gene, "constraintPenalty="
                        + format(gene.getConstraintPenalty()));
            }
        }

        out.println();
        out.println("Top tasks by completion time:");
        printGeneTableHeader();
        for (GeneEvaluationBreakdown gene : limit(byCompletionTime)) {
            printGeneTableRow(gene, "completion="
                    + formatSeconds(gene.getCompletionTimeSeconds()));
        }

        out.println();
    }

    private void printGenerationTrend(MaGaResult result) {
        printSection("9. GENERATION TREND");

        List<GenerationStat> history = result.getGenerationHistory();

        if (history == null || history.isEmpty()) {
            out.println("Generation history not available.");
            out.println();
            return;
        }

        GenerationStat first = history.get(0);
        GenerationStat last = history.get(history.size() - 1);

        out.println("History size: " + history.size());
        out.println("First generation best: "
                + format(first.getBestFitness()));
        out.println("Last generation best: "
                + format(last.getBestFitness()));
        out.println("Last generation average: "
                + format(last.getAverageFitness()));
        out.println("Last generation worst: "
                + format(last.getWorstFitness()));

        out.println();
        out.println("Sampled generations:");
        for (GenerationStat stat : sampleHistory(history)) {
            out.println("- generation " + stat.getGenerationIndex()
                    + " | best=" + format(stat.getBestFitness())
                    + " | avg=" + format(stat.getAverageFitness())
                    + " | worst=" + format(stat.getWorstFitness()));
        }

        out.println();
    }

    private void printInterpretationHints(
            SystemSnapshot snapshot,
            MaGaResult result
    ) {
        printSection("10. INTERPRETATION HINTS");

        EvaluationBreakdown evaluation = result.getBestEvaluation();

        GaParameterScalingResult scalingResult =
                config.resolveGaParameterScaling(snapshot);

        GeneticAlgorithmConfig effectiveGaConfig =
                scalingResult.getScaledConfig();

        if (snapshot.getTasks().size() >= 50
                && effectiveGaConfig.getPopulationSize() <= 40) {
            out.println("DIAGNOSIS: large snapshot with small effective population size.");
            out.println("Suggested action: use ADAPTIVE scaling mode or increase scaling limits.");
        }

        boolean stoppedByMaxGenerations =
                "MAX_GENERATIONS_REACHED".equals(result.getStopReason().name());

        long deadlineViolations = evaluation.getGeneBreakdowns()
                .stream()
                .filter(gene -> !gene.isDeadlineRespected())
                .count();

        long cpuViolations = evaluation.getExecutionNodeResourceUsageBreakdowns()
                .stream()
                .filter(ExecutionNodeResourceUsageBreakdown::hasCpuViolation)
                .count();

        long bandwidthViolations = evaluation.getLinkBandwidthUsageBreakdowns()
                .stream()
                .filter(LinkBandwidthUsageBreakdown::hasBandwidthViolation)
                .count();

        out.println("- If final fitness is dominated by Pres(C), inspect deadline and resource violations first.");
        out.println("- CPU is interpreted by physical executionNodeId.");
        out.println("- Bandwidth is interpreted by candidate/link.");
        out.println("- Local and cloud coverage times are conventional values from MobilityConfig.");
        out.println("- EDGE and V2V coverage times are computed, not read from JSON.");

        out.println();

        if (stoppedByMaxGenerations) {
            out.println("DIAGNOSIS: the GA stopped because it reached maxGenerations.");
            out.println("Suggested action: increase maxGenerations and/or populationSize, or add an adaptive GA parameter scaler.");
        }

        if (deadlineViolations > 0) {
            out.println("DIAGNOSIS: deadline violations detected: "
                    + deadlineViolations + ".");
            out.println("Suggested action: inspect top deadline violations and check whether local CPU, remote CPU, bandwidth or latency is the bottleneck.");
        }

        if (cpuViolations > 0) {
            out.println("DIAGNOSIS: CPU violations detected on physical execution nodes: "
                    + cpuViolations + ".");
            out.println("Suggested action: verify availableCpu coherence per executionNodeId and inspect CPU aggregation.");
        }

        if (bandwidthViolations > 0) {
            out.println("DIAGNOSIS: bandwidth violations detected on candidate links: "
                    + bandwidthViolations + ".");
            out.println("Suggested action: inspect selected candidates with overused bandwidth.");
        }



        out.println();
    }

    private Map<NodeType, Integer> countCandidatesByType(
            List<NodeCandidate> candidates
    ) {
        Map<NodeType, Integer> result = new EnumMap<>(NodeType.class);

        for (NodeCandidate candidate : candidates) {
            result.merge(candidate.getType(), 1, Integer::sum);
        }

        return result;
    }

    private int countPhysicalExecutionNodes(List<NodeCandidate> candidates) {
        Map<String, Boolean> nodes = new HashMap<>();

        for (NodeCandidate candidate : candidates) {
            nodes.put(candidate.getExecutionNodeId(), true);
        }

        return nodes.size();
    }

    private void printTopCpuResources(
            String title,
            List<ExecutionNodeResourceUsageBreakdown> usages
    ) {
        out.println(title + ":");

        if (usages.isEmpty()) {
            out.println("- none");
            out.println();
            return;
        }

        for (ExecutionNodeResourceUsageBreakdown usage : limit(usages)) {
            out.println("- " + usage.getExecutionNodeId()
                    + " [" + usage.getNodeType() + "]"
                    + " used=" + format(usage.getUsedCpu())
                    + " / available=" + format(usage.getAvailableCpu())
                    + " | usage=" + format(usage.getCpuUsagePercent()) + "%"
                    + " | overflowRatio=" + format(usage.getCpuOverflowRatio()));
        }

        out.println();
    }

    private void printTopBandwidthResources(
            String title,
            List<LinkBandwidthUsageBreakdown> usages
    ) {
        out.println(title + ":");

        if (usages.isEmpty()) {
            out.println("- none");
            out.println();
            return;
        }

        for (LinkBandwidthUsageBreakdown usage : limit(usages)) {
            out.println("- " + usage.getCandidateId()
                    + " [" + usage.getNodeType() + "]"
                    + " source=" + usage.getSourceVehicleId()
                    + " execution=" + usage.getExecutionNodeId()
                    + " used=" + format(usage.getUsedBandwidth())
                    + " / available=" + format(usage.getAvailableBandwidth())
                    + " | usage=" + format(usage.getBandwidthUsagePercent()) + "%"
                    + " | overflowRatio="
                    + format(usage.getBandwidthOverflowRatio()));
        }

        out.println();
    }

    private void printLocalWorkloadSummary(
            List<LocalResourceUsageBreakdown> localUsages
    ) {
        long vehiclesWithLocalWorkload = localUsages
                .stream()
                .filter(LocalResourceUsageBreakdown::hasLocalWorkload)
                .count();

        double maxLocalTime = localUsages
                .stream()
                .mapToDouble(LocalResourceUsageBreakdown::getMaxLocalExecutionTimeSeconds)
                .max()
                .orElse(0.0);

        out.println("- vehicles with local workload: "
                + vehiclesWithLocalWorkload);
        out.println("- max local execution time contribution: "
                + formatSeconds(maxLocalTime));

        List<LocalResourceUsageBreakdown> topLocal =
                localUsages.stream()
                        .filter(LocalResourceUsageBreakdown::hasLocalWorkload)
                        .sorted(Comparator.comparingDouble(
                                LocalResourceUsageBreakdown::getMaxLocalExecutionTimeSeconds
                        ).reversed())
                        .toList();

        if (!topLocal.isEmpty()) {
            out.println("- top local workloads:");
            for (LocalResourceUsageBreakdown local : limit(topLocal)) {
                out.println("  * " + local.getVehicleId()
                        + " | localCpuCycles="
                        + format(local.getLocalCpuCycles())
                        + " | maxLocalTime="
                        + formatSeconds(local.getMaxLocalExecutionTimeSeconds()));
            }
        }
    }

    private void printGeneTableHeader() {
        out.println("task | source | type | decision | candidate | completion | deadline | coverage | Pmob | Pconstraint | note");
    }

    private void printGeneTableRow(
            GeneEvaluationBreakdown gene,
            String note
    ) {
        out.println(gene.getTaskId()
                + " | " + gene.getSourceVehicleId()
                + " | " + gene.getNodeType()
                + " | " + gene.getDecisionType()
                + " | " + gene.getSelectedCandidateId()
                + " | " + formatSeconds(gene.getCompletionTimeSeconds())
                + " | " + formatSeconds(gene.getDeadlineSeconds())
                + " | " + formatSeconds(gene.getCoverageTimeSeconds())
                + " | " + format(gene.getMobilityPenalty())
                + " | " + format(gene.getConstraintPenalty())
                + " | " + note);
    }

    private boolean isNearCriticalCoverage(GeneEvaluationBreakdown gene) {
        if (gene.getCompletionTimeSeconds() <= 0.0) {
            return false;
        }

        double ratio = coverageMarginRatio(gene);
        return ratio >= 1.0 && ratio <= NEAR_COVERAGE_THRESHOLD_RATIO;
    }

    private double coverageMarginRatio(GeneEvaluationBreakdown gene) {
        if (gene.getCompletionTimeSeconds() <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }

        return gene.getCoverageTimeSeconds()
                / gene.getCompletionTimeSeconds();
    }

    private double deadlineViolationRatio(GeneEvaluationBreakdown gene) {
        if (gene.getDeadlineSeconds() <= 0.0) {
            return 0.0;
        }

        return Math.max(
                0.0,
                (gene.getCompletionTimeSeconds() - gene.getDeadlineSeconds())
                        / gene.getDeadlineSeconds()
        );
    }

    private String detectDominantTerm(
            double weightedT,
            double weightedL,
            double weightedMobility,
            double weightedResources
    ) {
        double max = Math.max(
                Math.max(weightedT, weightedL),
                Math.max(weightedMobility, weightedResources)
        );

        if (max == weightedResources) {
            return "resource/constraint penalty";
        }

        if (max == weightedT) {
            return "completion time";
        }

        if (max == weightedL) {
            return "communication latency";
        }

        return "mobility penalty";
    }

    private <T> List<T> limit(List<T> values) {
        if (values.size() <= topLimit) {
            return values;
        }

        return values.subList(0, topLimit);
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

    private void printSection(String title) {
        out.println("------------------------------------------------------------");
        out.println(title);
        out.println("------------------------------------------------------------");
    }

    private String format(double value) {
        if (Double.isInfinite(value)) {
            return "Infinity";
        }

        if (Double.isNaN(value)) {
            return "NaN";
        }

        return String.format("%.6f", value);
    }

    private String formatSeconds(double value) {
        return format(value) + " s";
    }

    private String formatPercent(double value) {
        return format(value * 100.0) + "%";
    }
}
