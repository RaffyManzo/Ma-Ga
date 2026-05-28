package window.dynamicity.metrics;

import model.node.NodeType;
import window.dynamicity.math.DynamicityMath;

import java.util.Objects;

/**
 * Stato confrontabile di un link/candidato source-aware.
 */
public final class LinkMetrics
        implements ComparableMetric<LinkMetrics> {

    private final String sourceVehicleId;
    private final String executionNodeId;
    private final NodeType nodeType;
    private final double availableBandwidth;
    private final double baseLatencySeconds;

    /**
     * Costruisce la metrica link.
     *
     * @param sourceVehicleId veicolo sorgente
     * @param executionNodeId nodo fisico di esecuzione
     * @param nodeType tipo del nodo
     * @param availableBandwidth banda disponibile
     * @param baseLatencySeconds latenza base
     */
    public LinkMetrics(
            String sourceVehicleId,
            String executionNodeId,
            NodeType nodeType,
            double availableBandwidth,
            double baseLatencySeconds
    ) {
        this.sourceVehicleId = sourceVehicleId;
        this.executionNodeId = executionNodeId;
        this.nodeType = nodeType;
        this.availableBandwidth = availableBandwidth;
        this.baseLatencySeconds = baseLatencySeconds;
    }

    @Override
    public double relativeVariation(LinkMetrics other) {
        double semanticVariation =
                sameSemanticLink(other) ? 0.0 : 1.0;

        double bandwidthVariation = DynamicityMath.relativeDifference(
                availableBandwidth,
                other.availableBandwidth
        );

        double latencyVariation = DynamicityMath.relativeDifference(
                baseLatencySeconds,
                other.baseLatencySeconds
        );

        return DynamicityMath.clamp01(
                0.10 * semanticVariation
                        + 0.55 * bandwidthVariation
                        + 0.35 * latencyVariation
        );
    }

    /**
     * Verifica se i due candidati rappresentano la stessa relazione logica.
     */
    private boolean sameSemanticLink(LinkMetrics other) {
        return Objects.equals(sourceVehicleId, other.sourceVehicleId)
                && Objects.equals(executionNodeId, other.executionNodeId)
                && nodeType == other.nodeType;
    }
}
