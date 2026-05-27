package ga;

import config.FitnessWeights;
import config.MaGaConfig;
import config.NormalizationConfig;
import config.PenaltyConfig;
import model.Chromosome;
import model.Gene;
import model.NodeCandidate;
import model.NodeType;
import model.SystemSnapshot;
import model.TaskInstance;
import model.VehicleSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Valuta un cromosoma MA-GA rispetto a uno snapshot statico del sistema.
 *
 * Nel modello source-aware, il gene non seleziona più un "nodo globale",
 * ma un candidato di esecuzione valido per il veicolo sorgente del task.
 *
 * Ogni candidato contiene:
 *
 * - candidateId;
 * - sourceVehicleId;
 * - executionNodeId;
 * - type;
 * - availableCpu;
 * - availableBandwidth;
 * - baseLatencySeconds;
 * - coverageTimeSeconds.
 *
 * La fitness implementata è:
 *
 * J(C) = wT * T(C) + wL * L(C) + wM * Pmob(C) + wR * Pres(C)
 */
public final class FitnessEvaluator {

    private static final double EPSILON = 1.0E-9;
    private static final double INVALID_SOLUTION_PENALTY = 1.0E9;

    private final MaGaConfig config;

    /**
     * Costruisce il valutatore della fitness.
     *
     * Parametri in ingresso:
     * - config: configurazione complessiva del MA-GA.
     *
     * Output:
     * - nuova istanza di FitnessEvaluator.
     */
    public FitnessEvaluator(MaGaConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
    }

    /**
     * Calcola solo il valore scalare della fitness.
     *
     * Parametri in ingresso:
     * - chromosome: cromosoma da valutare;
     * - snapshot: stato statico del sistema.
     *
     * Output:
     * - valore finale J(C).
     */
    public double evaluate(Chromosome chromosome, SystemSnapshot snapshot) {
        return evaluateDetailed(chromosome, snapshot).getFitness();
    }

    /**
     * Calcola la valutazione dettagliata di un cromosoma.
     *
     * Parametri in ingresso:
     * - chromosome: cromosoma da valutare;
     * - snapshot: stato statico del sistema.
     *
     * Output:
     * - EvaluationBreakdown con fitness globale, dettagli per task,
     *   uso CPU per nodo fisico, uso banda per link/candidato e carico locale.
     */
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

