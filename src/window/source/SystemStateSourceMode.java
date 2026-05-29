package window.source;

/**
 * Modalità con cui il sistema ottiene gli snapshot da elaborare.
 */
public enum SystemStateSourceMode {

    /**
     * Replay offline: gli snapshot JSON vengono letti in ordine.
     *
     * <p>La finestra adattiva viene calcolata e salvata nel report, ma non decide
     * quale file leggere. Questa modalità serve per test confrontabili.</p>
     */
    JSON_SEQUENTIAL_REPLAY,

    /**
     * Replay offline temporale: dato un tempo richiesto, viene scelto il primo
     * snapshot JSON disponibile a quel tempo o dopo.
     *
     * <p>Questa modalità simula una sorgente indicizzata nel tempo, ma con pochi
     * file può saltare alcune osservazioni.</p>
     */
    JSON_TIME_INDEXED_REPLAY,

    /**
     * Sorgente viva collegata a MOSAIC o a un adapter equivalente.
     */
    MOSAIC_LIVE
}
