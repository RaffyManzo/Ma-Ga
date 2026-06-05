# Fase 13B - Live causal state layer LOCAL/V2V

## 1. Obiettivo

La Fase 13B implementa un livello runtime causale riutilizzabile per mantenere
stato veicoli, task diagnostici, infrastruttura statica e preview dei candidati
`LOCAL`, `VEHICLE` direct single-hop e pool `DIRECT_V2V` durante una
simulazione MOSAIC isolata.

Non produce `SystemSnapshot`, non invoca MA-GA e non implementa Cell, EDGE,
CLOUD, bridge concreto, worker o policy overrun.

## 2. Relazione con 13A

La 13A ha dimostrato lifecycle applicativo, tick server, simulation time,
vehicle update, projected position, speed e stato radio ad-hoc. La 13B riusa
queste API e aggiunge trasformazioni causali su cache runtime.

Correzione preliminare 13A: la diagnostica
`phase_13a_live_api_probe_validation.json` ora usa `latestRunName` e
`latestRunRelativeDir`, senza path assoluti locali.

## 3. Scenario isolato

Scenario creato:

```text
data/mosaic-scenarios/MaGaLiveStateLayerStudy
```

Caratteristiche:

- `scenario id = MaGaLiveStateLayerStudy`;
- SUMO attivo;
- SNS attivo per radio ad-hoc;
- output federate attivo;
- Cell disattivato;
- RSU mantenute;
- server coordinator dedicato;
- veicoli con radio ad-hoc `SINGLE`.

Lo scenario canonico `MaGaIntegratedStudy` non e' stato modificato.

## 4. Cache live

Classe:

```text
LiveStateCache
```

La cache usa:

- `ConcurrentHashMap`;
- sostituzione atomica di oggetti immutabili;
- viste snapshot con copie difensive non modificabili.

Ogni veicolo contiene:

```text
vehicleId
lastUpdateTimeNs
projectedX
projectedY
speedMetersPerSecond
adHocEnabled
active
```

## 5. Modello causale

Policy:

```text
LATEST_OBSERVATION_AT_OR_BEFORE_SNAPSHOT_TIME
```

A ogni tick il coordinator legge solo stati con:

```text
lastUpdateTimeNs <= tickTimeNs
activationTimeNs <= tickTimeNs
```

La validazione ha prodotto:

```text
futureVehicleStateViolations = 0
futureTaskActivationViolations = 0
futureCandidateViolations = 0
futurePoolViolations = 0
```

## 6. Vehicle lifecycle

App:

```text
MaGaLiveVehicleStateApp
```

Lifecycle:

- `onStartup()` registra il veicolo attivo;
- `onVehicleUpdated(...)` aggiorna posizione proiettata, speed e radio;
- `onShutdown()` marca il veicolo inattivo.

Run validata:

```text
vehicleStarts = 4
vehicleUpdates = 36
vehicleStops = 4
vehicleStateRows = 36
```

## 7. Task diagnostici

Config:

```text
application/ma_ga_live_state_config.json
```

Task policy:

```text
PENDING_TASKS_AT_NEXT_COORDINATOR_TICK
```

Il task `diagnostic_light` viene attivato a `7000 ms`, esportato una volta nella
preview e poi marcato `EXPORTED_TO_PREVIEW`.

Risultato:

```text
tasksActivated = 1
taskRows = 1
```

## 8. Infrastruttura statica

La config carica una base statica con:

- 2 gateway RSU;
- 2 EDGE node metadata;
- 1 CLOUD node metadata;
- posizioni proiettate gateway;
- coverage radius;
- region binding;
- pool id gateway.

Questa fase non genera access link, gateway pool, EDGE candidate o CLOUD
candidate.

## 9. Candidati LOCAL

Regola:

```text
un candidato LOCAL per ogni veicolo attivo
```

Convenzione:

```text
candidateId = local_for_<vehicleId>
sourceVehicleId = executionNodeId
propagationDelaySeconds = 0
```

Risultato:

```text
localCandidateRows = 36
duplicateLocalCandidateIds = 0
```

## 10. Candidati V2V

Policy:

```text
DIRECT_SINGLEHOP_ONLY
```

Regola:

```text
source != target
source active
target active
source adHocEnabled
target adHocEnabled
distance <= singlehopRadiusMeters
```

Risultato:

```text
v2vCandidateRows = 68
sourceEqualsTargetViolations = 0
inactiveVehicleCandidateViolations = 0
inactiveRadioCandidateViolations = 0
radiusViolations = 0
```

## 11. Pool DIRECT_V2V

Regola:

```text
un pool condiviso per coppia non ordinata
```

Convenzione:

```text
direct_v2v_pool__veh_0__veh_1
```

Risultato:

```text
v2vPoolRows = 34
directReachablePairs = 34
duplicateV2vPoolIds = 0
ambiguousV2vPoolIds = 0
poolDirectionSharedViolations = 0
```

## 12. Causalita'

Il validator controlla:

- `vehicleState.lastUpdateTimeNs <= tickTimeNs`;
- `task.activationTimeNs <= tickTimeNs`;
- candidate time presente nei tick del coordinator;
- pool time presente nei tick del coordinator.

Tutte le violazioni risultano a zero.

## 13. Preview runtime

Run:

```text
tmp/mosaic-25.2/logs/log-20260605-105320-MaGaLiveStateLayerStudy
```

Directory CSV:

```text
tmp/mosaic-25.2/logs/log-20260605-105320-MaGaLiveStateLayerStudy/live-state-layer/
```

CSV prodotti:

```text
live_vehicle_state_preview.csv
live_task_preview.csv
live_local_candidate_preview.csv
live_v2v_candidate_preview.csv
live_v2v_bandwidth_pool_preview.csv
```

## 14. Build

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\build.ps1
```

Il build crea `tools/mosaic-live-state-layer/out/maga-live-state-layer.jar` e
lo copia nello scenario versionabile.

## 15. Deploy

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\deploy.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Deploy limitato a:

```text
tmp/mosaic-25.2/scenarios/MaGaLiveStateLayerStudy
```

## 16. Run

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

MOSAIC ha completato:

```text
Simulation ended after 20s of 20s (100%)
Simulation finished: 101
```

## 17. Validate

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\validate.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Diagnostica:

```text
data/mosaic-study/diagnostics/phase_13b_live_state_layer_validation.json
```

Stato:

```text
phase13bStatus = COMPLETED
readyForPhase13C = true
errors = []
```

## 18. Test

Esito test A-H:

- A lifecycle veicolo: passato;
- B immutable snapshot view: passato;
- C task causal activation: passato;
- D LOCAL per veicolo attivo: passato;
- E V2V direct single-hop: passato;
- F pool condiviso bidirezionale: passato;
- G no artificial traffic: passato;
- H portable diagnostics: passato.

## 19. Limiti

Non sono implementati:

- Cell live;
- access link;
- gateway pool;
- candidati EDGE;
- candidati CLOUD;
- `SystemSnapshot`;
- `MosaicSnapshotBridge`;
- MA-GA live;
- strategy applier;
- worker;
- policy overrun.

## 20. Cosa resta per 13C

La Fase 13C dovra' introdurre l'adapter Cell runtime e la semantica dei bucket
safe senza riusare dati futuri o log offline come se fossero live.
