package config;

/**
 * Configura i parametri evolutivi del Genetic Algorithm.
 *
 * Questa classe contiene solo parametri dell'algoritmo:
 *
 * - dimensione della popolazione;
 * - numero massimo di generazioni;
 * - probabilità di crossover;
 * - probabilità di mutazione;
 * - numero di individui elitari;
 * - criterio di arresto per stagnazione;
 * - soglia minima di miglioramento;
 * - seed casuale.
 *
 */

public final class GeneticAlgorithmConfig {

    private final int populationSize;
    private final int maxGenerations;
    private final double crossoverRate;
    private final double mutationRate;
    private final int elitismCount;
    private final int stallGenerations;
    private final double fitnessImprovementEpsilon;
    private final long randomSeed;

    public GeneticAlgorithmConfig(
            int populationSize,
            int maxGenerations,
            double crossoverRate,
            double mutationRate,
            int elitismCount,
            int stallGenerations,
            double fitnessImprovementEpsilon,
            long randomSeed
    ) {
        if (populationSize < 2) {
            throw new IllegalArgumentException("populationSize must be >= 2.");
        }

        if (maxGenerations < 1) {
            throw new IllegalArgumentException("maxGenerations must be >= 1.");
        }

        validateRate("crossoverRate", crossoverRate);
        validateRate("mutationRate", mutationRate);

        if (elitismCount < 0) {
            throw new IllegalArgumentException("elitismCount must be >= 0.");
        }

        if (elitismCount >= populationSize) {
            throw new IllegalArgumentException("elitismCount must be smaller than populationSize.");
        }

        if (stallGenerations < 1) {
            throw new IllegalArgumentException("stallGenerations must be >= 1.");
        }

        if (!Double.isFinite(fitnessImprovementEpsilon)) {
            throw new IllegalArgumentException("fitnessImprovementEpsilon must be finite.");
        }

        if (fitnessImprovementEpsilon < 0.0) {
            throw new IllegalArgumentException("fitnessImprovementEpsilon must be >= 0.");
        }

        this.populationSize = populationSize;
        this.maxGenerations = maxGenerations;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.elitismCount = elitismCount;
        this.stallGenerations = stallGenerations;
        this.fitnessImprovementEpsilon = fitnessImprovementEpsilon;
        this.randomSeed = randomSeed;
    }

    /**
     * Configurazione iniziale per il prototipo standalone.
     *
     * È pensata per testare il flusso completo del MA-GA su snapshot piccoli.
     * Non va considerata una configurazione finale.
     */
    public static GeneticAlgorithmConfig defaultConfig() {
        return new GeneticAlgorithmConfig(
                40,       // populationSize
                100,      // maxGenerations
                0.80,     // crossoverRate
                0.10,     // mutationRate
                2,        // elitismCount
                15,       // stallGenerations
                1.0E-6,   // fitnessImprovementEpsilon
                42L       // randomSeed
        );
    }

    public int getPopulationSize() {
        return populationSize;
    }

    public int getMaxGenerations() {
        return maxGenerations;
    }

    public double getCrossoverRate() {
        return crossoverRate;
    }

    public double getMutationRate() {
        return mutationRate;
    }

    public int getElitismCount() {
        return elitismCount;
    }

    public int getStallGenerations() {
        return stallGenerations;
    }

    public double getFitnessImprovementEpsilon() {
        return fitnessImprovementEpsilon;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    private static void validateRate(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be in [0, 1].");
        }
    }

    @Override
    public String toString() {
        return "GeneticAlgorithmConfig{" +
                "populationSize=" + populationSize +
                ", maxGenerations=" + maxGenerations +
                ", crossoverRate=" + crossoverRate +
                ", mutationRate=" + mutationRate +
                ", elitismCount=" + elitismCount +
                ", stallGenerations=" + stallGenerations +
                ", fitnessImprovementEpsilon=" + fitnessImprovementEpsilon +
                ", randomSeed=" + randomSeed +
                '}';
    }
}