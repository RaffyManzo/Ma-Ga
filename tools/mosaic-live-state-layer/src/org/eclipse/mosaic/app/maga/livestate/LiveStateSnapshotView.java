package org.eclipse.mosaic.app.maga.livestate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LiveStateSnapshotView {

    private final long tickTimeNs;
    private final List<LiveVehicleState> activeVehicles;
    private final List<LiveTaskState> pendingTasks;

    LiveStateSnapshotView(
            long tickTimeNs,
            List<LiveVehicleState> activeVehicles,
            List<LiveTaskState> pendingTasks
    ) {
        this.tickTimeNs = tickTimeNs;
        this.activeVehicles = Collections.unmodifiableList(new ArrayList<>(activeVehicles));
        this.pendingTasks = Collections.unmodifiableList(new ArrayList<>(pendingTasks));
    }

    long getTickTimeNs() {
        return tickTimeNs;
    }

    List<LiveVehicleState> getActiveVehicles() {
        return activeVehicles;
    }

    List<LiveTaskState> getPendingTasks() {
        return pendingTasks;
    }
}
