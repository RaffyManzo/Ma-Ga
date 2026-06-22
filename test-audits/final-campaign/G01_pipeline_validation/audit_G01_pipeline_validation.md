# G01 Pipeline Validation Audit

## 1. Identita della campagna
- Campaign_ID: final-test-campaign
- Group_ID: G01
- Config_ID: CFG-SMOKE
- Materialization_ID: MAT-CFG-SMOKE-104729
- Run_ID: PRE-03-SMOKE
- ScenarioName: MaGaLiteratureBasedUrbanStudy
- Stato audit: COMPLETED - RETRY-04 SIMULATION PASSED WITH OFFLINE POST-PROCESSING RECOVERY

## 2. Commit e stato Git
- Core congelato originario: 5a9477735a3d707a5f000a64653cd2a6fc7f2007
- Commit prima del checkpoint tooling G01G-G01L: 3e02146c9161e70caeae76c673f4068de5ecf1b7
- Checkpoint tooling pubblicato prima di RETRY-04: 7b3f6359aae81388d009d35da3adb527fc8f11b8
- Subject checkpoint tooling: fix(campaign): support validated recovered runtime artifact
- Working tree pre-RETRY-04: clean per i file versionabili dopo il checkpoint tooling.
- Diff Java/core congelato: vuoto.

## 3. Obiettivi
G01 doveva validare la pipeline tecnica end-to-end per la configurazione smoke gia' materializzata: deploy, avvio SUMO/MOSAIC, runtime MA-GA live, reporting e smoke validator. RETRY-04 ha usato esplicitamente la modalita' RECOVERED_VALIDATED_ARTIFACT per evitare una fresh build Windows non riproducibile e per usare il JAR runtime gia' prodotto e deployato in RETRY-02, validato per dimensione, SHA-256 e contenuto. Il tentativo non ha completato G01 perche' il summarizer si e' fermato dopo la simulazione MOSAIC.

## 4. Configurazioni e run
- Configurazione: CFG-SMOKE
- Materializzazione usata: MAT-CFG-SMOKE-104729
- Seed: 104729
- Density: nominal
- Duration profile: smoke
- gaParameterScalingMode: STATIC
- Mobility mode: SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK
- RETRY-04 attempt: eseguito una sola volta.
- Runtime artifact mode: RECOVERED_VALIDATED_ARTIFACT
- Runtime JAR SHA-256: 1a0aa6d2b0441f0be38baf5df1acc2d4d889b9f356b6111f7715105cd013a9b4
- Runtime JAR size: 491454 bytes

## 5. Comandi eseguiti
Il checkpoint tooling e' stato committato e pubblicato prima del retry. RETRY-04 e' stato avviato una sola volta tramite 	ools/intas-literature-scenario/run_literature_scenario.ps1, con -RuntimeArtifactMode RECOVERED_VALIDATED_ARTIFACT, il percorso assoluto del JAR recuperato, SHA-256 atteso e dimensione attesa. Non sono state eseguite fresh build, uild.ps1, javac, rimaterializzazioni, fix post-run o ulteriori retry.

## 6. Output grezzi
- Attempt guard: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/attempt_guard.json
- Runner command: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/runner_command.txt
- Runner stdout: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/runner_stdout.txt
- Runner stderr: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/runner_stderr.txt
- Runner console: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/runner_console.txt
- Runner exit code: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/runner_exit_code.txt
- Summary RETRY-04: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/retry-04_summary.json
- New MOSAIC run metadata: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/new_mosaic_run.json
- Detailed runtime report JSON: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/live_detailed_execution_report.json
- Detailed runtime report MD/TXT: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/live_detailed_execution_report.md, 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/live_detailed_execution_report.txt
- Runtime trace CSV/JSONL copiati: job events, temporal step records, GA runtime trace, strategy application trace, bridge snapshot trace, overrun trace, published snapshot manifest.
- Local run artifact inventory: 	est-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-04/local_run_artifact_inventory.csv

## 7. Esito validator
- Runtime artifact validation: PASS.
- Deploy: PASS.
- MOSAIC launch: PASS.
- SUMO launch: PASS.
- Simulazione MOSAIC: completata a 180s/180s.
- Runner exit code: 1.
- Final summarizer: FAIL, summarize-run.ps1 ha cercato C:\Users\raffa\IdeaProjects\maga-core\C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2.
- Smoke validator finale: NOT_RUN_SUMMARIZER_FAILED.
- Validator status LITERATURE_SMOKE_TEST_PASSED: non prodotto.

## 8. Metriche
Le metriche runtime disponibili da live_detailed_execution_report.json sono state registrate in metrics_G01.csv. RETRY-04 ha prodotto 243 GA jobs submitted/completed, 233 applied e 10 stale discarded; runtime GA mean 0.036802902543209871s, median 0.004650299s, P95 0.1787343s, max 0.5146541s. I conteggi task derivati dai tick coordinator sono 101 generated, 101 activated e 101 expired. 	asks_pending_at_end, 	asks_pending_peak, sequenze stale finali, SUMO errors, teleports ed emergency braking non sono disponibili perche' il summarizer/validator finale non e' stato completato. 	askCompletionModel = NOT_IMPLEMENTED; non viene calcolato alcun task completion rate.

