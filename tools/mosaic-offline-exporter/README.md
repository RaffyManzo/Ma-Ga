# MOSAIC Offline Exporter

Questo folder contiene i primi frammenti dell'exporter offline per lo studio Eclipse MOSAIC -> MA-GA.

Gli script sono diagnostici, usano solo la standard library Python, non invocano il core Java MA-GA e non modificano gli scenari MOSAIC.

## Fase 10A - Aggregazione del workload diagnostico

La Fase 10A aggrega il workload diagnostico generato da:

```text
org.eclipse.mosaic.app.maga.workload.MaGaWorkloadDiagnosticApp
```

Script:

```text
export_task_stream.py
```

Lo script legge i log applicativi MOSAIC e produce uno stream CSV di task attivati, utile per il futuro exporter di snapshot.

### Input 10A

Root dei log applicativi della run diagnostica:

```text
tmp/mosaic-25.2/logs/log-20260603-123856-MaGaWorkloadStudy/apps/
```

Lo script cerca ricorsivamente file chiamati:

```text
MaGaWorkloadDiagnosticApp.log
```

ed estrae i payload:

```text
TASK_ACTIVATION|...
```

I log MOSAIC possono avere prefissi di timestamp/logger prima del payload; lo script valida il payload `TASK_ACTIVATION` estratto dalla riga.

### Output 10A

CSV generato:

```text
data/mosaic-study/task_stream.csv
```

Colonne:

```text
taskId
sourceVehicleId
profileId
activationTimeNs
activationTimeMs
inputSizeBits
outputSizeBits
cpuCycles
deadlineSeconds
```

### Esempio PowerShell 10A

```powershell
python .\tools\mosaic-offline-exporter\export_task_stream.py `
  --workload-log-root ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\apps" `
  --out-file ".\data\mosaic-study\task_stream.csv"
```

Con il launcher Windows:

```powershell
py .\tools\mosaic-offline-exporter\export_task_stream.py `
  --workload-log-root ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\apps" `
  --out-file ".\data\mosaic-study\task_stream.csv"
```

### Validazioni 10A

Lo script fallisce se:

```text
la cartella input non esiste
non trova alcun MaGaWorkloadDiagnosticApp.log
non trova alcuna riga TASK_ACTIVATION
una riga TASK_ACTIVATION e' malformata
manca un campo obbligatorio
un campo appare due volte nella stessa riga
un valore numerico non e' valido o non e' finito
taskId, sourceVehicleId o profileId sono vuoti
esistono taskId duplicati
activationTimeNs < 0
activationTimeMs < 0
inputSizeBits <= 0
outputSizeBits < 0
cpuCycles <= 0
deadlineSeconds <= 0
```

Verifica inoltre:

```text
activationTimeNs == activationTimeMs * 1_000_000
taskId == <profileId>__<sourceVehicleId>__t_<activationTimeMs>
sourceVehicleId == nome della cartella veicolo che contiene il log
```

I record vengono ordinati per:

```text
activationTimeNs
sourceVehicleId
profileId
taskId
```

La scrittura e' atomica: prima file temporaneo, poi sostituzione del CSV finale.

### Limiti 10A

```text
non genera SystemSnapshot
non assegna task alle finestre MA-GA
non invoca il core Java
non implementa il bridge live
non consuma i task dopo una decisione MA-GA
non legge dinamicamente ma_ga_workload_config.json
```

## Fase 10B - Normalizzazione dello stato dei veicoli

La Fase 10B normalizza lo stato osservato dei veicoli a partire da `output.csv`.

Script:

```text
export_vehicle_state_stream.py
```

Lo script legge `VEHICLE_REGISTRATION` e `VEHICLE_UPDATES`, valida il ciclo di vita minimo dei veicoli e produce un CSV con una riga per ogni aggiornamento veicolare valido.

### Input 10B

```text
tmp/mosaic-25.2/logs/log-20260603-123856-MaGaWorkloadStudy/output.csv
```

Eventi usati:

```text
VEHICLE_REGISTRATION
VEHICLE_UPDATES
```

Gli altri eventi vengono ignorati.

### Campi letti da VEHICLE_UPDATES

```text
indice 0 -> event marker
indice 1 -> timeNs
indice 2 -> vehicleId
indice 3 -> speed
indice 4 -> heading
indice 5 -> latitude
indice 6 -> longitude
indice 7 -> altitude
```

Non vengono attribuiti significati non verificati agli altri campi o ai booleani presenti nelle righe.

### Output 10B

```text
data/mosaic-study/vehicle_state_stream.csv
```

Colonne:

```text
timeNs
timeSeconds
vehicleId
latitude
longitude
projectedX
projectedY
speed
heading
active
```

Regole:

```text
active = true per ogni VEHICLE_UPDATES esportato
projectedX e projectedY restano vuoti
timeSeconds = timeNs / 1_000_000_000
```

### Esempio PowerShell 10B

```powershell
python .\tools\mosaic-offline-exporter\export_vehicle_state_stream.py `
  --input-file ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\output.csv" `
  --out-file ".\data\mosaic-study\vehicle_state_stream.csv"
