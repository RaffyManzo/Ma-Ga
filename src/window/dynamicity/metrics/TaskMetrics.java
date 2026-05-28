package window.dynamicity.metrics;

import window.dynamicity.math.DynamicityMath;

import java.util.Objects;

/**
 * Stato confrontabile di un task.
 */
public final class TaskMetrics
        implements ComparableMetric<TaskMetrics> {

    private final String sourceVehicleId;
    private final double inputSizeBits;
    private final double outputSizeBits;
    private final double cpuCycles;
    private final double deadlineSeconds;

    /**
     * Costruisce la metrica task.
     *
     * @param sourceVehicleId veicolo sorgente
     * @param inputSizeBits dimensione input
     * @param outputSizeBits dimensione output
     * @param cpuCycles cicli CPU richiesti
     * @param deadlineSeconds deadline
     */
    public TaskMetrics(
            String sourceVehicleId,
            double inputSizeBits,
            double outputSizeBits,
            double cpuCycles,
            double deadlineSeconds
    ) {
        this.sourceVehicleId = sourceVehicleId;
        this.inputSizeBits = inputSizeBits;
        this.outputSizeBits = outputSizeBits;
        this.cpuCycles = cpuCycles;
        this.deadlineSeconds = deadlineSeconds;
    }

    @Override
    public double relativeVariation(TaskMetrics other) {
        double sourceVariation = Objects.equals(
                sourceVehicleId,
                other.sourceVehicleId
        ) ? 0.0 : 1.0;

        double inputVariation = DynamicityMath.relativeDifference(
                inputSizeBits,
                other.inputSizeBits
        );

        double outputVariation = DynamicityMath.relativeDifference(
                outputSizeBits,
                other.outputSizeBits
        );

        double cpuVariation = DynamicityMath.relativeDifference(
                cpuCycles,
                other.cpuCycles
        );

        double deadlineVariation = DynamicityMath.relativeDifference(
                deadlineSeconds,
                other.deadlineSeconds
        );

        return DynamicityMath.clamp01(
                0.20 * sourceVariation
                        + 0.20 * inputVariation
                        + 0.15 * outputVariation
                        + 0.30 * cpuVariation
                        + 0.15 * deadlineVariation
        );
    }
}
