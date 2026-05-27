package window;

/**
 * Tipologie di evento critico rilevabili dal livello di osservazione.
 *
 * <p>Un evento critico non è uno snapshot e non contiene lo stato completo del
 * sistema. È invece un segnale puntuale che può indurre il gestore temporale a
 * richiedere uno snapshot aggiornato e a rieseguire il MA-GA prima della
 * scadenza naturale della finestra.</p>
 *
 * <p>Le tipologie sono organizzate per area semantica:</p>
 *
 * <ul>
 *     <li>mobilità e copertura dei veicoli;</li>
 *     <li>arrivo, rimozione o rischio sui task;</li>
 *     <li>degrado o recupero di link e risorse computazionali;</li>
 *     <li>eventi personalizzati per simulazioni e test.</li>
 * </ul>
 */
public enum CriticalEventType {

    /**
     * Nuovo veicolo entrato nello scenario osservato.
     *
     * <p>Può rendere disponibili nuove risorse locali o nuovi candidati V2V,
     * oppure modificare la topologia dei link.</p>
     */
    VEHICLE_JOINED,

    /**
     * Veicolo uscito dallo scenario osservato.
     *
     * <p>Può invalidare task, link o cromosomi che facevano riferimento al
     * veicolo non più presente.</p>
     */
    VEHICLE_LEFT,

    /**
     * Nuovo task comparso durante la finestra corrente.
     *
     * <p>Può richiedere una nuova ottimizzazione perché la soluzione corrente
     * non contiene ancora una decisione di offloading per quel task.</p>
     */
    TASK_ARRIVAL,

    /**
     * Task non più presente o non più rilevante.
     *
     * <p>Può rendere sovradimensionata o incoerente una soluzione calcolata
     * prima della rimozione.</p>
     */
    TASK_REMOVED,

    /**
     * Peggioramento significativo di un link candidato.
     *
     * <p>Esempi tipici sono riduzione della banda, aumento della latenza o
     * peggioramento della stabilità del collegamento.</p>
     */
    LINK_DEGRADED,

    /**
     * Link non più disponibile.
     *
     * <p>È più forte di un degrado: una decisione di offloading che usa quel
     * candidato può diventare direttamente non valida.</p>
     */
    LINK_LOST,

    /**
     * Tempo di copertura stimato diventato insufficiente o vicino alla soglia
     * di rischio.
     *
     * <p>Questo evento è legato alla mobilità e alla durata attesa della
     * relazione sorgente-destinazione.</p>
     */
    COVERAGE_RISK,

    /**
     * Rischio di handover o cambio di copertura.
     *
     * <p>Segnala che la qualità o la disponibilità futura di un collegamento
     * potrebbe cambiare rapidamente.</p>
     */
    HANDOVER_RISK,

    /**
     * Riduzione significativa delle risorse disponibili su un nodo di esecuzione.
     *
     * <p>Può rendere non più sostenibili alcune assegnazioni di task già
     * pianificate.</p>
     */
    NODE_RESOURCE_DROP,

    /**
     * Recupero significativo di risorse.
     *
     * <p>Non sempre impone una riesecuzione urgente, ma può essere utile per
     * migliorare una strategia precedente diventata conservativa.</p>
     */
    NODE_RESOURCE_RECOVERY,

    /**
     * Rischio di violazione delle deadline dei task attivi.
     *
     * <p>Può dipendere da variazioni di latenza, banda, CPU disponibile o dalla
     * comparsa di task più urgenti.</p>
     */
    DEADLINE_RISK,

    /**
     * Evento definito manualmente per simulazioni statiche o test.
     *
     * <p>Permette di introdurre segnali artificiali senza estendere subito
     * l'enum con una nuova categoria specifica.</p>
     */
    CUSTOM;

    /**
     * Indica se il tipo di evento è collegato a mobilità, copertura o link.
     *
     * <p>La categoria include anche eventi di link perché nel modello
     * source-aware la qualità del collegamento dipende spesso dalla posizione e
     * dalla mobilità dei veicoli coinvolti.</p>
     *
     * @return {@code true} se l'evento è classificabile come mobility-related
     */
    public boolean isMobilityRelated() {
        return this == VEHICLE_JOINED
                || this == VEHICLE_LEFT
                || this == COVERAGE_RISK
                || this == HANDOVER_RISK
                || this == LINK_DEGRADED
                || this == LINK_LOST;
    }

    /**
     * Indica se il tipo di evento riguarda task, arrivi, rimozioni o deadline.
     *
     * @return {@code true} se l'evento è classificabile come task-related
     */
    public boolean isTaskRelated() {
        return this == TASK_ARRIVAL
                || this == TASK_REMOVED
                || this == DEADLINE_RISK;
    }

    /**
     * Indica se il tipo di evento riguarda risorse computazionali o comunicative.
     *
     * <p>I link degradati o persi rientrano anche in questa categoria perché
     * banda e latenza sono risorse di comunicazione usate dal fitness.</p>
     *
     * @return {@code true} se l'evento è classificabile come resource-related
     */
    public boolean isResourceRelated() {
        return this == NODE_RESOURCE_DROP
                || this == NODE_RESOURCE_RECOVERY
                || this == LINK_DEGRADED
                || this == LINK_LOST;
    }
}
