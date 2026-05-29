package test.runner.core;

import java.util.List;

/**
 * Gruppo coerente di test manuali.
 */
public interface ManualTestSuite {

    String getName();

    List<ManualTestCase> getTests();
}
