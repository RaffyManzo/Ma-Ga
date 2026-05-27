package window.dynamicity;

import window.population.PopulationReuseMode;

/**
 * Interpretazione qualitativa dell'indice di dinamicità dello scenario.
 *
 * <p>Il valore numerico della dinamicità viene calcolato da
 * {@link DynamicityEvaluator}. Questo enum traduce quel valore in una categoria
 * leggibile dal gestore temporale e direttamente collegabile alla modalità di
 * riuso della popolazione.</p>
 */
public enum DynamicityLevel {

    /**
     * Nessuna valutazione disponibile.
     *
     * <p>Tipico caso della prima finestra, dove non esiste uno snapshot
     * precedente e quindi non è possibile misurare una variazione reale.</p>
     */
    UNKNOWN,

    /**
     * Scenario stabile.
     *
     * <p>La dinamicità globale è sotto la soglia inferiore configurata. Questo
     * livello favorisce il riuso quasi completo della popolazione precedente.</p>
     */
    STABLE,

    /**
     * Scenario parzialmente cambiato.
     *
     * <p>La dinamicità globale cade tra la soglia inferiore e quella superiore.
     * Questo livello favorisce un partial restart.</p>
     */
    MODERATE,

    /**
     * Scenario fortemente cambiato.
     *
     * <p>La dinamicità globale supera la soglia superiore configurata. Questo
     * livello favorisce un cold start.</p>
     */
    HIGH;

    /**
     * Traduce il livello qualitativo nella strategia operativa di riuso.
     *
     * <p>La mappatura è volutamente concentrata qui, così il resto del package
     * può lavorare con una decisione chiara senza duplicare gli stessi switch.</p>
     *
     * @return modalità di riuso della popolazione suggerita dal livello
     */
    public PopulationReuseMode toReuseMode() {
        return switch (this) {
            case UNKNOWN -> PopulationReuseMode.FIRST_RUN;
            case STABLE -> PopulationReuseMode.WARM_START;
            case MODERATE -> PopulationReuseMode.PARTIAL_RESTART;
            case HIGH -> PopulationReuseMode.COLD_START;
        };
    }
}



