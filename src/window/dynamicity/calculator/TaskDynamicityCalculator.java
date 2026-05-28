package window.dynamicity.calculator;

import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import window.dynamicity.compare.MetricMapComparator;
import window.dynamicity.metrics.TaskMetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Calcola Dt(k), cioè la variazione dei task attivi tra due snapshot.
 *
 * Il confronto considera:
 *
 * - task comparsi o scomparsi;
 * - cambio del veicolo sorgente;
 * - input size;
 * - output size;
 * - cicli CPU richiesti;
 * - deadline.
 */
public final class TaskDynamicityCalculator {

    private final MetricMapComparator<TaskMetrics> comparator;

    /**
     * Costruisce il calculator con comparator standard.
     */
    public TaskDynamicityCalculator() {
        this(new MetricMapComparator<>());
    }

    /**
     * Costruisce il calculator con comparator esplicito.
     *
     * @param comparator comparator tra mappe di metriche
     */
    public TaskDynamicityCalculator(
            MetricMapComparator<TaskMetrics> comparator
    ) {
        this.comparator = Objects.requireNonNull(
                comparator,
                "comparator must not be null."
        );
    }

    /**
     * Calcola la variazione normalizzata dei task.
     *
     * @param previousSnapshot snapshot precedente
     * @param currentSnapshot snapshot corrente
     * @return Dt(k) in [0, 1]
     */
    public double compute(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        return comparator.computeVariation(
                buildTaskMap(previousSnapshot),
                buildTaskMap(currentSnapshot)
        );
    }

    /**
     * Costruisce la mappa taskId -> TaskMetrics.
     */
    private Map<String, TaskMetrics> buildTaskMap(
            SystemSnapshot snapshot
    ) {
        Map<String, TaskMetrics> result = new HashMap<>();

        for (TaskInstance task : snapshot.getTasks()) {
            result.put(
                    task.getTaskId(),
                    new TaskMetrics(
                            task.getSourceVehicleId(),
                            task.getInputSizeBits(),
                            task.getOutputSizeBits(),
                            task.getCpuCycles(),
                            task.getDeadlineSeconds()
                    )
            );
        }

        return result;
    }
}
