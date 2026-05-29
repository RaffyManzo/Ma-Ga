package ga.core;

import config.MaGaConfig;
import config.ga.GeneticAlgorithmConfig;
import ga.fitness.FitnessEvaluator;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.operators.CrossoverOperator;
import ga.operators.ElitismOperator;
import ga.operators.MutationOperator;
import ga.operators.PopulationInitializer;
import ga.operators.RepairOperator;
import ga.operators.SelectionOperator;
import model.genetic.Chromosome;
import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Orchestratore principale del MA-GA sul singolo snapshot.
 *
 * <p>Il MA-GA resta snapshot-based:</p>
 *
 * <ul>
 *     <li>riceve uno SystemSnapshot;</li>
 *     <li>riceve eventualmente una popolazione iniziale esterna;</li>
 *     <li>ripara la popolazione rispetto allo snapshot corrente;</li>
 *     <li>evolve la popolazione;</li>
 *     <li>restituisce miglior cromosoma, breakdown e popolazione finale.</li>
 * </ul>
 *
 * <p>La scelta tra COLD_START, WARM_START e PARTIAL_RESTART non appartiene
 * a questa classe. Quella decisione rimane nel package window.</p>
 */
public final class MaGaOptimizer {

    private static final int DEFAULT_TOURNAMENT_SIZE = 3;

    private final MaGaConfig config;
    private GeneticAlgorithmConfig gaConfig;
    private final FitnessEvaluator fitnessEvaluator;
    private final PopulationInitializer populationInitializer;
    private final RepairOperator repairOperator;
    private final SelectionOperator selectionOperator;
    private final CrossoverOperator crossoverOperator;
    private final MutationOperator mutationOperator;
    private final ElitismOperator elitismOperator;
    private final Random random;

    public MaGaOptimizer(MaGaConfig config) {
        this.config = Objects.requireNonNull(
                config,
                "config must not be null."
        );
        this.gaConfig = Objects.requireNonNull(
                config.getGeneticAlgorithmConfig(),
                "geneticAlgorithmConfig must not be null."
        );
        this.random = new Random(gaConfig.getRandomSeed());

        /*
         * Il repair riceve la MobilityConfig per usare lo stesso modello di
         * copertura della fitness. In questo modo il vincolo
         * T_i(C) <= T_i^coverage(n_i) viene controllato anche in repair,
         * non solo penalizzato in valutazione.
         */
        this.repairOperator = new RepairOperator(config.getMobilityConfig());

        this.fitnessEvaluator = new FitnessEvaluator(config);
        this.populationInitializer = new PopulationInitializer(random, repairOperator);
        this.selectionOperator = new SelectionOperator(random, DEFAULT_TOURNAMENT_SIZE);
        this.crossoverOperator = new CrossoverOperator(random);
        this.mutationOperator = new MutationOperator(random);
        this.elitismOperator = new ElitismOperator();
    }

    /**
     * Esegue il MA-GA partendo da popolazione generata internamente.
     *
     * <p>Metodo mantenuto per compatibilità.</p>
     */
    public Chromosome optimize(SystemSnapshot snapshot) {
        return optimizeDetailed(snapshot).getBestChromosome();
    }

    /**
     * Esegue il MA-GA partendo da una popolazione iniziale esterna.
     */
    public Chromosome optimize(
            SystemSnapshot snapshot,
            List<Chromosome> initialPopulation
    ) {
        return optimizeDetailed(snapshot, initialPopulation).getBestChromosome();
    }

    /**
     * Esegue il MA-GA e restituisce il risultato completo.
     *
     * <p>Metodo mantenuto per compatibilità.</p>
     */
    public MaGaResult optimizeDetailed(SystemSnapshot snapshot) {
        return optimizeDetailed(snapshot, null);
    }

