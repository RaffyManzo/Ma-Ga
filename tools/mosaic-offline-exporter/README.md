# MOSAIC Offline Exporter

Questo folder contiene i primi componenti diagnostici dell'exporter offline Eclipse MOSAIC -> MA-GA.

Gli script usano solo la standard library Python, non invocano il core Java MA-GA, non modificano gli scenari MOSAIC e non implementano il bridge live.

## Fase 10A - Aggregazione del workload diagnostico

Script:

```text
export_task_stream.py
```

Scopo: leggere i log applicativi di `MaGaWorkloadDiagnosticApp` ed esportare uno stream CSV dei task computazionali sintetici generati durante la simulazione.

### Input 10A

```text
tmp/mosaic-25.2/logs/log-20260603-123856-MaGaWorkloadStudy/apps/
```

Lo script cerca ricorsivamente:

```text
MaGaWorkloadDiagnosticApp.log
```

ed estrae solo payload:

```text
TASK_ACTIVATION|...
```

### Output 10A

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

### Comando PowerShell 10A

```powershell
python .\tools\mosaic-offline-exporter\export_task_stream.py `
  --workload-log-root ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\apps" `
  --out-file ".\data\mosaic-study\task_stream.csv"
```

Su Windows, se `python` non e' nel PATH, usare `py` con gli stessi argomenti.

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
sourceVehicleId == cartella veicolo che contiene il log
```

Limiti:

```text
non genera SystemSnapshot
non assegna task alle finestre MA-GA
non invoca il core Java
non implementa il bridge live
non consuma i task dopo una decisione MA-GA
non legge dinamicamente ma_ga_workload_config.json
```

## Fase 10B - Normalizzazione dello stato dei veicoli

Script:

```text
export_vehicle_state_stream.py
```

Scopo: normalizzare gli stati veicolari osservati da `output.csv`.

### Input 10B

```text
tmp/mosaic-25.2/logs/log-20260603-123856-MaGaWorkloadStudy/output.csv
```

Eventi usati:

```text
VEHICLE_REGISTRATION
VEHICLE_UPDATES
```

`VEHICLE_REGISTRATION` viene usato per validare il ciclo di vita minimo del veicolo. Il CSV contiene una riga per ogni `VEHICLE_UPDATES` valido.

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

Gli altri campi non ricevono significati non verificati.

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
active = true per ogni stato esportato
projectedX e projectedY restano vuoti
```

La conversione cartesiana, le distanze metriche precise e `localCpu` saranno gestite in fasi successive.

### Comando PowerShell 10B

```powershell
python .\tools\mosaic-offline-exporter\export_vehicle_state_stream.py `
  --input-file ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\output.csv" `
  --out-file ".\data\mosaic-study\vehicle_state_stream.csv"
```

### Validazioni 10B

Lo script fallisce se:

```text
il file input non esiste
non trova VEHICLE_REGISTRATION o VEHICLE_UPDATES
una registrazione o un update ha campi insufficienti
timeNs non e' intero o e' negativo
vehicleId e' vuoto
speed, heading, latitude, longitude o altitude non sono numerici o non sono finiti
speed < 0
heading < 0 o heading >= 360
coordinate fuori intervallo
un veicolo viene registrato piu' di una volta
un update compare prima della registrazione
lo stesso veicolo ha piu' stati allo stesso timeNs
il tempo degli eventi rilevanti diminuisce
```

Limiti:

```text
non calcola projectedX/projectedY
non calcola distanze
non aggiunge localCpu
non costruisce VehicleSnapshot
non assembla SystemSnapshot
non implementa il bridge live
non modella uscite dei veicoli, perche' non osservate nella run diagnostica corrente
```

## Fase 10C - Normalizzazione e validazione dell'infrastruttura

Script:

```text
export_infrastructure_snapshot.py
```

Scopo: costruire una fotografia statica validata dell'infrastruttura configurata per la run workload.

### Input 10C

```text
tmp/mosaic-25.2/logs/log-20260603-123856-MaGaWorkloadStudy/output.csv
tmp/mosaic-25.2/scenarios/MaGaWorkloadStudy/cell/cell_config.json
tmp/mosaic-25.2/scenarios/MaGaWorkloadStudy/cell/network.json
tmp/mosaic-25.2/scenarios/MaGaWorkloadStudy/cell/regions.json
tmp/mosaic-25.2/scenarios/MaGaWorkloadStudy/sns/sns_config.json
tmp/mosaic-25.2/scenarios/MaGaWorkloadStudy/application/ma_ga_resource_catalog.json
```

Eventi letti da `output.csv`:

```text
RSU_REGISTRATION
SERVER_REGISTRATION
```

### Output 10C

```text
data/mosaic-study/infrastructure_snapshot.json
```

Struttura generale:

```text
schemaVersion
source
runtimeRegistrations
policies
gateways
bandwidthPools
executionNodes
vehicleProfiles
v2vPolicy
cell
sns
validations
```

### Comando PowerShell 10C

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

### Validazioni 10C

Lo script valida:

```text
RSU e server registrati a runtime
mapping runtimeId -> gatewayId e server cloud
unicita' di gateway, pool e execution node
coverageRadiusMeters > 0
availableCpuCyclesPerSecond > 0 per EDGE e CLOUD
ritardi base finiti e non negativi
policy cloud THROUGH_ACTIVE_GATEWAY
coerenza dei cellRegionId
nominalBandwidthBitsPerSecond dei pool gateway = min(uplink, downlink) della regione Cell
```

Warning non bloccanti preservano valori ancora irrisolti:

```text
vehicleProfiles[*].localCpuCyclesPerSecond = null
v2vPolicy.nominalBandwidthBitsPerSecond = null
CONFIGURED_VALUE_TO_BE_CALIBRATED
```

Limiti:

```text
non calcola banda residua
non usa bandwidthMeasurements per sottrarre traffico
non seleziona gateway attivi
non costruisce access link
non costruisce candidati EDGE, CLOUD o V2V
non genera SystemSnapshot
non invoca il core Java
non implementa il bridge live
```

## Fase 10D - Diagnostica Cell su run storiche

Script:

```text
export_cell_network_diagnostics.py
```

Scopo: validare un parser per handover regionali Cell e misure aggregate/raw Cell usando run diagnostiche storiche, senza mescolare questi dati con la timeline della run workload.

La run workload:

```text
tmp/mosaic-25.2/logs/log-20260603-123856-MaGaWorkloadStudy/
```

non contiene `CELLULAR_HANDOVER` e i file `bandwidthMeasurements/ALL#ALL#ALL#Dn.csv` e `ALL#ALL#ALL#Up.csv` hanno solo intestazione. Questo e' coerente con lo scenario diagnostico workload e non va compensato inventando dati.

