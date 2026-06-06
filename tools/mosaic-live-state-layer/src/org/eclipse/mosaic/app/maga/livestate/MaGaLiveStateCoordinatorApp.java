package org.eclipse.mosaic.app.maga.livestate;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class MaGaLiveStateCoordinatorApp extends AbstractApplication<ServerOperatingSystem> {

    private final LiveStateCache cache = LiveStateCache.getInstance();
    private final LiveLocalAndV2vCandidatePreviewBuilder previewBuilder =
            new LiveLocalAndV2vCandidatePreviewBuilder();
    private final LiveCellTrafficAccountingCache cellAccounting =
            LiveCellTrafficAccountingCache.getInstance();
    private final LiveInfrastructurePreviewBuilder infrastructurePreviewBuilder =
            new LiveInfrastructurePreviewBuilder();
    private final LiveSystemSnapshotAssembler snapshotAssembler =
            new LiveSystemSnapshotAssembler();

    private MaGaLiveStateConfig config;
    private LiveSeededPoissonWorkloadGenerator workloadGenerator;
    private LiveStaticInfrastructureCatalog staticInfrastructureCatalog;
    private Path runDirectory;
    private Path outputDir;
    private Path infrastructureOutputDir;
    private Path snapshotOutputDir;
    private BufferedWriter vehicleWriter;
    private BufferedWriter taskWriter;
    private BufferedWriter localCandidateWriter;
    private BufferedWriter v2vCandidateWriter;
    private BufferedWriter v2vPoolWriter;
    private BufferedWriter cellTrafficEventWriter;
    private BufferedWriter cellBandwidthBucketWriter;
    private BufferedWriter accessLinkWriter;
    private BufferedWriter gatewayPoolWriter;
    private BufferedWriter remoteCandidateWriter;
    private BufferedWriter snapshotManifestWriter;
    private final Set<String> writtenCellEventIds = new HashSet<>();
    private long tickCount;
    private long tasksGenerated;
    private long tasksActivated;
    private boolean infrastructureSnapshotEnabled;

    @Override
    public void onStartup() {
        config = MaGaLiveStateConfig.load(getOs().getConfigurationPath());
        staticInfrastructureCatalog = LiveStaticInfrastructureCatalog.fromConfig(config);
        infrastructureSnapshotEnabled = config.hasCellDiagnosticAccounting();
        workloadGenerator = config.hasWorkloadGenerationEnabled()
                ? new LiveSeededPoissonWorkloadGenerator(config.getWorkloadGeneration(), config.getTickIntervalNs())
                : null;
        cache.reset();
        if (infrastructureSnapshotEnabled) {
            cellAccounting.reset();
        }
        cache.installTaskDefinitions(toTaskDefinitions(config));
        tickCount = 0L;
        tasksGenerated = 0L;
        tasksActivated = 0L;
        writtenCellEventIds.clear();
        boolean immutableViewSelfTest = LiveStateCache.runImmutableSnapshotViewSelfTest();

        try {
            runDirectory = resolveRunDirectory();
            outputDir = runDirectory.resolve("live-state-layer");
            Files.createDirectories(outputDir);
            openWriters();
            if (infrastructureSnapshotEnabled) {
                infrastructureOutputDir = runDirectory.resolve("live-infrastructure-snapshot");
                snapshotOutputDir = infrastructureOutputDir.resolve("snapshots");
                Files.createDirectories(snapshotOutputDir);
                openInfrastructureWriters();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initialize live state layer output", e);
        }

        getLog().infoSimTime(
                this,
                "LIVE_STATE_COORDINATOR_START"
                        + "|simulationTime=" + getOs().getSimulationTime()
                        + "|serverId=" + getOs().getId()
                        + "|tickIntervalMs=" + config.getTickIntervalMs()
                        + "|outputDir=" + outputDir.getFileName()
                        + "|infrastructureSnapshotEnabled=" + infrastructureSnapshotEnabled
        );
        getLog().infoSimTime(
                this,
                "LIVE_STATE_STATIC_INFRASTRUCTURE_LOADED"
                        + "|gateways=" + staticInfrastructureCatalog.getGatewayCount()
                        + "|edgeNodes=" + staticInfrastructureCatalog.getEdgeNodeCount()
                        + "|cloudNodes=" + staticInfrastructureCatalog.getCloudNodeCount()
        );
        if (config.hasConfiguredCellProfile()) {
            MaGaLiveStateConfig.ConfiguredCellProfile profile = config.getConfiguredCellProfile();
            String runtimeAccountingSource = infrastructureSnapshotEnabled
                    ? config.getCellDiagnosticAccounting().bandwidthSource
                    : "NOT_CONFIGURED";
            getLog().infoSimTime(
                    this,
                    "LIVE_STATE_CONFIGURED_CELL_PROFILE_LOADED"
                            + "|profileId=" + profile.profileId
                            + "|technology=" + profile.technology
                            + "|source=" + profile.source
                            + "|classification=" + profile.classification
                            + "|capacityBitsPerSecond=" + profile.capacityBitsPerSecond
                            + "|measuredRttSeconds=" + profile.measuredRttSeconds
                            + "|symmetricOneWayDelaySeconds=" + profile.symmetricOneWayDelaySeconds
                            + "|runtimeAccountingSource=" + runtimeAccountingSource
            );
        }
        getLog().infoSimTime(
                this,
                "LIVE_STATE_IMMUTABLE_VIEW_TEST"
                        + "|passed=" + immutableViewSelfTest
        );
        scheduleNextTick();
    }

    @Override
    public void processEvent(Event event) {
        long tickTimeNs = getOs().getSimulationTime();
        tickCount++;
        LiveStateSnapshotView vehicleObservationView = cache.snapshotAtOrBefore(tickTimeNs);
        int newlyGeneratedTasks = 0;
        if (workloadGenerator != null) {
            newlyGeneratedTasks = cache.installGeneratedTaskDefinitions(
                    workloadGenerator.generate(tickTimeNs, vehicleObservationView.getActiveVehicles())
            );
            tasksGenerated += newlyGeneratedTasks;
        }
        int newlyActivatedTasks = cache.activateDueTasks(tickTimeNs);
        tasksActivated += newlyActivatedTasks;
        LiveStateSnapshotView view = cache.snapshotAtOrBefore(tickTimeNs);
        LiveLocalAndV2vCandidatePreviewBuilder.PreviewResult preview = previewBuilder.build(view, config);
        List<LiveCellBandwidthBucket> safeCellBuckets = infrastructureSnapshotEnabled
                ? cellAccounting.latestSafeBuckets(tickTimeNs, config.getCellDiagnosticAccounting())
                : new ArrayList<LiveCellBandwidthBucket>();
        LiveInfrastructurePreviewBuilder.PreviewResult infrastructurePreview = infrastructureSnapshotEnabled
                ? infrastructurePreviewBuilder.build(view, config, safeCellBuckets)
                : null;
        LiveSnapshotManifestEntry manifestEntry = null;

        try {
            writeVehicleRows(view);
            writeTaskRows(view);
            writeLocalCandidateRows(preview.getLocalCandidates());
            writeV2vCandidateRows(preview.getV2vCandidates());
            writeV2vPoolRows(preview.getV2vPools());
            if (infrastructureSnapshotEnabled) {
                writeCellTrafficEventRows(tickTimeNs);
                writeCellBandwidthBucketRows(tickTimeNs, safeCellBuckets);
                writeAccessLinkRows(infrastructurePreview.getAccessLinks());
                writeGatewayPoolRows(infrastructurePreview.getGatewayPools());
                writeRemoteCandidateRows(infrastructurePreview.getRemoteCandidates());
                manifestEntry = snapshotAssembler.writeSnapshot(
                        snapshotOutputDir,
                        view,
                        preview,
                        infrastructurePreview,
                        config
                );
                if (manifestEntry != null) {
                    writeSnapshotManifestRow(manifestEntry);
                }
            }
            flushWriters();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write live state preview rows", e);
        }
        cache.markTasksExported(view.getPendingTasks());

        getLog().infoSimTime(
                this,
                "LIVE_STATE_COORDINATOR_TICK"
                        + "|simulationTime=" + tickTimeNs
                        + "|tickCount=" + tickCount
                        + "|activeVehicles=" + view.getActiveVehicles().size()
                        + "|pendingTasks=" + view.getPendingTasks().size()
                        + "|newlyGeneratedTasks=" + newlyGeneratedTasks
                        + "|newlyActivatedTasks=" + newlyActivatedTasks
                        + "|totalGeneratedTasks=" + tasksGenerated
                        + "|localCandidates=" + preview.getLocalCandidates().size()
                        + "|v2vCandidates=" + preview.getV2vCandidates().size()
                        + "|v2vPools=" + preview.getV2vPools().size()
                        + "|safeCellBuckets=" + safeCellBuckets.size()
                        + "|activeAccessLinks=" + activeAccessLinkCount(infrastructurePreview)
                        + "|edgeCloudCandidates=" + remoteCandidateCount(infrastructurePreview)
                        + "|snapshotWritten=" + (manifestEntry != null)
        );
        scheduleNextTick();
    }

    @Override
    public void onShutdown() {
        closeWriters();
        getLog().infoSimTime(
                this,
                "LIVE_STATE_COORDINATOR_STOP"
                        + "|simulationTime=" + getOs().getSimulationTime()
                        + "|serverId=" + getOs().getId()
                        + "|tickCount=" + tickCount
                        + "|tasksGenerated=" + tasksGenerated
                        + "|tasksActivated=" + tasksActivated
        );
    }

    private List<LiveTaskState> toTaskDefinitions(MaGaLiveStateConfig config) {
        List<LiveTaskState> tasks = new ArrayList<>();
        List<MaGaLiveStateConfig.TaskProfile> profiles = config.getTaskProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            tasks.add(profiles.get(i).toTaskState(i));
        }
        return tasks;
    }

    private Path resolveRunDirectory() {
        Path unitLogDirectory = getLog().getUnitLogDirectory();
        if (unitLogDirectory == null) {
            return resolveLatestRunDirectoryFromWorkingDirectory();
        }
        Path parent = unitLogDirectory.getParent();
        if (parent != null && "apps".equals(parent.getFileName().toString())) {
            return parent.getParent();
        }
        if (parent != null) {
            return parent;
        }
        return unitLogDirectory;
    }

    private Path resolveLatestRunDirectoryFromWorkingDirectory() {
        Path logsRoot = Paths.get(System.getProperty("user.dir")).resolve("logs");
        try (Stream<Path> paths = Files.list(logsRoot)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().contains("-MaGaLive"))
                    .max(Comparator.comparing(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new IllegalStateException(
                            "No MaGaLiveStateLayerStudy run directory found under " + logsRoot
                    ));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to resolve MOSAIC run directory under " + logsRoot, e);
        }
    }

    private void openWriters() throws IOException {
        vehicleWriter = openWriter("live_vehicle_state_preview.csv");
        taskWriter = openWriter("live_task_preview.csv");
        localCandidateWriter = openWriter("live_local_candidate_preview.csv");
        v2vCandidateWriter = openWriter("live_v2v_candidate_preview.csv");
        v2vPoolWriter = openWriter("live_v2v_bandwidth_pool_preview.csv");

        vehicleWriter.write("timeNs,vehicleId,lastUpdateTimeNs,projectedX,projectedY,speedMetersPerSecond,adHocEnabled,active\n");
        taskWriter.write("timeNs,taskId,profileId,sourceVehicleId,activationTimeNs,inputSizeBits,outputSizeBits,cpuCycles,deadlineSeconds,status\n");
        localCandidateWriter.write("timeNs,candidateId,sourceVehicleId,executionNodeId,type,availableCpu,cpuSource,propagationDelaySeconds\n");
        v2vCandidateWriter.write("timeNs,candidateId,sourceVehicleId,targetVehicleId,executionNodeId,type,distanceMeters,availableCpu,bandwidthPoolId,availableBandwidthBitsPerSecond,bandwidthSource,propagationDelaySeconds\n");
        v2vPoolWriter.write("timeNs,poolId,poolType,memberVehicleA,memberVehicleB,availableBandwidthBitsPerSecond,bandwidthSource\n");
    }

    private void openInfrastructureWriters() throws IOException {
        cellTrafficEventWriter = openInfrastructureWriter("live_cell_traffic_event_preview.csv");
        cellBandwidthBucketWriter = openInfrastructureWriter("live_cell_bandwidth_bucket_preview.csv");
        accessLinkWriter = openInfrastructureWriter("live_access_link_preview.csv");
        gatewayPoolWriter = openInfrastructureWriter("live_gateway_bandwidth_pool_preview.csv");
        remoteCandidateWriter = openInfrastructureWriter("live_remote_candidate_preview.csv");
        snapshotManifestWriter = openInfrastructureWriter("live_snapshot_manifest.csv");

        cellTrafficEventWriter.write("timeNs,eventId,messageId,direction,sourceId,destinationId,payloadBytes,payloadBits,bucketStartNs\n");
        cellBandwidthBucketWriter.write("timeNs,poolId,direction,bucketStartNs,bucketEndNs,availableFromNs,nominalCapacityBitsPerSecond,trafficObservedBitsPerSecond,residualCapacityBitsPerSecond,bandwidthSource\n");
        accessLinkWriter.write("timeNs,accessLinkId,vehicleId,gatewayId,runtimeGatewayId,distanceMeters,coverageRadiusMeters,active,available,cellRegionId,bandwidthPoolId\n");
        gatewayPoolWriter.write("timeNs,poolId,poolType,availableBandwidthBitsPerSecond,uplinkResidualBitsPerSecond,downlinkResidualBitsPerSecond,uplinkBucketStartNs,downlinkBucketStartNs,availableFromNs,bandwidthSource\n");
        remoteCandidateWriter.write("timeNs,candidateId,sourceVehicleId,executionNodeId,type,gatewayId,bandwidthPoolId,availableCpu,availableBandwidthBitsPerSecond,propagationDelaySeconds,nodeX,nodeY,coverageRadiusMeters\n");
        snapshotManifestWriter.write("timeNs,snapshotId,relativePath,vehicles,tasks,candidates,accessGateways,accessLinks,bandwidthPools\n");
    }

    private BufferedWriter openWriter(String fileName) throws IOException {
        return Files.newBufferedWriter(outputDir.resolve(fileName), StandardCharsets.UTF_8);
    }

    private BufferedWriter openInfrastructureWriter(String fileName) throws IOException {
        return Files.newBufferedWriter(infrastructureOutputDir.resolve(fileName), StandardCharsets.UTF_8);
    }

    private void writeVehicleRows(LiveStateSnapshotView view) throws IOException {
        for (LiveVehicleState vehicle : view.getActiveVehicles()) {
            vehicleWriter.write(
                    view.getTickTimeNs()
                            + "," + vehicle.getVehicleId()
                            + "," + vehicle.getLastUpdateTimeNs()
                            + "," + format(vehicle.getProjectedX())
                            + "," + format(vehicle.getProjectedY())
                            + "," + format(vehicle.getSpeedMetersPerSecond())
                            + "," + vehicle.isAdHocEnabled()
                            + "," + vehicle.isActive()
                            + "\n"
            );
        }
    }

    private void writeTaskRows(LiveStateSnapshotView view) throws IOException {
        for (LiveTaskState task : view.getPendingTasks()) {
            taskWriter.write(
                    view.getTickTimeNs()
                            + "," + task.getTaskId()
                            + "," + task.getProfileId()
                            + "," + task.getSourceVehicleId()
                            + "," + task.getActivationTimeNs()
                            + "," + task.getInputSizeBits()
                            + "," + task.getOutputSizeBits()
                            + "," + task.getCpuCycles()
                            + "," + format(task.getDeadlineSeconds())
                            + "," + task.getStatus()
                            + "\n"
            );
        }
    }

    private void writeLocalCandidateRows(List<LiveLocalCandidatePreview> candidates) throws IOException {
        for (LiveLocalCandidatePreview candidate : candidates) {
            localCandidateWriter.write(candidate.toCsvRow());
            localCandidateWriter.write("\n");
        }
    }

    private void writeV2vCandidateRows(List<LiveV2vCandidatePreview> candidates) throws IOException {
        for (LiveV2vCandidatePreview candidate : candidates) {
            v2vCandidateWriter.write(candidate.toCsvRow());
            v2vCandidateWriter.write("\n");
        }
    }

    private void writeV2vPoolRows(List<LiveV2vBandwidthPoolPreview> pools) throws IOException {
        for (LiveV2vBandwidthPoolPreview pool : pools) {
            v2vPoolWriter.write(pool.toCsvRow());
            v2vPoolWriter.write("\n");
        }
    }

    private void writeCellTrafficEventRows(long tickTimeNs) throws IOException {
        for (LiveCellTrafficEvent event : cellAccounting.eventsAtOrBefore(tickTimeNs)) {
            if (!writtenCellEventIds.add(event.getEventId())) {
                continue;
            }
            cellTrafficEventWriter.write(event.toCsvRow());
            cellTrafficEventWriter.write("\n");
        }
    }

    private void writeCellBandwidthBucketRows(
            long tickTimeNs,
            List<LiveCellBandwidthBucket> buckets
    ) throws IOException {
        for (LiveCellBandwidthBucket bucket : buckets) {
            cellBandwidthBucketWriter.write(bucket.toCsvRow(tickTimeNs));
            cellBandwidthBucketWriter.write("\n");
        }
    }

    private void writeAccessLinkRows(List<LiveAccessLinkPreview> accessLinks) throws IOException {
        for (LiveAccessLinkPreview link : accessLinks) {
            accessLinkWriter.write(link.toCsvRow());
            accessLinkWriter.write("\n");
        }
    }

    private void writeGatewayPoolRows(List<LiveGatewayBandwidthPoolPreview> pools) throws IOException {
        for (LiveGatewayBandwidthPoolPreview pool : pools) {
            gatewayPoolWriter.write(pool.toCsvRow());
            gatewayPoolWriter.write("\n");
        }
    }

    private void writeRemoteCandidateRows(List<LiveRemoteCandidatePreview> candidates) throws IOException {
        for (LiveRemoteCandidatePreview candidate : candidates) {
            remoteCandidateWriter.write(candidate.toCsvRow());
            remoteCandidateWriter.write("\n");
        }
    }

    private void writeSnapshotManifestRow(LiveSnapshotManifestEntry manifestEntry) throws IOException {
        snapshotManifestWriter.write(manifestEntry.toCsvRow());
        snapshotManifestWriter.write("\n");
    }

    private void flushWriters() throws IOException {
        vehicleWriter.flush();
        taskWriter.flush();
        localCandidateWriter.flush();
        v2vCandidateWriter.flush();
        v2vPoolWriter.flush();
        if (infrastructureSnapshotEnabled) {
            cellTrafficEventWriter.flush();
            cellBandwidthBucketWriter.flush();
            accessLinkWriter.flush();
            gatewayPoolWriter.flush();
            remoteCandidateWriter.flush();
            snapshotManifestWriter.flush();
        }
    }

    private void closeWriters() {
        closeWriter(vehicleWriter);
        closeWriter(taskWriter);
        closeWriter(localCandidateWriter);
        closeWriter(v2vCandidateWriter);
        closeWriter(v2vPoolWriter);
        closeWriter(cellTrafficEventWriter);
        closeWriter(cellBandwidthBucketWriter);
        closeWriter(accessLinkWriter);
        closeWriter(gatewayPoolWriter);
        closeWriter(remoteCandidateWriter);
        closeWriter(snapshotManifestWriter);
    }

    private void closeWriter(BufferedWriter writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            getLog().warnSimTime(this, "LIVE_STATE_OUTPUT_CLOSE_WARNING|message={}", e.getMessage());
        }
    }

    private void scheduleNextTick() {
        long nextTickTime = getOs().getSimulationTime() + config.getTickIntervalNs();
        getOs().getEventManager().addEvent(new Event(nextTickTime, this));
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static int activeAccessLinkCount(LiveInfrastructurePreviewBuilder.PreviewResult preview) {
        if (preview == null) {
            return 0;
        }
        int count = 0;
        for (LiveAccessLinkPreview link : preview.getAccessLinks()) {
            if (link.active) {
                count++;
            }
        }
        return count;
    }

    private static int remoteCandidateCount(LiveInfrastructurePreviewBuilder.PreviewResult preview) {
        return preview == null ? 0 : preview.getRemoteCandidates().size();
    }
}
