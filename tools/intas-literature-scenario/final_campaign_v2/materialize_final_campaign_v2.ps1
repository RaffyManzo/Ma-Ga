param(
    [ValidateSet("check", "archive", "pilot", "all", "materialization", "audit", "repair-canonical-metadata", "repair-bandwidth-serialization")]
    [string]$Mode = "check",

    [string]$MaterializationId = "",
    [string]$Python = "py",
    [string]$RepoRoot = "",
    [string]$Spec = "",
    [string]$IntasRoot = "C:\Users\raffa\IdeaProjects\external\InTAS",
    [string]$ScenarioConvert = "",
    [switch]$StopOnFailure
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Script = Join-Path $ScriptDir "materialize_final_campaign_v2.py"
if ([string]::IsNullOrWhiteSpace($Spec)) { $Spec = Join-Path $ScriptDir "final_campaign_v2_spec.json" }
if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $ScriptDir "..\..\..")).Path
}

if ((Split-Path -Leaf $Python) -eq "py" -or (Split-Path -Leaf $Python) -eq "py.exe") {
    $ArgsList = @("-3.12", $Script, "--mode", $Mode)
} else {
    $ArgsList = @($Script, "--mode", $Mode)
}
$ArgsList += @("--repo-root", $RepoRoot, "--spec", $Spec, "--intas-root", $IntasRoot)
if (-not [string]::IsNullOrWhiteSpace($MaterializationId)) { $ArgsList += @("--materialization-id", $MaterializationId) }
if (-not [string]::IsNullOrWhiteSpace($ScenarioConvert)) { $ArgsList += @("--scenario-convert", $ScenarioConvert) }
if ($StopOnFailure) { $ArgsList += "--stop-on-failure" }

& $Python @ArgsList
exit $LASTEXITCODE
