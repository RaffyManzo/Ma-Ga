package config.ga;

/**
 * Risultato diagnostico del dimensionamento dei parametri GA.
 *
 * Serve per capire perché una certa GeneticAlgorithmConfig è stata scelta.
 */
public final class GaParameterScalingResult {

    private final GaParameterScalingMode mode;

    private final int vehicleCount;
    private final int activeTaskCount;
    private final int candidateCount;
    private final double averageCandidatesPerTask;

    private final GeneticAlgorithmConfig baseConfig;
    private final GeneticAlgorithmConfig scaledConfig;

    private final String reason;

    public GaParameterScalingResult(
            GaParameterScalingMode mode,
            int vehicleCount,
            int activeTaskCount,
            int candidateCount,
            double averageCandidatesPerTask,
            GeneticAlgorithmConfig baseConfig,
            GeneticAlgorithmConfig scaledConfig,
            String reason
    ) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null.");
        }

        if (baseConfig == null) {
            throw new IllegalArgumentException("baseConfig must not be null.");
        }

        if (scaledConfig == null) {
            throw new IllegalArgumentException("scaledConfig must not be null.");
        }

        this.mode = mode;
        this.vehicleCount = vehicleCount;
        this.activeTaskCount = activeTaskCount;
        this.candidateCount = candidateCount;
        this.averageCandidatesPerTask = averageCandidatesPerTask;
        this.baseConfig = baseConfig;
        this.scaledConfig = scaledConfig;
        this.reason = reason == null ? "" : reason;
    }

    public GaParameterScalingMode getMode() {
        return mode;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public int getActiveTaskCount() {
        return activeTaskCount;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public double getAverageCandidatesPerTask() {
        return averageCandidatesPerTask;
    }

    public GeneticAlgorithmConfig getBaseConfig() {
        return baseConfig;
    }

    public GeneticAlgorithmConfig getScaledConfig() {
        return scaledConfig;
    }

    public String getReason() {
        return reason;
    }

    public boolean isAdaptive() {
        return mode == GaParameterScalingMode.ADAPTIVE;
    }

    public boolean hasChangedPopulationSize() {
        return baseConfig.getPopulationSize()
                != scaledConfig.getPopulationSize();
    }

    public boolean hasChangedMaxGenerations() {
        return baseConfig.getMaxGenerations()
                != scaledConfig.getMaxGenerations();
    }

    public boolean hasChanged() {
        return hasChangedPopulationSize()
                || hasChangedMaxGenerations()
                || baseConfig.getElitismCount() != scaledConfig.getElitismCount()
                || baseConfig.getStallGenerations()
                != scaledConfig.getStallGenerations();
    }

    @Override
    public String toString() {
        return "GaParameterScalingResult{"
                + "mode=" + mode
                + ", vehicleCount=" + vehicleCount
                + ", activeTaskCount=" + activeTaskCount
                + ", candidateCount=" + candidateCount
                + ", averageCandidatesPerTask=" + averageCandidatesPerTask
                + ", baseConfig=" + baseConfig
                + ", scaledConfig=" + scaledConfig
                + ", reason='" + reason + '\''
                + '}';
    }
}