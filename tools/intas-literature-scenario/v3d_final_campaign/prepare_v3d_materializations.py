#!/usr/bin/env python3
"""Prepare and verify all V3-D campaign materializations without running MOSAIC."""

from __future__ import annotations

import argparse
import csv
import importlib.util
import json
import shutil
import sys
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[2]
DEFAULT_SPEC = SCRIPT_DIR / "v3d_campaign_spec.json"
sys.dont_write_bytecode = True


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["check", "policy-test", "all", "one", "verify"], default="check")
    parser.add_argument("--repo-root", default=str(REPO_ROOT))
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    parser.add_argument("--materialization-id", default="")
    parser.add_argument("--intas-root", default=r"C:\Users\raffa\IdeaProjects\external\InTAS")
    parser.add_argument("--scenario-convert", default="")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def utc_now() -> str:
    return datetime.now(UTC).isoformat()


def expected_runtime_policy(
    row: dict[str, str],
    spec: dict[str, Any],
) -> dict[str, Any]:
    policy = spec["runtimePolicy"]
    expected = dict(policy["requiredValues"])
    tick_map = policy["coordinatorTickIntervalMsByWorkload"]
    expected["coordinatorTickIntervalMs"] = int(
        tick_map.get(row["workload"], tick_map["default"])
    )
    return expected


def runtime_policy_errors(
    runtime: dict[str, Any],
    row: dict[str, str],
    spec: dict[str, Any],
) -> list[str]:
    errors: list[str] = []
    for key, expected in expected_runtime_policy(row, spec).items():
        if key not in runtime:
            errors.append(f"runtime policy field missing: {key}")
            continue
        actual = runtime[key]
        if isinstance(expected, float):
            try:
                if abs(float(actual) - expected) > 1e-12:
                    errors.append(
                        f"runtime policy {key}: expected {expected!r}, found {actual!r}"
                    )
            except (TypeError, ValueError):
                errors.append(
                    f"runtime policy {key}: expected {expected!r}, found {actual!r}"
                )
        elif actual != expected:
            errors.append(
                f"runtime policy {key}: expected {expected!r}, found {actual!r}"
            )
    return errors


def identity_matches(
    manifest: dict[str, Any],
    validation: dict[str, Any],
    runtime: dict[str, Any],
    row: dict[str, str],
    spec: dict[str, Any],
) -> bool:
    valid_statuses = {"MATERIALIZED_VALIDATED", "MATERIALIZED_WITH_WARNINGS"}
    expected_seed = int(row["seed"])
    expected_workload_seed = expected_seed + int(
        spec["seedPolicy"]["workloadSeedOffset"]
    )
    checks = (
        manifest.get("campaignId") == spec["campaignId"],
        manifest.get("campaignBranch") == spec["campaignBranch"],
        manifest.get("frozenCommit") == spec["baselineCommit"],
        manifest.get("materializationId") == row["materialization_id"],
        manifest.get("configId") == row["config_id"],
        int(manifest.get("campaignSeed", -1)) == expected_seed,
        int(manifest.get("mobilitySeed", -1)) == expected_seed,
        int(manifest.get("workloadSeed", -1)) == expected_workload_seed,
        int(manifest.get("durationSeconds", -1))
        == int(row["duration"].split()[0]),
        manifest.get("workloadProfile") == row["workload"],
        validation.get("status") in valid_statuses,
    )
    manifest_policy = manifest.get("runtimePolicy", {})
    expected_policy = expected_runtime_policy(row, spec)
    policy_manifest_matches = (
        manifest.get("runtimePolicyId")
        == spec["runtimePolicy"]["policyId"]
        and all(manifest_policy.get(key) == value for key, value in expected_policy.items())
    )
    return (
        all(checks)
        and not runtime_policy_errors(runtime, row, spec)
        and policy_manifest_matches
    )


