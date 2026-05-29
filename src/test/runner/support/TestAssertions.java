package test.runner.support;

/**
 * Assertion minimali per i runner manuali.
 */
public final class TestAssertions {

    private static final double EPSILON = 1.0E-6;

    private TestAssertions() {
    }

    public static void assertEquals(
            String expected,
            String actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + " Expected: " + expected + ", actual: " + actual
            );
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertAlmostEquals(
            double expected,
            double actual,
            String message
    ) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(
                    message + " Expected: " + expected + ", actual: " + actual
            );
        }
    }

    public static void assertThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }

        throw new AssertionError(message);
    }
}
