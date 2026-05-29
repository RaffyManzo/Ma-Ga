package test.runner.suite;

import config.MaGaConfig;
import ga.fitness.FitnessEvaluator;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.snapshot.SystemSnapshot;
import test.runner.core.ManualTestCase;
import test.runner.core.ManualTestSuite;
import test.runner.support.TestAssertions;
import test.runner.support.TestLookups;
import test.runner.support.TestSnapshots;

import java.util.List;

/**
 * Test manuali dedicati ai calcoli di fitness e alle penalita.
 */
public final class FitnessEvaluatorTestSuite implements ManualTestSuite {

    @Override
    public String getName() {
        return "Fitness evaluator";
    }

    @Override
    public List<ManualTestCase> getTests() {
        return List.of(
                ManualTestCase.of(
                        "local execution time",
                        FitnessEvaluatorTestSuite::testLocalExecutionTime
                ),
                ManualTestCase.of(
                        "full offloading time",
                        FitnessEvaluatorTestSuite::testFullOffloadingTime
                ),
                ManualTestCase.of(
                        "partial offloading time",
                        FitnessEvaluatorTestSuite::testPartialOffloadingTime
                ),
                ManualTestCase.of(
                        "detects insufficient coverage",
                        FitnessEvaluatorTestSuite::testCoverageInsufficientIsDetected
                ),
                ManualTestCase.of(
                        "penalizes invalid candidate for source vehicle",
                        FitnessEvaluatorTestSuite::testInvalidCandidateForSourceVehicleIsPenalized
                ),
                ManualTestCase.of(
                        "aggregates CPU by executionNodeId",
                        FitnessEvaluatorTestSuite::testCpuAggregatedByExecutionNodeId
                ),
                ManualTestCase.of(
                        "tracks bandwidth by candidate/link",
                        FitnessEvaluatorTestSuite::testBandwidthTrackedByCandidateId
                )
        );
    }

    private static void testLocalExecutionTime() {
        SystemSnapshot snapshot = TestSnapshots.createOneVehicleSnapshot();

        Gene gene = new Gene(
                "task_001",
                "local_vehicle_001",
                0.0,
                500.0,
                0.0
        );

        GeneEvaluationBreakdown geneBreakdown =
                evaluateSingleGene(gene, snapshot);

        TestAssertions.assertAlmostEquals(
                2.0,
                geneBreakdown.getCompletionTimeSeconds(),
                "Local completion time should be 2.0 seconds."
        );

        TestAssertions.assertAlmostEquals(
                1000.0,
                geneBreakdown.getLocalCpuCycles(),
                "All CPU cycles should remain local."
        );
    }

    private static void testFullOffloadingTime() {
        SystemSnapshot snapshot = TestSnapshots.createOneVehicleSnapshot();

        Gene gene = new Gene(
                "task_001",
                "edge_001_for_vehicle_001",
                1.0,
                1000.0,
                100.0
        );

        GeneEvaluationBreakdown geneBreakdown =
                evaluateSingleGene(gene, snapshot);

        TestAssertions.assertAlmostEquals(
                2.3,
                geneBreakdown.getCompletionTimeSeconds(),
                "Full offloading completion time should be 2.3 seconds."
        );

        TestAssertions.assertAlmostEquals(
                0.0,
                geneBreakdown.getLocalCpuCycles(),
                "No CPU cycles should remain local with p_i = 1."
        );

        TestAssertions.assertAlmostEquals(
                1.3,
                geneBreakdown.getCommunicationLatencySeconds(),
                "Communication latency should be upload + download + base latency."
        );
    }

    private static void testPartialOffloadingTime() {
        SystemSnapshot snapshot = TestSnapshots.createOneVehicleSnapshot();

        Gene gene = new Gene(
                "task_001",
                "edge_001_for_vehicle_001",
                0.5,
                1000.0,
                100.0
        );

        GeneEvaluationBreakdown geneBreakdown =
                evaluateSingleGene(gene, snapshot);

        TestAssertions.assertAlmostEquals(
                500.0,
                geneBreakdown.getLocalCpuCycles(),
                "Partial offloading should keep 500 local cycles."
        );

        TestAssertions.assertAlmostEquals(
                1.0,
                geneBreakdown.getLocalExecutionTimeSeconds(),
                "Local part should take 1.0 second."
        );

        TestAssertions.assertAlmostEquals(
                1.3,
                geneBreakdown.getRemotePartTimeSeconds(),
                "Remote part should take 1.3 seconds."
        );

        TestAssertions.assertAlmostEquals(
                1.3,
                geneBreakdown.getCompletionTimeSeconds(),
                "Completion time should be max(local, remote)."
        );
    }

