# Replay Bootstrap Validation

Harness esterno per la Fase 10J-pre. Il tool valida due correzioni mirate:

```text
snapshot vuoto con tasks=[] e candidateNodes=[]
    -> EMPTY_TASK_SET senza avviare il GA

replay JSON_TIME
    -> parte dal primo timestamp JSON disponibile
```

Il tool non implementa la Fase 10J completa, non modifica gli snapshot e non
esegue replay end-to-end su tutti gli snapshot.

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\replay-bootstrap-validation\build.ps1
```

## Run

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\replay-bootstrap-validation\run.ps1
```

Output diagnostico:

```text
data/mosaic-study/diagnostics/phase_10j_pre_replay_bootstrap_validation.json
```

## Casi validati

```text
A: optimizer con snapshot vuoto -> EMPTY_TASK_SET
B: task non vuoto senza candidati -> IllegalArgumentException
C: JSON_TIME richiesta 0 s prima dello snapshot 5 s -> Optional.empty()
D: JSON_TIME richiesta 5 s -> snapshot 5 s exactTimeMatch=true
E: JSON_SEQUENCE preserva la finestra vuota iniziale
F: smoke JSON_SEQUENCE con maxSteps=2
G: smoke JSON_TIME con maxSteps=2
```
