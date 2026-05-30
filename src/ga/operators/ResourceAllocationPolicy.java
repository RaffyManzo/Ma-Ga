package ga.operators;

import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.offloading.OffloadingTimeModel;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.Objects;
import java.util.Random;

/**
 * Policy centralizzata per generare e mutare CPU e banda assegnate a un gene.
 *
 * <p>Questa classe non sostituisce il Genetic Algorithm. Produce allocazioni
 * iniziali e mutazioni plausibili, lasciando alla fitness la responsabilità di
 * premiare o scartare le soluzioni.</p>
 *
 * <p>Il suo compito è evitare combinazioni palesemente incoerenti tra:</p>
 *
 * <pre>
 * p_i                 quota di offloading
 * f_i                 CPU assegnata
 * b_i                 banda assegnata
 * deadline            vincolo temporale del task
 * candidate capacity  capacità massima del candidato
 * </pre>
 *
 * <p>La policy mantiene la componente genetica:</p>
 *
 * <ul>
 *     <li>una parte delle allocazioni resta casuale;</li>
 *     <li>le allocazioni deadline-aware sono perturbate con rumore;</li>
 *     <li>la mutazione conserva small-step e random reset;</li>
 *     <li>la fitness resta responsabile della selezione finale.</li>
 * </ul>
 */
public final class ResourceAllocationPolicy {

    private final OffloadingTimeModel offloadingTimeModel =
            new OffloadingTimeModel();

    private enum Feasibility {
        FEASIBLE,
        BORDERLINE,
        INFEASIBLE_LOCAL_BRANCH,
        INFEASIBLE_REMOTE_BRANCH,
        INFEASIBLE_BOTH_BRANCHES
    }

    private static final double EPSILON = 1.0E-9;

    /**
     * Frazione minima di risorsa assegnabile a un gene remoto.
     */
    private static final double MIN_RESOURCE_FRACTION = 0.05;

    /**
     * Tolleranza usata per non scartare subito scelte leggermente oltre deadline.
     *
     * <p>Se il lower bound è entro il 10% oltre la deadline, la scelta viene
     * trattata come borderline. Questo mantiene esplorazione genetica.</p>
     */
    private static final double DEADLINE_TOLERANCE = 1.10;

    /**
     * Rumore moltiplicativo applicato alle stime deadline-aware.
     */
    private static final double DEADLINE_NOISE_MIN = 0.90;
    private static final double DEADLINE_NOISE_MAX = 1.20;

    /**
     * Fattori per mutazione locale delle risorse.
     */
    private static final double SMALL_STEP_MIN = 0.80;
    private static final double SMALL_STEP_MAX = 1.25;

    /**
     * Range per allocazioni moderate.
     *
     * <p>Serve quando una scelta remota non sembra recuperabile neanche con una
     * stima ottimistica: in quel caso una saturazione aggressiva consumerebbe
     * risorse senza rendere la soluzione competitiva.</p>
     */
    private static final double MODERATE_MIN_FRACTION = 0.08;
    private static final double MODERATE_MAX_FRACTION = 0.45;

    /**
     * Range per scelte borderline.
     */
    private static final double BORDERLINE_MIN_FRACTION = 0.35;
    private static final double BORDERLINE_MAX_FRACTION = 0.70;

    /**
     * Range per scelte aggressive, usato solo raramente e solo quando la scelta
     * è almeno plausibile.
     */
    private static final double AGGRESSIVE_MIN_FRACTION = 0.60;
    private static final double AGGRESSIVE_MAX_FRACTION = 0.90;

