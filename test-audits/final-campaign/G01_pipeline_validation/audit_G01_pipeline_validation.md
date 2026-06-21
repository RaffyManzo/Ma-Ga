# G01 Pipeline Validation Audit

## 1. Identita della campagna
- Campaign_ID: final-test-campaign
- Group: G01 - pipeline validation
- Config_ID: CFG-SMOKE
- Materialization_ID richiesto: MAT-SMOKE-104729
- Materialization_ID canonico G00 usato: MAT-CFG-SMOKE-104729
- Run_ID: PRE-03-SMOKE
- Stato audit: FAILED - RETRY FAILED BEFORE MOSAIC

## 2. Commit e stato Git
- Branch: testing/final-campaign
- HEAD iniziale: 6449978026d205c3f0949cd5532782fd447fed1d
- Commit atteso G01: 6449978026d205c3f0949cd5532782fd447fed1d
- Commit retry G01R: 8cf83f53f3c08016edaacfc19a83fcbb767b479a
- Commit scientifico congelato: 5a9477735a3d707a5f000a64653cd2a6fc7f2007
- Working tree prima della run: clean
- Working tree prima del retry G01R: clean
- Diff Java congelato prima della run: vuoto
- Diff Java congelato prima del retry G01R: vuoto
- Stato post-G01R: retry fallito prima del deploy/MOSAIC; nessun file Java congelato modificato.

## 3. Obiettivi
Eseguire una sola smoke run end-to-end della pipeline SUMO -> MOSAIC -> Live-State Layer -> SystemSnapshot -> TemporalWindowManager -> MA-GA -> applicazione strategia -> reporting -> validator usando l'istanza gia' materializzata da G00. La run iniziale non e' arrivata a MOSAIC per fallimento del deploy validator canonico. Dopo G00C e G01E, la correzione dei metadati canonici e la build runtime esterna sono state validate. Il retry G01R e' stato avviato una sola volta, ma si e' interrotto durante la fase di build prima del deploy e prima della creazione di una nuova run MOSAIC.

## 4. Configurazioni e run
- Config_ID: CFG-SMOKE
- Materialization_ID nel piano G00: MAT-CFG-SMOKE-104729
- Directory materializzata: tmp/materialized-literature-scenarios/final-test-campaign/G01_pipeline_validation/CFG-SMOKE/104729/
- Stato validazione G00: MATERIALIZED_VALIDATED
- Stato compatibilita deploy dopo G00C: campaign validator PASS, canonical validator PASS per MAT-CFG-SMOKE-104729
- Run_ID: PRE-03-SMOKE
- Scenario MOSAIC: MaGaLiteratureBasedUrbanStudy
- Directory MOSAIC run del retry G01R: non creata; nuove run MOSAIC rilevate = 0
- Stato G01 corrente: FAILED - RETRY FAILED BEFORE MOSAIC

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

Il comando G01R e' stato eseguito una sola volta. Non sono stati eseguiti rilanci automatici della run smoke, `quick_literature_workflow.ps1`, nuove materializzazioni, SUMO o MOSAIC.
G01F non ha eseguito deploy, SUMO, MOSAIC o validator smoke.

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
- G01 resta FAILED perche' la smoke run end-to-end non ha raggiunto MOSAIC e non ha prodotto `LITERATURE_SMOKE_TEST_PASSED`.

## 8. Metriche
Le metriche runtime non sono disponibili perche' MOSAIC non e' stato avviato nel primo tentativo valido ne' nel retry G01R. `simulation_completed=false`; le altre metriche runtime richieste restano `NOT_AVAILABLE` in `test-audits/final-campaign/G01_pipeline_validation/metrics_G01.csv`. Le metriche diagnostiche di build sono registrate nei file G01B-G01E e nel summary G01R, non come risultati sperimentali MA-GA.

## 9. Copertura Test_ID
Test_ID primari G01 registrati: T-011, T-015, T-017, T-020, T-021. Test_ID con copertura G01 primaria o secondaria: T-002, T-010, T-011, T-012, T-013, T-014, T-015, T-016, T-017, T-018, T-020, T-021, T-035. La copertura resta diagnostica e non valida ancora la pipeline end-to-end.

