package window.event;

import java.util.Optional;

/**
 * Astrazione per cercare eventi critici in un intervallo temporale.
 *
 * <p>Il {@link TemporalWindowManager} usa questa interfaccia per capire se la
 * finestra corrente deve terminare prima della sua scadenza programmata. Se
 * viene trovato un evento critico rilevante, la nuova ottimizzazione viene
 * anticipata e il trigger diventa di tipo {@link TriggerReason#CRITICAL_EVENT}.</p>
 *
 * <p>L'interfaccia permette di separare la logica temporale dalla sorgente
 * degli eventi: nei test gli eventi possono essere statici, mentre in una
 * versione integrata possono arrivare da un simulatore o da un sistema di
 * monitoraggio.</p>
 */
public interface CriticalEventDetector {

    /**
     * Cerca il prossimo evento critico tra currentTimeSeconds e maxTimeSeconds.
     *
     * <p>Per convenzione il tempo corrente è escluso dalla ricerca, mentre il
     * limite superiore è incluso. Questo evita di ri-processare un evento già
     * usato per aprire la finestra corrente, ma consente di intercettare un
     * evento che cade esattamente sulla scadenza programmata.</p>
     *
     * @param currentTimeSeconds tempo corrente escluso dalla ricerca
     * @param maxTimeSeconds limite superiore incluso nella ricerca
     * @return prossimo evento critico, se presente
     */
    Optional<CriticalEvent> findNextCriticalEvent(
            double currentTimeSeconds,
            double maxTimeSeconds
    );
}



