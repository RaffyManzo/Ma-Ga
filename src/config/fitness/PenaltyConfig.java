package config.fitness;

import ga.fitness.FitnessEvaluator;

/**
 * Configura i coefficienti usati per costruire le penalità del MA-GA.
 *
 * La formalizzazione distingue:
 *
 * - Pmob(C): penalità complessiva legata alla mobilità;
 * - Pres(C): penalità complessiva legata alla violazione delle risorse.
 *
 * Questa classe NON calcola Pmob(C) o Pres(C).
 * Serve solo a contenere i coefficienti che saranno usati dal FitnessEvaluator.
 */
public final class PenaltyConfig {

    private final double coverageRiskWeight;
    private final double linkInstabilityWeight;
    private final double handoverRiskWeight;

    private final double bandwidthOveruseWeight;
    private final double cpuOveruseWeight;

    private final double deadlineViolationWeight;

    public PenaltyConfig(
            double coverageRiskWeight,
            double linkInstabilityWeight,
            double handoverRiskWeight,
            double bandwidthOveruseWeight,
            double cpuOveruseWeight,
            double deadlineViolationWeight
    ) {
        validateFiniteAndNonNegative("coverageRiskWeight", coverageRiskWeight);
        validateFiniteAndNonNegative("linkInstabilityWeight", linkInstabilityWeight);
        validateFiniteAndNonNegative("handoverRiskWeight", handoverRiskWeight);
        validateFiniteAndNonNegative("bandwidthOveruseWeight", bandwidthOveruseWeight);
        validateFiniteAndNonNegative("cpuOveruseWeight", cpuOveruseWeight);
        validateFiniteAndNonNegative("deadlineViolationWeight", deadlineViolationWeight);

        this.coverageRiskWeight = coverageRiskWeight;
        this.linkInstabilityWeight = linkInstabilityWeight;
        this.handoverRiskWeight = handoverRiskWeight;
        this.bandwidthOveruseWeight = bandwidthOveruseWeight;
        this.cpuOveruseWeight = cpuOveruseWeight;
        this.deadlineViolationWeight = deadlineViolationWeight;
    }

    /**
     * Configurazione iniziale per il primo prototipo.
     *
     * Le penalità di risorse e deadline sono volutamente più alte perché
     * rappresentano violazioni più gravi rispetto a una semplice scelta
     * mobility-aware poco conveniente.
     */
    public static PenaltyConfig defaultConfig() {
        return new PenaltyConfig(
                1.0,  // rischio di copertura insufficiente
                0.5,  // instabilità del collegamento
                0.5,  // rischio di handover
                10.0, // superamento banda disponibile
                10.0, // superamento CPU disponibile
                20.0  // violazione deadline
        );
    }

    public double getCoverageRiskWeight() {
        return coverageRiskWeight;
    }

    public double getLinkInstabilityWeight() {
        return linkInstabilityWeight;
    }

    public double getHandoverRiskWeight() {
        return handoverRiskWeight;
    }

    public double getBandwidthOveruseWeight() {
        return bandwidthOveruseWeight;
    }

    public double getCpuOveruseWeight() {
        return cpuOveruseWeight;
    }

    public double getDeadlineViolationWeight() {
        return deadlineViolationWeight;
    }

    private static void validateFiniteAndNonNegative(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }

    @Override
    public String toString() {
        return "PenaltyConfig{" +
                "coverageRiskWeight=" + coverageRiskWeight +
                ", linkInstabilityWeight=" + linkInstabilityWeight +
                ", handoverRiskWeight=" + handoverRiskWeight +
                ", bandwidthOveruseWeight=" + bandwidthOveruseWeight +
                ", cpuOveruseWeight=" + cpuOveruseWeight +
                ", deadlineViolationWeight=" + deadlineViolationWeight +
                '}';
    }
}


