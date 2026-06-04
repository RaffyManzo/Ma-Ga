import config.mobility.MobilityConfig;
import io.snapshot.SnapshotLoader;
import model.bandwidth.BandwidthPoolResolver;
import model.bandwidth.BandwidthPoolType;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;
import window.dynamicity.calculator.LinkDynamicityCalculator;
import window.timing.CoverageReferenceCalculator;

import java.nio.file.Path;

/**
 * Harness riutilizzabile per validare il contratto snapshot 10I-pre.
 *
 * <p>Il tool resta fuori da src/ e usa loader, validator e model reali del
 * core, senza introdurre dipendenze esterne o modificare il flusso MA-GA.</p>
 */
public final class SnapshotContractValidationMain {
    private final SnapshotLoader loader = new SnapshotLoader();
    private final Path fixturesDir;
    private int passed;
    private int failed;

    private SnapshotContractValidationMain(Path fixturesDir) {
        this.fixturesDir = fixturesDir;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: SnapshotContractValidationMain <fixturesDir>");
            System.exit(2);
        }

        SnapshotContractValidationMain harness =
                new SnapshotContractValidationMain(Path.of(args[0]));
        harness.runAll();
    }

    private void runAll() {
        run("A - LOCAL-only without active gateway is accepted", this::caseLocalOnly);
        run("B - V2V-only without active gateway is accepted", this::caseV2vOnly);
        run("C - mixed gateway/local/V2V snapshot is accepted", this::caseMixed);
        run("D - multiple active access links are rejected", this::caseMultipleActiveLinks);
        run("E - CLOUD without active gateway is rejected", this::caseCloudWithoutGateway);
        run("F - EDGE without active gateway is rejected", this::caseEdgeWithoutGateway);
        run("G - link dynamicity handles uncovered vehicles", this::caseLinkDynamicity);
        run("H - coverage reference uses only active-link vehicles", this::caseMixedCoverageReference);
        run("I - coverage reference fallback without gateway", this::caseNoGatewayCoverageReference);

        System.out.println("Snapshot contract validation completed");
        System.out.println("testsExecuted=" + (passed + failed));
        System.out.println("testsPassed=" + passed);
        System.out.println("testsFailed=" + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private void caseLocalOnly() throws Exception {
        loadAccepted("local_only_no_gateway.json");
    }

    private void caseV2vOnly() throws Exception {
        SystemSnapshot snapshot = loadAccepted("v2v_only_no_gateway.json");
        NodeCandidate v2v = firstCandidateOfType(snapshot, NodeType.VEHICLE);
        BandwidthPoolSnapshot pool = new BandwidthPoolResolver().resolve(snapshot, v2v);
        assertEquals("pool_v2v_veh_1_veh_2", pool.getPoolId(), "V2V pool id");
        assertEquals(BandwidthPoolType.DIRECT_V2V, pool.getPoolType(), "V2V pool type");
    }

    private void caseMixed() throws Exception {
        loadAccepted("mixed_gateway_and_local.json");
    }

    private void caseMultipleActiveLinks() {
        expectRejected(
                "invalid_multiple_active_links.json",
                "must not have more than one active access link"
        );
    }

    private void caseCloudWithoutGateway() {
        expectRejected(
                "invalid_cloud_without_active_gateway.json",
                "requires an active access link"
        );
    }

    private void caseEdgeWithoutGateway() {
        expectRejected(
                "invalid_edge_without_active_gateway.json",
                "requires an active access link"
        );
    }

    private void caseLinkDynamicity() throws Exception {
        SystemSnapshot covered = loadAccepted("mixed_gateway_and_local.json");
        SystemSnapshot uncovered = loadAccepted("local_only_no_gateway.json");
        LinkDynamicityCalculator calculator =
                new LinkDynamicityCalculator(MobilityConfig.defaultConfig());

        double coveredToUncovered = calculator.compute(covered, uncovered);
        assertTrue(
                coveredToUncovered > 0.0,
                "covered-to-uncovered dynamicity must be positive"
        );

        double uncoveredToUncovered = calculator.compute(uncovered, uncovered);
        assertEquals(0.0, uncoveredToUncovered, "uncovered-to-uncovered dynamicity");
    }

    private void caseMixedCoverageReference() throws Exception {
        SystemSnapshot mixed = loadAccepted("mixed_gateway_and_local.json");
        CoverageReferenceCalculator calculator =
                new CoverageReferenceCalculator(MobilityConfig.defaultConfig());

        double reference = calculator.computeReferenceCoverageSeconds(mixed);
        assertTrue(reference > 0.0, "mixed coverage reference must be positive");
        assertTrue(
                calculator.hasReferenceCoverage(mixed),
                "mixed snapshot must have reference coverage"
        );
    }

    private void caseNoGatewayCoverageReference() throws Exception {
        SystemSnapshot localOnly = loadAccepted("local_only_no_gateway.json");
        CoverageReferenceCalculator calculator =
                new CoverageReferenceCalculator(MobilityConfig.defaultConfig());

        assertEquals(
                0.0,
                calculator.computeReferenceCoverageSeconds(localOnly),
                "no-gateway coverage reference"
        );
        assertTrue(
                !calculator.hasReferenceCoverage(localOnly),
                "no-gateway snapshot must not have reference coverage"
        );
    }

    private SystemSnapshot loadAccepted(String fixtureName) throws Exception {
        return loader.load(fixturesDir.resolve(fixtureName).toString());
    }

    private void expectRejected(String fixtureName, String expectedMessagePart) {
        try {
            loadAccepted(fixtureName);
            throw new AssertionError("Fixture should have been rejected: " + fixtureName);
        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message == null || !message.contains(expectedMessagePart)) {
                throw new AssertionError(
                        "Unexpected rejection for " + fixtureName + ": " + message
                );
            }
        }
    }

    private NodeCandidate firstCandidateOfType(SystemSnapshot snapshot, NodeType type) {
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getType() == type) {
                return candidate;
            }
        }
        throw new AssertionError("Missing candidate of type " + type);
    }

    private void run(String name, TestCase testCase) {
        try {
            testCase.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (Throwable ex) {
            failed++;
            System.out.println("FAIL " + name);
            ex.printStackTrace(System.out);
        }
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected=" + expected + ", actual=" + actual
            );
        }
    }

    private void assertEquals(double expected, double actual, String message) {
        double tolerance = 0.000000001;
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(
                    message + ": expected=" + expected + ", actual=" + actual
            );
        }
    }

    @FunctionalInterface
    private interface TestCase {
        void run() throws Exception;
    }
}
