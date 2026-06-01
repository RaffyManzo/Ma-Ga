package ga.operators;

import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.offloading.OffloadingTimeModel;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Operatore di mutazione del MA-GA.
 *
 * <p>La mutazione mantiene la componente casuale dell'algoritmo genetico, ma
 * non agisce più solo con piccole variazioni locali.</p>
 *
 * <p>Per la quota di offloading {@code p_i} usa più modalità:</p>
 * <ul>
 *   <li>piccola perturbazione locale;</li>
 *   <li>reset casuale;</li>
 *   <li>salto verso {@code p = 1};</li>
 *   <li>salto verso una quota bilanciata tra ramo locale e ramo remoto.</li>
 * </ul>
 *
 * <p>Inoltre, quando muta il candidato, sceglie solo candidati validi per il
 * veicolo sorgente del task.</p>
 */
public final class MutationOperator {
    /** Probabilità interna di cambiare candidato quando un gene muta. */
    private static final double CANDIDATE_MUTATION_PROBABILITY = 0.25;

    /** Probabilità di preferire candidati remoti quando cambia candidato. */
    private static final double REMOTE_CANDIDATE_PREFERENCE = 0.60;

    /**
     * Probabilità di scegliere il candidato remoto con migliore stima
     * euristica invece di un remoto casuale.
     */
    private static final double BEST_REMOTE_CANDIDATE_PROBABILITY = 0.55;

    private final Random random;
    private final OffloadingRatioPolicy offloadingRatioPolicy;
    private final ResourceAllocationPolicy resourceAllocationPolicy;
    private final OffloadingTimeModel offloadingTimeModel;

    /** Costruisce l'operatore di mutazione. */
    public MutationOperator(Random random) {
        this.random = Objects.requireNonNull(
                random,
                "random must not be null."
        );
        this.offloadingRatioPolicy = new OffloadingRatioPolicy();
        this.resourceAllocationPolicy = new ResourceAllocationPolicy();
        this.offloadingTimeModel = new OffloadingTimeModel();
    }

    /**
     * Adapter compatibile con i chiamanti precedenti.
     *
     * @return cromosoma mutato
     */
    public Chromosome mutate(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            double mutationRate
    ) {
        return mutateDetailed(chromosome, snapshot, mutationRate).getChromosome();
    }

    /**
     * Applica la mutazione e conserva gli identificativi dei task modificati.
     *
     * <p>Ogni gene viene selezionato con probabilità {@code mutationRate}. Un
     * task viene marcato dirty soltanto se la decisione risultante differisce
     * realmente da quella precedente. Il tracking non cambia la probabilità
     * o la logica della mutazione: espone soltanto informazione già disponibile
     * per evitare repair ridondanti.</p>
     */
    public MutationResult mutateDetailed(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            double mutationRate
    ) {
        Objects.requireNonNull(chromosome, "chromosome must not be null.");
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        validateRate(mutationRate);

        List<Gene> mutatedGenes = new ArrayList<>();
        Set<String> mutatedTaskIds = new LinkedHashSet<>();
        for (Gene gene : chromosome.getGenes()) {
            Gene resultingGene = gene;
            if (random.nextDouble() < mutationRate) {
                resultingGene = mutateGene(gene, snapshot);
            }
            mutatedGenes.add(resultingGene);
            if (!sameDecision(gene, resultingGene)) {
                mutatedTaskIds.add(resultingGene.getTaskId());
            }
        }

        Chromosome mutated = new Chromosome(mutatedGenes);
        mutated.setFitness(chromosome.getFitness());
        return new MutationResult(mutated, mutatedTaskIds);
    }

