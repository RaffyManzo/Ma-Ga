package io.reporting;

import config.MaGaConfig;
import config.window.TemporalRuntimeProfile;
import window.source.FilteringSystemStateSource;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Punto unico di composizione del report temporale della finestra adattiva.
 *
 * <p>Il main non conosce più i singoli printer specialistici. Questo oggetto mantiene
 * insieme il report diagnostico generale, i bounds adattivi, il timing, il riuso della
 * popolazione, la sorgente dati e il prefilter.</p>
 */
public final class AdaptiveWindowReportPrinter {
    private final MaGaConfig config;
    private final PrintStream out;

    public AdaptiveWindowReportPrinter(MaGaConfig config) {
        this(config, System.out);
    }

    public AdaptiveWindowReportPrinter(MaGaConfig config, PrintStream out) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.out = Objects.requireNonNull(out, "out must not be null.");
    }

    /**
     * Mantiene compatibilità con eventuali chiamanti precedenti.
     *
     * <p>Il vecchio overload non esponeva il profilo temporale e viene quindi
     * interpretato come replay astratto configurato.</p>
     */
    public void print(
            String requestedSourceMode,
            String snapshotFolder,
            TemporalWindowResult result,
            FilteringSystemStateSource filteredSource
    ) {
        print(
                requestedSourceMode,
                TemporalRuntimeProfile.CONFIGURED_RUNTIME,
                snapshotFolder,
                result,
                filteredSource
        );
    }

    public void print(
            String requestedSourceMode,
            TemporalRuntimeProfile runtimeProfile,
            String snapshotFolder,
            TemporalWindowResult result,
            FilteringSystemStateSource filteredSource
    ) {
        Objects.requireNonNull(requestedSourceMode, "requestedSourceMode must not be null.");
        Objects.requireNonNull(runtimeProfile, "runtimeProfile must not be null.");
        Objects.requireNonNull(snapshotFolder, "snapshotFolder must not be null.");
        Objects.requireNonNull(result, "result must not be null.");
        Objects.requireNonNull(filteredSource, "filteredSource must not be null.");

        printExecutionMetadata(requestedSourceMode, runtimeProfile, snapshotFolder);
        new DeepTemporalWindowDiagnosticPrinter(config, out, 10).print(result);
        new AdaptiveWindowDiagnosticPrinter(out).print(result);
        new TemporalTimingDiagnosticPrinter(out).print(result);
        new PopulationReuseDecisionDiagnosticPrinter(out).print(result);
        new SystemStateSourceDiagnosticPrinter(out).print(result);
        new CandidateFilteringPrinter(out).print(filteredSource.getFilteringResults());
    }

    private void printExecutionMetadata(
            String requestedSourceMode,
            TemporalRuntimeProfile runtimeProfile,
            String snapshotFolder
    ) {
        out.println("============================================================");
        out.println("MA-GA ADAPTIVE WINDOW EXECUTION");
        out.println("============================================================");
        out.println("Requested source mode: " + requestedSourceMode);
        out.println("Runtime profile: " + runtimeProfile);
        out.println("Snapshot folder: " + snapshotFolder);
        out.println();
        out.println("Source mode interpretation:");
        out.println("- JSON_TIME: time-driven replay. The manager requests a logical time and the source must not expose future snapshots.");
        out.println("- JSON_SEQUENCE: ordinal diagnostic replay. All files are consumed in sequence; a positive time shift can be expected after adaptive window changes.");
        out.println();
        out.println("Runtime profile interpretation:");
        out.println("- OBSERVED_RUNTIME: operational verification. DeltaT_min uses the GA runtime observed in the previous window after the initial estimate.");
        out.println("- CONFIGURED_RUNTIME: abstract replay. DeltaT_min uses the configured GA estimate to keep offline experiments reproducible.");
        out.println();
    }
}
