# G02B controlled ablation tooling

G02B isolates three MA-GA ablation variants introduced after the verified G02 baseline:

- `LOCAL_ONLY`
- `NO_MOBILITY_PENALTY`
- `COLD_START_NO_REUSE`

The scientific campaign has 45 planned runs: 3 configurations (`CFG-N-I`, `CFG-N-S`, `CFG-H-I`) x 5 seeds x 3 variants. The `FULL_MA_GA` baseline is not repeated because the matching 15 G02 runs already exist and are paired by `config_id + seed`. `CFG-H-S` remains excluded.

## Inputs and outputs

Inputs come from the verified final-campaign materializations and reports:

- Plan source: `data/docs/testing/final-campaign/scenario_instance_plan.csv`
- G02 materializations: `tmp/materialized-literature-scenarios/final-test-campaign/`
- G02 metrics: `test-results/final-campaign/G02_main_factorial_experiments/metrics_G02_runs.csv`

G02B writes only isolated ignored state and scenario copies:

- Scenario copies: `tmp/materialized-literature-scenarios/g02b-ablation/`
- Runtime results and aggregation: `tmp/g02b-ablation/results/`
- Registry, bundles, and self-test reports: `tmp/g02b-ablation/state/`

Versioned tooling and plans live in this directory:

- `g02b_spec.json`
- `g02b_tool.py`
- `g02b.ps1`
- `plans/g02b_scientific_plan.csv`
- `plans/g02b_smoke_plan.csv`

The implementation base commit is `39751f5f0532f7a077a03df92627616683cb887d`. Tooling checks require this commit to be an ancestor of the effective HEAD, not that HEAD remains exactly equal to it. Run modes additionally require local HEAD to match `origin/experiment/g02b-ablation`, a clean working tree, unchanged `testing/final-campaign` heads, and no Java changes after the implementation base.

## Scenario preparation

Preparation never rebuilds topology, routes, demand, workload, density, resources, duration, seed, or GA mode. For each G02B row the tool:

1. Finds the corresponding G02 materialization from the official final-campaign plan.
2. Copies it under `tmp/materialized-literature-scenarios/g02b-ablation/`.
3. Changes only `application/ma_ga_live_runtime_config.json` to set `experimentalVariant`.
4. Writes `g02b_manifest.json` with provenance, branch, commit, hashes, variant, config, seed, runtime JAR expectation, and an allowlist of files permitted to differ.
5. Validates that every non-allowlisted file is byte-identical to the G02 source.

Allowed differences are limited to:

- `application/ma_ga_live_runtime_config.json`
- `g02b_manifest.json`
- `reports/g02b_pre_run_validation.json`

## Entry point

Default mode is non-destructive:

```powershell
.\tools\intas-literature-scenario\g02b\g02b.ps1
```

Available modes:

- `Check`: verify repo state, ignored roots, and plan consistency.
- `PrepareSmoke`: prepare and validate the four short smoke scenarios.
- `RunSmoke`: run the four smoke scenarios sequentially.
- `ResumeSmoke`: resume smoke processing without rerunning validated or completed runs.
- `PrepareCampaign`: prepare and validate the 45 scientific scenarios.
- `RunCampaign`: run the 45 scientific scenarios sequentially.
- `ResumeCampaign`: resume scientific processing without rerunning validated or completed runs.
- `Validate`: rerun prepared-scenario validation.
- `Aggregate`: aggregate paired scientific G02/G02B results; smoke output is excluded.
- `Bundle`: package plans and G02B result artifacts.

Run modes perform guard checks before execution: branch, implementation-base ancestry, clean working tree, local/remote HEAD match, no execution on `testing/final-campaign`, no Java changes after the implementation base, runtime JAR presence, runtime JAR hash/size, and G02B variant class presence. Each run is preceded by prepared-scenario revalidation. The wrapper records `g02b_run_context.json`, snapshots the compatible MOSAIC log directories before and after execution, and accepts exactly one new directory. It runs one MOSAIC job at a time and refuses to overwrite existing output.

