# InTAS literature scenario refactor manifest

## Preserved local dependencies

These folders are intentionally preserved:

```text
tmp/mosaic-25.2
tmp/external-tools
```

## Updated files

```text
tools/intas-literature-scenario/build_intas_literature_scenario.py
tools/intas-literature-scenario/materialize_literature_scenario.ps1
tools/intas-literature-scenario/validate_intas_source.py
tools/intas-literature-scenario/validate_literature_configuration.py
tools/intas-literature-scenario/validate_materialized_literature_scenario.py
tools/intas-literature-scenario/config/literature_scenario_targets.json
tools/intas-literature-scenario/README.md
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/README.md
data/docs/mosaic-study/14C4_1_persistent_materialization_and_literature_smoke_test.md
data/docs/mosaic-study/MOSAIC_LIVE_INTEGRATION_AND_EXECUTION_GUIDE.md
```

## Added files

```text
tools/intas-literature-scenario/config/candidate_0045_edge_ids.txt
tools/intas-literature-scenario/config/synthetic_mobility_profile.json
tools/intas-literature-scenario/quick_literature_workflow.ps1
tools/intas-literature-scenario/show_latest_literature_report.ps1
tools/intas-literature-scenario/REFACTOR_MANIFEST.md
tools/intas-literature-scenario/REFACTOR_VERIFICATION.md
data/docs/mosaic-study/14C4_2_synthetic_calibrated_intas_subscenario.md
```

## Removed local generated folders

The packaged refactor removes only obsolete local artifacts from the earlier replay experiments:

```text
tmp/manual-reduction-proof-14c41
tmp/audit-materialized-14c41
tmp/audit-materialized-14c41.zip
tmp/intas-literature-14c3-dryrun
tmp/intas-literature-14c3r-dryrun
tmp/intas-literature-config-dryrun
tmp/intas-literature-dryrun
tmp/intas-literature-materialization-staging
tmp/materialized-literature-scenarios
```

They are regenerated when required and are not runtime dependencies.

## Not modified

```text
src/
tools/mosaic-live-maga-runtime/
tools/mosaic-live-state/
tools/mosaic-adhoc-radio-diagnostic/
tmp/mosaic-25.2/
tmp/external-tools/
```
