# Audit riepilogativo G01 per il capitolo dei risultati

## Identificazione

- campagna: MA-GA V2 con contesa CPU locale;
- gruppo: G01 - validazione end-to-end della pipeline;
- configurazione: CFG-SMOKE;
- seed: 104729;
- durata: 180 s;
- densita': nominal;
- workload: WL-SMOKE;
- variante: FULL_MA_GA;
- modalita' di scaling GA: STATIC;
- run MOSAIC: log-20260626-215940-MaGaLiteratureBasedUrbanStudy;
- JAR congelato SHA-256: 3821c28a6a0898e94f4d5136b8039595d36bcbce0b6ecc3e9406910f9e083068;

## Obiettivo

Verificare con una singola run smoke l'intera catena scenario materializzato,
deploy, SUMO, MOSAIC, live-state layer, snapshot, TemporalWindowManager,
MA-GA, reporting e validator. G01 e' un test tecnico end-to-end e non
sostituisce gli esperimenti fattoriali G02.

## Esito tecnico

- validator: LITERATURE_SMOKE_TEST_PASSED;
- simulazione completata: True;
- task generati: 101;
- task attivati: 101;
- task rimossi alla deadline: 101;
- task pendenti finali: 0;
- picco task pendenti: 5;
- snapshot risolti/richiesti: 357 / 360;
- job GA completati/inviati: 212 / 212;
- strategie applicate: 199;
- risultati nulli: 0;
- job falliti: 0;
- lag massimo snapshot: 0 s;
- finestre con lag non nullo: 0;
- violazioni GA parallelo: 0;
- violazioni future snapshot/pool: 0 / 0;
- violazioni banda pool: 0;
- violazioni deltaTMax: 0;

## Runtime e stale result

- runtime GA medio: 0.043524142 s;
- mediana: 0.0051883 s;
- P95: 0.215584 s;
- massimo: 0.563803 s;
- job stale: 13;
- stale ratio: 6.132075%;
- sequenze stale: 12;
- sequenza stale massima: 2;

## Assegnazioni

- LOCAL: 37;
- VEHICLE: 14;
- EDGE: 0;
- CLOUD: 3;
- totale: 54;
- quota LOCAL: 68.518519%;
- quota remota: 31.481481%;

Questi valori dimostrano che il reporting delle decisioni e' operativo, ma
non devono essere usati come stima statistica delle prestazioni.

## Telemetria della contesa locale

- porzioni locali: 54;
- finestre-veicolo con contesa: 0;
- finestre-veicolo con overflow: 0;
- finestre con contesa: 0;
- finestre con overflow: 0;
- deadline locali violate: 0;
- massimo tempo locale indipendente: 3.2 s;
- massimo completion time locale conteso: 3.2 s;
- massimo ritardo da contesa: 0.0 s;
- massimo rapporto di domanda: 0.8;
- massimo rapporto di overflow: 0.0;

Il trace serializza i campi per gene
`independentLocalExecutionTimeSeconds`,
`contendedLocalCompletionTimeSeconds` e
`localContentionDelaySeconds`. Le finestre espongono
`maxLocalDemandRatio`, `maxLocalCpuOverflowRatio` e
`localDeadlineViolations`.

## Anomalie e limiti

- un tentativo infrastrutturale iniziale si e' arrestato a 0 s per il watchdog
  di avvio del federato SUMO; non ha prodotto trace runtime e non e' contato
  come run G01 valida;
- il retry e' stato eseguito una sola volta con watchdog MOSAIC pari a
  120 s;
- una sola run smoke valida: nessuna inferenza statistica;
- `localRepairApplied` non e' un campo serializzato del freeze e non viene
  richiesto come gate;
- i contatori SUMO specifici per teleport ed emergency braking non sono
  campi canonici del validator: T-016 resta evidenza parziale non bloccante;
- il modello non simula ancora il completamento reale dei task remoti.

## Evidenze

- test-results/final-campaign-v2-local-contention/G01_pipeline_validation/log-20260626-215940-MaGaLiteratureBasedUrbanStudy/;
- test-audits/final-campaign-v2-local-contention/G01_pipeline_validation/G01_metrics.json;
- test-audits/final-campaign-v2-local-contention/G01_pipeline_validation/G01_test_coverage.csv;
- test-audits/final-campaign-v2-local-contention/G01_pipeline_validation/G01_evidence_inventory.csv;
- test-audits/final-campaign-v2-local-contention/G01_pipeline_validation/G01_anomalies_and_limits.csv.

## Decisione

PASS_G01_COMPLETE_READY_FOR_G02_RUNS