La run storica selezionata per la diagnostica Cell e':

```text
tmp/mosaic-25.2/logs/log-20260602-172233-MaGaCellStudy/
```

Questa run contiene sia handover sia misure bandwidth popolate, quindi non serve usare sorgenti miste.

### Input 10D

```text
tmp/mosaic-25.2/logs/log-20260602-172233-MaGaCellStudy/output.csv
tmp/mosaic-25.2/logs/log-20260602-172233-MaGaCellStudy/bandwidthMeasurements/
data/mosaic-study/infrastructure_snapshot.json
```

### Output 10D

```text
data/mosaic-study/diagnostics/cell/cell_handover_stream.csv
data/mosaic-study/diagnostics/cell/cell_bandwidth_raw_stream.csv
data/mosaic-study/diagnostics/cell/cell_diagnostic_metadata.json
```

Non viene generato:

```text
data/mosaic-study/cell_bandwidth_stream.csv
```

finche' l'unita' dei valori Cell non viene dimostrata in modo esplicito e non ambiguo tramite file locali, sorgenti, JAR, Javadoc o documentazione disponibile.

### Handover stream 10D

Input atteso:

```text
CELLULAR_HANDOVER;timeNs;vehicleId;previousRegion;currentRegion
```

Output:

```text
timeNs
timeSeconds
vehicleId
previousRegion
currentRegion
eventType
sourceFile
```

Classificazione:

```text
previousRegion nullo e currentRegion valorizzato -> REGISTRATION
previousRegion valorizzato e currentRegion valorizzato e diverso -> REGION_TRANSITION
previousRegion valorizzato e currentRegion nullo -> REMOVAL
```

Valori null accettati:

```text
null
NULL
None
stringa vuota
```

### Bandwidth raw stream 10D

Formato osservato:

```text
time,region_north_normal,region_central_degraded,globalNetwork
```

Output:

```text
sourceFile
direction
timeRaw
regionId
trafficObservedRaw
unitStatus
```

`direction` deriva dal nome file:

```text
ALL#ALL#ALL#Up.csv -> UPLINK
ALL#ALL#ALL#Dn.csv -> DOWNLINK
```

`unitStatus` resta:

```text
UNRESOLVED
```

finche' l'unita' non e' provata. Per questo motivo lo script non calcola ancora:

```text
trafficObservedBitsPerSecond
residualCapacityBitsPerSecond
```

### Comando PowerShell 10D

```powershell
python .\tools\mosaic-offline-exporter\export_cell_network_diagnostics.py `
  --handover-output-csv ".\tmp\mosaic-25.2\logs\log-20260602-172233-MaGaCellStudy\output.csv" `
  --bandwidth-measurements-dir ".\tmp\mosaic-25.2\logs\log-20260602-172233-MaGaCellStudy\bandwidthMeasurements" `
  --infrastructure-snapshot ".\data\mosaic-study\infrastructure_snapshot.json" `
  --handover-out-file ".\data\mosaic-study\diagnostics\cell\cell_handover_stream.csv" `
  --bandwidth-raw-out-file ".\data\mosaic-study\diagnostics\cell\cell_bandwidth_raw_stream.csv" `
  --metadata-out-file ".\data\mosaic-study\diagnostics\cell\cell_diagnostic_metadata.json"
```

