# MOSAIC Offline Exporter

Questo folder contiene la pipeline diagnostica offline Eclipse MOSAIC -> MA-GA. Gli script usano solo la standard library Python, non invocano il core Java MA-GA, non modificano gli scenari MOSAIC e non implementano il bridge live.

## Struttura canonica

```text
data/docs/mosaic-study/       documentazione definitiva delle fasi
data/mosaic-scenarios/        sorgenti versionabili degli scenari MOSAIC
data/mosaic-study/            CSV, JSON e diagnostica generati
data/snapshots/               snapshot JSON finali della futura Fase 10I
tools/mosaic-offline-exporter/ exporter offline
tmp/mosaic-25.2/              deployment locale, log ed eseguibili temporanei
```

La baseline integrata definitiva usata per la pipeline 10A-10F e':

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/
```

I file di scenario versionabili sono letti da:

```text
data/mosaic-scenarios/MaGaIntegratedStudy/
```

Su Windows, se `python` non e' nel PATH, usare `py` con gli stessi argomenti.

## Fase 10A - Aggregazione del workload diagnostico

Script:

```text
export_task_stream.py
```

Input:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/apps/
```

Output:

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

Comando:

```powershell
python .\tools\mosaic-offline-exporter\export_task_stream.py `
  --workload-log-root ".\tmp\mosaic-25.2\logs\log-20260603-174645-MaGaIntegratedStudy\apps" `
  --out-file ".\data\mosaic-study\task_stream.csv"
```

Validazioni principali:

```text
cartella input esistente
almeno un MaGaWorkloadDiagnosticApp.log
almeno una riga TASK_ACTIVATION
campi obbligatori presenti una sola volta
valori numerici validi e finiti
deadlineSeconds finito e > 0
taskId non vuoto e univoco
activationTimeNs == activationTimeMs * 1_000_000
taskId == <profileId>__<sourceVehicleId>__t_<activationTimeMs>
sourceVehicleId coerente con la cartella del log
```

Limiti:

```text
non genera SystemSnapshot
non assegna task alle finestre MA-GA
non consuma i task dopo una decisione MA-GA
non invoca il core Java
non implementa il bridge live
```

## Fase 10B - Normalizzazione dello stato dei veicoli

Script:

```text
export_vehicle_state_stream.py
```

Input:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/output.csv
```

Eventi letti:

```text
VEHICLE_REGISTRATION
VEHICLE_UPDATES
```

`VEHICLE_REGISTRATION` valida il ciclo di vita minimo. Ogni `VEHICLE_UPDATES` valido produce una riga.

Output:

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
active = true
projectedX e projectedY restano vuoti
```

Comando:

```powershell
python .\tools\mosaic-offline-exporter\export_vehicle_state_stream.py `
  --input-file ".\tmp\mosaic-25.2\logs\log-20260603-174645-MaGaIntegratedStudy\output.csv" `
  --out-file ".\data\mosaic-study\vehicle_state_stream.csv"
```

Limiti:

```text
non calcola coordinate cartesiane
non calcola distanze
non aggiunge localCpu
non costruisce VehicleSnapshot
non assembla SystemSnapshot
non implementa il bridge live
```

## Fase 10C - Normalizzazione e validazione dell'infrastruttura

Script:

```text
export_infrastructure_snapshot.py
```

Input:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/output.csv
data/mosaic-scenarios/MaGaIntegratedStudy/cell/cell_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/cell/network.json
data/mosaic-scenarios/MaGaIntegratedStudy/cell/regions.json
data/mosaic-scenarios/MaGaIntegratedStudy/sns/sns_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/application/ma_ga_resource_catalog.json
```

Output:

```text
data/mosaic-study/infrastructure_snapshot.json
```

Comando:

```powershell
python .\tools\mosaic-offline-exporter\export_infrastructure_snapshot.py `
  --output-csv ".\tmp\mosaic-25.2\logs\log-20260603-174645-MaGaIntegratedStudy\output.csv" `
  --cell-config ".\data\mosaic-scenarios\MaGaIntegratedStudy\cell\cell_config.json" `
  --network-config ".\data\mosaic-scenarios\MaGaIntegratedStudy\cell\network.json" `
  --regions-config ".\data\mosaic-scenarios\MaGaIntegratedStudy\cell\regions.json" `
  --sns-config ".\data\mosaic-scenarios\MaGaIntegratedStudy\sns\sns_config.json" `
  --resource-catalog ".\data\mosaic-scenarios\MaGaIntegratedStudy\application\ma_ga_resource_catalog.json" `
  --out-file ".\data\mosaic-study\infrastructure_snapshot.json"
```

