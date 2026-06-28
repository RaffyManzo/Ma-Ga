param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-PropertyValue {
    param(
        [object]$Object,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [object]$DefaultValue = $null
    )

    if ($null -eq $Object) {
        return $DefaultValue
    }

    $Property = $Object.PSObject.Properties[$Name]

    if ($null -eq $Property -or $null -eq $Property.Value) {
        return $DefaultValue
    }

    return $Property.Value
}

function Convert-ToInt {
    param([object]$Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return 0
    }

    return [int]$Value
}

function Convert-ToDouble {
    param([object]$Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return 0.0
    }

    return [double]$Value
}

function Convert-ToBoolean {
    param([object]$Value)

    if ($Value -is [bool]) {
        return [bool]$Value
    }

    $Text = ([string]$Value).Trim().ToLowerInvariant()
    return ($Text -eq "true" -or $Text -eq "1")
}

function Get-NearestRankPercentile {
    param(
        [double[]]$Values,
        [double]$Percentile
    )

    if ($null -eq $Values -or $Values.Count -eq 0) {
        return $null
    }

    $Sorted = @($Values | Sort-Object)
    $Index = [int][Math]::Ceiling($Percentile * $Sorted.Count) - 1
    $Index = [Math]::Max(0, [Math]::Min($Sorted.Count - 1, $Index))
    return [double]$Sorted[$Index]
}

function Get-Statistics {
    param([double[]]$Values)

    $Values = @(
        $Values |
            Where-Object {
                (-not [double]::IsNaN([double]$_)) -and (-not [double]::IsInfinity([double]$_))
            }
    )

    if ($Values.Count -eq 0) {
        return [ordered]@{
            Count = 0
            Minimum = $null
            Mean = $null
            Median = $null
            P95 = $null
            Maximum = $null
        }
    }

    return [ordered]@{
        Count = $Values.Count
        Minimum = [double](($Values | Measure-Object -Minimum).Minimum)
        Mean = [Math]::Round(
            [double](($Values | Measure-Object -Average).Average),
            9
        )
        Median = Get-NearestRankPercentile -Values $Values -Percentile 0.50
        P95 = Get-NearestRankPercentile -Values $Values -Percentile 0.95
        Maximum = [double](($Values | Measure-Object -Maximum).Maximum)
    }
}

function Read-JsonOptional {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }

    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Resolve-PilotId {
    param([string]$ReportPath)

    $Parts = $ReportPath -split "[\\/]"
    $RunsIndex = [Array]::IndexOf($Parts, "runs")

    if ($RunsIndex -lt 0 -or ($RunsIndex + 1) -ge $Parts.Count) {
        throw "Impossibile ricavare il pilot ID da: $ReportPath"
    }

    return $Parts[$RunsIndex + 1]
}

function Resolve-RunRoot {
    param([string]$ReportPath)

    $Current = Split-Path -Parent $ReportPath

    while (-not [string]::IsNullOrWhiteSpace($Current)) {
        if (Test-Path -LiteralPath (Join-Path $Current "pilot_result.json")) {
            return $Current
        }

        $Parent = Split-Path -Parent $Current

        if ($Parent -eq $Current) {
            break
        }

        $Current = $Parent
    }

    throw "pilot_result.json non trovato risalendo da: $ReportPath"
}

function Get-ConfigIdFromPilotId {
    param([string]$PilotId)

    if ($PilotId -match "PILOT-(N-I|H-I|H-S)-") {
        return "CFG-" + $Matches[1]
    }

    return ""
}

function Get-ModeFromPilotId {
    param([string]$PilotId)

    if ($PilotId -match "-ADAPTIVE-") {
        return "LIVE_ADAPTIVE"
    }

    if ($PilotId -match "-STATIC_CONTROL-") {
        return "CONFIGURED_STATIC"
    }

    return ""
}

if (-not (Test-Path -LiteralPath $InputPath)) {
    throw "Input non trovato: $InputPath"
}

$InputPath = (Resolve-Path -LiteralPath $InputPath).Path
$TemporaryExtractionRoot = ""
$AnalysisRoot = ""

