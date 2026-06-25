param(
    [ValidateSet(
        "Check",
        "PrepareSmoke",
        "RunSmoke",
        "ResumeSmoke",
        "PrepareCampaign",
        "RunCampaign",
        "ResumeCampaign",
        "Validate",
        "Aggregate",
        "Bundle"
    )]
    [string]$Mode = "Check",

    [string]$RunId = "",
    [string]$Python = "py",
    [string]$MosaicRoot = ".\tmp\mosaic-25.2",
    [string]$ScenarioName = "MaGaLiteratureBasedUrbanStudy",
    [switch]$PrintDetailedLiveReport
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..\..")
$Tool = Join-Path $ScriptDir "g02b_tool.py"
$SpecPath = Join-Path $ScriptDir "g02b_spec.json"

function Invoke-G02BPython {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ToolMode,
        [string[]]$ExtraArgs = @()
    )

    $args = @()
    if ((Split-Path -Leaf $Python) -eq "py") {
        $args += "-3"
    }
    $args += @("-B", $Tool, "--mode", $ToolMode) + $ExtraArgs
    & $Python @args
    if ($LASTEXITCODE -ne 0) {
        throw "G02B Python tool failed in mode $ToolMode"
    }
}

function Get-GitValue {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string[]]$GitArguments
    )
    if ($GitArguments.Count -eq 0) {
        throw "Get-GitValue requires at least one Git argument"
    }
    $value = & git -c "safe.directory=$($RepoRoot.Path.Replace('\', '/'))" -C $RepoRoot @GitArguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($GitArguments -join ' ') failed"
    }
    return ($value | Out-String).Trim()
}

function Resolve-G02BMaybeRelative {
    param([string]$Path)
    if ([IO.Path]::IsPathRooted($Path)) {
        return (Resolve-Path -LiteralPath $Path).Path
    }
    return (Resolve-Path -LiteralPath (Join-Path $RepoRoot $Path)).Path
}

function Assert-G02BExecutionAllowed {
    $spec = Get-Content -Raw -Path $SpecPath | ConvertFrom-Json
    $branch = Get-GitValue -GitArguments @("branch", "--show-current")
    $head = Get-GitValue -GitArguments @("rev-parse", "HEAD")
    $remoteHead = Get-GitValue -GitArguments @("rev-parse", "origin/$($spec.branch)")
    $baseHead = Get-GitValue -GitArguments @("rev-parse", $spec.baseBranch)
    $remoteBaseHead = Get-GitValue -GitArguments @("rev-parse", "origin/$($spec.baseBranch)")
    $status = Get-GitValue -GitArguments @("status", "--short")
    if ($branch -eq $spec.baseBranch) {
        throw "Refusing to run on $($spec.baseBranch)"
    }
    if ($branch -ne $spec.branch) {
        throw "Unexpected branch: $branch"
    }
    & git -c "safe.directory=$($RepoRoot.Path.Replace('\', '/'))" -C $RepoRoot merge-base --is-ancestor $spec.implementationBaseCommit HEAD
    if ($LASTEXITCODE -ne 0) {
        throw "Implementation base is not an ancestor of HEAD: $($spec.implementationBaseCommit)"
    }
    if ($remoteHead -ne $head) {
        throw "Local HEAD $head does not match origin/$($spec.branch) $remoteHead"
    }
    if ($baseHead -ne $spec.baseHead -or $remoteBaseHead -ne $spec.baseHead) {
        throw "Base branch $($spec.baseBranch) is not at expected commit $($spec.baseHead)"
    }
    $javaChanges = Get-GitValue -GitArguments @("diff", "--name-only", "$($spec.implementationBaseCommit)..HEAD", "--", "*.java")
    if (-not [string]::IsNullOrWhiteSpace($javaChanges)) {
        throw "Java changes were introduced after implementation base:`n$javaChanges"
    }
    if (-not [string]::IsNullOrWhiteSpace($status)) {
        throw "Working tree must be clean before running MOSAIC. Current status:`n$status"
    }

    $runtimeJar = Join-Path $RepoRoot $spec.paths.runtimeJar
    if (-not (Test-Path -LiteralPath $runtimeJar)) {
        throw "Missing runtime JAR: $runtimeJar"
    }
    $jarHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $runtimeJar).Hash.ToLowerInvariant()
    $classList = & jar tf $runtimeJar
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect runtime JAR"
    }
    if (-not ($classList -match "MaGaExperimentalVariant.class")) {
        throw "Runtime JAR does not contain the G02B experimental variant class"
    }
    $runtimeJarItem = Get-Item -LiteralPath $runtimeJar
    return @{
        Branch = $branch
        Head = $head
        RemoteHead = $remoteHead
        RuntimeJar = $runtimeJar
        RuntimeJarSha256 = $jarHash
        RuntimeJarSizeBytes = $runtimeJarItem.Length
    }
}

