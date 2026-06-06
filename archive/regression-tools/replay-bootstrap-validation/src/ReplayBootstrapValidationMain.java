import app.AdaptiveWindowMain;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import config.mobility.MobilityConfig;
import ga.core.MaGaOptimizer;
import ga.core.MaGaResult;
import ga.core.StopReason;
import io.reporting.AccessLinkDynamicityDiagnosticPrinter;
import io.reporting.CloudGatewayDiagnosticPrinter;
import io.snapshot.JsonSnapshotFolderLoader;
import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;
import window.dynamicity.DynamicityBreakdown;
import window.population.PopulationReuseMode;
import window.source.SequentialSnapshotReplaySource;
import window.source.SystemStateObservation;
import window.source.SystemStateRequest;
import window.source.TimeIndexedSnapshotReplaySource;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;
import window.trigger.ReoptimizationTrigger;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReplayBootstrapValidationMain {
    private static final String PHASE =
            "10J_PRE2_OPTIONAL_GATEWAY_REPORTING_ALIGNMENT";
    private static final String EMPTY_TASK_POLICY =
            "ALLOW_EMPTY_CANDIDATES_WHEN_TASK_SET_IS_EMPTY";
    private static final String REPLAY_START_POLICY =
            "FIRST_AVAILABLE_SNAPSHOT_TIME";
    private static final String TIME_INDEXED_POLICY =
            "LATEST_SNAPSHOT_AT_OR_BEFORE_REQUESTED_TIME";
    private static final String REPORTING_OPTIONAL_GATEWAY_POLICY =
            "DESCRIPTIVE_REPORTING_USES_OPTIONAL_ACTIVE_ACCESS_LINK";
    private static final String MISSING_GATEWAY_QUALITY_POLICY =
            "ZERO_QUALITY_WITHOUT_ACTIVE_ACCESS_LINK";
    private static final String MISSING_GATEWAY_RENDERING_POLICY =
            "DASH_FOR_UNAVAILABLE_METRICS";
    private static final String GATEWAY_TRANSITION_POLICY =
            "DISTINGUISH_COVERAGE_GAIN_COVERAGE_LOSS_AND_HANDOVER";
    private static final String WARNING_BASELINE =
            "WARNING_DIAGNOSTIC_BASELINE_NOT_STRESSING_OFFLOADING";
    private static final String WARNING_JSON_TIME =
            "WARNING_JSON_TIME_FULL_HORIZON_NOT_YET_VALIDATED";

    private ReplayBootstrapValidationMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: ReplayBootstrapValidationMain <snapshotFolder> <phase10iValidationJson> <validationOutJson>"
            );
        }

        Path snapshotFolder = Path.of(args[0]);
        Path phase10iValidationFile = Path.of(args[1]);
        Path validationOutFile = Path.of(args[2]);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> phase10i = mapper.readValue(
                phase10iValidationFile.toFile(),
                new TypeReference<>() { }
        );
        String sourceRun = String.valueOf(phase10i.get("sourceRun"));

        List<SystemSnapshot> snapshots =
                new JsonSnapshotFolderLoader().load(snapshotFolder.toString());
        if (snapshots.size() < 2) {
            throw new IllegalArgumentException(
                    "At least two snapshots are required for replay bootstrap validation."
            );
        }

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Boolean> outcomes = new LinkedHashMap<>();

        outcomes.put("emptySnapshotOptimizerStatus", caseEmptySnapshotOptimizer(snapshots));
        outcomes.put("nonEmptyTaskWithoutCandidatesRejected", caseTaskWithoutCandidates(snapshots));
        outcomes.put("timeIndexedBeforeFirstSnapshotReturnsEmpty", caseTimeIndexedBeforeFirst(snapshots));
        outcomes.put("timeIndexedExactFirstSnapshotResolved", caseTimeIndexedExactFirst(snapshots));
        outcomes.put("sequentialEmptySnapshotPreserved", caseSequentialPreservesEmpty(snapshots));
        outcomes.put("uncoveredToUncoveredValidated", caseUncoveredToUncovered());
        outcomes.put("coveredToUncoveredValidated", caseCoveredToUncovered());
        outcomes.put("uncoveredToCoveredValidated", caseUncoveredToCovered());
        outcomes.put("gatewayToGatewayHandoverValidated", caseGatewayToGatewayHandover());
        outcomes.put("cloudConfigurationAggregateValidated", caseCloudConfigurationAggregate());

        SmokeResult jsonSequenceReplay = runSmoke(
                "JSON_SEQUENCE",
                "CONFIGURED_RUNTIME",
                snapshotFolder.toString(),
                "36"
        );
        outcomes.put("jsonSequenceReplayStatus", jsonSequenceReplay.passed());

        SmokeResult jsonTimeSmoke = runSmoke(
                "JSON_TIME",
                "OBSERVED_RUNTIME",
                snapshotFolder.toString(),
                "36"
        );
        outcomes.put("jsonTimeSmokeStatus", jsonTimeSmoke.passed());

        for (Map.Entry<String, Boolean> entry : outcomes.entrySet()) {
            if (!entry.getValue()) {
                errors.add(entry.getKey() + " failed.");
            }
        }

        int testsExecuted = outcomes.size();
        int testsPassed = (int) outcomes.values().stream().filter(Boolean::booleanValue).count();
        int testsFailed = testsExecuted - testsPassed;

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("sourceRun", sourceRun);
        diagnostics.put("phase", PHASE);
        diagnostics.put("reportingOptionalGatewayPolicy", REPORTING_OPTIONAL_GATEWAY_POLICY);
        diagnostics.put("missingGatewayQualityPolicy", MISSING_GATEWAY_QUALITY_POLICY);
        diagnostics.put("missingGatewayMetricRenderingPolicy", MISSING_GATEWAY_RENDERING_POLICY);
        diagnostics.put("gatewayTransitionPolicy", GATEWAY_TRANSITION_POLICY);
        diagnostics.put("emptyTaskSnapshotPolicy", EMPTY_TASK_POLICY);
        diagnostics.put("replayStartTimePolicy", REPLAY_START_POLICY);
        diagnostics.put("timeIndexedNoLookAheadPolicy", TIME_INDEXED_POLICY);
        diagnostics.put("classesInspected", List.of(
                "src/ga/core/MaGaOptimizer.java",
                "src/ga/core/MaGaResult.java",
                "src/ga/core/StopReason.java",
                "src/app/AdaptiveWindowMain.java",
                "src/window/core/TemporalWindowManager.java",
                "src/window/source/TimeIndexedSnapshotReplaySource.java",
                "src/window/source/SequentialSnapshotReplaySource.java",
                "src/window/source/SystemStateSourceFactory.java",
                "src/io/snapshot/JsonSnapshotFolderLoader.java",
                "src/io/reporting/AdaptiveWindowReportPrinter.java",
                "src/io/reporting/AccessLinkDynamicityDiagnosticPrinter.java",
                "src/io/reporting/CloudGatewayDiagnosticPrinter.java",
                "src/io/reporting/BandwidthPoolDiagnosticPrinter.java",
                "src/io/reporting/MobilityDiagnosticPrinter.java",
                "src/io/reporting/LatencyDiagnosticPrinter.java",
                "src/io/reporting/AdaptiveWindowDiagnosticPrinter.java",
                "src/io/reporting/TemporalTimingDiagnosticPrinter.java",
                "src/io/reporting/PopulationReuseDecisionDiagnosticPrinter.java",
                "src/io/reporting/SystemStateSourceDiagnosticPrinter.java",
                "src/io/reporting/CandidateFilteringPrinter.java",
                "src/model/mobility/AccessLinkMetricsEstimator.java",
                "src/model/mobility/AccessLinkResolver.java",
                "src/model/mobility/AccessLinkMetrics.java",
                "src/window/dynamicity/calculator/LinkDynamicityCalculator.java",
                "src/window/timing/CoverageReferenceCalculator.java"
        ));
        diagnostics.put("classesModified", List.of(
                "src/io/reporting/AccessLinkDynamicityDiagnosticPrinter.java",
                "src/io/reporting/CloudGatewayDiagnosticPrinter.java"
        ));
        diagnostics.put("strictConsumersFound", List.of(
                "src/model/mobility/AccessLinkMetricsEstimator.java#estimateActiveLink",
                "src/model/mobility/AccessLinkResolver.java#requireActiveAccessLink",
                "src/model/mobility/CoverageEstimator.java#estimateActiveLink",
                "src/window/prefilter/CandidatePrefilter.java#estimateActiveLink",
                "src/io/reporting/AccessLinkDynamicityDiagnosticPrinter.java#estimateActiveLink (updated)"
        ));
        diagnostics.put("optionalReportingConsumersUpdated", List.of(
                "src/io/reporting/AccessLinkDynamicityDiagnosticPrinter.java",
                "src/io/reporting/CloudGatewayDiagnosticPrinter.java"
        ));
        diagnostics.put("testsExecuted", testsExecuted);
        diagnostics.put("testsPassed", testsPassed);
        diagnostics.put("testsFailed", testsFailed);
        diagnostics.put("emptySnapshotOptimizerStatus", outcomes.get("emptySnapshotOptimizerStatus"));
        diagnostics.put(
                "nonEmptyTaskWithoutCandidatesRejected",
                outcomes.get("nonEmptyTaskWithoutCandidatesRejected")
        );
        diagnostics.put(
                "timeIndexedBeforeFirstSnapshotReturnsEmpty",
                outcomes.get("timeIndexedBeforeFirstSnapshotReturnsEmpty")
        );
        diagnostics.put(
                "timeIndexedExactFirstSnapshotResolved",
                outcomes.get("timeIndexedExactFirstSnapshotResolved")
        );
        diagnostics.put(
                "sequentialEmptySnapshotPreserved",
                outcomes.get("sequentialEmptySnapshotPreserved")
        );
        diagnostics.put("uncoveredToUncoveredValidated", outcomes.get("uncoveredToUncoveredValidated"));
        diagnostics.put("coveredToUncoveredValidated", outcomes.get("coveredToUncoveredValidated"));
        diagnostics.put("uncoveredToCoveredValidated", outcomes.get("uncoveredToCoveredValidated"));
        diagnostics.put("gatewayToGatewayHandoverValidated", outcomes.get("gatewayToGatewayHandoverValidated"));
        diagnostics.put("cloudConfigurationAggregateValidated", outcomes.get("cloudConfigurationAggregateValidated"));
        diagnostics.put("jsonSequenceReplayExitCode", jsonSequenceReplay.exitCode());
        diagnostics.put("jsonSequenceWindowsExecuted", jsonSequenceReplay.stepsExecuted());
        diagnostics.put("jsonSequenceTaskEvaluations", jsonSequenceReplay.taskEvaluations());
        diagnostics.put("jsonSequenceSmokeStatus", jsonSequenceReplay.status());
        diagnostics.put("jsonTimeSmokeExitCode", jsonTimeSmoke.exitCode());
        diagnostics.put("jsonTimeSmokeWindowsExecuted", jsonTimeSmoke.stepsExecuted());
        diagnostics.put("jsonTimeSmokeStepsExecuted", jsonTimeSmoke.stepsExecuted());
        diagnostics.put("jsonTimeSmokeStatus", jsonTimeSmoke.status());
        diagnostics.put("jsonTimeLastTriggerSeconds", jsonTimeSmoke.lastTriggerSeconds());
        diagnostics.put("jsonTimeLastSourceSnapshot", jsonTimeSmoke.lastSourceSnapshot());
        diagnostics.put("jsonTimeFutureLookAheadViolations", jsonTimeSmoke.futureLookAheadViolations());
        diagnostics.put("futureLookAheadViolations", jsonTimeSmoke.futureLookAheadViolations());
        diagnostics.put("allLocalDecisionsObserved", jsonSequenceReplay.allLocalDecisionsObserved());
        diagnostics.put("fullOffloadingObserved", jsonSequenceReplay.fullOffloadingObserved());
        diagnostics.put("diagnosticWarnings", List.of(WARNING_BASELINE, WARNING_JSON_TIME));
        diagnostics.put("warnings", warnings);
        diagnostics.put("errors", errors);
        diagnostics.put(
                "phase10jPre2Status",
                errors.isEmpty() ? "COMPLETED" : "FAILED"
        );
        diagnostics.put("readyForPhase10J", errors.isEmpty());

        Files.createDirectories(validationOutFile.toAbsolutePath().getParent());
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(validationOutFile.toFile(), diagnostics);

        System.out.println("Replay bootstrap validation completed");
        System.out.println("sourceRun=" + sourceRun);
        System.out.println("phase=" + PHASE);
        System.out.println("testsExecuted=" + testsExecuted);
        System.out.println("testsPassed=" + testsPassed);
        System.out.println("testsFailed=" + testsFailed);
        for (Map.Entry<String, Boolean> entry : outcomes.entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
        System.out.println("jsonSequenceReplayExitCode=" + jsonSequenceReplay.exitCode());
        System.out.println("jsonSequenceWindowsExecuted=" + jsonSequenceReplay.stepsExecuted());
        System.out.println("jsonSequenceTaskEvaluations=" + jsonSequenceReplay.taskEvaluations());
        System.out.println("jsonTimeSmokeExitCode=" + jsonTimeSmoke.exitCode());
        System.out.println("jsonTimeSmokeWindowsExecuted=" + jsonTimeSmoke.stepsExecuted());
        System.out.println("jsonTimeLastTriggerSeconds=" + jsonTimeSmoke.lastTriggerSeconds());
        System.out.println("jsonTimeLastSourceSnapshot=" + jsonTimeSmoke.lastSourceSnapshot());
        System.out.println("futureLookAheadViolations=" + jsonTimeSmoke.futureLookAheadViolations());
        System.out.println("phase10jPre2Status=" + diagnostics.get("phase10jPre2Status"));
        System.out.println("readyForPhase10J=" + diagnostics.get("readyForPhase10J"));
        System.out.println();
        System.out.println("JSON_SEQUENCE_REPLAY_OUTPUT_BEGIN");
        System.out.print(jsonSequenceReplay.output());
        System.out.println("JSON_SEQUENCE_REPLAY_OUTPUT_END");
        System.out.println();
        System.out.println("JSON_TIME_SMOKE_OUTPUT_BEGIN");
        System.out.print(jsonTimeSmoke.output());
        System.out.println("JSON_TIME_SMOKE_OUTPUT_END");

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Replay bootstrap validation failed: " + errors);
        }
    }

    private static boolean caseEmptySnapshotOptimizer(List<SystemSnapshot> snapshots) {
        MaGaOptimizer optimizer = new MaGaOptimizer(
                MaGaConfig.defaultConfig(GaParameterScalingMode.ADAPTIVE)
        );
        MaGaResult result = optimizer.optimizeDetailed(snapshots.get(0));
        return result.getStopReason() == StopReason.EMPTY_TASK_SET
                && result.getGenerationsExecuted() == 0
                && result.getBestChromosome().getFitness() == 0.0
                && result.getFinalBestFitness() == 0.0;
    }

    private static boolean caseTaskWithoutCandidates(List<SystemSnapshot> snapshots) {
        SystemSnapshot source = snapshots.get(1);
        SystemSnapshot invalid = new SystemSnapshot(
                "task_without_candidates",
                source.getTimeSeconds(),
                source.getVehicles(),
                source.getTasks(),
                List.of(),
                source.getAccessGateways(),
                source.getAccessLinks(),
                source.getBandwidthPools()
        );
        MaGaOptimizer optimizer = new MaGaOptimizer(
                MaGaConfig.defaultConfig(GaParameterScalingMode.ADAPTIVE)
        );
        try {
            optimizer.optimizeDetailed(invalid);
            return false;
        } catch (IllegalArgumentException expected) {
            return expected.getMessage().contains("candidateNodes");
        }
    }

    private static boolean caseTimeIndexedBeforeFirst(List<SystemSnapshot> snapshots) {
        TimeIndexedSnapshotReplaySource source =
                new TimeIndexedSnapshotReplaySource(firstTwo(snapshots));
        SystemStateRequest request = request(0, 0.0);
        return source.nextObservation(request).isEmpty();
    }

    private static boolean caseTimeIndexedExactFirst(List<SystemSnapshot> snapshots) {
        TimeIndexedSnapshotReplaySource source =
                new TimeIndexedSnapshotReplaySource(firstTwo(snapshots));
        double firstTime = snapshots.get(0).getTimeSeconds();
        Optional<SystemStateObservation> observation =
                source.nextObservation(request(0, firstTime));
        return observation.isPresent()
                && observation.get().isExactTimeMatch()
                && observation.get().getSourceObservationTimeSeconds() == firstTime
                && observation.get().getSnapshot().getSnapshotId()
                        .equals(snapshots.get(0).getSnapshotId());
    }

    private static boolean caseSequentialPreservesEmpty(List<SystemSnapshot> snapshots) {
        SequentialSnapshotReplaySource source =
                new SequentialSnapshotReplaySource(firstTwo(snapshots));
        Optional<SystemStateObservation> first =
                source.nextObservation(request(0, 0.0));
        Optional<SystemStateObservation> second =
                source.nextObservation(request(1, snapshots.get(0).getTimeSeconds()));
        return first.isPresent()
                && first.get().getSnapshot().getTasks().isEmpty()
                && first.get().getSnapshot().getCandidateNodes().isEmpty()
                && first.get().getSourceObservationTimeSeconds() == snapshots.get(0).getTimeSeconds()
                && second.isPresent()
                && second.get().getSourceObservationTimeSeconds() == snapshots.get(1).getTimeSeconds();
    }

    private static boolean caseUncoveredToUncovered() {
        String output = printAccessLinkDynamicity(
                snapshot("uncovered_previous", 5.0, false, null),
                snapshot("uncovered_current", 10.0, false, null)
        );
        return output.contains("UNCHANGED")
                && output.contains("- | -")
                && output.contains("0,000000 | 0,000000 | 0,000000");
    }

    private static boolean caseCoveredToUncovered() {
        String output = printAccessLinkDynamicity(
                snapshot("covered_previous", 5.0, true, "gateway_a"),
                snapshot("uncovered_current", 10.0, false, null)
        );
        return output.contains("gateway_a | -")
                && output.contains("COVERAGE_LOSS")
                && !transitionLine(output).contains("HANDOVER");
    }

    private static boolean caseUncoveredToCovered() {
        String output = printAccessLinkDynamicity(
                snapshot("uncovered_previous", 5.0, false, null),
                snapshot("covered_current", 10.0, true, "gateway_a")
        );
        return output.contains("- | gateway_a")
                && output.contains("COVERAGE_GAIN")
                && !transitionLine(output).contains("HANDOVER");
    }

    private static boolean caseGatewayToGatewayHandover() {
        String output = printAccessLinkDynamicity(
                snapshot("gateway_a_previous", 5.0, true, "gateway_a"),
                snapshot("gateway_b_current", 10.0, true, "gateway_b")
        );
        return output.contains("gateway_a | gateway_b")
                && output.contains("HANDOVER");
    }

    private static boolean caseCloudConfigurationAggregate() {
        TemporalWindowResult result = resultFor(
                snapshot("first_empty", 5.0, false, null),
                snapshot("second_active", 10.0, true, "gateway_a")
        );
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            new CloudGatewayDiagnosticPrinter(capture).print(result, List.of());
        }
        String output = buffer.toString(StandardCharsets.UTF_8);
        return output.contains("configuredGatewayCountAcrossRun: 2")
                && output.contains("firstSnapshotAccessLinkCount: 0")
                && output.contains("maximumAccessLinkCountAcrossWindows: 1")
                && output.contains("maximumActiveAccessLinkCountAcrossWindows: 1")
                && output.contains("windowsWithActiveAccessLinks: 1");
    }

    private static List<SystemSnapshot> firstTwo(List<SystemSnapshot> snapshots) {
        return List.of(snapshots.get(0), snapshots.get(1));
    }

    private static SystemStateRequest request(int windowIndex, double requestedTimeSeconds) {
        return new SystemStateRequest(
                windowIndex,
                ReoptimizationTrigger.firstRun(requestedTimeSeconds),
                requestedTimeSeconds,
                5.0
        );
    }

    private static SmokeResult runSmoke(String sourceMode, String profile, String folder, String steps) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exitCode = 0;
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            System.setErr(capture);
            AdaptiveWindowMain.main(new String[] { sourceMode, profile, folder, steps });
        } catch (Exception ex) {
            exitCode = 1;
            ex.printStackTrace(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        String output = buffer.toString(StandardCharsets.UTF_8);
        boolean noEmptyReport = !output.contains("No temporal step available.");
        boolean hasFirstSnapshot = output.contains("mosaic_generated_000_t_005");
        boolean sequenceEmptyVisible = !"JSON_SEQUENCE".equals(sourceMode)
                || output.contains("EMPTY_TASK_SET");
        boolean timeStepVisible = !"JSON_TIME".equals(sourceMode)
                || output.contains("Executed windows:");
        boolean passed = exitCode == 0 && noEmptyReport && hasFirstSnapshot && sequenceEmptyVisible
                && timeStepVisible;
        int executedSteps = parseExecutedWindows(output);
        int taskEvaluations = parseTaskEvaluations(output);
        TimingSummary timing = parseLastTiming(output);
        int futureLookAheadViolations = parseFutureLookAheadViolations(output);
        DecisionSummary decisionSummary = parseDecisionSummary(output);
        return new SmokeResult(
                passed,
                passed ? "PASS" : "FAIL",
                exitCode,
                executedSteps,
                taskEvaluations,
                timing.lastTriggerSeconds(),
                timing.lastSourceSnapshot(),
                futureLookAheadViolations,
                decisionSummary.allLocalDecisionsObserved(),
                decisionSummary.fullOffloadingObserved(),
                output
        );
    }

    private static int parseExecutedWindows(String output) {
        String prefix = "Executed windows:";
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return Integer.parseInt(trimmed.substring(prefix.length()).trim());
            }
        }
        return 0;
    }

    private static int parseTaskEvaluations(String output) {
        String prefix = "- task evaluations:";
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return Integer.parseInt(trimmed.substring(prefix.length()).trim());
            }
        }
        return 0;
    }

    private static TimingSummary parseLastTiming(String output) {
        boolean inTiming = false;
        double lastTrigger = Double.NaN;
        String lastSnapshot = "-";
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if ("TEMPORAL TIMING SUMMARY".equals(trimmed)) {
                inTiming = true;
                continue;
            }
            if (inTiming && trimmed.startsWith("POPULATION REUSE DECISION SUMMARY")) {
                break;
            }
            if (!inTiming || trimmed.isBlank() || trimmed.startsWith("-")
                    || trimmed.startsWith("idx |")) {
                continue;
            }
            String[] parts = trimmed.split("\\s*\\|\\s*");
            if (parts.length >= 3 && isInteger(parts[0])) {
                lastSnapshot = parts[1];
                lastTrigger = parseSeconds(parts[2]);
            }
        }
        return new TimingSummary(lastTrigger, lastSnapshot);
    }

    private static int parseFutureLookAheadViolations(String output) {
        boolean inSource = false;
        int violations = 0;
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if ("SYSTEM STATE SOURCE SUMMARY".equals(trimmed)) {
                inSource = true;
                continue;
            }
            if (inSource && trimmed.startsWith("CANDIDATE PREFILTER SUMMARY")) {
                break;
            }
            if (!inSource || trimmed.isBlank() || trimmed.startsWith("-")
                    || trimmed.startsWith("idx |")) {
                continue;
            }
            String[] parts = trimmed.split("\\s*\\|\\s*");
            if (parts.length >= 9 && isInteger(parts[0])
                    && "true".equalsIgnoreCase(parts[8])) {
                violations++;
            }
        }
        return violations;
    }

    private static DecisionSummary parseDecisionSummary(String output) {
        boolean inDecision = false;
        boolean allLocal = true;
        boolean sawTaskWindow = false;
        boolean fullOffloading = false;
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if ("4. DECISION / OFFLOADING SUMMARY".equals(trimmed)) {
                inDecision = true;
                continue;
            }
            if (inDecision && trimmed.startsWith("5. DEADLINE CAUSE SUMMARY")) {
                break;
            }
            if (!inDecision || trimmed.isBlank() || trimmed.startsWith("-")
                    || trimmed.startsWith("idx |")) {
                continue;
            }
            String[] parts = trimmed.split("\\s*\\|\\s*");
            if (parts.length >= 15 && isInteger(parts[0])) {
                int local = Integer.parseInt(parts[1]);
                int edge = Integer.parseInt(parts[2]);
                int cloud = Integer.parseInt(parts[3]);
                int vehicle = Integer.parseInt(parts[4]);
                int localExec = Integer.parseInt(parts[5]);
                int full = Integer.parseInt(parts[7]);
                if (local + edge + cloud + vehicle > 0) {
                    sawTaskWindow = true;
                    if (edge != 0 || cloud != 0 || vehicle != 0 || localExec != local) {
                        allLocal = false;
                    }
                }
                if (full > 0) {
                    fullOffloading = true;
                }
            }
        }
        return new DecisionSummary(sawTaskWindow && allLocal, fullOffloading);
    }

    private static String printAccessLinkDynamicity(
            SystemSnapshot previous,
            SystemSnapshot current
    ) {
        TemporalWindowResult result = resultFor(previous, current);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            new AccessLinkDynamicityDiagnosticPrinter(
                    MobilityConfig.defaultConfig(),
                    capture,
                    0
            ).print(result);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static TemporalWindowResult resultFor(SystemSnapshot... snapshots) {
        List<TemporalStepResult> steps = new ArrayList<>();
        MaGaOptimizer optimizer = new MaGaOptimizer(
                MaGaConfig.defaultConfig(GaParameterScalingMode.ADAPTIVE)
        );
        for (int i = 0; i < snapshots.length; i++) {
            SystemSnapshot snapshot = snapshots[i];
            MaGaResult result = optimizer.optimizeDetailed(snapshot);
            steps.add(new TemporalStepResult(
                    i,
                    ReoptimizationTrigger.firstRun(snapshot.getTimeSeconds()),
                    0.0,
                    snapshot.getTimeSeconds(),
                    snapshot,
                    DynamicityBreakdown.firstRun(
                            snapshot.getSnapshotId(),
                            snapshot.getTimeSeconds()
                    ),
                    PopulationReuseMode.FIRST_RUN,
                    0,
                    result.getFinalPopulation().size(),
                    result
            ));
        }
        return new TemporalWindowResult(steps);
    }

    private static SystemSnapshot snapshot(
            String id,
            double timeSeconds,
            boolean activeGateway,
            String gatewayId
    ) {
        List<AccessGatewaySnapshot> gateways = List.of(
                new AccessGatewaySnapshot("gateway_a", "RSU", 100.0, 0.0, 1000.0, "pool_a"),
                new AccessGatewaySnapshot("gateway_b", "RSU", 200.0, 0.0, 1000.0, "pool_b")
        );
        List<AccessLinkSnapshot> links = new ArrayList<>();
        if (activeGateway) {
            links.add(new AccessLinkSnapshot(
                    "link_veh_0_" + gatewayId,
                    "veh_0",
                    gatewayId,
                    true,
                    true
            ));
        }
        return new SystemSnapshot(
                id,
                timeSeconds,
                List.of(new VehicleSnapshot("veh_0", 0.0, 0.0, 10.0, 1000.0)),
                Collections.emptyList(),
                Collections.emptyList(),
                gateways,
                links,
                Collections.emptyList()
        );
    }

    private static String transitionLine(String output) {
        for (String line : output.split("\\R")) {
            if (line.contains("COVERAGE_") || line.contains("HANDOVER")
                    || line.contains("UNCHANGED")) {
                return line;
            }
        }
        return "";
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static double parseSeconds(String text) {
        String normalized = text.replace("s", "")
                .trim()
                .replace(',', '.');
        return Double.parseDouble(normalized);
    }

    private record SmokeResult(
            boolean passed,
            String status,
            int exitCode,
            int stepsExecuted,
            int taskEvaluations,
            double lastTriggerSeconds,
            String lastSourceSnapshot,
            int futureLookAheadViolations,
            boolean allLocalDecisionsObserved,
            boolean fullOffloadingObserved,
            String output
    ) {
    }

    private record TimingSummary(double lastTriggerSeconds, String lastSourceSnapshot) {
    }

    private record DecisionSummary(
            boolean allLocalDecisionsObserved,
            boolean fullOffloadingObserved
    ) {
    }
}
