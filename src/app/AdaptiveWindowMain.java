package app;

import io.snapshot.SnapshotPaths;

/**
 * Alias compatibile con il main usato nei test della finestra adattiva.
 */
public final class AdaptiveWindowMain {

    private AdaptiveWindowMain() {
    }

    public static void main(String[] args) throws Exception {
        String[] normalizedArgs;

        if (args.length == 0) {
            normalizedArgs = new String[]{
                    "JSON_SEQUENCE",
                    SnapshotPaths.TEMPORAL_WINDOW_URBAN_CALIBRATED_FOLDER,
                    "8"
            };
        } else if (args.length == 1) {
            normalizedArgs = new String[]{
                    "JSON_SEQUENCE",
                    args[0],
                    "8"
            };
        } else if (args.length == 2) {
            normalizedArgs = new String[]{
                    "JSON_SEQUENCE",
                    args[0],
                    args[1]
            };
        } else {
            normalizedArgs = args;
        }

        AdaptiveWindowSourceMain.main(normalizedArgs);
    }
}
