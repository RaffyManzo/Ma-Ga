#!/usr/bin/env python3
"""Fail-soft sequential batch runner for the V3-D final campaign."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
import time
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
    parser.add_argument(
        "--mode",
        choices=["dry-run", "self-test", "ready", "execute", "report", "_fake-child"],
        required=True,
    )
    parser.add_argument("--repo-root", default=str(REPO_ROOT))
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    parser.add_argument("--mosaic-root", default="tmp/mosaic-25.2")
    parser.add_argument("--behavior", default="")
    parser.add_argument("--fake-output", default="")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_csv(path: Path, rows: list[dict[str, Any]], fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def utc_now() -> str:
    return datetime.now(UTC).isoformat()


def tree_fingerprint(root: Path) -> dict[str, Any]:
    if not root.is_dir():
        raise RuntimeError(f"materialization root missing: {root}")
    digest = hashlib.sha256()
    files = sorted(path for path in root.rglob("*") if path.is_file())
    total_size = 0
    for path in files:
        relative = path.relative_to(root).as_posix()
        size = path.stat().st_size
        file_hash = sha256_file(path)
        total_size += size
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(str(size).encode("ascii"))
        digest.update(b"\0")
        digest.update(file_hash.encode("ascii"))
        digest.update(b"\n")
    return {
        "root": str(root),
        "fileCount": len(files),
        "sizeBytes": total_size,
        "treeSHA256": digest.hexdigest().upper(),
    }


def pid_is_running(pid: int) -> bool:
    if pid <= 0:
        return False
    if os.name == "nt":
        completed = subprocess.run(
            [
                "powershell.exe",
                "-NoProfile",
                "-Command",
                (
                    "$p=Get-Process -Id "
                    + str(pid)
                    + " -ErrorAction SilentlyContinue; "
                    + "if($null -ne $p){exit 0}else{exit 1}"
                ),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
        return completed.returncode == 0
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return True


def acquire_lock(lock_path: Path) -> dict[str, Any]:
    archived_stale_lock = ""
    if lock_path.exists():
        try:
            first_line = lock_path.read_text(
                encoding="utf-8", errors="replace"
            ).splitlines()[0]
            prior_pid = int(first_line)
        except Exception:  # noqa: BLE001
            prior_pid = -1
        if pid_is_running(prior_pid):
            raise RuntimeError(
                f"batch lock is active for PID {prior_pid}: {lock_path}"
            )
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
        stale_path = lock_path.with_name(f"batch.lock.stale-{stamp}")
        lock_path.replace(stale_path)
        archived_stale_lock = str(stale_path)

    descriptor = os.open(
        lock_path,
        os.O_CREAT | os.O_EXCL | os.O_WRONLY,
    )
    try:
        os.write(
            descriptor,
            f"{os.getpid()}\n{utc_now()}\n".encode("utf-8"),
        )
    finally:
        os.close(descriptor)
    return {"lockPath": str(lock_path), "archivedStaleLock": archived_stale_lock}


def git(repo: Path, *args: str, check: bool = True) -> str:
    completed = subprocess.run(
        ["git", *args],
        cwd=repo,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and completed.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {completed.stderr.strip()}")
    return completed.stdout.strip()


def resolve_under(repo: Path, relative: str, allowed_root: str) -> Path:
    path = (repo / relative).resolve()
    root = (repo / allowed_root).resolve()
    path.relative_to(root)
    return path


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


def load(repo: Path, spec_path: Path):
    spec = read_json(spec_path)
    executions = read_csv(repo / spec["paths"]["executionPlan"])
    materializations = read_csv(repo / spec["paths"]["scenarioInstancePlan"])
    return spec, executions, materializations


def validate_repository(
    repo: Path,
    spec: dict[str, Any],
    require_clean: bool,
    check_remote: bool = True,
) -> dict[str, Any]:
    errors: list[str] = []
    branch = git(repo, "branch", "--show-current")
    head = git(repo, "rev-parse", "HEAD")
    if branch != spec["campaignBranch"]:
        errors.append(f"branch: expected {spec['campaignBranch']}, found {branch}")
    ancestor = subprocess.run(
        ["git", "merge-base", "--is-ancestor", spec["baselineCommit"], head],
        cwd=repo,
        check=False,
    ).returncode == 0
    if not ancestor:
        errors.append("baseline commit is not an ancestor of HEAD")
    status = git(repo, "status", "--porcelain", "--untracked-files=all")
    if require_clean and status:
        errors.append("working tree is not clean")
    remote = ""
    if check_remote:
        remote_line = git(
            repo,
            "ls-remote",
            "--heads",
            "origin",
            spec["campaignBranch"],
        )
        remote = remote_line.split()[0] if remote_line else ""
        if require_clean and remote != head:
            errors.append("local and remote campaign branch are not aligned")

    dependency_keys = (
        "runScenarioScript",
        "deployScenarioScript",
        "runtimeArtifactValidatorScript",
        "runSummarizerScript",
        "smokeRunValidatorScript",
        "canonicalMaterializedValidator",
    )
    for key in dependency_keys:
        dependency = repo / spec["paths"][key]
        if not dependency.is_file():
            errors.append(f"required execution dependency missing: {dependency}")

    jar = repo / spec["paths"]["runtimeJar"]
    if not jar.is_file():
        errors.append(f"runtime JAR missing: {jar}")
    else:
        if jar.stat().st_size != int(spec["runtimeArtifact"]["sizeBytes"]):
            errors.append("runtime JAR size mismatch")
        if sha256_file(jar) != spec["runtimeArtifact"]["sha256"]:
            errors.append("runtime JAR SHA-256 mismatch")
        if not zipfile.is_zipfile(jar):
            errors.append("runtime JAR is not a valid ZIP/JAR archive")

    ad_hoc_jar = repo / spec["paths"]["adHocDiagnosticJar"]
    if not ad_hoc_jar.is_file():
        errors.append(f"ad-hoc diagnostic JAR missing: {ad_hoc_jar}")
    else:
        if ad_hoc_jar.stat().st_size <= 0:
            errors.append("ad-hoc diagnostic JAR is empty")
        if not zipfile.is_zipfile(ad_hoc_jar):
            errors.append("ad-hoc diagnostic JAR is not a valid ZIP/JAR archive")
    return {
        "branch": branch,
        "head": head,
        "remote": remote,
        "workingTreeClean": not bool(status),
        "errors": errors,
    }


def verify_materializations(repo: Path, spec: dict[str, Any], materializations: list[dict[str, str]]) -> dict[str, Any]:
    errors: list[str] = []
    entries = []
    valid_statuses = {"MATERIALIZED_VALIDATED", "MATERIALIZED_WITH_WARNINGS"}
    for row in materializations:
        root = resolve_under(repo, row["target_directory"], spec["paths"]["campaignScenarioRoot"])
        manifest_path = root / "final_campaign_manifest.json"
        validation_path = root / "reports/final_campaign_validation_report.json"
        database_path = root / "application/intas_literature_urban.db"
        runtime_path = root / "application/ma_ga_live_runtime_config.json"
        item_errors = []
        if not manifest_path.is_file():
            item_errors.append("manifest missing")
        if not validation_path.is_file():
            item_errors.append("validation report missing")
        if not database_path.is_file():
            item_errors.append("MOSAIC database missing")
        if not runtime_path.is_file():
            item_errors.append("runtime config missing")
        status = "MISSING"
        manifest_hash = ""
        validation_hash = ""
        if not item_errors:
            manifest = read_json(manifest_path)
            validation = read_json(validation_path)
            runtime = read_json(runtime_path)
            item_errors.extend(runtime_policy_errors(runtime, row, spec))
            if manifest.get("runtimePolicyId") != spec["runtimePolicy"]["policyId"]:
                item_errors.append("runtimePolicyId mismatch")
            manifest_policy = manifest.get("runtimePolicy", {})
            for key, expected in expected_runtime_policy(row, spec).items():
                if manifest_policy.get(key) != expected:
                    item_errors.append(
                        f"manifest runtime policy {key} mismatch"
                    )
            status = validation.get("status", "MISSING")
            if status not in valid_statuses:
                item_errors.append(f"invalid materialization status: {status}")
            expected_seed = int(row["seed"])
            expected_workload = expected_seed + int(spec["seedPolicy"]["workloadSeedOffset"])
            checks = {
                "materializationId": (manifest.get("materializationId"), row["materialization_id"]),
                "configId": (manifest.get("configId"), row["config_id"]),
                "campaignSeed": (int(manifest.get("campaignSeed", -1)), expected_seed),
                "mobilitySeed": (int(manifest.get("mobilitySeed", -1)), expected_seed),
                "workloadSeed": (int(manifest.get("workloadSeed", -1)), expected_workload),
                "campaignBranch": (manifest.get("campaignBranch"), spec["campaignBranch"]),
                "frozenCommit": (manifest.get("frozenCommit"), spec["baselineCommit"]),
            }
            for label, (actual, expected) in checks.items():
                if actual != expected:
                    item_errors.append(f"{label} mismatch")
            manifest_hash = sha256_file(manifest_path)
            validation_hash = sha256_file(validation_path)
        if item_errors:
            errors.append(f"{row['materialization_id']}: {'; '.join(item_errors)}")
        entries.append({
            "materializationId": row["materialization_id"],
            "status": status,
            "root": row["target_directory"],
            "manifestSHA256": manifest_hash,
            "validationSHA256": validation_hash,
            "errors": item_errors,
        })
    return {
        "status": "PASS_V3D_MATERIALIZATIONS_READY" if not errors else "FAIL",
        "errors": errors,
        "entries": entries,
    }


def command_for_run(
    repo: Path,
    spec: dict[str, Any],
    row: dict[str, str],
    mosaic_root: Path,
) -> list[str]:
    script = repo / spec["paths"]["runScenarioScript"]
    jar = repo / spec["paths"]["runtimeJar"]
    if not script.is_file():
        raise RuntimeError(f"run scenario script missing: {script}")
    if not jar.is_file():
        raise RuntimeError(f"runtime JAR missing: {jar}")
    scenario_root = resolve_under(
        repo,
        row["materialized_scenario_root"],
        spec["paths"]["campaignScenarioRoot"],
    )
    return [
        "powershell.exe",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        str(script),
        "-MaterializedScenarioRoot",
        str(scenario_root),
        "-MosaicRoot",
        str(mosaic_root),
        "-ScenarioName",
        spec["scenarioName"],
        "-RealtimeBrakeFactor",
        row["realtime_brake_factor"],
        "-PrintDetailedLiveReport",
        "-RuntimeArtifactMode",
        spec["runtimeArtifact"]["mode"],
        "-RuntimeJarPath",
        str(jar),
        "-ExpectedRuntimeJarSha256",
        spec["runtimeArtifact"]["sha256"],
        "-ExpectedRuntimeJarSizeBytes",
        str(spec["runtimeArtifact"]["sizeBytes"]),
    ]



def validate_required_executables(spec: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    for executable in spec["executionPolicy"]["requiredExecutables"]:
        if shutil.which(executable) is None:
            errors.append(f"required executable not found on PATH: {executable}")
    return errors


def validate_powershell_dependencies(
    repo: Path,
    spec: dict[str, Any],
) -> list[str]:
    if os.name != "nt":
        return []
    keys = (
        "runScenarioScript",
        "deployScenarioScript",
        "runtimeArtifactValidatorScript",
        "runSummarizerScript",
        "smokeRunValidatorScript",
    )
    paths = [str((repo / spec["paths"][key]).resolve()) for key in keys]
    encoded = json.dumps(paths)
    command = (
        "$ErrorActionPreference='Stop';"
        "$paths=ConvertFrom-Json @'\n"
        + encoded
        + "\n'@;"
        "$failures=@();"
        "foreach($path in $paths){"
        "$tokens=$null;$errors=$null;"
        "[void][Management.Automation.Language.Parser]::ParseFile("
        "$path,[ref]$tokens,[ref]$errors);"
        "if(@($errors).Count -gt 0){"
        "$failures += [pscustomobject]@{Path=$path;Errors=@($errors|ForEach-Object{$_.Message})}"
        "}"
        "};"
        "$failures|ConvertTo-Json -Compress -Depth 5"
    )
    completed = subprocess.run(
        ["powershell.exe", "-NoProfile", "-Command", command],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=120,
        check=False,
    )
    if completed.returncode != 0:
        return [
            "PowerShell dependency parser invocation failed: "
            + (completed.stderr or completed.stdout).strip()
        ]
    text = completed.stdout.strip()
    if not text:
        return []
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        return [f"PowerShell dependency parser returned invalid JSON: {text!r}"]
    failures = payload if isinstance(payload, list) else [payload]
    return [
        f"PowerShell parser errors in {item.get('Path')}: "
        + "; ".join(item.get("Errors", []))
        for item in failures
        if item
    ]

def dry_run(repo: Path, spec: dict[str, Any], executions, materializations, mosaic_root: Path) -> dict[str, Any]:
    repo_state = validate_repository(
        repo,
        spec,
        require_clean=False,
        check_remote=False,
    )
    materialization_state = verify_materializations(repo, spec, materializations)
    errors = (
        list(repo_state["errors"])
        + list(materialization_state["errors"])
        + validate_required_executables(spec)
        + validate_powershell_dependencies(repo, spec)
    )
    warnings: list[str] = []
    commands = []

    free_disk = shutil.disk_usage(repo).free
    minimum_free = int(spec["executionPolicy"]["minimumFreeDiskBytes"])
    if free_disk < minimum_free:
        errors.append(
            f"insufficient free disk: required at least {minimum_free}, found {free_disk}"
        )

    if not mosaic_root.is_dir():
        errors.append(f"MOSAIC root missing: {mosaic_root}")
    elif not (mosaic_root / "mosaic.bat").is_file():
        errors.append(f"mosaic.bat missing: {mosaic_root}")
    elif os.name == "nt":
        try:
            help_process = subprocess.run(
                [
                    os.environ.get("COMSPEC", "cmd.exe"),
                    "/d",
                    "/c",
                    "mosaic.bat --help",
                ],
                cwd=mosaic_root,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                timeout=60,
                check=False,
            )
            if "--realtime-brake" not in (help_process.stdout or ""):
                errors.append("local MOSAIC help does not expose --realtime-brake")
        except Exception as exc:  # noqa: BLE001
            errors.append(f"MOSAIC realtime-brake help check failed: {exc}")
    else:
        warnings.append("MOSAIC realtime-brake help check skipped on non-Windows validator host")

    for row in executions:
        try:
            command = command_for_run(repo, spec, row, mosaic_root)
            result_root = resolve_under(repo, row["result_root"], spec["paths"]["resultsRoot"])
            commands.append({
                "sequence": int(row["sequence"]),
                "runId": row["run_id"],
                "groupId": row["group_id"],
                "command": command,
                "resultRoot": str(result_root),
                "timeoutSeconds": int(row["timeout_seconds"]),
            })
        except Exception as exc:  # noqa: BLE001
            errors.append(f"{row['run_id']}: {exc}")
    report = {
        "status": "PASS_V3D_BATCH_DRY_RUN" if not errors else "FAIL_V3D_BATCH_DRY_RUN",
        "generatedAtUtc": utc_now(),
        "errors": errors,
        "warnings": warnings,
        "runCount": len(executions),
        "materializationCount": len(materializations),
        "freeDiskBytes": free_disk,
        "minimumFreeDiskBytes": minimum_free,
        "commands": commands,
        "mosaicExecuted": False,
    }
    audit_root = repo / spec["paths"]["preparationAuditRoot"]
    write_json(audit_root / "batch-dry-run.json", report)
    return report


REPORT_FIELDS = [
    "sequence",
    "runId",
    "groupId",
    "configId",
    "seed",
    "status",
    "startedAtUtc",
    "finishedAtUtc",
    "elapsedWallClockSeconds",
    "mosaicRunDirectory",
    "runnerExitCode",
    "error",
    "gaJobsCompleted",
    "gaJobsApplied",
    "gaJobsDiscardedAsStale",
    "staleRatioPercent",
]


def load_states(state_dir: Path) -> list[dict[str, Any]]:
    states = []
    if state_dir.is_dir():
        for path in sorted(state_dir.glob("*.json")):
            try:
                states.append(read_json(path))
            except Exception as exc:  # noqa: BLE001
                states.append({
                    "runId": path.stem,
                    "groupId": "UNKNOWN",
                    "configId": "UNKNOWN",
                    "seed": "",
                    "status": "FAILED_POSTPROCESS",
                    "error": f"malformed state file: {exc}",
                    "metrics": {},
                })
    return states


def generate_reports(repo: Path, spec: dict[str, Any], executions) -> dict[str, Any]:
    state_dir = repo / spec["paths"]["stateRoot"] / "runs"
    reports_root = repo / spec["paths"]["reportsRoot"]
    reports_root.mkdir(parents=True, exist_ok=True)
    states_by_id = {item["runId"]: item for item in load_states(state_dir)}
    rows = []
    for run in executions:
        state = states_by_id.get(run["run_id"])
        if state is None:
            state = {
                "sequence": int(run["sequence"]),
                "runId": run["run_id"],
                "groupId": run["group_id"],
                "configId": run["config_id"],
                "seed": int(run["seed"]),
                "status": "NOT_ATTEMPTED",
                "startedAtUtc": "",
                "finishedAtUtc": "",
                "elapsedWallClockSeconds": "",
                "mosaicRunDirectory": "",
                "runnerExitCode": "",
                "error": "",
                "metrics": {},
            }
        metrics = state.get("metrics", {})
        rows.append({
            "sequence": int(run["sequence"]),
            "runId": run["run_id"],
            "groupId": run["group_id"],
            "configId": run["config_id"],
            "seed": int(run["seed"]),
            "status": state["status"],
            "startedAtUtc": state.get("startedAtUtc", ""),
            "finishedAtUtc": state.get("finishedAtUtc", ""),
            "elapsedWallClockSeconds": state.get("elapsedWallClockSeconds", ""),
            "mosaicRunDirectory": state.get("mosaicRunDirectory", ""),
            "runnerExitCode": state.get("runnerExitCode", ""),
            "error": state.get("error", ""),
            "gaJobsCompleted": metrics.get("gaJobsCompleted", ""),
            "gaJobsApplied": metrics.get("gaJobsApplied", ""),
            "gaJobsDiscardedAsStale": metrics.get("gaJobsDiscardedAsStale", ""),
            "staleRatioPercent": metrics.get("staleRatioPercent", ""),
        })

    summaries = {}
    for group in [*spec["groupOrder"], "ALL"]:
        selected = rows if group == "ALL" else [row for row in rows if row["groupId"] == group]
        counts = Counter(row["status"] for row in selected)
        summary = {
            "status": (
                "COMPLETE"
                if selected and all(row["status"] != "NOT_ATTEMPTED" for row in selected)
                else "INCOMPLETE"
            ),
            "groupId": group,
            "plannedRuns": len(selected),
            "statusCounts": dict(counts),
            "passedRuns": counts.get("PASSED", 0),
            "failedRuns": sum(
                count for status, count in counts.items() if status.startswith("FAILED_")
            ),
            "notAttemptedRuns": counts.get("NOT_ATTEMPTED", 0),
            "generatedAtUtc": utc_now(),
        }
        prefix = "cumulative" if group == "ALL" else group
        write_csv(reports_root / f"{prefix}_runs.csv", selected, REPORT_FIELDS)
        write_json(reports_root / f"{prefix}_summary.json", summary)
        lines = [
            f"# V3-D campaign report: {group}",
            "",
            f"- planned runs: `{summary['plannedRuns']}`",
            f"- passed runs: `{summary['passedRuns']}`",
            f"- failed runs: `{summary['failedRuns']}`",
            f"- not attempted: `{summary['notAttemptedRuns']}`",
            "",
            "| Run | Config | Seed | Status | Applied | Stale |",
            "|---|---|---:|---|---:|---:|",
        ]
        for row in selected:
            lines.append(
                f"| {row['runId']} | {row['configId']} | {row['seed']} | "
                f"{row['status']} | {row['gaJobsApplied']} | "
                f"{row['gaJobsDiscardedAsStale']} |"
            )
        (reports_root / f"{prefix}_report.md").write_text(
            "\n".join(lines) + "\n", encoding="utf-8"
        )
        summaries[group] = summary
    return summaries


def fake_child(behavior: str, output: Path) -> int:
    output.mkdir(parents=True, exist_ok=True)
    if behavior == "PASS":
        write_json(output / "result.json", {"status": "PASS"})
        return 0
    if behavior == "POSTPROCESS":
        write_json(output / "result.json", {"status": "MISSING_SUMMARY"})
        return 0
    write_json(output / "result.json", {"status": "FAIL"})
    return 7


def self_test(repo: Path, spec: dict[str, Any]) -> dict[str, Any]:
    outcomes = []
    with tempfile.TemporaryDirectory(prefix="v3d-batch-self-test-") as temp:
        root = Path(temp)
        for index, behavior in enumerate(("PASS", "FAIL", "PASS"), start=1):
            output = root / f"run-{index}"
            completed = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).resolve()),
                    "--mode",
                    "_fake-child",
                    "--behavior",
                    behavior,
                    "--fake-output",
                    str(output),
                ],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            outcomes.append({
                "index": index,
                "behavior": behavior,
                "exitCode": completed.returncode,
                "continued": True,
                "resultExists": (output / "result.json").is_file(),
            })
        passed = (
            len(outcomes) == 3
            and outcomes[0]["exitCode"] == 0
            and outcomes[1]["exitCode"] != 0
            and outcomes[2]["exitCode"] == 0
            and all(item["resultExists"] for item in outcomes)
        )
    report = {
        "status": "PASS_V3D_FAIL_SOFT_SELF_TEST" if passed else "FAIL",
        "generatedAtUtc": utc_now(),
        "outcomes": outcomes,
        "automaticRetry": False,
        "mosaicExecuted": False,
    }
    write_json(repo / spec["paths"]["preparationAuditRoot"] / "fail-soft-self-test.json", report)
    return report


def readiness_file_set(repo: Path, spec_path: Path, spec: dict[str, Any], materialization_state):
    files = [
        spec_path,
        repo / spec["paths"]["sourceMatrix"],
        repo / spec["paths"]["scenarioInstancePlan"],
        repo / spec["paths"]["scenarioConfigurationMapping"],
        repo / spec["paths"]["testIdGroupMapping"],
        repo / spec["paths"]["executionPlan"],
        SCRIPT_DIR / "validate_v3d_tooling.py",
        SCRIPT_DIR / "prepare_v3d_materializations.py",
        SCRIPT_DIR / "run_v3d_campaign.py",
        SCRIPT_DIR / "v3d_final_campaign.ps1",
        repo / spec["paths"]["runScenarioScript"],
        repo / spec["paths"]["deployScenarioScript"],
        repo / spec["paths"]["runtimeArtifactValidatorScript"],
        repo / spec["paths"]["runSummarizerScript"],
        repo / spec["paths"]["smokeRunValidatorScript"],
        repo / spec["paths"]["runtimeJar"],
        repo / spec["paths"]["adHocDiagnosticJar"],
    ]
    records = []
    for path in files:
        records.append({
            "path": str(path.relative_to(repo)).replace("\\", "/"),
            "sizeBytes": path.stat().st_size,
            "sha256": sha256_file(path),
        })
    for entry in materialization_state["entries"]:
        root = repo / entry["root"]
        fingerprint = tree_fingerprint(root)
        records.append({
            "path": str(root.relative_to(repo)).replace("\\", "/"),
            "type": "MATERIALIZATION_TREE",
            "fileCount": fingerprint["fileCount"],
            "sizeBytes": fingerprint["sizeBytes"],
            "treeSHA256": fingerprint["treeSHA256"],
        })
    return records


def ready(repo: Path, spec_path: Path, spec: dict[str, Any], executions, materializations, mosaic_root: Path):
    repo_state = validate_repository(repo, spec, require_clean=True)
    dry = dry_run(repo, spec, executions, materializations, mosaic_root)
    test = self_test(repo, spec)
    materialization_state = verify_materializations(repo, spec, materializations)
    errors = (
        list(repo_state["errors"])
        + list(dry["errors"])
        + list(materialization_state["errors"])
    )
    if not test["status"].startswith("PASS_"):
        errors.append("fail-soft self-test failed")
    marker = {
        "status": "PASS_V3D_CAMPAIGN_READY" if not errors else "FAIL_V3D_CAMPAIGN_READY",
        "generatedAtUtc": utc_now(),
        "branch": repo_state["branch"],
        "head": repo_state["head"],
        "remote": repo_state["remote"],
        "errors": errors,
        "plannedRuns": len(executions),
        "materializations": len(materializations),
        "pacingFactor": spec["pacing"]["factor"],
        "mosaicRoot": str(mosaic_root.resolve()),
        "mosaicBatSHA256": (
            sha256_file(mosaic_root / "mosaic.bat")
            if not errors
            else ""
        ),
        "fileSet": (
            readiness_file_set(repo, spec_path, spec, materialization_state)
            if not errors
            else []
        ),
    }
    marker_path = repo / spec["paths"]["stateRoot"] / "campaign-ready.json"
    write_json(marker_path, marker)
    return marker


def verify_ready_marker(
    repo: Path,
    spec: dict[str, Any],
    mosaic_root: Path,
) -> dict[str, Any]:
    marker_path = repo / spec["paths"]["stateRoot"] / "campaign-ready.json"
    if not marker_path.is_file():
        raise RuntimeError("campaign-ready.json is missing; run PrepareAll first")
    marker = read_json(marker_path)
    if marker.get("status") != "PASS_V3D_CAMPAIGN_READY":
        raise RuntimeError("campaign readiness marker is not PASS")
    current = validate_repository(
        repo,
        spec,
        require_clean=True,
        check_remote=False,
    )
    if current["errors"]:
        raise RuntimeError("; ".join(current["errors"]))
    if current["head"] != marker.get("head"):
        raise RuntimeError("Git HEAD changed after readiness freeze")
    if str(mosaic_root.resolve()) != marker.get("mosaicRoot"):
        raise RuntimeError("MOSAIC root changed after readiness freeze")
    mosaic_bat = mosaic_root / "mosaic.bat"
    if not mosaic_bat.is_file():
        raise RuntimeError("mosaic.bat is missing after readiness freeze")
    if sha256_file(mosaic_bat) != marker.get("mosaicBatSHA256"):
        raise RuntimeError("mosaic.bat changed after readiness freeze")
    for record in marker.get("fileSet", []):
        path = repo / record["path"]
        if record.get("type") == "MATERIALIZATION_TREE":
            fingerprint = tree_fingerprint(path)
            if fingerprint["fileCount"] != int(record["fileCount"]):
                raise RuntimeError(
                    f"ready materialization file count changed: {record['path']}"
                )
            if fingerprint["sizeBytes"] != int(record["sizeBytes"]):
                raise RuntimeError(
                    f"ready materialization size changed: {record['path']}"
                )
            if fingerprint["treeSHA256"] != record["treeSHA256"]:
                raise RuntimeError(
                    f"ready materialization tree changed: {record['path']}"
                )
            continue
        if not path.is_file():
            raise RuntimeError(f"ready file missing: {record['path']}")
        if path.stat().st_size != int(record["sizeBytes"]):
            raise RuntimeError(f"ready file size changed: {record['path']}")
        if sha256_file(path) != record["sha256"]:
            raise RuntimeError(f"ready file hash changed: {record['path']}")
    return marker


def process_ids(mosaic_root: Path, scenario_name: str) -> list[dict[str, Any]]:
    if os.name != "nt":
        return []
    escaped_root = str(mosaic_root).replace("'", "''")
    escaped_scenario = scenario_name.replace("'", "''")
    command = (
        "$ErrorActionPreference='Stop'; "
        "$items=@(Get-CimInstance Win32_Process -ErrorAction Stop | "
        "Where-Object { $_.Name -match '^(java|sumo|sumo-gui)\\.exe$' -and "
        f"($_.CommandLine -like '*{escaped_root}*' -or "
        f"$_.CommandLine -like '*{escaped_scenario}*') }} | "
        "Select-Object ProcessId,Name,CommandLine); "
        "if($items.Count -eq 0){'[]'}else{$items | ConvertTo-Json -Compress}"
    )
    completed = subprocess.run(
        ["powershell.exe", "-NoProfile", "-Command", command],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=60,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(
            "residual-process query failed: "
            + (completed.stderr or completed.stdout).strip()
        )
    text = completed.stdout.strip()
    if not text:
        raise RuntimeError("residual-process query returned no JSON")
    try:
        payload = json.loads(text)
    except json.JSONDecodeError as exc:
        raise RuntimeError(
            f"residual-process query returned invalid JSON: {text!r}"
        ) from exc
    if payload is None:
        return []
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict):
        return [payload]
    raise RuntimeError(
        f"residual-process query returned unexpected payload: {type(payload)}"
    )


def cleanup_processes(mosaic_root: Path, scenario_name: str) -> dict[str, Any]:
    before = process_ids(mosaic_root, scenario_name)
    kill_results = []
    for item in before:
        completed = subprocess.run(
            ["taskkill", "/PID", str(item["ProcessId"]), "/T", "/F"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        kill_results.append({
            "processId": item["ProcessId"],
            "exitCode": completed.returncode,
            "stdout": completed.stdout.strip(),
            "stderr": completed.stderr.strip(),
        })
    time.sleep(1.0)
    after = process_ids(mosaic_root, scenario_name)
    return {
        "before": before,
        "killResults": kill_results,
        "after": after,
        "success": not after,
    }


def detect_new_run(logs_root: Path, scenario_name: str, before: set[str]) -> Path | None:
    candidates = [
        path for path in logs_root.glob(f"*-{scenario_name}")
        if path.is_dir() and path.name not in before
    ]
    if not candidates:
        return None
    return max(candidates, key=lambda path: path.stat().st_mtime_ns)


def copy_evidence(run_dir: Path, result_root: Path) -> None:
    evidence = result_root / "evidence"
    evidence.mkdir(parents=True, exist_ok=True)
    runtime = run_dir / "live-maga-runtime"
    if runtime.is_dir():
        shutil.copytree(runtime, evidence / "live-maga-runtime", dirs_exist_ok=True)
    for name in ("MOSAIC.log", "PerformanceMeasurements.csv", "RuntimeEvents.csv"):
        source = run_dir / name
        if source.is_file():
            shutil.copy2(source, evidence / name)
    coordinator = run_dir / "apps/server_0/MaGaLiveRuntimeCoordinatorApp.log"
    if coordinator.is_file():
        shutil.copy2(coordinator, evidence / coordinator.name)


def metrics_from_result(result_root: Path) -> dict[str, Any]:
    summary_path = result_root / "evidence/live-maga-runtime/live_run_summary.json"
    smoke_path = result_root / "evidence/live-maga-runtime/literature_smoke_validation.json"
    if not summary_path.is_file():
        raise RuntimeError("live_run_summary.json missing")
    if not smoke_path.is_file():
        raise RuntimeError("literature_smoke_validation.json missing")
    summary = read_json(summary_path)
    smoke = read_json(smoke_path)
    return {
        "simulationCompleted": summary.get("simulationCompleted"),
        "smokeStatus": smoke.get("status"),
        "gaJobsCompleted": summary.get("gaJobsCompleted"),
        "gaJobsApplied": summary.get("gaJobsApplied"),
        "gaJobsDiscardedAsStale": summary.get("gaJobsDiscardedAsStale"),
        "staleRatioPercent": summary.get("staleRatioPercent"),
        "runtimeTicksObserved": summary.get("runtimeTicksObserved"),
    }


def execute(repo: Path, spec: dict[str, Any], executions, mosaic_root: Path) -> int:
    verify_ready_marker(repo, spec, mosaic_root)
    initial_residual = process_ids(mosaic_root, spec["scenarioName"])
    if initial_residual:
        raise RuntimeError(
            "residual MOSAIC/SUMO processes exist before batch start: "
            + json.dumps(initial_residual)
        )

    state_root = repo / spec["paths"]["stateRoot"]
    run_state_dir = state_root / "runs"
    run_state_dir.mkdir(parents=True, exist_ok=True)
    lock_path = state_root / "batch.lock"
    lock_info = acquire_lock(lock_path)
    write_json(state_root / "batch-lock-audit.json", lock_info)

    fatal = False
    try:
        for position, row in enumerate(executions, start=1):
            state_path = run_state_dir / f"{row['run_id']}.json"
            if state_path.is_file():
                prior = read_json(state_path)
                if prior.get("status") in spec["terminalRunStatuses"]:
                    print(
                        f"[{position}/{len(executions)}] SKIP terminal "
                        f"{row['run_id']} ({prior.get('status')})",
                        flush=True,
                    )
                    try:
                        generate_reports(repo, spec, executions)
                    except Exception as exc:  # noqa: BLE001
                        raise RuntimeError(
                            f"report generation failed while resuming: {exc}"
                        ) from exc
                    continue

            try:
                result_root = resolve_under(
                    repo,
                    row["result_root"],
                    spec["paths"]["resultsRoot"],
                )
                if result_root.exists():
                    archive = result_root.parent / (
                        result_root.name
                        + ".preexisting-"
                        + datetime.now().strftime("%Y%m%d-%H%M%S")
                    )
                    result_root.rename(archive)
                result_root.mkdir(parents=True, exist_ok=True)
            except Exception as exc:  # noqa: BLE001
                state = {
                    "sequence": int(row["sequence"]),
                    "runId": row["run_id"],
                    "groupId": row["group_id"],
                    "configId": row["config_id"],
                    "seed": int(row["seed"]),
                    "status": "FAILED_PREFLIGHT",
                    "startedAtUtc": utc_now(),
                    "finishedAtUtc": utc_now(),
                    "elapsedWallClockSeconds": 0.0,
                    "mosaicRunDirectory": "",
                    "runnerExitCode": "",
                    "error": f"result-root setup failed: {exc}",
                    "metrics": {},
                }
                write_json(state_path, state)
                generate_reports(repo, spec, executions)
                print(json.dumps({
                    "runId": state["runId"],
                    "status": state["status"],
                    "error": state["error"],
                }), flush=True)
                continue

            console_path = result_root / "runner-console.txt"
            started = utc_now()
            started_perf = time.perf_counter()
            state = {
                "sequence": int(row["sequence"]),
                "runId": row["run_id"],
                "groupId": row["group_id"],
                "configId": row["config_id"],
                "seed": int(row["seed"]),
                "status": "RUNNING",
                "startedAtUtc": started,
                "finishedAtUtc": "",
                "elapsedWallClockSeconds": "",
                "mosaicRunDirectory": "",
                "runnerExitCode": "",
                "error": "",
                "metrics": {},
            }
            write_json(state_path, state)
            print(f"[{position}/{len(executions)}] RUN {row['run_id']}", flush=True)

            phase = "PREFLIGHT"
            try:
                scenario_root = resolve_under(
                    repo,
                    row["materialized_scenario_root"],
                    spec["paths"]["campaignScenarioRoot"],
                )
                validation = read_json(
                    scenario_root / "reports/final_campaign_validation_report.json"
                )
                if validation.get("status") not in {
                    "MATERIALIZED_VALIDATED",
                    "MATERIALIZED_WITH_WARNINGS",
                }:
                    raise RuntimeError(
                        f"materialization validation status: {validation.get('status')}"
                    )
                logs_root = mosaic_root / "logs"
                logs_root.mkdir(parents=True, exist_ok=True)
                before = {
                    path.name for path in logs_root.glob(f"*-{spec['scenarioName']}")
                    if path.is_dir()
                }
                command = command_for_run(repo, spec, row, mosaic_root)
                phase = "EXECUTION"
                with console_path.open("w", encoding="utf-8", errors="replace") as console:
                    process = subprocess.Popen(
                        command,
                        cwd=repo,
                        stdout=console,
                        stderr=subprocess.STDOUT,
                        text=True,
                    )
                    timed_out = False
                    try:
                        exit_code = process.wait(timeout=int(row["timeout_seconds"]))
                    except subprocess.TimeoutExpired:
                        timed_out = True
                        subprocess.run(
                            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
                            stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE,
                            text=True,
                            check=False,
                        )
                        exit_code = -9

                phase = "POSTPROCESS"
                run_dir = detect_new_run(logs_root, spec["scenarioName"], before)
                if run_dir is not None:
                    state["mosaicRunDirectory"] = str(run_dir)
                    copy_evidence(run_dir, result_root)

                state["runnerExitCode"] = exit_code
                metrics_error = ""
                if run_dir is not None:
                    phase = "VALIDATION"
                    try:
                        state["metrics"] = metrics_from_result(result_root)
                    except Exception as exc:  # noqa: BLE001
                        metrics_error = str(exc)

                if timed_out:
                    state["status"] = "FAILED_TIMEOUT"
                    state["error"] = f"timeout after {row['timeout_seconds']} seconds"
                elif exit_code != 0:
                    if run_dir is None:
                        state["status"] = "FAILED_EXECUTION"
                    elif metrics_error:
                        state["status"] = "FAILED_POSTPROCESS"
                    elif state["metrics"].get("simulationCompleted") is True:
                        state["status"] = "FAILED_VALIDATION"
                    else:
                        state["status"] = "FAILED_EXECUTION"
                    state["error"] = f"runner exit code {exit_code}"
                    if metrics_error:
                        state["error"] += f"; metrics unavailable: {metrics_error}"
                elif run_dir is None:
                    state["status"] = "FAILED_POSTPROCESS"
                    state["error"] = "new MOSAIC run directory not found"
                elif metrics_error:
                    state["status"] = "FAILED_POSTPROCESS"
                    state["error"] = metrics_error
                else:
                    metrics = state["metrics"]
                    if (
                        metrics.get("simulationCompleted") is True
                        and metrics.get("smokeStatus")
                        == "LITERATURE_SMOKE_TEST_PASSED"
                    ):
                        state["status"] = "PASSED"
                    else:
                        state["status"] = "FAILED_VALIDATION"
                        state["error"] = (
                            f"simulationCompleted={metrics.get('simulationCompleted')}; "
                            f"smokeStatus={metrics.get('smokeStatus')}"
                        )
            except Exception as exc:  # noqa: BLE001
                status_by_phase = {
                    "PREFLIGHT": "FAILED_PREFLIGHT",
                    "EXECUTION": "FAILED_EXECUTION",
                    "POSTPROCESS": "FAILED_POSTPROCESS",
                    "VALIDATION": "FAILED_VALIDATION",
                }
                state["status"] = status_by_phase.get(
                    phase,
                    "FAILED_EXECUTION",
                )
                state["error"] = f"{phase.lower()} failure: {exc}"

            try:
                cleanup = cleanup_processes(mosaic_root, spec["scenarioName"])
            except Exception as exc:  # noqa: BLE001
                cleanup = {
                    "before": [],
                    "after": [],
                    "success": False,
                    "error": str(exc),
                }
            write_json(result_root / "cleanup.json", cleanup)
            if not cleanup["success"]:
                state["status"] = "FAILED_CLEANUP"
                state["error"] = (
                    state.get("error", "")
                    + "; residual-process cleanup failed: "
                    + str(cleanup.get("error", "unsafe residual processes remain"))
                ).strip("; ")
                fatal = True

            state["finishedAtUtc"] = utc_now()
            state["elapsedWallClockSeconds"] = round(time.perf_counter() - started_perf, 3)
            write_json(state_path, state)
            write_json(result_root / "run-result.json", state)
            try:
                generate_reports(repo, spec, executions)
            except Exception as exc:  # noqa: BLE001
                state["status"] = "FAILED_POSTPROCESS"
                state["error"] = (
                    state.get("error", "")
                    + f"; report generation failed: {exc}"
                ).strip("; ")
                write_json(state_path, state)
                write_json(result_root / "run-result.json", state)
                fatal = True
            print(
                json.dumps({
                    "runId": state["runId"],
                    "status": state["status"],
                    "error": state["error"],
                }),
                flush=True,
            )
            if fatal:
                print("FATAL: batch safety condition failed; inspect the run state", flush=True)
                break
    finally:
        lock_path.unlink(missing_ok=True)

    try:
        summaries = generate_reports(repo, spec, executions)
    except Exception as exc:  # noqa: BLE001
        write_json(
            repo / spec["paths"]["stateRoot"] / "final-reporting-failure.json",
            {"status": "FAILED_POSTPROCESS", "error": str(exc), "generatedAtUtc": utc_now()},
        )
        return 3
    cumulative = summaries["ALL"]
    write_json(
        repo / spec["paths"]["resultsRoot"] / "batch-summary.json",
        {
            "status": (
                "ABORTED_UNSAFE_ENVIRONMENT"
                if fatal
                else (
                    "COMPLETED_ALL_PASSED"
                    if cumulative["failedRuns"] == 0
                    and cumulative["notAttemptedRuns"] == 0
                    else "COMPLETED_WITH_RECORDED_FAILURES"
                )
            ),
            "generatedAtUtc": utc_now(),
            "summary": cumulative,
        },
    )
    if fatal:
        return 3
    return 0 if cumulative["failedRuns"] == 0 and cumulative["notAttemptedRuns"] == 0 else 2


def main() -> int:
    args = parse_args()
    if args.mode == "_fake-child":
        return fake_child(args.behavior, Path(args.fake_output))

    repo = Path(args.repo_root).resolve()
    spec_path = Path(args.spec).resolve()
    spec, executions, materializations = load(repo, spec_path)
    mosaic_root = Path(args.mosaic_root)
    if not mosaic_root.is_absolute():
        mosaic_root = (repo / mosaic_root).resolve()

    if args.mode == "dry-run":
        report = dry_run(repo, spec, executions, materializations, mosaic_root)
        print(json.dumps({
            "status": report["status"],
            "errors": report["errors"],
            "warnings": report["warnings"],
            "runCount": report["runCount"],
            "materializationCount": report["materializationCount"],
            "freeDiskBytes": report["freeDiskBytes"],
            "reportPath": str(
                repo / spec["paths"]["preparationAuditRoot"]
                / "batch-dry-run.json"
            ),
            "mosaicExecuted": False,
        }, indent=2))
        return 0 if report["status"].startswith("PASS_") else 1
    if args.mode == "self-test":
        report = self_test(repo, spec)
        print(json.dumps(report, indent=2))
        return 0 if report["status"].startswith("PASS_") else 1
    if args.mode == "ready":
        marker = ready(repo, spec_path, spec, executions, materializations, mosaic_root)
        print(json.dumps({
            "status": marker["status"],
            "errors": marker["errors"],
            "branch": marker["branch"],
            "head": marker["head"],
            "plannedRuns": marker["plannedRuns"],
            "materializations": marker["materializations"],
            "frozenFiles": len(marker["fileSet"]),
            "markerPath": str(
                repo / spec["paths"]["stateRoot"] / "campaign-ready.json"
            ),
        }, indent=2))
        return 0 if marker["status"].startswith("PASS_") else 1
    if args.mode == "report":
        summaries = generate_reports(repo, spec, executions)
        print(json.dumps(summaries, indent=2))
        return 0
    if args.mode == "execute":
        return execute(repo, spec, executions, mosaic_root)
    raise RuntimeError(f"unsupported mode: {args.mode}")


if __name__ == "__main__":
    sys.exit(main())
