# Fase 10G - Preview diagnostica dei candidati LOCAL e V2V diretti

## Scopo

La Fase 10G estende la pipeline offline MOSAIC -> MA-GA con due nuove preview diagnostiche:

```text
LOCAL
VEHICLE / V2V diretto
```

La fase resta fuori dal core MA-GA. Non produce `SystemSnapshot` finali, non assegna task alle finestre, non invoca il core Java, non implementa replay JSON, non introduce un bridge live e non procede alla Fase 10H.

Il contratto del core rimane invariato:

```text
SystemSnapshot
    -> MA-GA core
    -> strategia di offloading
```

## Baseline usata

La baseline canonica resta:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/
```

La Fase 10G legge gli output gia' prodotti dalle fasi precedenti e i cataloghi versionabili dello scenario integrato. Non riesegue MOSAIC e non rigenera gli output 10A-10F.

## Input

Input principali:

```text
data/mosaic-study/vehicle_state_stream.csv
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/output.csv
data/mosaic-scenarios/MaGaIntegratedStudy/application/ma_ga_resource_catalog.json
data/mosaic-scenarios/MaGaIntegratedStudy/sns/sns_config.json
```

`vehicle_state_stream.csv` fornisce stati attivi, tempo, veicolo, latitudine e longitudine. `output.csv` viene ispezionato per gli eventi `ADHOC_CONFIGURATION`. Il catalogo risorse fornisce CPU locale, banda V2V, policy V2V e ritardo conservativo. `sns_config.json` fornisce il raggio single-hop.

## Output

La fase produce:

```text
data/mosaic-study/local_candidate_preview.csv
data/mosaic-study/v2v_candidate_preview.csv
data/mosaic-study/v2v_bandwidth_pool_preview.csv
data/mosaic-study/diagnostics/phase_10g_validation.json
```

Questi file sono preview diagnostiche. Non sono ancora snapshot finali caricabili dal core come sequenza temporale completa.

## Cataloghi aggiornati

Il catalogo canonico aggiornato e':

```text
data/mosaic-scenarios/MaGaIntegratedStudy/application/ma_ga_resource_catalog.json
```

Sono state aggiornate anche le tre copie integrate derivate, verificate come equivalenti tramite hash prima della modifica:

```text
data/mosaic-scenarios/MaGaIntegratedStudyRequest2x/application/ma_ga_resource_catalog.json
data/mosaic-scenarios/MaGaIntegratedStudyResponse2x/application/ma_ga_resource_catalog.json
data/mosaic-scenarios/MaGaIntegratedStudyFrequency2x/application/ma_ga_resource_catalog.json
```

Non sono stati modificati gli scenari storici `MaGaWorkloadStudy`, `MaGaCellStudy`, `MaGaMosaicStudy` o `MaGaV2VStudy`.

## Valori sintetici provvisori

Per completare temporaneamente la diagnostica sono stati inseriti nel catalogo:

```text
CPU locale =
    4000000000 cicli/s

cpuSource =
    DIAGNOSTIC_SYNTHETIC_VALUE

banda V2V nominale =
    10000000 bit/s

bandwidthSource =
    DIAGNOSTIC_SYNTHETIC_VALUE

calibrationStatus =
    TO_BE_REPLACED_DURING_RESOURCE_CALIBRATION
```

Non e' stato aggiunto un campo JSON obbligatorio `calibrationStatus`, perche' il catalogo e gli exporter esistenti usano gia' `cpuSource` e `bandwidthSource` come indicatori di provenienza.

## Motivazione

Il core MA-GA richiede candidati locali affidabili per il fallback. Inoltre i candidati V2V diretti richiedono una CPU del veicolo target e una capacita' di banda condivisa. Nella baseline attuale questi valori non arrivano da SUMO, SNS o misure MOSAIC.

Per questo i valori sono sintetici e provvisori. Servono soltanto per completare la pipeline diagnostica e rendere visibile la forma dei candidati attesi. In futuro dovranno essere sostituiti con provenienza motivata:

```text
LITERATURE_BASED
CALIBRATED_FROM_SCENARIO
```

## Diagnostica e calibrazione finale

La banda V2V nominale non e' una misura prodotta da SNS. SNS descrive raggio, ritardo e configurazione radio, ma non espone una banda residua allocabile per coppia diretta. Il valore corrente e' quindi una configurazione sintetica provvisoria.

La stessa cautela vale per la CPU locale: non deriva da SUMO e non e' ancora calibrata sul dispositivo o su una piattaforma reale.

## Policy LOCAL

La policy LOCAL genera un candidato per ogni stato veicolare attivo:

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
availableCpu = CPU locale letta da car_default
cpuSource = valore letto dal catalogo
propagationDelaySeconds = 0
```

