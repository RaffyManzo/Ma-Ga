package ga.core;

import config.MaGaConfig;
import config.ga.GeneticAlgorithmConfig;
import config.mobility.MobilityConfig;
import ga.diagnostics.GaRuntimeDiagnostics;
import ga.fitness.FitnessEvaluator;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.operators.CrossoverOperator;
import ga.operators.ElitismOperator;
import ga.operators.MutationOperator;
import ga.operators.MutationResult;
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
 * <p>Il MA-GA resta snapshot-based: ogni esecuzione riceve uno
 * {@link SystemSnapshot}, prepara una popolazione coerente con quello snapshot,
 * evolve i cromosomi e restituisce sia la soluzione migliore sia il breakdown
 * diagnostico della popolazione finale.</p>
 *
 * <p>La scelta tra cold start, warm start e partial restart non appartiene a
 * questa classe. Il package {@code window} decide la strategia temporale e passa
 * qui l'eventuale popolazione iniziale.</p>
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
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.gaConfig = Objects.requireNonNull(
                config.getGeneticAlgorithmConfig(),
                "geneticAlgorithmConfig must not be null."
        );
        this.random = new Random(gaConfig.getRandomSeed());

        /*
         * Repair e fitness condividono la stessa MobilityConfig, così il
         * vincolo di copertura viene interpretato nello stesso modo durante
         * correzione e valutazione.
         */
        this.repairOperator = new RepairOperator(config.getMobilityConfig());
        this.fitnessEvaluator = new FitnessEvaluator(config);
        this.populationInitializer = new PopulationInitializer(random, repairOperator);
        this.selectionOperator = new SelectionOperator(
                random,
                DEFAULT_TOURNAMENT_SIZE
        );
        this.crossoverOperator = new CrossoverOperator(random);
        this.mutationOperator = new MutationOperator(random);
        this.elitismOperator = new ElitismOperator();
    }

    /** Esegue il MA-GA partendo da popolazione generata internamente. */
    public Chromosome optimize(SystemSnapshot snapshot) {
        return optimizeDetailed(snapshot).getBestChromosome();
    }

    /** Restituisce la configurazione di mobilità usata da repair e fitness. */
    public MobilityConfig getMobilityConfig() {
        return config.getMobilityConfig();
    }

    /** Esegue il MA-GA partendo da una popolazione iniziale esterna. */
    public Chromosome optimize(
            SystemSnapshot snapshot,
            List<Chromosome> initialPopulation
    ) {
        return optimizeDetailed(snapshot, initialPopulation).getBestChromosome();
    }

    /** Esegue il MA-GA e restituisce il risultato completo. */
    public MaGaResult optimizeDetailed(SystemSnapshot snapshot) {
        return optimizeDetailed(snapshot, null);
    }

    /**
     * Esegue il MA-GA e restituisce un risultato completo.
     *
     * <p>Durante le generazioni interne dello stesso snapshot usa il repair
     * incrementale. La mutazione espone i task realmente modificati; il repair
     * rivaluta solo quei geni ma mantiene sempre il controllo CPU aggregato
     * globale, necessario anche dopo il crossover.</p>
     */
    public MaGaResult optimizeDetailed(
            SystemSnapshot snapshot,
            List<Chromosome> initialPopulation
    ) {
        long optimizerStartNs = System.nanoTime();
        long inputValidationStartNs = optimizerStartNs;
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        this.gaConfig = config.resolveGeneticAlgorithmConfig(snapshot);
        validateSnapshot(snapshot);
        long inputValidationNs = System.nanoTime() - inputValidationStartNs;
        GaRuntimeDiagnostics.Context diagnosticContext =
                GaRuntimeDiagnostics.current();
        GaRuntimeDiagnostics.JobDescriptor descriptor =
                diagnosticContext == null
                        ? new GaRuntimeDiagnostics.JobDescriptor("", "", "", "")
                        : diagnosticContext.descriptor();
        int reusedChromosomeCount = initialPopulation == null
                ? 0
                : Math.min(initialPopulation.size(), gaConfig.getPopulationSize());
        String warmStartMode = reusedChromosomeCount == 0
                ? "FRESH_OR_EMPTY_INPUT"
                : "INPUT_POPULATION_REUSED";

        List<GenerationStat> generationHistory = new ArrayList<>();
        if (snapshot.getTasks().isEmpty()) {
            GaRuntimeDiagnostics.stage("EMPTY_TASK_SET");
            long emptyFitnessStartNs = System.nanoTime();
            Chromosome empty = new Chromosome(new ArrayList<>());
            empty.setFitness(0.0);
            EvaluationBreakdown evaluation = fitnessEvaluator.evaluateDetailed(
                    empty,
                    snapshot
            );
            long emptyFitnessNs = System.nanoTime() - emptyFitnessStartNs;
            List<Chromosome> finalPopulation = new ArrayList<>();
            finalPopulation.add(copyChromosome(empty));
            MaGaResult result = new MaGaResult(
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
            emitOptimizerSummary(
                    descriptor,
                    reusedChromosomeCount,
                    warmStartMode,
                    inputValidationNs,
                    0L,
                    0L,
                    0L,
                    0.0,
                    0.0,
                    emptyFitnessNs,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    System.nanoTime() - optimizerStartNs,
                    result.getGenerationsExecuted(),
                    result.getStopReason().name()
            );
            return result;
        }

        GaRuntimeDiagnostics.stage("INITIAL_POPULATION_PREPARE");
        long populationInitializationStartNs = System.nanoTime();
        List<Chromosome> population = prepareInitialPopulation(
                snapshot,
                initialPopulation
        );
        long populationInitializationNs =
                System.nanoTime() - populationInitializationStartNs;
        GaRuntimeDiagnostics.recordStage(
                "populationInitialization",
                -1,
                -1,
                populationInitializationNs,
                population.size()
        );
        GaRuntimeDiagnostics.stage("INITIAL_FITNESS");
        long initialFitnessStartNs = System.nanoTime();
        evaluatePopulation(population, snapshot);
        long initialFitnessNs = System.nanoTime() - initialFitnessStartNs;
        GaRuntimeDiagnostics.recordStage(
                "initialFitness",
                -1,
                -1,
                initialFitnessNs,
                population.size()
        );

        long initialSortingStartNs = System.nanoTime();
        GenerationStat initialStat = computeGenerationStat(0, population);
        generationHistory.add(initialStat);
        Chromosome bestOverall = copyChromosome(findBest(population));
        long initialSortingNs = System.nanoTime() - initialSortingStartNs;
        GaRuntimeDiagnostics.recordStage(
                "initialSorting",
                0,
                -1,
                initialSortingNs,
                population.size()
        );
        double initialBestFitness = bestOverall.getFitness();
        int stallCounter = 0;
        int generationsExecuted = 0;
        StopReason stopReason = StopReason.MAX_GENERATIONS_REACHED;

        for (int generation = 1;
                generation <= gaConfig.getMaxGenerations();
                generation++) {
            if (diagnosticContext != null) {
                diagnosticContext.generation(generation);
            }
            GaRuntimeDiagnostics.stage("GENERATION_START");
            long heapBeforeBytes = GaRuntimeDiagnostics.heapUsedBytes();
            long generationStartNs = System.nanoTime();
            long generationCpuStartNs = GaRuntimeDiagnostics.cpuTimeNs();
            long selectionNs = 0L;
            long crossoverNs = 0L;
            long mutationNs = 0L;
            long repairNs = 0L;
            long fitnessNs = 0L;
            long sortingAndElitismNs = 0L;
            long terminationNs = 0L;
            long chromosomesCreated = 0L;
            long chromosomesReused = 0L;
            long chromosomesRepaired = 0L;
            long repairCalls = 0L;
            long fitnessEvaluations = 0L;
            long candidateScans = 0L;
            long taskGeneVisits = 0L;
            long elitismStartNs = System.nanoTime();
            List<Chromosome> nextPopulation = new ArrayList<>();
            nextPopulation.addAll(
                    elitismOperator.selectElite(
                            population,
                            gaConfig.getElitismCount()
                    )
            );
            sortingAndElitismNs += System.nanoTime() - elitismStartNs;
            chromosomesReused += nextPopulation.size();

            while (nextPopulation.size() < gaConfig.getPopulationSize()) {
                int chromosomeIndex = nextPopulation.size();
                GaRuntimeDiagnostics.chromosomeIndex(chromosomeIndex);
                long selectionStartNs = System.nanoTime();
                Chromosome parentA = selectionOperator.select(population);
                Chromosome parentB = selectionOperator.select(population);
                selectionNs += System.nanoTime() - selectionStartNs;

                Chromosome child;
                long crossoverStartNs = System.nanoTime();
                if (shouldApplyCrossover()) {
                    child = crossoverOperator.crossover(parentA, parentB);
                } else {
                    child = crossoverOperator.copyChromosome(parentA);
                }
                crossoverNs += System.nanoTime() - crossoverStartNs;
                chromosomesCreated++;

                long mutationStartNs = System.nanoTime();
                MutationResult mutationResult = mutationOperator.mutateDetailed(
                        child,
                        snapshot,
                        gaConfig.getMutationRate()
                );
                mutationNs += System.nanoTime() - mutationStartNs;
                taskGeneVisits += child.getGenes().size();
                candidateScans += (long) child.getGenes().size()
                        * snapshot.getCandidateNodes().size();

                long repairStartNs = System.nanoTime();
                child = repairOperator.repairChromosomeIncremental(
                        mutationResult.getChromosome(),
                        snapshot,
                        mutationResult.getMutatedTaskIds()
                );
                repairNs += System.nanoTime() - repairStartNs;
                chromosomesRepaired++;
                repairCalls++;
                long fitnessStartNs = System.nanoTime();
                child.setFitness(fitnessEvaluator.evaluate(child, snapshot));
                fitnessNs += System.nanoTime() - fitnessStartNs;
                fitnessEvaluations++;
                nextPopulation.add(child);
            }

            population = nextPopulation;
            generationsExecuted = generation;
            long statStartNs = System.nanoTime();
            GenerationStat generationStat = computeGenerationStat(
                    generation,
                    population
            );
            generationHistory.add(generationStat);

            Chromosome generationBest = findBest(population);
            sortingAndElitismNs += System.nanoTime() - statStartNs;
            double previousBestFitness = bestOverall.getFitness();
            long terminationStartNs = System.nanoTime();
            if (hasImproved(generationBest, bestOverall)) {
                bestOverall = copyChromosome(generationBest);
                stallCounter = 0;
            } else {
                stallCounter++;
            }
            double fitnessImprovement =
                    previousBestFitness - generationBest.getFitness();
            GaRuntimeDiagnostics.bestFitness(bestOverall.getFitness());
            GaRuntimeDiagnostics.stallCounter(stallCounter);

            if (stallCounter >= gaConfig.getStallGenerations()) {
                stopReason = StopReason.STAGNATION_REACHED;
                terminationNs += System.nanoTime() - terminationStartNs;
                emitGeneration(
                        descriptor,
                        generation,
                        generationStartNs,
                        generationCpuStartNs,
                        selectionNs,
                        crossoverNs,
                        mutationNs,
                        repairNs,
                        fitnessNs,
                        sortingAndElitismNs,
                        terminationNs,
                        chromosomesCreated,
                        chromosomesReused,
                        chromosomesRepaired,
                        repairCalls,
                        fitnessEvaluations,
                        candidateScans,
                        taskGeneVisits,
                        population,
                        bestOverall.getFitness(),
                        previousBestFitness,
                        fitnessImprovement,
                        stallCounter,
                        heapBeforeBytes
                );
                break;
            }
            terminationNs += System.nanoTime() - terminationStartNs;
            emitGeneration(
                    descriptor,
                    generation,
                    generationStartNs,
                    generationCpuStartNs,
                    selectionNs,
                    crossoverNs,
                    mutationNs,
                    repairNs,
                    fitnessNs,
                    sortingAndElitismNs,
                    terminationNs,
                    chromosomesCreated,
                    chromosomesReused,
                    chromosomesRepaired,
                    repairCalls,
                    fitnessEvaluations,
                    candidateScans,
                    taskGeneVisits,
                    population,
                    bestOverall.getFitness(),
                    previousBestFitness,
                    fitnessImprovement,
                    stallCounter,
                    heapBeforeBytes
            );
        }

        /*
         * Verifica finale prudenziale: costa un solo repair completo e tutela
         * il risultato restituito senza reinserire il costo nel ciclo interno.
         */
        GaRuntimeDiagnostics.stage("FINAL_REPAIR");
        long finalRepairStartNs = System.nanoTime();
        bestOverall = repairOperator.repairChromosome(bestOverall, snapshot);
        long finalRepairNs = System.nanoTime() - finalRepairStartNs;
        GaRuntimeDiagnostics.recordStage(
                "finalRepair",
                generationsExecuted,
                -1,
                finalRepairNs,
                1L
        );
        GaRuntimeDiagnostics.stage("FINAL_FITNESS");
        long finalFitnessStartNs = System.nanoTime();
        bestOverall.setFitness(fitnessEvaluator.evaluate(bestOverall, snapshot));

        EvaluationBreakdown bestEvaluation = fitnessEvaluator.evaluateDetailed(
                bestOverall,
                snapshot
        );
        long finalFitnessNs = System.nanoTime() - finalFitnessStartNs;
        GaRuntimeDiagnostics.recordStage(
                "finalFitness",
                generationsExecuted,
                -1,
                finalFitnessNs,
                2L
        );
        GaRuntimeDiagnostics.stage("RESULT_CONSTRUCTION");
        long resultConstructionStartNs = System.nanoTime();
        List<Chromosome> finalPopulation = prepareFinalPopulationForResult(
                population,
                bestOverall
        );
        long finalSortingNs = System.nanoTime() - resultConstructionStartNs;
        MaGaResult result = new MaGaResult(
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
        long resultConstructionNs =
                System.nanoTime() - resultConstructionStartNs;
        emitOptimizerSummary(
                descriptor,
                reusedChromosomeCount,
                warmStartMode,
                inputValidationNs,
                0L,
                diagnosticContext == null
                        ? 0L
                        : Math.round(
                                diagnosticContext.populationAdaptationMillis()
                                        * 1_000_000.0
                        ),
                populationInitializationNs,
                diagnosticContext == null
                        ? 0.0
                        : diagnosticContext.initialChromosomeCreationMillis(),
                diagnosticContext == null
                        ? 0.0
                        : diagnosticContext.initialRepairMillis(),
                initialFitnessNs,
                initialSortingNs,
                finalRepairNs,
                finalFitnessNs,
                finalSortingNs,
                resultConstructionNs,
                0L,
                System.nanoTime() - optimizerStartNs,
                result.getGenerationsExecuted(),
                result.getStopReason().name()
        );
        return result;
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
            List<Chromosome> randomChromosomes =
                    populationInitializer.createInitialPopulation(snapshot, missing);
            evaluatePopulation(randomChromosomes, snapshot);
            prepared.addAll(randomChromosomes);
        }
        return prepared;
    }

    /** Prepara la popolazione finale riutilizzabile dalle finestre successive. */
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
        return population
                .stream()
                .min(Comparator.comparingDouble(Chromosome::getFitness))
                .orElseThrow(
                        () -> new IllegalStateException("Population is empty.")
                );
    }

    private boolean hasImproved(Chromosome candidate, Chromosome currentBest) {
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
        double average = population.isEmpty() ? 0.0 : sum / population.size();
        return new GenerationStat(generationIndex, best, average, worst);
    }

    @SuppressWarnings("ParameterNumber")
    private void emitGeneration(
            GaRuntimeDiagnostics.JobDescriptor descriptor,
            int generation,
            long generationStartNs,
            long generationCpuStartNs,
            long selectionNs,
            long crossoverNs,
            long mutationNs,
            long repairNs,
            long fitnessNs,
            long sortingAndElitismNs,
            long terminationNs,
            long chromosomesCreated,
            long chromosomesReused,
            long chromosomesRepaired,
            long repairCalls,
            long fitnessEvaluations,
            long candidateScans,
            long taskGeneVisits,
            List<Chromosome> population,
            double bestFitness,
            double previousBestFitness,
            double fitnessImprovement,
            int stallCounter,
            long heapBeforeBytes
    ) {
        long generationElapsedNs = System.nanoTime() - generationStartNs;
        long cpuEndNs = GaRuntimeDiagnostics.cpuTimeNs();
        double generationCpuMillis = generationCpuStartNs < 0L || cpuEndNs < 0L
                ? -1.0
                : GaRuntimeDiagnostics.millis(cpuEndNs - generationCpuStartNs);
        long feasible = countFeasible(population);
        GaRuntimeDiagnostics.recordGeneration(new GaRuntimeDiagnostics.GenerationRecord(
                descriptor,
                generation,
                GaRuntimeDiagnostics.millis(generationElapsedNs),
                generationCpuMillis,
                GaRuntimeDiagnostics.millis(selectionNs),
                GaRuntimeDiagnostics.millis(crossoverNs),
                GaRuntimeDiagnostics.millis(mutationNs),
                GaRuntimeDiagnostics.millis(repairNs),
                GaRuntimeDiagnostics.millis(fitnessNs),
                GaRuntimeDiagnostics.millis(sortingAndElitismNs),
                GaRuntimeDiagnostics.millis(terminationNs),
                chromosomesCreated,
                chromosomesReused,
                chromosomesRepaired,
                repairCalls,
                fitnessEvaluations,
                candidateScans,
                taskGeneVisits,
                feasible,
                Math.max(0L, population.size() - feasible),
                bestFitness,
                previousBestFitness,
                fitnessImprovement,
                stallCounter,
                heapBeforeBytes,
                GaRuntimeDiagnostics.heapUsedBytes()
        ));
    }

    @SuppressWarnings("ParameterNumber")
    private void emitOptimizerSummary(
            GaRuntimeDiagnostics.JobDescriptor descriptor,
            int reusedChromosomeCount,
            String warmStartMode,
            long inputValidationNs,
            long taskCandidateMappingNs,
            long populationAdaptationNs,
            long populationInitializationNs,
            double initialChromosomeCreationMillis,
            double initialRepairMillis,
            long initialFitnessNs,
            long initialSortingNs,
            long finalRepairNs,
            long finalFitnessNs,
            long finalSortingNs,
            long resultConstructionNs,
            long serializationOrReportingNs,
            long totalOptimizerNs,
            int generationsExecuted,
            String stopReason
    ) {
        GaRuntimeDiagnostics.emitOptimizerSummary(
                new GaRuntimeDiagnostics.OptimizerSummaryRecord(
                        descriptor,
                        config.getGeneticAlgorithmConfig().getPopulationSize(),
                        gaConfig.getPopulationSize(),
                        gaConfig.getMaxGenerations(),
                        gaConfig.getStallGenerations(),
                        gaConfig.getFitnessImprovementEpsilon(),
                        gaConfig.getCrossoverRate(),
                        gaConfig.getMutationRate(),
                        gaConfig.getElitismCount(),
                        config.getGaParameterScalingMode().name(),
                        warmStartMode,
                        reusedChromosomeCount,
                        GaRuntimeDiagnostics.millis(inputValidationNs),
                        GaRuntimeDiagnostics.millis(taskCandidateMappingNs),
                        GaRuntimeDiagnostics.millis(populationAdaptationNs),
                        GaRuntimeDiagnostics.millis(populationInitializationNs),
                        initialChromosomeCreationMillis,
                        initialRepairMillis,
                        GaRuntimeDiagnostics.millis(initialFitnessNs),
                        GaRuntimeDiagnostics.millis(initialSortingNs),
                        GaRuntimeDiagnostics.millis(finalRepairNs),
                        GaRuntimeDiagnostics.millis(finalFitnessNs),
                        GaRuntimeDiagnostics.millis(finalSortingNs),
                        GaRuntimeDiagnostics.millis(resultConstructionNs),
                        GaRuntimeDiagnostics.millis(serializationOrReportingNs),
                        GaRuntimeDiagnostics.millis(totalOptimizerNs),
                        generationsExecuted,
                        stopReason == null ? "UNKNOWN" : stopReason
                )
        );
    }

    private long countFeasible(List<Chromosome> population) {
        long feasible = 0L;
        for (Chromosome chromosome : population) {
            double fitness = chromosome.getFitness();
            if (Double.isFinite(fitness) && fitness < 1.0E9) {
                feasible++;
            }
        }
        return feasible;
    }

    private void validateSnapshot(SystemSnapshot snapshot) {
        if (snapshot.getVehicles() == null) {
            throw new IllegalArgumentException("snapshot.vehicles must not be null.");
        }
        if (snapshot.getTasks() == null) {
            throw new IllegalArgumentException("snapshot.tasks must not be null.");
        }
        if (snapshot.getCandidateNodes() == null) {
            throw new IllegalArgumentException(
                    "snapshot.candidateNodes must not be null."
            );
        }
        if (!snapshot.getTasks().isEmpty()
                && snapshot.getCandidateNodes().isEmpty()) {
            throw new IllegalArgumentException(
                    "snapshot.candidateNodes must contain at least one node."
            );
        }
        if (gaConfig.getPopulationSize() < 1) {
            throw new IllegalArgumentException("populationSize must be >= 1.");
        }
    }
}
