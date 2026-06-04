package org.eclipse.mosaic.app.maga.adhocradio;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.util.scheduling.Event;

/**
 * Enables one ad-hoc radio for diagnostic direct V2V candidate extraction.
 *
 * <p>The app intentionally does not send V2X messages. It only enables the
 * MOSAIC ad-hoc module so the output federate can emit ADHOC_CONFIGURATION
 * events consumed by the offline Phase 10G exporter.</p>
 */
public class MaGaAdHocRadioDiagnosticApp extends AbstractApplication<VehicleOperatingSystem> {

    @Override
    public void onStartup() {
        AdHocModuleConfiguration configuration = new AdHocModuleConfiguration();
        configuration
                .addRadio()
                .channel(AdHocChannel.CCH)
                .create();

        getOs().getAdHocModule().enable(configuration);

        getLog().infoSimTime(
                this,
                "ADHOC_RADIO_DIAGNOSTIC_APP_START"
                        + "|vehicleId=" + getOs().getId()
        );
        getLog().infoSimTime(
                this,
                "ADHOC_RADIO_ENABLE"
                        + "|vehicleId=" + getOs().getId()
                        + "|radioMode=SINGLE"
                        + "|channel=CCH"
        );
    }

    @Override
    public void processEvent(Event event) {
        // No scheduled work is needed. The diagnostic only enables the radio.
    }

    @Override
    public void onShutdown() {
        getOs().getAdHocModule().disable();
        getLog().infoSimTime(
                this,
                "ADHOC_RADIO_DIAGNOSTIC_APP_STOP"
                        + "|vehicleId=" + getOs().getId()
        );
    }
}
