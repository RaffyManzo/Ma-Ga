package window.prefilter;

/** Motivo per cui un candidato remoto viene rimosso dal prefilter. */
public enum CandidateRejectionReason {
    KEPT,
    NO_TASK_FOR_SOURCE,
    INVALID_CPU,
    INVALID_BANDWIDTH,
    INSUFFICIENT_COVERAGE,
    /** CLOUD privo di access link attivo e disponibile. */
    ACCESS_LINK_UNAVAILABLE,
    /** @deprecated mantenuto per compatibilità con report storici. */
    @Deprecated DEADLINE_LOWER_BOUND_TOO_HIGH,
    RESTORED_AS_FALLBACK
}
