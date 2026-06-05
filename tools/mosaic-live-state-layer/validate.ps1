param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiveStateLayerStudy"
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$SafeRepoRoot = $RepoRoot.Replace("\", "/")
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$LogsRoot = Join-Path $ResolvedMosaicRoot "logs"
$DiagnosticsDir = Join-Path $RepoRoot "data\mosaic-study\diagnostics"
$DiagnosticsFile = Join-Path $DiagnosticsDir "phase_13b_live_state_layer_validation.json"
$SourceRoot = Join-Path $ToolRoot "src"
$ScenarioConfig = Join-Path $RepoRoot "data\mosaic-scenarios\MaGaLiveStateLayerStudy\application\ma_ga_live_state_config.json"

if (-not (Test-Path -LiteralPath $LogsRoot -PathType Container)) {
    throw "MOSAIC logs root not found: $LogsRoot"
}
if (-not (Test-Path -LiteralPath $DiagnosticsDir -PathType Container)) {
    New-Item -ItemType Directory -Path $DiagnosticsDir | Out-Null
}

if ($ScenarioName -eq "MaGaLiveInfrastructureSnapshotStudy") {
    $DiagnosticsFile13C = Join-Path $DiagnosticsDir "phase_13c_live_infrastructure_snapshot_validation.json"
    $ScenarioConfig13C = Join-Path $RepoRoot "data\mosaic-scenarios\$ScenarioName\application\ma_ga_live_state_config.json"
    if (-not (Test-Path -LiteralPath $ScenarioConfig13C -PathType Leaf)) {
        throw "Live infrastructure config not found: $ScenarioConfig13C"
    }
    $Config13C = Get-Content -LiteralPath $ScenarioConfig13C -Raw | ConvertFrom-Json
    $LatestRun13C = Get-ChildItem -LiteralPath $LogsRoot -Directory |
        Where-Object { $_.Name -like "*-$ScenarioName" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $LatestRun13C) {
        throw "No $ScenarioName run found under $LogsRoot"
    }

    $SourceRunRelativeDir13C = "tmp/mosaic-25.2/logs/$($LatestRun13C.Name)"
    $PreviewDir13C = Join-Path $LatestRun13C.FullName "live-state-layer"
    $InfrastructureDir = Join-Path $LatestRun13C.FullName "live-infrastructure-snapshot"
    $SnapshotDir = Join-Path $InfrastructureDir "snapshots"
    foreach ($RequiredDir in @($PreviewDir13C, $InfrastructureDir, $SnapshotDir)) {
        if (-not (Test-Path -LiteralPath $RequiredDir -PathType Container)) {
            throw "Required 13C output directory missing: $RequiredDir"
        }
    }

    function Import-Phase13CCsv {
        param(
            [string]$Directory,
            [string]$FileName
        )
        $Path = Join-Path $Directory $FileName
        if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
            throw "Required preview CSV missing: $Path"
        }
        return ,@(Import-Csv -LiteralPath $Path)
    }

    function Count-Phase13CDuplicates {
        param(
            [array]$Rows,
            [scriptblock]$KeySelector
        )
        $Groups = @{}
        foreach ($Row in $Rows) {
            $Key = & $KeySelector $Row
            if (-not $Groups.ContainsKey($Key)) {
                $Groups[$Key] = 0
            }
            $Groups[$Key]++
        }
        $Duplicates = 0
        foreach ($Count in $Groups.Values) {
            if ($Count -gt 1) {
                $Duplicates += ($Count - 1)
            }
        }
        return $Duplicates
    }

    function Get-Phase13CAbsoluteDiagnostics {
        $Files = @()
        $Count = 0
        foreach ($JsonFile in Get-ChildItem -LiteralPath $DiagnosticsDir -File -Filter "*.json") {
            $Text = Get-Content -LiteralPath $JsonFile.FullName -Raw
            $Matches = [regex]::Matches($Text, "[A-Za-z]:\\\\|[A-Za-z]:/|latestRunDir")
            if ($Matches.Count -gt 0) {
                $Count += $Matches.Count
                $Files += $JsonFile.Name
            }
        }
        return [pscustomobject]@{
            Count = $Count
            Files = @($Files | Select-Object -Unique)
        }
    }

    function Get-Phase13CFieldValues {
        param(
            [string[]]$Lines,
            [string]$Field
        )
        $Values = @()
        foreach ($Line in $Lines) {
            if ($Line -match ("[|]" + [regex]::Escape($Field) + "=([^| )]+)")) {
                $Values += $Matches[1]
            }
        }
        return $Values
    }

    function Run-Phase13CJavaSnapshotValidation {
        param(
            [string]$SnapshotsPath
        )
        $HarnessSource = Join-Path $ToolRoot "harness\LiveSnapshotValidationHarness.java"
        $HarnessClasses = Join-Path $ToolRoot "out\snapshot-harness-classes"
        if (-not (Test-Path -LiteralPath $HarnessSource -PathType Leaf)) {
            throw "Snapshot validation harness missing: $HarnessSource"
        }
        if (Test-Path -LiteralPath $HarnessClasses) {
            Remove-Item -LiteralPath $HarnessClasses -Recurse -Force
        }
        New-Item -ItemType Directory -Path $HarnessClasses | Out-Null

        $JacksonJars = @(
            (Join-Path $RepoRoot "out\codex-lib\jackson-annotations-2.17.2.jar"),
            (Join-Path $RepoRoot "out\codex-lib\jackson-core-2.17.2.jar"),
            (Join-Path $RepoRoot "out\codex-lib\jackson-databind-2.17.2.jar")
        )
        if (-not (Test-Path -LiteralPath $JacksonJars[0] -PathType Leaf)) {
            $JacksonJars = @(
                (Join-Path $ResolvedMosaicRoot "lib\third-party\jackson-annotations-2.16.1.jar"),
                (Join-Path $ResolvedMosaicRoot "lib\third-party\jackson-core-2.16.1.jar"),
                (Join-Path $ResolvedMosaicRoot "lib\third-party\jackson-databind-2.16.1.jar")
            )
        }
        foreach ($JarPath in $JacksonJars) {
            if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
                throw "Jackson JAR not found for snapshot validation: $JarPath"
            }
        }

        $SourcePath = Join-Path $RepoRoot "src"
        $CoreSourceDirs = @(
            (Join-Path $SourcePath "io\snapshot"),
            (Join-Path $SourcePath "io\snapshot\dto"),
            (Join-Path $SourcePath "model\snapshot"),
            (Join-Path $SourcePath "model\node"),
            (Join-Path $SourcePath "model\bandwidth"),
            (Join-Path $SourcePath "validation\snapshot")
        )
        $CoreSources = foreach ($Dir in $CoreSourceDirs) {
            Get-ChildItem -LiteralPath $Dir -File -Filter "*.java"
        }
        $CompileClasspath = ($JacksonJars) -join [IO.Path]::PathSeparator
        $CompileSources = @($HarnessSource) + @($CoreSources | ForEach-Object { $_.FullName })
        & javac -cp $CompileClasspath -d $HarnessClasses $CompileSources
        if ($LASTEXITCODE -ne 0) {
            return [pscustomobject]@{
                LoaderFailures = 1
                ValidatorFailures = 1
                SnapshotsLoaded = 0
                Output = "javac failed"
            }
        }

        $RunClasspath = @($HarnessClasses) + $JacksonJars
        $RunClasspathText = ($RunClasspath) -join [IO.Path]::PathSeparator
        $HarnessOutput = & java -cp $RunClasspathText LiveSnapshotValidationHarness $SnapshotsPath 2>&1
        if ($LASTEXITCODE -ne 0) {
            return [pscustomobject]@{
                LoaderFailures = 1
                ValidatorFailures = 1
                SnapshotsLoaded = 0
                Output = ($HarnessOutput | Out-String).Trim()
            }
        }
        $SnapshotsLoaded = 0
        foreach ($Line in $HarnessOutput) {
            if ($Line -match "snapshotsLoaded=(\d+)") {
                $SnapshotsLoaded = [int]$Matches[1]
            }
        }
        return [pscustomobject]@{
            LoaderFailures = 0
            ValidatorFailures = 0
            SnapshotsLoaded = $SnapshotsLoaded
            Output = ($HarnessOutput | Out-String).Trim()
        }
    }

    $VehicleRows13C = Import-Phase13CCsv -Directory $PreviewDir13C -FileName "live_vehicle_state_preview.csv"
    $TaskRows13C = Import-Phase13CCsv -Directory $PreviewDir13C -FileName "live_task_preview.csv"
    $LocalRows13C = Import-Phase13CCsv -Directory $PreviewDir13C -FileName "live_local_candidate_preview.csv"
    $V2vRows13C = Import-Phase13CCsv -Directory $PreviewDir13C -FileName "live_v2v_candidate_preview.csv"
    $V2vPoolRows13C = Import-Phase13CCsv -Directory $PreviewDir13C -FileName "live_v2v_bandwidth_pool_preview.csv"
    $CellEventRows = Import-Phase13CCsv -Directory $InfrastructureDir -FileName "live_cell_traffic_event_preview.csv"
    $CellBucketRows = Import-Phase13CCsv -Directory $InfrastructureDir -FileName "live_cell_bandwidth_bucket_preview.csv"
    $AccessLinkRows = Import-Phase13CCsv -Directory $InfrastructureDir -FileName "live_access_link_preview.csv"
    $GatewayPoolRows = Import-Phase13CCsv -Directory $InfrastructureDir -FileName "live_gateway_bandwidth_pool_preview.csv"
    $RemoteCandidateRows = Import-Phase13CCsv -Directory $InfrastructureDir -FileName "live_remote_candidate_preview.csv"
    $ManifestRows = Import-Phase13CCsv -Directory $InfrastructureDir -FileName "live_snapshot_manifest.csv"
    $SnapshotFiles = @(Get-ChildItem -LiteralPath $SnapshotDir -File -Filter "*.json")

    $LogFiles13C = Get-ChildItem -LiteralPath $LatestRun13C.FullName -Recurse -File -Filter "*.log"
    $LogLines13C = foreach ($LogFile in $LogFiles13C) {
        Get-Content -LiteralPath $LogFile.FullName
    }
    $MarkerLines13C = @($LogLines13C | Where-Object { $_ -match "LIVE_STATE_|LIVE_CELL_DIAGNOSTIC_" })
    $CoordinatorStartLines13C = @($MarkerLines13C | Where-Object { $_ -match "LIVE_STATE_COORDINATOR_START" })
    $CoordinatorTickLines13C = @($MarkerLines13C | Where-Object { $_ -match "LIVE_STATE_COORDINATOR_TICK" })
    $CoordinatorStopLines13C = @($MarkerLines13C | Where-Object { $_ -match "LIVE_STATE_COORDINATOR_STOP" })
    $CoordinatorTickTimes13C = @(Get-Phase13CFieldValues -Lines $CoordinatorTickLines13C -Field "simulationTime" | ForEach-Object { [Int64]$_ })
    $TickSet13C = @{}
    foreach ($Time in $CoordinatorTickTimes13C) {
        $TickSet13C[[string]$Time] = $true
    }

    $MosaicLog13C = Join-Path $LatestRun13C.FullName "MOSAIC.log"
    $MosaicLogText13C = if (Test-Path -LiteralPath $MosaicLog13C -PathType Leaf) {
        Get-Content -LiteralPath $MosaicLog13C -Raw
    } else {
        ""
    }
    $SimulationCompleted13C = $MosaicLogText13C -match "Simulation ended after" -and $MosaicLogText13C -match "Simulation finished"

    $FutureVehicleStateViolations = 0
    foreach ($Row in $VehicleRows13C) {
        if ([Int64]$Row.lastUpdateTimeNs -gt [Int64]$Row.timeNs) {
            $FutureVehicleStateViolations++
        }
    }
    $FutureTaskActivationViolations = 0
    foreach ($Row in $TaskRows13C) {
        if ([Int64]$Row.activationTimeNs -gt [Int64]$Row.timeNs) {
            $FutureTaskActivationViolations++
        }
    }
    $FutureCellEventViolations = 0
    foreach ($Row in $CellEventRows) {
        if ([Int64]$Row.bucketStartNs -gt [Int64]$Row.timeNs) {
            $FutureCellEventViolations++
        }
    }
    $FutureSafeBucketViolations = 0
    foreach ($Row in $CellBucketRows) {
        if ([Int64]$Row.availableFromNs -gt [Int64]$Row.timeNs) {
            $FutureSafeBucketViolations++
        }
    }
    $FutureAccessLinkViolations = 0
    foreach ($Row in $AccessLinkRows) {
        if (-not $TickSet13C.ContainsKey([string]$Row.timeNs)) {
            $FutureAccessLinkViolations++
        }
    }
    $FutureCandidateViolations = 0
    foreach ($Row in @($LocalRows13C + $V2vRows13C + $RemoteCandidateRows)) {
        if (-not $TickSet13C.ContainsKey([string]$Row.timeNs)) {
            $FutureCandidateViolations++
        }
    }
    $FuturePoolViolations = 0
    foreach ($Row in $V2vPoolRows13C) {
        if (-not $TickSet13C.ContainsKey([string]$Row.timeNs)) {
            $FuturePoolViolations++
        }
    }
    foreach ($Row in $GatewayPoolRows) {
        if ((-not $TickSet13C.ContainsKey([string]$Row.timeNs)) -or ([Int64]$Row.availableFromNs -gt [Int64]$Row.timeNs)) {
            $FuturePoolViolations++
        }
    }

    $MultipleActiveGatewayViolations = 0
    $ActiveLinkGroups = @{}
    foreach ($Row in $AccessLinkRows) {
        if ($Row.active -eq "true") {
            $Key = "$($Row.timeNs)|$($Row.vehicleId)"
            if (-not $ActiveLinkGroups.ContainsKey($Key)) {
                $ActiveLinkGroups[$Key] = 0
            }
            $ActiveLinkGroups[$Key]++
        }
    }
    foreach ($Count in $ActiveLinkGroups.Values) {
        if ($Count -gt 1) {
            $MultipleActiveGatewayViolations += ($Count - 1)
        }
    }
    $ActiveUnavailableLinkViolations = @($AccessLinkRows | Where-Object { $_.active -eq "true" -and $_.available -ne "true" }).Count

    $GatewayPoolsByTime = @{}
    foreach ($Row in $GatewayPoolRows) {
        $GatewayPoolsByTime["$($Row.timeNs)|$($Row.poolId)"] = $true
    }
    $UnresolvedGatewayPoolViolations = 0
    foreach ($Row in $RemoteCandidateRows) {
        if (-not $GatewayPoolsByTime.ContainsKey("$($Row.timeNs)|$($Row.bandwidthPoolId)")) {
            $UnresolvedGatewayPoolViolations++
        }
    }

    $DuplicateCandidateIds = 0
    foreach ($Group in @($LocalRows13C + $V2vRows13C + $RemoteCandidateRows) | Group-Object { "$($_.timeNs)|$($_.candidateId)" }) {
        if ($Group.Count -gt 1) {
            $DuplicateCandidateIds += ($Group.Count - 1)
        }
    }
    $DuplicatePoolIds = 0
    foreach ($Group in @($V2vPoolRows13C + $GatewayPoolRows) | Group-Object { "$($_.timeNs)|$($_.poolId)" }) {
        if ($Group.Count -gt 1) {
            $DuplicatePoolIds += ($Group.Count - 1)
        }
    }
    $CloudPlaceholderViolations = @($RemoteCandidateRows | Where-Object {
        $_.type -eq "CLOUD" -and (
            -not [string]::IsNullOrWhiteSpace($_.nodeX) -or
            -not [string]::IsNullOrWhiteSpace($_.nodeY) -or
            -not [string]::IsNullOrWhiteSpace($_.coverageRadiusMeters)
        )
    }).Count

    $OrphanReferenceViolations = 0
    foreach ($SnapshotFile in $SnapshotFiles) {
        $Snapshot = Get-Content -LiteralPath $SnapshotFile.FullName -Raw | ConvertFrom-Json
        $VehicleIds = @{}
        foreach ($Vehicle in @($Snapshot.vehicles)) { $VehicleIds[$Vehicle.vehicleId] = $true }
        $GatewayIds = @{}
        foreach ($Gateway in @($Snapshot.accessGateways)) { $GatewayIds[$Gateway.gatewayId] = $true }
        $PoolIds = @{}
        foreach ($Pool in @($Snapshot.bandwidthPools)) { $PoolIds[$Pool.poolId] = $true }
        foreach ($Task in @($Snapshot.tasks)) {
            if (-not $VehicleIds.ContainsKey($Task.sourceVehicleId)) { $OrphanReferenceViolations++ }
        }
        foreach ($Candidate in @($Snapshot.candidateNodes)) {
            if (-not $VehicleIds.ContainsKey($Candidate.sourceVehicleId)) { $OrphanReferenceViolations++ }
            if (($Candidate.type -eq "VEHICLE") -and (-not $VehicleIds.ContainsKey($Candidate.executionNodeId))) { $OrphanReferenceViolations++ }
            if ($Candidate.PSObject.Properties.Name -contains "bandwidthPoolId") {
                $PoolRef = [string]$Candidate.bandwidthPoolId
                if (-not [string]::IsNullOrWhiteSpace($PoolRef) -and -not $PoolIds.ContainsKey($PoolRef)) { $OrphanReferenceViolations++ }
            }
        }
        foreach ($Gateway in @($Snapshot.accessGateways)) {
            if (-not $PoolIds.ContainsKey($Gateway.bandwidthPoolId)) { $OrphanReferenceViolations++ }
        }
        foreach ($Link in @($Snapshot.accessLinks)) {
            if (-not $VehicleIds.ContainsKey($Link.vehicleId)) { $OrphanReferenceViolations++ }
            if (-not $GatewayIds.ContainsKey($Link.gatewayId)) { $OrphanReferenceViolations++ }
        }
    }

    $DistinctGatewayPoolTickCount = @($GatewayPoolRows | Select-Object -ExpandProperty timeNs -Unique).Count
    $TicksWithoutSafeCellBucket = [Math]::Max(0, $CoordinatorTickLines13C.Count - $DistinctGatewayPoolTickCount)
    $ActiveAccessLinkRows = @($AccessLinkRows | Where-Object { $_.active -eq "true" }).Count
    $StatesWithoutActiveGateway = [Math]::Max(0, $VehicleRows13C.Count - $ActiveLinkGroups.Count)
    $EdgeCandidateRows = @($RemoteCandidateRows | Where-Object { $_.type -eq "EDGE" }).Count
    $CloudCandidateRows = @($RemoteCandidateRows | Where-Object { $_.type -eq "CLOUD" }).Count

    $JavaValidation = Run-Phase13CJavaSnapshotValidation -SnapshotsPath $SnapshotDir

    $CoreStatus13C = git -c safe.directory="$SafeRepoRoot" status --short src
    if ($LASTEXITCODE -ne 0) {
        throw "git status for src failed with exit code $LASTEXITCODE"
    }
    $CanonicalStatus13C = git -c safe.directory="$SafeRepoRoot" status --short data/mosaic-scenarios/MaGaIntegratedStudy
    if ($LASTEXITCODE -ne 0) {
        throw "git status for canonical scenario failed with exit code $LASTEXITCODE"
    }
    $CoreModified13C = -not [string]::IsNullOrWhiteSpace(($CoreStatus13C | Out-String).Trim())
    $CanonicalScenarioModified13C = -not [string]::IsNullOrWhiteSpace(($CanonicalStatus13C | Out-String).Trim())

    $Warnings13C = @(
        "WARNING_CELL_BANDWIDTH_IS_DIAGNOSTIC_RUNTIME_ACCOUNTING_NOT_FEDERATE_MEASUREMENT",
        "WARNING_CELL_REGIONAL_HANDOVER_NOT_AVAILABLE_DIRECTLY_LIVE",
        "WARNING_DIAGNOSTIC_CPU_AND_V2V_BANDWIDTH_REQUIRE_FUTURE_CALIBRATION"
    )
    $Errors13C = @()
    function Require-Phase13CCondition {
        param(
            [bool]$Condition,
            [string]$Message
        )
        if (-not $Condition) {
            $script:Errors13C += $Message
        }
    }

    Require-Phase13CCondition $SimulationCompleted13C "simulationCompleted must be true"
    Require-Phase13CCondition ($CellBucketRows.Count -gt 0) "at least one safe Cell bucket must be used"
    Require-Phase13CCondition ($ActiveAccessLinkRows -gt 0) "at least one active access link must be observed"
    Require-Phase13CCondition ($EdgeCandidateRows -gt 0) "at least one EDGE candidate must be observed"
    Require-Phase13CCondition ($CloudCandidateRows -gt 0) "at least one CLOUD candidate must be observed"
    Require-Phase13CCondition ($SnapshotFiles.Count -gt 0) "at least one live SystemSnapshot JSON must be generated"
    Require-Phase13CCondition ($JavaValidation.LoaderFailures -eq 0) "javaLoaderValidationFailures must be 0"
    Require-Phase13CCondition ($JavaValidation.ValidatorFailures -eq 0) "javaValidatorFailures must be 0"
    Require-Phase13CCondition ($FutureVehicleStateViolations -eq 0) "futureVehicleStateViolations must be 0"
    Require-Phase13CCondition ($FutureTaskActivationViolations -eq 0) "futureTaskActivationViolations must be 0"
    Require-Phase13CCondition ($FutureCellEventViolations -eq 0) "futureCellEventViolations must be 0"
    Require-Phase13CCondition ($FutureSafeBucketViolations -eq 0) "futureSafeBucketViolations must be 0"
    Require-Phase13CCondition ($FutureAccessLinkViolations -eq 0) "futureAccessLinkViolations must be 0"
    Require-Phase13CCondition ($FutureCandidateViolations -eq 0) "futureCandidateViolations must be 0"
    Require-Phase13CCondition ($FuturePoolViolations -eq 0) "futurePoolViolations must be 0"
    Require-Phase13CCondition ($MultipleActiveGatewayViolations -eq 0) "multipleActiveGatewayViolations must be 0"
    Require-Phase13CCondition ($ActiveUnavailableLinkViolations -eq 0) "activeUnavailableLinkViolations must be 0"
    Require-Phase13CCondition ($UnresolvedGatewayPoolViolations -eq 0) "unresolvedGatewayPoolViolations must be 0"
    Require-Phase13CCondition ($OrphanReferenceViolations -eq 0) "orphanReferenceViolations must be 0"
    Require-Phase13CCondition ($DuplicateCandidateIds -eq 0) "duplicateCandidateIds must be 0"
    Require-Phase13CCondition ($DuplicatePoolIds -eq 0) "duplicatePoolIds must be 0"
    Require-Phase13CCondition ($CloudPlaceholderViolations -eq 0) "cloudPlaceholderViolations must be 0"
    Require-Phase13CCondition (-not $CoreModified13C) "coreModified must be false"
    Require-Phase13CCondition (-not $CanonicalScenarioModified13C) "canonicalScenarioModified must be false"

    $Completed13C = $Errors13C.Count -eq 0
    $Result13C = [ordered]@{
        phase = "13C_LIVE_INFRASTRUCTURE_AND_SNAPSHOT_ASSEMBLY"
        sourceRun = $LatestRun13C.Name
        sourceRunName = $LatestRun13C.Name
        sourceRunRelativeDir = $SourceRunRelativeDir13C
        scenarioName = $ScenarioName
        simulationCompleted = $SimulationCompleted13C
        coordinatorTicks = $CoordinatorTickLines13C.Count
        vehiclesObserved = @($VehicleRows13C | Select-Object -ExpandProperty vehicleId -Unique)
        tasksActivated = $TaskRows13C.Count
        localCandidateRows = $LocalRows13C.Count
        v2vCandidateRows = $V2vRows13C.Count
        v2vPoolRows = $V2vPoolRows13C.Count
        cellTrafficEvents = $CellEventRows.Count
        cellBandwidthBuckets = $CellBucketRows.Count
        safeCellBucketsUsed = $GatewayPoolRows.Count
        ticksWithoutSafeCellBucket = $TicksWithoutSafeCellBucket
        accessLinkRows = $AccessLinkRows.Count
        activeAccessLinkRows = $ActiveAccessLinkRows
        statesWithoutActiveGateway = $StatesWithoutActiveGateway
        gatewayPoolRows = $GatewayPoolRows.Count
        edgeCandidateRows = $EdgeCandidateRows
        cloudCandidateRows = $CloudCandidateRows
        snapshotsGenerated = $SnapshotFiles.Count
        snapshotManifestRows = $ManifestRows.Count
        snapshotJavaLoaderValidated = $JavaValidation.SnapshotsLoaded
        snapshotJavaValidatorValidated = $JavaValidation.SnapshotsLoaded
        futureVehicleStateViolations = $FutureVehicleStateViolations
        futureTaskActivationViolations = $FutureTaskActivationViolations
        futureCellEventViolations = $FutureCellEventViolations
        futureSafeBucketViolations = $FutureSafeBucketViolations
        futureAccessLinkViolations = $FutureAccessLinkViolations
        futureCandidateViolations = $FutureCandidateViolations
        futurePoolViolations = $FuturePoolViolations
        multipleActiveGatewayViolations = $MultipleActiveGatewayViolations
        activeUnavailableLinkViolations = $ActiveUnavailableLinkViolations
        unresolvedGatewayPoolViolations = $UnresolvedGatewayPoolViolations
        orphanReferenceViolations = $OrphanReferenceViolations
        duplicateCandidateIds = $DuplicateCandidateIds
        duplicatePoolIds = $DuplicatePoolIds
        cloudPlaceholderViolations = $CloudPlaceholderViolations
        javaLoaderValidationFailures = $JavaValidation.LoaderFailures
        javaValidatorFailures = $JavaValidation.ValidatorFailures
        absolutePathsInVersionedDiagnostics = 0
        absolutePathFiles = @()
        cellBandwidthSource = "DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES"
        accessLinkPolicy = "NEAREST_AVAILABLE_GATEWAY_BY_PROJECTED_DISTANCE"
        remoteCandidatePolicy = "EDGE_AND_CLOUD_REQUIRE_ACTIVE_GATEWAY_AND_SAFE_POOL"
        snapshotAssemblyPolicy = "CAUSAL_LATEST_AVAILABLE_DATA_AT_OR_BEFORE_TICK"
        warnings = $Warnings13C
        errors = $Errors13C
        phase13cStatus = if ($Completed13C) { "COMPLETED" } else { "BLOCKED" }
        readyForPhase13D = $Completed13C
    }

    Set-Content -LiteralPath $DiagnosticsFile13C -Value ($Result13C | ConvertTo-Json -Depth 8) -Encoding UTF8
    $AbsoluteDiagnostics = Get-Phase13CAbsoluteDiagnostics
    $Result13C.absolutePathsInVersionedDiagnostics = $AbsoluteDiagnostics.Count
    $Result13C.absolutePathFiles = $AbsoluteDiagnostics.Files
    if ($AbsoluteDiagnostics.Count -ne 0) {
        $Errors13C += "absolutePathsInVersionedDiagnostics must be 0"
    }
    $Completed13C = $Errors13C.Count -eq 0
    $Result13C.errors = $Errors13C
    $Result13C.phase13cStatus = if ($Completed13C) { "COMPLETED" } else { "BLOCKED" }
    $Result13C.readyForPhase13D = $Completed13C
    Set-Content -LiteralPath $DiagnosticsFile13C -Value ($Result13C | ConvertTo-Json -Depth 8) -Encoding UTF8

    Write-Host "Validation result: $($Result13C.phase13cStatus)"
    Write-Host "Diagnostics: $DiagnosticsFile13C"
    Write-Host "Run: $($LatestRun13C.Name)"
    Write-Host "coordinatorTicks=$($Result13C.coordinatorTicks)"
    Write-Host "cellTrafficEvents=$($Result13C.cellTrafficEvents)"
    Write-Host "cellBandwidthBuckets=$($Result13C.cellBandwidthBuckets)"
    Write-Host "safeCellBucketsUsed=$($Result13C.safeCellBucketsUsed)"
    Write-Host "activeAccessLinkRows=$($Result13C.activeAccessLinkRows)"
    Write-Host "edgeCandidateRows=$($Result13C.edgeCandidateRows)"
    Write-Host "cloudCandidateRows=$($Result13C.cloudCandidateRows)"
    Write-Host "snapshotsGenerated=$($Result13C.snapshotsGenerated)"
    Write-Host "javaLoaderValidationFailures=$($Result13C.javaLoaderValidationFailures)"
    Write-Host "javaValidatorFailures=$($Result13C.javaValidatorFailures)"
    Write-Host "absolutePathsInVersionedDiagnostics=$($Result13C.absolutePathsInVersionedDiagnostics)"
    if (-not $Completed13C) {
        Write-Host "Errors:"
        foreach ($ErrorItem in $Errors13C) {
            Write-Host "  $ErrorItem"
        }
        throw "Phase 13C validation failed"
    }
    return
}

