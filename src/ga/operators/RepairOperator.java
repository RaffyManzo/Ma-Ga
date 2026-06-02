package ga.operators;

import config.mobility.MobilityConfig;
import ga.constraints.DeadlineConstraintEvaluator;
import ga.constraints.DeadlineEvaluation;
import ga.constraints.DeadlineRepairCatalog;
import ga.constraints.DeadlineRepairCatalog.DeadlineRepairProfile;
import ga.constraints.SnapshotRepairContext;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.mobility.CoverageEstimator;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.offloading.OffloadingTimeModel;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ripara cromosomi e geni incoerenti.
 *
 * <p>Nel modello source-aware, un gene è valido solo se il candidato scelto
 * è compatibile con il veicolo sorgente del task.</p>
 *
 * <p>La riparazione avviene su cinque livelli:</p>
 *
 * <ol>
 *     <li>livello gene: corregge candidato, quota di offloading, CPU e banda;</li>
 *     <li>livello mobilità: evita candidati remoti con copertura insufficiente;</li>
 *     <li>livello deadline: prova una correzione limitata e aderente al modello;</li>
 *     <li>livello cromosoma: ridimensiona la CPU aggregata sui nodi fisici remoti;</li>
 *     <li>livello cromosoma: ridimensiona la banda aggregata sui link source-aware.</li>
 * </ol>
 *
 * <p>Gli indici e il catalogo lazy sono ottimizzazioni implementative. Non
 * introducono nuove variabili decisionali e non sostituiscono selezione,
 * crossover, mutazione o fitness del Genetic Algorithm.</p>
 *
 * <p>La banda viene verificata su due livelli gerarchici distinti:</p>
 *
 * <ul>
 *     <li>{@code candidateId}: capacità del singolo link source-aware;</li>
 *     <li>{@code poolId}: capacità radio condivisa della RSU o del link V2V.</li>
 * </ul>
 *
 * <p>Il pool non sostituisce il limite del link: lo contiene.</p>
 */
public final class RepairOperator {
    private static final double EPSILON = 1.0E-9;
    private static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05;
    private static final double MIN_RESOURCE_FRACTION = 0.05;

    /**
     * Numero massimo di passaggi repair gene + repair aggregati.
     *
     * <p>Due passaggi restano una scelta prudente: il ridimensionamento
     * aggregato di CPU o banda può rendere nuovamente insufficiente una
     * configurazione. Il secondo passaggio viene però limitato ai soli task
     * effettivamente ridimensionati.</p>
     */
    private static final int MAX_REPAIR_PASSES = 2;

    private final CpuAggregateRepairOperator cpuAggregateRepairOperator;
    private final BandwidthAggregateRepairOperator bandwidthAggregateRepairOperator;
    private final BandwidthPoolAggregateRepairOperator bandwidthPoolAggregateRepairOperator;
    private final CoverageEstimator coverageEstimator;
    private final OffloadingTimeModel offloadingTimeModel;
    private final OffloadingRatioPolicy offloadingRatioPolicy;
    private final DeadlineConstraintEvaluator deadlineConstraintEvaluator;

    private SnapshotRepairContext cachedContext;
    private DeadlineRepairCatalog cachedDeadlineRepairCatalog;

    /** Costruttore compatibile con il codice precedente. */
    public RepairOperator() {
        this(MobilityConfig.defaultConfig());
    }

    /** Costruisce il repair operator con configurazione di mobilità esplicita. */
    public RepairOperator(MobilityConfig mobilityConfig) {
        Objects.requireNonNull(
                mobilityConfig,
                "mobilityConfig must not be null."
        );

        this.cpuAggregateRepairOperator = new CpuAggregateRepairOperator();
        this.bandwidthAggregateRepairOperator =
                new BandwidthAggregateRepairOperator();
        this.bandwidthPoolAggregateRepairOperator =
                new BandwidthPoolAggregateRepairOperator();
        this.coverageEstimator = new CoverageEstimator(mobilityConfig);
        this.offloadingTimeModel = new OffloadingTimeModel();
        this.offloadingRatioPolicy = new OffloadingRatioPolicy();
        this.deadlineConstraintEvaluator = new DeadlineConstraintEvaluator(
                coverageEstimator,
                offloadingTimeModel
        );
    }

