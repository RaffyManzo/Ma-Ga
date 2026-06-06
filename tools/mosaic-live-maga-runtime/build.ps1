param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiveMagaRuntimeStudy"
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$SafeRepoRoot = $RepoRoot.Replace("\", "/")
$ResolvedMosaicRoot = (Resolve-Path -LiteralPath (Join-Path $RepoRoot $MosaicRoot)).Path
$RuntimeSourceRoot = Join-Path $ToolRoot "src"
$StateLayerSourceRoot = Join-Path $RepoRoot "tools\mosaic-live-state-layer\src"
$CoreSourceRoot = Join-Path $RepoRoot "src"
$OutRoot = Join-Path $ToolRoot "out"
$ClassesDir = Join-Path $OutRoot "classes"
$ClasspathDir = Join-Path $OutRoot "classpath"
$SourcesFile = Join-Path $OutRoot "sources.txt"
$JarFile = Join-Path $OutRoot "maga-live-maga-runtime.jar"

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
Assert-SafeScenarioName -Name $ScenarioName

if (-not (Test-Path -LiteralPath $ResolvedMosaicRoot -PathType Container)) {
    throw "MOSAIC root not found: $ResolvedMosaicRoot"
}
foreach ($CommandName in @("javac", "jar")) {
    if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
        throw "$CommandName not found in PATH"
    }
}

$DependencyJarPaths = @(
    "lib\mosaic\mosaic-application-25.2.jar",
    "lib\mosaic\mosaic-objects-25.2.jar",
    "lib\mosaic\mosaic-geomath-25.2.jar",
    "lib\mosaic\mosaic-interactions-25.2.jar",
    "lib\mosaic\mosaic-utils-25.2.jar",
    "lib\mosaic\mosaic-rti-api-25.2.jar",
    "lib\third-party\gson-2.10.1.jar",
    "lib\third-party\slf4j-api-2.0.12.jar",
    "lib\third-party\jackson-annotations-2.16.1.jar",
    "lib\third-party\jackson-core-2.16.1.jar",
    "lib\third-party\jackson-databind-2.16.1.jar"
)
$DependencyJars = foreach ($DependencyPath in $DependencyJarPaths) {
    $FullPath = Join-Path $ResolvedMosaicRoot $DependencyPath
    if (-not (Test-Path -LiteralPath $FullPath -PathType Leaf)) {
        throw "Required dependency not found: $FullPath"
    }
    Get-Item -LiteralPath $FullPath
}

$SourceFiles = @()
$SourceFiles += Get-ChildItem -LiteralPath $CoreSourceRoot -Recurse -Filter "*.java" -File
$SourceFiles += Get-ChildItem -LiteralPath $StateLayerSourceRoot -Recurse -Filter "*.java" -File
$SourceFiles += Get-ChildItem -LiteralPath $RuntimeSourceRoot -Recurse -Filter "*.java" -File
if ($SourceFiles.Count -eq 0) {
    throw "No Java source files found"
}

if (Test-Path -LiteralPath $OutRoot) {
    Remove-Item -LiteralPath $OutRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $ClassesDir -Force | Out-Null
New-Item -ItemType Directory -Path $ClasspathDir -Force | Out-Null
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines(
    $SourcesFile,
    [string[]]($SourceFiles | ForEach-Object { $_.FullName }),
    $Utf8NoBom
)

$CopiedDependencyJars = foreach ($DependencyJar in $DependencyJars) {
    $Destination = Join-Path $ClasspathDir $DependencyJar.Name
    Copy-Item -LiteralPath $DependencyJar.FullName -Destination $Destination -Force
    Get-Item -LiteralPath $Destination
}

$Classpath = ($CopiedDependencyJars | ForEach-Object { $_.FullName }) -join [IO.Path]::PathSeparator
Write-Host "JDK javac: $((Get-Command javac).Source)"
Write-Host "JDK jar: $((Get-Command jar).Source)"
Write-Host "MOSAIC root: $ResolvedMosaicRoot"
Write-Host "Compiling core, live state layer, and live MA-GA runtime..."
Write-Host "Source counts:"
Write-Host "  core=$((Get-ChildItem -LiteralPath $CoreSourceRoot -Recurse -Filter '*.java' -File).Count)"
Write-Host "  live-state-layer=$((Get-ChildItem -LiteralPath $StateLayerSourceRoot -Recurse -Filter '*.java' -File).Count)"
Write-Host "  live-maga-runtime=$((Get-ChildItem -LiteralPath $RuntimeSourceRoot -Recurse -Filter '*.java' -File).Count)"

& javac -cp $Classpath -d $ClassesDir "@$SourcesFile"
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$ExpectedClassFiles = @(
    "org\eclipse\mosaic\app\maga\liveruntime\MaGaLiveRuntimeCoordinatorApp.class",
    "org\eclipse\mosaic\app\maga\liveruntime\MaGaLiveMosaicSnapshotBridge.class",
    "org\eclipse\mosaic\app\maga\liveruntime\LiveGaExecutionCoordinator.class",
    "org\eclipse\mosaic\app\maga\liveruntime\LiveGaExecutionState.class",
    "org\eclipse\mosaic\app\maga\liveruntime\LiveStrategyApplier.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveStateLayerRuntimeFacade.class",
    "window\source\MosaicSystemStateSource.class",
    "window\core\TemporalWindowManager.class",
    "ga\core\MaGaOptimizer.class"
)
foreach ($ExpectedClassFile in $ExpectedClassFiles) {
    $ClassPath = Join-Path $ClassesDir $ExpectedClassFile
    if (-not (Test-Path -LiteralPath $ClassPath -PathType Leaf)) {
        throw "Expected compiled class missing: $ClassPath"
    }
}

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

foreach ($ProtectedPath in @(
    "tools/mosaic-live-state-layer",
    "src"
)) {
    $Status = git -c safe.directory="$SafeRepoRoot" status --short $ProtectedPath
    if ($LASTEXITCODE -ne 0) {
        throw "git status failed for $ProtectedPath"
    }
    if (-not [string]::IsNullOrWhiteSpace(($Status | Out-String).Trim())) {
        throw "Protected path modified after build: $ProtectedPath"
    }
}

Write-Host "Build completed: $JarFile"
Write-Host "Generated JAR is intentionally kept out of versioned scenarios."