    /**
     * Crea una allocazione iniziale.
     *
     * <p>La scelta è guidata ma non deterministica. La classificazione di
     * fattibilità impedisce alla policy di saturare risorse su scelte remote
     * che nemmeno in condizioni ottimistiche possono rispettare la deadline.</p>
     */
    public ResourceAllocationDecision allocateInitial(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio,
            Random random
    ) {
        validateInputs(task, candidate, random);

        if (candidate.getType() == NodeType.LOCAL) {
            return localAllocation(candidate, sourceVehicle);
        }

        Feasibility feasibility =
                classifyFeasibility(
                        task,
                        candidate,
                        sourceVehicle,
                        offloadingRatio
                );

        double roll = random.nextDouble();

        return switch (feasibility) {
            case FEASIBLE -> allocateForFeasibleChoice(
                    task,
                    candidate,
                    offloadingRatio,
                    random,
                    roll
            );

            case BORDERLINE -> allocateForBorderlineChoice(
                    candidate,
                    offloadingRatio,
                    random,
                    roll
            );

            case INFEASIBLE_LOCAL_BRANCH,
                    INFEASIBLE_REMOTE_BRANCH,
                    INFEASIBLE_BOTH_BRANCHES -> allocateForInfeasibleChoice(
                    candidate,
                    offloadingRatio,
                    random,
                    roll
            );
        };
    }

    /**
     * Muta CPU e banda.
     *
     * <p>Se il candidato cambia, viene generata una nuova allocazione iniziale.
     * Se il candidato resta lo stesso, si alternano small-step, deadline-aware,
     * random reset e allocazioni moderate.</p>
     */
    public ResourceAllocationDecision mutate(
            Gene currentGene,
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio,
            boolean candidateChanged,
            Random random
    ) {
        validateInputs(task, candidate, random);

        if (candidate.getType() == NodeType.LOCAL) {
            return localAllocation(candidate, sourceVehicle);
        }

        if (currentGene == null || candidateChanged) {
            return allocateInitial(
                    task,
                    candidate,
                    sourceVehicle,
                    offloadingRatio,
                    random
            );
        }

        Feasibility feasibility =
                classifyFeasibility(
                        task,
                        candidate,
                        sourceVehicle,
                        offloadingRatio
                );

        double roll = random.nextDouble();

        return switch (feasibility) {
            case FEASIBLE -> mutateFeasibleChoice(
                    currentGene,
                    task,
                    candidate,
                    offloadingRatio,
                    random,
                    roll
            );

            case BORDERLINE -> mutateBorderlineChoice(
                    currentGene,
                    candidate,
                    offloadingRatio,
                    random,
                    roll
            );

            case INFEASIBLE_LOCAL_BRANCH,
                    INFEASIBLE_REMOTE_BRANCH,
                    INFEASIBLE_BOTH_BRANCHES -> mutateInfeasibleChoice(
                    currentGene,
                    candidate,
                    offloadingRatio,
                    random,
                    roll
            );
        };
    }

    private ResourceAllocationDecision allocateForFeasibleChoice(
            TaskInstance task,
            NodeCandidate candidate,
            double offloadingRatio,
            Random random,
            double roll
    ) {
        // Scelte fattibili: prevale deadline-aware, con una quota esplorativa.
        if (roll < 0.30) {
            return randomRemoteAllocation(candidate, random);
        }

        if (roll < 0.90) {
            return deadlineAwareAllocation(
                    task,
                    candidate,
                    offloadingRatio,
                    random
            );
        }

        return aggressiveRemoteAllocation(
                candidate,
                offloadingRatio,
                random
        );
    }

    private ResourceAllocationDecision allocateForBorderlineChoice(
            NodeCandidate candidate,
            double offloadingRatio,
            Random random,
            double roll
    ) {
        // Scelte borderline: aumentano le risorse, ma senza saturare sistematicamente.
        if (roll < 0.25) {
            return randomRemoteAllocation(candidate, random);
        }

        if (roll < 0.90) {
            return borderlineRemoteAllocation(
                    candidate,
                    offloadingRatio,
                    random
            );
        }

        return aggressiveRemoteAllocation(
                candidate,
                offloadingRatio,
                random
        );
    }

