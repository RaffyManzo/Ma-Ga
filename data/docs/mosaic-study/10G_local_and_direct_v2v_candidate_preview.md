# Fase 10G - Preview diagnostica dei candidati LOCAL e V2V diretti

## Scopo della fase

La Fase 10G completa la pipeline offline MOSAIC -> MA-GA con le preview diagnostiche dei candidati:

```text
LOCAL
VEHICLE / V2V diretto
```

La fase resta esterna al core MA-GA. Non produce `SystemSnapshot` finali, non assegna task alle finestre temporali, non invoca il core Java, non implementa replay JSON, non introduce un bridge live e non procede alla Fase 10H.

Il contratto architetturale rimane:

```text
MOSAIC
    -> exporter offline
    -> stream e preview diagnostiche
    -> futura composizione SystemSnapshot
    -> MA-GA core
```

## Problema iniziale: assenza di ADHOC_CONFIGURATION

La prima esecuzione 10G sulla baseline:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/
```

ha validato la parte LOCAL, ma ha bloccato correttamente la parte V2V:

```text
vehicleStatesRead = 1824
localCandidatesExported = 1824
radioEventsRead = 0
vehiclesWithRadioEvents = 0
v2vGenerationStatus = SKIPPED_MISSING_RADIO_EVENTS
v2vCandidatesExported = 0
v2vPoolsExported = 0
futureLookAheadViolations = 0
```

La causa era a monte: `output.csv` non conteneva righe `ADHOC_CONFIGURATION`. Senza eventi radio osservati non e' ammesso assumere che tutti i veicoli abbiano sempre radio ad-hoc attiva.

## Diagnosi

La diagnosi sulla baseline precedente ha verificato:

```text
SNS attivo nello scenario
output_config.xml gia' sottoscritto ad ADHOC_CONFIGURATION
output.csv senza ADHOC_CONFIGURATION
Communication.log senza evidenze di radio ad-hoc abilitate
mapping veicolare privo di un'app che abiliti il modulo ad-hoc
workload e traffico Cell indipendenti dal problema
```

Il punto importante e' che SNS attivo non implica automaticamente radio veicolari attive. SNS puo' essere presente come federate, ma il sistema operativo del veicolo deve comunque avere il modulo ad-hoc abilitato da un'applicazione.

## Nuova applicazione diagnostica minimale

Per correggere la causa minima e' stato creato il tool:

```text
tools/mosaic-adhoc-radio-diagnostic/
```

La classe principale e':

```text
org.eclipse.mosaic.app.maga.adhocradio.MaGaAdHocRadioDiagnosticApp
```

Responsabilita':

```text
abilitare una singola radio ad-hoc all'avvio del veicolo
non inviare messaggi V2X
non generare traffico SNS
non modificare workload
non modificare traffico Cell
non implementare logica MA-GA
non modificare il core MA-GA
```

I log applicativi sono soltanto diagnostici:

```text
ADHOC_RADIO_DIAGNOSTIC_APP_START
ADHOC_RADIO_ENABLE
ADHOC_RADIO_DIAGNOSTIC_APP_STOP
```

Gli exporter non usano questi log come fonte canonica. La fonte canonica resta `ADHOC_CONFIGURATION` prodotta da MOSAIC in `output.csv`.

## API MOSAIC usate

Le API sono state verificate localmente sui JAR MOSAIC 25.2 con `jar tf` e `javap`.

Classi e metodi rilevanti:

```text
VehicleOperatingSystem
    extends AdHocCommunicative

AdHocCommunicative
    getAdHocModule()

AdHocModule
    enable(AdHocModuleConfiguration)
    disable()

AdHocModuleConfiguration
    addRadio()

AdHocModuleRadioConfiguration
    channel(AdHocChannel)
    create()

AdHocChannel
    CCH

AdHocConfiguration.RadioMode
    OFF
    SINGLE
    DUAL