## 9. Copertura Test_ID
Il tentativo copre la catena tecnica dei Test_ID G01 associati alla smoke run fino al completamento MOSAIC e alla generazione dei report runtime. Non copre la chiusura positiva del validator finale, quindi G01 resta fallita e non e' pronta per G02.

## 10. Anomalie
- AN-G01-0001 = OPEN: la pipeline non ha ancora prodotto LITERATURE_SMOKE_TEST_PASSED; RETRY-04 si e' fermato dopo MOSAIC durante il summarizer.
- AN-G01-0002 = ACCEPTED_LIMITATION: discrepanza nominale MAT-SMOKE-104729 / MAT-CFG-SMOKE-104729 senza impatto sul contenuto.
- AN-G01-0003 = ACCEPTED_LIMITATION: fresh javac non e' riproducibilmente stabile nell'ambiente Windows; il JAR recuperato e' un artefatto congelato gia' deployato e verificato, non una fresh build.
- AN-G01-0004 = RESOLVED: propagazione stderr PowerShell annidata corretta.
- AN-G01-0005 = RESOLVED: serializzazione banda MOSAIC riparata in forma fixed-point.
- AN-G01-0006 = RESOLVED: normalizzazione e propagazione di MosaicRoot relativo/assoluto corretta per build, deploy, summarizer e validator a livello runner. RETRY-04 rivela pero' che anche summarize-run.ps1 deve gestire internamente MosaicRoot assoluto; non e' stato applicato fix in questo incarico.

## 11. Interpretazione tecnica
Il primo tentativo G01 e' stato bloccato dal validator canonico; G00C ha corretto i metadati. RETRY-01 si e' fermato sulla propagazione dello stderr non fatale; G01F ha corretto il comportamento. RETRY-02 ha raggiunto MOSAIC ma si e' interrotto sul parsing della banda in notazione scientifica; G00D ha riparato la serializzazione. RETRY-03 si e' fermato prima della build per path assoluto ricombinato; G01G ha corretto runner e build script. G01H e G01J hanno confermato la non riproducibilita' della fresh build Windows per AccessDeniedException ZipFS/JavacFileManager. G01K ha recuperato copie valide del JAR RETRY-02 e G01L ha introdotto la modalita' esplicita RECOVERED_VALIDATED_ARTIFACT. Il checkpoint tooling e' stato pubblicato prima di RETRY-04. RETRY-04 ha validato il JAR, ha deployato, ha avviato SUMO/MOSAIC e ha completato la simulazione, ma si e' fermato durante summarize-run.ps1 per una nuova normalizzazione incompleta di MosaicRoot assoluto. Nessun fix e nessun secondo retry sono stati eseguiti.

## 12. Risultati riutilizzabili nella tesi
Sono riutilizzabili come risultati tecnici: correzione della compatibilita' canonical deploy, serializzazione MOSAIC della banda, isolamento del problema ambientale javac/ZipFS, validazione del JAR recuperato e prova che il runtime puo' produrre report live durante una smoke run completa di 180s. Non sono risultati comparativi MA-GA: il validator finale non e' passato e il modello di completamento task resta NOT_IMPLEMENTED.

## 13. Limiti
- Nessun file Java o core congelato e' stato modificato.
- RETRY-04 non chiude G01: il validator finale non ha prodotto LITERATURE_SMOKE_TEST_PASSED.
- Il problema ambientale fresh build Windows resta una limitazione accettata, non risolta.
- Il JAR runtime usato in RETRY-04 e' recuperato e validato, non prodotto da una nuova build.
- 	askCompletionModel = NOT_IMPLEMENTED; nessun task success rate viene calcolato.
- SUMO errors, teleports ed emergency braking non sono disponibili nei report copiati.
- G02 non e' stata avviata.

### Chiusura finale RETRY-04

RETRY-04 Ã¨ stato eseguito esattamente una volta usando la modalitÃ  RECOVERED_VALIDATED_ARTIFACT.

Il deploy del JAR Ã¨ riuscito. MOSAIC e SUMO sono stati avviati e la simulazione ha raggiunto 180 secondi su 180.

Il runner originale ha restituito exit code 1 dopo la simulazione perchÃ© summarize-run.ps1 ricombinava RepoRoot con un MosaicRoot giÃ  assoluto. Non Ã¨ stata eseguita una seconda simulazione.

Dopo la correzione del summarizer, il post-processing Ã¨ stato recuperato offline sul run giÃ  prodotto:

- summarizer executions: 1;
- validator executions: 1;
- validator status: LITERATURE_SMOKE_TEST_PASSED;
- validator errors: 0;
- runtime violations: 0;
- taskCompletionModel: NOT_IMPLEMENTED.

La pipeline runtime G01 Ã¨ quindi validata. La fresh build javac su Windows resta una limitazione accettata e il JAR recuperato non deve essere presentato come risultato di una nuova build.

G02 non Ã¨ stata avviata.



