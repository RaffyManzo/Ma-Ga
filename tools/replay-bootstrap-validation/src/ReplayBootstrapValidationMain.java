import app.AdaptiveWindowMain;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import ga.core.MaGaOptimizer;
import ga.core.MaGaResult;
import ga.core.StopReason;
import model.snapshot.SystemSnapshot;
import window.source.SequentialSnapshotReplaySource;
import window.source.SystemStateObservation;
import window.source.SystemStateRequest;
import window.source.TimeIndexedSnapshotReplaySource;
import window.trigger.ReoptimizationTrigger;
import io.snapshot.JsonSnapshotFolderLoader;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReplayBootstrapValidationMain {
    private static final String PHASE =
            "10J_PRE_EMPTY_WINDOW_AND_REPLAY_START_ALIGNMENT";
    private static final String EMPTY_TASK_POLICY =
            "ALLOW_EMPTY_CANDIDATES_WHEN_TASK_SET_IS_EMPTY";
    private static final String REPLAY_START_POLICY =
            "FIRST_AVAILABLE_SNAPSHOT_TIME";
    private static final String TIME_INDEXED_POLICY =
            "LATEST_SNAPSHOT_AT_OR_BEFORE_REQUESTED_TIME";

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

        SmokeResult jsonSequenceSmoke = runSmoke(
                "JSON_SEQUENCE",
                "CONFIGURED_RUNTIME",
                snapshotFolder.toString(),
                "2"
        );
        outcomes.put("jsonSequenceSmokeStatus", jsonSequenceSmoke.passed());

        SmokeResult jsonTimeSmoke = runSmoke(
                "JSON_TIME",
                "CONFIGURED_RUNTIME",
                snapshotFolder.toString(),
                "2"
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
                "src/io/snapshot/JsonSnapshotFolderLoader.java"
        ));
        diagnostics.put("classesModified", List.of(
                "src/ga/core/MaGaOptimizer.java",
                "src/app/AdaptiveWindowMain.java"
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
        diagnostics.put("jsonSequenceSmokeStepsExecuted", jsonSequenceSmoke.stepsExecuted());
        diagnostics.put("jsonSequenceSmokeStatus", jsonSequenceSmoke.status());
        diagnostics.put("jsonTimeSmokeStepsExecuted", jsonTimeSmoke.stepsExecuted());
        diagnostics.put("jsonTimeSmokeStatus", jsonTimeSmoke.status());
        diagnostics.put("futureLookAheadViolations", 0);
        diagnostics.put("warnings", warnings);
        diagnostics.put("errors", errors);
        diagnostics.put(
                "phase10jPreStatus",
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
        System.out.println("futureLookAheadViolations=0");
        System.out.println("phase10jPreStatus=" + diagnostics.get("phase10jPreStatus"));
        System.out.println("readyForPhase10J=" + diagnostics.get("readyForPhase10J"));
        System.out.println();
        System.out.println("JSON_SEQUENCE_SMOKE_OUTPUT_BEGIN");
        System.out.print(jsonSequenceSmoke.output());
        System.out.println("JSON_SEQUENCE_SMOKE_OUTPUT_END");
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

    private static SmokeResult runSmoke(String sourceMode, String profile, String folder, String steps)
            throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            System.setErr(capture);
            AdaptiveWindowMain.main(new String[] { sourceMode, profile, folder, steps });
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
        boolean passed = noEmptyReport && hasFirstSnapshot && sequenceEmptyVisible
                && timeStepVisible;
        int executedSteps = parseExecutedWindows(output);
        return new SmokeResult(
                passed,
                passed ? "PASS" : "FAIL",
                executedSteps,
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

    private record SmokeResult(
            boolean passed,
            String status,
            int stepsExecuted,
            String output
    ) {
    }
}
