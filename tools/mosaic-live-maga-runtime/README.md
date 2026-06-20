# MOSAIC live MA-GA runtime

> Nota post-audit 2026-06-14: questo tool e' ancora un componente attivo della
> pipeline finale `MaGaLiteratureBasedUrbanStudy`, perche' produce il JAR del
> runtime bridge, del coordinatore asincrono MA-GA e del reporting live. Le
> sezioni storiche sotto restano utili per capire le Fasi 13D-13E e lo scenario
> diagnostico `MaGaLiveMagaRuntimeStudy`, ma quello scenario non e' lo scenario
> finale literature-based. Nella pipeline finale il deploy e la run ordinaria
> passano da `tools/intas-literature-scenario/`.

Tool diagnostico versionabile per le Fasi 13D-13E. Collega il layer live 13C al
core MA-GA esistente usando un `MosaicSnapshotBridge` concreto, la
`MosaicSystemStateSource` invariata, `TemporalWindowManager.executeNextStepOrNull`
e un worker single-thread.

Non implementa esecuzione reale dei task, migrazione live, checkpoint,
strategy control operativo o scenario finale realistico. Lo strategy applier e'
diagnostico.

## Scenario

Contesto attuale: lo scenario finale e' `MaGaLiteratureBasedUrbanStudy`.
Lo scenario indicato qui sotto e' il predecessore diagnostico usato per
validare il bridge live prima della materializzazione literature-based.

Scenario versionabile:

```text
data/mosaic-scenarios/MaGaLiveMagaRuntimeStudy
```

Deriva da `MaGaLiveInfrastructureSnapshotStudy`, mantiene SUMO, SNS, Cell,
server coordinator, veicoli, RSU e accounting Cell diagnostico controllato. Il
coordinator app e' sostituito con:

```text
org.eclipse.mosaic.app.maga.liveruntime.MaGaLiveRuntimeCoordinatorApp
```

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\build.ps1
```

Il build compila:

```text
src/
tools/mosaic-live-state-layer/src/
tools/mosaic-live-maga-runtime/src/
```

e crea:

```text
tools/mosaic-live-maga-runtime/out/maga-live-maga-runtime.jar
```

Il JAR resta un artefatto generato e non viene piu' copiato nello scenario
versionabile.

## Deploy

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\deploy.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Il deploy sostituisce solo:

```text
tmp/mosaic-25.2/scenarios/MaGaLiveMagaRuntimeStudy/
```

Dopo la copia dello scenario versionabile, il deploy inietta il JAR generato in:

```text
tmp/mosaic-25.2/scenarios/MaGaLiveMagaRuntimeStudy/application/maga-live-maga-runtime.jar
```

## Run normale

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -Profile "normal"
```

## Run overrun diagnostico

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -Profile "diagnostic-overrun"
```

Il profilo overrun copia nel solo scenario deployato una configurazione con
`diagnosticArtificialGaDelayMs > 0`. Il ritardo e' marcato come diagnostico e
serve esclusivamente a osservare `WAIT_CAP_REACHED`,
`STALE_RESULT_DISCARDED` e `FRESH_REOPTIMIZATION_REQUESTED`.

## Validate

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\validate.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Il validator legge la run normale e la run overrun piu' recenti e genera:

```text
data/mosaic-study/diagnostics/phase_13e_live_bridge_end_to_end_validation.json
```

La validazione finale controlla:

- bridge `LIVE_CAUSAL_MOSAIC_SNAPSHOT_BRIDGE`;
- uso di `MosaicSystemStateSource`;
- assenza di GA concorrenti;
- `WAIT_CAP_REACHED` rilevato prima del completamento del worker;
- stale result mai applicato;
- fresh reoptimization richiesta;
- `futurePoolViolations` separato da `invalidPoolBandwidthViolations`;
- copie JSON degli snapshot pubblicati caricate con `JsonSnapshotFolderLoader`;
- `SnapshotValidator` e `LocalCandidateInvariantValidator`;
- parity tra snapshot pubblicato in memoria e copia JSON diagnostica.

## Summary singola run

Dopo una run normale il runner root richiama:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\summarize-run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -ScenarioName "MaGaLiveMagaRuntimeStudy" `
  -RunName "<run>"
