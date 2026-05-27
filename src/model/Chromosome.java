package model;

import java.util.List;

public class Chromosome {

    private List<Gene> genes;
    private double fitness = Double.POSITIVE_INFINITY;

    public Chromosome() {
    }

    public Chromosome(List<Gene> genes) {
        this.genes = genes;
    }

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