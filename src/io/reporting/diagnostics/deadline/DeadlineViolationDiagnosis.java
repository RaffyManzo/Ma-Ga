package io.reporting.diagnostics.deadline;

import ga.fitness.DecisionType;
import model.node.NodeType;

/**
 * Diagnosi di un singolo task rispetto alla deadline.
 *
 * L'oggetto conserva i tempi principali calcolati nella fitness e aggiunge
 * una classificazione diagnostica della causa dominante.
 */
public final class DeadlineViolationDiagnosis {

    private final String taskId;
    private final String sourceVehicleId;
    private final String selectedCandidateId;
    private final String executionNodeId;

    private final NodeType nodeType;
    private final DecisionType decisionType;

    private final double offloadingRatio;
    private final double allocatedCpu;
    private final double allocatedBandwidth;

    private final double completionTimeSeconds;
    private final double deadlineSeconds;
    private final double violationSeconds;
    private final double violationRatio;

    private final double localExecutionTimeSeconds;
    private final double uploadTimeSeconds;
    private final double remoteExecutionTimeSeconds;
    private final double downloadTimeSeconds;
    private final double baseLatencySeconds;
    private final double remotePartTimeSeconds;
    private final double communicationLatencySeconds;

    private final double localBranchRatio;
    private final double remoteBranchRatio;
    private final double branchBalanceRatio;

    private final double coverageTimeSeconds;
    private final boolean coverageSufficient;

    private final double mobilityPenalty;
    private final double constraintPenalty;

    private final DeadlineViolationCause primaryCause;
    private final DeadlineViolationCause secondaryCause;

    private final String dominantComponentName;
    private final double dominantComponentSeconds;
    private final String note;

    public DeadlineViolationDiagnosis(
            String taskId,
            String sourceVehicleId,
            String selectedCandidateId,
            String executionNodeId,
            NodeType nodeType,
            DecisionType decisionType,
            double offloadingRatio,
            double allocatedCpu,
            double allocatedBandwidth,
            double completionTimeSeconds,
            double deadlineSeconds,
            double violationSeconds,
            double violationRatio,
            double localExecutionTimeSeconds,
            double uploadTimeSeconds,
            double remoteExecutionTimeSeconds,
            double downloadTimeSeconds,
            double baseLatencySeconds,
            double remotePartTimeSeconds,
            double communicationLatencySeconds,
            double localBranchRatio,
            double remoteBranchRatio,
            double branchBalanceRatio,
            double coverageTimeSeconds,
            boolean coverageSufficient,
            double mobilityPenalty,
            double constraintPenalty,
            DeadlineViolationCause primaryCause,
            DeadlineViolationCause secondaryCause,
            String dominantComponentName,
            double dominantComponentSeconds,
            String note
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
        this.completionTimeSeconds = completionTimeSeconds;
        this.deadlineSeconds = deadlineSeconds;
        this.violationSeconds = violationSeconds;
        this.violationRatio = violationRatio;
        this.localExecutionTimeSeconds = localExecutionTimeSeconds;
        this.uploadTimeSeconds = uploadTimeSeconds;
        this.remoteExecutionTimeSeconds = remoteExecutionTimeSeconds;
        this.downloadTimeSeconds = downloadTimeSeconds;
        this.baseLatencySeconds = baseLatencySeconds;
        this.remotePartTimeSeconds = remotePartTimeSeconds;
        this.communicationLatencySeconds = communicationLatencySeconds;
        this.localBranchRatio = localBranchRatio;
        this.remoteBranchRatio = remoteBranchRatio;
        this.branchBalanceRatio = branchBalanceRatio;
        this.coverageTimeSeconds = coverageTimeSeconds;
        this.coverageSufficient = coverageSufficient;
        this.mobilityPenalty = mobilityPenalty;
        this.constraintPenalty = constraintPenalty;
        this.primaryCause = primaryCause;
        this.secondaryCause = secondaryCause;
        this.dominantComponentName = dominantComponentName;
        this.dominantComponentSeconds = dominantComponentSeconds;
        this.note = note;
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

    public double getCompletionTimeSeconds() {
        return completionTimeSeconds;
    }

    public double getDeadlineSeconds() {
        return deadlineSeconds;
    }

    public double getViolationSeconds() {
        return violationSeconds;
    }

    public double getViolationRatio() {
        return violationRatio;
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

    public double getCommunicationLatencySeconds() {
        return communicationLatencySeconds;
    }

    public double getLocalBranchRatio() {
        return localBranchRatio;
    }

    public double getRemoteBranchRatio() {
        return remoteBranchRatio;
    }

    public double getBranchBalanceRatio() {
        return branchBalanceRatio;
    }

    public double getCoverageTimeSeconds() {
        return coverageTimeSeconds;
    }

    public boolean isCoverageSufficient() {
        return coverageSufficient;
    }

    public double getMobilityPenalty() {
        return mobilityPenalty;
    }

    public double getConstraintPenalty() {
        return constraintPenalty;
    }

    public DeadlineViolationCause getPrimaryCause() {
        return primaryCause;
    }

    public DeadlineViolationCause getSecondaryCause() {
        return secondaryCause;
    }

    public String getDominantComponentName() {
        return dominantComponentName;
    }

    public double getDominantComponentSeconds() {
        return dominantComponentSeconds;
    }

    public String getNote() {
        return note;
    }

    public boolean isDeadlineViolated() {
        return violationSeconds > 0.0;
    }

    public boolean hasCoverageProblem() {
        return !coverageSufficient;
    }
}
