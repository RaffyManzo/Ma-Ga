# G01 Pipeline Validation Audit

## 1. Identita della campagna
- Campaign_ID: final-test-campaign
- Group: G01 - pipeline validation
- Config_ID: CFG-SMOKE
- Materialization_ID richiesto: MAT-SMOKE-104729
- Materialization_ID canonico G00 usato: MAT-CFG-SMOKE-104729
- Run_ID: PRE-03-SMOKE
- Stato audit: FAILED - RETRY-02 MOSAIC INTERRUPTED

## 2. Commit e stato Git
- Branch: testing/final-campaign
- HEAD iniziale: 6449978026d205c3f0949cd5532782fd447fed1d
- Commit atteso G01: 6449978026d205c3f0949cd5532782fd447fed1d
- Commit retry G01R: 8cf83f53f3c08016edaacfc19a83fcbb767b479a
- Commit retry G01R2: 33958c27ef7b312bf27192a79650220beabe1dd4
- Commit scientifico congelato: 5a9477735a3d707a5f000a64653cd2a6fc7f2007
- Working tree prima della run: clean
- Working tree prima del retry G01R: clean
- Working tree prima del retry G01R2: clean
- Diff Java congelato prima della run: vuoto
- Diff Java congelato prima del retry G01R: vuoto
- Diff Java congelato prima del retry G01R2: vuoto
- Stato post-G01R2: RETRY-02 ha completato build e deploy, ha creato una nuova run MOSAIC, ma la simulazione si e' interrotta a 0s; nessun file Java congelato modificato.

## 3. Obiettivi
Eseguire una sola smoke run end-to-end della pipeline SUMO -> MOSAIC -> Live-State Layer -> SystemSnapshot -> TemporalWindowManager -> MA-GA -> applicazione strategia -> reporting -> validator usando l'istanza gia' materializzata da G00. La run iniziale non e' arrivata a MOSAIC per fallimento del deploy validator canonico. Dopo G00C e G01E, la correzione dei metadati canonici e la build runtime esterna sono state validate. RETRY-01 si e' interrotto durante la propagazione dello stderr non fatale del build; G01F ha corretto quel comportamento. RETRY-02 e' stato eseguito una sola volta, ha superato build e deploy, ha creato una nuova run MOSAIC, ma la simulazione si e' interrotta a 0s prima della produzione dei report live e del validator smoke.

## 4. Configurazioni e run
- Config_ID: CFG-SMOKE
- Materialization_ID nel piano G00: MAT-CFG-SMOKE-104729
- Directory materializzata: tmp/materialized-literature-scenarios/final-test-campaign/G01_pipeline_validation/CFG-SMOKE/104729/
- Stato validazione G00: MATERIALIZED_VALIDATED
- Stato compatibilita deploy dopo G00C: campaign validator PASS, canonical validator PASS per MAT-CFG-SMOKE-104729
- Run_ID: PRE-03-SMOKE
- Scenario MOSAIC: MaGaLiteratureBasedUrbanStudy
- Directory MOSAIC run del retry G01R2: tmp/mosaic-25.2/logs/log-20260621-182204-MaGaLiteratureBasedUrbanStudy
- Nuove run MOSAIC RETRY-02: 1
- Stato G01 corrente: FAILED - RETRY-02 MOSAIC INTERRUPTED

