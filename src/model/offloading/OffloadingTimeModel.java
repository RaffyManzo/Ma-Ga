package model.offloading;

import model.node.NodeCandidate;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.Objects;

/**
 * Modello unico dei tempi di offloading.
 *
 * <p>La formalizzazione usa una quota remota {@code p_i}. Per una scelta
 * parzialmente remota, upload ed esecuzione remota scalano con {@code p_i},
 * mentre il risultato remoto viene restituito integralmente:</p>
 *
 * <pre>
 * T_local(p)  = (1 - p) * cycles / f_local
 * T_remote(p) = p * input / b + p * cycles / f + output / b + L_base
 * T_i         = max(T_local, T_remote)  se 0 < p < 1
 * T_i         = T_remote                se p = 1
 * </pre>
 *
 * <p>Per i geni locali vale {@code p = 0}, la banda è nulla e il tempo è
 * {@code cycles / f_local}.</p>
 *
 * <p>{@code L_base} viene mantenuta provvisoriamente come estensione operativa
 * associata al nodo remoto. La formalizzazione introduce il parametro
 * {@code tau_n}, ma deve ancora chiarire in modo univoco se e come includerlo
 * nelle formule operative. Questa scelta resta quindi esplicita e separata
 * dalla correzione certa sul download integrale.</p>
 */
public final class OffloadingTimeModel {

    public static final double EPSILON = 1.0E-9;

    /**
     * Valuta una scelta locale.
     *
     * @param task task da eseguire
     * @param localCpu CPU locale disponibile
     * @return breakdown temporale locale
     */
    public OffloadingTimeBreakdown evaluateLocal(
            TaskInstance task,
            double localCpu
    ) {
        Objects.requireNonNull(task, "task must not be null.");

        double localCpuCycles = safeNonNegative(task.getCpuCycles());
        double localExecutionTime = safeDivide(localCpuCycles, localCpu);

        return new OffloadingTimeBreakdown(
                0.0,
                localCpuCycles,
                localExecutionTime,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                localExecutionTime
        );
    }

    /**
     * Valuta una scelta remota o parzialmente remota.
     *
     * <p>Quando {@code p > 0}, il download usa l'output remoto integrale.
     * Questo riflette l'ipotesi della formalizzazione: il risultato della
     * porzione remota è necessario per ricomporre l'esito complessivo del
     * task.</p>
     *
     * @param task task da eseguire
     * @param candidate candidato remoto scelto
     * @param localCpu CPU locale del veicolo sorgente
     * @param offloadingRatio quota remota {@code p_i}
     * @param allocatedCpu CPU assegnata al ramo remoto
     * @param allocatedBandwidth banda assegnata alla comunicazione remota
     * @return breakdown temporale completo
     */
    public OffloadingTimeBreakdown evaluateRemote(
            TaskInstance task,
            NodeCandidate candidate,
            double localCpu,
            double offloadingRatio,
            double allocatedCpu,
            double allocatedBandwidth
    ) {
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");

        double p = clamp01(offloadingRatio);
        if (p <= EPSILON) {
            return evaluateLocal(task, localCpu);
        }

        double localCpuCycles = (1.0 - p) * safeNonNegative(task.getCpuCycles());
        double localExecutionTime = safeDivide(localCpuCycles, localCpu);

        double uploadTime = safeDivide(
                p * safeNonNegative(task.getInputSizeBits()),
                allocatedBandwidth
        );
        double remoteExecutionTime = safeDivide(
                p * safeNonNegative(task.getCpuCycles()),
                allocatedCpu
        );
        double downloadTime = safeDivide(
                safeNonNegative(task.getOutputSizeBits()),
                allocatedBandwidth
        );
        double baseLatency = safeNonNegative(candidate.getBaseLatencySeconds());

        double remotePartTime =
                uploadTime
                        + remoteExecutionTime
                        + downloadTime
                        + baseLatency;
        double communicationLatency =
                uploadTime
                        + downloadTime
                        + baseLatency;
        double completionTime = p >= 1.0 - EPSILON
                ? remotePartTime
                : Math.max(localExecutionTime, remotePartTime);

        return new OffloadingTimeBreakdown(
                p,
                localCpuCycles,
                localExecutionTime,
                uploadTime,
                remoteExecutionTime,
                downloadTime,
                baseLatency,
                remotePartTime,
                communicationLatency,
                completionTime
        );
    }

