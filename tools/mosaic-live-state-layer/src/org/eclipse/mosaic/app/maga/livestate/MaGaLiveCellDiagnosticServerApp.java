package org.eclipse.mosaic.app.maga.livestate;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;

public class MaGaLiveCellDiagnosticServerApp
        extends AbstractApplication<ServerOperatingSystem>
        implements CommunicationApplication {

    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    private final LiveCellTrafficAccountingCache cellAccounting =
            LiveCellTrafficAccountingCache.getInstance();

    private MaGaLiveStateConfig config;
    private MaGaLiveStateConfig.CellDiagnosticAccounting cellConfig;

    @Override
    public void onStartup() {
        config = MaGaLiveStateConfig.load(getOs().getConfigurationPath());
        cellConfig = config.getCellDiagnosticAccounting();

        getLog().infoSimTime(
                this,
                "LIVE_CELL_DIAGNOSTIC_SERVER_START"
                        + "|serverId=" + getOs().getId()
                        + "|bandwidthSource=" + cellConfig.bandwidthSource
                        + "|responsePayloadBytes=" + cellConfig.responsePayloadBytes
        );
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        V2xMessage message = receivedV2xMessage.getMessage();
        if (!(message instanceof MaGaLiveCellDiagnosticRequestMessage)) {
            return;
        }

        MaGaLiveCellDiagnosticRequestMessage request =
                (MaGaLiveCellDiagnosticRequestMessage) message;
        long receiveTimeNs = getOs().getSimulationTime();

        getLog().infoSimTime(
                this,
                "LIVE_CELL_DIAGNOSTIC_UPLINK_RECEIVE"
                        + "|messageId=" + request.getMessageId()
                        + "|sourceVehicleId=" + request.getSourceVehicleId()
                        + "|destinationId=" + request.getDestinationId()
                        + "|receiveTimeNs=" + receiveTimeNs
                        + "|payloadBytes=" + request.getPayloadBytes()
        );
        getLog().infoSimTime(
                this,
                "LIVE_CELL_DIAGNOSTIC_SERVER_RESPONSE_NOT_TRANSMITTED"
                        + "|messageId=" + request.getMessageId()
                        + "|reason=SERVER_CELL_MODULE_NOT_AVAILABLE_FOR_SERVER_0"
        );
    }

    private void accountControlledResponse(MaGaLiveCellDiagnosticRequestMessage request, long accountTimeNs) {
        long requestSendTimeMs = request.getSendTimeNs() / NANOSECONDS_PER_MILLISECOND;
        String responseMessageId = "live_cell_res__"
                + request.getSourceVehicleId()
                + "__t_"
                + requestSendTimeMs;

        cellAccounting.recordControlledTraffic(
                accountTimeNs,
                responseMessageId,
                LiveCellTrafficEvent.Direction.DOWNLINK,
                getOs().getId(),
                request.getSourceVehicleId(),
                cellConfig.responsePayloadBytes,
                cellConfig
        );

        getLog().infoSimTime(
                this,
                "LIVE_CELL_DIAGNOSTIC_DOWNLINK_ACCOUNTED"
                        + "|requestMessageId=" + request.getMessageId()
                        + "|responseMessageId=" + responseMessageId
                        + "|sourceServerId=" + getOs().getId()
                        + "|destinationVehicleId=" + request.getSourceVehicleId()
                        + "|accountTimeNs=" + accountTimeNs
                        + "|payloadBytes=" + cellConfig.responsePayloadBytes
        );
    }

    @Override
    public void processEvent(Event event) {
        // The server reacts only to controlled Cell requests.
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement receivedAcknowledgement) {
        // Acknowledgements are not part of the diagnostic accounting contract.
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
        // The 13C Cell probe does not customize CAM traffic.
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
        // Structured accounting is emitted at controlled send time.
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(
                this,
                "LIVE_CELL_DIAGNOSTIC_SERVER_STOP"
                        + "|serverId=" + getOs().getId()
        );
    }
}
