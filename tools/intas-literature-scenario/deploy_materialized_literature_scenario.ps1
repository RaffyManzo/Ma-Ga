param(
    [Parameter(Mandatory = $true)]
    [string]$MaterializedScenarioRoot,
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiteratureBasedUrbanStudy"
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
$GeneratedJar = Join-Path $RepoRoot "tools\mosaic-live-maga-runtime\out\maga-live-maga-runtime.jar"
$AdHocDiagnosticJar = Join-Path $RepoRoot "tools\mosaic-adhoc-radio-diagnostic\out\maga-adhoc-radio-diagnostic.jar"
$ScenarioDb = Join-Path $ResolvedScenarioRoot "application\intas_literature_urban.db"

if (-not (Test-Path -LiteralPath $MosaicBat -PathType Leaf)) {
    throw "mosaic.bat not found under MOSAIC root: $ResolvedMosaicRoot"
}
if (-not (Test-Path -LiteralPath $ScenarioDb -PathType Leaf)) {
    throw "Materialized scenario DB not found: $ScenarioDb"
}
if (-not (Test-Path -LiteralPath $GeneratedJar -PathType Leaf)) {
    throw "Build the live MA-GA runtime JAR before deploy: $GeneratedJar"
}
if (-not (Test-Path -LiteralPath $AdHocDiagnosticJar -PathType Leaf)) {
    throw "Ad-hoc radio diagnostic JAR not found. Build tools\mosaic-adhoc-radio-diagnostic first without committing generated output: $AdHocDiagnosticJar"
}

& py -3 -B (Join-Path $ToolRoot "validate_materialized_literature_scenario.py") --scenario-root $ResolvedScenarioRoot --repo-root $RepoRoot
if ($LASTEXITCODE -ne 0) {
    throw "Materialized scenario validation failed: $ResolvedScenarioRoot"
}

$ScenarioRoot = Join-Path $ResolvedMosaicRoot "scenarios"
if (-not (Test-Path -LiteralPath $ScenarioRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $ScenarioRoot -Force | Out-Null
}
$TargetScenario = Join-Path $ScenarioRoot $ScenarioName
$ResolvedScenarioRootParent = (Resolve-Path -LiteralPath $ScenarioRoot).Path
if (Test-Path -LiteralPath $TargetScenario) {
    $ResolvedTarget = (Resolve-Path -LiteralPath $TargetScenario).Path
    if (-not $ResolvedTarget.StartsWith($ResolvedScenarioRootParent, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove target outside MOSAIC scenarios root: $ResolvedTarget"
    }
    Remove-Item -LiteralPath $ResolvedTarget -Recurse -Force
}

Copy-Item -LiteralPath $ResolvedScenarioRoot -Destination $TargetScenario -Recurse
$TargetApplication = Join-Path $TargetScenario "application"
Copy-Item -LiteralPath $GeneratedJar -Destination (Join-Path $TargetApplication "maga-live-maga-runtime.jar") -Force
Copy-Item -LiteralPath $AdHocDiagnosticJar -Destination (Join-Path $TargetApplication "maga-adhoc-radio-diagnostic.jar") -Force

Write-Host "DEPLOYED_MATERIALIZED_LITERATURE_SCENARIO"
Write-Host "ScenarioName: $ScenarioName"
Write-Host "Source: $ResolvedScenarioRoot"
Write-Host "Target: $TargetScenario"
Write-Host "Database: $(Join-Path $TargetApplication 'intas_literature_urban.db')"
Write-Host "Injected runtime JAR: $GeneratedJar"
Write-Host "Injected ad-hoc radio diagnostic JAR: $AdHocDiagnosticJar"
