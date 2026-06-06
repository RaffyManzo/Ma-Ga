param(
    [string]$RepoRoot = ".",
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",

    [Parameter(Mandatory = $true)]
    [string]$ScenarioName,

    [Parameter(Mandatory = $true)]
    [string]$SourceRun,

    [Parameter(Mandatory = $true)]
    [double]$SimulationStartSeconds,

    [Parameter(Mandatory = $true)]
    [double]$SimulationEndSeconds,

    [Parameter(Mandatory = $true)]
    [double]$WindowIntervalSeconds,

    [Parameter(Mandatory = $true)]
    [int]$SafetyMaxSteps,

    [switch]$CleanGeneratedOutputs,
    [switch]$VerifyDeterminism
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$PipelineVersion = "1.0"
$LogicalStagesExpected = 15
$PipelineStartedAt = Get-Date
$StageRecords = New-Object System.Collections.Generic.List[object]
$Warnings = New-Object System.Collections.Generic.List[string]
$Errors = New-Object System.Collections.Generic.List[string]
$DeterminismMismatches = @()
$DeterministicArtifactsCompared = 0
$DeterminismVerificationCompleted = $false
$StaleArtifactsDetected = $false
$StaleArtifacts = @()
$FailureDiagnosticsAllowed = $false
$PipelineCommandText = ""

function Get-FullPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$BasePath
    )
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $BasePath $Path))
}

function Test-IsUnderPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Root
    )
    $full = [System.IO.Path]::GetFullPath($Path).TrimEnd('\')
    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd('\')
    return $full.Equals($rootFull, [System.StringComparison]::OrdinalIgnoreCase) -or
        $full.StartsWith($rootFull + "\", [System.StringComparison]::OrdinalIgnoreCase)
}

function Convert-ToRepoRelative {
    param([Parameter(Mandatory = $true)][string]$Path)
    $full = [System.IO.Path]::GetFullPath($Path)
    $root = [System.IO.Path]::GetFullPath($script:RepoRootFull).TrimEnd('\') + "\"
    if ($full.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $full.Substring($root.Length)
    }
    return $full
}

function Ensure-Directory {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Force -Path $Path | Out-Null
    }
}

function Ensure-FileExists {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing required file: $Path"
    }
}

function Ensure-DirectoryExists {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "Missing required directory: $Path"
    }
}

function Get-JsonFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    Ensure-FileExists $Path
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Write-JsonFile {
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string]$Path
    )
    Ensure-Directory (Split-Path -Parent $Path)
    $json = $Object | ConvertTo-Json -Depth 40
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $json + [Environment]::NewLine, $utf8NoBom)
}

function Get-Sha256OrNull {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Count-CsvRows {
    param([Parameter(Mandatory = $true)][string]$Path)
    Ensure-FileExists $Path
    $count = 0
    Import-Csv -LiteralPath $Path | ForEach-Object { $count++ }
    return $count
}

function Count-CsvRowsWhere {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][scriptblock]$Predicate
    )
    Ensure-FileExists $Path
    $count = 0
    Import-Csv -LiteralPath $Path | ForEach-Object {
        if (& $Predicate $_) {
            $count++
        }
    }
    return $count
}

function Read-SourceRunFromJson {
    param([Parameter(Mandatory = $true)][string]$Path)
    $json = Get-JsonFile $Path
    if ($null -ne $json.sourceRun) {
        return [string]$json.sourceRun
    }
    if ($null -ne $json.source -and $null -ne $json.source.sourceRun) {
        return [string]$json.source.sourceRun
    }
    return $null
}

function Get-OutputWhitelist {
    $items = New-Object System.Collections.Generic.List[string]
    @(
        "task_stream.csv",
        "vehicle_state_stream.csv",
        "infrastructure_snapshot.json",
        "cell_handover_stream.csv",
        "cell_bandwidth_stream.csv",
        "access_link_preview.csv",
        "remote_candidate_preview.csv",
        "local_candidate_preview.csv",
        "v2v_candidate_preview.csv",
        "v2v_bandwidth_pool_preview.csv",
        "optimization_window_timeline.csv",
        "window_task_assignment.csv",
        "vehicle_state_stream_projected.csv",
        "infrastructure_snapshot_projected.json",
        "snapshot_manifest.csv",
        "json_time_full_horizon_trace.csv"
    ) | ForEach-Object { $items.Add((Join-Path $script:StudyDir $_)) }

    @(
        "phase_10g_validation.json",
        "phase_10h_validation.json",
        "phase_10i_pre_snapshot_contract_validation.json",
        "phase_10i_pre2_projection_validation.json",
        "phase_10i_validation.json",
        "phase_10j_pre_replay_bootstrap_validation.json",
        "phase_10j_pre2_optional_gateway_reporting_validation.json",
        "phase_10j_validation.json",
        "phase_11_offline_pipeline_manifest.json",
        "phase_11_artifact_manifest.csv",
        "phase_11_offline_pipeline_validation.json"
    ) | ForEach-Object { $items.Add((Join-Path $script:DiagnosticsDir $_)) }

    @(
        "integrated_baseline_metadata.json"
    ) | ForEach-Object { $items.Add((Join-Path $script:CellDiagnosticsDir $_)) }

    if (Test-Path -LiteralPath $script:SnapshotDir) {
        Get-ChildItem -LiteralPath $script:SnapshotDir -Filter "*.json" -File |
            ForEach-Object { $items.Add($_.FullName) }
    }
    if (Test-Path -LiteralPath $script:Phase11Dir) {
        Get-ChildItem -LiteralPath $script:Phase11Dir -Recurse -File |
            ForEach-Object { $items.Add($_.FullName) }
    }
    return @($items | Sort-Object -Unique)
}

function Test-GeneratedOutputsExist {
    return @(Get-OutputWhitelist | Where-Object { Test-Path -LiteralPath $_ }).Count -gt 0
}

function Remove-GeneratedOutputs {
    $targets = @(Get-OutputWhitelist | Where-Object { Test-Path -LiteralPath $_ })
    Write-Host "CleanGeneratedOutputs whitelist:"
    foreach ($target in $targets) {
        if (-not (Test-IsUnderPath -Path $target -Root $script:RepoRootFull)) {
            throw "Refusing to delete path outside repository: $target"
        }
        Write-Host ("  " + (Convert-ToRepoRelative $target))
    }
    foreach ($target in $targets) {
        if (Test-Path -LiteralPath $target -PathType Leaf) {
            $removed = $false
            for ($attempt = 1; $attempt -le 8 -and -not $removed; $attempt++) {
                try {
                    Remove-Item -LiteralPath $target -Force
                    $removed = $true
                } catch {
                    if ($attempt -eq 8) {
                        throw
                    }
                    Start-Sleep -Milliseconds (250 * $attempt)
                }
            }
        }
    }
    if (Test-Path -LiteralPath $script:Phase11Dir) {
        $remaining = @(Get-ChildItem -LiteralPath $script:Phase11Dir -Recurse -Force)
        if ($remaining.Count -eq 0) {
            Remove-Item -LiteralPath $script:Phase11Dir -Recurse -Force
        }
    }
}