```

La scelta implementativa e':

```java
AdHocModuleConfiguration configuration = new AdHocModuleConfiguration();
configuration.addRadio().channel(AdHocChannel.CCH).create();
getOs().getAdHocModule().enable(configuration);
```

Una sola radio abilita la configurazione `SINGLE`. La disabilitazione in shutdown produce `OFF`.

## Scenari aggiornati

La nuova app e' stata aggiunta ai veicoli dei quattro scenari diagnostici integrati:

```text
data/mosaic-scenarios/MaGaIntegratedStudy/
data/mosaic-scenarios/MaGaIntegratedStudyRequest2x/
data/mosaic-scenarios/MaGaIntegratedStudyResponse2x/
data/mosaic-scenarios/MaGaIntegratedStudyFrequency2x/
```

Nei rispettivi `mapping/mapping_config.json`, i veicoli continuano a usare:

```text
org.eclipse.mosaic.app.maga.workload.MaGaWorkloadDiagnosticApp
org.eclipse.mosaic.app.maga.celltraffic.MaGaCellTrafficDiagnosticVehicleApp
```

e aggiungono:

```text
org.eclipse.mosaic.app.maga.adhocradio.MaGaAdHocRadioDiagnosticApp
```

Gli scenari storici non sono stati modificati.

## Nuova baseline canonica

Dopo build e deploy degli scenari, MOSAIC e' stato rieseguito su:

```text
MaGaIntegratedStudy
```

La nuova baseline canonica per gli output 10A-10G e':

```text
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/
```

La simulazione si e' conclusa correttamente:

```text
Simulation ended after 180s of 180s (100%)
Simulation finished: 101
```

Gli eventi radio osservati sono:

```text
ADHOC_CONFIGURATION SINGLE = 12
ADHOC_CONFIGURATION OFF = 12
```

Il conteggio delle righe `V2X_MESSAGE*` resta uguale alla baseline precedente:

```text
baseline precedente = 7134
nuova baseline = 7134
```

Le prime righe `V2X_MESSAGE*` sono messaggi `MaGaCellTrafficDiagnosticMessage` e `MaGaCellTrafficDiagnosticResponseMessage`, quindi non sono invii introdotti dalla nuova app ad-hoc.

## Input della pipeline

La 10G usa:

```text
data/mosaic-study/vehicle_state_stream.csv
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/output.csv
data/mosaic-scenarios/MaGaIntegratedStudy/application/ma_ga_resource_catalog.json
data/mosaic-scenarios/MaGaIntegratedStudy/sns/sns_config.json
```

`vehicle_state_stream.csv` fornisce tempo, veicolo, latitudine, longitudine e stato attivo. `output.csv` fornisce gli eventi `ADHOC_CONFIGURATION`. Il catalogo risorse fornisce CPU locale, banda V2V nominale, policy V2V e ritardo conservativo. `sns_config.json` fornisce il raggio single-hop.

## Output 10G

La fase produce:

```text
data/mosaic-study/local_candidate_preview.csv
data/mosaic-study/v2v_candidate_preview.csv
data/mosaic-study/v2v_bandwidth_pool_preview.csv
data/mosaic-study/diagnostics/phase_10g_validation.json
```

Sono preview diagnostiche. Non sono ancora snapshot finali caricabili dal core come sequenza temporale completa.

## Policy LOCAL

Viene generato un candidato LOCAL per ogni stato veicolare attivo:

```text
(timeNs, sourceVehicleId)
```

Naming:

```text
candidateId =
    local_for_<sourceVehicleId>
```

Per ogni candidato:

```text
sourceVehicleId = veicolo corrente
executionNodeId = veicolo corrente
type = LOCAL
availableCpu = CPU locale letta dal profilo car_default
cpuSource = valore letto dal catalogo
propagationDelaySeconds = 0
```

Non vengono generati pool di banda per LOCAL.

## Policy DIRECT_SINGLEHOP_ONLY

La policy V2V resta:

```text
candidatePolicy =
    DIRECT_SINGLEHOP_ONLY
