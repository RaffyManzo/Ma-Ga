# 03 - Ricavare i dati MA-GA da MOSAIC, SUMO, Cell e SNS

Data: 2026-06-02.

Questo documento traduce le evidenze locali Barnim/BarnimCell nel contratto MA-GA.

## Sintesi

Non tutti i campi possono essere estratti direttamente da MOSAIC.

- SUMO osserva mobilita'.
- Mapping dichiara entita' e applicazioni.
- Application Simulator genera logica applicativa e workload.
- Cell modella rete cellulare, capacita', ritardi, perdite e regioni.
- SNS modella V2V ad-hoc.
- Il bridge MA-GA deriva candidati, gateway attivi, link attivi e pool residui.

## Snapshot generale

| Campo MA-GA | Fonte pratica | Tipo |
| --- | --- | --- |
| `snapshotId` | bridge/exporter | derivata |
| `timeSeconds` | MOSAIC simulation time, oppure `timeNs / 1e9` da output | osservata |
| `vehicles` | SUMO `VehicleUpdates` / `output.csv` | osservata + profilo |
| `tasks` | `MaGaWorkloadApp` futura | workload |
| `candidateNodes` | resource catalog + vehicles + links + pools | derivata |
| `accessGateways` | RSU/gateway configurati | configurata |
| `accessLinks` | geometria + disponibilita' rete | derivata/osservata |
| `bandwidthPools` | Cell/SNS config + policy residua | configurata/osservata |

## `VehicleSnapshot`

Classe MA-GA:

```java
new VehicleSnapshot(vehicleId, x, y, speed, localCpu)
```

Fonti:

| Campo | Fonte |
| --- | --- |
| `vehicleId` | `VEHICLE_UPDATES` colonna `Updated:Name`, oppure `VehicleData.getName()` |
| `x`, `y` | preferibile `VehicleData.getProjectedPosition().getX/Y()` live; offline da lat/lon convertite con proiezione scenario |
| `speed` | `VEHICLE_UPDATES` colonna `Updated:Speed`, oppure `VehicleData.getSpeed()` |
| `localCpu` | profilo MA-GA, non SUMO |

API locale verificata con `javap`:

```text
VehicleUpdates.getAdded()
VehicleUpdates.getUpdated()
VehicleUpdates.getRemovedNames()
VehicleData.getPosition()
VehicleData.getProjectedPosition()
VehicleData.getSpeed()
VehicleData.getHeading()
```

Decisione: per il bridge live usare `getProjectedPosition()` quando disponibile. Per l'exporter offline usare lat/lon da `output.csv` e la proiezione dello scenario.

## `TaskInstance`

Classe MA-GA:

```java
new TaskInstance(taskId, sourceVehicleId, inputSizeBits, outputSizeBits, cpuCycles, deadlineSeconds)
```

Nessun campo nasce automaticamente da SUMO, Cell o SNS.

Fonte prevista:

- app MOSAIC dedicata `MaGaWorkloadApp`;
- configurazione `ma_ga_workload_config.json`;
- stream riproducibile con seed;
- output offline `task_stream.csv`;
- in live, cache eventi task nel bridge.

Decisione: non inferire task dai messaggi DENM del tutorial Barnim. Quei messaggi servono a studiare comunicazione, non workload computazionale MA-GA.

## `AccessGatewaySnapshot`

Classe MA-GA:

```java
new AccessGatewaySnapshot(gatewayId, gatewayType, x, y, coverageRadiusMeters, bandwidthPoolId)
```

Barnim base non contiene RSU. Quindi i gateway MA-GA vanno introdotti in uno scenario dedicato o in un catalogo esterno.

Decisione:

- gateway MA-GA = RSU/gateway fisico configurato;
- regione Cell = condizione radio/di rete, non gateway fisico;
- `bandwidthPoolId` collega gateway e pool radio.

## `AccessLinkSnapshot`

Classe MA-GA:

```java
new AccessLinkSnapshot(accessLinkId, vehicleId, gatewayId, active, available)
```

Fonte iniziale:

- posizione veicolo da SUMO;
- posizione e raggio gateway da configurazione;
- disponibilita' da Cell/SNS/policy;
- `active` deciso dal bridge, ad esempio gateway piu' vicino tra quelli disponibili.

