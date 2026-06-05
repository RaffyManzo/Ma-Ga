package org.eclipse.mosaic.app.maga.livestate;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

public final class MaGaLiveCellDiagnosticResponseMessage extends V2xMessage {

    private final String requestMessageId;
    private final String responseMessageId;
    private final String sourceServerId;
    private final String destinationVehicleId;
    private final long requestSendTimeNs;
    private final long payloadBytes;
    private final EncodedPayload payload;

    MaGaLiveCellDiagnosticResponseMessage(
            MessageRouting routing,
            String requestMessageId,
            String responseMessageId,
            String sourceServerId,
            String destinationVehicleId,
            long requestSendTimeNs,
            long payloadBytes
    ) {
        super(routing);
        this.requestMessageId = requireText(requestMessageId, "requestMessageId");
        this.responseMessageId = requireText(responseMessageId, "responseMessageId");
        this.sourceServerId = requireText(sourceServerId, "sourceServerId");
        this.destinationVehicleId = requireText(destinationVehicleId, "destinationVehicleId");
        this.requestSendTimeNs = requireNonNegative(requestSendTimeNs, "requestSendTimeNs");
        this.payloadBytes = requirePositive(payloadBytes, "payloadBytes");
        this.payload = new EncodedPayload(payloadBytes);
    }

    String getRequestMessageId() {
        return requestMessageId;
    }

    String getResponseMessageId() {
        return responseMessageId;
    }

    String getSourceServerId() {
        return sourceServerId;
    }

    String getDestinationVehicleId() {
        return destinationVehicleId;
    }

    long getRequestSendTimeNs() {
        return requestSendTimeNs;
    }

    long getPayloadBytes() {
        return payloadBytes;
    }

    @Override
    public EncodedPayload getPayload() {
        return payload;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value.trim();
    }

    private static long requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
        return value;
    }

    private static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
        return value;
    }
}
