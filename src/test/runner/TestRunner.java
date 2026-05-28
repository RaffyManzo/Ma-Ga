package test.runner;

import config.MaGaConfig;
import ga.fitness.FitnessEvaluator;
import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.GeneEvaluationBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import ga.fitness.breakdown.LocalResourceUsageBreakdown;
import ga.operators.MutationOperator;
import ga.operators.PopulationInitializer;
import ga.operators.RepairOperator;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;
import validation.snapshot.SnapshotValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Test manuali del prototipo MA-GA.
 *
 * Questa classe non usa JUnit.
 * Si avvia direttamente da IntelliJ come normale main.
 *
 * Se un test fallisce, viene lanciato AssertionError.
 * Se tutti i test passano, viene stampato un riepilogo in console.
 */
public final class TestRunner {

    private static final double EPSILON = 1.0E-6;

    /**
     * Punto di ingresso dei test manuali.
     */
    public static void main(String[] args) {
        runTest("SnapshotValidator accepts valid source-aware snapshot",
                TestRunner::testSnapshotValidatorAcceptsValidSnapshot);

        runTest("SnapshotValidator rejects duplicated candidateId",
                TestRunner::testSnapshotValidatorRejectsDuplicatedCandidateId);

        runTest("FitnessEvaluator local execution time",
                TestRunner::testLocalExecutionTime);

        runTest("FitnessEvaluator full offloading time",
                TestRunner::testFullOffloadingTime);

        runTest("FitnessEvaluator partial offloading time",
                TestRunner::testPartialOffloadingTime);

        runTest("FitnessEvaluator detects insufficient coverage",
                TestRunner::testCoverageInsufficientIsDetected);

        runTest("FitnessEvaluator penalizes invalid candidate for source vehicle",
                TestRunner::testInvalidCandidateForSourceVehicleIsPenalized);

        runTest("FitnessEvaluator aggregates CPU by executionNodeId",
                TestRunner::testCpuAggregatedByExecutionNodeId);

        runTest("FitnessEvaluator tracks bandwidth by candidate/link",
                TestRunner::testBandwidthTrackedByCandidateId);

        runTest("RepairOperator repairs invalid candidate",
                TestRunner::testRepairOperatorRepairsInvalidCandidate);

        runTest("PopulationInitializer uses only valid candidates",
                TestRunner::testPopulationInitializerUsesOnlyValidCandidates);

        runTest("MutationOperator uses only valid candidates",
                TestRunner::testMutationOperatorUsesOnlyValidCandidates);

        System.out.println();
        System.out.println("============================================================");
        System.out.println("ALL MANUAL TESTS PASSED");
        System.out.println("============================================================");
    }

    /**
     * Esegue un singolo test e stampa l'esito.
     */
    private static void runTest(String name, Runnable test) {
        try {
            test.run();
            System.out.println("[PASSED] " + name);
        } catch (Throwable error) {
            System.out.println("[FAILED] " + name);
            throw error;
        }
    }

    /**
     * Verifica che uno snapshot valido passi la validazione.
     */
    private static void testSnapshotValidatorAcceptsValidSnapshot() {
        SystemSnapshot snapshot = createTwoVehicleSnapshot();

        SnapshotValidator validator = new SnapshotValidator();
        validator.validate(snapshot);
    }