function Read-G02BPlan {
    param([ValidateSet("smoke", "campaign")][string]$PlanType)
    $spec = Get-Content -Raw -Path $SpecPath | ConvertFrom-Json
    $path = if ($PlanType -eq "smoke") { $spec.paths.smokePlan } else { $spec.paths.scientificPlan }
    $full = Join-Path $RepoRoot $path
    if (-not (Test-Path -LiteralPath $full)) {
        Invoke-G02BPython -ToolMode "generate-plan"
    }
    return Import-Csv -LiteralPath $full
}

function Get-G02BRegistryPath {
    $spec = Get-Content -Raw -Path $SpecPath | ConvertFrom-Json
    $stateRoot = Join-Path $RepoRoot $spec.paths.stateRoot
    New-Item -ItemType Directory -Force -Path $stateRoot | Out-Null
    return Join-Path $stateRoot "g02b_run_registry.json"
}

function Read-G02BRegistry {
    $path = Get-G02BRegistryPath
    if (Test-Path -LiteralPath $path) {
        return Get-Content -Raw -Path $path | ConvertFrom-Json
    }
    return [pscustomobject]@{ runs = [pscustomobject]@{} }
}

function Write-G02BRegistry {
    param([object]$Registry)
    $path = Get-G02BRegistryPath
    $Registry | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $path -Encoding UTF8
}

function Update-G02BRegistry {
    param(
        [string]$RunId,
        [string]$Status,
        [hashtable]$Context,
        [object]$Row = $null,
        [string]$FailureReason = "",
        [int]$RunnerExitCode = 0,
        [string]$MosaicRunName = "",
        [string]$CanonicalValidatorStatus = ""
    )
    $registry = Read-G02BRegistry
    $runs = @{}
    if ($registry.runs) {
        foreach ($property in $registry.runs.PSObject.Properties) {
            $runs[$property.Name] = $property.Value
        }
    }
    $runs[$RunId] = [pscustomobject]@{
        status = $Status
        updatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        branch = $Context.Branch
        head = $Context.Head
        runtimeJar = $Context.RuntimeJar
        runtimeJarSha256 = $Context.RuntimeJarSha256
        runtimeJarSizeBytes = $Context.RuntimeJarSizeBytes
        planType = if ($null -ne $Row) { $Row.plan_type } else { "" }
        configId = if ($null -ne $Row) { $Row.config_id } else { "" }
        seed = if ($null -ne $Row) { [int]$Row.seed } else { $null }
        variant = if ($null -ne $Row) { $Row.variant } else { "" }
        outputPath = if ($null -ne $Row) { $Row.output_path } else { "" }
        mosaicRunName = $MosaicRunName
        runnerExitCode = $RunnerExitCode
        canonicalValidatorStatus = $CanonicalValidatorStatus
        failureReason = $FailureReason
    }
    $registry = [pscustomobject]@{ runs = $runs }
    Write-G02BRegistry -Registry $registry
}

function Get-G02BRegistryEntry {
    param(
        [object]$Registry,
        [string]$RunId
    )
    if ($Registry.runs) {
        $property = $Registry.runs.PSObject.Properties[$RunId]
        if ($null -ne $property) {
            return $property.Value
        }
    }
    return $null
}

function Get-CompatibleMosaicRuns {
    param([string]$ResolvedMosaicRoot)
    $logsRoot = Join-Path $resolvedMosaicRoot "logs"
    if (-not (Test-Path -LiteralPath $logsRoot -PathType Container)) {
        return @()
    }
    return @(Get-ChildItem -LiteralPath $logsRoot -Directory |
        Where-Object { $_.Name -like "*-$ScenarioName" } |
        Select-Object -ExpandProperty Name)
}

function Get-SingleNewMosaicRun {
    param(
        [string[]]$Before,
        [string[]]$After
    )
    $beforeSet = @{}
    foreach ($name in $Before) {
        $beforeSet[$name] = $true
    }
    $new = @($After | Where-Object { -not $beforeSet.ContainsKey($_) })
    if ($new.Count -ne 1) {
        throw "Expected exactly one new MOSAIC directory, found $($new.Count): $($new -join ', ')"
    }
    return $new[0]
}

