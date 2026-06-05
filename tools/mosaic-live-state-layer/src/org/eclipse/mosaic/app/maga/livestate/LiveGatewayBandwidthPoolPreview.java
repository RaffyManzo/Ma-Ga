package org.eclipse.mosaic.app.maga.livestate;

import java.util.Locale;

final class LiveGatewayBandwidthPoolPreview {

    final long timeNs;
    final String poolId;
    final String poolType;
    final double availableBandwidthBitsPerSecond;
    final double uplinkResidualBitsPerSecond;
    final double downlinkResidualBitsPerSecond;
    final long uplinkBucketStartNs;
    final long downlinkBucketStartNs;
    final long availableFromNs;
    final String bandwidthSource;

    LiveGatewayBandwidthPoolPreview(
            long timeNs,
            String poolId,
            double availableBandwidthBitsPerSecond,
            double uplinkResidualBitsPerSecond,
            double downlinkResidualBitsPerSecond,
            long uplinkBucketStartNs,
            long downlinkBucketStartNs,
            long availableFromNs,
            String bandwidthSource
    ) {
        this.timeNs = timeNs;
        this.poolId = poolId;
        this.poolType = "GATEWAY";
        this.availableBandwidthBitsPerSecond = availableBandwidthBitsPerSecond;
        this.uplinkResidualBitsPerSecond = uplinkResidualBitsPerSecond;
        this.downlinkResidualBitsPerSecond = downlinkResidualBitsPerSecond;
        this.uplinkBucketStartNs = uplinkBucketStartNs;
        this.downlinkBucketStartNs = downlinkBucketStartNs;
        this.availableFromNs = availableFromNs;
        this.bandwidthSource = bandwidthSource;
    }

    String toCsvRow() {
        return timeNs
                + "," + poolId
                + "," + poolType
                + "," + format(availableBandwidthBitsPerSecond)
                + "," + format(uplinkResidualBitsPerSecond)
                + "," + format(downlinkResidualBitsPerSecond)
                + "," + uplinkBucketStartNs
                + "," + downlinkBucketStartNs
                + "," + availableFromNs
                + "," + bandwidthSource;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
