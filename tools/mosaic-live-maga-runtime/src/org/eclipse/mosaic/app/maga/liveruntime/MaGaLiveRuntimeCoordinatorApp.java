package org.eclipse.mosaic.app.maga.liveruntime;

import config.MaGaConfig;
import config.ga.GaParameterScalingMode;
import config.window.TemporalWindowConfig;
import ga.core.MaGaOptimizer;
import org.eclipse.mosaic.app.maga.livestate.LiveStateLayerRuntimeFacade;
import org.eclipse.mosaic.app.maga.liveruntime.reporting.LiveDetailedReportWriter;
import org.eclipse.mosaic.app.maga.liveruntime.reporting.LiveNativeReportingCollector;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import window.core.TemporalWindowManager;
import window.dynamicity.DynamicityEvaluator;
import window.event.CriticalEventDetector;
import window.population.PopulationAdapter;
import window.source.MosaicSystemStateSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

public class MaGaLiveRuntimeCoordinatorApp extends AbstractApplication<ServerOperatingSystem> {

    private MaGaLiveRuntimeConfig runtimeConfig;
    private LiveStateLayerRuntimeFacade stateFacade;
    private MaGaLiveMosaicSnapshotBridge bridge;
    private MosaicSystemStateSource systemStateSource;
    private LiveStrategyApplier strategyApplier;
    private LiveRuntimeTraceWriter traceWriter;
    private LiveNativeReportingCollector reportingCollector;
    private LiveGaExecutionCoordinator executionCoordinator;
    private MaGaConfig maGaConfig;
    private Path runDirectory;

