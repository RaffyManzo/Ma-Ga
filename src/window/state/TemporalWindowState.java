package window.state;

import ga.core.MaGaResult;
import model.genetic.Chromosome;
import model.snapshot.SystemSnapshot;
import window.timing.TemporalOperationalMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stato interno del gestore temporale.
 *
 * <p>Il tempo gestito da questa classe è il tempo logico del manager. Non deve
 * essere confuso con il tempo salvato dentro lo snapshot JSON. Lo snapshot resta
 * una fotografia del sistema; il manager decide quando chiedere una nuova
 * fotografia.</p>
 */
public final class TemporalWindowState {

    private final int windowIndex;
    private final double currentTimeSeconds;
    private final double nextScheduledTimeSeconds;
    private final double currentWindowDurationSeconds;
    private final SystemSnapshot lastSnapshot;
    private final MaGaResult lastResult;
    private final TemporalOperationalMetrics lastOperationalMetrics;
    private final List<Chromosome> lastFinalPopulation;

    public TemporalWindowState(
            int windowIndex,
            double currentTimeSeconds,
            double nextScheduledTimeSeconds,
            double currentWindowDurationSeconds,
            SystemSnapshot lastSnapshot,
            MaGaResult lastResult,
            TemporalOperationalMetrics lastOperationalMetrics,
            List<Chromosome> lastFinalPopulation
    ) {
        if (windowIndex < 0) {
            throw new IllegalArgumentException("windowIndex must be >= 0.");
        }
        validateFiniteAndNonNegative("currentTimeSeconds", currentTimeSeconds);
        validateFiniteAndNonNegative(
                "nextScheduledTimeSeconds",
                nextScheduledTimeSeconds
        );
        validatePositive("currentWindowDurationSeconds", currentWindowDurationSeconds);
        if (nextScheduledTimeSeconds < currentTimeSeconds) {
            throw new IllegalArgumentException(
                    "nextScheduledTimeSeconds must be >= currentTimeSeconds."
            );
        }

        this.windowIndex = windowIndex;
        this.currentTimeSeconds = currentTimeSeconds;
        this.nextScheduledTimeSeconds = nextScheduledTimeSeconds;
        this.currentWindowDurationSeconds = currentWindowDurationSeconds;
        this.lastSnapshot = lastSnapshot;
        this.lastResult = lastResult;
        this.lastOperationalMetrics = lastOperationalMetrics;
        this.lastFinalPopulation = Collections.unmodifiableList(
                new ArrayList<>(
                        lastFinalPopulation == null
                                ? Collections.emptyList()
                                : lastFinalPopulation
                )
        );
    }

    public static TemporalWindowState initial(
            double startTimeSeconds,
            double initialWindowSeconds,
            TemporalOperationalMetrics initialOperationalMetrics
    ) {
        validateFiniteAndNonNegative("startTimeSeconds", startTimeSeconds);
        validatePositive("initialWindowSeconds", initialWindowSeconds);

        return new TemporalWindowState(
                0,
                startTimeSeconds,
                startTimeSeconds + initialWindowSeconds,
                initialWindowSeconds,
                null,
                null,
                initialOperationalMetrics,
                Collections.emptyList()
        );
    }

    /**
     * Factory semplificata per esecuzioni a finestra iniziale fissa.
     */
    public static TemporalWindowState initial(
            double startTimeSeconds,
            double fixedIntervalSeconds
    ) {
        return initial(startTimeSeconds, fixedIntervalSeconds, null);
    }

    public static TemporalWindowState afterStep(TemporalStepResult stepResult) {
        if (stepResult == null) {
            throw new IllegalArgumentException("stepResult must not be null.");
        }

        double currentTimeSeconds = stepResult.getLogicalObservationTimeSeconds();
        double nextWindowDuration = stepResult
                .getAdaptiveWindowDecision()
                .getNextWindowDurationSeconds();

        return new TemporalWindowState(
                stepResult.getWindowIndex() + 1,
                currentTimeSeconds,
                currentTimeSeconds + nextWindowDuration,
                nextWindowDuration,
                stepResult.getSnapshot(),
                stepResult.getMaGaResult(),
                stepResult.getOperationalMetrics(),
                stepResult.getMaGaResult().getFinalPopulation()
        );
    }

    /**
     * Transizione semplificata per chiamanti che mantengono una durata fissa.
     */
    public static TemporalWindowState afterStep(
            TemporalStepResult stepResult,
            double fixedIntervalSeconds
    ) {
        if (stepResult == null) {
            throw new IllegalArgumentException("stepResult must not be null.");
        }

        double currentTimeSeconds = stepResult.getLogicalObservationTimeSeconds();

        return new TemporalWindowState(
                stepResult.getWindowIndex() + 1,
                currentTimeSeconds,
                currentTimeSeconds + fixedIntervalSeconds,
                fixedIntervalSeconds,
                stepResult.getSnapshot(),
                stepResult.getMaGaResult(),
                stepResult.getOperationalMetrics(),
                stepResult.getMaGaResult().getFinalPopulation()
        );
    }

    public int getWindowIndex() {
        return windowIndex;
    }

    public double getCurrentTimeSeconds() {
        return currentTimeSeconds;
    }

    public double getNextScheduledTimeSeconds() {
        return nextScheduledTimeSeconds;
    }

    public double getCurrentWindowDurationSeconds() {
        return currentWindowDurationSeconds;
    }

    public SystemSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public MaGaResult getLastResult() {
        return lastResult;
    }

    public TemporalOperationalMetrics getLastOperationalMetrics() {
        return lastOperationalMetrics;
    }

    public List<Chromosome> getLastFinalPopulation() {
        return lastFinalPopulation;
    }

    public boolean hasPreviousExecution() {
        return lastSnapshot != null && lastResult != null;
    }

    public boolean hasReusablePopulation() {
        return !lastFinalPopulation.isEmpty();
    }

    private static void validateFiniteAndNonNegative(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }

    private static void validatePositive(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be > 0.");
        }
    }

    @Override
    public String toString() {
        return "TemporalWindowState{" +
                "windowIndex=" + windowIndex +
                ", currentTimeSeconds=" + currentTimeSeconds +
                ", nextScheduledTimeSeconds=" + nextScheduledTimeSeconds +
                ", currentWindowDurationSeconds=" + currentWindowDurationSeconds +
                ", lastSnapshot=" +
                (lastSnapshot == null ? null : lastSnapshot.getSnapshotId()) +
                ", lastFinalPopulationSize=" + lastFinalPopulation.size() +
                '}';
    }
}
