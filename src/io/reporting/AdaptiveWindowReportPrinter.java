package io.reporting;

import config.MaGaConfig;
import config.window.TemporalRuntimeProfile;
import window.source.FilteringSystemStateSource;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.Objects;

/** Punto unico di composizione del report temporale. */
public final class AdaptiveWindowReportPrinter {
    private final MaGaConfig config; private final PrintStream out;
    public AdaptiveWindowReportPrinter(MaGaConfig config){this(config,System.out);} public AdaptiveWindowReportPrinter(MaGaConfig config,PrintStream out){this.config=Objects.requireNonNull(config,"config must not be null.");this.out=Objects.requireNonNull(out,"out must not be null.");}
    public void print(String sourceMode,String folder,TemporalWindowResult result,FilteringSystemStateSource filteredSource){print(sourceMode,TemporalRuntimeProfile.CONFIGURED_RUNTIME,folder,result,filteredSource);}
    public void print(String sourceMode,TemporalRuntimeProfile profile,String folder,TemporalWindowResult result,FilteringSystemStateSource filteredSource){metadata(sourceMode,profile,folder);new DeepTemporalWindowDiagnosticPrinter(config,out,10).print(result);new DeadlineBestEffortDiagnosticPrinter(out,10).print(result);new CloudGatewayDiagnosticPrinter(out).print(result,filteredSource.getFilteringResults());new AccessLinkDynamicityDiagnosticPrinter(config.getMobilityConfig(),out,10).print(result);new BandwidthPoolDiagnosticPrinter(out).print(result);new MobilityDiagnosticPrinter(config,out,10).print(result);new LatencyDiagnosticPrinter(config,out,10).print(result);new AdaptiveWindowDiagnosticPrinter(out).print(result);new TemporalTimingDiagnosticPrinter(out).print(result);new PopulationReuseDecisionDiagnosticPrinter(out).print(result);new SystemStateSourceDiagnosticPrinter(out).print(result);new CandidateFilteringPrinter(out).print(filteredSource.getFilteringResults());}
    private void metadata(String sourceMode,TemporalRuntimeProfile profile,String folder){out.println("============================================================");out.println("MA-GA ADAPTIVE WINDOW EXECUTION");out.println("============================================================");out.println("Requested source mode: "+sourceMode);out.println("Runtime profile: "+profile);out.println("Snapshot folder: "+folder);out.println();out.println("Bandwidth interpretation:");out.println("- 18.2: a GLOBAL pool reproduces Bmax from the formalization.");out.println("- 18.3: EDGE/CLOUD resolve the pool through the active gateway; V2V can bind a DIRECT_V2V pool.");out.println("- Hierarchical bandwidth repair checks candidateId first and poolId second.");out.println();}
}
