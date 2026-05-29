package window.provider;

import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Provider statico basato su snapshot già caricati in memoria.
 */
public final class StaticSystemStateProvider implements SystemStateProvider {

    private static final double DEFAULT_TIME_TOLERANCE_SECONDS = 1.0E-6;

    private final List<SystemSnapshot> snapshots;
    private final double timeToleranceSeconds;

    public StaticSystemStateProvider(List<SystemSnapshot> snapshots) {
        this(snapshots, DEFAULT_TIME_TOLERANCE_SECONDS);
    }

    public StaticSystemStateProvider(
            List<SystemSnapshot> snapshots,
            double timeToleranceSeconds
    ) {
        if (snapshots == null) {
            throw new IllegalArgumentException("snapshots must not be null.");
        }
        validateFiniteAndNonNegative("timeToleranceSeconds", timeToleranceSeconds);

        List<SystemSnapshot> copiedSnapshots = new ArrayList<>();
        for (SystemSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                throw new IllegalArgumentException(
                        "snapshots must not contain null elements."
                );
            }
            copiedSnapshots.add(snapshot);
        }

        copiedSnapshots.sort(Comparator.comparingDouble(SystemSnapshot::getTimeSeconds));
        this.snapshots = Collections.unmodifiableList(copiedSnapshots);
        this.timeToleranceSeconds = timeToleranceSeconds;
    }

    @Override
    public Optional<SystemSnapshot> findSnapshotAt(double observationTimeSeconds) {
        validateFiniteAndNonNegative(
                "observationTimeSeconds",
                observationTimeSeconds
        );

        for (SystemSnapshot snapshot : snapshots) {
            if (isSameTime(snapshot.getTimeSeconds(), observationTimeSeconds)) {
                return Optional.of(snapshot);
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<SystemSnapshot> findSnapshotAtOrAfter(double timeSeconds) {
        validateFiniteAndNonNegative("timeSeconds", timeSeconds);

        for (SystemSnapshot snapshot : snapshots) {
            if (snapshot.getTimeSeconds() + timeToleranceSeconds >= timeSeconds) {
                return Optional.of(snapshot);
            }
        }

        return Optional.empty();
    }

    public Optional<SystemSnapshot> getFirstSnapshot() {
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(snapshots.get(0));
    }

    public Optional<SystemSnapshot> getLastSnapshot() {
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(snapshots.get(snapshots.size() - 1));
    }

    public List<SystemSnapshot> getSnapshots() {
        return snapshots;
    }

    public double getTimeToleranceSeconds() {
        return timeToleranceSeconds;
    }

    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    private boolean isSameTime(
            double snapshotTimeSeconds,
            double requestedTimeSeconds
    ) {
        return Math.abs(snapshotTimeSeconds - requestedTimeSeconds)
                <= timeToleranceSeconds;
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
