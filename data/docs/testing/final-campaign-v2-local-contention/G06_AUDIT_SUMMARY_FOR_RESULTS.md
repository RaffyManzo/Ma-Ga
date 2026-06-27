# Audit riepilogativo G06 per il capitolo 7

## Disegno

- run canoniche: 3;
- controlli supplementari: 3;
- durata simulata complessiva: 1.200 s;
- parent G05: `74c1a7bb41a135ae9ee932dab7f270694e8feed1`;
- JAR: `3821c28a6a0898e94f4d5136b8039595d36bcbce0b6ecc3e9406910f9e083068`;
- simulazioni eseguite dalla chiusura: 0.

## Risultato causale principale

- job totali: 697;
- strategie applicate: 677;
- risultati stale: 15;
- shutdown in-flight: 5;
- stale oltre deltaTMax:
  15/15;
- applied oltre deltaTMax:
  0/677;
- fresh reoptimization richieste:
  15/15.

La serie controllata produce:

- 0 ms: 9,677419% stale;
- 100 ms: 42,857143% stale;
- 300 ms: 100% stale;
- replica 300 ms: 100% stale.

Il limite di cadenza è quindi collegato al rapporto tra runtime wall-clock
del GA e avanzamento del tempo simulato.

## Controprova sparse

La run sparse applica 616 strategie su 616 fino a 180 s. Il runtime può
mantenere la cadenza quando il task set è spesso vuoto e l'ottimizzazione è
economica. Il limite non è un arresto generale del simulatore o del TWM.

## Classificazione dei 19 test

- PASS: 12;
- PASS_CON_LIMITAZIONE:
  5;
- EVIDENZA_PARZIALE:
  1;
- NON_OSSERVATO:
  1;
- FAIL: 0.

## Otto controlli supplementari

- PASS: 5;
- PASS_CON_LIMITAZIONE:
  1;
- EVIDENZA_PARZIALE:
  1;
- NON_OSSERVATO:
  1.

## Limiti conservati

- la simulazione completata non garantisce copertura decisionale;
- nessuna strategia fresh viene applicata dopo uno stale;
- EXCEEDS_CURRENT_AND_NEXT non è osservato;
- CRITICAL_EVENT non è osservato live;
- il validator principale assume STATIC;
- `taskCompletionModel = NOT_IMPLEMENTED`.

## Decisione

**PASS_G06_COMPLETE_WITH_TEMPORAL_CADENCE_AND_OBSERVABILITY_LIMITS_READY_FOR_G02B**
