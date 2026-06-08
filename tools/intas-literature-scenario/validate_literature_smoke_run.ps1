param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiteratureBasedUrbanStudy",
    [string]$RunName = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)

function Assert-SafeName {
    param([string]$Name, [string]$Label)
    if ([string]::IsNullOrWhiteSpace($Name)) {
        throw "$Label must not be blank"
    }
    if ([IO.Path]::IsPathRooted($Name) -or
            $Name.Contains("..") -or
            $Name.Contains("\") -or
            $Name.Contains("/") -or
            -not ($Name -match "^[A-Za-z0-9_.-]+$")) {
        throw "Invalid ${Label}: $Name"
    }
}

function Resolve-MaybeRelative {
    param([string]$Path)
    if ([IO.Path]::IsPathRooted($Path)) {
        return (Resolve-Path -LiteralPath $Path).Path
    }
    return (Resolve-Path -LiteralPath (Join-Path $RepoRoot $Path)).Path
}

function Read-JsonFile {
    param([string]$Path)
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Get-IntOrZero {
    param([object]$Object, [string]$Name)
    if ($null -eq $Object) {
        return 0
    }
    if (-not ($Object.PSObject.Properties.Name -contains $Name)) {
        return 0
    }
    $Value = $Object.$Name
    if ($null -eq $Value -or $Value -eq "") {
        return 0
    }
    return [int]$Value
}

function Get-DoubleOrZero {
    param([object]$Object, [string]$Name)
    if ($null -eq $Object) {
        return 0.0
    }
    if (-not ($Object.PSObject.Properties.Name -contains $Name)) {
        return 0.0
    }
    $Value = $Object.$Name
    if ($null -eq $Value -or $Value -eq "") {
        return 0.0
    }
    return [double]$Value
}

function Get-StringOrDefault {
    param([object]$Object, [string]$Name, [string]$DefaultValue)
    if ($null -eq $Object) {
        return $DefaultValue
    }
    if (-not ($Object.PSObject.Properties.Name -contains $Name)) {
        return $DefaultValue
    }
    $Value = [string]$Object.$Name
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $DefaultValue
    }
    return $Value
}

function Count-Pattern {
    param([string[]]$Files, [string]$Pattern)
    $Count = 0
    foreach ($File in $Files) {
        $Matches = Select-String -LiteralPath $File -Pattern $Pattern -SimpleMatch -ErrorAction SilentlyContinue
        if ($Matches) {
            $Count += @($Matches).Count
        }
    }
    return $Count
}

Assert-SafeName -Name $ScenarioName -Label "ScenarioName"
$ResolvedMosaicRoot = Resolve-MaybeRelative -Path $MosaicRoot
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
    Assert-SafeName -Name $RunName -Label "RunName"
    $Run = Get-Item -LiteralPath (Join-Path $LogsRoot $RunName)
}
if ($null -eq $Run) {
    throw "No run found for scenario $ScenarioName under $LogsRoot"
}

$RuntimeDir = Join-Path $Run.FullName "live-maga-runtime"
$ReportingDir = Join-Path $RuntimeDir "live-reporting"
$SummaryPath = Join-Path $RuntimeDir "live_run_summary.json"
if (-not (Test-Path -LiteralPath $RuntimeDir -PathType Container)) {
    throw "Runtime directory not found: $RuntimeDir"
}
if (-not (Test-Path -LiteralPath $SummaryPath -PathType Leaf)) {
    throw "Run summary missing. Run summarize-run.ps1 before smoke validation: $SummaryPath"
}

$Summary = Read-JsonFile -Path $SummaryPath
$LogFiles = @(Get-ChildItem -LiteralPath $Run.FullName -Recurse -File -Filter "*.log" | ForEach-Object { $_.FullName })

