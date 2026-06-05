# MOSAIC live state layer

Tool diagnostico versionabile per la Fase 13B. Compila e deploya uno skeleton
runtime MOSAIC che mantiene una cache causale live per veicoli e task
diagnostici, poi produce preview CSV per candidati `LOCAL`, candidati
`VEHICLE` direct single-hop e pool condivisi `DIRECT_V2V`.

Il tool non genera `SystemSnapshot`, non invoca MA-GA e non implementa Cell,
EDGE, CLOUD, bridge concreto, strategy applier, worker o policy overrun.

## Scenario

Scenario versionabile:

```text
data/mosaic-scenarios/MaGaLiveStateLayerStudy
```

Lo scenario deriva dal probe 13A, mantiene SUMO, SNS, output federate, RSU,
server coordinator, veicoli e radio ad-hoc `SINGLE`. La federate Cell resta
disattivata.

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\build.ps1
```

Il build crea:

```text
tools/mosaic-live-state-layer/out/maga-live-state-layer.jar
```

e copia il JAR nello scenario versionabile:

```text
data/mosaic-scenarios/MaGaLiveStateLayerStudy/application/
```

## Deploy

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\deploy.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Il deploy sostituisce solo:

```text
tmp/mosaic-25.2/scenarios/MaGaLiveStateLayerStudy
```

## Run

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Lo script esegue:

```powershell
.\mosaic.bat -s MaGaLiveStateLayerStudy
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

Questi file sono output locali derivati e non vanno versionati.

## Validate

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-state-layer\validate.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Il validator legge la run piu' recente `*-MaGaLiveStateLayerStudy` e genera:

```text
data/mosaic-study/diagnostics/phase_13b_live_state_layer_validation.json
```

La validazione controlla lifecycle, causalita', task activation, candidati
LOCAL, candidati V2V, pool bidirezionali condivisi, assenza di messaggi V2X
artificiali e diagnostiche portabili.

## Semantica

- `LOCAL`: un candidato per ogni veicolo attivo.
- `V2V`: `DIRECT_SINGLEHOP_ONLY`.
- Peer candidabile: `source != target`, entrambi attivi, radio ad-hoc attiva
  su entrambi, distanza euclidea proiettata entro `singlehopRadiusMeters`.
- Pool: un `DIRECT_V2V` condiviso per coppia non ordinata.

## Limiti

Restano fuori da questa fase: Cell live, access link, gateway pool, EDGE, CLOUD,
`SystemSnapshot`, `MosaicSnapshotBridge`, `TemporalWindowManager`, MA-GA live,
strategy apply, worker e policy overrun.