    /**
     * Verifica che FitnessEvaluator rilevi correttamente una copertura insufficiente.
     *
     * Il test forza manualmente l'uso di un candidato EDGE con coverageTimeSeconds molto basso.
     *
     * Scenario:
     *
     * - task_001 nasce da vehicle_001;
     * - il gene sceglie edge_short_coverage_for_vehicle_001;
     * - p_i = 1.0, quindi full offloading;
     * - il tempo di completamento remoto risulta maggiore del tempo di copertura;
     * - quindi coverageSufficient deve essere false;
     * - mobilityPenalty deve essere maggiore di 0.
     *
     * Questo test è necessario perché nello snapshot_05 il GA può evitare il candidato
     * con copertura breve scegliendo il cloud. Qui invece forziamo il candidato rischioso
     * per controllare direttamente la logica mobility-aware.
     */
    private static void testCoverageInsufficientIsDetected() {
        SystemSnapshot snapshot = createCoveragePressureSnapshotForTest();

        Gene gene = new Gene(
                "task_001",
                "edge_short_coverage_for_vehicle_001",
                1.0,
                400.0,
                100.0
        );

        Chromosome chromosome = new Chromosome(List.of(gene));

        EvaluationBreakdown breakdown =
                evaluator().evaluateDetailed(chromosome, snapshot);

        GeneEvaluationBreakdown geneBreakdown =
                breakdown.getGeneBreakdowns().get(0);

        assertTrue(
                !geneBreakdown.isCoverageSufficient(),
                "Coverage should be insufficient because completion time exceeds coverage time."
        );

        assertTrue(
                geneBreakdown.getMobilityPenalty() > 0.0,
                "Mobility penalty should be greater than 0 when coverage is insufficient."
        );

        assertTrue(
                geneBreakdown.getCompletionTimeSeconds() > geneBreakdown.getCoverageTimeSeconds(),
                "Completion time should be greater than coverage time."
        );
    }

    /**
     * Crea un candidato LOCAL per il veicolo indicato.
     */
    private static NodeCandidate localCandidate(
            String vehicleId,
            double localCpu
    ) {
        return new NodeCandidate(
                "local_" + vehicleId,
                vehicleId,
                vehicleId,
                NodeType.LOCAL,
                localCpu,
                0.0,
                0.0,
                null,
                null,
                null
        );
    }

    /**
     * Crea un candidato EDGE con geometria di copertura esplicita.
     */
    private static NodeCandidate edgeCandidate(
            String candidateId,
            String sourceVehicleId,
            String executionNodeId,
            double availableCpu,
            double availableBandwidth,
            double baseLatencySeconds,
            double nodeX,
            double nodeY,
            double coverageRadiusMeters
    ) {
        return new NodeCandidate(
                candidateId,
                sourceVehicleId,
                executionNodeId,
                NodeType.EDGE,
                availableCpu,
                availableBandwidth,
                baseLatencySeconds,
                nodeX,
                nodeY,
                coverageRadiusMeters
        );
    }

    /**
     * Crea un candidato CLOUD.
     *
     * La copertura cloud non viene descritta geometricamente.
     * Sarà gestita da MobilityConfig/CoverageEstimator.
     */
    private static NodeCandidate cloudCandidate(
            String candidateId,
            String sourceVehicleId,
            String executionNodeId,
            double availableCpu,
            double availableBandwidth,
            double baseLatencySeconds
    ) {
        return new NodeCandidate(
                candidateId,
                sourceVehicleId,
                executionNodeId,
                NodeType.CLOUD,
                availableCpu,
                availableBandwidth,
                baseLatencySeconds,
                null,
                null,
                null
        );
    }

    /**
     * Crea un candidato V2V.
     *
     * La posizione del target non viene duplicata nel candidato.
     * CoverageEstimator la ricava dallo snapshot tramite executionNodeId.
     */
    private static NodeCandidate vehicleCandidate(
            String candidateId,
            String sourceVehicleId,
            String targetVehicleId,
            double availableCpu,
            double availableBandwidth,
            double baseLatencySeconds
    ) {
        return new NodeCandidate(
                candidateId,
                sourceVehicleId,
                targetVehicleId,
                NodeType.VEHICLE,
                availableCpu,
                availableBandwidth,
                baseLatencySeconds,
                null,
                null,
                null
        );
    }

    /**
     * Verifica che due candidati con lo stesso candidateId vengano rifiutati.
     */
    private static void testSnapshotValidatorRejectsDuplicatedCandidateId() {
        List<VehicleSnapshot> vehicles = List.of(
                new VehicleSnapshot("vehicle_001", 0.0, 0.0, 10.0, 500.0)
        );

        List<TaskInstance> tasks = List.of(
                new TaskInstance(
                        "task_001",
                        "vehicle_001",
                        100.0,
                        20.0,
                        1000.0,
                        10.0
                )
        );

        List<NodeCandidate> candidates = List.of(
                localCandidate("vehicle_001", 500.0),
                localCandidate("vehicle_001", 500.0)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                "duplicated_candidate_snapshot",
                0.0,
                vehicles,
                tasks,
                candidates
        );

        assertThrows(
                () -> new SnapshotValidator().validate(snapshot),
                "Duplicated candidateId should be rejected."
        );
    }

