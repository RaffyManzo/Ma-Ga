package config.window;

/**
 * Modalità di calcolo di DeltaT_min(k).
 *
 * <p>La formula resta quella della formalizzazione:</p>
 *
 * <pre>
 * DeltaT_min(k) = T_s(k) + T_GA_est(k) + T_apply(k) + epsilon_T
 * </pre>
 *
 * <p>Cambia solo il modo in cui viene stimato T_GA_est(k).</p>
 */
public enum TemporalMinimumBoundMode {

    /**
     * Usa il valore configurato di T_GA_est.
     *
     * <p>È la modalità consigliata nei test JSON, perché evita che il tempo
     * reale della JVM o del PC deformi la durata della finestra.</p>
     */
    CONFIGURED_GA_ESTIMATE,

    /**
     * Usa il tempo osservato dell'ultima esecuzione del GA.
     *
     * <p>È utile quando si vuole valutare il comportamento operativo reale
     * del sistema.</p>
     */
    OBSERVED_GA_RUNTIME
}
