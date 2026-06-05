package org.eclipse.mosaic.app.maga.livestate;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

public final class MaGaLiveCellDiagnosticRequestMessage extends V2xMessage {

    private final String messageId;
    private final String sourceVehicleId;
    private final String destinationId;
    private final long sendTimeNs;
    private final long payloadBytes;
    private final EncodedPayload payload;

    MaGaLiveCellDiagnosticRequestMessage(
            MessageRouting routing,
            String messageId,
            String sourceVehicleId,
            String destinationId,
            long sendTimeNs,
            long payloadBytes
    ) {
        super(routing);
        this.messageId = requireText(messageId, "messageId");
        this.sourceVehicleId = requireText(sourceVehicleId, "sourceVehicleId");
        this.destinationId = requireText(destinationId, "destinationId");
        this.sendTimeNs = requireNonNegative(sendTimeNs, "sendTimeNs");
        this.payloadBytes = requirePositive(payloadBytes, "payloadBytes");
        this.payload = new EncodedPayload(payloadBytes);
    }

    String getMessageId() {
        return messageId;
    }

    String getSourceVehicleId() {
        return sourceVehicleId;
    }

    String getDestinationId() {
        return destinationId;
    }

    long getSendTimeNs() {
        return sendTimeNs;
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
