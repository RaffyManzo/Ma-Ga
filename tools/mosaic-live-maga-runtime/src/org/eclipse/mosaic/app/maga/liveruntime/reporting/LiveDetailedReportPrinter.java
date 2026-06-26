package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import config.MaGaConfig;
import io.reporting.AccessLinkDynamicityDiagnosticPrinter;
import io.reporting.AdaptiveWindowDiagnosticPrinter;
import io.reporting.BandwidthPoolDiagnosticPrinter;
import io.reporting.CloudGatewayDiagnosticPrinter;
import io.reporting.DeadlineBestEffortDiagnosticPrinter;
import io.reporting.DeepTemporalWindowDiagnosticPrinter;
import io.reporting.LatencyDiagnosticPrinter;
import io.reporting.MobilityDiagnosticPrinter;
import io.reporting.PopulationReuseDecisionDiagnosticPrinter;
import io.reporting.SystemStateSourceDiagnosticPrinter;
import io.reporting.TemporalTimingDiagnosticPrinter;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

final class LiveDetailedReportPrinter {
    void print(
            PrintStream out,
            LiveReportingSummary summary,
            TemporalWindowResult appliedResult,
            MaGaConfig maGaConfig
    ) {
        liveMetadata(out, summary);
        liveJobSummary(out, summary);
        liveTiming(out, summary);
        liveApplication(out, summary);
        liveStale(out, summary);
        liveSnapshotAudit(out, summary);
        liveAssignment(out, summary);
        liveLocalContention(out, summary);
        liveCell(out, summary);
        liveLimitations(out);
        historicalSections(out, appliedResult, maGaConfig);
    }

    private void liveMetadata(PrintStream out, LiveReportingSummary summary) {
        title(out, "LIVE REPORT METADATA");
        out.println("scenarioName: " + summary.scenarioName);
        out.println("profile: " + summary.profile);
        out.println("experimentalVariant: " + summary.experimentalVariant);
        out.println("effectiveFitnessWeights: " + summary.effectiveFitnessWeights);
        out.println("bridgeDescription: " + summary.bridgeDescription);
        out.println("sourceMode: " + summary.sourceMode);
        out.println("optimizationSourceDescription: " + summary.optimizationSourceDescription);
        out.println("populationReusePolicyDescription: " + summary.populationReusePolicyDescription);
        out.println("construction: native live records; no replay; no GA re-execution");
        out.println();
    }

    private void liveJobSummary(PrintStream out, LiveReportingSummary summary) {
        title(out, "LIVE GA JOB SUMMARY");
        out.println("submitted: " + summary.submitted);
        out.println("completed: " + summary.completed);
        out.println("applied: " + summary.applied);
        out.println("stale discarded: " + summary.staleDiscarded);
        out.println("failed: " + summary.failed);
        out.println("null results: " + summary.nullResults);
        out.println("shutdown in-flight: " + summary.shutdownInFlight);
        out.println();
    }

    private void liveTiming(PrintStream out, LiveReportingSummary summary) {
        title(out, "LIVE GA WALL-CLOCK TIMING");
        out.println("count: " + summary.wallClockTiming.get("count"));
        out.println("min: " + format(summary.wallClockTiming.get("min")));
        out.println("mean: " + format(summary.wallClockTiming.get("mean")));
        out.println("median: " + format(summary.wallClockTiming.get("median")));
        out.println("p95: " + format(summary.wallClockTiming.get("p95")));
        out.println("max: " + format(summary.wallClockTiming.get("max")));
        out.println();
        out.println("jobId | status | runtime | deltaTMaxAtSubmission | timeoutBeforeCompletion | mismatch");
        for (LiveGaJobRecord record : summary.jobRecords) {
            out.println(record.jobId
                    + " | " + blank(record.finalStatus)
                    + " | " + f(record.gaRuntimeWallClockSeconds)
                    + " | " + f(record.deltaTMaxAtSubmissionSeconds)
                    + " | " + record.timeoutDetectedBeforeCompletion
                    + " | " + f(record.deltaTMaxMismatchSeconds));
        }
        out.println();
    }

    private void liveApplication(PrintStream out, LiveReportingSummary summary) {
        title(out, "LIVE RESULT APPLICATION SUMMARY");
        out.println("jobId | window | completedSnapshot | appliedAtSimulationTimeNs | status");
        for (LiveGaJobRecord record : summary.jobRecords) {
            if ("APPLIED".equals(record.finalStatus)) {
                out.println(record.jobId
                        + " | " + record.windowIndex
                        + " | " + record.snapshotId
                        + " | " + record.appliedAtSimulationTimeNs
                        + " | APPLIED");
            }
        }
        out.println();
    }

    private void liveStale(PrintStream out, LiveReportingSummary summary) {
        title(out, "LIVE STALE RESULT SUMMARY");
        out.println("jobId | waitCapSimulationTimeNs | waitCapWallClockNs | completionWallClockNs | freshReoptimization");
        for (LiveGaJobRecord record : summary.jobRecords) {
            if ("STALE_DISCARDED".equals(record.finalStatus)) {
                out.println(record.jobId
                        + " | " + record.waitCapDetectedSimulationTimeNs
                        + " | " + record.waitCapDetectedWallClockNs
                        + " | " + record.completionWallClockNs
                        + " | requested");
            }
        }
        out.println();
    }

