package window.source;

import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorator sperimentale che limita solo la vista di ottimizzazione ai nodi
 * locali, lasciando invariato lo snapshot fisico osservato.
 */
public final class LocalOnlySystemStateSource implements SystemStateSource {
    private final SystemStateSource delegate;

    public LocalOnlySystemStateSource(SystemStateSource delegate) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "delegate must not be null."
        );
    }

    @Override
    public Optional<SystemStateObservation> nextObservation(SystemStateRequest request) {
        return delegate.nextObservation(request).map(this::localOnlyObservation);
    }

    @Override
    public SystemStateSourceMode getMode() {
        return delegate.getMode();
    }

    @Override
    public String getDescription() {
        return "local-only(" + delegate.getDescription() + ")";
    }

    private SystemStateObservation localOnlyObservation(SystemStateObservation observation) {
        SystemSnapshot source = observation.getOptimizationSnapshot();
        List<NodeCandidate> localCandidates = new ArrayList<>();
        for (NodeCandidate candidate : source.getCandidateNodes()) {
            if (candidate.getType() == NodeType.LOCAL) {
                localCandidates.add(candidate);
            }
        }

        SystemSnapshot localOnlySnapshot = new SystemSnapshot(
                source.getSnapshotId(),
                source.getTimeSeconds(),
                source.getVehicles(),
                source.getTasks(),
                localCandidates,
                source.getAccessGateways(),
                source.getAccessLinks(),
                source.getBandwidthPools()
        );
        return observation.withOptimizationSnapshotAndDescription(
                localOnlySnapshot,
                getDescription()
        );
    }
}
