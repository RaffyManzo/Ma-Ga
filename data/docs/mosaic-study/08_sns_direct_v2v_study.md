# 08 - Studio SNS per candidati V2V diretti

Data: 2026-06-03.

## Obiettivo della fase

La Fase 8 serviva a comprendere quali informazioni SNS puo' fornire per costruire i candidati V2V diretti del MA-GA.

Non sono stati modificati codice Java, exporter o bridge. La fase documenta quanto e' gia' stato eseguito e verificato nello scenario diagnostico.

SNS non produce direttamente gli oggetti `NodeCandidate` e non espone una banda residua allocabile per ogni coppia di veicoli.

SNS fornisce o consente di osservare:

```text
raggio single-hop
ritardo single-hop
loss probability
max retries
maximum TTL
configurazione della radio ad-hoc
trasmissioni e ricezioni V2X
```

SUMO fornisce invece:

```text
posizione dei veicoli
velocita' dei veicoli
ingresso dei veicoli
uscita dei veicoli
```

Il futuro bridge combinera':

```text
SUMO VehicleUpdates
+
stato radio SNS
+
raggio single-hop SNS
+
catalogo MA-GA
->
candidati VEHICLE diretti
```

## Ricezione SNS e candidato V2V MA-GA

La distinzione centrale della fase e':

```text
ricezione ad-hoc SNS
    !=
candidato V2V diretto MA-GA
```

SNS puo' simulare comunicazioni ad-hoc e, a seconda della configurazione, anche forwarding multi-hop. Una ricezione di un messaggio V2X non dimostra automaticamente che mittente e destinatario siano collegati direttamente.

Per MA-GA viene adottata la policy:

```text
DIRECT_SINGLEHOP_ONLY
```

Un peer V2V e' candidabile se e solo se:

```text
radio ad-hoc attiva
AND
distanza <= raggio single-hop
```

In forma piu' esplicita, un veicolo `target` e' candidabile come nodo `VEHICLE` per un veicolo `source` soltanto se:

```text
source != target
source e target sono presenti nella simulazione
source e target hanno radio ad-hoc attiva
distance(source, target) <= singlehopRadius
```

Questa regola usa SNS come sorgente dello stato radio, del raggio single-hop e del ritardo, ma usa SUMO per la geometria effettiva dei veicoli.

## Scenario diagnostico

E' stato creato lo scenario:

```text
scenarios/MaGaV2VStudy/
```

partendo da:

```text
scenarios/MaGaMosaicStudy/
```

In `scenario_config.json` e' stato impostato:

```json
"id": "MaGaV2VStudy"
```

Sono rimasti attivi:

```json
"application": true,
"cell": true,
"sns": true,
"sumo": true,
"output": true
```

In `sns/sns_config.json` e' stata applicata questa configurazione:

