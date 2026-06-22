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

function Resolve-MaybeRelativeToRepo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "Path must not be blank"
    }

    $Candidate = if ([IO.Path]::IsPathRooted($Path)) {
        $Path
    } else {
        Join-Path $RepoRoot $Path
    }

    return (Resolve-Path -LiteralPath $Candidate).Path
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

function Get-Percentile {
    param([double[]]$Values, [double]$Percentile)
    if ($Values.Count -eq 0) {
        return 0.0
    }
    $Sorted = @($Values | Sort-Object)
    $Index = [Math]::Ceiling(($Percentile / 100.0) * $Sorted.Count) - 1
    if ($Index -lt 0) {
        $Index = 0
    }
    if ($Index -ge $Sorted.Count) {
        $Index = $Sorted.Count - 1
    }
    return [double]$Sorted[$Index]
}

function Get-MaximumSimulationTimeSeconds {
    param([array]$Rows)
    $Maximum = 0.0
    foreach ($Row in $Rows) {
        if ($Row.PSObject.Properties.Name -contains "simulationTimeNs" -and
                $Row.simulationTimeNs -ne $null -and
                $Row.simulationTimeNs -ne "") {
            $Seconds = [double]$Row.simulationTimeNs / 1.0E9
            if ($Seconds -gt $Maximum) {
                $Maximum = $Seconds
            }
        }
    }
    return $Maximum
}

function Get-StaleSequenceStats {
    param([array]$AppliedRows, [array]$DiscardedRows)

    $TerminalRows = @(
        foreach ($Row in $AppliedRows) {
            [pscustomobject]@{
                JobId = [string]$Row.jobId
                Outcome = "APPLIED"
                SubmissionSeconds = [double]$Row.submissionSimulationTimeNs / 1.0E9
            }
        }
        foreach ($Row in $DiscardedRows) {
            [pscustomobject]@{
                JobId = [string]$Row.jobId
                Outcome = "STALE"
                SubmissionSeconds = [double]$Row.submissionSimulationTimeNs / 1.0E9
            }
        }
    ) | Sort-Object SubmissionSeconds, JobId

    $SequenceCount = 0
    $CurrentLength = 0
    $LongestLength = 0

    foreach ($Row in $TerminalRows) {
        if ($Row.Outcome -eq "STALE") {
            if ($CurrentLength -eq 0) {
                $SequenceCount++
            }
            $CurrentLength++
            if ($CurrentLength -gt $LongestLength) {
                $LongestLength = $CurrentLength
            }
        } else {
            $CurrentLength = 0
        }
    }

    return [pscustomobject]@{
        SequenceCount = $SequenceCount
        LongestConsecutiveSequence = $LongestLength
    }
}

Assert-SafeScenarioName -Name $ScenarioName
$ResolvedMosaicRoot = Resolve-MaybeRelativeToRepo -Path $MosaicRoot
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
$AppliedWindowRows = Import-RuntimeCsv -RuntimeDir $RuntimeDir -FileName "live-reporting\live_applied_window_records.csv"
$DiscardedWindowRows = Import-RuntimeCsv -RuntimeDir $RuntimeDir -FileName "live-reporting\live_discarded_window_records.csv"

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

$GaRuntimeValues = @(
    $CompletedRows |
        Where-Object {
            $_.gaRuntimeWallClockSeconds -ne $null -and
            $_.gaRuntimeWallClockSeconds -ne ""
        } |
        ForEach-Object {
            [double]$_.gaRuntimeWallClockSeconds
        }
)
$GaRuntimeMeanSeconds = if ($GaRuntimeValues.Count -eq 0) {
    0.0
} else {
    [double](($GaRuntimeValues | Measure-Object -Average).Average)
}
$GaRuntimeMedianSeconds = Get-Percentile -Values $GaRuntimeValues -Percentile 50
$GaRuntimeP95Seconds = Get-Percentile -Values $GaRuntimeValues -Percentile 95
$GaRuntimeMaxSeconds = if ($GaRuntimeValues.Count -eq 0) {
    0.0
} else {
    [double](($GaRuntimeValues | Measure-Object -Maximum).Maximum)
}
$StaleRatioPercent = if ($CompletedRows.Count -eq 0) {
    0.0
} else {
    100.0 * $StaleRows.Count / $CompletedRows.Count
}
$StaleSequenceStats = Get-StaleSequenceStats -AppliedRows $AppliedWindowRows -DiscardedRows $DiscardedWindowRows

