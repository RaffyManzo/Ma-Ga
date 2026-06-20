# Punto 10 - Sviluppo completo della pipeline offline MOSAIC -> MA-GA

> Nota post-audit 2026-06-14: questo documento descrive la pipeline offline e
> il replay JSON costruiti prima dello scenario live finale
> `MaGaLiteratureBasedUrbanStudy`. Resta materiale regressivo e storico. Non
> va usato come descrizione primaria del workflow finale live-state/runtime.

## Scopo del documento

Questo documento accorpa la documentazione delle sottofasi del Punto 10, dalla
costruzione della pipeline offline MOSAIC fino alla validazione completa del
replay `JSON_TIME` sull'orizzonte temporale finale.

Il Punto 10 ha avuto un obiettivo preciso:

```text
MOSAIC / SUMO
    -> stream e preview diagnostiche offline
    -> SystemSnapshot JSON finali
    -> replay JSON_SEQUENCE e JSON_TIME nel core MA-GA
```

Il core MA-GA e' rimasto il consumatore di `SystemSnapshot`. Le sottofasi del
Punto 10 non hanno introdotto un bridge live e non hanno modificato la logica
scientifica del GA, salvo gli allineamenti contrattuali necessari per rendere
validi gli snapshot MOSAIC reali.

## Documenti sorgente accorpati

Il contenuto consolida i documenti:

```text
10A_10F_pipeline_offline_integrata.md
10D_integrated_cell_diagnostic_calibration.md
10F_integrated_offline_exporter_alignment.md
10G_local_and_direct_v2v_candidate_preview.md
10H_window_task_assignment.md
10I_pre_optional_gateway_access_contract_alignment.md
10I_pre2_sumo_projection_alignment.md
10I_system_snapshot_json_generation.md
10J_pre_empty_window_and_replay_start_alignment.md
10J_pre2_optional_gateway_reporting_alignment.md
10J_json_replay_full_horizon_validation.md
```

I documenti originari restano nel repository come storico operativo delle
sottofasi; questo file e' la vista unificata di riferimento.

## Baseline canonica

La baseline finale del Punto 10 e':

```text
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/
sourceRun = log-20260604-220216-MaGaIntegratedStudy
```

Le diagnostiche finali confermano l'allineamento della baseline in:

```text
phase_10g_validation.json
phase_10h_validation.json
phase_10i_pre_snapshot_contract_validation.json
phase_10i_pre2_projection_validation.json
phase_10i_validation.json
phase_10j_pre_replay_bootstrap_validation.json
phase_10j_pre2_optional_gateway_reporting_validation.json
phase_10j_validation.json
```

## Contratto architetturale

Il contratto preservato e':

```text
SystemSnapshot
    -> MA-GA core
    -> strategia di offloading
```

Le fasi 10A-10J hanno lavorato ai dati e al replay:

```text
MOSAIC
    -> exporter offline
    -> CSV / JSON intermedi
    -> preview diagnostiche
    -> SystemSnapshot JSON
    -> replay JSON
```

Sono rimasti fuori scope:

```text
bridge live MOSAIC
modifica fitness
modifica repair
modifica mutation
modifica crossover
ricalibrazione scientifica workload/risorse
valutazione competitiva dell'offloading
```

## Pipeline finale

Gli artefatti finali della pipeline sono:

```text
data/mosaic-study/task_stream.csv
data/mosaic-study/vehicle_state_stream.csv
data/mosaic-study/infrastructure_snapshot.json
data/mosaic-study/cell_handover_stream.csv
data/mosaic-study/cell_bandwidth_stream.csv
data/mosaic-study/access_link_preview.csv
data/mosaic-study/remote_candidate_preview.csv
data/mosaic-study/local_candidate_preview.csv
data/mosaic-study/v2v_candidate_preview.csv
data/mosaic-study/v2v_bandwidth_pool_preview.csv
data/mosaic-study/optimization_window_timeline.csv
data/mosaic-study/window_task_assignment.csv
data/mosaic-study/vehicle_state_stream_projected.csv
data/mosaic-study/infrastructure_snapshot_projected.json
data/mosaic-study/snapshot_manifest.csv
data/mosaic-study/json_time_full_horizon_trace.csv
data/snapshots/mosaic-generated/snapshot_*.json
```

