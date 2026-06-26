param(
    [string]$OutputRoot = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$HarnessRoot = $PSScriptRoot
$ToolRoot = Split-Path -Parent $HarnessRoot
$RuntimeOut = Join-Path $ToolRoot "out"
$RuntimeJar = Join-Path $RuntimeOut "maga-live-maga-runtime.jar"
$ClasspathDir = Join-Path $RuntimeOut "classpath"
$HarnessOut = Join-Path $RuntimeOut "local-contention-telemetry-harness"
$HarnessClasses = Join-Path $HarnessOut "classes"

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $HarnessOut "run"
}

if (-not (Test-Path -LiteralPath $RuntimeJar -PathType Leaf)) {
    throw "Runtime JAR not found. Run build.ps1 first."
}
if (-not (Test-Path -LiteralPath $ClasspathDir -PathType Container)) {
    throw "Runtime classpath directory not found. Run build.ps1 first."
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
New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null

$Jars = Get-ChildItem -LiteralPath $ClasspathDir -Filter "*.jar" -File
$CompileClasspath = @(
    (Resolve-Path -LiteralPath $RuntimeJar).Path
) + ($Jars | ForEach-Object { $_.FullName })
$Classpath = $CompileClasspath -join [IO.Path]::PathSeparator

$HarnessSource = Join-Path `
    $HarnessRoot `
    "LocalCpuContentionTelemetryHarness.java"

& javac -cp $Classpath -d $HarnessClasses $HarnessSource
if ($LASTEXITCODE -ne 0) {
    throw "javac telemetry harness failed with exit code $LASTEXITCODE"
}

$RunClasspath = @(
    $HarnessClasses,
    (Resolve-Path -LiteralPath $RuntimeJar).Path
) + ($Jars | ForEach-Object { $_.FullName })

& java `
    -cp ($RunClasspath -join [IO.Path]::PathSeparator) `
    org.eclipse.mosaic.app.maga.liveruntime.reporting.LocalCpuContentionTelemetryHarness `
    $OutputRoot
if ($LASTEXITCODE -ne 0) {
    throw "telemetry harness failed with exit code $LASTEXITCODE"
}
