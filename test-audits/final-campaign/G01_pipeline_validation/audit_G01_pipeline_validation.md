# G01 Pipeline Validation Audit â€” versione finale consolidata

## 1. IdentitÃ 

- Campaign_ID: `final-test-campaign`
- Group_ID: `G01`
- Config_ID: `CFG-SMOKE`
- Materialization_ID: `MAT-CFG-SMOKE-104729`
- Run_ID: `PRE-03-SMOKE`
- Scenario: `MaGaLiteratureBasedUrbanStudy`
- Stato finale: `COMPLETED - RETRY-04 SIMULATION PASSED WITH OFFLINE POST-PROCESSING RECOVERY`

## 2. Stato del codice

- Core scientifico congelato: `5a9477735a3d707a5f000a64653cd2a6fc7f2007`.
- Branch della campagna: `testing/final-campaign`.
- Nessuna modifica Java al core congelato durante G00 e G01.
- Diff Java/core finale: vuoto.

## 3. Obiettivo

G01 doveva validare una singola pipeline smoke end-to-end: selezione e validazione dell'artefatto runtime, deploy, Eclipse MOSAIC, SUMO, live-state layer, generazione dei task, snapshot, bridge, TemporalWindowManager, MA-GA, applicazione delle strategie, reporting e validator.

## 4. Configurazione eseguita

- Seed: `104729`.
- DensitÃ : `nominal`.
- Durata: `180 s`.
- `gaParameterScalingMode=STATIC`.
- ModalitÃ  artefatto: `RECOVERED_VALIDATED_ARTIFACT`.
- JAR: `491454` byte.
- SHA-256 JAR: `1a0aa6d2b0441f0be38baf5df1acc2d4d889b9f356b6111f7715105cd013a9b4`.

Il JAR Ã¨ un artefatto recuperato da una precedente build riuscita, poi validato per hash, dimensione, struttura e classi attese. Non deve essere presentato come fresh build.

## 5. Sequenza effettiva di RETRY-04

1. RETRY-04 Ã¨ stato eseguito esattamente una volta.
2. La validazione del JAR e il deploy sono passati.
3. Eclipse MOSAIC e SUMO sono stati avviati correttamente.
4. La simulazione ha raggiunto `180/180 s`.
5. Il runner originale ha restituito exit code `1` dopo la simulazione, perchÃ© il summarizer ricombinava un `MosaicRoot` giÃ  assoluto.
6. Non Ã¨ stata eseguita una seconda simulazione.
7. Dopo la correzione del summarizer, summarizer e validator sono stati eseguiti una sola volta offline sul run giÃ  concluso.
8. Il validator finale ha prodotto `LITERATURE_SMOKE_TEST_PASSED` con `0` errori.

Il fallimento iniziale del summarizer Ã¨ quindi un errore di post-processing, non un fallimento della simulazione.

## 6. Esito finale

- Runtime artifact validation: `PASS`.
- Deploy: `PASS`.
- MOSAIC: `PASS`.
- SUMO: `PASS`.
- Simulazione: `PASS`, `180/180 s`.
- Post-processing automatico iniziale: `FAIL`.
- Recovery offline del post-processing: `PASS`.
- Validator: `LITERATURE_SMOKE_TEST_PASSED`.
- Errori validator: `0`.
- Violazioni runtime: `0`.
- G01: `COMPLETED` con limitazione sulla fresh build Windows.
- G02: non avviato.

## 7. Metriche dimostrate

- Task generati/attivati/rimossi alla deadline: `101/101/101`.
- Task pendenti alla fine/picco: `0/5`.
- GA submitted/completed/applied: `243/243/233`.
- Risultati stale: `10`.
- Stale ratio: `4.115226%`.
- Sequenza stale consecutiva massima: `2`.
- Runtime GA mean/median/P95/max: `0.036802903 / 0.004650299 / 0.1787343 / 0.5146541 s`.
- Snapshot lag assoluto massimo: `0 s`.
- Finestre con lag non nullo: `0`.
- Ultima strategia applicata: `180 s`.
- Secondi finali senza strategia: `0 s`.

## 8. Copertura dei test

- `T-001`: `PASS`.
- `T-002`: `PASS_CON_LIMITAZIONE`, perchÃ© il JAR Ã¨ recuperato e validato ma non deriva da una fresh build dimostrata.
- `T-011`, `T-015`, `T-017`, `T-020`, `T-021`: `PASS`.
- `T-016`: `NON_DISPONIBILE`, perchÃ© SUMO errors, teleports ed emergency braking non sono riportati esplicitamente.

Gli altri test osservati solo indirettamente restano evidenza parziale fino al gruppo responsabile.

## 9. Anomalie

- `AN-G01-0001`: `RESOLVED`; post-processing recuperato offline e validator passato.
- `AN-G01-0002`: `ACCEPTED_LIMITATION`; discrepanza nominale dell'identificativo della materializzazione.
- `AN-G01-0003`: `ACCEPTED_LIMITATION`; fresh build javac non riproducibile stabilmente nell'ambiente Windows corrente.
- `AN-G01-0004`, `AN-G01-0005`, `AN-G01-0006`: `RESOLVED`.

## 10. Limiti

- `taskCompletionModel=NOT_IMPLEMENTED`: non Ã¨ disponibile un task completion rate.
- I valori runtime SUMO errors, teleports ed emergency braking non sono disponibili e non vengono inventati.
- Lo smoke dimostra il funzionamento tecnico della pipeline, non la qualitÃ  comparativa del MA-GA.
- Le run statistiche e comparative appartengono a G02 e G02B.

## 11. Conclusione

G01 Ã¨ formalmente chiusa. La pipeline tecnica integrata Ã¨ stata validata sullo smoke senza ripetere la simulazione. La campagna puÃ² passare alla preparazione di G02 mantenendo esplicita la limitazione della fresh build e senza attribuire allo smoke risultati statistici o comparativi.