Le diagnostiche principali sono:

```text
data/mosaic-study/diagnostics/phase_10g_validation.json
data/mosaic-study/diagnostics/phase_10h_validation.json
data/mosaic-study/diagnostics/phase_10i_pre_snapshot_contract_validation.json
data/mosaic-study/diagnostics/phase_10i_pre2_projection_validation.json
data/mosaic-study/diagnostics/phase_10i_validation.json
data/mosaic-study/diagnostics/phase_10j_pre_replay_bootstrap_validation.json
data/mosaic-study/diagnostics/phase_10j_pre2_optional_gateway_reporting_validation.json
data/mosaic-study/diagnostics/phase_10j_validation.json
```

## Fasi 10A-10F - Pipeline offline integrata

### Obiettivo

Le fasi 10A-10F hanno costruito la pipeline offline iniziale:

```text
10A task_stream.csv
10B vehicle_state_stream.csv
10C infrastructure_snapshot.json
10D cell_handover_stream.csv + cell_bandwidth_stream.csv
10E access_link_preview.csv
10F remote_candidate_preview.csv
```

La scelta e' stata volutamente offline per:

```text
- isolare errori di parsing e conversione;
- rendere riproducibile la trasformazione;
- separare simulatore e core MA-GA;
- validare incrementalmente ogni sorgente dati.
```

### 10A - Task stream

Da MOSAIC sono stati esportati i task diagnostici generati dai veicoli.

Risultato finale della baseline:

```text
tasksExported = 682
duplicates = 0
```

I task contengono:

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

### 10B - Stati veicolari

La fase ha normalizzato gli stati veicolari MOSAIC:

```text
timeNs
timeSeconds
vehicleId
latitude
longitude
projectedX
projectedY
speedMetersPerSecond
```

Risultato finale:

```text
vehicleStatesRead = 1824
vehicles = 12
```

In questa fase le coordinate `projectedX/projectedY` non erano ancora la
normalizzazione SUMO autorevole; la rettifica e' arrivata in 10I-pre2.

### 10C - Snapshot infrastrutturale

La fase ha esportato l'infrastruttura statica:

```text
2 gateway / RSU
2 pool gateway
2 EDGE
1 CLOUD
```

Il modello mantiene distinte:

```text
gateway RSU fisico
gatewayId logico
runtimeId MOSAIC
regionId Cell
bandwidthPoolId
EDGE execution node
CLOUD execution node
```

### 10D - Handover e banda Cell

La fase ha stabilizzato:

```text
cell_handover_stream.csv
cell_bandwidth_stream.csv
```

Risultati finali:

```text
handover Cell = 48
record banda = 1080
uplink = 540
downlink = 540
```

La semantica adottata e':

```text
bucketBoundaryPolicy = START_TIMESTAMP_FOR_INTERVAL
availableFromPolicy = SAFE_AFTER_TIMESTAMP
bandwidthLookupPolicy = LATEST_SAFE_AVAILABLE_CELL_BUCKET
```

La banda disponibile per pool gateway viene ricostruita in modo conservativo:

```text
availableBandwidth = min(uplinkResidualBandwidth, downlinkResidualBandwidth)
```

### 10E - Access link preview

La fase ha calcolato la disponibilita' diagnostica dei link veicolo-gateway:

```text
access_link_preview.csv
```

Risultato finale:

```text
link valutati = 3648
link attivi = 564
```

Un link attivo rappresenta accesso infrastrutturale disponibile per EDGE/CLOUD.
Non rappresenta una condizione necessaria per LOCAL o V2V.

### 10F - Candidati EDGE e CLOUD

La fase ha prodotto:

```text
remote_candidate_preview.csv
```

Risultato finale:

```text
remoteCandidates = 1128
EDGE = 564
CLOUD = 564
futureLookAhead = 0
```

Policy:

```text
EDGE/CLOUD richiedono un access link attivo e risolvibile
CLOUD resta gateway-aware
nessun placeholder cloud legacy
candidateId source-aware distinto da executionNodeId fisico
```

