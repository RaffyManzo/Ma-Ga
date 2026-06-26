# Validazione mirata della contesa CPU locale e della telemetria

## Stato

**PASS_READY_FOR_COMMIT_AND_FREEZE**

- JAR SHA-256: `3821c28a6a0898e94f4d5136b8039595d36bcbce0b6ecc3e9406910f9e083068`
- JAR size: `517007` byte
- Run validate: `4/4`
- Parametri calibrati modificati: **no**
- Campagna sperimentale V2 preparata: **no**

## Risultati mirati

| Run | Configurazione | Variante | LOCAL | Remoto | LOCAL % | Finestre con contesa | Delay positivi |
|---|---|---|---:|---:|---:|---:|---:|
| FZ-01-CANONICAL-SMOKE-FULL | CFG-SMOKE | FULL_MA_GA | 48 | 36 | 57.142857 | 0 | 0 |
| FZ-02-CANONICAL-N-I | CFG-N-I | FULL_MA_GA | 29 | 2 | 93.548387 | 5 | 21 |
| FZ-03-LOCALCPU-SENSITIVITY | CFG-R-LOCALCPU | FULL_MA_GA | 4 | 0 | 100.000000 | 1 | 13 |
| FZ-04-G02B-LOCAL-ONLY | CFG-SMOKE | LOCAL_ONLY | 185 | 0 | 100.000000 | 0 | 0 |

## Gate superati

- build e 261 classi runtime;
- 44 assertion della contesa locale;
- regressione G02B;
- harness della telemetria;
- regressioni live-state e reporting;
- `LOCAL_ONLY` senza assegnazioni remote;
- assegnazioni remote osservate nelle configurazioni non forzate;
- telemetria per gene e per finestra presente e numericamente valida;
- completion time locale conteso mai inferiore al tempo isolato;
- contesa osservata nei profili mirati.

Queste run servono soltanto alla validazione del core. Non sostituiscono la futura campagna sperimentale completa.
