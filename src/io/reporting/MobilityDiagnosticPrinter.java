package io.reporting;

import config.MaGaConfig;
import config.fitness.PenaltyConfig;
import config.mobility.MobilityConfig;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.MobilityPenaltyBreakdown;
import model.mobility.MobilityLinkMetrics;
import model.node.NodeType;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Report diagnostico dedicato alla mobilità.
 *
 * <p>La classe non ricalcola la fitness. Legge i breakdown prodotti durante
 * la valutazione del miglior cromosoma di ogni finestra e rende espliciti
 * distanza, copertura, phi_cov, phi_link, phi_ho e contributi pesati.</p>
 */
public final class MobilityDiagnosticPrinter {

    private static final int DEFAULT_TOP_RISK_LIMIT = 10;

    private final MaGaConfig config;
    private final PrintStream out;
    private final int topRiskLimit;

    public MobilityDiagnosticPrinter(MaGaConfig config, PrintStream out) {
        this(config, out, DEFAULT_TOP_RISK_LIMIT);
    }

    public MobilityDiagnosticPrinter(
            MaGaConfig config,
            PrintStream out,
            int topRiskLimit
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.out = Objects.requireNonNull(out, "out must not be null.");
        if (topRiskLimit <= 0) {
            throw new IllegalArgumentException("topRiskLimit must be > 0.");
        }
        this.topRiskLimit = topRiskLimit;
    }

    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");

