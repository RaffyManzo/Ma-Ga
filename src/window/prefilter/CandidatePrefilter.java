package window.prefilter;

import config.mobility.MobilityConfig;
import model.mobility.AccessLinkMetrics;
import model.mobility.AccessLinkMetricsEstimator;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prefiltro strutturale dei candidati destinati al GA. */
public final class CandidatePrefilter {
    private static final double EPSILON = 1.0E-9;
    private final CandidatePrefilterConfig config;
    private final AccessLinkMetricsEstimator accessLinkMetricsEstimator;

    public CandidatePrefilter() {
        this(CandidatePrefilterConfig.defaultConfig(), MobilityConfig.defaultConfig());
    }

    public CandidatePrefilter(CandidatePrefilterConfig config) {
        this(config, MobilityConfig.defaultConfig());
    }

    public CandidatePrefilter(CandidatePrefilterConfig config, MobilityConfig mobilityConfig) {
        if (config == null) { throw new IllegalArgumentException("config must not be null."); }
        if (mobilityConfig == null) { throw new IllegalArgumentException("mobilityConfig must not be null."); }
        this.config = config;
        this.accessLinkMetricsEstimator = new AccessLinkMetricsEstimator(mobilityConfig);
    }

    public CandidateFilteringResult filter(SystemSnapshot snapshot) {
        if (snapshot == null) { throw new IllegalArgumentException("snapshot must not be null."); }
        if (!config.isEnabled()) { return disabledResult(snapshot); }
        Map<String, VehicleSnapshot> vehicles = indexVehicles(snapshot);
        Set<String> taskSources = indexTaskSourceIds(snapshot);
        List<NodeCandidate> kept = new ArrayList<>();
        List<FilteredCandidateRecord> records = new ArrayList<>();
        Map<CandidateRejectionReason, Integer> counts = new EnumMap<>(CandidateRejectionReason.class);

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            CandidateDecision decision = evaluate(snapshot, candidate, taskSources, vehicles);
            records.add(new FilteredCandidateRecord(
                    candidate.getCandidateId(),
                    candidate.getSourceVehicleId(),
                    candidate.getExecutionNodeId(),
                    candidate.getType(),
                    decision.reason,
                    0.0,
                    decision.coverageSeconds,
                    decision.note
            ));
            counts.merge(decision.reason, 1, Integer::sum);
            if (decision.keep) { kept.add(candidate); }
        }