    /**
     * Esegue il MA-GA e restituisce un risultato completo.
     */
    public MaGaResult optimizeDetailed(
            SystemSnapshot snapshot,
            List<Chromosome> initialPopulation
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        this.gaConfig = config.resolveGeneticAlgorithmConfig(snapshot);
        validateSnapshot(snapshot);

        List<GenerationStat> generationHistory = new ArrayList<>();

        if (snapshot.getTasks().isEmpty()) {
            Chromosome empty = new Chromosome(new ArrayList<>());
            empty.setFitness(0.0);
            EvaluationBreakdown evaluation = fitnessEvaluator.evaluateDetailed(empty, snapshot);
            List<Chromosome> finalPopulation = new ArrayList<>();
            finalPopulation.add(copyChromosome(empty));

            return new MaGaResult(
                    snapshot.getSnapshotId(),
                    snapshot.getTimeSeconds(),
                    empty,
                    evaluation,
                    0,
                    StopReason.EMPTY_TASK_SET,
                    0.0,
                    0.0,
                    generationHistory,
                    finalPopulation
            );
        }

        List<Chromosome> population = prepareInitialPopulation(
                snapshot,
                initialPopulation
        );

        evaluatePopulation(population, snapshot);

        GenerationStat initialStat = computeGenerationStat(0, population);
        generationHistory.add(initialStat);

        Chromosome bestOverall = copyChromosome(findBest(population));
        double initialBestFitness = bestOverall.getFitness();

        int stallCounter = 0;
        int generationsExecuted = 0;
        StopReason stopReason = StopReason.MAX_GENERATIONS_REACHED;

        for (int generation = 1; generation <= gaConfig.getMaxGenerations(); generation++) {
            List<Chromosome> nextPopulation = new ArrayList<>();

            nextPopulation.addAll(
                    elitismOperator.selectElite(
                            population,
                            gaConfig.getElitismCount()
                    )
            );

            while (nextPopulation.size() < gaConfig.getPopulationSize()) {
                Chromosome parentA = selectionOperator.select(population);
                Chromosome parentB = selectionOperator.select(population);

                Chromosome child;

                if (shouldApplyCrossover()) {
                    child = crossoverOperator.crossover(parentA, parentB);
                } else {
                    child = crossoverOperator.copyChromosome(parentA);
                }

                child = mutationOperator.mutate(
                        child,
                        snapshot,
                        gaConfig.getMutationRate()
                );
                child = repairOperator.repairChromosome(child, snapshot);
                child.setFitness(fitnessEvaluator.evaluate(child, snapshot));
                nextPopulation.add(child);
            }

            population = nextPopulation;
            generationsExecuted = generation;

            GenerationStat generationStat = computeGenerationStat(
                    generation,
                    population
            );
            generationHistory.add(generationStat);

            Chromosome generationBest = findBest(population);

            if (hasImproved(generationBest, bestOverall)) {
                bestOverall = copyChromosome(generationBest);
                stallCounter = 0;
            } else {
                stallCounter++;
            }

            if (stallCounter >= gaConfig.getStallGenerations()) {
                stopReason = StopReason.STAGNATION_REACHED;
                break;
            }
        }

        EvaluationBreakdown bestEvaluation = fitnessEvaluator.evaluateDetailed(
                bestOverall,
                snapshot
        );

        List<Chromosome> finalPopulation = prepareFinalPopulationForResult(
                population,
                bestOverall
        );

        return new MaGaResult(
                snapshot.getSnapshotId(),
                snapshot.getTimeSeconds(),
                bestOverall,
                bestEvaluation,
                generationsExecuted,
                stopReason,
                initialBestFitness,
                bestOverall.getFitness(),
                generationHistory,
                finalPopulation
        );
    }

