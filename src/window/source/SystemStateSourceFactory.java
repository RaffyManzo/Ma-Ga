package window.source;

import model.snapshot.SystemSnapshot;

import java.util.List;
import java.util.Locale;

/**
 * Factory per costruire sorgenti dati a partire dagli argomenti dei main.
 */
public final class SystemStateSourceFactory {

    private SystemStateSourceFactory() {
    }

    public static SystemStateSource fromJsonFolder(
            String modeName,
            String folderPath
    ) throws Exception {
        if (folderPath == null || folderPath.isBlank()) {
            throw new IllegalArgumentException("folderPath must not be blank.");
        }

        String normalizedMode = normalizeMode(modeName);
        List<SystemSnapshot> snapshots = new JsonSnapshotFolderLoader().load(folderPath);

        return switch (normalizedMode) {
            case "JSON_TIME", "JSON_TIME_INDEXED", "TIME_INDEXED" ->
                    new TimeIndexedSnapshotReplaySource(
                            snapshots,
                            1.0E-6,
                            "time-indexed JSON replay from " + folderPath
                    );
            case "JSON_SEQUENCE", "JSON_SEQUENTIAL", "SEQUENTIAL" ->
                    new SequentialSnapshotReplaySource(
                            snapshots,
                            "sequential JSON replay from " + folderPath
                    );
            default -> throw new IllegalArgumentException(
                    "Unsupported JSON source mode: " + modeName
            );
        };
    }

    public static String normalizeMode(String modeName) {
        if (modeName == null || modeName.isBlank()) {
            return "JSON_SEQUENCE";
        }
        return modeName.trim().toUpperCase(Locale.ROOT);
    }
}
