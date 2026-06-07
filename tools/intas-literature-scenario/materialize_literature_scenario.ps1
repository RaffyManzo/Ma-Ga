param(
    [string]$IntasRoot = "C:\Users\raffa\IdeaProjects\external\InTAS",
    [string]$ScenarioConvert = "",
    [string]$PersistentRoot = ".\tmp\materialized-literature-scenarios",
    [string]$ScenarioName = "MaGaLiteratureBasedUrbanStudy",
    [ValidateSet("low_density", "nominal", "high_density")]
    [string]$Density = "nominal",
    [ValidateSet("smoke", "nominal", "extended")]
    [string]$DurationProfile = "smoke",
    [int]$Seed = 104729,
    [switch]$ForceRebuild
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ToolRoot = $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ToolRoot)
$ScenarioDbName = "intas_literature_urban.db"
$SubscenarioName = "intas_literature_urban"

function Assert-SafeName {
    param([string]$Name, [string]$Label)
    if ([string]::IsNullOrWhiteSpace($Name)) {
        throw "$Label must not be blank"
    }
    if ([IO.Path]::IsPathRooted($Name) -or
            $Name.Contains("..") -or
            $Name.Contains("\") -or
            $Name.Contains("/") -or
            -not ($Name -match "^[A-Za-z0-9_.-]+$")) {
        throw "Invalid ${Label}: $Name"
    }
}

function Resolve-RepoPath {
    param([string]$Path)
    if ([IO.Path]::IsPathRooted($Path)) {
        return (Resolve-Path -LiteralPath $Path).Path
    }
    return (Resolve-Path -LiteralPath (Join-Path $RepoRoot $Path)).Path
}

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-GitValue {
    param([string]$Path, [string[]]$Args)
    try {
        $Output = & git -C $Path @Args 2>$null
        if ($LASTEXITCODE -eq 0) {
            return (($Output | Out-String).Trim())
        }
    } catch {
    }
    return $null
}

function Find-ScenarioConvert {
    param([string]$ExplicitPath)
    $Candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        $Candidates += $ExplicitPath
    }
    if (-not [string]::IsNullOrWhiteSpace($env:SCENARIO_CONVERT)) {
        $Candidates += $env:SCENARIO_CONVERT
    }
    foreach ($CommandName in @("scenario-convert.bat", "scenario-convert", "scenario-convert.cmd")) {
        $Command = Get-Command $CommandName -ErrorAction SilentlyContinue
        if ($Command) {
            $Candidates += $Command.Source
        }
    }
    $DefaultBat = Join-Path $RepoRoot "tmp\external-tools\scenario-convert-25.2\scenario-convert.bat"
    if (Test-Path -LiteralPath $DefaultBat -PathType Leaf) {
        $Candidates += $DefaultBat
    }
    $TmpRoot = Join-Path $RepoRoot "tmp"
    if (Test-Path -LiteralPath $TmpRoot -PathType Container) {
        $Candidates += Get-ChildItem -LiteralPath $TmpRoot -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "scenario[-_]?convert.*\.(bat|cmd|ps1|sh|jar)$" } |
            ForEach-Object { $_.FullName }
    }
    foreach ($Candidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($Candidate)) {
            continue
        }
        $Resolved = if ([IO.Path]::IsPathRooted($Candidate)) {
            $Candidate
        } else {
            Join-Path $RepoRoot $Candidate
        }
        if (Test-Path -LiteralPath $Resolved -PathType Leaf) {
            return (Resolve-Path -LiteralPath $Resolved).Path
        }
    }
    throw "Scenario-Convert not found. Install MOSAIC Extended Scenario-Convert 25.2, or pass -ScenarioConvert <path>."
}

function Get-ScenarioConvertRoot {
    param([string]$ScenarioConvertPath)
    $Item = Get-Item -LiteralPath $ScenarioConvertPath
    if ($Item.Name -match "\.jar$") {
        if ($Item.Directory.Name -eq "tools") {
            return $Item.Directory.Parent.FullName
        }
        return $Item.Directory.FullName
    }
    return $Item.Directory.FullName
}

