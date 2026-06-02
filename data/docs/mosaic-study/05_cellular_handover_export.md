# 05 - Export degli handover regionali Cell

Data: 2026-06-02.

Questo documento chiude la Fase 5 dello studio MOSAIC: verificare se MOSAIC espone direttamente il passaggio dei veicoli tra regioni Cell.

## Scenario

Scenario analizzato:

```text
tmp/mosaic-25.2/scenarios/MaGaCellStudy
```

Run con export handover:

```text
tmp/mosaic-25.2/logs/log-20260602-172233-MaGaCellStudy
```

Le regioni Cell attive sono:

```text
region_north_normal
region_central_degraded
globalNetwork
```

`globalNetwork` rappresenta la rete globale/fallback fuori dalle regioni configurate.

## Export configurato

E' stato aggiunto l'export dell'interazione:

```text
CellularHandoverUpdates
```

nel file:

```text
output/output_config.xml
```

La run produce righe `CELLULAR_HANDOVER` in `output.csv`.

Formato osservato:

```text
CELLULAR_HANDOVER;timeNs;vehicleId;previousRegion;currentRegion
```

Esempi:

```text
CELLULAR_HANDOVER;12000000000;veh_3;null;region_north_normal
CELLULAR_HANDOVER;90000000000;veh_3;region_north_normal;region_central_degraded
CELLULAR_HANDOVER;114000000000;veh_23;region_north_normal;globalNetwork
```

## Eventi esportati

`output.csv` contiene 38 righe `CELLULAR_HANDOVER`.

Distribuzione completa:

| Transizione | Conteggio | Significato |
| --- | ---: | --- |
| `null -> region_north_normal` | 12 | registrazione iniziale dei veicoli cellulari in una regione |
| `region_north_normal -> region_central_degraded` | 2 | movimento reale verso regione degradata |
| `region_central_degraded -> globalNetwork` | 2 | uscita reale dalla regione degradata |
| `region_north_normal -> globalNetwork` | 10 | uscita reale dalla regione normale |
| `globalNetwork -> null` | 12 | rimozione/fine presenza del veicolo nella simulazione |

Le transizioni effettive dovute al movimento sono quindi 14:

| Transizione reale | Conteggio |
| --- | ---: |
| `region_north_normal -> region_central_degraded` | 2 |
| `region_central_degraded -> globalNetwork` | 2 |
| `region_north_normal -> globalNetwork` | 10 |

Gli altri 24 eventi rappresentano registrazione iniziale o rimozione dei veicoli.

## Veicoli coinvolti

Gli handover iniziali registrano i veicoli cellulari nella regione corrente. Gli eventi di movimento confermano quanto osservato nella Fase 4:

- `veh_3` passa da `region_north_normal` a `region_central_degraded`;
- `veh_13` passa da `region_north_normal` a `region_central_degraded`;
- altri veicoli cellulari escono da `region_north_normal` verso `globalNetwork` senza attraversare la regione degradata.

## Risultato

L'export di `CellularHandoverUpdates` e' sufficiente per ottenere:

```text
time
vehicleId
previousRegion
currentRegion
```

Non serve piu' creare una observer app Java dedicata come `CellRegionObserverApp` per questa fase. L'osservabilita' richiesta e' gia' disponibile tramite l'output federate, una volta configurata la subscription corretta.

## Interpretazione per MA-GA

Gli handover regionali sono utili per:

- validare che i veicoli attraversano regioni con condizioni di rete diverse;
- ricostruire lo stato radio regionale nel tempo;
- alimentare diagnostiche su mobilita' e connettivita';
- supportare future policy di banda e delay region-aware.

Ma gli handover regionali non equivalgono a handover di gateway MA-GA.

La distinzione resta fondamentale:

```text
regione Cell
  = area geografica con proprieta' di rete diverse

gateway MA-GA
  = punto di accesso radio fisico, ad esempio una RSU
```

Quindi un cambio:

```text
region_north_normal -> region_central_degraded
```

indica che cambiano le condizioni di rete applicate da Cell. Non indica che il veicolo sia passato da `rsu_north` a `rsu_south`.

## Decisione

Per lo studio MOSAIC:

- la Fase 5 e' completata;
- `CellularHandoverUpdates` va usato nell'exporter offline e, in seguito, nel bridge live;
- non serve una observer app Java per registrare transizioni regionali;
- le regioni Cell restano separate dai gateway MA-GA.

## Prossima fase

La fase successiva e' introdurre gateway espliciti e risorse MA-GA separate:

```text
RSU fisiche
  -> AccessGatewaySnapshot

pool di banda associati ai gateway
  -> BandwidthPoolSnapshot

nodi EDGE/CLOUD raggiungibili
  -> NodeCandidate
```

Servira' quindi uno scenario dedicato con RSU e un catalogo risorse separato, ad esempio:

```text
ma_ga_resource_catalog.json
```

Il catalogo dovra' collegare:

```text
gateway radio
pool di banda
nodo EDGE associato
nodo CLOUD raggiungibile
capacita' CPU
ritardi di propagazione
```

Solo dopo questa separazione il bridge potra' costruire correttamente:

```text
AccessGatewaySnapshot
AccessLinkSnapshot
BandwidthPoolSnapshot
NodeCandidate
```

