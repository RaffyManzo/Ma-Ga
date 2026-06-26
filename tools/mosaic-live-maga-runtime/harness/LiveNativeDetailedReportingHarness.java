package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import ga.core.MaGaResult;
import ga.core.StopReason;
import ga.fitness.breakdown.EvaluationBreakdown;
import model.genetic.Chromosome;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;
import window.dynamicity.DynamicityBreakdown;
import window.population.PopulationReuseMode;
import window.state.TemporalStepResult;
import window.trigger.ReoptimizationTrigger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LiveNativeDetailedReportingHarness {
    private LiveNativeDetailedReportingHarness() {
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "out/harness-reporting-run" : args[0]);
        recreate(output);

        LiveNativeReportingCollector collector = new LiveNativeReportingCollector(
                output,
                "HarnessScenario",
                "normal",
                "FULL_MA_GA",
                "LIVE_CAUSAL_MOSAIC_SNAPSHOT_BRIDGE",
                "MOSAIC_LIVE",
                "LIVE_CAUSAL_MOSAIC_SNAPSHOT_BRIDGE",
                "standard adaptive population reuse policy",
                "profileId=CELL_5G_AVEIRO_P50|source=LITERATURE_BASED_CONFIGURED_CELL_PROFILE",
                "DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES"
        );

        TemporalStepResult appliedStep = step("snapshot_applied", 1.0, 0);
        String appliedJob = collector.nextJobId();
        collector.recordSubmitted(appliedJob, 0, "FIRST_RUN", 1_000_000_000L, 10L, "snapshot_applied", 1.0, 0, 1, 0.2, 200_000_010L);
        collector.recordCompletedWithinBound(appliedJob, appliedStep, 100L, 0.000000090, 0.2, 0.0);
        collector.recordApplied(appliedJob, appliedStep, 1_100_000_000L);

        TemporalStepResult staleStep = step("snapshot_stale", 2.0, 1);
        String staleJob = collector.nextJobId();
        collector.recordSubmitted(staleJob, 1, "SCHEDULED_WINDOW_EXPIRATION", 2_000_000_000L, 200L, "snapshot_stale", 2.0, 0, 1, 0.1, 300L);
        collector.recordWaitCapReached(staleJob, 2_100_000_000L, 350L);
        collector.recordStaleDiscarded(staleJob, staleStep, 2_200_000_000L, 500L, 0.3, 0.1, 0.0);
        collector.recordFreshReoptimizationRequested(staleJob, 2_200_000_000L);

        String failedJob = collector.nextJobId();
        collector.recordSubmitted(failedJob, 2, "FRESH_REOPTIMIZATION_REQUESTED", 3_000_000_000L, 600L, "snapshot_failed", 3.0, 0, 1, 0.1, 700L);
        collector.recordFailed(failedJob, 3_100_000_000L, 700L, 0.1, new IllegalStateException("fixture failure"));

        String nullJob = collector.nextJobId();
        collector.recordSubmitted(nullJob, 3, "SCHEDULED_WINDOW_EXPIRATION", 4_000_000_000L, 800L, "snapshot_null", 4.0, 0, 1, 0.1, 900L);
        collector.recordNullStepResult(nullJob, 4_100_000_000L, 900L, 0.1);

        LiveDetailedReportWriter.LiveDetailedReportArtifacts artifacts =
                collector.writeFinalReports(MaGaConfig.defaultConfig(GaParameterScalingMode.ADAPTIVE));
        collector.close();

        require(Files.isRegularFile(output.resolve("live-reporting/live_ga_job_events.jsonl")), "events jsonl missing");
        require(Files.isRegularFile(output.resolve("live-reporting/live_temporal_step_records.jsonl")), "step jsonl missing");
        require(Files.isRegularFile(output.resolve("live-reporting/live_applied_window_records.csv")), "applied csv missing");
        require(Files.isRegularFile(output.resolve("live-reporting/live_discarded_window_records.csv")), "discarded csv missing");
        require(Files.isRegularFile(artifacts.getTxt()), "txt report missing");
        require(Files.isRegularFile(artifacts.getMarkdown()), "markdown report missing");
        require(Files.isRegularFile(artifacts.getJson()), "json report missing");

        String events = Files.readString(output.resolve("live-reporting/live_ga_job_events.jsonl"));
        require(events.contains("SUBMITTED"), "SUBMITTED event missing");
        require(events.contains("COMPLETED_WITHIN_BOUND"), "completion event missing");
        require(events.contains("APPLIED"), "APPLIED event missing");
        require(events.contains("STALE_DISCARDED"), "STALE_DISCARDED event missing");
        require(events.contains("FAILED"), "FAILED event missing");
        require(events.contains("NULL_STEP_RESULT"), "NULL_STEP_RESULT event missing");
        require(events.contains("FRESH_REOPTIMIZATION_REQUESTED"), "fresh reoptimization event missing");

        String steps = Files.readString(output.resolve("live-reporting/live_temporal_step_records.jsonl"));
        require(countLines(steps) == 2, "step records must include applied and stale results only");
        require(steps.contains("snapshot_applied"), "applied step missing");
        require(steps.contains("snapshot_stale"), "stale step missing");
        require(steps.contains("\"localContention\""),
                "local contention block missing from step JSONL");

        String appliedCsv = Files.readString(output.resolve("live-reporting/live_applied_window_records.csv"));
        String discardedCsv = Files.readString(output.resolve("live-reporting/live_discarded_window_records.csv"));
        require(appliedCsv.contains("snapshot_applied"), "applied csv missing applied snapshot");
        require(discardedCsv.contains("snapshot_stale"), "discarded csv missing stale snapshot");
        require(appliedCsv.contains("maxLocalDemandRatio"),
                "applied CSV local demand ratio header missing");
        require(appliedCsv.contains("maxLocalCpuOverflowRatio"),
                "applied CSV local overflow header missing");

        String txt = Files.readString(artifacts.getTxt());
        require(txt.contains("LIVE GA JOB SUMMARY"), "live summary section missing");
        require(txt.contains("experimentalVariant: FULL_MA_GA"), "txt experimental variant missing");
        require(txt.contains("effectiveFitnessWeights:"), "txt fitness weights missing");
        require(txt.contains("optimizationSourceDescription:"), "txt optimization source missing");
        require(txt.contains("populationReusePolicyDescription:"), "txt reuse policy missing");
        require(txt.contains("HISTORICAL CORE REPORT SECTIONS FROM APPLIED LIVE STEPS"), "historical applied-step section missing");
        require(!txt.contains("snapshot_stale | APPLIED"), "stale step must not be reported as applied");
        require(txt.contains("LIVE LOCAL CPU CONTENTION SUMMARY"),
                "txt local contention section missing");

        String markdown = Files.readString(artifacts.getMarkdown());
        require(markdown.contains("Experimental variant: `FULL_MA_GA`"), "markdown experimental variant missing");
        require(markdown.contains("Effective fitness weights:"), "markdown fitness weights missing");
        require(markdown.contains("Optimization source:"), "markdown optimization source missing");
        require(markdown.contains("Population reuse policy:"), "markdown reuse policy missing");
        require(markdown.contains("## Local CPU Contention"),
                "markdown local contention section missing");

        String json = Files.readString(artifacts.getJson());
        require(json.contains("\"experimentalVariant\": \"FULL_MA_GA\""), "json experimental variant missing");
        require(json.contains("\"effectiveFitnessWeights\""), "json fitness weights missing");
        require(json.contains("\"optimizationSourceDescription\""), "json optimization source missing");
        require(json.contains("\"populationReusePolicyDescription\""), "json reuse policy missing");
        require(json.contains("\"applied\": 1"), "json applied count mismatch");
        require(json.contains("\"staleDiscarded\": 1"), "json stale count mismatch");
        require(json.contains("\"failed\": 1"), "json failed count mismatch");
        require(json.contains("\"nullResults\": 1"), "json null count mismatch");
        require(json.contains("\"maxLocalDemandRatio\""),
                "json local demand ratio missing");
        require(json.contains("\"windowsWithLocalContention\""),
                "json contention window count missing");

        System.out.println("PHASE14C3R_REPORTING_HARNESS_PASSED");
    }

    private static TemporalStepResult step(String snapshotId, double timeSeconds, int windowIndex) {
        SystemSnapshot snapshot = new SystemSnapshot(
                snapshotId,
                timeSeconds,
                List.of(new VehicleSnapshot("veh_0", 0.0, 0.0, 0.0, 1_000_000_000.0)),
                List.of(),
                List.of(new NodeCandidate(
                        "local_for_veh_0",
                        "veh_0",
                        "veh_0",
                        NodeType.LOCAL,
                        1_000_000_000.0,
                        0.0,
                        0.0,
                        null,
                        null,
                        null,
                        null
                ))
        );
        EvaluationBreakdown evaluation = new EvaluationBreakdown(
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        MaGaResult result = new MaGaResult(
                snapshotId,
                timeSeconds,
                new Chromosome(List.of(), 1.0),
                evaluation,
                1,
                StopReason.EMPTY_TASK_SET,
                1.0,
                1.0,
                List.of(),
                List.of(new Chromosome(List.of(), 1.0))
        );
        return new TemporalStepResult(
                windowIndex,
                windowIndex == 0
                        ? ReoptimizationTrigger.firstRun(timeSeconds)
                        : ReoptimizationTrigger.scheduledExpiration(timeSeconds),
                0.0,
                timeSeconds,
                snapshot,
                DynamicityBreakdown.firstRun(snapshotId, timeSeconds),
                PopulationReuseMode.FIRST_RUN,
                0,
                0,
                result
        );
    }

    private static void recreate(Path output) throws Exception {
        if (Files.exists(output)) {
            try (var stream = Files.walk(output)) {
                for (Path path : stream.sorted((a, b) -> b.compareTo(a)).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(output);
    }

    private static int countLines(String text) {
        if (text.isBlank()) {
            return 0;
        }
        return text.split("\\R").length;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
