## Spiegazione della struttura del codice

### Package `model`

La repo è organizzata in diversi package, ognuno con un ruolo abbastanza preciso.

Il package `model` contiene le classi che rappresentano il problema. 
Qui ci sono gli oggetti che corrispondono alla formalizzazione: 
- lo snapshot del sistema,
- i veicoli, i task,
- i nodi candidati,
- i geni 
- i cromosomi

In particolare, `SystemSnapshot` rappresenta lo stato osservato in una finestra temporale, quindi l’insieme di veicoli, task e nodi disponibili.
`TaskInstance` rappresenta il singolo task computazionale, con dati come veicolo sorgente, dimensione dell’input, output, cicli CPU e deadline.
`VehicleSnapshot` rappresenta invece lo stato del veicolo in quel momento, quindi posizione, velocità e risorse locali.
`NodeCandidate` rappresenta le possibili destinazioni di esecuzione, quindi locale, veicolo, edge o cloud.

Le classi `Gene` e `Chromosome` sono quelle più direttamente collegate alla formalizzazione dell’algoritmo genetico. 
Il gene rispecchia la tupla formalizzata come decisione per un task: quota di offloading, CPU allocata, banda allocata e nodo scelto. 
Il cromosoma è quindi l’insieme dei geni, cioè una strategia completa di offloading per tutti i task attivi nella finestra.


Il package `ga` contiene invece il cuore dell’algoritmo genetico. 
Qui sono presenti:
- il ciclo evolutivo,
- la fitness 
- gli operatori genetici.

`MaGaOptimizer` coordina:
- inizializzazione della popolazione,
- valutazione,
- selezione,
- crossover,
- mutazione,
- repair,
- elitismo 
- criterio di arresto.


`FitnessEvaluator` traduce la funzione obiettivo formalizzata, considerando tempo, latenza, mobilità e uso delle risorse. 
Gli operatori, invece, servono a generare e modificare le soluzioni.

### Problematiche emerse durante i test nelle varie fasi dell'implementazione

Durante i test sono emersi alcuni problemi che hanno richiesto l’introduzione di policy più guidate. 

All’inizio alcune decisioni generate dal GA erano formalmente possibili, ma poco sensate rispetto ai vincoli reali del problema. 
Per esempio, potevano comparire quote di offloading non coerenti con il tipo di nodo scelto, oppure allocazioni di CPU e banda troppo deboli rispetto alla deadline del task. 
Per questo sono state aggiunte policy come `OffloadingRatioPolicy` e `ResourceAllocationPolicy`. 

Un’altra correzione importante riguarda il repair. 
Nei report è emerso che rispettare i vincoli del singolo gene non bastava sempre, perché più task potevano finire sullo stesso nodo fisico e superare la CPU complessiva disponibile. 
Per questo ho implementato un repair aggregato sulla CPU, così da riportare le soluzioni dentro i limiti delle risorse. 
Implementa in modo pratico il vincolo sulla capacità computazionale dei nodi.


Ho poi introdotto una gestione più esplicita della mobilità e della copertura. 
Nella formalizzazione il tempo di esecuzione deve essere compatibile non solo con la deadline, ma anche con il tempo di copertura del nodo scelto. 
Per questo è stato aggiunto un `CoverageEstimator`, usato poi nella fitness, nel prefilter e nel repair. 

-----

### Package `window`


Il package `window` è quello che prepara la parte più vicina al simulatore. 
Gestisce l'esecuzione del GA in finestre temporali successive.
La classe centrale è `TemporalWindowManager`, che gestisce il ciclo:
1. riceve uno snapshot,
2. valuta quanto è cambiato rispetto al precedente,
3. decide se riutilizzare parte della popolazione,
4. esegue il GA 
5. prepara la finestra successiva. 

Dentro `window` ci sono anche componenti più specifici. 
La parte `dynamicity` valuta quanto cambiano veicoli, task, risorse e link tra due finestre. 
La parte `population` gestisce il riuso della popolazione precedente: 
- se lo scenario è stabile può avere senso riutilizzarla,
- se ci sono cambiamenti forti conviene ripartire più da zero

La parte `prefilter` serve invece a ridurre i candidati prima di passarli al GA. 
Questo è stato utile perché alcuni candidati erano già chiaramente non utilizzabili: per esempio perché non compatibili con il veicolo sorgente, perché fuori copertura o perché anche nel caso migliore non avrebbero rispettato la deadline. 

#### Finestra adattiva

La parte `timing` riguarda la finestra temporale adattiva. 
L’obiettivo è non usare una durata fissa per tutte le finestre, ma adattarla alla dinamicità dello scenario e ai tempi operativi del sistema. 

-----

### Package `config`

Il package `config` raccoglie i parametri dell’algoritmo: 
- pesi della fitness,
- penalità,
- parametri del GA,
- parametri di mobilità
- parametri della finestra temporale.

----

### Package `io`, `validation` e `test` 

I package `io`, `validation` e `test` servono invece per caricare snapshot, produrre report, validare la struttura degli input ed eseguire test manuali. 
- `io` permette di stamoare dei report che analizzano e forniscono i risultati dell'esecuzion del GA e della finestra temporale eseguiti su una serie di snapshot statici in JSON.
- `validation` contiene uno `SnapshotValidator` che controlla la struttura del file JSON (quando verrá introdotto il simulatore verrá sostituita)
- `test` conteneva classi che runnavano alcuni test precedenti per testare il GA su snapshot singoli.

-----

### Situazione attuale

- Il core del GA é abbastanza stabile, tolto alcuni scenari con carichi computazionali alti
- Il prossimo passo è costruire un collegamento tra `MOSAIC/sumo` e `SystemSnapshot`.


In pratica, MOSAIC/SUMO dovrebbe fornire:
- veicoli,
- posizioni,
- velocità,
- task,
- nodi disponibili,
- banda,
- latenza
- eventuali eventi critici

Da questi dati poi semplicemente si costruisce lo snapshot e si passa al `TemporalWindowManager` può eseguire il MA-GA.
