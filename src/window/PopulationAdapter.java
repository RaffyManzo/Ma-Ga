package window;

import config.TemporalWindowConfig;
import ga.PopulationInitializer;
import ga.RepairOperator;
import model.Chromosome;
import model.Gene;
import model.SystemSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Adatta la popolazione genetica finale di una finestra temporale precedente
 * allo snapshot corrente.
 *
 * <p>Questa classe è il collegamento operativo tra il gestore temporale e il
 * MA-GA snapshot-based. Il gestore temporale decide una
 * {@link PopulationReuseMode}; l'adattatore traduce quella decisione in una
 * popolazione iniziale concreta per la nuova esecuzione.</p>
 *
 * <p>Formalmente, se la finestra precedente ha prodotto:</p>
 *
 * <pre>
 * P_final(k-1)
 * </pre>
 *
 * <p>all'arrivo della finestra {@code k}, o di un evento critico che anticipa
 * la riesecuzione, questa classe costruisce:</p>
 *
 * <pre>
 * P_init(k)
 * </pre>
 *
 * <p>Responsabilità escluse: {@code PopulationAdapter} non esegue il Genetic
 * Algorithm, non calcola crossover, non calcola mutazione e non calcola
 * fitness. Il suo compito è solo preparare una popolazione iniziale coerente
 * con lo snapshot corrente. L'evoluzione vera e propria rimane responsabilità
 * di {@code MaGaOptimizer}.</p>
 *
 * <p>La classe è separata dal package {@code ga} perché la popolazione
 * precedente potrebbe essere parzialmente incompatibile con il nuovo snapshot.
 * Per esempio:</p>
 *
 * <ul>
 *     <li>alcuni task della finestra precedente potrebbero non esistere più;</li>
 *     <li>potrebbero essere comparsi nuovi task;</li>
 *     <li>un candidato scelto in un gene potrebbe non essere più disponibile;</li>
 *     <li>un link potrebbe essere peggiorato;</li>
 *     <li>un nodo potrebbe avere meno risorse;</li>
 *     <li>un veicolo potrebbe essere uscito dallo scenario.</li>
 * </ul>
 *
 * <p>Per questo motivo ogni cromosoma riutilizzato viene copiato e riparato
 * rispetto allo snapshot corrente prima di essere inserito nella nuova
 * popolazione iniziale.</p>
 */
public final class PopulationAdapter {

    /**
     * Configurazione del gestore temporale.
     *
     * <p>Da qui viene letto soprattutto {@code rhoKeep}, cioè la quota della
     * popolazione precedente da conservare in caso di
     * {@link PopulationReuseMode#PARTIAL_RESTART}.</p>
     */
    private final TemporalWindowConfig config;

    /**
     * Operatore di riparazione già usato dal package ga.
     *
     * <p>Viene riutilizzato qui per non duplicare le regole di validità
     * source-aware dei cromosomi rispetto allo snapshot corrente.</p>
     */
    private final RepairOperator repairOperator;

    /**
     * Generatore della popolazione iniziale casuale già usato dal package ga.
     *
     * <p>Serve quando:</p>
     *
     * <ul>
     *     <li>siamo in {@link PopulationReuseMode#FIRST_RUN};</li>
     *     <li>siamo in {@link PopulationReuseMode#COLD_START};</li>
     *     <li>siamo in {@link PopulationReuseMode#PARTIAL_RESTART} e bisogna rigenerare la parte mancante;</li>
     *     <li>la popolazione precedente è vuota o non recuperabile.</li>
     * </ul>
     */
    private final PopulationInitializer populationInitializer;

    /**
     * Generatore pseudo-casuale condiviso dagli operatori di inizializzazione.
     *
     * <p>Qui viene conservato solo per poter costruire
     * {@link PopulationInitializer} e mantenere coerenza con il resto degli
     * operatori che dipendono dalla casualità.</p>
     */
    private final Random random;

    // ---------------------------------------------------------------------
    // Costruzione
    // ---------------------------------------------------------------------

    /**
     * Costruisce un adattatore di popolazione.
     *
     * @param config configurazione temporale contenente, tra gli altri, {@code rhoKeep}
     * @param random generatore pseudo-casuale condiviso con l'inizializzatore
     * @throws NullPointerException se {@code config} o {@code random} sono {@code null}
     */
    public PopulationAdapter(
            TemporalWindowConfig config,
            Random random
    ) {
        this.config = Objects.requireNonNull(
                config,
                "config must not be null."
        );

        this.random = Objects.requireNonNull(
                random,
                "random must not be null."
        );

        this.repairOperator = new RepairOperator();
        this.populationInitializer = new PopulationInitializer(
                this.random,
                this.repairOperator
        );
    }

