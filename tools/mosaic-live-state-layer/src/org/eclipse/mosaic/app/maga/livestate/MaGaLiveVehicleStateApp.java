package org.eclipse.mosaic.app.maga.livestate;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.util.Locale;

public class MaGaLiveVehicleStateApp
        extends AbstractApplication<VehicleOperatingSystem>
        implements VehicleApplication {

    private final LiveStateCache cache = LiveStateCache.getInstance();

    @Override
    public void onStartup() {
        long simulationTime = getOs().getSimulationTime();
        boolean adHocEnabled = readAdHocEnabled();
        cache.registerVehicleStarted(getOs().getId(), simulationTime, adHocEnabled);
        getLog().infoSimTime(
                this,
                "LIVE_STATE_VEHICLE_START"
                        + "|simulationTime=" + simulationTime
                        + "|vehicleId=" + getOs().getId()
                        + "|adHocEnabled=" + adHocEnabled
        );
    }

    @Override
    public void onVehicleUpdated(VehicleData previousVehicleData, VehicleData updatedVehicleData) {
        VehicleData vehicleData = updatedVehicleData != null
                ? updatedVehicleData
                : getOs().getVehicleData();
        CartesianPoint projectedPosition = vehicleData == null
                ? null
                : vehicleData.getProjectedPosition();

        double projectedX = projectedPosition == null ? Double.NaN : projectedPosition.getX();
        double projectedY = projectedPosition == null ? Double.NaN : projectedPosition.getY();
        double speed = vehicleData == null ? Double.NaN : vehicleData.getSpeed();
        boolean adHocEnabled = readAdHocEnabled();
        long simulationTime = getOs().getSimulationTime();

        cache.updateVehicle(getOs().getId(), simulationTime, projectedX, projectedY, speed, adHocEnabled);
        getLog().infoSimTime(
                this,
                "LIVE_STATE_VEHICLE_UPDATE"
                        + "|simulationTime=" + simulationTime
                        + "|vehicleId=" + getOs().getId()
                        + "|projectedX=" + format(projectedX)
                        + "|projectedY=" + format(projectedY)
                        + "|speed=" + format(speed)
                        + "|adHocEnabled=" + adHocEnabled
                        + "|finiteProjectedPosition=" + (Double.isFinite(projectedX) && Double.isFinite(projectedY))
                        + "|finiteSpeed=" + Double.isFinite(speed)
        );
    }

    @Override
    public void processEvent(Event event) {
        // Vehicle state is updated by MOSAIC vehicle callbacks only.
    }

    @Override
    public void onShutdown() {
        long simulationTime = getOs().getSimulationTime();
        boolean adHocEnabled = readAdHocEnabled();
        cache.markVehicleInactive(getOs().getId(), simulationTime, adHocEnabled);
        getLog().infoSimTime(
                this,
                "LIVE_STATE_VEHICLE_STOP"
                        + "|simulationTime=" + simulationTime
                        + "|vehicleId=" + getOs().getId()
                        + "|adHocEnabled=" + adHocEnabled
        );
    }

    private boolean readAdHocEnabled() {
        return getOs().getAdHocModule().isEnabled();
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