if (-not (Test-Path -LiteralPath $ScenarioConfig -PathType Leaf)) {
    throw "Live state config not found: $ScenarioConfig"
}

$Config = Get-Content -LiteralPath $ScenarioConfig -Raw | ConvertFrom-Json
$LatestRun = Get-ChildItem -LiteralPath $LogsRoot -Directory |
    Where-Object { $_.Name -like "*-MaGaLiveStateLayerStudy" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $LatestRun) {
    throw "No MaGaLiveStateLayerStudy run found under $LogsRoot"
}
$SourceRunRelativeDir = "tmp/mosaic-25.2/logs/$($LatestRun.Name)"
$PreviewDir = Join-Path $LatestRun.FullName "live-state-layer"
if (-not (Test-Path -LiteralPath $PreviewDir -PathType Container)) {
    throw "Live state preview directory not found: $PreviewDir"
}

function Import-PreviewCsv {
    param(
        [string]$FileName
    )
    $Path = Join-Path $PreviewDir $FileName
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required preview CSV missing: $Path"
    }
    return ,@(Import-Csv -LiteralPath $Path)
}

function Get-FieldValues {
    param(
        [string[]]$Lines,
        [string]$Field
    )
    $Values = @()
    foreach ($Line in $Lines) {
        if ($Line -match ("[|]" + [regex]::Escape($Field) + "=([^| )]+)")) {
            $Values += $Matches[1]
        }
    }
    return $Values
}

