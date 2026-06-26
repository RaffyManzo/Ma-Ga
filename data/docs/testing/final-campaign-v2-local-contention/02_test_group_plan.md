# Piano di controllo della campagna MA-GA V2

## Stato iniziale

- stato: `NOT_STARTED_V2`;
- branch: `testing/final-campaign-v2-local-contention`;
- freeze: `bf41e5682293a79939af2c53858126ad4b9f2ef0`;
- tag: `maga-local-contention-freeze-20260626`;
- materializzazioni V2 eseguite: 0;
- simulazioni V2 eseguite: 0;
- risultati legacy importati: 0;
- parametri scientifici modificati: 0.

Il file `legacy_02_test_group_plan_reference.md` e' conservato soltanto
come riferimento storico. I suoi stati di completamento non descrivono
la campagna V2.

## Gruppi canonici

| Gruppo | Nome | Test primari | Test obbligatori | Istanze da materializzare | Stato V2 |
|---|---|---:|---:|---:|---|
| G00 | scenario preparation and generation | 2 | 2 | 0 | `NOT_STARTED_V2` |
| G01 | pipeline validation | 5 | 5 | 1 | `NOT_STARTED_V2` |
| G02 | main factorial experiments | 5 | 5 | 45 | `NOT_STARTED_V2` |
| G03 | reproducibility and duration | 3 | 3 | 9 | `NOT_STARTED_V2` |
| G04 | mobility and connectivity | 23 | 23 | 5 | `NOT_STARTED_V2` |
| G05 | resource policies and repair | 12 | 10 | 6 | `NOT_STARTED_V2` |
| G06 | runtime and temporal policies | 19 | 15 | 3 | `NOT_STARTED_V2` |
| G07 | final cross-group audit | 18 | 18 | 0 | `NOT_STARTED_V2` |

## Sequenza operativa

G00 prepara e materializza gli scenari. Seguono G01, G02, G03,
G04/G04-R, G05, G06, G02B e infine G07.

Ogni gruppo deve essere chiuso tramite evidenze, manifest e audit prima
di passare al successivo. Le quattro run di freeze non sono risultati V2.

## Regole

- non modificare core, workload, CPU, deadline, banda, RTT, seed o pesi;
- non usare i percorsi legacy come destinazione;
- non considerare `localRepairApplied` un campo runtime obbligatorio;
- distinguere sempre dati configurati, osservati, derivati e misurati;
- non modificare il capitolo 7 prima della chiusura di G07.

## G00 Execution Result

Stato G00 V2: `COMPLETED`.

- planned materializations: `69`
- completed materializations: `69`
- validated materializations: `63`
- warning materializations: `6`
- failed materializations: `0`
- blocked materializations: `0`

Gli scenari sono isolati sotto `tmp/materialized-literature-scenarios/final-campaign-v2-local-contention/`. G00 non esegue MOSAIC o SUMO.
