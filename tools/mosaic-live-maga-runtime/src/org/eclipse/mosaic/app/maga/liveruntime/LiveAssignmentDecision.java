package org.eclipse.mosaic.app.maga.liveruntime;

import ga.fitness.breakdown.GeneEvaluationBreakdown;
import model.node.NodeType;

/** Immutable full assignment used by applied/stale strategy reporting. */
public final class LiveAssignmentDecision {
    private final String taskId;
    private final String sourceVehicleId;
    private final String selectedCandidateId;
    private final String executionNodeId;
    private final NodeType nodeType;
    private final String decisionType;
    private final double offloadingRatio;
    private final double allocatedCpu;
    private final double allocatedBandwidth;
    private final double completionTimeSeconds;
    private final double communicationLatencySeconds;
    private final double mobilityPenalty;
    private final double constraintPenalty;
    private final double deadlineSeconds;
    private final boolean deadlineRespected;
    private final double coverageTimeSeconds;
    private final boolean coverageSufficient;
    private final double coverageRisk;
    private final double linkInstability;
    private final double handoverRisk;

    private LiveAssignmentDecision(GeneEvaluationBreakdown g) {
        this.taskId = g.getTaskId();
        this.sourceVehicleId = g.getSourceVehicleId();
        this.selectedCandidateId = g.getSelectedCandidateId();
        this.executionNodeId = g.getExecutionNodeId();
        this.nodeType = g.getNodeType();
        this.decisionType = g.getDecisionType().name();
        this.offloadingRatio = g.getOffloadingRatio();
        this.allocatedCpu = g.getAllocatedCpu();
        this.allocatedBandwidth = g.getAllocatedBandwidth();
        this.completionTimeSeconds = g.getCompletionTimeSeconds();
        this.communicationLatencySeconds = g.getCommunicationLatencySeconds();
        this.mobilityPenalty = g.getMobilityPenalty();
        this.constraintPenalty = g.getConstraintPenalty();
        this.deadlineSeconds = g.getDeadlineSeconds();
        this.deadlineRespected = g.isDeadlineRespected();
        this.coverageTimeSeconds = g.getCoverageTimeSeconds();
        this.coverageSufficient = g.isCoverageSufficient();
        this.coverageRisk = g.getMobilityBreakdown().getCoverageRisk();
        this.linkInstability = g.getMobilityBreakdown().getLinkInstability();
        this.handoverRisk = g.getMobilityBreakdown().getHandoverRisk();
    }

    public static LiveAssignmentDecision from(GeneEvaluationBreakdown g) {
        return new LiveAssignmentDecision(g);
    }

    public String getTaskId() { return taskId; }
    public String getSourceVehicleId() { return sourceVehicleId; }
    public String getSelectedCandidateId() { return selectedCandidateId; }
    public String getExecutionNodeId() { return executionNodeId; }
    public NodeType getNodeType() { return nodeType; }
    public String getDecisionType() { return decisionType; }
    public double getOffloadingRatio() { return offloadingRatio; }
    public double getAllocatedCpu() { return allocatedCpu; }
    public double getAllocatedBandwidth() { return allocatedBandwidth; }
    public double getCompletionTimeSeconds() { return completionTimeSeconds; }
    public double getCommunicationLatencySeconds() { return communicationLatencySeconds; }
    public double getMobilityPenalty() { return mobilityPenalty; }
    public double getConstraintPenalty() { return constraintPenalty; }
    public double getDeadlineSeconds() { return deadlineSeconds; }
    public boolean isDeadlineRespected() { return deadlineRespected; }
    public double getCoverageTimeSeconds() { return coverageTimeSeconds; }
    public boolean isCoverageSufficient() { return coverageSufficient; }
    public double getCoverageRisk() { return coverageRisk; }
    public double getLinkInstability() { return linkInstability; }
    public double getHandoverRisk() { return handoverRisk; }

    public boolean samePlacement(LiveAssignmentDecision other) {
        return other != null
                && nodeType == other.nodeType
                && safe(selectedCandidateId).equals(safe(other.selectedCandidateId));
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
