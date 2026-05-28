package config.ga;

/**
 * Modalità di dimensionamento dei parametri evolutivi del MA-GA.
 */
public enum GaParameterScalingMode {

    /**
     * Usa la configurazione GA ricevuta senza modificarla.
     *
     * Utile per test controllati, debug e confronti riproducibili.
     */
    STATIC,

    /**
     * Scala populationSize, maxGenerations, elitismCount e stallGenerations
     * in base alla complessità dello snapshot osservato.
     */
    ADAPTIVE
}