package org.eclipse.mosaic.app.maga.liveprobe;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.util.Locale;

public class MaGaLiveVehicleApiProbeApp
        extends AbstractApplication<VehicleOperatingSystem>
        implements VehicleApplication {

    private MaGaLiveProbeConfig config;

    @Override
    public void onStartup() {
        config = MaGaLiveProbeConfig.load(getOs().getConfigurationPath());
        getLog().infoSimTime(
                this,
                "LIVE_PROBE_VEHICLE_START"
                        + "|simulationTime=" + getOs().getSimulationTime()
                        + "|vehicleId=" + getOs().getId()
                        + "|adHocEnabled=" + readAdHocEnabled()
        );
    }

    @Override
    public void onVehicleUpdated(VehicleData previousVehicleData, VehicleData updatedVehicleData) {
        if (config != null && !config.isLogVehicleUpdates()) {
            return;
        }

        VehicleData vehicleData = updatedVehicleData != null
                ? updatedVehicleData
                : getOs().getVehicleData();
        CartesianPoint projectedPosition = vehicleData == null
                ? null
                : vehicleData.getProjectedPosition();

        double projectedX = projectedPosition == null ? Double.NaN : projectedPosition.getX();
        double projectedY = projectedPosition == null ? Double.NaN : projectedPosition.getY();
        double speed = vehicleData == null ? Double.NaN : vehicleData.getSpeed();
        boolean finiteProjectedPosition = Double.isFinite(projectedX) && Double.isFinite(projectedY);
        boolean finiteSpeed = Double.isFinite(speed);

        getLog().infoSimTime(
                this,
                "LIVE_PROBE_VEHICLE_UPDATE"
                        + "|simulationTime=" + getOs().getSimulationTime()
                        + "|vehicleId=" + getOs().getId()
                        + "|projectedX=" + format(projectedX)
                        + "|projectedY=" + format(projectedY)
                        + "|speed=" + format(speed)
                        + "|adHocEnabled=" + readAdHocEnabled()
                        + "|finiteProjectedPosition=" + finiteProjectedPosition
                        + "|finiteSpeed=" + finiteSpeed
        );
    }

    @Override
    public void processEvent(Event event) {
        // Vehicle-side probe work is driven by MOSAIC vehicle update callbacks.
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(
                this,
                "LIVE_PROBE_VEHICLE_STOP"
                        + "|simulationTime=" + getOs().getSimulationTime()
                        + "|vehicleId=" + getOs().getId()
                        + "|adHocEnabled=" + readAdHocEnabled()
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
