package ga.operators;

import model.genetic.Chromosome;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Esito della mutazione di un cromosoma.
 *
 * <p>Oltre al cromosoma mutato conserva gli identificativi dei task per cui
 * la decisione genetica è cambiata realmente. Questo permette al repair
 * incrementale di rivalutare soltanto i geni dirty durante le generazioni
 * interne dello stesso snapshot.</p>
 */
public final class MutationResult {
    private final Chromosome chromosome;
    private final Set<String> mutatedTaskIds;

    public MutationResult(Chromosome chromosome, Set<String> mutatedTaskIds) {
        this.chromosome = Objects.requireNonNull(
                chromosome,
                "chromosome must not be null."
        );
        Set<String> copy = new LinkedHashSet<>();
        if (mutatedTaskIds != null) {
            for (String taskId : mutatedTaskIds) {
                if (taskId != null) {
                    copy.add(taskId);
                }
            }
        }
        this.mutatedTaskIds = Collections.unmodifiableSet(copy);
    }

    public Chromosome getChromosome() {
        return chromosome;
    }

    public Set<String> getMutatedTaskIds() {
        return mutatedTaskIds;
    }

    public boolean hasMutations() {
        return !mutatedTaskIds.isEmpty();
    }
}
