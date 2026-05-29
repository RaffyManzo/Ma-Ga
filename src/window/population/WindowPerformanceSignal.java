package window.population;

import ga.core.MaGaResult;
import ga.fitness.breakdown.EvaluationBreakdown;

/**
 * Segnale sintetico sulla qualità della finestra precedente.
 */
public enum WindowPerformanceSignal {
    UNKNOWN,
    GOOD,
    WARNING,
    BAD;

    public static WindowPerformanceSignal from(MaGaResult result) {
        if (result == null || result.getBestEvaluation() == null) {
            return UNKNOWN;
        }

        EvaluationBreakdown evaluation = result.getBestEvaluation();

        double resourcePenalty = safe(evaluation.getResourcePenalty());
        double mobilityPenalty = safe(evaluation.getMobilityPenalty());
        double fitness = safe(result.getFinalBestFitness());

        if (resourcePenalty >= 100.0 || mobilityPenalty >= 10.0 || fitness >= 50.0) {
            return BAD;
        }

        if (resourcePenalty >= 5.0 || mobilityPenalty >= 1.0 || fitness >= 5.0) {
            return WARNING;
        }

        return GOOD;
    }

    public boolean isGood() {
        return this == GOOD;
    }

    public boolean isBadOrWarning() {
        return this == WARNING || this == BAD;
    }

    private static double safe(double value) {
        if (!Double.isFinite(value)) {
            return Double.POSITIVE_INFINITY;
        }
        return value;
    }
}
