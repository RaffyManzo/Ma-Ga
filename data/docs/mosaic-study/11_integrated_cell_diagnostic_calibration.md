# Fase 11 - Diagnostica Cell integrata con uplink e downlink

## Obiettivo

Questa fase corregge la baseline diagnostica integrata aggiungendo traffico Cell downlink controllato.

Il flusso finale e':

```text
vehicle
    -> request Cell
    -> server
    -> response Cell
    -> vehicle
```

L'obiettivo e' validare in una stessa famiglia di run:

```text
workload MA-GA sintetico
CellularHandoverUpdates
bandwidthMeasurements uplink
bandwidthMeasurements downlink
scalabilita controllata request/response/frequenza
```

Questa diagnostica non e' lo scenario realistico finale e non modifica il core MA-GA.

## Problema iniziale

La prima baseline integrata generava solo:

```text
vehicle -> server
```

Il risultato era:

```text
ALL#ALL#ALL#Up.csv popolato
ALL#ALL#ALL#Dn.csv vuoto
```

Questo era sufficiente per classificare l'uplink, ma non per chiudere correttamente la Fase 10D sui dati Cell.

## Aggiunta del downlink

Il server ora invia una risposta Cell topological unicast al veicolo sorgente.

Log lato veicolo:

```text
CELL_TRAFFIC_APP_START
CELL_TRAFFIC_SEND
CELL_TRAFFIC_RESPONSE_RECEIVE
CELL_TRAFFIC_APP_STOP
```

Log lato server:

```text
CELL_TRAFFIC_SERVER_START
CELL_TRAFFIC_RECEIVE
CELL_TRAFFIC_RESPONSE_SEND
CELL_TRAFFIC_SERVER_STOP
```

Identificativi deterministici:

```text
request  = cell_diag_req__<vehicleId>__t_<sendTimeMs>
response = cell_diag_res__<vehicleId>__t_<requestSendTimeMs>
```

## API MOSAIC locali

Le API sono state verificate sui JAR locali con `javap` e `jar tf`.

Evidenze:

```text
ServerOperatingSystem
    extends OperatingSystem, Routable, CellCommunicative

VehicleOperatingSystem
    extends OperatingSystem, CellCommunicative

CellModule
    enable(CellModuleConfiguration)
    sendV2xMessage(V2xMessage)
    createMessageRouting()

CellMessageRoutingBuilder
    topological()
    destination(String)
    tcp()
    build()

ReceivedV2xMessage
    getMessage()

EncodedPayload
    EncodedPayload(long)
    getEffectiveLength()
    internal field lengthInBytes
```

Conclusione API:

```text
ServerOperatingSystem.getCellModule()
    .createMessageRouting()
    .topological()
    .destination(<vehicleId>)
    .tcp()
    .build()
```

e' una costruzione supportata localmente per la risposta server -> vehicle.

## Struttura App

Tool:

```text
tools/mosaic-cell-traffic-diagnostic/
```

Classi:

```text
MaGaCellTrafficDiagnosticConfig
MaGaCellTrafficDiagnosticMessage
MaGaCellTrafficDiagnosticResponseMessage
MaGaCellTrafficDiagnosticVehicleApp
MaGaCellTrafficDiagnosticServerApp
```

La request usa `requestPayloadBytes`.

La response usa `responsePayloadBytes`.

Entrambe usano `EncodedPayload(<bytes>)`.

## Scenari versionabili

Le sorgenti definitive sono state spostate in:

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

Lo scenario `MaGaIntegratedStudyPayload2x` non e' piu' usato come nome definitivo.

## Configurazioni A/B/C/D

| Scenario | Request bytes | Response bytes | Interval | Initial delay |
| --- | ---: | ---: | ---: | ---: |
| A - `MaGaIntegratedStudy` | 1000 | 500 | 1000 ms | 1000 ms |
| B - `MaGaIntegratedStudyRequest2x` | 2000 | 500 | 1000 ms | 1000 ms |
| C - `MaGaIntegratedStudyResponse2x` | 1000 | 1000 | 1000 ms | 1000 ms |
| D - `MaGaIntegratedStudyFrequency2x` | 1000 | 500 | 500 ms | 1000 ms |

Tutto il resto resta identico:

```text
12 veicoli
2 RSU
1 server
percorso SUMO
regioni Cell
capacita Cell
ritardi Cell
catalogo risorse
durata 180 s
```

## Mapping

Ogni veicolo esegue:

```text
org.eclipse.mosaic.app.maga.workload.MaGaWorkloadDiagnosticApp
org.eclipse.mosaic.app.maga.celltraffic.MaGaCellTrafficDiagnosticVehicleApp
```

