package window.source;

import model.snapshot.SystemSnapshot;
import window.prefilter.CandidateFilteringResult;
import window.prefilter.CandidatePrefilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Decorator che applica CandidatePrefilter agli snapshot prodotti da una sorgente.
 */
public final class FilteringSystemStateSource implements SystemStateSource {

    private final SystemStateSource delegate;
    private final CandidatePrefilter prefilter;
    private final List<CandidateFilteringResult> filteringResults;

    public FilteringSystemStateSource(
            SystemStateSource delegate,
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
    public Optional<SystemStateObservation> nextObservation(SystemStateRequest request) {
        return delegate.nextObservation(request)
                .map(this::filterObservation);
    }

    @Override
    public SystemStateSourceMode getMode() {
        return delegate.getMode();
    }

    @Override
    public String getDescription() {
        return "filtered(" + delegate.getDescription() + ")";
    }

    private SystemStateObservation filterObservation(SystemStateObservation observation) {
        CandidateFilteringResult result = prefilter.filter(observation.getSnapshot());
        filteringResults.add(result);

        SystemSnapshot filteredSnapshot = result.getFilteredSnapshot();
        return observation.withSnapshot(filteredSnapshot);
    }

    public List<CandidateFilteringResult> getFilteringResults() {
        return Collections.unmodifiableList(filteringResults);
    }
}
