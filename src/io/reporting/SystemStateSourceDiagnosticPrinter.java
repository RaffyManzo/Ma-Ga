package io.reporting;

import window.source.SystemStateObservation;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Printer per controllare come la sorgente dati ha risposto alle richieste del
 * gestore temporale.
 */
public final class SystemStateSourceDiagnosticPrinter {

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
                "idx | snapshot | mode | seq | managerTime | sourceTime | shift | exactMatch | source"
        );

        for (TemporalStepResult step : result.getSteps()) {
            if (step.getSystemStateObservation().isEmpty()) {
                out.println(
                        step.getWindowIndex()
                                + " | " + step.getSnapshot().getSnapshotId()
                                + " | LEGACY | - | "
                                + formatSeconds(step.getLogicalObservationTimeSeconds())
                                + " | " + formatSeconds(step.getSourceObservationTimeSeconds())
                                + " | " + formatSeconds(
                                step.getSourceObservationTimeSeconds()
                                        - step.getLogicalObservationTimeSeconds()
                        )
                                + " | - | legacy"
                );
                continue;
            }

            SystemStateObservation observation = step
                    .getSystemStateObservation()
                    .get();

            out.println(
                    step.getWindowIndex()
                            + " | " + step.getSnapshot().getSnapshotId()
                            + " | " + observation.getSourceMode()
                            + " | " + observation.getSequenceIndex()
                            + " | " + formatSeconds(
                            observation.getRequestedObservationTimeSeconds()
                    )
                            + " | " + formatSeconds(
                            observation.getSourceObservationTimeSeconds()
                    )
                            + " | " + formatSeconds(observation.getTimeShiftSeconds())
                            + " | " + observation.isExactTimeMatch()
                            + " | " + observation.getSourceDescription()
            );
        }

        out.println();
    }

    private String formatSeconds(double value) {
        return String.format("%.6f s", value);
    }
}