Il server esegue:

```text
org.eclipse.mosaic.app.maga.celltraffic.MaGaCellTrafficDiagnosticServerApp
```

Le RSU restano senza applicazioni.

## Output Configuration

L'export di `CellularHandoverUpdates` resta:

```xml
<subscription id="CellularHandoverUpdates">
    <entries>
        <entry>"CELLULAR_HANDOVER"</entry>
        <entry>Time</entry>
        <entry>Updated:NodeId</entry>
        <entry>Updated:PreviousRegion</entry>
        <entry>Updated:CurrentRegion</entry>
    </entries>
</subscription>
```

I `bandwidthMeasurements` restano configurati nei file Cell degli scenari copiati da `MaGaWorkloadStudy`.

## Build e Deploy

Script creati o aggiornati:

```text
tools/mosaic-cell-traffic-diagnostic/build.ps1
tools/deploy-mosaic-scenarios.ps1
```

Comando build:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\mosaic-cell-traffic-diagnostic\build.ps1
```

Il build:

```text
compila le classi Java;
verifica le classi .class attese;
crea il JAR;
verifica le entry del JAR;
copia il JAR negli scenari versionabili;
esegue il deploy verso tmp/mosaic-25.2/scenarios/;
verifica il JAR nei quattro scenari locali.
```

## AccessDeniedException

Durante il build `javac` stampa:

```text
java.nio.file.AccessDeniedException:
C:\Users\raffa\IdeaProjects\maga-core\tools\mosaic-cell-traffic-diagnostic\out\classpath\slf4j-api-2.0.12.jar
```

Azioni fatte:

```text
classpath wildcard non usata;
dipendenze MOSAIC copiate in out/classpath;
processi sumo/java/mosaic ispezionati;
classi .class verificate;
entry JAR verificate;
JAR deployato verificato;
quattro run MOSAIC completate.
```

La correzione prudente ha spostato il problema dal JAR MOSAIC originale a una copia locale del JAR `slf4j`, quindi il problema appare legato alla chiusura ZipFS/JDK su Windows in questo ambiente, non a un singolo JAR MOSAIC o a una wildcard di classpath.

Il warning non viene nascosto. Rimane un limite locale non bloccante perche' le verifiche successive passano.

## Comandi simulazione

Dalla cartella `tmp/mosaic-25.2/`:

```powershell
.\mosaic.bat -s MaGaIntegratedStudy
.\mosaic.bat -s MaGaIntegratedStudyRequest2x
.\mosaic.bat -s MaGaIntegratedStudyResponse2x
.\mosaic.bat -s MaGaIntegratedStudyFrequency2x
```

Run definitive:

```text
log-20260603-174645-MaGaIntegratedStudy
log-20260603-174659-MaGaIntegratedStudyRequest2x
log-20260603-174713-MaGaIntegratedStudyResponse2x
log-20260603-174732-MaGaIntegratedStudyFrequency2x
```

Tutte riportano:

```text
Simulation ended after 180s of 180s (100%)
```

## Conteggi run

| Metrica | A baseline | B request 2x | C response 2x | D frequency 2x |
| --- | ---: | ---: | ---: | ---: |
| VEHICLE_REGISTRATION | 12 | 12 | 12 | 12 |
| RSU_REGISTRATION | 2 | 2 | 2 | 2 |
| SERVER_REGISTRATION | 1 | 1 | 1 | 1 |
| WORKLOAD_APP_START | 12 | 12 | 12 | 12 |
| WORKLOAD_APP_STOP | 12 | 12 | 12 | 12 |
| TASK_ACTIVATION | 682 | 682 | 682 | 682 |
| taskId duplicati | 0 | 0 | 0 | 0 |
| CELLULAR_HANDOVER | 48 | 48 | 48 | 48 |
| CELL_TRAFFIC_APP_START | 12 | 12 | 12 | 12 |
| CELL_TRAFFIC_APP_STOP | 12 | 12 | 12 | 12 |
| CELL_TRAFFIC_SEND | 1824 | 1824 | 1824 | 3636 |
| CELL_TRAFFIC_RECEIVE | 1779 | 1779 | 1779 | 3574 |
| request messageId duplicati | 0 | 0 | 0 | 0 |
| CELL_TRAFFIC_SERVER_START | 1 | 1 | 1 | 1 |
| CELL_TRAFFIC_RESPONSE_SEND | 1779 | 1779 | 1779 | 3574 |
| CELL_TRAFFIC_RESPONSE_RECEIVE | 1752 | 1752 | 1752 | 3514 |
| responseMessageId duplicati | 0 | 0 | 0 | 0 |
| errori applicativi | 0 | 0 | 0 | 0 |

Il workload resta stabile:

```text
TASK_ACTIVATION = 682
```

## Analisi Up.csv

Tutti gli `Up.csv` hanno `180` righe dati.

Estratti:

```csv
A Up first
time,region_north_normal,region_central_degraded,globalNetwork
0,0,0,0
1,0,0,0
2,0,0,0
3,0,0,0
4,0,0,0
5,0,0,0
6,0,0,0
7,42320,0,0
8,42320,0,0