function Count-DuplicateKeys {
    param(
        [array]$Rows,
        [scriptblock]$KeySelector
    )
    $Groups = @{}
    foreach ($Row in $Rows) {
        $Key = & $KeySelector $Row
        if (-not $Groups.ContainsKey($Key)) {
            $Groups[$Key] = 0
        }
        $Groups[$Key]++
    }
    $Duplicates = 0
    foreach ($Count in $Groups.Values) {
        if ($Count -gt 1) {
            $Duplicates += ($Count - 1)
        }
    }
    return $Duplicates
}

function Pool-IdForPair {
    param(
        [string]$A,
        [string]$B
    )
    $Ordered = @($A, $B) | Sort-Object {
        if ($_ -match "^(.*?)(\d+)$") {
            "{0}{1:D8}" -f $Matches[1], [int]$Matches[2]
        } else {
            $_
        }
    }
    return "direct_v2v_pool__$($Ordered[0])__$($Ordered[1])"
}

function Count-AbsolutePathMatchesInDiagnostics {
    $Count = 0
    $Files = @(
        (Join-Path $DiagnosticsDir "phase_13a_live_api_probe_validation.json"),
        $DiagnosticsFile
    )
    foreach ($File in $Files) {
        if (Test-Path -LiteralPath $File -PathType Leaf) {
            $Text = Get-Content -LiteralPath $File -Raw
            $Count += ([regex]::Matches($Text, "[A-Za-z]:\\\\|C:/Users|latestRunDir")).Count
        }
    }
    return $Count
}

