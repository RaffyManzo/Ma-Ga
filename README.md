## Struttura del Codice

Il repository è organizzato in package con responsabilità distinte: modello del
problema, algoritmo genetico, gestione temporale, configurazione, I/O,
validazione e test manuali.

### Package `model`

Il package `model` contiene gli oggetti che rappresentano il problema di
offloading:

- `SystemSnapshot`: fotografia dello scenario in un istante simulato, con
  veicoli, task attivi e candidati di esecuzione.
- `VehicleSnapshot`: stato di un veicolo, inclusi posizione, velocità e CPU
  locale disponibile.
- `TaskInstance`: task computazionale generato da un veicolo, con input,
  output, cicli CPU e deadline.
- `NodeCandidate`: destinazione possibile per un task, locale o remota
  (`VEHICLE`, `EDGE`, `CLOUD`).
- `Gene`: decisione di offloading per un singolo task.
- `Chromosome`: strategia completa di offloading per tutti i task attivi.

`Gene` e `Chromosome` sono le classi più vicine alla formalizzazione del GA. Il
gene contiene candidato scelto, quota di offloading, CPU e banda allocate; il
cromosoma raccoglie un gene per ogni task.

### Package `ga`

Il package `ga` contiene il cuore dell'algoritmo genetico.

`MaGaOptimizer` coordina il ciclo evolutivo:

1. prepara la popolazione iniziale;
2. valuta i cromosomi;
3. applica elitismo, selezione, crossover e mutazione;
4. ripara cromosomi incoerenti;
5. produce la soluzione migliore e la popolazione finale.

`FitnessEvaluator` traduce la funzione obiettivo in codice, combinando tempo di
completamento, latenza di comunicazione, rischio di mobilità/copertura e uso
delle risorse.

Gli operatori genetici generano e modificano le soluzioni. Alcune policy
aggiuntive, come `OffloadingRatioPolicy` e `ResourceAllocationPolicy`, guidano
la generazione di quote e risorse per evitare combinazioni formalmente valide
ma poco plausibili rispetto a deadline, banda e CPU disponibili.

### Package `window`

Il package `window` gestisce l'esecuzione del MA-GA su finestre temporali
successive.

La classe centrale è `TemporalWindowManager`, che:

1. richiede uno snapshot alla sorgente dati;
2. valuta la dinamicità rispetto allo snapshot precedente;
3. decide quanto riutilizzare della popolazione precedente;
4. esegue il GA sullo snapshot corrente;
5. calcola la durata della finestra successiva.

Le sottosezioni principali sono:

- `dynamicity`: misura le variazioni di veicoli, task, risorse e link.
- `population`: decide e costruisce il riuso della popolazione precedente.
- `prefilter`: elimina candidati chiaramente non competitivi prima del GA.
- `source`: astrae la sorgente dati, oggi basata su replay JSON e predisposta
  per un bridge MOSAIC/SUMO.
- `timing`: calcola i limiti e la durata della finestra adattiva.

### Finestra Adattiva

La finestra adattiva evita una durata fissa per tutte le esecuzioni. La durata
viene aggiornata in base alla dinamicità dello scenario, ai tempi operativi del
sistema e ai vincoli di copertura.

### Package `config`

Il package `config` raccoglie i parametri del sistema:

- pesi della fitness;
- penalità;
- parametri del GA;
- parametri di mobilità;
- parametri della finestra temporale;
- regole di scaling dei parametri GA.

### Package `io`, `validation` e `test`

`io` contiene loader e printer diagnostici. I loader trasformano snapshot JSON
in oggetti di dominio; i printer producono report tecnici per analizzare GA,
finestra temporale, riuso popolazione, sorgente dati e prefilter.

`validation` contiene `SnapshotValidator`, che controlla struttura e coerenza
degli snapshot prima dell'esecuzione.

`test` contiene runner manuali e suite sintetiche usate per verificare il
prototipo senza dipendere da un framework di test esterno.

### Stato Attuale

Il core del GA è stabile nei casi principali, ma alcuni scenari con carichi
computazionali elevati richiedono ancora calibrazione di fitness, repair e
policy di allocazione.

Il prossimo passo architetturale è collegare MOSAIC/SUMO alla generazione di
`SystemSnapshot`. Il simulatore dovrebbe fornire veicoli, posizioni, velocità,
task, nodi disponibili, banda, latenza ed eventuali eventi critici; da questi
dati si costruisce lo snapshot da passare a `TemporalWindowManager`.
