# G01 Pipeline Validation Audit

## 1. Identita della campagna
- Campaign_ID: final-test-campaign
- Group: G01 - pipeline validation
- Config_ID: CFG-SMOKE
- Materialization_ID richiesto: MAT-SMOKE-104729
- Materialization_ID canonico G00 usato: MAT-CFG-SMOKE-104729
- Run_ID: PRE-03-SMOKE
- Stato audit: FAILED - RETRY PENDING

## 2. Commit e stato Git
- Branch: testing/final-campaign
- HEAD iniziale: 6449978026d205c3f0949cd5532782fd447fed1d
- Commit atteso G01: 6449978026d205c3f0949cd5532782fd447fed1d
- Commit scientifico congelato: 5a9477735a3d707a5f000a64653cd2a6fc7f2007
- Working tree prima della run: clean
- Diff Java congelato prima della run: vuoto
- Stato post-G01E-F: modifiche documentali e diagnostiche non committate; nessun file Java congelato modificato.

## 3. Obiettivi
Eseguire una sola smoke run end-to-end della pipeline SUMO -> MOSAIC -> Live-State Layer -> SystemSnapshot -> TemporalWindowManager -> MA-GA -> applicazione strategia -> reporting -> validator usando l'istanza gia' materializzata da G00. La run iniziale non e' arrivata a MOSAIC per fallimento del deploy validator canonico. Dopo G00C e G01E, la correzione dei metadati canonici e la build runtime esterna sono state validate, ma la run end-to-end deve ancora essere ripetuta.

## 4. Configurazioni e run
- Config_ID: CFG-SMOKE
- Materialization_ID nel piano G00: MAT-CFG-SMOKE-104729
- Directory materializzata: tmp/materialized-literature-scenarios/final-test-campaign/G01_pipeline_validation/CFG-SMOKE/104729/
- Stato validazione G00: MATERIALIZED_VALIDATED
- Stato compatibilita deploy dopo G00C: campaign validator PASS, canonical validator PASS per MAT-CFG-SMOKE-104729
- Run_ID: PRE-03-SMOKE
- Scenario MOSAIC: MaGaLiteratureBasedUrbanStudy
- Directory MOSAIC run: non creata dal tentativo valido, perche' il primo tentativo si e' fermato prima dell'esecuzione MOSAIC
- Stato G01 corrente: FAILED - RETRY PENDING