def archive_incompatible_target(
    repo: Path,
    row: dict[str, str],
    spec: dict[str, Any],
) -> str:
    target = (repo / row["target_directory"]).resolve()
    campaign_root = (repo / spec["paths"]["campaignScenarioRoot"]).resolve()
    target.relative_to(campaign_root)

    if not target.exists() or not any(target.iterdir()):
        return ""

    manifest_path = target / "final_campaign_manifest.json"
    validation_path = target / "reports/final_campaign_validation_report.json"
    if manifest_path.is_file() and validation_path.is_file():
        try:
            manifest = read_json(manifest_path)
            validation = read_json(validation_path)
            runtime = read_json(
                target / "application/ma_ga_live_runtime_config.json"
            )
            if identity_matches(manifest, validation, runtime, row, spec):
                return ""
        except Exception:  # noqa: BLE001
            pass

    archive_root = (repo / spec["paths"]["archiveRoot"]).resolve()
    archive_root.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    destination = (
        archive_root
        / "materialization-retries"
        / f"{row['materialization_id']}-{stamp}"
    )
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        raise RuntimeError(f"archive destination already exists: {destination}")
    shutil.move(str(target), str(destination))
    return str(destination.relative_to(repo)).replace("\\", "/")


def load_legacy(repo: Path, spec_path: Path):
    legacy_path = (
        repo
        / "tools/intas-literature-scenario/final_campaign/materialize_final_campaign.py"
    )
    module_spec = importlib.util.spec_from_file_location(
        "v3d_legacy_materializer",
        legacy_path,
    )
    if module_spec is None or module_spec.loader is None:
        raise RuntimeError(f"cannot import legacy materializer: {legacy_path}")
    module = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(module)
    module.DEFAULT_SPEC = spec_path

    original_apply_runtime_overlay = module.apply_runtime_overlay
    original_write_final_manifest = module.write_final_manifest

    def apply_runtime_overlay_v3d(
        runtime: dict[str, Any],
        config_id: str,
        config_row: dict[str, str],
        active_spec: dict[str, Any],
    ):
        mode, delay_ms, limitations = original_apply_runtime_overlay(
            runtime,
            config_id,
            config_row,
            active_spec,
        )
        for key, value in active_spec["runtimePolicy"]["requiredValues"].items():
            runtime[key] = value
        limitations = list(limitations)
        limitations.append(
            "V3D_RUNTIME_POLICY_FORCED_FROM_VALIDATED_PACED_PILOT"
        )
        return (
            str(runtime["gaParameterScalingMode"]),
            int(runtime["diagnosticArtificialGaDelayMs"]),
            limitations,
        )

    def write_final_manifest_v3d(*args, **kwargs):
        original_write_final_manifest(*args, **kwargs)
        row = args[0]
        target_root = args[2]
        active_spec = args[4]
        runtime = read_json(
            target_root / "application/ma_ga_live_runtime_config.json"
        )
        expected = expected_runtime_policy(row, active_spec)
        errors = runtime_policy_errors(runtime, row, active_spec)
        if errors:
            raise RuntimeError("; ".join(errors))
        manifest_path = target_root / "final_campaign_manifest.json"
        manifest = read_json(manifest_path)
        manifest["runtimePolicyId"] = active_spec["runtimePolicy"]["policyId"]
        manifest["runtimePolicyReferenceConfigSHA256"] = active_spec[
            "runtimePolicy"
        ]["validatedReferenceConfigSHA256"]
        manifest["runtimePolicy"] = {
            key: runtime[key] for key in expected
        }
        write_json(manifest_path, manifest)

    module.apply_runtime_overlay = apply_runtime_overlay_v3d
    module.write_final_manifest = write_final_manifest_v3d
    return module


