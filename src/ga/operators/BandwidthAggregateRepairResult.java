package ga.operators;

import model.genetic.Chromosome;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Esito dettagliato del repair aggregato della banda. */
public final class BandwidthAggregateRepairResult {
    private final Chromosome chromosome;
    private final boolean changed;
    private final Set<String> affectedBandwidthPoolIds;
    private final Set<String> affectedTaskIds;

    private BandwidthAggregateRepairResult(
            Chromosome chromosome,
            boolean changed,
            Set<String> affectedBandwidthPoolIds,
            Set<String> affectedTaskIds
    ) {
        this.chromosome = chromosome;
        this.changed = changed;
        this.affectedBandwidthPoolIds = immutableCopy(affectedBandwidthPoolIds);
        this.affectedTaskIds = immutableCopy(affectedTaskIds);
    }

    public static BandwidthAggregateRepairResult unchanged(Chromosome chromosome) {
        return new BandwidthAggregateRepairResult(chromosome, false, Set.of(), Set.of());
    }

    public static BandwidthAggregateRepairResult changed(
            Chromosome chromosome,
            Set<String> affectedBandwidthPoolIds,
            Set<String> affectedTaskIds
    ) {
        return new BandwidthAggregateRepairResult(
                Objects.requireNonNull(chromosome, "chromosome must not be null."),
                true,
                affectedBandwidthPoolIds,
                affectedTaskIds
        );
    }

    public Chromosome getChromosome() { return chromosome; }
    public boolean isChanged() { return changed; }
    public Set<String> getAffectedBandwidthPoolIds() { return affectedBandwidthPoolIds; }
    public Set<String> getAffectedTaskIds() { return affectedTaskIds; }

    /** Alias storico mantenuto durante la migrazione 18.1 -> 18.2. */
    @Deprecated
    public Set<String> getAffectedCandidateIds() { return affectedBandwidthPoolIds; }

    private static Set<String> immutableCopy(Set<String> source) {
        if (source == null || source.isEmpty()) { return Set.of(); }
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
}
