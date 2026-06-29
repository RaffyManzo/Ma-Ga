param(
    [ValidateSet(
        "Validate",
        "Prepare",
        "DryRun",
        "SelfTest",
        "Ready",
        "PrepareAll",
        "Execute",
        "Report"
    )]
    [string]$Mode = "Validate",

    [string]$RepoRoot = "",
    [string]$MosaicRoot = "tmp\mosaic-25.2",
    [string]$IntasRoot = "C:\Users\raffa\IdeaProjects\external\InTAS",
    [string]$ScenarioConvert = "",
    [string]$Python = "py"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$env:PYTHONDONTWRITEBYTECODE = "1"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (
        Resolve-Path (Join-Path $ScriptDir "..\..\..")
    ).Path
} else {
    $RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
}

$Spec = Join-Path $ScriptDir "v3d_campaign_spec.json"
$Validator = Join-Path $ScriptDir "validate_v3d_tooling.py"
$Prepare = Join-Path $ScriptDir "prepare_v3d_materializations.py"
$Runner = Join-Path $ScriptDir "run_v3d_campaign.py"

function Invoke-Python {
    param([string]$Script, [string[]]$Arguments)

    if ((Split-Path -Leaf $Python) -eq "py") {
        & $Python -3 $Script @Arguments
    } else {
        & $Python $Script @Arguments
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Python command failed with exit code $LASTEXITCODE`: $Script"
    }
}

function Invoke-Validate {
    Invoke-Python $Validator @(
        "--repo-root", $RepoRoot,
        "--spec", $Spec
    )
    Invoke-Python $Prepare @(
        "--mode", "policy-test",
        "--repo-root", $RepoRoot,
        "--spec", $Spec
    )
}

function Invoke-Prepare {
    $Args = @(
        "--mode", "all",
        "--repo-root", $RepoRoot,
        "--spec", $Spec,
        "--intas-root", $IntasRoot
    )
    if (-not [string]::IsNullOrWhiteSpace($ScenarioConvert)) {
        $Args += @("--scenario-convert", $ScenarioConvert)
    }
    Invoke-Python $Prepare $Args
}

function Invoke-DryRun {
    Invoke-Python $Runner @(
        "--mode", "dry-run",
        "--repo-root", $RepoRoot,
        "--spec", $Spec,
        "--mosaic-root", $MosaicRoot
    )
}

function Invoke-SelfTest {
    Invoke-Python $Runner @(
        "--mode", "self-test",
        "--repo-root", $RepoRoot,
        "--spec", $Spec,
        "--mosaic-root", $MosaicRoot
    )
}

function Invoke-Ready {
    Invoke-Python $Runner @(
        "--mode", "ready",
        "--repo-root", $RepoRoot,
        "--spec", $Spec,
        "--mosaic-root", $MosaicRoot
    )
}

switch ($Mode) {
    "Validate" {
        Invoke-Validate
    }
    "Prepare" {
        Invoke-Validate
        Invoke-Prepare
    }
    "DryRun" {
        Invoke-DryRun
    }
    "SelfTest" {
        Invoke-SelfTest
    }
    "Ready" {
        Invoke-Ready
    }
    "PrepareAll" {
        Invoke-Validate
        Invoke-Prepare
        Invoke-DryRun
        Invoke-SelfTest
        Invoke-Ready
        Write-Host "PASS_V3D_CAMPAIGN_PREPARED_AND_READY"
    }
    "Execute" {
        $Arguments = @(
            "--mode", "execute",
            "--repo-root", $RepoRoot,
            "--spec", $Spec,
            "--mosaic-root", $MosaicRoot
        )
        if ((Split-Path -Leaf $Python) -eq "py") {
            & $Python -3 $Runner @Arguments
        } else {
            & $Python $Runner @Arguments
        }
        $Code = $LASTEXITCODE
        if ($Code -eq 0) {
            Write-Host "PASS_V3D_CAMPAIGN_BATCH_ALL_RUNS_PASSED"
        } elseif ($Code -eq 2) {
            Write-Warning "V3-D batch completed with recorded run failures; inspect group reports."
            Write-Host "PASS_V3D_CAMPAIGN_BATCH_COMPLETED_WITH_RECORDED_FAILURES"
        } else {
            throw "V3-D batch aborted with exit code $Code"
        }
    }
    "Report" {
        Invoke-Python $Runner @(
            "--mode", "report",
            "--repo-root", $RepoRoot,
            "--spec", $Spec,
            "--mosaic-root", $MosaicRoot
        )
    }
}
