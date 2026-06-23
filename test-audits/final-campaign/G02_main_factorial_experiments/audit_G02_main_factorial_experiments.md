# Audit finale G02 — esperimenti fattoriali principali

## Esito

- Run completate: **45/45 PASS**.
- Configurazioni: **9/9**, cinque seed ciascuna.
- Validator errors: **0**.
- Validator warnings: **0**.
- Snapshot lag massimo: **0 s**.
- Violazioni runtime: **0**.
- Diff Java dal commit congelato: **vuoto**.

## Metriche complessive

- Task generati: **451449**.
- GA submitted/completed/applied: **24410/24410/23313**.
- Risultati stale: **1097**, ratio pesata **4,494060%**.
- Runtime GA massimo osservato: **0,6401189 s**.

## Interpretazione

- Le configurazioni low_density e nominal restano i profili operativi di riferimento.
- high_density resta uno stress profile documentato.
- Lo stale cresce nei profili più gravosi, senza errori validator, lag causale o violazioni runtime.
- La distribuzione delle assegnazioni deve essere approfondita nella sottofase comparativa G02B.

## Limiti

- taskCompletionModel = NOT_IMPLEMENTED: non è disponibile un completion rate.
- I task rimossi alla deadline non devono essere interpretati come task completati.
- Non vengono dichiarati valori numerici per SUMO errors, teleports o emergency braking senza evidenza esplicita.

## Artefatti

- metrics_G02_runs.csv
- metrics_G02_by_configuration.csv
- evidence_manifest_G02.json
- run-summaries/ con 45 JSON compatti
- matrici completa e semplificata post-G02
