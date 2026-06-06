param(
    [string[]]$ScenarioIds = @(
        "MaGaIntegratedStudy",
        "MaGaIntegratedStudyRequest2x",
        "MaGaIntegratedStudyResponse2x",
        "MaGaIntegratedStudyFrequency2x"
    )
)

$ErrorActionPreference = "Stop"

$ScriptRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent $ScriptRoot
$SourceRoot = Join-Path $RepoRoot "data\mosaic-scenarios"
$MosaicScenarioRoot = Join-Path $RepoRoot "tmp\mosaic-25.2\scenarios"

if (-not (Test-Path -LiteralPath $SourceRoot -PathType Container)) {
    throw "Versioned scenario source root not found: $SourceRoot"
}

if (-not (Test-Path -LiteralPath $MosaicScenarioRoot -PathType Container)) {
    throw "MOSAIC scenario deployment root not found: $MosaicScenarioRoot"
}

$ResolvedSourceRoot = (Resolve-Path -LiteralPath $SourceRoot).Path
$ResolvedMosaicScenarioRoot = (Resolve-Path -LiteralPath $MosaicScenarioRoot).Path
$Copied = @()

function Remove-ScenarioDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $LastError = $null
    for ($Attempt = 1; $Attempt -le 5; $Attempt++) {
        try {
            Remove-Item -LiteralPath $Path -Recurse -Force
            return
        } catch {
            $LastError = $_
            if ($Attempt -lt 5) {
                Start-Sleep -Milliseconds 500
            }
        }
    }

    throw "Unable to remove existing deployed scenario after retries: $Path. Last error: $LastError"
}

foreach ($ScenarioId in $ScenarioIds) {
    $SourceScenario = Join-Path $ResolvedSourceRoot $ScenarioId
    if (-not (Test-Path -LiteralPath $SourceScenario -PathType Container)) {
        throw "Scenario source not found: $SourceScenario"
    }

    $TargetScenario = Join-Path $ResolvedMosaicScenarioRoot $ScenarioId
    $TargetParent = Split-Path -Parent $TargetScenario
    $ResolvedTargetParent = (Resolve-Path -LiteralPath $TargetParent).Path
    if (-not ($ResolvedTargetParent.Equals($ResolvedMosaicScenarioRoot, [System.StringComparison]::OrdinalIgnoreCase))) {
        throw "Refusing to deploy outside MOSAIC scenario root: $TargetScenario"
    }

    if (Test-Path -LiteralPath $TargetScenario) {
        $ResolvedTargetScenario = (Resolve-Path -LiteralPath $TargetScenario).Path
        if (-not ($ResolvedTargetScenario.StartsWith($ResolvedMosaicScenarioRoot, [System.StringComparison]::OrdinalIgnoreCase))) {
            throw "Refusing to remove target outside MOSAIC scenario root: $ResolvedTargetScenario"
        }
        Remove-ScenarioDirectory -Path $ResolvedTargetScenario
    }

    Copy-Item -LiteralPath $SourceScenario -Destination $TargetScenario -Recurse
    $Copied += $ScenarioId
}

Write-Host "Deployed MOSAIC diagnostic scenarios:"
foreach ($ScenarioId in $Copied) {
    Write-Host "  $ScenarioId"
}
