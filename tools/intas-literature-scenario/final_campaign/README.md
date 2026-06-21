# Final Test Campaign Tooling

Tooling dedicated to the MA-GA final test campaign on branch
`testing/final-campaign`.

This directory is intentionally separate from the canonical InTAS literature
scenario materializer. The campaign orchestrator reads the documented
planning CSV files, invokes the frozen builder, applies overlays only to the
materialized copy, runs campaign-specific validation, and writes per-instance
manifests.

The tooling must not modify:

- `data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/`
- `tools/intas-literature-scenario/config/`
- canonical validators or materializers
- Java/runtime/core sources

## Files

- `final_campaign_spec.json`: campaign constants, workload profiles, direct
  route profiles, baseline resource values, pilot list, and expected paths.
- `materialize_final_campaign.py`: checkpoint-aware orchestrator for syntax
  checks, cleanup archival, materialization, validation, plan updates, repro
  comparison, and G00 audit generation.
- `validate_final_campaign.py`: campaign validator for a single materialized
  instance.
- `materialize_final_campaign.ps1`: PowerShell entry point.

## Checkpoints

Run from the repository root.

```powershell
python tools/intas-literature-scenario/final_campaign/materialize_final_campaign.py --mode check
python tools/intas-literature-scenario/final_campaign/materialize_final_campaign.py --mode archive
python tools/intas-literature-scenario/final_campaign/materialize_final_campaign.py --mode pilot
python tools/intas-literature-scenario/final_campaign/materialize_final_campaign.py --mode all
```

The PowerShell wrapper accepts the same `mode` values:

```powershell
tools/intas-literature-scenario/final_campaign/materialize_final_campaign.ps1 -Mode check
```

## Outputs

Concrete scenarios are written only under the `target_directory` values from
`data/docs/testing/final-campaign/scenario_instance_plan.csv`, currently rooted
at:

```text
tmp/materialized-literature-scenarios/final-test-campaign/
```

Campaign audit outputs are written under:

```text
test-audits/final-campaign/G00_scenario_preparation_generation/
```

The CFG-REPRO comparison is written under:

```text
test-results/final-campaign/G03_reproducibility_duration/repro_materialization_comparison.json
```
