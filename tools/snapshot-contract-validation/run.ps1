param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ClassesDir = Join-Path $ToolRoot "out/classes"
$FixturesDir = Join-Path $ToolRoot "fixtures"
$LibDir = Join-Path $RepoRoot "out/codex-lib"
$PreferredJava = Join-Path $env:ProgramFiles "Java/jdk-21/bin/java.exe"
$Java = if (Test-Path $PreferredJava) { $PreferredJava } else { "java" }

if (-not (Test-Path $ClassesDir)) {
    throw "Missing compiled classes. Run tools/snapshot-contract-validation/build.ps1 first."
}
if (-not (Test-Path $FixturesDir)) {
    throw "Missing fixtures directory: $FixturesDir"
}

$Jars = @(Get-ChildItem -Path $LibDir -Filter "*.jar" | Sort-Object Name)
if ($Jars.Count -eq 0) {
    throw "No jars found in $LibDir"
}

$Classpath = (@($ClassesDir) + $Jars.FullName) -join ";"

Write-Host "Snapshot contract validation run"
Write-Host "fixturesDir=$FixturesDir"
Write-Host "classpath=$Classpath"
Write-Host "java=$Java"
& $Java -version
& $Java -cp $Classpath SnapshotContractValidationMain $FixturesDir
