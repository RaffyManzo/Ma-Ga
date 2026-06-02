# 01 - Installazione e prima run

Data: 2026-06-02.

## Materiale locale

- ZIP MOSAIC: `C:\Users\raffa\Downloads\eclipse-mosaic-25.2.zip`
- Estrazione di studio: `tmp/mosaic-25.2`
- Scenario base: `tmp/mosaic-25.2/scenarios/Barnim`
- Scenario temporaneo con Cell attivo: `tmp/mosaic-25.2/scenarios/BarnimCell`

## Prerequisiti verificati

```text
java version "17.0.12" 2024-07-16 LTS
SUMO_HOME = C:\Program Files (x86)\Eclipse\Sumo
sumo.exe = C:\Program Files (x86)\Eclipse\Sumo\bin\sumo.exe
sumo --version = Eclipse SUMO 1.26.0
```

Nota: la documentazione/roadmap indicava SUMO 1.25.0 come versione raccomandata. La macchina ha SUMO 1.26.0. MOSAIC avvisa che la versione non e' supportata formalmente, ma la run Barnim e BarnimCell sono terminate correttamente.

## Comandi usati

```powershell
tar -xf C:\Users\raffa\Downloads\eclipse-mosaic-25.2.zip -C tmp\mosaic-25.2
.\mosaic.bat -s Barnim
.\mosaic.bat -s BarnimCell
```

Per `BarnimCell` e' stata fatta una copia temporanea di Barnim e sono stati cambiati solo:

```json
"id": "BarnimCell"
"cell": true
```

## Run Barnim

Esito: corretta.

```text
Log dir: tmp/mosaic-25.2/logs/log-20260602-161309-Barnim
Durata simulata: 1000 s
Durata reale: circa 7.472 s
RTF finale: 133.00
Federate attivi: application, environment, mapping, sns, sumo, output
Cell.log: vuoto perche' cell=false
```

File rilevanti generati:

```text
Application.log
Cell.log
Communication.log
Environment.log
Mapping.log
MOSAIC.log
Navigation.log
Traffic.log
output.csv
apps/<unit>/...
```

Conteggio righe principali in `output.csv`:

| Tipo riga | Conteggio |
| --- | ---: |
| `VEHICLE_UPDATES` | 52319 |
| `V2X_MESSAGE_RECEPTION` | 16612 |
| `V2X_MESSAGE_TRANSMISSION` | 4658 |
| `VEHICLE_REGISTRATION` | 120 |
| `ADHOC_CONFIGURATION` | 48 |
| `TRAFFICLIGHT_REGISTRATION` | 42 |
| `CELL_CONFIGURATION` | 26 |
| `SERVER_REGISTRATION` | 1 |

Warning/criticita':

- `Traffic.log` segnala: SUMO 1.26.0 non formalmente supportato.
- Alcuni warning SUMO sono loggati come `ERROR SumoAmbassador` ma descrivono emergency braking di veicoli specifici. La simulazione termina comunque.

## Run BarnimCell

Esito: corretta.

```text
Log dir: tmp/mosaic-25.2/logs/log-20260602-161704-BarnimCell
Durata simulata: 1000 s
Durata reale: circa 9.605 s
RTF finale: 104.00
Federate attivi: application, environment, mapping, cell, sns, sumo, output
Cell.log: popolato
```

Evidenza da `Cell.log`:

```text
globalNetwork:
  uplink.delay = 100 ms
  uplink.capacity = 28000000 bit/s
  downlink.unicast.delay = 100 ms
  downlink.multicast.delay = 100 ms
  downlink.capacity = 42200000 bit/s

server_0:
  downlink capacity = 100000000000 bit/s
  uplink capacity = 100000000000 bit/s

messaggi processati:
  Upstream = 873
  Geocaster = 872
  Downstream = 2684
```

Conteggio righe principali in `output.csv` con Cell attivo:

| Tipo riga | Conteggio |
| --- | ---: |
| `VEHICLE_UPDATES` | 52099 |
| `V2X_MESSAGE_RECEPTION` | 19195 |
| `V2X_MESSAGE_TRANSMISSION` | 2976 |
| `VEHICLE_REGISTRATION` | 120 |
| `ADHOC_CONFIGURATION` | 48 |
| `TRAFFICLIGHT_REGISTRATION` | 42 |
| `CELL_CONFIGURATION` | 26 |
| `SERVER_REGISTRATION` | 1 |

Esempio di comportamento osservato:

```text
veh_3 invia DENM via CELL_TOPOCAST a t=87.000 s
server_0 riceve a t=87.300 s
server_0 rilancia via CELL_GEOCAST a t=88.000 s
veh_3 riceve a t=88.300 s
```

Questa latenza applicativa osservata e' utile per validare la configurazione, ma non va confusa automaticamente con la sola `propagationDelaySeconds` di MA-GA.

## Criteri di diagnosi iniziali

Run corretta:

- `MOSAIC.log` contiene `Simulation ended after 1000s of 1000s`.
- `output.csv` viene generato.
- i log dei federate attivi non contengono eccezioni bloccanti.

Errore SUMO:

- `Traffic.log` contiene errori di avvio TraCI, configurazione SUMO o incompatibilita' bloccanti.

Errore Mapping:

- `Mapping.log` non riesce a creare vehicle flow, server, RSU o applicazioni associate.

Errore Application:

- `Application.log` o `apps/<unit>/*.log` contiene classi applicative mancanti, jar non caricato o callback fallite.

Errore Cell:

- `Cell.log` non inizializza `Upstream`, `Geocaster`, `Downstream`, oppure non riconosce server/nodi cellulari configurati.

Errore SNS:

- `Communication.log` non inizializza SNS o non configura radio ad-hoc per i nodi attesi.

## Riferimenti ufficiali consultati

- https://eclipse.dev/mosaic/docs/getting_started/run_mosaic/
- https://eclipse.dev/mosaic/docs/getting_started/results/

