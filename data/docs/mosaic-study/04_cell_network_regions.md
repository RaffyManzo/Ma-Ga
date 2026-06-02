# 04 - Studio delle regioni Cell e variazioni di rete

Data: 2026-06-02.

Questo documento riassume la Fase 4 dello studio MOSAIC: verificare se MOSAIC Cell applica davvero proprieta' di rete diverse in aree geografiche differenti e se tali differenze sono osservabili nei log, nei messaggi e nelle misure di banda.

## Scenario

Scenario analizzato:

```text
tmp/mosaic-25.2/scenarios/MaGaCellStudy
```

Run analizzata:

```text
tmp/mosaic-25.2/logs/log-20260602-170713-MaGaCellStudy
```

La simulazione termina correttamente:

```text
Simulation ended after 1000s of 1000s
Federate attivi: application, cell, environment, mapping, sns, sumo, output
```

Restano i warning gia' noti:

- SUMO installato in versione `1.26.0`, non formalmente supportata da MOSAIC 25.2;
- alcuni emergency braking SUMO loggati come `ERROR SumoAmbassador`, ma non bloccanti.

## Configurazione Cell

`cell/cell_config.json` abilita le misure aggregate:

```json
"bandwidthMeasurementInterval": 1,
"bandwidthMeasurementCompression": false,
"bandwidthMeasurements": [
  { "fromRegion": "*", "toRegion": "*", "transmissionMode": "UplinkUnicast" },
  { "fromRegion": "*", "toRegion": "*", "transmissionMode": "DownlinkUnicast" },
  { "fromRegion": "*", "toRegion": "*", "transmissionMode": "DownlinkMulticast" }
]
```

La run produce:

```text
bandwidthMeasurements/ALL#ALL#ALL#Up.csv
bandwidthMeasurements/ALL#ALL#ALL#Dn.csv
```

## Regioni caricate

`Cell.log` conferma il caricamento di due regioni:

| Regione | Area | Uplink | Downlink | Delay | Loss |
| --- | --- | ---: | ---: | ---: | ---: |
| `region_north_normal` | lat 52.657..52.632, lon 13.563..13.571 | 40 Mbps | 60 Mbps | 80 ms | 0% |
| `region_central_degraded` | lat 52.632..52.618, lon 13.559..13.567 | 8 Mbps | 12 Mbps | 250 ms | 5% |

La rete globale resta configurata come fallback:

| Rete | Uplink | Downlink | Delay |
| --- | ---: | ---: | ---: |
| `globalNetwork` | 28 Mbps | 42.2 Mbps | 100 ms |

Il server meteo del tutorial ha ritardo dedicato di 200 ms verso/da Cell.

## Attraversamento delle regioni

I veicoli cellulari rilevati nella run sono:

```text
veh_3, veh_13, veh_23, veh_33, veh_43, veh_53,
veh_63, veh_73, veh_83, veh_93, veh_103, veh_113
```

Tutti attraversano `region_north_normal`. Solo `veh_3` e `veh_13` attraversano anche `region_central_degraded` durante la fase in cui trasmettono messaggi cellulari verso il server.

Esempi osservati:

| Veicolo | Regione | Prima osservazione | Note |
| --- | --- | ---: | --- |
| `veh_3` | `region_north_normal` | 13 s | entra nella regione normale dopo la registrazione |
| `veh_3` | `region_central_degraded` | 90 s | qui aumenta la latenza |
| `veh_13` | `region_north_normal` | 33 s | entra nella regione normale dopo la registrazione |
| `veh_13` | `region_central_degraded` | 116 s | qui aumenta la latenza |

## Ritardi osservati

L'effetto delle regioni e' visibile nei messaggi `CELL_TOPOCAST` verso `server_0`.

| Sorgente | Regione trasmissione | TX | RX | Persi | Delay medio |
| --- | --- | ---: | ---: | ---: | ---: |
| `veh_3` | `region_north_normal` | 3 | 3 | 0 | 0.280 s |
| `veh_3` | `region_central_degraded` | 202 | 188 | 14 | 0.450 s |
| `veh_13` | `region_north_normal` | 9 | 9 | 0 | 0.299 s |
| `veh_13` | `region_central_degraded` | 202 | 185 | 17 | 0.450 s |

L'interpretazione piu' coerente e':

```text
region_north_normal:
  80 ms regione mobile + 200 ms server = circa 280 ms

region_central_degraded:
  250 ms regione mobile + 200 ms server = circa 450 ms
```

La perdita osservata nella regione degradata e' coerente con `lossProbability = 0.05`, anche se il numero esatto dipende dal flusso di messaggi e dal seed.

## Misure di banda

Le misure esportate sono aggregate per regione e direzione.

| File | Regione | Intervalli non zero | Somma | Massimo |
| --- | --- | ---: | ---: | ---: |
| `ALL#ALL#ALL#Up.csv` | `region_north_normal` | 11 | 108240 | 9840 |
| `ALL#ALL#ALL#Up.csv` | `region_central_degraded` | 221 | 3680160 | 19680 |
| `ALL#ALL#ALL#Up.csv` | `globalNetwork` | 0 | 0 | 0 |
| `ALL#ALL#ALL#Dn.csv` | `region_north_normal` | 105 | 8487000 | 123000 |
| `ALL#ALL#ALL#Dn.csv` | `region_central_degraded` | 119 | 1629504 | 15744 |
| `ALL#ALL#ALL#Dn.csv` | `globalNetwork` | 275 | 33574080 | 236160 |

Queste misure dimostrano traffico osservato, non banda residua direttamente allocabile dal GA.

## Risposte alle domande della fase

1. Le regioni vengono caricate correttamente?

Si'. `Cell.log` mostra entrambe le regioni con coordinate, delay, capacita' e loss attesi.

2. I veicoli le attraversano realmente?

Si'. L'attraversamento si ricostruisce incrociando `VehicleUpdates` e bounding box delle regioni. Tutti i veicoli cellulari attraversano `region_north_normal`; `veh_3` e `veh_13` attraversano anche `region_central_degraded`.

3. I ritardi dei messaggi cambiano?

Si'. I messaggi in `region_north_normal` arrivano intorno a 0.280 s; quelli in `region_central_degraded` arrivano intorno a 0.450 s.

4. La capacita' configurata influenza le misure esportate?

Le misure esportate distinguono le regioni e mostrano traffico non zero per le regioni attraversate. Tuttavia non rappresentano automaticamente la banda residua per MA-GA.

5. MOSAIC espone direttamente gli aggiornamenti di handover regionali?

In questa run no: senza subscription/output dedicato gli handover non compaiono in `output.csv`. La Fase 5 ha poi aggiunto l'export di `CellularHandoverUpdates`.

6. Le misure Cell sono sufficienti per valorizzare `availableBandwidth`?

Non da sole. Servono per stimare traffico osservato e validare la configurazione, ma `availableBandwidth` MA-GA richiede una policy:

```text
availableBandwidth =
  capacita' nominale della regione/pool
  - traffico osservato
  - banda gia' riservata dalle decisioni MA-GA
```

## Implicazione per MA-GA

Le regioni Cell sono una buona sorgente per ritardi, perdita e capacita' nominale o misurata. Non sono pero' gateway fisici MA-GA.

La distinzione resta:

```text
regione Cell
  = condizione di rete geografica

gateway MA-GA
  = punto di accesso radio fisico, ad esempio RSU
```

La fase successiva deve quindi esportare esplicitamente gli handover regionali e poi introdurre RSU/gateway MA-GA separati.

