# 07 - Progettazione del catalogo risorse MA-GA

Data: 2026-06-03.

## Obiettivo della fase

La Fase 7 definisce il ruolo del catalogo risorse MA-GA per lo scenario:

```text
scenarios/MaGaMosaicStudy/application/ma_ga_resource_catalog.json
```

Il catalogo non viene letto da MOSAIC e non sostituisce SUMO, Cell, SNS o Mapping. MOSAIC continua a simulare mobilita', comunicazioni e registrazione delle entita' secondo i propri federate.

Il catalogo verra' letto dal futuro exporter offline e, successivamente, dal bridge live. Il suo compito e' tradurre le entita' generiche del simulatore nel modello computazionale richiesto da MA-GA.

In altre parole:

```text
MOSAIC osserva e coordina la simulazione
ma_ga_resource_catalog.json assegna significato computazionale alle risorse
exporter/bridge combinano le fonti e costruiscono SystemSnapshot
```

## Fonti separate

La costruzione di `SystemSnapshot` deve rimanere una composizione di fonti diverse.

```text
SUMO
    -> posizione, velocita', ingresso e uscita dei veicoli

MOSAIC Cell
    -> regioni, handover regionali, ritardi e traffico osservato

SNS
    -> raggiungibilita' e ritardo V2V

Mapping
    -> RSU e server registrati

ma_ga_resource_catalog.json
    -> significato computazionale delle risorse

workload generator
    -> task attivi
```

Questa separazione evita di attribuire a MOSAIC dati che non appartengono al suo modello. SUMO non conosce la CPU locale dei veicoli, Cell non conosce automaticamente i nodi EDGE, e Mapping non definisce da solo la semantica di offloading MA-GA.

## Decisioni progettuali

### Identificativi runtime e identificativi logici

Gli identificativi runtime MOSAIC e gli identificativi logici MA-GA restano distinti.

| Identificativo MOSAIC | Identificativo MA-GA | Significato |
| --------------------- | -------------------- | ----------- |
| `rsu_0` | `rsu_north` | Gateway fisico nella zona nord |
| `rsu_1` | `rsu_central` | Gateway fisico nella zona centrale degradata |
| `server_0` | `cloud_regional` | Server remoto usato come nodo CLOUD |

Questa distinzione permette di non vincolare il modello MA-GA agli identificativi generati da MOSAIC durante la registrazione.

### Regioni Cell e gateway

Le regioni Cell non sono gateway.

```text
region_north_normal
region_central_degraded
```

descrivono condizioni di rete, come capacita', latenza, perdita e handover regionale.

I gateway fisici MA-GA sono invece:

```text
rsu_north
rsu_central
```

I nodi computazionali EDGE sono:

```text
edge_north
edge_central
```

Il server remoto e':

```text
cloud_regional
```

Quindi una regione Cell puo' influenzare il pool di banda o il ritardo, ma non sostituisce una RSU fisica.

### Pool condivisi per gateway

Ogni gateway usa un pool di banda condiviso:

```text
rsu_north
    -> pool_rsu_north

rsu_central
    -> pool_rsu_central
```

Il bridge usera' il gateway attivo del veicolo per risolvere il pool associato ai candidati `EDGE` e `CLOUD`.

### Accesso al cloud

Il cloud e' raggiungibile attraverso il gateway attivo del veicolo.

Questa decisione mantiene separati:

```text
punto di accesso radio
nodo computazionale EDGE
server CLOUD remoto
```

Nel catalogo questa policy e' rappresentata da:

```text
cloudAccess: THROUGH_ACTIVE_GATEWAY
accessPolicy: THROUGH_ACTIVE_GATEWAY
```

### Mappatura conservativa della banda Cell

MOSAIC Cell distingue uplink e downlink. La formalizzazione MA-GA usa invece una sola banda scalare per il candidato o il pool.

Per la prima integrazione viene scelta una mappatura conservativa:

```text
nominalBandwidth = min(uplinkCapacity, downlinkCapacity)
```

Questa decisione evita di sovrastimare la banda disponibile quando una delle due direzioni e' piu' debole. Nella prima versione il catalogo usa valori nominali configurati; la banda residua osservata sara' affrontata nell'exporter offline e nel bridge live.

## Contenuto del catalogo

Il catalogo definito durante la fase contiene quattro sezioni principali:

```text
policies
gateways
bandwidthPools
executionNodes
```

Il contenuto corrente e':

```json
{
  "schemaVersion": "0.1",
  "description": "Catalogo configurato delle risorse MA-GA associate allo scenario MaGaMosaicStudy.",

  "policies": {
    "gatewaySelection": "NEAREST_AVAILABLE_WITHIN_RADIUS",
    "cloudAccess": "THROUGH_ACTIVE_GATEWAY",
    "gatewayPoolBandwidth": "MIN_CELL_UPLINK_DOWNLINK",
    "bandwidthResidualPolicy": "NOMINAL_ONLY_FOR_INITIAL_EXPORTER"
  },

  "gateways": [
    {
      "runtimeId": "rsu_0",
      "gatewayId": "rsu_north",
      "gatewayType": "RSU",
      "cellRegionId": "region_north_normal",
      "coverageRadiusMeters": 520.0,
      "bandwidthPoolId": "pool_rsu_north"
    },
    {
      "runtimeId": "rsu_1",
      "gatewayId": "rsu_central",
      "gatewayType": "RSU",
      "cellRegionId": "region_central_degraded",
      "coverageRadiusMeters": 300.0,
      "bandwidthPoolId": "pool_rsu_central"
    }
  ],

  "bandwidthPools": [
    {
      "poolId": "pool_rsu_north",
      "poolType": "GATEWAY",
      "cellRegionId": "region_north_normal",
      "nominalBandwidthBitsPerSecond": 40000000
    },
    {
      "poolId": "pool_rsu_central",
      "poolType": "GATEWAY",
      "cellRegionId": "region_central_degraded",
      "nominalBandwidthBitsPerSecond": 8000000
    }
  ],

  "executionNodes": [
    {
      "executionNodeId": "edge_north",
      "type": "EDGE",
      "gatewayIds": [
        "rsu_north"
      ],
      "availableCpuCyclesPerSecond": 12000000000,
      "basePropagationDelaySeconds": 0.005
    },
    {
      "executionNodeId": "edge_central",
      "type": "EDGE",
      "gatewayIds": [
        "rsu_central"
      ],
      "availableCpuCyclesPerSecond": 9000000000,
      "basePropagationDelaySeconds": 0.005
    },
    {
      "executionNodeId": "cloud_regional",
      "type": "CLOUD",
      "mosaicServerRuntimeId": "server_0",
      "accessPolicy": "THROUGH_ACTIVE_GATEWAY",
      "availableCpuCyclesPerSecond": 100000000000,
      "serverBaseDelaySeconds": 0.2
    }
  ]
}
```

