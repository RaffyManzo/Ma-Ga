package window.state;

import ga.core.MaGaResult;
import model.snapshot.SystemSnapshot;
import window.dynamicity.DynamicityBreakdown;
import window.population.PopulationReuseDecision;
import window.population.PopulationReuseMode;
import window.population.WindowPerformanceSignal;
import window.source.SystemStateObservation;
import window.timing.AdaptiveWindowDecision;
import window.timing.TemporalOperationalMetrics;
import window.timing.TemporalWindowBounds;
import window.trigger.ReoptimizationTrigger;

import java.util.Objects;
import java.util.Optional;

/**
 * Risultato di una singola finestra temporale.
 *
 * <p>Il risultato conserva due tempi diversi:</p>
 *
 * <ul>
 *     <li>tempo logico della finestra, deciso dal TemporalWindowManager;</li>
 *     <li>tempo sorgente dello snapshot, salvato nel SystemSnapshot.</li>
 * </ul>
 *
 * <p>Questa separazione è necessaria perché nei test JSON sequenziali il
 * manager può avanzare con finestre adattive, mentre gli snapshot restano una
 * sequenza di fotografie salvate su disco.</p>
 */
public final class TemporalStepResult {

    private static final double EPSILON = 1.0E-6;

    private final int windowIndex;
    private final ReoptimizationTrigger trigger;
    private final double dataCollectionDelaySeconds;
    private final double logicalObservationTimeSeconds;
    private final SystemSnapshot snapshot;
    private final SystemStateObservation systemStateObservation;
    private final DynamicityBreakdown dynamicityBreakdown;
    private final PopulationReuseDecision populationReuseDecision;
    private final PopulationReuseMode reuseMode;
    private final AdaptiveWindowDecision adaptiveWindowDecision;
    private final TemporalOperationalMetrics operationalMetrics;
    private final int initialPopulationSize;
    private final int finalPopulationSize;
    private final MaGaResult maGaResult;

    public TemporalStepResult(
            int windowIndex,
            ReoptimizationTrigger trigger,
            double dataCollectionDelaySeconds,
            double logicalObservationTimeSeconds,
            SystemSnapshot snapshot,
            SystemStateObservation systemStateObservation,
            DynamicityBreakdown dynamicityBreakdown,
            PopulationReuseDecision populationReuseDecision,
            AdaptiveWindowDecision adaptiveWindowDecision,
            TemporalOperationalMetrics operationalMetrics,
            int initialPopulationSize,
            int finalPopulationSize,
            MaGaResult maGaResult
    ) {
        if (windowIndex < 0) {
            throw new IllegalArgumentException("windowIndex must be >= 0.");
        }
        validateFiniteAndNonNegative(
                "dataCollectionDelaySeconds",
                dataCollectionDelaySeconds
        );
        validateFiniteAndNonNegative(
                "logicalObservationTimeSeconds",
                logicalObservationTimeSeconds
        );
        if (initialPopulationSize < 0) {
            throw new IllegalArgumentException(
                    "initialPopulationSize must be >= 0."
            );
        }
        if (finalPopulationSize < 0) {
            throw new IllegalArgumentException(
                    "finalPopulationSize must be >= 0."
            );
        }

        this.windowIndex = windowIndex;
        this.trigger = Objects.requireNonNull(trigger, "trigger must not be null.");
        this.dataCollectionDelaySeconds = dataCollectionDelaySeconds;
        this.logicalObservationTimeSeconds = logicalObservationTimeSeconds;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null.");
        this.systemStateObservation = systemStateObservation;
        this.dynamicityBreakdown = Objects.requireNonNull(
                dynamicityBreakdown,
                "dynamicityBreakdown must not be null."
        );
        this.populationReuseDecision = Objects.requireNonNull(
                populationReuseDecision,
                "populationReuseDecision must not be null."
        );
        this.reuseMode = populationReuseDecision.getAppliedMode();
        this.adaptiveWindowDecision = Objects.requireNonNull(
                adaptiveWindowDecision,
                "adaptiveWindowDecision must not be null."
        );
        this.operationalMetrics = Objects.requireNonNull(
                operationalMetrics,
                "operationalMetrics must not be null."
        );
        this.initialPopulationSize = initialPopulationSize;
        this.finalPopulationSize = finalPopulationSize;
        this.maGaResult = Objects.requireNonNull(maGaResult, "maGaResult must not be null.");

        validateObservationConsistency(
                trigger,
                logicalObservationTimeSeconds,
                dataCollectionDelaySeconds
        );
        validateSnapshotConsistency(snapshot, maGaResult);
        validateSourceObservationConsistency(
                snapshot,
                systemStateObservation,
                logicalObservationTimeSeconds
        );
    }

