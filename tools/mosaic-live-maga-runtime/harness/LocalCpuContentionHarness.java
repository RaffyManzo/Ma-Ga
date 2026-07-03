package ga.fitness.local;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import ga.fitness.FitnessEvaluator;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LocalResourceUsageBreakdown;
import ga.operators.RepairOperator;
import model.bandwidth.BandwidthPoolType;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Harness deterministico della contesa CPU locale.
 */
public final class LocalCpuContentionHarness {

    private static final double EPSILON = 1.0E-9;
    private static int assertions;

    private LocalCpuContentionHarness() {
    }

    public static void main(String[] args) {
        verifySingleTaskCompatibility();
        verifySameVehicleContentionAndEdf();
        verifyNoCrossVehicleContention();
        verifyPartialOffloadingLocalShare();
        verifyFitnessUsesContendedCompletion();
        verifyRepairRelievesLocalViolation();
        verifyAdaptiveRepairPreservesInvariants();
        verifyAdaptiveRepairDeterminism();
        verifyForcedFallbackMatchesLegacy();
        verifyLocalOnlyInvariant();
        verifyDeterminism();

        System.out.println(
                "LOCAL_CPU_CONTENTION_HARNESS_PASSED assertions="
                        + assertions
        );
    }

    private static void verifySingleTaskCompatibility() {
        SystemSnapshot snapshot = localOnlySnapshot(
                List.of(task("task_0", "veh_0", 200_000_000.0, 0.5)),
                List.of(vehicle("veh_0")),
                List.of(localCandidate("veh_0"))
        );
        Chromosome chromosome = new Chromosome(
                List.of(localGene("task_0", "veh_0"))
        );

        LocalCpuContentionEvaluator.Evaluation evaluation =
                new LocalCpuContentionEvaluator().evaluate(
                        snapshot,
                        chromosome
                );
        LocalCpuContentionEvaluator.TaskResult task =
                evaluation.getTaskResult("task_0");

        requireClose(
                task.getIndependentExecutionTimeSeconds(),
                0.2,
                "single independent time"
        );
        requireClose(
                task.getContendedCompletionTimeSeconds(),
                0.2,
                "single contended time"
        );
        requireClose(task.getDemandRatio(), 0.4, "single demand ratio");
        require(task.isDeadlineRespected(), "single deadline");
        require(!evaluation.hasCpuOverflow(), "single no overflow");
    }

    private static void verifySameVehicleContentionAndEdf() {
        SystemSnapshot snapshot = localOnlySnapshot(
                List.of(
                        task("task_b", "veh_0", 600_000_000.0, 1.0),
                        task("task_a", "veh_0", 600_000_000.0, 1.0)
                ),
                List.of(vehicle("veh_0")),
                List.of(localCandidate("veh_0"))
        );
        Chromosome chromosome = new Chromosome(
                List.of(
                        localGene("task_b", "veh_0"),
                        localGene("task_a", "veh_0")
                )
        );

        LocalCpuContentionEvaluator.Evaluation evaluation =
                new LocalCpuContentionEvaluator().evaluate(
                        snapshot,
                        chromosome
                );
        LocalCpuContentionEvaluator.TaskResult first =
                evaluation.getTaskResult("task_a");
        LocalCpuContentionEvaluator.TaskResult second =
                evaluation.getTaskResult("task_b");
        LocalCpuContentionEvaluator.VehicleResult vehicle =
                evaluation.getVehicleResult("veh_0");

        require(first.getEdfPosition() == 0, "EDF taskId tie first");
        require(second.getEdfPosition() == 1, "EDF taskId tie second");
        requireClose(
                first.getContendedCompletionTimeSeconds(),
                0.6,
                "first contended completion"
        );
        requireClose(
                second.getContendedCompletionTimeSeconds(),
                1.2,
                "second contended completion"
        );
        requireClose(
                second.getContentionDelaySeconds(),
                0.6,
                "second contention delay"
        );
        requireClose(
                vehicle.getMaxDemandRatio(),
                1.2,
                "vehicle max demand ratio"
        );
        requireClose(
                vehicle.getCpuOverflowRatio(),
                0.2,
                "vehicle overflow"
        );
        require(vehicle.getDeadlineViolationCount() == 1,
                "one local deadline violation");
    }

