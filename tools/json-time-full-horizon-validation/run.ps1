param(
    [Parameter(Mandatory = $true)]
    [string]$SnapshotFolder,

    [Parameter(Mandatory = $true)]
    [int]$SafetyMaxSteps,

    [Parameter(Mandatory = $true)]
    [string]$TraceOutFile,

    [Parameter(Mandatory = $true)]
    [string]$ValidationOutFile,

    [ValidateSet("OBSERVED_RUNTIME")]
    [string]$RuntimeProfile = "OBSERVED_RUNTIME",

    [string]$Phase10iValidationFile = ".\data\mosaic-study\diagnostics\phase_10i_validation.json",
    [string]$Phase10jPreValidationFile = ".\data\mosaic-study\diagnostics\phase_10j_pre_replay_bootstrap_validation.json",
    [string]$Phase10jPre2ValidationFile = ".\data\mosaic-study\diagnostics\phase_10j_pre2_optional_gateway_reporting_validation.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ClassesDir = Join-Path $ToolRoot "out/classes"
$LibDir = Join-Path $RepoRoot "out/codex-lib"
$PreferredJava = Join-Path $env:ProgramFiles "Java/jdk-21/bin/java.exe"
$Java = if (Test-Path $PreferredJava) { $PreferredJava } else { "java" }

if (-not (Test-Path $ClassesDir)) {
    throw "Missing compiled classes. Run tools/json-time-full-horizon-validation/build.ps1 first."
}
if (-not (Test-Path $SnapshotFolder)) {
    throw "Missing snapshot folder: $SnapshotFolder"
}
if ($SafetyMaxSteps -lt 1) {
    throw "SafetyMaxSteps must be >= 1."
}
foreach ($PathToCheck in @($Phase10iValidationFile, $Phase10jPreValidationFile, $Phase10jPre2ValidationFile)) {
    if (-not (Test-Path $PathToCheck)) {
        throw "Missing validation prerequisite: $PathToCheck"
    }
}

$Jars = @(Get-ChildItem -Path $LibDir -Filter "*.jar" | Sort-Object Name)
if ($Jars.Count -eq 0) {
    throw "No jars found in $LibDir"
}

$Classpath = (@($ClassesDir) + $Jars.FullName) -join ";"

Write-Host "JSON_TIME full horizon validation run"
Write-Host "snapshotFolder=$SnapshotFolder"
Write-Host "runtimeProfile=$RuntimeProfile"
Write-Host "safetyMaxSteps=$SafetyMaxSteps"
Write-Host "traceOutFile=$TraceOutFile"
Write-Host "validationOutFile=$ValidationOutFile"
Write-Host "phase10iValidationFile=$Phase10iValidationFile"
Write-Host "phase10jPreValidationFile=$Phase10jPreValidationFile"
Write-Host "phase10jPre2ValidationFile=$Phase10jPre2ValidationFile"
Write-Host "classpath=$Classpath"
Write-Host "java=$Java"
& $Java -version
& $Java -cp $Classpath JsonTimeFullHorizonValidationMain `
    $SnapshotFolder `
    $RuntimeProfile `
    $SafetyMaxSteps `
    $TraceOutFile `
    $ValidationOutFile `
    $Phase10iValidationFile `
    $Phase10jPreValidationFile `
    $Phase10jPre2ValidationFile
