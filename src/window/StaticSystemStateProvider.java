package window;

import model.SystemSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Implementazione statica di {@link SystemStateProvider} basata su una lista
 * predefinita di snapshot.
 *
 * <p>Questa classe è pensata per le prime simulazioni temporali e per i test,
 * prima dell'integrazione con un simulatore esterno come MOSAIC. Riceve una
 * lista di {@link SystemSnapshot}, la ordina per tempo simulato e permette al
 * gestore temporale di recuperare il primo snapshot disponibile a partire da
 * un certo istante.</p>
 *
 * <p>La lista interna è immutabile: il costruttore copia gli snapshot ricevuti
 * e pubblica solo una vista non modificabile. Gli oggetti {@code SystemSnapshot}
 * non vengono però clonati profondamente.</p>
 */
public final class StaticSystemStateProvider implements SystemStateProvider {

    /**
     * Tolleranza numerica usata nel confronto tra tempo richiesto e tempo dello
     * snapshot.
     *
     * <p>Serve a evitare che piccoli errori di rappresentazione dei {@code double}
     * facciano saltare uno snapshot teoricamente coincidente con il tempo
     * richiesto.</p>
     */
    private static final double EPSILON = 1.0E-9;

    /**
     * Snapshot disponibili, ordinati per {@code timeSeconds} crescente.
     */
    private final List<SystemSnapshot> snapshots;

    /**
     * Crea un provider statico.
     *
     * <p>Gli snapshot vengono copiati in una nuova lista, validati contro valori
     * nulli e ordinati per tempo simulato. La lista originale passata dal
     * chiamante può quindi essere modificata senza alterare l'ordine interno del
     * provider.</p>
     *
     * @param snapshots snapshot disponibili ordinabili per tempo
     */
    public StaticSystemStateProvider(List<SystemSnapshot> snapshots) {
        if (snapshots == null) {
            throw new IllegalArgumentException("snapshots must not be null.");
        }

        List<SystemSnapshot> copiedSnapshots = new ArrayList<>();

        for (SystemSnapshot snapshot : snapshots) {
            // Uno snapshot nullo renderebbe impossibile ordinamento e ricerca temporale.
            if (snapshot == null) {
                throw new IllegalArgumentException(
                        "snapshots must not contain null elements."
                );
            }

            copiedSnapshots.add(snapshot);
        }

        copiedSnapshots.sort(
                Comparator.comparingDouble(SystemSnapshot::getTimeSeconds)
        );

        this.snapshots = Collections.unmodifiableList(copiedSnapshots);
    }

    /**
     * Restituisce il primo snapshot con tempo maggiore o uguale al tempo richiesto.
     *
     * <p>La ricerca è lineare perché questa implementazione è pensata per test e
     * simulazioni piccole. Se in futuro gli snapshot diventassero molti, questa
     * sezione potrebbe essere sostituita con una ricerca binaria mantenendo lo
     * stesso contratto pubblico.</p>
     *
     * @param timeSeconds tempo simulato richiesto
     * @return primo snapshot disponibile a partire dal tempo richiesto
     */
    @Override
    public Optional<SystemSnapshot> findSnapshotAtOrAfter(double timeSeconds) {
        validateFiniteAndNonNegative("timeSeconds", timeSeconds);

        for (SystemSnapshot snapshot : snapshots) {
            // EPSILON rende robusto il confronto contro piccoli arrotondamenti floating-point.
            if (snapshot.getTimeSeconds() + EPSILON >= timeSeconds) {
                return Optional.of(snapshot);
            }
        }

        return Optional.empty();
    }

    /**
     * @return primo snapshot disponibile, se presente
     */
    public Optional<SystemSnapshot> getFirstSnapshot() {
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(snapshots.get(0));
    }

    /**
     * @return ultimo snapshot disponibile, se presente
     */
    public Optional<SystemSnapshot> getLastSnapshot() {
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(snapshots.get(snapshots.size() - 1));
    }

    /**
     * Restituisce tutti gli snapshot gestiti dal provider.
     *
     * @return lista immutabile ordinata per tempo simulato
     */
    public List<SystemSnapshot> getSnapshots() {
        return snapshots;
    }

    /**
     * @return {@code true} se il provider non contiene snapshot
     */
    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    /**
     * Valida che un tempo simulato sia finito e non negativo.
     */
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
