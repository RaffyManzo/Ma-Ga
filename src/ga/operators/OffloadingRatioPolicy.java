package ga.operators;

import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.Random;

/**
 * Policy centralizzata per generare e modificare la quota di offloading p_i.
 *
 * Questa classe non sceglie il candidato di esecuzione e non valuta la fitness.
 * Serve solo a produrre valori di offloadingRatio più coerenti con il problema.
 *
 * Obiettivo:
 * - mantenere esplorazione casuale;
 * - rendere esplorabili anche i casi semanticamente importanti:
 *   p = 0, p = 1 e p bilanciato;
 * - evitare che PopulationInitializer, MutationOperator e RepairOperator
 *   implementino logiche diverse sulla stessa variabile.
 */
public final class OffloadingRatioPolicy {

    private static final double EPSILON = 1.0E-9;

    /**
     * Minima quota remota ammessa quando il candidato non è LOCAL.
     *
     * Se un gene sceglie un candidato remoto, una quota troppo vicina a zero
     * significa che il candidato remoto è quasi inutile.
     */
    public static final double MIN_REMOTE_OFFLOADING_RATIO = 0.05;

    /**
     * Quota che rappresenta esecuzione locale.
     */
    public static final double LOCAL_RATIO = 0.0;

    /**
     * Quota che rappresenta full offloading.
     */
    public static final double FULL_OFFLOADING_RATIO = 1.0;

    /**
     * Ampiezza usata per piccole mutazioni locali della quota p_i.
     */
    private static final double SMALL_MUTATION_DELTA = 0.15;

    /**
     * Restituisce la quota locale.
     *
     * @return 0.0
     */
    public double localRatio() {
        return LOCAL_RATIO;
    }

    /**
     * Restituisce la quota di full offloading.
     *
     * @return 1.0
     */
    public double fullRatio() {
        return FULL_OFFLOADING_RATIO;
    }

    /**
     * Genera una quota remota casuale.
     *
     * Usa ancora casualità pura, ma solo per candidati remoti.
     * Questo metodo mantiene la componente esplorativa del GA.
     *
     * @param random generatore casuale
     * @return quota in [MIN_REMOTE_OFFLOADING_RATIO, 1.0]
     */
    public double randomRemoteRatio(Random random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null.");
        }

