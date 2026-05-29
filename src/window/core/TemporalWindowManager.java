package window.core;

import config.mobility.MobilityConfig;
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
 * <p>Coordina trigger, raccolta snapshot, dinamicità, riuso popolazione,
 * finestra adattiva ed esecuzione del MA-GA.</p>
 */
public final class TemporalWindowManager {

    private final TemporalWindowConfig windowConfig;
    private final MaGaOptimizer optimizer;
    private final DynamicityEvaluator dynamicityEvaluator;
    private final PopulationAdapter populationAdapter;
    private final PopulationReuseDecisionPolicy reuseDecisionPolicy;
    private final AdaptiveWindowController adaptiveWindowController;
    private final CriticalEventDetector criticalEventDetector;
    private final SystemStateProvider systemStateProvider;
    private final int targetPopulationSize;

    public TemporalWindowManager(
            TemporalWindowConfig windowConfig,
            MaGaOptimizer optimizer,
            DynamicityEvaluator dynamicityEvaluator,
            PopulationAdapter populationAdapter,
            CriticalEventDetector criticalEventDetector,
            SystemStateProvider systemStateProvider,
            int targetPopulationSize
    ) {
        this(
                windowConfig,
                optimizer,
                dynamicityEvaluator,
                populationAdapter,
                new PopulationReuseDecisionPolicy(),
                defaultAdaptiveWindowController(windowConfig, MobilityConfig.defaultConfig()),
                criticalEventDetector,
                systemStateProvider,
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
        this.systemStateProvider = Objects.requireNonNull(
                systemStateProvider,
                "systemStateProvider must not be null."
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
                new TemporalWindowBoundsCalculator(
                        config,
                        coverageReferenceCalculator
                );
        return new AdaptiveWindowController(config, boundsCalculator);
    }

    public TemporalWindowResult run(
            double startTimeSeconds,
            int maxSteps
    ) {
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
        Objects.requireNonNull(state, "state must not be null.");

        ReoptimizationTrigger plannedTrigger = resolveTrigger(state);
        double requestedObservationTimeSeconds = computeObservationTime(plannedTrigger);

        SystemSnapshot currentSnapshot = systemStateProvider
                .findSnapshotAtOrAfter(requestedObservationTimeSeconds)
                .orElse(null);

        if (currentSnapshot == null) {
            return null;
        }

        ReoptimizationTrigger effectiveTrigger = alignTriggerToSnapshotTime(
                plannedTrigger,
                currentSnapshot
        );

        double observationTimeSeconds = computeObservationTime(effectiveTrigger);

        DynamicityBreakdown dynamicityBreakdown = dynamicityEvaluator.evaluate(
                state.getLastSnapshot(),
                currentSnapshot
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
                        currentSnapshot,
                        metricsForDecision
                );

        PopulationReuseMode reuseMode = reuseDecision.getAppliedMode();

        List<Chromosome> initialPopulation = populationAdapter.adaptPopulation(
                state.getLastFinalPopulation(),
                currentSnapshot,
                reuseMode,
                targetPopulationSize
        );

        long startNs = System.nanoTime();
        MaGaResult maGaResult = optimizer.optimizeDetailed(
                currentSnapshot,
                initialPopulation
        );
        long elapsedNs = System.nanoTime() - startNs;

        TemporalOperationalMetrics observedMetrics = observedOperationalMetrics(
                elapsedNs
        );

        return new TemporalStepResult(
                state.getWindowIndex(),
                effectiveTrigger,
                windowConfig.getDataCollectionDelaySeconds(),
                observationTimeSeconds,
                currentSnapshot,
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
                        () -> ReoptimizationTrigger.scheduledExpiration(
                                scheduledTimeSeconds
                        )
                );
    }

    private ReoptimizationTrigger alignTriggerToSnapshotTime(
            ReoptimizationTrigger plannedTrigger,
            SystemSnapshot snapshot
    ) {
        double alignedTriggerTime = snapshot.getTimeSeconds()
                - windowConfig.getDataCollectionDelaySeconds();

        if (Math.abs(plannedTrigger.getTriggerTimeSeconds() - alignedTriggerTime)
                <= 1.0E-6) {
            return plannedTrigger;
        }

        if (plannedTrigger.isFirstRun()) {
            return ReoptimizationTrigger.firstRun(alignedTriggerTime);
        }

        if (plannedTrigger.isCriticalEventTrigger()) {
            return plannedTrigger;
        }

        return ReoptimizationTrigger.scheduledExpiration(alignedTriggerTime);
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
        double gaRuntimeSeconds = Math.max(0.0, elapsedNs / 1_000_000_000.0);
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