## Fase 10G - Candidati LOCAL e V2V diretti

### Problema iniziale

La prima baseline integrata non conteneva eventi:

```text
ADHOC_CONFIGURATION
```

Quindi la generazione V2V e' stata correttamente bloccata:

```text
radioEventsRead = 0
v2vGenerationStatus = SKIPPED_MISSING_RADIO_EVENTS
```

La diagnosi ha chiarito che:

```text
SNS attivo != radio ad-hoc veicolare attiva
```

### Correzione MOSAIC minimale

E' stato creato il tool:

```text
tools/mosaic-adhoc-radio-diagnostic/
```

Responsabilita':

```text
abilitare il modulo ad-hoc del veicolo
modalita' SINGLE
nessun messaggio V2X inviato
nessun traffico SNS artificiale
nessuna logica MA-GA
```

Gli scenari diagnostici integrati sono stati aggiornati per aggiungere la app
ad-hoc mantenendo workload e traffico Cell indipendenti.

### Policy LOCAL

Ogni veicolo presente al timestamp produce un candidato:

```text
type = LOCAL
sourceVehicleId == executionNodeId
propagationDelaySeconds = 0
localCpuCyclesPerSecond letto dal catalogo
```

Il valore CPU locale resta:

```text
4000000000 cicli/s
cpuSource = DIAGNOSTIC_SYNTHETIC_VALUE
calibrationStatus = TO_BE_REPLACED_DURING_RESOURCE_CALIBRATION
```

### Policy V2V

Policy:

```text
candidatePolicy = DIRECT_SINGLEHOP_ONLY
distancePolicy = HAVERSINE_FROM_LAT_LON_DIAGNOSTIC
poolPolicy = ONE_SHARED_POOL_PER_UNORDERED_PAIR
```

Un candidato V2V e' valido se:

```text
source != target
source e target presenti allo stesso timestamp
radio source attiva
radio target attiva
distanza <= singlehopRadius
```

La radio e' ricostruita solo da eventi:

```text
ADHOC_CONFIGURATION
latest known event with eventTime <= candidateTime
SINGLE -> attiva
OFF -> disattiva
```

Valori diagnostici:

```text
singlehopRadius = 709.4 m
conservativePropagationDelaySeconds = 0.0024
v2vNominalBandwidthBitsPerSecond = 10000000
bandwidthSource = DIAGNOSTIC_SYNTHETIC_VALUE
```

La banda V2V nominale non e' una misura SNS; e' una configurazione sintetica
provvisoria da calibrare.

### Output 10G

```text
local_candidate_preview.csv
v2v_candidate_preview.csv
v2v_bandwidth_pool_preview.csv
diagnostics/phase_10g_validation.json
```

La validazione finale ha confermato:

```text
radioEventsRead > 0
vehiclesWithRadioEvents = 12
v2vGenerationStatus = COMPLETED
v2vCandidatesExported > 0
v2vPoolsExported > 0
futureLookAheadViolations = 0
```

## Fase 10H - Assegnazione task alle finestre

### Obiettivo

La 10H ha introdotto una timeline esplicita e l'assegnazione diagnostica dei
task alle finestre MA-GA.

Output:

```text
optimization_window_timeline.csv
window_task_assignment.csv
diagnostics/phase_10h_validation.json
```

### Timeline

Policy:

```text
timelinePolicy = FIXED_INTERVAL_DIAGNOSTIC
simulationStartSeconds = 0
simulationEndSeconds = 180
windowIntervalSeconds = 5
```

Finestre:

```text
5, 10, 15, ..., 180
windowsGenerated = 36
```

Regola confini:

```text
prima finestra: [0, 5]
successive: (5, 10], (10, 15], ...
```

L'assegnazione usa `activationTimeNs` e confronti interi in nanosecondi, non
confronti floating point.

### Policy assegnazione

```text
taskAssignmentPolicy = PENDING_TASKS_AT_NEXT_OPTIMIZATION_WINDOW
consumptionPolicy = REMOVE_AFTER_EXPORT_TO_WINDOW
```