    private ResourceAllocationDecision allocateForInfeasibleChoice(
            NodeCandidate candidate,
            double offloadingRatio,
            Random random,
            double roll
    ) {
        // Scelte non salvabili: si preferiscono allocazioni moderate o casuali.
        if (roll < 0.35) {
            return randomRemoteAllocation(candidate, random);
        }

        return moderateRemoteAllocation(
                candidate,
                offloadingRatio,
                random
        );
    }

    private ResourceAllocationDecision mutateFeasibleChoice(
            Gene currentGene,
            TaskInstance task,
            NodeCandidate candidate,
            double offloadingRatio,
            Random random,
            double roll
    ) {
        if (roll < 0.50) {
            return smallStepMutation(
                    currentGene,
                    candidate,
                    random
            );
        }

        if (roll < 0.80) {
            return deadlineAwareAllocation(
                    task,
                    candidate,
                    offloadingRatio,
                    random
            );
        }

        if (roll < 0.95) {
            return randomRemoteAllocation(candidate, random);
        }

        return aggressiveRemoteAllocation(
                candidate,
                offloadingRatio,
                random
        );
    }

    private ResourceAllocationDecision mutateBorderlineChoice(
            Gene currentGene,
            NodeCandidate candidate,
            double offloadingRatio,
            Random random,
            double roll
    ) {
        if (roll < 0.45) {
            return smallStepMutation(
                    currentGene,
                    candidate,
                    random
            );
        }

        if (roll < 0.85) {
            return borderlineRemoteAllocation(
                    candidate,
                    offloadingRatio,
                    random
            );
        }

        return randomRemoteAllocation(candidate, random);
    }

    private ResourceAllocationDecision mutateInfeasibleChoice(
            Gene currentGene,
            NodeCandidate candidate,
            double offloadingRatio,
            Random random,
            double roll
    ) {
        if (roll < 0.40) {
            return smallStepMutation(
                    currentGene,
                    candidate,
                    random
            );
        }

        if (roll < 0.75) {
            return moderateRemoteAllocation(
                    candidate,
                    offloadingRatio,
                    random
            );
        }

        return randomRemoteAllocation(candidate, random);
    }

    /**
     * Classifica se una scelta remota è plausibile rispetto alla deadline.
     *
     * <p>La stima è ottimistica: usa max CPU e max bandwidth del candidato.
     * Se fallisce anche così, assegnare risorse aggressive non risolve il
     * problema e rischia solo di saturare il sistema.</p>
     */
    private Feasibility classifyFeasibility(
            TaskInstance task,
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio
    ) {
        double deadline = safeNonNegative(task.getDeadlineSeconds());

        if (deadline <= EPSILON) {
            return Feasibility.INFEASIBLE_BOTH_BRANCHES;
        }

        double p = normalizeRemoteRatio(offloadingRatio);

        double localLowerBound =
                estimateLocalBranchTime(
                        task,
                        sourceVehicle,
                        p
                );

        double remoteLowerBound =
                estimateRemoteBranchLowerBound(
                        task,
                        candidate,
                        p
                );

        boolean localFinite = Double.isFinite(localLowerBound);
        boolean remoteFinite = Double.isFinite(remoteLowerBound);

        if (!localFinite && !remoteFinite) {
            return Feasibility.INFEASIBLE_BOTH_BRANCHES;
        }

        if (!remoteFinite) {
            return Feasibility.INFEASIBLE_REMOTE_BRANCH;
        }

        double completionLowerBound;

        if (!localFinite) {
            completionLowerBound = remoteLowerBound;
        } else {
            completionLowerBound =
                    Math.max(localLowerBound, remoteLowerBound);
        }

        if (completionLowerBound <= deadline) {
            return Feasibility.FEASIBLE;
        }

        if (completionLowerBound <= deadline * DEADLINE_TOLERANCE) {
            return Feasibility.BORDERLINE;
        }

        boolean localBad =
                localFinite && localLowerBound > deadline * DEADLINE_TOLERANCE;

        boolean remoteBad =
                remoteLowerBound > deadline * DEADLINE_TOLERANCE;

        if (localBad && remoteBad) {
            return Feasibility.INFEASIBLE_BOTH_BRANCHES;
        }

        if (localBad) {
            return Feasibility.INFEASIBLE_LOCAL_BRANCH;
        }

        return Feasibility.INFEASIBLE_REMOTE_BRANCH;
    }

