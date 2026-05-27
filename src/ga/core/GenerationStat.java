package ga.core;

/**
 * Contiene statistiche sintetiche su una generazione del Genetic Algorithm.
 *
 * Queste informazioni servono per ricostruire l'andamento della fitness nel tempo.
 */
public final class GenerationStat {

    private final int generationIndex;
    private final double bestFitness;
    private final double averageFitness;
    private final double worstFitness;

    /**
     * Costruisce le statistiche di una generazione.
     *
     * Parametri in ingresso:
     * - generationIndex: indice della generazione;
     * - bestFitness: migliore fitness della generazione;
     * - averageFitness: fitness media della generazione;
     * - worstFitness: peggiore fitness della generazione.
     *
     * Output:
     * - nuova istanza immutabile di GenerationStat.
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
     *
     * Output:
     * - indice numerico della generazione.
     */
    public int getGenerationIndex() {
        return generationIndex;
    }

    /**
     * Restituisce la migliore fitness della generazione.
     *
     * Output:
     * - fitness minima della generazione.
     */
    public double getBestFitness() {
        return bestFitness;
    }

    /**
     * Restituisce la fitness media della generazione.
     *
     * Output:
     * - media delle fitness della popolazione.
     */
    public double getAverageFitness() {
        return averageFitness;
    }

    /**
     * Restituisce la peggiore fitness della generazione.
     *
     * Output:
     * - fitness massima della generazione.
     */
    public double getWorstFitness() {
        return worstFitness;
    }

    /**
     * Restituisce una rappresentazione testuale sintetica della generazione.
     *
     * Output:
     * - stringa leggibile della statistica.
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

