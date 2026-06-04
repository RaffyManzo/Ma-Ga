# MA-GA Core

Questo repository contiene il core Java del **Mobility-Aware Genetic Algorithm
(MA-GA)** per il computation offloading in scenari veicolo, edge e cloud.

L'obiettivo del progetto e' scegliere, per ogni task generato da un veicolo,
dove eseguirlo e con quali risorse:

- esecuzione locale sul veicolo sorgente;
- offloading verso un altro veicolo;
- offloading verso edge;
- offloading verso cloud tramite gateway radio attivo.

Il codice lavora su snapshot JSON dello scenario e supporta sia ottimizzazioni
statiche indipendenti, sia sequenze temporali con finestra adattiva, riuso della
popolazione e diagnostiche dettagliate.

## Stato attuale

La versione corrente e' riallineata al modello gateway-aware e alla banda
gerarchica.

Punti principali:

- il cloud operativo e' `STRICT_GATEWAY`: copertura, instabilita' link e rischio
  handover delle decisioni `CLOUD` derivano dall'access gateway attivo;
- il vecchio placeholder cloud stabile non e' usato nei report correnti
  (`legacyPlaceholderEnabled: false`);
- gli snapshot possono includere gateway, access link e pool di banda tramite
  `AccessGatewaySnapshot`, `AccessLinkSnapshot` e `BandwidthPoolSnapshot`;
- la banda e' controllata su due livelli:
  - limite source-aware del singolo candidato (`candidateId`);
  - pool condiviso (`poolId`) di tipo `GLOBAL`, `GATEWAY` o `DIRECT_V2V`;
- il repair aggregato della banda e' implementato e lavora insieme al repair
  del singolo gene e al repair aggregato CPU;
- `Dv(k)` misura il churn dei veicoli, mentre `Dl(k)` misura la variazione della
  qualita' dell'access link attivo;
- il prefilter dei candidati rimuove candidati non utilizzabili e mantiene nello
  snapshot filtrato gateway, access link e pool di banda.

## Cosa ottimizza MA-GA

Una soluzione e' rappresentata come un cromosoma. Ogni gene descrive la scelta
per un singolo task:

- candidato selezionato;
- quota di offloading `p`;
- CPU assegnata;
- banda assegnata.

La fitness minimizza una combinazione di:

- tempo di completamento;
- latenza comunicativa complessiva;
- penalita' mobility-aware;
- uso e pressione di CPU e banda;
- violazioni residue di deadline.

Se il repair non trova una scelta che rispetti la deadline tra le alternative
valutate, la decisione puo' essere marcata come `DEGRADED_BEST_EFFORT`. Questa
etichetta indica un esito di repair limitato e degradato, non una prova di
infeasibilita' globale del task.

## Funzionalita'

- GA snapshot-based con inizializzazione, selezione, crossover, mutazione,
  elitismo, repair e fitness dettagliata.
- Scaling adattivo dei parametri GA in base allo snapshot.
- Vincoli e repair per deadline, coverage, CPU aggregata, banda per-link e
  banda per-pool.
- Gestione temporale con `TemporalWindowManager`, finestra adattiva e riuso
  della popolazione.
- Replay JSON in due modalita':
  - `JSON_TIME`, indicizzato sul tempo logico richiesto dal manager;
  - `JSON_SEQUENCE`, replay ordinale utile per diagnosi riproducibili.
- Profilo runtime della finestra:
  - `OBSERVED_RUNTIME`, usa il runtime GA osservato nella finestra precedente;
  - `CONFIGURED_RUNTIME`, usa una stima configurata e riproducibile.
- Diagnostiche per deadline, best-effort degradato, gateway cloud, access link,
  banda gerarchica, mobilita', latenza, finestra adattiva, sorgente temporale,
  prefilter e riuso della popolazione.

## Struttura

```text
src/
  app/                 entry point eseguibili
  config/              pesi, soglie, parametri GA, mobilita' e finestra
  ga/                  algoritmo genetico, fitness, vincoli e operatori
  io/                  loader JSON e report diagnostici
  model/               snapshot, nodi, geni, offloading, banda e mobilita'
  validation/          validazione degli snapshot e invarianti
  window/              sorgenti temporali, dinamicita', riuso e timing

data/
  snapshots/           dataset JSON inclusi
  docs/                documentazione tecnica e guide di lettura
```

## Entry point

Il progetto e' un modulo Java per IntelliJ IDEA. Richiede Java 21 e Jackson
Databind configurato nel progetto. Non e' presente un wrapper Maven o Gradle:
il modo piu' diretto per eseguire i main e' una Run Configuration di IntelliJ.

### Finestra adattiva

Main class:

```text
app.AdaptiveWindowMain
```

Uso:

```text
AdaptiveWindowMain [sourceMode] [runtimeProfile] [folder] [maxSteps]
```

Argomenti:

- `sourceMode`: `JSON_TIME`, `JSON_SEQUENCE`; `MOSAIC` e' dichiarato ma richiede
  una implementazione concreta di `MosaicSnapshotBridge`;
- `runtimeProfile`: `OBSERVED_RUNTIME` o `CONFIGURED_RUNTIME`;
- `folder`: cartella di snapshot temporali;
- `maxSteps`: numero massimo di finestre da eseguire.

Esempio diagnostico completo sullo scenario piu' ricco:

```text
JSON_SEQUENCE CONFIGURED_RUNTIME data/snapshots/temporal/scenarios/comprehensive_dynamic_validation 27
```

Esempio operativo indicizzato sul tempo:

```text
JSON_TIME OBSERVED_RUNTIME data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8
```

Note per replay JSON offline:

