package test.runner;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import config.window.TemporalWindowConfig;
import ga.core.MaGaOptimizer;
import io.reporting.TemporalWindowStressPrinter;
import io.snapshot.SnapshotLoader;
import model.snapshot.SystemSnapshot;
import validation.snapshot.SnapshotValidator;
import window.core.TemporalWindowManager;
import window.dynamicity.DynamicityEvaluator;
import window.event.StaticCriticalEventDetector;
import window.population.PopulationAdapter;
import window.provider.StaticSystemStateProvider;
import window.state.TemporalWindowResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Main di stress test del sistema temporale completo.
 *
 * Questo main usa davvero TemporalWindowManager:
 *
 * - StaticSystemStateProvider per fornire snapshot JSON statici;
 * - StaticCriticalEventDetector.empty() per usare solo finestre programmate;
 * - DynamicityEvaluator per misurare il cambiamento tra snapshot;
 * - PopulationAdapter per FIRST_RUN/WARM_START/PARTIAL_RESTART/COLD_START;
 * - MaGaOptimizer per eseguire il MA-GA su ogni finestra.
 *
 * Lo scopo è testare il comportamento del package window sotto stress costante.
 */
public final class TemporalWindowStressTestMain {

    private static final String DEFAULT_FOLDER =
            "data/window_static_stress";

    private static final double START_TIME_SECONDS = 0.0;

    private TemporalWindowStressTestMain() {
    }

    public static void main(String[] args) throws Exception {
        String folderPath = args.length > 0 ? args[0] : DEFAULT_FOLDER;

        List<SystemSnapshot> snapshots = loadAndValidateSnapshots(folderPath);

        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException(
                    "No snapshots loaded from: " + folderPath
            );
        }

        MaGaConfig maGaConfig = MaGaConfig.defaultConfig(
                GaParameterScalingMode.ADAPTIVE
        );

        TemporalWindowConfig windowConfig =
                TemporalWindowConfig.defaultConfig();

        int targetPopulationSize = maGaConfig
                .resolveGeneticAlgorithmConfig(snapshots.get(0))
                .getPopulationSize();

        TemporalWindowManager manager = new TemporalWindowManager(
                windowConfig,
                new MaGaOptimizer(maGaConfig),
                new DynamicityEvaluator(windowConfig),
                new PopulationAdapter(
                        windowConfig,
                        new Random(
                                maGaConfig
                                        .getGeneticAlgorithmConfig()
                                        .getRandomSeed()
                        )
                ),
                StaticCriticalEventDetector.empty(),
                new StaticSystemStateProvider(snapshots),
                targetPopulationSize
        );

        TemporalWindowResult result = manager.run(
                START_TIME_SECONDS,
                snapshots.size()
        );

        TemporalWindowStressPrinter printer =
                new TemporalWindowStressPrinter();

        printer.print(result);
    }

    private static List<SystemSnapshot> loadAndValidateSnapshots(
            String folderPath
    ) throws Exception {
        SnapshotLoader loader = new SnapshotLoader();
        SnapshotValidator validator = new SnapshotValidator();

        List<SystemSnapshot> snapshots = new ArrayList<>();

        for (File file : listSnapshotFiles(folderPath)) {
            SystemSnapshot snapshot = loader.load(file.getPath());
            validator.validate(snapshot);
            snapshots.add(snapshot);
        }

        snapshots.sort(
                Comparator.comparingDouble(SystemSnapshot::getTimeSeconds)
        );

        return snapshots;
    }

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
                        && file.getName().startsWith(
                        "snapshot_window_static_stress_"
                )
        );

        if (files == null || files.length == 0) {
            throw new IllegalArgumentException(
                    "No temporal stress snapshot found in: " + folderPath
            );
        }

        List<File> result = new ArrayList<>(List.of(files));
        result.sort(Comparator.comparing(File::getName));

        return result;
    }
}