function Get-ScenarioConvertClasspath {
    param([string]$ScenarioConvertRoot)
    $ScenarioJar = Get-ChildItem -LiteralPath (Join-Path $ScenarioConvertRoot "tools") -Filter "scenario-convert-*.jar" -File |
        Sort-Object Name |
        Select-Object -First 1
    if ($null -eq $ScenarioJar) {
        throw "Scenario-Convert jar not found under $ScenarioConvertRoot\tools"
    }
    $ClasspathParts = @($ScenarioJar.FullName)
    foreach ($Relative in @("lib\mosaic", "lib\extended", "lib\third-party")) {
        $Dir = Join-Path $ScenarioConvertRoot $Relative
        if (Test-Path -LiteralPath $Dir -PathType Container) {
            $ClasspathParts += (Join-Path $Dir "*")
        }
    }
    return ($ClasspathParts -join [IO.Path]::PathSeparator)
}

function Invoke-ScenarioConvert {
    param(
        [string]$ScenarioConvertRoot,
        [string]$WorkingDirectory,
        [string[]]$Arguments
    )
    $Classpath = Get-ScenarioConvertClasspath -ScenarioConvertRoot $ScenarioConvertRoot
    Push-Location $WorkingDirectory
    try {
        Write-Host "Scenario-Convert: scenario-convert $($Arguments -join ' ')"
        & java -cp $Classpath com.dcaiti.mosaic.tools.scenarioconvert.core.Starter @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Scenario-Convert failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Get-ScenarioConvertHelp {
    param([string]$ScenarioConvertRoot)
    $Classpath = Get-ScenarioConvertClasspath -ScenarioConvertRoot $ScenarioConvertRoot
    $Output = & java -cp $Classpath com.dcaiti.mosaic.tools.scenarioconvert.core.Starter --help 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Scenario-Convert --help failed with exit code $LASTEXITCODE"
    }
    return ($Output | Out-String)
}

function Copy-DirectoryContents {
    param([string]$Source, [string]$Destination)
    if (-not (Test-Path -LiteralPath $Destination -PathType Container)) {
        New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    }
    Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse -Force
    }
}

