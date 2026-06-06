package org.eclipse.mosaic.app.maga.liveruntime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;
import org.eclipse.mosaic.app.maga.livestate.LiveStateLayerRuntimeFacade;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class LiveRuntimeTraceWriter implements AutoCloseable {

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final String profile;
    private final Path outputDir;
    private final Path publishedSnapshotsDir;
    private final int publishedSnapshotCopyLimit;
    private final Set<String> publishedSnapshotIds = new HashSet<>();
    private final BufferedWriter runtimeTraceWriter;
    private final BufferedWriter strategyTraceWriter;
    private final BufferedWriter bridgeSnapshotTraceWriter;
    private final BufferedWriter overrunTraceWriter;
    private final BufferedWriter publishedSnapshotManifestWriter;

    LiveRuntimeTraceWriter(
            Path runDirectory,
            String profile,
            int publishedSnapshotCopyLimit
    ) throws IOException {
        this.profile = profile;
        this.publishedSnapshotCopyLimit = publishedSnapshotCopyLimit;
        this.outputDir = runDirectory.resolve("live-maga-runtime");
        this.publishedSnapshotsDir = outputDir.resolve("published-snapshots");
        Files.createDirectories(outputDir);
        Files.createDirectories(publishedSnapshotsDir);
        this.runtimeTraceWriter = open("live_ga_runtime_trace.csv");
        this.strategyTraceWriter = open("live_strategy_application_trace.csv");
        this.bridgeSnapshotTraceWriter = open("live_bridge_snapshot_trace.csv");
        this.overrunTraceWriter = open("live_overrun_trace.csv");
        this.publishedSnapshotManifestWriter = open("live_published_snapshot_manifest.csv");
        runtimeTraceWriter.write("profile,simulationTimeNs,windowIndex,runtimeState,triggerType,snapshotId,snapshotTimeSeconds,taskCount,candidateCount,gaSubmitted,gaCompleted,gaRuntimeWallClockSeconds,deltaTMaxSeconds,resultApplied,resultDiscardedAsStale,lastAppliedStrategySnapshotId,error,deltaTMaxAtSubmissionSeconds,deltaTMaxFromCompletedStepSeconds,deltaTMaxMismatchSeconds,wallClockDeadlineNs,timeoutDetectedBeforeCompletion,waitCapDetectedWallClockNs,waitCapDetectedSimulationTimeNs,invalidPoolBandwidthViolations,futurePoolViolations\n");
        strategyTraceWriter.write("profile,simulationTimeNs,windowIndex,snapshotId,fitness,localAssignments,vehicleAssignments,edgeAssignments,cloudAssignments\n");
        bridgeSnapshotTraceWriter.write("profile,simulationTimeNs,snapshotResolved,snapshotId,snapshotTimeSeconds,vehicles,tasks,candidates,accessGateways,accessLinks,bandwidthPools,safeCellBuckets,invalidPoolBandwidthViolations,futurePoolViolations\n");
        overrunTraceWriter.write("profile,simulationTimeNs,windowIndex,runtimeState,snapshotId,gaRuntimeWallClockSeconds,deltaTMaxSeconds,resultDiscardedAsStale,deltaTMaxAtSubmissionSeconds,deltaTMaxFromCompletedStepSeconds,deltaTMaxMismatchSeconds,wallClockDeadlineNs,timeoutDetectedBeforeCompletion,waitCapDetectedWallClockNs,waitCapDetectedSimulationTimeNs\n");
        publishedSnapshotManifestWriter.write("profile,snapshotId,snapshotTimeSeconds,relativePath,vehicles,tasks,candidates,localCandidates,vehicleCandidates,edgeCandidates,cloudCandidates,accessLinks,bandwidthPools,candidateIds,poolIds,accessLinkIds,taskIds\n");
    }

    Path getOutputDir() {
        return outputDir;
    }

    void writeBridgeSnapshot(
            long simulationTimeNs,
            LiveStateLayerRuntimeFacade.RuntimeSnapshot runtimeSnapshot
    ) throws IOException {
        String snapshotId = "";
        String snapshotTime = "";
        boolean resolved = runtimeSnapshot.getSnapshot().isPresent();
        if (resolved) {
            SystemSnapshot snapshot = runtimeSnapshot.getSnapshot().get();
            snapshotId = snapshot.getSnapshotId();
            snapshotTime = format(snapshot.getTimeSeconds());
        }
        bridgeSnapshotTraceWriter.write(profile
                + "," + simulationTimeNs
                + "," + resolved
                + "," + snapshotId
                + "," + snapshotTime
                + "," + runtimeSnapshot.getVehicles()
                + "," + runtimeSnapshot.getTasks()
                + "," + runtimeSnapshot.getCandidates()
                + "," + runtimeSnapshot.getAccessGateways()
                + "," + runtimeSnapshot.getAccessLinks()
                + "," + runtimeSnapshot.getBandwidthPools()
                + "," + runtimeSnapshot.getSafeCellBuckets()
                + "," + runtimeSnapshot.getAudit().getInvalidPoolBandwidthViolations()
                + "," + runtimeSnapshot.getAudit().getFuturePoolViolations()
                + "\n");
        bridgeSnapshotTraceWriter.flush();
        if (resolved) {
            writePublishedSnapshot(runtimeSnapshot.getSnapshot().get());
        }
    }

    void writeRuntime(
            long simulationTimeNs,
            int windowIndex,
            LiveGaExecutionState state,
            String triggerType,
            String snapshotId,
            double snapshotTimeSeconds,
            int taskCount,
            int candidateCount,
            boolean gaSubmitted,
            boolean gaCompleted,
            double runtimeSeconds,
            double deltaTMaxSeconds,
            boolean resultApplied,
            boolean stale,
            String lastAppliedSnapshotId,
            String error
    ) throws IOException {
        writeRuntime(
                simulationTimeNs,
                windowIndex,
                state,
                triggerType,
                snapshotId,
                snapshotTimeSeconds,
                taskCount,
                candidateCount,
                gaSubmitted,
                gaCompleted,
                runtimeSeconds,
                deltaTMaxSeconds,
                resultApplied,
                stale,
                lastAppliedSnapshotId,
                error,
                LiveRuntimeTraceDetails.EMPTY
        );
    }

    void writeRuntime(
            long simulationTimeNs,
            int windowIndex,
            LiveGaExecutionState state,
            String triggerType,
            String snapshotId,
            double snapshotTimeSeconds,
            int taskCount,
            int candidateCount,
            boolean gaSubmitted,
            boolean gaCompleted,
            double runtimeSeconds,
            double deltaTMaxSeconds,
            boolean resultApplied,
            boolean stale,
            String lastAppliedSnapshotId,
            String error,
            LiveRuntimeTraceDetails details
    ) throws IOException {
        runtimeTraceWriter.write(profile
                + "," + simulationTimeNs
                + "," + windowIndex
                + "," + state
                + "," + safe(triggerType)
                + "," + safe(snapshotId)
                + "," + format(snapshotTimeSeconds)
                + "," + taskCount
                + "," + candidateCount
                + "," + gaSubmitted
                + "," + gaCompleted
                + "," + format(runtimeSeconds)
                + "," + format(deltaTMaxSeconds)
                + "," + resultApplied
                + "," + stale
                + "," + safe(lastAppliedSnapshotId)
                + "," + safe(error)
                + "," + format(details.deltaTMaxAtSubmissionSeconds)
                + "," + format(details.deltaTMaxFromCompletedStepSeconds)
                + "," + format(details.deltaTMaxMismatchSeconds)
                + "," + details.wallClockDeadlineNs
                + "," + details.timeoutDetectedBeforeCompletion
                + "," + details.waitCapDetectedWallClockNs
                + "," + details.waitCapDetectedSimulationTimeNs
                + "," + details.invalidPoolBandwidthViolations
                + "," + details.futurePoolViolations
                + "\n");
        runtimeTraceWriter.flush();
    }

    void writeStrategy(long simulationTimeNs, int windowIndex, LiveAppliedStrategy strategy) throws IOException {
        strategyTraceWriter.write(profile
                + "," + simulationTimeNs
                + "," + windowIndex
                + "," + strategy.getSnapshotId()
                + "," + format(strategy.getFitness())
                + "," + strategy.getLocalAssignments()
                + "," + strategy.getVehicleAssignments()
                + "," + strategy.getEdgeAssignments()
                + "," + strategy.getCloudAssignments()
                + "\n");
        strategyTraceWriter.flush();
    }

    void writeOverrun(
            long simulationTimeNs,
            int windowIndex,
            LiveGaExecutionState state,
            String snapshotId,
            double runtimeSeconds,
            double deltaTMaxSeconds,
            boolean stale
    ) throws IOException {
        writeOverrun(
                simulationTimeNs,
                windowIndex,
                state,
                snapshotId,
                runtimeSeconds,
                deltaTMaxSeconds,
                stale,
                LiveRuntimeTraceDetails.EMPTY
        );
    }

    void writeOverrun(
            long simulationTimeNs,
            int windowIndex,
            LiveGaExecutionState state,
            String snapshotId,
            double runtimeSeconds,
            double deltaTMaxSeconds,
            boolean stale,
            LiveRuntimeTraceDetails details
    ) throws IOException {
        overrunTraceWriter.write(profile
                + "," + simulationTimeNs
                + "," + windowIndex
                + "," + state
                + "," + safe(snapshotId)
                + "," + format(runtimeSeconds)
                + "," + format(deltaTMaxSeconds)
                + "," + stale
                + "," + format(details.deltaTMaxAtSubmissionSeconds)
                + "," + format(details.deltaTMaxFromCompletedStepSeconds)
                + "," + format(details.deltaTMaxMismatchSeconds)
                + "," + details.wallClockDeadlineNs
                + "," + details.timeoutDetectedBeforeCompletion
                + "," + details.waitCapDetectedWallClockNs
                + "," + details.waitCapDetectedSimulationTimeNs
                + "\n");
        overrunTraceWriter.flush();
    }

    @Override
    public void close() throws IOException {
        runtimeTraceWriter.close();
        strategyTraceWriter.close();
        bridgeSnapshotTraceWriter.close();
        overrunTraceWriter.close();
        publishedSnapshotManifestWriter.close();
    }

    private BufferedWriter open(String fileName) throws IOException {
        return Files.newBufferedWriter(outputDir.resolve(fileName), StandardCharsets.UTF_8);
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace(',', ';');
    }

    private void writePublishedSnapshot(SystemSnapshot snapshot) throws IOException {
        if (publishedSnapshotIds.size() >= publishedSnapshotCopyLimit
                || !publishedSnapshotIds.add(snapshot.getSnapshotId())) {
            return;
        }
        String fileName = "published_" + snapshot.getSnapshotId() + ".json";
        Files.write(
                publishedSnapshotsDir.resolve(fileName),
                gson.toJson(toSnapshotJson(snapshot)).getBytes(StandardCharsets.UTF_8)
        );

        List<NodeCandidate> candidates = new ArrayList<>(snapshot.getCandidateNodes());
        List<String> candidateIds = candidates.stream()
                .map(NodeCandidate::getCandidateId)
                .sorted()
                .collect(Collectors.toList());
        List<String> poolIds = snapshot.getBandwidthPools().stream()
                .map(BandwidthPoolSnapshot::getPoolId)
                .sorted()
                .collect(Collectors.toList());
        List<String> accessLinkIds = snapshot.getAccessLinks().stream()
                .map(AccessLinkSnapshot::getAccessLinkId)
                .sorted()
                .collect(Collectors.toList());
        List<String> taskIds = snapshot.getTasks().stream()
                .map(TaskInstance::getTaskId)
                .sorted()
                .collect(Collectors.toList());

        publishedSnapshotManifestWriter.write(profile
                + "," + snapshot.getSnapshotId()
                + "," + format(snapshot.getTimeSeconds())
                + "," + "published-snapshots/" + fileName
                + "," + snapshot.getVehicles().size()
                + "," + snapshot.getTasks().size()
                + "," + snapshot.getCandidateNodes().size()
                + "," + countType(candidates, NodeType.LOCAL)
                + "," + countType(candidates, NodeType.VEHICLE)
                + "," + countType(candidates, NodeType.EDGE)
                + "," + countType(candidates, NodeType.CLOUD)
                + "," + snapshot.getAccessLinks().size()
                + "," + snapshot.getBandwidthPools().size()
                + "," + joinIds(candidateIds)
                + "," + joinIds(poolIds)
                + "," + joinIds(accessLinkIds)
                + "," + joinIds(taskIds)
                + "\n");
        publishedSnapshotManifestWriter.flush();
    }

    private static long countType(List<NodeCandidate> candidates, NodeType type) {
        return candidates.stream().filter(candidate -> candidate.getType() == type).count();
    }

    private static String joinIds(List<String> values) {
        values.sort(Comparator.naturalOrder());
        return String.join("|", values);
    }

    private static JsonObject toSnapshotJson(SystemSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("snapshotId", snapshot.getSnapshotId());
        root.addProperty("timeSeconds", snapshot.getTimeSeconds());

        JsonArray vehicles = new JsonArray();
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            JsonObject item = new JsonObject();
            item.addProperty("vehicleId", vehicle.getVehicleId());
            item.addProperty("x", vehicle.getX());
            item.addProperty("y", vehicle.getY());
            item.addProperty("speed", vehicle.getSpeed());
            item.addProperty("localCpu", vehicle.getLocalCpu());
            vehicles.add(item);
        }
        root.add("vehicles", vehicles);

        JsonArray tasks = new JsonArray();
        for (TaskInstance task : snapshot.getTasks()) {
            JsonObject item = new JsonObject();
            item.addProperty("taskId", task.getTaskId());
            item.addProperty("sourceVehicleId", task.getSourceVehicleId());
            item.addProperty("inputSizeBits", task.getInputSizeBits());
            item.addProperty("outputSizeBits", task.getOutputSizeBits());
            item.addProperty("cpuCycles", task.getCpuCycles());
            item.addProperty("deadlineSeconds", task.getDeadlineSeconds());
            tasks.add(item);
        }
        root.add("tasks", tasks);

        JsonArray candidates = new JsonArray();
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            JsonObject item = new JsonObject();
            item.addProperty("candidateId", candidate.getCandidateId());
            item.addProperty("sourceVehicleId", candidate.getSourceVehicleId());
            item.addProperty("executionNodeId", candidate.getExecutionNodeId());
            item.addProperty("type", candidate.getType().name());
            item.addProperty("availableCpu", candidate.getAvailableCpu());
            item.addProperty("availableBandwidth", candidate.getAvailableBandwidth());
            item.addProperty("baseLatencySeconds", candidate.getPropagationDelaySeconds());
            if (candidate.getNodeX() != null) {
                item.addProperty("nodeX", candidate.getNodeX());
            }
            if (candidate.getNodeY() != null) {
                item.addProperty("nodeY", candidate.getNodeY());
            }
            if (candidate.getCoverageRadiusMeters() != null) {
                item.addProperty("coverageRadiusMeters", candidate.getCoverageRadiusMeters());
            }
            if (candidate.getBandwidthPoolId() != null) {
                item.addProperty("bandwidthPoolId", candidate.getBandwidthPoolId());
            }
            candidates.add(item);
        }
        root.add("candidateNodes", candidates);

        JsonArray gateways = new JsonArray();
        for (AccessGatewaySnapshot gateway : snapshot.getAccessGateways()) {
            JsonObject item = new JsonObject();
            item.addProperty("gatewayId", gateway.getGatewayId());
            item.addProperty("gatewayType", gateway.getGatewayType());
            item.addProperty("x", gateway.getX());
            item.addProperty("y", gateway.getY());
            item.addProperty("coverageRadiusMeters", gateway.getCoverageRadiusMeters());
            if (gateway.getBandwidthPoolId() != null) {
                item.addProperty("bandwidthPoolId", gateway.getBandwidthPoolId());
            }
            gateways.add(item);
        }
        root.add("accessGateways", gateways);

        JsonArray accessLinks = new JsonArray();
        for (AccessLinkSnapshot accessLink : snapshot.getAccessLinks()) {
            JsonObject item = new JsonObject();
            item.addProperty("accessLinkId", accessLink.getAccessLinkId());
            item.addProperty("vehicleId", accessLink.getVehicleId());
            item.addProperty("gatewayId", accessLink.getGatewayId());
            item.addProperty("active", accessLink.isActive());
            item.addProperty("available", accessLink.isAvailable());
            accessLinks.add(item);
        }
        root.add("accessLinks", accessLinks);

        JsonArray bandwidthPools = new JsonArray();
        for (BandwidthPoolSnapshot pool : snapshot.getBandwidthPools()) {
            JsonObject item = new JsonObject();
            item.addProperty("poolId", pool.getPoolId());
            item.addProperty("poolType", pool.getPoolType().name());
            item.addProperty("availableBandwidth", pool.getAvailableBandwidth());
            bandwidthPools.add(item);
        }
        root.add("bandwidthPools", bandwidthPools);

        return root;
    }
}
