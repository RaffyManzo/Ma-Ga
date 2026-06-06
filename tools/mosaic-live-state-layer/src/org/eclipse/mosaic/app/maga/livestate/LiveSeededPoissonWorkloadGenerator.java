package org.eclipse.mosaic.app.maga.livestate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

final class LiveSeededPoissonWorkloadGenerator {

    private final MaGaLiveStateConfig.WorkloadGeneration config;
    private final Random random;
    private final double tickIntervalSeconds;
    private long sequence;

    LiveSeededPoissonWorkloadGenerator(MaGaLiveStateConfig.WorkloadGeneration config, long tickIntervalNs) {
        if (config == null || !config.enabled) {
            throw new IllegalArgumentException("Enabled workloadGeneration configuration is required");
        }
        this.config = config;
        this.random = new Random(config.randomSeed);
        this.tickIntervalSeconds = tickIntervalNs / 1_000_000_000.0;
        this.sequence = 0L;
    }

    List<LiveTaskState> generate(long tickTimeNs, List<LiveVehicleState> activeVehicles) {
        List<LiveTaskState> generated = new ArrayList<>();
        if (tickTimeNs < config.getStartTimeNs()) {
            return generated;
        }
        List<LiveVehicleState> orderedVehicles = new ArrayList<>();
        for (LiveVehicleState vehicle : activeVehicles) {
            if (vehicle.isActive()) {
                orderedVehicles.add(vehicle);
            }
        }
        orderedVehicles.sort(Comparator.comparing(LiveVehicleState::getVehicleId, LiveStateCache::naturalCompare));

        double lambda = config.arrivalRateTasksPerSecondPerActiveVehicle * tickIntervalSeconds;
        for (LiveVehicleState vehicle : orderedVehicles) {
            int taskCount = Math.min(
                    samplePoisson(lambda),
                    config.maxGeneratedTasksPerTickPerVehicle
            );
            for (int i = 0; i < taskCount; i++) {
                MaGaLiveStateConfig.GeneratedTaskProfile profile = chooseProfile();
                long currentSequence = sequence++;
                String taskId = "task_generated__"
                        + profile.profileId
                        + "__" + vehicle.getVehicleId()
                        + "__t_" + tickTimeNs
                        + "__seq_" + currentSequence;
                generated.add(
                        new LiveTaskState(
                                taskId,
                                profile.profileId,
                                vehicle.getVehicleId(),
                                tickTimeNs,
                                profile.inputSizeBits,
                                profile.outputSizeBits,
                                profile.cpuCycles,
                                profile.deadlineSeconds,
                                LiveTaskStatus.PENDING
                        )
                );
            }
        }
        return generated;
    }

    private int samplePoisson(double lambda) {
        if (lambda <= 0.0) {
            return 0;
        }
        double threshold = Math.exp(-lambda);
        int count = 0;
        double product = 1.0;
        do {
            count++;
            product *= random.nextDouble();
        } while (product > threshold);
        return count - 1;
    }

    private MaGaLiveStateConfig.GeneratedTaskProfile chooseProfile() {
        double draw = random.nextDouble();
        double cumulative = 0.0;
        MaGaLiveStateConfig.GeneratedTaskProfile fallback = null;
        for (MaGaLiveStateConfig.GeneratedTaskProfile profile : config.getProfiles()) {
            cumulative += profile.weight;
            fallback = profile;
            if (draw <= cumulative) {
                return profile;
            }
        }
        return fallback;
    }
}
