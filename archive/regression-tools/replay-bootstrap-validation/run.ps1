param(
    [string]$SnapshotFolder = ".\data\snapshots\mosaic-generated",
    [string]$Phase10iValidationFile = ".\data\mosaic-study\diagnostics\phase_10i_validation.json",
    [string]$ValidationOutFile = ".\data\mosaic-study\diagnostics\phase_10j_pre2_optional_gateway_reporting_validation.json"
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
    throw "Missing compiled classes. Run tools/replay-bootstrap-validation/build.ps1 first."
}
if (-not (Test-Path $SnapshotFolder)) {
    throw "Missing snapshot folder: $SnapshotFolder"
}
if (-not (Test-Path $Phase10iValidationFile)) {
    throw "Missing Phase 10I validation file: $Phase10iValidationFile"
}

$Jars = @(Get-ChildItem -Path $LibDir -Filter "*.jar" | Sort-Object Name)
if ($Jars.Count -eq 0) {
    throw "No jars found in $LibDir"
}

$Classpath = (@($ClassesDir) + $Jars.FullName) -join ";"

Write-Host "Replay bootstrap validation run"
Write-Host "snapshotFolder=$SnapshotFolder"
Write-Host "phase10iValidationFile=$Phase10iValidationFile"
Write-Host "validationOutFile=$ValidationOutFile"
Write-Host "classpath=$Classpath"
Write-Host "java=$Java"
& $Java -version
& $Java -cp $Classpath ReplayBootstrapValidationMain `
    $SnapshotFolder `
    $Phase10iValidationFile `
    $ValidationOutFile
