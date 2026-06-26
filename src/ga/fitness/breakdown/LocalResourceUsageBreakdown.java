package ga.fitness.breakdown;

/**
 * Carico locale aggregato per un veicolo.
 *
 * <p>Oltre ai cicli locali, il breakdown distingue il tempo calcolato in modo
 * indipendente per ogni task dal completion time corretto dalla contesa EDF.
 * Il rapporto massimo di domanda confronta, per ogni prefisso EDF, i cicli
 * cumulativi con la capacità disponibile entro la deadline del task.</p>
 */
public final class LocalResourceUsageBreakdown {

    private static final double EPSILON = 1.0E-9;

    private final String vehicleId;
    private final double localCpu;

    private int localTaskCount;
    private int deadlineViolationCount;
    private double localCpuCycles;
    private double maxIndependentLocalExecutionTimeSeconds;
    private double maxLocalExecutionTimeSeconds;
    private double maxLocalDemandRatio;
    private double maxContentionDelaySeconds;

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
     * Adapter compatibile con il modello precedente.
     *
     * <p>Il valore temporale viene interpretato sia come tempo indipendente sia
     * come tempo corretto. I nuovi chiamanti devono usare l'overload completo.</p>
     */
    public void addLocalWorkload(
            double cpuCycles,
            double localExecutionTimeSeconds
    ) {
        addLocalWorkload(
                cpuCycles,
                localExecutionTimeSeconds,
                localExecutionTimeSeconds,
                0.0,
                true
        );
    }

    /**
     * Aggiunge una porzione locale già valutata dal modello di contesa.
     *
     * @param cpuCycles cicli CPU eseguiti localmente
     * @param independentExecutionTimeSeconds tempo del task isolato
     * @param contendedCompletionTimeSeconds completion time EDF corretto
     * @param demandRatio rapporto tra cicli cumulativi e capacità entro deadline
     * @param deadlineRespected esito della deadline dopo la contesa
     */
    public void addLocalWorkload(
            double cpuCycles,
            double independentExecutionTimeSeconds,
            double contendedCompletionTimeSeconds,
            double demandRatio,
            boolean deadlineRespected
    ) {
        double safeCycles = nonNegative(cpuCycles);
        if (safeCycles <= EPSILON) {
            return;
        }

        double safeIndependent = nonNegative(
                independentExecutionTimeSeconds
        );
        double safeContended = nonNegative(
                contendedCompletionTimeSeconds
        );
        double safeDemandRatio = nonNegative(demandRatio);

        localTaskCount++;
        localCpuCycles += safeCycles;
        maxIndependentLocalExecutionTimeSeconds = Math.max(
                maxIndependentLocalExecutionTimeSeconds,
                safeIndependent
        );
        maxLocalExecutionTimeSeconds = Math.max(
                maxLocalExecutionTimeSeconds,
                safeContended
        );
        maxLocalDemandRatio = Math.max(
                maxLocalDemandRatio,
                safeDemandRatio
        );
        maxContentionDelaySeconds = Math.max(
                maxContentionDelaySeconds,
                Math.max(0.0, safeContended - safeIndependent)
        );

        if (!deadlineRespected) {
            deadlineViolationCount++;
        }
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public double getLocalCpu() {
        return localCpu;
    }

    public int getLocalTaskCount() {
        return localTaskCount;
    }

    public int getDeadlineViolationCount() {
        return deadlineViolationCount;
    }

    public double getLocalCpuCycles() {
        return localCpuCycles;
    }

    /**
     * Restituisce il massimo tempo del ramo locale dopo la contesa.
     */
    public double getMaxLocalExecutionTimeSeconds() {
        return maxLocalExecutionTimeSeconds;
    }

    /**
     * Restituisce il massimo tempo che si avrebbe valutando i task isolati.
     */
    public double getMaxIndependentLocalExecutionTimeSeconds() {
        return maxIndependentLocalExecutionTimeSeconds;
    }

    public double getMaxLocalDemandRatio() {
        return maxLocalDemandRatio;
    }

    public double getMaxContentionDelaySeconds() {
        return maxContentionDelaySeconds;
    }

    /**
     * Overflow locale coerente con le deadline EDF.
     */
    public double getCpuOverflowRatio() {
        return Math.max(0.0, maxLocalDemandRatio - 1.0);
    }

    public boolean hasCpuViolation() {
        return getCpuOverflowRatio() > EPSILON;
    }

    public boolean hasDeadlineViolations() {
        return deadlineViolationCount > 0;
    }

    public boolean hasContention() {
        return localTaskCount > 1
                && maxContentionDelaySeconds > EPSILON;
    }

    public boolean hasLocalWorkload() {
        return localCpuCycles > EPSILON;
    }

    private double nonNegative(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }
        return value;
    }
}