- uno snapshot con `tasks=[]` e `candidateNodes=[]` e' una finestra vuota
  valida; l'optimizer restituisce `EMPTY_TASK_SET`, fitness `0.0` e non avvia
  il ciclo genetico;
- `JSON_TIME` parte dal primo timestamp realmente disponibile nella cartella
  caricata, non da un'origine temporale sintetica a `0.0 s`;
- `TimeIndexedSnapshotReplaySource` conserva la semantica no-look-ahead: una
  richiesta precedente al primo snapshot restituisce `Optional.empty()`;
- `JSON_SEQUENCE` continua a consumare ordinalmente anche una finestra vuota.
- i report diagnostici supportano veicoli senza access link attivo: in assenza
  di gateway la qualita' accesso e' `q_v(k)=0`, distanza e `phiLink` sono
  renderizzati come `-`, e perdita copertura, recupero copertura e handover
  sono distinti;
- il riepilogo cloud separa il conteggio dei link del primo snapshot dagli
  aggregati dell'intera run, evitando di trattare una finestra vuota iniziale
  come rappresentativa della copertura globale.

Validazione del replay MOSAIC generato:

- `JSON_SEQUENCE` valida l'esecuzione ordinale end-to-end: ogni file JSON viene
  consumato nella sequenza ordinata;
- `JSON_TIME` valida la causalita' temporale con lookup
  `latest <= requestedTime` fino all'orizzonte finale realmente disponibile;
- lo stop ordinario della validazione temporale e'
  `FULL_TIME_HORIZON_REACHED`;
- `SAFETY_MAX_STEPS_REACHED` e' solo un guardrail e non rappresenta una
  condizione di successo.

### Batch statico GA

Main class:

```text
app.GaBatchMain
```

Uso:

```text
GaBatchMain [snapshotFolder] [--details]
```

Esempio sugli scenari statici presenti:

```text
data/snapshots/ga/scenarios --details
```

Ogni snapshot viene trattato come scenario indipendente: la popolazione finale
di uno snapshot non viene riusata nel successivo.

## Dataset inclusi

Gli snapshot sono divisi in due famiglie:

- `data/snapshots/ga`: scenari statici per confrontare il GA;
- `data/snapshots/temporal`: sequenze temporali per la finestra adattiva.

Scenari temporali principali:

- `data/snapshots/temporal/scenarios/comprehensive_dynamic_validation`;
- `data/snapshots/temporal/scenarios/gateway_cloud_validation`;
- `data/snapshots/temporal/scenarios/static_baseline`;
- `data/snapshots/temporal/scenarios/urban_moderate`;
- `data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated`.

Lo scenario piu' utile per controllare il comportamento corrente di gateway,
access link, deadline, banda gerarchica, mobilita' e finestra adattiva e':

```text
data/snapshots/temporal/scenarios/comprehensive_dynamic_validation
```

## Come leggere i report

Il report di `AdaptiveWindowMain` e' composto da molte sezioni. La lettura
consigliata e':

1. `EXECUTIVE SUMMARY`;
2. `WORST WINDOWS`;
3. sezioni deadline e `DEGRADED_BEST_EFFORT`;
4. sezioni latenza comunicativa;
5. sezioni banda link/pool;
6. sezioni gateway cloud, access link e mobilita';
7. sezioni finestra adattiva, sorgente temporale, riuso popolazione e prefilter.

La guida completa alla lettura e' in:

```text
data/docs/guida_lettura_report_maga.md
```

Il riallineamento dei risultati correnti rispetto ai report storici e' in:

```text
data/docs/actual_results.md
```

## Interpretazione dello scenario completo

Nel run diagnostico completo su
`comprehensive_dynamic_validation`, il sistema mostra il comportamento atteso
per molte parti del modello:

- cloud gateway-aware attivo;
- placeholder cloud disabilitato;
- prefilter operativo;
- repair CPU efficace;
- repair banda gerarchico efficace;
- nessuna violazione finale di coverage nello scenario analizzato.

Il problema residuo principale emerso dal report completo e' sulle deadline in
finestre severe: alcune decisioni cloud parziali restano in
`DEGRADED_BEST_EFFORT` per latenza di upload troppo alta. In quei casi le
violazioni non sono causate da coverage insufficiente o da violazione formale
del pool di banda; il collo di bottiglia e' spesso il link cloud per-candidato.

## Limiti ancora aperti

- `MOSAIC`/`MOSAIC_LIVE` richiede ancora un bridge concreto.
- Il modello V2V usa distanza euclidea e velocita' relativa scalare
  `abs(v_source - v_target)`, senza vettori di traiettoria o heading.
- Il repair best-effort valuta un insieme limitato di alternative: non dimostra
  infeasibilita' globale.
- In scenari con finestre molto corte, il runtime osservato del GA puo' superare
  la finestra logica; l'integrazione live richiede una policy esplicita.
- `FULL_OFFLOADING` puo' non comparire in alcuni scenari: se e' atteso, vanno
  controllate inizializzazione, mutazione e repair di `offloadingRatio`.

## Documentazione tecnica

Documentazione estesa del codice:

```text
data/docs/documentazione_completa_codice_maga.md
```

Documentazione architetturale in LaTeX:

```text
data/docs/documentazione_architettura_maga.tex
```

Guida consigliata per orientarsi nel codice:

1. `model.snapshot`, `model.node`, `model.bandwidth` e `model.genetic`;
2. `model.offloading` e `model.mobility`;
3. `ga.constraints` e `ga.operators`;
4. `ga.fitness.FitnessEvaluator`;
5. `ga.core.MaGaOptimizer`;
6. `window.core.TemporalWindowManager`;
7. `io.reporting`.