## 5. Comandi eseguiti
- `git branch --show-current; git rev-parse HEAD; git status --short; git diff --name-only -- src tools/mosaic-live-maga-runtime/src tools/mosaic-live-state-layer/src tools/mosaic-adhoc-radio-diagnostic/src`
- `Inventory and move tmp/mosaic-25.2/scenarios/MaGaLiteratureBasedUrbanStudy to tmp/archive/final-test-campaign-pre-G01-<timestamp>/`
- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/intas-literature-scenario/run_literature_scenario.ps1 -MaterializedScenarioRoot "tmp/materialized-literature-scenarios/final-test-campaign/G01_pipeline_validation/CFG-SMOKE/104729/" -MosaicRoot ".\tmp\mosaic-25.2" -ScenarioName "MaGaLiteratureBasedUrbanStudy" -PrintDetailedLiveReport`
- G00C: riparazione dei metadati canonical deploy e validazione campaign/canonical delle 69 materializzazioni.
- G01B: diagnostica controllata dell'AccessDeniedException durante build runtime.
- G01C: validazione negativa del workaround `-implicit:none`.
- G01D: isolamento per workspace, source set e JDK.
- G01E: validazione della staging esterna e modifica minima di `tools/mosaic-live-maga-runtime/build.ps1`.

Non sono stati eseguiti rilanci automatici della run smoke, `quick_literature_workflow.ps1`, nuove materializzazioni, deploy dopo G01E, SUMO o MOSAIC.

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

## 7. Esito validator
- Primo tentativo G01: FAILED_DEPLOY_VALIDATOR / INVALID_MATERIALIZED_SCENARIO.
- Errori iniziali rilevati dal deploy validator:
  - Materialization manifest must declare synthetic-calibrated InTAS mobility mode.
  - Materialization report must declare gaParameterScalingMode = STATIC.
  - Materialization manifest must declare gaParameterScalingMode = STATIC.
- Dopo G00C, MAT-CFG-SMOKE-104729 risulta compatibile sia con il validator della campagna sia con il validator canonico.
- Dopo G01E, la build runtime canonica produce 252 classi e un JAR valido senza `AccessDeniedException` o internal compiler exception.
- La smoke run end-to-end non e' ancora stata ripetuta; pertanto G01 resta FAILED - RETRY PENDING.

## 8. Metriche
Le metriche runtime non sono disponibili perche' MOSAIC non e' stato avviato nel primo tentativo valido. `simulation_completed=false`; le altre metriche runtime richieste restano `NOT_AVAILABLE` in `test-audits/final-campaign/G01_pipeline_validation/metrics_G01.csv` fino al retry. Le metriche diagnostiche di build sono registrate nei file G01B-G01E, non come risultati sperimentali MA-GA.

## 9. Copertura Test_ID
Test_ID primari G01 registrati: T-011, T-015, T-017, T-020, T-021. Test_ID con copertura G01 primaria o secondaria: T-002, T-010, T-011, T-012, T-013, T-014, T-015, T-016, T-017, T-018, T-020, T-021, T-035. La copertura resta diagnostica e non valida ancora la pipeline end-to-end.

## 10. Anomalie
Sono registrate in `test-audits/final-campaign/G01_pipeline_validation/anomalies_G01.csv`:
- AN-G01-0001 = OPEN: il deploy/run G01 resta da ripetere end-to-end.
- AN-G01-0002 = ACCEPTED_LIMITATION: discrepanza nominale tra MAT-SMOKE-104729 e MAT-CFG-SMOKE-104729, senza impatto sul contenuto dello scenario usato.
- AN-G01-0003 = RESOLVED: la build runtime e' stata risolta compilando in una staging esterna al workspace repository e pubblicando `out/` solo dopo compilazione e validazione completa del JAR.

La risoluzione di AN-G01-0003 non chiude AN-G01-0001: la smoke run deve ancora essere rilanciata e superare deploy, MOSAIC, reporting e validator.

## 11. Interpretazione tecnica
Il primo tentativo G01 e' stato bloccato dal validator canonico prima dell'avvio di MOSAIC. La sottofase G00C ha corretto i metadati canonical deploy preservando il core e i contenuti scientifici dello scenario; lo scenario smoke ora passa sia il campaign validator sia il canonical validator.

Durante la preparazione della ripetizione e' emersa una `AccessDeniedException` durante la build nel workspace repository. Le diagnostiche G01B, G01C e G01D hanno escluso il workaround `-implicit:none`, hanno verificato l'assenza di sorgenti Java nei JAR del classpath, e hanno isolato la causa come `WORKSPACE_OR_SCANNER_INTERFERENCE`: fuori da `C:\Users\raffa\IdeaProjects`, lo stesso Oracle JDK 17.0.12 compila 198 sorgenti in 252 classi senza eccezioni. Il processo esterno preciso non e' stato identificato; non viene attribuita con certezza la causa a IntelliJ, antivirus o altro scanner.

G01E ha validato una build in staging esterna e ha modificato solo `tools/mosaic-live-maga-runtime/build.ps1`, senza modificare file Java o il core congelato. La build canonica modificata produce 252 classi e un JAR valido senza `AccessDeniedException` o internal compiler exception, preservando la struttura finale `out/classes`, `out/classpath`, `out/sources.txt` e `out/maga-live-maga-runtime.jar`.

La run MOSAIC deve ancora essere ripetuta: non sono stati prodotti snapshot, job GA, strategie applicate o report runtime interpretabili.

## 12. Risultati riutilizzabili nella tesi
Questa sottofase documenta la separazione tra un problema ambientale di build e la logica MA-GA. La diagnostica mostra che la staging esterna e' efficace per il build runtime, preserva il core congelato e mantiene il contratto degli output `out/` richiesto dai consumer.

Questi dati sono riutilizzabili come evidenza metodologica di robustezza del workflow di test e di isolamento degli errori ambientali. Non costituiscono risultati sperimentali MA-GA: non misurano prestazioni, qualita' decisionale, completamento task o comportamento runtime della strategia.

## 13. Limiti
- Nessun file Java o core e' stato modificato.
- `tools/mosaic-live-maga-runtime/build.ps1` e' stato modificato solo per introdurre la staging esterna e la pubblicazione ritardata di `out/` dopo validazione completa.
- Dopo il primo tentativo fallito non sono stati eseguiti deploy, SUMO o MOSAIC.
- Non sono disponibili metriche runtime.
- Il blocker generale G01 resta aperto fino al retry della smoke run end-to-end.
- L'interferenza e' stata localizzata al workspace/scanner, ma il processo responsabile non e' stato identificato con certezza.