$LogFiles = Get-ChildItem -LiteralPath $LatestRun.FullName -Recurse -File -Filter "*.log"
$LogLines = foreach ($LogFile in $LogFiles) {
    Get-Content -LiteralPath $LogFile.FullName
}
$MarkerLines = @($LogLines | Where-Object { $_ -match "LIVE_STATE_" })
$CoordinatorStartLines = @($MarkerLines | Where-Object { $_ -match "LIVE_STATE_COORDINATOR_START" })
$CoordinatorTickLines = @($MarkerLines | Where-Object { $_ -match "LIVE_STATE_COORDINATOR_TICK" })
$CoordinatorStopLines = @($MarkerLines | Where-Object { $_ -match "LIVE_STATE_COORDINATOR_STOP" })
$VehicleStartLines = @($MarkerLines | Where-Object { $_ -match "LIVE_STATE_VEHICLE_START" })
$VehicleUpdateLines = @($MarkerLines | Where-Object { $_ -match "LIVE_STATE_VEHICLE_UPDATE" })
$VehicleStopLines = @($MarkerLines | Where-Object { $_ -match "LIVE_STATE_VEHICLE_STOP" })
$ImmutableTestLines = @($MarkerLines | Where-Object { $_ -match "LIVE_STATE_IMMUTABLE_VIEW_TEST" })
$StaticCatalogLines = @($MarkerLines | Where-Object { $_ -match "LIVE_STATE_STATIC_INFRASTRUCTURE_LOADED" })
$CoordinatorTickTimes = @(Get-FieldValues -Lines $CoordinatorTickLines -Field "simulationTime" | ForEach-Object { [Int64]$_ })
$TickTimeSet = @{}
foreach ($Time in $CoordinatorTickTimes) {
    $TickTimeSet[[string]$Time] = $true
}