    /**
     * Ripara integralmente un cromosoma rispetto allo snapshot corrente.
     *
     * <p>Questo percorso resta obbligatorio per popolazioni appena create,
     * cromosomi provenienti da una finestra precedente e chiamanti esterni che
     * non dispongono dell'elenco dei geni modificati.</p>
     */
    public Chromosome repairChromosome(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        return repairChromosomeInternal(chromosome, snapshot, null);
    }

    /**
     * Ripara incrementalmente un figlio prodotto durante l'evoluzione nello
     * stesso snapshot.
     *
     * <p>I genitori della generazione corrente sono già stati riparati. Un gene
     * ereditato senza modifiche resta quindi individualmente valido. Il metodo
     * rivaluta soltanto i task indicati come dirty, ma esegue sempre i repair
     * aggregati CPU e banda sull'intero cromosoma: il crossover può infatti
     * combinare geni validi singolarmente e creare una nuova contesa
     * collettiva.</p>
     *
     * <p>Se la struttura del cromosoma non rispetta l'insieme dei task dello
     * snapshot, il metodo effettua automaticamente un repair completo.</p>
     *
     * @param chromosome figlio da riparare
     * @param snapshot snapshot corrente
     * @param dirtyTaskIds task realmente modificati dalla mutazione
     * @return cromosoma riparato
     */
    public Chromosome repairChromosomeIncremental(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            Set<String> dirtyTaskIds
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        SnapshotRepairContext context = contextFor(snapshot);
        if (!hasCompleteTaskCoverage(chromosome, context)) {
            return repairChromosomeInternal(chromosome, snapshot, null);
        }

        return repairChromosomeInternal(
                chromosome,
                snapshot,
                normalizeDirtyTaskIds(dirtyTaskIds, context)
        );
    }

    /**
     * Implementazione condivisa dai percorsi completo e incrementale.
     *
     * <p>{@code initialTargetedTaskIds == null} indica il repair completo del
     * primo passaggio. Un insieme vuoto indica invece che nessun gene necessita
     * di repair individuale prima dei controlli aggregati globali.</p>
     */
    private Chromosome repairChromosomeInternal(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            Set<String> initialTargetedTaskIds
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        SnapshotRepairContext context = contextFor(snapshot);
        DeadlineRepairCatalog catalog = catalogFor(context);
        Chromosome current = chromosome;
        Set<String> targetedTaskIds = initialTargetedTaskIds;

        for (int pass = 0; pass < MAX_REPAIR_PASSES; pass++) {
            current = repairGenes(
                    current,
                    snapshot,
                    context,
                    catalog,
                    targetedTaskIds
            );

            CpuAggregateRepairResult cpuAggregateResult =
                    cpuAggregateRepairOperator.repairChromosomeDetailed(
                            current,
                            snapshot,
                            context
                    );
            current = cpuAggregateResult.getChromosome();

            BandwidthAggregateRepairResult bandwidthAggregateResult =
                    bandwidthAggregateRepairOperator.repairChromosomeDetailed(
                            current,
                            snapshot,
                            context
                    );
            current = bandwidthAggregateResult.getChromosome();

            BandwidthPoolAggregateRepairResult bandwidthPoolAggregateResult =
                    bandwidthPoolAggregateRepairOperator
                            .repairChromosomeDetailed(
                                    current,
                                    snapshot,
                                    context
                            );
            current = bandwidthPoolAggregateResult.getChromosome();

            if (!cpuAggregateResult.isChanged()
                    && !bandwidthAggregateResult.isChanged()
                    && !bandwidthPoolAggregateResult.isChanged()) {
                return current;
            }

            targetedTaskIds = new LinkedHashSet<>();
            targetedTaskIds.addAll(cpuAggregateResult.getAffectedTaskIds());
            targetedTaskIds.addAll(
                    bandwidthAggregateResult.getAffectedTaskIds()
            );
            targetedTaskIds.addAll(
                    bandwidthPoolAggregateResult.getAffectedTaskIds()
            );

            if (targetedTaskIds.isEmpty()) {
                return current;
            }
        }

        return current;
    }

