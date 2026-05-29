package window.source;

import io.snapshot.SnapshotLoader;
import model.snapshot.SystemSnapshot;
import validation.snapshot.SnapshotValidator;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Loader comune per snapshot JSON usati nei test offline.
 */
public final class JsonSnapshotFolderLoader {

    private static final String SNAPSHOT_PREFIX = "snapshot_window_static_stress_";
    private static final String JSON_EXTENSION = ".json";

    private final SnapshotLoader snapshotLoader;
    private final SnapshotValidator snapshotValidator;

    public JsonSnapshotFolderLoader() {
        this(new SnapshotLoader(), new SnapshotValidator());
    }

    public JsonSnapshotFolderLoader(
            SnapshotLoader snapshotLoader,
            SnapshotValidator snapshotValidator
    ) {
        if (snapshotLoader == null) {
            throw new IllegalArgumentException("snapshotLoader must not be null.");
        }
        if (snapshotValidator == null) {
            throw new IllegalArgumentException("snapshotValidator must not be null.");
        }
        this.snapshotLoader = snapshotLoader;
        this.snapshotValidator = snapshotValidator;
    }

    public List<SystemSnapshot> load(String folderPath) throws Exception {
        List<SystemSnapshot> snapshots = new ArrayList<>();

        for (File file : listSnapshotFiles(folderPath)) {
            SystemSnapshot snapshot = snapshotLoader.load(file.getPath());
            snapshotValidator.validate(snapshot);
            snapshots.add(snapshot);
        }

        snapshots.sort(Comparator.comparingDouble(SystemSnapshot::getTimeSeconds));
        return snapshots;
    }

    private List<File> listSnapshotFiles(String folderPath) {
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException(
                    "Snapshot folder not found: " + folderPath
            );
        }

        File[] files = folder.listFiles(
                file -> file.isFile()
                        && file.getName().endsWith(JSON_EXTENSION)
                        && file.getName().startsWith(SNAPSHOT_PREFIX)
        );

        if (files == null || files.length == 0) {
            throw new IllegalArgumentException(
                    "No snapshot file found in: " + folderPath
            );
        }

        List<File> result = new ArrayList<>(List.of(files));
        result.sort(Comparator.comparing(File::getName));
        return result;
    }
}
