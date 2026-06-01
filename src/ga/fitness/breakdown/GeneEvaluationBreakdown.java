package ga.fitness.breakdown;

import ga.fitness.DecisionType;
import model.node.NodeType;

import java.util.Objects;

/**
 * Dettaglio della valutazione di un singolo gene.
 *
 * <p>Ogni istanza descrive come un task viene eseguito e quali tempi,
 * penalità e risorse derivano dalla scelta fatta dal cromosoma.</p>
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
                uploadTimeSeconds,
                remoteExecutionTimeSeconds,
                downloadTimeSeconds,
                baseLatencySeconds,
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
            double baseLatencySeconds,
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
        this.nodeType = Objects.requireNonNull(nodeType, "nodeType must not be null.");
        this.decisionType = Objects.requireNonNull(decisionType, "decisionType must not be null.");
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
    public double getLocalExecutionTimeSeconds() { return localExecutionTimeSeconds; }
    public double getUploadTimeSeconds() { return uploadTimeSeconds; }
    public double getRemoteExecutionTimeSeconds() { return remoteExecutionTimeSeconds; }
    public double getDownloadTimeSeconds() { return downloadTimeSeconds; }
    public double getBaseLatencySeconds() { return baseLatencySeconds; }
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
