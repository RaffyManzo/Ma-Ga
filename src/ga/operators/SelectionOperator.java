package ga.operators;

import model.genetic.Chromosome;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Operatore di selezione dei genitori.
 *
 * <p>Implementa una tournament selection per un problema di minimizzazione:
 * a ogni selezione vengono estratti {@code tournamentSize} cromosomi e viene
 * restituito quello con fitness più bassa.</p>
 */
public final class SelectionOperator {

    private final Random random;
    private final int tournamentSize;

    /**
     * Costruisce un operatore di selezione.
     *
     * @param random generatore casuale usato per campionare i partecipanti
     * @param tournamentSize numero di cromosomi estratti per torneo
     */
    public SelectionOperator(Random random, int tournamentSize) {
        this.random = Objects.requireNonNull(random, "random must not be null.");

        if (tournamentSize < 1) {
            throw new IllegalArgumentException("tournamentSize must be >= 1.");
        }

        this.tournamentSize = tournamentSize;
    }

    /**
     * Seleziona un cromosoma dalla popolazione tramite torneo.
     *
     * @param population popolazione corrente, già valutata
     * @return cromosoma con fitness più bassa tra i candidati estratti
     */
    public Chromosome select(List<Chromosome> population) {
        if (population == null || population.isEmpty()) {
            throw new IllegalArgumentException("population must not be empty.");
        }

        Chromosome best = null;

        for (int i = 0; i < tournamentSize; i++) {
            Chromosome candidate = population.get(random.nextInt(population.size()));

            if (best == null || candidate.getFitness() < best.getFitness()) {
                best = candidate;
            }
        }

        return best;
    }
}

