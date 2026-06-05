package org.eclipse.mosaic.app.maga.livestate;

final class LiveV2vBandwidthPoolPreview {

    final long timeNs;
    final String poolId;
    final String poolType;
    final String memberVehicleA;
    final String memberVehicleB;
    final long availableBandwidthBitsPerSecond;
    final String bandwidthSource;

    LiveV2vBandwidthPoolPreview(
            long timeNs,
            String poolId,
            String memberVehicleA,
            String memberVehicleB,
            long availableBandwidthBitsPerSecond,
            String bandwidthSource
    ) {
        this.timeNs = timeNs;
        this.poolId = poolId;
        this.poolType = "DIRECT_V2V";
        this.memberVehicleA = memberVehicleA;
        this.memberVehicleB = memberVehicleB;
        this.availableBandwidthBitsPerSecond = availableBandwidthBitsPerSecond;
        this.bandwidthSource = bandwidthSource;
    }

    String toCsvRow() {
        return timeNs
                + "," + poolId
                + "," + poolType
                + "," + memberVehicleA
                + "," + memberVehicleB
                + "," + availableBandwidthBitsPerSecond
                + "," + bandwidthSource;
    }
}