    // ---------------------------------------------------------------------
    // API pubblica
    // ---------------------------------------------------------------------

    /**
     * Costruisce la popolazione iniziale da passare al MA-GA nella finestra
     * corrente.
     *
     * <p>Questo metodo è il punto principale della classe.</p>
     *
     * <p>Input concettuali:</p>
     *
     * <ul>
     *     <li>{@code previousFinalPopulation = P_final(k-1)};</li>
     *     <li>{@code currentSnapshot = S_k};</li>
     *     <li>{@code reuseMode}: decisione temporale presa dal gestore;</li>
     *     <li>{@code targetPopulationSize}: dimensione desiderata della popolazione.</li>
     * </ul>
     *
     * <p>Output:</p>
     *
     * <pre>
     * P_init(k)
     * </pre>
     *
     * <p>Regole:</p>
     *
     * <ul>
     *     <li>{@code FIRST_RUN}: non esiste una popolazione precedente; si genera tutto da zero;</li>
     *     <li>{@code COLD_START}: lo scenario è cambiato troppo; si ignora la popolazione precedente;</li>
     *     <li>{@code WARM_START}: si prova a riutilizzare tutta la popolazione precedente riparata;</li>
     *     <li>{@code PARTIAL_RESTART}: si conserva una quota dei migliori cromosomi e si rigenera il resto.</li>
     * </ul>
     *
     * <p>Anche se {@code MaGaOptimizer} fosse capace di ricevere una lista vuota
     * e generare internamente la popolazione, qui viene restituita sempre una
     * popolazione concreta. Questo rende il comportamento del package
     * {@code window} più esplicito e più facile da verificare nei test.</p>
     *
     * @param previousFinalPopulation popolazione finale della finestra precedente
     * @param currentSnapshot snapshot su cui preparare la nuova popolazione
     * @param reuseMode modalità di riuso scelta dal gestore temporale
     * @param targetPopulationSize dimensione desiderata della popolazione iniziale
     * @return popolazione iniziale coerente con lo snapshot corrente
     */
    public List<Chromosome> adaptPopulation(
            List<Chromosome> previousFinalPopulation,
            SystemSnapshot currentSnapshot,
            PopulationReuseMode reuseMode,
            int targetPopulationSize
    ) {
        Objects.requireNonNull(
                currentSnapshot,
                "currentSnapshot must not be null."
        );

        Objects.requireNonNull(
                reuseMode,
                "reuseMode must not be null."
        );

        if (targetPopulationSize < 1) {
            throw new IllegalArgumentException(
                    "targetPopulationSize must be >= 1."
            );
        }

        // Caso 1: prima esecuzione. Non esiste memoria genetica precedente.
        if (reuseMode == PopulationReuseMode.FIRST_RUN) {
            return createFreshPopulation(currentSnapshot, targetPopulationSize);
        }

        // Caso 2: cold start. Lo scenario è troppo diverso per riusare P_final(k-1).
        if (reuseMode == PopulationReuseMode.COLD_START) {
            return createFreshPopulation(currentSnapshot, targetPopulationSize);
        }

        /*
         * Prima di applicare warm start o partial restart, la popolazione precedente
         * viene normalizzata:
         *
         * - vengono rimossi elementi null;
         * - ogni cromosoma viene copiato;
         * - ogni cromosoma viene riparato rispetto allo snapshot corrente;
         * - la popolazione viene ordinata per fitness crescente.
         *
         * Nota:
         * in questo progetto la fitness è da minimizzare, quindi fitness più bassa
         * significa cromosoma migliore.
         */
        List<Chromosome> repairedPreviousPopulation =
                repairAndSortPreviousPopulation(
                        previousFinalPopulation,
                        currentSnapshot
                );

        /*
         * Se la popolazione precedente non è disponibile oppure non è recuperabile,
         * anche warm start e partial restart degradano in una generazione fresca.
         *
         * Questa scelta rende il sistema robusto:
         * il gestore temporale non fallisce solo perché manca P_final(k-1).
         */
        if (repairedPreviousPopulation.isEmpty()) {
            return createFreshPopulation(currentSnapshot, targetPopulationSize);
        }

        // Caso 3: warm start. Conserva più informazione genetica possibile.
        if (reuseMode == PopulationReuseMode.WARM_START) {
            return buildWarmStartPopulation(
                    repairedPreviousPopulation,
                    currentSnapshot,
                    targetPopulationSize
                );
        }

        // Caso 4: partial restart. Mantiene solo una quota dei migliori cromosomi.
        if (reuseMode == PopulationReuseMode.PARTIAL_RESTART) {
            return buildPartialRestartPopulation(
                    repairedPreviousPopulation,
                    currentSnapshot,
                    targetPopulationSize
            );
        }

        /*
         * Questo ramo non dovrebbe essere raggiunto, ma rende il metodo robusto
         * nel caso in cui in futuro vengano aggiunte nuove modalità all'enum.
         */
        return createFreshPopulation(currentSnapshot, targetPopulationSize);
    }

