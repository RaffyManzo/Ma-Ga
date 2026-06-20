# Fase 11 - Consolidamento end-to-end della pipeline offline

> Nota post-audit 2026-06-14: questa fase documenta il consolidamento offline
> MOSAIC -> snapshot JSON -> replay. La pipeline finale usa invece SUMO/MOSAIC
> live, live-state layer, runtime bridge e `SystemSnapshot` costruiti durante la
> simulazione. Conservare questo documento come storico/regressivo.

## Scopo

La Fase 11 consolida in un solo comando la pipeline offline gia' validata nelle
fasi 10A-10J. Il flusso parte da una run MOSAIC esistente, non riesegue MOSAIC,
rigenera gli stream diagnostici, compone i `SystemSnapshot` JSON, valida loader e
validator Java, esegue il replay `JSON_SEQUENCE` e conclude con il replay
`JSON_TIME` fino all'orizzonte finale.

Questa fase non modifica il core algoritmico MA-GA, non modifica gli snapshot a
mano, non cambia parametri scientifici e non implementa il bridge live.

## Punto di partenza

Baseline consolidata:

```text
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/
```

Scenario versionabile:

```text
data/mosaic-scenarios/MaGaIntegratedStudy/
```

Output canonici:

```text
data/mosaic-study/
data/snapshots/mosaic-generated/
data/mosaic-study/diagnostics/
```

## Orchestratore

Script:

```text
tools/mosaic-offline-exporter/run_offline_pipeline.ps1
```

Comando canonico:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-offline-exporter\run_offline_pipeline.ps1 `
  -RepoRoot "." `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -ScenarioName "MaGaIntegratedStudy" `
  -SourceRun "log-20260604-220216-MaGaIntegratedStudy" `
  -SimulationStartSeconds 0 `
  -SimulationEndSeconds 180 `
  -WindowIntervalSeconds 5 `
  -SafetyMaxSteps 100000 `
  -CleanGeneratedOutputs `
  -VerifyDeterminism
```

`RepoRoot` e `MosaicRoot` hanno default operativi. `ScenarioName`,
`SourceRun`, `SimulationStartSeconds`, `SimulationEndSeconds`,
`WindowIntervalSeconds` e `SafetyMaxSteps` restano parametri espliciti.

## Preflight

Lo script verifica:

- branch `MOSAIC/SUMO-integration`;
- disponibilita' di Python, PowerShell, Java e JDK;
- esistenza della run MOSAIC;
- esistenza di `output.csv`, `apps/` e `bandwidthMeasurements/`;
- esistenza dello scenario versionabile;
- esistenza di catalogo risorse, SNS, Cell e rete SUMO;
- esistenza degli exporter e degli harness Java.

Se un input manca, la pipeline si interrompe prima di pulire o rigenerare
artefatti.

## Pulizia sicura

`-CleanGeneratedOutputs` e' richiesto quando gli output canonici esistono gia'.
Senza il flag la pipeline si ferma e stampa l'elenco dei file presenti.

Con il flag vengono eliminati solo file in whitelist:

- stream CSV e JSON generati in `data/mosaic-study/`;
- snapshot JSON in `data/snapshots/mosaic-generated/`;
- diagnostiche rigenerabili 10G-10J e Fase 11;
- log Fase 11.

Non vengono eliminati:

- run MOSAIC sotto `tmp/mosaic-25.2/logs/`;
- scenari versionabili;
- `src/`;
- documentazione;
- cataloghi e configurazioni di scenario.

La cancellazione rifiuta path esterni alla repository e usa retry breve per
gestire il rilascio tardivo di handle su Windows.

## Stage

La pipeline logica contiene 15 stage:

```text
00 preflight
01 10A task_stream
02 10B vehicle_state_stream
03 10C infrastructure_snapshot
04 10D cell streams
05 10E access_link_preview
06 10F remote_candidate_preview
07 10G local/V2V previews
08 10H timeline
09 10H task assignment
10 10I-pre snapshot contract validation
11 10I-pre2 SUMO projection
12 10I SystemSnapshot generation
13 10J-pre / 10J-pre2 bootstrap and reporting validation
14 10J JSON_TIME full horizon validation
```

Gli stage Java di build/run sono registrati come processi fisici separati nei
log; con `-VerifyDeterminism` i processi fisici completati sono 34, perche' gli
stage 01-14 vengono eseguiti due volte.

Ogni stage produce:

```text
data/mosaic-study/diagnostics/phase_11/logs/<pass>_<stage>.stdout.log
data/mosaic-study/diagnostics/phase_11/logs/<pass>_<stage>.stderr.log
```

La pipeline e' fail-fast: qualunque exit code diverso da zero interrompe il
flusso.

## Baseline unica

