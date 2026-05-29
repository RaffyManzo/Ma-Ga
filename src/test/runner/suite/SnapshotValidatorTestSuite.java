package test.runner.suite;

import model.node.NodeCandidate;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;
import test.runner.core.ManualTestCase;
import test.runner.core.ManualTestSuite;
import test.runner.support.TestAssertions;
import test.runner.support.TestSnapshots;
import validation.snapshot.SnapshotValidator;

import java.util.List;

/**
 * Test manuali dedicati alla validazione degli snapshot.
 */
public final class SnapshotValidatorTestSuite implements ManualTestSuite {

    @Override
    public String getName() {
        return "Snapshot validation";
    }

    @Override
    public List<ManualTestCase> getTests() {
        return List.of(
                ManualTestCase.of(
                        "accepts valid source-aware snapshot",
                        SnapshotValidatorTestSuite::testAcceptsValidSnapshot
                ),
                ManualTestCase.of(
                        "rejects duplicated candidateId",
                        SnapshotValidatorTestSuite::testRejectsDuplicatedCandidateId
                )
        );
    }

    private static void testAcceptsValidSnapshot() {
        SystemSnapshot snapshot = TestSnapshots.createTwoVehicleSnapshot();

        SnapshotValidator validator = new SnapshotValidator();
        validator.validate(snapshot);
    }

    private static void testRejectsDuplicatedCandidateId() {
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
                TestSnapshots.localCandidate("vehicle_001", 500.0),
                TestSnapshots.localCandidate("vehicle_001", 500.0)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                "duplicated_candidate_snapshot",
                0.0,
                vehicles,
                tasks,
                candidates
        );

        TestAssertions.assertThrows(
                () -> new SnapshotValidator().validate(snapshot),
                "Duplicated candidateId should be rejected."
        );
    }
}
