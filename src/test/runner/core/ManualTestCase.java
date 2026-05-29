package test.runner.core;

import java.util.Objects;

/**
 * Singolo caso di test manuale.
 */
public final class ManualTestCase {

    private final String name;
    private final Runnable action;

    private ManualTestCase(String name, Runnable action) {
        this.name = Objects.requireNonNull(name, "name must not be null.");
        this.action = Objects.requireNonNull(action, "action must not be null.");
    }

    public static ManualTestCase of(String name, Runnable action) {
        return new ManualTestCase(name, action);
    }

    public String getName() {
        return name;
    }

    public void run() {
        action.run();
    }
}
