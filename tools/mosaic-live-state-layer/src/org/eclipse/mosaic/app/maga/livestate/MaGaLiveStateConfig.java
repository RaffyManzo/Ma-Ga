package org.eclipse.mosaic.app.maga.livestate;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MaGaLiveStateConfig {

    static final String CONFIG_FILE_NAME = "ma_ga_live_state_config.json";
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    long tickIntervalMs;
    double singlehopRadiusMeters;
    long localCpuCyclesPerSecond;
    String localCpuSource;
    long v2vNominalBandwidthBitsPerSecond;
    String v2vBandwidthSource;
    double v2vPropagationDelaySeconds;
    CellDiagnosticAccounting cellDiagnosticAccounting;
    List<TaskProfile> taskProfiles;
    StaticInfrastructure staticInfrastructure;

    static MaGaLiveStateConfig load(File configurationPath) {
        if (configurationPath == null) {
            throw new IllegalArgumentException("MOSAIC configuration path is null");
        }
        File configFile = new File(configurationPath, CONFIG_FILE_NAME);
        if (!configFile.isFile()) {
            throw new IllegalArgumentException("Missing live state config: " + configFile.getAbsolutePath());
        }
        try {
            String json = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            MaGaLiveStateConfig config = new Gson().fromJson(json, MaGaLiveStateConfig.class);
            config.validate(configFile);
            return config;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read " + configFile.getAbsolutePath(), e);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON in " + configFile.getAbsolutePath(), e);
        }
    }

    long getTickIntervalMs() {
        return tickIntervalMs;
    }

    long getTickIntervalNs() {
        return tickIntervalMs * NANOSECONDS_PER_MILLISECOND;
    }

    double getSinglehopRadiusMeters() {
        return singlehopRadiusMeters;
    }

    long getLocalCpuCyclesPerSecond() {
        return localCpuCyclesPerSecond;
    }

    String getLocalCpuSource() {
        return localCpuSource;
    }

    long getV2vNominalBandwidthBitsPerSecond() {
        return v2vNominalBandwidthBitsPerSecond;
    }

    String getV2vBandwidthSource() {
        return v2vBandwidthSource;
    }

    double getV2vPropagationDelaySeconds() {
        return v2vPropagationDelaySeconds;
    }

    List<TaskProfile> getTaskProfiles() {
        return Collections.unmodifiableList(taskProfiles);
    }

    StaticInfrastructure getStaticInfrastructure() {
        return staticInfrastructure;
    }

    boolean hasCellDiagnosticAccounting() {
        return cellDiagnosticAccounting != null;
    }

    CellDiagnosticAccounting getCellDiagnosticAccounting() {
        if (cellDiagnosticAccounting == null) {
            throw new IllegalStateException("cellDiagnosticAccounting is not configured");
        }
        return cellDiagnosticAccounting;
    }

    private void validate(File configFile) {
        String source = configFile.getAbsolutePath();
        requirePositive(tickIntervalMs, "tickIntervalMs", source);
        requireFinitePositive(singlehopRadiusMeters, "singlehopRadiusMeters", source);
        requirePositive(localCpuCyclesPerSecond, "localCpuCyclesPerSecond", source);
        requireText(localCpuSource, "localCpuSource", source);
        requirePositive(v2vNominalBandwidthBitsPerSecond, "v2vNominalBandwidthBitsPerSecond", source);
        requireText(v2vBandwidthSource, "v2vBandwidthSource", source);
        requireFiniteNonNegative(v2vPropagationDelaySeconds, "v2vPropagationDelaySeconds", source);
        if (cellDiagnosticAccounting != null) {
            cellDiagnosticAccounting.validate(source);
        }
        if (taskProfiles == null || taskProfiles.isEmpty()) {
            throw new IllegalArgumentException(source + ": taskProfiles must not be empty");
        }
        List<TaskProfile> normalizedProfiles = new ArrayList<>();
        for (int i = 0; i < taskProfiles.size(); i++) {
            TaskProfile profile = taskProfiles.get(i);
            profile.validate(source, i);
            normalizedProfiles.add(profile);
        }
        taskProfiles = normalizedProfiles;
        if (staticInfrastructure == null) {
            throw new IllegalArgumentException(source + ": staticInfrastructure is required");
        }
        staticInfrastructure.validate(source);
    }

    private static void requireText(String value, String field, String source) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(source + ": " + field + " must be non-empty");
        }
    }

    private static void requirePositive(long value, String field, String source) {
        if (value <= 0) {
            throw new IllegalArgumentException(source + ": " + field + " must be > 0");
        }
    }

    private static void requireFinitePositive(double value, String field, String source) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(source + ": " + field + " must be finite and > 0");
        }
    }

    private static void requireFiniteNonNegative(double value, String field, String source) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(source + ": " + field + " must be finite and >= 0");
        }
    }

    static final class TaskProfile {
        String profileId;
        long activationTimeMs;
        String sourceVehicleId;
        long inputSizeBits;
        long outputSizeBits;
        long cpuCycles;
        double deadlineSeconds;

        void validate(String source, int index) {
            String prefix = "taskProfiles[" + index + "]";
            requireText(profileId, prefix + ".profileId", source);
            requirePositive(activationTimeMs, prefix + ".activationTimeMs", source);
            requireText(sourceVehicleId, prefix + ".sourceVehicleId", source);
            requirePositive(inputSizeBits, prefix + ".inputSizeBits", source);
            requirePositive(outputSizeBits, prefix + ".outputSizeBits", source);
            requirePositive(cpuCycles, prefix + ".cpuCycles", source);
            requireFinitePositive(deadlineSeconds, prefix + ".deadlineSeconds", source);
        }

        LiveTaskState toTaskState(int index) {
            long activationTimeNs = activationTimeMs * NANOSECONDS_PER_MILLISECOND;
            String taskId = "task_" + profileId + "__" + sourceVehicleId + "__t_" + activationTimeMs + "__" + index;
            return new LiveTaskState(
                    taskId,
                    profileId,
                    sourceVehicleId,
                    activationTimeNs,
                    inputSizeBits,
                    outputSizeBits,
                    cpuCycles,
                    deadlineSeconds,
                    LiveTaskStatus.PENDING
            );
        }
    }

    static final class StaticInfrastructure {
        List<Gateway> gateways;
        List<EdgeNode> edgeNodes;
        List<CloudNode> cloudNodes;

        void validate(String source) {
            if (gateways == null || gateways.isEmpty()) {
                throw new IllegalArgumentException(source + ": staticInfrastructure.gateways must not be empty");
            }
            if (edgeNodes == null) {
                edgeNodes = Collections.emptyList();
            }
            if (cloudNodes == null) {
                cloudNodes = Collections.emptyList();
            }
            for (int i = 0; i < gateways.size(); i++) {
                gateways.get(i).validate(source, i);
            }
            for (int i = 0; i < edgeNodes.size(); i++) {
                edgeNodes.get(i).validate(source, i);
            }
            for (int i = 0; i < cloudNodes.size(); i++) {
                cloudNodes.get(i).validate(source, i);
            }
        }
    }

    static final class CellDiagnosticAccounting {
        long bucketDurationMs;
        String availableFromPolicy;
        String bandwidthSource;
        String destinationId;
        long requestPayloadBytes;
        long responsePayloadBytes;
        long intervalMs;
        long initialDelayMs;
        String maxUplinkBitrate;
        String maxDownlinkBitrate;
        List<GatewayPool> gatewayPools;

        long getBucketDurationNs() {
            return bucketDurationMs * NANOSECONDS_PER_MILLISECOND;
        }

        long getIntervalNs() {
            return intervalMs * NANOSECONDS_PER_MILLISECOND;
        }

        long getInitialDelayNs() {
            return initialDelayMs * NANOSECONDS_PER_MILLISECOND;
        }

        long getMaxUplinkBitrateBitsPerSecond() {
            return parseBitrateBitsPerSecond(maxUplinkBitrate);
        }

        long getMaxDownlinkBitrateBitsPerSecond() {
            return parseBitrateBitsPerSecond(maxDownlinkBitrate);
        }

        void validate(String source) {
            requirePositive(bucketDurationMs, "cellDiagnosticAccounting.bucketDurationMs", source);
            requireText(availableFromPolicy, "cellDiagnosticAccounting.availableFromPolicy", source);
            if (!"SAFE_AFTER_TIMESTAMP".equals(availableFromPolicy)) {
                throw new IllegalArgumentException(source + ": cellDiagnosticAccounting.availableFromPolicy must be SAFE_AFTER_TIMESTAMP");
            }
            requireText(bandwidthSource, "cellDiagnosticAccounting.bandwidthSource", source);
            if (!"DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES".equals(bandwidthSource)) {
                throw new IllegalArgumentException(source + ": cellDiagnosticAccounting.bandwidthSource must be DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES");
            }
            requireText(destinationId, "cellDiagnosticAccounting.destinationId", source);
            requirePositive(requestPayloadBytes, "cellDiagnosticAccounting.requestPayloadBytes", source);
            requirePositive(responsePayloadBytes, "cellDiagnosticAccounting.responsePayloadBytes", source);
            requirePositive(intervalMs, "cellDiagnosticAccounting.intervalMs", source);
            requirePositive(initialDelayMs, "cellDiagnosticAccounting.initialDelayMs", source);
            requireText(maxUplinkBitrate, "cellDiagnosticAccounting.maxUplinkBitrate", source);
            requireText(maxDownlinkBitrate, "cellDiagnosticAccounting.maxDownlinkBitrate", source);
            requirePositive(parseBitrateBitsPerSecond(maxUplinkBitrate), "cellDiagnosticAccounting.maxUplinkBitrate", source);
            requirePositive(parseBitrateBitsPerSecond(maxDownlinkBitrate), "cellDiagnosticAccounting.maxDownlinkBitrate", source);
            if (gatewayPools == null || gatewayPools.isEmpty()) {
                throw new IllegalArgumentException(source + ": cellDiagnosticAccounting.gatewayPools must not be empty");
            }
            for (int i = 0; i < gatewayPools.size(); i++) {
                gatewayPools.get(i).validate(source, i);
            }
        }
    }

    private static long parseBitrateBitsPerSecond(String value) {
        String normalized = value.trim().toLowerCase();
        double multiplier = 1.0;
        if (normalized.endsWith("gbps")) {
            multiplier = 1_000_000_000.0;
            normalized = normalized.substring(0, normalized.length() - 4).trim();
        } else if (normalized.endsWith("mbps")) {
            multiplier = 1_000_000.0;
            normalized = normalized.substring(0, normalized.length() - 4).trim();
        } else if (normalized.endsWith("kbps")) {
            multiplier = 1_000.0;
            normalized = normalized.substring(0, normalized.length() - 4).trim();
        } else if (normalized.endsWith("bps")) {
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        }
        double parsed = Double.parseDouble(normalized);
        return Math.round(parsed * multiplier);
    }

    static final class GatewayPool {
        String poolId;
        long nominalCapacityBitsPerSecond;

        void validate(String source, int index) {
            String prefix = "cellDiagnosticAccounting.gatewayPools[" + index + "]";
            requireText(poolId, prefix + ".poolId", source);
            requirePositive(nominalCapacityBitsPerSecond, prefix + ".nominalCapacityBitsPerSecond", source);
        }
    }

    static final class Gateway {
        String runtimeId;
        String gatewayId;
        String gatewayType;
        double projectedX;
        double projectedY;
        double coverageRadiusMeters;
        String cellRegionId;
        String bandwidthPoolId;

        void validate(String source, int index) {
            String prefix = "staticInfrastructure.gateways[" + index + "]";
            requireText(runtimeId, prefix + ".runtimeId", source);
            requireText(gatewayId, prefix + ".gatewayId", source);
            requireText(gatewayType, prefix + ".gatewayType", source);
            requireFiniteNonNegative(projectedX, prefix + ".projectedX", source);
            requireFiniteNonNegative(projectedY, prefix + ".projectedY", source);
            requireFinitePositive(coverageRadiusMeters, prefix + ".coverageRadiusMeters", source);
            requireText(cellRegionId, prefix + ".cellRegionId", source);
            requireText(bandwidthPoolId, prefix + ".bandwidthPoolId", source);
        }
    }

    static final class EdgeNode {
        String executionNodeId;
        List<String> gatewayIds;
        long availableCpuCyclesPerSecond;
        double basePropagationDelaySeconds;

        void validate(String source, int index) {
            String prefix = "staticInfrastructure.edgeNodes[" + index + "]";
            requireText(executionNodeId, prefix + ".executionNodeId", source);
            if (gatewayIds == null || gatewayIds.isEmpty()) {
                throw new IllegalArgumentException(source + ": " + prefix + ".gatewayIds must not be empty");
            }
            requirePositive(availableCpuCyclesPerSecond, prefix + ".availableCpuCyclesPerSecond", source);
            requireFiniteNonNegative(basePropagationDelaySeconds, prefix + ".basePropagationDelaySeconds", source);
        }
    }

    static final class CloudNode {
        String executionNodeId;
        String mosaicServerRuntimeId;
        long availableCpuCyclesPerSecond;
        double serverBaseDelaySeconds;

        void validate(String source, int index) {
            String prefix = "staticInfrastructure.cloudNodes[" + index + "]";
            requireText(executionNodeId, prefix + ".executionNodeId", source);
            requireText(mosaicServerRuntimeId, prefix + ".mosaicServerRuntimeId", source);
            requirePositive(availableCpuCyclesPerSecond, prefix + ".availableCpuCyclesPerSecond", source);
            requireFiniteNonNegative(serverBaseDelaySeconds, prefix + ".serverBaseDelaySeconds", source);
        }
    }
}
