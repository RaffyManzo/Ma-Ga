# Fase 13E - validazione end-to-end finale bridge live

> Nota post-audit 2026-06-14: questa validazione resta importante per il bridge
> live, la causalita e la policy sugli overrun. Tuttavia precede lo scenario
> finale `MaGaLiteratureBasedUrbanStudy`; lo scenario `MaGaLiveMagaRuntimeStudy`
> citato qui va letto come scenario diagnostico precedente.

## 1. Obiettivo

La Fase 13E stabilizza il bridge live MOSAIC -> `SystemSnapshot` -> MA-GA
introdotto nella Fase 13D. Non aggiunge nuove funzionalita' scientifiche:
corregge due semantiche circoscritte e valida il flusso end-to-end con run
normale e run diagnostica overrun.

Flusso validato:

```text
MOSAIC runtime
  -> cache causale live
  -> assembler SystemSnapshot in memoria
  -> MaGaLiveMosaicSnapshotBridge
  -> MosaicSystemStateSource
  -> TemporalWindowManager invariato
  -> MaGaOptimizer invariato
  -> strategy applier diagnostico
```

## 2. Stato iniziale

La Fase 13D era completata con:

```text
phase13dStatus = COMPLETED
readyForPhase13E = true
bridgeDescription = LIVE_CAUSAL_MOSAIC_SNAPSHOT_BRIDGE
```

Il core MA-GA e il `TemporalWindowManager` restano invariati.

## 3. Correzione timeout

Nella 13D il timeout veniva riconosciuto solo dopo la conclusione del worker GA.
La 13E sposta la rilevazione del cap dentro il polling dello stato
`GA_RUNNING`: se il wall clock supera il deadline del job mentre il worker e'
ancora attivo, il runtime registra `WAIT_CAP_REACHED`, conserva la strategia
precedente e continua ad attendere la conclusione del worker senza avviare un
secondo GA.

## 4. Calcolo DeltaT_max pre-submission

`DeltaT_max` non e' hardcoded. Il tool runtime usa l'adapter esterno
`LiveGaOverrunDeadlinePolicy`, che richiama il core:

```text
TemporalWindowBoundsCalculator.compute(snapshot, metrics, fallbackWindowSeconds)
```

L'adapter usa la stessa `TemporalWindowConfig` del manager, il
`CoverageReferenceCalculator`, lo snapshot causale sottoposto al GA e le
metriche operative coerenti con lo stato della finestra. Il fallback e'
`currentWindowDurationSeconds`.

## 5. Rilevazione cap durante GA_RUNNING

Ogni tick del coordinator verifica:

```text
GA_RUNNING
AND wallClockNow >= wallClockDeadlineNs
AND timeout non ancora registrato
```

Quando la guard condition e' vera:

```text
runtimeState = WAIT_CAP_REACHED
timeoutDetectedBeforeCompletion = true
lastAppliedStrategy resta attiva
nessun GA concorrente viene avviato
```

La diagnostica 13E conferma:

```text
waitCapDetectedBeforeCompletion = true
parallelGaViolations = 0
```

## 6. Stale result discard

Quando il worker termina dopo un cap gia' rilevato, il risultato e' marcato
stale e viene scartato:

```text
STALE_RESULT_DISCARDED
resultApplied = false
```

La validazione confronta gli snapshotId scartati e gli snapshotId applicati
all'interno dello stesso profilo di run, evitando falsi positivi tra run
diverse.

## 7. Fresh reoptimization

Dopo lo scarto di un risultato tardivo il runtime registra:

```text
FRESH_REOPTIMIZATION_REQUESTED
```

Il nuovo GA parte solo quando il worker precedente e' libero e puo' usare uno
snapshot fresco disponibile dal coordinator.

## 8. Causalita' pool

La 13E separa due concetti prima confusi:

```text
futurePoolViolations
    poolAvailableFromTime > snapshotTime

invalidPoolBandwidthViolations
    availableBandwidth <= 0
```

Il core `BandwidthPoolSnapshot` non viene modificato. I metadati causali vivono
nel facade runtime esterno tramite `LivePublishedSnapshotAudit`, che accompagna
lo snapshot prima della pubblicazione nel bridge.

## 9. Distinzione invalid bandwidth / look-ahead

`futurePoolViolations` ora misura solo look-ahead temporale. La banda non valida
usa `invalidPoolBandwidthViolations`; in condizioni normali il costruttore core
continua comunque a rifiutare pool con banda non positiva.

