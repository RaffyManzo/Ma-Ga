package ga.fitness;

import config.MaGaConfig;
import config.fitness.FitnessWeights;
import config.fitness.NormalizationConfig;
import config.fitness.PenaltyConfig;
import ga.fitness.breakdown.BandwidthPoolUsageBreakdown;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import ga.fitness.breakdown.LocalResourceUsageBreakdown;
import ga.fitness.breakdown.MobilityPenaltyBreakdown;
import ga.fitness.local.LocalCpuContentionEvaluator;
import ga.fitness.local.LocalCpuContentionEvaluator.Evaluation;
import ga.fitness.local.LocalCpuContentionEvaluator.TaskResult;
import model.bandwidth.BandwidthPoolResolver;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.mobility.CoverageEstimator;
import model.mobility.MobilityLinkMetrics;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.offloading.OffloadingTimeBreakdown;
import model.offloading.OffloadingTimeModel;
import model.snapshot.BandwidthPoolSnapshot;
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
 *
 * <ul>
 *     <li>tempo massimo di completamento dei task;</li>
 *     <li>latenza comunicativa complessiva delle decisioni remote;</li>
 *     <li>rischio mobility-aware legato a copertura e stabilità del link;</li>
 *     <li>penalità di vincolo e sovrauso delle risorse.</li>
 * </ul>
 *
 * <p>La banda viene osservata su due livelli gerarchici distinti:</p>
 *
 * <ul>
 *     <li>{@code candidateId}: limite del singolo link source-aware;</li>
 *     <li>{@code poolId}: limite radio condiviso della RSU o del V2V.</li>
 * </ul>
 *
 * <p>Il pool non sostituisce il limite del link: lo contiene.</p>
 */
public final class FitnessEvaluator {
    private static final double EPSILON = 1.0E-9;
    private static final double INVALID_SOLUTION_PENALTY = 1.0E9;
    private static final double HARD_DEADLINE_VIOLATION_PENALTY = 1.0E12;

