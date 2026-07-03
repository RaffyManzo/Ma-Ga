package org.eclipse.mosaic.app.maga.liveruntime;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import config.ga.GaParameterScalingMode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/** Runtime configuration with V3-C temporal-domain separation. */
public final class MaGaLiveRuntimeConfig {

    static final String CONFIG_FILE_NAME = "ma_ga_live_runtime_config.json";
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

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
    Boolean advancedMobilityDiagnosticsEnabled;
    Integer advancedDiagnosticsFlushBatchSize;
    String advancedDiagnosticsRunId;
    Long advancedDiagnosticsSeed;
    String gaParameterScalingMode;
    String experimentalVariant;

    // Canonical V3-C names.
    String gaWallClockBudgetMode;
    Double configuredInitialGaWallClockBudgetSeconds;
    Double adaptiveGaWallClockBudgetMinimumSeconds;
    Double adaptiveGaWallClockBudgetMaximumSeconds;
    Integer adaptiveGaWallClockBudgetHistorySize;
    Integer adaptiveGaWallClockBudgetWarmupSamples;
    Double adaptiveGaWallClockBudgetSafetyMarginSeconds;
    Double adaptiveGaWallClockBudgetMaximumStepUpSeconds;
    Double adaptiveGaWallClockBudgetMaximumStepDownSeconds;
    Double maxSnapshotAgeSimulationSeconds;
    Boolean cooperativeGaBudgetStopEnabled;

