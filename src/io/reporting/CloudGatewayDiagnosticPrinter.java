package io.reporting;

import ga.fitness.breakdown.GeneEvaluationBreakdown;
import model.mobility.MobilityLinkMetrics;
import model.node.NodeType;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.SystemSnapshot;
import window.prefilter.CandidateFilteringResult;
import window.prefilter.CandidateRejectionReason;
import window.source.SystemStateObservation;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Report dedicato ai gateway radio e alle decisioni CLOUD gateway-aware. */
public final class CloudGatewayDiagnosticPrinter {
    private final PrintStream out;

    public CloudGatewayDiagnosticPrinter(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    public void print(TemporalWindowResult result, List<CandidateFilteringResult> filteringResults) {
        Objects.requireNonNull(result, "result must not be null.");
        printConfiguration(result);
        printSummary(result, filteringResults == null ? List.of() : filteringResults);
        printCloudDecisionDetails(result);
        printTransitions(result);
    }

    private void printConfiguration(TemporalWindowResult result) {
        title("CLOUD GATEWAY CONFIGURATION SUMMARY");
        out.println("mode: STRICT_GATEWAY");
        out.println("legacyPlaceholderEnabled: false");
        if (result.getSteps().isEmpty()) {
            out.println("configuredGatewayCountAcrossRun: 0");
            out.println("firstSnapshotAccessLinkCount: 0");
            out.println("maximumAccessLinkCountAcrossWindows: 0");
            out.println("maximumActiveAccessLinkCountAcrossWindows: 0");
            out.println("windowsWithActiveAccessLinks: 0");
            out.println("windowsWithoutActiveAccessLinks: 0");
            out.println();
            return;
        }
        Set<String> gatewayIds = new HashSet<>();
        int maxAccessLinks = 0;
        int maxActiveLinks = 0;
        int windowsWithActiveLinks = 0;
        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot snapshot = observed(step);
            snapshot.getAccessGateways().forEach(gateway -> gatewayIds.add(gateway.getGatewayId()));
            int activeLinks = countActive(snapshot);
            maxAccessLinks = Math.max(maxAccessLinks, snapshot.getAccessLinks().size());
            maxActiveLinks = Math.max(maxActiveLinks, activeLinks);
            if (activeLinks > 0) {
                windowsWithActiveLinks++;
            }
        }

        SystemSnapshot firstSnapshot = observed(result.getSteps().get(0));
        out.println("configuredGatewayCountAcrossRun: " + gatewayIds.size());
        out.println("firstSnapshotAccessLinkCount: " + firstSnapshot.getAccessLinks().size());
        out.println("maximumAccessLinkCountAcrossWindows: " + maxAccessLinks);
        out.println("maximumActiveAccessLinkCountAcrossWindows: " + maxActiveLinks);
        out.println("windowsWithActiveAccessLinks: " + windowsWithActiveLinks);
        out.println("windowsWithoutActiveAccessLinks: "
                + (result.getSteps().size() - windowsWithActiveLinks));
        out.println();
    }

