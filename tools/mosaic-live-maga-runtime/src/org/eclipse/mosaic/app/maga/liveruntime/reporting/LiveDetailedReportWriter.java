package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.MaGaConfig;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LiveDetailedReportWriter {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    LiveDetailedReportArtifacts write(
            Path reportingDir,
            LiveReportingSummary summary,
            List<TemporalStepResult> appliedSteps,
            MaGaConfig maGaConfig
    ) throws IOException {
        Files.createDirectories(reportingDir);
        Path txt = reportingDir.resolve("live_detailed_execution_report.txt");
        Path markdown = reportingDir.resolve("live_detailed_execution_report.md");
        Path json = reportingDir.resolve("live_detailed_execution_report.json");

        TemporalWindowResult appliedResult = new TemporalWindowResult(appliedSteps);
        try (PrintStream out = new PrintStream(
                Files.newOutputStream(txt),
                true,
                StandardCharsets.UTF_8.name()
        )) {
            new LiveDetailedReportPrinter().print(out, summary, appliedResult, maGaConfig);
        }

        Files.writeString(markdown, markdown(summary), StandardCharsets.UTF_8);
        Files.writeString(json, gson.toJson(summary), StandardCharsets.UTF_8);
        return new LiveDetailedReportArtifacts(txt, markdown, json);
    }

    private static String markdown(LiveReportingSummary summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Native Live Detailed Execution Report\n\n");
        builder.append("- Scenario: `").append(summary.scenarioName).append("`\n");
        builder.append("- Profile: `").append(summary.profile).append("`\n");
        builder.append("- Experimental variant: `").append(summary.experimentalVariant).append("`\n");
        builder.append("- Bridge: `").append(summary.bridgeDescription).append("`\n");
        builder.append("- Source mode: `").append(summary.sourceMode).append("`\n");
        builder.append("- Optimization source: `").append(summary.optimizationSourceDescription).append("`\n");
        builder.append("- Population reuse policy: `").append(summary.populationReusePolicyDescription).append("`\n");
        builder.append("- Effective fitness weights: `").append(summary.effectiveFitnessWeights).append("`\n");
        builder.append("- Submitted jobs: `").append(summary.submitted).append("`\n");
        builder.append("- Applied jobs: `").append(summary.applied).append("`\n");
        builder.append("- Stale discarded jobs: `").append(summary.staleDiscarded).append("`\n");
        builder.append("- Failed jobs: `").append(summary.failed).append("`\n");
        builder.append("- Null step results: `").append(summary.nullResults).append("`\n\n");
        builder.append("## Artifacts\n\n");
        for (var entry : summary.artifacts.entrySet()) {
            builder.append("- ").append(entry.getKey()).append(": `")
                    .append(entry.getValue()).append("`\n");
        }
        builder.append("\n## Runtime Timing\n\n");
        builder.append("- mean: `").append(summary.wallClockTiming.get("mean")).append("`\n");
        builder.append("- median: `").append(summary.wallClockTiming.get("median")).append("`\n");
        builder.append("- p95: `").append(summary.wallClockTiming.get("p95")).append("`\n\n");
        builder.append("See `live_detailed_execution_report.txt` for the full console-readable report.\n");
        return builder.toString();
    }

    public static final class LiveDetailedReportArtifacts {
        private final Path txt;
        private final Path markdown;
        private final Path json;

        LiveDetailedReportArtifacts(Path txt, Path markdown, Path json) {
            this.txt = txt;
            this.markdown = markdown;
            this.json = json;
        }

        public Path getTxt() {
            return txt;
        }

        public Path getMarkdown() {
            return markdown;
        }

        public Path getJson() {
            return json;
        }
    }
}
