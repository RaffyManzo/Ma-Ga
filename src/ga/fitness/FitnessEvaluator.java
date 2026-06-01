package ga.fitness;

import config.MaGaConfig;
import config.fitness.FitnessWeights;
import config.fitness.NormalizationConfig;
import config.fitness.PenaltyConfig;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import ga.fitness.breakdown.LocalResourceUsageBreakdown;
import ga.fitness.breakdown.MobilityPenaltyBreakdown;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.mobility.CoverageEstimator;
import model.mobility.MobilityLinkMetrics;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.offloading.OffloadingTimeBreakdown;
import model.offloading.OffloadingTimeModel;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Valuta un cromosoma MA-GA rispetto a uno snapshot del sistema.
 *
 * <p>La valutazione combina quattro famiglie di costo:</p>
 * <ul>
 *     <li>tempo massimo di completamento dei task;</li>
 *     <li>latenza comunicativa complessivamente introdotta dalle decisioni remote;</li>
 *     <li>rischio mobility-aware legato alla copertura e alla stabilità del link;</li>
 *     <li>penalità di vincolo e sovrauso risorse.</li>
 * </ul>
 *
 * <p>Il tempo di copertura e l'instabilità del collegamento vengono calcolati
 * tramite {@link CoverageEstimator}, così il modello non dipende da valori
 * precomputati dentro {@code NodeCandidate}.</p>
 */
public final class FitnessEvaluator {

    private static final double EPSILON = 1.0E-9;
    private static final double INVALID_SOLUTION_PENALTY = 1.0E9;

    private final MaGaConfig config;
    private final CoverageEstimator coverageEstimator;
    private final OffloadingTimeModel offloadingTimeModel;

    public FitnessEvaluator(MaGaConfig config) {
        this(
                config,
                new CoverageEstimator(
                        Objects.requireNonNull(
                                config,
                                "config must not be null."
                        ).getMobilityConfig()
                )
        );
    }

    public FitnessEvaluator(
            MaGaConfig config,
            CoverageEstimator coverageEstimator
    ) {
        this.config = Objects.requireNonNull(
                config,
                "config must not be null."
        );
        this.coverageEstimator = Objects.requireNonNull(
                coverageEstimator,
                "coverageEstimator must not be null."
        );
        this.offloadingTimeModel = new OffloadingTimeModel();
    }

    public double evaluate(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        return evaluateDetailed(chromosome, snapshot).getFitness();
    }

