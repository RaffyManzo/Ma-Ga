package app;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import ga.core.MaGaOptimizer;
import ga.core.MaGaResult;
import io.reporting.GaBatchReportPrinter;
import io.snapshot.JsonSnapshotFolderLoader;
import io.snapshot.SnapshotPaths;
import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Esegue il MA-GA separatamente su tutti gli snapshot JSON contenuti in una cartella.
 *
 * <p>Ogni snapshot viene trattato come uno scenario indipendente. La popolazione finale
 * di una precedente esecuzione non viene riutilizzata: questo entry point serve a
 * confrontare il comportamento del GA in situazioni statiche differenti.</p>
 *
 * <p>Argomenti supportati:</p>
 * <pre>
 *   GaBatchMain
 *   GaBatchMain data/snapshots/ga/scenarios/static_baseline
 *   GaBatchMain data/snapshots/ga/scenarios/static_baseline --details
 * </pre>
 */
public final class GaBatchMain {
    private static final String DETAILS_FLAG = "--details";

    private GaBatchMain() {
    }

    public static void main(String[] args) throws Exception {
        RunArguments runArguments = RunArguments.parse(args);

        List<SystemSnapshot> snapshots = new JsonSnapshotFolderLoader()
                .load(runArguments.folderPath());

        MaGaConfig config = MaGaConfig.defaultConfig(GaParameterScalingMode.ADAPTIVE);
        MaGaOptimizer optimizer = new MaGaOptimizer(config);
        List<GaBatchReportPrinter.SnapshotRun> runs = new ArrayList<>();

        for (int index = 0; index < snapshots.size(); index++) {
            SystemSnapshot snapshot = snapshots.get(index);
            long startNanos = System.nanoTime();
            MaGaResult result = optimizer.optimizeDetailed(snapshot);
            long runtimeMillis = (System.nanoTime() - startNanos) / 1_000_000L;

            runs.add(new GaBatchReportPrinter.SnapshotRun(
                    index,
                    snapshot,
                    result,
                    runtimeMillis
            ));
        }

        new GaBatchReportPrinter(config)
                .print(runArguments.folderPath(), runs, runArguments.printDetails());
    }

    private record RunArguments(String folderPath, boolean printDetails) {
        private static RunArguments parse(String[] args) {
            if (args.length == 0) {
                return new RunArguments(SnapshotPaths.GA_DEFAULT_BATCH_FOLDER, false);
            }
            if (args.length == 1) {
                if (DETAILS_FLAG.equalsIgnoreCase(args[0])) {
                    return new RunArguments(SnapshotPaths.GA_DEFAULT_BATCH_FOLDER, true);
                }
                return new RunArguments(args[0], false);
            }
            if (args.length == 2 && DETAILS_FLAG.equalsIgnoreCase(args[1])) {
                return new RunArguments(args[0], true);
            }
            throw new IllegalArgumentException(
                    "Usage: GaBatchMain [snapshotFolder] [--details]"
            );
        }
    }
}
