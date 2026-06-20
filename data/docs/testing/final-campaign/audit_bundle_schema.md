# Audit Bundle Schema

## Required location

For each group, create this bundle only when that group is actually executed:

~~~text
test-audits/final-campaign/<Gxx_nome>/
  audit_<Gxx_nome>.md
  metrics_Gxx.csv
  evidence_manifest_Gxx.json
  anomalies_Gxx.csv
~~~

No audit bundle is generated in this planning subphase.

## Markdown audit sections

Every audit_<Gxx_nome>.md must contain these sections in order:

1. Identita della campagna
2. Commit e stato Git
3. Obiettivi
4. Configurazioni e run
5. Comandi eseguiti
6. Output grezzi
7. Esito validator
8. Metriche
9. Copertura Test_ID
10. Anomalie
11. Interpretazione tecnica
12. Risultati riutilizzabili nella tesi
13. Limiti

## metrics_Gxx.csv minimum schema

| Column | Required | Description |
| --- | --- | --- |
| campaign_id | yes | Stable campaign identifier, e.g. final-test-campaign |
| group_id | yes | G00 through G07 |
| config_id | yes when applicable | Logical configuration; blank only for pure audit rows |
| materialization_id | yes when applicable | Concrete scenario instance id |
| run_id | yes when applicable | MOSAIC execution id |
| test_id | yes when metric supports a test | Covered Test_ID or semicolon list |
| metric_name | yes | Machine-readable metric name |
| metric_value | yes | Numeric or categorical value |
| metric_unit | no | Unit such as s, ms, bps, count, percent |
| source_file | yes | Relative path to evidence file |
| validator_status | yes | PASS, FAIL, WARN, NOT_RUN, NOT_APPLICABLE |
| notes | no | Short interpretation or caveat |

## evidence_manifest_Gxx.json minimum schema

~~~json
{
  "campaign_id": "final-test-campaign",
  "group_id": "Gxx",
  "created_at_utc": "YYYY-MM-DDTHH:MM:SSZ",
  "git": {
    "branch": "testing/final-campaign",
    "frozen_branch": "MOSAIC/SUMO-integration",
    "frozen_commit": "5a9477735a3d707a5f000a64653cd2a6fc7f2007",
    "working_tree_status_short": []
  },
  "workbook": {
    "source": "Matrice_test_MA_GA_MOSAIC_SUMO_Fase0_completa.xlsx",
    "counts_verified": true
  },
  "entries": [
    {
      "evidence_id": "EV-Gxx-0001",
      "config_id": "CFG-N-I",
      "materialization_id": "MAT-CFG-N-I-104729",
      "run_id": "N-I-01",
      "test_ids": ["T-032"],
      "kind": "raw-log|summary|validator|manifest|metric|command-output",
      "path": "relative/path/from/repo",
      "sha256": "hex",
      "created_at_utc": "YYYY-MM-DDTHH:MM:SSZ",
      "description": "short evidence description"
    }
  ]
}
~~~

## anomalies_Gxx.csv minimum schema

| Column | Required | Description |
| --- | --- | --- |
| anomaly_id | yes | Stable id such as AN-G02-0001 |
| group_id | yes | G00 through G07 |
| severity | yes | INFO, LOW, MEDIUM, HIGH, BLOCKER |
| config_id | no | Configuration involved |
| materialization_id | no | Materialization involved |
| run_id | no | Run involved |
| test_id | no | Test_ID involved |
| observed | yes | Observed condition |
| expected | yes | Oracle or expected condition |
| impact | yes | Technical/scientific impact |
| status | yes | OPEN, ACCEPTED_LIMITATION, RESOLVED, NOT_REPRODUCED |
| decision_required | yes | yes/no |
| evidence_id | no | Link to evidence_manifest entry |
| notes | no | Short notes |

## Naming rules

- Keep raw MOSAIC logs separate from versioned scenario templates.
- Every copied or referenced evidence file must have a SHA-256 in the evidence manifest.
- A group audit may reference external raw logs under tmp/mosaic-25.2/logs, but reusable thesis evidence should be copied or summarized under test-results/final-campaign.
- If a Test_ID belongs to multiple groups, the primary group owns the row in metrics_Gxx.csv and references other groups through evidence_manifest_Gxx.json.
