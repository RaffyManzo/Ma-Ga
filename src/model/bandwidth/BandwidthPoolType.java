package model.bandwidth;

/** Tipologia del pool radio condiviso. */
public enum BandwidthPoolType {
    /** Unico pool globale: rappresenta direttamente il Bmax della formalizzazione. */
    GLOBAL,
    /** Pool associato a una RSU o a un gateway di accesso. */
    GATEWAY,
    /** Pool dedicato a un collegamento diretto V2V. */
    DIRECT_V2V
}