```

Un peer V2V e' candidabile se e solo se:

```text
source != target
source presente allo stesso timestamp
target presente allo stesso timestamp
radio ad-hoc source attiva
radio ad-hoc target attiva
distanza(source, target) <= singlehopRadius
```

Non vengono usate ricezioni `V2X_MESSAGE_RECEPTION` come prova automatica di collegamento diretto. Una ricezione SNS non equivale necessariamente a un collegamento V2V diretto MA-GA.

## Stato radio ADHOC_CONFIGURATION

La sorgente dichiarata e':

```text
radioStateSource =
    ADHOC_CONFIGURATION
```

Interpretazione:

```text
SINGLE
    -> radio ad-hoc attiva

OFF
    -> radio ad-hoc disattiva
```

Modalita' diverse da `SINGLE` e `OFF` devono essere trattate come ambigue fino a nuova verifica.

## Lookup temporale senza future look-ahead

Per ogni timestamp candidato viene usato soltanto l'ultimo evento radio noto con:

```text
eventTime <= candidateTime
```

Non viene usato alcun evento radio futuro. Se un veicolo non ha ancora un evento radio noto, non viene generato un candidato V2V per quel peer a quel timestamp.

## Distanza Haversine

La distanza V2V usa temporaneamente latitudine e longitudine:

```text
distancePolicy =
    HAVERSINE_FROM_LAT_LON_DIAGNOSTIC
```

Le coordinate `projectedX` e `projectedY` dello stream veicolare sono ancora vuote. Una distanza cartesiana su coordinate proiettate resta un miglioramento futuro.

## Raggio V2V

Il raggio viene letto da:

```text
data/mosaic-scenarios/MaGaIntegratedStudy/sns/sns_config.json
```

Valore osservato:

```text
singlehopRadius =
    709.4 m
```

Lo script non contiene questo valore come costante operativa: lo legge dalla configurazione SNS.

## Ritardo conservativo

Il ritardo V2V conservativo viene letto dal catalogo:

```text
v2vPolicy.conservativePropagationDelaySeconds =
    0.0024 s
```

Policy:

```text
propagationDelayPolicy =
    SNS_SINGLEHOP_MAX_DELAY
```

## CPU locale sintetica

Il catalogo contiene:

```text
CPU locale =
    4000000000 cicli/s

cpuSource =
    DIAGNOSTIC_SYNTHETIC_VALUE

calibrationStatus =
    TO_BE_REPLACED_DURING_RESOURCE_CALIBRATION
```

Il valore e' provvisorio. Non proviene da SUMO, SNS o misure MOSAIC.

## Banda V2V sintetica

Il catalogo contiene:

```text
banda V2V nominale =
    10000000 bit/s

bandwidthSource =
    DIAGNOSTIC_SYNTHETIC_VALUE

calibrationStatus =
    TO_BE_REPLACED_DURING_RESOURCE_CALIBRATION
```

La banda V2V nominale non e' una misura prodotta da SNS. SNS non espone una banda residua allocabile per coppia diretta. Il valore corrente e' una configurazione sintetica provvisoria.

In futuro CPU locale e banda V2V dovranno essere sostituite con provenienza motivata:

```text
LITERATURE_BASED
CALIBRATED_FROM_SCENARIO
```

## Pool condivisi per coppia non ordinata

La policy dei pool e':

```text
poolPolicy =
    ONE_SHARED_POOL_PER_UNORDERED_PAIR
```

Per una coppia non ordinata:

```text
{veh_A, veh_B}
```

viene creato un solo pool:

```text
pool_v2v_<vehicleA>_<vehicleB>
```

`vehicleA` e `vehicleB` sono ordinati con ordinamento naturale, cosi' `veh_3` precede `veh_11`.

Il tipo pool e':

```text
poolType =
    DIRECT_V2V
```

Il candidato V2V e' direzionale. Il pool e' fisico e condiviso dalla coppia non ordinata.

## Naming dei candidati e dei pool

Naming V2V:

```text
candidateId =
    vehicle_<targetVehicleId>_v2v_for_<sourceVehicleId>
```

Esempio:

```text
candidateId:
    vehicle_veh_11_v2v_for_veh_3

sourceVehicleId:
    veh_3

targetVehicleId:
    veh_11

executionNodeId:
    veh_11

bandwidthPoolId:
    pool_v2v_veh_3_veh_11
