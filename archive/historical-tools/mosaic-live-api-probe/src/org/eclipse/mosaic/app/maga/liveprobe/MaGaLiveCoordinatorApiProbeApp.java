package org.eclipse.mosaic.app.maga.liveprobe;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.lib.util.scheduling.Event;

public class MaGaLiveCoordinatorApiProbeApp extends AbstractApplication<ServerOperatingSystem> {

    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    private MaGaLiveProbeConfig config;
    private long tickCount;

    @Override
    public void onStartup() {
        config = MaGaLiveProbeConfig.load(getOs().getConfigurationPath());
        tickCount = 0L;
        getLog().infoSimTime(
                this,
                "LIVE_PROBE_COORDINATOR_START"
                        + "|simulationTime=" + getOs().getSimulationTime()
                        + "|serverId=" + getOs().getId()
                        + "|tickIntervalMs=" + config.getTickIntervalMs()
        );
        scheduleNextTick();
    }

    @Override
    public void processEvent(Event event) {
        tickCount++;
        if (config.isLogCoordinatorTicks()) {
            getLog().infoSimTime(
                    this,
                    "LIVE_PROBE_COORDINATOR_TICK"
                            + "|simulationTime=" + getOs().getSimulationTime()
                            + "|serverId=" + getOs().getId()
                            + "|tickCount=" + tickCount
                            + "|eventTime=" + event.getTime()
            );
        }
        scheduleNextTick();
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(
                this,
                "LIVE_PROBE_COORDINATOR_STOP"
                        + "|simulationTime=" + getOs().getSimulationTime()
                        + "|serverId=" + getOs().getId()
                        + "|tickCount=" + tickCount
        );
    }

    private void scheduleNextTick() {
        long delayNs = config.getTickIntervalMs() * NANOSECONDS_PER_MILLISECOND;
        long nextTickTime = getOs().getSimulationTime() + delayNs;
        getOs().getEventManager().addEvent(new Event(nextTickTime, this));
    }
}
