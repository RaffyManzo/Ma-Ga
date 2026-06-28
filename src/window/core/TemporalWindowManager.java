package window.core;

import config.mobility.MobilityConfig;
import config.window.TemporalWindowConfig;
import ga.core.GaExecutionBudget;
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
import window.source.SystemStateObservation;
import window.source.SystemStateRequest;
import window.source.SystemStateSource;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;
import window.state.TemporalWindowState;
import window.timing.AdaptiveWindowController;
import window.timing.AdaptiveWindowDecision;
import window.timing.CoverageReferenceCalculator;
import window.timing.TemporalOperationalMetrics;
import window.timing.TemporalWindowBoundsCalculator;
import window.trigger.ReoptimizationTrigger;

import java.util.List;
import java.util.Objects;

/**
 * Orchestratore del ciclo temporale del MA-GA.
 *
 * <p>Il manager distingue lo snapshot fisico osservato dallo snapshot filtrato
 * destinato all'ottimizzazione. Dinamicità e bounds temporali devono descrivere
 * lo scenario reale. Population adapter e GA lavorano invece sulla vista
 * ridotta dal prefilter.</p>
 */
public final class TemporalWindowManager {

    private final TemporalWindowConfig windowConfig;
    private final MaGaOptimizer optimizer;
    private final DynamicityEvaluator dynamicityEvaluator;
    private final PopulationAdapter populationAdapter;
    private final PopulationReuseDecisionPolicy reuseDecisionPolicy;
    private final AdaptiveWindowController adaptiveWindowController;
    private final CriticalEventDetector criticalEventDetector;
    private final SystemStateSource systemStateSource;
    private final int targetPopulationSize;

    public TemporalWindowManager(
            TemporalWindowConfig windowConfig,
            MaGaOptimizer optimizer,
            DynamicityEvaluator dynamicityEvaluator,
            PopulationAdapter populationAdapter,
            CriticalEventDetector criticalEventDetector,
            SystemStateSource systemStateSource,
            int targetPopulationSize
    ) {
        this(
                windowConfig,
                optimizer,
                dynamicityEvaluator,
                populationAdapter,
                new PopulationReuseDecisionPolicy(windowConfig),
                defaultAdaptiveWindowController(
                        windowConfig,
                        optimizerMobilityConfig(optimizer)
                ),
                criticalEventDetector,
                systemStateSource,
                targetPopulationSize
        );
    }

    public TemporalWindowManager(
            TemporalWindowConfig windowConfig,
            MaGaOptimizer optimizer,
            DynamicityEvaluator dynamicityEvaluator,
            PopulationAdapter populationAdapter,
            PopulationReuseDecisionPolicy reuseDecisionPolicy,
            CriticalEventDetector criticalEventDetector,
            SystemStateSource systemStateSource,
            int targetPopulationSize
    ) {
        this(
                windowConfig,
                optimizer,
                dynamicityEvaluator,
                populationAdapter,
                reuseDecisionPolicy,
                defaultAdaptiveWindowController(
                        windowConfig,
                        optimizerMobilityConfig(optimizer)
                ),
                criticalEventDetector,
                systemStateSource,
                targetPopulationSize
        );
    }

    public TemporalWindowManager(
            TemporalWindowConfig windowConfig,
            MaGaOptimizer optimizer,
            DynamicityEvaluator dynamicityEvaluator,
            PopulationAdapter populationAdapter,
            PopulationReuseDecisionPolicy reuseDecisionPolicy,
            AdaptiveWindowController adaptiveWindowController,
            CriticalEventDetector criticalEventDetector,
            SystemStateSource systemStateSource,
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
        this.reuseDecisionPolicy = Objects.requireNonNull(
                reuseDecisionPolicy,
                "reuseDecisionPolicy must not be null."
        );
        this.adaptiveWindowController = Objects.requireNonNull(
                adaptiveWindowController,
                "adaptiveWindowController must not be null."
        );
        this.criticalEventDetector = Objects.requireNonNull(
                criticalEventDetector,
                "criticalEventDetector must not be null."
        );
        this.systemStateSource = Objects.requireNonNull(
                systemStateSource,
                "systemStateSource must not be null."
        );
        if (targetPopulationSize < 1) {
            throw new IllegalArgumentException("targetPopulationSize must be >= 1.");
        }
        this.targetPopulationSize = targetPopulationSize;
    }

    private static AdaptiveWindowController defaultAdaptiveWindowController(
            TemporalWindowConfig config,
            MobilityConfig mobilityConfig
    ) {
        CoverageReferenceCalculator coverageReferenceCalculator =
                new CoverageReferenceCalculator(mobilityConfig);
        TemporalWindowBoundsCalculator boundsCalculator =
                new TemporalWindowBoundsCalculator(config, coverageReferenceCalculator);
        return new AdaptiveWindowController(config, boundsCalculator);
    }

    private static MobilityConfig optimizerMobilityConfig(MaGaOptimizer optimizer) {
        return Objects.requireNonNull(
                optimizer,
                "optimizer must not be null."
        ).getMobilityConfig();
    }

    public TemporalWindowResult run(double startTimeSeconds, int maxSteps) {
        validateFiniteAndNonNegative("startTimeSeconds", startTimeSeconds);
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be >= 1.");
        }

        TemporalOperationalMetrics initialMetrics = initialOperationalMetrics();
        TemporalWindowState state = TemporalWindowState.initial(
                startTimeSeconds,
                windowConfig.getInitialWindowSeconds(),
                initialMetrics
        );
        TemporalWindowResult result = TemporalWindowResult.empty();

