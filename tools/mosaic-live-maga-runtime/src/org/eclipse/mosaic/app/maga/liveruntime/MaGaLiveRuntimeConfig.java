package org.eclipse.mosaic.app.maga.liveruntime;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class MaGaLiveRuntimeConfig {

    static final String CONFIG_FILE_NAME = "ma_ga_live_runtime_config.json";
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

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

    public String profileName() {
        return diagnosticArtificialGaDelayMs > 0 ? "diagnostic-overrun" : "normal";
    }

    private void validate(File configFile) {
        String source = configFile.getAbsolutePath();
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
}
