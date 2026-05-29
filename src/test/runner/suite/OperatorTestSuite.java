package test.runner.suite;

import ga.operators.MutationOperator;
import ga.operators.PopulationInitializer;
import ga.operators.RepairOperator;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.snapshot.SystemSnapshot;
import test.runner.core.ManualTestCase;
import test.runner.core.ManualTestSuite;
import test.runner.support.TestAssertions;
import test.runner.support.TestLookups;
import test.runner.support.TestSnapshots;

import java.util.List;
import java.util.Random;

/**
 * Test manuali dedicati agli operatori genetici.
 */
public final class OperatorTestSuite implements ManualTestSuite {

    @Override
    public String getName() {
        return "Genetic operators";
    }

    @Override
    public List<ManualTestCase> getTests() {
        return List.of(
                ManualTestCase.of(
                        "RepairOperator repairs invalid candidate",
                        OperatorTestSuite::testRepairOperatorRepairsInvalidCandidate
                ),
                ManualTestCase.of(
                        "PopulationInitializer uses only valid candidates",
                        OperatorTestSuite::testPopulationInitializerUsesOnlyValidCandidates
                ),
                ManualTestCase.of(
                        "MutationOperator uses only valid candidates",
                        OperatorTestSuite::testMutationOperatorUsesOnlyValidCandidates
                )
        );
    }

    private static void testRepairOperatorRepairsInvalidCandidate() {
        SystemSnapshot snapshot = TestSnapshots.createTwoVehicleSnapshot();

        Gene invalidGene = new Gene(
                "task_001",
                "local_vehicle_002",
                0.0,
                650.0,
                0.0
        );

        Chromosome invalidChromosome = new Chromosome(List.of(invalidGene));

        RepairOperator repairOperator = new RepairOperator();
        Chromosome repaired = repairOperator.repairChromosome(
                invalidChromosome,
                snapshot
        );

        Gene repairedGene = repaired.getGenes().get(0);

        TestAssertions.assertEquals(
                "local_vehicle_001",
                repairedGene.getSelectedCandidateId(),
                "Repair should choose local_vehicle_001 for task_001."
        );
    }

    private static void testPopulationInitializerUsesOnlyValidCandidates() {
        SystemSnapshot snapshot = TestSnapshots.createTwoVehicleSnapshot();

        PopulationInitializer initializer = new PopulationInitializer(
                new Random(42),
                new RepairOperator()
        );

        List<Chromosome> population =
                initializer.createInitialPopulation(snapshot, 50);

        for (Chromosome chromosome : population) {
            TestLookups.assertChromosomeUsesOnlyValidCandidates(
                    chromosome,
                    snapshot
            );
        }
    }

    private static void testMutationOperatorUsesOnlyValidCandidates() {
        SystemSnapshot snapshot = TestSnapshots.createTwoVehicleSnapshot();

        Chromosome chromosome = new Chromosome(List.of(
                new Gene(
                        "task_001",
                        "local_vehicle_001",
                        0.0,
                        500.0,
                        0.0
                ),
                new Gene(
                        "task_002",
                        "local_vehicle_002",
                        0.0,
                        650.0,
                        0.0
                )
        ));

        MutationOperator mutationOperator = new MutationOperator(
                new Random(7)
        );

        Chromosome mutated = mutationOperator.mutate(
                chromosome,
                snapshot,
                1.0
        );

        TestLookups.assertChromosomeUsesOnlyValidCandidates(
                mutated,
                snapshot
        );
    }
}
