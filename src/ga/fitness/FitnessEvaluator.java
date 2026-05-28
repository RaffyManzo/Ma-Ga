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
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.mobility.CoverageEstimator;
import model.node.NodeCandidate;
import model.node.NodeType;
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
 * Usa CoverageEstimator per stimare il tempo di copertura. Il tempo di
 * copertura non viene più letto direttamente da NodeCandidate.
 */
public final class FitnessEvaluator {

    private static final double EPSILON = 1.0E-9;
    private static final double INVALID_SOLUTION_PENALTY = 1.0E9;

    private final MaGaConfig config;
    private final CoverageEstimator coverageEstimator;

    /**
     * Costruisce il valutatore usando la configurazione MA-GA.
     *
     * @param config configurazione complessiva del MA-GA
     */
    public FitnessEvaluator(MaGaConfig config) {
        this(
                config,
                new CoverageEstimator(
                        Objects.requireNonNull(config, "config must not be null.")
                                .getMobilityConfig()
                )
        );
    }

    /**
     * Costruisce il valutatore usando uno stimatore di copertura esplicito.
     *
     * @param config configurazione complessiva del MA-GA
     * @param coverageEstimator stimatore del tempo di copertura
     */
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
    }

    /**
     * Calcola solo il valore scalare della fitness.
     *
     * @param chromosome cromosoma da valutare
     * @param snapshot snapshot corrente
     * @return valore finale della fitness
     */
    public double evaluate(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        return evaluateDetailed(chromosome, snapshot).getFitness();
    }

    /**
     * Calcola la valutazione dettagliata di un cromosoma.
     *
     * @param chromosome cromosoma da valutare
     * @param snapshot snapshot corrente
     * @return breakdown completo della valutazione
     */
    public EvaluationBreakdown evaluateDetailed(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        Objects.requireNonNull(chromosome, "chromosome must not be null.");
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        List<TaskInstance> tasks = requireList(
                snapshot.getTasks(),
                "snapshot.tasks"
        );

        List<VehicleSnapshot> vehicles = requireList(
                snapshot.getVehicles(),
                "snapshot.vehicles"
        );

        List<NodeCandidate> candidates = requireList(
                snapshot.getCandidateNodes(),
                "snapshot.candidateNodes"
        );

        List<Gene> genes = requireList(
                chromosome.getGenes(),
                "chromosome.genes"
        );

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

            GeneEvaluationBreakdown geneBreakdown =
                    evaluateGene(snapshot, task, gene, candidate, sourceVehicle);

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

        double averageCommunicationLatency =
                tasks.isEmpty() ? 0.0 : communicationLatencySum / tasks.size();

        double resourcePenalty = computeResourcePenalty(
                cpuUsageByExecutionNode,
                bandwidthUsageByCandidate
        );

        double totalResourceAndConstraintPenalty =
                resourcePenalty + constraintPenalty + invalidPenalty;

        FitnessWeights weights = config.getFitnessWeights();
        NormalizationConfig normalization = config.getNormalizationConfig();

        double normalizedCompletionTime =
                completionTime / normalization.getTRef();

        double normalizedCommunicationLatency =
                averageCommunicationLatency / normalization.getLRef();

        double normalizedMobilityPenalty =
                mobilityPenalty / normalization.getPmobRef();

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
                averageCommunicationLatency,
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

    /**
     * Valuta un singolo gene rispetto al task associato.
     */
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

        double coverageTimeSeconds =
                coverageEstimator.estimateCoverageTimeSeconds(
                        snapshot,
                        task,
                        candidate
                );

        if (candidate.getType() == NodeType.LOCAL) {
            double localCpuCycles = task.getCpuCycles();
            double localExecutionTime = safeDivide(localCpuCycles, localCpu);

            if (Math.abs(p) > EPSILON) {
                constraintPenalty += Math.abs(p) * INVALID_SOLUTION_PENALTY;
            }

            double deadlinePenalty = computeDeadlinePenalty(
                    localExecutionTime,
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
                    localCpuCycles,
                    localExecutionTime,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    localExecutionTime,
                    0.0,
                    0.0,
                    constraintPenalty + deadlinePenalty,
                    task.getDeadlineSeconds(),
                    isDeadlineRespected(
                            localExecutionTime,
                            task.getDeadlineSeconds()
                    ),
                    coverageTimeSeconds,
                    true
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

        double localCpuCycles = (1.0 - p) * task.getCpuCycles();
        double localExecutionTime = safeDivide(localCpuCycles, localCpu);

        double uploadTime = safeDivide(
                p * task.getInputSizeBits(),
                allocatedBandwidth
        );

        double remoteExecutionTime = safeDivide(
                p * task.getCpuCycles(),
                allocatedCpu
        );

        double downloadTime = safeDivide(
                task.getOutputSizeBits(),
                allocatedBandwidth
        );

        double baseLatency = Math.max(0.0, candidate.getBaseLatencySeconds());

        double remotePartTime =
                uploadTime + remoteExecutionTime + downloadTime + baseLatency;

        double communicationLatency =
                uploadTime + downloadTime + baseLatency;

        double completionTime =
                p >= 1.0 - EPSILON
                        ? remotePartTime
                        : Math.max(localExecutionTime, remotePartTime);

        double mobilityPenalty = computeMobilityPenalty(
                candidate,
                coverageTimeSeconds,
                completionTime,
                penalties
        );

        double deadlinePenalty = computeDeadlinePenalty(
                completionTime,
                task.getDeadlineSeconds(),
                penalties
        );

        DecisionType decisionType =
                p >= 1.0 - EPSILON
                        ? DecisionType.FULL_OFFLOADING
                        : DecisionType.PARTIAL_OFFLOADING;

        boolean coverageSufficient =
                coverageTimeSeconds > 0.0
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
                localCpuCycles,
                localExecutionTime,
                uploadTime,
                remoteExecutionTime,
                downloadTime,
                baseLatency,
                remotePartTime,
                completionTime,
                communicationLatency,
                mobilityPenalty,
                constraintPenalty + deadlinePenalty,
                task.getDeadlineSeconds(),
                isDeadlineRespected(completionTime, task.getDeadlineSeconds()),
                coverageTimeSeconds,
                coverageSufficient
        );
    }

    /**
     * Inizializza l'uso CPU aggregato per nodo fisico.
     */
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

    /**
     * Inizializza l'uso banda per candidato/link.
     */
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

    /**
     * Inizializza il carico locale per veicolo.
     */
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

    /**
     * Calcola la penalità per superamento delle risorse.
     */
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
     * Calcola la penalità mobility-aware usando la copertura stimata.
     */
    private double computeMobilityPenalty(
            NodeCandidate candidate,
            double coverageTimeSeconds,
            double completionTimeSeconds,
            PenaltyConfig penalties
    ) {
        if (candidate.getType() == NodeType.LOCAL) {
            return 0.0;
        }

        if (!isStrictlyPositive(completionTimeSeconds)) {
            return 0.0;
        }

        if (!isStrictlyPositive(coverageTimeSeconds)) {
            return penalties.getCoverageRiskWeight()
                    + penalties.getHandoverRiskWeight();
        }

        double coverageRisk = Math.max(
                0.0,
                1.0 - coverageTimeSeconds / completionTimeSeconds
        );

        double handoverRisk = Math.min(
                1.0,
                completionTimeSeconds / coverageTimeSeconds
        );

        double linkInstability = 0.0;

        return penalties.getCoverageRiskWeight() * coverageRisk
                + penalties.getLinkInstabilityWeight() * linkInstability
                + penalties.getHandoverRiskWeight() * handoverRisk;
    }

    /**
     * Calcola la penalità di deadline.
     */
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
    private <T> List<T> requireList(
            List<?> list,
            String name
    ) {
        if (list == null) {
            throw new IllegalArgumentException(name + " must not be null.");
        }

        return (List<T>) list;
    }

    private boolean isStrictlyPositive(double value) {
        return Double.isFinite(value) && value > EPSILON;
    }

    private double safeDivide(
            double numerator,
            double denominator
    ) {
        if (!Double.isFinite(numerator)) {
            return INVALID_SOLUTION_PENALTY;
        }

        if (!isStrictlyPositive(denominator)) {
            return INVALID_SOLUTION_PENALTY;
        }

        return numerator / denominator;
    }

    private double clamp(
            double value,
            double min,
            double max
    ) {
        if (!Double.isFinite(value)) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }
}