if (Test-Path -LiteralPath $InputPath -PathType Leaf) {
    if ([IO.Path]::GetExtension($InputPath) -ne ".zip") {
        throw "Il file di input deve essere uno ZIP."
    }

    $TemporaryExtractionRoot = Join-Path `
        $env:TEMP `
        ("v3b-batch-analysis-" + [Guid]::NewGuid().ToString("N"))

    New-Item `
        -ItemType Directory `
        -Path $TemporaryExtractionRoot `
        -Force |
        Out-Null

    Expand-Archive `
        -LiteralPath $InputPath `
        -DestinationPath $TemporaryExtractionRoot `
        -Force

    $AnalysisRoot = $TemporaryExtractionRoot
}
else {
    $AnalysisRoot = $InputPath
}

try {
    $ReportFiles = @(
        Get-ChildItem `
            -LiteralPath $AnalysisRoot `
            -Recurse `
            -File `
            -Filter "live_detailed_execution_report.json"
    )

    if ($ReportFiles.Count -eq 0) {
        throw "Nessun live_detailed_execution_report.json trovato."
    }

    if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
        $BaseName = if (Test-Path -LiteralPath $InputPath -PathType Leaf) {
            [IO.Path]::GetFileNameWithoutExtension($InputPath)
        }
        else {
            Split-Path -Leaf $InputPath
        }

        $OutputDirectory = Join-Path `
            (Split-Path -Parent $InputPath) `
            ($BaseName + "_corrected_analysis")
    }

    New-Item `
        -ItemType Directory `
        -Path $OutputDirectory `
        -Force |
        Out-Null

    $Rows = @()
    $JsonRuns = @()

    foreach ($ReportFile in $ReportFiles) {
        $Report = Get-Content `
            -LiteralPath $ReportFile.FullName `
            -Raw |
            ConvertFrom-Json

        $PilotId = Resolve-PilotId -ReportPath $ReportFile.FullName
        $RunRoot = Resolve-RunRoot -ReportPath $ReportFile.FullName
        $PilotResult = Read-JsonOptional -Path (Join-Path $RunRoot "pilot_result.json")
        $RuntimeArtifacts = Join-Path $RunRoot "runtime-artifacts"
        $Validation = Read-JsonOptional -Path (
            Join-Path $RuntimeArtifacts "literature_smoke_validation.json"
        )

        $ConfigId = [string](
            Get-PropertyValue `
                -Object $PilotResult `
                -Name "configId" `
                -DefaultValue (Get-ConfigIdFromPilotId -PilotId $PilotId)
        )

        $Mode = [string](
            Get-PropertyValue `
                -Object $PilotResult `
                -Name "deltaTMaxMode" `
                -DefaultValue (Get-ModeFromPilotId -PilotId $PilotId)
        )

        $Submitted = Convert-ToInt (
            Get-PropertyValue -Object $Report -Name "submitted" -DefaultValue 0
        )
        $Applied = Convert-ToInt (
            Get-PropertyValue -Object $Report -Name "applied" -DefaultValue 0
        )
        $Stale = Convert-ToInt (
            Get-PropertyValue -Object $Report -Name "staleDiscarded" -DefaultValue 0
        )
        $Failed = Convert-ToInt (
            Get-PropertyValue -Object $Report -Name "failed" -DefaultValue 0
        )
        $NullResults = Convert-ToInt (
            Get-PropertyValue -Object $Report -Name "nullResults" -DefaultValue 0
        )
        $ShutdownInFlight = Convert-ToInt (
            Get-PropertyValue -Object $Report -Name "shutdownInFlight" -DefaultValue 0
        )

        $TerminalAccounted = (
            $Applied +
            $Stale +
            $Failed +
            $NullResults +
            $ShutdownInFlight
        )

        $JobRecords = @(
            Get-PropertyValue `
                -Object $Report `
                -Name "jobRecords" `
                -DefaultValue @()
        )

        $AcceptedUniqueJobIds = @(
            $JobRecords |
                Where-Object {
                    Convert-ToBoolean (
                        Get-PropertyValue `
                            -Object $_ `
                            -Name "postCompletionAdaptiveDeltaTMaxSampleAccepted" `
                            -DefaultValue $false
                    )
                } |
                ForEach-Object {
                    [string](
                        Get-PropertyValue `
                            -Object $_ `
                            -Name "jobId" `
                            -DefaultValue ""
                    )
                } |
                Where-Object {
                    -not [string]::IsNullOrWhiteSpace($_)
                } |
                Sort-Object -Unique
        )

        $AppliedAges = @()

        foreach ($JobRecord in $JobRecords) {
            $FinalStatus = [string](
                Get-PropertyValue `
                    -Object $JobRecord `
                    -Name "finalStatus" `
                    -DefaultValue ""
            )

            if ($FinalStatus -ne "APPLIED") {
                continue
            }

            $StoredAgeProperty = $JobRecord.PSObject.Properties[
                "appliedSnapshotAgeSimulationSeconds"
            ]

            if (
                $null -ne $StoredAgeProperty -and
                $null -ne $StoredAgeProperty.Value
            ) {
                $Age = Convert-ToDouble $StoredAgeProperty.Value
            }
            else {
                $AppliedAtNs = Convert-ToDouble (
                    Get-PropertyValue `
                        -Object $JobRecord `
                        -Name "appliedAtSimulationTimeNs" `
                        -DefaultValue 0
                )

                $SnapshotTimeSeconds = Convert-ToDouble (
                    Get-PropertyValue `
                        -Object $JobRecord `
                        -Name "snapshotTimeSeconds" `
                        -DefaultValue 0
                )

                $Age = ($AppliedAtNs / 1000000000.0) - $SnapshotTimeSeconds
            }

            if ((-not [double]::IsNaN($Age)) -and (-not [double]::IsInfinity($Age))) {
                $AppliedAges += $Age
            }
        }

        $AgeStats = Get-Statistics -Values $AppliedAges

        $ValidationStatus = [string](
            Get-PropertyValue `
                -Object $Validation `
                -Name "status" `
                -DefaultValue ""
        )

        $ValidationErrors = @(
            Get-PropertyValue `
                -Object $Validation `
                -Name "errors" `
                -DefaultValue @()
        )

        $PilotValidation = Get-PropertyValue `
            -Object $PilotResult `
            -Name "validation" `
            -DefaultValue $null

        $PilotExecution = Get-PropertyValue `
            -Object $PilotResult `
            -Name "execution" `
            -DefaultValue $null

        $SimulationCompleted = Convert-ToBoolean (
            Get-PropertyValue `
                -Object $PilotValidation `
                -Name "simulationCompleted" `
                -DefaultValue $false
        )

        $TotalViolationCount = Convert-ToInt (
            Get-PropertyValue `
                -Object $PilotValidation `
                -Name "totalViolationCount" `
                -DefaultValue 0
        )

        $RunnerExitCode = Convert-ToInt (
            Get-PropertyValue `
                -Object $PilotExecution `
                -Name "runnerExitCode" `
                -DefaultValue 0
        )

        $ExecutionValid = (
            $RunnerExitCode -eq 0 -and
            $ValidationStatus -eq "LITERATURE_SMOKE_TEST_PASSED" -and
            $ValidationErrors.Count -eq 0 -and
            $SimulationCompleted -and
            $TotalViolationCount -eq 0 -and
            $TerminalAccounted -eq $Submitted
        )

        $Classification = if (-not $ExecutionValid) {
            "INVALID_OR_REVIEW_REQUIRED"
        }
        elseif ($ShutdownInFlight -gt 0) {
            "VALID_PASS_WITH_TERMINAL_IN_FLIGHT"
        }
        else {
            "VALID_PASS"
        }

        $WallClockTiming = Get-PropertyValue `
            -Object $Report `
            -Name "wallClockTiming" `
            -DefaultValue $null

        $Row = [pscustomobject]@{
            PilotId = $PilotId
            ConfigId = $ConfigId
            DeltaTMaxMode = $Mode
            ExecutionClassification = $Classification
            Submitted = $Submitted
            TerminalAccounted = $TerminalAccounted
            Applied = $Applied
            StaleDiscarded = $Stale
            Failed = $Failed
            NullResults = $NullResults
            ShutdownInFlight = $ShutdownInFlight
            UniqueAcceptedAdaptiveSamples = $AcceptedUniqueJobIds.Count
            AppliedSnapshotAgeCount = $AgeStats.Count
            AppliedSnapshotAgeMinimumSeconds = $AgeStats.Minimum
            AppliedSnapshotAgeMeanSeconds = $AgeStats.Mean
            AppliedSnapshotAgeMedianSeconds = $AgeStats.Median
            AppliedSnapshotAgeP95Seconds = $AgeStats.P95
            AppliedSnapshotAgeMaximumSeconds = $AgeStats.Maximum
            GaRuntimeP95Seconds = Get-PropertyValue `
                -Object $WallClockTiming `
                -Name "p95" `
                -DefaultValue $null
            ValidatorStatus = $ValidationStatus
            ValidatorErrorCount = $ValidationErrors.Count
            SimulationCompleted = $SimulationCompleted
            TotalViolationCount = $TotalViolationCount
            RunnerExitCode = $RunnerExitCode
        }

        $Rows += $Row

        $JsonRuns += [ordered]@{
            pilotId = $PilotId
            configId = $ConfigId
            deltaTMaxMode = $Mode
            executionClassification = $Classification
            submitted = $Submitted
            terminalAccounted = $TerminalAccounted
            applied = $Applied
            staleDiscarded = $Stale
            failed = $Failed
            nullResults = $NullResults
            shutdownInFlight = $ShutdownInFlight
            uniqueAcceptedAdaptiveSamples = $AcceptedUniqueJobIds.Count
            acceptedAdaptiveJobIds = $AcceptedUniqueJobIds
            appliedSnapshotAgeSimulationSeconds = $AgeStats
            validatorStatus = $ValidationStatus
            validatorErrorCount = $ValidationErrors.Count
            simulationCompleted = $SimulationCompleted
            totalViolationCount = $TotalViolationCount
            runnerExitCode = $RunnerExitCode
        }
    }

    $Rows = @($Rows | Sort-Object ConfigId, DeltaTMaxMode)

    $RunSummaryPath = Join-Path `
        $OutputDirectory `
        "V3B_CORRECTED_RUN_SUMMARY.csv"

    $Rows |
        Export-Csv `
            -LiteralPath $RunSummaryPath `
            -NoTypeInformation `
            -Encoding UTF8

    $ComparisonRows = @()

    foreach ($ConfigGroup in ($Rows | Group-Object ConfigId)) {
        $StaticRows = @(
            $ConfigGroup.Group |
                Where-Object {
                    $_.DeltaTMaxMode -eq "CONFIGURED_STATIC"
                }
        )

        $AdaptiveRows = @(
            $ConfigGroup.Group |
                Where-Object {
                    $_.DeltaTMaxMode -eq "LIVE_ADAPTIVE"
                }
        )

        if ($StaticRows.Count -ne 1 -or $AdaptiveRows.Count -ne 1) {
            continue
        }

        $Static = $StaticRows[0]
        $Adaptive = $AdaptiveRows[0]

        $ComparisonRows += [pscustomobject]@{
            ConfigId = $ConfigGroup.Name
            StaticClassification = $Static.ExecutionClassification
            AdaptiveClassification = $Adaptive.ExecutionClassification
            AppliedDifferenceAdaptiveMinusStatic = (
                $Adaptive.Applied - $Static.Applied
            )
            StaleDifferenceAdaptiveMinusStatic = (
                $Adaptive.StaleDiscarded - $Static.StaleDiscarded
            )
            ShutdownInFlightDifferenceAdaptiveMinusStatic = (
                $Adaptive.ShutdownInFlight - $Static.ShutdownInFlight
            )
            AdaptiveUniqueAcceptedSamples = (
                $Adaptive.UniqueAcceptedAdaptiveSamples
            )
            StaticAppliedSnapshotAgeMaximumSeconds = (
                $Static.AppliedSnapshotAgeMaximumSeconds
            )
            AdaptiveAppliedSnapshotAgeMaximumSeconds = (
                $Adaptive.AppliedSnapshotAgeMaximumSeconds
            )
            AppliedSnapshotAgeMaximumDifferenceSeconds = (
                [double]$Adaptive.AppliedSnapshotAgeMaximumSeconds -
                [double]$Static.AppliedSnapshotAgeMaximumSeconds
            )
        }
    }

    $ComparisonPath = Join-Path `
        $OutputDirectory `
        "V3B_STATIC_ADAPTIVE_CORRECTED_COMPARISON.csv"

    $ComparisonRows |
        Export-Csv `
            -LiteralPath $ComparisonPath `
            -NoTypeInformation `
            -Encoding UTF8

    $InvalidRows = @(
        $Rows |
            Where-Object {
                $_.ExecutionClassification -eq "INVALID_OR_REVIEW_REQUIRED"
            }
    )

    $OverallStatus = if ($InvalidRows.Count -eq 0) {
        "ALL_EXECUTIONS_TECHNICALLY_VALID"
    }
    else {
        "TECHNICAL_REVIEW_REQUIRED"
    }

    $JsonSummaryPath = Join-Path `
        $OutputDirectory `
        "V3B_CORRECTED_ANALYSIS.json"

    [ordered]@{
        schemaVersion = "1.0"
        generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        inputPath = $InputPath
        overallStatus = $OverallStatus
        runCount = $Rows.Count
        technicallyValidRunCount = $Rows.Count - $InvalidRows.Count
        invalidOrReviewRequiredRunCount = $InvalidRows.Count
        runs = $JsonRuns
    } |
        ConvertTo-Json -Depth 50 |
        Set-Content `
            -LiteralPath $JsonSummaryPath `
            -Encoding UTF8

    $AuditPath = Join-Path `
        $OutputDirectory `
        "V3B_CORRECTED_ANALYSIS_AUDIT.md"

    @(
        "# V3-B corrected batch analysis",
        "",
        "- Input: $InputPath",
        "- Generated: $((Get-Date).ToString("o"))",
        "- Runs: $($Rows.Count)",
        "- Overall status: $OverallStatus",
        "",
        "## Corrected terminal accounting",
        "",
        "APPLIED + STALE_DISCARDED + FAILED + NULL_STEP_RESULT + SHUTDOWN_IN_FLIGHT = SUBMITTED",
        "",
        "A terminal `SHUTDOWN_IN_FLIGHT` job is explicit and does not invalidate an otherwise successful simulation.",
        "",
        "## Adaptive sample accounting",
        "",
        "Accepted samples are counted once per unique `jobId` from final detailed job records.",
        "",
        "## Applied snapshot age",
        "",
        "appliedSnapshotAgeSimulationSeconds = appliedAtSimulationTimeNs / 1e9 - snapshotTimeSeconds"
    ) |
        Set-Content `
            -LiteralPath $AuditPath `
            -Encoding UTF8

    $ManifestPath = Join-Path $OutputDirectory "MANIFEST_SHA256.csv"
    $ManifestRows = @()

    foreach (
        $File in @(
            Get-ChildItem `
                -LiteralPath $OutputDirectory `
                -File |
                Where-Object {
                    $_.FullName -ne $ManifestPath
                } |
                Sort-Object Name
        )
    ) {
        $ManifestRows += [pscustomobject]@{
            Path = $File.Name
            SizeBytes = $File.Length
            Sha256 = (
                Get-FileHash `
                    -LiteralPath $File.FullName `
                    -Algorithm SHA256
            ).Hash.ToLowerInvariant()
        }
    }

    $ManifestRows |
        Export-Csv `
            -LiteralPath $ManifestPath `
            -NoTypeInformation `
            -Encoding UTF8

    Write-Host ""
    Write-Host "V3B_CORRECTED_BATCH_ANALYSIS_COMPLETE"
    Write-Host "OverallStatus=$OverallStatus"
    Write-Host "RunCount=$($Rows.Count)"
    Write-Host "CorrectedRunSummary=$RunSummaryPath"
    Write-Host "CorrectedComparison=$ComparisonPath"
    Write-Host "CorrectedAnalysisJson=$JsonSummaryPath"
    Write-Host "Audit=$AuditPath"
    Write-Host "Manifest=$ManifestPath"
}
finally {
    if (
        -not [string]::IsNullOrWhiteSpace($TemporaryExtractionRoot) -and
        (Test-Path -LiteralPath $TemporaryExtractionRoot)
    ) {
        Remove-Item `
            -LiteralPath $TemporaryExtractionRoot `
            -Recurse `
            -Force
    }
}
