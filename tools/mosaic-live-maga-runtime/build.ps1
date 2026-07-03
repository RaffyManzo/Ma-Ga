param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiveMagaRuntimeStudy",
    [string]$ExternalBuildRoot = "",
    [switch]$AllowDirtyCoreSource
)

$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$SafeRepoRoot = $RepoRoot.Replace("\", "/")

function Resolve-MaybeRelativeToRepo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "Path must not be blank"
    }

    $Candidate = if ([IO.Path]::IsPathRooted($Path)) {
        $Path
    }
    else {
        Join-Path $RepoRoot $Path
    }

    return (Resolve-Path -LiteralPath $Candidate).Path
}

$ResolvedMosaicRoot = Resolve-MaybeRelativeToRepo -Path $MosaicRoot
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

function Resolve-FullPathForBuild {
    param([string]$Path)
    if ([IO.Path]::IsPathRooted($Path)) {
        return [IO.Path]::GetFullPath($Path)
    }
    return [IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Path))
}

function Assert-ExternalBuildRoot {
    param([string]$BuildRoot)
    $RepoFull = [IO.Path]::GetFullPath($RepoRoot).TrimEnd("\") + "\"
    $BuildFull = [IO.Path]::GetFullPath($BuildRoot).TrimEnd("\")
    $MosaicFull = [IO.Path]::GetFullPath($ResolvedMosaicRoot).TrimEnd("\")
    if ($BuildFull.Equals($RepoFull.TrimEnd("\"), [StringComparison]::OrdinalIgnoreCase) -or
            ($BuildFull + "\").StartsWith($RepoFull, [StringComparison]::OrdinalIgnoreCase)) {
        throw "ExternalBuildRoot must be outside the repository root: $BuildFull"
    }
    if ($BuildFull.Equals($MosaicFull, [StringComparison]::OrdinalIgnoreCase)) {
        throw "ExternalBuildRoot must not coincide with the MOSAIC root: $BuildFull"
    }
    if ((Test-Path -LiteralPath $BuildFull -PathType Container) -and
            (Get-ChildItem -LiteralPath $BuildFull -Force | Select-Object -First 1)) {
        throw "ExternalBuildRoot must be empty or absent: $BuildFull"
    }
}

function Resolve-RelativePath {
    param([string]$Base, [string]$Path)
    $BaseFull = [IO.Path]::GetFullPath($Base).TrimEnd("\") + "\"
    $PathFull = [IO.Path]::GetFullPath($Path)
    $BaseUri = [Uri]::new($BaseFull)
    $PathUri = [Uri]::new($PathFull)
    return [Uri]::UnescapeDataString($BaseUri.MakeRelativeUri($PathUri).ToString()).Replace("/", "\")
}

function Copy-SourceTree {
    param(
        [string]$SourceRoot,
        [string]$DestinationRoot
    )
    $Count = 0
    New-Item -ItemType Directory -Path $DestinationRoot -Force | Out-Null
    foreach ($SourceFile in Get-ChildItem -LiteralPath $SourceRoot -Recurse -Filter "*.java" -File) {
        $RelativePath = Resolve-RelativePath -Base $SourceRoot -Path $SourceFile.FullName
        $Destination = Join-Path $DestinationRoot $RelativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $Destination) -Force | Out-Null
        Copy-Item -LiteralPath $SourceFile.FullName -Destination $Destination -Force
        $Count += 1
    }
    return $Count
}

function Invoke-NativeCaptured {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory,
        [string]$StdoutPath,
        [string]$StderrPath
    )
    if (Test-Path -LiteralPath $StdoutPath) {
        Remove-Item -LiteralPath $StdoutPath -Force
    }
    if (Test-Path -LiteralPath $StderrPath) {
        Remove-Item -LiteralPath $StderrPath -Force
    }
    $Process = Start-Process `
        -FilePath $FilePath `
        -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $StdoutPath `
        -RedirectStandardError $StderrPath `
        -Wait `
        -PassThru `
        -NoNewWindow
    $StdoutText = if (Test-Path -LiteralPath $StdoutPath) { [IO.File]::ReadAllText($StdoutPath) } else { "" }
    $StderrText = if (Test-Path -LiteralPath $StderrPath) { [IO.File]::ReadAllText($StderrPath) } else { "" }
    if (-not [string]::IsNullOrWhiteSpace($StdoutText)) {
        Write-Host $StdoutText.TrimEnd()
    }
    if (-not [string]::IsNullOrWhiteSpace($StderrText)) {
        # Native tools such as javac write warnings and informational notes
        # to stderr even when they complete successfully. Surface the captured
        # text through the host output so a parent PowerShell process using
        # ErrorActionPreference=Stop does not abort before checking the exit code.
        Write-Host $StderrText.TrimEnd()
    }
    return [pscustomobject]@{
        ExitCode = $Process.ExitCode
        Stdout = $StdoutText
        Stderr = $StderrText
        Combined = "$StdoutText`n$StderrText"
    }
}

function Assert-NoJavacInternalFailure {
    param([string]$Output)
    if ($Output -match "AccessDeniedException") {
        throw "javac output contains AccessDeniedException"
    }
    if ($Output -match "An exception has occurred in the compiler") {
        throw "javac output contains an internal compiler exception"
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
$JavacExe = (Get-Command javac).Source
$JarExe = (Get-Command jar).Source

$Timestamp = Get-Date -Format "yyyyMMddHHmmssfff"
if ([string]::IsNullOrWhiteSpace($ExternalBuildRoot)) {
    $BuildStagingRoot = Join-Path $env:TEMP "maga-live-maga-runtime-build-$PID-$Timestamp"
} else {
    $BuildStagingRoot = Resolve-FullPathForBuild -Path $ExternalBuildRoot
}
$BuildStagingRoot = [IO.Path]::GetFullPath($BuildStagingRoot)
Assert-ExternalBuildRoot -BuildRoot $BuildStagingRoot

$StagingSourceRoot = Join-Path $BuildStagingRoot "source"
$StagingCoreSourceRoot = Join-Path $StagingSourceRoot "core"
$StagingStateLayerSourceRoot = Join-Path $StagingSourceRoot "live-state-layer"
$StagingRuntimeSourceRoot = Join-Path $StagingSourceRoot "live-runtime"
$StagingBuildRoot = Join-Path $BuildStagingRoot "build"
$StagingClassesDir = Join-Path $StagingBuildRoot "classes"
$StagingClasspathDir = Join-Path $StagingBuildRoot "classpath"
$StagingSourcesFile = Join-Path $StagingBuildRoot "sources.txt"
$StagingJarFile = Join-Path $StagingBuildRoot "maga-live-maga-runtime.jar"
$JavacStdoutFile = Join-Path $StagingBuildRoot "javac.stdout.txt"
$JavacStderrFile = Join-Path $StagingBuildRoot "javac.stderr.txt"
$JarStdoutFile = Join-Path $StagingBuildRoot "jar.stdout.txt"
$JarStderrFile = Join-Path $StagingBuildRoot "jar.stderr.txt"
$PublishRoot = Join-Path $ToolRoot "out.publish-$PID-$Timestamp"

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

$ExpectedClassFiles = @(
    "org\eclipse\mosaic\app\maga\liveruntime\MaGaLiveRuntimeCoordinatorApp.class",
    "org\eclipse\mosaic\app\maga\liveruntime\MaGaLiveMosaicSnapshotBridge.class",
    "org\eclipse\mosaic\app\maga\liveruntime\LiveGaExecutionCoordinator.class",
    "org\eclipse\mosaic\app\maga\liveruntime\LiveGaExecutionState.class",
    "org\eclipse\mosaic\app\maga\liveruntime\LiveStrategyApplier.class",
    "org\eclipse\mosaic\app\maga\livestate\LiveStateLayerRuntimeFacade.class",
    "window\source\MosaicSystemStateSource.class",
    "window\core\TemporalWindowManager.class",
    "ga\core\MaGaOptimizer.class",
    "ga\core\GaExecutionBudget.class",
    "ga\fitness\FitnessEvaluationContext.class",
    "ga\fitness\local\LocalCpuContentionEvaluator.class",
    "org\eclipse\mosaic\app\maga\liveruntime\LiveStaleReason.class",
    "org\eclipse\mosaic\app\maga\liveruntime\LiveAssignmentDecision.class",
    "org\eclipse\mosaic\app\maga\liveruntime\reporting\LiveStaleStrategyWriter.class",
    "org\eclipse\mosaic\app\maga\liveruntime\reporting\LiveStaleStrategyWriter`$1.class"
)

try {
    New-Item -ItemType Directory -Path $StagingClassesDir -Force | Out-Null
    New-Item -ItemType Directory -Path $StagingClasspathDir -Force | Out-Null

    $CopiedSourceCount = 0
    $CopiedSourceCount += Copy-SourceTree -SourceRoot $CoreSourceRoot -DestinationRoot $StagingCoreSourceRoot
    $CopiedSourceCount += Copy-SourceTree -SourceRoot $StateLayerSourceRoot -DestinationRoot $StagingStateLayerSourceRoot
    $CopiedSourceCount += Copy-SourceTree -SourceRoot $RuntimeSourceRoot -DestinationRoot $StagingRuntimeSourceRoot
    if ($CopiedSourceCount -ne $SourceFiles.Count) {
        throw "Copied source count mismatch: copied=$CopiedSourceCount original=$($SourceFiles.Count)"
    }

    $StagingSourceFiles = Get-ChildItem -LiteralPath $StagingSourceRoot -Recurse -Filter "*.java" -File
    if ($StagingSourceFiles.Count -ne $SourceFiles.Count) {
        throw "Staging source count mismatch: staging=$($StagingSourceFiles.Count) original=$($SourceFiles.Count)"
    }

    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines(
        $StagingSourcesFile,
        [string[]]($StagingSourceFiles | ForEach-Object { $_.FullName }),
        $Utf8NoBom
    )

    $CopiedDependencyJars = foreach ($DependencyJar in $DependencyJars) {
        $Destination = Join-Path $StagingClasspathDir $DependencyJar.Name
        Copy-Item -LiteralPath $DependencyJar.FullName -Destination $Destination -Force
        Get-Item -LiteralPath $Destination
    }

    $Classpath = ($CopiedDependencyJars | ForEach-Object { $_.FullName }) -join [IO.Path]::PathSeparator
    Write-Host "JDK javac: $JavacExe"
    Write-Host "JDK jar: $JarExe"
    Write-Host "MOSAIC root: $ResolvedMosaicRoot"
    Write-Host "External build root: $BuildStagingRoot"
    Write-Host "Compiling core, live state layer, and live MA-GA runtime in external staging..."
    Write-Host "Source counts:"
    Write-Host "  core=$((Get-ChildItem -LiteralPath $CoreSourceRoot -Recurse -Filter '*.java' -File).Count)"
    Write-Host "  live-state-layer=$((Get-ChildItem -LiteralPath $StateLayerSourceRoot -Recurse -Filter '*.java' -File).Count)"
    Write-Host "  live-maga-runtime=$((Get-ChildItem -LiteralPath $RuntimeSourceRoot -Recurse -Filter '*.java' -File).Count)"

    $JavacResult = Invoke-NativeCaptured `
        -FilePath $JavacExe `
        -Arguments @("-cp", $Classpath, "-d", $StagingClassesDir, "@$StagingSourcesFile") `
        -WorkingDirectory $StagingBuildRoot `
        -StdoutPath $JavacStdoutFile `
        -StderrPath $JavacStderrFile
    if ($JavacResult.ExitCode -ne 0) {
        throw "javac failed with exit code $($JavacResult.ExitCode)"
    }
    Assert-NoJavacInternalFailure -Output $JavacResult.Combined

    # Expected class count after the snapshot-scoped fitness evaluation context.
    # The context adds one class without changing fitness, repair, chromosome,
    # genetic operators or runtime policy.
    $ExpectedFrozenClassCount = 274
    $ActualClassCount = (Get-ChildItem -LiteralPath $StagingClassesDir -Recurse -Filter "*.class" -File).Count
    if ($ActualClassCount -ne $ExpectedFrozenClassCount) {
        throw "Unexpected class count: expected=$ExpectedFrozenClassCount actual=$ActualClassCount"
    }

    foreach ($ExpectedClassFile in $ExpectedClassFiles) {
        $ClassPath = Join-Path $StagingClassesDir $ExpectedClassFile
        if (-not (Test-Path -LiteralPath $ClassPath -PathType Leaf)) {
            throw "Expected compiled class missing: $ClassPath"
        }
    }

    $JarCreateResult = Invoke-NativeCaptured `
        -FilePath $JarExe `
        -Arguments @("cf", $StagingJarFile, "-C", $StagingClassesDir, ".") `
        -WorkingDirectory $StagingBuildRoot `
        -StdoutPath $JarStdoutFile `
        -StderrPath $JarStderrFile
    if ($JarCreateResult.ExitCode -ne 0) {
        throw "jar failed with exit code $($JarCreateResult.ExitCode)"
    }

    $JarEntries = & $JarExe tf $StagingJarFile
    if ($LASTEXITCODE -ne 0) {
        throw "jar tf failed with exit code $LASTEXITCODE"
    }
    foreach ($ExpectedClassFile in $ExpectedClassFiles) {
        $ExpectedJarEntry = $ExpectedClassFile.Replace("\", "/")
        if (-not ($JarEntries -contains $ExpectedJarEntry)) {
            throw "Expected JAR entry missing: $ExpectedJarEntry"
        }
    }

    New-Item -ItemType Directory -Path $PublishRoot -Force | Out-Null
    $PublishClassesDir = Join-Path $PublishRoot "classes"
    $PublishClasspathDir = Join-Path $PublishRoot "classpath"
    New-Item -ItemType Directory -Path $PublishClassesDir -Force | Out-Null
    New-Item -ItemType Directory -Path $PublishClasspathDir -Force | Out-Null
    Copy-Item -Path (Join-Path $StagingClassesDir "*") -Destination $PublishClassesDir -Recurse -Force
    Copy-Item -Path (Join-Path $StagingClasspathDir "*") -Destination $PublishClasspathDir -Recurse -Force
    [System.IO.File]::WriteAllLines(
        (Join-Path $PublishRoot "sources.txt"),
        [string[]]($SourceFiles | ForEach-Object { $_.FullName }),
        $Utf8NoBom
    )
    Copy-Item -LiteralPath $StagingJarFile -Destination (Join-Path $PublishRoot "maga-live-maga-runtime.jar") -Force

    $PublishedJarFile = Join-Path $PublishRoot "maga-live-maga-runtime.jar"
    if (-not (Test-Path -LiteralPath $PublishedJarFile -PathType Leaf)) {
        throw "Published JAR missing before out swap: $PublishedJarFile"
    }
    $PublishedJarEntries = & $JarExe tf $PublishedJarFile
    if ($LASTEXITCODE -ne 0) {
        throw "published jar tf failed with exit code $LASTEXITCODE"
    }
    foreach ($ExpectedClassFile in $ExpectedClassFiles) {
        $ExpectedJarEntry = $ExpectedClassFile.Replace("\", "/")
        if (-not ($PublishedJarEntries -contains $ExpectedJarEntry)) {
            throw "Expected published JAR entry missing: $ExpectedJarEntry"
        }
    }
    $PublishedClassCount = (Get-ChildItem -LiteralPath $PublishClassesDir -Recurse -Filter "*.class" -File).Count
    if ($PublishedClassCount -ne $ExpectedFrozenClassCount) {
        throw "Unexpected published class count: expected=$ExpectedFrozenClassCount actual=$PublishedClassCount"
    }

    if (Test-Path -LiteralPath $OutRoot) {
        Remove-Item -LiteralPath $OutRoot -Recurse -Force
    }
    Move-Item -LiteralPath $PublishRoot -Destination $OutRoot

    Remove-Item -LiteralPath $BuildStagingRoot -Recurse -Force
    Write-Host "External staging removed: $BuildStagingRoot"
} catch {
    if (Test-Path -LiteralPath $PublishRoot) {
        Remove-Item -LiteralPath $PublishRoot -Recurse -Force
    }
    if (Test-Path -LiteralPath $BuildStagingRoot) {
        Write-Host "External staging preserved for diagnostics: $BuildStagingRoot"
    }
    throw
}

if (-not $AllowDirtyCoreSource) {
    foreach ($ProtectedPath in @(
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
}
else {
    Write-Host "Dirty core source check intentionally bypassed for controlled review build."
}

Write-Host "Build completed: $JarFile"
Write-Host "Generated JAR is intentionally kept out of versioned scenarios."