    private ResourceAllocationDecision localAllocation(
            NodeCandidate candidate,
            VehicleSnapshot sourceVehicle
    ) {
        double localCpu = sourceVehicle == null
                ? safeNonNegative(candidate.getAvailableCpu())
                : safeNonNegative(sourceVehicle.getLocalCpu());

        return new ResourceAllocationDecision(
                localCpu,
                0.0,
                ResourceAllocationDecision.Mode.LOCAL
        );
    }

    /**
     * Allocazione deadline-aware per scelte effettivamente fattibili.
     */
    private ResourceAllocationDecision deadlineAwareAllocation(
            TaskInstance task,
            NodeCandidate candidate,
            double offloadingRatio,
            Random random
    ) {
        double maxCpu = safeNonNegative(candidate.getAvailableCpu());
        double maxBandwidth = safeNonNegative(candidate.getAvailableBandwidth());

        if (maxCpu <= EPSILON || maxBandwidth <= EPSILON) {
            return randomRemoteAllocation(candidate, random);
        }

        double p = normalizeRemoteRatio(offloadingRatio);
        double deadline = safeNonNegative(task.getDeadlineSeconds());
        double baseLatency = safeNonNegative(candidate.getBaseLatencySeconds());

        if (deadline <= baseLatency + EPSILON) {
            return borderlineRemoteAllocation(candidate, p, random);
        }

        double remoteBudget = deadline - baseLatency;

        double remoteCycles = p * safeNonNegative(task.getCpuCycles());
        double remoteBits = p * (
                safeNonNegative(task.getInputSizeBits())
                        + safeNonNegative(task.getOutputSizeBits())
        );

        if (remoteCycles <= EPSILON && remoteBits <= EPSILON) {
            return randomRemoteAllocation(candidate, random);
        }

        double minExecAtMaxCpu =
                remoteCycles <= EPSILON ? 0.0 : remoteCycles / maxCpu;

        double minCommAtMaxBandwidth =
                remoteBits <= EPSILON ? 0.0 : remoteBits / maxBandwidth;

        double minimumRemoteTime =
                minExecAtMaxCpu + minCommAtMaxBandwidth;

        if (minimumRemoteTime > remoteBudget) {
            return borderlineRemoteAllocation(candidate, p, random);
        }

        double slack = Math.max(0.0, remoteBudget - minimumRemoteTime);
        double totalMin = Math.max(EPSILON, minimumRemoteTime);

        double communicationShare =
                clamp(minCommAtMaxBandwidth / totalMin, 0.35, 0.65);

        if (remoteBits <= EPSILON) {
            communicationShare = 0.0;
        }

        if (remoteCycles <= EPSILON) {
            communicationShare = 1.0;
        }

        double communicationBudget =
                minCommAtMaxBandwidth + slack * communicationShare;

        double executionBudget =
                minExecAtMaxCpu + slack * (1.0 - communicationShare);

        double requiredBandwidth =
                remoteBits <= EPSILON
                        ? maxBandwidth * MIN_RESOURCE_FRACTION
                        : remoteBits / Math.max(EPSILON, communicationBudget);

        double requiredCpu =
                remoteCycles <= EPSILON
                        ? maxCpu * MIN_RESOURCE_FRACTION
                        : remoteCycles / Math.max(EPSILON, executionBudget);

        /*
         * Floor adattivo: cresce con p, ma resta limitato per evitare che il
         * campionamento deadline-aware saturi sistematicamente nodi e link.
         */
        double adaptiveMinFraction =
                clamp(MIN_RESOURCE_FRACTION + 0.15 * p, 0.05, 0.25);

        requiredCpu =
                Math.max(requiredCpu, maxCpu * adaptiveMinFraction);

        requiredBandwidth =
                Math.max(requiredBandwidth, maxBandwidth * adaptiveMinFraction);

        requiredCpu *= randomFactor(
                random,
                DEADLINE_NOISE_MIN,
                DEADLINE_NOISE_MAX
        );

        requiredBandwidth *= randomFactor(
                random,
                DEADLINE_NOISE_MIN,
                DEADLINE_NOISE_MAX
        );

        return new ResourceAllocationDecision(
                clampResource(requiredCpu, maxCpu),
                clampResource(requiredBandwidth, maxBandwidth),
                ResourceAllocationDecision.Mode.DEADLINE_AWARE
        );
    }

