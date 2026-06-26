param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$OutputRoot = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$HarnessRoot = $PSScriptRoot
$ToolRoot = Split-Path -Parent $HarnessRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$RuntimeOut = Join-Path $ToolRoot "out"
$RuntimeJar = Join-Path $RuntimeOut "maga-live-maga-runtime.jar"
$ClasspathDir = Join-Path $RuntimeOut "classpath"

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputRoot = Join-Path $RepoRoot (
        "tmp\local-cpu-contention\offline-harness-$timestamp"
    )
}
elseif (-not [IO.Path]::IsPathRooted($OutputRoot)) {
    $OutputRoot = Join-Path $RepoRoot $OutputRoot
}

$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
$HarnessClasses = Join-Path $OutputRoot "classes"
$G02BRun = Join-Path $OutputRoot "g02b-regression"

function Invoke-NativeCaptured {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$StdoutPath,
        [Parameter(Mandatory = $true)][string]$StderrPath
    )

    foreach ($path in @($StdoutPath, $StderrPath)) {
        $parent = Split-Path -Parent $path
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
        if (Test-Path -LiteralPath $path) {
            Remove-Item -LiteralPath $path -Force
        }
    }

    $process = Start-Process `
        -FilePath $FilePath `
        -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $StdoutPath `
        -RedirectStandardError $StderrPath `
        -Wait `
        -PassThru `
        -NoNewWindow

    $stdout = if (Test-Path -LiteralPath $StdoutPath) {
        [IO.File]::ReadAllText($StdoutPath)
    }
    else {
        ""
    }
    $stderr = if (Test-Path -LiteralPath $StderrPath) {
        [IO.File]::ReadAllText($StderrPath)
    }
    else {
        ""
    }

    if (-not [string]::IsNullOrWhiteSpace($stdout)) {
        Write-Host $stdout.TrimEnd()
    }
    if (-not [string]::IsNullOrWhiteSpace($stderr)) {
        Write-Host $stderr.TrimEnd()
    }

    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Stdout = $stdout
        Stderr = $stderr
    }
}

if (-not (Test-Path -LiteralPath $RuntimeJar -PathType Leaf)) {
    throw "Runtime JAR non trovato. Eseguire prima tools\mosaic-live-maga-runtime\build.ps1."
}
if (-not (Test-Path -LiteralPath $ClasspathDir -PathType Container)) {
    throw "Classpath runtime non trovato: $ClasspathDir"
}

foreach ($commandName in @("javac", "java")) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        throw "$commandName non trovato nel PATH."
    }
}

if (Test-Path -LiteralPath $OutputRoot) {
    Remove-Item -LiteralPath $OutputRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $HarnessClasses -Force | Out-Null
New-Item -ItemType Directory -Path $G02BRun -Force | Out-Null

$jars = @(
    Get-ChildItem -LiteralPath $ClasspathDir -Filter "*.jar" -File |
        Sort-Object FullName
)
$compileClasspath = @(
    (Resolve-Path -LiteralPath $RuntimeJar).Path
) + @($jars | ForEach-Object { $_.FullName })
$classpath = $compileClasspath -join [IO.Path]::PathSeparator

$localHarnessSource = Join-Path $HarnessRoot "LocalCpuContentionHarness.java"
$g02bHarnessSource = Join-Path $HarnessRoot "G02BExperimentalVariantHarness.java"

foreach ($source in @($localHarnessSource, $g02bHarnessSource)) {
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Sorgente harness non trovato: $source"
    }
}

$compileArgs = @(
    "--release", "17",
    "-cp", $classpath,
    "-d", $HarnessClasses,
    $localHarnessSource,
    $g02bHarnessSource
)
$compile = Invoke-NativeCaptured `
    -FilePath (Get-Command javac).Source `
    -Arguments $compileArgs `
    -WorkingDirectory $RepoRoot `
    -StdoutPath (Join-Path $OutputRoot "javac.stdout.txt") `
    -StderrPath (Join-Path $OutputRoot "javac.stderr.txt")

if ($compile.ExitCode -ne 0) {
    throw "Compilazione harness fallita con exit code $($compile.ExitCode)."
}

$runClasspath = @(
    $HarnessClasses,
    (Resolve-Path -LiteralPath $RuntimeJar).Path
) + @($jars | ForEach-Object { $_.FullName })
$runClasspathText = $runClasspath -join [IO.Path]::PathSeparator

$localRun = Invoke-NativeCaptured `
    -FilePath (Get-Command java).Source `
    -Arguments @(
        "-cp",
        $runClasspathText,
        "ga.fitness.local.LocalCpuContentionHarness"
    ) `
    -WorkingDirectory $RepoRoot `
    -StdoutPath (Join-Path $OutputRoot "local-contention.stdout.txt") `
    -StderrPath (Join-Path $OutputRoot "local-contention.stderr.txt")

if ($localRun.ExitCode -ne 0) {
    throw "LocalCpuContentionHarness fallito con exit code $($localRun.ExitCode)."
}
if ($localRun.Stdout -notmatch "LOCAL_CPU_CONTENTION_HARNESS_PASSED") {
    throw "Marker LOCAL_CPU_CONTENTION_HARNESS_PASSED assente."
}

$g02bRunResult = Invoke-NativeCaptured `
    -FilePath (Get-Command java).Source `
    -Arguments @(
        "-cp",
        $runClasspathText,
        "org.eclipse.mosaic.app.maga.liveruntime.G02BExperimentalVariantHarness",
        $G02BRun
    ) `
    -WorkingDirectory $RepoRoot `
    -StdoutPath (Join-Path $OutputRoot "g02b-regression.stdout.txt") `
    -StderrPath (Join-Path $OutputRoot "g02b-regression.stderr.txt")

if ($g02bRunResult.ExitCode -ne 0) {
    throw "G02BExperimentalVariantHarness fallito con exit code $($g02bRunResult.ExitCode)."
}
if ($g02bRunResult.Stdout -notmatch "G02B_EXPERIMENTAL_VARIANT_HARNESS_PASSED") {
    throw "Marker G02B_EXPERIMENTAL_VARIANT_HARNESS_PASSED assente."
}

$summary = [ordered]@{
    status = "PASS"
    runtimeJar = $RuntimeJar
    runtimeJarSha256 = (
        Get-FileHash -LiteralPath $RuntimeJar -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    runtimeJarSizeBytes = (Get-Item -LiteralPath $RuntimeJar).Length
    localContentionHarness = "PASS"
    g02bVariantRegressionHarness = "PASS"
    outputRoot = $OutputRoot
    generatedAt = (Get-Date).ToString("o")
}

$summary |
    ConvertTo-Json -Depth 4 |
    Set-Content `
        -LiteralPath (Join-Path $OutputRoot "harness_summary.json") `
        -Encoding UTF8

Write-Host ""
Write-Host "LOCAL_CPU_CONTENTION_OFFLINE_HARNESSES_PASSED"
Write-Host "Local contention harness: PASS"
Write-Host "G02B variant regression: PASS"
Write-Host "Runtime JAR SHA256: $($summary.runtimeJarSha256)"
Write-Host "Output root: $OutputRoot"