    // ---------------------------------------------------------------------
    // Strategie di costruzione della popolazione
    // ---------------------------------------------------------------------

    /**
     * Crea una popolazione nuova da zero.
     *
     * <p>Questo metodo viene usato nei casi:</p>
     *
     * <ul>
     *     <li>{@link PopulationReuseMode#FIRST_RUN};</li>
     *     <li>{@link PopulationReuseMode#COLD_START};</li>
     *     <li>fallback quando la popolazione precedente non è recuperabile.</li>
     * </ul>
     *
     * @param snapshot snapshot corrente
     * @param targetPopulationSize dimensione desiderata
     * @return popolazione generata interamente da zero
     */
    private List<Chromosome> createFreshPopulation(
            SystemSnapshot snapshot,
            int targetPopulationSize
    ) {
        return populationInitializer.createInitialPopulation(
                snapshot,
                targetPopulationSize
        );
    }

    /**
     * Prepara la popolazione precedente al riuso.
     *
     * <p>Questa è una delle sezioni più importanti della classe. La popolazione
     * finale della finestra precedente non può essere riutilizzata direttamente,
     * perché lo snapshot corrente potrebbe essere cambiato.</p>
     *
     * <p>Per questo motivo ogni cromosoma viene:</p>
     *
     * <ol>
     *     <li>copiato;</li>
     *     <li>riparato rispetto allo snapshot corrente;</li>
     *     <li>inserito in una nuova lista;</li>
     *     <li>ordinato per fitness crescente.</li>
     * </ol>
     *
     * <p>La copia evita effetti collaterali: il package {@code window} non deve
     * modificare oggetti già conservati in vecchi risultati.</p>
     *
     * @param previousFinalPopulation popolazione prodotta nella finestra precedente
     * @param currentSnapshot snapshot corrente usato per riparare i cromosomi
     * @return cromosomi copiati, riparati e ordinati per fitness crescente
     */
    private List<Chromosome> repairAndSortPreviousPopulation(
            List<Chromosome> previousFinalPopulation,
            SystemSnapshot currentSnapshot
    ) {
        List<Chromosome> repaired = new ArrayList<>();

        if (previousFinalPopulation == null || previousFinalPopulation.isEmpty()) {
            return repaired;
        }

        for (Chromosome chromosome : previousFinalPopulation) {
            if (chromosome == null || chromosome.getGenes() == null) {
                continue;
            }

            // Copia prima della riparazione: la popolazione storica resta intatta.
            Chromosome copied = copyChromosome(chromosome);
            Chromosome repairedChromosome =
                    repairOperator.repairChromosome(copied, currentSnapshot);

            repaired.add(repairedChromosome);
        }

        repaired.sort(Comparator.comparingDouble(Chromosome::getFitness));
        return repaired;
    }

    /**
     * Costruisce la popolazione per WARM_START.
     *
     * <p>Logica:</p>
     *
     * <ul>
     *     <li>si conservano i migliori cromosomi riparati;</li>
     *     <li>se sono meno della dimensione richiesta, si genera la parte mancante;</li>
     *     <li>se sono più della dimensione richiesta, si tagliano i peggiori.</li>
     * </ul>
     *
     * <p>In questo modo il warm start prova a mantenere continuità genetica tra
     * finestre consecutive, ma resta compatibile con la dimensione di
     * popolazione richiesta dal GA.</p>
     */
    private List<Chromosome> buildWarmStartPopulation(
            List<Chromosome> repairedPreviousPopulation,
            SystemSnapshot currentSnapshot,
            int targetPopulationSize
    ) {
        List<Chromosome> result = new ArrayList<>();

        int keepCount = Math.min(
                targetPopulationSize,
                repairedPreviousPopulation.size()
        );

        for (int i = 0; i < keepCount; i++) {
            result.add(copyChromosome(repairedPreviousPopulation.get(i)));
        }

        fillWithFreshChromosomes(
                result,
                currentSnapshot,
                targetPopulationSize
        );

        return result;
    }

