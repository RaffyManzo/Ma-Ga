param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiveMagaRuntimeStudy"
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$SafeRepoRoot = $RepoRoot.Replace("\", "/")
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$LogsRoot = Join-Path $ResolvedMosaicRoot "logs"
$DiagnosticsDir = Join-Path $RepoRoot "data\mosaic-study\diagnostics"
$DiagnosticsFile = Join-Path $DiagnosticsDir "phase_13e_live_bridge_end_to_end_validation.json"
$RuntimeConfigFile = Join-Path $RepoRoot "data\mosaic-scenarios\$ScenarioName\application\ma_ga_live_runtime_config.json"
$OverrunConfigFile = Join-Path $RepoRoot "data\mosaic-scenarios\$ScenarioName\application\ma_ga_live_runtime_config_diagnostic_overrun.json"

if (-not (Test-Path -LiteralPath $LogsRoot -PathType Container)) {
    throw "MOSAIC logs root not found: $LogsRoot"
}
if (-not (Test-Path -LiteralPath $DiagnosticsDir -PathType Container)) {
    New-Item -ItemType Directory -Path $DiagnosticsDir | Out-Null
}
foreach ($ConfigFile in @($RuntimeConfigFile, $OverrunConfigFile)) {
    if (-not (Test-Path -LiteralPath $ConfigFile -PathType Leaf)) {
        throw "Runtime config not found: $ConfigFile"
    }
}

$RuntimeConfig = Get-Content -LiteralPath $RuntimeConfigFile -Raw | ConvertFrom-Json
$OverrunConfig = Get-Content -LiteralPath $OverrunConfigFile -Raw | ConvertFrom-Json
$DeltaTEpsilon = [double]$RuntimeConfig.deltaTMaxComparisonEpsilonSeconds
if ($DeltaTEpsilon -le 0.0) {
    $DeltaTEpsilon = 1.0E-9
}
$OverrunDeltaTEpsilon = [double]$OverrunConfig.deltaTMaxComparisonEpsilonSeconds
if ($OverrunDeltaTEpsilon -gt $DeltaTEpsilon) {
    $DeltaTEpsilon = $OverrunDeltaTEpsilon
}

function Import-RuntimeCsv {
    param(
        [string]$RunDir,
        [string]$FileName
    )
    $Path = Join-Path $RunDir "live-maga-runtime\$FileName"
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return @()
    }
    return @(Import-Csv -LiteralPath $Path)
}

function Is-SimulationCompleted {
    param([string]$RunDir)
    $MosaicLog = Join-Path $RunDir "MOSAIC.log"
    if (-not (Test-Path -LiteralPath $MosaicLog -PathType Leaf)) {
        return $false
    }
    $Text = Get-Content -LiteralPath $MosaicLog -Raw
    return ($Text -match "Simulation ended after" -and $Text -match "Simulation finished")
}

function Find-RuntimeRun {
    param([string]$Profile)
    $Candidates = Get-ChildItem -LiteralPath $LogsRoot -Directory |
        Where-Object { $_.Name -like "*-$ScenarioName" } |
        Sort-Object LastWriteTime -Descending
    foreach ($Run in $Candidates) {
        $RuntimeRows = Import-RuntimeCsv -RunDir $Run.FullName -FileName "live_ga_runtime_trace.csv"
        if ($RuntimeRows.Count -gt 0 -and $RuntimeRows[0].profile -eq $Profile) {
            return $Run
        }
    }
    return $null
}

function Count-AbsoluteDiagnostics {
    $Count = 0
    $Files = @()
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

function To-Bool {
    param([object]$Value)
    if ($null -eq $Value) {
        return $false
    }
    return ([string]$Value).Equals("true", [System.StringComparison]::OrdinalIgnoreCase)
}

function Sum-IntField {
    param(
        [array]$Rows,
        [string]$Field
    )
    $Total = 0
    foreach ($Row in $Rows) {
        if ($Row.$Field -ne $null -and $Row.$Field -ne "") {
            $Total += [int]$Row.$Field
        }
    }
    return $Total
}

function Max-IntField {
    param(
        [array]$Rows,
        [string]$Field
    )
    $Max = 0
    foreach ($Row in $Rows) {
        if ($Row.$Field -ne $null -and $Row.$Field -ne "") {
            $Value = [int]$Row.$Field
            if ($Value -gt $Max) {
                $Max = $Value
            }
        }
    }
    return $Max
}

function As-Array {
    param([object]$Value)
    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [System.Array]) {
        return @($Value)
    }
    return @($Value)
}

