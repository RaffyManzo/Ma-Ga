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
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class MaGaLiveStateCoordinatorApp extends AbstractApplication<ServerOperatingSystem> {

    private final LiveStateCache cache = LiveStateCache.getInstance();
    private final LiveLocalAndV2vCandidatePreviewBuilder previewBuilder =
            new LiveLocalAndV2vCandidatePreviewBuilder();

    private MaGaLiveStateConfig config;
    private LiveStaticInfrastructureCatalog staticInfrastructureCatalog;
    private Path outputDir;
    private BufferedWriter vehicleWriter;
    private BufferedWriter taskWriter;
    private BufferedWriter localCandidateWriter;
    private BufferedWriter v2vCandidateWriter;
    private BufferedWriter v2vPoolWriter;
    private long tickCount;
    private long tasksActivated;

    @Override
    public void onStartup() {
        config = MaGaLiveStateConfig.load(getOs().getConfigurationPath());
        staticInfrastructureCatalog = LiveStaticInfrastructureCatalog.fromConfig(config);
        cache.reset();
        cache.installTaskDefinitions(toTaskDefinitions(config));
        tickCount = 0L;
        tasksActivated = 0L;
        boolean immutableViewSelfTest = LiveStateCache.runImmutableSnapshotViewSelfTest();

        try {
            outputDir = resolveRunDirectory().resolve("live-state-layer");
            Files.createDirectories(outputDir);
            openWriters();
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
        );
        getLog().infoSimTime(
                this,
                "LIVE_STATE_STATIC_INFRASTRUCTURE_LOADED"
                        + "|gateways=" + staticInfrastructureCatalog.getGatewayCount()
                        + "|edgeNodes=" + staticInfrastructureCatalog.getEdgeNodeCount()
                        + "|cloudNodes=" + staticInfrastructureCatalog.getCloudNodeCount()
        );
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
        int newlyActivatedTasks = cache.activateDueTasks(tickTimeNs);
        tasksActivated += newlyActivatedTasks;
        LiveStateSnapshotView view = cache.snapshotAtOrBefore(tickTimeNs);
        LiveLocalAndV2vCandidatePreviewBuilder.PreviewResult preview = previewBuilder.build(view, config);

        try {
            writeVehicleRows(view);
            writeTaskRows(view);
            writeLocalCandidateRows(preview.getLocalCandidates());
            writeV2vCandidateRows(preview.getV2vCandidates());
            writeV2vPoolRows(preview.getV2vPools());
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
                        + "|newlyActivatedTasks=" + newlyActivatedTasks
                        + "|localCandidates=" + preview.getLocalCandidates().size()
                        + "|v2vCandidates=" + preview.getV2vCandidates().size()
                        + "|v2vPools=" + preview.getV2vPools().size()
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
                    .filter(path -> path.getFileName().toString().endsWith("-MaGaLiveStateLayerStudy"))
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

    private BufferedWriter openWriter(String fileName) throws IOException {
        return Files.newBufferedWriter(outputDir.resolve(fileName), StandardCharsets.UTF_8);
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

    private void flushWriters() throws IOException {
        vehicleWriter.flush();
        taskWriter.flush();
        localCandidateWriter.flush();
        v2vCandidateWriter.flush();
        v2vPoolWriter.flush();
    }

    private void closeWriters() {
        closeWriter(vehicleWriter);
        closeWriter(taskWriter);
        closeWriter(localCandidateWriter);
        closeWriter(v2vCandidateWriter);
        closeWriter(v2vPoolWriter);
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
}