function Invoke-PipelineStage {
    param(
        [Parameter(Mandatory = $true)][string]$PassName,
        [Parameter(Mandatory = $true)][string]$StageId,
        [Parameter(Mandatory = $true)][string]$StageName,
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )

    Ensure-Directory $script:LogsDir
    $safePass = $PassName.ToLowerInvariant()
    $safeStage = ($StageId + "_" + ($StageName -replace "[^A-Za-z0-9]+", "_")).Trim("_").ToLowerInvariant()
    $stdoutLog = Join-Path $script:LogsDir ($safePass + "_" + $safeStage + ".stdout.log")
    $stderrLog = Join-Path $script:LogsDir ($safePass + "_" + $safeStage + ".stderr.log")
    $started = Get-Date
    $exitCode = 0
    $commandText = ($Executable + " " + (($Arguments | ForEach-Object {
        if ($_ -match "\s") { '"' + $_ + '"' } else { $_ }
    }) -join " "))

    Write-Host ("[" + $PassName + "] " + $StageId + " " + $StageName)
    try {
        $process = Start-Process `
            -FilePath $Executable `
            -ArgumentList $Arguments `
            -WorkingDirectory $WorkingDirectory `
            -RedirectStandardOutput $stdoutLog `
            -RedirectStandardError $stderrLog `
            -NoNewWindow `
            -Wait `
            -PassThru
        $exitCode = [int]$process.ExitCode
    } catch {
        $_ | Out-File -LiteralPath $stderrLog -Append -Encoding UTF8
        $exitCode = 1
    }
    $completed = Get-Date
    $duration = ($completed - $started).TotalSeconds

    $record = [ordered]@{
        pass = $PassName
        stageId = $StageId
        stageName = $StageName
        command = $commandText
        workingDirectory = $WorkingDirectory
        startedAt = $started.ToString("o")
        completedAt = $completed.ToString("o")
        durationSeconds = [Math]::Round($duration, 3)
        exitCode = $exitCode
        stdoutLog = Convert-ToRepoRelative $stdoutLog
        stderrLog = Convert-ToRepoRelative $stderrLog
        status = if ($exitCode -eq 0) { "COMPLETED" } else { "FAILED" }
    }
    $script:StageRecords.Add([pscustomobject]$record)

    if ($exitCode -ne 0) {
        $stderrPreview = if (Test-Path -LiteralPath $stderrLog) {
            (Get-Content -LiteralPath $stderrLog -Tail 40) -join [Environment]::NewLine
        } else {
            ""
        }
        throw "Stage failed: $StageId $StageName exitCode=$exitCode`n$stderrPreview"
    }
}

function Assert-ValidationField {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Field,
        [Parameter(Mandatory = $true)]$Expected
    )
    $json = Get-JsonFile $Path
    $actual = $json.$Field
    if ($actual -ne $Expected) {
        throw "Validation mismatch in $(Convert-ToRepoRelative $Path): $Field expected '$Expected' actual '$actual'"
    }
}

function Write-SnapshotContractValidationIfMissing {
    param(
        [Parameter(Mandatory = $true)][string]$PassName
    )
    $outFile = Join-Path $script:DiagnosticsDir "phase_10i_pre_snapshot_contract_validation.json"
    if (Test-Path -LiteralPath $outFile) {
        return
    }
    $stdoutLog = Join-Path $script:LogsDir ($PassName.ToLowerInvariant() + "_10b_10i_pre_contract_run.stdout.log")
    Ensure-FileExists $stdoutLog
    $text = Get-Content -LiteralPath $stdoutLog -Raw
    $testsExecuted = if ($text -match "testsExecuted=(\d+)") { [int]$Matches[1] } else { 0 }
    $testsPassed = if ($text -match "testsPassed=(\d+)") { [int]$Matches[1] } else { 0 }
    $testsFailed = if ($text -match "testsFailed=(\d+)") { [int]$Matches[1] } else { 1 }
    $errors = @()
    if ($testsFailed -ne 0 -or $testsExecuted -eq 0) {
        $errors += "Snapshot contract validation harness did not report a clean pass."
    }
    $diagnostic = [ordered]@{
        sourceRun = $script:SourceRun
        phase = "10I_PRE_OPTIONAL_GATEWAY_ACCESS_CONTRACT_ALIGNMENT"
        previousContract = "EXACTLY_ONE_ACTIVE_ACCESS_LINK_PER_VEHICLE"
        updatedContract = "ZERO_OR_ONE_ACTIVE_ACCESS_LINK_PER_VEHICLE"
        activeLinkCardinalityPolicy = "ZERO_OR_ONE_ACTIVE_ACCESS_LINK_PER_VEHICLE"
        remoteCandidateGatewayPolicy = "ACTIVE_GATEWAY_REQUIRED_FOR_EDGE_AND_CLOUD"
        linkDynamicityMissingGatewayPolicy = "ZERO_QUALITY_WITHOUT_ACTIVE_ACCESS_LINK"
        coverageReferencePopulationPolicy = "ACTIVE_ACCESS_LINK_VEHICLES_ONLY"
        coverageReferenceNoActiveLinkFallback = "ZERO_REFERENCE_COVERAGE_AND_EXISTING_TEMPORAL_FALLBACK"
        classesInspected = @(
            "src/validation/snapshot/SnapshotValidator.java",
            "src/model/mobility/AccessLinkResolver.java",
            "src/model/mobility/AccessLinkMetricsEstimator.java",
            "src/window/dynamicity/calculator/LinkDynamicityCalculator.java",
            "src/window/timing/CoverageReferenceCalculator.java"
        )
        classesModified = @()
        testsExecuted = $testsExecuted
        testsPassed = $testsPassed
        testsFailed = $testsFailed
        placeholderGatewaysIntroduced = 0
        placeholderLinksIntroduced = 0
        placeholderPoolsIntroduced = 0
        compilationStatus = "COMPLETED"
        warnings = @()
        errors = $errors
        phase10iPreStatus = if ($errors.Count -eq 0) { "COMPLETED" } else { "FAILED" }
        readyForPhase10I = ($errors.Count -eq 0)
    }
    Write-JsonFile $diagnostic $outFile
}