    /**
     * Stima il tempo locale puro, cioè il caso {@code p = 0}.
     */
    public double estimateLocalOnlyTime(
            TaskInstance task,
            VehicleSnapshot sourceVehicle
    ) {
        Objects.requireNonNull(task, "task must not be null.");
        if (sourceVehicle == null) {
            return Double.POSITIVE_INFINITY;
        }
        return safeDivide(
                safeNonNegative(task.getCpuCycles()),
                sourceVehicle.getLocalCpu()
        );
    }

    /**
     * Stima il ramo locale per una quota remota arbitraria.
     */
    public double estimateLocalBranchTime(
            TaskInstance task,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio
    ) {
        Objects.requireNonNull(task, "task must not be null.");
        if (sourceVehicle == null) {
            return Double.POSITIVE_INFINITY;
        }
        double p = clamp01(offloadingRatio);
        double localCycles = (1.0 - p) * safeNonNegative(task.getCpuCycles());
        return safeDivide(localCycles, sourceVehicle.getLocalCpu());
    }

    /**
     * Stima la componente del ramo remoto che scala con {@code p}:
     * upload dell'input ed esecuzione remota.
     */
    public double estimateRemoteScaledTime(
            TaskInstance task,
            NodeCandidate candidate
    ) {
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");

        double bandwidth = candidate.getAvailableBandwidth();
        double remoteCpu = candidate.getAvailableCpu();
        double uploadTime = safeDivide(
                safeNonNegative(task.getInputSizeBits()),
                bandwidth
        );
        double remoteExecutionTime = safeDivide(
                safeNonNegative(task.getCpuCycles()),
                remoteCpu
        );
        return uploadTime + remoteExecutionTime;
    }

    /**
     * Stima la componente fissa del ramo remoto per ogni {@code p > 0}:
     * download integrale dell'output e latenza base provvisoria.
     */
    public double estimateRemoteFixedTime(
            TaskInstance task,
            NodeCandidate candidate
    ) {
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");

        double downloadTime = safeDivide(
                safeNonNegative(task.getOutputSizeBits()),
                candidate.getAvailableBandwidth()
        );
        double baseLatency = safeNonNegative(candidate.getBaseLatencySeconds());
        return downloadTime + baseLatency;
    }

    /**
     * Mantiene compatibilità con chiamanti precedenti.
     *
     * <p>Restituisce il costo remoto completo per {@code p = 1}, senza
     * latenza base. Non deve essere moltiplicato nuovamente per {@code p}
     * nelle euristiche del partial offloading.</p>
     */
    @Deprecated
    public double estimateRemoteLinearTime(
            TaskInstance task,
            NodeCandidate candidate
    ) {
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");

        double downloadTime = safeDivide(
                safeNonNegative(task.getOutputSizeBits()),
                candidate.getAvailableBandwidth()
        );
        return estimateRemoteScaledTime(task, candidate) + downloadTime;
    }

    /**
     * Stima il completion time usando la quota remota e le risorse massime del
     * candidato.
     */
    public double estimateCompletionWithCandidateCapacity(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio
    ) {
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");
        if (sourceVehicle == null) {
            return Double.POSITIVE_INFINITY;
        }
        return evaluateRemote(
                task,
                candidate,
                sourceVehicle.getLocalCpu(),
                offloadingRatio,
                candidate.getAvailableCpu(),
                candidate.getAvailableBandwidth()
        ).getCompletionTimeSeconds();
    }

    private double safeDivide(double numerator, double denominator) {
        if (!Double.isFinite(numerator)) {
            return Double.POSITIVE_INFINITY;
        }
        if (!Double.isFinite(denominator) || denominator <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        return numerator / denominator;
    }

    private double safeNonNegative(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }
        return value;
    }

    private double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
