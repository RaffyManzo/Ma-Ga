# Campagna finale MA-GA V3-D

**Stato:** `PLANNED_NOT_EXECUTED`

- Branch: `testing/v3d-final-campaign`
- Baseline: `7a5d9aa9bf00338e060854b1b89e125bc02da029`
- Pacing: MOSAIC `--realtime-brake 1`
- JAR SHA-256: `8645EE981E5D4696BFB6EEE8A2F2AC9F4F16B73E72C67AFFFD26B9BDB844EC36`
- Run obbligatorie: `68`
- Repliche principali: `5`

## Domande di ricerca

- **RQ1:** effetto di densità e workload sul comportamento del MA-GA.
- **RQ2:** validità temporale, stale e continuità delle strategie.
- **RQ3:** reazione alle transizioni tra RSU.
- **RQ4:** effetto della contesa locale, edge e della banda cellulare.
- **RQ5 opzionale:** confronto con varianti ablate, subordinato a un audit
  separato di compatibilità con V3-D.

## Gruppi

| Gruppo | Scopo | Run |
|---|---|---:|
| G01 | Smoke paced | 1 |
| G02 | Fattoriale 3x3 con cinque seed | 45 |
| G03 | Ripetibilità runtime aggiuntiva | 2 |
| G04 | Mobilità RSU-switch | 5 |
| G05 | Stress LOCALCPU, EDGECPU e CELLBW | 15 |
| Totale | | 68 |

## Regole metodologiche

- tutte le run usano pacing `1.0`;
- il core e il JAR restano congelati;
- nessuna run viene sostituita senza audit;
- cinque seed per configurazione principale;
- analisi descrittiva, senza affermazioni di significatività inferenziale;
- `taskCompletionModel=NOT_IMPLEMENTED` resta un limite;
- le campagne V2 sono evidenze preliminari, non dati da unire alla V3-D;
- l'ablation non appartiene alla matrice obbligatoria finché non ne viene
  verificata la compatibilità.