    public TemporalStepResult(
            int windowIndex,
            ReoptimizationTrigger trigger,
            double dataCollectionDelaySeconds,
            double logicalObservationTimeSeconds,
            SystemSnapshot snapshot,
            DynamicityBreakdown dynamicityBreakdown,
            PopulationReuseDecision populationReuseDecision,
            AdaptiveWindowDecision adaptiveWindowDecision,
            TemporalOperationalMetrics operationalMetrics,
            int initialPopulationSize,
            int finalPopulationSize,
            MaGaResult maGaResult
    ) {
        this(
                windowIndex,
                trigger,
                dataCollectionDelaySeconds,
                logicalObservationTimeSeconds,
                snapshot,
                null,
                dynamicityBreakdown,
                populationReuseDecision,
                adaptiveWindowDecision,
                operationalMetrics,
                initialPopulationSize,
                finalPopulationSize,
                maGaResult
        );
    }

    /**
     * Costruttore compatibile con la versione precedente.
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
        this(
                windowIndex,
                trigger,
                dataCollectionDelaySeconds,
                observationTimeSeconds,
                snapshot,
                null,
                dynamicityBreakdown,
                new PopulationReuseDecision(
                        dynamicityBreakdown.getSuggestedReuseMode(),
                        reuseMode,
                        WindowPerformanceSignal.UNKNOWN,
                        false,
                        false,
                        "Legacy constructor."
                ),
                AdaptiveWindowDecision.fixed(
                        Math.max(1.0, trigger.getTriggerTimeSeconds()),
                        new TemporalWindowBounds(
                                1.0,
                                Math.max(1.0, trigger.getTriggerTimeSeconds()),
                                0.0,
                                false
                        ),
                        dynamicityBreakdown.getDynamicityLevel(),
                        "Legacy constructor."
                ),
                TemporalOperationalMetrics.estimated(
                        dataCollectionDelaySeconds,
                        0.1,
                        0.0,
                        1.0E-6
                ),
                initialPopulationSize,
                finalPopulationSize,
                maGaResult
        );
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

    /**
     * Tempo logico/adattivo della finestra.
     *
     * <p>Il nome resta compatibile con i printer esistenti. Da ora questo valore
     * non deve essere interpretato come tempo interno del JSON.</p>
     */
    public double getObservationTimeSeconds() {
        return logicalObservationTimeSeconds;
    }

    public double getLogicalObservationTimeSeconds() {
        return logicalObservationTimeSeconds;
    }

    public double getSourceObservationTimeSeconds() {
        return systemStateObservation == null
                ? snapshot.getTimeSeconds()
                : systemStateObservation.getSourceObservationTimeSeconds();
    }

    public double getSnapshotTimeSeconds() {
        return snapshot.getTimeSeconds();
    }

    public SystemSnapshot getSnapshot() {
        return snapshot;
    }

    public Optional<SystemStateObservation> getSystemStateObservation() {
        return Optional.ofNullable(systemStateObservation);
    }

    public DynamicityBreakdown getDynamicityBreakdown() {
        return dynamicityBreakdown;
    }

    public PopulationReuseDecision getPopulationReuseDecision() {
        return populationReuseDecision;
    }

    public PopulationReuseMode getReuseMode() {
        return reuseMode;
    }

    public AdaptiveWindowDecision getAdaptiveWindowDecision() {
        return adaptiveWindowDecision;
    }

    public TemporalOperationalMetrics getOperationalMetrics() {
        return operationalMetrics;
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
            double logicalObservationTimeSeconds,
            double dataCollectionDelaySeconds
    ) {
        double expectedObservationTime = trigger.getTriggerTimeSeconds()
                + dataCollectionDelaySeconds;

        if (Math.abs(expectedObservationTime - logicalObservationTimeSeconds) > EPSILON) {
            throw new IllegalArgumentException(
                    "logicalObservationTimeSeconds must be triggerTimeSeconds + dataCollectionDelaySeconds."
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

    private static void validateSourceObservationConsistency(
            SystemSnapshot snapshot,
            SystemStateObservation observation,
            double logicalObservationTimeSeconds
    ) {
        if (observation == null) {
            return;
        }
        if (!snapshot.getSnapshotId().equals(observation.getSnapshot().getSnapshotId())) {
            throw new IllegalArgumentException(
                    "snapshot and systemStateObservation must refer to the same snapshotId."
            );
        }
        if (Math.abs(observation.getRequestedObservationTimeSeconds()
                - logicalObservationTimeSeconds) > EPSILON) {
            throw new IllegalArgumentException(
                    "observation requested time must match logicalObservationTimeSeconds."
            );
        }
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

    @Override
    public String toString() {
        return "TemporalStepResult{" +
                "windowIndex=" + windowIndex +
                ", triggerTimeSeconds=" + getTriggerTimeSeconds() +
                ", logicalObservationTimeSeconds=" + logicalObservationTimeSeconds +
                ", sourceObservationTimeSeconds=" + getSourceObservationTimeSeconds() +
                ", snapshotId='" + snapshot.getSnapshotId() + '\'' +
                ", dynamicity=" + dynamicityBreakdown.getGlobalDynamicity() +
                ", reuseMode=" + reuseMode +
                ", nextWindow=" + adaptiveWindowDecision.getNextWindowSeconds() +
                ", finalBestFitness=" + maGaResult.getFinalBestFitness() +
                '}';
    }
}
