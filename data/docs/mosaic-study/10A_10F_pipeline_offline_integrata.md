# Fase 13 - Documentazione della pipeline offline MOSAIC -> MA-GA 10A-10F

## Indice

1. [Introduzione](#introduzione)
2. [Perche' e' stato scelto un exporter offline](#perche-e-stato-scelto-un-exporter-offline)
3. [Baseline iniziale e problema emerso](#baseline-iniziale-e-problema-emerso)
4. [Struttura canonica usata](#struttura-canonica-usata)
5. [Baseline definitiva documentata](#baseline-definitiva-documentata)
6. [Fase 10A - Aggregazione workload](#fase-10a---aggregazione-workload)
7. [Fase 10B - Normalizzazione stati veicolari](#fase-10b---normalizzazione-stati-veicolari)
8. [Fase 10C - Snapshot infrastrutturale](#fase-10c---snapshot-infrastrutturale)
9. [Fase 10D - Handover e banda Cell](#fase-10d---handover-e-banda-cell)
10. [Fase 10E - Preview degli access link](#fase-10e---preview-degli-access-link)
11. [Fase 10F - Candidati EDGE e CLOUD](#fase-10f---candidati-edge-e-cloud)
12. [Tabella riassuntiva 10A-10F](#tabella-riassuntiva-10a-10f)
13. [Flusso dati completo](#flusso-dati-completo)
14. [Validazioni finali osservate](#validazioni-finali-osservate)
15. [Cosa non e' stato ancora implementato](#cosa-non-e-stato-ancora-implementato)
16. [Prossimo passo](#prossimo-passo)
17. [Fonti lette](#fonti-lette)
18. [Conflitti e ambiguita' da ricordare](#conflitti-e-ambiguita-da-ricordare)

## Introduzione

Il core MA-GA standalone esiste gia'. La parte centrale del progetto non nasce in questa fase e non viene riscritta qui: il core lavora su `SystemSnapshot`, cioe' su viste coerenti dello stato del sistema che contengono task, veicoli, risorse disponibili, candidati di esecuzione e vincoli temporali.

L'obiettivo della Fase 10 non e' modificare fitness, repair, mutation, crossover o il modo in cui MA-GA prende le decisioni. L'obiettivo e' piu' prudente: sostituire progressivamente snapshot JSON sintetici con dati osservati in Eclipse MOSAIC, mantenendo il core separato dal simulatore fino a quando la trasformazione dati non e' abbastanza chiara e verificata.

Il percorso concettuale e':

```text
log MOSAIC
    ↓
stream normalizzati
    ↓
preview diagnostiche
    ↓
snapshot JSON futuri
    ↓
replay MA-GA
```

Le Fasi 10A-10F costruiscono quindi una pipeline offline. La pipeline non invoca il core Java MA-GA, non produce ancora `SystemSnapshot` finali e non implementa un bridge live. Produce invece CSV e JSON intermedi, leggibili e validabili, che permettono di controllare una trasformazione alla volta.

## Perche' e' stato scelto un exporter offline

Un collegamento live immediato tra MOSAIC e MA-GA avrebbe reso difficile capire dove nasce un errore: dal simulatore, dal bridge, dalla conversione delle unita', dalla semantica temporale oppure dal core decisionale. Per questo e' stato scelto un exporter offline.

La scelta porta alcuni vantaggi concreti:

- controllo dei dati: ogni file intermedio ha colonne esplicite, conteggi attesi e policy dichiarate;
- isolamento degli errori: se un exporter fallisce, il problema resta localizzato nello step che sta trasformando quei dati;
- riproducibilita': la stessa run MOSAIC puo' essere riletta piu' volte e deve produrre gli stessi artefatti;
- separazione tra simulatori e core: MOSAIC produce eventi e misure, MA-GA resta consumatore futuro di snapshot normalizzati;
- validazione incrementale: task, veicoli, infrastruttura, rete Cell, access link e candidati remoti vengono verificati in sequenza;
- riduzione della complessita': prima si dimostra che la semantica offline e' corretta, poi si potra' discutere un eventuale bridge live.

Questa impostazione e' volutamente conservativa. Non cerca di risolvere tutto in una sola fase: evita di mescolare parsing dei log, calibrazione radio, generazione dei candidati e chiamata al core MA-GA nello stesso punto del codice.

## Baseline iniziale e problema emerso

Il lavoro parte dalla necessita' di usare MOSAIC come sorgente dati per MA-GA. La prima baseline utile, `MaGaWorkloadStudy`, permetteva gia' di osservare:

```text
task
stati veicolari
infrastruttura
```

Quella run era sufficiente per iniziare a esportare workload e posizioni, ma non bastava per chiudere la parte Cell della pipeline. Mancavano infatti elementi necessari per costruire candidati remoti realistici almeno a livello diagnostico:

```text
CELLULAR_HANDOVER utili
traffico Cell uplink e downlink completo
bandwidthMeasurements popolati in entrambe le direzioni
```

In particolare, le prime prove Cell mostravano un problema importante: l'uplink veniva osservato, ma il downlink non era ancora popolato in modo utile. Questo impediva di stimare una banda residua conservativa per un candidato remoto, perche' un offload reale deve attraversare sia la direzione veicolo -> rete sia la direzione rete -> veicolo.

Per studiare e calibrare il comportamento Cell sono stati creati scenari diagnostici integrati:

```text
MaGaIntegratedStudy
MaGaIntegratedStudyRequest2x
MaGaIntegratedStudyResponse2x
MaGaIntegratedStudyFrequency2x
```

La famiglia diagnostica ha permesso di variare separatamente dimensione della request, dimensione della response e frequenza. Il risultato utile e' stato verificare che:

- aumentando la request cresce l'uplink;
- aumentando la response cresce il downlink;
- aumentando la frequenza crescono entrambe le direzioni;
- i CSV `bandwidthMeasurements` sono coerenti con valori in bit/s;
- il workload resta stabile a `TASK_ACTIVATION = 682`;
- gli handover Cell restano osservabili.

Questa distinzione e' centrale:

```text
scenario diagnostico integrato
≠
scenario realistico finale
```

`MaGaIntegratedStudy` non e' presentato come scenario scientifico finale. E' la baseline integrata controllata da cui derivano gli artefatti 10A-10F. Le run `MaGaWorkloadStudy`, `MaGaCellStudy`, `MaGaIntegratedStudyRequest2x`, `MaGaIntegratedStudyResponse2x` e `MaGaIntegratedStudyFrequency2x` restano documentazione storica e calibrazione, non input della pipeline canonica.

## Struttura canonica usata

La repository separa sorgenti, output generati, documentazione e ambiente temporaneo:

```text
data/
├── docs/
│   └── mosaic-study/
│       └── documentazione definitiva
├── mosaic-scenarios/
│   └── scenari MOSAIC sorgente versionabili
├── mosaic-study/
│   ├── diagnostics/
│   ├── task_stream.csv
│   ├── vehicle_state_stream.csv
│   ├── infrastructure_snapshot.json
│   ├── cell_handover_stream.csv
│   ├── cell_bandwidth_stream.csv
│   ├── access_link_preview.csv
│   └── remote_candidate_preview.csv
└── snapshots/
    └── snapshot JSON finali futuri

tools/
├── mosaic-offline-exporter/
├── mosaic-cell-traffic-diagnostic/
└── deploy-mosaic-scenarios.ps1

tmp/
└── mosaic-25.2/
    ├── logs/
    └── scenarios/
```

Le regole pratiche sono:

- `data/docs/mosaic-study/` contiene la documentazione definitiva;
- `data/mosaic-scenarios/` contiene gli scenari MOSAIC versionabili;
- `data/mosaic-study/` contiene output generati dagli exporter;
- `data/snapshots/` conterra' i futuri snapshot JSON della Fase 10I;
- `tmp/mosaic-25.2/` e' un ambiente locale temporaneo di esecuzione, log ed eseguibili.

Questo documento e' collocato in `data/docs/mosaic-study/` proprio per non confondere documentazione definitiva e output CSV/JSON generati.

## Baseline definitiva documentata

La singola run canonica documentata qui e':

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/
```

Tutti gli output finali 10A-10F derivano da questa run. I file sorgente versionabili dello scenario stanno in:

```text
data/mosaic-scenarios/MaGaIntegratedStudy/
```

La configurazione dello scenario conferma:

- durata simulazione: `180s`;
- federati attivi: application, cell, environment, sns, output, sumo;
- 12 veicoli generati dal mapping;
- 2 RSU;
- 1 server;
- traffico Cell diagnostico con request da 1000 byte, response da 500 byte, intervallo 1000 ms;
- misure Cell con intervallo `1 s`;
- workload diagnostico sintetico periodico con profili `perception_light`, `planning_medium`, `cooperative_awareness`.

## Fase 10A - Aggregazione workload

### Input

La Fase 10A legge i log applicativi del workload diagnostico:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/
apps/*/MaGaWorkloadDiagnosticApp.log
```

Ogni cartella veicolo contiene un log dell'applicazione che emette eventi `TASK_ACTIVATION`.

### Script

Lo script e':

```text
tools/mosaic-offline-exporter/export_task_stream.py
```

### Output

L'output canonico e':

```text
data/mosaic-study/task_stream.csv
```

Le colonne sono:

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

### Logica

Lo script cerca `MaGaWorkloadDiagnosticApp.log` sotto la root `apps/`, individua le righe che contengono `TASK_ACTIVATION|` e normalizza il payload key-value. Per ogni task controlla:

- presenza di tutti i campi obbligatori;
- valori numerici validi;
- `deadlineSeconds > 0`;
- `inputSizeBits > 0`;
- `cpuCycles > 0`;
- `sourceVehicleId` coerente con la cartella del log;
- `activationTimeNs == activationTimeMs * 1_000_000`;
- formato deterministico del `taskId`.

Il formato atteso del task e':

```text
<profileId>__<sourceVehicleId>__t_<activationTimeMs>
```

I record vengono ordinati per tempo, veicolo, profilo e identificativo. I duplicati di `taskId` sono bloccanti.

### Risultato osservato

La baseline integrata produce:

```text
tasksExported = 682
duplicates = 0
```

Distribuzione per profilo:

```text
cooperative_awareness = 126
perception_light = 369
planning_medium = 187
```

Il primo task e' a `7s`, l'ultimo a `180s`. La Fase 10A non assegna task a finestre MA-GA e non costruisce snapshot: esporta solo uno stream ordinato e verificabile.

## Fase 10B - Normalizzazione stati veicolari

### Input

La Fase 10B legge:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/output.csv
```

Gli eventi usati sono:

```text
VEHICLE_REGISTRATION
VEHICLE_UPDATES
```

### Script

Lo script e':

```text
tools/mosaic-offline-exporter/export_vehicle_state_stream.py
```

### Output

L'output canonico e':

```text
data/mosaic-study/vehicle_state_stream.csv
```

Le colonne sono:

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

### Logica

`VEHICLE_REGISTRATION` viene usato per validare il ciclo di vita minimo del veicolo. Ogni `VEHICLE_UPDATES` valido diventa una riga dello stream.

Lo script verifica:

- registrazioni presenti;
- nessuna registrazione duplicata;
- nessun update prima della registrazione;
- una sola osservazione per coppia `(vehicleId, timeNs)`;
- tempo non decrescente negli eventi rilevanti;
- latitudine tra `-90` e `90`;
- longitudine tra `-180` e `180`;
- velocita' non negativa;
- heading in `[0, 360)`.

Per ora:

```text
active = true
projectedX = vuoto
projectedY = vuoto
```

Le coordinate cartesiane non sono state introdotte in questa fase. Gli access link della 10E usano quindi direttamente latitudine e longitudine.

### Risultato osservato

La baseline produce:

```text
registrationsFound = 12
statesExported = 1824
duplicateVehicleStates = 0
updatesBeforeRegistration = 0
projectedCoordinatesPopulated = 0
```

Sono presenti 12 veicoli distinti. Gli stati vanno da `7s` a `180s`.

## Fase 10C - Snapshot infrastrutturale

### Input

La Fase 10C unisce runtime MOSAIC e configurazione versionabile:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/output.csv
data/mosaic-scenarios/MaGaIntegratedStudy/cell/cell_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/cell/network.json
data/mosaic-scenarios/MaGaIntegratedStudy/cell/regions.json
data/mosaic-scenarios/MaGaIntegratedStudy/sns/sns_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/application/ma_ga_resource_catalog.json
```

### Script

Lo script e':

```text
tools/mosaic-offline-exporter/export_infrastructure_snapshot.py
```

### Output

L'output canonico e':

```text
data/mosaic-study/infrastructure_snapshot.json
```

### Concetti distinti

Questa fase e' importante perche' separa nomi e ruoli che possono sembrare simili:

- regione Cell: area radio definita in `cell/regions.json`, con capacita' e ritardi uplink/downlink;
- gateway RSU: punto di accesso logico MA-GA derivato dal catalogo;
- runtimeId MOSAIC: identificativo runtime apparso in `output.csv`, per esempio `rsu_0`;
- gatewayId logico MA-GA: identificativo stabile usato dalla pipeline, per esempio `rsu_north`;
- executionNodeId: nodo su cui MA-GA potra' eseguire task, per esempio `edge_north`;
- bandwidthPoolId: pool logico di banda associato al gateway;
- server CLOUD: runtime MOSAIC `server_0`, esposto alla pipeline come nodo `cloud_regional`.

La mappatura reale e':

```text
rsu_0
    -> rsu_north
    -> pool_rsu_north
    -> region_north_normal
    -> edge_north

rsu_1
    -> rsu_central
    -> pool_rsu_central
    -> region_central_degraded
    -> edge_central

server_0
    -> cloud_regional
```

`region_north_normal` ha `40 Mbps` uplink, `60 Mbps` downlink e ritardo `80 ms`. `region_central_degraded` ha `8 Mbps` uplink, `12 Mbps` downlink, ritardo `250 ms` e perdita `0.05`. I pool gateway usano il minimo tra uplink e downlink nominale:

```text
pool_rsu_north = 40000000 bit/s
pool_rsu_central = 8000000 bit/s
```

### Risultato osservato

La baseline produce:

```text
rsuRegistrationsFound = 2
serverRegistrationsFound = 1
gatewaysExported = 2
bandwidthPoolsExported = 2
executionNodesExported = 3
errorsCount = 0
```

### Warning aperti

Lo snapshot conserva warning non bloccanti:

```text
localCpuCyclesPerSecond = null
v2vPolicy.nominalBandwidthBitsPerSecond = null
CONFIGURED_VALUE_TO_BE_CALIBRATED
WeatherServer come nome runtime storico
SNS maximumTtl = 10 ma policy MA-GA DIRECT_SINGLEHOP_ONLY
```

Questi warning non impediscono 10A-10F, ma spiegano perche' non si puo' ancora produrre un `SystemSnapshot` finale completo. CPU locale e banda V2V sono ancora da calibrare.

## Fase 10D - Handover e banda Cell

### Diagnostica storica

Prima dello stream integrato canonico esisteva:

```text
tools/mosaic-offline-exporter/export_cell_network_diagnostics.py
```

Questo script esporta handover e misure raw Cell in forma diagnostica storica. E' utile per ispezionare run come `MaGaCellStudy`, ma non deve essere usato per generare gli output finali 10D della baseline integrata. Nel codice lo stato unita' resta:

```text
unitStatus = UNRESOLVED
finalBandwidthStreamGenerated = false
```

In altre parole: lo script storico serve a capire e confrontare, non a produrre il `cell_bandwidth_stream.csv` finale.

### Versione integrata canonica

Lo script canonico della Fase 10D e':

```text
tools/mosaic-offline-exporter/export_cell_network_streams.py
```

### Input

La Fase 10D legge:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/output.csv
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/bandwidthMeasurements/
data/mosaic-study/infrastructure_snapshot.json
```

I file Cell richiesti sono:

```text
ALL#ALL#ALL#Up.csv
ALL#ALL#ALL#Dn.csv
```

### Output

La baseline definitiva produce:

```text
data/mosaic-study/cell_handover_stream.csv
data/mosaic-study/cell_bandwidth_stream.csv
data/mosaic-study/diagnostics/cell/integrated_baseline_metadata.json
```

### Handover

Gli eventi `CELLULAR_HANDOVER` vengono normalizzati in:

```text
timeNs
timeSeconds
vehicleId
previousRegion
currentRegion
eventType
sourceFile
```

La classificazione e':

- `REGISTRATION`: regione precedente vuota, regione corrente valorizzata;
- `REGION_TRANSITION`: entrambe valorizzate e diverse;
- `REMOVAL`: regione precedente valorizzata, regione corrente vuota.

Risultato:

```text
CELLULAR_HANDOVER = 48
REGISTRATION = 12
REGION_TRANSITION = 24
REMOVAL = 12
```

Tutte le righe provengono dalla run:

```text
log-20260603-174645-MaGaIntegratedStudy
```

### Banda Cell

La parte bandwidth legge i CSV `bandwidthMeasurements`, valida le regioni note, riconosce direzione `UPLINK`/`DOWNLINK`, ricava il bucket temporale e sottrae traffico osservato dalla capacita' nominale.

Risultato:

```text
bandwidthRecords = 1080
UPLINK = 540
DOWNLINK = 540
terminalBuckets = 0
```

Le 1080 righe derivano da:

```text
180 secondi
× 3 regioni esportate (globalNetwork, region_north_normal, region_central_degraded)
× 2 direzioni
= 1080
```

### Calibrazione dell'unita'

La calibrazione storica ha ricostruito la catena:

```text
EncodedPayload.getEffectiveLength()
    -> payload in byte
    -> conversione × 8
    -> aggiunta header
    -> calculateNeededCapacity(...)
    -> consumedBandwidth
    -> CSV bandwidthMeasurements
```

Le prove con request 2x, response 2x e frequenza 2x hanno confermato che i valori dei CSV Cell sono coerenti con bit al secondo. La classificazione finale e':

```text
unitStatus = PROVEN_BITS_PER_SECOND
```

### Semantica temporale dei bucket

La semantica documentata e codificata e':

```text
bucketBoundaryPolicy =
    START_TIMESTAMP_FOR_INTERVAL

availableFromPolicy =
    SAFE_AFTER_TIMESTAMP
```

Una riga:

```text
time = t
```

rappresenta:

```text
bucket [t, t + 1)
```

Quel valore descrive traffico osservato durante l'intervallo. Per evitare future look-ahead, non puo' essere usato per una decisione presa a `t`; diventa sicuro solo da:

```text
t + 1
```

Per questo `cell_bandwidth_stream.csv` contiene sia `measurementTimeSeconds` sia `availableFromTimeSeconds`.

### Formula diagnostica residua

La formula usata e':

```text
residualCapacityBitsPerSecond =
    max(
        0,
        nominalCapacityBitsPerSecond
        - trafficObservedBitsPerSecond
    )
```

Questa e' una baseline diagnostica. Non e' ancora un modello scientifico definitivo di scheduling radio, contesa, allocazione per flusso o interferenza. Serve pero' a costruire candidati remoti conservativi senza usare banda nominale pura.

## Fase 10E - Preview degli access link

### Input

La Fase 10E legge:

```text
data/mosaic-study/vehicle_state_stream.csv
data/mosaic-study/infrastructure_snapshot.json
data/mosaic-study/cell_handover_stream.csv
```

### Script

Lo script e':

```text
tools/mosaic-offline-exporter/export_access_link_preview.py
```

### Output

L'output canonico e':

```text
data/mosaic-study/access_link_preview.csv
```

Le colonne sono:

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

### Logica

La 10E e' una preview diagnostica degli access link. Usa latitudine e longitudine degli stati veicolari e la posizione dei gateway esportata nello snapshot infrastrutturale. La policy e':

```text
distancePolicy =
    HAVERSINE_FROM_LAT_LON_DIAGNOSTIC
```

Un gateway e' disponibile se:

```text
distanceMeters <= coverageRadiusMeters
```

Il gateway attivo, per una coppia `(timeNs, vehicleId)`, e' il gateway disponibile piu' vicino. In caso di pareggio viene usato l'ordinamento lessicografico del `gatewayId`.

Gli handover Cell regionali vengono letti solo come controllo diagnostico sulle regioni e sull'intervallo temporale. Non vengono usati come handover fisici di RSU. Questa distinzione evita di confondere:

```text
cambio di regione Cell
≠
cambio di gateway fisico MA-GA
```

### Risultato osservato

La baseline produce:

```text
vehicleStatesRead = 1824
gatewaysRead = 2
linksEvaluated = 3648
availableLinks = 564
activeLinks = 564
statesWithActiveGateway = 564
statesWithoutActiveGateway = 1260
multipleActiveGatewayViolations = 0
activeUnavailableViolations = 0
```

Il numero `3648` deriva da:

```text
1824 stati veicolari × 2 gateway = 3648 link valutati
```

Resta un warning non bloccante: un handover Cell cade fuori dall'intervallo degli stati veicolari esportati. Questo avviene perche' una registrazione Cell puo' precedere il primo `VEHICLE_UPDATES` disponibile nello stream 10B.

## Fase 10F - Candidati EDGE e CLOUD

### Input

La Fase 10F legge:

```text
data/mosaic-study/access_link_preview.csv
data/mosaic-study/infrastructure_snapshot.json
data/mosaic-study/cell_bandwidth_stream.csv
```

### Script

Lo script e':

```text
tools/mosaic-offline-exporter/export_remote_candidate_preview.py
```

### Output

L'output canonico e':

```text
data/mosaic-study/remote_candidate_preview.csv
```

Le colonne includono:

```text
bandwidthMeasurementTimeSeconds
bandwidthAgeSeconds
uplinkResidualBandwidth
downlinkResidualBandwidth
bandwidthPolicy
bandwidthSource
bandwidthLookupPolicy
bucketBoundaryPolicy
```

### Logica source-aware

La 10F costruisce candidati remoti source-aware, cioe' dipendenti dal veicolo sorgente e dal gateway attivo in quel tempo.

Le regole sono:

- si considerano solo access link `active = true`;
- un candidato `EDGE` e' generato solo se il nodo EDGE e' associato al gateway attivo;
- un candidato `CLOUD` e' generato attraverso il gateway attivo;
- il CLOUD fisico e' lo stesso (`cloud_regional`), ma il percorso e' source-aware perche' cambia gateway, regione Cell, banda residua e ritardo radio;
- il `candidateId` ha forma `<executionNodeId>_for_<sourceVehicleId>`.

### Lookup banda sicura

La policy banda e':

```text
bandwidthLookupPolicy =
    LATEST_SAFE_AVAILABLE_CELL_BUCKET

bandwidthPolicy =
    MIN_RESIDUAL_CELL_UPLINK_DOWNLINK_DIAGNOSTIC

bandwidthSource =
    CELL_BANDWIDTH_STREAM_RESIDUAL
```

Per un candidato al tempo `timeSeconds`, lo script cerca l'ultima misura Cell per cui:

```text
availableFromTimeSeconds <= timeSeconds
```

Questa scelta implementa la semantica `SAFE_AFTER_TIMESTAMP`. Non usa dati futuri.

La banda disponibile del candidato e':

```text
availableBandwidth =
    min(
        uplinkResidualBandwidth,
        downlinkResidualBandwidth
    )
```

Il minimo tra uplink e downlink e' una scelta conservativa: un offload remoto richiede che entrambe le direzioni siano sostenibili.

### Ritardo

Il ritardo diagnostico usa il massimo tra ritardo uplink e downlink unicast della regione Cell associata al gateway, piu' il ritardo base del nodo:

```text
propagationDelayPolicy =
    MAX_CELL_UPLINK_DOWNLINK_UNICAST_PLUS_NODE_BASE_DIAGNOSTIC
```

Per esempio, nella regione `region_north_normal`, il ritardo radio e' `0.08s`; un EDGE aggiunge `0.005s`, mentre il CLOUD aggiunge `0.2s`. Nella regione degradata centrale, il ritardo radio e' `0.25s`.

### Risultato osservato

La baseline produce:

```text
candidatesExported = 1128
edgeCandidates = 564
cloudCandidates = 564
futureLookAheadViolations = 0
candidatesMissingSafeBandwidth = 0
```

Distribuzione per gateway:

```text
rsu_north:
    EDGE = 348
    CLOUD = 348

rsu_central:
    EDGE = 216
    CLOUD = 216
```

I controlli finali confermano:

```text
availableBandwidth != min(uplinkResidualBandwidth, downlinkResidualBandwidth) = 0
NOMINAL_ONLY_FOR_INITIAL_EXPORTER occurrences = 0
MIN_RESIDUAL_CELL_UPLINK_DOWNLINK_DIAGNOSTIC occurrences = 1128
```

## Tabella riassuntiva 10A-10F

| Fase | Input | Script | Output | Risultato | Stato |
| --- | --- | --- | --- | --- | --- |
| 10A | `apps/*/MaGaWorkloadDiagnosticApp.log` | `export_task_stream.py` | `task_stream.csv` | 682 task, 0 duplicati | completata |
| 10B | `output.csv` con `VEHICLE_REGISTRATION` e `VEHICLE_UPDATES` | `export_vehicle_state_stream.py` | `vehicle_state_stream.csv` | 1824 stati, 12 veicoli | completata |
| 10C | `output.csv`, Cell, SNS, catalogo risorse | `export_infrastructure_snapshot.py` | `infrastructure_snapshot.json` | 2 gateway, 2 pool, 3 execution node, 0 errori | completata con warning |
| 10D | `output.csv`, `bandwidthMeasurements`, snapshot infrastrutturale | `export_cell_network_streams.py` | `cell_handover_stream.csv`, `cell_bandwidth_stream.csv`, metadata | 48 handover, 1080 record banda | completata |
| 10E | stati veicolari, infrastruttura, handover Cell | `export_access_link_preview.py` | `access_link_preview.csv` | 3648 link, 564 attivi | completata |
| 10F | access link, infrastruttura, stream banda Cell | `export_remote_candidate_preview.py` | `remote_candidate_preview.csv` | 1128 candidati, 0 look-ahead | completata |

## Flusso dati completo

```text
MaGaIntegratedStudy
        ↓
apps/*/MaGaWorkloadDiagnosticApp.log
        ↓
task_stream.csv

output.csv
        ↓
vehicle_state_stream.csv

catalogo + Cell + SNS + output.csv
        ↓
infrastructure_snapshot.json

output.csv + bandwidthMeasurements
        ↓
cell_handover_stream.csv
cell_bandwidth_stream.csv

vehicle_state_stream.csv
+ infrastructure_snapshot.json
        ↓
access_link_preview.csv

access_link_preview.csv
+ infrastructure_snapshot.json
+ cell_bandwidth_stream.csv
        ↓
remote_candidate_preview.csv
```

La pipeline effettiva e':

```text
MaGaIntegratedStudy
        ↓
10A task_stream.csv
        ↓
10B vehicle_state_stream.csv
        ↓
10C infrastructure_snapshot.json
        ↓
10D cell_handover_stream.csv
        ↓
10D cell_bandwidth_stream.csv
        ↓
10E access_link_preview.csv
        ↓
10F remote_candidate_preview.csv
```

La freccia indica dipendenza logica, non sempre dipendenza esclusiva. Per esempio 10A produce i task, ma 10B non consuma `task_stream.csv`; entrambi leggono sorgenti della stessa run. La sequenza e' comunque utile perche' ricostruisce la progressiva disponibilita' degli artefatti MA-GA.

## Validazioni finali osservate

Sui file canonici della baseline integrata sono stati osservati questi risultati:

```text
cell_handover_stream.csv:
    righe dati = 48
    source run unica = log-20260603-174645-MaGaIntegratedStudy
    riferimenti a log-20260602-172233-MaGaCellStudy = 0

cell_bandwidth_stream.csv:
    righe dati = 1080
    UPLINK = 540
    DOWNLINK = 540
    residual formula violations = 0

access_link_preview.csv:
    righe dati = 3648
    active = 564
    available = 564

remote_candidate_preview.csv:
    righe dati = 1128
    EDGE = 564
    CLOUD = 564
    NOMINAL_ONLY_FOR_INITIAL_EXPORTER occurrences = 0
    MIN_RESIDUAL_CELL_UPLINK_DOWNLINK_DIAGNOSTIC occurrences = 1128
    future look-ahead violations = 0
    availableBandwidth != min(uplinkResidualBandwidth, downlinkResidualBandwidth) = 0
```

Il documento `integrated_baseline_metadata.json` conferma:

```text
sourceRun = log-20260603-174645-MaGaIntegratedStudy
unitStatus = PROVEN_BITS_PER_SECOND
bucketBoundaryPolicy = START_TIMESTAMP_FOR_INTERVAL
availableFromPolicy = SAFE_AFTER_TIMESTAMP
residualPolicy = NOMINAL_MINUS_OBSERVED_DIAGNOSTIC
terminalBuckets = 0
```

## Cosa non e' stato ancora implementato

La pipeline 10A-10F non implementa ancora:

```text
candidati LOCAL
candidati VEHICLE / V2V
banda V2V calibrata
CPU locale calibrata
assegnazione task alle finestre
SystemSnapshot finali
replay JSON_SEQUENCE
replay JSON_TIME
bridge live
scenario realistico finale
```

Questi elementi sono lasciati fuori intenzionalmente. Le fasi fin qui chiuse dimostrano che e' possibile passare da una run MOSAIC integrata a stream normalizzati e candidati remoti diagnostici, ma non ancora a una sequenza completa di decisioni MA-GA.

## Prossimo passo

Il prossimo passo naturale e':

```text
Fase 10G - candidati LOCAL e V2V diretti
```

Prima di implementarla serve discutere alcuni punti tecnici:

- banda V2V nominale diagnostica;
- calibrazione scientifica futura;
- pool source-aware per coppia diretta;
- distanza massima single-hop;
- uso della policy `DIRECT_SINGLEHOP_ONLY`;
- distinzione tra diagnostica iniziale e scenario realistico finale.

La ragione per non passare automaticamente alla 10G e' che i candidati V2V introducono assunzioni nuove. La 10F ha potuto appoggiarsi a Cell, gateway e banda residua gia' osservati nella baseline integrata. La 10G dovra' invece decidere come rappresentare un canale diretto veicolo-veicolo in assenza di una banda V2V calibrata e senza confondere SNS, ad hoc configuration e modello finale MA-GA.

## Fonti lette

Documentazione:

```text
data/docs/mosaic-study/10D_integrated_cell_diagnostic_calibration.md
data/docs/mosaic-study/10F_integrated_offline_exporter_alignment.md
tools/mosaic-offline-exporter/README.md
```

Script exporter:

```text
tools/mosaic-offline-exporter/export_task_stream.py
tools/mosaic-offline-exporter/export_vehicle_state_stream.py
tools/mosaic-offline-exporter/export_infrastructure_snapshot.py
tools/mosaic-offline-exporter/export_cell_network_diagnostics.py
tools/mosaic-offline-exporter/export_cell_network_streams.py
tools/mosaic-offline-exporter/export_access_link_preview.py
tools/mosaic-offline-exporter/export_remote_candidate_preview.py
```

Artefatti generati:

```text
data/mosaic-study/task_stream.csv
data/mosaic-study/vehicle_state_stream.csv
data/mosaic-study/infrastructure_snapshot.json
data/mosaic-study/cell_handover_stream.csv
data/mosaic-study/cell_bandwidth_stream.csv
data/mosaic-study/access_link_preview.csv
data/mosaic-study/remote_candidate_preview.csv
data/mosaic-study/diagnostics/cell/integrated_baseline_metadata.json
```

Scenario integrato versionabile:

```text
data/mosaic-scenarios/MaGaIntegratedStudy/scenario_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/mapping/mapping_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/cell/cell_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/cell/network.json
data/mosaic-scenarios/MaGaIntegratedStudy/cell/regions.json
data/mosaic-scenarios/MaGaIntegratedStudy/sns/sns_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/application/application_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/application/ma_ga_cell_traffic_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/application/ma_ga_resource_catalog.json
data/mosaic-scenarios/MaGaIntegratedStudy/application/ma_ga_workload_config.json
data/mosaic-scenarios/MaGaIntegratedStudy/output/output_config.xml
data/mosaic-scenarios/MaGaIntegratedStudy/sumo/sumo_config.json
```

## Conflitti e ambiguita' da ricordare

Sono emerse alcune ambiguita' non bloccanti:

- in `infrastructure_snapshot.json`, `source.scenarioId` vale `UNKNOWN`, anche se i path sorgente puntano correttamente a `data/mosaic-scenarios/MaGaIntegratedStudy/`; sembra un limite del derivatore di `scenarioId` rispetto alla struttura `data/mosaic-scenarios`;
- il catalogo risorse contiene la descrizione storica `MaGaMosaicStudy`, mentre viene usato per `MaGaIntegratedStudy`;
- `policies.bandwidthResidualPolicy` nello snapshot conserva `NOMINAL_ONLY_FOR_INITIAL_EXPORTER` dal catalogo sorgente, ma la Fase 10F non usa quella policy per i candidati remoti: usa `CELL_BANDWIDTH_STREAM_RESIDUAL` e `MIN_RESIDUAL_CELL_UPLINK_DOWNLINK_DIAGNOSTIC`;
- il server runtime registrato ha profilo `WeatherServer`, nome storico riusato come contenitore del server Cell diagnostico;
- `sns.maximumTtl = 10`, mentre la policy MA-GA V2V pianificata e' `DIRECT_SINGLEHOP_ONLY`;
- `projectedX` e `projectedY` sono vuoti, quindi la distanza 10E usa Haversine su latitudine/longitudine;
- CPU locale e banda V2V restano volutamente `CONFIGURED_VALUE_TO_BE_CALIBRATED`.

Nessuna di queste ambiguita' richiede una modifica automatica per la pipeline 10A-10F. Sono pero' punti da risolvere o discutere prima di passare a snapshot finali, replay del core o candidati V2V.
