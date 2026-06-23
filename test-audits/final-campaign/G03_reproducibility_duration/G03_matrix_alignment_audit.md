# Audit di allineamento delle matrici dopo G03

## Criterio

Ogni foglio è stato letto integralmente e classificato come aggiornato oppure verificato senza modifiche.
Non sono rimasti stati `DA_ESEGUIRE_G03`, riferimenti a G03 non avviata, decisioni G02B ancora aperte
o indicazioni che richiedano di ripetere H-S-EXT-01.

## Matrice completa

| Foglio | Esito | Intervento |
| --- | --- | --- |
| 00_Guida | AGGIORNATO | Stato G03, sequenza successiva, KPI e collegamento al nuovo foglio risultati. |
| 01_Profili | AGGIORNATO | Evidenze extended e limite high_density a 464 s. |
| 02_Configurazioni | AGGIORNATO | Quattro configurazioni G03 riallineate a PASS/limite stress. |
| 03_Piano_run | AGGIORNATO | Tutte le 11 azioni G03, run directory, validator, metriche e stato G02B differito. |
| 04_Catalogo_test | AGGIORNATO | T-010, T-014 e T-018; evidenza parziale T-032. |
| 05_Output_dizionario | AGGIORNATO | Nuovi artefatti e stato OPTIONAL_STRESS_LIMIT. |
| 06_Oracoli | AGGIORNATO | Dati SUMO non inferibili, validità simulationCompleted e oracolo stress opzionale. |
| 07_Checklist | AGGIORNATO | Extended 5/5, verifiche G03 e decisione G02B dopo G06. |
| 08_Limiti | AGGIORNATO | Limite endurance, variabilità host e interpretazione delle run opzionali. |
| 09_Fonti | AGGIORNATO | Bundle diagnostico, evidenze G03 e decisione sulla sequenza G02B. |
| 10_Risultati_G00_G01 | AGGIORNATO | Stato storico riallineato a G02/G03 e gruppi successivi. |
| 11_Pre_G02_Checkpoint | AGGIORNATO | Nome storico mantenuto; aggiunto checkpoint G03 completo. |
| 12_Risultati_G02 | AGGIORNATO | G02B differita dopo G06 e baseline da riutilizzare. |
| 13_Risultati_G03 | CREATO | Dettaglio delle 11 azioni, analisi runtime, extended e stress limit. |

## Matrice semplificata

| Foglio | Esito | Intervento |
| --- | --- | --- |
| 00_Dashboard | AGGIORNATO | KPI G03, stato complessivo e sequenza successiva. |
| 01_Run_G02 | VERIFICATO/AGGIORNATO | Storico G02 marcato come definitivo. |
| 02_Riepilogo_config | AGGIORNATO | Pianificazione G02B dopo G06. |
| 03_Storico | AGGIORNATO | Aggiunta chiusura G03 e prossimo gruppo G04. |
| 04_Legenda | AGGIORNATO | Nuovi stati G03 e G02B. |
| 05_Risultati_G03 | CREATO | Riepilogo operativo e metriche essenziali. |

## Controlli finali

- Nessun errore formula `#REF!`, `#DIV/0!`, `#VALUE!`, `#NAME?` o `#N/A`.
- Nessun riferimento residuo `DA_ESEGUIRE_G03`.
- Nessun riferimento residuo a `G02B DECISION_REQUIRED` come stato corrente.
- G04, G05, G06 e G07 restano correttamente non eseguiti.
- G02B è `DEFERRED_AFTER_G06`.
- H-S-EXT-01 è `OPTIONAL_STRESS_LIMIT`, non PASS e non da rilanciare selettivamente.
