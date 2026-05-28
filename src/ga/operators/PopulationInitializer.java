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
 * Genera la popolazione iniziale del MA-GA.
 *
 * Nel modello source-aware, per ogni task vengono considerati solo i candidati
 * validi per il veicolo sorgente del task.
 *
 * La popolazione iniziale non è più solo casuale.
 * Vengono generati cromosomi con profili diversi:
 *
 * - RANDOM: esplorazione casuale classica;
 * - LOCAL_BIASED: soluzione prevalentemente locale;
 * - BALANCED_REMOTE: candidati remoti con quota p bilanciata;
 * - FULL_REMOTE_TRIAL: candidati remoti con p = 1.
 *
 * La selezione finale resta affidata alla fitness.
 * Questi profili servono solo a rendere lo spazio iniziale più ricco.
 */
public final class PopulationInitializer {

    private static final double MIN_RESOURCE_FRACTION = 0.05;

    private final Random random;
    private final RepairOperator repairOperator;
    private final OffloadingRatioPolicy offloadingRatioPolicy;

    /**
     * Costruisce l'inizializzatore.
     *
     * @param random generatore casuale condiviso dal GA
     * @param repairOperator operatore di riparazione dei cromosomi
     */
    public PopulationInitializer(
            Random random,
            RepairOperator repairOperator
    ) {
        this.random = Objects.requireNonNull(
                random,
                "random must not be null."
        );

        this.repairOperator = Objects.requireNonNull(
                repairOperator,
                "repairOperator must not be null."
        );

        this.offloadingRatioPolicy = new OffloadingRatioPolicy();
    }

    /**
     * Crea la popolazione iniziale.
     *
     * La popolazione viene composta alternando profili diversi.
     * Ogni cromosoma viene poi riparato, così restano validi:
     *
     * - candidati source-aware;
     * - risorse minime;
     * - CPU aggregata per executionNodeId.
     *
     * @param snapshot stato osservato del sistema
     * @param populationSize numero di cromosomi da generare
     * @return popolazione iniziale riparata
     */
    public List<Chromosome> createInitialPopulation(
            SystemSnapshot snapshot,
            int populationSize
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        if (populationSize < 1) {
            throw new IllegalArgumentException(
                    "populationSize must be >= 1."
            );
        }

        List<Chromosome> population = new ArrayList<>();

        for (int i = 0; i < populationSize; i++) {
            InitializationProfile profile =
                    selectProfile(i, populationSize);

            Chromosome chromosome = createChromosome(
                    snapshot,
                    profile
            );

            chromosome = repairOperator.repairChromosome(
                    chromosome,
                    snapshot
            );

            population.add(chromosome);
        }

        return population;
    }

    /**
     * Seleziona il profilo di inizializzazione per un cromosoma.
     *
     * La distribuzione è volutamente semplice:
     *
     * - circa 10% LOCAL_BIASED;
     * - circa 35% BALANCED_REMOTE;
     * - circa 20% FULL_REMOTE_TRIAL;
     * - il resto RANDOM.
     *
     * RANDOM resta la quota più libera e preserva diversità.
     */
    private InitializationProfile selectProfile(
            int index,
            int populationSize
    ) {
        double position = (double) index / populationSize;

        if (position < 0.10) {
            return InitializationProfile.LOCAL_BIASED;
        }

        if (position < 0.45) {
            return InitializationProfile.BALANCED_REMOTE;
        }

        if (position < 0.65) {
            return InitializationProfile.FULL_REMOTE_TRIAL;
        }

        return InitializationProfile.RANDOM;
    }

    /**
     * Crea un cromosoma secondo il profilo scelto.
     */
    private Chromosome createChromosome(
            SystemSnapshot snapshot,
            InitializationProfile profile
    ) {
        List<Gene> genes = new ArrayList<>();

        for (TaskInstance task : snapshot.getTasks()) {
            genes.add(
                    createGene(
                            task,
                            snapshot,
                            profile
                    )
            );
        }

        return new Chromosome(genes);
    }

    /**
     * Crea un gene secondo il profilo scelto.
     */
    private Gene createGene(
            TaskInstance task,
            SystemSnapshot snapshot,
            InitializationProfile profile
    ) {
        List<NodeCandidate> validCandidates =
                findCandidatesForTask(task, snapshot);

        if (validCandidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No execution candidates found for source vehicle "
                            + task.getSourceVehicleId()
                            + " and task "
                            + task.getTaskId()
            );
        }

        VehicleSnapshot sourceVehicle = findVehicle(
                snapshot,
                task.getSourceVehicleId()
        );

