package ga.constraints;

import ga.operators.OffloadingRatioPolicy;
import model.genetic.Gene;
import model.mobility.CoverageEstimator;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.offloading.OffloadingTimeModel;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Catalogo lazy dei profili deadline-aware riutilizzabili nello snapshot.
 *
 * <p>Il catalogo non decide la strategia globale e non sostituisce il GA.
 * Memorizza soltanto risultati deterministici del repair per una combinazione
 * task-candidato-quota già esplorata: profilo a capacità massima, valutazione
 * temporale e minima scala comune di CPU e banda ammissibile.</p>
 *
 * <p>Le quote vengono ancora esplorate dalla policy limitata del repair. La
 * cache evita di ricalcolare le stesse formule durante figli e generazioni
 * successive dello stesso snapshot.</p>
 */
public final class DeadlineRepairCatalog {
    private static final double EPSILON = 1.0E-9;
    private static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05;
    private static final double MIN_RESOURCE_FRACTION = 0.05;

    private final SnapshotRepairContext context;
    private final CoverageEstimator coverageEstimator;
    private final OffloadingTimeModel offloadingTimeModel;
    private final OffloadingRatioPolicy offloadingRatioPolicy;
    private final DeadlineConstraintEvaluator deadlineConstraintEvaluator;
    private final Map<TaskCandidateKey, List<Double>> baseRatiosByTaskAndCandidate;
    private final Map<ProfileKey, DeadlineRepairProfile> profileByKey;

    public DeadlineRepairCatalog(
            SnapshotRepairContext context,
            CoverageEstimator coverageEstimator,
            OffloadingTimeModel offloadingTimeModel,
            OffloadingRatioPolicy offloadingRatioPolicy,
            DeadlineConstraintEvaluator deadlineConstraintEvaluator
    ) {
        this.context = Objects.requireNonNull(context, "context must not be null.");
        this.coverageEstimator = Objects.requireNonNull(
                coverageEstimator,
                "coverageEstimator must not be null."
        );
        this.offloadingTimeModel = Objects.requireNonNull(
                offloadingTimeModel,
                "offloadingTimeModel must not be null."
        );
        this.offloadingRatioPolicy = Objects.requireNonNull(
                offloadingRatioPolicy,
                "offloadingRatioPolicy must not be null."
        );
        this.deadlineConstraintEvaluator = Objects.requireNonNull(
                deadlineConstraintEvaluator,
                "deadlineConstraintEvaluator must not be null."
        );
        this.baseRatiosByTaskAndCandidate = new HashMap<>();
        this.profileByKey = new HashMap<>();
    }

    public SnapshotRepairContext getContext() {
        return context;
    }

    /**
     * Restituisce le quote considerate dal repair mantenendo lo stesso ordine
     * della policy precedente: quota preferita, quota bilanciata e griglia.
     */
    public List<Double> buildRatioCandidates(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double preferredRatio
    ) {
        Set<Double> ratios = new LinkedHashSet<>();
        ratios.add(clampRemoteRatio(preferredRatio));
        ratios.addAll(baseRatios(task, candidate, sourceVehicle));
        return new ArrayList<>(ratios);
    }

    /**
     * Restituisce il profilo precalcolato o lo calcola al primo utilizzo.
     */
    public DeadlineRepairProfile getProfile(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double ratio
    ) {
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");
        double normalizedRatio = clampRemoteRatio(ratio);
        ProfileKey key = new ProfileKey(
                task.getTaskId(),
                candidate.getCandidateId(),
                normalizedRatio
        );
        return profileByKey.computeIfAbsent(
                key,
                ignored -> computeProfile(
                        task,
                        candidate,
                        sourceVehicle,
                        normalizedRatio
                )
        );
    }

    private List<Double> baseRatios(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
        TaskCandidateKey key = new TaskCandidateKey(
                task.getTaskId(),
                candidate.getCandidateId()
        );
        return baseRatiosByTaskAndCandidate.computeIfAbsent(
                key,
                ignored -> computeBaseRatios(task, candidate, sourceVehicle)
        );
    }

    private List<Double> computeBaseRatios(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
        Set<Double> ratios = new LinkedHashSet<>();
        ratios.add(
                clampRemoteRatio(
                        offloadingRatioPolicy.balancedRemoteRatio(
                                task,
                                candidate,
                                sourceVehicle
                        )
                )
        );
        for (int step = 1; step <= 20; step++) {
            ratios.add(step * 0.05);
        }
        return List.copyOf(ratios);
    }

    private DeadlineRepairProfile computeProfile(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double ratio
    ) {
        Gene maxCapacityGene = createScaledRemoteGene(task, candidate, ratio, 1.0);
        DeadlineEvaluation maxCapacityEvaluation = deadlineConstraintEvaluator.evaluate(
                maxCapacityGene,
                task,
                context
        );
        Gene minimalFeasibleGene = findMinimalFeasibleResourceScale(
                task,
                candidate,
                sourceVehicle,
                ratio,
                maxCapacityGene,
                maxCapacityEvaluation
        );
        return new DeadlineRepairProfile(
                minimalFeasibleGene,
                maxCapacityGene,
                maxCapacityEvaluation
        );
    }

