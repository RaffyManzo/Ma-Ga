# Chiusura G04 e passaggio a G05

La chiusura formale di G04 è predisposta con stato `PASS_WITH_OBSERVABILITY_LIMITS`; commit e push vengono eseguiti dallo script di chiusura.

## Stato campagna

- G00: chiusa;
- G01: chiusa;
- G02: chiusa, 45/45 PASS;
- G03: chiusa, `PASS_WITH_OPTIONAL_STRESS_LIMIT`;
- G04: 5/5 run tecnicamente PASS, audit T-090–T-099 completato; chiusura Git da registrare;
- G05: prossimo gruppo, non ancora avviato;
- G06: non avviato;
- G02B: `DEFERRED_AFTER_G06`;
- G07: non avviato.

Sequenza: `G05 → G06 → G02B → G07`.

## Risultati G04 da preservare

- T-090 e T-099 PASS;
- T-092, T-093, T-095 e T-097 PASS_CON_LIMITAZIONE;
- T-091 e T-096 EVIDENZA_PARZIALE;
- T-094 e T-098 NON_OSSERVATO;
- nessun FAIL;
- nessuna nuova simulazione necessaria per la chiusura;
- `M-V2V-01` con stale ratio 48,186528%, senza lag o violazioni runtime;
- `taskCompletionModel=NOT_IMPLEMENTED`;
- metriche SUMO non esposte restano `null`.

## Prossimo passo

Preparare il preflight G05 usando le configurazioni pianificate:

- R-LOCALCPU-01;
- R-EDGECPU-01;
- R-CLOUDCPU-01;
- R-CELLBW-01;
- R-V2VBW-01;
- R-RTT-01.

Il core Java resta congelato. Nessuna modifica Java può essere introdotta senza discussione preventiva.