        return MIN_REMOTE_OFFLOADING_RATIO
                + random.nextDouble()
                * (FULL_OFFLOADING_RATIO - MIN_REMOTE_OFFLOADING_RATIO);
    }

    /**
     * Calcola una quota remota bilanciata tra ramo locale e ramo remoto.
     *
     * La stima assume:
     *
     * local(p)  = (1 - p) * A
     * remote(p) = L + p * B
     *
     * dove:
     * - A è il tempo locale puro;
     * - B è il costo remoto lineare per p = 1;
     * - L è la latenza base.
     *
     * Il valore p bilanciato è quello che prova ad avvicinare local(p)
     * e remote(p). Non è una soluzione ottima: è solo un buon punto
     * da esplorare nella popolazione o nella mutazione.
     *
     * @param task task considerato
     * @param candidate candidato remoto
     * @param sourceVehicle veicolo sorgente del task
     * @return quota remota in [MIN_REMOTE_OFFLOADING_RATIO, 1.0]
     */
    public double balancedRemoteRatio(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null.");
        }

        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null.");
        }

        if (candidate.getType() == NodeType.LOCAL) {
            return LOCAL_RATIO;
        }

        double localOnlyTime = estimateLocalOnlyTime(
                task,
                sourceVehicle
        );

        double remoteLinearTime = estimateRemoteLinearTime(
                task,
                candidate
        );

        double baseLatency = safeNonNegative(
                candidate.getBaseLatencySeconds()
        );

        if (!Double.isFinite(localOnlyTime)
                || !Double.isFinite(remoteLinearTime)
                || remoteLinearTime <= EPSILON) {
            return FULL_OFFLOADING_RATIO;
        }

        double denominator = localOnlyTime + remoteLinearTime;

        if (denominator <= EPSILON) {
            return MIN_REMOTE_OFFLOADING_RATIO;
        }

        double p = (localOnlyTime - baseLatency) / denominator;

        return clampRemoteRatio(p);
    }

    /**
     * Applica una piccola mutazione locale a una quota remota esistente.
     *
     * Questa è la mutazione classica: resta vicina al valore corrente.
     *
     * @param currentRatio quota corrente
     * @param random generatore casuale
     * @return nuova quota remota valida
     */
    public double mutateBySmallStep(
            double currentRatio,
            Random random
    ) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null.");
        }

        double base = normalizeRemoteRatio(currentRatio);

        double delta = (random.nextDouble() - 0.5)
                * 2.0
                * SMALL_MUTATION_DELTA;

        return clampRemoteRatio(base + delta);
    }

    /**
     * Genera una mutazione random-reset.
     *
     * A differenza della piccola perturbazione, questa permette al GA
     * di saltare in un'altra zona dello spazio delle quote.
     *
     * @param random generatore casuale
     * @return quota remota casuale valida
     */
    public double mutateByRandomReset(Random random) {
        return randomRemoteRatio(random);
    }

    /**
     * Genera una mutazione verso full offloading.
     *
     * Serve a rendere p = 1 esplicitamente esplorabile.
     *
     * @return 1.0
     */
    public double mutateToFullOffloading() {
        return FULL_OFFLOADING_RATIO;
    }

    /**
     * Genera una mutazione verso la quota bilanciata.
     *
     * @param task task considerato
     * @param candidate candidato remoto
     * @param sourceVehicle veicolo sorgente
     * @return quota bilanciata valida
     */
    public double mutateToBalancedRatio(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
        return balancedRemoteRatio(
                task,
                candidate,
                sourceVehicle
        );
    }

    /**
     * Normalizza una quota remota.
     *
     * Se il valore non è valido o è troppo basso, viene portato alla quota
     * minima remota.
     *
     * @param ratio quota proposta
     * @return quota remota valida
     */
    public double normalizeRemoteRatio(double ratio) {
        if (!Double.isFinite(ratio) || ratio <= MIN_REMOTE_OFFLOADING_RATIO) {
            return MIN_REMOTE_OFFLOADING_RATIO;
        }

        return clampRemoteRatio(ratio);
    }

    /**
     * Limita una quota remota nell'intervallo ammesso.
     */
    private double clampRemoteRatio(double ratio) {
        if (!Double.isFinite(ratio)) {
            return MIN_REMOTE_OFFLOADING_RATIO;
        }

        return Math.max(
                MIN_REMOTE_OFFLOADING_RATIO,
                Math.min(FULL_OFFLOADING_RATIO, ratio)
        );
    }

    /**
     * Stima il tempo locale puro.
     */
    private double estimateLocalOnlyTime(
            TaskInstance task,
            VehicleSnapshot sourceVehicle
    ) {
        if (sourceVehicle == null) {
            return Double.POSITIVE_INFINITY;
        }

        double localCpu = safeNonNegative(
                sourceVehicle.getLocalCpu()
        );

        if (localCpu <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }

        return safeNonNegative(task.getCpuCycles()) / localCpu;
    }

    /**
     * Stima il costo remoto lineare per p = 1.
     *
     * Include upload, esecuzione remota e download.
     * La latenza base viene trattata separatamente nel bilanciamento.
     */
    private double estimateRemoteLinearTime(
            TaskInstance task,
            NodeCandidate candidate
    ) {
        double bandwidth = safeNonNegative(
                candidate.getAvailableBandwidth()
        );

        double remoteCpu = safeNonNegative(
                candidate.getAvailableCpu()
        );

        if (bandwidth <= EPSILON || remoteCpu <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }

        double uploadTime = safeNonNegative(task.getInputSizeBits())
                / bandwidth;

        double remoteExecutionTime = safeNonNegative(task.getCpuCycles())
                / remoteCpu;

        double downloadTime = safeNonNegative(task.getOutputSizeBits())
                / bandwidth;

        return uploadTime + remoteExecutionTime + downloadTime;
    }

    /**
     * Converte valori non validi in zero.
     */
    private double safeNonNegative(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }

        return value;
    }
}