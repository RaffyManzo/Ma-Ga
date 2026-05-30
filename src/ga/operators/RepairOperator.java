package ga.operators;

import config.mobility.MobilityConfig;
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
import java.util.List;
import java.util.Objects;

/**
 * Ripara cromosomi e geni incoerenti.
 *
 * <p>Nel modello source-aware, un gene è valido solo se il candidato scelto
 * è compatibile con il veicolo sorgente del task.</p>
 *
 * <p>La riparazione avviene su tre livelli:</p>
 *
 * <ol>
 *     <li>livello gene: corregge candidato, quota di offloading, CPU e banda;</li>
 *     <li>livello mobilità: evita candidati remoti con copertura insufficiente;</li>
 *     <li>livello cromosoma: ridimensiona la CPU aggregata sui nodi fisici remoti.</li>
 * </ol>
 *
 * <p>La riparazione mobility-aware implementa direttamente il vincolo:</p>
 *
 * <pre>
 * T_i(C) <= T_i^coverage(n_i)
 * </pre>
 *
 * <p>Non modifica la fitness, non aggiunge nuove variabili decisionali e non
 * sostituisce la selezione genetica: elimina solo geni remoti che violano
 * un vincolo già presente nella formalizzazione.</p>
 */
public final class RepairOperator {

    private static final double EPSILON = 1.0E-9;
    private static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05;
    private static final double MIN_RESOURCE_FRACTION = 0.05;

    /**
     * Numero massimo di passaggi repair gene + repair CPU aggregata.
     *
     * <p>Serve perché il repair CPU aggregato può ridurre la CPU assegnata e
     * rendere nuovamente insufficiente la copertura. Due passaggi sono una
     * scelta prudente: correggono l'effetto più comune senza introdurre un
     * ciclo di ottimizzazione locale che snaturerebbe il GA.</p>
     */
    private static final int MAX_REPAIR_PASSES = 2;

    private final CpuAggregateRepairOperator cpuAggregateRepairOperator;
    private final CoverageEstimator coverageEstimator;
    private final OffloadingTimeModel offloadingTimeModel;

    /**
     * Costruttore compatibile con il codice precedente.
     */
    public RepairOperator() {
        this(MobilityConfig.defaultConfig());
    }

    /**
     * Costruisce il repair operator principale con configurazione di mobilità esplicita.
     *
     * @param mobilityConfig configurazione usata da CoverageEstimator
     */
    public RepairOperator(MobilityConfig mobilityConfig) {
        this.cpuAggregateRepairOperator = new CpuAggregateRepairOperator();
        this.coverageEstimator = new CoverageEstimator(
                Objects.requireNonNull(mobilityConfig, "mobilityConfig must not be null.")
        );
        this.offloadingTimeModel = new OffloadingTimeModel();
    }

    /**
     * Ripara un cromosoma rispetto allo snapshot corrente.
     *
     * @param chromosome cromosoma da riparare
     * @param snapshot snapshot corrente
     * @return cromosoma riparato
     */
    public Chromosome repairChromosome(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        Chromosome current = chromosome;

        for (int pass = 0; pass < MAX_REPAIR_PASSES; pass++) {
            current = repairGenes(current, snapshot);
            current = cpuAggregateRepairOperator.repairChromosome(current, snapshot);
        }

        return current;
    }

    private Chromosome repairGenes(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
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
    public Gene repairGene(
            Gene gene,
            TaskInstance task,
            SystemSnapshot snapshot
    ) {
        NodeCandidate candidate = findCandidate(
                snapshot,
                gene.getSelectedCandidateId()
        );

        if (candidate == null || !candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
            candidate = defaultCandidate(task, snapshot);
        }

        VehicleSnapshot sourceVehicle = findVehicle(
                snapshot,
                task.getSourceVehicleId()
        );

        double offloadingRatio = clamp(
                gene.getOffloadingRatio(),
                0.0,
                1.0
        );

        double allocatedCpu = Math.max(0.0, gene.getAllocatedCpu());
        double allocatedBandwidth = Math.max(0.0, gene.getAllocatedBandwidth());

        if (candidate.getType() == NodeType.LOCAL) {
            return createLocalGene(task, candidate, sourceVehicle);
        }

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
                NodeCandidate localCandidate = findLocalCandidate(task, snapshot);

                if (localCandidate != null) {
                    return createLocalGene(task, localCandidate, sourceVehicle);
                }

                return createLocalGene(task, candidate, sourceVehicle);
            }

            candidate = replacement;
            allocatedCpu = clampResource(
                    allocatedCpu,
                    candidate.getAvailableCpu()
            );
            allocatedBandwidth = clampResource(
                    allocatedBandwidth,
                    candidate.getAvailableBandwidth()
            );
        }

