package app;

import config.MaGaConfig;
import config.window.TemporalWindowConfig;
import ga.core.MaGaOptimizer;
import io.reporting.TemporalWindowPrinter;
import io.snapshot.SnapshotLoader;
import io.snapshot.SnapshotPaths;
import model.snapshot.SystemSnapshot;
import validation.snapshot.SnapshotValidator;
import window.core.TemporalWindowManager;
import window.dynamicity.DynamicityEvaluator;
import window.event.CriticalEvent;
import window.event.CriticalEventSeverity;
import window.event.CriticalEventType;
import window.event.StaticCriticalEventDetector;
import window.population.PopulationAdapter;
import window.provider.StaticSystemStateProvider;
import window.state.TemporalWindowResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Main manuale per testare il ciclo temporale del MA-GA.
 *
 * Carica una sequenza di snapshot statici, configura un evento critico
 * simulato e stampa un report focalizzato sul funzionamento della finestra.
 */
public final class TemporalWindowTestMain {

    private TemporalWindowTestMain() {
    }

    /**
     * Esegue il test temporale.
     *
     * @param args non usato
     * @throws Exception se il caricamento o la validazione degli snapshot fallisce
     */
    public static void main(String[] args) throws Exception {
        List<SystemSnapshot> snapshots = loadAndValidateSnapshots();

        MaGaConfig maGaConfig = MaGaConfig.defaultConfig();

        TemporalWindowConfig windowConfig = new TemporalWindowConfig(
                5.0,    // fixedIntervalSeconds
                0.15,   // thetaLow
                0.45,   // thetaHigh
                0.40,   // rhoKeep
                0.25,   // lambdaVehicles
                0.25,   // lambdaTasks
                0.25,   // lambdaResources
                0.25,   // lambdaLinks
                0.50,   // alphaT
                1.20,   // etaUp
                0.80,   // etaDown
                1.0E-6  // epsilonT
        );

        MaGaOptimizer optimizer = new MaGaOptimizer(maGaConfig);

        DynamicityEvaluator dynamicityEvaluator =
                new DynamicityEvaluator(windowConfig);

        PopulationAdapter populationAdapter =
                new PopulationAdapter(
                        windowConfig,
                        new Random(maGaConfig.getGeneticAlgorithmConfig().getRandomSeed())
                );

        StaticSystemStateProvider stateProvider =
                new StaticSystemStateProvider(snapshots);

        StaticCriticalEventDetector eventDetector =
                new StaticCriticalEventDetector(createCriticalEvents());

        TemporalWindowManager manager =
                new TemporalWindowManager(
                        windowConfig,
                        optimizer,
                        dynamicityEvaluator,
                        populationAdapter,
                        eventDetector,
                        stateProvider,
                        maGaConfig.getGeneticAlgorithmConfig().getPopulationSize()
                );

        TemporalWindowResult result = manager.run(
                1.0, // startTimeSeconds
                4    // maxSteps
        );

        TemporalWindowPrinter printer = new TemporalWindowPrinter();
        printer.print(result);
    }

    /**
     * Carica e valida gli snapshot usati dal test.
     */
    private static List<SystemSnapshot> loadAndValidateSnapshots() throws Exception {
        SnapshotLoader loader = new SnapshotLoader();
        SnapshotValidator validator = new SnapshotValidator();

        List<SystemSnapshot> snapshots = new ArrayList<>();

        for (String path : SnapshotPaths.WINDOW_EXAMPLES) {
            SystemSnapshot snapshot = loader.load(path);
            validator.validate(snapshot);
            snapshots.add(snapshot);
        }

        return snapshots;
    }

    /**
     * Crea eventi statici per verificare la riesecuzione anticipata.
     */
    private static List<CriticalEvent> createCriticalEvents() {
        return List.of(
                CriticalEvent.forCandidate(
                        "event_critical_link_001",
                        8.5,
                        CriticalEventType.LINK_DEGRADED,
                        CriticalEventSeverity.HIGH,
                        "edge_001_for_vehicle_002",
                        "Simulated degradation on the edge link for vehicle_002."
                )
        );
    }
}
