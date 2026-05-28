package config.ga;

/**
 * Configurazione della policy che scala i parametri del Genetic Algorithm.
 *
 * Questa classe non sostituisce GeneticAlgorithmConfig.
 * Definisce solo come calcolare una nuova GeneticAlgorithmConfig
 * quando la modalità adattiva è abilitata.
 */
public final class GaParameterScalingConfig {

    private final GaParameterScalingMode mode;

    private final int minPopulationSize;
    private final int maxPopulationSize;
    private final int populationPerTask;
    private final double populationPerCandidate;

    private final int minMaxGenerations;
    private final int maxMaxGenerations;
    private final int generationsPerTask;
    private final double generationsPerCandidate;

    private final int minElitismCount;
    private final double elitismRate;

    private final int minStallGenerations;
    private final double stallGenerationRate;

    /**
     * Costruisce la configurazione dello scaler.
     */
    public GaParameterScalingConfig(
            GaParameterScalingMode mode,
            int minPopulationSize,
            int maxPopulationSize,
            int populationPerTask,
            double populationPerCandidate,
            int minMaxGenerations,
            int maxMaxGenerations,
            int generationsPerTask,
            double generationsPerCandidate,
            int minElitismCount,
            double elitismRate,
            int minStallGenerations,
            double stallGenerationRate
    ) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null.");
        }

        this.mode = mode;

        this.minPopulationSize = validatePositive(
                "minPopulationSize",
                minPopulationSize
        );

        this.maxPopulationSize = validatePositive(
                "maxPopulationSize",
                maxPopulationSize
        );

        if (maxPopulationSize < minPopulationSize) {
            throw new IllegalArgumentException(
                    "maxPopulationSize must be >= minPopulationSize."
            );
        }

        this.populationPerTask = validateNonNegative(
                "populationPerTask",
                populationPerTask
        );

        this.populationPerCandidate = validateFiniteNonNegative(
                "populationPerCandidate",
                populationPerCandidate
        );

        this.minMaxGenerations = validatePositive(
                "minMaxGenerations",
                minMaxGenerations
        );

        this.maxMaxGenerations = validatePositive(
                "maxMaxGenerations",
                maxMaxGenerations
        );

        if (maxMaxGenerations < minMaxGenerations) {
            throw new IllegalArgumentException(
                    "maxMaxGenerations must be >= minMaxGenerations."
            );
        }

        this.generationsPerTask = validateNonNegative(
                "generationsPerTask",
                generationsPerTask
        );

        this.generationsPerCandidate = validateFiniteNonNegative(
                "generationsPerCandidate",
                generationsPerCandidate
        );

        this.minElitismCount = validateNonNegative(
                "minElitismCount",
                minElitismCount
        );

        this.elitismRate = validateRate(
                "elitismRate",
                elitismRate
        );

        this.minStallGenerations = validatePositive(
                "minStallGenerations",
                minStallGenerations
        );

        this.stallGenerationRate = validateRate(
                "stallGenerationRate",
                stallGenerationRate
        );
    }

    /**
     * Configurazione statica: lo scaler restituisce i parametri GA originali.
     */
    public static GaParameterScalingConfig staticMode() {
        return new GaParameterScalingConfig(
                GaParameterScalingMode.STATIC,
                40,
                40,
                0,
                0.0,
                100,
                100,
                0,
                0.0,
                2,
                0.05,
                15,
                0.15
        );
    }

    /**
     * Configurazione adattiva iniziale per il prototipo standalone.
     *
     * Per snapshot piccoli resta vicina ai valori di default.
     * Per snapshot grandi aumenta popolazione e generazioni entro limiti.
     */
    public static GaParameterScalingConfig adaptiveDefault() {
        return new GaParameterScalingConfig(
                GaParameterScalingMode.ADAPTIVE,

                40,     // minPopulationSize
                220,    // maxPopulationSize
                2,      // populationPerTask
                0.05,   // populationPerCandidate

                100,    // minMaxGenerations
                500,    // maxMaxGenerations
                3,      // generationsPerTask
                0.10,   // generationsPerCandidate

                2,      // minElitismCount
                0.05,   // elitismRate

                15,     // minStallGenerations
                0.15    // stallGenerationRate
        );
    }

    public GaParameterScalingMode getMode() {
        return mode;
    }

    public int getMinPopulationSize() {
        return minPopulationSize;
    }

    public int getMaxPopulationSize() {
        return maxPopulationSize;
    }

    public int getPopulationPerTask() {
        return populationPerTask;
    }

    public double getPopulationPerCandidate() {
        return populationPerCandidate;
    }

    public int getMinMaxGenerations() {
        return minMaxGenerations;
    }

    public int getMaxMaxGenerations() {
        return maxMaxGenerations;
    }

    public int getGenerationsPerTask() {
        return generationsPerTask;
    }

    public double getGenerationsPerCandidate() {
        return generationsPerCandidate;
    }

    public int getMinElitismCount() {
        return minElitismCount;
    }

    public double getElitismRate() {
        return elitismRate;
    }

    public int getMinStallGenerations() {
        return minStallGenerations;
    }

    public double getStallGenerationRate() {
        return stallGenerationRate;
    }

    private static int validatePositive(String fieldName, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0.");
        }

        return value;
    }

    private static int validateNonNegative(String fieldName, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }

        return value;
    }

    private static double validateFiniteNonNegative(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }

        return value;
    }

    private static double validateRate(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be in [0, 1]."
            );
        }

        return value;
    }
}