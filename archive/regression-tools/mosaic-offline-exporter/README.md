# MOSAIC Offline Exporter

Questo folder contiene la pipeline diagnostica offline Eclipse MOSAIC -> MA-GA. Gli script usano solo la standard library Python, non invocano il core Java MA-GA, non modificano gli scenari MOSAIC e non implementano il bridge live.

## Struttura canonica

```text
data/docs/mosaic-study/       documentazione definitiva delle fasi
data/mosaic-scenarios/        sorgenti versionabili degli scenari MOSAIC
data/mosaic-study/            CSV, JSON e diagnostica generati
data/snapshots/               snapshot JSON finali generati dalla Fase 10I
tools/mosaic-offline-exporter/ exporter offline
tmp/mosaic-25.2/              deployment locale, log ed eseguibili temporanei
```

La baseline integrata definitiva usata per la pipeline 10A-10G e':

```text
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/
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
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/apps/
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
  --workload-log-root ".\tmp\mosaic-25.2\logs\log-20260604-220216-MaGaIntegratedStudy\apps" `
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
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/output.csv
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
  --input-file ".\tmp\mosaic-25.2\logs\log-20260604-220216-MaGaIntegratedStudy\output.csv" `
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
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/output.csv
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
  --output-csv ".\tmp\mosaic-25.2\logs\log-20260604-220216-MaGaIntegratedStudy\output.csv" `
  --cell-config ".\data\mosaic-scenarios\MaGaIntegratedStudy\cell\cell_config.json" `
  --network-config ".\data\mosaic-scenarios\MaGaIntegratedStudy\cell\network.json" `
  --regions-config ".\data\mosaic-scenarios\MaGaIntegratedStudy\cell\regions.json" `
  --sns-config ".\data\mosaic-scenarios\MaGaIntegratedStudy\sns\sns_config.json" `
  --resource-catalog ".\data\mosaic-scenarios\MaGaIntegratedStudy\application\ma_ga_resource_catalog.json" `
  --out-file ".\data\mosaic-study\infrastructure_snapshot.json"
```

Lo script valida RSU e server registrati, alias runtime -> MA-GA, unicita' di gateway/pool/nodi, coverage, CPU, ritardi, policy cloud e `nominalBandwidthBitsPerSecond = min(uplink, downlink)` dei pool gateway.

Valori diagnostici provvisori restano marcati nel catalogo come sintetici:

```text
vehicleProfiles[car_default].localCpuCyclesPerSecond = 4000000000
vehicleProfiles[car_default].cpuSource = DIAGNOSTIC_SYNTHETIC_VALUE
v2vPolicy.nominalBandwidthBitsPerSecond = 10000000
v2vPolicy.bandwidthSource = DIAGNOSTIC_SYNTHETIC_VALUE
calibrationStatus = TO_BE_REPLACED_DURING_RESOURCE_CALIBRATION
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
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/output.csv
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/bandwidthMeasurements/
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
  --output-csv ".\tmp\mosaic-25.2\logs\log-20260604-220216-MaGaIntegratedStudy\output.csv" `
  --bandwidth-measurements-dir ".\tmp\mosaic-25.2\logs\log-20260604-220216-MaGaIntegratedStudy\bandwidthMeasurements" `
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

Questa fase estende la pipeline offline con preview diagnostiche dei candidati `LOCAL` e dei candidati `VEHICLE` per V2V diretto single-hop. La baseline precedente non conteneva `ADHOC_CONFIGURATION`; per questo e' stato aggiunto il tool minimale `tools/mosaic-adhoc-radio-diagnostic/`, che abilita una radio ad-hoc senza inviare messaggi V2X. La nuova baseline contiene 12 eventi `SINGLE` e 12 eventi `OFF`, sufficienti a completare la validazione V2V.

La 10G non genera `SystemSnapshot`, non invoca il core Java MA-GA, non implementa il replay e non procede alla Fase 10H.

Input:

```text
data/mosaic-study/vehicle_state_stream.csv
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/output.csv
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
  --output-csv ".\tmp\mosaic-25.2\logs\log-20260604-220216-MaGaIntegratedStudy\output.csv" `
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

Se una futura baseline non contenesse eventi `ADHOC_CONFIGURATION`, l'exporter genererebbe comunque i candidati `LOCAL`, creerebbe CSV V2V vuoti con intestazione e registrerebbe nel JSON di validazione che la parte V2V e' stata saltata per mancanza di stato radio osservabile. Non assume mai che tutti i veicoli abbiano radio sempre attiva.

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
nessun bandwidthPoolId e' ambiguo allo stesso timestamp
phase10gStatus = COMPLETED solo se LOCAL, V2V e pool sono validi
readyForPhase10H = true solo se la pipeline 10G e' completa
```

