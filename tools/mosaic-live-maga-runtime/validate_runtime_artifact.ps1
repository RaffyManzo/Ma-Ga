param(
    [Parameter(Mandatory = $true)]
    [string]$RuntimeJarPath,

    [Parameter(Mandatory = $true)]
    [string]$ExpectedSha256,

    [long]$ExpectedSizeBytes = 0,

    [string]$ReportPath = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ExpectedClasses = @(
    "org/eclipse/mosaic/app/maga/liveruntime/MaGaLiveRuntimeCoordinatorApp.class",
    "org/eclipse/mosaic/app/maga/liveruntime/MaGaLiveMosaicSnapshotBridge.class",
    "org/eclipse/mosaic/app/maga/liveruntime/LiveGaExecutionCoordinator.class",
    "org/eclipse/mosaic/app/maga/liveruntime/LiveGaExecutionState.class",
    "org/eclipse/mosaic/app/maga/liveruntime/LiveStrategyApplier.class",
    "org/eclipse/mosaic/app/maga/livestate/LiveStateLayerRuntimeFacade.class",
    "window/source/MosaicSystemStateSource.class",
    "window/core/TemporalWindowManager.class",
    "ga/core/MaGaOptimizer.class"
)

function Resolve-MaybeRelative {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "RuntimeJarPath must not be blank"
    }
    if ([IO.Path]::IsPathRooted($Path)) {
        return (Resolve-Path -LiteralPath $Path).Path
    }
    return (Resolve-Path -LiteralPath (Join-Path (Get-Location).Path $Path)).Path
}

$Errors = New-Object System.Collections.Generic.List[string]
$ResolvedJar = $null
$ActualSize = 0L
$ActualSha = ""
$SizeMatch = $false
$ShaMatch = $false
$JarTfStatus = "FAIL"
$ZipOpenStatus = "FAIL"
$EntryCount = 0
$MissingExpectedEntries = @()
$ExpectedEntriesPresent = $false

try {
    $ResolvedJar = Resolve-MaybeRelative -Path $RuntimeJarPath
    if (-not (Test-Path -LiteralPath $ResolvedJar -PathType Leaf)) {
        throw "Runtime JAR not found or not a file: $ResolvedJar"
    }

    $Item = Get-Item -LiteralPath $ResolvedJar
    $ActualSize = $Item.Length
    $ActualSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $ResolvedJar).Hash.ToLowerInvariant()
    $ExpectedShaLower = $ExpectedSha256.ToLowerInvariant()
    $ShaMatch = $ActualSha -eq $ExpectedShaLower
    $SizeMatch = ($ExpectedSizeBytes -le 0) -or ($ActualSize -eq $ExpectedSizeBytes)

    $JarExe = (Get-Command jar -ErrorAction Stop).Source
    $JarEntries = @(& $JarExe tf $ResolvedJar)
    if ($LASTEXITCODE -eq 0 -and $JarEntries.Count -gt 0) {
        $JarTfStatus = "PASS"
    }
    else {
        $Errors.Add("jar tf failed or returned no entries")
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $Archive = [System.IO.Compression.ZipFile]::OpenRead($ResolvedJar)
    try {
        $ZipEntries = @($Archive.Entries | ForEach-Object { $_.FullName })
        $EntryCount = $ZipEntries.Count
        if ($EntryCount -gt 0) {
            $ZipOpenStatus = "PASS"
        }
        else {
            $Errors.Add("ZipArchive OpenRead returned no entries")
        }
    }
    finally {
        $Archive.Dispose()
    }

    $EntrySet = if ($ZipOpenStatus -eq "PASS") { $ZipEntries } else { $JarEntries }
    $MissingExpectedEntries = @($ExpectedClasses | Where-Object { $_ -notin $EntrySet })
    $ExpectedEntriesPresent = $MissingExpectedEntries.Count -eq 0

    if (-not $ShaMatch) {
        $Errors.Add("SHA-256 mismatch")
    }
    if (-not $SizeMatch) {
        $Errors.Add("Size mismatch")
    }
    if (-not $ExpectedEntriesPresent) {
        $Errors.Add("Missing expected runtime entries")
    }
}
catch {
    $Errors.Add($_.Exception.Message)
}

$ValidationStatus = if (
    $Errors.Count -eq 0 -and
    $ShaMatch -and
    $SizeMatch -and
    $JarTfStatus -eq "PASS" -and
    $ZipOpenStatus -eq "PASS" -and
    $ExpectedEntriesPresent
) { "PASS" } else { "FAIL" }

$Report = [ordered]@{
    timestampUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    runtimeJarPath = $ResolvedJar
    filename = if ($ResolvedJar) { Split-Path -Leaf $ResolvedJar } else { $null }
    expectedSizeBytes = $ExpectedSizeBytes
    actualSizeBytes = $ActualSize
    sizeMatch = $SizeMatch
    expectedSha256 = $ExpectedSha256.ToLowerInvariant()
    actualSha256 = $ActualSha
    sha256Match = $ShaMatch
    jarTfStatus = $JarTfStatus
    zipOpenStatus = $ZipOpenStatus
    entryCount = $EntryCount
    expectedEntries = $ExpectedClasses
    missingExpectedEntries = $MissingExpectedEntries
    expectedEntriesPresent = $ExpectedEntriesPresent
    freshBuildExecuted = $false
    validationStatus = $ValidationStatus
    classification = if ($ValidationStatus -eq "PASS") {
        "RECOVERED_RUNTIME_ARTIFACT_VALIDATED"
    }
    else {
        "RECOVERED_RUNTIME_ARTIFACT_VALIDATION_FAILED"
    }
    errors = @($Errors)
}

if (-not [string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportDir = Split-Path -Parent $ReportPath
    if (-not [string]::IsNullOrWhiteSpace($ReportDir)) {
        New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
    }
    $Report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $ReportPath -Encoding UTF8
}

Write-Host "Runtime artifact validation status: $ValidationStatus"
Write-Host "Runtime artifact SHA-256: $ActualSha"
Write-Host "Runtime artifact size bytes: $ActualSize"
Write-Host "Runtime artifact fresh build executed: false"

if ($ValidationStatus -ne "PASS") {
    foreach ($ErrorMessage in $Errors) {
        Write-Host "Validation error: $ErrorMessage"
    }
    exit 1
}

exit 0
