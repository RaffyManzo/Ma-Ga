# MOSAIC Live Integration and Execution Guide

> Nota post-audit 2026-06-14: questo documento resta utile come guida storica
> dell'integrazione live fino alle Fasi 13D-13E. Non e' pero' il punto di
> ingresso finale dello scenario literature-based. Per lo stato operativo
> corrente usare `README.md`, `tools/intas-literature-scenario/README.md` e
> `data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/README.md`.

## Indice documentale post-audit

| Area | Documenti | Stato |
| --- | --- | --- |
| Pipeline finale literature-based | `14C4_1_persistent_materialization_and_literature_smoke_test.md`, `14C4_2_synthetic_calibrated_intas_subscenario.md` | Piu' vicini allo stato operativo finale. |
| Configurazione radio/Cell/compute e workload | `14C2_literature_radio_cell_compute_configuration.md`, `14C3_live_state_workload_and_cell_profile_extension.md`, `14C3R_native_live_detailed_reporting.md` | Ancora utili come base tecnica della pipeline finale. |
| Bridge live precedente | `13B_*`, `13C_*`, `13D_*`, `13E_*` | Storico tecnico: valida i componenti poi riusati nella pipeline finale. |
| Pipeline offline/replay | `10_punto_10_sviluppo_completo.md`, `11_offline_pipeline_end_to_end_consolidation.md` | Regressione standalone, non percorso ordinario live finale. |
| Guide iniziali e Barnim | `01_*` ... `09_*` | Storiche, utili per capire l'evoluzione. |

Distinzione corrente delle cartelle:

```text
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/     = template versionato
tmp/materialized-literature-scenarios/                    = scenari concreti generati
tmp/mosaic-25.2/scenarios/MaGaLiteratureBasedUrbanStudy/  = scenario deployato locale
tmp/mosaic-25.2/logs/                                     = evidenza locale delle run
data/snapshots/                                           = replay/regressione standalone
src/                                                      = core MA-GA da non toccare
```

## 1. Stato finale

Il repository e' pronto per la fase pre-calibrazione. Il bridge live
MOSAIC -> `SystemSnapshot` -> `TemporalWindowManager` -> MA-GA e' operativo in
scenario isolato e la Fase 13E ha validato causalita', overrun policy,
snapshot parity, loader Java e `SnapshotValidator`.

## 2. Architettura

```text
MOSAIC runtime
  -> vehicle callbacks e Cell diagnostic accounting
  -> LiveStateLayerRuntimeFacade
  -> LiveSystemSnapshotAssembler
  -> MaGaLiveMosaicSnapshotBridge
  -> MosaicSystemStateSource
  -> TemporalWindowManager
  -> MaGaOptimizer
  -> LiveStrategyApplier diagnostico
```

## 3. Offline vs live

La pipeline offline storica produceva CSV e JSON da run MOSAIC gia'
registrate. Il runtime live costruisce snapshot causali in memoria durante la
simulazione e li pubblica al bridge senza leggere log o JSON runtime da disco.
Le copie JSON pubblicate sono solo evidenza diagnostica post-run.

## 4. Ruolo MOSAIC

MOSAIC ospita lo scenario `MaGaLiveMagaRuntimeStudy`, le vehicle app, la radio
ad-hoc diagnostica, il federate Cell e il server coordinator. Il coordinator
programma tick periodici e non invia traffico V2X artificiale.

## 5. Cache live

Il live state layer mantiene cache causali per veicoli, task diagnostici,
infrastruttura statica, Cell diagnostic accounting, candidati `LOCAL`, V2V,
`EDGE`, `CLOUD`, pool `DIRECT_V2V` e pool `GATEWAY`.

## 6. Bridge

`MaGaLiveMosaicSnapshotBridge` implementa il contratto
`MosaicSnapshotBridge`. Restituisce snapshot causali disponibili per il tempo
richiesto e conserva audit esterni per verificare `availableFromTime <= t` sui
pool.

## 7. TemporalWindowManager

Il `TemporalWindowManager` core resta invariato. Il runtime lo invoca con
`executeNextStepOrNull(state)` dentro un worker single-thread, usando snapshot
immutabili gia' pubblicati.

## 8. MA-GA

`MaGaOptimizer` resta invariato. La strategy application e' diagnostica: registra
assegnazioni e fitness ma non esegue task reali.