function Write-ReplayBootstrapValidationIfMissing {
    $preOutFile = Join-Path $script:DiagnosticsDir "phase_10j_pre_replay_bootstrap_validation.json"
    if (Test-Path -LiteralPath $preOutFile) {
        return
    }
    $pre2File = Join-Path $script:DiagnosticsDir "phase_10j_pre2_optional_gateway_reporting_validation.json"
    $pre2 = Get-JsonFile $pre2File
    $errors = @()
    if ([int]$pre2.testsFailed -ne 0) {
        $errors += "Replay bootstrap validation reported failing tests."
    }
    if ([int]$pre2.futureLookAheadViolations -ne 0) {
        $errors += "Replay bootstrap validation reported future look-ahead violations."
    }
    $diagnostic = [ordered]@{
        sourceRun = $script:SourceRun
        phase = "10J_PRE_EMPTY_WINDOW_AND_REPLAY_START_ALIGNMENT"
        emptyTaskSnapshotPolicy = "ALLOW_EMPTY_CANDIDATES_WHEN_TASK_SET_IS_EMPTY"
        replayStartTimePolicy = "FIRST_AVAILABLE_SNAPSHOT_TIME"
        timeIndexedNoLookAheadPolicy = "LATEST_SNAPSHOT_AT_OR_BEFORE_REQUESTED_TIME"
        classesInspected = @(
            "src/ga/core/MaGaOptimizer.java",
            "src/app/AdaptiveWindowMain.java",
            "src/window/source/TimeIndexedSnapshotReplaySource.java",
            "src/window/source/SequentialSnapshotReplaySource.java"
        )
        classesModified = @()
        testsExecuted = [int]$pre2.testsExecuted
        testsPassed = [int]$pre2.testsPassed
        testsFailed = [int]$pre2.testsFailed
        emptySnapshotOptimizerStatus = [bool]$pre2.emptySnapshotOptimizerStatus
        nonEmptyTaskWithoutCandidatesRejected = [bool]$pre2.nonEmptyTaskWithoutCandidatesRejected
        timeIndexedBeforeFirstSnapshotReturnsEmpty = [bool]$pre2.timeIndexedBeforeFirstSnapshotReturnsEmpty
        timeIndexedExactFirstSnapshotResolved = [bool]$pre2.timeIndexedExactFirstSnapshotResolved
        sequentialEmptySnapshotPreserved = [bool]$pre2.sequentialEmptySnapshotPreserved
        jsonSequenceSmokeStepsExecuted = [int]$pre2.jsonSequenceWindowsExecuted
        jsonSequenceSmokeStatus = [string]$pre2.jsonSequenceSmokeStatus
        jsonTimeSmokeStepsExecuted = [int]$pre2.jsonTimeSmokeStepsExecuted
        jsonTimeSmokeStatus = [string]$pre2.jsonTimeSmokeStatus
        futureLookAheadViolations = [int]$pre2.futureLookAheadViolations
        warnings = @()
        errors = $errors
        phase10jPreStatus = if ($errors.Count -eq 0) { "COMPLETED" } else { "FAILED" }
        readyForPhase10J = ($errors.Count -eq 0)
    }
    Write-JsonFile $diagnostic $preOutFile
}

function Test-BaselineConsistency {
    $files = @(
        (Join-Path $script:CellDiagnosticsDir "integrated_baseline_metadata.json"),
        (Join-Path $script:DiagnosticsDir "phase_10g_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10h_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10i_pre_snapshot_contract_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10i_pre2_projection_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10i_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10j_pre_replay_bootstrap_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10j_pre2_optional_gateway_reporting_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10j_validation.json")
    )
    $records = @()
    foreach ($file in $files) {
        Ensure-FileExists $file
        $value = Read-SourceRunFromJson $file
        $ok = ($value -eq $script:SourceRun)
        $records += [pscustomobject]@{
            file = Convert-ToRepoRelative $file
            sourceRun = $value
            matches = $ok
        }
        if (-not $ok) {
            throw "Baseline mismatch in $(Convert-ToRepoRelative $file): expected '$script:SourceRun' actual '$value'"
        }
    }
    return $records
}