    /**
     * Ripara tutti i geni nel primo passaggio e soltanto i geni indicati nei
     * passaggi successivi. I geni non coinvolti dai ridimensionamenti aggregati
     * vengono conservati senza una rivalutazione ridondante.
     */
    private Chromosome repairGenes(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog,
            Set<String> targetedTaskIds
    ) {
        Map<String, Gene> geneByTaskId = indexGenes(chromosome);
        List<Gene> repairedGenes = new ArrayList<>();

        for (TaskInstance task : context.getTasks()) {
            Gene gene = geneByTaskId.get(task.getTaskId());
            boolean mustRepair = targetedTaskIds == null
                    || targetedTaskIds.contains(task.getTaskId())
                    || gene == null;

            if (gene == null) {
                gene = createFallbackGene(task, context);
            }

            repairedGenes.add(
                    mustRepair
                            ? repairGene(gene, task, snapshot, context, catalog)
                            : gene
            );
        }

        Chromosome repaired = new Chromosome(repairedGenes);
        if (chromosome != null) {
            repaired.setFitness(chromosome.getFitness());
        }
        return repaired;
    }

    /** Adapter compatibile con i chiamanti precedenti. */
    public Gene repairGene(
            Gene gene,
            TaskInstance task,
            SystemSnapshot snapshot
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        SnapshotRepairContext context = contextFor(snapshot);
        return repairGene(gene, task, snapshot, context, catalogFor(context));
    }

    /** Ripara un gene usando indici e catalogo dello snapshot corrente. */
    private Gene repairGene(
            Gene gene,
            TaskInstance task,
            SystemSnapshot snapshot,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog
    ) {
        Objects.requireNonNull(gene, "gene must not be null.");
        Objects.requireNonNull(task, "task must not be null.");

        NodeCandidate localCandidate = context.requireLocalCandidateForTask(task);
        NodeCandidate candidate = context.getCandidateById(
                gene.getSelectedCandidateId()
        );

        if (candidate == null
                || !candidate.isValidForSourceVehicle(
                        task.getSourceVehicleId()
                )) {
            candidate = localCandidate;
        }

        VehicleSnapshot sourceVehicle = context.getVehicleById(
                task.getSourceVehicleId()
        );

        double offloadingRatio = clamp(gene.getOffloadingRatio(), 0.0, 1.0);
        double allocatedCpu = Math.max(0.0, gene.getAllocatedCpu());
        double allocatedBandwidth = Math.max(
                0.0,
                gene.getAllocatedBandwidth()
        );

        Gene mobilityCoherentGene;
        if (candidate.getType() == NodeType.LOCAL) {
            mobilityCoherentGene = createLocalGene(
                    task,
                    candidate,
                    sourceVehicle
            );
        } else {
            if (offloadingRatio <= EPSILON) {
                offloadingRatio = MIN_REMOTE_OFFLOADING_RATIO;
            }

            allocatedCpu = clampResource(
                    allocatedCpu,
                    candidate.getAvailableCpu()
            );
            allocatedBandwidth = clampResource(
                    allocatedBandwidth,
                    candidate.getAvailableBandwidth()
            );

            if (!isCoverageSufficient(
                    task,
                    candidate,
                    sourceVehicle,
                    offloadingRatio,
                    allocatedCpu,
                    allocatedBandwidth,
                    context
            )) {
                NodeCandidate replacement =
                        findCoverageSustainableRemoteCandidate(
                                task,
                                sourceVehicle,
                                offloadingRatio,
                                allocatedCpu,
                                allocatedBandwidth,
                                candidate.getCandidateId(),
                                context
                        );

                if (replacement == null) {
                    mobilityCoherentGene = createLocalGene(
                            task,
                            localCandidate,
                            sourceVehicle
                    );
                } else {
                    candidate = replacement;
                    allocatedCpu = clampResource(
                            allocatedCpu,
                            candidate.getAvailableCpu()
                    );
                    allocatedBandwidth = clampResource(
                            allocatedBandwidth,
                            candidate.getAvailableBandwidth()
                    );
                    mobilityCoherentGene = new Gene(
                            task.getTaskId(),
                            candidate.getCandidateId(),
                            offloadingRatio,
                            allocatedCpu,
                            allocatedBandwidth
                    );
                }
            } else {
                mobilityCoherentGene = new Gene(
                        task.getTaskId(),
                        candidate.getCandidateId(),
                        offloadingRatio,
                        allocatedCpu,
                        allocatedBandwidth
                );
            }
        }

        return repairDeadlineIfNeeded(
                mobilityCoherentGene,
                task,
                context,
                catalog,
                sourceVehicle,
                localCandidate
        );
    }

