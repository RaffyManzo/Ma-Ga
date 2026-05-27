package config;

import config.fitness.FitnessWeights;
import config.fitness.NormalizationConfig;
import config.fitness.PenaltyConfig;
import config.ga.GeneticAlgorithmConfig;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.Objects;

/**
 * Configurazione complessiva del Mobility-Aware Genetic Algorithm.
 *
 * Questa classe aggrega:
 *
 * - FitnessWeights: pesi wT, wL, wM, wR della funzione obiettivo;
 * - PenaltyConfig: coefficienti usati per costruire Pmob(C), Pres(C) e penalità di deadline;
 * - NormalizationConfig: riferimenti usati per normalizzare i termini della fitness;
 * - GeneticAlgorithmConfig: parametri evolutivi del GA.
 *
 * Non contiene:
 *
 * - SystemSnapshot;
 * - VehicleSnapshot;
 * - TaskInstance;
 * - NodeCandidate;
 * - Gene;
 * - Chromosome;
 * - logica di valutazione;
 * - logica di mutazione, crossover o selezione;
 * - logica di caricamento JSON;
 * - logica MOSAIC/SUMO.
 */
public final class MaGaConfig {

    private final FitnessWeights fitnessWeights;
    private final PenaltyConfig penaltyConfig;
    private final NormalizationConfig normalizationConfig;
    private final GeneticAlgorithmConfig geneticAlgorithmConfig;

    public MaGaConfig(
            FitnessWeights fitnessWeights,
            PenaltyConfig penaltyConfig,
            NormalizationConfig normalizationConfig,
            GeneticAlgorithmConfig geneticAlgorithmConfig
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
    }

    public static MaGaConfig defaultConfig() {
        return new MaGaConfig(
                FitnessWeights.defaultWeights(),
                PenaltyConfig.defaultConfig(),
                NormalizationConfig.neutral(),
                GeneticAlgorithmConfig.defaultConfig()
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

    @Override
    public String toString() {
        return "MaGaConfig{" +
                "fitnessWeights=" + fitnessWeights +
                ", penaltyConfig=" + penaltyConfig +
                ", normalizationConfig=" + normalizationConfig +
                ", geneticAlgorithmConfig=" + geneticAlgorithmConfig +
                '}';
    }
}

