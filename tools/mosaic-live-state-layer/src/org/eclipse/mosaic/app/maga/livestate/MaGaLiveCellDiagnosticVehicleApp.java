package org.eclipse.mosaic.app.maga.livestate;

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

public class MaGaLiveCellDiagnosticVehicleApp
        extends AbstractApplication<VehicleOperatingSystem>
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

        CellModuleConfiguration cellModuleConfiguration = new CellModuleConfiguration()
                .maxUplinkBitrate(cellConfig.getMaxUplinkBitrateBitsPerSecond())
                .maxDownlinkBitrate(cellConfig.getMaxDownlinkBitrateBitsPerSecond());
        getOs().getCellModule().enable(cellModuleConfiguration);

        getLog().infoSimTime(
                this,
                "LIVE_CELL_DIAGNOSTIC_VEHICLE_START"
                        + "|vehicleId=" + getOs().getId()
                        + "|bandwidthSource=" + cellConfig.bandwidthSource
                        + "|requestPayloadBytes=" + cellConfig.requestPayloadBytes
                        + "|intervalMs=" + cellConfig.intervalMs
        );
        scheduleNext(cellConfig.getInitialDelayNs());
    }

    @Override
    public void processEvent(Event event) {
        long sendTimeNs = getOs().getSimulationTime();
        long sendTimeMs = sendTimeNs / NANOSECONDS_PER_MILLISECOND;
        String vehicleId = getOs().getId();
        String messageId = "live_cell_req__" + vehicleId + "__t_" + sendTimeMs;

        MessageRouting routing = getOs()
                .getCellModule()
                .createMessageRouting()
                .topological()
                .destination(cellConfig.destinationId)
                .tcp()
                .build();

        MaGaLiveCellDiagnosticRequestMessage message =
                new MaGaLiveCellDiagnosticRequestMessage(
                        routing,
                        messageId,
                        vehicleId,
                        cellConfig.destinationId,
                        sendTimeNs,
                        cellConfig.requestPayloadBytes
                );

        cellAccounting.recordControlledTraffic(
                sendTimeNs,
                messageId,
                LiveCellTrafficEvent.Direction.UPLINK,
                vehicleId,
                cellConfig.destinationId,
                cellConfig.requestPayloadBytes,
                cellConfig
        );
        String responseAccountingId = "live_cell_res_accounted__" + vehicleId + "__t_" + sendTimeMs;
        cellAccounting.recordControlledTraffic(
                sendTimeNs,
                responseAccountingId,
                LiveCellTrafficEvent.Direction.DOWNLINK,
                cellConfig.destinationId,
                vehicleId,
                cellConfig.responsePayloadBytes,
                cellConfig
        );
        getOs().getCellModule().sendV2xMessage(message);

        getLog().infoSimTime(
                this,
                "LIVE_CELL_DIAGNOSTIC_UPLINK_SEND"
                        + "|messageId=" + messageId
                        + "|vehicleId=" + vehicleId
                        + "|destinationId=" + cellConfig.destinationId
                        + "|sendTimeNs=" + sendTimeNs
                        + "|payloadBytes=" + cellConfig.requestPayloadBytes
        );
        getLog().infoSimTime(
                this,
                "LIVE_CELL_DIAGNOSTIC_DOWNLINK_ACCOUNTED"
                        + "|responseAccountingId=" + responseAccountingId
                        + "|sourceServerId=" + cellConfig.destinationId
                        + "|destinationVehicleId=" + vehicleId
                        + "|accountTimeNs=" + sendTimeNs
                        + "|payloadBytes=" + cellConfig.responsePayloadBytes
        );
        scheduleNext(cellConfig.getIntervalNs());
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (!(receivedV2xMessage.getMessage() instanceof MaGaLiveCellDiagnosticResponseMessage)) {
            return;
        }
        MaGaLiveCellDiagnosticResponseMessage response =
                (MaGaLiveCellDiagnosticResponseMessage) receivedV2xMessage.getMessage();
        getLog().infoSimTime(
                this,
                "LIVE_CELL_DIAGNOSTIC_DOWNLINK_RECEIVE"
                        + "|requestMessageId=" + response.getRequestMessageId()
                        + "|responseMessageId=" + response.getResponseMessageId()
                        + "|vehicleId=" + getOs().getId()
                        + "|sourceServerId=" + response.getSourceServerId()
                        + "|receiveTimeNs=" + getOs().getSimulationTime()
                        + "|payloadBytes=" + response.getPayloadBytes()
        );
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
                "LIVE_CELL_DIAGNOSTIC_VEHICLE_STOP"
                        + "|vehicleId=" + getOs().getId()
        );
    }

    private void scheduleNext(long delayNs) {
        getOs().getEventManager().addEvent(new Event(getOs().getSimulationTime() + delayNs, this));
    }
}
