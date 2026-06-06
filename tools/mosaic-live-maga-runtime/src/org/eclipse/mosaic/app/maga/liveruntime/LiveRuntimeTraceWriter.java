package org.eclipse.mosaic.app.maga.liveruntime;

import model.snapshot.SystemSnapshot;
import org.eclipse.mosaic.app.maga.livestate.LiveStateLayerRuntimeFacade;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class LiveRuntimeTraceWriter implements AutoCloseable {

    private final String profile;
    private final Path outputDir;
    private final BufferedWriter runtimeTraceWriter;
    private final BufferedWriter strategyTraceWriter;
    private final BufferedWriter bridgeSnapshotTraceWriter;
    private final BufferedWriter overrunTraceWriter;

    LiveRuntimeTraceWriter(Path runDirectory, String profile) throws IOException {
        this.profile = profile;
        this.outputDir = runDirectory.resolve("live-maga-runtime");
        Files.createDirectories(outputDir);
        this.runtimeTraceWriter = open("live_ga_runtime_trace.csv");
        this.strategyTraceWriter = open("live_strategy_application_trace.csv");
        this.bridgeSnapshotTraceWriter = open("live_bridge_snapshot_trace.csv");
        this.overrunTraceWriter = open("live_overrun_trace.csv");
        runtimeTraceWriter.write("profile,simulationTimeNs,windowIndex,runtimeState,triggerType,snapshotId,snapshotTimeSeconds,taskCount,candidateCount,gaSubmitted,gaCompleted,gaRuntimeWallClockSeconds,deltaTMaxSeconds,resultApplied,resultDiscardedAsStale,lastAppliedStrategySnapshotId,error\n");
        strategyTraceWriter.write("profile,simulationTimeNs,windowIndex,snapshotId,fitness,localAssignments,vehicleAssignments,edgeAssignments,cloudAssignments\n");
        bridgeSnapshotTraceWriter.write("profile,simulationTimeNs,snapshotResolved,snapshotId,snapshotTimeSeconds,vehicles,tasks,candidates,accessGateways,accessLinks,bandwidthPools,safeCellBuckets\n");
        overrunTraceWriter.write("profile,simulationTimeNs,windowIndex,runtimeState,snapshotId,gaRuntimeWallClockSeconds,deltaTMaxSeconds,resultDiscardedAsStale\n");
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
                + "\n");
        bridgeSnapshotTraceWriter.flush();
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
        overrunTraceWriter.write(profile
                + "," + simulationTimeNs
                + "," + windowIndex
                + "," + state
                + "," + safe(snapshotId)
                + "," + format(runtimeSeconds)
                + "," + format(deltaTMaxSeconds)
                + "," + stale
                + "\n");
        overrunTraceWriter.flush();
    }

    @Override
    public void close() throws IOException {
        runtimeTraceWriter.close();
        strategyTraceWriter.close();
        bridgeSnapshotTraceWriter.close();
        overrunTraceWriter.close();
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
}
