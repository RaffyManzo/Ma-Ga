# Audit di allineamento matrici post-G02B

## Sorgenti

Le matrici G07-A sono state usate come base storica. L'aggiornamento incorpora esclusivamente evidenze presenti nel bundle canonico G02B e nei relativi aggregati.

## Output

| File | SHA-256 |
| --- | --- |
| `Matrice_test_MA_GA_MOSAIC_SUMO_G02B_allineata.xlsx` | `a40dc108e8c5838479e9a8cf9fc4d3b1283c8eb0dd4a99b22b45693320e8ac64` |
| `Matrice_semplificata_G02B_allineata.xlsx` | `a30afbef861517665d26307a2f0618656e540831846ac4ec42088e17e0d71f1d` |

Entrambi i file sono collocati nella root del repository e nella cartella `test-results/final-campaign/G02B_comparative_analysis/`.

## Matrice completa

- Aggiornato `00_Guida` con lo stato post-G02B.
- Aggiunti i test `T-122`–`T-129` al catalogo.
- Aggiunte le checklist `C-077`–`C-087`.
- Aggiunti i limiti `L-040`–`L-047`.
- Aggiunte le fonti `F-030`–`F-035`.
- Creato il foglio `19_Risultati_G02B`, con riepilogo, disegno delle varianti, aggregati per configurazione e 45 righe per-run.

## Matrice semplificata

- Aggiornati `00_Dashboard` e `03_Storico`.
- Creato `11_Risultati_G02B`.
- Conservata la leggenda e mantenuto lo storico delle fasi precedenti.

## Verifiche

- Struttura dei fogli: `PASS`.
- Errori formula rilevati: 0.
- Controllo visivo: `PASS`.
- Righe storiche eliminate: 0.
- Coerenza con 45 run e 162 righe aggregate: `PASS`.

## Decisione

`PASS_MATRIX_ALIGNMENT`. Le due matrici diventano il riferimento corrente per il G07 finale; i file G07-A restano nello storico e non vengono sovrascritti.
