# Campagna MA-GA V2 con contesa CPU locale

Questa directory contiene il control plane della nuova campagna sperimentale.

## Freeze

- branch sorgente: `fix/local-cpu-contention`
- branch campagna: `testing/final-campaign-v2-local-contention`
- commit: `bf41e5682293a79939af2c53858126ad4b9f2ef0`
- tag: `maga-local-contention-freeze-20260626`

## Stato

- control plane creato;
- scenari V2 non ancora materializzati;
- simulazioni V2 non ancora eseguite;
- risultati legacy esclusi dai nuovi aggregati.

## File principali

- `scenario_instance_plan.csv`: 69 istanze V2, tutte `NOT_STARTED_V2`;
- `scenario_configuration_mapping.csv`: copia byte-identica del mapping canonico;
- `test_id_group_mapping.csv`: copia byte-identica del mapping canonico;
- `02_test_group_plan.md`: piano di controllo V2;
- `legacy_02_test_group_plan_reference.md`: riferimento storico non operativo;
- `semantic_equivalence_report.csv`: verifica riga per riga;
- `v2_path_mapping.csv`: separazione dei percorsi;
- `source_plan_hashes.csv`: hash delle fonti legacy canoniche.

Non modificare i parametri scientifici per cambiare la quota di offloading.

## G00 Scenario Preparation And Generation

Stato G00 V2: `COMPLETED`.

- planned materializations: `69`
- completed materializations: `69`
- validated materializations: `63`
- warning materializations: `6`
- failed materializations: `0`
- blocked materializations: `0`

Gli scenari sono isolati sotto `tmp/materialized-literature-scenarios/final-campaign-v2-local-contention/`. G00 non esegue MOSAIC o SUMO.