function Join-SortedValues {
    param([array]$Values)
    return [string]::Join("|", @($Values | Where-Object { $_ -ne $null } | Sort-Object))
}

function Run-JavaSnapshotValidation {
    param([string]$SnapshotsPath)

    $JsonFiles = @(Get-ChildItem -LiteralPath $SnapshotsPath -File -Filter "*.json" -ErrorAction SilentlyContinue)
    if ($JsonFiles.Count -eq 0) {
        return [pscustomobject]@{
            LoaderFailures = 1
            ValidatorFailures = 1
            SnapshotsLoaded = 0
            Output = "no published snapshot copies found"
        }
    }

    $HarnessSource = Join-Path $RepoRoot "tools\mosaic-live-state-layer\harness\LiveSnapshotValidationHarness.java"
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

function Test-SnapshotParity {
    param([string]$RunDir)

    $RuntimeDir = Join-Path $RunDir "live-maga-runtime"
    $ManifestPath = Join-Path $RuntimeDir "live_published_snapshot_manifest.csv"
    $ManifestRows = @()
    if (Test-Path -LiteralPath $ManifestPath -PathType Leaf) {
        $ManifestRows = @(Import-Csv -LiteralPath $ManifestPath)
    }
    $Mismatches = 0
    foreach ($Row in $ManifestRows) {
        $SnapshotPath = Join-Path $RuntimeDir ([string]$Row.relativePath)
        if (-not (Test-Path -LiteralPath $SnapshotPath -PathType Leaf)) {
            $Mismatches++
            continue
        }
        $Snapshot = Get-Content -LiteralPath $SnapshotPath -Raw | ConvertFrom-Json
        $Vehicles = @(As-Array $Snapshot.vehicles)
        $Tasks = @(As-Array $Snapshot.tasks)
        $Candidates = @(As-Array $Snapshot.candidateNodes)
        $AccessLinks = @(As-Array $Snapshot.accessLinks)
        $Pools = @(As-Array $Snapshot.bandwidthPools)

        if ([string]$Snapshot.snapshotId -ne [string]$Row.snapshotId) { $Mismatches++ }
        if ([Math]::Abs(([double]$Snapshot.timeSeconds) - ([double]$Row.snapshotTimeSeconds)) -gt 1.0E-6) { $Mismatches++ }
        if ($Vehicles.Count -ne [int]$Row.vehicles) { $Mismatches++ }
        if ($Tasks.Count -ne [int]$Row.tasks) { $Mismatches++ }
        if ($Candidates.Count -ne [int]$Row.candidates) { $Mismatches++ }
        if ($AccessLinks.Count -ne [int]$Row.accessLinks) { $Mismatches++ }
        if ($Pools.Count -ne [int]$Row.bandwidthPools) { $Mismatches++ }

        $LocalCount = @($Candidates | Where-Object { $_.type -eq "LOCAL" }).Count
        $VehicleCount = @($Candidates | Where-Object { $_.type -eq "VEHICLE" }).Count
        $EdgeCount = @($Candidates | Where-Object { $_.type -eq "EDGE" }).Count
        $CloudCount = @($Candidates | Where-Object { $_.type -eq "CLOUD" }).Count
        if ($LocalCount -ne [int]$Row.localCandidates) { $Mismatches++ }
        if ($VehicleCount -ne [int]$Row.vehicleCandidates) { $Mismatches++ }
        if ($EdgeCount -ne [int]$Row.edgeCandidates) { $Mismatches++ }
        if ($CloudCount -ne [int]$Row.cloudCandidates) { $Mismatches++ }

        $CandidateIds = Join-SortedValues @($Candidates | ForEach-Object { $_.candidateId })
        $PoolIds = Join-SortedValues @($Pools | ForEach-Object { $_.poolId })
        $AccessLinkIds = Join-SortedValues @($AccessLinks | ForEach-Object { $_.accessLinkId })
        $TaskIds = Join-SortedValues @($Tasks | ForEach-Object { $_.taskId })
        if ($CandidateIds -ne [string]$Row.candidateIds) { $Mismatches++ }
        if ($PoolIds -ne [string]$Row.poolIds) { $Mismatches++ }
        if ($AccessLinkIds -ne [string]$Row.accessLinkIds) { $Mismatches++ }
        if ($TaskIds -ne [string]$Row.taskIds) { $Mismatches++ }
    }
    return [pscustomobject]@{
        Rows = $ManifestRows.Count
        Mismatches = $Mismatches
    }
}

$NormalRun = Find-RuntimeRun -Profile "normal"
$OverrunRun = Find-RuntimeRun -Profile "diagnostic-overrun"
$Runs = @()
if ($NormalRun -ne $null) { $Runs += $NormalRun }
if ($OverrunRun -ne $null) { $Runs += $OverrunRun }
if ($Runs.Count -eq 0) {
    throw "No live MA-GA runtime runs found under $LogsRoot"
}

$AllRuntimeRows = @()
$AllStrategyRows = @()
$AllBridgeRows = @()
$AllOverrunRows = @()
$AllManifestRows = @()
$SimulationCompleted = $true
foreach ($Run in $Runs) {
    $AllRuntimeRows += Import-RuntimeCsv -RunDir $Run.FullName -FileName "live_ga_runtime_trace.csv"
    $AllStrategyRows += Import-RuntimeCsv -RunDir $Run.FullName -FileName "live_strategy_application_trace.csv"
    $AllBridgeRows += Import-RuntimeCsv -RunDir $Run.FullName -FileName "live_bridge_snapshot_trace.csv"
    $AllOverrunRows += Import-RuntimeCsv -RunDir $Run.FullName -FileName "live_overrun_trace.csv"
    $AllManifestRows += Import-RuntimeCsv -RunDir $Run.FullName -FileName "live_published_snapshot_manifest.csv"
    $SimulationCompleted = $SimulationCompleted -and (Is-SimulationCompleted -RunDir $Run.FullName)
}

$SubmittedRows = @($AllRuntimeRows | Where-Object { To-Bool $_.gaSubmitted })
$CompletedRows = @($AllRuntimeRows | Where-Object {
    $_.runtimeState -eq "RESULT_READY_WITHIN_BOUND" -or $_.runtimeState -eq "STALE_RESULT_DISCARDED"
})
$AppliedRuntimeRows = @($AllRuntimeRows | Where-Object { To-Bool $_.resultApplied })
$StaleRows = @($AllRuntimeRows | Where-Object {
    $_.runtimeState -eq "STALE_RESULT_DISCARDED"
})
$ResolvedBridgeRows = @($AllBridgeRows | Where-Object { To-Bool $_.snapshotResolved })
$EmptyBridgeRows = @($AllBridgeRows | Where-Object { -not (To-Bool $_.snapshotResolved) })

$FutureSnapshotViolations = 0
foreach ($Row in $AllRuntimeRows) {
    if ($Row.snapshotTimeSeconds -ne "" -and $Row.simulationTimeNs -ne "") {
        $SnapshotNs = [double]$Row.snapshotTimeSeconds * 1000000000.0
        if ($SnapshotNs -gt ([double]$Row.simulationTimeNs + 1.0)) {
            $FutureSnapshotViolations++
        }
    }
}
foreach ($Row in $AllBridgeRows) {
    if ($Row.snapshotTimeSeconds -ne "" -and $Row.simulationTimeNs -ne "") {
        $SnapshotNs = [double]$Row.snapshotTimeSeconds * 1000000000.0
        if ($SnapshotNs -gt ([double]$Row.simulationTimeNs + 1.0)) {
            $FutureSnapshotViolations++
        }
    }
}

$FuturePoolViolations = [Math]::Max(
    (Max-IntField -Rows $AllRuntimeRows -Field "futurePoolViolations"),
    (Max-IntField -Rows $AllBridgeRows -Field "futurePoolViolations")
)
$InvalidPoolBandwidthViolations = [Math]::Max(
    (Max-IntField -Rows $AllRuntimeRows -Field "invalidPoolBandwidthViolations"),
    (Max-IntField -Rows $AllBridgeRows -Field "invalidPoolBandwidthViolations")
)

$DeltaTMaxMismatchViolations = 0
foreach ($Row in $CompletedRows) {
    if ($Row.deltaTMaxMismatchSeconds -ne "" -and
            ([double]$Row.deltaTMaxMismatchSeconds) -gt $DeltaTEpsilon) {
        $DeltaTMaxMismatchViolations++
    }
}

$WaitCapRows = @($AllRuntimeRows | Where-Object { $_.runtimeState -eq "WAIT_CAP_REACHED" })
$WaitCapReachedObserved = $WaitCapRows.Count -gt 0
$WaitCapDetectedBeforeCompletion = @($WaitCapRows | Where-Object {
    (To-Bool $_.timeoutDetectedBeforeCompletion) -and -not (To-Bool $_.gaCompleted)
}).Count -gt 0
$StaleResultDiscardedObserved = $StaleRows.Count -gt 0
$FreshReoptimizationRequestedObserved =
        @($AllRuntimeRows | Where-Object { $_.runtimeState -eq "FRESH_REOPTIMIZATION_REQUESTED" }).Count -gt 0
$LastAppliedStrategyPreservedWhileRunning =
        @($AllRuntimeRows | Where-Object {
            $_.runtimeState -eq "GA_RUNNING" -and -not [string]::IsNullOrWhiteSpace($_.lastAppliedStrategySnapshotId)
        }).Count -gt 0

$StaleSnapshotIds = @{}
foreach ($Row in $StaleRows) {
    if (-not [string]::IsNullOrWhiteSpace($Row.snapshotId)) {
        $StaleSnapshotIds["$($Row.profile)|$($Row.snapshotId)"] = $true
    }
}
$StaleResultAppliedCount = 0
foreach ($Row in $AllStrategyRows) {
    if ($StaleSnapshotIds.ContainsKey("$($Row.profile)|$($Row.snapshotId)")) {
        $StaleResultAppliedCount++
    }
}
$StaleResultNeverApplied = $StaleResultAppliedCount -eq 0

$LocalAssignments = Sum-IntField -Rows $AllStrategyRows -Field "localAssignments"
$VehicleAssignments = Sum-IntField -Rows $AllStrategyRows -Field "vehicleAssignments"
$EdgeAssignments = Sum-IntField -Rows $AllStrategyRows -Field "edgeAssignments"
$CloudAssignments = Sum-IntField -Rows $AllStrategyRows -Field "cloudAssignments"

$PublishedSnapshotCopies = 0
$SnapshotParityMismatches = 0
$JavaLoaderValidationFailures = 0
$JavaValidatorFailures = 0
foreach ($Run in $Runs) {
    $PublishedDir = Join-Path $Run.FullName "live-maga-runtime\published-snapshots"
    $Copies = @(Get-ChildItem -LiteralPath $PublishedDir -File -Filter "*.json" -ErrorAction SilentlyContinue)
    $PublishedSnapshotCopies += $Copies.Count
    $Parity = Test-SnapshotParity -RunDir $Run.FullName
    $SnapshotParityMismatches += $Parity.Mismatches
    $JavaValidation = Run-JavaSnapshotValidation -SnapshotsPath $PublishedDir
    $JavaLoaderValidationFailures += $JavaValidation.LoaderFailures
    $JavaValidatorFailures += $JavaValidation.ValidatorFailures
}

$NormalRows = @($AllRuntimeRows | Where-Object { $_.profile -eq "normal" })
$NormalStrategyRows = @($AllStrategyRows | Where-Object { $_.profile -eq "normal" })
$OverrunRows = @($AllRuntimeRows | Where-Object { $_.profile -eq "diagnostic-overrun" })
$NormalRunValidated = $NormalRun -ne $null -and
        (Is-SimulationCompleted -RunDir $NormalRun.FullName) -and
        (@($NormalRows | Where-Object { To-Bool $_.gaSubmitted }).Count -gt 0) -and
        ($NormalStrategyRows.Count -gt 0)
$OverrunRunValidated = $OverrunRun -ne $null -and
        (Is-SimulationCompleted -RunDir $OverrunRun.FullName) -and
        $WaitCapReachedObserved -and
        $WaitCapDetectedBeforeCompletion -and
        $StaleResultDiscardedObserved -and
        $FreshReoptimizationRequestedObserved

$CoreStatus = git -c safe.directory="$SafeRepoRoot" status --short src
if ($LASTEXITCODE -ne 0) { throw "git status for src failed" }
$TemporalWindowManagerStatus = git -c safe.directory="$SafeRepoRoot" status --short src/window/core/TemporalWindowManager.java
if ($LASTEXITCODE -ne 0) { throw "git status for TemporalWindowManager failed" }
$ProtectedScenarioStatus = git -c safe.directory="$SafeRepoRoot" status --short `
    data/mosaic-scenarios/MaGaIntegratedStudy `
    data/mosaic-scenarios/MaGaLiveBridgeProbe `
    data/mosaic-scenarios/MaGaLiveStateLayerStudy `
    data/mosaic-scenarios/MaGaLiveInfrastructureSnapshotStudy
if ($LASTEXITCODE -ne 0) { throw "git status for protected scenarios failed" }
$StateLayerStatus = git -c safe.directory="$SafeRepoRoot" status --short tools/mosaic-live-state-layer
if ($LASTEXITCODE -ne 0) { throw "git status for live state layer failed" }

$CoreModified = -not [string]::IsNullOrWhiteSpace(($CoreStatus | Out-String).Trim())
$TemporalWindowManagerModified = -not [string]::IsNullOrWhiteSpace(($TemporalWindowManagerStatus | Out-String).Trim())
$ProtectedScenariosModified = -not [string]::IsNullOrWhiteSpace(($ProtectedScenarioStatus | Out-String).Trim())
$LiveStateLayerModified = -not [string]::IsNullOrWhiteSpace(($StateLayerStatus | Out-String).Trim())

$AbsoluteDiagnostics = Count-AbsoluteDiagnostics
$Warnings = @(
    "WARNING_STRATEGY_APPLICATION_IS_DIAGNOSTIC_NOT_TASK_EXECUTION",
    "WARNING_CELL_BANDWIDTH_IS_DIAGNOSTIC_RUNTIME_ACCOUNTING_NOT_FEDERATE_MEASUREMENT",
    "WARNING_REUSED_STRATEGY_MAY_BECOME_STALE_DURING_GA_OVERRUN",
    "WARNING_DIAGNOSTIC_ARTIFICIAL_GA_DELAY_USED_ONLY_FOR_OVERRUN_TEST",
    "WARNING_DIAGNOSTIC_CPU_AND_V2V_BANDWIDTH_REQUIRE_FUTURE_CALIBRATION",
    "WARNING_SCENARIO_NOT_YET_CALIBRATED_FOR_NON_LOCAL_OFFLOADING"
)
$Errors = @()
function Require-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { $script:Errors += $Message }
}

