package org.eclipse.mosaic.app.maga.celltraffic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small scenario-local configuration for the integrated Cell diagnostic.
 *
 * <p>The MOSAIC API provides {@code OperatingSystem.getConfigurationPath()}.
 * This class deliberately parses only the known diagnostic keys and does not
 * try to become a general JSON parser.
 */
public final class MaGaCellTrafficDiagnosticConfig {

    public static final String CONFIG_FILE_NAME = "ma_ga_cell_traffic_config.json";

    private static final String DEFAULT_DESTINATION_ID = "server_0";
    private static final int DEFAULT_REQUEST_PAYLOAD_BYTES = 1000;
    private static final int DEFAULT_RESPONSE_PAYLOAD_BYTES = 500;
    private static final long DEFAULT_INTERVAL_MS = 1000L;
    private static final long DEFAULT_INITIAL_DELAY_MS = 1000L;
    private static final long DEFAULT_MAX_UPLINK_BITRATE = 50_000_000L;
    private static final long DEFAULT_MAX_DOWNLINK_BITRATE = 50_000_000L;

    private final String destinationId;
    private final int requestPayloadBytes;
    private final int responsePayloadBytes;
    private final long intervalMs;
    private final long initialDelayMs;
    private final long maxUplinkBitrate;
    private final long maxDownlinkBitrate;

    private MaGaCellTrafficDiagnosticConfig(
            String destinationId,
            int requestPayloadBytes,
            int responsePayloadBytes,
            long intervalMs,
            long initialDelayMs,
            long maxUplinkBitrate,
            long maxDownlinkBitrate
    ) {
        this.destinationId = destinationId;
        this.requestPayloadBytes = requestPayloadBytes;
        this.responsePayloadBytes = responsePayloadBytes;
        this.intervalMs = intervalMs;
        this.initialDelayMs = initialDelayMs;
        this.maxUplinkBitrate = maxUplinkBitrate;
        this.maxDownlinkBitrate = maxDownlinkBitrate;
    }

    public static MaGaCellTrafficDiagnosticConfig defaults() {
        return new MaGaCellTrafficDiagnosticConfig(
                DEFAULT_DESTINATION_ID,
                DEFAULT_REQUEST_PAYLOAD_BYTES,
                DEFAULT_RESPONSE_PAYLOAD_BYTES,
                DEFAULT_INTERVAL_MS,
                DEFAULT_INITIAL_DELAY_MS,
                DEFAULT_MAX_UPLINK_BITRATE,
                DEFAULT_MAX_DOWNLINK_BITRATE
        );
    }

    public static MaGaCellTrafficDiagnosticConfig load(File configurationPath) {
        MaGaCellTrafficDiagnosticConfig fallback = defaults();
        if (configurationPath == null) {
            return fallback;
        }

        File configFile = new File(configurationPath, CONFIG_FILE_NAME);
        if (!configFile.isFile()) {
            return fallback;
        }

        try {
            String json = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            String destinationId = readString(json, "destinationId", fallback.destinationId);
            int requestPayloadBytes = readPositiveInt(json, "requestPayloadBytes", fallback.requestPayloadBytes);
            int responsePayloadBytes = readPositiveInt(json, "responsePayloadBytes", fallback.responsePayloadBytes);
            long intervalMs = readPositiveLong(json, "intervalMs", fallback.intervalMs);
            long initialDelayMs = readNonNegativeLong(json, "initialDelayMs", fallback.initialDelayMs);
            long maxUplinkBitrate = parseBitrate(readString(json, "maxUplinkBitrate", "50 Mbps"), "maxUplinkBitrate");
            long maxDownlinkBitrate = parseBitrate(readString(json, "maxDownlinkBitrate", "50 Mbps"), "maxDownlinkBitrate");

            if (destinationId.trim().isEmpty()) {
                throw new IllegalArgumentException("destinationId must not be empty");
            }

            return new MaGaCellTrafficDiagnosticConfig(
                    destinationId.trim(),
                    requestPayloadBytes,
                    responsePayloadBytes,
                    intervalMs,
                    initialDelayMs,
                    maxUplinkBitrate,
                    maxDownlinkBitrate
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read " + configFile.getAbsolutePath(), e);
        }
    }

    public String getDestinationId() {
        return destinationId;
    }

    public int getRequestPayloadBytes() {
        return requestPayloadBytes;
    }

    public int getResponsePayloadBytes() {
        return responsePayloadBytes;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public long getMaxUplinkBitrate() {
        return maxUplinkBitrate;
    }

    public long getMaxDownlinkBitrate() {
        return maxDownlinkBitrate;
    }

    private static String readString(String json, String key, String defaultValue) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private static int readPositiveInt(String json, String key, int defaultValue) {
        long value = readPositiveLong(json, key, defaultValue);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " is too large");
        }
        return (int) value;
    }

    private static long readPositiveLong(String json, String key, long defaultValue) {
        long value = readLong(json, key, defaultValue);
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be > 0");
        }
        return value;
    }

    private static long readNonNegativeLong(String json, String key, long defaultValue) {
        long value = readLong(json, key, defaultValue);
        if (value < 0) {
            throw new IllegalArgumentException(key + " must be >= 0");
        }
        return value;
    }

    private static long readLong(String json, String key, long defaultValue) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : defaultValue;
    }

    private static long parseBitrate(String value, String key) {
        Pattern pattern = Pattern.compile("\\s*([0-9]+)\\s*(bps|Kbps|Mbps|Gbps)\\s*");
        Matcher matcher = pattern.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(key + " has unsupported bitrate: " + value);
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        if ("bps".equals(unit)) {
            return amount;
        }
        if ("Kbps".equals(unit)) {
            return amount * 1_000L;
        }
        if ("Mbps".equals(unit)) {
            return amount * 1_000_000L;
        }
        if ("Gbps".equals(unit)) {
            return amount * 1_000_000_000L;
        }
        throw new IllegalArgumentException(key + " has unsupported bitrate unit: " + unit);
    }
}