        double averageCommunicationLatency = tasks.isEmpty()
                ? 0.0
                : communicationLatencySum / tasks.size();

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
     * Valuta un singolo gene rispetto al task, al candidato e al veicolo sorgente.
     *
     * Parametri in ingresso:
     * - task: task valutato;
     * - gene: decisione di offloading;
     * - candidate: candidato di esecuzione scelto;
     * - sourceVehicle: veicolo che ha generato il task.
     *
     * Output:
     * - breakdown dettagliato del gene.
     */
    private GeneEvaluationBreakdown evaluateGene(
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
                    isDeadlineRespected(localExecutionTime, task.getDeadlineSeconds()),
                    candidate.getCoverageTimeSeconds(),
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

        double completionTime = p >= 1.0 - EPSILON
                ? remotePartTime
                : Math.max(localExecutionTime, remotePartTime);

        double mobilityPenalty = computeMobilityPenalty(
                candidate,
                completionTime,
                penalties
        );

        double deadlinePenalty = computeDeadlinePenalty(
                completionTime,
                task.getDeadlineSeconds(),
                penalties
        );

        DecisionType decisionType = p >= 1.0 - EPSILON
                ? DecisionType.FULL_OFFLOADING
                : DecisionType.PARTIAL_OFFLOADING;

        boolean coverageSufficient =
                candidate.getCoverageTimeSeconds() <= 0.0
                        || candidate.getCoverageTimeSeconds() >= completionTime;

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
                candidate.getCoverageTimeSeconds(),
                coverageSufficient
        );
    }

    /**
     * Inizializza l'uso CPU per nodo fisico di esecuzione.
     *
     * Nota:
     * più candidati diversi possono puntare allo stesso executionNodeId.
     * In quel caso la CPU va aggregata sullo stesso nodo fisico.
     */
    private Map<String, ExecutionNodeResourceUsageBreakdown> initializeExecutionNodeCpuUsage(
            List<NodeCandidate> candidates
    ) {
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
     *
     * Nota:
     * la banda viene trattata come proprietà del link sorgente-destinazione,
     * quindi resta associata al candidateId.
     */
    private Map<String, LinkBandwidthUsageBreakdown> initializeLinkBandwidthUsage(
            List<NodeCandidate> candidates
    ) {
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
    private Map<String, LocalResourceUsageBreakdown> initializeLocalUsage(
            List<VehicleSnapshot> vehicles
    ) {
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

        for (ExecutionNodeResourceUsageBreakdown usage : cpuUsageByExecutionNode.values()) {
            totalPenalty += penalties.getCpuOveruseWeight() * usage.getCpuOverflowRatio();
        }

        for (LinkBandwidthUsageBreakdown usage : bandwidthUsageByCandidate.values()) {
            totalPenalty += penalties.getBandwidthOveruseWeight() * usage.getBandwidthOverflowRatio();
        }

        return totalPenalty;
    }

    /**
     * Calcola la penalità mobility-aware.
     */
    private double computeMobilityPenalty(
            NodeCandidate candidate,
            double completionTimeSeconds,
            PenaltyConfig penalties
    ) {
        if (candidate.getType() == NodeType.LOCAL) {
            return 0.0;
        }

        double coverageTime = candidate.getCoverageTimeSeconds();

        if (!isStrictlyPositive(completionTimeSeconds)) {
            return 0.0;
        }

        if (!isStrictlyPositive(coverageTime)) {
            return penalties.getCoverageRiskWeight() + penalties.getHandoverRiskWeight();
        }

        double coverageRisk = Math.max(0.0, 1.0 - coverageTime / completionTimeSeconds);
        double handoverRisk = Math.min(1.0, completionTimeSeconds / coverageTime);
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

    /**
     * Verifica se la deadline è rispettata.
     */
    private boolean isDeadlineRespected(
            double completionTimeSeconds,
            double deadlineSeconds
    ) {
        return deadlineSeconds <= 0.0 || completionTimeSeconds <= deadlineSeconds;
    }

    private double computeCardinalityPenalty(
            List<TaskInstance> tasks,
            List<Gene> genes
    ) {
        if (tasks.size() == genes.size()) {
            return 0.0;
        }

        return INVALID_SOLUTION_PENALTY * Math.abs(tasks.size() - genes.size());
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

    private Map<String, VehicleSnapshot> indexVehicles(List<VehicleSnapshot> vehicles) {
        Map<String, VehicleSnapshot> result = new HashMap<>();

        for (VehicleSnapshot vehicle : vehicles) {
            result.put(vehicle.getVehicleId(), vehicle);
        }

        return result;
    }

    private Map<String, NodeCandidate> indexCandidates(List<NodeCandidate> candidates) {
        Map<String, NodeCandidate> result = new HashMap<>();

        for (NodeCandidate candidate : candidates) {
            result.put(candidate.getCandidateId(), candidate);
        }

        return result;
    }

    private Map<String, Gene> indexGenes(List<Gene> genes) {
        Map<String, Gene> result = new HashMap<>();

        for (Gene gene : genes) {
            if (!result.containsKey(gene.getTaskId())) {
                result.put(gene.getTaskId(), gene);
            }
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

    /**
     * Breakdown globale della fitness.
     */
    public static final class EvaluationBreakdown {

        private final double fitness;
        private final double completionTimeSeconds;
        private final double communicationLatencySeconds;
        private final double mobilityPenalty;
        private final double resourcePenalty;

        private final double normalizedCompletionTime;
        private final double normalizedCommunicationLatency;
        private final double normalizedMobilityPenalty;
        private final double normalizedResourcePenalty;

        private final List<GeneEvaluationBreakdown> geneBreakdowns;
        private final List<ExecutionNodeResourceUsageBreakdown> executionNodeResourceUsageBreakdowns;
        private final List<LinkBandwidthUsageBreakdown> linkBandwidthUsageBreakdowns;
        private final List<LocalResourceUsageBreakdown> localResourceUsageBreakdowns;

        private EvaluationBreakdown(
                double fitness,
                double completionTimeSeconds,
                double communicationLatencySeconds,
                double mobilityPenalty,
                double resourcePenalty,
                double normalizedCompletionTime,
                double normalizedCommunicationLatency,
                double normalizedMobilityPenalty,
                double normalizedResourcePenalty,
                List<GeneEvaluationBreakdown> geneBreakdowns,
                List<ExecutionNodeResourceUsageBreakdown> executionNodeResourceUsageBreakdowns,
                List<LinkBandwidthUsageBreakdown> linkBandwidthUsageBreakdowns,
                List<LocalResourceUsageBreakdown> localResourceUsageBreakdowns
        ) {
            this.fitness = fitness;
            this.completionTimeSeconds = completionTimeSeconds;
            this.communicationLatencySeconds = communicationLatencySeconds;
            this.mobilityPenalty = mobilityPenalty;
            this.resourcePenalty = resourcePenalty;
            this.normalizedCompletionTime = normalizedCompletionTime;
            this.normalizedCommunicationLatency = normalizedCommunicationLatency;
            this.normalizedMobilityPenalty = normalizedMobilityPenalty;
            this.normalizedResourcePenalty = normalizedResourcePenalty;
            this.geneBreakdowns = List.copyOf(geneBreakdowns);
            this.executionNodeResourceUsageBreakdowns = List.copyOf(executionNodeResourceUsageBreakdowns);
            this.linkBandwidthUsageBreakdowns = List.copyOf(linkBandwidthUsageBreakdowns);
            this.localResourceUsageBreakdowns = List.copyOf(localResourceUsageBreakdowns);
        }

        public double getFitness() {
            return fitness;
        }

        public double getCompletionTimeSeconds() {
            return completionTimeSeconds;
        }

        public double getCommunicationLatencySeconds() {
            return communicationLatencySeconds;
        }

        public double getMobilityPenalty() {
            return mobilityPenalty;
        }

        public double getResourcePenalty() {
            return resourcePenalty;
        }

        public double getNormalizedCompletionTime() {
            return normalizedCompletionTime;
        }

        public double getNormalizedCommunicationLatency() {
            return normalizedCommunicationLatency;
        }

        public double getNormalizedMobilityPenalty() {
            return normalizedMobilityPenalty;
        }

        public double getNormalizedResourcePenalty() {
            return normalizedResourcePenalty;
        }

        public List<GeneEvaluationBreakdown> getGeneBreakdowns() {
            return geneBreakdowns;
        }

        public List<ExecutionNodeResourceUsageBreakdown> getExecutionNodeResourceUsageBreakdowns() {
            return executionNodeResourceUsageBreakdowns;
        }

        public List<LinkBandwidthUsageBreakdown> getLinkBandwidthUsageBreakdowns() {
            return linkBandwidthUsageBreakdowns;
        }

        public List<LocalResourceUsageBreakdown> getLocalResourceUsageBreakdowns() {
            return localResourceUsageBreakdowns;
        }
    }

    /**
     * Breakdown di un singolo gene/task.
     */
    public static final class GeneEvaluationBreakdown {

        private final String taskId;
        private final String sourceVehicleId;
        private final String selectedCandidateId;
        private final String executionNodeId;
        private final NodeType nodeType;
        private final DecisionType decisionType;

        private final double offloadingRatio;
        private final double allocatedCpu;
        private final double allocatedBandwidth;

        private final double localCpuCycles;
        private final double localExecutionTimeSeconds;
        private final double uploadTimeSeconds;
        private final double remoteExecutionTimeSeconds;
        private final double downloadTimeSeconds;
        private final double baseLatencySeconds;
        private final double remotePartTimeSeconds;
        private final double completionTimeSeconds;
        private final double communicationLatencySeconds;

        private final double mobilityPenalty;
        private final double constraintPenalty;

        private final double deadlineSeconds;
        private final boolean deadlineRespected;

        private final double coverageTimeSeconds;
        private final boolean coverageSufficient;

        private GeneEvaluationBreakdown(
                String taskId,
                String sourceVehicleId,
                String selectedCandidateId,
                String executionNodeId,
                NodeType nodeType,
                DecisionType decisionType,
                double offloadingRatio,
                double allocatedCpu,
                double allocatedBandwidth,
                double localCpuCycles,
                double localExecutionTimeSeconds,
                double uploadTimeSeconds,
                double remoteExecutionTimeSeconds,
                double downloadTimeSeconds,
                double baseLatencySeconds,
                double remotePartTimeSeconds,
                double completionTimeSeconds,
                double communicationLatencySeconds,
                double mobilityPenalty,
                double constraintPenalty,
                double deadlineSeconds,
                boolean deadlineRespected,
                double coverageTimeSeconds,
                boolean coverageSufficient
        ) {
            this.taskId = taskId;
            this.sourceVehicleId = sourceVehicleId;
            this.selectedCandidateId = selectedCandidateId;
            this.executionNodeId = executionNodeId;
            this.nodeType = nodeType;
            this.decisionType = decisionType;
            this.offloadingRatio = offloadingRatio;
            this.allocatedCpu = allocatedCpu;
            this.allocatedBandwidth = allocatedBandwidth;
            this.localCpuCycles = localCpuCycles;
            this.localExecutionTimeSeconds = localExecutionTimeSeconds;
            this.uploadTimeSeconds = uploadTimeSeconds;
            this.remoteExecutionTimeSeconds = remoteExecutionTimeSeconds;
            this.downloadTimeSeconds = downloadTimeSeconds;
            this.baseLatencySeconds = baseLatencySeconds;
            this.remotePartTimeSeconds = remotePartTimeSeconds;
            this.completionTimeSeconds = completionTimeSeconds;
            this.communicationLatencySeconds = communicationLatencySeconds;
            this.mobilityPenalty = mobilityPenalty;
            this.constraintPenalty = constraintPenalty;
            this.deadlineSeconds = deadlineSeconds;
            this.deadlineRespected = deadlineRespected;
            this.coverageTimeSeconds = coverageTimeSeconds;
            this.coverageSufficient = coverageSufficient;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getSourceVehicleId() {
            return sourceVehicleId;
        }

        public String getSelectedCandidateId() {
            return selectedCandidateId;
        }

        public String getExecutionNodeId() {
            return executionNodeId;
        }

        public NodeType getNodeType() {
            return nodeType;
        }

        public DecisionType getDecisionType() {
            return decisionType;
        }

        public double getOffloadingRatio() {
            return offloadingRatio;
        }

        public double getAllocatedCpu() {
            return allocatedCpu;
        }

        public double getAllocatedBandwidth() {
            return allocatedBandwidth;
        }

        public double getLocalCpuCycles() {
            return localCpuCycles;
        }

        public double getLocalExecutionTimeSeconds() {
            return localExecutionTimeSeconds;
        }

        public double getUploadTimeSeconds() {
            return uploadTimeSeconds;
        }

        public double getRemoteExecutionTimeSeconds() {
            return remoteExecutionTimeSeconds;
        }

        public double getDownloadTimeSeconds() {
            return downloadTimeSeconds;
        }

        public double getBaseLatencySeconds() {
            return baseLatencySeconds;
        }

        public double getRemotePartTimeSeconds() {
            return remotePartTimeSeconds;
        }

        public double getCompletionTimeSeconds() {
            return completionTimeSeconds;
        }

        public double getCommunicationLatencySeconds() {
            return communicationLatencySeconds;
        }

        public double getMobilityPenalty() {
            return mobilityPenalty;
        }

        public double getConstraintPenalty() {
            return constraintPenalty;
        }

        public double getDeadlineSeconds() {
            return deadlineSeconds;
        }

        public boolean isDeadlineRespected() {
            return deadlineRespected;
        }

        public double getCoverageTimeSeconds() {
            return coverageTimeSeconds;
        }

        public boolean isCoverageSufficient() {
            return coverageSufficient;
        }
    }

    /**
     * Uso CPU aggregato per nodo fisico di esecuzione.
     */
    public static final class ExecutionNodeResourceUsageBreakdown {

        private final String executionNodeId;
        private final NodeType nodeType;
        private final double availableCpu;

        private double usedCpu;

        private ExecutionNodeResourceUsageBreakdown(
                String executionNodeId,
                NodeType nodeType,
                double availableCpu
        ) {
            this.executionNodeId = executionNodeId;
            this.nodeType = nodeType;
            this.availableCpu = availableCpu;
        }

        private void addCpu(double value) {
            this.usedCpu += Math.max(0.0, value);
        }

        public String getExecutionNodeId() {
            return executionNodeId;
        }

        public NodeType getNodeType() {
            return nodeType;
        }

        public double getAvailableCpu() {
            return availableCpu;
        }

        public double getUsedCpu() {
            return usedCpu;
        }

        public double getCpuUsagePercent() {
            if (availableCpu <= EPSILON) {
                return 0.0;
            }

            return (usedCpu / availableCpu) * 100.0;
        }

        public double getCpuOverflowRatio() {
            if (availableCpu <= EPSILON) {
                return usedCpu > 0.0 ? 1.0 : 0.0;
            }

            return Math.max(0.0, (usedCpu - availableCpu) / availableCpu);
        }

        public boolean hasCpuViolation() {
            return usedCpu > availableCpu;
        }

        public boolean isCpuSaturated(double thresholdPercent) {
            return !hasCpuViolation() && getCpuUsagePercent() >= thresholdPercent;
        }
    }

    /**
     * Uso banda per candidato/link sorgente-destinazione.
     */
    public static final class LinkBandwidthUsageBreakdown {

        private final String candidateId;
        private final String sourceVehicleId;
        private final String executionNodeId;
        private final NodeType nodeType;
        private final double availableBandwidth;

        private double usedBandwidth;

        private LinkBandwidthUsageBreakdown(
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

        private void addBandwidth(double value) {
            this.usedBandwidth += Math.max(0.0, value);
        }

        public String getCandidateId() {
            return candidateId;
        }

        public String getSourceVehicleId() {
            return sourceVehicleId;
        }

        public String getExecutionNodeId() {
            return executionNodeId;
        }

        public NodeType getNodeType() {
            return nodeType;
        }

        public double getAvailableBandwidth() {
            return availableBandwidth;
        }

        public double getUsedBandwidth() {
            return usedBandwidth;
        }

        public double getBandwidthUsagePercent() {
            if (availableBandwidth <= EPSILON) {
                return 0.0;
            }

            return (usedBandwidth / availableBandwidth) * 100.0;
        }

        public double getBandwidthOverflowRatio() {
            if (availableBandwidth <= EPSILON) {
                return usedBandwidth > 0.0 ? 1.0 : 0.0;
            }

            return Math.max(0.0, (usedBandwidth - availableBandwidth) / availableBandwidth);
        }

        public boolean hasBandwidthViolation() {
            return usedBandwidth > availableBandwidth;
        }

        public boolean isBandwidthSaturated(double thresholdPercent) {
            return !hasBandwidthViolation() && getBandwidthUsagePercent() >= thresholdPercent;
        }
    }

    /**
     * Carico locale stimato per veicolo.
     */
    public static final class LocalResourceUsageBreakdown {

        private final String vehicleId;
        private final double localCpu;

        private double localCpuCycles;
        private double maxLocalExecutionTimeSeconds;

        private LocalResourceUsageBreakdown(String vehicleId, double localCpu) {
            this.vehicleId = vehicleId;
            this.localCpu = localCpu;
        }

        private void addLocalWorkload(
                double cpuCycles,
                double localExecutionTimeSeconds
        ) {
            this.localCpuCycles += Math.max(0.0, cpuCycles);
            this.maxLocalExecutionTimeSeconds = Math.max(
                    this.maxLocalExecutionTimeSeconds,
                    Math.max(0.0, localExecutionTimeSeconds)
            );
        }

        public String getVehicleId() {
            return vehicleId;
        }

        public double getLocalCpu() {
            return localCpu;
        }

        public double getLocalCpuCycles() {
            return localCpuCycles;
        }

        public double getMaxLocalExecutionTimeSeconds() {
            return maxLocalExecutionTimeSeconds;
        }

        public boolean hasLocalWorkload() {
            return localCpuCycles > EPSILON;
        }
    }
}