function Invoke-PipelineOnce {
    param([Parameter(Mandatory = $true)][string]$PassName)

    Ensure-Directory $script:StudyDir
    Ensure-Directory $script:DiagnosticsDir
    Ensure-Directory $script:CellDiagnosticsDir
    Ensure-Directory $script:SnapshotDir
    Ensure-Directory $script:LogsDir

    Invoke-PipelineStage $PassName "01" "10A_task_stream" "py" @(
        $script:ExportTaskStream,
        "--workload-log-root", $script:AppsDir,
        "--out-file", (Join-Path $script:StudyDir "task_stream.csv")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "02" "10B_vehicle_state" "py" @(
        $script:ExportVehicleState,
        "--input-file", $script:OutputCsv,
        "--out-file", (Join-Path $script:StudyDir "vehicle_state_stream.csv")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "03" "10C_infrastructure" "py" @(
        $script:ExportInfrastructure,
        "--output-csv", $script:OutputCsv,
        "--cell-config", (Join-Path $script:ScenarioDir "cell\cell_config.json"),
        "--network-config", (Join-Path $script:ScenarioDir "cell\network.json"),
        "--regions-config", (Join-Path $script:ScenarioDir "cell\regions.json"),
        "--sns-config", (Join-Path $script:ScenarioDir "sns\sns_config.json"),
        "--resource-catalog", (Join-Path $script:ScenarioDir "application\ma_ga_resource_catalog.json"),
        "--out-file", (Join-Path $script:StudyDir "infrastructure_snapshot.json")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "04" "10D_cell_network_streams" "py" @(
        $script:ExportCellStreams,
        "--output-csv", $script:OutputCsv,
        "--bandwidth-measurements-dir", $script:BandwidthMeasurementsDir,
        "--infrastructure-snapshot", (Join-Path $script:StudyDir "infrastructure_snapshot.json"),
        "--handover-out-file", (Join-Path $script:StudyDir "cell_handover_stream.csv"),
        "--bandwidth-out-file", (Join-Path $script:StudyDir "cell_bandwidth_stream.csv"),
        "--metadata-out-file", (Join-Path $script:CellDiagnosticsDir "integrated_baseline_metadata.json")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "05" "10E_access_link_preview" "py" @(
        $script:ExportAccessLinks,
        "--vehicle-state-file", (Join-Path $script:StudyDir "vehicle_state_stream.csv"),
        "--infrastructure-snapshot", (Join-Path $script:StudyDir "infrastructure_snapshot.json"),
        "--out-file", (Join-Path $script:StudyDir "access_link_preview.csv"),
        "--cell-handover-stream", (Join-Path $script:StudyDir "cell_handover_stream.csv")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "06" "10F_remote_candidates" "py" @(
        $script:ExportRemoteCandidates,
        "--access-link-file", (Join-Path $script:StudyDir "access_link_preview.csv"),
        "--infrastructure-snapshot", (Join-Path $script:StudyDir "infrastructure_snapshot.json"),
        "--cell-bandwidth-stream", (Join-Path $script:StudyDir "cell_bandwidth_stream.csv"),
        "--out-file", (Join-Path $script:StudyDir "remote_candidate_preview.csv")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "07" "10G_local_v2v_candidates" "py" @(
        $script:ExportLocalV2vCandidates,
        "--vehicle-state-file", (Join-Path $script:StudyDir "vehicle_state_stream.csv"),
        "--output-csv", $script:OutputCsv,
        "--resource-catalog", (Join-Path $script:ScenarioDir "application\ma_ga_resource_catalog.json"),
        "--sns-config", (Join-Path $script:ScenarioDir "sns\sns_config.json"),
        "--local-out-file", (Join-Path $script:StudyDir "local_candidate_preview.csv"),
        "--v2v-out-file", (Join-Path $script:StudyDir "v2v_candidate_preview.csv"),
        "--v2v-pool-out-file", (Join-Path $script:StudyDir "v2v_bandwidth_pool_preview.csv"),
        "--validation-out-file", (Join-Path $script:DiagnosticsDir "phase_10g_validation.json"),
        "--catalogs-updated",
        (Join-Path $script:ScenarioDir "application\ma_ga_resource_catalog.json")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "08" "10H_timeline" "py" @(
        $script:GenerateTimeline,
        "--simulation-start-seconds", ([string]$script:SimulationStartSeconds),
        "--simulation-end-seconds", ([string]$script:SimulationEndSeconds),
        "--window-interval-seconds", ([string]$script:WindowIntervalSeconds),
        "--output-file", (Join-Path $script:StudyDir "optimization_window_timeline.csv")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "09" "10H_task_assignment" "py" @(
        $script:ExportWindowTaskAssignment,
        "--task-stream-file", (Join-Path $script:StudyDir "task_stream.csv"),
        "--timeline-file", (Join-Path $script:StudyDir "optimization_window_timeline.csv"),
        "--baseline-metadata-file", (Join-Path $script:CellDiagnosticsDir "integrated_baseline_metadata.json"),
        "--expected-source-run", $script:SourceRun,
        "--output-file", (Join-Path $script:StudyDir "window_task_assignment.csv"),
        "--validation-out-file", (Join-Path $script:DiagnosticsDir "phase_10h_validation.json")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "10a" "10I_pre_contract_build" "powershell" @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $script:RepoRootFull "tools\snapshot-contract-validation\build.ps1")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "10b" "10I_pre_contract_run" "powershell" @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $script:RepoRootFull "tools\snapshot-contract-validation\run.ps1")
    ) $script:RepoRootFull
    Write-SnapshotContractValidationIfMissing $PassName

    Invoke-PipelineStage $PassName "11" "10I_pre2_projection" "py" @(
        $script:ExportProjectedCoordinates,
        "--vehicle-state-file", (Join-Path $script:StudyDir "vehicle_state_stream.csv"),
        "--infrastructure-file", (Join-Path $script:StudyDir "infrastructure_snapshot.json"),
        "--sumo-network-file", (Join-Path $script:ScenarioDir "sumo\Barnim.net.xml"),
        "--vehicle-state-out-file", (Join-Path $script:StudyDir "vehicle_state_stream_projected.csv"),
        "--infrastructure-out-file", (Join-Path $script:StudyDir "infrastructure_snapshot_projected.json"),
        "--validation-out-file", (Join-Path $script:DiagnosticsDir "phase_10i_pre2_projection_validation.json")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "12" "10I_system_snapshots" "py" @(
        $script:ExportSystemSnapshots,
        "--timeline-file", (Join-Path $script:StudyDir "optimization_window_timeline.csv"),
        "--window-task-assignment-file", (Join-Path $script:StudyDir "window_task_assignment.csv"),
        "--vehicle-state-file", (Join-Path $script:StudyDir "vehicle_state_stream_projected.csv"),
        "--infrastructure-file", (Join-Path $script:StudyDir "infrastructure_snapshot_projected.json"),
        "--cell-bandwidth-file", (Join-Path $script:StudyDir "cell_bandwidth_stream.csv"),
        "--access-link-file", (Join-Path $script:StudyDir "access_link_preview.csv"),
        "--remote-candidate-file", (Join-Path $script:StudyDir "remote_candidate_preview.csv"),
        "--local-candidate-file", (Join-Path $script:StudyDir "local_candidate_preview.csv"),
        "--v2v-candidate-file", (Join-Path $script:StudyDir "v2v_candidate_preview.csv"),
        "--v2v-pool-file", (Join-Path $script:StudyDir "v2v_bandwidth_pool_preview.csv"),
        "--baseline-metadata-file", (Join-Path $script:CellDiagnosticsDir "integrated_baseline_metadata.json"),
        "--phase-10g-validation-file", (Join-Path $script:DiagnosticsDir "phase_10g_validation.json"),
        "--phase-10h-validation-file", (Join-Path $script:DiagnosticsDir "phase_10h_validation.json"),
        "--phase-10i-pre-validation-file", (Join-Path $script:DiagnosticsDir "phase_10i_pre_snapshot_contract_validation.json"),
        "--phase-10i-pre2-validation-file", (Join-Path $script:DiagnosticsDir "phase_10i_pre2_projection_validation.json"),
        "--expected-source-run", $script:SourceRun,
        "--output-dir", $script:SnapshotDir,
        "--manifest-out-file", (Join-Path $script:StudyDir "snapshot_manifest.csv"),
        "--validation-out-file", (Join-Path $script:DiagnosticsDir "phase_10i_validation.json"),
        "--clean-output-dir"
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "13a" "10J_pre_bootstrap_build" "powershell" @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $script:RepoRootFull "tools\replay-bootstrap-validation\build.ps1")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "13b" "10J_pre_bootstrap_run" "powershell" @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $script:RepoRootFull "tools\replay-bootstrap-validation\run.ps1"),
        "-SnapshotFolder", $script:SnapshotDir,
        "-Phase10iValidationFile", (Join-Path $script:DiagnosticsDir "phase_10i_validation.json"),
        "-ValidationOutFile", (Join-Path $script:DiagnosticsDir "phase_10j_pre2_optional_gateway_reporting_validation.json")
    ) $script:RepoRootFull
    Write-ReplayBootstrapValidationIfMissing

    Invoke-PipelineStage $PassName "14a" "10J_full_horizon_build" "powershell" @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $script:RepoRootFull "tools\json-time-full-horizon-validation\build.ps1")
    ) $script:RepoRootFull

    Invoke-PipelineStage $PassName "14b" "10J_full_horizon_run" "powershell" @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $script:RepoRootFull "tools\json-time-full-horizon-validation\run.ps1"),
        "-SnapshotFolder", $script:SnapshotDir,
        "-SafetyMaxSteps", ([string]$script:SafetyMaxSteps),
        "-TraceOutFile", (Join-Path $script:StudyDir "json_time_full_horizon_trace.csv"),
        "-ValidationOutFile", (Join-Path $script:DiagnosticsDir "phase_10j_validation.json"),
        "-Phase10iValidationFile", (Join-Path $script:DiagnosticsDir "phase_10i_validation.json"),
        "-Phase10jPreValidationFile", (Join-Path $script:DiagnosticsDir "phase_10j_pre_replay_bootstrap_validation.json"),
        "-Phase10jPre2ValidationFile", (Join-Path $script:DiagnosticsDir "phase_10j_pre2_optional_gateway_reporting_validation.json")
    ) $script:RepoRootFull

    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10h_validation.json") "phase10hStatus" "COMPLETED"
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10h_validation.json") "readyForPhase10I" $true
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10i_pre_snapshot_contract_validation.json") "readyForPhase10I" $true
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10i_pre2_projection_validation.json") "phase10iPre2Status" "COMPLETED"
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10i_pre2_projection_validation.json") "readyForPhase10I" $true
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10i_validation.json") "phase10iStatus" "COMPLETED"
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10i_validation.json") "readyForPhase10J" $true
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10j_pre2_optional_gateway_reporting_validation.json") "phase10jPre2Status" "COMPLETED"
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10j_pre2_optional_gateway_reporting_validation.json") "readyForPhase10J" $true
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10j_validation.json") "phase10jStatus" "COMPLETED"
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10j_validation.json") "point10ReadyToClose" $true
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10j_validation.json") "stopReason" "FULL_TIME_HORIZON_REACHED"
    Assert-ValidationField (Join-Path $script:DiagnosticsDir "phase_10j_validation.json") "futureLookAheadViolations" 0

    return Test-BaselineConsistency
}

function Get-RequiredArtifactPaths {
    return @(
        (Join-Path $script:StudyDir "task_stream.csv"),
        (Join-Path $script:StudyDir "vehicle_state_stream.csv"),
        (Join-Path $script:StudyDir "infrastructure_snapshot.json"),
        (Join-Path $script:StudyDir "cell_handover_stream.csv"),
        (Join-Path $script:StudyDir "cell_bandwidth_stream.csv"),
        (Join-Path $script:StudyDir "access_link_preview.csv"),
        (Join-Path $script:StudyDir "remote_candidate_preview.csv"),
        (Join-Path $script:StudyDir "local_candidate_preview.csv"),
        (Join-Path $script:StudyDir "v2v_candidate_preview.csv"),
        (Join-Path $script:StudyDir "v2v_bandwidth_pool_preview.csv"),
        (Join-Path $script:StudyDir "optimization_window_timeline.csv"),
        (Join-Path $script:StudyDir "window_task_assignment.csv"),
        (Join-Path $script:StudyDir "vehicle_state_stream_projected.csv"),
        (Join-Path $script:StudyDir "infrastructure_snapshot_projected.json"),
        (Join-Path $script:StudyDir "snapshot_manifest.csv"),
        (Join-Path $script:StudyDir "json_time_full_horizon_trace.csv"),
        (Join-Path $script:CellDiagnosticsDir "integrated_baseline_metadata.json"),
        (Join-Path $script:DiagnosticsDir "phase_10g_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10h_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10i_pre_snapshot_contract_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10i_pre2_projection_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10i_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10j_pre2_optional_gateway_reporting_validation.json"),
        (Join-Path $script:DiagnosticsDir "phase_10j_validation.json")
    )
}

function Get-ArtifactDeterminismClass {
    param([Parameter(Mandatory = $true)][string]$Path)
    $relative = Convert-ToRepoRelative $Path
    if ($relative -like "data\mosaic-study\diagnostics\phase_11*") {
        return "RUNTIME_SENSITIVE_DIAGNOSTIC"
    }
    if ($relative -like "data\mosaic-study\diagnostics\phase_11\logs\*") {
        return "RUNTIME_SENSITIVE_DIAGNOSTIC"
    }
    if ($relative -like "*phase_10j*") {
        return "RUNTIME_SENSITIVE_DIAGNOSTIC"
    }
    if ($relative -like "*json_time_full_horizon_trace.csv") {
        return "RUNTIME_SENSITIVE_DIAGNOSTIC"
    }
    if ($relative -like "*stdout.log" -or $relative -like "*stderr.log") {
        return "RUNTIME_SENSITIVE_DIAGNOSTIC"
    }
    return "DETERMINISTIC_FROM_INPUTS"
}

function Get-ArtifactType {
    param([Parameter(Mandatory = $true)][string]$Path)
    $extension = [System.IO.Path]::GetExtension($Path).ToLowerInvariant()
    switch ($extension) {
        ".csv" { return "CSV" }
        ".json" { return "JSON" }
        ".log" { return "LOG" }
        default { return "FILE" }
    }
}

function Build-ArtifactManifestRows {
    $paths = New-Object System.Collections.Generic.List[string]
    foreach ($path in (Get-RequiredArtifactPaths)) {
        $paths.Add($path)
    }
    if (Test-Path -LiteralPath $script:SnapshotDir) {
        Get-ChildItem -LiteralPath $script:SnapshotDir -Filter "*.json" -File |
            ForEach-Object { $paths.Add($_.FullName) }
    }
    if (Test-Path -LiteralPath $script:LogsDir) {
        Get-ChildItem -LiteralPath $script:LogsDir -Recurse -File |
            ForEach-Object { $paths.Add($_.FullName) }
    }

    $rows = @()
    foreach ($path in @($paths | Sort-Object -Unique)) {
        $exists = Test-Path -LiteralPath $path -PathType Leaf
        $item = if ($exists) { Get-Item -LiteralPath $path } else { $null }
        $rows += [pscustomobject]@{
            artifactPath = Convert-ToRepoRelative $path
            artifactType = Get-ArtifactType $path
            stageId = ""
            exists = $exists
            sizeBytes = if ($exists) { $item.Length } else { 0 }
            sha256 = if ($exists) { Get-Sha256OrNull $path } else { "" }
            determinismClass = Get-ArtifactDeterminismClass $path
            sourceRun = $script:SourceRun
            lastWriteTimeUtc = if ($exists) { $item.LastWriteTimeUtc.ToString("o") } else { $null }
        }
    }
    return $rows
}

function Get-DeterministicArtifactHashes {
    $rows = Build-ArtifactManifestRows
    $map = @{}
    foreach ($row in $rows) {
        if ($row.exists -and $row.determinismClass -eq "DETERMINISTIC_FROM_INPUTS") {
            $map[$row.artifactPath] = $row.sha256
        }
    }
    return $map
}

function Compare-DeterministicHashes {
    param(
        [Parameter(Mandatory = $true)]$First,
        [Parameter(Mandatory = $true)]$Second
    )
    $keys = @($First.Keys + $Second.Keys | Sort-Object -Unique)
    $mismatches = @()
    foreach ($key in $keys) {
        $a = if ($First.ContainsKey($key)) { $First[$key] } else { $null }
        $b = if ($Second.ContainsKey($key)) { $Second[$key] } else { $null }
        if ($a -ne $b) {
            $mismatches += [pscustomobject]@{
                artifactPath = $key
                firstSha256 = $a
                secondSha256 = $b
            }
        }
    }
    return $mismatches
}

function Get-PipelineCounts {
    $infra = Get-JsonFile (Join-Path $script:StudyDir "infrastructure_snapshot.json")
    $phase10i = Get-JsonFile (Join-Path $script:DiagnosticsDir "phase_10i_validation.json")
    $phase10j = Get-JsonFile (Join-Path $script:DiagnosticsDir "phase_10j_validation.json")
    return [ordered]@{
        taskCount = Count-CsvRows (Join-Path $script:StudyDir "task_stream.csv")
        vehicleStateCount = Count-CsvRows (Join-Path $script:StudyDir "vehicle_state_stream.csv")
        gatewayCount = @($infra.gateways).Count
        gatewayPoolCount = @($infra.bandwidthPools | Where-Object { $_.poolType -eq "GATEWAY" }).Count
        executionNodeCount = @($infra.executionNodes).Count
        cellHandoverCount = Count-CsvRows (Join-Path $script:StudyDir "cell_handover_stream.csv")
        cellBandwidthRecordCount = Count-CsvRows (Join-Path $script:StudyDir "cell_bandwidth_stream.csv")
        activeAccessLinkPreviewCount = Count-CsvRowsWhere (Join-Path $script:StudyDir "access_link_preview.csv") { param($row) $row.active -eq "true" }
        remoteCandidateCount = Count-CsvRows (Join-Path $script:StudyDir "remote_candidate_preview.csv")
        localCandidateCount = Count-CsvRows (Join-Path $script:StudyDir "local_candidate_preview.csv")
        v2vCandidateCount = Count-CsvRows (Join-Path $script:StudyDir "v2v_candidate_preview.csv")
        v2vPoolCount = Count-CsvRows (Join-Path $script:StudyDir "v2v_bandwidth_pool_preview.csv")
        optimizationWindowCount = Count-CsvRows (Join-Path $script:StudyDir "optimization_window_timeline.csv")
        snapshotCount = @(Get-ChildItem -LiteralPath $script:SnapshotDir -Filter "*.json" -File).Count
        snapshotLoaderValidationFailures = [int]$phase10i.javaLoaderValidationFailures
        snapshotValidatorFailures = [int]$phase10i.javaValidatorFailures
        jsonTimeStopReason = [string]$phase10j.stopReason
        jsonTimeFinalSnapshotReached = [bool]$phase10j.fullTimeHorizonReached
        futureLookAheadViolations = [int]$phase10j.futureLookAheadViolations
    }
}

function Test-RequiredArtifacts {
    $missing = @()
    $empty = @()
    foreach ($path in (Get-RequiredArtifactPaths)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            $missing += (Convert-ToRepoRelative $path)
            continue
        }
        if ((Get-Item -LiteralPath $path).Length -eq 0) {
            $empty += (Convert-ToRepoRelative $path)
        }
    }
    $snapshotFiles = @(Get-ChildItem -LiteralPath $script:SnapshotDir -Filter "*.json" -File -ErrorAction SilentlyContinue)
    if ($snapshotFiles.Count -eq 0) {
        $missing += (Convert-ToRepoRelative (Join-Path $script:SnapshotDir "*.json"))
    }
    return [pscustomobject]@{
        missing = $missing
        empty = $empty
    }
}

