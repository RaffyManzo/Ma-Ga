# Audit riepilogativo G04 per il capitolo dei risultati

## Identificazione

- gruppo: G04 - mobilità e connettività;
- parent: `15311ae172fd6e1f67e7774b7e9a70d6ba0bbe4e`;
- run: 5 da 180 s;
- validator PASS: 5/5;
- JAR SHA-256: `3821c28a6a0898e94f4d5136b8039595d36bcbce0b6ecc3e9406910f9e083068`;
- simulazioni eseguite dalla chiusura: 0.

## Validità tecnica

- task generati complessivi:
  32036;
- job GA submitted/applied:
  148 /
  135;
- risultati stale:
  8;
- stale ratio massimo:
  9.677419%;
- runtime GA massimo:
  3.611148900 s;
- snapshot lag massimo: 0 s;
- violazioni runtime: 0.

## Decisioni applicate

- LOCAL:
  50;
- VEHICLE:
  2;
- EDGE:
  0;
- CLOUD:
  0.

EDGE e CLOUD compaiono nei candidati e nei geni calcolati, ma non in
strategie applicate. Questa distinzione è obbligatoria.

## Evidenza mobility-aware

- record temporali:
  143;
- geni:
  159;
- geni remoti:
  24;
- phiLink non nullo:
  24;
- phiHo non nullo:
  24;
- Pmob non nullo:
  24;
- errore massimo della formula Pmob:
  0.000e+00;
- placeholder cloud legacy:
  0;
- coverage insufficiente:
  0;
- handover osservati:
  2;
- handover nel profilo switch:
  1.

## Dinamicità e riuso

Livelli osservati:

`{"UNKNOWN": 5, "STABLE": 123, "MODERATE": 14, "HIGH": 1}`

Modalità di riuso osservate:

`{"FIRST_RUN": 5, "WARM_START": 123, "PARTIAL_RESTART": 11, "COLD_START": 4}`

Tutti i record contengono Dv, Dt, Dr, Dl e D con valori finiti.

## Classificazione dei 23 test primari

- PASS:
  7;
- PASS_CON_LIMITAZIONE:
  8;
- EVIDENZA_PARZIALE:
  7;
- NON_OSSERVATO:
  1;
- FAIL:
  0.

Il dettaglio è in `G04_functional_test_results.csv`.

## Risultati principali

- il caso LOCAL convenzionale è verificato;
- i candidati VEHICLE sono osservati e due decisioni VEHICLE sono applicate;
- i candidati EDGE e CLOUD gateway-aware sono costruiti;
- il placeholder cloud legacy non compare;
- la velocità relativa V2V è finita;
- phiLink, phiHo e Pmob sono calcolati;
- la formula Pmob coincide con i pesi congelati;
- un handover `rsu_1→rsu_0` è osservato nel profilo switch;
- tutti i livelli di dinamicità e tutte le modalità di riuso sono esercitati.

## Limiti

- il cadence limit di G03 riduce la copertura decisionale delle run;
- EDGE e CLOUD non sono applicati;
- T-094 coverage insufficiente resta NON_OSSERVATO;
- fallback e cause complete del prefilter non sono serializzati;
- `taskCompletionModel = NOT_IMPLEMENTED`;
- metriche SUMO non esposte non vengono dichiarate.

## Decisione

**PASS_G04_COMPLETE_WITH_OBSERVABILITY_LIMITS_READY_FOR_G05**
