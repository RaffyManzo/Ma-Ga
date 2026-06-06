param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ClassesDir = Join-Path $ToolRoot "out/classes"
$SourcesList = Join-Path $ToolRoot "out/sources.txt"
$LibDir = Join-Path $RepoRoot "out/codex-lib"
$PreferredJavac = Join-Path $env:ProgramFiles "Java/jdk-21/bin/javac.exe"
$Javac = if (Test-Path $PreferredJavac) { $PreferredJavac } else { "javac" }

if (-not (Test-Path $LibDir)) {
    throw "Missing Jackson library directory: $LibDir"
}

$Jars = @(Get-ChildItem -Path $LibDir -Filter "*.jar" | Sort-Object Name)
if ($Jars.Count -eq 0) {
    throw "No jars found in $LibDir"
}

if (Test-Path $ClassesDir) {
    Remove-Item -LiteralPath $ClassesDir -Recurse -Force
}
New-Item -ItemType Directory -Force $ClassesDir | Out-Null
New-Item -ItemType Directory -Force (Split-Path -Parent $SourcesList) | Out-Null

$ProjectSources = @(Get-ChildItem -Path (Join-Path $RepoRoot "src") -Recurse -Filter "*.java" |
    Sort-Object FullName)
$HarnessSources = @(Get-ChildItem -Path (Join-Path $ToolRoot "src") -Recurse -Filter "*.java" |
    Sort-Object FullName)

if ($ProjectSources.Count -eq 0) {
    throw "No Java project sources found."
}
if ($HarnessSources.Count -eq 0) {
    throw "No Java harness sources found."
}

@($ProjectSources.FullName + $HarnessSources.FullName) |
    Set-Content -Path $SourcesList -Encoding ASCII

$Classpath = ($Jars.FullName -join ";")

Write-Host "Replay bootstrap validation build"
Write-Host "repoRoot=$RepoRoot"
Write-Host "classesDir=$ClassesDir"
Write-Host "classpath=$Classpath"
Write-Host "projectSources=$($ProjectSources.Count)"
Write-Host "harnessSources=$($HarnessSources.Count)"
Write-Host "javac=$Javac"
& $Javac -version
& $Javac -encoding UTF-8 -cp $Classpath -d $ClassesDir "@$SourcesList"
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}
Write-Host "buildStatus=COMPLETED"