function Test-StaleArtifacts {
    $stale = @()
    foreach ($path in (Get-RequiredArtifactPaths)) {
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            $item = Get-Item -LiteralPath $path
            if ($item.LastWriteTime -lt $script:EffectiveRunStart) {
                $stale += (Convert-ToRepoRelative $path)
            }
        }
    }
    return $stale
}

function Write-ArtifactCsv {
    param(
        [Parameter(Mandatory = $true)]$Rows,
        [Parameter(Mandatory = $true)][string]$Path
    )
    Ensure-Directory (Split-Path -Parent $Path)
    $Rows |
        Select-Object artifactPath, artifactType, stageId, exists, sizeBytes, sha256, determinismClass, sourceRun |
        Sort-Object artifactPath |
        Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
}

function Write-Phase11Outputs {
    param(
        [Parameter(Mandatory = $true)]$BaselineConsistency,
        [Parameter(Mandatory = $true)]$ArtifactRows,
        [Parameter(Mandatory = $true)]$ArtifactCheck,
        [Parameter(Mandatory = $true)]$Counts,
        [Parameter(Mandatory = $true)][bool]$PipelineSucceeded
    )

    $completedAt = Get-Date
    $durationSeconds = [Math]::Round(($completedAt - $PipelineStartedAt).TotalSeconds, 3)
    $phase11Status = if ($PipelineSucceeded -and $Errors.Count -eq 0) { "COMPLETED" } else { "FAILED" }
    $readyForPhase12 = ($phase11Status -eq "COMPLETED")

    $environment = [ordered]@{
        host = $env:COMPUTERNAME
        user = $env:USERNAME
        powershellVersion = $PSVersionTable.PSVersion.ToString()
        java = ((& cmd /c "java -version 2>&1") | Select-Object -First 1) -join ""
        python = ((& cmd /c "py --version 2>&1") | Select-Object -First 1) -join ""
    }

    $artifacts = @()
    foreach ($row in $ArtifactRows) {
        $artifacts += [ordered]@{
            path = $row.artifactPath
            exists = [bool]$row.exists
            sizeBytes = [int64]$row.sizeBytes
            sha256 = $row.sha256
            determinismClass = $row.determinismClass
        }
    }
    $stageArray = @($script:StageRecords.ToArray())
    $baselineArray = @($BaselineConsistency)
    $warningArray = @($Warnings.ToArray())
    $errorArray = @($Errors.ToArray())

    $manifest = [ordered]@{
        sourceRun = $SourceRun
        scenarioName = $ScenarioName
        pipelinePhase = "11_OFFLINE_PIPELINE_CONSOLIDATION"
        pipelineVersion = $PipelineVersion
        repoRoot = $RepoRootFull
        mosaicRoot = $MosaicRootFull
        sourceRunDir = $SourceRunDir
        scenarioDir = $ScenarioDir
        studyDir = $StudyDir
        snapshotDir = $SnapshotDir
        startedAt = $PipelineStartedAt.ToString("o")
        completedAt = $completedAt.ToString("o")
        durationSeconds = $durationSeconds
        parameters = [ordered]@{
            repoRoot = $RepoRoot
            mosaicRoot = $MosaicRoot
            scenarioName = $ScenarioName
            sourceRun = $SourceRun
            simulationStartSeconds = $SimulationStartSeconds
            simulationEndSeconds = $SimulationEndSeconds
            windowIntervalSeconds = $WindowIntervalSeconds
            safetyMaxSteps = $SafetyMaxSteps
            cleanGeneratedOutputs = [bool]$CleanGeneratedOutputs
            verifyDeterminism = [bool]$VerifyDeterminism
        }
        environment = $environment
        stages = $stageArray
        artifacts = $artifacts
        artifactCounts = $Counts
        baselineConsistency = $baselineArray
        staleArtifactsDetected = $StaleArtifactsDetected
        staleArtifacts = $StaleArtifacts
        warnings = $warningArray
        errors = $errorArray
        phase11Status = $phase11Status
        readyForPhase12 = $readyForPhase12
    }

    Write-JsonFile `
        -Object $manifest `
        -Path (Join-Path $DiagnosticsDir "phase_11_offline_pipeline_manifest.json")
    Write-ArtifactCsv `
        -Rows $ArtifactRows `
        -Path (Join-Path $DiagnosticsDir "phase_11_artifact_manifest.csv")

    $phase10j = Get-JsonFile (Join-Path $DiagnosticsDir "phase_10j_validation.json")
    $phase10jPre2 = Get-JsonFile (Join-Path $DiagnosticsDir "phase_10j_pre2_optional_gateway_reporting_validation.json")
    $validation = [ordered]@{
        sourceRun = $SourceRun
        scenarioName = $ScenarioName
        phase = "11_OFFLINE_PIPELINE_CONSOLIDATION"
        pipelineCommand = $script:PipelineCommandText
        stagesExpected = $LogicalStagesExpected
        stagesCompleted = @($StageRecords | Where-Object { $_.status -eq "COMPLETED" }).Count
        stagesFailed = @($StageRecords | Where-Object { $_.status -ne "COMPLETED" }).Count
        pipelineExitCode = if ($PipelineSucceeded) { 0 } else { 1 }
        baselineConsistencyValidated = @($BaselineConsistency | Where-Object { -not $_.matches }).Count -eq 0
        staleArtifactsDetected = $StaleArtifactsDetected
        staleArtifacts = $StaleArtifacts
        missingArtifacts = @($ArtifactCheck.missing)
        emptyArtifacts = @($ArtifactCheck.empty)
        determinismVerificationRequested = [bool]$VerifyDeterminism
        determinismVerificationCompleted = $DeterminismVerificationCompleted
        deterministicArtifactsCompared = $DeterministicArtifactsCompared
        deterministicArtifactMismatches = @($DeterminismMismatches)
        taskCount = $Counts.taskCount
        vehicleStateCount = $Counts.vehicleStateCount
        gatewayCount = $Counts.gatewayCount
        gatewayPoolCount = $Counts.gatewayPoolCount
        executionNodeCount = $Counts.executionNodeCount
        cellHandoverCount = $Counts.cellHandoverCount
        cellBandwidthRecordCount = $Counts.cellBandwidthRecordCount
        activeAccessLinkPreviewCount = $Counts.activeAccessLinkPreviewCount
        remoteCandidateCount = $Counts.remoteCandidateCount
        localCandidateCount = $Counts.localCandidateCount
        v2vCandidateCount = $Counts.v2vCandidateCount
        v2vPoolCount = $Counts.v2vPoolCount
        optimizationWindowCount = $Counts.optimizationWindowCount
        snapshotCount = $Counts.snapshotCount
        snapshotLoaderValidationFailures = $Counts.snapshotLoaderValidationFailures
        snapshotValidatorFailures = $Counts.snapshotValidatorFailures
        jsonSequenceValidationStatus = if ([int]$phase10jPre2.jsonSequenceReplayExitCode -eq 0) { "COMPLETED" } else { "FAILED" }
        jsonTimeFullHorizonValidationStatus = [string]$phase10j.jsonTimeFullHorizonValidationStatus
        jsonTimeStopReason = $Counts.jsonTimeStopReason
        jsonTimeFinalSnapshotReached = $Counts.jsonTimeFinalSnapshotReached
        futureLookAheadViolations = $Counts.futureLookAheadViolations
        warningDiagnosticBaselineNotStressingOffloading = $true
        warningAllDecisionsLocal = [bool]$phase10j.allLocalDecisionsObserved
        warningFullOffloadingNotObserved = -not [bool]$phase10j.fullOffloadingObserved
        warnings = $warningArray
        errors = $errorArray
        phase11Status = $phase11Status
        readyForPhase12 = $readyForPhase12
    }

    Write-JsonFile `
        -Object $validation `
        -Path (Join-Path $DiagnosticsDir "phase_11_offline_pipeline_validation.json")
}