$TasksGeneratedCumulative = Get-IntOrZero -Object $Summary -Name "tasksGeneratedCumulative"
$TasksActivatedCumulative = Get-IntOrZero -Object $Summary -Name "tasksActivatedCumulative"
$TasksRemovedAtDeadlineCumulative = Get-IntOrZero -Object $Summary -Name "tasksRemovedAtDeadlineCumulative"
$TasksPendingAtEnd = Get-IntOrZero -Object $Summary -Name "tasksPendingAtEnd"
$TasksPendingPeak = Get-IntOrZero -Object $Summary -Name "tasksPendingPeak"
$TaskCompletionModel = Get-StringOrDefault -Object $Summary -Name "taskCompletionModel" -DefaultValue "UNSPECIFIED"
$TaskCountersSource = Get-StringOrDefault -Object $Summary -Name "taskCountersSource" -DefaultValue "UNSPECIFIED"
$GaParameterScalingMode = Get-StringOrDefault -Object $Summary -Name "gaParameterScalingMode" -DefaultValue "UNSPECIFIED"
$GaRuntimeMeanSeconds = Get-DoubleOrZero -Object $Summary -Name "gaRuntimeMeanSeconds"
$GaRuntimeMedianSeconds = Get-DoubleOrZero -Object $Summary -Name "gaRuntimeMedianSeconds"
$GaRuntimeP95Seconds = Get-DoubleOrZero -Object $Summary -Name "gaRuntimeP95Seconds"
$GaRuntimeMaxSeconds = Get-DoubleOrZero -Object $Summary -Name "gaRuntimeMaxSeconds"
$StaleRatioPercent = Get-DoubleOrZero -Object $Summary -Name "staleRatioPercent"
$StaleSequenceCount = Get-IntOrZero -Object $Summary -Name "staleSequenceCount"
$LongestConsecutiveStaleSequence = Get-IntOrZero -Object $Summary -Name "longestConsecutiveStaleSequence"
$MaximumAbsoluteSnapshotLagSeconds = Get-DoubleOrZero -Object $Summary -Name "maximumAbsoluteSnapshotLagSeconds"
$NonZeroLagWindowCount = Get-IntOrZero -Object $Summary -Name "nonZeroLagWindowCount"
$LastAppliedStrategySimulationTimeSeconds = Get-DoubleOrZero -Object $Summary -Name "lastAppliedStrategySimulationTimeSeconds"
$SecondsWithoutAppliedStrategyAtEnd = Get-DoubleOrZero -Object $Summary -Name "secondsWithoutAppliedStrategyAtEnd"

if ($TasksGeneratedCumulative -le 0) {
    $TasksGeneratedCumulative = Get-IntOrZero -Object $Summary -Name "tasksGenerated"
}
if ($TasksActivatedCumulative -le 0) {
    $TasksActivatedCumulative = Get-IntOrZero -Object $Summary -Name "tasksActivated"
}

$TasksGenerated = $TasksGeneratedCumulative
$TasksActivated = $TasksActivatedCumulative

$DetailedJsonPath = Join-Path $ReportingDir "live_detailed_execution_report.json"
$DetailedJsonValid = $false
if (Test-Path -LiteralPath $DetailedJsonPath -PathType Leaf) {
    try {
        $null = Read-JsonFile -Path $DetailedJsonPath
        $DetailedJsonValid = $true
    } catch {
        $DetailedJsonValid = $false
    }
}

$RequiredArtifacts = [ordered]@{
    gaJobEventsJsonl = Join-Path $ReportingDir "live_ga_job_events.jsonl"
    temporalStepRecordsJsonl = Join-Path $ReportingDir "live_temporal_step_records.jsonl"
    detailedReportTxt = Join-Path $ReportingDir "live_detailed_execution_report.txt"
    detailedReportMarkdown = Join-Path $ReportingDir "live_detailed_execution_report.md"
    detailedReportJson = $DetailedJsonPath
}
$ArtifactStatus = [ordered]@{}
foreach ($Entry in $RequiredArtifacts.GetEnumerator()) {
    $Exists = Test-Path -LiteralPath $Entry.Value -PathType Leaf
    $NonEmpty = $Exists -and ((Get-Item -LiteralPath $Entry.Value).Length -gt 0)
    $ArtifactStatus[$Entry.Key] = [ordered]@{
        path = $Entry.Value
        exists = $Exists
        nonEmpty = $NonEmpty
    }
}

