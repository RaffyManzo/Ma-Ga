package org.eclipse.mosaic.app.maga.liveruntime;

import config.MaGaConfig;
import config.fitness.FitnessWeights;
import config.ga.GaParameterScalingMode;
import model.bandwidth.BandwidthPoolType;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;
import window.dynamicity.DynamicityBreakdown;
import window.dynamicity.DynamicityLevel;
import window.population.PopulationReuseDecision;
import window.population.PopulationReuseDecisionPolicy;
import window.population.PopulationReuseMode;
import window.source.LocalOnlySystemStateSource;
import window.source.SequentialSnapshotReplaySource;
import window.source.SystemStateObservation;
import window.source.SystemStateRequest;
import window.trigger.ReoptimizationTrigger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class G02BExperimentalVariantHarness {
    private static final double EPSILON = 1.0E-9;

    private G02BExperimentalVariantHarness() {
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0
                ? "tmp/g02b-harness-run-" + System.nanoTime()
                : args[0]);
        Files.createDirectories(output);

        verifyConfigParsing(output);
        verifyLocalOnlySource();
        verifyPopulationReusePolicies();
        verifyNoMobilityPenaltyWeights();
        verifyFullMaGaSmoke();

        System.out.println("G02B_EXPERIMENTAL_VARIANT_HARNESS_PASSED");
    }

    private static void verifyConfigParsing(Path output) throws Exception {
        require(loadConfig(output, "missing", null).getExperimentalVariant()
                        == MaGaExperimentalVariant.FULL_MA_GA,
                "missing experimentalVariant must default to FULL_MA_GA");
        require(loadConfig(output, "blank", "").getExperimentalVariant()
                        == MaGaExperimentalVariant.FULL_MA_GA,
                "blank experimentalVariant must default to FULL_MA_GA");
        require(loadConfig(output, "case-insensitive", "local_only").getExperimentalVariant()
                        == MaGaExperimentalVariant.LOCAL_ONLY,
                "experimentalVariant parsing must be case-insensitive");

        try {
            loadConfig(output, "unknown", "not_a_variant");
            throw new AssertionError("unknown experimentalVariant should fail");
        } catch (IllegalArgumentException e) {
            require(e.getMessage().contains("experimentalVariant"),
                    "unknown variant error must mention experimentalVariant");
        }
    }

    private static MaGaLiveRuntimeConfig loadConfig(
            Path output,
            String name,
            String variant
    ) throws Exception {
        Path dir = output.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve(MaGaLiveRuntimeConfig.CONFIG_FILE_NAME),
                configJson(variant)
        );
        return MaGaLiveRuntimeConfig.load(dir.toFile());
    }

    private static String configJson(String variant) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"scenarioName\": \"HarnessScenario\",\n");
        builder.append("  \"coordinatorTickIntervalMs\": 100,\n");
        builder.append("  \"initialOptimizationDelayMs\": 100,\n");
        builder.append("  \"gaPollingIntervalMs\": 50,\n");
        builder.append("  \"singleInFlightGaOnly\": true,\n");
        builder.append("  \"discardLateResult\": true,\n");
        builder.append("  \"keepLastAppliedStrategyWhileRunning\": true,\n");
        builder.append("  \"freshReoptimizationAfterTimeout\": true,\n");
        builder.append("  \"runtimeTraceEnabled\": false,\n");
        builder.append("  \"diagnosticArtificialGaDelayMs\": 0,\n");
        builder.append("  \"temporalInitialWindowSeconds\": 1.0,\n");
        builder.append("  \"configuredGaRuntimeEstimateSeconds\": 0.2,\n");
        builder.append("  \"configuredMaxWindowSeconds\": 5.0,\n");
        builder.append("  \"deltaTMaxComparisonEpsilonSeconds\": 0.001,\n");
        builder.append("  \"publishedSnapshotCopyLimit\": 1,\n");
        builder.append("  \"nativeLiveDetailedReportingEnabled\": true,\n");
        builder.append("  \"nativeLiveDetailedReportPrintToConsole\": false,\n");
        builder.append("  \"gaParameterScalingMode\": \"adaptive\"");
        if (variant != null) {
            builder.append(",\n");
            builder.append("  \"experimentalVariant\": \"").append(variant).append("\"");
        }
        builder.append("\n}\n");
        return builder.toString();
    }

    private static void verifyLocalOnlySource() {
        SystemSnapshot snapshot = fixtureSnapshot();
        LocalOnlySystemStateSource source = new LocalOnlySystemStateSource(
                new SequentialSnapshotReplaySource(List.of(snapshot), "fixture source")
        );
        SystemStateObservation observation = source.nextObservation(new SystemStateRequest(
                0,
                ReoptimizationTrigger.firstRun(snapshot.getTimeSeconds()),
                snapshot.getTimeSeconds(),
                1.0
        )).orElseThrow();

        require(observation.getObservedSnapshot() == snapshot,
                "observed snapshot must remain the delegate snapshot");
        SystemSnapshot optimization = observation.getOptimizationSnapshot();
        require(optimization != snapshot,
                "optimization snapshot must be a separate local-only view");
        require(optimization.getCandidateNodes().size() == 1,
                "optimization snapshot must keep only local candidates");
        require(optimization.getCandidateNodes().get(0).getType() == NodeType.LOCAL,
                "remaining candidate must be LOCAL");
        require(optimization.getVehicles().size() == snapshot.getVehicles().size(),
                "vehicles must be preserved");
        require(optimization.getTasks().size() == snapshot.getTasks().size(),
                "tasks must be preserved");
        require(optimization.getAccessGateways().size() == snapshot.getAccessGateways().size(),
                "gateways must be preserved");
        require(optimization.getAccessLinks().size() == snapshot.getAccessLinks().size(),
                "access links must be preserved");
        require(optimization.getBandwidthPools().size() == snapshot.getBandwidthPools().size(),
                "bandwidth pools must be preserved");
        require(observation.getSourceDescription().startsWith("local-only("),
                "source description must identify the local-only decorator");
    }

    private static SystemSnapshot fixtureSnapshot() {
        return new SystemSnapshot(
                "snapshot_g02b",
                12.0,
                List.of(new VehicleSnapshot("veh_0", 0.0, 0.0, 0.0, 1_000_000_000.0)),
                List.of(new TaskInstance("task_0", "veh_0", 1000.0, 100.0, 2000.0, 1.0)),
                List.of(
                        new NodeCandidate("local_0", "veh_0", "veh_0", NodeType.LOCAL,
                                1_000_000_000.0, 0.0, 0.0, null, null, null),
                        new NodeCandidate("vehicle_0", "veh_0", "veh_1", NodeType.VEHICLE,
                                1_000_000_000.0, 1_000_000.0, 0.01, 1.0, 1.0, 250.0),
                        new NodeCandidate("edge_0", "veh_0", "rsu_0", NodeType.EDGE,
                                2_000_000_000.0, 2_000_000.0, 0.02, 10.0, 10.0, 250.0),
                        new NodeCandidate("cloud_0", "veh_0", "cloud", NodeType.CLOUD,
                                5_000_000_000.0, 5_000_000.0, 0.05, null, null, null)
                ),
                List.of(new AccessGatewaySnapshot("rsu_0", "RSU", 10.0, 10.0, 250.0, "pool_gateway")),
                List.of(new AccessLinkSnapshot("link_0", "veh_0", "rsu_0", true, true)),
                List.of(new BandwidthPoolSnapshot("pool_gateway", BandwidthPoolType.GATEWAY, 2_000_000.0))
        );
    }

    private static void verifyPopulationReusePolicies() {
        PopulationReuseDecisionPolicy standard = new PopulationReuseDecisionPolicy();
        PopulationReuseDecision standardDecision = standard.decide(
                stableWarmBreakdown(),
                null,
                true,
                false
        );
        require(standardDecision.getAppliedMode() == PopulationReuseMode.WARM_START,
                "standard policy must keep warm-start behavior unchanged");

        PopulationReuseDecisionPolicy forced = PopulationReuseDecisionPolicy.forcedColdStartNoReuse(
                config.window.TemporalWindowConfig.defaultConfig()
        );
        PopulationReuseDecision first = forced.decide(
                DynamicityBreakdown.firstRun("first", 0.0),
                null,
                false,
                false
        );
        require(first.getAppliedMode() == PopulationReuseMode.FIRST_RUN,
                "forced-cold policy must keep FIRST_RUN on first window");

        PopulationReuseDecision next = forced.decide(
                stableWarmBreakdown(),
                null,
                true,
                false
        );
        require(next.getAppliedMode() == PopulationReuseMode.COLD_START,
                "forced-cold policy must cold-start after first window");
        require(next.getReason().contains("G02B"),
                "forced-cold diagnostic reason must mention G02B");
    }

    private static DynamicityBreakdown stableWarmBreakdown() {
        return new DynamicityBreakdown(
                "previous",
                "current",
                0.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                DynamicityLevel.STABLE,
                PopulationReuseMode.WARM_START
        );
    }

    private static void verifyNoMobilityPenaltyWeights() {
        MaGaConfig standard = MaGaConfig.defaultConfig(GaParameterScalingMode.ADAPTIVE);
        MaGaConfig adjusted = MaGaExperimentalVariant.NO_MOBILITY_PENALTY.applyTo(standard);
        FitnessWeights original = standard.getFitnessWeights();
        FitnessWeights weights = adjusted.getFitnessWeights();

        requireClose(weights.getMobilityPenaltyWeight(), 0.0,
                "NO_MOBILITY_PENALTY must set wM to zero");
        requireClose(
                weights.getCompletionTimeWeight()
                        + weights.getCommunicationLatencyWeight()
                        + weights.getMobilityPenaltyWeight()
                        + weights.getResourcePenaltyWeight(),
                1.0,
                "NO_MOBILITY_PENALTY weights must sum to 1"
        );
        requireClose(
                weights.getCompletionTimeWeight() / weights.getCommunicationLatencyWeight(),
                original.getCompletionTimeWeight() / original.getCommunicationLatencyWeight(),
                "wT/wL ratio must be preserved"
        );
        requireClose(
                weights.getCompletionTimeWeight() / weights.getResourcePenaltyWeight(),
                original.getCompletionTimeWeight() / original.getResourcePenaltyWeight(),
                "wT/wR ratio must be preserved"
        );
        require(adjusted.getPenaltyConfig() == standard.getPenaltyConfig(),
                "PenaltyConfig must be copied unchanged");
        require(adjusted.getNormalizationConfig() == standard.getNormalizationConfig(),
                "NormalizationConfig must be copied unchanged");
        require(adjusted.getGeneticAlgorithmConfig() == standard.getGeneticAlgorithmConfig(),
                "GeneticAlgorithmConfig must be copied unchanged");
        require(adjusted.getMobilityConfig() == standard.getMobilityConfig(),
                "MobilityConfig must be copied unchanged");
        require(adjusted.getGaParameterScalingMode() == standard.getGaParameterScalingMode(),
                "GaParameterScalingMode must be copied unchanged");
    }

    private static void verifyFullMaGaSmoke() {
        MaGaConfig standard = MaGaConfig.defaultConfig(GaParameterScalingMode.ADAPTIVE);
        require(MaGaExperimentalVariant.FULL_MA_GA.applyTo(standard) == standard,
                "FULL_MA_GA must use the standard MA-GA configuration path");
    }

    private static void requireClose(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > EPSILON) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
