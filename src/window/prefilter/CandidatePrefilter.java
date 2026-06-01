package window.prefilter;

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

/**
 * Prefiltra i candidati prima dell'esecuzione del GA.
 *
 * <p>Il filtro applica soltanto controlli strutturali. Non elimina candidati
 * raggiungibili perché poco convenienti, vicini al limite di copertura o
 * apparentemente incompatibili con una deadline. Queste valutazioni spettano
 * al repair deadline-aware, alla fitness e alla penalità mobility-aware.</p>
 *
 * <p>I candidati LOCAL vengono sempre mantenuti. I candidati remoti vengono
 * rimossi soltanto se non possono essere utilizzati in modo matematicamente
 * sensato: CPU o banda nulle/non valide, assenza di task per la sorgente,
 * sorgente mancante, EDGE fuori copertura oppure collegamento V2V fuori raggio.</p>
 */
public final class CandidatePrefilter {

    private static final double EPSILON = 1.0E-9;

    private final CandidatePrefilterConfig config;

    public CandidatePrefilter() {
        this(CandidatePrefilterConfig.defaultConfig());
    }

    public CandidatePrefilter(CandidatePrefilterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null.");
        }
        this.config = config;
    }

    /**
     * Applica il prefilter allo snapshot.
     *
     * @param snapshot snapshot originale osservato dalla sorgente
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
        Set<String> taskSourceIds = indexTaskSourceIds(snapshot);
        List<NodeCandidate> keptCandidates = new ArrayList<>();
        List<FilteredCandidateRecord> records = new ArrayList<>();
        Map<CandidateRejectionReason, Integer> reasonCounts =
                new EnumMap<>(CandidateRejectionReason.class);

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            CandidateDecision decision = evaluateCandidate(
                    candidate,
                    taskSourceIds,
                    vehicleById
            );

            records.add(new FilteredCandidateRecord(
                    candidate.getCandidateId(),
                    candidate.getSourceVehicleId(),
                    candidate.getExecutionNodeId(),
                    candidate.getType(),
                    decision.reason,
                    0.0,
                    decision.estimatedCoverageSeconds,
                    decision.note
            ));
            reasonCounts.merge(decision.reason, 1, Integer::sum);

            if (decision.keep) {
                keptCandidates.add(candidate);
            }
        }

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
            Set<String> taskSourceIds,
            Map<String, VehicleSnapshot> vehicleById
    ) {
        if (candidate == null) {
            throw new IllegalArgumentException(
                    "snapshot candidateNodes must not contain null elements."
            );
        }

        if (candidate.getType() == NodeType.LOCAL) {
            return CandidateDecision.keep(
                    Double.POSITIVE_INFINITY,
                    "LOCAL candidate preserved."
            );
        }

        if (!taskSourceIds.contains(candidate.getSourceVehicleId())) {
            return CandidateDecision.reject(
                    CandidateRejectionReason.NO_TASK_FOR_SOURCE,
                    0.0,
                    "No active task for candidate source vehicle."
            );
        }

        VehicleSnapshot sourceVehicle = vehicleById.get(candidate.getSourceVehicleId());
        if (sourceVehicle == null) {
            return CandidateDecision.reject(
                    CandidateRejectionReason.INSUFFICIENT_COVERAGE,
                    0.0,
                    "Source vehicle is missing from the observed snapshot."
            );
        }

        if (!Double.isFinite(candidate.getAvailableCpu())
                || candidate.getAvailableCpu() <= 0.0) {
            return CandidateDecision.reject(
                    CandidateRejectionReason.INVALID_CPU,
                    0.0,
                    "Remote CPU must be finite and > 0."
            );
        }

        if (!Double.isFinite(candidate.getAvailableBandwidth())
                || candidate.getAvailableBandwidth() <= 0.0) {
            return CandidateDecision.reject(
                    CandidateRejectionReason.INVALID_BANDWIDTH,
                    0.0,
                    "Remote bandwidth must be finite and > 0."
            );
        }

        double coverageSeconds = estimateCoverageSeconds(
                candidate,
                sourceVehicle,
                vehicleById
        );

        if (candidate.getType() != NodeType.CLOUD
                && (!Double.isFinite(coverageSeconds) || coverageSeconds <= 0.0)) {
            return CandidateDecision.reject(
                    CandidateRejectionReason.INSUFFICIENT_COVERAGE,
                    coverageSeconds,
                    "Remote candidate is currently outside its physical coverage."
            );
        }

        return CandidateDecision.keep(
                coverageSeconds,
                "Structurally valid candidate preserved for GA evaluation."
        );
    }

    /**
     * Stima la copertura strutturale corrente per EDGE, VEHICLE e CLOUD.
     *
     * <p>Il tempo residuo non viene confrontato con soglie euristiche. Per il
     * prefilter conta soltanto che il candidato sia raggiungibile nell'istante
     * osservato. La fragilità della scelta resta valutabile dalla penalità
     * mobility-aware.</p>
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
            if (!candidate.hasCoverageGeometry()) {
                return 0.0;
            }
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
            double speed = Math.max(EPSILON, Math.abs(sourceVehicle.getSpeed()));
            return (radius - distance) / speed;
        }

        if (candidate.getType() == NodeType.VEHICLE) {
            VehicleSnapshot targetVehicle = vehicleById.get(
                    candidate.getExecutionNodeId()
            );
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

    private Map<String, VehicleSnapshot> indexVehicles(SystemSnapshot snapshot) {
        Map<String, VehicleSnapshot> result = new HashMap<>();
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            result.put(vehicle.getVehicleId(), vehicle);
        }
        return result;
    }

    private Set<String> indexTaskSourceIds(SystemSnapshot snapshot) {
        Set<String> result = new HashSet<>();
        for (TaskInstance task : snapshot.getTasks()) {
            result.add(task.getSourceVehicleId());
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
        private final double estimatedCoverageSeconds;
        private final String note;

        private CandidateDecision(
                boolean keep,
                CandidateRejectionReason reason,
                double estimatedCoverageSeconds,
                String note
        ) {
            this.keep = keep;
            this.reason = reason;
            this.estimatedCoverageSeconds = estimatedCoverageSeconds;
            this.note = note;
        }

        private static CandidateDecision keep(
                double estimatedCoverageSeconds,
                String note
        ) {
            return new CandidateDecision(
                    true,
                    CandidateRejectionReason.KEPT,
                    estimatedCoverageSeconds,
                    note
            );
        }

        private static CandidateDecision reject(
                CandidateRejectionReason reason,
                double estimatedCoverageSeconds,
                String note
        ) {
            return new CandidateDecision(false, reason, estimatedCoverageSeconds, note);
        }
    }
}
