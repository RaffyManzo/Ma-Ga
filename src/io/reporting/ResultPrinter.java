package io.reporting;

import config.fitness.FitnessWeights;
import config.ga.GeneticAlgorithmConfig;
import config.MaGaConfig;
import config.fitness.NormalizationConfig;
import config.fitness.PenaltyConfig;
import ga.fitness.FitnessEvaluator;
import ga.core.GenerationStat;
import ga.core.MaGaResult;
import model.node.NodeCandidate;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Stampa un report tecnico leggibile del risultato prodotto dal MA-GA.
 *
 * Questa classe non ricalcola la fitness.
 * Legge solo i dati già calcolati da FitnessEvaluator e raccolti in MaGaResult.
 */
public final class ResultPrinter {

    private static final double SATURATION_NOTICE_THRESHOLD_PERCENT = 99.999;

    private final MaGaConfig config;
    private final PrintStream out;

    /**
     * Costruisce il printer usando System.out.
     *
     * Parametri in ingresso:
     * - config: configurazione usata dal MA-GA.
     *
     * Output:
     * - nuovo ResultPrinter.
     */
    public ResultPrinter(MaGaConfig config) {
        this(config, System.out);
    }

    /**
     * Costruisce il printer usando uno stream personalizzato.
     *
     * Parametri in ingresso:
     * - config: configurazione usata dal MA-GA;
     * - out: stream di stampa.
     *
     * Output:
     * - nuovo ResultPrinter.
     */
    public ResultPrinter(MaGaConfig config, PrintStream out) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    /**
     * Stampa il report completo.
     *
     * Parametri in ingresso:
     * - snapshot: snapshot usato come input;
     * - result: risultato completo prodotto da MaGaOptimizer.
     *
     * Output:
     * - nessun valore restituito;
     * - stampa il report.
     */
    public void printOptimizationResult(SystemSnapshot snapshot, MaGaResult result) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(result, "result must not be null.");

