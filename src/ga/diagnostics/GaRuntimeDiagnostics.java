package ga.diagnostics;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Locale;

public final class GaRuntimeDiagnostics {
    public static final long HEARTBEAT_INTERVAL_NS = 500_000_000L;

    private static final ThreadMXBean THREAD_MX_BEAN =
            ManagementFactory.getThreadMXBean();
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private GaRuntimeDiagnostics() {
    }

    public interface Sink {
        void onStage(StageRecord record);

        void onGeneration(GenerationRecord record);

        void onFitness(FitnessRecord record);

        void onRepair(RepairRecord record);

        void onHeartbeat(HeartbeatRecord record);

        void onOptimizerSummary(OptimizerSummaryRecord record);
    }

    public static Context beginJob(JobDescriptor descriptor, Sink sink) {
        Context context = new Context(descriptor, sink);
        CURRENT.set(context);
        context.stage("JOB_START");
        return context;
    }

    public static void clearJob() {
        CURRENT.remove();
    }

    public static Context current() {
        return CURRENT.get();
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    public static long cpuTimeNs() {
        if (THREAD_MX_BEAN == null
                || !THREAD_MX_BEAN.isCurrentThreadCpuTimeSupported()) {
            return -1L;
        }
        if (!THREAD_MX_BEAN.isThreadCpuTimeEnabled()) {
            try {
                THREAD_MX_BEAN.setThreadCpuTimeEnabled(true);
            } catch (SecurityException | UnsupportedOperationException ignored) {
                return -1L;
            }
        }
        return THREAD_MX_BEAN.getCurrentThreadCpuTime();
    }

    public static long heapUsedBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    public static String format(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    public static void stage(String stage) {
        Context context = CURRENT.get();
        if (context != null) {
            context.stage(stage);
        }
    }

    public static void chromosomeIndex(int chromosomeIndex) {
        Context context = CURRENT.get();
        if (context != null) {
            context.currentChromosomeIndex = chromosomeIndex;
            context.heartbeat(false);
        }
    }

    public static void taskIndex(int taskIndex) {
        Context context = CURRENT.get();
        if (context != null) {
            context.currentTaskIndex = taskIndex;
            context.heartbeat(false);
        }
    }

    public static void bestFitness(double bestFitness) {
        Context context = CURRENT.get();
        if (context != null) {
            context.bestFitness = bestFitness;
        }
    }

    public static void stallCounter(int stallCounter) {
        Context context = CURRENT.get();
        if (context != null) {
            context.stallCounter = stallCounter;
        }
    }

    public static void recordStage(
            String stage,
            int generationIndex,
            int chromosomeIndex,
            long elapsedNs,
            long units
    ) {
        Context context = CURRENT.get();
        if (context != null) {
            context.recordStage(stage, generationIndex, chromosomeIndex, elapsedNs, units);
        }
    }

    public static void addInitialChromosomeCreationNs(long elapsedNs) {
        Context context = CURRENT.get();
        if (context != null) {
            context.addInitialChromosomeCreationNs(elapsedNs);
        }
    }

    public static void addPopulationAdaptationNs(long elapsedNs) {
        Context context = CURRENT.get();
        if (context != null) {
            context.addPopulationAdaptationNs(elapsedNs);
        }
    }

    public static void addInitialRepairNs(long elapsedNs) {
        Context context = CURRENT.get();
        if (context != null) {
            context.addInitialRepairNs(elapsedNs);
        }
    }

    public static void recordGeneration(GenerationRecord record) {
        Context context = CURRENT.get();
        if (context != null) {
            context.lastKnownGeneration = record.generationIndex;
            context.bestFitness = record.bestFitness;
            context.stallCounter = record.stallCounter;
            context.emitGeneration(record);
        }
    }

    public static void recordFitness(FitnessRecord record) {
        Context context = CURRENT.get();
        if (context != null) {
            context.fitnessEvaluations += record.fitnessEvaluationCount;
            context.emitFitness(record);
        }
    }

    public static RepairScope beginRepair(String repairMode) {
        Context context = CURRENT.get();
        if (context == null) {
            return RepairScope.noop();
        }
        context.repairCalls++;
        return new RepairScope(context, repairMode);
    }

    public static void emitRepair(RepairRecord record) {
        Context context = CURRENT.get();
        if (context != null) {
            context.emitRepair(record);
        }
    }

    public static void emitOptimizerSummary(OptimizerSummaryRecord record) {
        Context context = CURRENT.get();
        if (context != null) {
            context.emitOptimizerSummary(record);
        }
    }

    public static final class Context {
        private final JobDescriptor descriptor;
        private final Sink sink;
        private final long startNs;
        private long lastHeartbeatNs;
        private long lastProgressNs;
        private String currentStage = "UNKNOWN";
        private int lastKnownGeneration = -1;
        private int currentChromosomeIndex = -1;
        private int currentTaskIndex = -1;
        private double bestFitness = Double.NaN;
        private int stallCounter;
        private long repairCalls;
        private long fitnessEvaluations;
        private long populationAdaptationNs;
        private long initialChromosomeCreationNs;
        private long initialRepairNs;

        private Context(JobDescriptor descriptor, Sink sink) {
            this.descriptor = descriptor;
            this.sink = sink;
            this.startNs = System.nanoTime();
            this.lastHeartbeatNs = startNs;
            this.lastProgressNs = startNs;
        }

        public JobDescriptor descriptor() {
            return descriptor;
        }

        public String currentStage() {
            return currentStage;
        }

        public int lastKnownGeneration() {
            return lastKnownGeneration;
        }

        public long elapsedNs() {
            return System.nanoTime() - startNs;
        }

        public long lastProgressElapsedNs() {
            return lastProgressNs - startNs;
        }

        public void addInitialChromosomeCreationNs(long elapsedNs) {
            initialChromosomeCreationNs += Math.max(0L, elapsedNs);
        }

        public void addPopulationAdaptationNs(long elapsedNs) {
            populationAdaptationNs += Math.max(0L, elapsedNs);
        }

        public void addInitialRepairNs(long elapsedNs) {
            initialRepairNs += Math.max(0L, elapsedNs);
        }

        public double initialChromosomeCreationMillis() {
            return millis(initialChromosomeCreationNs);
        }

        public double populationAdaptationMillis() {
            return millis(populationAdaptationNs);
        }

        public double initialRepairMillis() {
            return millis(initialRepairNs);
        }

        public void stage(String stage) {
            currentStage = stage == null || stage.isBlank() ? "UNKNOWN" : stage;
            lastProgressNs = System.nanoTime();
            heartbeat(false);
        }

        public void generation(int generationIndex) {
            lastKnownGeneration = generationIndex;
            lastProgressNs = System.nanoTime();
            heartbeat(false);
        }

        public void heartbeat(boolean force) {
            if (sink == null) {
                return;
            }
            long now = System.nanoTime();
            if (!force && now - lastHeartbeatNs < HEARTBEAT_INTERVAL_NS) {
                return;
            }
            lastHeartbeatNs = now;
            sink.onHeartbeat(new HeartbeatRecord(
                    descriptor,
                    lastKnownGeneration,
                    currentStage,
                    currentChromosomeIndex,
                    currentTaskIndex,
                    millis(now - startNs),
                    bestFitness,
                    stallCounter,
                    repairCalls,
                    fitnessEvaluations,
                    millis(lastProgressNs - startNs)
            ));
        }

        public void recordStage(
                String stage,
                int generationIndex,
                int chromosomeIndex,
                long elapsedNs,
                long units
        ) {
            if (sink == null) {
                return;
            }
            sink.onStage(new StageRecord(
                    descriptor,
                    generationIndex,
                    chromosomeIndex,
                    stage,
                    millis(elapsedNs),
                    Math.max(0L, units)
            ));
        }

        private void emitGeneration(GenerationRecord record) {
            if (sink != null) {
                sink.onGeneration(record);
                heartbeat(true);
            }
        }

        private void emitFitness(FitnessRecord record) {
            if (sink != null) {
                sink.onFitness(record);
            }
        }

        private void emitRepair(RepairRecord record) {
            if (sink != null) {
                sink.onRepair(record);
            }
        }

        private void emitOptimizerSummary(OptimizerSummaryRecord record) {
            if (sink != null) {
                sink.onOptimizerSummary(record);
            }
        }
    }

    public static final class RepairScope {
        private final Context context;
        private final String repairMode;
        private final long startNs;
        private long geneRepairNs;
        private long localContentionNs;
        private long cpuAggregateNs;
        private long bandwidthAggregateNs;
        private long bandwidthPoolAggregateNs;
        private long repairedGeneCount;
        private long candidateSearches;
        private long candidateComparisons;
        private long fallbackAssignments;
        private long localOverflowRepairs;
        private long bandwidthRepairs;
        private long cpuCapacityRepairs;
        private long structuralRepairs;
        private long mobilityRepairs;
        private long deadlineRepairs;
        private long unrepairedViolations;
        private int passCount;
        private boolean repairedChromosome;

        private RepairScope(Context context, String repairMode) {
            this.context = context;
            this.repairMode = repairMode;
            this.startNs = System.nanoTime();
        }

        private static RepairScope noop() {
            return new RepairScope(null, "NOOP");
        }

        public boolean active() {
            return context != null;
        }

        public void pass() {
            passCount++;
        }

        public void geneRepairNs(long value) {
            geneRepairNs += Math.max(0L, value);
        }

        public void localContentionNs(long value) {
            localContentionNs += Math.max(0L, value);
        }

        public void cpuAggregateNs(long value) {
            cpuAggregateNs += Math.max(0L, value);
        }

        public void bandwidthAggregateNs(long value) {
            bandwidthAggregateNs += Math.max(0L, value);
        }

        public void bandwidthPoolAggregateNs(long value) {
            bandwidthPoolAggregateNs += Math.max(0L, value);
        }

        public void repairedGenes(long value) {
            repairedGeneCount += Math.max(0L, value);
        }

        public void candidateSearch() {
            candidateSearches++;
        }

        public void candidateComparison() {
            candidateComparisons++;
        }

        public void fallbackAssignment() {
            fallbackAssignments++;
            structuralRepairs++;
            repairedChromosome = true;
        }

        public void localOverflowRepair() {
            localOverflowRepairs++;
            repairedChromosome = true;
        }

        public void bandwidthRepair() {
            bandwidthRepairs++;
            repairedChromosome = true;
        }

        public void cpuCapacityRepair() {
            cpuCapacityRepairs++;
            repairedChromosome = true;
        }

        public void structuralRepair() {
            structuralRepairs++;
            repairedChromosome = true;
        }

        public void mobilityRepair() {
            mobilityRepairs++;
            repairedChromosome = true;
        }

        public void deadlineRepair() {
            deadlineRepairs++;
            repairedChromosome = true;
        }

        public void unrepairedViolation() {
            unrepairedViolations++;
        }

        public void changed() {
            repairedChromosome = true;
        }

        public void finish() {
            if (context == null) {
                return;
            }
            long totalNs = System.nanoTime() - startNs;
            context.emitRepair(new RepairRecord(
                    context.descriptor,
                    context.lastKnownGeneration,
                    context.currentChromosomeIndex,
                    repairMode,
                    1L,
                    millis(totalNs),
                    repairedChromosome ? 1L : 0L,
                    repairedGeneCount,
                    localOverflowRepairs,
                    bandwidthRepairs,
                    cpuCapacityRepairs,
                    structuralRepairs,
                    mobilityRepairs,
                    deadlineRepairs,
                    candidateSearches,
                    candidateComparisons,
                    fallbackAssignments,
                    unrepairedViolations,
                    passCount,
                    millis(geneRepairNs),
                    millis(localContentionNs),
                    millis(cpuAggregateNs),
                    millis(bandwidthAggregateNs),
                    millis(bandwidthPoolAggregateNs)
            ));
            context.recordStage(
                    "repair." + repairMode,
                    context.lastKnownGeneration,
                    context.currentChromosomeIndex,
                    totalNs,
                    1L
            );
        }
    }

    public static final class JobDescriptor {
        public final String runId;
        public final String configurationId;
        public final String seed;
        public final String jobId;

        public JobDescriptor(
                String runId,
                String configurationId,
                String seed,
                String jobId
        ) {
            this.runId = clean(runId);
            this.configurationId = clean(configurationId);
            this.seed = clean(seed);
            this.jobId = clean(jobId);
        }

        private static String clean(String value) {
            return value == null || value.isBlank() ? "UNKNOWN" : value;
        }
    }

    public static final class StageRecord {
        public final JobDescriptor job;
        public final int generationIndex;
        public final int chromosomeIndex;
        public final String stage;
        public final double wallClockMillis;
        public final long units;

        public StageRecord(
                JobDescriptor job,
                int generationIndex,
                int chromosomeIndex,
                String stage,
                double wallClockMillis,
                long units
        ) {
            this.job = job;
            this.generationIndex = generationIndex;
            this.chromosomeIndex = chromosomeIndex;
            this.stage = stage;
            this.wallClockMillis = wallClockMillis;
            this.units = units;
        }
    }

    public static final class GenerationRecord {
        public final JobDescriptor job;
        public final int generationIndex;
        public final double generationWallClockMillis;
        public final double generationThreadCpuMillis;
        public final double selectionMillis;
        public final double crossoverMillis;
        public final double mutationMillis;
        public final double repairMillis;
        public final double fitnessMillis;
        public final double sortingAndElitismMillis;
        public final double terminationCheckMillis;
        public final long chromosomesCreated;
        public final long chromosomesReused;
        public final long chromosomesRepaired;
        public final long repairCalls;
        public final long fitnessEvaluations;
        public final long candidateScans;
        public final long taskGeneVisits;
        public final long feasibleChromosomes;
        public final long infeasibleChromosomes;
        public final double bestFitness;
        public final double previousBestFitness;
        public final double fitnessImprovement;
        public final int stallCounter;
        public final long heapUsedBeforeBytes;
        public final long heapUsedAfterBytes;

        @SuppressWarnings("ParameterNumber")
        public GenerationRecord(
                JobDescriptor job,
                int generationIndex,
                double generationWallClockMillis,
                double generationThreadCpuMillis,
                double selectionMillis,
                double crossoverMillis,
                double mutationMillis,
                double repairMillis,
                double fitnessMillis,
                double sortingAndElitismMillis,
                double terminationCheckMillis,
                long chromosomesCreated,
                long chromosomesReused,
                long chromosomesRepaired,
                long repairCalls,
                long fitnessEvaluations,
                long candidateScans,
                long taskGeneVisits,
                long feasibleChromosomes,
                long infeasibleChromosomes,
                double bestFitness,
                double previousBestFitness,
                double fitnessImprovement,
                int stallCounter,
                long heapUsedBeforeBytes,
                long heapUsedAfterBytes
        ) {
            this.job = job;
            this.generationIndex = generationIndex;
            this.generationWallClockMillis = generationWallClockMillis;
            this.generationThreadCpuMillis = generationThreadCpuMillis;
            this.selectionMillis = selectionMillis;
            this.crossoverMillis = crossoverMillis;
            this.mutationMillis = mutationMillis;
            this.repairMillis = repairMillis;
            this.fitnessMillis = fitnessMillis;
            this.sortingAndElitismMillis = sortingAndElitismMillis;
            this.terminationCheckMillis = terminationCheckMillis;
            this.chromosomesCreated = chromosomesCreated;
            this.chromosomesReused = chromosomesReused;
            this.chromosomesRepaired = chromosomesRepaired;
            this.repairCalls = repairCalls;
            this.fitnessEvaluations = fitnessEvaluations;
            this.candidateScans = candidateScans;
            this.taskGeneVisits = taskGeneVisits;
            this.feasibleChromosomes = feasibleChromosomes;
            this.infeasibleChromosomes = infeasibleChromosomes;
            this.bestFitness = bestFitness;
            this.previousBestFitness = previousBestFitness;
            this.fitnessImprovement = fitnessImprovement;
            this.stallCounter = stallCounter;
            this.heapUsedBeforeBytes = heapUsedBeforeBytes;
            this.heapUsedAfterBytes = heapUsedAfterBytes;
        }
    }

    public static final class FitnessRecord {
        public final JobDescriptor job;
        public final int generationIndex;
        public final int chromosomeIndex;
        public final long fitnessEvaluationCount;
        public final double totalFitnessMillis;
        public final double localExecutionEvaluationMillis;
        public final double remoteExecutionEvaluationMillis;
        public final double communicationLatencyMillis;
        public final double mobilityPenaltyMillis;
        public final double resourcePenaltyMillis;
        public final double deadlinePenaltyMillis;
        public final double localContentionMillis;
        public final double bandwidthPoolEvaluationMillis;
        public final double structuralConstraintMillis;
        public final long localGenesEvaluated;
        public final long vehicleGenesEvaluated;
        public final long edgeGenesEvaluated;
        public final long cloudGenesEvaluated;
        public final long taskGeneVisits;

        @SuppressWarnings("ParameterNumber")
        public FitnessRecord(
                JobDescriptor job,
                int generationIndex,
                int chromosomeIndex,
                long fitnessEvaluationCount,
                double totalFitnessMillis,
                double localExecutionEvaluationMillis,
                double remoteExecutionEvaluationMillis,
                double communicationLatencyMillis,
                double mobilityPenaltyMillis,
                double resourcePenaltyMillis,
                double deadlinePenaltyMillis,
                double localContentionMillis,
                double bandwidthPoolEvaluationMillis,
                double structuralConstraintMillis,
                long localGenesEvaluated,
                long vehicleGenesEvaluated,
                long edgeGenesEvaluated,
                long cloudGenesEvaluated,
                long taskGeneVisits
        ) {
            this.job = job;
            this.generationIndex = generationIndex;
            this.chromosomeIndex = chromosomeIndex;
            this.fitnessEvaluationCount = fitnessEvaluationCount;
            this.totalFitnessMillis = totalFitnessMillis;
            this.localExecutionEvaluationMillis = localExecutionEvaluationMillis;
            this.remoteExecutionEvaluationMillis = remoteExecutionEvaluationMillis;
            this.communicationLatencyMillis = communicationLatencyMillis;
            this.mobilityPenaltyMillis = mobilityPenaltyMillis;
            this.resourcePenaltyMillis = resourcePenaltyMillis;
            this.deadlinePenaltyMillis = deadlinePenaltyMillis;
            this.localContentionMillis = localContentionMillis;
            this.bandwidthPoolEvaluationMillis = bandwidthPoolEvaluationMillis;
            this.structuralConstraintMillis = structuralConstraintMillis;
            this.localGenesEvaluated = localGenesEvaluated;
            this.vehicleGenesEvaluated = vehicleGenesEvaluated;
            this.edgeGenesEvaluated = edgeGenesEvaluated;
            this.cloudGenesEvaluated = cloudGenesEvaluated;
            this.taskGeneVisits = taskGeneVisits;
        }
    }

    public static final class RepairRecord {
        public final JobDescriptor job;
        public final int generationIndex;
        public final int chromosomeIndex;
        public final String repairMode;
        public final long repairInvocationCount;
        public final double totalRepairMillis;
        public final long repairedChromosomeCount;
        public final long repairedGeneCount;
        public final long localOverflowRepairs;
        public final long bandwidthRepairs;
        public final long cpuCapacityRepairs;
        public final long structuralRepairs;
        public final long mobilityRepairs;
        public final long deadlineRepairs;
        public final long candidateSearches;
        public final long candidateComparisons;
        public final long fallbackAssignments;
        public final long unrepairedViolations;
        public final int repairPasses;
        public final double geneRepairMillis;
        public final double localContentionRepairMillis;
        public final double cpuAggregateRepairMillis;
        public final double bandwidthAggregateRepairMillis;
        public final double bandwidthPoolRepairMillis;

        @SuppressWarnings("ParameterNumber")
        public RepairRecord(
                JobDescriptor job,
                int generationIndex,
                int chromosomeIndex,
                String repairMode,
                long repairInvocationCount,
                double totalRepairMillis,
                long repairedChromosomeCount,
                long repairedGeneCount,
                long localOverflowRepairs,
                long bandwidthRepairs,
                long cpuCapacityRepairs,
                long structuralRepairs,
                long mobilityRepairs,
                long deadlineRepairs,
                long candidateSearches,
                long candidateComparisons,
                long fallbackAssignments,
                long unrepairedViolations,
                int repairPasses,
                double geneRepairMillis,
                double localContentionRepairMillis,
                double cpuAggregateRepairMillis,
                double bandwidthAggregateRepairMillis,
                double bandwidthPoolRepairMillis
        ) {
            this.job = job;
            this.generationIndex = generationIndex;
            this.chromosomeIndex = chromosomeIndex;
            this.repairMode = repairMode;
            this.repairInvocationCount = repairInvocationCount;
            this.totalRepairMillis = totalRepairMillis;
            this.repairedChromosomeCount = repairedChromosomeCount;
            this.repairedGeneCount = repairedGeneCount;
            this.localOverflowRepairs = localOverflowRepairs;
            this.bandwidthRepairs = bandwidthRepairs;
            this.cpuCapacityRepairs = cpuCapacityRepairs;
            this.structuralRepairs = structuralRepairs;
            this.mobilityRepairs = mobilityRepairs;
            this.deadlineRepairs = deadlineRepairs;
            this.candidateSearches = candidateSearches;
            this.candidateComparisons = candidateComparisons;
            this.fallbackAssignments = fallbackAssignments;
            this.unrepairedViolations = unrepairedViolations;
            this.repairPasses = repairPasses;
            this.geneRepairMillis = geneRepairMillis;
            this.localContentionRepairMillis = localContentionRepairMillis;
            this.cpuAggregateRepairMillis = cpuAggregateRepairMillis;
            this.bandwidthAggregateRepairMillis = bandwidthAggregateRepairMillis;
            this.bandwidthPoolRepairMillis = bandwidthPoolRepairMillis;
        }
    }

    public static final class HeartbeatRecord {
        public final JobDescriptor job;
        public final int currentGeneration;
        public final String currentStage;
        public final int currentChromosomeIndex;
        public final int currentTaskIndex;
        public final double elapsedWallClockMillis;
        public final double bestFitness;
        public final int stallCounter;
        public final long repairCalls;
        public final long fitnessEvaluations;
        public final double lastProgressWallClockMillis;

        @SuppressWarnings("ParameterNumber")
        public HeartbeatRecord(
                JobDescriptor job,
                int currentGeneration,
                String currentStage,
                int currentChromosomeIndex,
                int currentTaskIndex,
                double elapsedWallClockMillis,
                double bestFitness,
                int stallCounter,
                long repairCalls,
                long fitnessEvaluations,
                double lastProgressWallClockMillis
        ) {
            this.job = job;
            this.currentGeneration = currentGeneration;
            this.currentStage = currentStage;
            this.currentChromosomeIndex = currentChromosomeIndex;
            this.currentTaskIndex = currentTaskIndex;
            this.elapsedWallClockMillis = elapsedWallClockMillis;
            this.bestFitness = bestFitness;
            this.stallCounter = stallCounter;
            this.repairCalls = repairCalls;
            this.fitnessEvaluations = fitnessEvaluations;
            this.lastProgressWallClockMillis = lastProgressWallClockMillis;
        }
    }

    public static final class OptimizerSummaryRecord {
        public final JobDescriptor job;
        public final int populationSizeConfigured;
        public final int populationSizeActual;
        public final int maxGenerations;
        public final int stallGenerationLimit;
        public final double improvementEpsilon;
        public final double crossoverRate;
        public final double mutationRate;
        public final int eliteCount;
        public final String scalingMode;
        public final String warmStartMode;
        public final int reusedChromosomeCount;
        public final double inputValidationMillis;
        public final double taskCandidateMappingMillis;
        public final double populationAdaptationMillis;
        public final double populationInitializationMillis;
        public final double initialChromosomeCreationMillis;
        public final double initialRepairMillis;
        public final double initialFitnessMillis;
        public final double initialSortingMillis;
        public final double finalRepairMillis;
        public final double finalFitnessMillis;
        public final double finalSortingMillis;
        public final double resultConstructionMillis;
        public final double serializationOrReportingMillis;
        public final double totalOptimizerMillis;
        public final int generationsExecuted;
        public final String stopReason;

        @SuppressWarnings("ParameterNumber")
        public OptimizerSummaryRecord(
                JobDescriptor job,
                int populationSizeConfigured,
                int populationSizeActual,
                int maxGenerations,
                int stallGenerationLimit,
                double improvementEpsilon,
                double crossoverRate,
                double mutationRate,
                int eliteCount,
                String scalingMode,
                String warmStartMode,
                int reusedChromosomeCount,
                double inputValidationMillis,
                double taskCandidateMappingMillis,
                double populationAdaptationMillis,
                double populationInitializationMillis,
                double initialChromosomeCreationMillis,
                double initialRepairMillis,
                double initialFitnessMillis,
                double initialSortingMillis,
                double finalRepairMillis,
                double finalFitnessMillis,
                double finalSortingMillis,
                double resultConstructionMillis,
                double serializationOrReportingMillis,
                double totalOptimizerMillis,
                int generationsExecuted,
                String stopReason
        ) {
            this.job = job;
            this.populationSizeConfigured = populationSizeConfigured;
            this.populationSizeActual = populationSizeActual;
            this.maxGenerations = maxGenerations;
            this.stallGenerationLimit = stallGenerationLimit;
            this.improvementEpsilon = improvementEpsilon;
            this.crossoverRate = crossoverRate;
            this.mutationRate = mutationRate;
            this.eliteCount = eliteCount;
            this.scalingMode = scalingMode;
            this.warmStartMode = warmStartMode;
            this.reusedChromosomeCount = reusedChromosomeCount;
            this.inputValidationMillis = inputValidationMillis;
            this.taskCandidateMappingMillis = taskCandidateMappingMillis;
            this.populationAdaptationMillis = populationAdaptationMillis;
            this.populationInitializationMillis = populationInitializationMillis;
            this.initialChromosomeCreationMillis = initialChromosomeCreationMillis;
            this.initialRepairMillis = initialRepairMillis;
            this.initialFitnessMillis = initialFitnessMillis;
            this.initialSortingMillis = initialSortingMillis;
            this.finalRepairMillis = finalRepairMillis;
            this.finalFitnessMillis = finalFitnessMillis;
            this.finalSortingMillis = finalSortingMillis;
            this.resultConstructionMillis = resultConstructionMillis;
            this.serializationOrReportingMillis = serializationOrReportingMillis;
            this.totalOptimizerMillis = totalOptimizerMillis;
            this.generationsExecuted = generationsExecuted;
            this.stopReason = stopReason;
        }
    }
}
