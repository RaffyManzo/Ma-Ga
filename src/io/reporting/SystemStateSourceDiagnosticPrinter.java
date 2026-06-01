package io.reporting;

import window.source.SystemStateObservation;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Printer per controllare l'allineamento tra tempo logico del manager e timestamp
 * degli snapshot restituiti dalla sorgente dati.
 */
public final class SystemStateSourceDiagnosticPrinter {
    private static final double EPSILON_SECONDS = 1.0E-6;

    private final PrintStream out;

    public SystemStateSourceDiagnosticPrinter() {
        this(System.out);
    }

    public SystemStateSourceDiagnosticPrinter(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        out.println("------------------------------------------------------------");
        out.println("SYSTEM STATE SOURCE SUMMARY");
        out.println("------------------------------------------------------------");
        out.println(
                "idx | snapshot | mode | seq | managerTime | sourceTime | shift | "
                        + "exactMatch | futureLookAhead | interpretation | source"
        );

        for (TemporalStepResult step : result.getSteps()) {
            if (step.getSystemStateObservation().isEmpty()) {
                double shift = step.getSourceObservationTimeSeconds()
                        - step.getLogicalObservationTimeSeconds();
                out.println(
                        step.getWindowIndex() + " | "
                                + step.getSnapshot().getSnapshotId() + " | LEGACY | - | "
                                + formatSeconds(step.getLogicalObservationTimeSeconds()) + " | "
                                + formatSeconds(step.getSourceObservationTimeSeconds()) + " | "
                                + formatSeconds(shift) + " | - | "
                                + isFutureLookAhead(shift) + " | legacy observation | legacy"
                );
                continue;
            }

            SystemStateObservation observation = step.getSystemStateObservation().get();
            double shift = observation.getTimeShiftSeconds();
            String mode = String.valueOf(observation.getSourceMode());

            out.println(
                    step.getWindowIndex() + " | "
                            + step.getSnapshot().getSnapshotId() + " | "
                            + mode + " | "
                            + observation.getSequenceIndex() + " | "
                            + formatSeconds(observation.getRequestedObservationTimeSeconds()) + " | "
                            + formatSeconds(observation.getSourceObservationTimeSeconds()) + " | "
                            + formatSeconds(shift) + " | "
                            + observation.isExactTimeMatch() + " | "
                            + isFutureLookAhead(shift) + " | "
                            + interpret(mode, shift) + " | "
                            + observation.getSourceDescription()
            );
        }

        out.println();
        out.println("Interpretation notes:");
        out.println("- In JSON_SEQUENCE mode, a positive shift is expected when the adaptive window duration changes because files are consumed ordinally.");
        out.println("- In JSON_TIME mode, a futureLookAhead=true row is anomalous: it means that a snapshot newer than the manager time was exposed.");
        out.println();
    }

    private String interpret(String mode, double shift) {
        if (!isFutureLookAhead(shift)) {
            return Math.abs(shift) <= EPSILON_SECONDS
                    ? "aligned"
                    : "past snapshot reuse";
        }
        if (mode.contains("SEQUENTIAL")) {
            return "expected ordinal shift";
        }
        return "unexpected future snapshot";
    }

    private boolean isFutureLookAhead(double shift) {
        return shift > EPSILON_SECONDS;
    }

    private String formatSeconds(double value) {
        return String.format("%.6f s", value);
    }
}
