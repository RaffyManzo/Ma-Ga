package window.source;

import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sorgente JSON per test offline sequenziali.
 *
 * <p>Restituisce gli snapshot nell'ordine della lista, indipendentemente dal
 * tempo richiesto dalla finestra adattiva. Questa modalità serve quando gli
 * snapshot sono una successione di fotografie già decisa e vogliamo eseguirle
 * tutte senza saltarne nessuna.</p>
 */
public final class SequentialSnapshotReplaySource implements SystemStateSource {

    private final List<SystemSnapshot> snapshots;
    private final String description;
    private int cursor;

    public SequentialSnapshotReplaySource(List<SystemSnapshot> snapshots) {
        this(snapshots, "sequential JSON replay");
    }

    public SequentialSnapshotReplaySource(
            List<SystemSnapshot> snapshots,
            String description
    ) {
        if (snapshots == null) {
            throw new IllegalArgumentException("snapshots must not be null.");
        }

        List<SystemSnapshot> copied = new ArrayList<>();
        for (SystemSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                throw new IllegalArgumentException("snapshots must not contain null elements.");
            }
            copied.add(snapshot);
        }
        copied.sort(Comparator.comparingDouble(SystemSnapshot::getTimeSeconds));

        this.snapshots = Collections.unmodifiableList(copied);
        this.description = description == null ? "sequential JSON replay" : description;
        this.cursor = 0;
    }

    @Override
    public Optional<SystemStateObservation> nextObservation(SystemStateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null.");
        }
        if (cursor >= snapshots.size()) {
            return Optional.empty();
        }

        int currentIndex = cursor;
        SystemSnapshot snapshot = snapshots.get(cursor++);

        boolean exactTimeMatch = Math.abs(
                snapshot.getTimeSeconds() - request.getRequestedObservationTimeSeconds()
        ) <= 1.0E-6;

        return Optional.of(
                new SystemStateObservation(
                        snapshot,
                        request.getRequestedObservationTimeSeconds(),
                        snapshot.getTimeSeconds(),
                        getMode(),
                        description,
                        currentIndex,
                        exactTimeMatch
                )
        );
    }

    @Override
    public SystemStateSourceMode getMode() {
        return SystemStateSourceMode.JSON_SEQUENTIAL_REPLAY;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public int size() {
        return snapshots.size();
    }

    public int getCursor() {
        return cursor;
    }

    public boolean isExhausted() {
        return cursor >= snapshots.size();
    }

    public List<SystemSnapshot> getSnapshots() {
        return snapshots;
    }
}
