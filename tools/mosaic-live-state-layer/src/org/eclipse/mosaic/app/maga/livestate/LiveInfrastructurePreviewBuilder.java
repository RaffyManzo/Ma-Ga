package org.eclipse.mosaic.app.maga.livestate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LiveInfrastructurePreviewBuilder {

    PreviewResult build(
            LiveStateSnapshotView view,
            MaGaLiveStateConfig config,
            List<LiveCellBandwidthBucket> safeBuckets
    ) {
        Map<String, LiveGatewayBandwidthPoolPreview> gatewayPoolsById =
                buildGatewayPools(view.getTickTimeNs(), safeBuckets);
        List<LiveAccessLinkPreview> accessLinks = buildAccessLinks(view, config);
        Map<String, LiveAccessLinkPreview> activeAccessLinksByVehicle = activeLinksByVehicle(accessLinks);
        List<LiveRemoteCandidatePreview> remoteCandidates =
                buildRemoteCandidates(view, config, activeAccessLinksByVehicle, gatewayPoolsById);

        List<LiveGatewayBandwidthPoolPreview> gatewayPools = new ArrayList<>(gatewayPoolsById.values());
        gatewayPools.sort(Comparator.comparing(pool -> pool.poolId));
        remoteCandidates.sort(
                Comparator
                        .comparing((LiveRemoteCandidatePreview candidate) -> candidate.sourceVehicleId, LiveStateCache::naturalCompare)
                        .thenComparing(candidate -> candidate.type)
                        .thenComparing(candidate -> candidate.executionNodeId)
        );
        return new PreviewResult(accessLinks, gatewayPools, remoteCandidates);
    }

    private Map<String, LiveGatewayBandwidthPoolPreview> buildGatewayPools(
            long tickTimeNs,
            List<LiveCellBandwidthBucket> safeBuckets
    ) {
        Map<String, LiveCellBandwidthBucket> uplinkByPool = new HashMap<>();
        Map<String, LiveCellBandwidthBucket> downlinkByPool = new HashMap<>();
        for (LiveCellBandwidthBucket bucket : safeBuckets) {
            if (bucket.getDirection() == LiveCellTrafficEvent.Direction.UPLINK) {
                uplinkByPool.put(bucket.getPoolId(), bucket);
            } else if (bucket.getDirection() == LiveCellTrafficEvent.Direction.DOWNLINK) {
                downlinkByPool.put(bucket.getPoolId(), bucket);
            }
        }

        Map<String, LiveGatewayBandwidthPoolPreview> poolsById = new HashMap<>();
        for (Map.Entry<String, LiveCellBandwidthBucket> entry : uplinkByPool.entrySet()) {
            String poolId = entry.getKey();
            LiveCellBandwidthBucket uplink = entry.getValue();
            LiveCellBandwidthBucket downlink = downlinkByPool.get(poolId);
            if (downlink == null) {
                continue;
            }
            double availableBandwidth = Math.min(
                    uplink.getResidualCapacityBitsPerSecond(),
                    downlink.getResidualCapacityBitsPerSecond()
            );
            if (availableBandwidth <= 0.0) {
                continue;
            }
            poolsById.put(
                    poolId,
                    new LiveGatewayBandwidthPoolPreview(
                            tickTimeNs,
                            poolId,
                            availableBandwidth,
                            uplink.getResidualCapacityBitsPerSecond(),
                            downlink.getResidualCapacityBitsPerSecond(),
                            uplink.getBucketStartNs(),
                            downlink.getBucketStartNs(),
                            Math.max(uplink.getAvailableFromNs(), downlink.getAvailableFromNs()),
                            uplink.getBandwidthSource()
                    )
            );
        }
        return poolsById;
    }

    private List<LiveAccessLinkPreview> buildAccessLinks(
            LiveStateSnapshotView view,
            MaGaLiveStateConfig config
    ) {
        List<LiveAccessLinkPreview> links = new ArrayList<>();
        for (LiveVehicleState vehicle : view.getActiveVehicles()) {
            MaGaLiveStateConfig.Gateway activeGateway = activeGateway(vehicle, config);
            for (MaGaLiveStateConfig.Gateway gateway : config.getStaticInfrastructure().gateways) {
                double distance = distanceMeters(vehicle, gateway);
                boolean available = vehicle.hasFinitePosition() && distance <= gateway.coverageRadiusMeters;
                boolean active = activeGateway != null && activeGateway.gatewayId.equals(gateway.gatewayId);
                links.add(
                        new LiveAccessLinkPreview(
                                view.getTickTimeNs(),
                                "access_link__" + vehicle.getVehicleId() + "__" + gateway.gatewayId,
                                vehicle.getVehicleId(),
                                gateway.gatewayId,
                                gateway.runtimeId,
                                distance,
                                gateway.coverageRadiusMeters,
                                active,
                                available,
                                gateway.cellRegionId,
                                gateway.bandwidthPoolId
                        )
                );
            }
        }
        links.sort(
                Comparator
                        .comparing((LiveAccessLinkPreview link) -> link.vehicleId, LiveStateCache::naturalCompare)
                        .thenComparing(link -> link.gatewayId)
        );
        return links;
    }

    private MaGaLiveStateConfig.Gateway activeGateway(
            LiveVehicleState vehicle,
            MaGaLiveStateConfig config
    ) {
        MaGaLiveStateConfig.Gateway best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (MaGaLiveStateConfig.Gateway gateway : config.getStaticInfrastructure().gateways) {
            double distance = distanceMeters(vehicle, gateway);
            if (!vehicle.hasFinitePosition() || distance > gateway.coverageRadiusMeters) {
                continue;
            }
            if (best == null
                    || distance < bestDistance
                    || (distance == bestDistance && gateway.gatewayId.compareTo(best.gatewayId) < 0)) {
                best = gateway;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Map<String, LiveAccessLinkPreview> activeLinksByVehicle(List<LiveAccessLinkPreview> accessLinks) {
        Map<String, LiveAccessLinkPreview> result = new HashMap<>();
        for (LiveAccessLinkPreview link : accessLinks) {
            if (link.active) {
                result.put(link.vehicleId, link);
            }
        }
        return result;
    }

    private List<LiveRemoteCandidatePreview> buildRemoteCandidates(
            LiveStateSnapshotView view,
            MaGaLiveStateConfig config,
            Map<String, LiveAccessLinkPreview> activeAccessLinksByVehicle,
            Map<String, LiveGatewayBandwidthPoolPreview> gatewayPoolsById
    ) {
        List<LiveRemoteCandidatePreview> candidates = new ArrayList<>();
        for (LiveVehicleState vehicle : view.getActiveVehicles()) {
            LiveAccessLinkPreview activeLink = activeAccessLinksByVehicle.get(vehicle.getVehicleId());
            if (activeLink == null) {
                continue;
            }
            LiveGatewayBandwidthPoolPreview gatewayPool =
                    gatewayPoolsById.get(activeLink.bandwidthPoolId);
            if (gatewayPool == null) {
                continue;
            }
            MaGaLiveStateConfig.Gateway gateway = gatewayById(config, activeLink.gatewayId);
            if (gateway == null) {
                continue;
            }
            addEdgeCandidates(view, config, candidates, vehicle, gateway, gatewayPool);
            addCloudCandidates(view, config, candidates, vehicle, gateway, gatewayPool);
        }
        return candidates;
    }

    private void addEdgeCandidates(
            LiveStateSnapshotView view,
            MaGaLiveStateConfig config,
            List<LiveRemoteCandidatePreview> candidates,
            LiveVehicleState vehicle,
            MaGaLiveStateConfig.Gateway gateway,
            LiveGatewayBandwidthPoolPreview gatewayPool
    ) {
        for (MaGaLiveStateConfig.EdgeNode edgeNode : config.getStaticInfrastructure().edgeNodes) {
            if (!edgeNode.gatewayIds.contains(gateway.gatewayId)) {
                continue;
            }
            candidates.add(
                    new LiveRemoteCandidatePreview(
                            view.getTickTimeNs(),
                            "edge_" + edgeNode.executionNodeId + "_via_" + gateway.gatewayId + "_for_" + vehicle.getVehicleId(),
                            vehicle.getVehicleId(),
                            edgeNode.executionNodeId,
                            "EDGE",
                            gateway.gatewayId,
                            gateway.bandwidthPoolId,
                            edgeNode.availableCpuCyclesPerSecond,
                            gatewayPool.availableBandwidthBitsPerSecond,
                            edgeNode.basePropagationDelaySeconds,
                            gateway.projectedX,
                            gateway.projectedY,
                            gateway.coverageRadiusMeters
                    )
            );
        }
    }

    private void addCloudCandidates(
            LiveStateSnapshotView view,
            MaGaLiveStateConfig config,
            List<LiveRemoteCandidatePreview> candidates,
            LiveVehicleState vehicle,
            MaGaLiveStateConfig.Gateway gateway,
            LiveGatewayBandwidthPoolPreview gatewayPool
    ) {
        for (MaGaLiveStateConfig.CloudNode cloudNode : config.getStaticInfrastructure().cloudNodes) {
            candidates.add(
                    new LiveRemoteCandidatePreview(
                            view.getTickTimeNs(),
                            "cloud_" + cloudNode.executionNodeId + "_via_" + gateway.gatewayId + "_for_" + vehicle.getVehicleId(),
                            vehicle.getVehicleId(),
                            cloudNode.executionNodeId,
                            "CLOUD",
                            gateway.gatewayId,
                            gateway.bandwidthPoolId,
                            cloudNode.availableCpuCyclesPerSecond,
                            gatewayPool.availableBandwidthBitsPerSecond,
                            cloudNode.serverBaseDelaySeconds,
                            null,
                            null,
                            null
                    )
            );
        }
    }

    private MaGaLiveStateConfig.Gateway gatewayById(
            MaGaLiveStateConfig config,
            String gatewayId
    ) {
        for (MaGaLiveStateConfig.Gateway gateway : config.getStaticInfrastructure().gateways) {
            if (gateway.gatewayId.equals(gatewayId)) {
                return gateway;
            }
        }
        return null;
    }

    private static double distanceMeters(LiveVehicleState vehicle, MaGaLiveStateConfig.Gateway gateway) {
        if (!vehicle.hasFinitePosition()) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = vehicle.getProjectedX() - gateway.projectedX;
        double dy = vehicle.getProjectedY() - gateway.projectedY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    static final class PreviewResult {
        private final List<LiveAccessLinkPreview> accessLinks;
        private final List<LiveGatewayBandwidthPoolPreview> gatewayPools;
        private final List<LiveRemoteCandidatePreview> remoteCandidates;

        PreviewResult(
                List<LiveAccessLinkPreview> accessLinks,
                List<LiveGatewayBandwidthPoolPreview> gatewayPools,
                List<LiveRemoteCandidatePreview> remoteCandidates
        ) {
            this.accessLinks = accessLinks;
            this.gatewayPools = gatewayPools;
            this.remoteCandidates = remoteCandidates;
        }

        List<LiveAccessLinkPreview> getAccessLinks() {
            return accessLinks;
        }

        List<LiveGatewayBandwidthPoolPreview> getGatewayPools() {
            return gatewayPools;
        }

        List<LiveRemoteCandidatePreview> getRemoteCandidates() {
            return remoteCandidates;
        }
    }
}
