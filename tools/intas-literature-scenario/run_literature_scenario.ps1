param(
    [Parameter(Mandatory = $true)]
    [string]$MaterializedScenarioRoot,
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiteratureBasedUrbanStudy",
    [switch]$PrintDetailedLiveReport,
    [ValidateSet(
        "BUILD",
        "RECOVERED_VALIDATED_ARTIFACT"
    )]
    [string]$RuntimeArtifactMode = "BUILD",
    [string]$RuntimeJarPath = "",
    [string]$ExpectedRuntimeJarSha256 = "",
    [long]$ExpectedRuntimeJarSizeBytes = 0
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

function Resolve-MaybeRelative {
    param([string]$Path)
    if ([IO.Path]::IsPathRooted($Path)) {
        return (Resolve-Path -LiteralPath $Path).Path
    }
    return (Resolve-Path -LiteralPath (Join-Path $RepoRoot $Path)).Path
}

Assert-SafeScenarioName -Name $ScenarioName
$ResolvedScenarioRoot = Resolve-MaybeRelative -Path $MaterializedScenarioRoot
$ResolvedMosaicRoot = Resolve-MaybeRelative -Path $MosaicRoot
$MosaicBat = Join-Path $ResolvedMosaicRoot "mosaic.bat"
$LogsRoot = Join-Path $ResolvedMosaicRoot "logs"
$ScenarioDb = Join-Path $ResolvedScenarioRoot "application\intas_literature_urban.db"

if (-not (Test-Path -LiteralPath $ScenarioDb -PathType Leaf)) {
    throw "Refusing to run literature scenario without real MOSAIC DB: $ScenarioDb"
}
if (-not (Test-Path -LiteralPath $MosaicBat -PathType Leaf)) {
    throw "mosaic.bat not found under MOSAIC root: $ResolvedMosaicRoot"
}
if (-not (Test-Path -LiteralPath $LogsRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $LogsRoot -Force | Out-Null
}

if ($RuntimeArtifactMode -eq "BUILD") {
    Write-Host "Building live MA-GA runtime JAR..."
    $BuildArgs = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        (Join-Path $RepoRoot "tools\mosaic-live-maga-runtime\build.ps1"),
        "-MosaicRoot",
        $ResolvedMosaicRoot,
        "-ScenarioName",
        $ScenarioName
    )
    & powershell @BuildArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Runtime build failed"
    }
}
else {
    if ([string]::IsNullOrWhiteSpace($RuntimeJarPath)) {
        throw "RuntimeJarPath is required for RECOVERED_VALIDATED_ARTIFACT mode"
    }
    if ([string]::IsNullOrWhiteSpace($ExpectedRuntimeJarSha256)) {
        throw "ExpectedRuntimeJarSha256 is required for RECOVERED_VALIDATED_ARTIFACT mode"
    }
    $ResolvedRuntimeJarPath = Resolve-MaybeRelative -Path $RuntimeJarPath
    Write-Host "Runtime artifact mode:"
    Write-Host "RECOVERED_VALIDATED_ARTIFACT"
    Write-Host "Runtime build executed:"
    Write-Host "false"
    Write-Host "Runtime artifact path:"
    Write-Host $ResolvedRuntimeJarPath
    Write-Host "Runtime artifact expected SHA-256:"
    Write-Host $ExpectedRuntimeJarSha256
    & powershell -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $RepoRoot "tools\mosaic-live-maga-runtime\validate_runtime_artifact.ps1") `
        -RuntimeJarPath $ResolvedRuntimeJarPath `
        -ExpectedSha256 $ExpectedRuntimeJarSha256 `
        -ExpectedSizeBytes $ExpectedRuntimeJarSizeBytes
    if ($LASTEXITCODE -ne 0) {
        throw "Recovered runtime artifact validation failed"
    }
}

Write-Host "Deploying materialized literature scenario..."
$DeployArgs = @(
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    (Join-Path $ToolRoot "deploy_materialized_literature_scenario.ps1"),
    "-MaterializedScenarioRoot",
    $ResolvedScenarioRoot,
    "-MosaicRoot",
    $ResolvedMosaicRoot,
    "-ScenarioName",
    $ScenarioName
)
if ($RuntimeArtifactMode -eq "RECOVERED_VALIDATED_ARTIFACT") {
    $DeployArgs += @(
        "-RuntimeJarPath",
        $ResolvedRuntimeJarPath,
        "-ExpectedRuntimeJarSha256",
        $ExpectedRuntimeJarSha256,
        "-ExpectedRuntimeJarSizeBytes",
        $ExpectedRuntimeJarSizeBytes
    )
}
& powershell @DeployArgs
if ($LASTEXITCODE -ne 0) {
    throw "Materialized scenario deploy failed"
}

$BeforeRuns = @{}
Get-ChildItem -LiteralPath $LogsRoot -Directory |
    Where-Object { $_.Name -like "*-$ScenarioName" } |
    ForEach-Object { $BeforeRuns[$_.Name] = $_.FullName }

Push-Location $ResolvedMosaicRoot
try {
    Write-Host "Running: .\mosaic.bat -s $ScenarioName"
    & .\mosaic.bat -s $ScenarioName
    if ($LASTEXITCODE -ne 0) {
        throw "MOSAIC run failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$AfterRuns = @(Get-ChildItem -LiteralPath $LogsRoot -Directory |
    Where-Object { $_.Name -like "*-$ScenarioName" } |
    Sort-Object LastWriteTime -Descending)
$NewRun = $AfterRuns | Where-Object { -not $BeforeRuns.ContainsKey($_.Name) } | Select-Object -First 1
if ($null -eq $NewRun) {
    $NewRun = $AfterRuns | Select-Object -First 1
}
if ($null -eq $NewRun) {
    throw "No MOSAIC run directory found for scenario $ScenarioName"
}

Write-Host "Summarizing run $($NewRun.Name)..."
$SummarizeArgs = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass",
    "-File", (Join-Path $RepoRoot "tools\mosaic-live-maga-runtime\summarize-run.ps1"),
    "-MosaicRoot", $ResolvedMosaicRoot,
    "-ScenarioName", $ScenarioName,
    "-RunName", $NewRun.Name
)
if ($PrintDetailedLiveReport) {
    $SummarizeArgs += "-PrintDetailedLiveReport"
}
& powershell @SummarizeArgs
if ($LASTEXITCODE -ne 0) {
    throw "Run summarizer failed"
}

Write-Host "Validating literature smoke run..."
& powershell -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $ToolRoot "validate_literature_smoke_run.ps1") `
    -MosaicRoot $ResolvedMosaicRoot `
    -ScenarioName $ScenarioName `
    -RunName $NewRun.Name
if ($LASTEXITCODE -ne 0) {
    throw "Literature smoke validator failed"
}

$RuntimeDir = Join-Path $NewRun.FullName "live-maga-runtime"
Write-Host "LITERATURE_SCENARIO_RUN_COMPLETED"
Write-Host "Run name: $($NewRun.Name)"
Write-Host "Run directory: $($NewRun.FullName)"
Write-Host "Summary JSON: $(Join-Path $RuntimeDir 'live_run_summary.json')"
Write-Host "Summary Markdown: $(Join-Path $RuntimeDir 'live_run_summary.md')"
Write-Host "Smoke validation JSON: $(Join-Path $RuntimeDir 'literature_smoke_validation.json')"
Write-Host "Smoke validation Markdown: $(Join-Path $RuntimeDir 'literature_smoke_validation.md')"
Write-Host "Detailed live report TXT: $(Join-Path $RuntimeDir 'live-reporting\live_detailed_execution_report.txt')"
Write-Host "Detailed live report Markdown: $(Join-Path $RuntimeDir 'live-reporting\live_detailed_execution_report.md')"
Write-Host "Detailed live report JSON: $(Join-Path $RuntimeDir 'live-reporting\live_detailed_execution_report.json')"
