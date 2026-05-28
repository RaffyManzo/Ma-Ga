package config;

import config.fitness.FitnessWeights;
import config.fitness.NormalizationConfig;
import config.fitness.PenaltyConfig;
import config.ga.GeneticAlgorithmConfig;
import config.mobility.MobilityConfig;

import java.util.Objects;

/**
 * Configurazione complessiva del Mobility-Aware Genetic Algorithm.
 *
 * Aggrega le configurazioni necessarie al GA:
 * - pesi della fitness;
 * - penalità;
 * - normalizzazione;
 * - parametri evolutivi;
 * - parametri di mobilità/copertura.
 */
public final class MaGaConfig {

    private final FitnessWeights fitnessWeights;
    private final PenaltyConfig penaltyConfig;
    private final NormalizationConfig normalizationConfig;
    private final GeneticAlgorithmConfig geneticAlgorithmConfig;
    private final MobilityConfig mobilityConfig;

    /**
     * Costruttore compatibile con la versione precedente.
     *
     * Usa MobilityConfig.defaultConfig().
     */
    public MaGaConfig(
            FitnessWeights fitnessWeights,
            PenaltyConfig penaltyConfig,
            NormalizationConfig normalizationConfig,
            GeneticAlgorithmConfig geneticAlgorithmConfig
    ) {
        this(
                fitnessWeights,
                penaltyConfig,
                normalizationConfig,
                geneticAlgorithmConfig,
                MobilityConfig.defaultConfig()
        );
    }

    /**
     * Costruisce la configurazione completa del MA-GA.
     *
     * @param fitnessWeights pesi della funzione obiettivo
     * @param penaltyConfig configurazione delle penalità
     * @param normalizationConfig riferimenti di normalizzazione
     * @param geneticAlgorithmConfig parametri evolutivi del GA
     * @param mobilityConfig parametri per stimare copertura e mobilità
     */
    public MaGaConfig(
            FitnessWeights fitnessWeights,
            PenaltyConfig penaltyConfig,
            NormalizationConfig normalizationConfig,
            GeneticAlgorithmConfig geneticAlgorithmConfig,
            MobilityConfig mobilityConfig
    ) {
        this.fitnessWeights = Objects.requireNonNull(
                fitnessWeights,
                "fitnessWeights must not be null."
        );

        this.penaltyConfig = Objects.requireNonNull(
                penaltyConfig,
                "penaltyConfig must not be null."
        );

        this.normalizationConfig = Objects.requireNonNull(
                normalizationConfig,
                "normalizationConfig must not be null."
        );

        this.geneticAlgorithmConfig = Objects.requireNonNull(
                geneticAlgorithmConfig,
                "geneticAlgorithmConfig must not be null."
        );

        this.mobilityConfig = Objects.requireNonNull(
                mobilityConfig,
                "mobilityConfig must not be null."
        );
    }

    /**
     * Configurazione iniziale del prototipo.
     *
     * @return configurazione MA-GA completa
     */
    public static MaGaConfig defaultConfig() {
        return new MaGaConfig(
                FitnessWeights.defaultWeights(),
                PenaltyConfig.defaultConfig(),
                NormalizationConfig.neutral(),
                GeneticAlgorithmConfig.defaultConfig(),
                MobilityConfig.defaultConfig()
        );
    }

    public FitnessWeights getFitnessWeights() {
        return fitnessWeights;
    }

    public PenaltyConfig getPenaltyConfig() {
        return penaltyConfig;
    }

    public NormalizationConfig getNormalizationConfig() {
        return normalizationConfig;
    }

    public GeneticAlgorithmConfig getGeneticAlgorithmConfig() {
        return geneticAlgorithmConfig;
    }

    public MobilityConfig getMobilityConfig() {
        return mobilityConfig;
    }

    @Override
    public String toString() {
        return "MaGaConfig{"
                + "fitnessWeights=" + fitnessWeights
                + ", penaltyConfig=" + penaltyConfig
                + ", normalizationConfig=" + normalizationConfig
                + ", geneticAlgorithmConfig=" + geneticAlgorithmConfig
                + ", mobilityConfig=" + mobilityConfig
                + '}';
    }
}