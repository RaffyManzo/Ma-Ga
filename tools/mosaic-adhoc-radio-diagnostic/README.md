# MOSAIC Ad-Hoc Radio Diagnostic

Questo tool contiene una piccola applicazione Eclipse MOSAIC usata dalla Fase 10G della pipeline offline MOSAIC -> MA-GA.

## Scopo

L'applicazione abilita una radio ad-hoc sui veicoli all'avvio, in modo che MOSAIC produca eventi canonici:

```text
ADHOC_CONFIGURATION
```

Questi eventi vengono letti dall'exporter offline 10G per decidere se un candidato `VEHICLE` / V2V diretto puo' essere generato senza assumere che la radio sia sempre attiva.

## Responsabilita' limitata

L'applicazione:

```text
abilita una singola radio ad-hoc;
non invia messaggi V2X;
non genera traffico SNS;
non modifica workload;
non modifica traffico Cell;
non implementa logica MA-GA;
non modifica il core MA-GA.
```

I log applicativi sono solo diagnostici:

```text
ADHOC_RADIO_DIAGNOSTIC_APP_START
ADHOC_RADIO_ENABLE
ADHOC_RADIO_DIAGNOSTIC_APP_STOP
```

Gli eventi usati dagli exporter restano quelli prodotti da MOSAIC in `output.csv`, non questi log applicativi.

## API MOSAIC usate

API verificate localmente con `jar tf` e `javap` sui JAR MOSAIC 25.2:

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

Una sola radio produce configurazione `SINGLE`. La disabilitazione in shutdown produce `OFF`.

## Build

Comando dalla root della repository:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\mosaic-adhoc-radio-diagnostic\build.ps1
```

Il build:

```text
compila le classi Java;
crea tools/mosaic-adhoc-radio-diagnostic/out/maga-adhoc-radio-diagnostic.jar;
verifica le entry del JAR;
copia il JAR negli scenari diagnostici versionabili.
```

## Scenari che lo usano

Il JAR viene copiato in:

```text
data/mosaic-scenarios/MaGaIntegratedStudy/application/
data/mosaic-scenarios/MaGaIntegratedStudyRequest2x/application/
data/mosaic-scenarios/MaGaIntegratedStudyResponse2x/application/
data/mosaic-scenarios/MaGaIntegratedStudyFrequency2x/application/
```

I mapping dei veicoli includono:

```text
org.eclipse.mosaic.app.maga.adhocradio.MaGaAdHocRadioDiagnosticApp
```

insieme alle applicazioni gia' esistenti:

```text
org.eclipse.mosaic.app.maga.workload.MaGaWorkloadDiagnosticApp
org.eclipse.mosaic.app.maga.celltraffic.MaGaCellTrafficDiagnosticVehicleApp
```

## Deploy

Dopo il build, gli scenari versionabili vengono deployati con:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\deploy-mosaic-scenarios.ps1
```

## Relazione con la Fase 10G

La radio attiva non e' da sola un candidato V2V. L'exporter 10G genera candidati `VEHICLE` solo quando, allo stesso timestamp, entrambi i veicoli:

```text
sono presenti;
hanno radio SINGLE osservata;
sono entro il raggio single-hop;
non richiedono look-ahead temporale.
```

La banda V2V resta una configurazione diagnostica del catalogo MA-GA, non una misura SNS.

## Validazione sulla baseline integrata

Dopo build, deploy e nuova run `MaGaIntegratedStudy`, la baseline:

```text
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/
```

contiene:

```text
ADHOC_CONFIGURATION SINGLE = 12
ADHOC_CONFIGURATION OFF = 12
```

Il conteggio delle righe `V2X_MESSAGE*` resta uguale alla baseline precedente. Le prime righe sono ancora messaggi `MaGaCellTrafficDiagnosticMessage` e `MaGaCellTrafficDiagnosticResponseMessage`; la nuova app non invia messaggi V2X.
