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
 * Operatore di mutazione.
 *
 * La mutazione introduce variazioni casuali nei cromosomi, così il GA mantiene
 * diversità nella popolazione e riduce il rischio di convergenza prematura.
 */
public final class MutationOperator {

    private static final double EPSILON = 1.0E-9;
    private static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05;
    private static final double MIN_RESOURCE_FRACTION = 0.05;

    private final Random random;

    /**
     * Costruisce l'operatore di mutazione.
     *
     * Parametri in ingresso:
     * - random: generatore casuale usato per decidere se e come mutare.
     *
     * Output:
     * - un MutationOperator pronto ad applicare mutazioni.
     */
    public MutationOperator(Random random) {
        // Salva il generatore casuale, impedendo che sia nullo.
        this.random = Objects.requireNonNull(random, "random must not be null.");
    }

    /**
     * Applica mutazione ai geni di un cromosoma.
     *
     * Parametri in ingresso:
     * - chromosome: cromosoma da mutare;
     * - snapshot: stato statico del sistema;
     * - mutationRate: probabilità di mutazione applicata a ogni gene.
     *
     * Output:
     * - nuovo Chromosome mutato.
     */
    public Chromosome mutate(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            double mutationRate
    ) {
        // Verifica che il mutation rate sia nell'intervallo [0, 1].
        validateRate(mutationRate);

        // Crea la lista dei geni del nuovo cromosoma.
        List<Gene> mutatedGenes = new ArrayList<>();

        // Scorre tutti i geni del cromosoma originale.
        for (Gene gene : chromosome.getGenes()) {

            // Decide casualmente se mutare il gene corrente.
            if (random.nextDouble() < mutationRate) {
                mutatedGenes.add(mutateGene(gene, snapshot));
            }

            // Se il gene non muta, viene copiato così com'è.
            else {
                mutatedGenes.add(gene);
            }
        }

        // Crea il cromosoma mutato.
        Chromosome mutated = new Chromosome(mutatedGenes);

        // Copia temporaneamente la fitness precedente, anche se verrà ricalcolata dopo.
        mutated.setFitness(chromosome.getFitness());

        // Restituisce il cromosoma mutato.
        return mutated;
    }

    /**
     * Applica una mutazione a un singolo gene.
     *
     * Parametri in ingresso:
     * - gene: gene da mutare;
     * - snapshot: stato statico del sistema.
     *
     * Output:
     * - nuovo Gene mutato.
     */
    private Gene mutateGene(Gene gene, SystemSnapshot snapshot) {
        // Cerca il task associato al gene.
        TaskInstance task = findTask(snapshot, gene.getTaskId());

        // Se il task non esiste, il gene non può essere mutato in modo sicuro.
        if (task == null) {
            return gene;
        }

        // Cerca il nodo attualmente selezionato dal gene.
        NodeCandidate node = findNode(snapshot, gene.getSelectedNodeId());

        // Con una certa probabilità cambia anche il nodo selezionato.
        if (node == null || random.nextDouble() < 0.25) {
            node = randomNode(snapshot.getCandidateNodes());
        }

        // Cerca il veicolo sorgente del task.
        VehicleSnapshot sourceVehicle = findVehicle(snapshot, task.getSourceVehicleId());

        // Se il nodo mutato è locale, forza una decisione locale coerente.
        if (node.getType() == NodeType.LOCAL) {
            double localCpu = sourceVehicle == null
                    ? 0.0
                    : Math.max(0.0, sourceVehicle.getLocalCpu());

            return new Gene(
                    task.getTaskId(),
                    node.getNodeId(),
                    0.0,
                    localCpu,
                    0.0
            );
        }

        // Muta la quota di offloading mantenendola positiva.
        double offloadingRatio = mutateRatio(gene.getOffloadingRatio());

        // Muta le risorse computazionali assegnate.
        double allocatedCpu = mutateResource(
                gene.getAllocatedCpu(),
                node.getAvailableCpu()
        );

        // Muta la banda assegnata.
        double allocatedBandwidth = mutateResource(
                gene.getAllocatedBandwidth(),
                node.getAvailableBandwidth()
        );

        // Restituisce il gene remoto mutato.
        return new Gene(
                task.getTaskId(),
                node.getNodeId(),
                offloadingRatio,
                allocatedCpu,
                allocatedBandwidth
        );
    }

