package ga.operators;

import model.genetic.Chromosome;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Operatore di selezione dei genitori.
 *
 * Usa la tournament selection.
 *
 * Poiché il MA-GA minimizza la fitness, il vincitore del torneo è il cromosoma
 * con fitness più bassa.
 */
public final class SelectionOperator {

    private final Random random;
    private final int tournamentSize;

    /**
     * Costruisce un operatore di selezione.
     *
     * Parametri in ingresso:
     * - random: generatore casuale;
     * - tournamentSize: numero di cromosomi estratti in ogni torneo.
     *
     * Output:
     * - un SelectionOperator pronto per selezionare genitori.
     */
    public SelectionOperator(Random random, int tournamentSize) {
        // Salva il generatore casuale, impedendo che sia nullo.
        this.random = Objects.requireNonNull(random, "random must not be null.");

        // Verifica che il torneo abbia almeno un partecipante.
        if (tournamentSize < 1) {
            throw new IllegalArgumentException("tournamentSize must be >= 1.");
        }

        // Salva la dimensione del torneo.
        this.tournamentSize = tournamentSize;
    }

    /**
     * Seleziona un cromosoma dalla popolazione tramite torneo.
     *
     * Parametri in ingresso:
     * - population: popolazione corrente di cromosomi già valutati.
     *
     * Output:
     * - cromosoma vincitore del torneo, cioè quello con fitness più bassa
     *   tra i candidati estratti.
     */
    public Chromosome select(List<Chromosome> population) {
        // Verifica che la popolazione non sia nulla o vuota.
        if (population == null || population.isEmpty()) {
            throw new IllegalArgumentException("population must not be empty.");
        }

        // Variabile che conterrà il miglior cromosoma del torneo.
        Chromosome best = null;

        // Esegue il numero configurato di estrazioni casuali.
        for (int i = 0; i < tournamentSize; i++) {

            // Estrae casualmente un cromosoma dalla popolazione.
            Chromosome candidate = population.get(random.nextInt(population.size()));

            // Aggiorna il migliore se il candidato ha fitness più bassa.
            if (best == null || candidate.getFitness() < best.getFitness()) {
                best = candidate;
            }
        }

        // Restituisce il vincitore del torneo.
        return best;
    }
}

