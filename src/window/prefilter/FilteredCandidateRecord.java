package window.prefilter;

import model.node.NodeType;

/**
 * Record diagnostico di un candidato rimosso o mantenuto dal prefilter.
 */
public final class FilteredCandidateRecord {

    private final String candidateId;
    private final String sourceVehicleId;
    private final String executionNodeId;
    private final NodeType nodeType;
    private final CandidateRejectionReason reason;
    private final double estimatedBestCompletionSeconds;
    private final double estimatedCoverageSeconds;
    private final String note;

    public FilteredCandidateRecord(
            String candidateId,
            String sourceVehicleId,
            String executionNodeId,
            NodeType nodeType,
            CandidateRejectionReason reason,
            double estimatedBestCompletionSeconds,
            double estimatedCoverageSeconds,
            String note
    ) {
        this.candidateId = candidateId;
        this.sourceVehicleId = sourceVehicleId;
        this.executionNodeId = executionNodeId;
        this.nodeType = nodeType;
        this.reason = reason;
        this.estimatedBestCompletionSeconds = estimatedBestCompletionSeconds;
        this.estimatedCoverageSeconds = estimatedCoverageSeconds;
        this.note = note == null ? "" : note;
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

    public CandidateRejectionReason getReason() {
        return reason;
    }

    public double getEstimatedBestCompletionSeconds() {
        return estimatedBestCompletionSeconds;
    }

    public double getEstimatedCoverageSeconds() {
        return estimatedCoverageSeconds;
    }

    public String getNote() {
        return note;
    }
}
