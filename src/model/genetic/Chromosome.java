package model.genetic;

import java.util.List;

/**
 * Soluzione candidata del GA.
 *
 * <p>Ogni cromosoma contiene un gene per task e una fitness scalare. La fitness
 * viene inizializzata a infinito finché il cromosoma non viene valutato.</p>
 */
public class Chromosome {

    private List<Gene> genes;
    private double fitness = Double.POSITIVE_INFINITY;

    public Chromosome() {
    }

    /**
     * Crea un cromosoma non ancora valutato.
     *
     * @param genes decisioni di offloading contenute nel cromosoma
     */
    public Chromosome(List<Gene> genes) {
        this.genes = genes;
    }

    /**
     * Crea un cromosoma con fitness già assegnata.
     *
     * @param genes decisioni di offloading contenute nel cromosoma
     * @param fitness valore di fitness calcolato
     */
    public Chromosome(List<Gene> genes, double fitness) {
        this.genes = genes;
        this.fitness = fitness;
    }

    public List<Gene> getGenes() {
        return genes;
    }

    public void setGenes(List<Gene> genes) {
        this.genes = genes;
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    @Override
    public String toString() {
        return "Chromosome{" +
                "genes=" + genes +
                ", fitness=" + fitness +
                '}';
    }
}

