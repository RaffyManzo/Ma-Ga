package ga;

import config.GeneticAlgorithmConfig;
import config.MaGaConfig;
import model.Chromosome;
import model.SystemSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Orchestratore principale del MA-GA sul singolo snapshot statico.
 *
 * Questa classe coordina gli operatori genetici e produce sia:
 *
 * - il miglior cromosoma;
 * - un MaGaResult completo per stampa, debug e analisi sperimentale.
 */
public final class MaGaOptimizer {

    private static final int DEFAULT_TOURNAMENT_SIZE = 3;

    private final MaGaConfig config;
    private final GeneticAlgorithmConfig gaConfig;

    private final FitnessEvaluator fitnessEvaluator;
    private final PopulationInitializer populationInitializer;
    private final RepairOperator repairOperator;
    private final SelectionOperator selectionOperator;
    private final CrossoverOperator crossoverOperator;
    private final MutationOperator mutationOperator;
    private final ElitismOperator elitismOperator;

    private final Random random;

    /**
     * Costruisce un optimizer con operatori standard.
     *
     * Parametri in ingresso:
     * - config: configurazione generale del MA-GA.
     *
     * Output:
     * - nuovo MaGaOptimizer pronto per l'esecuzione.
     */
    public MaGaOptimizer(MaGaConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.gaConfig = Objects.requireNonNull(
                config.getGeneticAlgorithmConfig(),
                "geneticAlgorithmConfig must not be null."
        );

        this.random = new Random(gaConfig.getRandomSeed());

        this.repairOperator = new RepairOperator();
        this.fitnessEvaluator = new FitnessEvaluator(config);
        this.populationInitializer = new PopulationInitializer(random, repairOperator);
        this.selectionOperator = new SelectionOperator(random, DEFAULT_TOURNAMENT_SIZE);
        this.crossoverOperator = new CrossoverOperator(random);
        this.mutationOperator = new MutationOperator(random);
        this.elitismOperator = new ElitismOperator();
    }

    /**
     * Esegue il MA-GA e restituisce solo il miglior cromosoma.
     *
     * Parametri in ingresso:
     * - snapshot: snapshot statico del sistema.
     *
     * Output:
     * - miglior Chromosome trovato.
     *
     * Nota:
     * questo metodo resta per compatibilità. Per stampa e analisi usare optimizeDetailed().
     */
    public Chromosome optimize(SystemSnapshot snapshot) {
        return optimizeDetailed(snapshot).getBestChromosome();
    }

    /**
     * Esegue il MA-GA e restituisce un risultato completo.
     *
     * Parametri in ingresso:
     * - snapshot: snapshot statico del sistema.
     *
     * Output:
     * - MaGaResult contenente miglior cromosoma, breakdown fitness,
     *   generazioni eseguite, motivo di arresto e storico fitness.
     */
    public MaGaResult optimizeDetailed(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        validateSnapshot(snapshot);

        List<GenerationStat> generationHistory = new ArrayList<>();

        if (snapshot.getTasks().isEmpty()) {
            Chromosome empty = new Chromosome(new ArrayList<>());
            empty.setFitness(0.0);

            FitnessEvaluator.EvaluationBreakdown evaluation =
                    fitnessEvaluator.evaluateDetailed(empty, snapshot);

            return new MaGaResult(
                    snapshot.getSnapshotId(),
                    snapshot.getTimeSeconds(),
                    empty,
                    evaluation,
                    0,
                    StopReason.EMPTY_TASK_SET,
                    0.0,
                    0.0,
                    generationHistory
            );
        }

        List<Chromosome> population = populationInitializer.createInitialPopulation(
                snapshot,
                gaConfig.getPopulationSize()
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
                    elitismOperator.selectElite(population, gaConfig.getElitismCount())
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

            GenerationStat generationStat = computeGenerationStat(generation, population);
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

        FitnessEvaluator.EvaluationBreakdown bestEvaluation =
                fitnessEvaluator.evaluateDetailed(bestOverall, snapshot);

        return new MaGaResult(
                snapshot.getSnapshotId(),
                snapshot.getTimeSeconds(),
                bestOverall,
                bestEvaluation,
                generationsExecuted,
                stopReason,
                initialBestFitness,
                bestOverall.getFitness(),
                generationHistory
        );
    }

    /**
     * Decide se applicare crossover.
     *
     * Output:
     * - true se applicare crossover;
     * - false se copiare un genitore.
     */
    private boolean shouldApplyCrossover() {
        return random.nextDouble() < gaConfig.getCrossoverRate();
    }

    /**
     * Valuta tutti i cromosomi della popolazione.
     */
    private void evaluatePopulation(List<Chromosome> population, SystemSnapshot snapshot) {
        for (Chromosome chromosome : population) {
            chromosome.setFitness(fitnessEvaluator.evaluate(chromosome, snapshot));
        }
    }

    /**
     * Trova il miglior cromosoma della popolazione.
     */
    private Chromosome findBest(List<Chromosome> population) {
        return population.stream()
                .min(Comparator.comparingDouble(Chromosome::getFitness))
                .orElseThrow(() -> new IllegalStateException("Population is empty."));
    }

    /**
     * Verifica se candidate migliora currentBest.
     */
    private boolean hasImproved(Chromosome candidate, Chromosome currentBest) {
        return candidate.getFitness() + gaConfig.getFitnessImprovementEpsilon()
                < currentBest.getFitness();
    }

    /**
     * Crea una copia superficiale del cromosoma.
     */
    private Chromosome copyChromosome(Chromosome source) {
        Chromosome copy = new Chromosome(new ArrayList<>(source.getGenes()));
        copy.setFitness(source.getFitness());

        return copy;
    }

    /**
     * Calcola statistiche sintetiche della popolazione.
     */
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

        double average = population.isEmpty() ? 0.0 : sum / population.size();

        return new GenerationStat(
                generationIndex,
                best,
                average,
                worst
        );
    }

    /**
     * Valida lo snapshot prima dell'esecuzione.
     */
    private void validateSnapshot(SystemSnapshot snapshot) {
        if (snapshot.getVehicles() == null) {
            throw new IllegalArgumentException("snapshot.vehicles must not be null.");
        }

        if (snapshot.getTasks() == null) {
            throw new IllegalArgumentException("snapshot.tasks must not be null.");
        }

        if (snapshot.getCandidateNodes() == null || snapshot.getCandidateNodes().isEmpty()) {
            throw new IllegalArgumentException(
                    "snapshot.candidateNodes must contain at least one node."
            );
        }
    }
}