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
 * <p>Flusso di esecuzione:</p>
 *
 * <ol>
 *     <li>carica uno snapshot JSON;</li>
 *     <li>valida lo scenario;</li>
 *     <li>costruisce la configurazione MA-GA;</li>
 *     <li>esegue l'ottimizzazione dettagliata;</li>
 *     <li>stampa il report diagnostico di stress.</li>
 * </ol>
 */
public final class Main {

    /**
     * Avvia il prototipo.
     *
     * @param args opzionalmente, in posizione 0, il percorso dello snapshot
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

        StressResultPrinter resultPrinter = new StressResultPrinter(config);
        resultPrinter.printStressReport(snapshot, result);
    }
}
