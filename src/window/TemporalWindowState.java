package window;

import ga.MaGaResult;
import model.Chromosome;
import model.SystemSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stato immutabile corrente del gestore temporale.
 *
 * <p>Il {@link TemporalWindowManager} usa questa classe per conservare le
 * informazioni necessarie tra una riesecuzione del MA-GA e la successiva.
 * Lo stato contiene sia la posizione temporale della simulazione sia la memoria
 * dell'ultima ottimizzazione eseguita.</p>
 *
 * <p>La classe non avanza autonomamente il tempo e non esegue ottimizzazioni:
 * rappresenta solo una fotografia del ciclo temporale. Per passare allo stato
 * successivo si usa {@link #afterStep(TemporalStepResult, double)}.</p>
 */
public final class TemporalWindowState {

    /**
     * Indice della finestra corrente.
     */
    private final int windowIndex;

    /**
     * Tempo simulato da cui parte la prossima decisione temporale.
     */
    private final double currentTimeSeconds;

    /**
     * Tempo della prossima scadenza programmata, in assenza di eventi critici.
     */
    private final double nextScheduledTimeSeconds;

    /**
     * Ultimo snapshot ottimizzato dal MA-GA.
     *
     * <p>È {@code null} nello stato iniziale, prima della prima esecuzione.</p>
     */
    private final SystemSnapshot lastSnapshot;

    /**
     * Ultimo risultato dettagliato prodotto dal MA-GA.
     *
     * <p>È {@code null} nello stato iniziale.</p>
     */
    private final MaGaResult lastResult;

    /**
     * Popolazione finale dell'ultima esecuzione, usata come memoria genetica
     * riutilizzabile nella finestra successiva.
     */
    private final List<Chromosome> lastFinalPopulation;

    /**
     * Crea uno stato temporale.
     *
     * <p>Il costruttore valida la coerenza temporale: la prossima scadenza
     * programmata non può essere precedente al tempo corrente. La popolazione
     * finale viene copiata in una lista immutabile; se è {@code null}, viene
     * trattata come lista vuota.</p>
     *
     * @param windowIndex indice della finestra corrente
     * @param currentTimeSeconds tempo simulato corrente
     * @param nextScheduledTimeSeconds prossima riesecuzione programmata
     * @param lastSnapshot ultimo snapshot ottimizzato
     * @param lastResult ultimo risultato MA-GA
     * @param lastFinalPopulation popolazione finale dell'ultima esecuzione
     */
    public TemporalWindowState(
            int windowIndex,
            double currentTimeSeconds,
            double nextScheduledTimeSeconds,
            SystemSnapshot lastSnapshot,
            MaGaResult lastResult,
            List<Chromosome> lastFinalPopulation
    ) {
        // Gli indici di finestra partono da zero e avanzano di uno per step.
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
     * Stato iniziale prima della prima esecuzione.
     *
     * <p>Non contiene snapshot, risultato o popolazione precedente. La prima
     * riesecuzione verrà quindi trattata come {@link TriggerReason#FIRST_RUN}.</p>
     *
     * @param startTimeSeconds tempo di partenza della simulazione
     * @param fixedIntervalSeconds intervallo temporale programmato
     * @return stato iniziale del gestore temporale
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
     * Costruisce lo stato successivo partendo dal risultato appena prodotto.
     *
     * <p>Lo stato avanza di una finestra, conserva lo snapshot appena
     * ottimizzato e memorizza la popolazione finale del MA-GA come possibile
     * base di riuso per lo step successivo.</p>
     *
     * @param stepResult risultato della finestra appena eseguita
     * @param nextScheduledTimeSeconds prossima riesecuzione programmata
     * @return nuovo stato del gestore temporale
     */
    public static TemporalWindowState afterStep(
            TemporalStepResult stepResult,
            double nextScheduledTimeSeconds
    ) {
        if (stepResult == null) {
            throw new IllegalArgumentException("stepResult must not be null.");
        }

        return new TemporalWindowState(
                stepResult.getWindowIndex() + 1,
                stepResult.getTrigger().getTriggerTimeSeconds(),
                nextScheduledTimeSeconds,
                stepResult.getSnapshot(),
                stepResult.getMaGaResult(),
                stepResult.getMaGaResult().getFinalPopulation()
        );
    }

    /**
     * @return indice della finestra corrente
     */
    public int getWindowIndex() {
        return windowIndex;
    }

    /**
     * @return tempo simulato corrente
     */
    public double getCurrentTimeSeconds() {
        return currentTimeSeconds;
    }

    /**
     * @return tempo della prossima scadenza programmata
     */
    public double getNextScheduledTimeSeconds() {
        return nextScheduledTimeSeconds;
    }

    /**
     * @return ultimo snapshot ottimizzato, oppure {@code null} nello stato iniziale
     */
    public SystemSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    /**
     * @return ultimo risultato MA-GA, oppure {@code null} nello stato iniziale
     */
    public MaGaResult getLastResult() {
        return lastResult;
    }

    /**
     * @return popolazione finale dell'ultima esecuzione, come lista immutabile
     */
    public List<Chromosome> getLastFinalPopulation() {
        return lastFinalPopulation;
    }

    /**
     * @return true se esiste già una precedente esecuzione MA-GA
     */
    public boolean hasPreviousExecution() {
        return lastSnapshot != null && lastResult != null;
    }

    /**
     * @return true se è disponibile una popolazione finale riutilizzabile
     */
    public boolean hasReusablePopulation() {
        return !lastFinalPopulation.isEmpty();
    }

    /**
     * Valida che un tempo simulato sia finito e non negativo.
     */
    private static void validateFiniteAndNonNegative(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }

    /**
     * Valida che una durata o un intervallo sia finito e strettamente positivo.
     */
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
                + ", lastResult="
                + (lastResult == null ? null : lastResult.getSnapshotId())
                + ", lastFinalPopulationSize=" + lastFinalPopulation.size()
                + '}';
    }
}
