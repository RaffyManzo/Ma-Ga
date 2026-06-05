param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2"
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$LogsRoot = Join-Path $ResolvedMosaicRoot "logs"
$DiagnosticsDir = Join-Path $RepoRoot "data\mosaic-study\diagnostics"
$DiagnosticsFile = Join-Path $DiagnosticsDir "phase_13a_live_api_probe_validation.json"
$ProbeSourceRoot = Join-Path $ToolRoot "src"
$CanonicalScenario = Join-Path $RepoRoot "data\mosaic-scenarios\MaGaIntegratedStudy"
$SafeRepoRoot = $RepoRoot.Replace("\", "/")

if (-not (Test-Path -LiteralPath $LogsRoot -PathType Container)) {
    throw "MOSAIC logs root not found: $LogsRoot"
}
if (-not (Test-Path -LiteralPath $DiagnosticsDir -PathType Container)) {
    New-Item -ItemType Directory -Path $DiagnosticsDir | Out-Null
}

$LatestRun = Get-ChildItem -LiteralPath $LogsRoot -Directory |
    Where-Object { $_.Name -like "*-MaGaLiveBridgeProbe" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $LatestRun) {
    throw "No MaGaLiveBridgeProbe run found under $LogsRoot"
}

$LogFiles = Get-ChildItem -LiteralPath $LatestRun.FullName -Recurse -File -Filter "*.log"
$LogLines = foreach ($LogFile in $LogFiles) {
    Get-Content -LiteralPath $LogFile.FullName
}

$MarkerLines = $LogLines | Where-Object { $_ -match "LIVE_PROBE_" }
$CoordinatorStartLines = $MarkerLines | Where-Object { $_ -match "LIVE_PROBE_COORDINATOR_START" }
$CoordinatorTickLines = $MarkerLines | Where-Object { $_ -match "LIVE_PROBE_COORDINATOR_TICK" }
$CoordinatorStopLines = $MarkerLines | Where-Object { $_ -match "LIVE_PROBE_COORDINATOR_STOP" }
$VehicleStartLines = $MarkerLines | Where-Object { $_ -match "LIVE_PROBE_VEHICLE_START" }
$VehicleUpdateLines = $MarkerLines | Where-Object { $_ -match "LIVE_PROBE_VEHICLE_UPDATE" }
$VehicleStopLines = $MarkerLines | Where-Object { $_ -match "LIVE_PROBE_VEHICLE_STOP" }

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

$VehicleIdsObserved = @(Get-FieldValues -Lines $VehicleUpdateLines -Field "vehicleId" | Sort-Object -Unique)
$SimulationTimes = @(Get-FieldValues -Lines $MarkerLines -Field "simulationTime" | ForEach-Object { [Int64]$_ } | Sort-Object)
$CoordinatorTickTimes = @(Get-FieldValues -Lines $CoordinatorTickLines -Field "simulationTime" | ForEach-Object { [Int64]$_ })
$ProjectedPositionSamples = @($VehicleUpdateLines | Where-Object { $_ -match "\|projectedX=" -and $_ -match "\|projectedY=" }).Count
$InvalidProjectedPositions = @($VehicleUpdateLines | Where-Object { $_ -match "\|finiteProjectedPosition=false" }).Count
$SpeedSamples = @($VehicleUpdateLines | Where-Object { $_ -match "\|speed=" }).Count
$InvalidSpeedSamples = @($VehicleUpdateLines | Where-Object { $_ -match "\|finiteSpeed=false" }).Count
$AdHocStateSamples = @($VehicleUpdateLines | Where-Object { $_ -match "\|adHocEnabled=" }).Count

$SourceText = Get-ChildItem -LiteralPath $ProbeSourceRoot -Recurse -File -Filter "*.java" |
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

$Warnings = @()
$Errors = @()
if ($V2xTransmissionEvents -gt 0 -or $V2xReceptionEvents -gt 0) {
    $Warnings += "WARNING_V2X_OUTPUT_EVENTS_OBSERVED"
}

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
Require-Condition ($ProjectedPositionSamples -gt 0) "projectedPositionSamples must be > 0"
Require-Condition ($InvalidProjectedPositions -eq 0) "invalidProjectedPositions must be 0"
Require-Condition ($SpeedSamples -gt 0) "speedSamples must be > 0"
Require-Condition ($InvalidSpeedSamples -eq 0) "invalidSpeedSamples must be 0"
Require-Condition ($AdHocStateSamples -gt 0) "adHocStateSamples must be > 0"
Require-Condition ($ArtificialSendCalls -eq 0) "artificialV2xSendCallsInProbeSource must be 0"
Require-Condition (-not $CoreModified) "coreModified must be false"
Require-Condition (-not $CanonicalScenarioModified) "canonicalScenarioModified must be false"

$Completed = $Errors.Count -eq 0
$Result = [ordered]@{
    phase = "13A_LIVE_API_PROBE_AND_RUNTIME_SKELETON"
    sourceRun = $LatestRun.Name
    scenarioName = "MaGaLiveBridgeProbe"
    latestRunDir = $LatestRun.FullName
    simulationCompleted = $SimulationCompleted
    coordinatorStarts = $CoordinatorStartLines.Count
    coordinatorTicks = $CoordinatorTickLines.Count
    coordinatorStops = $CoordinatorStopLines.Count
    vehicleStarts = $VehicleStartLines.Count
    vehicleUpdates = $VehicleUpdateLines.Count
    vehicleStops = $VehicleStopLines.Count
    projectedPositionSamples = $ProjectedPositionSamples
    invalidProjectedPositions = $InvalidProjectedPositions
    speedSamples = $SpeedSamples
    invalidSpeedSamples = $InvalidSpeedSamples
    adHocStateSamples = $AdHocStateSamples
    artificialV2xSendCallsInProbeSource = $ArtificialSendCalls
    v2xTransmissionEventsInOutput = $V2xTransmissionEvents
    v2xReceptionEventsInOutput = $V2xReceptionEvents
    coreModified = $CoreModified
    canonicalScenarioModified = $CanonicalScenarioModified
    vehicleIdsObserved = $VehicleIdsObserved
    firstSimulationTime = if ($SimulationTimes.Count -gt 0) { $SimulationTimes[0] } else { $null }
    lastSimulationTime = if ($SimulationTimes.Count -gt 0) { $SimulationTimes[$SimulationTimes.Count - 1] } else { $null }
    coordinatorTickTimes = $CoordinatorTickTimes
    warnings = $Warnings
    errors = $Errors
    phase13aStatus = if ($Completed) { "COMPLETED" } else { "BLOCKED" }
    readyForPhase13B = $Completed
}

$Json = $Result | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $DiagnosticsFile -Value $Json -Encoding UTF8

Write-Host "Validation result: $($Result.phase13aStatus)"
Write-Host "Diagnostics: $DiagnosticsFile"
Write-Host "Run: $($LatestRun.FullName)"
Write-Host "coordinatorStarts=$($Result.coordinatorStarts)"
Write-Host "coordinatorTicks=$($Result.coordinatorTicks)"
Write-Host "coordinatorStops=$($Result.coordinatorStops)"
Write-Host "vehicleStarts=$($Result.vehicleStarts)"
Write-Host "vehicleUpdates=$($Result.vehicleUpdates)"
Write-Host "vehicleStops=$($Result.vehicleStops)"
Write-Host "projectedPositionSamples=$($Result.projectedPositionSamples)"
Write-Host "invalidProjectedPositions=$($Result.invalidProjectedPositions)"
Write-Host "speedSamples=$($Result.speedSamples)"
Write-Host "invalidSpeedSamples=$($Result.invalidSpeedSamples)"
Write-Host "adHocStateSamples=$($Result.adHocStateSamples)"
Write-Host "artificialV2xSendCallsInProbeSource=$($Result.artificialV2xSendCallsInProbeSource)"
if (-not $Completed) {
    Write-Host "Errors:"
    foreach ($ErrorItem in $Errors) {
        Write-Host "  $ErrorItem"
    }
    throw "Phase 13A validation failed"
}