```

Con il launcher Windows:

```powershell
py .\tools\mosaic-offline-exporter\export_vehicle_state_stream.py `
  --input-file ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\output.csv" `
  --out-file ".\data\mosaic-study\vehicle_state_stream.csv"
```

### Validazioni 10B

Lo script fallisce se:

```text
il file input non esiste o non e' un file
non trova VEHICLE_REGISTRATION o VEHICLE_UPDATES
una riga VEHICLE_REGISTRATION ha meno di 3 campi
una riga VEHICLE_UPDATES ha meno di 8 campi
timeNs non e' intero valido o e' < 0
vehicleId e' vuoto
speed, heading, latitude, longitude o altitude non sono numerici o non sono finiti
speed < 0
heading < 0 oppure heading >= 360
latitude fuori [-90, 90]
longitude fuori [-180, 180]
un veicolo viene registrato piu' di una volta
un VEHICLE_UPDATES compare prima della registrazione
lo stesso veicolo ha piu' stati allo stesso timeNs
il tempo degli eventi rilevanti diminuisce durante la lettura
```

Gli stati vengono ordinati per:

```text
timeNs
vehicleId con ordinamento naturale
```

La scrittura e' atomica.

### Limiti 10B

```text
non calcola projectedX/projectedY
non calcola distanze
non aggiunge localCpu
non costruisce VehicleSnapshot
non assembla SystemSnapshot
non invoca il core Java
non implementa il bridge live
```

Nella run diagnostica corrente non sono stati osservati eventi di rimozione dei veicoli. La gestione delle uscite verra' estesa solo dopo avere osservato il relativo formato reale.

## Fase 10C - Normalizzazione e validazione dell'infrastruttura

La Fase 10C costruisce una fotografia statica validata dell'infrastruttura necessaria alle fasi successive dell'exporter offline.

Script:

```text
export_infrastructure_snapshot.py
```

Lo script combina:

```text
registrazioni runtime RSU/server da output.csv
configurazione Cell
configurazione SNS
catalogo risorse MA-GA
```

e produce:

```text
data/mosaic-study/infrastructure_snapshot.json
```

### Input 10C

Argomenti obbligatori:

```text
--output-csv
--cell-config
--network-config
--regions-config
--sns-config
--resource-catalog
--out-file
```

Run/scenario diagnostico:

```text
tmp/mosaic-25.2/logs/log-20260603-123856-MaGaWorkloadStudy/output.csv
tmp/mosaic-25.2/scenarios/MaGaWorkloadStudy/cell/cell_config.json
tmp/mosaic-25.2/scenarios/MaGaWorkloadStudy/cell/network.json
tmp/mosaic-25.2/scenarios/MaGaWorkloadStudy/cell/regions.json
tmp/mosaic-25.2/scenarios/MaGaWorkloadStudy/sns/sns_config.json
tmp/mosaic-25.2/scenarios/MaGaWorkloadStudy/application/ma_ga_resource_catalog.json
```

### Esempio PowerShell 10C

```powershell
python .\tools\mosaic-offline-exporter\export_infrastructure_snapshot.py `
  --output-csv ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\output.csv" `
  --cell-config ".\tmp\mosaic-25.2\scenarios\MaGaWorkloadStudy\cell\cell_config.json" `
  --network-config ".\tmp\mosaic-25.2\scenarios\MaGaWorkloadStudy\cell\network.json" `
  --regions-config ".\tmp\mosaic-25.2\scenarios\MaGaWorkloadStudy\cell\regions.json" `
  --sns-config ".\tmp\mosaic-25.2\scenarios\MaGaWorkloadStudy\sns\sns_config.json" `
  --resource-catalog ".\tmp\mosaic-25.2\scenarios\MaGaWorkloadStudy\application\ma_ga_resource_catalog.json" `
  --out-file ".\data\mosaic-study\infrastructure_snapshot.json"
```

Con il launcher Windows:

```powershell
py .\tools\mosaic-offline-exporter\export_infrastructure_snapshot.py `
  --output-csv ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\output.csv" `
  --cell-config ".\tmp\mosaic-25.2\scenarios\MaGaWorkloadStudy\cell\cell_config.json" `
  --network-config ".\tmp\mosaic-25.2\scenarios\MaGaWorkloadStudy\cell\network.json" `
  --regions-config ".\tmp\mosaic-25.2\scenarios\MaGaWorkloadStudy\cell\regions.json" `
  --sns-config ".\tmp\mosaic-25.2\scenarios\MaGaWorkloadStudy\sns\sns_config.json" `
  --resource-catalog ".\tmp\mosaic-25.2\scenarios\MaGaWorkloadStudy\application\ma_ga_resource_catalog.json" `
  --out-file ".\data\mosaic-study\infrastructure_snapshot.json"
```

