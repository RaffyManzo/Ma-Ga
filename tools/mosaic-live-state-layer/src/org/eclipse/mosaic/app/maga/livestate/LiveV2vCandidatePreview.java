package org.eclipse.mosaic.app.maga.livestate;

import java.util.Locale;

final class LiveV2vCandidatePreview {

    final long timeNs;
    final String candidateId;
    final String sourceVehicleId;
    final String targetVehicleId;
    final String executionNodeId;
    final String type;
    final double distanceMeters;
    final long availableCpu;
    final String bandwidthPoolId;
    final long availableBandwidthBitsPerSecond;
    final String bandwidthSource;
    final double propagationDelaySeconds;

    LiveV2vCandidatePreview(
            long timeNs,
            String candidateId,
            String sourceVehicleId,
            String targetVehicleId,
            double distanceMeters,
            long availableCpu,
            String bandwidthPoolId,
            long availableBandwidthBitsPerSecond,
            String bandwidthSource,
            double propagationDelaySeconds
    ) {
        this.timeNs = timeNs;
        this.candidateId = candidateId;
        this.sourceVehicleId = sourceVehicleId;
        this.targetVehicleId = targetVehicleId;
        this.executionNodeId = targetVehicleId;
        this.type = "VEHICLE";
        this.distanceMeters = distanceMeters;
        this.availableCpu = availableCpu;
        this.bandwidthPoolId = bandwidthPoolId;
        this.availableBandwidthBitsPerSecond = availableBandwidthBitsPerSecond;
        this.bandwidthSource = bandwidthSource;
        this.propagationDelaySeconds = propagationDelaySeconds;
    }

    String toCsvRow() {
        return timeNs
                + "," + candidateId
                + "," + sourceVehicleId
                + "," + targetVehicleId
                + "," + executionNodeId
                + "," + type
                + "," + String.format(Locale.ROOT, "%.6f", distanceMeters)
                + "," + availableCpu
                + "," + bandwidthPoolId
                + "," + availableBandwidthBitsPerSecond
                + "," + bandwidthSource
                + "," + String.format(Locale.ROOT, "%.6f", propagationDelaySeconds);
    }
}