    /**
     * Calcola la minima scala comune di CPU e banda che rende ammissibile una
     * scelta remota per una quota data.
     *
     * <pre>
     * T_remote(q) = [p * input / Bmax
     *              + p * cycles / Fmax
     *              + output / Bmax] / q
     *              + tau_n
     * </pre>
     *
     * <p>Il calcolo è equivalente alla ricerca binaria rimossa nel livello 1,
     * ma viene memorizzato e riutilizzato nello snapshot corrente.</p>
     */
    private Gene findMinimalFeasibleResourceScale(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double ratio,
            Gene maxCapacityGene,
            DeadlineEvaluation maxCapacityEvaluation
    ) {
        if (candidate.getType() == NodeType.LOCAL
                || sourceVehicle == null
                || !isStrictlyPositive(candidate.getAvailableCpu())
                || !isStrictlyPositive(candidate.getAvailableBandwidth())) {
            return null;
        }

        double temporalLimit = estimateRemoteTemporalLimitSeconds(task, candidate);
        if (!isStrictlyPositive(temporalLimit)) {
            return null;
        }

        double localBranchTime = offloadingTimeModel.estimateLocalBranchTime(
                task,
                sourceVehicle,
                ratio
        );
        if (!Double.isFinite(localBranchTime)
                || localBranchTime > temporalLimit + EPSILON) {
            return null;
        }

        double propagationDelay = Math.max(
                0.0,
                candidate.getPropagationDelaySeconds()
        );
        double denominator = temporalLimit - propagationDelay;
        if (!isStrictlyPositive(denominator)) {
            return null;
        }

        double scaledRemoteTimeAtFullResources =
                safeDivide(
                        ratio * Math.max(0.0, task.getInputSizeBits())
                                + Math.max(0.0, task.getOutputSizeBits()),
                        candidate.getAvailableBandwidth()
                )
                + safeDivide(
                        ratio * Math.max(0.0, task.getCpuCycles()),
                        candidate.getAvailableCpu()
                );
        if (!Double.isFinite(scaledRemoteTimeAtFullResources)) {
            return null;
        }

        double requiredScale = scaledRemoteTimeAtFullResources / denominator;
        if (!Double.isFinite(requiredScale)
                || requiredScale > 1.0 + EPSILON) {
            return null;
        }

        Gene analyticalGene = createScaledRemoteGene(
                task,
                candidate,
                ratio,
                Math.max(MIN_RESOURCE_FRACTION, requiredScale)
        );
        if (deadlineConstraintEvaluator
                .evaluate(analyticalGene, task, context)
                .isAdmissible()) {
            return analyticalGene;
        }

        /* Protezione numerica per il bordo del vincolo. */
        if (maxCapacityEvaluation.isAdmissible()) {
            return maxCapacityGene;
        }
        return null;
    }

    private double estimateRemoteTemporalLimitSeconds(
            TaskInstance task,
            NodeCandidate candidate
    ) {
        double coverageTime = context.estimateCoverageTimeSeconds(
                task,
                candidate,
                coverageEstimator
        );
        if (!isStrictlyPositive(coverageTime)) {
            return 0.0;
        }
        double deadline = task.getDeadlineSeconds();
        if (!Double.isFinite(deadline) || deadline <= 0.0) {
            return coverageTime;
        }
        return Math.min(deadline, coverageTime);
    }

    private Gene createScaledRemoteGene(
            TaskInstance task,
            NodeCandidate candidate,
            double ratio,
            double resourceScale
    ) {
        double scale = clamp(resourceScale, MIN_RESOURCE_FRACTION, 1.0);
        return new Gene(
                task.getTaskId(),
                candidate.getCandidateId(),
                clampRemoteRatio(ratio),
                candidate.getAvailableCpu() * scale,
                candidate.getAvailableBandwidth() * scale
        );
    }

    private double clampRemoteRatio(double ratio) {
        return clamp(ratio, MIN_REMOTE_OFFLOADING_RATIO, 1.0);
    }

    private double safeDivide(double numerator, double denominator) {
        if (!Double.isFinite(numerator)
                || !Double.isFinite(denominator)
                || denominator <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        return numerator / denominator;
    }

    private boolean isStrictlyPositive(double value) {
        return Double.isFinite(value) && value > EPSILON;
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** Profilo memorizzato per una combinazione task-candidato-quota. */
    public static final class DeadlineRepairProfile {
        private final Gene minimalFeasibleGene;
        private final Gene maxCapacityGene;
        private final DeadlineEvaluation maxCapacityEvaluation;

        private DeadlineRepairProfile(
                Gene minimalFeasibleGene,
                Gene maxCapacityGene,
                DeadlineEvaluation maxCapacityEvaluation
        ) {
            this.minimalFeasibleGene = minimalFeasibleGene;
            this.maxCapacityGene = maxCapacityGene;
            this.maxCapacityEvaluation = maxCapacityEvaluation;
        }

        public Gene getMinimalFeasibleGene() {
            return minimalFeasibleGene;
        }

        public Gene getMaxCapacityGene() {
            return maxCapacityGene;
        }

        public DeadlineEvaluation getMaxCapacityEvaluation() {
            return maxCapacityEvaluation;
        }
    }

    private static final class TaskCandidateKey {
        private final String taskId;
        private final String candidateId;

        private TaskCandidateKey(String taskId, String candidateId) {
            this.taskId = taskId;
            this.candidateId = candidateId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TaskCandidateKey)) {
                return false;
            }
            TaskCandidateKey key = (TaskCandidateKey) other;
            return Objects.equals(taskId, key.taskId)
                    && Objects.equals(candidateId, key.candidateId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskId, candidateId);
        }
    }

    private static final class ProfileKey {
        private final String taskId;
        private final String candidateId;
        private final long ratioBits;

        private ProfileKey(String taskId, String candidateId, double ratio) {
            this.taskId = taskId;
            this.candidateId = candidateId;
            this.ratioBits = Double.doubleToLongBits(ratio);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProfileKey)) {
                return false;
            }
            ProfileKey key = (ProfileKey) other;
            return ratioBits == key.ratioBits
                    && Objects.equals(taskId, key.taskId)
                    && Objects.equals(candidateId, key.candidateId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskId, candidateId, ratioBits);
        }
    }
}
