package org.eclipse.mosaic.app.maga.livestate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LiveSystemSnapshotAssembler {

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    LiveSnapshotManifestEntry writeSnapshot(
            Path snapshotDir,
            LiveStateSnapshotView view,
            LiveLocalAndV2vCandidatePreviewBuilder.PreviewResult localAndV2v,
            LiveInfrastructurePreviewBuilder.PreviewResult infrastructure,
            MaGaLiveStateConfig config
    ) throws IOException {
        List<Map<String, Object>> vehicles = vehicles(view, config);
        Set<String> vehicleIds = ids(vehicles, "vehicleId");
        List<Map<String, Object>> tasks = tasks(view, vehicleIds);
        List<Map<String, Object>> localCandidates =
                localCandidates(localAndV2v.getLocalCandidates(), vehicleIds);
        List<Map<String, Object>> v2vCandidates =
                v2vCandidates(localAndV2v.getV2vCandidates(), vehicleIds);
        Set<String> v2vPoolIds = poolIdsFromCandidates(v2vCandidates);
        List<Map<String, Object>> directV2vPools =
                directV2vPools(localAndV2v.getV2vPools(), v2vPoolIds);

        Set<String> gatewayPoolIds = gatewayPoolIds(infrastructure.getGatewayPools());
        List<Map<String, Object>> accessGateways =
                accessGateways(config, gatewayPoolIds);
        Set<String> gatewayIds = ids(accessGateways, "gatewayId");
        List<Map<String, Object>> accessLinks =
                accessLinks(infrastructure.getAccessLinks(), vehicleIds, gatewayIds);
        List<Map<String, Object>> remoteCandidates =
                remoteCandidates(infrastructure.getRemoteCandidates(), vehicleIds, gatewayIds, gatewayPoolIds);
        Set<String> remotePoolIds = poolIdsFromCandidates(remoteCandidates);
        remotePoolIds.addAll(poolIdsFromGateways(accessGateways));
        List<Map<String, Object>> gatewayPools =
                gatewayPools(infrastructure.getGatewayPools(), remotePoolIds);

        List<Map<String, Object>> bandwidthPools = new ArrayList<>();
        bandwidthPools.addAll(directV2vPools);
        bandwidthPools.addAll(gatewayPools);
        if (vehicles.isEmpty() || bandwidthPools.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.addAll(localCandidates);
        candidates.addAll(v2vCandidates);
        candidates.addAll(remoteCandidates);

        String snapshotId = "live_snapshot_t_" + view.getTickTimeNs();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("snapshotId", snapshotId);
        snapshot.put("timeSeconds", view.getTickTimeNs() / 1_000_000_000.0);
        snapshot.put("vehicles", vehicles);
        snapshot.put("tasks", tasks);
        snapshot.put("candidateNodes", candidates);
        snapshot.put("accessGateways", accessGateways);
        snapshot.put("accessLinks", accessLinks);
        snapshot.put("bandwidthPools", bandwidthPools);

        Files.createDirectories(snapshotDir);
        String fileName = "snapshot_" + view.getTickTimeNs() + ".json";
        Files.write(
                snapshotDir.resolve(fileName),
                gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8)
        );
        return new LiveSnapshotManifestEntry(
                view.getTickTimeNs(),
                snapshotId,
                "snapshots/" + fileName,
                vehicles.size(),
                tasks.size(),
                candidates.size(),
                accessGateways.size(),
                accessLinks.size(),
                bandwidthPools.size()
        );
    }

    private List<Map<String, Object>> vehicles(
            LiveStateSnapshotView view,
            MaGaLiveStateConfig config
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LiveVehicleState vehicle : view.getActiveVehicles()) {
            if (!vehicle.hasFinitePosition() || !Double.isFinite(vehicle.getSpeedMetersPerSecond())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vehicleId", vehicle.getVehicleId());
            row.put("x", vehicle.getProjectedX());
            row.put("y", vehicle.getProjectedY());
            row.put("speed", vehicle.getSpeedMetersPerSecond());
            row.put("localCpu", (double) config.getLocalCpuCyclesPerSecond());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> tasks(LiveStateSnapshotView view, Set<String> vehicleIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LiveTaskState task : view.getPendingTasks()) {
            if (!vehicleIds.contains(task.getSourceVehicleId())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", task.getTaskId());
            row.put("sourceVehicleId", task.getSourceVehicleId());
            row.put("inputSizeBits", (double) task.getInputSizeBits());
            row.put("outputSizeBits", (double) task.getOutputSizeBits());
            row.put("cpuCycles", (double) task.getCpuCycles());
            row.put("deadlineSeconds", task.getDeadlineSeconds());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> localCandidates(
            List<LiveLocalCandidatePreview> candidates,
            Set<String> vehicleIds
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LiveLocalCandidatePreview candidate : candidates) {
            if (!vehicleIds.contains(candidate.sourceVehicleId)) {
                continue;
            }
            Map<String, Object> row = candidateBase(candidate);
            row.put("availableCpu", (double) candidate.availableCpu);
            row.put("availableBandwidth", 0.0);
            row.put("baseLatencySeconds", candidate.propagationDelaySeconds);
            row.put("nodeX", null);
            row.put("nodeY", null);
            row.put("coverageRadiusMeters", null);
            row.put("bandwidthPoolId", null);
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> candidateBase(LiveLocalCandidatePreview candidate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("candidateId", candidate.candidateId);
        row.put("sourceVehicleId", candidate.sourceVehicleId);
        row.put("executionNodeId", candidate.executionNodeId);
        row.put("type", candidate.type);
        return row;
    }

    private List<Map<String, Object>> v2vCandidates(
            List<LiveV2vCandidatePreview> candidates,
            Set<String> vehicleIds
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LiveV2vCandidatePreview candidate : candidates) {
            if (!vehicleIds.contains(candidate.sourceVehicleId)
                    || !vehicleIds.contains(candidate.executionNodeId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("candidateId", candidate.candidateId);
            row.put("sourceVehicleId", candidate.sourceVehicleId);
            row.put("executionNodeId", candidate.executionNodeId);
            row.put("type", candidate.type);
            row.put("availableCpu", (double) candidate.availableCpu);
            row.put("availableBandwidth", (double) candidate.availableBandwidthBitsPerSecond);
            row.put("baseLatencySeconds", candidate.propagationDelaySeconds);
            row.put("nodeX", null);
            row.put("nodeY", null);
            row.put("coverageRadiusMeters", null);
            row.put("bandwidthPoolId", candidate.bandwidthPoolId);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> directV2vPools(
            List<LiveV2vBandwidthPoolPreview> pools,
            Set<String> referencedPoolIds
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LiveV2vBandwidthPoolPreview pool : pools) {
            if (!referencedPoolIds.contains(pool.poolId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("poolId", pool.poolId);
            row.put("poolType", pool.poolType);
            row.put("availableBandwidth", (double) pool.availableBandwidthBitsPerSecond);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> accessGateways(
            MaGaLiveStateConfig config,
            Set<String> safeGatewayPoolIds
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaGaLiveStateConfig.Gateway gateway : config.getStaticInfrastructure().gateways) {
            if (!safeGatewayPoolIds.contains(gateway.bandwidthPoolId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gatewayId", gateway.gatewayId);
            row.put("gatewayType", gateway.gatewayType);
            row.put("x", gateway.projectedX);
            row.put("y", gateway.projectedY);
            row.put("coverageRadiusMeters", gateway.coverageRadiusMeters);
            row.put("bandwidthPoolId", gateway.bandwidthPoolId);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> accessLinks(
            List<LiveAccessLinkPreview> links,
            Set<String> vehicleIds,
            Set<String> gatewayIds
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LiveAccessLinkPreview link : links) {
            if (!vehicleIds.contains(link.vehicleId) || !gatewayIds.contains(link.gatewayId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accessLinkId", link.accessLinkId);
            row.put("vehicleId", link.vehicleId);
            row.put("gatewayId", link.gatewayId);
            row.put("active", link.active);
            row.put("available", link.available);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> remoteCandidates(
            List<LiveRemoteCandidatePreview> candidates,
            Set<String> vehicleIds,
            Set<String> gatewayIds,
            Set<String> gatewayPoolIds
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LiveRemoteCandidatePreview candidate : candidates) {
            if (!vehicleIds.contains(candidate.sourceVehicleId)
                    || !gatewayIds.contains(candidate.gatewayId)
                    || !gatewayPoolIds.contains(candidate.bandwidthPoolId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("candidateId", candidate.candidateId);
            row.put("sourceVehicleId", candidate.sourceVehicleId);
            row.put("executionNodeId", candidate.executionNodeId);
            row.put("type", candidate.type);
            row.put("availableCpu", candidate.availableCpu);
            row.put("availableBandwidth", candidate.availableBandwidthBitsPerSecond);
            row.put("baseLatencySeconds", candidate.propagationDelaySeconds);
            row.put("nodeX", candidate.nodeX);
            row.put("nodeY", candidate.nodeY);
            row.put("coverageRadiusMeters", candidate.coverageRadiusMeters);
            row.put("bandwidthPoolId", candidate.bandwidthPoolId);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> gatewayPools(
            List<LiveGatewayBandwidthPoolPreview> pools,
            Set<String> referencedPoolIds
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LiveGatewayBandwidthPoolPreview pool : pools) {
            if (!referencedPoolIds.contains(pool.poolId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("poolId", pool.poolId);
            row.put("poolType", pool.poolType);
            row.put("availableBandwidth", pool.availableBandwidthBitsPerSecond);
            rows.add(row);
        }
        return rows;
    }

    private Set<String> poolIdsFromCandidates(List<Map<String, Object>> candidates) {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> candidate : candidates) {
            Object poolId = candidate.get("bandwidthPoolId");
            if (poolId instanceof String && !((String) poolId).isEmpty()) {
                ids.add((String) poolId);
            }
        }
        return ids;
    }

    private Set<String> poolIdsFromGateways(List<Map<String, Object>> gateways) {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> gateway : gateways) {
            Object poolId = gateway.get("bandwidthPoolId");
            if (poolId instanceof String && !((String) poolId).isEmpty()) {
                ids.add((String) poolId);
            }
        }
        return ids;
    }

    private Set<String> gatewayPoolIds(List<LiveGatewayBandwidthPoolPreview> pools) {
        Set<String> ids = new HashSet<>();
        for (LiveGatewayBandwidthPoolPreview pool : pools) {
            ids.add(pool.poolId);
        }
        return ids;
    }

    private Set<String> ids(List<Map<String, Object>> rows, String field) {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(field);
            if (value instanceof String && !((String) value).isEmpty()) {
                ids.add((String) value);
            }
        }
        return ids;
    }
}
