package ga.operators;

import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Operatore di mutazione del MA-GA.
 *
 * La mutazione mantiene la componente casuale dell'algoritmo genetico,
 * ma non agisce più solo con piccole variazioni locali.
 *
 * Per la quota di offloading p_i usa più modalità:
 *
 * - piccola perturbazione locale;
 * - reset casuale;
 * - salto verso p = 1;
 * - salto verso p bilanciato tra ramo locale e ramo remoto.
 *
 * Inoltre, quando muta il candidato, sceglie solo candidati validi
 * per il veicolo sorgente del task.
 */
public final class MutationOperator {

    private static final double MIN_RESOURCE_FRACTION = 0.05;

    /**
     * Probabilità interna di cambiare candidato quando un gene muta.
     */
    private static final double CANDIDATE_MUTATION_PROBABILITY = 0.25;

    /**
     * Probabilità di preferire candidati remoti quando viene cambiato candidato.
     */
    private static final double REMOTE_CANDIDATE_PREFERENCE = 0.60;

    /**
     * Probabilità di scegliere il candidato remoto con migliore stima euristica
     * invece di un remoto casuale.
     */
    private static final double BEST_REMOTE_CANDIDATE_PROBABILITY = 0.55;

    private final Random random;
    private final OffloadingRatioPolicy offloadingRatioPolicy;
    private final ResourceAllocationPolicy resourceAllocationPolicy;

    /**
     * Costruisce l'operatore di mutazione.
     *
     * @param random generatore casuale condiviso dal GA
     */
    public MutationOperator(Random random) {
        this.random = Objects.requireNonNull(
                random,
                "random must not be null."
        );

        this.offloadingRatioPolicy = new OffloadingRatioPolicy();
        this.resourceAllocationPolicy = new ResourceAllocationPolicy();
    }

    /**
     * Applica la mutazione a un cromosoma.
     *
     * Ogni gene viene mutato con probabilità mutationRate.
     * I geni non mutati vengono copiati senza modifiche.
     *
     * @param chromosome cromosoma da mutare
     * @param snapshot snapshot corrente
     * @param mutationRate probabilità di mutazione per gene
     * @return cromosoma mutato
     */
    public Chromosome mutate(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            double mutationRate
    ) {
        validateRate(mutationRate);

        List<Gene> mutatedGenes = new ArrayList<>();

        for (Gene gene : chromosome.getGenes()) {
            if (random.nextDouble() < mutationRate) {
                mutatedGenes.add(
                        mutateGene(
                                gene,
                                snapshot
                        )
                );
            } else {
                mutatedGenes.add(gene);
            }
        }

        Chromosome mutated = new Chromosome(mutatedGenes);
        mutated.setFitness(chromosome.getFitness());

        return mutated;
    }

    /**
     * Muta un singolo gene.
     *
     * La mutazione può:
     *
     * - mantenere il candidato e cambiare solo p_i/risorse;
     * - cambiare candidato e ricalcolare p_i in modo coerente;
     * - trasformare una decisione locale in una remota;
     * - trasformare una decisione remota in locale, se il candidato locale viene scelto.
     */
    private Gene mutateGene(
            Gene gene,
            SystemSnapshot snapshot
    ) {
        TaskInstance task = findTask(
                snapshot,
                gene.getTaskId()
        );

        if (task == null) {
            return gene;
        }

        VehicleSnapshot sourceVehicle = findVehicle(
                snapshot,
                task.getSourceVehicleId()
        );

        List<NodeCandidate> validCandidates =
                findCandidatesForTask(
                        task,
                        snapshot
                );

        if (validCandidates.isEmpty()) {
            return gene;
        }

        NodeCandidate currentCandidate = findCandidate(
                snapshot,
                gene.getSelectedCandidateId()
        );

        boolean candidateChanged =
                currentCandidate == null
                        || !currentCandidate.isValidForSourceVehicle(
                        task.getSourceVehicleId()
                )
                        || random.nextDouble()
                        < CANDIDATE_MUTATION_PROBABILITY;

        NodeCandidate selectedCandidate = candidateChanged
                ? selectCandidateForMutation(
                task,
                validCandidates,
                sourceVehicle
        )
                : currentCandidate;

        if (selectedCandidate.getType() == NodeType.LOCAL) {
            return createLocalGene(
                    task,
                    selectedCandidate,
                    sourceVehicle
            );
        }

        double offloadingRatio = mutateOffloadingRatio(
                gene,
                task,
                selectedCandidate,
                sourceVehicle,
                candidateChanged
        );

        ResourceAllocationDecision allocation =
                resourceAllocationPolicy.mutate(
                        gene,
                        task,
                        selectedCandidate,
                        sourceVehicle,
                        offloadingRatio,
                        candidateChanged,
                        random
                );

        return new Gene(
                task.getTaskId(),
                selectedCandidate.getCandidateId(),
                offloadingRatio,
                allocation.getAllocatedCpu(),
                allocation.getAllocatedBandwidth()
        );
    }

