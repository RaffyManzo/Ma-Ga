package window.source;

import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sorgente JSON indicizzata nel tempo logico del manager.
 *
 * <p>Dato un istante richiesto, restituisce lo snapshot più recente che era già
 * disponibile a quell'istante. Non espone mai snapshot futuri. Se tra due
 * richieste non è diventato disponibile un nuovo file, riutilizza l'ultimo
 * snapshot noto: lo stato osservato resta valido fino all'arrivo di una nuova
 * osservazione.</p>
 *
 * <p>Questa semantica distingue il replay temporale da quello sequenziale:
 * {@link SequentialSnapshotReplaySource} consuma ordinalmente tutti i file,
 * mentre questa classe rispetta il tempo logico richiesto dal manager.</p>
 */
public final class TimeIndexedSnapshotReplaySource implements SystemStateSource {
    private static final double DEFAULT_TIME_TOLERANCE_SECONDS = 1.0E-6;

    private final List<SystemSnapshot> snapshots;
    private final String description;
    private final double timeToleranceSeconds;

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
        this.description = description == null
                ? "time-indexed JSON replay"
                : description;
    }

    @Override
    public Optional<SystemStateObservation> nextObservation(SystemStateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null.");
        }

        double requestedTime = request.getRequestedObservationTimeSeconds();
        int selectedIndex = findLatestSnapshotAtOrBefore(requestedTime);
        if (selectedIndex < 0) {
            return Optional.empty();
        }

        SystemSnapshot snapshot = snapshots.get(selectedIndex);
        boolean exactTimeMatch = Math.abs(snapshot.getTimeSeconds() - requestedTime)
                <= timeToleranceSeconds;

        return Optional.of(
                new SystemStateObservation(
                        snapshot,
                        requestedTime,
                        snapshot.getTimeSeconds(),
                        getMode(),
                        description,
                        selectedIndex,
                        exactTimeMatch
                )
        );
    }

    /**
     * Restituisce l'indice dello snapshot più recente non successivo al tempo
     * richiesto. La ricerca binaria mantiene il comportamento corretto anche
     * quando lo stesso snapshot deve essere riutilizzato in finestre diverse.
     */
    private int findLatestSnapshotAtOrBefore(double requestedTime) {
        int low = 0;
        int high = snapshots.size() - 1;
        int selectedIndex = -1;

        while (low <= high) {
            int middle = low + (high - low) / 2;
            SystemSnapshot snapshot = snapshots.get(middle);

            if (snapshot.getTimeSeconds() <= requestedTime + timeToleranceSeconds) {
                selectedIndex = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        return selectedIndex;
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

    private static void validateFiniteAndNonNegative(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }
}
