# Phase 14C.3 - Live-State Workload and Cell Profile Extension

## Objective

Phase 14C.3 removes the last temporary compatibility pieces from the
literature-based text scenario and minimally extends the live-state layer:

- `LOCAL` and remote `VEHICLE` CPU are separately configurable;
- workload can be generated deterministically per active vehicle;
- static `taskProfiles` remain supported for historical diagnostics;
- configured Cell profile metadata is distinct from diagnostic runtime
  accounting.

The MA-GA core under `src/` remains unchanged.

## Classes Changed

The live-state layer changes are intentionally local:

```text
MaGaLiveStateConfig.java
LiveSeededPoissonWorkloadGenerator.java
LiveStateCache.java
LiveLocalAndV2vCandidatePreviewBuilder.java
MaGaLiveStateCoordinatorApp.java
```

The live MA-GA runtime facade also receives a small adapter so runtime
snapshots can use the same generated workload:

```text
LiveStateLayerRuntimeFacade.java
MaGaLiveRuntimeCoordinatorApp.java
```

`tools/mosaic-live-state-layer/build.ps1` now keeps the generated JAR under
`tools/mosaic-live-state-layer/out/` instead of copying it into a versioned
scenario.

## Compatibility

Historical scenarios continue to work with static `taskProfiles`. The new
`workloadGeneration` section is optional:

```text
missing or enabled=false -> legacy static taskProfiles path
enabled=true             -> generated workload path, taskProfiles may be []
```

If a legacy config omits `remoteVehicleCpuCyclesPerSecond`, the live-state
config loader falls back to the local CPU value to preserve old diagnostics.
The literature-based validator requires the explicit remote CPU field.

## CPU LOCAL and VEHICLE

`LOCAL` candidates still use:

```text
localCpuCyclesPerSecond
localCpuSource
```

`VEHICLE` candidates now use:

```text
remoteVehicleCpuCyclesPerSecond
remoteVehicleCpuSource
```

For the literature scenario both are currently configured to:

```text
1,000,000,000 cycles/s
source = LITERATURE_BASED_RANGE_CHOICE
```

The harness uses a deliberately different remote value (`750,000,000`) to
prove that the V2V builder reads the remote field rather than the local one.

## Poisson Workload

The generated literature config uses:

```json
"workloadGeneration": {
  "enabled": true,
  "mode": "SEEDED_POISSON_PER_ACTIVE_VEHICLE",
  "randomSeed": 104729,
  "startTimeMs": 7000,
  "arrivalRateTasksPerSecondPerActiveVehicle": 1.0,
  "maxGeneratedTasksPerTickPerVehicle": 10
}
```

The generator uses only `java.util.Random`. The PRNG is initialized once from
the configured seed and is not reset per tick.

For each tick:

```text
lambda = arrivalRateTasksPerSecondPerActiveVehicle * tickIntervalSeconds
```

Vehicles are sorted with `LiveStateCache.naturalCompare`. Tasks are generated
only for vehicles active in the causal view at the current tick. Generated
task IDs are deterministic:

```text
task_generated__<profileId>__<vehicleId>__t_<tickTimeNs>__seq_<sequence>
```

## Causal Tick Order

The coordinator order is:

```text
1. observe active vehicles at tickTimeNs
2. generate and install new task definitions for those active vehicles
3. activate tasks due at tickTimeNs
4. build snapshot view
5. build LOCAL, V2V, access link, gateway pool and remote previews
6. mark exported tasks after writing previews/snapshots
```

Generated tasks receive:

```text
activationTimeNs = tickTimeNs
```

No future task is exposed.

## Task Profiles

The literature workload profiles are:

```text
light:
  weight = 0.50
  input = 160000 bits
  output = 8000 bits
  CPU = 200000000 cycles
  deadline = 0.5 s

medium:
  weight = 0.35
  input = 800000 bits
  output = 8000 bits
  CPU = 600000000 cycles
  deadline = 1.0 s

heavy:
  weight = 0.15
  input = 8000000 bits
  output = 8000 bits
  CPU = 3200000000 cycles
  deadline = 4.0 s
```

The 1 kB output size is recorded as a controlled assumption.

## Cell Separation

The literature scenario now has a top-level configured profile:

```text
configuredCellProfile.source = LITERATURE_BASED_CONFIGURED_CELL_PROFILE
```

The live accounting source remains:

```text
cellDiagnosticAccounting.bandwidthSource =
  DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES
```

These are deliberately distinct. The configured profile documents the
literature-based scenario setting; the runtime buckets still come from
diagnostic controlled Cell messages until the next source-mode extension.

Startup logs include:

```text
LIVE_STATE_CONFIGURED_CELL_PROFILE_LOADED
```

with profile id, technology, classification, capacity, RTT, one-way delay and
runtime accounting source.

## Harness

The harness is:

```text
tools/mosaic-live-state-layer/harness/LiveSeededPoissonWorkloadGeneratorHarness.java
tools/mosaic-live-state-layer/harness/run_phase14c3_harness.ps1
```

It validates:

- same seed and same inputs produce identical task IDs/profiles;
- different seed changes the sequence;
- rate `0` generates no tasks;
- no active vehicles generate no tasks;
- inactive vehicles do not receive tasks;
- generated tasks are causal;
- profiles are only `light`, `medium`, `heavy`;
- V2V candidates use remote VEHICLE CPU;
- configured Cell profile and diagnostic accounting are distinct.

## Textual Scenario Generation

The InTAS materializer now emits:

```text
remoteVehicleCpuCyclesPerSecond
remoteVehicleCpuSource
configuredCellProfile
taskProfiles = []
workloadGeneration
```

The validator rejects any residual `bootstrap_medium_until_14C3` marker.

## Build and Validation

Required commands:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\build.ps1

powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\harness\run_phase14c3_harness.ps1

powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\build.ps1

py -3 -B tools\intas-literature-scenario\build_intas_literature_scenario.py `
  --intas-root C:\Users\raffa\IdeaProjects\external\InTAS `
  --output-root C:\Users\raffa\IdeaProjects\maga-core\tmp\intas-literature-14c3-dryrun `
  --density all `
  --duration-profile nominal `
  --dry-run

py -3 -B tools\intas-literature-scenario\validate_literature_configuration.py `
  --scenario-root C:\Users\raffa\IdeaProjects\maga-core\tmp\intas-literature-14c3-dryrun\MaGaLiteratureBasedUrbanStudy
```

## Limits and Phase 14C.4 Boundary

Still out of scope:

- 40-replicate runner;
- calibrated workload rate matrix;
- scientific calibration;
- MOSAIC end-to-end execution for the literature scenario;
- Scenario-Convert database generation, because Scenario-Convert is still not
  available locally.

Phase 14C.4 should prepare calibration execution and replicate orchestration
without changing the MA-GA core semantics.