### Validazioni 10D

Handover:

```text
file esistente
almeno una riga CELLULAR_HANDOVER
almeno 5 campi
timeNs intero e >= 0
vehicleId valorizzato
regioni coerenti con infrastructure_snapshot.json o globalNetwork
nessun duplicato
tempo non decrescente
```

Bandwidth raw:

```text
cartella esistente
almeno un CSV
intestazione presente
prima colonna time
almeno una regione
righe con stesso numero di colonne dell'intestazione
valori numerici finiti e >= 0
tempo non decrescente per file
nessun record duplicato
regionId riconosciuto
```

Limiti:

```text
gli output 10D sono diagnostici
gli output 10D non sono allineati alla timeline MaGaWorkloadStudy
gli output 10D non devono essere usati direttamente per assemblare snapshot finali della run workload
la banda viene esportata raw se l'unita' non e' dimostrata
non viene calcolata banda residua
non viene attribuita una misura aggregata a un gateway fisico
```

## Fase 10E - Preview dei gateway attivi sulla run workload

Script:

```text
export_access_link_preview.py
```

Scopo: costruire una preview diagnostica dei gateway disponibili e attivi per ogni stato veicolare della run workload.

La 10E e' indipendente dagli handover regionali Cell. Gli handover regionali descrivono cambi di condizioni Cell, non cambi fisici di RSU. Il gateway attivo deriva dalla geometria.

### Input 10E

Obbligatori:

```text
data/mosaic-study/vehicle_state_stream.csv
data/mosaic-study/infrastructure_snapshot.json
```

Facoltativo, solo se disponibile uno stream handover allineato alla stessa run degli stati veicolari:

```text
cell_handover_stream.csv
```

Se lo stream handover viene fornito, viene usato solo per controlli diagnostici. Non seleziona direttamente una RSU.

Lo stream prodotto dalla Fase 10D corrente deriva da una run storica Cell e non e' allineato alla timeline `MaGaWorkloadStudy`; non deve quindi essere passato alla 10E della run workload corrente.

### Output 10E

```text
data/mosaic-study/access_link_preview.csv
```

Colonne:

```text
timeNs
timeSeconds
vehicleId
gatewayId
runtimeGatewayId
distanceMeters
coverageRadiusMeters
active
available
cellRegionId
bandwidthPoolId
```

Viene prodotta una riga per ogni combinazione:

```text
stato veicolo x gateway
```

### Policy geometrica 10E

Poiche' `projectedX` e `projectedY` sono ancora vuoti, la distanza e' calcolata in modo diagnostico con formula Haversine:

```text
earthRadiusMeters = 6371000.0
distancePolicy = HAVERSINE_FROM_LAT_LON_DIAGNOSTIC
```

Regola:

```text
available = distanceMeters <= coverageRadiusMeters
active = gateway disponibile piu' vicino per (timeNs, vehicleId)
tie-break = gatewayId lessicograficamente minore
```

Se nessun gateway e' disponibile, nessun link viene marcato `active`.

### Comando PowerShell 10E

```powershell
python .\tools\mosaic-offline-exporter\export_access_link_preview.py `
  --vehicle-state-file ".\data\mosaic-study\vehicle_state_stream.csv" `
  --infrastructure-snapshot ".\data\mosaic-study\infrastructure_snapshot.json" `
  --out-file ".\data\mosaic-study\access_link_preview.csv"
```

Con handover opzionale allineato alla stessa run, non con gli output storici della 10D corrente:

```powershell
python .\tools\mosaic-offline-exporter\export_access_link_preview.py `
  --vehicle-state-file ".\data\mosaic-study\vehicle_state_stream.csv" `
  --infrastructure-snapshot ".\data\mosaic-study\infrastructure_snapshot.json" `
  --cell-handover-stream ".\data\mosaic-study\cell_handover_stream.csv" `
  --out-file ".\data\mosaic-study\access_link_preview.csv"
```

### Validazioni 10E

Lo script valida:

```text
file input esistenti
CSV e JSON validi
campi obbligatori presenti
coordinate numeriche e finite
gatewayId univoci
runtimeGatewayId univoci
coverageRadiusMeters > 0
pool referenziati esistenti
regioni referenziate esistenti
distanceMeters >= 0
massimo un gateway active per veicolo e timeNs
ogni active e' anche available
```

Se `--cell-handover-stream` non e' fornito, lo script emette il warning:

```text
cell handover stream not provided; access links are derived from geometry only
```

Limiti:

```text
projectedX e projectedY restano vuoti
la distanza Haversine e' una baseline diagnostica
gli handover regionali non equivalgono a handover fisici di RSU
non vengono costruiti candidati EDGE o CLOUD
non vengono costruiti candidati V2V
non viene generato SystemSnapshot
non viene invocato il core Java
non viene implementato il bridge live
```
