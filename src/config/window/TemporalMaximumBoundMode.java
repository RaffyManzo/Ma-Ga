package config.window;

/**
 * Modalità di calcolo di DeltaT_max(k).
 *
 * <p>La modalità adattiva segue la formalizzazione:</p>
 *
 * <pre>
 * DeltaT_max(k) = alpha_T * T_coverage_ref(k)
 * </pre>
 *
 * <p>La modalità configurata serve solo per test controllati. Non sostituisce
 * la modalità formalizzata.</p>
 */
public enum TemporalMaximumBoundMode {

    /**
     * Usa un limite massimo configurato.
     */
    CONFIGURED_MAX,

    /**
     * Usa il limite adattivo basato su T_coverage_ref(k).
     */
    COVERAGE_ADAPTIVE
}
