package test.runner.core;

import java.util.List;

/**
 * Esegue suite di test manuali senza dipendere da JUnit.
 */
public final class ManualTestExecutor {

    private ManualTestExecutor() {
    }

    public static void run(List<ManualTestSuite> suites) {
        int total = 0;
        int passed = 0;

        for (ManualTestSuite suite : suites) {
            System.out.println();
            System.out.println("== " + suite.getName() + " ==");

            for (ManualTestCase test : suite.getTests()) {
                total++;
                runTest(test);
                passed++;
            }
        }

        System.out.println();
        System.out.println("============================================================");
        System.out.println("ALL MANUAL TESTS PASSED (" + passed + "/" + total + ")");
        System.out.println("============================================================");
    }

    private static void runTest(ManualTestCase test) {
        try {
            test.run();
            System.out.println("[PASSED] " + test.getName());
        } catch (RuntimeException | Error error) {
            System.out.println("[FAILED] " + test.getName());
            throw error;
        }
    }
}