## 5. Comandi eseguiti
- `git branch --show-current; git rev-parse HEAD; git status --short; git diff --name-only -- src tools/mosaic-live-maga-runtime/src tools/mosaic-live-state-layer/src tools/mosaic-adhoc-radio-diagnostic/src`
- `Inventory and move tmp/mosaic-25.2/scenarios/MaGaLiteratureBasedUrbanStudy to tmp/archive/final-test-campaign-pre-G01-<timestamp>/`
- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/intas-literature-scenario/run_literature_scenario.ps1 -MaterializedScenarioRoot "tmp/materialized-literature-scenarios/final-test-campaign/G01_pipeline_validation/CFG-SMOKE/104729/" -MosaicRoot ".\tmp\mosaic-25.2" -ScenarioName "MaGaLiteratureBasedUrbanStudy" -PrintDetailedLiveReport`
- G00C: riparazione dei metadati canonical deploy e validazione campaign/canonical delle 69 materializzazioni.
- G01B: diagnostica controllata dell'AccessDeniedException durante build runtime.
- G01C: validazione negativa del workaround `-implicit:none`.
- G01D: isolamento per workspace, source set e JDK.
- G01E: validazione della staging esterna e modifica minima di `tools/mosaic-live-maga-runtime/build.ps1`.
- G01R: `powershell -NoProfile -ExecutionPolicy Bypass -File tools/intas-literature-scenario/run_literature_scenario.ps1 -MaterializedScenarioRoot "tmp/materialized-literature-scenarios/final-test-campaign/G01_pipeline_validation/CFG-SMOKE/104729/" -MosaicRoot ".\tmp\mosaic-25.2" -ScenarioName "MaGaLiteratureBasedUrbanStudy" -PrintDetailedLiveReport`
- G01F: test annidato controllato di `tools/mosaic-live-maga-runtime/build.ps1` tramite harness PowerShell padre con `ErrorActionPreference=Stop`.
- G01R2 / RETRY-02: `powershell -NoProfile -ExecutionPolicy Bypass -File tools/intas-literature-scenario/run_literature_scenario.ps1 -MaterializedScenarioRoot "tmp/materialized-literature-scenarios/final-test-campaign/G01_pipeline_validation/CFG-SMOKE/104729/" -MosaicRoot ".\tmp\mosaic-25.2" -ScenarioName "MaGaLiteratureBasedUrbanStudy" -PrintDetailedLiveReport`

Il comando G01R e' stato eseguito una sola volta. Non sono stati eseguiti rilanci automatici della run smoke, `quick_literature_workflow.ps1`, nuove materializzazioni, SUMO o MOSAIC.
G01F non ha eseguito deploy, SUMO, MOSAIC o validator smoke.
Il comando RETRY-02 e' stato eseguito una sola volta; non e' stato eseguito un secondo tentativo dopo l'interruzione MOSAIC.

## 6. Output grezzi
- Log console integrale del primo tentativo: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/run_literature_scenario_console.log`
- Pre-check: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/pre_execution_checks.json`
- Preparazione ambiente: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/environment_preparation.json`
- Failure summary deploy: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/deploy_validation_failure.json`
- Run evidence index: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/run_evidence_index.json`
- Build preflight: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/runtime_build_preflight.json`
- Access diagnostic: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/runtime_build_access_diagnostic.json`
- Implicit none validation: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/runtime_build_implicit_none_validation.json`
- JDK/workspace isolation: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/runtime_jdk_workspace_isolation.json`
- Runtime out consumer inventory: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/runtime_out_consumer_inventory.json`
- External workspace build validation: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/runtime_external_workspace_build_validation.json`
- Retry G01R console log: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-01/run_literature_scenario_console.log`
- Retry G01R runs before inventory: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-01/mosaic_runs_before.csv`
- Retry G01R runs after inventory: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-01/mosaic_runs_after.csv`
- Retry G01R new run metadata: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-01/new_mosaic_run.json`
- Retry G01R execution summary: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-01/retry_execution_summary.json`
- G01F nested PowerShell build validation: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/runtime_nested_powershell_build_validation.json`
- G01F nested parent stdout: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/runtime_nested_powershell_build_validation/nested_parent_stdout.txt`
- G01F nested parent stderr: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/runtime_nested_powershell_build_validation/nested_parent_stderr.txt`
- G01F published out inventory: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/runtime_nested_powershell_build_validation/published_out_inventory.csv`
- Retry-02 console log: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-02/run_literature_scenario_console.log`
- Retry-02 runs before inventory: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-02/mosaic_runs_before.csv`
- Retry-02 runs after inventory: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-02/mosaic_runs_after.csv`
- Retry-02 new run metadata: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-02/new_mosaic_run.json`
- Retry-02 local run artifact inventory: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-02/local_run_artifact_inventory.csv`
- Retry-02 execution summary: `test-results/final-campaign/G01_pipeline_validation/PRE-03-SMOKE/retry-02/retry_execution_summary.json`
- G00D bandwidth scan before repair: `test-results/final-campaign/G00_scenario_preparation_generation/bandwidth_serialization_scan_before.csv`
- G00D bandwidth scan after repair: `test-results/final-campaign/G00_scenario_preparation_generation/bandwidth_serialization_scan_after.csv`
- G00D bandwidth repair report: `test-results/final-campaign/G00_scenario_preparation_generation/bandwidth_serialization_repair_report.json`

## 7. Esito validator
- Primo tentativo G01: FAILED_DEPLOY_VALIDATOR / INVALID_MATERIALIZED_SCENARIO.
- Errori iniziali rilevati dal deploy validator:
  - Materialization manifest must declare synthetic-calibrated InTAS mobility mode.
  - Materialization report must declare gaParameterScalingMode = STATIC.
  - Materialization manifest must declare gaParameterScalingMode = STATIC.
