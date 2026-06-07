param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiveMagaRuntimeStudy",
    [string]$RunName = "",
    [switch]$PrintDetailedLiveReport
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)

function Assert-SafeScenarioName {
    param([string]$Name)
    if ([string]::IsNullOrWhiteSpace($Name)) {
        throw "ScenarioName must not be blank"
    }
    if ([IO.Path]::IsPathRooted($Name) -or
            $Name.Contains("..") -or
            $Name.Contains("\") -or
            $Name.Contains("/") -or
            -not ($Name -match "^[A-Za-z0-9_.-]+$")) {
        throw "Invalid ScenarioName: $Name"
    }
}

function Import-RuntimeCsv {
    param([string]$RuntimeDir, [string]$FileName)
    $Path = Join-Path $RuntimeDir $FileName
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return @()
    }
    return @(Import-Csv -LiteralPath $Path)
}

function To-Bool {
    param([object]$Value)
    if ($null -eq $Value) {
        return $false
    }
    return ([string]$Value).Equals("true", [System.StringComparison]::OrdinalIgnoreCase)
}

function Sum-IntField {
    param([array]$Rows, [string]$Field)
    $Total = 0
    foreach ($Row in $Rows) {
        if ($Row.$Field -ne $null -and $Row.$Field -ne "") {
            $Total += [int]$Row.$Field
        }
    }
    return $Total
}

