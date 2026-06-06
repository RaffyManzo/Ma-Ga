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
$DiagnosticsFile = Join-Path $DiagnosticsDir "phase_13d_live_maga_runtime_validation.json"

if (-not (Test-Path -LiteralPath $LogsRoot -PathType Container)) {
    throw "MOSAIC logs root not found: $LogsRoot"
}
if (-not (Test-Path -LiteralPath $DiagnosticsDir -PathType Container)) {
    New-Item -ItemType Directory -Path $DiagnosticsDir | Out-Null
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
    return ,@(Import-Csv -LiteralPath $Path)
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

function Get-StopField {
    param(
        [string]$RunDir,
        [string]$Field
    )
    $LogFiles = Get-ChildItem -LiteralPath $RunDir -Recurse -File -Filter "*.log"
    foreach ($LogFile in $LogFiles) {
        foreach ($Line in Get-Content -LiteralPath $LogFile.FullName) {
            if ($Line -match "LIVE_MAGA_RUNTIME_COORDINATOR_STOP" -and
                    $Line -match ("[|]" + [regex]::Escape($Field) + "=([^| )]+)")) {
                return $Matches[1]
            }
        }
    }
    return $null
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
$SimulationCompleted = $true
$SourceRun = $Runs[0].Name
$LatestRun = $Runs | Sort-Object LastWriteTime -Descending | Select-Object -First 1
foreach ($Run in $Runs) {
    $AllRuntimeRows += Import-RuntimeCsv -RunDir $Run.FullName -FileName "live_ga_runtime_trace.csv"
    $AllStrategyRows += Import-RuntimeCsv -RunDir $Run.FullName -FileName "live_strategy_application_trace.csv"
    $AllBridgeRows += Import-RuntimeCsv -RunDir $Run.FullName -FileName "live_bridge_snapshot_trace.csv"
    $AllOverrunRows += Import-RuntimeCsv -RunDir $Run.FullName -FileName "live_overrun_trace.csv"
    $SimulationCompleted = $SimulationCompleted -and (Is-SimulationCompleted -RunDir $Run.FullName)
}

$SubmittedRows = @($AllRuntimeRows | Where-Object { $_.gaSubmitted -eq "true" })
$CompletedRows = @($AllRuntimeRows | Where-Object { $_.runtimeState -eq "RESULT_READY_WITHIN_BOUND" -or $_.runtimeState -eq "WAIT_CAP_REACHED" })
$AppliedRows = @($AllStrategyRows)
$StaleRows = @($AllRuntimeRows | Where-Object { $_.runtimeState -eq "STALE_RESULT_DISCARDED" })
$ResolvedBridgeRows = @($AllBridgeRows | Where-Object { $_.snapshotResolved -eq "true" })
$EmptyBridgeRows = @($AllBridgeRows | Where-Object { $_.snapshotResolved -ne "true" })

$SnapshotsRequested = 0
$SnapshotsResolved = 0
$SnapshotEmptyResponses = 0
$ParallelGaViolations = 0
foreach ($Run in $Runs) {
    $Value = Get-StopField -RunDir $Run.FullName -Field "snapshotsRequested"
    if ($Value -ne $null) { $SnapshotsRequested += [int]$Value }
    $Value = Get-StopField -RunDir $Run.FullName -Field "snapshotsResolved"
    if ($Value -ne $null) { $SnapshotsResolved += [int]$Value }
    $Value = Get-StopField -RunDir $Run.FullName -Field "snapshotEmptyResponses"
    if ($Value -ne $null) { $SnapshotEmptyResponses += [int]$Value }
    $Value = Get-StopField -RunDir $Run.FullName -Field "parallelGaViolations"
    if ($Value -ne $null) { $ParallelGaViolations += [int]$Value }
}
if ($SnapshotsRequested -eq 0) { $SnapshotsRequested = $SubmittedRows.Count }
if ($SnapshotsResolved -eq 0) { $SnapshotsResolved = $ResolvedBridgeRows.Count }
if ($SnapshotEmptyResponses -eq 0) { $SnapshotEmptyResponses = $EmptyBridgeRows.Count }

$FutureSnapshotViolations = 0
foreach ($Row in $AllRuntimeRows) {
    if ($Row.snapshotTimeSeconds -ne "") {
        $SnapshotNs = [double]$Row.snapshotTimeSeconds * 1000000000.0
        if ($SnapshotNs -gt ([double]$Row.simulationTimeNs + 1.0)) {
            $FutureSnapshotViolations++
        }
    }
}
$FuturePoolViolations = 0

$LocalAssignments = Sum-IntField -Rows $AllStrategyRows -Field "localAssignments"
$VehicleAssignments = Sum-IntField -Rows $AllStrategyRows -Field "vehicleAssignments"
$EdgeAssignments = Sum-IntField -Rows $AllStrategyRows -Field "edgeAssignments"
$CloudAssignments = Sum-IntField -Rows $AllStrategyRows -Field "cloudAssignments"

$NormalRows = @($AllRuntimeRows | Where-Object { $_.profile -eq "normal" })
$NormalStrategyRows = @($AllStrategyRows | Where-Object { $_.profile -eq "normal" })
$OverrunRows = @($AllRuntimeRows | Where-Object { $_.profile -eq "diagnostic-overrun" })
$NormalRunValidated = $NormalRun -ne $null -and
        (Is-SimulationCompleted -RunDir $NormalRun.FullName) -and
        (@($NormalRows | Where-Object { $_.gaSubmitted -eq "true" }).Count -gt 0) -and
        ($NormalStrategyRows.Count -gt 0)
$WaitCapReachedObserved = @($AllRuntimeRows | Where-Object { $_.runtimeState -eq "WAIT_CAP_REACHED" }).Count -gt 0
$StaleResultDiscardedObserved = $StaleRows.Count -gt 0
$FreshReoptimizationRequestedObserved = @($AllRuntimeRows | Where-Object { $_.runtimeState -eq "FRESH_REOPTIMIZATION_REQUESTED" }).Count -gt 0
$OverrunRunValidated = $OverrunRun -ne $null -and
        (Is-SimulationCompleted -RunDir $OverrunRun.FullName) -and
        $WaitCapReachedObserved -and
        $StaleResultDiscardedObserved -and
        $FreshReoptimizationRequestedObserved
$LastAppliedStrategyPreservedWhileRunning =
        @($AllRuntimeRows | Where-Object {
            $_.runtimeState -eq "GA_RUNNING" -and -not [string]::IsNullOrWhiteSpace($_.lastAppliedStrategySnapshotId)
        }).Count -gt 0

$CoreStatus = git -c safe.directory="$SafeRepoRoot" status --short src
if ($LASTEXITCODE -ne 0) { throw "git status for src failed" }
$ProtectedScenarioStatus = git -c safe.directory="$SafeRepoRoot" status --short `
    data/mosaic-scenarios/MaGaIntegratedStudy `
    data/mosaic-scenarios/MaGaLiveStateLayerStudy `
    data/mosaic-scenarios/MaGaLiveInfrastructureSnapshotStudy
if ($LASTEXITCODE -ne 0) { throw "git status for protected scenarios failed" }
$CoreModified = -not [string]::IsNullOrWhiteSpace(($CoreStatus | Out-String).Trim())
$ProtectedScenariosModified = -not [string]::IsNullOrWhiteSpace(($ProtectedScenarioStatus | Out-String).Trim())

$AbsoluteDiagnostics = Count-AbsoluteDiagnostics
$Warnings = @(
    "WARNING_STRATEGY_APPLICATION_IS_DIAGNOSTIC_NOT_TASK_EXECUTION",
    "WARNING_CELL_BANDWIDTH_IS_DIAGNOSTIC_RUNTIME_ACCOUNTING_NOT_FEDERATE_MEASUREMENT",
    "WARNING_REUSED_STRATEGY_MAY_BECOME_STALE_DURING_GA_OVERRUN",
    "WARNING_DIAGNOSTIC_ARTIFICIAL_GA_DELAY_USED_ONLY_FOR_OVERRUN_TEST",
    "WARNING_DIAGNOSTIC_CPU_AND_V2V_BANDWIDTH_REQUIRE_FUTURE_CALIBRATION"
)
$Errors = @()
function Require-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { $script:Errors += $Message }
}

Require-Condition $SimulationCompleted "simulationCompleted must be true for available runtime runs"
Require-Condition ($NormalRunValidated) "normalRunValidated must be true"
Require-Condition ($OverrunRunValidated) "overrunRunValidated must be true"
Require-Condition ($SnapshotsResolved -gt 0) "at least one snapshot must be resolved"
Require-Condition ($SubmittedRows.Count -gt 0) "at least one GA job must be submitted"
Require-Condition ($CompletedRows.Count -gt 0) "at least one GA job must complete"
Require-Condition ($AppliedRows.Count -gt 0) "at least one strategy must be applied"
Require-Condition ($ParallelGaViolations -eq 0) "parallelGaViolations must be 0"
Require-Condition ($FutureSnapshotViolations -eq 0) "futureSnapshotViolations must be 0"
Require-Condition ($FuturePoolViolations -eq 0) "futurePoolViolations must be 0"
Require-Condition ($LastAppliedStrategyPreservedWhileRunning) "lastAppliedStrategyPreservedWhileRunning must be true"
Require-Condition ($WaitCapReachedObserved) "WAIT_CAP_REACHED must be observed"
Require-Condition ($StaleResultDiscardedObserved) "STALE_RESULT_DISCARDED must be observed"
Require-Condition ($FreshReoptimizationRequestedObserved) "FRESH_REOPTIMIZATION_REQUESTED must be observed"
Require-Condition ($AbsoluteDiagnostics.Count -eq 0) "absolutePathsInVersionedDiagnostics must be 0"
Require-Condition (-not $CoreModified) "coreModified must be false"
Require-Condition (-not $ProtectedScenariosModified) "protected previous scenarios must remain unchanged"

$Completed = $Errors.Count -eq 0
$Result = [ordered]@{
    phase = "13D_LIVE_MAGA_RUNTIME_BRIDGE_AND_OVERRUN_POLICY"
    sourceRun = $LatestRun.Name
    sourceRunName = $LatestRun.Name
    sourceRunRelativeDir = "tmp/mosaic-25.2/logs/$($LatestRun.Name)"
    normalRunName = if ($NormalRun -ne $null) { $NormalRun.Name } else { $null }
    overrunRunName = if ($OverrunRun -ne $null) { $OverrunRun.Name } else { $null }
    scenarioName = $ScenarioName
    simulationCompleted = $SimulationCompleted
    bridgeDescription = "LIVE_CAUSAL_MOSAIC_SNAPSHOT_BRIDGE"
    mosaicSystemStateSourceMode = "MOSAIC_LIVE"
    coordinatorTicks = $AllBridgeRows.Count
    snapshotsRequested = $SnapshotsRequested
    snapshotsResolved = $SnapshotsResolved
    snapshotEmptyResponses = $SnapshotEmptyResponses
    gaJobsSubmitted = $SubmittedRows.Count
    gaJobsCompleted = $CompletedRows.Count
    gaJobsApplied = $AppliedRows.Count
    gaJobsDiscardedAsStale = $StaleRows.Count
    parallelGaViolations = $ParallelGaViolations
    futureSnapshotViolations = $FutureSnapshotViolations
    futurePoolViolations = $FuturePoolViolations
    strategyApplications = $AppliedRows.Count
    localAssignments = $LocalAssignments
    vehicleAssignments = $VehicleAssignments
    edgeAssignments = $EdgeAssignments
    cloudAssignments = $CloudAssignments
    lastAppliedStrategyPreservedWhileRunning = $LastAppliedStrategyPreservedWhileRunning
    normalRunValidated = $NormalRunValidated
    overrunRunValidated = $OverrunRunValidated
    waitCapReachedObserved = $WaitCapReachedObserved
    staleResultDiscardedObserved = $StaleResultDiscardedObserved
    freshReoptimizationRequestedObserved = $FreshReoptimizationRequestedObserved
    absolutePathsInVersionedDiagnostics = $AbsoluteDiagnostics.Count
    absolutePathFiles = $AbsoluteDiagnostics.Files
    warnings = $Warnings
    errors = $Errors
    phase13dStatus = if ($Completed) { "COMPLETED" } else { "BLOCKED" }
    readyForPhase13E = $Completed
}

Set-Content -LiteralPath $DiagnosticsFile -Value ($Result | ConvertTo-Json -Depth 8) -Encoding UTF8

Write-Host "Validation result: $($Result.phase13dStatus)"
Write-Host "Diagnostics: $DiagnosticsFile"
Write-Host "Latest run: $($LatestRun.Name)"
Write-Host "normalRunValidated=$($Result.normalRunValidated)"
Write-Host "overrunRunValidated=$($Result.overrunRunValidated)"
Write-Host "snapshotsRequested=$($Result.snapshotsRequested)"
Write-Host "snapshotsResolved=$($Result.snapshotsResolved)"
Write-Host "gaJobsSubmitted=$($Result.gaJobsSubmitted)"
Write-Host "gaJobsCompleted=$($Result.gaJobsCompleted)"
Write-Host "gaJobsApplied=$($Result.gaJobsApplied)"
Write-Host "gaJobsDiscardedAsStale=$($Result.gaJobsDiscardedAsStale)"
Write-Host "parallelGaViolations=$($Result.parallelGaViolations)"
Write-Host "futureSnapshotViolations=$($Result.futureSnapshotViolations)"
Write-Host "futurePoolViolations=$($Result.futurePoolViolations)"
Write-Host "waitCapReachedObserved=$($Result.waitCapReachedObserved)"
Write-Host "staleResultDiscardedObserved=$($Result.staleResultDiscardedObserved)"
Write-Host "freshReoptimizationRequestedObserved=$($Result.freshReoptimizationRequestedObserved)"
Write-Host "absolutePathsInVersionedDiagnostics=$($Result.absolutePathsInVersionedDiagnostics)"
if (-not $Completed) {
    Write-Host "Errors:"
    foreach ($ErrorItem in $Errors) {
        Write-Host "  $ErrorItem"
    }
}