Ogni task viene esportato verso una sola finestra e non viene ripetuto in
finestre successive.

Risultati:

```text
tasksRead = 682
tasksAssigned = 682
duplicateTaskIdsInInput = 0
duplicateAssignments = 0
tasksLost = 0
tasksAssignedBeforeActivation = 0
tasksAssignedToNonEarliestWindow = 0
tasksAfterSimulationEnd = 0
negativeActivationTimes = 0
readyForPhase10I = true
```

## Fase 10I-pre - Gateway infrastrutturale opzionale

### Problema

La baseline MOSAIC reale contiene veicoli presenti senza gateway attivo:

```text
VehicleSnapshot presente
LOCAL disponibile
eventuali candidati V2V disponibili
nessuna RSU raggiungibile
```

Il contratto Java precedente richiedeva invece esattamente un access link
attivo per ogni veicolo, bloccando snapshot strutturalmente corretti.

### Contratto aggiornato

Cardinalita':

```text
access link attivi per veicolo in {0, 1}
> 1 resta errore
```

Semantica candidati:

```text
LOCAL -> non richiede gateway
VEHICLE / V2V -> non richiede gateway infrastrutturale
EDGE -> richiede gateway attivo
CLOUD -> richiede gateway attivo
```

API:

```text
AccessLinkResolver.findActiveAccessLink(...) -> Optional
AccessLinkResolver.requireActiveAccessLink(...) -> strict preservata
AccessLinkMetricsEstimator.estimateActiveLinkIfPresent(...) -> Optional
AccessLinkMetricsEstimator.estimateActiveLink(...) -> strict preservata
```

### Dinamicita' e coverage reference

`Dl(k)`:

```text
link attivo e disponibile -> q_v(k) = 1 - phi_link
link attivo non disponibile -> q_v(k) = 0
nessun link attivo -> q_v(k) = 0
```

`CoverageReferenceCalculator` calcola la media solo sui veicoli con access link
attivo. Se nessun veicolo ha access link attivo:

```text
computeReferenceCoverageSeconds(snapshot) = 0.0
hasReferenceCoverage(snapshot) = false
```

Test obbligatori A-I superati:

```text
LOCAL-only senza gateway accettato
V2V-only senza gateway accettato
scenario misto accettato
piu' link attivi rifiutati
EDGE/CLOUD senza gateway rifiutati
dinamicita' senza gateway non solleva eccezioni
coverage reference usa fallback quando nessun link e' attivo
```

## Fase 10I-pre2 - Proiezione SUMO autorevole

### Problema

Le coordinate geografiche MOSAIC non possono essere usate come coordinate
metriche:

```text
latitude != x
longitude != y
```

Inserire lat/lon in formule euclidee del core avrebbe creato distanze
fisicamente errate.

### Sorgente autorevole

La proiezione e' stata ricostruita da:

```text
data/mosaic-scenarios/MaGaIntegratedStudy/sumo/Barnim.net.xml
```

Elemento SUMO:

```text
<location ... />
netOffset
convBoundary
origBoundary
projParameter
```

Policy finale:

```text
projectionPolicy = SUMO_NET_XML_UTM_WGS84_WITH_NET_OFFSET
projectionSourceFile = data/mosaic-scenarios/MaGaIntegratedStudy/sumo/Barnim.net.xml
```

### Output

```text
vehicle_state_stream_projected.csv
infrastructure_snapshot_projected.json
diagnostics/phase_10i_pre2_projection_validation.json
```

La validazione ha confermato:

```text
coordinate veicolo proiettate complete
gateway proiettati
coordinate finite
round-trip validato
confronto projected-vs-Haversine completato
readyForPhase10I = true
```

## Fase 10I - Generazione SystemSnapshot JSON

### Obiettivo

La 10I ha assemblato gli stream 10A-10H in `SystemSnapshot` JSON finali
compatibili con loader e validator Java.

Output:

```text
data/snapshots/mosaic-generated/snapshot_000_t_005.json
...
data/snapshots/mosaic-generated/snapshot_035_t_180.json
snapshot_manifest.csv
diagnostics/phase_10i_validation.json
```

