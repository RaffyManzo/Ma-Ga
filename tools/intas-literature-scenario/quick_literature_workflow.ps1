param(
    [string]$IntasRoot = "C:\Users\raffa\IdeaProjects\external\InTAS",
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$PersistentRoot = ".\tmp\materialized-literature-scenarios",
    [string]$ScenarioName = "MaGaLiteratureBasedUrbanStudy",
    [ValidateSet("low_density", "nominal", "high_density")]
    [string]$Density = "nominal",
    [ValidateSet("smoke", "nominal", "extended")]
    [string]$DurationProfile = "smoke",
    [int]$Seed = 104729,
    [ValidateRange(0.0, 1000000.0)]
    [double]$RealtimeBrakeFactor = 0.0,
    [switch]$ForceRebuild,
    [switch]$PrintDetailedLiveReport,
    [switch]$PrintSummary
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$VariantName = "$Density-$DurationProfile-seed-$Seed"
$ResolvedPersistentRoot = if ([IO.Path]::IsPathRooted($PersistentRoot)) {
    $PersistentRoot
} else {
    Join-Path $RepoRoot $PersistentRoot
}
$MaterializedScenarioRoot = Join-Path (Join-Path $ResolvedPersistentRoot $ScenarioName) $VariantName

$MaterializeArgs = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass",
    "-File", (Join-Path $ToolRoot "materialize_literature_scenario.ps1"),
    "-IntasRoot", $IntasRoot,
    "-PersistentRoot", $PersistentRoot,
    "-ScenarioName", $ScenarioName,
    "-Density", $Density,
    "-DurationProfile", $DurationProfile,
    "-Seed", $Seed
)
if ($ForceRebuild) {
    $MaterializeArgs += "-ForceRebuild"
}

Write-Host "Materializing synthetic-calibrated InTAS scenario..."
& powershell @MaterializeArgs
if ($LASTEXITCODE -ne 0) {
    throw "Literature scenario materialization failed"
}

$RunArgs = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass",
    "-File", (Join-Path $ToolRoot "run_literature_scenario.ps1"),
    "-MaterializedScenarioRoot", $MaterializedScenarioRoot,
    "-MosaicRoot", $MosaicRoot,
    "-ScenarioName", $ScenarioName
)
if ($RealtimeBrakeFactor -gt 0.0) {
    $RunArgs += @(
        "-RealtimeBrakeFactor",
        $RealtimeBrakeFactor.ToString(
            "0.################",
            [Globalization.CultureInfo]::InvariantCulture
        )
    )
}
if ($PrintDetailedLiveReport) {
    $RunArgs += "-PrintDetailedLiveReport"
}

Write-Host "Running MOSAIC literature scenario..."
& powershell @RunArgs
if ($LASTEXITCODE -ne 0) {
    throw "Literature scenario run failed"
}

$ShowArgs = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass",
    "-File", (Join-Path $ToolRoot "show_latest_literature_report.ps1"),
    "-MosaicRoot", $MosaicRoot,
    "-ScenarioName", $ScenarioName
)
if ($PrintSummary) {
    $ShowArgs += "-PrintSummary"
}

Write-Host "Showing latest reports..."
& powershell @ShowArgs
if ($LASTEXITCODE -ne 0) {
    throw "Latest-report lookup failed"
}