    /**
     * Applica la policy deadline-aware soltanto quando il gene corrente non
     * rispetta la deadline.
     *
     * <ol>
     *     <li>prova quote alternative mantenendo il nodo remoto corrente;</li>
     *     <li>prova candidati remoti alternativi;</li>
     *     <li>prova l'esecuzione locale;</li>
     *     <li>se nessuna alternativa è ammissibile, sceglie il best-effort.</li>
     * </ol>
     */
    private Gene repairDeadlineIfNeeded(
            Gene currentGene,
            TaskInstance task,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog,
            VehicleSnapshot sourceVehicle,
            NodeCandidate localCandidate
    ) {
        DeadlineEvaluation currentEvaluation =
                deadlineConstraintEvaluator.evaluate(
                        currentGene,
                        task,
                        context
                );

        if (currentEvaluation.isDeadlineRespected()) {
            return currentGene;
        }

        NodeCandidate currentCandidate = context.getCandidateById(
                currentGene.getSelectedCandidateId()
        );

        if (currentCandidate != null
                && currentCandidate.getType() != NodeType.LOCAL) {
            Gene repairedOnCurrentCandidate =
                    findDeadlineFeasibleGeneForCandidate(
                            task,
                            sourceVehicle,
                            currentCandidate,
                            currentGene.getOffloadingRatio(),
                            currentGene.getAllocatedCpu(),
                            currentGene.getAllocatedBandwidth(),
                            context,
                            catalog
                    );

            if (repairedOnCurrentCandidate != null) {
                return repairedOnCurrentCandidate;
            }
        }

        Gene repairedOnAlternativeRemote =
                findDeadlineFeasibleRemoteAlternative(
                        task,
                        sourceVehicle,
                        currentCandidate == null
                                ? null
                                : currentCandidate.getCandidateId(),
                        currentGene,
                        context,
                        catalog
                );

        if (repairedOnAlternativeRemote != null) {
            return repairedOnAlternativeRemote;
        }

        Gene localGene = createLocalGene(task, localCandidate, sourceVehicle);
        if (deadlineConstraintEvaluator
                .evaluate(localGene, task, context)
                .isAdmissible()) {
            return localGene;
        }

        return selectDegradedBestEffortGene(
                task,
                sourceVehicle,
                localGene,
                currentGene,
                context,
                catalog
        );
    }

    private Gene findDeadlineFeasibleRemoteAlternative(
            TaskInstance task,
            VehicleSnapshot sourceVehicle,
            String excludedCandidateId,
            Gene currentGene,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog
    ) {
        Gene bestGene = null;
        DeadlineEvaluation bestEvaluation = null;

        for (NodeCandidate candidate : context.getCandidatesForTask(task)) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }
            if (candidate.getCandidateId().equals(excludedCandidateId)) {
                continue;
            }

            Gene candidateGene = findDeadlineFeasibleGeneForCandidate(
                    task,
                    sourceVehicle,
                    candidate,
                    currentGene.getOffloadingRatio(),
                    currentGene.getAllocatedCpu(),
                    currentGene.getAllocatedBandwidth(),
                    context,
                    catalog
            );

            if (candidateGene == null) {
                continue;
            }

            DeadlineEvaluation evaluation =
                    deadlineConstraintEvaluator.evaluate(
                            candidateGene,
                            task,
                            context
                    );