### Schema reale

Campi root:

```text
snapshotId
timeSeconds
vehicles
tasks
candidateNodes
accessGateways
accessLinks
bandwidthPools
```

DTO principali:

```text
VehicleInputDto:
    vehicleId, x, y, speed, localCpu

TaskInputDto:
    taskId, sourceVehicleId, inputSizeBits, outputSizeBits,
    cpuCycles, deadlineSeconds

NodeCandidateInputDto:
    candidateId, sourceVehicleId, executionNodeId, type,
    availableCpu, availableBandwidth, baseLatencySeconds,
    nodeX, nodeY, coverageRadiusMeters, bandwidthPoolId

AccessGatewayInputDto:
    gatewayId, gatewayType, x, y, coverageRadiusMeters, bandwidthPoolId

AccessLinkInputDto:
    accessLinkId, vehicleId, gatewayId, active, available

BandwidthPoolInputDto:
    poolId, poolType, capacityBitsPerSecond
```

### Policy di assemblaggio

Timeline:

```text
snapshotTimelinePolicy = EXPLICIT_OPTIMIZATION_WINDOW_TIMELINE
```

Task:

```text
da window_task_assignment.csv
nessun task reinserito
nessun task pendente simulato
```

Veicoli attivi:

```text
activeVehicleSetPolicy = ACTIVE_VEHICLES_FROM_EXACT_LOCAL_CANDIDATES
vehicleLookupPolicy = LATEST_AVAILABLE_STATE_AT_OR_BEFORE_WINDOW
```

Access link e candidati:

```text
accessLinkLookupPolicy = EXACT_WINDOW_TIMESTAMP
localCandidateLookupPolicy = EXACT_WINDOW_TIMESTAMP
v2vCandidateLookupPolicy = EXACT_WINDOW_TIMESTAMP
remoteCandidateLookupPolicy = EXACT_WINDOW_TIMESTAMP
v2vPoolLookupPolicy = EXACT_WINDOW_TIMESTAMP
```

Pool gateway:

```text
gatewayPoolAssemblyPolicy = LATEST_SAFE_AVAILABLE_CELL_BUCKET_PER_GATEWAY_POOL
availableBandwidth = min(uplinkResidualBandwidth, downlinkResidualBandwidth)
```

### Risultati 10I

```text
snapshotsGenerated = 36
expectedSnapshots = 36
emptyTaskSnapshots = 1
totalTasksAcrossSnapshots = 682
uniqueTasksAcrossSnapshots = 682
tasksLost = 0
duplicateTaskAssignmentsAcrossSnapshots = 0
futureLookAheadViolations = 0
orphanReferenceViolations = 0
duplicateCandidateIds = 0
duplicatePoolIds = 0
unresolvedGatewayPools = 0
unresolvedV2vPools = 0
multipleActiveGatewayViolations = 0
activeUnavailableLinkViolations = 0
cloudPlaceholderViolations = 0
javaLoaderValidationFailures = 0
javaValidatorFailures = 0
readyForPhase10J = true
```

## Fase 10J-pre - Finestre vuote e avvio replay

### Problema JSON_SEQUENCE

Il primo snapshot e' volutamente vuoto:

```text
snapshot_000_t_005.json
tasks = []
vehicles = []
candidateNodes = []
accessLinks = []
```

Il replay sequenziale falliva prima di raggiungere il ramo gia' esistente:

```text
StopReason.EMPTY_TASK_SET
fitness = 0.0
generationsExecuted = 0
```

### Fix

`MaGaOptimizer` e' stato riallineato:

```text
tasks == null -> errore
candidateNodes == null -> errore
tasks vuoto -> candidateNodes puo' essere vuoto
tasks non vuoto e candidateNodes vuoto -> errore
```

### Problema JSON_TIME

`AdaptiveWindowMain` partiva da `0.0 s`, mentre il primo snapshot disponibile e':

```text
5.0 s
```

La sorgente temporale faceva correttamente no-look-ahead e quindi non restituiva
uno snapshot futuro.

### Fix

Il replay offline parte da:

