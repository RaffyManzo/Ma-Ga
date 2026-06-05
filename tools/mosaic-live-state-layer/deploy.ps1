param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiveStateLayerStudy"
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$SourceScenario = Join-Path $RepoRoot "data\mosaic-scenarios\$ScenarioName"
$MosaicScenarioRoot = Join-Path $ResolvedMosaicRoot "scenarios"
$TargetScenario = Join-Path $MosaicScenarioRoot $ScenarioName

if (-not (Test-Path -LiteralPath (Join-Path $ResolvedMosaicRoot "mosaic.bat") -PathType Leaf)) {
    throw "mosaic.bat not found under MOSAIC root: $ResolvedMosaicRoot"
}
if (-not (Test-Path -LiteralPath $MosaicScenarioRoot -PathType Container)) {
    throw "MOSAIC scenario root not found: $MosaicScenarioRoot"
}
if (-not (Test-Path -LiteralPath $SourceScenario -PathType Container)) {
    throw "Versioned live state scenario not found: $SourceScenario"
}
if (-not (Test-Path -LiteralPath (Join-Path $SourceScenario "application\maga-live-state-layer.jar") -PathType Leaf)) {
    throw "Build the live state layer JAR before deploy."
}

$ResolvedScenarioRoot = (Resolve-Path -LiteralPath $MosaicScenarioRoot).Path
$TargetParent = Split-Path -Parent $TargetScenario
$ResolvedTargetParent = (Resolve-Path -LiteralPath $TargetParent).Path
if (-not ($ResolvedTargetParent.Equals($ResolvedScenarioRoot, [System.StringComparison]::OrdinalIgnoreCase))) {
    throw "Refusing to deploy outside MOSAIC scenario root: $TargetScenario"
}
if ($ScenarioName -eq "MaGaIntegratedStudy" -or $TargetScenario -like "*MaGaIntegratedStudy*") {
    throw "Refusing to deploy over canonical scenario: $TargetScenario"
}

if (Test-Path -LiteralPath $TargetScenario) {
    $ResolvedTargetScenario = (Resolve-Path -LiteralPath $TargetScenario).Path
    if (-not ($ResolvedTargetScenario.StartsWith($ResolvedScenarioRoot, [System.StringComparison]::OrdinalIgnoreCase))) {
        throw "Refusing to remove target outside MOSAIC scenario root: $ResolvedTargetScenario"
    }
    Remove-Item -LiteralPath $ResolvedTargetScenario -Recurse -Force
}

Copy-Item -LiteralPath $SourceScenario -Destination $TargetScenario -Recurse

Write-Host "Deployed only requested live state scenario:"
Write-Host "  ScenarioName: $ScenarioName"
Write-Host "  Source: $SourceScenario"
Write-Host "  Target: $TargetScenario"
Write-Host "Copied files:"
Get-ChildItem -LiteralPath $TargetScenario -Recurse -File |
    ForEach-Object {
        Write-Host "  $($_.FullName.Substring($TargetScenario.Length + 1))"
    }