## 9. Cartelle attive

```text
src/
tools/mosaic-live-maga-runtime/
tools/mosaic-live-state-layer/
tools/mosaic-adhoc-radio-diagnostic/
data/mosaic-scenarios/MaGaLiveMagaRuntimeStudy/
data/docs/
data/mosaic-study/diagnostics/
```

## 10. Archive

`archive/` contiene scenari storici, tool storici, tool regressivi e artefatti
offline rigenerabili. Non e' richiesto dal runtime live ordinario.

## 11. Prerequisiti

- MOSAIC 25.2 in `tmp/mosaic-25.2/`;
- JDK disponibile in `PATH`;
- PowerShell;
- branch `MOSAIC/SUMO-integration`.

## 12. Avvio a comando unico

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\run_maga_live.ps1
```

Il comando compila, deploya, avvia MOSAIC, individua la nuova run e genera:

```text
tmp/mosaic-25.2/logs/<run>/live-maga-runtime/live_run_summary.json
tmp/mosaic-25.2/logs/<run>/live-maga-runtime/live_run_summary.md
```

## 13. Validazione bridge

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\run_maga_live.ps1 `
  -Mode "validate-bridge"
```

Esegue run normale, run `diagnostic-overrun` e validator 13E.

## 14. Report prodotti

Directory:

```text
tmp/mosaic-25.2/logs/<run>/live-maga-runtime/
```

File principali:

```text
live_ga_runtime_trace.csv
live_strategy_application_trace.csv
live_bridge_snapshot_trace.csv
live_overrun_trace.csv
live_published_snapshot_manifest.csv
live_run_summary.json
live_run_summary.md
published-snapshots/
```

## 15. Lettura CSV

- `live_ga_runtime_trace.csv`: stato del coordinator, submission, completion,
  `DeltaT_max`, timeout e stale discard.
- `live_strategy_application_trace.csv`: strategia diagnostica applicata.
- `live_bridge_snapshot_trace.csv`: snapshot richiesti/risolti e audit pool.
- `live_overrun_trace.csv`: transizioni `WAIT_CAP_REACHED`,
  `STALE_RESULT_DISCARDED`, `FRESH_REOPTIMIZATION_REQUESTED`.

## 16. Limiti correnti

- Strategy application diagnostica, non task execution reale.
- Banda Cell diagnostica da accounting runtime controllato, non misura diretta
  del federate Cell.
- Nessuna live migration, checkpoint, ripresa upload/download o persistenza
  remota.
- Scenario non ancora calibrato per offloading non locale.

## 17. Calibrazione futura

La calibrazione dovra' introdurre mapping autorevoli per:

```text
ConfigurationId x Seed x Replicate
```

La futura campagna usera' una matrice configurazioni x 5 repliche. I parametri
`-ConfigurationId`, `-Seed` e `-Replicate` sono gia' riservati nel runner root
ma generano errore esplicito finche' la calibrazione non definisce mapping
autorevoli.

## 18. Troubleshooting

- Se il build fallisce, verificare JDK e presenza dei JAR MOSAIC sotto
  `tmp/mosaic-25.2/lib/`.
- Se il deploy fallisce, eseguire prima il build: il JAR runtime deve esistere
  in `tools/mosaic-live-maga-runtime/out/`.
- Se il runner non trova una run, controllare che MOSAIC abbia scritto in
  `tmp/mosaic-25.2/logs/`.
- Se `validate-bridge` fallisce, leggere
  `data/mosaic-study/diagnostics/phase_13e_live_bridge_end_to_end_validation.json`.

## 19. Comandi finali

Avvio ordinario:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\run_maga_live.ps1
```

Validazione completa:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\run_maga_live.ps1 `
  -Mode "validate-bridge"
```

## Quick workflow for the synthetic-calibrated InTAS scenario

The final literature scenario uses a real reduced InTAS topology and deterministic synthetic SUMO traffic. The local MOSAIC installation under `tmp\mosaic-25.2` must remain available.

Run materialization, MOSAIC execution and report lookup with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\quick_literature_workflow.ps1 `
  -ForceRebuild `
  -PrintDetailedLiveReport `
  -PrintSummary
```

Show only the latest reports with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\show_latest_literature_report.ps1 `
  -PrintSummary
```

The design and calibration evidence are documented in `14C4_2_synthetic_calibrated_intas_subscenario.md`.
