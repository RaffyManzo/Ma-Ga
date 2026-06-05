package org.eclipse.mosaic.app.maga.livestate;

import java.util.Locale;

final class LiveLocalCandidatePreview {

    final long timeNs;
    final String candidateId;
    final String sourceVehicleId;
    final String executionNodeId;
    final String type;
    final long availableCpu;
    final String cpuSource;
    final double propagationDelaySeconds;

    LiveLocalCandidatePreview(
            long timeNs,
            String candidateId,
            String sourceVehicleId,
            String executionNodeId,
            long availableCpu,
            String cpuSource
    ) {
        this.timeNs = timeNs;
        this.candidateId = candidateId;
        this.sourceVehicleId = sourceVehicleId;
        this.executionNodeId = executionNodeId;
        this.type = "LOCAL";
        this.availableCpu = availableCpu;
        this.cpuSource = cpuSource;
        this.propagationDelaySeconds = 0.0;
    }

    String toCsvRow() {
        return timeNs
                + "," + candidateId
                + "," + sourceVehicleId
                + "," + executionNodeId
                + "," + type
                + "," + availableCpu
                + "," + cpuSource
                + "," + String.format(Locale.ROOT, "%.6f", propagationDelaySeconds);
    }
}
