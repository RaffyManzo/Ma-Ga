# 09 - Generazione diagnostica del workload computazionale

Data: 2026-06-03.

## Obiettivo della fase

La Fase 9 serve a sostituire progressivamente la sezione `tasks` oggi scritta manualmente negli snapshot JSON.

SUMO, Cell e SNS non generano task computazionali MA-GA. SUMO produce mobilita', Cell produce osservazioni e condizioni di rete cellulare, SNS produce osservazioni e configurazioni ad-hoc. Nessuno di questi federate conosce automaticamente la semantica applicativa dei task MA-GA.

Per questo e' stata creata una semplice applicazione MOSAIC diagnostica:

```text
org.eclipse.mosaic.app.maga.workload.MaGaWorkloadDiagnosticApp
```

La classe viene compilata in:

```text
scenarios/MaGaWorkloadStudy/application/maga-workload-diagnostic.jar
```

e associata ai veicoli tramite Mapping.

Questa fase documenta esclusivamente cio' che e' stato implementato e verificato. Non sono stati modificati codice Java, scenari o configurazioni durante la stesura di questo documento.

## Scenario

E' stato creato:

```text
scenarios/MaGaWorkloadStudy/
```

partendo da:

```text
scenarios/MaGaMosaicStudy/
```

In `scenario_config.json` sono configurati:

```text
id = MaGaWorkloadStudy
duration = 180s
application = true
cell = true
sns = true
sumo = true
output = true
```

La durata e' stata ridotta a `180s` perche' la fase e' diagnostica. L'obiettivo non e' valutare prestazioni MA-GA su una simulazione lunga, ma verificare che MOSAIC simulation time possa guidare una generazione periodica e riproducibile di task computazionali.

La run verificata e':

```text
tmp/mosaic-25.2/logs/log-20260603-123856-MaGaWorkloadStudy/
```

`MOSAIC.log` conferma:

```text
Start federation with id 'MaGaWorkloadStudy'
Simulation ended after 180s of 180s (100%)
```

## Mapping

Il Mapping utilizza:

```text
12 veicoli
2 RSU
1 server
```

Le applicazioni predefinite del tutorial sono state rimosse per isolare il workload. Ogni veicolo usa esclusivamente:

```text
org.eclipse.mosaic.app.maga.workload.MaGaWorkloadDiagnosticApp
```

Il blocco veicoli contiene:

```text
maxNumberVehicles = 12
targetFlow = 900
group = WorkloadDiagnostic
application = org.eclipse.mosaic.app.maga.workload.MaGaWorkloadDiagnosticApp
```

`Mapping.log` conferma la creazione del flusso veicolare e dei veicoli `veh_0` ... `veh_11`:

```text
VehicleFlowGenerator[spawningMode=ConstantSpawningMode[timeSpacing=4000000000],lanes=[0, 1],types=[[name=Car,apps=[org.eclipse.mosaic.app.maga.workload.MaGaWorkloadDiagnosticApp]]],pos=1417,route=1]
Creating Vehicle: time=5000000000,name=veh_0,route=1,lane=0,pos=1417,type=Car
Creating Vehicle: time=49000000000,name=veh_11,route=1,lane=1,pos=1417,type=Car
```

RSU e server vengono mantenuti nell'infrastruttura, ma con:

```json
"applications": []
```

`Mapping.log` conferma:

```text
ServerSpawner - Creating Server: ServerSpawner[applications=[]]
RoadSideUnitSpawner - Creating RSU: RoadSideUnitSpawner[position=GeoPoint{lat=52.644000,lon=13.567000,alt=0.00},applications=[]]
RoadSideUnitSpawner - Creating RSU: RoadSideUnitSpawner[position=GeoPoint{lat=52.625000,lon=13.563000,alt=0.00},applications=[]]
```

## Configurazione workload

Il file di configurazione diagnostico e':

```text
scenarios/MaGaWorkloadStudy/application/ma_ga_workload_config.json
```

Il file contiene la semantica prevista per il workload:

