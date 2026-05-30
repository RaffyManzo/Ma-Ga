package test.runner.suite;

import config.MaGaConfig;
import config.fitness.FitnessWeights;
import config.fitness.NormalizationConfig;
import config.fitness.PenaltyConfig;
import config.ga.GeneticAlgorithmConfig;
import config.mobility.MobilityConfig;
import config.window.TemporalWindowConfig;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;
import test.runner.core.ManualTestCase;
import test.runner.core.ManualTestSuite;
import test.runner.support.TestAssertions;
import window.population.PopulationAdapter;
import window.population.PopulationReuseMode;

import java.util.List;
import java.util.Random;

/**
 * Test manuali dedicati al riuso della popolazione tra finestre temporali.
 */
public final class PopulationAdapterTestSuite implements ManualTestSuite {

    @Override
    public String getName() {
        return "Population adapter";
    }

    @Override
    public List<ManualTestCase> getTests() {
        return List.of(
                ManualTestCase.of(
                        "re-scores reused population",
                        PopulationAdapterTestSuite::testReusesCurrentFitnessForSelection
                ),
                ManualTestCase.of(
                        "uses configured MobilityConfig",
                        PopulationAdapterTestSuite::testUsesConfiguredMobilityForRepair
                )
        );
    }

    private static void testReusesCurrentFitnessForSelection() {
        SystemSnapshot snapshot = createCloudChoiceSnapshot();

        Chromosome oldBestButCurrentlySlow = new Chromosome(List.of(
                cloudGene()
        ));
        oldBestButCurrentlySlow.setFitness(0.0);

        Chromosome oldWorstButCurrentlyFast = new Chromosome(List.of(
                localGene()
        ));
        oldWorstButCurrentlyFast.setFitness(9999.0);

        PopulationAdapter adapter = new PopulationAdapter(
                TemporalWindowConfig.defaultConfig(),
                timeOnlyConfig(MobilityConfig.defaultConfig()),
                new Random(11)
        );

        List<Chromosome> adapted = adapter.adaptPopulation(
                List.of(oldBestButCurrentlySlow, oldWorstButCurrentlyFast),
                snapshot,
                PopulationReuseMode.WARM_START,
                1
        );

        Gene keptGene = adapted.get(0).getGenes().get(0);

        TestAssertions.assertEquals(
                "local_vehicle_001",
                keptGene.getSelectedCandidateId(),
                "Warm start should keep the chromosome with the best current fitness."
        );

        TestAssertions.assertTrue(
                adapted.get(0).getFitness() < 9999.0,
                "Adapted chromosome should have a freshly evaluated fitness."
        );
    }

    private static void testUsesConfiguredMobilityForRepair() {
        SystemSnapshot snapshot = createCloudChoiceSnapshot();

        MobilityConfig strictCloudCoverage = new MobilityConfig(
                0.1,
                250.0,
                300.0,
                0.5,
                300.0
        );

        Chromosome cloudChromosome = new Chromosome(List.of(
                cloudGene()
        ));
        cloudChromosome.setFitness(0.0);

        PopulationAdapter adapter = new PopulationAdapter(
                TemporalWindowConfig.defaultConfig(),
                timeOnlyConfig(strictCloudCoverage),
                new Random(13)
        );

        List<Chromosome> adapted = adapter.adaptPopulation(
                List.of(cloudChromosome),
                snapshot,
                PopulationReuseMode.WARM_START,
                1
        );

        Gene repairedGene = adapted.get(0).getGenes().get(0);

        TestAssertions.assertEquals(
                "local_vehicle_001",
                repairedGene.getSelectedCandidateId(),
                "Population reuse repair should use the MobilityConfig from MaGaConfig."
        );
    }

    private static MaGaConfig timeOnlyConfig(MobilityConfig mobilityConfig) {
        return new MaGaConfig(
                new FitnessWeights(1.0, 0.0, 0.0, 0.0),
                PenaltyConfig.defaultConfig(),
                NormalizationConfig.neutral(),
                new GeneticAlgorithmConfig(
                        2,
                        1,
                        0.80,
                        0.10,
                        1,
                        1,
                        1.0E-6,
                        17L
                ),
                mobilityConfig
        );
    }

    private static SystemSnapshot createCloudChoiceSnapshot() {
        List<VehicleSnapshot> vehicles = List.of(
                new VehicleSnapshot(
                        "vehicle_001",
                        0.0,
                        0.0,
                        10.0,
                        1000.0
                )
        );

        List<TaskInstance> tasks = List.of(
                new TaskInstance(
                        "task_001",
                        "vehicle_001",
                        1000.0,
                        1000.0,
                        1000.0,
                        1000.0
                )
        );

        List<NodeCandidate> candidates = List.of(
                new NodeCandidate(
                        "local_vehicle_001",
                        "vehicle_001",
                        "vehicle_001",
                        NodeType.LOCAL,
                        1000.0,
                        0.0,
                        0.0,
                        null,
                        null,
                        null
                ),
                new NodeCandidate(
                        "cloud_001_for_vehicle_001",
                        "vehicle_001",
                        "cloud_001",
                        NodeType.CLOUD,
                        100.0,
                        10.0,
                        0.0,
                        null,
                        null,
                        null
                )
        );

        return new SystemSnapshot(
                "population_adapter_snapshot",
                0.0,
                vehicles,
                tasks,
                candidates
        );
    }

    private static Gene localGene() {
        return new Gene(
                "task_001",
                "local_vehicle_001",
                0.0,
                1000.0,
                0.0
        );
    }

    private static Gene cloudGene() {
        return new Gene(
                "task_001",
                "cloud_001_for_vehicle_001",
                1.0,
                100.0,
                10.0
        );
    }
}
