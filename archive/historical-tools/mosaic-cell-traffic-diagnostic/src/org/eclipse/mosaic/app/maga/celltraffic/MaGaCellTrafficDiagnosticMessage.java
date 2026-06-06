package org.eclipse.mosaic.app.maga.celltraffic;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

/**
 * Uplink request carrying diagnostic Cell traffic metadata.
 */
public final class MaGaCellTrafficDiagnosticMessage extends V2xMessage {

    private final String messageId;
    private final String sourceVehicleId;
    private final String destinationId;
    private final long sendTimeNs;
    private final long requestPayloadBytes;
    private final EncodedPayload payload;

    public MaGaCellTrafficDiagnosticMessage(
            MessageRouting routing,
            String messageId,
            String sourceVehicleId,
            String destinationId,
            long sendTimeNs,
            long requestPayloadBytes
    ) {
        super(routing);
        if (messageId == null || messageId.trim().isEmpty()) {
            throw new IllegalArgumentException("messageId must not be empty");
        }
        if (sourceVehicleId == null || sourceVehicleId.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceVehicleId must not be empty");
        }
        if (destinationId == null || destinationId.trim().isEmpty()) {
            throw new IllegalArgumentException("destinationId must not be empty");
        }
        if (sendTimeNs < 0) {
            throw new IllegalArgumentException("sendTimeNs must be >= 0");
        }
        if (requestPayloadBytes <= 0) {
            throw new IllegalArgumentException("requestPayloadBytes must be > 0");
        }

        this.messageId = messageId.trim();
        this.sourceVehicleId = sourceVehicleId.trim();
        this.destinationId = destinationId.trim();
        this.sendTimeNs = sendTimeNs;
        this.requestPayloadBytes = requestPayloadBytes;
        this.payload = new EncodedPayload(requestPayloadBytes);
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSourceVehicleId() {
        return sourceVehicleId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public long getSendTimeNs() {
        return sendTimeNs;
    }

    public long getRequestPayloadBytes() {
        return requestPayloadBytes;
    }

    @Override
    public EncodedPayload getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "MaGaCellTrafficDiagnosticMessage{"
                + "messageId='" + messageId + '\''
                + ", sourceVehicleId='" + sourceVehicleId + '\''
                + ", destinationId='" + destinationId + '\''
                + ", sendTimeNs=" + sendTimeNs
                + ", requestPayloadBytes=" + requestPayloadBytes
                + '}';
    }
}