- Dopo G00C, MAT-CFG-SMOKE-104729 risulta compatibile sia con il validator della campagna sia con il validator canonico.
- Dopo G01E, la build runtime canonica produce 252 classi e un JAR valido senza `AccessDeniedException` o internal compiler exception.
- Retry G01R: il comando e' terminato con exit code 1 prima del deploy/MOSAIC; non e' stata creata alcuna nuova directory MOSAIC.
- Retry G01R: `AccessDeniedException` assente; `An exception has occurred in the compiler` assente; validator smoke non eseguito.
- G01F: il test annidato ha confermato che la build produce 252 classi, 11 JAR di classpath, `sources.txt` con 198 righe, JAR runtime valido e stderr del processo padre vuoto.
- RETRY-02: runtime build PASS, deploy PASS, nuova run MOSAIC = 1.
- RETRY-02: MOSAIC ha avviato la federazione ma la simulazione e' terminata a 0s con `Simulation interrupted: -1`.
- RETRY-02: i report `live-maga-runtime` e `literature_smoke_validation.json` non sono stati prodotti; validator smoke non eseguito.
- G01 resta FAILED perche' la smoke run end-to-end non ha prodotto `LITERATURE_SMOKE_TEST_PASSED`.

## 8. Metriche
Le metriche runtime non sono disponibili perche' RETRY-02 si e' interrotto a 0s prima della produzione dei report live. `simulation_completed=false`; le altre metriche runtime richieste restano `NOT_AVAILABLE` in `test-audits/final-campaign/G01_pipeline_validation/metrics_G01.csv`. Le metriche diagnostiche di build sono registrate nei file G01B-G01F e nei summary dei retry, non come risultati sperimentali MA-GA.

## 9. Copertura Test_ID
Test_ID primari G01 registrati: T-011, T-015, T-017, T-020, T-021. Test_ID con copertura G01 primaria o secondaria: T-002, T-010, T-011, T-012, T-013, T-014, T-015, T-016, T-017, T-018, T-020, T-021, T-035. La copertura resta diagnostica e non valida ancora la pipeline end-to-end.

## 10. Anomalie
Sono registrate in `test-audits/final-campaign/G01_pipeline_validation/anomalies_G01.csv`:
- AN-G01-0001 = OPEN: la pipeline G01 non ha ancora prodotto una smoke run valida con `LITERATURE_SMOKE_TEST_PASSED`.
- AN-G01-0002 = ACCEPTED_LIMITATION: discrepanza nominale tra MAT-SMOKE-104729 e MAT-CFG-SMOKE-104729, senza impatto sul contenuto dello scenario usato.
- AN-G01-0003 = RESOLVED: la build runtime e' stata risolta compilando in una staging esterna al workspace repository e pubblicando `out/` solo dopo compilazione e validazione completa del JAR.
- AN-G01-0004 = RESOLVED: la propagazione dello stderr non fatale di `javac` attraverso PowerShell annidato e' stata corretta.
- AN-G01-0005 = RESOLVED: MOSAIC Cell Ambassador ha rifiutato la banda in notazione scientifica; G00D ha riparato la serializzazione testuale senza cambiare i valori calibrati.

La risoluzione di AN-G01-0003, AN-G01-0004 e AN-G01-0005 non chiude AN-G01-0001: RETRY-02 ha raggiunto MOSAIC ma si e' interrotto a 0s e non ha prodotto una smoke run end-to-end valida.

## 11. Interpretazione tecnica
Il primo tentativo G01 e' stato bloccato dal validator canonico prima dell'avvio di MOSAIC. La sottofase G00C ha corretto i metadati canonical deploy preservando il core e i contenuti scientifici dello scenario; lo scenario smoke ora passa sia il campaign validator sia il canonical validator.

Durante la preparazione della ripetizione e' emersa una `AccessDeniedException` durante la build nel workspace repository. Le diagnostiche G01B, G01C e G01D hanno escluso il workaround `-implicit:none`, hanno verificato l'assenza di sorgenti Java nei JAR del classpath, e hanno isolato la causa come `WORKSPACE_OR_SCANNER_INTERFERENCE`: fuori da `C:\Users\raffa\IdeaProjects`, lo stesso Oracle JDK 17.0.12 compila 198 sorgenti in 252 classi senza eccezioni. Il processo esterno preciso non e' stato identificato; non viene attribuita con certezza la causa a IntelliJ, antivirus o altro scanner.

G01E ha validato una build in staging esterna e ha modificato solo `tools/mosaic-live-maga-runtime/build.ps1`, senza modificare file Java o il core congelato. La build canonica modificata produce 252 classi e un JAR valido senza `AccessDeniedException` o internal compiler exception, preservando la struttura finale `out/classes`, `out/classpath`, `out/sources.txt` e `out/maga-live-maga-runtime.jar`.

