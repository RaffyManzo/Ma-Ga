package ga.operators;

import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Ripara cromosomi e geni incoerenti.
 *
 * Nel modello source-aware, un gene è valido solo se il candidato scelto
 * è compatibile con il veicolo sorgente del task.
 */
public final class RepairOperator {

    private static final double EPSILON = 1.0E-9;
    private static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05;
    private static final double MIN_RESOURCE_FRACTION = 0.05;

    /**
     * Ripara un cromosoma.
     */
    public Chromosome repairChromosome(Chromosome chromosome, SystemSnapshot snapshot) {
        List<Gene> repairedGenes = new ArrayList<>();

        for (TaskInstance task : snapshot.getTasks()) {
            Gene gene = findGene(chromosome, task.getTaskId());

            if (gene == null) {
                gene = createFallbackGene(task, snapshot);
            }

            repairedGenes.add(repairGene(gene, task, snapshot));
        }

        Chromosome repaired = new Chromosome(repairedGenes);
        repaired.setFitness(chromosome.getFitness());

        return repaired;
    }

    /**
     * Ripara un gene.
     */
    public Gene repairGene(Gene gene, TaskInstance task, SystemSnapshot snapshot) {
        NodeCandidate candidate = findCandidate(snapshot, gene.getSelectedCandidateId());

        if (candidate == null || !candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
            candidate = defaultCandidate(task, snapshot);
        }

        VehicleSnapshot sourceVehicle = findVehicle(snapshot, task.getSourceVehicleId());

        double offloadingRatio = clamp(gene.getOffloadingRatio(), 0.0, 1.0);
        double allocatedCpu = Math.max(0.0, gene.getAllocatedCpu());
        double allocatedBandwidth = Math.max(0.0, gene.getAllocatedBandwidth());

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

        if (offloadingRatio <= EPSILON) {
            offloadingRatio = MIN_REMOTE_OFFLOADING_RATIO;
        }

        allocatedCpu = clampResource(allocatedCpu, candidate.getAvailableCpu());
        allocatedBandwidth = clampResource(allocatedBandwidth, candidate.getAvailableBandwidth());

        return new Gene(
                task.getTaskId(),
                candidate.getCandidateId(),
                offloadingRatio,
                allocatedCpu,
                allocatedBandwidth
        );
    }

    /**
     * Crea un gene di fallback.
     */
    private Gene createFallbackGene(TaskInstance task, SystemSnapshot snapshot) {
        NodeCandidate candidate = defaultCandidate(task, snapshot);
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

        return new Gene(
                task.getTaskId(),
                candidate.getCandidateId(),
                MIN_REMOTE_OFFLOADING_RATIO,
                clampResource(0.0, candidate.getAvailableCpu()),
                clampResource(0.0, candidate.getAvailableBandwidth())
        );
    }

    /**
     * Sceglie il candidato di default per un task.
     *
     * Preferisce LOCAL del veicolo sorgente, se presente.
     */
    private NodeCandidate defaultCandidate(TaskInstance task, SystemSnapshot snapshot) {
        List<NodeCandidate> validCandidates = findCandidatesForTask(task, snapshot);

        for (NodeCandidate candidate : validCandidates) {
            if (candidate.getType() == NodeType.LOCAL) {
                return candidate;
            }
        }

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
     * Limita una risorsa.
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

