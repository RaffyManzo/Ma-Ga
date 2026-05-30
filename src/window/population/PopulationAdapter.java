package window.population;

import config.MaGaConfig;
import config.window.TemporalWindowConfig;
import ga.fitness.FitnessEvaluator;
import ga.operators.PopulationInitializer;
import ga.operators.RepairOperator;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Adatta la popolazione genetica finale di una finestra temporale precedente
 * allo snapshot corrente.
 *
 * <p>È il ponte tra il ciclo temporale e l'ottimizzatore snapshot-based: il
 * gestore temporale sceglie una {@link PopulationReuseMode}, mentre questo
 * adattatore costruisce la popolazione iniziale {@code P_init(k)} per la nuova
 * finestra.</p>
 *
 * <ul>
 *     <li>{@code FIRST_RUN} e {@code COLD_START}: genera una popolazione nuova;</li>
 *     <li>{@code WARM_START}: prova a riusare tutta la popolazione precedente;</li>
 *     <li>{@code PARTIAL_RESTART}: conserva una quota dei migliori cromosomi e
 *     rigenera il resto.</li>
 * </ul>
 *
 * <p>Ogni cromosoma riusato viene copiato, riparato e rivalutato rispetto allo
 * snapshot corrente. La fitness storica non decide quali cromosomi conservare
 * nella nuova finestra.</p>
 */
public final class PopulationAdapter {

    /**
     * Configurazione temporale; in questa classe viene usata soprattutto
     * {@code rhoKeep} per calcolare la quota conservata in partial restart.
     */
    private final TemporalWindowConfig config;

    /**
     * Operatore usato per rendere i cromosomi storici compatibili con lo
     * snapshot corrente prima del riuso.
     */
    private final RepairOperator repairOperator;

    /**
     * Valuta i cromosomi adattati usando lo snapshot corrente.
     */
    private final FitnessEvaluator fitnessEvaluator;

    /**
     * Generatore usato per creare cromosomi freschi quando il riuso non è
     * possibile o quando una popolazione parziale va completata.
     */
    private final PopulationInitializer populationInitializer;

    /**
     * Generatore pseudo-casuale condiviso con l'inizializzatore.
     */
    private final Random random;

    /**
     * Costruisce un adattatore di popolazione.
     *
     * @param config configurazione temporale contenente, tra gli altri, {@code rhoKeep}
     * @param maGaConfig configurazione usata da repair e fitness
     * @param random generatore pseudo-casuale condiviso con l'inizializzatore
     * @throws NullPointerException se un parametro richiesto e' {@code null}
     */
    public PopulationAdapter(
            TemporalWindowConfig config,
            MaGaConfig maGaConfig,
            Random random
    ) {
        this.config = Objects.requireNonNull(
                config,
                "config must not be null."
        );

        MaGaConfig safeMaGaConfig = Objects.requireNonNull(
                maGaConfig,
                "maGaConfig must not be null."
        );

        this.random = Objects.requireNonNull(
                random,
                "random must not be null."
        );

        this.repairOperator = new RepairOperator(
                safeMaGaConfig.getMobilityConfig()
        );
        this.fitnessEvaluator = new FitnessEvaluator(safeMaGaConfig);
        this.populationInitializer = new PopulationInitializer(
                this.random,
                this.repairOperator
        );
    }

