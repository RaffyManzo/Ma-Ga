package test.runner.support;

import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.List;

/**
 * Fixture sintetiche condivise dai test manuali.
 */
public final class TestSnapshots {

    private TestSnapshots() {
    }

    public static SystemSnapshot createCoveragePressureSnapshot() {
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

    public static SystemSnapshot createOneVehicleSnapshot() {
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

    public static SystemSnapshot createTwoVehicleSnapshot() {
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

    public static NodeCandidate localCandidate(
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
}