        return switch (profile) {
            case LOCAL_BIASED -> createLocalBiasedGene(
                    task,
                    validCandidates,
                    sourceVehicle
            );

            case BALANCED_REMOTE -> createBalancedRemoteGene(
                    task,
                    validCandidates,
                    sourceVehicle
            );

            case FULL_REMOTE_TRIAL -> createFullRemoteTrialGene(
                    task,
                    validCandidates,
                    sourceVehicle
            );

            case RANDOM -> createRandomGene(
                    task,
                    validCandidates,
                    sourceVehicle
            );
        };
    }

    /**
     * Crea un gene orientato al locale.
     *
     * Se esiste il candidato LOCAL, viene scelto.
     * Altrimenti si ricade su un candidato casuale valido.
     */
    private Gene createLocalBiasedGene(
            TaskInstance task,
            List<NodeCandidate> validCandidates,
            VehicleSnapshot sourceVehicle
    ) {
        NodeCandidate localCandidate =
                findLocalCandidate(validCandidates);

        if (localCandidate != null) {
            return createLocalGene(
                    task,
                    localCandidate,
                    sourceVehicle
            );
        }

        return createRandomGene(
                task,
                validCandidates,
                sourceVehicle
        );
    }

    /**
     * Crea un gene remoto con quota p bilanciata.
     *
     * Se non esistono candidati remoti, usa LOCAL.
     */
    private Gene createBalancedRemoteGene(
            TaskInstance task,
            List<NodeCandidate> validCandidates,
            VehicleSnapshot sourceVehicle
    ) {
        List<NodeCandidate> remoteCandidates =
                findRemoteCandidates(validCandidates);

        if (remoteCandidates.isEmpty()) {
            return createLocalBiasedGene(
                    task,
                    validCandidates,
                    sourceVehicle
            );
        }

        NodeCandidate candidate =
                selectCandidateWithBestEstimatedCompletion(
                        task,
                        remoteCandidates,
                        sourceVehicle
                );

        double offloadingRatio =
                offloadingRatioPolicy.balancedRemoteRatio(
                        task,
                        candidate,
                        sourceVehicle
                );

        return createRemoteGene(
                task,
                candidate,
                offloadingRatio
        );
    }

    /**
     * Crea un gene remoto con p = 1.
     *
     * Serve a rendere il full offloading esplicitamente presente
     * nella popolazione iniziale.
     */
    private Gene createFullRemoteTrialGene(
            TaskInstance task,
            List<NodeCandidate> validCandidates,
            VehicleSnapshot sourceVehicle
    ) {
        List<NodeCandidate> remoteCandidates =
                findRemoteCandidates(validCandidates);

        if (remoteCandidates.isEmpty()) {
            return createLocalBiasedGene(
                    task,
                    validCandidates,
                    sourceVehicle
            );
        }

        NodeCandidate candidate =
                selectCandidateWithBestEstimatedCompletion(
                        task,
                        remoteCandidates,
                        sourceVehicle
                );

        return createRemoteGene(
                task,
                candidate,
                offloadingRatioPolicy.fullRatio()
        );
    }

    /**
     * Crea un gene casuale.
     *
     * Questo mantiene la componente classica di esplorazione casuale
     * dell'algoritmo genetico.
     */
    private Gene createRandomGene(
            TaskInstance task,
            List<NodeCandidate> validCandidates,
            VehicleSnapshot sourceVehicle
    ) {
        NodeCandidate candidate =
                validCandidates.get(
                        random.nextInt(validCandidates.size())
                );

        if (candidate.getType() == NodeType.LOCAL) {
            return createLocalGene(
                    task,
                    candidate,
                    sourceVehicle
            );
        }

        double offloadingRatio =
                offloadingRatioPolicy.randomRemoteRatio(random);

        return createRemoteGene(
                task,
                candidate,
                offloadingRatio
        );
    }

    /**
     * Crea un gene locale.
     *
     * La quota di offloading è sempre 0 e la banda assegnata è 0.
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
     * Crea un gene remoto.
     *
     * CPU e banda vengono inizializzate in modo casuale entro i limiti
     * del candidato. La quota p viene invece scelta dal profilo.
     */
    private Gene createRemoteGene(
            TaskInstance task,
            NodeCandidate candidate,
            double offloadingRatio
    ) {
        return new Gene(
                task.getTaskId(),
                candidate.getCandidateId(),
                offloadingRatio,
                randomResource(candidate.getAvailableCpu()),
                randomResource(candidate.getAvailableBandwidth())
        );
    }

    /**
     * Sceglie il candidato remoto con migliore stima ottimistica.
     *
     * Non è una valutazione di fitness.
     * Serve solo per evitare che i profili BALANCED e FULL partano
     * da candidati remoti palesemente peggiori.
     */
    private NodeCandidate selectCandidateWithBestEstimatedCompletion(
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
     * Stima il miglior completion teorico dato un candidato remoto.
     *
     * Usa la stessa idea della quota bilanciata:
     *
     * local(p)  = (1 - p) * A
     * remote(p) = L + p * B
     *
     * dove A è il locale puro, B è il remoto lineare e L è la latenza base.
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
     * Stima upload + remote execution + download per p = 1.
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
     * Trova i candidati validi per il task.
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
     * Restituisce il candidato LOCAL, se presente.
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
     * Restituisce solo i candidati remoti.
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
     * Cerca il veicolo sorgente nello snapshot.
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
     * Genera una quantità casuale di risorsa.
     *
     * La risorsa è almeno una piccola frazione della disponibilità,
     * così il gene remoto non parte con CPU o banda praticamente nulle.
     */
    private double randomResource(double available) {
        if (!Double.isFinite(available) || available <= 0.0) {
            return 0.0;
        }

        double min = available * MIN_RESOURCE_FRACTION;

        return min + random.nextDouble() * (available - min);
    }

    /**
     * Profili di inizializzazione della popolazione.
     */
    private enum InitializationProfile {

        /**
         * Preferisce esecuzione locale.
         */
        LOCAL_BIASED,

        /**
         * Usa candidati remoti con quota p bilanciata.
         */
        BALANCED_REMOTE,

        /**
         * Usa candidati remoti con p = 1.
         */
        FULL_REMOTE_TRIAL,

        /**
         * Mantiene la generazione casuale classica.
         */
        RANDOM
    }
}