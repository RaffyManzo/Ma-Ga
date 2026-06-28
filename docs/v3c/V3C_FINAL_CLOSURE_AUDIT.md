# Chiusura formale V3-C

**Stato:** `PASS_IMPLEMENTATION_VALIDATED_WITH_PACING_LIMIT`

- Branch: `experiment/v3c-freshness-aware-window`
- HEAD: `3053294386f99e1955e9308e232b5c8230f33ed7`
- JAR SHA-256: `8645EE981E5D4696BFB6EEE8A2F2AC9F4F16B73E72C67AFFFD26B9BDB844EC36`
- Pilot MOSAIC post-R3: no
- Commit o push prodotti da questo audit: no

## Funzionalità validate

Sono stati validati tramite build e harness:

- runtime estimator sui soli job validi con task;
- history 20 e P95;
- separazione tra DeltaTMin, DeltaTMax, budget wall-clock e freshness cap;
- stale wall-clock, simulation-age e combinati;
- cooperative best-so-far;
- reporting integrale delle strategie stale;
- conservazione dell'ultima strategia valida;
- assenza di fallback LOCAL e warm start da risultati stale;
- freshness cap indipendente da DeltaTMax.

Fitness, repair, cromosoma e operatori genetici non sono stati modificati.

## Evidenze

| ID | SHA-256 | Stato |
|---|---|---|
| R2 | `1A96748B3A61695CDA232E7CB52EE4D56AA96B9048E3345D0DDAE89707D97FC1` | verificata |
| R3 | `81A98C62D1C3524F8988154C6DC673AA95706E0194381B983FF27FD9A66B071C` | verificata |
| VALIDATOR | `89CC4060F17C9672D3847992A63730EDD443ECA657BD5E5FA0F524C316F302E5` | verificata |
| PILOT | `9F3D299578CC594B7898A6222C88957EEEF1FFFF19A167A4355B42C92648AF17` | verificata |

## Pilot precedente alla R3

Il singolo pilot senza pacing ha completato 300 secondi simulati in circa
38,398 secondi reali:

- 47 job completati;
- 24 applicati;
- 23 stale;
- 20 stale per età simulata;
- 3 stale per entrambe le cause;
- 18 arresti `TIME_BUDGET_BEST_SO_FAR`.

Il pilot non costituisce una valutazione prestazionale definitiva della R3.

## Limite sperimentale

Senza pacing, il tempo simulato avanza più rapidamente del calcolo
wall-clock del GA. Ulteriori run sarebbero dominati dallo stesso squilibrio
temporale e non produrrebbero evidenza indipendente.

La V3-C è quindi chiusa come implementazione validata deterministicamente.
La valutazione end-to-end dovrà proseguire in un branch separato dedicato
al pacing, derivato dal commit `3053294386f99e1955e9308e232b5c8230f33ed7`.
