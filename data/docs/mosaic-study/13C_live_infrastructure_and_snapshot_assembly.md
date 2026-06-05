# Fase 13C - Live infrastructure and snapshot assembly

## 1. Obiettivo

La Fase 13C estende il runtime diagnostico 13B per produrre `SystemSnapshot`
JSON live durante una simulazione MOSAIC isolata. Il flusso validato e':

```text
vehicle callbacks MOSAIC
  -> LiveStateCache 13B
  -> accounting Cell diagnostico controllato
  -> bucket Cell safe-after-timestamp
  -> access link fisici RSU
  -> gateway pool
  -> candidati EDGE/CLOUD
  -> LOCAL/V2V 13B
  -> LiveSystemSnapshotAssembler
  -> Java loader + SnapshotValidator
```

MA-GA non viene invocato. Il bridge live concreto non viene implementato.

## 2. Relazione con 13B

La 13C riusa il tool `tools/mosaic-live-state-layer/`, la `LiveStateCache`, i
task diagnostici, i candidati `LOCAL`, i candidati `VEHICLE` direct single-hop
e i pool `DIRECT_V2V` creati in 13B. Non duplica la cache e non cambia la
semantica V2V:

```text
source != target
entrambi attivi
radio ad-hoc attiva per entrambi
distanza euclidea <= singlehopRadiusMeters
```

## 3. Correzione diagnostica portabile 13A

Prima della 13C e' stata rigenerata la diagnostica 13A con lo script reale:

```text
data/mosaic-study/diagnostics/phase_13a_live_api_probe_validation.json
```

Il JSON versionato usa ora:

```text
latestRunName
latestRunRelativeDir
```

e non contiene `latestRunDir` o path assoluti locali.

## 4. Limite API Cell

Le API applicative MOSAIC 25.2 non espongono direttamente a questo livello:

```text
regione Cell corrente live
handover regionale completo
traffico bucket del federate
banda residua uplink/downlink del federate
```

La 13C non legge log offline durante la simulazione e non presenta la banda
come misura del federate Cell.

## 5. Accounting diagnostico runtime

La sorgente dichiarata e':

```text
DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES
```

I veicoli generano richieste Cell controllate. Il budget downlink diagnostico e'
contabilizzato come risposta controllata associata alla richiesta, perche'
MOSAIC 25.2 non consente l'attivazione del Cell module sul server runtime
`server_0` in questo scenario. Questo limite resta esplicito e non viene
trattato come misura scientifica definitiva.

## 6. Sorgente dei dati

I dati live provengono da:

- `VehicleData.getProjectedPosition()` e `VehicleData.getSpeed()`;
- stato radio locale letto dalla app veicolo 13B;
- task diagnostici configurati;
- infrastruttura statica configurata nello scenario isolato;
- accounting Cell runtime controllato.

## 7. Bucket safe

I bucket Cell usano:

```text
bucketBoundaryPolicy = START_TIMESTAMP_FOR_INTERVAL
availableFromPolicy = SAFE_AFTER_TIMESTAMP
lookupPolicy = LATEST_SAFE_AVAILABLE_CELL_BUCKET
```

Un bucket `[t, t + duration)` e' utilizzabile solo quando:

```text
availableFromTime = t + duration <= tickTime
```

Il gateway pool usa:

```text
min(uplinkResidualBandwidth, downlinkResidualBandwidth)
```

Senza bucket sicuro non viene creato pool gateway.

## 8. Access link fisici

Gli access link sono derivati geometricamente da posizione proiettata live e
gateway statici:

```text
policy = NEAREST_AVAILABLE_GATEWAY_BY_PROJECTED_DISTANCE
available = distance(vehicle, gateway) <= coverageRadiusMeters
active = gateway disponibile piu' vicino
tie-break = gatewayId naturale crescente
```

Ogni veicolo ha zero o un solo link attivo.

## 9. Gateway pool

I pool `GATEWAY` vengono creati solo per pool con ultimo bucket Cell sicuro.
Non vengono prodotti fallback nominali e non vengono scritti pool orfani.

## 10. EDGE

Il candidato `EDGE` e' source-aware e viene prodotto solo se:

```text
source vehicle attivo
active gateway risolvibile
gateway pool sicuro risolvibile
edge node associato al gateway
```

Il candidato include gateway, pool, CPU disponibile, banda disponibile,
propagation delay e geometria del gateway.

## 11. CLOUD

Il candidato `CLOUD` richiede lo stesso gateway attivo e lo stesso pool sicuro.
Non vengono inventate coordinate cloud remote: `nodeX`, `nodeY` e
`coverageRadiusMeters` restano assenti per il cloud.

## 12. Snapshot JSON live

`LiveSystemSnapshotAssembler` genera snapshot compatibili con il loader Java:

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

Gli snapshot includono solo dati con:

```text
sourceTime <= tickTime
availableFromTime <= tickTime
```

Gli snapshot non validabili dal contratto Java, per esempio senza alcun pool di
banda, non vengono scritti.

## 13. Java loader

Il validator 13C compila un harness esterno in:

```text
tools/mosaic-live-state-layer/out/snapshot-harness-classes
```

Il harness usa `JsonSnapshotFolderLoader` senza modificare `src/`.

## 14. Java validator

Ogni snapshot live generato viene validato con:

```text
SnapshotValidator
LocalCandidateInvariantValidator
```

La run validata ha caricato e validato 57 snapshot.

## 15. Causalita'

La diagnostica 13C registra zero violazioni per:

```text
futureVehicleStateViolations
futureTaskActivationViolations
futureCellEventViolations
futureSafeBucketViolations
futureAccessLinkViolations
futureCandidateViolations
futurePoolViolations
```

## 16. Output runtime ignorati

Gli output runtime restano locali:

```text
tmp/mosaic-25.2/logs/<run>/live-state-layer/
tmp/mosaic-25.2/logs/<run>/live-infrastructure-snapshot/
tools/mosaic-live-state-layer/out/
```

Solo la diagnostica sintetica e' versionata.

## 17. Warning

Warning registrati:

```text
WARNING_CELL_BANDWIDTH_IS_DIAGNOSTIC_RUNTIME_ACCOUNTING_NOT_FEDERATE_MEASUREMENT
WARNING_CELL_REGIONAL_HANDOVER_NOT_AVAILABLE_DIRECTLY_LIVE
WARNING_DIAGNOSTIC_CPU_AND_V2V_BANDWIDTH_REQUIRE_FUTURE_CALIBRATION
```

## 18. Limiti

Restano fuori scope:

```text
MosaicSnapshotBridge concreto
MosaicSystemStateSource live
TemporalWindowManager live
MA-GA live
strategy applier
worker GA
policy overrun DeltaT_max
```

La banda Cell e' diagnostica runtime accounting, non una misura del federate.

## 19. Readiness 13D

La Fase 13C e' completata se:

```text
simulationCompleted = true
bucket Cell sicuri > 0
access link attivi > 0
EDGE/CLOUD candidati > 0
snapshot JSON live > 0
java loader failures = 0
java validator failures = 0
violazioni causali = 0
absolutePathsInVersionedDiagnostics = 0
errors = []
```

La diagnostica corrente soddisfa questi criteri ed espone:

```text
phase13cStatus = COMPLETED
readyForPhase13D = true
```