            if (bestGene == null
                    || evaluation.getCompletionTimeSeconds()
                    < bestEvaluation.getCompletionTimeSeconds()) {
                bestGene = candidateGene;
                bestEvaluation = evaluation;
            }
        }

        return bestGene;
    }

    private Gene findDeadlineFeasibleGeneForCandidate(
            TaskInstance task,
            VehicleSnapshot sourceVehicle,
            NodeCandidate candidate,
            double preferredRatio,
            double preferredCpu,
            double preferredBandwidth,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog
    ) {
        Gene bestGene = null;
        double bestPressure = Double.POSITIVE_INFINITY;
        double bestCompletion = Double.POSITIVE_INFINITY;

        for (double ratio : catalog.buildRatioCandidates(
                task,
                candidate,
                sourceVehicle,
                preferredRatio
        )) {
            Gene preservedResourcesGene = new Gene(
                    task.getTaskId(),
                    candidate.getCandidateId(),
                    ratio,
                    clampResource(preferredCpu, candidate.getAvailableCpu()),
                    clampResource(
                            preferredBandwidth,
                            candidate.getAvailableBandwidth()
                    )
            );

            Gene feasibleGene;
            DeadlineEvaluation evaluation =
                    deadlineConstraintEvaluator.evaluate(
                            preservedResourcesGene,
                            task,
                            context
                    );

            if (evaluation.isAdmissible()) {
                feasibleGene = preservedResourcesGene;
            } else {
                feasibleGene = catalog
                        .getProfile(task, candidate, sourceVehicle, ratio)
                        .getMinimalFeasibleGene();

                if (feasibleGene != null) {
                    evaluation = deadlineConstraintEvaluator.evaluate(
                            feasibleGene,
                            task,
                            context
                    );
                }
            }

            if (feasibleGene == null || !evaluation.isAdmissible()) {
                continue;
            }

            double pressure = computeResourcePressure(feasibleGene, candidate);
            if (pressure < bestPressure - EPSILON
                    || (Math.abs(pressure - bestPressure) <= EPSILON
                    && evaluation.getCompletionTimeSeconds()
                    < bestCompletion)) {
                bestGene = feasibleGene;
                bestPressure = pressure;
                bestCompletion = evaluation.getCompletionTimeSeconds();
            }
        }

        return bestGene;
    }

    /*
     * Quando nessuna configurazione valutata dal repair riesce a rispettare
     * la deadline, il task entra in modalità DEGRADED_BEST_EFFORT. La scelta
     * confronta il fallback LOCAL e i candidati remoti sostenibili rispetto
     * alla mobilità, quindi conserva l'alternativa con lo sforamento previsto
     * più basso. Non viene usata una priorità esplicita e il CLOUD non viene
     * privilegiato automaticamente: può essere selezionato soltanto se riduce
     * realmente la lateness stimata.
     *
     * Questa sezione non certifica l'insoddisfacibilità matematica globale del
     * task: il repair esplora intenzionalmente un insieme limitato di quote e
     * risorse per non trasformarsi in un secondo ottimizzatore.
     */
    private Gene selectDegradedBestEffortGene(
            TaskInstance task,
            VehicleSnapshot sourceVehicle,
            Gene localGene,
            Gene currentGene,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog
    ) {
        Gene bestGene = localGene;
        DeadlineEvaluation bestEvaluation =
                deadlineConstraintEvaluator.evaluate(
                        localGene,
                        task,
                        context
                );

        for (NodeCandidate candidate : context.getCandidatesForTask(task)) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }

            for (double ratio : catalog.buildRatioCandidates(
                    task,
                    candidate,
                    sourceVehicle,
                    currentGene.getOffloadingRatio()
            )) {
                DeadlineRepairProfile profile = catalog.getProfile(
                        task,
                        candidate,
                        sourceVehicle,
                        ratio
                );

                Gene remoteGene = profile.getMaxCapacityGene();
                DeadlineEvaluation evaluation =
                        profile.getMaxCapacityEvaluation();

                if (!evaluation.isValid()
                        || !evaluation.isMobilitySustainable()) {
                    continue;
                }

                if (isBetterBestEffortChoice(
                        remoteGene,
                        evaluation,
                        bestGene,
                        bestEvaluation,
                        context
                )) {
                    bestGene = remoteGene;
                    bestEvaluation = evaluation;
                }
            }
        }

        return bestGene;
    }

    private boolean isBetterBestEffortChoice(
            Gene candidateGene,
            DeadlineEvaluation candidateEvaluation,
            Gene currentBestGene,
            DeadlineEvaluation currentBestEvaluation,
            SnapshotRepairContext context
    ) {
        if (candidateEvaluation.getLatenessSeconds()
                < currentBestEvaluation.getLatenessSeconds() - EPSILON) {
            return true;
        }

        if (Math.abs(
                candidateEvaluation.getLatenessSeconds()
                        - currentBestEvaluation.getLatenessSeconds()
        ) > EPSILON) {
            return false;
        }

        if (candidateEvaluation.getCompletionTimeSeconds()
                < currentBestEvaluation.getCompletionTimeSeconds() - EPSILON) {
            return true;
        }

        if (Math.abs(
                candidateEvaluation.getCompletionTimeSeconds()
                        - currentBestEvaluation.getCompletionTimeSeconds()
        ) > EPSILON) {
            return false;
        }

        NodeCandidate candidate = context.getCandidateById(
                candidateGene.getSelectedCandidateId()
        );
        NodeCandidate currentBest = context.getCandidateById(
                currentBestGene.getSelectedCandidateId()
        );

        return candidate != null
                && currentBest != null
                && candidate.getType() == NodeType.LOCAL
                && currentBest.getType() != NodeType.LOCAL;
    }

    private double computeResourcePressure(
            Gene gene,
            NodeCandidate candidate
    ) {
        return safeDivide(
                gene.getAllocatedCpu(),
                candidate.getAvailableCpu()
        ) + safeDivide(
                gene.getAllocatedBandwidth(),
                candidate.getAvailableBandwidth()
        );
    }

    /** Crea un gene locale di fallback quando il cromosoma non contiene il task. */
    private Gene createFallbackGene(
            TaskInstance task,
            SnapshotRepairContext context
    ) {
        NodeCandidate localCandidate = context.requireLocalCandidateForTask(task);
        VehicleSnapshot sourceVehicle = context.getVehicleById(
                task.getSourceVehicleId()
        );
        return createLocalGene(task, localCandidate, sourceVehicle);
    }

    /** Cerca un candidato remoto alternativo che soddisfi la copertura. */
    private NodeCandidate findCoverageSustainableRemoteCandidate(
            TaskInstance task,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio,
            double allocatedCpu,
            double allocatedBandwidth,
            String excludedCandidateId,
            SnapshotRepairContext context
    ) {
        NodeCandidate bestCandidate = null;
        double bestCompletionTime = Double.POSITIVE_INFINITY;

        for (NodeCandidate candidate : context.getCandidatesForTask(task)) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }
            if (candidate.getCandidateId().equals(excludedCandidateId)) {
                continue;
            }

            double candidateCpu = clampResource(
                    allocatedCpu,
                    candidate.getAvailableCpu()
            );
            double candidateBandwidth = clampResource(
                    allocatedBandwidth,
                    candidate.getAvailableBandwidth()
            );

            double completionTime = estimateCompletionTimeSeconds(
                    task,
                    candidate,
                    sourceVehicle,
                    offloadingRatio,
                    candidateCpu,
                    candidateBandwidth
            );
            double coverageTime = estimateCoverageTimeSeconds(
                    task,
                    candidate,
                    context
            );

            if (isStrictlyPositive(coverageTime)
                    && completionTime <= coverageTime
                    && completionTime < bestCompletionTime) {
                bestCandidate = candidate;
                bestCompletionTime = completionTime;
            }
        }

        return bestCandidate;
    }

    private boolean isCoverageSufficient(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio,
            double allocatedCpu,
            double allocatedBandwidth,
            SnapshotRepairContext context
    ) {
        if (candidate.getType() == NodeType.LOCAL) {
            return true;
        }

        double completionTime = estimateCompletionTimeSeconds(
                task,
                candidate,
                sourceVehicle,
                offloadingRatio,
                allocatedCpu,
                allocatedBandwidth
        );
        double coverageTime = estimateCoverageTimeSeconds(
                task,
                candidate,
                context
        );

        return isStrictlyPositive(coverageTime)
                && completionTime <= coverageTime;
    }

    private double estimateCoverageTimeSeconds(
            TaskInstance task,
            NodeCandidate candidate,
            SnapshotRepairContext context
    ) {
        return context.estimateCoverageTimeSeconds(
                task,
                candidate,
                coverageEstimator
        );
    }

    /** Stima il completion time usando la stessa struttura della fitness. */
    private double estimateCompletionTimeSeconds(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio,
            double allocatedCpu,
            double allocatedBandwidth
    ) {
        if (sourceVehicle == null) {
            return Double.POSITIVE_INFINITY;
        }

        double localCpu = sourceVehicle.getLocalCpu();
        if (!isStrictlyPositive(localCpu)) {
            return Double.POSITIVE_INFINITY;
        }

        if (candidate.getType() == NodeType.LOCAL) {
            return offloadingTimeModel
                    .evaluateLocal(task, localCpu)
                    .getCompletionTimeSeconds();
        }

        if (!isStrictlyPositive(allocatedCpu)
                || !isStrictlyPositive(allocatedBandwidth)) {
            return Double.POSITIVE_INFINITY;
        }

        double p = clamp(
                offloadingRatio,
                MIN_REMOTE_OFFLOADING_RATIO,
                1.0
        );

        return offloadingTimeModel
                .evaluateRemote(
                        task,
                        candidate,
                        localCpu,
                        p,
                        allocatedCpu,
                        allocatedBandwidth
                )
                .getCompletionTimeSeconds();
    }

    private Gene createLocalGene(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
        if (candidate == null
                || candidate.getType() != NodeType.LOCAL
                || !candidate.isValidForSourceVehicle(
                        task.getSourceVehicleId()
                )) {
            throw new IllegalArgumentException(
                    "Local fallback requires a LOCAL candidate valid for "
                            + "source vehicle "
                            + task.getSourceVehicleId()
            );
        }

        double localCpu = sourceVehicle == null
                ? candidate.getAvailableCpu()
                : Math.max(0.0, sourceVehicle.getLocalCpu());

        return new Gene(
                task.getTaskId(),
                candidate.getCandidateId(),
                0.0,
                localCpu,
                0.0
        );
    }

    private Map<String, Gene> indexGenes(Chromosome chromosome) {
        Map<String, Gene> result = new HashMap<>();
        if (chromosome == null || chromosome.getGenes() == null) {
            return result;
        }

        for (Gene gene : chromosome.getGenes()) {
            if (gene != null && gene.getTaskId() != null) {
                result.put(gene.getTaskId(), gene);
            }
        }

        return result;
    }

    /** Verifica che il figlio contenga esattamente un gene per task attivo. */
    private boolean hasCompleteTaskCoverage(
            Chromosome chromosome,
            SnapshotRepairContext context
    ) {
        if (chromosome == null || chromosome.getGenes() == null) {
            return false;
        }
        if (chromosome.getGenes().size() != context.getTasks().size()) {
            return false;
        }

        Set<String> knownTaskIds = new HashSet<>();
        for (TaskInstance task : context.getTasks()) {
            knownTaskIds.add(task.getTaskId());
        }

        Set<String> observedTaskIds = new HashSet<>();
        for (Gene gene : chromosome.getGenes()) {
            if (gene == null
                    || gene.getTaskId() == null
                    || !knownTaskIds.contains(gene.getTaskId())
                    || !observedTaskIds.add(gene.getTaskId())) {
                return false;
            }
        }

        return observedTaskIds.size() == knownTaskIds.size();
    }

    /** Mantiene solo identificativi dirty appartenenti allo snapshot corrente. */
    private Set<String> normalizeDirtyTaskIds(
            Set<String> dirtyTaskIds,
            SnapshotRepairContext context
    ) {
        Set<String> knownTaskIds = new HashSet<>();
        for (TaskInstance task : context.getTasks()) {
            knownTaskIds.add(task.getTaskId());
        }

        Set<String> result = new LinkedHashSet<>();
        if (dirtyTaskIds == null) {
            return result;
        }

        for (String taskId : dirtyTaskIds) {
            if (taskId != null && knownTaskIds.contains(taskId)) {
                result.add(taskId);
            }
        }

        return result;
    }

    /** Limita una risorsa al range ammesso dal singolo candidato. */
    private double clampResource(double value, double maxAvailable) {
        if (!Double.isFinite(maxAvailable) || maxAvailable <= 0.0) {
            return 0.0;
        }

        double min = maxAvailable * MIN_RESOURCE_FRACTION;
        if (!Double.isFinite(value) || value <= 0.0) {
            return min;
        }

        return clamp(value, min, maxAvailable);
    }

    private SnapshotRepairContext contextFor(SystemSnapshot snapshot) {
        if (cachedContext == null || !cachedContext.isFor(snapshot)) {
            cachedContext = new SnapshotRepairContext(snapshot);
            cachedDeadlineRepairCatalog = null;
        }
        return cachedContext;
    }

    private DeadlineRepairCatalog catalogFor(SnapshotRepairContext context) {
        if (cachedDeadlineRepairCatalog == null
                || cachedDeadlineRepairCatalog.getContext() != context) {
            cachedDeadlineRepairCatalog = new DeadlineRepairCatalog(
                    context,
                    coverageEstimator,
                    offloadingTimeModel,
                    offloadingRatioPolicy,
                    deadlineConstraintEvaluator
            );
        }
        return cachedDeadlineRepairCatalog;
    }

    private double safeDivide(double numerator, double denominator) {
        if (!Double.isFinite(numerator)
                || !Double.isFinite(denominator)
                || denominator <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        return numerator / denominator;
    }

    private boolean isStrictlyPositive(double value) {
        return Double.isFinite(value) && value > EPSILON;
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