$Errors = @()
$Warnings = @()
$SimulationCompleted = [bool]$Summary.simulationCompleted
$ConfiguredCellProfileLoaded = (Count-Pattern -Files $LogFiles -Pattern "LIVE_STATE_CONFIGURED_CELL_PROFILE_LOADED") -gt 0
$NativeReportWritten = (Count-Pattern -Files $LogFiles -Pattern "LIVE_NATIVE_DETAILED_REPORT_WRITTEN") -gt 0
$SnapshotRequests = Get-IntOrZero -Object $Summary -Name "snapshotRequests"
$SnapshotResolved = Get-IntOrZero -Object $Summary -Name "snapshotResolved"
$GaJobsSubmitted = Get-IntOrZero -Object $Summary -Name "gaJobsSubmitted"
$GaJobsCompleted = Get-IntOrZero -Object $Summary -Name "gaJobsCompleted"
$StrategyApplications = Get-IntOrZero -Object $Summary -Name "strategyApplications"
$LocalAssignments = Get-IntOrZero -Object $Summary -Name "localAssignments"
$VehicleAssignments = Get-IntOrZero -Object $Summary -Name "vehicleAssignments"
$EdgeAssignments = Get-IntOrZero -Object $Summary -Name "edgeAssignments"
$CloudAssignments = Get-IntOrZero -Object $Summary -Name "cloudAssignments"
$ParallelGaViolations = Get-IntOrZero -Object $Summary -Name "parallelGaViolations"
$FutureSnapshotViolations = Get-IntOrZero -Object $Summary -Name "futureSnapshotViolations"
$FuturePoolViolations = Get-IntOrZero -Object $Summary -Name "futurePoolViolations"
$InvalidPoolBandwidthViolations = Get-IntOrZero -Object $Summary -Name "invalidPoolBandwidthViolations"
$DeltaTMaxMismatchViolations = Get-IntOrZero -Object $Summary -Name "deltaTMaxMismatchViolations"

if (-not $SimulationCompleted) { $Errors += "simulationCompleted is false" }
if (-not $ConfiguredCellProfileLoaded) { $Errors += "LIVE_STATE_CONFIGURED_CELL_PROFILE_LOADED not observed" }
if (-not $NativeReportWritten) { $Errors += "LIVE_NATIVE_DETAILED_REPORT_WRITTEN not observed" }
if ($TasksGeneratedCumulative -le 0) { $Errors += "tasksGeneratedCumulative <= 0" }
if ($TasksActivatedCumulative -le 0) { $Errors += "tasksActivatedCumulative <= 0" }
if ($TasksRemovedAtDeadlineCumulative -lt 0) { $Errors += "tasksRemovedAtDeadlineCumulative < 0" }
if ($TasksPendingAtEnd -lt 0) { $Errors += "tasksPendingAtEnd < 0" }
if ($TasksPendingPeak -lt 0) { $Errors += "tasksPendingPeak < 0" }
if ($TaskCompletionModel -ne "NOT_IMPLEMENTED") { $Errors += "taskCompletionModel must be NOT_IMPLEMENTED" }
if ($TaskCountersSource -ne "LIVE_MAGA_RUNTIME_COORDINATOR_TICK") { $Errors += "taskCountersSource must be LIVE_MAGA_RUNTIME_COORDINATOR_TICK" }
if ($GaParameterScalingMode -ne "STATIC") { $Errors += "gaParameterScalingMode must be STATIC" }
if ($MaximumAbsoluteSnapshotLagSeconds -gt 1.0E-9) { $Errors += "maximumAbsoluteSnapshotLagSeconds = $MaximumAbsoluteSnapshotLagSeconds" }
if ($NonZeroLagWindowCount -ne 0) { $Errors += "nonZeroLagWindowCount = $NonZeroLagWindowCount" }
if ($SnapshotRequests -le 0) { $Errors += "snapshotRequests <= 0" }
if ($SnapshotResolved -le 0) { $Errors += "snapshotResolved <= 0" }
if ($GaJobsSubmitted -le 0) { $Errors += "gaJobsSubmitted <= 0" }
if ($GaJobsCompleted -le 0) { $Errors += "gaJobsCompleted <= 0" }
if ($StrategyApplications -le 0) { $Errors += "strategyApplications <= 0" }
if ($ParallelGaViolations -ne 0) { $Errors += "parallelGaViolations = $ParallelGaViolations" }
if ($FutureSnapshotViolations -ne 0) { $Errors += "futureSnapshotViolations = $FutureSnapshotViolations" }
if ($FuturePoolViolations -ne 0) { $Errors += "futurePoolViolations = $FuturePoolViolations" }
if ($InvalidPoolBandwidthViolations -ne 0) { $Errors += "invalidPoolBandwidthViolations = $InvalidPoolBandwidthViolations" }
if ($DeltaTMaxMismatchViolations -ne 0) { $Errors += "deltaTMaxMismatchViolations = $DeltaTMaxMismatchViolations" }
foreach ($Entry in $ArtifactStatus.GetEnumerator()) {
    if (-not $Entry.Value.exists -or -not $Entry.Value.nonEmpty) {
        $Errors += "Required live reporting artifact missing or empty: $($Entry.Value.path)"
    }
}
if (-not $DetailedJsonValid) {
    $Errors += "Detailed live report JSON is not valid JSON"
}

