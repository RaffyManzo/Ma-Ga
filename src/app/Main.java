package app;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import ga.core.MaGaOptimizer;
import ga.core.MaGaResult;
import io.reporting.ResultPrinter;
import io.reporting.StressResultPrinter;
import io.snapshot.SnapshotLoader;
import io.snapshot.SnapshotPaths;
import model.snapshot.SystemSnapshot;
import validation.snapshot.SnapshotValidator;

/**
 * Punto di ingresso del prototipo Java standalone del MA-GA.
 *
 * Questa versione integra SnapshotValidator.
 *
 * Flusso:
 *
 * 1. carica lo snapshot JSON;
 * 2. valida lo snapshot;
 * 3. crea la configurazione MA-GA;
 * 4. esegue optimizeDetailed();
 * 5. stampa il report tecnico.
 */
public final class Main {

    /**
     * Avvia il prototipo.
     *
     * Parametri in ingresso:
     * - args[0], opzionale: percorso dello snapshot da eseguire.
     *
     * Se args[0] non viene fornito, usa uno snapshot di test di default.
     */
    public static void main(String[] args) throws Exception {
        String snapshotPath = args.length > 0
                ? args[0]
                : SnapshotPaths.MAGA_DEFAULT_STRESS;

        SnapshotLoader snapshotLoader = new SnapshotLoader();
        SystemSnapshot snapshot = snapshotLoader.load(snapshotPath);

        SnapshotValidator validator = new SnapshotValidator();
        validator.validate(snapshot);

        MaGaConfig config = MaGaConfig.defaultConfig(
                GaParameterScalingMode.ADAPTIVE
        );
        MaGaOptimizer optimizer = new MaGaOptimizer(config);
        MaGaResult result = optimizer.optimizeDetailed(snapshot);

        //  resultPrinter = new ResultPrinter(config);
        // resultPrinter.printOptimizationResult(snapshot, result);




        StressResultPrinter resultPrinter = new StressResultPrinter(config);
        resultPrinter.printStressReport(snapshot, result);
    }
}