function Max-IntField {
    param([array]$Rows, [string]$Field)
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

Assert-SafeScenarioName -Name $ScenarioName
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$LogsRoot = Join-Path $ResolvedMosaicRoot "logs"
if (-not (Test-Path -LiteralPath $LogsRoot -PathType Container)) {
    throw "MOSAIC logs root not found: $LogsRoot"
}

if ([string]::IsNullOrWhiteSpace($RunName)) {
    $Run = Get-ChildItem -LiteralPath $LogsRoot -Directory |
        Where-Object { $_.Name -like "*-$ScenarioName" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
} else {
    if ($RunName.Contains("..") -or $RunName.Contains("\") -or $RunName.Contains("/") -or [IO.Path]::IsPathRooted($RunName)) {
        throw "Invalid RunName: $RunName"
    }
    $Run = Get-Item -LiteralPath (Join-Path $LogsRoot $RunName)
}
if ($null -eq $Run) {
    throw "No run found for scenario $ScenarioName under $LogsRoot"
}

$RuntimeDir = Join-Path $Run.FullName "live-maga-runtime"
if (-not (Test-Path -LiteralPath $RuntimeDir -PathType Container)) {
    throw "Runtime trace directory not found: $RuntimeDir"
}

$RuntimeRows = Import-RuntimeCsv -RuntimeDir $RuntimeDir -FileName "live_ga_runtime_trace.csv"
$StrategyRows = Import-RuntimeCsv -RuntimeDir $RuntimeDir -FileName "live_strategy_application_trace.csv"
$BridgeRows = Import-RuntimeCsv -RuntimeDir $RuntimeDir -FileName "live_bridge_snapshot_trace.csv"
$OverrunRows = Import-RuntimeCsv -RuntimeDir $RuntimeDir -FileName "live_overrun_trace.csv"

$MosaicLog = Join-Path $Run.FullName "MOSAIC.log"
$SimulationCompleted = $false
if (Test-Path -LiteralPath $MosaicLog -PathType Leaf) {
    $Text = Get-Content -LiteralPath $MosaicLog -Raw
    $SimulationCompleted = ($Text -match "Simulation ended after" -and $Text -match "Simulation finished")
}

$Profile = if ($RuntimeRows.Count -gt 0) { $RuntimeRows[0].profile } else { $null }
$SubmittedRows = @($RuntimeRows | Where-Object { To-Bool $_.gaSubmitted })
$CompletedRows = @($RuntimeRows | Where-Object { $_.runtimeState -eq "RESULT_READY_WITHIN_BOUND" -or $_.runtimeState -eq "STALE_RESULT_DISCARDED" })
$AppliedRows = @($RuntimeRows | Where-Object { To-Bool $_.resultApplied })
$StaleRows = @($RuntimeRows | Where-Object { $_.runtimeState -eq "STALE_RESULT_DISCARDED" })
$ResolvedBridgeRows = @($BridgeRows | Where-Object { To-Bool $_.snapshotResolved })

$FutureSnapshotViolations = 0
foreach ($Row in @($RuntimeRows + $BridgeRows)) {
    if ($Row.PSObject.Properties.Name -contains "snapshotTimeSeconds" -and
            $Row.PSObject.Properties.Name -contains "simulationTimeNs" -and
            $Row.snapshotTimeSeconds -ne "" -and $Row.simulationTimeNs -ne "") {
        $SnapshotNs = [double]$Row.snapshotTimeSeconds * 1000000000.0
        if ($SnapshotNs -gt ([double]$Row.simulationTimeNs + 1.0)) {
            $FutureSnapshotViolations++
        }
    }
}

$ParallelGaViolations = 0
$LogFiles = Get-ChildItem -LiteralPath $Run.FullName -Recurse -File -Filter "*.log"
foreach ($Log in $LogFiles) {
    foreach ($Line in Get-Content -LiteralPath $Log.FullName) {
        if ($Line -match "LIVE_MAGA_RUNTIME_COORDINATOR_STOP" -and
                $Line -match "[|]parallelGaViolations=([^| )]+)") {
            $ParallelGaViolations += [int]$Matches[1]
        }
    }
}

$ReportFiles = [ordered]@{
    gaTrace = Join-Path $RuntimeDir "live_ga_runtime_trace.csv"
    strategyTrace = Join-Path $RuntimeDir "live_strategy_application_trace.csv"
    bridgeTrace = Join-Path $RuntimeDir "live_bridge_snapshot_trace.csv"
    overrunTrace = Join-Path $RuntimeDir "live_overrun_trace.csv"
    nativeDetailedReportTxt = Join-Path $RuntimeDir "live-reporting\live_detailed_execution_report.txt"
    nativeDetailedReportMarkdown = Join-Path $RuntimeDir "live-reporting\live_detailed_execution_report.md"
    nativeDetailedReportJson = Join-Path $RuntimeDir "live-reporting\live_detailed_execution_report.json"
    nativeGaJobEventsJsonl = Join-Path $RuntimeDir "live-reporting\live_ga_job_events.jsonl"
    nativeTemporalStepRecordsJsonl = Join-Path $RuntimeDir "live-reporting\live_temporal_step_records.jsonl"
    nativeAppliedWindowsCsv = Join-Path $RuntimeDir "live-reporting\live_applied_window_records.csv"
    nativeDiscardedWindowsCsv = Join-Path $RuntimeDir "live-reporting\live_discarded_window_records.csv"
}

$Warnings = @()
$Errors = @()
$DetailedReportTxt = $ReportFiles.nativeDetailedReportTxt
if (-not (Test-Path -LiteralPath $DetailedReportTxt -PathType Leaf)) {
    $Warnings += "native live detailed report TXT is not present"
}
if ($RuntimeRows.Count -eq 0) { $Errors += "live_ga_runtime_trace.csv is missing or empty" }
if ($BridgeRows.Count -eq 0) { $Warnings += "live_bridge_snapshot_trace.csv is missing or empty" }
if (-not $SimulationCompleted) { $Errors += "simulationCompleted is false" }

$Summary = [ordered]@{
    runName = $Run.Name
    scenarioName = $ScenarioName
    profile = $Profile
    simulationCompleted = $SimulationCompleted
    generatedAt = (Get-Date).ToString("o")
    reportDirectory = "tmp/mosaic-25.2/logs/$($Run.Name)/live-maga-runtime"
    snapshotRequests = $BridgeRows.Count
    snapshotResolved = $ResolvedBridgeRows.Count
    gaJobsSubmitted = $SubmittedRows.Count
    gaJobsCompleted = $CompletedRows.Count
    gaJobsApplied = $AppliedRows.Count
    gaJobsDiscardedAsStale = $StaleRows.Count
    strategyApplications = $StrategyRows.Count
    localAssignments = Sum-IntField -Rows $StrategyRows -Field "localAssignments"
    vehicleAssignments = Sum-IntField -Rows $StrategyRows -Field "vehicleAssignments"
    edgeAssignments = Sum-IntField -Rows $StrategyRows -Field "edgeAssignments"
    cloudAssignments = Sum-IntField -Rows $StrategyRows -Field "cloudAssignments"
    parallelGaViolations = $ParallelGaViolations
    futureSnapshotViolations = $FutureSnapshotViolations
    futurePoolViolations = [Math]::Max((Max-IntField -Rows $RuntimeRows -Field "futurePoolViolations"), (Max-IntField -Rows $BridgeRows -Field "futurePoolViolations"))
    invalidPoolBandwidthViolations = [Math]::Max((Max-IntField -Rows $RuntimeRows -Field "invalidPoolBandwidthViolations"), (Max-IntField -Rows $BridgeRows -Field "invalidPoolBandwidthViolations"))
    deltaTMaxMismatchViolations = @($RuntimeRows | Where-Object {
        $_.deltaTMaxMismatchSeconds -ne "" -and ([double]$_.deltaTMaxMismatchSeconds) -gt 1.0E-9
    }).Count
    reportFiles = $ReportFiles
    warnings = $Warnings
    errors = $Errors
}

$SummaryJson = Join-Path $RuntimeDir "live_run_summary.json"
$SummaryMarkdown = Join-Path $RuntimeDir "live_run_summary.md"
Set-Content -LiteralPath $SummaryJson -Value ($Summary | ConvertTo-Json -Depth 8) -Encoding UTF8

$Markdown = @"
# Live MA-GA Run Summary

- Run: $($Summary.runName)
- Scenario: $ScenarioName
- Profile: $Profile
- Simulation completed: $SimulationCompleted
- Snapshots resolved: $($Summary.snapshotResolved) / $($Summary.snapshotRequests)
- GA jobs submitted: $($Summary.gaJobsSubmitted)
- GA jobs completed: $($Summary.gaJobsCompleted)
- GA jobs applied: $($Summary.gaJobsApplied)
- GA jobs discarded as stale: $($Summary.gaJobsDiscardedAsStale)
- Strategy applications: $($Summary.strategyApplications)
- Assignments LOCAL/VEHICLE/EDGE/CLOUD: $($Summary.localAssignments) / $($Summary.vehicleAssignments) / $($Summary.edgeAssignments) / $($Summary.cloudAssignments)
- parallelGaViolations: $($Summary.parallelGaViolations)
- futureSnapshotViolations: $($Summary.futureSnapshotViolations)
- futurePoolViolations: $($Summary.futurePoolViolations)
- invalidPoolBandwidthViolations: $($Summary.invalidPoolBandwidthViolations)
- deltaTMaxMismatchViolations: $($Summary.deltaTMaxMismatchViolations)

## Reports

- GA trace: $($ReportFiles.gaTrace)
- Strategy trace: $($ReportFiles.strategyTrace)
- Bridge trace: $($ReportFiles.bridgeTrace)
- Overrun trace: $($ReportFiles.overrunTrace)
- Native detailed TXT: $($ReportFiles.nativeDetailedReportTxt)
- Native detailed Markdown: $($ReportFiles.nativeDetailedReportMarkdown)
- Native detailed JSON: $($ReportFiles.nativeDetailedReportJson)
- Native GA events JSONL: $($ReportFiles.nativeGaJobEventsJsonl)
- Native temporal step records JSONL: $($ReportFiles.nativeTemporalStepRecordsJsonl)
- Native applied windows CSV: $($ReportFiles.nativeAppliedWindowsCsv)
- Native discarded windows CSV: $($ReportFiles.nativeDiscardedWindowsCsv)

## Warnings

$(@($Warnings | ForEach-Object { "- $_" }) -join "`n")

## Errors

$(@($Errors | ForEach-Object { "- $_" }) -join "`n")
"@
Set-Content -LiteralPath $SummaryMarkdown -Value $Markdown -Encoding UTF8

Write-Host "Simulation completed: $SimulationCompleted"
Write-Host "Run directory: $($Run.FullName)"
Write-Host "Summary Markdown: $SummaryMarkdown"
Write-Host "Summary JSON: $SummaryJson"
Write-Host "Strategy trace: $($ReportFiles.strategyTrace)"
Write-Host "GA trace: $($ReportFiles.gaTrace)"
Write-Host "Bridge trace: $($ReportFiles.bridgeTrace)"
Write-Host "Overrun trace: $($ReportFiles.overrunTrace)"
Write-Host "Native detailed TXT: $($ReportFiles.nativeDetailedReportTxt)"
Write-Host "Native detailed Markdown: $($ReportFiles.nativeDetailedReportMarkdown)"
Write-Host "Native detailed JSON: $($ReportFiles.nativeDetailedReportJson)"
Write-Host "Native GA events JSONL: $($ReportFiles.nativeGaJobEventsJsonl)"
Write-Host "Native temporal step records JSONL: $($ReportFiles.nativeTemporalStepRecordsJsonl)"
Write-Host "Native applied windows CSV: $($ReportFiles.nativeAppliedWindowsCsv)"
Write-Host "Native discarded windows CSV: $($ReportFiles.nativeDiscardedWindowsCsv)"
if ($PrintDetailedLiveReport) {
    if (-not (Test-Path -LiteralPath $DetailedReportTxt -PathType Leaf)) {
        throw "Detailed live report TXT not found: $DetailedReportTxt"
    }
    Write-Host ""
    Write-Host "===== NATIVE LIVE DETAILED REPORT ====="
    Get-Content -LiteralPath $DetailedReportTxt
}
if ($Errors.Count -gt 0) {
    throw "Run summary contains errors"
}
