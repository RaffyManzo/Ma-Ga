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

/** Report dedicato ai pool di banda radio. */
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
        printConfiguration(result);
        printSummary(result);
        printDetails(result);
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

    private void printSummary(TemporalWindowResult result) {
        title("BANDWIDTH POOL SUMMARY BY WINDOW");
        out.println("idx | snapshot | pools | remoteGenes | unresolved | violatedPools | saturatedPools | worstPool | worstUsage% | totalRequested | totalCapacity");

        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot snapshot = observed(step);
            PoolAnalysis analysis = analyze(step);
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

    private void printDetails(TemporalWindowResult result) {
        title("BANDWIDTH POOL DETAILS BY WINDOW");
        out.println("idx | snapshot | poolId | type | used | capacity | usage% | violation | saturated | taskCount");

        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot snapshot = observed(step);
            PoolAnalysis analysis = analyze(step);

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

    private PoolAnalysis analyze(TemporalStepResult step) {
        SystemSnapshot snapshot = observed(step);
        PoolAnalysis analysis = new PoolAnalysis();

        for (BandwidthPoolSnapshot pool : snapshot.getBandwidthPools()) {
            analysis.usageByPoolId.put(pool.getPoolId(), new PoolUsage());
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
            try {
                BandwidthPoolSnapshot pool = resolver.resolve(
                        snapshot,
                        candidate
                );
                PoolUsage usage = analysis.usageByPoolId.computeIfAbsent(
                        pool.getPoolId(),
                        ignored -> new PoolUsage()
                );
                usage.usedBandwidth += Math.max(
                        0.0,
                        gene.getAllocatedBandwidth()
                );
                usage.taskCount++;
            } catch (IllegalArgumentException ex) {
                analysis.unresolvedRemoteGenes++;
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

    private static final class PoolAnalysis {
        private final Map<String, PoolUsage> usageByPoolId =
                new LinkedHashMap<>();
        private int remoteGenes;
        private int unresolvedRemoteGenes;
    }

    private static final class PoolUsage {
        private double usedBandwidth;
        private int taskCount;
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
                PoolAnalysis analysis
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
