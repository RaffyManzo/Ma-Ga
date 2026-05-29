package test.runner;

import config.MaGaConfig;
import ga.core.MaGaOptimizer;
import ga.core.MaGaResult;
import io.reporting.ResultPrinter;
import io.snapshot.SnapshotLoader;
import io.snapshot.SnapshotPaths;
import model.snapshot.SystemSnapshot;
import validation.snapshot.SnapshotValidator;

/**
 * Runner manuale per eseguire gli snapshot di esempio MA-GA in sequenza.
 *
 * Non usa JUnit. Serve a controllare rapidamente che:
 *
 * - il loader legga correttamente gli snapshot;
 * - il validator li accetti;
 * - il MA-GA venga eseguito;
 * - il report venga stampato per ogni caso.
 */
public final class SnapshotBatchRunner {

    /**
     * Esegue tutti gli snapshot di esempio MA-GA.
     */
    public static void main(String[] args) throws Exception {
        SnapshotLoader loader = new SnapshotLoader();
        SnapshotValidator validator = new SnapshotValidator();
        MaGaConfig config = MaGaConfig.defaultConfig();
        MaGaOptimizer optimizer = new MaGaOptimizer(config);
        ResultPrinter printer = new ResultPrinter(config);

        for (String path : SnapshotPaths.MAGA_EXAMPLES) {
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