def static_check(repo: Path, spec: dict[str, Any]) -> dict[str, Any]:
    paths = spec["paths"]
    plan = read_csv(repo / paths["scenarioInstancePlan"])
    configs = read_csv(repo / paths["scenarioConfigurationMapping"])
    executions = read_csv(repo / paths["executionPlan"])
    errors: list[str] = []
    if len(plan) != spec["expectedMaterializationCount"]:
        errors.append("materialization count mismatch")
    if len(configs) != spec["expectedConfigCount"]:
        errors.append("configuration count mismatch")
    if len(executions) != spec["expectedRunCount"]:
        errors.append("execution count mismatch")
    if len({row["materialization_id"] for row in plan}) != len(plan):
        errors.append("duplicate materialization IDs")
    if len({row["target_directory"].rstrip("/\\") for row in plan}) != len(plan):
        errors.append("duplicate materialization targets")
    if len({row["run_id"] for row in executions}) != len(executions):
        errors.append("duplicate run IDs")
    materialization_ids = {row["materialization_id"] for row in plan}
    if any(row["materialization_id"] not in materialization_ids for row in executions):
        errors.append("execution plan references unknown materializations")
    return {
        "status": "PASS_V3D_MATERIALIZATION_STATIC_CHECK" if not errors else "FAIL",
        "errors": errors,
        "materializations": len(plan),
        "runs": len(executions),
        "configs": len(configs),
    }


def verify_all(repo: Path, spec_path: Path, spec: dict[str, Any], legacy) -> dict[str, Any]:
    plan = read_csv(repo / spec["paths"]["scenarioInstancePlan"])
    results = []
    valid_statuses = {"MATERIALIZED_VALIDATED", "MATERIALIZED_WITH_WARNINGS"}
    for row in plan:
        root = repo / row["target_directory"]
        manifest_path = root / "final_campaign_manifest.json"
        report_path = root / "reports/final_campaign_validation_report.json"
        errors: list[str] = []
        warnings: list[str] = []
        status = "BLOCKED"
        if not manifest_path.is_file():
            errors.append("missing final_campaign_manifest.json")
        if not report_path.is_file():
            errors.append("missing final_campaign_validation_report.json")
        if not (root / "application/intas_literature_urban.db").is_file():
            errors.append("missing application/intas_literature_urban.db")
        runtime_path = root / "application/ma_ga_live_runtime_config.json"
        if not runtime_path.is_file():
            errors.append("missing application/ma_ga_live_runtime_config.json")
        if not errors:
            manifest = read_json(manifest_path)
            runtime = read_json(runtime_path)
            errors.extend(runtime_policy_errors(runtime, row, spec))
            if manifest.get("runtimePolicyId") != spec["runtimePolicy"]["policyId"]:
                errors.append("runtimePolicyId mismatch")
            manifest_policy = manifest.get("runtimePolicy", {})
            for key, expected in expected_runtime_policy(row, spec).items():
                if manifest_policy.get(key) != expected:
                    errors.append(
                        f"manifest runtime policy {key}: expected "
                        f"{expected!r}, found {manifest_policy.get(key)!r}"
                    )
            prior = read_json(report_path)
            recheck = legacy.validate_scenario(root, spec)
            write_json(root / "reports/v3d_validation_recheck.json", recheck)
            status = recheck.get("status", "BLOCKED")
            errors.extend(recheck.get("errors", []))
            warnings.extend(recheck.get("warnings", []))
            expected_workload_seed = int(row["seed"]) + int(spec["seedPolicy"]["workloadSeedOffset"])
            checks = {
                "campaignId": (manifest.get("campaignId"), spec["campaignId"]),
                "campaignBranch": (manifest.get("campaignBranch"), spec["campaignBranch"]),
                "frozenCommit": (manifest.get("frozenCommit"), spec["baselineCommit"]),
                "materializationId": (manifest.get("materializationId"), row["materialization_id"]),
                "configId": (manifest.get("configId"), row["config_id"]),
                "campaignSeed": (int(manifest.get("campaignSeed", -1)), int(row["seed"])),
                "mobilitySeed": (int(manifest.get("mobilitySeed", -1)), int(row["seed"])),
                "workloadSeed": (int(manifest.get("workloadSeed", -1)), expected_workload_seed),
                "durationSeconds": (
                    int(manifest.get("durationSeconds", -1)),
                    int(row["duration"].split()[0]),
                ),
                "workloadProfile": (manifest.get("workloadProfile"), row["workload"]),
            }
            for label, (actual, expected) in checks.items():
                if actual != expected:
                    errors.append(f"{label}: expected {expected!r}, found {actual!r}")
            if prior.get("status") not in valid_statuses:
                errors.append(f"prior validation status is {prior.get('status')!r}")
            if errors:
                status = "FAILED_VALIDATION"

        results.append({
            "materializationId": row["materialization_id"],
            "groupId": row["group_id"],
            "configId": row["config_id"],
            "targetDirectory": row["target_directory"],
            "status": status,
            "errors": errors,
            "warnings": warnings,
        })

    counts = Counter(item["status"] for item in results)
    valid = sum(counts[s] for s in valid_statuses)
    summary = {
        "status": (
            "PASS_V3D_ALL_MATERIALIZATIONS_VERIFIED"
            if valid == len(plan)
            else "FAIL_V3D_MATERIALIZATION_VERIFICATION"
        ),
        "generatedAtUtc": utc_now(),
        "planned": len(plan),
        "valid": valid,
        "counts": dict(counts),
        "results": results,
    }
    audit_root = repo / spec["paths"]["preparationAuditRoot"]
    write_json(audit_root / "materialization-verification.json", summary)
    return summary



