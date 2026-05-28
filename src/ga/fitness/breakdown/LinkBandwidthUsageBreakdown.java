package ga.fitness.breakdown;

import model.node.NodeType;

/**
 * Uso della banda associata a un candidato/link source-aware.
 *
 * A differenza della CPU, la banda è legata al collegamento tra sorgente
 * e candidato selezionato.
 */
public final class LinkBandwidthUsageBreakdown {

    private static final double EPSILON = 1.0E-9;

    private final String candidateId;
    private final String sourceVehicleId;
    private final String executionNodeId;
    private final NodeType nodeType;
    private final double availableBandwidth;

    private double usedBandwidth;

    /**
     * Crea il breakdown di uso banda per un candidato.
     *
     * @param candidateId candidato/link selezionabile
     * @param sourceVehicleId veicolo sorgente
     * @param executionNodeId nodo fisico di esecuzione
     * @param nodeType tipo del nodo
     * @param availableBandwidth banda disponibile
     */
    public LinkBandwidthUsageBreakdown(
            String candidateId,
            String sourceVehicleId,
            String executionNodeId,
            NodeType nodeType,
            double availableBandwidth
    ) {
        this.candidateId = candidateId;
        this.sourceVehicleId = sourceVehicleId;
        this.executionNodeId = executionNodeId;
        this.nodeType = nodeType;
        this.availableBandwidth = availableBandwidth;
    }

    /**
     * Aggiunge banda assegnata da un gene.
     *
     * @param value banda da sommare
     */
    public void addBandwidth(double value) {
        this.usedBandwidth += Math.max(0.0, value);
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getSourceVehicleId() {
        return sourceVehicleId;
    }

    public String getExecutionNodeId() {
        return executionNodeId;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public double getAvailableBandwidth() {
        return availableBandwidth;
    }

    public double getUsedBandwidth() {
        return usedBandwidth;
    }

    public double getBandwidthUsagePercent() {
        if (availableBandwidth <= EPSILON) {
            return 0.0;
        }

        return (usedBandwidth / availableBandwidth) * 100.0;
    }

    public double getBandwidthOverflowRatio() {
        if (availableBandwidth <= EPSILON) {
            return usedBandwidth > 0.0 ? 1.0 : 0.0;
        }

        return Math.max(
                0.0,
                (usedBandwidth - availableBandwidth) / availableBandwidth
        );
    }

    public boolean hasBandwidthViolation() {
        return usedBandwidth > availableBandwidth;
    }

    public boolean isBandwidthSaturated(double thresholdPercent) {
        return !hasBandwidthViolation()
                && getBandwidthUsagePercent() >= thresholdPercent;
    }
}