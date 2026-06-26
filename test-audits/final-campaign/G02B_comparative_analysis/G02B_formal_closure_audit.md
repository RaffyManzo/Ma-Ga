# Audit di chiusura formale G02B

## Decisione

**Stato finale: `PASS_CANONICAL_COMPLETE`.**

La fase G02B è chiusa. Non sono richieste ulteriori esecuzioni delle varianti di ablation prima del G07 finale.

## Stato Git e runtime

- Branch: `experiment/g02b-ablation`.
- HEAD locale/remoto: `bfbfee9eb1877ee9012b991fc3ffe88735dcf0f5`.
- Base `testing/final-campaign`: `790c7cf29d72e6e9b9b2717a48f4f4e44a28ad57`.
- Commit di implementazione delle varianti: `39751f5f0532f7a077a03df92627616683cb887d`.
- Runtime JAR: 497151 byte.
- SHA-256 runtime JAR: `eadbe15a66045a2e48a71d8a827a65f849c16f9712b4fc7a206e1de12973b398`.
- Working tree alla chiusura: pulito.

## Copertura verificata

| Oggetto | Esito |
| --- | ---: |
| Self-test tooling | 43/43 |
| Smoke preparati e validati | 4/4 |
| Scenari scientifici preparati e validati | 45/45 |
| Run scientifiche validate | 45/45 |
| Righe paired per-run | 45 |
| Righe aggregate | 162 |
| Baseline FULL_MA_GA riusate | 15 |

Le varianti validate sono `LOCAL_ONLY`, `NO_MOBILITY_PENALTY` e `COLD_START_NO_REUSE`. `FULL_MA_GA` è stata eseguita soltanto nello smoke tecnico e, per la campagna scientifica, è stata riusata dalle evidenze G02.

## Integrità canonica

- Bundle: `G02B_CANONICAL_COMPLETE_20260626-095238.zip`.
- SHA-256: `0ea3b977193d3aa1a02d524bdf33d6e35f26954810044104395b5359b8a4d184`.
- Entry ZIP: 256.
- Entry nel manifest interno: 255.
- Hash e dimensioni interne: verificati.
- `g02b_manifest.json`: 49.
- `g02b_pre_run_validation.json`: 49.
- `g02b_run_context.json`: 49.
- `g02b_variant_validation.json`: 49.
- `g02b_causal_job_records.csv`: 49.

## Risultati da riportare

- `LOCAL_ONLY` forza tutte le destinazioni a `LOCAL`; la riduzione di runtime e stale ratio riflette anche la semplificazione del problema.
- `NO_MOBILITY_PENALTY` usa `wM=0` e pesi rinormalizzati. La fitness totale non è direttamente confrontabile con la baseline.
- `COLD_START_NO_REUSE` elimina warm start e partial restart. In `CFG-H-I` il delta medio del runtime GA è `+0.0476426352 s` e il delta dello stale ratio è `+11.8100444` punti percentuali.
- Snapshot lag massimo e runtime violations restano pari a zero nelle run validate.

## Limiti

I risultati sono descrittivi, con cinque seed per configurazione. Il modello di completamento dei task resta `NOT_IMPLEMENTED`. Alcune metriche temporali della baseline G02 non sono disponibili e rimangono vuote. Il warning SUMO sulla fase semaforica non sicura è comune alle configurazioni e non ha impedito il completamento.

## Conclusione

Le evidenze sono sufficienti per aggiornare le matrici, chiudere G02B e passare al G07 finale cross-phase e alla stesura consolidata del capitolo sperimentale.
