package test;

import config.MaGaConfig;
import ga.MaGaOptimizer;
import ga.MaGaResult;
import io.ResultPrinter;
import io.SnapshotLoader;
import model.SystemSnapshot;
import validation.SnapshotValidator;

/**
 * Runner manuale per eseguire più snapshot di test in sequenza.
 *
 * Non usa JUnit.
 * Serve a controllare rapidamente che:
 *
 * - il loader legga correttamente gli snapshot;
 * - il validator li accetti;
 * - il MA-GA venga eseguito;
 * - il report venga stampato per ogni caso.
 */
public final class SnapshotBatchRunner {

    private static final String[] SNAPSHOT_PATHS = {
            "data/tests/snapshot_01_local_only.json",
            "data/tests/snapshot_02_local_vs_edge.json",
            "data/tests/snapshot_03_partial_offloading.json",
            "data/tests/snapshot_04_deadline_pressure.json",
            "data/tests/snapshot_05_coverage_pressure.json",
            "data/tests/snapshot_06_source_aware_multi_vehicle.json"
    };

    /**
     * Esegue tutti gli snapshot di test.
     */
    public static void main(String[] args) throws Exception {
        SnapshotLoader loader = new SnapshotLoader();
        SnapshotValidator validator = new SnapshotValidator();
        MaGaConfig config = MaGaConfig.defaultConfig();
        MaGaOptimizer optimizer = new MaGaOptimizer(config);
        ResultPrinter printer = new ResultPrinter(config);

        for (String path : SNAPSHOT_PATHS) {
            System.out.println();
            System.out.println("############################################################");
            System.out.println("RUNNING SNAPSHOT: " + path);
            System.out.println("############################################################");
            System.out.println();

            SystemSnapshot snapshot = loader.load(path);
            validator.validate(snapshot);

            MaGaResult result = optimizer.optimizeDetailed(snapshot);
            printer.printOptimizationResult(snapshot, result);
        }
    }
}