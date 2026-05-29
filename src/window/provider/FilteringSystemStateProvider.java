package window.provider;

import model.snapshot.SystemSnapshot;
import window.prefilter.CandidateFilteringResult;
import window.prefilter.CandidatePrefilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Decorator di SystemStateProvider che applica CandidatePrefilter.
 */
public final class FilteringSystemStateProvider implements SystemStateProvider {

    private final SystemStateProvider delegate;
    private final CandidatePrefilter prefilter;
    private final List<CandidateFilteringResult> filteringResults;

    public FilteringSystemStateProvider(
            SystemStateProvider delegate,
            CandidatePrefilter prefilter
    ) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null.");
        }
        if (prefilter == null) {
            throw new IllegalArgumentException("prefilter must not be null.");
        }

        this.delegate = delegate;
        this.prefilter = prefilter;
        this.filteringResults = new ArrayList<>();
    }

    @Override
    public Optional<SystemSnapshot> findSnapshotAt(double observationTimeSeconds) {
        Optional<SystemSnapshot> snapshot = delegate.findSnapshotAt(
                observationTimeSeconds
        );
        return snapshot.map(this::filterSnapshot);
    }

    @Override
    public Optional<SystemSnapshot> findSnapshotAtOrAfter(double observationTimeSeconds) {
        Optional<SystemSnapshot> snapshot = delegate.findSnapshotAtOrAfter(
                observationTimeSeconds
        );
        return snapshot.map(this::filterSnapshot);
    }

    private SystemSnapshot filterSnapshot(SystemSnapshot snapshot) {
        CandidateFilteringResult result = prefilter.filter(snapshot);
        filteringResults.add(result);
        return result.getFilteredSnapshot();
    }

    public List<CandidateFilteringResult> getFilteringResults() {
        return Collections.unmodifiableList(filteringResults);
    }
}
