package org.eclipse.mosaic.app.maga.livestate;

final class LiveCellTrafficEvent {

    enum Direction {
        UPLINK,
        DOWNLINK
    }

    private final long timeNs;
    private final String eventId;
    private final String messageId;
    private final Direction direction;
    private final String sourceId;
    private final String destinationId;
    private final long payloadBytes;
    private final long bucketStartNs;

    LiveCellTrafficEvent(
            long timeNs,
            String eventId,
            String messageId,
            Direction direction,
            String sourceId,
            String destinationId,
            long payloadBytes,
            long bucketStartNs
    ) {
        this.timeNs = timeNs;
        this.eventId = eventId;
        this.messageId = messageId;
        this.direction = direction;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.payloadBytes = payloadBytes;
        this.bucketStartNs = bucketStartNs;
    }

    long getTimeNs() {
        return timeNs;
    }

    String getEventId() {
        return eventId;
    }

    String getMessageId() {
        return messageId;
    }

    Direction getDirection() {
        return direction;
    }

    String getSourceId() {
        return sourceId;
    }

    String getDestinationId() {
        return destinationId;
    }

    long getPayloadBytes() {
        return payloadBytes;
    }

    long getPayloadBits() {
        return payloadBytes * 8L;
    }

    long getBucketStartNs() {
        return bucketStartNs;
    }

    String toCsvRow() {
        return timeNs
                + "," + eventId
                + "," + messageId
                + "," + direction
                + "," + sourceId
                + "," + destinationId
                + "," + payloadBytes
                + "," + getPayloadBits()
                + "," + bucketStartNs;
    }
}
