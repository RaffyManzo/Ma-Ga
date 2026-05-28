package ga.fitness.breakdown;

/**
 * Carico locale stimato per un veicolo.
 *
 * Tiene traccia dei cicli eseguiti localmente e del tempo massimo di
 * esecuzione locale osservato per quel veicolo.
 */
public final class LocalResourceUsageBreakdown {

    private static final double EPSILON = 1.0E-9;

    private final String vehicleId;
    private final double localCpu;

    private double localCpuCycles;
    private double maxLocalExecutionTimeSeconds;

    /**
     * Crea il breakdown di uso locale per un veicolo.
     *
     * @param vehicleId veicolo sorgente
     * @param localCpu CPU locale disponibile
     */
    public LocalResourceUsageBreakdown(
            String vehicleId,
            double localCpu
    ) {
        this.vehicleId = vehicleId;
        this.localCpu = localCpu;
    }

    /**
     * Aggiunge workload locale prodotto da un gene.
     *
     * @param cpuCycles cicli CPU eseguiti localmente
     * @param localExecutionTimeSeconds tempo locale del gene
     */
    public void addLocalWorkload(
            double cpuCycles,
            double localExecutionTimeSeconds
    ) {
        this.localCpuCycles += Math.max(0.0, cpuCycles);

        this.maxLocalExecutionTimeSeconds = Math.max(
                this.maxLocalExecutionTimeSeconds,
                Math.max(0.0, localExecutionTimeSeconds)
        );
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public double getLocalCpu() {
        return localCpu;
    }

    public double getLocalCpuCycles() {
        return localCpuCycles;
    }

    public double getMaxLocalExecutionTimeSeconds() {
        return maxLocalExecutionTimeSeconds;
    }

    public boolean hasLocalWorkload() {
        return localCpuCycles > EPSILON;
    }
}