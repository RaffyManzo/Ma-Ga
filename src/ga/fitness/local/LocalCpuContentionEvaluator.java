package ga.fitness.local;

import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Valuta la contesa della CPU locale tra le porzioni locali dei task attivi.
 *
 * <p>Le porzioni locali appartenenti allo stesso veicolo condividono la CPU
 * osservata nello snapshot. La schedulazione è deterministica e segue EDF:
 * deadline crescente e, a parità, taskId crescente.</p>
 *
 * <p>Per un task nella posizione {@code k}:</p>
 *
 * <pre>
 * C_i_local = (1 - p_i) * C_i
 * T_i_local_contended = sum_{j=1..k}(C_j_local) / f_vehicle
 * rho_i = sum_{j=1..k}(C_j_local) / (f_vehicle * deadline_i)
 * </pre>
 *
 * <p>Il modello è snapshot-level: rappresenta la contesa tra i task presenti
 * nello stesso snapshot, ma non introduce una coda persistente tra finestre.</p>
 */
public final class LocalCpuContentionEvaluator {

    private static final double EPSILON = 1.0E-9;
    private static final double INVALID_METRIC = 1.0E18;

    /**
     * Valuta direttamente snapshot e cromosoma.
     */
    public Evaluation evaluate(
            SystemSnapshot snapshot,
            Chromosome chromosome
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(chromosome, "chromosome must not be null.");

        Map<String, Gene> geneByTaskId = new LinkedHashMap<>();
        if (chromosome.getGenes() != null) {
            for (Gene gene : chromosome.getGenes()) {
                if (gene != null && gene.getTaskId() != null) {
                    geneByTaskId.putIfAbsent(gene.getTaskId(), gene);
                }
            }
        }

        Map<String, NodeCandidate> candidateById = new LinkedHashMap<>();
        if (snapshot.getCandidateNodes() != null) {
            for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
                if (candidate != null && candidate.getCandidateId() != null) {
                    candidateById.put(candidate.getCandidateId(), candidate);
                }
            }
        }