```text
runtimeBinding.mode = DOCUMENTATION_ONLY_DIAGNOSTIC
clockSource = MOSAIC_SIMULATION_TIME
activationReference = VEHICLE_APPLICATION_STARTUP
generationMode = DETERMINISTIC_PERIODIC
deliveryPolicy = PENDING_TASKS_AT_NEXT_OPTIMIZATION_WINDOW
consumptionPolicy = REMOVE_AFTER_OFFLOADING_DECISION
remoteExecutionPersistence = NOT_MODELED
taskIdFormat = <profileId>__<vehicleId>__t_<activationTimeMs>
```

Il JSON documenta la configurazione, ma in questa fase non viene ancora letto dinamicamente dalla classe Java. I valori sono replicati intenzionalmente nel codice per mantenere semplice l'esperimento diagnostico.

Questa scelta evita di confondere due obiettivi diversi:

```text
verificare il modello event-driven MOSAIC
    prima

progettare un loader configurabile del workload
    dopo
```

## Profili diagnostici

Sono stati definiti tre profili:

| Profilo | Input bit | Output bit | CPU cycles | Deadline | Intervallo | Offset |
| ------- | --------: | ---------: | ---------: | -------: | ---------: | -----: |
| `perception_light` | `8000000` | `64000` | `1500000000` | `0.5 s` | `5 s` | `1 s` |
| `planning_medium` | `2000000` | `32000` | `3000000000` | `1.0 s` | `10 s` | `2 s` |
| `cooperative_awareness` | `512000` | `16000` | `800000000` | `0.75 s` | `15 s` | `3 s` |

Si tratta di valori sintetici diagnostici, non di una calibrazione scientifica definitiva.

Il loro scopo e' produrre un mix controllato di task:

```text
perception_light
    -> frequente, input grande, deadline stretta

planning_medium
    -> meno frequente, CPU piu' alta, deadline 1 secondo

cooperative_awareness
    -> frequenza intermedia, task piu' leggero
```

## Classe Java

Il JAR contiene:

```text
org/eclipse/mosaic/app/maga/workload/MaGaWorkloadDiagnosticApp.class
org/eclipse/mosaic/app/maga/workload/MaGaWorkloadDiagnosticApp$TaskProfile.class
```

`MaGaWorkloadDiagnosticApp.java` implementa un'applicazione veicolare diagnostica.

La classe:

```text
1. estende AbstractApplication<VehicleOperatingSystem>;
2. scrive WORKLOAD_APP_START in onStartup();
3. pianifica il primo evento di ciascun profilo usando l'offset;
4. riceve gli eventi in processEvent();
5. genera un taskId deterministico;
6. scrive una riga strutturata TASK_ACTIVATION;
7. pianifica l'attivazione successiva dello stesso profilo;
8. scrive WORKLOAD_APP_STOP in onShutdown().
```

Il formato del taskId osservato nei log e':

```text
<profileId>__<vehicleId>__t_<activationTimeMs>
```

Esempio:

```text
perception_light__veh_0__t_7000
```

La classe non introduce custom interaction MOSAIC e non legge ancora il JSON di configurazione.

## Run verificata

La run:

```text
logs/log-20260603-123856-MaGaWorkloadStudy/
```

termina correttamente dopo `180s`.

Le evidenze osservate sono:

| Evidenza | Conteggio |
| -------- | --------: |
| `VEHICLE_REGISTRATION` | 12 |
| `VEHICLE_UPDATES` | 1824 |
| `RSU_REGISTRATION` | 2 |
| `SERVER_REGISTRATION` | 1 |
| `WORKLOAD_APP_START` | 12 |
| `WORKLOAD_APP_STOP` | 12 |
| `TASK_ACTIVATION` | 682 |
| errori applicativi | 0 |
| task duplicati | 0 |
| eventi ignorati | 0 |

I log applicativi sono separati per veicolo:

```text
apps/veh_0/MaGaWorkloadDiagnosticApp.log
apps/veh_1/MaGaWorkloadDiagnosticApp.log
...
apps/veh_11/MaGaWorkloadDiagnosticApp.log
```

Ogni veicolo ha una riga `WORKLOAD_APP_START` e una riga `WORKLOAD_APP_STOP`.

## Distribuzione per profilo

La distribuzione dei task generati per profilo e':

| Profilo | Conteggio |
| ------- | --------: |
| `perception_light` | `369` |
| `planning_medium` | `187` |
| `cooperative_awareness` | `126` |
| Totale | `682` |