    public EvaluationBreakdown evaluateDetailed(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        Objects.requireNonNull(chromosome, "chromosome must not be null.");
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        List<TaskInstance> tasks = requireList(snapshot.getTasks(), "snapshot.tasks");
        List<VehicleSnapshot> vehicles = requireList(snapshot.getVehicles(), "snapshot.vehicles");
        List<NodeCandidate> candidates = requireList(snapshot.getCandidateNodes(), "snapshot.candidateNodes");
        List<Gene> genes = requireList(chromosome.getGenes(), "chromosome.genes");

        Map<String, TaskInstance> taskById = indexTasks(tasks);
        Map<String, VehicleSnapshot> vehicleById = indexVehicles(vehicles);
        Map<String, NodeCandidate> candidateById = indexCandidates(candidates);
        Map<String, Gene> geneByTaskId = indexGenes(genes);

        double invalidPenalty = computeCardinalityPenalty(tasks, genes);
        invalidPenalty += computeUnknownGeneTaskPenalty(geneByTaskId, taskById);

        Map<String, ExecutionNodeResourceUsageBreakdown> cpuUsageByExecutionNode =
                initializeExecutionNodeCpuUsage(candidates);
        Map<String, LinkBandwidthUsageBreakdown> bandwidthUsageByCandidate =
                initializeLinkBandwidthUsage(candidates);
        Map<String, LocalResourceUsageBreakdown> localUsageByVehicle =
                initializeLocalUsage(vehicles);

        List<GeneEvaluationBreakdown> geneBreakdowns = new ArrayList<>();

        double completionTime = 0.0;
        double communicationLatencySum = 0.0;
        double mobilityPenalty = 0.0;
        double constraintPenalty = 0.0;

        for (TaskInstance task : tasks) {
            Gene gene = geneByTaskId.get(task.getTaskId());
            if (gene == null) {
                invalidPenalty += INVALID_SOLUTION_PENALTY;
                continue;
            }

            NodeCandidate candidate = candidateById.get(gene.getSelectedCandidateId());
            VehicleSnapshot sourceVehicle = vehicleById.get(task.getSourceVehicleId());

            if (candidate == null || sourceVehicle == null) {
                invalidPenalty += INVALID_SOLUTION_PENALTY;
                continue;
            }

            if (!candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
                invalidPenalty += INVALID_SOLUTION_PENALTY;
                continue;
            }

            GeneEvaluationBreakdown geneBreakdown = evaluateGene(
                    snapshot,
                    task,
                    gene,
                    candidate,
                    sourceVehicle
            );

            geneBreakdowns.add(geneBreakdown);

            completionTime = Math.max(
                    completionTime,
                    geneBreakdown.getCompletionTimeSeconds()
            );
            communicationLatencySum += geneBreakdown.getCommunicationLatencySeconds();
            mobilityPenalty += geneBreakdown.getMobilityPenalty();
            constraintPenalty += geneBreakdown.getConstraintPenalty();

            if (candidate.getType() != NodeType.LOCAL) {
                ExecutionNodeResourceUsageBreakdown cpuUsage =
                        cpuUsageByExecutionNode.get(candidate.getExecutionNodeId());
                if (cpuUsage != null) {
                    cpuUsage.addCpu(geneBreakdown.getAllocatedCpu());
                }

                LinkBandwidthUsageBreakdown bandwidthUsage =
                        bandwidthUsageByCandidate.get(candidate.getCandidateId());
                if (bandwidthUsage != null) {
                    bandwidthUsage.addBandwidth(geneBreakdown.getAllocatedBandwidth());
                }
            }

            LocalResourceUsageBreakdown localUsage =
                    localUsageByVehicle.get(task.getSourceVehicleId());
            if (localUsage != null) {
                localUsage.addLocalWorkload(
                        geneBreakdown.getLocalCpuCycles(),
                        geneBreakdown.getLocalExecutionTimeSeconds()
                );
            }
        }

        /*
         * La formalizzazione definisce L(C) come somma delle latenze
         * comunicative dei singoli task, non come media.
         */
        double totalCommunicationLatency = communicationLatencySum;

        double resourcePenalty = computeResourcePenalty(
                cpuUsageByExecutionNode,
                bandwidthUsageByCandidate
        );

        double totalResourceAndConstraintPenalty =
                resourcePenalty + constraintPenalty + invalidPenalty;

        FitnessWeights weights = config.getFitnessWeights();
        NormalizationConfig normalization = config.getNormalizationConfig();

        double normalizedCompletionTime = completionTime / normalization.getTRef();
        double normalizedCommunicationLatency =
                totalCommunicationLatency / normalization.getLRef();
        double normalizedMobilityPenalty = mobilityPenalty / normalization.getPmobRef();
        double normalizedResourcePenalty =
                totalResourceAndConstraintPenalty / normalization.getPresRef();

        double fitness =
                weights.getWT() * normalizedCompletionTime
                        + weights.getWL() * normalizedCommunicationLatency
                        + weights.getWM() * normalizedMobilityPenalty
                        + weights.getWR() * normalizedResourcePenalty;

        return new EvaluationBreakdown(
                fitness,
                completionTime,
                totalCommunicationLatency,
                mobilityPenalty,
                totalResourceAndConstraintPenalty,
                normalizedCompletionTime,
                normalizedCommunicationLatency,
                normalizedMobilityPenalty,
                normalizedResourcePenalty,
                geneBreakdowns,
                new ArrayList<>(cpuUsageByExecutionNode.values()),
                new ArrayList<>(bandwidthUsageByCandidate.values()),
                new ArrayList<>(localUsageByVehicle.values())
        );
    }