## 10. Anomalie
Sono registrate in `test-audits/final-campaign/G01_pipeline_validation/anomalies_G01.csv`:
- AN-G01-0001 = OPEN: il deploy/run G01 resta da ripetere end-to-end.
- AN-G01-0002 = ACCEPTED_LIMITATION: discrepanza nominale tra MAT-SMOKE-104729 e MAT-CFG-SMOKE-104729, senza impatto sul contenuto dello scenario usato.
- AN-G01-0003 = RESOLVED: la build runtime e' stata risolta compilando in una staging esterna al workspace repository e pubblicando `out/` solo dopo compilazione e validazione completa del JAR.
- AN-G01-0004 = RESOLVED: la propagazione dello stderr non fatale di `javac` attraverso PowerShell annidato e' stata corretta.

La risoluzione di AN-G01-0003 e AN-G01-0004 non chiude AN-G01-0001: il retry G01R e' fallito prima di MOSAIC e non ha prodotto una smoke run end-to-end valida.

## 11. Interpretazione tecnica
Il primo tentativo G01 e' stato bloccato dal validator canonico prima dell'avvio di MOSAIC. La sottofase G00C ha corretto i metadati canonical deploy preservando il core e i contenuti scientifici dello scenario; lo scenario smoke ora passa sia il campaign validator sia il canonical validator.

Durante la preparazione della ripetizione e' emersa una `AccessDeniedException` durante la build nel workspace repository. Le diagnostiche G01B, G01C e G01D hanno escluso il workaround `-implicit:none`, hanno verificato l'assenza di sorgenti Java nei JAR del classpath, e hanno isolato la causa come `WORKSPACE_OR_SCANNER_INTERFERENCE`: fuori da `C:\Users\raffa\IdeaProjects`, lo stesso Oracle JDK 17.0.12 compila 198 sorgenti in 252 classi senza eccezioni. Il processo esterno preciso non e' stato identificato; non viene attribuita con certezza la causa a IntelliJ, antivirus o altro scanner.

G01E ha validato una build in staging esterna e ha modificato solo `tools/mosaic-live-maga-runtime/build.ps1`, senza modificare file Java o il core congelato. La build canonica modificata produce 252 classi e un JAR valido senza `AccessDeniedException` o internal compiler exception, preservando la struttura finale `out/classes`, `out/classpath`, `out/sources.txt` e `out/maga-live-maga-runtime.jar`.

Il retry G01R e' stato avviato una sola volta al commit `8cf83f53f3c08016edaacfc19a83fcbb767b479a`, ma la shell esterna ha terminato il comando durante la fase di build dopo l'emissione del contesto `javac` su stderr. La build aveva realmente prodotto una `out/` completa: 252 classi pubblicate, 11 JAR di classpath pubblicati e JAR runtime presente. Il log del retry non contiene `AccessDeniedException` ne' internal compiler exception, ma non e' stata creata una nuova directory MOSAIC.

G01F ha identificato il punto di integrazione: `build.ps1` inoltrava le normali note di deprecazione di `javac` tramite `$Host.UI.WriteErrorLine`, e il processo PowerShell padre con `ErrorActionPreference=Stop` le trattava come errore terminante prima di raggiungere deploy e MOSAIC. Il testo catturato viene ora mostrato tramite `Write-Host`, mentre gli errori reali restano coperti dai controlli espliciti su exit code, `AccessDeniedException`, internal compiler exception, conteggio classi, classi attese, `jar tf` ed entry JAR attese. Il test annidato G01F e' passato integralmente con stderr del padre vuoto.

Non sono stati prodotti snapshot, job GA, strategie applicate o report runtime interpretabili. E' ancora necessario un nuovo tentativo end-to-end dopo commit e push della correzione G01F.

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
- E' necessario un nuovo tentativo end-to-end dopo commit e push della correzione G01F.
- Il blocker generale G01 resta aperto.
- L'interferenza e' stata localizzata al workspace/scanner, ma il processo responsabile non e' stato identificato con certezza.
