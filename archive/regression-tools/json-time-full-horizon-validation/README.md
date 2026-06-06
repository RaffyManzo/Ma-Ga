# JSON_TIME Full Horizon Validation

Harness esterno per la Fase 10J-final. Il tool valida il replay temporale
`JSON_TIME OBSERVED_RUNTIME` fino all'ultimo timestamp disponibile nella
cartella di snapshot MOSAIC generati, senza modificare il core MA-GA.

Il criterio ordinario di successo e':

```text
FULL_TIME_HORIZON_REACHED
```

definito come:

```text
lastObservationTimeSeconds >= finalSnapshotTimeSeconds
AND
lastResolvedSnapshotId == finalSnapshotId
```

Il parametro `SafetyMaxSteps` e' un guardrail esplicito. Se viene raggiunto,
la validazione fallisce con:

```text
SAFETY_MAX_STEPS_REACHED
```

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\json-time-full-horizon-validation\build.ps1
```

## Run canonico

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\json-time-full-horizon-validation\run.ps1 `
  -SnapshotFolder ".\data\snapshots\mosaic-generated" `
  -SafetyMaxSteps 100000 `
  -TraceOutFile ".\data\mosaic-study\json_time_full_horizon_trace.csv" `
  -ValidationOutFile ".\data\mosaic-study\diagnostics\phase_10j_validation.json"
```

## Output

```text
data/mosaic-study/json_time_full_horizon_trace.csv
data/mosaic-study/diagnostics/phase_10j_validation.json
```

## Policy

```text
sourceMode = JSON_TIME
runtimeProfile = OBSERVED_RUNTIME
lookupPolicy = LATEST_SNAPSHOT_AT_OR_BEFORE_REQUESTED_TIME
ordinaryStopPolicy = FULL_TIME_HORIZON_REACHED
safetyStopPolicy = SAFETY_MAX_STEPS_REACHED
```

Il tool registra exact match, riuso di snapshot passati, avanzamenti della
sorgente, skip di snapshot intermedi e violazioni future look-ahead.