    /**
     * Verifica il calcolo del tempo locale:
     *
     * cpuCycles = 1000
     * localCpu = 500
     *
     * T_local = 1000 / 500 = 2 s
     */
    private static void testLocalExecutionTime() {
        SystemSnapshot snapshot = createOneVehicleSnapshot();

        Gene gene = new Gene(
                "task_001",
                "local_vehicle_001",
                0.0,
                500.0,
                0.0
        );

        Chromosome chromosome = new Chromosome(List.of(gene));

        EvaluationBreakdown breakdown =
                evaluator().evaluateDetailed(chromosome, snapshot);

        GeneEvaluationBreakdown geneBreakdown =
                breakdown.getGeneBreakdowns().get(0);

        assertAlmostEquals(
                2.0,
                geneBreakdown.getCompletionTimeSeconds(),
                "Local completion time should be 2.0 seconds."
        );

        assertAlmostEquals(
                1000.0,
                geneBreakdown.getLocalCpuCycles(),
                "All CPU cycles should remain local."
        );
    }

    /**
     * Verifica il calcolo del full offloading.
     *
     * Parametri:
     * input = 100 bit
     * output = 20 bit
     * cycles = 1000
     * bandwidth = 100 bit/s
     * remote CPU = 1000 cycles/s
     * base latency = 0.1 s
     *
     * upload = 100 / 100 = 1.0
     * remote execution = 1000 / 1000 = 1.0
     * download = 20 / 100 = 0.2
     * total = 1.0 + 1.0 + 0.2 + 0.1 = 2.3
     */
    private static void testFullOffloadingTime() {
        SystemSnapshot snapshot = createOneVehicleSnapshot();

        Gene gene = new Gene(
                "task_001",
                "edge_001_for_vehicle_001",
                1.0,
                1000.0,
                100.0
        );

        Chromosome chromosome = new Chromosome(List.of(gene));

        EvaluationBreakdown breakdown =
                evaluator().evaluateDetailed(chromosome, snapshot);

        GeneEvaluationBreakdown geneBreakdown =
                breakdown.getGeneBreakdowns().get(0);

        assertAlmostEquals(
                2.3,
                geneBreakdown.getCompletionTimeSeconds(),
                "Full offloading completion time should be 2.3 seconds."
        );

        assertAlmostEquals(
                0.0,
                geneBreakdown.getLocalCpuCycles(),
                "No CPU cycles should remain local with p_i = 1."
        );

        assertAlmostEquals(
                1.3,
                geneBreakdown.getCommunicationLatencySeconds(),
                "Communication latency should be upload + download + base latency."
        );
    }

    /**
     * Verifica il partial offloading.
     *
     * p = 0.5
     *
     * local cycles = (1 - 0.5) * 1000 = 500
     * local time = 500 / 500 = 1.0
     *
     * upload = 0.5 * 100 / 100 = 0.5
     * remote execution = 0.5 * 1000 / 1000 = 0.5
     * download = 20 / 100 = 0.2
     * base latency = 0.1
     * remote part = 1.3
     *
     * completion = max(1.0, 1.3) = 1.3
     */
    private static void testPartialOffloadingTime() {
        SystemSnapshot snapshot = createOneVehicleSnapshot();

        Gene gene = new Gene(
                "task_001",
                "edge_001_for_vehicle_001",
                0.5,
                1000.0,
                100.0
        );

        Chromosome chromosome = new Chromosome(List.of(gene));

        EvaluationBreakdown breakdown =
                evaluator().evaluateDetailed(chromosome, snapshot);

        GeneEvaluationBreakdown geneBreakdown =
                breakdown.getGeneBreakdowns().get(0);

        assertAlmostEquals(
                500.0,
                geneBreakdown.getLocalCpuCycles(),
                "Partial offloading should keep 500 local cycles."
        );

        assertAlmostEquals(
                1.0,
                geneBreakdown.getLocalExecutionTimeSeconds(),
                "Local part should take 1.0 second."
        );

        assertAlmostEquals(
                1.3,
                geneBreakdown.getRemotePartTimeSeconds(),
                "Remote part should take 1.3 seconds."
        );

        assertAlmostEquals(
                1.3,
                geneBreakdown.getCompletionTimeSeconds(),
                "Completion time should be max(local, remote)."
        );
    }