Risultati della baseline `log-20260604-220216-MaGaIntegratedStudy`:

```text
vehicleStatesRead = 1824
localCandidatesExported = 1824
radioEventsRead = 24
vehiclesWithRadioEvents = 12
v2vGenerationStatus = COMPLETED
v2vCandidatesExported = 13206
v2vPoolsExported = 6603
futureLookAheadViolations = 0
phase10gStatus = COMPLETED
readyForPhase10H = true
```

Limiti:

```text
la CPU locale e la banda V2V sono sintetiche e provvisorie
la distanza usa Haversine su latitudine/longitudine
SNS non espone una banda residua allocabile per coppia diretta
la Fase 10G non produce snapshot finali
la Fase 10I usa questi dati come input e non viene eseguita dalla 10G
il bridge live non e' implementato
```

## Fase 10H - Assegnazione diagnostica dei task alle finestre MA-GA

La Fase 10H associa i task MOSAIC esportati in `task_stream.csv` a una timeline esplicita di finestre diagnostiche MA-GA. La fase non genera `SystemSnapshot`, non invoca il core Java, non modifica `TemporalWindowManager` e non procede alla Fase 10I.

Script:

```text
generate_fixed_optimization_window_timeline.py
export_window_task_assignment.py
```

Il primo script genera una timeline esplicita. Il secondo legge `task_stream.csv`, legge la timeline, verifica la baseline dichiarata nei metadata e assegna ogni task alla prima finestra valida.

Input:

```text
data/mosaic-study/task_stream.csv
data/mosaic-study/optimization_window_timeline.csv
data/mosaic-study/diagnostics/cell/integrated_baseline_metadata.json
```

Output:

```text
data/mosaic-study/optimization_window_timeline.csv
data/mosaic-study/window_task_assignment.csv
data/mosaic-study/diagnostics/phase_10h_validation.json
```

Timeline diagnostica:

```text
timelinePolicy = FIXED_INTERVAL_DIAGNOSTIC
simulationStartSeconds = 0
simulationEndSeconds = 180
windowIntervalSeconds = 5
calibrationStatus = TO_BE_REPLACED_OR_DRIVEN_BY_TEMPORAL_WINDOW_MANAGER
```

La timeline fissa da 5 secondi e' una configurazione diagnostica. Non rappresenta ancora la durata adattiva delle finestre prevista dalla formalizzazione del MA-GA.

Generazione timeline:

```powershell
python .\tools\mosaic-offline-exporter\generate_fixed_optimization_window_timeline.py `
  --simulation-start-seconds 0 `
  --simulation-end-seconds 180 `
  --window-interval-seconds 5 `
  --output-file ".\data\mosaic-study\optimization_window_timeline.csv"
```

Assegnazione task:

```powershell
python .\tools\mosaic-offline-exporter\export_window_task_assignment.py `
  --task-stream-file ".\data\mosaic-study\task_stream.csv" `
  --timeline-file ".\data\mosaic-study\optimization_window_timeline.csv" `
  --baseline-metadata-file ".\data\mosaic-study\diagnostics\cell\integrated_baseline_metadata.json" `
  --expected-source-run "log-20260604-220216-MaGaIntegratedStudy" `
  --output-file ".\data\mosaic-study\window_task_assignment.csv" `
  --validation-out-file ".\data\mosaic-study\diagnostics\phase_10h_validation.json"
```

Policy di assegnazione:

```text
taskAssignmentPolicy = PENDING_TASKS_AT_NEXT_OPTIMIZATION_WINDOW
consumptionPolicy = REMOVE_AFTER_EXPORT_TO_WINDOW
boundaryPolicy = INITIAL_INTERVAL_CLOSED_THEN_LEFT_OPEN_RIGHT_CLOSED
```

Regola temporale:

```text
prima finestra: [0, 5]
finestre successive: (5, 10], (10, 15], ..., (175, 180]
```

L'assegnazione usa `activationTimeNs` e confronti su nanosecondi interi. Un task sul confine entra nella finestra con lo stesso timestamp.

Risultati della baseline `log-20260604-220216-MaGaIntegratedStudy`:

