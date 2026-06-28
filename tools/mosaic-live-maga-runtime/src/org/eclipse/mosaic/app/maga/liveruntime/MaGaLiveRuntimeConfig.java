package org.eclipse.mosaic.app.maga.liveruntime;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import config.ga.GaParameterScalingMode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public final class MaGaLiveRuntimeConfig {

    static final String CONFIG_FILE_NAME = "ma_ga_live_runtime_config.json";
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;
    private static final double CONFIGURED_REPLAY_DATA_COLLECTION_DELAY_SECONDS = 0.0;
    private static final double CONFIGURED_REPLAY_STRATEGY_APPLICATION_SECONDS = 0.05;
    private static final double CONFIGURED_REPLAY_EPSILON_SECONDS = 1.0E-6;

    String scenarioName;
    long coordinatorTickIntervalMs;
    long initialOptimizationDelayMs;
    long gaPollingIntervalMs;
    boolean singleInFlightGaOnly;
    boolean discardLateResult;
    boolean keepLastAppliedStrategyWhileRunning;
    boolean freshReoptimizationAfterTimeout;
    boolean runtimeTraceEnabled;
    long diagnosticArtificialGaDelayMs;
    double temporalInitialWindowSeconds;
    double configuredGaRuntimeEstimateSeconds;
    double configuredMaxWindowSeconds;
    double deltaTMaxComparisonEpsilonSeconds;
    int publishedSnapshotCopyLimit;
    boolean nativeLiveDetailedReportingEnabled;
    boolean nativeLiveDetailedReportPrintToConsole;
    String gaParameterScalingMode;
    String experimentalVariant;
    String deltaTMaxMode;
    Double configuredInitialDeltaTMaxSeconds;
    Double adaptiveDeltaTMaxMinimumSeconds;
    Double adaptiveDeltaTMaxMaximumSeconds;
    Integer adaptiveDeltaTMaxHistorySize;
    Integer adaptiveDeltaTMaxWarmupSamples;
    Double adaptiveDeltaTMaxSafetyMarginSeconds;
    Double adaptiveDeltaTMaxMaximumStepUpSeconds;
    Double adaptiveDeltaTMaxMaximumStepDownSeconds;

    public static MaGaLiveRuntimeConfig load(File configurationPath) {
        if (configurationPath == null) {
            throw new IllegalArgumentException("MOSAIC configuration path is null");
        }
        File configFile = new File(configurationPath, CONFIG_FILE_NAME);
        if (!configFile.isFile()) {
            throw new IllegalArgumentException("Missing live runtime config: " + configFile.getAbsolutePath());
        }
        try {
            String json = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            MaGaLiveRuntimeConfig config = new Gson().fromJson(json, MaGaLiveRuntimeConfig.class);
            config.validate(configFile);
            return config;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read " + configFile.getAbsolutePath(), e);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON in " + configFile.getAbsolutePath(), e);
        }
    }

    public long getCoordinatorTickIntervalNs() {
        return coordinatorTickIntervalMs * NANOSECONDS_PER_MILLISECOND;
    }

    public long getInitialOptimizationDelayNs() {
        return initialOptimizationDelayMs * NANOSECONDS_PER_MILLISECOND;
    }

    public long getDiagnosticArtificialGaDelayMs() {
        return diagnosticArtificialGaDelayMs;
    }

    public double getTemporalInitialWindowSeconds() {
        return temporalInitialWindowSeconds;
    }

    public double getConfiguredGaRuntimeEstimateSeconds() {
        return configuredGaRuntimeEstimateSeconds;
    }

    public double getConfiguredMaxWindowSeconds() {
        return configuredMaxWindowSeconds;
    }

    public double getDeltaTMaxComparisonEpsilonSeconds() {
        return deltaTMaxComparisonEpsilonSeconds;
    }

    public int getPublishedSnapshotCopyLimit() {
        return publishedSnapshotCopyLimit;
    }

    public boolean isNativeLiveDetailedReportingEnabled() {
        return nativeLiveDetailedReportingEnabled;
    }

    public boolean isNativeLiveDetailedReportPrintToConsole() {
        return nativeLiveDetailedReportPrintToConsole;
    }

    public GaParameterScalingMode getGaParameterScalingMode() {
        return GaParameterScalingMode.valueOf(gaParameterScalingMode);
    }

    public MaGaExperimentalVariant getExperimentalVariant() {
        return MaGaExperimentalVariant.parse(experimentalVariant, null);
    }

    public LiveDeltaTMaxMode getDeltaTMaxMode() {
        return LiveDeltaTMaxMode.valueOf(deltaTMaxMode);
    }

    public LiveAdaptiveDeltaTMaxEstimator.Config getAdaptiveDeltaTMaxConfig() {
        if (getDeltaTMaxMode() != LiveDeltaTMaxMode.LIVE_ADAPTIVE) {
            throw new IllegalStateException(
                    "Adaptive deltaTMax config is only available in LIVE_ADAPTIVE mode."
            );
        }
        return new LiveAdaptiveDeltaTMaxEstimator.Config(
                configuredInitialDeltaTMaxSeconds,
                adaptiveDeltaTMaxMinimumSeconds,
                adaptiveDeltaTMaxMaximumSeconds,
                adaptiveDeltaTMaxHistorySize,
                adaptiveDeltaTMaxWarmupSamples,
                adaptiveDeltaTMaxSafetyMarginSeconds,
                adaptiveDeltaTMaxMaximumStepUpSeconds,
                adaptiveDeltaTMaxMaximumStepDownSeconds
        );
    }

    public String getScenarioName() {
        return scenarioName == null || scenarioName.isBlank()
                ? "MaGaLiveMagaRuntimeStudy"
                : scenarioName;
    }

    public String profileName() {
        return diagnosticArtificialGaDelayMs > 0 ? "diagnostic-overrun" : "normal";
    }

    private void validate(File configFile) {
        String source = configFile.getAbsolutePath();
        if (scenarioName == null || scenarioName.isBlank()) {
            scenarioName = "MaGaLiveMagaRuntimeStudy";
        }
        if (scenarioName.contains("..")
                || scenarioName.contains("\\")
                || scenarioName.contains("/")
                || !scenarioName.matches("^[A-Za-z0-9_.-]+$")) {
            throw new IllegalArgumentException(source + ": scenarioName is invalid");
        }
        if (gaParameterScalingMode == null || gaParameterScalingMode.isBlank()) {
            gaParameterScalingMode = GaParameterScalingMode.ADAPTIVE.name();
        } else {
            String normalized = gaParameterScalingMode.trim().toUpperCase(Locale.ROOT);
            try {
                gaParameterScalingMode = GaParameterScalingMode.valueOf(normalized).name();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        source + ": gaParameterScalingMode must be STATIC or ADAPTIVE",
                        e
                );
            }
        }
        if (deltaTMaxMode == null || deltaTMaxMode.isBlank()) {
            deltaTMaxMode = LiveDeltaTMaxMode.CONFIGURED_STATIC.name();
        } else {
            String normalized = deltaTMaxMode.trim().toUpperCase(Locale.ROOT);
            try {
                deltaTMaxMode = LiveDeltaTMaxMode.valueOf(normalized).name();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        source + ": deltaTMaxMode must be CONFIGURED_STATIC or LIVE_ADAPTIVE",
                        e
                );
            }
        }
        experimentalVariant = MaGaExperimentalVariant.parse(experimentalVariant, source).name();
        requirePositive(coordinatorTickIntervalMs, "coordinatorTickIntervalMs", source);
        requirePositive(initialOptimizationDelayMs, "initialOptimizationDelayMs", source);
        requirePositive(gaPollingIntervalMs, "gaPollingIntervalMs", source);
        requirePositive(temporalInitialWindowSeconds, "temporalInitialWindowSeconds", source);
        requirePositive(configuredGaRuntimeEstimateSeconds, "configuredGaRuntimeEstimateSeconds", source);
        requirePositive(configuredMaxWindowSeconds, "configuredMaxWindowSeconds", source);
        requirePositive(deltaTMaxComparisonEpsilonSeconds, "deltaTMaxComparisonEpsilonSeconds", source);
        if (publishedSnapshotCopyLimit < 1) {
            throw new IllegalArgumentException(source + ": publishedSnapshotCopyLimit must be >= 1");
        }
        if (!singleInFlightGaOnly) {
            throw new IllegalArgumentException(source + ": singleInFlightGaOnly must be true for Phase 13D");
        }
        if (!discardLateResult) {
            throw new IllegalArgumentException(source + ": discardLateResult must be true for Phase 13D");
        }
        if (!keepLastAppliedStrategyWhileRunning) {
            throw new IllegalArgumentException(source + ": keepLastAppliedStrategyWhileRunning must be true for Phase 13D");
        }
        if (!freshReoptimizationAfterTimeout) {
            throw new IllegalArgumentException(source + ": freshReoptimizationAfterTimeout must be true for Phase 13D");
        }
        if (diagnosticArtificialGaDelayMs < 0) {
            throw new IllegalArgumentException(source + ": diagnosticArtificialGaDelayMs must be >= 0");
        }
        if (getDeltaTMaxMode() == LiveDeltaTMaxMode.LIVE_ADAPTIVE) {
            validateAdaptiveDeltaTMaxConfig(source);
        }
    }

    private void validateAdaptiveDeltaTMaxConfig(String source) {
        requirePresent(configuredInitialDeltaTMaxSeconds,
                "configuredInitialDeltaTMaxSeconds", source);
        requirePresent(adaptiveDeltaTMaxMinimumSeconds,
                "adaptiveDeltaTMaxMinimumSeconds", source);
        requirePresent(adaptiveDeltaTMaxMaximumSeconds,
                "adaptiveDeltaTMaxMaximumSeconds", source);
        requirePresent(adaptiveDeltaTMaxHistorySize,
                "adaptiveDeltaTMaxHistorySize", source);
        requirePresent(adaptiveDeltaTMaxWarmupSamples,
                "adaptiveDeltaTMaxWarmupSamples", source);
        requirePresent(adaptiveDeltaTMaxSafetyMarginSeconds,
                "adaptiveDeltaTMaxSafetyMarginSeconds", source);
        requirePresent(adaptiveDeltaTMaxMaximumStepUpSeconds,
                "adaptiveDeltaTMaxMaximumStepUpSeconds", source);
        requirePresent(adaptiveDeltaTMaxMaximumStepDownSeconds,
                "adaptiveDeltaTMaxMaximumStepDownSeconds", source);

        requirePositive(configuredInitialDeltaTMaxSeconds,
                "configuredInitialDeltaTMaxSeconds", source);
        requireFiniteAndNonNegative(adaptiveDeltaTMaxMinimumSeconds,
                "adaptiveDeltaTMaxMinimumSeconds", source);
        requirePositive(adaptiveDeltaTMaxMaximumSeconds,
                "adaptiveDeltaTMaxMaximumSeconds", source);
        requireFiniteAndNonNegative(adaptiveDeltaTMaxSafetyMarginSeconds,
                "adaptiveDeltaTMaxSafetyMarginSeconds", source);
        requirePositive(adaptiveDeltaTMaxMaximumStepUpSeconds,
                "adaptiveDeltaTMaxMaximumStepUpSeconds", source);
        requirePositive(adaptiveDeltaTMaxMaximumStepDownSeconds,
                "adaptiveDeltaTMaxMaximumStepDownSeconds", source);

        if (adaptiveDeltaTMaxHistorySize < 1) {
            throw new IllegalArgumentException(
                    source + ": adaptiveDeltaTMaxHistorySize must be >= 1"
            );
        }
        if (adaptiveDeltaTMaxWarmupSamples < 1
                || adaptiveDeltaTMaxWarmupSamples > adaptiveDeltaTMaxHistorySize) {
            throw new IllegalArgumentException(
                    source + ": adaptiveDeltaTMaxWarmupSamples must be in [1, adaptiveDeltaTMaxHistorySize]"
            );
        }
        if (adaptiveDeltaTMaxMinimumSeconds > adaptiveDeltaTMaxMaximumSeconds) {
            throw new IllegalArgumentException(
                    source
                            + ": BOUND_CONFLICT adaptiveDeltaTMaxMinimumSeconds exceeds adaptiveDeltaTMaxMaximumSeconds"
                            + " | adaptiveDeltaTMaxMinimumSeconds="
                            + adaptiveDeltaTMaxMinimumSeconds
                            + " | adaptiveDeltaTMaxMaximumSeconds="
                            + adaptiveDeltaTMaxMaximumSeconds
            );
        }
        if (configuredInitialDeltaTMaxSeconds < adaptiveDeltaTMaxMinimumSeconds) {
            throw new IllegalArgumentException(
                    source + ": configuredInitialDeltaTMaxSeconds must be >= adaptiveDeltaTMaxMinimumSeconds"
            );
        }
        if (configuredInitialDeltaTMaxSeconds > adaptiveDeltaTMaxMaximumSeconds) {
            throw new IllegalArgumentException(
                    source + ": configuredInitialDeltaTMaxSeconds must be <= adaptiveDeltaTMaxMaximumSeconds"
            );
        }
        double configuredTemporalMinimumSeconds =
                CONFIGURED_REPLAY_DATA_COLLECTION_DELAY_SECONDS
                        + configuredGaRuntimeEstimateSeconds
                        + CONFIGURED_REPLAY_STRATEGY_APPLICATION_SECONDS
                        + CONFIGURED_REPLAY_EPSILON_SECONDS;
        double effectiveMinimumSeconds = Math.max(
                configuredTemporalMinimumSeconds,
                adaptiveDeltaTMaxMinimumSeconds
        );
        if (effectiveMinimumSeconds > adaptiveDeltaTMaxMaximumSeconds) {
            throw new IllegalArgumentException(
                    source
                            + ": BOUND_CONFLICT effective minimum exceeds adaptive maximum"
                            + " | configuredTemporalMinimumSeconds="
                            + configuredTemporalMinimumSeconds
                            + " | adaptiveDeltaTMaxMinimumSeconds="
                            + adaptiveDeltaTMaxMinimumSeconds
                            + " | effectiveMinimumSeconds="
                            + effectiveMinimumSeconds
                            + " | adaptiveDeltaTMaxMaximumSeconds="
                            + adaptiveDeltaTMaxMaximumSeconds
            );
        }
    }

    private static void requirePresent(Object value, String field, String source) {
        if (value == null) {
            throw new IllegalArgumentException(source + ": " + field + " is required when deltaTMaxMode is LIVE_ADAPTIVE");
        }
    }

    private static void requirePositive(long value, String field, String source) {
        if (value <= 0) {
            throw new IllegalArgumentException(source + ": " + field + " must be > 0");
        }
    }

    private static void requirePositive(double value, String field, String source) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(source + ": " + field + " must be finite and > 0");
        }
    }

    private static void requireFiniteAndNonNegative(double value, String field, String source) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(source + ": " + field + " must be finite and >= 0");
        }
    }
}