La Fase 11 controlla che le diagnostiche rigenerate dichiarino la stessa
baseline:

```text
log-20260604-220216-MaGaIntegratedStudy
```

Sono controllati almeno:

- `diagnostics/cell/integrated_baseline_metadata.json`;
- `phase_10g_validation.json`;
- `phase_10h_validation.json`;
- `phase_10i_pre_snapshot_contract_validation.json`;
- `phase_10i_pre2_projection_validation.json`;
- `phase_10i_validation.json`;
- `phase_10j_pre_replay_bootstrap_validation.json`;
- `phase_10j_pre2_optional_gateway_reporting_validation.json`;
- `phase_10j_validation.json`.

## Protezione da output obsoleti

Gli output obbligatori devono esistere, non essere vuoti e risultare rigenerati
dalla pipeline corrente. File residui di run precedenti non sono accettati come
prova di successo.

## Manifest

Output strutturati della Fase 11:

```text
data/mosaic-study/diagnostics/phase_11_offline_pipeline_manifest.json
data/mosaic-study/diagnostics/phase_11_artifact_manifest.csv
data/mosaic-study/diagnostics/phase_11_offline_pipeline_validation.json
```

Il manifest JSON registra parametri, ambiente, stage, log, artefatti, SHA-256,
classificazione deterministica, baseline consistency, warning, errori e
readiness.

Il CSV contiene una riga per artefatto con:

```text
artifactPath, artifactType, stageId, exists, sizeBytes, sha256,
determinismClass, sourceRun
```

## Determinismo

Con `-VerifyDeterminism` la pipeline:

1. esegue una prima rigenerazione completa;
2. calcola hash degli artefatti `DETERMINISTIC_FROM_INPUTS`;
3. pulisce solo la whitelist;
4. esegue una seconda rigenerazione;
5. confronta gli hash deterministici.

Sono runtime-sensitive:

- trace `JSON_TIME`;
- diagnostiche con runtime osservati o timestamp di esecuzione;
- log stdout/stderr;
- manifest Fase 11.

Nel run canonico sono stati confrontati 57 artefatti deterministici e non sono
emersi mismatch.

## Risultati

Run canonico:

```text
phase11Status = COMPLETED
readyForPhase12 = true
pipelineExitCode = 0
baselineConsistencyValidated = true
staleArtifactsDetected = false
deterministicArtifactsCompared = 57
deterministicArtifactMismatches = []
```

Conteggi osservati:

```text
taskCount = 682
vehicleStateCount = 1824
gatewayCount = 2
gatewayPoolCount = 2
executionNodeCount = 3
cellHandoverCount = 48
cellBandwidthRecordCount = 1080
activeAccessLinkPreviewCount = 564
remoteCandidateCount = 1128
localCandidateCount = 1824
v2vCandidateCount = 13206
v2vPoolCount = 6603
optimizationWindowCount = 36
snapshotCount = 36
```

Validazioni:

```text
snapshotLoaderValidationFailures = 0
snapshotValidatorFailures = 0
jsonSequenceValidationStatus = COMPLETED
jsonTimeFullHorizonValidationStatus = COMPLETED
jsonTimeStopReason = FULL_TIME_HORIZON_REACHED
jsonTimeFinalSnapshotReached = true
futureLookAheadViolations = 0
```

## Warning preservati

La Fase 11 registra ma non corregge:

```text
WARNING_DIAGNOSTIC_BASELINE_NOT_STRESSING_OFFLOADING
WARNING_ALL_DECISIONS_LOCAL
WARNING_FULL_OFFLOADING_NOT_OBSERVED
```

La baseline e' strutturalmente valida ma non stressa ancora l'offloading:
le decisioni osservate restano locali, `FULL_OFFLOADING` non appare e CPU/banda
non sono calibrate per una valutazione scientifica finale.

## Readiness

La Fase 11 chiude il consolidamento offline riproducibile
MOSAIC -> stream -> snapshot -> replay. Il sistema e' pronto per una Fase 12
centrata sulle attivita' successive, ad esempio calibrazione sperimentale,
scenario piu' stressante o bridge live. Il bridge live MOSAIC non e'
implementato in questa fase.

## Troubleshooting

- Se gli output esistono e manca `-CleanGeneratedOutputs`, rilanciare con il
  flag dopo aver verificato la whitelist stampata.
- Se `SourceRun` non esiste, controllare `MosaicRoot\logs\<SourceRun>`.
- Se una baseline non coincide, non correggere i JSON a mano: rigenerare dalla
  run corretta.
- Se fallisce il determinismo, ispezionare
  `deterministicArtifactMismatches` in `phase_11_offline_pipeline_validation.json`.
- Se fallisce il replay temporale, ispezionare
  `data/mosaic-study/json_time_full_horizon_trace.csv`.
