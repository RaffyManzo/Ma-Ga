package ga.fitness.breakdown;

import ga.fitness.DecisionType;
import model.node.NodeType;

import java.util.Objects;

/**
 * Dettaglio della valutazione di un singolo gene.
 *
 * <p>Il tempo locale è esposto su due livelli:</p>
 *
 * <ul>
 *     <li>tempo indipendente: porzione locale valutata come task isolato;</li>
 *     <li>tempo locale corretto: completion time della porzione locale dopo la
 *     schedulazione EDF sul veicolo sorgente.</li>
 * </ul>
 *
 * <p>Il getter storico {@link #getLocalExecutionTimeSeconds()} restituisce il
 * valore corretto dalla contesa.</p>
 */
public final class GeneEvaluationBreakdown {

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
    private final double independentLocalExecutionTimeSeconds;
    private final double localExecutionTimeSeconds;
    private final double uploadTimeSeconds;
    private final double remoteExecutionTimeSeconds;
    private final double downloadTimeSeconds;
    private final double propagationDelaySeconds;
    private final double remotePartTimeSeconds;
    private final double completionTimeSeconds;
    private final double communicationLatencySeconds;
    private final double mobilityPenalty;
    private final double constraintPenalty;
    private final double deadlineSeconds;
    private final boolean deadlineRespected;
    private final double coverageTimeSeconds;
    private final boolean coverageSufficient;
    private final MobilityPenaltyBreakdown mobilityBreakdown;

    /**
     * Costruttore compatibile con i chiamanti precedenti.
     */
    public GeneEvaluationBreakdown(
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
            double propagationDelaySeconds,
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
        this(
                taskId,
                sourceVehicleId,
                selectedCandidateId,
                executionNodeId,
                nodeType,
                decisionType,
                offloadingRatio,
                allocatedCpu,
                allocatedBandwidth,
                localCpuCycles,
                localExecutionTimeSeconds,
                localExecutionTimeSeconds,
                uploadTimeSeconds,
                remoteExecutionTimeSeconds,
                downloadTimeSeconds,
                propagationDelaySeconds,
                remotePartTimeSeconds,
                completionTimeSeconds,
                communicationLatencySeconds,
                mobilityPenalty,
                constraintPenalty,
                deadlineSeconds,
                deadlineRespected,
                coverageTimeSeconds,
                coverageSufficient,
                MobilityPenaltyBreakdown.legacy(
                        nodeType,
                        coverageTimeSeconds,
                        mobilityPenalty
                )
        );
    }

    /**
     * Costruttore compatibile con il breakdown mobility-aware precedente.
     */
    public GeneEvaluationBreakdown(
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
            double propagationDelaySeconds,
            double remotePartTimeSeconds,
            double completionTimeSeconds,
            double communicationLatencySeconds,
            double mobilityPenalty,
            double constraintPenalty,
            double deadlineSeconds,
            boolean deadlineRespected,
            double coverageTimeSeconds,
            boolean coverageSufficient,
            MobilityPenaltyBreakdown mobilityBreakdown
    ) {
        this(
                taskId,
                sourceVehicleId,
                selectedCandidateId,
                executionNodeId,
                nodeType,
                decisionType,
                offloadingRatio,
                allocatedCpu,
                allocatedBandwidth,
                localCpuCycles,
                localExecutionTimeSeconds,
                localExecutionTimeSeconds,
                uploadTimeSeconds,
                remoteExecutionTimeSeconds,
                downloadTimeSeconds,
                propagationDelaySeconds,
                remotePartTimeSeconds,
                completionTimeSeconds,
                communicationLatencySeconds,
                mobilityPenalty,
                constraintPenalty,
                deadlineSeconds,
                deadlineRespected,
                coverageTimeSeconds,
                coverageSufficient,
                mobilityBreakdown
        );
    }

