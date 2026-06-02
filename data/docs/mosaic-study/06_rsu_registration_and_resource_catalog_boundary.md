# 06 - Registrazione RSU e confine con il catalogo risorse

Data: 2026-06-02.

## Obiettivo della fase

La Fase 6 verifica che lo scenario MOSAIC possa contenere punti di accesso radio espliciti, distinti dalle regioni Cell e dai nodi computazionali MA-GA.

Lo scopo non e' ancora costruire il catalogo risorse definitivo. Lo scopo e' stabilire il confine tra:

```text
RSU fisiche osservate/configurate nello scenario
regioni Cell che descrivono condizioni di rete
nodi EDGE usati dal modello di offloading
server CLOUD raggiungibile tramite rete cellulare
```

## Scenario analizzato

Scenario:

```text
MaGaMosaicStudy
```

Run analizzata:

```text
tmp/mosaic-25.2/logs/log-20260602-173039-MaGaMosaicStudy
```

La simulazione termina correttamente dopo 1000 secondi simulati.

Rispetto alla fase precedente, lo scenario mantiene attivi:

```text
SUMO
SNS
Cell
Output
export di CellularHandoverUpdates
```

Inoltre sono state aggiunte due RSU nel Mapping Ambassador, usate come punti di accesso radio candidati per MA-GA.

## Modifiche applicate allo scenario

Nel Mapping Ambassador sono state aggiunte due RSU con profilo `MaGaGateway`.

Le coordinate configurate sono:

| RSU runtime | Profilo MOSAIC | Latitudine | Longitudine |
| ----------- | -------------- | ---------: | ----------: |
| `rsu_0`     | `MaGaGateway`  |     52.644 |      13.567 |
| `rsu_1`     | `MaGaGateway`  |     52.625 |      13.563 |

Le regioni Cell gia' definite restano attive:

```text
region_north_normal
region_central_degraded
globalNetwork
```

Questa scelta e' intenzionale: le regioni Cell servono a descrivere condizioni radio differenti, mentre le RSU rappresentano entita' fisiche da cui MA-GA potra' derivare gateway, link di accesso e pool di banda.

## Evidenze della run

Il file `output.csv` contiene le registrazioni delle due RSU:

```text
RSU_REGISTRATION;0;rsu_0;MaGaGateway;[];52.644;13.567
RSU_REGISTRATION;0;rsu_1;MaGaGateway;[];52.625;13.563
```

Sono presenti anche le seguenti interazioni esportate:

| Interazione             | Conteggio |
| ----------------------- | --------: |
| `CELLULAR_HANDOVER`     |        38 |
| `RSU_REGISTRATION`      |         2 |
| `SERVER_REGISTRATION`   |         1 |

`Mapping.log` conferma la creazione di due `RoadSideUnitSpawner` alle coordinate attese:

```text
GeoPoint{lat=52.644000,lon=13.567000,alt=0.00}
GeoPoint{lat=52.625000,lon=13.563000,alt=0.00}
```

Non risultano errori bloccanti. Restano warning SUMO relativi a episodi di emergency braking, che non impediscono la conclusione della simulazione.

## Identificativi runtime e alias MA-GA

MOSAIC registra le RSU con identificativi runtime generati:

```text
rsu_0
rsu_1
```

Per MA-GA e' pero' utile lavorare con nomi semanticamente leggibili, ad esempio:

```text
rsu_north
rsu_central
```

Questi nomi non devono essere dedotti implicitamente dal solo ordine di registrazione. Dovranno essere gestiti come alias configurati, associati agli identificativi runtime o a regole di matching robuste.

La responsabilita' di questa associazione appartiene al futuro catalogo risorse MA-GA, non al core genetico.

## Confine tra RSU, regioni Cell, EDGE e CLOUD

