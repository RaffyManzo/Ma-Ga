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

/** Report diagnostico dedicato alla mobilità e alle metriche gateway-aware. */
public final class MobilityDiagnosticPrinter {
    private static final int DEFAULT_TOP_RISK_LIMIT = 10;
    private final MaGaConfig config;
    private final PrintStream out;
    private final int topRiskLimit;

    public MobilityDiagnosticPrinter(MaGaConfig config, PrintStream out) {
        this(config, out, DEFAULT_TOP_RISK_LIMIT);
    }

    public MobilityDiagnosticPrinter(MaGaConfig config, PrintStream out, int topRiskLimit) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.out = Objects.requireNonNull(out, "out must not be null.");
        if (topRiskLimit <= 0) { throw new IllegalArgumentException("topRiskLimit must be > 0."); }
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
        title("MOBILITY CONFIGURATION SUMMARY");
        printf("coverageRiskWeight: %.6f%n", penalties.getCoverageRiskWeight());
        printf("linkInstabilityWeight: %.6f%n", penalties.getLinkInstabilityWeight());
        printf("handoverRiskWeight: %.6f%n", penalties.getHandoverRiskWeight());
        out.println();
        printf("epsilonSpeed: %.6f m/s%n", mobility.getEpsilonSpeedMetersPerSecond());
        printf("v2vCommunicationRadius: %.6f m%n", mobility.getV2vCommunicationRadiusMeters());
        printf("localCoverageTime: %.6f s%n", mobility.getLocalCoverageTimeSeconds());
        printf("maxCoverageTime: %.6f s%n", mobility.getMaxCoverageTimeSeconds());
        out.println("cloudCoverageTime: derived from active access gateway");
        out.println();
    }

    private void printPenaltySummaryByWindow(TemporalWindowResult result) {
        title("MOBILITY PENALTY SUMMARY BY WINDOW");
        out.println("idx | snapshot | remote | Pmob | normalizedPmob | avgCoverage | minCoverage | avgPhiCov | maxPhiCov | avgPhiLink | maxPhiLink | avgPhiHo | maxPhiHo | gatewayAwareCloud | placeholderCloud");
        for (TemporalStepResult step : result.getSteps()) {
            List<GeneEvaluationBreakdown> remote = remoteGenes(step);
            Stats stats = Stats.from(remote);
            printf("%d | %s | %d | %.6f | %.6f | %s | %s | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %d | %d%n",
                    step.getWindowIndex(), step.getSnapshot().getSnapshotId(), remote.size(),
                    step.getMaGaResult().getBestEvaluation().getMobilityPenalty(),
                    step.getMaGaResult().getBestEvaluation().getNormalizedMobilityPenalty(),
                    n(stats.averageCoverage), n(stats.minimumCoverage), stats.averageCovRisk, stats.maxCovRisk,
                    stats.averageLink, stats.maxLink, stats.averageHo, stats.maxHo,
                    stats.gatewayAwareCloud, stats.placeholderCloud);
        }
        out.println();
    }

    private void printSummaryByNodeType(TemporalWindowResult result) {
        title("MOBILITY SUMMARY BY NODE TYPE");
        out.println("idx | type | tasks | avgDistance | avgRadius | avgCoverage | avgPhiLink | avgPhiHo | totalPenalty | model");
        for (TemporalStepResult step : result.getSteps()) {
            Map<NodeType, List<GeneEvaluationBreakdown>> byType = new EnumMap<>(NodeType.class);
            for (NodeType type : NodeType.values()) { byType.put(type, new ArrayList<>()); }
            for (GeneEvaluationBreakdown gene : genes(step)) { byType.get(gene.getNodeType()).add(gene); }
            for (NodeType type : NodeType.values()) {
                List<GeneEvaluationBreakdown> values = byType.get(type);
                if (values.isEmpty()) { continue; }
                Stats stats = Stats.from(values);
                printf("%d | %s | %d | %s | %s | %s | %.6f | %.6f | %.6f | %s%n",
                        step.getWindowIndex(), type, values.size(), n(stats.averageDistance), n(stats.averageRadius),
                        n(stats.averageCoverage), stats.averageLink, stats.averageHo, stats.totalPenalty, modelSummary(values));
            }
        }
        out.println();
    }

    private void printTopMobilityRiskDecisions(TemporalWindowResult result) {
        title("TOP MOBILITY-RISK DECISIONS BY WINDOW");
        for (TemporalStepResult step : result.getSteps()) {
            List<GeneEvaluationBreakdown> ranked = remoteGenes(step);
            ranked.sort(Comparator.comparingDouble((GeneEvaluationBreakdown gene) -> gene.getMobilityBreakdown().getTotalMobilityPenalty()).reversed());
            out.printf(Locale.ITALY, "Window %d | snapshot=%s | remote=%d%n", step.getWindowIndex(), step.getSnapshot().getSnapshotId(), ranked.size());
            if (ranked.isEmpty()) { out.println("- no remote mobility-aware decisions"); out.println(); continue; }
            out.println("task | source | candidate | type | p | gateway | distance | radius | completion | coverage | phiCov | phiLink | phiHo | Pmob | model");
            for (int i = 0; i < Math.min(topRiskLimit, ranked.size()); i++) {
                GeneEvaluationBreakdown gene = ranked.get(i);
                MobilityPenaltyBreakdown mobility = gene.getMobilityBreakdown();
                MobilityLinkMetrics link = mobility.getLinkMetrics();
                printf("%s | %s | %s | %s | %.6f | %s | %s | %s | %.6f s | %.6f s | %.6f | %.6f | %.6f | %.6f | %s%n",
                        gene.getTaskId(), gene.getSourceVehicleId(), gene.getSelectedCandidateId(), gene.getNodeType(), gene.getOffloadingRatio(),
                        text(link.getReferenceAccessGatewayId()), n(link.getDistanceMeters()), n(link.getCoverageRadiusMeters()),
                        gene.getCompletionTimeSeconds(), link.getCoverageTimeSeconds(), mobility.getCoverageRisk(), mobility.getLinkInstability(),
                        mobility.getHandoverRisk(), mobility.getTotalMobilityPenalty(), link.getModelMode());
            }
            out.println();
        }
    }

    private void printModelAssumptions() {
        MobilityConfig mobility = config.getMobilityConfig();
        title("MOBILITY MODEL ASSUMPTIONS");
        printf("- LOCAL decisions use a conventional coverage time of %.6f s.%n", mobility.getLocalCoverageTimeSeconds());
        out.println("- CLOUD decisions derive coverage and phi_link from the active radio access gateway.");
        out.println("- CLOUD_STABLE_PLACEHOLDER is not used in STRICT_GATEWAY mode.");
        out.println("- EDGE coverage uses current Euclidean distance, node radius and source speed.");
        out.println("- V2V coverage uses Euclidean distance and scalar relative speed abs(v_source - v_target).");
        out.println("- V2V heading and trajectory vectors are not modeled yet.");
        printf("- Coverage estimates are clamped to %.6f s.%n", mobility.getMaxCoverageTimeSeconds());
        out.println();
    }

    private List<GeneEvaluationBreakdown> genes(TemporalStepResult step) { return step.getMaGaResult().getBestEvaluation().getGeneBreakdowns(); }
    private List<GeneEvaluationBreakdown> remoteGenes(TemporalStepResult step) {
        List<GeneEvaluationBreakdown> result = new ArrayList<>();
        for (GeneEvaluationBreakdown gene : genes(step)) { if (gene.getNodeType() != NodeType.LOCAL) { result.add(gene); } }
        return result;
    }
    private String modelSummary(List<GeneEvaluationBreakdown> genes) {
        List<String> modes = new ArrayList<>();
        for (GeneEvaluationBreakdown gene : genes) {
            String mode = gene.getMobilityBreakdown().getLinkMetrics().getModelMode().name();
            if (!modes.contains(mode)) { modes.add(mode); }
        }
        return String.join(",", modes);
    }
    private String n(double value) { return Double.isFinite(value) ? String.format(Locale.ITALY, "%.6f", value) : "-"; }
    private String text(String value) { return value == null || value.isBlank() ? "-" : value; }
    private void printf(String format, Object... values) { out.printf(Locale.ITALY, format, values); }
    private void title(String value) { out.println("------------------------------------------------------------"); out.println(value); out.println("------------------------------------------------------------"); }

    private static final class Stats {
        int count, gatewayAwareCloud, placeholderCloud, distCount, radiusCount;
        double totalPenalty, sumCoverage, minimumCoverage = Double.POSITIVE_INFINITY, sumCovRisk, maxCovRisk, sumLink, maxLink, sumHo, maxHo, sumDistance, sumRadius;
        double averageCoverage = Double.NaN, averageCovRisk, averageLink, averageHo, averageDistance = Double.NaN, averageRadius = Double.NaN;
        static Stats from(List<GeneEvaluationBreakdown> genes) {
            Stats s = new Stats();
            for (GeneEvaluationBreakdown gene : genes) {
                MobilityPenaltyBreakdown m = gene.getMobilityBreakdown(); MobilityLinkMetrics l = m.getLinkMetrics(); s.count++; s.totalPenalty += m.getTotalMobilityPenalty();
                s.sumCoverage += l.getCoverageTimeSeconds(); s.minimumCoverage = Math.min(s.minimumCoverage, l.getCoverageTimeSeconds());
                s.sumCovRisk += m.getCoverageRisk(); s.maxCovRisk = Math.max(s.maxCovRisk, m.getCoverageRisk()); s.sumLink += m.getLinkInstability(); s.maxLink = Math.max(s.maxLink, m.getLinkInstability()); s.sumHo += m.getHandoverRisk(); s.maxHo = Math.max(s.maxHo, m.getHandoverRisk());
                if (l.isCloudGatewayAware()) { s.gatewayAwareCloud++; } if (l.isCloudStablePlaceholder()) { s.placeholderCloud++; }
                if (Double.isFinite(l.getDistanceMeters())) { s.sumDistance += l.getDistanceMeters(); s.distCount++; }
                if (Double.isFinite(l.getCoverageRadiusMeters())) { s.sumRadius += l.getCoverageRadiusMeters(); s.radiusCount++; }
            }
            if (s.count > 0) { s.averageCoverage = s.sumCoverage / s.count; s.averageCovRisk = s.sumCovRisk / s.count; s.averageLink = s.sumLink / s.count; s.averageHo = s.sumHo / s.count; } else { s.minimumCoverage = Double.NaN; }
            if (s.distCount > 0) { s.averageDistance = s.sumDistance / s.distCount; } if (s.radiusCount > 0) { s.averageRadius = s.sumRadius / s.radiusCount; }
            return s;
        }
    }
}
