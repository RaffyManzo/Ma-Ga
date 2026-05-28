package window.prefilter;

/**
 * Motivo per cui un candidato remoto viene rimosso dal prefilter.
 */
public enum CandidateRejectionReason {

    /**
     * Il candidato è mantenuto.
     */
    KEPT,

    /**
     * Candidato non valido per nessun task dello stesso veicolo sorgente.
     */
    NO_TASK_FOR_SOURCE,

    /**
     * CPU non disponibile o non valida.
     */
    INVALID_CPU,

    /**
     * Banda non disponibile o non valida.
     */
    INVALID_BANDWIDTH,

    /**
     * Copertura nulla o troppo bassa.
     */
    INSUFFICIENT_COVERAGE,

    /**
     * Anche nel caso ottimistico, il candidato è troppo lento
     * rispetto alle deadline dei task associati.
     */
    DEADLINE_LOWER_BOUND_TOO_HIGH,

    /**
     * Candidato ripristinato come fallback per non lasciare un task
     * senza alcuna opzione di esecuzione.
     */
    RESTORED_AS_FALLBACK
}
