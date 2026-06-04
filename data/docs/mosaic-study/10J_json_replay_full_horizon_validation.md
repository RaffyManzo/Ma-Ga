# Fase 10J - Validazione JSON_TIME full horizon

## Scopo

Questa fase chiude il Punto 10 verificando che gli snapshot MOSAIC generati
offline possano essere riprodotti in modalita' `JSON_TIME OBSERVED_RUNTIME`
fino all'orizzonte temporale finale, senza modificare il core MA-GA, gli
snapshot, gli exporter o la timeline.

## Baseline

La baseline canonica e':

```text
log-20260604-220216-MaGaIntegratedStudy
```

La cartella snapshot validata e':

```text
data/snapshots/mosaic-generated/
```

Gli snapshot caricati sono 36. Il primo snapshot e':

```text
mosaic_generated_000_t_005
timeSeconds = 5.0
```

Lo snapshot finale, ricavato automaticamente dai JSON caricati, e':

```text
mosaic_generated_035_t_180
timeSeconds = 180.0
```

## Stato precedente

Le diagnostiche precedenti confermano:

```text
phase10iStatus = COMPLETED
phase10jPreStatus = COMPLETED
phase10jPre2Status = COMPLETED
readyForPhase10J = true
```

`JSON_SEQUENCE CONFIGURED_RUNTIME` era gia' stato validato end-to-end:

```text
exit code = 0
windows = 36
task evaluations = 682
EMPTY_TASK_SET osservato nella prima finestra
```

`JSON_TIME OBSERVED_RUNTIME` era gia' stato validato come smoke test, ma con 36
step non raggiungeva l'orizzonte finale per via delle finestre adattive.

## Limite dei 36 step

In `JSON_TIME`, `maxSteps` non coincide con il numero di file JSON. Il
`TemporalWindowManager` puo' ridurre la durata della finestra e quindi
riutilizzare lo stesso snapshot passato per molti step consecutivi.

Per questo la condizione semantica di successo non e':

```text
steps == snapshotsLoaded
```

ma:

```text
lastObservationTimeSeconds >= finalSnapshotTimeSeconds
AND
lastResolvedSnapshotId == finalSnapshotId
```

## Harness esterno

Il tool creato e':

```text
tools/json-time-full-horizon-validation/
```

Il tool:

```text
- carica gli snapshot con JsonSnapshotFolderLoader;
- costruisce TimeIndexedSnapshotReplaySource;
- applica CandidatePrefilter come AdaptiveWindowMain;
- costruisce TemporalWindowManager con OBSERVED_RUNTIME;
- esegue executeNextStepOrNull(state) passo per passo;
- aggiorna lo stato con TemporalWindowState.afterStep(step);
- registra una trace CSV;
- genera phase_10j_validation.json.
```

Non modifica il core e non esegue bridge live.

## Policy temporali

```text
sourceMode = JSON_TIME
runtimeProfile = OBSERVED_RUNTIME
lookupPolicy = LATEST_SNAPSHOT_AT_OR_BEFORE_REQUESTED_TIME
ordinaryStopPolicy = FULL_TIME_HORIZON_REACHED
safetyStopPolicy = SAFETY_MAX_STEPS_REACHED
```

Il replay parte da:

```text
snapshots.get(0).getTimeSeconds()
```

L'orizzonte finale e' ricavato da:

```text
snapshots.get(snapshots.size() - 1).getTimeSeconds()
```

## Lookup causale

Sono validi:

```text
EXACT_TIMESTAMP_MATCH
PAST_SNAPSHOT_REUSE
SOURCE_SNAPSHOT_ADVANCE
SOURCE_SNAPSHOT_SKIP
```

Uno skip di snapshot intermedi non e' un errore. E' vietato soltanto:

```text
sourceSnapshotTimeSeconds > observationTimeSeconds
```

## Trace CSV

La trace e':

```text
data/mosaic-study/json_time_full_horizon_trace.csv
```

Contiene, per ogni step:

```text
stepIndex
trigger
triggerTimeSeconds
observationTimeSeconds
sourceSnapshotId
sourceSnapshotTimeSeconds
exactTimestampMatch
pastSnapshotReuse
sourceSnapshotAdvanced
sourceSnapshotSkippedCount
futureLookAhead
tasksCount
vehiclesCount
candidateNodesCount
dynamicity
dynamicityLevel
suggestedReusePolicy
appliedReusePolicy
runtimeSeconds
horizonReachedAfterStep
```

## Risultati

Comando eseguito:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\json-time-full-horizon-validation\run.ps1 `
  -SnapshotFolder ".\data\snapshots\mosaic-generated" `
  -SafetyMaxSteps 100000 `
  -TraceOutFile ".\data\mosaic-study\json_time_full_horizon_trace.csv" `
  -ValidationOutFile ".\data\mosaic-study\diagnostics\phase_10j_validation.json"
```

Risultato:

```text
stepsExecuted = 281
stopReason = FULL_TIME_HORIZON_REACHED
lastObservationTimeSeconds = 180.08505951061724
lastSourceSnapshotId = mosaic_generated_035_t_180
lastSourceSnapshotTimeSeconds = 180.0
futureLookAheadViolations = 0
noTemporalStepFailures = 0
safetyGuardrailTriggered = false
```

Metriche temporali:

```text
exactTimestampMatches = 4
pastSnapshotReuses = 245
sourceSnapshotAdvances = 35
sourceSnapshotSkips = 0
distinctSourceSnapshotsObserved = 36
```

Il guardrail scelto e':

```text
SafetyMaxSteps = 100000
```

Serve soltanto a prevenire loop anomali. Non e' stato raggiunto e non e' stato
usato come criterio di successo.

## Warning sperimentali

Restano aperti:

```text
WARNING_DIAGNOSTIC_BASELINE_NOT_STRESSING_OFFLOADING
WARNING_ALL_DECISIONS_LOCAL
WARNING_FULL_OFFLOADING_NOT_OBSERVED
```

La baseline corrente valida la struttura offline e il replay, ma non stressa
scientificamente l'offloading:

```text
- tutte le decisioni osservate risultano LOCAL;
- offloadingRatio resta p = 0;
- EDGE, CLOUD e VEHICLE non vengono selezionati;
- CPU e banda non risultano sotto pressione.
```

Non sono stati modificati fitness, repair, mutation, initialization, workload,
CPU sintetica o banda V2V sintetica.

## Chiusura Punto 10

Il Punto 10 puo' essere chiuso perche':

```text
- 10A-10H hanno prodotto gli stream offline;
- 10I ha prodotto SystemSnapshot JSON finali validati;
- 10J-pre ha allineato bootstrap e finestra vuota;
- 10J-pre2 ha allineato il reporting al gateway opzionale;
- JSON_SEQUENCE e' validato end-to-end;
- JSON_TIME e' validato fino all'orizzonte temporale finale;
- non sono stati rilevati future look-ahead;
- il guardrail non e' stato attivato;
- la diagnostica finale non contiene errori.
```

## Attivita escluse

Questa fase non implementa:

```text
bridge live MOSAIC
modifiche al core algoritmico
modifiche a TemporalWindowManager
rigenerazione snapshot
ricalibrazione workload o risorse
valutazione scientifica dell'offloading
```

## Prossima fase

Il prossimo lavoro puo' spostarsi fuori dal Punto 10: calibrazione, scenari piu'
stressanti e valutazione scientifica delle decisioni di offloading.