Cell puo' indicare regioni e handover regionali, ma in Barnim `regions.json` e' vuoto. La classe locale `CellularHandoverUpdates` espone:

```text
getUpdated() -> List<HandoverInfo>
HandoverInfo.getNodeId()
HandoverInfo.getCurrentRegion()
HandoverInfo.getPreviousRegion()
```

Questo puo' aiutare a validare cambi di regione, ma non sostituisce direttamente il gateway fisico MA-GA.

## `NodeCandidate`

Classe MA-GA:

```java
new NodeCandidate(
  candidateId,
  sourceVehicleId,
  executionNodeId,
  type,
  availableCpu,
  availableBandwidth,
  propagationDelaySeconds,
  nodeX,
  nodeY,
  coverageRadiusMeters,
  bandwidthPoolId
)
```

Fonti:

| Campo | Fonte |
| --- | --- |
| `candidateId` | bridge |
| `sourceVehicleId` | veicolo osservato |
| `executionNodeId` | resource catalog |
| `type` | resource catalog: `LOCAL`, `VEHICLE`, `EDGE`, `CLOUD` |
| `availableCpu` | resource catalog + policy di residuo |
| `availableBandwidth` | pool/policy source-aware |
| `propagationDelaySeconds` | Cell/SNS/configurazione |
| `nodeX`, `nodeY`, `coverageRadiusMeters` | gateway/nodo configurato o veicolo osservato per V2V |
| `bandwidthPoolId` | esplicito per V2V o pool gateway |

Decisione: i candidati sono derivati. MOSAIC non produce direttamente `NodeCandidate`.

## `BandwidthPoolSnapshot`

Classe MA-GA:

```java
new BandwidthPoolSnapshot(poolId, poolType, availableBandwidth)
```

Fonti iniziali:

| Pool | Fonte |
| --- | --- |
| `GLOBAL` | capacita' globale Cell o fallback storico |
| `GATEWAY` | capacita' assegnata al gateway/RSU o alla regione/policy |
| `DIRECT_V2V` | SNS config e policy per link diretto |

Evidenza Cell da BarnimCell:

```text
uplink.capacity = 28000000 bit/s
downlink.capacity = 42200000 bit/s
default node bitrate = 100000000000 bit/s
```

Evidenza SNS da Barnim:

```text
singlehopRadius = 709.4 m
singlehopDelay = 0.4 ms .. 2.4 ms
lossProbability = 0.0
maxRetries = 0
```

Decisione: `availableBandwidth` non deve essere semplicemente copiato da `network.json`. Serve una policy:

```text
availableBandwidth =
  capacita' nominale configurata
  - traffico osservato/riservato
  - allocazioni gia' decise nella finestra
```

La prima versione puo' usare capacita' nominale e poi aggiungere consumo residuo quando l'exporter offline sara' stabile.

## Offline prima del live

Prima integrazione consigliata:

```text
output.csv + Mapping + Cell/SNS config + workload stream
        -> MosaicOfflineSnapshotExporter
        -> SystemSnapshot JSON
        -> sorgente JSON_SEQUENCE / JSON_TIME esistente
```

Poi:

```text
MOSAIC interactions
        -> MosaicRuntimeStateCache
        -> MosaicSnapshotAssembler
        -> MosaicSnapshotBridge
        -> MosaicSystemStateSource
        -> TemporalWindowManager
```

## Prossime verifiche

1. Creare `VehicleStateProbeApp` per confrontare `VehicleData.getProjectedPosition()` con `output.csv`.
2. Aggiungere almeno due RSU/gateway a uno scenario MA-GA dedicato.
3. Aggiungere regioni Cell non vuote e verificare `CellularHandoverUpdates`.
4. Configurare workload sintetico e produrre `task_stream.csv`.
5. Scrivere exporter offline prima del bridge live.

## Riferimenti ufficiali consultati

- https://eclipse.dev/mosaic/docs/develop_applications/operating_system/
- https://eclipse.dev/mosaic/docs/simulators/network_simulator_cell/
- https://eclipse.dev/mosaic/docs/mosaic_configuration/sns_config/

