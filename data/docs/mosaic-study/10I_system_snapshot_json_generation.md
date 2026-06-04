# Fase 10I - Generazione dei SystemSnapshot JSON finali

## 1. Scopo

La Fase 10I trasforma gli stream e le preview diagnostiche 10A-10H in `SystemSnapshot` JSON compatibili con il loader e con i validator Java esistenti.

La fase non esegue il MA-GA, non implementa replay `JSON_SEQUENCE`, non implementa replay `JSON_TIME` e non modifica il core Java. La Fase 10J usera' questi snapshot per il replay.

## 2. Baseline

La baseline canonica verificata e':

```text
log-20260604-220216-MaGaIntegratedStudy
```

La 10I usa anche il gate della 10I-pre2:

```text
phase10iPre2Status = COMPLETED
readyForPhase10I = true
```

## 3. Input

Gli input canonici sono:

```text
optimization_window_timeline.csv
window_task_assignment.csv
vehicle_state_stream_projected.csv
infrastructure_snapshot_projected.json
cell_bandwidth_stream.csv
access_link_preview.csv
remote_candidate_preview.csv
local_candidate_preview.csv
v2v_candidate_preview.csv
v2v_bandwidth_pool_preview.csv
phase_10g_validation.json
phase_10h_validation.json
phase_10i_pre_snapshot_contract_validation.json
phase_10i_pre2_projection_validation.json
```

Gli input delle fasi precedenti sono trattati come read-only.

## 4. Schema JSON reale

Lo schema e' stato ricostruito ispezionando DTO, loader, model e snapshot sintetici.

Campi root:

```text
snapshotId
timeSeconds
vehicles
tasks
candidateNodes
accessGateways
accessLinks
bandwidthPools
```

Campi principali:

```text
VehicleInputDto:
    vehicleId, x, y, speed, localCpu

TaskInputDto:
    taskId, sourceVehicleId, inputSizeBits, outputSizeBits,
    cpuCycles, deadlineSeconds

NodeCandidateInputDto:
    candidateId, sourceVehicleId, executionNodeId, type,
    availableCpu, availableBandwidth, baseLatencySeconds,
    nodeX, nodeY, coverageRadiusMeters, bandwidthPoolId

AccessGatewayInputDto:
    gatewayId, gatewayType, x, y, coverageRadiusMeters, bandwidthPoolId

AccessLinkInputDto:
    accessLinkId, vehicleId, gatewayId, active, available

BandwidthPoolInputDto:
    poolId, poolType, availableBandwidth
```

Enum osservate:

```text
NodeType = LOCAL, VEHICLE, EDGE, CLOUD
BandwidthPoolType = GLOBAL, GATEWAY, DIRECT_V2V
```

## 5. Policy temporali

```text
snapshotTimelinePolicy = EXPLICIT_OPTIMIZATION_WINDOW_TIMELINE
activeVehicleSetPolicy = ACTIVE_VEHICLES_FROM_EXACT_LOCAL_CANDIDATES
vehicleLookupPolicy = LATEST_AVAILABLE_STATE_AT_OR_BEFORE_WINDOW
accessLinkLookupPolicy = EXACT_WINDOW_TIMESTAMP
localCandidateLookupPolicy = EXACT_WINDOW_TIMESTAMP
v2vCandidateLookupPolicy = EXACT_WINDOW_TIMESTAMP
remoteCandidateLookupPolicy = EXACT_WINDOW_TIMESTAMP
v2vPoolLookupPolicy = EXACT_WINDOW_TIMESTAMP
gatewayPoolAssemblyPolicy = LATEST_SAFE_AVAILABLE_CELL_BUCKET_PER_GATEWAY_POOL
```

Ogni snapshot corrisponde a una finestra della timeline 10H. La finestra iniziale a 5 s e' rappresentata anche se non contiene task o veicoli.

## 6. Regole di composizione

I task vengono letti da `window_task_assignment.csv`, non direttamente da `task_stream.csv`. Ogni task compare in un solo snapshot.

I veicoli attivi sono quelli che hanno un candidato LOCAL esatto nella finestra. La posizione viene presa dall'ultimo stato proiettato disponibile a o prima della finestra, senza usare dati futuri.

I gateway sono statici e vengono letti da `infrastructure_snapshot_projected.json`.

Gli access link sono inclusi solo se hanno timestamp esatto della finestra e veicolo attivo. Il contratto 10I-pre consente veicoli senza gateway attivo.

I candidati LOCAL e VEHICLE/V2V non richiedono gateway infrastrutturale. I candidati EDGE e CLOUD richiedono invece un access link attivo e un gateway risolvibile.

I pool V2V sono inclusi solo se referenziati da candidati V2V della finestra. I pool gateway vengono ricostruiti dalla banda Cell residua sicura:

```text
availableBandwidth =
    min(uplinkResidualBandwidth, downlinkResidualBandwidth)
```

## 7. Output

```text
data/snapshots/mosaic-generated/snapshot_000_t_005.json
...
data/snapshots/mosaic-generated/snapshot_035_t_180.json
data/mosaic-study/snapshot_manifest.csv
data/mosaic-study/diagnostics/phase_10i_validation.json
```

## 8. Risultati osservati

```text
snapshotsGenerated = 36
expectedSnapshots = 36
emptyTaskSnapshots = 1
totalTasksAcrossSnapshots = 682
uniqueTasksAcrossSnapshots = 682
tasksLost = 0
duplicateTaskAssignmentsAcrossSnapshots = 0
vehiclesAcrossSnapshots = 369
localCandidatesAcrossSnapshots = 369
v2vCandidatesAcrossSnapshots = 2594
edgeCandidatesAcrossSnapshots = 112
cloudCandidatesAcrossSnapshots = 112
gatewayPoolsAcrossSnapshots = 72
v2vPoolsAcrossSnapshots = 1297
futureLookAheadViolations = 0
orphanReferenceViolations = 0
duplicateCandidateIds = 0
duplicatePoolIds = 0
unresolvedGatewayPools = 0
unresolvedV2vPools = 0
multipleActiveGatewayViolations = 0
activeUnavailableLinkViolations = 0
cloudPlaceholderViolations = 0
```

## 9. Validazione

La validazione Python controlla riferimenti orfani, duplicati, pool risolti, gateway attivi, assenza di look-ahead, coordinate metriche e coerenza tra candidati e link.

La validazione Java usa un harness temporaneo fuori da `src/`:

```text
tmp/phase10i-validation/Phase10iSnapshotValidationMain.java
```

Il harness usa `SnapshotLoader`, `JsonSnapshotFolderLoader` e `SnapshotValidator`, carica tutti i 36 JSON e non esegue il GA.

Risultato:

```text
javaLoaderValidationFailures = 0
javaValidatorFailures = 0
phase10iStatus = COMPLETED
readyForPhase10J = true
```

## 10. Limiti

La timeline resta:

```text
FIXED_INTERVAL_DIAGNOSTIC
```

CPU locale e banda V2V restano:

```text
DIAGNOSTIC_SYNTHETIC_VALUE
```

Questi valori dovranno essere calibrati in una fase successiva.

## 11. Readiness per 10J

La Fase 10I produce snapshot JSON finali compatibili con il core, validati in Python e in Java. Il sistema e' pronto per la Fase 10J, che dovra' eseguire il replay `JSON_SEQUENCE` e `JSON_TIME` senza modificare il contratto snapshot.