    private ResourceAllocationDecision borderlineRemoteAllocation(
            NodeCandidate candidate,
            double offloadingRatio,
            Random random
    ) {
        double p = normalizeRemoteRatio(offloadingRatio);

        double minFraction =
                clamp(BORDERLINE_MIN_FRACTION + 0.10 * p, 0.35, 0.55);

        return fractionRangeAllocation(
                candidate,
                minFraction,
                BORDERLINE_MAX_FRACTION,
                random,
                ResourceAllocationDecision.Mode.BORDERLINE
        );
    }

    private ResourceAllocationDecision moderateRemoteAllocation(
            NodeCandidate candidate,
            double offloadingRatio,
            Random random
    ) {
        double p = normalizeRemoteRatio(offloadingRatio);

        double maxFraction =
                clamp(MODERATE_MAX_FRACTION + 0.10 * p, 0.45, 0.55);

        return fractionRangeAllocation(
                candidate,
                MODERATE_MIN_FRACTION,
                maxFraction,
                random,
                ResourceAllocationDecision.Mode.MODERATE
        );
    }

    private ResourceAllocationDecision aggressiveRemoteAllocation(
            NodeCandidate candidate,
            double offloadingRatio,
            Random random
    ) {
        double p = normalizeRemoteRatio(offloadingRatio);

        double minFraction =
                clamp(AGGRESSIVE_MIN_FRACTION + 0.10 * p, 0.60, 0.75);

        return fractionRangeAllocation(
                candidate,
                minFraction,
                AGGRESSIVE_MAX_FRACTION,
                random,
                ResourceAllocationDecision.Mode.AGGRESSIVE
        );
    }

    private ResourceAllocationDecision randomRemoteAllocation(
            NodeCandidate candidate,
            Random random
    ) {
        double cpu = randomResource(
                candidate.getAvailableCpu(),
                random
        );

        double bandwidth = randomResource(
                candidate.getAvailableBandwidth(),
                random
        );

        return new ResourceAllocationDecision(
                cpu,
                bandwidth,
                ResourceAllocationDecision.Mode.RANDOM
        );
    }

    private ResourceAllocationDecision smallStepMutation(
            Gene gene,
            NodeCandidate candidate,
            Random random
    ) {
        double cpu = mutateResourceBySmallStep(
                gene.getAllocatedCpu(),
                candidate.getAvailableCpu(),
                random
        );

        double bandwidth = mutateResourceBySmallStep(
                gene.getAllocatedBandwidth(),
                candidate.getAvailableBandwidth(),
                random
        );

        return new ResourceAllocationDecision(
                cpu,
                bandwidth,
                ResourceAllocationDecision.Mode.SMALL_STEP
        );
    }

