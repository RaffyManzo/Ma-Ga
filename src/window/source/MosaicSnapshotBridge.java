package window.source;

import model.snapshot.SystemSnapshot;

import java.util.Optional;

/**
 * Porta minima verso MOSAIC o verso un adapter equivalente.
 *
 * <p>Questa interfaccia non contiene logica MA-GA. Il suo unico compito è
 * restituire una fotografia del sistema al tempo richiesto.</p>
 */
public interface MosaicSnapshotBridge {

    /**
     * Legge o costruisce lo snapshot del sistema al tempo richiesto.
     */
    Optional<SystemSnapshot> readSnapshot(double observationTimeSeconds);

    /**
     * Descrizione leggibile dell'adapter.
     */
    default String getDescription() {
        return "MOSAIC bridge";
    }
}
