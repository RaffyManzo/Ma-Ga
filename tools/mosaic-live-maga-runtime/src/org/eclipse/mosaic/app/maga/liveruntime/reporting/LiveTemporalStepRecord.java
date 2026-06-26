package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LocalResourceUsageBreakdown;
import window.state.TemporalStepResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LiveTemporalStepRecord {
    public final String jobId;
    public final String finalStatus;
    public final int windowIndex;
    public final String trigger;
    public final String snapshotId;
    public final double snapshotTimeSeconds;
    public final double logicalObservationTimeSeconds;
    public final double sourceObservationTimeSeconds;
    public final double dataCollectionDelaySeconds;
    public final Map<String, Object> dynamicity;
    public final Map<String, Object> populationReuse;
    public final String reuseMode;
    public final Map<String, Object> adaptiveWindowDecision;
    public final Map<String, Object> operationalMetrics;
    public final int initialPopulationSize;
    public final int finalPopulationSize;
    public final Map<String, Object> fitness;
    public final Map<String, Object> localContention;
    public final List<Map<String, Object>> genes;

    private LiveTemporalStepRecord(
            String jobId,
            String finalStatus,
            TemporalStepResult step
    ) {
        this.jobId = jobId;
        this.finalStatus = finalStatus;
        this.windowIndex = step.getWindowIndex();
        this.trigger = String.valueOf(step.getTrigger().getReason());
        this.snapshotId = step.getSnapshot().getSnapshotId();
        this.snapshotTimeSeconds = step.getSnapshotTimeSeconds();
        this.logicalObservationTimeSeconds = step.getLogicalObservationTimeSeconds();
        this.sourceObservationTimeSeconds = step.getSourceObservationTimeSeconds();
        this.dataCollectionDelaySeconds = step.getDataCollectionDelaySeconds();
        this.dynamicity = dynamicity(step);
        this.populationReuse = populationReuse(step);
        this.reuseMode = String.valueOf(step.getReuseMode());
        this.adaptiveWindowDecision = adaptiveWindowDecision(step);
        this.operationalMetrics = operationalMetrics(step);
        this.initialPopulationSize = step.getInitialPopulationSize();
        this.finalPopulationSize = step.getFinalPopulationSize();
        this.fitness = fitness(step);
        this.localContention = localContention(step);
        this.genes = genes(step);
    }

    static LiveTemporalStepRecord from(
            String jobId,
            String finalStatus,
            TemporalStepResult step
    ) {
        return new LiveTemporalStepRecord(jobId, finalStatus, step);
    }

    private static Map<String, Object> dynamicity(TemporalStepResult step) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("previousSnapshotId", step.getDynamicityBreakdown().getPreviousSnapshotId());
        row.put("currentSnapshotId", step.getDynamicityBreakdown().getCurrentSnapshotId());
        row.put("previousSnapshotTimeSeconds", step.getDynamicityBreakdown().getPreviousSnapshotTimeSeconds());
        row.put("currentSnapshotTimeSeconds", step.getDynamicityBreakdown().getCurrentSnapshotTimeSeconds());
        row.put("vehicleVariation", step.getDynamicityBreakdown().getVehicleVariation());
        row.put("taskVariation", step.getDynamicityBreakdown().getTaskVariation());
        row.put("resourceVariation", step.getDynamicityBreakdown().getResourceVariation());
        row.put("linkVariation", step.getDynamicityBreakdown().getLinkVariation());
        row.put("globalDynamicity", step.getDynamicityBreakdown().getGlobalDynamicity());
        row.put("dynamicityLevel", String.valueOf(step.getDynamicityBreakdown().getDynamicityLevel()));
        row.put("suggestedReuseMode", String.valueOf(step.getDynamicityBreakdown().getSuggestedReuseMode()));
        return row;
    }

    private static Map<String, Object> populationReuse(TemporalStepResult step) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("baseReuseMode", String.valueOf(step.getPopulationReuseDecision().getBaseReuseMode()));
        row.put("appliedReuseMode", String.valueOf(step.getPopulationReuseDecision().getAppliedMode()));
        row.put("previousPerformanceSignal", String.valueOf(step.getPopulationReuseDecision().getPreviousPerformanceSignal()));
        row.put("componentSpikeDetected", step.getPopulationReuseDecision().isComponentSpikeDetected());
        row.put("severeComponentSpikeDetected", step.getPopulationReuseDecision().isSevereComponentSpikeDetected());
        row.put("corrected", step.getPopulationReuseDecision().isCorrected());
        row.put("reason", step.getPopulationReuseDecision().getReason());
        return row;
    }

    private static Map<String, Object> adaptiveWindowDecision(TemporalStepResult step) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("currentWindowSeconds", step.getAdaptiveWindowDecision().getCurrentWindowSeconds());
        row.put("nextWindowSeconds", step.getAdaptiveWindowDecision().getNextWindowSeconds());
        row.put("action", String.valueOf(step.getAdaptiveWindowDecision().getAction()));
        row.put("reason", step.getAdaptiveWindowDecision().getReason());
        row.put("dynamicityLevel", String.valueOf(step.getAdaptiveWindowDecision().getDynamicityLevel()));
        row.put("minimumWindowSeconds", step.getAdaptiveWindowDecision().getBounds().getMinimumWindowSeconds());
        row.put("maximumWindowSeconds", step.getAdaptiveWindowDecision().getBounds().getMaximumWindowSeconds());
        row.put("coverageReferenceSeconds", step.getAdaptiveWindowDecision().getBounds().getCoverageReferenceSeconds());
        row.put("gaRuntimeEstimateUsedSeconds", step.getAdaptiveWindowDecision().getBounds().getGaRuntimeEstimateUsedSeconds());
        row.put("observedGaRuntimeSeconds", step.getAdaptiveWindowDecision().getBounds().getObservedGaRuntimeSeconds());
        row.put("adaptiveMaximumWindowSeconds", step.getAdaptiveWindowDecision().getBounds().getAdaptiveMaximumWindowSeconds());
        row.put("configuredMaximumWindowSeconds", step.getAdaptiveWindowDecision().getBounds().getConfiguredMaximumWindowSeconds());
        row.put("minimumBoundMode", String.valueOf(step.getAdaptiveWindowDecision().getBounds().getMinimumBoundMode()));
        row.put("maximumBoundMode", String.valueOf(step.getAdaptiveWindowDecision().getBounds().getMaximumBoundMode()));
        row.put("maximumRaisedToMinimum", step.getAdaptiveWindowDecision().getBounds().isMaximumRaisedToMinimum());
        return row;
    }

    private static Map<String, Object> operationalMetrics(TemporalStepResult step) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dataCollectionSeconds", step.getOperationalMetrics().getDataCollectionSeconds());
        row.put("gaRuntimeEstimateSeconds", step.getOperationalMetrics().getGaRuntimeEstimateSeconds());
        row.put("observedGaRuntimeSeconds", step.getOperationalMetrics().getObservedGaRuntimeSeconds());
        row.put("strategyApplicationSeconds", step.getOperationalMetrics().getStrategyApplicationSeconds());
        row.put("epsilonSeconds", step.getOperationalMetrics().getEpsilonSeconds());
        row.put("minimumWindowSeconds", step.getOperationalMetrics().getMinimumWindowSeconds());
        return row;
    }

    private static Map<String, Object> fitness(TemporalStepResult step) {
        EvaluationBreakdown evaluation = step.getMaGaResult().getBestEvaluation();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("snapshotId", step.getMaGaResult().getSnapshotId());
        row.put("initialBestFitness", step.getMaGaResult().getInitialBestFitness());
        row.put("finalBestFitness", step.getMaGaResult().getFinalBestFitness());
        row.put("generationsExecuted", step.getMaGaResult().getGenerationsExecuted());
        row.put("stopReason", String.valueOf(step.getMaGaResult().getStopReason()));
        row.put("fitness", evaluation.getFitness());
        row.put("completionTimeSeconds", evaluation.getCompletionTimeSeconds());
        row.put("communicationLatencySeconds", evaluation.getCommunicationLatencySeconds());
        row.put("mobilityPenalty", evaluation.getMobilityPenalty());
        row.put("resourcePenalty", evaluation.getResourcePenalty());
        row.put("normalizedCompletionTime", evaluation.getNormalizedCompletionTime());
        row.put("normalizedCommunicationLatency", evaluation.getNormalizedCommunicationLatency());
        row.put("normalizedMobilityPenalty", evaluation.getNormalizedMobilityPenalty());
        row.put("normalizedResourcePenalty", evaluation.getNormalizedResourcePenalty());
        return row;
    }

    private static Map<String, Object> localContention(
            TemporalStepResult step
    ) {
        EvaluationBreakdown evaluation = step
                .getMaGaResult()
                .getBestEvaluation();

        int localTaskPortions = 0;
        int vehiclesWithLocalWorkload = 0;
        int vehiclesWithLocalContention = 0;
        int vehiclesWithLocalCpuOverflow = 0;
        int localDeadlineViolations = 0;
        double maxIndependentLocalExecutionTimeSeconds = 0.0;
        double maxContendedLocalCompletionTimeSeconds = 0.0;
        double maxLocalContentionDelaySeconds = 0.0;
        double maxLocalDemandRatio = 0.0;
        double maxLocalCpuOverflowRatio = 0.0;

        for (LocalResourceUsageBreakdown usage
                : evaluation.getLocalResourceUsageBreakdowns()) {
            if (!usage.hasLocalWorkload()) {
                continue;
            }

            vehiclesWithLocalWorkload++;
            localTaskPortions += usage.getLocalTaskCount();
            localDeadlineViolations += usage.getDeadlineViolationCount();

            if (usage.hasContention()) {
                vehiclesWithLocalContention++;
            }
            if (usage.hasCpuViolation()) {
                vehiclesWithLocalCpuOverflow++;
            }

            maxIndependentLocalExecutionTimeSeconds = Math.max(
                    maxIndependentLocalExecutionTimeSeconds,
                    usage.getMaxIndependentLocalExecutionTimeSeconds()
            );
            maxContendedLocalCompletionTimeSeconds = Math.max(
                    maxContendedLocalCompletionTimeSeconds,
                    usage.getMaxLocalExecutionTimeSeconds()
            );
            maxLocalContentionDelaySeconds = Math.max(
                    maxLocalContentionDelaySeconds,
                    usage.getMaxContentionDelaySeconds()
            );
            maxLocalDemandRatio = Math.max(
                    maxLocalDemandRatio,
                    usage.getMaxLocalDemandRatio()
            );
            maxLocalCpuOverflowRatio = Math.max(
                    maxLocalCpuOverflowRatio,
                    usage.getCpuOverflowRatio()
            );
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("localTaskPortions", localTaskPortions);
        row.put("vehiclesWithLocalWorkload", vehiclesWithLocalWorkload);
        row.put("vehiclesWithLocalContention", vehiclesWithLocalContention);
        row.put("vehiclesWithLocalCpuOverflow", vehiclesWithLocalCpuOverflow);
        row.put("localDeadlineViolations", localDeadlineViolations);
        row.put(
                "maxIndependentLocalExecutionTimeSeconds",
                maxIndependentLocalExecutionTimeSeconds
        );
        row.put(
                "maxContendedLocalCompletionTimeSeconds",
                maxContendedLocalCompletionTimeSeconds
        );
        row.put(
                "maxLocalContentionDelaySeconds",
                maxLocalContentionDelaySeconds
        );
        row.put("maxLocalDemandRatio", maxLocalDemandRatio);
        row.put("maxLocalCpuOverflowRatio", maxLocalCpuOverflowRatio);
        return row;
    }

    private static List<Map<String, Object>> genes(TemporalStepResult step) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GeneEvaluationBreakdown gene : step.getMaGaResult().getBestEvaluation().getGeneBreakdowns()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", gene.getTaskId());
            row.put("sourceVehicleId", gene.getSourceVehicleId());
            row.put("selectedCandidateId", gene.getSelectedCandidateId());
            row.put("executionNodeId", gene.getExecutionNodeId());
            row.put("nodeType", String.valueOf(gene.getNodeType()));
            row.put("decisionType", String.valueOf(gene.getDecisionType()));
            row.put("offloadingRatio", gene.getOffloadingRatio());
            row.put("allocatedCpu", gene.getAllocatedCpu());
            row.put("allocatedBandwidth", gene.getAllocatedBandwidth());
            row.put("completionTimeSeconds", gene.getCompletionTimeSeconds());
            row.put("uploadTimeSeconds", gene.getUploadTimeSeconds());
            row.put("downloadTimeSeconds", gene.getDownloadTimeSeconds());
            row.put("propagationDelaySeconds", gene.getPropagationDelaySeconds());
            row.put("communicationLatencySeconds", gene.getCommunicationLatencySeconds());
            row.put("remoteExecutionTimeSeconds", gene.getRemoteExecutionTimeSeconds());
            row.put("remotePartTimeSeconds", gene.getRemotePartTimeSeconds());
            row.put(
                    "independentLocalExecutionTimeSeconds",
                    gene.getIndependentLocalExecutionTimeSeconds()
            );
            row.put(
                    "contendedLocalCompletionTimeSeconds",
                    gene.getContendedLocalCompletionTimeSeconds()
            );
            row.put(
                    "localContentionDelaySeconds",
                    gene.getLocalContentionDelaySeconds()
            );
            row.put(
                    "localExecutionTimeSeconds",
                    gene.getLocalExecutionTimeSeconds()
            );
            row.put("deadlineSeconds", gene.getDeadlineSeconds());
            row.put("deadlineRespected", gene.isDeadlineRespected());
            row.put("coverageTimeSeconds", gene.getCoverageTimeSeconds());
            row.put("coverageSufficient", gene.isCoverageSufficient());
            row.put("mobilityPenalty", gene.getMobilityPenalty());
            row.put("constraintPenalty", gene.getConstraintPenalty());
            row.put("bandwidthPool", null);
            row.put("gateway", gene.getMobilityBreakdown().getLinkMetrics().getReferenceAccessGatewayId());
            row.put("mobilityCoverageRisk", gene.getMobilityBreakdown().getCoverageRisk());
            row.put("mobilityLinkInstability", gene.getMobilityBreakdown().getLinkInstability());
            row.put("mobilityHandoverRisk", gene.getMobilityBreakdown().getHandoverRisk());
            row.put("mobilityModelMode", String.valueOf(gene.getMobilityBreakdown().getLinkMetrics().getModelMode()));
            rows.add(row);
        }
        return rows;
    }
}
