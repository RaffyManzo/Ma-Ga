package window.prefilter;

import model.node.NodeCandidate;
import model.node.NodeType;
import model.offloading.OffloadingTimeModel;
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

/**
 * Prefiltra i candidati prima dell'esecuzione del GA.
 *
 * Il prefilter riduce lo spazio di ricerca eliminando candidati remoti
 * chiaramente non utilizzabili:
 *
 * - CPU o banda non valide;
 * - EDGE/V2V senza copertura sufficiente;
 * - candidati che, anche con una stima ottimistica, non sono competitivi
 *   rispetto alle deadline dei task associati al veicolo sorgente.
 *
 * I candidati LOCAL vengono sempre mantenuti.
 */
public final class CandidatePrefilter {

    private static final double EPSILON = 1.0E-9;

    private final CandidatePrefilterConfig config;
    private final OffloadingTimeModel offloadingTimeModel;

    public CandidatePrefilter() {
        this(CandidatePrefilterConfig.defaultConfig());
    }

    public CandidatePrefilter(CandidatePrefilterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null.");
        }

        this.config = config;
        this.offloadingTimeModel = new OffloadingTimeModel();
    }

    /**
     * Applica il prefilter allo snapshot.
     *
     * @param snapshot snapshot originale
     * @return risultato contenente snapshot filtrato e statistiche
     */
    public CandidateFilteringResult filter(SystemSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null.");
        }

        if (!config.isEnabled()) {
            return disabledResult(snapshot);
        }

        Map<String, VehicleSnapshot> vehicleById = indexVehicles(snapshot);
        Map<String, List<TaskInstance>> tasksBySource =
                indexTasksBySource(snapshot);

        List<NodeCandidate> keptCandidates = new ArrayList<>();
        List<FilteredCandidateRecord> records = new ArrayList<>();
        Map<CandidateRejectionReason, Integer> reasonCounts =
                new EnumMap<>(CandidateRejectionReason.class);

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            CandidateDecision decision = evaluateCandidate(
                    candidate,
                    tasksBySource.get(candidate.getSourceVehicleId()),
                    vehicleById
            );

            records.add(
                    new FilteredCandidateRecord(
                            candidate.getCandidateId(),
                            candidate.getSourceVehicleId(),
                            candidate.getExecutionNodeId(),
                            candidate.getType(),
                            decision.reason,
                            decision.estimatedBestCompletionSeconds,
                            decision.estimatedCoverageSeconds,
                            decision.note
                    )
            );

            reasonCounts.merge(decision.reason, 1, Integer::sum);

            if (decision.keep) {
                keptCandidates.add(candidate);
            }
        }

        keptCandidates = restoreFallbackCandidatesIfNeeded(
                snapshot,
                keptCandidates,
                records,
                reasonCounts
        );

        SystemSnapshot filteredSnapshot = new SystemSnapshot(
                snapshot.getSnapshotId(),
                snapshot.getTimeSeconds(),
                snapshot.getVehicles(),
                snapshot.getTasks(),
                keptCandidates
        );

        CandidateFilteringStats stats = new CandidateFilteringStats(
                snapshot.getCandidateNodes().size(),
                keptCandidates.size(),
                reasonCounts
        );

        return new CandidateFilteringResult(
                snapshot,
                filteredSnapshot,
                stats,
                records
        );
    }

    private CandidateFilteringResult disabledResult(SystemSnapshot snapshot) {
        Map<CandidateRejectionReason, Integer> reasonCounts =
                new EnumMap<>(CandidateRejectionReason.class);

        reasonCounts.put(
                CandidateRejectionReason.KEPT,
                snapshot.getCandidateNodes().size()
        );

        return new CandidateFilteringResult(
                snapshot,
                snapshot,
                new CandidateFilteringStats(
                        snapshot.getCandidateNodes().size(),
                        snapshot.getCandidateNodes().size(),
                        reasonCounts
                ),
                List.of()
        );
    }

    private CandidateDecision evaluateCandidate(
            NodeCandidate candidate,
            List<TaskInstance> sourceTasks,
            Map<String, VehicleSnapshot> vehicleById
    ) {
        if (candidate.getType() == NodeType.LOCAL) {
            return CandidateDecision.keep(
                    0.0,
                    Double.POSITIVE_INFINITY,
                    "LOCAL candidate preserved."
            );
        }

        if (sourceTasks == null || sourceTasks.isEmpty()) {
            return CandidateDecision.reject(
                    CandidateRejectionReason.NO_TASK_FOR_SOURCE,
                    0.0,
                    0.0,
                    "No active task for candidate source vehicle."
            );
        }

        if (!Double.isFinite(candidate.getAvailableCpu())
                || candidate.getAvailableCpu() < config.getMinRemoteCpu()) {
            return CandidateDecision.reject(
                    CandidateRejectionReason.INVALID_CPU,
                    0.0,
                    0.0,
                    "Remote CPU below minimum threshold."
            );
        }

        if (!Double.isFinite(candidate.getAvailableBandwidth())
                || candidate.getAvailableBandwidth()
                < config.getMinRemoteBandwidth()) {
            return CandidateDecision.reject(
                    CandidateRejectionReason.INVALID_BANDWIDTH,
                    0.0,
                    0.0,
                    "Remote bandwidth below minimum threshold."
            );
        }

        VehicleSnapshot sourceVehicle =
                vehicleById.get(candidate.getSourceVehicleId());

        double coverageSeconds = estimateCoverageSeconds(
                candidate,
                sourceVehicle,
                vehicleById
        );

        if (coverageSeconds < config.getMinCoverageSeconds()) {
            return CandidateDecision.reject(
                    CandidateRejectionReason.INSUFFICIENT_COVERAGE,
                    0.0,
                    coverageSeconds,
                    "Coverage below minimum threshold."
            );
        }

        if (candidate.getType() == NodeType.CLOUD
                && config.isKeepAllCloudCandidates()) {
            return CandidateDecision.keep(
                    0.0,
                    coverageSeconds,
                    "CLOUD candidate preserved by config."
            );
        }

        CandidateTaskFeasibility best =
                bestTaskFeasibilityForCandidate(
                        candidate,
                        sourceVehicle,
                        sourceTasks,
                        coverageSeconds
                );

        if (!best.acceptable) {
            return CandidateDecision.reject(
                    best.reason,
                    best.bestCompletionSeconds,
                    coverageSeconds,
                    best.note
            );
        }

        return CandidateDecision.keep(
                best.bestCompletionSeconds,
                coverageSeconds,
                best.note
        );
    }

    /**
     * Stima se il candidato può essere utile per almeno un task della sorgente.
     */
    private CandidateTaskFeasibility bestTaskFeasibilityForCandidate(
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            List<TaskInstance> sourceTasks,
            double coverageSeconds
    ) {
        double bestCompletion = Double.POSITIVE_INFINITY;
        boolean atLeastOneAcceptable = false;
        CandidateRejectionReason rejectionReason =
                CandidateRejectionReason.DEADLINE_LOWER_BOUND_TOO_HIGH;

        for (TaskInstance task : sourceTasks) {
            double estimatedBestCompletion =
                    estimateOptimisticBestCompletionSeconds(
                            task,
                            candidate,
                            sourceVehicle
                    );

            bestCompletion = Math.min(
                    bestCompletion,
                    estimatedBestCompletion
            );

            double deadlineLimit = task.getDeadlineSeconds()
                    * config.getDeadlineSlackFactor();

            boolean deadlineCompatible =
                    estimatedBestCompletion <= deadlineLimit;

            boolean coverageCompatible =
                    coverageSeconds >= Math.max(
                            config.getMinCoverageSeconds(),
                            estimatedBestCompletion
                                    * config.getCoverageSafetyFactor()
                    );

            if (deadlineCompatible && coverageCompatible) {
                atLeastOneAcceptable = true;
                break;
            }

            if (!coverageCompatible) {
                rejectionReason =
                        CandidateRejectionReason.INSUFFICIENT_COVERAGE;
            }
        }

        if (atLeastOneAcceptable) {
            return CandidateTaskFeasibility.accept(
                    bestCompletion,
                    "Candidate has at least one compatible task."
            );
        }

        return CandidateTaskFeasibility.reject(
                rejectionReason,
                bestCompletion,
                "No task for this source is compatible with candidate lower-bound estimates."
        );
    }

    /**
     * Stima ottimistica del miglior completion ottenibile con candidato remoto.
     *
     * Usa una formula semplificata di split continuo:
     *
     * local(p)  = (1-p) * A
     * remote(p) = L + p * B
     *
     * dove:
     * A = tempo locale puro;
     * B = upload + remote execution + download per p=1;
     * L = latenza base.
     */
    private double estimateOptimisticBestCompletionSeconds(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
        double localOnly = offloadingTimeModel.estimateLocalOnlyTime(
                task,
                sourceVehicle
        );

        double remoteLinear = offloadingTimeModel.estimateRemoteLinearTime(
                task,
                candidate
        );

        double latency = candidate.getBaseLatencySeconds();

        if (!Double.isFinite(localOnly)) {
            return latency + remoteLinear;
        }

        double denominator = localOnly + remoteLinear;

        if (denominator <= EPSILON) {
            return 0.0;
        }

        double pStar = (localOnly - latency) / denominator;

        if (pStar <= 0.0) {
            return Math.min(localOnly, latency);
        }

        if (pStar >= 1.0) {
            return Math.min(localOnly, latency + remoteLinear);
        }

        double localBranch = (1.0 - pStar) * localOnly;
        double remoteBranch = latency + pStar * remoteLinear;

        return Math.max(localBranch, remoteBranch);
    }

    /**
     * Stima copertura per EDGE, VEHICLE e CLOUD.
     */
    private double estimateCoverageSeconds(
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            Map<String, VehicleSnapshot> vehicleById
    ) {
        if (candidate.getType() == NodeType.CLOUD) {
            return config.getCloudCoverageSeconds();
        }

        if (sourceVehicle == null) {
            return 0.0;
        }

        if (candidate.getType() == NodeType.EDGE) {
            double radius = candidate.getCoverageRadiusMeters();

            if (!Double.isFinite(radius) || radius <= 0.0) {
                return 0.0;
            }

            double distance = distance(
                    sourceVehicle.getX(),
                    sourceVehicle.getY(),
                    candidate.getNodeX(),
                    candidate.getNodeY()
            );

            if (distance >= radius) {
                return 0.0;
            }

            double speed = Math.max(EPSILON, sourceVehicle.getSpeed());

            return (radius - distance) / speed;
        }

        if (candidate.getType() == NodeType.VEHICLE) {
            VehicleSnapshot targetVehicle =
                    vehicleById.get(candidate.getExecutionNodeId());

            if (targetVehicle == null) {
                return 0.0;
            }

            double distance = distance(
                    sourceVehicle.getX(),
                    sourceVehicle.getY(),
                    targetVehicle.getX(),
                    targetVehicle.getY()
            );

            double radius = config.getV2vCoverageRadiusMeters();

            if (distance >= radius) {
                return 0.0;
            }

            double relativeSpeed = Math.abs(
                    sourceVehicle.getSpeed() - targetVehicle.getSpeed()
            );

            if (relativeSpeed <= EPSILON) {
                return config.getCloudCoverageSeconds();
            }

            return (radius - distance) / relativeSpeed;
        }

        return 0.0;
    }

    /**
     * Ripristina almeno un candidato per ogni task, per evitare snapshot non ottimizzabili.
     */
    private List<NodeCandidate> restoreFallbackCandidatesIfNeeded(
            SystemSnapshot snapshot,
            List<NodeCandidate> keptCandidates,
            List<FilteredCandidateRecord> records,
            Map<CandidateRejectionReason, Integer> reasonCounts
    ) {
        Map<String, List<NodeCandidate>> keptBySource = new HashMap<>();

        for (NodeCandidate candidate : keptCandidates) {
            keptBySource
                    .computeIfAbsent(candidate.getSourceVehicleId(), key -> new ArrayList<>())
                    .add(candidate);
        }

        Set<String> requiredSources = new HashSet<>();

        for (TaskInstance task : snapshot.getTasks()) {
            requiredSources.add(task.getSourceVehicleId());
        }

        List<NodeCandidate> restored = new ArrayList<>(keptCandidates);

        for (String sourceVehicleId : requiredSources) {
            if (keptBySource.containsKey(sourceVehicleId)
                    && !keptBySource.get(sourceVehicleId).isEmpty()) {
                continue;
            }

            NodeCandidate fallback = findFallbackCandidate(
                    sourceVehicleId,
                    snapshot.getCandidateNodes()
            );

            if (fallback == null) {
                continue;
            }

            restored.add(fallback);

            reasonCounts.merge(
                    CandidateRejectionReason.RESTORED_AS_FALLBACK,
                    1,
                    Integer::sum
            );

            records.add(
                    new FilteredCandidateRecord(
                            fallback.getCandidateId(),
                            fallback.getSourceVehicleId(),
                            fallback.getExecutionNodeId(),
                            fallback.getType(),
                            CandidateRejectionReason.RESTORED_AS_FALLBACK,
                            0.0,
                            0.0,
                            "Restored to preserve at least one candidate for source vehicle."
                    )
            );
        }

        return restored;
    }

    private NodeCandidate findFallbackCandidate(
            String sourceVehicleId,
            List<NodeCandidate> candidates
    ) {
        NodeCandidate firstValid = null;

        for (NodeCandidate candidate : candidates) {
            if (!candidate.isValidForSourceVehicle(sourceVehicleId)) {
                continue;
            }

            if (firstValid == null) {
                firstValid = candidate;
            }

            if (candidate.getType() == NodeType.LOCAL) {
                return candidate;
            }
        }

        return firstValid;
    }

    private Map<String, VehicleSnapshot> indexVehicles(
            SystemSnapshot snapshot
    ) {
        Map<String, VehicleSnapshot> result = new HashMap<>();

        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            result.put(vehicle.getVehicleId(), vehicle);
        }

        return result;
    }

    private Map<String, List<TaskInstance>> indexTasksBySource(
            SystemSnapshot snapshot
    ) {
        Map<String, List<TaskInstance>> result = new HashMap<>();

        for (TaskInstance task : snapshot.getTasks()) {
            result.computeIfAbsent(
                    task.getSourceVehicleId(),
                    key -> new ArrayList<>()
            ).add(task);
        }

        return result;
    }

    private double distance(
            double x1,
            double y1,
            double x2,
            double y2
    ) {
        if (!Double.isFinite(x1)
                || !Double.isFinite(y1)
                || !Double.isFinite(x2)
                || !Double.isFinite(y2)) {
            return Double.POSITIVE_INFINITY;
        }

        double dx = x1 - x2;
        double dy = y1 - y2;

        return Math.sqrt(dx * dx + dy * dy);
    }

    private static final class CandidateDecision {

        private final boolean keep;
        private final CandidateRejectionReason reason;
        private final double estimatedBestCompletionSeconds;
        private final double estimatedCoverageSeconds;
        private final String note;

        private CandidateDecision(
                boolean keep,
                CandidateRejectionReason reason,
                double estimatedBestCompletionSeconds,
                double estimatedCoverageSeconds,
                String note
        ) {
            this.keep = keep;
            this.reason = reason;
            this.estimatedBestCompletionSeconds = estimatedBestCompletionSeconds;
            this.estimatedCoverageSeconds = estimatedCoverageSeconds;
            this.note = note;
        }

        private static CandidateDecision keep(
                double estimatedBestCompletionSeconds,
                double estimatedCoverageSeconds,
                String note
        ) {
            return new CandidateDecision(
                    true,
                    CandidateRejectionReason.KEPT,
                    estimatedBestCompletionSeconds,
                    estimatedCoverageSeconds,
                    note
            );
        }

        private static CandidateDecision reject(
                CandidateRejectionReason reason,
                double estimatedBestCompletionSeconds,
                double estimatedCoverageSeconds,
                String note
        ) {
            return new CandidateDecision(
                    false,
                    reason,
                    estimatedBestCompletionSeconds,
                    estimatedCoverageSeconds,
                    note
            );
        }
    }

    private static final class CandidateTaskFeasibility {

        private final boolean acceptable;
        private final CandidateRejectionReason reason;
        private final double bestCompletionSeconds;
        private final String note;

        private CandidateTaskFeasibility(
                boolean acceptable,
                CandidateRejectionReason reason,
                double bestCompletionSeconds,
                String note
        ) {
            this.acceptable = acceptable;
            this.reason = reason;
            this.bestCompletionSeconds = bestCompletionSeconds;
            this.note = note;
        }

        private static CandidateTaskFeasibility accept(
                double bestCompletionSeconds,
                String note
        ) {
            return new CandidateTaskFeasibility(
                    true,
                    CandidateRejectionReason.KEPT,
                    bestCompletionSeconds,
                    note
            );
        }

        private static CandidateTaskFeasibility reject(
                CandidateRejectionReason reason,
                double bestCompletionSeconds,
                String note
        ) {
            return new CandidateTaskFeasibility(
                    false,
                    reason,
                    bestCompletionSeconds,
                    note
            );
        }
    }
}
