package window.population;

/**
 * Sintesi qualitativa della qualità della soluzione prodotta nella finestra precedente.
 *
 * <p>Questa informazione non appartiene al GA core: viene usata dal gestore temporale
 * per capire se la popolazione precedente merita di essere riutilizzata quasi per intero,
 * parzialmente o ignorata.</p>
 */
public enum WindowPerformanceSignal {

    /** Nessuna finestra precedente disponibile. */
    UNKNOWN,

    /** La finestra precedente ha rispettato bene deadline, copertura e vincoli duri. */
    GOOD,

    /** La finestra precedente ha mostrato qualche criticità ma non un fallimento grave. */
    WARNING,

    /** La finestra precedente ha mostrato criticità forti: molte deadline violate o vincoli duri. */
    BAD
}
