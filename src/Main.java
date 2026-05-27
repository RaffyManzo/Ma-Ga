import config.MaGaConfig;
import ga.MaGaOptimizer;
import ga.MaGaResult;
import io.ResultPrinter;
import io.SnapshotLoader;
import model.SystemSnapshot;

/**
 * Punto di ingresso del primo prototipo Java standalone del MA-GA.
 *
 * Questa versione carica lo snapshot complesso snapshot_002.json.
 */
public final class Main {

    /**
     * Avvia il prototipo:
     *
     * 1. carica uno snapshot statico;
     * 2. crea la configurazione del MA-GA;
     * 3. esegue l'ottimizzazione dettagliata;
     * 4. stampa un report tecnico del risultato.
     */
    public static void main(String[] args) throws Exception {
        String snapshotPath = "data/snapshot_003.json";

        SnapshotLoader snapshotLoader = new SnapshotLoader();
        SystemSnapshot snapshot = snapshotLoader.load(snapshotPath);

        MaGaConfig config = MaGaConfig.defaultConfig();

        MaGaOptimizer optimizer = new MaGaOptimizer(config);
        MaGaResult result = optimizer.optimizeDetailed(snapshot);

        ResultPrinter resultPrinter = new ResultPrinter(config);
        resultPrinter.printOptimizationResult(snapshot, result);
    }
}
