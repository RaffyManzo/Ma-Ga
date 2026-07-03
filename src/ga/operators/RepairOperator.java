package ga.operators;

import config.mobility.MobilityConfig;
import ga.constraints.DeadlineConstraintEvaluator;
import ga.constraints.DeadlineEvaluation;
import ga.constraints.DeadlineRepairCatalog;
import ga.constraints.DeadlineRepairCatalog.DeadlineRepairProfile;
import ga.constraints.SnapshotRepairContext;
import ga.fitness.local.LocalCpuContentionEvaluator;
import ga.fitness.local.LocalCpuContentionEvaluator.Evaluation;
import ga.fitness.local.LocalCpuContentionEvaluator.TaskResult;
import ga.fitness.local.LocalCpuContentionEvaluator.VehicleResult;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
     * <p>Tre passaggi permettono di comporre il repair della contesa locale
     * con i ridimensionamenti aggregati di CPU e banda remota. I passaggi
     * successivi restano limitati ai task effettivamente modificati.</p>
     */
    private static final int MAX_REPAIR_PASSES = 3;
    /**
     * Verifica differenziale usata esclusivamente dagli harness V3-A.
     * Quando attiva, ogni scelta incrementale viene confrontata con il
     * percorso completo originale.
     */
    private static final boolean VERIFY_LOCAL_CONTENTION_DELTA =
        Boolean.getBoolean("maga.repair.verifyLocalContentionDelta");

    private static final String LOCAL_CONTENTION_MODE_PROPERTY =
            "maga.repair.localContentionMode";
    private static final String LOCAL_CONTENTION_MODE_LEGACY = "legacy";
    private static final String FORCE_ADAPTIVE_FALLBACK_PROPERTY =
            "maga.repair.forceAdaptiveFallback";

    private static final double LOCAL_CONTENTION_INVALID_METRIC = 1.0E18;

    private final CpuAggregateRepairOperator cpuAggregateRepairOperator;
    private final BandwidthAggregateRepairOperator bandwidthAggregateRepairOperator;
    private final BandwidthPoolAggregateRepairOperator bandwidthPoolAggregateRepairOperator;
    private final CoverageEstimator coverageEstimator;
    private final OffloadingTimeModel offloadingTimeModel;
    private final OffloadingRatioPolicy offloadingRatioPolicy;
    private final DeadlineConstraintEvaluator deadlineConstraintEvaluator;
    private final LocalCpuContentionEvaluator localCpuContentionEvaluator;

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
        this.localCpuContentionEvaluator =
                new LocalCpuContentionEvaluator();
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

            LocalContentionRepairResult localContentionResult =
                    repairLocalCpuContention(
                            current,
                            snapshot,
                            context,
                            catalog
                    );
            current = localContentionResult.getChromosome();

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

            if (!localContentionResult.isChanged()
                    && !cpuAggregateResult.isChanged()
                    && !bandwidthAggregateResult.isChanged()
                    && !bandwidthPoolAggregateResult.isChanged()) {
                return current;
            }

            targetedTaskIds = new LinkedHashSet<>();
            targetedTaskIds.addAll(
                    localContentionResult.getAffectedTaskIds()
            );
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
     * Ripara esclusivamente le violazioni aggregate della CPU locale.
     *
     * <p>La fitness resta responsabile di valorizzare anche la contesa
     * fattibile. Il repair interviene soltanto quando EDF rileva un overflow o
     * una deadline locale violata. In assenza di candidati remoti ammissibili
     * il cromosoma viene conservato e la violazione resta visibile alla
     * fitness.</p>
     */
    private LocalContentionRepairResult repairLocalCpuContention(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog
    ) {
        if (useLegacyLocalContentionRepair()) {
            return repairLocalCpuContentionLegacy(
                    chromosome,
                    snapshot,
                    context,
                    catalog
            );
        }

        if (Boolean.getBoolean(FORCE_ADAPTIVE_FALLBACK_PROPERTY)) {
            return repairLocalCpuContentionLegacy(
                    chromosome,
                    snapshot,
                    context,
                    catalog
            );
        }

        try {
            return repairLocalCpuContentionAdaptive(
                    chromosome,
                    snapshot,
                    context,
                    catalog
            );
        } catch (RuntimeException adaptiveFailure) {
            return repairLocalCpuContentionLegacy(
                    chromosome,
                    snapshot,
                    context,
                    catalog
            );
        }
    }

    /**
     * Percorso storico conservato come fallback.
     */
    private LocalContentionRepairResult repairLocalCpuContentionLegacy(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog
    ) {
        if (chromosome == null || chromosome.getGenes() == null) {
            return LocalContentionRepairResult.unchanged(chromosome);
        }

        Chromosome current = chromosome;
        Set<String> affectedTaskIds = new LinkedHashSet<>();
        int maxReplacements = Math.max(1, context.getTasks().size());

        for (int replacementIndex = 0;
                replacementIndex < maxReplacements;
                replacementIndex++) {
            Evaluation evaluation =
                    localCpuContentionEvaluator.evaluate(snapshot, current);

            if (!evaluation.hasCpuOverflow()
                    && !evaluation.hasDeadlineViolations()) {
                break;
            }

            LocalContentionReplacement replacement =
                    findBestLocalContentionReplacement(
                            current,
                            evaluation,
                            context,
                            catalog
                    );

            if (replacement == null) {
                break;
            }

            current = replaceGene(
                    current,
                    replacement.getReplacementGene()
            );
            affectedTaskIds.add(replacement.getTaskId());
        }

        if (affectedTaskIds.isEmpty()) {
            return LocalContentionRepairResult.unchanged(chromosome);
        }

        return LocalContentionRepairResult.changed(
                current,
                affectedTaskIds
        );
    }

    /**
     * Repair adattivo della contesa locale.
     *
     * <p>Il metodo valuta integralmente il cromosoma all'inizio e alla fine.
     * Tra i due punti lavora su rappresentazioni compatte delle singole code
     * EDF. Le mosse remote ammissibili vengono costruite una sola volta,
     * ripulite tramite pruning sicuro e rivalutate soltanto rispetto allo
     * stato locale aggiornato.</p>
     *
     * <p>Il comportamento resta greedy: a ogni passo viene applicata la mossa
     * migliore secondo lo stesso comparatore del percorso storico. Le modifiche
     * vengono materializzate nel cromosoma una sola volta al termine del
     * blocco. Se il percorso rapido non migliora in modo verificabile o non
     * completa il repair, il percorso storico viene richiamato come fallback.</p>
     */
    private LocalContentionRepairResult repairLocalCpuContentionAdaptive(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog
    ) {
        if (chromosome == null || chromosome.getGenes() == null) {
            return LocalContentionRepairResult.unchanged(chromosome);
        }

        Evaluation baseline =
                localCpuContentionEvaluator.evaluate(snapshot, chromosome);

        if (!baseline.hasCpuOverflow()
                && !baseline.hasDeadlineViolations()) {
            return LocalContentionRepairResult.unchanged(chromosome);
        }

        Map<String, Gene> workingGenes = new LinkedHashMap<>(
                indexGenes(chromosome)
        );
        Map<String, AdaptiveVehicleState> vehicleStates =
                buildAdaptiveVehicleStates(baseline);

        List<String> violatingVehicleIds = new ArrayList<>();
        for (AdaptiveVehicleState state : vehicleStates.values()) {
            if (state.hasViolations()) {
                violatingVehicleIds.add(state.getVehicleId());
            }
        }
        Collections.sort(violatingVehicleIds);

        Set<String> affectedTaskIds = new LinkedHashSet<>();
        int maxReplacements = Math.max(1, context.getTasks().size());
        int replacementsApplied = 0;

        for (String vehicleId : violatingVehicleIds) {
            AdaptiveVehicleState state = vehicleStates.get(vehicleId);
            if (state == null || !state.hasViolations()) {
                continue;
            }

            List<AdaptiveContentionMove> moves =
                    buildAdaptiveContentionMoves(
                            state,
                            workingGenes,
                            context,
                            catalog
                    );

            while (state.hasViolations()
                    && replacementsApplied < maxReplacements) {
                LocalContentionMetrics currentMetrics =
                        aggregateAdaptiveMetrics(vehicleStates);
                LocalContentionReplacement best = null;
                AdaptiveContentionMove bestMove = null;

                for (AdaptiveContentionMove move : moves) {
                    double currentLocalCycles =
                            state.getLocalCycles(move.getTaskId());
                    double reduction =
                            currentLocalCycles
                                    - move.getReplacementLocalCycles();

                    if (reduction <= EPSILON) {
                        continue;
                    }

                    AdaptiveVehicleMetrics candidateVehicleMetrics =
                            state.evaluateReplacement(
                                    move.getTaskId(),
                                    move.getReplacementLocalCycles()
                            );
                    LocalContentionMetrics after =
                            aggregateAdaptiveMetrics(
                                    vehicleStates,
                                    vehicleId,
                                    candidateVehicleMetrics
                            );

                    if (!isStrictContentionImprovement(
                            after.getViolatingVehicleCount(),
                            after.getDeadlineViolationCount(),
                            after.getMaxOverflowRatio(),
                            after.getTotalOverflowRatio(),
                            after.getMaxDemandRatio(),
                            currentMetrics.getViolatingVehicleCount(),
                            currentMetrics.getDeadlineViolationCount(),
                            currentMetrics.getMaxOverflowRatio(),
                            currentMetrics.getTotalOverflowRatio(),
                            currentMetrics.getMaxDemandRatio()
                    )) {
                        continue;
                    }

                    LocalContentionReplacement option =
                            new LocalContentionReplacement(
                                    move.getTaskId(),
                                    move.getCandidateId(),
                                    move.getReplacementGene(),
                                    reduction,
                                    move.getReplacementGene()
                                            .getOffloadingRatio(),
                                    move.getCompletionTimeSeconds(),
                                    after.getViolatingVehicleCount(),
                                    after.getDeadlineViolationCount(),
                                    after.getMaxOverflowRatio(),
                                    after.getTotalOverflowRatio(),
                                    after.getMaxDemandRatio()
                            );

                    if (isBetterContentionReplacement(option, best)) {
                        best = option;
                        bestMove = move;
                    }
                }

                if (best == null || bestMove == null) {
                    break;
                }

                state.applyReplacement(
                        bestMove.getTaskId(),
                        bestMove.getReplacementLocalCycles()
                );
                workingGenes.put(
                        bestMove.getTaskId(),
                        bestMove.getReplacementGene()
                );
                affectedTaskIds.add(bestMove.getTaskId());
                replacementsApplied++;
            }
        }

        if (affectedTaskIds.isEmpty()) {
            return repairLocalCpuContentionLegacy(
                    chromosome,
                    snapshot,
                    context,
                    catalog
            );
        }

        Chromosome adaptiveChromosome =
                replaceGenes(chromosome, workingGenes);
        Evaluation validated =
                localCpuContentionEvaluator.evaluate(
                        snapshot,
                        adaptiveChromosome
                );

        LocalContentionMetrics baselineMetrics =
                metricsFromEvaluation(baseline);
        LocalContentionMetrics validatedMetrics =
                metricsFromEvaluation(validated);

        if (!isStrictContentionImprovement(
                validatedMetrics.getViolatingVehicleCount(),
                validatedMetrics.getDeadlineViolationCount(),
                validatedMetrics.getMaxOverflowRatio(),
                validatedMetrics.getTotalOverflowRatio(),
                validatedMetrics.getMaxDemandRatio(),
                baselineMetrics.getViolatingVehicleCount(),
                baselineMetrics.getDeadlineViolationCount(),
                baselineMetrics.getMaxOverflowRatio(),
                baselineMetrics.getTotalOverflowRatio(),
                baselineMetrics.getMaxDemandRatio()
        )) {
            return repairLocalCpuContentionLegacy(
                    chromosome,
                    snapshot,
                    context,
                    catalog
            );
        }

        if (validated.hasCpuOverflow()
                || validated.hasDeadlineViolations()) {
            LocalContentionRepairResult legacyContinuation =
                    repairLocalCpuContentionLegacy(
                            adaptiveChromosome,
                            snapshot,
                            context,
                            catalog
                    );

            Set<String> combinedAffectedTaskIds = new LinkedHashSet<>(
                    affectedTaskIds
            );
            combinedAffectedTaskIds.addAll(
                    legacyContinuation.getAffectedTaskIds()
            );

            if (combinedAffectedTaskIds.isEmpty()) {
                return LocalContentionRepairResult.unchanged(chromosome);
            }

            return LocalContentionRepairResult.changed(
                    legacyContinuation.getChromosome(),
                    combinedAffectedTaskIds
            );
        }

        return LocalContentionRepairResult.changed(
                adaptiveChromosome,
                affectedTaskIds
        );
    }

    private boolean useLegacyLocalContentionRepair() {
        String mode = System.getProperty(
                LOCAL_CONTENTION_MODE_PROPERTY,
                "adaptive"
        );
        return LOCAL_CONTENTION_MODE_LEGACY.equalsIgnoreCase(
                mode == null ? "" : mode.trim()
        );
    }

    private Map<String, AdaptiveVehicleState> buildAdaptiveVehicleStates(
            Evaluation evaluation
    ) {
        Map<String, AdaptiveVehicleState> result = new LinkedHashMap<>();
        List<String> vehicleIds = new ArrayList<>(
                evaluation.getVehicleResults().keySet()
        );
        Collections.sort(vehicleIds);

        for (String vehicleId : vehicleIds) {
            VehicleResult vehicle =
                    evaluation.getVehicleResult(vehicleId);
            if (vehicle != null) {
                result.put(
                        vehicleId,
                        new AdaptiveVehicleState(vehicle)
                );
            }
        }
        return result;
    }

    private List<AdaptiveContentionMove> buildAdaptiveContentionMoves(
            AdaptiveVehicleState state,
            Map<String, Gene> geneByTaskId,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog
    ) {
        List<AdaptiveContentionMove> result = new ArrayList<>();
        Set<String> deduplicationKeys = new HashSet<>();

        for (AdaptiveTaskState localTask : state.getTasks()) {
            TaskInstance task = context.getTaskById(
                    localTask.getTaskId()
            );
            Gene currentGene = geneByTaskId.get(
                    localTask.getTaskId()
            );
            VehicleSnapshot sourceVehicle = context.getVehicleById(
                    state.getVehicleId()
            );

            if (task == null
                    || currentGene == null
                    || sourceVehicle == null) {
                continue;
            }

            for (NodeCandidate candidate
                    : context.getCandidatesForTask(task)) {
                if (candidate == null
                        || candidate.getType() == NodeType.LOCAL) {
                    continue;
                }

                double preferredRatio =
                        candidate.getCandidateId().equals(
                                currentGene.getSelectedCandidateId()
                        )
                                ? currentGene.getOffloadingRatio()
                                : MIN_REMOTE_OFFLOADING_RATIO;

                List<Double> ratios = new ArrayList<>(
                        catalog.buildRatioCandidates(
                                task,
                                candidate,
                                sourceVehicle,
                                preferredRatio
                        )
                );
                ratios.sort(Double::compareTo);

                for (double ratio : ratios) {
                    Gene replacement;
                    try {
                        DeadlineRepairProfile profile =
                                catalog.getProfile(
                                        task,
                                        candidate,
                                        sourceVehicle,
                                        ratio
                                );
                        replacement = profile.getMinimalFeasibleGene();
                    } catch (IllegalArgumentException ignored) {
                        replacement = null;
                    }

                    if (replacement == null) {
                        continue;
                    }

                    double replacementLocalCycles =
                            computeLocalCpuCycles(
                                    task,
                                    replacement,
                                    candidate
                            );
                    if (localTask.getLocalCycles()
                            - replacementLocalCycles <= EPSILON) {
                        continue;
                    }

                    DeadlineEvaluation deadlineEvaluation =
                            deadlineConstraintEvaluator.evaluate(
                                    replacement,
                                    task,
                                    context
                            );
                    if (!deadlineEvaluation.isAdmissible()) {
                        continue;
                    }

                    String key = buildAdaptiveMoveKey(replacement);
                    if (!deduplicationKeys.add(key)) {
                        continue;
                    }

                    result.add(
                            new AdaptiveContentionMove(
                                    task.getTaskId(),
                                    candidate.getCandidateId(),
                                    replacement,
                                    replacementLocalCycles,
                                    deadlineEvaluation
                                        .getCompletionTimeSeconds()
                            )
                    );
                }
            }
        }

        result.sort(
                Comparator
                        .comparing(AdaptiveContentionMove::getTaskId)
                        .thenComparing(
                                AdaptiveContentionMove::getCandidateId
                        )
                        .thenComparingDouble(
                                move -> move.getReplacementGene()
                                    .getOffloadingRatio()
                        )
                        .thenComparingDouble(
                                move -> move.getReplacementGene()
                                    .getAllocatedCpu()
                        )
                        .thenComparingDouble(
                                move -> move.getReplacementGene()
                                    .getAllocatedBandwidth()
                        )
        );
        return result;
    }

    private String buildAdaptiveMoveKey(Gene replacement) {
        return replacement.getTaskId()
                + '\u0000'
                + replacement.getSelectedCandidateId()
                + '\u0000'
                + Double.doubleToLongBits(
                        replacement.getOffloadingRatio()
                )
                + '\u0000'
                + Double.doubleToLongBits(
                        replacement.getAllocatedCpu()
                )
                + '\u0000'
                + Double.doubleToLongBits(
                        replacement.getAllocatedBandwidth()
                );
    }

    private LocalContentionMetrics aggregateAdaptiveMetrics(
            Map<String, AdaptiveVehicleState> vehicleStates
    ) {
        return aggregateAdaptiveMetrics(
                vehicleStates,
                null,
                null
        );
    }

    private LocalContentionMetrics aggregateAdaptiveMetrics(
            Map<String, AdaptiveVehicleState> vehicleStates,
            String replacementVehicleId,
            AdaptiveVehicleMetrics replacementMetrics
    ) {
        int violatingVehicleCount = 0;
        int deadlineViolationCount = 0;
        double maxOverflowRatio = 0.0;
        double totalOverflowRatio = 0.0;
        double maxDemandRatio = 0.0;

        for (AdaptiveVehicleState state : vehicleStates.values()) {
            AdaptiveVehicleMetrics metrics =
                    replacementVehicleId != null
                            && replacementVehicleId.equals(
                                    state.getVehicleId()
                            )
                            && replacementMetrics != null
                                    ? replacementMetrics
                                    : state.getMetrics();

            if (metrics.hasViolations()) {
                violatingVehicleCount++;
            }
            deadlineViolationCount +=
                    metrics.getDeadlineViolationCount();
            maxOverflowRatio = Math.max(
                    maxOverflowRatio,
                    metrics.getOverflowRatio()
            );
            totalOverflowRatio += metrics.getOverflowRatio();
            maxDemandRatio = Math.max(
                    maxDemandRatio,
                    metrics.getMaxDemandRatio()
            );
        }

        return new LocalContentionMetrics(
                violatingVehicleCount,
                deadlineViolationCount,
                maxOverflowRatio,
                totalOverflowRatio,
                maxDemandRatio
        );
    }

    private LocalContentionMetrics metricsFromEvaluation(
            Evaluation evaluation
    ) {
        return new LocalContentionMetrics(
                countViolatingVehicles(evaluation),
                countDeadlineViolations(evaluation),
                maximumOverflowRatio(evaluation),
                totalOverflowRatio(evaluation),
                evaluation.getMaximumDemandRatio()
        );
    }

    private Chromosome replaceGenes(
            Chromosome chromosome,
            Map<String, Gene> replacementByTaskId
    ) {
        List<Gene> replacedGenes = new ArrayList<>();
        for (Gene gene : chromosome.getGenes()) {
            Gene replacement = replacementByTaskId.get(
                    gene.getTaskId()
            );
            replacedGenes.add(
                    replacement == null ? gene : replacement
            );
        }

        Chromosome result = new Chromosome(replacedGenes);
        result.setFitness(chromosome.getFitness());
        return result;
    }

        /**
     * Cerca la migliore sostituzione usando una rivalutazione delta della
     * sola coda EDF del veicolo sorgente interessato.
     *
     * Lo spazio delle opzioni, i controlli deadline, i comparatori e i
     * tie-break restano identici alla baseline.
     */
    private LocalContentionReplacement findBestLocalContentionReplacement(
        Chromosome chromosome,
        Evaluation evaluation,
        SnapshotRepairContext context,
        DeadlineRepairCatalog catalog
    ) {
        Map<String, Gene> geneByTaskId = indexGenes(chromosome);
        LocalContentionReplacement best = null;

        int currentViolatingVehicles = countViolatingVehicles(evaluation);
        int currentDeadlineViolations = countDeadlineViolations(evaluation);
        double currentMaxOverflow = maximumOverflowRatio(evaluation);
        double currentTotalOverflow = totalOverflowRatio(evaluation);
        double currentMaxDemand = evaluation.getMaximumDemandRatio();

        for (VehicleResult vehicle
            : evaluation.getVehicleResults().values()) {
            if (!vehicle.hasCpuOverflow()
                && !vehicle.hasDeadlineViolations()) {
                continue;
            }

            LocalContentionDeltaEvaluator deltaEvaluator =
                new LocalContentionDeltaEvaluator(evaluation, vehicle);

            for (TaskResult localTask : vehicle.getTaskResults()) {
                TaskInstance task = context.getTaskById(
                    localTask.getTaskId()
                );
                Gene currentGene = geneByTaskId.get(
                    localTask.getTaskId()
                );
                VehicleSnapshot sourceVehicle = context.getVehicleById(
                    localTask.getVehicleId()
                );

                if (task == null
                    || currentGene == null
                    || sourceVehicle == null) {
                    continue;
                }

                double currentLocalCycles =
                    localTask.getLocalCpuCycles();

                for (NodeCandidate candidate
                    : context.getCandidatesForTask(task)) {
                    if (candidate == null
                        || candidate.getType() == NodeType.LOCAL) {
                        continue;
                    }

                    double preferredRatio =
                        candidate.getCandidateId().equals(
                            currentGene.getSelectedCandidateId()
                        )
                            ? currentGene.getOffloadingRatio()
                            : MIN_REMOTE_OFFLOADING_RATIO;

                    List<Double> ratios = new ArrayList<>(
                        catalog.buildRatioCandidates(
                            task,
                            candidate,
                            sourceVehicle,
                            preferredRatio
                        )
                    );
                    ratios.sort(Double::compareTo);

                    for (double ratio : ratios) {
                        Gene replacement;
                        try {
                            DeadlineRepairProfile profile =
                                catalog.getProfile(
                                    task,
                                    candidate,
                                    sourceVehicle,
                                    ratio
                                );
                            replacement =
                                profile.getMinimalFeasibleGene();
                        } catch (IllegalArgumentException ignored) {
                            replacement = null;
                        }

                        if (replacement == null) {
                            continue;
                        }

                        double replacementLocalCycles =
                            computeLocalCpuCycles(
                                task,
                                replacement,
                                candidate
                            );
                        double reduction =
                            currentLocalCycles
                                - replacementLocalCycles;

                        if (reduction <= EPSILON) {
                            continue;
                        }

                        DeadlineEvaluation deadlineEvaluation =
                            deadlineConstraintEvaluator.evaluate(
                                replacement,
                                task,
                                context
                            );
                        if (!deadlineEvaluation.isAdmissible()) {
                            continue;
                        }

                        LocalContentionMetrics after =
                            deltaEvaluator.evaluateReplacement(
                                task.getTaskId(),
                                replacementLocalCycles
                            );

                        if (!isStrictContentionImprovement(
                            after.getViolatingVehicleCount(),
                            after.getDeadlineViolationCount(),
                            after.getMaxOverflowRatio(),
                            after.getTotalOverflowRatio(),
                            after.getMaxDemandRatio(),
                            currentViolatingVehicles,
                            currentDeadlineViolations,
                            currentMaxOverflow,
                            currentTotalOverflow,
                            currentMaxDemand
                        )) {
                            continue;
                        }

                        LocalContentionReplacement option =
                            new LocalContentionReplacement(
                                task.getTaskId(),
                                candidate.getCandidateId(),
                                replacement,
                                reduction,
                                replacement.getOffloadingRatio(),
                                deadlineEvaluation
                                    .getCompletionTimeSeconds(),
                                after.getViolatingVehicleCount(),
                                after.getDeadlineViolationCount(),
                                after.getMaxOverflowRatio(),
                                after.getTotalOverflowRatio(),
                                after.getMaxDemandRatio()
                            );

                        if (isBetterContentionReplacement(option, best)) {
                            best = option;
                        }
                    }
                }
            }
        }

        if (VERIFY_LOCAL_CONTENTION_DELTA) {
            LocalContentionReplacement reference =
                findBestLocalContentionReplacementReference(
                    chromosome,
                    evaluation,
                    context,
                    catalog
                );
            assertEquivalentLocalContentionReplacement(best, reference);
        }

        return best;
    }
