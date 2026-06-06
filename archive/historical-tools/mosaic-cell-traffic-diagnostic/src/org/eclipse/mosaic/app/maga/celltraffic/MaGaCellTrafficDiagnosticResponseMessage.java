package org.eclipse.mosaic.app.maga.celltraffic;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

/**
 * Downlink response paired with one diagnostic Cell request.
 */
public final class MaGaCellTrafficDiagnosticResponseMessage extends V2xMessage {

    private final String requestMessageId;
    private final String responseMessageId;
    private final String sourceServerId;
    private final String destinationVehicleId;
    private final long requestSendTimeNs;
    private final long responsePayloadBytes;
    private final EncodedPayload payload;

    public MaGaCellTrafficDiagnosticResponseMessage(
            MessageRouting routing,
            String requestMessageId,
            String responseMessageId,
            String sourceServerId,
            String destinationVehicleId,
            long requestSendTimeNs,
            long responsePayloadBytes
    ) {
        super(routing);
        if (requestMessageId == null || requestMessageId.trim().isEmpty()) {
            throw new IllegalArgumentException("requestMessageId must not be empty");
        }
        if (responseMessageId == null || responseMessageId.trim().isEmpty()) {
            throw new IllegalArgumentException("responseMessageId must not be empty");
        }
        if (sourceServerId == null || sourceServerId.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceServerId must not be empty");
        }
        if (destinationVehicleId == null || destinationVehicleId.trim().isEmpty()) {
            throw new IllegalArgumentException("destinationVehicleId must not be empty");
        }
        if (requestSendTimeNs < 0) {
            throw new IllegalArgumentException("requestSendTimeNs must be >= 0");
        }
        if (responsePayloadBytes <= 0) {
            throw new IllegalArgumentException("responsePayloadBytes must be > 0");
        }

        this.requestMessageId = requestMessageId.trim();
        this.responseMessageId = responseMessageId.trim();
        this.sourceServerId = sourceServerId.trim();
        this.destinationVehicleId = destinationVehicleId.trim();
        this.requestSendTimeNs = requestSendTimeNs;
        this.responsePayloadBytes = responsePayloadBytes;
        this.payload = new EncodedPayload(responsePayloadBytes);
    }

    public String getRequestMessageId() {
        return requestMessageId;
    }

    public String getResponseMessageId() {
        return responseMessageId;
    }

    public String getSourceServerId() {
        return sourceServerId;
    }

    public String getDestinationVehicleId() {
        return destinationVehicleId;
    }

    public long getRequestSendTimeNs() {
        return requestSendTimeNs;
    }

    public long getResponsePayloadBytes() {
        return responsePayloadBytes;
    }

    @Override
    public EncodedPayload getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "MaGaCellTrafficDiagnosticResponseMessage{"
                + "requestMessageId='" + requestMessageId + '\''
                + ", responseMessageId='" + responseMessageId + '\''
                + ", sourceServerId='" + sourceServerId + '\''
                + ", destinationVehicleId='" + destinationVehicleId + '\''
                + ", requestSendTimeNs=" + requestSendTimeNs
                + ", responsePayloadBytes=" + responsePayloadBytes
                + '}';
    }
}
