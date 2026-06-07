# Phase 14C.4.1 - Persistent Materialization and Literature Smoke Test

## Objective

Phase 14C.4.1 separates two workflows that must stay distinct before
calibration:

1. rare materialization from external InTAS assets into a persistent local
   MOSAIC scenario with a real navigation database;
2. frequent execution of that already materialized scenario through the live
   MA-GA runtime and native live detailed reporting.

The MA-GA core under `src/` is not modified.

## Materialization Versus Execution

Materialization performs:

```text
external InTAS checkout
    -> deterministic candidate_0045 extraction
    -> reduced SUMO net and route subset
    -> MOSAIC Scenario-Convert database creation
    -> route import into the database
    -> persistent scenario manifest
```

Execution performs:

```text
persistent scenario
    -> deploy into tmp/mosaic-25.2/scenarios/
    -> inject generated live runtime JAR
    -> mosaic.bat -s MaGaLiteratureBasedUrbanStudy
    -> summarize true live runtime traces
    -> smoke validate the run
```

The execution script never invokes InTAS, Scenario-Convert, `netconvert`, or
the materializer.

## Scenario-Convert

The official MOSAIC Scenario-Convert tool is required for the database step.
The script detects an explicit `-ScenarioConvert` path, `SCENARIO_CONVERT`,
`scenario-convert` commands on `PATH`, or the local extracted MOSAIC Extended
tool under `tmp/external-tools/`.

For MOSAIC 25.2 the verified CLI contains:

```text
database create
route import
scenario create
```

The script invokes the Scenario-Convert Java starter with an explicit
classpath. This avoids depending on the current directory of
`scenario-convert.bat` and keeps outputs inside the persistent scenario.

## Persistent Path

The default persistent output is:

```text
tmp/materialized-literature-scenarios/
  MaGaLiteratureBasedUrbanStudy/
    nominal-smoke-seed-104729/
```

The path contains:

```text
application/intas_literature_urban.db
application/application_config.json
application/ma_ga_live_state_config.json
application/ma_ga_live_runtime_config.json
mapping/mapping_config.json
sns/sns_config.json
cell/cell_config.json
cell/network.json
cell/regions.json
sumo/intas_literature_urban.net.xml
sumo/intas_literature_urban_nominal.rou.xml
scenario_config.json
materialization_manifest.json
reports/
```

These files are generated artifacts under `tmp/` and are not versioned.

## Manifest

`materialization_manifest.json` records:

```text
scenarioName
candidateId
intasCommit
density
durationProfile
seed
networkChecksum
routeChecksum
databaseChecksum
materializerVersion
scenarioConvertPath
scenarioConvertVersion
projection
rsuCoordinates
generatedAt
```

The materializer reuses an existing persistent scenario when the manifest
matches the requested inputs and the validator still passes. Otherwise it
requires `-ForceRebuild`.

## Database

The expected database is:

```text
application/intas_literature_urban.db
```

It is created from the reduced SUMO network and then populated with the
selected route subset. The tool never copies `Barnim.db` and never creates a
placeholder database.

The validator checks that the database:

- exists;
- has a SQLite header;
- is readable with `sqlite3`;
- has discoverable tables;
- has non-zero counts where applicable;
- is not identical to known diagnostic databases.

## Deploy

`deploy_materialized_literature_scenario.ps1` validates the persistent
scenario and copies only that scenario to:

```text
tmp/mosaic-25.2/scenarios/MaGaLiteratureBasedUrbanStudy/
```

It injects:

```text
tools/mosaic-live-maga-runtime/out/maga-live-maga-runtime.jar
tools/mosaic-adhoc-radio-diagnostic/out/maga-adhoc-radio-diagnostic.jar
```

The ad-hoc radio diagnostic JAR is required because the imported SUMO vehicle
prototypes still include the radio diagnostics app used by the live state
layer. Both JARs are generated local artifacts and are injected only into the
local MOSAIC scenario.

The deploy step does not regenerate the network, routes, or database.

## Smoke Run

`run_literature_scenario.ps1` builds the runtime JAR, deploys the persistent
scenario, runs MOSAIC, summarizes the run and executes:

```text
validate_literature_smoke_run.ps1
```

The smoke validator checks:

```text
simulationCompleted = true
configured Cell profile log observed
native detailed live report log observed
tasksGenerated > 0
tasksActivated > 0
snapshotRequests > 0
snapshotResolved > 0
gaJobsSubmitted > 0
gaJobsCompleted > 0
strategyApplications > 0
parallelGaViolations = 0
futureSnapshotViolations = 0
futurePoolViolations = 0
invalidPoolBandwidthViolations = 0
deltaTMaxMismatchViolations = 0
live-reporting artifacts present and non-empty
```

It does not require VEHICLE, EDGE, or CLOUD assignments. The smoke test
validates technical execution, not calibrated decision distribution.

For the `smoke` persistent materialization, the manifest records:

```text
TECHNICAL_SMOKE_THROTTLE_NOT_CALIBRATION
```

This lowers the workload arrival rate and uses a 500 ms coordinator tick only
for the first bounded end-to-end smoke run. It is explicitly not a calibration
parameter and does not change the nominal literature catalog.

## Native Reporting

The smoke run uses the native reporting added in 14C.3R. The detailed report is
built from the true live execution data and the real `TemporalStepResult`
objects produced during MOSAIC execution. It does not replay snapshots and does
not rerun MA-GA.

Expected local artifacts:

```text
live-maga-runtime/live_run_summary.json
live-maga-runtime/live_run_summary.md
live-maga-runtime/literature_smoke_validation.json
live-maga-runtime/literature_smoke_validation.md
live-maga-runtime/live-reporting/live_ga_job_events.jsonl
live-maga-runtime/live-reporting/live_temporal_step_records.jsonl
live-maga-runtime/live-reporting/live_detailed_execution_report.txt
live-maga-runtime/live-reporting/live_detailed_execution_report.md
live-maga-runtime/live-reporting/live_detailed_execution_report.json
```

## Commands

Materialize:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\materialize_literature_scenario.ps1 `
  -Density nominal `
  -DurationProfile smoke `
  -Seed 104729
```

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\run_literature_scenario.ps1 `
  -MaterializedScenarioRoot .\tmp\materialized-literature-scenarios\MaGaLiteratureBasedUrbanStudy\nominal-smoke-seed-104729 `
  -PrintDetailedLiveReport
```

## Limits

The scenario is still not a calibrated experimental campaign. Strategy
application remains diagnostic; real task execution, task migration,
checkpointing, CPU-residue modeling and 40-replicate orchestration remain
outside this phase.

## Boundary for 14C.4.2

The next step can build on the persistent scenario and smoke-validated runtime
to introduce calibration run orchestration and replicate management without
regenerating the database for every run.

## Superseded mobility-generation path

The original 14C.4.1 materializer attempted to derive concrete traffic subsets directly from the InTAS full-day demand. Phase 14C.4.2 replaces that mobility-generation path with `SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK`.

The persistent materialization boundary remains valid: Scenario-Convert still creates the real MOSAIC SQLite database once, while frequent runs reuse the materialized scenario. See `14C4_2_synthetic_calibrated_intas_subscenario.md` for the final design.
