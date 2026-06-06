param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiveMagaRuntimeStudy"
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$SourceScenario = Join-Path $RepoRoot "data\mosaic-scenarios\$ScenarioName"
$MosaicScenarioRoot = Join-Path $ResolvedMosaicRoot "scenarios"
$TargetScenario = Join-Path $MosaicScenarioRoot $ScenarioName
$GeneratedJar = Join-Path $ToolRoot "out\maga-live-maga-runtime.jar"

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
Assert-SafeScenarioName -Name $ScenarioName

if (-not (Test-Path -LiteralPath (Join-Path $ResolvedMosaicRoot "mosaic.bat") -PathType Leaf)) {
    throw "mosaic.bat not found under MOSAIC root: $ResolvedMosaicRoot"
}
if (-not (Test-Path -LiteralPath $SourceScenario -PathType Container)) {
    throw "Versioned runtime scenario not found: $SourceScenario"
}
if (-not (Test-Path -LiteralPath $GeneratedJar -PathType Leaf)) {
    throw "Build the live MA-GA runtime JAR before deploy: $GeneratedJar"
}

$ResolvedScenarioRoot = (Resolve-Path -LiteralPath $MosaicScenarioRoot).Path
if (Test-Path -LiteralPath $TargetScenario) {
    $ResolvedTargetScenario = (Resolve-Path -LiteralPath $TargetScenario).Path
    if (-not ($ResolvedTargetScenario.StartsWith($ResolvedScenarioRoot, [System.StringComparison]::OrdinalIgnoreCase))) {
        throw "Refusing to remove target outside MOSAIC scenario root: $ResolvedTargetScenario"
    }
    Remove-Item -LiteralPath $ResolvedTargetScenario -Recurse -Force
}
Copy-Item -LiteralPath $SourceScenario -Destination $TargetScenario -Recurse
$TargetApplicationDir = Join-Path $TargetScenario "application"
if (-not (Test-Path -LiteralPath $TargetApplicationDir -PathType Container)) {
    throw "Deployed scenario application directory not found: $TargetApplicationDir"
}
Copy-Item -LiteralPath $GeneratedJar -Destination (Join-Path $TargetApplicationDir "maga-live-maga-runtime.jar") -Force

Write-Host "Deployed only requested runtime scenario:"
Write-Host "  ScenarioName: $ScenarioName"
Write-Host "  Source: $SourceScenario"
Write-Host "  Target: $TargetScenario"
Write-Host "  Injected runtime JAR: $GeneratedJar"
Write-Host "Copied files:"
Get-ChildItem -LiteralPath $TargetScenario -Recurse -File |
    ForEach-Object { Write-Host "  $($_.FullName.Substring($TargetScenario.Length + 1))" }