    /**
     * Verifica che un gene con candidato non valido per il sourceVehicleId
     * venga penalizzato.
     *
     * task_001 nasce da vehicle_001.
     * Il gene seleziona local_vehicle_002.
     *
     * Questo non è valido.
     */
    private static void testInvalidCandidateForSourceVehicleIsPenalized() {
        SystemSnapshot snapshot = createTwoVehicleSnapshot();

        Gene invalidGene = new Gene(
                "task_001",
                "local_vehicle_002",
                0.0,
                650.0,
                0.0
        );

        Chromosome chromosome = new Chromosome(List.of(invalidGene));

        EvaluationBreakdown breakdown =
                evaluator().evaluateDetailed(chromosome, snapshot);

        assertTrue(
                breakdown.getResourcePenalty() > 0.0,
                "Invalid candidate should generate a resource/constraint penalty."
        );
    }

    /**
     * Verifica che la CPU remota venga aggregata per executionNodeId.
     *
     * Due candidati diversi:
     * - edge_001_for_vehicle_001
     * - edge_001_for_vehicle_002
     *
     * puntano allo stesso executionNodeId:
     * - edge_001
     *
     * Quindi la CPU usata deve sommarsi.
     */
    private static void testCpuAggregatedByExecutionNodeId() {
        SystemSnapshot snapshot = createTwoVehicleSnapshot();

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
                findExecutionNodeUsage(breakdown, "edge_001");

        assertAlmostEquals(
                2500.0,
                edgeUsage.getUsedCpu(),
                "CPU on edge_001 should aggregate both genes."
        );
    }

    /**
     * Verifica che la banda venga tracciata per candidateId/link.
     *
     * Ogni candidateId rappresenta un link sorgente-destinazione.
     */
    private static void testBandwidthTrackedByCandidateId() {
        SystemSnapshot snapshot = createTwoVehicleSnapshot();

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
                findLinkUsage(breakdown, "edge_001_for_vehicle_001");

        LinkBandwidthUsageBreakdown linkVehicle2 =
                findLinkUsage(breakdown, "edge_001_for_vehicle_002");

        assertAlmostEquals(
                100.0,
                linkVehicle1.getUsedBandwidth(),
                "Bandwidth for vehicle_001 link should be 100."
        );

        assertAlmostEquals(
                120.0,
                linkVehicle2.getUsedBandwidth(),
                "Bandwidth for vehicle_002 link should be 120."
        );
    }

    /**
     * Verifica che il RepairOperator corregga un gene con candidato non valido.
     *
     * task_001 nasce da vehicle_001, ma il gene seleziona local_vehicle_002.
     *
     * Il repair deve sostituirlo con un candidato valido per vehicle_001,
     * preferibilmente local_vehicle_001.
     */
    private static void testRepairOperatorRepairsInvalidCandidate() {
        SystemSnapshot snapshot = createTwoVehicleSnapshot();

        Gene invalidGene = new Gene(
                "task_001",
                "local_vehicle_002",
                0.0,
                650.0,
                0.0
        );

        Chromosome invalidChromosome = new Chromosome(List.of(invalidGene));

        RepairOperator repairOperator = new RepairOperator();
        Chromosome repaired = repairOperator.repairChromosome(invalidChromosome, snapshot);

        Gene repairedGene = repaired.getGenes().get(0);

        assertEquals(
                "local_vehicle_001",
                repairedGene.getSelectedCandidateId(),
                "Repair should choose local_vehicle_001 for task_001."
        );
    }

    /**
     * Verifica che PopulationInitializer scelga solo candidati validi.
     */
    private static void testPopulationInitializerUsesOnlyValidCandidates() {
        SystemSnapshot snapshot = createTwoVehicleSnapshot();

        PopulationInitializer initializer = new PopulationInitializer(
                new Random(42),
                new RepairOperator()
        );

        List<Chromosome> population = initializer.createInitialPopulation(snapshot, 50);

        for (Chromosome chromosome : population) {
            assertChromosomeUsesOnlyValidCandidates(chromosome, snapshot);
        }
    }

