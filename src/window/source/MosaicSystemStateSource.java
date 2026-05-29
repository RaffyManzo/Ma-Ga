package window.source;

import model.snapshot.SystemSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * Sorgente dati per MOSAIC.
 *
 * <p>Il collegamento concreto con MOSAIC verrà implementato nel bridge.
 * Il TemporalWindowManager non cambia: continua a ricevere SystemSnapshot.</p>
 */
public final class MosaicSystemStateSource implements SystemStateSource {

    private final MosaicSnapshotBridge bridge;

    public MosaicSystemStateSource(MosaicSnapshotBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null.");
    }

    @Override
    public Optional<SystemStateObservation> nextObservation(SystemStateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null.");
        }

        double requestedTime = request.getRequestedObservationTimeSeconds();

        return bridge.readSnapshot(requestedTime)
                .map(snapshot -> new SystemStateObservation(
                        snapshot,
                        requestedTime,
                        snapshot.getTimeSeconds(),
                        getMode(),
                        bridge.getDescription(),
                        request.getWindowIndex(),
                        Math.abs(snapshot.getTimeSeconds() - requestedTime) <= 1.0E-6
                ));
    }

    @Override
    public SystemStateSourceMode getMode() {
        return SystemStateSourceMode.MOSAIC_LIVE;
    }

    @Override
    public String getDescription() {
        return bridge.getDescription();
    }
}
