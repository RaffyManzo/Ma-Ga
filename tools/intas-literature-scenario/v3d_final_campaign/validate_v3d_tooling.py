#!/usr/bin/env python3
"""Static validator for V3-D final campaign batch tooling."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[2]
DEFAULT_SPEC = SCRIPT_DIR / "v3d_campaign_spec.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", default=str(REPO_ROOT))
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    parser.add_argument("--json-output", default="")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def compile_without_bytecode(path: Path) -> None:
    source = path.read_text(encoding="utf-8-sig")
    compile(source, str(path), "exec")


def main() -> int:
    args = parse_args()
    repo = Path(args.repo_root).resolve()
    spec_path = Path(args.spec).resolve()
    spec = read_json(spec_path)
    errors: list[str] = []
    warnings: list[str] = []

    required_scripts = [
        SCRIPT_DIR / "validate_v3d_tooling.py",
        SCRIPT_DIR / "prepare_v3d_materializations.py",
        SCRIPT_DIR / "run_v3d_campaign.py",
        SCRIPT_DIR / "v3d_final_campaign.ps1",
    ]
    dependency_paths = [
        repo / spec["paths"]["legacyMaterializer"],
        repo / spec["paths"]["legacyValidator"],
        repo / spec["paths"]["runScenarioScript"],
        repo / spec["paths"]["deployScenarioScript"],
        repo / spec["paths"]["runtimeArtifactValidatorScript"],
        repo / spec["paths"]["runSummarizerScript"],
        repo / spec["paths"]["smokeRunValidatorScript"],
        repo / spec["paths"]["canonicalBuilder"],
        repo / spec["paths"]["canonicalSourceValidator"],
        repo / spec["paths"]["canonicalMaterializedValidator"],
    ]

    for path in required_scripts:
        if not path.is_file():
            errors.append(f"missing tooling file: {path}")
        elif path.suffix == ".py":
            try:
                compile_without_bytecode(path)
            except Exception as exc:  # noqa: BLE001
                errors.append(f"Python compile failure in {path.name}: {exc}")

    for path in dependency_paths:
        if not path.is_file():
            errors.append(f"missing required dependency: {path}")

    bytecode_files = sorted(
        path for path in SCRIPT_DIR.rglob("*")
        if path.is_file() and (path.suffix == ".pyc" or "__pycache__" in path.parts)
    )
    if bytecode_files:
        errors.append(
            "generated Python bytecode exists under versioned tooling: "
            + ", ".join(str(path.relative_to(repo)).replace("\\", "/") for path in bytecode_files)
        )

    wrapper_text = (
        (SCRIPT_DIR / "v3d_final_campaign.ps1").read_text(
            encoding="utf-8-sig", errors="replace"
        )
        if (SCRIPT_DIR / "v3d_final_campaign.ps1").is_file()
        else ""
    )
    if 'PYTHONDONTWRITEBYTECODE' not in wrapper_text:
        errors.append("PowerShell entry point does not suppress Python bytecode")

    paths = spec["paths"]
    matrix_path = repo / paths["sourceMatrix"]
    materialization_path = repo / paths["scenarioInstancePlan"]
    config_path = repo / paths["scenarioConfigurationMapping"]
    test_path = repo / paths["testIdGroupMapping"]
    execution_path = repo / paths["executionPlan"]

    for path in (
        matrix_path,
        materialization_path,
        config_path,
        test_path,
        execution_path,
    ):
        if not path.is_file():
            errors.append(f"missing planning file: {path}")

    if errors:
        report = {
            "status": "FAIL_V3D_TOOLING_STATIC_VALIDATION",
            "errors": errors,
            "warnings": warnings,
        }
        print(json.dumps(report, indent=2))
        return 1

    matrix = read_csv(matrix_path)
    materializations = read_csv(materialization_path)
    configs = read_csv(config_path)
    tests = read_csv(test_path)
    executions = read_csv(execution_path)

    if spec.get("baselineCommit") != spec.get("frozenCommit"):
        errors.append("baselineCommit and frozenCommit differ")

    expected_runtime_policy = {
        "scenarioName": "MaGaLiteratureBasedUrbanStudy",
        "initialOptimizationDelayMs": 1000,
        "gaPollingIntervalMs": 50,
        "singleInFlightGaOnly": True,
        "discardLateResult": True,
        "keepLastAppliedStrategyWhileRunning": True,
        "freshReoptimizationAfterTimeout": True,
        "runtimeTraceEnabled": True,
        "diagnosticArtificialGaDelayMs": 0,
        "temporalInitialWindowSeconds": 1.0,
        "configuredGaRuntimeEstimateSeconds": 0.01,
        "configuredMaxWindowSeconds": 0.2,
        "deltaTMaxComparisonEpsilonSeconds": 1e-9,
        "publishedSnapshotCopyLimit": 32,
        "nativeLiveDetailedReportingEnabled": True,
        "nativeLiveDetailedReportPrintToConsole": False,
        "gaParameterScalingMode": "STATIC",
        "gaWallClockBudgetMode": "LIVE_ADAPTIVE",
        "configuredInitialGaWallClockBudgetSeconds": 0.2,
        "adaptiveGaWallClockBudgetMinimumSeconds": 0.1,
        "adaptiveGaWallClockBudgetMaximumSeconds": 1.5,
        "adaptiveGaWallClockBudgetHistorySize": 20,
        "adaptiveGaWallClockBudgetWarmupSamples": 3,
        "adaptiveGaWallClockBudgetSafetyMarginSeconds": 0.1,
        "adaptiveGaWallClockBudgetMaximumStepUpSeconds": 0.25,
        "adaptiveGaWallClockBudgetMaximumStepDownSeconds": 0.1,
        "maxSnapshotAgeSimulationSeconds": 2.0,
        "cooperativeGaBudgetStopEnabled": True,
    }
    runtime_policy = spec.get("runtimePolicy", {})
    if runtime_policy.get("policyId") != "V3D_LIVE_ADAPTIVE_FRESHNESS_AWARE":
        errors.append("unexpected runtime policy ID")
    if runtime_policy.get("validatedReferenceConfigSHA256") != (
        "A5693E8225A7F97DF0448576F5F1224B7235D83DC7AB2C36845ED65A57F8790F"
    ):
        errors.append("unexpected validated runtime reference hash")
    if runtime_policy.get("requiredValues") != expected_runtime_policy:
        errors.append("runtime policy values differ from the validated V3-D pilot")
    if runtime_policy.get("coordinatorTickIntervalMsByWorkload") != {
        "default": 100,
        "WL-SMOKE": 500,
    }:
        errors.append("unexpected coordinator tick policy")
    if int(spec["executionPolicy"].get("minimumFreeDiskBytes", 0)) < 5 * 1024**3:
        errors.append("minimum free disk threshold is below 5 GiB")
    if sha256_file(matrix_path) != spec["planningSourceSHA256"]:
        errors.append("source matrix SHA-256 differs from frozen planning hash")

    expected_counts = {
        "matrix": (len(matrix), int(spec["expectedRunCount"])),
        "execution": (len(executions), int(spec["expectedRunCount"])),
        "tests": (len(tests), int(spec["expectedTestCount"])),
        "materializations": (
            len(materializations),
            int(spec["expectedMaterializationCount"]),
        ),
        "configs": (len(configs), int(spec["expectedConfigCount"])),
    }
    for label, (actual, expected) in expected_counts.items():
        if actual != expected:
            errors.append(f"{label} rows: expected {expected}, found {actual}")

    for label, values in (
        ("matrix RunId", [row["RunId"] for row in matrix]),
        ("execution run_id", [row["run_id"] for row in executions]),
        ("test_id", [row["test_id"] for row in tests]),
        (
            "materialization_id",
            [row["materialization_id"] for row in materializations],
        ),
        (
            "target_directory",
            [row["target_directory"].rstrip("/\\") for row in materializations],
        ),
        ("result_root", [row["result_root"].rstrip("/\\") for row in executions]),
    ):
        duplicates = [item for item, count in Counter(values).items() if count > 1]
        if duplicates:
            errors.append(f"duplicate {label}: {duplicates[:5]}")

    matrix_by_id = {row["RunId"]: row for row in matrix}
    execution_by_id = {row["run_id"]: row for row in executions}
    test_ids = {row["test_id"] for row in tests}
    matrix_ids = set(matrix_by_id)
    if matrix_ids != set(execution_by_id):
        errors.append("execution plan RunIds do not match source matrix")
    if matrix_ids != test_ids:
        errors.append("test plan IDs do not match source matrix")

    expected_sequences = list(range(1, len(executions) + 1))
    actual_sequences = sorted(int(row["sequence"]) for row in executions)
    if actual_sequences != expected_sequences:
        errors.append("execution sequence is not the contiguous range 1..68")

    for run_id in sorted(matrix_ids & set(execution_by_id)):
        matrix_row = matrix_by_id[run_id]
        execution_row = execution_by_id[run_id]
        comparisons = {
            "group": (execution_row["group_id"], matrix_row["GroupId"]),
            "config": (execution_row["config_id"], matrix_row["ConfigId"]),
            "seed": (execution_row["seed"], matrix_row["Seed"]),
            "duration": (
                execution_row["duration_seconds"],
                matrix_row["DurationSeconds"],
            ),
            "pacing": (
                float(execution_row["realtime_brake_factor"]),
                float(matrix_row["RealtimeBrakeFactor"]),
            ),
        }
        for label, (actual, expected) in comparisons.items():
            if actual != expected:
                errors.append(
                    f"{run_id}: {label} differs between matrix and execution plan"
                )

        duration = int(execution_row["duration_seconds"])
        timeout = int(execution_row["timeout_seconds"])
        if timeout < duration + 600:
            errors.append(f"{run_id}: timeout margin is below 600 seconds")
        seed = int(execution_row["seed"])
        if int(execution_row["mobility_seed"]) != seed:
            errors.append(f"{run_id}: mobility seed mismatch")
        expected_workload_seed = seed + int(
            spec["seedPolicy"]["workloadSeedOffset"]
        )
        if int(execution_row["workload_seed"]) != expected_workload_seed:
            errors.append(f"{run_id}: workload seed mismatch")
        if float(execution_row["realtime_brake_factor"]) != float(
            spec["pacing"]["factor"]
        ):
            errors.append(f"{run_id}: pacing mismatch")
        if execution_row["required"] != "YES":
            errors.append(f"{run_id}: required must be YES")

    config_ids = {row["config_id"] for row in configs}
    missing_configs = sorted(
        {row["config_id"] for row in executions} - config_ids
    )
    if missing_configs:
        errors.append(f"execution configs missing from mapping: {missing_configs}")
    non_materializable = [
        row["config_id"]
        for row in configs
        if row.get("materializable", "").strip().lower() != "yes"
    ]
    if non_materializable:
        errors.append(f"non-materializable configs: {non_materializable}")

    materialization_by_id = {
        row["materialization_id"]: row for row in materializations
    }
    for row in executions:
        materialization = materialization_by_id.get(row["materialization_id"])
        if materialization is None:
            errors.append(f"{row['run_id']}: unknown materialization_id")
            continue
        if row["group_id"] == "G03":
            if materialization["config_id"] != "CFG-N-I":
                errors.append(f"{row['run_id']}: G03 reuse config mismatch")
            if int(materialization["seed"]) != 104729:
                errors.append(f"{row['run_id']}: G03 reuse seed mismatch")
        else:
            for label, actual, expected in (
                ("config", materialization["config_id"], row["config_id"]),
                ("seed", materialization["seed"], row["seed"]),
                (
                    "duration",
                    materialization["duration"].split()[0],
                    row["duration_seconds"],
                ),
            ):
                if actual != expected:
                    errors.append(
                        f"{row['run_id']}: materialization {label} mismatch"
                    )

        target = (repo / row["materialized_scenario_root"]).resolve()
        campaign_root = (repo / paths["campaignScenarioRoot"]).resolve()
        try:
            target.relative_to(campaign_root)
        except ValueError:
            errors.append(f"{row['run_id']}: materialization escapes campaign root")

        result = (repo / row["result_root"]).resolve()
        results_root = (repo / paths["resultsRoot"]).resolve()
        try:
            result.relative_to(results_root)
        except ValueError:
            errors.append(f"{row['run_id']}: result path escapes results root")

    groups = Counter(row["group_id"] for row in executions)
    if dict(groups) != spec["groupExpectedRuns"]:
        errors.append(f"group counts mismatch: {dict(groups)}")

    g03 = [row for row in executions if row["group_id"] == "G03"]
    base = [
        row
        for row in executions
        if row["run_id"] == "V3D-G02-CFG-N-I-104729"
    ]
    if len(g03) != 2 or len(base) != 1:
        errors.append("G03 reuse structure is incomplete")
    elif any(
        row["materialization_id"] != base[0]["materialization_id"]
        for row in g03
    ):
        errors.append(
            "G03 does not reuse the frozen G02 CFG-N-I/104729 materialization"
        )

    forbidden_refs = [
        '"testing/final-campaign"',
        '"5a9477735a3d707a5f000a64653cd2a6fc7f2007"',
        '"expectedMaterializationCount": 69',
        '"expectedTestCount": 87',
    ]
    legacy_scan_paths = [
        path
        for path in required_scripts
        if path.name != "validate_v3d_tooling.py"
    ] + [spec_path]
    for path in legacy_scan_paths:
        if not path.exists() or path.suffix not in {".py", ".ps1", ".json"}:
            continue
        text = path.read_text(encoding="utf-8-sig", errors="replace")
        for fragment in forbidden_refs:
            if fragment in text:
                errors.append(
                    f"{path.name}: legacy campaign constant present: {fragment}"
                )

    jar = repo / paths["runtimeJar"]
    if not jar.is_file():
        warnings.append(f"runtime JAR not present during static validation: {jar}")
    else:
        if jar.stat().st_size != int(spec["runtimeArtifact"]["sizeBytes"]):
            errors.append("runtime JAR size mismatch")
        if sha256_file(jar) != spec["runtimeArtifact"]["sha256"]:
            errors.append("runtime JAR SHA-256 mismatch")

    ad_hoc_jar = repo / paths["adHocDiagnosticJar"]
    if not ad_hoc_jar.is_file():
        warnings.append(
            "ad-hoc diagnostic JAR is not present yet; "
            "DryRun and Ready will reject the campaign until it exists"
        )
    elif ad_hoc_jar.stat().st_size <= 0:
        errors.append("ad-hoc diagnostic JAR is empty")

    status = (
        "PASS_V3D_BATCH_TOOLING_STATIC_VALIDATION"
        if not errors
        else "FAIL_V3D_TOOLING_STATIC_VALIDATION"
    )
    report = {
        "status": status,
        "errors": errors,
        "warnings": warnings,
        "counts": {
            "matrixRuns": len(matrix),
            "executionRuns": len(executions),
            "materializations": len(materializations),
            "configs": len(configs),
            "tests": len(tests),
        },
        "groupCounts": dict(groups),
        "pacingFactor": spec["pacing"]["factor"],
        "automaticRetry": spec["executionPolicy"]["automaticRetry"],
        "bytecodeFiles": len(bytecode_files),
    }
    if args.json_output:
        output = Path(args.json_output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(report, indent=2) + "\n",
            encoding="utf-8",
        )
    print(json.dumps(report, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
