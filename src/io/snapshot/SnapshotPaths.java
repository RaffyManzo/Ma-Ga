package io.snapshot;

import java.nio.file.Path;
import java.util.List;

/**
 * Catalogo centralizzato dei dataset inclusi nel repository.
 *
 * <p>La struttura distingue gli snapshot statici destinati al confronto del GA dalle
 * sequenze temporali destinate al gestore adattivo.</p>
 */
public final class SnapshotPaths {
    private static final String DATA_ROOT = "data";

    public static final String GA_EXAMPLES_ROOT = path("snapshots", "ga", "examples");
    public static final String GA_SCENARIOS_ROOT = path("snapshots", "ga", "scenarios");
    public static final String GA_DEFAULT_BATCH_FOLDER = path(
            "snapshots", "ga", "scenarios", "static_baseline"
    );
    public static final String GA_COHERENT_CPU_FOLDER = path(
            "snapshots", "ga", "scenarios", "coherent_cpu"
    );
    public static final String GA_MIXED_PRESSURE_FOLDER = path(
            "snapshots", "ga", "scenarios", "mixed_pressure"
    );

    public static final String TEMPORAL_EXAMPLES_ROOT = path(
            "snapshots", "temporal", "examples"
    );
    public static final String TEMPORAL_BASIC_SEQUENCE_FOLDER = path(
            "snapshots", "temporal", "examples", "basic_sequence"
    );
    public static final String TEMPORAL_SCENARIOS_ROOT = path(
            "snapshots", "temporal", "scenarios"
    );
    public static final String TEMPORAL_URBAN_MODERATE_FOLDER = path(
            "snapshots", "temporal", "scenarios", "urban_moderate"
    );
    public static final String TEMPORAL_DEFAULT_SCENARIO_FOLDER = path(
            "snapshots", "temporal", "scenarios", "urban_realistic_dynamic_calibrated"
    );
    public static final String TEMPORAL_VALIDATION_FOLDER = path(
            "snapshots", "temporal", "validation"
    );

    /* Alias temporanei per runner e suite manuali non ancora migrati. */
    @Deprecated
    public static final String MAGA_DEFAULT_STRESS = path(
            "snapshots", "ga", "scenarios", "coherent_cpu",
            "snapshot_maga_stress_100v_60tasks_coherent_cpu.json"
    );

    @Deprecated
    public static final List<String> MAGA_EXAMPLES = List.of(
            path("snapshots", "ga", "examples", "basic_edge_cloud", "snapshot_maga_01_basic_edge_cloud.json"),
            path("snapshots", "ga", "examples", "v2v_candidates", "snapshot_maga_02_v2v_candidates.json"),
            path("snapshots", "ga", "examples", "coverage_pressure", "snapshot_maga_03_coverage_pressure.json")
    );

    @Deprecated
    public static final List<String> WINDOW_EXAMPLES = List.of(
            path("snapshots", "temporal", "examples", "basic_sequence", "snapshot_window_001.json"),
            path("snapshots", "temporal", "examples", "basic_sequence", "snapshot_window_002.json"),
            path("snapshots", "temporal", "examples", "basic_sequence", "snapshot_window_003.json"),
            path("snapshots", "temporal", "examples", "basic_sequence", "snapshot_window_004.json")
    );

    @Deprecated
    public static final String STATIC_WINDOW_STRESS_FOLDER = GA_DEFAULT_BATCH_FOLDER;

    @Deprecated
    public static final String TEMPORAL_WINDOW_STRESS_FOLDER = TEMPORAL_URBAN_MODERATE_FOLDER;

    @Deprecated
    public static final String TEMPORAL_WINDOW_URBAN_CALIBRATED_FOLDER = TEMPORAL_DEFAULT_SCENARIO_FOLDER;

    @Deprecated
    public static final String WINDOW_VALIDATION_FOLDER = TEMPORAL_VALIDATION_FOLDER;

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
