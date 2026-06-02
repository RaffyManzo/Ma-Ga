package io.reporting;

import model.bandwidth.BandwidthPoolResolver;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;
import window.source.SystemStateObservation;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Report gerarchico della banda.
 *
 * <p>La banda viene osservata su due livelli distinti:</p>
 *
 * <ul>
 *     <li>candidateId: capacità del singolo link source-aware;</li>
 *     <li>poolId: capacità condivisa della RSU o del collegamento V2V.</li>
 * </ul>
 */
public final class BandwidthPoolDiagnosticPrinter {
    private static final double SATURATION_THRESHOLD_PERCENT = 95.0;

    private final PrintStream out;
    private final BandwidthPoolResolver resolver;

    public BandwidthPoolDiagnosticPrinter(PrintStream out) {
        this(out, new BandwidthPoolResolver());
    }

    public BandwidthPoolDiagnosticPrinter(
            PrintStream out,
            BandwidthPoolResolver resolver
    ) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
        this.resolver = Objects.requireNonNull(
                resolver,
                "resolver must not be null."
        );
    }

    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");
        printInterpretation();
        printConfiguration(result);
        printHierarchicalSummary(result);
        printLinkSummary(result);
        printLinkDetails(result);
        printPoolSummary(result);
        printPoolDetails(result);
    }

    private void printInterpretation() {
        title("HIERARCHICAL BANDWIDTH INTERPRETATION");
        out.println("- link constraint: sum bandwidth by candidateId <= candidate.availableBandwidth");
        out.println("- pool constraint: sum bandwidth by poolId <= pool.availableBandwidth");
        out.println("- the pool does not replace the source-aware link limit: both constraints must hold");
        out.println("- EDGE/CLOUD resolve poolId through the active access gateway; V2V uses the explicit DIRECT_V2V binding");
        out.println();
    }

    private void printConfiguration(TemporalWindowResult result) {
        title("BANDWIDTH POOL CONFIGURATION SUMMARY");
        if (result.getSteps().isEmpty()) {
            out.println("No temporal step available.");
            out.println();
            return;
        }

        SystemSnapshot snapshot = observed(result.getSteps().get(0));
        out.println("poolCount: " + snapshot.getBandwidthPools().size());
        for (BandwidthPoolSnapshot pool : snapshot.getBandwidthPools()) {
            out.println("- " + pool.getPoolId()
                    + " | type=" + pool.getPoolType()
                    + " | capacity=" + format(pool.getAvailableBandwidth()));
        }
        out.println();
    }

    private void printHierarchicalSummary(TemporalWindowResult result) {
        title("HIERARCHICAL BANDWIDTH CHECK BY WINDOW");
        out.println("idx | snapshot | remoteGenes | unresolved | violatedLinks | saturatedLinks | violatedPools | saturatedPools | status");

        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot snapshot = observed(step);
            HierarchicalAnalysis analysis = analyze(step);
            LinkStats linkStats = LinkStats.from(analysis);
            PoolStats poolStats = PoolStats.from(snapshot, analysis);
            String status = analysis.unresolvedRemoteGenes == 0
                    && linkStats.violatedLinks == 0
                    && poolStats.violatedPools == 0
                    ? "OK"
                    : "CHECK";

            out.println(step.getWindowIndex()
                    + " | " + snapshot.getSnapshotId()
                    + " | " + analysis.remoteGenes
                    + " | " + analysis.unresolvedRemoteGenes
                    + " | " + linkStats.violatedLinks
                    + " | " + linkStats.saturatedLinks
                    + " | " + poolStats.violatedPools
                    + " | " + poolStats.saturatedPools
                    + " | " + status);
        }
        out.println();
    }

    private void printLinkSummary(TemporalWindowResult result) {
        title("LINK BANDWIDTH PRESSURE SUMMARY BY WINDOW");
        out.println("idx | snapshot | usedLinks | violatedLinks | saturatedLinks | worstLink | worstUsage% | totalRequested");

        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot snapshot = observed(step);
            HierarchicalAnalysis analysis = analyze(step);
            LinkStats stats = LinkStats.from(analysis);

            out.println(step.getWindowIndex()
                    + " | " + snapshot.getSnapshotId()
                    + " | " + stats.usedLinks
                    + " | " + stats.violatedLinks
                    + " | " + stats.saturatedLinks
                    + " | " + stats.worstCandidateId
                    + " | " + format(stats.worstUsagePercent)
                    + " | " + format(stats.totalRequestedBandwidth));
        }
        out.println();
    }

    private void printLinkDetails(TemporalWindowResult result) {
        title("LINK BANDWIDTH DETAILS BY WINDOW");
        out.println("idx | snapshot | candidateId | sourceVehicle | executionNode | type | used | capacity | usage% | violation | saturated | taskCount | resolvedPool");

        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot snapshot = observed(step);
            HierarchicalAnalysis analysis = analyze(step);

            for (Map.Entry<String, LinkUsage> entry
                    : analysis.usageByCandidateId.entrySet()) {
                LinkUsage usage = entry.getValue();
                if (usage.usedBandwidth <= 0.0 && usage.taskCount == 0) {
                    continue;
                }

                double percent = usagePercent(
                        usage.usedBandwidth,
                        usage.availableBandwidth
                );
                boolean violation = usage.usedBandwidth
                        > usage.availableBandwidth;
                boolean saturated = !violation
                        && percent >= SATURATION_THRESHOLD_PERCENT;

                out.println(step.getWindowIndex()
                        + " | " + snapshot.getSnapshotId()
                        + " | " + usage.candidateId
                        + " | " + usage.sourceVehicleId
                        + " | " + usage.executionNodeId
                        + " | " + usage.nodeType
                        + " | " + format(usage.usedBandwidth)
                        + " | " + format(usage.availableBandwidth)
                        + " | " + format(percent)
                        + " | " + violation
                        + " | " + saturated
                        + " | " + usage.taskCount
                        + " | " + usage.resolvedPoolId);
            }
        }
        out.println();
    }

    private void printPoolSummary(TemporalWindowResult result) {
        title("BANDWIDTH POOL SUMMARY BY WINDOW");
        out.println("idx | snapshot | pools | remoteGenes | unresolved | violatedPools | saturatedPools | worstPool | worstUsage% | totalRequested | totalCapacity");

        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot snapshot = observed(step);
            HierarchicalAnalysis analysis = analyze(step);
            PoolStats stats = PoolStats.from(snapshot, analysis);

            out.println(step.getWindowIndex()
                    + " | " + snapshot.getSnapshotId()
                    + " | " + stats.poolCount
                    + " | " + analysis.remoteGenes
                    + " | " + analysis.unresolvedRemoteGenes
                    + " | " + stats.violatedPools
                    + " | " + stats.saturatedPools
                    + " | " + stats.worstPoolId
                    + " | " + format(stats.worstUsagePercent)
                    + " | " + format(stats.totalRequestedBandwidth)
                    + " | " + format(stats.totalCapacity));
        }
        out.println();
    }

    private void printPoolDetails(TemporalWindowResult result) {
        title("BANDWIDTH POOL DETAILS BY WINDOW");
        out.println("idx | snapshot | poolId | type | used | capacity | usage% | violation | saturated | taskCount");

        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot snapshot = observed(step);
            HierarchicalAnalysis analysis = analyze(step);

            for (BandwidthPoolSnapshot pool : snapshot.getBandwidthPools()) {
                PoolUsage usage = analysis.usageByPoolId.getOrDefault(
                        pool.getPoolId(),
                        new PoolUsage()
                );
                double percent = usagePercent(
                        usage.usedBandwidth,
                        pool.getAvailableBandwidth()
                );
                boolean violation = usage.usedBandwidth
                        > pool.getAvailableBandwidth();
                boolean saturated = !violation
                        && percent >= SATURATION_THRESHOLD_PERCENT;

                out.println(step.getWindowIndex()
                        + " | " + snapshot.getSnapshotId()
                        + " | " + pool.getPoolId()
                        + " | " + pool.getPoolType()
                        + " | " + format(usage.usedBandwidth)
                        + " | " + format(pool.getAvailableBandwidth())
                        + " | " + format(percent)
                        + " | " + violation
                        + " | " + saturated
                        + " | " + usage.taskCount);
            }
        }
        out.println();
    }

    private HierarchicalAnalysis analyze(TemporalStepResult step) {
        SystemSnapshot snapshot = observed(step);
        HierarchicalAnalysis analysis = new HierarchicalAnalysis();

        for (BandwidthPoolSnapshot pool : snapshot.getBandwidthPools()) {
            analysis.usageByPoolId.put(pool.getPoolId(), new PoolUsage());
        }

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }
            analysis.usageByCandidateId.put(
                    candidate.getCandidateId(),
                    LinkUsage.from(candidate)
            );
        }

        for (Gene gene : step.getMaGaResult().getBestChromosome().getGenes()) {
            NodeCandidate candidate = findCandidate(
                    snapshot,
                    gene.getSelectedCandidateId()
            );
            if (candidate == null || candidate.getType() == NodeType.LOCAL) {
                continue;
            }

            analysis.remoteGenes++;
            double bandwidth = Math.max(0.0, gene.getAllocatedBandwidth());
            LinkUsage linkUsage = analysis.usageByCandidateId.computeIfAbsent(
                    candidate.getCandidateId(),
                    ignored -> LinkUsage.from(candidate)
            );
            linkUsage.usedBandwidth += bandwidth;
            linkUsage.taskCount++;

            try {
                BandwidthPoolSnapshot pool = resolver.resolve(
                        snapshot,
                        candidate
                );
                linkUsage.resolvedPoolId = pool.getPoolId();
                PoolUsage poolUsage = analysis.usageByPoolId.computeIfAbsent(
                        pool.getPoolId(),
                        ignored -> new PoolUsage()
                );
                poolUsage.usedBandwidth += bandwidth;
                poolUsage.taskCount++;
            } catch (IllegalArgumentException ex) {
                analysis.unresolvedRemoteGenes++;
                linkUsage.resolvedPoolId = "UNRESOLVED";
            }
        }

        return analysis;
    }

    private NodeCandidate findCandidate(SystemSnapshot snapshot, String id) {
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getCandidateId().equals(id)) {
                return candidate;
            }
        }
        return null;
    }

    private SystemSnapshot observed(TemporalStepResult step) {
        return step.getSystemStateObservation()
                .map(SystemStateObservation::getObservedSnapshot)
                .orElse(step.getSnapshot());
    }

    private double usagePercent(double used, double capacity) {
        return capacity <= 0.0 ? 0.0 : (used / capacity) * 100.0;
    }

    private String format(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ITALY, "%.6f", value)
                : "-";
    }

    private void title(String value) {
        out.println("------------------------------------------------------------");
        out.println(value);
        out.println("------------------------------------------------------------");
    }

    private static final class HierarchicalAnalysis {
        private final Map<String, LinkUsage> usageByCandidateId =
                new LinkedHashMap<>();
        private final Map<String, PoolUsage> usageByPoolId =
                new LinkedHashMap<>();
        private int remoteGenes;
        private int unresolvedRemoteGenes;
    }

    private static final class LinkUsage {
        private final String candidateId;
        private final String sourceVehicleId;
        private final String executionNodeId;
        private final NodeType nodeType;
        private final double availableBandwidth;
        private String resolvedPoolId = "-";
        private double usedBandwidth;
        private int taskCount;

        private LinkUsage(
                String candidateId,
                String sourceVehicleId,
                String executionNodeId,
                NodeType nodeType,
                double availableBandwidth
        ) {
            this.candidateId = candidateId;
            this.sourceVehicleId = sourceVehicleId;
            this.executionNodeId = executionNodeId;
            this.nodeType = nodeType;
            this.availableBandwidth = availableBandwidth;
        }

        private static LinkUsage from(NodeCandidate candidate) {
            return new LinkUsage(
                    candidate.getCandidateId(),
                    candidate.getSourceVehicleId(),
                    candidate.getExecutionNodeId(),
                    candidate.getType(),
                    candidate.getAvailableBandwidth()
            );
        }
    }

    private static final class PoolUsage {
        private double usedBandwidth;
        private int taskCount;
    }

    private static final class LinkStats {
        private int usedLinks;
        private int violatedLinks;
        private int saturatedLinks;
        private double worstUsagePercent;
        private double totalRequestedBandwidth;
        private String worstCandidateId = "none";

        private static LinkStats from(HierarchicalAnalysis analysis) {
            LinkStats result = new LinkStats();
            for (LinkUsage usage : analysis.usageByCandidateId.values()) {
                if (usage.usedBandwidth <= 0.0 && usage.taskCount == 0) {
                    continue;
                }
                result.usedLinks++;
                result.totalRequestedBandwidth += usage.usedBandwidth;
                double percent = usage.availableBandwidth <= 0.0
                        ? 0.0
                        : (usage.usedBandwidth / usage.availableBandwidth)
                        * 100.0;

                if (usage.usedBandwidth > usage.availableBandwidth) {
                    result.violatedLinks++;
                } else if (percent >= SATURATION_THRESHOLD_PERCENT) {
                    result.saturatedLinks++;
                }

                if (percent > result.worstUsagePercent) {
                    result.worstUsagePercent = percent;
                    result.worstCandidateId = usage.candidateId;
                }
            }
            return result;
        }
    }

    private static final class PoolStats {
        private int poolCount;
        private int violatedPools;
        private int saturatedPools;
        private double worstUsagePercent;
        private double totalRequestedBandwidth;
        private double totalCapacity;
        private String worstPoolId = "none";

        private static PoolStats from(
                SystemSnapshot snapshot,
                HierarchicalAnalysis analysis
        ) {
            PoolStats result = new PoolStats();
            result.poolCount = snapshot.getBandwidthPools().size();

            for (BandwidthPoolSnapshot pool : snapshot.getBandwidthPools()) {
                PoolUsage usage = analysis.usageByPoolId.getOrDefault(
                        pool.getPoolId(),
                        new PoolUsage()
                );
                double percent = pool.getAvailableBandwidth() <= 0.0
                        ? 0.0
                        : (usage.usedBandwidth
                        / pool.getAvailableBandwidth()) * 100.0;

                result.totalRequestedBandwidth += usage.usedBandwidth;
                result.totalCapacity += pool.getAvailableBandwidth();

                if (usage.usedBandwidth > pool.getAvailableBandwidth()) {
                    result.violatedPools++;
                } else if (percent >= SATURATION_THRESHOLD_PERCENT) {
                    result.saturatedPools++;
                }

                if (percent > result.worstUsagePercent) {
                    result.worstUsagePercent = percent;
                    result.worstPoolId = pool.getPoolId();
                }
            }
            return result;
        }
    }
}
