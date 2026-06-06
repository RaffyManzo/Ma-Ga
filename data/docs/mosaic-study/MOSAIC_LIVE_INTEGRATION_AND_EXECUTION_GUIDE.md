# MOSAIC Live Integration and Execution Guide

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
