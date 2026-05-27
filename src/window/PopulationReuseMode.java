package window;

/**
 * Modalità con cui il gestore temporale costruisce la popolazione iniziale
 * della nuova esecuzione MA-GA.
 *
 * <p>Questa scelta appartiene al package {@code window}, non al package
 * {@code ga}. Il gestore temporale valuta la dinamicità dello scenario,
 * sceglie una strategia di riuso e prepara una popolazione iniziale già
 * coerente con lo snapshot corrente.</p>
 *
 * <p>Il MA-GA continua quindi a ricevere una popolazione iniziale, senza dover
 * conoscere direttamente se arriva da una prima esecuzione, da un warm start,
 * da un riavvio parziale o da un cold start.</p>
 */
public enum PopulationReuseMode {

    /**
     * Prima esecuzione del sistema.
     *
     * <p>Non esiste una popolazione precedente da riutilizzare. La popolazione
     * iniziale deve quindi essere generata interamente sullo snapshot corrente.</p>
     */
    FIRST_RUN,

    /**
     * Scenario stabile.
     *
     * <p>La popolazione finale della finestra precedente viene riutilizzata
     * quasi interamente, dopo copia, adattamento e riparazione rispetto allo
     * snapshot corrente.</p>
     */
    WARM_START,

    /**
     * Scenario parzialmente cambiato.
     *
     * <p>Una quota della popolazione precedente viene mantenuta, mentre il resto
     * viene generato da zero. La quota conservata è controllata da
     * {@code rhoKeep} nella configurazione temporale.</p>
     */
    PARTIAL_RESTART,

    /**
     * Scenario molto cambiato.
     *
     * <p>La popolazione precedente non viene riutilizzata. Il MA-GA riparte da
     * una nuova popolazione generata sullo snapshot corrente.</p>
     */
    COLD_START;

    /**
     * Indica se la modalità conserva almeno una parte della popolazione finale
     * precedente.
     *
     * @return {@code true} per {@link #WARM_START} e {@link #PARTIAL_RESTART}
     */
    public boolean usesPreviousPopulation() {
        return this == WARM_START || this == PARTIAL_RESTART;
    }

    /**
     * Indica se la modalità richiede anche la generazione di nuovi cromosomi.
     *
     * <p>Il warm start puro prova a coprire la popolazione usando cromosomi
     * precedenti riparati. Le altre modalità generano una popolazione nuova
     * completa o una quota di completamento.</p>
     *
     * @return {@code true} se la modalità prevede cromosomi generati da zero
     */
    public boolean generatesNewPopulation() {
        return this == FIRST_RUN || this == PARTIAL_RESTART || this == COLD_START;
    }
}
