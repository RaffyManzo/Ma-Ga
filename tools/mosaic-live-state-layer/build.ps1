param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2"
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$VersionedScenario = Join-Path $RepoRoot "data\mosaic-scenarios\MaGaLiveStateLayerStudy"
$ScenarioApplicationDir = Join-Path $VersionedScenario "application"
$SourceRoot = Join-Path $ToolRoot "src"
$OutRoot = Join-Path $ToolRoot "out"
$ClassesDir = Join-Path $OutRoot "classes"
$ClasspathDir = Join-Path $OutRoot "classpath"
$JarFile = Join-Path $OutRoot "maga-live-state-layer.jar"
$SourcesFile = Join-Path $OutRoot "sources.txt"

if (-not (Test-Path -LiteralPath $ResolvedMosaicRoot -PathType Container)) {
    throw "MOSAIC root not found: $ResolvedMosaicRoot"
}
if (-not (Test-Path -LiteralPath $ScenarioApplicationDir -PathType Container)) {
    throw "Live state scenario application directory not found: $ScenarioApplicationDir"
}
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac not found in PATH"
}
if (-not (Get-Command jar -ErrorAction SilentlyContinue)) {
    throw "jar not found in PATH"
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

$OriginalJarDependencies = foreach ($DependencyPath in $JarDependencyPaths) {
    $FullPath = Join-Path $ResolvedMosaicRoot $DependencyPath
    if (-not (Test-Path -LiteralPath $FullPath -PathType Leaf)) {
        throw "Required MOSAIC JAR dependency not found: $FullPath"
    }
    Get-Item -LiteralPath $FullPath
}

$SourceFiles = Get-ChildItem -LiteralPath $SourceRoot -Recurse -Filter "*.java" -File
if ($SourceFiles.Count -eq 0) {
    throw "No Java source files found under $SourceRoot"
}

if (Test-Path -LiteralPath $OutRoot) {
    Remove-Item -LiteralPath $OutRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $ClassesDir | Out-Null
New-Item -ItemType Directory -Path $ClasspathDir | Out-Null

$JarDependencies = foreach ($Dependency in $OriginalJarDependencies) {
    $CopiedDependency = Join-Path $ClasspathDir $Dependency.Name
    Copy-Item -LiteralPath $Dependency.FullName -Destination $CopiedDependency -Force
    Get-Item -LiteralPath $CopiedDependency
}

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines(
    $SourcesFile,
    [string[]]($SourceFiles | ForEach-Object { $_.FullName }),
    $Utf8NoBom
)

$Classpath = ($JarDependencies | ForEach-Object { $_.FullName }) -join [IO.Path]::PathSeparator

Write-Host "JDK javac: $((Get-Command javac).Source)"
Write-Host "JDK jar: $((Get-Command jar).Source)"
Write-Host "MOSAIC root: $ResolvedMosaicRoot"
Write-Host "Compiling MaGa live state layer classes..."
Write-Host "Classpath:"
foreach ($Dependency in $JarDependencies) {
    Write-Host "  $($Dependency.FullName)"
}

& javac -cp $Classpath -d $ClassesDir "@$SourcesFile"
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$ExpectedClassFiles = @(
    "org\eclipse\mosaic\app\maga\livestate\MaGaLiveStateConfig.class",
    "org\eclipse\mosaic\app\maga\livestate\MaGaLiveVehicleStateApp.class",
    "org\eclipse\mosaic\app\maga\livestate\MaGaLiveStateCoordinatorApp.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveVehicleState.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveTaskState.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveTaskStatus.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveStateCache.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveStateSnapshotView.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveStaticInfrastructureCatalog.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveLocalCandidatePreview.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveV2vCandidatePreview.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveV2vBandwidthPoolPreview.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveLocalAndV2vCandidatePreviewBuilder.class"
)
foreach ($ExpectedClassFile in $ExpectedClassFiles) {
    $ClassPath = Join-Path $ClassesDir $ExpectedClassFile
    if (-not (Test-Path -LiteralPath $ClassPath -PathType Leaf)) {
        throw "Expected compiled class missing: $ClassPath"
    }
}

Write-Host "Creating JAR $JarFile..."
& jar cf $JarFile -C $ClassesDir .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

$JarEntries = & jar tf $JarFile
if ($LASTEXITCODE -ne 0) {
    throw "jar tf failed with exit code $LASTEXITCODE"
}
foreach ($ExpectedClassFile in $ExpectedClassFiles) {
    $ExpectedJarEntry = $ExpectedClassFile.Replace("\", "/")
    if (-not ($JarEntries -contains $ExpectedJarEntry)) {
        throw "Expected JAR entry missing: $ExpectedJarEntry"
    }
}

$ScenarioJar = Join-Path $ScenarioApplicationDir "maga-live-state-layer.jar"
Copy-Item -LiteralPath $JarFile -Destination $ScenarioJar -Force
Write-Host "Copied live state layer JAR to versioned scenario: $ScenarioJar"
Write-Host "Build completed: $JarFile"