    @Override
    public void onStartup() {
        runtimeConfig = MaGaLiveRuntimeConfig.load(getOs().getConfigurationPath());
        stateFacade = LiveStateLayerRuntimeFacade.load(getOs().getConfigurationPath());
        stateFacade.resetForRun();
        bridge = new MaGaLiveMosaicSnapshotBridge();
        systemStateSource = new MosaicSystemStateSource(bridge);
        strategyApplier = new LiveStrategyApplier();

        try {
            runDirectory = resolveRunDirectory();
            traceWriter = new LiveRuntimeTraceWriter(
                    runDirectory,
                    runtimeConfig.profileName(),
                    runtimeConfig.getPublishedSnapshotCopyLimit()
            );
            if (runtimeConfig.isNativeLiveDetailedReportingEnabled()) {
                reportingCollector = new LiveNativeReportingCollector(
                        traceWriter.getOutputDir(),
                        runtimeConfig.getScenarioName(),
                        runtimeConfig.profileName(),
                        bridge.getDescription(),
                        String.valueOf(systemStateSource.getMode()),
                        stateFacade.configuredCellProfileSummary(),
                        stateFacade.runtimeAccountingSource()
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to open live MA-GA runtime traces", e);
        }

        TemporalWindowConfig temporalConfig = TemporalWindowConfig.configuredBoundsForReplay(
                runtimeConfig.getTemporalInitialWindowSeconds(),
                runtimeConfig.getConfiguredGaRuntimeEstimateSeconds(),
                runtimeConfig.getConfiguredMaxWindowSeconds()
        );
        GaParameterScalingMode scalingMode = runtimeConfig.getGaParameterScalingMode();
        maGaConfig = MaGaConfig.defaultConfig(scalingMode);
        MaGaOptimizer optimizer = new MaGaOptimizer(maGaConfig);
        DynamicityEvaluator dynamicityEvaluator =
                new DynamicityEvaluator(temporalConfig, maGaConfig.getMobilityConfig());
        PopulationAdapter populationAdapter =
                new PopulationAdapter(temporalConfig, maGaConfig, new Random(5L));
        CriticalEventDetector criticalEventDetector =
                (currentTimeSeconds, maxTimeSeconds) -> Optional.empty();
        TemporalWindowManager manager = new TemporalWindowManager(
                temporalConfig,
                optimizer,
                dynamicityEvaluator,
                populationAdapter,
                criticalEventDetector,
                systemStateSource,
                maGaConfig.getGeneticAlgorithmConfig().getPopulationSize()
        );
        LiveGaOverrunDeadlinePolicy deadlinePolicy =
                new LiveGaOverrunDeadlinePolicy(temporalConfig, maGaConfig.getMobilityConfig());
        executionCoordinator = new LiveGaExecutionCoordinator(
                runtimeConfig,
                manager,
                bridge,
                strategyApplier,
                traceWriter,
                deadlinePolicy,
                reportingCollector
        );

        getLog().infoSimTime(
                this,
                "LIVE_MAGA_RUNTIME_COORDINATOR_START"
                        + "|serverId=" + getOs().getId()
                        + "|profile=" + runtimeConfig.profileName()
                        + "|gaParameterScalingMode=" + scalingMode
                        + "|bridgeDescription=" + bridge.getDescription()
                        + "|sourceMode=" + systemStateSource.getMode()
                        + "|traceDir=" + traceWriter.getOutputDir().getFileName()
        );
        stateFacade.configuredCellProfileLogFields().ifPresent(fields ->
                getLog().infoSimTime(this, "LIVE_STATE_CONFIGURED_CELL_PROFILE_LOADED" + fields)
        );
        scheduleNext(runtimeConfig.getCoordinatorTickIntervalNs());
    }

    @Override
    public void processEvent(Event event) {
        long tickTimeNs = getOs().getSimulationTime();
        int expiredTasks = stateFacade.removeExpiredTasks(tickTimeNs);
        int generatedTasks = stateFacade.generateWorkloadTasks(tickTimeNs);
        int activatedTasks = stateFacade.activateDueTasks(tickTimeNs);
        LiveStateLayerRuntimeFacade.RuntimeSnapshot runtimeSnapshot =
                stateFacade.buildSnapshotAt(tickTimeNs);
        try {
            traceWriter.writeBridgeSnapshot(tickTimeNs, runtimeSnapshot);
            runtimeSnapshot.getSnapshot().ifPresent(
                    snapshot -> bridge.publishSnapshot(snapshot, runtimeSnapshot.getAudit())
            );
            executionCoordinator.onTick(tickTimeNs, runtimeSnapshot.getSnapshot());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write live MA-GA runtime trace", e);
        }

        getLog().infoSimTime(
                this,
                "LIVE_MAGA_RUNTIME_COORDINATOR_TICK"
                        + "|simulationTime=" + tickTimeNs
                        + "|profile=" + runtimeConfig.profileName()
                        + "|generatedTasks=" + generatedTasks
                        + "|activatedTasks=" + activatedTasks
                        + "|expiredTasks=" + expiredTasks
                        + "|snapshotResolved=" + runtimeSnapshot.getSnapshot().isPresent()
                        + "|snapshotsRequested=" + bridge.getSnapshotsRequested()
                        + "|snapshotsResolved=" + bridge.getSnapshotsResolved()
                        + "|gaJobsSubmitted=" + executionCoordinator.getGaJobsSubmitted()
                        + "|gaJobsCompleted=" + executionCoordinator.getGaJobsCompleted()
                        + "|gaJobsApplied=" + executionCoordinator.getGaJobsApplied()
                        + "|gaJobsDiscardedAsStale=" + executionCoordinator.getGaJobsDiscardedAsStale()
        );
        scheduleNext(runtimeConfig.getCoordinatorTickIntervalNs());
    }

    @Override
    public void onShutdown() {
        long shutdownTimeNs = getOs().getSimulationTime();
        try {
            if (executionCoordinator != null) {
                executionCoordinator.finishOnShutdown(shutdownTimeNs);
                executionCoordinator.close();
            }
            if (reportingCollector != null) {
                LiveDetailedReportWriter.LiveDetailedReportArtifacts artifacts =
                        reportingCollector.writeFinalReports(maGaConfig);
                getLog().infoSimTime(
                        this,
                        "LIVE_NATIVE_DETAILED_REPORT_WRITTEN"
                                + "|directory=" + reportingCollector.getReportingDir()
                                + "|reportTxt=" + artifacts.getTxt()
                                + "|reportMarkdown=" + artifacts.getMarkdown()
                                + "|reportJson=" + artifacts.getJson()
                                + "|appliedSteps=" + reportingCollector.getAppliedStepCount()
                                + "|staleDiscardedSteps=" + reportingCollector.getStaleDiscardedStepCount()
                                + "|failedJobs=" + reportingCollector.getFailedJobCount()
                );
                if (runtimeConfig.isNativeLiveDetailedReportPrintToConsole()) {
                    System.out.println(Files.readString(artifacts.getTxt()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to finish live MA-GA runtime coordinator", e);
        } finally {
            if (reportingCollector != null) {
                try {
                    reportingCollector.close();
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to close native live reporting", e);
                }
            }
            if (traceWriter != null) {
                try {
                    traceWriter.close();
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to close live MA-GA runtime traces", e);
                }
            }
        }
        getLog().infoSimTime(
                this,
                "LIVE_MAGA_RUNTIME_COORDINATOR_STOP"
                        + "|serverId=" + getOs().getId()
                        + "|profile=" + runtimeConfig.profileName()
                        + "|gaParameterScalingMode=" + runtimeConfig.getGaParameterScalingMode()
                        + "|snapshotsRequested=" + bridge.getSnapshotsRequested()
                        + "|snapshotsResolved=" + bridge.getSnapshotsResolved()
                        + "|snapshotEmptyResponses=" + bridge.getEmptyResponses()
                        + "|gaJobsSubmitted=" + executionCoordinator.getGaJobsSubmitted()
                        + "|gaJobsCompleted=" + executionCoordinator.getGaJobsCompleted()
                        + "|gaJobsApplied=" + executionCoordinator.getGaJobsApplied()
                        + "|gaJobsDiscardedAsStale=" + executionCoordinator.getGaJobsDiscardedAsStale()
                        + "|parallelGaViolations=" + executionCoordinator.getParallelGaViolations()
                        + "|deltaTMaxMismatchViolations=" + executionCoordinator.getDeltaTMaxMismatchViolations()
                        + "|futurePoolViolations=" + bridge.getFuturePoolViolations()
                        + "|invalidPoolBandwidthViolations=" + bridge.getInvalidPoolBandwidthViolations()
        );
    }

    private void scheduleNext(long delayNs) {
        getOs().getEventManager().addEvent(new Event(getOs().getSimulationTime() + delayNs, this));
    }

    private Path resolveRunDirectory() {
        Path unitLogDirectory = getLog().getUnitLogDirectory();
        if (unitLogDirectory == null) {
            return resolveLatestRunDirectoryFromWorkingDirectory();
        }
        Path parent = unitLogDirectory.getParent();
        if (parent != null && "apps".equals(parent.getFileName().toString())) {
            return parent.getParent();
        }
        if (parent != null) {
            return parent;
        }
        return unitLogDirectory;
    }

    private Path resolveLatestRunDirectoryFromWorkingDirectory() {
        Path logsRoot = Paths.get(System.getProperty("user.dir")).resolve("logs");
        try (Stream<Path> paths = Files.list(logsRoot)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().contains("-" + runtimeConfig.getScenarioName()))
                    .max(Comparator.comparing(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new IllegalStateException(
                            "No " + runtimeConfig.getScenarioName() + " run directory found under " + logsRoot
                    ));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to resolve MOSAIC run directory under " + logsRoot, e);
        }
    }
}