    /** Muta un singolo gene. */
    private Gene mutateGene(Gene gene, SystemSnapshot snapshot) {
        TaskInstance task = findTask(snapshot, gene.getTaskId());
        if (task == null) {
            return gene;
        }

        VehicleSnapshot sourceVehicle = findVehicle(
                snapshot,
                task.getSourceVehicleId()
        );
        List<NodeCandidate> validCandidates = findCandidatesForTask(task, snapshot);
        if (validCandidates.isEmpty()) {
            return gene;
        }

        NodeCandidate currentCandidate = findCandidate(
                snapshot,
                gene.getSelectedCandidateId()
        );
        boolean candidateChanged = currentCandidate == null
                || !currentCandidate.isValidForSourceVehicle(
                        task.getSourceVehicleId()
                )
                || random.nextDouble() < CANDIDATE_MUTATION_PROBABILITY;

        NodeCandidate selectedCandidate = candidateChanged
                ? selectCandidateForMutation(task, validCandidates, sourceVehicle)
                : currentCandidate;

        if (selectedCandidate.getType() == NodeType.LOCAL) {
            return createLocalGene(task, selectedCandidate, sourceVehicle);
        }

        double offloadingRatio = mutateOffloadingRatio(
                gene,
                task,
                selectedCandidate,
                sourceVehicle,
                candidateChanged
        );
        ResourceAllocationDecision allocation = resourceAllocationPolicy.mutate(
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

    /** Sceglie un candidato valido per la mutazione. */
    private NodeCandidate selectCandidateForMutation(
            TaskInstance task,
            List<NodeCandidate> validCandidates,
            VehicleSnapshot sourceVehicle
    ) {
        List<NodeCandidate> remoteCandidates = findRemoteCandidates(validCandidates);
        NodeCandidate localCandidate = findLocalCandidate(validCandidates);

        if (!remoteCandidates.isEmpty()
                && random.nextDouble() < REMOTE_CANDIDATE_PREFERENCE) {
            if (random.nextDouble() < BEST_REMOTE_CANDIDATE_PROBABILITY) {
                return selectBestEstimatedRemoteCandidate(
                        task,
                        remoteCandidates,
                        sourceVehicle
                );
            }
            return remoteCandidates.get(random.nextInt(remoteCandidates.size()));
        }

        if (localCandidate != null && random.nextDouble() < 0.50) {
            return localCandidate;
        }
        return validCandidates.get(random.nextInt(validCandidates.size()));
    }

    /** Muta la quota di offloading {@code p_i}. */
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

    /** Crea un gene locale coerente. */
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

    /** Sceglie il candidato remoto con migliore stima euristica. */
    private NodeCandidate selectBestEstimatedRemoteCandidate(
            TaskInstance task,
            List<NodeCandidate> remoteCandidates,
            VehicleSnapshot sourceVehicle
    ) {
        return remoteCandidates
                .stream()
                .min(
                        Comparator.comparingDouble(
                                candidate -> estimateBestCompletion(
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

    /** Stima euristica del miglior completion ottenibile da un remoto. */
    private double estimateBestCompletion(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
        double localOnlyTime = estimateLocalOnlyTime(task, sourceVehicle);
        double remoteLinearTime = estimateRemoteLinearTime(task, candidate);
        double baseLatency = Math.max(0.0, candidate.getBaseLatencySeconds());

        if (!Double.isFinite(localOnlyTime)) {
            return baseLatency + remoteLinearTime;
        }
        if (!Double.isFinite(remoteLinearTime)) {
            return localOnlyTime;
        }

        double p = offloadingRatioPolicy.balancedRemoteRatio(
                task,
                candidate,
                sourceVehicle
        );
        double localBranch = (1.0 - p) * localOnlyTime;
        double remoteBranch = baseLatency + p * remoteLinearTime;
        return Math.max(localBranch, remoteBranch);
    }

    private double estimateLocalOnlyTime(
            TaskInstance task,
            VehicleSnapshot sourceVehicle
    ) {
        return offloadingTimeModel.estimateLocalOnlyTime(task, sourceVehicle);
    }

    private double estimateRemoteLinearTime(
            TaskInstance task,
            NodeCandidate candidate
    ) {
        return offloadingTimeModel.estimateRemoteLinearTime(task, candidate);
    }

    /** Trova i candidati validi per il veicolo sorgente del task. */
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

    private List<NodeCandidate> findRemoteCandidates(List<NodeCandidate> candidates) {
        List<NodeCandidate> result = new ArrayList<>();
        for (NodeCandidate candidate : candidates) {
            if (candidate.getType() != NodeType.LOCAL) {
                result.add(candidate);
            }
        }
        return result;
    }

    private NodeCandidate findLocalCandidate(List<NodeCandidate> candidates) {
        for (NodeCandidate candidate : candidates) {
            if (candidate.getType() == NodeType.LOCAL) {
                return candidate;
            }
        }
        return null;
    }

    private NodeCandidate findCandidate(SystemSnapshot snapshot, String candidateId) {
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getCandidateId().equals(candidateId)) {
                return candidate;
            }
        }
        return null;
    }

    private VehicleSnapshot findVehicle(SystemSnapshot snapshot, String vehicleId) {
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            if (vehicle.getVehicleId().equals(vehicleId)) {
                return vehicle;
            }
        }
        return null;
    }

    private TaskInstance findTask(SystemSnapshot snapshot, String taskId) {
        for (TaskInstance task : snapshot.getTasks()) {
            if (task.getTaskId().equals(taskId)) {
                return task;
            }
        }
        return null;
    }

    /** Confronta semanticamente due geni immutabili. */
    private boolean sameDecision(Gene first, Gene second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return Objects.equals(first.getTaskId(), second.getTaskId())
                && Objects.equals(
                        first.getSelectedCandidateId(),
                        second.getSelectedCandidateId()
                )
                && Double.compare(
                        first.getOffloadingRatio(),
                        second.getOffloadingRatio()
                ) == 0
                && Double.compare(
                        first.getAllocatedCpu(),
                        second.getAllocatedCpu()
                ) == 0
                && Double.compare(
                        first.getAllocatedBandwidth(),
                        second.getAllocatedBandwidth()
                ) == 0;
    }

    private void validateRate(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    "mutationRate must be in [0, 1]."
            );
        }
    }
}