```

Output:

```text
tmp/mosaic-25.2/logs/<run>/live-maga-runtime/live_run_summary.json
tmp/mosaic-25.2/logs/<run>/live-maga-runtime/live_run_summary.md
```

Se il runtime ha prodotto il reporting nativo dettagliato, il summarizer lo
indicizza nel summary e puo' stamparne il TXT:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\summarize-run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -ScenarioName "MaGaLiveMagaRuntimeStudy" `
  -RunName "<run>" `
  -PrintDetailedLiveReport
```

Il summarizer non riesegue MA-GA e non ricostruisce il report. Stampa soltanto
il file TXT gia' prodotto dal runtime Java durante la vera simulazione live.

## Native live detailed reporting

Il reporting nativo live e' opzionale e retrocompatibile. Nei runtime config
puo' essere abilitato con:

```json
{
  "nativeLiveDetailedReportingEnabled": true,
  "nativeLiveDetailedReportPrintToConsole": false
}
```

Quando abilitato, `MaGaLiveRuntimeCoordinatorApp` crea un collector durante la
run e registra incrementalmente ogni job GA reale:

```text
SUBMITTED
WAIT_CAP_REACHED
COMPLETED_WITHIN_BOUND
APPLIED
STALE_DISCARDED
FAILED
NULL_STEP_RESULT
SHUTDOWN_IN_FLIGHT
```

Il collector conserva i `TemporalStepResult` realmente restituiti da
`TemporalWindowManager.executeNextStepOrNull(...)`. Non richiama di nuovo il
manager, non richiama `MaGaOptimizer` e non usa replay offline degli snapshot.

Artifact locali:

```text
tmp/mosaic-25.2/logs/<run>/live-maga-runtime/live-reporting/
|-- live_ga_job_events.jsonl
|-- live_temporal_step_records.jsonl
|-- live_applied_window_records.csv
|-- live_discarded_window_records.csv
|-- live_detailed_execution_report.txt
|-- live_detailed_execution_report.md
`-- live_detailed_execution_report.json
```

Il TXT aggrega gli step APPLIED con `TemporalWindowResult(appliedSteps)` e
riusa i printer storici compatibili. Le sezioni operative live-specific
coprono wall-clock reale, overrun, stale discard, fresh reoptimization,
snapshot audit e distinzione tra profilo Cell configurato e accounting runtime
diagnostico.

## Runner root

Comando ordinario:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\run_maga_live.ps1
```

Validazione completa bridge:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\run_maga_live.ps1 `
  -Mode "validate-bridge"
```

## Output locali

Le trace runtime sono scritte in:

```text
tmp/mosaic-25.2/logs/<run>/live-maga-runtime/
```

File:

```text
live_ga_runtime_trace.csv
live_strategy_application_trace.csv
live_bridge_snapshot_trace.csv
live_overrun_trace.csv
live_published_snapshot_manifest.csv
published-snapshots/
```

Questi file sono derivati e non vanno versionati.

## Policy

- Bridge: `LIVE_CAUSAL_MOSAIC_SNAPSHOT_BRIDGE`.
- Source mode: `MOSAIC_LIVE` tramite `MosaicSystemStateSource`.
- Worker: single-thread, nessuna chiamata MOSAIC dal worker.
- Snapshot: costruito prima della submission e letto dal bridge in memoria.
- Deadline overrun: `DeltaT_max` calcolato prima della submission tramite
  `TemporalWindowBoundsCalculator.compute(...)`.
- Parallel GA: `SINGLE_IN_FLIGHT_GA_ONLY`.
- Strategia durante GA: `KEEP_LAST_APPLIED_STRATEGY`.
- Late result: `DISCARD_RESULT_IF_COMPLETED_AFTER_DELTA_T_MAX`.
- Recovery: `REQUEST_FRESH_SNAPSHOT_AND_REOPTIMIZE_AT_FIRST_SAFE_INSTANT`.
- Snapshot copies: solo diagnostiche post-run, mai lette durante la simulazione.

## Limiti

La strategy application e' solo diagnostica. La banda Cell resta accounting
diagnostico runtime da messaggi controllati, non misura diretta del federate
Cell. CPU diagnostica e banda V2V richiedono calibrazione futura. Task
execution reale, live migration, checkpoint e ripresa upload/download restano
fuori scope.
