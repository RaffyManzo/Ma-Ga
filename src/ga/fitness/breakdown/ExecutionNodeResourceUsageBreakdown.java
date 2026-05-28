package ga.fitness.breakdown;

import model.node.NodeType;

/**
 * Uso aggregato della CPU su un nodo fisico di esecuzione.
 *
 * Più candidati possono puntare allo stesso executionNodeId.
 * Questa classe serve a controllare l'uso complessivo della CPU del nodo.
 */
public final class ExecutionNodeResourceUsageBreakdown {

    private static final double EPSILON = 1.0E-9;

    private final String executionNodeId;
    private final NodeType nodeType;
    private final double availableCpu;

    private double usedCpu;

    /**
     * Crea il breakdown di uso CPU per un nodo fisico.
     *
     * @param executionNodeId nodo fisico di esecuzione
     * @param nodeType tipo del nodo
     * @param availableCpu CPU disponibile sul nodo
     */
    public ExecutionNodeResourceUsageBreakdown(
            String executionNodeId,
            NodeType nodeType,
            double availableCpu
    ) {
        this.executionNodeId = executionNodeId;
        this.nodeType = nodeType;
        this.availableCpu = availableCpu;
    }

    /**
     * Aggiunge CPU assegnata da un gene.
     *
     * @param value CPU da sommare
     */
    public void addCpu(double value) {
        this.usedCpu += Math.max(0.0, value);
    }

    public String getExecutionNodeId() {
        return executionNodeId;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public double getAvailableCpu() {
        return availableCpu;
    }

    public double getUsedCpu() {
        return usedCpu;
    }

    public double getCpuUsagePercent() {
        if (availableCpu <= EPSILON) {
            return 0.0;
        }

        return (usedCpu / availableCpu) * 100.0;
    }

    public double getCpuOverflowRatio() {
        if (availableCpu <= EPSILON) {
            return usedCpu > 0.0 ? 1.0 : 0.0;
        }

        return Math.max(0.0, (usedCpu - availableCpu) / availableCpu);
    }

    public boolean hasCpuViolation() {
        return usedCpu > availableCpu;
    }

    public boolean isCpuSaturated(double thresholdPercent) {
        return !hasCpuViolation()
                && getCpuUsagePercent() >= thresholdPercent;
    }
}