    /**
     * Costruisce la popolazione per PARTIAL_RESTART.
     *
     * <p>Logica:</p>
     *
     * <ul>
     *     <li>si calcola quanti cromosomi conservare usando {@code rhoKeep};</li>
     *     <li>si prendono i migliori cromosomi della popolazione precedente riparata;</li>
     *     <li>il resto viene generato da zero sullo snapshot corrente.</li>
     * </ul>
     *
     * <p>Esempio:</p>
     *
     * <pre>
     * targetPopulationSize = 40
     * rhoKeep = 0.40
     *
     * allora:
     *
     * keepCount = 16
     * freshCount = 24
     *
     * Quindi P_init(k) sarà composta da:
     *
     * - 16 cromosomi riusati;
     * - 24 cromosomi nuovi.
     * </pre>
     */
    private List<Chromosome> buildPartialRestartPopulation(
            List<Chromosome> repairedPreviousPopulation,
            SystemSnapshot currentSnapshot,
            int targetPopulationSize
    ) {
        List<Chromosome> result = new ArrayList<>();

        int keepCount = computePartialRestartKeepCount(
                repairedPreviousPopulation.size(),
                targetPopulationSize
        );

        for (int i = 0; i < keepCount; i++) {
            result.add(copyChromosome(repairedPreviousPopulation.get(i)));
        }

        fillWithFreshChromosomes(
                result,
                currentSnapshot,
                targetPopulationSize
        );

        return result;
    }

    /**
     * Calcola quanti cromosomi mantenere in partial restart.
     *
     * <p>La formula base è:</p>
     *
     * <pre>
     * keepCount = round(rhoKeep * targetPopulationSize)
     * </pre>
     *
     * <p>Il valore viene poi limitato in modo da:</p>
     *
     * <ul>
     *     <li>non superare la popolazione precedente disponibile;</li>
     *     <li>non superare la dimensione target;</li>
     *     <li>mantenere almeno un cromosoma se {@code rhoKeep > 0} e se esiste una popolazione precedente non vuota.</li>
     * </ul>
     */
    private int computePartialRestartKeepCount(
            int availablePreviousCount,
            int targetPopulationSize
    ) {
        if (availablePreviousCount <= 0 || config.getRhoKeep() <= 0.0) {
            return 0;
        }

        int keepCount = (int) Math.round(
                config.getRhoKeep() * targetPopulationSize
        );

        if (keepCount < 1) {
            keepCount = 1;
        }

        keepCount = Math.min(keepCount, availablePreviousCount);
        keepCount = Math.min(keepCount, targetPopulationSize);

        return keepCount;
    }

    /**
     * Completa una popolazione parziale con cromosomi nuovi.
     *
     * <p>Questo metodo viene usato sia nel warm start sia nel partial restart.
     * Dopo aver riutilizzato una parte della popolazione precedente, può mancare
     * ancora un certo numero di cromosomi per raggiungere la dimensione
     * richiesta dal GA.</p>
     */
    private void fillWithFreshChromosomes(
            List<Chromosome> result,
            SystemSnapshot currentSnapshot,
            int targetPopulationSize
    ) {
        int missing = targetPopulationSize - result.size();

        if (missing <= 0) {
            trimToTargetSize(result, targetPopulationSize);
            return;
        }

        List<Chromosome> freshChromosomes =
                populationInitializer.createInitialPopulation(
                        currentSnapshot,
                        missing
                );

        result.addAll(freshChromosomes);
        trimToTargetSize(result, targetPopulationSize);
    }

    /**
     * Taglia la popolazione alla dimensione richiesta.
     *
     * <p>Se per qualche errore o cambiamento futuro la lista diventasse più
     * grande del necessario, vengono rimossi gli ultimi cromosomi.</p>
     *
     * <p>Prima del taglio la lista viene ordinata per fitness crescente.</p>
     */
    private void trimToTargetSize(
            List<Chromosome> chromosomes,
            int targetPopulationSize
    ) {
        chromosomes.sort(Comparator.comparingDouble(Chromosome::getFitness));

        while (chromosomes.size() > targetPopulationSize) {
            chromosomes.remove(chromosomes.size() - 1);
        }
    }

    /**
     * Crea una copia superficiale di un cromosoma.
     *
     * <p>I {@link Gene} sono immutabili nel modello attuale, quindi è
     * sufficiente copiare la lista dei geni senza ricostruire ogni singolo
     * gene.</p>
     *
     * <p>Se in futuro {@code Gene} diventasse mutabile, questo metodo andrebbe
     * aggiornato per creare una copia profonda dei geni.</p>
     */
    private Chromosome copyChromosome(Chromosome source) {
        List<Gene> copiedGenes = new ArrayList<>(source.getGenes());

        Chromosome copy = new Chromosome(copiedGenes);
        copy.setFitness(source.getFitness());

        return copy;
    }
}
