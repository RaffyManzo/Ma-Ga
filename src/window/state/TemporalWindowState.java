package window.state;

import ga.core.MaGaResult;
import model.genetic.Chromosome;
import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stato interno del gestore temporale.
 *
 * Conserva le informazioni necessarie per passare da una finestra alla
 * successiva: ultimo snapshot, ultimo risultato e popolazione finale.
 */
public final class TemporalWindowState {

    private final int windowIndex;
    private final double currentTimeSeconds;
    private final double nextScheduledTimeSeconds;

    private final SystemSnapshot lastSnapshot;
    private final MaGaResult lastResult;
    private final List<Chromosome> lastFinalPopulation;

    /**
     * Crea uno stato temporale.
     *
     * @param windowIndex indice della prossima finestra
     * @param currentTimeSeconds tempo da cui riparte il controllo temporale
     * @param nextScheduledTimeSeconds prossima scadenza programmata
     * @param lastSnapshot ultimo snapshot ottimizzato
     * @param lastResult ultimo risultato MA-GA
     * @param lastFinalPopulation popolazione finale precedente
     */
    public TemporalWindowState(
            int windowIndex,
            double currentTimeSeconds,
            double nextScheduledTimeSeconds,
            SystemSnapshot lastSnapshot,
            MaGaResult lastResult,
            List<Chromosome> lastFinalPopulation
    ) {
        if (windowIndex < 0) {
            throw new IllegalArgumentException("windowIndex must be >= 0.");
        }

        validateFiniteAndNonNegative("currentTimeSeconds", currentTimeSeconds);
        validateFiniteAndNonNegative("nextScheduledTimeSeconds", nextScheduledTimeSeconds);

        if (nextScheduledTimeSeconds < currentTimeSeconds) {
            throw new IllegalArgumentException(
                    "nextScheduledTimeSeconds must be >= currentTimeSeconds."
            );
        }

        this.windowIndex = windowIndex;
        this.currentTimeSeconds = currentTimeSeconds;
        this.nextScheduledTimeSeconds = nextScheduledTimeSeconds;
        this.lastSnapshot = lastSnapshot;
        this.lastResult = lastResult;

        this.lastFinalPopulation = Collections.unmodifiableList(
                new ArrayList<>(
                        lastFinalPopulation == null
                                ? Collections.emptyList()
                                : lastFinalPopulation
                )
        );
    }

    /**
     * Crea lo stato prima della prima esecuzione.
     *
     * @param startTimeSeconds tempo iniziale
     * @param fixedIntervalSeconds intervallo programmato
     * @return stato iniziale
     */
    public static TemporalWindowState initial(
            double startTimeSeconds,
            double fixedIntervalSeconds
    ) {
        validateFiniteAndNonNegative("startTimeSeconds", startTimeSeconds);
        validatePositive("fixedIntervalSeconds", fixedIntervalSeconds);

        return new TemporalWindowState(
                0,
                startTimeSeconds,
                startTimeSeconds + fixedIntervalSeconds,
                null,
                null,
                Collections.emptyList()
        );
    }

    /**
     * Costruisce lo stato dopo una finestra eseguita.
     *
     * Il tempo corrente viene allineato allo snapshot ottimizzato, non al solo
     * trigger. In questo modo la finestra successiva parte dallo stato realmente
     * usato dal MA-GA.
     *
     * @param stepResult risultato appena prodotto
     * @param fixedIntervalSeconds intervallo programmato
     * @return stato successivo
     */
    public static TemporalWindowState afterStep(
            TemporalStepResult stepResult,
            double fixedIntervalSeconds
    ) {
        if (stepResult == null) {
            throw new IllegalArgumentException("stepResult must not be null.");
        }

        validatePositive("fixedIntervalSeconds", fixedIntervalSeconds);

        double currentTimeSeconds = stepResult.getSnapshot().getTimeSeconds();

        return new TemporalWindowState(
                stepResult.getWindowIndex() + 1,
                currentTimeSeconds,
                currentTimeSeconds + fixedIntervalSeconds,
                stepResult.getSnapshot(),
                stepResult.getMaGaResult(),
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

    public SystemSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public MaGaResult getLastResult() {
        return lastResult;
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

    private static void validateFiniteAndNonNegative(String fieldName, double value) {
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
        return "TemporalWindowState{"
                + "windowIndex=" + windowIndex
                + ", currentTimeSeconds=" + currentTimeSeconds
                + ", nextScheduledTimeSeconds=" + nextScheduledTimeSeconds
                + ", lastSnapshot="
                + (lastSnapshot == null ? null : lastSnapshot.getSnapshotId())
                + ", lastFinalPopulationSize=" + lastFinalPopulation.size()
                + '}';
    }
}