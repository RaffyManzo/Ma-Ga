package ga.operators;

import model.genetic.Chromosome;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Esito del repair CPU aggregato.
 *
 * <p>Oltre al cromosoma riparato, espone i nodi fisici ridimensionati e i task
 * effettivamente coinvolti. Il RepairOperator può così limitare il secondo
 * passaggio ai soli geni modificati dal ridimensionamento aggregato.</p>
 */
public final class CpuAggregateRepairResult {
    private final Chromosome chromosome;
    private final Set<String> scaledExecutionNodeIds;
    private final Set<String> affectedTaskIds;
    private final boolean changed;

    private CpuAggregateRepairResult(
            Chromosome chromosome,
            Set<String> scaledExecutionNodeIds,
            Set<String> affectedTaskIds,
            boolean changed
    ) {
        this.chromosome = chromosome;
        this.scaledExecutionNodeIds = immutableCopy(scaledExecutionNodeIds);
        this.affectedTaskIds = immutableCopy(affectedTaskIds);
        this.changed = changed;
    }

    public static CpuAggregateRepairResult unchanged(Chromosome chromosome) {
        return new CpuAggregateRepairResult(
                chromosome,
                Set.of(),
                Set.of(),
                false
        );
    }

    public static CpuAggregateRepairResult changed(
            Chromosome chromosome,
            Set<String> scaledExecutionNodeIds,
            Set<String> affectedTaskIds
    ) {
        return new CpuAggregateRepairResult(
                chromosome,
                Objects.requireNonNull(
                        scaledExecutionNodeIds,
                        "scaledExecutionNodeIds must not be null."
                ),
                Objects.requireNonNull(
                        affectedTaskIds,
                        "affectedTaskIds must not be null."
                ),
                true
        );
    }

    public Chromosome getChromosome() {
        return chromosome;
    }

    public Set<String> getScaledExecutionNodeIds() {
        return scaledExecutionNodeIds;
    }

    public Set<String> getAffectedTaskIds() {
        return affectedTaskIds;
    }

    public boolean isChanged() {
        return changed;
    }

    private static Set<String> immutableCopy(Set<String> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
}
