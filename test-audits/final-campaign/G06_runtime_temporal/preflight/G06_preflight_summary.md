# G06-A - Preflight e inventario delle evidenze temporali

Stato: **G06_PREFLIGHT_COMPLETE_MANDATORY_STALE_READY**

## Obiettivo

Questa sottofase verifica se le configurazioni e le evidenze richieste da G06 sono realmente esposte dal codice, dagli script di materializzazione e dal reporting. Non avvia simulazioni e non modifica matrici o Java.

## Piano G06 estratto dalla matrice

| Run | Configurazione | Obbligatoria | Scopo |
|---|---|---|---|
| G-SPARSE-01 | CFG-G-SPARSE | No | Finestre sparse e possibili task set vuoti |
| G-ADAPTIVE-01 | CFG-G-ADAPTIVE | No | Scaling ADAPTIVE separato |
| G-STALE-01 | CFG-G-STALE | Si | Stale controllato con ritardo artificiale |

## Readiness configurazioni

| Configurazione | Esposizione | Materializzazione presente | Readiness |
|---|---|---:|---|
| CFG-G-SPARSE | EXACT_VARIANT_EXPOSURE_PROVEN | True | OPTIONAL_READY_AFTER_MANDATORY_STALE |
| CFG-G-ADAPTIVE | EXPOSURE_NOT_PROVEN | True | OPTIONAL_MANUAL_REVIEW |
| CFG-G-STALE | RUNTIME_AND_MATERIALIZER_EXPOSURE_PROVEN | True | READY_FOR_EXECUTION_PREPARATION |

## Regola di esecuzione

G-STALE-01 e la run obbligatoria. G-SPARSE-01 e G-ADAPTIVE-01 restano opzionali e non vengono eseguite prima dell audit causale della run stale.

## Test temporali

T-110--T-117 vengono valutati usando campi strutturati del reporting e, quando necessario, la nuova run G-STALE-01. T-118 non viene forzato nella campagna live congelata. T-119 e T-120 sono gia PASS. T-121 appartiene al test opzionale ADAPTIVE.

## Decisione

- Prossima azione: `PREPARE_AND_EXECUTE_G_STALE_01`
- Run opzionali: `DEFER_G_SPARSE_AND_G_ADAPTIVE_UNTIL_AFTER_G_STALE_AUDIT`
- Simulazioni avviate in G06-A: `false`
- Matrici aggiornate: `false`
- Modifiche Java: `false`