        return new Gene(
                task.getTaskId(),
                candidate.getCandidateId(),
                offloadingRatio,
                allocatedCpu,
                allocatedBandwidth
        );
    }

    /**
     * Crea un gene di fallback quando il cromosoma non contiene il task.
     */
    private Gene createFallbackGene(
            TaskInstance task,
            SystemSnapshot snapshot
    ) {
        NodeCandidate candidate = defaultCandidate(task, snapshot);
        VehicleSnapshot sourceVehicle = findVehicle(
                snapshot,
                task.getSourceVehicleId()
        );

        if (candidate.getType() == NodeType.LOCAL) {
            return createLocalGene(task, candidate, sourceVehicle);
        }

        double offloadingRatio = MIN_REMOTE_OFFLOADING_RATIO;
        double allocatedCpu = clampResource(0.0, candidate.getAvailableCpu());
        double allocatedBandwidth = clampResource(0.0, candidate.getAvailableBandwidth());

        if (!isCoverageSufficient(
                snapshot,
                task,
                candidate,
                sourceVehicle,
                offloadingRatio,
                allocatedCpu,
                allocatedBandwidth
        )) {
            NodeCandidate localCandidate = findLocalCandidate(task, snapshot);

            if (localCandidate != null) {
                return createLocalGene(task, localCandidate, sourceVehicle);
            }
        }

        return new Gene(
                task.getTaskId(),
                candidate.getCandidateId(),
                offloadingRatio,
                allocatedCpu,
                allocatedBandwidth
        );
    }

    /**
     * Sceglie il candidato di default per un task.
     *
     * <p>Preferisce LOCAL del veicolo sorgente, se presente.</p>
     */
    private NodeCandidate defaultCandidate(
            TaskInstance task,
            SystemSnapshot snapshot
    ) {
        NodeCandidate localCandidate = findLocalCandidate(task, snapshot);

        if (localCandidate != null) {
            return localCandidate;
        }

        List<NodeCandidate> validCandidates = findCandidatesForTask(
                task,
                snapshot
        );

        if (validCandidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No valid candidate found for task "
                            + task.getTaskId()
                            + " and source vehicle "
                            + task.getSourceVehicleId()
            );
        }

        return validCandidates.get(0);
    }

    private NodeCandidate findLocalCandidate(
            TaskInstance task,
            SystemSnapshot snapshot
    ) {
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
     * <p>La scelta resta prudente: non si cerca il candidato con fitness migliore,
     * ma il candidato remoto con completion time stimato più basso tra quelli
     * che rispettano la copertura. Questo è repair di vincolo, non una seconda
     * ottimizzazione locale.</p>
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

        double coverageTime = estimateCoverageTimeSeconds(
                snapshot,
                task,
                candidate
        );

        return isStrictlyPositive(coverageTime)
                && completionTime <= coverageTime;
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
            return offloadingTimeModel.evaluateLocal(
                    task,
                    localCpu
            ).getCompletionTimeSeconds();
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

        return offloadingTimeModel.evaluateRemote(
                task,
                candidate,
                localCpu,
                p,
                allocatedCpu,
                allocatedBandwidth
        ).getCompletionTimeSeconds();
    }

    private Gene createLocalGene(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
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
    private Gene findGene(
            Chromosome chromosome,
            String taskId
    ) {
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
    private NodeCandidate findCandidate(
            SystemSnapshot snapshot,
            String candidateId
    ) {
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
    private VehicleSnapshot findVehicle(
            SystemSnapshot snapshot,
            String vehicleId
    ) {
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
    private double clampResource(
            double value,
            double maxAvailable
    ) {
        if (!Double.isFinite(maxAvailable) || maxAvailable <= 0.0) {
            return 0.0;
        }

        double min = maxAvailable * MIN_RESOURCE_FRACTION;

        if (!Double.isFinite(value) || value <= 0.0) {
            return min;
        }

        return clamp(value, min, maxAvailable);
    }

    private boolean isStrictlyPositive(double value) {
        return Double.isFinite(value) && value > EPSILON;
    }

    /**
     * Limita un valore dentro un intervallo.
     */
    private double clamp(
            double value,
            double min,
            double max
    ) {
        if (!Double.isFinite(value)) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }
}
