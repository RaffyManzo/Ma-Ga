param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$OutputRoot = ""
)
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$ToolRoot = Split-Path -Parent $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $ToolRoot "out\v3c-freshness-budget-harness"
}
$Jar = Join-Path $ToolRoot "out\maga-live-maga-runtime.jar"
$ClasspathDir = Join-Path $ToolRoot "out\classpath"
$Classes = Join-Path $OutputRoot "classes"
$Source = Join-Path $PSScriptRoot "V3CFreshnessBudgetHarness.java"
if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) { throw "Runtime JAR missing: $Jar" }
if (Test-Path -LiteralPath $OutputRoot) { Remove-Item -LiteralPath $OutputRoot -Recurse -Force }
New-Item -ItemType Directory -Path $Classes -Force | Out-Null
$Jars = @(Get-ChildItem -LiteralPath $ClasspathDir -Filter "*.jar" -File | Sort-Object FullName | ForEach-Object FullName)
$Cp = (@($Jar) + $Jars) -join [IO.Path]::PathSeparator
& javac -encoding UTF-8 -cp $Cp -d $Classes $Source
if ($LASTEXITCODE -ne 0) { throw "V3-C harness compilation failed: $LASTEXITCODE" }
$RunCp = (@($Classes, $Jar) + $Jars) -join [IO.Path]::PathSeparator
$HarnessRun = Join-Path $OutputRoot "run"
New-Item -ItemType Directory -Path $HarnessRun -Force | Out-Null
& java -cp $RunCp `
    org.eclipse.mosaic.app.maga.liveruntime.V3CFreshnessBudgetHarness `
    $HarnessRun
if ($LASTEXITCODE -ne 0) { throw "V3-C harness failed: $LASTEXITCODE" }
@(
    "status=V3C_FRESHNESS_BUDGET_HARNESS_PASSED"
    "jarSha256=$((Get-FileHash -LiteralPath $Jar -Algorithm SHA256).Hash)"
) | Set-Content -LiteralPath (Join-Path $OutputRoot "summary.txt") -Encoding UTF8
Write-Host "V3C_FRESHNESS_BUDGET_HARNESS_PASSED"