A Up last
175,0,42320,465520
176,0,0,507840
177,0,0,507840
178,0,0,507840
179,0,0,507840
```

```csv
B Up first
time,region_north_normal,region_central_degraded,globalNetwork
0,0,0,0
1,0,0,0
2,0,0,0
3,0,0,0
4,0,0,0
5,0,0,0
6,0,0,0
7,82320,0,0
8,82320,0,0

B Up last
175,0,82320,905520
176,0,0,987840
177,0,0,987840
178,0,0,987840
179,0,0,987840
```

```csv
C Up first
time,region_north_normal,region_central_degraded,globalNetwork
0,0,0,0
1,0,0,0
2,0,0,0
3,0,0,0
4,0,0,0
5,0,0,0
6,0,0,0
7,42320,0,0
8,42320,0,0

C Up last
175,0,42320,465520
176,0,0,507840
177,0,0,507840
178,0,0,507840
179,0,0,507840
```

```csv
D Up first
time,region_north_normal,region_central_degraded,globalNetwork
0,0,0,0
1,0,0,0
2,0,0,0
3,0,0,0
4,0,0,0
5,0,0,0
6,0,0,0
7,84640,0,0
8,84640,0,0

D Up last
175,0,84640,931040
176,0,0,1015680
177,0,0,1015680
178,0,0,1015680
179,0,0,1015680
```

Somme uplink:

| Run | region_north_normal | region_central_degraded | globalNetwork | totale |
| --- | ---: | ---: | ---: | ---: |
| A | 38595840 | 23487600 | 13203840 | 75287280 |
| B | 75075840 | 45687600 | 25683840 | 146447280 |
| C | 38595840 | 23487600 | 13203840 | 75287280 |
| D | 77191680 | 47652320 | 26407680 | 151251680 |

Massimi uplink:

| Run | region_north_normal | region_central_degraded | globalNetwork |
| --- | ---: | ---: | ---: |
| A | 507840 | 507840 | 507840 |
| B | 987840 | 987840 | 987840 |
| C | 507840 | 507840 | 507840 |
| D | 1015680 | 1015680 | 1015680 |

Valori non zero uplink:

| Run | region_north_normal | region_central_degraded | globalNetwork |
| --- | ---: | ---: | ---: |
| A | 120 | 92 | 48 |
| B | 120 | 92 | 48 |
| C | 120 | 92 | 48 |
| D | 120 | 93 | 48 |

## Analisi Dn.csv

`Dn.csv` ora e' popolato.

A, B e C hanno `180` righe dati. D ha `181` righe dati e include il bucket finale `time=180`.

Estratti:

```csv
A Dn first
time,region_north_normal,region_central_degraded,globalNetwork
0,0,0,0
1,0,0,0
2,0,0,0
3,0,0,0
4,0,0,0
5,0,0,0
6,0,0,0
7,55800,0,0
8,55800,0,0

A Dn last
175,0,17856,491040
176,0,0,535680
177,0,0,535680
178,0,0,535680
179,0,0,535680
```

```csv
B Dn first
time,region_north_normal,region_central_degraded,globalNetwork
0,0,0,0
1,0,0,0
2,0,0,0
3,0,0,0
4,0,0,0
5,0,0,0
6,0,0,0
7,55800,0,0
8,55800,0,0

B Dn last
175,0,17856,491040
176,0,0,535680
177,0,0,535680
178,0,0,535680
179,0,0,535680
```

```csv
C Dn first
time,region_north_normal,region_central_degraded,globalNetwork
0,0,0,0
1,0,0,0
2,0,0,0
3,0,0,0
4,0,0,0
5,0,0,0
6,0,0,0
7,105800,0,0
8,105800,0,0

C Dn last
175,0,33856,931040
176,0,0,1015680
177,0,0,1015680
178,0,0,1015680
179,0,0,1015680
```

```csv
D Dn first
time,region_north_normal,region_central_degraded,globalNetwork
0,0,0,0
1,0,0,0
2,0,0,0
3,0,0,0
4,0,0,0
5,0,0,0
6,0,0,0
7,111600,0,0
8,167400,0,0