    /**
     * Verifica che MutationOperator non muti verso candidati di altri veicoli.
     */
    private static void testMutationOperatorUsesOnlyValidCandidates() {
        SystemSnapshot snapshot = createTwoVehicleSnapshot();

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

        MutationOperator mutationOperator = new MutationOperator(new Random(7));

        Chromosome mutated = mutationOperator.mutate(
                chromosome,
                snapshot,
                1.0
        );

        assertChromosomeUsesOnlyValidCandidates(mutated, snapshot);
    }

    /**
     * Crea uno snapshot minimo per testare la copertura insufficiente.
     *
     * Il candidato EDGE non contiene più coverageTimeSeconds.
     * La copertura viene calcolata da CoverageEstimator usando:
     *
     * distance = 0 m
     * coverageRadius = 12.5 m
     * vehicleSpeed = 25 m/s
     *
     * coverageTime = 12.5 / 25 = 0.5 s
     *
     * Il gene del test forza full offloading su quel candidato.
     *
     * Con i valori scelti:
     *
     * upload time          = 800 / 100 = 8.0 s
     * remote execution     = 1600 / 400 = 4.0 s
     * download time        = 80 / 100 = 0.8 s
     * base latency         = 0.1 s
     *
     * completion time      = 12.9 s
     * coverage time        = 0.5 s
     *
     * Quindi la copertura deve risultare insufficiente.
     */
    private static SystemSnapshot createCoveragePressureSnapshotForTest() {
        List<VehicleSnapshot> vehicles = List.of(
                new VehicleSnapshot(
                        "vehicle_001",
                        0.0,
                        0.0,
                        25.0,
                        500.0
                )
        );

        List<TaskInstance> tasks = List.of(
                new TaskInstance(
                        "task_001",
                        "vehicle_001",
                        800.0,
                        80.0,
                        1600.0,
                        100.0
                )
        );

        List<NodeCandidate> candidates = List.of(
                localCandidate("vehicle_001", 500.0),
                edgeCandidate(
                        "edge_short_coverage_for_vehicle_001",
                        "vehicle_001",
                        "edge_short_coverage",
                        400.0,
                        100.0,
                        0.1,
                        0.0,
                        0.0,
                        12.5
                )
        );

        return new SystemSnapshot(
                "coverage_pressure_unit_snapshot",
                0.0,
                vehicles,
                tasks,
                candidates
        );
    }

    /**
     * Crea uno snapshot minimo:
     *
     * 1 veicolo
     * 1 task
     * 1 candidato LOCAL
     * 1 candidato EDGE
     */
    private static SystemSnapshot createOneVehicleSnapshot() {
        List<VehicleSnapshot> vehicles = List.of(
                new VehicleSnapshot(
                        "vehicle_001",
                        0.0,
                        0.0,
                        10.0,
                        500.0
                )
        );

        List<TaskInstance> tasks = List.of(
                new TaskInstance(
                        "task_001",
                        "vehicle_001",
                        100.0,
                        20.0,
                        1000.0,
                        10.0
                )
        );

        List<NodeCandidate> candidates = List.of(
                localCandidate("vehicle_001", 500.0),
                edgeCandidate(
                        "edge_001_for_vehicle_001",
                        "vehicle_001",
                        "edge_001",
                        4000.0,
                        100.0,
                        0.1,
                        0.0,
                        0.0,
                        300.0
                )
        );

        return new SystemSnapshot(
                "one_vehicle_snapshot",
                0.0,
                vehicles,
                tasks,
                candidates
        );
    }