def runtime_policy_self_test(
    repo: Path,
    spec_path: Path,
    spec: dict[str, Any],
) -> dict[str, Any]:
    legacy = load_legacy(repo, spec_path)
    outcomes = []
    for workload, tick in (("WL-I", 100), ("WL-SMOKE", 500)):
        runtime = {
            "scenarioName": "MaGaLiteratureBasedUrbanStudy",
            "coordinatorTickIntervalMs": tick,
            "gaWallClockBudgetMode": "CONFIGURED_STATIC",
        }
        config_row = {"notes": "ga=STATIC, delayMs=0"}
        mode, delay_ms, limitations = legacy.apply_runtime_overlay(
            runtime,
            "CFG-N-I" if workload == "WL-I" else "CFG-SMOKE",
            config_row,
            spec,
        )
        row = {"workload": workload}
        errors = runtime_policy_errors(runtime, row, spec)
        outcomes.append(
            {
                "workload": workload,
                "mode": mode,
                "delayMs": delay_ms,
                "gaWallClockBudgetMode": runtime.get(
                    "gaWallClockBudgetMode"
                ),
                "coordinatorTickIntervalMs": runtime.get(
                    "coordinatorTickIntervalMs"
                ),
                "limitations": limitations,
                "errors": errors,
            }
        )
    errors = [
        error
        for outcome in outcomes
        for error in outcome["errors"]
    ]
    report = {
        "status": (
            "PASS_V3D_RUNTIME_POLICY_SELF_TEST"
            if not errors
            else "FAIL_V3D_RUNTIME_POLICY_SELF_TEST"
        ),
        "policyId": spec["runtimePolicy"]["policyId"],
        "errors": errors,
        "outcomes": outcomes,
        "mosaicExecuted": False,
    }
    write_json(
        repo
        / spec["paths"]["preparationAuditRoot"]
        / "runtime-policy-self-test.json",
        report,
    )
    return report

