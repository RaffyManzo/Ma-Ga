package ga.operators;

import config.mobility.MobilityConfig;
import ga.constraints.DeadlineConstraintEvaluator;
import ga.constraints.DeadlineEvaluation;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ripara cromosomi e geni incoerenti.
 *
 * <p>Nel modello source-aware, un gene è valido solo se il candidato scelto
 * è compatibile con il veicolo sorgente del task.</p>
 *
 * <p>La riparazione avviene su quattro livelli:</p>
 *
 * <ol>
 *   <li>livello gene: corregge candidato, quota di offloading, CPU e banda;</li>
 *   <li>livello mobilità: evita candidati remoti con copertura insufficiente;</li>
 *   <li>livello deadline: prova una correzione limitata e aderente al modello;</li>
 *   <li>livello cromosoma: ridimensiona la CPU aggregata sui nodi fisici remoti.</li>
 * </ol>
 *
 * <p>La riparazione mobility-aware implementa direttamente il vincolo:</p>
 *
 * <pre>
 * T_i(C) &lt;= T_i^coverage(n_i)
 * </pre>
 *
 * <p>La riparazione deadline-aware non sostituisce il GA con un secondo
 * ottimizzatore. Esplora un insieme limitato di alternative coerenti con la
 * formalizzazione e conserva una strategia degradata best-effort soltanto
 * quando nessuna alternativa valutata rispetta la deadline.</p>
 */
public final class RepairOperator {
    private static final double EPSILON = 1.0E-9;
    private static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05;
    private static final double MIN_RESOURCE_FRACTION = 0.05;
    private static final int RESOURCE_SCALE_BINARY_SEARCH_STEPS = 24;

    /**
     * Numero massimo di passaggi repair gene + repair CPU aggregata.
     *
     * <p>Serve perché il repair CPU aggregato può ridurre la CPU assegnata e
     * rendere nuovamente insufficiente la copertura o la deadline. Due passaggi
     * sono una scelta prudente: correggono l'effetto più comune senza
     * introdurre un ciclo di ottimizzazione locale che snaturerebbe il GA.</p>
     */
    private static final int MAX_REPAIR_PASSES = 2;

    private final CpuAggregateRepairOperator cpuAggregateRepairOperator;
    private final CoverageEstimator coverageEstimator;
    private final OffloadingTimeModel offloadingTimeModel;
    private final OffloadingRatioPolicy offloadingRatioPolicy;
    private final DeadlineConstraintEvaluator deadlineConstraintEvaluator;

    /**
     * Costruttore compatibile con il codice precedente.
     */
    public RepairOperator() {
        this(MobilityConfig.defaultConfig());
    }

    /**
     * Costruisce il repair operator principale con configurazione di mobilità
     * esplicita.
     *
     * @param mobilityConfig configurazione usata da {@link CoverageEstimator}
     */
    public RepairOperator(MobilityConfig mobilityConfig) {
        Objects.requireNonNull(mobilityConfig, "mobilityConfig must not be null.");
        this.cpuAggregateRepairOperator = new CpuAggregateRepairOperator();
        this.coverageEstimator = new CoverageEstimator(mobilityConfig);
        this.offloadingTimeModel = new OffloadingTimeModel();
        this.offloadingRatioPolicy = new OffloadingRatioPolicy();
        this.deadlineConstraintEvaluator = new DeadlineConstraintEvaluator(
                coverageEstimator,
                offloadingTimeModel
        );
    }

    /**
     * Ripara un cromosoma rispetto allo snapshot corrente.
     *
     * @param chromosome cromosoma da riparare
     * @param snapshot snapshot corrente
     * @return cromosoma riparato
     */
    public Chromosome repairChromosome(Chromosome chromosome, SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Chromosome current = chromosome;
        for (int pass = 0; pass < MAX_REPAIR_PASSES; pass++) {
            current = repairGenes(current, snapshot);
            current = cpuAggregateRepairOperator.repairChromosome(current, snapshot);
        }
        return current;
    }