        printHeader();
        printSnapshotSummary(snapshot);
        printConfigurationSummary();
        printGaExecutionSummary(result);
        printBestChromosome(result);
        printFitnessBreakdown(result.getBestEvaluation());
        printTaskLevelAnalysis(result.getBestEvaluation());
        printResourceUsage(result.getBestEvaluation());
        printWarnings(result);
        printFinalInterpretation(result);
    }

    private void printHeader() {
        out.println("============================================================");
        out.println("MA-GA OPTIMIZATION REPORT");
        out.println("============================================================");
        out.println();
    }

    private void printSnapshotSummary(SystemSnapshot snapshot) {
        printSectionTitle("1. SNAPSHOT SUMMARY");

        out.println("Snapshot ID: " + snapshot.getSnapshotId());
        out.println("Simulation time: " + formatSeconds(snapshot.getTimeSeconds()));
        out.println("Number of vehicles: " + snapshot.getVehicles().size());
        out.println("Number of tasks: " + snapshot.getTasks().size());
        out.println("Number of execution candidates: " + snapshot.getCandidateNodes().size());
        out.println();

        out.println("Vehicles:");
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            out.println("- " + vehicle.getVehicleId());
            out.println("  position: (" + format(vehicle.getX()) + ", " + format(vehicle.getY()) + ")");
            out.println("  speed: " + format(vehicle.getSpeed()) + " m/s");
            out.println("  local CPU: " + format(vehicle.getLocalCpu()) + " cycles/s");
        }
        out.println();

        out.println("Tasks:");
        for (TaskInstance task : snapshot.getTasks()) {
            out.println("- " + task.getTaskId());
            out.println("  source vehicle: " + task.getSourceVehicleId());
            out.println("  input size: " + format(task.getInputSizeBits()) + " bits");
            out.println("  output size: " + format(task.getOutputSizeBits()) + " bits");
            out.println("  CPU cycles: " + format(task.getCpuCycles()));
            out.println("  deadline: " + formatSeconds(task.getDeadlineSeconds()));
        }
        out.println();

        out.println("Execution candidates:");
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            out.println("- " + candidate.getCandidateId() + " [" + candidate.getType() + "]");
            out.println("  source vehicle: " + candidate.getSourceVehicleId());
            out.println("  execution node: " + candidate.getExecutionNodeId());
            out.println("  available CPU: " + format(candidate.getAvailableCpu()) + " cycles/s");
            out.println("  available bandwidth: " + format(candidate.getAvailableBandwidth()) + " bit/s");
            out.println("  base latency: " + formatSeconds(candidate.getBaseLatencySeconds()));
            out.println("  coverage time: " + formatSeconds(candidate.getCoverageTimeSeconds()));
        }
        out.println();
    }

    private void printConfigurationSummary() {
        printSectionTitle("2. CONFIGURATION SUMMARY");

        FitnessWeights weights = config.getFitnessWeights();
        PenaltyConfig penalties = config.getPenaltyConfig();
        NormalizationConfig normalization = config.getNormalizationConfig();
        GeneticAlgorithmConfig ga = config.getGeneticAlgorithmConfig();

        out.println("Fitness weights:");
        out.println("wT = " + format(weights.getWT()) + " -> completion time");
        out.println("wL = " + format(weights.getWL()) + " -> communication latency");
        out.println("wM = " + format(weights.getWM()) + " -> mobility penalty");
        out.println("wR = " + format(weights.getWR()) + " -> resource penalty");
        out.println();

        out.println("Penalty configuration:");
        out.println("coverageRiskWeight      = " + format(penalties.getCoverageRiskWeight()));
        out.println("linkInstabilityWeight   = " + format(penalties.getLinkInstabilityWeight()));
        out.println("handoverRiskWeight      = " + format(penalties.getHandoverRiskWeight()));
        out.println("bandwidthOveruseWeight  = " + format(penalties.getBandwidthOveruseWeight()));
        out.println("cpuOveruseWeight        = " + format(penalties.getCpuOveruseWeight()));
        out.println("deadlineViolationWeight = " + format(penalties.getDeadlineViolationWeight()));
        out.println();

        out.println("Normalization references:");
        out.println("T_ref    = " + format(normalization.getTRef()));
        out.println("L_ref    = " + format(normalization.getLRef()));
        out.println("Pmob_ref = " + format(normalization.getPmobRef()));
        out.println("Pres_ref = " + format(normalization.getPresRef()));
        out.println();

        out.println("GA parameters:");
        out.println("populationSize            = " + ga.getPopulationSize());
        out.println("maxGenerations            = " + ga.getMaxGenerations());
        out.println("crossoverRate             = " + format(ga.getCrossoverRate()));
        out.println("mutationRate              = " + format(ga.getMutationRate()));
        out.println("elitismCount              = " + ga.getElitismCount());
        out.println("stallGenerations          = " + ga.getStallGenerations());
        out.println("fitnessImprovementEpsilon = " + format(ga.getFitnessImprovementEpsilon()));
        out.println("randomSeed                = " + ga.getRandomSeed());
        out.println();
    }

    private void printGaExecutionSummary(MaGaResult result) {
        printSectionTitle("3. GA EXECUTION SUMMARY");

        out.println("Snapshot ID: " + result.getSnapshotId());
        out.println("Snapshot time: " + formatSeconds(result.getSnapshotTimeSeconds()));
        out.println("Generations executed: " + result.getGenerationsExecuted());
        out.println("Stop reason: " + result.getStopReason());
        out.println("Initial best fitness: " + format(result.getInitialBestFitness()));
        out.println("Final best fitness: " + format(result.getFinalBestFitness()));
        out.println();

        out.println("Fitness history:");
        for (GenerationStat stat : result.getGenerationHistory()) {
            out.println("- generation " + stat.getGenerationIndex()
                    + " | best=" + format(stat.getBestFitness())
                    + " | avg=" + format(stat.getAverageFitness())
                    + " | worst=" + format(stat.getWorstFitness()));
        }
        out.println();
    }

    private void printBestChromosome(MaGaResult result) {
        printSectionTitle("4. BEST CHROMOSOME");

        out.println("Overall fitness: " + format(result.getBestChromosome().getFitness()));
        out.println();

        int index = 1;

        for (FitnessEvaluator.GeneEvaluationBreakdown gene
                : result.getBestEvaluation().getGeneBreakdowns()) {

            out.println("[" + index + "] Task: " + gene.getTaskId());
            out.println("    source vehicle: " + gene.getSourceVehicleId());
            out.println("    selected candidate: " + gene.getSelectedCandidateId());
            out.println("    execution node: " + gene.getExecutionNodeId());
            out.println("    candidate type: " + gene.getNodeType());
            out.println("    decision type: " + gene.getDecisionType());
            out.println("    offloading ratio p_i: " + format(gene.getOffloadingRatio()));
            out.println("    allocated CPU f_i: " + format(gene.getAllocatedCpu()) + " cycles/s");
            out.println("    allocated bandwidth b_i: " + format(gene.getAllocatedBandwidth()) + " bit/s");
            index++;
        }

        out.println();
    }

    private void printFitnessBreakdown(FitnessEvaluator.EvaluationBreakdown breakdown) {
        printSectionTitle("5. FITNESS BREAKDOWN");

        FitnessWeights weights = config.getFitnessWeights();

        out.println("J(C) = wT*T(C) + wL*L(C) + wM*Pmob(C) + wR*Pres(C)");
        out.println();

        out.println("Raw terms:");
        out.println("T(C)     = " + formatSeconds(breakdown.getCompletionTimeSeconds()));
        out.println("L(C)     = " + formatSeconds(breakdown.getCommunicationLatencySeconds()));
        out.println("Pmob(C)  = " + format(breakdown.getMobilityPenalty()));
        out.println("Pres(C)  = " + format(breakdown.getResourcePenalty()));
        out.println();

        out.println("Normalized terms:");
        out.println("T_norm    = " + format(breakdown.getNormalizedCompletionTime()));
        out.println("L_norm    = " + format(breakdown.getNormalizedCommunicationLatency()));
        out.println("Pmob_norm = " + format(breakdown.getNormalizedMobilityPenalty()));
        out.println("Pres_norm = " + format(breakdown.getNormalizedResourcePenalty()));
        out.println();

        out.println("Weighted contribution:");
        out.println("wT*T_norm    = " + format(weights.getWT() * breakdown.getNormalizedCompletionTime()));
        out.println("wL*L_norm    = " + format(weights.getWL() * breakdown.getNormalizedCommunicationLatency()));
        out.println("wM*Pmob_norm = " + format(weights.getWM() * breakdown.getNormalizedMobilityPenalty()));
        out.println("wR*Pres_norm = " + format(weights.getWR() * breakdown.getNormalizedResourcePenalty()));
        out.println();

        out.println("Final fitness:");
        out.println("J(C) = " + format(breakdown.getFitness()));
        out.println();
    }

    private void printTaskLevelAnalysis(FitnessEvaluator.EvaluationBreakdown breakdown) {
        printSectionTitle("6. TASK-LEVEL ANALYSIS");

        for (FitnessEvaluator.GeneEvaluationBreakdown gene : breakdown.getGeneBreakdowns()) {
            out.println("Task " + gene.getTaskId() + ":");
            out.println("source vehicle: " + gene.getSourceVehicleId());
            out.println("selected candidate: " + gene.getSelectedCandidateId());
            out.println("execution node: " + gene.getExecutionNodeId());
            out.println("candidate type: " + gene.getNodeType());
            out.println("decision: " + gene.getDecisionType());
            out.println();

            out.println("Time model:");
            out.println("local CPU cycles kept on vehicle: " + format(gene.getLocalCpuCycles()));
            out.println("local execution time: " + formatSeconds(gene.getLocalExecutionTimeSeconds()));
            out.println("upload time: " + formatSeconds(gene.getUploadTimeSeconds()));
            out.println("remote execution time: " + formatSeconds(gene.getRemoteExecutionTimeSeconds()));
            out.println("download time: " + formatSeconds(gene.getDownloadTimeSeconds()));
            out.println("base latency: " + formatSeconds(gene.getBaseLatencySeconds()));
            out.println("remote part time: " + formatSeconds(gene.getRemotePartTimeSeconds()));
            out.println("communication latency L_i: " + formatSeconds(gene.getCommunicationLatencySeconds()));
            out.println("completion time T_i: " + formatSeconds(gene.getCompletionTimeSeconds()));
            out.println();

            out.println("Deadline:");
            out.println("deadline: " + formatSeconds(gene.getDeadlineSeconds()));
            out.println("status: " + (gene.isDeadlineRespected() ? "respected" : "VIOLATED"));
            out.println();

            out.println("Mobility:");
            out.println("coverage time: " + formatSeconds(gene.getCoverageTimeSeconds()));
            out.println("coverage status: " + (gene.isCoverageSufficient() ? "sufficient" : "INSUFFICIENT"));
            out.println("mobility penalty: " + format(gene.getMobilityPenalty()));
            out.println("constraint penalty: " + format(gene.getConstraintPenalty()));
            out.println();
        }
    }

    private void printResourceUsage(FitnessEvaluator.EvaluationBreakdown breakdown) {
        printSectionTitle("7. RESOURCE USAGE");

        out.println("CPU usage by physical execution node:");
        out.println();

        for (FitnessEvaluator.ExecutionNodeResourceUsageBreakdown usage
                : breakdown.getExecutionNodeResourceUsageBreakdowns()) {

            out.println("Execution node " + usage.getExecutionNodeId() + " [" + usage.getNodeType() + "]:");
            out.println("available CPU: " + format(usage.getAvailableCpu()) + " cycles/s");
            out.println("used CPU:      " + format(usage.getUsedCpu()) + " cycles/s");
            out.println("CPU usage:     " + format(usage.getCpuUsagePercent()) + "%");

            if (usage.hasCpuViolation()) {
                out.println("CPU status:    VIOLATED");
            } else if (usage.isCpuSaturated(SATURATION_NOTICE_THRESHOLD_PERCENT)) {
                out.println("CPU status:    SOFT WARNING - saturated resource");
            } else {
                out.println("CPU status:    feasible");
            }

            out.println();
        }

        out.println("Bandwidth usage by source-execution link candidate:");
        out.println();

        for (FitnessEvaluator.LinkBandwidthUsageBreakdown usage
                : breakdown.getLinkBandwidthUsageBreakdowns()) {

            out.println("Candidate " + usage.getCandidateId() + " [" + usage.getNodeType() + "]:");
            out.println("source vehicle: " + usage.getSourceVehicleId());
            out.println("execution node: " + usage.getExecutionNodeId());
            out.println("available bandwidth: " + format(usage.getAvailableBandwidth()) + " bit/s");
            out.println("used bandwidth:      " + format(usage.getUsedBandwidth()) + " bit/s");
            out.println("bandwidth usage:     " + format(usage.getBandwidthUsagePercent()) + "%");

            if (usage.hasBandwidthViolation()) {
                out.println("bandwidth status:    VIOLATED");
            } else if (usage.isBandwidthSaturated(SATURATION_NOTICE_THRESHOLD_PERCENT)) {
                out.println("bandwidth status:    SOFT WARNING - saturated resource");
            } else {
                out.println("bandwidth status:    feasible");
            }

            out.println();
        }

        out.println("Estimated local computational workload by vehicle:");
        out.println();

        for (FitnessEvaluator.LocalResourceUsageBreakdown local
                : breakdown.getLocalResourceUsageBreakdowns()) {

            out.println("Vehicle " + local.getVehicleId() + ":");
            out.println("local CPU capacity: " + format(local.getLocalCpu()) + " cycles/s");
            out.println("local CPU cycles kept on vehicle: " + format(local.getLocalCpuCycles()));
            out.println("max local execution time contribution: "
                    + formatSeconds(local.getMaxLocalExecutionTimeSeconds()));
            out.println("status: " + (local.hasLocalWorkload() ? "local workload present" : "no local workload"));
            out.println();
        }
    }

    private void printWarnings(MaGaResult result) {
        printSectionTitle("8. WARNINGS");

        boolean hasMessages = false;

        for (FitnessEvaluator.GeneEvaluationBreakdown gene
                : result.getBestEvaluation().getGeneBreakdowns()) {

            if (!gene.isDeadlineRespected()) {
                out.println("WARNING: task " + gene.getTaskId() + " violates its deadline.");
                hasMessages = true;
            }

            if (!gene.isCoverageSufficient()) {
                out.println("WARNING: task " + gene.getTaskId()
                        + " may exceed coverage time on candidate "
                        + gene.getSelectedCandidateId() + ".");
                hasMessages = true;
            }

            if (gene.getConstraintPenalty() > 0.0) {
                out.println("WARNING: task " + gene.getTaskId()
                        + " has constraint penalty "
                        + format(gene.getConstraintPenalty()) + ".");
                hasMessages = true;
            }
        }

        for (FitnessEvaluator.ExecutionNodeResourceUsageBreakdown usage
                : result.getBestEvaluation().getExecutionNodeResourceUsageBreakdowns()) {

            if (usage.hasCpuViolation()) {
                out.println("WARNING: execution node "
                        + usage.getExecutionNodeId()
                        + " exceeds available CPU.");
                hasMessages = true;
            } else if (usage.isCpuSaturated(SATURATION_NOTICE_THRESHOLD_PERCENT)) {
                out.println("SOFT WARNING: execution node "
                        + usage.getExecutionNodeId()
                        + " uses approximately 100% of available CPU.");
                hasMessages = true;
            }
        }

        for (FitnessEvaluator.LinkBandwidthUsageBreakdown usage
                : result.getBestEvaluation().getLinkBandwidthUsageBreakdowns()) {

            if (usage.hasBandwidthViolation()) {
                out.println("WARNING: candidate "
                        + usage.getCandidateId()
                        + " exceeds available bandwidth.");
                hasMessages = true;
            } else if (usage.isBandwidthSaturated(SATURATION_NOTICE_THRESHOLD_PERCENT)) {
                out.println("SOFT WARNING: candidate "
                        + usage.getCandidateId()
                        + " uses approximately 100% of available bandwidth.");
                hasMessages = true;
            }
        }

        if (!hasMessages) {
            out.println("No resource violations detected.");
            out.println("No deadline violations detected.");
            out.println("No coverage violations detected.");
            out.println("No constraint penalties detected.");
            out.println("No saturated remote resources detected.");
        }

        out.println();
    }

    private void printFinalInterpretation(MaGaResult result) {
        printSectionTitle("9. FINAL INTERPRETATION");

        out.println("The best chromosome represents the offloading strategy selected by MA-GA.");
        out.println("Each gene maps one task to one source-aware execution candidate.");
        out.println("CPU is aggregated by physical execution node.");
        out.println("Bandwidth is tracked by candidate/link because it depends on the source-execution relation.");
        out.println();

        for (FitnessEvaluator.GeneEvaluationBreakdown gene
                : result.getBestEvaluation().getGeneBreakdowns()) {

            out.println("- Task " + gene.getTaskId()
                    + " from " + gene.getSourceVehicleId()
                    + " uses candidate " + gene.getSelectedCandidateId()
                    + " and executes on " + gene.getExecutionNodeId()
                    + " with decision type: " + gene.getDecisionType() + ".");
        }

        out.println();
    }

    private void printSectionTitle(String title) {
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
}


