package org.eclipse.mosaic.app.maga.liveruntime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LiveAppliedStrategy {
    private final long appliedAtSimulationTimeNs;
    private final String snapshotId;
    private final double snapshotTimeSeconds;
    private final double fitness;
    private final Map<String, LiveAssignmentDecision> assignments;
    private final int localAssignments;
    private final int vehicleAssignments;
    private final int edgeAssignments;
    private final int cloudAssignments;

    LiveAppliedStrategy(
            long appliedAtSimulationTimeNs, String snapshotId,
            double snapshotTimeSeconds, double fitness,
            Map<String, LiveAssignmentDecision> assignments,
            int localAssignments, int vehicleAssignments,
            int edgeAssignments, int cloudAssignments
    ) {
        this.appliedAtSimulationTimeNs = appliedAtSimulationTimeNs;
        this.snapshotId = snapshotId;
        this.snapshotTimeSeconds = snapshotTimeSeconds;
        this.fitness = fitness;
        this.assignments = Collections.unmodifiableMap(
                new LinkedHashMap<>(assignments)
        );
        this.localAssignments = localAssignments;
        this.vehicleAssignments = vehicleAssignments;
        this.edgeAssignments = edgeAssignments;
        this.cloudAssignments = cloudAssignments;
    }

    /** Legacy constructor retained for old harnesses. */
    LiveAppliedStrategy(
            long appliedAtSimulationTimeNs, String snapshotId, double fitness,
            int localAssignments, int vehicleAssignments,
            int edgeAssignments, int cloudAssignments
    ) {
        this(appliedAtSimulationTimeNs, snapshotId, 0.0, fitness,
                Collections.emptyMap(), localAssignments, vehicleAssignments,
                edgeAssignments, cloudAssignments);
    }

    public long getAppliedAtSimulationTimeNs() { return appliedAtSimulationTimeNs; }
    public String getSnapshotId() { return snapshotId; }
    public double getSnapshotTimeSeconds() { return snapshotTimeSeconds; }
    public double getFitness() { return fitness; }
    public Map<String, LiveAssignmentDecision> getAssignments() { return assignments; }
    public int getLocalAssignments() { return localAssignments; }
    public int getVehicleAssignments() { return vehicleAssignments; }
    public int getEdgeAssignments() { return edgeAssignments; }
    public int getCloudAssignments() { return cloudAssignments; }
}