    private Chromosome repairGenes(Chromosome chromosome, SystemSnapshot snapshot) {
        List<Gene> repairedGenes = new ArrayList<>();
        for (TaskInstance task : snapshot.getTasks()) {
            Gene gene = findGene(chromosome, task.getTaskId());
            if (gene == null) {
                gene = createFallbackGene(task, snapshot);
            }
            repairedGenes.add(repairGene(gene, task, snapshot));
        }
        Chromosome repaired = new Chromosome(repairedGenes);
        if (chromosome != null) {
            repaired.setFitness(chromosome.getFitness());
        }
        return repaired;
    }

    /**
     * Ripara un gene rispetto al task e allo snapshot corrente.
     *
     * @param gene gene da riparare
     * @param task task associato al gene
     * @param snapshot snapshot corrente
     * @return gene coerente con il task
     */
    public Gene repairGene(Gene gene, TaskInstance task, SystemSnapshot snapshot) {
        Objects.requireNonNull(gene, "gene must not be null.");
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        NodeCandidate localCandidate = requireLocalCandidate(task, snapshot);
        NodeCandidate candidate = findCandidate(snapshot, gene.getSelectedCandidateId());
        if (candidate == null
                || !candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
            candidate = localCandidate;
        }

        VehicleSnapshot sourceVehicle = findVehicle(snapshot, task.getSourceVehicleId());
        double offloadingRatio = clamp(gene.getOffloadingRatio(), 0.0, 1.0);
        double allocatedCpu = Math.max(0.0, gene.getAllocatedCpu());
        double allocatedBandwidth = Math.max(0.0, gene.getAllocatedBandwidth());

        Gene mobilityCoherentGene;
        if (candidate.getType() == NodeType.LOCAL) {
            mobilityCoherentGene = createLocalGene(task, candidate, sourceVehicle);
        } else {
            if (offloadingRatio <= EPSILON) {
                offloadingRatio = MIN_REMOTE_OFFLOADING_RATIO;
            }
            allocatedCpu = clampResource(allocatedCpu, candidate.getAvailableCpu());
            allocatedBandwidth = clampResource(
                    allocatedBandwidth,
                    candidate.getAvailableBandwidth()
            );

            if (!isCoverageSufficient(
                    snapshot,
                    task,
                    candidate,
                    sourceVehicle,
                    offloadingRatio,
                    allocatedCpu,
                    allocatedBandwidth
            )) {
                NodeCandidate replacement = findCoverageSustainableRemoteCandidate(
                        snapshot,
                        task,
                        sourceVehicle,
                        offloadingRatio,
                        allocatedCpu,
                        allocatedBandwidth,
                        candidate.getCandidateId()
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
                snapshot,
                sourceVehicle,
                localCandidate
        );
    }

    /**
     * Applica la policy deadline-aware soltanto quando il gene corrente non
     * rispetta la deadline.
     *
     * <p>L'ordine resta volutamente limitato:</p>
     *
     * <ol>
     *   <li>prova quote alternative mantenendo il nodo remoto corrente;</li>
     *   <li>prova candidati remoti alternativi;</li>
     *   <li>prova l'esecuzione locale;</li>
     *   <li>se nessuna alternativa è ammissibile, sceglie il best-effort.</li>
     * </ol>
     */
    private Gene repairDeadlineIfNeeded(
            Gene currentGene,
            TaskInstance task,
            SystemSnapshot snapshot,
            VehicleSnapshot sourceVehicle,
            NodeCandidate localCandidate
    ) {
        DeadlineEvaluation currentEvaluation = deadlineConstraintEvaluator.evaluate(
                currentGene,
                task,
                snapshot
        );
        if (currentEvaluation.isDeadlineRespected()) {
            return currentGene;
        }

        NodeCandidate currentCandidate = findCandidate(
                snapshot,
                currentGene.getSelectedCandidateId()
        );

        if (currentCandidate != null && currentCandidate.getType() != NodeType.LOCAL) {
            Gene repairedOnCurrentCandidate = findDeadlineFeasibleGeneForCandidate(
                    task,
                    snapshot,
                    sourceVehicle,
                    currentCandidate,
                    currentGene.getOffloadingRatio(),
                    currentGene.getAllocatedCpu(),
                    currentGene.getAllocatedBandwidth()
            );
            if (repairedOnCurrentCandidate != null) {
                return repairedOnCurrentCandidate;
            }
        }

        Gene repairedOnAlternativeRemote = findDeadlineFeasibleRemoteAlternative(
                task,
                snapshot,
                sourceVehicle,
                currentCandidate == null ? null : currentCandidate.getCandidateId(),
                currentGene
        );
        if (repairedOnAlternativeRemote != null) {
            return repairedOnAlternativeRemote;
        }

        Gene localGene = createLocalGene(task, localCandidate, sourceVehicle);
        if (deadlineConstraintEvaluator.evaluate(localGene, task, snapshot).isAdmissible()) {
            return localGene;
        }

        return selectDegradedBestEffortGene(
                task,
                snapshot,
                sourceVehicle,
                localGene,
                currentGene
        );
    }

    private Gene findDeadlineFeasibleRemoteAlternative(
            TaskInstance task,
            SystemSnapshot snapshot,
            VehicleSnapshot sourceVehicle,
            String excludedCandidateId,
            Gene currentGene
    ) {
        Gene bestGene = null;
        DeadlineEvaluation bestEvaluation = null;

        for (NodeCandidate candidate : findCandidatesForTask(task, snapshot)) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }
            if (candidate.getCandidateId().equals(excludedCandidateId)) {
                continue;
            }

            Gene candidateGene = findDeadlineFeasibleGeneForCandidate(
                    task,
                    snapshot,
                    sourceVehicle,
                    candidate,
                    currentGene.getOffloadingRatio(),
                    currentGene.getAllocatedCpu(),
                    currentGene.getAllocatedBandwidth()
            );
            if (candidateGene == null) {
                continue;
            }

            DeadlineEvaluation evaluation = deadlineConstraintEvaluator.evaluate(
                    candidateGene,
                    task,
                    snapshot
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
            SystemSnapshot snapshot,
            VehicleSnapshot sourceVehicle,
            NodeCandidate candidate,
            double preferredRatio,
            double preferredCpu,
            double preferredBandwidth
    ) {
        Gene bestGene = null;
        double bestPressure = Double.POSITIVE_INFINITY;
        double bestCompletion = Double.POSITIVE_INFINITY;

        for (double ratio : buildRatioCandidates(
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
            Gene feasibleGene = null;
            DeadlineEvaluation evaluation = deadlineConstraintEvaluator.evaluate(
                    preservedResourcesGene,
                    task,
                    snapshot
            );
            if (evaluation.isAdmissible()) {
                feasibleGene = preservedResourcesGene;
            } else {
                feasibleGene = findMinimalFeasibleResourceScale(
                        task,
                        snapshot,
                        candidate,
                        ratio
                );
                if (feasibleGene != null) {
                    evaluation = deadlineConstraintEvaluator.evaluate(
                            feasibleGene,
                            task,
                            snapshot
                    );
                }
            }

            if (feasibleGene == null || !evaluation.isAdmissible()) {
                continue;
            }

            double pressure = computeResourcePressure(feasibleGene, candidate);
            if (pressure < bestPressure - EPSILON
                    || (Math.abs(pressure - bestPressure) <= EPSILON
                    && evaluation.getCompletionTimeSeconds() < bestCompletion)) {
                bestGene = feasibleGene;
                bestPressure = pressure;
                bestCompletion = evaluation.getCompletionTimeSeconds();
            }
        }
        return bestGene;
    }

    /**
     * Cerca la minima scala comune di CPU e banda che rende ammissibile la
     * scelta remota per una quota data.
     *
     * <p>La ricerca binaria non è una nuova variabile del cromosoma. È una
     * correzione interna e limitata delle due risorse già formalizzate.</p>
     */
    private Gene findMinimalFeasibleResourceScale(
            TaskInstance task,
            SystemSnapshot snapshot,
            NodeCandidate candidate,
            double ratio
    ) {
        if (!isStrictlyPositive(candidate.getAvailableCpu())
                || !isStrictlyPositive(candidate.getAvailableBandwidth())) {
            return null;
        }

        Gene maxCapacityGene = createScaledRemoteGene(task, candidate, ratio, 1.0);
        if (!deadlineConstraintEvaluator
                .evaluate(maxCapacityGene, task, snapshot)
                .isAdmissible()) {
            return null;
        }

        double low = MIN_RESOURCE_FRACTION;
        double high = 1.0;
        for (int step = 0; step < RESOURCE_SCALE_BINARY_SEARCH_STEPS; step++) {
            double middle = (low + high) / 2.0;
            Gene middleGene = createScaledRemoteGene(task, candidate, ratio, middle);
            if (deadlineConstraintEvaluator
                    .evaluate(middleGene, task, snapshot)
                    .isAdmissible()) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return createScaledRemoteGene(task, candidate, ratio, high);
    }

    private Gene createScaledRemoteGene(
            TaskInstance task,
            NodeCandidate candidate,
            double ratio,
            double resourceScale
    ) {
        double scale = clamp(resourceScale, MIN_RESOURCE_FRACTION, 1.0);
        return new Gene(
                task.getTaskId(),
                candidate.getCandidateId(),
                clamp(ratio, MIN_REMOTE_OFFLOADING_RATIO, 1.0),
                candidate.getAvailableCpu() * scale,
                candidate.getAvailableBandwidth() * scale
        );
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
            SystemSnapshot snapshot,
            VehicleSnapshot sourceVehicle,
            Gene localGene,
            Gene currentGene
    ) {
        Gene bestGene = localGene;
        DeadlineEvaluation bestEvaluation = deadlineConstraintEvaluator.evaluate(
                localGene,
                task,
                snapshot
        );

        for (NodeCandidate candidate : findCandidatesForTask(task, snapshot)) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }

            for (double ratio : buildRatioCandidates(
                    task,
                    candidate,
                    sourceVehicle,
                    currentGene.getOffloadingRatio()
            )) {
                Gene remoteGene = createScaledRemoteGene(task, candidate, ratio, 1.0);
                DeadlineEvaluation evaluation = deadlineConstraintEvaluator.evaluate(
                        remoteGene,
                        task,
                        snapshot
                );
                if (!evaluation.isValid() || !evaluation.isMobilitySustainable()) {
                    continue;
                }
                if (isBetterBestEffortChoice(
                        remoteGene,
                        evaluation,
                        bestGene,
                        bestEvaluation,
                        snapshot
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
            SystemSnapshot snapshot
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

        NodeCandidate candidate = findCandidate(
                snapshot,
                candidateGene.getSelectedCandidateId()
        );
        NodeCandidate currentBest = findCandidate(
                snapshot,
                currentBestGene.getSelectedCandidateId()
        );
        return candidate != null
                && currentBest != null
                && candidate.getType() == NodeType.LOCAL
                && currentBest.getType() != NodeType.LOCAL;
    }

    private List<Double> buildRatioCandidates(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double preferredRatio
    ) {
        Set<Double> ratios = new LinkedHashSet<>();
        ratios.add(clamp(preferredRatio, MIN_REMOTE_OFFLOADING_RATIO, 1.0));
        ratios.add(
                clamp(
                        offloadingRatioPolicy.balancedRemoteRatio(
                                task,
                                candidate,
                                sourceVehicle
                        ),
                        MIN_REMOTE_OFFLOADING_RATIO,
                        1.0
                )
        );
        for (int step = 1; step <= 20; step++) {
            ratios.add(step * 0.05);
        }
        return new ArrayList<>(ratios);
    }

    private double computeResourcePressure(Gene gene, NodeCandidate candidate) {
        return safeDivide(gene.getAllocatedCpu(), candidate.getAvailableCpu())
                + safeDivide(
                        gene.getAllocatedBandwidth(),
                        candidate.getAvailableBandwidth()
                );
    }

    /**
     * Crea un gene locale di fallback quando il cromosoma non contiene il task.
     */
    private Gene createFallbackGene(TaskInstance task, SystemSnapshot snapshot) {
        NodeCandidate localCandidate = requireLocalCandidate(task, snapshot);
        VehicleSnapshot sourceVehicle = findVehicle(snapshot, task.getSourceVehicleId());
        return createLocalGene(task, localCandidate, sourceVehicle);
    }

    /**
     * Recupera il candidato locale obbligatorio del veicolo sorgente.
     *
     * <p>Il fallback locale non può riutilizzare un candidato remoto. Se manca
     * il nodo locale, lo snapshot viola un'invariante del modello e deve essere
     * rifiutato invece di generare un gene semanticamente incoerente.</p>
     */
    private NodeCandidate requireLocalCandidate(
            TaskInstance task,
            SystemSnapshot snapshot
    ) {
        NodeCandidate localCandidate = findLocalCandidate(task, snapshot);
        if (localCandidate == null) {
            throw new IllegalArgumentException(
                    "No LOCAL candidate found for task "
                            + task.getTaskId()
                            + " and source vehicle "
                            + task.getSourceVehicleId()
            );
        }
        return localCandidate;
    }

    private NodeCandidate findLocalCandidate(TaskInstance task, SystemSnapshot snapshot) {
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.isValidForSourceVehicle(task.getSourceVehicleId())
                    && candidate.getType() == NodeType.LOCAL) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Cerca un candidato remoto alternativo che soddisfi la copertura.
     *
     * <p>La scelta resta prudente: non si cerca il candidato con fitness
     * migliore, ma il candidato remoto con completion time stimato più basso
     * tra quelli che rispettano la copertura. Questo è repair di vincolo, non
     * una seconda ottimizzazione locale.</p>
     */
    private NodeCandidate findCoverageSustainableRemoteCandidate(
            SystemSnapshot snapshot,
            TaskInstance task,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio,
            double allocatedCpu,
            double allocatedBandwidth,
            String excludedCandidateId
    ) {
        NodeCandidate bestCandidate = null;
        double bestCompletionTime = Double.POSITIVE_INFINITY;

        for (NodeCandidate candidate : findCandidatesForTask(task, snapshot)) {
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
                    snapshot,
                    task,
                    candidate
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
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio,
            double allocatedCpu,
            double allocatedBandwidth
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
        double coverageTime = estimateCoverageTimeSeconds(snapshot, task, candidate);
        return isStrictlyPositive(coverageTime) && completionTime <= coverageTime;
    }

    private double estimateCoverageTimeSeconds(
            SystemSnapshot snapshot,
            TaskInstance task,
            NodeCandidate candidate
    ) {
        try {
            return coverageEstimator.estimateCoverageTimeSeconds(
                    snapshot,
                    task,
                    candidate
            );
        } catch (IllegalArgumentException ex) {
            return 0.0;
        }
    }

    /**
     * Stima il completion time usando la stessa struttura della fitness.
     */
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
        double p = clamp(offloadingRatio, MIN_REMOTE_OFFLOADING_RATIO, 1.0);
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
                || !candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
            throw new IllegalArgumentException(
                    "Local fallback requires a LOCAL candidate valid for source vehicle "
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

    /**
     * Trova candidati validi per un task.
     */
    private List<NodeCandidate> findCandidatesForTask(
            TaskInstance task,
            SystemSnapshot snapshot
    ) {
        List<NodeCandidate> result = new ArrayList<>();
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * Cerca un gene per taskId.
     */
    private Gene findGene(Chromosome chromosome, String taskId) {
        if (chromosome == null || chromosome.getGenes() == null) {
            return null;
        }
        for (Gene gene : chromosome.getGenes()) {
            if (gene.getTaskId().equals(taskId)) {
                return gene;
            }
        }
        return null;
    }

    /**
     * Cerca un candidato per candidateId.
     */
    private NodeCandidate findCandidate(SystemSnapshot snapshot, String candidateId) {
        if (candidateId == null) {
            return null;
        }
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getCandidateId().equals(candidateId)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Cerca un veicolo.
     */
    private VehicleSnapshot findVehicle(SystemSnapshot snapshot, String vehicleId) {
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            if (vehicle.getVehicleId().equals(vehicleId)) {
                return vehicle;
            }
        }
        return null;
    }

    /**
     * Limita una risorsa al range ammesso dal singolo candidato.
     */
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

    /**
     * Limita un valore dentro un intervallo.
     */
    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
