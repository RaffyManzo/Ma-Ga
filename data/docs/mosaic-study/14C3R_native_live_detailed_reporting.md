# Phase 14C.3R - Native Live Detailed Reporting

## Objective

Phase 14C.3R adds detailed reporting to the true live MA-GA runtime execution.
The report is produced from data collected during the MOSAIC simulation itself:
GA submission, wall-clock completion, timeout detection, stale discard, strategy
application and the exact `TemporalStepResult` objects returned by the live
`TemporalWindowManager` invocation.

The MA-GA core under `src/` remains unchanged.

## Why Offline Replay Is Not Used

An offline replay of published snapshots would execute MA-GA again. That would
lose the real live facts that matter for the bridge:

- real wall-clock runtime of the GA worker;
- submission and completion wall-clock instants;
- wait-cap detection while the GA is still running;
- stale results discarded after timeout;
- strategy preserved while a job is active;
- difference between a completed result and an actually applied result;
- runtime errors observed by the live coordinator.

The native report therefore never calls `MaGaOptimizer` and never reruns
`TemporalWindowManager` for reporting.

## Data Collected During Live Execution

Each GA job receives a stable monotonic id:

```text
live_ga_job_000001
live_ga_job_000002
...
```

The job record stores:

```text
jobId
windowIndex
triggerType
submissionSimulationTimeNs
submissionWallClockNs
snapshotId
snapshotTimeSeconds
taskCount
candidateCount
deltaTMaxAtSubmissionSeconds
wallClockDeadlineNs
timeoutDetectedBeforeCompletion
waitCapDetectedWallClockNs
waitCapDetectedSimulationTimeNs
completionWallClockNs
gaRuntimeWallClockSeconds
deltaTMaxFromCompletedStepSeconds
deltaTMaxMismatchSeconds
finalStatus
appliedAtSimulationTimeNs
errorType
errorMessage
```

Final states are:

```text
APPLIED
STALE_DISCARDED
FAILED
NULL_STEP_RESULT
SHUTDOWN_IN_FLIGHT
```

Intermediate events include:

```text
SUBMITTED
WAIT_CAP_REACHED
COMPLETED_WITHIN_BOUND
FRESH_REOPTIMIZATION_REQUESTED
```

## Classes Added

The reporting package is:

```text
tools/mosaic-live-maga-runtime/src/org/eclipse/mosaic/app/maga/liveruntime/reporting/
```

Classes:

```text
LiveNativeReportingCollector
LiveGaJobRecord
LiveTemporalStepRecord
LiveDetailedReportWriter
LiveDetailedReportPrinter
LiveReportingJsonlWriter
LiveReportingSummary
```

Responsibilities are separated:

- the collector records live events and keeps completed `TemporalStepResult`
  references in memory;
- the JSONL writer persists incremental events and explicit DTOs;
- the summary aggregates final counts and runtime statistics;
- the report writer emits TXT, Markdown and JSON final reports;
- the report printer combines live-specific sections with compatible historical
  diagnostic printers.

## Classes Modified

Runtime classes:

```text
LiveGaJob
LiveGaCompletion
LiveGaExecutionCoordinator
MaGaLiveRuntimeConfig
MaGaLiveRuntimeCoordinatorApp
LiveStateLayerRuntimeFacade
```

Script and literature scenario integration:

```text
tools/mosaic-live-maga-runtime/summarize-run.ps1
tools/intas-literature-scenario/build_intas_literature_scenario.py
tools/intas-literature-scenario/validate_literature_configuration.py
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/application/ma_ga_live_runtime_config.template.json
```

## Historical Printers Reused

The final TXT report creates:

```java
new TemporalWindowResult(appliedSteps)
```

where `appliedSteps` are the real `TemporalStepResult` objects that were
actually applied during the live run. This aggregation does not execute any GA.

Compatible historical printers are reused directly:

```text
DeepTemporalWindowDiagnosticPrinter
DeadlineBestEffortDiagnosticPrinter
CloudGatewayDiagnosticPrinter
AccessLinkDynamicityDiagnosticPrinter
BandwidthPoolDiagnosticPrinter
MobilityDiagnosticPrinter
LatencyDiagnosticPrinter
AdaptiveWindowDiagnosticPrinter
TemporalTimingDiagnosticPrinter
PopulationReuseDecisionDiagnosticPrinter
SystemStateSourceDiagnosticPrinter
```

