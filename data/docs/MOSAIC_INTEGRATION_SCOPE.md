# MOSAIC Integration Scope

Data: 2026-06-02.

Questo documento congela il perimetro iniziale dell'integrazione tra MA-GA ed Eclipse MOSAIC. L'obiettivo non e' trasformare MA-GA in un simulatore completo del ciclo di vita dei task distribuiti. L'obiettivo e' costruire lo stesso `SystemSnapshot` che oggi arriva dai JSON, usando dati osservati, configurati, derivati e generati da workload.

## Regola principale

Il core MA-GA resta invariato durante lo studio di MOSAIC:

- niente modifiche a fitness, repair, mutation, crossover;
- niente modifiche al `TemporalWindowManager`;
- il bridge deve adattarsi al contratto `SystemSnapshot`;
- l'integrazione live deve passare da `MosaicSnapshotBridge` e `MosaicSystemStateSource`.

## Assunzione su issue #20

Il prototipo MA-GA ottimizza periodicamente la strategia di offloading sulla base dello stato osservato nella finestra corrente.

Il modello non rappresenta il ciclo di vita persistente dei task remoti gia' avviati e non simula live migration, checkpoint, trasferimento dello stato applicativo o ripresa dell'esecuzione dopo un handover.

Le variazioni di mobilita' e connettivita' influenzano la selezione dei candidati, la fitness e la successiva riesecuzione dell'algoritmo.

Quindi la issue #20 non va considerata risolta. Va chiusa o riclassificata come estensione fuori perimetro/future work.

## Categorie dati

| Categoria | Significato | Esempi |
| --- | --- | --- |
| Osservata | Prodotta dalla simulazione durante la run | posizione, velocita', ingresso/uscita veicoli, messaggi V2X osservati |
| Configurata | Dichiarata nello scenario o in file MA-GA dedicati | CPU edge/cloud, CPU locale veicolo, raggio RSU, capacita' nominale link |
| Derivata | Calcolata dal bridge da dati osservati/configurati | distanza, gateway attivo, candidati raggiungibili, pool radio assegnato |
| Workload | Generata da logica applicativa non nota a SUMO/Cell/SNS | task, input/output size, cicli CPU, deadline |

## Matrice snapshot

| Campo `SystemSnapshot` | Categoria | Fonte iniziale |
| --- | --- | --- |
| `snapshotId` | Derivata | bridge/exporter, ad esempio `mosaic_t087_window_004` |
| `timeSeconds` | Osservata | tempo MOSAIC/RTI o `OperatingSystem.getSimulationTime()` |
| `vehicles` | Osservata + configurata | SUMO `VehicleUpdates`; `localCpu` da profilo |
| `tasks` | Workload | applicazione MA-GA dedicata, non SUMO |
| `candidateNodes` | Derivata + configurata | catalogo risorse + veicoli + gateway + rete |
| `accessGateways` | Configurata | RSU/gateway dichiarati in Mapping o catalogo MA-GA |
| `accessLinks` | Derivata + osservata | geometria veicolo-gateway + disponibilita' rete |
| `bandwidthPools` | Configurata + osservata | Cell/SNS config, misure o policy di residuo |

## Decisioni iniziali

- SUMO serve per mobilita': veicoli, posizione, velocita', ingresso e uscita.
- Mapping serve per entita' e applicazioni: veicoli, server, RSU quando presenti, profili e app.
- Application Simulator serve per generare workload e, in seguito, ospitare il bridge live.
- Cell serve per rete cellulare, server e regioni; le regioni non sono automaticamente gateway MA-GA.
- SNS serve per V2V ad-hoc diretto.
- Per la prima integrazione, un gateway MA-GA deve essere una RSU/gateway configurata esplicitamente, non una regione Cell.

## Riferimenti locali

- Bridge gia' previsto: `src/window/source/MosaicSnapshotBridge.java`
- Adapter verso il gestore temporale: `src/window/source/MosaicSystemStateSource.java`
- Snapshot: `src/model/snapshot/SystemSnapshot.java`
- Veicoli: `src/model/snapshot/VehicleSnapshot.java`
- Task: `src/model/snapshot/TaskInstance.java`
- Candidati: `src/model/node/NodeCandidate.java`
- Gateway/link/pool: `src/model/snapshot/*Gateway*`, `*AccessLink*`, `*BandwidthPool*`

