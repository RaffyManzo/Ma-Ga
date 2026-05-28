package ga.operators;

import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.Random;

/**
 * Policy centralizzata per generare e modificare la quota di offloading p_i.
 *
 * <p>Questa classe non sceglie il candidato di esecuzione e non valuta la fitness.
 * Serve solo a produrre valori di offloadingRatio più coerenti con il problema.</p>
 *
 * <p>Rispetto alla formalizzazione, questa classe opera solo sulla variabile
 * decisionale p_i del gene:</p>
 *
 * <pre>
 * g_i = (p_i, f_i, b_i, n_i)
 * </pre>
 *
 * <p>Non introduce nuove variabili decisionali e non modifica la funzione di
 * fitness. Le stime interne usano parametri già presenti nel modello:</p>
 *
 * <ul>
 *     <li>deadline del task;</li>
 *     <li>dimensione dei dati in input/output;</li>
 *     <li>cicli CPU richiesti dal task;</li>
 *     <li>CPU locale;</li>
 *     <li>CPU e banda massime del candidato;</li>
 *     <li>latenza base del candidato.</li>
 * </ul>
 *
 * <p>Obiettivo:</p>
 *
 * <ul>
 *     <li>mantenere esplorazione casuale;</li>
 *     <li>rendere esplorabili i casi p = 0, p = 1 e partial offloading;</li>
 *     <li>evitare che inizializzazione e mutazione producano troppe quote
 *     formalmente valide ma temporalmente poco plausibili;</li>
 *     <li>ridurre il rischio di upload bottleneck quando il task è
 *     communication-heavy.</li>
 * </ul>
 */
public final class OffloadingRatioPolicy {

    private static final double EPSILON = 1.0E-9;

    /**
     * Minima quota remota ammessa quando il candidato non è LOCAL.
     *
     * <p>Se un gene sceglie un candidato remoto, una quota troppo vicina a zero
     * significa che il candidato remoto è quasi inutile.</p>
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
     * Se l'upload rappresenta una quota elevata del costo remoto, la policy
     * evita di campionare p troppo vicino all'upper bound.
     *
     * <p>Questa non è una nuova variabile del cromosoma e non è una nuova
     * penalità. È una regola interna di campionamento collegata ai termini
     * di upload già presenti in T_i(C) e L(C).</p>
     */
    private static final double UPLOAD_DOMINANCE_THRESHOLD = 0.45;

    /**
     * Frazione dell'intervallo [lowerBound, upperBound] usata quando il task
     * è upload-heavy.
     *
     * <p>Valori più bassi riducono p quando il trasferimento dell'input domina.
     * Valori più alti lasciano più spazio all'esplorazione remota.</p>
     */
    private static final double UPLOAD_HEAVY_INTERVAL_FRACTION = 0.65;

