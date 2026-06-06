param(
    [string[]]$ScenarioIds = @(
        "MaGaIntegratedStudy",
        "MaGaIntegratedStudyRequest2x",
        "MaGaIntegratedStudyResponse2x",
        "MaGaIntegratedStudyFrequency2x"
    )
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$MosaicRoot = Join-Path $RepoRoot "tmp\mosaic-25.2"
$VersionedScenarioRoot = Join-Path $RepoRoot "data\mosaic-scenarios"
$DeployScript = Join-Path $RepoRoot "tools\deploy-mosaic-scenarios.ps1"
$SourceRoot = Join-Path $ToolRoot "src"
$OutRoot = Join-Path $ToolRoot "out"
$ClassesDir = Join-Path $OutRoot "classes"
$ClasspathDir = Join-Path $OutRoot "classpath"
$JarFile = Join-Path $OutRoot "maga-cell-traffic-diagnostic.jar"
$SourcesFile = Join-Path $OutRoot "sources.txt"

if (-not (Test-Path -LiteralPath $MosaicRoot -PathType Container)) {
    throw "MOSAIC root not found: $MosaicRoot"
}

if (-not (Test-Path -LiteralPath $VersionedScenarioRoot -PathType Container)) {
    throw "Versioned scenario root not found: $VersionedScenarioRoot"
}

if (-not (Test-Path -LiteralPath $DeployScript -PathType Leaf)) {
    throw "Deploy script not found: $DeployScript"
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
    "lib\mosaic\mosaic-interactions-25.2.jar",
    "lib\mosaic\mosaic-utils-25.2.jar",
    "lib\mosaic\mosaic-rti-api-25.2.jar",
    "lib\third-party\slf4j-api-2.0.12.jar"
)
$OriginalJarDependencies = foreach ($DependencyPath in $JarDependencyPaths) {
    $FullPath = Join-Path $MosaicRoot $DependencyPath
    if (-not (Test-Path -LiteralPath $FullPath -PathType Leaf)) {
        throw "Required MOSAIC JAR dependency not found: $FullPath"
    }
    Get-Item -LiteralPath $FullPath
}

$SourceFiles = Get-ChildItem -LiteralPath $SourceRoot -Recurse -Filter "*.java" -File
if ($SourceFiles.Count -eq 0) {
    throw "No Java source files found under $SourceRoot"
}

foreach ($ScenarioId in $ScenarioIds) {
    $ScenarioApplicationDir = Join-Path $VersionedScenarioRoot "$ScenarioId\application"
    if (-not (Test-Path -LiteralPath $ScenarioApplicationDir -PathType Container)) {
        throw "Versioned scenario application directory not found: $ScenarioApplicationDir"
    }
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

Write-Host "Compiling MaGa Cell traffic diagnostic classes..."
& javac -cp $Classpath -d $ClassesDir "@$SourcesFile"
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$ExpectedClassFiles = @(
    "org\eclipse\mosaic\app\maga\celltraffic\MaGaCellTrafficDiagnosticConfig.class",
    "org\eclipse\mosaic\app\maga\celltraffic\MaGaCellTrafficDiagnosticMessage.class",
    "org\eclipse\mosaic\app\maga\celltraffic\MaGaCellTrafficDiagnosticResponseMessage.class",
    "org\eclipse\mosaic\app\maga\celltraffic\MaGaCellTrafficDiagnosticServerApp.class",
    "org\eclipse\mosaic\app\maga\celltraffic\MaGaCellTrafficDiagnosticVehicleApp.class"
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

foreach ($ScenarioId in $ScenarioIds) {
    $ScenarioApplicationDir = Join-Path $VersionedScenarioRoot "$ScenarioId\application"
    Copy-Item -LiteralPath $JarFile -Destination (Join-Path $ScenarioApplicationDir "maga-cell-traffic-diagnostic.jar") -Force
    Write-Host "Copied JAR to versioned scenario $ScenarioApplicationDir"
}

Write-Host "Deploying versioned diagnostic scenarios to local MOSAIC..."
& $DeployScript -ScenarioIds $ScenarioIds
if ($LASTEXITCODE -ne 0) {
    throw "Deploy script failed with exit code $LASTEXITCODE"
}

foreach ($ScenarioId in $ScenarioIds) {
    $LocalJar = Join-Path $MosaicRoot "scenarios\$ScenarioId\application\maga-cell-traffic-diagnostic.jar"
    if (-not (Test-Path -LiteralPath $LocalJar -PathType Leaf)) {
        throw "Diagnostic JAR missing from deployed scenario: $LocalJar"
    }
    if ((Get-Item -LiteralPath $LocalJar).Length -le 0) {
        throw "Diagnostic JAR is empty in deployed scenario: $LocalJar"
    }
    Write-Host "Verified deployed JAR: $LocalJar"
}

Write-Host "Build completed: $JarFile"
