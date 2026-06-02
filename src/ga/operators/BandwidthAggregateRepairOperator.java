package ga.operators;

import ga.constraints.SnapshotRepairContext;
import model.bandwidth.BandwidthPoolResolver;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ridimensiona proporzionalmente la banda aggregata per pool radio. */
public final class BandwidthAggregateRepairOperator {
    private static final double EPSILON = 1.0E-9;
    private final BandwidthPoolResolver poolResolver;

    public BandwidthAggregateRepairOperator() {
        this(new BandwidthPoolResolver());
    }

    public BandwidthAggregateRepairOperator(BandwidthPoolResolver poolResolver) {
        if (poolResolver == null) { throw new IllegalArgumentException("poolResolver must not be null."); }
        this.poolResolver = poolResolver;
    }

    public Chromosome repairChromosome(Chromosome chromosome, SystemSnapshot snapshot) {
        return repairChromosomeDetailed(chromosome, snapshot, new SnapshotRepairContext(snapshot)).getChromosome();
    }

    public BandwidthAggregateRepairResult repairChromosomeDetailed(
            Chromosome chromosome,
            SystemSnapshot snapshot,
            SnapshotRepairContext context
    ) {
        if (chromosome == null || chromosome.getGenes() == null) {
            return BandwidthAggregateRepairResult.unchanged(chromosome);
        }
        if (snapshot == null) { throw new IllegalArgumentException("snapshot must not be null."); }
        if (context == null || !context.isFor(snapshot)) {
            throw new IllegalArgumentException("context must refer to the supplied snapshot.");
        }

        Map<String, Double> usedByPool = new HashMap<>();
        Map<String, BandwidthPoolSnapshot> poolById = new HashMap<>();
        for (Gene gene : chromosome.getGenes()) {
            NodeCandidate candidate = context.getCandidateById(gene.getSelectedCandidateId());
            if (candidate == null || candidate.getType() == NodeType.LOCAL) { continue; }
            BandwidthPoolSnapshot pool = poolResolver.resolve(snapshot, candidate);
            poolById.put(pool.getPoolId(), pool);
            double value = gene.getAllocatedBandwidth();
            if (Double.isFinite(value) && value > 0.0) {
                usedByPool.merge(pool.getPoolId(), value, Double::sum);
            }
        }

        Map<String, Double> scaleByPool = new HashMap<>();
        for (Map.Entry<String, Double> entry : usedByPool.entrySet()) {
            BandwidthPoolSnapshot pool = poolById.get(entry.getKey());
            double available = pool == null ? 0.0 : pool.getAvailableBandwidth();
            double used = entry.getValue();
            if (!Double.isFinite(available) || available <= EPSILON) {
                scaleByPool.put(entry.getKey(), 0.0);
            } else if (used > available + EPSILON) {
                scaleByPool.put(entry.getKey(), available / used);
            }
        }

        if (scaleByPool.isEmpty()) {
            return BandwidthAggregateRepairResult.unchanged(chromosome);
        }

        List<Gene> repairedGenes = new ArrayList<>();
        Set<String> affectedTasks = new LinkedHashSet<>();
        for (Gene gene : chromosome.getGenes()) {
            NodeCandidate candidate = context.getCandidateById(gene.getSelectedCandidateId());
            if (candidate == null || candidate.getType() == NodeType.LOCAL) {
                repairedGenes.add(gene);
                continue;
            }
            String poolId = poolResolver.resolve(snapshot, candidate).getPoolId();
            Double factor = scaleByPool.get(poolId);
            if (factor == null) {
                repairedGenes.add(gene);
                continue;
            }
            affectedTasks.add(gene.getTaskId());
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
        return BandwidthAggregateRepairResult.changed(repaired, scaleByPool.keySet(), affectedTasks);
    }
}
