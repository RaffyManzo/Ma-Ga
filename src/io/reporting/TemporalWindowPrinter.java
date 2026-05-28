package io.reporting;

import ga.core.MaGaResult;
import ga.fitness.FitnessEvaluator;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import model.snapshot.SystemSnapshot;
import window.dynamicity.DynamicityBreakdown;
import window.event.CriticalEvent;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;
import window.trigger.ReoptimizationTrigger;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Printer sintetico per il ciclo temporale del MA-GA.
 *
 * Usa i risultati già calcolati dal package window e dal MA-GA.
 * Non ricalcola fitness, dinamicità o popolazioni.
 */
public final class TemporalWindowPrinter {

    private final PrintStream out;

    /**
     * Crea un printer su System.out.
     */
    public TemporalWindowPrinter() {
        this(System.out);
    }

    /**
     * Crea un printer usando uno stream specifico.
     *
     * @param out stream di stampa
     */
    public TemporalWindowPrinter(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    /**
     * Stampa il report completo della sequenza temporale.
     *
     * @param result risultato prodotto dal TemporalWindowManager
     */
    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        printHeader(result);

        if (result.isEmpty()) {
            out.println("No temporal window steps executed.");
            return;
        }

        for (TemporalStepResult step : result.getSteps()) {
            printStep(step);
        }

        printSummary(result);
    }

    /**
     * Stampa intestazione e statistiche globali.
     */
    private void printHeader(TemporalWindowResult result) {
        out.println("============================================================");
        out.println("TEMPORAL WINDOW MA-GA REPORT");
        out.println("============================================================");
        out.println("Executed steps: " + result.getStepCount());
        out.println("Critical-event steps: " + result.countCriticalEventSteps());
        out.println("Population-reuse steps: " + result.countPopulationReuseSteps());
        out.println("Best final fitness: " + formatOptional(result.getBestFinalFitness().orElse(null)));
        out.println();
    }

    /**
     * Stampa un singolo step temporale.
     */
    private void printStep(TemporalStepResult step) {
        ReoptimizationTrigger trigger = step.getTrigger();
        SystemSnapshot snapshot = step.getSnapshot();
        DynamicityBreakdown dynamicity = step.getDynamicityBreakdown();
        MaGaResult maGaResult = step.getMaGaResult();

        out.println("------------------------------------------------------------");
        out.println("WINDOW STEP " + step.getWindowIndex());
        out.println("------------------------------------------------------------");

        printTrigger(trigger);
        printSnapshot(snapshot);
        printDynamicity(dynamicity);
        printPopulation(step);
        printMaGaSummary(maGaResult);
        printBestDecisions(maGaResult);

        out.println();
    }

    /**
     * Stampa il motivo della riesecuzione.
     */
    private void printTrigger(ReoptimizationTrigger trigger) {
        out.println("Trigger:");
        out.println("- reason: " + trigger.getReason());
        out.println("- trigger time: " + formatSeconds(trigger.getTriggerTimeSeconds()));

        if (trigger.isCriticalEventTrigger()) {
            printCriticalEvent(trigger.getCriticalEvent());
        }

        out.println();
    }

    /**
     * Stampa i dettagli minimi dell'evento critico.
     */
    private void printCriticalEvent(CriticalEvent event) {
        out.println("- critical event id: " + event.getEventId());
        out.println("- critical event type: " + event.getType());
        out.println("- critical event severity: " + event.getSeverity());

        if (event.getAffectedVehicleId() != null) {
            out.println("- affected vehicle: " + event.getAffectedVehicleId());
        }

        if (event.getAffectedTaskId() != null) {
            out.println("- affected task: " + event.getAffectedTaskId());
        }

        if (event.getAffectedCandidateId() != null) {
            out.println("- affected candidate: " + event.getAffectedCandidateId());
        }

        if (event.getAffectedExecutionNodeId() != null) {
            out.println("- affected execution node: " + event.getAffectedExecutionNodeId());
        }

        if (event.getDescription() != null) {
            out.println("- description: " + event.getDescription());
        }
    }