```text
windowsGenerated = 36
tasksRead = 682
tasksAssigned = 682
duplicateTaskIdsInInput = 0
duplicateAssignments = 0
tasksLost = 0
tasksAssignedBeforeActivation = 0
tasksAssignedToNonEarliestWindow = 0
tasksAtExactBoundary = 117
tasksAfterSimulationEnd = 0
negativeActivationTimes = 0
emptyWindows = 1
minimumAssignmentDelayNs = 0
maximumAssignmentDelayNs = 4000000000
averageAssignmentDelayNs = 2127565982.4046922
phase10hStatus = COMPLETED
readyForPhase10I = true
```

Limiti:

```text
la Fase 10H non produce SystemSnapshot JSON
la Fase 10H non invoca il core MA-GA
la Fase 10H non modifica TemporalWindowManager
la Fase 10H non simula completamento, migrazione o persistenza dei task remoti
```

Prossimo passo:

```text
Fase 10I - composizione diagnostica dei SystemSnapshot JSON
```

## Fase 10I-pre - Contratto snapshot per veicoli senza gateway

Prima della composizione dei SystemSnapshot JSON finali, il contratto Java e'
stato riallineato per rappresentare veicoli presenti nella simulazione ma privi
di accesso infrastrutturale attivo.

Regole aggiornate:

```text
active access link per veicolo = 0 oppure 1
piu' di 1 link attivo = snapshot invalido
LOCAL = nessun gateway richiesto
VEHICLE / V2V = nessun gateway infrastrutturale richiesto
EDGE / CLOUD = gateway attivo e risolvibile ancora obbligatorio
```

Questa sottofase non introduce placeholder e non genera ancora snapshot MOSAIC
finali. Il sistema e' pronto per riprendere la Fase 10I usando i dati 10A-10H
gia' validati.

## Fase 10I-pre2 - Normalizzazione cartesiana MOSAIC/SUMO

La Fase 10I-pre2 normalizza le coordinate geografiche MOSAIC in coordinate
cartesiane metriche coerenti con la rete SUMO. Non modifica gli input originali
e non modifica il core Java.

Script:

```text
export_projected_mosaic_coordinates.py
```

Input:

```text
data/mosaic-study/vehicle_state_stream.csv
data/mosaic-study/infrastructure_snapshot.json
data/mosaic-scenarios/MaGaIntegratedStudy/sumo/Barnim.net.xml
```

Output:

```text
data/mosaic-study/vehicle_state_stream_projected.csv
data/mosaic-study/infrastructure_snapshot_projected.json
data/mosaic-study/diagnostics/phase_10i_pre2_projection_validation.json
```

Comando:

```powershell
python .\tools\mosaic-offline-exporter\export_projected_mosaic_coordinates.py `
  --vehicle-state-file ".\data\mosaic-study\vehicle_state_stream.csv" `
  --infrastructure-file ".\data\mosaic-study\infrastructure_snapshot.json" `
  --sumo-network-file ".\data\mosaic-scenarios\MaGaIntegratedStudy\sumo\Barnim.net.xml" `
  --vehicle-state-out-file ".\data\mosaic-study\vehicle_state_stream_projected.csv" `
  --infrastructure-out-file ".\data\mosaic-study\infrastructure_snapshot_projected.json" `
  --validation-out-file ".\data\mosaic-study\diagnostics\phase_10i_pre2_projection_validation.json"
```

Policy:

```text
projectionPolicy = SUMO_NET_XML_UTM_WGS84_WITH_NET_OFFSET
projectionUtility = STANDARD_LIBRARY_UTM_WGS84_FROM_SUMO_PROJ_PARAMETER
```

La proiezione deriva da `Barnim.net.xml`:

```text
projParameter = +proj=utm +zone=33 +ellps=WGS84 +datum=WGS84 +units=m +no_defs
netOffset = -395635.35,-5826456.24
```

L'utility SUMO locale `sumolib` e' stata ispezionata. La sua semantica e'
`UTM + netOffset`, ma nell'ambiente locale la conversione richiede `pyproj`,
non installato. Per evitare dipendenze esterne, lo script implementa in standard
library la conversione UTM WGS84 derivata dal `projParameter` SUMO.

Controlli:

```text
coordinate veicolari finite e complete
gateway proiettati
round-trip lat/lon -> x/y -> lat/lon
confronto diagnostico distanza proiettata vs Haversine
phase10iPre2Status = COMPLETED solo se errors e' vuoto
readyForPhase10I = true solo se la proiezione e' validata
```

Risultati della baseline:

```text
vehicleStatesProjected = 1824
gatewaysProjected = 2
roundTripValidationSamples = 1826
roundTripMaximumErrorMeters = 0.00037287069228226144
distanceComparisonSamples = 3648
maximumProjectedVsHaversineDifferenceMeters = 3.100796595092106
phase10iPre2Status = COMPLETED
readyForPhase10I = true
```

