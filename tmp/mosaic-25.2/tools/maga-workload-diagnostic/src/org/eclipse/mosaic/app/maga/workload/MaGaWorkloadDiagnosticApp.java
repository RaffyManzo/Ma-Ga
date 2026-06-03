package org.eclipse.mosaic.app.maga.workload;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

/**
 * Diagnostic workload generator for the MA-GA integration study.
 *
 * <p>This first version deliberately keeps the task profiles inside the class.
 * It does not read the JSON configuration and does not emit custom MOSAIC
 * interactions. Each generated task is written as one structured log line.
 */
public class MaGaWorkloadDiagnosticApp
        extends AbstractApplication<VehicleOperatingSystem> {

    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    /**
     * Schedules the first activation of each task profile.
     */
    @Override
    public void onStartup() {
        getLog().infoSimTime(
                this,
                "WORKLOAD_APP_START"
                        + "|vehicleId=" + getOs().getId()
                        + "|mode=DETERMINISTIC_PERIODIC"
                        + "|clockSource=MOSAIC_SIMULATION_TIME"
        );

        for (TaskProfile profile : TaskProfile.values()) {
            schedule(profile, profile.startOffsetNs);
        }
    }

    /**
     * Generates one task activation and schedules the next activation of the
     * same profile.
     *
     * @param event scheduled MOSAIC event
     */
    @Override
    public void processEvent(Event event) {
        Object resource = event.getResource();

        if (!(resource instanceof TaskProfile)) {
            getLog().infoSimTime(
                    this,
                    "WORKLOAD_EVENT_IGNORED"
                            + "|vehicleId=" + getOs().getId()
                            + "|reason=UNEXPECTED_RESOURCE"
            );
            return;
        }

        TaskProfile profile = (TaskProfile) resource;
        long activationTimeNs = getOs().getSimulationTime();
        long activationTimeMs = activationTimeNs / NANOSECONDS_PER_MILLISECOND;

        String taskId = profile.profileId
                + "__"
                + getOs().getId()
                + "__t_"
                + activationTimeMs;

        getLog().infoSimTime(
                this,
                "TASK_ACTIVATION"
                        + "|taskId=" + taskId
                        + "|sourceVehicleId=" + getOs().getId()
                        + "|profileId=" + profile.profileId
                        + "|activationTimeNs=" + activationTimeNs
                        + "|activationTimeMs=" + activationTimeMs
                        + "|inputSizeBits=" + profile.inputSizeBits
                        + "|outputSizeBits=" + profile.outputSizeBits
                        + "|cpuCycles=" + profile.cpuCycles
                        + "|deadlineSeconds=" + profile.deadlineSeconds
        );

        schedule(profile, profile.intervalNs);
    }

    /**
     * Writes a final diagnostic line when the vehicle application is removed.
     */
    @Override
    public void onShutdown() {
        getLog().infoSimTime(
                this,
                "WORKLOAD_APP_STOP"
                        + "|vehicleId=" + getOs().getId()
        );
    }

    private void schedule(TaskProfile profile, long delayNs) {
        long triggerTimeNs = getOs().getSimulationTime() + delayNs;

        getOs().getEventManager().addEvent(
                new Event(triggerTimeNs, this, profile)
        );
    }

    /**
     * Synthetic task profiles used only during the diagnostic phase.
     */
    private enum TaskProfile {
        PERCEPTION_LIGHT(
                "perception_light",
                8_000_000L,
                64_000L,
                1_500_000_000L,
                0.5,
                5L * TIME.SECOND,
                1L * TIME.SECOND
        ),

        PLANNING_MEDIUM(
                "planning_medium",
                2_000_000L,
                32_000L,
                3_000_000_000L,
                1.0,
                10L * TIME.SECOND,
                2L * TIME.SECOND
        ),

        COOPERATIVE_AWARENESS(
                "cooperative_awareness",
                512_000L,
                16_000L,
                800_000_000L,
                0.75,
                15L * TIME.SECOND,
                3L * TIME.SECOND
        );

        private final String profileId;
        private final long inputSizeBits;
        private final long outputSizeBits;
        private final long cpuCycles;
        private final double deadlineSeconds;
        private final long intervalNs;
        private final long startOffsetNs;

        TaskProfile(
                String profileId,
                long inputSizeBits,
                long outputSizeBits,
                long cpuCycles,
                double deadlineSeconds,
                long intervalNs,
                long startOffsetNs
        ) {
            this.profileId = profileId;
            this.inputSizeBits = inputSizeBits;
            this.outputSizeBits = outputSizeBits;
            this.cpuCycles = cpuCycles;
            this.deadlineSeconds = deadlineSeconds;
            this.intervalNs = intervalNs;
            this.startOffsetNs = startOffsetNs;
        }
    }
}