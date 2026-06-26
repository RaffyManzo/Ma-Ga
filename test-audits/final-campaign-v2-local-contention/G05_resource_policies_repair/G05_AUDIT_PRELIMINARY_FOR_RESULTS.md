# Audit preliminare G05 - risorse e repair

## Stato tecnico

- run pianificate: 6;
- run completate: 6/6;
- validator PASS: 6/6;
- task complessivi: 37596;
- job GA submitted/applied:
  175 /
  160;
- stale complessivi:
  9;
- stale ratio massimo:
  9.677419%;
- runtime GA massimo:
  9.910865300 s;
- overflow applicati CPU/link/pool:
  {'cpu': 0, 'link': 0, 'pool': 0};
- snapshot lag massimo: 0 s;
- violazioni runtime: 0.

## Risorse target

- R-LOCALCPU-01: TARGET_APPLIED; applicati=4; stale=0.
- R-EDGECPU-01: TARGET_NOT_EXERCISED; applicati=0; stale=0.
- R-CLOUDCPU-01: TARGET_STALE_ONLY; applicati=0; stale=1.
- R-CELLBW-01: TARGET_STALE_ONLY; applicati=0; stale=2.
- R-V2VBW-01: TARGET_STALE_ONLY; applicati=0; stale=1.
- R-RTT-01: TARGET_STALE_ONLY; applicati=0; stale=4.

## Offloading

Bucket osservati nelle strategie applicate:

`{"P0": 34, "P1": 3}`

Il conteggio distingue p=0, offloading parziale e p=1.

## Repair e vincoli

Le strategie applicate non superano le capacità CPU, link o pool
ricostruite dagli snapshot.

Questo dimostra il rispetto finale dei vincoli. Non dimostra
automaticamente che un operatore di repair sia stato attivato, perché
il freeze può non serializzare un contatore causale del repair o del
fallback.

Run con contatore diretto repair/fallback esposto:
0/6.

## Riuso e finestra

- correzioni della policy di riuso:
  5;
- mismatch suggested/applied:
  5;
- azioni finestra:
  `{"FIRST_RUN": 6, "CLAMP_TO_BOUNDS": 163}`.

## Confronto con G02

Le baseline matched vengono lette dalle evidenze G02 già versionate.
Poiché G02 dura 300 secondi e G05 180 secondi, il confronto finale deve
usare tassi, percentuali e rapporti, non conteggi grezzi.

## Limiti

- una run tecnica PASS non implica che la risorsa target sia stata usata;
- evitamento della risorsa degradata e repair sono fenomeni differenti;
- il cadence limit di G03 resta visibile nelle metriche;
- `taskCompletionModel = NOT_IMPLEMENTED`;
- EDGE, CLOUD o VEHICLE stale non sono strategie applicate.

## Prossimo passo

Allegare il bundle per l'audit funzionale e la chiusura formale G05.
