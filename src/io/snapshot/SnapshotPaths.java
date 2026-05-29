package io.snapshot;

import java.nio.file.Path;
import java.util.List;

/**
 * Catalogo dei dataset inclusi nel repository.
 *
 * Mantiene i percorsi degli snapshot in un unico punto, in modo che app e
 * runner manuali non dipendano dalla struttura fisica di data/.
 */
public final class SnapshotPaths {

    public static final String MAGA_DEFAULT_STRESS =
            path(
                    "snapshots",
                    "maga",
                    "stress",
                    "snapshot_maga_stress_100v_60tasks_coherent_cpu.json"
            );

    public static final List<String> MAGA_EXAMPLES = List.of(
            path(
                    "snapshots",
                    "maga",
                    "examples",
                    "snapshot_maga_01_basic_edge_cloud.json"
            ),
            path(
                    "snapshots",
                    "maga",
                    "examples",
                    "snapshot_maga_02_v2v_candidates.json"
            ),
            path(
                    "snapshots",
                    "maga",
                    "examples",
                    "snapshot_maga_03_coverage_pressure.json"
            )
    );

    public static final List<String> WINDOW_EXAMPLES = List.of(
            path("snapshots", "window", "examples", "snapshot_window_001.json"),
            path("snapshots", "window", "examples", "snapshot_window_002.json"),
            path("snapshots", "window", "examples", "snapshot_window_003.json"),
            path("snapshots", "window", "examples", "snapshot_window_004.json")
    );

    public static final String STATIC_WINDOW_STRESS_FOLDER =
            path("snapshots", "window", "stress", "static_baseline");

    public static final String TEMPORAL_WINDOW_STRESS_FOLDER =
            path("snapshots", "window", "stress", "urban_moderate");

    public static final String TEMPORAL_WINDOW_URBAN_CALIBRRATED_FOLDER =
            path("snapshots", "window", "stress", "realistic_scenarios", "urban_dynamic_calibrated");

    public static final String WINDOW_VALIDATION_FOLDER =
            path("snapshots", "window", "validation");

    private static final String DATA_ROOT = "data";

    private SnapshotPaths() {
    }

    private static String path(String first, String... more) {
        Path result = Path.of(DATA_ROOT, first);

        for (String part : more) {
            result = result.resolve(part);
        }

        return result.toString();
    }
}
