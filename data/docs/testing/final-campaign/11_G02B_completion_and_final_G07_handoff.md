# 11 — Chiusura G02B e handoff al G07 finale

## Stato raggiunto

G02B è chiusa con stato `PASS_CANONICAL_COMPLETE`. Il branch sperimentale resta `experiment/g02b-ablation` al commit `bfbfee9eb1877ee9012b991fc3ffe88735dcf0f5`; il branch stabile `testing/final-campaign` resta al commit `790c7cf29d72e6e9b9b2717a48f4f4e44a28ad57`.

La campagna comprende 4 smoke, 45 run scientifiche e 15 baseline G02 riusate. Tutte le run previste risultano validate. L'aggregazione finale contiene 45 righe paired e 162 righe descrittive.

## Evidenza canonica

- Bundle: `G02B_CANONICAL_COMPLETE_20260626-095238.zip`.
- SHA-256: `0ea3b977193d3aa1a02d524bdf33d6e35f26954810044104395b5359b8a4d184`.
- Manifest interno: 255 entry validate.
- Audit versionato: `test-results/final-campaign/G02B_comparative_analysis/G02B_CANONICAL_COMPLETE_20260626-095238.audit.json`.

Il bundle binario completo resta nell'archivio di handoff e non viene duplicato nel repository. Gli aggregati, l'audit e le matrici sono invece versionati.

## Matrici correnti

I nuovi riferimenti sono:

- `Matrice_test_MA_GA_MOSAIC_SUMO_G02B_allineata.xlsx`.
- `Matrice_semplificata_G02B_allineata.xlsx`.

I file G07-A restano come checkpoint storico. Non devono essere eliminati o sovrascritti.

## Risultati da portare nel capitolo sperimentale

### LOCAL_ONLY

La variante assegna tutti i task localmente. La riduzione del runtime GA e dello stale ratio è attesa perché il dominio decisionale è molto più semplice. Va usata come baseline vincolata, non come dimostrazione di superiorità.

### NO_MOBILITY_PENALTY

La penalità di mobilità è disabilitata (`wM=0`) e i pesi rimanenti sono rinormalizzati. La fitness totale cambia significato e non deve essere confrontata direttamente con `FULL_MA_GA`. Le metriche operative possono essere discusse in modo descrittivo.

### COLD_START_NO_REUSE

Warm start e partial restart sono disabilitati. L'effetto è dipendente dalla configurazione. Il segnale più netto emerge in `CFG-H-I`: runtime medio GA `+0.0476426352 s`, stale ratio `+11.8100444` punti percentuali e minore numero di strategie applicate rispetto alla baseline.

## Limiti obbligatori da dichiarare

- Cinque seed per configurazione e nessun test inferenziale.
- `taskCompletionModel=NOT_IMPLEMENTED`.
- Metriche baseline mancanti per numero di applicazioni e tempo dell'ultima strategia.
- Fitness totale non confrontabile per `NO_MOBILITY_PENALTY`.
- `LOCAL_ONLY` semplifica il problema.
- Warning SUMO semaforico comune allo scenario.

## Prossima fase: G07 finale

Il G07 finale deve:

1. consolidare G00–G06, G04-R e G02B;
2. verificare la coerenza tra matrici, audit e cartelle `test-results`;
3. produrre la decisione finale sui test T-130–T-135;
4. separare chiaramente risultati osservati, limiti e sviluppi futuri;
5. generare il materiale definitivo per il capitolo 7 senza nuove run, salvo anomalie documentali concrete.

Non sono previste ulteriori simulazioni G02B.