Resume modes skip only `VALIDATED`. If output and context exist with `COMPLETED` or `VALIDATION_FAILED`, resume tries validation only. It does not rerun MOSAIC automatically. `RUN_FAILED` requires manual intervention.

## Recommended sequence

Generate and check plans:

```powershell
py -3 -B .\tools\intas-literature-scenario\g02b\g02b_tool.py --mode generate-plan
.\tools\intas-literature-scenario\g02b\g02b.ps1 -Mode Check
```

Prepare smoke scenarios:

```powershell
.\tools\intas-literature-scenario\g02b\g02b.ps1 -Mode PrepareSmoke
```

After manual review, execute smoke scenarios:

```powershell
.\tools\intas-literature-scenario\g02b\g02b.ps1 -Mode RunSmoke
.\tools\intas-literature-scenario\g02b\g02b.ps1 -Mode ResumeSmoke
```

Prepare the scientific campaign:

```powershell
.\tools\intas-literature-scenario\g02b\g02b.ps1 -Mode PrepareCampaign
```

Run or resume the scientific campaign:

```powershell
.\tools\intas-literature-scenario\g02b\g02b.ps1 -Mode RunCampaign
.\tools\intas-literature-scenario\g02b\g02b.ps1 -Mode ResumeCampaign
```

Validate and aggregate:

```powershell
.\tools\intas-literature-scenario\g02b\g02b.ps1 -Mode Validate
.\tools\intas-literature-scenario\g02b\g02b.ps1 -Mode Aggregate
.\tools\intas-literature-scenario\g02b\g02b.ps1 -Mode Bundle
```

## Post-run validation focus

The validator loads three runtime artifacts separately:

- canonical summary: `live_run_summary.json`
- native detailed report: `live-reporting/live_detailed_execution_report.json`
- temporal records: `live-reporting/live_temporal_step_records.jsonl`
- canonical validator: `literature_smoke_validation.json`

Missing required files fail validation. The canonical summary supplies counters, runtime metrics, snapshot lag, violation totals, and final interval fields. The native report supplies `experimentalVariant`, `effectiveFitnessWeights` (`wT`, `wL`, `wM`, `wR`), optimization source, reuse policy, top-level assignment counters, and `jobRecords`. The JSONL records supply trigger, nested dynamicity, adaptive window decisions, and reuse mode. The canonical validator must report `LITERATURE_SMOKE_TEST_PASSED` and `simulationCompleted = true`.

The validator also writes `g02b_causal_job_records.csv` per run to support analysis of the long final interval without applied strategies. The causal CSV uses the Java `LiveGaJobRecord` fields directly, converts simulation nanoseconds to seconds, joins temporal records by `jobId` first and `windowIndex` only as fallback, and includes trigger, reuse modes, dynamicity, window sizing, deadline, timeout, error, and classification fields.

If the canonical runner exits non-zero, the wrapper still inspects the before/after MOSAIC log directory set. When exactly one new run exists and all required artifacts are complete with `simulationCompleted = true`, the run is recorded as `COMPLETED` and proceeds to validation. Missing directories or incomplete artifacts become `RUN_FAILED`; canonical validation failures after complete artifacts become `VALIDATION_FAILED`.

For `NO_MOBILITY_PENALTY`, total fitness is marked as not directly comparable with `FULL_MA_GA`, because the objective function changes when mobility weight is zeroed and the other weights are renormalized.

Aggregation is scientific-only by default. Complete aggregation requires 45 scientific PASS validations, 15 config/seed pairs, 15 runs per variant, and no duplicates. Smoke validations may be reviewed separately but are not included in the scientific paired result.

Bundle mode includes the spec, README, plans, registry, G02B manifests, pre-run validation reports, run contexts, post-run validations, causal CSVs, aggregate outputs, Git metadata, and an internal bundle manifest with path, size, and SHA-256 for each included entry. Self-test folders are excluded.
