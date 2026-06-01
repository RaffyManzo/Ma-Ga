package ga.constraints;

import config.mobility.MobilityConfig;
import model.genetic.Gene;
import model.mobility.CoverageEstimator;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.offloading.OffloadingTimeBreakdown;
import model.offloading.OffloadingTimeModel;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.Objects;

/**
 * Valuta il vincolo di deadline del singolo task usando lo stesso modello
 * temporale impiegato dalla fitness.
 *
 * <p>Questa classe non sceglie autonomamente il nodo migliore e non sostituisce
 * il GA. Espone soltanto una valutazione coerente e riutilizzabile dal repair.</p>
 */
public final class DeadlineConstraintEvaluator {
    private static final double EPSILON = 1.0E-9;

    private final CoverageEstimator coverageEstimator;
    private final OffloadingTimeModel offloadingTimeModel;

    public DeadlineConstraintEvaluator() {
        this(MobilityConfig.defaultConfig());
    }

    public DeadlineConstraintEvaluator(MobilityConfig mobilityConfig) {
        this(
                new CoverageEstimator(
                        Objects.requireNonNull(
                                mobilityConfig,
                                "mobilityConfig must not be null."
                        )
                ),
                new OffloadingTimeModel()
        );
    }

    public DeadlineConstraintEvaluator(
            CoverageEstimator coverageEstimator,
            OffloadingTimeModel offloadingTimeModel
    ) {
        this.coverageEstimator = Objects.requireNonNull(
                coverageEstimator,
                "coverageEstimator must not be null."
        );
        this.offloadingTimeModel = Objects.requireNonNull(
                offloadingTimeModel,
                "offloadingTimeModel must not be null."
        );
    }

    /**
     * Valuta un gene rispetto al task e allo snapshot corrente.
     *
     * <p>Per i candidati remoti la scelta è considerata mobility-aware soltanto
     * se il completion time stimato non supera il tempo di copertura.</p>
     */
    public DeadlineEvaluation evaluate(
            Gene gene,
            TaskInstance task,
            SystemSnapshot snapshot
    ) {
        Objects.requireNonNull(gene, "gene must not be null.");
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        NodeCandidate candidate = findCandidate(snapshot, gene.getSelectedCandidateId());
        VehicleSnapshot sourceVehicle = findVehicle(snapshot, task.getSourceVehicleId());
        double deadline = task.getDeadlineSeconds();

        if (candidate == null
                || sourceVehicle == null
                || !candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
            return DeadlineEvaluation.invalid(deadline);
        }

        double localCpu = sourceVehicle.getLocalCpu();
        if (!isStrictlyPositive(localCpu)) {
            return DeadlineEvaluation.invalid(deadline);
        }

        OffloadingTimeBreakdown timeBreakdown;
        if (candidate.getType() == NodeType.LOCAL) {
            if (Math.abs(gene.getOffloadingRatio()) > EPSILON) {
                return DeadlineEvaluation.invalid(deadline);
            }
            timeBreakdown = offloadingTimeModel.evaluateLocal(task, localCpu);
        } else {
            double p = gene.getOffloadingRatio();
            if (!Double.isFinite(p)
                    || p <= EPSILON
                    || p > 1.0 + EPSILON
                    || !isStrictlyPositive(gene.getAllocatedCpu())
                    || !isStrictlyPositive(gene.getAllocatedBandwidth())) {
                return DeadlineEvaluation.invalid(deadline);
            }
            timeBreakdown = offloadingTimeModel.evaluateRemote(
                    task,
                    candidate,
                    localCpu,
                    p,
                    gene.getAllocatedCpu(),
                    gene.getAllocatedBandwidth()
            );
        }

        double completion = timeBreakdown.getCompletionTimeSeconds();
        boolean deadlineRespected = deadline <= 0.0
                || completion <= deadline + EPSILON;
        boolean mobilitySustainable = candidate.getType() == NodeType.LOCAL
                || isRemoteCoverageSufficient(snapshot, task, candidate, completion);

        return DeadlineEvaluation.valid(
                completion,
                deadline,
                deadlineRespected,
                mobilitySustainable
        );
    }

    private boolean isRemoteCoverageSufficient(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate,
            double completionTimeSeconds
    ) {
        double coverageTimeSeconds;
        try {
            coverageTimeSeconds = coverageEstimator.estimateCoverageTimeSeconds(
                    snapshot,
                    task,
                    candidate
            );
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return isStrictlyPositive(coverageTimeSeconds)
                && completionTimeSeconds <= coverageTimeSeconds + EPSILON;
    }

    private NodeCandidate findCandidate(SystemSnapshot snapshot, String candidateId) {
        if (candidateId == null) {
            return null;
        }
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getCandidateId().equals(candidateId)) {
                return candidate;
            }
        }
        return null;
    }

    private VehicleSnapshot findVehicle(SystemSnapshot snapshot, String vehicleId) {
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            if (vehicle.getVehicleId().equals(vehicleId)) {
                return vehicle;
            }
        }
        return null;
    }

    private boolean isStrictlyPositive(double value) {
        return Double.isFinite(value) && value > EPSILON;
    }
}
