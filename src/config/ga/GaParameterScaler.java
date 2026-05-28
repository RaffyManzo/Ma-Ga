package config.ga;

import model.snapshot.SystemSnapshot;

import java.util.Objects;

/**
 * Scala i parametri evolutivi del MA-GA in base alla complessità
 * dello snapshot osservato.
 *
 * La classe ha due modalità:
 *
 * - STATIC: restituisce la configurazione GA di base senza modificarla;
 * - ADAPTIVE: aumenta popolazione e generazioni in base a task e candidati.
 *
 * Questa classe non appartiene al cromosoma e non modifica lo snapshot.
 * Agisce prima dell'esecuzione del MaGaOptimizer.
 */
public final class GaParameterScaler {

    private final GaParameterScalingConfig scalingConfig;

    /**
     * Costruisce lo scaler.
     *
     * @param scalingConfig configurazione dello scaling
     */
    public GaParameterScaler(GaParameterScalingConfig scalingConfig) {
        this.scalingConfig = Objects.requireNonNull(
                scalingConfig,
                "scalingConfig must not be null."
        );
    }

    /**
     * Crea uno scaler in modalità statica.
     *
     * @return scaler statico
     */
    public static GaParameterScaler staticScaler() {
        return new GaParameterScaler(
                GaParameterScalingConfig.staticMode()
        );
    }

    /**
     * Crea uno scaler adattivo con configurazione iniziale.
     *
     * @return scaler adattivo
     */
    public static GaParameterScaler adaptiveDefault() {
        return new GaParameterScaler(
                GaParameterScalingConfig.adaptiveDefault()
        );
    }

    /**
     * Scala la configurazione GA, restituendo solo la config finale.
     *
     * @param snapshot snapshot osservato
     * @param baseConfig configurazione GA di partenza
     * @return configurazione GA da usare
     */
    public GeneticAlgorithmConfig scale(
            SystemSnapshot snapshot,
            GeneticAlgorithmConfig baseConfig
    ) {
        return scaleDetailed(snapshot, baseConfig).getScaledConfig();
    }

    /**
     * Scala la configurazione GA e restituisce anche informazioni diagnostiche.
     *
     * @param snapshot snapshot osservato
     * @param baseConfig configurazione GA di partenza
     * @return risultato diagnostico dello scaling
     */
    public GaParameterScalingResult scaleDetailed(
            SystemSnapshot snapshot,
            GeneticAlgorithmConfig baseConfig
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(baseConfig, "baseConfig must not be null.");

        int vehicleCount = snapshot.getVehicles().size();
        int activeTaskCount = snapshot.getTasks().size();
        int candidateCount = snapshot.getCandidateNodes().size();

        double averageCandidatesPerTask = activeTaskCount == 0
                ? 0.0
                : (double) candidateCount / activeTaskCount;

        if (scalingConfig.getMode() == GaParameterScalingMode.STATIC) {
            return new GaParameterScalingResult(
                    GaParameterScalingMode.STATIC,
                    vehicleCount,
                    activeTaskCount,
                    candidateCount,
                    averageCandidatesPerTask,
                    baseConfig,
                    baseConfig,
                    "STATIC mode: base GeneticAlgorithmConfig returned unchanged."
            );
        }

        GeneticAlgorithmConfig scaledConfig = buildAdaptiveConfig(
                activeTaskCount,
                candidateCount,
                baseConfig
        );

        return new GaParameterScalingResult(
                GaParameterScalingMode.ADAPTIVE,
                vehicleCount,
                activeTaskCount,
                candidateCount,
                averageCandidatesPerTask,
                baseConfig,
                scaledConfig,
                "ADAPTIVE mode: populationSize and maxGenerations scaled from active tasks and candidate count."
        );
    }

    /**
     * Costruisce la configurazione adattiva.
     */
    private GeneticAlgorithmConfig buildAdaptiveConfig(
            int activeTaskCount,
            int candidateCount,
            GeneticAlgorithmConfig baseConfig
    ) {
        int populationSize = computePopulationSize(
                activeTaskCount,
                candidateCount,
                baseConfig
        );

        int maxGenerations = computeMaxGenerations(
                activeTaskCount,
                candidateCount,
                baseConfig
        );

        int elitismCount = computeElitismCount(
                populationSize,
                baseConfig
        );

        int stallGenerations = computeStallGenerations(
                maxGenerations,
                baseConfig
        );

        return new GeneticAlgorithmConfig(
                populationSize,
                maxGenerations,
                baseConfig.getCrossoverRate(),
                baseConfig.getMutationRate(),
                elitismCount,
                stallGenerations,
                baseConfig.getFitnessImprovementEpsilon(),
                baseConfig.getRandomSeed()
        );
    }

    /**
     * Calcola la dimensione della popolazione.
     */
    private int computePopulationSize(
            int activeTaskCount,
            int candidateCount,
            GeneticAlgorithmConfig baseConfig
    ) {
        int proposed = baseConfig.getPopulationSize()
                + activeTaskCount * scalingConfig.getPopulationPerTask()
                + (int) Math.ceil(
                candidateCount * scalingConfig.getPopulationPerCandidate()
        );

        return clamp(
                proposed,
                scalingConfig.getMinPopulationSize(),
                scalingConfig.getMaxPopulationSize()
        );
    }

    /**
     * Calcola il numero massimo di generazioni.
     */
    private int computeMaxGenerations(
            int activeTaskCount,
            int candidateCount,
            GeneticAlgorithmConfig baseConfig
    ) {
        int proposed = baseConfig.getMaxGenerations()
                + activeTaskCount * scalingConfig.getGenerationsPerTask()
                + (int) Math.ceil(
                candidateCount * scalingConfig.getGenerationsPerCandidate()
        );

        return clamp(
                proposed,
                scalingConfig.getMinMaxGenerations(),
                scalingConfig.getMaxMaxGenerations()
        );
    }

    /**
     * Calcola quanti individui elitari conservare.
     */
    private int computeElitismCount(
            int populationSize,
            GeneticAlgorithmConfig baseConfig
    ) {
        int proposed = Math.max(
                baseConfig.getElitismCount(),
                (int) Math.round(
                        populationSize * scalingConfig.getElitismRate()
                )
        );

        proposed = Math.max(
                scalingConfig.getMinElitismCount(),
                proposed
        );

        return Math.min(
                proposed,
                populationSize - 1
        );
    }

    /**
     * Calcola la soglia di stagnazione in generazioni.
     */
    private int computeStallGenerations(
            int maxGenerations,
            GeneticAlgorithmConfig baseConfig
    ) {
        int proposed = Math.max(
                baseConfig.getStallGenerations(),
                (int) Math.round(
                        maxGenerations
                                * scalingConfig.getStallGenerationRate()
                )
        );

        proposed = Math.max(
                scalingConfig.getMinStallGenerations(),
                proposed
        );

        return Math.min(
                proposed,
                maxGenerations
        );
    }

    private int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(min, Math.min(max, value));
    }
}