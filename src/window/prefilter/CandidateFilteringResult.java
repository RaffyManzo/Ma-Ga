package window.prefilter;

import model.snapshot.SystemSnapshot;

import java.util.List;

/**
 * Risultato del prefiltraggio di uno snapshot.
 */
public final class CandidateFilteringResult {

    private final SystemSnapshot originalSnapshot;
    private final SystemSnapshot filteredSnapshot;
    private final CandidateFilteringStats stats;
    private final List<FilteredCandidateRecord> records;

    public CandidateFilteringResult(
            SystemSnapshot originalSnapshot,
            SystemSnapshot filteredSnapshot,
            CandidateFilteringStats stats,
            List<FilteredCandidateRecord> records
    ) {
        this.originalSnapshot = originalSnapshot;
        this.filteredSnapshot = filteredSnapshot;
        this.stats = stats;
        this.records = List.copyOf(records);
    }

    public SystemSnapshot getOriginalSnapshot() {
        return originalSnapshot;
    }

    public SystemSnapshot getFilteredSnapshot() {
        return filteredSnapshot;
    }

    public CandidateFilteringStats getStats() {
        return stats;
    }

    public List<FilteredCandidateRecord> getRecords() {
        return records;
    }
}