    private final MaGaConfig config;
    private final CoverageEstimator coverageEstimator;
    private final OffloadingTimeModel offloadingTimeModel;
    private final BandwidthPoolResolver bandwidthPoolResolver;
    private final LocalCpuContentionEvaluator localCpuContentionEvaluator;

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
        this.bandwidthPoolResolver = new BandwidthPoolResolver();
        this.localCpuContentionEvaluator =
                new LocalCpuContentionEvaluator();
    }

    /** Costruisce gli indici immutabili riutilizzabili nello stesso snapshot. */
    public FitnessEvaluationContext createContext(SystemSnapshot snapshot) {
        return FitnessEvaluationContext.from(snapshot);
    }

    public double evaluate(Chromosome chromosome, SystemSnapshot snapshot) {
        return evaluate(chromosome, createContext(snapshot));
    }

    /** Valutazione che riusa un contesto già costruito per lo snapshot. */
    public double evaluate(
            Chromosome chromosome,
            FitnessEvaluationContext context
    ) {
        return evaluateDetailed(chromosome, context).getFitness();
    }

    public EvaluationBreakdown evaluateDetailed(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        return evaluateDetailed(chromosome, createContext(snapshot));
    }

    /** Valutazione dettagliata con indici snapshot-scoped riutilizzabili. */
    public EvaluationBreakdown evaluateDetailed(
            Chromosome chromosome,
            FitnessEvaluationContext context
    ) {
        Objects.requireNonNull(
                chromosome,
                "chromosome must not be null."
        );
        Objects.requireNonNull(context, "context must not be null.");

        SystemSnapshot snapshot = context.getSnapshot();
        List<TaskInstance> tasks = context.getTasks();
        List<VehicleSnapshot> vehicles = context.getVehicles();
        List<NodeCandidate> candidates = context.getCandidates();
        List<Gene> genes = requireList(
                chromosome.getGenes(),
                "chromosome.genes"
        );

        Map<String, TaskInstance> taskById = context.getTaskById();
        Map<String, VehicleSnapshot> vehicleById = context.getVehicleById();
        Map<String, NodeCandidate> candidateById = context.getCandidateById();
        Map<String, Gene> geneByTaskId = indexGenes(genes);

        double invalidPenalty = computeCardinalityPenalty(tasks, genes);
        invalidPenalty += computeUnknownGeneTaskPenalty(geneByTaskId, taskById);

        Map<String, ExecutionNodeResourceUsageBreakdown>
                cpuUsageByExecutionNode = initializeExecutionNodeCpuUsage(
                        candidates
                );
        Map<String, LinkBandwidthUsageBreakdown>
                bandwidthUsageByCandidate = initializeLinkBandwidthUsage(
                        candidates
                );
        Map<String, BandwidthPoolUsageBreakdown>
                bandwidthUsageByPool = initializeBandwidthPoolUsage(snapshot);
        Map<String, LocalResourceUsageBreakdown>
                localUsageByVehicle = initializeLocalUsage(vehicles);
        Evaluation localContention = localCpuContentionEvaluator.evaluate(
                tasks,
                geneByTaskId,
                candidateById,
                vehicleById
        );

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

            NodeCandidate candidate = candidateById.get(
                    gene.getSelectedCandidateId()
            );
            VehicleSnapshot sourceVehicle = vehicleById.get(
                    task.getSourceVehicleId()
            );

            if (candidate == null || sourceVehicle == null) {
                invalidPenalty += INVALID_SOLUTION_PENALTY;
                continue;
            }
            if (!candidate.isValidForSourceVehicle(
                    task.getSourceVehicleId()
            )) {
                invalidPenalty += INVALID_SOLUTION_PENALTY;
                continue;
            }

            GeneEvaluationBreakdown geneBreakdown = evaluateGene(
                    snapshot,
                    task,
                    gene,
                    candidate,
                    sourceVehicle,
                    localContention.getTaskResult(task.getTaskId())
            );
            geneBreakdowns.add(geneBreakdown);

            completionTime = Math.max(
                    completionTime,
                    geneBreakdown.getCompletionTimeSeconds()
            );
            communicationLatencySum +=
                    geneBreakdown.getCommunicationLatencySeconds();
            mobilityPenalty += geneBreakdown.getMobilityPenalty();
            constraintPenalty += geneBreakdown.getConstraintPenalty();

            if (candidate.getType() != NodeType.LOCAL) {
                ExecutionNodeResourceUsageBreakdown cpuUsage =
                        cpuUsageByExecutionNode.get(
                                candidate.getExecutionNodeId()
                        );
                if (cpuUsage != null) {
                    cpuUsage.addCpu(geneBreakdown.getAllocatedCpu());
                }

                LinkBandwidthUsageBreakdown linkUsage =
                        bandwidthUsageByCandidate.get(
                                candidate.getCandidateId()
                        );
                if (linkUsage != null) {
                    linkUsage.addBandwidth(
                            geneBreakdown.getAllocatedBandwidth()
                    );
                }

                try {
                    BandwidthPoolSnapshot pool = bandwidthPoolResolver.resolve(
                            snapshot,
                            candidate
                    );
                    BandwidthPoolUsageBreakdown poolUsage =
                            bandwidthUsageByPool.get(pool.getPoolId());
                    if (poolUsage == null) {
                        invalidPenalty += INVALID_SOLUTION_PENALTY;
                    } else {
                        poolUsage.addBandwidth(
                                geneBreakdown.getAllocatedBandwidth()
                        );
                    }
                } catch (IllegalArgumentException ex) {
                    invalidPenalty += INVALID_SOLUTION_PENALTY;
                }
            }

            LocalResourceUsageBreakdown localUsage = localUsageByVehicle.get(
                    task.getSourceVehicleId()
            );
            if (localUsage != null
                    && geneBreakdown.getLocalCpuCycles() > EPSILON) {
                TaskResult taskContention =
                        localContention.getTaskResult(task.getTaskId());
                double demandRatio = taskContention == null
                        ? 0.0
                        : taskContention.getDemandRatio();

                localUsage.addLocalWorkload(
                        geneBreakdown.getLocalCpuCycles(),
                        geneBreakdown
                                .getIndependentLocalExecutionTimeSeconds(),
                        geneBreakdown.getLocalExecutionTimeSeconds(),
                        demandRatio,
                        taskContention == null
                                || taskContention.isDeadlineRespected()
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
                bandwidthUsageByCandidate,
                bandwidthUsageByPool,
                localUsageByVehicle
        );
        double totalResourceAndConstraintPenalty =
                resourcePenalty + constraintPenalty + invalidPenalty;
        double hardDeadlinePenalty = computeHardDeadlinePenalty(
                geneBreakdowns
        );

        FitnessWeights weights = config.getFitnessWeights();
        NormalizationConfig normalization = config.getNormalizationConfig();

        double normalizedCompletionTime =
                completionTime / normalization.getTRef();
        double normalizedCommunicationLatency =
                totalCommunicationLatency / normalization.getLRef();
        double normalizedMobilityPenalty =
                mobilityPenalty / normalization.getPmobRef();
        double normalizedResourcePenalty =
                totalResourceAndConstraintPenalty / normalization.getPresRef();

        double fitness = hardDeadlinePenalty
                + weights.getWT() * normalizedCompletionTime
                + weights.getWL() * normalizedCommunicationLatency
                + weights.getWM() * normalizedMobilityPenalty
                + weights.getWR() * normalizedResourcePenalty;

        /*
         * EvaluationBreakdown conserva il breakdown storico per-link per
         * compatibilità. La diagnostica dei pool viene stampata dal printer
         * gerarchico leggendo snapshot e cromosoma finale.
         */
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
            VehicleSnapshot sourceVehicle,
            TaskResult localContention
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

        MobilityLinkMetrics mobilityLinkMetrics =
                coverageEstimator.estimateLinkMetrics(
                        snapshot,
                        task,
                        candidate
                );
        double coverageTimeSeconds =
                mobilityLinkMetrics.getCoverageTimeSeconds();

        if (candidate.getType() == NodeType.LOCAL) {
            OffloadingTimeBreakdown timeBreakdown =
                    offloadingTimeModel.evaluateLocal(task, localCpu);

            if (Math.abs(p) > EPSILON) {
                constraintPenalty += Math.abs(p)
                        * INVALID_SOLUTION_PENALTY;
            }

            double independentLocalTime =
                    timeBreakdown.getLocalExecutionTimeSeconds();
            double contendedLocalTime = resolveContendedLocalTime(
                    localContention,
                    independentLocalTime
            );
            double completionTime = contendedLocalTime;
            double deadlinePenalty = computeDeadlinePenalty(
                    completionTime,
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
                    independentLocalTime,
                    contendedLocalTime,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    completionTime,
                    0.0,
                    0.0,
                    constraintPenalty + deadlinePenalty,
                    task.getDeadlineSeconds(),
                    isDeadlineRespected(
                            completionTime,
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

        OffloadingTimeBreakdown timeBreakdown =
                offloadingTimeModel.evaluateRemote(
                        task,
                        candidate,
                        localCpu,
                        p,
                        allocatedCpu,
                        allocatedBandwidth
                );

        double independentLocalTime =
                timeBreakdown.getLocalExecutionTimeSeconds();
        double contendedLocalTime = p >= 1.0 - EPSILON
                ? 0.0
                : resolveContendedLocalTime(
                        localContention,
                        independentLocalTime
                );
        double completionTime = p >= 1.0 - EPSILON
                ? timeBreakdown.getRemotePartTimeSeconds()
                : Math.max(
                        contendedLocalTime,
                        timeBreakdown.getRemotePartTimeSeconds()
                );

        MobilityPenaltyBreakdown mobilityBreakdown =
                computeMobilityPenaltyBreakdown(
                        candidate,
                        mobilityLinkMetrics,
                        completionTime,
                        penalties
                );

        double mobilityPenalty = mobilityBreakdown.getTotalMobilityPenalty();
        double deadlinePenalty = computeDeadlinePenalty(
                completionTime,
                task.getDeadlineSeconds(),
                penalties
        );
        DecisionType decisionType = p >= 1.0 - EPSILON
                ? DecisionType.FULL_OFFLOADING
                : DecisionType.PARTIAL_OFFLOADING;
        boolean coverageSufficient = coverageTimeSeconds > 0.0
                && coverageTimeSeconds >= completionTime;

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
                independentLocalTime,
                contendedLocalTime,
                timeBreakdown.getUploadTimeSeconds(),
                timeBreakdown.getRemoteExecutionTimeSeconds(),
                timeBreakdown.getDownloadTimeSeconds(),
                timeBreakdown.getPropagationDelaySeconds(),
                timeBreakdown.getRemotePartTimeSeconds(),
                completionTime,
                timeBreakdown.getCommunicationLatencySeconds(),
                mobilityPenalty,
                constraintPenalty + deadlinePenalty,
                task.getDeadlineSeconds(),
                isDeadlineRespected(
                        completionTime,
                        task.getDeadlineSeconds()
                ),
                coverageTimeSeconds,
                coverageSufficient,
                mobilityBreakdown
        );
    }

    private double resolveContendedLocalTime(
            TaskResult localContention,
            double independentLocalTime
    ) {
        if (localContention == null) {
            return independentLocalTime;
        }

        double contended = localContention
                .getContendedCompletionTimeSeconds();
        if (!Double.isFinite(contended)
                || contended < independentLocalTime) {
            return independentLocalTime;
        }
        return contended;
    }

    private Map<String, ExecutionNodeResourceUsageBreakdown>
    initializeExecutionNodeCpuUsage(List<NodeCandidate> candidates) {
        Map<String, ExecutionNodeResourceUsageBreakdown> result =
                new HashMap<>();

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

    private Map<String, BandwidthPoolUsageBreakdown>
    initializeBandwidthPoolUsage(SystemSnapshot snapshot) {
        Map<String, BandwidthPoolUsageBreakdown> result = new HashMap<>();

        for (BandwidthPoolSnapshot pool : snapshot.getBandwidthPools()) {
            result.put(
                    pool.getPoolId(),
                    new BandwidthPoolUsageBreakdown(
                            pool.getPoolId(),
                            pool.getPoolType(),
                            pool.getAvailableBandwidth()
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
            Map<String, ExecutionNodeResourceUsageBreakdown>
                    cpuUsageByExecutionNode,
            Map<String, LinkBandwidthUsageBreakdown>
                    bandwidthUsageByCandidate,
            Map<String, BandwidthPoolUsageBreakdown>
                    bandwidthUsageByPool,
            Map<String, LocalResourceUsageBreakdown>
                    localUsageByVehicle
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

        for (BandwidthPoolUsageBreakdown usage
                : bandwidthUsageByPool.values()) {
            totalPenalty += penalties.getBandwidthOveruseWeight()
                    * usage.getBandwidthOverflowRatio();
        }

        for (LocalResourceUsageBreakdown usage
                : localUsageByVehicle.values()) {
            totalPenalty += penalties.getCpuOveruseWeight()
                    * usage.getCpuOverflowRatio();
        }

        return totalPenalty;
    }

    /** Calcola la penalità mobility-aware e conserva il breakdown diagnostico. */
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

    /**
     * Penalità rigida applicata direttamente alla fitness.
     *
     * <p>La parte costante rende ogni violazione nettamente peggiore rispetto
     * a una strategia interamente ammissibile. La parte proporzionale mantiene
     * un ordinamento utile anche quando tutte le alternative sono degradate.</p>
     */
    private double computeHardDeadlinePenalty(
            List<GeneEvaluationBreakdown> geneBreakdowns
    ) {
        double penalty = 0.0;

        for (GeneEvaluationBreakdown gene : geneBreakdowns) {
            if (gene.isDeadlineRespected()) {
                continue;
            }

            double violation = Math.max(
                    0.0,
                    gene.getCompletionTimeSeconds()
                            - gene.getDeadlineSeconds()
            );
            double violationRatio = isStrictlyPositive(
                    gene.getDeadlineSeconds()
            )
                    ? safeDivide(violation, gene.getDeadlineSeconds())
                    : 0.0;

            penalty += HARD_DEADLINE_VIOLATION_PENALTY + violationRatio;
        }
        return penalty;
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

    private Map<String, Gene> indexGenes(List<Gene> genes) {
        Map<String, Gene> result = new HashMap<>();
        for (Gene gene : genes) {
            result.putIfAbsent(gene.getTaskId(), gene);
        }
        return result;
    }

    private <T> List<T> requireList(List<T> list, String name) {
        if (list == null) {
            throw new IllegalArgumentException(name + " must not be null.");
        }
        return list;
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