$TerminalWindowRows = @($AppliedWindowRows) + @($DiscardedWindowRows)
$SnapshotLagValues = @(
    $TerminalWindowRows |
        Where-Object {
            $_.submissionSimulationTimeNs -ne $null -and
            $_.submissionSimulationTimeNs -ne "" -and
            $_.snapshotTimeSeconds -ne $null -and
            $_.snapshotTimeSeconds -ne ""
        } |
        ForEach-Object {
            ([double]$_.submissionSimulationTimeNs / 1.0E9) - [double]$_.snapshotTimeSeconds
        }
)
$MaximumAbsoluteSnapshotLagSeconds = if ($SnapshotLagValues.Count -eq 0) {
    0.0
} else {
    [double]((
        $SnapshotLagValues |
            ForEach-Object { [Math]::Abs($_) } |
            Measure-Object -Maximum
    ).Maximum)
}
$NonZeroLagWindowCount = @(
    $SnapshotLagValues |
        Where-Object { [Math]::Abs($_) -gt 1.0E-9 }
).Count

$SimulationEndSeconds = Get-MaximumSimulationTimeSeconds -Rows $BridgeRows
if ($SimulationEndSeconds -eq 0.0) {
    $SimulationEndSeconds = Get-MaximumSimulationTimeSeconds -Rows $RuntimeRows
}
$LastAppliedStrategySimulationTimeSeconds = if ($AppliedWindowRows.Count -eq 0) {
    $null
} else {
    [double]((
        $AppliedWindowRows |
            ForEach-Object { [double]$_.submissionSimulationTimeNs / 1.0E9 } |
            Measure-Object -Maximum
    ).Maximum)
}
$SecondsWithoutAppliedStrategyAtEnd = if ($null -eq $LastAppliedStrategySimulationTimeSeconds) {
    $null
} else {
    [Math]::Max(0.0, $SimulationEndSeconds - $LastAppliedStrategySimulationTimeSeconds)
}

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
$RuntimeTicksObserved = 0
$GaParameterScalingModesObserved = @()
$TasksGeneratedCumulative = 0
$TasksActivatedCumulative = 0
$TasksRemovedAtDeadlineCumulative = 0

$LogFiles = Get-ChildItem -LiteralPath $Run.FullName -Recurse -File -Filter "*.log"
foreach ($Log in $LogFiles) {
    foreach ($Line in Get-Content -LiteralPath $Log.FullName) {
        if ($Line -match "LIVE_MAGA_RUNTIME_COORDINATOR_STOP" -and
                $Line -match "[|]parallelGaViolations=([^| )]+)") {
            $ParallelGaViolations += [int]$Matches[1]
        }

        if ($Line -match "[|]gaParameterScalingMode=([^| )]+)") {
            $GaParameterScalingModesObserved += $Matches[1].Trim().ToUpperInvariant()
        }

        if ($Line -match "LIVE_MAGA_RUNTIME_COORDINATOR_TICK") {
            $RuntimeTicksObserved++

            if ($Line -match "[|]generatedTasks=([^| )]+)") {
                $TasksGeneratedCumulative += [int]$Matches[1]
            }
            if ($Line -match "[|]activatedTasks=([^| )]+)") {
                $TasksActivatedCumulative += [int]$Matches[1]
            }
            if ($Line -match "[|]expiredTasks=([^| )]+)") {
                $TasksRemovedAtDeadlineCumulative += [int]$Matches[1]
            }
        }
    }
}

$DistinctGaParameterScalingModes = @(
    $GaParameterScalingModesObserved |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Sort-Object -Unique
)
$GaParameterScalingMode = if ($DistinctGaParameterScalingModes.Count -eq 1) {
    $DistinctGaParameterScalingModes[0]
} elseif ($DistinctGaParameterScalingModes.Count -eq 0) {
    "UNSPECIFIED"
} else {
    "AMBIGUOUS:" + ($DistinctGaParameterScalingModes -join ",")
}

