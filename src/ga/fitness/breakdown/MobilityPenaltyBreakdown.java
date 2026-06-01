package ga.fitness.breakdown;

import model.mobility.MobilityLinkMetrics;
import model.node.NodeType;

import java.util.Objects;

/**
 * Scomposizione della penalità mobility-aware associata a un gene.
 *
 * <p>La classe conserva sia i valori grezzi sia i contributi pesati usati
 * dalla fitness. Non modifica il calcolo: rende soltanto il risultato
 * osservabile nei report diagnostici.</p>
 */
public final class MobilityPenaltyBreakdown {

    private final MobilityLinkMetrics linkMetrics;
    private final double coverageRisk;
    private final double linkInstability;
    private final double handoverRisk;
    private final double weightedCoverageRisk;
    private final double weightedLinkInstability;
    private final double weightedHandoverRisk;
    private final double totalMobilityPenalty;

    public MobilityPenaltyBreakdown(
            MobilityLinkMetrics linkMetrics,
            double coverageRisk,
            double linkInstability,
            double handoverRisk,
            double weightedCoverageRisk,
            double weightedLinkInstability,
            double weightedHandoverRisk
    ) {
        this.linkMetrics = Objects.requireNonNull(linkMetrics, "linkMetrics must not be null.");
        this.coverageRisk = clamp01(coverageRisk);
        this.linkInstability = clamp01(linkInstability);
        this.handoverRisk = clamp01(handoverRisk);
        this.weightedCoverageRisk = finiteOrZero(weightedCoverageRisk);
        this.weightedLinkInstability = finiteOrZero(weightedLinkInstability);
        this.weightedHandoverRisk = finiteOrZero(weightedHandoverRisk);
        this.totalMobilityPenalty = this.weightedCoverageRisk
                + this.weightedLinkInstability
                + this.weightedHandoverRisk;
    }

    public static MobilityPenaltyBreakdown zero(MobilityLinkMetrics linkMetrics) {
        return new MobilityPenaltyBreakdown(
                linkMetrics,
                0.0,
                linkMetrics.getLinkInstability(),
                0.0,
                0.0,
                0.0,
                0.0
        );
    }

    /**
     * Adapter conservativo per eventuali chiamanti legacy che non producono
     * ancora il breakdown dettagliato.
     */
    public static MobilityPenaltyBreakdown legacy(
            NodeType nodeType,
            double coverageTimeSeconds,
            double totalMobilityPenalty
    ) {
        return new MobilityPenaltyBreakdown(
                MobilityLinkMetrics.legacy(nodeType, coverageTimeSeconds),
                0.0,
                0.0,
                0.0,
                Math.max(0.0, totalMobilityPenalty),
                0.0,
                0.0
        );
    }

    public MobilityLinkMetrics getLinkMetrics() {
        return linkMetrics;
    }

    public double getCoverageRisk() {
        return coverageRisk;
    }

    public double getLinkInstability() {
        return linkInstability;
    }

    public double getHandoverRisk() {
        return handoverRisk;
    }

    public double getWeightedCoverageRisk() {
        return weightedCoverageRisk;
    }

    public double getWeightedLinkInstability() {
        return weightedLinkInstability;
    }

    public double getWeightedHandoverRisk() {
        return weightedHandoverRisk;
    }

    public double getTotalMobilityPenalty() {
        return totalMobilityPenalty;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