function Read-G02BRunContext {
    param([string]$OutputPath)
    $spec = Get-Content -Raw -Path $SpecPath | ConvertFrom-Json
    $path = Join-Path $OutputPath $spec.runContextFileName
    if (Test-Path -LiteralPath $path) {
        return Get-Content -Raw -Path $path | ConvertFrom-Json
    }
    return $null
}

function Write-G02BRunContext {
    param(
        [object]$Row,
        [hashtable]$Context,
        [string]$OutputPath,
        [string]$Status,
        [string]$MosaicRunName = "",
        [int]$RunnerExitCode = 0,
        [string]$CanonicalValidatorStatus = "",
        [string]$FailureReason = ""
    )
    $spec = Get-Content -Raw -Path $SpecPath | ConvertFrom-Json
    New-Item -ItemType Directory -Force -Path $OutputPath | Out-Null
    $contextPath = Join-Path $OutputPath $spec.runContextFileName
    $existing = if (Test-Path -LiteralPath $contextPath) { Get-Content -Raw -Path $contextPath | ConvertFrom-Json } else { $null }
    $startedAt = if ($null -ne $existing -and $existing.startedAt) { $existing.startedAt } else { (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ") }
    $data = [ordered]@{
        runId = $Row.run_id
        planType = $Row.plan_type
        configId = $Row.config_id
        seed = [int]$Row.seed
        variant = $Row.variant
        branch = $Context.Branch
        head = $Context.Head
        runtimeJar = $Context.RuntimeJar
        runtimeJarSha256 = $Context.RuntimeJarSha256
        runtimeJarSizeBytes = $Context.RuntimeJarSizeBytes
        sourceMaterialization = $Row.source_materialization
        preparedScenario = $Row.destination_directory
        outputPath = $Row.output_path
        startedAt = $startedAt
        status = $Status
        runnerExitCode = $RunnerExitCode
        canonicalValidatorStatus = $CanonicalValidatorStatus
        failureReason = $FailureReason
    }
    if (-not [string]::IsNullOrWhiteSpace($MosaicRunName)) {
        $data.mosaicRunName = $MosaicRunName
    }
    if ($Status -in @("COMPLETED", "VALIDATION_FAILED", "VALIDATED", "RUN_FAILED")) {
        $data.completedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    }
    $data | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $contextPath -Encoding UTF8
}

function Test-G02BRunArtifactsComplete {
    param([string]$OutputPath)
    $spec = Get-Content -Raw -Path $SpecPath | ConvertFrom-Json
    $required = @(
        (Join-Path $OutputPath "live_run_summary.json"),
        (Join-Path $OutputPath "live-reporting\live_detailed_execution_report.json"),
        (Join-Path $OutputPath "live-reporting\live_temporal_step_records.jsonl"),
        (Join-Path $OutputPath $spec.canonicalValidatorFileName)
    )
    foreach ($path in $required) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            return [pscustomobject]@{ Complete = $false; Reason = "Missing required artifact: $path"; CanonicalStatus = ""; SimulationCompleted = $false }
        }
    }
    $canonical = Get-Content -Raw -Path (Join-Path $OutputPath $spec.canonicalValidatorFileName) | ConvertFrom-Json
    if ($canonical.simulationCompleted -ne $true) {
        return [pscustomobject]@{ Complete = $false; Reason = "simulationCompleted is not true"; CanonicalStatus = [string]$canonical.status; SimulationCompleted = $false }
    }
    return [pscustomobject]@{ Complete = $true; Reason = ""; CanonicalStatus = [string]$canonical.status; SimulationCompleted = $true }
}

function Assert-G02BPreparedScenario {
    param(
        [object]$Row,
        [hashtable]$Context,
        [ValidateSet("smoke", "campaign")]
        [string]$PlanType
    )
    $spec = Get-Content -Raw -Path $SpecPath | ConvertFrom-Json
    $mode = if ($PlanType -eq "smoke") { "validate-prepared-smoke" } else { "validate-prepared-campaign" }
    Invoke-G02BPython -ToolMode $mode -ExtraArgs @("--run-id", $Row.run_id)
    $scenarioRoot = Join-Path $RepoRoot $Row.destination_directory
    $manifest = Get-Content -Raw -Path (Join-Path $scenarioRoot $spec.g02bManifestRelativePath) | ConvertFrom-Json
    if ($manifest.validationStatus -ne "PRE_RUN_VALIDATED") {
        throw "Prepared scenario is not PRE_RUN_VALIDATED for $($Row.run_id)"
    }
    $runtimeConfig = Get-Content -Raw -Path (Join-Path $scenarioRoot $spec.runtimeConfigRelativePath) | ConvertFrom-Json
    if ($runtimeConfig.experimentalVariant -ne $Row.variant) {
        throw "Runtime config variant mismatch for $($Row.run_id)"
    }
    if ($manifest.runtimeJarSha256 -ne $Context.RuntimeJarSha256 -or [int64]$manifest.runtimeJarSizeBytes -ne [int64]$Context.RuntimeJarSizeBytes) {
        throw "Runtime JAR differs from prepared manifest for $($Row.run_id)"
    }
}