D Dn last
176,0,0,1071360
177,0,0,1071360
178,0,0,1071360
179,0,0,1071360
180,0,0,535680
```

Somme downlink:

| Run | region_north_normal | region_central_degraded | globalNetwork | totale |
| --- | ---: | ---: | ---: | ---: |
| A | 50889600 | 9427968 | 13927680 | 74245248 |
| B | 50889600 | 9427968 | 13927680 | 74245248 |
| C | 96489600 | 17875968 | 26407680 | 140773248 |
| D | 152668800 | 19034496 | 28391040 | 200094336 |

Massimi downlink:

| Run | region_north_normal | region_central_degraded | globalNetwork |
| --- | ---: | ---: | ---: |
| A | 669600 | 214272 | 535680 |
| B | 669600 | 214272 | 535680 |
| C | 1269600 | 406272 | 1015680 |
| D | 2008800 | 410688 | 1071360 |

Valori non zero downlink:

| Run | region_north_normal | region_central_degraded | globalNetwork |
| --- | ---: | ---: | ---: |
| A | 120 | 92 | 48 |
| B | 120 | 92 | 48 |
| C | 120 | 92 | 48 |
| D | 121 | 93 | 49 |

## Rapporti

Rapporti sulle somme raw per regione.

### Uplink

| Regione | B/A request 2x | C/A response 2x | D/A frequency 2x |
| --- | ---: | ---: | ---: |
| region_north_normal | 1.945180 | 1.000000 | 2.000000 |
| region_central_degraded | 1.945180 | 1.000000 | 2.028829 |
| globalNetwork | 1.945180 | 1.000000 | 2.000000 |

### Downlink

| Regione | B/A request 2x | C/A response 2x | D/A frequency 2x |
| --- | ---: | ---: | ---: |
| region_north_normal | 1.000000 | 1.896057 | 3.000000 |
| region_central_degraded | 1.000000 | 1.896057 | 2.018939 |
| globalNetwork | 1.000000 | 1.896057 | 2.038462 |

Rapporti sui totali:

| Direzione | B/A | C/A | D/A |
| --- | ---: | ---: | ---: |
| UPLINK | 1.945180 | 1.000000 | 2.008994 |
| DOWNLINK | 1.000000 | 1.896057 | 2.695046 |

Interpretazione:

```text
B/A
    uplink cresce;
    downlink resta invariato.

C/A
    uplink resta invariato;
    downlink cresce.

D/A
    uplink cresce;
    downlink cresce.
```

I rapporti non sono forzati a 2. Gli scostamenti derivano da header fissi, latenze, attraversamenti regionali, messaggi non ricevuti e bucket temporali. Nel caso D, il downlink include anche un bucket finale `time=180` e mostra sovrapposizioni temporali piu' marcate delle risposte.

## Catena dell'unita Cell

Catena verificata localmente:

```text
EncodedPayload.getEffectiveLength()
    -> lengthInBytes
    -> CapacityUtility.getMessageLengthWithHeaders(...)
    -> conversione payload byte * 8
    -> header Cell/IP/TCP aggiunti
    -> CapacityUtility.calculateNeededCapacity(length, delayNs)
    -> consumedBandwidth / StreamProperties.bandwidth
    -> CSV bandwidthMeasurements
```

La nuova evidenza downlink e' coerente:

```text
request 2x
    -> modifica uplink
    -> non modifica downlink

response 2x
    -> non modifica uplink
    -> modifica downlink

frequency 2x
    -> modifica uplink
    -> modifica downlink
```

Classificazione finale:

```text
PROVEN_BITS_PER_SECOND
```

## Limiti

La fase resta diagnostica.

Non sono ancora implementati:

```text
exporter offline sulla baseline integrata definitiva;
SystemSnapshot finale da MOSAIC;
calcolo banda residua scientificamente calibrato;
candidati V2V definitivi;
bridge live;
scenario realistico finale.
```

Gli handover regionali Cell descrivono condizioni di rete, non gateway fisici MA-GA.

## Conclusione

La correzione downlink e' stata implementata e validata.

Risultati principali:

```text
TASK_ACTIVATION = 682 in tutte le run;
CELLULAR_HANDOVER = 48 in tutte le run;
request e response senza duplicati;
Up.csv popolato;
Dn.csv popolato;
unita CSV Cell confermata come PROVEN_BITS_PER_SECOND;
core MA-GA non modificato;
exporter offline non rieseguito sulla baseline integrata definitiva.
```
