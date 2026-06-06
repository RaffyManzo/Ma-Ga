# MOSAIC live API probe

Tool diagnostico versionabile per la Fase 13A. Compila e deploya un JAR
MOSAIC minimale che verifica il lifecycle runtime necessario al futuro bridge
live MA-GA, senza generare `SystemSnapshot` e senza invocare il core MA-GA.

## Cosa verifica

- startup e shutdown di app MOSAIC veicolari e server;
- callback periodici con `EventManager.addEvent(new Event(...))`;
- lettura di `getOs().getSimulationTime()`;
- callback `onVehicleUpdated(...)`;
- `VehicleData.getProjectedPosition().getX()/getY()`;
- `VehicleData.getSpeed()`;
- `getOs().getAdHocModule().isEnabled()`;
- assenza di chiamate sorgente a invio V2X nel probe.

## Scenario

Lo scenario versionabile e':

```text
data/mosaic-scenarios/MaGaLiveBridgeProbe
```

Lo scenario canonico `MaGaIntegratedStudy` non viene modificato. Il probe usa
SUMO, SNS, output federate, RSU, veicoli e un server dedicato che ospita il
coordinator diagnostico. La federate Cell e' disattivata.

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-api-probe\build.ps1
```

Il build:

1. verifica JDK e JAR MOSAIC locali;
2. compila le classi Java;
3. crea `tools/mosaic-live-api-probe/out/maga-live-api-probe.jar`;
4. verifica le entry attese;
5. copia il JAR in
   `data/mosaic-scenarios/MaGaLiveBridgeProbe/application/`.

`out/` e' un output derivato locale.

## Deploy

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-api-probe\deploy.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Il deploy sostituisce solo:

```text
tmp/mosaic-25.2/scenarios/MaGaLiveBridgeProbe
```

Non tocca `MaGaIntegratedStudy` e non rimuove log.

## Run

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-api-probe\run.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Lo script esegue:

```powershell
.\mosaic.bat -s MaGaLiveBridgeProbe
```

dalla root MOSAIC locale.

## Validate

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\mosaic-live-api-probe\validate.ps1 `
  -MosaicRoot ".\tmp\mosaic-25.2"
```

Il validator legge la run piu' recente `*-MaGaLiveBridgeProbe` sotto
`tmp/mosaic-25.2/logs/` e genera:

```text
data/mosaic-study/diagnostics/phase_13a_live_api_probe_validation.json
```

La 13A e' completata solo se lifecycle, tick, update veicolo, projected
position, speed, stato ad-hoc, core non modificato e scenario canonico non
modificato risultano validi.

## Limiti

Questo tool non implementa cache live, assembler, bridge concreto, MA-GA live,
strategy applier, worker asincrono, policy overrun, task generation o adapter
Cell runtime.
