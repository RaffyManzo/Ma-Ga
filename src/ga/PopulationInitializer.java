package ga;

import model.Chromosome;
import model.Gene;
import model.NodeCandidate;
import model.NodeType;
import model.SystemSnapshot;
import model.TaskInstance;
import model.VehicleSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Genera la popolazione iniziale del MA-GA.
 *
 * Nel modello source-aware, per ogni task vengono considerati solo i candidati
 * validi per il veicolo sorgente del task.
 */
public final class PopulationInitializer {

    private static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05;
    private static final double MIN_RESOURCE_FRACTION = 0.05;

    private final Random random;
    private final RepairOperator repairOperator;

    /**
     * Costruisce l'inizializzatore.
     */
    public PopulationInitializer(Random random, RepairOperator repairOperator) {
        this.random = Objects.requireNonNull(random, "random must not be null.");
        this.repairOperator = Objects.requireNonNull(
                repairOperator,
                "repairOperator must not be null."
        );
    }

    /**
     * Crea la popolazione iniziale.
     *
     * Parametri in ingresso:
     * - snapshot: stato statico del sistema;
     * - populationSize: numero di cromosomi da generare.
     *
     * Output:
     * - lista di cromosomi iniziali riparati.
     */
    public List<Chromosome> createInitialPopulation(
            SystemSnapshot snapshot,
            int populationSize
    ) {
        if (populationSize < 1) {
            throw new IllegalArgumentException("populationSize must be >= 1.");
        }

        List<Chromosome> population = new ArrayList<>();

        for (int i = 0; i < populationSize; i++) {
            Chromosome chromosome = createRandomChromosome(snapshot);
            chromosome = repairOperator.repairChromosome(chromosome, snapshot);
            population.add(chromosome);
        }

        return population;
    }

    /**
     * Crea un cromosoma casuale.
     */
    private Chromosome createRandomChromosome(SystemSnapshot snapshot) {
        List<Gene> genes = new ArrayList<>();

        for (TaskInstance task : snapshot.getTasks()) {
            genes.add(createRandomGene(task, snapshot));
        }

        return new Chromosome(genes);
    }

    /**
     * Crea un gene casuale scegliendo solo candidati validi per il veicolo sorgente.
     */
    private Gene createRandomGene(TaskInstance task, SystemSnapshot snapshot) {
        List<NodeCandidate> validCandidates = findCandidatesForTask(task, snapshot);

        if (validCandidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No execution candidates found for source vehicle "
                            + task.getSourceVehicleId()
                            + " and task "
                            + task.getTaskId()
            );
        }

        NodeCandidate candidate = validCandidates.get(random.nextInt(validCandidates.size()));
        VehicleSnapshot sourceVehicle = findVehicle(snapshot, task.getSourceVehicleId());

        if (candidate.getType() == NodeType.LOCAL) {
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

        double offloadingRatio = MIN_REMOTE_OFFLOADING_RATIO
                + random.nextDouble() * (1.0 - MIN_REMOTE_OFFLOADING_RATIO);

        double allocatedCpu = randomResource(candidate.getAvailableCpu());
        double allocatedBandwidth = randomResource(candidate.getAvailableBandwidth());

        return new Gene(
                task.getTaskId(),
                candidate.getCandidateId(),
                offloadingRatio,
                allocatedCpu,
                allocatedBandwidth
        );
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
            if (candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
                result.add(candidate);
            }
        }

        return result;
    }

    /**
     * Cerca un veicolo nello snapshot.
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
     * Genera una quantità casuale di risorsa.
     */
    private double randomResource(double available) {
        if (!Double.isFinite(available) || available <= 0.0) {
            return 0.0;
        }

        double min = available * MIN_RESOURCE_FRACTION;

        return min + random.nextDouble() * (available - min);
    }
}