    private static void verifyNoCrossVehicleContention() {
        SystemSnapshot snapshot = localOnlySnapshot(
                List.of(
                        task("task_0", "veh_0", 600_000_000.0, 1.0),
                        task("task_1", "veh_1", 600_000_000.0, 1.0)
                ),
                List.of(vehicle("veh_0"), vehicle("veh_1")),
                List.of(
                        localCandidate("veh_0"),
                        localCandidate("veh_1")
                )
        );
        Chromosome chromosome = new Chromosome(
                List.of(
                        localGene("task_0", "veh_0"),
                        localGene("task_1", "veh_1")
                )
        );

        LocalCpuContentionEvaluator.Evaluation evaluation =
                new LocalCpuContentionEvaluator().evaluate(
                        snapshot,
                        chromosome
                );

        requireClose(
                evaluation
                        .getTaskResult("task_0")
                        .getContendedCompletionTimeSeconds(),
                0.6,
                "vehicle 0 isolated"
        );
        requireClose(
                evaluation
                        .getTaskResult("task_1")
                        .getContendedCompletionTimeSeconds(),
                0.6,
                "vehicle 1 isolated"
        );
        require(!evaluation.hasCpuOverflow(), "no cross-vehicle overflow");
    }

    private static void verifyPartialOffloadingLocalShare() {
        SystemSnapshot snapshot = remoteCapableSnapshot(
                List.of(
                        task("task_local", "veh_0", 600_000_000.0, 1.0),
                        task("task_partial", "veh_0", 600_000_000.0, 2.0)
                )
        );
        Chromosome chromosome = new Chromosome(
                List.of(
                        localGene("task_local", "veh_0"),
                        new Gene(
                                "task_partial",
                                "edge_veh_0",
                                0.5,
                                2_000_000_000.0,
                                100_000_000.0
                        )
                )
        );

        LocalCpuContentionEvaluator.Evaluation evaluation =
                new LocalCpuContentionEvaluator().evaluate(
                        snapshot,
                        chromosome
                );
        LocalCpuContentionEvaluator.TaskResult partial =
                evaluation.getTaskResult("task_partial");

        requireClose(
                partial.getLocalCpuCycles(),
                300_000_000.0,
                "partial local cycles"
        );
        requireClose(
                partial.getIndependentExecutionTimeSeconds(),
                0.3,
                "partial independent time"
        );
        requireClose(
                partial.getContendedCompletionTimeSeconds(),
                0.9,
                "partial contended completion"
        );
    }

    private static void verifyFitnessUsesContendedCompletion() {
        SystemSnapshot snapshot = localOnlySnapshot(
                List.of(
                        task("task_a", "veh_0", 600_000_000.0, 1.0),
                        task("task_b", "veh_0", 600_000_000.0, 1.0)
                ),
                List.of(vehicle("veh_0")),
                List.of(localCandidate("veh_0"))
        );
        Chromosome chromosome = new Chromosome(
                List.of(
                        localGene("task_a", "veh_0"),
                        localGene("task_b", "veh_0")
                )
        );

        FitnessEvaluator evaluator = new FitnessEvaluator(
                MaGaConfig.defaultConfig(GaParameterScalingMode.STATIC)
        );
        EvaluationBreakdown breakdown = evaluator.evaluateDetailed(
                chromosome,
                snapshot
        );

        requireClose(
                breakdown.getCompletionTimeSeconds(),
                1.2,
                "fitness makespan includes local contention"
        );
        require(
                breakdown.getFitness() >= 1.0E12,
                "hard deadline penalty must observe contention"
        );

        GeneEvaluationBreakdown taskB = breakdown
                .getGeneBreakdowns()
                .stream()
                .filter(item -> "task_b".equals(item.getTaskId()))
                .findFirst()
                .orElseThrow();

        requireClose(
                taskB.getIndependentLocalExecutionTimeSeconds(),
                0.6,
                "gene independent local time"
        );
        requireClose(
                taskB.getLocalExecutionTimeSeconds(),
                1.2,
                "gene contended local time"
        );

        LocalResourceUsageBreakdown local = breakdown
                .getLocalResourceUsageBreakdowns()
                .stream()
                .filter(LocalResourceUsageBreakdown::hasLocalWorkload)
                .findFirst()
                .orElseThrow();

        require(local.getLocalTaskCount() == 2, "local task count");
        requireClose(
                local.getMaxIndependentLocalExecutionTimeSeconds(),
                0.6,
                "local independent max"
        );
        requireClose(
                local.getMaxLocalExecutionTimeSeconds(),
                1.2,
                "local contended max"
        );
        requireClose(
                local.getCpuOverflowRatio(),
                0.2,
                "local resource penalty overflow"
        );
        require(local.getDeadlineViolationCount() == 1,
                "local breakdown deadline violations");
    }