Lo script valida RSU e server registrati, alias runtime -> MA-GA, unicita' di gateway/pool/nodi, coverage, CPU, ritardi, policy cloud e `nominalBandwidthBitsPerSecond = min(uplink, downlink)` dei pool gateway.

Valori intenzionalmente irrisolti restano preservati come warning:

```text
vehicleProfiles[*].localCpuCyclesPerSecond = null
v2vPolicy.nominalBandwidthBitsPerSecond = null
CONFIGURED_VALUE_TO_BE_CALIBRATED
```

Limiti:

```text
non calcola gateway attivi
non costruisce access link
non costruisce candidati EDGE, CLOUD o V2V
non usa bandwidthMeasurements per sottrarre traffico
non genera SystemSnapshot
```

## Fase 10D - Stream Cell integrati

Script:

```text
export_cell_network_streams.py
```

Questa fase usa la baseline integrata definitiva, non le run storiche Cell. Il parser storico resta disponibile in `export_cell_network_diagnostics.py` e produce solo output raw diagnostici sotto `data/mosaic-study/diagnostics/cell/`.

Input:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/output.csv
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/bandwidthMeasurements/
data/mosaic-study/infrastructure_snapshot.json
```

Output:

```text
data/mosaic-study/cell_handover_stream.csv
data/mosaic-study/cell_bandwidth_stream.csv
data/mosaic-study/diagnostics/cell/integrated_baseline_metadata.json
```

Comando:

```powershell
python .\tools\mosaic-offline-exporter\export_cell_network_streams.py `
  --output-csv ".\tmp\mosaic-25.2\logs\log-20260603-174645-MaGaIntegratedStudy\output.csv" `
  --bandwidth-measurements-dir ".\tmp\mosaic-25.2\logs\log-20260603-174645-MaGaIntegratedStudy\bandwidthMeasurements" `
  --infrastructure-snapshot ".\data\mosaic-study\infrastructure_snapshot.json" `
  --handover-out-file ".\data\mosaic-study\cell_handover_stream.csv" `
  --bandwidth-out-file ".\data\mosaic-study\cell_bandwidth_stream.csv" `
  --metadata-out-file ".\data\mosaic-study\diagnostics\cell\integrated_baseline_metadata.json"
```

### Handover

Input:

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

Eventi:

```text
REGISTRATION
REGION_TRANSITION
REMOVAL
```

### Bandwidth

File letti:

```text
ALL#ALL#ALL#Up.csv
ALL#ALL#ALL#Dn.csv
```

Unita':

```text
unitStatus = PROVEN_BITS_PER_SECOND
```

Semantica temporale dimostrata dai bytecode locali MOSAIC:

```text
bucketBoundaryPolicy = START_TIMESTAMP_FOR_INTERVAL
availableFromPolicy = SAFE_AFTER_TIMESTAMP
```

Una riga `time = t` rappresenta il bucket `[t, t + 1)`. Per evitare future look-ahead, il dato e' disponibile solo da `t + 1`.

Output:

```text
measurementTimeSeconds
availableFromTimeSeconds
bucketStartSeconds
bucketEndSeconds
regionId
direction
trafficObservedBitsPerSecond
nominalCapacityBitsPerSecond
residualCapacityBitsPerSecond
residualPolicy
bucketBoundaryPolicy
availableFromPolicy
sourceFile
```

Policy residua diagnostica:

```text
residualPolicy = NOMINAL_MINUS_OBSERVED_DIAGNOSTIC
residualCapacityBitsPerSecond = max(0, nominalCapacityBitsPerSecond - trafficObservedBitsPerSecond)
```

Questa formula e' una baseline diagnostica iniziale, non un modello scientificamente definitivo di allocazione radio.

## Fase 10E - Preview dei gateway attivi

Script:

```text
export_access_link_preview.py
```

Input:

```text
data/mosaic-study/vehicle_state_stream.csv
data/mosaic-study/infrastructure_snapshot.json
data/mosaic-study/cell_handover_stream.csv
```

Gli handover regionali Cell sono usati solo per controlli diagnostici. Non selezionano direttamente una RSU.

Output:

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

Comando:

```powershell
python .\tools\mosaic-offline-exporter\export_access_link_preview.py `
  --vehicle-state-file ".\data\mosaic-study\vehicle_state_stream.csv" `
  --infrastructure-snapshot ".\data\mosaic-study\infrastructure_snapshot.json" `
  --cell-handover-stream ".\data\mosaic-study\cell_handover_stream.csv" `
  --out-file ".\data\mosaic-study\access_link_preview.csv"
