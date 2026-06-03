# Fase 12 - Riallineamento exporter offline alla baseline integrata

## Obiettivo

Questa fase riallinea la pipeline offline MOSAIC -> MA-GA alla baseline integrata definitiva:

```text
tmp/mosaic-25.2/logs/log-20260603-174645-MaGaIntegratedStudy/
```

Tutti gli artefatti 10A-10F generati in `data/mosaic-study/` provengono da questa singola run. Le run `MaGaCellStudy`, `MaGaWorkloadStudy`, `MaGaIntegratedStudyRequest2x`, `MaGaIntegratedStudyResponse2x` e `MaGaIntegratedStudyFrequency2x` restano utili per studio e calibrazione, ma non alimentano la pipeline finale riallineata.

## Struttura cartelle

La struttura canonica adottata e':

```text
data/docs/mosaic-study/       documentazione definitiva delle fasi
data/mosaic-scenarios/        sorgenti versionabili degli scenari MOSAIC
data/mosaic-study/            CSV, JSON e diagnostica generati dagli exporter
data/snapshots/               snapshot JSON finali della futura Fase 10I
tools/mosaic-offline-exporter/ exporter offline
tmp/mosaic-25.2/              deployment locale, log ed eseguibili temporanei
```

Il documento di calibrazione Cell integrata e' stato collocato in:

```text
data/docs/mosaic-study/11_integrated_cell_diagnostic_calibration.md
```

## Baseline usata

Run:

```text
log-20260603-174645-MaGaIntegratedStudy
```

Scenario versionabile:

```text
data/mosaic-scenarios/MaGaIntegratedStudy/
```

La pipeline usa:

```text
output.csv
apps/*/MaGaWorkloadDiagnosticApp.log
bandwidthMeasurements/ALL#ALL#ALL#Up.csv
bandwidthMeasurements/ALL#ALL#ALL#Dn.csv
```

## Ispezione bucket Cell

Sono state ispezionate classi locali nel JAR:

```text
tmp/mosaic-25.2/lib/mosaic/mosaic-cell-25.2.jar
```

Classi rilevanti:

```text
org.eclipse.mosaic.fed.cell.viz.PerRegionBandwidthMeasurement
org.eclipse.mosaic.fed.cell.viz.BandwidthMeasurementManager
org.eclipse.mosaic.fed.cell.viz.OnDemandPerRegionBandwidthMeasurements
org.eclipse.mosaic.fed.cell.message.StreamResult
org.eclipse.mosaic.fed.cell.utility.CapacityUtility
org.eclipse.mosaic.fed.cell.utility.RegionCapacityUtility
```

Le evidenze locali mostrano che `PerRegionBandwidthMeasurement`:

```text
1. usa bandwidthMeasurementInterval per costruire bucket temporali;
2. assegna un messaggio ai bucket a partire da start / interval;
3. scrive nel CSV il timestamp csvSize * interval;
4. usa valori di bandwidth consumata gia' espressi come bit/s.
```

La catena di unita' gia' verificata nella calibrazione integrata resta:

```text
EncodedPayload.getEffectiveLength()
    -> CapacityUtility.getMessageLengthWithHeaders(...)
    -> lunghezza effettiva in bit
    -> calculateNeededCapacity(length, delayNs)
    -> consumedBandwidth
    -> bandwidthMeasurements CSV
```

Classificazione:

```text
unitStatus = PROVEN_BITS_PER_SECOND
```

## Policy temporale

La colonna `time` dei CSV Cell rappresenta lo start del bucket:

```text
bucketBoundaryPolicy = START_TIMESTAMP_FOR_INTERVAL
```

Con intervallo di misura pari a `1 s`, una riga:

```text
time = t
```

rappresenta:

```text
[t, t + 1)
```

La misura non deve essere usata per decisioni a tempo `t`, perche' descrive traffico osservato durante il bucket appena iniziato. La policy sicura e':

```text
availableFromPolicy = SAFE_AFTER_TIMESTAMP
availableFromTimeSeconds = bucketEndSeconds
```

Quindi il bucket `[t, t + 1)` diventa disponibile da `t + 1`. Questa scelta evita future look-ahead nella generazione dei candidati.

