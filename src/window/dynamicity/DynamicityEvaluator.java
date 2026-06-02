package window.dynamicity;

import config.mobility.MobilityConfig;
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
 * <p>Rappresenta la formula globale:</p>
 *
 * <pre>
 * D(k) = lambdaVehicles  * Dv(k)
 *      + lambdaTasks     * Dt(k)
 *      + lambdaResources * Dr(k)
 *      + lambdaLinks     * Dl(k)
 * </pre>
 */
public final class DynamicityEvaluator {
    private final TemporalWindowConfig config;
    private final VehicleDynamicityCalculator vehicleCalculator;
    private final TaskDynamicityCalculator taskCalculator;
    private final ResourceDynamicityCalculator resourceCalculator;
    private final LinkDynamicityCalculator linkCalculator;

    /**
     * Costruttore storico. Usa MobilityConfig.defaultConfig().
     * Preferire l'overload gateway-aware nel wiring applicativo.
     */
    public DynamicityEvaluator(TemporalWindowConfig config) {
        this(config, MobilityConfig.defaultConfig());
    }

    /**
     * Costruisce il valutatore usando la stessa configurazione di mobilità del
     * GA e del calcolo di copertura.
     *
     * @param config configurazione della finestra temporale
     * @param mobilityConfig configurazione condivisa di mobilità
     */
    public DynamicityEvaluator(
            TemporalWindowConfig config,
            MobilityConfig mobilityConfig
    ) {
        this(
                config,
                new VehicleDynamicityCalculator(),
                new TaskDynamicityCalculator(),
                new ResourceDynamicityCalculator(),
                new LinkDynamicityCalculator(mobilityConfig)
        );
    }

    /**
     * Costruttore con calculator espliciti. Utile per test mirati.
     */
    public DynamicityEvaluator(
            TemporalWindowConfig config,
            VehicleDynamicityCalculator vehicleCalculator,
            TaskDynamicityCalculator taskCalculator,
            ResourceDynamicityCalculator resourceCalculator,
            LinkDynamicityCalculator linkCalculator
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
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

    /** Confronta due snapshot e produce il breakdown completo. */
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

    private double computeGlobalDynamicity(
            double vehicleVariation,
            double taskVariation,
            double resourceVariation,
            double linkVariation
    ) {
        double value = config.getNormalizedLambdaVehicles() * vehicleVariation
                + config.getNormalizedLambdaTasks() * taskVariation
                + config.getNormalizedLambdaResources() * resourceVariation
                + config.getNormalizedLambdaLinks() * linkVariation;
        return window.dynamicity.math.DynamicityMath.clamp01(value);
    }

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