    private static void verifyRepairRelievesLocalViolation() {
        SystemSnapshot snapshot = remoteCapableSnapshot(
                List.of(
                        task("task_a", "veh_0", 600_000_000.0, 1.0),
                        task("task_b", "veh_0", 600_000_000.0, 1.0)
                )
        );
        Chromosome original = new Chromosome(
                List.of(
                        localGene("task_a", "veh_0"),
                        localGene("task_b", "veh_0")
                )
        );

        Chromosome repaired = new RepairOperator().repairChromosome(
                original,
                snapshot
        );

        long remoteGenes = repaired.getGenes()
                .stream()
                .filter(gene -> !"local_veh_0".equals(
                        gene.getSelectedCandidateId()
                ))
                .count();

        require(remoteGenes == 1, "repair must change only one task when sufficient");

        Gene remoteGene = repaired.getGenes()
                .stream()
                .filter(gene -> !"local_veh_0".equals(
                        gene.getSelectedCandidateId()
                ))
                .findFirst()
                .orElseThrow();
        require(
                remoteGene.getOffloadingRatio() > 0.0
                        && remoteGene.getOffloadingRatio() < 1.0,
                "repair must prefer the minimum sufficient partial ratio"
        );
        requireClose(
                remoteGene.getOffloadingRatio(),
                0.35,
                "minimum sufficient ratio on the 0.05 repair grid"
        );

        LocalCpuContentionEvaluator.Evaluation evaluation =
                new LocalCpuContentionEvaluator().evaluate(
                        snapshot,
                        repaired
                );

        require(!evaluation.hasCpuOverflow(),
                "repair must remove local CPU overflow");
        require(!evaluation.hasDeadlineViolations(),
                "repair must remove local deadline violation");
    }

    private static void verifyAdaptiveRepairPreservesInvariants() {
        SystemSnapshot snapshot = remoteCapableSnapshot(
                List.of(
                        task("task_a", "veh_0", 650_000_000.0, 0.9),
                        task("task_b", "veh_0", 600_000_000.0, 1.1),
                        task("task_c", "veh_0", 550_000_000.0, 1.3),
                        task("task_d", "veh_0", 500_000_000.0, 1.5)
                )
        );
        Chromosome original = new Chromosome(
                List.of(
                        localGene("task_a", "veh_0"),
                        localGene("task_b", "veh_0"),
                        localGene("task_c", "veh_0"),
                        localGene("task_d", "veh_0")
                )
        );

        Chromosome adaptive = repairWithMode(
                "adaptive",
                false,
                original,
                snapshot
        );
        Chromosome legacy = repairWithMode(
                "legacy",
                false,
                original,
                snapshot
        );

        LocalCpuContentionEvaluator evaluator =
                new LocalCpuContentionEvaluator();
        LocalCpuContentionEvaluator.Evaluation adaptiveEvaluation =
                evaluator.evaluate(snapshot, adaptive);
        LocalCpuContentionEvaluator.Evaluation legacyEvaluation =
                evaluator.evaluate(snapshot, legacy);

        require(
                !adaptiveEvaluation.hasCpuOverflow(),
                "adaptive repair must remove local CPU overflow"
        );
        require(
                !adaptiveEvaluation.hasDeadlineViolations(),
                "adaptive repair must remove local deadline violations"
        );
        require(
                !legacyEvaluation.hasCpuOverflow(),
                "legacy control must remove local CPU overflow"
        );
        require(
                !legacyEvaluation.hasDeadlineViolations(),
                "legacy control must remove local deadline violations"
        );
        require(
                adaptive.getGenes().size() == original.getGenes().size(),
                "adaptive repair must preserve chromosome size"
        );
        require(
                adaptive.getGenes().stream().allMatch(
                        gene -> gene.getTaskId() != null
                                && gene.getSelectedCandidateId() != null
                ),
                "adaptive repair must return complete genes"
        );
    }

    private static void verifyAdaptiveRepairDeterminism() {
        SystemSnapshot snapshot = remoteCapableSnapshot(
                List.of(
                        task("task_a", "veh_0", 700_000_000.0, 1.0),
                        task("task_b", "veh_0", 650_000_000.0, 1.0),
                        task("task_c", "veh_0", 600_000_000.0, 1.2)
                )
        );
        Chromosome original = new Chromosome(
                List.of(
                        localGene("task_a", "veh_0"),
                        localGene("task_b", "veh_0"),
                        localGene("task_c", "veh_0")
                )
        );

        Chromosome first = repairWithMode(
                "adaptive",
                false,
                original,
                snapshot
        );
        Chromosome second = repairWithMode(
                "adaptive",
                false,
                original,
                snapshot
        );

        requireSameGenes(
                first,
                second,
                "adaptive repair determinism"
        );
    }

