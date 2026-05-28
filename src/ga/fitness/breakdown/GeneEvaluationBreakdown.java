package ga.fitness.breakdown;

import ga.fitness.DecisionType;
import model.node.NodeType;

/**
 * Dettaglio della valutazione di un singolo gene.
 *
 * Ogni istanza descrive come un task viene eseguito e quali tempi,
 * penalità e risorse derivano dalla scelta fatta dal cromosoma.
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

    /**
     * Crea il breakdown di valutazione di un gene.
     *
     * @param taskId task valutato
     * @param sourceVehicleId veicolo sorgente del task
     * @param selectedCandidateId candidato selezionato dal gene
     * @param executionNodeId nodo fisico di esecuzione
     * @param nodeType tipo del nodo selezionato
     * @param decisionType tipo di decisione locale/remota/parziale
     * @param offloadingRatio quota di offloading
     * @param allocatedCpu CPU assegnata
     * @param allocatedBandwidth banda assegnata
     * @param localCpuCycles cicli eseguiti localmente
     * @param localExecutionTimeSeconds tempo di esecuzione locale
     * @param uploadTimeSeconds tempo di upload
     * @param remoteExecutionTimeSeconds tempo di esecuzione remota
     * @param downloadTimeSeconds tempo di download
     * @param baseLatencySeconds latenza base del collegamento
     * @param remotePartTimeSeconds tempo complessivo della parte remota
     * @param completionTimeSeconds tempo finale di completamento
     * @param communicationLatencySeconds latenza comunicativa
     * @param mobilityPenalty penalità di mobilità
     * @param constraintPenalty penalità di vincolo
     * @param deadlineSeconds deadline del task
     * @param deadlineRespected true se la deadline è rispettata
     * @param coverageTimeSeconds tempo di copertura calcolato
     * @param coverageSufficient true se la copertura basta per completare il task
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

    /**
     * Restituisce il tempo di copertura usato nella valutazione.
     *
     * Questo valore deve essere calcolato tramite CoverageEstimator,
     * non letto direttamente dal NodeCandidate.
     */
    public double getCoverageTimeSeconds() {
        return coverageTimeSeconds;
    }

    public boolean isCoverageSufficient() {
        return coverageSufficient;
    }
}