package org.eclipse.mosaic.app.maga.liveprobe;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MaGaLiveProbeConfig {

    static final String CONFIG_FILE_NAME = "ma_ga_live_probe_config.json";

    private static final long DEFAULT_TICK_INTERVAL_MS = 1000L;
    private static final boolean DEFAULT_LOG_VEHICLE_UPDATES = true;
    private static final boolean DEFAULT_LOG_COORDINATOR_TICKS = true;

    private final long tickIntervalMs;
    private final boolean logVehicleUpdates;
    private final boolean logCoordinatorTicks;

    private MaGaLiveProbeConfig(
            long tickIntervalMs,
            boolean logVehicleUpdates,
            boolean logCoordinatorTicks
    ) {
        this.tickIntervalMs = tickIntervalMs;
        this.logVehicleUpdates = logVehicleUpdates;
        this.logCoordinatorTicks = logCoordinatorTicks;
    }

    static MaGaLiveProbeConfig defaults() {
        return new MaGaLiveProbeConfig(
                DEFAULT_TICK_INTERVAL_MS,
                DEFAULT_LOG_VEHICLE_UPDATES,
                DEFAULT_LOG_COORDINATOR_TICKS
        );
    }

    static MaGaLiveProbeConfig load(File configurationPath) {
        MaGaLiveProbeConfig fallback = defaults();
        if (configurationPath == null) {
            return fallback;
        }

        File configFile = new File(configurationPath, CONFIG_FILE_NAME);
        if (!configFile.isFile()) {
            return fallback;
        }

        try {
            String json = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            long tickIntervalMs = readPositiveLong(json, "tickIntervalMs", fallback.tickIntervalMs);
            boolean logVehicleUpdates = readBoolean(json, "logVehicleUpdates", fallback.logVehicleUpdates);
            boolean logCoordinatorTicks = readBoolean(json, "logCoordinatorTicks", fallback.logCoordinatorTicks);
            return new MaGaLiveProbeConfig(tickIntervalMs, logVehicleUpdates, logCoordinatorTicks);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read " + configFile.getAbsolutePath(), e);
        }
    }

    long getTickIntervalMs() {
        return tickIntervalMs;
    }

    boolean isLogVehicleUpdates() {
        return logVehicleUpdates;
    }

    boolean isLogCoordinatorTicks() {
        return logCoordinatorTicks;
    }

    private static long readPositiveLong(String json, String key, long defaultValue) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher matcher = pattern.matcher(json);
        long value = matcher.find() ? Long.parseLong(matcher.group(1)) : defaultValue;
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be > 0");
        }
        return value;
    }

    private static boolean readBoolean(String json, String key, boolean defaultValue) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : defaultValue;
    }
}
