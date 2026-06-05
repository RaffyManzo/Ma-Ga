package org.eclipse.mosaic.app.maga.livestate;

import java.util.Locale;

final class LiveRemoteCandidatePreview {

    final long timeNs;
    final String candidateId;
    final String sourceVehicleId;
    final String executionNodeId;
    final String type;
    final String gatewayId;
    final String bandwidthPoolId;
    final double availableCpu;
    final double availableBandwidthBitsPerSecond;
    final double propagationDelaySeconds;
    final Double nodeX;
    final Double nodeY;
    final Double coverageRadiusMeters;

    LiveRemoteCandidatePreview(
            long timeNs,
            String candidateId,
            String sourceVehicleId,
            String executionNodeId,
            String type,
            String gatewayId,
            String bandwidthPoolId,
            double availableCpu,
            double availableBandwidthBitsPerSecond,
            double propagationDelaySeconds,
            Double nodeX,
            Double nodeY,
            Double coverageRadiusMeters
    ) {
        this.timeNs = timeNs;
        this.candidateId = candidateId;
        this.sourceVehicleId = sourceVehicleId;
        this.executionNodeId = executionNodeId;
        this.type = type;
        this.gatewayId = gatewayId;
        this.bandwidthPoolId = bandwidthPoolId;
        this.availableCpu = availableCpu;
        this.availableBandwidthBitsPerSecond = availableBandwidthBitsPerSecond;
        this.propagationDelaySeconds = propagationDelaySeconds;
        this.nodeX = nodeX;
        this.nodeY = nodeY;
        this.coverageRadiusMeters = coverageRadiusMeters;
    }

    String toCsvRow() {
        return timeNs
                + "," + candidateId
                + "," + sourceVehicleId
                + "," + executionNodeId
                + "," + type
                + "," + gatewayId
                + "," + bandwidthPoolId
                + "," + format(availableCpu)
                + "," + format(availableBandwidthBitsPerSecond)
                + "," + format(propagationDelaySeconds)
                + "," + optional(nodeX)
                + "," + optional(nodeY)
                + "," + optional(coverageRadiusMeters);
    }

    private static String optional(Double value) {
        return value == null ? "" : format(value);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
