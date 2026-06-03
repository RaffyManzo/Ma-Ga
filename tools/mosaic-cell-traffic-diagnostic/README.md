# MOSAIC Cell Traffic Diagnostic

Questo strumento genera traffico Cell controllato negli scenari diagnostici integrati MA-GA.

Il flusso prodotto e':

```text
vehicle
    -> CELL_TRAFFIC_SEND
    -> server
    -> CELL_TRAFFIC_RECEIVE
    -> CELL_TRAFFIC_RESPONSE_SEND
    -> vehicle
    -> CELL_TRAFFIC_RESPONSE_RECEIVE
```

Lo scopo e' validare insieme:

```text
workload MA-GA sintetico
traffico Cell uplink controllato
traffico Cell downlink controllato
CellularHandoverUpdates
bandwidthMeasurements Up e Dn popolati
```

Lo strumento non modifica il core MA-GA, non crea custom interaction MOSAIC e non implementa il bridge live.

## Struttura

```text
tools/mosaic-cell-traffic-diagnostic/
|-- README.md
|-- build.ps1
`-- src/
    `-- org/eclipse/mosaic/app/maga/celltraffic/
        |-- MaGaCellTrafficDiagnosticConfig.java
        |-- MaGaCellTrafficDiagnosticMessage.java
        |-- MaGaCellTrafficDiagnosticResponseMessage.java
        |-- MaGaCellTrafficDiagnosticServerApp.java
        `-- MaGaCellTrafficDiagnosticVehicleApp.java
```

Il build genera:

```text
tools/mosaic-cell-traffic-diagnostic/out/maga-cell-traffic-diagnostic.jar
```

## API MOSAIC usate

Le API sono state verificate sui JAR locali MOSAIC 25.2.

Veicolo:

```text
AbstractApplication<VehicleOperatingSystem>
CommunicationApplication
VehicleOperatingSystem.getCellModule()
CellModuleConfiguration.maxUplinkBitrate(...)
CellModuleConfiguration.maxDownlinkBitrate(...)
CellModule.createMessageRouting()
CellMessageRoutingBuilder.topological().destination(...).tcp().build()
CellModule.sendV2xMessage(...)
onMessageReceived(ReceivedV2xMessage)
```

Server:

```text
AbstractApplication<ServerOperatingSystem>
CommunicationApplication
ServerOperatingSystem extends CellCommunicative
ServerOperatingSystem.getCellModule()
CellModule.sendV2xMessage(...)
```

Messaggi:

```text
V2xMessage
MessageRouting
EncodedPayload
ReceivedV2xMessage.getMessage()
```

`EncodedPayload` viene alimentato con lunghezza in byte. MOSAIC Cell converte in bit e aggiunge gli header tramite `CapacityUtility.getMessageLengthWithHeaders(...)`.

## Configurazione

Ogni scenario contiene:

```text
application/ma_ga_cell_traffic_config.json
```

Formato:

```json
{
  "destinationId": "server_0",
  "requestPayloadBytes": 1000,
  "responsePayloadBytes": 500,
  "intervalMs": 1000,
  "initialDelayMs": 1000,
  "maxUplinkBitrate": "50 Mbps",
  "maxDownlinkBitrate": "50 Mbps"
}
```

La configurazione viene letta con `OperatingSystem.getConfigurationPath()` e un parser locale volutamente limitato ai campi diagnostici noti.

## Scenari Versionabili

Le sorgenti definitive degli scenari sono in:

```text
data/mosaic-scenarios/
```

Scenari:

```text
MaGaIntegratedStudy
MaGaIntegratedStudyRequest2x
MaGaIntegratedStudyResponse2x
MaGaIntegratedStudyFrequency2x
```

Tutti mantengono:

```text
12 veicoli
2 RSU
1 server
MaGaWorkloadDiagnosticApp
MaGaCellTrafficDiagnosticVehicleApp
MaGaCellTrafficDiagnosticServerApp
CellularHandoverUpdates
bandwidthMeasurements
```

Varianti:

| Scenario | Request bytes | Response bytes | Interval |
| --- | ---: | ---: | ---: |
| `MaGaIntegratedStudy` | 1000 | 500 | 1000 ms |
| `MaGaIntegratedStudyRequest2x` | 2000 | 500 | 1000 ms |
| `MaGaIntegratedStudyResponse2x` | 1000 | 1000 | 1000 ms |
| `MaGaIntegratedStudyFrequency2x` | 1000 | 500 | 500 ms |

## Deploy

Gli scenari versionabili vengono copiati nel runtime MOSAIC con:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\deploy-mosaic-scenarios.ps1
```

Lo script sostituisce soltanto le quattro cartelle diagnostiche integrate omonime sotto:

```text
tmp/mosaic-25.2/scenarios/
```

Non modifica gli scenari baseline storici.

## Build

Dalla root della repository:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\mosaic-cell-traffic-diagnostic\build.ps1
```

Il build:

```text
1. compila le classi Java;
2. verifica le classi .class attese;
3. crea il JAR;
4. verifica le entry del JAR;
5. copia il JAR negli scenari versionabili;
6. esegue il deploy verso tmp/mosaic-25.2/scenarios/;
7. verifica il JAR nei quattro scenari locali.
```

Durante il build locale `javac` stampa una `AccessDeniedException` in chiusura di `out/classpath/slf4j-api-2.0.12.jar`. Il warning non viene nascosto. Il build resta accettabile solo perche' classi, JAR, entry JAR, deploy e run MOSAIC sono stati verificati.

## Run

Dalla cartella `tmp/mosaic-25.2/`:

```powershell
.\mosaic.bat -s MaGaIntegratedStudy
.\mosaic.bat -s MaGaIntegratedStudyRequest2x
.\mosaic.bat -s MaGaIntegratedStudyResponse2x
.\mosaic.bat -s MaGaIntegratedStudyFrequency2x
```

## Log

Veicolo:

```text
CELL_TRAFFIC_APP_START
CELL_TRAFFIC_SEND
CELL_TRAFFIC_RESPONSE_RECEIVE
CELL_TRAFFIC_APP_STOP
```

Server:

```text
CELL_TRAFFIC_SERVER_START
CELL_TRAFFIC_RECEIVE
CELL_TRAFFIC_RESPONSE_SEND
CELL_TRAFFIC_SERVER_STOP
```

Identificativi:

```text
request  = cell_diag_req__<vehicleId>__t_<sendTimeMs>
response = cell_diag_res__<vehicleId>__t_<requestSendTimeMs>
```

## Limiti

Questa diagnostica:

```text
non aggiorna gli exporter 10A-10F;
non genera ancora SystemSnapshot;
non invoca il core Java MA-GA;
non implementa il bridge live;
non e' lo scenario realistico finale;
non calibra ancora una policy definitiva di banda residua.
```