## Fase 10I - Generazione SystemSnapshot JSON

La Fase 10I assembla gli stream validati in snapshot JSON compatibili con il
loader e con `SnapshotValidator`. Non esegue il GA, non implementa replay
`JSON_SEQUENCE`, non implementa replay `JSON_TIME` e non modifica il core Java.

Script:

```text
export_system_snapshots.py
```

Input principali:

```text
optimization_window_timeline.csv
window_task_assignment.csv
vehicle_state_stream_projected.csv
infrastructure_snapshot_projected.json
cell_bandwidth_stream.csv
access_link_preview.csv
remote_candidate_preview.csv
local_candidate_preview.csv
v2v_candidate_preview.csv
v2v_bandwidth_pool_preview.csv
```

Output:

```text
data/snapshots/mosaic-generated/snapshot_*.json
data/mosaic-study/snapshot_manifest.csv
data/mosaic-study/diagnostics/phase_10i_validation.json
```

Comando:

```powershell
python .\tools\mosaic-offline-exporter\export_system_snapshots.py `
  --timeline-file ".\data\mosaic-study\optimization_window_timeline.csv" `
  --window-task-assignment-file ".\data\mosaic-study\window_task_assignment.csv" `
  --vehicle-state-file ".\data\mosaic-study\vehicle_state_stream_projected.csv" `
  --infrastructure-file ".\data\mosaic-study\infrastructure_snapshot_projected.json" `
  --cell-bandwidth-file ".\data\mosaic-study\cell_bandwidth_stream.csv" `
  --access-link-file ".\data\mosaic-study\access_link_preview.csv" `
  --remote-candidate-file ".\data\mosaic-study\remote_candidate_preview.csv" `
  --local-candidate-file ".\data\mosaic-study\local_candidate_preview.csv" `
  --v2v-candidate-file ".\data\mosaic-study\v2v_candidate_preview.csv" `
  --v2v-pool-file ".\data\mosaic-study\v2v_bandwidth_pool_preview.csv" `
  --baseline-metadata-file ".\data\mosaic-study\diagnostics\cell\integrated_baseline_metadata.json" `
  --phase-10g-validation-file ".\data\mosaic-study\diagnostics\phase_10g_validation.json" `
  --phase-10h-validation-file ".\data\mosaic-study\diagnostics\phase_10h_validation.json" `
  --phase-10i-pre-validation-file ".\data\mosaic-study\diagnostics\phase_10i_pre_snapshot_contract_validation.json" `
  --phase-10i-pre2-validation-file ".\data\mosaic-study\diagnostics\phase_10i_pre2_projection_validation.json" `
  --expected-source-run "log-20260604-220216-MaGaIntegratedStudy" `
  --output-dir ".\data\snapshots\mosaic-generated" `
  --manifest-out-file ".\data\mosaic-study\snapshot_manifest.csv" `
  --validation-out-file ".\data\mosaic-study\diagnostics\phase_10i_validation.json" `
  --clean-output-dir
```

Policy:

```text
snapshotTimelinePolicy = EXPLICIT_OPTIMIZATION_WINDOW_TIMELINE
activeVehicleSetPolicy = ACTIVE_VEHICLES_FROM_EXACT_LOCAL_CANDIDATES
vehicleLookupPolicy = LATEST_AVAILABLE_STATE_AT_OR_BEFORE_WINDOW
accessLinkLookupPolicy = EXACT_WINDOW_TIMESTAMP
localCandidateLookupPolicy = EXACT_WINDOW_TIMESTAMP
v2vCandidateLookupPolicy = EXACT_WINDOW_TIMESTAMP
remoteCandidateLookupPolicy = EXACT_WINDOW_TIMESTAMP
v2vPoolLookupPolicy = EXACT_WINDOW_TIMESTAMP
gatewayPoolAssemblyPolicy = LATEST_SAFE_AVAILABLE_CELL_BUCKET_PER_GATEWAY_POOL
```

Risultati:

```text
snapshotsGenerated = 36
emptyTaskSnapshots = 1
totalTasksAcrossSnapshots = 682
vehiclesAcrossSnapshots = 369
v2vCandidatesAcrossSnapshots = 2594
edgeCandidatesAcrossSnapshots = 112
cloudCandidatesAcrossSnapshots = 112
futureLookAheadViolations = 0
orphanReferenceViolations = 0
javaLoaderValidationFailures = 0
javaValidatorFailures = 0
phase10iStatus = COMPLETED
readyForPhase10J = true
```

