package org.eclipse.mosaic.app.maga.celltraffic;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;

/**
 * Receives and logs controlled Cell diagnostic traffic on the MOSAIC server.
 */
public class MaGaCellTrafficDiagnosticServerApp
        extends AbstractApplication<ServerOperatingSystem>
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
                "CELL_TRAFFIC_SERVER_START"
                        + "|serverId=" + getOs().getId()
                        + "|responsePayloadBytes=" + config.getResponsePayloadBytes()
        );
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        V2xMessage message = receivedV2xMessage.getMessage();
        if (!(message instanceof MaGaCellTrafficDiagnosticMessage)) {
            return;
        }

        MaGaCellTrafficDiagnosticMessage diagnosticMessage = (MaGaCellTrafficDiagnosticMessage) message;
        long receiveTimeNs = getOs().getSimulationTime();
        long receiveTimeMs = receiveTimeNs / NANOSECONDS_PER_MILLISECOND;

        getLog().infoSimTime(
                this,
                "CELL_TRAFFIC_RECEIVE"
                        + "|messageId=" + diagnosticMessage.getMessageId()
                        + "|sourceVehicleId=" + diagnosticMessage.getSourceVehicleId()
                        + "|destinationId=" + diagnosticMessage.getDestinationId()
                        + "|receiveTimeNs=" + receiveTimeNs
                        + "|receiveTimeMs=" + receiveTimeMs
                        + "|requestPayloadBytes=" + diagnosticMessage.getRequestPayloadBytes()
        );

        sendResponse(diagnosticMessage, receiveTimeNs);
    }

    private void sendResponse(MaGaCellTrafficDiagnosticMessage requestMessage, long sendTimeNs) {
        long sendTimeMs = sendTimeNs / NANOSECONDS_PER_MILLISECOND;
        long requestSendTimeMs = requestMessage.getSendTimeNs() / NANOSECONDS_PER_MILLISECOND;
        String responseMessageId = "cell_diag_res__"
                + requestMessage.getSourceVehicleId()
                + "__t_"
                + requestSendTimeMs;

        MessageRouting routing = getOs()
                .getCellModule()
                .createMessageRouting()
                .topological()
                .destination(requestMessage.getSourceVehicleId())
                .tcp()
                .build();

        MaGaCellTrafficDiagnosticResponseMessage responseMessage =
                new MaGaCellTrafficDiagnosticResponseMessage(
                        routing,
                        requestMessage.getMessageId(),
                        responseMessageId,
                        getOs().getId(),
                        requestMessage.getSourceVehicleId(),
                        requestMessage.getSendTimeNs(),
                        config.getResponsePayloadBytes()
                );

        getOs().getCellModule().sendV2xMessage(responseMessage);

        getLog().infoSimTime(
                this,
                "CELL_TRAFFIC_RESPONSE_SEND"
                        + "|messageId=" + requestMessage.getMessageId()
                        + "|responseMessageId=" + responseMessageId
                        + "|sourceServerId=" + getOs().getId()
                        + "|destinationVehicleId=" + requestMessage.getSourceVehicleId()
                        + "|sendTimeNs=" + sendTimeNs
                        + "|sendTimeMs=" + sendTimeMs
                        + "|responsePayloadBytes=" + config.getResponsePayloadBytes()
        );
    }

    @Override
    public void processEvent(Event event) {
        // The server is passive in this diagnostic.
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
        // The server does not transmit diagnostic messages in this experiment.
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(
                this,
                "CELL_TRAFFIC_SERVER_STOP"
                        + "|serverId=" + getOs().getId()
        );
    }
}
