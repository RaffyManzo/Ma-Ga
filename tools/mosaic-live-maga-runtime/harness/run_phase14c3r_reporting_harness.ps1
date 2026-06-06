param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$HarnessRoot = $PSScriptRoot
$ToolRoot = Split-Path -Parent $HarnessRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$RuntimeOut = Join-Path $ToolRoot "out"
$RuntimeJar = Join-Path $RuntimeOut "maga-live-maga-runtime.jar"
$ClasspathDir = Join-Path $RuntimeOut "classpath"
$HarnessOut = Join-Path $RuntimeOut "phase14c3r-harness"
$HarnessClasses = Join-Path $HarnessOut "classes"
$HarnessRun = Join-Path $HarnessOut "run"

if (-not (Test-Path -LiteralPath $RuntimeJar -PathType Leaf)) {
    throw "Runtime JAR not found. Run tools\mosaic-live-maga-runtime\build.ps1 first."
}
if (-not (Test-Path -LiteralPath $ClasspathDir -PathType Container)) {
    throw "Runtime classpath directory not found. Run tools\mosaic-live-maga-runtime\build.ps1 first."
}
foreach ($CommandName in @("javac", "java")) {
    if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
        throw "$CommandName not found in PATH"
    }
}

if (Test-Path -LiteralPath $HarnessOut) {
    Remove-Item -LiteralPath $HarnessOut -Recurse -Force
}
New-Item -ItemType Directory -Path $HarnessClasses -Force | Out-Null
New-Item -ItemType Directory -Path $HarnessRun -Force | Out-Null

$Jars = Get-ChildItem -LiteralPath $ClasspathDir -Filter "*.jar" -File
$CompileClasspath = @((Resolve-Path -LiteralPath $RuntimeJar).Path) + ($Jars | ForEach-Object { $_.FullName })
$Classpath = $CompileClasspath -join [IO.Path]::PathSeparator

$HarnessSource = Join-Path $HarnessRoot "LiveNativeDetailedReportingHarness.java"
& javac -cp $Classpath -d $HarnessClasses $HarnessSource
if ($LASTEXITCODE -ne 0) {
    throw "javac harness failed with exit code $LASTEXITCODE"
}

$RunClasspath = @($HarnessClasses, (Resolve-Path -LiteralPath $RuntimeJar).Path) + ($Jars | ForEach-Object { $_.FullName })
& java -cp ($RunClasspath -join [IO.Path]::PathSeparator) `
    org.eclipse.mosaic.app.maga.liveruntime.reporting.LiveNativeDetailedReportingHarness `
    $HarnessRun
if ($LASTEXITCODE -ne 0) {
    throw "reporting harness failed with exit code $LASTEXITCODE"
}
