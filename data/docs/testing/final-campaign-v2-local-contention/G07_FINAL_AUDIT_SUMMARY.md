# Audit finale G07

## Esito

`PASS_G07_FINAL_AUDIT_COMPLETE_WITH_DOCUMENTED_OBSERVABILITY_AND_COMPLETION_LIMITS_READY_FOR_RESULTS_ANALYSIS_AND_CHAPTER_7_REWRITE`

G07 chiude l'audit cross-group senza nuove simulazioni.

## Classificazione dei 18 controlli

- PASS: 15;
- PASS_CON_LIMITAZIONE: 1;
- EVIDENZA_PARZIALE: 2;
- NON_OSSERVATO: 0;
- FAIL: 0.

## Dimensione della campagna

- run runtime valide: 117;
- tempo simulato: 34860 s
  (581 min,
  9.68 h);
- task generati e attivati: 1223256;
- task rimossi alla deadline: 1214390;
- task pendenti alla fine delle run: 8866;
- job GA inviati: 12718;
- strategie applicate: 12363;
- risultati stale: 267;
- job in-flight allo shutdown: 88.

La contabilità globale è coerente:

`submitted = applied + stale + shutdown`

`12718 =
12363 +
267 +
88`

Anche il lifecycle dei task è coerente:

`generated = activated = removed_at_deadline + pending_at_end`

## Risultati dimostrati

1. Il runtime usa dati live MOSAIC e mantiene la causalità degli snapshot.
2. Non sono stati osservati snapshot o pool provenienti dal futuro.
3. Il candidato LOCAL è presente per ogni veicolo negli snapshot controllati.
4. I pool di banda osservati sono finiti, non negativi e coerenti.
5. Le componenti della fitness sono serializzate e numericamente valide.
6. La contabilità asincrona dei job è coerente.
7. I file strutturati controllati sono parsabili.
8. Il placeholder legacy `CLOUD_STABLE_PLACEHOLDER` non compare nel runtime.
9. G02B mostra un effetto netto di LOCAL_ONLY: minore costo computazionale,
   ma forte contesa locale.
10. Il sistema scarta correttamente i risultati stale invece di applicarli.

## Evidenze parziali

### Integrità della mobilità SUMO

Le 69 materializzazioni non riportano errori
SUMO, teleport o emergency braking nei contatori disponibili. Tuttavia,
teleport ed emergency braking non sono contatori canonici uniformi del
validator di tutte le run. Il risultato resta quindi parziale, ma non
bloccante.

### Lifecycle dei veicoli

Sono osservati JOIN, aggiornamenti di posizione e aggiornamenti di velocità.
Non è osservato alcun evento LEFT nella finestra di snapshot pubblicati
conservata. Non bisogna quindi dichiarare che l'intero ciclo JOIN/UPDATE/LEFT
sia stato dimostrato sperimentalmente.

## Limite principale

`taskCompletionModel = NOT_IMPLEMENTED`

Il prototipo genera task, costruisce snapshot, ottimizza assegnazioni,
applica strategie e rimuove i task alla deadline. Non simula però il consumo
progressivo del lavoro fino al completamento applicativo.

Di conseguenza non sono sostenibili affermazioni su:

- task effettivamente completati;
- throughput applicativo end-to-end;
- response time finale consumato;
- percentuale reale di deadline rispettate dopo l'esecuzione;
- energia o costo operativo realmente consumati.

## Decisione editoriale

Le evidenze sono sufficienti per iniziare l'analisi condivisa dei risultati
e, successivamente, riscrivere il capitolo 7. La riscrittura non deve partire
automaticamente dai report: prima occorre concordare interpretazioni,
grafici, tabelle, limiti e formulazioni ammesse.
