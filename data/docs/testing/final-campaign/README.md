# Final Test Campaign Planning

- Branch di partenza verificato: MOSAIC/SUMO-integration
- Commit congelato verificato: 5a9477735a3d707a5f000a64653cd2a6fc7f2007
- Branch di lavoro preparato: testing/final-campaign
- Workbook analizzato: C:/Users/raffa/Downloads/Matrice_test_MA_GA_MOSAIC_SUMO_Fase0_completa.xlsx
- Stato iniziale prima dell'esecuzione di G00: pianificazione/audit soltanto; nessuno scenario generato, nessuna simulazione eseguita, nessun codice modificato.

## Documenti prodotti

- [01_scenario_compatibility_audit.md](01_scenario_compatibility_audit.md): audit del tooling, campi reali, parametri e classificazione Config_ID.
- [02_test_group_plan.md](02_test_group_plan.md): gruppi G00-G07, mappatura Config_ID/Run_ID/Test_ID e struttura proposta.
- [scenario_configuration_mapping.csv](scenario_configuration_mapping.csv): una riga per ogni Config_ID.
- [scenario_instance_plan.csv](scenario_instance_plan.csv): una riga per ogni Materialization_ID pianificata.
- [test_id_group_mapping.csv](test_id_group_mapping.csv): mappatura completa degli 87 Test_ID.
- [audit_bundle_schema.md](audit_bundle_schema.md): formato obbligatorio degli audit di gruppo.

## Conteggi workbook

| Oggetto | Atteso | Reale | Esito |
| --- | --- | --- | --- |
| Configurazioni totali | 30 | 30 | OK |
| Configurazioni materializzabili | 28 | 28 | OK |
| Operazioni/run pianificati | 73 | 73 | OK |
| Operazioni obbligatorie | 67 | 67 | OK |
| Test funzionali censiti | 87 | 87 | OK |

## Sintesi classificazioni - Stato iniziale prima dell'esecuzione di G00

| Classificazione | Conteggio |
| --- | --- |
| NEEDS_TEST_TOOLING_EXTENSION | 1 |
| NON_MATERIALIZABLE | 2 |
| READY_CONFIG_ONLY | 14 |
| READY_EXISTING_TOOLING | 7 |
| REQUIRES_DECISION | 6 |

## Stato operativo iniziale prima dell'esecuzione di G00

Questa sottofase non materializza scenari e non lancia MOSAIC/SUMO. Le prossime modifiche operative devono restare sul branch testing/final-campaign e dovranno essere limitate a scenari sperimentali, orchestrazione, audit, manifest e documentazione dei test.

## G00 Scenario Preparation And Generation

Stato iniziale prima dell'esecuzione di G00: documentazione e piano di campagna predisposti, con tooling finale ancora da creare e scenari non ancora materializzati.

Stato corrente post-G00/G00F: `COMPLETED`.

- planned materializations: `69`
- completed materializations: `69`
- validated materializations: `63`
- warning materializations: `6`
- failed materializations: `0`
- blocked materializations: `0`
- NON_MATERIALIZABLE: `2`
- READY_CONFIG_ONLY: `20`
- READY_EXISTING_TOOLING: `8`
- REQUIRES_DECISION: `0`
- NEEDS_TEST_TOOLING_EXTENSION: `0`

Tooling created under `tools/intas-literature-scenario/final_campaign/`. Concrete scenarios remain under `tmp/materialized-literature-scenarios/final-test-campaign/`; MOSAIC was not executed. G00 is technically complete and the audit bundle was normalized in G00F.
