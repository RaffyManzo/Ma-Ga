package io.reporting.diagnostics.deadline;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Riassunto diagnostico delle deadline per una singola finestra temporale.
 */
public final class DeadlineViolationWindowSummary {

    private final int totalTasks;
    private final int respectedTasks;
    private final int violatedTasks;
    private final double violationRate;

    private final Map<DeadlineViolationCause, Integer> countByCause;
    private final List<DeadlineViolationDiagnosis> diagnoses;
    private final List<DeadlineViolationDiagnosis> violationsBySeverity;

    public DeadlineViolationWindowSummary(
            int totalTasks,
            int respectedTasks,
            int violatedTasks,
            double violationRate,
            Map<DeadlineViolationCause, Integer> countByCause,
            List<DeadlineViolationDiagnosis> diagnoses,
            List<DeadlineViolationDiagnosis> violationsBySeverity
    ) {
        this.totalTasks = totalTasks;
        this.respectedTasks = respectedTasks;
        this.violatedTasks = violatedTasks;
        this.violationRate = violationRate;
        this.countByCause = new EnumMap<>(countByCause);
        this.diagnoses = List.copyOf(diagnoses);
        this.violationsBySeverity = List.copyOf(violationsBySeverity);
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public int getRespectedTasks() {
        return respectedTasks;
    }

    public int getViolatedTasks() {
        return violatedTasks;
    }

    public double getViolationRate() {
        return violationRate;
    }

    public Map<DeadlineViolationCause, Integer> getCountByCause() {
        return Collections.unmodifiableMap(countByCause);
    }

    public int getCountForCause(DeadlineViolationCause cause) {
        return countByCause.getOrDefault(cause, 0);
    }

    public List<DeadlineViolationDiagnosis> getDiagnoses() {
        return diagnoses;
    }

    public List<DeadlineViolationDiagnosis> getViolationsBySeverity() {
        return violationsBySeverity;
    }
}
