param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2"
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