        Map<String, VehicleSnapshot> vehicleById = new LinkedHashMap<>();
        if (snapshot.getVehicles() != null) {
            for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
                if (vehicle != null && vehicle.getVehicleId() != null) {
                    vehicleById.put(vehicle.getVehicleId(), vehicle);
                }
            }
        }

        return evaluate(
                snapshot.getTasks(),
                geneByTaskId,
                candidateById,
                vehicleById
        );
    }

    /**
     * Valuta la contesa usando indici già disponibili.
     */
    public Evaluation evaluate(
            List<TaskInstance> tasks,
            Map<String, Gene> geneByTaskId,
            Map<String, NodeCandidate> candidateById,
            Map<String, VehicleSnapshot> vehicleById
    ) {
        Objects.requireNonNull(tasks, "tasks must not be null.");
        Objects.requireNonNull(geneByTaskId, "geneByTaskId must not be null.");
        Objects.requireNonNull(candidateById, "candidateById must not be null.");
        Objects.requireNonNull(vehicleById, "vehicleById must not be null.");

        Map<String, List<PendingLocalTask>> pendingByVehicle =
                new LinkedHashMap<>();

        for (TaskInstance task : tasks) {
            if (task == null
                    || task.getTaskId() == null
                    || task.getSourceVehicleId() == null) {
                continue;
            }

            Gene gene = geneByTaskId.get(task.getTaskId());
            if (gene == null) {
                continue;
            }

            NodeCandidate candidate = candidateById.get(
                    gene.getSelectedCandidateId()
            );
            VehicleSnapshot vehicle = vehicleById.get(
                    task.getSourceVehicleId()
            );

            if (candidate == null
                    || vehicle == null
                    || !candidate.isValidForSourceVehicle(
                            task.getSourceVehicleId()
                    )) {
                continue;
            }

            double p = sanitizeOffloadingRatio(gene, candidate);
            double localCycles = Math.max(
                    0.0,
                    (1.0 - p) * safeNonNegative(task.getCpuCycles())
            );
            if (localCycles <= EPSILON) {
                continue;
            }

            double localCpu = sanitizeLocalCpu(vehicle.getLocalCpu());
            double independentTime = safeDivide(localCycles, localCpu);

            PendingLocalTask pending = new PendingLocalTask(
                    task,
                    localCycles,
                    independentTime,
                    localCpu
            );

            pendingByVehicle.computeIfAbsent(
                    task.getSourceVehicleId(),
                    ignored -> new ArrayList<>()
            ).add(pending);
        }

        Map<String, TaskResult> taskResults = new LinkedHashMap<>();
        Map<String, VehicleResult> vehicleResults = new LinkedHashMap<>();

        for (Map.Entry<String, List<PendingLocalTask>> entry
                : pendingByVehicle.entrySet()) {
            String vehicleId = entry.getKey();
            List<PendingLocalTask> pendingTasks =
                    new ArrayList<>(entry.getValue());

            pendingTasks.sort(
                    Comparator
                            .comparingDouble(
                                    (PendingLocalTask task) ->
                                            deadlineSortKey(
                                                    task.task.getDeadlineSeconds()
                                            )
                            )
                            .thenComparing(task -> task.task.getTaskId())
            );

            double cumulativeCycles = 0.0;
            double maxIndependentTime = 0.0;
            double maxContendedTime = 0.0;
            double maxDemandRatio = 0.0;
            int deadlineViolationCount = 0;
            List<TaskResult> orderedResults = new ArrayList<>();

            double localCpu = pendingTasks.isEmpty()
                    ? 0.0
                    : pendingTasks.get(0).localCpu;

            for (int index = 0; index < pendingTasks.size(); index++) {
                PendingLocalTask pending = pendingTasks.get(index);
                cumulativeCycles += pending.localCycles;

                double contendedTime = safeDivide(cumulativeCycles, localCpu);
                double deadline = pending.task.getDeadlineSeconds();
                double demandRatio = computeDemandRatio(
                        cumulativeCycles,
                        localCpu,
                        deadline
                );
                boolean deadlineRespected = deadline <= 0.0
                        || contendedTime <= deadline + EPSILON;

                if (!deadlineRespected) {
                    deadlineViolationCount++;
                }

                TaskResult result = new TaskResult(
                        pending.task.getTaskId(),
                        vehicleId,
                        index,
                        pending.localCycles,
                        cumulativeCycles,
                        pending.independentTime,
                        contendedTime,
                        Math.max(
                                0.0,
                                contendedTime - pending.independentTime
                        ),
                        deadline,
                        demandRatio,
                        deadlineRespected
                );

                orderedResults.add(result);
                taskResults.put(result.getTaskId(), result);

                maxIndependentTime = Math.max(
                        maxIndependentTime,
                        pending.independentTime
                );
                maxContendedTime = Math.max(
                        maxContendedTime,
                        contendedTime
                );
                maxDemandRatio = Math.max(maxDemandRatio, demandRatio);
            }

            VehicleResult vehicleResult = new VehicleResult(
                    vehicleId,
                    localCpu,
                    cumulativeCycles,
                    maxIndependentTime,
                    maxContendedTime,
                    maxDemandRatio,
                    Math.max(0.0, maxDemandRatio - 1.0),
                    deadlineViolationCount,
                    orderedResults
            );
            vehicleResults.put(vehicleId, vehicleResult);
        }

        return new Evaluation(taskResults, vehicleResults);
    }

    private double sanitizeOffloadingRatio(
            Gene gene,
            NodeCandidate candidate
    ) {
        if (candidate.getType() == NodeType.LOCAL) {
            return 0.0;
        }

        double value = gene.getOffloadingRatio();
        if (!Double.isFinite(value)) {
            value = 0.0;
        }
        value = Math.max(0.0, Math.min(1.0, value));

        if (value <= EPSILON) {
            return EPSILON;
        }
        return value;
    }

    private double sanitizeLocalCpu(double localCpu) {
        if (!Double.isFinite(localCpu) || localCpu <= EPSILON) {
            return EPSILON;
        }
        return localCpu;
    }

    private double safeNonNegative(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }
        return value;
    }

    private double safeDivide(double numerator, double denominator) {
        if (!Double.isFinite(numerator)
                || !Double.isFinite(denominator)
                || denominator <= EPSILON) {
            return INVALID_METRIC;
        }

        double value = numerator / denominator;
        if (!Double.isFinite(value)) {
            return INVALID_METRIC;
        }
        return Math.min(value, INVALID_METRIC);
    }

    private double computeDemandRatio(
            double cumulativeCycles,
            double localCpu,
            double deadlineSeconds
    ) {
        if (!Double.isFinite(deadlineSeconds)
                || deadlineSeconds <= EPSILON) {
            return 0.0;
        }
        return safeDivide(
                cumulativeCycles,
                localCpu * deadlineSeconds
        );
    }

    private double deadlineSortKey(double deadlineSeconds) {
        if (!Double.isFinite(deadlineSeconds)
                || deadlineSeconds <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        return deadlineSeconds;
    }

    private static final class PendingLocalTask {
        private final TaskInstance task;
        private final double localCycles;
        private final double independentTime;
        private final double localCpu;

        private PendingLocalTask(
                TaskInstance task,
                double localCycles,
                double independentTime,
                double localCpu
        ) {
            this.task = task;
            this.localCycles = localCycles;
            this.independentTime = independentTime;
            this.localCpu = localCpu;
        }
    }

    /**
     * Risultato complessivo della valutazione.
     */
    public static final class Evaluation {
        private final Map<String, TaskResult> taskResults;
        private final Map<String, VehicleResult> vehicleResults;

        private Evaluation(
                Map<String, TaskResult> taskResults,
                Map<String, VehicleResult> vehicleResults
        ) {
            this.taskResults = Collections.unmodifiableMap(
                    new LinkedHashMap<>(taskResults)
            );
            this.vehicleResults = Collections.unmodifiableMap(
                    new LinkedHashMap<>(vehicleResults)
            );
        }

        public TaskResult getTaskResult(String taskId) {
            return taskId == null ? null : taskResults.get(taskId);
        }

        public VehicleResult getVehicleResult(String vehicleId) {
            return vehicleId == null ? null : vehicleResults.get(vehicleId);
        }

        public Map<String, TaskResult> getTaskResults() {
            return taskResults;
        }

        public Map<String, VehicleResult> getVehicleResults() {
            return vehicleResults;
        }

        public boolean hasDeadlineViolations() {
            return vehicleResults.values().stream()
                    .anyMatch(VehicleResult::hasDeadlineViolations);
        }

        public boolean hasCpuOverflow() {
            return vehicleResults.values().stream()
                    .anyMatch(VehicleResult::hasCpuOverflow);
        }

        public double getMaximumDemandRatio() {
            return vehicleResults.values().stream()
                    .mapToDouble(VehicleResult::getMaxDemandRatio)
                    .max()
                    .orElse(0.0);
        }
    }

    /**
     * Risultato per una singola porzione locale.
     */
    public static final class TaskResult {
        private final String taskId;
        private final String vehicleId;
        private final int edfPosition;
        private final double localCpuCycles;
        private final double cumulativeLocalCpuCycles;
        private final double independentExecutionTimeSeconds;
        private final double contendedCompletionTimeSeconds;
        private final double contentionDelaySeconds;
        private final double deadlineSeconds;
        private final double demandRatio;
        private final boolean deadlineRespected;

        private TaskResult(
                String taskId,
                String vehicleId,
                int edfPosition,
                double localCpuCycles,
                double cumulativeLocalCpuCycles,
                double independentExecutionTimeSeconds,
                double contendedCompletionTimeSeconds,
                double contentionDelaySeconds,
                double deadlineSeconds,
                double demandRatio,
                boolean deadlineRespected
        ) {
            this.taskId = taskId;
            this.vehicleId = vehicleId;
            this.edfPosition = edfPosition;
            this.localCpuCycles = localCpuCycles;
            this.cumulativeLocalCpuCycles = cumulativeLocalCpuCycles;
            this.independentExecutionTimeSeconds =
                    independentExecutionTimeSeconds;
            this.contendedCompletionTimeSeconds =
                    contendedCompletionTimeSeconds;
            this.contentionDelaySeconds = contentionDelaySeconds;
            this.deadlineSeconds = deadlineSeconds;
            this.demandRatio = demandRatio;
            this.deadlineRespected = deadlineRespected;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getVehicleId() {
            return vehicleId;
        }

        public int getEdfPosition() {
            return edfPosition;
        }

        public double getLocalCpuCycles() {
            return localCpuCycles;
        }

        public double getCumulativeLocalCpuCycles() {
            return cumulativeLocalCpuCycles;
        }

        public double getIndependentExecutionTimeSeconds() {
            return independentExecutionTimeSeconds;
        }

        public double getContendedCompletionTimeSeconds() {
            return contendedCompletionTimeSeconds;
        }

        public double getContentionDelaySeconds() {
            return contentionDelaySeconds;
        }

        public double getDeadlineSeconds() {
            return deadlineSeconds;
        }

        public double getDemandRatio() {
            return demandRatio;
        }

        public boolean isDeadlineRespected() {
            return deadlineRespected;
        }
    }

    /**
     * Risultato aggregato per veicolo.
     */
    public static final class VehicleResult {
        private final String vehicleId;
        private final double localCpu;
        private final double totalLocalCpuCycles;
        private final double maxIndependentExecutionTimeSeconds;
        private final double maxContendedCompletionTimeSeconds;
        private final double maxDemandRatio;
        private final double cpuOverflowRatio;
        private final int deadlineViolationCount;
        private final List<TaskResult> taskResults;

        private VehicleResult(
                String vehicleId,
                double localCpu,
                double totalLocalCpuCycles,
                double maxIndependentExecutionTimeSeconds,
                double maxContendedCompletionTimeSeconds,
                double maxDemandRatio,
                double cpuOverflowRatio,
                int deadlineViolationCount,
                List<TaskResult> taskResults
        ) {
            this.vehicleId = vehicleId;
            this.localCpu = localCpu;
            this.totalLocalCpuCycles = totalLocalCpuCycles;
            this.maxIndependentExecutionTimeSeconds =
                    maxIndependentExecutionTimeSeconds;
            this.maxContendedCompletionTimeSeconds =
                    maxContendedCompletionTimeSeconds;
            this.maxDemandRatio = maxDemandRatio;
            this.cpuOverflowRatio = cpuOverflowRatio;
            this.deadlineViolationCount = deadlineViolationCount;
            this.taskResults = Collections.unmodifiableList(
                    new ArrayList<>(taskResults)
            );
        }

        public String getVehicleId() {
            return vehicleId;
        }

        public double getLocalCpu() {
            return localCpu;
        }

        public double getTotalLocalCpuCycles() {
            return totalLocalCpuCycles;
        }

        public double getMaxIndependentExecutionTimeSeconds() {
            return maxIndependentExecutionTimeSeconds;
        }

        public double getMaxContendedCompletionTimeSeconds() {
            return maxContendedCompletionTimeSeconds;
        }

        public double getMaxDemandRatio() {
            return maxDemandRatio;
        }

        public double getCpuOverflowRatio() {
            return cpuOverflowRatio;
        }

        public int getDeadlineViolationCount() {
            return deadlineViolationCount;
        }

        public List<TaskResult> getTaskResults() {
            return taskResults;
        }

        public int getLocalTaskCount() {
            return taskResults.size();
        }

        public boolean hasCpuOverflow() {
            return cpuOverflowRatio > EPSILON;
        }

        public boolean hasDeadlineViolations() {
            return deadlineViolationCount > 0;
        }

        public boolean hasContention() {
            return taskResults.size() > 1
                    && maxContendedCompletionTimeSeconds
                    > maxIndependentExecutionTimeSeconds + EPSILON;
        }
    }
}
