package org.eclipse.mosaic.app.maga.celltraffic;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;

/**
 * Periodically sends controlled Cell traffic from vehicles to server_0.
 */
public class MaGaCellTrafficDiagnosticVehicleApp
        extends AbstractApplication<VehicleOperatingSystem>
        implements CommunicationApplication {

    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    private MaGaCellTrafficDiagnosticConfig config;

    @Override
    public void onStartup() {
        config = MaGaCellTrafficDiagnosticConfig.load(getOs().getConfigurationPath());

        CellModuleConfiguration cellConfiguration = new CellModuleConfiguration()
                .maxUplinkBitrate(config.getMaxUplinkBitrate())
                .maxDownlinkBitrate(config.getMaxDownlinkBitrate());
        getOs().getCellModule().enable(cellConfiguration);

        getLog().infoSimTime(
                this,
                "CELL_TRAFFIC_APP_START"
                        + "|vehicleId=" + getOs().getId()
                        + "|requestPayloadBytes=" + config.getRequestPayloadBytes()
                        + "|responsePayloadBytes=" + config.getResponsePayloadBytes()
                        + "|intervalMs=" + config.getIntervalMs()
        );

        scheduleNext(config.getInitialDelayMs());
    }

    @Override
    public void processEvent(Event event) {
        long sendTimeNs = getOs().getSimulationTime();
        long sendTimeMs = sendTimeNs / NANOSECONDS_PER_MILLISECOND;
        String vehicleId = getOs().getId();
        String messageId = "cell_diag_req__" + vehicleId + "__t_" + sendTimeMs;

        MessageRouting routing = getOs()
                .getCellModule()
                .createMessageRouting()
                .topological()
                .destination(config.getDestinationId())
                .tcp()
                .build();

        MaGaCellTrafficDiagnosticMessage message = new MaGaCellTrafficDiagnosticMessage(
                routing,
                messageId,
                vehicleId,
                config.getDestinationId(),
                sendTimeNs,
                config.getRequestPayloadBytes()
        );

        getOs().getCellModule().sendV2xMessage(message);

        getLog().infoSimTime(
                this,
                "CELL_TRAFFIC_SEND"
                        + "|messageId=" + messageId
                        + "|vehicleId=" + vehicleId
                        + "|destinationId=" + config.getDestinationId()
                        + "|sendTimeNs=" + sendTimeNs
                        + "|sendTimeMs=" + sendTimeMs
                        + "|requestPayloadBytes=" + config.getRequestPayloadBytes()
                        + "|intervalMs=" + config.getIntervalMs()
        );

        scheduleNext(config.getIntervalMs());
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(
                this,
                "CELL_TRAFFIC_APP_STOP"
                        + "|vehicleId=" + getOs().getId()
        );
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (!(receivedV2xMessage.getMessage() instanceof MaGaCellTrafficDiagnosticResponseMessage)) {
            return;
        }

        MaGaCellTrafficDiagnosticResponseMessage responseMessage =
                (MaGaCellTrafficDiagnosticResponseMessage) receivedV2xMessage.getMessage();
        long receiveTimeNs = getOs().getSimulationTime();
        long receiveTimeMs = receiveTimeNs / NANOSECONDS_PER_MILLISECOND;

        getLog().infoSimTime(
                this,
                "CELL_TRAFFIC_RESPONSE_RECEIVE"
                        + "|messageId=" + responseMessage.getRequestMessageId()
                        + "|responseMessageId=" + responseMessage.getResponseMessageId()
                        + "|vehicleId=" + getOs().getId()
                        + "|sourceServerId=" + responseMessage.getSourceServerId()
                        + "|receiveTimeNs=" + receiveTimeNs
                        + "|receiveTimeMs=" + receiveTimeMs
                        + "|responsePayloadBytes=" + responseMessage.getResponsePayloadBytes()
        );
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement receivedAcknowledgement) {
        // No acknowledgement logic is needed for the diagnostic stream.
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
        // CAM customization is outside this diagnostic.
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
        // Structured send logs are emitted at scheduling time.
    }

    private void scheduleNext(long delayMs) {
        long triggerTimeNs = getOs().getSimulationTime() + delayMs * NANOSECONDS_PER_MILLISECOND;
        getOs().getEventManager().addEvent(new Event(triggerTimeNs, this));
    }
}