## Output 10A-10F

### 10A - task stream

Output:

```text
data/mosaic-study/task_stream.csv
```

Esito:

```text
tasksExported = 682
duplicates = 0
```

### 10B - vehicle state stream

Output:

```text
data/mosaic-study/vehicle_state_stream.csv
```

Esito:

```text
registrationsFound = 12
statesExported = 1824
duplicateVehicleStates = 0
updatesBeforeRegistration = 0
```

`projectedX` e `projectedY` restano vuoti. La conversione cartesiana non e' stata introdotta in questa fase.

### 10C - infrastructure snapshot

Output:

```text
data/mosaic-study/infrastructure_snapshot.json
```

Esito:

```text
rsuRegistrationsFound = 2
serverRegistrationsFound = 1
gatewaysExported = 2
bandwidthPoolsExported = 2
executionNodesExported = 3
errorsCount = 0
```

Restano warning non bloccanti per parametri ancora da calibrare:

```text
vehicleProfiles[*].localCpuCyclesPerSecond = null
v2vPolicy.nominalBandwidthBitsPerSecond = null
CONFIGURED_VALUE_TO_BE_CALIBRATED
```

### 10D - Cell stream integrati

Output:

```text
data/mosaic-study/cell_handover_stream.csv
data/mosaic-study/cell_bandwidth_stream.csv
data/mosaic-study/diagnostics/cell/integrated_baseline_metadata.json
```

Esito:

```text
cellularHandoversFound = 48
handoverRegistrations = 12
handoverRegionTransitions = 24
handoverRemovals = 12
bandwidthRecordsExported = 1080
UPLINK = 540
DOWNLINK = 540
terminalBuckets = 0
```

Policy residua diagnostica:

```text
residualCapacityBitsPerSecond =
    max(
        0,
        nominalCapacityBitsPerSecond - trafficObservedBitsPerSecond
    )
```

Questa formula e' una baseline diagnostica. Non rappresenta ancora un modello definitivo di scheduling, contesa radio o allocazione per singolo flusso.

### 10E - access link preview

Output:

```text
data/mosaic-study/access_link_preview.csv
```

Esito:

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

Policy distanza:

```text
distancePolicy = HAVERSINE_FROM_LAT_LON_DIAGNOSTIC
```

Gli handover regionali Cell vengono validati solo come tracciabilita'. Non selezionano la RSU attiva. Nella baseline integrata un handover di registrazione cade prima del primo `VEHICLE_UPDATES` esportato: questo produce un warning non bloccante.

### 10F - remote candidate preview

Output:

```text
data/mosaic-study/remote_candidate_preview.csv
```

Esito:

```text
activeAccessLinksRead = 564
edgeCandidates = 564
cloudCandidates = 564
candidatesExported = 1128
futureLookAheadViolations = 0
```

Policy banda:

```text
bandwidthPolicy = MIN_RESIDUAL_CELL_UPLINK_DOWNLINK_DIAGNOSTIC
bandwidthLookupPolicy = LATEST_SAFE_AVAILABLE_CELL_BUCKET
availableBandwidth = min(uplinkResidualBandwidth, downlinkResidualBandwidth)
```

Il lookup usa solo misure con:

```text
availableFromTimeSeconds <= candidate.timeSeconds
```

Quindi non usa misure future.

Policy ritardo:

```text
propagationDelayPolicy =
    MAX_CELL_UPLINK_DOWNLINK_UNICAST_PLUS_NODE_BASE_DIAGNOSTIC
```

## Limiti

Restano fuori da questa fase:

```text
candidati LOCAL
candidati VEHICLE / V2V
assegnazione dei task a finestre MA-GA
costruzione di SystemSnapshot finali
invocazione del core Java
bridge live
allocazione radio scientificamente calibrata
calcolo cartesiano projectedX/projectedY
```

## Prossimi passi

La prossima fase utile e' la Fase 10G: costruzione diagnostica dei candidati locali e V2V. La Fase 10G dovra' continuare a rispettare la separazione tra:

```text
regione Cell
gateway RSU
handover regionale
cambio di gateway fisico
```

Non e' stata implementata in questa fase.