| Concetto | Ruolo | Origine | Uso in MA-GA |
| -------- | ----- | ------- | ------------ |
| RSU fisica | Punto di accesso radio esplicito | Mapping Ambassador | Base per `AccessGatewaySnapshot` |
| Regione Cell | Area geografica con capacita', latenza e perdita specifiche | `cell/regions.json` | Condizione di rete, non gateway fisico |
| Nodo EDGE | Risorsa computazionale vicina a una o piu' RSU | Catalogo MA-GA | `NodeCandidate` di tipo `EDGE` |
| Server CLOUD | Risorsa computazionale remota | Catalogo MA-GA e/o registrazione server MOSAIC | `NodeCandidate` di tipo `CLOUD` |

Questa distinzione e' fondamentale. Una regione Cell puo' influenzare banda, ritardo, perdita o handover regionale, ma non identifica automaticamente il gateway MA-GA attivo.

Il modello resta quindi:

```text
veicolo
    -> access link derivato dal bridge
    -> RSU/gateway fisico
    -> pool di banda
    -> nodo EDGE oppure CLOUD
```

## Catalogo risorse MA-GA

La prossima fase dovra' progettare un file di configurazione dedicato:

```text
ma_ga_resource_catalog.json
```

Il catalogo non viene generato in questa fase. Il suo ruolo sara' contenere metadati configurati che MOSAIC non puo' produrre automaticamente come dati osservati.

Il catalogo dovra' includere, almeno:

```text
alias leggibili delle RSU
associazione alias -> identificativo runtime o regola di matching
raggio di copertura dei gateway
pool di banda associati ai gateway
capacita' nominale o disponibile dei pool
nodi EDGE disponibili
capacita' CPU degli EDGE
associazione EDGE -> gateway
nodo CLOUD
capacita' CPU del CLOUD
ritardi di propagazione configurati
policy di raggiungibilita' del CLOUD tramite gateway attivo
```

Un esempio puramente concettuale e non definitivo e':

```text
gateway rsu_north
    runtimeId: rsu_0
    coverageRadiusMeters: configurato
    bandwidthPool: pool_rsu_north
    edgeNode: edge_north

gateway rsu_central
    runtimeId: rsu_1
    coverageRadiusMeters: configurato
    bandwidthPool: pool_rsu_central
    edgeNode: edge_central

cloud
    reachableVia: active_gateway
```

Questo schema serve solo a chiarire il confine concettuale. Il file JSON definitivo verra' progettato separatamente.

## Dati che non appartengono al catalogo

Il catalogo deve rimanere una sorgente di dati configurati e stabili. Non deve contenere dati dinamici della simulazione.

Non appartengono al catalogo:

```text
posizione corrente dei veicoli
velocita' corrente dei veicoli
regione Cell corrente di un veicolo
eventi di handover
traffico osservato
misure aggregate prodotte da Cell
link di accesso attivi in una finestra
task generati dal workload
candidati source-aware gia' assemblati per uno snapshot
```

Questi dati devono provenire dai log MOSAIC, dall'export offline o dal bridge live.

## Implicazioni per SystemSnapshot

La costruzione dello snapshot dovra' combinare dati osservati, configurati e derivati:

| Campo snapshot | Origine prevista |
| -------------- | ---------------- |
| `vehicles` | SUMO / output MOSAIC |
| `accessGateways` | RSU registrate + catalogo MA-GA |
| `accessLinks` | derivati dal bridge usando posizione veicolo e copertura gateway |
| `bandwidthPools` | catalogo + misure/policy Cell |
| `candidateNodes` | catalogo + veicoli osservati + link di accesso |
| `tasks` | workload applicativo |

Questa fase conferma che MOSAIC puo' registrare RSU esplicite, ma la semantica MA-GA dei gateway resta una responsabilita' del bridge e del catalogo.

## Roadmap aggiornata

La Fase 6 e' completata.

La prossima attivita' e':

```text
progettare ma_ga_resource_catalog.json
```

Il lavoro successivo dovra':

```text
definire lo schema del catalogo
stabilire come mappare rsu_0 e rsu_1 in alias leggibili
definire raggi di copertura dei gateway
definire pool di banda associati alle RSU
definire capacita' CPU di EDGE e CLOUD
definire associazioni EDGE -> gateway
stabilire quali campi restano configurati e quali saranno derivati dal bridge
```

Non sono state introdotte modifiche al codice Java e non e' stato generato il catalogo definitivo.
