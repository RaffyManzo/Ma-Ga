package org.eclipse.mosaic.app.maga.livestate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class LiveStateCache {

    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000.0;
    private static final LiveStateCache INSTANCE = new LiveStateCache();

    private final ConcurrentMap<String, LiveVehicleState> vehicles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LiveTaskState> taskDefinitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LiveTaskState> activeTasks = new ConcurrentHashMap<>();

    static LiveStateCache getInstance() {
        return INSTANCE;
    }

    static boolean runImmutableSnapshotViewSelfTest() {
        LiveStateCache cache = new LiveStateCache();
        cache.registerVehicleStarted("veh_test", 1L, true);
        cache.updateVehicle("veh_test", 2L, 1.0, 2.0, 3.0, true);
        LiveStateSnapshotView before = cache.snapshotAtOrBefore(2L);
        int beforeSize = before.getActiveVehicles().size();
        double beforeX = before.getActiveVehicles().get(0).getProjectedX();
        cache.updateVehicle("veh_test", 3L, 10.0, 20.0, 30.0, true);
        cache.registerVehicleStarted("veh_added", 3L, true);
        return beforeSize == 1 && before.getActiveVehicles().get(0).getProjectedX() == beforeX;
    }

    void reset() {
        vehicles.clear();
        taskDefinitions.clear();
        activeTasks.clear();
    }

    void installTaskDefinitions(List<LiveTaskState> tasks) {
        for (LiveTaskState task : tasks) {
            taskDefinitions.put(task.getTaskId(), task);
        }
    }

    int installGeneratedTaskDefinitions(List<LiveTaskState> tasks) {
        int inserted = 0;
        for (LiveTaskState task : tasks) {
            if (task == null || task.getActivationTimeNs() < 0) {
                continue;
            }
            if (taskDefinitions.putIfAbsent(task.getTaskId(), task) == null) {
                inserted++;
            }
        }
        return inserted;
    }

    void registerVehicleStarted(String vehicleId, long timeNs, boolean adHocEnabled) {
        vehicles.put(
                vehicleId,
                new LiveVehicleState(
                        vehicleId,
                        timeNs,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN,
                        adHocEnabled,
                        true
                )
        );
    }

    void updateVehicle(
            String vehicleId,
            long timeNs,
            double projectedX,
            double projectedY,
            double speedMetersPerSecond,
            boolean adHocEnabled
    ) {
        vehicles.put(
                vehicleId,
                new LiveVehicleState(
                        vehicleId,
                        timeNs,
                        projectedX,
                        projectedY,
                        speedMetersPerSecond,
                        adHocEnabled,
                        true
                )
        );
    }

    void markVehicleInactive(String vehicleId, long timeNs, boolean adHocEnabled) {
        LiveVehicleState previous = vehicles.get(vehicleId);
        double projectedX = previous == null ? Double.NaN : previous.getProjectedX();
        double projectedY = previous == null ? Double.NaN : previous.getProjectedY();
        double speed = previous == null ? Double.NaN : previous.getSpeedMetersPerSecond();
        vehicles.put(
                vehicleId,
                new LiveVehicleState(vehicleId, timeNs, projectedX, projectedY, speed, adHocEnabled, false)
        );
    }

    int activateDueTasks(long tickTimeNs) {
        int activated = 0;
        for (LiveTaskState task : taskDefinitions.values()) {
            if (task.getActivationTimeNs() <= tickTimeNs && !activeTasks.containsKey(task.getTaskId())) {
                activeTasks.put(task.getTaskId(), task);
                activated++;
            }
        }
        return activated;
    }

    int removeExpiredTasks(long tickTimeNs) {
        int removed = 0;

        for (Map.Entry<String, LiveTaskState> entry : taskDefinitions.entrySet()) {
            String taskId = entry.getKey();
            LiveTaskState task = entry.getValue();

            long deadlineDurationNs = Math.round(
                    task.getDeadlineSeconds() * NANOSECONDS_PER_SECOND
            );

            long deadlineTimeNs = task.getActivationTimeNs() + deadlineDurationNs;

            if (deadlineTimeNs > tickTimeNs) {
                continue;
            }

            if (taskDefinitions.remove(taskId, task)) {
                removed++;
            }

            activeTasks.remove(taskId);
        }

        return removed;
    }

    List<LiveTaskState> expiredTasksDueAt(long tickTimeNs) {
        List<LiveTaskState> expired = new ArrayList<>();
        for (LiveTaskState task : taskDefinitions.values()) {
            long deadlineDurationNs = Math.round(
                    task.getDeadlineSeconds() * NANOSECONDS_PER_SECOND
            );
            long deadlineTimeNs = task.getActivationTimeNs() + deadlineDurationNs;
            if (deadlineTimeNs <= tickTimeNs) {
                expired.add(task);
            }
        }
        expired.sort(Comparator.comparing(LiveTaskState::getTaskId, LiveStateCache::naturalCompare));
        return expired;
    }

    int vehicleCacheSize() {
        return vehicles.size();
    }

    int taskDefinitionCacheSize() {
        return taskDefinitions.size();
    }

    int activeTaskCacheSize() {
        return activeTasks.size();
    }

    void markTasksExported(List<LiveTaskState> tasks) {
        for (LiveTaskState task : tasks) {
            activeTasks.computeIfPresent(
                    task.getTaskId(),
                    (taskId, current) -> current.withStatus(LiveTaskStatus.EXPORTED_TO_PREVIEW)
            );
        }
    }

    LiveStateSnapshotView snapshotAtOrBefore(long tickTimeNs) {
        List<LiveVehicleState> activeVehicles = new ArrayList<>();
        for (LiveVehicleState state : vehicles.values()) {
            if (state.isActive() && state.getLastUpdateTimeNs() <= tickTimeNs) {
                activeVehicles.add(state);
            }
        }
        activeVehicles.sort(Comparator.comparing(LiveVehicleState::getVehicleId, LiveStateCache::naturalCompare));

        List<LiveTaskState> pendingTasks = new ArrayList<>();
        for (Map.Entry<String, LiveTaskState> entry : activeTasks.entrySet()) {
            LiveTaskState task = entry.getValue();
            if (task.getStatus() == LiveTaskStatus.PENDING && task.getActivationTimeNs() <= tickTimeNs) {
                pendingTasks.add(task);
            }
        }
        pendingTasks.sort(Comparator.comparing(LiveTaskState::getTaskId, LiveStateCache::naturalCompare));
        return new LiveStateSnapshotView(tickTimeNs, activeVehicles, pendingTasks);
    }

    static int naturalCompare(String left, String right) {
        String leftPrefix = left.replaceAll("\\d+$", "");
        String rightPrefix = right.replaceAll("\\d+$", "");
        if (leftPrefix.equals(rightPrefix)) {
            String leftDigits = left.substring(leftPrefix.length());
            String rightDigits = right.substring(rightPrefix.length());
            if (!leftDigits.isEmpty() && !rightDigits.isEmpty()) {
                int numeric = Integer.compare(Integer.parseInt(leftDigits), Integer.parseInt(rightDigits));
                if (numeric != 0) {
                    return numeric;
                }
            }
        }
        return left.compareTo(right);
    }
}