    private static void verifyForcedFallbackMatchesLegacy() {
        SystemSnapshot snapshot = remoteCapableSnapshot(
                List.of(
                        task("task_a", "veh_0", 600_000_000.0, 1.0),
                        task("task_b", "veh_0", 600_000_000.0, 1.0)
                )
        );
        Chromosome original = new Chromosome(
                List.of(
                        localGene("task_a", "veh_0"),
                        localGene("task_b", "veh_0")
                )
        );

        Chromosome legacy = repairWithMode(
                "legacy",
                false,
                original,
                snapshot
        );
        Chromosome forcedFallback = repairWithMode(
                "adaptive",
                true,
                original,
                snapshot
        );

        requireSameGenes(
                legacy,
                forcedFallback,
                "forced adaptive fallback"
        );
    }

    private static Chromosome repairWithMode(
            String mode,
            boolean forceFallback,
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        String modeProperty = "maga.repair.localContentionMode";
        String fallbackProperty = "maga.repair.forceAdaptiveFallback";
        String previousMode = System.getProperty(modeProperty);
        String previousFallback = System.getProperty(fallbackProperty);

        try {
            System.setProperty(modeProperty, mode);
            if (forceFallback) {
                System.setProperty(fallbackProperty, "true");
            } else {
                System.clearProperty(fallbackProperty);
            }
            return new RepairOperator().repairChromosome(
                    chromosome,
                    snapshot
            );
        } finally {
            restoreProperty(modeProperty, previousMode);
            restoreProperty(fallbackProperty, previousFallback);
        }
    }

