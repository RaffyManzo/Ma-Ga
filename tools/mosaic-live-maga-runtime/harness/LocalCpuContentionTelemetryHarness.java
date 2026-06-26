package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Harness deterministico della telemetria live della contesa CPU locale.
 */
public final class LocalCpuContentionTelemetryHarness {
    private static int assertions;

    private LocalCpuContentionTelemetryHarness() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Expected one output directory argument."
            );
        }

        Path outputRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(outputRoot);

        LiveGaJobRecord record = new LiveGaJobRecord(
                "job_001",
                3,
                "PERIODIC",
                10L,
                20L,
                "snapshot_003",
                3.0,
                4,
                8,
                1.0,
                30L
        );
        record.finalStatus = LiveNativeReportingCollector.STATUS_APPLIED;
        record.completionWallClockNs = 100L;
        record.gaRuntimeWallClockSeconds = 0.25;

        LiveReportingSummary.LiveWindowSummary window =
                new LiveReportingSummary.LiveWindowSummary(
                        "job_001",
                        3,
                        "snapshot_003",
                        3.0,
                        1.5,
                        2,
                        1,
                        0,
                        1,
                        3,
                        2,
                        1,
                        1,
                        1,
                        0.6,
                        1.2,
                        0.6,
                        1.2,
                        0.2,
                        LiveNativeReportingCollector.STATUS_APPLIED
                );

        LiveReportingSummary summary = LiveReportingSummary.from(
                "scenario",
                "profile",
                "FULL_MA_GA",
                "LIVE_CAUSAL_MOSAIC_SNAPSHOT_BRIDGE",
                "standard adaptive population reuse policy",
                "bridge",
                "LIVE",
                "cell-profile",
                "runtime-accounting",
                List.of(record),
                List.of(window),
                List.of(),
                MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC),
                outputRoot
        );

        verifySummary(summary);
        verifyCsv(outputRoot, window, record);
        verifyDetailedReports(outputRoot, summary);

        System.out.println(
                "LOCAL_CPU_CONTENTION_TELEMETRY_HARNESS_PASSED assertions="
                        + assertions
        );
    }

    private static void verifySummary(LiveReportingSummary summary) {
        require(summary.localAssignments == 2, "local assignments");
        require(summary.vehicleAssignments == 1, "vehicle assignments");
        require(summary.cloudAssignments == 1, "cloud assignments");
        require(summary.localTaskPortions == 3, "local task portions");
        require(
                summary.localContentionVehicleWindows == 1,
                "contention vehicle-window count"
        );
        require(
                summary.localCpuOverflowVehicleWindows == 1,
                "overflow vehicle-window count"
        );
        require(
                summary.windowsWithLocalContention == 1,
                "windows with contention"
        );
        require(
                summary.windowsWithLocalCpuOverflow == 1,
                "windows with overflow"
        );
        require(
                summary.localDeadlineViolations == 1,
                "local deadline violations"
        );
        requireClose(
                summary.maxIndependentLocalExecutionTimeSeconds,
                0.6,
                "independent local max"
        );
        requireClose(
                summary.maxContendedLocalCompletionTimeSeconds,
                1.2,
                "contended local max"
        );
        requireClose(
                summary.maxLocalContentionDelaySeconds,
                0.6,
                "contention delay max"
        );
        requireClose(
                summary.maxLocalDemandRatio,
                1.2,
                "demand ratio max"
        );
        requireClose(
                summary.maxLocalCpuOverflowRatio,
                0.2,
                "overflow ratio max"
        );
    }

    private static void verifyCsv(
            Path outputRoot,
            LiveReportingSummary.LiveWindowSummary window,
            LiveGaJobRecord record
    ) throws Exception {
        try (LiveReportingJsonlWriter writer =
                new LiveReportingJsonlWriter(outputRoot)) {
            writer.writeWindowCsv(
                    new LiveReportingJsonlWriter.LiveWindowCsvRecord(
                            window.jobId,
                            window.windowIndex,
                            window.snapshotId,
                            window.snapshotTimeSeconds,
                            record.submissionSimulationTimeNs,
                            record.gaRuntimeWallClockSeconds,
                            record.deltaTMaxAtSubmissionSeconds,
                            window.fitness,
                            window.localAssignments,
                            window.vehicleAssignments,
                            window.edgeAssignments,
                            window.cloudAssignments,
                            window.localTaskPortions,
                            window.vehiclesWithLocalWorkload,
                            window.vehiclesWithLocalContention,
                            window.vehiclesWithLocalCpuOverflow,
                            window.localDeadlineViolations,
                            window.maxIndependentLocalExecutionTimeSeconds,
                            window.maxContendedLocalCompletionTimeSeconds,
                            window.maxLocalContentionDelaySeconds,
                            window.maxLocalDemandRatio,
                            window.maxLocalCpuOverflowRatio,
                            window.status
                    ),
                    true
            );
        }

        String csv = Files.readString(
                outputRoot.resolve("live_applied_window_records.csv"),
                StandardCharsets.UTF_8
        );
        require(
                csv.contains("maxLocalDemandRatio"),
                "CSV demand ratio header"
        );
        require(
                csv.contains("maxLocalCpuOverflowRatio"),
                "CSV overflow header"
        );
        require(
                csv.contains("maxContendedLocalCompletionTimeSeconds"),
                "CSV contended completion header"
        );
        require(
                csv.contains(",3,2,1,1,1,0.600000000,1.200000000,"),
                "CSV contention values"
        );
    }

    private static void verifyDetailedReports(
            Path outputRoot,
            LiveReportingSummary summary
    ) throws Exception {
        new LiveDetailedReportWriter().write(
                outputRoot,
                summary,
                List.of(),
                MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC)
        );

        String json = Files.readString(
                outputRoot.resolve("live_detailed_execution_report.json"),
                StandardCharsets.UTF_8
        );
        String markdown = Files.readString(
                outputRoot.resolve("live_detailed_execution_report.md"),
                StandardCharsets.UTF_8
        );
        String txt = Files.readString(
                outputRoot.resolve("live_detailed_execution_report.txt"),
                StandardCharsets.UTF_8
        );

        require(
                json.contains("\"maxLocalDemandRatio\": 1.2"),
                "JSON demand ratio"
        );
        require(
                json.contains("\"windowsWithLocalContention\": 1"),
                "JSON contention windows"
        );
        require(
                markdown.contains("## Local CPU Contention"),
                "Markdown contention section"
        );
        require(
                markdown.contains("Max local CPU overflow ratio"),
                "Markdown overflow metric"
        );
        require(
                txt.contains("LIVE LOCAL CPU CONTENTION SUMMARY"),
                "TXT contention section"
        );
        require(
                txt.contains("max local demand ratio: 1.200000000"),
                "TXT demand ratio value"
        );
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new IllegalStateException(
                    "Assertion failed: " + message
            );
        }
    }

    private static void requireClose(
            double actual,
            double expected,
            String message
    ) {
        require(
                Math.abs(actual - expected) <= 1.0E-9,
                message + " actual=" + actual + " expected=" + expected
        );
    }
}
