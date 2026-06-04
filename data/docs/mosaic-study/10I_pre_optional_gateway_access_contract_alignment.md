# Fase 10I-pre - Riallineamento del contratto snapshot per veicoli senza gateway

## 1. Contesto

Le Fasi 10A-10H hanno prodotto una pipeline offline MOSAIC -> MA-GA completa
fino all'assegnazione dei task alle finestre. La baseline canonica e':

```text
log-20260604-220216-MaGaIntegratedStudy
```

Prima della composizione dei SystemSnapshot JSON finali e' emerso un blocco di
contratto: il core Java trattava ogni `VehicleSnapshot` come se dovesse avere
sempre esattamente un access link attivo. I dati MOSAIC validati contengono
invece veicoli presenti nella simulazione, candidati LOCAL disponibili e spesso
candidati VEHICLE/V2V, ma nessuna RSU nel raggio di copertura.

Questa sottofase non implementa la 10I e non genera snapshot finali. Riallinea
solo il contratto Java necessario per rappresentare correttamente tali finestre.

## 2. Blocco rilevato prima della 10I

Il contratto precedente era:

```text
VehicleSnapshot presente
    -> esattamente un access link active=true
```

Questa regola rendeva invalide finestre reali come:

```text
veicolo presente
    -> candidato LOCAL disponibile
    -> candidati VEHICLE / V2V diretti disponibili
    -> nessun gateway RSU attivo
```

Il problema non era nei dati MOSAIC. Un veicolo puo' essere presente nella
simulazione senza essere coperto da una RSU. Inventare un gateway attivo avrebbe
alterato copertura, banda gateway, candidati EDGE/CLOUD e fitness.

## 3. Contratto aggiornato

La cardinalita' degli access link attivi per veicolo diventa:

```text
activeLinks(vehicle) in {0, 1}
```

Il valore `0` e' valido per veicoli LOCAL-only o V2V-only. Il valore `1` e'
valido per veicoli con accesso infrastrutturale. Un valore maggiore di `1`
resta invalido.

La semantica strict e' preservata per i candidati remoti infrastrutturali:

```text
EDGE  -> richiede access link attivo e gateway risolvibile
CLOUD -> richiede access link attivo e gateway risolvibile
```

La semantica senza gateway e' ammessa per:

```text
LOCAL       -> non richiede gateway
VEHICLE/V2V -> usa pool DIRECT_V2V, non access link infrastrutturale
```

Non vengono introdotti gateway, link o pool fittizi.

## 4. Classi modificate

`SnapshotValidator`

- accetta zero o un access link attivo per veicolo;
- rifiuta ancora piu' di un link attivo;
- impone una guardia esplicita per EDGE/CLOUD prima della risoluzione del pool;
- impedisce che un `bandwidthPoolId` esplicito renda valido un EDGE/CLOUD senza
  gateway attivo.

`AccessLinkResolver`

- mantiene `requireActiveAccessLink(...)` come API strict;
- aggiunge `findActiveAccessLink(...)`;
- restituisce `Optional.empty()` se il veicolo non ha link attivo;
- solleva errore se i link attivi sono piu' di uno.

`AccessLinkMetricsEstimator`

- mantiene `estimateActiveLink(...)` come API strict;
- aggiunge `estimateActiveLinkIfPresent(...)`;
- non crea metriche sintetiche quando il gateway manca.

`LinkDynamicityCalculator`

- usa l'estimator opzionale;
- assegna qualita' `0.0` ai veicoli senza access link attivo;
- conserva la formula di `Dl(k)` come media delle differenze sui veicoli comuni.

`CoverageReferenceCalculator`

- calcola `T_coverage_ref` solo sui veicoli con access link attivo;
- esclude i veicoli scoperti dal denominatore;
- restituisce `0.0` se nessun veicolo ha access link attivo;
- mantiene `hasReferenceCoverage(snapshot) == false` in assenza di copertura.

## 5. Classi ispezionate ma non modificate

`CandidatePrefilter` resta coerente: LOCAL e VEHICLE non dipendono dal gateway,
mentre CLOUD viene scartato se il link attivo manca.