function Invoke-G02BSequentialRuns {
    param(
        [ValidateSet("smoke", "campaign")]
        [string]$PlanType,
        [switch]$Resume
    )
    $context = Assert-G02BExecutionAllowed
    $resolvedMosaicRoot = Resolve-G02BMaybeRelative -Path $MosaicRoot
    $rows = Read-G02BPlan -PlanType $PlanType
    $registry = Read-G02BRegistry
    foreach ($row in $rows) {
        if ($RunId -and $row.run_id -ne $RunId) {
            continue
        }
        $entry = Get-G02BRegistryEntry -Registry $registry -RunId $row.run_id
        if ($Resume -and $null -ne $entry -and $entry.status -eq "VALIDATED") {
            continue
        }
        $outputPath = Join-Path $RepoRoot $row.output_path
        $runContext = Read-G02BRunContext -OutputPath $outputPath
        if ($Resume -and $null -ne $entry -and $entry.status -eq "RUN_FAILED") {
            throw "Run $($row.run_id) is RUN_FAILED; manual intervention is required"
        }
        if ($Resume -and (Test-Path -LiteralPath $outputPath) -and $null -ne $runContext -and ($runContext.status -in @("COMPLETED", "VALIDATION_FAILED"))) {
            $validatorMode = if ($PlanType -eq "smoke") { "validate-run-smoke" } else { "validate-run-campaign" }
            try {
                Invoke-G02BPython -ToolMode $validatorMode -ExtraArgs @("--run-id", $row.run_id)
                Write-G02BRunContext -Row $row -Context $context -OutputPath $outputPath -Status "VALIDATED" -MosaicRunName $runContext.mosaicRunName
                Update-G02BRegistry -RunId $row.run_id -Status "VALIDATED" -Context $context -Row $row
                continue
            }
            catch {
                Write-G02BRunContext -Row $row -Context $context -OutputPath $outputPath -Status "VALIDATION_FAILED" -MosaicRunName $runContext.mosaicRunName
                Update-G02BRegistry -RunId $row.run_id -Status "VALIDATION_FAILED" -Context $context -Row $row
                throw
            }
        }
        if (Test-Path -LiteralPath $outputPath) {
            throw "Output already exists for $($row.run_id): $($row.output_path)"
        }
        Assert-G02BPreparedScenario -Row $row -Context $context -PlanType $PlanType
        Write-G02BRunContext -Row $row -Context $context -OutputPath $outputPath -Status "STARTED"
        Update-G02BRegistry -RunId $row.run_id -Status "STARTED" -Context $context -Row $row
        $beforeRuns = Get-CompatibleMosaicRuns -ResolvedMosaicRoot $resolvedMosaicRoot
        $runnerArgs = @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            (Join-Path $RepoRoot "tools\intas-literature-scenario\run_literature_scenario.ps1"),
            "-MaterializedScenarioRoot",
            (Join-Path $RepoRoot $row.destination_directory),
            "-MosaicRoot",
            $resolvedMosaicRoot,
            "-ScenarioName",
            $ScenarioName,
            "-RuntimeArtifactMode",
            "RECOVERED_VALIDATED_ARTIFACT",
            "-RuntimeJarPath",
            $context.RuntimeJar,
            "-ExpectedRuntimeJarSha256",
            $context.RuntimeJarSha256,
            "-ExpectedRuntimeJarSizeBytes",
            $context.RuntimeJarSizeBytes
        )
        if ($PrintDetailedLiveReport) {
            $runnerArgs += "-PrintDetailedLiveReport"
        }
        $runnerExitCode = 0
        try {
            & powershell @runnerArgs
            $runnerExitCode = $LASTEXITCODE
        }
        catch {
            $runnerExitCode = if ($LASTEXITCODE -ne $null) { $LASTEXITCODE } else { 1 }
        }
        $newRunName = ""
        try {
            $afterRuns = Get-CompatibleMosaicRuns -ResolvedMosaicRoot $resolvedMosaicRoot
            $newRunName = Get-SingleNewMosaicRun -Before $beforeRuns -After $afterRuns
            $newRunDirectory = Join-Path (Join-Path $resolvedMosaicRoot "logs") $newRunName
            $liveRuntime = Join-Path $newRunDirectory "live-maga-runtime"
            if (-not (Test-Path -LiteralPath $liveRuntime -PathType Container)) {
                throw "Missing live runtime output for $($row.run_id): $liveRuntime"
            }
            Copy-Item -Path (Join-Path $liveRuntime "*") -Destination $outputPath -Recurse
            $artifactState = Test-G02BRunArtifactsComplete -OutputPath $outputPath
            if (-not $artifactState.Complete) {
                throw $artifactState.Reason
            }
            Write-G02BRunContext -Row $row -Context $context -OutputPath $outputPath -Status "COMPLETED" -MosaicRunName $newRunName -RunnerExitCode $runnerExitCode -CanonicalValidatorStatus $artifactState.CanonicalStatus
            Update-G02BRegistry -RunId $row.run_id -Status "COMPLETED" -Context $context -Row $row -RunnerExitCode $runnerExitCode -MosaicRunName $newRunName -CanonicalValidatorStatus $artifactState.CanonicalStatus
        }
        catch {
            $reason = $_.Exception.Message
            Write-G02BRunContext -Row $row -Context $context -OutputPath $outputPath -Status "RUN_FAILED" -MosaicRunName $newRunName -RunnerExitCode $runnerExitCode -FailureReason $reason
            Update-G02BRegistry -RunId $row.run_id -Status "RUN_FAILED" -Context $context -Row $row -RunnerExitCode $runnerExitCode -MosaicRunName $newRunName -FailureReason $reason
            throw
        }
        $validatorMode = if ($PlanType -eq "smoke") { "validate-run-smoke" } else { "validate-run-campaign" }
        try {
            Invoke-G02BPython -ToolMode $validatorMode -ExtraArgs @("--run-id", $row.run_id)
            Write-G02BRunContext -Row $row -Context $context -OutputPath $outputPath -Status "VALIDATED" -MosaicRunName $newRunName -RunnerExitCode $runnerExitCode -CanonicalValidatorStatus $artifactState.CanonicalStatus
            Update-G02BRegistry -RunId $row.run_id -Status "VALIDATED" -Context $context -Row $row -RunnerExitCode $runnerExitCode -MosaicRunName $newRunName -CanonicalValidatorStatus $artifactState.CanonicalStatus
        }
        catch {
            $reason = $_.Exception.Message
            Write-G02BRunContext -Row $row -Context $context -OutputPath $outputPath -Status "VALIDATION_FAILED" -MosaicRunName $newRunName -RunnerExitCode $runnerExitCode -CanonicalValidatorStatus $artifactState.CanonicalStatus -FailureReason $reason
            Update-G02BRegistry -RunId $row.run_id -Status "VALIDATION_FAILED" -Context $context -Row $row -RunnerExitCode $runnerExitCode -MosaicRunName $newRunName -CanonicalValidatorStatus $artifactState.CanonicalStatus -FailureReason $reason
            throw
        }
    }
}