La distribuzione e' coerente con le periodicita' configurate: `perception_light` ha intervallo minore e quindi produce piu' attivazioni.

## Distribuzione per veicolo

La distribuzione dei task generati per veicolo e':

| Veicolo | Conteggio |
| ------- | --------: |
| `veh_0` | `65` |
| `veh_1` | `63` |
| `veh_2` | `62` |
| `veh_3` | `61` |
| `veh_4` | `59` |
| `veh_5` | `58` |
| `veh_6` | `55` |
| `veh_7` | `55` |
| `veh_8` | `54` |
| `veh_9` | `52` |
| `veh_10` | `50` |
| `veh_11` | `48` |

I veicoli entrano progressivamente nella simulazione. `Mapping.log` mostra una spaziatura di 4 secondi tra gli inserimenti: `veh_0` entra a 5s, `veh_1` a 9s, fino a `veh_11` a 49s.

Di conseguenza, i veicoli entrati piu' tardi hanno meno tempo utile per generare eventi periodici prima dell'orizzonte diagnostico di `180s`. Gli eventi pianificati oltre il confine terminale della simulazione non vengono processati. Per questo il totale osservato e' coerente con la durata ridotta della run.

## Esempi di TASK_ACTIVATION

Esempi osservati in:

```text
apps/veh_0/MaGaWorkloadDiagnosticApp.log
```

sono:

```text
TASK_ACTIVATION|taskId=perception_light__veh_0__t_7000|sourceVehicleId=veh_0|profileId=perception_light|activationTimeNs=7000000000|activationTimeMs=7000|inputSizeBits=8000000|outputSizeBits=64000|cpuCycles=1500000000|deadlineSeconds=0.5
TASK_ACTIVATION|taskId=planning_medium__veh_0__t_8000|sourceVehicleId=veh_0|profileId=planning_medium|activationTimeNs=8000000000|activationTimeMs=8000|inputSizeBits=2000000|outputSizeBits=32000|cpuCycles=3000000000|deadlineSeconds=1.0
TASK_ACTIVATION|taskId=cooperative_awareness__veh_0__t_9000|sourceVehicleId=veh_0|profileId=cooperative_awareness|activationTimeNs=9000000000|activationTimeMs=9000|inputSizeBits=512000|outputSizeBits=16000|cpuCycles=800000000|deadlineSeconds=0.75
```

Queste righe contengono tutti i campi necessari per costruire un `TaskInstance`:

```text
taskId
sourceVehicleId
inputSizeBits
outputSizeBits
cpuCycles
deadlineSeconds
```

## Risultato

La fase dimostra la catena:

```text
MOSAIC simulation time
        ↓
evento periodico
        ↓
task computazionale sintetico
        ↓
log strutturato
        ↓
dati sufficienti per costruire TaskInstance
```

Questo e' il primo passaggio concreto verso la sostituzione dei task manuali negli snapshot JSON.

Il risultato non cambia il core MA-GA. Stabilisce invece una sorgente applicativa riproducibile da cui il futuro exporter offline potra' costruire `SystemSnapshot.tasks`.

## Limiti ancora presenti

Non sono ancora implementati:

```text
lettura dinamica del JSON
task_stream.csv
SystemSnapshot.tasks
consumo dei task dopo una decisione MA-GA
custom interaction MOSAIC
exporter offline
bridge live
```

Inoltre, la persistenza dell'esecuzione remota resta fuori scope:

```text
remoteExecutionPersistence = NOT_MODELED
```

Questo e' coerente con il perimetro gia' fissato: il prototipo MA-GA ottimizza la decisione di offloading nella finestra corrente, ma non simula live migration, checkpoint o ripresa persistente dei task remoti.

## Roadmap aggiornata

La Fase 9 diagnostica e' completata.

La prossima attivita' e' la Fase 10:

```text
exporter offline verso snapshot JSON MA-GA
```

La Fase 10 dovra' prendere i dati osservati e configurati:

```text
output.csv
log applicativi TASK_ACTIVATION
mapping_config.json
ma_ga_resource_catalog.json
ma_ga_workload_config.json
configurazioni Cell e SNS
```

e produrre snapshot JSON compatibili con il core MA-GA, senza modificare fitness, operatori genetici, repair operator o `TemporalWindowManager`.
