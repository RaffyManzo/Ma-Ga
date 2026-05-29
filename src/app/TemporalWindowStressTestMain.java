package app;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import config.window.TemporalWindowConfig;
import ga.core.MaGaOptimizer;
import io.reporting.CandidateFilteringPrinter;
import io.reporting.DeepTemporalWindowDiagnosticPrinter;
import io.snapshot.SnapshotLoader;
import model.snapshot.SystemSnapshot;
import validation.snapshot.SnapshotValidator;
import window.core.TemporalWindowManager;
import window.dynamicity.DynamicityEvaluator;
import window.event.StaticCriticalEventDetector;
import window.population.PopulationAdapter;
import window.prefilter.CandidatePrefilter;
import window.prefilter.CandidatePrefilterConfig;
import window.provider.FilteringSystemStateProvider;
import window.provider.StaticSystemStateProvider;
import window.provider.SystemStateProvider;
import window.state.TemporalWindowResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Main di stress test del sistema temporale completo MA-GA.
 *
 * Questo main testa:
 *
 * - caricamento di snapshot statici consecutivi da JSON;
 * - validazione snapshot;
 * - prefiltraggio dei candidati prima del GA;
 * - TemporalWindowManager;
 * - DynamicityEvaluator;
 * - PopulationAdapter;
 * - MaGaOptimizer con scaling adattivo;
 * - report diagnostico deadline-oriented;
 * - report sintetico del candidate prefilter.
 */
public final class TemporalWindowStressTestMain {

    private static final String DEFAULT_SNAPSHOT_FOLDER =
            "data/validation_scenarios/computation_heavy";

    private static final double START_TIME_SECONDS = 0.0;

    private TemporalWindowStressTestMain() {
    }

    public static void main(String[] args) throws Exception {
        String folderPath = args.length > 0
                ? args[0]
                : DEFAULT_SNAPSHOT_FOLDER;

        List<SystemSnapshot> snapshots =
                loadAndValidateSnapshots(folderPath);

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

        CandidatePrefilterConfig prefilterConfig =
                CandidatePrefilterConfig.defaultConfig();

        CandidatePrefilter prefilter =
                new CandidatePrefilter(prefilterConfig);

        SystemStateProvider baseProvider =
                new StaticSystemStateProvider(snapshots);

        FilteringSystemStateProvider filteredProvider =
                new FilteringSystemStateProvider(
                        baseProvider,
                        prefilter
                );

        SystemSnapshot firstFilteredSnapshot =
                prefilter
                        .filter(snapshots.get(0))
                        .getFilteredSnapshot();

        int targetPopulationSize = maGaConfig
                .resolveGeneticAlgorithmConfig(firstFilteredSnapshot)
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
                filteredProvider,
                targetPopulationSize
        );

        TemporalWindowResult result = manager.run(
                START_TIME_SECONDS,
                snapshots.size()
        );

        DeepTemporalWindowDiagnosticPrinter diagnosticPrinter =
                new DeepTemporalWindowDiagnosticPrinter(maGaConfig);

        diagnosticPrinter.print(result);

        CandidateFilteringPrinter filteringPrinter =
                new CandidateFilteringPrinter();

        filteringPrinter.print(
                filteredProvider.getFilteringResults()
        );
    }

    /**
     * Carica e valida tutti gli snapshot presenti nella cartella.
     *
     * Gli snapshot vengono ordinati in base al loro timeSeconds,
     * non solo in base al nome del file.
     */
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

    /**
     * Trova i file JSON dello stress test temporale.
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

        result.sort(
                Comparator.comparing(File::getName)
        );

        return result;
    }
}