package io.snapshot;

import model.snapshot.SystemSnapshot;
import validation.snapshot.SnapshotValidator;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Carica e valida tutti gli snapshot JSON contenuti direttamente in una cartella.
 *
 * <p>Il loader non impone più prefissi legati ai vecchi stress test. Una cartella
 * rappresenta uno scenario e può quindi contenere file con nomi descrittivi differenti.
 * I JSON vengono caricati in ordine alfabetico e restituiti in ordine temporale stabile.</p>
 */
public final class JsonSnapshotFolderLoader {
    private static final String JSON_EXTENSION = ".json";

    private final SnapshotLoader snapshotLoader;
    private final SnapshotValidator snapshotValidator;

    public JsonSnapshotFolderLoader() {
        this(new SnapshotValidator());
    }

    public JsonSnapshotFolderLoader(SnapshotValidator snapshotValidator) {
        this(new SnapshotLoader(snapshotValidator), snapshotValidator);
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

        snapshots.sort(
                Comparator.comparingDouble(SystemSnapshot::getTimeSeconds)
                        .thenComparing(SystemSnapshot::getSnapshotId)
        );
        return snapshots;
    }

    private List<File> listSnapshotFiles(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            throw new IllegalArgumentException("folderPath must not be blank.");
        }

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("Snapshot folder not found: " + folderPath);
        }

        File[] files = folder.listFiles(file ->
                file.isFile()
                        && file.getName().toLowerCase().endsWith(JSON_EXTENSION)
        );
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No JSON snapshot found in: " + folderPath);
        }

        List<File> result = new ArrayList<>(List.of(files));
        result.sort(Comparator.comparing(File::getName));
        return result;
    }
}
