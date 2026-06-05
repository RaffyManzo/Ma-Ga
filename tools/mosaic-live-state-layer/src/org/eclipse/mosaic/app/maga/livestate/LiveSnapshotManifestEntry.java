package org.eclipse.mosaic.app.maga.livestate;

final class LiveSnapshotManifestEntry {

    private final long timeNs;
    private final String snapshotId;
    private final String relativePath;
    private final int vehicles;
    private final int tasks;
    private final int candidates;
    private final int accessGateways;
    private final int accessLinks;
    private final int bandwidthPools;

    LiveSnapshotManifestEntry(
            long timeNs,
            String snapshotId,
            String relativePath,
            int vehicles,
            int tasks,
            int candidates,
            int accessGateways,
            int accessLinks,
            int bandwidthPools
    ) {
        this.timeNs = timeNs;
        this.snapshotId = snapshotId;
        this.relativePath = relativePath;
        this.vehicles = vehicles;
        this.tasks = tasks;
        this.candidates = candidates;
        this.accessGateways = accessGateways;
        this.accessLinks = accessLinks;
        this.bandwidthPools = bandwidthPools;
    }

    String toCsvRow() {
        return timeNs
                + "," + snapshotId
                + "," + relativePath
                + "," + vehicles
                + "," + tasks
                + "," + candidates
                + "," + accessGateways
                + "," + accessLinks
                + "," + bandwidthPools;
    }
}