    // V3-B aliases, accepted only for backward compatibility.
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
            throw new IllegalArgumentException(
                    "Missing live runtime config: " + configFile.getAbsolutePath()
            );
        }
        try {
            String json = new String(
                    Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8
            );
            MaGaLiveRuntimeConfig config = new Gson().fromJson(
                    json, MaGaLiveRuntimeConfig.class
            );
            config.validate(configFile);
            return config;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Unable to read " + configFile.getAbsolutePath(), e
            );
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException(
                    "Invalid JSON in " + configFile.getAbsolutePath(), e
            );
        }
    }

    public long getCoordinatorTickIntervalNs() {
        return coordinatorTickIntervalMs * NANOSECONDS_PER_MILLISECOND;
    }
    public long getInitialOptimizationDelayNs() {
        return initialOptimizationDelayMs * NANOSECONDS_PER_MILLISECOND;
    }
    public long getDiagnosticArtificialGaDelayMs() { return diagnosticArtificialGaDelayMs; }
    public double getTemporalInitialWindowSeconds() { return temporalInitialWindowSeconds; }
    public double getConfiguredGaRuntimeEstimateSeconds() { return configuredGaRuntimeEstimateSeconds; }
    public double getConfiguredMaxWindowSeconds() { return configuredMaxWindowSeconds; }
    public double getDeltaTMaxComparisonEpsilonSeconds() { return deltaTMaxComparisonEpsilonSeconds; }
    public int getPublishedSnapshotCopyLimit() { return publishedSnapshotCopyLimit; }
    public boolean isNativeLiveDetailedReportingEnabled() { return nativeLiveDetailedReportingEnabled; }
    public boolean isNativeLiveDetailedReportPrintToConsole() { return nativeLiveDetailedReportPrintToConsole; }
    public boolean isAdvancedMobilityDiagnosticsEnabled() {
        return Boolean.TRUE.equals(advancedMobilityDiagnosticsEnabled);
    }
    public int getAdvancedDiagnosticsFlushBatchSize() {
        return advancedDiagnosticsFlushBatchSize;
    }
    public String getAdvancedDiagnosticsRunId() {
        return advancedDiagnosticsRunId;
    }
    public Long getAdvancedDiagnosticsSeed() {
        return advancedDiagnosticsSeed;
    }
    public boolean isCooperativeGaBudgetStopEnabled() { return cooperativeGaBudgetStopEnabled; }
    public double getMaxSnapshotAgeSimulationSeconds() { return maxSnapshotAgeSimulationSeconds; }
    public double getConfiguredInitialGaWallClockBudgetSeconds() {
        return configuredInitialGaWallClockBudgetSeconds;
    }
    /**
     * Reserve used to stop the cooperative search before the hard wall-clock
     * deadline, leaving the configured safety margin for final repair,
     * detailed evaluation and result publication.
     */
    public double getCooperativeGaFinalizationReserveSeconds() {
        return adaptiveGaWallClockBudgetSafetyMarginSeconds;
    }
    public GaParameterScalingMode getGaParameterScalingMode() {
        return GaParameterScalingMode.valueOf(gaParameterScalingMode);
    }
    public MaGaExperimentalVariant getExperimentalVariant() {
        return MaGaExperimentalVariant.parse(experimentalVariant, null);
    }
    public LiveDeltaTMaxMode getGaWallClockBudgetMode() {
        return LiveDeltaTMaxMode.valueOf(gaWallClockBudgetMode);
    }
    /** Legacy getter. */
    public LiveDeltaTMaxMode getDeltaTMaxMode() { return getGaWallClockBudgetMode(); }

    public LiveAdaptiveDeltaTMaxEstimator.Config getAdaptiveGaWallClockBudgetConfig() {
        if (getGaWallClockBudgetMode() != LiveDeltaTMaxMode.LIVE_ADAPTIVE) {
            throw new IllegalStateException(
                    "Adaptive wall-clock budget config requires LIVE_ADAPTIVE mode."
            );
        }
        return new LiveAdaptiveDeltaTMaxEstimator.Config(
                configuredInitialGaWallClockBudgetSeconds,
                adaptiveGaWallClockBudgetMinimumSeconds,
                adaptiveGaWallClockBudgetMaximumSeconds,
                adaptiveGaWallClockBudgetHistorySize,
                adaptiveGaWallClockBudgetWarmupSamples,
                adaptiveGaWallClockBudgetSafetyMarginSeconds,
                adaptiveGaWallClockBudgetMaximumStepUpSeconds,
                adaptiveGaWallClockBudgetMaximumStepDownSeconds
        );
    }
    /** Legacy getter. */
    public LiveAdaptiveDeltaTMaxEstimator.Config getAdaptiveDeltaTMaxConfig() {
        return getAdaptiveGaWallClockBudgetConfig();
    }

    public String getScenarioName() {
        return scenarioName == null || scenarioName.isBlank()
                ? "MaGaLiveMagaRuntimeStudy" : scenarioName;
    }
    public String profileName() {
        return diagnosticArtificialGaDelayMs > 0 ? "diagnostic-overrun" : "normal";
    }

    private void validate(File configFile) {
        String source = configFile.getAbsolutePath();
        normalizeCore(source);
        normalizeV3CAliases(source);

        requirePositive(coordinatorTickIntervalMs, "coordinatorTickIntervalMs", source);
        requirePositive(initialOptimizationDelayMs, "initialOptimizationDelayMs", source);
        requirePositive(gaPollingIntervalMs, "gaPollingIntervalMs", source);
        requirePositive(temporalInitialWindowSeconds, "temporalInitialWindowSeconds", source);
        requirePositive(configuredGaRuntimeEstimateSeconds, "configuredGaRuntimeEstimateSeconds", source);
        requirePositive(configuredMaxWindowSeconds, "configuredMaxWindowSeconds", source);
        requirePositive(deltaTMaxComparisonEpsilonSeconds, "deltaTMaxComparisonEpsilonSeconds", source);
        requirePositive(maxSnapshotAgeSimulationSeconds, "maxSnapshotAgeSimulationSeconds", source);
        requirePositive(configuredInitialGaWallClockBudgetSeconds,
                "configuredInitialGaWallClockBudgetSeconds", source);
        requireFiniteAndNonNegative(adaptiveGaWallClockBudgetSafetyMarginSeconds,
                "adaptiveGaWallClockBudgetSafetyMarginSeconds", source);

        if (publishedSnapshotCopyLimit < 1) {
            throw new IllegalArgumentException(
                    source + ": publishedSnapshotCopyLimit must be >= 1"
            );
        }
        if (advancedDiagnosticsFlushBatchSize < 1
                || advancedDiagnosticsFlushBatchSize > 4096) {
            throw new IllegalArgumentException(
                    source + ": advancedDiagnosticsFlushBatchSize must be in [1,4096]"
            );
        }
        if (advancedDiagnosticsSeed != null && advancedDiagnosticsSeed < 0L) {
            throw new IllegalArgumentException(
                    source + ": advancedDiagnosticsSeed must be >= 0 when provided"
            );
        }
        if (!singleInFlightGaOnly || !discardLateResult
                || !keepLastAppliedStrategyWhileRunning
                || !freshReoptimizationAfterTimeout) {
            throw new IllegalArgumentException(
                    source + ": V3-C requires single-flight, stale discard, "
                            + "strategy preservation and fresh reoptimization."
            );
        }
        if (diagnosticArtificialGaDelayMs < 0) {
            throw new IllegalArgumentException(
                    source + ": diagnosticArtificialGaDelayMs must be >= 0"
            );
        }
        if (getGaWallClockBudgetMode() == LiveDeltaTMaxMode.LIVE_ADAPTIVE) {
            validateAdaptiveBudget(source);
        }
    }

    private void normalizeCore(String source) {
        if (scenarioName == null || scenarioName.isBlank()) {
            scenarioName = "MaGaLiveMagaRuntimeStudy";
        }
        if (scenarioName.contains("..") || scenarioName.contains("\\")
                || scenarioName.contains("/")
                || !scenarioName.matches("^[A-Za-z0-9_.-]+$")) {
            throw new IllegalArgumentException(source + ": scenarioName is invalid");
        }
        if (advancedMobilityDiagnosticsEnabled == null) {
            advancedMobilityDiagnosticsEnabled = Boolean.FALSE;
        }
        if (advancedDiagnosticsFlushBatchSize == null) {
            advancedDiagnosticsFlushBatchSize = 32;
        }
        if (advancedDiagnosticsRunId == null || advancedDiagnosticsRunId.isBlank()) {
            advancedDiagnosticsRunId = scenarioName;
        } else {
            advancedDiagnosticsRunId = advancedDiagnosticsRunId.trim();
        }

        gaParameterScalingMode = normalizeEnum(
                gaParameterScalingMode, GaParameterScalingMode.ADAPTIVE.name(),
                source, "gaParameterScalingMode", "STATIC or ADAPTIVE"
        );
        experimentalVariant = MaGaExperimentalVariant.parse(
                experimentalVariant, source
        ).name();
    }

    private void normalizeV3CAliases(String source) {
        if (gaWallClockBudgetMode == null || gaWallClockBudgetMode.isBlank()) {
            gaWallClockBudgetMode = deltaTMaxMode;
        }
        gaWallClockBudgetMode = normalizeEnum(
                gaWallClockBudgetMode, LiveDeltaTMaxMode.CONFIGURED_STATIC.name(),
                source, "gaWallClockBudgetMode", "CONFIGURED_STATIC or LIVE_ADAPTIVE"
        );

        configuredInitialGaWallClockBudgetSeconds = firstNonNull(
                configuredInitialGaWallClockBudgetSeconds,
                configuredInitialDeltaTMaxSeconds,
                configuredGaRuntimeEstimateSeconds
        );
        adaptiveGaWallClockBudgetMinimumSeconds = firstNonNull(
                adaptiveGaWallClockBudgetMinimumSeconds,
                adaptiveDeltaTMaxMinimumSeconds,
                0.10
        );
        adaptiveGaWallClockBudgetMaximumSeconds = firstNonNull(
                adaptiveGaWallClockBudgetMaximumSeconds,
                adaptiveDeltaTMaxMaximumSeconds,
                1.50
        );
        adaptiveGaWallClockBudgetHistorySize = firstNonNull(
                adaptiveGaWallClockBudgetHistorySize,
                adaptiveDeltaTMaxHistorySize,
                20
        );
        adaptiveGaWallClockBudgetWarmupSamples = firstNonNull(
                adaptiveGaWallClockBudgetWarmupSamples,
                adaptiveDeltaTMaxWarmupSamples,
                3
        );
        adaptiveGaWallClockBudgetSafetyMarginSeconds = firstNonNull(
                adaptiveGaWallClockBudgetSafetyMarginSeconds,
                adaptiveDeltaTMaxSafetyMarginSeconds,
                0.10
        );
        adaptiveGaWallClockBudgetMaximumStepUpSeconds = firstNonNull(
                adaptiveGaWallClockBudgetMaximumStepUpSeconds,
                adaptiveDeltaTMaxMaximumStepUpSeconds,
                0.25
        );
        adaptiveGaWallClockBudgetMaximumStepDownSeconds = firstNonNull(
                adaptiveGaWallClockBudgetMaximumStepDownSeconds,
                adaptiveDeltaTMaxMaximumStepDownSeconds,
                0.10
        );
        if (maxSnapshotAgeSimulationSeconds == null) {
            maxSnapshotAgeSimulationSeconds = configuredMaxWindowSeconds;
        }
        if (cooperativeGaBudgetStopEnabled == null) {
            cooperativeGaBudgetStopEnabled = Boolean.TRUE;
        }
    }

    private void validateAdaptiveBudget(String source) {
        requireFiniteAndNonNegative(adaptiveGaWallClockBudgetMinimumSeconds,
                "adaptiveGaWallClockBudgetMinimumSeconds", source);
        requirePositive(adaptiveGaWallClockBudgetMaximumSeconds,
                "adaptiveGaWallClockBudgetMaximumSeconds", source);
        requireFiniteAndNonNegative(adaptiveGaWallClockBudgetSafetyMarginSeconds,
                "adaptiveGaWallClockBudgetSafetyMarginSeconds", source);
        requirePositive(adaptiveGaWallClockBudgetMaximumStepUpSeconds,
                "adaptiveGaWallClockBudgetMaximumStepUpSeconds", source);
        requirePositive(adaptiveGaWallClockBudgetMaximumStepDownSeconds,
                "adaptiveGaWallClockBudgetMaximumStepDownSeconds", source);
        if (adaptiveGaWallClockBudgetHistorySize < 1) {
            throw new IllegalArgumentException(
                    source + ": adaptiveGaWallClockBudgetHistorySize must be >= 1"
            );
        }
        if (adaptiveGaWallClockBudgetWarmupSamples < 1
                || adaptiveGaWallClockBudgetWarmupSamples
                > adaptiveGaWallClockBudgetHistorySize) {
            throw new IllegalArgumentException(
                    source + ": adaptiveGaWallClockBudgetWarmupSamples out of range"
            );
        }
        if (adaptiveGaWallClockBudgetMinimumSeconds
                > adaptiveGaWallClockBudgetMaximumSeconds) {
            throw new IllegalArgumentException(
                    source + ": adaptive wall-clock minimum exceeds maximum"
            );
        }
        if (configuredInitialGaWallClockBudgetSeconds
                < adaptiveGaWallClockBudgetMinimumSeconds
                || configuredInitialGaWallClockBudgetSeconds
                > adaptiveGaWallClockBudgetMaximumSeconds) {
            throw new IllegalArgumentException(
                    source + ": configured initial wall-clock budget outside adaptive bounds"
            );
        }
    }

    private static String normalizeEnum(
            String value, String defaultValue, String source,
            String field, String allowed
    ) {
        String normalized = value == null || value.isBlank()
                ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        try {
            if ("gaParameterScalingMode".equals(field)) {
                return GaParameterScalingMode.valueOf(normalized).name();
            }
            return LiveDeltaTMaxMode.valueOf(normalized).name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    source + ": " + field + " must be " + allowed, e
            );
        }
    }

    private static <T> T firstNonNull(T first, T second, T fallback) {
        return first != null ? first : (second != null ? second : fallback);
    }
    private static void requirePositive(long value, String field, String source) {
        if (value <= 0) {
            throw new IllegalArgumentException(source + ": " + field + " must be > 0");
        }
    }
    private static void requirePositive(double value, String field, String source) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    source + ": " + field + " must be finite and > 0"
            );
        }
    }
    private static void requireFiniteAndNonNegative(
            double value, String field, String source
    ) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    source + ": " + field + " must be finite and >= 0"
            );
        }
    }
}
