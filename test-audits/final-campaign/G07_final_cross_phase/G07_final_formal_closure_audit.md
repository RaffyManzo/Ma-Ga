# Audit di chiusura formale G07 finale

## Decisione

**Stato: `PASS_FINAL_READY_FOR_THESIS_CONCLUSIONS`.**

Il G07 finale consolida G00–G06, G04-R e G02B. Non sono necessarie ulteriori simulazioni prima dell'integrazione del capitolo 7 e della stesura delle conclusioni, salvo anomalie documentali concrete.

## Stato Git

- Branch operativo: `experiment/g02b-ablation`.
- HEAD pre-G07: `0d12707e0ba65194055d936c532a5615bf1169e2`.
- Base stabile: `790c7cf29d72e6e9b9b2717a48f4f4e44a28ad57`.
- Modifiche Java durante G07: 0.
- Nuove run durante G07: 0.

## Scope

| Scope | File strutturati | JSON | JSONL record | Righe CSV |
| --- | ---: | ---: | ---: | ---: |
| G07-A storico | 303 | 210 | 982 | 33.283 |
| G02B canonico | 254 | 201 | 0 | 31.057 |
| Totale composito | 557 | 411 | 982 | 64.340 |

Il totale composito è uno scope autorevole costruito sommando due corpus disgiunti nel tempo. Non rappresenta ogni file locale o temporaneo della repository.

## Verdetti finali

- T-130: `PASS_FINAL_CROSS_PHASE`.
- T-131: `PASS_FINAL_AUTHORITATIVE_CORPUS`.
- T-132: `PASS_FINAL_COUNTER_AND_PAIRING_COHERENCE`.
- T-133: `PASS_FINAL_COMPLETION_MODEL_DECLARED`.
- T-134: `PASS_FINAL_PROVENANCE_LEDGER`.
- T-135: `PASS_FINAL_NUMERIC_AND_THESIS_INTEGRITY`.

## Coerenza scientifica

Le 45 run G02B sono paired con 15 baseline G02 usando la stessa configurazione e lo stesso seed. L'aggregazione contiene 45 righe per-run e 162 righe descrittive. La fitness totale di `NO_MOBILITY_PENALTY` resta esclusa dal confronto, `LOCAL_ONLY` è trattata come controllo strutturale e i risultati non sono presentati come statisticamente significativi.

## Limiti mantenuti

- `taskCompletionModel=NOT_IMPLEMENTED`;
- `high_density` classificato come stress profile;
- cinque seed per configurazione;
- nessun test inferenziale;
- metriche baseline mancanti lasciate vuote;
- runtime wall-clock dipendente dall'host.

## Conclusione

Le matrici definitive e il capitolo 7 aggiornato sono coerenti con gli audit. La fase sperimentale può essere considerata chiusa ai fini della tesi.
