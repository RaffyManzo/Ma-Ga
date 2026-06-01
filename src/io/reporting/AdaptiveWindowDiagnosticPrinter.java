package io.reporting;

import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;
import window.timing.AdaptiveWindowDecision;
import window.timing.TemporalWindowBounds;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Printer compatto per controllare la finestra adattiva.
 *
 * <p>Oltre ai bounds, mostra se il runtime osservato del GA è compatibile con
 * la durata della finestra corrente e con quella successiva. L'avviso non
 * modifica il comportamento del manager: rende visibile una condizione che
 * dovrà essere discussa prima di introdurre budget adattivi o asincronia.</p>
 */
public final class AdaptiveWindowDiagnosticPrinter {
    private static final double EPSILON_SECONDS = 1.0E-6;

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
        out.println(
                "idx | snapshot | currentDt | nextDt | minDt | maxDt | "
                        + "TcovRef | minMode | maxMode | gaUsed | gaObserved | "
                        + "adaptiveMax | configuredMax | raisedMax | runtimeBudget | action | reason"
        );

        int warningCount = 0;
        for (TemporalStepResult step : result.getSteps()) {
            AdaptiveWindowDecision decision = step.getAdaptiveWindowDecision();
            TemporalWindowBounds bounds = decision.getBounds();
            String runtimeBudget = runtimeBudgetStatus(decision, bounds);
            if (!"WITHIN_WINDOW".equals(runtimeBudget)
                    && !"NOT_AVAILABLE".equals(runtimeBudget)) {
                warningCount++;
            }

            out.println(
                    step.getWindowIndex()
                            + " | " + step.getSnapshot().getSnapshotId()
                            + " | " + formatSeconds(decision.getCurrentWindowSeconds())
                            + " | " + formatSeconds(decision.getNextWindowSeconds())
                            + " | " + formatSeconds(bounds.getMinimumWindowSeconds())
                            + " | " + formatSeconds(bounds.getMaximumWindowSeconds())
                            + " | " + formatSeconds(bounds.getCoverageReferenceSeconds())
                            + " | " + bounds.getMinimumBoundMode()
                            + " | " + bounds.getMaximumBoundMode()
                            + " | " + formatSeconds(bounds.getGaRuntimeEstimateUsedSeconds())
                            + " | " + formatSeconds(bounds.getObservedGaRuntimeSeconds())
                            + " | " + formatSeconds(bounds.getAdaptiveMaximumWindowSeconds())
                            + " | " + formatSeconds(bounds.getConfiguredMaximumWindowSeconds())
                            + " | " + bounds.isMaximumRaisedToMinimum()
                            + " | " + runtimeBudget
                            + " | " + decision.getAction()
                            + " | " + decision.getReason()
            );
        }

        out.println();
        out.println("Runtime budget notes:");
        out.println("- WITHIN_WINDOW: the observed GA runtime fits both the current and next logical window.");
        out.println("- EXCEEDS_CURRENT: the GA completed after the current logical window duration.");
        out.println("- EXCEEDS_NEXT: the measured runtime is longer than the next planned window.");
        out.println("- EXCEEDS_CURRENT_AND_NEXT: both conditions hold. This requires an explicit orchestration decision before live integration.");
        out.println("- Runtime budget warnings: " + warningCount);
        out.println();
    }

    private String runtimeBudgetStatus(
            AdaptiveWindowDecision decision,
            TemporalWindowBounds bounds
    ) {
        double observedRuntime = bounds.getObservedGaRuntimeSeconds();
        if (!Double.isFinite(observedRuntime) || observedRuntime <= EPSILON_SECONDS) {
            return "NOT_AVAILABLE";
        }

        boolean exceedsCurrent = observedRuntime
                > decision.getCurrentWindowSeconds() + EPSILON_SECONDS;
        boolean exceedsNext = observedRuntime
                > decision.getNextWindowSeconds() + EPSILON_SECONDS;

        if (exceedsCurrent && exceedsNext) {
            return "EXCEEDS_CURRENT_AND_NEXT";
        }
        if (exceedsCurrent) {
            return "EXCEEDS_CURRENT";
        }
        if (exceedsNext) {
            return "EXCEEDS_NEXT";
        }
        return "WITHIN_WINDOW";
    }

    private String formatSeconds(double value) {
        return String.format("%.6f s", value);
    }
}
