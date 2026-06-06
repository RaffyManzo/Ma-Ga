package org.eclipse.mosaic.app.maga.liveruntime;

import model.snapshot.SystemSnapshot;
import org.eclipse.mosaic.app.maga.livestate.LiveStateLayerRuntimeFacade;
import window.source.MosaicSnapshotBridge;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MaGaLiveMosaicSnapshotBridge implements MosaicSnapshotBridge {

    public static final String DESCRIPTION = "LIVE_CAUSAL_MOSAIC_SNAPSHOT_BRIDGE";
    private static final double EPSILON_SECONDS = 1.0E-9;

    private final ConcurrentSkipListMap<Double, SystemSnapshot> snapshotsByTime = new ConcurrentSkipListMap<>();
    private final Map<String, LiveStateLayerRuntimeFacade.LivePublishedSnapshotAudit> auditsBySnapshotId =
            new HashMap<>();
    private final AtomicInteger snapshotsRequested = new AtomicInteger();
    private final AtomicInteger snapshotsResolved = new AtomicInteger();
    private final AtomicInteger emptyResponses = new AtomicInteger();
    private final AtomicInteger futureSnapshotViolations = new AtomicInteger();
    private final AtomicInteger futurePoolViolations = new AtomicInteger();
    private final AtomicInteger invalidPoolBandwidthViolations = new AtomicInteger();

    public synchronized void publishSnapshot(
            SystemSnapshot snapshot,
            LiveStateLayerRuntimeFacade.LivePublishedSnapshotAudit audit
    ) {
        if (snapshot == null) {
            return;
        }
        if (audit != null) {
            if (!snapshot.getSnapshotId().equals(audit.getSnapshotId())) {
                futureSnapshotViolations.incrementAndGet();
                return;
            }
            futurePoolViolations.addAndGet(audit.getFuturePoolViolations());
            invalidPoolBandwidthViolations.addAndGet(audit.getInvalidPoolBandwidthViolations());
            auditsBySnapshotId.put(snapshot.getSnapshotId(), audit);
        }
        snapshotsByTime.put(snapshot.getTimeSeconds(), snapshot);
    }

    @Override
    public synchronized Optional<SystemSnapshot> readSnapshot(double observationTimeSeconds) {
        snapshotsRequested.incrementAndGet();
        Map.Entry<Double, SystemSnapshot> entry =
                snapshotsByTime.floorEntry(observationTimeSeconds + EPSILON_SECONDS);
        if (entry == null) {
            emptyResponses.incrementAndGet();
            return Optional.empty();
        }
        SystemSnapshot snapshot = entry.getValue();
        if (snapshot.getTimeSeconds() > observationTimeSeconds + EPSILON_SECONDS) {
            futureSnapshotViolations.incrementAndGet();
            emptyResponses.incrementAndGet();
            return Optional.empty();
        }
        LiveStateLayerRuntimeFacade.LivePublishedSnapshotAudit audit =
                auditsBySnapshotId.get(snapshot.getSnapshotId());
        if (audit != null) {
            futurePoolViolations.addAndGet(audit.getFuturePoolViolations());
            invalidPoolBandwidthViolations.addAndGet(audit.getInvalidPoolBandwidthViolations());
        }
        snapshotsResolved.incrementAndGet();
        return Optional.of(snapshot);
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    public int getSnapshotsRequested() {
        return snapshotsRequested.get();
    }

    public int getSnapshotsResolved() {
        return snapshotsResolved.get();
    }

    public int getEmptyResponses() {
        return emptyResponses.get();
    }

    public int getFutureSnapshotViolations() {
        return futureSnapshotViolations.get();
    }

    public int getFuturePoolViolations() {
        return futurePoolViolations.get();
    }

    public int getInvalidPoolBandwidthViolations() {
        return invalidPoolBandwidthViolations.get();
    }
}