switch ($Mode) {
    "Check" {
        Invoke-G02BPython -ToolMode "check"
    }
    "PrepareSmoke" {
        $extra = if ($RunId) { @("--run-id", $RunId) } else { @() }
        Invoke-G02BPython -ToolMode "prepare-smoke" -ExtraArgs $extra
        Invoke-G02BPython -ToolMode "validate-prepared-smoke" -ExtraArgs $extra
    }
    "PrepareCampaign" {
        $extra = if ($RunId) { @("--run-id", $RunId) } else { @() }
        Invoke-G02BPython -ToolMode "prepare-campaign" -ExtraArgs $extra
        Invoke-G02BPython -ToolMode "validate-prepared-campaign" -ExtraArgs $extra
    }
    "RunSmoke" {
        Invoke-G02BSequentialRuns -PlanType "smoke"
    }
    "ResumeSmoke" {
        Invoke-G02BSequentialRuns -PlanType "smoke" -Resume
    }
    "RunCampaign" {
        Invoke-G02BSequentialRuns -PlanType "campaign"
    }
    "ResumeCampaign" {
        Invoke-G02BSequentialRuns -PlanType "campaign" -Resume
    }
    "Validate" {
        Invoke-G02BPython -ToolMode "validate-prepared-smoke"
        Invoke-G02BPython -ToolMode "validate-prepared-campaign"
    }
    "Aggregate" {
        Invoke-G02BPython -ToolMode "aggregate" -ExtraArgs @("--complete")
    }
    "Bundle" {
        Invoke-G02BPython -ToolMode "bundle"
    }
}
