package config.fitness;

import ga.fitness.FitnessEvaluator;

/**
 * Rappresenta i pesi della funzione di fitness del MA-GA.
 *
 * Formalizzazione:
 *
 * J(C) = wT * T(C) + wL * L(C) + wM * Pmob(C) + wR * Pres(C)
 *
 * Dove:
 * - wT pesa il tempo complessivo di completamento T(C);
 * - wL pesa la latenza comunicativa L(C);
 * - wM pesa la penalità di mobilità Pmob(C);
 * - wR pesa la penalità per violazione delle risorse Pres(C).
 *
 * Serve solo a configurare il peso relativo dei termini che saranno usati dal FitnessEvaluator.
 */
public final class FitnessWeights {

    private static final double SUM_TOLERANCE = 1.0E-9;

    private final double completionTimeWeight;
    private final double communicationLatencyWeight;
    private final double mobilityPenaltyWeight;
    private final double resourcePenaltyWeight;

    public FitnessWeights(
            double completionTimeWeight,
            double communicationLatencyWeight,
            double mobilityPenaltyWeight,
            double resourcePenaltyWeight
    ) {
        validateFinite("completionTimeWeight", completionTimeWeight);
        validateFinite("communicationLatencyWeight", communicationLatencyWeight);
        validateFinite("mobilityPenaltyWeight", mobilityPenaltyWeight);
        validateFinite("resourcePenaltyWeight", resourcePenaltyWeight);

        validateNonNegative("completionTimeWeight", completionTimeWeight);
        validateNonNegative("communicationLatencyWeight", communicationLatencyWeight);
        validateNonNegative("mobilityPenaltyWeight", mobilityPenaltyWeight);
        validateNonNegative("resourcePenaltyWeight", resourcePenaltyWeight);

        double sum =
                completionTimeWeight
                        + communicationLatencyWeight
                        + mobilityPenaltyWeight
                        + resourcePenaltyWeight;

        if (Math.abs(sum - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "The fitness weights must sum to 1.0. Current sum: " + sum
            );
        }

        this.completionTimeWeight = completionTimeWeight;
        this.communicationLatencyWeight = communicationLatencyWeight;
        this.mobilityPenaltyWeight = mobilityPenaltyWeight;
        this.resourcePenaltyWeight = resourcePenaltyWeight;
    }

    /**
     * Configurazione iniziale ragionevole per il primo prototipo.
     *
     * Non è una scelta sperimentalmente validata.
     * Serve solo come punto di partenza per verificare il funzionamento
     * del MA-GA su snapshot statici.
     */
    public static FitnessWeights defaultWeights() {
        return new FitnessWeights(
                0.35, // wT: tempo di completamento
                0.25, // wL: latenza comunicativa
                0.25, // wM: mobilità/copertura
                0.15  // wR: violazione risorse
        );
    }

    /**
     * Factory utile quando si vogliono fornire pesi grezzi e normalizzarli.
     * Da usare consapevolmente: nel costruttore principale, invece, la somma
     * deve già essere pari a 1.0.
     */
    public static FitnessWeights normalized(
            double completionTimeWeight,
            double communicationLatencyWeight,
            double mobilityPenaltyWeight,
            double resourcePenaltyWeight
    ) {
        validateFinite("completionTimeWeight", completionTimeWeight);
        validateFinite("communicationLatencyWeight", communicationLatencyWeight);
        validateFinite("mobilityPenaltyWeight", mobilityPenaltyWeight);
        validateFinite("resourcePenaltyWeight", resourcePenaltyWeight);

        validateNonNegative("completionTimeWeight", completionTimeWeight);
        validateNonNegative("communicationLatencyWeight", communicationLatencyWeight);
        validateNonNegative("mobilityPenaltyWeight", mobilityPenaltyWeight);
        validateNonNegative("resourcePenaltyWeight", resourcePenaltyWeight);

        double sum =
                completionTimeWeight
                        + communicationLatencyWeight
                        + mobilityPenaltyWeight
                        + resourcePenaltyWeight;

        if (sum <= 0.0) {
            throw new IllegalArgumentException("At least one fitness weight must be greater than 0.");
        }

        return new FitnessWeights(
                completionTimeWeight / sum,
                communicationLatencyWeight / sum,
                mobilityPenaltyWeight / sum,
                resourcePenaltyWeight / sum
        );
    }

    public double getCompletionTimeWeight() {
        return completionTimeWeight;
    }

    public double getCommunicationLatencyWeight() {
        return communicationLatencyWeight;
    }

    public double getMobilityPenaltyWeight() {
        return mobilityPenaltyWeight;
    }

    public double getResourcePenaltyWeight() {
        return resourcePenaltyWeight;
    }

    public double getWT() {
        return completionTimeWeight;
    }

    public double getWL() {
        return communicationLatencyWeight;
    }

    public double getWM() {
        return mobilityPenaltyWeight;
    }

    public double getWR() {
        return resourcePenaltyWeight;
    }

    private static void validateFinite(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
    }

    private static void validateNonNegative(String fieldName, double value) {
        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }

    @Override
    public String toString() {
        return "FitnessWeights{" +
                "wT=" + completionTimeWeight +
                ", wL=" + communicationLatencyWeight +
                ", wM=" + mobilityPenaltyWeight +
                ", wR=" + resourcePenaltyWeight +
                '}';
    }
}