$VehicleRows = Import-PreviewCsv "live_vehicle_state_preview.csv"
$TaskRows = Import-PreviewCsv "live_task_preview.csv"
$LocalRows = Import-PreviewCsv "live_local_candidate_preview.csv"
$V2vRows = Import-PreviewCsv "live_v2v_candidate_preview.csv"
$PoolRows = Import-PreviewCsv "live_v2v_bandwidth_pool_preview.csv"

$VehicleStateByTimeVehicle = @{}
foreach ($Row in $VehicleRows) {
    $VehicleStateByTimeVehicle["$($Row.timeNs)|$($Row.vehicleId)"] = $Row
}

$DuplicateLocalCandidateIds = Count-DuplicateKeys -Rows $LocalRows -KeySelector { param($Row) "$($Row.timeNs)|$($Row.candidateId)" }
$DuplicateV2vCandidateIds = Count-DuplicateKeys -Rows $V2vRows -KeySelector { param($Row) "$($Row.timeNs)|$($Row.candidateId)" }
$DuplicateV2vPoolIds = Count-DuplicateKeys -Rows $PoolRows -KeySelector { param($Row) "$($Row.timeNs)|$($Row.poolId)" }

$AmbiguousV2vPoolIds = 0
$PoolPairByTimeId = @{}
foreach ($Row in $PoolRows) {
    $Key = "$($Row.timeNs)|$($Row.poolId)"
    $Pair = "$($Row.memberVehicleA)|$($Row.memberVehicleB)"
    if ($PoolPairByTimeId.ContainsKey($Key) -and $PoolPairByTimeId[$Key] -ne $Pair) {
        $AmbiguousV2vPoolIds++
    } else {
        $PoolPairByTimeId[$Key] = $Pair
    }
}

