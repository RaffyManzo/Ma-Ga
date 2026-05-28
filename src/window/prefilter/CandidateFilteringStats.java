package window.prefilter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Statistiche del prefiltraggio candidati su uno snapshot.
 */
public final class CandidateFilteringStats {

    private final int originalCandidateCount;
    private final int filteredCandidateCount;
    private final int removedCandidateCount;
    private final Map<CandidateRejectionReason, Integer> countByReason;

    public CandidateFilteringStats(
            int originalCandidateCount,
            int filteredCandidateCount,
            Map<CandidateRejectionReason, Integer> countByReason
    ) {
        this.originalCandidateCount = originalCandidateCount;
        this.filteredCandidateCount = filteredCandidateCount;
        this.removedCandidateCount = Math.max(
                0,
                originalCandidateCount - filteredCandidateCount
        );
        this.countByReason = new EnumMap<>(countByReason);
    }

    public int getOriginalCandidateCount() {
        return originalCandidateCount;
    }

    public int getFilteredCandidateCount() {
        return filteredCandidateCount;
    }

    public int getRemovedCandidateCount() {
        return removedCandidateCount;
    }

    public Map<CandidateRejectionReason, Integer> getCountByReason() {
        return Collections.unmodifiableMap(countByReason);
    }

    public int getCountForReason(CandidateRejectionReason reason) {
        return countByReason.getOrDefault(reason, 0);
    }
}
