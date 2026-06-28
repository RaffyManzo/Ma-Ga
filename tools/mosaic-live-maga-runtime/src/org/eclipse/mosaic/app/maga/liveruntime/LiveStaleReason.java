package org.eclipse.mosaic.app.maga.liveruntime;

/** Motivo per cui un risultato GA non può essere applicato. */
public enum LiveStaleReason {
    NONE(false, false),
    WALL_CLOCK(true, false),
    SIMULATION_AGE(false, true),
    WALL_CLOCK_AND_SIMULATION_AGE(true, true);

    private final boolean wallClock;
    private final boolean simulationAge;

    LiveStaleReason(boolean wallClock, boolean simulationAge) {
        this.wallClock = wallClock;
        this.simulationAge = simulationAge;
    }

    public boolean includesWallClock() { return wallClock; }
    public boolean includesSimulationAge() { return simulationAge; }
    public boolean isStale() { return this != NONE; }

    public static LiveStaleReason of(boolean wallClock, boolean simulationAge) {
        if (wallClock && simulationAge) { return WALL_CLOCK_AND_SIMULATION_AGE; }
        if (wallClock) { return WALL_CLOCK; }
        if (simulationAge) { return SIMULATION_AGE; }
        return NONE;
    }
}
