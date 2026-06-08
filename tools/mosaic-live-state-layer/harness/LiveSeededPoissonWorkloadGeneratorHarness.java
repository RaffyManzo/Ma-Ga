package org.eclipse.mosaic.app.maga.livestate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LiveSeededPoissonWorkloadGeneratorHarness {

    private static final long TICK_INTERVAL_NS = 1_000_000_000L;
    private static final long TICK_TIME_NS = 7_000_000_000L;

    public static void main(String[] args) {
        verifySameSeedReproducible();
        verifyDifferentSeedChangesSequence();
        verifyZeroRateGeneratesNothing();
        verifyNoActiveVehiclesGeneratesNothing();
        verifyOnlyActiveVehiclesReceiveTasks();
        verifyTaskCausalityAndProfiles();
        verifySubTickCallsDoNotOverGenerate();
        verifyExpiredTasksAreRemoved();
        verifyRemoteVehicleCpuForV2vCandidate();
        verifyConfiguredCellProfileIsDistinctFromRuntimeAccounting();
        System.out.println("PHASE14C3_HARNESS_PASSED");
    }

    private static void verifySameSeedReproducible() {
        List<LiveTaskState> first = generateTasks(config(104729L, 3.0, 1_000_000_000L, 750_000_000L), activeVehicles());
        List<LiveTaskState> second = generateTasks(config(104729L, 3.0, 1_000_000_000L, 750_000_000L), activeVehicles());
        require(!first.isEmpty(), "same seed test must generate at least one task");
        require(signature(first).equals(signature(second)), "same seed must generate same task sequence");
    }

    private static void verifyDifferentSeedChangesSequence() {
        List<LiveTaskState> first = generateTasks(config(104729L, 3.0, 1_000_000_000L, 750_000_000L), activeVehicles());
        List<LiveTaskState> second = generateTasks(config(130363L, 3.0, 1_000_000_000L, 750_000_000L), activeVehicles());
        require(!signature(first).equals(signature(second)), "different seed must change generated task sequence");
    }

    private static void verifyZeroRateGeneratesNothing() {
        List<LiveTaskState> tasks = generateTasks(config(104729L, 0.0, 1_000_000_000L, 750_000_000L), activeVehicles());
        require(tasks.isEmpty(), "rate 0 must generate no tasks");
    }

    private static void verifyNoActiveVehiclesGeneratesNothing() {
        List<LiveTaskState> tasks = generateTasks(
                config(104729L, 3.0, 1_000_000_000L, 750_000_000L),
                new ArrayList<LiveVehicleState>()
        );
        require(tasks.isEmpty(), "no active vehicles must generate no tasks");
    }

    private static void verifyOnlyActiveVehiclesReceiveTasks() {
        List<LiveVehicleState> vehicles = activeVehicles();
        vehicles.add(new LiveVehicleState("veh_9", TICK_TIME_NS, 5.0, 5.0, 0.0, true, false));
        List<LiveTaskState> tasks = generateTasks(config(104729L, 3.0, 1_000_000_000L, 750_000_000L), vehicles);
        for (LiveTaskState task : tasks) {
            require(!"veh_9".equals(task.getSourceVehicleId()), "inactive vehicle must not receive generated tasks");
        }
    }

    private static void verifyTaskCausalityAndProfiles() {
        List<LiveTaskState> tasks = generateTasks(config(104729L, 3.0, 1_000_000_000L, 750_000_000L), activeVehicles());
        for (LiveTaskState task : tasks) {
            require(task.getActivationTimeNs() <= TICK_TIME_NS, "generated task activation time must not be future");
            require(
                    Arrays.asList("light", "medium", "heavy").contains(task.getProfileId()),
                    "generated task profile must be light, medium or heavy"
            );
        }
    }

    private static void verifySubTickCallsDoNotOverGenerate() {
        MaGaLiveStateConfig cfg =
                config(104729L, 30.0, 1_000_000_000L, 750_000_000L);

        LiveSeededPoissonWorkloadGenerator generator =
                new LiveSeededPoissonWorkloadGenerator(
                        cfg.getWorkloadGeneration(),
                        TICK_INTERVAL_NS
                );

        List<LiveTaskState> first =
                generator.generate(TICK_TIME_NS, activeVehicles());

        List<LiveTaskState> insideSameTick =
                generator.generate(
                        TICK_TIME_NS + 100_000_000L,
                        activeVehicles()
                );

        List<LiveTaskState> nextTick =
                generator.generate(
                        TICK_TIME_NS + TICK_INTERVAL_NS,
                        activeVehicles()
                );

        require(!first.isEmpty(), "first scheduled workload tick must generate tasks");
        require(insideSameTick.isEmpty(), "sub-tick call must not generate tasks again");
        require(!nextTick.isEmpty(), "next scheduled workload tick must generate tasks");
    }

    private static void verifyExpiredTasksAreRemoved() {
        MaGaLiveStateConfig cfg =
                config(104729L, 30.0, 1_000_000_000L, 750_000_000L);

        List<LiveTaskState> generated =
                generateTasks(cfg, activeVehicles());

        LiveStateCache cache = new LiveStateCache();

        require(
                cache.installGeneratedTaskDefinitions(generated)
                        == generated.size(),
                "all generated tasks must be inserted"
        );

        require(
                cache.activateDueTasks(TICK_TIME_NS)
                        == generated.size(),
                "all generated tasks must activate"
        );

        require(
                !cache.snapshotAtOrBefore(TICK_TIME_NS)
                        .getPendingTasks()
                        .isEmpty(),
                "activated tasks must appear in the pending view"
        );

        require(
                cache.removeExpiredTasks(
                        TICK_TIME_NS + 5_000_000_000L
                ) == generated.size(),
                "all generated tasks must expire after their deadlines"
        );

        require(
                cache.snapshotAtOrBefore(
                        TICK_TIME_NS + 5_000_000_000L
                ).getPendingTasks().isEmpty(),
                "expired tasks must disappear from the pending view"
        );

        require(
                cache.activateDueTasks(
                        TICK_TIME_NS + 5_000_000_000L
                ) == 0,
                "expired tasks must not reactivate"
        );
    }

    private static void verifyRemoteVehicleCpuForV2vCandidate() {
        MaGaLiveStateConfig cfg = config(104729L, 3.0, 1_000_000_000L, 750_000_000L);
        LiveStateSnapshotView view = new LiveStateSnapshotView(TICK_TIME_NS, activeVehicles(), new ArrayList<LiveTaskState>());
        LiveLocalAndV2vCandidatePreviewBuilder.PreviewResult preview =
                new LiveLocalAndV2vCandidatePreviewBuilder().build(view, cfg);
        require(!preview.getV2vCandidates().isEmpty(), "V2V preview must not be empty");
        for (LiveV2vCandidatePreview candidate : preview.getV2vCandidates()) {
            require(candidate.availableCpu == 750_000_000L, "V2V candidate must use remote VEHICLE CPU");
        }
        for (LiveLocalCandidatePreview candidate : preview.getLocalCandidates()) {
            require(candidate.availableCpu == 1_000_000_000L, "LOCAL candidate must use local CPU");
        }
    }

    private static void verifyConfiguredCellProfileIsDistinctFromRuntimeAccounting() {
        MaGaLiveStateConfig cfg = config(104729L, 3.0, 1_000_000_000L, 750_000_000L);
        require(cfg.hasConfiguredCellProfile(), "configured Cell profile must be present");
        require(cfg.hasCellDiagnosticAccounting(), "diagnostic Cell accounting must be present");
        require(
                "LITERATURE_BASED_CONFIGURED_CELL_PROFILE".equals(cfg.getConfiguredCellProfile().source),
                "configured Cell source must be literature based"
        );
        require(
                "DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES".equals(
                        cfg.getCellDiagnosticAccounting().bandwidthSource
                ),
                "runtime accounting source must remain diagnostic"
        );
        require(
                !cfg.getConfiguredCellProfile().source.equals(cfg.getCellDiagnosticAccounting().bandwidthSource),
                "configured Cell profile and runtime accounting must be distinct concepts"
        );
    }

    private static List<LiveTaskState> generateTasks(
            MaGaLiveStateConfig cfg,
            List<LiveVehicleState> vehicles
    ) {
        LiveSeededPoissonWorkloadGenerator generator =
                new LiveSeededPoissonWorkloadGenerator(cfg.getWorkloadGeneration(), TICK_INTERVAL_NS);
        return generator.generate(TICK_TIME_NS, vehicles);
    }

    private static List<LiveVehicleState> activeVehicles() {
        List<LiveVehicleState> vehicles = new ArrayList<>();
        vehicles.add(new LiveVehicleState("veh_1", TICK_TIME_NS, 0.0, 0.0, 0.0, true, true));
        vehicles.add(new LiveVehicleState("veh_0", TICK_TIME_NS, 10.0, 0.0, 0.0, true, true));
        return vehicles;
    }

    private static String signature(List<LiveTaskState> tasks) {
        StringBuilder builder = new StringBuilder();
        for (LiveTaskState task : tasks) {
            builder
                    .append(task.getTaskId())
                    .append('|')
                    .append(task.getProfileId())
                    .append('|')
                    .append(task.getSourceVehicleId())
                    .append('\n');
        }
        return builder.toString();
    }

    private static MaGaLiveStateConfig config(
            long seed,
            double rate,
            long localCpu,
            long remoteCpu
    ) {
        MaGaLiveStateConfig cfg = new MaGaLiveStateConfig();
        cfg.tickIntervalMs = 1000L;
        cfg.singlehopRadiusMeters = 250.0;
        cfg.localCpuCyclesPerSecond = localCpu;
        cfg.localCpuSource = "HARNESS_LOCAL";
        cfg.remoteVehicleCpuCyclesPerSecond = remoteCpu;
        cfg.remoteVehicleCpuSource = "HARNESS_REMOTE";
        cfg.v2vNominalBandwidthBitsPerSecond = 4_700_000L;
        cfg.v2vBandwidthSource = "HARNESS_V2V";
        cfg.v2vPropagationDelaySeconds = 0.002;
        cfg.configuredCellProfile = configuredCellProfile();
        cfg.cellDiagnosticAccounting = cellDiagnosticAccounting();
        cfg.taskProfiles = new ArrayList<>();
        cfg.workloadGeneration = workload(seed, rate);
        cfg.staticInfrastructure = new MaGaLiveStateConfig.StaticInfrastructure();
        cfg.staticInfrastructure.gateways = new ArrayList<>();
        cfg.staticInfrastructure.edgeNodes = new ArrayList<>();
        cfg.staticInfrastructure.cloudNodes = new ArrayList<>();
        return cfg;
    }

    private static MaGaLiveStateConfig.WorkloadGeneration workload(long seed, double rate) {
        MaGaLiveStateConfig.WorkloadGeneration workload = new MaGaLiveStateConfig.WorkloadGeneration();
        workload.enabled = true;
        workload.mode = "SEEDED_POISSON_PER_ACTIVE_VEHICLE";
        workload.randomSeed = seed;
        workload.startTimeMs = 7000L;
        workload.arrivalRateTasksPerSecondPerActiveVehicle = rate;
        workload.maxGeneratedTasksPerTickPerVehicle = 10;
        workload.profiles = Arrays.asList(
                generatedProfile("light", 0.50, 160_000L, 8_000L, 200_000_000L, 0.5),
                generatedProfile("medium", 0.35, 800_000L, 8_000L, 600_000_000L, 1.0),
                generatedProfile("heavy", 0.15, 8_000_000L, 8_000L, 3_200_000_000L, 4.0)
        );
        return workload;
    }

    private static MaGaLiveStateConfig.GeneratedTaskProfile generatedProfile(
            String profileId,
            double weight,
            long inputSizeBits,
            long outputSizeBits,
            long cpuCycles,
            double deadlineSeconds
    ) {
        MaGaLiveStateConfig.GeneratedTaskProfile profile = new MaGaLiveStateConfig.GeneratedTaskProfile();
        profile.profileId = profileId;
        profile.weight = weight;
        profile.inputSizeBits = inputSizeBits;
        profile.outputSizeBits = outputSizeBits;
        profile.cpuCycles = cpuCycles;
        profile.deadlineSeconds = deadlineSeconds;
        return profile;
    }

    private static MaGaLiveStateConfig.ConfiguredCellProfile configuredCellProfile() {
        MaGaLiveStateConfig.ConfiguredCellProfile profile = new MaGaLiveStateConfig.ConfiguredCellProfile();
        profile.profileId = "CELL_5G_AVEIRO_P50";
        profile.technology = "5G";
        profile.source = "LITERATURE_BASED_CONFIGURED_CELL_PROFILE";
        profile.classification = "CALIBRATED_ABSTRACTION";
        profile.capacityBitsPerSecond = 49_200_000L;
        profile.measuredRttSeconds = 0.0522;
        profile.symmetricOneWayDelaySeconds = 0.0261;
        return profile;
    }

    private static MaGaLiveStateConfig.CellDiagnosticAccounting cellDiagnosticAccounting() {
        MaGaLiveStateConfig.CellDiagnosticAccounting accounting = new MaGaLiveStateConfig.CellDiagnosticAccounting();
        accounting.bucketDurationMs = 1000L;
        accounting.availableFromPolicy = "SAFE_AFTER_TIMESTAMP";
        accounting.bandwidthSource = "DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES";
        accounting.destinationId = "server_0";
        accounting.requestPayloadBytes = 1000L;
        accounting.responsePayloadBytes = 500L;
        accounting.intervalMs = 1000L;
        accounting.initialDelayMs = 1000L;
        accounting.maxUplinkBitrate = "49.2 Mbps";
        accounting.maxDownlinkBitrate = "49.2 Mbps";
        accounting.gatewayPools = new ArrayList<>();
        return accounting;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
