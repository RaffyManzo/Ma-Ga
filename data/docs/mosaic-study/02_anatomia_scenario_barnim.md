# 02 - Anatomia dello scenario Barnim

Data: 2026-06-02.

## File letti

```text
scenarios/Barnim/scenario_config.json
scenarios/Barnim/mapping/mapping_config.json
scenarios/Barnim/sumo/sumo_config.json
scenarios/Barnim/application/application_config.json
scenarios/Barnim/cell/cell_config.json
scenarios/Barnim/cell/network.json
scenarios/Barnim/cell/regions.json
scenarios/Barnim/sns/sns_config.json
scenarios/Barnim/output/output_config.xml
```

## Federate attivi nello scenario base

```json
{
  "application": true,
  "cell": false,
  "environment": true,
  "sns": true,
  "ns3": false,
  "omnetpp": false,
  "output": true,
  "sumo": true
}
```

Barnim base permette gia' di studiare SUMO, Mapping, Application, SNS e Output. Cell e' configurato ma non attivo finche' `cell` resta `false`.

## Mapping

`mapping/mapping_config.json` contiene:

- un prototipo `Car` di classe `ElectricVehicle`;
- un server `WeatherServer`, mappato come `server_0` nella run;
- un flusso veicolare da `startingTime = 5.0 s`;
- `targetFlow = 1800`;
- `maxNumberVehicles = 120`;
- route `1`;
- corsie `[0, 1]`;
- tre gruppi veicolo:
  - `Cellular`, peso 0.1, app `WeatherWarningAppCell` e `SlowDownApp`;
  - `AdHoc`, peso 0.2, app `WeatherWarningApp` e `SlowDownApp`;
  - `Unequipped`, peso 0.7, app `SlowDownApp`.

Origini dati per MA-GA:

- veicoli e profili: Mapping + registrazioni in `output.csv`;
- server: Mapping + `SERVER_REGISTRATION`;
- applicazioni: Mapping + log Application;
- RSU: non presenti in Barnim base, quindi per MA-GA vanno aggiunte in uno scenario dedicato.

## SUMO

`sumo/sumo_config.json`:

```json
{
  "sumoConfigurationFile": "Barnim.sumocfg",
  "updateInterval": "1s",
  "visualizer": false
}
```

`Traffic.log` conferma:

```text
sumoConfig.updateInterval: 1000
Simulation step size is 1.0 sec.
```

Per MA-GA, SUMO e' la fonte primaria per:

- ingresso veicolo;
- uscita veicolo;
- posizione;
- velocita';
- heading;
- road/lane/route se utili per diagnostica.

## Output

`output/output_config.xml` abilita `FileOutputGenerator` e sottoscrive, tra le altre:

- `VehicleUpdates`;
- `VehicleRegistration`;
- `ServerRegistration`;
- `AdHocCommunicationConfiguration`;
- `CellularCommunicationConfiguration`;
- `V2xMessageTransmission`;
- `V2xMessageReception`.

La riga `VEHICLE_UPDATES` di `output.csv` ha questa forma pratica:

```text
VEHICLE_UPDATES;
timeNs;
vehicleId;
speed;
heading;
latitude;
longitude;
altitude;
distanceDriven;
longitudinalAcceleration;
...
routeId;
roadPosition.connectionId;
laneIndex;
...
```

Questa e' gia' sufficiente per un exporter offline di `VehicleSnapshot`, con la sola eccezione di `localCpu`, che deve venire da configurazione MA-GA.

## Cell

File:

```text
cell/cell_config.json
cell/network.json
cell/regions.json
```

`cell_config.json` punta a `network.json` e `regions.json`.

`network.json` in Barnim definisce:

```text
globalNetwork.uplink.delay = 100 ms
globalNetwork.uplink.capacity = 28000000 bit/s
globalNetwork.downlink.unicast.delay = 100 ms
globalNetwork.downlink.multicast.delay = 100 ms
globalNetwork.downlink.capacity = 42200000 bit/s
WeatherServer uplink/downlink delay = 200 ms
defaultDownlinkCapacity/defaultUplinkCapacity = 100 Gbps
```

`regions.json` e' vuoto:

```json
{ "regions": [] }
```

Con `cell=false`, `Cell.log` resta vuoto e non ci sono misure Cell reali.

Con `cell=true`, `Cell.log` conferma l'inizializzazione dei moduli:

```text
Upstream
Geocaster
Downstream
```

e l'abilitazione di server e nodi cellulari. Non sono stati osservati handover perche' non ci sono regioni.

## SNS

`sns/sns_config.json`:

```json
{
  "maximumTtl": 10,
  "singlehopRadius": 709.4,
  "adhocTransmissionModel": {
    "type": "SophisticatedAdhocTransmissionModel"
  },
  "singlehopDelay": {
    "type": "SimpleRandomDelay",
    "steps": 5,
    "minDelay": "0.4 ms",
    "maxDelay": "2.4 ms"
  },
  "singleHopTransmission": {
    "lossProbability": 0.0,
    "maxRetries": 0
  }
}
```

`Communication.log` conferma:

- inizializzazione SNS;
- aggiunta dei veicoli ad-hoc;
- configurazione radio in modo `SINGLE`;
- raggio 709.4 m;
- rimozione dei nodi quando escono dalla simulazione.

Per MA-GA, SNS e' la fonte/policy iniziale per:

- ammissibilita' V2V diretta;
- raggio V2V;
- ritardo V2V;
- perdita V2V;
- pool `DIRECT_V2V`.

## Da quale file provengono i concetti

| Concetto | Fonte Barnim |
| --- | --- |
| Veicoli | `mapping_config.json`, `VehicleRegistration`, `VehicleUpdates` |
| Rotte | `mapping_config.json`, `Barnim.sumocfg`, `Barnim.net.xml`, `VehicleUpdates.routeId` |
| RSU | non presenti in Barnim base; da aggiungere in `mapping_config.json` |
| Server | `mapping_config.json`, `SERVER_REGISTRATION`, `cell/network.json` |
| Applicazioni | `mapping_config.json`, JAR in `application/`, log in `apps/<unit>` |
| SUMO | `sumo/sumo_config.json`, `Barnim.sumocfg`, `Traffic.log` |
| Cell | `scenario_config.json`, `cell/*`, `Cell.log`, `CELL_CONFIGURATION`, messaggi `CELL_*` |
| SNS | `scenario_config.json`, `sns/sns_config.json`, `Communication.log`, `ADHOC_CONFIGURATION`, messaggi ad-hoc |

## Riferimenti ufficiali consultati

- https://eclipse.dev/mosaic/docs/simulators/application_mapping/
- https://eclipse.dev/mosaic/docs/simulators/application_simulator/
- https://eclipse.dev/mosaic/docs/simulators/network_simulator_cell/
- https://eclipse.dev/mosaic/docs/mosaic_configuration/sns_config/

