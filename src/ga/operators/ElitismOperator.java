package ga.operators;

import model.genetic.Chromosome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Operatore di elitismo.
 *
 * <p>Copia nella generazione successiva i migliori cromosomi della popolazione
 * corrente, preservando le soluzioni già trovate durante l'evoluzione.</p>
 */
public final class ElitismOperator {

    /**
     * Seleziona i migliori cromosomi della popolazione.
     *
     * @param population popolazione corrente già valutata
     * @param elitismCount numero massimo di cromosomi da conservare
     * @return copie dei cromosomi con fitness più bassa
     */
    public List<Chromosome> selectElite(List<Chromosome> population, int elitismCount) {
        if (elitismCount <= 0) {
            return new ArrayList<>();
        }

        List<Chromosome> sorted = new ArrayList<>(population);
        sorted.sort(Comparator.comparingDouble(Chromosome::getFitness));

        int count = Math.min(elitismCount, sorted.size());
        List<Chromosome> elite = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            elite.add(copyChromosome(sorted.get(i)));
        }

        return elite;
    }

    /**
     * Crea una copia superficiale di un cromosoma.
     *
     * @param source cromosoma da copiare
     * @return nuovo cromosoma con stessi geni e stessa fitness
     */
    private Chromosome copyChromosome(Chromosome source) {
        Chromosome copy = new Chromosome(new ArrayList<>(source.getGenes()));
        copy.setFitness(source.getFitness());
        return copy;
    }
}