    /**
     * Margine di prudenza applicato al lower bound remoto.
     *
     * <p>Il lower bound usa maxCpu e maxBandwidth, quindi è ottimistico.
     * Questo fattore evita che la policy campioni p assumendo condizioni
     * sempre ideali.</p>
     */
    private static final double REMOTE_LOWER_BOUND_SAFETY_FACTOR = 1.15;

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
     * <p>Usa ancora casualità pura, ma solo per candidati remoti.
     * Questo metodo mantiene la componente esplorativa del GA.</p>
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
     * <p>La stima assume:</p>
     *
     * <pre>
     * local(p)  = (1 - p) * A
     * remote(p) = L + p * B
     * </pre>
     *
     * <p>dove A è il tempo locale puro, B è il costo remoto lineare per p = 1
     * e L è la latenza base.</p>
     *
     * <p>Il valore p bilanciato non è una soluzione ottima. È solo un punto
     * plausibile da esplorare nella popolazione o nella mutazione.</p>
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
     * Genera una quota di offloading deadline-aware e upload-aware.
     *
     * <p>La logica non sostituisce il GA con una scelta deterministica.
     * Se esiste un intervallo plausibile di valori di p compatibili con la
     * deadline, viene campionato un valore casuale dentro quell'intervallo.</p>
     *
     * <p>Se il task è upload-heavy, cioè se il tempo di upload domina il costo
     * remoto stimato, il campionamento viene ristretto verso la parte bassa o
     * intermedia dell'intervallo. Questo serve a ridurre casi in cui p è
     * formalmente valido ma genera upload bottleneck.</p>
     *
     * <p>Se l'intervallo non esiste, la policy ricade sulla quota bilanciata
     * con rumore. In questo modo il GA conserva esplorazione, ma evita di
     * usare sistematicamente quote remote incoerenti con il vincolo temporale.</p>
     *
     * @param task task considerato
     * @param candidate candidato remoto
     * @param sourceVehicle veicolo sorgente
     * @param random generatore casuale
     * @return quota remota in [MIN_REMOTE_OFFLOADING_RATIO, 1.0]
     */
    public double deadlineAwareRatio(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            Random random
    ) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null.");
        }

        if (task == null || candidate == null || sourceVehicle == null) {
            return randomRemoteRatio(random);
        }

        if (candidate.getType() == NodeType.LOCAL) {
            return LOCAL_RATIO;
        }

        double deadline = safePositive(task.getDeadlineSeconds());
        double localCpu = safePositive(sourceVehicle.getLocalCpu());
        double maxCpu = safePositive(candidate.getAvailableCpu());
        double maxBandwidth = safePositive(candidate.getAvailableBandwidth());

        if (deadline <= EPSILON
                || localCpu <= EPSILON
                || maxCpu <= EPSILON
                || maxBandwidth <= EPSILON) {
            return randomRemoteRatio(random);
        }

        double cpuCycles = safeNonNegative(task.getCpuCycles());
        double inputBits = safeNonNegative(task.getInputSizeBits());
        double outputBits = safeNonNegative(task.getOutputSizeBits());
        double baseLatency = safeNonNegative(candidate.getBaseLatencySeconds());

        double localFullTime = cpuCycles / localCpu;

        double uploadFullTime = inputBits / maxBandwidth;
        double remoteExecutionFullTime = cpuCycles / maxCpu;
        double downloadFullTime = outputBits / maxBandwidth;

        double remoteVariableFullTime =
                uploadFullTime
                        + remoteExecutionFullTime
                        + downloadFullTime;

        /*
         * Il lower bound remoto è ottimistico: usa massima CPU e massima
         * banda del candidato. Applichiamo un fattore di sicurezza leggero
         * per non generare p troppo alti basandoci su condizioni ideali.
         */
        double safeRemoteVariableFullTime =
                remoteVariableFullTime * REMOTE_LOWER_BOUND_SAFETY_FACTOR;

        if (localFullTime <= EPSILON
                || safeRemoteVariableFullTime <= EPSILON
                || !Double.isFinite(localFullTime)
                || !Double.isFinite(safeRemoteVariableFullTime)) {
            return randomRemoteRatio(random);
        }

        /*
         * Vincolo sul ramo locale:
         *
         * local(p) = (1 - p) * localFullTime <= deadline
         * quindi p >= 1 - deadline / localFullTime.
         */
        double lowerBound = 1.0 - deadline / localFullTime;

        /*
         * Vincolo sul ramo remoto:
         *
         * remote(p) = baseLatency + p * safeRemoteVariableFullTime <= deadline
         * quindi p <= (deadline - baseLatency) / safeRemoteVariableFullTime.
         */
        double upperBound =
                (deadline - baseLatency) / safeRemoteVariableFullTime;

        lowerBound = clampRemoteRatio(lowerBound);
        upperBound = clampRemoteRatio(upperBound);

        if (lowerBound <= upperBound) {
            double uploadShare = remoteVariableFullTime <= EPSILON
                    ? 0.0
                    : uploadFullTime / remoteVariableFullTime;

            if (uploadShare >= UPLOAD_DOMINANCE_THRESHOLD) {
                double reducedUpperBound =
                        lowerBound
                                + (upperBound - lowerBound)
                                * UPLOAD_HEAVY_INTERVAL_FRACTION;

                return randomBetween(
                        lowerBound,
                        reducedUpperBound,
                        random
                );
            }

            return randomBetween(lowerBound, upperBound, random);
        }

        /*
         * Nessun intervallo chiaramente fattibile.
         * Non forziamo p = 1. Usiamo il valore bilanciato perturbato.
         */
        double balanced = balancedRemoteRatio(
                task,
                candidate,
                sourceVehicle
        );

        double noise = randomBetween(0.85, 1.15, random);

        return clampRemoteRatio(balanced * noise);
    }

    /**
     * Applica una piccola mutazione locale a una quota remota esistente.
     *
     * <p>Questa è la mutazione classica: resta vicina al valore corrente.</p>
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
     * <p>A differenza della piccola perturbazione, questa permette al GA
     * di saltare in un'altra zona dello spazio delle quote.</p>
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
     * <p>Serve a rendere p = 1 esplicitamente esplorabile.</p>
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
     * <p>Se il valore non è valido o è troppo basso, viene portato alla quota
     * minima remota.</p>
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
     * <p>Include upload, esecuzione remota e download. La latenza base viene
     * trattata separatamente nel bilanciamento.</p>
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
     * Converte valori non validi o non positivi in zero.
     */
    private double safePositive(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 0.0;
        }

        return value;
    }

    /**
     * Genera un valore casuale nell'intervallo [min, max].
     */
    private double randomBetween(
            double min,
            double max,
            Random random
    ) {
        if (max <= min) {
            return min;
        }

        return min + random.nextDouble() * (max - min);
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
