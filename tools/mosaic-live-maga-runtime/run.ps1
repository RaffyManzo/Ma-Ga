param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [ValidateSet("normal", "diagnostic-overrun")]
    [string]$Profile = "normal",
    [string]$ScenarioName = "MaGaLiveMagaRuntimeStudy"
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$MosaicBat = Join-Path $ResolvedMosaicRoot "mosaic.bat"
$DeployedScenario = Join-Path $ResolvedMosaicRoot "scenarios\$ScenarioName"
$DeployedApplication = Join-Path $DeployedScenario "application"

if (-not (Test-Path -LiteralPath $MosaicBat -PathType Leaf)) {
    throw "mosaic.bat not found: $MosaicBat"
}
if (-not (Test-Path -LiteralPath $DeployedScenario -PathType Container)) {
    throw "Runtime scenario $ScenarioName is not deployed under $ResolvedMosaicRoot"
}

$VersionedApplication = Join-Path $RepoRoot "data\mosaic-scenarios\$ScenarioName\application"
$ProfileConfig = if ($Profile -eq "diagnostic-overrun") {
    Join-Path $VersionedApplication "ma_ga_live_runtime_config_diagnostic_overrun.json"
} else {
    Join-Path $VersionedApplication "ma_ga_live_runtime_config.json"
}
if (-not (Test-Path -LiteralPath $ProfileConfig -PathType Leaf)) {
    throw "Runtime profile config not found: $ProfileConfig"
}
Copy-Item -LiteralPath $ProfileConfig -Destination (Join-Path $DeployedApplication "ma_ga_live_runtime_config.json") -Force
Write-Host "Runtime profile applied to deployed scenario: $Profile"

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
