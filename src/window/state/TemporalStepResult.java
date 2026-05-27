package window.state;

import ga.core.MaGaResult;
import model.snapshot.SystemSnapshot;
import window.dynamicity.DynamicityBreakdown;
import window.population.PopulationReuseMode;
import window.trigger.ReoptimizationTrigger;

import java.util.Objects;

/**
 * Risultato di una singola finestra temporale.
 *
 * Conserva trigger, tempo di osservazione, snapshot usato, modalità di riuso
 * e risultato MA-GA.
 */
public final class TemporalStepResult {

    private final int windowIndex;
    private final ReoptimizationTrigger trigger;

    private final double dataCollectionDelaySeconds;
    private final double observationTimeSeconds;

    private final SystemSnapshot snapshot;
    private final DynamicityBreakdown dynamicityBreakdown;
    private final PopulationReuseMode reuseMode;

    private final int initialPopulationSize;
    private final int finalPopulationSize;

    private final MaGaResult maGaResult;

    /**
     * Crea il risultato di una finestra temporale.
     *
     * @param windowIndex indice della finestra
     * @param trigger causa della riesecuzione
     * @param dataCollectionDelaySeconds ritardo di raccolta dati
     * @param observationTimeSeconds tempo richiesto per lo snapshot
     * @param snapshot snapshot ottimizzato
     * @param dynamicityBreakdown dinamicità calcolata
     * @param reuseMode modalità di riuso applicata
     * @param initialPopulationSize dimensione popolazione iniziale
     * @param finalPopulationSize dimensione popolazione finale
     * @param maGaResult risultato MA-GA
     */
    public TemporalStepResult(
            int windowIndex,
            ReoptimizationTrigger trigger,
            double dataCollectionDelaySeconds,
            double observationTimeSeconds,
            SystemSnapshot snapshot,
            DynamicityBreakdown dynamicityBreakdown,
            PopulationReuseMode reuseMode,
            int initialPopulationSize,
            int finalPopulationSize,
            MaGaResult maGaResult
    ) {
        if (windowIndex < 0) {
            throw new IllegalArgumentException("windowIndex must be >= 0.");
        }

        validateFiniteAndNonNegative("dataCollectionDelaySeconds", dataCollectionDelaySeconds);
        validateFiniteAndNonNegative("observationTimeSeconds", observationTimeSeconds);

        if (initialPopulationSize < 0) {
            throw new IllegalArgumentException("initialPopulationSize must be >= 0.");
        }

        if (finalPopulationSize < 0) {
            throw new IllegalArgumentException("finalPopulationSize must be >= 0.");
        }

        this.windowIndex = windowIndex;
        this.trigger = Objects.requireNonNull(trigger, "trigger must not be null.");
        this.dataCollectionDelaySeconds = dataCollectionDelaySeconds;
        this.observationTimeSeconds = observationTimeSeconds;

        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null.");
        this.dynamicityBreakdown = Objects.requireNonNull(
                dynamicityBreakdown,
                "dynamicityBreakdown must not be null."
        );
        this.reuseMode = Objects.requireNonNull(reuseMode, "reuseMode must not be null.");
        this.maGaResult = Objects.requireNonNull(maGaResult, "maGaResult must not be null.");

        this.initialPopulationSize = initialPopulationSize;
        this.finalPopulationSize = finalPopulationSize;

        validateObservationConsistency(trigger, observationTimeSeconds, dataCollectionDelaySeconds);
        validateSnapshotConsistency(snapshot, maGaResult);
        validateSnapshotObservationTime(snapshot, observationTimeSeconds);
        validateReuseModeConsistency(dynamicityBreakdown, reuseMode);
    }

    public int getWindowIndex() {
        return windowIndex;
    }

    public ReoptimizationTrigger getTrigger() {
        return trigger;
    }

    public double getDataCollectionDelaySeconds() {
        return dataCollectionDelaySeconds;
    }

    public double getObservationTimeSeconds() {
        return observationTimeSeconds;
    }

    public SystemSnapshot getSnapshot() {
        return snapshot;
    }

    public DynamicityBreakdown getDynamicityBreakdown() {
        return dynamicityBreakdown;
    }

    public PopulationReuseMode getReuseMode() {
        return reuseMode;
    }

    public int getInitialPopulationSize() {
        return initialPopulationSize;
    }

    public int getFinalPopulationSize() {
        return finalPopulationSize;
    }

    public MaGaResult getMaGaResult() {
        return maGaResult;
    }

    public double getTriggerTimeSeconds() {
        return trigger.getTriggerTimeSeconds();
    }

    public boolean wasTriggeredByCriticalEvent() {
        return trigger.isCriticalEventTrigger();
    }

    public boolean reusedPreviousPopulation() {
        return reuseMode.usesPreviousPopulation();
    }

    private static void validateObservationConsistency(
            ReoptimizationTrigger trigger,
            double observationTimeSeconds,
            double dataCollectionDelaySeconds
    ) {
        double expectedObservationTime =
                trigger.getTriggerTimeSeconds() + dataCollectionDelaySeconds;

        if (Math.abs(expectedObservationTime - observationTimeSeconds) > 1.0E-6) {
            throw new IllegalArgumentException(
                    "observationTimeSeconds must be triggerTimeSeconds + dataCollectionDelaySeconds."
            );
        }
    }

    private static void validateSnapshotConsistency(
            SystemSnapshot snapshot,
            MaGaResult maGaResult
    ) {
        if (!snapshot.getSnapshotId().equals(maGaResult.getSnapshotId())) {
            throw new IllegalArgumentException(
                    "snapshot and maGaResult must refer to the same snapshotId."
            );
        }
    }

    private static void validateSnapshotObservationTime(
            SystemSnapshot snapshot,
            double observationTimeSeconds
    ) {
        if (Math.abs(snapshot.getTimeSeconds() - observationTimeSeconds) > 1.0E-6) {
            throw new IllegalArgumentException(
                    "snapshot.timeSeconds must match observationTimeSeconds."
            );
        }
    }

    private static void validateReuseModeConsistency(
            DynamicityBreakdown dynamicityBreakdown,
            PopulationReuseMode reuseMode
    ) {
        if (dynamicityBreakdown.getSuggestedReuseMode() != reuseMode) {
            throw new IllegalArgumentException(
                    "reuseMode must match dynamicityBreakdown.suggestedReuseMode."
            );
        }
    }

    private static void validateFiniteAndNonNegative(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }

    @Override
    public String toString() {
        return "TemporalStepResult{"
                + "windowIndex=" + windowIndex
                + ", triggerTimeSeconds=" + getTriggerTimeSeconds()
                + ", dataCollectionDelaySeconds=" + dataCollectionDelaySeconds
                + ", observationTimeSeconds=" + observationTimeSeconds
                + ", snapshotId='" + snapshot.getSnapshotId() + '\''
                + ", snapshotTimeSeconds=" + snapshot.getTimeSeconds()
                + ", dynamicity=" + dynamicityBreakdown.getGlobalDynamicity()
                + ", reuseMode=" + reuseMode
                + ", finalBestFitness=" + maGaResult.getFinalBestFitness()
                + '}';
    }
}