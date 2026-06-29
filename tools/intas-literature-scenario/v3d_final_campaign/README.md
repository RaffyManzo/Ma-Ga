# V3-D Final Campaign Batch Tooling

This directory contains the isolated tooling for the final paced MA-GA campaign.

The only operational entry point is:

```powershell
tools/intas-literature-scenario/v3d_final_campaign/v3d_final_campaign.ps1
```

Modes:

- `Validate`: static validation only.
- `Prepare`: materialize and validate all 66 unique scenarios; MOSAIC is not run.
- `DryRun`: validate all 68 commands and output paths; MOSAIC is not run.
- `SelfTest`: deterministic fail-soft and continuation test; MOSAIC is not run.
- `Ready`: freeze Git, JAR, plans, tooling and materialization hashes.
- `PrepareAll`: run Validate, Prepare, DryRun, SelfTest and Ready.
- `Execute`: execute all 68 runs sequentially from G01 through G05.
- `Report`: regenerate group and cumulative reports from saved run states.

Execution policy:

- one sequential run at a time;
- no automatic retries;
- an ordinary run failure is recorded and later safe runs continue;
- a residual-process cleanup failure aborts the batch because continuing would
  contaminate later runs;
- terminal run states are not executed again on resume;
- reports are refreshed after every run;
- G01, G02, G03, G04 and G05 each have CSV, JSON and Markdown reports;
- a cumulative report is maintained throughout the batch.

The canonical materializer does not propagate the workload seed. Preparation
therefore reuses the validated final-campaign overlay semantics and explicitly
sets:

```text
workloadSeed = mobilitySeed + 1000003
```

G03 reuses the materialization created for `V3D-G02-CFG-N-I-104729`.
There are 66 unique materializations and 68 MOSAIC executions.

No Java, fitness, repair, chromosome or genetic operator is modified.

Operational run evidence and continuously refreshed reports are written under
`tmp/v3d-final-campaign-results/`. Keeping these outputs outside versioned
paths preserves a clean working tree and allows safe resume. A final audited
export will be created only after the batch has completed.

Readiness also requires at least 5 GiB of free space on the repository volume before the batch can be frozen.

Additional safety guarantees:

- Python bytecode generation is disabled, so validation and preparation do not
  create untracked `__pycache__` files.
- incomplete or identity-mismatched materializations are archived before a
  controlled retry; they are never overwritten silently.
- readiness freezes a deterministic fingerprint of every file in all 66
  materialization trees.
- execution refuses to start when residual MOSAIC/SUMO processes are present.
- stale batch locks are archived automatically only when their recorded PID is
  no longer active.
- residual-process queries are treated as safety-critical: a query failure
  aborts rather than being interpreted as an empty process set.

## Frozen runtime policy

Every generated scenario is forced to the runtime policy validated by the
paced V3-D pilot. In particular, `gaWallClockBudgetMode` is
`LIVE_ADAPTIVE`; the adaptive estimator, freshness cap and cooperative
best-so-far parameters are pinned in `v3d_campaign_spec.json`.

The canonical scenario template currently defaults to `CONFIGURED_STATIC`.
The V3-D adapter therefore applies and verifies the policy explicitly instead
of relying on that template default. `Validate` includes a policy self-test,
and `Ready` rejects any materialization whose runtime config or manifest does
not match the frozen policy.

## Final execution preflight

`DryRun` and `Ready` require the diagnostic ad-hoc radio JAR used by the
deployment script, validate that both runtime JARs are readable JAR archives,
parse every PowerShell dependency, and check the required executables.

The readiness marker freezes the diagnostic JAR, all execution scripts and
the MOSAIC launcher. `Execute` no longer depends on remote Git availability;
it checks the locally frozen HEAD, clean tree, artifact hashes and MOSAIC
launcher before G01.

Run failures are classified according to the phase actually reached:
preflight, execution, post-processing, validation or cleanup.
