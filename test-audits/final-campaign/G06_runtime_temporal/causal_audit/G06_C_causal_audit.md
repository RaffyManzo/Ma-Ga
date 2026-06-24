# G06-C - Audit causale temporale

Stato: **G06_CAUSAL_AUDIT_COMPLETE**

## Risultato della run G-STALE-01

- Verdetto causale: **PASS_WITH_FORCED_STALE_AND_NO_PRIOR_STRATEGY_LIMIT**
- Job submitted/completed/stale: `33 / 33 / 33`
- Job applicati: `0`
- Strategy applications: `0`
- Stale ratio: `100`
- Record temporali APPLIED/STALE: `0 / 33`
- Trigger FIRST_RUN: `33`
- Riuso FIRST_RUN: `33`
- Azioni finestra FIRST_RUN: `33`
- EXCEEDS_CURRENT_AND_NEXT osservati: `0`

## Interpretazione

Il ritardo artificiale ha reso obsolete tutte le 33 risposte del GA. Nessuna strategia e stata applicata e gli oracoli HARD restano validi. Il validator canonico fallisce esclusivamente per strategyApplications <= 0, condizione attesa nella run forced-stale.

Poiche non esiste una strategia valida precedente, questa run non puo dimostrare il mantenimento dell ultima strategia applicata. Inoltre il TemporalWindowManager continua a descrivere ogni ciclo come FIRST_RUN: la run dimostra lo scarto stale, ma non esercita la progressione scheduled, il riuso non-first e l adattamento ordinario della finestra.

## Copertura cross-run

- File temporali analizzati: `72`
- Record totali: `36486`
- Trigger: `FIRST_RUN=104; SCHEDULED_WINDOW_EXPIRATION=36382`
- Livelli: `HIGH=153; MODERATE=2493; STABLE=33736; UNKNOWN=104`
- Riuso applicato: `COLD_START=165; FIRST_RUN=104; PARTIAL_RESTART=2471; WARM_START=33746`
- Azioni finestra: `CLAMP_TO_BOUNDS=36363; FIRST_RUN=104; KEEP=19`
- Correzioni riuso: `22`

## Decisione sulle run opzionali

- G-SPARSE-01: **SKIP_NOT_REQUIRED_FOR_MANDATORY_G06**
- G-ADAPTIVE-01: **SKIP_OPTIONAL_NON_CANONICAL_T121**
- Prossima azione: **G06_D_PREPARE_FORMAL_CLOSURE**

Le matrici non vengono aggiornate in questa sottofase.
