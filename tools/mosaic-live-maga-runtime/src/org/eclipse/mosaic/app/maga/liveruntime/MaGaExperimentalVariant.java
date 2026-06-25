package org.eclipse.mosaic.app.maga.liveruntime;

import config.MaGaConfig;
import config.fitness.FitnessWeights;

import java.util.Locale;

public enum MaGaExperimentalVariant {
    FULL_MA_GA,
    LOCAL_ONLY,
    NO_MOBILITY_PENALTY,
    COLD_START_NO_REUSE;

    public static MaGaExperimentalVariant parse(String value, String source) {
        if (value == null || value.isBlank()) {
            return FULL_MA_GA;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return MaGaExperimentalVariant.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            String prefix = source == null || source.isBlank() ? "" : source + ": ";
            throw new IllegalArgumentException(
                    prefix + "experimentalVariant must be one of FULL_MA_GA, LOCAL_ONLY, "
                            + "NO_MOBILITY_PENALTY, COLD_START_NO_REUSE",
                    e
            );
        }
    }

    public MaGaConfig applyTo(MaGaConfig standardConfig) {
        if (standardConfig == null) {
            throw new IllegalArgumentException("standardConfig must not be null.");
        }
        if (this != NO_MOBILITY_PENALTY) {
            return standardConfig;
        }

        FitnessWeights original = standardConfig.getFitnessWeights();
        FitnessWeights adjusted = FitnessWeights.normalized(
                original.getCompletionTimeWeight(),
                original.getCommunicationLatencyWeight(),
                0.0,
                original.getResourcePenaltyWeight()
        );
        return new MaGaConfig(
                adjusted,
                standardConfig.getPenaltyConfig(),
                standardConfig.getNormalizationConfig(),
                standardConfig.getGeneticAlgorithmConfig(),
                standardConfig.getMobilityConfig(),
                standardConfig.getGaParameterScalingMode()
        );
    }
}