$Status = if ($Errors.Count -eq 0) { "LITERATURE_SMOKE_TEST_PASSED" } else { "LITERATURE_SMOKE_TEST_FAILED" }
$Payload = [ordered]@{
    status = $Status
    runName = $Run.Name
    scenarioName = $ScenarioName
    simulationCompleted = $SimulationCompleted
    configuredCellProfileLoaded = $ConfiguredCellProfileLoaded
    nativeDetailedReportWritten = $NativeReportWritten
    taskCountersSource = $TaskCountersSource
    taskCompletionModel = $TaskCompletionModel
    gaParameterScalingMode = $GaParameterScalingMode
    gaRuntimeMeanSeconds = $GaRuntimeMeanSeconds
    gaRuntimeMedianSeconds = $GaRuntimeMedianSeconds
    gaRuntimeP95Seconds = $GaRuntimeP95Seconds
    gaRuntimeMaxSeconds = $GaRuntimeMaxSeconds
    staleRatioPercent = $StaleRatioPercent
    staleSequenceCount = $StaleSequenceCount
    longestConsecutiveStaleSequence = $LongestConsecutiveStaleSequence
    maximumAbsoluteSnapshotLagSeconds = $MaximumAbsoluteSnapshotLagSeconds
    nonZeroLagWindowCount = $NonZeroLagWindowCount
    lastAppliedStrategySimulationTimeSeconds = $LastAppliedStrategySimulationTimeSeconds
    secondsWithoutAppliedStrategyAtEnd = $SecondsWithoutAppliedStrategyAtEnd
    tasksGeneratedCumulative = $TasksGeneratedCumulative
    tasksActivatedCumulative = $TasksActivatedCumulative
    tasksRemovedAtDeadlineCumulative = $TasksRemovedAtDeadlineCumulative
    tasksPendingAtEnd = $TasksPendingAtEnd
    tasksPendingPeak = $TasksPendingPeak
    tasksGenerated = $TasksGenerated
    tasksActivated = $TasksActivated
    snapshotRequests = $SnapshotRequests
    snapshotResolved = $SnapshotResolved
    gaJobsSubmitted = $GaJobsSubmitted
    gaJobsCompleted = $GaJobsCompleted
    strategyApplications = $StrategyApplications
    localAssignments = $LocalAssignments
    vehicleAssignments = $VehicleAssignments
    edgeAssignments = $EdgeAssignments
    cloudAssignments = $CloudAssignments
    parallelGaViolations = $ParallelGaViolations
    futureSnapshotViolations = $FutureSnapshotViolations
    futurePoolViolations = $FuturePoolViolations
    invalidPoolBandwidthViolations = $InvalidPoolBandwidthViolations
    deltaTMaxMismatchViolations = $DeltaTMaxMismatchViolations
    artifacts = $ArtifactStatus
    detailedReportJsonValid = $DetailedJsonValid
    warnings = $Warnings
    errors = $Errors
}