private LocalContentionReplacement
    findBestLocalContentionReplacementReference(
            Chromosome chromosome,
            Evaluation evaluation,
            SnapshotRepairContext context,
            DeadlineRepairCatalog catalog
    ) {
        Map<String, Gene> geneByTaskId = indexGenes(chromosome);
        LocalContentionReplacement best = null;

        int currentViolatingVehicles = countViolatingVehicles(evaluation);
        int currentDeadlineViolations = countDeadlineViolations(evaluation);
        double currentMaxOverflow = maximumOverflowRatio(evaluation);
        double currentTotalOverflow = totalOverflowRatio(evaluation);
        double currentMaxDemand = evaluation.getMaximumDemandRatio();

        for (VehicleResult vehicle : evaluation
                .getVehicleResults()
                .values()) {
            if (!vehicle.hasCpuOverflow()
                    && !vehicle.hasDeadlineViolations()) {
                continue;
            }

            for (TaskResult localTask : vehicle.getTaskResults()) {
                TaskInstance task = context.getTaskById(
                        localTask.getTaskId()
                );
                Gene currentGene = geneByTaskId.get(
                        localTask.getTaskId()
                );
                VehicleSnapshot sourceVehicle = context.getVehicleById(
                        localTask.getVehicleId()
                );

                if (task == null
                        || currentGene == null
                        || sourceVehicle == null) {
                    continue;
                }

                double currentLocalCycles =
                        localTask.getLocalCpuCycles();

                for (NodeCandidate candidate
                        : context.getCandidatesForTask(task)) {
                    if (candidate == null
                            || candidate.getType() == NodeType.LOCAL) {
                        continue;
                    }

                    double preferredRatio = candidate.getCandidateId().equals(
                            currentGene.getSelectedCandidateId()
                    )
                            ? currentGene.getOffloadingRatio()
                            : MIN_REMOTE_OFFLOADING_RATIO;

                    List<Double> ratios = new ArrayList<>(
                            catalog.buildRatioCandidates(
                                    task,
                                    candidate,
                                    sourceVehicle,
                                    preferredRatio
                            )
                    );
                    ratios.sort(Double::compareTo);

                    for (double ratio : ratios) {
                        Gene replacement;
                        try {
                            DeadlineRepairProfile profile =
                                    catalog.getProfile(
                                            task,
                                            candidate,
                                            sourceVehicle,
                                            ratio
                                    );
                            replacement = profile.getMinimalFeasibleGene();
                        } catch (IllegalArgumentException ignored) {
                            replacement = null;
                        }

                        if (replacement == null) {
                            continue;
                        }

                        double replacementLocalCycles =
                                computeLocalCpuCycles(
                                        task,
                                        replacement,
                                        candidate
                                );
                        double reduction =
                                currentLocalCycles
                                        - replacementLocalCycles;

                        if (reduction <= EPSILON) {
                            continue;
                        }

                        DeadlineEvaluation deadlineEvaluation =
                                deadlineConstraintEvaluator.evaluate(
                                        replacement,
                                        task,
                                        context
                                );
                        if (!deadlineEvaluation.isAdmissible()) {
                            continue;
                        }

                        Chromosome proposed = replaceGene(
                                chromosome,
                                replacement
                        );
                        Evaluation after =
                                localCpuContentionEvaluator.evaluate(
                                        context.getSnapshot(),
                                        proposed
                                );

                        int afterViolatingVehicles =
                                countViolatingVehicles(after);
                        int afterDeadlineViolations =
                                countDeadlineViolations(after);
                        double afterMaxOverflow =
                                maximumOverflowRatio(after);
                        double afterTotalOverflow =
                                totalOverflowRatio(after);
                        double afterMaxDemand =
                                after.getMaximumDemandRatio();

                        if (!isStrictContentionImprovement(
                                afterViolatingVehicles,
                                afterDeadlineViolations,
                                afterMaxOverflow,
                                afterTotalOverflow,
                                afterMaxDemand,
                                currentViolatingVehicles,
                                currentDeadlineViolations,
                                currentMaxOverflow,
                                currentTotalOverflow,
                                currentMaxDemand
                        )) {
                            continue;
                        }

                        LocalContentionReplacement option =
                                new LocalContentionReplacement(
                                        task.getTaskId(),
                                        candidate.getCandidateId(),
                                        replacement,
                                        reduction,
                                        replacement.getOffloadingRatio(),
                                        deadlineEvaluation
                                                .getCompletionTimeSeconds(),
                                        afterViolatingVehicles,
                                        afterDeadlineViolations,
                                        afterMaxOverflow,
                                        afterTotalOverflow,
                                        afterMaxDemand
                                );

                        if (isBetterContentionReplacement(option, best)) {
                            best = option;
                        }
                    }
                }
            }
        }

        return best;
    }

        /**
     * Confronta il risultato delta con la valutazione completa originale.
     * Viene eseguito soltanto quando la relativa system property ÃƒÆ’Ã‚Â¨ attiva.
     */
    private void assertEquivalentLocalContentionReplacement(
        LocalContentionReplacement optimized,
        LocalContentionReplacement reference
    ) {
        if (optimized == null || reference == null) {
            if (optimized != reference) {
                throw new IllegalStateException(
                    "V3-A local-contention delta mismatch: "
                        + "one replacement is null."
                );
            }
            return;
        }

        Gene optimizedGene = optimized.getReplacementGene();
        Gene referenceGene = reference.getReplacementGene();

        boolean sameDecision =
            optimized.getTaskId().equals(reference.getTaskId())
                && optimized.getCandidateId().equals(
                    reference.getCandidateId()
                )
                && optimizedGene.getTaskId().equals(
                    referenceGene.getTaskId()
                )
                && optimizedGene.getSelectedCandidateId().equals(
                    referenceGene.getSelectedCandidateId()
                )
                && sameContentionMetric(
                    optimizedGene.getOffloadingRatio(),
                    referenceGene.getOffloadingRatio()
                )
                && sameContentionMetric(
                    optimizedGene.getAllocatedCpu(),
                    referenceGene.getAllocatedCpu()
                )
                && sameContentionMetric(
                    optimizedGene.getAllocatedBandwidth(),
                    referenceGene.getAllocatedBandwidth()
                );

        boolean sameMetrics =
            optimized.getViolatingVehicleCount()
                == reference.getViolatingVehicleCount()
                && optimized.getDeadlineViolationCount()
                    == reference.getDeadlineViolationCount()
                && sameContentionMetric(
                    optimized.getMaxOverflowRatio(),
                    reference.getMaxOverflowRatio()
                )
                && sameContentionMetric(
                    optimized.getTotalOverflowRatio(),
                    reference.getTotalOverflowRatio()
                )
                && sameContentionMetric(
                    optimized.getMaxDemandRatio(),
                    reference.getMaxDemandRatio()
                );

        if (!sameDecision || !sameMetrics) {
            throw new IllegalStateException(
                "V3-A local-contention delta mismatch. "
                    + "optimized="
                    + describeLocalContentionReplacement(optimized)
                    + ", reference="
                    + describeLocalContentionReplacement(reference)
            );
        }
    }

    private boolean sameContentionMetric(double left, double right) {
        if (Double.doubleToLongBits(left)
            == Double.doubleToLongBits(right)) {
            return true;
        }
        if (!Double.isFinite(left) || !Double.isFinite(right)) {
            return false;
        }
        double scale = Math.max(
            1.0,
            Math.max(Math.abs(left), Math.abs(right))
        );
        return Math.abs(left - right) <= EPSILON * scale;
    }

    private String describeLocalContentionReplacement(
        LocalContentionReplacement replacement
    ) {
        if (replacement == null) {
            return "null";
        }
        Gene gene = replacement.getReplacementGene();
        return "{task="
            + replacement.getTaskId()
            + ", candidate="
            + replacement.getCandidateId()
            + ", ratio="
            + gene.getOffloadingRatio()
            + ", cpu="
            + gene.getAllocatedCpu()
            + ", bandwidth="
            + gene.getAllocatedBandwidth()
            + ", violatingVehicles="
            + replacement.getViolatingVehicleCount()
            + ", deadlineViolations="
            + replacement.getDeadlineViolationCount()
            + ", maxOverflow="
            + replacement.getMaxOverflowRatio()
            + ", totalOverflow="
            + replacement.getTotalOverflowRatio()
            + ", maxDemand="
            + replacement.getMaxDemandRatio()
            + "}";
    }
