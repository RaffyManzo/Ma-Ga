# Fase 13A - API probe e skeleton runtime MOSAIC

## 1. Obiettivo

La Fase 13A crea un probe runtime isolato per verificare concretamente il
lifecycle delle applicazioni MOSAIC e le API minime necessarie al futuro bridge
live MA-GA. Il probe non produce `SystemSnapshot`, non invoca MA-GA e non
implementa cache o bridge live.

## 2. Perche' la 13A e' isolata

La Fase 12 ha definito l'architettura
`CENTRALIZED_LIVE_COORDINATOR_WITH_CAUSAL_CACHE`. Prima di implementare cache,
assembler o coordinator reale, la 13A verifica se MOSAIC 25.2 consente davvero
di osservare lifecycle, tempo simulato, update veicolo, posizione proiettata,
speed, radio locale e tick periodici su server.

## 3. Scenario probe

Scenario creato:

```text
data/mosaic-scenarios/MaGaLiveBridgeProbe
```

Lo scenario usa:

- `application = true`;
- `sumo = true`;
- `sns = true`;
- `output = true`;
- `cell = false`;
- `environment = false`.

Lo scenario canonico `data/mosaic-scenarios/MaGaIntegratedStudy` non e' stato
modificato.

## 4. Applicazione veicolo

Classe:

```text
org.eclipse.mosaic.app.maga.liveprobe.MaGaLiveVehicleApiProbeApp
```

Responsabilita':

- log `LIVE_PROBE_VEHICLE_START`;
- log `LIVE_PROBE_VEHICLE_UPDATE`;
- log `LIVE_PROBE_VEHICLE_STOP`;
- lettura di `getOs().getSimulationTime()`;
- lettura di `getOs().getId()`;
- lettura di `VehicleData.getProjectedPosition()`;
- lettura di `VehicleData.getSpeed()`;
- lettura di `getOs().getAdHocModule().isEnabled()`.

L'app non invia messaggi e non crea candidati, task o snapshot.

## 5. Coordinator server

Classe:

```text
org.eclipse.mosaic.app.maga.liveprobe.MaGaLiveCoordinatorApiProbeApp
```

Host MOSAIC:

```text
server_0 / MaGaLiveProbeCoordinator
```

Il coordinator legge `tickIntervalMs` dal file scenario-local
`application/ma_ga_live_probe_config.json`, programma eventi locali e logga:

- `LIVE_PROBE_COORDINATOR_START`;
- `LIVE_PROBE_COORDINATOR_TICK`;
- `LIVE_PROBE_COORDINATOR_STOP`.

## 6. Lifecycle

La run locale `log-20260605-103006-MaGaLiveBridgeProbe` ha prodotto:

```text
coordinatorStarts = 1
coordinatorStops = 1
vehicleStarts = 4
vehicleStops = 4
```

Questo conferma startup e shutdown su server e veicoli.

## 7. Projected position

API verificata sui JAR locali e usata dal probe:

```text
VehicleData.getProjectedPosition()
CartesianPoint.getX()
CartesianPoint.getY()
```

Risultato validazione:

```text
projectedPositionSamples = 36
invalidProjectedPositions = 0
```

## 8. Speed

API usata:

```text
VehicleData.getSpeed()
```

Risultato validazione:

```text
speedSamples = 36
invalidSpeedSamples = 0
```

## 9. Radio locale

Per attivare la radio locale lo scenario riusa:

```text
org.eclipse.mosaic.app.maga.adhocradio.MaGaAdHocRadioDiagnosticApp
```

Il probe veicolo legge:

```text
getOs().getAdHocModule().isEnabled()
```

Risultato validazione:

```text
adHocStateSamples = 36
```

## 10. Event scheduling

API verificata:

```text
getOs().getEventManager().addEvent(new Event(nextTickTime, this))
```

Risultato validazione:

```text
coordinatorTicks = 20
coordinatorTickTimes = 1s .. 20s
```

## 11. Assenza di traffico artificiale

Il sorgente `tools/mosaic-live-api-probe/src` non contiene chiamate a invio V2X.
La run validata riporta inoltre:

```text
artificialV2xSendCallsInProbeSource = 0
v2xTransmissionEventsInOutput = 0
v2xReceptionEventsInOutput = 0
```

## 12. Build

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-api-probe\build.ps1
```

Output principale:

```text
Build completed: tools/mosaic-live-api-probe/out/maga-live-api-probe.jar
Copied live probe JAR to versioned scenario
```

Nota Windows: `javac` ha stampato un `AccessDeniedException` in chiusura ZIP,
ma il processo e' uscito con codice 0 e il JAR e' stato creato e verificato.

## 13. Deploy

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-api-probe\deploy.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Deploy effettuato solo su:

```text
tmp/mosaic-25.2/scenarios/MaGaLiveBridgeProbe
```

## 14. Run

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-api-probe\run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

MOSAIC ha completato 20 secondi simulati:

```text
Simulation ended after 20s of 20s (100%)
Simulation finished: 101
```

## 15. Validate

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-api-probe\validate.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Diagnostica:

```text
data/mosaic-study/diagnostics/phase_13a_live_api_probe_validation.json
```

Stato:

```text
phase13aStatus = COMPLETED
readyForPhase13B = true
errors = []
```

## 16. Log osservati

Run:

```text
tmp/mosaic-25.2/logs/log-20260605-103006-MaGaLiveBridgeProbe
```

Log applicativi letti:

```text
apps/server_0/MaGaLiveCoordinatorApiProbeApp.log
apps/veh_*/MaGaLiveVehicleApiProbeApp.log
apps/veh_*/MaGaAdHocRadioDiagnosticApp.log
```

## 17. Limiti

Il probe conferma osservabilita' e lifecycle, ma non dimostra ancora:

- cache causale condivisa;
- ingest veicolo -> coordinator;
- task generation live;
- adapter Cell runtime;
- assembler `SystemSnapshot`;
- bridge concreto;
- scheduling MA-GA;
- applicazione strategie;
- worker asincrono;
- policy overrun.

## 18. Cosa non e' ancora implementato

Non sono stati implementati:

- `LiveStateCache`;
- `LiveSystemSnapshotAssembler`;
- `MosaicSnapshotBridge` concreto;
- `MosaicSystemStateSource` live;
- `TemporalWindowManager` live;
- MA-GA live;
- strategy applier;
- worker asincrono;
- policy overrun;
- adapter Cell runtime;
- generazione task live.

## 19. Readiness 13B

La Fase 13A e' pronta per la 13B perche':

- il coordinator puo' vivere su un server MOSAIC;
- `EventManager` produce tick periodici riproducibili;
- i veicoli producono update osservabili;
- projected position e speed sono finite;
- lo stato radio ad-hoc locale e' leggibile;
- il probe non introduce traffico V2X;
- core e scenario canonico restano non modificati.
