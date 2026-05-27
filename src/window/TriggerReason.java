package window;

/**
 * Motivo per cui il gestore temporale decide di rieseguire il MA-GA.
 *
 * <p>Questo enum descrive la causa temporale della nuova ottimizzazione. Non
 * contiene lo snapshot da ottimizzare e non decide la modalità di riuso della
 * popolazione: fornisce solo l'informazione sul perché il sistema sta
 * avviando una nuova esecuzione.</p>
 *
 * <p>Le cause previste sono:</p>
 *
 * <ul>
 *     <li>prima finestra temporale, senza storia precedente;</li>
 *     <li>scadenza naturale dell'intervallo programmato;</li>
 *     <li>evento critico che anticipa la fine della finestra corrente.</li>
 * </ul>
 */
public enum TriggerReason {

    /**
     * Prima esecuzione del sistema.
     *
     * <p>Non esistono ancora uno snapshot precedente, una popolazione finale
     * precedente o una decisione di riuso da recuperare.</p>
     */
    FIRST_RUN,

    /**
     * Raggiunta la scadenza naturale della finestra temporale.
     *
     * <p>Corrisponde al caso ordinario in cui il sistema riesegue il MA-GA
     * perché è terminato l'intervallo fisso o adattivo previsto.</p>
     */
    SCHEDULED_WINDOW_EXPIRATION,

    /**
     * Un evento critico ha reso potenzialmente non più affidabile la strategia
     * corrente e richiede una nuova ottimizzazione.
     *
     * <p>In questo caso la riesecuzione avviene prima della scadenza naturale
     * della finestra temporale.</p>
     */
    CRITICAL_EVENT;

    /**
     * Indica se la riesecuzione è stata causata da un evento critico.
     *
     * @return {@code true} solo per {@link #CRITICAL_EVENT}
     */
    public boolean isCritical() {
        return this == CRITICAL_EVENT;
    }

    /**
     * Indica se la riesecuzione corrisponde alla scadenza programmata.
     *
     * @return {@code true} solo per {@link #SCHEDULED_WINDOW_EXPIRATION}
     */
    public boolean isScheduled() {
        return this == SCHEDULED_WINDOW_EXPIRATION;
    }
}