Require-Condition $SimulationCompleted "normal and overrun simulations must complete"
Require-Condition $NormalRunValidated "normal run must be validated"
Require-Condition $OverrunRunValidated "overrun run must be validated"
Require-Condition ($ResolvedBridgeRows.Count -gt 0) "at least one live bridge snapshot must resolve"
Require-Condition ($PublishedSnapshotCopies -gt 0) "at least one published snapshot copy must be written"
Require-Condition ($JavaLoaderValidationFailures -eq 0) "javaLoaderValidationFailures must be 0"
Require-Condition ($JavaValidatorFailures -eq 0) "javaValidatorFailures must be 0"
Require-Condition ($SnapshotParityMismatches -eq 0) "snapshotParityMismatches must be 0"
Require-Condition ($SubmittedRows.Count -gt 0) "at least one GA job must be submitted"
Require-Condition ($CompletedRows.Count -gt 0) "at least one GA job must complete"
Require-Condition ($AppliedRuntimeRows.Count -gt 0 -and $AllStrategyRows.Count -gt 0) "at least one strategy must be applied"
Require-Condition ($StaleRows.Count -gt 0) "at least one stale result must be discarded"
Require-Condition ($FutureSnapshotViolations -eq 0) "futureSnapshotViolations must be 0"
Require-Condition ($FuturePoolViolations -eq 0) "futurePoolViolations must be 0"
Require-Condition ($InvalidPoolBandwidthViolations -eq 0) "invalidPoolBandwidthViolations must be 0"
Require-Condition ($DeltaTMaxMismatchViolations -eq 0) "deltaTMaxMismatchViolations must be 0"
Require-Condition $WaitCapReachedObserved "WAIT_CAP_REACHED must be observed"
Require-Condition $WaitCapDetectedBeforeCompletion "WAIT_CAP_REACHED must be detected before worker completion"
Require-Condition $StaleResultDiscardedObserved "STALE_RESULT_DISCARDED must be observed"
Require-Condition $StaleResultNeverApplied "stale result must never be applied"
Require-Condition $FreshReoptimizationRequestedObserved "fresh reoptimization must be requested"
Require-Condition $LastAppliedStrategyPreservedWhileRunning "last strategy must be preserved while GA is running"
Require-Condition ($AbsoluteDiagnostics.Count -eq 0) "absolutePathsInVersionedDiagnostics must be 0"
Require-Condition (-not $CoreModified) "coreModified must be false"
Require-Condition (-not $TemporalWindowManagerModified) "TemporalWindowManager must remain unchanged"
Require-Condition (-not $ProtectedScenariosModified) "previous scenarios must remain unchanged"
Require-Condition (-not $LiveStateLayerModified) "tools/mosaic-live-state-layer must remain unchanged"

