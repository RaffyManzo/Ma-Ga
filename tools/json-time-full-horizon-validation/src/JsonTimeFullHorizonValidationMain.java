import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import config.window.TemporalRuntimeProfile;
import config.window.TemporalWindowConfig;
import ga.core.MaGaOptimizer;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import window.core.TemporalWindowManager;
import window.dynamicity.DynamicityEvaluator;
import window.event.StaticCriticalEventDetector;
import window.population.PopulationAdapter;
import window.population.PopulationReuseDecisionPolicy;
import window.prefilter.CandidatePrefilter;
import window.prefilter.CandidatePrefilterConfig;
import window.source.FilteringSystemStateSource;
import window.source.SystemStateObservation;
import window.source.TimeIndexedSnapshotReplaySource;
import window.state.TemporalStepResult;
import window.state.TemporalWindowState;
import window.timing.AdaptiveWindowController;
import window.timing.CoverageReferenceCalculator;
import window.timing.TemporalOperationalMetrics;
import window.timing.TemporalWindowBoundsCalculator;
import io.snapshot.JsonSnapshotFolderLoader;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class JsonTimeFullHorizonValidationMain {
    private static final double EPSILON_SECONDS = 1.0E-6;
    private static final String PHASE = "10J_JSON_REPLAY_FULL_HORIZON_VALIDATION";
    private static final String SOURCE_MODE = "JSON_TIME";
    private static final String LOOKUP_POLICY = "LATEST_SNAPSHOT_AT_OR_BEFORE_REQUESTED_TIME";
    private static final String ORDINARY_STOP_POLICY = "FULL_TIME_HORIZON_REACHED";
    private static final String SAFETY_STOP_POLICY = "SAFETY_MAX_STEPS_REACHED";
    private static final String WARNING_BASELINE =
            "WARNING_DIAGNOSTIC_BASELINE_NOT_STRESSING_OFFLOADING";
    private static final String WARNING_ALL_LOCAL =
            "WARNING_ALL_DECISIONS_LOCAL";
    private static final String WARNING_FULL_OFFLOADING_NOT_OBSERVED =
            "WARNING_FULL_OFFLOADING_NOT_OBSERVED";

    private JsonTimeFullHorizonValidationMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 8) {
            throw new IllegalArgumentException(
                    "Usage: JsonTimeFullHorizonValidationMain "
                            + "<snapshotFolder> <runtimeProfile> <safetyMaxSteps> "
                            + "<traceOutFile> <validationOutFile> "
                            + "<phase10iValidationJson> <phase10jPreValidationJson> "
                            + "<phase10jPre2ValidationJson>"
            );
        }

        String snapshotFolder = args[0];
        TemporalRuntimeProfile runtimeProfile = TemporalRuntimeProfile.parse(args[1]);
        if (runtimeProfile != TemporalRuntimeProfile.OBSERVED_RUNTIME) {
            throw new IllegalArgumentException(
                    "Fase 10J-final supports only OBSERVED_RUNTIME."
            );
        }
        int safetyMaxSteps = Integer.parseInt(args[2]);
        if (safetyMaxSteps < 1) {
            throw new IllegalArgumentException("safetyMaxSteps must be >= 1.");
        }
        Path traceOutFile = Path.of(args[3]);
        Path validationOutFile = Path.of(args[4]);
        Path phase10iValidationFile = Path.of(args[5]);
        Path phase10jPreValidationFile = Path.of(args[6]);
        Path phase10jPre2ValidationFile = Path.of(args[7]);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> phase10i = readJson(mapper, phase10iValidationFile);
        Map<String, Object> phase10jPre = readJson(mapper, phase10jPreValidationFile);
        Map<String, Object> phase10jPre2 = readJson(mapper, phase10jPre2ValidationFile);

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String sourceRun = validatePreviousDiagnostics(
                phase10i,
                phase10jPre,
                phase10jPre2,
                errors
        );

        List<SystemSnapshot> snapshots =
                new JsonSnapshotFolderLoader().load(snapshotFolder);
        if (snapshots.isEmpty()) {
            errors.add("snapshotFolder contains no snapshots.");
        }
        snapshots.sort(
                Comparator.comparingDouble(SystemSnapshot::getTimeSeconds)
                        .thenComparing(SystemSnapshot::getSnapshotId)
        );

        SystemSnapshot firstSnapshot = snapshots.isEmpty() ? null : snapshots.get(0);
        SystemSnapshot finalSnapshot = snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
        String firstSnapshotId = firstSnapshot == null ? null : firstSnapshot.getSnapshotId();
        double firstSnapshotTimeSeconds = firstSnapshot == null ? Double.NaN : firstSnapshot.getTimeSeconds();
        String finalSnapshotId = finalSnapshot == null ? null : finalSnapshot.getSnapshotId();
        double finalSnapshotTimeSeconds = finalSnapshot == null ? Double.NaN : finalSnapshot.getTimeSeconds();

        RunOutcome outcome;
        if (errors.isEmpty()) {
            outcome = executeFullHorizon(
                    snapshots,
                    snapshotFolder,
                    runtimeProfile,
                    safetyMaxSteps,
                    traceOutFile,
                    errors
            );
        } else {
            outcome = RunOutcome.empty("PREREQUISITE_VALIDATION_FAILED");
        }

        boolean jsonSequenceValidationStatus = jsonSequenceValidated(
                phase10i,
                phase10jPre2
        );
        boolean jsonTimeSmokeValidationStatus = jsonTimeSmokeValidated(phase10jPre2);
        boolean fullTimeHorizonReached =
                ORDINARY_STOP_POLICY.equals(outcome.stopReason())
                        && outcome.lastObservationTimeSeconds() + EPSILON_SECONDS >= finalSnapshotTimeSeconds
                        && finalSnapshotId != null
                        && finalSnapshotId.equals(outcome.lastSourceSnapshotId());

        if (!jsonSequenceValidationStatus) {
            errors.add("JSON_SEQUENCE end-to-end validation is not coherent with Phase 10I diagnostics.");
        }
        if (!jsonTimeSmokeValidationStatus) {
            errors.add("JSON_TIME smoke validation is not coherent with Phase 10J-pre2 diagnostics.");
        }
        if (!fullTimeHorizonReached) {
            errors.add("JSON_TIME full horizon was not reached.");
        }
        if (outcome.safetyGuardrailTriggered()) {
            errors.add("Safety guardrail was triggered before reaching full horizon.");
        }
        if (outcome.futureLookAheadViolations() > 0) {
            errors.add("Future look-ahead violations detected.");
        }
        if (outcome.noTemporalStepFailures() > 0) {
            errors.add("No temporal step available before horizon.");
        }
        if (!Files.exists(traceOutFile)) {
            errors.add("Trace CSV was not generated: " + traceOutFile);
        }

        warnings.add(WARNING_BASELINE);
        if (outcome.allLocalDecisionsObserved()) {
            warnings.add(WARNING_ALL_LOCAL);
        }
        if (!outcome.fullOffloadingObserved()) {
            warnings.add(WARNING_FULL_OFFLOADING_NOT_OBSERVED);
        }

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("sourceRun", sourceRun);
        diagnostics.put("phase", PHASE);
        diagnostics.put("sourceMode", SOURCE_MODE);
        diagnostics.put("runtimeProfile", String.valueOf(runtimeProfile));
        diagnostics.put("lookupPolicy", LOOKUP_POLICY);
        diagnostics.put("ordinaryStopPolicy", ORDINARY_STOP_POLICY);
        diagnostics.put("safetyStopPolicy", SAFETY_STOP_POLICY);
        diagnostics.put("snapshotFolder", snapshotFolder);
        diagnostics.put("snapshotsLoaded", snapshots.size());
        diagnostics.put("firstSnapshotId", firstSnapshotId);
        diagnostics.put("firstSnapshotTimeSeconds", finiteOrNull(firstSnapshotTimeSeconds));
        diagnostics.put("finalSnapshotId", finalSnapshotId);
        diagnostics.put("finalSnapshotTimeSeconds", finiteOrNull(finalSnapshotTimeSeconds));
        diagnostics.put("safetyMaxSteps", safetyMaxSteps);
        diagnostics.put("stepsExecuted", outcome.stepsExecuted());
        diagnostics.put("stopReason", outcome.stopReason());
        diagnostics.put("fullTimeHorizonReached", fullTimeHorizonReached);
        diagnostics.put("safetyGuardrailTriggered", outcome.safetyGuardrailTriggered());
        diagnostics.put("lastTriggerTimeSeconds", finiteOrNull(outcome.lastTriggerTimeSeconds()));
        diagnostics.put("lastObservationTimeSeconds", finiteOrNull(outcome.lastObservationTimeSeconds()));
        diagnostics.put("lastSourceSnapshotId", outcome.lastSourceSnapshotId());
        diagnostics.put("lastSourceSnapshotTimeSeconds", finiteOrNull(outcome.lastSourceSnapshotTimeSeconds()));
        diagnostics.put("distinctSourceSnapshotsObserved", outcome.distinctSourceSnapshotsObserved());
        diagnostics.put("exactTimestampMatches", outcome.exactTimestampMatches());
        diagnostics.put("pastSnapshotReuses", outcome.pastSnapshotReuses());
        diagnostics.put("sourceSnapshotAdvances", outcome.sourceSnapshotAdvances());
        diagnostics.put("sourceSnapshotSkips", outcome.sourceSnapshotSkips());
        diagnostics.put("skippedSnapshotIds", outcome.skippedSnapshotIds());
        diagnostics.put("futureLookAheadViolations", outcome.futureLookAheadViolations());
        diagnostics.put("noTemporalStepFailures", outcome.noTemporalStepFailures());
        diagnostics.put("emptyTaskSteps", outcome.emptyTaskSteps());
        diagnostics.put("taskEvaluationsAcrossTemporalSteps", outcome.taskEvaluationsAcrossTemporalSteps());
        diagnostics.put("jsonSequenceValidationStatus", jsonSequenceValidationStatus ? "COMPLETED" : "FAILED");
        diagnostics.put("jsonSequenceWindowsExecuted", intValue(phase10jPre2, "jsonSequenceWindowsExecuted"));
        diagnostics.put("jsonSequenceTaskEvaluations", intValue(phase10jPre2, "jsonSequenceTaskEvaluations"));
        diagnostics.put("jsonTimeSmokeValidationStatus", jsonTimeSmokeValidationStatus ? "COMPLETED" : "FAILED");
        diagnostics.put("jsonTimeFullHorizonValidationStatus", fullTimeHorizonReached ? "COMPLETED" : "FAILED");
        diagnostics.put("allLocalDecisionsObserved", outcome.allLocalDecisionsObserved());
        diagnostics.put("fullOffloadingObserved", outcome.fullOffloadingObserved());
        diagnostics.put("diagnosticWarnings", warnings);
        diagnostics.put("warnings", List.of());
        diagnostics.put("errors", errors);
        diagnostics.put("phase10jStatus", errors.isEmpty() ? "COMPLETED" : "FAILED");
        diagnostics.put("point10ReadyToClose", errors.isEmpty());

        Files.createDirectories(validationOutFile.toAbsolutePath().getParent());
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(validationOutFile.toFile(), diagnostics);

        System.out.println("JSON_TIME full horizon validation completed");
        System.out.println("sourceRun=" + sourceRun);
        System.out.println("sourceMode=" + SOURCE_MODE);
        System.out.println("runtimeProfile=" + runtimeProfile);
        System.out.println("lookupPolicy=" + LOOKUP_POLICY);
        System.out.println("ordinaryStopPolicy=" + ORDINARY_STOP_POLICY);
        System.out.println("safetyStopPolicy=" + SAFETY_STOP_POLICY);
        System.out.println("snapshotFolder=" + snapshotFolder);
        System.out.println("snapshotsLoaded=" + snapshots.size());
        System.out.println("firstSnapshotId=" + firstSnapshotId);
        System.out.println("firstSnapshotTimeSeconds=" + finiteOrDash(firstSnapshotTimeSeconds));
        System.out.println("finalSnapshotId=" + finalSnapshotId);
        System.out.println("finalSnapshotTimeSeconds=" + finiteOrDash(finalSnapshotTimeSeconds));
        System.out.println("safetyMaxSteps=" + safetyMaxSteps);
        System.out.println("stepsExecuted=" + outcome.stepsExecuted());
        System.out.println("stopReason=" + outcome.stopReason());
        System.out.println("fullTimeHorizonReached=" + fullTimeHorizonReached);
        System.out.println("safetyGuardrailTriggered=" + outcome.safetyGuardrailTriggered());
        System.out.println("lastTriggerTimeSeconds=" + finiteOrDash(outcome.lastTriggerTimeSeconds()));
        System.out.println("lastObservationTimeSeconds=" + finiteOrDash(outcome.lastObservationTimeSeconds()));
        System.out.println("lastSourceSnapshotId=" + outcome.lastSourceSnapshotId());
        System.out.println("lastSourceSnapshotTimeSeconds=" + finiteOrDash(outcome.lastSourceSnapshotTimeSeconds()));
        System.out.println("exactTimestampMatches=" + outcome.exactTimestampMatches());
        System.out.println("pastSnapshotReuses=" + outcome.pastSnapshotReuses());
        System.out.println("sourceSnapshotAdvances=" + outcome.sourceSnapshotAdvances());
        System.out.println("sourceSnapshotSkips=" + outcome.sourceSnapshotSkips());
        System.out.println("futureLookAheadViolations=" + outcome.futureLookAheadViolations());
        System.out.println("noTemporalStepFailures=" + outcome.noTemporalStepFailures());
        System.out.println("traceOutFile=" + traceOutFile);
        System.out.println("validationOutFile=" + validationOutFile);
        System.out.println("jsonSequenceValidationStatus=" + diagnostics.get("jsonSequenceValidationStatus"));
        System.out.println("jsonTimeSmokeValidationStatus=" + diagnostics.get("jsonTimeSmokeValidationStatus"));
        System.out.println("jsonTimeFullHorizonValidationStatus=" + diagnostics.get("jsonTimeFullHorizonValidationStatus"));
        System.out.println("allLocalDecisionsObserved=" + outcome.allLocalDecisionsObserved());
        System.out.println("fullOffloadingObserved=" + outcome.fullOffloadingObserved());
        System.out.println("errorsCount=" + errors.size());
        System.out.println("phase10jStatus=" + diagnostics.get("phase10jStatus"));
        System.out.println("point10ReadyToClose=" + diagnostics.get("point10ReadyToClose"));

        if (!errors.isEmpty()) {
            throw new IllegalStateException("JSON_TIME full horizon validation failed: " + errors);
        }
    }

    private static RunOutcome executeFullHorizon(
            List<SystemSnapshot> snapshots,
            String snapshotFolder,
            TemporalRuntimeProfile runtimeProfile,
            int safetyMaxSteps,
            Path traceOutFile,
            List<String> errors
    ) throws Exception {
        MaGaConfig maGaConfig = MaGaConfig.defaultConfig(GaParameterScalingMode.ADAPTIVE);
        TemporalWindowConfig windowConfig = runtimeProfile.createWindowConfig();
        CandidatePrefilter prefilter = new CandidatePrefilter(
                CandidatePrefilterConfig.defaultConfig(),
                maGaConfig.getMobilityConfig()
        );
        TimeIndexedSnapshotReplaySource raw = new TimeIndexedSnapshotReplaySource(
                snapshots,
                EPSILON_SECONDS,
                "time-indexed JSON full horizon validation from " + snapshotFolder
        );
        FilteringSystemStateSource source = new FilteringSystemStateSource(raw, prefilter);
        SystemSnapshot firstFiltered = prefilter.filter(snapshots.get(0)).getFilteredSnapshot();
        int targetPopulationSize =
                maGaConfig.resolveGeneticAlgorithmConfig(firstFiltered).getPopulationSize();
        CoverageReferenceCalculator coverageReference =
                new CoverageReferenceCalculator(maGaConfig.getMobilityConfig());
        TemporalWindowBoundsCalculator bounds =
                new TemporalWindowBoundsCalculator(windowConfig, coverageReference);
        AdaptiveWindowController controller =
                new AdaptiveWindowController(windowConfig, bounds);
        TemporalWindowManager manager = new TemporalWindowManager(
                windowConfig,
                new MaGaOptimizer(maGaConfig),
                new DynamicityEvaluator(windowConfig, maGaConfig.getMobilityConfig()),
                new PopulationAdapter(
                        windowConfig,
                        maGaConfig,
                        new Random(maGaConfig.getGeneticAlgorithmConfig().getRandomSeed())
                ),
                new PopulationReuseDecisionPolicy(windowConfig),
                controller,
                StaticCriticalEventDetector.empty(),
                source,
                targetPopulationSize
        );

        double replayStartTimeSeconds = snapshots.get(0).getTimeSeconds();
        double finalSnapshotTimeSeconds = snapshots.get(snapshots.size() - 1).getTimeSeconds();
        String finalSnapshotId = snapshots.get(snapshots.size() - 1).getSnapshotId();
        Map<String, Integer> snapshotIndexById = new LinkedHashMap<>();
        for (int i = 0; i < snapshots.size(); i++) {
            snapshotIndexById.put(snapshots.get(i).getSnapshotId(), i);
        }

        TemporalWindowState state = TemporalWindowState.initial(
                replayStartTimeSeconds,
                windowConfig.getInitialWindowSeconds(),
                TemporalOperationalMetrics.estimated(
                        windowConfig.getDataCollectionDelaySeconds(),
                        windowConfig.getDefaultGaRuntimeEstimateSeconds(),
                        windowConfig.getStrategyApplicationSeconds(),
                        windowConfig.getEpsilonT()
                )
        );

        Files.createDirectories(traceOutFile.toAbsolutePath().getParent());
        List<TraceRow> rows = new ArrayList<>();
        Set<String> distinctSourceSnapshots = new LinkedHashSet<>();
        Set<String> skippedSnapshotIds = new LinkedHashSet<>();

        int exactTimestampMatches = 0;
        int pastSnapshotReuses = 0;
        int sourceSnapshotAdvances = 0;
        int sourceSnapshotSkips = 0;
        int futureLookAheadViolations = 0;
        int noTemporalStepFailures = 0;
        int emptyTaskSteps = 0;
        int taskEvaluationsAcrossTemporalSteps = 0;
        boolean allLocalDecisionsObserved = true;
        boolean sawTaskDecision = false;
        boolean fullOffloadingObserved = false;
        String stopReason = null;
        boolean safetyGuardrailTriggered = false;
        Integer previousSourceIndex = null;
        String previousSourceSnapshotId = null;
        double lastTriggerTimeSeconds = Double.NaN;
        double lastObservationTimeSeconds = Double.NaN;
        String lastSourceSnapshotId = null;
        double lastSourceSnapshotTimeSeconds = Double.NaN;

        while (true) {
            if (rows.size() >= safetyMaxSteps) {
                stopReason = SAFETY_STOP_POLICY;
                safetyGuardrailTriggered = true;
                break;
            }

            TemporalStepResult step = manager.executeNextStepOrNull(state);
            if (step == null) {
                stopReason = "NO_TEMPORAL_STEP_AVAILABLE_BEFORE_HORIZON";
                noTemporalStepFailures++;
                break;
            }

            Optional<SystemStateObservation> maybeObservation =
                    step.getSystemStateObservation();
            if (maybeObservation.isEmpty()) {
                errors.add("Step " + step.getWindowIndex() + " has no SystemStateObservation.");
                stopReason = "MISSING_SYSTEM_STATE_OBSERVATION";
                break;
            }
            SystemStateObservation observation = maybeObservation.get();
            SystemSnapshot sourceSnapshot = observation.getObservedSnapshot();
            String sourceSnapshotId = sourceSnapshot.getSnapshotId();
            int sourceIndex = snapshotIndexById.getOrDefault(sourceSnapshotId, -1);
            if (sourceIndex < 0) {
                errors.add("Unrecognized source snapshot: " + sourceSnapshotId);
                stopReason = "UNKNOWN_SOURCE_SNAPSHOT";
                break;
            }

            double observationTimeSeconds = observation.getRequestedObservationTimeSeconds();
            double sourceTimeSeconds = observation.getSourceObservationTimeSeconds();
            boolean futureLookAhead =
                    sourceTimeSeconds - observationTimeSeconds > EPSILON_SECONDS;
            if (futureLookAhead) {
                futureLookAheadViolations++;
            }
            boolean exactTimestampMatch = observation.isExactTimeMatch();
            if (exactTimestampMatch) {
                exactTimestampMatches++;
            }

            boolean pastSnapshotReuse = previousSourceIndex != null
                    && sourceIndex == previousSourceIndex;
            boolean sourceSnapshotAdvanced = previousSourceIndex != null
                    && sourceIndex > previousSourceIndex;
            int skippedCount = 0;
            if (sourceSnapshotAdvanced) {
                sourceSnapshotAdvances++;
                skippedCount = Math.max(0, sourceIndex - previousSourceIndex - 1);
                sourceSnapshotSkips += skippedCount;
                for (int i = previousSourceIndex + 1; i < sourceIndex; i++) {
                    skippedSnapshotIds.add(snapshots.get(i).getSnapshotId());
                }
            }
            if (pastSnapshotReuse) {
                pastSnapshotReuses++;
            }

            distinctSourceSnapshots.add(sourceSnapshotId);
            if (sourceSnapshot.getTasks().isEmpty()) {
                emptyTaskSteps++;
            }
            int taskCount = sourceSnapshot.getTasks().size();
            taskEvaluationsAcrossTemporalSteps += taskCount;

            DecisionObservation decisions = observeDecisions(step);
            if (decisions.sawDecision()) {
                sawTaskDecision = true;
                if (!decisions.allLocal()) {
                    allLocalDecisionsObserved = false;
                }
                if (decisions.fullOffloadingObserved()) {
                    fullOffloadingObserved = true;
                }
            }

            boolean horizonReachedAfterStep =
                    observationTimeSeconds + EPSILON_SECONDS >= finalSnapshotTimeSeconds
                            && finalSnapshotId.equals(sourceSnapshotId);

            rows.add(new TraceRow(
                    step.getWindowIndex(),
                    String.valueOf(step.getTrigger().getReason()),
                    step.getTriggerTimeSeconds(),
                    observationTimeSeconds,
                    sourceSnapshotId,
                    sourceTimeSeconds,
                    exactTimestampMatch,
                    pastSnapshotReuse,
                    sourceSnapshotAdvanced,
                    skippedCount,
                    futureLookAhead,
                    taskCount,
                    sourceSnapshot.getVehicles().size(),
                    sourceSnapshot.getCandidateNodes().size(),
                    step.getDynamicityBreakdown().getGlobalDynamicity(),
                    String.valueOf(step.getDynamicityBreakdown().getDynamicityLevel()),
                    String.valueOf(step.getDynamicityBreakdown().getSuggestedReuseMode()),
                    String.valueOf(step.getReuseMode()),
                    step.getOperationalMetrics().getObservedGaRuntimeSeconds(),
                    horizonReachedAfterStep
            ));

            lastTriggerTimeSeconds = step.getTriggerTimeSeconds();
            lastObservationTimeSeconds = observationTimeSeconds;
            lastSourceSnapshotId = sourceSnapshotId;
            lastSourceSnapshotTimeSeconds = sourceTimeSeconds;

            if (futureLookAhead) {
                stopReason = "FUTURE_LOOK_AHEAD_DETECTED";
                break;
            }
            if (horizonReachedAfterStep) {
                stopReason = ORDINARY_STOP_POLICY;
                break;
            }

            previousSourceIndex = sourceIndex;
            previousSourceSnapshotId = sourceSnapshotId;
            state = TemporalWindowState.afterStep(step);
        }

        writeTrace(traceOutFile, rows);

        return new RunOutcome(
                rows.size(),
                stopReason == null ? "UNKNOWN" : stopReason,
                ORDINARY_STOP_POLICY.equals(stopReason),
                safetyGuardrailTriggered,
                lastTriggerTimeSeconds,
                lastObservationTimeSeconds,
                lastSourceSnapshotId,
                lastSourceSnapshotTimeSeconds,
                distinctSourceSnapshots.size(),
                exactTimestampMatches,
                pastSnapshotReuses,
                sourceSnapshotAdvances,
                sourceSnapshotSkips,
                new ArrayList<>(skippedSnapshotIds),
                futureLookAheadViolations,
                noTemporalStepFailures,
                emptyTaskSteps,
                taskEvaluationsAcrossTemporalSteps,
                sawTaskDecision && allLocalDecisionsObserved,
                fullOffloadingObserved
        );
    }

    private static DecisionObservation observeDecisions(TemporalStepResult step) {
        SystemSnapshot optimizationSnapshot = step.getSystemStateObservation()
                .map(SystemStateObservation::getOptimizationSnapshot)
                .orElse(step.getSnapshot());
        Map<String, NodeType> candidatesById = new LinkedHashMap<>();
        for (NodeCandidate candidate : optimizationSnapshot.getCandidateNodes()) {
            candidatesById.put(candidate.getCandidateId(), candidate.getType());
        }
        boolean sawDecision = false;
        boolean allLocal = true;
        boolean fullOffloadingObserved = false;
        if (step.getMaGaResult().getBestChromosome().getGenes() == null) {
            return new DecisionObservation(false, true, false);
        }
        for (Gene gene : step.getMaGaResult().getBestChromosome().getGenes()) {
            sawDecision = true;
            NodeType type = candidatesById.get(gene.getSelectedCandidateId());
            if (type != NodeType.LOCAL || Math.abs(gene.getOffloadingRatio()) > EPSILON_SECONDS) {
                allLocal = false;
            }
            if (gene.getOffloadingRatio() >= 1.0 - EPSILON_SECONDS) {
                fullOffloadingObserved = true;
            }
        }
        return new DecisionObservation(sawDecision, allLocal, fullOffloadingObserved);
    }

    private static String validatePreviousDiagnostics(
            Map<String, Object> phase10i,
            Map<String, Object> phase10jPre,
            Map<String, Object> phase10jPre2,
            List<String> errors
    ) {
        String sourceRun = stringValue(phase10i, "sourceRun");
        String preSourceRun = stringValue(phase10jPre, "sourceRun");
        String pre2SourceRun = stringValue(phase10jPre2, "sourceRun");
        if (sourceRun == null || preSourceRun == null || pre2SourceRun == null) {
            errors.add("One or more prerequisite diagnostics are missing sourceRun.");
        } else if (!sourceRun.equals(preSourceRun) || !sourceRun.equals(pre2SourceRun)) {
            errors.add("Prerequisite diagnostics sourceRun mismatch: "
                    + sourceRun + ", " + preSourceRun + ", " + pre2SourceRun);
        }
        requireEquals(phase10i, "phase10iStatus", "COMPLETED", errors);
        requireTrue(phase10i, "readyForPhase10J", errors);
        requireEquals(phase10jPre, "phase10jPreStatus", "COMPLETED", errors);
        requireTrue(phase10jPre, "readyForPhase10J", errors);
        requireEquals(phase10jPre2, "phase10jPre2Status", "COMPLETED", errors);
        requireTrue(phase10jPre2, "readyForPhase10J", errors);
        return sourceRun;
    }

    private static boolean jsonSequenceValidated(
            Map<String, Object> phase10i,
            Map<String, Object> phase10jPre2
    ) {
        return intValue(phase10jPre2, "jsonSequenceReplayExitCode") == 0
                && intValue(phase10jPre2, "jsonSequenceWindowsExecuted")
                        == intValue(phase10i, "snapshotsGenerated")
                && intValue(phase10jPre2, "jsonSequenceTaskEvaluations")
                        == intValue(phase10i, "totalTasksAcrossSnapshots");
    }

    private static boolean jsonTimeSmokeValidated(Map<String, Object> phase10jPre2) {
        return intValue(phase10jPre2, "jsonTimeSmokeExitCode") == 0
                && intValue(phase10jPre2, "jsonTimeFutureLookAheadViolations") == 0;
    }

    private static Map<String, Object> readJson(ObjectMapper mapper, Path file) throws Exception {
        return mapper.readValue(file.toFile(), new TypeReference<>() { });
    }

    private static void requireEquals(
            Map<String, Object> values,
            String field,
            String expected,
            List<String> errors
    ) {
        String actual = stringValue(values, field);
        if (!expected.equals(actual)) {
            errors.add(field + " expected " + expected + " but was " + actual);
        }
    }

    private static void requireTrue(
            Map<String, Object> values,
            String field,
            List<String> errors
    ) {
        Object value = values.get(field);
        if (!(value instanceof Boolean b) || !b) {
            errors.add(field + " expected true but was " + value);
        }
    }

    private static String stringValue(Map<String, Object> values, String field) {
        Object value = values.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private static String finiteOrDash(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.9f", value)
                : "-";
    }

    private static void writeTrace(Path traceOutFile, List<TraceRow> rows) throws Exception {
        Files.createDirectories(traceOutFile.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(
                traceOutFile,
                StandardCharsets.UTF_8
        )) {
            writer.write(String.join(",",
                    "stepIndex",
                    "trigger",
                    "triggerTimeSeconds",
                    "observationTimeSeconds",
                    "sourceSnapshotId",
                    "sourceSnapshotTimeSeconds",
                    "exactTimestampMatch",
                    "pastSnapshotReuse",
                    "sourceSnapshotAdvanced",
                    "sourceSnapshotSkippedCount",
                    "futureLookAhead",
                    "tasksCount",
                    "vehiclesCount",
                    "candidateNodesCount",
                    "dynamicity",
                    "dynamicityLevel",
                    "suggestedReusePolicy",
                    "appliedReusePolicy",
                    "runtimeSeconds",
                    "horizonReachedAfterStep"
            ));
            writer.newLine();
            for (TraceRow row : rows) {
                writer.write(row.toCsv());
                writer.newLine();
            }
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private record TraceRow(
            int stepIndex,
            String trigger,
            double triggerTimeSeconds,
            double observationTimeSeconds,
            String sourceSnapshotId,
            double sourceSnapshotTimeSeconds,
            boolean exactTimestampMatch,
            boolean pastSnapshotReuse,
            boolean sourceSnapshotAdvanced,
            int sourceSnapshotSkippedCount,
            boolean futureLookAhead,
            int tasksCount,
            int vehiclesCount,
            int candidateNodesCount,
            double dynamicity,
            String dynamicityLevel,
            String suggestedReusePolicy,
            String appliedReusePolicy,
            double runtimeSeconds,
            boolean horizonReachedAfterStep
    ) {
        private String toCsv() {
            return String.join(",",
                    String.valueOf(stepIndex),
                    csv(trigger),
                    Double.toString(triggerTimeSeconds),
                    Double.toString(observationTimeSeconds),
                    csv(sourceSnapshotId),
                    Double.toString(sourceSnapshotTimeSeconds),
                    String.valueOf(exactTimestampMatch),
                    String.valueOf(pastSnapshotReuse),
                    String.valueOf(sourceSnapshotAdvanced),
                    String.valueOf(sourceSnapshotSkippedCount),
                    String.valueOf(futureLookAhead),
                    String.valueOf(tasksCount),
                    String.valueOf(vehiclesCount),
                    String.valueOf(candidateNodesCount),
                    Double.toString(dynamicity),
                    csv(dynamicityLevel),
                    csv(suggestedReusePolicy),
                    csv(appliedReusePolicy),
                    Double.toString(runtimeSeconds),
                    String.valueOf(horizonReachedAfterStep)
            );
        }
    }

    private record DecisionObservation(
            boolean sawDecision,
            boolean allLocal,
            boolean fullOffloadingObserved
    ) {
    }

    private record RunOutcome(
            int stepsExecuted,
            String stopReason,
            boolean fullTimeHorizonReached,
            boolean safetyGuardrailTriggered,
            double lastTriggerTimeSeconds,
            double lastObservationTimeSeconds,
            String lastSourceSnapshotId,
            double lastSourceSnapshotTimeSeconds,
            int distinctSourceSnapshotsObserved,
            int exactTimestampMatches,
            int pastSnapshotReuses,
            int sourceSnapshotAdvances,
            int sourceSnapshotSkips,
            List<String> skippedSnapshotIds,
            int futureLookAheadViolations,
            int noTemporalStepFailures,
            int emptyTaskSteps,
            int taskEvaluationsAcrossTemporalSteps,
            boolean allLocalDecisionsObserved,
            boolean fullOffloadingObserved
    ) {
        private static RunOutcome empty(String stopReason) {
            return new RunOutcome(
                    0,
                    stopReason,
                    false,
                    false,
                    Double.NaN,
                    Double.NaN,
                    null,
                    Double.NaN,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    0,
                    0,
                    0,
                    0,
                    false,
                    false
            );
        }
    }
}
