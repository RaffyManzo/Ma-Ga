package org.eclipse.mosaic.app.maga.liveruntime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LiveAdaptiveDeltaTMaxEstimator {

    static final String FALLBACK_CONFIGURED_STATIC = "CONFIGURED_STATIC";
    static final String FALLBACK_NO_SAMPLES = "NO_SAMPLES";
    static final String FALLBACK_WARMUP = "WARMUP";
    static final String FALLBACK_LIVE_ADAPTIVE = "LIVE_ADAPTIVE";
    static final String FALLBACK_INVALID_SAMPLE_EXCLUDED = "INVALID_SAMPLE_EXCLUDED";
    static final String FALLBACK_BOUND_CONFLICT = "BOUND_CONFLICT";

    private final Config config;
    private final ArrayDeque<Double> history = new ArrayDeque<>();
    private double currentEstimateSeconds;
    private Snapshot lastSnapshot;

    LiveAdaptiveDeltaTMaxEstimator(Config config) {
        this.config = config;
        this.currentEstimateSeconds = config.getConfiguredInitialDeltaTMaxSeconds();
        this.lastSnapshot = Snapshot.liveAdaptive(
                currentEstimateSeconds,
                0,
                0.0,
                0.0,
                currentEstimateSeconds,
                currentEstimateSeconds,
                currentEstimateSeconds,
                FALLBACK_NO_SAMPLES
        );
    }

    synchronized Snapshot estimateForSubmission(double deltaTMinSeconds) {
        double lowerBound = lowerBound(deltaTMinSeconds);
        double previous = clamp(currentEstimateSeconds, lowerBound);
        double p95 = history.isEmpty() ? 0.0 : nearestRankP95(history);
        double target = history.isEmpty()
                ? 0.0
                : p95 + config.getSafetyMarginSeconds();
        String reason = fallbackReasonForCurrentHistory();

        currentEstimateSeconds = previous;
        lastSnapshot = Snapshot.liveAdaptive(
                previous,
                history.size(),
                p95,
                target,
                previous,
                previous,
                previous,
                reason
        );
        return lastSnapshot;
    }

    synchronized Snapshot recordCompletedRuntime(
            double runtimeSeconds,
            double deltaTMinSeconds
    ) {
        double lowerBound = lowerBound(deltaTMinSeconds);
        double previous = clamp(currentEstimateSeconds, lowerBound);

        if (!isValidSample(runtimeSeconds)) {
            currentEstimateSeconds = previous;
            lastSnapshot = Snapshot.liveAdaptive(
                    previous,
                    history.size(),
                    history.isEmpty() ? 0.0 : nearestRankP95(history),
                    0.0,
                    previous,
                    previous,
                    previous,
                    FALLBACK_INVALID_SAMPLE_EXCLUDED,
                    false,
                    runtimeSeconds
            );
            return lastSnapshot;
        }

        history.addLast(runtimeSeconds);
        while (history.size() > config.getHistorySize()) {
            history.removeFirst();
        }

        double p95 = nearestRankP95(history);
        double target = p95 + config.getSafetyMarginSeconds();
        double raw = clamp(target, lowerBound);
        double updated;
        String reason;

        if (history.size() < config.getWarmupSamples()) {
            updated = clamp(config.getConfiguredInitialDeltaTMaxSeconds(), lowerBound);
            reason = FALLBACK_WARMUP;
        } else {
            double boundedDelta = clamp(
                    raw - previous,
                    -config.getMaximumStepDownSeconds(),
                    config.getMaximumStepUpSeconds()
            );
            updated = clamp(previous + boundedDelta, lowerBound);
            reason = FALLBACK_LIVE_ADAPTIVE;
        }

        currentEstimateSeconds = updated;
        lastSnapshot = Snapshot.liveAdaptive(
                updated,
                history.size(),
                p95,
                target,
                raw,
                previous,
                updated,
                reason,
                true,
                runtimeSeconds
        );
        return lastSnapshot;
    }

    synchronized Snapshot getLastSnapshot() {
        return lastSnapshot;
    }

    synchronized int getSampleCount() {
        return history.size();
    }

    static boolean isValidSample(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    static double nearestRankP95(Iterable<Double> samples) {
        List<Double> values = new ArrayList<>();
        for (double sample : samples) {
            if (isValidSample(sample)) {
                values.add(sample);
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("nearest-rank P95 requires at least one valid sample.");
        }
        Collections.sort(values);
        int rank = (int) Math.ceil(0.95 * values.size());
        int index = Math.max(0, Math.min(values.size() - 1, rank - 1));
        return values.get(index);
    }

    private String fallbackReasonForCurrentHistory() {
        if (history.isEmpty()) {
            return FALLBACK_NO_SAMPLES;
        }
        if (history.size() < config.getWarmupSamples()) {
            return FALLBACK_WARMUP;
        }
        return FALLBACK_LIVE_ADAPTIVE;
    }

    private double lowerBound(double deltaTMinSeconds) {
        validateFiniteAndNonNegative("deltaTMinSeconds", deltaTMinSeconds);
        double lowerBound = Math.max(
                deltaTMinSeconds,
                config.getAdaptiveMinimumSeconds()
        );
        if (lowerBound > config.getAdaptiveMaximumSeconds()) {
            throw new BoundConflictException(
                    "BOUND_CONFLICT: effective minimum exceeds adaptive maximum"
                            + " | deltaTMinSeconds=" + deltaTMinSeconds
                            + " | adaptiveMinimumSeconds=" + config.getAdaptiveMinimumSeconds()
                            + " | effectiveMinimumSeconds=" + lowerBound
                            + " | adaptiveMaximumSeconds=" + config.getAdaptiveMaximumSeconds()
            );
        }
        return lowerBound;
    }

    private double clamp(double value, double lowerBound) {
        return clamp(value, lowerBound, config.getAdaptiveMaximumSeconds());
    }

    private static double clamp(double value, double lowerBound, double upperBound) {
        if (!Double.isFinite(value)) {
            return lowerBound;
        }
        return Math.max(lowerBound, Math.min(upperBound, value));
    }

    private static void validateFiniteAndNonNegative(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }

    static final class Config {
        private final double configuredInitialDeltaTMaxSeconds;
        private final double adaptiveMinimumSeconds;
        private final double adaptiveMaximumSeconds;
        private final int historySize;
        private final int warmupSamples;
        private final double safetyMarginSeconds;
        private final double maximumStepUpSeconds;
        private final double maximumStepDownSeconds;

        Config(
                double configuredInitialDeltaTMaxSeconds,
                double adaptiveMinimumSeconds,
                double adaptiveMaximumSeconds,
                int historySize,
                int warmupSamples,
                double safetyMarginSeconds,
                double maximumStepUpSeconds,
                double maximumStepDownSeconds
        ) {
            validatePositive(
                    "configuredInitialDeltaTMaxSeconds",
                    configuredInitialDeltaTMaxSeconds
            );
            validateFiniteAndNonNegative(
                    "adaptiveMinimumSeconds",
                    adaptiveMinimumSeconds
            );
            validatePositive(
                    "adaptiveMaximumSeconds",
                    adaptiveMaximumSeconds
            );
            if (adaptiveMinimumSeconds > adaptiveMaximumSeconds) {
                throw new IllegalArgumentException(
                        "BOUND_CONFLICT: adaptiveMinimumSeconds exceeds adaptiveMaximumSeconds"
                                + " | adaptiveMinimumSeconds="
                                + adaptiveMinimumSeconds
                                + " | adaptiveMaximumSeconds="
                                + adaptiveMaximumSeconds
                );
            }
            if (configuredInitialDeltaTMaxSeconds < adaptiveMinimumSeconds) {
                throw new IllegalArgumentException(
                        "configuredInitialDeltaTMaxSeconds must be >= adaptiveMinimumSeconds."
                );
            }
            if (configuredInitialDeltaTMaxSeconds > adaptiveMaximumSeconds) {
                throw new IllegalArgumentException(
                        "configuredInitialDeltaTMaxSeconds must be <= adaptiveMaximumSeconds."
                );
            }
            if (historySize < 1) {
                throw new IllegalArgumentException("historySize must be >= 1.");
            }
            if (warmupSamples < 1 || warmupSamples > historySize) {
                throw new IllegalArgumentException(
                        "warmupSamples must be in [1, historySize]."
                );
            }
            validateFiniteAndNonNegative(
                    "safetyMarginSeconds",
                    safetyMarginSeconds
            );
            validatePositive("maximumStepUpSeconds", maximumStepUpSeconds);
            validatePositive("maximumStepDownSeconds", maximumStepDownSeconds);

            this.configuredInitialDeltaTMaxSeconds =
                    configuredInitialDeltaTMaxSeconds;
            this.adaptiveMinimumSeconds = adaptiveMinimumSeconds;
            this.adaptiveMaximumSeconds = adaptiveMaximumSeconds;
            this.historySize = historySize;
            this.warmupSamples = warmupSamples;
            this.safetyMarginSeconds = safetyMarginSeconds;
            this.maximumStepUpSeconds = maximumStepUpSeconds;
            this.maximumStepDownSeconds = maximumStepDownSeconds;
        }

        double getConfiguredInitialDeltaTMaxSeconds() {
            return configuredInitialDeltaTMaxSeconds;
        }

        double getAdaptiveMinimumSeconds() {
            return adaptiveMinimumSeconds;
        }

        double getAdaptiveMaximumSeconds() {
            return adaptiveMaximumSeconds;
        }

        int getHistorySize() {
            return historySize;
        }

        int getWarmupSamples() {
            return warmupSamples;
        }

        double getSafetyMarginSeconds() {
            return safetyMarginSeconds;
        }

        double getMaximumStepUpSeconds() {
            return maximumStepUpSeconds;
        }

        double getMaximumStepDownSeconds() {
            return maximumStepDownSeconds;
        }

        private static void validatePositive(String fieldName, double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(fieldName + " must be finite.");
            }
            if (value <= 0.0) {
                throw new IllegalArgumentException(fieldName + " must be > 0.");
            }
        }
    }

    static final class BoundConflictException extends IllegalStateException {
        BoundConflictException(String message) {
            super(message);
        }
    }

    static final class Snapshot {
        private final LiveDeltaTMaxMode mode;
        private final double estimateSeconds;
        private final int sampleCount;
        private final double p95Seconds;
        private final double targetSeconds;
        private final double clampedSeconds;
        private final double previousSeconds;
        private final double updatedSeconds;
        private final String fallbackReason;
        private final boolean sampleAccepted;
        private final double sampleRuntimeSeconds;

        private Snapshot(
                LiveDeltaTMaxMode mode,
                double estimateSeconds,
                int sampleCount,
                double p95Seconds,
                double targetSeconds,
                double clampedSeconds,
                double previousSeconds,
                double updatedSeconds,
                String fallbackReason,
                boolean sampleAccepted,
                double sampleRuntimeSeconds
        ) {
            this.mode = mode;
            this.estimateSeconds = estimateSeconds;
            this.sampleCount = sampleCount;
            this.p95Seconds = p95Seconds;
            this.targetSeconds = targetSeconds;
            this.clampedSeconds = clampedSeconds;
            this.previousSeconds = previousSeconds;
            this.updatedSeconds = updatedSeconds;
            this.fallbackReason = fallbackReason;
            this.sampleAccepted = sampleAccepted;
            this.sampleRuntimeSeconds = sampleRuntimeSeconds;
        }

        static Snapshot configuredStatic(double deltaTMaxSeconds) {
            return new Snapshot(
                    LiveDeltaTMaxMode.CONFIGURED_STATIC,
                    deltaTMaxSeconds,
                    0,
                    0.0,
                    deltaTMaxSeconds,
                    deltaTMaxSeconds,
                    deltaTMaxSeconds,
                    deltaTMaxSeconds,
                    FALLBACK_CONFIGURED_STATIC,
                    false,
                    0.0
            );
        }

        static Snapshot liveAdaptive(
                double estimateSeconds,
                int sampleCount,
                double p95Seconds,
                double targetSeconds,
                double clampedSeconds,
                double previousSeconds,
                double updatedSeconds,
                String fallbackReason
        ) {
            return liveAdaptive(
                    estimateSeconds,
                    sampleCount,
                    p95Seconds,
                    targetSeconds,
                    clampedSeconds,
                    previousSeconds,
                    updatedSeconds,
                    fallbackReason,
                    false,
                    0.0
            );
        }

        static Snapshot liveAdaptive(
                double estimateSeconds,
                int sampleCount,
                double p95Seconds,
                double targetSeconds,
                double clampedSeconds,
                double previousSeconds,
                double updatedSeconds,
                String fallbackReason,
                boolean sampleAccepted,
                double sampleRuntimeSeconds
        ) {
            return new Snapshot(
                    LiveDeltaTMaxMode.LIVE_ADAPTIVE,
                    estimateSeconds,
                    sampleCount,
                    p95Seconds,
                    targetSeconds,
                    clampedSeconds,
                    previousSeconds,
                    updatedSeconds,
                    fallbackReason,
                    sampleAccepted,
                    sampleRuntimeSeconds
            );
        }

        LiveDeltaTMaxMode getMode() {
            return mode;
        }

        boolean isLiveAdaptive() {
            return mode == LiveDeltaTMaxMode.LIVE_ADAPTIVE;
        }

        double getEstimateSeconds() {
            return estimateSeconds;
        }

        int getSampleCount() {
            return sampleCount;
        }

        double getP95Seconds() {
            return p95Seconds;
        }

        double getTargetSeconds() {
            return targetSeconds;
        }

        double getClampedSeconds() {
            return clampedSeconds;
        }

        double getPreviousSeconds() {
            return previousSeconds;
        }

        double getUpdatedSeconds() {
            return updatedSeconds;
        }

        String getFallbackReason() {
            return fallbackReason;
        }

        boolean isSampleAccepted() {
            return sampleAccepted;
        }

        double getSampleRuntimeSeconds() {
            return sampleRuntimeSeconds;
        }
    }
}