    /**
     * Sceglie un candidato valido per la mutazione.
     *
     * Preferisce candidati remoti perché il problema osservato nei report
     * è un uso troppo conservativo del locale. Non elimina però il locale:
     * mantiene una quota di casualità e quindi di diversità.
     */
    private NodeCandidate selectCandidateForMutation(
            TaskInstance task,
            List<NodeCandidate> validCandidates,
            VehicleSnapshot sourceVehicle
    ) {
        List<NodeCandidate> remoteCandidates =
                findRemoteCandidates(validCandidates);

        NodeCandidate localCandidate =
                findLocalCandidate(validCandidates);

        if (!remoteCandidates.isEmpty()
                && random.nextDouble() < REMOTE_CANDIDATE_PREFERENCE) {
            if (random.nextDouble() < BEST_REMOTE_CANDIDATE_PROBABILITY) {
                return selectBestEstimatedRemoteCandidate(
                        task,
                        remoteCandidates,
                        sourceVehicle
                );
            }

            return remoteCandidates.get(
                    random.nextInt(remoteCandidates.size())
            );
        }

        if (localCandidate != null && random.nextDouble() < 0.50) {
            return localCandidate;
        }

        return validCandidates.get(
                random.nextInt(validCandidates.size())
        );
    }

    /**
     * Muta la quota di offloading p_i.
     *
     * La mutazione resta genetica e casuale, ma quando serve una quota
     * "ragionata" usa una stima deadline-aware invece del solo bilanciamento
     * locale/remoto.
     */
    private double mutateOffloadingRatio(
            Gene gene,
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            boolean candidateChanged
    ) {
        if (candidate.getType() == NodeType.LOCAL) {
            return offloadingRatioPolicy.localRatio();
        }

        double roll = random.nextDouble();

        if (candidateChanged) {
            if (roll < 0.50) {
                return offloadingRatioPolicy.deadlineAwareRatio(
                        task,
                        candidate,
                        sourceVehicle,
                        random
                );
            }

            if (roll < 0.62) {
                return offloadingRatioPolicy.mutateToFullOffloading();
            }

            if (roll < 0.85) {
                return offloadingRatioPolicy.mutateByRandomReset(random);
            }

            return offloadingRatioPolicy.mutateBySmallStep(
                    gene.getOffloadingRatio(),
                    random
            );
        }

        if (roll < 0.50) {
            return offloadingRatioPolicy.mutateBySmallStep(
                    gene.getOffloadingRatio(),
                    random
            );
        }

        if (roll < 0.78) {
            return offloadingRatioPolicy.deadlineAwareRatio(
                    task,
                    candidate,
                    sourceVehicle,
                    random
            );
        }

        if (roll < 0.88) {
            return offloadingRatioPolicy.mutateToFullOffloading();
        }

        return offloadingRatioPolicy.mutateByRandomReset(random);
    }

