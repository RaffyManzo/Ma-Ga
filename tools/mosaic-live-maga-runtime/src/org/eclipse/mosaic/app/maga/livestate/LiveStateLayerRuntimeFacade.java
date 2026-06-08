package org.eclipse.mosaic.app.maga.livestate;

import model.bandwidth.BandwidthPoolType;
import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;
import validation.snapshot.LocalCandidateInvariantValidator;
import validation.snapshot.SnapshotValidator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LiveStateLayerRuntimeFacade {

    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000.0;

    private final MaGaLiveStateConfig config;
    private final LiveStateCache cache;
    private final LiveCellTrafficAccountingCache cellAccounting;
    private final LiveLocalAndV2vCandidatePreviewBuilder localAndV2vBuilder;
    private final LiveInfrastructurePreviewBuilder infrastructureBuilder;
    private final SnapshotValidator snapshotValidator;
    private final LocalCandidateInvariantValidator localCandidateValidator;
    private LiveSeededPoissonWorkloadGenerator workloadGenerator;

    private LiveStateLayerRuntimeFacade(MaGaLiveStateConfig config) {
        this.config = config;
        this.cache = LiveStateCache.getInstance();
        this.cellAccounting = LiveCellTrafficAccountingCache.getInstance();
        this.localAndV2vBuilder = new LiveLocalAndV2vCandidatePreviewBuilder();
        this.infrastructureBuilder = new LiveInfrastructurePreviewBuilder();
        this.snapshotValidator = new SnapshotValidator();
        this.localCandidateValidator = new LocalCandidateInvariantValidator();
    }

    public static LiveStateLayerRuntimeFacade load(File configurationPath) {
        return new LiveStateLayerRuntimeFacade(MaGaLiveStateConfig.load(configurationPath));
    }

    public void resetForRun() {
        cache.reset();
        if (config.hasCellDiagnosticAccounting()) {
            cellAccounting.reset();
        }
        workloadGenerator = config.hasWorkloadGenerationEnabled()
                ? new LiveSeededPoissonWorkloadGenerator(config.getWorkloadGeneration(), config.getTickIntervalNs())
                : null;
        List<LiveTaskState> taskDefinitions = new ArrayList<>();
        List<MaGaLiveStateConfig.TaskProfile> profiles = config.getTaskProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            taskDefinitions.add(profiles.get(i).toTaskState(i));
        }
        cache.installTaskDefinitions(taskDefinitions);
    }

    public int generateWorkloadTasks(long tickTimeNs) {
        if (workloadGenerator == null) {
            return 0;
        }
        LiveStateSnapshotView vehicleObservationView = cache.snapshotAtOrBefore(tickTimeNs);
        return cache.installGeneratedTaskDefinitions(
                workloadGenerator.generate(tickTimeNs, vehicleObservationView.getActiveVehicles())
        );
    }

    public int activateDueTasks(long tickTimeNs) {
        return cache.activateDueTasks(tickTimeNs);
    }

    public int removeExpiredTasks(long tickTimeNs) {
        return cache.removeExpiredTasks(tickTimeNs);
    }

    public Optional<String> configuredCellProfileLogFields() {
        if (!config.hasConfiguredCellProfile()) {
            return Optional.empty();
        }
        MaGaLiveStateConfig.ConfiguredCellProfile profile = config.getConfiguredCellProfile();
        String runtimeAccountingSource = config.hasCellDiagnosticAccounting()
                ? config.getCellDiagnosticAccounting().bandwidthSource
                : "NOT_CONFIGURED";
        return Optional.of(
                "|profileId=" + profile.profileId
                        + "|technology=" + profile.technology
                        + "|source=" + profile.source
                        + "|classification=" + profile.classification
                        + "|capacityBitsPerSecond=" + profile.capacityBitsPerSecond
                        + "|measuredRttSeconds=" + profile.measuredRttSeconds
                        + "|symmetricOneWayDelaySeconds=" + profile.symmetricOneWayDelaySeconds
                        + "|runtimeAccountingSource=" + runtimeAccountingSource
        );
    }

    public String configuredCellProfileSummary() {
        return configuredCellProfileLogFields().orElse("NOT_CONFIGURED");
    }

    public String runtimeAccountingSource() {
        return config.hasCellDiagnosticAccounting()
                ? config.getCellDiagnosticAccounting().bandwidthSource
                : "NOT_CONFIGURED";
    }

    public RuntimeSnapshot buildSnapshotAt(long tickTimeNs) {
        LiveStateSnapshotView view = cache.snapshotAtOrBefore(tickTimeNs);
        List<LiveCellBandwidthBucket> safeBuckets = config.hasCellDiagnosticAccounting()
                ? cellAccounting.latestSafeBuckets(tickTimeNs, config.getCellDiagnosticAccounting())
                : new ArrayList<LiveCellBandwidthBucket>();
        LiveLocalAndV2vCandidatePreviewBuilder.PreviewResult localAndV2v =
                localAndV2vBuilder.build(view, config);
        LiveInfrastructurePreviewBuilder.PreviewResult infrastructure =
                infrastructureBuilder.build(view, config, safeBuckets);

        List<VehicleSnapshot> vehicles = vehicles(view);
        Set<String> vehicleIds = vehicleIds(vehicles);
        List<TaskInstance> tasks = tasks(view, vehicleIds);
        List<NodeCandidate> localCandidates = localCandidates(localAndV2v.getLocalCandidates(), vehicleIds);
        List<NodeCandidate> v2vCandidates = v2vCandidates(localAndV2v.getV2vCandidates(), vehicleIds);
        Set<String> v2vPoolIds = poolIdsFromCandidates(v2vCandidates);
        List<BandwidthPoolSnapshot> directV2vPools =
                directV2vPools(localAndV2v.getV2vPools(), v2vPoolIds);

        Set<String> safeGatewayPoolIds = gatewayPoolIds(infrastructure.getGatewayPools());
        List<AccessGatewaySnapshot> gateways = accessGateways(safeGatewayPoolIds);
        Set<String> gatewayIds = gatewayIds(gateways);
        List<AccessLinkSnapshot> accessLinks =
                accessLinks(infrastructure.getAccessLinks(), vehicleIds, gatewayIds);
        List<NodeCandidate> remoteCandidates =
                remoteCandidates(infrastructure.getRemoteCandidates(), vehicleIds, gatewayIds, safeGatewayPoolIds);
        Set<String> remotePoolIds = poolIdsFromCandidates(remoteCandidates);
        remotePoolIds.addAll(poolIdsFromGateways(gateways));
        List<BandwidthPoolSnapshot> gatewayPools =
                gatewayPools(infrastructure.getGatewayPools(), remotePoolIds);

        List<BandwidthPoolSnapshot> bandwidthPools = new ArrayList<>();
        bandwidthPools.addAll(directV2vPools);
        bandwidthPools.addAll(gatewayPools);

        List<NodeCandidate> candidates = new ArrayList<>();
        candidates.addAll(localCandidates);
        candidates.addAll(v2vCandidates);
        candidates.addAll(remoteCandidates);

        LivePublishedSnapshotAudit audit = buildAudit(
                "live_runtime_snapshot_t_" + tickTimeNs,
                tickTimeNs,
                localAndV2v.getV2vPools(),
                v2vPoolIds,
                infrastructure.getGatewayPools(),
                remotePoolIds
        );

        if (vehicles.isEmpty() || bandwidthPools.isEmpty()) {
            return RuntimeSnapshot.empty(
                    tickTimeNs,
                    vehicles.size(),
                    tasks.size(),
                    candidates.size(),
                    gateways.size(),
                    accessLinks.size(),
                    bandwidthPools.size(),
                    safeBuckets.size(),
                    audit
            );
        }

        SystemSnapshot snapshot = new SystemSnapshot(
                "live_runtime_snapshot_t_" + tickTimeNs,
                tickTimeNs / NANOSECONDS_PER_SECOND,
                vehicles,
                tasks,
                candidates,
                gateways,
                accessLinks,
                bandwidthPools
        );
        snapshotValidator.validate(snapshot);
        localCandidateValidator.validate(snapshot);
        return RuntimeSnapshot.resolved(snapshot, tickTimeNs, safeBuckets.size(), audit);
    }

    private List<VehicleSnapshot> vehicles(LiveStateSnapshotView view) {
        List<VehicleSnapshot> rows = new ArrayList<>();
        for (LiveVehicleState vehicle : view.getActiveVehicles()) {
            if (!vehicle.hasFinitePosition() || !Double.isFinite(vehicle.getSpeedMetersPerSecond())) {
                continue;
            }
            rows.add(new VehicleSnapshot(
                    vehicle.getVehicleId(),
                    vehicle.getProjectedX(),
                    vehicle.getProjectedY(),
                    vehicle.getSpeedMetersPerSecond(),
                    (double) config.getLocalCpuCyclesPerSecond()
            ));
        }
        return rows;
    }

    private List<TaskInstance> tasks(LiveStateSnapshotView view, Set<String> vehicleIds) {
        List<TaskInstance> rows = new ArrayList<>();
        for (LiveTaskState task : view.getPendingTasks()) {
            if (!vehicleIds.contains(task.getSourceVehicleId())) {
                continue;
            }
            rows.add(new TaskInstance(
                    task.getTaskId(),
                    task.getSourceVehicleId(),
                    (double) task.getInputSizeBits(),
                    (double) task.getOutputSizeBits(),
                    (double) task.getCpuCycles(),
                    task.getDeadlineSeconds()
            ));
        }
        return rows;
    }

    private List<NodeCandidate> localCandidates(
            List<LiveLocalCandidatePreview> candidates,
            Set<String> vehicleIds
    ) {
        List<NodeCandidate> rows = new ArrayList<>();
        for (LiveLocalCandidatePreview candidate : candidates) {
            if (!vehicleIds.contains(candidate.sourceVehicleId)) {
                continue;
            }
            rows.add(new NodeCandidate(
                    candidate.candidateId,
                    candidate.sourceVehicleId,
                    candidate.executionNodeId,
                    NodeType.LOCAL,
                    (double) candidate.availableCpu,
                    0.0,
                    candidate.propagationDelaySeconds,
                    null,
                    null,
                    null,
                    null
            ));
        }
        return rows;
    }

    private List<NodeCandidate> v2vCandidates(
            List<LiveV2vCandidatePreview> candidates,
            Set<String> vehicleIds
    ) {
        List<NodeCandidate> rows = new ArrayList<>();
        for (LiveV2vCandidatePreview candidate : candidates) {
            if (!vehicleIds.contains(candidate.sourceVehicleId)
                    || !vehicleIds.contains(candidate.executionNodeId)) {
                continue;
            }
            rows.add(new NodeCandidate(
                    candidate.candidateId,
                    candidate.sourceVehicleId,
                    candidate.executionNodeId,
                    NodeType.VEHICLE,
                    (double) candidate.availableCpu,
                    (double) candidate.availableBandwidthBitsPerSecond,
                    candidate.propagationDelaySeconds,
                    null,
                    null,
                    null,
                    candidate.bandwidthPoolId
            ));
        }
        return rows;
    }

    private List<BandwidthPoolSnapshot> directV2vPools(
            List<LiveV2vBandwidthPoolPreview> pools,
            Set<String> referencedPoolIds
    ) {
        List<BandwidthPoolSnapshot> rows = new ArrayList<>();
        for (LiveV2vBandwidthPoolPreview pool : pools) {
            if (referencedPoolIds.contains(pool.poolId)) {
                rows.add(new BandwidthPoolSnapshot(
                        pool.poolId,
                        BandwidthPoolType.DIRECT_V2V,
                        (double) pool.availableBandwidthBitsPerSecond
                ));
            }
        }
        return rows;
    }

    private List<AccessGatewaySnapshot> accessGateways(Set<String> safeGatewayPoolIds) {
        List<AccessGatewaySnapshot> rows = new ArrayList<>();
        for (MaGaLiveStateConfig.Gateway gateway : config.getStaticInfrastructure().gateways) {
            if (!safeGatewayPoolIds.contains(gateway.bandwidthPoolId)) {
                continue;
            }
            rows.add(new AccessGatewaySnapshot(
                    gateway.gatewayId,
                    gateway.gatewayType,
                    gateway.projectedX,
                    gateway.projectedY,
                    gateway.coverageRadiusMeters,
                    gateway.bandwidthPoolId
            ));
        }
        return rows;
    }

    private List<AccessLinkSnapshot> accessLinks(
            List<LiveAccessLinkPreview> links,
            Set<String> vehicleIds,
            Set<String> gatewayIds
    ) {
        List<AccessLinkSnapshot> rows = new ArrayList<>();
        for (LiveAccessLinkPreview link : links) {
            if (!vehicleIds.contains(link.vehicleId) || !gatewayIds.contains(link.gatewayId)) {
                continue;
            }
            rows.add(new AccessLinkSnapshot(
                    link.accessLinkId,
                    link.vehicleId,
                    link.gatewayId,
                    link.active,
                    link.available
            ));
        }
        return rows;
    }

    private List<NodeCandidate> remoteCandidates(
            List<LiveRemoteCandidatePreview> candidates,
            Set<String> vehicleIds,
            Set<String> gatewayIds,
            Set<String> gatewayPoolIds
    ) {
        List<NodeCandidate> rows = new ArrayList<>();
        for (LiveRemoteCandidatePreview candidate : candidates) {
            if (!vehicleIds.contains(candidate.sourceVehicleId)
                    || !gatewayIds.contains(candidate.gatewayId)
                    || !gatewayPoolIds.contains(candidate.bandwidthPoolId)) {
                continue;
            }
            rows.add(new NodeCandidate(
                    candidate.candidateId,
                    candidate.sourceVehicleId,
                    candidate.executionNodeId,
                    NodeType.valueOf(candidate.type),
                    candidate.availableCpu,
                    candidate.availableBandwidthBitsPerSecond,
                    candidate.propagationDelaySeconds,
                    candidate.nodeX,
                    candidate.nodeY,
                    candidate.coverageRadiusMeters,
                    candidate.bandwidthPoolId
            ));
        }
        return rows;
    }

    private List<BandwidthPoolSnapshot> gatewayPools(
            List<LiveGatewayBandwidthPoolPreview> pools,
            Set<String> referencedPoolIds
    ) {
        List<BandwidthPoolSnapshot> rows = new ArrayList<>();
        for (LiveGatewayBandwidthPoolPreview pool : pools) {
            if (referencedPoolIds.contains(pool.poolId)) {
                rows.add(new BandwidthPoolSnapshot(
                        pool.poolId,
                        BandwidthPoolType.GATEWAY,
                        pool.availableBandwidthBitsPerSecond
                ));
            }
        }
        return rows;
    }

    private LivePublishedSnapshotAudit buildAudit(
            String snapshotId,
            long tickTimeNs,
            List<LiveV2vBandwidthPoolPreview> directPools,
            Set<String> referencedDirectPoolIds,
            List<LiveGatewayBandwidthPoolPreview> gatewayPools,
            Set<String> referencedGatewayPoolIds
    ) {
        double snapshotTimeSeconds = tickTimeNs / NANOSECONDS_PER_SECOND;
        Map<String, Double> gatewayPoolAvailableFromTimes = new LinkedHashMap<>();
        Map<String, Double> directV2vPoolSourceTimes = new LinkedHashMap<>();
        int futurePoolViolations = 0;
        int invalidPoolBandwidthViolations = 0;

        for (LiveV2vBandwidthPoolPreview pool : directPools) {
            if (!referencedDirectPoolIds.contains(pool.poolId)) {
                continue;
            }
            double sourceTimeSeconds = pool.timeNs / NANOSECONDS_PER_SECOND;
            directV2vPoolSourceTimes.put(pool.poolId, sourceTimeSeconds);
            if (sourceTimeSeconds > snapshotTimeSeconds + 1.0E-9) {
                futurePoolViolations++;
            }
            if (pool.availableBandwidthBitsPerSecond <= 0) {
                invalidPoolBandwidthViolations++;
            }
        }

        for (LiveGatewayBandwidthPoolPreview pool : gatewayPools) {
            if (!referencedGatewayPoolIds.contains(pool.poolId)) {
                continue;
            }
            double availableFromSeconds = pool.availableFromNs / NANOSECONDS_PER_SECOND;
            gatewayPoolAvailableFromTimes.put(pool.poolId, availableFromSeconds);
            if (availableFromSeconds > snapshotTimeSeconds + 1.0E-9) {
                futurePoolViolations++;
            }
            if (pool.availableBandwidthBitsPerSecond <= 0.0) {
                invalidPoolBandwidthViolations++;
            }
        }

        return new LivePublishedSnapshotAudit(
                snapshotId,
                snapshotTimeSeconds,
                gatewayPoolAvailableFromTimes,
                directV2vPoolSourceTimes,
                futurePoolViolations,
                invalidPoolBandwidthViolations
        );
    }

    private static Set<String> vehicleIds(List<VehicleSnapshot> vehicles) {
        Set<String> ids = new HashSet<>();
        for (VehicleSnapshot vehicle : vehicles) {
            ids.add(vehicle.getVehicleId());
        }
        return ids;
    }

    private static Set<String> gatewayIds(List<AccessGatewaySnapshot> gateways) {
        Set<String> ids = new HashSet<>();
        for (AccessGatewaySnapshot gateway : gateways) {
            ids.add(gateway.getGatewayId());
        }
        return ids;
    }

    private static Set<String> gatewayPoolIds(List<LiveGatewayBandwidthPoolPreview> pools) {
        Set<String> ids = new HashSet<>();
        for (LiveGatewayBandwidthPoolPreview pool : pools) {
            ids.add(pool.poolId);
        }
        return ids;
    }

    private static Set<String> poolIdsFromCandidates(List<NodeCandidate> candidates) {
        Set<String> ids = new HashSet<>();
        for (NodeCandidate candidate : candidates) {
            String poolId = candidate.getBandwidthPoolId();
            if (poolId != null && !poolId.isBlank()) {
                ids.add(poolId);
            }
        }
        return ids;
    }

    private static Set<String> poolIdsFromGateways(List<AccessGatewaySnapshot> gateways) {
        Set<String> ids = new HashSet<>();
        for (AccessGatewaySnapshot gateway : gateways) {
            String poolId = gateway.getBandwidthPoolId();
            if (poolId != null && !poolId.isBlank()) {
                ids.add(poolId);
            }
        }
        return ids;
    }

    public static final class RuntimeSnapshot {
        private final long tickTimeNs;
        private final SystemSnapshot snapshot;
        private final int vehicles;
        private final int tasks;
        private final int candidates;
        private final int accessGateways;
        private final int accessLinks;
        private final int bandwidthPools;
        private final int safeCellBuckets;
        private final LivePublishedSnapshotAudit audit;

        private RuntimeSnapshot(
                long tickTimeNs,
                SystemSnapshot snapshot,
                int vehicles,
                int tasks,
                int candidates,
                int accessGateways,
                int accessLinks,
                int bandwidthPools,
                int safeCellBuckets,
                LivePublishedSnapshotAudit audit
        ) {
            this.tickTimeNs = tickTimeNs;
            this.snapshot = snapshot;
            this.vehicles = vehicles;
            this.tasks = tasks;
            this.candidates = candidates;
            this.accessGateways = accessGateways;
            this.accessLinks = accessLinks;
            this.bandwidthPools = bandwidthPools;
            this.safeCellBuckets = safeCellBuckets;
            this.audit = audit;
        }

        static RuntimeSnapshot resolved(
                SystemSnapshot snapshot,
                long tickTimeNs,
                int safeCellBuckets,
                LivePublishedSnapshotAudit audit
        ) {
            return new RuntimeSnapshot(
                    tickTimeNs,
                    snapshot,
                    snapshot.getVehicles().size(),
                    snapshot.getTasks().size(),
                    snapshot.getCandidateNodes().size(),
                    snapshot.getAccessGateways().size(),
                    snapshot.getAccessLinks().size(),
                    snapshot.getBandwidthPools().size(),
                    safeCellBuckets,
                    audit
            );
        }

        static RuntimeSnapshot empty(
                long tickTimeNs,
                int vehicles,
                int tasks,
                int candidates,
                int accessGateways,
                int accessLinks,
                int bandwidthPools,
                int safeCellBuckets,
                LivePublishedSnapshotAudit audit
        ) {
            return new RuntimeSnapshot(
                    tickTimeNs,
                    null,
                    vehicles,
                    tasks,
                    candidates,
                    accessGateways,
                    accessLinks,
                    bandwidthPools,
                    safeCellBuckets,
                    audit
            );
        }

        public long getTickTimeNs() {
            return tickTimeNs;
        }

        public Optional<SystemSnapshot> getSnapshot() {
            return Optional.ofNullable(snapshot);
        }

        public int getVehicles() {
            return vehicles;
        }

        public int getTasks() {
            return tasks;
        }

        public int getCandidates() {
            return candidates;
        }

        public int getAccessGateways() {
            return accessGateways;
        }

        public int getAccessLinks() {
            return accessLinks;
        }

        public int getBandwidthPools() {
            return bandwidthPools;
        }

        public int getSafeCellBuckets() {
            return safeCellBuckets;
        }

        public LivePublishedSnapshotAudit getAudit() {
            return audit;
        }
    }

    public static final class LivePublishedSnapshotAudit {
        private final String snapshotId;
        private final double snapshotTimeSeconds;
        private final Map<String, Double> gatewayPoolAvailableFromTimes;
        private final Map<String, Double> directV2vPoolSourceTimes;
        private final int futurePoolViolations;
        private final int invalidPoolBandwidthViolations;

        LivePublishedSnapshotAudit(
                String snapshotId,
                double snapshotTimeSeconds,
                Map<String, Double> gatewayPoolAvailableFromTimes,
                Map<String, Double> directV2vPoolSourceTimes,
                int futurePoolViolations,
                int invalidPoolBandwidthViolations
        ) {
            this.snapshotId = snapshotId;
            this.snapshotTimeSeconds = snapshotTimeSeconds;
            this.gatewayPoolAvailableFromTimes = Collections.unmodifiableMap(
                    new LinkedHashMap<>(gatewayPoolAvailableFromTimes)
            );
            this.directV2vPoolSourceTimes = Collections.unmodifiableMap(
                    new LinkedHashMap<>(directV2vPoolSourceTimes)
            );
            this.futurePoolViolations = futurePoolViolations;
            this.invalidPoolBandwidthViolations = invalidPoolBandwidthViolations;
        }

        public String getSnapshotId() {
            return snapshotId;
        }

        public double getSnapshotTimeSeconds() {
            return snapshotTimeSeconds;
        }

        public Map<String, Double> getGatewayPoolAvailableFromTimes() {
            return gatewayPoolAvailableFromTimes;
        }

        public Map<String, Double> getDirectV2vPoolSourceTimes() {
            return directV2vPoolSourceTimes;
        }

        public int getFuturePoolViolations() {
            return futurePoolViolations;
        }

        public int getInvalidPoolBandwidthViolations() {
            return invalidPoolBandwidthViolations;
        }
    }
}
