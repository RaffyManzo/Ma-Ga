package test;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import ga.core.MaGaOptimizer;
import ga.core.MaGaResult;
import io.reporting.StaticWindowStressPrinter;
import io.snapshot.SnapshotLoader;
import model.genetic.Chromosome;
import model.snapshot.SystemSnapshot;
import validation.snapshot.SnapshotValidator;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Main di test per stressare il MA-GA su una sequenza di finestre statiche.
 *
 * Questo main non usa eventi critici.
 * Ogni file JSON rappresenta una finestra programmata consecutiva.
 *
 * Il test riusa la popolazione finale della finestra precedente come
 * popolazione iniziale della finestra successiva, in modo da simulare
 * un warm start statico sotto stress costante.
 */
public final class StaticWindowStressTestMain {

    private static final String DEFAULT_FOLDER =
            "data/window_static_stress";

    private StaticWindowStressTestMain() {
    }

    public static void main(String[] args) throws Exception {
        String folderPath = args.length > 0 ? args[0] : DEFAULT_FOLDER;

        SnapshotLoader loader = new SnapshotLoader();
        SnapshotValidator validator = new SnapshotValidator();

        MaGaConfig config = MaGaConfig.defaultConfig(
                GaParameterScalingMode.ADAPTIVE
        );

        MaGaOptimizer optimizer = new MaGaOptimizer(config);

        List<File> snapshotFiles = listSnapshotFiles(folderPath);
        List<StaticWindowStressPrinter.StepReport> reports = new ArrayList<>();

        List<Chromosome> previousFinalPopulation = null;

        for (int i = 0; i < snapshotFiles.size(); i++) {
            File file = snapshotFiles.get(i);

            SystemSnapshot snapshot = loader.load(file.getPath());
            validator.validate(snapshot);

            int initialPopulationSize = previousFinalPopulation == null
                    ? 0
                    : previousFinalPopulation.size();

            long startMillis = System.currentTimeMillis();

            MaGaResult result = optimizer.optimizeDetailed(
                    snapshot,
                    previousFinalPopulation
            );

            long runtimeMillis = System.currentTimeMillis() - startMillis;

            reports.add(
                    new StaticWindowStressPrinter.StepReport(
                            i,
                            snapshot,
                            result,
                            initialPopulationSize,
                            runtimeMillis
                    )
            );

            previousFinalPopulation = result.getFinalPopulation();
        }

        StaticWindowStressPrinter printer =
                new StaticWindowStressPrinter();

        printer.print(reports);
    }

    /**
     * Restituisce i file snapshot ordinati per nome.
     */
    private static List<File> listSnapshotFiles(String folderPath) {
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException(
                    "Snapshot folder not found: " + folderPath
            );
        }

        File[] files = folder.listFiles(
                file -> file.isFile()
                        && file.getName().endsWith(".json")
                        && file.getName().startsWith("snapshot_window_static_stress_")
        );

        if (files == null || files.length == 0) {
            throw new IllegalArgumentException(
                    "No static window stress snapshot found in: " + folderPath
            );
        }

        List<File> result = new ArrayList<>(List.of(files));

        result.sort(Comparator.comparing(File::getName));

        return result;
    }
}
