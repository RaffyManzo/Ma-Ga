package window;

import model.SystemSnapshot;

import java.util.Optional;

/**
 * Astrazione per ottenere uno snapshot del sistema a partire da un tempo simulato.
 *
 * <p>Il {@link TemporalWindowManager} dipende da questa interfaccia per
 * recuperare lo stato del sistema su cui eseguire il MA-GA. In questo modo il
 * gestore temporale non deve sapere se gli snapshot arrivano da una lista
 * statica, da file caricati in memoria o da un simulatore integrato.</p>
 *
 * <p>La responsabilità dell'implementazione è trovare lo snapshot più adatto
 * alla richiesta temporale. La responsabilità del manager è invece usare quello
 * snapshot per valutare dinamicità, preparare la popolazione ed eseguire
 * l'ottimizzazione.</p>
 */
public interface SystemStateProvider {

    /**
     * Restituisce il primo snapshot disponibile al tempo indicato o dopo quel tempo.
     *
     * <p>Nella versione statica può selezionare uno snapshot già caricato. In
     * una versione integrata con simulatore potrà invece costruire lo snapshot
     * dallo stato corrente del simulatore.</p>
     *
     * <p>Se non esiste nessuno snapshot disponibile a partire dal tempo
     * richiesto, il metodo restituisce {@link Optional#empty()} e il ciclo
     * temporale può terminare senza errore.</p>
     *
     * @param timeSeconds tempo simulato richiesto
     * @return snapshot disponibile, se presente
     */
    Optional<SystemSnapshot> findSnapshotAtOrAfter(double timeSeconds);
}