$SourceEqualsTargetViolations = 0
$InactiveVehicleCandidateViolations = 0
$InactiveRadioCandidateViolations = 0
$RadiusViolations = 0
$PoolDirectionSharedViolations = 0
$FutureVehicleStateViolations = 0
$FutureTaskActivationViolations = 0
$FutureCandidateViolations = 0
$FuturePoolViolations = 0

foreach ($Row in $VehicleRows) {
    if ([Int64]$Row.lastUpdateTimeNs -gt [Int64]$Row.timeNs) {
        $FutureVehicleStateViolations++
    }
}
foreach ($Row in $TaskRows) {
    if ([Int64]$Row.activationTimeNs -gt [Int64]$Row.timeNs) {
        $FutureTaskActivationViolations++
    }
}
foreach ($Row in $LocalRows) {
    if (-not $TickTimeSet.ContainsKey([string]$Row.timeNs)) {
        $FutureCandidateViolations++
    }
}
foreach ($Row in $V2vRows) {
    if (-not $TickTimeSet.ContainsKey([string]$Row.timeNs)) {
        $FutureCandidateViolations++
    }
    if ($Row.sourceVehicleId -eq $Row.targetVehicleId -or $Row.sourceVehicleId -eq $Row.executionNodeId) {
        $SourceEqualsTargetViolations++
    }
    $SourceState = $VehicleStateByTimeVehicle["$($Row.timeNs)|$($Row.sourceVehicleId)"]
    $TargetState = $VehicleStateByTimeVehicle["$($Row.timeNs)|$($Row.targetVehicleId)"]
    if ($null -eq $SourceState -or $null -eq $TargetState -or $SourceState.active -ne "true" -or $TargetState.active -ne "true") {
        $InactiveVehicleCandidateViolations++
    } else {
        if ($SourceState.adHocEnabled -ne "true" -or $TargetState.adHocEnabled -ne "true") {
            $InactiveRadioCandidateViolations++
        }
    }
    if ([double]$Row.distanceMeters -gt [double]$Config.singlehopRadiusMeters) {
        $RadiusViolations++
    }
    $ExpectedPoolId = Pool-IdForPair -A $Row.sourceVehicleId -B $Row.targetVehicleId
    if ($Row.bandwidthPoolId -ne $ExpectedPoolId) {
        $PoolDirectionSharedViolations++
    }
}
foreach ($Row in $PoolRows) {
    if (-not $TickTimeSet.ContainsKey([string]$Row.timeNs)) {
        $FuturePoolViolations++
    }
    if ($Row.memberVehicleA -eq $Row.memberVehicleB) {
        $SourceEqualsTargetViolations++
    }
}

