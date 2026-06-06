param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2"
)

$ErrorActionPreference = "Stop"

$ToolRoot = Split-Path -Parent $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$HarnessSource = Join-Path $PSScriptRoot "LiveSeededPoissonWorkloadGeneratorHarness.java"
$SourceRoot = Join-Path $ToolRoot "src"
$OutRoot = Join-Path $ToolRoot "out"
$HarnessClasses = Join-Path $OutRoot "phase14c3-harness-classes"
$SourcesFile = Join-Path $OutRoot "phase14c3-harness-sources.txt"

if (-not (Test-Path -LiteralPath $ResolvedMosaicRoot -PathType Container)) {
    throw "MOSAIC root not found: $ResolvedMosaicRoot"
}
if (-not (Test-Path -LiteralPath $HarnessSource -PathType Leaf)) {
    throw "Harness source not found: $HarnessSource"
}
foreach ($CommandName in @("javac", "java")) {
    if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
        throw "$CommandName not found in PATH"
    }
}

$JarDependencyPaths = @(
    "lib\mosaic\mosaic-application-25.2.jar",
    "lib\mosaic\mosaic-objects-25.2.jar",
    "lib\mosaic\mosaic-geomath-25.2.jar",
    "lib\mosaic\mosaic-interactions-25.2.jar",
    "lib\mosaic\mosaic-utils-25.2.jar",
    "lib\mosaic\mosaic-rti-api-25.2.jar",
    "lib\third-party\gson-2.10.1.jar",
    "lib\third-party\slf4j-api-2.0.12.jar"
)

$JarDependencies = foreach ($DependencyPath in $JarDependencyPaths) {
    $FullPath = Join-Path $ResolvedMosaicRoot $DependencyPath
    if (-not (Test-Path -LiteralPath $FullPath -PathType Leaf)) {
        throw "Required JAR dependency not found: $FullPath"
    }
    (Get-Item -LiteralPath $FullPath).FullName
}

if (Test-Path -LiteralPath $HarnessClasses) {
    Remove-Item -LiteralPath $HarnessClasses -Recurse -Force
}
New-Item -ItemType Directory -Path $HarnessClasses -Force | Out-Null
New-Item -ItemType Directory -Path $OutRoot -Force | Out-Null

$SourceFiles = @()
$SourceFiles += Get-ChildItem -LiteralPath $SourceRoot -Recurse -Filter "*.java" -File | ForEach-Object { $_.FullName }
$SourceFiles += $HarnessSource

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($SourcesFile, [string[]]$SourceFiles, $Utf8NoBom)

$Classpath = ($JarDependencies) -join [IO.Path]::PathSeparator
Write-Host "Compiling Phase 14C.3 harness..."
& javac -cp $Classpath -d $HarnessClasses "@$SourcesFile"
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$RunClasspath = @($HarnessClasses) + $JarDependencies
$RunClasspathText = ($RunClasspath) -join [IO.Path]::PathSeparator
Write-Host "Running Phase 14C.3 harness..."
& java -cp $RunClasspathText org.eclipse.mosaic.app.maga.livestate.LiveSeededPoissonWorkloadGeneratorHarness
if ($LASTEXITCODE -ne 0) {
    throw "Harness failed with exit code $LASTEXITCODE"
}
