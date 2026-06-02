package model.bandwidth;

import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;

import java.util.Objects;

/**
 * Risolve il pool di banda applicabile a una scelta remota.
 *
 * <p>Nel livello 18.2 tutte le decisioni remote consumano dallo stesso pool
 * GLOBAL. Il livello 18.3 sostituisce questa implementazione mantenendo la
 * stessa API.</p>
 */
public final class BandwidthPoolResolver {

    public BandwidthPoolSnapshot resolve(
            SystemSnapshot snapshot,
            NodeCandidate candidate
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");
        if (candidate.getType() == NodeType.LOCAL) {
            throw new IllegalArgumentException("LOCAL candidates do not consume remote bandwidth pools.");
        }
        BandwidthPoolSnapshot found = null;
        for (BandwidthPoolSnapshot pool : snapshot.getBandwidthPools()) {
            if (pool.getPoolType() != BandwidthPoolType.GLOBAL) {
                continue;
            }
            if (found != null) {
                throw new IllegalArgumentException("Level 18.2 requires exactly one GLOBAL bandwidth pool.");
            }
            found = pool;
        }
        if (found == null) {
            throw new IllegalArgumentException("Missing GLOBAL bandwidth pool for remote candidate " + candidate.getCandidateId());
        }
        return found;
    }
}
