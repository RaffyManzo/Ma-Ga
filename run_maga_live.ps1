param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiveMagaRuntimeStudy",
    [ValidateSet("normal", "validate-bridge")]
    [string]$Mode = "normal",
    [switch]$SkipBuild,
    [switch]$SkipDeploy,
    [string]$ConfigurationId = "",
    [string]$Seed = "",
    [string]$Replicate = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RepoRoot = $PSScriptRoot
$RuntimeTool = Join-Path $RepoRoot "tools\mosaic-live-maga-runtime"
$BuildScript = Join-Path $RuntimeTool "build.ps1"
$DeployScript = Join-Path $RuntimeTool "deploy.ps1"
$RunScript = Join-Path $RuntimeTool "run.ps1"
$ValidateScript = Join-Path $RuntimeTool "validate.ps1"
$SummaryScript = Join-Path $RuntimeTool "summarize-run.ps1"

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

function Assert-CalibrationParametersNotYetSupported {
    if (-not [string]::IsNullOrWhiteSpace($ConfigurationId) -or
            -not [string]::IsNullOrWhiteSpace($Seed) -or
            -not [string]::IsNullOrWhiteSpace($Replicate)) {
        throw "NOT_YET_SUPPORTED_UNTIL_CALIBRATION: -ConfigurationId, -Seed and -Replicate will be enabled when calibrated scenario mappings are authoritative."
    }
}

function Get-LatestRunName {
    param([string]$ResolvedMosaicRoot, [string]$Scenario)
    $LogsRoot = Join-Path $ResolvedMosaicRoot "logs"
    if (-not (Test-Path -LiteralPath $LogsRoot -PathType Container)) {
        return $null
    }
    $Run = Get-ChildItem -LiteralPath $LogsRoot -Directory |
        Where-Object { $_.Name -like "*-$Scenario" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $Run) {
        return $null
    }
    return $Run.Name
}

function Invoke-Build {
    if ($SkipBuild) {
        Write-Host "Skipping build"
        return
    }
    & powershell -NoProfile -ExecutionPolicy Bypass `
        -File $BuildScript `
        -MosaicRoot $MosaicRoot `
        -ScenarioName $ScenarioName
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed with exit code $LASTEXITCODE"
    }
}

function Invoke-Deploy {
    if ($SkipDeploy) {
        Write-Host "Skipping deploy"
        return
    }
    & powershell -NoProfile -ExecutionPolicy Bypass `
        -File $DeployScript `
        -MosaicRoot $MosaicRoot `
        -ScenarioName $ScenarioName
    if ($LASTEXITCODE -ne 0) {
        throw "Deploy failed with exit code $LASTEXITCODE"
    }
}

function Invoke-LiveRun {
    param([ValidateSet("normal", "diagnostic-overrun")] [string]$Profile)
    & powershell -NoProfile -ExecutionPolicy Bypass `
        -File $RunScript `
        -MosaicRoot $MosaicRoot `
        -ScenarioName $ScenarioName `
        -Profile $Profile
    if ($LASTEXITCODE -ne 0) {
        throw "MOSAIC run failed with exit code $LASTEXITCODE"
    }
}

Assert-SafeScenarioName -Name $ScenarioName
Assert-CalibrationParametersNotYetSupported

foreach ($Script in @($BuildScript, $DeployScript, $RunScript, $ValidateScript, $SummaryScript)) {
    if (-not (Test-Path -LiteralPath $Script -PathType Leaf)) {
        throw "Required script missing: $Script"
    }
}

$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$RunBefore = Get-LatestRunName -ResolvedMosaicRoot $ResolvedMosaicRoot -Scenario $ScenarioName

Invoke-Build
Invoke-Deploy

if ($Mode -eq "normal") {
    Invoke-LiveRun -Profile "normal"
    $RunAfter = Get-LatestRunName -ResolvedMosaicRoot $ResolvedMosaicRoot -Scenario $ScenarioName
    if ([string]::IsNullOrWhiteSpace($RunAfter) -or $RunAfter -eq $RunBefore) {
        throw "Unable to identify the new MOSAIC run for scenario $ScenarioName"
    }
    & powershell -NoProfile -ExecutionPolicy Bypass `
        -File $SummaryScript `
        -MosaicRoot $MosaicRoot `
        -ScenarioName $ScenarioName `
        -RunName $RunAfter
    if ($LASTEXITCODE -ne 0) {
        throw "Run summary failed with exit code $LASTEXITCODE"
    }
    return
}

Invoke-LiveRun -Profile "normal"
$NormalRun = Get-LatestRunName -ResolvedMosaicRoot $ResolvedMosaicRoot -Scenario $ScenarioName
& powershell -NoProfile -ExecutionPolicy Bypass `
    -File $SummaryScript `
    -MosaicRoot $MosaicRoot `
    -ScenarioName $ScenarioName `
    -RunName $NormalRun
if ($LASTEXITCODE -ne 0) {
    throw "Normal run summary failed with exit code $LASTEXITCODE"
}

Invoke-LiveRun -Profile "diagnostic-overrun"
& powershell -NoProfile -ExecutionPolicy Bypass `
    -File $ValidateScript `
    -MosaicRoot $MosaicRoot `
    -ScenarioName $ScenarioName
if ($LASTEXITCODE -ne 0) {
    throw "Bridge validation failed with exit code $LASTEXITCODE"
}

$Diagnostics = Join-Path $RepoRoot "data\mosaic-study\diagnostics\phase_13e_live_bridge_end_to_end_validation.json"
$Result = Get-Content -LiteralPath $Diagnostics -Raw | ConvertFrom-Json
Write-Host "Bridge validation diagnostics: $Diagnostics"
Write-Host "phase13eStatus=$($Result.phase13eStatus)"
Write-Host "phase13Status=$($Result.phase13Status)"
Write-Host "readyForCalibration=$($Result.readyForCalibration)"
