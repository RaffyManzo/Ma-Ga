package org.eclipse.mosaic.app.maga.livestate;

import java.util.Locale;

final class LiveCellBandwidthBucket {

    private final String poolId;
    private final LiveCellTrafficEvent.Direction direction;
    private final long bucketStartNs;
    private final long bucketEndNs;
    private final long availableFromNs;
    private final long nominalCapacityBitsPerSecond;
    private final double trafficObservedBitsPerSecond;
    private final double residualCapacityBitsPerSecond;
    private final String bandwidthSource;

    LiveCellBandwidthBucket(
            String poolId,
            LiveCellTrafficEvent.Direction direction,
            long bucketStartNs,
            long bucketEndNs,
            long availableFromNs,
            long nominalCapacityBitsPerSecond,
            double trafficObservedBitsPerSecond,
            double residualCapacityBitsPerSecond,
            String bandwidthSource
    ) {
        this.poolId = poolId;
        this.direction = direction;
        this.bucketStartNs = bucketStartNs;
        this.bucketEndNs = bucketEndNs;
        this.availableFromNs = availableFromNs;
        this.nominalCapacityBitsPerSecond = nominalCapacityBitsPerSecond;
        this.trafficObservedBitsPerSecond = trafficObservedBitsPerSecond;
        this.residualCapacityBitsPerSecond = residualCapacityBitsPerSecond;
        this.bandwidthSource = bandwidthSource;
    }

    String getPoolId() {
        return poolId;
    }

    LiveCellTrafficEvent.Direction getDirection() {
        return direction;
    }

    long getBucketStartNs() {
        return bucketStartNs;
    }

    long getAvailableFromNs() {
        return availableFromNs;
    }

    double getResidualCapacityBitsPerSecond() {
        return residualCapacityBitsPerSecond;
    }

    String getBandwidthSource() {
        return bandwidthSource;
    }

    String toCsvRow(long tickTimeNs) {
        return tickTimeNs
                + "," + poolId
                + "," + direction
                + "," + bucketStartNs
                + "," + bucketEndNs
                + "," + availableFromNs
                + "," + nominalCapacityBitsPerSecond
                + "," + format(trafficObservedBitsPerSecond)
                + "," + format(residualCapacityBitsPerSecond)
                + "," + bandwidthSource;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
