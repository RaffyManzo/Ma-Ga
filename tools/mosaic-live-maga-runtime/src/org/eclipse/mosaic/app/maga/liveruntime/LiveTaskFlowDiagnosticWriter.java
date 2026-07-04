package org.eclipse.mosaic.app.maga.liveruntime;

import ga.core.MaGaResult;
import model.genetic.Chromosome;
import org.eclipse.mosaic.app.maga.livestate.LiveStateLayerRuntimeFacade;
import window.state.TemporalStepResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LiveTaskFlowDiagnosticWriter implements AutoCloseable {

    private final BufferedWriter invocationWriter;
    private final BufferedWriter exclusionWriter;
    private final String runId;
    private final String configurationId;
    private final String seed;

    LiveTaskFlowDiagnosticWriter(
            Path outputDir,
            Path configurationPath,
            MaGaLiveRuntimeConfig runtimeConfig
    ) throws IOException {
        Files.createDirectories(outputDir);
        this.configurationId = deriveConfigurationId(configurationPath);
        this.seed = deriveSeed(configurationPath, configurationId);
        this.runId = deriveRunId(runtimeConfig.getScenarioName(), configurationId, seed);
        this.invocationWriter = Files.newBufferedWriter(
                outputDir.resolve("live_task_flow_ga_invocations.csv"),
                StandardCharsets.UTF_8
        );
        this.exclusionWriter = Files.newBufferedWriter(
                outputDir.resolve("live_task_flow_task_exclusions.csv"),
                StandardCharsets.UTF_8
        );
        invocationWriter.write("runId,configurationId,seed,jobId,windowIndex,triggerType,simulationTimestamp,completionSimulationTimestamp,snapshotTimestamp,vehiclesInCache,vehiclesInSnapshot,tasksInCache,pendingTasks,activeTasks,tasksAfterLifecycleFilter,tasksWithValidSourceVehicle,tasksInPreview,tasksInSystemSnapshot,tasksPassedToTemporalWindowManager,tasksPassedToMaGa,initialPopulationSize,bestChromosomeGeneCount,resultStatus,appliedOrStale,assignmentsLocal,assignmentsVehicle,assignmentsEdge,assignmentsCloud,gaRuntimeMillis,deltaTMax\n");
        exclusionWriter.write("runId,configurationId,seed,taskId,sourceVehicleId,activationTime,absoluteDeadline,currentSimulationTime,taskAge,remainingTimeToDeadline,currentState,exclusionStage,exclusionReason\n");
    }

    void writeInvocation(
            long completionSimulationTimeNs,
            LiveGaCompletion completion,
            String appliedOrStale,
            LiveStrategyApplier.AssignmentCounts assignments
    ) throws IOException {
        LiveGaJob job = completion.getJob();
        if (job == null) {
            return;
        }
        LiveStateLayerRuntimeFacade.TaskFlowDiagnostics diagnostics = job.getTaskFlowDiagnostics();
        TemporalStepResult stepResult = completion.getStepResult();
        invocationWriter.write(runId
                + "," + configurationId
                + "," + seed
                + "," + safe(job.getJobId())
                + "," + job.getWindowIndex()
                + "," + safe(job.getTriggerType())
                + "," + formatSeconds(job.getSubmissionSimulationTimeNs())
                + "," + formatSeconds(completionSimulationTimeNs)
                + "," + format(job.getSnapshot().getTimeSeconds())
                + "," + value(diagnostics == null ? 0 : diagnostics.getVehiclesInCache())
                + "," + value(diagnostics == null ? 0 : diagnostics.getVehiclesInSnapshot())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksInCache())
                + "," + value(diagnostics == null ? 0 : diagnostics.getPendingTasks())
                + "," + value(diagnostics == null ? 0 : diagnostics.getActiveTasks())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksAfterLifecycleFilter())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksWithValidSourceVehicle())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksInPreview())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksInSystemSnapshot())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksPassedToTemporalWindowManager())
                + "," + tasksPassedToMaGa(stepResult)
                + "," + initialPopulationSize(stepResult)
                + "," + bestChromosomeGeneCount(stepResult)
                + "," + safe(resultStatus(completion))
                + "," + safe(appliedOrStale)
                + "," + assignments.getLocalAssignments()
                + "," + assignments.getVehicleAssignments()
                + "," + assignments.getEdgeAssignments()
                + "," + assignments.getCloudAssignments()
                + "," + format(completion.getWallClockRuntimeSeconds() * 1000.0)
                + "," + format(deltaTMax(completion))
                + "\n");
        invocationWriter.flush();
    }

    void writeShutdownInFlight(long simulationTimeNs, LiveGaJob job) throws IOException {
        if (job == null) {
            return;
        }
        LiveStateLayerRuntimeFacade.TaskFlowDiagnostics diagnostics = job.getTaskFlowDiagnostics();
        invocationWriter.write(runId
                + "," + configurationId
                + "," + seed
                + "," + safe(job.getJobId())
                + "," + job.getWindowIndex()
                + "," + safe(job.getTriggerType())
                + "," + formatSeconds(job.getSubmissionSimulationTimeNs())
                + "," + formatSeconds(simulationTimeNs)
                + "," + format(job.getSnapshot().getTimeSeconds())
                + "," + value(diagnostics == null ? 0 : diagnostics.getVehiclesInCache())
                + "," + value(diagnostics == null ? 0 : diagnostics.getVehiclesInSnapshot())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksInCache())
                + "," + value(diagnostics == null ? 0 : diagnostics.getPendingTasks())
                + "," + value(diagnostics == null ? 0 : diagnostics.getActiveTasks())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksAfterLifecycleFilter())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksWithValidSourceVehicle())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksInPreview())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksInSystemSnapshot())
                + "," + value(diagnostics == null ? 0 : diagnostics.getTasksPassedToTemporalWindowManager())
                + ",0,0,0,SHUTDOWN_IN_FLIGHT,SHUTDOWN_IN_FLIGHT,0,0,0,0,,"
                + format(job.getDeltaTMaxAtSubmissionSeconds())
                + "\n");
        invocationWriter.flush();
    }

    void writeTaskExclusions(List<LiveStateLayerRuntimeFacade.TaskExclusionRecord> records) throws IOException {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (LiveStateLayerRuntimeFacade.TaskExclusionRecord record : records) {
            exclusionWriter.write(runId
                    + "," + configurationId
                    + "," + seed
                    + "," + safe(record.getTaskId())
                    + "," + safe(record.getSourceVehicleId())
                    + "," + format(record.getActivationTime())
                    + "," + format(record.getAbsoluteDeadline())
                    + "," + format(record.getCurrentSimulationTime())
                    + "," + format(record.getTaskAge())
                    + "," + format(record.getRemainingTimeToDeadline())
                    + "," + safe(record.getCurrentState())
                    + "," + safe(record.getExclusionStage())
                    + "," + safe(record.getExclusionReason())
                    + "\n");
        }
        exclusionWriter.flush();
    }

    @Override
    public void close() throws IOException {
        invocationWriter.close();
        exclusionWriter.close();
    }

    private static int tasksPassedToMaGa(TemporalStepResult stepResult) {
        return stepResult == null ? 0 : stepResult.getSnapshot().getTasks().size();
    }

    private static int initialPopulationSize(TemporalStepResult stepResult) {
        return stepResult == null ? 0 : stepResult.getInitialPopulationSize();
    }

    private static int bestChromosomeGeneCount(TemporalStepResult stepResult) {
        if (stepResult == null || stepResult.getMaGaResult() == null) {
            return 0;
        }
        Chromosome chromosome = stepResult.getMaGaResult().getBestChromosome();
        if (chromosome == null || chromosome.getGenes() == null) {
            return 0;
        }
        return chromosome.getGenes().size();
    }

    private static String resultStatus(LiveGaCompletion completion) {
        if (completion.hasError()) {
            Throwable error = completion.getError();
            return error == null ? "ERROR" : "ERROR_" + error.getClass().getSimpleName();
        }
        TemporalStepResult stepResult = completion.getStepResult();
        if (stepResult == null) {
            return "NULL_STEP_RESULT";
        }
        MaGaResult result = stepResult.getMaGaResult();
        return result == null || result.getStopReason() == null
                ? "UNKNOWN"
                : result.getStopReason().name();
    }

    private static double deltaTMax(LiveGaCompletion completion) {
        if (completion.getDeltaTMaxSeconds() > 0.0) {
            return completion.getDeltaTMaxSeconds();
        }
        return completion.getJob() == null ? Double.NaN : completion.getJob().getDeltaTMaxAtSubmissionSeconds();
    }

    private static String deriveRunId(String scenarioName, String configurationId, String seed) {
        if (!"UNKNOWN".equals(configurationId) || !"UNKNOWN".equals(seed)) {
            return configurationId + "_" + seed;
        }
        return scenarioName == null || scenarioName.isBlank() ? "UNKNOWN" : scenarioName;
    }

    private static String deriveConfigurationId(Path configurationPath) {
        for (String name : pathNames(configurationPath)) {
            if (name.startsWith("CFG-")) {
                return name;
            }
        }
        return "UNKNOWN";
    }

    private static String deriveSeed(Path configurationPath, String configurationId) {
        List<String> names = pathNames(configurationPath);
        for (int i = 0; i < names.size() - 1; i++) {
            if (names.get(i).equals(configurationId) && names.get(i + 1).matches("\\d+")) {
                return names.get(i + 1);
            }
        }
        for (String name : names) {
            if (name.matches("\\d{3,}")) {
                return name;
            }
        }
        return "UNKNOWN";
    }

    private static List<String> pathNames(Path path) {
        List<String> names = new ArrayList<>();
        if (path == null) {
            return names;
        }
        Path absolutePath = path.toAbsolutePath();
        for (Path part : absolutePath) {
            names.add(part.toString());
        }
        return names;
    }

    private static String formatSeconds(long timeNs) {
        return format(timeNs / 1_000_000_000.0);
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static String value(int value) {
        return Integer.toString(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }
}