    private GeneEvaluationBreakdown evaluateGene(
            SystemSnapshot snapshot,
            TaskInstance task,
            Gene gene,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
        PenaltyConfig penalties = config.getPenaltyConfig();
        double constraintPenalty = 0.0;

        double p = gene.getOffloadingRatio();
        if (!Double.isFinite(p) || p < 0.0 || p > 1.0) {
            constraintPenalty += INVALID_SOLUTION_PENALTY;
            p = clamp(p, 0.0, 1.0);
        }

        double localCpu = sourceVehicle.getLocalCpu();
        if (!isStrictlyPositive(localCpu)) {
            constraintPenalty += INVALID_SOLUTION_PENALTY;
            localCpu = EPSILON;
        }

        MobilityLinkMetrics mobilityLinkMetrics = coverageEstimator.estimateLinkMetrics(
                snapshot,
                task,
                candidate
        );
        double coverageTimeSeconds = mobilityLinkMetrics.getCoverageTimeSeconds();

        if (candidate.getType() == NodeType.LOCAL) {
            OffloadingTimeBreakdown timeBreakdown =
                    offloadingTimeModel.evaluateLocal(task, localCpu);

            if (Math.abs(p) > EPSILON) {
                constraintPenalty += Math.abs(p) * INVALID_SOLUTION_PENALTY;
            }

            double deadlinePenalty = computeDeadlinePenalty(
                    timeBreakdown.getCompletionTimeSeconds(),
                    task.getDeadlineSeconds(),
                    penalties
            );

            return new GeneEvaluationBreakdown(
                    task.getTaskId(),
                    task.getSourceVehicleId(),
                    candidate.getCandidateId(),
                    candidate.getExecutionNodeId(),
                    candidate.getType(),
                    DecisionType.LOCAL_EXECUTION,
                    0.0,
                    localCpu,
                    0.0,
                    timeBreakdown.getLocalCpuCycles(),
                    timeBreakdown.getLocalExecutionTimeSeconds(),
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    timeBreakdown.getCompletionTimeSeconds(),
                    0.0,
                    0.0,
                    constraintPenalty + deadlinePenalty,
                    task.getDeadlineSeconds(),
                    isDeadlineRespected(
                            timeBreakdown.getCompletionTimeSeconds(),
                            task.getDeadlineSeconds()
                    ),
                    coverageTimeSeconds,
                    true,
                    MobilityPenaltyBreakdown.zero(mobilityLinkMetrics)
            );
        }

        if (p <= EPSILON) {
            constraintPenalty += INVALID_SOLUTION_PENALTY;
            p = EPSILON;
        }

        double allocatedCpu = gene.getAllocatedCpu();
        double allocatedBandwidth = gene.getAllocatedBandwidth();

        if (!isStrictlyPositive(allocatedCpu)) {
            constraintPenalty += INVALID_SOLUTION_PENALTY;
            allocatedCpu = EPSILON;
        }

        if (!isStrictlyPositive(allocatedBandwidth)) {
            constraintPenalty += INVALID_SOLUTION_PENALTY;
            allocatedBandwidth = EPSILON;
        }

        OffloadingTimeBreakdown timeBreakdown = offloadingTimeModel.evaluateRemote(
                task,
                candidate,
                localCpu,
                p,
                allocatedCpu,
                allocatedBandwidth
        );

        MobilityPenaltyBreakdown mobilityBreakdown = computeMobilityPenaltyBreakdown(
                candidate,
                mobilityLinkMetrics,
                timeBreakdown.getCompletionTimeSeconds(),
                penalties
        );
        double mobilityPenalty = mobilityBreakdown.getTotalMobilityPenalty();

        double deadlinePenalty = computeDeadlinePenalty(
                timeBreakdown.getCompletionTimeSeconds(),
                task.getDeadlineSeconds(),
                penalties
        );

        DecisionType decisionType = p >= 1.0 - EPSILON
                ? DecisionType.FULL_OFFLOADING
                : DecisionType.PARTIAL_OFFLOADING;

        boolean coverageSufficient = coverageTimeSeconds > 0.0
                && coverageTimeSeconds >= timeBreakdown.getCompletionTimeSeconds();

        return new GeneEvaluationBreakdown(
                task.getTaskId(),
                task.getSourceVehicleId(),
                candidate.getCandidateId(),
                candidate.getExecutionNodeId(),
                candidate.getType(),
                decisionType,
                p,
                allocatedCpu,
                allocatedBandwidth,
                timeBreakdown.getLocalCpuCycles(),
                timeBreakdown.getLocalExecutionTimeSeconds(),
                timeBreakdown.getUploadTimeSeconds(),
                timeBreakdown.getRemoteExecutionTimeSeconds(),
                timeBreakdown.getDownloadTimeSeconds(),
                timeBreakdown.getBaseLatencySeconds(),
                timeBreakdown.getRemotePartTimeSeconds(),
                timeBreakdown.getCompletionTimeSeconds(),
                timeBreakdown.getCommunicationLatencySeconds(),
                mobilityPenalty,
                constraintPenalty + deadlinePenalty,
                task.getDeadlineSeconds(),
                isDeadlineRespected(
                        timeBreakdown.getCompletionTimeSeconds(),
                        task.getDeadlineSeconds()
                ),
                coverageTimeSeconds,
                coverageSufficient,
                mobilityBreakdown
        );
    }

