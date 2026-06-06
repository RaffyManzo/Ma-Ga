package org.eclipse.mosaic.app.maga.liveruntime;

final class LiveAppliedStrategy {
    private final long appliedAtSimulationTimeNs;
    private final String snapshotId;
    private final double fitness;
    private final int localAssignments;
    private final int vehicleAssignments;
    private final int edgeAssignments;
    private final int cloudAssignments;

    LiveAppliedStrategy(
            long appliedAtSimulationTimeNs,
            String snapshotId,
            double fitness,
            int localAssignments,
            int vehicleAssignments,
            int edgeAssignments,
            int cloudAssignments
    ) {
        this.appliedAtSimulationTimeNs = appliedAtSimulationTimeNs;
        this.snapshotId = snapshotId;
        this.fitness = fitness;
        this.localAssignments = localAssignments;
        this.vehicleAssignments = vehicleAssignments;
        this.edgeAssignments = edgeAssignments;
        this.cloudAssignments = cloudAssignments;
    }

    long getAppliedAtSimulationTimeNs() {
        return appliedAtSimulationTimeNs;
    }

    String getSnapshotId() {
        return snapshotId;
    }

    double getFitness() {
        return fitness;
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