    private static void testCoverageInsufficientIsDetected() {
        SystemSnapshot snapshot = TestSnapshots.createCoveragePressureSnapshot();

        Gene gene = new Gene(
                "task_001",
                "edge_short_coverage_for_vehicle_001",
                1.0,
                400.0,
                100.0
        );

        GeneEvaluationBreakdown geneBreakdown =
                evaluateSingleGene(gene, snapshot);

        TestAssertions.assertTrue(
                !geneBreakdown.isCoverageSufficient(),
                "Coverage should be insufficient because completion time exceeds coverage time."
        );

        TestAssertions.assertTrue(
                geneBreakdown.getMobilityPenalty() > 0.0,
                "Mobility penalty should be greater than 0 when coverage is insufficient."
        );

        TestAssertions.assertTrue(
                geneBreakdown.getCompletionTimeSeconds()
                        > geneBreakdown.getCoverageTimeSeconds(),
                "Completion time should be greater than coverage time."
        );
    }

    private static void testInvalidCandidateForSourceVehicleIsPenalized() {
        SystemSnapshot snapshot = TestSnapshots.createTwoVehicleSnapshot();

        Gene invalidGene = new Gene(
                "task_001",
                "local_vehicle_002",
                0.0,
                650.0,
                0.0
        );

        EvaluationBreakdown breakdown =
                evaluator().evaluateDetailed(
                        new Chromosome(List.of(invalidGene)),
                        snapshot
                );

        TestAssertions.assertTrue(
                breakdown.getResourcePenalty() > 0.0,
                "Invalid candidate should generate a resource/constraint penalty."
        );
    }

    private static void testCpuAggregatedByExecutionNodeId() {
        SystemSnapshot snapshot = TestSnapshots.createTwoVehicleSnapshot();

        Chromosome chromosome = new Chromosome(List.of(
                new Gene(
                        "task_001",
                        "edge_001_for_vehicle_001",
                        1.0,
                        1000.0,
                        100.0
                ),
                new Gene(
                        "task_002",
                        "edge_001_for_vehicle_002",
                        1.0,
                        1500.0,
                        120.0
                )
        ));

        EvaluationBreakdown breakdown =
                evaluator().evaluateDetailed(chromosome, snapshot);

        ExecutionNodeResourceUsageBreakdown edgeUsage =
                TestLookups.findExecutionNodeUsage(breakdown, "edge_001");

        TestAssertions.assertAlmostEquals(
                2500.0,
                edgeUsage.getUsedCpu(),
                "CPU on edge_001 should aggregate both genes."
        );
    }

    private static void testBandwidthTrackedByCandidateId() {
        SystemSnapshot snapshot = TestSnapshots.createTwoVehicleSnapshot();

        Chromosome chromosome = new Chromosome(List.of(
                new Gene(
                        "task_001",
                        "edge_001_for_vehicle_001",
                        1.0,
                        1000.0,
                        100.0
                ),
                new Gene(
                        "task_002",
                        "edge_001_for_vehicle_002",
                        1.0,
                        1500.0,
                        120.0
                )
        ));

        EvaluationBreakdown breakdown =
                evaluator().evaluateDetailed(chromosome, snapshot);

        LinkBandwidthUsageBreakdown linkVehicle1 =
                TestLookups.findLinkUsage(
                        breakdown,
                        "edge_001_for_vehicle_001"
                );

        LinkBandwidthUsageBreakdown linkVehicle2 =
                TestLookups.findLinkUsage(
                        breakdown,
                        "edge_001_for_vehicle_002"
                );

        TestAssertions.assertAlmostEquals(
                100.0,
                linkVehicle1.getUsedBandwidth(),
                "Bandwidth for vehicle_001 link should be 100."
        );

        TestAssertions.assertAlmostEquals(
                120.0,
                linkVehicle2.getUsedBandwidth(),
                "Bandwidth for vehicle_002 link should be 120."
        );
    }

    private static GeneEvaluationBreakdown evaluateSingleGene(
            Gene gene,
            SystemSnapshot snapshot
    ) {
        EvaluationBreakdown breakdown =
                evaluator().evaluateDetailed(
                        new Chromosome(List.of(gene)),
                        snapshot
                );

        return breakdown.getGeneBreakdowns().get(0);
    }

    private static FitnessEvaluator evaluator() {
        return new FitnessEvaluator(MaGaConfig.defaultConfig());
    }
}
