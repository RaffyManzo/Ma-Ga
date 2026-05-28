package io.reporting.diagnostics.deadline;

/**
 * Causa diagnostica principale di una deadline non rispettata.
 *
 * Questa enum è usata solo dal reporting diagnostico.
 * Non modifica fitness, repair, mutazione o selezione.
 */
public enum DeadlineViolationCause {

    /**
     * Il task rispetta la deadline.
     */
    DEADLINE_RESPECTED,

    /**
     * Esecuzione puramente locale più lenta della deadline.
     */
    LOCAL_EXECUTION_BOTTLENECK,

    /**
     * Il task è parzialmente offloadato, ma il ramo locale domina
     * il completion time.
     */
    LOCAL_BRANCH_DOMINATES,

    /**
     * Il task è parzialmente offloadato, ma il ramo remoto domina
     * il completion time.
     */
    REMOTE_BRANCH_DOMINATES,

    /**
     * Locale e remoto sono entrambi vicini al critical path.
     */
    MIXED_LOCAL_REMOTE_BOTTLENECK,

    /**
     * Upload dominante nella pipeline remota.
     */
    UPLOAD_BOTTLENECK,

    /**
     * Esecuzione remota dominante nella pipeline remota.
     */
    REMOTE_EXECUTION_BOTTLENECK,

    /**
     * Download dominante nella pipeline remota.
     */
    DOWNLOAD_BOTTLENECK,

    /**
     * Latenza base dominante nella pipeline remota.
     */
    BASE_LATENCY_BOTTLENECK,

    /**
     * Pipeline remota distribuita senza singola componente dominante.
     */
    MIXED_REMOTE_PIPELINE_BOTTLENECK,

    /**
     * Candidato remoto con copertura insufficiente.
     */
    COVERAGE_INSUFFICIENT,

    /**
     * Causa non classificabile con i dati disponibili.
     */
    UNKNOWN
}