```text
snapshots.get(0).getTimeSeconds()
```

La semantica di `TimeIndexedSnapshotReplaySource` resta:

```text
LATEST_SNAPSHOT_AT_OR_BEFORE_REQUESTED_TIME
```

Test 10J-pre:

```text
testsExecuted = 7
testsPassed = 7
futureLookAheadViolations = 0
readyForPhase10J = true
```

## Fase 10J-pre2 - Reporting con gateway opzionale

### Problema

Il replay arrivava alla stampa del report finale ma falliva in:

```text
AccessLinkMetricsEstimator.estimateActiveLink(...)
AccessLinkDynamicityDiagnosticPrinter
```

Il printer usava un'API strict su veicoli generici, mentre il contratto 10I-pre
consente veicoli senza access link attivo.

### Fix reporting

`AccessLinkDynamicityDiagnosticPrinter` usa:

```text
estimateActiveLinkIfPresent(...)
```

Semantica:

```text
nessun link attivo -> q_v(k) = 0
gateway/distanza/phiLink mancanti -> "-"
metriche sintetiche non inventate
```

Transizioni:

```text
UNCHANGED
COVERAGE_GAIN
COVERAGE_LOSS
HANDOVER
```

`CloudGatewayDiagnosticPrinter` ora separa:

```text
firstSnapshotAccessLinkCount
maximumAccessLinkCountAcrossWindows
maximumActiveAccessLinkCountAcrossWindows
windowsWithActiveAccessLinks
windowsWithoutActiveAccessLinks
```

### Validazione 10J-pre2

```text
testsExecuted = 12
testsPassed = 12
JSON_SEQUENCE exit code = 0
JSON_SEQUENCE windows = 36
JSON_SEQUENCE task evaluations = 682
JSON_TIME smoke exit code = 0
futureLookAheadViolations = 0
readyForPhase10J = true
```

Warning registrati:

```text
WARNING_DIAGNOSTIC_BASELINE_NOT_STRESSING_OFFLOADING
WARNING_JSON_TIME_FULL_HORIZON_NOT_YET_VALIDATED
```

## Fase 10J-final - JSON_TIME full horizon

### Problema

Lo smoke test `JSON_TIME OBSERVED_RUNTIME` con 36 step dimostrava bootstrap e
causalita', ma non raggiungeva necessariamente lo snapshot finale. In modalita'
temporale adattiva:

```text
36 step != 36 file JSON
```

Il manager puo' riutilizzare lo stesso snapshot passato molte volte.

### Harness full horizon

Tool creato:

```text
tools/json-time-full-horizon-validation/
```

Policy:

```text
sourceMode = JSON_TIME
runtimeProfile = OBSERVED_RUNTIME
lookupPolicy = LATEST_SNAPSHOT_AT_OR_BEFORE_REQUESTED_TIME
ordinaryStopPolicy = FULL_TIME_HORIZON_REACHED
safetyStopPolicy = SAFETY_MAX_STEPS_REACHED
```

Condizione ordinaria di successo:

```text
lastObservationTimeSeconds >= finalSnapshotTimeSeconds
AND
lastSourceSnapshotId == finalSnapshotId
```

Guardrail:

```text
SafetyMaxSteps = 100000
```

Il guardrail non e' una condizione di successo.

### Risultati 10J-final

```text
snapshotsLoaded = 36
firstSnapshotId = mosaic_generated_000_t_005
firstSnapshotTimeSeconds = 5.0
finalSnapshotId = mosaic_generated_035_t_180
finalSnapshotTimeSeconds = 180.0
stepsExecuted = 281
stopReason = FULL_TIME_HORIZON_REACHED
fullTimeHorizonReached = true
safetyGuardrailTriggered = false
lastTriggerTimeSeconds = 180.08505951061724
lastObservationTimeSeconds = 180.08505951061724
lastSourceSnapshotId = mosaic_generated_035_t_180
lastSourceSnapshotTimeSeconds = 180.0
distinctSourceSnapshotsObserved = 36
exactTimestampMatches = 4
pastSnapshotReuses = 245
sourceSnapshotAdvances = 35
sourceSnapshotSkips = 0
futureLookAheadViolations = 0
noTemporalStepFailures = 0
taskEvaluationsAcrossTemporalSteps = 5408
phase10jStatus = COMPLETED
point10ReadyToClose = true
```

