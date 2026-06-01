package io.reporting;

import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Report dedicato alle decisioni finali che restano fuori deadline dopo il repair.
 *
 * <p>Una violazione residua viene indicata come {@code DEGRADED_BEST_EFFORT}:
 * il repair ha confrontato un insieme limitato di alternative coerenti e ha
 * mantenuto quella con lo sforamento stimato più basso. La marcatura non
 * certifica l'insoddisfacibilità matematica globale del task.</p>
 */
public final class DeadlineBestEffortDiagnosticPrinter {

    private static final int DEFAULT_TOP_LIMIT = 10;

    private final PrintStream out;
    private final int topLimit;

    public DeadlineBestEffortDiagnosticPrinter() {
        this(System.out, DEFAULT_TOP_LIMIT);
    }

    public DeadlineBestEffortDiagnosticPrinter(PrintStream out) {
        this(out, DEFAULT_TOP_LIMIT);
    }

    public DeadlineBestEffortDiagnosticPrinter(PrintStream out, int topLimit) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
        if (topLimit <= 0) {
            throw new IllegalArgumentException("topLimit must be > 0.");
        }
        this.topLimit = topLimit;
    }

    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        printHeader();
        printInterpretation();
        printWindowSummary(result.getSteps());
        printTopResidualViolations(result.getSteps());
    }

    private void printHeader() {
        out.println("============================================================");
        out.println("DEADLINE / DEGRADED BEST-EFFORT REPORT");
        out.println("============================================================");
        out.println();
    }

    private void printInterpretation() {
        out.println("Interpretation:");
        out.println("- ordinary mode: the repaired decision respects the task deadline;");
        out.println("- DEGRADED_BEST_EFFORT: the final decision still exceeds the deadline;");
        out.println("- the degraded label records a bounded-repair outcome, not a proof of global infeasibility;");
        out.println("- CLOUD is never preferred automatically: the retained decision is the one with the lowest predicted lateness among the alternatives evaluated by the repair policy.");
        out.println();
    }

    private void printWindowSummary(List<TemporalStepResult> steps) {
        printSection("1. DEGRADED SUMMARY BY WINDOW");
        out.println("idx | snapshot | tasks | deadlineOK | degradedBestEffort | maxLateness | avgLateness");

        for (TemporalStepResult step : steps) {
            List<GeneEvaluationBreakdown> degraded = degradedGenes(
                    step.getMaGaResult().getBestEvaluation()
            );

            int total = step.getMaGaResult()
                    .getBestEvaluation()
                    .getGeneBreakdowns()
                    .size();
            int satisfied = total - degraded.size();

            out.println(
                    step.getWindowIndex() + " | "
                            + step.getSnapshot().getSnapshotId() + " | "
                            + total + " | "
                            + satisfied + " | "
                            + degraded.size() + " | "
                            + formatSeconds(maxLateness(degraded)) + " | "
                            + formatSeconds(averageLateness(degraded))
            );
        }
        out.println();
    }

    private void printTopResidualViolations(List<TemporalStepResult> steps) {
        printSection("2. TOP DEGRADED BEST-EFFORT DECISIONS BY WINDOW");

        for (TemporalStepResult step : steps) {
            List<GeneEvaluationBreakdown> degraded = degradedGenes(
                    step.getMaGaResult().getBestEvaluation()
            );

            out.println(
                    "Window " + step.getWindowIndex()
                            + " | snapshot=" + step.getSnapshot().getSnapshotId()
                            + " | degradedBestEffort=" + degraded.size()
            );

            if (degraded.isEmpty()) {
                out.println("- no degraded best-effort decision");
                out.println();
                continue;
            }

            out.println("task | source | candidate | nodeType | decision | p | completion | deadline | lateness | status");
            degraded.stream()
                    .sorted(Comparator.comparingDouble(this::latenessSeconds).reversed())
                    .limit(topLimit)
                    .forEach(gene -> out.println(
                            gene.getTaskId() + " | "
                                    + gene.getSourceVehicleId() + " | "
                                    + gene.getSelectedCandidateId() + " | "
                                    + gene.getNodeType() + " | "
                                    + gene.getDecisionType() + " | "
                                    + format(gene.getOffloadingRatio()) + " | "
                                    + formatSeconds(gene.getCompletionTimeSeconds()) + " | "
                                    + formatSeconds(gene.getDeadlineSeconds()) + " | "
                                    + formatSeconds(latenessSeconds(gene)) + " | "
                                    + "DEGRADED_BEST_EFFORT"
                    ));
            out.println();
        }
    }

    private List<GeneEvaluationBreakdown> degradedGenes(EvaluationBreakdown evaluation) {
        return evaluation.getGeneBreakdowns()
                .stream()
                .filter(gene -> !gene.isDeadlineRespected())
                .toList();
    }

    private double maxLateness(List<GeneEvaluationBreakdown> degraded) {
        return degraded.stream()
                .mapToDouble(this::latenessSeconds)
                .max()
                .orElse(0.0);
    }

    private double averageLateness(List<GeneEvaluationBreakdown> degraded) {
        return degraded.stream()
                .mapToDouble(this::latenessSeconds)
                .average()
                .orElse(0.0);
    }

    private double latenessSeconds(GeneEvaluationBreakdown gene) {
        return Math.max(
                0.0,
                gene.getCompletionTimeSeconds() - gene.getDeadlineSeconds()
        );
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
}