    private ResourceAllocationDecision fractionRangeAllocation(
            NodeCandidate candidate,
            double minFraction,
            double maxFraction,
            Random random,
            ResourceAllocationDecision.Mode mode
    ) {
        double maxCpu = safeNonNegative(candidate.getAvailableCpu());
        double maxBandwidth = safeNonNegative(candidate.getAvailableBandwidth());

        if (maxCpu <= EPSILON || maxBandwidth <= EPSILON) {
            return new ResourceAllocationDecision(
                    0.0,
                    0.0,
                    mode
            );
        }

        double safeMin = clamp(minFraction, MIN_RESOURCE_FRACTION, 1.0);
        double safeMax = clamp(maxFraction, safeMin, 1.0);

        double cpu =
                randomBetween(maxCpu * safeMin, maxCpu * safeMax, random);

        double bandwidth =
                randomBetween(maxBandwidth * safeMin, maxBandwidth * safeMax, random);

        return new ResourceAllocationDecision(
                clampResource(cpu, maxCpu),
                clampResource(bandwidth, maxBandwidth),
                mode
        );
    }

    private double estimateLocalBranchTime(
            TaskInstance task,
            VehicleSnapshot sourceVehicle,
            double offloadingRatio
    ) {
        return offloadingTimeModel.estimateLocalBranchTime(
                task,
                sourceVehicle,
                normalizeRemoteRatio(offloadingRatio)
        );
    }

    private double estimateRemoteBranchLowerBound(
            TaskInstance task,
            NodeCandidate candidate,
            double offloadingRatio
    ) {
        double p = normalizeRemoteRatio(offloadingRatio);
        return offloadingTimeModel.evaluateRemote(
                task,
                candidate,
                1.0,
                p,
                candidate.getAvailableCpu(),
                candidate.getAvailableBandwidth()
        ).getRemotePartTimeSeconds();
    }

    private double mutateResourceBySmallStep(
            double currentValue,
            double maxAvailable,
            Random random
    ) {
        double max = safeNonNegative(maxAvailable);

        if (max <= EPSILON) {
            return 0.0;
        }

        if (!Double.isFinite(currentValue) || currentValue <= EPSILON) {
            return randomResource(max, random);
        }

        double factor = randomFactor(
                random,
                SMALL_STEP_MIN,
                SMALL_STEP_MAX
        );

        return clampResource(currentValue * factor, max);
    }

    private double randomResource(
            double maxAvailable,
            Random random
    ) {
        double max = safeNonNegative(maxAvailable);

        if (max <= EPSILON) {
            return 0.0;
        }

        double min = max * MIN_RESOURCE_FRACTION;
        return randomBetween(min, max, random);
    }

    private double clampResource(
            double value,
            double maxAvailable
    ) {
        double max = safeNonNegative(maxAvailable);

        if (max <= EPSILON) {
            return 0.0;
        }

        double min = max * MIN_RESOURCE_FRACTION;

        if (!Double.isFinite(value) || value <= 0.0) {
            return min;
        }

        return clamp(value, min, max);
    }

    private double normalizeRemoteRatio(double value) {
        if (!Double.isFinite(value)) {
            return OffloadingRatioPolicy.MIN_REMOTE_OFFLOADING_RATIO;
        }

        return clamp(
                value,
                OffloadingRatioPolicy.MIN_REMOTE_OFFLOADING_RATIO,
                OffloadingRatioPolicy.FULL_OFFLOADING_RATIO
        );
    }

    private double randomFactor(
            Random random,
            double min,
            double max
    ) {
        return min + random.nextDouble() * (max - min);
    }

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

    private double safeNonNegative(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }

        return value;
    }

    private double clamp(
            double value,
            double min,
            double max
    ) {
        if (!Double.isFinite(value)) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }

    private void validateInputs(
            TaskInstance task,
            NodeCandidate candidate,
            Random random
    ) {
        Objects.requireNonNull(task, "task must not be null.");
        Objects.requireNonNull(candidate, "candidate must not be null.");
        Objects.requireNonNull(random, "random must not be null.");
    }
}
