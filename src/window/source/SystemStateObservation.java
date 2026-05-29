package window.source;

import model.snapshot.SystemSnapshot;

import java.util.Objects;

/**
 * Snapshot restituito da una sorgente dati.
 *
 * <p>Distingue il tempo richiesto dal gestore temporale dal tempo realmente
 * disponibile nella sorgente. Questa distinzione è necessaria perché una
 * sequenza JSON è discreta, mentre MOSAIC potrà fornire lo stato al tempo
 * richiesto.</p>
 */
public final class SystemStateObservation {

    private final SystemSnapshot snapshot;
    private final double requestedObservationTimeSeconds;
    private final double actualObservationTimeSeconds;
    private final SystemStateSourceMode sourceMode;
    private final String sourceDescription;
    private final int sequenceIndex;
    private final boolean exactTimeMatch;

    public SystemStateObservation(
            SystemSnapshot snapshot,
            double requestedObservationTimeSeconds,
            double actualObservationTimeSeconds,
            SystemStateSourceMode sourceMode,
            String sourceDescription,
            int sequenceIndex,
            boolean exactTimeMatch
    ) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null.");
        validateFiniteAndNonNegative(
                "requestedObservationTimeSeconds",
                requestedObservationTimeSeconds
        );
        validateFiniteAndNonNegative(
                "actualObservationTimeSeconds",
                actualObservationTimeSeconds
        );
        this.requestedObservationTimeSeconds = requestedObservationTimeSeconds;
        this.actualObservationTimeSeconds = actualObservationTimeSeconds;
        this.sourceMode = Objects.requireNonNull(sourceMode, "sourceMode must not be null.");
        this.sourceDescription = sourceDescription == null ? "" : sourceDescription;
        this.sequenceIndex = sequenceIndex;
        this.exactTimeMatch = exactTimeMatch;

        if (Math.abs(snapshot.getTimeSeconds() - actualObservationTimeSeconds) > 1.0E-6) {
            throw new IllegalArgumentException(
                    "snapshot.timeSeconds must match actualObservationTimeSeconds."
            );
        }
    }

    public SystemSnapshot getSnapshot() {
        return snapshot;
    }

    public double getRequestedObservationTimeSeconds() {
        return requestedObservationTimeSeconds;
    }

    public double getActualObservationTimeSeconds() {
        return actualObservationTimeSeconds;
    }

    public SystemStateSourceMode getSourceMode() {
        return sourceMode;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    public int getSequenceIndex() {
        return sequenceIndex;
    }

    public boolean isExactTimeMatch() {
        return exactTimeMatch;
    }

    public boolean isTimeShifted() {
        return Math.abs(requestedObservationTimeSeconds - actualObservationTimeSeconds) > 1.0E-6;
    }

    public SystemStateObservation withSnapshot(SystemSnapshot updatedSnapshot) {
        return new SystemStateObservation(
                updatedSnapshot,
                requestedObservationTimeSeconds,
                updatedSnapshot.getTimeSeconds(),
                sourceMode,
                sourceDescription,
                sequenceIndex,
                exactTimeMatch
        );
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
}
