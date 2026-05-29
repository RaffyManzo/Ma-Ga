package window.source;

import window.trigger.ReoptimizationTrigger;

import java.util.Objects;

/**
 * Richiesta di osservazione dello stato del sistema.
 *
 * <p>Il gestore temporale produce questa richiesta. La sorgente dati decide come
 * soddisfarla. Nei test JSON può restituire il prossimo snapshot della sequenza;
 * con MOSAIC potrà interrogare il simulatore al tempo richiesto.</p>
 */
public final class SystemStateRequest {

    private final int windowIndex;
    private final ReoptimizationTrigger plannedTrigger;
    private final double requestedObservationTimeSeconds;
    private final double currentWindowDurationSeconds;

    public SystemStateRequest(
            int windowIndex,
            ReoptimizationTrigger plannedTrigger,
            double requestedObservationTimeSeconds,
            double currentWindowDurationSeconds
    ) {
        if (windowIndex < 0) {
            throw new IllegalArgumentException("windowIndex must be >= 0.");
        }
        validateFiniteAndNonNegative(
                "requestedObservationTimeSeconds",
                requestedObservationTimeSeconds
        );
        validatePositive(
                "currentWindowDurationSeconds",
                currentWindowDurationSeconds
        );

        this.windowIndex = windowIndex;
        this.plannedTrigger = Objects.requireNonNull(
                plannedTrigger,
                "plannedTrigger must not be null."
        );
        this.requestedObservationTimeSeconds = requestedObservationTimeSeconds;
        this.currentWindowDurationSeconds = currentWindowDurationSeconds;
    }

    public int getWindowIndex() {
        return windowIndex;
    }

    public ReoptimizationTrigger getPlannedTrigger() {
        return plannedTrigger;
    }

    public double getRequestedObservationTimeSeconds() {
        return requestedObservationTimeSeconds;
    }

    public double getCurrentWindowDurationSeconds() {
        return currentWindowDurationSeconds;
    }

    private static void validateFiniteAndNonNegative(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }

    private static void validatePositive(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be > 0.");
        }
    }
}
