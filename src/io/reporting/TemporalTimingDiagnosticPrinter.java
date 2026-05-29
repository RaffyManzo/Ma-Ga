package io.reporting;

import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Printer compatto dedicato alla separazione dei tempi.
 *
 * <p>Serve a verificare che il TemporalWindowManager stia usando il proprio
 * tempo logico/adattivo, senza dipendere dal tempo salvato negli snapshot JSON.</p>
 */
public final class TemporalTimingDiagnosticPrinter {

    private final PrintStream out;

    public TemporalTimingDiagnosticPrinter() {
        this(System.out);
    }

    public TemporalTimingDiagnosticPrinter(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        out.println("------------------------------------------------------------");
        out.println("TEMPORAL TIMING SUMMARY");
        out.println("------------------------------------------------------------");
        out.println("idx | snapshot | triggerTime | managerObsTime | sourceTime | currentDt | nextDt");

        for (TemporalStepResult step : result.getSteps()) {
            out.println(
                    step.getWindowIndex()
                            + " | " + step.getSnapshot().getSnapshotId()
                            + " | " + formatSeconds(step.getTriggerTimeSeconds())
                            + " | " + formatSeconds(step.getLogicalObservationTimeSeconds())
                            + " | " + formatSeconds(step.getSourceObservationTimeSeconds())
                            + " | " + formatSeconds(
                            step.getAdaptiveWindowDecision().getCurrentWindowSeconds()
                    )
                            + " | " + formatSeconds(
                            step.getAdaptiveWindowDecision().getNextWindowSeconds()
                    )
            );
        }

        out.println();
    }

    private String formatSeconds(double value) {
        return String.format("%.6f s", value);
    }
}
