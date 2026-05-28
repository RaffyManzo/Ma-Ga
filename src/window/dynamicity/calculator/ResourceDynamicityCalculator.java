package window.dynamicity.calculator;

import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;
import window.dynamicity.compare.NumericMapComparator;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Calcola Dr(k), cioè la variazione delle risorse computazionali disponibili.
 *
 * La CPU locale è indicizzata per vehicleId.
 * La CPU remota è indicizzata per executionNodeId, perché è una risorsa
 * fisica del nodo di esecuzione e non del singolo candidateId.
 */
public final class ResourceDynamicityCalculator {

    private final NumericMapComparator comparator;

    /**
     * Costruisce il calculator con comparator numerico standard.
     */
    public ResourceDynamicityCalculator() {
        this(new NumericMapComparator());
    }

    /**
     * Costruisce il calculator con comparator esplicito.
     *
     * @param comparator comparator numerico
     */
    public ResourceDynamicityCalculator(
            NumericMapComparator comparator
    ) {
        this.comparator = Objects.requireNonNull(
                comparator,
                "comparator must not be null."
        );
    }

    /**
     * Calcola la variazione normalizzata delle risorse.
     *
     * @param previousSnapshot snapshot precedente
     * @param currentSnapshot snapshot corrente
     * @return Dr(k) in [0, 1]
     */
    public double compute(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        return comparator.computeVariation(
                buildResourceMap(previousSnapshot),
                buildResourceMap(currentSnapshot)
        );
    }

    /**
     * Costruisce la mappa delle risorse computazionali osservate.
     */
    private Map<String, Double> buildResourceMap(
            SystemSnapshot snapshot
    ) {
        Map<String, Double> resources = new HashMap<>();

        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            resources.put(
                    "vehicle:" + vehicle.getVehicleId(),
                    vehicle.getLocalCpu()
            );
        }

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }

            resources.merge(
                    "executionNode:" + candidate.getExecutionNodeId(),
                    candidate.getAvailableCpu(),
                    Math::max
            );
        }

        return resources;
    }
}