    /**
     * Stampa le dimensioni dello snapshot usato nello step.
     */
    private void printSnapshot(SystemSnapshot snapshot) {
        out.println("Snapshot:");
        out.println("- id: " + snapshot.getSnapshotId());
        out.println("- time: " + formatSeconds(snapshot.getTimeSeconds()));
        out.println("- vehicles: " + snapshot.getVehicles().size());
        out.println("- tasks: " + snapshot.getTasks().size());
        out.println("- candidates: " + snapshot.getCandidateNodes().size());
        out.println();
    }

    /**
     * Stampa la dinamicità usata per scegliere il riuso della popolazione.
     */
    private void printDynamicity(DynamicityBreakdown dynamicity) {
        out.println("Dynamicity:");
        out.println("- previous snapshot: " + dynamicity.getPreviousSnapshotId());
        out.println("- current snapshot: " + dynamicity.getCurrentSnapshotId());
        out.println("- vehicle variation: " + format(dynamicity.getVehicleVariation()));
        out.println("- task variation: " + format(dynamicity.getTaskVariation()));
        out.println("- resource variation: " + format(dynamicity.getResourceVariation()));
        out.println("- link variation: " + format(dynamicity.getLinkVariation()));
        out.println("- global dynamicity: " + format(dynamicity.getGlobalDynamicity()));
        out.println("- level: " + dynamicity.getDynamicityLevel());
        out.println("- suggested reuse mode: " + dynamicity.getSuggestedReuseMode());
        out.println();
    }

    /**
     * Stampa come è stata costruita la popolazione dello step.
     */
    private void printPopulation(TemporalStepResult step) {
        out.println("Population:");
        out.println("- reuse mode: " + step.getReuseMode());
        out.println("- reused previous population: " + step.reusedPreviousPopulation());
        out.println("- initial population size: " + step.getInitialPopulationSize());
        out.println("- final population size: " + step.getFinalPopulationSize());
        out.println("- data collection delay: "
                + formatSeconds(step.getDataCollectionDelaySeconds()));
        out.println("- observation time: "
                + formatSeconds(step.getObservationTimeSeconds()));
        out.println();
    }

    /**
     * Stampa solo la sintesi del MA-GA.
     */
    private void printMaGaSummary(MaGaResult result) {
        out.println("MA-GA summary:");
        out.println("- stop reason: " + result.getStopReason());
        out.println("- generations executed: " + result.getGenerationsExecuted());
        out.println("- initial best fitness: " + format(result.getInitialBestFitness()));
        out.println("- final best fitness: " + format(result.getFinalBestFitness()));
        out.println("- best completion time: " + formatSeconds(result.getBestEvaluation().getCompletionTimeSeconds()));
        out.println("- best communication latency: " + formatSeconds(result.getBestEvaluation().getCommunicationLatencySeconds()));
        out.println("- mobility penalty: " + format(result.getBestEvaluation().getMobilityPenalty()));
        out.println("- resource penalty: " + format(result.getBestEvaluation().getResourcePenalty()));
        out.println();
    }

    /**
     * Stampa la decisione finale per ogni task.
     */
    private void printBestDecisions(MaGaResult result) {
        out.println("Best decisions:");

        for (GeneEvaluationBreakdown gene
                : result.getBestEvaluation().getGeneBreakdowns()) {
            out.println("- " + gene.getTaskId()
                    + " | source=" + gene.getSourceVehicleId()
                    + " | candidate=" + gene.getSelectedCandidateId()
                    + " | execNode=" + gene.getExecutionNodeId()
                    + " | type=" + gene.getNodeType()
                    + " | decision=" + gene.getDecisionType()
                    + " | p=" + format(gene.getOffloadingRatio()));
        }

        out.println();
    }

    /**
     * Stampa la sintesi finale della simulazione temporale.
     */
    private void printSummary(TemporalWindowResult result) {
        out.println("================================================------------");
        out.println("TEMPORAL SUMMARY");
        out.println("================================================------------");
        out.println("Total steps: " + result.getStepCount());
        out.println("Critical-event steps: " + result.countCriticalEventSteps());
        out.println("Population reuse steps: " + result.countPopulationReuseSteps());
        out.println("Best final fitness: " + formatOptional(result.getBestFinalFitness().orElse(null)));
        out.println();
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

    private String formatOptional(Double value) {
        return value == null ? "n/a" : format(value);
    }
}