$V2vGroups = $V2vRows | Group-Object { "$($_.timeNs)|$($_.bandwidthPoolId)" }
foreach ($Group in $V2vGroups) {
    if ($Group.Count -ne 2) {
        $PoolDirectionSharedViolations++
        continue
    }
    $Rows = @($Group.Group)
    if ($Rows[0].sourceVehicleId -ne $Rows[1].targetVehicleId -or $Rows[0].targetVehicleId -ne $Rows[1].sourceVehicleId) {
        $PoolDirectionSharedViolations++
    }
}

$SourceText = Get-ChildItem -LiteralPath $SourceRoot -Recurse -File -Filter "*.java" |
    ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }
$ArtificialSendCalls = @($SourceText | Select-String -Pattern "sendV2xMessage|sendCam" -AllMatches).Matches.Count

$OutputCsv = Join-Path $LatestRun.FullName "output.csv"
$V2xTransmissionEvents = 0
$V2xReceptionEvents = 0
if (Test-Path -LiteralPath $OutputCsv -PathType Leaf) {
    $OutputLines = Get-Content -LiteralPath $OutputCsv
    $V2xTransmissionEvents = @($OutputLines | Where-Object { $_ -match "V2X_MESSAGE_TRANSMISSION" }).Count
    $V2xReceptionEvents = @($OutputLines | Where-Object { $_ -match "V2X_MESSAGE_RECEPTION" }).Count
}

$MosaicLog = Join-Path $LatestRun.FullName "MOSAIC.log"
$MosaicLogText = if (Test-Path -LiteralPath $MosaicLog -PathType Leaf) {
    Get-Content -LiteralPath $MosaicLog -Raw
} else {
    ""
}
$SimulationCompleted = $MosaicLogText -match "Simulation ended after" -and $MosaicLogText -match "Simulation finished"

$CoreStatus = git -c safe.directory="$SafeRepoRoot" status --short src
if ($LASTEXITCODE -ne 0) {
    throw "git status for src failed with exit code $LASTEXITCODE"
}
$CanonicalStatus = git -c safe.directory="$SafeRepoRoot" status --short data/mosaic-scenarios/MaGaIntegratedStudy
if ($LASTEXITCODE -ne 0) {
    throw "git status for canonical scenario failed with exit code $LASTEXITCODE"
}
$CoreModified = -not [string]::IsNullOrWhiteSpace(($CoreStatus | Out-String).Trim())
$CanonicalScenarioModified = -not [string]::IsNullOrWhiteSpace(($CanonicalStatus | Out-String).Trim())

$VehiclesObserved = @($VehicleRows | Select-Object -ExpandProperty vehicleId -Unique)
$DirectReachablePairs = $PoolRows.Count
$TasksActivated = $TaskRows.Count
$ImmutableSnapshotViewTestPassed = ($ImmutableTestLines.Count -ge 1 -and ($ImmutableTestLines[0] -match "passed=true"))
$StaticCatalogLoaded = ($StaticCatalogLines.Count -ge 1)

$Warnings = @()
$Errors = @()
function Require-Condition {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        $script:Errors += $Message
    }
}

Require-Condition $SimulationCompleted "simulationCompleted must be true"
Require-Condition ($CoordinatorStartLines.Count -eq 1) "coordinatorStarts must be 1"
Require-Condition ($CoordinatorTickLines.Count -ge 2) "coordinatorTicks must be >= 2"
Require-Condition ($CoordinatorStopLines.Count -eq 1) "coordinatorStops must be 1"
Require-Condition ($VehicleStartLines.Count -gt 0) "vehicleStarts must be > 0"
Require-Condition ($VehicleUpdateLines.Count -gt 0) "vehicleUpdates must be > 0"
Require-Condition ($VehicleStopLines.Count -gt 0) "vehicleStops must be > 0"
Require-Condition ($VehicleRows.Count -gt 0) "vehicleStateRows must be > 0"
Require-Condition ($TasksActivated -gt 0) "tasksActivated must be > 0"
Require-Condition ($LocalRows.Count -gt 0) "localCandidateRows must be > 0"
Require-Condition ($V2vRows.Count -gt 0) "v2vCandidateRows must be > 0"
Require-Condition ($PoolRows.Count -gt 0) "v2vPoolRows must be > 0"
Require-Condition ($DirectReachablePairs -gt 0) "directReachablePairs must be > 0"
Require-Condition ($DuplicateLocalCandidateIds -eq 0) "duplicateLocalCandidateIds must be 0"
Require-Condition ($DuplicateV2vCandidateIds -eq 0) "duplicateV2vCandidateIds must be 0"
Require-Condition ($DuplicateV2vPoolIds -eq 0) "duplicateV2vPoolIds must be 0"
Require-Condition ($AmbiguousV2vPoolIds -eq 0) "ambiguousV2vPoolIds must be 0"
Require-Condition ($SourceEqualsTargetViolations -eq 0) "sourceEqualsTargetViolations must be 0"
Require-Condition ($InactiveVehicleCandidateViolations -eq 0) "inactiveVehicleCandidateViolations must be 0"
Require-Condition ($InactiveRadioCandidateViolations -eq 0) "inactiveRadioCandidateViolations must be 0"
Require-Condition ($RadiusViolations -eq 0) "radiusViolations must be 0"
Require-Condition ($PoolDirectionSharedViolations -eq 0) "poolDirectionSharedViolations must be 0"
Require-Condition ($FutureVehicleStateViolations -eq 0) "futureVehicleStateViolations must be 0"
Require-Condition ($FutureTaskActivationViolations -eq 0) "futureTaskActivationViolations must be 0"
Require-Condition ($FutureCandidateViolations -eq 0) "futureCandidateViolations must be 0"
Require-Condition ($FuturePoolViolations -eq 0) "futurePoolViolations must be 0"
Require-Condition ($ArtificialSendCalls -eq 0) "artificialV2xSendCallsInSource must be 0"
Require-Condition ($V2xTransmissionEvents -eq 0) "v2xTransmissionEventsInOutput must be 0"
Require-Condition ($V2xReceptionEvents -eq 0) "v2xReceptionEventsInOutput must be 0"
Require-Condition $ImmutableSnapshotViewTestPassed "immutable snapshot view self-test must pass"
Require-Condition $StaticCatalogLoaded "static infrastructure catalog must be loaded"
Require-Condition (-not $CoreModified) "coreModified must be false"
Require-Condition (-not $CanonicalScenarioModified) "canonicalScenarioModified must be false"

