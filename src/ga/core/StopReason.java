package ga.core;

/**
 * Indica perché il ciclo evolutivo del MA-GA si è fermato.
 *
 * Questa informazione è utile per debug, diario di bordo e analisi sperimentale.
 */
public enum StopReason {

    /**
     * Lo snapshot non contiene task attivi da ottimizzare.
     */
    EMPTY_TASK_SET,

    /**
     * L'algoritmo ha raggiunto il numero massimo di generazioni configurato.
     */
    MAX_GENERATIONS_REACHED,

    /**
     * L'algoritmo si è fermato perché la fitness non migliorava più.
     */
    STAGNATION_REACHED
}

