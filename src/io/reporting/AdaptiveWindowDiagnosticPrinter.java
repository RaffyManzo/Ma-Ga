package io.reporting;

import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;
import window.timing.AdaptiveWindowDecision;
import window.timing.TemporalWindowBounds;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Printer compatto per controllare la finestra adattiva.
 */
public final class AdaptiveWindowDiagnosticPrinter {

    private final PrintStream out;

    public AdaptiveWindowDiagnosticPrinter() {
        this(System.out);
    }

    public AdaptiveWindowDiagnosticPrinter(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        out.println("------------------------------------------------------------");
        out.println("ADAPTIVE WINDOW SUMMARY");
        out.println("------------------------------------------------------------");
        out.println("idx | snapshot | currentDt | nextDt | minDt | maxDt | TcovRef | action | reason");

        for (TemporalStepResult step : result.getSteps()) {
            AdaptiveWindowDecision decision = step.getAdaptiveWindowDecision();
            TemporalWindowBounds bounds = decision.getBounds();

            out.println(
                    step.getWindowIndex()
                            + " | " + step.getSnapshot().getSnapshotId()
                            + " | " + formatSeconds(decision.getCurrentWindowSeconds())
                            + " | " + formatSeconds(decision.getNextWindowSeconds())
                            + " | " + formatSeconds(bounds.getMinimumWindowSeconds())
                            + " | " + formatSeconds(bounds.getMaximumWindowSeconds())
                            + " | " + formatSeconds(bounds.getCoverageReferenceSeconds())
                            + " | " + decision.getAction()
                            + " | " + decision.getReason()
            );
        }

        out.println();
    }

    private String formatSeconds(double value) {
        return String.format("%.6f s", value);
    }
}