$AbsolutePathsInVersionedDiagnostics = Count-AbsolutePathMatchesInDiagnostics
Require-Condition ($AbsolutePathsInVersionedDiagnostics -eq 0) "absolutePathsInVersionedDiagnostics must be 0"

$Completed = $Errors.Count -eq 0
$Result = [ordered]@{
    phase = "13B_LIVE_CAUSAL_STATE_LAYER_LOCAL_AND_V2V"
    sourceRun = $LatestRun.Name
    sourceRunName = $LatestRun.Name
    sourceRunRelativeDir = $SourceRunRelativeDir
    scenarioName = "MaGaLiveStateLayerStudy"
    simulationCompleted = $SimulationCompleted
    coordinatorStarts = $CoordinatorStartLines.Count
    coordinatorTicks = $CoordinatorTickLines.Count
    coordinatorStops = $CoordinatorStopLines.Count
    vehicleStarts = $VehicleStartLines.Count
    vehicleUpdates = $VehicleUpdateLines.Count
    vehicleStops = $VehicleStopLines.Count
    vehiclesObserved = $VehiclesObserved
    vehicleStateRows = $VehicleRows.Count
    taskRows = $TaskRows.Count
    tasksActivated = $TasksActivated
    localCandidateRows = $LocalRows.Count
    v2vCandidateRows = $V2vRows.Count
    v2vPoolRows = $PoolRows.Count
    directReachablePairs = $DirectReachablePairs
    duplicateLocalCandidateIds = $DuplicateLocalCandidateIds
    duplicateV2vCandidateIds = $DuplicateV2vCandidateIds
    duplicateV2vPoolIds = $DuplicateV2vPoolIds
    ambiguousV2vPoolIds = $AmbiguousV2vPoolIds
    sourceEqualsTargetViolations = $SourceEqualsTargetViolations
    inactiveVehicleCandidateViolations = $InactiveVehicleCandidateViolations
    inactiveRadioCandidateViolations = $InactiveRadioCandidateViolations
    radiusViolations = $RadiusViolations
    poolDirectionSharedViolations = $PoolDirectionSharedViolations
    futureVehicleStateViolations = $FutureVehicleStateViolations
    futureTaskActivationViolations = $FutureTaskActivationViolations
    futureCandidateViolations = $FutureCandidateViolations
    futurePoolViolations = $FuturePoolViolations
    artificialV2xSendCallsInSource = $ArtificialSendCalls
    v2xTransmissionEventsInOutput = $V2xTransmissionEvents
    v2xReceptionEventsInOutput = $V2xReceptionEvents
    absolutePathsInVersionedDiagnostics = $AbsolutePathsInVersionedDiagnostics
    immutableSnapshotViewTestPassed = $ImmutableSnapshotViewTestPassed
    staticInfrastructureCatalogLoaded = $StaticCatalogLoaded
    coreModified = $CoreModified
    canonicalScenarioModified = $CanonicalScenarioModified
    warnings = $Warnings
    errors = $Errors
    phase13bStatus = if ($Completed) { "COMPLETED" } else { "BLOCKED" }
    readyForPhase13C = $Completed
}

$Json = $Result | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $DiagnosticsFile -Value $Json -Encoding UTF8

Write-Host "Validation result: $($Result.phase13bStatus)"
Write-Host "Diagnostics: $DiagnosticsFile"
Write-Host "Run: $($LatestRun.Name)"
Write-Host "coordinatorStarts=$($Result.coordinatorStarts)"
Write-Host "coordinatorTicks=$($Result.coordinatorTicks)"
Write-Host "coordinatorStops=$($Result.coordinatorStops)"
Write-Host "vehicleStarts=$($Result.vehicleStarts)"
Write-Host "vehicleUpdates=$($Result.vehicleUpdates)"
Write-Host "vehicleStops=$($Result.vehicleStops)"
Write-Host "vehicleStateRows=$($Result.vehicleStateRows)"
Write-Host "tasksActivated=$($Result.tasksActivated)"
Write-Host "localCandidateRows=$($Result.localCandidateRows)"
Write-Host "v2vCandidateRows=$($Result.v2vCandidateRows)"
Write-Host "v2vPoolRows=$($Result.v2vPoolRows)"
Write-Host "directReachablePairs=$($Result.directReachablePairs)"
Write-Host "futureVehicleStateViolations=$($Result.futureVehicleStateViolations)"
Write-Host "futureTaskActivationViolations=$($Result.futureTaskActivationViolations)"
Write-Host "futureCandidateViolations=$($Result.futureCandidateViolations)"
Write-Host "futurePoolViolations=$($Result.futurePoolViolations)"
Write-Host "artificialV2xSendCallsInSource=$($Result.artificialV2xSendCallsInSource)"
Write-Host "absolutePathsInVersionedDiagnostics=$($Result.absolutePathsInVersionedDiagnostics)"
if (-not $Completed) {
    Write-Host "Errors:"
    foreach ($ErrorItem in $Errors) {
        Write-Host "  $ErrorItem"
    }
    throw "Phase 13B validation failed"
}