try {
    $script:RepoRootFull = (Resolve-Path -LiteralPath (Get-FullPath $RepoRoot (Get-Location).ProviderPath)).ProviderPath
    $script:MosaicRootFull = Get-FullPath $MosaicRoot $RepoRootFull
    $script:SourceRun = $SourceRun
    $script:SimulationStartSeconds = $SimulationStartSeconds
    $script:SimulationEndSeconds = $SimulationEndSeconds
    $script:WindowIntervalSeconds = $WindowIntervalSeconds
    $script:SafetyMaxSteps = $SafetyMaxSteps
    $script:PipelineCommandText = @(
        "powershell -NoProfile -ExecutionPolicy Bypass",
        "-File `"$(Convert-ToRepoRelative $PSCommandPath)`"",
        "-RepoRoot `"$RepoRoot`"",
        "-MosaicRoot `"$MosaicRoot`"",
        "-ScenarioName `"$ScenarioName`"",
        "-SourceRun `"$SourceRun`"",
        "-SimulationStartSeconds $SimulationStartSeconds",
        "-SimulationEndSeconds $SimulationEndSeconds",
        "-WindowIntervalSeconds $WindowIntervalSeconds",
        "-SafetyMaxSteps $SafetyMaxSteps",
        $(if ($CleanGeneratedOutputs) { "-CleanGeneratedOutputs" } else { "" }),
        $(if ($VerifyDeterminism) { "-VerifyDeterminism" } else { "" })
    ) -join " "
    $script:SourceRunDir = Join-Path $MosaicRootFull ("logs\" + $SourceRun)
    $script:ScenarioDir = Join-Path $RepoRootFull ("data\mosaic-scenarios\" + $ScenarioName)
    $script:StudyDir = Join-Path $RepoRootFull "data\mosaic-study"
    $script:DiagnosticsDir = Join-Path $StudyDir "diagnostics"
    $script:CellDiagnosticsDir = Join-Path $DiagnosticsDir "cell"
    $script:SnapshotDir = Join-Path $RepoRootFull "data\snapshots\mosaic-generated"
    $script:Phase11Dir = Join-Path $DiagnosticsDir "phase_11"
    $script:LogsDir = Join-Path $Phase11Dir "logs"
    $script:OutputCsv = Join-Path $SourceRunDir "output.csv"
    $script:AppsDir = Join-Path $SourceRunDir "apps"
    $script:BandwidthMeasurementsDir = Join-Path $SourceRunDir "bandwidthMeasurements"

    $toolRoot = Join-Path $RepoRootFull "tools\mosaic-offline-exporter"
    $script:ExportTaskStream = Join-Path $toolRoot "export_task_stream.py"
    $script:ExportVehicleState = Join-Path $toolRoot "export_vehicle_state_stream.py"
    $script:ExportInfrastructure = Join-Path $toolRoot "export_infrastructure_snapshot.py"
    $script:ExportCellStreams = Join-Path $toolRoot "export_cell_network_streams.py"
    $script:ExportAccessLinks = Join-Path $toolRoot "export_access_link_preview.py"
    $script:ExportRemoteCandidates = Join-Path $toolRoot "export_remote_candidate_preview.py"
    $script:ExportLocalV2vCandidates = Join-Path $toolRoot "export_local_and_v2v_candidate_preview.py"
    $script:GenerateTimeline = Join-Path $toolRoot "generate_fixed_optimization_window_timeline.py"
    $script:ExportWindowTaskAssignment = Join-Path $toolRoot "export_window_task_assignment.py"
    $script:ExportProjectedCoordinates = Join-Path $toolRoot "export_projected_mosaic_coordinates.py"
    $script:ExportSystemSnapshots = Join-Path $toolRoot "export_system_snapshots.py"

    Write-Host "Phase 11 offline pipeline preflight"
    Write-Host "repoRoot=$RepoRootFull"
    Write-Host "mosaicRoot=$MosaicRootFull"
    Write-Host "sourceRun=$SourceRun"
    Write-Host "scenarioName=$ScenarioName"

    $gitSafeDirectory = $RepoRootFull -replace "\\", "/"
    $branch = (& git -c safe.directory="$gitSafeDirectory" branch --show-current)
    if ($null -eq $branch) {
        throw "Unable to read current Git branch."
    }
    $branch = $branch.Trim()
    if ($branch -ne "MOSAIC/SUMO-integration") {
        throw "Wrong branch: expected MOSAIC/SUMO-integration actual $branch"
    }
    $gitStatus = (& git -c safe.directory="$gitSafeDirectory" status --short)
    Write-Host "branch=$branch"
    Write-Host "gitStatusLines=$(@($gitStatus).Count)"
    & py --version
    & powershell -NoProfile -Command '$PSVersionTable.PSVersion.ToString()'
    & java -version
    & javac -version

    Ensure-DirectoryExists $MosaicRootFull
    Ensure-DirectoryExists $SourceRunDir
    Ensure-DirectoryExists $ScenarioDir
    Ensure-FileExists $OutputCsv
    Ensure-DirectoryExists $AppsDir
    Ensure-DirectoryExists $BandwidthMeasurementsDir
    Ensure-FileExists (Join-Path $ScenarioDir "application\ma_ga_resource_catalog.json")
    Ensure-FileExists (Join-Path $ScenarioDir "sns\sns_config.json")
    Ensure-FileExists (Join-Path $ScenarioDir "sumo\Barnim.net.xml")
    Ensure-FileExists (Join-Path $ScenarioDir "cell\cell_config.json")
    Ensure-FileExists (Join-Path $ScenarioDir "cell\network.json")
    Ensure-FileExists (Join-Path $ScenarioDir "cell\regions.json")
    foreach ($scriptFile in @(
        $ExportTaskStream, $ExportVehicleState, $ExportInfrastructure, $ExportCellStreams,
        $ExportAccessLinks, $ExportRemoteCandidates, $ExportLocalV2vCandidates,
        $GenerateTimeline, $ExportWindowTaskAssignment, $ExportProjectedCoordinates,
        $ExportSystemSnapshots
    )) {
        Ensure-FileExists $scriptFile
    }

    if ((Test-GeneratedOutputsExist) -and -not $CleanGeneratedOutputs) {
        $existing = @(Get-OutputWhitelist | Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object { Convert-ToRepoRelative $_ })
        Write-Host "Existing generated outputs detected:"
        $existing | ForEach-Object { Write-Host ("  " + $_) }
        throw "Generated outputs already exist. Re-run with -CleanGeneratedOutputs to regenerate them safely."
    }

    if ($CleanGeneratedOutputs) {
        Remove-GeneratedOutputs
        $script:FailureDiagnosticsAllowed = $true
    }

    $script:EffectiveRunStart = Get-Date

    if ($VerifyDeterminism) {
        Invoke-PipelineOnce "determinism_a" | Out-Null
        $firstHashes = Get-DeterministicArtifactHashes
        Remove-GeneratedOutputs
        $script:EffectiveRunStart = Get-Date
        $baselineConsistency = Invoke-PipelineOnce "determinism_b"
        $secondHashes = Get-DeterministicArtifactHashes
        $DeterminismMismatches = @(Compare-DeterministicHashes $firstHashes $secondHashes)
        $DeterministicArtifactsCompared = @($firstHashes.Keys + $secondHashes.Keys | Sort-Object -Unique).Count
        $DeterminismVerificationCompleted = $true
        if ($DeterminismMismatches.Count -gt 0) {
            throw "Deterministic artifact mismatches detected: $($DeterminismMismatches.Count)"
        }
    } else {
        $baselineConsistency = Invoke-PipelineOnce "main"
    }

    $artifactCheck = Test-RequiredArtifacts
    if ($artifactCheck.missing.Count -gt 0) {
        throw "Missing required artifacts: $($artifactCheck.missing -join ', ')"
    }
    if ($artifactCheck.empty.Count -gt 0) {
        throw "Empty required artifacts: $($artifactCheck.empty -join ', ')"
    }

    $StaleArtifacts = @(Test-StaleArtifacts)
    $StaleArtifactsDetected = $StaleArtifacts.Count -gt 0
    if ($StaleArtifactsDetected) {
        throw "Stale artifacts detected: $($StaleArtifacts -join ', ')"
    }

    $counts = Get-PipelineCounts
    $artifactRows = Build-ArtifactManifestRows
    Write-Phase11Outputs `
        -BaselineConsistency $baselineConsistency `
        -ArtifactRows $artifactRows `
        -ArtifactCheck $artifactCheck `
        -Counts $counts `
        -PipelineSucceeded $true

    Write-Host "Phase 11 offline pipeline completed"
    Write-Host "sourceRun=$SourceRun"
    Write-Host "scenarioName=$ScenarioName"
    Write-Host "stagesCompleted=$(@($StageRecords | Where-Object { $_.status -eq 'COMPLETED' }).Count)"
    Write-Host "taskCount=$($counts.taskCount)"
    Write-Host "vehicleStateCount=$($counts.vehicleStateCount)"
    Write-Host "snapshotCount=$($counts.snapshotCount)"
    Write-Host "jsonTimeStopReason=$($counts.jsonTimeStopReason)"
    Write-Host "futureLookAheadViolations=$($counts.futureLookAheadViolations)"
    Write-Host "determinismVerificationRequested=$([bool]$VerifyDeterminism)"
    Write-Host "deterministicArtifactsCompared=$DeterministicArtifactsCompared"
    Write-Host "deterministicArtifactMismatches=$($DeterminismMismatches.Count)"
    Write-Host "phase11Status=COMPLETED"
    Write-Host "readyForPhase12=true"
    exit 0
} catch {
    $failureMessage = if ($null -ne $_.Exception -and -not [string]::IsNullOrWhiteSpace($_.Exception.Message)) {
        $_.Exception.Message
    } else {
        [string]$_
    }
    $Errors.Add($failureMessage)
    Write-Error -Message $failureMessage
    try {
        $baselineConsistency = @()
        $artifactRows = if (Test-Path -LiteralPath $script:RepoRootFull) { Build-ArtifactManifestRows } else { @() }
        $artifactCheck = [pscustomobject]@{ missing = @(); empty = @() }
        $counts = [ordered]@{
            taskCount = 0; vehicleStateCount = 0; gatewayCount = 0; gatewayPoolCount = 0
            executionNodeCount = 0; cellHandoverCount = 0; cellBandwidthRecordCount = 0
            activeAccessLinkPreviewCount = 0; remoteCandidateCount = 0; localCandidateCount = 0
            v2vCandidateCount = 0; v2vPoolCount = 0; optimizationWindowCount = 0
            snapshotCount = 0; snapshotLoaderValidationFailures = 0; snapshotValidatorFailures = 0
            jsonTimeStopReason = ""; jsonTimeFinalSnapshotReached = $false; futureLookAheadViolations = 0
        }
        if ($script:FailureDiagnosticsAllowed -and (Test-Path -LiteralPath $script:DiagnosticsDir)) {
            Write-Phase11Outputs `
                -BaselineConsistency $baselineConsistency `
                -ArtifactRows $artifactRows `
                -ArtifactCheck $artifactCheck `
                -Counts $counts `
                -PipelineSucceeded $false
        }
    } catch {
        Write-Warning "Failed to write failure diagnostics: $($_.Exception.Message)"
    }
    exit 1
}