## Historical Sections Not Reused

`AdaptiveWindowReportPrinter` and `CandidateFilteringPrinter` depend on
offline filtering structures that are not produced by the live runtime. They
are not called by the native reporter.

They are replaced by live-specific sections:

```text
LIVE REPORT METADATA
LIVE GA JOB SUMMARY
LIVE GA WALL-CLOCK TIMING
LIVE RESULT APPLICATION SUMMARY
LIVE STALE RESULT SUMMARY
LIVE OVERRUN SUMMARY
LIVE SNAPSHOT AUDIT
LIVE ASSIGNMENT SUMMARY
LIVE CONFIGURED CELL PROFILE VS RUNTIME ACCOUNTING
LIVE REPORT LIMITATIONS
```

## Artifacts

Artifacts are written under the local run directory:

```text
tmp/mosaic-25.2/logs/<run>/live-maga-runtime/live-reporting/
```

Incremental files:

```text
live_ga_job_events.jsonl
live_temporal_step_records.jsonl
live_applied_window_records.csv
live_discarded_window_records.csv
```

Final reports:

```text
live_detailed_execution_report.txt
live_detailed_execution_report.md
live_detailed_execution_report.json
```

The JSONL records are explicit DTOs. They do not serialize the whole Java graph
opaquely.

## Summarizer

`summarize-run.ps1` now detects native report artifacts and includes them in:

```text
live_run_summary.json
live_run_summary.md
```

With:

```powershell
-PrintDetailedLiveReport
```

it prints the existing TXT report. It does not generate the detailed report and
does not rerun MA-GA.

## Runtime Configuration

The runtime config supports optional fields:

```json
{
  "nativeLiveDetailedReportingEnabled": true,
  "nativeLiveDetailedReportPrintToConsole": false
}
```

If omitted, both default to `false` for backward compatibility.

The literature-based runtime template enables detailed reporting but does not
print the full report during MOSAIC execution by default.

## Harness

The reporting harness is:

```text
tools/mosaic-live-maga-runtime/harness/LiveNativeDetailedReportingHarness.java
tools/mosaic-live-maga-runtime/harness/run_phase14c3r_reporting_harness.ps1
```

It validates:

- APPLIED jobs produce submitted/completed/applied events and applied CSV rows;
- STALE_DISCARDED jobs are retained separately;
- FAILED jobs are retained separately;
- NULL_STEP_RESULT is recorded explicitly;
- wall-clock runtime, `DeltaT_max` and mismatch fields are persisted;
- JSONL events are flushed incrementally;
- TXT, Markdown and JSON reports are produced;
- historical report aggregation uses only APPLIED steps;
- reporting is independent of `publishedSnapshotCopyLimit`;
- disabled reporting remains backward compatible.

## Build and Validation

Required checks:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\build.ps1

powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\harness\run_phase14c3r_reporting_harness.ps1

powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\harness\run_phase14c3_harness.ps1

py -3 -B tools\intas-literature-scenario\build_intas_literature_scenario.py `
  --intas-root C:\Users\raffa\IdeaProjects\external\InTAS `
  --output-root C:\Users\raffa\IdeaProjects\maga-core\tmp\intas-literature-14c3r-dryrun `
  --density all `
  --duration-profile nominal `
  --dry-run

py -3 -B tools\intas-literature-scenario\validate_literature_configuration.py `
  --scenario-root C:\Users\raffa\IdeaProjects\maga-core\tmp\intas-literature-14c3r-dryrun\MaGaLiteratureBasedUrbanStudy
```

Expected harness markers:

```text
PHASE14C3R_REPORTING_HARNESS_PASSED
PHASE14C3_HARNESS_PASSED
```

## Compatibility

The reporting path is opt-in. Diagnostic scenarios that omit the new runtime
fields keep their previous behavior and do not require report artifacts.

The live runtime still writes existing traces:

```text
live_ga_runtime_trace.csv
live_strategy_application_trace.csv
live_bridge_snapshot_trace.csv
live_overrun_trace.csv
```

## Limits and Phase 14C.4.1 Boundary

The detailed report is native to the true live runtime, but strategy
application remains diagnostic. It does not execute real remote tasks, migrate
tasks, checkpoint work, resume uploads/downloads or calibrate scientific
parameters.

Phase 14C.4.1 should build on this reporting layer to support calibration run
orchestration and evidence collection without changing MA-GA core semantics.
