package ga.core;

/**
 * Contiene statistiche sintetiche su una generazione del Genetic Algorithm.
 *
 * <p>Le statistiche sono usate dai report per ricostruire l'andamento della
 * fitness durante l'evoluzione.</p>
 */
public final class GenerationStat {

    private final int generationIndex;
    private final double bestFitness;
    private final double averageFitness;
    private final double worstFitness;

    /**
     * Costruisce le statistiche di una generazione.
     *
     * @param generationIndex indice della generazione
     * @param bestFitness migliore fitness della generazione
     * @param averageFitness fitness media della generazione
     * @param worstFitness peggiore fitness della generazione
     */
    public GenerationStat(
            int generationIndex,
            double bestFitness,
            double averageFitness,
            double worstFitness
    ) {
        this.generationIndex = generationIndex;
        this.bestFitness = bestFitness;
        this.averageFitness = averageFitness;
        this.worstFitness = worstFitness;
    }

    /**
     * Restituisce l'indice della generazione.
     */
    public int getGenerationIndex() {
        return generationIndex;
    }

    /**
     * Restituisce la migliore fitness della generazione.
     */
    public double getBestFitness() {
        return bestFitness;
    }

    /**
     * Restituisce la fitness media della generazione.
     */
    public double getAverageFitness() {
        return averageFitness;
    }

    /**
     * Restituisce la peggiore fitness della generazione.
     */
    public double getWorstFitness() {
        return worstFitness;
    }

    /**
     * Restituisce una rappresentazione testuale sintetica della generazione.
     */
    @Override
    public String toString() {
        return "GenerationStat{" +
                "generationIndex=" + generationIndex +
                ", bestFitness=" + bestFitness +
                ", averageFitness=" + averageFitness +
                ", worstFitness=" + worstFitness +
                '}';
    }
}

