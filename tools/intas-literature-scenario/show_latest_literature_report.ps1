param(
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiteratureBasedUrbanStudy",
    [switch]$PrintSummary
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)

function Resolve-RepoPath {
    param([string]$Path)
    if ([IO.Path]::IsPathRooted($Path)) {
        return (Resolve-Path -LiteralPath $Path).Path
    }
    return (Resolve-Path -LiteralPath (Join-Path $RepoRoot $Path)).Path
}

$ResolvedMosaicRoot = Resolve-RepoPath -Path $MosaicRoot
$LogsRoot = Join-Path $ResolvedMosaicRoot "logs"

if (-not (Test-Path -LiteralPath $LogsRoot -PathType Container)) {
    throw "MOSAIC logs directory not found: $LogsRoot"
}

$LatestRun = Get-ChildItem -LiteralPath $LogsRoot -Directory |
    Where-Object { $_.Name -like "*-$ScenarioName" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $LatestRun) {
    throw "No MOSAIC run found for scenario $ScenarioName under $LogsRoot"
}

$RuntimeDir = Join-Path $LatestRun.FullName "live-maga-runtime"
$SummaryJson = Join-Path $RuntimeDir "live_run_summary.json"
$SummaryMarkdown = Join-Path $RuntimeDir "live_run_summary.md"
$SmokeJson = Join-Path $RuntimeDir "literature_smoke_validation.json"
$SmokeMarkdown = Join-Path $RuntimeDir "literature_smoke_validation.md"
$DetailedTxt = Join-Path $RuntimeDir "live-reporting\live_detailed_execution_report.txt"
$DetailedMarkdown = Join-Path $RuntimeDir "live-reporting\live_detailed_execution_report.md"
$DetailedJson = Join-Path $RuntimeDir "live-reporting\live_detailed_execution_report.json"

Write-Host "LATEST_LITERATURE_RUN"
Write-Host "Run name: $($LatestRun.Name)"
Write-Host "Run directory: $($LatestRun.FullName)"
Write-Host "Summary JSON: $SummaryJson"
Write-Host "Summary Markdown: $SummaryMarkdown"
Write-Host "Smoke validation JSON: $SmokeJson"
Write-Host "Smoke validation Markdown: $SmokeMarkdown"
Write-Host "Detailed report TXT: $DetailedTxt"
Write-Host "Detailed report Markdown: $DetailedMarkdown"
Write-Host "Detailed report JSON: $DetailedJson"

if ($PrintSummary) {
    if (Test-Path -LiteralPath $SummaryMarkdown -PathType Leaf) {
        "`n===== LIVE SUMMARY =====`n"
        Get-Content -LiteralPath $SummaryMarkdown
    }
    if (Test-Path -LiteralPath $SmokeMarkdown -PathType Leaf) {
        "`n===== SMOKE VALIDATION =====`n"
        Get-Content -LiteralPath $SmokeMarkdown
    }
}
