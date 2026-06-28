package org.eclipse.mosaic.app.maga.liveruntime;

import ga.fitness.breakdown.GeneEvaluationBreakdown;
import model.node.NodeType;
import window.state.TemporalStepResult;

import java.util.LinkedHashMap;
import java.util.Map;

final class LiveStrategyApplier {

    private LiveAppliedStrategy lastAppliedStrategy;
    private int strategyApplications;
    private int localAssignments;
    private int vehicleAssignments;
    private int edgeAssignments;
    private int cloudAssignments;

    LiveAppliedStrategy apply(TemporalStepResult stepResult, long simulationTimeNs) {
        Map<String, LiveAssignmentDecision> decisions = new LinkedHashMap<>();
        int local = 0, vehicle = 0, edge = 0, cloud = 0;
        for (GeneEvaluationBreakdown gene : stepResult
                .getMaGaResult().getBestEvaluation().getGeneBreakdowns()) {
            LiveAssignmentDecision decision = LiveAssignmentDecision.from(gene);
            decisions.put(decision.getTaskId(), decision);
            NodeType type = decision.getNodeType();
            if (type == NodeType.LOCAL) { local++; }
            else if (type == NodeType.VEHICLE) { vehicle++; }
            else if (type == NodeType.EDGE) { edge++; }
            else if (type == NodeType.CLOUD) { cloud++; }
        }

        lastAppliedStrategy = new LiveAppliedStrategy(
                simulationTimeNs,
                stepResult.getSnapshot().getSnapshotId(),
                stepResult.getSnapshot().getTimeSeconds(),
                stepResult.getMaGaResult().getFinalBestFitness(),
                decisions, local, vehicle, edge, cloud
        );
        strategyApplications++;
        localAssignments += local;
        vehicleAssignments += vehicle;
        edgeAssignments += edge;
        cloudAssignments += cloud;
        return lastAppliedStrategy;
    }

    LiveAppliedStrategy getLastAppliedStrategy() { return lastAppliedStrategy; }
    boolean hasLastAppliedStrategy() { return lastAppliedStrategy != null; }
    int getStrategyApplications() { return strategyApplications; }
    int getLocalAssignments() { return localAssignments; }
    int getVehicleAssignments() { return vehicleAssignments; }
    int getEdgeAssignments() { return edgeAssignments; }
    int getCloudAssignments() { return cloudAssignments; }
}
