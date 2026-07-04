package org.eclipse.mosaic.app.maga.liveruntime;

import ga.core.StopReason;
import ga.diagnostics.GaRuntimeDiagnostics;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class LiveGaRuntimeHotspotDiagnosticWriter
        implements AutoCloseable, GaRuntimeDiagnostics.Sink {
    private static final double NS_PER_SECOND = 1_000_000_000.0;

    private final String runId;
    private final String configurationId;
    private final String seed;
    private final BufferedWriter jobWriter;
    private final BufferedWriter generationWriter;
    private final BufferedWriter stageWriter;
    private final BufferedWriter fitnessWriter;
    private final BufferedWriter repairWriter;
    private final BufferedWriter workerBlockingWriter;
    private final BufferedWriter heartbeatWriter;
    private final BufferedWriter jvmWriter;
    private final Map<String, JobRuntimeRecord> jobs = new LinkedHashMap<>();

    LiveGaRuntimeHotspotDiagnosticWriter(
            Path runtimeOutputDir,
            String runId,
            String configurationId,
            String seed
    ) throws IOException {
        this.runId = clean(runId);
        this.configurationId = clean(configurationId);
        this.seed = clean(seed);
        Path diagnosticsDir = runtimeOutputDir.resolve("g03-ga-runtime-hotspot");
        Files.createDirectories(diagnosticsDir);
        this.jobWriter = open(diagnosticsDir.resolve("ga_job_profile.csv"));
        this.generationWriter = open(diagnosticsDir.resolve("ga_generation_profile.csv"));
        this.stageWriter = open(diagnosticsDir.resolve("ga_stage_profile.csv"));
        this.fitnessWriter = open(diagnosticsDir.resolve("ga_fitness_profile.csv"));
        this.repairWriter = open(diagnosticsDir.resolve("ga_repair_profile.csv"));
        this.workerBlockingWriter = open(diagnosticsDir.resolve("ga_worker_blocking_timeline.csv"));
        this.heartbeatWriter = open(diagnosticsDir.resolve("ga_heartbeat.csv"));
        this.jvmWriter = open(diagnosticsDir.resolve("jvm_runtime_profile.csv"));
        writeHeaders();
        writeJvmProfile("RUN_START");
    }

    private static BufferedWriter open(Path path) throws IOException {
        return Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    GaRuntimeDiagnostics.JobDescriptor descriptor(String jobId) {
        return new GaRuntimeDiagnostics.JobDescriptor(
                runId,
                configurationId,
                seed,
                jobId
        );
    }

    synchronized void recordSubmitted(LiveGaJob job) {
        JobRuntimeRecord record = JobRuntimeRecord.from(job);
        jobs.put(job.getJobId(), record);
        writeWorkerBlocking(record, "SUBMITTED", job.getSubmissionSimulationTimeNs());
    }

    synchronized void recordWorkerBusyTick(
            long simulationTimeNs,
            LiveGaJob job,
            boolean snapshotProduced,
            int generatedTasks,
            int expiredTasks
    ) {
        if (job == null) {
            return;
        }
        JobRuntimeRecord record = jobs.get(job.getJobId());
        if (record == null) {
            return;
        }
        record.skippedSubmissionTicksWhileWorkerBusy++;
        if (snapshotProduced) {
            record.snapshotsProducedWhileWorkerBusy++;
        }
        record.tasksGeneratedWhileWorkerBusy += Math.max(0, generatedTasks);
        record.tasksExpiredWhileWorkerBusy += Math.max(0, expiredTasks);
        if (job.isTimeoutDetectedBeforeCompletion()) {
            record.workerBusyTicksAfterBudget++;
        }
    }

    synchronized void recordBudgetExceeded(LiveGaJob job, long simulationTimeNs) {
        if (job == null) {
            return;
        }
        JobRuntimeRecord record = jobs.get(job.getJobId());
        if (record == null || record.firstBudgetOverrunWallClockNs > 0L) {
            return;
        }
        record.firstBudgetOverrunWallClockNs = job.getWaitCapDetectedWallClockNs();
        record.firstBudgetOverrunSimulationTimeNs = simulationTimeNs;
        writeWorkerBlocking(record, "BUDGET_EXCEEDED", simulationTimeNs);
    }

    synchronized void recordCompletion(
            LiveGaCompletion completion,
            long simulationTimeNs,
            boolean applied,
            boolean stale,
            boolean failed,
            boolean shutdownInFlight
    ) {
        if (completion == null || completion.getJob() == null) {
            return;
        }
        LiveGaJob job = completion.getJob();
        JobRuntimeRecord record = jobs.computeIfAbsent(
                job.getJobId(),
                ignored -> JobRuntimeRecord.from(job)
        );
        record.wallClockEndEpochMillis = completion.getCompletionWallClockEpochMillis();
        record.gaWallClockMillis = completion.getWallClockRuntimeSeconds() * 1000.0;
        record.gaThreadCpuMillis = completion.getThreadCpuMillis();
        record.completionPollSimulationTimeNs = simulationTimeNs;
        record.deltaTMaxMillis = Math.max(
                0.0,
                completion.getDeltaTMaxSeconds() * 1000.0
        );
        if (record.deltaTMaxMillis <= 0.0) {
            record.deltaTMaxMillis = Math.max(
                    0.0,
                    job.getDeltaTMaxAtSubmissionSeconds() * 1000.0
            );
        }
        record.timeBeyondBudgetMillis = Math.max(
                0.0,
                record.gaWallClockMillis - record.deltaTMaxMillis
        );
        record.gcCollectionCountDelta = currentGcCollectionCount()
                - record.gcCollectionCountAtStart;
        record.gcCollectionTimeMillisDelta = currentGcCollectionTimeMillis()
                - record.gcCollectionTimeMillisAtStart;
        record.heapUsedAtEnd = usedHeapBytes();
        record.applied = applied;
        record.stale = stale;
        record.failed = failed;
        record.shutdownInFlight = shutdownInFlight;
        if (completion.getStepResult() != null
                && completion.getStepResult().getMaGaResult() != null) {
            StopReason stopReason = completion.getStepResult()
                    .getMaGaResult()
                    .getStopReason();
            record.stopReason = stopReason == null ? "UNKNOWN" : stopReason.name();
            record.emptyTaskSet = stopReason == StopReason.EMPTY_TASK_SET;
        }
        writeJob(record);
        writeWorkerBlocking(
                record,
                shutdownInFlight ? "SHUTDOWN_IN_FLIGHT"
                        : stale ? "STALE"
                        : failed ? "FAILED" : "COMPLETED",
                simulationTimeNs
        );
    }

    synchronized void recordShutdownInFlight(LiveGaJob job, long simulationTimeNs) {
        if (job == null) {
            return;
        }
        JobRuntimeRecord record = jobs.computeIfAbsent(
                job.getJobId(),
                ignored -> JobRuntimeRecord.from(job)
        );
        record.wallClockEndEpochMillis = System.currentTimeMillis();
        record.completionPollSimulationTimeNs = simulationTimeNs;
        record.shutdownInFlight = true;
        record.heapUsedAtEnd = usedHeapBytes();
        record.gcCollectionCountDelta = currentGcCollectionCount()
                - record.gcCollectionCountAtStart;
        record.gcCollectionTimeMillisDelta = currentGcCollectionTimeMillis()
                - record.gcCollectionTimeMillisAtStart;
        writeHeartbeat(new GaRuntimeDiagnostics.HeartbeatRecord(
                descriptor(job.getJobId()),
                record.lastKnownGeneration,
                record.lastKnownStage,
                -1,
                -1,
                elapsedMillis(job.getSubmissionWallClockNs(), System.nanoTime()),
                Double.NaN,
                0,
                0,
                0,
                record.lastHeartbeatWallClockMillis
        ));
        writeJob(record);
        writeWorkerBlocking(record, "SHUTDOWN_IN_FLIGHT", simulationTimeNs);
    }

    @Override
    public synchronized void onStage(GaRuntimeDiagnostics.StageRecord record) {
        try {
            writeCsv(
                    stageWriter,
                    record.job.runId,
                    record.job.configurationId,
                    record.job.seed,
                    record.job.jobId,
                    record.generationIndex,
                    record.chromosomeIndex,
                    record.stage,
                    f(record.wallClockMillis),
                    record.units
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void onGeneration(GaRuntimeDiagnostics.GenerationRecord record) {
        JobRuntimeRecord job = jobs.get(record.job.jobId);
        if (job != null) {
            job.lastKnownGeneration = record.generationIndex;
            job.lastKnownStage = "GENERATION_END";
        }
        try {
            writeCsv(
                    generationWriter,
                    record.job.runId,
                    record.job.configurationId,
                    record.job.seed,
                    record.job.jobId,
                    record.generationIndex,
                    f(record.generationWallClockMillis),
                    f(record.generationThreadCpuMillis),
                    f(record.selectionMillis),
                    f(record.crossoverMillis),
                    f(record.mutationMillis),
                    f(record.repairMillis),
                    f(record.fitnessMillis),
                    f(record.sortingAndElitismMillis),
                    f(record.terminationCheckMillis),
                    record.chromosomesCreated,
                    record.chromosomesReused,
                    record.chromosomesRepaired,
                    record.repairCalls,
                    record.fitnessEvaluations,
                    record.candidateScans,
                    record.taskGeneVisits,
                    record.feasibleChromosomes,
                    record.infeasibleChromosomes,
                    f(record.bestFitness),
                    f(record.previousBestFitness),
                    f(record.fitnessImprovement),
                    record.stallCounter,
                    record.heapUsedBeforeBytes,
                    record.heapUsedAfterBytes
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void onFitness(GaRuntimeDiagnostics.FitnessRecord record) {
        try {
            writeCsv(
                    fitnessWriter,
                    record.job.runId,
                    record.job.configurationId,
                    record.job.seed,
                    record.job.jobId,
                    record.generationIndex,
                    record.chromosomeIndex,
                    record.fitnessEvaluationCount,
                    f(record.totalFitnessMillis),
                    f(record.localExecutionEvaluationMillis),
                    f(record.remoteExecutionEvaluationMillis),
                    f(record.communicationLatencyMillis),
                    f(record.mobilityPenaltyMillis),
                    f(record.resourcePenaltyMillis),
                    f(record.deadlinePenaltyMillis),
                    f(record.localContentionMillis),
                    f(record.bandwidthPoolEvaluationMillis),
                    f(record.structuralConstraintMillis),
                    record.localGenesEvaluated,
                    record.vehicleGenesEvaluated,
                    record.edgeGenesEvaluated,
                    record.cloudGenesEvaluated,
                    record.taskGeneVisits
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void onRepair(GaRuntimeDiagnostics.RepairRecord record) {
        try {
            writeCsv(
                    repairWriter,
                    record.job.runId,
                    record.job.configurationId,
                    record.job.seed,
                    record.job.jobId,
                    record.generationIndex,
                    record.chromosomeIndex,
                    record.repairMode,
                    record.repairInvocationCount,
                    f(record.totalRepairMillis),
                    record.repairedChromosomeCount,
                    record.repairedGeneCount,
                    record.localOverflowRepairs,
                    record.bandwidthRepairs,
                    record.cpuCapacityRepairs,
                    record.structuralRepairs,
                    record.mobilityRepairs,
                    record.deadlineRepairs,
                    record.candidateSearches,
                    record.candidateComparisons,
                    record.fallbackAssignments,
                    record.unrepairedViolations,
                    record.repairPasses,
                    f(record.geneRepairMillis),
                    f(record.localContentionRepairMillis),
                    f(record.cpuAggregateRepairMillis),
                    f(record.bandwidthAggregateRepairMillis),
                    f(record.bandwidthPoolRepairMillis)
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void onHeartbeat(GaRuntimeDiagnostics.HeartbeatRecord record) {
        JobRuntimeRecord job = jobs.get(record.job.jobId);
        if (job != null) {
            job.lastKnownGeneration = record.currentGeneration;
            job.lastKnownStage = record.currentStage;
            job.lastHeartbeatWallClockMillis = record.elapsedWallClockMillis;
            job.lastProgressWallClockMillis = record.lastProgressWallClockMillis;
        }
        writeHeartbeat(record);
    }

    @Override
    public synchronized void onOptimizerSummary(
            GaRuntimeDiagnostics.OptimizerSummaryRecord record
    ) {
        JobRuntimeRecord job = jobs.get(record.job.jobId);
        if (job == null) {
            return;
        }
        job.populationSizeConfigured = record.populationSizeConfigured;
        job.populationSizeActual = record.populationSizeActual;
        job.maxGenerations = record.maxGenerations;
        job.stallGenerationLimit = record.stallGenerationLimit;
        job.improvementEpsilon = record.improvementEpsilon;
        job.crossoverRate = record.crossoverRate;
        job.mutationRate = record.mutationRate;
        job.eliteCount = record.eliteCount;
        job.scalingMode = record.scalingMode;
        job.warmStartMode = record.warmStartMode;
        job.reusedChromosomeCount = record.reusedChromosomeCount;
        job.inputValidationMillis = record.inputValidationMillis;
        job.taskCandidateMappingMillis = record.taskCandidateMappingMillis;
        job.populationAdaptationMillis = record.populationAdaptationMillis;
        job.populationInitializationMillis = record.populationInitializationMillis;
        job.initialChromosomeCreationMillis = record.initialChromosomeCreationMillis;
        job.initialRepairMillis = record.initialRepairMillis;
        job.initialFitnessMillis = record.initialFitnessMillis;
        job.initialSortingMillis = record.initialSortingMillis;
        job.finalRepairMillis = record.finalRepairMillis;
        job.finalFitnessMillis = record.finalFitnessMillis;
        job.finalSortingMillis = record.finalSortingMillis;
        job.resultConstructionMillis = record.resultConstructionMillis;
        job.serializationOrReportingMillis = record.serializationOrReportingMillis;
        job.totalOptimizerMillis = record.totalOptimizerMillis;
        job.generationsExecuted = record.generationsExecuted;
        job.stopReason = record.stopReason;
        job.emptyTaskSet = "EMPTY_TASK_SET".equals(record.stopReason);
    }

    synchronized void writeJvmProfile(String event) {
        try {
            MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memory.getHeapMemoryUsage();
            writeCsv(
                    jvmWriter,
                    runId,
                    configurationId,
                    seed,
                    event,
                    System.currentTimeMillis(),
                    System.getProperty("java.version", ""),
                    System.getProperty("java.vendor", ""),
                    System.getProperty("os.name", "") + " "
                            + System.getProperty("os.version", ""),
                    System.getProperty("os.arch", ""),
                    Runtime.getRuntime().availableProcessors(),
                    Runtime.getRuntime().maxMemory(),
                    heap.getInit(),
                    heap.getCommitted(),
                    heap.getUsed(),
                    currentGcCollectionCount(),
                    currentGcCollectionTimeMillis()
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        writeJvmProfile("RUN_END");
        jobWriter.close();
        generationWriter.close();
        stageWriter.close();
        fitnessWriter.close();
        repairWriter.close();
        workerBlockingWriter.close();
        heartbeatWriter.close();
        jvmWriter.close();
    }

    private void writeHeartbeat(GaRuntimeDiagnostics.HeartbeatRecord record) {
        try {
            writeCsv(
                    heartbeatWriter,
                    record.job.runId,
                    record.job.configurationId,
                    record.job.seed,
                    record.job.jobId,
                    record.currentGeneration,
                    record.currentStage,
                    record.currentChromosomeIndex,
                    record.currentTaskIndex,
                    f(record.elapsedWallClockMillis),
                    f(record.bestFitness),
                    record.stallCounter,
                    record.repairCalls,
                    record.fitnessEvaluations,
                    f(record.lastProgressWallClockMillis)
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeJob(JobRuntimeRecord record) {
        try {
            double completionPollSeconds = record.completionPollSimulationTimeNs / NS_PER_SECOND;
            double submitSeconds = record.submissionSimulationTimeNs / NS_PER_SECOND;
            writeCsv(
                    jobWriter,
                    runId,
                    configurationId,
                    seed,
                    record.jobId,
                    f(submitSeconds),
                    f(record.snapshotTimestampSeconds),
                    record.wallClockStartEpochMillis,
                    record.wallClockEndEpochMillis,
                    f(record.gaWallClockMillis),
                    f(record.gaThreadCpuMillis),
                    f(completionPollSeconds),
                    f(Math.max(0.0, completionPollSeconds - submitSeconds)),
                    f(Math.max(0.0, completionPollSeconds - record.snapshotTimestampSeconds)),
                    f(record.deltaTMaxMillis),
                    f(record.timeBeyondBudgetMillis),
                    record.taskCount,
                    record.candidateCountTotal,
                    record.candidateCountMinPerTask,
                    f(record.candidateCountMeanPerTask),
                    record.candidateCountMaxPerTask,
                    record.vehicleCount,
                    record.edgeCount,
                    record.cloudCount,
                    record.bandwidthPoolCount,
                    bool(record.applied),
                    bool(record.stale),
                    bool(record.failed),
                    bool(record.emptyTaskSet),
                    bool(record.shutdownInFlight),
                    record.skippedSubmissionTicksWhileWorkerBusy,
                    record.snapshotsProducedWhileWorkerBusy,
                    record.tasksGeneratedWhileWorkerBusy,
                    record.tasksExpiredWhileWorkerBusy,
                    record.lastKnownGeneration,
                    record.lastKnownStage,
                    f(record.lastHeartbeatWallClockMillis),
                    record.populationSizeConfigured,
                    record.populationSizeActual,
                    record.maxGenerations,
                    record.stallGenerationLimit,
                    f(record.improvementEpsilon),
                    f(record.crossoverRate),
                    f(record.mutationRate),
                    record.eliteCount,
                    record.scalingMode,
                    record.warmStartMode,
                    record.reusedChromosomeCount,
                    f(record.inputValidationMillis),
                    f(record.taskCandidateMappingMillis),
                    f(record.populationAdaptationMillis),
                    f(record.populationInitializationMillis),
                    f(record.initialChromosomeCreationMillis),
                    f(record.initialRepairMillis),
                    f(record.initialFitnessMillis),
                    f(record.initialSortingMillis),
                    f(record.finalRepairMillis),
                    f(record.finalFitnessMillis),
                    f(record.finalSortingMillis),
                    f(record.resultConstructionMillis),
                    f(record.serializationOrReportingMillis),
                    f(record.totalOptimizerMillis),
                    record.generationsExecuted,
                    record.stopReason,
                    record.heapUsedAtStart,
                    record.heapUsedAtEnd,
                    record.gcCollectionCountDelta,
                    record.gcCollectionTimeMillisDelta
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeWorkerBlocking(
            JobRuntimeRecord record,
            String event,
            long simulationTimeNs
    ) {
        try {
            long nowNs = System.nanoTime();
            double elapsedMs = elapsedMillis(
                    record.submissionWallClockNs,
                    nowNs
            );
            writeCsv(
                    workerBlockingWriter,
                    runId,
                    configurationId,
                    seed,
                    record.jobId,
                    event,
                    f(simulationTimeNs / NS_PER_SECOND),
                    System.currentTimeMillis(),
                    f(elapsedMs),
                    f(record.deltaTMaxMillis),
                    record.workerBusyTicksAfterBudget,
                    record.skippedSubmissionTicksWhileWorkerBusy,
                    record.snapshotsProducedWhileWorkerBusy,
                    record.tasksGeneratedWhileWorkerBusy,
                    record.tasksExpiredWhileWorkerBusy,
                    record.lastKnownGeneration,
                    record.lastKnownStage
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeHeaders() throws IOException {
        writeLine(jobWriter,
                "runId,configurationId,seed,jobId,"
                        + "simulationTimestampAtSubmit_s,snapshotTimestamp_s,"
                        + "wallClockStartEpochMillis,wallClockEndEpochMillis,"
                        + "gaWallClockMillis,gaThreadCpuMillis,"
                        + "simulationTimestampAtCompletionPoll_s,"
                        + "simulatedTimeAdvancedWhileRunning_s,"
                        + "snapshotAgeAtCompletion_s,deltaTMaxMillis,"
                        + "timeBeyondBudgetMillis,taskCount,candidateCountTotal,"
                        + "candidateCountMinPerTask,candidateCountMeanPerTask,"
                        + "candidateCountMaxPerTask,vehicleCount,edgeCount,"
                        + "cloudCount,bandwidthPoolCount,applied,stale,failed,"
                        + "emptyTaskSet,shutdownInFlight,"
                        + "skippedSubmissionTicksWhileWorkerBusy,"
                        + "snapshotsProducedWhileWorkerBusy,"
                        + "tasksGeneratedWhileWorkerBusy,tasksExpiredWhileWorkerBusy,"
                        + "lastKnownGeneration,lastKnownStage,"
                        + "lastHeartbeatWallClockMillis,populationSizeConfigured,"
                        + "populationSizeActual,maxGenerations,stallGenerationLimit,"
                        + "improvementEpsilon,crossoverRate,mutationRate,eliteCount,"
                        + "scalingMode,warmStartMode,reusedChromosomeCount,"
                        + "inputValidationMillis,taskCandidateMappingMillis,"
                        + "populationAdaptationMillis,populationInitializationMillis,"
                        + "initialChromosomeCreationMillis,initialRepairMillis,"
                        + "initialFitnessMillis,initialSortingMillis,finalRepairMillis,"
                        + "finalFitnessMillis,finalSortingMillis,"
                        + "resultConstructionMillis,serializationOrReportingMillis,"
                        + "totalOptimizerMillis,generationsExecuted,stopReason,"
                        + "heapUsedAtStartBytes,heapUsedAtEndBytes,"
                        + "gcCollectionCountDelta,gcCollectionTimeMillisDelta");
        writeLine(generationWriter,
                "runId,configurationId,seed,jobId,generationIndex,"
                        + "generationWallClockMillis,generationThreadCpuMillis,"
                        + "selectionMillis,crossoverMillis,mutationMillis,"
                        + "repairMillis,fitnessMillis,sortingAndElitismMillis,"
                        + "terminationCheckMillis,chromosomesCreated,"
                        + "chromosomesReused,chromosomesRepaired,repairCalls,"
                        + "fitnessEvaluations,candidateScans,taskGeneVisits,"
                        + "feasibleChromosomes,infeasibleChromosomes,bestFitness,"
                        + "previousBestFitness,fitnessImprovement,stallCounter,"
                        + "heapUsedBeforeBytes,heapUsedAfterBytes");
        writeLine(stageWriter,
                "runId,configurationId,seed,jobId,generationIndex,"
                        + "chromosomeIndex,stage,wallClockMillis,units");
        writeLine(fitnessWriter,
                "runId,configurationId,seed,jobId,generationIndex,"
                        + "chromosomeIndex,fitnessEvaluationCount,totalFitnessMillis,"
                        + "localExecutionEvaluationMillis,"
                        + "remoteExecutionEvaluationMillis,communicationLatencyMillis,"
                        + "mobilityPenaltyMillis,resourcePenaltyMillis,"
                        + "deadlinePenaltyMillis,localContentionMillis,"
                        + "bandwidthPoolEvaluationMillis,structuralConstraintMillis,"
                        + "localGenesEvaluated,vehicleGenesEvaluated,"
                        + "edgeGenesEvaluated,cloudGenesEvaluated,taskGeneVisits");
        writeLine(repairWriter,
                "runId,configurationId,seed,jobId,generationIndex,"
                        + "chromosomeIndex,repairMode,repairInvocationCount,"
                        + "totalRepairMillis,repairedChromosomeCount,"
                        + "repairedGeneCount,localOverflowRepairs,"
                        + "bandwidthRepairs,cpuCapacityRepairs,structuralRepairs,"
                        + "mobilityRepairs,deadlineRepairs,candidateSearches,"
                        + "candidateComparisons,fallbackAssignments,"
                        + "unrepairedViolations,repairPasses,geneRepairMillis,"
                        + "localContentionRepairMillis,cpuAggregateRepairMillis,"
                        + "bandwidthAggregateRepairMillis,bandwidthPoolRepairMillis");
        writeLine(workerBlockingWriter,
                "runId,configurationId,seed,jobId,event,"
                        + "simulationTimestamp_s,wallClockEpochMillis,"
                        + "elapsedSinceSubmitMillis,deltaTMaxMillis,"
                        + "workerBusyTicksAfterBudget,"
                        + "skippedSubmissionTicksWhileWorkerBusy,"
                        + "snapshotsProducedWhileWorkerBusy,"
                        + "tasksGeneratedWhileWorkerBusy,tasksExpiredWhileWorkerBusy,"
                        + "lastKnownGeneration,lastKnownStage");
        writeLine(heartbeatWriter,
                "runId,configurationId,seed,jobId,currentGeneration,"
                        + "currentStage,currentChromosomeIndex,currentTaskIndex,"
                        + "elapsedWallClockMillis,bestFitness,stallCounter,"
                        + "repairCalls,fitnessEvaluations,lastProgressWallClockMillis");
        writeLine(jvmWriter,
                "runId,configurationId,seed,event,wallClockEpochMillis,"
                        + "javaVersion,jvmVendor,os,cpuArchitecture,"
                        + "availableProcessors,maxHeapBytes,initialHeapBytes,"
                        + "committedHeapBytes,usedHeapBytes,gcCollectionCount,"
                        + "gcCollectionTimeMillis");
    }

    private static void writeCsv(BufferedWriter writer, Object... values)
            throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csv(values[i]));
        }
        writeLine(writer, builder.toString());
    }

    private static void writeLine(BufferedWriter writer, String line)
            throws IOException {
        writer.write(line);
        writer.newLine();
    }

    private static String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains("\"")) {
            text = text.replace("\"", "\"\"");
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n")
                || text.contains("\r")) {
            return "\"" + text + "\"";
        }
        return text;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private static String f(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String bool(boolean value) {
        return value ? "true" : "false";
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long currentGcCollectionCount() {
        long total = 0L;
        for (GarbageCollectorMXBean bean
                : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            if (count >= 0L) {
                total += count;
            }
        }
        return total;
    }

    private static long currentGcCollectionTimeMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean bean
                : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = bean.getCollectionTime();
            if (time >= 0L) {
                total += time;
            }
        }
        return total;
    }

    private static double elapsedMillis(long startNs, long endNs) {
        return Math.max(0.0, (endNs - startNs) / 1_000_000.0);
    }

    private static final class JobRuntimeRecord {
        private final String jobId;
        private final long submissionSimulationTimeNs;
        private final long submissionWallClockNs;
        private final long wallClockStartEpochMillis;
        private final double snapshotTimestampSeconds;
        private final int taskCount;
        private final int candidateCountTotal;
        private final int candidateCountMinPerTask;
        private final double candidateCountMeanPerTask;
        private final int candidateCountMaxPerTask;
        private final int vehicleCount;
        private final int edgeCount;
        private final int cloudCount;
        private final int bandwidthPoolCount;
        private final long heapUsedAtStart;
        private final long gcCollectionCountAtStart;
        private final long gcCollectionTimeMillisAtStart;
        private long wallClockEndEpochMillis;
        private double gaWallClockMillis;
        private double gaThreadCpuMillis;
        private long completionPollSimulationTimeNs;
        private double deltaTMaxMillis;
        private double timeBeyondBudgetMillis;
        private boolean applied;
        private boolean stale;
        private boolean failed;
        private boolean emptyTaskSet;
        private boolean shutdownInFlight;
        private long skippedSubmissionTicksWhileWorkerBusy;
        private long snapshotsProducedWhileWorkerBusy;
        private long tasksGeneratedWhileWorkerBusy;
        private long tasksExpiredWhileWorkerBusy;
        private long workerBusyTicksAfterBudget;
        private long firstBudgetOverrunWallClockNs;
        private long firstBudgetOverrunSimulationTimeNs;
        private int lastKnownGeneration = -1;
        private String lastKnownStage = "UNKNOWN";
        private double lastHeartbeatWallClockMillis;
        private double lastProgressWallClockMillis;
        private int populationSizeConfigured;
        private int populationSizeActual;
        private int maxGenerations;
        private int stallGenerationLimit;
        private double improvementEpsilon;
        private double crossoverRate;
        private double mutationRate;
        private int eliteCount;
        private String scalingMode = "UNKNOWN";
        private String warmStartMode = "UNKNOWN";
        private int reusedChromosomeCount;
        private double inputValidationMillis;
        private double taskCandidateMappingMillis;
        private double populationAdaptationMillis;
        private double populationInitializationMillis;
        private double initialChromosomeCreationMillis;
        private double initialRepairMillis;
        private double initialFitnessMillis;
        private double initialSortingMillis;
        private double finalRepairMillis;
        private double finalFitnessMillis;
        private double finalSortingMillis;
        private double resultConstructionMillis;
        private double serializationOrReportingMillis;
        private double totalOptimizerMillis;
        private int generationsExecuted;
        private String stopReason = "UNKNOWN";
        private long heapUsedAtEnd;
        private long gcCollectionCountDelta;
        private long gcCollectionTimeMillisDelta;

        private JobRuntimeRecord(LiveGaJob job) {
            this.jobId = job.getJobId();
            this.submissionSimulationTimeNs = job.getSubmissionSimulationTimeNs();
            this.submissionWallClockNs = job.getSubmissionWallClockNs();
            this.wallClockStartEpochMillis = job.getSubmissionWallClockEpochMillis();
            SystemSnapshot snapshot = job.getSnapshot();
            this.snapshotTimestampSeconds = snapshot.getTimeSeconds();
            this.taskCount = snapshot.getTasks().size();
            this.candidateCountTotal = snapshot.getCandidateNodes().size();
            CandidateStats candidateStats = candidateStats(snapshot);
            this.candidateCountMinPerTask = candidateStats.min;
            this.candidateCountMeanPerTask = candidateStats.mean;
            this.candidateCountMaxPerTask = candidateStats.max;
            this.vehicleCount = snapshot.getVehicles().size();
            this.edgeCount = countCandidates(snapshot, NodeType.EDGE);
            this.cloudCount = countCandidates(snapshot, NodeType.CLOUD);
            this.bandwidthPoolCount = snapshot.getBandwidthPools().size();
            this.deltaTMaxMillis = job.getDeltaTMaxAtSubmissionSeconds() * 1000.0;
            this.heapUsedAtStart = usedHeapBytes();
            this.heapUsedAtEnd = heapUsedAtStart;
            this.gcCollectionCountAtStart = currentGcCollectionCount();
            this.gcCollectionTimeMillisAtStart = currentGcCollectionTimeMillis();
            this.emptyTaskSet = taskCount == 0;
        }

        private static JobRuntimeRecord from(LiveGaJob job) {
            return new JobRuntimeRecord(job);
        }

        private static int countCandidates(SystemSnapshot snapshot, NodeType type) {
            int count = 0;
            for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
                if (candidate.getType() == type) {
                    count++;
                }
            }
            return count;
        }

        private static CandidateStats candidateStats(SystemSnapshot snapshot) {
            if (snapshot.getTasks().isEmpty()) {
                return new CandidateStats(0, 0.0, 0);
            }
            int min = Integer.MAX_VALUE;
            int max = 0;
            long total = 0L;
            for (model.snapshot.TaskInstance task : snapshot.getTasks()) {
                int count = 0;
                for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
                    if (candidate.isValidForSourceVehicle(
                            task.getSourceVehicleId()
                    )) {
                        count++;
                    }
                }
                min = Math.min(min, count);
                max = Math.max(max, count);
                total += count;
            }
            return new CandidateStats(
                    min == Integer.MAX_VALUE ? 0 : min,
                    total / (double) snapshot.getTasks().size(),
                    max
            );
        }
    }

    private static final class CandidateStats {
        private final int min;
        private final double mean;
        private final int max;

        private CandidateStats(int min, double mean, int max) {
            this.min = min;
            this.mean = mean;
            this.max = max;
        }
    }
}

