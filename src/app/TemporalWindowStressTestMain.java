package app;

/**
 * Main storico mantenuto per compatibilità.
 *
 * <p>Ora usa la nuova architettura a sorgente dati astratta. Per i test JSON
 * viene usato il replay sequenziale, così tutti gli snapshot vengono letti in
 * ordine e la finestra adattiva resta una decisione del manager, non del loader
 * dei file.</p>
 */
public final class TemporalWindowStressTestMain {

    private static final String DEFAULT_SNAPSHOT_FOLDER = "data/window_static_stress";

    private TemporalWindowStressTestMain() {
    }

    public static void main(String[] args) throws Exception {
        String folderPath = args.length > 0
                ? args[0]
                : DEFAULT_SNAPSHOT_FOLDER;

        String maxSteps = args.length > 1
                ? args[1]
                : "8";

        AdaptiveWindowSourceMain.main(
                new String[]{
                        "JSON_SEQUENCE",
                        folderPath,
                        maxSteps
                }
        );
    }
}
