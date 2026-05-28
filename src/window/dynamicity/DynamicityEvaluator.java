package window.dynamicity;

import config.window.TemporalWindowConfig;
import model.snapshot.SystemSnapshot;
import window.dynamicity.calculator.LinkDynamicityCalculator;
import window.dynamicity.calculator.ResourceDynamicityCalculator;
import window.dynamicity.calculator.TaskDynamicityCalculator;
import window.dynamicity.calculator.VehicleDynamicityCalculator;
import window.population.PopulationReuseMode;

import java.util.Objects;

/**
 * Orchestratore della valutazione di dinamicità tra due snapshot consecutivi.
 *
 * Questa classe rappresenta la formula globale della formalizzazione:
 *
 * D(k) = lambdaVehicles * Dv(k)
 *      + lambdaTasks * Dt(k)
 *      + lambdaResources * Dr(k)
 *      + lambdaLinks * Dl(k)
 *
 * I dettagli delle singole componenti sono delegati ai calculator dedicati.
 */
public final class DynamicityEvaluator {

    private final TemporalWindowConfig config;

    private final VehicleDynamicityCalculator vehicleCalculator;
    private final TaskDynamicityCalculator taskCalculator;
    private final ResourceDynamicityCalculator resourceCalculator;
    private final LinkDynamicityCalculator linkCalculator;

    /**
     * Costruisce il valutatore con i calculator di default.
     *
     * @param config configurazione temporale
     */
    public DynamicityEvaluator(TemporalWindowConfig config) {
        this(
                config,
                new VehicleDynamicityCalculator(),
                new TaskDynamicityCalculator(),
                new ResourceDynamicityCalculator(),
                new LinkDynamicityCalculator()
        );
    }

    /**
     * Costruisce il valutatore con calculator espliciti.
     *
     * Utile per test o sostituzioni future di singole componenti.
     *
     * @param config configurazione temporale
     * @param vehicleCalculator calcolatore Dv(k)
     * @param taskCalculator calcolatore Dt(k)
     * @param resourceCalculator calcolatore Dr(k)
     * @param linkCalculator calcolatore Dl(k)
     */
    public DynamicityEvaluator(
            TemporalWindowConfig config,
            VehicleDynamicityCalculator vehicleCalculator,
            TaskDynamicityCalculator taskCalculator,
            ResourceDynamicityCalculator resourceCalculator,
            LinkDynamicityCalculator linkCalculator
    ) {
        this.config = Objects.requireNonNull(
                config,
                "config must not be null."
        );

        this.vehicleCalculator = Objects.requireNonNull(
                vehicleCalculator,
                "vehicleCalculator must not be null."
        );

        this.taskCalculator = Objects.requireNonNull(
                taskCalculator,
                "taskCalculator must not be null."
        );

        this.resourceCalculator = Objects.requireNonNull(
                resourceCalculator,
                "resourceCalculator must not be null."
        );

        this.linkCalculator = Objects.requireNonNull(
                linkCalculator,
                "linkCalculator must not be null."
        );
    }

    /**
     * Confronta due snapshot e produce il breakdown completo.
     *
     * @param previousSnapshot snapshot precedente, nullo solo alla prima finestra
     * @param currentSnapshot snapshot corrente
     * @return breakdown della dinamicità
     */
    public DynamicityBreakdown evaluate(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        Objects.requireNonNull(
                currentSnapshot,
                "currentSnapshot must not be null."
        );

        if (previousSnapshot == null) {
            return DynamicityBreakdown.firstRun(
                    currentSnapshot.getSnapshotId(),
                    currentSnapshot.getTimeSeconds()
            );
        }

        double vehicleVariation = vehicleCalculator.compute(
                previousSnapshot,
                currentSnapshot
        );

        double taskVariation = taskCalculator.compute(
                previousSnapshot,
                currentSnapshot
        );

        double resourceVariation = resourceCalculator.compute(
                previousSnapshot,
                currentSnapshot
        );

        double linkVariation = linkCalculator.compute(
                previousSnapshot,
                currentSnapshot
        );

        double globalDynamicity = computeGlobalDynamicity(
                vehicleVariation,
                taskVariation,
                resourceVariation,
                linkVariation
        );

        DynamicityLevel level = classify(globalDynamicity);
        PopulationReuseMode reuseMode = level.toReuseMode();

        return new DynamicityBreakdown(
                previousSnapshot.getSnapshotId(),
                currentSnapshot.getSnapshotId(),
                previousSnapshot.getTimeSeconds(),
                currentSnapshot.getTimeSeconds(),
                vehicleVariation,
                taskVariation,
                resourceVariation,
                linkVariation,
                globalDynamicity,
                level,
                reuseMode
        );
    }

    /**
     * Combina le componenti tramite i lambda normalizzati della config.
     */
    private double computeGlobalDynamicity(
            double vehicleVariation,
            double taskVariation,
            double resourceVariation,
            double linkVariation
    ) {
        double value =
                config.getNormalizedLambdaVehicles() * vehicleVariation
                        + config.getNormalizedLambdaTasks() * taskVariation
                        + config.getNormalizedLambdaResources() * resourceVariation
                        + config.getNormalizedLambdaLinks() * linkVariation;

        return window.dynamicity.math.DynamicityMath.clamp01(value);
    }

    /**
     * Classifica l'indice globale usando thetaLow/thetaHigh.
     */
    private DynamicityLevel classify(double globalDynamicity) {
        if (globalDynamicity < config.getThetaLow()) {
            return DynamicityLevel.STABLE;
        }

        if (globalDynamicity <= config.getThetaHigh()) {
            return DynamicityLevel.MODERATE;
        }

        return DynamicityLevel.HIGH;
    }
}