    private static void restoreProperty(
            String propertyName,
            String previousValue
    ) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }

    private static void requireSameGenes(
            Chromosome left,
            Chromosome right,
            String message
    ) {
        require(
                left.getGenes().size() == right.getGenes().size(),
                message + " size"
        );

        for (int index = 0; index < left.getGenes().size(); index++) {
            Gene leftGene = left.getGenes().get(index);
            Gene rightGene = right.getGenes().get(index);

            require(
                    leftGene.getTaskId().equals(rightGene.getTaskId()),
                    message + " task " + index
            );
            require(
                    leftGene.getSelectedCandidateId().equals(
                            rightGene.getSelectedCandidateId()
                    ),
                    message + " candidate " + index
            );
            requireClose(
                    leftGene.getOffloadingRatio(),
                    rightGene.getOffloadingRatio(),
                    message + " ratio " + index
            );
            requireClose(
                    leftGene.getAllocatedCpu(),
                    rightGene.getAllocatedCpu(),
                    message + " cpu " + index
            );
            requireClose(
                    leftGene.getAllocatedBandwidth(),
                    rightGene.getAllocatedBandwidth(),
                    message + " bandwidth " + index
            );
        }
    }

    private static void verifyLocalOnlyInvariant() {
        SystemSnapshot snapshot = localOnlySnapshot(
                List.of(
                        task("task_a", "veh_0", 600_000_000.0, 1.0),
                        task("task_b", "veh_0", 600_000_000.0, 1.0)
                ),
                List.of(vehicle("veh_0")),
                List.of(localCandidate("veh_0"))
        );
        Chromosome original = new Chromosome(
                List.of(
                        localGene("task_a", "veh_0"),
                        localGene("task_b", "veh_0")
                )
        );

        Chromosome repaired = new RepairOperator().repairChromosome(
                original,
                snapshot
        );

        require(
                repaired.getGenes().stream().allMatch(
                        gene -> "local_veh_0".equals(
                                gene.getSelectedCandidateId()
                        )
                ),
                "LOCAL_ONLY candidate set must remain local"
        );

        LocalCpuContentionEvaluator.Evaluation evaluation =
                new LocalCpuContentionEvaluator().evaluate(
                        snapshot,
                        repaired
                );
        require(evaluation.hasCpuOverflow(),
                "unavoidable LOCAL_ONLY overflow must remain observable");
    }

    private static void verifyDeterminism() {
        SystemSnapshot snapshot = localOnlySnapshot(
                List.of(
                        task("task_z", "veh_0", 200_000_000.0, 2.0),
                        task("task_a", "veh_0", 200_000_000.0, 2.0),
                        task("task_m", "veh_0", 200_000_000.0, 1.0)
                ),
                List.of(vehicle("veh_0")),
                List.of(localCandidate("veh_0"))
        );
        Chromosome chromosome = new Chromosome(
                List.of(
                        localGene("task_z", "veh_0"),
                        localGene("task_a", "veh_0"),
                        localGene("task_m", "veh_0")
                )
        );

        LocalCpuContentionEvaluator evaluator =
                new LocalCpuContentionEvaluator();
        LocalCpuContentionEvaluator.Evaluation first =
                evaluator.evaluate(snapshot, chromosome);
        LocalCpuContentionEvaluator.Evaluation second =
                evaluator.evaluate(snapshot, chromosome);

        for (String taskId : List.of("task_m", "task_a", "task_z")) {
            LocalCpuContentionEvaluator.TaskResult firstResult =
                    first.getTaskResult(taskId);
            LocalCpuContentionEvaluator.TaskResult secondResult =
                    second.getTaskResult(taskId);

            require(
                    firstResult.getEdfPosition()
                            == secondResult.getEdfPosition(),
                    "deterministic EDF position " + taskId
            );
            requireClose(
                    firstResult.getContendedCompletionTimeSeconds(),
                    secondResult.getContendedCompletionTimeSeconds(),
                    "deterministic completion " + taskId
            );
        }

        require(
                first.getTaskResult("task_m").getEdfPosition() == 0,
                "earlier deadline first"
        );
        require(
                first.getTaskResult("task_a").getEdfPosition() == 1,
                "taskId tie a before z"
        );
        require(
                first.getTaskResult("task_z").getEdfPosition() == 2,
                "taskId tie z last"
        );
    }

    private static SystemSnapshot localOnlySnapshot(
            List<TaskInstance> tasks,
            List<VehicleSnapshot> vehicles,
            List<NodeCandidate> candidates
    ) {
        return new SystemSnapshot(
                "snapshot_local",
                0.0,
                vehicles,
                tasks,
                candidates
        );
    }

    private static SystemSnapshot remoteCapableSnapshot(
            List<TaskInstance> tasks
    ) {
        List<NodeCandidate> candidates = new ArrayList<>();
        candidates.add(localCandidate("veh_0"));
        candidates.add(new NodeCandidate(
                "edge_veh_0",
                "veh_0",
                "edge_0",
                NodeType.EDGE,
                5_000_000_000.0,
                100_000_000.0,
                0.01,
                0.0,
                0.0,
                1_000.0
        ));

        return new SystemSnapshot(
                "snapshot_remote",
                0.0,
                List.of(vehicle("veh_0")),
                tasks,
                candidates,
                List.of(new AccessGatewaySnapshot(
                        "rsu_0",
                        "RSU",
                        0.0,
                        0.0,
                        1_000.0,
                        "pool_rsu_0"
                )),
                List.of(new AccessLinkSnapshot(
                        "link_veh_0_rsu_0",
                        "veh_0",
                        "rsu_0",
                        true,
                        true
                )),
                List.of(new BandwidthPoolSnapshot(
                        "pool_rsu_0",
                        BandwidthPoolType.GATEWAY,
                        100_000_000.0
                ))
        );
    }

    private static TaskInstance task(
            String taskId,
            String vehicleId,
            double cpuCycles,
            double deadlineSeconds
    ) {
        return new TaskInstance(
                taskId,
                vehicleId,
                1_000_000.0,
                100_000.0,
                cpuCycles,
                deadlineSeconds
        );
    }

    private static VehicleSnapshot vehicle(String vehicleId) {
        return new VehicleSnapshot(
                vehicleId,
                0.0,
                0.0,
                1.0,
                1_000_000_000.0
        );
    }

    private static NodeCandidate localCandidate(String vehicleId) {
        return new NodeCandidate(
                "local_" + vehicleId,
                vehicleId,
                vehicleId,
                NodeType.LOCAL,
                1_000_000_000.0,
                0.0,
                0.0,
                null,
                null,
                null
        );
    }

    private static Gene localGene(String taskId, String vehicleId) {
        return new Gene(
                taskId,
                "local_" + vehicleId,
                0.0,
                1_000_000_000.0,
                0.0
        );
    }

    private static void requireClose(
            double actual,
            double expected,
            String message
    ) {
        assertions++;
        if (Math.abs(actual - expected) > EPSILON) {
            throw new AssertionError(
                    message
                            + ": expected=" + expected
                            + " actual=" + actual
            );
        }
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
