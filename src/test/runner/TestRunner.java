package test.runner;

import test.runner.core.ManualTestExecutor;
import test.runner.suite.FitnessEvaluatorTestSuite;
import test.runner.suite.OperatorTestSuite;
import test.runner.suite.SnapshotValidatorTestSuite;

import java.util.List;

/**
 * Entry point unico per i test manuali del prototipo MA-GA.
 */
public final class TestRunner {

    private TestRunner() {
    }

    public static void main(String[] args) {
        ManualTestExecutor.run(
                List.of(
                        new SnapshotValidatorTestSuite(),
                        new FitnessEvaluatorTestSuite(),
                        new OperatorTestSuite()
                )
        );
    }
}
