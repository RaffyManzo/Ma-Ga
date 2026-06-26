# G02B comparative analysis — risultati definitivi

## Stato

`PASS_CANONICAL_COMPLETE`

La cartella raccoglie gli artefatti versionabili della fase G02B. Le evidenze raw delle run restano nel bundle canonico locale; nel repository vengono conservati gli aggregati, l'audit conclusivo e le matrici allineate.

## Copertura

- 4 smoke, uno per variante, tutti validati.
- 45 run scientifiche: 3 configurazioni × 5 seed × 3 varianti.
- 15 baseline `FULL_MA_GA` G02 riusate senza nuova esecuzione.
- 45 righe paired e 162 righe aggregate.
- 43 self-test, zero fallimenti.

## File principali

- `g02b_paired_per_run.csv`: confronto paired per singola run.
- `g02b_paired_aggregate.csv`: aggregazione per variante, configurazione e metrica.
- `g02b_paired_summary.json` e `.md`: riepilogo canonico.
- `G02B_phase_run_status.csv`: vista compatta delle 45 run scientifiche.
- `G02B_CANONICAL_COMPLETE_20260626-095238.audit.json`: audit del bundle canonico.
- `G02B_matrix_alignment_decision.json`: tracciamento delle modifiche alle matrici.
- `Matrice_*_G02B_allineata.xlsx`: matrici aggiornate dopo la chiusura G02B.

## Regole di interpretazione

`LOCAL_ONLY` è una baseline vincolata e semplifica il problema; tempi inferiori non provano una superiorità generale. `NO_MOBILITY_PENALTY` modifica la funzione obiettivo, quindi la fitness totale non è confrontabile con `FULL_MA_GA`. `COLD_START_NO_REUSE` isola l'effetto del riuso e mostra il limite più netto in `CFG-H-I`, dove aumentano runtime medio e stale ratio.

## Integrità

Bundle canonico: `0ea3b977193d3aa1a02d524bdf33d6e35f26954810044104395b5359b8a4d184`. Il bundle completo non è duplicato in questa cartella per evitare una seconda copia binaria; l'audit versionato ne registra percorso logico, hash, conteggi e metadati Git.
