package ga.operators;

import model.genetic.Chromosome;
import model.genetic.Gene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Operatore di crossover.
 *
 * <p>Combina due cromosomi tramite single-point crossover. I geni prima del
 * punto di taglio arrivano dal primo genitore, quelli successivi dal secondo.
 * Se i cromosomi hanno lunghezze diverse viene usata la lunghezza minima.</p>
 */
public final class CrossoverOperator {

    private final Random random;

    /**
     * Costruisce l'operatore di crossover.
     *
     * @param random generatore casuale usato per scegliere il punto di taglio
     */
    public CrossoverOperator(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null.");
    }

    /**
     * Applica single-point crossover tra due genitori.
     *
     * @param parentA primo cromosoma genitore
     * @param parentB secondo cromosoma genitore
     * @return figlio ottenuto combinando i due genitori
     */
    public Chromosome crossover(Chromosome parentA, Chromosome parentB) {
        List<Gene> genesA = parentA.getGenes();
        List<Gene> genesB = parentB.getGenes();

        int size = Math.min(genesA.size(), genesB.size());

        if (size <= 1) {
            return copyChromosome(parentA);
        }

        int crossoverPoint = 1 + random.nextInt(size - 1);

        List<Gene> childGenes = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            if (i < crossoverPoint) {
                childGenes.add(genesA.get(i));
            } else {
                childGenes.add(genesB.get(i));
            }
        }

        return new Chromosome(childGenes);
    }

    /**
     * Crea una copia superficiale di un cromosoma.
     *
     * <p>La copia è superficiale perché {@link Gene} è immutabile.</p>
     *
     * @param source cromosoma da copiare
     * @return nuovo cromosoma con stessi geni e stessa fitness
     */
    public Chromosome copyChromosome(Chromosome source) {
        Chromosome copy = new Chromosome(new ArrayList<>(source.getGenes()));
        copy.setFitness(source.getFitness());
        return copy;
    }
}

