package window.provider;

import model.snapshot.SystemSnapshot;

import java.util.Optional;

/**
 * Astrazione per ottenere lo snapshot osservato a un tempo simulato.
 */
public interface SystemStateProvider {

    /**
     * Cerca lo snapshot relativo al tempo di osservazione richiesto.
     */
    Optional<SystemSnapshot> findSnapshotAt(double observationTimeSeconds);

    /**
     * Cerca il primo snapshot disponibile al tempo richiesto o dopo.
     *
     * <p>Il metodo default mantiene compatibilità con provider continui o
     * provider che supportano solo il match esatto. I provider basati su una
     * sequenza discreta di snapshot possono sovrascriverlo.</p>
     */
    default Optional<SystemSnapshot> findSnapshotAtOrAfter(double observationTimeSeconds) {
        return findSnapshotAt(observationTimeSeconds);
    }
}