```json
{
  "maximumTtl": 1,
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

La scelta:

```text
maximumTtl = 1
```

e' una scelta diagnostica usata per impedire forwarding oltre il singolo hop durante il test.

## Primo tentativo non valido

Il primo tentativo non valido e' stato eseguito nella run:

```text
tmp/mosaic-25.2/logs/log-20260603-115245-MaGaV2VStudy
```

Nel primo `mapping_config.json`, il blocco:

```json
"types": [...]
```

era stato inserito al livello principale e mancava:

```json
"vehicles": [...]
```

`Mapping.log` riportava:

```text
No vehicle spawners defined in mapping config.
Only external vehicles will be simulated.
```

SUMO riportava:

```text
Inserted: 0
```

Il file `output.csv` non conteneva:

```text
VEHICLE_REGISTRATION
VEHICLE_UPDATES
ADHOC_CONFIGURATION
V2X_MESSAGE_TRANSMISSION
V2X_MESSAGE_RECEPTION
```

La simulazione terminava formalmente senza errori, ma non validava SNS. Senza veicoli non esistono radio ad-hoc, posizioni, coppie V2V o ricezioni da osservare.

## Correzione applicata

Il blocco dei veicoli e' stato ripristinato dentro:

```text
vehicles[0].types
```

La configurazione corretta dei veicoli e':

```json
"vehicles": [
  {
    "startingTime": "5.0 s",
    "targetFlow": 900,
    "maxNumberVehicles": 30,
    "pos": 1417,
    "route": "1",
    "lanes": [0, 1],
    "types": [
      {
        "applications": [
          "org.eclipse.mosaic.app.tutorial.WeatherWarningApp",
          "org.eclipse.mosaic.app.tutorial.SlowDownApp"
        ],
        "name": "Car",
        "group": "AdHoc",
        "weight": 1.0
      }
    ]
  }
]
```

Lo scenario corretto contiene:

```text
30 veicoli
tutti appartenenti al gruppo AdHoc
tutti dotati di WeatherWarningApp e SlowDownApp
targetFlow = 900
maximumTtl = 1
singlehopRadius = 709,4 m
```

`Mapping.log` conferma la creazione del `VehicleFlowGenerator` e dei veicoli `veh_0` ... `veh_29`.

Esempi:

```text
VehicleFlowGenerator[spawningMode=ConstantSpawningMode[timeSpacing=4000000000],lanes=[0, 1],types=[[name=Car,apps=[...]]],pos=1417,route=1]
Creating Vehicle: time=5000000000,name=veh_0,route=1,lane=0,pos=1417,type=Car
Creating Vehicle: time=121000000000,name=veh_29,route=1,lane=1,pos=1417,type=Car
```

## Run valida

La run valida e':

```text
tmp/mosaic-25.2/logs/log-20260603-115949-MaGaV2VStudy
```

`MOSAIC.log` conferma l'avvio dello scenario e la conclusione dopo 1000 secondi simulati:

```text
Start federation with id 'MaGaV2VStudy'
Simulation ended after 1000s of 1000s (100%)
```

SUMO conferma:

```text
Inserted: 30
```

## Evidenze quantitative

La run valida ha prodotto:

| Evidenza | Valore |
| -------- | -----: |
| veicoli inseriti da SUMO | 30 |
| configurazioni radio `SINGLE` | 30 |
| configurazioni radio `OFF` | 30 |
| `VEHICLE_UPDATES` | 12923 |
| trasmissioni V2X | 6211 |
| ricezioni V2X | 152254 |

La coppia `SINGLE`/`OFF` e' coerente con l'attivazione della radio quando il veicolo entra nella simulazione e la disattivazione quando il veicolo esce.

## Inizializzazione SNS e raggio radio

`Communication.log` mostra l'inizializzazione di SNS:

```text
SnsAmbassador - Initialized SNS
```

Mostra inoltre la configurazione radio dei veicoli in modalita' `SINGLE` con raggio `709.4`:

```text
Radio configured in mode SINGLE with communication radius 709.4 for node id=veh_0 @time=6.000,000,000 s
Radio configured in mode SINGLE with communication radius 709.4 for node id=veh_1 @time=10.000,000,000 s
Radio configured in mode SINGLE with communication radius 709.4 for node id=veh_29 @time=122.000,000,000 s
```

Questo e' il raggio radio da usare nella policy `DIRECT_SINGLEHOP_ONLY`.

Attenzione: nelle righe `V2X_MESSAGE_TRANSMISSION` compare anche il valore finale `200`. In questo esperimento quel valore appartiene al messaggio geocast dell'applicazione, non al raggio radio SNS. Il raggio radio osservato per la configurazione ad-hoc e' `709.4`.

## Registrazione, aggiornamenti e configurazione ad-hoc

Esempi da `output.csv`:

```text
VEHICLE_REGISTRATION;5000000000;veh_0;[org.eclipse.mosaic.app.tutorial.WeatherWarningApp, org.eclipse.mosaic.app.tutorial.SlowDownApp];ElectricVehicle;null;AdHoc;...
ADHOC_CONFIGURATION;6000000000;veh_0;SINGLE;/10.1.0.1;50.0;CCH;null;null;null;null;null
VEHICLE_UPDATES;7000000000;veh_0;34.82466244661715;186.33265938410221;52.6560022198442;13.569067123065468;...
```

Queste righe confermano che per ogni finestra futura sara' possibile combinare:

```text
identita' del veicolo
posizione aggiornata da VehicleUpdates
stato della radio ad-hoc
raggio single-hop SNS
```

## Trasmissioni e ricezioni V2X

Esempi di trasmissione:

```text
V2X_MESSAGE_TRANSMISSION;81000000000;Denm;0;veh_0;52.63254233024483;13.565230282090148;0.0;AD_HOC_GEOCAST;/255.255.255.255;CCH;200
V2X_MESSAGE_TRANSMISSION;82000000000;Denm;1;veh_0;52.63226574802239;13.565183396175907;0.0;AD_HOC_GEOCAST;/255.255.255.255;CCH;200
```

Esempi di ricezione:

```text
V2X_MESSAGE_RECEPTION;81000400000;Denm;0;veh_1;0.0;200
V2X_MESSAGE_RECEPTION;81000900000;Denm;0;veh_5;0.0;200
V2X_MESSAGE_RECEPTION;81001400000;Denm;0;veh_2;0.0;200
```

Queste righe dimostrano che la comunicazione V2X ad-hoc e' attiva. Non sono pero' sufficienti, da sole, a costruire candidati `VEHICLE`: il candidato MA-GA deve essere derivato dalla distanza diretta tra i veicoli e dallo stato della radio.

## Validazione geometrica delle ricezioni

La validazione quantitativa delle ricezioni V2X ha prodotto:

```text
ricezioni analizzate: 152254
ricezioni prive di trasmissione associata: 0
distanza massima mittente-destinatario: 708,99 m
raggio configurato: 709,40 m
ricezioni oltre il raggio: 0
```

Questa evidenza e' importante: nella run diagnostica, le ricezioni osservate risultano compatibili con il raggio single-hop configurato.

La configurazione:

```text
maximumTtl = 1
```

impedisce inoltre il forwarding oltre il singolo hop durante il test. La combinazione tra TTL diagnostico e validazione geometrica rende la run utilizzabile per progettare la regola `DIRECT_SINGLEHOP_ONLY`.

La conclusione resta volutamente prudente:

```text
la run valida SNS per candidati diretti single-hop
le ricezioni V2X non diventano automaticamente candidati MA-GA
la candidabilita' MA-GA resta una derivazione geometrica del bridge
```

## Ritardi osservati

I ritardi osservati sono esattamente:

```text
0,4 ms
0,9 ms
1,4 ms
1,9 ms
2,4 ms
```

Sono coerenti con la configurazione:

```json
"singlehopDelay": {
  "type": "SimpleRandomDelay",
  "steps": 5,
  "minDelay": "0.4 ms",
  "maxDelay": "2.4 ms"
}
```

Esempio diagnostico:

| Destinatario | Distanza approssimativa | Ritardo |
| ------------ | ----------------------: | ------: |
| `veh_1` | `143,75 m` | `0,4 ms` |
| `veh_2` | `286,41 m` | `1,4 ms` |
| `veh_3` | `428,04 m` | `1,4 ms` |
| `veh_4` | `566,07 m` | `1,9 ms` |
| `veh_5` | `707,88 m` | `0,9 ms` |

Per MA-GA, la scelta conservativa iniziale e' usare il massimo ritardo single-hop SNS:

```text
2,4 ms = 0.0024 s
```

## Regola MA-GA risultante

La regola risultante per costruire candidati V2V e':

```text
peer V2V candidabile
    <=>