private boolean isStrictContentionImprovement(
            int candidateViolatingVehicles,
            int candidateDeadlineViolations,
            double candidateMaxOverflow,
            double candidateTotalOverflow,
            double candidateMaxDemand,
            int currentViolatingVehicles,
            int currentDeadlineViolations,
            double currentMaxOverflow,
            double currentTotalOverflow,
            double currentMaxDemand
    ) {
        if (candidateViolatingVehicles != currentViolatingVehicles) {
            return candidateViolatingVehicles < currentViolatingVehicles;
        }
        if (candidateDeadlineViolations != currentDeadlineViolations) {
            return candidateDeadlineViolations < currentDeadlineViolations;
        }
        if (Math.abs(candidateMaxOverflow - currentMaxOverflow) > EPSILON) {
            return candidateMaxOverflow < currentMaxOverflow;
        }
        if (Math.abs(candidateTotalOverflow - currentTotalOverflow) > EPSILON) {
            return candidateTotalOverflow < currentTotalOverflow;
        }
        return candidateMaxDemand < currentMaxDemand - EPSILON;
    }

    private boolean isBetterContentionReplacement(
            LocalContentionReplacement candidate,
            LocalContentionReplacement currentBest
    ) {
        if (currentBest == null) {
            return true;
        }

        if (candidate.getViolatingVehicleCount()
                != currentBest.getViolatingVehicleCount()) {
            return candidate.getViolatingVehicleCount()
                    < currentBest.getViolatingVehicleCount();
        }
        if (candidate.getDeadlineViolationCount()
                != currentBest.getDeadlineViolationCount()) {
            return candidate.getDeadlineViolationCount()
                    < currentBest.getDeadlineViolationCount();
        }
        if (Math.abs(
                candidate.getMaxOverflowRatio()
                        - currentBest.getMaxOverflowRatio()
        ) > EPSILON) {
            return candidate.getMaxOverflowRatio()
                    < currentBest.getMaxOverflowRatio();
        }
        if (Math.abs(
                candidate.getTotalOverflowRatio()
                        - currentBest.getTotalOverflowRatio()
        ) > EPSILON) {
            return candidate.getTotalOverflowRatio()
                    < currentBest.getTotalOverflowRatio();
        }
        boolean candidateFeasible =
                candidate.getViolatingVehicleCount() == 0
                        && candidate.getDeadlineViolationCount() == 0
                        && candidate.getMaxOverflowRatio() <= EPSILON;
        boolean currentBestFeasible =
                currentBest.getViolatingVehicleCount() == 0
                        && currentBest.getDeadlineViolationCount() == 0
                        && currentBest.getMaxOverflowRatio() <= EPSILON;

        if (candidateFeasible && currentBestFeasible) {
            if (Math.abs(
                    candidate.getOffloadingRatio()
                            - currentBest.getOffloadingRatio()
            ) > EPSILON) {
                return candidate.getOffloadingRatio()
                        < currentBest.getOffloadingRatio();
            }
        } else {
            if (Math.abs(
                    candidate.getMaxDemandRatio()
                            - currentBest.getMaxDemandRatio()
            ) > EPSILON) {
                return candidate.getMaxDemandRatio()
                        < currentBest.getMaxDemandRatio();
            }
            if (Math.abs(
                    candidate.getOffloadingRatio()
                            - currentBest.getOffloadingRatio()
            ) > EPSILON) {
                return candidate.getOffloadingRatio()
                        < currentBest.getOffloadingRatio();
            }
        }
        if (Math.abs(
                candidate.getLocalCycleReduction()
                        - currentBest.getLocalCycleReduction()
        ) > EPSILON) {
            return candidate.getLocalCycleReduction()
                    < currentBest.getLocalCycleReduction();
        }
        if (Math.abs(
                candidate.getCompletionTimeSeconds()
                        - currentBest.getCompletionTimeSeconds()
        ) > EPSILON) {
            return candidate.getCompletionTimeSeconds()
                    < currentBest.getCompletionTimeSeconds();
        }

        int taskComparison = candidate.getTaskId().compareTo(
                currentBest.getTaskId()
        );
        if (taskComparison != 0) {
            return taskComparison < 0;
        }

        return candidate.getCandidateId().compareTo(
                currentBest.getCandidateId()
        ) < 0;
    }

    private int countViolatingVehicles(Evaluation evaluation) {
        int count = 0;
        for (VehicleResult vehicle : evaluation
                .getVehicleResults()
                .values()) {
            if (vehicle.hasCpuOverflow()
                    || vehicle.hasDeadlineViolations()) {
                count++;
            }
        }
        return count;
    }

    private int countDeadlineViolations(Evaluation evaluation) {
        int count = 0;
        for (VehicleResult vehicle : evaluation
                .getVehicleResults()
                .values()) {
            count += vehicle.getDeadlineViolationCount();
        }
        return count;
    }

    private double maximumOverflowRatio(Evaluation evaluation) {
        double maximum = 0.0;
        for (VehicleResult vehicle : evaluation
                .getVehicleResults()
                .values()) {
            maximum = Math.max(maximum, vehicle.getCpuOverflowRatio());
        }
        return maximum;
    }

    private double totalOverflowRatio(Evaluation evaluation) {
        double total = 0.0;
        for (VehicleResult vehicle : evaluation
                .getVehicleResults()
                .values()) {
            total += vehicle.getCpuOverflowRatio();
        }
        return total;
    }

    private Chromosome replaceGene(
            Chromosome chromosome,
            Gene replacement
    ) {
        List<Gene> replacedGenes = new ArrayList<>();

        for (Gene gene : chromosome.getGenes()) {
            if (gene.getTaskId().equals(replacement.getTaskId())) {
                replacedGenes.add(replacement);
            } else {
                replacedGenes.add(gene);
            }
        }

        Chromosome result = new Chromosome(replacedGenes);
        result.setFitness(chromosome.getFitness());
        return result;
    }

    private double computeLocalCpuCycles(
            TaskInstance task,
            Gene gene,
            NodeCandidate candidate
    ) {
        if (candidate.getType() == NodeType.LOCAL) {
            return Math.max(0.0, task.getCpuCycles());
        }

        double ratio = clamp(gene.getOffloadingRatio(), 0.0, 1.0);
        return Math.max(
                0.0,
                (1.0 - ratio) * task.getCpuCycles()
        );
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

    /**
     * Mossa già validata e prunata per il repair locale adattivo.
     */
    private static final class AdaptiveContentionMove {
        private final String taskId;
        private final String candidateId;
        private final Gene replacementGene;
        private final double replacementLocalCycles;
        private final double completionTimeSeconds;

        private AdaptiveContentionMove(
                String taskId,
                String candidateId,
                Gene replacementGene,
                double replacementLocalCycles,
                double completionTimeSeconds
        ) {
            this.taskId = taskId;
            this.candidateId = candidateId;
            this.replacementGene = replacementGene;
            this.replacementLocalCycles = replacementLocalCycles;
            this.completionTimeSeconds = completionTimeSeconds;
        }

        private String getTaskId() {
            return taskId;
        }

        private String getCandidateId() {
            return candidateId;
        }

        private Gene getReplacementGene() {
            return replacementGene;
        }

        private double getReplacementLocalCycles() {
            return replacementLocalCycles;
        }

        private double getCompletionTimeSeconds() {
            return completionTimeSeconds;
        }
    }

    /**
     * Stato compatto di un task nella coda EDF locale.
     */
    private static final class AdaptiveTaskState {
        private final String taskId;
        private final double deadlineSeconds;
        private double localCycles;

        private AdaptiveTaskState(TaskResult taskResult) {
            this.taskId = taskResult.getTaskId();
            this.deadlineSeconds = taskResult.getDeadlineSeconds();
            this.localCycles = AdaptiveVehicleState.normalizeLocalCycles(
                    taskResult.getLocalCpuCycles()
            );
        }

        private String getTaskId() {
            return taskId;
        }

        private double getDeadlineSeconds() {
            return deadlineSeconds;
        }

        private double getLocalCycles() {
            return localCycles;
        }

        private void setLocalCycles(double localCycles) {
            this.localCycles = AdaptiveVehicleState.normalizeLocalCycles(localCycles);
        }
    }

    /**
     * Metriche della sola coda EDF di un veicolo.
     */
    private static final class AdaptiveVehicleMetrics {
        private final int deadlineViolationCount;
        private final double maxDemandRatio;
        private final double overflowRatio;

        private AdaptiveVehicleMetrics(
                int deadlineViolationCount,
                double maxDemandRatio
        ) {
            this.deadlineViolationCount = deadlineViolationCount;
            this.maxDemandRatio = maxDemandRatio;
            this.overflowRatio = Math.max(0.0, maxDemandRatio - 1.0);
        }

        private int getDeadlineViolationCount() {
            return deadlineViolationCount;
        }

        private double getMaxDemandRatio() {
            return maxDemandRatio;
        }

        private double getOverflowRatio() {
            return overflowRatio;
        }

        private boolean hasViolations() {
            return deadlineViolationCount > 0
                    || overflowRatio > EPSILON;
        }
    }

    /**
     * Coda EDF modificabile usata dal repair adattivo.
     */
    private static final class AdaptiveVehicleState {
        private final String vehicleId;
        private final double localCpu;
        private final List<AdaptiveTaskState> tasks;
        private final Map<String, AdaptiveTaskState> taskById;
        private AdaptiveVehicleMetrics metrics;

        private AdaptiveVehicleState(VehicleResult vehicleResult) {
            this.vehicleId = vehicleResult.getVehicleId();
            this.localCpu = vehicleResult.getLocalCpu();
            this.tasks = new ArrayList<>();
            this.taskById = new LinkedHashMap<>();

            for (TaskResult taskResult : vehicleResult.getTaskResults()) {
                AdaptiveTaskState task =
                        new AdaptiveTaskState(taskResult);
                tasks.add(task);
                taskById.put(task.getTaskId(), task);
            }
            this.metrics = evaluateReplacement(null, 0.0);
        }

        private String getVehicleId() {
            return vehicleId;
        }

        private List<AdaptiveTaskState> getTasks() {
            return Collections.unmodifiableList(tasks);
        }

        private AdaptiveVehicleMetrics getMetrics() {
            return metrics;
        }

        private boolean hasViolations() {
            return metrics.hasViolations();
        }

        private double getLocalCycles(String taskId) {
            AdaptiveTaskState task = taskById.get(taskId);
            return task == null ? 0.0 : task.getLocalCycles();
        }

        private AdaptiveVehicleMetrics evaluateReplacement(
                String taskId,
                double replacementLocalCycles
        ) {
            double cumulativeCycles = 0.0;
            double maxDemandRatio = 0.0;
            int deadlineViolations = 0;

            for (AdaptiveTaskState task : tasks) {
                double localCycles =
                        taskId != null
                                && taskId.equals(task.getTaskId())
                                        ? normalizeLocalCycles(
                                                replacementLocalCycles
                                        )
                                        : task.getLocalCycles();

                if (localCycles <= EPSILON) {
                    continue;
                }

                cumulativeCycles += localCycles;
                double contendedTime = safeContentionDivide(
                        cumulativeCycles,
                        localCpu
                );
                double deadline = task.getDeadlineSeconds();
                double demandRatio =
                        !Double.isFinite(deadline)
                                || deadline <= EPSILON
                                        ? 0.0
                                        : safeContentionDivide(
                                                cumulativeCycles,
                                                localCpu * deadline
                                        );

                if (deadline > 0.0
                        && contendedTime > deadline + EPSILON) {
                    deadlineViolations++;
                }
                maxDemandRatio = Math.max(
                        maxDemandRatio,
                        demandRatio
                );
            }

            return new AdaptiveVehicleMetrics(
                    deadlineViolations,
                    maxDemandRatio
            );
        }

        private void applyReplacement(
                String taskId,
                double replacementLocalCycles
        ) {
            AdaptiveTaskState task = taskById.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException(
                        "Unknown adaptive local task: " + taskId
                );
            }
            task.setLocalCycles(replacementLocalCycles);
            metrics = evaluateReplacement(null, 0.0);
        }

        private static double normalizeLocalCycles(double value) {
            if (!Double.isFinite(value) || value < 0.0) {
                return 0.0;
            }
            return value;
        }

        private static double safeContentionDivide(
                double numerator,
                double denominator
        ) {
            if (!Double.isFinite(numerator)
                    || !Double.isFinite(denominator)
                    || denominator <= EPSILON) {
                return LOCAL_CONTENTION_INVALID_METRIC;
            }
            double value = numerator / denominator;
            if (!Double.isFinite(value)) {
                return LOCAL_CONTENTION_INVALID_METRIC;
            }
            return Math.min(value, LOCAL_CONTENTION_INVALID_METRIC);
        }
    }

    /**
     * Metriche aggregate sufficienti ai comparatori del repair.
     */
    private static final class LocalContentionMetrics {
        private final int violatingVehicleCount;
        private final int deadlineViolationCount;
        private final double maxOverflowRatio;
        private final double totalOverflowRatio;
        private final double maxDemandRatio;

        private LocalContentionMetrics(
            int violatingVehicleCount,
            int deadlineViolationCount,
            double maxOverflowRatio,
            double totalOverflowRatio,
            double maxDemandRatio
        ) {
            this.violatingVehicleCount = violatingVehicleCount;
            this.deadlineViolationCount = deadlineViolationCount;
            this.maxOverflowRatio = maxOverflowRatio;
            this.totalOverflowRatio = totalOverflowRatio;
            this.maxDemandRatio = maxDemandRatio;
        }

        private int getViolatingVehicleCount() {
            return violatingVehicleCount;
        }

        private int getDeadlineViolationCount() {
            return deadlineViolationCount;
        }

        private double getMaxOverflowRatio() {
            return maxOverflowRatio;
        }

        private double getTotalOverflowRatio() {
            return totalOverflowRatio;
        }

        private double getMaxDemandRatio() {
            return maxDemandRatio;
        }
    }

    /**
     * Rivaluta una sostituzione modificando soltanto la coda EDF del
     * veicolo sorgente coinvolto.
     */
    private static final class LocalContentionDeltaEvaluator {
        private final Evaluation baseline;
        private final VehicleResult affectedVehicle;

        private LocalContentionDeltaEvaluator(
            Evaluation baseline,
            VehicleResult affectedVehicle
        ) {
            this.baseline = Objects.requireNonNull(
                baseline,
                "baseline must not be null."
            );
            this.affectedVehicle = Objects.requireNonNull(
                affectedVehicle,
                "affectedVehicle must not be null."
            );
        }

        private LocalContentionMetrics evaluateReplacement(
            String taskId,
            double replacementLocalCycles
        ) {
            double localCpu = affectedVehicle.getLocalCpu();
            double cumulativeCycles = 0.0;
            double affectedMaxDemand = 0.0;
            int affectedDeadlineViolations = 0;

            for (TaskResult task
                : affectedVehicle.getTaskResults()) {
                double localCycles =
                    task.getTaskId().equals(taskId)
                        ? normalizeLocalCycles(replacementLocalCycles)
                        : task.getLocalCpuCycles();

                if (localCycles <= EPSILON) {
                    continue;
                }

                cumulativeCycles += localCycles;

                double contendedTime = safeContentionDivide(
                    cumulativeCycles,
                    localCpu
                );
                double deadline = task.getDeadlineSeconds();
                double demandRatio =
                    !Double.isFinite(deadline)
                        || deadline <= EPSILON
                            ? 0.0
                            : safeContentionDivide(
                                cumulativeCycles,
                                localCpu * deadline
                            );

                boolean deadlineRespected =
                    deadline <= 0.0
                        || contendedTime <= deadline + EPSILON;
                if (!deadlineRespected) {
                    affectedDeadlineViolations++;
                }

                affectedMaxDemand = Math.max(
                    affectedMaxDemand,
                    demandRatio
                );
            }

            double affectedOverflow = Math.max(
                0.0,
                affectedMaxDemand - 1.0
            );

            int violatingVehicleCount = 0;
            int deadlineViolationCount = 0;
            double maxOverflowRatio = 0.0;
            double totalOverflowRatio = 0.0;
            double maxDemandRatio = 0.0;

            for (VehicleResult vehicle
                : baseline.getVehicleResults().values()) {
                boolean affected =
                    affectedVehicle.getVehicleId().equals(
                        vehicle.getVehicleId()
                    );

                double overflow = affected
                    ? affectedOverflow
                    : vehicle.getCpuOverflowRatio();
                int violations = affected
                    ? affectedDeadlineViolations
                    : vehicle.getDeadlineViolationCount();
                double demand = affected
                    ? affectedMaxDemand
                    : vehicle.getMaxDemandRatio();

                if (overflow > EPSILON || violations > 0) {
                    violatingVehicleCount++;
                }
                deadlineViolationCount += violations;
                maxOverflowRatio = Math.max(
                    maxOverflowRatio,
                    overflow
                );
                totalOverflowRatio += overflow;
                maxDemandRatio = Math.max(
                    maxDemandRatio,
                    demand
                );
            }

            return new LocalContentionMetrics(
                violatingVehicleCount,
                deadlineViolationCount,
                maxOverflowRatio,
                totalOverflowRatio,
                maxDemandRatio
            );
        }

        private static double normalizeLocalCycles(double value) {
            if (!Double.isFinite(value) || value < 0.0) {
                return 0.0;
            }
            return value;
        }

        private static double safeContentionDivide(
            double numerator,
            double denominator
        ) {
            if (!Double.isFinite(numerator)
                || !Double.isFinite(denominator)
                || denominator <= EPSILON) {
                return LOCAL_CONTENTION_INVALID_METRIC;
            }
            double value = numerator / denominator;
            if (!Double.isFinite(value)) {
                return LOCAL_CONTENTION_INVALID_METRIC;
            }
            return Math.min(
                value,
                LOCAL_CONTENTION_INVALID_METRIC
            );
        }
    }