Il core supporta `NodeType.LOCAL` e valida che un candidato LOCAL abbia `executionNodeId` uguale al veicolo sorgente. Non vengono generati pool di banda per LOCAL.

Le fixture sintetiche storiche usano anche naming come `local_vehicle_001`; il core pero' non impone quel pattern. Per MOSAIC e' stato scelto il naming source-aware `local_for_veh_X`, piu' coerente con gli exporter 10F.

## Policy DIRECT_SINGLEHOP_ONLY

La policy V2V resta:

```text
candidatePolicy =
    DIRECT_SINGLEHOP_ONLY
```

Un peer V2V e' candidabile solo se:

```text
source != target
source attivo
target attivo
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

Nella baseline integrata `output.csv` non contiene righe `ADHOC_CONFIGURATION`. Per questo la parte V2V non e' stata dichiarata completata: non e' possibile ricostruire lo stato radio senza supposizioni.

## Interpretazione SINGLE / OFF

Quando gli eventi radio saranno disponibili, l'interpretazione prevista e':

```text
SINGLE
    -> radio ad-hoc attiva

OFF
    -> radio ad-hoc disattiva
```

Modalita' diverse da `SINGLE` e `OFF` devono essere trattate come ambigue fino a nuova verifica.

## Lookup temporale

Per ogni timestamp candidato deve essere usato soltanto l'ultimo evento radio noto con:

```text
eventTime <= candidateTime
```

Non e' ammesso usare eventi futuri. Se un veicolo non ha un evento radio noto prima del timestamp, il suo stato radio non viene inventato.

## Distanza Haversine

La distanza V2V usa temporaneamente latitudine e longitudine:

```text
distancePolicy =
    HAVERSINE_FROM_LAT_LON_DIAGNOSTIC
```

Le coordinate `projectedX` e `projectedY` dello stream veicolare sono ancora vuote. Una distanza cartesiana su coordinate proiettate resta un miglioramento futuro.

## Raggio single-hop

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

`BandwidthPoolType.DIRECT_V2V` e `BandwidthPoolResolver` sono gia' supportati dal core. Il candidato V2V usa `bandwidthPoolId` esplicito; il pool e' fisico e condiviso, mentre il candidato e' direzionale.

## Naming dei candidati

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

Il candidato e' direzionale. Il pool e' condiviso dalla coppia non ordinata.

## Validazioni

La Fase 10G valida:

```text
ogni candidato LOCAL usa CPU letta dal catalogo
ogni candidato LOCAL ha executionNodeId uguale al sourceVehicleId
ogni candidato V2V usa CPU target letta dal catalogo
ogni candidato V2V ha source != target
source e target sono presenti allo stesso timestamp
radio source e target risultano attive
nessun evento radio futuro viene usato
ogni candidato V2V rispetta distanza <= singlehopRadius
ogni pool usa capacita' letta dal catalogo
ogni pool e' condiviso dalle due direzioni quando entrambe esistono
nessun candidateId e' duplicato allo stesso timestamp
nessun bandwidthPoolId e' ambiguo allo stesso timestamp
```

Nel caso della baseline corrente, la validazione registra esplicitamente che gli eventi radio mancano e quindi la generazione V2V e' saltata.

## Risultati prodotti

Sulla baseline integrata:

```text
vehicleStatesRead = 1824
localCandidatesExported = 1824
radioEventsRead = 0
v2vCandidatesExported = 0
v2vPoolsExported = 0
futureLookAheadViolations = 0
```

I CSV V2V sono creati con intestazione, ma senza righe dati. Questo e' intenzionale: generare candidati V2V senza stato radio osservabile violerebbe la policy della fase.

## Limiti

Restano aperti:

```text
assenza di ADHOC_CONFIGURATION nella baseline integrata
nessuna banda V2V misurata o residua da SNS
CPU locale sintetica
distanza Haversine diagnostica
nessun SystemSnapshot finale
nessun replay MA-GA
nessun bridge live
```

## Prossimo passo

Il prossimo passo sara' la Fase 10H, ma non e' implementata qui. Prima di procedere serve decidere come ottenere o generare uno stato radio ad-hoc osservabile e come sostituire i valori sintetici con calibrazioni motivate.