    /**
     * Crea un gene locale coerente.
     */
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
                offloadingRatioPolicy.localRatio(),
                localCpu,
                0.0
        );
    }

    /**
     * Sceglie il candidato remoto con migliore stima euristica.
     *
     * La stima non sostituisce la fitness.
     * Serve solo a non sprecare mutazioni su candidati palesemente peggiori.
     */
    private NodeCandidate selectBestEstimatedRemoteCandidate(
            TaskInstance task,
            List<NodeCandidate> remoteCandidates,
            VehicleSnapshot sourceVehicle
    ) {
        return remoteCandidates
                .stream()
                .min(
                        Comparator.comparingDouble(
                                candidate ->
                                        estimateBestCompletion(
                                                task,
                                                candidate,
                                                sourceVehicle
                                        )
                        )
                )
                .orElse(
                        remoteCandidates.get(
                                random.nextInt(remoteCandidates.size())
                        )
                );
    }

    /**
     * Stima euristica del miglior completion ottenibile con un candidato remoto.
     *
     * Usa lo stesso modello usato dalla policy di p:
     *
     * local(p)  = (1 - p) * A
     * remote(p) = L + p * B
     */
    private double estimateBestCompletion(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
        double localOnlyTime =
                estimateLocalOnlyTime(task, sourceVehicle);

        double remoteLinearTime =
                estimateRemoteLinearTime(task, candidate);

        double baseLatency =
                Math.max(0.0, candidate.getBaseLatencySeconds());

        if (!Double.isFinite(localOnlyTime)) {
            return baseLatency + remoteLinearTime;
        }

        if (!Double.isFinite(remoteLinearTime)) {
            return localOnlyTime;
        }

        double p =
                offloadingRatioPolicy.balancedRemoteRatio(
                        task,
                        candidate,
                        sourceVehicle
                );

        double localBranch =
                (1.0 - p) * localOnlyTime;

        double remoteBranch =
                baseLatency + p * remoteLinearTime;

        return Math.max(localBranch, remoteBranch);
    }

    /**
     * Stima il tempo locale puro.
     */
    private double estimateLocalOnlyTime(
            TaskInstance task,
            VehicleSnapshot sourceVehicle
    ) {
        if (sourceVehicle == null || sourceVehicle.getLocalCpu() <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }

        return Math.max(0.0, task.getCpuCycles())
                / sourceVehicle.getLocalCpu();
    }

    /**
     * Stima upload + esecuzione remota + download per p = 1.
     */
    private double estimateRemoteLinearTime(
            TaskInstance task,
            NodeCandidate candidate
    ) {
        if (candidate.getAvailableBandwidth() <= 0.0
                || candidate.getAvailableCpu() <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }

        double upload =
                Math.max(0.0, task.getInputSizeBits())
                        / candidate.getAvailableBandwidth();

        double remoteExecution =
                Math.max(0.0, task.getCpuCycles())
                        / candidate.getAvailableCpu();

        double download =
                Math.max(0.0, task.getOutputSizeBits())
                        / candidate.getAvailableBandwidth();

        return upload + remoteExecution + download;
    }

    /**
     * Trova i candidati validi per il veicolo sorgente del task.
     */
    private List<NodeCandidate> findCandidatesForTask(
            TaskInstance task,
            SystemSnapshot snapshot
    ) {
        List<NodeCandidate> result = new ArrayList<>();

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.isValidForSourceVehicle(
                    task.getSourceVehicleId()
            )) {
                result.add(candidate);
            }
        }

        return result;
    }

    /**
     * Estrae i candidati remoti.
     */
    private List<NodeCandidate> findRemoteCandidates(
            List<NodeCandidate> candidates
    ) {
        List<NodeCandidate> result = new ArrayList<>();

        for (NodeCandidate candidate : candidates) {
            if (candidate.getType() != NodeType.LOCAL) {
                result.add(candidate);
            }
        }

        return result;
    }

    /**
     * Trova il candidato locale, se presente.
     */
    private NodeCandidate findLocalCandidate(
            List<NodeCandidate> candidates
    ) {
        for (NodeCandidate candidate : candidates) {
            if (candidate.getType() == NodeType.LOCAL) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Cerca un candidato tramite candidateId.
     */
    private NodeCandidate findCandidate(
            SystemSnapshot snapshot,
            String candidateId
    ) {
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getCandidateId().equals(candidateId)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Cerca il veicolo sorgente.
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
     * Cerca il task associato al gene.
     */
    private TaskInstance findTask(
            SystemSnapshot snapshot,
            String taskId
    ) {
        for (TaskInstance task : snapshot.getTasks()) {
            if (task.getTaskId().equals(taskId)) {
                return task;
            }
        }

        return null;
    }

    /**
     * Valida una probabilità.
     */
    private void validateRate(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    "mutationRate must be in [0, 1]."
            );
        }
    }

}