```

Policy geometrica:

```text
distancePolicy = HAVERSINE_FROM_LAT_LON_DIAGNOSTIC
earthRadiusMeters = 6371000.0
available = distanceMeters <= coverageRadiusMeters
active = gateway disponibile piu' vicino per (timeNs, vehicleId)
tie-break = gatewayId lessicograficamente minore
```

Limiti:

```text
projectedX e projectedY restano vuoti
gli handover regionali non equivalgono a handover fisici di RSU
non costruisce candidati EDGE, CLOUD o V2V
non genera SystemSnapshot
```

## Fase 10F - Preview diagnostica dei candidati EDGE e CLOUD

Script:

```text
export_remote_candidate_preview.py
```

Input:

```text
data/mosaic-study/access_link_preview.csv
data/mosaic-study/infrastructure_snapshot.json
data/mosaic-study/cell_bandwidth_stream.csv
```

Output:

```text
data/mosaic-study/remote_candidate_preview.csv
```

Comando:

```powershell
python .\tools\mosaic-offline-exporter\export_remote_candidate_preview.py `
  --access-link-file ".\data\mosaic-study\access_link_preview.csv" `
  --infrastructure-snapshot ".\data\mosaic-study\infrastructure_snapshot.json" `
  --cell-bandwidth-stream ".\data\mosaic-study\cell_bandwidth_stream.csv" `
  --out-file ".\data\mosaic-study\remote_candidate_preview.csv"
```

Colonne:

```text
timeNs
timeSeconds
candidateId
sourceVehicleId
executionNodeId
type
availableCpu
availableBandwidth
propagationDelaySeconds
regionalRadioDelaySeconds
nodeBaseDelaySeconds
bandwidthPoolId
gatewayId
runtimeGatewayId
cellRegionId
bandwidthMeasurementTimeSeconds
bandwidthAgeSeconds
uplinkResidualBandwidth
downlinkResidualBandwidth
bandwidthPolicy
bandwidthSource
bandwidthLookupPolicy
bucketBoundaryPolicy
propagationDelayPolicy
```

Regole:

```text
EDGE e' disponibile solo tramite gateway associato in executionNode.gatewayIds
CLOUD e' disponibile tramite il gateway attivo
candidateId = <executionNodeId>_for_<sourceVehicleId>
```

Policy banda:

```text
bandwidthPolicy = MIN_RESIDUAL_CELL_UPLINK_DOWNLINK_DIAGNOSTIC
bandwidthLookupPolicy = LATEST_SAFE_AVAILABLE_CELL_BUCKET
bandwidthSource = CELL_BANDWIDTH_STREAM_RESIDUAL
availableBandwidth = min(uplinkResidualCapacityBitsPerSecond, downlinkResidualCapacityBitsPerSecond)
```

La lookup usa solo misure con:

```text
availableFromTimeSeconds <= timeSeconds del candidato
```

Quindi non usa future look-ahead.

Policy ritardo:

```text
regionalRadioDelaySeconds = max(uplinkDelaySeconds, downlinkUnicastDelaySeconds)
EDGE propagationDelaySeconds = regionalRadioDelaySeconds + basePropagationDelaySeconds
CLOUD propagationDelaySeconds = regionalRadioDelaySeconds + serverBaseDelaySeconds
propagationDelayPolicy = MAX_CELL_UPLINK_DOWNLINK_UNICAST_PLUS_NODE_BASE_DIAGNOSTIC
```

Limiti:

```text
availableBandwidth usa banda residua diagnostica, non allocazione finale
non genera candidati LOCAL
non genera candidati VEHICLE o V2V
non assembla SystemSnapshot
non invoca il core Java
non implementa il bridge live
```

## Fase 10G - Preview diagnostica dei candidati LOCAL e V2V diretti

Script:

```text
export_local_and_v2v_candidate_preview.py
```

Questa fase estende la pipeline offline con preview diagnostiche dei candidati `LOCAL` e, quando lo stato radio e' ricostruibile senza supposizioni, dei candidati `VEHICLE` per V2V diretto single-hop. Non genera `SystemSnapshot`, non invoca il core Java MA-GA, non implementa il replay e non procede alla Fase 10H.

Input:

```text
data/mosaic-study/vehicle_state_stream.csv
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/output.csv
data/mosaic-scenarios/MaGaIntegratedStudy/application/ma_ga_resource_catalog.json
data/mosaic-scenarios/MaGaIntegratedStudy/sns/sns_config.json
```

Output:

```text
data/mosaic-study/local_candidate_preview.csv
data/mosaic-study/v2v_candidate_preview.csv
data/mosaic-study/v2v_bandwidth_pool_preview.csv
data/mosaic-study/diagnostics/phase_10g_validation.json
```

Comando:

```powershell
python .\tools\mosaic-offline-exporter\export_local_and_v2v_candidate_preview.py `
  --vehicle-state-file ".\data\mosaic-study\vehicle_state_stream.csv" `
  --output-csv ".\tmp\mosaic-25.2\logs\log-20260603-174645-MaGaIntegratedStudy\output.csv" `
  --resource-catalog ".\data\mosaic-scenarios\MaGaIntegratedStudy\application\ma_ga_resource_catalog.json" `
  --sns-config ".\data\mosaic-scenarios\MaGaIntegratedStudy\sns\sns_config.json" `
  --local-out-file ".\data\mosaic-study\local_candidate_preview.csv" `
  --v2v-out-file ".\data\mosaic-study\v2v_candidate_preview.csv" `
  --v2v-pool-out-file ".\data\mosaic-study\v2v_bandwidth_pool_preview.csv" `
  --validation-out-file ".\data\mosaic-study\diagnostics\phase_10g_validation.json" `
  --catalogs-updated `
    ".\data\mosaic-scenarios\MaGaIntegratedStudy\application\ma_ga_resource_catalog.json" `
    ".\data\mosaic-scenarios\MaGaIntegratedStudyRequest2x\application\ma_ga_resource_catalog.json" `
    ".\data\mosaic-scenarios\MaGaIntegratedStudyResponse2x\application\ma_ga_resource_catalog.json" `
    ".\data\mosaic-scenarios\MaGaIntegratedStudyFrequency2x\application\ma_ga_resource_catalog.json"
```

