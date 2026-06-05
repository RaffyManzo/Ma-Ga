package org.eclipse.mosaic.app.maga.livestate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LiveCellTrafficAccountingCache {

    private static final LiveCellTrafficAccountingCache INSTANCE = new LiveCellTrafficAccountingCache();

    private final List<LiveCellTrafficEvent> events = new ArrayList<>();

    static LiveCellTrafficAccountingCache getInstance() {
        return INSTANCE;
    }

    synchronized void reset() {
        events.clear();
    }

    synchronized void recordControlledTraffic(
            long timeNs,
            String messageId,
            LiveCellTrafficEvent.Direction direction,
            String sourceId,
            String destinationId,
            long payloadBytes,
            MaGaLiveStateConfig.CellDiagnosticAccounting config
    ) {
        long bucketStartNs = bucketStart(timeNs, config.getBucketDurationNs());
        String eventId = "cell_" + direction.name().toLowerCase() + "__" + messageId + "__t_" + timeNs;
        events.add(
                new LiveCellTrafficEvent(
                        timeNs,
                        eventId,
                        messageId,
                        direction,
                        sourceId,
                        destinationId,
                        payloadBytes,
                        bucketStartNs
                )
        );
    }

    synchronized List<LiveCellTrafficEvent> eventsAtOrBefore(long tickTimeNs) {
        List<LiveCellTrafficEvent> copy = new ArrayList<>();
        for (LiveCellTrafficEvent event : events) {
            if (event.getTimeNs() <= tickTimeNs) {
                copy.add(event);
            }
        }
        copy.sort(
                Comparator
                        .comparingLong(LiveCellTrafficEvent::getTimeNs)
                        .thenComparing(LiveCellTrafficEvent::getEventId)
        );
        return copy;
    }

    synchronized List<LiveCellBandwidthBucket> latestSafeBuckets(
            long tickTimeNs,
            MaGaLiveStateConfig.CellDiagnosticAccounting config
    ) {
        long bucketDurationNs = config.getBucketDurationNs();
        Map<LiveCellTrafficEvent.Direction, Map<Long, Long>> trafficBits = new EnumMap<>(LiveCellTrafficEvent.Direction.class);
        for (LiveCellTrafficEvent.Direction direction : LiveCellTrafficEvent.Direction.values()) {
            trafficBits.put(direction, new HashMap<Long, Long>());
        }

        for (LiveCellTrafficEvent event : events) {
            if (event.getTimeNs() > tickTimeNs) {
                continue;
            }
            long bucketStartNs = event.getBucketStartNs();
            long availableFromNs = bucketStartNs + bucketDurationNs;
            if (availableFromNs > tickTimeNs) {
                continue;
            }
            Map<Long, Long> byBucket = trafficBits.get(event.getDirection());
            byBucket.put(bucketStartNs, byBucket.getOrDefault(bucketStartNs, 0L) + event.getPayloadBits());
        }

        List<LiveCellBandwidthBucket> buckets = new ArrayList<>();
        for (MaGaLiveStateConfig.GatewayPool pool : config.gatewayPools) {
            for (LiveCellTrafficEvent.Direction direction : LiveCellTrafficEvent.Direction.values()) {
                Map<Long, Long> byBucket = trafficBits.get(direction);
                Long latestBucketStart = latestBucketStart(byBucket);
                if (latestBucketStart == null) {
                    continue;
                }
                long observedBits = byBucket.get(latestBucketStart);
                double seconds = bucketDurationNs / 1_000_000_000.0;
                double observedBitsPerSecond = observedBits / seconds;
                double residual = Math.max(0.0, pool.nominalCapacityBitsPerSecond - observedBitsPerSecond);
                buckets.add(
                        new LiveCellBandwidthBucket(
                                pool.poolId,
                                direction,
                                latestBucketStart,
                                latestBucketStart + bucketDurationNs,
                                latestBucketStart + bucketDurationNs,
                                pool.nominalCapacityBitsPerSecond,
                                observedBitsPerSecond,
                                residual,
                                config.bandwidthSource
                        )
                );
            }
        }
        buckets.sort(
                Comparator
                        .comparing(LiveCellBandwidthBucket::getPoolId)
                        .thenComparing(bucket -> bucket.getDirection().name())
        );
        return buckets;
    }

    private static Long latestBucketStart(Map<Long, Long> byBucket) {
        Long latest = null;
        for (Long bucketStart : byBucket.keySet()) {
            if (latest == null || bucketStart > latest) {
                latest = bucketStart;
            }
        }
        return latest;
    }

    private static long bucketStart(long timeNs, long bucketDurationNs) {
        return (timeNs / bucketDurationNs) * bucketDurationNs;
    }
}
