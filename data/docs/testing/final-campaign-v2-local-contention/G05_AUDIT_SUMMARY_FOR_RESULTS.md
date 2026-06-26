# Audit riepilogativo G05 per il capitolo dei risultati

## Identificazione

- gruppo: G05 - politiche di risorsa e repair;
- parent: `c6141e3ae7550f7ab71ab1a9fc9cea903727b7f8`;
- run: 6 da 180 s;
- validator PASS: 6/6;
- JAR SHA-256: `3821c28a6a0898e94f4d5136b8039595d36bcbce0b6ecc3e9406910f9e083068`;
- simulazioni eseguite dalla chiusura: 0.

## Validità tecnica

- task generati:
  37596;
- job GA submitted/applied:
  175 /
  160;
- risultati stale:
  9;
- stale ratio massimo:
  9.677419%;
- runtime GA massimo:
  9.910865300 s;
- overflow applicati CPU/link/pool:
  `{"cpu": 0, "link": 0, "pool": 0}`;
- snapshot lag massimo: 0 s;
- violazioni runtime: 0.

## Risorse target

- LOCAL_CPU: esercitata in 4 geni applicati;
- EDGE_CPU: non esercitata;
- CLOUD_CPU: osservata solo in un risultato stale;
- CELL_BANDWIDTH: osservata solo in risultati stale;
- V2V_BANDWIDTH: osservata solo in un risultato stale;
- CELL_RTT: osservata solo in risultati stale.

L'assenza di overflow nelle strategie applicate dimostra il rispetto
finale delle capacità. Non dimostra, da sola, l'attivazione causale
dell'operatore di repair.

## Offloading

Bucket complessivi:

`{"P0": 101, "MID_LOW": 9, "MID_HIGH": 6, "LOW": 2, "HIGH": 13, "P1": 17}`

Bucket applicati:

`{"P0": 34, "P1": 3}`

Tutti i bucket sono osservati, ma nelle strategie applicate compaiono
soltanto p=0 e p=1. L'offloading parziale resta evidenza parziale.

## Penalità, riuso e finestra

- ResourcePenalty non nulla in strategie applicate:
  osservata in quattro run;
- correzioni della policy di riuso:
  5;
- mismatch suggested/applied:
  5;
- azioni finestra:
  `{"FIRST_RUN": 6, "CLAMP_TO_BOUNDS": 163}`.

## Classificazione dei 12 test primari

- PASS:
  4;
- PASS_CON_LIMITAZIONE:
  1;
- EVIDENZA_PARZIALE:
  7;
- NON_OSSERVATO:
  0;
- FAIL:
  0.

Il dettaglio è in `G05_functional_test_results.csv`.

## Limiti

- il cadence limit riduce la parte di timeline coperta da decisioni;
- repair e fallback non hanno contatori causali diretti;
- le risorse remote degradate sono prevalentemente stale o evitate;
- le categorie complete di feasibility non sono serializzate;
- le modalità interne del ratio non sono serializzate;
- T-114 attende evidenza integrativa da G06;
- `taskCompletionModel = NOT_IMPLEMENTED`.

## Decisione

**PASS_G05_COMPLETE_WITH_REPAIR_OBSERVABILITY_LIMITS_READY_FOR_G06**