    /**
     * Crea uno snapshot con due veicoli e due task.
     *
     * Entrambi i veicoli possono usare lo stesso executionNodeId edge_001,
     * ma tramite candidati diversi.
     */
    private static SystemSnapshot createTwoVehicleSnapshot() {
        List<VehicleSnapshot> vehicles = List.of(
                new VehicleSnapshot(
                        "vehicle_001",
                        0.0,
                        0.0,
                        10.0,
                        500.0
                ),
                new VehicleSnapshot(
                        "vehicle_002",
                        20.0,
                        0.0,
                        12.0,
                        650.0
                )
        );

        List<TaskInstance> tasks = List.of(
                new TaskInstance(
                        "task_001",
                        "vehicle_001",
                        100.0,
                        20.0,
                        1000.0,
                        10.0
                ),
                new TaskInstance(
                        "task_002",
                        "vehicle_002",
                        200.0,
                        30.0,
                        1200.0,
                        10.0
                )
        );

        List<NodeCandidate> candidates = List.of(
                localCandidate("vehicle_001", 500.0),
                edgeCandidate(
                        "edge_001_for_vehicle_001",
                        "vehicle_001",
                        "edge_001",
                        4000.0,
                        100.0,
                        0.1,
                        0.0,
                        0.0,
                        300.0
                ),
                localCandidate("vehicle_002", 650.0),
                edgeCandidate(
                        "edge_001_for_vehicle_002",
                        "vehicle_002",
                        "edge_001",
                        4000.0,
                        120.0,
                        0.1,
                        0.0,
                        0.0,
                        300.0
                )
        );

        return new SystemSnapshot(
                "two_vehicle_snapshot",
                0.0,
                vehicles,
                tasks,
                candidates
        );
    }

    /**
     * Verifica che un cromosoma usi solo candidati validi per il sourceVehicleId del task.
     */
    private static void assertChromosomeUsesOnlyValidCandidates(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        for (Gene gene : chromosome.getGenes()) {
            TaskInstance task = findTask(snapshot, gene.getTaskId());
            NodeCandidate candidate = findCandidate(snapshot, gene.getSelectedCandidateId());

            assertTrue(
                    task != null,
                    "Task should exist for gene " + gene.getTaskId()
            );

            assertTrue(
                    candidate != null,
                    "Candidate should exist for gene " + gene.getSelectedCandidateId()
            );

            assertEquals(
                    task.getSourceVehicleId(),
                    candidate.getSourceVehicleId(),
                    "Gene candidate must match task sourceVehicleId."
            );
        }
    }

    /**
     * Crea un FitnessEvaluator con configurazione di default.
     */
    private static FitnessEvaluator evaluator() {
        return new FitnessEvaluator(MaGaConfig.defaultConfig());
    }

    /**
     * Trova uso CPU per executionNodeId.
     */
    private static ExecutionNodeResourceUsageBreakdown findExecutionNodeUsage(
            EvaluationBreakdown breakdown,
            String executionNodeId
    ) {
        for (ExecutionNodeResourceUsageBreakdown usage
                : breakdown.getExecutionNodeResourceUsageBreakdowns()) {
            if (usage.getExecutionNodeId().equals(executionNodeId)) {
                return usage;
            }
        }

        throw new AssertionError("Execution node usage not found: " + executionNodeId);
    }

    /**
     * Trova uso banda per candidateId.
     */
    private static LinkBandwidthUsageBreakdown findLinkUsage(
            EvaluationBreakdown breakdown,
            String candidateId
    ) {
        for (LinkBandwidthUsageBreakdown usage
                : breakdown.getLinkBandwidthUsageBreakdowns()) {
            if (usage.getCandidateId().equals(candidateId)) {
                return usage;
            }
        }

        throw new AssertionError("Link usage not found: " + candidateId);
    }

    /**
     * Trova un task.
     */
    private static TaskInstance findTask(SystemSnapshot snapshot, String taskId) {
        for (TaskInstance task : snapshot.getTasks()) {
            if (task.getTaskId().equals(taskId)) {
                return task;
            }
        }

        return null;
    }

    /**
     * Trova un candidato.
     */
    private static NodeCandidate findCandidate(SystemSnapshot snapshot, String candidateId) {
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getCandidateId().equals(candidateId)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Verifica uguaglianza tra stringhe.
     */
    private static void assertEquals(
            String expected,
            String actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + " Expected: " + expected + ", actual: " + actual
            );
        }
    }

    /**
     * Verifica che una condizione sia vera.
     */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Verifica uguaglianza approssimata tra double.
     */
    private static void assertAlmostEquals(
            double expected,
            double actual,
            String message
    ) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(
                    message + " Expected: " + expected + ", actual: " + actual
            );
        }
    }

    /**
     * Verifica che un blocco lanci un'eccezione.
     */
    private static void assertThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }

        throw new AssertionError(message);
    }
}

