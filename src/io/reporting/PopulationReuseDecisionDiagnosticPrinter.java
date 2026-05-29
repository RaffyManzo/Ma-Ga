package io.reporting;

import window.dynamicity.DynamicityBreakdown;
import window.population.PopulationReuseDecision;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Printer dedicato alla decisione di riuso della popolazione.
 *
 * <p>Serve a capire perché la policy applica WARM_START, PARTIAL_RESTART
 * o COLD_START. Evita di dedurre la scelta solo guardando D(k).</p>
 */
public final class PopulationReuseDecisionDiagnosticPrinter {

    private final PrintStream out;

    public PopulationReuseDecisionDiagnosticPrinter() {
        this(System.out);
    }

    public PopulationReuseDecisionDiagnosticPrinter(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        out.println("------------------------------------------------------------");
        out.println("POPULATION REUSE DECISION SUMMARY");
        out.println("------------------------------------------------------------");
        out.println("idx | snapshot | D | Dt | Dl | Dr | Dv | base | applied | prevPerf | spike | severeSpike | corrected | reason");

        for (TemporalStepResult step : result.getSteps()) {
            DynamicityBreakdown d = step.getDynamicityBreakdown();
            PopulationReuseDecision decision = step.getPopulationReuseDecision();

            out.println(
                    step.getWindowIndex()
                            + " | " + step.getSnapshot().getSnapshotId()
                            + " | " + format(d.getGlobalDynamicity())
                            + " | " + format(d.getTaskVariation())
                            + " | " + format(d.getLinkVariation())
                            + " | " + format(d.getResourceVariation())
                            + " | " + format(d.getVehicleVariation())
                            + " | " + decision.getBaseReuseMode()
                            + " | " + decision.getAppliedMode()
                            + " | " + decision.getPreviousPerformanceSignal()
                            + " | " + decision.isComponentSpikeDetected()
                            + " | " + decision.isSevereComponentSpikeDetected()
                            + " | " + decision.isCorrected()
                            + " | " + decision.getReason()
            );
        }

        out.println();
    }

    private String format(double value) {
        return String.format("%.6f", value);
    }
}
