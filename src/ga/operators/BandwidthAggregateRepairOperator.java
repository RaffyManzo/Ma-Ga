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
 * Ripara l'allocazione aggregata della banda sui link source-aware già
 * modellati nel prototipo.
 *
 * <p>Il repair gene-level limita già la banda del singolo gene rispetto al
 * candidato selezionato. Questo operatore controlla invece la somma delle
 * bande assegnate allo stesso {@code candidateId}. Il raggruppamento per
 * candidateId è volutamente temporaneo: rappresenta la semantica corrente e
 * chiude il bug operativo della issue #18 senza anticipare la modellazione dei
 * pool radio.</p>
 */
public final class BandwidthAggregateRepairOperator {
    private static final double EPSILON = 1.0E-9;

    /** Adapter compatibile con chiamanti esterni e test manuali. */
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
     * Ridimensiona proporzionalmente la banda dei geni remoti quando la somma
     * assegnata allo stesso candidateId supera la banda disponibile.
     *
     * <p>L'esito dettagliato consente al chiamante di rivalutare soltanto i
     * task modificati. La rivalutazione è necessaria perché una banda ridotta
     * può peggiorare la latenza e la sostenibilità rispetto alla deadline.</p>
     */
    public BandwidthAggregateRepairResult repairChromosomeDetailed(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            SnapshotRepairContext context
    ) {
        if (chromosome == null || chromosome.getGenes() == null) {
            return BandwidthAggregateRepairResult.unchanged(chromosome);
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null.");
        }
        if (context == null || !context.isFor(snapshot)) {
            throw new IllegalArgumentException(
                    "context must refer to the supplied snapshot."
            );
        }

        Map<String, Double> usedBandwidthByCandidate =
                computeUsedBandwidthByCandidate(chromosome, context);
        Map<String, Double> scaleFactorByCandidate =
                computeScaleFactorByCandidate(
                        usedBandwidthByCandidate,
                        context
                );

        if (scaleFactorByCandidate.isEmpty()) {
            return BandwidthAggregateRepairResult.unchanged(chromosome);
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

            Double factor = scaleFactorByCandidate.get(candidate.getCandidateId());
            if (factor == null) {
                repairedGenes.add(gene);
                continue;
            }

            affectedTaskIds.add(gene.getTaskId());
            repairedGenes.add(new Gene(
                    gene.getTaskId(),
                    gene.getSelectedCandidateId(),
                    gene.getOffloadingRatio(),
                    gene.getAllocatedCpu(),
                    gene.getAllocatedBandwidth() * factor
            ));
        }

        Chromosome repaired = new Chromosome(repairedGenes);
        repaired.setFitness(chromosome.getFitness());
        return BandwidthAggregateRepairResult.changed(
                repaired,
                scaleFactorByCandidate.keySet(),
                affectedTaskIds
        );
    }

    /** Calcola la banda totale richiesta per candidateId. */
    private Map<String, Double> computeUsedBandwidthByCandidate(
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
            double allocatedBandwidth = gene.getAllocatedBandwidth();
            if (!Double.isFinite(allocatedBandwidth)
                    || allocatedBandwidth <= 0.0) {
                continue;
            }
            result.merge(
                    candidate.getCandidateId(),
                    allocatedBandwidth,
                    Double::sum
            );
        }
        return result;
    }

    /** Calcola il fattore di riduzione per i candidateId sovra-allocati. */
    private Map<String, Double> computeScaleFactorByCandidate(
            Map<String, Double> usedBandwidthByCandidate,
            SnapshotRepairContext context
    ) {
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Double> entry
                : usedBandwidthByCandidate.entrySet()) {
            String candidateId = entry.getKey();
            double usedBandwidth = entry.getValue();
            NodeCandidate candidate = context.getCandidateById(candidateId);
            double availableBandwidth = candidate == null
                    ? 0.0
                    : candidate.getAvailableBandwidth();

            if (!Double.isFinite(availableBandwidth)
                    || availableBandwidth <= EPSILON) {
                result.put(candidateId, 0.0);
                continue;
            }
            if (usedBandwidth > availableBandwidth + EPSILON) {
                result.put(candidateId, availableBandwidth / usedBandwidth);
            }
        }
        return result;
    }
}
