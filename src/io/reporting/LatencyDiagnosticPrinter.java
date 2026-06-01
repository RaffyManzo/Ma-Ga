package io.reporting;

import config.MaGaConfig;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import model.node.NodeType;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Report diagnostico dedicato alla latenza comunicativa.
 *
 * <p>La classe non ricalcola la fitness. Legge i breakdown prodotti per il
 * miglior cromosoma di ogni finestra e rende esplicita la semantica adottata
 * nella fase 6A:</p>
 *
 * <pre>
 * L_i = p * input / b + output / b + L_base   per p > 0
 * L(C) = sum_i L_i
 * </pre>
 *
 * <p>{@code L_base} resta una estensione operativa provvisoria associata al
 * nodo remoto. La sua relazione con {@code tau_n} deve essere chiarita nella
 * formalizzazione prima di un ulteriore refactor.</p>
 */
public final class LatencyDiagnosticPrinter {

    private static final double EPSILON = 1.0E-9;
    private static final int DEFAULT_TOP_LIMIT = 10;

    private final MaGaConfig config;
    private final PrintStream out;
    private final int topLimit;

    public LatencyDiagnosticPrinter(MaGaConfig config, PrintStream out) {
        this(config, out, DEFAULT_TOP_LIMIT);
    }

    public LatencyDiagnosticPrinter(
            MaGaConfig config,
            PrintStream out,
            int topLimit
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.out = Objects.requireNonNull(out, "out must not be null.");
        if (topLimit <= 0) {
            throw new IllegalArgumentException("topLimit must be > 0.");
        }
        this.topLimit = topLimit;
    }

    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        printConfiguration();
        printSummaryByWindow(result);
        printTopCommunicationDecisions(result);
        printAssumptions();
    }

    private void printConfiguration() {
        printSectionTitle("COMMUNICATION LATENCY CONFIGURATION SUMMARY");
        printf("Lref: %.6f s%n", config.getNormalizationConfig().getLRef());
        out.println("Aggregation rule: L(C) = sum_i L_i(C)");
        out.println("Partial upload rule: p * input / bandwidth");
        out.println("Partial download rule: output / bandwidth for every remote choice p > 0");
        out.println("Base latency rule: included once as provisional operational extension");
        out.println();
    }

    private void printSummaryByWindow(TemporalWindowResult result) {
        printSectionTitle("COMMUNICATION LATENCY SUMMARY BY WINDOW");
        out.println("idx | snapshot | tasks | remote | partial | full | L(C) | Lref | normalizedL | avgPerTask | avgPerRemote | uploadSum | downloadSum | baseLatencySum | componentDelta");

        for (TemporalStepResult step : result.getSteps()) {
            EvaluationBreakdown evaluation = step.getMaGaResult().getBestEvaluation();
            List<GeneEvaluationBreakdown> genes = evaluation.getGeneBreakdowns();
            List<GeneEvaluationBreakdown> remoteGenes = remoteGenes(genes);

            int partial = 0;
            int full = 0;
            double uploadSum = 0.0;
            double downloadSum = 0.0;
            double baseLatencySum = 0.0;

            for (GeneEvaluationBreakdown gene : remoteGenes) {
                if (gene.getOffloadingRatio() >= 1.0 - EPSILON) {
                    full++;
                } else {
                    partial++;
                }
                uploadSum += gene.getUploadTimeSeconds();
                downloadSum += gene.getDownloadTimeSeconds();
                baseLatencySum += gene.getBaseLatencySeconds();
            }

            double totalLatency = evaluation.getCommunicationLatencySeconds();
            double components = uploadSum + downloadSum + baseLatencySum;
            double delta = totalLatency - components;
            double avgPerTask = genes.isEmpty() ? 0.0 : totalLatency / genes.size();
            double avgPerRemote = remoteGenes.isEmpty() ? 0.0 : totalLatency / remoteGenes.size();

            printf(
                    "%d | %s | %d | %d | %d | %d | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %.9f%n",
                    step.getWindowIndex(),
                    step.getSnapshot().getSnapshotId(),
                    genes.size(),
                    remoteGenes.size(),
                    partial,
                    full,
                    totalLatency,
                    config.getNormalizationConfig().getLRef(),
                    evaluation.getNormalizedCommunicationLatency(),
                    avgPerTask,
                    avgPerRemote,
                    uploadSum,
                    downloadSum,
                    baseLatencySum,
                    delta
            );
        }
        out.println();
    }

    private void printTopCommunicationDecisions(TemporalWindowResult result) {
        printSectionTitle("TOP COMMUNICATION-LATENCY DECISIONS BY WINDOW");

        for (TemporalStepResult step : result.getSteps()) {
            List<GeneEvaluationBreakdown> ranked = remoteGenes(
                    step.getMaGaResult().getBestEvaluation().getGeneBreakdowns()
            );
            ranked.sort(
                    Comparator.comparingDouble(
                            GeneEvaluationBreakdown::getCommunicationLatencySeconds
                    ).reversed()
            );

            printf(
                    "Window %d | snapshot=%s | remote=%d%n",
                    step.getWindowIndex(),
                    step.getSnapshot().getSnapshotId(),
                    ranked.size()
            );

            if (ranked.isEmpty()) {
                out.println("- no remote communication decisions");
                out.println();
                continue;
            }

            out.println("task | source | candidate | type | p | upload | download | baseLatency | L_i | remoteExec | remotePart | completion");

            for (int index = 0; index < Math.min(topLimit, ranked.size()); index++) {
                GeneEvaluationBreakdown gene = ranked.get(index);
                printf(
                        "%s | %s | %s | %s | %.6f | %.6f s | %.6f s | %.6f s | %.6f s | %.6f s | %.6f s | %.6f s%n",
                        gene.getTaskId(),
                        gene.getSourceVehicleId(),
                        gene.getSelectedCandidateId(),
                        gene.getNodeType(),
                        gene.getOffloadingRatio(),
                        gene.getUploadTimeSeconds(),
                        gene.getDownloadTimeSeconds(),
                        gene.getBaseLatencySeconds(),
                        gene.getCommunicationLatencySeconds(),
                        gene.getRemoteExecutionTimeSeconds(),
                        gene.getRemotePartTimeSeconds(),
                        gene.getCompletionTimeSeconds()
                );
            }
            out.println();
        }
    }

    private void printAssumptions() {
        printSectionTitle("COMMUNICATION LATENCY MODEL ASSUMPTIONS");
        out.println("- LOCAL decisions have L_i = 0.");
        out.println("- For every remote choice p > 0, upload scales with p.");
        out.println("- For every remote choice p > 0, download uses the integral remote output.");
        out.println("- L(C) is the sum of the task communication latencies, not their average.");
        out.println("- baseLatencySeconds is included once as a provisional operational extension.");
        out.println("- The relationship between baseLatencySeconds and the formal parameter tau_n remains an Open Issue.");
        out.println();
    }

    private List<GeneEvaluationBreakdown> remoteGenes(
            List<GeneEvaluationBreakdown> genes
    ) {
        List<GeneEvaluationBreakdown> result = new ArrayList<>();
        for (GeneEvaluationBreakdown gene : genes) {
            if (gene.getNodeType() != NodeType.LOCAL) {
                result.add(gene);
            }
        }
        return result;
    }

    private void printSectionTitle(String title) {
        out.println("------------------------------------------------------------");
        out.println(title);
        out.println("------------------------------------------------------------");
    }

    private void printf(String format, Object... values) {
        out.printf(Locale.ITALY, format, values);
    }
}