    /**
     * Muta la quota di offloading.
     *
     * Parametri in ingresso:
     * - currentRatio: quota di offloading attuale.
     *
     * Output:
     * - nuova quota di offloading compresa tra MIN_REMOTE_OFFLOADING_RATIO e 1.
     */
    private double mutateRatio(double currentRatio) {
        // Se la quota attuale non è valida, parte dal minimo remoto.
        if (!Double.isFinite(currentRatio) || currentRatio <= EPSILON) {
            currentRatio = MIN_REMOTE_OFFLOADING_RATIO;
        }

        // Calcola una variazione casuale limitata.
        double delta = (random.nextDouble() - 0.5) * 0.30;

        // Applica la variazione e limita il risultato.
        return clamp(
                currentRatio + delta,
                MIN_REMOTE_OFFLOADING_RATIO,
                1.0
        );
    }

    /**
     * Muta una risorsa assegnata.
     *
     * Parametri in ingresso:
     * - currentValue: valore corrente della risorsa;
     * - maxAvailable: massimo disponibile sul nodo.
     *
     * Output:
     * - nuovo valore di risorsa entro i limiti disponibili.
     */
    private double mutateResource(double currentValue, double maxAvailable) {
        // Se non esiste risorsa disponibile, restituisce zero.
        if (!Double.isFinite(maxAvailable) || maxAvailable <= 0.0) {
            return 0.0;
        }

        // Se il valore corrente non è valido, genera una risorsa casuale.
        if (!Double.isFinite(currentValue) || currentValue <= 0.0) {
            return randomResource(maxAvailable);
        }

        // Applica una variazione moltiplicativa controllata.
        double factor = 0.75 + random.nextDouble() * 0.50;

        // Limita il nuovo valore entro le risorse disponibili.
        return clampResource(currentValue * factor, maxAvailable);
    }

    /**
     * Genera casualmente una quantità di risorsa.
     *
     * Parametri in ingresso:
     * - available: quantità massima disponibile.
     *
     * Output:
     * - valore casuale positivo se available è valido;
     * - 0 altrimenti.
     */
    private double randomResource(double available) {
        // Se la risorsa disponibile non è valida, restituisce zero.
        if (!Double.isFinite(available) || available <= 0.0) {
            return 0.0;
        }

        // Calcola una soglia minima positiva.
        double min = available * MIN_RESOURCE_FRACTION;

        // Restituisce un valore casuale tra minimo e massimo.
        return min + random.nextDouble() * (available - min);
    }

    /**
     * Limita una risorsa assegnata entro l'intervallo valido.
     *
     * Parametri in ingresso:
     * - value: valore proposto;
     * - maxAvailable: massimo disponibile.
     *
     * Output:
     * - valore compreso tra una soglia minima e maxAvailable;
     * - 0 se maxAvailable non è valido.
     */
    private double clampResource(double value, double maxAvailable) {
        // Se la risorsa massima non è valida, restituisce zero.
        if (!Double.isFinite(maxAvailable) || maxAvailable <= 0.0) {
            return 0.0;
        }

        // Calcola una quantità minima positiva.
        double min = maxAvailable * MIN_RESOURCE_FRACTION;

        // Se il valore proposto non è valido, usa il minimo.
        if (!Double.isFinite(value) || value <= 0.0) {
            return min;
        }

        // Limita il valore tra minimo e massimo disponibile.
        return clamp(value, min, maxAvailable);
    }

