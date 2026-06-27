# Audit preliminare G02B

## Disegno

- baseline FULL_MA_GA riusate da G02: 15;
- nuove run LOCAL_ONLY: 15;
- nuove run NO_MOBILITY_PENALTY: 15;
- nuove run COLD_START_NO_REUSE: 15;
- confronti totali: 60;
- configurazioni: CFG-N-I, CFG-N-S, CFG-H-I;
- seed: 104729, 130363, 155921, 181081, 207547;
- durata: 300 s per run.

## Gate già verificati

- LOCAL_ONLY: zero assegnazioni remote in 15/15;
- contesa locale osservata in ogni configurazione LOCAL_ONLY;
- NO_MOBILITY_PENALTY: peso effettivo wM=0 in 15/15;
- COLD_START_NO_REUSE: nessun WARM_START o PARTIAL_RESTART;
- task generati uguali alla baseline associata in 45/45;
- snapshot lag e violazioni runtime pari a zero;
- JAR congelato invariato;
- nessuna modifica Java/core;
- baseline FULL_MA_GA non rilanciate.

## Nota metodologica

La variante NO_MOBILITY_PENALTY usa l'implementazione congelata:
la componente mobility viene posta a zero e i pesi rimanenti vengono
normalizzati. Questo effetto deve essere dichiarato nell'interpretazione.

## Verdetto tecnico

`PASS_G02B_RUNS_READY_FOR_FORMAL_ABLATION_AUDIT`

La classificazione scientifica e il commit G02B restano successivi
all'audit del bundle.
