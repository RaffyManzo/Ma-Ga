param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2"
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$MosaicBat = Join-Path $ResolvedMosaicRoot "mosaic.bat"

if (-not (Test-Path -LiteralPath $MosaicBat -PathType Leaf)) {
    throw "mosaic.bat not found: $MosaicBat"
}
if (-not (Test-Path -LiteralPath (Join-Path $ResolvedMosaicRoot "scenarios\MaGaLiveStateLayerStudy") -PathType Container)) {
    throw "Live state layer scenario is not deployed under $ResolvedMosaicRoot"
}

Push-Location $ResolvedMosaicRoot
try {
    Write-Host "Running: .\mosaic.bat -s MaGaLiveStateLayerStudy"
    & .\mosaic.bat -s MaGaLiveStateLayerStudy
    if ($LASTEXITCODE -ne 0) {
        throw "MOSAIC run failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
