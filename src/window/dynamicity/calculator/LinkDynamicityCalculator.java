package window.dynamicity.calculator;

import model.node.NodeCandidate;
import model.snapshot.SystemSnapshot;
import window.dynamicity.compare.MetricMapComparator;
import window.dynamicity.metrics.LinkMetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Calcola Dl(k), cioè la variazione dei link/candidati source-aware.
 *
 * La copertura non viene usata qui perché non è una proprietà statica
 * del NodeCandidate. Viene calcolata separatamente dal CoverageEstimator.
 */
public final class LinkDynamicityCalculator {

    private final MetricMapComparator<LinkMetrics> comparator;

    /**
     * Costruisce il calculator con comparator standard.
     */
    public LinkDynamicityCalculator() {
        this(new MetricMapComparator<>());
    }

    /**
     * Costruisce il calculator con comparator esplicito.
     *
     * @param comparator comparator tra mappe di metriche
     */
    public LinkDynamicityCalculator(
            MetricMapComparator<LinkMetrics> comparator
    ) {
        this.comparator = Objects.requireNonNull(
                comparator,
                "comparator must not be null."
        );
    }

    /**
     * Calcola la variazione normalizzata dei link/candidati.
     *
     * @param previousSnapshot snapshot precedente
     * @param currentSnapshot snapshot corrente
     * @return Dl(k) in [0, 1]
     */
    public double compute(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        return comparator.computeVariation(
                buildLinkMap(previousSnapshot),
                buildLinkMap(currentSnapshot)
        );
    }

    /**
     * Costruisce la mappa candidateId -> LinkMetrics.
     */
    private Map<String, LinkMetrics> buildLinkMap(
            SystemSnapshot snapshot
    ) {
        Map<String, LinkMetrics> result = new HashMap<>();

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            result.put(
                    candidate.getCandidateId(),
                    new LinkMetrics(
                            candidate.getSourceVehicleId(),
                            candidate.getExecutionNodeId(),
                            candidate.getType(),
                            candidate.getAvailableBandwidth(),
                            candidate.getBaseLatencySeconds()
                    )
            );
        }

        return result;
    }
}
