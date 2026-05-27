package window.provider;

import model.snapshot.SystemSnapshot;

import java.util.Optional;

/**
 * Astrazione per ottenere lo snapshot osservato a un tempo simulato.
 *
 * Il gestore temporale usa questa interfaccia dopo aver calcolato il tempo di
 * osservazione: triggerTime + dataCollectionDelaySeconds.
 */
public interface SystemStateProvider {

    /**
     * Cerca lo snapshot relativo al tempo di osservazione richiesto.
     *
     * @param observationTimeSeconds tempo in cui lo stato viene osservato
     * @return snapshot osservato, se disponibile
     */
    Optional<SystemSnapshot> findSnapshotAt(double observationTimeSeconds);
}