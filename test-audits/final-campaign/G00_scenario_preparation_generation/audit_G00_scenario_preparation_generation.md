# G00 Scenario Preparation And Generation Audit

## 1. Identita della campagna

- campaign_id: `final-test-campaign`
- group_id: `G00`
- group_name: `scenario preparation and generation`
- phase: `G00F - finalizzazione e normalizzazione dell'audit G00`

## 2. Commit e stato Git

- frozen_branch: `MOSAIC/SUMO-integration`
- frozen_commit: `5a9477735a3d707a5f000a64653cd2a6fc7f2007`
- campaign_branch: `testing/final-campaign`
- campaign_head_before_g00_commit: `6449978026d205c3f0949cd5532782fd447fed1d`
- frozen Java path diff: empty at G00F precondition

Working tree status at audit generation is captured in `git_status_short_G00F.txt`.

## 3. Obiettivi

G00 prepared campaign-specific tooling, materialized the planned scenario instances, validated the materialized inputs and produced the G00 audit bundle. G00F normalized the audit artifacts without rematerializing scenarios and without running MOSAIC.

## 4. Configurazioni e run

- planned materializations: `69`
- completed materializations: `69`
- validated materializations: `63`
- warning materializations: `6`
- failed materializations: `0`
- blocked materializations: `0`
- unique Materialization_ID: `69`
- unique target directories: `69`

No Run_ID was executed in G00; planned runtime repetitions remain future MOSAIC runs.

## 5. Comandi eseguiti

- `git branch --show-current`
- `git rev-parse HEAD`
- `git status --short`
- `git diff --name-only -- src tools/mosaic-live-maga-runtime/src tools/mosaic-live-state-layer/src tools/mosaic-adhoc-radio-diagnostic/src`
- `python tools/intas-literature-scenario/final_campaign/materialize_final_campaign.py --mode check`
- `python tools/intas-literature-scenario/final_campaign/materialize_final_campaign.py --mode audit`

G00F did not run SUMO, MOSAIC, Scenario-Convert, archive, pilot, all or materialization modes.

## 6. Output grezzi

- materialized scenario root: `tmp/materialized-literature-scenarios/final-test-campaign`
- summary: `test-audits/final-campaign/G00_scenario_preparation_generation/materialization_summary_all.json`
- input index: `test-results/final-campaign/G00_scenario_preparation_generation/materialization_input_index.csv`
- reproducibility comparison: `test-results/final-campaign/G03_reproducibility_duration/repro_materialization_comparison.json`
- metrics: `test-audits/final-campaign/G00_scenario_preparation_generation/metrics_G00.csv`
- anomalies: `test-audits/final-campaign/G00_scenario_preparation_generation/anomalies_G00.csv`
- evidence manifest: `test-audits/final-campaign/G00_scenario_preparation_generation/evidence_manifest_G00.json`

## 7. Esito validator

The 69 validation reports record `63` `MATERIALIZED_VALIDATED` instances and `6` `MATERIALIZED_WITH_WARNINGS` instances. Failed and blocked materializations are zero. Aggregate SUMO diagnostic counters from the materialization reports are zero for errors, teleports and emergency braking mentions.

## 8. Metriche

`metrics_G00.csv` uses the long schema from `audit_bundle_schema.md` and contains `18` rows. Main values: planned=69, completed=69, validated=63, warnings=6, failed=0, blocked=0.

## 9. Copertura Test_ID

`T-001` covers campaign preparation and freeze evidence, `T-002` covers materialized artifact evidence and validator counters, and `T-014` is included as preliminary reproducibility evidence. G03 keeps responsibility for final reproducibility interpretation.

## 10. Anomalie

`anomalies_G00.csv` contains `20` rows. The six direct engineering profiles are accepted limitations. Intermediate failed or blocked attempts are marked `RESOLVED`. The raw/logical CFG-REPRO distinction and the post-materialization plan hash change are documented limitations, not materialization errors.

## 11. Interpretazione tecnica

The campaign-specific tooling produced one manifest and one validation report for each planned materialization. Direct route profiles are explicitly marked as directed engineering profiles and are excluded from main factorial claims. The ADAPTIVE GA case remains non-canonical and is accepted only by the campaign validator.

### Canonical deploy compatibility correction

G00C identified and resolved a metadata compatibility issue exposed by the failed G01 deploy attempt. The campaign validator now checks the canonical `materialization_manifest.json` and `reports/intas_literature_materialization_report.json` metadata required by the deploy validator. The repair updated only metadata and validation reports; it did not change mobility, workload, resource or GA runtime configuration files.


## 12. Risultati riutilizzabili nella tesi

Le 69 istanze previste dalla matrice sono state materializzate.

Sessantatre istanze hanno superato il validator senza warning.

Sei profili diretti hanno prodotto esclusivamente i warning metodologici attesi.

Il confronto logico delle due materializzazioni CFG-REPRO non ha rilevato differenze sostanziali.

## 13. Limiti

MOSAIC non e stato eseguito in G00 o G00F. Nessuna affermazione prestazionale sul MA-GA deriva da questo audit.

Il sourceFiles hash di scenario_instance_plan.csv nei manifest rappresenta il piano operativo al momento della materializzazione. Il piano corrente e stato successivamente aggiornato con stati e percorsi, mentre i parametri materializzati sono congelati in materialization_input_index.csv e nei singoli manifest.

I profili route diretti sono profili funzionali di engineering, non profili literature-calibrated. `CFG-G-SPARSE` mantiene `EMPTY_TASK_SET` come obiettivo osservativo, non come risultato garantito.