    /**
     * Costruisce la popolazione iniziale da passare al MA-GA nella finestra
     * corrente.
     *
     * <p>Il metodo restituisce sempre una popolazione concreta, così il package
     * {@code window} rende esplicita la strategia scelta prima di invocare
     * {@code MaGaOptimizer}.</p>
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

        // Prima esecuzione: non esiste memoria genetica precedente.
        if (reuseMode == PopulationReuseMode.FIRST_RUN) {
            return createFreshPopulation(currentSnapshot, targetPopulationSize);
        }

        // Cold start: lo scenario è troppo diverso per riusare P_final(k-1).
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
         * - ogni cromosoma viene rivalutato sullo snapshot corrente;
         * - la popolazione viene ordinata per fitness corrente crescente.
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

        // Warm start: conserva più informazione genetica possibile.
        if (reuseMode == PopulationReuseMode.WARM_START) {
            return buildWarmStartPopulation(
                    repairedPreviousPopulation,
                    currentSnapshot,
                    targetPopulationSize
                );
        }

        // Partial restart: mantiene solo una quota dei migliori cromosomi.
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

    /**
     * Crea una popolazione nuova da zero.
     *
     * @param snapshot snapshot corrente
     * @param targetPopulationSize dimensione desiderata
     * @return popolazione generata interamente da zero
     */
    private List<Chromosome> createFreshPopulation(
            SystemSnapshot snapshot,
            int targetPopulationSize
    ) {
        List<Chromosome> freshPopulation =
                populationInitializer.createInitialPopulation(
                        snapshot,
                        targetPopulationSize
                );

        evaluatePopulation(freshPopulation, snapshot);
        return freshPopulation;
    }

    /**
     * Prepara la popolazione precedente al riuso.
     *
     * <p>Ogni cromosoma storico viene:</p>
     *
     * <ol>
     *     <li>copiato;</li>
     *     <li>riparato rispetto allo snapshot corrente;</li>
     *     <li>rivalutato sullo snapshot corrente;</li>
     *     <li>ordinato per fitness corrente crescente.</li>
     * </ol>
     *
     * <p>La copia evita effetti collaterali sui risultati delle finestre già
     * completate.</p>
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

            repairedChromosome.setFitness(
                    fitnessEvaluator.evaluate(
                            repairedChromosome,
                            currentSnapshot
                    )
            );

            repaired.add(repairedChromosome);
        }

        repaired.sort(Comparator.comparingDouble(Chromosome::getFitness));
        return repaired;
    }

    /**
     * Costruisce la popolazione per WARM_START.
     *
     * <p>Conserva i migliori cromosomi riparati e genera eventuali cromosomi
     * mancanti, mantenendo la dimensione target richiesta dal GA.</p>
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
     * <p>Conserva {@code rhoKeep * targetPopulationSize} cromosomi riparati e
     * genera da zero il resto della popolazione.</p>
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
     * <p>La formula base è {@code round(rhoKeep * targetPopulationSize)}. Il
     * risultato viene poi limitato per:</p>
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
     * Rivaluta una popolazione rispetto allo snapshot corrente.
     */
    private void evaluatePopulation(
            List<Chromosome> population,
            SystemSnapshot snapshot
    ) {
        for (Chromosome chromosome : population) {
            if (chromosome == null || chromosome.getGenes() == null) {
                continue;
            }

            chromosome.setFitness(
                    fitnessEvaluator.evaluate(chromosome, snapshot)
            );
        }
    }

    /**
     * Completa una popolazione parziale con cromosomi nuovi.
     *
     * <p>È usato sia dal warm start sia dal partial restart quando la porzione
     * riusata non raggiunge ancora la dimensione target.</p>
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
                createFreshPopulation(
                        currentSnapshot,
                        missing
                );

        result.addAll(freshChromosomes);
        trimToTargetSize(result, targetPopulationSize);
    }

    /**
     * Taglia la popolazione alla dimensione richiesta.
     *
     * <p>Prima del taglio ordina per fitness crescente, così vengono rimossi i
     * cromosomi peggiori.</p>
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
     * <p>La copia è superficiale perché {@link Gene} è immutabile nel modello
     * attuale.</p>
     */
    private Chromosome copyChromosome(Chromosome source) {
        List<Gene> copiedGenes = new ArrayList<>(source.getGenes());

        Chromosome copy = new Chromosome(copiedGenes);
        copy.setFitness(source.getFitness());

        return copy;
    }
}