$ParallelGaViolations = 0
foreach ($Run in $Runs) {
    $StopLogs = Get-ChildItem -LiteralPath $Run.FullName -Recurse -File -Filter "*.log"
    foreach ($Log in $StopLogs) {
        foreach ($Line in Get-Content -LiteralPath $Log.FullName) {
            if ($Line -match "LIVE_MAGA_RUNTIME_COORDINATOR_STOP" -and
                    $Line -match "[|]parallelGaViolations=([^| )]+)") {
                $ParallelGaViolations += [int]$Matches[1]
            }
        }
    }
}
Require-Condition ($ParallelGaViolations -eq 0) "parallelGaViolations must be 0"

$Completed = $Errors.Count -eq 0
$LatestRun = $Runs | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$Result = [ordered]@{
    phase = "13E_LIVE_BRIDGE_END_TO_END_VALIDATION"
    normalRunName = if ($NormalRun -ne $null) { $NormalRun.Name } else { $null }
    overrunRunName = if ($OverrunRun -ne $null) { $OverrunRun.Name } else { $null }
    sourceRun = $LatestRun.Name
    sourceRunName = $LatestRun.Name
    sourceRunRelativeDir = "tmp/mosaic-25.2/logs/$($LatestRun.Name)"
    scenarioName = $ScenarioName
    simulationCompleted = $SimulationCompleted
    bridgeDescription = "LIVE_CAUSAL_MOSAIC_SNAPSHOT_BRIDGE"
    mosaicSystemStateSourceMode = "MOSAIC_LIVE"
    temporalWindowManagerModified = $TemporalWindowManagerModified
    coreModified = $CoreModified
    snapshotsRequested = $AllBridgeRows.Count
    snapshotsResolved = $ResolvedBridgeRows.Count
    snapshotEmptyResponses = $EmptyBridgeRows.Count
    publishedSnapshotCopies = $PublishedSnapshotCopies
    javaLoaderValidationFailures = $JavaLoaderValidationFailures
    javaValidatorFailures = $JavaValidatorFailures
    snapshotParityMismatches = $SnapshotParityMismatches
    gaJobsSubmitted = $SubmittedRows.Count
    gaJobsCompleted = $CompletedRows.Count
    gaJobsApplied = $AppliedRuntimeRows.Count
    gaJobsDiscardedAsStale = $StaleRows.Count
    parallelGaViolations = $ParallelGaViolations
    futureSnapshotViolations = $FutureSnapshotViolations
    futurePoolViolations = $FuturePoolViolations
    invalidPoolBandwidthViolations = $InvalidPoolBandwidthViolations
    deltaTMaxMismatchViolations = $DeltaTMaxMismatchViolations
    waitCapReachedObserved = $WaitCapReachedObserved
    waitCapDetectedBeforeCompletion = $WaitCapDetectedBeforeCompletion
    staleResultDiscardedObserved = $StaleResultDiscardedObserved
    staleResultNeverApplied = $StaleResultNeverApplied
    freshReoptimizationRequestedObserved = $FreshReoptimizationRequestedObserved
    lastAppliedStrategyPreservedWhileRunning = $LastAppliedStrategyPreservedWhileRunning
    strategyApplications = $AllStrategyRows.Count
    localAssignments = $LocalAssignments
    vehicleAssignments = $VehicleAssignments
    edgeAssignments = $EdgeAssignments
    cloudAssignments = $CloudAssignments
    normalRunValidated = $NormalRunValidated
    overrunRunValidated = $OverrunRunValidated
    absolutePathsInVersionedDiagnostics = $AbsoluteDiagnostics.Count
    absolutePathFiles = $AbsoluteDiagnostics.Files
    warnings = $Warnings
    errors = $Errors
    phase13eStatus = if ($Completed) { "COMPLETED" } else { "BLOCKED" }
    phase13Status = if ($Completed) { "COMPLETED" } else { "BLOCKED" }
    readyForCalibration = $Completed
}

