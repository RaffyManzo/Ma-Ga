# MA-GA literature scenario tool

Questo e' il tool principale della pipeline finale
`MaGaLiteratureBasedUrbanStudy`.

Serve a:

1. materializzare la sottorete InTAS `candidate_0045`;
2. generare mobilita sintetica deterministica;
3. creare configurazioni MOSAIC/SUMO concrete;
4. creare il database SQLite con Scenario-Convert;
5. deployare lo scenario nel runtime MOSAIC locale;
6. eseguire MOSAIC/SUMO;
7. riassumere e validare la run live MA-GA.

Il tool non modifica il core MA-GA sotto `src/`.

## Disegno finale

La soluzione finale separa realismo topologico e controllabilita del traffico:

- la rete stradale e' una sottorete reale InTAS di Ingolstadt;
- la sottorete congelata e' `candidate_0045`;
- le due RSU sono posizionate a coordinate validate;
- la domanda veicolare e' sintetica, deterministica e riproducibile;
- SUMO simula comunque la mobilita live durante MOSAIC;
- Scenario-Convert crea un database SQLite reale usato dal federate application;
- il live-state layer e il runtime bridge costruiscono `SystemSnapshot` live.

Mobility mode:

```text
SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK
```

Il vecchio tentativo di replay diretto della finestra InTAS `14250-14430 s`
e' storico: richiedeva gestione di veicoli gia' attivi, save-state/load-state
e route in corso. La pipeline finale usa invece una sottorete reale con
domanda sintetica controllata.

## Input versionati

```text
tools/intas-literature-scenario/config/candidate_0045_edge_ids.txt
tools/intas-literature-scenario/config/synthetic_mobility_profile.json
tools/intas-literature-scenario/config/literature_calibration_catalog.json
tools/intas-literature-scenario/config/literature_scenario_targets.json
tools/intas-literature-scenario/config/reproducibility_seeds.json
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/*.template.json
```

La cartella `data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/` e' solo
template/scaffold. I file concreti vengono generati sotto `tmp/`.

## Output generati

Materialized scenarios:

```text
tmp/materialized-literature-scenarios/MaGaLiteratureBasedUrbanStudy/<density>-<duration>-seed-<seed>/
```

Scenario deployato in MOSAIC:

```text
tmp/mosaic-25.2/scenarios/MaGaLiteratureBasedUrbanStudy/
```

Run evidence:

```text
tmp/mosaic-25.2/logs/log-<timestamp>-MaGaLiteratureBasedUrbanStudy/
```

Queste cartelle sono locali, ignored e non sono sorgente. Non cancellarle
durante una fase documentale.

## Profili

Density:

| Profilo | Uso |
| --- | --- |
| `low_density` | Variante controllata a densita ridotta. |
| `nominal` | Profilo calibrato sulla sottorete `candidate_0045`. |
| `high_density` | Stress profile documentato, non baseline stabile. |

Duration:

| Profilo | Durata | Uso |
| --- | ---: | --- |
| `smoke` | 180 s | Controllo tecnico end-to-end. |
| `nominal` | 300 s | Test operativo ordinario. |
| `extended` | 600 s | Verifica piu' lunga. |

Nota: `nominal-smoke-seed-104729` e' lo scenario attualmente deployato nel
runtime MOSAIC locale. `nominal-extended-seed-104729` puo' essere l'ultimo
scenario materializzato per timestamp, ma non e' automaticamente lo scenario
deployato corrente.

## Prerequisiti locali

```text
Java 21
PowerShell
Python launcher: py
SUMO_HOME
sumo
netconvert
sumolib
tmp/mosaic-25.2
tmp/external-tools/scenario-convert-25.2
external InTAS checkout
```

Default external InTAS path:

```text
C:\Users\raffa\IdeaProjects\external\InTAS
```

`tmp/mosaic-25.2` contiene il runtime locale MOSAIC e non va trattato come
output usa-e-getta.

## Struttura del tool

```text
tools/intas-literature-scenario/
|-- README.md
|-- build_intas_literature_scenario.py
|-- materialize_literature_scenario.ps1
|-- deploy_materialized_literature_scenario.ps1
|-- run_literature_scenario.ps1
|-- quick_literature_workflow.ps1
|-- show_latest_literature_report.ps1
|-- validate_intas_source.py
|-- validate_literature_configuration.py
|-- validate_materialized_literature_scenario.py
|-- validate_literature_smoke_run.ps1
`-- config/
    |-- candidate_0045_edge_ids.txt
    |-- literature_calibration_catalog.json
    |-- literature_scenario_targets.json
    |-- reproducibility_seeds.json
    `-- synthetic_mobility_profile.json
```

## Comandi principali

Workflow completo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\quick_literature_workflow.ps1 `
  -Density nominal `
  -DurationProfile smoke `
  -Seed 104729 `
  -ForceRebuild `
  -PrintDetailedLiveReport `
  -PrintSummary
```

Solo materializzazione:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\materialize_literature_scenario.ps1 `
  -Density nominal `
  -DurationProfile smoke `
  -Seed 104729 `
  -ForceRebuild
```

Run di una variante gia' materializzata:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\run_literature_scenario.ps1 `
  -MaterializedScenarioRoot .\tmp\materialized-literature-scenarios\MaGaLiteratureBasedUrbanStudy\nominal-smoke-seed-104729 `
  -PrintDetailedLiveReport
```

Mostrare l'ultimo report:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\show_latest_literature_report.ps1 `
  -PrintSummary
```

## Materializzazione

La materializzazione:

1. valida il checkout InTAS e gli strumenti SUMO;
2. estrae `candidate_0045` con `netconvert`;
3. controlla 155 edge esterni, 88 junction e 8 junction semaforizzate;
4. calcola route valide sulla rete ridotta;
5. raggruppa le route per copertura RSU;
6. genera traffico passenger deterministico;
7. esegue una validazione SUMO;
8. scrive configurazioni MOSAIC/SUMO concrete;
9. crea il database SQLite con Scenario-Convert;
10. importa le route nel database;
11. scrive manifest e report di validazione.

## Esecuzione frequente

La run non ricostruisce la sottorete InTAS. Usa una variante gia'
materializzata, builda il runtime live, deploya nel runtime MOSAIC locale,
avvia `mosaic.bat`, produce summary e valida lo smoke/live report.

Il deploy finale si appoggia anche al JAR diagnostico ad-hoc radio generato
sotto `tools/mosaic-adhoc-radio-diagnostic/out/`. La riconciliazione dei
default di build di quel tool e' documentata nel relativo README e va trattata
in una fase successiva, non in questa.

## Warning noto

La rete InTAS estratta puo' emettere warning su `tlLogic` unsafe green phase.
Il warning e' documentato e non blocca la materializzazione. Restano bloccanti:
errori SUMO, teleport, emergency braking e route invalide.
