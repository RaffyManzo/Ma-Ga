# Replay Bootstrap Validation

Harness esterno per le sottofasi di preparazione alla Fase 10J. Il tool valida
il bootstrap del replay e l'allineamento del reporting al contratto con gateway
opzionale.

```text
snapshot vuoto con tasks=[] e candidateNodes=[]
    -> EMPTY_TASK_SET senza avviare il GA

replay JSON_TIME
    -> parte dal primo timestamp JSON disponibile
```

Il tool non implementa la Fase 10J completa, non modifica gli snapshot e non
valida l'intero orizzonte temporale `JSON_TIME`. Per la 10J-pre2 esegue pero'
un replay `JSON_SEQUENCE CONFIGURED_RUNTIME` a 36 step e uno smoke
`JSON_TIME OBSERVED_RUNTIME` a 36 step per verificare che il reporting completi
senza crash.

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
data/mosaic-study/diagnostics/phase_10j_pre2_optional_gateway_reporting_validation.json
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

La versione 10J-pre2 aggiunge inoltre:

```text
A: report uncovered -> uncovered senza eccezioni
B: report covered -> uncovered come COVERAGE_LOSS
C: report uncovered -> covered come COVERAGE_GAIN
D: report gateway_a -> gateway_b come HANDOVER
E: riepilogo CLOUD aggregato della run
F: replay JSON_SEQUENCE CONFIGURED_RUNTIME con 36 step
G: smoke JSON_TIME OBSERVED_RUNTIME con 36 step
```

Nel reporting descrittivo l'assenza di gateway non genera placeholder:

```text
q_v(k) = 0 senza access link attivo
distanza e phiLink mancanti = "-"
COVERAGE_GAIN, COVERAGE_LOSS e HANDOVER restano transizioni distinte
```