Validazione Java:

```text
tmp/phase10i-validation/Phase10iSnapshotValidationMain.java
```

Il harness temporaneo carica tutti gli snapshot con `SnapshotLoader` e
`JsonSnapshotFolderLoader`, invoca `SnapshotValidator` e non esegue il GA.

Prossimo passo:

```text
Fase 10J - replay JSON_SEQUENCE e JSON_TIME nel core MA-GA
```

## Fase 11 - Esecuzione end-to-end offline

La Fase 11 consolida la pipeline offline MOSAIC -> MA-GA in un solo
orchestratore PowerShell:

```text
run_offline_pipeline.ps1
```

Lo script consuma una run MOSAIC gia' esistente, non riesegue MOSAIC, non
modifica gli scenari e non implementa il bridge live. Esegue in ordine gli
exporter 10A-10I, le validazioni Java, il replay `JSON_SEQUENCE`, il replay
`JSON_TIME` full horizon e genera manifest/diagnostiche Fase 11.

Comando canonico:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-offline-exporter\run_offline_pipeline.ps1 `
  -RepoRoot "." `
  -MosaicRoot ".\tmp\mosaic-25.2" `
  -ScenarioName "MaGaIntegratedStudy" `
  -SourceRun "log-20260604-220216-MaGaIntegratedStudy" `
  -SimulationStartSeconds 0 `
  -SimulationEndSeconds 180 `
  -WindowIntervalSeconds 5 `
  -SafetyMaxSteps 100000 `
  -CleanGeneratedOutputs `
  -VerifyDeterminism
```

Parametri principali:

```text
RepoRoot                 root repository, default "."
MosaicRoot               installazione/log MOSAIC locale, default ".\tmp\mosaic-25.2"
ScenarioName             scenario versionabile sotto data/mosaic-scenarios/
SourceRun                cartella log sotto <MosaicRoot>\logs
SimulationStartSeconds   inizio timeline 10H
SimulationEndSeconds     fine timeline 10H
WindowIntervalSeconds    passo timeline 10H
SafetyMaxSteps           guardrail JSON_TIME full horizon
CleanGeneratedOutputs    abilita pulizia whitelist dei soli output generati
VerifyDeterminism        esegue due pass e confronta gli hash deterministici
```

Stage logici:

```text
00 preflight
01 10A task stream
02 10B vehicle state
03 10C infrastructure
04 10D Cell streams
05 10E access links
06 10F remote candidates
07 10G local/V2V candidates
08 10H timeline
09 10H task assignment
10 10I-pre contract validation
11 10I-pre2 SUMO projection
12 10I SystemSnapshot JSON
13 10J-pre/10J-pre2 replay bootstrap and reporting
14 10J JSON_TIME full horizon
```

Output Fase 11:

```text
data/mosaic-study/diagnostics/phase_11/logs/
data/mosaic-study/diagnostics/phase_11_offline_pipeline_manifest.json
data/mosaic-study/diagnostics/phase_11_artifact_manifest.csv
data/mosaic-study/diagnostics/phase_11_offline_pipeline_validation.json
```

Regole di pulizia:

```text
-CleanGeneratedOutputs elimina solo stream, snapshot, diagnostiche rigenerabili
e log Fase 11 esplicitamente in whitelist.
Non elimina tmp/mosaic-25.2/logs, data/mosaic-scenarios, data/docs o src.
```

Determinismo:

```text
DETERMINISTIC_FROM_INPUTS
    CSV, JSON e snapshot derivati dagli input

RUNTIME_SENSITIVE_DIAGNOSTIC
    log, trace temporali e diagnostiche con runtime/timestamp di esecuzione
```

Risultato canonico della baseline:

```text
phase11Status = COMPLETED
readyForPhase12 = true
deterministicArtifactsCompared = 57
deterministicArtifactMismatches = []
jsonTimeStopReason = FULL_TIME_HORIZON_REACHED
futureLookAheadViolations = 0
```

## Fase 12 - progettazione bridge live

La Fase 11 offline e' completata sulla baseline
`log-20260604-220216-MaGaIntegratedStudy`. La Fase 12 ha completato il design
del bridge live, ma non implementa ancora `MosaicSnapshotBridge` concreto, app
runtime MOSAIC definitive o worker GA. La Fase 13 dovra' introdurre skeleton
runtime, cache causali, adapter Cell live, assembler `SystemSnapshot` e
coordinatore GA/strategy applier.
