package ga.operators;

import model.genetic.Chromosome;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Esito dettagliato del repair aggregato della banda.
 *
 * <p>Conserva il cromosoma risultante e gli identificativi dei task modificati,
 * così {@link RepairOperator} può rivalutare soltanto i geni realmente toccati
 * dal ridimensionamento collettivo.</p>
 */
public final class BandwidthAggregateRepairResult {
    private final Chromosome chromosome;
    private final boolean changed;
    private final Set<String> affectedCandidateIds;
    private final Set<String> affectedTaskIds;

    private BandwidthAggregateRepairResult(
            Chromosome chromosome,
            boolean changed,
            Set<String> affectedCandidateIds,
            Set<String> affectedTaskIds
    ) {
        this.chromosome = chromosome;
        this.changed = changed;
        this.affectedCandidateIds = immutableCopy(affectedCandidateIds);
        this.affectedTaskIds = immutableCopy(affectedTaskIds);
    }

    public static BandwidthAggregateRepairResult unchanged(Chromosome chromosome) {
        return new BandwidthAggregateRepairResult(
                chromosome,
                false,
                Set.of(),
                Set.of()
        );
    }

    public static BandwidthAggregateRepairResult changed(
            Chromosome chromosome,
            Set<String> affectedCandidateIds,
            Set<String> affectedTaskIds
    ) {
        return new BandwidthAggregateRepairResult(
                Objects.requireNonNull(chromosome, "chromosome must not be null."),
                true,
                affectedCandidateIds,
                affectedTaskIds
        );
    }

    public Chromosome getChromosome() {
        return chromosome;
    }

    public boolean isChanged() {
        return changed;
    }

    public Set<String> getAffectedCandidateIds() {
        return affectedCandidateIds;
    }

    public Set<String> getAffectedTaskIds() {
        return affectedTaskIds;
    }

    private static Set<String> immutableCopy(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
}
