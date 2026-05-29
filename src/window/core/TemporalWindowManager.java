package window.core;

import config.window.TemporalWindowConfig;
import ga.core.MaGaOptimizer;
import ga.core.MaGaResult;
import model.genetic.Chromosome;
import model.snapshot.SystemSnapshot;
import window.dynamicity.DynamicityBreakdown;
import window.dynamicity.DynamicityEvaluator;
import window.event.CriticalEventDetector;
import window.population.PopulationAdapter;
import window.population.PopulationReuseDecision;
import window.population.PopulationReuseDecisionPolicy;
import window.population.PopulationReuseMode;
import window.provider.SystemStateProvider;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;
import window.state.TemporalWindowState;
import window.trigger.ReoptimizationTrigger;

import java.util.List;
import java.util.Objects;

/**
 * Orchestratore del ciclo temporale del MA-GA.
 *
 * <p>Coordina trigger, raccolta snapshot, dinamicità, riuso popolazione ed
 * esecuzione del MA-GA.</p>
 *
 * <p>Nota metodologica: la dinamicità resta calcolata da
 * {@link DynamicityEvaluator}. La modalità finale di riuso viene però filtrata
 * da {@link PopulationReuseDecisionPolicy}, così il sistema non resta bloccato
 * su {@code PARTIAL_RESTART} quando i report suggeriscono warm/cold start.</p>
 */
public final class TemporalWindowManager {

    private final TemporalWindowConfig windowConfig;
    private final MaGaOptimizer optimizer;
    private final DynamicityEvaluator dynamicityEvaluator;
    private final PopulationAdapter populationAdapter;
    private final PopulationReuseDecisionPolicy reuseDecisionPolicy;
    private final CriticalEventDetector criticalEventDetector;
    private final SystemStateProvider systemStateProvider;
    private final int targetPopulationSize;

    /**
     * Crea un gestore temporale.
     *
     * @param windowConfig configurazione della finestra
     * @param optimizer ottimizzatore MA-GA
     * @param dynamicityEvaluator valutatore della dinamicità
     * @param populationAdapter adattatore della popolazione
     * @param criticalEventDetector detector eventi critici
     * @param systemStateProvider provider degli snapshot
     * @param targetPopulationSize dimensione popolazione richiesta
     */
    public TemporalWindowManager(
            TemporalWindowConfig windowConfig,
            MaGaOptimizer optimizer,
            DynamicityEvaluator dynamicityEvaluator,
            PopulationAdapter populationAdapter,
            CriticalEventDetector criticalEventDetector,
            SystemStateProvider systemStateProvider,
            int targetPopulationSize
    ) {
        this.windowConfig = Objects.requireNonNull(
                windowConfig,
                "windowConfig must not be null."
        );
        this.optimizer = Objects.requireNonNull(
                optimizer,
                "optimizer must not be null."
        );
        this.dynamicityEvaluator = Objects.requireNonNull(
                dynamicityEvaluator,
                "dynamicityEvaluator must not be null."
        );
        this.populationAdapter = Objects.requireNonNull(
                populationAdapter,
                "populationAdapter must not be null."
        );
        this.criticalEventDetector = Objects.requireNonNull(
                criticalEventDetector,
                "criticalEventDetector must not be null."
        );
        this.systemStateProvider = Objects.requireNonNull(
                systemStateProvider,
                "systemStateProvider must not be null."
        );

        if (targetPopulationSize < 1) {
            throw new IllegalArgumentException("targetPopulationSize must be >= 1.");
        }

        this.targetPopulationSize = targetPopulationSize;
        this.reuseDecisionPolicy = new PopulationReuseDecisionPolicy(this.windowConfig);
    }

    /**
     * Esegue una sequenza di finestre temporali.
     *
     * @param startTimeSeconds tempo iniziale
     * @param maxSteps numero massimo di step
     * @return risultato aggregato
     */
    public TemporalWindowResult run(
            double startTimeSeconds,
            int maxSteps
    ) {
        validateFiniteAndNonNegative("startTimeSeconds", startTimeSeconds);

        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be >= 1.");
        }

        TemporalWindowState state = TemporalWindowState.initial(
                startTimeSeconds,
                windowConfig.getFixedIntervalSeconds()
        );

        TemporalWindowResult result = TemporalWindowResult.empty();

        for (int i = 0; i < maxSteps; i++) {
            TemporalStepResult stepResult = executeNextStepOrNull(state);

            if (stepResult == null) {
                break;
            }

            result = result.append(stepResult);

            state = TemporalWindowState.afterStep(
                    stepResult,
                    windowConfig.getFixedIntervalSeconds()
            );
        }

        return result;
    }

    /**
     * Esegue il prossimo step temporale.
     *
     * @param state stato corrente del gestore
     * @return risultato dello step, oppure null se non esiste snapshot osservabile
     */
    public TemporalStepResult executeNextStepOrNull(TemporalWindowState state) {
        Objects.requireNonNull(state, "state must not be null.");

        ReoptimizationTrigger trigger = resolveTrigger(state);
        double observationTimeSeconds = computeObservationTime(trigger);

        SystemSnapshot currentSnapshot = systemStateProvider
                .findSnapshotAt(observationTimeSeconds)
                .orElse(null);

        if (currentSnapshot == null) {
            return null;
        }

        DynamicityBreakdown dynamicityBreakdown = dynamicityEvaluator.evaluate(
                state.getLastSnapshot(),
                currentSnapshot
        );

        PopulationReuseDecision reuseDecision = reuseDecisionPolicy.decide(
                dynamicityBreakdown,
                state.getLastResult(),
                state.hasReusablePopulation(),
                trigger.isCriticalEventTrigger()
        );

        PopulationReuseMode reuseMode = reuseDecision.getAppliedReuseMode();

        List<Chromosome> initialPopulation = populationAdapter.adaptPopulation(
                state.getLastFinalPopulation(),
                currentSnapshot,
                reuseMode,
                targetPopulationSize
        );

        MaGaResult maGaResult = optimizer.optimizeDetailed(
                currentSnapshot,
                initialPopulation
        );

        return new TemporalStepResult(
                state.getWindowIndex(),
                trigger,
                windowConfig.getDataCollectionDelaySeconds(),
                observationTimeSeconds,
                currentSnapshot,
                dynamicityBreakdown,
                reuseMode,
                initialPopulation.size(),
                maGaResult.getFinalPopulation().size(),
                maGaResult,
                reuseDecision
        );
    }

    /**
     * Determina il trigger della prossima riesecuzione.
     */
    private ReoptimizationTrigger resolveTrigger(TemporalWindowState state) {
        if (!state.hasPreviousExecution()) {
            return ReoptimizationTrigger.firstRun(
                    state.getCurrentTimeSeconds()
            );
        }

        double currentTimeSeconds = state.getCurrentTimeSeconds();
        double scheduledTimeSeconds = state.getNextScheduledTimeSeconds();

        return criticalEventDetector
                .findNextCriticalEvent(currentTimeSeconds, scheduledTimeSeconds)
                .map(ReoptimizationTrigger::criticalEvent)
                .orElseGet(
                        () -> ReoptimizationTrigger.scheduledExpiration(scheduledTimeSeconds)
                );
    }

    /**
     * Calcola il tempo dello snapshot da osservare.
     */
    private double computeObservationTime(ReoptimizationTrigger trigger) {
        return trigger.getTriggerTimeSeconds()
                + windowConfig.getDataCollectionDelaySeconds();
    }

    public TemporalWindowConfig getWindowConfig() {
        return windowConfig;
    }

    public int getTargetPopulationSize() {
        return targetPopulationSize;
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
}