radio ad-hoc attiva
AND
distanza <= raggio single-hop
```

La policy MA-GA documentata per il catalogo e':

```json
"vehicleProfiles": [
  {
    "profileId": "car_default",
    "mappingPrototype": "Car",
    "localCpuCyclesPerSecond": null,
    "cpuSource": "CONFIGURED_VALUE_TO_BE_CALIBRATED"
  }
],

"v2vPolicy": {
  "candidatePolicy": "DIRECT_SINGLEHOP_ONLY",
  "positionSource": "SUMO_VEHICLE_UPDATES",
  "radioStateSource": "ADHOC_CONFIGURATION",
  "radiusSource": "SNS_CONFIG_OR_ADHOC_CONFIGURATION",
  "poolPolicy": "ONE_SHARED_POOL_PER_UNORDERED_PAIR",
  "nominalBandwidthBitsPerSecond": null,
  "bandwidthSource": "CONFIGURED_VALUE_TO_BE_CALIBRATED",
  "propagationDelayPolicy": "SNS_SINGLEHOP_MAX_DELAY",
  "conservativePropagationDelaySeconds": 0.0024,
  "lossProbabilitySource": "SNS_CONFIG"
}
```

Il significato e':

```text
candidato V2V
    -> direzionale

pool V2V
    -> condiviso dalla coppia non ordinata

capacita' CPU peer
    -> configurata nel catalogo

banda V2V
    -> non determinata automaticamente da SNS
```

Esempio:

```text
candidate_vehicle_beta_for_vehicle_alpha
    sourceVehicleId = vehicle_alpha
    executionNodeId = vehicle_beta
    type = VEHICLE

candidate_vehicle_alpha_for_vehicle_beta
    sourceVehicleId = vehicle_beta
    executionNodeId = vehicle_alpha
    type = VEHICLE

pool_v2v_alpha_beta
    condiviso dalla coppia {vehicle_alpha, vehicle_beta}
```

Non vengono inventati valori numerici per CPU locale e banda V2V.

SNS fornisce raggio, ritardo, perdita e osservabilita' delle comunicazioni, ma non una capacita' residua allocabile per coppia. La capacita' nominale V2V dovra' essere configurata e poi calibrata.

## Procedura prevista per exporter e bridge

Il futuro exporter offline, e poi il bridge live, dovranno costruire i candidati `VEHICLE` seguendo questi passi:

```text
1. leggere i veicoli presenti dalla simulazione
2. mantenere solo veicoli con radio ad-hoc attiva
3. leggere la posizione piu' recente da SUMO VehicleUpdates
4. calcolare la distanza tra coppie di veicoli
5. applicare distance(source, target) <= singlehopRadius
6. creare candidati VEHICLE direzionali
7. creare un pool DIRECT_V2V per coppia non ordinata
8. usare il ritardo massimo SNS come ritardo di propagazione iniziale
```

Questa procedura non e' stata implementata nella fase corrente.

## Dati non risolti da SNS

Restano fuori dalla responsabilita' diretta di SNS:

```text
availableCpu del veicolo target
banda residua allocabile per coppia V2V
task attivi generati dal workload
candidateId source-aware
decisione di offloading
fitness MA-GA
```

Questi dati dovranno provenire dal catalogo MA-GA, dal workload generator e dal bridge.

## Roadmap aggiornata

La Fase 8 e' completata.

La prossima attivita' e' la Fase 9:

```text
generazione diagnostica del workload computazionale
```

La Fase 9 dovra' sostituire i task scritti manualmente negli snapshot JSON con uno stream riproducibile di task applicativi.

Non sono state introdotte modifiche al codice Java e non sono stati implementati exporter o bridge.