        SystemSnapshot filtered = new SystemSnapshot(
                snapshot.getSnapshotId(),
                snapshot.getTimeSeconds(),
                snapshot.getVehicles(),
                snapshot.getTasks(),
                kept,
                snapshot.getAccessGateways(),
                snapshot.getAccessLinks()
        );
        return new CandidateFilteringResult(
                snapshot,
                filtered,
                new CandidateFilteringStats(snapshot.getCandidateNodes().size(), kept.size(), counts),
                records
        );
    }

    private CandidateFilteringResult disabledResult(SystemSnapshot snapshot) {
        Map<CandidateRejectionReason, Integer> counts = new EnumMap<>(CandidateRejectionReason.class);
        counts.put(CandidateRejectionReason.KEPT, snapshot.getCandidateNodes().size());
        return new CandidateFilteringResult(
                snapshot,
                snapshot,
                new CandidateFilteringStats(snapshot.getCandidateNodes().size(), snapshot.getCandidateNodes().size(), counts),
                List.of()
        );
    }

    private CandidateDecision evaluate(
            SystemSnapshot snapshot,
            NodeCandidate candidate,
            Set<String> taskSources,
            Map<String, VehicleSnapshot> vehicles
    ) {
        if (candidate == null) { throw new IllegalArgumentException("candidate must not be null."); }
        if (candidate.getType() == NodeType.LOCAL) {
            return CandidateDecision.keep(Double.POSITIVE_INFINITY, "LOCAL candidate preserved.");
        }
        if (!taskSources.contains(candidate.getSourceVehicleId())) {
            return CandidateDecision.reject(CandidateRejectionReason.NO_TASK_FOR_SOURCE, 0.0, "No active task for source vehicle.");
        }
        VehicleSnapshot source = vehicles.get(candidate.getSourceVehicleId());
        if (source == null) {
            return CandidateDecision.reject(CandidateRejectionReason.INSUFFICIENT_COVERAGE, 0.0, "Source vehicle missing.");
        }
        if (!Double.isFinite(candidate.getAvailableCpu()) || candidate.getAvailableCpu() <= 0.0) {
            return CandidateDecision.reject(CandidateRejectionReason.INVALID_CPU, 0.0, "Remote CPU must be > 0.");
        }
        if (!Double.isFinite(candidate.getAvailableBandwidth()) || candidate.getAvailableBandwidth() <= 0.0) {
            return CandidateDecision.reject(CandidateRejectionReason.INVALID_BANDWIDTH, 0.0, "Remote bandwidth must be > 0.");
        }
        if (candidate.getType() == NodeType.CLOUD) {
            try {
                AccessLinkMetrics access = accessLinkMetricsEstimator.estimateActiveLink(snapshot, candidate.getSourceVehicleId());
                if (!access.isAvailable() || access.getCoverageTimeSeconds() <= 0.0) {
                    return CandidateDecision.reject(
                            CandidateRejectionReason.ACCESS_LINK_UNAVAILABLE,
                            access.getCoverageTimeSeconds(),
                            "CLOUD access gateway is unavailable or outside coverage."
                    );
                }
                return CandidateDecision.keep(
                        access.getCoverageTimeSeconds(),
                        "CLOUD candidate preserved through active gateway " + access.getGatewayId() + "."
                );
            } catch (IllegalArgumentException ex) {
                return CandidateDecision.reject(
                        CandidateRejectionReason.ACCESS_LINK_UNAVAILABLE,
                        0.0,
                        ex.getMessage()
                );
            }
        }
        double coverage = estimateNonCloudCoverage(candidate, source, vehicles);
        if (!Double.isFinite(coverage) || coverage <= 0.0) {
            return CandidateDecision.reject(CandidateRejectionReason.INSUFFICIENT_COVERAGE, coverage, "Remote candidate outside physical coverage.");
        }
        return CandidateDecision.keep(coverage, "Structurally valid candidate preserved for GA evaluation.");
    }

    private double estimateNonCloudCoverage(
            NodeCandidate candidate,
            VehicleSnapshot source,
            Map<String, VehicleSnapshot> vehicles
    ) {
        if (candidate.getType() == NodeType.EDGE) {
            if (!candidate.hasCoverageGeometry()) { return 0.0; }
            double radius = candidate.getCoverageRadiusMeters();
            double distance = distance(source.getX(), source.getY(), candidate.getNodeX(), candidate.getNodeY());
            if (!Double.isFinite(radius) || radius <= 0.0 || distance >= radius) { return 0.0; }
            return (radius - distance) / Math.max(EPSILON, Math.abs(source.getSpeed()));
        }
        if (candidate.getType() == NodeType.VEHICLE) {
            VehicleSnapshot target = vehicles.get(candidate.getExecutionNodeId());
            if (target == null) { return 0.0; }
            double distance = distance(source.getX(), source.getY(), target.getX(), target.getY());
            double radius = config.getV2vCoverageRadiusMeters();
            if (distance >= radius) { return 0.0; }
            double relativeSpeed = Math.abs(source.getSpeed() - target.getSpeed());
            return relativeSpeed <= EPSILON
                    ? config.getCloudCoverageSeconds()
                    : (radius - distance) / relativeSpeed;
        }
        return 0.0;
    }

    private Map<String, VehicleSnapshot> indexVehicles(SystemSnapshot snapshot) {
        Map<String, VehicleSnapshot> result = new HashMap<>();
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) { result.put(vehicle.getVehicleId(), vehicle); }
        return result;
    }
    private Set<String> indexTaskSourceIds(SystemSnapshot snapshot) {
        Set<String> result = new HashSet<>();
        for (TaskInstance task : snapshot.getTasks()) { result.add(task.getSourceVehicleId()); }
        return result;
    }
    private double distance(double x1, double y1, double x2, double y2) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)) { return Double.POSITIVE_INFINITY; }
        double dx = x1 - x2; double dy = y1 - y2; return Math.sqrt(dx * dx + dy * dy);
    }

    private static final class CandidateDecision {
        private final boolean keep;
        private final CandidateRejectionReason reason;
        private final double coverageSeconds;
        private final String note;
        private CandidateDecision(boolean keep, CandidateRejectionReason reason, double coverageSeconds, String note) {
            this.keep = keep; this.reason = reason; this.coverageSeconds = coverageSeconds; this.note = note;
        }
        private static CandidateDecision keep(double coverage, String note) { return new CandidateDecision(true, CandidateRejectionReason.KEPT, coverage, note); }
        private static CandidateDecision reject(CandidateRejectionReason reason, double coverage, String note) { return new CandidateDecision(false, reason, coverage, note); }
    }
}
