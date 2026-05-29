package window.source;

import model.snapshot.SystemSnapshot;

import java.util.Objects;

/**
 * Osservazione restituita da una sorgente dati.
 *
 * <p>Questa classe separa tre concetti che prima venivano confusi:</p>
 *
 * <ul>
 *     <li>il tempo richiesto dal TemporalWindowManager;</li>
 *     <li>il tempo della sorgente, cioè il tempo salvato nello snapshot;</li>
 *     <li>la modalità con cui la sorgente ha prodotto lo snapshot.</li>
 * </ul>
 *
 * <p>Nel replay JSON sequenziale il tempo richiesto può essere adattivo, mentre
 * il tempo dello snapshot resta quello scritto nel file. Con MOSAIC, invece,
 * i due valori dovrebbero coincidere o essere molto vicini.</p>
 */
public final class SystemStateObservation {

    private static final double EPSILON = 1.0E-6;

    private final SystemSnapshot snapshot;
    private final double requestedObservationTimeSeconds;
    private final double sourceObservationTimeSeconds;
    private final SystemStateSourceMode sourceMode;
    private final String sourceDescription;
    private final int sequenceIndex;
    private final boolean exactTimeMatch;

    public SystemStateObservation(
            SystemSnapshot snapshot,
            double requestedObservationTimeSeconds,
            double sourceObservationTimeSeconds,
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
                "sourceObservationTimeSeconds",
                sourceObservationTimeSeconds
        );

        this.requestedObservationTimeSeconds = requestedObservationTimeSeconds;
        this.sourceObservationTimeSeconds = sourceObservationTimeSeconds;
        this.sourceMode = Objects.requireNonNull(sourceMode, "sourceMode must not be null.");
        this.sourceDescription = sourceDescription == null ? "" : sourceDescription;
        this.sequenceIndex = sequenceIndex;
        this.exactTimeMatch = exactTimeMatch;

        if (Math.abs(snapshot.getTimeSeconds() - sourceObservationTimeSeconds) > EPSILON) {
            throw new IllegalArgumentException(
                    "snapshot.timeSeconds must match sourceObservationTimeSeconds."
            );
        }
    }

    public SystemSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Tempo chiesto dal TemporalWindowManager.
     *
     * <p>Questo è il tempo logico/adattivo della finestra.</p>
     */
    public double getRequestedObservationTimeSeconds() {
        return requestedObservationTimeSeconds;
    }

    /**
     * Tempo associato alla fotografia prodotta dalla sorgente.
     *
     * <p>Nel caso JSON è il valore salvato nel file. Nel caso MOSAIC sarà il
     * tempo di simulazione dello snapshot restituito.</p>
     */
    public double getSourceObservationTimeSeconds() {
        return sourceObservationTimeSeconds;
    }

    /**
     * Alias compatibile con la versione precedente.
     *
     * <p>Il nome "actual" resta per non rompere codice già scritto. Il valore
     * però indica il tempo della sorgente, non il tempo logico del manager.</p>
     */
    public double getActualObservationTimeSeconds() {
        return sourceObservationTimeSeconds;
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
        return Math.abs(requestedObservationTimeSeconds - sourceObservationTimeSeconds) > EPSILON;
    }

    public double getTimeShiftSeconds() {
        return sourceObservationTimeSeconds - requestedObservationTimeSeconds;
    }

    public SystemStateObservation withSnapshot(SystemSnapshot updatedSnapshot) {
        Objects.requireNonNull(updatedSnapshot, "updatedSnapshot must not be null.");

        if (Math.abs(updatedSnapshot.getTimeSeconds() - sourceObservationTimeSeconds) > EPSILON) {
            throw new IllegalArgumentException(
                    "filtered snapshot must preserve the original source time."
            );
        }

        return new SystemStateObservation(
                updatedSnapshot,
                requestedObservationTimeSeconds,
                sourceObservationTimeSeconds,
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
