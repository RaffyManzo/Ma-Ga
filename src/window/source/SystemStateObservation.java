package window.source;

import model.snapshot.SystemSnapshot;

import java.util.Objects;

/**
 * Osservazione restituita da una sorgente dati.
 *
 * <p>La classe distingue la fotografia fisica osservata dalla vista destinata
 * all'ottimizzazione. All'origine le due viste coincidono. Un decoratore può
 * applicare il prefilter e sostituire soltanto la vista ottimizzabile, senza
 * nascondere al gestore temporale lo stato reale dello scenario.</p>
 *
 * <p>Restano inoltre separati il tempo logico richiesto dal manager, il tempo
 * salvato nella sorgente e la modalità con cui la sorgente ha prodotto lo
 * snapshot.</p>
 */
public final class SystemStateObservation {

    private static final double EPSILON = 1.0E-6;

    private final SystemSnapshot observedSnapshot;
    private final SystemSnapshot optimizationSnapshot;
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
        this(
                snapshot,
                snapshot,
                requestedObservationTimeSeconds,
                sourceObservationTimeSeconds,
                sourceMode,
                sourceDescription,
                sequenceIndex,
                exactTimeMatch
        );
    }

    private SystemStateObservation(
            SystemSnapshot observedSnapshot,
            SystemSnapshot optimizationSnapshot,
            double requestedObservationTimeSeconds,
            double sourceObservationTimeSeconds,
            SystemStateSourceMode sourceMode,
            String sourceDescription,
            int sequenceIndex,
            boolean exactTimeMatch
    ) {
        this.observedSnapshot = Objects.requireNonNull(
                observedSnapshot,
                "observedSnapshot must not be null."
        );
        this.optimizationSnapshot = Objects.requireNonNull(
                optimizationSnapshot,
                "optimizationSnapshot must not be null."
        );
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
        this.sourceMode = Objects.requireNonNull(
                sourceMode,
                "sourceMode must not be null."
        );
        this.sourceDescription = sourceDescription == null ? "" : sourceDescription;
        this.sequenceIndex = sequenceIndex;
        this.exactTimeMatch = exactTimeMatch;

        validateSnapshotTime(observedSnapshot, "observed snapshot");
        validateSnapshotTime(optimizationSnapshot, "optimization snapshot");
        if (!observedSnapshot.getSnapshotId().equals(optimizationSnapshot.getSnapshotId())) {
            throw new IllegalArgumentException(
                    "observedSnapshot and optimizationSnapshot must refer to the same snapshotId."
            );
        }
    }

    /**
     * Vista destinata all'ottimizzazione.
     *
     * <p>Il metodo conserva la semantica storica per non rompere i chiamanti
     * esistenti. Il gestore temporale deve usare esplicitamente
     * {@link #getObservedSnapshot()} per dinamicità e bounds.</p>
     */
    public SystemSnapshot getSnapshot() {
        return optimizationSnapshot;
    }

    /** Fotografia grezza prodotta dalla sorgente prima del prefilter. */
    public SystemSnapshot getObservedSnapshot() {
        return observedSnapshot;
    }

    /** Vista filtrata destinata al GA. */
    public SystemSnapshot getOptimizationSnapshot() {
        return optimizationSnapshot;
    }

    public double getRequestedObservationTimeSeconds() {
        return requestedObservationTimeSeconds;
    }

    public double getSourceObservationTimeSeconds() {
        return sourceObservationTimeSeconds;
    }

    /** Alias storico per il tempo osservato dalla sorgente. */
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
        return Math.abs(
                requestedObservationTimeSeconds - sourceObservationTimeSeconds
        ) > EPSILON;
    }

    public double getTimeShiftSeconds() {
        return sourceObservationTimeSeconds - requestedObservationTimeSeconds;
    }

    /**
     * Restituisce una nuova osservazione mantenendo lo snapshot grezzo e
     * sostituendo soltanto la vista destinata al GA.
     */
    public SystemStateObservation withOptimizationSnapshot(
            SystemSnapshot updatedOptimizationSnapshot
    ) {
        return withOptimizationSnapshotAndDescription(
                updatedOptimizationSnapshot,
                sourceDescription
        );
    }

    /**
     * Restituisce una nuova osservazione mantenendo lo snapshot grezzo e
     * sostituendo la vista destinata al GA e la descrizione diagnostica.
     */
    public SystemStateObservation withOptimizationSnapshotAndDescription(
            SystemSnapshot updatedOptimizationSnapshot,
            String updatedSourceDescription
    ) {
        Objects.requireNonNull(
                updatedOptimizationSnapshot,
                "updatedOptimizationSnapshot must not be null."
        );
        return new SystemStateObservation(
                observedSnapshot,
                updatedOptimizationSnapshot,
                requestedObservationTimeSeconds,
                sourceObservationTimeSeconds,
                sourceMode,
                updatedSourceDescription,
                sequenceIndex,
                exactTimeMatch
        );
    }

    /**
     * Alias storico mantenuto per compatibilità.
     *
     * @deprecated usare {@link #withOptimizationSnapshot(SystemSnapshot)}.
     */
    @Deprecated
    public SystemStateObservation withSnapshot(SystemSnapshot updatedSnapshot) {
        return withOptimizationSnapshot(updatedSnapshot);
    }

    private void validateSnapshotTime(SystemSnapshot snapshot, String label) {
        if (Math.abs(snapshot.getTimeSeconds() - sourceObservationTimeSeconds) > EPSILON) {
            throw new IllegalArgumentException(
                    label + ".timeSeconds must match sourceObservationTimeSeconds."
            );
        }
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