$TasksPendingAtEnd = $TasksActivatedCumulative - $TasksRemovedAtDeadlineCumulative
$TasksPendingPeak = Max-IntField -Rows $BridgeRows -Field "tasks"
$TaskCompletionModel = "NOT_IMPLEMENTED"
$TaskCountersSource = "LIVE_MAGA_RUNTIME_COORDINATOR_TICK"

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
if ($GaParameterScalingMode -notin @("STATIC", "ADAPTIVE")) {
    $Warnings += "gaParameterScalingMode could not be resolved unambiguously from runtime logs: $GaParameterScalingMode"
}
$DetailedReportTxt = $ReportFiles.nativeDetailedReportTxt
if (-not (Test-Path -LiteralPath $DetailedReportTxt -PathType Leaf)) {
    $Warnings += "native live detailed report TXT is not present"
}
if ($RuntimeRows.Count -eq 0) { $Errors += "live_ga_runtime_trace.csv is missing or empty" }
if ($BridgeRows.Count -eq 0) { $Warnings += "live_bridge_snapshot_trace.csv is missing or empty" }
if (-not $SimulationCompleted) { $Errors += "simulationCompleted is false" }
if ($RuntimeTicksObserved -eq 0) { $Warnings += "LIVE_MAGA_RUNTIME_COORDINATOR_TICK was not observed; cumulative task counters are unavailable" }
if ($TasksPendingAtEnd -lt 0) { $Errors += "tasksPendingAtEnd is negative" }