`CoverageEstimator` resta strict per CLOUD e non richiede modifiche per LOCAL o
VEHICLE.

`BandwidthPoolResolver` non e' stato modificato. V2V continua a usare pool
espliciti `DIRECT_V2V`; EDGE/CLOUD restano vincolati dal validator alla presenza
di un gateway attivo.

Sono state ispezionate anche le classi snapshot e nodo:

```text
SystemSnapshot
AccessLinkSnapshot
AccessGatewaySnapshot
BandwidthPoolSnapshot
NodeCandidate
NodeType
```

## 6. Test introdotti

Non essendoci un framework di test attivo in `src/test`, e' stato creato un
harness esterno:

```text
tools/snapshot-contract-validation/
```

Il tool compila il progetto e usa loader, validator e model reali. Le fixture
JSON validano i casi minimi richiesti:

```text
A LOCAL-only senza gateway attivo              -> accettato
B V2V-only senza gateway attivo                -> accettato
C scenario misto gateway/local/V2V             -> accettato
D piu' link attivi per lo stesso veicolo        -> rifiutato
E CLOUD senza access link attivo               -> rifiutato
F EDGE senza access link attivo                -> rifiutato
G dinamicita' covered -> uncovered             -> nessuna eccezione, variazione > 0
G dinamicita' uncovered -> uncovered           -> variazione 0
H coverage reference con popolazione mista     -> media sui soli link attivi
I coverage reference senza gateway             -> 0.0 e hasReferenceCoverage=false
```

Risultato:

```text
testsExecuted = 9
testsPassed = 9
testsFailed = 0
```

La compilazione usa JDK 21. Il compilatore produce classi ed exit code `0`, ma
su questa macchina stampa un warning/bug `AccessDeniedException` durante la
chiusura di un JAR Jackson. L'esecuzione runtime del harness ha caricato le
classi e superato tutti i casi.

## 7. Diagnostica sulla baseline MOSAIC

La diagnostica read-only sugli output 10A-10H mostra:

```text
windowsRead = 36
windowsWithVehicles = 35
windowsWithVehiclesWithoutActiveGateway = 35
windowsWithRemoteCandidates = 25
windowsWithOnlyLocalOrV2vCandidates = 10
localVehicleStatesRead = 1824
activeGatewayStatesRead = 564
statesWithoutActiveGateway = 1260
tasksInWindowsWithoutActiveGateway = 472
```

Esempi:

```text
10 s:
    veicoli = veh_0
    gateway attivi = 0
    task = 3
    candidati remoti = 0
    candidati V2V = 0

15 s:
    veicoli = veh_0, veh_1, veh_2
    gateway attivi = 0
    task = 5
    candidati remoti = 0
    candidati V2V = 6

30 s:
    veicoli = veh_0, veh_1, veh_2, veh_3, veh_4, veh_5
    gateway attivi = 0
    task = 12
    candidati remoti = 0
    candidati V2V = 28

180 s:
    veicoli = 12
    gateway attivi = 0
    task = 23
    candidati remoti = 0
    candidati V2V = 0
```

Queste finestre devono poter essere rappresentate senza placeholder.

## 8. Readiness per la Fase 10I

La 10I puo' riprendere perche':

- i veicoli presenti senza RSU attiva sono ora rappresentabili;
- LOCAL e V2V non dipendono piu' da link infrastrutturali fittizi;
- EDGE e CLOUD restano strict e richiedono gateway attivo;
- la dinamicita' link gestisce transizioni coperto/non coperto;
- il riferimento di copertura usa solo link reali;
- non sono stati introdotti placeholder;
- i test obbligatori sono passati.

La futura 10I dovra' ancora comporre i SystemSnapshot JSON finali e validarli
con loader e validator Java. Questa sottofase non lo fa.

## 9. Attivita' escluse

Non sono stati implementati:

```text
export_system_snapshots.py
SystemSnapshot JSON MOSAIC finali
snapshot_manifest.csv
phase_10i_validation.json
Fase 10J
replay JSON_SEQUENCE
replay JSON_TIME
bridge live
GA
placeholder di gateway, link o pool
```
