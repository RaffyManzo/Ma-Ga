# G06 matrix alignment audit

## Stato finale

- Gruppo: G06 - runtime e policy temporali.
- Stato: `PASS_WITH_CONTROLLED_STALE_AND_DOCUMENTED_LIMITS`.
- Branch: `testing/final-campaign`.
- HEAD sorgente pre-chiusura: `c5a2e829ecdfee5674f7e112ba52fbdd2c3e9667`.
- Core congelato: `5a9477735a3d707a5f000a64653cd2a6fc7f2007`.
- Diff Java dal freeze: vuoto.
- Nuove simulazioni G06: una (`G-STALE-01`).
- Run opzionali G-SPARSE e G-ADAPTIVE: non eseguite, perché non necessarie per la copertura canonica obbligatoria.

## Evidenza G-STALE-01

La configurazione `CFG-G-STALE` introduce 300 ms di ritardo artificiale nel GA. La run ha prodotto:

- 33 job submitted;
- 33 job completed;
- 33 risultati `STALE_DISCARDED`;
- 0 job applicati;
- stale ratio 100%;
- snapshot lag massimo 0;
- violazioni runtime 0;
- `deltaTMaxMismatchViolations` uguale a 0.

Il validator canonico ha restituito `LITERATURE_SMOKE_TEST_FAILED` esclusivamente per `strategyApplications <= 0`. Questo rigetto è stato classificato come atteso nel profilo forced-stale. Il validator canonico non è stato modificato.

## Limite causale

Poiché non è stata applicata alcuna strategia prima degli scarti stale, la run non può dimostrare il mantenimento dell'ultima strategia valida. Inoltre il token `EXCEEDS_CURRENT_AND_NEXT` non è stato osservato nelle 66 righe overrun. T-116 è quindi classificato come `PASS_WITH_FORCED_STALE_AND_NO_PRIOR_STRATEGY_LIMIT`.

## Audit cross-run

L'audit G06-C ha analizzato 72 file temporali e 36.486 record:

- trigger: FIRST_RUN 104; SCHEDULED_WINDOW_EXPIRATION 36.382;
- livelli: UNKNOWN 104; STABLE 33.736; MODERATE 2.493; HIGH 153;
- riuso applicato: FIRST_RUN 104; WARM_START 33.746; PARTIAL_RESTART 2.471; COLD_START 165;
- azioni finestra: FIRST_RUN 104; KEEP 19; CLAMP_TO_BOUNDS 36.363;
- correzioni riuso osservate: 22.

## Verdetti T-110 - T-121

| Test | Verdetto |
|---|---|
| T-110 | PASS |
| T-111 | PASS |
| T-112 | PASS |
| T-113 | PASS_CORRECTION_OBSERVED |
| T-114 | PASS |
| T-115 | PASS |
| T-116 | PASS_WITH_FORCED_STALE_AND_NO_PRIOR_STRATEGY_LIMIT |
| T-117 | PASS |
| T-118 | NOT_APPLICABLE_OR_NOT_EXPOSED |
| T-119 | PASS |
| T-120 | PASS |
| T-121 | DEFERRED_OPTIONAL_NOT_REQUIRED_FOR_CANONICAL_G06 |

## Matrice completa

- 17 fogli.
- Nuovo foglio: `16_Risultati_G06`.
- Aggiornati: guida, configurazioni, piano run, catalogo test, dizionario output, oracoli, checklist, limiti, fonti e fogli storici.
- Rimossi gli stati G06 `DA_ESEGUIRE` dalle tre configurazioni e dalle tre run.
- G02B riallineata a `PLANNED_AFTER_RESIDUAL_AUDIT`.

## Matrice semplificata

- 9 fogli.
- Nuovo foglio: `08_Risultati_G06`.
- Dashboard, riepilogo configurazioni, storico e legenda aggiornati.
- Sequenza successiva: audit evidenze residue, eventuali G04-R, G02B, G07.

## Controlli

- Nessun errore formula rilevato.
- Nessun riferimento residuo `DEFERRED_AFTER_G06`.
- Nessun riferimento operativo a G06 come gruppo ancora da avviare.
- Anteprime visive verificate.
