package ga.operators;

import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ripara l'allocazione CPU aggregata sui nodi fisici remoti.
 *
 * Il RepairOperator già limita la CPU di un singolo gene rispetto al candidato
 * scelto. Questo operatore lavora a livello di cromosoma e controlla invece
 * la somma delle CPU assegnate allo stesso executionNodeId.
 *
 * La banda non viene modificata: il repair della banda resta una OpenIssue.
 */
public final class CpuAggregateRepairOperator {

    private static final double EPSILON = 1.0E-9;

    /**
     * Ridimensiona proporzionalmente la CPU dei geni remoti quando la somma
     * assegnata a uno stesso nodo fisico supera la CPU disponibile.
     *
     * @param chromosome cromosoma già riparato a livello di singolo gene
     * @param snapshot snapshot corrente
     * @return cromosoma con CPU aggregate coerenti
     */
    public Chromosome repairChromosome(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        if (chromosome == null || chromosome.getGenes() == null) {
            return chromosome;
        }

        Map<String, NodeCandidate> candidateById = indexCandidates(snapshot);
        Map<String, Double> availableCpuByExecutionNode =
                buildAvailableCpuByExecutionNode(snapshot);

        Map<String, Double> usedCpuByExecutionNode = computeUsedCpuByExecutionNode(
                chromosome,
                candidateById
        );

        Map<String, Double> scaleFactorByExecutionNode =
                computeScaleFactorByExecutionNode(
                        usedCpuByExecutionNode,
                        availableCpuByExecutionNode
                );

        if (scaleFactorByExecutionNode.isEmpty()) {
            return chromosome;
        }

        List<Gene> repairedGenes = new ArrayList<>();

        for (Gene gene : chromosome.getGenes()) {
            NodeCandidate candidate = candidateById.get(
                    gene.getSelectedCandidateId()
            );

            if (candidate == null || candidate.getType() == NodeType.LOCAL) {
                repairedGenes.add(gene);
                continue;
            }

            Double factor = scaleFactorByExecutionNode.get(
                    candidate.getExecutionNodeId()
            );

            if (factor == null) {
                repairedGenes.add(gene);
                continue;
            }

            repairedGenes.add(
                    new Gene(
                            gene.getTaskId(),
                            gene.getSelectedCandidateId(),
                            gene.getOffloadingRatio(),
                            gene.getAllocatedCpu() * factor,
                            gene.getAllocatedBandwidth()
                    )
            );
        }

        Chromosome repaired = new Chromosome(repairedGenes);
        repaired.setFitness(chromosome.getFitness());

        return repaired;
    }

    /**
     * Indicizza i candidati tramite candidateId.
     */
    private Map<String, NodeCandidate> indexCandidates(SystemSnapshot snapshot) {
        Map<String, NodeCandidate> result = new HashMap<>();

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            result.put(candidate.getCandidateId(), candidate);
        }

        return result;
    }

    /**
     * Costruisce la capacità CPU disponibile per ogni nodo fisico remoto.
     *
     * LOCAL viene escluso perché la CPU locale viene trattata separatamente
     * dalla fitness attuale.
     */
    private Map<String, Double> buildAvailableCpuByExecutionNode(
            SystemSnapshot snapshot
    ) {
        Map<String, Double> result = new HashMap<>();

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getType() == NodeType.LOCAL) {
                continue;
            }

            result.putIfAbsent(
                    candidate.getExecutionNodeId(),
                    candidate.getAvailableCpu()
            );
        }

        return result;
    }

    /**
     * Calcola la CPU remota totale richiesta da ogni nodo fisico.
     */
    private Map<String, Double> computeUsedCpuByExecutionNode(
            Chromosome chromosome,
            Map<String, NodeCandidate> candidateById
    ) {
        Map<String, Double> result = new HashMap<>();

        for (Gene gene : chromosome.getGenes()) {
            NodeCandidate candidate = candidateById.get(
                    gene.getSelectedCandidateId()
            );

            if (candidate == null || candidate.getType() == NodeType.LOCAL) {
                continue;
            }

            double allocatedCpu = gene.getAllocatedCpu();

            if (!Double.isFinite(allocatedCpu) || allocatedCpu <= 0.0) {
                continue;
            }

            result.merge(
                    candidate.getExecutionNodeId(),
                    allocatedCpu,
                    Double::sum
            );
        }

        return result;
    }

    /**
     * Calcola il fattore di riduzione per i nodi sovra-allocati.
     */
    private Map<String, Double> computeScaleFactorByExecutionNode(
            Map<String, Double> usedCpuByExecutionNode,
            Map<String, Double> availableCpuByExecutionNode
    ) {
        Map<String, Double> result = new HashMap<>();

        for (Map.Entry<String, Double> entry
                : usedCpuByExecutionNode.entrySet()) {
            String executionNodeId = entry.getKey();
            double usedCpu = entry.getValue();

            double availableCpu = availableCpuByExecutionNode.getOrDefault(
                    executionNodeId,
                    0.0
            );

            if (!Double.isFinite(availableCpu) || availableCpu <= EPSILON) {
                result.put(executionNodeId, 0.0);
                continue;
            }

            if (usedCpu > availableCpu + EPSILON) {
                result.put(executionNodeId, availableCpu / usedCpu);
            }
        }

        return result;
    }
}