### Struttura generale del JSON 10C

```json
{
  "schemaVersion": "0.1",
  "source": {},
  "runtimeRegistrations": {
    "rsus": [],
    "servers": []
  },
  "policies": {},
  "gateways": [],
  "bandwidthPools": [],
  "executionNodes": [],
  "vehicleProfiles": [],
  "v2vPolicy": {},
  "cell": {
    "globalNetwork": {},
    "regions": [],
    "bandwidthMeasurements": []
  },
  "sns": {},
  "validations": {
    "errors": [],
    "warnings": []
  }
}
```

`gateways` copia il catalogo e aggiunge le coordinate runtime della RSU registrata. `bandwidthPools` copia il catalogo e aggiunge:

```text
expectedNominalBandwidthBitsPerSecond
nominalBandwidthValidation = PASS
```

### Validazioni bloccanti 10C

Lo script fallisce se:

```text
un file input non esiste o non e' un file
un JSON non e' valido
non trova RSU_REGISTRATION o SERVER_REGISTRATION
una registrazione runtime e' malformata
timeNs e' invalido o negativo
runtimeId o profile sono vuoti
coordinate RSU non numeriche, non finite o fuori range
una RSU o un server runtime viene registrato piu' di una volta
gateway runtimeId non registrato come RSU
gatewayId/runtimeId/poolId/executionNodeId non sono valorizzati o non sono univoci
gatewayType non e' valorizzato
coverageRadiusMeters <= 0
bandwidthPoolId non riferisce un pool esistente
cellRegionId non riferisce una regione Cell esistente
poolType non e' valorizzato
nominalBandwidthBitsPerSecond <= 0
executionNodes[*].type non e' EDGE o CLOUD
EDGE senza gatewayId o con gatewayId inesistente
CPU EDGE/CLOUD <= 0
ritardi base EDGE/CLOUD mancanti, non finiti o negativi
nessun CLOUD presente
CLOUD senza mosaicServerRuntimeId registrato
CLOUD accessPolicy diverso da THROUGH_ACTIVE_GATEWAY
policies.cloudAccess diverso da THROUGH_ACTIVE_GATEWAY
policies gatewaySelection/gatewayPoolBandwidth/bandwidthResidualPolicy non valorizzate
nominalBandwidthBitsPerSecond di un pool gateway non coincide con min(uplink, downlink) della regione Cell
```

Lo script non corregge automaticamente i JSON e non inventa valori mancanti.

### Warning non bloccanti 10C

Lo script completa l'export ma registra warning se:

```text
la description del catalogo cita MaGaMosaicStudy mentre il catalogo e' usato in MaGaWorkloadStudy
il server runtime del CLOUD ha profilo WeatherServer
v2vPolicy.candidatePolicy = DIRECT_SINGLEHOP_ONLY ma sns.maximumTtl > 1
vehicleProfiles[*].localCpuCyclesPerSecond e' null
v2vPolicy.nominalBandwidthBitsPerSecond e' null
v2vPolicy.bandwidthSource = CONFIGURED_VALUE_TO_BE_CALIBRATED
vehicleProfiles[*].cpuSource = CONFIGURED_VALUE_TO_BE_CALIBRATED
```

Questi warning non vengono risolti nella Fase 10C.

### Parametri ancora null

Sono preservati intenzionalmente:

```text
vehicleProfiles[*].localCpuCyclesPerSecond = null
v2vPolicy.nominalBandwidthBitsPerSecond = null
```

Non vengono sostituiti con numeri arbitrari.

### Limiti 10C

La Fase 10C:

```text
non calcola banda residua
non usa bandwidthMeasurements per sottrarre traffico
non seleziona gateway attivi
non costruisce access link
non costruisce candidati EDGE
non costruisce candidati CLOUD
non costruisce candidati V2V
non genera SystemSnapshot
non invoca il core Java
non implementa il bridge live
```

Il file `infrastructure_snapshot.json` e' un artefatto statico validato per le fasi successive dell'exporter offline.

## Stato complessivo

Le fasi implementate producono artefatti intermedi:

```text
data/mosaic-study/task_stream.csv
data/mosaic-study/vehicle_state_stream.csv
data/mosaic-study/infrastructure_snapshot.json
```

Questi file saranno input per le fasi successive dell'exporter offline. Non costituiscono ancora snapshot MA-GA completi.