private static final class LocalContentionReplacement {
        private final String taskId;
        private final String candidateId;
        private final Gene replacementGene;
        private final double localCycleReduction;
        private final double offloadingRatio;
        private final double completionTimeSeconds;
        private final int violatingVehicleCount;
        private final int deadlineViolationCount;
        private final double maxOverflowRatio;
        private final double totalOverflowRatio;
        private final double maxDemandRatio;

        private LocalContentionReplacement(
                String taskId,
                String candidateId,
                Gene replacementGene,
                double localCycleReduction,
                double offloadingRatio,
                double completionTimeSeconds,
                int violatingVehicleCount,
                int deadlineViolationCount,
                double maxOverflowRatio,
                double totalOverflowRatio,
                double maxDemandRatio
        ) {
            this.taskId = taskId;
            this.candidateId = candidateId;
            this.replacementGene = replacementGene;
            this.localCycleReduction = localCycleReduction;
            this.offloadingRatio = offloadingRatio;
            this.completionTimeSeconds = completionTimeSeconds;
            this.violatingVehicleCount = violatingVehicleCount;
            this.deadlineViolationCount = deadlineViolationCount;
            this.maxOverflowRatio = maxOverflowRatio;
            this.totalOverflowRatio = totalOverflowRatio;
            this.maxDemandRatio = maxDemandRatio;
        }