function Set-JsonFile {
    param([string]$Path, [object]$Value)
    $Json = $Value | ConvertTo-Json -Depth 32
    [System.IO.File]::WriteAllText(
        [System.IO.Path]::GetFullPath($Path),
        $Json,
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Apply-SmokeExecutionOverrides {
    param([string]$ScenarioRoot)
    $StateConfigPath = Join-Path $ScenarioRoot "application\ma_ga_live_state_config.json"
    $RuntimeConfigPath = Join-Path $ScenarioRoot "application\ma_ga_live_runtime_config.json"
    $StateConfig = Get-Content -LiteralPath $StateConfigPath -Raw | ConvertFrom-Json
    $RuntimeConfig = Get-Content -LiteralPath $RuntimeConfigPath -Raw | ConvertFrom-Json

    if ($StateConfig.PSObject.Properties.Name -contains "workloadGeneration" -and
            $StateConfig.workloadGeneration.PSObject.Properties.Name -contains "arrivalRateTasksPerSecondPerActiveVehicle") {
        $StateConfig.workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle = 0.02
        $StateConfig.workloadGeneration.maxGeneratedTasksPerTickPerVehicle = 1
        $StateConfig.workloadGeneration | Add-Member -NotePropertyName "smokeExecutionOverride" -NotePropertyValue "TECHNICAL_SMOKE_THROTTLE_NOT_CALIBRATION" -Force
    }
    $RuntimeConfig.coordinatorTickIntervalMs = 500
    $RuntimeConfig | Add-Member -NotePropertyName "smokeExecutionOverride" -NotePropertyValue "TECHNICAL_SMOKE_THROTTLE_NOT_CALIBRATION" -Force

    Set-JsonFile -Path $StateConfigPath -Value $StateConfig
    Set-JsonFile -Path $RuntimeConfigPath -Value $RuntimeConfig
    return [ordered]@{
        applied = $true
        reason = "TECHNICAL_SMOKE_THROTTLE_NOT_CALIBRATION"
        workloadArrivalRateTasksPerSecondPerActiveVehicle = 0.02
        maxGeneratedTasksPerTickPerVehicle = 1
        coordinatorTickIntervalMs = 500
    }
}

Assert-SafeName -Name $ScenarioName -Label "ScenarioName"
$ResolvedIntasRoot = Resolve-RepoPath -Path $IntasRoot
$ResolvedPersistentRoot = if ([IO.Path]::IsPathRooted($PersistentRoot)) {
    $PersistentRoot
} else {
    Join-Path $RepoRoot $PersistentRoot
}
$ResolvedPersistentRoot = [IO.Path]::GetFullPath($ResolvedPersistentRoot)
$VariantName = "$Density-$DurationProfile-seed-$Seed"
$PersistentScenarioRoot = Join-Path (Join-Path $ResolvedPersistentRoot $ScenarioName) $VariantName
$ReportsDir = Join-Path $PersistentScenarioRoot "reports"
$ManifestPath = Join-Path $PersistentScenarioRoot "materialization_manifest.json"

if (-not (Test-Path -LiteralPath (Join-Path $ResolvedIntasRoot "scenario\ingolstadt.net.xml") -PathType Leaf)) {
    throw "InTAS source is missing scenario\ingolstadt.net.xml: $ResolvedIntasRoot"
}
if (-not (Get-Command py -ErrorAction SilentlyContinue)) {
    throw "Python launcher 'py' not found in PATH"
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java not found in PATH; Scenario-Convert requires Java"
}

$ScenarioConvertPath = Find-ScenarioConvert -ExplicitPath $ScenarioConvert
$ScenarioConvertRoot = Get-ScenarioConvertRoot -ScenarioConvertPath $ScenarioConvertPath
$ScenarioConvertHelp = Get-ScenarioConvertHelp -ScenarioConvertRoot $ScenarioConvertRoot
if ($ScenarioConvertHelp -notmatch "database create" -or $ScenarioConvertHelp -notmatch "route import") {
    throw "Scenario-Convert CLI does not expose required 'database create' and 'route import' commands."
}

if ((Test-Path -LiteralPath $ManifestPath -PathType Leaf) -and -not $ForceRebuild) {
    $Manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    if ($Manifest.scenarioName -eq $ScenarioName -and
            $Manifest.density -eq $Density -and
            $Manifest.durationProfile -eq $DurationProfile -and
            [int]$Manifest.seed -eq $Seed) {
        & py -3 -B (Join-Path $ToolRoot "validate_materialized_literature_scenario.py") --scenario-root $PersistentScenarioRoot --repo-root $RepoRoot
        if ($LASTEXITCODE -ne 0) {
            throw "Existing materialized scenario failed validation. Rebuild with -ForceRebuild after inspecting it: $PersistentScenarioRoot"
        }
        Write-Host "MATERIALIZED_SCENARIO_REUSED"
        Write-Host "Materialized scenario root: $PersistentScenarioRoot"
        exit 0
    }
    throw "Materialized scenario exists with different inputs. Use -ForceRebuild to replace: $PersistentScenarioRoot"
}

if (Test-Path -LiteralPath $PersistentScenarioRoot) {
    if (-not $ForceRebuild) {
        throw "Materialized scenario exists without reusable manifest. Use -ForceRebuild after inspection: $PersistentScenarioRoot"
    }
    $ResolvedTarget = (Resolve-Path -LiteralPath $PersistentScenarioRoot).Path
    $ResolvedBase = [IO.Path]::GetFullPath((Join-Path $ResolvedPersistentRoot $ScenarioName))
    if (-not $ResolvedTarget.StartsWith($ResolvedBase, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove path outside persistent materialization root: $ResolvedTarget"
    }
    Remove-Item -LiteralPath $ResolvedTarget -Recurse -Force
}

$StagingRoot = Join-Path (Join-Path $RepoRoot "tmp\intas-literature-materialization-staging") ([Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $StagingRoot -Force | Out-Null
try {
    Write-Host "Validating InTAS source..."
    & py -3 -B (Join-Path $ToolRoot "validate_intas_source.py") --intas-root $ResolvedIntasRoot
    if ($LASTEXITCODE -ne 0) {
        throw "InTAS source validation failed"
    }

    Write-Host "Generating reduced SUMO and text configuration..."
    & py -3 -B (Join-Path $ToolRoot "build_intas_literature_scenario.py") `
        --intas-root $ResolvedIntasRoot `
        --output-root $StagingRoot `
        --density $Density `
        --duration-profile $DurationProfile `
        --seed $Seed `
        --dry-run
    if ($LASTEXITCODE -ne 0) {
        throw "InTAS literature materializer failed"
    }

    $GeneratedScenario = Join-Path $StagingRoot $ScenarioName
    if (-not (Test-Path -LiteralPath $GeneratedScenario -PathType Container)) {
        throw "Generated scenario not found: $GeneratedScenario"
    }
    New-Item -ItemType Directory -Path $PersistentScenarioRoot -Force | Out-Null
    Copy-DirectoryContents -Source $GeneratedScenario -Destination $PersistentScenarioRoot
    $OutputSource = Join-Path $RepoRoot "data\mosaic-scenarios\MaGaLiveMagaRuntimeStudy\output\output_config.xml"
    $OutputTargetDir = Join-Path $PersistentScenarioRoot "output"
    $OutputTarget = Join-Path $OutputTargetDir "output_config.xml"
    if (-not (Test-Path -LiteralPath $OutputSource -PathType Leaf)) {
        throw "Reference output_config.xml not found: $OutputSource"
    }
    New-Item -ItemType Directory -Path $OutputTargetDir -Force | Out-Null
    Copy-Item -LiteralPath $OutputSource -Destination $OutputTarget -Force
    $SmokeExecutionOverrides = [ordered]@{ applied = $false }
    if ($DurationProfile -eq "smoke") {
        $SmokeExecutionOverrides = Apply-SmokeExecutionOverrides -ScenarioRoot $PersistentScenarioRoot
        Write-Host "Applied smoke execution override: workload rate 0.02 task/s/active vehicle, runtime tick 500ms"
    }

    $ApplicationDir = Join-Path $PersistentScenarioRoot "application"
    $SumoDir = Join-Path $PersistentScenarioRoot "sumo"
    $NetFile = Join-Path $SumoDir "$SubscenarioName.net.xml"
    $RouteFile = Join-Path $SumoDir "${SubscenarioName}_${Density}.rou.xml"
    $DatabasePath = Join-Path $ApplicationDir $ScenarioDbName
    if (-not (Test-Path -LiteralPath $NetFile -PathType Leaf)) {
        throw "Reduced network missing: $NetFile"
    }
    if (-not (Test-Path -LiteralPath $RouteFile -PathType Leaf)) {
        throw "Selected route subset missing: $RouteFile"
    }

    New-Item -ItemType Directory -Path $ApplicationDir -Force | Out-Null
    Write-Host "Creating MOSAIC database..."
    Invoke-ScenarioConvert -ScenarioConvertRoot $ScenarioConvertRoot -WorkingDirectory $ApplicationDir -Arguments @(
        "database", "create", $NetFile, "-s", $SubscenarioName, "-f"
    )
    $GeneratedDbs = @(Get-ChildItem -LiteralPath $ApplicationDir -Filter "*.db" -File)
    if ($GeneratedDbs.Count -eq 0) {
        $GeneratedDbs = @(Get-ChildItem -LiteralPath $PersistentScenarioRoot -Recurse -Filter "*.db" -File)
    }
    if ($GeneratedDbs.Count -eq 0) {
        throw "Scenario-Convert database create did not produce a DB."
    }
    $CreatedDb = $GeneratedDbs | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($CreatedDb.FullName -ne $DatabasePath) {
        Move-Item -LiteralPath $CreatedDb.FullName -Destination $DatabasePath -Force
    }

    Write-Host "Importing selected route subset into MOSAIC database..."
    Invoke-ScenarioConvert -ScenarioConvertRoot $ScenarioConvertRoot -WorkingDirectory $ApplicationDir -Arguments @(
        "route", "import", $DatabasePath, $RouteFile
    )
    $ScenarioConvertLogs = @(
        (Join-Path $ApplicationDir "scenario-convert.log"),
        (Join-Path $RepoRoot "scenario-convert.log")
    )

    foreach ($ScenarioConvertLog in $ScenarioConvertLogs) {
        if (Test-Path -LiteralPath $ScenarioConvertLog -PathType Leaf) {
            Remove-Item -LiteralPath $ScenarioConvertLog -Force
        }
    }

    $ReportJson = Join-Path $PersistentScenarioRoot "reports\intas_literature_materialization_report.json"
    $Report = Get-Content -LiteralPath $ReportJson -Raw | ConvertFrom-Json
    $Report | Add-Member -NotePropertyName "smokeExecutionOverrides" -NotePropertyValue $SmokeExecutionOverrides -Force
    Set-JsonFile -Path $ReportJson -Value $Report
    $NetworkChecksum = Get-Sha256 -Path $NetFile
    $RouteChecksum = Get-Sha256 -Path $RouteFile
    $DatabaseChecksum = Get-Sha256 -Path $DatabasePath
    $Manifest = [ordered]@{
        scenarioName = $ScenarioName
        candidateId = $Report.selectedCandidateId
        mobilityMode = $Report.mobilityMode
        intasCommit = $Report.source.commit
        density = $Density
        durationProfile = $DurationProfile
        seed = $Seed
        networkChecksum = $NetworkChecksum
        routeChecksum = $RouteChecksum
        databaseChecksum = $DatabaseChecksum
        materializerVersion = @{
            scriptSha256 = Get-Sha256 -Path (Join-Path $ToolRoot "build_intas_literature_scenario.py")
            validatorSha256 = Get-Sha256 -Path (Join-Path $ToolRoot "validate_materialized_literature_scenario.py")
            syntheticMobilityProfileSha256 = Get-Sha256 -Path (Join-Path $ToolRoot "config\synthetic_mobility_profile.json")
            selectedEdgeIdsSha256 = Get-Sha256 -Path (Join-Path $ToolRoot "config\candidate_0045_edge_ids.txt")
        }
        scenarioConvertPath = $ScenarioConvertPath
        scenarioConvertRoot = $ScenarioConvertRoot
        scenarioConvertVersion = (($ScenarioConvertHelp -split "`r?`n") | Select-Object -First 1)
        scenarioConvertCli = "database create; route import"
        projection = $Report.projection
        rsuCoordinates = $Report.selectedCandidate.candidateRsuPositions
        smokeExecutionOverrides = $SmokeExecutionOverrides
        generatedAt = (Get-Date).ToString("o")
        databasePath = ($DatabasePath.Replace($RepoRoot, "")).TrimStart("\")
        databaseSizeBytes = (Get-Item -LiteralPath $DatabasePath).Length
    }
    Set-JsonFile -Path $ManifestPath -Value $Manifest

    Write-Host "Validating materialized literature scenario..."
    & py -3 -B (Join-Path $ToolRoot "validate_materialized_literature_scenario.py") --scenario-root $PersistentScenarioRoot --repo-root $RepoRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Materialized literature scenario validation failed"
    }

    Write-Host "MATERIALIZED_SCENARIO_CREATED"
    Write-Host "Materialized scenario root: $PersistentScenarioRoot"
    Write-Host "Database: $DatabasePath"
    Write-Host "Database SHA-256: $DatabaseChecksum"
    Write-Host "Manifest: $ManifestPath"
} finally {
    if (Test-Path -LiteralPath $StagingRoot) {
        Remove-Item -LiteralPath $StagingRoot -Recurse -Force
    }
}
