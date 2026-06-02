package io.reporting;

import config.MaGaConfig;
import config.window.TemporalRuntimeProfile;
import window.source.FilteringSystemStateSource;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.Objects;

/** Punto unico di composizione del report temporale della finestra adattiva. */
public final class AdaptiveWindowReportPrinter {
    private final MaGaConfig config;
    private final PrintStream out;

    public AdaptiveWindowReportPrinter(MaGaConfig config) { this(config, System.out); }
    public AdaptiveWindowReportPrinter(MaGaConfig config, PrintStream out) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    public void print(String sourceMode, String folder, TemporalWindowResult result, FilteringSystemStateSource filteredSource) {
        print(sourceMode, TemporalRuntimeProfile.CONFIGURED_RUNTIME, folder, result, filteredSource);
    }

    public void print(
            String sourceMode,
            TemporalRuntimeProfile runtimeProfile,
            String folder,
            TemporalWindowResult result,
            FilteringSystemStateSource filteredSource
    ) {
        printExecutionMetadata(sourceMode, runtimeProfile, folder);
        new DeepTemporalWindowDiagnosticPrinter(config, out, 10).print(result);
        new DeadlineBestEffortDiagnosticPrinter(out, 10).print(result);
        new CloudGatewayDiagnosticPrinter(out).print(result, filteredSource.getFilteringResults());
        new MobilityDiagnosticPrinter(config, out, 10).print(result);
        new LatencyDiagnosticPrinter(config, out, 10).print(result);
        new AdaptiveWindowDiagnosticPrinter(out).print(result);
        new TemporalTimingDiagnosticPrinter(out).print(result);
        new PopulationReuseDecisionDiagnosticPrinter(out).print(result);
        new SystemStateSourceDiagnosticPrinter(out).print(result);
        new CandidateFilteringPrinter(out).print(filteredSource.getFilteringResults());
    }

    private void printExecutionMetadata(String sourceMode, TemporalRuntimeProfile profile, String folder) {
        out.println("============================================================");
        out.println("MA-GA ADAPTIVE WINDOW EXECUTION");
        out.println("============================================================");
        out.println("Requested source mode: " + sourceMode);
        out.println("Runtime profile: " + profile);
        out.println("Snapshot folder: " + folder);
        out.println();
        out.println("Gateway interpretation:");
        out.println("- STRICT_GATEWAY: every observed vehicle must expose one active access link.");
        out.println("- CLOUD decisions derive coverage and link instability from the active gateway.");
        out.println("- No silent CLOUD_STABLE_PLACEHOLDER fallback is enabled.");
        out.println();
    }
}
