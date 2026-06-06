package org.eclipse.mosaic.app.maga.liveruntime;

import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import window.state.TemporalStepResult;

import java.util.HashMap;
import java.util.Map;

final class LiveStrategyApplier {

    private LiveAppliedStrategy lastAppliedStrategy;
    private int strategyApplications;
    private int localAssignments;
    private int vehicleAssignments;
    private int edgeAssignments;
    private int cloudAssignments;

    LiveAppliedStrategy apply(TemporalStepResult stepResult, long simulationTimeNs) {
        Map<String, NodeType> candidateTypes = new HashMap<>();
        for (NodeCandidate candidate : stepResult.getSnapshot().getCandidateNodes()) {
            candidateTypes.put(candidate.getCandidateId(), candidate.getType());
        }

        int local = 0;
        int vehicle = 0;
        int edge = 0;
        int cloud = 0;
        Chromosome chromosome = stepResult.getMaGaResult().getBestChromosome();
        if (chromosome != null && chromosome.getGenes() != null) {
            for (Gene gene : chromosome.getGenes()) {
                NodeType type = candidateTypes.get(gene.getSelectedCandidateId());
                if (type == NodeType.LOCAL) {
                    local++;
                } else if (type == NodeType.VEHICLE) {
                    vehicle++;
                } else if (type == NodeType.EDGE) {
                    edge++;
                } else if (type == NodeType.CLOUD) {
                    cloud++;
                }
            }
        }

        lastAppliedStrategy = new LiveAppliedStrategy(
                simulationTimeNs,
                stepResult.getSnapshot().getSnapshotId(),
                stepResult.getMaGaResult().getFinalBestFitness(),
                local,
                vehicle,
                edge,
                cloud
        );
        strategyApplications++;
        localAssignments += local;
        vehicleAssignments += vehicle;
        edgeAssignments += edge;
        cloudAssignments += cloud;
        return lastAppliedStrategy;
    }

    LiveAppliedStrategy getLastAppliedStrategy() {
        return lastAppliedStrategy;
    }

    boolean hasLastAppliedStrategy() {
        return lastAppliedStrategy != null;
    }

    int getStrategyApplications() {
        return strategyApplications;
    }

    int getLocalAssignments() {
        return localAssignments;
    }

    int getVehicleAssignments() {
        return vehicleAssignments;
    }

    int getEdgeAssignments() {
        return edgeAssignments;
    }

    int getCloudAssignments() {
        return cloudAssignments;
    }
}