La trace finale e':

```text
data/mosaic-study/json_time_full_horizon_trace.csv
```

## Stato finale del Punto 10

Il Punto 10 e' chiuso dal punto di vista strutturale:

```text
10A task stream generato
10B stati veicolari generati
10C infrastruttura generata
10D handover e banda Cell generati
10E access link preview generata
10F candidati EDGE/CLOUD generati
10G candidati LOCAL/V2V e pool V2V generati
10H task assegnati alle finestre
10I snapshot JSON finali generati e validati
10J JSON_SEQUENCE validato end-to-end
10J JSON_TIME validato fino all'orizzonte finale
```

Condizioni finali:

```text
futureLookAheadViolations = 0
orphanReferenceViolations = 0
javaLoaderValidationFailures = 0
javaValidatorFailures = 0
JSON_SEQUENCE exit code = 0
JSON_TIME full horizon = COMPLETED
point10ReadyToClose = true
```

## Warning e limiti residui

Restano aperti warning sperimentali:

```text
WARNING_DIAGNOSTIC_BASELINE_NOT_STRESSING_OFFLOADING
WARNING_ALL_DECISIONS_LOCAL
WARNING_FULL_OFFLOADING_NOT_OBSERVED
```

Motivazione:

```text
tutte le decisioni osservate risultano LOCAL
offloadingRatio resta p = 0
EDGE, CLOUD e VEHICLE non vengono selezionati
CPU e banda non risultano sotto pressione
la baseline valida la struttura, non la qualita' scientifica dell'offloading
```

Questi warning non bloccano la chiusura del Punto 10. Indicano che le fasi
successive dovranno costruire scenari piu' stressanti e calibrare workload,
CPU, banda e parametri di valutazione.

## Classificazione dei dati

```text
OSSERVATI_DA_MOSAIC:
    task
    posizioni e velocita' veicoli
    eventi Cell
    bandwidthMeasurements Cell
    eventi ADHOC_CONFIGURATION

CONFIGURATI_NEL_CATALOGO:
    CPU locale diagnostica
    banda nominale V2V diagnostica
    ritardo conservativo V2V
    profili workload

DERIVATI_DALL_EXPORTER:
    access link
    candidati EDGE/CLOUD
    candidati LOCAL
    candidati VEHICLE/V2V
    pool V2V
    task-window assignment
    SystemSnapshot JSON

DIAGNOSTICI_DA_CALIBRARE:
    CPU locale sintetica
    banda V2V sintetica
    raggio e ritardo V2V diagnostici
    stress workload
    pressione CPU/banda
```

## Artefatti principali da usare dopo il Punto 10

Per replay e analisi:

```text
data/snapshots/mosaic-generated/
data/mosaic-study/snapshot_manifest.csv
data/mosaic-study/json_time_full_horizon_trace.csv
```

Per audit:

```text
data/mosaic-study/diagnostics/phase_10i_validation.json
data/mosaic-study/diagnostics/phase_10j_validation.json
```

Per ricostruzione completa:

```text
data/mosaic-study/*.csv
data/mosaic-study/*_projected.json
data/mosaic-study/diagnostics/*.json
```

## Attivita escluse dal Punto 10

Non sono state implementate:

```text
bridge live MOSAIC
replay live
migrazione remota task
persistenza esecuzione remota
ricalibrazione scientifica del workload
ottimizzazione fitness
modifiche a repair/mutation/crossover
scenari stressanti per offloading competitivo
```

## Prossimi passi consigliati

Le attivita' successive dovrebbero concentrarsi su:

```text
calibrazione risorse e workload
scenari che producano pressione CPU/banda reale
validazione scientifica delle decisioni EDGE/CLOUD/V2V
eventuale bridge MOSAIC live solo dopo stabilizzazione offline
```

Il Punto 10 consegna quindi una pipeline offline completa, validata e
causalmente riproducibile.
