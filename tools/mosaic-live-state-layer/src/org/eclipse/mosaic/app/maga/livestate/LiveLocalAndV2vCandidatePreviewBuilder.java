package org.eclipse.mosaic.app.maga.livestate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LiveLocalAndV2vCandidatePreviewBuilder {

    PreviewResult build(LiveStateSnapshotView view, MaGaLiveStateConfig config) {
        List<LiveLocalCandidatePreview> localCandidates = new ArrayList<>();
        for (LiveVehicleState vehicle : view.getActiveVehicles()) {
            localCandidates.add(
                    new LiveLocalCandidatePreview(
                            view.getTickTimeNs(),
                            "local_for_" + vehicle.getVehicleId(),
                            vehicle.getVehicleId(),
                            vehicle.getVehicleId(),
                            config.getLocalCpuCyclesPerSecond(),
                            config.getLocalCpuSource()
                    )
            );
        }

        List<LiveV2vCandidatePreview> v2vCandidates = new ArrayList<>();
        Map<String, LiveV2vBandwidthPoolPreview> poolsById = new LinkedHashMap<>();
        List<LiveVehicleState> vehicles = view.getActiveVehicles();
        for (int i = 0; i < vehicles.size(); i++) {
            LiveVehicleState left = vehicles.get(i);
            for (int j = i + 1; j < vehicles.size(); j++) {
                LiveVehicleState right = vehicles.get(j);
                if (!isDirectPairEligible(left, right, config.getSinglehopRadiusMeters())) {
                    continue;
                }
                double distance = distanceMeters(left, right);
                String[] ordered = orderedPair(left.getVehicleId(), right.getVehicleId());
                String poolId = "direct_v2v_pool__" + ordered[0] + "__" + ordered[1];
                poolsById.putIfAbsent(
                        poolId,
                        new LiveV2vBandwidthPoolPreview(
                                view.getTickTimeNs(),
                                poolId,
                                ordered[0],
                                ordered[1],
                                config.getV2vNominalBandwidthBitsPerSecond(),
                                config.getV2vBandwidthSource()
                        )
                );
                addDirectionalCandidate(view, config, v2vCandidates, left, right, distance, poolId);
                addDirectionalCandidate(view, config, v2vCandidates, right, left, distance, poolId);
            }
        }

        v2vCandidates.sort(
                Comparator
                        .comparing((LiveV2vCandidatePreview item) -> item.sourceVehicleId, LiveStateCache::naturalCompare)
                        .thenComparing(item -> item.targetVehicleId, LiveStateCache::naturalCompare)
        );
        List<LiveV2vBandwidthPoolPreview> pools = new ArrayList<>(poolsById.values());
        pools.sort(Comparator.comparing(item -> item.poolId));
        return new PreviewResult(localCandidates, v2vCandidates, pools);
    }

    private static boolean isDirectPairEligible(
            LiveVehicleState left,
            LiveVehicleState right,
            double singlehopRadiusMeters
    ) {
        if (left.getVehicleId().equals(right.getVehicleId())) {
            return false;
        }
        if (!left.isActive() || !right.isActive()) {
            return false;
        }
        if (!left.isAdHocEnabled() || !right.isAdHocEnabled()) {
            return false;
        }
        if (!left.hasFinitePosition() || !right.hasFinitePosition()) {
            return false;
        }
        return distanceMeters(left, right) <= singlehopRadiusMeters;
    }

    private static void addDirectionalCandidate(
            LiveStateSnapshotView view,
            MaGaLiveStateConfig config,
            List<LiveV2vCandidatePreview> candidates,
            LiveVehicleState source,
            LiveVehicleState target,
            double distance,
            String poolId
    ) {
        candidates.add(
                new LiveV2vCandidatePreview(
                        view.getTickTimeNs(),
                        "vehicle_" + target.getVehicleId() + "_for_" + source.getVehicleId(),
                        source.getVehicleId(),
                        target.getVehicleId(),
                        distance,
                        config.getLocalCpuCyclesPerSecond(),
                        poolId,
                        config.getV2vNominalBandwidthBitsPerSecond(),
                        config.getV2vBandwidthSource(),
                        config.getV2vPropagationDelaySeconds()
                )
        );
    }

    private static double distanceMeters(LiveVehicleState left, LiveVehicleState right) {
        double dx = left.getProjectedX() - right.getProjectedX();
        double dy = left.getProjectedY() - right.getProjectedY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static String[] orderedPair(String left, String right) {
        if (LiveStateCache.naturalCompare(left, right) <= 0) {
            return new String[]{left, right};
        }
        return new String[]{right, left};
    }

    static final class PreviewResult {
        private final List<LiveLocalCandidatePreview> localCandidates;
        private final List<LiveV2vCandidatePreview> v2vCandidates;
        private final List<LiveV2vBandwidthPoolPreview> v2vPools;

        PreviewResult(
                List<LiveLocalCandidatePreview> localCandidates,
                List<LiveV2vCandidatePreview> v2vCandidates,
                List<LiveV2vBandwidthPoolPreview> v2vPools
        ) {
            this.localCandidates = localCandidates;
            this.v2vCandidates = v2vCandidates;
            this.v2vPools = v2vPools;
        }

        List<LiveLocalCandidatePreview> getLocalCandidates() {
            return localCandidates;
        }

        List<LiveV2vCandidatePreview> getV2vCandidates() {
            return v2vCandidates;
        }

        List<LiveV2vBandwidthPoolPreview> getV2vPools() {
            return v2vPools;
        }
    }
}
