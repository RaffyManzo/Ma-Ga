package ga.fitness.breakdown;

import java.util.List;

/**
 * Risultato dettagliato della valutazione di un cromosoma.
 *
 * Contiene la fitness finale, i contributi normalizzati e i breakdown
 * necessari per report, debug e analisi della soluzione.
 */
public final class EvaluationBreakdown {

    private final double fitness;
    private final double completionTimeSeconds;
    private final double communicationLatencySeconds;
    private final double mobilityPenalty;
    private final double resourcePenalty;

    private final double normalizedCompletionTime;
    private final double normalizedCommunicationLatency;
    private final double normalizedMobilityPenalty;
    private final double normalizedResourcePenalty;

    private final List<GeneEvaluationBreakdown> geneBreakdowns;
    private final List<ExecutionNodeResourceUsageBreakdown> executionNodeResourceUsageBreakdowns;
    private final List<LinkBandwidthUsageBreakdown> linkBandwidthUsageBreakdowns;
    private final List<LocalResourceUsageBreakdown> localResourceUsageBreakdowns;

    /**
     * Crea il breakdown globale della fitness.
     *
     * @param fitness valore finale della fitness
     * @param completionTimeSeconds tempo di completamento del cromosoma
     * @param communicationLatencySeconds latenza comunicativa media
     * @param mobilityPenalty penalità di mobilità
     * @param resourcePenalty penalità di risorse e vincoli
     * @param normalizedCompletionTime tempo normalizzato
     * @param normalizedCommunicationLatency latenza normalizzata
     * @param normalizedMobilityPenalty penalità mobilità normalizzata
     * @param normalizedResourcePenalty penalità risorse normalizzata
     * @param geneBreakdowns breakdown dei singoli geni
     * @param executionNodeResourceUsageBreakdowns uso CPU per nodo fisico
     * @param linkBandwidthUsageBreakdowns uso banda per candidato/link
     * @param localResourceUsageBreakdowns uso locale per veicolo
     */
    public EvaluationBreakdown(
            double fitness,
            double completionTimeSeconds,
            double communicationLatencySeconds,
            double mobilityPenalty,
            double resourcePenalty,
            double normalizedCompletionTime,
            double normalizedCommunicationLatency,
            double normalizedMobilityPenalty,
            double normalizedResourcePenalty,
            List<GeneEvaluationBreakdown> geneBreakdowns,
            List<ExecutionNodeResourceUsageBreakdown> executionNodeResourceUsageBreakdowns,
            List<LinkBandwidthUsageBreakdown> linkBandwidthUsageBreakdowns,
            List<LocalResourceUsageBreakdown> localResourceUsageBreakdowns
    ) {
        this.fitness = fitness;
        this.completionTimeSeconds = completionTimeSeconds;
        this.communicationLatencySeconds = communicationLatencySeconds;
        this.mobilityPenalty = mobilityPenalty;
        this.resourcePenalty = resourcePenalty;
        this.normalizedCompletionTime = normalizedCompletionTime;
        this.normalizedCommunicationLatency = normalizedCommunicationLatency;
        this.normalizedMobilityPenalty = normalizedMobilityPenalty;
        this.normalizedResourcePenalty = normalizedResourcePenalty;
        this.geneBreakdowns = List.copyOf(geneBreakdowns);
        this.executionNodeResourceUsageBreakdowns =
                List.copyOf(executionNodeResourceUsageBreakdowns);
        this.linkBandwidthUsageBreakdowns =
                List.copyOf(linkBandwidthUsageBreakdowns);
        this.localResourceUsageBreakdowns =
                List.copyOf(localResourceUsageBreakdowns);
    }

    public double getFitness() {
        return fitness;
    }

    public double getCompletionTimeSeconds() {
        return completionTimeSeconds;
    }

    public double getCommunicationLatencySeconds() {
        return communicationLatencySeconds;
    }

    public double getMobilityPenalty() {
        return mobilityPenalty;
    }

    public double getResourcePenalty() {
        return resourcePenalty;
    }

    public double getNormalizedCompletionTime() {
        return normalizedCompletionTime;
    }

    public double getNormalizedCommunicationLatency() {
        return normalizedCommunicationLatency;
    }

    public double getNormalizedMobilityPenalty() {
        return normalizedMobilityPenalty;
    }

    public double getNormalizedResourcePenalty() {
        return normalizedResourcePenalty;
    }

    public List<GeneEvaluationBreakdown> getGeneBreakdowns() {
        return geneBreakdowns;
    }

    public List<ExecutionNodeResourceUsageBreakdown> getExecutionNodeResourceUsageBreakdowns() {
        return executionNodeResourceUsageBreakdowns;
    }

    public List<LinkBandwidthUsageBreakdown> getLinkBandwidthUsageBreakdowns() {
        return linkBandwidthUsageBreakdowns;
    }

    public List<LocalResourceUsageBreakdown> getLocalResourceUsageBreakdowns() {
        return localResourceUsageBreakdowns;
    }
}