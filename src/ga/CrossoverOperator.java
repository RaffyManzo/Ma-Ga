package ga;

import model.Chromosome;
import model.Gene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Operatore di crossover.
 *
 * In questa prima versione implementa un single-point crossover.
 *
 * Il crossover combina porzioni di due cromosomi genitori per generare
 * un nuovo cromosoma figlio.
 */
public final class CrossoverOperator {

    private final Random random;

    /**
     * Costruisce l'operatore di crossover.
     *
     * Parametri in ingresso:
     * - random: generatore casuale usato per scegliere il punto di taglio.
     *
     * Output:
     * - un CrossoverOperator pronto a generare figli.
     */
    public CrossoverOperator(Random random) {
        // Salva il generatore casuale, impedendo che sia nullo.
        this.random = Objects.requireNonNull(random, "random must not be null.");
    }

    /**
     * Applica single-point crossover tra due genitori.
     *
     * Parametri in ingresso:
     * - parentA: primo cromosoma genitore;
     * - parentB: secondo cromosoma genitore.
     *
     * Output:
     * - nuovo cromosoma figlio composto da una parte di parentA e una parte di parentB.
     */
    public Chromosome crossover(Chromosome parentA, Chromosome parentB) {
        // Estrae la lista dei geni del primo genitore.
        List<Gene> genesA = parentA.getGenes();

        // Estrae la lista dei geni del secondo genitore.
        List<Gene> genesB = parentB.getGenes();

        // Usa la dimensione minima per evitare problemi se i cromosomi hanno lunghezze diverse.
        int size = Math.min(genesA.size(), genesB.size());

        // Se il cromosoma ha zero o un gene, il crossover non è significativo.
        if (size <= 1) {
            return copyChromosome(parentA);
        }

        // Sceglie un punto di crossover interno, evitando gli estremi.
        int crossoverPoint = 1 + random.nextInt(size - 1);

        // Crea la lista dei geni del figlio.
        List<Gene> childGenes = new ArrayList<>();

        // Costruisce il figlio combinando i geni prima e dopo il punto di taglio.
        for (int i = 0; i < size; i++) {

            // Prima del punto di taglio copia i geni dal primo genitore.
            if (i < crossoverPoint) {
                childGenes.add(genesA.get(i));
            }

            // Dopo il punto di taglio copia i geni dal secondo genitore.
            else {
                childGenes.add(genesB.get(i));
            }
        }

        // Restituisce il nuovo cromosoma figlio.
        return new Chromosome(childGenes);
    }

    /**
     * Crea una copia superficiale di un cromosoma.
     *
     * Parametri in ingresso:
     * - source: cromosoma da copiare.
     *
     * Output:
     * - nuovo Chromosome con stessi geni e stessa fitness.
     */
    public Chromosome copyChromosome(Chromosome source) {
        // Copia la lista dei geni in una nuova lista.
        Chromosome copy = new Chromosome(new ArrayList<>(source.getGenes()));

        // Copia anche il valore di fitness.
        copy.setFitness(source.getFitness());

        // Restituisce la copia.
        return copy;
    }
}