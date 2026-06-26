# Stato congelato della contesa CPU locale

## Riferimenti

- branch: `fix/local-cpu-contention`
- commit implementativo: `512944235ccbe25ca585714f133f01ea38859091`
- tag finale previsto: `maga-local-contention-freeze-20260626`
- base precedente: `804b2e60a6df41655bb99345388328e05f6e88fa`
- freeze storico precedente: `5a9477735a3d707a5f000a64653cd2a6fc7f2007`
- JAR SHA-256: `3821c28a6a0898e94f4d5136b8039595d36bcbce0b6ecc3e9406910f9e083068`
- JAR size: `517007` byte
- classi runtime: `261`

## Validazione

- build pulita: PASS;
- harness contesa locale: PASS, 44 assertion;
- regressione G02B: PASS;
- harness telemetria: PASS;
- regressione live-state: PASS;
- regressione reporting: PASS;
- run live mirate: 4/4 PASS;
- `LOCAL_ONLY`: nessuna assegnazione remota;
- scelte remote osservate nelle configurazioni non forzate;
- telemetria della contesa osservata e numericamente valida.

## Perimetro

Il freeze include core, repair, reporting e test della contesa CPU locale.
Non include la preparazione o l'esecuzione della nuova campagna sperimentale.
Il futuro branch di campagna dovrà essere creato a partire dal tag indicato.
