# MA-GA Core

Questo repository contiene il core Java del **Mobility-Aware Genetic Algorithm
(MA-GA)** per il computation offloading nel continuum veicolo, edge e cloud.

L'obiettivo del progetto e' scegliere, per ogni task generato da un veicolo,
dove eseguirlo e con quali risorse:

- esecuzione locale sul veicolo sorgente;
- offloading verso un altro veicolo;
- offloading verso edge;
- offloading verso cloud.

Il codice lavora su snapshot JSON dello scenario e puo' eseguire sia ottimizzazioni
statiche indipendenti, sia una sequenza temporale con finestra adattiva.

## Cosa fa il progetto

MA-GA rappresenta una soluzione come un cromosoma. Ogni gene descrive la scelta
per un singolo task:

- candidato selezionato;
- quota di offloading `p`;
- CPU assegnata;
- banda assegnata.

La fitness valuta la soluzione combinando:

- tempo di completamento;
- latenza comunicativa complessiva;
- penalita' mobility-aware;
- uso di CPU e banda;
- violazioni residue di deadline.

La versione corrente include anche un repair piu' esplicito per deadline,
copertura e CPU aggregata. Se il repair non trova una scelta ammissibile per un
task, il codice puo' usare una modalita' best-effort degradata, segnalata nei
report.

## Funzionalita' principali

- GA snapshot-based con inizializzazione, selezione, crossover, mutazione,
  elitismo, repair e fitness dettagliata.
- Gestione temporale con `TemporalWindowManager`, riuso della popolazione e
  finestra adattiva.
- Replay JSON in due modalita':
  - `JSON_TIME`, coerente con il tempo logico del manager;
  - `JSON_SEQUENCE`, replay ordinale utile per diagnosi.
- Profilo runtime della finestra:
  - `OBSERVED_RUNTIME`, usa il runtime GA osservato dalla finestra precedente;
  - `CONFIGURED_RUNTIME`, usa una stima configurata e riproducibile.
- Diagnostiche dedicate per deadline, latenza, mobilita', sorgente temporale,
  risorse, prefilter e riuso della popolazione.

## Stato del modello

Il codice e' allineato alla formalizzazione nelle parti centrali:

- gene e cromosoma rappresentano le variabili decisionali;
- `OffloadingTimeModel` calcola i tempi locale/remoto/parziale;
- `FitnessEvaluator` calcola la funzione obiettivo;
- `DynamicityEvaluator` misura la dinamicita' tra finestre;
- `window.timing` calcola i bound temporali della finestra.

Restano alcune scelte di prototipo da tenere presenti:

- la banda e' ancora modellata per link/candidato, non come unico `Bmax`
  globale aggregato;
- esiste un repair aggregato CPU, ma non ancora un repair aggregato banda;
- la copertura cloud e' un placeholder stabile;
- la copertura V2V usa velocita' relativa scalare;
- l'integrazione MOSAIC/SUMO e' predisposta tramite interfacce, ma non contiene
  ancora un bridge concreto nel repository.

## Struttura rapida

```text
src/
  app/                 entry point eseguibili
  config/              pesi, soglie, parametri GA, mobilita' e finestra
  ga/                  algoritmo genetico, fitness, vincoli e operatori
  io/                  loader JSON e report diagnostici
  model/               snapshot, nodi, geni, tempi e mobilita'
  validation/          validazione degli snapshot e invarianti richiesti
  window/              sorgenti temporali, dinamicita', riuso e timing

data/
  snapshots/           dataset JSON inclusi
  docs/                documentazione tecnica estesa
```

## Esecuzione rapida

Il progetto e' un modulo Java per IntelliJ IDEA. Richiede un JDK compatibile
con Java 21 e la libreria Jackson Databind configurata nel progetto.

In IntelliJ e' sufficiente creare una Run Configuration con la main class
desiderata e inserire gli eventuali program arguments.

### Finestra adattiva

Esegue il ciclo temporale completo sullo scenario predefinito.

```text
Main class:
app.AdaptiveWindowMain

Program arguments:
```

Esempio con sorgente temporale, profilo runtime, cartella snapshot e numero di
finestre:

```text
Main class:
app.AdaptiveWindowMain

Program arguments:
JSON_TIME OBSERVED_RUNTIME data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8
```

Per un replay ordinale diagnostico:

```text
Main class:
app.AdaptiveWindowMain

Program arguments:
JSON_SEQUENCE CONFIGURED_RUNTIME data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8
```

### Batch statico GA

Esegue il GA su tutti gli snapshot di una cartella, trattandoli come scenari
indipendenti.

```text
Main class:
app.GaBatchMain

Program arguments:
```

Con cartella esplicita e dettagli:

```text
Main class:
app.GaBatchMain

Program arguments:
data/snapshots/ga/scenarios/static_baseline --details
```

## Dataset inclusi

Gli snapshot sono divisi in due gruppi principali:

- `data/snapshots/ga`: scenari statici per confrontare il GA;
- `data/snapshots/temporal`: sequenze temporali per la finestra adattiva.

La cartella temporale usata di default e':

```text
data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated
```

## Documentazione completa

Il README e' solo una preview del progetto. La spiegazione completa del codice,
classe per classe e metodo per metodo, si trova in:

```text
data/docs/documentazione_completa_codice_maga.md
```

Quella documentazione include anche il confronto con la formalizzazione e le
problematiche aperte associate alle classi coinvolte.

## Lettura consigliata

Per orientarsi nel codice senza perdersi:

1. partire da `model.snapshot`, `model.node` e `model.genetic`;
2. leggere `model.offloading` e `model.mobility`;
3. guardare `ga.constraints` e `ga.operators.RepairOperator`;
4. leggere `ga.fitness.FitnessEvaluator`;
5. seguire il ciclo in `ga.core.MaGaOptimizer`;
6. chiudere con `window.core.TemporalWindowManager` e i report in `io.reporting`.