def main() -> int:
    args = parse_args()
    repo = Path(args.repo_root).resolve()
    spec_path = Path(args.spec).resolve()
    spec = read_json(spec_path)
    audit_root = repo / spec["paths"]["preparationAuditRoot"]
    audit_root.mkdir(parents=True, exist_ok=True)

    if args.mode == "policy-test":
        report = runtime_policy_self_test(repo, spec_path, spec)
        print(json.dumps(report, indent=2))
        return 0 if report["status"].startswith("PASS_") else 1

    check = static_check(repo, spec)
    write_json(audit_root / "materialization-static-check.json", check)
    if check["errors"]:
        print(json.dumps(check, indent=2))
        return 1
    if args.mode == "check":
        print(json.dumps(check, indent=2))
        return 0

    legacy = load_legacy(repo, spec_path)
    if args.mode == "verify":
        report = verify_all(repo, spec_path, spec, legacy)
        print(json.dumps({
            "status": report["status"],
            "planned": report["planned"],
            "valid": report["valid"],
            "counts": report["counts"],
            "reportPath": str(
                repo / spec["paths"]["preparationAuditRoot"]
                / "materialization-verification.json"
            ),
        }, indent=2))
        return 0 if report["status"].startswith("PASS_") else 1

    plan = read_csv(repo / spec["paths"]["scenarioInstancePlan"])
    configs = read_csv(repo / spec["paths"]["scenarioConfigurationMapping"])
    config_by_id = {row["config_id"]: row for row in configs}
    if args.mode == "one":
        selected = [row for row in plan if row["materialization_id"] == args.materialization_id]
        if len(selected) != 1:
            raise RuntimeError("--materialization-id must identify exactly one row")
    else:
        selected = plan

    scenario_convert = legacy.find_scenario_convert(repo, args.scenario_convert)
    scenario_convert_root = legacy.scenario_convert_root(scenario_convert)
    legacy.verify_scenario_convert(scenario_convert_root)
    intas_root = Path(args.intas_root).resolve()
    if not intas_root.exists():
        raise RuntimeError(f"InTAS root not found: {intas_root}")

    states_root = repo / spec["paths"]["stateRoot"] / "materializations"
    states_root.mkdir(parents=True, exist_ok=True)
    results = []
    for index, row in enumerate(selected, start=1):
        print(
            f"[{index}/{len(selected)}] MATERIALIZE {row['materialization_id']} "
            f"-> {row['target_directory']}",
            flush=True,
        )
        try:
            archived_target = archive_incompatible_target(repo, row, spec)
            result = legacy.materialize_one(
                row,
                config_by_id,
                repo,
                spec,
                intas_root,
                scenario_convert_root,
                scenario_convert,
            )
        except Exception as exc:  # noqa: BLE001
            result = {
                "materializationId": row["materialization_id"],
                "configId": row["config_id"],
                "groupId": row["group_id"],
                "targetDirectory": row["target_directory"],
                "status": "FAILED_MATERIALIZATION",
                "errors": [str(exc)],
                "warnings": [],
            }
        if "archived_target" in locals() and archived_target:
            result["archivedPreviousTarget"] = archived_target
        archived_target = ""
        result["recordedAtUtc"] = utc_now()
        results.append(result)
        write_json(states_root / f"{row['materialization_id']}.json", result)
        print(json.dumps({
            "materializationId": result["materializationId"],
            "status": result["status"],
            "errors": result.get("errors", []),
        }), flush=True)

    counts = Counter(item["status"] for item in results)
    summary = {
        "status": (
            "PASS_V3D_MATERIALIZATION_ATTEMPTS_COMPLETE"
            if all(item["status"] in {"MATERIALIZED_VALIDATED", "MATERIALIZED_WITH_WARNINGS"} for item in results)
            else "V3D_MATERIALIZATION_ATTEMPTS_COMPLETE_WITH_FAILURES"
        ),
        "generatedAtUtc": utc_now(),
        "attempted": len(results),
        "counts": dict(counts),
        "results": results,
        "mosaicExecuted": False,
    }
    write_json(audit_root / "materialization-attempt-summary.json", summary)

    verification = verify_all(repo, spec_path, spec, legacy)
    print(json.dumps({
        "status": verification["status"],
        "attempted": summary["attempted"],
        "attemptCounts": summary["counts"],
        "verified": verification["valid"],
        "verificationCounts": verification["counts"],
        "attemptReportPath": str(
            audit_root / "materialization-attempt-summary.json"
        ),
        "verificationReportPath": str(
            audit_root / "materialization-verification.json"
        ),
        "mosaicExecuted": False,
    }, indent=2))
    return 0 if verification["status"].startswith("PASS_") else 2


if __name__ == "__main__":
    sys.exit(main())