    private void printSummary(TemporalWindowResult result, List<CandidateFilteringResult> filteringResults) {
        title("CLOUD GATEWAY SUMMARY BY WINDOW");
        out.println("idx | snapshot | vehicles | gateways | activeLinks | cloudCandidates | cloudDecisions | gatewayAwareCloud | placeholderCloud | filteredCloud | avgPhiLink | maxPhiLink | avgCoverage | minCoverage");
        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot snapshot = observed(step);
            List<GeneEvaluationBreakdown> cloudGenes = cloudGenes(step);
            double sumPhi = 0.0, maxPhi = 0.0, sumCoverage = 0.0, minCoverage = Double.POSITIVE_INFINITY;
            int gatewayAware = 0, placeholder = 0;
            for (GeneEvaluationBreakdown gene : cloudGenes) {
                MobilityLinkMetrics metrics = gene.getMobilityBreakdown().getLinkMetrics();
                double phi = gene.getMobilityBreakdown().getLinkInstability();
                sumPhi += phi; maxPhi = Math.max(maxPhi, phi);
                sumCoverage += metrics.getCoverageTimeSeconds();
                minCoverage = Math.min(minCoverage, metrics.getCoverageTimeSeconds());
                if (metrics.isCloudGatewayAware()) { gatewayAware++; }
                if (metrics.isCloudStablePlaceholder()) { placeholder++; }
            }
            int filteredCloud = step.getWindowIndex() < filteringResults.size()
                    ? filteringResults.get(step.getWindowIndex()).getStats().getCountForReason(CandidateRejectionReason.ACCESS_LINK_UNAVAILABLE)
                    : 0;
            printf("%d | %s | %d | %d | %d | %d | %d | %d | %d | %d | %s | %s | %s | %s%n",
                    step.getWindowIndex(),
                    snapshot.getSnapshotId(),
                    snapshot.getVehicles().size(),
                    snapshot.getAccessGateways().size(),
                    countActive(snapshot),
                    countCloudCandidates(snapshot),
                    cloudGenes.size(),
                    gatewayAware,
                    placeholder,
                    filteredCloud,
                    numberOrDash(cloudGenes.isEmpty() ? Double.NaN : sumPhi / cloudGenes.size()),
                    numberOrDash(cloudGenes.isEmpty() ? Double.NaN : maxPhi),
                    numberOrDash(cloudGenes.isEmpty() ? Double.NaN : sumCoverage / cloudGenes.size()),
                    numberOrDash(cloudGenes.isEmpty() ? Double.NaN : minCoverage)
            );
        }
        out.println();
    }

    private void printCloudDecisionDetails(TemporalWindowResult result) {
        title("CLOUD ACCESS-RISK DECISIONS BY WINDOW");
        out.println("idx | task | vehicle | cloudCandidate | gateway | distance | radius | coverage | phiLink | phiHo | model");
        for (TemporalStepResult step : result.getSteps()) {
            for (GeneEvaluationBreakdown gene : cloudGenes(step)) {
                MobilityLinkMetrics link = gene.getMobilityBreakdown().getLinkMetrics();
                printf("%d | %s | %s | %s | %s | %s | %s | %.6f s | %.6f | %.6f | %s%n",
                        step.getWindowIndex(), gene.getTaskId(), gene.getSourceVehicleId(), gene.getSelectedCandidateId(),
                        textOrDash(link.getReferenceAccessGatewayId()), numberOrDash(link.getDistanceMeters()),
                        numberOrDash(link.getCoverageRadiusMeters()), link.getCoverageTimeSeconds(),
                        gene.getMobilityBreakdown().getLinkInstability(), gene.getMobilityBreakdown().getHandoverRisk(), link.getModelMode());
            }
        }
        out.println();
    }

    private void printTransitions(TemporalWindowResult result) {
        title("ACCESS LINK TRANSITIONS");
        out.println("idx | vehicle | previousGateway | currentGateway | transition");
        Map<String, String> previous = null;
        for (TemporalStepResult step : result.getSteps()) {
            Map<String, String> current = activeGatewayByVehicle(observed(step));
            if (previous != null) {
                Set<String> vehicles = new HashSet<>();
                vehicles.addAll(previous.keySet());
                vehicles.addAll(current.keySet());
                List<String> orderedVehicles = new ArrayList<>(vehicles);
                orderedVehicles.sort(String::compareTo);
                for (String vehicleId : orderedVehicles) {
                    String oldGateway = previous.get(vehicleId);
                    String newGateway = current.get(vehicleId);
                    GatewayTransition transition = transition(oldGateway, newGateway);
                    if (transition != GatewayTransition.UNCHANGED) {
                        out.println(step.getWindowIndex()
                                + " | " + vehicleId
                                + " | " + textOrDash(oldGateway)
                                + " | " + textOrDash(newGateway)
                                + " | " + transition);
                    }
                }
            }
            previous = current;
        }
        out.println();
    }

    private SystemSnapshot observed(TemporalStepResult step) {
        return step.getSystemStateObservation()
                .map(SystemStateObservation::getObservedSnapshot)
                .orElse(step.getSnapshot());
    }
    private int countActive(SystemSnapshot snapshot) {
        int count = 0; for (AccessLinkSnapshot link : snapshot.getAccessLinks()) { if (link.isActive()) { count++; } } return count;
    }
    private int countCloudCandidates(SystemSnapshot snapshot) {
        int count = 0; for (var c : snapshot.getCandidateNodes()) { if (c.getType() == NodeType.CLOUD) { count++; } } return count;
    }
    private List<GeneEvaluationBreakdown> cloudGenes(TemporalStepResult step) {
        List<GeneEvaluationBreakdown> result = new ArrayList<>();
        for (GeneEvaluationBreakdown gene : step.getMaGaResult().getBestEvaluation().getGeneBreakdowns()) {
            if (gene.getNodeType() == NodeType.CLOUD) { result.add(gene); }
        }
        return result;
    }
    private Map<String, String> activeGatewayByVehicle(SystemSnapshot snapshot) {
        Map<String, String> result = new HashMap<>();
        for (AccessLinkSnapshot link : snapshot.getAccessLinks()) { if (link.isActive()) { result.put(link.getVehicleId(), link.getGatewayId()); } }
        return result;
    }
    private GatewayTransition transition(String previousGateway, String currentGateway) {
        if (previousGateway == null && currentGateway == null) { return GatewayTransition.UNCHANGED; }
        if (previousGateway == null) { return GatewayTransition.COVERAGE_GAIN; }
        if (currentGateway == null) { return GatewayTransition.COVERAGE_LOSS; }
        if (previousGateway.equals(currentGateway)) { return GatewayTransition.UNCHANGED; }
        return GatewayTransition.HANDOVER;
    }
    private String numberOrDash(double value) { return Double.isFinite(value) ? String.format(Locale.ITALY, "%.6f", value) : "-"; }
    private String textOrDash(String value) { return value == null || value.isBlank() ? "-" : value; }
    private void printf(String format, Object... values) { out.printf(Locale.ITALY, format, values); }
    private void title(String value) { out.println("------------------------------------------------------------"); out.println(value); out.println("------------------------------------------------------------"); }
    private enum GatewayTransition { UNCHANGED, COVERAGE_GAIN, COVERAGE_LOSS, HANDOVER }
}
