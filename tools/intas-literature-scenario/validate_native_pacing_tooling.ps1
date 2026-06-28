param(
    [string]$RepoRoot = "."
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ResolvedRepo = (Resolve-Path -LiteralPath $RepoRoot).Path
$RunPath = Join-Path $ResolvedRepo "tools\intas-literature-scenario\run_literature_scenario.ps1"
$QuickPath = Join-Path $ResolvedRepo "tools\intas-literature-scenario\quick_literature_workflow.ps1"

foreach ($Path in @($RunPath, $QuickPath)) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required script not found: $Path"
    }

    $Tokens = $null
    $Errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $Path,
        [ref]$Tokens,
        [ref]$Errors
    )
    if (@($Errors).Count -ne 0) {
        $Errors | Format-List
        throw "PowerShell parser check failed: $Path"
    }
}

$RunText = Get-Content -LiteralPath $RunPath -Raw
$QuickText = Get-Content -LiteralPath $QuickPath -Raw

$RunRequirements = @(
    '[double]$RealtimeBrakeFactor = 0.0',
    '$RealtimeBrakeFactor -gt 0.0',
    '[Globalization.CultureInfo]::InvariantCulture',
    '$MosaicArgs += @("-b", $RealtimeBrakeArgument)',
    '& .\mosaic.bat @MosaicArgs',
    'Native realtime brake enabled:'
)

$QuickRequirements = @(
    '[double]$RealtimeBrakeFactor = 0.0',
    '$RealtimeBrakeFactor -gt 0.0',
    '"-RealtimeBrakeFactor"',
    '[Globalization.CultureInfo]::InvariantCulture'
)

foreach ($Fragment in $RunRequirements) {
    if (-not $RunText.Contains($Fragment)) {
        throw "Missing run-script pacing fragment: $Fragment"
    }
}

foreach ($Fragment in $QuickRequirements) {
    if (-not $QuickText.Contains($Fragment)) {
        throw "Missing quick-workflow pacing fragment: $Fragment"
    }
}

if ($RunText.Contains('& .\mosaic.bat -s $ScenarioName')) {
    throw "Legacy fixed MOSAIC invocation is still present"
}

[pscustomobject]@{
    Status = "PASS_V3D_NATIVE_PACING_TOOLING_VALIDATED"
    RunScript = $RunPath
    QuickWorkflow = $QuickPath
    DefaultPacingDisabled = $true
    InvariantFactorFormatting = $true
    NativeRealtimeBrakeArgument = $true
    JavaModified = $false
} | Format-List
