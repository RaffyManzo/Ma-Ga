package model.offloading;

/**
 * Componenti temporali di una decisione di offloading.
 *
 * <p>Il breakdown segue la formalizzazione del gene: ramo locale, ramo remoto,
 * latenza di comunicazione e tempo finale di completamento.</p>
 *
 * <p>Per una scelta parzialmente remota, {@code downloadTimeSeconds}
 * rappresenta il download integrale del risultato remoto. Non scala con la
 * quota di offloading quando {@code p > 0}.</p>
 *
 * <p>{@code baseLatencySeconds} resta una estensione operativa provvisoria
 * associata al nodo remoto, in attesa di chiarire formalmente il ruolo di
 * {@code tau_n}.</p>
 */
public final class OffloadingTimeBreakdown {

    private final double offloadingRatio;
    private final double localCpuCycles;
    private final double localExecutionTimeSeconds;
    private final double uploadTimeSeconds;
    private final double remoteExecutionTimeSeconds;
    private final double downloadTimeSeconds;
    private final double baseLatencySeconds;
    private final double remotePartTimeSeconds;
    private final double communicationLatencySeconds;
    private final double completionTimeSeconds;

    OffloadingTimeBreakdown(
            double offloadingRatio,
            double localCpuCycles,
            double localExecutionTimeSeconds,
            double uploadTimeSeconds,
            double remoteExecutionTimeSeconds,
            double downloadTimeSeconds,
            double baseLatencySeconds,
            double remotePartTimeSeconds,
            double communicationLatencySeconds,
            double completionTimeSeconds
    ) {
        this.offloadingRatio = offloadingRatio;
        this.localCpuCycles = localCpuCycles;
        this.localExecutionTimeSeconds = localExecutionTimeSeconds;
        this.uploadTimeSeconds = uploadTimeSeconds;
        this.remoteExecutionTimeSeconds = remoteExecutionTimeSeconds;
        this.downloadTimeSeconds = downloadTimeSeconds;
        this.baseLatencySeconds = baseLatencySeconds;
        this.remotePartTimeSeconds = remotePartTimeSeconds;
        this.communicationLatencySeconds = communicationLatencySeconds;
        this.completionTimeSeconds = completionTimeSeconds;
    }

    public double getOffloadingRatio() { return offloadingRatio; }
    public double getLocalCpuCycles() { return localCpuCycles; }
    public double getLocalExecutionTimeSeconds() { return localExecutionTimeSeconds; }
    public double getUploadTimeSeconds() { return uploadTimeSeconds; }
    public double getRemoteExecutionTimeSeconds() { return remoteExecutionTimeSeconds; }
    public double getDownloadTimeSeconds() { return downloadTimeSeconds; }
    public double getBaseLatencySeconds() { return baseLatencySeconds; }
    public double getRemotePartTimeSeconds() { return remotePartTimeSeconds; }
    public double getCommunicationLatencySeconds() { return communicationLatencySeconds; }
    public double getCompletionTimeSeconds() { return completionTimeSeconds; }
}
