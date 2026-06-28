param(
    [string]$AnalyzerPath = (
        Join-Path `
            $PSScriptRoot `
            "analyze_v3b_micro_pilot_batch.ps1"
    )
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not (Test-Path -LiteralPath $AnalyzerPath -PathType Leaf)) {
    throw "Analyzer non trovato: $AnalyzerPath"
}

$TestRoot = Join-Path `
    $env:TEMP `
    ("v3b-analysis-self-test-" + [Guid]::NewGuid().ToString("N"))

$RunRoot = Join-Path `
    $TestRoot `
    "batch\runs\V3B-PILOT-N-I-ADAPTIVE-104729"

$RuntimeArtifacts = Join-Path $RunRoot "runtime-artifacts"
$ReportingDirectory = Join-Path $RuntimeArtifacts "live-reporting"
$OutputDirectory = Join-Path $TestRoot "analysis"

New-Item `
    -ItemType Directory `
    -Path $ReportingDirectory `
    -Force |
    Out-Null

try {
    [ordered]@{
        submitted = 4
        completed = 4
        applied = 2
        staleDiscarded = 1
        failed = 0
        nullResults = 0
        shutdownInFlight = 1
        wallClockTiming = [ordered]@{
            count = 3
            min = 0.1
            mean = 0.2
            median = 0.2
            p95 = 0.3
            max = 0.3
        }
        jobRecords = @(
            [ordered]@{
                jobId = "job-1"
                finalStatus = "APPLIED"
                snapshotTimeSeconds = 1.0
                appliedAtSimulationTimeNs = 2500000000
                appliedSnapshotAgeSimulationSeconds = 1.5
                postCompletionAdaptiveDeltaTMaxSampleAccepted = $true
            },
            [ordered]@{
                jobId = "job-2"
                finalStatus = "APPLIED"
                snapshotTimeSeconds = 2.0
                appliedAtSimulationTimeNs = 5000000000
                appliedSnapshotAgeSimulationSeconds = 3.0
                postCompletionAdaptiveDeltaTMaxSampleAccepted = $true
            },
            [ordered]@{
                jobId = "job-3"
                finalStatus = "STALE_DISCARDED"
                snapshotTimeSeconds = 3.0
                appliedAtSimulationTimeNs = 0
                appliedSnapshotAgeSimulationSeconds = 0.0
                postCompletionAdaptiveDeltaTMaxSampleAccepted = $true
            },
            [ordered]@{
                jobId = "job-4"
                finalStatus = "SHUTDOWN_IN_FLIGHT"
                snapshotTimeSeconds = 4.0
                appliedAtSimulationTimeNs = 0
                appliedSnapshotAgeSimulationSeconds = 0.0
                postCompletionAdaptiveDeltaTMaxSampleAccepted = $false
            }
        )
    } |
        ConvertTo-Json -Depth 20 |
        Set-Content `
            -LiteralPath (
                Join-Path `
                    $ReportingDirectory `
                    "live_detailed_execution_report.json"
            ) `
            -Encoding UTF8

    [ordered]@{
        status = "LITERATURE_SMOKE_TEST_PASSED"
        errors = @()
    } |
        ConvertTo-Json -Depth 10 |
        Set-Content `
            -LiteralPath (
                Join-Path `
                    $RuntimeArtifacts `
                    "literature_smoke_validation.json"
            ) `
            -Encoding UTF8

    [ordered]@{
        configId = "CFG-N-I"
        deltaTMaxMode = "LIVE_ADAPTIVE"
        execution = [ordered]@{
            runnerExitCode = 0
        }
        validation = [ordered]@{
            simulationCompleted = $true
            totalViolationCount = 0
        }
    } |
        ConvertTo-Json -Depth 10 |
        Set-Content `
            -LiteralPath (
                Join-Path `
                    $RunRoot `
                    "pilot_result.json"
            ) `
            -Encoding UTF8

    & powershell.exe `
        -NoProfile `
        -ExecutionPolicy Bypass `
        -File $AnalyzerPath `
        -InputPath (Join-Path $TestRoot "batch") `
        -OutputDirectory $OutputDirectory

    if ($LASTEXITCODE -ne 0) {
        throw "Analyzer self-test terminato con exit code $LASTEXITCODE"
    }

    $Rows = @(
        Import-Csv `
            -LiteralPath (
                Join-Path `
                    $OutputDirectory `
                    "V3B_CORRECTED_RUN_SUMMARY.csv"
            )
    )

    if ($Rows.Count -ne 1) {
        throw "Self-test: numero righe inatteso."
    }

    $Row = $Rows[0]

    if (
        $Row.ExecutionClassification -ne
        "VALID_PASS_WITH_TERMINAL_IN_FLIGHT"
    ) {
        throw "Self-test: classificazione terminale errata."
    }

    if ([int]$Row.TerminalAccounted -ne 4) {
        throw "Self-test: terminal accounting errato."
    }

    if ([int]$Row.UniqueAcceptedAdaptiveSamples -ne 3) {
        throw "Self-test: conteggio campioni unici errato."
    }

    if (
        [Math]::Abs(
            [double]$Row.AppliedSnapshotAgeMaximumSeconds - 3.0
        ) -gt 0.000000001
    ) {
        throw "Self-test: età massima snapshot errata."
    }

    Write-Host ""
    Write-Host "V3B_BATCH_ANALYZER_SELF_TEST_PASSED"
    Write-Host "Assertions=4"
}
finally {
    if (Test-Path -LiteralPath $TestRoot) {
        Remove-Item `
            -LiteralPath $TestRoot `
            -Recurse `
            -Force
    }
}
