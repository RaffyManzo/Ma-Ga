package org.eclipse.mosaic.app.maga.livestate;

import java.util.Locale;

final class LiveAccessLinkPreview {

    final long timeNs;
    final String accessLinkId;
    final String vehicleId;
    final String gatewayId;
    final String runtimeGatewayId;
    final double distanceMeters;
    final double coverageRadiusMeters;
    final boolean active;
    final boolean available;
    final String cellRegionId;
    final String bandwidthPoolId;

    LiveAccessLinkPreview(
            long timeNs,
            String accessLinkId,
            String vehicleId,
            String gatewayId,
            String runtimeGatewayId,
            double distanceMeters,
            double coverageRadiusMeters,
            boolean active,
            boolean available,
            String cellRegionId,
            String bandwidthPoolId
    ) {
        this.timeNs = timeNs;
        this.accessLinkId = accessLinkId;
        this.vehicleId = vehicleId;
        this.gatewayId = gatewayId;
        this.runtimeGatewayId = runtimeGatewayId;
        this.distanceMeters = distanceMeters;
        this.coverageRadiusMeters = coverageRadiusMeters;
        this.active = active;
        this.available = available;
        this.cellRegionId = cellRegionId;
        this.bandwidthPoolId = bandwidthPoolId;
    }

    String toCsvRow() {
        return timeNs
                + "," + accessLinkId
                + "," + vehicleId
                + "," + gatewayId
                + "," + runtimeGatewayId
                + "," + String.format(Locale.ROOT, "%.6f", distanceMeters)
                + "," + String.format(Locale.ROOT, "%.6f", coverageRadiusMeters)
                + "," + active
                + "," + available
                + "," + cellRegionId
                + "," + bandwidthPoolId;
    }
}