Valori diagnostici sintetici letti dal catalogo:

```text
vehicleProfiles[car_default].localCpuCyclesPerSecond = 4000000000
vehicleProfiles[car_default].cpuSource = DIAGNOSTIC_SYNTHETIC_VALUE

v2vPolicy.nominalBandwidthBitsPerSecond = 10000000
v2vPolicy.bandwidthSource = DIAGNOSTIC_SYNTHETIC_VALUE
```

Questi valori non provengono da SUMO, SNS o misure MOSAIC. Sono una configurazione provvisoria con stato:

```text
calibrationStatus = TO_BE_REPLACED_DURING_RESOURCE_CALIBRATION
```

In futuro dovranno essere sostituiti con valori `LITERATURE_BASED` o `CALIBRATED_FROM_SCENARIO`.

Policy `LOCAL`:

```text
candidateId = local_for_<sourceVehicleId>
executionNodeId = sourceVehicleId
type = LOCAL
availableCpu = vehicleProfiles[car_default].localCpuCyclesPerSecond
propagationDelaySeconds = 0
```

Policy V2V diretta:

```text
candidatePolicy = DIRECT_SINGLEHOP_ONLY
radioStateSource = ADHOC_CONFIGURATION
poolPolicy = ONE_SHARED_POOL_PER_UNORDERED_PAIR
poolType = DIRECT_V2V
distancePolicy = HAVERSINE_FROM_LAT_LON_DIAGNOSTIC
propagationDelayPolicy = SNS_SINGLEHOP_MAX_DELAY
```

Il raggio `singlehopRadius` viene letto da `sns_config.json`. Il ritardo conservativo viene letto da `v2vPolicy.conservativePropagationDelaySeconds`. La banda V2V viene letta da `v2vPolicy.nominalBandwidthBitsPerSecond`.

Lo stato radio e' interpretato solo se gli eventi `ADHOC_CONFIGURATION` sono presenti:

```text
SINGLE -> radio ad-hoc attiva
OFF    -> radio ad-hoc disattiva
```

Per ogni timestamp candidato viene usato soltanto l'ultimo evento radio noto con:

```text
eventTime <= candidateTime
```

Se la baseline non contiene eventi `ADHOC_CONFIGURATION`, l'exporter genera comunque i candidati `LOCAL`, crea CSV V2V vuoti con intestazione e registra nel JSON di validazione che la parte V2V e' stata saltata per mancanza di stato radio osservabile. Non assume mai che tutti i veicoli abbiano radio sempre attiva.

Controlli principali:

```text
ogni candidato LOCAL usa CPU letta dal catalogo
ogni candidato LOCAL ha executionNodeId uguale al veicolo sorgente
ogni candidato V2V, quando generato, ha source != target
ogni candidato V2V usa CPU target letta dal profilo car_default
ogni candidato V2V usa banda V2V letta dal catalogo
ogni pool V2V usa poolType DIRECT_V2V
ogni pool e' condiviso per coppia non ordinata
nessun lookup radio usa eventi futuri
nessun candidateId e' duplicato allo stesso timestamp
```

Limiti:

```text
la CPU locale e la banda V2V sono sintetiche e provvisorie
la distanza usa Haversine su latitudine/longitudine
SNS non espone una banda residua allocabile per coppia diretta
la Fase 10G non produce snapshot finali
la Fase 10H non e' implementata
il bridge live non e' implementato
```