        printConfiguration();
        printPenaltySummaryByWindow(result);
        printSummaryByNodeType(result);
        printTopMobilityRiskDecisions(result);
        printModelAssumptions();
    }

    private void printConfiguration() {
        MobilityConfig mobility = config.getMobilityConfig();
        PenaltyConfig penalties = config.getPenaltyConfig();

        printSectionTitle("MOBILITY CONFIGURATION SUMMARY");
        printf("coverageRiskWeight: %.6f%n", penalties.getCoverageRiskWeight());
        printf("linkInstabilityWeight: %.6f%n", penalties.getLinkInstabilityWeight());
        printf("handoverRiskWeight: %.6f%n", penalties.getHandoverRiskWeight());
        out.println();
        printf("epsilonSpeed: %.6f m/s%n", mobility.getEpsilonSpeedMetersPerSecond());
        printf("v2vCommunicationRadius: %.6f m%n", mobility.getV2vCommunicationRadiusMeters());
        printf("localCoverageTime: %.6f s%n", mobility.getLocalCoverageTimeSeconds());
        printf("cloudCoverageTime: %.6f s%n", mobility.getCloudCoverageTimeSeconds());
        printf("maxCoverageTime: %.6f s%n", mobility.getMaxCoverageTimeSeconds());
        out.println();
    }

    private void printPenaltySummaryByWindow(TemporalWindowResult result) {
        printSectionTitle("MOBILITY PENALTY SUMMARY BY WINDOW");
        out.println("idx | snapshot | remote | Pmob | normalizedPmob | avgCoverage | minCoverage | avgPhiCov | maxPhiCov | avgPhiLink | maxPhiLink | avgPhiHo | maxPhiHo | cloudPlaceholder");

        for (TemporalStepResult step : result.getSteps()) {
            List<GeneEvaluationBreakdown> remoteGenes = remoteGenes(step);
            Stats stats = Stats.from(remoteGenes);

            printf(
                    "%d | %s | %d | %.6f | %.6f | %s | %s | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %d%n",
                    step.getWindowIndex(),
                    step.getSnapshot().getSnapshotId(),
                    remoteGenes.size(),
                    step.getMaGaResult().getBestEvaluation().getMobilityPenalty(),
                    step.getMaGaResult().getBestEvaluation().getNormalizedMobilityPenalty(),
                    numberOrDash(stats.averageCoverageTime),
                    numberOrDash(stats.minimumCoverageTime),
                    stats.averageCoverageRisk,
                    stats.maximumCoverageRisk,
                    stats.averageLinkInstability,
                    stats.maximumLinkInstability,
                    stats.averageHandoverRisk,
                    stats.maximumHandoverRisk,
                    stats.cloudPlaceholderCount
            );
        }
        out.println();
    }

    private void printSummaryByNodeType(TemporalWindowResult result) {
        printSectionTitle("MOBILITY SUMMARY BY NODE TYPE");
        out.println("idx | type | tasks | avgDistance | avgRadius | avgSourceSpeed | avgRelativeSpeed | avgCoverage | avgPhiCov | avgPhiLink | avgPhiHo | totalPenalty | model");

        for (TemporalStepResult step : result.getSteps()) {
            Map<NodeType, List<GeneEvaluationBreakdown>> byType = new EnumMap<>(NodeType.class);
            for (NodeType type : NodeType.values()) {
                byType.put(type, new ArrayList<>());
            }
            for (GeneEvaluationBreakdown gene : genes(step)) {
                byType.get(gene.getNodeType()).add(gene);
            }

            for (NodeType type : NodeType.values()) {
                List<GeneEvaluationBreakdown> entries = byType.get(type);
                if (entries.isEmpty()) {
                    continue;
                }
                Stats stats = Stats.from(entries);
                printf(
                        "%d | %s | %d | %s | %s | %s | %s | %s | %.6f | %.6f | %.6f | %.6f | %s%n",
                        step.getWindowIndex(),
                        type,
                        entries.size(),
                        numberOrDash(stats.averageDistance),
                        numberOrDash(stats.averageRadius),
                        numberOrDash(stats.averageSourceSpeed),
                        numberOrDash(stats.averageRelativeSpeed),
                        numberOrDash(stats.averageCoverageTime),
                        stats.averageCoverageRisk,
                        stats.averageLinkInstability,
                        stats.averageHandoverRisk,
                        stats.totalMobilityPenalty,
                        modelSummary(entries)
                );
            }
        }
        out.println();
    }

    private void printTopMobilityRiskDecisions(TemporalWindowResult result) {
        printSectionTitle("TOP MOBILITY-RISK DECISIONS BY WINDOW");

        for (TemporalStepResult step : result.getSteps()) {
            List<GeneEvaluationBreakdown> ranked = remoteGenes(step);
            ranked.sort(
                    Comparator.comparingDouble(
                            (GeneEvaluationBreakdown gene) -> gene
                                    .getMobilityBreakdown()
                                    .getTotalMobilityPenalty()
                    ).reversed()
            );

            out.printf(
                    Locale.ITALY,
                    "Window %d | snapshot=%s | remote=%d%n",
                    step.getWindowIndex(),
                    step.getSnapshot().getSnapshotId(),
                    ranked.size()
            );

            if (ranked.isEmpty()) {
                out.println("- no remote mobility-aware decisions");
                out.println();
                continue;
            }

            out.println("task | source | candidate | type | p | distance | radius | sourceSpeed | relativeSpeed | completion | coverage | phiCov | phiLink | phiHo | weightedCov | weightedLink | weightedHo | Pmob | model");

            for (int index = 0; index < Math.min(topRiskLimit, ranked.size()); index++) {
                GeneEvaluationBreakdown gene = ranked.get(index);
                MobilityPenaltyBreakdown mobility = gene.getMobilityBreakdown();
                MobilityLinkMetrics link = mobility.getLinkMetrics();

                printf(
                        "%s | %s | %s | %s | %.6f | %s | %s | %s | %s | %.6f s | %.6f s | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %s%n",
                        gene.getTaskId(),
                        gene.getSourceVehicleId(),
                        gene.getSelectedCandidateId(),
                        gene.getNodeType(),
                        gene.getOffloadingRatio(),
                        metersOrDash(link.getDistanceMeters()),
                        metersOrDash(link.getCoverageRadiusMeters()),
                        speedOrDash(link.getSourceSpeedMetersPerSecond()),
                        speedOrDash(link.getRelativeSpeedMetersPerSecond()),
                        gene.getCompletionTimeSeconds(),
                        link.getCoverageTimeSeconds(),
                        mobility.getCoverageRisk(),
                        mobility.getLinkInstability(),
                        mobility.getHandoverRisk(),
                        mobility.getWeightedCoverageRisk(),
                        mobility.getWeightedLinkInstability(),
                        mobility.getWeightedHandoverRisk(),
                        mobility.getTotalMobilityPenalty(),
                        link.getModelMode()
                );
            }
            out.println();
        }
    }

    private void printModelAssumptions() {
        MobilityConfig mobility = config.getMobilityConfig();

        printSectionTitle("MOBILITY MODEL ASSUMPTIONS");
        printf("- LOCAL decisions use a conventional coverage time of %.6f s.%n", mobility.getLocalCoverageTimeSeconds());
        printf("- CLOUD decisions use a provisional coverage time of %.6f s.%n", mobility.getCloudCoverageTimeSeconds());
        out.println("- CLOUD link instability is provisionally fixed to 0 because no radio access gateway is modeled yet.");
        out.println("- EDGE coverage uses current Euclidean distance, node radius and source speed.");
        out.println("- V2V coverage uses Euclidean distance and scalar relative speed abs(v_source - v_target).");
        out.println("- V2V heading and trajectory vectors are not modeled yet.");
        printf("- Coverage estimates are clamped to %.6f s.%n", mobility.getMaxCoverageTimeSeconds());
        out.println();
    }

    private List<GeneEvaluationBreakdown> genes(TemporalStepResult step) {
        return step.getMaGaResult().getBestEvaluation().getGeneBreakdowns();
    }

    private List<GeneEvaluationBreakdown> remoteGenes(TemporalStepResult step) {
        List<GeneEvaluationBreakdown> result = new ArrayList<>();
        for (GeneEvaluationBreakdown gene : genes(step)) {
            if (gene.getNodeType() != NodeType.LOCAL) {
                result.add(gene);
            }
        }
        return result;
    }

    private String modelSummary(List<GeneEvaluationBreakdown> genes) {
        List<String> modes = new ArrayList<>();
        for (GeneEvaluationBreakdown gene : genes) {
            String mode = gene.getMobilityBreakdown()
                    .getLinkMetrics()
                    .getModelMode()
                    .name();
            if (!modes.contains(mode)) {
                modes.add(mode);
            }
        }
        return String.join(",", modes);
    }

    private String numberOrDash(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ITALY, "%.6f", value)
                : "-";
    }

    private String metersOrDash(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ITALY, "%.6f m", value)
                : "-";
    }

    private String speedOrDash(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ITALY, "%.6f m/s", value)
                : "-";
    }

    private void printSectionTitle(String title) {
        out.println("------------------------------------------------------------");
        out.println(title);
        out.println("------------------------------------------------------------");
    }

    private void printf(String format, Object... values) {
        out.printf(Locale.ITALY, format, values);
    }

    private static final class Stats {
        private int count;
        private int cloudPlaceholderCount;
        private double totalMobilityPenalty;
        private double totalCoverageTime;
        private double minimumCoverageTime = Double.POSITIVE_INFINITY;
        private double totalCoverageRisk;
        private double maximumCoverageRisk;
        private double totalLinkInstability;
        private double maximumLinkInstability;
        private double totalHandoverRisk;
        private double maximumHandoverRisk;
        private double totalDistance;
        private int distanceCount;
        private double totalRadius;
        private int radiusCount;
        private double totalSourceSpeed;
        private int sourceSpeedCount;
        private double totalRelativeSpeed;
        private int relativeSpeedCount;

        private double averageCoverageTime = Double.NaN;
        private double averageCoverageRisk;
        private double averageLinkInstability;
        private double averageHandoverRisk;
        private double averageDistance = Double.NaN;
        private double averageRadius = Double.NaN;
        private double averageSourceSpeed = Double.NaN;
        private double averageRelativeSpeed = Double.NaN;

        private static Stats from(List<GeneEvaluationBreakdown> genes) {
            Stats stats = new Stats();
            for (GeneEvaluationBreakdown gene : genes) {
                MobilityPenaltyBreakdown mobility = gene.getMobilityBreakdown();
                MobilityLinkMetrics link = mobility.getLinkMetrics();

                stats.count++;
                stats.totalMobilityPenalty += mobility.getTotalMobilityPenalty();
                stats.totalCoverageTime += link.getCoverageTimeSeconds();
                stats.minimumCoverageTime = Math.min(
                        stats.minimumCoverageTime,
                        link.getCoverageTimeSeconds()
                );
                stats.totalCoverageRisk += mobility.getCoverageRisk();
                stats.maximumCoverageRisk = Math.max(
                        stats.maximumCoverageRisk,
                        mobility.getCoverageRisk()
                );
                stats.totalLinkInstability += mobility.getLinkInstability();
                stats.maximumLinkInstability = Math.max(
                        stats.maximumLinkInstability,
                        mobility.getLinkInstability()
                );
                stats.totalHandoverRisk += mobility.getHandoverRisk();
                stats.maximumHandoverRisk = Math.max(
                        stats.maximumHandoverRisk,
                        mobility.getHandoverRisk()
                );

                if (link.isCloudStablePlaceholder()) {
                    stats.cloudPlaceholderCount++;
                }
                if (Double.isFinite(link.getDistanceMeters())) {
                    stats.totalDistance += link.getDistanceMeters();
                    stats.distanceCount++;
                }
                if (Double.isFinite(link.getCoverageRadiusMeters())) {
                    stats.totalRadius += link.getCoverageRadiusMeters();
                    stats.radiusCount++;
                }
                if (Double.isFinite(link.getSourceSpeedMetersPerSecond())) {
                    stats.totalSourceSpeed += link.getSourceSpeedMetersPerSecond();
                    stats.sourceSpeedCount++;
                }
                if (Double.isFinite(link.getRelativeSpeedMetersPerSecond())) {
                    stats.totalRelativeSpeed += link.getRelativeSpeedMetersPerSecond();
                    stats.relativeSpeedCount++;
                }
            }

            if (stats.count > 0) {
                stats.averageCoverageTime = stats.totalCoverageTime / stats.count;
                stats.averageCoverageRisk = stats.totalCoverageRisk / stats.count;
                stats.averageLinkInstability = stats.totalLinkInstability / stats.count;
                stats.averageHandoverRisk = stats.totalHandoverRisk / stats.count;
            } else {
                stats.minimumCoverageTime = Double.NaN;
            }
            if (stats.distanceCount > 0) {
                stats.averageDistance = stats.totalDistance / stats.distanceCount;
            }
            if (stats.radiusCount > 0) {
                stats.averageRadius = stats.totalRadius / stats.radiusCount;
            }
            if (stats.sourceSpeedCount > 0) {
                stats.averageSourceSpeed = stats.totalSourceSpeed / stats.sourceSpeedCount;
            }
            if (stats.relativeSpeedCount > 0) {
                stats.averageRelativeSpeed = stats.totalRelativeSpeed / stats.relativeSpeedCount;
            }
            return stats;
        }
    }
}
