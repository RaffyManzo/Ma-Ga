# MA-GA - integrazione live Eclipse MOSAIC / SUMO

Questo repository contiene il prototipo Java del Mobility-Aware Genetic
Algorithm (MA-GA) per il computation offloading in un Vehicular Edge-to-Cloud
Continuum.

L'idea centrale e' semplice:

```text
SUMO genera la mobilita.
Eclipse MOSAIC orchestra la simulazione.
Il live-state layer ricostruisce lo stato causale.
Il runtime bridge lo trasforma in SystemSnapshot.
Il core MA-GA ottimizza la strategia di offloading.
Il reporting rende la run verificabile.
```

Il core MA-GA lavora sempre sul contratto `SystemSnapshot`: non dipende
direttamente da MOSAIC o da SUMO.

## Stato finale

Branch di integrazione:

```text
MOSAIC/SUMO-integration
```

Commit congelato:

```text
5a9477735a3d707a5f000a64653cd2a6fc7f2007
```

Lo scenario finale testato localmente e':

```text
MaGaLiteratureBasedUrbanStudy
```

La sottorete urbana congelata e':

```text
candidate_0045
```

Valori strutturali dello scenario:

```text
external edges                  = 155
external junctions              = 88
physical traffic-light junctions = 8
tlLogic definitions             = 7
RSU                              = 2
RSU coverage radius              = 250 m
```

## Cartelle e responsabilita

| Percorso | Ruolo |
| --- | --- |
| `src/` | Core MA-GA standalone. Non toccare durante riallineamenti documentali o cleanup. |
| `data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/` | Template/scaffold versionato dello scenario finale. Non contiene lo scenario concreto gia' eseguito. |
| `tools/intas-literature-scenario/` | Tool principale per materializzare, deployare, eseguire, riassumere e validare lo scenario literature-based. |
| `tools/mosaic-live-state-layer/` | Sorgenti del layer che ricostruisce stato live, workload, candidati e snapshot. |
| `tools/mosaic-live-maga-runtime/` | Sorgenti del runtime bridge, coordinatore asincrono MA-GA e reporting live. |
| `tools/mosaic-adhoc-radio-diagnostic/` | App MOSAIC diagnostica richiesta dai veicoli per abilitare la radio ad-hoc. |
| `tmp/materialized-literature-scenarios/` | Scenari concreti generati dal materializer. Output locale ignored, non sorgente. |
| `tmp/mosaic-25.2/scenarios/MaGaLiteratureBasedUrbanStudy/` | Copia deployata nel runtime MOSAIC locale. Output locale ignored. |
| `tmp/mosaic-25.2/logs/` | Evidenza locale delle run MOSAIC/SUMO. Non e' sorgente. |
| `tmp/external-tools/` | Dipendenze locali, incluso Scenario-Convert. Non toccare senza una fase dedicata. |
| `data/snapshots/` | Snapshot standalone/regressivi per replay del core. Non sono la sorgente della pipeline live finale. |
| `archive/` | Materiale storico, freeze manifest e prove di cleanup. |

## Template, materializzazione, deploy e run

La distinzione importante e':

```text
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/
  -> template versionati

tmp/materialized-literature-scenarios/MaGaLiteratureBasedUrbanStudy/<profile>/
  -> scenario concreto generato dal materializer

tmp/mosaic-25.2/scenarios/MaGaLiteratureBasedUrbanStudy/
  -> scenario concreto copiato dentro MOSAIC locale per la run

tmp/mosaic-25.2/logs/log-<timestamp>-MaGaLiteratureBasedUrbanStudy/
  -> evidenza locale della singola esecuzione
```

Quindi `data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/` non deve essere
letto come scenario gia' eseguibile: e' lo scaffold da cui il materializer
produce i file concreti.

## Profili disponibili

Profili di densita:

| Profilo | Uso |
| --- | --- |
| `low_density` | Variante controllata a densita ridotta. |
| `nominal` | Profilo calibrato sulla sottorete `candidate_0045`. |
| `high_density` | Stress profile documentato, non configurazione operativa stabile. |

Profili di durata:

| Profilo | Durata | Uso |
| --- | ---: | --- |
| `smoke` | 180 s | Controllo tecnico end-to-end. |
| `nominal` | 300 s | Test operativo ordinario. |
| `extended` | 600 s | Verifica piu' lunga della stabilita. |

Nota post-audit: `nominal-smoke-seed-104729` e' lo scenario attualmente
deployato nel runtime MOSAIC locale. `nominal-extended-seed-104729` risulta
l'ultimo scenario materializzato per timestamp, ma non va confuso
automaticamente con lo scenario deployato corrente.

## Workflow principale

Esecuzione completa con materializzazione, deploy, run e report:

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

Esecuzione di uno scenario gia' materializzato:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\run_literature_scenario.ps1 `
  -MaterializedScenarioRoot .\tmp\materialized-literature-scenarios\MaGaLiteratureBasedUrbanStudy\nominal-smoke-seed-104729 `
  -PrintDetailedLiveReport
```

Visualizzazione dell'ultimo report locale:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\show_latest_literature_report.ps1 `
  -PrintSummary
```

## Output di run

Ogni run crea una directory locale:

```text
tmp/mosaic-25.2/logs/log-<timestamp>-MaGaLiteratureBasedUrbanStudy/
```

File principali:

```text
live-maga-runtime/live_run_summary.json
live-maga-runtime/live_run_summary.md
live-maga-runtime/literature_smoke_validation.json
live-maga-runtime/literature_smoke_validation.md
live-maga-runtime/live-reporting/live_detailed_execution_report.md
live-maga-runtime/live-reporting/live_detailed_execution_report.json
```

I log locali sono evidenza dei test, non sorgenti da editare a mano.

## Limiti noti

- `taskCompletionModel = NOT_IMPLEMENTED`: un task rimosso alla deadline non
  equivale a un task completato con successo.
- `LiveStrategyApplier` registra la strategia applicata, ma non controlla una
  reale esecuzione distribuita del task.
- La banda Cell live deriva da accounting diagnostico controllato, non da una
  misura scientifica diretta del federate Cell.
- `high_density` serve come stress profile: puo' mostrare overrun o risultati
  stale e non deve essere presentato come baseline operativa stabile.
- I file sotto `tmp/` e `tools/*/out/` sono locali e ignored; non vanno puliti
  senza una fase dedicata.

## Documentazione

Punto di ingresso operativo:

```text
README.md
tools/intas-literature-scenario/README.md
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/README.md
```

Documenti vicini allo stato finale:

```text
data/docs/mosaic-study/14C4_1_persistent_materialization_and_literature_smoke_test.md
data/docs/mosaic-study/14C4_2_synthetic_calibrated_intas_subscenario.md
```

Documenti precedenti utili come storico tecnico:

```text
data/docs/mosaic-study/10_punto_10_sviluppo_completo.md
data/docs/mosaic-study/11_offline_pipeline_end_to_end_consolidation.md
data/docs/mosaic-study/13D_live_maga_runtime_bridge_and_overrun_policy.md
data/docs/mosaic-study/13E_live_bridge_end_to_end_validation.md
data/docs/mosaic-study/MOSAIC_LIVE_INTEGRATION_AND_EXECUTION_GUIDE.md
```

La pipeline offline MOSAIC -> CSV -> `SystemSnapshot` JSON -> replay resta
disponibile come materiale regressivo, ma non e' il percorso ordinario dello
scenario finale.
