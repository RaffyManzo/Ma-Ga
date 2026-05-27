package ga.fitness;

/**
 * Descrive il tipo operativo di decisione rappresentata da un gene.
 *
 * Questa informazione serve sia per la stampa dei risultati sia per eventuali
 * analisi future quando il MA-GA sarà collegato a MOSAIC.
 */
public enum DecisionType {

    /**
     * Il task viene eseguito interamente sul veicolo sorgente.
     */
    LOCAL_EXECUTION,

    /**
     * Il task viene offloadato interamente su un nodo remoto.
     */
    FULL_OFFLOADING,

    /**
     * Il task viene diviso tra esecuzione locale ed esecuzione remota.
     */
    PARTIAL_OFFLOADING,

    /**
     * La decisione è incoerente o non interpretabile.
     */
    UNKNOWN
}


