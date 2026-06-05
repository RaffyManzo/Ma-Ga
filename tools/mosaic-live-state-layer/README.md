# MOSAIC live state layer

Tool diagnostico versionabile per le Fasi 13B e 13C. Compila e deploya uno
skeleton runtime MOSAIC che mantiene una cache causale live per veicoli e task
diagnostici, produce preview CSV per candidati `LOCAL`, candidati `VEHICLE`
direct single-hop e pool condivisi `DIRECT_V2V`, e nello scenario 13C aggiunge
access link, gateway pool, candidati `EDGE`/`CLOUD` e `SystemSnapshot` JSON
live.

Il tool non invoca MA-GA e non implementa bridge concreto, strategy applier,
worker o policy overrun.

## Scenario

Scenario versionabile:

```text
data/mosaic-scenarios/MaGaLiveStateLayerStudy
data/mosaic-scenarios/MaGaLiveInfrastructureSnapshotStudy
```

`MaGaLiveStateLayerStudy` deriva dal probe 13A e mantiene Cell disattivato.
`MaGaLiveInfrastructureSnapshotStudy` deriva dalla 13B, abilita il federate
Cell e usa accounting diagnostico runtime controllato:

```text
DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES
```

La banda Cell non e' una misura diretta del federate.

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\build.ps1
```

Il build crea:

```text
tools/mosaic-live-state-layer/out/maga-live-state-layer.jar
```

e copia il JAR nello scenario 13C versionabile:

```text
data/mosaic-scenarios/MaGaLiveInfrastructureSnapshotStudy/application/
```

## Deploy

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\deploy.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -ScenarioName "MaGaLiveInfrastructureSnapshotStudy"
```

Il deploy sostituisce solo:

```text
tmp/mosaic-25.2/scenarios/<ScenarioName>
```

## Run

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -ScenarioName "MaGaLiveInfrastructureSnapshotStudy"
```

Lo script esegue:

```powershell
.\mosaic.bat -s <ScenarioName>
```

## Runtime output

Il coordinator scrive i CSV nella run locale:

```text
tmp/mosaic-25.2/logs/<run>/live-state-layer/
```

File prodotti:

```text
live_vehicle_state_preview.csv
live_task_preview.csv
live_local_candidate_preview.csv
live_v2v_candidate_preview.csv
live_v2v_bandwidth_pool_preview.csv
```

Per 13C viene scritto anche:

```text
tmp/mosaic-25.2/logs/<run>/live-infrastructure-snapshot/
  live_cell_traffic_event_preview.csv
  live_cell_bandwidth_bucket_preview.csv
  live_access_link_preview.csv
  live_gateway_bandwidth_pool_preview.csv
  live_remote_candidate_preview.csv
  live_snapshot_manifest.csv
  snapshots/snapshot_*.json
```

Questi file sono output locali derivati e non vanno versionati.

## Validate

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\validate.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -ScenarioName "MaGaLiveInfrastructureSnapshotStudy"
```

Il validator legge la run piu' recente dello scenario richiesto. Per 13B genera:

```text
data/mosaic-study/diagnostics/phase_13b_live_state_layer_validation.json
```

Per 13C genera:

```text
data/mosaic-study/diagnostics/phase_13c_live_infrastructure_snapshot_validation.json
```

La validazione controlla lifecycle, causalita', task activation, candidati
LOCAL, candidati V2V, pool bidirezionali condivisi, assenza di messaggi V2X
artificiali e diagnostiche portabili. In 13C controlla inoltre bucket Cell
safe, access link, gateway pool, candidati EDGE/CLOUD, snapshot JSON, loader
Java e `SnapshotValidator`.

## Semantica

- `LOCAL`: un candidato per ogni veicolo attivo.
- `V2V`: `DIRECT_SINGLEHOP_ONLY`.
- Peer candidabile: `source != target`, entrambi attivi, radio ad-hoc attiva
  su entrambi, distanza euclidea proiettata entro `singlehopRadiusMeters`.
- Pool: un `DIRECT_V2V` condiviso per coppia non ordinata.
- Access link: `NEAREST_AVAILABLE_GATEWAY_BY_PROJECTED_DISTANCE`.
- Gateway pool: `LATEST_SAFE_AVAILABLE_CELL_BUCKET`.
- Remote: `EDGE_AND_CLOUD_REQUIRE_ACTIVE_GATEWAY_AND_SAFE_POOL`.

## Limiti

Restano fuori da questa fase: `MosaicSnapshotBridge` concreto,
`MosaicSystemStateSource` live, `TemporalWindowManager` live, MA-GA live,
strategy apply, worker e policy overrun. La Cell bandwidth 13C e' accounting
diagnostico runtime, non una misura scientifica definitiva del federate Cell.