Risultato 13E:

```text
futurePoolViolations = 0
invalidPoolBandwidthViolations = 0
```

## 10. Snapshot parity

Il bridge salva copie diagnostiche locali degli snapshot effettivamente
pubblicati sotto:

```text
tmp/mosaic-25.2/logs/<run>/live-maga-runtime/published-snapshots/
```

Questi JSON non sono letti durante la simulazione. Dopo la run il validator:

- carica le copie con `JsonSnapshotFolderLoader`;
- valida con `SnapshotValidator` e `LocalCandidateInvariantValidator`;
- confronta `snapshotId`, timestamp, candidateId, poolId, access link, task e
  conteggi `LOCAL`, `VEHICLE`, `EDGE`, `CLOUD` con la manifest prodotta dal
  bridge.

Risultato:

```text
publishedSnapshotCopies = 64
javaLoaderValidationFailures = 0
javaValidatorFailures = 0
snapshotParityMismatches = 0
```

## 11. Run normale

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -Profile "normal"
```

Run validata:

```text
log-20260606-105353-MaGaLiveMagaRuntimeStudy
```

## 12. Run overrun

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-maga-runtime\run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -Profile "diagnostic-overrun"
```

Run validata:

```text
log-20260606-105409-MaGaLiveMagaRuntimeStudy
```

Il profilo usa `DIAGNOSTIC_ARTIFICIAL_GA_DELAY` solo per osservare
deterministicamente la policy overrun.

## 13. Validazione loader

Le copie pubblicate sono serializzate nel contratto JSON atteso dal loader
ufficiale. In particolare i candidati usano `baseLatencySeconds`, non il nome
interno del modello `propagationDelaySeconds`.

Risultato:

```text
javaLoaderValidationFailures = 0
```

## 14. Validazione validator

Tutte le copie pubblicate superano `SnapshotValidator` e
`LocalCandidateInvariantValidator`.

Risultato:

```text
javaValidatorFailures = 0
```

## 15. Risultati

Diagnostica finale:

```text
data/mosaic-study/diagnostics/phase_13e_live_bridge_end_to_end_validation.json
```

Risultati principali:

```text
phase13eStatus = COMPLETED
phase13Status = COMPLETED
readyForCalibration = true
snapshotsResolved = 1122
gaJobsSubmitted = 121
gaJobsCompleted = 121
gaJobsApplied = 117
gaJobsDiscardedAsStale = 4
parallelGaViolations = 0
futureSnapshotViolations = 0
futurePoolViolations = 0
invalidPoolBandwidthViolations = 0
deltaTMaxMismatchViolations = 0
```

## 16. Warning residui

Restano registrati:

```text
WARNING_STRATEGY_APPLICATION_IS_DIAGNOSTIC_NOT_TASK_EXECUTION
WARNING_CELL_BANDWIDTH_IS_DIAGNOSTIC_RUNTIME_ACCOUNTING_NOT_FEDERATE_MEASUREMENT
WARNING_REUSED_STRATEGY_MAY_BECOME_STALE_DURING_GA_OVERRUN
WARNING_DIAGNOSTIC_ARTIFICIAL_GA_DELAY_USED_ONLY_FOR_OVERRUN_TEST
WARNING_DIAGNOSTIC_CPU_AND_V2V_BANDWIDTH_REQUIRE_FUTURE_CALIBRATION
WARNING_SCENARIO_NOT_YET_CALIBRATED_FOR_NON_LOCAL_OFFLOADING
```

## 17. Limiti

La Fase 13E non implementa:

- esecuzione reale dei task;
- live migration;
- checkpoint;
- ripresa upload/download;
- cicli CPU residui;
- persistenza remota;
- calibrazione scientifica;
- scenario realistico finale.

La strategy application resta diagnostica. La banda Cell resta accounting
diagnostico runtime da messaggi controllati, non misura diretta del federate
Cell.

## 18. Conclusione Fase 13

La Fase 13 e' completata: il bridge live MOSAIC -> `SystemSnapshot` -> MA-GA
funziona end-to-end in scenario isolato, con manager e optimizer invariati,
single-in-flight GA, overrun policy verificata e snapshot pubblicati validati
dal loader Java.

## 19. Readiness calibrazione

Il sistema e' pronto per la calibrazione perche' il plumbing live e' stabile e
validato. La calibrazione dovra' concentrarsi sui parametri scientifici:
capacita' Cell non diagnostica, CPU, banda V2V, workload, profili task e
scenario non-local offloading.
