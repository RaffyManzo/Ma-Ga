package ga.operators;

import ga.constraints.SnapshotRepairContext;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ripara l'allocazione CPU aggregata sui nodi fisici remoti.
 *
 * <p>Il RepairOperator limita già la CPU del singolo gene rispetto al candidato
 * scelto. Questo operatore controlla invece la somma delle CPU assegnate allo
 * stesso {@code executionNodeId}.</p>
 *
 * <p>La banda viene riparata da operatori dedicati, eseguiti dopo il repair
 * CPU nel {@link RepairOperator}.</p>
 */
public final class CpuAggregateRepairOperator {
    private static final double EPSILON = 1.0E-9;

    /**
     * Adapter compatibile con i chiamanti precedenti.
     */
    public Chromosome repairChromosome(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        return repairChromosomeDetailed(
                chromosome,
                snapshot,
                new SnapshotRepairContext(snapshot)
        ).getChromosome();
    }

    /**
     * Ridimensiona proporzionalmente la CPU dei geni remoti quando la somma
     * assegnata allo stesso nodo fisico supera la CPU disponibile.
     *
     * <p>L'esito dettagliato permette al chiamante di ripetere il repair
     * gene-level soltanto sui task effettivamente modificati.</p>
     */
    public CpuAggregateRepairResult repairChromosomeDetailed(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            SnapshotRepairContext context
    ) {
        if (chromosome == null || chromosome.getGenes() == null) {
            return CpuAggregateRepairResult.unchanged(chromosome);
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null.");
        }
        if (context == null || !context.isFor(snapshot)) {
            throw new IllegalArgumentException(
                    "context must refer to the supplied snapshot."
            );
        }

        Map<String, Double> availableCpuByExecutionNode =
                context.getAvailableCpuByExecutionNodeId();
        Map<String, Double> usedCpuByExecutionNode =
                computeUsedCpuByExecutionNode(chromosome, context);
        Map<String, Double> scaleFactorByExecutionNode =
                computeScaleFactorByExecutionNode(
                        usedCpuByExecutionNode,
                        availableCpuByExecutionNode
                );

        if (scaleFactorByExecutionNode.isEmpty()) {
            return CpuAggregateRepairResult.unchanged(chromosome);
        }

        List<Gene> repairedGenes = new ArrayList<>();
        Set<String> affectedTaskIds = new LinkedHashSet<>();
        for (Gene gene : chromosome.getGenes()) {
            NodeCandidate candidate = context.getCandidateById(
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

            affectedTaskIds.add(gene.getTaskId());
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
        return CpuAggregateRepairResult.changed(
                repaired,
                scaleFactorByExecutionNode.keySet(),
                affectedTaskIds
        );
    }

    /** Calcola la CPU remota totale richiesta da ogni nodo fisico. */
    private Map<String, Double> computeUsedCpuByExecutionNode(
            Chromosome chromosome,
            SnapshotRepairContext context
    ) {
        Map<String, Double> result = new HashMap<>();
        for (Gene gene : chromosome.getGenes()) {
            NodeCandidate candidate = context.getCandidateById(
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

    /** Calcola il fattore di riduzione per i nodi sovra-allocati. */
    private Map<String, Double> computeScaleFactorByExecutionNode(
            Map<String, Double> usedCpuByExecutionNode,
            Map<String, Double> availableCpuByExecutionNode
    ) {
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Double> entry : usedCpuByExecutionNode.entrySet()) {
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