    private void liveSnapshotAudit(PrintStream out, LiveReportingSummary summary) {
        title(out, "LIVE SNAPSHOT AUDIT");
        out.println("The live report uses SystemSnapshot objects published in memory by the bridge.");
        out.println("Diagnostic JSON snapshot copies remain secondary and are not read to build this report.");
        out.println("eventsJsonl: " + summary.artifacts.get("eventsJsonl"));
        out.println("stepRecordsJsonl: " + summary.artifacts.get("stepRecordsJsonl"));
        out.println();
    }

    private void liveAssignment(PrintStream out, LiveReportingSummary summary) {
        title(out, "LIVE ASSIGNMENT SUMMARY");
        out.println("LOCAL: " + summary.localAssignments);
        out.println("VEHICLE: " + summary.vehicleAssignments);
        out.println("EDGE: " + summary.edgeAssignments);
        out.println("CLOUD: " + summary.cloudAssignments);
        out.println();
    }

    private void liveLocalContention(
            PrintStream out,
            LiveReportingSummary summary
    ) {
        title(out, "LIVE LOCAL CPU CONTENTION SUMMARY");
        out.println("local task portions: " + summary.localTaskPortions);
        out.println(
                "vehicle-window contention count: "
                        + summary.localContentionVehicleWindows
        );
        out.println(
                "vehicle-window CPU overflow count: "
                        + summary.localCpuOverflowVehicleWindows
        );
        out.println(
                "windows with local contention: "
                        + summary.windowsWithLocalContention
        );
        out.println(
                "windows with local CPU overflow: "
                        + summary.windowsWithLocalCpuOverflow
        );
        out.println(
                "local deadline violations: "
                        + summary.localDeadlineViolations
        );
        out.println(
                "max independent local execution time: "
                        + f(summary.maxIndependentLocalExecutionTimeSeconds)
                        + " s"
        );
        out.println(
                "max contended local completion time: "
                        + f(summary.maxContendedLocalCompletionTimeSeconds)
                        + " s"
        );
        out.println(
                "max local contention delay: "
                        + f(summary.maxLocalContentionDelaySeconds)
                        + " s"
        );
        out.println(
                "max local demand ratio: "
                        + f(summary.maxLocalDemandRatio)
        );
        out.println(
                "max local CPU overflow ratio: "
                        + f(summary.maxLocalCpuOverflowRatio)
        );
        out.println();
    }

    private void liveCell(PrintStream out, LiveReportingSummary summary) {
        title(out, "LIVE CONFIGURED CELL PROFILE VS RUNTIME ACCOUNTING");
        out.println("configuredProfile: " + blank(summary.configuredCellProfile));
        out.println("configuredSource: LITERATURE_BASED_CONFIGURED_CELL_PROFILE when present");
        out.println("runtimeAccountingSource: " + blank(summary.runtimeAccountingSource));
        out.println("distinction: configured Cell profile documents the literature scenario; runtime buckets still come from diagnostic accounting.");
        out.println();
    }

    private void liveLimitations(PrintStream out) {
        title(out, "LIVE REPORT LIMITATIONS");
        out.println("- Built from the true live run records.");
        out.println("- No offline replay is used.");
        out.println("- No MaGaOptimizer or TemporalWindowManager execution is triggered while writing reports.");
        out.println("- Strategy application is diagnostic, not real task execution.");
        out.println("- Remote task execution, migration and checkpointing are still out of scope.");
        out.println();
    }

    private void historicalSections(
            PrintStream out,
            TemporalWindowResult result,
            MaGaConfig maGaConfig
    ) {
        title(out, "HISTORICAL CORE REPORT SECTIONS FROM APPLIED LIVE STEPS");
        if (result.isEmpty()) {
            out.println("No applied TemporalStepResult available.");
            out.println();
            return;
        }
        new DeepTemporalWindowDiagnosticPrinter(maGaConfig, out, 10).print(result);
        new DeadlineBestEffortDiagnosticPrinter(out, 10).print(result);
        new CloudGatewayDiagnosticPrinter(out).print(result, List.of());
        new AccessLinkDynamicityDiagnosticPrinter(maGaConfig.getMobilityConfig(), out, 10).print(result);
        new BandwidthPoolDiagnosticPrinter(out).print(result);
        new MobilityDiagnosticPrinter(maGaConfig, out, 10).print(result);
        new LatencyDiagnosticPrinter(maGaConfig, out, 10).print(result);
        new AdaptiveWindowDiagnosticPrinter(out).print(result);
        new TemporalTimingDiagnosticPrinter(out).print(result);
        new PopulationReuseDecisionDiagnosticPrinter(out).print(result);
        new SystemStateSourceDiagnosticPrinter(out).print(result);
    }

    private static void title(PrintStream out, String value) {
        out.println("============================================================");
        out.println(value);
        out.println("============================================================");
    }

    private static String format(Object value) {
        return value == null ? "null" : f(((Number) value).doubleValue()) + " s";
    }

    private static String f(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.9f", value)
                : "null";
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "not available" : value;
    }
}
