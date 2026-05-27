package window;

/**
 * Severità di un evento critico osservato dal sistema.
 *
 * <p>La severità non decide da sola tutto il comportamento del gestore
 * temporale, ma fornisce una regola locale per distinguere eventi informativi
 * da eventi abbastanza forti da anticipare la riesecuzione del MA-GA.</p>
 *
 * <p>La scelta finale resta esterna: un eventuale gestore temporale può
 * combinare la severità con altri dati, come tempo residuo della finestra,
 * tipo di evento o snapshot corrente.</p>
 */
public enum CriticalEventSeverity {

    /**
     * Evento debole o informativo.
     *
     * <p>Di norma non richiede una nuova ottimizzazione immediata.</p>
     */
    LOW,

    /**
     * Evento significativo ma non necessariamente urgente.
     *
     * <p>Può essere registrato o analizzato, ma non forza da solo una
     * riesecuzione anticipata.</p>
     */
    MEDIUM,

    /**
     * Evento forte.
     *
     * <p>In generale dovrebbe attivare una nuova ottimizzazione se cade prima
     * della scadenza naturale della finestra.</p>
     */
    HIGH,

    /**
     * Evento critico.
     *
     * <p>La strategia corrente va considerata potenzialmente non più valida.</p>
     */
    CRITICAL;

    /**
     * Indica se questa severità è sufficiente per proporre una riesecuzione
     * anticipata.
     *
     * @return {@code true} per {@link #HIGH} e {@link #CRITICAL}
     */
    public boolean shouldTriggerReoptimization() {
        return this == HIGH || this == CRITICAL;
    }
}
