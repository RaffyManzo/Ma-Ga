# Documentazione completa del codice MA-GA

Questa documentazione descrive lo stato attuale del repository `maga-core` e sostituisce le parti superate dalla riscrittura recente. Il linguaggio e' volutamente semplice: prima spiega il progetto nel suo insieme, poi entra in ogni classe e in ogni metodo rilevato.

Inventario generato dal codice sorgente: **132 file/classi top-level**, **155 tipi totali includendo tipi interni**, **1302 metodi o costruttori rilevati**.

> Nota: le sezioni 'Problematiche aperte' segnalano discrepanze o decisioni operative emerse dal confronto con la formalizzazione e dalle issue aperte. Non tutte sono bug: alcune sono scelte di prototipo da dichiarare negli esperimenti.

## 1. Visione globale del progetto

Il progetto implementa un Mobility-Aware Genetic Algorithm per decidere il computation offloading in uno scenario veicolare edge-to-cloud. A ogni finestra temporale osserva uno snapshot, costruisce possibili decisioni di offloading, le corregge quando violano vincoli strutturali, le evolve con il GA e produce una strategia finale.

La domanda centrale e': per ogni task generato da un veicolo, conviene eseguirlo localmente, su un altro veicolo, su edge o su cloud? Se lo eseguo in remoto, quanta parte del task va offloadata e quante risorse CPU/banda devo assegnare?

La formalizzazione usa questi concetti:
- `S_k`: stato del sistema nella finestra temporale k.
- `T_k`: insieme dei task attivi.
- `V_k`: insieme dei veicoli osservati.
- `N_valid_i`: candidati utilizzabili per il task i.
- `g_i = (p_i, f_i, b_i, n_i)`: gene, cioe' decisione per un task.
- `C`: cromosoma, cioe' strategia completa per tutti i task.
- `J(C)`: fitness da minimizzare.
- `D(k)`: dinamicita' dello scenario tra due finestre.
- `DeltaT_min`, `DeltaT_max`: limiti della finestra temporale.

## 2. Cosa e' cambiato nella versione corrente

- Il vincolo di deadline e' stato rafforzato: ora ci sono `DeadlineConstraintEvaluator`, `DeadlineRepairCatalog`, `DeadlineEvaluation` e una penalita' hard in `FitnessEvaluator`.
- Il repair e' piu' strutturato: lavora su candidati validi, copertura, deadline, CPU aggregata e usa un percorso incrementale quando conosce i task mutati.
- La mobilita' non e' piu' solo tempo di copertura: ora `MobilityLinkMetrics` e `MobilityPenaltyBreakdown` rendono espliciti copertura, instabilita' link e handover risk.
- La latenza comunicativa e' allineata alla formalizzazione come somma `L(C) = sum_i L_i`, non media per task.
- `AdaptiveWindowMain` espone il profilo runtime e usa di default `OBSERVED_RUNTIME`; `CONFIGURED_RUNTIME` resta disponibile per replay astratti.
- `TimeIndexedSnapshotReplaySource` non guarda piu' nel futuro: sceglie l'ultimo snapshot disponibile al tempo richiesto.
- Sono stati aggiunti report specifici per latenza, mobilita' e best-effort deadline.

## 3. Come comunicano le classi

Il flusso principale e' questo:

```text
JSON/MOSAIC ->
SystemStateSource ->
CandidatePrefilter ->
TemporalWindowManager

TemporalWindowManager ->
DynamicityEvaluator ->
PopulationAdapter ->
MaGaOptimizer

MaGaOptimizer -> PopulationInitializer/Selection/Crossover/MutationResult/Repair/Fitness
RepairOperator ->
SnapshotRepairContext/DeadlineRepairCatalog/CpuAggregateRepairOperator
FitnessEvaluator ->
EvaluationBreakdown ->
Report/Diagnostics
```

Le classi `model` rappresentano dati e formule. Le classi `ga` cercano una buona soluzione su uno snapshot. Le classi `ga.constraints` aiutano il repair a trattare deadline e copertura. Le classi `window` decidono quando rieseguire il GA e come riusare la popolazione precedente. Le classi `io` leggono o stampano dati. Le classi `config` evitano di nascondere costanti dentro l'algoritmo.

## 4. Traduzione della formalizzazione in codice

| Formalizzazione | Classe o package | Spiegazione semplice |
|---|---|---|
| Stato `S_k` | `SystemSnapshot` | Fotografia del mondo: veicoli, task e candidati. |
| Task `i` | `TaskInstance` | Lavoro da eseguire, con input, output, cicli CPU e deadline. |
| Veicolo `v` | `VehicleSnapshot` | Posizione, velocita' e CPU locale di un veicolo. |
| Nodo candidato `n_i` | `NodeCandidate` | Possibile destinazione: local, vehicle, edge, cloud. |
| Gene `g_i` | `Gene` | Decisione per un singolo task. |
| Cromosoma `C` | `Chromosome` | Lista di geni, quindi strategia completa. |
| Tempo `T_i(C)` | `OffloadingTimeModel` | Calcola locale, remoto e partial offloading. |
| Deadline `T_i <= D_i` | `ga.constraints` + `FitnessEvaluator` | Il repair prova a renderla ammissibile; la fitness penalizza duramente violazioni residue. |
| Copertura e mobilita' | `CoverageEstimator`, `MobilityLinkMetrics` | Stimano copertura, distanza, velocita' relativa e instabilita'. |
| Penalita' `Pmob` | `MobilityPenaltyBreakdown` | Scompone coverage risk, link instability e handover risk. |
| Latenza `L(C)` | `OffloadingTimeModel`, `LatencyDiagnosticPrinter` | Usa la somma delle latenze comunicative. |
| Fitness `J(C)` | `FitnessEvaluator` | Combina tempo, latenza, mobilita', risorse e penalita' hard. |
| Dinamicita' `D(k)` | `DynamicityEvaluator` | Misura cambiamento tra snapshot. |
| Riuso popolazione | `PopulationAdapter` | Decide quanto della popolazione precedente tenere. |
| Finestra temporale | `window.timing` | Calcola limiti e prossima durata della finestra. |

## 5. Problematiche aperte principali

- **Banda globale aggregata**: il codice resta per-link/candidateId. Se la formalizzazione mantiene un unico `Bmax`, questa e' ancora una discrepanza.
- **Repair banda**: esiste repair aggregato CPU, ma non un repair aggregato banda/gateway.
- **Deadline**: molto migliorata rispetto alla versione precedente, ma il best-effort degradato non prova infeasibilita' globale.
- **Runtime GA**: ora il default operativo e' osservato, ma i risultati vanno sempre letti sapendo se si usa `OBSERVED_RUNTIME` o `CONFIGURED_RUNTIME`.
- **Mobilita' cloud/V2V**: cloud e' ancora placeholder stabile; V2V usa velocita' relativa scalare; edge non usa heading.
- **`DeltaT_max < DeltaT_min`**: il codice rialza il massimo al minimo e lo segnala; la formalizzazione puo' richiedere una policy di fallback esplicita.
- **MOSAIC/SUMO**: ci sono interfacce e sorgenti predisposte, ma manca un bridge concreto nel repository.

Collegamento sintetico con le issue aperte note: `#6 Formalizzazione` riguarda il riallineamento modello/codice, `#4 saturazione soft` riguarda risorse vicine al limite, `#3 MOSAIC/SUMO` riguarda l'integrazione operativa con il simulatore.

## 6. Package del progetto

### `app`

Contiene i punti di ingresso eseguibili. Qui il codice compone configurazioni, sorgenti dati, profili runtime, prefilter, GA e printer.

### `config`

Raccoglie parametri e policy di configurazione. Nella formalizzazione questi corrispondono a pesi, soglie, limiti e costanti sperimentali.

### `config.fitness`

Configura pesi della funzione obiettivo, penalita' e scale di normalizzazione.

### `config.ga`

Configura e scala i parametri evolutivi del Genetic Algorithm.

### `config.mobility`

Configura i parametri usati nella stima della copertura e dell'instabilita' dei link.

### `config.window`

Configura la finestra temporale adattiva, inclusa la scelta tra runtime GA osservato e runtime configurato.

### `ga.constraints`

Contiene valutatori e cataloghi usati dal repair per trattare deadline e sostenibilita' mobility-aware in modo esplicito.

### `ga.core`

Coordina il ciclo evolutivo e conserva il risultato finale.

### `ga.fitness`

Calcola la funzione di fitness e classifica il tipo di decisione.

### `ga.fitness.breakdown`

Conserva dettagli diagnostici della fitness, inclusi risorse, latenza e mobilita'.

### `ga.operators`

Contiene inizializzazione, selezione, crossover, mutazione, repair e policy di allocazione.

### `io.reporting`

Stampa report leggibili per capire cosa ha fatto il GA e dove nascono costi o violazioni.

### `io.reporting.diagnostics.deadline`

Analizza le deadline violate e ne spiega la causa probabile.

### `io.snapshot`

Legge file JSON e li converte nel modello interno.

### `io.snapshot.dto`

Rappresenta gli oggetti grezzi letti dal JSON prima della validazione.

### `model.genetic`

Rappresenta cromosomi e geni, cioe' la forma concreta delle soluzioni del GA.

### `model.mobility`

Stima metriche mobility-aware: copertura, distanza, velocita' relativa e instabilita' del link.

### `model.node`

Descrive i tipi e le istanze dei nodi candidati: locale, veicolo, edge, cloud.

### `model.offloading`

Implementa le formule temporali di esecuzione locale, remota e parziale.

### `model.snapshot`

Rappresenta la fotografia del sistema nella finestra corrente.

### `validation.snapshot`

Valida snapshot grezzi e invarianti necessari al repair.

### `window.core`

Orchestra il ciclo temporale completo.

### `window.dynamicity`

Misura quanto lo scenario cambia tra due finestre.

### `window.dynamicity.calculator`

Calcola singole componenti della dinamicita': veicoli, task, risorse, link.

### `window.dynamicity.compare`

Fornisce comparatori generici per mappe di metriche.

### `window.dynamicity.math`

Contiene funzioni matematiche comuni per la dinamicita'.

### `window.dynamicity.metrics`

Rappresenta metriche confrontabili tra snapshot.

### `window.event`

Modella gli eventi critici che possono anticipare una riottimizzazione.

### `window.population`

Decide e costruisce il riuso della popolazione tra finestre.

### `window.prefilter`

Riduce lo spazio dei candidati prima del GA.

### `window.source`

Astrazione della sorgente di stato: JSON time, JSON sequence e predisposizione MOSAIC.

### `window.state`

Conserva lo stato del ciclo temporale e i risultati di ogni finestra.

### `window.timing`

Calcola limiti e decisioni della finestra adattiva.

### `window.trigger`

Modella il motivo temporale della riesecuzione.

## 7. Schede complete di classi, enum, interfacce e record

## Package `app`

Contiene i punti di ingresso eseguibili. Qui il codice compone configurazioni, sorgenti dati, profili runtime, prefilter, GA e printer.

### `AdaptiveWindowMain`

- File: `src/app/AdaptiveWindowMain.java:55`
- Tipo: `class`
- Nome completo: `app.AdaptiveWindowMain`

**Cosa fa, in parole semplici**

Esegue il ciclo temporale completo del MA-GA su una cartella di snapshot consecutivi. Questo è l'unico entry point applicativo per la finestra adattiva. Compone sorgente dati, prefilter, gestore temporale, riuso della popolazione, bounds adattivi e report. La modalità sorgente predefinita è `JSON_TIME`: il manager richiede lo snapshot coerente con il proprio tempo logico. `JSON_SEQUENCE` resta disponibile come replay diagnostico ordinale. Il profilo temporale predefinito è `OBSERVED_RUNTIME`: dopo la prima finestra, il bound minimo usa il runtime del GA osservato nella finestra precedente. Il profilo `CONFIGURED_RUNTIME` resta disponibile per replay algoritmici astratti e riproducibili. Argomenti supportati: AdaptiveWindowMain AdaptiveWindowMain data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated AdaptiveWindowMain data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8 AdaptiveWindowMain JSON_TIME OBSERVED_RUNTIME data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8 AdaptiveWindowMain JSON_SEQUENCE CONFIGURED_RUNTIME data/snapshots/temporal/scenarios/urban_realistic_dynamic_calibrated 8 Compone il flusso completo e ora permette di scegliere anche il profilo runtime: `OBSERVED_RUNTIME` per esperimenti operativi, `CONFIGURED_RUNTIME` per replay astratti.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `AdaptiveWindowController`, `AdaptiveWindowReportPrinter`, `CandidatePrefilter`, `CandidatePrefilterConfig`, `CoverageReferenceCalculator`, `DynamicityEvaluator`, `FilteringSystemStateSource`, `GaParameterScalingMode`, `JsonSnapshotFolderLoader`, `MaGaConfig`, `MaGaOptimizer`, `PopulationAdapter`, `PopulationReuseDecisionPolicy`, `SequentialSnapshotReplaySource`, `SnapshotPaths`, `StaticCriticalEventDetector`, ... altri 8.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final String DEFAULT_SOURCE_MODE = "JSON_TIME"`
- `private static final double START_TIME_SECONDS = 0.0`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private AdaptiveWindowMain()` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static void main(String[] args) throws Exception` | public | Punto di ingresso dell'applicazione: legge argomenti, compone gli oggetti principali e avvia l'esecuzione. |
| `private static SystemStateSource buildSource( String mode, String folderPath, List<SystemSnapshot> snapshots ) throws Exception` | private | Costruisce `build source` usando le informazioni disponibili. |
| `private static boolean isSupportedSourceMode(String value)` | private | Risponde con true/false alla domanda `is supported source mode`. |

**Problematiche aperte**

- La modalita' MOSAIC non e' avviabile dal main senza una `MosaicSnapshotBridge` concreta.

### `RunArguments` (tipo interno di `AdaptiveWindowMain`)

- File: `src/app/AdaptiveWindowMain.java:175`
- Tipo: `record`
- Nome completo: `app.AdaptiveWindowMain.RunArguments`

**Cosa fa, in parole semplici**

Record: piccolo contenitore immutabile usato per passare dati insieme.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `AdaptiveWindowController`, `AdaptiveWindowReportPrinter`, `CandidatePrefilter`, `CandidatePrefilterConfig`, `CoverageReferenceCalculator`, `DynamicityEvaluator`, `FilteringSystemStateSource`, `GaParameterScalingMode`, `JsonSnapshotFolderLoader`, `MaGaConfig`, `MaGaOptimizer`, `PopulationAdapter`, `PopulationReuseDecisionPolicy`, `SequentialSnapshotReplaySource`, `SnapshotPaths`, `StaticCriticalEventDetector`, ... altri 8.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private static RunArguments parse(String[] args)` | private | Interpreta input testuale o grezzo e lo trasforma in un valore usabile dal codice. |
| `private static String usage()` | private | Metodo di supporto: realizza il passo `usage` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `GaBatchMain`

- File: `src/app/GaBatchMain.java:29`
- Tipo: `class`
- Nome completo: `app.GaBatchMain`

**Cosa fa, in parole semplici**

Esegue il MA-GA separatamente su tutti gli snapshot JSON contenuti in una cartella. Ogni snapshot viene trattato come uno scenario indipendente. La popolazione finale di una precedente esecuzione non viene riutilizzata: questo entry point serve a confrontare il comportamento del GA in situazioni statiche differenti. Argomenti supportati: GaBatchMain GaBatchMain data/snapshots/ga/scenarios/static_baseline GaBatchMain data/snapshots/ga/scenarios/static_baseline --details Esegue il GA su piu' snapshot senza ciclo temporale adattivo, utile per confronti batch.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `GaBatchReportPrinter`, `GaParameterScalingMode`, `JsonSnapshotFolderLoader`, `MaGaConfig`, `MaGaOptimizer`, `MaGaResult`, `SnapshotPaths`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final String DETAILS_FLAG = "--details"`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private GaBatchMain()` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static void main(String[] args) throws Exception` | public | Punto di ingresso dell'applicazione: legge argomenti, compone gli oggetti principali e avvia l'esecuzione. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `RunArguments` (tipo interno di `GaBatchMain`)

- File: `src/app/GaBatchMain.java:63`
- Tipo: `record`
- Nome completo: `app.GaBatchMain.RunArguments`

**Cosa fa, in parole semplici**

Record: piccolo contenitore immutabile usato per passare dati insieme.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `GaBatchReportPrinter`, `GaParameterScalingMode`, `JsonSnapshotFolderLoader`, `MaGaConfig`, `MaGaOptimizer`, `MaGaResult`, `SnapshotPaths`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private static RunArguments parse(String[] args)` | private | Interpreta input testuale o grezzo e lo trasforma in un valore usabile dal codice. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `config`

Raccoglie parametri e policy di configurazione. Nella formalizzazione questi corrispondono a pesi, soglie, limiti e costanti sperimentali.

### `MaGaConfig`

- File: `src/config/MaGaConfig.java:23`
- Tipo: `class`
- Nome completo: `config.MaGaConfig`

**Cosa fa, in parole semplici**

Configurazione complessiva del Mobility-Aware Genetic Algorithm. Aggrega pesi della fitness, penalità, normalizzazione, configurazione GA, parametri di mobilità e modalità di scaling. I main normalmente scelgono solo la modalità di scaling; la configurazione GA effettiva viene risolta quando è disponibile lo snapshot. Aggrega tutte le configurazioni usate dal GA: fitness, penalita', normalizzazione, mobilita' e scaling.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `FitnessWeights`, `GaParameterScaler`, `GaParameterScalingMode`, `GaParameterScalingResult`, `GeneticAlgorithmConfig`, `MobilityConfig`, `NormalizationConfig`, `PenaltyConfig`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final FitnessWeights fitnessWeights`
- `private final PenaltyConfig penaltyConfig`
- `private final NormalizationConfig normalizationConfig`
- `private final GeneticAlgorithmConfig geneticAlgorithmConfig`
- `private final MobilityConfig mobilityConfig`
- `private final GaParameterScalingMode gaParameterScalingMode`
- `private final GaParameterScaler gaParameterScaler`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public MaGaConfig( FitnessWeights fitnessWeights, PenaltyConfig penaltyConfig, NormalizationConfig normalizationConfig, GeneticAlgorithmConfig geneticAlgorithmConfig )` | public | Overload storico in modalità `GaParameterScalingMode#STATIC`. |
| `public MaGaConfig( FitnessWeights fitnessWeights, PenaltyConfig penaltyConfig, NormalizationConfig normalizationConfig, GeneticAlgorithmConfig geneticAlgorithmConfig, MobilityConfig mobilityConfig )` | public | Overload con configurazione di mobilità esplicita in modalità `GaParameterScalingMode#STATIC`. |
| `public MaGaConfig( FitnessWeights fitnessWeights, PenaltyConfig penaltyConfig, NormalizationConfig normalizationConfig, GeneticAlgorithmConfig geneticAlgorithmConfig, MobilityConfig mobilityConfig, GaParameterScalingMode gaParameterScalingMode )` | public | Costruttore principale. @param fitnessWeights pesi della funzione obiettivo @param penaltyConfig configurazione delle penalità @param normalizationConfig riferimenti di normalizzazione @param geneticAlgorithmConfig configurazione GA di base @param mobilityConfig configurazione di mobilità/copertura @param gaParameterScalingMode modalità STATIC o ADAPTIVE |
| `public static MaGaConfig defaultConfig()` | public | Configurazione default in modalità STATIC. |
| `public static MaGaConfig defaultConfig( GaParameterScalingMode scalingMode )` | public | Configurazione default scegliendo solo la modalità di scaling. Questo è il metodo consigliato nel main. @param scalingMode modalità STATIC o ADAPTIVE @return configurazione MA-GA completa |
| `public FitnessWeights getFitnessWeights()` | public | Restituisce il valore di `FitnessWeights` senza modificarlo. |
| `public PenaltyConfig getPenaltyConfig()` | public | Restituisce il valore di `PenaltyConfig` senza modificarlo. |
| `public NormalizationConfig getNormalizationConfig()` | public | Restituisce il valore di `NormalizationConfig` senza modificarlo. |
| `public GeneticAlgorithmConfig getGeneticAlgorithmConfig()` | public | Restituisce la configurazione GA di base. Attenzione: in modalità ADAPTIVE questa non è necessariamente la configurazione usata durante una specifica esecuzione. Per ottenere la configurazione effettiva usare resolveGeneticAlgorithmConfig(snapshot). |
| `public MobilityConfig getMobilityConfig()` | public | Restituisce il valore di `MobilityConfig` senza modificarlo. |
| `public GaParameterScalingMode getGaParameterScalingMode()` | public | Restituisce il valore di `GaParameterScalingMode` senza modificarlo. |
| `public GeneticAlgorithmConfig resolveGeneticAlgorithmConfig( SystemSnapshot snapshot )` | public | Risolve la configurazione GA effettiva per lo snapshot corrente. In modalità STATIC restituisce geneticAlgorithmConfig. In modalità ADAPTIVE restituisce una configurazione scalata. @param snapshot snapshot da ottimizzare @return configurazione GA effettiva |
| `public GaParameterScalingResult resolveGaParameterScaling( SystemSnapshot snapshot )` | public | Risolve la configurazione GA effettiva e restituisce anche informazioni diagnostiche. @param snapshot snapshot da ottimizzare @return risultato dello scaling |
| `private static GaParameterScaler createScaler( GaParameterScalingMode mode )` | private | Crea `create scaler` come nuovo oggetto o nuova struttura dati. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `config.fitness`

Configura pesi della funzione obiettivo, penalita' e scale di normalizzazione.

### `FitnessWeights`

- File: `src/config/fitness/FitnessWeights.java:18`
- Tipo: `class`
- Nome completo: `config.fitness.FitnessWeights`

**Cosa fa, in parole semplici**

Rappresenta i pesi della funzione di fitness del MA-GA. Formalizzazione: J(C) = wT * T(C) + wL * L(C) + wM * Pmob(C) + wR * Pres(C) Dove: - wT pesa il tempo complessivo di completamento T(C); - wL pesa la latenza comunicativa L(C); - wM pesa la penalità di mobilità Pmob(C); - wR pesa la penalità per violazione delle risorse Pres(C). Serve solo a configurare il peso relativo dei termini che saranno usati dal FitnessEvaluator.

**Relazione con la formalizzazione**

Realizza la funzione obiettivo `J(C)` con pesi, normalizzazione e penalita'.

**Con chi comunica**

Comunica con le classi operative tramite costruttori e getter di configurazione.
Serve a rendere espliciti parametri che nella formalizzazione sono pesi, soglie o costanti.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double SUM_TOLERANCE = 1.0E-9`
- `private final double completionTimeWeight`
- `private final double communicationLatencyWeight`
- `private final double mobilityPenaltyWeight`
- `private final double resourcePenaltyWeight`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public FitnessWeights( double completionTimeWeight, double communicationLatencyWeight, double mobilityPenaltyWeight, double resourcePenaltyWeight )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static FitnessWeights defaultWeights()` | public | Configurazione iniziale ragionevole per il primo prototipo. Non è una scelta sperimentalmente validata. Serve solo come punto di partenza per verificare il funzionamento del MA-GA su snapshot statici. |
| `public static FitnessWeights normalized( double completionTimeWeight, double communicationLatencyWeight, double mobilityPenaltyWeight, double resourcePenaltyWeight )` | public | Factory utile quando si vogliono fornire pesi grezzi e normalizzarli. Da usare consapevolmente: nel costruttore principale, invece, la somma deve già essere pari a 1.0. |
| `public double getCompletionTimeWeight()` | public | Restituisce il valore di `CompletionTimeWeight` senza modificarlo. |
| `public double getCommunicationLatencyWeight()` | public | Restituisce il valore di `CommunicationLatencyWeight` senza modificarlo. |
| `public double getMobilityPenaltyWeight()` | public | Restituisce il valore di `MobilityPenaltyWeight` senza modificarlo. |
| `public double getResourcePenaltyWeight()` | public | Restituisce il valore di `ResourcePenaltyWeight` senza modificarlo. |
| `public double getWT()` | public | Restituisce il valore di `WT` senza modificarlo. |
| `public double getWL()` | public | Restituisce il valore di `WL` senza modificarlo. |
| `public double getWM()` | public | Restituisce il valore di `WM` senza modificarlo. |
| `public double getWR()` | public | Restituisce il valore di `WR` senza modificarlo. |
| `private static void validateFinite(String fieldName, double value)` | private | Controlla la correttezza di `validate finite` e solleva un'eccezione se trova dati incoerenti. |
| `private static void validateNonNegative(String fieldName, double value)` | private | Controlla la correttezza di `validate non negative` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `NormalizationConfig`

- File: `src/config/fitness/NormalizationConfig.java:18`
- Tipo: `class`
- Nome completo: `config.fitness.NormalizationConfig`

**Cosa fa, in parole semplici**

Configura i valori di riferimento usati per normalizzare i termini della fitness. La formalizzazione evidenzia che T(C), L(C), Pmob(C) e Pres(C) possono avere scale numeriche diverse. Per evitare che un termine domini gli altri solo per ordine di grandezza, il FitnessEvaluator potrà usare: T_hat(C) = T(C) / T_ref L_hat(C) = L(C) / L_ref Pmob_hat(C) = Pmob(C) / Pmob_ref Pres_hat(C) = Pres(C) / Pres_ref Questa classe NON normalizza direttamente i valori. Espone solo i riferimenti numerici.

**Relazione con la formalizzazione**

Realizza la funzione obiettivo `J(C)` con pesi, normalizzazione e penalita'.

**Con chi comunica**

Comunica con le classi operative tramite costruttori e getter di configurazione.
Serve a rendere espliciti parametri che nella formalizzazione sono pesi, soglie o costanti.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double completionTimeReferenceSeconds`
- `private final double communicationLatencyReferenceSeconds`
- `private final double mobilityPenaltyReference`
- `private final double resourcePenaltyReference`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public NormalizationConfig( double completionTimeReferenceSeconds, double communicationLatencyReferenceSeconds, double mobilityPenaltyReference, double resourcePenaltyReference )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static NormalizationConfig neutral()` | public | Configurazione neutra. Con tutti i riferimenti pari a 1.0, il FitnessEvaluator può già funzionare senza alterare i valori originali. In seguito questi valori andranno tarati usando simulazioni o soglie operative realistiche. |
| `public double getCompletionTimeReferenceSeconds()` | public | Restituisce il valore di `CompletionTimeReferenceSeconds` senza modificarlo. |
| `public double getCommunicationLatencyReferenceSeconds()` | public | Restituisce il valore di `CommunicationLatencyReferenceSeconds` senza modificarlo. |
| `public double getMobilityPenaltyReference()` | public | Restituisce il valore di `MobilityPenaltyReference` senza modificarlo. |
| `public double getResourcePenaltyReference()` | public | Restituisce il valore di `ResourcePenaltyReference` senza modificarlo. |
| `public double getTRef()` | public | Restituisce il valore di `TRef` senza modificarlo. |
| `public double getLRef()` | public | Restituisce il valore di `LRef` senza modificarlo. |
| `public double getPmobRef()` | public | Restituisce il valore di `PmobRef` senza modificarlo. |
| `public double getPresRef()` | public | Restituisce il valore di `PresRef` senza modificarlo. |
| `private static void validateStrictlyPositive(String fieldName, double value)` | private | Controlla la correttezza di `validate strictly positive` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `PenaltyConfig`

- File: `src/config/fitness/PenaltyConfig.java:14`
- Tipo: `class`
- Nome completo: `config.fitness.PenaltyConfig`

**Cosa fa, in parole semplici**

Configura i coefficienti usati per costruire le penalità del MA-GA. La formalizzazione distingue: - Pmob(C): penalità complessiva legata alla mobilità; - Pres(C): penalità complessiva legata alla violazione delle risorse. Questa classe NON calcola Pmob(C) o Pres(C). Serve solo a contenere i coefficienti che saranno usati dal FitnessEvaluator.

**Relazione con la formalizzazione**

Realizza la funzione obiettivo `J(C)` con pesi, normalizzazione e penalita'.

**Con chi comunica**

Comunica con le classi operative tramite costruttori e getter di configurazione.
Serve a rendere espliciti parametri che nella formalizzazione sono pesi, soglie o costanti.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double coverageRiskWeight`
- `private final double linkInstabilityWeight`
- `private final double handoverRiskWeight`
- `private final double bandwidthOveruseWeight`
- `private final double cpuOveruseWeight`
- `private final double deadlineViolationWeight`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public PenaltyConfig( double coverageRiskWeight, double linkInstabilityWeight, double handoverRiskWeight, double bandwidthOveruseWeight, double cpuOveruseWeight, double deadlineViolationWeight )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static PenaltyConfig defaultConfig()` | public | Configurazione iniziale per il primo prototipo. Le penalità di risorse e deadline sono volutamente più alte perché rappresentano violazioni più gravi rispetto a una semplice scelta mobility-aware poco conveniente. |
| `public double getCoverageRiskWeight()` | public | Restituisce il valore di `CoverageRiskWeight` senza modificarlo. |
| `public double getLinkInstabilityWeight()` | public | Restituisce il valore di `LinkInstabilityWeight` senza modificarlo. |
| `public double getHandoverRiskWeight()` | public | Restituisce il valore di `HandoverRiskWeight` senza modificarlo. |
| `public double getBandwidthOveruseWeight()` | public | Restituisce il valore di `BandwidthOveruseWeight` senza modificarlo. |
| `public double getCpuOveruseWeight()` | public | Restituisce il valore di `CpuOveruseWeight` senza modificarlo. |
| `public double getDeadlineViolationWeight()` | public | Restituisce il valore di `DeadlineViolationWeight` senza modificarlo. |
| `private static void validateFiniteAndNonNegative(String fieldName, double value)` | private | Controlla la correttezza di `validate finite and non negative` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `config.ga`

Configura e scala i parametri evolutivi del Genetic Algorithm.

### `GaParameterScaler`

- File: `src/config/ga/GaParameterScaler.java:19`
- Tipo: `class`
- Nome completo: `config.ga.GaParameterScaler`

**Cosa fa, in parole semplici**

Scala i parametri evolutivi del MA-GA in base alla complessità dello snapshot osservato. La classe ha due modalità: - STATIC: restituisce la configurazione GA di base senza modificarla; - ADAPTIVE: aumenta popolazione e generazioni in base a task e candidati. Questa classe non appartiene al cromosoma e non modifica lo snapshot. Agisce prima dell'esecuzione del MaGaOptimizer.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final GaParameterScalingConfig scalingConfig`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public GaParameterScaler(GaParameterScalingConfig scalingConfig)` | public | Costruisce lo scaler. @param scalingConfig configurazione dello scaling |
| `public static GaParameterScaler staticScaler()` | public | Crea uno scaler in modalità statica. @return scaler statico |
| `public static GaParameterScaler adaptiveDefault()` | public | Crea uno scaler adattivo con configurazione iniziale. @return scaler adattivo |
| `public GeneticAlgorithmConfig scale( SystemSnapshot snapshot, GeneticAlgorithmConfig baseConfig )` | public | Scala la configurazione GA, restituendo solo la config finale. @param snapshot snapshot osservato @param baseConfig configurazione GA di partenza @return configurazione GA da usare |
| `public GaParameterScalingResult scaleDetailed( SystemSnapshot snapshot, GeneticAlgorithmConfig baseConfig )` | public | Scala la configurazione GA e restituisce anche informazioni diagnostiche. @param snapshot snapshot osservato @param baseConfig configurazione GA di partenza @return risultato diagnostico dello scaling |
| `private GeneticAlgorithmConfig buildAdaptiveConfig( int activeTaskCount, int candidateCount, GeneticAlgorithmConfig baseConfig )` | private | Costruisce la configurazione adattiva. |
| `private int computePopulationSize( int activeTaskCount, int candidateCount, GeneticAlgorithmConfig baseConfig )` | private | Calcola la dimensione della popolazione. |
| `private int computeMaxGenerations( int activeTaskCount, int candidateCount, GeneticAlgorithmConfig baseConfig )` | private | Calcola il numero massimo di generazioni. |
| `private int computeElitismCount( int populationSize, GeneticAlgorithmConfig baseConfig )` | private | Calcola quanti individui elitari conservare. |
| `private int computeStallGenerations( int maxGenerations, GeneticAlgorithmConfig baseConfig )` | private | Calcola la soglia di stagnazione in generazioni. |
| `private int clamp( int value, int min, int max )` | private | Limita un valore dentro un intervallo ammesso. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `GaParameterScalingConfig`

- File: `src/config/ga/GaParameterScalingConfig.java:10`
- Tipo: `class`
- Nome completo: `config.ga.GaParameterScalingConfig`

**Cosa fa, in parole semplici**

Configurazione della policy che scala i parametri del Genetic Algorithm. Questa classe non sostituisce GeneticAlgorithmConfig. Definisce solo come calcolare una nuova GeneticAlgorithmConfig quando la modalità adattiva è abilitata.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica con le classi operative tramite costruttori e getter di configurazione.
Serve a rendere espliciti parametri che nella formalizzazione sono pesi, soglie o costanti.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final GaParameterScalingMode mode`
- `private final int minPopulationSize`
- `private final int maxPopulationSize`
- `private final int populationPerTask`
- `private final double populationPerCandidate`
- `private final int minMaxGenerations`
- `private final int maxMaxGenerations`
- `private final int generationsPerTask`
- `private final double generationsPerCandidate`
- `private final int minElitismCount`
- `private final double elitismRate`
- `private final int minStallGenerations`
- `private final double stallGenerationRate`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public GaParameterScalingConfig( GaParameterScalingMode mode, int minPopulationSize, int maxPopulationSize, int populationPerTask, double populationPerCandidate, int minMaxGenerations, int maxMaxGenerations, int generationsPerTask, double generationsPerCandidate, int minElitismCount, double elitismRate, int minStallGenerations, double stallGenerationRate )` | public | Costruisce la configurazione dello scaler. |
| `public static GaParameterScalingConfig staticMode()` | public | Configurazione statica: lo scaler restituisce i parametri GA originali. |
| `public static GaParameterScalingConfig adaptiveDefault()` | public | Configurazione adattiva iniziale per il prototipo standalone. Per snapshot piccoli resta vicina ai valori di default. Per snapshot grandi aumenta popolazione e generazioni entro limiti. |
| `public GaParameterScalingMode getMode()` | public | Restituisce il valore di `Mode` senza modificarlo. |
| `public int getMinPopulationSize()` | public | Restituisce il valore di `MinPopulationSize` senza modificarlo. |
| `public int getMaxPopulationSize()` | public | Restituisce il valore di `MaxPopulationSize` senza modificarlo. |
| `public int getPopulationPerTask()` | public | Restituisce il valore di `PopulationPerTask` senza modificarlo. |
| `public double getPopulationPerCandidate()` | public | Restituisce il valore di `PopulationPerCandidate` senza modificarlo. |
| `public int getMinMaxGenerations()` | public | Restituisce il valore di `MinMaxGenerations` senza modificarlo. |
| `public int getMaxMaxGenerations()` | public | Restituisce il valore di `MaxMaxGenerations` senza modificarlo. |
| `public int getGenerationsPerTask()` | public | Restituisce il valore di `GenerationsPerTask` senza modificarlo. |
| `public double getGenerationsPerCandidate()` | public | Restituisce il valore di `GenerationsPerCandidate` senza modificarlo. |
| `public int getMinElitismCount()` | public | Restituisce il valore di `MinElitismCount` senza modificarlo. |
| `public double getElitismRate()` | public | Restituisce il valore di `ElitismRate` senza modificarlo. |
| `public int getMinStallGenerations()` | public | Restituisce il valore di `MinStallGenerations` senza modificarlo. |
| `public double getStallGenerationRate()` | public | Restituisce il valore di `StallGenerationRate` senza modificarlo. |
| `private static int validatePositive(String fieldName, int value)` | private | Controlla la correttezza di `validate positive` e solleva un'eccezione se trova dati incoerenti. |
| `private static int validateNonNegative(String fieldName, int value)` | private | Controlla la correttezza di `validate non negative` e solleva un'eccezione se trova dati incoerenti. |
| `private static double validateFiniteNonNegative( String fieldName, double value )` | private | Controlla la correttezza di `validate finite non negative` e solleva un'eccezione se trova dati incoerenti. |
| `private static double validateRate(String fieldName, double value)` | private | Controlla la correttezza di `validate rate` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `GaParameterScalingMode`

- File: `src/config/ga/GaParameterScalingMode.java:6`
- Tipo: `enum`
- Nome completo: `config.ga.GaParameterScalingMode`

**Cosa fa, in parole semplici**

Modalità di dimensionamento dei parametri evolutivi del MA-GA.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`STATIC`, `ADAPTIVE`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `GaParameterScalingResult`

- File: `src/config/ga/GaParameterScalingResult.java:8`
- Tipo: `class`
- Nome completo: `config.ga.GaParameterScalingResult`

**Cosa fa, in parole semplici**

Risultato diagnostico del dimensionamento dei parametri GA. Serve per capire perché una certa GeneticAlgorithmConfig è stata scelta.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica con le classi operative tramite costruttori e getter di configurazione.
Serve a rendere espliciti parametri che nella formalizzazione sono pesi, soglie o costanti.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final GaParameterScalingMode mode`
- `private final int vehicleCount`
- `private final int activeTaskCount`
- `private final int candidateCount`
- `private final double averageCandidatesPerTask`
- `private final GeneticAlgorithmConfig baseConfig`
- `private final GeneticAlgorithmConfig scaledConfig`
- `private final String reason`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public GaParameterScalingResult( GaParameterScalingMode mode, int vehicleCount, int activeTaskCount, int candidateCount, double averageCandidatesPerTask, GeneticAlgorithmConfig baseConfig, GeneticAlgorithmConfig scaledConfig, String reason )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public GaParameterScalingMode getMode()` | public | Restituisce il valore di `Mode` senza modificarlo. |
| `public int getVehicleCount()` | public | Restituisce il valore di `VehicleCount` senza modificarlo. |
| `public int getActiveTaskCount()` | public | Restituisce il valore di `ActiveTaskCount` senza modificarlo. |
| `public int getCandidateCount()` | public | Restituisce il valore di `CandidateCount` senza modificarlo. |
| `public double getAverageCandidatesPerTask()` | public | Restituisce il valore di `AverageCandidatesPerTask` senza modificarlo. |
| `public GeneticAlgorithmConfig getBaseConfig()` | public | Restituisce il valore di `BaseConfig` senza modificarlo. |
| `public GeneticAlgorithmConfig getScaledConfig()` | public | Restituisce il valore di `ScaledConfig` senza modificarlo. |
| `public String getReason()` | public | Restituisce il valore di `Reason` senza modificarlo. |
| `public boolean isAdaptive()` | public | Risponde con true/false alla domanda `is adaptive`. |
| `public boolean hasChangedPopulationSize()` | public | Risponde con true/false alla domanda `has changed population size`. |
| `public boolean hasChangedMaxGenerations()` | public | Risponde con true/false alla domanda `has changed max generations`. |
| `public boolean hasChanged()` | public | Risponde con true/false alla domanda `has changed`. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `GeneticAlgorithmConfig`

- File: `src/config/ga/GeneticAlgorithmConfig.java:19`
- Tipo: `class`
- Nome completo: `config.ga.GeneticAlgorithmConfig`

**Cosa fa, in parole semplici**

Configura i parametri evolutivi del Genetic Algorithm. Questa classe contiene solo parametri dell'algoritmo: - dimensione della popolazione; - numero massimo di generazioni; - probabilità di crossover; - probabilità di mutazione; - numero di individui elitari; - criterio di arresto per stagnazione; - soglia minima di miglioramento; - seed casuale.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica con le classi operative tramite costruttori e getter di configurazione.
Serve a rendere espliciti parametri che nella formalizzazione sono pesi, soglie o costanti.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final int populationSize`
- `private final int maxGenerations`
- `private final double crossoverRate`
- `private final double mutationRate`
- `private final int elitismCount`
- `private final int stallGenerations`
- `private final double fitnessImprovementEpsilon`
- `private final long randomSeed`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public GeneticAlgorithmConfig( int populationSize, int maxGenerations, double crossoverRate, double mutationRate, int elitismCount, int stallGenerations, double fitnessImprovementEpsilon, long randomSeed )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static GeneticAlgorithmConfig defaultConfig()` | public | Configurazione iniziale per il prototipo standalone. È pensata per testare il flusso completo del MA-GA su snapshot piccoli. Non va considerata una configurazione finale. |
| `public int getPopulationSize()` | public | Restituisce il valore di `PopulationSize` senza modificarlo. |
| `public int getMaxGenerations()` | public | Restituisce il valore di `MaxGenerations` senza modificarlo. |
| `public double getCrossoverRate()` | public | Restituisce il valore di `CrossoverRate` senza modificarlo. |
| `public double getMutationRate()` | public | Restituisce il valore di `MutationRate` senza modificarlo. |
| `public int getElitismCount()` | public | Restituisce il valore di `ElitismCount` senza modificarlo. |
| `public int getStallGenerations()` | public | Restituisce il valore di `StallGenerations` senza modificarlo. |
| `public double getFitnessImprovementEpsilon()` | public | Restituisce il valore di `FitnessImprovementEpsilon` senza modificarlo. |
| `public long getRandomSeed()` | public | Restituisce il valore di `RandomSeed` senza modificarlo. |
| `private static void validateRate(String fieldName, double value)` | private | Controlla la correttezza di `validate rate` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `config.mobility`

Configura i parametri usati nella stima della copertura e dell'instabilita' dei link.

### `MobilityConfig`

- File: `src/config/mobility/MobilityConfig.java:9`
- Tipo: `class`
- Nome completo: `config.mobility.MobilityConfig`

**Cosa fa, in parole semplici**

Configurazione dei parametri usati per stimare il tempo di copertura. La classe non calcola direttamente la copertura. Fornisce solo i valori necessari a CoverageEstimator.

**Relazione con la formalizzazione**

Implementa la parte mobility-aware: tempo di copertura, instabilita' link, rischio handover e limiti temporali collegati alla copertura.

**Con chi comunica**

Comunica con le classi operative tramite costruttori e getter di configurazione.
Serve a rendere espliciti parametri che nella formalizzazione sono pesi, soglie o costanti.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double epsilonSpeedMetersPerSecond`
- `private final double v2vCommunicationRadiusMeters`
- `private final double localCoverageTimeSeconds`
- `private final double cloudCoverageTimeSeconds`
- `private final double maxCoverageTimeSeconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public MobilityConfig( double epsilonSpeedMetersPerSecond, double v2vCommunicationRadiusMeters, double localCoverageTimeSeconds, double cloudCoverageTimeSeconds, double maxCoverageTimeSeconds )` | public | Costruisce la configurazione di mobilità. @param epsilonSpeedMetersPerSecond velocità minima usata per evitare divisioni per zero @param v2vCommunicationRadiusMeters raggio massimo del collegamento V2V @param localCoverageTimeSeconds tempo convenzionale per esecuzione locale @param cloudCoverageTimeSeconds tempo convenzionale per esecuzione cloud @param maxCoverageTimeSeconds limite massimo per i tempi di copertura stimati |
| `public static MobilityConfig defaultConfig()` | public | Configurazione iniziale per il prototipo statico. I valori sono volutamente conservativi e possono essere raffinati quando verranno introdotti dati da MOSAIC/SUMO. |
| `public double getEpsilonSpeedMetersPerSecond()` | public | Restituisce il valore di `EpsilonSpeedMetersPerSecond` senza modificarlo. |
| `public double getV2vCommunicationRadiusMeters()` | public | Restituisce il valore di `V2vCommunicationRadiusMeters` senza modificarlo. |
| `public double getLocalCoverageTimeSeconds()` | public | Restituisce il valore di `LocalCoverageTimeSeconds` senza modificarlo. |
| `public double getCloudCoverageTimeSeconds()` | public | Restituisce il valore di `CloudCoverageTimeSeconds` senza modificarlo. |
| `public double getMaxCoverageTimeSeconds()` | public | Restituisce il valore di `MaxCoverageTimeSeconds` senza modificarlo. |
| `public double clampCoverageTime(double coverageTimeSeconds)` | public | Limita un tempo di copertura al range usato dal modello. @param coverageTimeSeconds tempo di copertura stimato @return tempo limitato tra 0 e maxCoverageTimeSeconds |
| `private static double validatePositive(String fieldName, double value)` | private | Controlla la correttezza di `validate positive` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

- I 300 secondi per local/cloud sono convenzioni del prototipo, non misure derivate dal simulatore.

## Package `config.window`

Configura la finestra temporale adattiva, inclusa la scelta tra runtime GA osservato e runtime configurato.

### `TemporalMaximumBoundMode`

- File: `src/config/window/TemporalMaximumBoundMode.java:15`
- Tipo: `enum`
- Nome completo: `config.window.TemporalMaximumBoundMode`

**Cosa fa, in parole semplici**

Modalità di calcolo di DeltaT_max(k). La modalità adattiva segue la formalizzazione: DeltaT_max(k) = alpha_T * T_coverage_ref(k) La modalità configurata serve solo per test controllati. Non sostituisce la modalità formalizzata.

**Relazione con la formalizzazione**

Realizza i limiti `DeltaT_min`, `DeltaT_max` e la scelta della finestra temporale.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`CONFIGURED_MAX`, `COVERAGE_ADAPTIVE`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TemporalMinimumBoundMode`

- File: `src/config/window/TemporalMinimumBoundMode.java:14`
- Tipo: `enum`
- Nome completo: `config.window.TemporalMinimumBoundMode`

**Cosa fa, in parole semplici**

Modalità di calcolo di DeltaT_min(k). La formula resta quella della formalizzazione: DeltaT_min(k) = T_s(k) + T_GA_est(k) + T_apply(k) + epsilon_T Cambia solo il modo in cui viene stimato T_GA_est(k).

**Relazione con la formalizzazione**

Realizza i limiti `DeltaT_min`, `DeltaT_max` e la scelta della finestra temporale.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`CONFIGURED_GA_ESTIMATE`, `OBSERVED_GA_RUNTIME`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TemporalRuntimeProfile`

- File: `src/config/window/TemporalRuntimeProfile.java:14`
- Tipo: `enum`
- Nome completo: `config.window.TemporalRuntimeProfile`

**Cosa fa, in parole semplici**

Profilo temporale usato per costruire i bounds della finestra adattiva. `#OBSERVED_RUNTIME` segue il profilo operativo della formalizzazione: dopo la prima finestra usa il runtime del GA osservato nella finestra precedente. `#CONFIGURED_RUNTIME` mantiene invece una stima configurata e resta disponibile per replay algoritmici astratti e riproducibili. Rende esplicita la scelta modellistica sul tempo del GA, punto centrale nel confronto tra simulazione astratta e simulazione operativa.

**Relazione con la formalizzazione**

Realizza i limiti `DeltaT_min`, `DeltaT_max` e la scelta della finestra temporale.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public abstract TemporalWindowConfig createWindowConfig()` | public | Crea `create window config` come nuovo oggetto o nuova struttura dati. |
| `public static TemporalRuntimeProfile parse(String value)` | public | Interpreta input testuale o grezzo e lo trasforma in un valore usabile dal codice. |
| `public static boolean isSupported(String value)` | public | Risponde con true/false alla domanda `is supported`. |

**Problematiche aperte**

- La scelta tra `OBSERVED_RUNTIME` e `CONFIGURED_RUNTIME` cambia il significato sperimentale dei tempi; non mescolare risultati ottenuti con profili diversi.

### `TemporalWindowConfig`

- File: `src/config/window/TemporalWindowConfig.java:14`
- Tipo: `class`
- Nome completo: `config.window.TemporalWindowConfig`

**Cosa fa, in parole semplici**

Configurazione del gestore temporale del MA-GA. Contiene soglie di dinamicità, pesi delle componenti, parametri di crescita/riduzione della finestra e limiti operativi usati dal controller adattivo. `fixedIntervalSeconds` identifica la durata iniziale della finestra. Dopo la prima esecuzione, la durata può essere aggiornata dal controller adattivo. Parametri della finestra temporale: soglie, lambda, eta, runtime stimato, limiti.

**Relazione con la formalizzazione**

Realizza i limiti `DeltaT_min`, `DeltaT_max` e la scelta della finestra temporale.

**Con chi comunica**

Comunica con le classi operative tramite costruttori e getter di configurazione.
Serve a rendere espliciti parametri che nella formalizzazione sono pesi, soglie o costanti.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double DEFAULT_STRATEGY_APPLICATION_SECONDS = 0.05`
- `private static final double DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS = 0.10`
- `private static final double DEFAULT_CONFIGURED_MAX_WINDOW_SECONDS = 8.0`
- `private final double fixedIntervalSeconds`
- `private final double dataCollectionDelaySeconds`
- `private final double thetaLow`
- `private final double thetaHigh`
- `private final double rhoKeep`
- `private final double lambdaVehicles`
- `private final double lambdaTasks`
- `private final double lambdaResources`
- `private final double lambdaLinks`
- `private final double alphaT`
- `private final double etaUp`
- `private final double etaDown`
- `private final double epsilonT`
- `private final double strategyApplicationSeconds`
- `private final double defaultGaRuntimeEstimateSeconds`
- `private final double configuredMaxWindowSeconds`
- `private final TemporalMinimumBoundMode minimumBoundMode`
- `private final TemporalMaximumBoundMode maximumBoundMode`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TemporalWindowConfig( double fixedIntervalSeconds, double thetaLow, double thetaHigh, double rhoKeep, double lambdaVehicles, double lambdaTasks, double lambdaResources, double lambdaLinks, double alphaT, double etaUp, double etaDown, double epsilonT )` | public | Overload storico senza ritardo di raccolta dati. |
| `public TemporalWindowConfig( double fixedIntervalSeconds, double dataCollectionDelaySeconds, double thetaLow, double thetaHigh, double rhoKeep, double lambdaVehicles, double lambdaTasks, double lambdaResources, double lambdaLinks, double alphaT, double etaUp, double etaDown, double epsilonT )` | public | Overload storico con ritardo di raccolta dati esplicito. |
| `public static TemporalWindowConfig defaultConfig()` | public | Configurazione iniziale per i test con snapshot JSON. Il minimo usa una stima configurata del tempo GA. Questo evita che il wall-clock della JVM domini DeltaT_min durante i test locali. Il massimo resta aderente alla formalizzazione: DeltaT_max(k) = alphaT * T_coverage_ref(k) |
| `public static TemporalWindowConfig observedRuntimeBoundsConfig()` | public | Configurazione utile se si vuole studiare il comportamento operativo usando il tempo reale osservato del GA. |
| `public static TemporalWindowConfig configuredBoundsForReplay( double initialWindowSeconds, double configuredGaRuntimeEstimateSeconds, double configuredMaxWindowSeconds )` | public | Configurazione controllata. Utile solo per test sintetici. |
| `public static TemporalWindowConfig fixedInterval(double fixedIntervalSeconds)` | public | Metodo di supporto: realizza il passo `fixed interval` dentro la responsabilita' della classe. |
| `public static TemporalWindowConfig fixedIntervalWithCollectionDelay( double fixedIntervalSeconds, double dataCollectionDelaySeconds )` | public | Metodo di supporto: realizza il passo `fixed interval with collection delay` dentro la responsabilita' della classe. |
| `public double getFixedIntervalSeconds()` | public | Restituisce il valore di `FixedIntervalSeconds` senza modificarlo. |
| `public double getInitialWindowSeconds()` | public | Restituisce il valore di `InitialWindowSeconds` senza modificarlo. |
| `public double getDataCollectionDelaySeconds()` | public | Restituisce il valore di `DataCollectionDelaySeconds` senza modificarlo. |
| `public double getThetaLow()` | public | Restituisce il valore di `ThetaLow` senza modificarlo. |
| `public double getThetaHigh()` | public | Restituisce il valore di `ThetaHigh` senza modificarlo. |
| `public double getRhoKeep()` | public | Restituisce il valore di `RhoKeep` senza modificarlo. |
| `public double getLambdaVehicles()` | public | Restituisce il valore di `LambdaVehicles` senza modificarlo. |
| `public double getLambdaTasks()` | public | Restituisce il valore di `LambdaTasks` senza modificarlo. |
| `public double getLambdaResources()` | public | Restituisce il valore di `LambdaResources` senza modificarlo. |
| `public double getLambdaLinks()` | public | Restituisce il valore di `LambdaLinks` senza modificarlo. |
| `public double getAlphaT()` | public | Restituisce il valore di `AlphaT` senza modificarlo. |
| `public double getEtaUp()` | public | Restituisce il valore di `EtaUp` senza modificarlo. |
| `public double getEtaDown()` | public | Restituisce il valore di `EtaDown` senza modificarlo. |
| `public double getEpsilonT()` | public | Restituisce il valore di `EpsilonT` senza modificarlo. |
| `public double getStrategyApplicationSeconds()` | public | Restituisce il valore di `StrategyApplicationSeconds` senza modificarlo. |
| `public double getDefaultGaRuntimeEstimateSeconds()` | public | Restituisce il valore di `DefaultGaRuntimeEstimateSeconds` senza modificarlo. |
| `public double getConfiguredMaxWindowSeconds()` | public | Restituisce il valore di `ConfiguredMaxWindowSeconds` senza modificarlo. |
| `public TemporalMinimumBoundMode getMinimumBoundMode()` | public | Restituisce il valore di `MinimumBoundMode` senza modificarlo. |
| `public TemporalMaximumBoundMode getMaximumBoundMode()` | public | Restituisce il valore di `MaximumBoundMode` senza modificarlo. |
| `public double getLambdaSum()` | public | Restituisce il valore di `LambdaSum` senza modificarlo. |
| `public double getNormalizedLambdaVehicles()` | public | Restituisce il valore di `NormalizedLambdaVehicles` senza modificarlo. |
| `public double getNormalizedLambdaTasks()` | public | Restituisce il valore di `NormalizedLambdaTasks` senza modificarlo. |
| `public double getNormalizedLambdaResources()` | public | Restituisce il valore di `NormalizedLambdaResources` senza modificarlo. |
| `public double getNormalizedLambdaLinks()` | public | Restituisce il valore di `NormalizedLambdaLinks` senza modificarlo. |
| `private static void validatePositive(String fieldName, double value)` | private | Controlla la correttezza di `validate positive` e solleva un'eccezione se trova dati incoerenti. |
| `private static void validateFiniteAndNonNegative(String fieldName, double value)` | private | Controlla la correttezza di `validate finite and non negative` e solleva un'eccezione se trova dati incoerenti. |
| `private static void validateRate(String fieldName, double value)` | private | Controlla la correttezza di `validate rate` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

- `defaultConfig()` resta il profilo configurato/astratto; il main usa di default `OBSERVED_RUNTIME`. Nei report va dichiarato sempre quale profilo e' stato usato.

## Package `ga.constraints`

Contiene valutatori e cataloghi usati dal repair per trattare deadline e sostenibilita' mobility-aware in modo esplicito.

### `DeadlineConstraintEvaluator`

- File: `src/ga/constraints/DeadlineConstraintEvaluator.java:23`
- Tipo: `class`
- Nome completo: `ga.constraints.DeadlineConstraintEvaluator`

**Cosa fa, in parole semplici**

Valuta il vincolo di deadline del singolo task usando lo stesso modello temporale impiegato dalla fitness. Questa classe non sceglie autonomamente il nodo migliore e non sostituisce il GA. Espone soltanto una valutazione coerente e riutilizzabile dal repair. Valuta in modo riutilizzabile se un gene rispetta deadline e sostenibilita' mobility-aware.

**Relazione con la formalizzazione**

Rafforza il trattamento dei vincoli di deadline e copertura durante il repair, senza aggiungere nuove variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `CoverageEstimator`, `Gene`, `MobilityConfig`, `NodeCandidate`, `NodeType`, `OffloadingTimeBreakdown`, `OffloadingTimeModel`, `SystemSnapshot`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private final CoverageEstimator coverageEstimator`
- `private final OffloadingTimeModel offloadingTimeModel`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public DeadlineConstraintEvaluator()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public DeadlineConstraintEvaluator(MobilityConfig mobilityConfig)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public DeadlineConstraintEvaluator( CoverageEstimator coverageEstimator, OffloadingTimeModel offloadingTimeModel )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public DeadlineEvaluation evaluate( Gene gene, TaskInstance task, SystemSnapshot snapshot )` | public | Adapter compatibile con i chiamanti precedenti. |
| `public DeadlineEvaluation evaluate( Gene gene, TaskInstance task, SnapshotRepairContext context )` | public | Valuta un gene usando gli indici e la cache di copertura dello snapshot. Questo overload evita scansioni lineari ripetute durante il ciclo evolutivo. La semantica del vincolo resta invariata. |
| `private boolean isRemoteCoverageSufficient( SnapshotRepairContext context, TaskInstance task, NodeCandidate candidate, double completionTimeSeconds )` | private | Risponde con true/false alla domanda `is remote coverage sufficient`. |
| `private boolean isStrictlyPositive(double value)` | private | Risponde con true/false alla domanda `is strictly positive`. |

**Problematiche aperte**

- Valuta il singolo gene rispetto a deadline e copertura; non dimostra la fattibilita' globale di tutto il cromosoma.

### `DeadlineEvaluation`

- File: `src/ga/constraints/DeadlineEvaluation.java:17`
- Tipo: `class`
- Nome completo: `ga.constraints.DeadlineEvaluation`

**Cosa fa, in parole semplici**

Risultato della valutazione temporale di un gene rispetto alla deadline. La valutazione separa due condizioni: rispetto della deadline del task; sostenibilità mobility-aware della scelta remota. Una scelta è ammissibile per il repair ordinario soltanto quando entrambe le condizioni sono rispettate. Il valore di lateness viene usato nella sola modalità degradata best-effort per confrontare alternative tardive. Separa validita', rispetto deadline, sostenibilita' mobilita' e lateness.

**Relazione con la formalizzazione**

Rafforza il trattamento dei vincoli di deadline e copertura durante il repair, senza aggiungere nuove variabili decisionali.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double completionTimeSeconds`
- `private final double deadlineSeconds`
- `private final double latenessSeconds`
- `private final boolean deadlineRespected`
- `private final boolean mobilitySustainable`
- `private final boolean valid`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private DeadlineEvaluation( double completionTimeSeconds, double deadlineSeconds, double latenessSeconds, boolean deadlineRespected, boolean mobilitySustainable, boolean valid )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static DeadlineEvaluation valid( double completionTimeSeconds, double deadlineSeconds, boolean deadlineRespected, boolean mobilitySustainable )` | public | Metodo di supporto: realizza il passo `valid` dentro la responsabilita' della classe. |
| `public static DeadlineEvaluation invalid(double deadlineSeconds)` | public | Metodo di supporto: realizza il passo `invalid` dentro la responsabilita' della classe. |
| `public double getCompletionTimeSeconds()` | public | Restituisce il valore di `CompletionTimeSeconds` senza modificarlo. |
| `public double getDeadlineSeconds()` | public | Restituisce il valore di `DeadlineSeconds` senza modificarlo. |
| `public double getLatenessSeconds()` | public | Restituisce il valore di `LatenessSeconds` senza modificarlo. |
| `public boolean isDeadlineRespected()` | public | Risponde con true/false alla domanda `is deadline respected`. |
| `public boolean isMobilitySustainable()` | public | Risponde con true/false alla domanda `is mobility sustainable`. |
| `public boolean isValid()` | public | Risponde con true/false alla domanda `is valid`. |
| `public boolean isAdmissible()` | public | Restituisce true soltanto per una scelta utilizzabile dal repair ordinario. |

**Problematiche aperte**

- La lateness serve al best-effort, ma non certifica che il task sia matematicamente irrecuperabile.

### `DeadlineRepairCatalog`

- File: `src/ga/constraints/DeadlineRepairCatalog.java:32`
- Tipo: `class`
- Nome completo: `ga.constraints.DeadlineRepairCatalog`

**Cosa fa, in parole semplici**

Catalogo lazy dei profili deadline-aware riutilizzabili nello snapshot. Il catalogo non decide la strategia globale e non sostituisce il GA. Memorizza soltanto risultati deterministici del repair per una combinazione task-candidato-quota già esplorata: profilo a capacità massima, valutazione temporale e minima scala comune di CPU e banda ammissibile. Le quote vengono ancora esplorate dalla policy limitata del repair. La cache evita di ricalcolare le stesse formule durante figli e generazioni successive dello stesso snapshot. Memorizza profili deadline-aware per evitare ricalcoli durante il repair dello stesso snapshot.

**Relazione con la formalizzazione**

Rafforza il trattamento dei vincoli di deadline e copertura durante il repair, senza aggiungere nuove variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `CoverageEstimator`, `Gene`, `NodeCandidate`, `NodeType`, `OffloadingRatioPolicy`, `OffloadingTimeModel`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05`
- `private static final double MIN_RESOURCE_FRACTION = 0.05`
- `private final SnapshotRepairContext context`
- `private final CoverageEstimator coverageEstimator`
- `private final OffloadingTimeModel offloadingTimeModel`
- `private final OffloadingRatioPolicy offloadingRatioPolicy`
- `private final DeadlineConstraintEvaluator deadlineConstraintEvaluator`
- `private final Map<TaskCandidateKey, List<Double>> baseRatiosByTaskAndCandidate`
- `private final Map<ProfileKey, DeadlineRepairProfile> profileByKey`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public DeadlineRepairCatalog( SnapshotRepairContext context, CoverageEstimator coverageEstimator, OffloadingTimeModel offloadingTimeModel, OffloadingRatioPolicy offloadingRatioPolicy, DeadlineConstraintEvaluator deadlineConstraintEvaluator )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public SnapshotRepairContext getContext()` | public | Restituisce il valore di `Context` senza modificarlo. |
| `public List<Double> buildRatioCandidates( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double preferredRatio )` | public | Restituisce le quote considerate dal repair mantenendo lo stesso ordine della policy precedente: quota preferita, quota bilanciata e griglia. |
| `public DeadlineRepairProfile getProfile( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double ratio )` | public | Restituisce il profilo precalcolato o lo calcola al primo utilizzo. |
| `private List<Double> baseRatios( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | private | Metodo di supporto: realizza il passo `base ratios` dentro la responsabilita' della classe. |
| `private List<Double> computeBaseRatios( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | private | Calcola `compute base ratios` a partire dai dati ricevuti. |
| `private DeadlineRepairProfile computeProfile( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double ratio )` | private | Calcola `compute profile` a partire dai dati ricevuti. |
| `private Gene findMinimalFeasibleResourceScale( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double ratio, Gene maxCapacityGene, DeadlineEvaluation maxCapacityEvaluation )` | private | Calcola la minima scala comune di CPU e banda che rende ammissibile una scelta remota per una quota data. T_remote(q) = [p * input / Bmax + p * cycles / Fmax + output / Bmax] / q + tau_n Il calcolo è equivalente alla ricerca binaria rimossa nel livello 1, ma viene memorizzato e riutilizzato nello snapshot corrente. |
| `private double estimateRemoteTemporalLimitSeconds( TaskInstance task, NodeCandidate candidate )` | private | Stima `estimate remote temporal limit seconds` senza modificare lo stato principale. |
| `private Gene createScaledRemoteGene( TaskInstance task, NodeCandidate candidate, double ratio, double resourceScale )` | private | Crea `create scaled remote gene` come nuovo oggetto o nuova struttura dati. |
| `private double clampRemoteRatio(double ratio)` | private | Limita un valore dentro un intervallo ammesso. |
| `private double safeDivide(double numerator, double denominator)` | private | Esegue un'operazione protetta per evitare valori non finiti o divisioni non valide. |
| `private boolean isStrictlyPositive(double value)` | private | Risponde con true/false alla domanda `is strictly positive`. |
| `private double clamp(double value, double min, double max)` | private | Limita un valore dentro un intervallo ammesso. |

**Problematiche aperte**

- Esplora una griglia limitata di quote e profili; e' un repair deterministico/cache, non un secondo ottimizzatore completo.

### `DeadlineRepairProfile` (tipo interno di `DeadlineRepairCatalog`)

- File: `src/ga/constraints/DeadlineRepairCatalog.java:336`
- Tipo: `class`
- Nome completo: `ga.constraints.DeadlineRepairCatalog.DeadlineRepairProfile`

**Cosa fa, in parole semplici**

Profilo memorizzato per una combinazione task-candidato-quota.

**Relazione con la formalizzazione**

Rafforza il trattamento dei vincoli di deadline e copertura durante il repair, senza aggiungere nuove variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `CoverageEstimator`, `Gene`, `NodeCandidate`, `NodeType`, `OffloadingRatioPolicy`, `OffloadingTimeModel`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final Gene minimalFeasibleGene`
- `private final Gene maxCapacityGene`
- `private final DeadlineEvaluation maxCapacityEvaluation`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private DeadlineRepairProfile( Gene minimalFeasibleGene, Gene maxCapacityGene, DeadlineEvaluation maxCapacityEvaluation )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public Gene getMinimalFeasibleGene()` | public | Restituisce il valore di `MinimalFeasibleGene` senza modificarlo. |
| `public Gene getMaxCapacityGene()` | public | Restituisce il valore di `MaxCapacityGene` senza modificarlo. |
| `public DeadlineEvaluation getMaxCapacityEvaluation()` | public | Restituisce il valore di `MaxCapacityEvaluation` senza modificarlo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TaskCandidateKey` (tipo interno di `DeadlineRepairCatalog`)

- File: `src/ga/constraints/DeadlineRepairCatalog.java:364`
- Tipo: `class`
- Nome completo: `ga.constraints.DeadlineRepairCatalog.TaskCandidateKey`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Rafforza il trattamento dei vincoli di deadline e copertura durante il repair, senza aggiungere nuove variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `CoverageEstimator`, `Gene`, `NodeCandidate`, `NodeType`, `OffloadingRatioPolicy`, `OffloadingTimeModel`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String taskId`
- `private final String candidateId`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private TaskCandidateKey(String taskId, String candidateId)` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public boolean equals(Object other)` | public | Implementa confronto e hashing per usare l'oggetto in mappe o set. |
| `public int hashCode()` | public | Risponde con true/false alla domanda `hash code`. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `ProfileKey` (tipo interno di `DeadlineRepairCatalog`)

- File: `src/ga/constraints/DeadlineRepairCatalog.java:392`
- Tipo: `class`
- Nome completo: `ga.constraints.DeadlineRepairCatalog.ProfileKey`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Rafforza il trattamento dei vincoli di deadline e copertura durante il repair, senza aggiungere nuove variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `CoverageEstimator`, `Gene`, `NodeCandidate`, `NodeType`, `OffloadingRatioPolicy`, `OffloadingTimeModel`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String taskId`
- `private final String candidateId`
- `private final long ratioBits`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private ProfileKey(String taskId, String candidateId, double ratio)` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public boolean equals(Object other)` | public | Implementa confronto e hashing per usare l'oggetto in mappe o set. |
| `public int hashCode()` | public | Risponde con true/false alla domanda `hash code`. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SnapshotRepairContext`

- File: `src/ga/constraints/SnapshotRepairContext.java:28`
- Tipo: `class`
- Nome completo: `ga.constraints.SnapshotRepairContext`

**Cosa fa, in parole semplici**

Indici e cache riutilizzabili durante il repair di uno stesso snapshot. Durante una singola esecuzione del GA lo snapshot resta invariato. Questa classe evita quindi di scandire ripetutamente le liste di task, veicoli e candidati per ogni gene, figlio e generazione. Il contesto non introduce nuove decisioni e non modifica la formalizzazione: indicizza soltanto dati già osservati nello snapshot. Indicizza lo snapshot e mette in cache la copertura per repair e deadline evaluator.

**Relazione con la formalizzazione**

Rafforza il trattamento dei vincoli di deadline e copertura durante il repair, senza aggiungere nuove variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `CoverageEstimator`, `NodeCandidate`, `NodeType`, `SystemSnapshot`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final SystemSnapshot snapshot`
- `private final List<TaskInstance> tasks`
- `private final Map<String, TaskInstance> taskById`
- `private final Map<String, VehicleSnapshot> vehicleById`
- `private final Map<String, NodeCandidate> candidateById`
- `private final Map<String, List<NodeCandidate>> candidatesBySourceVehicleId`
- `private final Map<String, NodeCandidate> localCandidateBySourceVehicleId`
- `private final Map<String, Double> availableCpuByExecutionNodeId`
- `private final Map<CoverageKey, Double> coverageTimeByTaskAndCandidate`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public SnapshotRepairContext(SystemSnapshot snapshot)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public boolean isFor(SystemSnapshot candidateSnapshot)` | public | Risponde con true/false alla domanda `is for`. |
| `public SystemSnapshot getSnapshot()` | public | Restituisce il valore di `Snapshot` senza modificarlo. |
| `public List<TaskInstance> getTasks()` | public | Restituisce il valore di `Tasks` senza modificarlo. |
| `public TaskInstance getTaskById(String taskId)` | public | Restituisce il valore di `TaskById` senza modificarlo. |
| `public VehicleSnapshot getVehicleById(String vehicleId)` | public | Restituisce il valore di `VehicleById` senza modificarlo. |
| `public NodeCandidate getCandidateById(String candidateId)` | public | Restituisce il valore di `CandidateById` senza modificarlo. |
| `public List<NodeCandidate> getCandidatesForSourceVehicle(String sourceVehicleId)` | public | Restituisce il valore di `CandidatesForSourceVehicle` senza modificarlo. |
| `public List<NodeCandidate> getCandidatesForTask(TaskInstance task)` | public | Restituisce il valore di `CandidatesForTask` senza modificarlo. |
| `public NodeCandidate getLocalCandidateForTask(TaskInstance task)` | public | Restituisce il valore di `LocalCandidateForTask` senza modificarlo. |
| `public Map<String, Double> getAvailableCpuByExecutionNodeId()` | public | Restituisce il valore di `AvailableCpuByExecutionNodeId` senza modificarlo. |
| `public NodeCandidate requireLocalCandidateForTask(TaskInstance task)` | public | Metodo di supporto: realizza il passo `require local candidate for task` dentro la responsabilita' della classe. |
| `public double estimateCoverageTimeSeconds( TaskInstance task, NodeCandidate candidate, CoverageEstimator coverageEstimator )` | public | Restituisce il tempo di copertura, calcolandolo una sola volta per coppia task-candidato nello snapshot corrente. |
| `private double computeCoverageTimeSeconds( TaskInstance task, NodeCandidate candidate, CoverageEstimator coverageEstimator )` | private | Calcola `compute coverage time seconds` a partire dai dati ricevuti. |
| `private Map<String, TaskInstance> indexTasks(List<TaskInstance> source)` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private Map<String, VehicleSnapshot> indexVehicles(List<VehicleSnapshot> source)` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private Map<String, NodeCandidate> indexCandidates(List<NodeCandidate> source)` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private Map<String, List<NodeCandidate>> indexCandidatesBySourceVehicle( List<NodeCandidate> source )` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private Map<String, Double> indexAvailableCpuByExecutionNode( List<NodeCandidate> source )` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private Map<String, NodeCandidate> indexLocalCandidates(List<NodeCandidate> source)` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private <T> List<T> immutableCopy(List<T> source)` | private | Metodo di supporto: realizza il passo `immutable copy` dentro la responsabilita' della classe. |

**Problematiche aperte**

- La cache e' valida solo per lo snapshot corrente; non va riusata tra snapshot diversi.

### `CoverageKey` (tipo interno di `SnapshotRepairContext`)

- File: `src/ga/constraints/SnapshotRepairContext.java:242`
- Tipo: `class`
- Nome completo: `ga.constraints.SnapshotRepairContext.CoverageKey`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Rafforza il trattamento dei vincoli di deadline e copertura durante il repair, senza aggiungere nuove variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `CoverageEstimator`, `NodeCandidate`, `NodeType`, `SystemSnapshot`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String taskId`
- `private final String candidateId`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private CoverageKey(String taskId, String candidateId)` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public boolean equals(Object other)` | public | Implementa confronto e hashing per usare l'oggetto in mappe o set. |
| `public int hashCode()` | public | Risponde con true/false alla domanda `hash code`. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `ga.core`

Coordina il ciclo evolutivo e conserva il risultato finale.

### `GenerationStat`

- File: `src/ga/core/GenerationStat.java:9`
- Tipo: `class`
- Nome completo: `ga.core.GenerationStat`

**Cosa fa, in parole semplici**

Contiene statistiche sintetiche su una generazione del Genetic Algorithm. Le statistiche sono usate dai report per ricostruire l'andamento della fitness durante l'evoluzione.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final int generationIndex`
- `private final double bestFitness`
- `private final double averageFitness`
- `private final double worstFitness`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public GenerationStat( int generationIndex, double bestFitness, double averageFitness, double worstFitness )` | public | Costruisce le statistiche di una generazione. @param generationIndex indice della generazione @param bestFitness migliore fitness della generazione @param averageFitness fitness media della generazione @param worstFitness peggiore fitness della generazione |
| `public int getGenerationIndex()` | public | Restituisce l'indice della generazione. |
| `public double getBestFitness()` | public | Restituisce la migliore fitness della generazione. |
| `public double getAverageFitness()` | public | Restituisce la fitness media della generazione. |
| `public double getWorstFitness()` | public | Restituisce la peggiore fitness della generazione. |
| `public String toString()` | public | Restituisce una rappresentazione testuale sintetica della generazione. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `MaGaOptimizer`

- File: `src/ga/core/MaGaOptimizer.java:36`
- Tipo: `class`
- Nome completo: `ga.core.MaGaOptimizer`

**Cosa fa, in parole semplici**

Orchestratore principale del MA-GA sul singolo snapshot. Il MA-GA resta snapshot-based: ogni esecuzione riceve uno `SystemSnapshot`, prepara una popolazione coerente con quello snapshot, evolve i cromosomi e restituisce sia la soluzione migliore sia il breakdown diagnostico della popolazione finale. La scelta tra cold start, warm start e partial restart non appartiene a questa classe. Il package `window` decide la strategia temporale e passa qui l'eventuale popolazione iniziale. Coordina il ciclo genetico su uno snapshot e usa repair incrementale durante le generazioni.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `CrossoverOperator`, `ElitismOperator`, `EvaluationBreakdown`, `FitnessEvaluator`, `GeneticAlgorithmConfig`, `MaGaConfig`, `MobilityConfig`, `MutationOperator`, `MutationResult`, `PopulationInitializer`, `RepairOperator`, `SelectionOperator`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final int DEFAULT_TOURNAMENT_SIZE = 3`
- `private final MaGaConfig config`
- `private GeneticAlgorithmConfig gaConfig`
- `private final FitnessEvaluator fitnessEvaluator`
- `private final PopulationInitializer populationInitializer`
- `private final RepairOperator repairOperator`
- `private final SelectionOperator selectionOperator`
- `private final CrossoverOperator crossoverOperator`
- `private final MutationOperator mutationOperator`
- `private final ElitismOperator elitismOperator`
- `private final Random random`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public MaGaOptimizer(MaGaConfig config)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public Chromosome optimize(SystemSnapshot snapshot)` | public | Esegue il MA-GA partendo da popolazione generata internamente. |
| `public MobilityConfig getMobilityConfig()` | public | Restituisce la configurazione di mobilità usata da repair e fitness. |
| `public Chromosome optimize( SystemSnapshot snapshot, List<Chromosome> initialPopulation )` | public | Esegue il MA-GA partendo da una popolazione iniziale esterna. |
| `public MaGaResult optimizeDetailed(SystemSnapshot snapshot)` | public | Esegue il MA-GA e restituisce il risultato completo. |
| `public MaGaResult optimizeDetailed( SystemSnapshot snapshot, List<Chromosome> initialPopulation )` | public | Esegue il MA-GA e restituisce un risultato completo. Durante le generazioni interne dello stesso snapshot usa il repair incrementale. La mutazione espone i task realmente modificati; il repair rivaluta solo quei geni ma mantiene sempre il controllo CPU aggregato globale, necessario anche dopo il crossover. |
| `private List<Chromosome> prepareInitialPopulation( SystemSnapshot snapshot, List<Chromosome> initialPopulation )` | private | Metodo di supporto: realizza il passo `prepare initial population` dentro la responsabilita' della classe. |
| `private List<Chromosome> prepareFinalPopulationForResult( List<Chromosome> population, Chromosome bestOverall )` | private | Prepara la popolazione finale riutilizzabile dalle finestre successive. |
| `private boolean shouldApplyCrossover()` | private | Risponde con true/false alla domanda `should apply crossover`. |
| `private void evaluatePopulation( List<Chromosome> population, SystemSnapshot snapshot )` | private | Valuta `evaluate population` e restituisce un risultato o un breakdown. |
| `private Chromosome findBest(List<Chromosome> population)` | private | Cerca `find best` nelle collezioni o nello stato corrente. |
| `private boolean hasImproved(Chromosome candidate, Chromosome currentBest)` | private | Risponde con true/false alla domanda `has improved`. |
| `private Chromosome copyChromosome(Chromosome source)` | private | Crea una copia per evitare di modificare direttamente l'oggetto originale. |
| `private GenerationStat computeGenerationStat( int generationIndex, List<Chromosome> population )` | private | Calcola `compute generation stat` a partire dai dati ricevuti. |
| `private void validateSnapshot(SystemSnapshot snapshot)` | private | Controlla la correttezza di `validate snapshot` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `MaGaResult`

- File: `src/ga/core/MaGaResult.java:30`
- Tipo: `class`
- Nome completo: `ga.core.MaGaResult`

**Cosa fa, in parole semplici**

Risultato completo di una esecuzione del MA-GA su uno snapshot. Questa classe separa: - ottimizzazione; - valutazione; - stampa; - analisi sperimentale; - riuso temporale della popolazione. Nel gestore temporale, ogni finestra k deve poter conservare: - C*_k, cioè il miglior cromosoma trovato; - P_final_k, cioè la popolazione finale ottenuta al termine del GA.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `EvaluationBreakdown`, `FitnessEvaluator`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String snapshotId`
- `private final double snapshotTimeSeconds`
- `private final Chromosome bestChromosome`
- `private final EvaluationBreakdown bestEvaluation`
- `private final int generationsExecuted`
- `private final StopReason stopReason`
- `private final double initialBestFitness`
- `private final double finalBestFitness`
- `private final List<GenerationStat> generationHistory`
- `private final List<Chromosome> finalPopulation`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public MaGaResult( String snapshotId, double snapshotTimeSeconds, Chromosome bestChromosome, EvaluationBreakdown bestEvaluation, int generationsExecuted, StopReason stopReason, double initialBestFitness, double finalBestFitness, List<GenerationStat> generationHistory, List<Chromosome> finalPopulation )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public String getSnapshotId()` | public | Restituisce il valore di `SnapshotId` senza modificarlo. |
| `public double getSnapshotTimeSeconds()` | public | Restituisce il valore di `SnapshotTimeSeconds` senza modificarlo. |
| `public Chromosome getBestChromosome()` | public | Restituisce il valore di `BestChromosome` senza modificarlo. |
| `public EvaluationBreakdown getBestEvaluation()` | public | Restituisce il valore di `BestEvaluation` senza modificarlo. |
| `public int getGenerationsExecuted()` | public | Restituisce il valore di `GenerationsExecuted` senza modificarlo. |
| `public StopReason getStopReason()` | public | Restituisce il valore di `StopReason` senza modificarlo. |
| `public double getInitialBestFitness()` | public | Restituisce il valore di `InitialBestFitness` senza modificarlo. |
| `public double getFinalBestFitness()` | public | Restituisce il valore di `FinalBestFitness` senza modificarlo. |
| `public List<GenerationStat> getGenerationHistory()` | public | Restituisce il valore di `GenerationHistory` senza modificarlo. |
| `public List<Chromosome> getFinalPopulation()` | public | Restituisce la popolazione finale prodotta dal MA-GA. Questa lista è immutabile rispetto al riferimento esterno. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `StopReason`

- File: `src/ga/core/StopReason.java:8`
- Tipo: `enum`
- Nome completo: `ga.core.StopReason`

**Cosa fa, in parole semplici**

Indica perché il ciclo evolutivo del MA-GA si è fermato. Questa informazione è utile per debug, diario di bordo e analisi sperimentale.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`EMPTY_TASK_SET`, `MAX_GENERATIONS_REACHED`, `STAGNATION_REACHED`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `ga.fitness`

Calcola la funzione di fitness e classifica il tipo di decisione.

### `DecisionType`

- File: `src/ga/fitness/DecisionType.java:9`
- Tipo: `enum`
- Nome completo: `ga.fitness.DecisionType`

**Cosa fa, in parole semplici**

Descrive il tipo operativo di decisione rappresentata da un gene. Questa informazione serve sia per la stampa dei risultati sia per eventuali analisi future quando il MA-GA sarà collegato a MOSAIC.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`LOCAL_EXECUTION`, `FULL_OFFLOADING`, `PARTIAL_OFFLOADING`, `UNKNOWN`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `FitnessEvaluator`

- File: `src/ga/fitness/FitnessEvaluator.java:52`
- Tipo: `class`
- Nome completo: `ga.fitness.FitnessEvaluator`

**Cosa fa, in parole semplici**

Valuta un cromosoma MA-GA rispetto a uno snapshot del sistema. La valutazione combina quattro famiglie di costo: tempo massimo di completamento dei task; latenza comunicativa complessivamente introdotta dalle decisioni remote; rischio mobility-aware legato alla copertura e alla stabilità del link; penalità di vincolo e sovrauso risorse. Il tempo di copertura e l'instabilità del collegamento vengono calcolati tramite `CoverageEstimator`, così il modello non dipende da valori precomputati dentro `NodeCandidate`. Il vincolo di deadline mantiene la penalità proporzionale già usata nel breakdown, ma introduce anche una penalità rigida indipendente dai pesi configurabili. In questo modo un cromosoma tardivo non può essere preferito a un cromosoma ammissibile soltanto perché `wR` è basso o nullo. Implementa `J(C)`, ora con latenza sommata, breakdown di mobilita' e penalita' hard per deadline violate.

**Relazione con la formalizzazione**

Realizza la funzione obiettivo `J(C)` con pesi, normalizzazione e penalita'.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `CoverageEstimator`, `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `FitnessWeights`, `Gene`, `GeneEvaluationBreakdown`, `LinkBandwidthUsageBreakdown`, `LocalResourceUsageBreakdown`, `MaGaConfig`, `MobilityLinkMetrics`, `MobilityPenaltyBreakdown`, `NodeCandidate`, `NodeType`, `NormalizationConfig`, `OffloadingTimeBreakdown`, ... altri 5.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private static final double INVALID_SOLUTION_PENALTY = 1.0E9`
- `private static final double HARD_DEADLINE_VIOLATION_PENALTY = 1.0E12`
- `private final MaGaConfig config`
- `private final CoverageEstimator coverageEstimator`
- `private final OffloadingTimeModel offloadingTimeModel`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public FitnessEvaluator(MaGaConfig config)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public FitnessEvaluator( MaGaConfig config, CoverageEstimator coverageEstimator )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public double evaluate(Chromosome chromosome, SystemSnapshot snapshot)` | public | Valuta `evaluate` e restituisce un risultato o un breakdown. |
| `public EvaluationBreakdown evaluateDetailed( Chromosome chromosome, SystemSnapshot snapshot )` | public | Valuta `evaluate detailed` e restituisce un risultato o un breakdown. |
| `private GeneEvaluationBreakdown evaluateGene( SystemSnapshot snapshot, TaskInstance task, Gene gene, NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | private | Valuta `evaluate gene` e restituisce un risultato o un breakdown. |
| `private Map<String, ExecutionNodeResourceUsageBreakdown> initializeExecutionNodeCpuUsage(List<NodeCandidate> candidates)` | private | Metodo di supporto: realizza il passo `initialize execution node cpu usage` dentro la responsabilita' della classe. |
| `private Map<String, LinkBandwidthUsageBreakdown> initializeLinkBandwidthUsage(List<NodeCandidate> candidates)` | private | Metodo di supporto: realizza il passo `initialize link bandwidth usage` dentro la responsabilita' della classe. |
| `private Map<String, LocalResourceUsageBreakdown> initializeLocalUsage(List<VehicleSnapshot> vehicles)` | private | Metodo di supporto: realizza il passo `initialize local usage` dentro la responsabilita' della classe. |
| `private double computeResourcePenalty( Map<String, ExecutionNodeResourceUsageBreakdown> cpuUsageByExecutionNode, Map<String, LinkBandwidthUsageBreakdown> bandwidthUsageByCandidate )` | private | Calcola `compute resource penalty` a partire dai dati ricevuti. |
| `private MobilityPenaltyBreakdown computeMobilityPenaltyBreakdown( NodeCandidate candidate, MobilityLinkMetrics linkMetrics, double completionTimeSeconds, PenaltyConfig penalties )` | private | Calcola la penalità mobility-aware e conserva il breakdown diagnostico. |
| `private double computeDeadlinePenalty( double completionTimeSeconds, double deadlineSeconds, PenaltyConfig penalties )` | private | Calcola `compute deadline penalty` a partire dai dati ricevuti. |
| `private double computeHardDeadlinePenalty( List<GeneEvaluationBreakdown> geneBreakdowns )` | private | Penalità rigida applicata direttamente alla fitness. La parte costante rende ogni violazione nettamente peggiore rispetto a una strategia interamente ammissibile. La parte proporzionale mantiene un ordinamento utile anche quando tutte le alternative disponibili sono degradate. |
| `private boolean isDeadlineRespected( double completionTimeSeconds, double deadlineSeconds )` | private | Risponde con true/false alla domanda `is deadline respected`. |
| `private double computeCardinalityPenalty( List<TaskInstance> tasks, List<Gene> genes )` | private | Calcola `compute cardinality penalty` a partire dai dati ricevuti. |
| `private double computeUnknownGeneTaskPenalty( Map<String, Gene> geneByTaskId, Map<String, TaskInstance> taskById )` | private | Calcola `compute unknown gene task penalty` a partire dai dati ricevuti. |
| `private Map<String, TaskInstance> indexTasks(List<TaskInstance> tasks)` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private Map<String, VehicleSnapshot> indexVehicles( List<VehicleSnapshot> vehicles )` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private Map<String, NodeCandidate> indexCandidates( List<NodeCandidate> candidates )` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private Map<String, Gene> indexGenes(List<Gene> genes)` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private <T> List<T> requireList(List<T> list, String name)` | private | Metodo di supporto: realizza il passo `require list` dentro la responsabilita' della classe. |
| `private boolean isStrictlyPositive(double value)` | private | Risponde con true/false alla domanda `is strictly positive`. |
| `private double safeDivide(double numerator, double denominator)` | private | Esegue un'operazione protetta per evitare valori non finiti o divisioni non valide. |
| `private double clamp(double value, double min, double max)` | private | Limita un valore dentro un intervallo ammesso. |

**Problematiche aperte**

- La deadline non e' piu' soltanto soft: esiste una penalita' hard indipendente da `wR`. Restano pero' possibili soluzioni degradate se il repair non trova alternative ammissibili.
- La banda e' ancora penalizzata per candidato/link source-aware, non come unico vincolo globale `Bmax`.
- La saturazione sotto il limite resta diagnostica: diventa penalita' solo oltre il limite.

## Package `ga.fitness.breakdown`

Conserva dettagli diagnostici della fitness, inclusi risorse, latenza e mobilita'.

### `EvaluationBreakdown`

- File: `src/ga/fitness/breakdown/EvaluationBreakdown.java:11`
- Tipo: `class`
- Nome completo: `ga.fitness.breakdown.EvaluationBreakdown`

**Cosa fa, in parole semplici**

Risultato dettagliato della valutazione di un cromosoma. Contiene la fitness finale, i contributi normalizzati e i breakdown necessari per report, debug e analisi della soluzione.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double fitness`
- `private final double completionTimeSeconds`
- `private final double communicationLatencySeconds`
- `private final double mobilityPenalty`
- `private final double resourcePenalty`
- `private final double normalizedCompletionTime`
- `private final double normalizedCommunicationLatency`
- `private final double normalizedMobilityPenalty`
- `private final double normalizedResourcePenalty`
- `private final List<GeneEvaluationBreakdown> geneBreakdowns`
- `private final List<ExecutionNodeResourceUsageBreakdown> executionNodeResourceUsageBreakdowns`
- `private final List<LinkBandwidthUsageBreakdown> linkBandwidthUsageBreakdowns`
- `private final List<LocalResourceUsageBreakdown> localResourceUsageBreakdowns`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public EvaluationBreakdown( double fitness, double completionTimeSeconds, double communicationLatencySeconds, double mobilityPenalty, double resourcePenalty, double normalizedCompletionTime, double normalizedCommunicationLatency, double normalizedMobilityPenalty, double normalizedResourcePenalty, List<GeneEvaluationBreakdown> geneBreakdowns, List<ExecutionNodeResourceUsageBreakdown> executionNodeResourceUsageBreakdowns, List<LinkBandwidthUsageBreakdown> linkBandwidthUsageBreakdowns, List<LocalResourceUsageBreakdown> localResourceUsageBreakdowns )` | public | Crea il breakdown globale della fitness. @param fitness valore finale della fitness @param completionTimeSeconds tempo di completamento del cromosoma @param communicationLatencySeconds latenza comunicativa totale introdotta dalla soluzione @param mobilityPenalty penalità di mobilità @param resourcePenalty penalità di risorse e vincoli @param normalizedCompletionTime tempo normalizzato @param normalizedCommunicationLatency latenza totale normalizzata @param normalizedMobilityPenalty penalità mobilità normalizzata @param normalizedResourcePenalty penalità risorse normalizzata @param geneBreakdowns breakdown dei singoli geni @param executionNodeResourceUsageBreakdowns uso CPU per nodo fisico @param linkBandwidthUsageBreakdowns uso banda per candidato/link @param localResourceUsageBreakdowns uso locale per veicolo |
| `public double getFitness()` | public | Restituisce il valore di `Fitness` senza modificarlo. |
| `public double getCompletionTimeSeconds()` | public | Restituisce il valore di `CompletionTimeSeconds` senza modificarlo. |
| `public double getCommunicationLatencySeconds()` | public | Restituisce il valore di `CommunicationLatencySeconds` senza modificarlo. |
| `public double getMobilityPenalty()` | public | Restituisce il valore di `MobilityPenalty` senza modificarlo. |
| `public double getResourcePenalty()` | public | Restituisce il valore di `ResourcePenalty` senza modificarlo. |
| `public double getNormalizedCompletionTime()` | public | Restituisce il valore di `NormalizedCompletionTime` senza modificarlo. |
| `public double getNormalizedCommunicationLatency()` | public | Restituisce il valore di `NormalizedCommunicationLatency` senza modificarlo. |
| `public double getNormalizedMobilityPenalty()` | public | Restituisce il valore di `NormalizedMobilityPenalty` senza modificarlo. |
| `public double getNormalizedResourcePenalty()` | public | Restituisce il valore di `NormalizedResourcePenalty` senza modificarlo. |
| `public List<GeneEvaluationBreakdown> getGeneBreakdowns()` | public | Restituisce il valore di `GeneBreakdowns` senza modificarlo. |
| `public List<ExecutionNodeResourceUsageBreakdown> getExecutionNodeResourceUsageBreakdowns()` | public | Restituisce il valore di `ExecutionNodeResourceUsageBreakdowns` senza modificarlo. |
| `public List<LinkBandwidthUsageBreakdown> getLinkBandwidthUsageBreakdowns()` | public | Restituisce il valore di `LinkBandwidthUsageBreakdowns` senza modificarlo. |
| `public List<LocalResourceUsageBreakdown> getLocalResourceUsageBreakdowns()` | public | Restituisce il valore di `LocalResourceUsageBreakdowns` senza modificarlo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `ExecutionNodeResourceUsageBreakdown`

- File: `src/ga/fitness/breakdown/ExecutionNodeResourceUsageBreakdown.java:11`
- Tipo: `class`
- Nome completo: `ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown`

**Cosa fa, in parole semplici**

Uso aggregato della CPU su un nodo fisico di esecuzione. Più candidati possono puntare allo stesso executionNodeId. Questa classe serve a controllare l'uso complessivo della CPU del nodo.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private static final double RELATIVE_CPU_TOLERANCE = 1.0E-9`
- `private static final double ABSOLUTE_CPU_TOLERANCE = 1.0E-6`
- `private final String executionNodeId`
- `private final NodeType nodeType`
- `private final double availableCpu`
- `private double usedCpu`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public ExecutionNodeResourceUsageBreakdown( String executionNodeId, NodeType nodeType, double availableCpu )` | public | Crea il breakdown di uso CPU per un nodo fisico. @param executionNodeId nodo fisico di esecuzione @param nodeType tipo del nodo @param availableCpu CPU disponibile sul nodo |
| `public void addCpu(double value)` | public | Aggiunge CPU assegnata da un gene. @param value CPU da sommare |
| `public String getExecutionNodeId()` | public | Restituisce il valore di `ExecutionNodeId` senza modificarlo. |
| `public NodeType getNodeType()` | public | Restituisce il valore di `NodeType` senza modificarlo. |
| `public double getAvailableCpu()` | public | Restituisce il valore di `AvailableCpu` senza modificarlo. |
| `public double getUsedCpu()` | public | Restituisce il valore di `UsedCpu` senza modificarlo. |
| `public double getCpuUsagePercent()` | public | Restituisce il valore di `CpuUsagePercent` senza modificarlo. |
| `public double getCpuOverflowRatio()` | public | Restituisce il valore di `CpuOverflowRatio` senza modificarlo. |
| `public boolean hasCpuViolation()` | public | Risponde con true/false alla domanda `has cpu violation`. |
| `public boolean isCpuSaturated(double thresholdPercent)` | public | Risponde con true/false alla domanda `is cpu saturated`. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `GeneEvaluationBreakdown`

- File: `src/ga/fitness/breakdown/GeneEvaluationBreakdown.java:14`
- Tipo: `class`
- Nome completo: `ga.fitness.breakdown.GeneEvaluationBreakdown`

**Cosa fa, in parole semplici**

Dettaglio della valutazione di un singolo gene. Ogni istanza descrive come un task viene eseguito e quali tempi, penalità e risorse derivano dalla scelta fatta dal cromosoma.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `DecisionType`, `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String taskId`
- `private final String sourceVehicleId`
- `private final String selectedCandidateId`
- `private final String executionNodeId`
- `private final NodeType nodeType`
- `private final DecisionType decisionType`
- `private final double offloadingRatio`
- `private final double allocatedCpu`
- `private final double allocatedBandwidth`
- `private final double localCpuCycles`
- `private final double localExecutionTimeSeconds`
- `private final double uploadTimeSeconds`
- `private final double remoteExecutionTimeSeconds`
- `private final double downloadTimeSeconds`
- `private final double propagationDelaySeconds`
- `private final double remotePartTimeSeconds`
- `private final double completionTimeSeconds`
- `private final double communicationLatencySeconds`
- `private final double mobilityPenalty`
- `private final double constraintPenalty`
- `private final double deadlineSeconds`
- `private final boolean deadlineRespected`
- `private final double coverageTimeSeconds`
- `private final boolean coverageSufficient`
- `private final MobilityPenaltyBreakdown mobilityBreakdown`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public String getTaskId()` | public | Restituisce il valore di `TaskId` senza modificarlo. |
| `public String getSourceVehicleId()` | public | Restituisce il valore di `SourceVehicleId` senza modificarlo. |
| `public String getSelectedCandidateId()` | public | Restituisce il valore di `SelectedCandidateId` senza modificarlo. |
| `public String getExecutionNodeId()` | public | Restituisce il valore di `ExecutionNodeId` senza modificarlo. |
| `public NodeType getNodeType()` | public | Restituisce il valore di `NodeType` senza modificarlo. |
| `public DecisionType getDecisionType()` | public | Restituisce il valore di `DecisionType` senza modificarlo. |
| `public double getOffloadingRatio()` | public | Restituisce il valore di `OffloadingRatio` senza modificarlo. |
| `public double getAllocatedCpu()` | public | Restituisce il valore di `AllocatedCpu` senza modificarlo. |
| `public double getAllocatedBandwidth()` | public | Restituisce il valore di `AllocatedBandwidth` senza modificarlo. |
| `public double getLocalCpuCycles()` | public | Restituisce il valore di `LocalCpuCycles` senza modificarlo. |
| `public double getLocalExecutionTimeSeconds()` | public | Restituisce il valore di `LocalExecutionTimeSeconds` senza modificarlo. |
| `public double getUploadTimeSeconds()` | public | Restituisce il valore di `UploadTimeSeconds` senza modificarlo. |
| `public double getRemoteExecutionTimeSeconds()` | public | Restituisce il valore di `RemoteExecutionTimeSeconds` senza modificarlo. |
| `public double getDownloadTimeSeconds()` | public | Restituisce il valore di `DownloadTimeSeconds` senza modificarlo. |
| `public double getPropagationDelaySeconds()` | public | Restituisce tau_n: il ritardo end-to-end aggregato del percorso remoto. |
| `public double getBaseLatencySeconds()` | public | Alias mantenuto per compatibilità con printer e chiamanti precedenti. @deprecated usare `#getPropagationDelaySeconds()` |
| `public double getRemotePartTimeSeconds()` | public | Restituisce il valore di `RemotePartTimeSeconds` senza modificarlo. |
| `public double getCompletionTimeSeconds()` | public | Restituisce il valore di `CompletionTimeSeconds` senza modificarlo. |
| `public double getCommunicationLatencySeconds()` | public | Restituisce il valore di `CommunicationLatencySeconds` senza modificarlo. |
| `public double getMobilityPenalty()` | public | Restituisce il valore di `MobilityPenalty` senza modificarlo. |
| `public double getConstraintPenalty()` | public | Restituisce il valore di `ConstraintPenalty` senza modificarlo. |
| `public double getDeadlineSeconds()` | public | Restituisce il valore di `DeadlineSeconds` senza modificarlo. |
| `public boolean isDeadlineRespected()` | public | Risponde con true/false alla domanda `is deadline respected`. |
| `public double getCoverageTimeSeconds()` | public | Restituisce il valore di `CoverageTimeSeconds` senza modificarlo. |
| `public boolean isCoverageSufficient()` | public | Risponde con true/false alla domanda `is coverage sufficient`. |
| `public MobilityPenaltyBreakdown getMobilityBreakdown()` | public | Restituisce il valore di `MobilityBreakdown` senza modificarlo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `LinkBandwidthUsageBreakdown`

- File: `src/ga/fitness/breakdown/LinkBandwidthUsageBreakdown.java:11`
- Tipo: `class`
- Nome completo: `ga.fitness.breakdown.LinkBandwidthUsageBreakdown`

**Cosa fa, in parole semplici**

Uso della banda associata a un candidato/link source-aware. A differenza della CPU, la banda è legata al collegamento tra sorgente e candidato selezionato.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private final String candidateId`
- `private final String sourceVehicleId`
- `private final String executionNodeId`
- `private final NodeType nodeType`
- `private final double availableBandwidth`
- `private double usedBandwidth`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public LinkBandwidthUsageBreakdown( String candidateId, String sourceVehicleId, String executionNodeId, NodeType nodeType, double availableBandwidth )` | public | Crea il breakdown di uso banda per un candidato. @param candidateId candidato/link selezionabile @param sourceVehicleId veicolo sorgente @param executionNodeId nodo fisico di esecuzione @param nodeType tipo del nodo @param availableBandwidth banda disponibile |
| `public void addBandwidth(double value)` | public | Aggiunge banda assegnata da un gene. @param value banda da sommare |
| `public String getCandidateId()` | public | Restituisce il valore di `CandidateId` senza modificarlo. |
| `public String getSourceVehicleId()` | public | Restituisce il valore di `SourceVehicleId` senza modificarlo. |
| `public String getExecutionNodeId()` | public | Restituisce il valore di `ExecutionNodeId` senza modificarlo. |
| `public NodeType getNodeType()` | public | Restituisce il valore di `NodeType` senza modificarlo. |
| `public double getAvailableBandwidth()` | public | Restituisce il valore di `AvailableBandwidth` senza modificarlo. |
| `public double getUsedBandwidth()` | public | Restituisce il valore di `UsedBandwidth` senza modificarlo. |
| `public double getBandwidthUsagePercent()` | public | Restituisce il valore di `BandwidthUsagePercent` senza modificarlo. |
| `public double getBandwidthOverflowRatio()` | public | Restituisce il valore di `BandwidthOverflowRatio` senza modificarlo. |
| `public boolean hasBandwidthViolation()` | public | Risponde con true/false alla domanda `has bandwidth violation`. |
| `public boolean isBandwidthSaturated(double thresholdPercent)` | public | Risponde con true/false alla domanda `is bandwidth saturated`. |

**Problematiche aperte**

- Il vincolo di banda e' per link/candidateId. Se la formalizzazione resta con `Bmax` globale, questa classe rappresenta una scelta diversa.
- La saturazione al 95-100% e' diagnostica: diventa penalita' solo se supera il limite.

### `LocalResourceUsageBreakdown`

- File: `src/ga/fitness/breakdown/LocalResourceUsageBreakdown.java:9`
- Tipo: `class`
- Nome completo: `ga.fitness.breakdown.LocalResourceUsageBreakdown`

**Cosa fa, in parole semplici**

Carico locale stimato per un veicolo. Tiene traccia dei cicli eseguiti localmente e del tempo massimo di esecuzione locale osservato per quel veicolo.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private final String vehicleId`
- `private final double localCpu`
- `private double localCpuCycles`
- `private double maxLocalExecutionTimeSeconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public LocalResourceUsageBreakdown( String vehicleId, double localCpu )` | public | Crea il breakdown di uso locale per un veicolo. @param vehicleId veicolo sorgente @param localCpu CPU locale disponibile |
| `public void addLocalWorkload( double cpuCycles, double localExecutionTimeSeconds )` | public | Aggiunge workload locale prodotto da un gene. @param cpuCycles cicli CPU eseguiti localmente @param localExecutionTimeSeconds tempo locale del gene |
| `public String getVehicleId()` | public | Restituisce il valore di `VehicleId` senza modificarlo. |
| `public double getLocalCpu()` | public | Restituisce il valore di `LocalCpu` senza modificarlo. |
| `public double getLocalCpuCycles()` | public | Restituisce il valore di `LocalCpuCycles` senza modificarlo. |
| `public double getMaxLocalExecutionTimeSeconds()` | public | Restituisce il valore di `MaxLocalExecutionTimeSeconds` senza modificarlo. |
| `public boolean hasLocalWorkload()` | public | Risponde con true/false alla domanda `has local workload`. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `MobilityPenaltyBreakdown`

- File: `src/ga/fitness/breakdown/MobilityPenaltyBreakdown.java:15`
- Tipo: `class`
- Nome completo: `ga.fitness.breakdown.MobilityPenaltyBreakdown`

**Cosa fa, in parole semplici**

Scomposizione della penalità mobility-aware associata a un gene. La classe conserva sia i valori grezzi sia i contributi pesati usati dalla fitness. Non modifica il calcolo: rende soltanto il risultato osservabile nei report diagnostici. Scompone `Pmob` in rischio copertura, instabilita' link e rischio handover.

**Relazione con la formalizzazione**

Implementa la parte mobility-aware: tempo di copertura, instabilita' link, rischio handover e limiti temporali collegati alla copertura.

**Con chi comunica**

Comunica direttamente con: `MobilityLinkMetrics`, `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final MobilityLinkMetrics linkMetrics`
- `private final double coverageRisk`
- `private final double linkInstability`
- `private final double handoverRisk`
- `private final double weightedCoverageRisk`
- `private final double weightedLinkInstability`
- `private final double weightedHandoverRisk`
- `private final double totalMobilityPenalty`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public MobilityPenaltyBreakdown( MobilityLinkMetrics linkMetrics, double coverageRisk, double linkInstability, double handoverRisk, double weightedCoverageRisk, double weightedLinkInstability, double weightedHandoverRisk )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static MobilityPenaltyBreakdown zero(MobilityLinkMetrics linkMetrics)` | public | Metodo di supporto: realizza il passo `zero` dentro la responsabilita' della classe. |
| `public static MobilityPenaltyBreakdown legacy( NodeType nodeType, double coverageTimeSeconds, double totalMobilityPenalty )` | public | Adapter conservativo per eventuali chiamanti legacy che non producono ancora il breakdown dettagliato. |
| `public MobilityLinkMetrics getLinkMetrics()` | public | Restituisce il valore di `LinkMetrics` senza modificarlo. |
| `public double getCoverageRisk()` | public | Restituisce il valore di `CoverageRisk` senza modificarlo. |
| `public double getLinkInstability()` | public | Restituisce il valore di `LinkInstability` senza modificarlo. |
| `public double getHandoverRisk()` | public | Restituisce il valore di `HandoverRisk` senza modificarlo. |
| `public double getWeightedCoverageRisk()` | public | Restituisce il valore di `WeightedCoverageRisk` senza modificarlo. |
| `public double getWeightedLinkInstability()` | public | Restituisce il valore di `WeightedLinkInstability` senza modificarlo. |
| `public double getWeightedHandoverRisk()` | public | Restituisce il valore di `WeightedHandoverRisk` senza modificarlo. |
| `public double getTotalMobilityPenalty()` | public | Restituisce il valore di `TotalMobilityPenalty` senza modificarlo. |
| `private static double clamp01(double value)` | private | Limita un valore dentro un intervallo ammesso. |
| `private static double finiteOrZero(double value)` | private | Metodo di supporto: realizza il passo `finite or zero` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `ga.operators`

Contiene inizializzazione, selezione, crossover, mutazione, repair e policy di allocazione.

### `CpuAggregateRepairOperator`

- File: `src/ga/operators/CpuAggregateRepairOperator.java:26`
- Tipo: `class`
- Nome completo: `ga.operators.CpuAggregateRepairOperator`

**Cosa fa, in parole semplici**

Ripara l'allocazione CPU aggregata sui nodi fisici remoti. Il RepairOperator limita già la CPU del singolo gene rispetto al candidato scelto. Questo operatore controlla invece la somma delle CPU assegnate allo stesso `executionNodeId`. La banda non viene modificata: il repair della banda resta una OpenIssue. Riduce la CPU assegnata quando piu' geni sovraccaricano lo stesso nodo fisico.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `Gene`, `NodeCandidate`, `NodeType`, `SnapshotRepairContext`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public Chromosome repairChromosome( Chromosome chromosome, SystemSnapshot snapshot )` | public | Adapter compatibile con i chiamanti precedenti. |
| `public CpuAggregateRepairResult repairChromosomeDetailed( Chromosome chromosome, SystemSnapshot snapshot, SnapshotRepairContext context )` | public | Ridimensiona proporzionalmente la CPU dei geni remoti quando la somma assegnata allo stesso nodo fisico supera la CPU disponibile. L'esito dettagliato permette al chiamante di ripetere il repair gene-level soltanto sui task effettivamente modificati. |
| `private Map<String, Double> computeUsedCpuByExecutionNode( Chromosome chromosome, SnapshotRepairContext context )` | private | Calcola la CPU remota totale richiesta da ogni nodo fisico. |
| `private Map<String, Double> computeScaleFactorByExecutionNode( Map<String, Double> usedCpuByExecutionNode, Map<String, Double> availableCpuByExecutionNode )` | private | Calcola il fattore di riduzione per i nodi sovra-allocati. |

**Problematiche aperte**

- Ridimensiona la CPU aggregata per executionNodeId, ma non modifica la banda: il repair banda resta OpenIssue.

### `CpuAggregateRepairResult`

- File: `src/ga/operators/CpuAggregateRepairResult.java:17`
- Tipo: `class`
- Nome completo: `ga.operators.CpuAggregateRepairResult`

**Cosa fa, in parole semplici**

Esito del repair CPU aggregato. Oltre al cromosoma riparato, espone i nodi fisici ridimensionati e i task effettivamente coinvolti. Il RepairOperator può così limitare il secondo passaggio ai soli geni modificati dal ridimensionamento aggregato. Dice al repair quali nodi e task sono stati toccati dal ridimensionamento CPU.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Chromosome`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final Chromosome chromosome`
- `private final Set<String> scaledExecutionNodeIds`
- `private final Set<String> affectedTaskIds`
- `private final boolean changed`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private CpuAggregateRepairResult( Chromosome chromosome, Set<String> scaledExecutionNodeIds, Set<String> affectedTaskIds, boolean changed )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static CpuAggregateRepairResult unchanged(Chromosome chromosome)` | public | Metodo di supporto: realizza il passo `unchanged` dentro la responsabilita' della classe. |
| `public static CpuAggregateRepairResult changed( Chromosome chromosome, Set<String> scaledExecutionNodeIds, Set<String> affectedTaskIds )` | public | Metodo di supporto: realizza il passo `changed` dentro la responsabilita' della classe. |
| `public Chromosome getChromosome()` | public | Restituisce il valore di `Chromosome` senza modificarlo. |
| `public Set<String> getScaledExecutionNodeIds()` | public | Restituisce il valore di `ScaledExecutionNodeIds` senza modificarlo. |
| `public Set<String> getAffectedTaskIds()` | public | Restituisce il valore di `AffectedTaskIds` senza modificarlo. |
| `public boolean isChanged()` | public | Risponde con true/false alla domanda `is changed`. |
| `private static Set<String> immutableCopy(Set<String> source)` | private | Metodo di supporto: realizza il passo `immutable copy` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `CrossoverOperator`

- File: `src/ga/operators/CrossoverOperator.java:18`
- Tipo: `class`
- Nome completo: `ga.operators.CrossoverOperator`

**Cosa fa, in parole semplici**

Operatore di crossover. Combina due cromosomi tramite single-point crossover. I geni prima del punto di taglio arrivano dal primo genitore, quelli successivi dal secondo. Se i cromosomi hanno lunghezze diverse viene usata la lunghezza minima.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `Gene`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final Random random`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public CrossoverOperator(Random random)` | public | Costruisce l'operatore di crossover. @param random generatore casuale usato per scegliere il punto di taglio |
| `public Chromosome crossover(Chromosome parentA, Chromosome parentB)` | public | Applica single-point crossover tra due genitori. @param parentA primo cromosoma genitore @param parentB secondo cromosoma genitore @return figlio ottenuto combinando i due genitori |
| `public Chromosome copyChromosome(Chromosome source)` | public | Crea una copia superficiale di un cromosoma. La copia è superficiale perché `Gene` è immutabile. @param source cromosoma da copiare @return nuovo cromosoma con stessi geni e stessa fitness |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `ElitismOperator`

- File: `src/ga/operators/ElitismOperator.java:15`
- Tipo: `class`
- Nome completo: `ga.operators.ElitismOperator`

**Cosa fa, in parole semplici**

Operatore di elitismo. Copia nella generazione successiva i migliori cromosomi della popolazione corrente, preservando le soluzioni già trovate durante l'evoluzione.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Chromosome`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public List<Chromosome> selectElite(List<Chromosome> population, int elitismCount)` | public | Seleziona i migliori cromosomi della popolazione. @param population popolazione corrente già valutata @param elitismCount numero massimo di cromosomi da conservare @return copie dei cromosomi con fitness più bassa |
| `private Chromosome copyChromosome(Chromosome source)` | private | Crea una copia superficiale di un cromosoma. @param source cromosoma da copiare @return nuovo cromosoma con stessi geni e stessa fitness |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `MutationOperator`

- File: `src/ga/operators/MutationOperator.java:37`
- Tipo: `class`
- Nome completo: `ga.operators.MutationOperator`

**Cosa fa, in parole semplici**

Operatore di mutazione del MA-GA. La mutazione mantiene la componente casuale dell'algoritmo genetico, ma non agisce più solo con piccole variazioni locali. Per la quota di offloading `p_i` usa più modalità: piccola perturbazione locale; reset casuale; salto verso `p = 1`; salto verso una quota bilanciata tra ramo locale e ramo remoto. Inoltre, quando muta il candidato, sceglie solo candidati validi per il veicolo sorgente del task. Muta geni e restituisce anche i task realmente modificati.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `Gene`, `NodeCandidate`, `NodeType`, `OffloadingTimeModel`, `SystemSnapshot`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double CANDIDATE_MUTATION_PROBABILITY = 0.25`
- `private static final double REMOTE_CANDIDATE_PREFERENCE = 0.60`
- `private static final double BEST_REMOTE_CANDIDATE_PROBABILITY = 0.55`
- `private final Random random`
- `private final OffloadingRatioPolicy offloadingRatioPolicy`
- `private final ResourceAllocationPolicy resourceAllocationPolicy`
- `private final OffloadingTimeModel offloadingTimeModel`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public MutationOperator(Random random)` | public | Costruisce l'operatore di mutazione. |
| `public Chromosome mutate( Chromosome chromosome, SystemSnapshot snapshot, double mutationRate )` | public | Adapter compatibile con i chiamanti precedenti. @return cromosoma mutato |
| `public MutationResult mutateDetailed( Chromosome chromosome, SystemSnapshot snapshot, double mutationRate )` | public | Applica la mutazione e conserva gli identificativi dei task modificati. Ogni gene viene selezionato con probabilità `mutationRate`. Un task viene marcato dirty soltanto se la decisione risultante differisce realmente da quella precedente. Il tracking non cambia la probabilità o la logica della mutazione: espone soltanto informazione già disponibile per evitare repair ridondanti. |
| `private Gene mutateGene(Gene gene, SystemSnapshot snapshot)` | private | Muta un singolo gene. |
| `private NodeCandidate selectCandidateForMutation( TaskInstance task, List<NodeCandidate> validCandidates, VehicleSnapshot sourceVehicle )` | private | Sceglie un candidato valido per la mutazione. |
| `private double mutateOffloadingRatio( Gene gene, TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, boolean candidateChanged )` | private | Muta la quota di offloading `p_i`. |
| `private Gene createLocalGene( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | private | Crea un gene locale coerente. |
| `private NodeCandidate selectBestEstimatedRemoteCandidate( TaskInstance task, List<NodeCandidate> remoteCandidates, VehicleSnapshot sourceVehicle )` | private | Sceglie il candidato remoto con migliore stima euristica. |
| `private double estimateBestCompletion( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | private | Stima euristica del miglior completion ottenibile da un remoto. |
| `private double estimateLocalOnlyTime( TaskInstance task, VehicleSnapshot sourceVehicle )` | private | Stima `estimate local only time` senza modificare lo stato principale. |
| `private double estimateRemoteLinearTime( TaskInstance task, NodeCandidate candidate )` | private | Stima `estimate remote linear time` senza modificare lo stato principale. |
| `private List<NodeCandidate> findCandidatesForTask( TaskInstance task, SystemSnapshot snapshot )` | private | Trova i candidati validi per il veicolo sorgente del task. |
| `private List<NodeCandidate> findRemoteCandidates(List<NodeCandidate> candidates)` | private | Cerca `find remote candidates` nelle collezioni o nello stato corrente. |
| `private NodeCandidate findLocalCandidate(List<NodeCandidate> candidates)` | private | Cerca `find local candidate` nelle collezioni o nello stato corrente. |
| `private NodeCandidate findCandidate(SystemSnapshot snapshot, String candidateId)` | private | Cerca `find candidate` nelle collezioni o nello stato corrente. |
| `private VehicleSnapshot findVehicle(SystemSnapshot snapshot, String vehicleId)` | private | Cerca `find vehicle` nelle collezioni o nello stato corrente. |
| `private TaskInstance findTask(SystemSnapshot snapshot, String taskId)` | private | Cerca `find task` nelle collezioni o nello stato corrente. |
| `private boolean sameDecision(Gene first, Gene second)` | private | Confronta semanticamente due geni immutabili. |
| `private void validateRate(double value)` | private | Controlla la correttezza di `validate rate` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `MutationResult`

- File: `src/ga/operators/MutationResult.java:18`
- Tipo: `class`
- Nome completo: `ga.operators.MutationResult`

**Cosa fa, in parole semplici**

Esito della mutazione di un cromosoma. Oltre al cromosoma mutato conserva gli identificativi dei task per cui la decisione genetica è cambiata realmente. Questo permette al repair incrementale di rivalutare soltanto i geni dirty durante le generazioni interne dello stesso snapshot. Trasporta cromosoma mutato e dirty task ids verso il repair incrementale.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Chromosome`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final Chromosome chromosome`
- `private final Set<String> mutatedTaskIds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public MutationResult(Chromosome chromosome, Set<String> mutatedTaskIds)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public Chromosome getChromosome()` | public | Restituisce il valore di `Chromosome` senza modificarlo. |
| `public Set<String> getMutatedTaskIds()` | public | Restituisce il valore di `MutatedTaskIds` senza modificarlo. |
| `public boolean hasMutations()` | public | Risponde con true/false alla domanda `has mutations`. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `OffloadingRatioPolicy`

- File: `src/ga/operators/OffloadingRatioPolicy.java:47`
- Tipo: `class`
- Nome completo: `ga.operators.OffloadingRatioPolicy`

**Cosa fa, in parole semplici**

Policy centralizzata per generare e modificare la quota di offloading p_i. Questa classe non sceglie il candidato di esecuzione e non valuta la fitness. Serve solo a produrre valori di offloadingRatio più coerenti con il problema. Rispetto alla formalizzazione, questa classe opera solo sulla variabile decisionale p_i del gene: g_i = (p_i, f_i, b_i, n_i) Non introduce nuove variabili decisionali e non modifica la funzione di fitness. Le stime interne usano parametri già presenti nel modello: deadline del task; dimensione dei dati in input/output; cicli CPU richiesti dal task; CPU locale; CPU e banda massime del candidato; latenza base del candidato. Obiettivo: mantenere esplorazione casuale; rendere esplorabili i casi p = 0, p = 1 e partial offloading; evitare che inizializzazione e mutazione producano troppe quote formalmente valide ma temporalmente poco plausibili; ridurre il rischio di upload bottleneck quando il task è communication-heavy. Propone valori di `p_i` piu' sensati per inizializzazione e mutazione.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`, `NodeType`, `OffloadingTimeModel`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `public static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05`
- `public static final double LOCAL_RATIO = 0.0`
- `public static final double FULL_OFFLOADING_RATIO = 1.0`
- `private static final double SMALL_MUTATION_DELTA = 0.15`
- `private static final double UPLOAD_DOMINANCE_THRESHOLD = 0.45`
- `private static final double UPLOAD_HEAVY_INTERVAL_FRACTION = 0.65`
- `private static final double REMOTE_LOWER_BOUND_SAFETY_FACTOR = 1.15`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public double localRatio()` | public | Restituisce la quota locale. @return 0.0 |
| `public double fullRatio()` | public | Restituisce la quota di full offloading. @return 1.0 |
| `public double randomRemoteRatio(Random random)` | public | Genera una quota remota casuale. Usa ancora casualità pura, ma solo per candidati remoti. Questo metodo mantiene la componente esplorativa del GA. @param random generatore casuale @return quota in [MIN_REMOTE_OFFLOADING_RATIO, 1.0] |
| `public double balancedRemoteRatio( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | public | Calcola una quota remota bilanciata tra ramo locale e ramo remoto. La stima assume: local(p) = (1 - p) * A remote(p) = L + p * B dove A è il tempo locale puro, B è il costo remoto lineare per p = 1 e L è la latenza base. Il valore p bilanciato non è una soluzione ottima. È solo un punto plausibile da esplorare nella popolazione o nella mutazione. @param task task considerato @param candidate candidato remoto @param sourceVehicle veicolo sorgente del task @return quota remota in [MIN_REMOTE_OFFLOADING_RATIO, 1.0] |
| `public double deadlineAwareRatio( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, Random random )` | public | Genera una quota di offloading deadline-aware e upload-aware. La logica non sostituisce il GA con una scelta deterministica. Se esiste un intervallo plausibile di valori di p compatibili con la deadline, viene campionato un valore casuale dentro quell'intervallo. Se il task è upload-heavy, cioè se il tempo di upload domina il costo remoto stimato, il campionamento viene ristretto verso la parte bassa o intermedia dell'intervallo. Questo serve a ridurre casi in cui p è formalmente valido ma genera upload bottleneck. Se l'intervallo non esiste, la policy ricade sulla quota bilanciata con rumore. In questo modo il GA conserva esplorazione, ma evita di usare sistematicamente quote remote incoerenti con il vincolo temporale. @param task task considerato @param candidate candidato remoto @param sourceVehicle veicolo sorgente @param random generatore casuale @return quota remota in [MIN_REMOTE_OFFLOADING_RATIO, 1.0] |
| `public double mutateBySmallStep( double currentRatio, Random random )` | public | Applica una piccola mutazione locale a una quota remota esistente. Questa è la mutazione classica: resta vicina al valore corrente. @param currentRatio quota corrente @param random generatore casuale @return nuova quota remota valida |
| `public double mutateByRandomReset(Random random)` | public | Genera una mutazione random-reset. A differenza della piccola perturbazione, questa permette al GA di saltare in un'altra zona dello spazio delle quote. @param random generatore casuale @return quota remota casuale valida |
| `public double mutateToFullOffloading()` | public | Genera una mutazione verso full offloading. Serve a rendere p = 1 esplicitamente esplorabile. @return 1.0 |
| `public double mutateToBalancedRatio( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | public | Genera una mutazione verso la quota bilanciata. @param task task considerato @param candidate candidato remoto @param sourceVehicle veicolo sorgente @return quota bilanciata valida |
| `public double normalizeRemoteRatio(double ratio)` | public | Normalizza una quota remota. Se il valore non è valido o è troppo basso, viene portato alla quota minima remota. @param ratio quota proposta @return quota remota valida |
| `private double clampRemoteRatio(double ratio)` | private | Limita una quota remota nell'intervallo ammesso. |
| `private double estimateLocalOnlyTime( TaskInstance task, VehicleSnapshot sourceVehicle )` | private | Stima il tempo locale puro. |
| `private double estimateRemoteLinearTime( TaskInstance task, NodeCandidate candidate )` | private | Stima il costo remoto lineare per p = 1. Include upload, esecuzione remota e download. La latenza base viene trattata separatamente nel bilanciamento. |
| `private double safePositive(double value)` | private | Converte valori non validi o non positivi in zero. |
| `private double randomBetween( double min, double max, Random random )` | private | Genera un valore casuale nell'intervallo [min, max]. |
| `private double safeNonNegative(double value)` | private | Converte valori non validi in zero. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `PopulationInitializer`

- File: `src/ga/operators/PopulationInitializer.java:35`
- Tipo: `class`
- Nome completo: `ga.operators.PopulationInitializer`

**Cosa fa, in parole semplici**

Genera la popolazione iniziale del MA-GA. Nel modello source-aware, per ogni task vengono considerati solo i candidati validi per il veicolo sorgente del task. La popolazione iniziale non è più solo casuale. Vengono generati cromosomi con profili diversi: - RANDOM: esplorazione casuale classica; - LOCAL_BIASED: soluzione prevalentemente locale; - BALANCED_REMOTE: candidati remoti con quota p bilanciata; - FULL_REMOTE_TRIAL: candidati remoti con p = 1. La selezione finale resta affidata alla fitness. Questi profili servono solo a rendere lo spazio iniziale più ricco. Crea cromosomi iniziali con profili locali/remoti/random e passa tutto al repair.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `Gene`, `NodeCandidate`, `NodeType`, `OffloadingTimeModel`, `SystemSnapshot`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final Random random`
- `private final RepairOperator repairOperator`
- `private final OffloadingRatioPolicy offloadingRatioPolicy`
- `private final ResourceAllocationPolicy resourceAllocationPolicy`
- `private final OffloadingTimeModel offloadingTimeModel`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public PopulationInitializer( Random random, RepairOperator repairOperator )` | public | Costruisce l'inizializzatore. @param random generatore casuale condiviso dal GA @param repairOperator operatore di riparazione dei cromosomi |
| `public List<Chromosome> createInitialPopulation( SystemSnapshot snapshot, int populationSize )` | public | Crea la popolazione iniziale. La popolazione viene composta alternando profili diversi. Ogni cromosoma viene poi riparato, così restano validi: - candidati source-aware; - risorse minime; - CPU aggregata per executionNodeId. @param snapshot stato osservato del sistema @param populationSize numero di cromosomi da generare @return popolazione iniziale riparata |
| `private InitializationProfile selectProfile( int index, int populationSize )` | private | Seleziona il profilo di inizializzazione. Distribuzione più prudente: - circa 15% LOCAL_BIASED; - circa 35% BALANCED_REMOTE; - circa 10% FULL_REMOTE_TRIAL; - circa 40% RANDOM. Il full offloading resta esplorabile, ma non domina più la popolazione. |
| `private Chromosome createChromosome( SystemSnapshot snapshot, InitializationProfile profile )` | private | Crea un cromosoma secondo il profilo scelto. |
| `private Gene createGene( TaskInstance task, SystemSnapshot snapshot, InitializationProfile profile )` | private | Crea un gene secondo il profilo scelto. |
| `private Gene createLocalBiasedGene( TaskInstance task, List<NodeCandidate> validCandidates, VehicleSnapshot sourceVehicle )` | private | Crea un gene orientato al locale. Se esiste il candidato LOCAL, viene scelto. Altrimenti si ricade su un candidato casuale valido. |
| `private Gene createBalancedRemoteGene( TaskInstance task, List<NodeCandidate> validCandidates, VehicleSnapshot sourceVehicle )` | private | Crea un gene remoto con quota p bilanciata. Se non esistono candidati remoti, usa LOCAL. |
| `private Gene createFullRemoteTrialGene( TaskInstance task, List<NodeCandidate> validCandidates, VehicleSnapshot sourceVehicle )` | private | Crea un gene remoto con p = 1. Serve a rendere il full offloading esplicitamente presente nella popolazione iniziale. |
| `private Gene createRandomGene( TaskInstance task, List<NodeCandidate> validCandidates, VehicleSnapshot sourceVehicle )` | private | Crea un gene casuale. Questo mantiene la componente classica di esplorazione casuale dell'algoritmo genetico. |
| `private Gene createLocalGene( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | private | Crea un gene locale. La quota di offloading è sempre 0 e la banda assegnata è 0. |
| `private Gene createRemoteGene( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double offloadingRatio )` | private | Crea un gene remoto. CPU e banda non sono più generate solo in modo cieco. La ResourceAllocationPolicy mantiene una componente casuale, ma prova a rendere le risorse coerenti con: - quota di offloading; - deadline; - capacità del candidato; - dimensione del task. |
| `private NodeCandidate selectCandidateWithBestEstimatedCompletion( TaskInstance task, List<NodeCandidate> remoteCandidates, VehicleSnapshot sourceVehicle )` | private | Sceglie il candidato remoto con migliore stima ottimistica. Non è una valutazione di fitness. Serve solo per evitare che i profili BALANCED e FULL partano da candidati remoti palesemente peggiori. |
| `private double estimateBestCompletion( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | private | Stima il miglior completion teorico dato un candidato remoto. Usa la stessa idea della quota bilanciata: local(p) = (1 - p) * A remote(p) = L + p * B dove A è il locale puro, B è il remoto lineare e L è la latenza base. |
| `private double estimateLocalOnlyTime( TaskInstance task, VehicleSnapshot sourceVehicle )` | private | Stima il tempo locale puro. |
| `private double estimateRemoteLinearTime( TaskInstance task, NodeCandidate candidate )` | private | Stima upload + remote execution + download per p = 1. |
| `private List<NodeCandidate> findCandidatesForTask( TaskInstance task, SystemSnapshot snapshot )` | private | Trova i candidati validi per il task. |
| `private NodeCandidate findLocalCandidate( List<NodeCandidate> candidates )` | private | Restituisce il candidato LOCAL, se presente. |
| `private List<NodeCandidate> findRemoteCandidates( List<NodeCandidate> candidates )` | private | Restituisce solo i candidati remoti. |
| `private VehicleSnapshot findVehicle( SystemSnapshot snapshot, String vehicleId )` | private | Cerca il veicolo sorgente nello snapshot. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `InitializationProfile` (tipo interno di `PopulationInitializer`)

- File: `src/ga/operators/PopulationInitializer.java:604`
- Tipo: `enum`
- Nome completo: `ga.operators.PopulationInitializer.InitializationProfile`

**Cosa fa, in parole semplici**

Profili di inizializzazione della popolazione.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `Gene`, `NodeCandidate`, `NodeType`, `OffloadingTimeModel`, `SystemSnapshot`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Valori enum principali:
`LOCAL_BIASED`, `BALANCED_REMOTE`, `FULL_REMOTE_TRIAL`, `RANDOM`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `RepairOperator`

- File: `src/ga/operators/RepairOperator.java:47`
- Tipo: `class`
- Nome completo: `ga.operators.RepairOperator`

**Cosa fa, in parole semplici**

Ripara cromosomi e geni incoerenti. Nel modello source-aware, un gene è valido solo se il candidato scelto è compatibile con il veicolo sorgente del task. La riparazione avviene su quattro livelli: livello gene: corregge candidato, quota di offloading, CPU e banda; livello mobilità: evita candidati remoti con copertura insufficiente; livello deadline: prova una correzione limitata e aderente al modello; livello cromosoma: ridimensiona la CPU aggregata sui nodi fisici remoti. Gli indici e il catalogo lazy sono ottimizzazioni implementative. Non introducono nuove variabili decisionali e non sostituiscono selezione, crossover, mutazione o fitness del Genetic Algorithm. Corregge cromosomi su candidati, mobilita', deadline e CPU aggregata, con fallback best-effort quando necessario.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `CoverageEstimator`, `DeadlineConstraintEvaluator`, `DeadlineEvaluation`, `DeadlineRepairCatalog`, `DeadlineRepairProfile`, `Gene`, `MobilityConfig`, `NodeCandidate`, `NodeType`, `OffloadingTimeModel`, `SnapshotRepairContext`, `SystemSnapshot`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05`
- `private static final double MIN_RESOURCE_FRACTION = 0.05`
- `private static final int MAX_REPAIR_PASSES = 2`
- `private final CpuAggregateRepairOperator cpuAggregateRepairOperator`
- `private final CoverageEstimator coverageEstimator`
- `private final OffloadingTimeModel offloadingTimeModel`
- `private final OffloadingRatioPolicy offloadingRatioPolicy`
- `private final DeadlineConstraintEvaluator deadlineConstraintEvaluator`
- `private SnapshotRepairContext cachedContext`
- `private DeadlineRepairCatalog cachedDeadlineRepairCatalog`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public RepairOperator()` | public | Costruttore compatibile con il codice precedente. |
| `public RepairOperator(MobilityConfig mobilityConfig)` | public | Costruisce il repair operator con configurazione di mobilità esplicita. |
| `public Chromosome repairChromosome(Chromosome chromosome, SystemSnapshot snapshot)` | public | Ripara integralmente un cromosoma rispetto allo snapshot corrente. Questo percorso resta obbligatorio per popolazioni appena create, cromosomi provenienti da una finestra precedente e chiamanti esterni che non dispongono dell'elenco dei geni modificati. |
| `public Chromosome repairChromosomeIncremental( Chromosome chromosome, SystemSnapshot snapshot, Set<String> dirtyTaskIds )` | public | Ripara incrementalmente un figlio prodotto durante l'evoluzione nello stesso snapshot. I genitori della generazione corrente sono già stati riparati. Un gene ereditato senza modifiche resta quindi individualmente valido. Il metodo rivaluta soltanto i task indicati come dirty, ma esegue sempre il repair CPU aggregato sull'intero cromosoma: il crossover può infatti combinare geni validi singolarmente e creare una nuova contesa collettiva. Se la struttura del cromosoma non rispetta l'insieme dei task dello snapshot, il metodo effettua automaticamente un repair completo. @param chromosome figlio da riparare @param snapshot snapshot corrente @param dirtyTaskIds task realmente modificati dalla mutazione @return cromosoma riparato |
| `private Chromosome repairChromosomeInternal( Chromosome chromosome, SystemSnapshot snapshot, Set<String> initialTargetedTaskIds )` | private | Implementazione condivisa dai percorsi completo e incrementale. `initialTargetedTaskIds == null` indica il repair completo del primo passaggio. Un insieme vuoto indica invece che nessun gene necessita di repair individuale prima del controllo CPU aggregato globale. |
| `private Chromosome repairGenes( Chromosome chromosome, SystemSnapshot snapshot, SnapshotRepairContext context, DeadlineRepairCatalog catalog, Set<String> targetedTaskIds )` | private | Ripara tutti i geni nel primo passaggio e soltanto i geni indicati nei passaggi successivi. I geni non coinvolti dal ridimensionamento CPU aggregato vengono conservati senza una rivalutazione ridondante. |
| `public Gene repairGene(Gene gene, TaskInstance task, SystemSnapshot snapshot)` | public | Adapter compatibile con i chiamanti precedenti. |
| `private Gene repairGene( Gene gene, TaskInstance task, SystemSnapshot snapshot, SnapshotRepairContext context, DeadlineRepairCatalog catalog )` | private | Ripara un gene usando indici e catalogo dello snapshot corrente. |
| `private Gene repairDeadlineIfNeeded( Gene currentGene, TaskInstance task, SnapshotRepairContext context, DeadlineRepairCatalog catalog, VehicleSnapshot sourceVehicle, NodeCandidate localCandidate )` | private | Applica la policy deadline-aware soltanto quando il gene corrente non rispetta la deadline. prova quote alternative mantenendo il nodo remoto corrente; prova candidati remoti alternativi; prova l'esecuzione locale; se nessuna alternativa è ammissibile, sceglie il best-effort. |
| `private Gene findDeadlineFeasibleRemoteAlternative( TaskInstance task, VehicleSnapshot sourceVehicle, String excludedCandidateId, Gene currentGene, SnapshotRepairContext context, DeadlineRepairCatalog catalog )` | private | Cerca `find deadline feasible remote alternative` nelle collezioni o nello stato corrente. |
| `private Gene findDeadlineFeasibleGeneForCandidate( TaskInstance task, VehicleSnapshot sourceVehicle, NodeCandidate candidate, double preferredRatio, double preferredCpu, double preferredBandwidth, SnapshotRepairContext context, DeadlineRepairCatalog catalog )` | private | Cerca `find deadline feasible gene for candidate` nelle collezioni o nello stato corrente. |
| `private Gene selectDegradedBestEffortGene( TaskInstance task, VehicleSnapshot sourceVehicle, Gene localGene, Gene currentGene, SnapshotRepairContext context, DeadlineRepairCatalog catalog )` | private | Applica la policy deadline-aware soltanto quando il gene corrente non rispetta la deadline. prova quote alternative mantenendo il nodo remoto corrente; prova candidati remoti alternativi; prova l'esecuzione locale; se nessuna alternativa è ammissibile, sceglie il best-effort. private Gene repairDeadlineIfNeeded( Gene currentGene, TaskInstance task, SnapshotRepairContext context, DeadlineRepairCatalog catalog, VehicleSnapshot sourceVehicle, NodeCandidate localCandidate ) { DeadlineEvaluation currentEvaluation = deadlineConstraintEvaluator.evaluate( currentGene, task, context ); if (currentEvaluation.isDeadlineRespected()) { return currentGene; } NodeCandidate currentCandidate = context.getCandidateById( currentGene.getSelectedCandidateId() ); if (currentCandidate != null && currentCandidate.getType() != NodeType.LOCAL) { Gene repairedOnCurrentCandidate = findDeadlineFeasibleGeneForCandidate( task, sourceVehicle, currentCandidate, currentGene.getOffloadingRatio(), currentGene.getAllocatedCpu(), currentGene.getAllocatedBandwidth(), context, catalog ); if (repairedOnCurrentCandidate != null) { return repairedOnCurrentCandidate; } } Gene repairedOnAlternativeRemote = findDeadlineFeasibleRemoteAlternative( task, sourceVehicle, currentCandidate == null ? null : currentCandidate.getCandidateId(), currentGene, context, catalog ); if (repairedOnAlternativeRemote != null) { return repairedOnAlternativeRemote; } Gene localGene = createLocalGene(task, localCandidate, sourceVehicle); if (deadlineConstraintEvaluator.evaluate(localGene, task, context).isAdmissible()) { return localGene; } return selectDegradedBestEffortGene( task, sourceVehicle, localGene, currentGene, context, catalog ); } private Gene findDeadlineFeasibleRemoteAlternative( TaskInstance task, VehicleSnapshot sourceVehicle, String excludedCandidateId, Gene currentGene, SnapshotRepairContext context, DeadlineRepairCatalog catalog ) { Gene bestGene = null; DeadlineEvaluation bestEvaluation = null; for (NodeCandidate candidate : context.getCandidatesForTask(task)) { if (candidate.getType() == NodeType.LOCAL) { continue; } if (candidate.getCandidateId().equals(excludedCandidateId)) { continue; } Gene candidateGene = findDeadlineFeasibleGeneForCandidate( task, sourceVehicle, candidate, currentGene.getOffloadingRatio(), currentGene.getAllocatedCpu(), currentGene.getAllocatedBandwidth(), context, catalog ); if (candidateGene == null) { continue; } DeadlineEvaluation evaluation = deadlineConstraintEvaluator.evaluate( candidateGene, task, context ); if (bestGene == null \|\| evaluation.getCompletionTimeSeconds() < bestEvaluation.getCompletionTimeSeconds()) { bestGene = candidateGene; bestEvaluation = evaluation; } } return bestGene; } private Gene findDeadlineFeasibleGeneForCandidate( TaskInstance task, VehicleSnapshot sourceVehicle, NodeCandidate candidate, double preferredRatio, double preferredCpu, double preferredBandwidth, SnapshotRepairContext context, DeadlineRepairCatalog catalog ) { Gene bestGene = null; double bestPressure = Double.POSITIVE_INFINITY; double bestCompletion = Double.POSITIVE_INFINITY; for (double ratio : catalog.buildRatioCandidates( task, candidate, sourceVehicle, preferredRatio )) { Gene preservedResourcesGene = new Gene( task.getTaskId(), candidate.getCandidateId(), ratio, clampResource(preferredCpu, candidate.getAvailableCpu()), clampResource( preferredBandwidth, candidate.getAvailableBandwidth() ) ); Gene feasibleGene; DeadlineEvaluation evaluation = deadlineConstraintEvaluator.evaluate( preservedResourcesGene, task, context ); if (evaluation.isAdmissible()) { feasibleGene = preservedResourcesGene; } else { feasibleGene = catalog .getProfile(task, candidate, sourceVehicle, ratio) .getMinimalFeasibleGene(); if (feasibleGene != null) { evaluation = deadlineConstraintEvaluator.evaluate( feasibleGene, task, context ); } } if (feasibleGene == null \|\| !evaluation.isAdmissible()) { continue; } double pressure = computeResourcePressure(feasibleGene, candidate); if (pressure < bestPressure - EPSILON \|\| (Math.abs(pressure - bestPressure) <= EPSILON && evaluation.getCompletionTimeSeconds() < bestCompletion)) { bestGene = feasibleGene; bestPressure = pressure; bestCompletion = evaluation.getCompletionTimeSeconds(); } } return bestGene; } /* Quando nessuna configurazione valutata dal repair riesce a rispettare la deadline, il task entra in modalità DEGRADED_BEST_EFFORT. La scelta confronta il fallback LOCAL e i candidati remoti sostenibili rispetto alla mobilità, quindi conserva l'alternativa con lo sforamento previsto più basso. Non viene usata una priorità esplicita e il CLOUD non viene privilegiato automaticamente: può essere selezionato soltanto se riduce realmente la lateness stimata. Questa sezione non certifica l'insoddisfacibilità matematica globale del task: il repair esplora intenzionalmente un insieme limitato di quote e risorse per non trasformarsi in un secondo ottimizzatore. |
| `private boolean isBetterBestEffortChoice( Gene candidateGene, DeadlineEvaluation candidateEvaluation, Gene currentBestGene, DeadlineEvaluation currentBestEvaluation, SnapshotRepairContext context )` | private | Risponde con true/false alla domanda `is better best effort choice`. |
| `private double computeResourcePressure(Gene gene, NodeCandidate candidate)` | private | Calcola `compute resource pressure` a partire dai dati ricevuti. |
| `private Gene createFallbackGene( TaskInstance task, SnapshotRepairContext context )` | private | Crea un gene locale di fallback quando il cromosoma non contiene il task. |
| `private NodeCandidate findCoverageSustainableRemoteCandidate( TaskInstance task, VehicleSnapshot sourceVehicle, double offloadingRatio, double allocatedCpu, double allocatedBandwidth, String excludedCandidateId, SnapshotRepairContext context )` | private | Cerca un candidato remoto alternativo che soddisfi la copertura. |
| `private boolean isCoverageSufficient( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double offloadingRatio, double allocatedCpu, double allocatedBandwidth, SnapshotRepairContext context )` | private | Risponde con true/false alla domanda `is coverage sufficient`. |
| `private double estimateCoverageTimeSeconds( TaskInstance task, NodeCandidate candidate, SnapshotRepairContext context )` | private | Stima `estimate coverage time seconds` senza modificare lo stato principale. |
| `private double estimateCompletionTimeSeconds( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double offloadingRatio, double allocatedCpu, double allocatedBandwidth )` | private | Stima il completion time usando la stessa struttura della fitness. |
| `private Gene createLocalGene( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | private | Crea `create local gene` come nuovo oggetto o nuova struttura dati. |
| `private Map<String, Gene> indexGenes(Chromosome chromosome)` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private boolean hasCompleteTaskCoverage( Chromosome chromosome, SnapshotRepairContext context )` | private | Verifica che il figlio contenga esattamente un gene per task attivo. |
| `private Set<String> normalizeDirtyTaskIds( Set<String> dirtyTaskIds, SnapshotRepairContext context )` | private | Mantiene solo identificativi dirty appartenenti allo snapshot corrente. |
| `private double clampResource(double value, double maxAvailable)` | private | Limita una risorsa al range ammesso dal singolo candidato. |
| `private SnapshotRepairContext contextFor(SystemSnapshot snapshot)` | private | Metodo di supporto: realizza il passo `context for` dentro la responsabilita' della classe. |
| `private DeadlineRepairCatalog catalogFor(SnapshotRepairContext context)` | private | Metodo di supporto: realizza il passo `catalog for` dentro la responsabilita' della classe. |
| `private double safeDivide(double numerator, double denominator)` | private | Esegue un'operazione protetta per evitare valori non finiti o divisioni non valide. |
| `private boolean isStrictlyPositive(double value)` | private | Risponde con true/false alla domanda `is strictly positive`. |
| `private double clamp(double value, double min, double max)` | private | Limita un valore dentro un intervallo ammesso. |

**Problematiche aperte**

- Il best-effort degradato non e' una prova di infeasibilita' globale: indica solo che il repair limitato non ha trovato una scelta ammissibile.
- Il repair aggregato resta sulla CPU; non esiste ancora un repair aggregato equivalente per banda globale o gateway.

### `ResourceAllocationDecision`

- File: `src/ga/operators/ResourceAllocationDecision.java:9`
- Tipo: `class`
- Nome completo: `ga.operators.ResourceAllocationDecision`

**Cosa fa, in parole semplici**

Risultato di una scelta di allocazione delle risorse per un gene. La classe è immutabile e contiene CPU, banda e modalità diagnostica dell'allocazione.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double allocatedCpu`
- `private final double allocatedBandwidth`
- `private final Mode mode`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public ResourceAllocationDecision( double allocatedCpu, double allocatedBandwidth, Mode mode )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public double getAllocatedCpu()` | public | Restituisce il valore di `AllocatedCpu` senza modificarlo. |
| `public double getAllocatedBandwidth()` | public | Restituisce il valore di `AllocatedBandwidth` senza modificarlo. |
| `public Mode getMode()` | public | Restituisce il valore di `Mode` senza modificarlo. |
| `private static double validateFinite(String fieldName, double value)` | private | Controlla la correttezza di `validate finite` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `Mode` (tipo interno di `ResourceAllocationDecision`)

- File: `src/ga/operators/ResourceAllocationDecision.java:11`
- Tipo: `enum`
- Nome completo: `ga.operators.ResourceAllocationDecision.Mode`

**Cosa fa, in parole semplici**

Enum: rappresenta un insieme chiuso di valori usati per rendere esplicite le scelte del modello.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`LOCAL`, `RANDOM`, `DEADLINE_AWARE`, `BORDERLINE`, `MODERATE`, `AGGRESSIVE`, `SMALL_STEP`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `ResourceAllocationPolicy`

- File: `src/ga/operators/ResourceAllocationPolicy.java:39`
- Tipo: `class`
- Nome completo: `ga.operators.ResourceAllocationPolicy`

**Cosa fa, in parole semplici**

Policy centralizzata per generare e mutare CPU e banda assegnate a un gene. Questa classe non sostituisce il Genetic Algorithm. Produce allocazioni iniziali e mutazioni plausibili, lasciando alla fitness la responsabilità di premiare o scartare le soluzioni. Il suo compito è evitare combinazioni palesemente incoerenti tra: p_i quota di offloading f_i CPU assegnata b_i banda assegnata deadline vincolo temporale del task candidate capacity capacità massima del candidato La policy mantiene la componente genetica: una parte delle allocazioni resta casuale; le allocazioni deadline-aware sono perturbate con rumore; la mutazione conserva small-step e random reset; la fitness resta responsabile della selezione finale. Propone `f_i` e `b_i` coerenti con quota, deadline e capacita' del candidato.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Gene`, `NodeCandidate`, `NodeType`, `OffloadingTimeModel`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private static final double MIN_RESOURCE_FRACTION = 0.05`
- `private static final double DEADLINE_TOLERANCE = 1.10`
- `private static final double DEADLINE_NOISE_MIN = 0.90`
- `private static final double DEADLINE_NOISE_MAX = 1.20`
- `private static final double SMALL_STEP_MIN = 0.80`
- `private static final double SMALL_STEP_MAX = 1.25`
- `private static final double MODERATE_MIN_FRACTION = 0.08`
- `private static final double MODERATE_MAX_FRACTION = 0.45`
- `private static final double BORDERLINE_MIN_FRACTION = 0.35`
- `private static final double BORDERLINE_MAX_FRACTION = 0.70`
- `private static final double AGGRESSIVE_MIN_FRACTION = 0.60`
- `private static final double AGGRESSIVE_MAX_FRACTION = 0.90`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public ResourceAllocationDecision allocateInitial( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double offloadingRatio, Random random )` | public | Crea una allocazione iniziale. La scelta è guidata ma non deterministica. La classificazione di fattibilità impedisce alla policy di saturare risorse su scelte remote che nemmeno in condizioni ottimistiche possono rispettare la deadline. |
| `public ResourceAllocationDecision mutate( Gene currentGene, TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double offloadingRatio, boolean candidateChanged, Random random )` | public | Muta CPU e banda. Se il candidato cambia, viene generata una nuova allocazione iniziale. Se il candidato resta lo stesso, si alternano small-step, deadline-aware, random reset e allocazioni moderate. |
| `private ResourceAllocationDecision allocateForFeasibleChoice( TaskInstance task, NodeCandidate candidate, double offloadingRatio, Random random, double roll )` | private | Metodo di supporto: realizza il passo `allocate for feasible choice` dentro la responsabilita' della classe. |
| `private ResourceAllocationDecision allocateForBorderlineChoice( NodeCandidate candidate, double offloadingRatio, Random random, double roll )` | private | Metodo di supporto: realizza il passo `allocate for borderline choice` dentro la responsabilita' della classe. |
| `private ResourceAllocationDecision allocateForInfeasibleChoice( NodeCandidate candidate, double offloadingRatio, Random random, double roll )` | private | Metodo di supporto: realizza il passo `allocate for infeasible choice` dentro la responsabilita' della classe. |
| `private ResourceAllocationDecision mutateFeasibleChoice( Gene currentGene, TaskInstance task, NodeCandidate candidate, double offloadingRatio, Random random, double roll )` | private | Applica una mutazione genetica collegata a `mutate feasible choice`. |
| `private ResourceAllocationDecision mutateBorderlineChoice( Gene currentGene, NodeCandidate candidate, double offloadingRatio, Random random, double roll )` | private | Applica una mutazione genetica collegata a `mutate borderline choice`. |
| `private ResourceAllocationDecision mutateInfeasibleChoice( Gene currentGene, NodeCandidate candidate, double offloadingRatio, Random random, double roll )` | private | Applica una mutazione genetica collegata a `mutate infeasible choice`. |
| `private Feasibility classifyFeasibility( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double offloadingRatio )` | private | Classifica se una scelta remota è plausibile rispetto alla deadline. La stima è ottimistica: usa max CPU e max bandwidth del candidato. Se fallisce anche così, assegnare risorse aggressive non risolve il problema e rischia solo di saturare il sistema. |
| `private ResourceAllocationDecision localAllocation( NodeCandidate candidate, VehicleSnapshot sourceVehicle )` | private | Metodo di supporto: realizza il passo `local allocation` dentro la responsabilita' della classe. |
| `private ResourceAllocationDecision deadlineAwareAllocation( TaskInstance task, NodeCandidate candidate, double offloadingRatio, Random random )` | private | Allocazione deadline-aware per scelte effettivamente fattibili. |
| `private ResourceAllocationDecision borderlineRemoteAllocation( NodeCandidate candidate, double offloadingRatio, Random random )` | private | Metodo di supporto: realizza il passo `borderline remote allocation` dentro la responsabilita' della classe. |
| `private ResourceAllocationDecision moderateRemoteAllocation( NodeCandidate candidate, double offloadingRatio, Random random )` | private | Metodo di supporto: realizza il passo `moderate remote allocation` dentro la responsabilita' della classe. |
| `private ResourceAllocationDecision aggressiveRemoteAllocation( NodeCandidate candidate, double offloadingRatio, Random random )` | private | Metodo di supporto: realizza il passo `aggressive remote allocation` dentro la responsabilita' della classe. |
| `private ResourceAllocationDecision randomRemoteAllocation( NodeCandidate candidate, Random random )` | private | Metodo di supporto: realizza il passo `random remote allocation` dentro la responsabilita' della classe. |
| `private ResourceAllocationDecision smallStepMutation( Gene gene, NodeCandidate candidate, Random random )` | private | Metodo di supporto: realizza il passo `small step mutation` dentro la responsabilita' della classe. |
| `private ResourceAllocationDecision fractionRangeAllocation( NodeCandidate candidate, double minFraction, double maxFraction, Random random, ResourceAllocationDecision.Mode mode )` | private | Metodo di supporto: realizza il passo `fraction range allocation` dentro la responsabilita' della classe. |
| `private double estimateLocalBranchTime( TaskInstance task, VehicleSnapshot sourceVehicle, double offloadingRatio )` | private | Stima `estimate local branch time` senza modificare lo stato principale. |
| `private double estimateRemoteBranchLowerBound( TaskInstance task, NodeCandidate candidate, double offloadingRatio )` | private | Stima `estimate remote branch lower bound` senza modificare lo stato principale. |
| `private double mutateResourceBySmallStep( double currentValue, double maxAvailable, Random random )` | private | Applica una mutazione genetica collegata a `mutate resource by small step`. |
| `private double randomResource( double maxAvailable, Random random )` | private | Metodo di supporto: realizza il passo `random resource` dentro la responsabilita' della classe. |
| `private double clampResource( double value, double maxAvailable )` | private | Limita un valore dentro un intervallo ammesso. |
| `private double normalizeRemoteRatio(double value)` | private | Metodo di supporto: realizza il passo `normalize remote ratio` dentro la responsabilita' della classe. |
| `private double randomFactor( Random random, double min, double max )` | private | Metodo di supporto: realizza il passo `random factor` dentro la responsabilita' della classe. |
| `private double randomBetween( double min, double max, Random random )` | private | Metodo di supporto: realizza il passo `random between` dentro la responsabilita' della classe. |
| `private double safeNonNegative(double value)` | private | Esegue un'operazione protetta per evitare valori non finiti o divisioni non valide. |
| `private double clamp( double value, double min, double max )` | private | Limita un valore dentro un intervallo ammesso. |
| `private void validateInputs( TaskInstance task, NodeCandidate candidate, Random random )` | private | Controlla la correttezza di `validate inputs` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

- Riduce molte scelte deboli, ma resta euristica: non garantisce da sola la deadline-feasibility finale.
- Lavora sulle capacita' del candidato/link, non su un budget globale condiviso di banda.

### `Feasibility` (tipo interno di `ResourceAllocationPolicy`)

- File: `src/ga/operators/ResourceAllocationPolicy.java:44`
- Tipo: `enum`
- Nome completo: `ga.operators.ResourceAllocationPolicy.Feasibility`

**Cosa fa, in parole semplici**

Enum: rappresenta un insieme chiuso di valori usati per rendere esplicite le scelte del modello.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Gene`, `NodeCandidate`, `NodeType`, `OffloadingTimeModel`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Valori enum principali:
`FEASIBLE`, `BORDERLINE`, `INFEASIBLE_LOCAL_BRANCH`, `INFEASIBLE_REMOTE_BRANCH`, `INFEASIBLE_BOTH_BRANCHES`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SelectionOperator`

- File: `src/ga/operators/SelectionOperator.java:16`
- Tipo: `class`
- Nome completo: `ga.operators.SelectionOperator`

**Cosa fa, in parole semplici**

Operatore di selezione dei genitori. Implementa una tournament selection per un problema di minimizzazione: a ogni selezione vengono estratti `tournamentSize` cromosomi e viene restituito quello con fitness più bassa.

**Relazione con la formalizzazione**

Agisce sul processo evolutivo: crea, modifica o ripara cromosomi mantenendo la forma delle variabili decisionali.

**Con chi comunica**

Comunica direttamente con: `Chromosome`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final Random random`
- `private final int tournamentSize`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public SelectionOperator(Random random, int tournamentSize)` | public | Costruisce un operatore di selezione. @param random generatore casuale usato per campionare i partecipanti @param tournamentSize numero di cromosomi estratti per torneo |
| `public Chromosome select(List<Chromosome> population)` | public | Seleziona un cromosoma dalla popolazione tramite torneo. @param population popolazione corrente, già valutata @return cromosoma con fitness più bassa tra i candidati estratti |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `io.reporting`

Stampa report leggibili per capire cosa ha fatto il GA e dove nascono costi o violazioni.

### `AdaptiveWindowDiagnosticPrinter`

- File: `src/io/reporting/AdaptiveWindowDiagnosticPrinter.java:19`
- Tipo: `class`
- Nome completo: `io.reporting.AdaptiveWindowDiagnosticPrinter`

**Cosa fa, in parole semplici**

Printer compatto per controllare la finestra adattiva. Oltre ai bounds, mostra se il runtime osservato del GA è compatibile con la durata della finestra corrente e con quella successiva. L'avviso non modifica il comportamento del manager: rende visibile una condizione che dovrà essere discussa prima di introdurre budget adattivi o asincronia.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `AdaptiveWindowDecision`, `TemporalStepResult`, `TemporalWindowBounds`, `TemporalWindowResult`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON_SECONDS = 1.0E-6`
- `private final PrintStream out`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public AdaptiveWindowDiagnosticPrinter()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public AdaptiveWindowDiagnosticPrinter(PrintStream out)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public void print(TemporalWindowResult result)` | public | Stampa una sezione diagnostica o un report leggibile. |
| `private String runtimeBudgetStatus( AdaptiveWindowDecision decision, TemporalWindowBounds bounds )` | private | Metodo di supporto: realizza il passo `runtime budget status` dentro la responsabilita' della classe. |
| `private String formatSeconds(double value)` | private | Metodo di supporto: realizza il passo `format seconds` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `AdaptiveWindowReportPrinter`

- File: `src/io/reporting/AdaptiveWindowReportPrinter.java:18`
- Tipo: `class`
- Nome completo: `io.reporting.AdaptiveWindowReportPrinter`

**Cosa fa, in parole semplici**

Punto unico di composizione del report temporale della finestra adattiva. Il main non conosce più i singoli printer specialistici. Questo oggetto mantiene insieme il report diagnostico generale, i bounds adattivi, il timing, il riuso della popolazione, la sorgente dati e il prefilter.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `FilteringSystemStateSource`, `MaGaConfig`, `TemporalRuntimeProfile`, `TemporalWindowResult`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final MaGaConfig config`
- `private final PrintStream out`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public AdaptiveWindowReportPrinter(MaGaConfig config)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public AdaptiveWindowReportPrinter(MaGaConfig config, PrintStream out)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public void print( String requestedSourceMode, String snapshotFolder, TemporalWindowResult result, FilteringSystemStateSource filteredSource )` | public | Mantiene compatibilità con eventuali chiamanti precedenti. Il vecchio overload non esponeva il profilo temporale e viene quindi interpretato come replay astratto configurato. |
| `public void print( String requestedSourceMode, TemporalRuntimeProfile runtimeProfile, String snapshotFolder, TemporalWindowResult result, FilteringSystemStateSource filteredSource )` | public | Stampa una sezione diagnostica o un report leggibile. |
| `private void printExecutionMetadata( String requestedSourceMode, TemporalRuntimeProfile runtimeProfile, String snapshotFolder )` | private | Stampa una sezione diagnostica o un report leggibile. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `CandidateFilteringPrinter`

- File: `src/io/reporting/CandidateFilteringPrinter.java:13`
- Tipo: `class`
- Nome completo: `io.reporting.CandidateFilteringPrinter`

**Cosa fa, in parole semplici**

Printer sintetico per verificare l'effetto del CandidatePrefilter.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `CandidateFilteringResult`, `CandidateRejectionReason`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final PrintStream out`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public CandidateFilteringPrinter()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public CandidateFilteringPrinter(PrintStream out)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public void print(List<CandidateFilteringResult> results)` | public | Stampa una sezione diagnostica o un report leggibile. |
| `private String formatReasons( Map<CandidateRejectionReason, Integer> reasonMap )` | private | Metodo di supporto: realizza il passo `format reasons` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `DeadlineBestEffortDiagnosticPrinter`

- File: `src/io/reporting/DeadlineBestEffortDiagnosticPrinter.java:21`
- Tipo: `class`
- Nome completo: `io.reporting.DeadlineBestEffortDiagnosticPrinter`

**Cosa fa, in parole semplici**

Report dedicato alle decisioni finali che restano fuori deadline dopo il repair. Una violazione residua viene indicata come `DEGRADED_BEST_EFFORT`: il repair ha confrontato un insieme limitato di alternative coerenti e ha mantenuto quella con lo sforamento stimato più basso. La marcatura non certifica l'insoddisfacibilità matematica globale del task. Evidenzia i task finiti in scelta degradata best-effort.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `EvaluationBreakdown`, `GeneEvaluationBreakdown`, `TemporalStepResult`, `TemporalWindowResult`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final int DEFAULT_TOP_LIMIT = 10`
- `private final PrintStream out`
- `private final int topLimit`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public DeadlineBestEffortDiagnosticPrinter()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public DeadlineBestEffortDiagnosticPrinter(PrintStream out)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public DeadlineBestEffortDiagnosticPrinter(PrintStream out, int topLimit)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public void print(TemporalWindowResult result)` | public | Stampa una sezione diagnostica o un report leggibile. |
| `private void printHeader()` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printInterpretation()` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printWindowSummary(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printTopResidualViolations(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private List<GeneEvaluationBreakdown> degradedGenes(EvaluationBreakdown evaluation)` | private | Metodo di supporto: realizza il passo `degraded genes` dentro la responsabilita' della classe. |
| `private double maxLateness(List<GeneEvaluationBreakdown> degraded)` | private | Metodo di supporto: realizza il passo `max lateness` dentro la responsabilita' della classe. |
| `private double averageLateness(List<GeneEvaluationBreakdown> degraded)` | private | Metodo di supporto: realizza il passo `average lateness` dentro la responsabilita' della classe. |
| `private double latenessSeconds(GeneEvaluationBreakdown gene)` | private | Metodo di supporto: realizza il passo `lateness seconds` dentro la responsabilita' della classe. |
| `private void printSection(String title)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private String format(double value)` | private | Metodo di supporto: realizza il passo `format` dentro la responsabilita' della classe. |
| `private String formatSeconds(double value)` | private | Metodo di supporto: realizza il passo `format seconds` dentro la responsabilita' della classe. |

**Problematiche aperte**

- Il report segnala fallback degradati, ma non sostituisce un'analisi matematica di infeasibilita'.

### `DeepTemporalWindowDiagnosticPrinter`

- File: `src/io/reporting/DeepTemporalWindowDiagnosticPrinter.java:44`
- Tipo: `class`
- Nome completo: `io.reporting.DeepTemporalWindowDiagnosticPrinter`

**Cosa fa, in parole semplici**

Printer diagnostico mirato per stress test temporali MA-GA. Questa versione non stampa ogni riga disponibile del sistema. Si concentra sul problema attuale emerso dai report: - deadline non rispettate; - cause delle deadline violate; - uso troppo conservativo dell'offloading; - candidati remoti senza copertura sufficiente; - pressione su CPU e banda senza entrare nel dettaglio completo di ogni risorsa; - andamento del GA sufficiente a capire se sta ancora migliorando. Non modifica fitness, GA, repair o temporal manager.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `DeadlineViolationAnalyzer`, `DeadlineViolationCause`, `DeadlineViolationDiagnosis`, `DeadlineViolationWindowSummary`, `DecisionType`, `DynamicityBreakdown`, `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `GaParameterScalingResult`, `GeneEvaluationBreakdown`, `GenerationStat`, `GeneticAlgorithmConfig`, `LinkBandwidthUsageBreakdown`, `MaGaConfig`, `NodeType`, `TemporalStepResult`, ... altri 1.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final int DEFAULT_TOP_LIMIT = 10`
- `private static final double SATURATION_THRESHOLD_PERCENT = 95.0`
- `private final MaGaConfig config`
- `private final PrintStream out`
- `private final int topLimit`
- `private final DeadlineViolationAnalyzer deadlineAnalyzer`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public DeepTemporalWindowDiagnosticPrinter()` | public | Costruisce il printer senza diagnostica di scaling GA. |
| `public DeepTemporalWindowDiagnosticPrinter(MaGaConfig config)` | public | Costruisce il printer con configurazione MA-GA. @param config configurazione usata nel test |
| `public DeepTemporalWindowDiagnosticPrinter( MaGaConfig config, PrintStream out, int topLimit )` | public | Costruisce il printer completo. @param config configurazione usata nel test, può essere null @param out stream di output @param topLimit numero massimo di task mostrati nelle sezioni top-N |
| `public void print(TemporalWindowResult result)` | public | Stampa il report diagnostico mirato. @param result risultato prodotto da TemporalWindowManager |
| `private void printHeader()` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printExecutiveSummary( TemporalWindowResult result, List<TemporalStepResult> steps )` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printTemporalAndDynamicitySummary( List<TemporalStepResult> steps )` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printGaConvergenceSummary(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printDecisionAndOffloadingSummary( List<TemporalStepResult> steps )` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printDeadlineCauseSummary(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printTopDeadlineViolations(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printCoverageProblemSummary(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printResourcePressureSummary(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printWorstWindows(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printWorstByFitness(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printWorstByDeadline(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printWorstByCoverage(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printDiagnosis(List<TemporalStepResult> steps)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private DeadlineViolationWindowSummary deadlineSummary( TemporalStepResult step )` | private | Metodo di supporto: realizza il passo `deadline summary` dentro la responsabilita' della classe. |
| `private GeneticAlgorithmConfig resolveEffectiveConfig( TemporalStepResult step )` | private | Metodo di supporto: realizza il passo `resolve effective config` dentro la responsabilita' della classe. |
| `private String triggerLabel(TemporalStepResult step)` | private | Metodo di supporto: realizza il passo `trigger label` dentro la responsabilita' della classe. |
| `private <T> List<T> limit(List<T> values, int limit)` | private | Metodo di supporto: realizza il passo `limit` dentro la responsabilita' della classe. |
| `private void printCauseCounters( Map<DeadlineViolationCause, Integer> counters )` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printSection(String title)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private String format(double value)` | private | Metodo di supporto: realizza il passo `format` dentro la responsabilita' della classe. |
| `private String formatSeconds(double value)` | private | Metodo di supporto: realizza il passo `format seconds` dentro la responsabilita' della classe. |
| `private String formatPercent(double value)` | private | Metodo di supporto: realizza il passo `format percent` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `GaRunStats` (tipo interno di `DeepTemporalWindowDiagnosticPrinter`)

- File: `src/io/reporting/DeepTemporalWindowDiagnosticPrinter.java:607`
- Tipo: `class`
- Nome completo: `io.reporting.DeepTemporalWindowDiagnosticPrinter.GaRunStats`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `DeadlineViolationAnalyzer`, `DeadlineViolationCause`, `DeadlineViolationDiagnosis`, `DeadlineViolationWindowSummary`, `DecisionType`, `DynamicityBreakdown`, `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `GaParameterScalingResult`, `GeneEvaluationBreakdown`, `GenerationStat`, `GeneticAlgorithmConfig`, `LinkBandwidthUsageBreakdown`, `MaGaConfig`, `NodeType`, `TemporalStepResult`, ... altri 1.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double improvementRatio`
- `private final double improvementLast10`
- `private final double improvementLast50`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private GaRunStats( double improvementRatio, double improvementLast10, double improvementLast50 )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `private static GaRunStats from(TemporalStepResult step)` | private | Metodo di supporto: realizza il passo `from` dentro la responsabilita' della classe. |
| `private static double improvementOverLast( List<GenerationStat> history, int window )` | private | Metodo di supporto: realizza il passo `improvement over last` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `StepStats` (tipo interno di `DeepTemporalWindowDiagnosticPrinter`)

- File: `src/io/reporting/DeepTemporalWindowDiagnosticPrinter.java:659`
- Tipo: `class`
- Nome completo: `io.reporting.DeepTemporalWindowDiagnosticPrinter.StepStats`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `DeadlineViolationAnalyzer`, `DeadlineViolationCause`, `DeadlineViolationDiagnosis`, `DeadlineViolationWindowSummary`, `DecisionType`, `DynamicityBreakdown`, `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `GaParameterScalingResult`, `GeneEvaluationBreakdown`, `GenerationStat`, `GeneticAlgorithmConfig`, `LinkBandwidthUsageBreakdown`, `MaGaConfig`, `NodeType`, `TemporalStepResult`, ... altri 1.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final Map<NodeType, Integer> nodeTypeCounters`
- `private final Map<DecisionType, Integer> decisionTypeCounters`
- `private final OffloadingBuckets buckets`
- `private final double averageOffloadingRatio`
- `private final int deadlineViolations`
- `private final double deadlineViolationRate`
- `private final int coverageInsufficient`
- `private final int cpuViolations`
- `private final int cpuSaturated`
- `private final String worstCpuNode`
- `private final String worstCpuUsagePercent`
- `private final int bandwidthViolations`
- `private final int bandwidthSaturated`
- `private final String worstBandwidthLink`
- `private final String worstBandwidthUsagePercent`
- `private final double resourcePenalty`
- `private final DeadlineViolationCause mainDeadlineCause`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private static StepStats from( TemporalStepResult step, DeadlineViolationAnalyzer analyzer )` | private | Metodo di supporto: realizza il passo `from` dentro la responsabilita' della classe. |
| `private static int countCoverageInsufficient( DeadlineViolationWindowSummary summary )` | private | Metodo di supporto: realizza il passo `count coverage insufficient` dentro la responsabilita' della classe. |
| `private static DeadlineViolationCause dominantCause( DeadlineViolationWindowSummary summary )` | private | Metodo di supporto: realizza il passo `dominant cause` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `ResourceStats` (tipo interno di `DeepTemporalWindowDiagnosticPrinter`)

- File: `src/io/reporting/DeepTemporalWindowDiagnosticPrinter.java:824`
- Tipo: `class`
- Nome completo: `io.reporting.DeepTemporalWindowDiagnosticPrinter.ResourceStats`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `DeadlineViolationAnalyzer`, `DeadlineViolationCause`, `DeadlineViolationDiagnosis`, `DeadlineViolationWindowSummary`, `DecisionType`, `DynamicityBreakdown`, `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `GaParameterScalingResult`, `GeneEvaluationBreakdown`, `GenerationStat`, `GeneticAlgorithmConfig`, `LinkBandwidthUsageBreakdown`, `MaGaConfig`, `NodeType`, `TemporalStepResult`, ... altri 1.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final int cpuViolations`
- `private final int cpuSaturated`
- `private final String worstCpuNode`
- `private final String worstCpuUsagePercent`
- `private final int bandwidthViolations`
- `private final int bandwidthSaturated`
- `private final String worstBandwidthLink`
- `private final String worstBandwidthUsagePercent`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private ResourceStats( int cpuViolations, int cpuSaturated, String worstCpuNode, String worstCpuUsagePercent, int bandwidthViolations, int bandwidthSaturated, String worstBandwidthLink, String worstBandwidthUsagePercent )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `private static ResourceStats from(EvaluationBreakdown evaluation)` | private | Metodo di supporto: realizza il passo `from` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `OffloadingBuckets` (tipo interno di `DeepTemporalWindowDiagnosticPrinter`)

- File: `src/io/reporting/DeepTemporalWindowDiagnosticPrinter.java:916`
- Tipo: `class`
- Nome completo: `io.reporting.DeepTemporalWindowDiagnosticPrinter.OffloadingBuckets`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `DeadlineViolationAnalyzer`, `DeadlineViolationCause`, `DeadlineViolationDiagnosis`, `DeadlineViolationWindowSummary`, `DecisionType`, `DynamicityBreakdown`, `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `GaParameterScalingResult`, `GeneEvaluationBreakdown`, `GenerationStat`, `GeneticAlgorithmConfig`, `LinkBandwidthUsageBreakdown`, `MaGaConfig`, `NodeType`, `TemporalStepResult`, ... altri 1.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private int zero`
- `private int low`
- `private int midLow`
- `private int midHigh`
- `private int high`
- `private int one`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private void add(double p)` | private | Metodo di supporto: realizza il passo `add` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `GlobalStats` (tipo interno di `DeepTemporalWindowDiagnosticPrinter`)

- File: `src/io/reporting/DeepTemporalWindowDiagnosticPrinter.java:944`
- Tipo: `class`
- Nome completo: `io.reporting.DeepTemporalWindowDiagnosticPrinter.GlobalStats`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `DeadlineViolationAnalyzer`, `DeadlineViolationCause`, `DeadlineViolationDiagnosis`, `DeadlineViolationWindowSummary`, `DecisionType`, `DynamicityBreakdown`, `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `GaParameterScalingResult`, `GeneEvaluationBreakdown`, `GenerationStat`, `GeneticAlgorithmConfig`, `LinkBandwidthUsageBreakdown`, `MaGaConfig`, `NodeType`, `TemporalStepResult`, ... altri 1.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final int totalTasks`
- `private final int deadlineViolations`
- `private final int coverageInsufficient`
- `private final int cpuViolations`
- `private final int bandwidthViolations`
- `private final int cpuSaturated`
- `private final int bandwidthSaturated`
- `private final Map<DeadlineViolationCause, Integer> causeCounters`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private GlobalStats( int totalTasks, int deadlineViolations, int coverageInsufficient, int cpuViolations, int bandwidthViolations, int cpuSaturated, int bandwidthSaturated, Map<DeadlineViolationCause, Integer> causeCounters )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `private double deadlineViolationRate()` | private | Metodo di supporto: realizza il passo `deadline violation rate` dentro la responsabilita' della classe. |
| `private static GlobalStats from( List<TemporalStepResult> steps, DeadlineViolationAnalyzer analyzer )` | private | Metodo di supporto: realizza il passo `from` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `GaBatchReportPrinter`

- File: `src/io/reporting/GaBatchReportPrinter.java:20`
- Tipo: `class`
- Nome completo: `io.reporting.GaBatchReportPrinter`

**Cosa fa, in parole semplici**

Report aggregato per il confronto di più esecuzioni statiche e indipendenti del MA-GA.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `GeneEvaluationBreakdown`, `LinkBandwidthUsageBreakdown`, `MaGaConfig`, `MaGaResult`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final MaGaConfig config`
- `private final PrintStream out`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public GaBatchReportPrinter(MaGaConfig config)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public GaBatchReportPrinter(MaGaConfig config, PrintStream out)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public void print( String snapshotFolder, List<SnapshotRun> runs, boolean includeDetailedReports )` | public | Stampa una sezione diagnostica o un report leggibile. |
| `private void printHeader(String snapshotFolder, int runCount)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printComparisonTable(List<SnapshotRun> runs)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printWorstSnapshots(List<SnapshotRun> runs)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printDetailedReports(List<SnapshotRun> runs)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private long countDeadlineViolations(EvaluationBreakdown evaluation)` | private | Metodo di supporto: realizza il passo `count deadline violations` dentro la responsabilita' della classe. |
| `private long countDegradedBestEffort(EvaluationBreakdown evaluation)` | private | Metodo di supporto: realizza il passo `count degraded best effort` dentro la responsabilita' della classe. |
| `private double computeMaxLateness(EvaluationBreakdown evaluation)` | private | Calcola `compute max lateness` a partire dai dati ricevuti. |
| `private double latenessSeconds(GeneEvaluationBreakdown gene)` | private | Metodo di supporto: realizza il passo `lateness seconds` dentro la responsabilita' della classe. |
| `private long countCpuViolations(EvaluationBreakdown evaluation)` | private | Metodo di supporto: realizza il passo `count cpu violations` dentro la responsabilita' della classe. |
| `private long countBandwidthViolations(EvaluationBreakdown evaluation)` | private | Metodo di supporto: realizza il passo `count bandwidth violations` dentro la responsabilita' della classe. |
| `private double improvementRatio(MaGaResult result)` | private | Metodo di supporto: realizza il passo `improvement ratio` dentro la responsabilita' della classe. |
| `private void printSection(String title)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private String format(double value)` | private | Metodo di supporto: realizza il passo `format` dentro la responsabilita' della classe. |
| `private String formatSeconds(double value)` | private | Metodo di supporto: realizza il passo `format seconds` dentro la responsabilita' della classe. |
| `private String formatPercent(double value)` | private | Metodo di supporto: realizza il passo `format percent` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SnapshotRun` (tipo interno di `GaBatchReportPrinter`)

- File: `src/io/reporting/GaBatchReportPrinter.java:241`
- Tipo: `record`
- Nome completo: `io.reporting.GaBatchReportPrinter.SnapshotRun`

**Cosa fa, in parole semplici**

Risultato di una singola ottimizzazione statica inclusa nel batch.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `GeneEvaluationBreakdown`, `LinkBandwidthUsageBreakdown`, `MaGaConfig`, `MaGaResult`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public SnapshotRun` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `LatencyDiagnosticPrinter`

- File: `src/io/reporting/LatencyDiagnosticPrinter.java:33`
- Tipo: `class`
- Nome completo: `io.reporting.LatencyDiagnosticPrinter`

**Cosa fa, in parole semplici**

Report diagnostico dedicato alla latenza comunicativa. La classe non ricalcola la fitness. Legge i breakdown prodotti per il miglior cromosoma di ogni finestra e rende esplicita la semantica adottata nella fase 6A: L_i = p * input / b + output / b + tau_n per p > 0 L(C) = sum_i L_i `tau_n` è interpretato come ritardo end-to-end aggregato del percorso remoto. Il valore è indipendente da `p` e viene conteggiato una sola volta per ogni scelta remota. Spiega e verifica la semantica attuale di `L(C) = sum_i L_i`.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `EvaluationBreakdown`, `GeneEvaluationBreakdown`, `MaGaConfig`, `NodeType`, `TemporalStepResult`, `TemporalWindowResult`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private static final int DEFAULT_TOP_LIMIT = 10`
- `private final MaGaConfig config`
- `private final PrintStream out`
- `private final int topLimit`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public LatencyDiagnosticPrinter(MaGaConfig config, PrintStream out)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public LatencyDiagnosticPrinter( MaGaConfig config, PrintStream out, int topLimit )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public void print(TemporalWindowResult result)` | public | Stampa una sezione diagnostica o un report leggibile. |
| `private void printConfiguration()` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printSummaryByWindow(TemporalWindowResult result)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printTopCommunicationDecisions(TemporalWindowResult result)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printAssumptions()` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private List<GeneEvaluationBreakdown> remoteGenes( List<GeneEvaluationBreakdown> genes )` | private | Metodo di supporto: realizza il passo `remote genes` dentro la responsabilita' della classe. |
| `private void printSectionTitle(String title)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printf(String format, Object... values)` | private | Stampa una sezione diagnostica o un report leggibile. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `MobilityDiagnosticPrinter`

- File: `src/io/reporting/MobilityDiagnosticPrinter.java:29`
- Tipo: `class`
- Nome completo: `io.reporting.MobilityDiagnosticPrinter`

**Cosa fa, in parole semplici**

Report diagnostico dedicato alla mobilità. La classe non ricalcola la fitness. Legge i breakdown prodotti durante la valutazione del miglior cromosoma di ogni finestra e rende espliciti distanza, copertura, phi_cov, phi_link, phi_ho e contributi pesati. Rende visibili copertura, instabilita' e componenti della penalita' mobility-aware.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `GeneEvaluationBreakdown`, `MaGaConfig`, `MobilityConfig`, `MobilityLinkMetrics`, `MobilityPenaltyBreakdown`, `NodeType`, `PenaltyConfig`, `TemporalStepResult`, `TemporalWindowResult`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final int DEFAULT_TOP_RISK_LIMIT = 10`
- `private final MaGaConfig config`
- `private final PrintStream out`
- `private final int topRiskLimit`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public MobilityDiagnosticPrinter(MaGaConfig config, PrintStream out)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public MobilityDiagnosticPrinter( MaGaConfig config, PrintStream out, int topRiskLimit )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public void print(TemporalWindowResult result)` | public | Stampa una sezione diagnostica o un report leggibile. |
| `private void printConfiguration()` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printPenaltySummaryByWindow(TemporalWindowResult result)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printSummaryByNodeType(TemporalWindowResult result)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printTopMobilityRiskDecisions(TemporalWindowResult result)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printModelAssumptions()` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private List<GeneEvaluationBreakdown> genes(TemporalStepResult step)` | private | Metodo di supporto: realizza il passo `genes` dentro la responsabilita' della classe. |
| `private List<GeneEvaluationBreakdown> remoteGenes(TemporalStepResult step)` | private | Metodo di supporto: realizza il passo `remote genes` dentro la responsabilita' della classe. |
| `private String modelSummary(List<GeneEvaluationBreakdown> genes)` | private | Metodo di supporto: realizza il passo `model summary` dentro la responsabilita' della classe. |
| `private String numberOrDash(double value)` | private | Metodo di supporto: realizza il passo `number or dash` dentro la responsabilita' della classe. |
| `private String metersOrDash(double value)` | private | Metodo di supporto: realizza il passo `meters or dash` dentro la responsabilita' della classe. |
| `private String speedOrDash(double value)` | private | Metodo di supporto: realizza il passo `speed or dash` dentro la responsabilita' della classe. |
| `private void printSectionTitle(String title)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printf(String format, Object... values)` | private | Stampa una sezione diagnostica o un report leggibile. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `Stats` (tipo interno di `MobilityDiagnosticPrinter`)

- File: `src/io/reporting/MobilityDiagnosticPrinter.java:281`
- Tipo: `class`
- Nome completo: `io.reporting.MobilityDiagnosticPrinter.Stats`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `GeneEvaluationBreakdown`, `MaGaConfig`, `MobilityConfig`, `MobilityLinkMetrics`, `MobilityPenaltyBreakdown`, `NodeType`, `PenaltyConfig`, `TemporalStepResult`, `TemporalWindowResult`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private int count`
- `private int cloudPlaceholderCount`
- `private double totalMobilityPenalty`
- `private double totalCoverageTime`
- `private double minimumCoverageTime = Double.POSITIVE_INFINITY`
- `private double totalCoverageRisk`
- `private double maximumCoverageRisk`
- `private double totalLinkInstability`
- `private double maximumLinkInstability`
- `private double totalHandoverRisk`
- `private double maximumHandoverRisk`
- `private double totalDistance`
- `private int distanceCount`
- `private double totalRadius`
- `private int radiusCount`
- `private double totalSourceSpeed`
- `private int sourceSpeedCount`
- `private double totalRelativeSpeed`
- `private int relativeSpeedCount`
- `private double averageCoverageTime = Double.NaN`
- `private double averageCoverageRisk`
- `private double averageLinkInstability`
- `private double averageHandoverRisk`
- `private double averageDistance = Double.NaN`
- `private double averageRadius = Double.NaN`
- `private double averageSourceSpeed = Double.NaN`
- `private double averageRelativeSpeed = Double.NaN`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private static Stats from(List<GeneEvaluationBreakdown> genes)` | private | Metodo di supporto: realizza il passo `from` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `PopulationReuseDecisionDiagnosticPrinter`

- File: `src/io/reporting/PopulationReuseDecisionDiagnosticPrinter.java:17`
- Tipo: `class`
- Nome completo: `io.reporting.PopulationReuseDecisionDiagnosticPrinter`

**Cosa fa, in parole semplici**

Printer dedicato alla decisione di riuso della popolazione. Serve a capire perché la policy applica WARM_START, PARTIAL_RESTART o COLD_START. Evita di dedurre la scelta solo guardando D(k).

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `DynamicityBreakdown`, `PopulationReuseDecision`, `TemporalStepResult`, `TemporalWindowResult`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final PrintStream out`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public PopulationReuseDecisionDiagnosticPrinter()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public PopulationReuseDecisionDiagnosticPrinter(PrintStream out)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public void print(TemporalWindowResult result)` | public | Stampa una sezione diagnostica o un report leggibile. |
| `private String format(double value)` | private | Metodo di supporto: realizza il passo `format` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `StressResultPrinter`

- File: `src/io/reporting/StressResultPrinter.java:42`
- Tipo: `class`
- Nome completo: `io.reporting.StressResultPrinter`

**Cosa fa, in parole semplici**

Printer diagnostico per scenari MA-GA di grandi dimensioni. A differenza di ResultPrinter, non stampa ogni dettaglio dello scenario. Riassume le informazioni importanti per capire: - quanto il GA è migliorato; - quali tipi di decisione sono stati scelti; - quali task violano deadline o copertura; - quali risorse CPU o banda sono violate/sature; - quali task contribuiscono maggiormente ai problemi della soluzione.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `DecisionType`, `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `FitnessWeights`, `GaParameterScalingResult`, `GeneEvaluationBreakdown`, `GenerationStat`, `GeneticAlgorithmConfig`, `LinkBandwidthUsageBreakdown`, `LocalResourceUsageBreakdown`, `MaGaConfig`, `MaGaResult`, `NodeCandidate`, `NodeType`, `SystemSnapshot`, `TaskInstance`, ... altri 1.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final int DEFAULT_TOP_LIMIT = 10`
- `private static final double SATURATION_THRESHOLD_PERCENT = 95.0`
- `private static final double NEAR_COVERAGE_THRESHOLD_RATIO = 1.25`
- `private final MaGaConfig config`
- `private final PrintStream out`
- `private final int topLimit`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public StressResultPrinter(MaGaConfig config)` | public | Costruisce il printer usando System.out e top 10 diagnostici. @param config configurazione MA-GA usata nell'esecuzione |
| `public StressResultPrinter(MaGaConfig config, PrintStream out)` | public | Costruisce il printer con stream personalizzato. @param config configurazione MA-GA usata nell'esecuzione @param out stream di output |
| `public StressResultPrinter( MaGaConfig config, PrintStream out, int topLimit )` | public | Costruisce il printer con stream e numero massimo di righe top-N. @param config configurazione MA-GA usata nell'esecuzione @param out stream di output @param topLimit numero massimo di elementi nelle classifiche diagnostiche |
| `public void printStressReport( SystemSnapshot snapshot, MaGaResult result )` | public | Stampa il report diagnostico dello stress test. @param snapshot snapshot usato come input del MA-GA @param result risultato prodotto da MaGaOptimizer |
| `private void printHeader()` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printScenarioSummary(SystemSnapshot snapshot)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printGaSummary( SystemSnapshot snapshot, MaGaResult result )` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printFitnessSummary(EvaluationBreakdown evaluation)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printDecisionDistribution(EvaluationBreakdown evaluation)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printDeadlineSummary(EvaluationBreakdown evaluation)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printResourceSummary(EvaluationBreakdown evaluation)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printMobilitySummary(EvaluationBreakdown evaluation)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printTopProblematicTasks(EvaluationBreakdown evaluation)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printGenerationTrend(MaGaResult result)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printInterpretationHints( SystemSnapshot snapshot, MaGaResult result )` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private Map<NodeType, Integer> countCandidatesByType( List<NodeCandidate> candidates )` | private | Metodo di supporto: realizza il passo `count candidates by type` dentro la responsabilita' della classe. |
| `private int countPhysicalExecutionNodes(List<NodeCandidate> candidates)` | private | Metodo di supporto: realizza il passo `count physical execution nodes` dentro la responsabilita' della classe. |
| `private void printTopCpuResources( String title, List<ExecutionNodeResourceUsageBreakdown> usages )` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printTopBandwidthResources( String title, List<LinkBandwidthUsageBreakdown> usages )` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printLocalWorkloadSummary( List<LocalResourceUsageBreakdown> localUsages )` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printGeneTableHeader()` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private void printGeneTableRow( GeneEvaluationBreakdown gene, String note )` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private boolean isNearCriticalCoverage(GeneEvaluationBreakdown gene)` | private | Risponde con true/false alla domanda `is near critical coverage`. |
| `private double coverageMarginRatio(GeneEvaluationBreakdown gene)` | private | Metodo di supporto: realizza il passo `coverage margin ratio` dentro la responsabilita' della classe. |
| `private double deadlineViolationRatio(GeneEvaluationBreakdown gene)` | private | Metodo di supporto: realizza il passo `deadline violation ratio` dentro la responsabilita' della classe. |
| `private String detectDominantTerm( double weightedT, double weightedL, double weightedMobility, double weightedResources )` | private | Metodo di supporto: realizza il passo `detect dominant term` dentro la responsabilita' della classe. |
| `private <T> List<T> limit(List<T> values)` | private | Metodo di supporto: realizza il passo `limit` dentro la responsabilita' della classe. |
| `private List<GenerationStat> sampleHistory(List<GenerationStat> history)` | private | Metodo di supporto: realizza il passo `sample history` dentro la responsabilita' della classe. |
| `private void printSection(String title)` | private | Stampa una sezione diagnostica o un report leggibile. |
| `private String format(double value)` | private | Metodo di supporto: realizza il passo `format` dentro la responsabilita' della classe. |
| `private String formatSeconds(double value)` | private | Metodo di supporto: realizza il passo `format seconds` dentro la responsabilita' della classe. |
| `private String formatPercent(double value)` | private | Metodo di supporto: realizza il passo `format percent` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SystemStateSourceDiagnosticPrinter`

- File: `src/io/reporting/SystemStateSourceDiagnosticPrinter.java:14`
- Tipo: `class`
- Nome completo: `io.reporting.SystemStateSourceDiagnosticPrinter`

**Cosa fa, in parole semplici**

Printer per controllare l'allineamento tra tempo logico del manager e timestamp degli snapshot restituiti dalla sorgente dati.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `SystemStateObservation`, `TemporalStepResult`, `TemporalWindowResult`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON_SECONDS = 1.0E-6`
- `private final PrintStream out`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public SystemStateSourceDiagnosticPrinter()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public SystemStateSourceDiagnosticPrinter(PrintStream out)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public void print(TemporalWindowResult result)` | public | Stampa una sezione diagnostica o un report leggibile. |
| `private String interpret(String mode, double shift)` | private | Metodo di supporto: realizza il passo `interpret` dentro la responsabilita' della classe. |
| `private boolean isFutureLookAhead(double shift)` | private | Risponde con true/false alla domanda `is future look ahead`. |
| `private String formatSeconds(double value)` | private | Metodo di supporto: realizza il passo `format seconds` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TemporalTimingDiagnosticPrinter`

- File: `src/io/reporting/TemporalTimingDiagnosticPrinter.java:15`
- Tipo: `class`
- Nome completo: `io.reporting.TemporalTimingDiagnosticPrinter`

**Cosa fa, in parole semplici**

Printer compatto dedicato alla separazione dei tempi. Serve a verificare che il TemporalWindowManager stia usando il proprio tempo logico/adattivo, senza dipendere dal tempo salvato negli snapshot JSON.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `TemporalStepResult`, `TemporalWindowResult`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final PrintStream out`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TemporalTimingDiagnosticPrinter()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public TemporalTimingDiagnosticPrinter(PrintStream out)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public void print(TemporalWindowResult result)` | public | Stampa una sezione diagnostica o un report leggibile. |
| `private String formatSeconds(double value)` | private | Metodo di supporto: realizza il passo `format seconds` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `io.reporting.diagnostics.deadline`

Analizza le deadline violate e ne spiega la causa probabile.

### `DeadlineViolationAnalyzer`

- File: `src/io/reporting/diagnostics/deadline/DeadlineViolationAnalyzer.java:25`
- Tipo: `class`
- Nome completo: `io.reporting.diagnostics.deadline.DeadlineViolationAnalyzer`

**Cosa fa, in parole semplici**

Analizzatore diagnostico delle deadline. La versione raffinata distingue meglio: - task completamente locali; - task parzialmente offloadati ma dominati dal ramo locale; - task parzialmente offloadati ma dominati dal ramo remoto; - veri casi misti locale/remoto; - problemi di copertura. La classe non modifica fitness, repair o cromosomi.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `GeneEvaluationBreakdown`, `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private static final double BRANCH_DOMINANCE_MARGIN = 0.10`
- `private static final double BRANCH_BALANCE_THRESHOLD = 0.85`
- `private static final double REMOTE_COMPONENT_DOMINANCE_RATIO = 0.45`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public DeadlineViolationWindowSummary summarize( List<GeneEvaluationBreakdown> genes )` | public | Diagnostica tutti i task della finestra. |
| `public DeadlineViolationDiagnosis diagnose( GeneEvaluationBreakdown gene )` | public | Diagnostica un singolo task. |
| `private DeadlineViolationCause classifyPrimaryCause( GeneEvaluationBreakdown gene, double local, double remote, double branchBalance )` | private | Metodo di supporto: realizza il passo `classify primary cause` dentro la responsabilita' della classe. |
| `private DeadlineViolationCause classifySecondaryCause( GeneEvaluationBreakdown gene, DeadlineViolationCause primaryCause )` | private | Metodo di supporto: realizza il passo `classify secondary cause` dentro la responsabilita' della classe. |
| `private DeadlineViolationCause classifyRemotePipeline( GeneEvaluationBreakdown gene )` | private | Metodo di supporto: realizza il passo `classify remote pipeline` dentro la responsabilita' della classe. |
| `private Component dominantComponent( GeneEvaluationBreakdown gene )` | private | Metodo di supporto: realizza il passo `dominant component` dentro la responsabilita' della classe. |
| `private Component maxComponent(Component... components)` | private | Metodo di supporto: realizza il passo `max component` dentro la responsabilita' della classe. |
| `private String buildNote( GeneEvaluationBreakdown gene, DeadlineViolationCause primaryCause, DeadlineViolationCause secondaryCause, double localRatio, double remoteRatio, double branchBalance, Component dominant )` | private | Costruisce `build note` usando le informazioni disponibili. |
| `private double safe(double value)` | private | Esegue un'operazione protetta per evitare valori non finiti o divisioni non valide. |
| `private String format(double value)` | private | Metodo di supporto: realizza il passo `format` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `Component` (tipo interno di `DeadlineViolationAnalyzer`)

- File: `src/io/reporting/diagnostics/deadline/DeadlineViolationAnalyzer.java:388`
- Tipo: `class`
- Nome completo: `io.reporting.diagnostics.deadline.DeadlineViolationAnalyzer.Component`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `GeneEvaluationBreakdown`, `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String name`
- `private final double seconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private Component(String name, double seconds)` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `DeadlineViolationCause`

- File: `src/io/reporting/diagnostics/deadline/DeadlineViolationCause.java:9`
- Tipo: `enum`
- Nome completo: `io.reporting.diagnostics.deadline.DeadlineViolationCause`

**Cosa fa, in parole semplici**

Causa diagnostica principale di una deadline non rispettata. Questa enum è usata solo dal reporting diagnostico. Non modifica fitness, repair, mutazione o selezione.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`DEADLINE_RESPECTED`, `LOCAL_EXECUTION_BOTTLENECK`, `LOCAL_BRANCH_DOMINATES`, `REMOTE_BRANCH_DOMINATES`, `MIXED_LOCAL_REMOTE_BOTTLENECK`, `UPLOAD_BOTTLENECK`, `REMOTE_EXECUTION_BOTTLENECK`, `DOWNLOAD_BOTTLENECK`, `BASE_LATENCY_BOTTLENECK`, `MIXED_REMOTE_PIPELINE_BOTTLENECK`, `COVERAGE_INSUFFICIENT`, `UNKNOWN`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `DeadlineViolationDiagnosis`

- File: `src/io/reporting/diagnostics/deadline/DeadlineViolationDiagnosis.java:12`
- Tipo: `class`
- Nome completo: `io.reporting.diagnostics.deadline.DeadlineViolationDiagnosis`

**Cosa fa, in parole semplici**

Diagnosi di un singolo task rispetto alla deadline. L'oggetto conserva i tempi principali calcolati nella fitness e aggiunge una classificazione diagnostica della causa dominante.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica direttamente con: `DecisionType`, `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String taskId`
- `private final String sourceVehicleId`
- `private final String selectedCandidateId`
- `private final String executionNodeId`
- `private final NodeType nodeType`
- `private final DecisionType decisionType`
- `private final double offloadingRatio`
- `private final double allocatedCpu`
- `private final double allocatedBandwidth`
- `private final double completionTimeSeconds`
- `private final double deadlineSeconds`
- `private final double violationSeconds`
- `private final double violationRatio`
- `private final double localExecutionTimeSeconds`
- `private final double uploadTimeSeconds`
- `private final double remoteExecutionTimeSeconds`
- `private final double downloadTimeSeconds`
- `private final double baseLatencySeconds`
- `private final double remotePartTimeSeconds`
- `private final double communicationLatencySeconds`
- `private final double localBranchRatio`
- `private final double remoteBranchRatio`
- `private final double branchBalanceRatio`
- `private final double coverageTimeSeconds`
- `private final boolean coverageSufficient`
- `private final double mobilityPenalty`
- `private final double constraintPenalty`
- `private final DeadlineViolationCause primaryCause`
- `private final DeadlineViolationCause secondaryCause`
- `private final String dominantComponentName`
- `private final double dominantComponentSeconds`
- `private final String note`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public String getTaskId()` | public | Restituisce il valore di `TaskId` senza modificarlo. |
| `public String getSourceVehicleId()` | public | Restituisce il valore di `SourceVehicleId` senza modificarlo. |
| `public String getSelectedCandidateId()` | public | Restituisce il valore di `SelectedCandidateId` senza modificarlo. |
| `public String getExecutionNodeId()` | public | Restituisce il valore di `ExecutionNodeId` senza modificarlo. |
| `public NodeType getNodeType()` | public | Restituisce il valore di `NodeType` senza modificarlo. |
| `public DecisionType getDecisionType()` | public | Restituisce il valore di `DecisionType` senza modificarlo. |
| `public double getOffloadingRatio()` | public | Restituisce il valore di `OffloadingRatio` senza modificarlo. |
| `public double getAllocatedCpu()` | public | Restituisce il valore di `AllocatedCpu` senza modificarlo. |
| `public double getAllocatedBandwidth()` | public | Restituisce il valore di `AllocatedBandwidth` senza modificarlo. |
| `public double getCompletionTimeSeconds()` | public | Restituisce il valore di `CompletionTimeSeconds` senza modificarlo. |
| `public double getDeadlineSeconds()` | public | Restituisce il valore di `DeadlineSeconds` senza modificarlo. |
| `public double getViolationSeconds()` | public | Restituisce il valore di `ViolationSeconds` senza modificarlo. |
| `public double getViolationRatio()` | public | Restituisce il valore di `ViolationRatio` senza modificarlo. |
| `public double getLocalExecutionTimeSeconds()` | public | Restituisce il valore di `LocalExecutionTimeSeconds` senza modificarlo. |
| `public double getUploadTimeSeconds()` | public | Restituisce il valore di `UploadTimeSeconds` senza modificarlo. |
| `public double getRemoteExecutionTimeSeconds()` | public | Restituisce il valore di `RemoteExecutionTimeSeconds` senza modificarlo. |
| `public double getDownloadTimeSeconds()` | public | Restituisce il valore di `DownloadTimeSeconds` senza modificarlo. |
| `public double getBaseLatencySeconds()` | public | Restituisce il valore di `BaseLatencySeconds` senza modificarlo. |
| `public double getRemotePartTimeSeconds()` | public | Restituisce il valore di `RemotePartTimeSeconds` senza modificarlo. |
| `public double getCommunicationLatencySeconds()` | public | Restituisce il valore di `CommunicationLatencySeconds` senza modificarlo. |
| `public double getLocalBranchRatio()` | public | Restituisce il valore di `LocalBranchRatio` senza modificarlo. |
| `public double getRemoteBranchRatio()` | public | Restituisce il valore di `RemoteBranchRatio` senza modificarlo. |
| `public double getBranchBalanceRatio()` | public | Restituisce il valore di `BranchBalanceRatio` senza modificarlo. |
| `public double getCoverageTimeSeconds()` | public | Restituisce il valore di `CoverageTimeSeconds` senza modificarlo. |
| `public boolean isCoverageSufficient()` | public | Risponde con true/false alla domanda `is coverage sufficient`. |
| `public double getMobilityPenalty()` | public | Restituisce il valore di `MobilityPenalty` senza modificarlo. |
| `public double getConstraintPenalty()` | public | Restituisce il valore di `ConstraintPenalty` senza modificarlo. |
| `public DeadlineViolationCause getPrimaryCause()` | public | Restituisce il valore di `PrimaryCause` senza modificarlo. |
| `public DeadlineViolationCause getSecondaryCause()` | public | Restituisce il valore di `SecondaryCause` senza modificarlo. |
| `public String getDominantComponentName()` | public | Restituisce il valore di `DominantComponentName` senza modificarlo. |
| `public double getDominantComponentSeconds()` | public | Restituisce il valore di `DominantComponentSeconds` senza modificarlo. |
| `public String getNote()` | public | Restituisce il valore di `Note` senza modificarlo. |
| `public boolean isDeadlineViolated()` | public | Risponde con true/false alla domanda `is deadline violated`. |
| `public boolean hasCoverageProblem()` | public | Risponde con true/false alla domanda `has coverage problem`. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `DeadlineViolationWindowSummary`

- File: `src/io/reporting/diagnostics/deadline/DeadlineViolationWindowSummary.java:11`
- Tipo: `class`
- Nome completo: `io.reporting.diagnostics.deadline.DeadlineViolationWindowSummary`

**Cosa fa, in parole semplici**

Riassunto diagnostico delle deadline per una singola finestra temporale.

**Relazione con la formalizzazione**

Non cambia il modello: rende osservabili fitness, vincoli, tempi e diagnosi.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final int totalTasks`
- `private final int respectedTasks`
- `private final int violatedTasks`
- `private final double violationRate`
- `private final Map<DeadlineViolationCause, Integer> countByCause`
- `private final List<DeadlineViolationDiagnosis> diagnoses`
- `private final List<DeadlineViolationDiagnosis> violationsBySeverity`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public DeadlineViolationWindowSummary( int totalTasks, int respectedTasks, int violatedTasks, double violationRate, Map<DeadlineViolationCause, Integer> countByCause, List<DeadlineViolationDiagnosis> diagnoses, List<DeadlineViolationDiagnosis> violationsBySeverity )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public int getTotalTasks()` | public | Restituisce il valore di `TotalTasks` senza modificarlo. |
| `public int getRespectedTasks()` | public | Restituisce il valore di `RespectedTasks` senza modificarlo. |
| `public int getViolatedTasks()` | public | Restituisce il valore di `ViolatedTasks` senza modificarlo. |
| `public double getViolationRate()` | public | Restituisce il valore di `ViolationRate` senza modificarlo. |
| `public Map<DeadlineViolationCause, Integer> getCountByCause()` | public | Restituisce il valore di `CountByCause` senza modificarlo. |
| `public int getCountForCause(DeadlineViolationCause cause)` | public | Restituisce il valore di `CountForCause` senza modificarlo. |
| `public List<DeadlineViolationDiagnosis> getDiagnoses()` | public | Restituisce il valore di `Diagnoses` senza modificarlo. |
| `public List<DeadlineViolationDiagnosis> getViolationsBySeverity()` | public | Restituisce il valore di `ViolationsBySeverity` senza modificarlo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `io.snapshot`

Legge file JSON e li converte nel modello interno.

### `JsonSnapshotFolderLoader`

- File: `src/io/snapshot/JsonSnapshotFolderLoader.java:18`
- Tipo: `class`
- Nome completo: `io.snapshot.JsonSnapshotFolderLoader`

**Cosa fa, in parole semplici**

Carica e valida tutti gli snapshot JSON contenuti direttamente in una cartella. Il loader non impone più prefissi legati ai vecchi stress test. Una cartella rappresenta uno scenario e può quindi contenere file con nomi descrittivi differenti. I JSON vengono caricati in ordine alfabetico e restituiti in ordine temporale stabile.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `SnapshotValidator`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final String JSON_EXTENSION = ".json"`
- `private final SnapshotLoader snapshotLoader`
- `private final SnapshotValidator snapshotValidator`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public JsonSnapshotFolderLoader()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public JsonSnapshotFolderLoader(SnapshotValidator snapshotValidator)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public JsonSnapshotFolderLoader( SnapshotLoader snapshotLoader, SnapshotValidator snapshotValidator )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public List<SystemSnapshot> load(String folderPath) throws Exception` | public | Metodo di supporto: realizza il passo `load` dentro la responsabilita' della classe. |
| `private List<File> listSnapshotFiles(String folderPath)` | private | Metodo di supporto: realizza il passo `list snapshot files` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SnapshotLoader`

- File: `src/io/snapshot/SnapshotLoader.java:28`
- Tipo: `class`
- Nome completo: `io.snapshot.SnapshotLoader`

**Cosa fa, in parole semplici**

Carica uno snapshot statico del sistema da file JSON. Il loader separa quattro passaggi: lettura del JSON in DTO grezzi, validazione centralizzata in `SnapshotValidator`, mapping verso il modello interno e verifica dell'invariante locale necessario al repair.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `LocalCandidateInvariantValidator`, `NodeCandidate`, `NodeCandidateInputDto`, `NodeType`, `ObjectMapper`, `SnapshotInputDto`, `SnapshotValidator`, `SystemSnapshot`, `TaskInputDto`, `TaskInstance`, `VehicleInputDto`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final ObjectMapper objectMapper`
- `private final SnapshotValidator snapshotValidator`
- `private final LocalCandidateInvariantValidator localCandidateInvariantValidator`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public SnapshotLoader()` | public | Costruisce un loader basato su Jackson e sui validator standard. |
| `public SnapshotLoader(SnapshotValidator snapshotValidator)` | public | Costruisce un loader mantenendo compatibilità con i chiamanti esistenti. @param snapshotValidator validator da usare prima del mapping |
| `public SnapshotLoader( SnapshotValidator snapshotValidator, LocalCandidateInvariantValidator localCandidateInvariantValidator )` | public | Costruisce un loader con validator espliciti. @param snapshotValidator validator dei DTO grezzi @param localCandidateInvariantValidator validator del fallback locale |
| `public SystemSnapshot load(String filePath) throws IOException` | public | Carica uno snapshot da file JSON. @param filePath percorso del file JSON @return snapshot convertito nel modello interno @throws IOException se il file non e' leggibile o il JSON non e' valido |
| `private SystemSnapshot toSystemSnapshot(SnapshotInputDto dto)` | private | Converte il DTO principale in `SystemSnapshot`. |
| `private List<VehicleSnapshot> toVehicles(List<VehicleInputDto> vehicleDtos)` | private | Converte i veicoli JSON in `VehicleSnapshot`. |
| `private List<TaskInstance> toTasks(List<TaskInputDto> taskDtos)` | private | Converte i task JSON in `TaskInstance`. |
| `private List<NodeCandidate> toCandidateNodes(List<NodeCandidateInputDto> nodeDtos)` | private | Converte i candidati JSON in `NodeCandidate`. |
| `private NodeType parseNodeType(String value)` | private | Converte il tipo del nodo da stringa JSON a enum. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SnapshotPaths`

- File: `src/io/snapshot/SnapshotPaths.java:12`
- Tipo: `class`
- Nome completo: `io.snapshot.SnapshotPaths`

**Cosa fa, in parole semplici**

Catalogo centralizzato dei dataset inclusi nel repository. La struttura distingue gli snapshot statici destinati al confronto del GA dalle sequenze temporali destinate al gestore adattivo.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final String DATA_ROOT = "data"`
- `public static final String STATIC_WINDOW_STRESS_FOLDER = GA_DEFAULT_BATCH_FOLDER`
- `public static final String TEMPORAL_WINDOW_STRESS_FOLDER = TEMPORAL_URBAN_MODERATE_FOLDER`
- `public static final String TEMPORAL_WINDOW_URBAN_CALIBRATED_FOLDER = TEMPORAL_DEFAULT_SCENARIO_FOLDER`
- `public static final String WINDOW_VALIDATION_FOLDER = TEMPORAL_VALIDATION_FOLDER`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private SnapshotPaths()` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `private static String path(String first, String... more)` | private | Metodo di supporto: realizza il passo `path` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `io.snapshot.dto`

Rappresenta gli oggetti grezzi letti dal JSON prima della validazione.

### `NodeCandidateInputDto`

- File: `src/io/snapshot/dto/NodeCandidateInputDto.java:11`
- Tipo: `class`
- Nome completo: `io.snapshot.dto.NodeCandidateInputDto`

**Cosa fa, in parole semplici**

DTO grezzo di un candidato di esecuzione nello snapshot di input. Il campo JSON `baseLatencySeconds` viene mantenuto per compatibilità con gli snapshot già prodotti. Semanticamente rappresenta `tau_n`: il ritardo end-to-end aggregato del percorso remoto, conteggiato una sola volta per ogni scelta remota.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica soprattutto con il loader JSON e con la validazione dello snapshot.
E' un contenitore di input: non decide, trasporta dati grezzi verso il modello interno.

**Campi o valori importanti**

Campi dichiarati principali:
- `public String candidateId`
- `public String sourceVehicleId`
- `public String executionNodeId`
- `public String type`
- `public Double availableCpu`
- `public Double availableBandwidth`
- `public Double baseLatencySeconds`
- `public Double nodeX`
- `public Double nodeY`
- `public Double coverageRadiusMeters`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SnapshotInputDto`

- File: `src/io/snapshot/dto/SnapshotInputDto.java:12`
- Tipo: `class`
- Nome completo: `io.snapshot.dto.SnapshotInputDto`

**Cosa fa, in parole semplici**

DTO grezzo dello snapshot letto da JSON o da un adapter esterno. Non contiene logica di dominio: rappresenta solo la forma dell'input. La validazione avviene in `SnapshotValidator` prima del mapping verso il modello interno.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica soprattutto con il loader JSON e con la validazione dello snapshot.
E' un contenitore di input: non decide, trasporta dati grezzi verso il modello interno.

**Campi o valori importanti**

Campi dichiarati principali:
- `public String snapshotId`
- `public Double timeSeconds`
- `public List<VehicleInputDto> vehicles`
- `public List<TaskInputDto> tasks`
- `public List<NodeCandidateInputDto> candidateNodes`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TaskInputDto`

- File: `src/io/snapshot/dto/TaskInputDto.java:6`
- Tipo: `class`
- Nome completo: `io.snapshot.dto.TaskInputDto`

**Cosa fa, in parole semplici**

DTO grezzo di un task nello snapshot di input.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica soprattutto con il loader JSON e con la validazione dello snapshot.
E' un contenitore di input: non decide, trasporta dati grezzi verso il modello interno.

**Campi o valori importanti**

Campi dichiarati principali:
- `public String taskId`
- `public String sourceVehicleId`
- `public Double inputSizeBits`
- `public Double outputSizeBits`
- `public Double cpuCycles`
- `public Double deadlineSeconds`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `VehicleInputDto`

- File: `src/io/snapshot/dto/VehicleInputDto.java:6`
- Tipo: `class`
- Nome completo: `io.snapshot.dto.VehicleInputDto`

**Cosa fa, in parole semplici**

DTO grezzo di un veicolo nello snapshot di input.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica soprattutto con il loader JSON e con la validazione dello snapshot.
E' un contenitore di input: non decide, trasporta dati grezzi verso il modello interno.

**Campi o valori importanti**

Campi dichiarati principali:
- `public String vehicleId`
- `public Double x`
- `public Double y`
- `public Double speed`
- `public Double localCpu`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `model.genetic`

Rappresenta cromosomi e geni, cioe' la forma concreta delle soluzioni del GA.

### `Chromosome`

- File: `src/model/genetic/Chromosome.java:11`
- Tipo: `class`
- Nome completo: `model.genetic.Chromosome`

**Cosa fa, in parole semplici**

Soluzione candidata del GA. Ogni cromosoma contiene un gene per task e una fitness scalare. La fitness viene inizializzata a infinito finché il cromosoma non viene valutato. Rappresenta una strategia completa, cioe' una lista di geni.

**Relazione con la formalizzazione**

Collega direttamente il codice alla rappresentazione della soluzione: gene = decisione per task, cromosoma = strategia completa `C`.

**Con chi comunica**

Comunica passando dati tra loader, GA, fitness, repair e report.
Di norma non orchestra il flusso: viene letto da altre classi che prendono decisioni.

**Campi o valori importanti**

Campi dichiarati principali:
- `private List<Gene> genes`
- `private double fitness = Double.POSITIVE_INFINITY`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public Chromosome()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public Chromosome(List<Gene> genes)` | public | Crea un cromosoma non ancora valutato. @param genes decisioni di offloading contenute nel cromosoma |
| `public Chromosome(List<Gene> genes, double fitness)` | public | Crea un cromosoma con fitness già assegnata. @param genes decisioni di offloading contenute nel cromosoma @param fitness valore di fitness calcolato |
| `public List<Gene> getGenes()` | public | Restituisce il valore di `Genes` senza modificarlo. |
| `public void setGenes(List<Gene> genes)` | public | Aggiorna il valore di `Genes` nell'oggetto. |
| `public double getFitness()` | public | Restituisce il valore di `Fitness` senza modificarlo. |
| `public void setFitness(double fitness)` | public | Aggiorna il valore di `Fitness` nell'oggetto. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `Gene`

- File: `src/model/genetic/Gene.java:23`
- Tipo: `class`
- Nome completo: `model.genetic.Gene`

**Cosa fa, in parole semplici**

Rappresenta la decisione di offloading per un singolo task. Formalmente: g_i = (n_i, p_i, f_i, b_i) `n_i`: candidato di esecuzione scelto; `p_i`: quota di offloading; `f_i`: CPU assegnata; `b_i`: banda assegnata. Nel modello source-aware `n_i` è un `candidateId`, non un nodo globale. Il candidato deve essere valido per il veicolo che ha generato il task. Rappresenta una decisione per un task: candidato, quota remota, CPU e banda.

**Relazione con la formalizzazione**

Collega direttamente il codice alla rappresentazione della soluzione: gene = decisione per task, cromosoma = strategia completa `C`.

**Con chi comunica**

Comunica passando dati tra loader, GA, fitness, repair e report.
Di norma non orchestra il flusso: viene letto da altre classi che prendono decisioni.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String taskId`
- `private final String selectedCandidateId`
- `private final double offloadingRatio`
- `private final double allocatedCpu`
- `private final double allocatedBandwidth`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public Gene( String taskId, String selectedCandidateId, double offloadingRatio, double allocatedCpu, double allocatedBandwidth )` | public | Costruisce un gene. @param taskId task a cui il gene si riferisce @param selectedCandidateId candidato di esecuzione scelto @param offloadingRatio quota remota `p_i` @param allocatedCpu CPU assegnata `f_i` @param allocatedBandwidth banda assegnata `b_i` |
| `public String getTaskId()` | public | Restituisce il task associato al gene. |
| `public String getSelectedCandidateId()` | public | Restituisce il candidato di esecuzione scelto. |
| `public String getSelectedNodeId()` | public | Metodo di compatibilità temporanea. Nel modello source-aware è preferibile usare `#getSelectedCandidateId()`. |
| `public double getOffloadingRatio()` | public | Restituisce la quota di offloading. |
| `public double getAllocatedCpu()` | public | Restituisce la CPU assegnata. |
| `public double getAllocatedBandwidth()` | public | Restituisce la banda assegnata. |
| `private static String requireText(String value, String fieldName)` | private | Verifica che una stringa sia valorizzata. |
| `private static double validateFinite(String fieldName, double value)` | private | Verifica che un double sia finito. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `model.mobility`

Stima metriche mobility-aware: copertura, distanza, velocita' relativa e instabilita' del link.

### `CoverageEstimator`

- File: `src/model/mobility/CoverageEstimator.java:20`
- Tipo: `class`
- Nome completo: `model.mobility.CoverageEstimator`

**Cosa fa, in parole semplici**

Stima le grandezze mobility-aware di un candidato rispetto a un task. La classe usa lo snapshot corrente, il task e il candidato selezionato. Il tempo di copertura e l'instabilità del collegamento vengono ricavati da un'unica stima, così fitness e report osservano gli stessi valori. Calcola copertura e instabilita' link con una stima unica usata da fitness, repair e report.

**Relazione con la formalizzazione**

Implementa la parte mobility-aware: tempo di copertura, instabilita' link, rischio handover e limiti temporali collegati alla copertura.

**Con chi comunica**

Comunica direttamente con: `MobilityConfig`, `NodeCandidate`, `NodeType`, `SystemSnapshot`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final MobilityConfig mobilityConfig`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public CoverageEstimator(MobilityConfig mobilityConfig)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public MobilityLinkMetrics estimateLinkMetrics( SystemSnapshot snapshot, TaskInstance task, NodeCandidate candidate )` | public | Calcola tutte le metriche mobility-aware grezze del collegamento. |
| `public double estimateCoverageTimeSeconds( SystemSnapshot snapshot, TaskInstance task, NodeCandidate candidate )` | public | Stima `estimate coverage time seconds` senza modificare lo stato principale. |
| `public double estimateLinkInstability( SystemSnapshot snapshot, TaskInstance task, NodeCandidate candidate )` | public | Stima `estimate link instability` senza modificare lo stato principale. |
| `private MobilityLinkMetrics estimateInfrastructureMetrics( SystemSnapshot snapshot, TaskInstance task, NodeCandidate candidate )` | private | Stima `estimate infrastructure metrics` senza modificare lo stato principale. |
| `private MobilityLinkMetrics estimateV2vMetrics( SystemSnapshot snapshot, TaskInstance task, NodeCandidate candidate )` | private | Stima `estimate v2v metrics` senza modificare lo stato principale. |
| `private void requireInfrastructureGeometry(NodeCandidate candidate)` | private | Metodo di supporto: realizza il passo `require infrastructure geometry` dentro la responsabilita' della classe. |
| `private VehicleSnapshot findVehicleById( SystemSnapshot snapshot, String vehicleId )` | private | Cerca `find vehicle by id` nelle collezioni o nello stato corrente. |
| `private void validateCandidateForTask( TaskInstance task, NodeCandidate candidate )` | private | Controlla la correttezza di `validate candidate for task` e solleva un'eccezione se trova dati incoerenti. |
| `private double distance( double x1, double y1, double x2, double y2 )` | private | Metodo di supporto: realizza il passo `distance` dentro la responsabilita' della classe. |
| `private double clamp01(double value)` | private | Limita un valore dentro un intervallo ammesso. |

**Problematiche aperte**

- La copertura cloud e' ancora un placeholder stabile: il gateway radio verso cloud non e' modellato esplicitamente.
- La copertura V2V usa differenza scalare di velocita', non vettori direzionali completi.
- La copertura edge usa geometria e velocita' scalare, senza heading del veicolo.

### `MobilityLinkMetrics`

- File: `src/model/mobility/MobilityLinkMetrics.java:14`
- Tipo: `class`
- Nome completo: `model.mobility.MobilityLinkMetrics`

**Cosa fa, in parole semplici**

Metriche geometriche e cinematiche usate per valutare un collegamento. L'oggetto separa i dati grezzi del modello di mobilità dalla fitness. In questo modo il report può spiegare come sono stati ottenuti tempo di copertura e instabilità del collegamento senza ricalcolarli a posteriori. Rende osservabile il modello di mobilita' usato per ogni collegamento.

**Relazione con la formalizzazione**

Implementa la parte mobility-aware: tempo di copertura, instabilita' link, rischio handover e limiti temporali collegati alla copertura.

**Con chi comunica**

Comunica direttamente con: `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final NodeType nodeType`
- `private final ModelMode modelMode`
- `private final double distanceMeters`
- `private final double coverageRadiusMeters`
- `private final double sourceSpeedMetersPerSecond`
- `private final double relativeSpeedMetersPerSecond`
- `private final double coverageTimeSeconds`
- `private final double linkInstability`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public MobilityLinkMetrics( NodeType nodeType, ModelMode modelMode, double distanceMeters, double coverageRadiusMeters, double sourceSpeedMetersPerSecond, double relativeSpeedMetersPerSecond, double coverageTimeSeconds, double linkInstability )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static MobilityLinkMetrics local(double coverageTimeSeconds)` | public | Metodo di supporto: realizza il passo `local` dentro la responsabilita' della classe. |
| `public static MobilityLinkMetrics cloud(double coverageTimeSeconds)` | public | Metodo di supporto: realizza il passo `cloud` dentro la responsabilita' della classe. |
| `public static MobilityLinkMetrics legacy( NodeType nodeType, double coverageTimeSeconds )` | public | Metodo di supporto: realizza il passo `legacy` dentro la responsabilita' della classe. |
| `public NodeType getNodeType()` | public | Restituisce il valore di `NodeType` senza modificarlo. |
| `public ModelMode getModelMode()` | public | Restituisce il valore di `ModelMode` senza modificarlo. |
| `public double getDistanceMeters()` | public | Restituisce il valore di `DistanceMeters` senza modificarlo. |
| `public double getCoverageRadiusMeters()` | public | Restituisce il valore di `CoverageRadiusMeters` senza modificarlo. |
| `public double getSourceSpeedMetersPerSecond()` | public | Restituisce il valore di `SourceSpeedMetersPerSecond` senza modificarlo. |
| `public double getRelativeSpeedMetersPerSecond()` | public | Restituisce il valore di `RelativeSpeedMetersPerSecond` senza modificarlo. |
| `public double getCoverageTimeSeconds()` | public | Restituisce il valore di `CoverageTimeSeconds` senza modificarlo. |
| `public double getLinkInstability()` | public | Restituisce il valore di `LinkInstability` senza modificarlo. |
| `public boolean isCloudStablePlaceholder()` | public | Risponde con true/false alla domanda `is cloud stable placeholder`. |
| `public boolean hasGeometricDistance()` | public | Risponde con true/false alla domanda `has geometric distance`. |
| `public boolean hasCoverageRadius()` | public | Risponde con true/false alla domanda `has coverage radius`. |
| `public boolean hasRelativeSpeed()` | public | Risponde con true/false alla domanda `has relative speed`. |
| `private static double finiteOrZero(double value)` | private | Metodo di supporto: realizza il passo `finite or zero` dentro la responsabilita' della classe. |
| `private static double clamp01(double value)` | private | Limita un valore dentro un intervallo ammesso. |

**Problematiche aperte**

- I valori `CLOUD_STABLE_PLACEHOLDER` e `V2V_SCALAR_RELATIVE_SPEED` indicano approssimazioni ancora da sostituire con dati piu' ricchi.

### `ModelMode` (tipo interno di `MobilityLinkMetrics`)

- File: `src/model/mobility/MobilityLinkMetrics.java:19`
- Tipo: `enum`
- Nome completo: `model.mobility.MobilityLinkMetrics.ModelMode`

**Cosa fa, in parole semplici**

Modalità usata per stimare le metriche del collegamento.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Valori enum principali:
`LOCAL_CONVENTIONAL`, `EDGE_GEOMETRIC`, `V2V_SCALAR_RELATIVE_SPEED`, `CLOUD_STABLE_PLACEHOLDER`, `LEGACY_UNAVAILABLE`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `model.node`

Descrive i tipi e le istanze dei nodi candidati: locale, veicolo, edge, cloud.

### `NodeCandidate`

- File: `src/model/node/NodeCandidate.java:25`
- Tipo: `class`
- Nome completo: `model.node.NodeCandidate`

**Cosa fa, in parole semplici**

Rappresenta una possibile opzione di esecuzione per un task generato da uno specifico veicolo sorgente. Il candidato è source-aware: `sourceVehicleId` indica il veicolo che può usare il candidato; `executionNodeId` indica il nodo fisico che eseguirà il task; `propagationDelaySeconds` rappresenta `tau_n`, cioè il ritardo end-to-end aggregato del percorso remoto. Il ritardo di propagazione esclude i tempi di trasferimento dipendenti dalla banda. Viene conteggiato una sola volta per ogni scelta remota ed è nullo per i candidati locali. Il tempo di copertura non è memorizzato in questa classe. Viene calcolato da una classe dedicata usando veicoli, posizione del nodo, raggio di copertura e, nel caso V2V, veicolo target. Rappresenta un possibile nodo `n_i` per un task: local, vehicle, edge o cloud.

**Relazione con la formalizzazione**

Rappresenta parametri osservati nello stato `S_k`, non variabili decise dal GA.

**Con chi comunica**

Comunica passando dati tra loader, GA, fitness, repair e report.
Di norma non orchestra il flusso: viene letto da altre classi che prendono decisioni.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String candidateId`
- `private final String sourceVehicleId`
- `private final String executionNodeId`
- `private final NodeType type`
- `private final double availableCpu`
- `private final double availableBandwidth`
- `private final double propagationDelaySeconds`
- `private final Double nodeX`
- `private final Double nodeY`
- `private final Double coverageRadiusMeters`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public NodeCandidate( String candidateId, String sourceVehicleId, String executionNodeId, NodeType type, double availableCpu, double availableBandwidth, double propagationDelaySeconds, Double nodeX, Double nodeY, Double coverageRadiusMeters )` | public | Costruisce un candidato di esecuzione source-aware. @param candidateId identificativo univoco del candidato @param sourceVehicleId veicolo sorgente per cui il candidato è valido @param executionNodeId nodo fisico che esegue il task @param type tipo del candidato @param availableCpu CPU disponibile sul nodo di esecuzione @param availableBandwidth banda disponibile sul link sorgente-destinazione @param propagationDelaySeconds ritardo end-to-end aggregato `tau_n` @param nodeX coordinata X del nodo infrastrutturale, se applicabile @param nodeY coordinata Y del nodo infrastrutturale, se applicabile @param coverageRadiusMeters raggio di copertura del nodo, se applicabile |
| `public String getCandidateId()` | public | Restituisce il valore di `CandidateId` senza modificarlo. |
| `public String getNodeId()` | public | Metodo di compatibilità con vecchie parti del codice. @return identificativo del candidato |
| `public String getSourceVehicleId()` | public | Restituisce il valore di `SourceVehicleId` senza modificarlo. |
| `public String getExecutionNodeId()` | public | Restituisce il valore di `ExecutionNodeId` senza modificarlo. |
| `public NodeType getType()` | public | Restituisce il valore di `Type` senza modificarlo. |
| `public double getAvailableCpu()` | public | Restituisce il valore di `AvailableCpu` senza modificarlo. |
| `public double getAvailableBandwidth()` | public | Restituisce il valore di `AvailableBandwidth` senza modificarlo. |
| `public double getPropagationDelaySeconds()` | public | Restituisce `tau_n`: il ritardo end-to-end aggregato del percorso remoto, esclusi i tempi di trasferimento dipendenti dalla banda. @return ritardo fisso conteggiato una volta per ogni scelta remota |
| `public double getBaseLatencySeconds()` | public | Alias mantenuto per compatibilità con il nome storico usato dal JSON e da alcune parti del prototipo. @return lo stesso valore restituito da `#getPropagationDelaySeconds()` @deprecated usare `#getPropagationDelaySeconds()` |
| `public Double getNodeX()` | public | @return coordinata X del nodo, se presente |
| `public Double getNodeY()` | public | @return coordinata Y del nodo, se presente |
| `public Double getCoverageRadiusMeters()` | public | @return raggio di copertura del nodo, se presente |
| `public boolean isLocal()` | public | Risponde con true/false alla domanda `is local`. |
| `public boolean isVehicle()` | public | Risponde con true/false alla domanda `is vehicle`. |
| `public boolean isEdge()` | public | Risponde con true/false alla domanda `is edge`. |
| `public boolean isCloud()` | public | Risponde con true/false alla domanda `is cloud`. |
| `public boolean isRemote()` | public | Risponde con true/false alla domanda `is remote`. |
| `public boolean isInfrastructureCandidate()` | public | @return true se il candidato rappresenta un nodo con geometria fisica |
| `public boolean hasCoverageGeometry()` | public | @return true se posizione e raggio sono disponibili |
| `public boolean isValidForSourceVehicle(String vehicleId)` | public | Verifica se il candidato è utilizzabile dal veicolo sorgente indicato. @param vehicleId veicolo sorgente da controllare @return true se il candidato è valido per quel veicolo |
| `private static String requireText(String value, String fieldName)` | private | Metodo di supporto: realizza il passo `require text` dentro la responsabilita' della classe. |
| `private static double validateFiniteNonNegative( String fieldName, double value )` | private | Controlla la correttezza di `validate finite non negative` e solleva un'eccezione se trova dati incoerenti. |
| `private static Double validateOptionalFinite(String fieldName, Double value)` | private | Controlla la correttezza di `validate optional finite` e solleva un'eccezione se trova dati incoerenti. |
| `private static Double validateOptionalPositive(String fieldName, Double value)` | private | Controlla la correttezza di `validate optional positive` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `NodeType`

- File: `src/model/node/NodeType.java:3`
- Tipo: `enum`
- Nome completo: `model.node.NodeType`

**Cosa fa, in parole semplici**

Enum: rappresenta un insieme chiuso di valori usati per rendere esplicite le scelte del modello.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`LOCAL`, `VEHICLE`, `EDGE`, `CLOUD`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `model.offloading`

Implementa le formule temporali di esecuzione locale, remota e parziale.

### `OffloadingTimeBreakdown`

- File: `src/model/offloading/OffloadingTimeBreakdown.java:17`
- Tipo: `class`
- Nome completo: `model.offloading.OffloadingTimeBreakdown`

**Cosa fa, in parole semplici**

Componenti temporali di una decisione di offloading. Il breakdown segue la formalizzazione del gene: ramo locale, ramo remoto, latenza di comunicazione e tempo finale di completamento. Per una scelta parzialmente remota, `downloadTimeSeconds` rappresenta il download integrale del risultato remoto. Non scala con la quota di offloading quando `p > 0`. `propagationDelaySeconds` rappresenta `tau_n`: il ritardo end-to-end aggregato del percorso remoto. Il valore viene conteggiato una sola volta per ogni scelta remota ed è indipendente dalla quota `p`. Conserva i pezzi temporali calcolati dal modello di offloading.

**Relazione con la formalizzazione**

Implementa le formule dei tempi `T_i(C)`, rami locale/remoto e latenza comunicativa.

**Con chi comunica**

Comunica passando dati tra loader, GA, fitness, repair e report.
Di norma non orchestra il flusso: viene letto da altre classi che prendono decisioni.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double offloadingRatio`
- `private final double localCpuCycles`
- `private final double localExecutionTimeSeconds`
- `private final double uploadTimeSeconds`
- `private final double remoteExecutionTimeSeconds`
- `private final double downloadTimeSeconds`
- `private final double propagationDelaySeconds`
- `private final double remotePartTimeSeconds`
- `private final double communicationLatencySeconds`
- `private final double completionTimeSeconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `OffloadingTimeBreakdown( double offloadingRatio, double localCpuCycles, double localExecutionTimeSeconds, double uploadTimeSeconds, double remoteExecutionTimeSeconds, double downloadTimeSeconds, double propagationDelaySeconds, double remotePartTimeSeconds, double communicationLatencySeconds, double completionTimeSeconds )` | package-private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public double getOffloadingRatio()` | public | Restituisce il valore di `OffloadingRatio` senza modificarlo. |
| `public double getLocalCpuCycles()` | public | Restituisce il valore di `LocalCpuCycles` senza modificarlo. |
| `public double getLocalExecutionTimeSeconds()` | public | Restituisce il valore di `LocalExecutionTimeSeconds` senza modificarlo. |
| `public double getUploadTimeSeconds()` | public | Restituisce il valore di `UploadTimeSeconds` senza modificarlo. |
| `public double getRemoteExecutionTimeSeconds()` | public | Restituisce il valore di `RemoteExecutionTimeSeconds` senza modificarlo. |
| `public double getDownloadTimeSeconds()` | public | Restituisce il valore di `DownloadTimeSeconds` senza modificarlo. |
| `public double getPropagationDelaySeconds()` | public | Restituisce tau_n: il ritardo end-to-end aggregato del percorso remoto. |
| `public double getBaseLatencySeconds()` | public | Alias mantenuto per compatibilità con chiamanti precedenti. @deprecated usare `#getPropagationDelaySeconds()` |
| `public double getRemotePartTimeSeconds()` | public | Restituisce il valore di `RemotePartTimeSeconds` senza modificarlo. |
| `public double getCommunicationLatencySeconds()` | public | Restituisce il valore di `CommunicationLatencySeconds` senza modificarlo. |
| `public double getCompletionTimeSeconds()` | public | Restituisce il valore di `CompletionTimeSeconds` senza modificarlo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `OffloadingTimeModel`

- File: `src/model/offloading/OffloadingTimeModel.java:31`
- Tipo: `class`
- Nome completo: `model.offloading.OffloadingTimeModel`

**Cosa fa, in parole semplici**

Modello unico dei tempi di offloading. La formalizzazione usa una quota remota `p_i`. Per una scelta parzialmente remota, upload ed esecuzione remota scalano con `p_i`, mentre il risultato remoto viene restituito integralmente: T_local(p) = (1 - p) * cycles / f_local T_remote(p) = p * input / b + p * cycles / f + output / b + tau_n T_i = max(T_local, T_remote) se 0 Per i geni locali vale `p = 0`, la banda è nulla e il tempo è `cycles / f_local`. `tau_n` è interpretato come ritardo end-to-end aggregato del percorso remoto. Il valore esclude i tempi di trasferimento dipendenti dalla banda, viene conteggiato una sola volta per ogni scelta remota e non scala con `p_i`. Implementa le formule `Tlocal`, `Tremote` e `T_i`.

**Relazione con la formalizzazione**

Implementa le formule dei tempi `T_i(C)`, rami locale/remoto e latenza comunicativa.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `public static final double EPSILON = 1.0E-9`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public OffloadingTimeBreakdown evaluateLocal( TaskInstance task, double localCpu )` | public | Valuta una scelta locale. @param task task da eseguire @param localCpu CPU locale disponibile @return breakdown temporale locale |
| `public OffloadingTimeBreakdown evaluateRemote( TaskInstance task, NodeCandidate candidate, double localCpu, double offloadingRatio, double allocatedCpu, double allocatedBandwidth )` | public | Valuta una scelta remota o parzialmente remota. Quando `p > 0`, il download usa l'output remoto integrale. Questo riflette l'ipotesi della formalizzazione: il risultato della porzione remota è necessario per ricomporre l'esito complessivo del task. @param task task da eseguire @param candidate candidato remoto scelto @param localCpu CPU locale del veicolo sorgente @param offloadingRatio quota remota `p_i` @param allocatedCpu CPU assegnata al ramo remoto @param allocatedBandwidth banda assegnata alla comunicazione remota @return breakdown temporale completo |
| `public double estimateLocalOnlyTime( TaskInstance task, VehicleSnapshot sourceVehicle )` | public | Stima il tempo locale puro, cioè il caso `p = 0`. |
| `public double estimateLocalBranchTime( TaskInstance task, VehicleSnapshot sourceVehicle, double offloadingRatio )` | public | Stima il ramo locale per una quota remota arbitraria. |
| `public double estimateRemoteScaledTime( TaskInstance task, NodeCandidate candidate )` | public | Stima la componente del ramo remoto che scala con `p`: upload dell'input ed esecuzione remota. |
| `public double estimateRemoteFixedTime( TaskInstance task, NodeCandidate candidate )` | public | Stima la componente fissa del ramo remoto per ogni `p > 0`: download integrale dell'output e ritardo di propagazione aggregato. |
| `public double estimateRemoteLinearTime( TaskInstance task, NodeCandidate candidate )` | public | Mantiene compatibilità con chiamanti precedenti. Restituisce il costo remoto completo per `p = 1`, senza ritardo di propagazione. Non deve essere moltiplicato nuovamente per `p` nelle euristiche del partial offloading. |
| `public double estimateCompletionWithCandidateCapacity( TaskInstance task, NodeCandidate candidate, VehicleSnapshot sourceVehicle, double offloadingRatio )` | public | Stima il completion time usando la quota remota e le risorse massime del candidato. |
| `private double safeDivide(double numerator, double denominator)` | private | Esegue un'operazione protetta per evitare valori non finiti o divisioni non valide. |
| `private double safeNonNegative(double value)` | private | Esegue un'operazione protetta per evitare valori non finiti o divisioni non valide. |
| `private double clamp01(double value)` | private | Limita un valore dentro un intervallo ammesso. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `model.snapshot`

Rappresenta la fotografia del sistema nella finestra corrente.

### `SystemSnapshot`

- File: `src/model/snapshot/SystemSnapshot.java:14`
- Tipo: `class`
- Nome completo: `model.snapshot.SystemSnapshot`

**Cosa fa, in parole semplici**

Fotografia dello stato del sistema in un istante simulato. Contiene veicoli, task attivi e candidati di esecuzione disponibili per quello specifico tempo. La classe resta mutabile per supportare la deserializzazione e la composizione degli snapshot filtrati. Rappresenta lo stato `S_k` dato al GA.

**Relazione con la formalizzazione**

Rappresenta parametri osservati nello stato `S_k`, non variabili decise dal GA.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private String snapshotId`
- `private double timeSeconds`
- `private List<VehicleSnapshot> vehicles`
- `private List<TaskInstance> tasks`
- `private List<NodeCandidate> candidateNodes`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public SystemSnapshot()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public SystemSnapshot( String snapshotId, double timeSeconds, List<VehicleSnapshot> vehicles, List<TaskInstance> tasks, List<NodeCandidate> candidateNodes )` | public | Crea uno snapshot completo. @param snapshotId identificativo dello snapshot @param timeSeconds tempo simulato associato allo snapshot @param vehicles veicoli presenti nello scenario @param tasks task attivi nello scenario @param candidateNodes candidati di esecuzione disponibili |
| `public String getSnapshotId()` | public | Restituisce il valore di `SnapshotId` senza modificarlo. |
| `public void setSnapshotId(String snapshotId)` | public | Aggiorna il valore di `SnapshotId` nell'oggetto. |
| `public double getTimeSeconds()` | public | Restituisce il valore di `TimeSeconds` senza modificarlo. |
| `public void setTimeSeconds(double timeSeconds)` | public | Aggiorna il valore di `TimeSeconds` nell'oggetto. |
| `public List<VehicleSnapshot> getVehicles()` | public | Restituisce il valore di `Vehicles` senza modificarlo. |
| `public void setVehicles(List<VehicleSnapshot> vehicles)` | public | Aggiorna il valore di `Vehicles` nell'oggetto. |
| `public List<TaskInstance> getTasks()` | public | Restituisce il valore di `Tasks` senza modificarlo. |
| `public void setTasks(List<TaskInstance> tasks)` | public | Aggiorna il valore di `Tasks` nell'oggetto. |
| `public List<NodeCandidate> getCandidateNodes()` | public | Restituisce il valore di `CandidateNodes` senza modificarlo. |
| `public void setCandidateNodes(List<NodeCandidate> candidateNodes)` | public | Aggiorna il valore di `CandidateNodes` nell'oggetto. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TaskInstance`

- File: `src/model/snapshot/TaskInstance.java:9`
- Tipo: `class`
- Nome completo: `model.snapshot.TaskInstance`

**Cosa fa, in parole semplici**

Task computazionale generato da un veicolo sorgente. Il task descrive il carico da decidere nel cromosoma: dati da trasferire, cicli CPU richiesti e deadline massima ammessa. Rappresenta i parametri del task i: input, output, cicli, deadline.

**Relazione con la formalizzazione**

Rappresenta parametri osservati nello stato `S_k`, non variabili decise dal GA.

**Con chi comunica**

Comunica passando dati tra loader, GA, fitness, repair e report.
Di norma non orchestra il flusso: viene letto da altre classi che prendono decisioni.

**Campi o valori importanti**

Campi dichiarati principali:
- `private String taskId`
- `private String sourceVehicleId`
- `private double inputSizeBits`
- `private double outputSizeBits`
- `private double cpuCycles`
- `private double deadlineSeconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TaskInstance()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public TaskInstance( String taskId, String sourceVehicleId, double inputSizeBits, double outputSizeBits, double cpuCycles, double deadlineSeconds )` | public | Crea un task computazionale. @param taskId identificativo del task @param sourceVehicleId veicolo che genera il task @param inputSizeBits dimensione dell'input da trasferire @param outputSizeBits dimensione dell'output prodotto @param cpuCycles cicli CPU richiesti @param deadlineSeconds deadline massima in secondi |
| `public String getTaskId()` | public | Restituisce il valore di `TaskId` senza modificarlo. |
| `public void setTaskId(String taskId)` | public | Aggiorna il valore di `TaskId` nell'oggetto. |
| `public String getSourceVehicleId()` | public | Restituisce il valore di `SourceVehicleId` senza modificarlo. |
| `public void setSourceVehicleId(String sourceVehicleId)` | public | Aggiorna il valore di `SourceVehicleId` nell'oggetto. |
| `public double getInputSizeBits()` | public | Restituisce il valore di `InputSizeBits` senza modificarlo. |
| `public void setInputSizeBits(double inputSizeBits)` | public | Aggiorna il valore di `InputSizeBits` nell'oggetto. |
| `public double getOutputSizeBits()` | public | Restituisce il valore di `OutputSizeBits` senza modificarlo. |
| `public void setOutputSizeBits(double outputSizeBits)` | public | Aggiorna il valore di `OutputSizeBits` nell'oggetto. |
| `public double getCpuCycles()` | public | Restituisce il valore di `CpuCycles` senza modificarlo. |
| `public void setCpuCycles(double cpuCycles)` | public | Aggiorna il valore di `CpuCycles` nell'oggetto. |
| `public double getDeadlineSeconds()` | public | Restituisce il valore di `DeadlineSeconds` senza modificarlo. |
| `public void setDeadlineSeconds(double deadlineSeconds)` | public | Aggiorna il valore di `DeadlineSeconds` nell'oggetto. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `VehicleSnapshot`

- File: `src/model/snapshot/VehicleSnapshot.java:9`
- Tipo: `class`
- Nome completo: `model.snapshot.VehicleSnapshot`

**Cosa fa, in parole semplici**

Stato sintetico di un veicolo nello snapshot. La posizione e la velocità alimentano le stime di copertura, mentre `localCpu` rappresenta la capacità disponibile per esecuzione locale. Rappresenta un veicolo osservato nello snapshot: posizione, velocita', CPU locale.

**Relazione con la formalizzazione**

Rappresenta parametri osservati nello stato `S_k`, non variabili decise dal GA.

**Con chi comunica**

Comunica passando dati tra loader, GA, fitness, repair e report.
Di norma non orchestra il flusso: viene letto da altre classi che prendono decisioni.

**Campi o valori importanti**

Campi dichiarati principali:
- `private String vehicleId`
- `private double x`
- `private double y`
- `private double speed`
- `private double localCpu`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public VehicleSnapshot()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public VehicleSnapshot( String vehicleId, double x, double y, double speed, double localCpu )` | public | Crea lo stato di un veicolo. @param vehicleId identificativo del veicolo @param x coordinata X @param y coordinata Y @param speed velocità scalare @param localCpu CPU locale disponibile |
| `public String getVehicleId()` | public | Restituisce il valore di `VehicleId` senza modificarlo. |
| `public void setVehicleId(String vehicleId)` | public | Aggiorna il valore di `VehicleId` nell'oggetto. |
| `public double getX()` | public | Restituisce il valore di `X` senza modificarlo. |
| `public void setX(double x)` | public | Aggiorna il valore di `X` nell'oggetto. |
| `public double getY()` | public | Restituisce il valore di `Y` senza modificarlo. |
| `public void setY(double y)` | public | Aggiorna il valore di `Y` nell'oggetto. |
| `public double getSpeed()` | public | Restituisce il valore di `Speed` senza modificarlo. |
| `public void setSpeed(double speed)` | public | Aggiorna il valore di `Speed` nell'oggetto. |
| `public double getLocalCpu()` | public | Restituisce il valore di `LocalCpu` senza modificarlo. |
| `public void setLocalCpu(double localCpu)` | public | Aggiorna il valore di `LocalCpu` nell'oggetto. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `validation.snapshot`

Valida snapshot grezzi e invarianti necessari al repair.

### `LocalCandidateInvariantValidator`

- File: `src/validation/snapshot/LocalCandidateInvariantValidator.java:23`
- Tipo: `class`
- Nome completo: `validation.snapshot.LocalCandidateInvariantValidator`

**Cosa fa, in parole semplici**

Valida l'invariante necessario al fallback locale del MA-GA. Ogni task deve disporre di un candidato `NodeType#LOCAL` valido per il proprio veicolo sorgente. Il repair usa questa alternativa quando una scelta remota non è più sostenibile. Senza tale candidato, un fallback locale rischierebbe di mantenere l'identificativo di un nodo remoto e produrre un gene semanticamente incoerente. La classe è separata dal validator JSON generale perché l'invariante deve poter essere riutilizzato anche da sorgenti future diverse dai file JSON, incluso il bridge MOSAIC. Garantisce che ogni task abbia un fallback locale valido, necessario al repair.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`, `NodeType`, `SystemSnapshot`, `TaskInstance`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public void validate(SystemSnapshot snapshot)` | public | Verifica che ogni task abbia il proprio candidato locale. @param snapshot snapshot da validare @throws IllegalArgumentException se almeno un task non dispone del fallback locale richiesto |
| `private boolean hasLocalCandidate(SystemSnapshot snapshot, TaskInstance task)` | private | Risponde con true/false alla domanda `has local candidate`. |

**Problematiche aperte**

- Ogni sorgente futura, incluso MOSAIC, deve produrre candidati LOCAL coerenti per non rompere il fallback del repair.

### `SnapshotValidator`

- File: `src/validation/snapshot/SnapshotValidator.java:27`
- Tipo: `class`
- Nome completo: `validation.snapshot.SnapshotValidator`

**Cosa fa, in parole semplici**

Punto unico di validazione degli snapshot. La validazione puo' avvenire prima del mapping, sui DTO grezzi letti dal JSON, oppure sul domain model per compatibilita' con i chiamanti esistenti. Le regole applicate restano le stesse: campi obbligatori, valori numerici, unicita' degli ID, riferimenti e vincoli per tipo di candidato.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`, `NodeCandidateInputDto`, `NodeType`, `SnapshotInputDto`, `SystemSnapshot`, `TaskInputDto`, `TaskInstance`, `VehicleInputDto`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public void validate(SnapshotInputDto dto)` | public | Valida lo snapshot grezzo prima della costruzione del domain model. @param dto snapshot letto dal JSON o da un adapter esterno |
| `public void validate(SystemSnapshot snapshot)` | public | Valida uno snapshot gia' costruito. @param snapshot snapshot da controllare |
| `private void validateSnapshot( String snapshotId, Double timeSeconds, List<VehicleData> vehicles, List<TaskData> tasks, List<CandidateData> candidates )` | private | Controlla la correttezza di `validate snapshot` e solleva un'eccezione se trova dati incoerenti. |
| `private List<VehicleData> toVehicleDataFromDto( List<VehicleInputDto> vehicles )` | private | Metodo di supporto: realizza il passo `to vehicle data from dto` dentro la responsabilita' della classe. |
| `private List<VehicleData> toVehicleDataFromModel( List<VehicleSnapshot> vehicles )` | private | Metodo di supporto: realizza il passo `to vehicle data from model` dentro la responsabilita' della classe. |
| `private List<TaskData> toTaskDataFromDto(List<TaskInputDto> tasks)` | private | Metodo di supporto: realizza il passo `to task data from dto` dentro la responsabilita' della classe. |
| `private List<TaskData> toTaskDataFromModel(List<TaskInstance> tasks)` | private | Metodo di supporto: realizza il passo `to task data from model` dentro la responsabilita' della classe. |
| `private List<CandidateData> toCandidateDataFromDto( List<NodeCandidateInputDto> candidates )` | private | Metodo di supporto: realizza il passo `to candidate data from dto` dentro la responsabilita' della classe. |
| `private List<CandidateData> toCandidateDataFromModel( List<NodeCandidate> candidates )` | private | Metodo di supporto: realizza il passo `to candidate data from model` dentro la responsabilita' della classe. |
| `private <T> List<T> requireList(List<T> list, String name)` | private | Metodo di supporto: realizza il passo `require list` dentro la responsabilita' della classe. |
| `private <T> T requireElement(T element, String listName, int index)` | private | Metodo di supporto: realizza il passo `require element` dentro la responsabilita' della classe. |
| `private void validateVehicleIds(List<VehicleData> vehicles)` | private | Controlla la correttezza di `validate vehicle ids` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateTaskIds(List<TaskData> tasks)` | private | Controlla la correttezza di `validate task ids` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateCandidateIds(List<CandidateData> candidates)` | private | Controlla la correttezza di `validate candidate ids` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateCandidateTypes(List<CandidateData> candidates)` | private | Controlla la correttezza di `validate candidate types` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateVehicleNumericFields(List<VehicleData> vehicles)` | private | Controlla la correttezza di `validate vehicle numeric fields` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateTaskRequiredFields(List<TaskData> tasks)` | private | Controlla la correttezza di `validate task required fields` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateTaskNumericFields(List<TaskData> tasks)` | private | Controlla la correttezza di `validate task numeric fields` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateCandidateRequiredFields( List<CandidateData> candidates )` | private | Controlla la correttezza di `validate candidate required fields` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateCandidateNumericFields( List<CandidateData> candidates )` | private | Controlla la correttezza di `validate candidate numeric fields` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateTasksReferenceExistingVehicles( List<TaskData> tasks, List<VehicleData> vehicles )` | private | Controlla la correttezza di `validate tasks reference existing vehicles` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateCandidatesReferenceExistingSourceVehicles( List<CandidateData> candidates, List<VehicleData> vehicles )` | private | Controlla la correttezza di `validate candidates reference existing source vehicles` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateCandidateSemanticRules( List<CandidateData> candidates, List<VehicleData> vehicles )` | private | Controlla la correttezza di `validate candidate semantic rules` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateLocalCandidate(CandidateData candidate)` | private | Controlla la correttezza di `validate local candidate` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateVehicleCandidate( CandidateData candidate, Set<String> vehicleIds )` | private | Controlla la correttezza di `validate vehicle candidate` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateEdgeCandidate(CandidateData candidate)` | private | Controlla la correttezza di `validate edge candidate` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateCloudCandidate(CandidateData candidate)` | private | Controlla la correttezza di `validate cloud candidate` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateEachTaskHasValidCandidates( List<TaskData> tasks, List<CandidateData> candidates )` | private | Controlla la correttezza di `validate each task has valid candidates` e solleva un'eccezione se trova dati incoerenti. |
| `private Set<String> collectVehicleIds(List<VehicleData> vehicles)` | private | Metodo di supporto: realizza il passo `collect vehicle ids` dentro la responsabilita' della classe. |
| `private NodeType parseNodeType(String rawType, String candidateId)` | private | Interpreta input testuale o grezzo e lo trasforma in un valore usabile dal codice. |
| `private void requireText(String value, String fieldName)` | private | Metodo di supporto: realizza il passo `require text` dentro la responsabilita' della classe. |
| `private void requireFinite( Double value, String fieldName, String ownerId )` | private | Metodo di supporto: realizza il passo `require finite` dentro la responsabilita' della classe. |
| `private void requireNonNegativeFinite( Double value, String fieldName, String ownerId )` | private | Metodo di supporto: realizza il passo `require non negative finite` dentro la responsabilita' della classe. |
| `private void requirePositiveFinite( Double value, String fieldName, String ownerId )` | private | Metodo di supporto: realizza il passo `require positive finite` dentro la responsabilita' della classe. |
| `private void validateOptionalFinite( Double value, String fieldName, String ownerId )` | private | Controlla la correttezza di `validate optional finite` e solleva un'eccezione se trova dati incoerenti. |
| `private void validateOptionalPositive( Double value, String fieldName, String ownerId )` | private | Controlla la correttezza di `validate optional positive` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `VehicleData` (tipo interno di `SnapshotValidator`)

- File: `src/validation/snapshot/SnapshotValidator.java:712`
- Tipo: `class`
- Nome completo: `validation.snapshot.SnapshotValidator.VehicleData`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`, `NodeCandidateInputDto`, `NodeType`, `SnapshotInputDto`, `SystemSnapshot`, `TaskInputDto`, `TaskInstance`, `VehicleInputDto`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String vehicleId`
- `private final Double x`
- `private final Double y`
- `private final Double speed`
- `private final Double localCpu`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private VehicleData( String vehicleId, Double x, Double y, Double speed, Double localCpu )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TaskData` (tipo interno di `SnapshotValidator`)

- File: `src/validation/snapshot/SnapshotValidator.java:735`
- Tipo: `class`
- Nome completo: `validation.snapshot.SnapshotValidator.TaskData`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`, `NodeCandidateInputDto`, `NodeType`, `SnapshotInputDto`, `SystemSnapshot`, `TaskInputDto`, `TaskInstance`, `VehicleInputDto`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String taskId`
- `private final String sourceVehicleId`
- `private final Double inputSizeBits`
- `private final Double outputSizeBits`
- `private final Double cpuCycles`
- `private final Double deadlineSeconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private TaskData( String taskId, String sourceVehicleId, Double inputSizeBits, Double outputSizeBits, Double cpuCycles, Double deadlineSeconds )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `CandidateData` (tipo interno di `SnapshotValidator`)

- File: `src/validation/snapshot/SnapshotValidator.java:761`
- Tipo: `class`
- Nome completo: `validation.snapshot.SnapshotValidator.CandidateData`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`, `NodeCandidateInputDto`, `NodeType`, `SnapshotInputDto`, `SystemSnapshot`, `TaskInputDto`, `TaskInstance`, `VehicleInputDto`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String candidateId`
- `private final String sourceVehicleId`
- `private final String executionNodeId`
- `private final String rawType`
- `private NodeType type`
- `private final Double availableCpu`
- `private final Double availableBandwidth`
- `private final Double baseLatencySeconds`
- `private final Double nodeX`
- `private final Double nodeY`
- `private final Double coverageRadiusMeters`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private CandidateData( String candidateId, String sourceVehicleId, String executionNodeId, String rawType, NodeType type, Double availableCpu, Double availableBandwidth, Double baseLatencySeconds, Double nodeX, Double nodeY, Double coverageRadiusMeters )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `private static CandidateData fromInput( String candidateId, String sourceVehicleId, String executionNodeId, String rawType, Double availableCpu, Double availableBandwidth, Double baseLatencySeconds, Double nodeX, Double nodeY, Double coverageRadiusMeters )` | private | Metodo di supporto: realizza il passo `from input` dentro la responsabilita' della classe. |
| `private static CandidateData fromModel( String candidateId, String sourceVehicleId, String executionNodeId, NodeType type, Double availableCpu, Double availableBandwidth, Double baseLatencySeconds, Double nodeX, Double nodeY, Double coverageRadiusMeters )` | private | Metodo di supporto: realizza il passo `from model` dentro la responsabilita' della classe. |
| `private boolean hasCoverageGeometry()` | private | Risponde con true/false alla domanda `has coverage geometry`. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.core`

Orchestra il ciclo temporale completo.

### `TemporalWindowManager`

- File: `src/window/core/TemporalWindowManager.java:40`
- Tipo: `class`
- Nome completo: `window.core.TemporalWindowManager`

**Cosa fa, in parole semplici**

Orchestratore del ciclo temporale del MA-GA. Il manager distingue lo snapshot fisico osservato dallo snapshot filtrato destinato all'ottimizzazione. Dinamicità e bounds temporali devono descrivere lo scenario reale. Population adapter e GA lavorano invece sulla vista ridotta dal prefilter.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `AdaptiveWindowController`, `AdaptiveWindowDecision`, `Chromosome`, `CoverageReferenceCalculator`, `CriticalEventDetector`, `DynamicityBreakdown`, `DynamicityEvaluator`, `MaGaOptimizer`, `MaGaResult`, `MobilityConfig`, `PopulationAdapter`, `PopulationReuseDecision`, `PopulationReuseDecisionPolicy`, `PopulationReuseMode`, `ReoptimizationTrigger`, `SystemSnapshot`, ... altri 9.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final TemporalWindowConfig windowConfig`
- `private final MaGaOptimizer optimizer`
- `private final DynamicityEvaluator dynamicityEvaluator`
- `private final PopulationAdapter populationAdapter`
- `private final PopulationReuseDecisionPolicy reuseDecisionPolicy`
- `private final AdaptiveWindowController adaptiveWindowController`
- `private final CriticalEventDetector criticalEventDetector`
- `private final SystemStateSource systemStateSource`
- `private final int targetPopulationSize`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TemporalWindowManager( TemporalWindowConfig windowConfig, MaGaOptimizer optimizer, DynamicityEvaluator dynamicityEvaluator, PopulationAdapter populationAdapter, CriticalEventDetector criticalEventDetector, SystemStateSource systemStateSource, int targetPopulationSize )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public TemporalWindowManager( TemporalWindowConfig windowConfig, MaGaOptimizer optimizer, DynamicityEvaluator dynamicityEvaluator, PopulationAdapter populationAdapter, PopulationReuseDecisionPolicy reuseDecisionPolicy, AdaptiveWindowController adaptiveWindowController, CriticalEventDetector criticalEventDetector, SystemStateSource systemStateSource, int targetPopulationSize )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `private static AdaptiveWindowController defaultAdaptiveWindowController( TemporalWindowConfig config, MobilityConfig mobilityConfig )` | private | Metodo di supporto: realizza il passo `default adaptive window controller` dentro la responsabilita' della classe. |
| `private static MobilityConfig optimizerMobilityConfig(MaGaOptimizer optimizer)` | private | Metodo di supporto: realizza il passo `optimizer mobility config` dentro la responsabilita' della classe. |
| `public TemporalWindowResult run(double startTimeSeconds, int maxSteps)` | public | Metodo di supporto: realizza il passo `run` dentro la responsabilita' della classe. |
| `public TemporalStepResult executeNextStepOrNull(TemporalWindowState state)` | public | Metodo di supporto: realizza il passo `execute next step or null` dentro la responsabilita' della classe. |
| `private ReoptimizationTrigger resolveTrigger(TemporalWindowState state)` | private | Metodo di supporto: realizza il passo `resolve trigger` dentro la responsabilita' della classe. |
| `private double computeObservationTime(ReoptimizationTrigger trigger)` | private | Calcola `compute observation time` a partire dai dati ricevuti. |
| `private TemporalOperationalMetrics initialOperationalMetrics()` | private | Metodo di supporto: realizza il passo `initial operational metrics` dentro la responsabilita' della classe. |
| `private TemporalOperationalMetrics metricsForDecision(TemporalWindowState state)` | private | Metodo di supporto: realizza il passo `metrics for decision` dentro la responsabilita' della classe. |
| `private TemporalOperationalMetrics observedOperationalMetrics(long elapsedNs)` | private | Metodo di supporto: realizza il passo `observed operational metrics` dentro la responsabilita' della classe. |
| `public TemporalWindowConfig getWindowConfig()` | public | Restituisce il valore di `WindowConfig` senza modificarlo. |
| `public int getTargetPopulationSize()` | public | Restituisce il valore di `TargetPopulationSize` senza modificarlo. |
| `public SystemStateSource getSystemStateSource()` | public | Restituisce il valore di `SystemStateSource` senza modificarlo. |
| `private static void validateFiniteAndNonNegative( String fieldName, double value )` | private | Controlla la correttezza di `validate finite and non negative` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

- Il runtime osservato del GA puo' influenzare solo le finestre successive, perche' e' misurato dopo l'esecuzione corrente.
- La dimensione target della popolazione viene fissata all'avvio sul primo snapshot filtrato; lo scaling GA interno puo' comunque variare per snapshot.

## Package `window.dynamicity`

Misura quanto lo scenario cambia tra due finestre.

### `DynamicityBreakdown`

- File: `src/window/dynamicity/DynamicityBreakdown.java:30`
- Tipo: `class`
- Nome completo: `window.dynamicity.DynamicityBreakdown`

**Cosa fa, in parole semplici**

Risultato immutabile del confronto tra due snapshot consecutivi. Questa classe non calcola la dinamicità: conserva il risultato prodotto da `DynamicityEvaluator` in una forma leggibile, validata e facile da usare nel resto del package `window`. Il breakdown mantiene sia i valori numerici intermedi sia la decisione operativa finale. In particolare permette di sapere: quali snapshot sono stati confrontati; a quali istanti temporali appartengono; quanto sono cambiati veicoli, task, risorse e link; qual è la dinamicità globale normalizzata; quale livello qualitativo è stato rilevato; quale modalità di riuso della popolazione è suggerita. Tutte le componenti di variazione sono attese nell'intervallo `[0, 1]`, dove `0` significa nessun cambiamento e `1` significa cambiamento massimo.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `PopulationReuseMode`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String previousSnapshotId`
- `private final String currentSnapshotId`
- `private final double previousSnapshotTimeSeconds`
- `private final double currentSnapshotTimeSeconds`
- `private final double vehicleVariation`
- `private final double taskVariation`
- `private final double resourceVariation`
- `private final double linkVariation`
- `private final double globalDynamicity`
- `private final DynamicityLevel dynamicityLevel`
- `private final PopulationReuseMode suggestedReuseMode`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public DynamicityBreakdown( String previousSnapshotId, String currentSnapshotId, double previousSnapshotTimeSeconds, double currentSnapshotTimeSeconds, double vehicleVariation, double taskVariation, double resourceVariation, double linkVariation, double globalDynamicity, DynamicityLevel dynamicityLevel, PopulationReuseMode suggestedReuseMode )` | public | Costruisce un breakdown completo della dinamicità. Il costruttore valida i campi numerici per impedire la creazione di risultati incoerenti. Gli identificativi e le decisioni qualitative vengono invece conservati così come prodotti dal valutatore, con la sola eccezione dello snapshot corrente e delle decisioni finali, che sono obbligatori. @param previousSnapshotId identificativo dello snapshot precedente, o `null` @param currentSnapshotId identificativo dello snapshot corrente @param previousSnapshotTimeSeconds tempo dello snapshot precedente @param currentSnapshotTimeSeconds tempo dello snapshot corrente @param vehicleVariation variazione dei veicoli in `[0, 1]` @param taskVariation variazione dei task in `[0, 1]` @param resourceVariation variazione delle risorse in `[0, 1]` @param linkVariation variazione dei link in `[0, 1]` @param globalDynamicity dinamicità globale in `[0, 1]` @param dynamicityLevel livello qualitativo associato alla dinamicità @param suggestedReuseMode modalità di riuso suggerita |
| `public static DynamicityBreakdown firstRun( String currentSnapshotId, double currentSnapshotTimeSeconds )` | public | Breakdown speciale per la prima finestra. Nella prima finestra non esiste uno snapshot precedente, quindi la dinamicità non è realmente misurabile. Per convenzione tutte le variazioni numeriche vengono impostate a `0`, mentre il livello è `DynamicityLevel#UNKNOWN` e la modalità suggerita è `PopulationReuseMode#FIRST_RUN`. @param currentSnapshotId identificativo dello snapshot corrente @param currentSnapshotTimeSeconds tempo simulato dello snapshot corrente @return breakdown coerente con la prima esecuzione |
| `public String getPreviousSnapshotId()` | public | @return identificativo dello snapshot precedente, o `null` alla prima esecuzione |
| `public String getCurrentSnapshotId()` | public | @return identificativo dello snapshot corrente |
| `public double getPreviousSnapshotTimeSeconds()` | public | @return tempo simulato dello snapshot precedente |
| `public double getCurrentSnapshotTimeSeconds()` | public | @return tempo simulato dello snapshot corrente |
| `public double getVehicleVariation()` | public | @return variazione dei veicoli in `[0, 1]` |
| `public double getTaskVariation()` | public | @return variazione dei task in `[0, 1]` |
| `public double getResourceVariation()` | public | @return variazione delle risorse in `[0, 1]` |
| `public double getLinkVariation()` | public | @return variazione dei link/candidati in `[0, 1]` |
| `public double getGlobalDynamicity()` | public | @return indice globale di dinamicità in `[0, 1]` |
| `public DynamicityLevel getDynamicityLevel()` | public | @return livello qualitativo della dinamicità |
| `public PopulationReuseMode getSuggestedReuseMode()` | public | @return modalità di riuso della popolazione suggerita |
| `public boolean hasPreviousSnapshot()` | public | Indica se il breakdown è stato calcolato confrontando due snapshot reali. @return `true` se esiste uno snapshot precedente |
| `public boolean isFirstRun()` | public | Indica se questo breakdown rappresenta la prima finestra temporale. @return `true` se la modalità suggerita è `PopulationReuseMode#FIRST_RUN` |
| `public boolean suggestsWarmStart()` | public | @return `true` se il breakdown suggerisce un warm start |
| `public boolean suggestsPartialRestart()` | public | @return `true` se il breakdown suggerisce un partial restart |
| `public boolean suggestsColdStart()` | public | @return `true` se il breakdown suggerisce un cold start |
| `private static void validateFinite(String fieldName, double value)` | private | Valida che un valore numerico sia finito. Questa validazione è usata per campi temporali, che devono essere numeri reali ma non sono metriche normalizzate. |
| `private static void validateRate(String fieldName, double value)` | private | Valida una metrica normalizzata. Le componenti di dinamicità e l'indice globale devono essere sempre valori finiti nell'intervallo `[0, 1]`. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `DynamicityEvaluator`

- File: `src/window/dynamicity/DynamicityEvaluator.java:25`
- Tipo: `class`
- Nome completo: `window.dynamicity.DynamicityEvaluator`

**Cosa fa, in parole semplici**

Orchestratore della valutazione di dinamicità tra due snapshot consecutivi. Questa classe rappresenta la formula globale della formalizzazione: D(k) = lambdaVehicles * Dv(k) + lambdaTasks * Dt(k) + lambdaResources * Dr(k) + lambdaLinks * Dl(k) I dettagli delle singole componenti sono delegati ai calculator dedicati.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `LinkDynamicityCalculator`, `PopulationReuseMode`, `ResourceDynamicityCalculator`, `SystemSnapshot`, `TaskDynamicityCalculator`, `TemporalWindowConfig`, `VehicleDynamicityCalculator`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final TemporalWindowConfig config`
- `private final VehicleDynamicityCalculator vehicleCalculator`
- `private final TaskDynamicityCalculator taskCalculator`
- `private final ResourceDynamicityCalculator resourceCalculator`
- `private final LinkDynamicityCalculator linkCalculator`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public DynamicityEvaluator(TemporalWindowConfig config)` | public | Costruisce il valutatore con i calculator di default. @param config configurazione temporale |
| `public DynamicityEvaluator( TemporalWindowConfig config, VehicleDynamicityCalculator vehicleCalculator, TaskDynamicityCalculator taskCalculator, ResourceDynamicityCalculator resourceCalculator, LinkDynamicityCalculator linkCalculator )` | public | Costruisce il valutatore con calculator espliciti. Utile per test o sostituzioni future di singole componenti. @param config configurazione temporale @param vehicleCalculator calcolatore Dv(k) @param taskCalculator calcolatore Dt(k) @param resourceCalculator calcolatore Dr(k) @param linkCalculator calcolatore Dl(k) |
| `public DynamicityBreakdown evaluate( SystemSnapshot previousSnapshot, SystemSnapshot currentSnapshot )` | public | Confronta due snapshot e produce il breakdown completo. @param previousSnapshot snapshot precedente, nullo solo alla prima finestra @param currentSnapshot snapshot corrente @return breakdown della dinamicità |
| `private double computeGlobalDynamicity( double vehicleVariation, double taskVariation, double resourceVariation, double linkVariation )` | private | Combina le componenti tramite i lambda normalizzati della config. |
| `private DynamicityLevel classify(double globalDynamicity)` | private | Classifica l'indice globale usando thetaLow/thetaHigh. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `DynamicityLevel`

- File: `src/window/dynamicity/DynamicityLevel.java:13`
- Tipo: `enum`
- Nome completo: `window.dynamicity.DynamicityLevel`

**Cosa fa, in parole semplici**

Interpretazione qualitativa dell'indice di dinamicità dello scenario. Il valore numerico della dinamicità viene calcolato da `DynamicityEvaluator`. Questo enum traduce quel valore in una categoria leggibile dal gestore temporale e direttamente collegabile alla modalità di riuso della popolazione.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `PopulationReuseMode`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Valori enum principali:
`UNKNOWN`, `STABLE`, `MODERATE`, `HIGH`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public PopulationReuseMode toReuseMode()` | public | Traduce il livello qualitativo nella strategia operativa di riuso. La mappatura è volutamente concentrata qui, così il resto del package può lavorare con una decisione chiara senza duplicare gli stessi switch. @return modalità di riuso della popolazione suggerita dal livello |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.dynamicity.calculator`

Calcola singole componenti della dinamicita': veicoli, task, risorse, link.

### `LinkDynamicityCalculator`

- File: `src/window/dynamicity/calculator/LinkDynamicityCalculator.java:18`
- Tipo: `class`
- Nome completo: `window.dynamicity.calculator.LinkDynamicityCalculator`

**Cosa fa, in parole semplici**

Calcola Dl(k), cioè la variazione dei link/candidati source-aware. La copertura non viene usata qui perché non è una proprietà statica del NodeCandidate. Viene calcolata separatamente dal CoverageEstimator.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `LinkMetrics`, `MetricMapComparator`, `NodeCandidate`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final MetricMapComparator<LinkMetrics> comparator`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public LinkDynamicityCalculator()` | public | Costruisce il calculator con comparator standard. |
| `public LinkDynamicityCalculator( MetricMapComparator<LinkMetrics> comparator )` | public | Costruisce il calculator con comparator esplicito. @param comparator comparator tra mappe di metriche |
| `public double compute( SystemSnapshot previousSnapshot, SystemSnapshot currentSnapshot )` | public | Calcola la variazione normalizzata dei link/candidati. @param previousSnapshot snapshot precedente @param currentSnapshot snapshot corrente @return Dl(k) in [0, 1] |
| `private Map<String, LinkMetrics> buildLinkMap( SystemSnapshot snapshot )` | private | Costruisce la mappa candidateId -> LinkMetrics. |

**Problematiche aperte**

- `Dl(k)` usa candidateId, banda e latenza; non e' ancora un indicatore radio `q_v(k)` derivato direttamente dal simulatore.

### `ResourceDynamicityCalculator`

- File: `src/window/dynamicity/calculator/ResourceDynamicityCalculator.java:20`
- Tipo: `class`
- Nome completo: `window.dynamicity.calculator.ResourceDynamicityCalculator`

**Cosa fa, in parole semplici**

Calcola Dr(k), cioè la variazione delle risorse computazionali disponibili. La CPU locale è indicizzata per vehicleId. La CPU remota è indicizzata per executionNodeId, perché è una risorsa fisica del nodo di esecuzione e non del singolo candidateId.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`, `NodeType`, `NumericMapComparator`, `SystemSnapshot`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final NumericMapComparator comparator`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public ResourceDynamicityCalculator()` | public | Costruisce il calculator con comparator numerico standard. |
| `public ResourceDynamicityCalculator( NumericMapComparator comparator )` | public | Costruisce il calculator con comparator esplicito. @param comparator comparator numerico |
| `public double compute( SystemSnapshot previousSnapshot, SystemSnapshot currentSnapshot )` | public | Calcola la variazione normalizzata delle risorse. @param previousSnapshot snapshot precedente @param currentSnapshot snapshot corrente @return Dr(k) in [0, 1] |
| `private Map<String, Double> buildResourceMap( SystemSnapshot snapshot )` | private | Costruisce la mappa delle risorse computazionali osservate. |

**Problematiche aperte**

- `Dr(k)` include anche CPU locale dei veicoli; va confermato se nella formalizzazione finale le risorse locali devono pesare allo stesso modo delle remote.

### `TaskDynamicityCalculator`

- File: `src/window/dynamicity/calculator/TaskDynamicityCalculator.java:24`
- Tipo: `class`
- Nome completo: `window.dynamicity.calculator.TaskDynamicityCalculator`

**Cosa fa, in parole semplici**

Calcola Dt(k), cioè la variazione dei task attivi tra due snapshot. Il confronto considera: - task comparsi o scomparsi; - cambio del veicolo sorgente; - input size; - output size; - cicli CPU richiesti; - deadline.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `MetricMapComparator`, `SystemSnapshot`, `TaskInstance`, `TaskMetrics`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final MetricMapComparator<TaskMetrics> comparator`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TaskDynamicityCalculator()` | public | Costruisce il calculator con comparator standard. |
| `public TaskDynamicityCalculator( MetricMapComparator<TaskMetrics> comparator )` | public | Costruisce il calculator con comparator esplicito. @param comparator comparator tra mappe di metriche |
| `public double compute( SystemSnapshot previousSnapshot, SystemSnapshot currentSnapshot )` | public | Calcola la variazione normalizzata dei task. @param previousSnapshot snapshot precedente @param currentSnapshot snapshot corrente @return Dt(k) in [0, 1] |
| `private Map<String, TaskMetrics> buildTaskMap( SystemSnapshot snapshot )` | private | Costruisce la mappa taskId -> TaskMetrics. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `VehicleDynamicityCalculator`

- File: `src/window/dynamicity/calculator/VehicleDynamicityCalculator.java:22`
- Tipo: `class`
- Nome completo: `window.dynamicity.calculator.VehicleDynamicityCalculator`

**Cosa fa, in parole semplici**

Calcola Dv(k), cioè la variazione dei veicoli tra due snapshot. Il confronto non usa solo la presenza degli ID. Per veicoli presenti in entrambi gli snapshot considera anche: - posizione; - velocità; - CPU locale.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `MetricMapComparator`, `SystemSnapshot`, `VehicleMetrics`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final MetricMapComparator<VehicleMetrics> comparator`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public VehicleDynamicityCalculator()` | public | Costruisce il calculator con comparator standard. |
| `public VehicleDynamicityCalculator( MetricMapComparator<VehicleMetrics> comparator )` | public | Costruisce il calculator con comparator esplicito. @param comparator comparator tra mappe di metriche |
| `public double compute( SystemSnapshot previousSnapshot, SystemSnapshot currentSnapshot )` | public | Calcola la variazione normalizzata dei veicoli. @param previousSnapshot snapshot precedente @param currentSnapshot snapshot corrente @return Dv(k) in [0, 1] |
| `private Map<String, VehicleMetrics> buildVehicleMap( SystemSnapshot snapshot )` | private | Costruisce la mappa vehicleId -> VehicleMetrics. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.dynamicity.compare`

Fornisce comparatori generici per mappe di metriche.

### `MetricMapComparator`

- File: `src/window/dynamicity/compare/MetricMapComparator.java:19`
- Tipo: `class`
- Nome completo: `window.dynamicity.compare.MetricMapComparator`

**Cosa fa, in parole semplici**

Comparator generico per mappe di metriche confrontabili. Regola: - chiave presente solo in uno snapshot: variazione 1; - chiave presente in entrambi: relativeVariation(...); - risultato finale: media normalizzata in [0, 1]. @param tipo della metrica confrontabile

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `ComparableMetric`, `DynamicityMath`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public double computeVariation( Map<String, T> previousValues, Map<String, T> currentValues )` | public | Calcola la variazione media tra due mappe. @param previousValues valori precedenti @param currentValues valori correnti @return variazione normalizzata in [0, 1] |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `NumericMapComparator`

- File: `src/window/dynamicity/compare/NumericMapComparator.java:14`
- Tipo: `class`
- Nome completo: `window.dynamicity.compare.NumericMapComparator`

**Cosa fa, in parole semplici**

Comparator per mappe numeriche. Usato per Dr(k), dove le metriche sono capacità computazionali associate a vehicleId o executionNodeId.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `DynamicityMath`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public double computeVariation( Map<String, Double> previousValues, Map<String, Double> currentValues )` | public | Calcola la variazione media tra due mappe numeriche. @param previousValues valori precedenti @param currentValues valori correnti @return variazione normalizzata in [0, 1] |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.dynamicity.math`

Contiene funzioni matematiche comuni per la dinamicita'.

### `DynamicityMath`

- File: `src/window/dynamicity/math/DynamicityMath.java:9`
- Tipo: `class`
- Nome completo: `window.dynamicity.math.DynamicityMath`

**Cosa fa, in parole semplici**

Utility matematica per il calcolo della dinamicità.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private DynamicityMath()` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static double relativeDifference( double previous, double current )` | public | Differenza relativa normalizzata tra due valori. @param previous valore precedente @param current valore corrente @return differenza relativa in [0, 1] |
| `public static double clamp01(double value)` | public | Limita un valore in [0, 1]. @param value valore da normalizzare @return valore limitato |
| `public static Set<String> union( Set<String> first, Set<String> second )` | public | Restituisce l'unione di due insiemi di chiavi. @param first primo insieme @param second secondo insieme @return unione |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.dynamicity.metrics`

Rappresenta metriche confrontabili tra snapshot.

### `ComparableMetric`

- File: `src/window/dynamicity/metrics/ComparableMetric.java:8`
- Tipo: `interface`
- Nome completo: `window.dynamicity.metrics.ComparableMetric`

**Cosa fa, in parole semplici**

Contratto per metriche confrontabili tra due snapshot. @param tipo concreto della metrica

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `double relativeVariation(T other)` | package-private | Calcola la variazione relativa rispetto a una metrica corrente. @param other metrica corrente @return variazione normalizzata in [0, 1] |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `LinkMetrics`

- File: `src/window/dynamicity/metrics/LinkMetrics.java:11`
- Tipo: `class`
- Nome completo: `window.dynamicity.metrics.LinkMetrics`

**Cosa fa, in parole semplici**

Stato confrontabile di un link/candidato source-aware.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `DynamicityMath`, `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String sourceVehicleId`
- `private final String executionNodeId`
- `private final NodeType nodeType`
- `private final double availableBandwidth`
- `private final double baseLatencySeconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public LinkMetrics( String sourceVehicleId, String executionNodeId, NodeType nodeType, double availableBandwidth, double baseLatencySeconds )` | public | Costruisce la metrica link. @param sourceVehicleId veicolo sorgente @param executionNodeId nodo fisico di esecuzione @param nodeType tipo del nodo @param availableBandwidth banda disponibile @param baseLatencySeconds latenza base |
| `public double relativeVariation(LinkMetrics other)` | public | Metodo di supporto: realizza il passo `relative variation` dentro la responsabilita' della classe. |
| `private boolean sameSemanticLink(LinkMetrics other)` | private | Verifica se i due candidati rappresentano la stessa relazione logica. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TaskMetrics`

- File: `src/window/dynamicity/metrics/TaskMetrics.java:10`
- Tipo: `class`
- Nome completo: `window.dynamicity.metrics.TaskMetrics`

**Cosa fa, in parole semplici**

Stato confrontabile di un task.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `DynamicityMath`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String sourceVehicleId`
- `private final double inputSizeBits`
- `private final double outputSizeBits`
- `private final double cpuCycles`
- `private final double deadlineSeconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TaskMetrics( String sourceVehicleId, double inputSizeBits, double outputSizeBits, double cpuCycles, double deadlineSeconds )` | public | Costruisce la metrica task. @param sourceVehicleId veicolo sorgente @param inputSizeBits dimensione input @param outputSizeBits dimensione output @param cpuCycles cicli CPU richiesti @param deadlineSeconds deadline |
| `public double relativeVariation(TaskMetrics other)` | public | Metodo di supporto: realizza il passo `relative variation` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `VehicleMetrics`

- File: `src/window/dynamicity/metrics/VehicleMetrics.java:8`
- Tipo: `class`
- Nome completo: `window.dynamicity.metrics.VehicleMetrics`

**Cosa fa, in parole semplici**

Stato confrontabile di un veicolo.

**Relazione con la formalizzazione**

Realizza l'indice `D(k)` e le sue componenti `Dv`, `Dt`, `Dr`, `Dl`.

**Con chi comunica**

Comunica direttamente con: `DynamicityMath`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double POSITION_REFERENCE_METERS = 250.0`
- `private final double x`
- `private final double y`
- `private final double speed`
- `private final double localCpu`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public VehicleMetrics( double x, double y, double speed, double localCpu )` | public | Costruisce la metrica veicolo. @param x coordinata X @param y coordinata Y @param speed velocità @param localCpu CPU locale |
| `public double relativeVariation(VehicleMetrics other)` | public | Metodo di supporto: realizza il passo `relative variation` dentro la responsabilita' della classe. |
| `private double positionVariation(VehicleMetrics other)` | private | Calcola la variazione spaziale normalizzata. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.event`

Modella gli eventi critici che possono anticipare una riottimizzazione.

### `CriticalEvent`

- File: `src/window/event/CriticalEvent.java:26`
- Tipo: `class`
- Nome completo: `window.event.CriticalEvent`

**Cosa fa, in parole semplici**

Evento critico osservato durante l'esecuzione temporale del sistema. Questa classe separa in modo esplicito tre concetti che nel gestore temporale devono restare distinti: la finestra temporale programmata; l'evento imprevisto che può invalidare la strategia corrente; lo snapshot aggiornato che verrà eventualmente ottimizzato dopo l'evento. Un `CriticalEvent` non è uno snapshot, non contiene lo stato completo del sistema e non esegue il MA-GA. Il suo ruolo è rappresentare il fatto che, a un certo tempo simulato, è accaduto qualcosa che può rendere non più affidabile la strategia corrente di offloading. Dopo un evento critico, il gestore temporale potrà richiedere o selezionare uno `SystemSnapshot` aggiornato e costruire un `ReoptimizationTrigger` di tipo `TriggerReason#CRITICAL_EVENT`.

**Relazione con la formalizzazione**

Rappresenta la riesecuzione anticipata o programmata del ciclo temporale.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String eventId`
- `private final double eventTimeSeconds`
- `private final CriticalEventType type`
- `private final CriticalEventSeverity severity`
- `private final String affectedVehicleId`
- `private final String affectedTaskId`
- `private final String affectedCandidateId`
- `private final String affectedExecutionNodeId`
- `private final String description`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public CriticalEvent( String eventId, double eventTimeSeconds, CriticalEventType type, CriticalEventSeverity severity, String affectedVehicleId, String affectedTaskId, String affectedCandidateId, String affectedExecutionNodeId, String description )` | public | Costruisce un evento critico. I campi `affected*` sono opzionali perché non tutti gli eventi riguardano lo stesso tipo di entità. Il costruttore normalizza le stringhe opzionali vuote a `null`, così il resto del codice può distinguere facilmente tra campo assente e campo valorizzato. Esempi di associazione: `CriticalEventType#VEHICLE_LEFT`: `affectedVehicleId`; `CriticalEventType#TASK_ARRIVAL`: `affectedTaskId`; `CriticalEventType#LINK_DEGRADED`: `affectedCandidateId`; `CriticalEventType#NODE_RESOURCE_DROP`: `affectedExecutionNodeId`. @param eventId identificativo univoco dell'evento @param eventTimeSeconds tempo simulato dell'evento @param type tipologia dell'evento @param severity severità dell'evento @param affectedVehicleId veicolo interessato, opzionale @param affectedTaskId task interessato, opzionale @param affectedCandidateId candidato/link interessato, opzionale @param affectedExecutionNodeId nodo di esecuzione interessato, opzionale @param description descrizione libera opzionale |
| `public static CriticalEvent forVehicle( String eventId, double eventTimeSeconds, CriticalEventType type, CriticalEventSeverity severity, String affectedVehicleId, String description )` | public | Factory method per creare un evento che riguarda un veicolo. Utile per eventi come `CriticalEventType#VEHICLE_JOINED`, `CriticalEventType#VEHICLE_LEFT`, `CriticalEventType#COVERAGE_RISK` o `CriticalEventType#HANDOVER_RISK`. @return evento critico con solo `affectedVehicleId` valorizzato |
| `public static CriticalEvent forTask( String eventId, double eventTimeSeconds, CriticalEventType type, CriticalEventSeverity severity, String affectedTaskId, String description )` | public | Factory method per creare un evento che riguarda un task. Utile per eventi come `CriticalEventType#TASK_ARRIVAL`, `CriticalEventType#TASK_REMOVED` o `CriticalEventType#DEADLINE_RISK`. @return evento critico con solo `affectedTaskId` valorizzato |
| `public static CriticalEvent forCandidate( String eventId, double eventTimeSeconds, CriticalEventType type, CriticalEventSeverity severity, String affectedCandidateId, String description )` | public | Factory method per creare un evento che riguarda un candidato/link. Utile per eventi come `CriticalEventType#LINK_DEGRADED`, `CriticalEventType#LINK_LOST` o `CriticalEventType#COVERAGE_RISK` quando il rischio è riferito a uno specifico `candidateId`. @return evento critico con solo `affectedCandidateId` valorizzato |
| `public static CriticalEvent forExecutionNode( String eventId, double eventTimeSeconds, CriticalEventType type, CriticalEventSeverity severity, String affectedExecutionNodeId, String description )` | public | Factory method per creare un evento che riguarda un nodo fisico di esecuzione. Utile per eventi come `CriticalEventType#NODE_RESOURCE_DROP` o `CriticalEventType#NODE_RESOURCE_RECOVERY`. @return evento critico con solo `affectedExecutionNodeId` valorizzato |
| `public String getEventId()` | public | @return identificativo univoco dell'evento |
| `public double getEventTimeSeconds()` | public | @return tempo simulato dell'evento |
| `public CriticalEventType getType()` | public | @return tipologia dell'evento |
| `public CriticalEventSeverity getSeverity()` | public | @return severità dell'evento |
| `public String getAffectedVehicleId()` | public | @return veicolo interessato, oppure `null` se non applicabile |
| `public String getAffectedTaskId()` | public | @return task interessato, oppure `null` se non applicabile |
| `public String getAffectedCandidateId()` | public | @return candidato/link interessato, oppure `null` se non applicabile |
| `public String getAffectedExecutionNodeId()` | public | @return nodo di esecuzione interessato, oppure `null` se non applicabile |
| `public String getDescription()` | public | @return descrizione opzionale dell'evento, oppure `null` |
| `public boolean shouldTriggerReoptimization()` | public | Indica se l'evento dovrebbe attivare una riesecuzione anticipata. La scelta finale spetta comunque al gestore temporale. Questo metodo fornisce solo una valutazione locale basata sulla severità. @return `true` se la severità è alta o critica |
| `public boolean isMobilityRelated()` | public | Indica se l'evento riguarda la mobilità. Questo metodo è utile nei report e nelle analisi sperimentali. @return `true` se il tipo è classificato come mobility-related |
| `public boolean isTaskRelated()` | public | Indica se l'evento riguarda task o deadline. @return `true` se il tipo è classificato come task-related |
| `public boolean isResourceRelated()` | public | Indica se l'evento riguarda risorse computazionali o comunicative. @return `true` se il tipo è classificato come resource-related |
| `private static String requireText(String value, String fieldName)` | private | Verifica che una stringa obbligatoria sia valorizzata. @return stringa originale se valida |
| `private static String normalizeOptionalText(String value)` | private | Normalizza una stringa opzionale. Le stringhe nulle, vuote o composte solo da spazi vengono trattate come assenza del valore e quindi convertite in `null`. |
| `private static void validateFiniteAndNonNegative( String fieldName, double value )` | private | Valida che un valore numerico sia finito e non negativo. Il tempo simulato dell'evento non può essere infinito, NaN o negativo. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `CriticalEventDetector`

- File: `src/window/event/CriticalEventDetector.java:18`
- Tipo: `interface`
- Nome completo: `window.event.CriticalEventDetector`

**Cosa fa, in parole semplici**

Astrazione per cercare eventi critici in un intervallo temporale. Il `TemporalWindowManager` usa questa interfaccia per capire se la finestra corrente deve terminare prima della sua scadenza programmata. Se viene trovato un evento critico rilevante, la nuova ottimizzazione viene anticipata e il trigger diventa di tipo `TriggerReason#CRITICAL_EVENT`. L'interfaccia permette di separare la logica temporale dalla sorgente degli eventi: nei test gli eventi possono essere statici, mentre in una versione integrata possono arrivare da un simulatore o da un sistema di monitoraggio.

**Relazione con la formalizzazione**

Rappresenta la riesecuzione anticipata o programmata del ciclo temporale.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `Optional<CriticalEvent> findNextCriticalEvent( double currentTimeSeconds, double maxTimeSeconds )` | package-private | Cerca il prossimo evento critico tra currentTimeSeconds e maxTimeSeconds. Per convenzione il tempo corrente è escluso dalla ricerca, mentre il limite superiore è incluso. Questo evita di ri-processare un evento già usato per aprire la finestra corrente, ma consente di intercettare un evento che cade esattamente sulla scadenza programmata. @param currentTimeSeconds tempo corrente escluso dalla ricerca @param maxTimeSeconds limite superiore incluso nella ricerca @return prossimo evento critico, se presente |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `CriticalEventSeverity`

- File: `src/window/event/CriticalEventSeverity.java:14`
- Tipo: `enum`
- Nome completo: `window.event.CriticalEventSeverity`

**Cosa fa, in parole semplici**

Severità di un evento critico osservato dal sistema. La severità non decide da sola tutto il comportamento del gestore temporale, ma fornisce una regola locale per distinguere eventi informativi da eventi abbastanza forti da anticipare la riesecuzione del MA-GA. La scelta finale resta esterna: un eventuale gestore temporale può combinare la severità con altri dati, come tempo residuo della finestra, tipo di evento o snapshot corrente.

**Relazione con la formalizzazione**

Rappresenta la riesecuzione anticipata o programmata del ciclo temporale.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public boolean shouldTriggerReoptimization()` | public | Indica se questa severità è sufficiente per proporre una riesecuzione anticipata. @return `true` per `#HIGH` e `#CRITICAL` |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `CriticalEventType`

- File: `src/window/event/CriticalEventType.java:20`
- Tipo: `enum`
- Nome completo: `window.event.CriticalEventType`

**Cosa fa, in parole semplici**

Tipologie di evento critico rilevabili dal livello di osservazione. Un evento critico non è uno snapshot e non contiene lo stato completo del sistema. È invece un segnale puntuale che può indurre il gestore temporale a richiedere uno snapshot aggiornato e a rieseguire il MA-GA prima della scadenza naturale della finestra. Le tipologie sono organizzate per area semantica: mobilità e copertura dei veicoli; arrivo, rimozione o rischio sui task; degrado o recupero di link e risorse computazionali; eventi personalizzati per simulazioni e test.

**Relazione con la formalizzazione**

Rappresenta la riesecuzione anticipata o programmata del ciclo temporale.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`VEHICLE_JOINED`, `VEHICLE_LEFT`, `TASK_ARRIVAL`, `TASK_REMOVED`, `LINK_DEGRADED`, `LINK_LOST`, `COVERAGE_RISK`, `HANDOVER_RISK`, `NODE_RESOURCE_DROP`, `NODE_RESOURCE_RECOVERY`, `DEADLINE_RISK`, `CUSTOM`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public boolean isMobilityRelated()` | public | Indica se il tipo di evento è collegato a mobilità, copertura o link. La categoria include anche eventi di link perché nel modello source-aware la qualità del collegamento dipende spesso dalla posizione e dalla mobilità dei veicoli coinvolti. @return `true` se l'evento è classificabile come mobility-related |
| `public boolean isTaskRelated()` | public | Indica se il tipo di evento riguarda task, arrivi, rimozioni o deadline. @return `true` se l'evento è classificabile come task-related |
| `public boolean isResourceRelated()` | public | Indica se il tipo di evento riguarda risorse computazionali o comunicative. I link degradati o persi rientrano anche in questa categoria perché banda e latenza sono risorse di comunicazione usate dal fitness. @return `true` se l'evento è classificabile come resource-related |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `StaticCriticalEventDetector`

- File: `src/window/event/StaticCriticalEventDetector.java:22`
- Tipo: `class`
- Nome completo: `window.event.StaticCriticalEventDetector`

**Cosa fa, in parole semplici**

Implementazione statica di `CriticalEventDetector` basata su una lista predefinita di eventi critici. Questa classe è pensata per test e prime simulazioni senza integrazione con MOSAIC o con un monitor runtime. Riceve una lista di eventi, la ordina per tempo simulato e restituisce il primo evento critico rilevante in un intervallo temporale richiesto. Vengono restituiti solo eventi la cui severità richiede una riottimizzazione anticipata, secondo `CriticalEvent#shouldTriggerReoptimization()`.

**Relazione con la formalizzazione**

Rappresenta la riesecuzione anticipata o programmata del ciclo temporale.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final List<CriticalEvent> events`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public StaticCriticalEventDetector(List<CriticalEvent> events)` | public | Crea un detector statico. La lista ricevuta viene copiata, validata e ordinata. La lista interna è immutabile, così le successive modifiche alla lista del chiamante non cambiano il comportamento del detector. @param events eventi disponibili ordinabili per tempo |
| `public static StaticCriticalEventDetector empty()` | public | Crea un detector senza eventi critici. È utile quando si vuole eseguire il ciclo temporale solo con scadenze programmate, senza anticipi dovuti a eventi. @return detector vuoto |
| `public Optional<CriticalEvent> findNextCriticalEvent( double currentTimeSeconds, double maxTimeSeconds )` | public | Restituisce il primo evento nell'intervallo indicato. L'intervallo è aperto a sinistra e chiuso a destra: currentTimeSeconds Vengono considerati solo eventi con severità sufficiente ad attivare una riesecuzione. |
| `public List<CriticalEvent> getEvents()` | public | @return lista immutabile degli eventi ordinati per tempo simulato |
| `public boolean isEmpty()` | public | @return `true` se non sono stati configurati eventi critici |
| `private static void validateTimeRange( double currentTimeSeconds, double maxTimeSeconds )` | private | Valida l'intervallo temporale richiesto al detector. Entrambi gli estremi devono essere finiti e non negativi; il limite superiore non può precedere il tempo corrente. |
| `private static void validateFiniteAndNonNegative( String fieldName, double value )` | private | Valida che un tempo simulato sia finito e non negativo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.population`

Decide e costruisce il riuso della popolazione tra finestre.

### `PopulationAdapter`

- File: `src/window/population/PopulationAdapter.java:38`
- Tipo: `class`
- Nome completo: `window.population.PopulationAdapter`

**Cosa fa, in parole semplici**

Adatta la popolazione genetica finale di una finestra temporale precedente allo snapshot corrente. È il ponte tra il ciclo temporale e l'ottimizzatore snapshot-based: il gestore temporale sceglie una `PopulationReuseMode`, mentre questo adattatore costruisce la popolazione iniziale `P_init(k)` per la nuova finestra. `FIRST_RUN` e `COLD_START`: genera una popolazione nuova; `WARM_START`: prova a riusare tutta la popolazione precedente; `PARTIAL_RESTART`: conserva una quota dei migliori cromosomi e rigenera il resto. Ogni cromosoma riusato viene copiato, riparato e rivalutato rispetto allo snapshot corrente. La fitness storica non decide quali cromosomi conservare nella nuova finestra.

**Relazione con la formalizzazione**

Realizza la regola di riuso della popolazione tra finestre successive.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `FitnessEvaluator`, `Gene`, `MaGaConfig`, `PopulationInitializer`, `RepairOperator`, `SystemSnapshot`, `TemporalWindowConfig`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final TemporalWindowConfig config`
- `private final RepairOperator repairOperator`
- `private final FitnessEvaluator fitnessEvaluator`
- `private final PopulationInitializer populationInitializer`
- `private final Random random`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public PopulationAdapter( TemporalWindowConfig config, MaGaConfig maGaConfig, Random random )` | public | Costruisce un adattatore di popolazione. @param config configurazione temporale contenente, tra gli altri, `rhoKeep` @param maGaConfig configurazione usata da repair e fitness @param random generatore pseudo-casuale condiviso con l'inizializzatore @throws NullPointerException se un parametro richiesto e' `null` |
| `public List<Chromosome> adaptPopulation( List<Chromosome> previousFinalPopulation, SystemSnapshot currentSnapshot, PopulationReuseMode reuseMode, int targetPopulationSize )` | public | Costruisce la popolazione iniziale da passare al MA-GA nella finestra corrente. Il metodo restituisce sempre una popolazione concreta, così il package `window` rende esplicita la strategia scelta prima di invocare `MaGaOptimizer`. @param previousFinalPopulation popolazione finale della finestra precedente @param currentSnapshot snapshot su cui preparare la nuova popolazione @param reuseMode modalità di riuso scelta dal gestore temporale @param targetPopulationSize dimensione desiderata della popolazione iniziale @return popolazione iniziale coerente con lo snapshot corrente |
| `private List<Chromosome> createFreshPopulation( SystemSnapshot snapshot, int targetPopulationSize )` | private | Crea una popolazione nuova da zero. @param snapshot snapshot corrente @param targetPopulationSize dimensione desiderata @return popolazione generata interamente da zero |
| `private List<Chromosome> repairAndSortPreviousPopulation( List<Chromosome> previousFinalPopulation, SystemSnapshot currentSnapshot )` | private | Prepara la popolazione precedente al riuso. Ogni cromosoma storico viene: copiato; riparato rispetto allo snapshot corrente; rivalutato sullo snapshot corrente; ordinato per fitness corrente crescente. La copia evita effetti collaterali sui risultati delle finestre già completate. @param previousFinalPopulation popolazione prodotta nella finestra precedente @param currentSnapshot snapshot corrente usato per riparare i cromosomi @return cromosomi copiati, riparati e ordinati per fitness crescente |
| `private List<Chromosome> buildWarmStartPopulation( List<Chromosome> repairedPreviousPopulation, SystemSnapshot currentSnapshot, int targetPopulationSize )` | private | Costruisce la popolazione per WARM_START. Conserva i migliori cromosomi riparati e genera eventuali cromosomi mancanti, mantenendo la dimensione target richiesta dal GA. |
| `private List<Chromosome> buildPartialRestartPopulation( List<Chromosome> repairedPreviousPopulation, SystemSnapshot currentSnapshot, int targetPopulationSize )` | private | Costruisce la popolazione per PARTIAL_RESTART. Conserva `rhoKeep * targetPopulationSize` cromosomi riparati e genera da zero il resto della popolazione. |
| `private int computePartialRestartKeepCount( int availablePreviousCount, int targetPopulationSize )` | private | Calcola quanti cromosomi mantenere in partial restart. La formula base è `round(rhoKeep * targetPopulationSize)`. Il risultato viene poi limitato per: non superare la popolazione precedente disponibile; non superare la dimensione target; mantenere almeno un cromosoma se `rhoKeep > 0` e se esiste una popolazione precedente non vuota. |
| `private void evaluatePopulation( List<Chromosome> population, SystemSnapshot snapshot )` | private | Rivaluta una popolazione rispetto allo snapshot corrente. |
| `private void fillWithFreshChromosomes( List<Chromosome> result, SystemSnapshot currentSnapshot, int targetPopulationSize )` | private | Completa una popolazione parziale con cromosomi nuovi. È usato sia dal warm start sia dal partial restart quando la porzione riusata non raggiunge ancora la dimensione target. |
| `private void trimToTargetSize( List<Chromosome> chromosomes, int targetPopulationSize )` | private | Taglia la popolazione alla dimensione richiesta. Prima del taglio ordina per fitness crescente, così vengono rimossi i cromosomi peggiori. |
| `private Chromosome copyChromosome(Chromosome source)` | private | Crea una copia superficiale di un cromosoma. La copia è superficiale perché `Gene` è immutabile nel modello attuale. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `PopulationReuseDecision`

- File: `src/window/population/PopulationReuseDecision.java:11`
- Tipo: `class`
- Nome completo: `window.population.PopulationReuseDecision`

**Cosa fa, in parole semplici**

Decisione finale sul riuso della popolazione genetica tra due finestre. La dinamicità produce una decisione di base. Questa classe conserva anche l'eventuale correzione introdotta dalla policy temporale.

**Relazione con la formalizzazione**

Realizza la regola di riuso della popolazione tra finestre successive.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final PopulationReuseMode baseReuseMode`
- `private final PopulationReuseMode appliedReuseMode`
- `private final WindowPerformanceSignal previousPerformanceSignal`
- `private final boolean componentSpikeDetected`
- `private final boolean severeComponentSpikeDetected`
- `private final String reason`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public PopulationReuseDecision( PopulationReuseMode baseReuseMode, PopulationReuseMode appliedReuseMode, WindowPerformanceSignal previousPerformanceSignal, boolean componentSpikeDetected, String reason )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public PopulationReuseDecision( PopulationReuseMode baseReuseMode, PopulationReuseMode appliedReuseMode, WindowPerformanceSignal previousPerformanceSignal, boolean componentSpikeDetected, boolean severeComponentSpikeDetected, String reason )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static PopulationReuseDecision unchanged( PopulationReuseMode mode, WindowPerformanceSignal signal, boolean componentSpikeDetected, String reason )` | public | Metodo di supporto: realizza il passo `unchanged` dentro la responsabilita' della classe. |
| `public static PopulationReuseDecision unchanged( PopulationReuseMode mode, WindowPerformanceSignal signal, boolean componentSpikeDetected, boolean severeComponentSpikeDetected, String reason )` | public | Metodo di supporto: realizza il passo `unchanged` dentro la responsabilita' della classe. |
| `public PopulationReuseMode getBaseReuseMode()` | public | Restituisce il valore di `BaseReuseMode` senza modificarlo. |
| `public PopulationReuseMode getAppliedReuseMode()` | public | Restituisce il valore di `AppliedReuseMode` senza modificarlo. |
| `public PopulationReuseMode getAppliedMode()` | public | Alias usato dalle versioni più recenti del TemporalWindowManager. |
| `public WindowPerformanceSignal getPreviousPerformanceSignal()` | public | Restituisce il valore di `PreviousPerformanceSignal` senza modificarlo. |
| `public boolean isComponentSpikeDetected()` | public | Risponde con true/false alla domanda `is component spike detected`. |
| `public boolean isSevereComponentSpikeDetected()` | public | Risponde con true/false alla domanda `is severe component spike detected`. |
| `public boolean isCorrected()` | public | Risponde con true/false alla domanda `is corrected`. |
| `public String getReason()` | public | Restituisce il valore di `Reason` senza modificarlo. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `PopulationReuseDecisionPolicy`

- File: `src/window/population/PopulationReuseDecisionPolicy.java:27`
- Tipo: `class`
- Nome completo: `window.population.PopulationReuseDecisionPolicy`

**Cosa fa, in parole semplici**

Policy temporale per correggere la modalità di riuso della popolazione. La dinamicità formalizzata resta il punto di partenza, ma la decisione finale considera anche la qualità della finestra precedente e gli spike delle componenti più critiche per l'offloading. La policy non modifica GA, fitness o cromosomi: decide soltanto quanta popolazione precedente riutilizzare. Correzione principale: se una finestra mostra uno spike congiunto forte su task e link, il sistema non resta più in PARTIAL_RESTART solo perché D(k) aggregato è ancora sotto thetaHigh. In quel caso la popolazione precedente è considerata poco rappresentativa.

**Relazione con la formalizzazione**

Realizza la regola di riuso della popolazione tra finestre successive.

**Con chi comunica**

Comunica direttamente con: `DynamicityBreakdown`, `EvaluationBreakdown`, `ExecutionNodeResourceUsageBreakdown`, `GeneEvaluationBreakdown`, `LinkBandwidthUsageBreakdown`, `MaGaResult`, `TemporalWindowConfig`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double DEADLINE_RATE_GOOD_MAX = 0.03`
- `private static final double DEADLINE_RATE_WARNING_MIN = 0.10`
- `private static final double DEADLINE_RATE_BAD_MIN = 0.25`
- `private static final double COVERAGE_RATE_WARNING_MIN = 0.02`
- `private static final double COVERAGE_RATE_BAD_MIN = 0.05`
- `private static final double SATURATION_THRESHOLD_PERCENT = 95.0`
- `private static final int SATURATION_WARNING_COUNT = 8`
- `private static final double TASK_SPIKE_THRESHOLD = 0.70`
- `private static final double LINK_SPIKE_THRESHOLD = 0.75`
- `private static final double RESOURCE_SPIKE_THRESHOLD = 0.65`
- `private static final double VEHICLE_SPIKE_THRESHOLD = 0.55`
- `private static final double SEVERE_TASK_SPIKE_THRESHOLD = 0.70`
- `private static final double SEVERE_LINK_SPIKE_THRESHOLD = 0.74`
- `private static final double VERY_HIGH_SINGLE_COMPONENT_SPIKE = 0.85`
- `private static final double MODERATE_LOW_DYNAMICITY_FOR_WARM = 0.42`
- `private final TemporalWindowConfig config`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public PopulationReuseDecisionPolicy()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public PopulationReuseDecisionPolicy(TemporalWindowConfig config)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public PopulationReuseDecision decide( DynamicityBreakdown dynamicityBreakdown, MaGaResult previousResult, boolean hasReusablePopulation, boolean criticalEventTrigger )` | public | Metodo di supporto: realizza il passo `decide` dentro la responsabilita' della classe. |
| `private double midpointTheta()` | private | Metodo di supporto: realizza il passo `midpoint theta` dentro la responsabilita' della classe. |
| `private boolean hasComponentSpike(DynamicityBreakdown breakdown)` | private | Risponde con true/false alla domanda `has component spike`. |
| `private boolean hasCriticalTaskLinkSpike(DynamicityBreakdown breakdown)` | private | Risponde con true/false alla domanda `has critical task link spike`. |
| `private boolean hasSevereComponentSpike(DynamicityBreakdown breakdown)` | private | Risponde con true/false alla domanda `has severe component spike`. |
| `private WindowPerformanceSignal classifyPreviousPerformance(MaGaResult previousResult)` | private | Metodo di supporto: realizza il passo `classify previous performance` dentro la responsabilita' della classe. |
| `private int countCpuViolations(List<?> usageBreakdowns)` | private | Metodo di supporto: realizza il passo `count cpu violations` dentro la responsabilita' della classe. |
| `private int countBandwidthViolations(List<?> usageBreakdowns)` | private | Metodo di supporto: realizza il passo `count bandwidth violations` dentro la responsabilita' della classe. |
| `private int countSaturatedResources(EvaluationBreakdown evaluation)` | private | Metodo di supporto: realizza il passo `count saturated resources` dentro la responsabilita' della classe. |

**Problematiche aperte**

- Aggiunge correzioni euristiche basate su performance precedente e spike, oltre alla regola pura `D(k) -> reuse`.

### `PopulationReuseMode`

- File: `src/window/population/PopulationReuseMode.java:16`
- Tipo: `enum`
- Nome completo: `window.population.PopulationReuseMode`

**Cosa fa, in parole semplici**

Modalità con cui il gestore temporale costruisce la popolazione iniziale della nuova esecuzione MA-GA. Questa scelta appartiene al package `window`, non al package `ga`. Il gestore temporale valuta la dinamicità dello scenario, sceglie una strategia di riuso e prepara una popolazione iniziale già coerente con lo snapshot corrente. Il MA-GA continua quindi a ricevere una popolazione iniziale, senza dover conoscere direttamente se arriva da una prima esecuzione, da un warm start, da un riavvio parziale o da un cold start.

**Relazione con la formalizzazione**

Realizza la regola di riuso della popolazione tra finestre successive.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`FIRST_RUN`, `WARM_START`, `PARTIAL_RESTART`, `COLD_START`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public boolean usesPreviousPopulation()` | public | Indica se la modalità conserva almeno una parte della popolazione finale precedente. @return `true` per `#WARM_START` e `#PARTIAL_RESTART` |
| `public boolean generatesNewPopulation()` | public | Indica se la modalità richiede anche la generazione di nuovi cromosomi. Il warm start puro prova a coprire la popolazione usando cromosomi precedenti riparati. Le altre modalità generano una popolazione nuova completa o una quota di completamento. @return `true` se la modalità prevede cromosomi generati da zero |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `WindowPerformanceSignal`

- File: `src/window/population/WindowPerformanceSignal.java:9`
- Tipo: `enum`
- Nome completo: `window.population.WindowPerformanceSignal`

**Cosa fa, in parole semplici**

Segnale sintetico sulla qualità della finestra precedente.

**Relazione con la formalizzazione**

Realizza la regola di riuso della popolazione tra finestre successive.

**Con chi comunica**

Comunica direttamente con: `EvaluationBreakdown`, `MaGaResult`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Valori enum principali:
`UNKNOWN`, `GOOD`, `WARNING`, `BAD`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public static WindowPerformanceSignal from(MaGaResult result)` | public | Metodo di supporto: realizza il passo `from` dentro la responsabilita' della classe. |
| `public boolean isGood()` | public | Risponde con true/false alla domanda `is good`. |
| `public boolean isBadOrWarning()` | public | Risponde con true/false alla domanda `is bad or warning`. |
| `private static double safe(double value)` | private | Esegue un'operazione protetta per evitare valori non finiti o divisioni non valide. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.prefilter`

Riduce lo spazio dei candidati prima del GA.

### `CandidateFilteringResult`

- File: `src/window/prefilter/CandidateFilteringResult.java:10`
- Tipo: `class`
- Nome completo: `window.prefilter.CandidateFilteringResult`

**Cosa fa, in parole semplici**

Risultato del prefiltraggio di uno snapshot.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final SystemSnapshot originalSnapshot`
- `private final SystemSnapshot filteredSnapshot`
- `private final CandidateFilteringStats stats`
- `private final List<FilteredCandidateRecord> records`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public CandidateFilteringResult( SystemSnapshot originalSnapshot, SystemSnapshot filteredSnapshot, CandidateFilteringStats stats, List<FilteredCandidateRecord> records )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public SystemSnapshot getOriginalSnapshot()` | public | Restituisce il valore di `OriginalSnapshot` senza modificarlo. |
| `public SystemSnapshot getFilteredSnapshot()` | public | Restituisce il valore di `FilteredSnapshot` senza modificarlo. |
| `public CandidateFilteringStats getStats()` | public | Restituisce il valore di `Stats` senza modificarlo. |
| `public List<FilteredCandidateRecord> getRecords()` | public | Restituisce il valore di `Records` senza modificarlo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `CandidateFilteringStats`

- File: `src/window/prefilter/CandidateFilteringStats.java:10`
- Tipo: `class`
- Nome completo: `window.prefilter.CandidateFilteringStats`

**Cosa fa, in parole semplici**

Statistiche del prefiltraggio candidati su uno snapshot.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final int originalCandidateCount`
- `private final int filteredCandidateCount`
- `private final int removedCandidateCount`
- `private final Map<CandidateRejectionReason, Integer> countByReason`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public CandidateFilteringStats( int originalCandidateCount, int filteredCandidateCount, Map<CandidateRejectionReason, Integer> countByReason )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public int getOriginalCandidateCount()` | public | Restituisce il valore di `OriginalCandidateCount` senza modificarlo. |
| `public int getFilteredCandidateCount()` | public | Restituisce il valore di `FilteredCandidateCount` senza modificarlo. |
| `public int getRemovedCandidateCount()` | public | Restituisce il valore di `RemovedCandidateCount` senza modificarlo. |
| `public Map<CandidateRejectionReason, Integer> getCountByReason()` | public | Restituisce il valore di `CountByReason` senza modificarlo. |
| `public int getCountForReason(CandidateRejectionReason reason)` | public | Restituisce il valore di `CountForReason` senza modificarlo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `CandidatePrefilter`

- File: `src/window/prefilter/CandidatePrefilter.java:30`
- Tipo: `class`
- Nome completo: `window.prefilter.CandidatePrefilter`

**Cosa fa, in parole semplici**

Prefiltra i candidati prima dell'esecuzione del GA. Il filtro applica soltanto controlli strutturali. Non elimina candidati raggiungibili perché poco convenienti, vicini al limite di copertura o apparentemente incompatibili con una deadline. Queste valutazioni spettano al repair deadline-aware, alla fitness e alla penalità mobility-aware. I candidati LOCAL vengono sempre mantenuti. I candidati remoti vengono rimossi soltanto se non possono essere utilizzati in modo matematicamente sensato: CPU o banda nulle/non valide, assenza di task per la sorgente, sorgente mancante, EDGE fuori copertura oppure collegamento V2V fuori raggio.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`, `NodeType`, `SystemSnapshot`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-9`
- `private final CandidatePrefilterConfig config`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public CandidatePrefilter()` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public CandidatePrefilter(CandidatePrefilterConfig config)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public CandidateFilteringResult filter(SystemSnapshot snapshot)` | public | Applica il prefilter allo snapshot. @param snapshot snapshot originale osservato dalla sorgente @return risultato contenente snapshot filtrato e statistiche |
| `private CandidateFilteringResult disabledResult(SystemSnapshot snapshot)` | private | Metodo di supporto: realizza il passo `disabled result` dentro la responsabilita' della classe. |
| `private CandidateDecision evaluateCandidate( NodeCandidate candidate, Set<String> taskSourceIds, Map<String, VehicleSnapshot> vehicleById )` | private | Valuta `evaluate candidate` e restituisce un risultato o un breakdown. |
| `private double estimateCoverageSeconds( NodeCandidate candidate, VehicleSnapshot sourceVehicle, Map<String, VehicleSnapshot> vehicleById )` | private | Stima la copertura strutturale corrente per EDGE, VEHICLE e CLOUD. Il tempo residuo non viene confrontato con soglie euristiche. Per il prefilter conta soltanto che il candidato sia raggiungibile nell'istante osservato. La fragilità della scelta resta valutabile dalla penalità mobility-aware. |
| `private Map<String, VehicleSnapshot> indexVehicles(SystemSnapshot snapshot)` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private Set<String> indexTaskSourceIds(SystemSnapshot snapshot)` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private double distance( double x1, double y1, double x2, double y2 )` | private | Metodo di supporto: realizza il passo `distance` dentro la responsabilita' della classe. |

**Problematiche aperte**

- Usa lower bound e slack: riduce candidati impossibili o deboli, ma non garantisce che il GA rispetti tutte le deadline.
- Per cloud e V2V dipende ancora dalle approssimazioni di mobilita' disponibili nello snapshot.

### `CandidateDecision` (tipo interno di `CandidatePrefilter`)

- File: `src/window/prefilter/CandidatePrefilter.java:308`
- Tipo: `class`
- Nome completo: `window.prefilter.CandidatePrefilter.CandidateDecision`

**Cosa fa, in parole semplici**

Classe di supporto del progetto MA-GA.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `NodeCandidate`, `NodeType`, `SystemSnapshot`, `TaskInstance`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final boolean keep`
- `private final CandidateRejectionReason reason`
- `private final double estimatedCoverageSeconds`
- `private final String note`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private CandidateDecision( boolean keep, CandidateRejectionReason reason, double estimatedCoverageSeconds, String note )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `private static CandidateDecision keep( double estimatedCoverageSeconds, String note )` | private | Metodo di supporto: realizza il passo `keep` dentro la responsabilita' della classe. |
| `private static CandidateDecision reject( CandidateRejectionReason reason, double estimatedCoverageSeconds, String note )` | private | Metodo di supporto: realizza il passo `reject` dentro la responsabilita' della classe. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `CandidatePrefilterConfig`

- File: `src/window/prefilter/CandidatePrefilterConfig.java:11`
- Tipo: `class`
- Nome completo: `window.prefilter.CandidatePrefilterConfig`

**Cosa fa, in parole semplici**

Configurazione del prefiltraggio dei candidati. Il prefilter aderente alla formalizzazione applica soltanto controlli strutturali. Alcuni parametri storici restano esposti per compatibilità con chiamanti precedenti, ma non vengono più usati per eliminare candidati raggiungibili soltanto perché deboli o poco competitivi.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final boolean enabled`
- `private final double minRemoteCpu`
- `private final double minRemoteBandwidth`
- `private final double minCoverageSeconds`
- `private final double coverageSafetyFactor`
- `private final double deadlineSlackFactor`
- `private final double v2vCoverageRadiusMeters`
- `private final double cloudCoverageSeconds`
- `private final boolean keepAllCloudCandidates`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public CandidatePrefilterConfig( boolean enabled, double minRemoteCpu, double minRemoteBandwidth, double minCoverageSeconds, double coverageSafetyFactor, double deadlineSlackFactor, double v2vCoverageRadiusMeters, double cloudCoverageSeconds, boolean keepAllCloudCandidates )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static CandidatePrefilterConfig defaultConfig()` | public | Configurazione predefinita aderente alla formalizzazione. I candidati remoti strutturalmente validi restano valutabili dal GA. CPU, banda e copertura vengono filtrate soltanto quando sono nulle o non valide. Le deadline non vengono usate come criterio di pruning. |
| `public static CandidatePrefilterConfig disabled()` | public | Configurazione completamente disabilitata. |
| `public boolean isEnabled()` | public | Risponde con true/false alla domanda `is enabled`. |
| `public double getMinRemoteCpu()` | public | @deprecated mantenuto soltanto per compatibilità. |
| `public double getMinRemoteBandwidth()` | public | @deprecated mantenuto soltanto per compatibilità. |
| `public double getMinCoverageSeconds()` | public | @deprecated mantenuto soltanto per compatibilità. |
| `public double getCoverageSafetyFactor()` | public | @deprecated mantenuto soltanto per compatibilità. |
| `public double getDeadlineSlackFactor()` | public | @deprecated mantenuto soltanto per compatibilità. |
| `public double getV2vCoverageRadiusMeters()` | public | Restituisce il valore di `V2vCoverageRadiusMeters` senza modificarlo. |
| `public double getCloudCoverageSeconds()` | public | Restituisce il valore di `CloudCoverageSeconds` senza modificarlo. |
| `public boolean isKeepAllCloudCandidates()` | public | @deprecated il cloud è sempre mantenuto finché manca un gateway esplicito. |
| `private static double validateNonNegative(String fieldName, double value)` | private | Controlla la correttezza di `validate non negative` e solleva un'eccezione se trova dati incoerenti. |
| `private static double validatePositive(String fieldName, double value)` | private | Controlla la correttezza di `validate positive` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

- I parametri di slack, copertura e soglie sono sperimentali e vanno calibrati sullo scenario MOSAIC/SUMO.

### `CandidateRejectionReason`

- File: `src/window/prefilter/CandidateRejectionReason.java:6`
- Tipo: `enum`
- Nome completo: `window.prefilter.CandidateRejectionReason`

**Cosa fa, in parole semplici**

Motivo per cui un candidato remoto viene rimosso dal prefilter.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`KEPT`, `NO_TASK_FOR_SOURCE`, `INVALID_CPU`, `INVALID_BANDWIDTH`, `INSUFFICIENT_COVERAGE`, `DEADLINE_LOWER_BOUND_TOO_HIGH`, `RESTORED_AS_FALLBACK`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `FilteredCandidateRecord`

- File: `src/window/prefilter/FilteredCandidateRecord.java:8`
- Tipo: `class`
- Nome completo: `window.prefilter.FilteredCandidateRecord`

**Cosa fa, in parole semplici**

Record diagnostico di un candidato rimosso o mantenuto dal prefilter.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `NodeType`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final String candidateId`
- `private final String sourceVehicleId`
- `private final String executionNodeId`
- `private final NodeType nodeType`
- `private final CandidateRejectionReason reason`
- `private final double estimatedBestCompletionSeconds`
- `private final double estimatedCoverageSeconds`
- `private final String note`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public FilteredCandidateRecord( String candidateId, String sourceVehicleId, String executionNodeId, NodeType nodeType, CandidateRejectionReason reason, double estimatedBestCompletionSeconds, double estimatedCoverageSeconds, String note )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public String getCandidateId()` | public | Restituisce il valore di `CandidateId` senza modificarlo. |
| `public String getSourceVehicleId()` | public | Restituisce il valore di `SourceVehicleId` senza modificarlo. |
| `public String getExecutionNodeId()` | public | Restituisce il valore di `ExecutionNodeId` senza modificarlo. |
| `public NodeType getNodeType()` | public | Restituisce il valore di `NodeType` senza modificarlo. |
| `public CandidateRejectionReason getReason()` | public | Restituisce il valore di `Reason` senza modificarlo. |
| `public double getEstimatedBestCompletionSeconds()` | public | Restituisce il valore di `EstimatedBestCompletionSeconds` senza modificarlo. |
| `public double getEstimatedCoverageSeconds()` | public | Restituisce il valore di `EstimatedCoverageSeconds` senza modificarlo. |
| `public String getNote()` | public | Restituisce il valore di `Note` senza modificarlo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.source`

Astrazione della sorgente di stato: JSON time, JSON sequence e predisposizione MOSAIC.

### `FilteringSystemStateSource`

- File: `src/window/source/FilteringSystemStateSource.java:20`
- Tipo: `class`
- Nome completo: `window.source.FilteringSystemStateSource`

**Cosa fa, in parole semplici**

Decorator che applica CandidatePrefilter agli snapshot prodotti da una sorgente. Lo snapshot osservato resta disponibile al gestore temporale. Il filtro modifica soltanto la vista destinata al GA, evitando che una riduzione dello spazio di ricerca venga interpretata come variazione fisica dello scenario.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `CandidateFilteringResult`, `CandidatePrefilter`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final SystemStateSource delegate`
- `private final CandidatePrefilter prefilter`
- `private final List<CandidateFilteringResult> filteringResults`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public FilteringSystemStateSource( SystemStateSource delegate, CandidatePrefilter prefilter )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public Optional<SystemStateObservation> nextObservation(SystemStateRequest request)` | public | Metodo di supporto: realizza il passo `next observation` dentro la responsabilita' della classe. |
| `public SystemStateSourceMode getMode()` | public | Restituisce il valore di `Mode` senza modificarlo. |
| `public String getDescription()` | public | Restituisce il valore di `Description` senza modificarlo. |
| `private SystemStateObservation filterObservation(SystemStateObservation observation)` | private | Metodo di supporto: realizza il passo `filter observation` dentro la responsabilita' della classe. |
| `public List<CandidateFilteringResult> getFilteringResults()` | public | Restituisce il valore di `FilteringResults` senza modificarlo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `MosaicSnapshotBridge`

- File: `src/window/source/MosaicSnapshotBridge.java:13`
- Tipo: `interface`
- Nome completo: `window.source.MosaicSnapshotBridge`

**Cosa fa, in parole semplici**

Porta minima verso MOSAIC o verso un adapter equivalente. Questa interfaccia non contiene logica MA-GA. Il suo unico compito è restituire una fotografia del sistema al tempo richiesto.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `Optional<SystemSnapshot> readSnapshot(double observationTimeSeconds)` | package-private | Legge o costruisce lo snapshot del sistema al tempo richiesto. |
| `default String getDescription()` | package-private | Descrizione leggibile dell'adapter. |

**Problematiche aperte**

- E' solo un'interfaccia: l'integrazione MOSAIC/SUMO vera non e' implementata qui.

### `MosaicSystemStateSource`

- File: `src/window/source/MosaicSystemStateSource.java:14`
- Tipo: `class`
- Nome completo: `window.source.MosaicSystemStateSource`

**Cosa fa, in parole semplici**

Sorgente dati per MOSAIC. Il collegamento concreto con MOSAIC verrà implementato nel bridge. Il TemporalWindowManager non cambia: continua a ricevere SystemSnapshot.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final MosaicSnapshotBridge bridge`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public MosaicSystemStateSource(MosaicSnapshotBridge bridge)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public Optional<SystemStateObservation> nextObservation(SystemStateRequest request)` | public | Metodo di supporto: realizza il passo `next observation` dentro la responsabilita' della classe. |
| `public SystemStateSourceMode getMode()` | public | Restituisce il valore di `Mode` senza modificarlo. |
| `public String getDescription()` | public | Restituisce il valore di `Description` senza modificarlo. |

**Problematiche aperte**

- Dipende da un bridge MOSAIC esterno non ancora implementato nel repository.

### `SequentialSnapshotReplaySource`

- File: `src/window/source/SequentialSnapshotReplaySource.java:19`
- Tipo: `class`
- Nome completo: `window.source.SequentialSnapshotReplaySource`

**Cosa fa, in parole semplici**

Sorgente JSON per test offline sequenziali. Restituisce gli snapshot nell'ordine della lista, indipendentemente dal tempo richiesto dalla finestra adattiva. Questa modalità serve quando gli snapshot sono una successione di fotografie già decisa e vogliamo eseguirle tutte senza saltarne nessuna. Implementa il replay ordinale diagnostico: consuma tutti i file in sequenza anche se il tempo del manager diverge.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final List<SystemSnapshot> snapshots`
- `private final String description`
- `private int cursor`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public SequentialSnapshotReplaySource(List<SystemSnapshot> snapshots)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public SequentialSnapshotReplaySource( List<SystemSnapshot> snapshots, String description )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public Optional<SystemStateObservation> nextObservation(SystemStateRequest request)` | public | Metodo di supporto: realizza il passo `next observation` dentro la responsabilita' della classe. |
| `public SystemStateSourceMode getMode()` | public | Restituisce il valore di `Mode` senza modificarlo. |
| `public String getDescription()` | public | Restituisce il valore di `Description` senza modificarlo. |
| `public int size()` | public | Metodo di supporto: realizza il passo `size` dentro la responsabilita' della classe. |
| `public int getCursor()` | public | Restituisce il valore di `Cursor` senza modificarlo. |
| `public boolean isExhausted()` | public | Risponde con true/false alla domanda `is exhausted`. |
| `public List<SystemSnapshot> getSnapshots()` | public | Restituisce il valore di `Snapshots` senza modificarlo. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SystemStateObservation`

- File: `src/window/source/SystemStateObservation.java:19`
- Tipo: `class`
- Nome completo: `window.source.SystemStateObservation`

**Cosa fa, in parole semplici**

Osservazione restituita da una sorgente dati. La classe distingue la fotografia fisica osservata dalla vista destinata all'ottimizzazione. All'origine le due viste coincidono. Un decoratore può applicare il prefilter e sostituire soltanto la vista ottimizzabile, senza nascondere al gestore temporale lo stato reale dello scenario. Restano inoltre separati il tempo logico richiesto dal manager, il tempo salvato nella sorgente e la modalità con cui la sorgente ha prodotto lo snapshot.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-6`
- `private final SystemSnapshot observedSnapshot`
- `private final SystemSnapshot optimizationSnapshot`
- `private final double requestedObservationTimeSeconds`
- `private final double sourceObservationTimeSeconds`
- `private final SystemStateSourceMode sourceMode`
- `private final String sourceDescription`
- `private final int sequenceIndex`
- `private final boolean exactTimeMatch`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public SystemStateObservation( SystemSnapshot snapshot, double requestedObservationTimeSeconds, double sourceObservationTimeSeconds, SystemStateSourceMode sourceMode, String sourceDescription, int sequenceIndex, boolean exactTimeMatch )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `private SystemStateObservation( SystemSnapshot observedSnapshot, SystemSnapshot optimizationSnapshot, double requestedObservationTimeSeconds, double sourceObservationTimeSeconds, SystemStateSourceMode sourceMode, String sourceDescription, int sequenceIndex, boolean exactTimeMatch )` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public SystemSnapshot getSnapshot()` | public | Vista destinata all'ottimizzazione. Il metodo conserva la semantica storica per non rompere i chiamanti esistenti. Il gestore temporale deve usare esplicitamente `#getObservedSnapshot()` per dinamicità e bounds. |
| `public SystemSnapshot getObservedSnapshot()` | public | Fotografia grezza prodotta dalla sorgente prima del prefilter. |
| `public SystemSnapshot getOptimizationSnapshot()` | public | Vista filtrata destinata al GA. |
| `public double getRequestedObservationTimeSeconds()` | public | Restituisce il valore di `RequestedObservationTimeSeconds` senza modificarlo. |
| `public double getSourceObservationTimeSeconds()` | public | Restituisce il valore di `SourceObservationTimeSeconds` senza modificarlo. |
| `public double getActualObservationTimeSeconds()` | public | Alias storico per il tempo osservato dalla sorgente. |
| `public SystemStateSourceMode getSourceMode()` | public | Restituisce il valore di `SourceMode` senza modificarlo. |
| `public String getSourceDescription()` | public | Restituisce il valore di `SourceDescription` senza modificarlo. |
| `public int getSequenceIndex()` | public | Restituisce il valore di `SequenceIndex` senza modificarlo. |
| `public boolean isExactTimeMatch()` | public | Risponde con true/false alla domanda `is exact time match`. |
| `public boolean isTimeShifted()` | public | Risponde con true/false alla domanda `is time shifted`. |
| `public double getTimeShiftSeconds()` | public | Restituisce il valore di `TimeShiftSeconds` senza modificarlo. |
| `public SystemStateObservation withOptimizationSnapshot( SystemSnapshot updatedOptimizationSnapshot )` | public | Restituisce una nuova osservazione mantenendo lo snapshot grezzo e sostituendo soltanto la vista destinata al GA. |
| `public SystemStateObservation withSnapshot(SystemSnapshot updatedSnapshot)` | public | Alias storico mantenuto per compatibilità. @deprecated usare `#withOptimizationSnapshot(SystemSnapshot)`. |
| `private void validateSnapshotTime(SystemSnapshot snapshot, String label)` | private | Controlla la correttezza di `validate snapshot time` e solleva un'eccezione se trova dati incoerenti. |
| `private static void validateFiniteAndNonNegative( String fieldName, double value )` | private | Controlla la correttezza di `validate finite and non negative` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SystemStateRequest`

- File: `src/window/source/SystemStateRequest.java:15`
- Tipo: `class`
- Nome completo: `window.source.SystemStateRequest`

**Cosa fa, in parole semplici**

Richiesta di osservazione dello stato del sistema. La richiesta contiene il tempo logico scelto dal TemporalWindowManager. La sorgente dati decide come soddisfarlo: nei test JSON può restituire il prossimo file della sequenza; in MOSAIC potrà interrogare il simulatore al tempo richiesto.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `ReoptimizationTrigger`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final int windowIndex`
- `private final ReoptimizationTrigger plannedTrigger`
- `private final double requestedObservationTimeSeconds`
- `private final double currentWindowDurationSeconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public SystemStateRequest( int windowIndex, ReoptimizationTrigger plannedTrigger, double requestedObservationTimeSeconds, double currentWindowDurationSeconds )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public int getWindowIndex()` | public | Restituisce il valore di `WindowIndex` senza modificarlo. |
| `public ReoptimizationTrigger getPlannedTrigger()` | public | Restituisce il valore di `PlannedTrigger` senza modificarlo. |
| `public double getRequestedObservationTimeSeconds()` | public | Tempo logico/adattivo richiesto dal manager. |
| `public double getCurrentWindowDurationSeconds()` | public | Restituisce il valore di `CurrentWindowDurationSeconds` senza modificarlo. |
| `private static void validateFiniteAndNonNegative( String fieldName, double value )` | private | Controlla la correttezza di `validate finite and non negative` e solleva un'eccezione se trova dati incoerenti. |
| `private static void validatePositive(String fieldName, double value)` | private | Controlla la correttezza di `validate positive` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SystemStateSource`

- File: `src/window/source/SystemStateSource.java:12`
- Tipo: `interface`
- Nome completo: `window.source.SystemStateSource`

**Cosa fa, in parole semplici**

Porta di ingresso degli snapshot nel gestore temporale. Il MA-GA e il TemporalWindowManager non devono sapere se lo snapshot arriva da JSON, MOSAIC o da un altro simulatore. Devono ricevere solo una fotografia coerente del sistema.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `Optional<SystemStateObservation> nextObservation(SystemStateRequest request)` | package-private | Restituisce la prossima osservazione disponibile per la richiesta data. |
| `SystemStateSourceMode getMode()` | package-private | Modalità della sorgente. |
| `default String getDescription()` | package-private | Descrizione leggibile per report e debug. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `SystemStateSourceFactory`

- File: `src/window/source/SystemStateSourceFactory.java:12`
- Tipo: `class`
- Nome completo: `window.source.SystemStateSourceFactory`

**Cosa fa, in parole semplici**

Factory per costruire sorgenti dati temporali a partire da una cartella di snapshot JSON.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `JsonSnapshotFolderLoader`, `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `private SystemStateSourceFactory()` | private | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static SystemStateSource fromJsonFolder( String modeName, String folderPath ) throws Exception` | public | Metodo di supporto: realizza il passo `from json folder` dentro la responsabilita' della classe. |
| `public static String normalizeMode(String modeName)` | public | Metodo di supporto: realizza il passo `normalize mode` dentro la responsabilita' della classe. |

**Problematiche aperte**

- Costruisce solo sorgenti JSON; MOSAIC richiede un bridge concreto separato.

### `SystemStateSourceMode`

- File: `src/window/source/SystemStateSourceMode.java:6`
- Tipo: `enum`
- Nome completo: `window.source.SystemStateSourceMode`

**Cosa fa, in parole semplici**

Modalità con cui il sistema ottiene gli snapshot da elaborare.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`JSON_SEQUENTIAL_REPLAY`, `JSON_TIME_INDEXED_REPLAY`, `MOSAIC_LIVE`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TimeIndexedSnapshotReplaySource`

- File: `src/window/source/TimeIndexedSnapshotReplaySource.java:24`
- Tipo: `class`
- Nome completo: `window.source.TimeIndexedSnapshotReplaySource`

**Cosa fa, in parole semplici**

Sorgente JSON indicizzata nel tempo logico del manager. Dato un istante richiesto, restituisce lo snapshot più recente che era già disponibile a quell'istante. Non espone mai snapshot futuri. Se tra due richieste non è diventato disponibile un nuovo file, riutilizza l'ultimo snapshot noto: lo stato osservato resta valido fino all'arrivo di una nuova osservazione. Questa semantica distingue il replay temporale da quello sequenziale: `SequentialSnapshotReplaySource` consuma ordinalmente tutti i file, mentre questa classe rispetta il tempo logico richiesto dal manager. Implementa il replay temporale corretto: usa l'ultimo snapshot disponibile e non espone futuro.

**Relazione con la formalizzazione**

Fornisce o valida lo stato osservato `S_k` prima che il GA possa ragionare.

**Con chi comunica**

Comunica direttamente con: `SystemSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double DEFAULT_TIME_TOLERANCE_SECONDS = 1.0E-6`
- `private final List<SystemSnapshot> snapshots`
- `private final String description`
- `private final double timeToleranceSeconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TimeIndexedSnapshotReplaySource(List<SystemSnapshot> snapshots)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public TimeIndexedSnapshotReplaySource( List<SystemSnapshot> snapshots, double timeToleranceSeconds, String description )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public Optional<SystemStateObservation> nextObservation(SystemStateRequest request)` | public | Metodo di supporto: realizza il passo `next observation` dentro la responsabilita' della classe. |
| `private int findLatestSnapshotAtOrBefore(double requestedTime)` | private | Restituisce l'indice dello snapshot più recente non successivo al tempo richiesto. La ricerca binaria mantiene il comportamento corretto anche quando lo stesso snapshot deve essere riutilizzato in finestre diverse. |
| `public SystemStateSourceMode getMode()` | public | Restituisce il valore di `Mode` senza modificarlo. |
| `public String getDescription()` | public | Restituisce il valore di `Description` senza modificarlo. |
| `public List<SystemSnapshot> getSnapshots()` | public | Restituisce il valore di `Snapshots` senza modificarlo. |
| `private static void validateFiniteAndNonNegative(String fieldName, double value)` | private | Controlla la correttezza di `validate finite and non negative` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.state`

Conserva lo stato del ciclo temporale e i risultati di ogni finestra.

### `TemporalStepResult`

- File: `src/window/state/TemporalStepResult.java:32`
- Tipo: `class`
- Nome completo: `window.state.TemporalStepResult`

**Cosa fa, in parole semplici**

Risultato di una singola finestra temporale. Il risultato conserva due tempi diversi: tempo logico della finestra, deciso dal TemporalWindowManager; tempo sorgente dello snapshot, salvato nel SystemSnapshot. Questa separazione è necessaria perché nei test JSON sequenziali il manager può avanzare con finestre adattive, mentre gli snapshot restano una sequenza di fotografie salvate su disco.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `AdaptiveWindowDecision`, `DynamicityBreakdown`, `MaGaResult`, `PopulationReuseDecision`, `PopulationReuseMode`, `ReoptimizationTrigger`, `SystemSnapshot`, `SystemStateObservation`, `TemporalOperationalMetrics`, `TemporalWindowBounds`, `WindowPerformanceSignal`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private static final double EPSILON = 1.0E-6`
- `private final int windowIndex`
- `private final ReoptimizationTrigger trigger`
- `private final double dataCollectionDelaySeconds`
- `private final double logicalObservationTimeSeconds`
- `private final SystemSnapshot snapshot`
- `private final SystemStateObservation systemStateObservation`
- `private final DynamicityBreakdown dynamicityBreakdown`
- `private final PopulationReuseDecision populationReuseDecision`
- `private final PopulationReuseMode reuseMode`
- `private final AdaptiveWindowDecision adaptiveWindowDecision`
- `private final TemporalOperationalMetrics operationalMetrics`
- `private final int initialPopulationSize`
- `private final int finalPopulationSize`
- `private final MaGaResult maGaResult`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TemporalStepResult( int windowIndex, ReoptimizationTrigger trigger, double dataCollectionDelaySeconds, double logicalObservationTimeSeconds, SystemSnapshot snapshot, SystemStateObservation systemStateObservation, DynamicityBreakdown dynamicityBreakdown, PopulationReuseDecision populationReuseDecision, AdaptiveWindowDecision adaptiveWindowDecision, TemporalOperationalMetrics operationalMetrics, int initialPopulationSize, int finalPopulationSize, MaGaResult maGaResult )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public TemporalStepResult( int windowIndex, ReoptimizationTrigger trigger, double dataCollectionDelaySeconds, double logicalObservationTimeSeconds, SystemSnapshot snapshot, DynamicityBreakdown dynamicityBreakdown, PopulationReuseDecision populationReuseDecision, AdaptiveWindowDecision adaptiveWindowDecision, TemporalOperationalMetrics operationalMetrics, int initialPopulationSize, int finalPopulationSize, MaGaResult maGaResult )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public TemporalStepResult( int windowIndex, ReoptimizationTrigger trigger, double dataCollectionDelaySeconds, double observationTimeSeconds, SystemSnapshot snapshot, DynamicityBreakdown dynamicityBreakdown, PopulationReuseMode reuseMode, int initialPopulationSize, int finalPopulationSize, MaGaResult maGaResult )` | public | Costruttore semplificato per chiamanti che non producono ancora decisioni adattive complete. |
| `public int getWindowIndex()` | public | Restituisce il valore di `WindowIndex` senza modificarlo. |
| `public ReoptimizationTrigger getTrigger()` | public | Restituisce il valore di `Trigger` senza modificarlo. |
| `public double getDataCollectionDelaySeconds()` | public | Restituisce il valore di `DataCollectionDelaySeconds` senza modificarlo. |
| `public double getObservationTimeSeconds()` | public | Tempo logico/adattivo della finestra. Il nome resta compatibile con i printer esistenti. Da ora questo valore non deve essere interpretato come tempo interno del JSON. |
| `public double getLogicalObservationTimeSeconds()` | public | Restituisce il valore di `LogicalObservationTimeSeconds` senza modificarlo. |
| `public double getSourceObservationTimeSeconds()` | public | Restituisce il valore di `SourceObservationTimeSeconds` senza modificarlo. |
| `public double getSnapshotTimeSeconds()` | public | Restituisce il valore di `SnapshotTimeSeconds` senza modificarlo. |
| `public SystemSnapshot getSnapshot()` | public | Restituisce il valore di `Snapshot` senza modificarlo. |
| `public Optional<SystemStateObservation> getSystemStateObservation()` | public | Restituisce il valore di `SystemStateObservation` senza modificarlo. |
| `public DynamicityBreakdown getDynamicityBreakdown()` | public | Restituisce il valore di `DynamicityBreakdown` senza modificarlo. |
| `public PopulationReuseDecision getPopulationReuseDecision()` | public | Restituisce il valore di `PopulationReuseDecision` senza modificarlo. |
| `public PopulationReuseMode getReuseMode()` | public | Restituisce il valore di `ReuseMode` senza modificarlo. |
| `public AdaptiveWindowDecision getAdaptiveWindowDecision()` | public | Restituisce il valore di `AdaptiveWindowDecision` senza modificarlo. |
| `public TemporalOperationalMetrics getOperationalMetrics()` | public | Restituisce il valore di `OperationalMetrics` senza modificarlo. |
| `public int getInitialPopulationSize()` | public | Restituisce il valore di `InitialPopulationSize` senza modificarlo. |
| `public int getFinalPopulationSize()` | public | Restituisce il valore di `FinalPopulationSize` senza modificarlo. |
| `public MaGaResult getMaGaResult()` | public | Restituisce il valore di `MaGaResult` senza modificarlo. |
| `public double getTriggerTimeSeconds()` | public | Restituisce il valore di `TriggerTimeSeconds` senza modificarlo. |
| `public boolean wasTriggeredByCriticalEvent()` | public | Metodo di supporto: realizza il passo `was triggered by critical event` dentro la responsabilita' della classe. |
| `public boolean reusedPreviousPopulation()` | public | Metodo di supporto: realizza il passo `reused previous population` dentro la responsabilita' della classe. |
| `private static void validateObservationConsistency( ReoptimizationTrigger trigger, double logicalObservationTimeSeconds, double dataCollectionDelaySeconds )` | private | Controlla la correttezza di `validate observation consistency` e solleva un'eccezione se trova dati incoerenti. |
| `private static void validateSnapshotConsistency( SystemSnapshot snapshot, MaGaResult maGaResult )` | private | Controlla la correttezza di `validate snapshot consistency` e solleva un'eccezione se trova dati incoerenti. |
| `private static void validateSourceObservationConsistency( SystemSnapshot snapshot, SystemStateObservation observation, double logicalObservationTimeSeconds )` | private | Controlla la correttezza di `validate source observation consistency` e solleva un'eccezione se trova dati incoerenti. |
| `private static void validateFiniteAndNonNegative( String fieldName, double value )` | private | Controlla la correttezza di `validate finite and non negative` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TemporalWindowResult`

- File: `src/window/state/TemporalWindowResult.java:19`
- Tipo: `class`
- Nome completo: `window.state.TemporalWindowResult`

**Cosa fa, in parole semplici**

Risultato immutabile di una sequenza di finestre temporali. Questa classe aggrega i `TemporalStepResult` prodotti da `TemporalWindowManager#run(double, int)`. Non esegue calcoli genetici e non modifica gli step: offre solo una vista compatta della sequenza e alcuni helper utili per report e test. La lista interna è immutabile. Ogni operazione di aggiunta restituisce un nuovo `TemporalWindowResult`, lasciando invariato l'oggetto originale.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final List<TemporalStepResult> steps`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TemporalWindowResult(List<TemporalStepResult> steps)` | public | Crea un risultato temporale aggregato. La lista viene copiata e validata, poi esposta come lista non modificabile. Gli oggetti `TemporalStepResult` non vengono clonati perché sono già pensati come risultati immutabili. @param steps risultati delle finestre eseguite |
| `public static TemporalWindowResult empty()` | public | Crea un risultato vuoto. Usato come accumulatore iniziale quando il manager inizia a eseguire una sequenza di finestre. @return risultato senza step |
| `public static TemporalWindowResult single(TemporalStepResult step)` | public | Crea un risultato contenente un solo step. @param step risultato della singola finestra @return risultato aggregato con un elemento |
| `public List<TemporalStepResult> getSteps()` | public | @return lista immutabile degli step eseguiti |
| `public int getStepCount()` | public | @return numero di step temporali aggregati |
| `public boolean isEmpty()` | public | @return `true` se non è stato eseguito nessuno step |
| `public Optional<TemporalStepResult> getFirstStep()` | public | @return primo step, se presente |
| `public Optional<TemporalStepResult> getLastStep()` | public | @return ultimo step, se presente |
| `public long countCriticalEventSteps()` | public | @return numero di finestre rieseguite per evento critico |
| `public long countPopulationReuseSteps()` | public | @return numero di finestre che hanno riutilizzato popolazione precedente |
| `public Optional<Double> getBestFinalFitness()` | public | @return migliore fitness finale osservata nella sequenza |
| `public TemporalWindowResult append(TemporalStepResult step)` | public | Restituisce un nuovo risultato con uno step aggiunto. L'oggetto corrente resta invariato. Questa scelta rende il risultato aggregato semplice da passare tra metodi senza effetti collaterali. @param step nuovo step da aggiungere @return nuovo TemporalWindowResult immutabile |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TemporalWindowState`

- File: `src/window/state/TemporalWindowState.java:20`
- Tipo: `class`
- Nome completo: `window.state.TemporalWindowState`

**Cosa fa, in parole semplici**

Stato interno del gestore temporale. Il tempo gestito da questa classe è il tempo logico del manager. Non deve essere confuso con il tempo salvato dentro lo snapshot JSON. Lo snapshot resta una fotografia del sistema; il manager decide quando chiedere una nuova fotografia.

**Relazione con la formalizzazione**

Supporta la traduzione pratica della formalizzazione in codice.

**Con chi comunica**

Comunica direttamente con: `Chromosome`, `MaGaResult`, `SystemSnapshot`, `TemporalOperationalMetrics`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final int windowIndex`
- `private final double currentTimeSeconds`
- `private final double nextScheduledTimeSeconds`
- `private final double currentWindowDurationSeconds`
- `private final SystemSnapshot lastSnapshot`
- `private final MaGaResult lastResult`
- `private final TemporalOperationalMetrics lastOperationalMetrics`
- `private final List<Chromosome> lastFinalPopulation`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TemporalWindowState( int windowIndex, double currentTimeSeconds, double nextScheduledTimeSeconds, double currentWindowDurationSeconds, SystemSnapshot lastSnapshot, MaGaResult lastResult, TemporalOperationalMetrics lastOperationalMetrics, List<Chromosome> lastFinalPopulation )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static TemporalWindowState initial( double startTimeSeconds, double initialWindowSeconds, TemporalOperationalMetrics initialOperationalMetrics )` | public | Metodo di supporto: realizza il passo `initial` dentro la responsabilita' della classe. |
| `public static TemporalWindowState initial( double startTimeSeconds, double fixedIntervalSeconds )` | public | Factory semplificata per esecuzioni a finestra iniziale fissa. |
| `public static TemporalWindowState afterStep(TemporalStepResult stepResult)` | public | Metodo di supporto: realizza il passo `after step` dentro la responsabilita' della classe. |
| `public static TemporalWindowState afterStep( TemporalStepResult stepResult, double fixedIntervalSeconds )` | public | Transizione semplificata per chiamanti che mantengono una durata fissa. |
| `public int getWindowIndex()` | public | Restituisce il valore di `WindowIndex` senza modificarlo. |
| `public double getCurrentTimeSeconds()` | public | Restituisce il valore di `CurrentTimeSeconds` senza modificarlo. |
| `public double getNextScheduledTimeSeconds()` | public | Restituisce il valore di `NextScheduledTimeSeconds` senza modificarlo. |
| `public double getCurrentWindowDurationSeconds()` | public | Restituisce il valore di `CurrentWindowDurationSeconds` senza modificarlo. |
| `public SystemSnapshot getLastSnapshot()` | public | Restituisce il valore di `LastSnapshot` senza modificarlo. |
| `public MaGaResult getLastResult()` | public | Restituisce il valore di `LastResult` senza modificarlo. |
| `public TemporalOperationalMetrics getLastOperationalMetrics()` | public | Restituisce il valore di `LastOperationalMetrics` senza modificarlo. |
| `public List<Chromosome> getLastFinalPopulation()` | public | Restituisce il valore di `LastFinalPopulation` senza modificarlo. |
| `public boolean hasPreviousExecution()` | public | Risponde con true/false alla domanda `has previous execution`. |
| `public boolean hasReusablePopulation()` | public | Risponde con true/false alla domanda `has reusable population`. |
| `private static void validateFiniteAndNonNegative( String fieldName, double value )` | private | Controlla la correttezza di `validate finite and non negative` e solleva un'eccezione se trova dati incoerenti. |
| `private static void validatePositive(String fieldName, double value)` | private | Controlla la correttezza di `validate positive` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## Package `window.timing`

Calcola limiti e decisioni della finestra adattiva.

### `AdaptiveWindowController`

- File: `src/window/timing/AdaptiveWindowController.java:15`
- Tipo: `class`
- Nome completo: `window.timing.AdaptiveWindowController`

**Cosa fa, in parole semplici**

Controller della finestra adattiva. Non modifica il GA. Decide solo la durata della prossima finestra.

**Relazione con la formalizzazione**

Realizza i limiti `DeltaT_min`, `DeltaT_max` e la scelta della finestra temporale.

**Con chi comunica**

Comunica direttamente con: `DynamicityBreakdown`, `DynamicityLevel`, `SystemSnapshot`, `TemporalWindowConfig`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final TemporalWindowConfig config`
- `private final TemporalWindowBoundsCalculator boundsCalculator`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public AdaptiveWindowController( TemporalWindowConfig config, TemporalWindowBoundsCalculator boundsCalculator )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public AdaptiveWindowDecision decideNextWindow( double currentWindowSeconds, DynamicityBreakdown dynamicityBreakdown, SystemSnapshot currentSnapshot, TemporalOperationalMetrics operationalMetrics )` | public | Metodo di supporto: realizza il passo `decide next window` dentro la responsabilita' della classe. |
| `private boolean hasSevereComponentSpike(DynamicityBreakdown breakdown)` | private | Risponde con true/false alla domanda `has severe component spike`. |
| `private static void validatePositive(String fieldName, double value)` | private | Controlla la correttezza di `validate positive` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `AdaptiveWindowDecision`

- File: `src/window/timing/AdaptiveWindowDecision.java:8`
- Tipo: `class`
- Nome completo: `window.timing.AdaptiveWindowDecision`

**Cosa fa, in parole semplici**

Decisione prodotta dal controller della finestra adattiva.

**Relazione con la formalizzazione**

Realizza i limiti `DeltaT_min`, `DeltaT_max` e la scelta della finestra temporale.

**Con chi comunica**

Comunica direttamente con: `DynamicityLevel`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double currentWindowSeconds`
- `private final double nextWindowSeconds`
- `private final TemporalWindowBounds bounds`
- `private final DynamicityLevel dynamicityLevel`
- `private final Action action`
- `private final String reason`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public AdaptiveWindowDecision( double currentWindowSeconds, double nextWindowSeconds, TemporalWindowBounds bounds, DynamicityLevel dynamicityLevel, Action action, String reason )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static AdaptiveWindowDecision fixed( double windowSeconds, TemporalWindowBounds bounds, DynamicityLevel dynamicityLevel, String reason )` | public | Metodo di supporto: realizza il passo `fixed` dentro la responsabilita' della classe. |
| `public double getCurrentWindowSeconds()` | public | Restituisce il valore di `CurrentWindowSeconds` senza modificarlo. |
| `public double getNextWindowSeconds()` | public | Restituisce il valore di `NextWindowSeconds` senza modificarlo. |
| `public double getNextWindowDurationSeconds()` | public | Restituisce il valore di `NextWindowDurationSeconds` senza modificarlo. |
| `public TemporalWindowBounds getBounds()` | public | Restituisce il valore di `Bounds` senza modificarlo. |
| `public DynamicityLevel getDynamicityLevel()` | public | Restituisce il valore di `DynamicityLevel` senza modificarlo. |
| `public Action getAction()` | public | Restituisce il valore di `Action` senza modificarlo. |
| `public String getReason()` | public | Restituisce il valore di `Reason` senza modificarlo. |
| `private static double validatePositive(String fieldName, double value)` | private | Controlla la correttezza di `validate positive` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `Action` (tipo interno di `AdaptiveWindowDecision`)

- File: `src/window/timing/AdaptiveWindowDecision.java:10`
- Tipo: `enum`
- Nome completo: `window.timing.AdaptiveWindowDecision.Action`

**Cosa fa, in parole semplici**

Enum: rappresenta un insieme chiuso di valori usati per rendere esplicite le scelte del modello.

**Relazione con la formalizzazione**

Realizza i limiti `DeltaT_min`, `DeltaT_max` e la scelta della finestra temporale.

**Con chi comunica**

Comunica direttamente con: `DynamicityLevel`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Valori enum principali:
`FIRST_RUN`, `INCREASE`, `KEEP`, `DECREASE`, `CLAMP_TO_BOUNDS`

**Metodi**

Questa classe non dichiara metodi propri; contiene soprattutto dati, costanti o valori enum.

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `CoverageReferenceCalculator`

- File: `src/window/timing/CoverageReferenceCalculator.java:28`
- Tipo: `class`
- Nome completo: `window.timing.CoverageReferenceCalculator`

**Cosa fa, in parole semplici**

Calcola il tempo di copertura di riferimento della finestra corrente. La formalizzazione richiede una sola stima per ciascun veicolo osservato, non una stima per ogni candidato computazionale. Nella versione standalone non esiste ancora un modello esplicito del collegamento di accesso radio. Come correzione minima, questa classe usa quindi il migliore candidato EDGE raggiungibile di ciascun veicolo come proxy del relativo nodo infrastrutturale o di accesso. I candidati VEHICLE sono esclusi: descrivono alternative V2V utili al GA, ma non il collegamento infrastrutturale di riferimento del veicolo. LOCAL e CLOUD restano esclusi perché usano tempi convenzionali e falserebbero il bound massimo della finestra.

**Relazione con la formalizzazione**

Implementa la parte mobility-aware: tempo di copertura, instabilita' link, rischio handover e limiti temporali collegati alla copertura.

**Con chi comunica**

Comunica direttamente con: `MobilityConfig`, `NodeCandidate`, `NodeType`, `SystemSnapshot`, `VehicleSnapshot`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final MobilityConfig mobilityConfig`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public CoverageReferenceCalculator(MobilityConfig mobilityConfig)` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public double computeReferenceCoverageSeconds(SystemSnapshot snapshot)` | public | Calcola la media dei migliori tempi di copertura EDGE, una sola volta per ciascun veicolo per il quale è disponibile una proxy infrastrutturale. Se un veicolo dispone di più candidati EDGE, viene usato quello con maggiore copertura residua. In questo modo il numero delle alternative computazionali non modifica artificialmente il peso del veicolo nella media. |
| `public boolean hasReferenceCoverage(SystemSnapshot snapshot)` | public | Risponde con true/false alla domanda `has reference coverage`. |
| `private double estimateEdgeCoverage( NodeCandidate candidate, VehicleSnapshot source )` | private | Stima `estimate edge coverage` senza modificare lo stato principale. |
| `private Map<String, VehicleSnapshot> indexVehicles(SystemSnapshot snapshot)` | private | Prepara una mappa di lookup per trovare rapidamente gli oggetti. |
| `private double euclideanDistance( double x1, double y1, double x2, double y2 )` | private | Metodo di supporto: realizza il passo `euclidean distance` dentro la responsabilita' della classe. |

**Problematiche aperte**

- Il riferimento di copertura e' una scelta aggregata operativa; va verificato rispetto alla definizione finale di `T_ref_coverage(k)`.

### `TemporalOperationalMetrics`

- File: `src/window/timing/TemporalOperationalMetrics.java:16`
- Tipo: `class`
- Nome completo: `window.timing.TemporalOperationalMetrics`

**Cosa fa, in parole semplici**

Tempi operativi usati per calcolare il limite minimo della finestra. Formalmente: DeltaT_min(k) = T_s(k) + T_GA_est(k) + T_apply(k) + epsilon_T Questa classe conserva il valore di T_GA_est usato per il calcolo. Il tempo osservato reale del GA viene mantenuto separato quando serve nella diagnostica.

**Relazione con la formalizzazione**

Realizza i limiti `DeltaT_min`, `DeltaT_max` e la scelta della finestra temporale.

**Con chi comunica**

Comunica principalmente con classi dello stesso package o con il chiamante diretto.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double dataCollectionSeconds`
- `private final double gaRuntimeEstimateSeconds`
- `private final double strategyApplicationSeconds`
- `private final double epsilonSeconds`
- `private final double observedGaRuntimeSeconds`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TemporalOperationalMetrics( double dataCollectionSeconds, double gaRuntimeEstimateSeconds, double strategyApplicationSeconds, double epsilonSeconds )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public TemporalOperationalMetrics( double dataCollectionSeconds, double gaRuntimeEstimateSeconds, double strategyApplicationSeconds, double epsilonSeconds, double observedGaRuntimeSeconds )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public static TemporalOperationalMetrics estimated( double dataCollectionSeconds, double defaultGaRuntimeEstimateSeconds, double strategyApplicationSeconds, double epsilonSeconds )` | public | Stima `estimated` senza modificare lo stato principale. |
| `public static TemporalOperationalMetrics observed( double dataCollectionSeconds, double observedGaRuntimeSeconds, double strategyApplicationSeconds, double epsilonSeconds )` | public | Metodo di supporto: realizza il passo `observed` dentro la responsabilita' della classe. |
| `public TemporalOperationalMetrics withGaRuntimeEstimateSeconds( double newGaRuntimeEstimateSeconds )` | public | Metodo di supporto: realizza il passo `with ga runtime estimate seconds` dentro la responsabilita' della classe. |
| `public double getDataCollectionSeconds()` | public | Restituisce il valore di `DataCollectionSeconds` senza modificarlo. |
| `public double getGaRuntimeEstimateSeconds()` | public | Restituisce il valore di `GaRuntimeEstimateSeconds` senza modificarlo. |
| `public double getStrategyApplicationSeconds()` | public | Restituisce il valore di `StrategyApplicationSeconds` senza modificarlo. |
| `public double getEpsilonSeconds()` | public | Restituisce il valore di `EpsilonSeconds` senza modificarlo. |
| `public double getObservedGaRuntimeSeconds()` | public | Restituisce il valore di `ObservedGaRuntimeSeconds` senza modificarlo. |
| `public double getMinimumWindowSeconds()` | public | Restituisce il valore di `MinimumWindowSeconds` senza modificarlo. |
| `private static double validateFiniteAndNonNegative( String fieldName, double value )` | private | Controlla la correttezza di `validate finite and non negative` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TemporalWindowBounds`

- File: `src/window/timing/TemporalWindowBounds.java:9`
- Tipo: `class`
- Nome completo: `window.timing.TemporalWindowBounds`

**Cosa fa, in parole semplici**

Limiti della prossima finestra temporale.

**Relazione con la formalizzazione**

Realizza i limiti `DeltaT_min`, `DeltaT_max` e la scelta della finestra temporale.

**Con chi comunica**

Comunica direttamente con: `TemporalMaximumBoundMode`, `TemporalMinimumBoundMode`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double minimumWindowSeconds`
- `private final double maximumWindowSeconds`
- `private final double coverageReferenceSeconds`
- `private final boolean coverageReferenceAvailable`
- `private final double adaptiveMaximumWindowSeconds`
- `private final double configuredMaximumWindowSeconds`
- `private final double gaRuntimeEstimateUsedSeconds`
- `private final double observedGaRuntimeSeconds`
- `private final TemporalMinimumBoundMode minimumBoundMode`
- `private final TemporalMaximumBoundMode maximumBoundMode`
- `private final boolean maximumRaisedToMinimum`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TemporalWindowBounds( double minimumWindowSeconds, double maximumWindowSeconds, double coverageReferenceSeconds, boolean coverageReferenceAvailable )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public TemporalWindowBounds( double minimumWindowSeconds, double maximumWindowSeconds, double coverageReferenceSeconds, boolean coverageReferenceAvailable, double adaptiveMaximumWindowSeconds, double configuredMaximumWindowSeconds, double gaRuntimeEstimateUsedSeconds, double observedGaRuntimeSeconds, TemporalMinimumBoundMode minimumBoundMode, TemporalMaximumBoundMode maximumBoundMode, boolean maximumRaisedToMinimum )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public double getMinimumWindowSeconds()` | public | Restituisce il valore di `MinimumWindowSeconds` senza modificarlo. |
| `public double getMaximumWindowSeconds()` | public | Restituisce il valore di `MaximumWindowSeconds` senza modificarlo. |
| `public double getCoverageReferenceSeconds()` | public | Restituisce il valore di `CoverageReferenceSeconds` senza modificarlo. |
| `public boolean isCoverageReferenceAvailable()` | public | Risponde con true/false alla domanda `is coverage reference available`. |
| `public double getAdaptiveMaximumWindowSeconds()` | public | Restituisce il valore di `AdaptiveMaximumWindowSeconds` senza modificarlo. |
| `public double getConfiguredMaximumWindowSeconds()` | public | Restituisce il valore di `ConfiguredMaximumWindowSeconds` senza modificarlo. |
| `public double getGaRuntimeEstimateUsedSeconds()` | public | Restituisce il valore di `GaRuntimeEstimateUsedSeconds` senza modificarlo. |
| `public double getObservedGaRuntimeSeconds()` | public | Restituisce il valore di `ObservedGaRuntimeSeconds` senza modificarlo. |
| `public TemporalMinimumBoundMode getMinimumBoundMode()` | public | Restituisce il valore di `MinimumBoundMode` senza modificarlo. |
| `public TemporalMaximumBoundMode getMaximumBoundMode()` | public | Restituisce il valore di `MaximumBoundMode` senza modificarlo. |
| `public boolean isMaximumRaisedToMinimum()` | public | Risponde con true/false alla domanda `is maximum raised to minimum`. |
| `public double clamp(double value)` | public | Limita un valore dentro un intervallo ammesso. |
| `private static double validatePositive(String fieldName, double value)` | private | Controlla la correttezza di `validate positive` e solleva un'eccezione se trova dati incoerenti. |
| `private static double validateFiniteAndNonNegative( String fieldName, double value )` | private | Controlla la correttezza di `validate finite and non negative` e solleva un'eccezione se trova dati incoerenti. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TemporalWindowBoundsCalculator`

- File: `src/window/timing/TemporalWindowBoundsCalculator.java:24`
- Tipo: `class`
- Nome completo: `window.timing.TemporalWindowBoundsCalculator`

**Cosa fa, in parole semplici**

Calcola DeltaT_min(k) e DeltaT_max(k). La formula resta quella formalizzata: DeltaT_min(k) = T_s(k) + T_GA_est(k) + T_apply(k) + epsilon_T DeltaT_max(k) = alpha_T * T_coverage_ref(k) Le modalità servono solo a decidere se T_GA_est(k) e il limite massimo vengono stimati da valori configurati o da valori adattivi. Il calcolo di T_coverage_ref(k) non viene cambiato. Applica le formule di `DeltaT_min` e `DeltaT_max` usando il profilo runtime scelto.

**Relazione con la formalizzazione**

Realizza i limiti `DeltaT_min`, `DeltaT_max` e la scelta della finestra temporale.

**Con chi comunica**

Comunica direttamente con: `SystemSnapshot`, `TemporalMaximumBoundMode`, `TemporalMinimumBoundMode`, `TemporalWindowConfig`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final TemporalWindowConfig config`
- `private final CoverageReferenceCalculator coverageReferenceCalculator`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public TemporalWindowBoundsCalculator( TemporalWindowConfig config, CoverageReferenceCalculator coverageReferenceCalculator )` | public | Costruttore: crea l'oggetto e controlla che i dati necessari siano validi. |
| `public TemporalWindowBounds compute( SystemSnapshot snapshot, TemporalOperationalMetrics operationalMetrics, double fallbackWindowSeconds )` | public | Calcola `compute` a partire dai dati ricevuti. |
| `private TemporalOperationalMetrics selectMetricsForMinimum( TemporalOperationalMetrics operationalMetrics )` | private | Seleziona `select metrics for minimum` tra alternative disponibili. |
| `private double selectMaximum( double adaptiveMaximum, double configuredMaximum, double fallbackWindowSeconds, boolean hasReferenceCoverage )` | private | Seleziona `select maximum` tra alternative disponibili. |
| `private static void validatePositive(String fieldName, double value)` | private | Controlla la correttezza di `validate positive` e solleva un'eccezione se trova dati incoerenti. |

**Problematiche aperte**

- Se `DeltaT_max < DeltaT_min`, il codice rialza il massimo al minimo e lo segnala; la formalizzazione puo' richiedere invece fallback o riduzione del carico.

## Package `window.trigger`

Modella il motivo temporale della riesecuzione.

### `ReoptimizationTrigger`

- File: `src/window/trigger/ReoptimizationTrigger.java:22`
- Tipo: `class`
- Nome completo: `window.trigger.ReoptimizationTrigger`

**Cosa fa, in parole semplici**

Causa concreta di una riesecuzione del MA-GA. Questo oggetto rappresenta il punto in cui il gestore temporale decide che una nuova ottimizzazione deve essere avviata. Risponde a due domande: quando avviene la riesecuzione, espresso in tempo simulato; perché avviene, espresso tramite `TriggerReason`. Nel caso di `TriggerReason#CRITICAL_EVENT`, il trigger conserva anche l'evento critico che ha anticipato la fine naturale della finestra. Nei casi programmati, invece, non deve esistere alcun evento associato.

**Relazione con la formalizzazione**

Rappresenta la riesecuzione anticipata o programmata del ciclo temporale.

**Con chi comunica**

Comunica direttamente con: `CriticalEvent`.
In pratica questa classe riceve questi oggetti, li costruisce oppure li passa allo step successivo del flusso.

**Campi o valori importanti**

Campi dichiarati principali:
- `private final double triggerTimeSeconds`
- `private final TriggerReason reason`
- `private final CriticalEvent criticalEvent`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public ReoptimizationTrigger( double triggerTimeSeconds, TriggerReason reason, CriticalEvent criticalEvent )` | public | Costruisce un trigger di riesecuzione. Regola di consistenza principale: se `reason == CRITICAL_EVENT`, `criticalEvent` deve essere valorizzato; se `reason != CRITICAL_EVENT`, `criticalEvent` deve essere `null`. Questa regola evita stati ambigui, per esempio un trigger dichiarato critico ma senza evento associato. @param triggerTimeSeconds tempo simulato della riesecuzione @param reason motivo della riesecuzione @param criticalEvent evento critico associato, solo per trigger critici |
| `public static ReoptimizationTrigger firstRun(double triggerTimeSeconds)` | public | Crea un trigger per la prima esecuzione del sistema. Non esiste ancora una finestra precedente e non c'è alcun evento critico associato. @param triggerTimeSeconds tempo simulato della prima esecuzione @return trigger con motivo `TriggerReason#FIRST_RUN` |
| `public static ReoptimizationTrigger scheduledExpiration( double triggerTimeSeconds )` | public | Crea un trigger per scadenza naturale della finestra temporale. Questo corrisponde al caso: t_{k+1} = t_k + Delta_t senza eventi critici intermedi. @param triggerTimeSeconds tempo simulato della scadenza programmata @return trigger con motivo `TriggerReason#SCHEDULED_WINDOW_EXPIRATION` |
| `public static ReoptimizationTrigger criticalEvent( CriticalEvent criticalEvent )` | public | Crea un trigger causato da evento critico. Questo corrisponde al caso: t_crit quindi la nuova ottimizzazione viene anticipata rispetto alla scadenza naturale della finestra. @param criticalEvent evento che causa la riesecuzione @return trigger con motivo `TriggerReason#CRITICAL_EVENT` |
| `public double getTriggerTimeSeconds()` | public | @return tempo simulato in cui avviene la riesecuzione |
| `public TriggerReason getReason()` | public | @return motivo della riesecuzione |
| `public CriticalEvent getCriticalEvent()` | public | @return evento critico associato, oppure `null` per trigger non critici |
| `public boolean isCriticalEventTrigger()` | public | Restituisce true se il trigger deriva da un evento critico. @return `true` se `#getReason()` è `TriggerReason#CRITICAL_EVENT` |
| `public boolean isScheduledExpiration()` | public | Restituisce true se il trigger deriva dalla scadenza naturale della finestra. @return `true` se il trigger è programmato |
| `public boolean isFirstRun()` | public | Restituisce true se il trigger rappresenta la prima esecuzione. @return `true` se il trigger è la prima esecuzione |
| `private static void validateCriticalEventConsistency( TriggerReason reason, CriticalEvent criticalEvent )` | private | Controlla la coerenza tra reason e criticalEvent. Questa validazione serve a evitare oggetti semanticamente inconsistenti e concentra in un solo punto l'invariante tra motivo e payload opzionale. |
| `private static void validateFiniteAndNonNegative( String fieldName, double value )` | private | Valida che un tempo simulato sia finito e non negativo. |
| `public String toString()` | public | Converte l'oggetto in una stringa utile per debug e report. |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

### `TriggerReason`

- File: `src/window/trigger/TriggerReason.java:19`
- Tipo: `enum`
- Nome completo: `window.trigger.TriggerReason`

**Cosa fa, in parole semplici**

Motivo per cui il gestore temporale decide di rieseguire il MA-GA. Questo enum descrive la causa temporale della nuova ottimizzazione. Non contiene lo snapshot da ottimizzare e non decide la modalità di riuso della popolazione: fornisce solo l'informazione sul perché il sistema sta avviando una nuova esecuzione. Le cause previste sono: prima finestra temporale, senza storia precedente; scadenza naturale dell'intervallo programmato; evento critico che anticipa la fine della finestra corrente.

**Relazione con la formalizzazione**

Rappresenta la riesecuzione anticipata o programmata del ciclo temporale.

**Con chi comunica**

Comunica come etichetta condivisa: altre classi lo usano per evitare stringhe libere e rendere esplicite le scelte.

**Campi o valori importanti**

Valori enum principali:
`FIRST_RUN`, `SCHEDULED_WINDOW_EXPIRATION`, `CRITICAL_EVENT`

**Metodi**

| Metodo | Visibilita' | Spiegazione semplice |
|---|---:|---|
| `public boolean isCritical()` | public | Indica se la riesecuzione è stata causata da un evento critico. @return `true` solo per `#CRITICAL_EVENT` |
| `public boolean isScheduled()` | public | Indica se la riesecuzione corrisponde alla scadenza programmata. @return `true` solo per `#SCHEDULED_WINDOW_EXPIRATION` |

**Problematiche aperte**

Nessuna specifica nota per questa classe.

## 8. Percorso consigliato di studio

1. Parti da `model.snapshot`, `model.node`, `model.genetic`: capisci i dati.
2. Leggi `model.offloading` e `model.mobility`: capisci tempi, copertura e instabilita'.
3. Passa a `ga.constraints` e `ga.operators.RepairOperator`: capisci come il codice prova a rispettare deadline e copertura.
4. Leggi `ga.fitness.FitnessEvaluator`: capisci come viene calcolata `J(C)`.
5. Studia `ga.core.MaGaOptimizer`: vedi il ciclo evolutivo e il repair incrementale.
6. Poi passa a `window.source`, `window.prefilter`, `window.core`: capisci il ciclo temporale.
7. Infine usa `io.reporting`: i report spiegano dove nascono costi, saturazioni e fallback.

## 9. Sintesi finale

La versione corrente e' piu' vicina alla formalizzazione rispetto alla precedente: deadline, latenza e mobilita' sono trattate in modo piu' esplicito e diagnosticabile. Le principali differenze residue sono concentrate su banda globale, modello di mobilita' semplificato, bridge MOSAIC assente e gestione operativa dei casi in cui i bounds temporali entrano in conflitto.
