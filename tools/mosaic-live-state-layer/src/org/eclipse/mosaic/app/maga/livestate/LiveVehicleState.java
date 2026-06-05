package org.eclipse.mosaic.app.maga.livestate;

final class LiveVehicleState {

    private final String vehicleId;
    private final long lastUpdateTimeNs;
    private final double projectedX;
    private final double projectedY;
    private final double speedMetersPerSecond;
    private final boolean adHocEnabled;
    private final boolean active;

    LiveVehicleState(
            String vehicleId,
            long lastUpdateTimeNs,
            double projectedX,
            double projectedY,
            double speedMetersPerSecond,
            boolean adHocEnabled,
            boolean active
    ) {
        this.vehicleId = vehicleId;
        this.lastUpdateTimeNs = lastUpdateTimeNs;
        this.projectedX = projectedX;
        this.projectedY = projectedY;
        this.speedMetersPerSecond = speedMetersPerSecond;
        this.adHocEnabled = adHocEnabled;
        this.active = active;
    }

    String getVehicleId() {
        return vehicleId;
    }

    long getLastUpdateTimeNs() {
        return lastUpdateTimeNs;
    }

    double getProjectedX() {
        return projectedX;
    }

    double getProjectedY() {
        return projectedY;
    }

    double getSpeedMetersPerSecond() {
        return speedMetersPerSecond;
    }

    boolean isAdHocEnabled() {
        return adHocEnabled;
    }

    boolean isActive() {
        return active;
    }

    boolean hasFinitePosition() {
        return Double.isFinite(projectedX) && Double.isFinite(projectedY);
    }
}