Il retry G01R e' stato avviato una sola volta al commit `8cf83f53f3c08016edaacfc19a83fcbb767b479a`, ma la shell esterna ha terminato il comando durante la fase di build dopo l'emissione del contesto `javac` su stderr. La build aveva realmente prodotto una `out/` completa: 252 classi pubblicate, 11 JAR di classpath pubblicati e JAR runtime presente. Il log del retry non contiene `AccessDeniedException` ne' internal compiler exception, ma non e' stata creata una nuova directory MOSAIC.

G01F ha identificato il punto di integrazione: `build.ps1` inoltrava le normali note di deprecazione di `javac` tramite `$Host.UI.WriteErrorLine`, e il processo PowerShell padre con `ErrorActionPreference=Stop` le trattava come errore terminante prima di raggiungere deploy e MOSAIC. Il testo catturato viene ora mostrato tramite `Write-Host`, mentre gli errori reali restano coperti dai controlli espliciti su exit code, `AccessDeniedException`, internal compiler exception, conteggio classi, classi attese, `jar tf` ed entry JAR attese. Il test annidato G01F e' passato integralmente con stderr del padre vuoto.

Non sono stati prodotti snapshot, job GA, strategie applicate o report runtime interpretabili. Dopo G01F era necessario un nuovo tentativo end-to-end; RETRY-02 ha poi raggiunto MOSAIC ma si e' interrotto durante l'inizializzazione del Cell Ambassador.

RETRY-02 e' stato avviato una sola volta al commit `33958c27ef7b312bf27192a79650220beabe1dd4`. La build runtime ha superato i controlli: 252 classi, 11 JAR nel classpath, 198 sorgenti e JAR runtime SHA-256 `1a0aa6d2b0441f0be38baf5df1acc2d4d889b9f356b6111f7715105cd013a9b4`. Il deploy canonico ha validato lo scenario e ha iniettato i JAR runtime e diagnostico. MOSAIC ha creato `log-20260621-182204-MaGaLiteratureBasedUrbanStudy`, ma la simulazione si e' interrotta immediatamente con `Simulation interrupted: -1`, prima che il runtime live producesse summary, detailed report o validator smoke.

G00D ha identificato la causa esatta dell'interruzione MOSAIC: i campi testuali di banda erano serializzati come `4.92e+07 bps`, forma rifiutata dal parser del Cell Ambassador. La `NullPointerException` in chiusura simulazione e' secondaria al fallimento di inizializzazione del Cell Ambassador. La riparazione G00D ha sostituito la serializzazione scientifica con forma fixed-point (`49200000 bps` per CFG-SMOKE), ha aggiornato le 69 materializzazioni e ha confermato staticamente campaign validator e canonical validator nei conteggi attesi. Nessun report live e' stato prodotto da RETRY-02; serve un nuovo retry dopo commit e push della correzione G00D.

## 12. Risultati riutilizzabili nella tesi
Questa sottofase documenta la separazione tra un problema ambientale di build e la logica MA-GA. La diagnostica mostra che la staging esterna e' efficace per il build runtime, preserva il core congelato e mantiene il contratto degli output `out/` richiesto dai consumer.

G01F aggiunge un'evidenza metodologica sul controllo degli stream tra PowerShell annidati: le note non fatali di `javac` restano visibili senza interrompere il processo padre prima dei controlli semantici del build. Questi dati sono riutilizzabili come evidenza di robustezza del workflow di test e di isolamento degli errori ambientali/tooling. Non costituiscono risultati sperimentali MA-GA: non misurano prestazioni, qualita' decisionale, completamento task o comportamento runtime della strategia.

## 13. Limiti
- Nessun file Java o core e' stato modificato.
- `tools/mosaic-live-maga-runtime/build.ps1` e' stato modificato solo per introdurre la staging esterna e la pubblicazione ritardata di `out/` dopo validazione completa.
- Dopo il primo tentativo fallito non sono stati eseguiti deploy, SUMO o MOSAIC.
- Non sono disponibili metriche runtime.
- Il retry G01R autorizzato e' stato consumato una sola volta e si e' fermato prima del deploy/MOSAIC.
- G01F ha eseguito solo il build annidato del runtime; non ha eseguito deploy, SUMO, MOSAIC o validator smoke.
- RETRY-02 e' stato consumato una sola volta, ha raggiunto MOSAIC, ma si e' interrotto a 0s.
- G00D ha riparato solo la serializzazione testuale della banda nelle materializzazioni e nel tooling di campagna; non sono stati eseguiti build, deploy, SUMO o MOSAIC.
- E' necessario un nuovo retry end-to-end dopo commit e push della correzione G00D.
- Il blocker generale G01 resta aperto.
- L'interferenza e' stata localizzata al workspace/scanner, ma il processo responsabile non e' stato identificato con certezza.