## Significato delle sezioni

### policies

La sezione `policies` dichiara le scelte interpretative che il futuro exporter o bridge dovra' applicare.

| Campo | Significato |
| ----- | ----------- |
| `gatewaySelection` | Il gateway attivo viene scelto tra quelli disponibili nel raggio, usando il piu' vicino |
| `cloudAccess` | Il cloud viene raggiunto tramite il gateway attivo del veicolo |
| `gatewayPoolBandwidth` | La banda nominale del pool deriva dalla scelta conservativa tra uplink e downlink Cell |
| `bandwidthResidualPolicy` | Per il primo exporter si usa banda nominale, non ancora banda residua misurata |

### gateways

La sezione `gateways` collega le RSU registrate da MOSAIC agli identificativi logici MA-GA.

Contiene:

```text
runtimeId MOSAIC
gatewayId MA-GA
tipo di gateway
regione Cell associata
raggio di copertura
pool di banda collegato
```

Il campo `cellRegionId` non trasforma la regione in gateway. Serve solo a collegare il gateway alle condizioni di rete da usare come riferimento iniziale.

### bandwidthPools

La sezione `bandwidthPools` definisce i pool di banda condivisi dai gateway.

Ogni pool contiene:

```text
poolId
poolType
cellRegionId di riferimento
nominalBandwidthBitsPerSecond
```

Nella prima integrazione il valore `nominalBandwidthBitsPerSecond` e' configurato secondo la policy:

```text
min(uplinkCapacity, downlinkCapacity)
```

La banda residua effettiva non viene ancora calcolata in questa fase.

### executionNodes

La sezione `executionNodes` definisce i nodi computazionali usati per costruire `NodeCandidate`.

Per gli EDGE contiene:

```text
executionNodeId
type = EDGE
gatewayIds raggiungibili
availableCpuCyclesPerSecond
basePropagationDelaySeconds
```

Per il CLOUD contiene:

```text
executionNodeId
type = CLOUD
mosaicServerRuntimeId
accessPolicy
availableCpuCyclesPerSecond
serverBaseDelaySeconds
```

Il cloud non e' associato a una singola RSU. Viene raggiunto attraverso il gateway attivo del veicolo.

## Parametri iniziali

I valori CPU, i raggi di copertura e i ritardi base sono parametri configurati iniziali.

Non rappresentano ancora una calibrazione scientifica definitiva dello scenario. Servono a rendere esplicita la semantica computazionale necessaria per costruire snapshot MA-GA coerenti e riproducibili.

La calibrazione potra' arrivare solo dopo aver collegato:

```text
misure Cell
log SNS
decisioni di offloading
metriche di esecuzione MA-GA
```

## Dati esclusi dal catalogo

Il catalogo non deve contenere dati dinamici o source-aware.

Non appartengono al catalogo:

```text
posizione corrente dei veicoli
velocita' corrente
regione Cell corrente
handover osservati
traffico cellulare misurato
task attivi
candidateId source-aware
access link dinamici
```

Questi dati saranno prodotti o derivati da SUMO, Cell, SNS, Mapping, workload generator, exporter offline e bridge live.

## Implicazioni per SystemSnapshot

Il catalogo abilita la costruzione dei campi configurati o parzialmente derivati dello snapshot:

| Campo `SystemSnapshot` | Uso del catalogo |
| ---------------------- | ---------------- |
| `accessGateways` | alias, tipo gateway, raggio di copertura e pool collegato |
| `bandwidthPools` | identificativi dei pool e banda nominale iniziale |
| `candidateNodes` | EDGE, CLOUD, CPU disponibili e ritardi base |
| `accessLinks` | non contenuti nel catalogo, ma derivati usando gateway e copertura |

Il catalogo non decide la strategia di offloading. Fornisce solo le informazioni statiche necessarie per costruire candidati coerenti con il modello MA-GA.

## Roadmap aggiornata

La Fase 7 e' completata.

La prossima attivita' e' la Fase 8:

```text
studio SNS e costruzione offline delle coppie V2V direttamente raggiungibili
```

In particolare, la Fase 8 dovra':

```text
verificare raggio e ritardo V2V
osservare la raggiungibilita' tra coppie di veicoli
definire come costruire candidati VEHICLE
definire pool DIRECT_V2V iniziali
mantenere separata la capacita' V2V configurata dalle osservazioni SNS
```

Non sono state introdotte modifiche al codice Java e non sono stati implementati exporter o bridge.