    private Map<String, ExecutionNodeResourceUsageBreakdown>
    initializeExecutionNodeCpuUsage(List<NodeCandidate> candidates) {
        Map<String, ExecutionNodeResourceUsageBreakdown> result = new HashMap<>();

        for (NodeCandidate candidate : candidates) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }

            result.putIfAbsent(
                    candidate.getExecutionNodeId(),
                    new ExecutionNodeResourceUsageBreakdown(
                            candidate.getExecutionNodeId(),
                            candidate.getType(),
                            candidate.getAvailableCpu()
                    )
            );
        }

        return result;
    }

    private Map<String, LinkBandwidthUsageBreakdown>
    initializeLinkBandwidthUsage(List<NodeCandidate> candidates) {
        Map<String, LinkBandwidthUsageBreakdown> result = new HashMap<>();

        for (NodeCandidate candidate : candidates) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }

            result.put(
                    candidate.getCandidateId(),
                    new LinkBandwidthUsageBreakdown(
                            candidate.getCandidateId(),
                            candidate.getSourceVehicleId(),
                            candidate.getExecutionNodeId(),
                            candidate.getType(),
                            candidate.getAvailableBandwidth()
                    )
            );
        }

        return result;
    }

    private Map<String, LocalResourceUsageBreakdown>
    initializeLocalUsage(List<VehicleSnapshot> vehicles) {
        Map<String, LocalResourceUsageBreakdown> result = new HashMap<>();

        for (VehicleSnapshot vehicle : vehicles) {
            result.put(
                    vehicle.getVehicleId(),
                    new LocalResourceUsageBreakdown(
                            vehicle.getVehicleId(),
                            vehicle.getLocalCpu()
                    )
            );
        }

        return result;
    }

    private double computeResourcePenalty(
            Map<String, ExecutionNodeResourceUsageBreakdown> cpuUsageByExecutionNode,
            Map<String, LinkBandwidthUsageBreakdown> bandwidthUsageByCandidate
    ) {
        PenaltyConfig penalties = config.getPenaltyConfig();
        double totalPenalty = 0.0;

        for (ExecutionNodeResourceUsageBreakdown usage
                : cpuUsageByExecutionNode.values()) {
            totalPenalty += penalties.getCpuOveruseWeight()
                    * usage.getCpuOverflowRatio();
        }

        for (LinkBandwidthUsageBreakdown usage
                : bandwidthUsageByCandidate.values()) {
            totalPenalty += penalties.getBandwidthOveruseWeight()
                    * usage.getBandwidthOverflowRatio();
        }

        return totalPenalty;
    }

    /**
     * Calcola la penalità mobility-aware e conserva il breakdown diagnostico.
     */
    private MobilityPenaltyBreakdown computeMobilityPenaltyBreakdown(
            NodeCandidate candidate,
            MobilityLinkMetrics linkMetrics,
            double completionTimeSeconds,
            PenaltyConfig penalties
    ) {
        if (candidate.getType() == NodeType.LOCAL) {
            return MobilityPenaltyBreakdown.zero(linkMetrics);
        }

        if (!isStrictlyPositive(completionTimeSeconds)) {
            return MobilityPenaltyBreakdown.zero(linkMetrics);
        }

        double coverageTimeSeconds = linkMetrics.getCoverageTimeSeconds();
        double linkInstability = linkMetrics.getLinkInstability();
        double coverageRisk;
        double handoverRisk;

        if (!isStrictlyPositive(coverageTimeSeconds)) {
            coverageRisk = 1.0;
            handoverRisk = 1.0;
        } else {
            coverageRisk = Math.max(
                    0.0,
                    1.0 - coverageTimeSeconds / completionTimeSeconds
            );
            handoverRisk = Math.min(
                    1.0,
                    completionTimeSeconds / coverageTimeSeconds
            );
        }

        return new MobilityPenaltyBreakdown(
                linkMetrics,
                coverageRisk,
                linkInstability,
                handoverRisk,
                penalties.getCoverageRiskWeight() * coverageRisk,
                penalties.getLinkInstabilityWeight() * linkInstability,
                penalties.getHandoverRiskWeight() * handoverRisk
        );
    }

    private double computeDeadlinePenalty(
            double completionTimeSeconds,
            double deadlineSeconds,
            PenaltyConfig penalties
    ) {
        if (!isStrictlyPositive(deadlineSeconds)) {
            return 0.0;
        }

        double violation = completionTimeSeconds - deadlineSeconds;
        if (violation <= 0.0) {
            return 0.0;
        }

        return penalties.getDeadlineViolationWeight()
                * safeDivide(violation, deadlineSeconds);
    }

    private boolean isDeadlineRespected(
            double completionTimeSeconds,
            double deadlineSeconds
    ) {
        return deadlineSeconds <= 0.0
                || completionTimeSeconds <= deadlineSeconds;
    }

    private double computeCardinalityPenalty(
            List<TaskInstance> tasks,
            List<Gene> genes
    ) {
        if (tasks.size() == genes.size()) {
            return 0.0;
        }

        return INVALID_SOLUTION_PENALTY
                * Math.abs(tasks.size() - genes.size());
    }

    private double computeUnknownGeneTaskPenalty(
            Map<String, Gene> geneByTaskId,
            Map<String, TaskInstance> taskById
    ) {
        double penalty = 0.0;

        for (String geneTaskId : geneByTaskId.keySet()) {
            if (!taskById.containsKey(geneTaskId)) {
                penalty += INVALID_SOLUTION_PENALTY;
            }
        }

        return penalty;
    }

    private Map<String, TaskInstance> indexTasks(List<TaskInstance> tasks) {
        Map<String, TaskInstance> result = new HashMap<>();
        for (TaskInstance task : tasks) {
            result.put(task.getTaskId(), task);
        }
        return result;
    }

    private Map<String, VehicleSnapshot> indexVehicles(
            List<VehicleSnapshot> vehicles
    ) {
        Map<String, VehicleSnapshot> result = new HashMap<>();
        for (VehicleSnapshot vehicle : vehicles) {
            result.put(vehicle.getVehicleId(), vehicle);
        }
        return result;
    }

    private Map<String, NodeCandidate> indexCandidates(
            List<NodeCandidate> candidates
    ) {
        Map<String, NodeCandidate> result = new HashMap<>();
        for (NodeCandidate candidate : candidates) {
            result.put(candidate.getCandidateId(), candidate);
        }
        return result;
    }

    private Map<String, Gene> indexGenes(List<Gene> genes) {
        Map<String, Gene> result = new HashMap<>();
        for (Gene gene : genes) {
            result.putIfAbsent(gene.getTaskId(), gene);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> requireList(List<?> list, String name) {
        if (list == null) {
            throw new IllegalArgumentException(name + " must not be null.");
        }
        return (List<T>) list;
    }

    private boolean isStrictlyPositive(double value) {
        return Double.isFinite(value) && value > EPSILON;
    }

    private double safeDivide(double numerator, double denominator) {
        if (!Double.isFinite(numerator)) {
            return INVALID_SOLUTION_PENALTY;
        }

        if (!isStrictlyPositive(denominator)) {
            return INVALID_SOLUTION_PENALTY;
        }

        return numerator / denominator;
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }
}