Set-Content -LiteralPath $DiagnosticsFile -Value ($Result | ConvertTo-Json -Depth 8) -Encoding UTF8

Write-Host "Validation result: $($Result.phase13eStatus)"
Write-Host "Diagnostics: $DiagnosticsFile"
Write-Host "Normal run: $($Result.normalRunName)"
Write-Host "Overrun run: $($Result.overrunRunName)"
Write-Host "snapshotsRequested=$($Result.snapshotsRequested)"
Write-Host "snapshotsResolved=$($Result.snapshotsResolved)"
Write-Host "publishedSnapshotCopies=$($Result.publishedSnapshotCopies)"
Write-Host "javaLoaderValidationFailures=$($Result.javaLoaderValidationFailures)"
Write-Host "javaValidatorFailures=$($Result.javaValidatorFailures)"
Write-Host "snapshotParityMismatches=$($Result.snapshotParityMismatches)"
Write-Host "gaJobsSubmitted=$($Result.gaJobsSubmitted)"
Write-Host "gaJobsCompleted=$($Result.gaJobsCompleted)"
Write-Host "gaJobsApplied=$($Result.gaJobsApplied)"
Write-Host "gaJobsDiscardedAsStale=$($Result.gaJobsDiscardedAsStale)"
Write-Host "parallelGaViolations=$($Result.parallelGaViolations)"
Write-Host "futureSnapshotViolations=$($Result.futureSnapshotViolations)"
Write-Host "futurePoolViolations=$($Result.futurePoolViolations)"
Write-Host "invalidPoolBandwidthViolations=$($Result.invalidPoolBandwidthViolations)"
Write-Host "deltaTMaxMismatchViolations=$($Result.deltaTMaxMismatchViolations)"
Write-Host "waitCapDetectedBeforeCompletion=$($Result.waitCapDetectedBeforeCompletion)"
Write-Host "staleResultNeverApplied=$($Result.staleResultNeverApplied)"
Write-Host "freshReoptimizationRequestedObserved=$($Result.freshReoptimizationRequestedObserved)"
Write-Host "lastAppliedStrategyPreservedWhileRunning=$($Result.lastAppliedStrategyPreservedWhileRunning)"
Write-Host "absolutePathsInVersionedDiagnostics=$($Result.absolutePathsInVersionedDiagnostics)"
if (-not $Completed) {
    Write-Host "Errors:"
    foreach ($ErrorItem in $Errors) {
        Write-Host "  $ErrorItem"
    }
    throw "Phase 13E validation failed"
}