$Summary = [ordered]@{
    runName = $Run.Name
    scenarioName = $ScenarioName
    profile = $Profile
    gaParameterScalingMode = $GaParameterScalingMode
    simulationCompleted = $SimulationCompleted
    generatedAt = (Get-Date).ToString("o")
    reportDirectory = "tmp/mosaic-25.2/logs/$($Run.Name)/live-maga-runtime"
    runtimeTicksObserved = $RuntimeTicksObserved
    taskCountersSource = $TaskCountersSource
    taskCompletionModel = $TaskCompletionModel
    tasksGeneratedCumulative = $TasksGeneratedCumulative
    tasksActivatedCumulative = $TasksActivatedCumulative
    tasksRemovedAtDeadlineCumulative = $TasksRemovedAtDeadlineCumulative
    tasksPendingAtEnd = $TasksPendingAtEnd
    tasksPendingPeak = $TasksPendingPeak
    tasksGenerated = $TasksGeneratedCumulative
    tasksActivated = $TasksActivatedCumulative
    snapshotRequests = $BridgeRows.Count
    snapshotResolved = $ResolvedBridgeRows.Count
    gaJobsSubmitted = $SubmittedRows.Count
    gaJobsCompleted = $CompletedRows.Count
    gaJobsApplied = $AppliedRows.Count
    gaJobsDiscardedAsStale = $StaleRows.Count
    staleRatioPercent = [Math]::Round($StaleRatioPercent, 6)
    staleSequenceCount = $StaleSequenceStats.SequenceCount
    longestConsecutiveStaleSequence = $StaleSequenceStats.LongestConsecutiveSequence
    gaRuntimeMeanSeconds = [Math]::Round($GaRuntimeMeanSeconds, 9)
    gaRuntimeMedianSeconds = [Math]::Round($GaRuntimeMedianSeconds, 9)
    gaRuntimeP95Seconds = [Math]::Round($GaRuntimeP95Seconds, 9)
    gaRuntimeMaxSeconds = [Math]::Round($GaRuntimeMaxSeconds, 9)
    maximumAbsoluteSnapshotLagSeconds = [Math]::Round($MaximumAbsoluteSnapshotLagSeconds, 9)
    nonZeroLagWindowCount = $NonZeroLagWindowCount
    lastAppliedStrategySimulationTimeSeconds = $LastAppliedStrategySimulationTimeSeconds
    secondsWithoutAppliedStrategyAtEnd = $SecondsWithoutAppliedStrategyAtEnd
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
- GA parameter scaling mode: $($Summary.gaParameterScalingMode)
- Simulation completed: $SimulationCompleted
- Runtime ticks observed: $($Summary.runtimeTicksObserved)
- Task counters source: $($Summary.taskCountersSource)
- Task completion model: $($Summary.taskCompletionModel)
- Tasks generated cumulative: $($Summary.tasksGeneratedCumulative)
- Tasks activated cumulative: $($Summary.tasksActivatedCumulative)
- Tasks removed at deadline cumulative: $($Summary.tasksRemovedAtDeadlineCumulative)
- Tasks pending at end: $($Summary.tasksPendingAtEnd)
- Tasks pending peak: $($Summary.tasksPendingPeak)
- Snapshots resolved: $($Summary.snapshotResolved) / $($Summary.snapshotRequests)
- GA jobs submitted: $($Summary.gaJobsSubmitted)
- GA jobs completed: $($Summary.gaJobsCompleted)
- GA jobs applied: $($Summary.gaJobsApplied)
- GA jobs discarded as stale: $($Summary.gaJobsDiscardedAsStale)
- Stale ratio percent: $($Summary.staleRatioPercent)
- Stale sequences: $($Summary.staleSequenceCount)
- Longest consecutive stale sequence: $($Summary.longestConsecutiveStaleSequence)
- GA runtime mean seconds: $($Summary.gaRuntimeMeanSeconds)
- GA runtime median seconds: $($Summary.gaRuntimeMedianSeconds)
- GA runtime P95 seconds: $($Summary.gaRuntimeP95Seconds)
- GA runtime max seconds: $($Summary.gaRuntimeMaxSeconds)
- Maximum absolute snapshot lag seconds: $($Summary.maximumAbsoluteSnapshotLagSeconds)
- Non-zero lag window count: $($Summary.nonZeroLagWindowCount)
- Last applied strategy simulation time seconds: $($Summary.lastAppliedStrategySimulationTimeSeconds)
- Seconds without applied strategy at end: $($Summary.secondsWithoutAppliedStrategyAtEnd)
- Strategy applications: $($Summary.strategyApplications)
- Assignments LOCAL/VEHICLE/EDGE/CLOUD: $($Summary.localAssignments) / $($Summary.vehicleAssignments) / $($Summary.edgeAssignments) / $($Summary.cloudAssignments)
- parallelGaViolations: $($Summary.parallelGaViolations)
- futureSnapshotViolations: $($Summary.futureSnapshotViolations)
- futurePoolViolations: $($Summary.futurePoolViolations)
- invalidPoolBandwidthViolations: $($Summary.invalidPoolBandwidthViolations)
- deltaTMaxMismatchViolations: $($Summary.deltaTMaxMismatchViolations)

## Task lifecycle note

Tasks removed at deadline are not reported as completed. The live prototype does not simulate task execution completion.

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
Write-Host "Tasks generated cumulative: $TasksGeneratedCumulative"
Write-Host "Tasks activated cumulative: $TasksActivatedCumulative"
Write-Host "Tasks removed at deadline cumulative: $TasksRemovedAtDeadlineCumulative"
Write-Host "Tasks pending at end: $TasksPendingAtEnd"
Write-Host "Tasks pending peak: $TasksPendingPeak"
Write-Host "Task completion model: $TaskCompletionModel"
Write-Host "GA parameter scaling mode: $GaParameterScalingMode"
Write-Host "GA stale ratio percent: $([Math]::Round($StaleRatioPercent, 6))"
Write-Host "GA runtime mean/median/P95/max seconds: $([Math]::Round($GaRuntimeMeanSeconds, 9)) / $([Math]::Round($GaRuntimeMedianSeconds, 9)) / $([Math]::Round($GaRuntimeP95Seconds, 9)) / $([Math]::Round($GaRuntimeMaxSeconds, 9))"
Write-Host "Maximum absolute snapshot lag seconds: $([Math]::Round($MaximumAbsoluteSnapshotLagSeconds, 9))"
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
