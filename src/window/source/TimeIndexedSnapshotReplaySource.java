package window.source;

import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sorgente JSON indicizzata nel tempo.
 *
 * <p>Dato un tempo richiesto, restituisce il primo snapshot disponibile a quel
 * tempo o dopo. Può saltare file se la finestra adattiva produce tempi non
 * allineati agli snapshot salvati.</p>
 */
public final class TimeIndexedSnapshotReplaySource implements SystemStateSource {

    private static final double DEFAULT_TIME_TOLERANCE_SECONDS = 1.0E-6;

    private final List<SystemSnapshot> snapshots;
    private final String description;
    private final double timeToleranceSeconds;
    private int cursor;

    public TimeIndexedSnapshotReplaySource(List<SystemSnapshot> snapshots) {
        this(snapshots, DEFAULT_TIME_TOLERANCE_SECONDS, "time-indexed JSON replay");
    }

    public TimeIndexedSnapshotReplaySource(
            List<SystemSnapshot> snapshots,
            double timeToleranceSeconds,
            String description
    ) {
        if (snapshots == null) {
            throw new IllegalArgumentException("snapshots must not be null.");
        }
        validateFiniteAndNonNegative("timeToleranceSeconds", timeToleranceSeconds);

        List<SystemSnapshot> copied = new ArrayList<>();
        for (SystemSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                throw new IllegalArgumentException("snapshots must not contain null elements.");
            }
            copied.add(snapshot);
        }
        copied.sort(Comparator.comparingDouble(SystemSnapshot::getTimeSeconds));

        this.snapshots = Collections.unmodifiableList(copied);
        this.timeToleranceSeconds = timeToleranceSeconds;
        this.description = description == null ? "time-indexed JSON replay" : description;
        this.cursor = 0;
    }

    @Override
    public Optional<SystemStateObservation> nextObservation(SystemStateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null.");
        }

        double requestedTime = request.getRequestedObservationTimeSeconds();

        for (int i = cursor; i < snapshots.size(); i++) {
            SystemSnapshot snapshot = snapshots.get(i);
            if (snapshot.getTimeSeconds() + timeToleranceSeconds >= requestedTime) {
                cursor = i + 1;

                boolean exactTimeMatch = Math.abs(snapshot.getTimeSeconds() - requestedTime)
                        <= timeToleranceSeconds;

                return Optional.of(
                        new SystemStateObservation(
                                snapshot,
                                requestedTime,
                                snapshot.getTimeSeconds(),
                                getMode(),
                                description,
                                i,
                                exactTimeMatch
                        )
                );
            }
        }

        return Optional.empty();
    }

    @Override
    public SystemStateSourceMode getMode() {
        return SystemStateSourceMode.JSON_TIME_INDEXED_REPLAY;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public List<SystemSnapshot> getSnapshots() {
        return snapshots;
    }

    private static void validateFiniteAndNonNegative(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }
}
