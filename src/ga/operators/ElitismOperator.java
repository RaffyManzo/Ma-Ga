package ga.operators;

import model.genetic.Chromosome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Operatore di elitismo.
 *
 * Conserva i migliori cromosomi della generazione corrente e li copia direttamente
 * nella generazione successiva.
 *
 * Questo impedisce al GA di perdere le migliori soluzioni già trovate.
 */
public final class ElitismOperator {

    /**
     * Seleziona i migliori cromosomi della popolazione.
     *
     * Parametri in ingresso:
     * - population: popolazione corrente già valutata;
     * - elitismCount: numero di cromosomi migliori da conservare.
     *
     * Output:
     * - lista di copie dei migliori cromosomi.
     */
    public List<Chromosome> selectElite(List<Chromosome> population, int elitismCount) {
        // Se l'elitismo è disattivato, restituisce una lista vuota.
        if (elitismCount <= 0) {
            return new ArrayList<>();
        }

        // Crea una copia della popolazione per non modificare l'ordine originale.
        List<Chromosome> sorted = new ArrayList<>(population);

        // Ordina i cromosomi per fitness crescente, perché il problema è di minimizzazione.
        sorted.sort(Comparator.comparingDouble(Chromosome::getFitness));

        // Calcola quanti cromosomi elitari possono essere copiati davvero.
        int count = Math.min(elitismCount, sorted.size());

        // Crea la lista dei cromosomi elitari.
        List<Chromosome> elite = new ArrayList<>();

        // Copia i migliori cromosomi nella lista elite.
        for (int i = 0; i < count; i++) {
            elite.add(copyChromosome(sorted.get(i)));
        }

        // Restituisce la lista dei cromosomi elitari.
        return elite;
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
    private Chromosome copyChromosome(Chromosome source) {
        // Crea una nuova lista contenente gli stessi geni.
        Chromosome copy = new Chromosome(new ArrayList<>(source.getGenes()));

        // Copia il valore di fitness.
        copy.setFitness(source.getFitness());

        // Restituisce la copia.
        return copy;
    }
}