```

## Conteggi ottenuti

La pipeline 10A-10G rigenerata dalla nuova baseline produce:

```text
10A task_stream.csv:
    682 task
    0 duplicati

10B vehicle_state_stream.csv:
    1824 stati
    12 veicoli

10C infrastructure_snapshot.json:
    2 gateway
    2 pool Cell
    2 EDGE
    1 CLOUD

10D Cell:
    48 handover
    1080 record banda
    540 UPLINK
    540 DOWNLINK

10E access_link_preview.csv:
    3648 link valutati
    564 link attivi

10F remote_candidate_preview.csv:
    1128 candidati remoti
    564 EDGE
    564 CLOUD
    0 future look-ahead

10G:
    1824 candidati LOCAL
    24 eventi radio
    12 veicoli con eventi radio
    13206 candidati V2V
    6603 pool V2V
```

## Validazioni eseguite

Validazioni 10G:

```text
localCpuViolations = 0
duplicateLocalCandidateIds = 0
sourceEqualsTargetViolations = 0
sameTimestampPresenceViolations = 0
radioActiveViolations = 0
radioFutureLookAheadViolations = 0
distanceRadiusViolations = 0
expectedPoolIdViolations = 0
v2vCpuViolations = 0
v2vBandwidthViolations = 0
v2vDelayViolations = 0
duplicateV2vCandidateIds = 0
poolDirectionSharedViolations = 0
ambiguousBandwidthPoolIds = 0
poolCapacityViolations = 0
futureLookAheadViolations = 0
```

Il JSON finale riporta:

```text
phase10gStatus = COMPLETED
readyForPhase10H = true
warnings = []
errors = []
```

Distribuzione delle distanze V2V:

```text
numero candidati = 13206
min = 100.816475 m
max = 709.396941 m
media = 367.546524 m
mediana = 377.340855 m
```

## Confronto con la precedente baseline 10A-10F

Rispetto alla baseline precedente `log-20260603-174645-MaGaIntegratedStudy`, i conteggi 10A-10F restano invariati:

```text
task = 682
stati veicolari = 1824
gateway = 2
pool Cell = 2
handover Cell = 48
record banda Cell = 1080
access link attivi = 564
candidati remoti = 1128
future look-ahead remoti = 0
```

La differenza intenzionale e' la presenza di:

```text
ADHOC_CONFIGURATION SINGLE = 12
ADHOC_CONFIGURATION OFF = 12
```

che consente di completare la parte V2V senza assumere stati radio non osservati.

## Limiti ancora aperti

Restano fuori dalla 10G:

```text
SystemSnapshot finali
assegnazione task alle finestre
replay JSON_SEQUENCE
replay JSON_TIME
bridge live
migrazione task remoti
calibrazione scientifica di CPU locale
calibrazione scientifica di banda V2V
distanza cartesiana da coordinate proiettate
allocazione radio V2V residua per coppia
```

## Readiness per la Fase 10H

La Fase 10H puo' iniziare perche' la pipeline offline dispone ora di:

```text
task_stream.csv
vehicle_state_stream.csv
infrastructure_snapshot.json
cell_handover_stream.csv
cell_bandwidth_stream.csv
access_link_preview.csv
remote_candidate_preview.csv
local_candidate_preview.csv
v2v_candidate_preview.csv
v2v_bandwidth_pool_preview.csv
phase_10g_validation.json
```

I dati sono separati semanticamente:

```text
OSSERVATI_DA_MOSAIC:
    task, posizioni, velocita', RSU, server, handover Cell, banda Cell, radio ad-hoc

CONFIGURATI_NEL_CATALOGO:
    CPU locale diagnostica, banda V2V nominale diagnostica, ritardo V2V

DERIVATI_DALL_EXPORTER:
    access link, candidati EDGE, candidati CLOUD, candidati LOCAL, candidati VEHICLE, pool V2V

DIAGNOSTICI_DA_CALIBRARE:
    CPU locale, banda V2V nominale, distanza Haversine diagnostica
```

La 10H dovra' occuparsi della composizione temporale degli snapshot, non della correzione degli input MOSAIC. La 10H non e' implementata in questa fase.