    /**
     * Costruttore completo con distinzione tra tempo locale indipendente e
     * completion time locale corretto dalla contesa.
     */
    public GeneEvaluationBreakdown(
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
            double independentLocalExecutionTimeSeconds,
            double localExecutionTimeSeconds,
            double uploadTimeSeconds,
            double remoteExecutionTimeSeconds,
            double downloadTimeSeconds,
            double propagationDelaySeconds,
            double remotePartTimeSeconds,
            double completionTimeSeconds,
            double communicationLatencySeconds,
            double mobilityPenalty,
            double constraintPenalty,
            double deadlineSeconds,
            boolean deadlineRespected,
            double coverageTimeSeconds,
            boolean coverageSufficient,
            MobilityPenaltyBreakdown mobilityBreakdown
    ) {
        this.taskId = taskId;
        this.sourceVehicleId = sourceVehicleId;
        this.selectedCandidateId = selectedCandidateId;
        this.executionNodeId = executionNodeId;
        this.nodeType = Objects.requireNonNull(
                nodeType,
                "nodeType must not be null."
        );
        this.decisionType = Objects.requireNonNull(
                decisionType,
                "decisionType must not be null."
        );
        this.offloadingRatio = offloadingRatio;
        this.allocatedCpu = allocatedCpu;
        this.allocatedBandwidth = allocatedBandwidth;
        this.localCpuCycles = localCpuCycles;
        this.independentLocalExecutionTimeSeconds =
                independentLocalExecutionTimeSeconds;
        this.localExecutionTimeSeconds = localExecutionTimeSeconds;
        this.uploadTimeSeconds = uploadTimeSeconds;
        this.remoteExecutionTimeSeconds = remoteExecutionTimeSeconds;
        this.downloadTimeSeconds = downloadTimeSeconds;
        this.propagationDelaySeconds = propagationDelaySeconds;
        this.remotePartTimeSeconds = remotePartTimeSeconds;
        this.completionTimeSeconds = completionTimeSeconds;
        this.communicationLatencySeconds = communicationLatencySeconds;
        this.mobilityPenalty = mobilityPenalty;
        this.constraintPenalty = constraintPenalty;
        this.deadlineSeconds = deadlineSeconds;
        this.deadlineRespected = deadlineRespected;
        this.coverageTimeSeconds = coverageTimeSeconds;
        this.coverageSufficient = coverageSufficient;
        this.mobilityBreakdown = Objects.requireNonNull(
                mobilityBreakdown,
                "mobilityBreakdown must not be null."
        );
    }

    public String getTaskId() { return taskId; }
    public String getSourceVehicleId() { return sourceVehicleId; }
    public String getSelectedCandidateId() { return selectedCandidateId; }
    public String getExecutionNodeId() { return executionNodeId; }
    public NodeType getNodeType() { return nodeType; }
    public DecisionType getDecisionType() { return decisionType; }
    public double getOffloadingRatio() { return offloadingRatio; }
    public double getAllocatedCpu() { return allocatedCpu; }
    public double getAllocatedBandwidth() { return allocatedBandwidth; }
    public double getLocalCpuCycles() { return localCpuCycles; }

    /**
     * Tempo locale valutato come se il task fosse isolato.
     */
    public double getIndependentLocalExecutionTimeSeconds() {
        return independentLocalExecutionTimeSeconds;
    }

    /**
     * Completion time del ramo locale dopo la contesa EDF.
     */
    public double getLocalExecutionTimeSeconds() {
        return localExecutionTimeSeconds;
    }

    public double getContendedLocalCompletionTimeSeconds() {
        return localExecutionTimeSeconds;
    }

    public double getLocalContentionDelaySeconds() {
        return Math.max(
                0.0,
                localExecutionTimeSeconds
                        - independentLocalExecutionTimeSeconds
        );
    }

    public double getUploadTimeSeconds() { return uploadTimeSeconds; }
    public double getRemoteExecutionTimeSeconds() { return remoteExecutionTimeSeconds; }
    public double getDownloadTimeSeconds() { return downloadTimeSeconds; }

    /** Restituisce tau_n: il ritardo end-to-end aggregato del percorso remoto. */
    public double getPropagationDelaySeconds() { return propagationDelaySeconds; }

    /**
     * Alias mantenuto per compatibilità con printer e chiamanti precedenti.
     *
     * @deprecated usare {@link #getPropagationDelaySeconds()}
     */
    @Deprecated
    public double getBaseLatencySeconds() { return getPropagationDelaySeconds(); }

    public double getRemotePartTimeSeconds() { return remotePartTimeSeconds; }
    public double getCompletionTimeSeconds() { return completionTimeSeconds; }
    public double getCommunicationLatencySeconds() { return communicationLatencySeconds; }
    public double getMobilityPenalty() { return mobilityPenalty; }
    public double getConstraintPenalty() { return constraintPenalty; }
    public double getDeadlineSeconds() { return deadlineSeconds; }
    public boolean isDeadlineRespected() { return deadlineRespected; }
    public double getCoverageTimeSeconds() { return coverageTimeSeconds; }
    public boolean isCoverageSufficient() { return coverageSufficient; }
    public MobilityPenaltyBreakdown getMobilityBreakdown() { return mobilityBreakdown; }
}
