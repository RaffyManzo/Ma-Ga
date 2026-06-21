param(
    [ValidateSet("check", "archive", "pilot", "all", "materialization", "audit")]
    [string]$Mode = "check",

    [string]$MaterializationId = "",
    [string]$Python = "py",
    [string]$IntasRoot = "",
    [string]$ScenarioConvert = "",
    [switch]$StopOnFailure
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Script = Join-Path $ScriptDir "materialize_final_campaign.py"

if ((Split-Path -Leaf $Python) -eq "py") {
    $ArgsList = @("-3", $Script, "--mode", $Mode)
} else {
    $ArgsList = @($Script, "--mode", $Mode)
}

if ($MaterializationId -ne "") {
    $ArgsList += @("--materialization-id", $MaterializationId)
}

if ($IntasRoot -ne "") {
    $ArgsList += @("--intas-root", $IntasRoot)
}

if ($ScenarioConvert -ne "") {
    $ArgsList += @("--scenario-convert", $ScenarioConvert)
}

if ($StopOnFailure) {
    $ArgsList += "--stop-on-failure"
}

& $Python @ArgsList
exit $LASTEXITCODE
