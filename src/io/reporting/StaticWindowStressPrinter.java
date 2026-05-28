package io.reporting;

import ga.core.MaGaResult;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Printer diagnostico per stress test su finestre temporali statiche.
 *
 * È pensato per leggere una sequenza di snapshot consecutivi e capire:
 *
 * - come varia la fitness finestra per finestra;
 * - quanto il GA migliora dentro ogni finestra;
 * - quali decisioni vengono scelte;
 * - quante deadline vengono violate;
 * - quali risorse CPU/banda diventano critiche;
 * - se il warm start sta aiutando o meno.
 *
 * Questo printer non sostituisce ResultPrinter: riduce il dettaglio per task
 * e produce una vista temporale più leggibile.
 */
public final class StaticWindowStressPrinter {

    private static final double SATURATION_THRESHOLD_PERCENT = 95.0;

    private final PrintStream out;

    public StaticWindowStressPrinter() {
        this(System.out);
    }

    public StaticWindowStressPrinter(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    /**
     * Stampa un report su più finestre statiche.
     *
     * @param steps risultati ordinati temporalmente
     */
    public void print(List<StepReport> steps) {
        Objects.requireNonNull(steps, "steps must not be null.");

        printHeader();
        printGlobalSummary(steps);
        printWindowTable(steps);
        printDecisionTrend(steps);
        printDeadlineTrend(steps);
        printResourceTrend(steps);
        printWorstWindows(steps);
        printInterpretationHints(steps);
    }

    private void printHeader() {
        out.println("============================================================");
        out.println("MA-GA STATIC WINDOW STRESS REPORT");
        out.println("============================================================");
        out.println();
    }

    private void printGlobalSummary(List<StepReport> steps) {
        printSection("1. GLOBAL SUMMARY");

        if (steps.isEmpty()) {
            out.println("No step available.");
            out.println();
            return;
        }

        int totalTasks = 0;
        int totalDeadlineViolations = 0;
        int totalCpuViolations = 0;
        int totalBandwidthViolations = 0;

        double firstFitness = steps.get(0).getFinalFitness();
        double lastFitness = steps.get(steps.size() - 1).getFinalFitness();

        for (StepReport step : steps) {
            totalTasks += step.getTaskCount();
            totalDeadlineViolations += step.getDeadlineViolationCount();
            totalCpuViolations += step.getCpuViolationCount();
            totalBandwidthViolations += step.getBandwidthViolationCount();
        }

        out.println("Windows analyzed: " + steps.size());
        out.println("First window time: " + formatSeconds(steps.get(0).getSnapshotTimeSeconds()));
        out.println("Last window time: " + formatSeconds(steps.get(steps.size() - 1).getSnapshotTimeSeconds()));
        out.println("Total task evaluations: " + totalTasks);
        out.println("Total deadline violations: " + totalDeadlineViolations);
        out.println("Total CPU violations: " + totalCpuViolations);
        out.println("Total bandwidth violations: " + totalBandwidthViolations);
        out.println("First final fitness: " + format(firstFitness));
        out.println("Last final fitness: " + format(lastFitness));
        out.println("Final fitness variation: " + format(lastFitness - firstFitness));
        out.println();
    }

    private void printWindowTable(List<StepReport> steps) {
        printSection("2. WINDOW-BY-WINDOW SUMMARY");

        out.println("idx | time | snapshot | tasks | cand | initPop | finalPop | gen | stop | initBest | finalBest | gain% | T | L | Pmob | Pres");

        for (StepReport step : steps) {
            out.println(step.getWindowIndex()
                    + " | " + formatSeconds(step.getSnapshotTimeSeconds())
                    + " | " + step.getSnapshotId()
                    + " | " + step.getTaskCount()
                    + " | " + step.getCandidateCount()
                    + " | " + step.getInitialPopulationSize()
                    + " | " + step.getFinalPopulationSize()
                    + " | " + step.getGenerationsExecuted()
                    + " | " + step.getStopReason()
                    + " | " + format(step.getInitialBestFitness())
                    + " | " + format(step.getFinalFitness())
                    + " | " + formatPercent(step.getImprovementRatio())
                    + " | " + formatSeconds(step.getCompletionTimeSeconds())
                    + " | " + formatSeconds(step.getCommunicationLatencySeconds())
                    + " | " + format(step.getMobilityPenalty())
                    + " | " + format(step.getResourcePenalty()));
        }

        out.println();
    }

    private void printDecisionTrend(List<StepReport> steps) {
        printSection("3. DECISION TREND");

        out.println("idx | LOCAL | EDGE | CLOUD | VEHICLE | deadlineViol | coverageInsuff | avgOffloadingRatio");

        for (StepReport step : steps) {
            out.println(step.getWindowIndex()
                    + " | " + step.getNodeTypeCount(NodeType.LOCAL)
                    + " | " + step.getNodeTypeCount(NodeType.EDGE)
                    + " | " + step.getNodeTypeCount(NodeType.CLOUD)
                    + " | " + step.getNodeTypeCount(NodeType.VEHICLE)
                    + " | " + step.getDeadlineViolationCount()
                    + " | " + step.getCoverageInsufficientCount()
                    + " | " + format(step.getAverageOffloadingRatio()));
        }

        out.println();
    }

    private void printDeadlineTrend(List<StepReport> steps) {
        printSection("4. DEADLINE TREND");

        out.println("idx | respected | violated | violationRate | worstTask | worstCompletion | worstDeadline | worstRatio");

        for (StepReport step : steps) {
            GeneEvaluationBreakdown worst = step.getWorstDeadlineViolation();

            if (worst == null) {
                out.println(step.getWindowIndex()
                        + " | " + (step.getTaskCount() - step.getDeadlineViolationCount())
                        + " | 0 | 0.000000% | none | - | - | -");
                continue;
            }

            out.println(step.getWindowIndex()
                    + " | " + (step.getTaskCount() - step.getDeadlineViolationCount())
                    + " | " + step.getDeadlineViolationCount()
                    + " | " + formatPercent(step.getDeadlineViolationRate())
                    + " | " + worst.getTaskId()
                    + " | " + formatSeconds(worst.getCompletionTimeSeconds())
                    + " | " + formatSeconds(worst.getDeadlineSeconds())
                    + " | " + format(step.deadlineViolationRatio(worst)));
        }

        out.println();
    }

    private void printResourceTrend(List<StepReport> steps) {
        printSection("5. RESOURCE TREND");

        out.println("idx | cpuViol | cpuSat>=95 | bwViol | bwSat>=95 | worstCpuNode | worstCpuUsage | worstBwLink | worstBwUsage");

        for (StepReport step : steps) {
            ExecutionNodeResourceUsageBreakdown worstCpu = step.getWorstCpuUsage();
            LinkBandwidthUsageBreakdown worstBw = step.getWorstBandwidthUsage();

            out.println(step.getWindowIndex()
                    + " | " + step.getCpuViolationCount()
                    + " | " + step.getCpuSaturationCount()
                    + " | " + step.getBandwidthViolationCount()
                    + " | " + step.getBandwidthSaturationCount()
                    + " | " + (worstCpu == null ? "none" : worstCpu.getExecutionNodeId())
                    + " | " + (worstCpu == null ? "-" : format(worstCpu.getCpuUsagePercent()) + "%")
                    + " | " + (worstBw == null ? "none" : worstBw.getCandidateId())
                    + " | " + (worstBw == null ? "-" : format(worstBw.getBandwidthUsagePercent()) + "%"));
        }

        out.println();
    }

    private void printWorstWindows(List<StepReport> steps) {
        printSection("6. WORST WINDOWS");

        List<StepReport> byFitness = new ArrayList<>(steps);
        byFitness.sort(Comparator.comparingDouble(StepReport::getFinalFitness).reversed());

        out.println("Worst windows by final fitness:");
        for (StepReport step : limit(byFitness, 5)) {
            out.println("- idx=" + step.getWindowIndex()
                    + " snapshot=" + step.getSnapshotId()
                    + " finalFitness=" + format(step.getFinalFitness())
                    + " Pres=" + format(step.getResourcePenalty())
                    + " deadlineViol=" + step.getDeadlineViolationCount()
                    + " cpuViol=" + step.getCpuViolationCount());
        }

        out.println();

        List<StepReport> byDeadline = new ArrayList<>(steps);
        byDeadline.sort(Comparator.comparingInt(StepReport::getDeadlineViolationCount).reversed());

        out.println("Worst windows by deadline violations:");
        for (StepReport step : limit(byDeadline, 5)) {
            out.println("- idx=" + step.getWindowIndex()
                    + " snapshot=" + step.getSnapshotId()
                    + " violations=" + step.getDeadlineViolationCount()
                    + " rate=" + formatPercent(step.getDeadlineViolationRate())
                    + " finalFitness=" + format(step.getFinalFitness()));
        }

        out.println();
    }

    private void printInterpretationHints(List<StepReport> steps) {
        printSection("7. INTERPRETATION HINTS");

        boolean hasCpuViolation = steps.stream().anyMatch(step -> step.getCpuViolationCount() > 0);
        boolean hasBandwidthViolation = steps.stream().anyMatch(step -> step.getBandwidthViolationCount() > 0);
        boolean hasDeadlineViolation = steps.stream().anyMatch(step -> step.getDeadlineViolationCount() > 0);
        boolean allMaxGenerations = steps.stream().allMatch(step -> step.getStopReason().contains("MAX_GENERATIONS"));

        out.println("- This report tests static scheduled windows under repeated stress.");
        out.println("- If the initial population size is greater than 0 after the first window, warm start is active.");
        out.println("- CPU is interpreted as a physical node resource, aggregated by executionNodeId.");
        out.println("- Bandwidth is interpreted as a candidate/link resource, aggregated by candidateId.");
        out.println("- Bandwidth repair remains an open issue.");
        out.println("- CPU repair is recommended if CPU violations persist across windows.");

        out.println();

        if (allMaxGenerations) {
            out.println("DIAGNOSIS: every window reached MAX_GENERATIONS.");
            out.println("Action: check whether adaptive scaling limits are still too low or whether resource repair is needed.");
        }

        if (hasDeadlineViolation) {
            out.println("DIAGNOSIS: deadline violations appear in at least one window.");
            out.println("Action: inspect the worst windows by deadline violations and compare with CPU saturation.");
        }

        if (hasCpuViolation) {
            out.println("DIAGNOSIS: CPU violations appear in at least one window.");
            out.println("Action: introduce CPU aggregate repair before evaluation or immediately after genetic operators.");
        }

        if (hasBandwidthViolation) {
            out.println("DIAGNOSIS: bandwidth violations appear in at least one window.");
            out.println("Action: keep this as OpenIssue if bandwidth repair is intentionally postponed.");
        }

        out.println();
    }

    private void printSection(String title) {
        out.println("------------------------------------------------------------");
        out.println(title);
        out.println("------------------------------------------------------------");
    }

    private static <T> List<T> limit(List<T> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }

        return values.subList(0, limit);
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
     * Risultato diagnostico di una singola finestra statica.
     */
    public static final class StepReport {

        private final int windowIndex;
        private final SystemSnapshot snapshot;
        private final MaGaResult result;
        private final int initialPopulationSize;
        private final long runtimeMillis;

        public StepReport(
                int windowIndex,
                SystemSnapshot snapshot,
                MaGaResult result,
                int initialPopulationSize,
                long runtimeMillis
        ) {
            this.windowIndex = windowIndex;
            this.snapshot = Objects.requireNonNull(
                    snapshot,
                    "snapshot must not be null."
            );
            this.result = Objects.requireNonNull(
                    result,
                    "result must not be null."
            );
            this.initialPopulationSize = initialPopulationSize;
            this.runtimeMillis = runtimeMillis;
        }

        public int getWindowIndex() {
            return windowIndex;
        }

        public String getSnapshotId() {
            return snapshot.getSnapshotId();
        }

        public double getSnapshotTimeSeconds() {
            return snapshot.getTimeSeconds();
        }

        public int getTaskCount() {
            return snapshot.getTasks().size();
        }

        public int getCandidateCount() {
            return snapshot.getCandidateNodes().size();
        }

        public int getInitialPopulationSize() {
            return initialPopulationSize;
        }

        public int getFinalPopulationSize() {
            return result.getFinalPopulation().size();
        }

        public int getGenerationsExecuted() {
            return result.getGenerationsExecuted();
        }

        public String getStopReason() {
            return String.valueOf(result.getStopReason());
        }

        public double getInitialBestFitness() {
            return result.getInitialBestFitness();
        }

        public double getFinalFitness() {
            return result.getFinalBestFitness();
        }

        public double getImprovementRatio() {
            if (result.getInitialBestFitness() == 0.0) {
                return 0.0;
            }

            return (result.getInitialBestFitness() - result.getFinalBestFitness())
                    / result.getInitialBestFitness();
        }

        public long getRuntimeMillis() {
            return runtimeMillis;
        }

        public EvaluationBreakdown getEvaluation() {
            return result.getBestEvaluation();
        }

        public double getCompletionTimeSeconds() {
            return getEvaluation().getCompletionTimeSeconds();
        }

        public double getCommunicationLatencySeconds() {
            return getEvaluation().getCommunicationLatencySeconds();
        }

        public double getMobilityPenalty() {
            return getEvaluation().getMobilityPenalty();
        }

        public double getResourcePenalty() {
            return getEvaluation().getResourcePenalty();
        }

        public int getNodeTypeCount(NodeType type) {
            int count = 0;

            for (GeneEvaluationBreakdown gene : getEvaluation().getGeneBreakdowns()) {
                if (gene.getNodeType() == type) {
                    count++;
                }
            }

            return count;
        }

        public double getAverageOffloadingRatio() {
            if (getEvaluation().getGeneBreakdowns().isEmpty()) {
                return 0.0;
            }

            return getEvaluation().getGeneBreakdowns()
                    .stream()
                    .mapToDouble(GeneEvaluationBreakdown::getOffloadingRatio)
                    .average()
                    .orElse(0.0);
        }

        public int getDeadlineViolationCount() {
            int count = 0;

            for (GeneEvaluationBreakdown gene : getEvaluation().getGeneBreakdowns()) {
                if (!gene.isDeadlineRespected()) {
                    count++;
                }
            }

            return count;
        }

        public double getDeadlineViolationRate() {
            if (getTaskCount() == 0) {
                return 0.0;
            }

            return (double) getDeadlineViolationCount() / getTaskCount();
        }

        public GeneEvaluationBreakdown getWorstDeadlineViolation() {
            return getEvaluation().getGeneBreakdowns()
                    .stream()
                    .filter(gene -> !gene.isDeadlineRespected())
                    .max(Comparator.comparingDouble(this::deadlineViolationRatio))
                    .orElse(null);
        }

        public double deadlineViolationRatio(GeneEvaluationBreakdown gene) {
            if (gene.getDeadlineSeconds() <= 0.0) {
                return 0.0;
            }

            return Math.max(
                    0.0,
                    (gene.getCompletionTimeSeconds() - gene.getDeadlineSeconds())
                            / gene.getDeadlineSeconds()
            );
        }

        public int getCoverageInsufficientCount() {
            int count = 0;

            for (GeneEvaluationBreakdown gene : getEvaluation().getGeneBreakdowns()) {
                if (gene.getNodeType() != NodeType.LOCAL
                        && !gene.isCoverageSufficient()) {
                    count++;
                }
            }

            return count;
        }

        public int getCpuViolationCount() {
            int count = 0;

            for (ExecutionNodeResourceUsageBreakdown usage
                    : getEvaluation().getExecutionNodeResourceUsageBreakdowns()) {
                if (usage.hasCpuViolation()) {
                    count++;
                }
            }

            return count;
        }

        public int getCpuSaturationCount() {
            int count = 0;

            for (ExecutionNodeResourceUsageBreakdown usage
                    : getEvaluation().getExecutionNodeResourceUsageBreakdowns()) {
                if (!usage.hasCpuViolation()
                        && usage.getCpuUsagePercent()
                        >= SATURATION_THRESHOLD_PERCENT) {
                    count++;
                }
            }

            return count;
        }

        public ExecutionNodeResourceUsageBreakdown getWorstCpuUsage() {
            return getEvaluation().getExecutionNodeResourceUsageBreakdowns()
                    .stream()
                    .max(Comparator.comparingDouble(
                            ExecutionNodeResourceUsageBreakdown::getCpuUsagePercent
                    ))
                    .orElse(null);
        }

        public int getBandwidthViolationCount() {
            int count = 0;

            for (LinkBandwidthUsageBreakdown usage
                    : getEvaluation().getLinkBandwidthUsageBreakdowns()) {
                if (usage.hasBandwidthViolation()) {
                    count++;
                }
            }

            return count;
        }

        public int getBandwidthSaturationCount() {
            int count = 0;

            for (LinkBandwidthUsageBreakdown usage
                    : getEvaluation().getLinkBandwidthUsageBreakdowns()) {
                if (!usage.hasBandwidthViolation()
                        && usage.getBandwidthUsagePercent()
                        >= SATURATION_THRESHOLD_PERCENT) {
                    count++;
                }
            }

            return count;
        }

        public LinkBandwidthUsageBreakdown getWorstBandwidthUsage() {
            return getEvaluation().getLinkBandwidthUsageBreakdowns()
                    .stream()
                    .max(Comparator.comparingDouble(
                            LinkBandwidthUsageBreakdown::getBandwidthUsagePercent
                    ))
                    .orElse(null);
        }
    }
}
