package io.reporting;

import config.MaGaConfig;
import ga.core.MaGaResult;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import model.snapshot.SystemSnapshot;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Report aggregato per il confronto di più esecuzioni statiche e indipendenti
 * del MA-GA.
 */
public final class GaBatchReportPrinter {

    private final MaGaConfig config;
    private final PrintStream out;

    public GaBatchReportPrinter(MaGaConfig config) {
        this(config, System.out);
    }

    public GaBatchReportPrinter(MaGaConfig config, PrintStream out) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    public void print(
            String snapshotFolder,
            List<SnapshotRun> runs,
            boolean includeDetailedReports
    ) {
        Objects.requireNonNull(snapshotFolder, "snapshotFolder must not be null.");
        Objects.requireNonNull(runs, "runs must not be null.");

        printHeader(snapshotFolder, runs.size());
        printComparisonTable(runs);
        printWorstSnapshots(runs);

        if (includeDetailedReports) {
            printDetailedReports(runs);
        }
    }

    private void printHeader(String snapshotFolder, int runCount) {
        out.println("============================================================");
        out.println("MA-GA STATIC BATCH REPORT");
        out.println("============================================================");
        out.println("Snapshot folder: " + snapshotFolder);
        out.println("Independent GA runs: " + runCount);
        out.println("Population reuse: disabled");
        out.println("Deadline residuals: labelled as DEGRADED_BEST_EFFORT");
        out.println();
    }

    private void printComparisonTable(List<SnapshotRun> runs) {
        printSection("1. SNAPSHOT COMPARISON");
        out.println(
                "idx | snapshot | time | vehicles | tasks | candidates | gen | stop | "
                        + "initBest | finalBest | gain% | deadlineViol | degradedBestEffort | "
                        + "maxLateness | cpuViol | bwViol | runtime"
        );

        for (SnapshotRun run : runs) {
            EvaluationBreakdown evaluation = run.result().getBestEvaluation();
            out.println(
                    run.index() + " | "
                            + run.snapshot().getSnapshotId() + " | "
                            + formatSeconds(run.snapshot().getTimeSeconds()) + " | "
                            + run.snapshot().getVehicles().size() + " | "
                            + run.snapshot().getTasks().size() + " | "
                            + run.snapshot().getCandidateNodes().size() + " | "
                            + run.result().getGenerationsExecuted() + " | "
                            + run.result().getStopReason() + " | "
                            + format(run.result().getInitialBestFitness()) + " | "
                            + format(run.result().getFinalBestFitness()) + " | "
                            + formatPercent(improvementRatio(run.result())) + " | "
                            + countDeadlineViolations(evaluation) + " | "
                            + countDegradedBestEffort(evaluation) + " | "
                            + formatSeconds(computeMaxLateness(evaluation)) + " | "
                            + countCpuViolations(evaluation) + " | "
                            + countBandwidthViolations(evaluation) + " | "
                            + run.runtimeMillis() + " ms"
            );
        }
        out.println();
    }

    private void printWorstSnapshots(List<SnapshotRun> runs) {
        printSection("2. WORST SNAPSHOTS");

        if (runs.isEmpty()) {
            out.println("No snapshot run available.");
            out.println();
            return;
        }

        out.println("Worst by final fitness:");
        runs.stream()
                .sorted(Comparator.comparingDouble(
                        (SnapshotRun run) -> run.result().getFinalBestFitness()
                ).reversed())
                .limit(5)
                .forEach(run -> out.println(
                        "- idx=" + run.index()
                                + " snapshot=" + run.snapshot().getSnapshotId()
                                + " J=" + format(run.result().getFinalBestFitness())
                                + " degradedBestEffort=" + countDegradedBestEffort(
                                run.result().getBestEvaluation()
                        )
                                + " maxLateness=" + formatSeconds(computeMaxLateness(
                                run.result().getBestEvaluation()
                        ))
                ));
        out.println();

        out.println("Worst by degraded best-effort decisions:");
        runs.stream()
                .sorted(
                        Comparator.comparingLong(
                                (SnapshotRun run) -> countDegradedBestEffort(
                                        run.result().getBestEvaluation()
                                )
                        )
                                .thenComparingDouble(run -> computeMaxLateness(
                                        run.result().getBestEvaluation()
                                ))
                                .reversed()
                )
                .limit(5)
                .forEach(run -> out.println(
                        "- idx=" + run.index()
                                + " snapshot=" + run.snapshot().getSnapshotId()
                                + " degradedBestEffort=" + countDegradedBestEffort(
                                run.result().getBestEvaluation()
                        )
                                + " maxLateness=" + formatSeconds(computeMaxLateness(
                                run.result().getBestEvaluation()
                        ))
                                + " J=" + format(run.result().getFinalBestFitness())
                ));
        out.println();
    }

    private void printDetailedReports(List<SnapshotRun> runs) {
        printSection("3. DETAILED SNAPSHOT REPORTS");
        StressResultPrinter detailPrinter = new StressResultPrinter(config, out);

        for (SnapshotRun run : runs) {
            out.println("############################################################");
            out.println("DETAIL idx=" + run.index() + " snapshot=" + run.snapshot().getSnapshotId());
            out.println("############################################################");
            detailPrinter.printStressReport(run.snapshot(), run.result());
        }
    }

    private long countDeadlineViolations(EvaluationBreakdown evaluation) {
        return evaluation.getGeneBreakdowns()
                .stream()
                .filter(gene -> !gene.isDeadlineRespected())
                .count();
    }

    private long countDegradedBestEffort(EvaluationBreakdown evaluation) {
        return countDeadlineViolations(evaluation);
    }

    private double computeMaxLateness(EvaluationBreakdown evaluation) {
        return evaluation.getGeneBreakdowns()
                .stream()
                .mapToDouble(this::latenessSeconds)
                .max()
                .orElse(0.0);
    }

    private double latenessSeconds(GeneEvaluationBreakdown gene) {
        if (gene.isDeadlineRespected()) {
            return 0.0;
        }
        return Math.max(
                0.0,
                gene.getCompletionTimeSeconds() - gene.getDeadlineSeconds()
        );
    }

    private long countCpuViolations(EvaluationBreakdown evaluation) {
        return evaluation.getExecutionNodeResourceUsageBreakdowns()
                .stream()
                .filter(ExecutionNodeResourceUsageBreakdown::hasCpuViolation)
                .count();
    }

    private long countBandwidthViolations(EvaluationBreakdown evaluation) {
        return evaluation.getLinkBandwidthUsageBreakdowns()
                .stream()
                .filter(LinkBandwidthUsageBreakdown::hasBandwidthViolation)
                .count();
    }

    private double improvementRatio(MaGaResult result) {
        if (result.getInitialBestFitness() == 0.0) {
            return 0.0;
        }
        return (result.getInitialBestFitness() - result.getFinalBestFitness())
                / result.getInitialBestFitness();
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

    /**
     * Risultato di una singola ottimizzazione statica inclusa nel batch.
     */
    public record SnapshotRun(
            int index,
            SystemSnapshot snapshot,
            MaGaResult result,
            long runtimeMillis
    ) {
        public SnapshotRun {
            Objects.requireNonNull(snapshot, "snapshot must not be null.");
            Objects.requireNonNull(result, "result must not be null.");
            if (index < 0) {
                throw new IllegalArgumentException("index must be >= 0.");
            }
            if (runtimeMillis < 0) {
                throw new IllegalArgumentException("runtimeMillis must be >= 0.");
            }
        }
    }
}