        private String getTaskId() {
            return taskId;
        }

        private String getCandidateId() {
            return candidateId;
        }

        private Gene getReplacementGene() {
            return replacementGene;
        }

        private double getLocalCycleReduction() {
            return localCycleReduction;
        }

        private double getOffloadingRatio() {
            return offloadingRatio;
        }

        private double getCompletionTimeSeconds() {
            return completionTimeSeconds;
        }

        private int getViolatingVehicleCount() {
            return violatingVehicleCount;
        }

        private int getDeadlineViolationCount() {
            return deadlineViolationCount;
        }

        private double getMaxOverflowRatio() {
            return maxOverflowRatio;
        }

        private double getTotalOverflowRatio() {
            return totalOverflowRatio;
        }

        private double getMaxDemandRatio() {
            return maxDemandRatio;
        }
    }

    private static final class LocalContentionRepairResult {
        private final Chromosome chromosome;
        private final Set<String> affectedTaskIds;
        private final boolean changed;

        private LocalContentionRepairResult(
                Chromosome chromosome,
                Set<String> affectedTaskIds,
                boolean changed
        ) {
            this.chromosome = chromosome;
            this.affectedTaskIds = Set.copyOf(affectedTaskIds);
            this.changed = changed;
        }

        private static LocalContentionRepairResult unchanged(
                Chromosome chromosome
        ) {
            return new LocalContentionRepairResult(
                    chromosome,
                    Set.of(),
                    false
            );
        }

        private static LocalContentionRepairResult changed(
                Chromosome chromosome,
                Set<String> affectedTaskIds
        ) {
            return new LocalContentionRepairResult(
                    chromosome,
                    affectedTaskIds,
                    true
            );
        }

        private Chromosome getChromosome() {
            return chromosome;
        }

        private Set<String> getAffectedTaskIds() {
            return affectedTaskIds;
        }

        private boolean isChanged() {
            return changed;
        }
    }

}