        for (int i = 0; i < maxSteps; i++) {
            TemporalStepResult stepResult = executeNextStepOrNull(state);
            if (stepResult == null) {
                break;
            }
            result = result.append(stepResult);
            state = TemporalWindowState.afterStep(stepResult);
        }
        return result;
    }

    public TemporalStepResult executeNextStepOrNull(TemporalWindowState state) {
        return executeNextStepOrNull(state, GaExecutionBudget.unlimited());
    }

    public TemporalStepResult executeNextStepOrNull(
            TemporalWindowState state,
            GaExecutionBudget executionBudget
    ) {
        Objects.requireNonNull(state, "state must not be null.");
        Objects.requireNonNull(
                executionBudget,
                "executionBudget must not be null."
        );

        ReoptimizationTrigger plannedTrigger = resolveTrigger(state);
        double requestedObservationTimeSeconds = computeObservationTime(plannedTrigger);
        SystemStateRequest request = new SystemStateRequest(
                state.getWindowIndex(),
                plannedTrigger,
                requestedObservationTimeSeconds,
                state.getCurrentWindowDurationSeconds()
        );
        SystemStateObservation observation = systemStateSource
                .nextObservation(request)
                .orElse(null);
        if (observation == null) {
            return null;
        }

        SystemSnapshot observedSnapshot = observation.getObservedSnapshot();
        SystemSnapshot optimizationSnapshot = observation.getOptimizationSnapshot();
        ReoptimizationTrigger effectiveTrigger = plannedTrigger;
        double observationTimeSeconds = request.getRequestedObservationTimeSeconds();

        // La dinamicità e i bounds devono descrivere il sistema fisico osservato,
        // non la vista ridotta dal prefilter per accelerare il GA.
        DynamicityBreakdown dynamicityBreakdown = dynamicityEvaluator.evaluate(
                state.getLastSnapshot(),
                observedSnapshot
        );
        PopulationReuseDecision reuseDecision = reuseDecisionPolicy.decide(
                dynamicityBreakdown,
                state.getLastResult(),
                state.hasReusablePopulation(),
                effectiveTrigger.isCriticalEventTrigger()
        );
        TemporalOperationalMetrics metricsForDecision = metricsForDecision(state);
        AdaptiveWindowDecision adaptiveWindowDecision =
                adaptiveWindowController.decideNextWindow(
                        state.getCurrentWindowDurationSeconds(),
                        dynamicityBreakdown,
                        observedSnapshot,
                        metricsForDecision
                );

        PopulationReuseMode reuseMode = reuseDecision.getAppliedMode();
        List<Chromosome> initialPopulation = populationAdapter.adaptPopulation(
                state.getLastFinalPopulation(),
                optimizationSnapshot,
                reuseMode,
                targetPopulationSize
        );

        long startNs = System.nanoTime();
        MaGaResult maGaResult = optimizer.optimizeDetailed(
                optimizationSnapshot,
                initialPopulation,
                executionBudget
        );
        long elapsedNs = System.nanoTime() - startNs;
        TemporalOperationalMetrics observedMetrics = observedOperationalMetrics(elapsedNs);

        // TemporalStepResult conserva lo snapshot grezzo: lo stato della finestra
        // successiva deve confrontare osservazioni fisiche, non candidati filtrati.
        return new TemporalStepResult(
                state.getWindowIndex(),
                effectiveTrigger,
                windowConfig.getDataCollectionDelaySeconds(),
                observationTimeSeconds,
                observedSnapshot,
                observation,
                dynamicityBreakdown,
                reuseDecision,
                adaptiveWindowDecision,
                observedMetrics,
                initialPopulation.size(),
                maGaResult.getFinalPopulation().size(),
                maGaResult
        );
    }

    private ReoptimizationTrigger resolveTrigger(TemporalWindowState state) {
        if (!state.hasPreviousExecution()) {
            return ReoptimizationTrigger.firstRun(state.getCurrentTimeSeconds());
        }
        double currentTimeSeconds = state.getCurrentTimeSeconds();
        double scheduledTimeSeconds = state.getNextScheduledTimeSeconds();
        return criticalEventDetector
                .findNextCriticalEvent(currentTimeSeconds, scheduledTimeSeconds)
                .map(ReoptimizationTrigger::criticalEvent)
                .orElseGet(() -> ReoptimizationTrigger.scheduledExpiration(
                        scheduledTimeSeconds
                ));
    }

    private double computeObservationTime(ReoptimizationTrigger trigger) {
        return trigger.getTriggerTimeSeconds()
                + windowConfig.getDataCollectionDelaySeconds();
    }

    private TemporalOperationalMetrics initialOperationalMetrics() {
        return TemporalOperationalMetrics.estimated(
                windowConfig.getDataCollectionDelaySeconds(),
                windowConfig.getDefaultGaRuntimeEstimateSeconds(),
                windowConfig.getStrategyApplicationSeconds(),
                windowConfig.getEpsilonT()
        );
    }

    private TemporalOperationalMetrics metricsForDecision(TemporalWindowState state) {
        if (state.getLastOperationalMetrics() != null) {
            return state.getLastOperationalMetrics();
        }
        return initialOperationalMetrics();
    }

    private TemporalOperationalMetrics observedOperationalMetrics(long elapsedNs) {
        double gaRuntimeSeconds = Math.max(
                0.0,
                elapsedNs / 1_000_000_000.0
        );
        return TemporalOperationalMetrics.observed(
                windowConfig.getDataCollectionDelaySeconds(),
                gaRuntimeSeconds,
                windowConfig.getStrategyApplicationSeconds(),
                windowConfig.getEpsilonT()
        );
    }

    public TemporalWindowConfig getWindowConfig() {
        return windowConfig;
    }

    public int getTargetPopulationSize() {
        return targetPopulationSize;
    }

    public SystemStateSource getSystemStateSource() {
        return systemStateSource;
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