    private List<Chromosome> prepareInitialPopulation(
            SystemSnapshot snapshot,
            List<Chromosome> initialPopulation
    ) {
        if (initialPopulation == null || initialPopulation.isEmpty()) {
            return populationInitializer.createInitialPopulation(
                    snapshot,
                    gaConfig.getPopulationSize()
            );
        }

        List<Chromosome> prepared = new ArrayList<>();

        for (Chromosome chromosome : initialPopulation) {
            if (chromosome == null || chromosome.getGenes() == null) {
                continue;
            }

            Chromosome copied = copyChromosome(chromosome);
            Chromosome repaired = repairOperator.repairChromosome(copied, snapshot);
            repaired.setFitness(fitnessEvaluator.evaluate(repaired, snapshot));
            prepared.add(repaired);
        }

        if (prepared.isEmpty()) {
            return populationInitializer.createInitialPopulation(
                    snapshot,
                    gaConfig.getPopulationSize()
            );
        }

        if (prepared.size() > gaConfig.getPopulationSize()) {
            prepared.sort(Comparator.comparingDouble(Chromosome::getFitness));
            List<Chromosome> reduced = new ArrayList<>();

            for (int i = 0; i < gaConfig.getPopulationSize(); i++) {
                reduced.add(copyChromosome(prepared.get(i)));
            }

            return reduced;
        }

        if (prepared.size() < gaConfig.getPopulationSize()) {
            int missing = gaConfig.getPopulationSize() - prepared.size();
            List<Chromosome> randomChromosomes = populationInitializer.createInitialPopulation(
                    snapshot,
                    missing
            );
            evaluatePopulation(randomChromosomes, snapshot);
            prepared.addAll(randomChromosomes);
        }

        return prepared;
    }

    /**
     * Prepara la popolazione finale da conservare in MaGaResult.
     */
    private List<Chromosome> prepareFinalPopulationForResult(
            List<Chromosome> population,
            Chromosome bestOverall
    ) {
        List<Chromosome> result = new ArrayList<>();

        for (Chromosome chromosome : population) {
            if (chromosome != null && chromosome.getGenes() != null) {
                result.add(copyChromosome(chromosome));
            }
        }

        result.add(copyChromosome(bestOverall));
        result.sort(Comparator.comparingDouble(Chromosome::getFitness));

        while (result.size() > gaConfig.getPopulationSize()) {
            result.remove(result.size() - 1);
        }

        return result;
    }

    private boolean shouldApplyCrossover() {
        return random.nextDouble() < gaConfig.getCrossoverRate();
    }

    private void evaluatePopulation(
            List<Chromosome> population,
            SystemSnapshot snapshot
    ) {
        for (Chromosome chromosome : population) {
            chromosome.setFitness(fitnessEvaluator.evaluate(chromosome, snapshot));
        }
    }

    private Chromosome findBest(List<Chromosome> population) {
        return population.stream()
                .min(Comparator.comparingDouble(Chromosome::getFitness))
                .orElseThrow(() -> new IllegalStateException("Population is empty."));
    }

    private boolean hasImproved(
            Chromosome candidate,
            Chromosome currentBest
    ) {
        return candidate.getFitness() + gaConfig.getFitnessImprovementEpsilon()
                < currentBest.getFitness();
    }

    private Chromosome copyChromosome(Chromosome source) {
        Chromosome copy = new Chromosome(new ArrayList<>(source.getGenes()));
        copy.setFitness(source.getFitness());
        return copy;
    }

    private GenerationStat computeGenerationStat(
            int generationIndex,
            List<Chromosome> population
    ) {
        double best = Double.POSITIVE_INFINITY;
        double worst = Double.NEGATIVE_INFINITY;
        double sum = 0.0;

        for (Chromosome chromosome : population) {
            double fitness = chromosome.getFitness();
            best = Math.min(best, fitness);
            worst = Math.max(worst, fitness);
            sum += fitness;
        }

        double average = population.isEmpty()
                ? 0.0
                : sum / population.size();

        return new GenerationStat(
                generationIndex,
                best,
                average,
                worst
        );
    }

    private void validateSnapshot(SystemSnapshot snapshot) {
        if (snapshot.getVehicles() == null) {
            throw new IllegalArgumentException("snapshot.vehicles must not be null.");
        }

        if (snapshot.getTasks() == null) {
            throw new IllegalArgumentException("snapshot.tasks must not be null.");
        }

        if (snapshot.getCandidateNodes() == null
                || snapshot.getCandidateNodes().isEmpty()) {
            throw new IllegalArgumentException(
                    "snapshot.candidateNodes must contain at least one node."
            );
        }

        if (gaConfig.getPopulationSize() < 1) {
            throw new IllegalArgumentException("populationSize must be >= 1.");
        }
    }
}