    /**
     * Seleziona casualmente un nodo candidato.
     *
     * Parametri in ingresso:
     * - nodes: lista dei nodi candidati.
     *
     * Output:
     * - nodo candidato scelto casualmente.
     */
    private NodeCandidate randomNode(List<NodeCandidate> nodes) {
        // Verifica che la lista dei nodi sia valida.
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "The snapshot must contain at least one candidate node."
            );
        }

        // Estrae casualmente un nodo.
        return nodes.get(random.nextInt(nodes.size()));
    }

    /**
     * Cerca un nodo nello snapshot tramite nodeId.
     *
     * Parametri in ingresso:
     * - snapshot: stato statico del sistema;
     * - nodeId: identificativo del nodo.
     *
     * Output:
     * - NodeCandidate corrispondente se trovato;
     * - null altrimenti.
     */
    private NodeCandidate findNode(SystemSnapshot snapshot, String nodeId) {
        // Cerca il nodo tramite id esatto.
        for (NodeCandidate node : snapshot.getCandidateNodes()) {
            if (node.getNodeId().equals(nodeId)) {
                return node;
            }
        }

        // Gestisce il caso testuale "LOCAL".
        if ("LOCAL".equalsIgnoreCase(nodeId) || "local".equalsIgnoreCase(nodeId)) {
            for (NodeCandidate node : snapshot.getCandidateNodes()) {
                if (node.getType() == NodeType.LOCAL) {
                    return node;
                }
            }
        }

        // Nessun nodo trovato.
        return null;
    }

    /**
     * Cerca un veicolo nello snapshot tramite vehicleId.
     *
     * Parametri in ingresso:
     * - snapshot: stato statico del sistema;
     * - vehicleId: identificativo del veicolo.
     *
     * Output:
     * - VehicleSnapshot corrispondente se trovato;
     * - null altrimenti.
     */
    private VehicleSnapshot findVehicle(SystemSnapshot snapshot, String vehicleId) {
        // Scorre tutti i veicoli osservati.
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {

            // Restituisce il veicolo se l'id coincide.
            if (vehicle.getVehicleId().equals(vehicleId)) {
                return vehicle;
            }
        }

        // Nessun veicolo trovato.
        return null;
    }

    /**
     * Cerca un task nello snapshot tramite taskId.
     *
     * Parametri in ingresso:
     * - snapshot: stato statico del sistema;
     * - taskId: identificativo del task.
     *
     * Output:
     * - TaskInstance corrispondente se trovato;
     * - null altrimenti.
     */
    private TaskInstance findTask(SystemSnapshot snapshot, String taskId) {
        // Scorre tutti i task dello snapshot.
        for (TaskInstance task : snapshot.getTasks()) {

            // Restituisce il task se l'id coincide.
            if (task.getTaskId().equals(taskId)) {
                return task;
            }
        }

        // Nessun task trovato.
        return null;
    }

    /**
     * Valida una probabilità di mutazione.
     *
     * Parametri in ingresso:
     * - value: mutation rate da verificare.
     *
     * Output:
     * - nessun valore restituito;
     * - solleva IllegalArgumentException se il valore non è in [0, 1].
     */
    private void validateRate(double value) {
        // Verifica che il valore sia finito e compreso nell'intervallo ammesso.
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("mutationRate must be in [0, 1].");
        }
    }

    /**
     * Limita un valore dentro un intervallo.
     *
     * Parametri in ingresso:
     * - value: valore da limitare;
     * - min: limite inferiore;
     * - max: limite superiore.
     *
     * Output:
     * - valore limitato nell'intervallo [min, max].
     */
    private double clamp(double value, double min, double max) {
        // Se il valore non è finito, restituisce il minimo.
        if (!Double.isFinite(value)) {
            return min;
        }

        // Applica i limiti inferiore e superiore.
        return Math.max(min, Math.min(max, value));
    }
}