$JsonOut = Join-Path $RuntimeDir "literature_smoke_validation.json"
$MarkdownOut = Join-Path $RuntimeDir "literature_smoke_validation.md"
Set-Content -LiteralPath $JsonOut -Value ($Payload | ConvertTo-Json -Depth 8) -Encoding UTF8
$ArtifactLines = @($RequiredArtifacts.GetEnumerator() | ForEach-Object { "- $($_.Key): $($_.Value)" }) -join "`n"
$ErrorLines = @($Errors | ForEach-Object { "- $_" }) -join "`n"
if ([string]::IsNullOrWhiteSpace($ErrorLines)) {
    $ErrorLines = "- none"
}
$Markdown = @"
# Literature Scenario Smoke Validation

- status: $Status
- run: $($Run.Name)
- scenario: $ScenarioName
- simulationCompleted: $SimulationCompleted
- taskCountersSource: $TaskCountersSource
- taskCompletionModel: $TaskCompletionModel
- gaParameterScalingMode: $GaParameterScalingMode
- gaRuntimeMeanSeconds: $GaRuntimeMeanSeconds
- gaRuntimeMedianSeconds: $GaRuntimeMedianSeconds
- gaRuntimeP95Seconds: $GaRuntimeP95Seconds
- gaRuntimeMaxSeconds: $GaRuntimeMaxSeconds
- staleRatioPercent: $StaleRatioPercent
- staleSequenceCount: $StaleSequenceCount
- longestConsecutiveStaleSequence: $LongestConsecutiveStaleSequence
- maximumAbsoluteSnapshotLagSeconds: $MaximumAbsoluteSnapshotLagSeconds
- nonZeroLagWindowCount: $NonZeroLagWindowCount
- lastAppliedStrategySimulationTimeSeconds: $LastAppliedStrategySimulationTimeSeconds
- secondsWithoutAppliedStrategyAtEnd: $SecondsWithoutAppliedStrategyAtEnd
- tasksGeneratedCumulative: $TasksGeneratedCumulative
- tasksActivatedCumulative: $TasksActivatedCumulative
- tasksRemovedAtDeadlineCumulative: $TasksRemovedAtDeadlineCumulative
- tasksPendingAtEnd: $TasksPendingAtEnd
- tasksPendingPeak: $TasksPendingPeak
- snapshots: $SnapshotResolved / $SnapshotRequests
- GA jobs completed/submitted: $GaJobsCompleted / $GaJobsSubmitted
- strategyApplications: $StrategyApplications
- assignments LOCAL/VEHICLE/EDGE/CLOUD: $LocalAssignments / $VehicleAssignments / $EdgeAssignments / $CloudAssignments
- violations parallel/futureSnapshot/futurePool/invalidPool/deltaTMaxMismatch: $ParallelGaViolations / $FutureSnapshotViolations / $FuturePoolViolations / $InvalidPoolBandwidthViolations / $DeltaTMaxMismatchViolations

## Task lifecycle note

Tasks removed at deadline are not reported as completed. The live prototype does not simulate task execution completion.

## Artifacts

$ArtifactLines

## Errors

$ErrorLines
"@
Set-Content -LiteralPath $MarkdownOut -Value $Markdown -Encoding UTF8

Write-Host $Status
Write-Host "Run: $($Run.Name)"
Write-Host "Tasks generated cumulative: $TasksGeneratedCumulative"
Write-Host "Tasks activated cumulative: $TasksActivatedCumulative"
Write-Host "Tasks removed at deadline cumulative: $TasksRemovedAtDeadlineCumulative"
Write-Host "Tasks pending at end: $TasksPendingAtEnd"
Write-Host "Tasks pending peak: $TasksPendingPeak"
Write-Host "Task completion model: $TaskCompletionModel"
Write-Host "GA parameter scaling mode: $GaParameterScalingMode"
Write-Host "GA stale ratio percent: $StaleRatioPercent"
Write-Host "GA runtime mean/median/P95/max seconds: $GaRuntimeMeanSeconds / $GaRuntimeMedianSeconds / $GaRuntimeP95Seconds / $GaRuntimeMaxSeconds"
Write-Host "Maximum absolute snapshot lag seconds: $MaximumAbsoluteSnapshotLagSeconds"
Write-Host "Validation JSON: $JsonOut"
Write-Host "Validation Markdown: $MarkdownOut"
if ($Errors.Count -gt 0) {
    throw "Literature smoke validation failed"
}
