package window.source;

import java.util.Optional;

/**
 * Porta di ingresso degli snapshot nel gestore temporale.
 *
 * <p>Il MA-GA e il TemporalWindowManager non devono sapere se lo snapshot arriva
 * da JSON, MOSAIC o da un altro simulatore. Devono ricevere solo una fotografia
 * coerente del sistema.</p>
 */
public interface SystemStateSource {

    /**
     * Restituisce la prossima osservazione disponibile per la richiesta data.
     */
    Optional<SystemStateObservation> nextObservation(SystemStateRequest request);

    /**
     * Modalità della sorgente.
     */
    SystemStateSourceMode getMode();

    /**
     * Descrizione leggibile per report e debug.
     */
    default String getDescription() {
        return getMode().name();
    }
}
