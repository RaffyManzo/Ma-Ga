#!/usr/bin/env python3
"""Isolated G02B campaign tooling.

This module prepares and validates G02B ablation scenarios without rebuilding
canonical materializations and without launching MOSAIC/SUMO.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import json
import math
import os
import shutil
import statistics
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Any, Iterable


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[2]
SPEC_PATH = SCRIPT_DIR / "g02b_spec.json"
UTC = dt.timezone.utc


class G02BError(RuntimeError):
    pass


def rel(path: Path) -> str:
    try:
        return path.resolve().relative_to(REPO_ROOT.resolve()).as_posix()
    except ValueError:
        return path.resolve().as_posix()


def load_spec() -> dict[str, Any]:
    with SPEC_PATH.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def repo_path(spec: dict[str, Any], key: str) -> Path:
    return (REPO_ROOT / spec["paths"][key]).resolve()


def timestamp() -> str:
    return dt.datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def run_git(args: list[str], check: bool = True) -> str:
    cmd = ["git", "-c", f"safe.directory={REPO_ROOT.as_posix()}", *args]
    proc = subprocess.run(cmd, cwd=REPO_ROOT, text=True, capture_output=True)
    if check and proc.returncode != 0:
        raise G02BError(
            f"git {' '.join(args)} failed with exit code {proc.returncode}\n"
            f"stdout:\n{proc.stdout}\nstderr:\n{proc.stderr}"
        )
    return proc.stdout.strip()


def ensure_ancestor(base: str, head: str = "HEAD") -> None:
    ancestor = subprocess.run(
        ["git", "-c", f"safe.directory={REPO_ROOT.as_posix()}", "merge-base", "--is-ancestor", base, head],
        cwd=REPO_ROOT,
        text=True,
        capture_output=True,
    )
    if ancestor.returncode != 0:
        raise G02BError(f"{base} is not an ancestor of {head}")


def repo_state(require_clean: bool, require_remote_match: bool = False) -> dict[str, str]:
    spec = load_spec()
    implementation_base = spec["implementationBaseCommit"]
    state = {
        "origin": run_git(["remote", "get-url", "origin"]),
        "branch": run_git(["branch", "--show-current"]),
        "head": run_git(["rev-parse", "HEAD"]),
        "remoteHead": run_git(["rev-parse", f"origin/{spec['branch']}"]),
        "baseHead": run_git(["rev-parse", spec["baseBranch"]]),
        "remoteBaseHead": run_git(["rev-parse", f"origin/{spec['baseBranch']}"]),
        "statusShort": run_git(["status", "--short"]),
        "implementationBaseCommit": implementation_base,
    }
    if state["origin"] != spec["origin"]:
        raise G02BError(f"Unexpected origin: {state['origin']}")
    if state["branch"] != spec["branch"]:
        raise G02BError(f"Unexpected branch: {state['branch']}")
    if state["branch"] == spec["baseBranch"]:
        raise G02BError("Refusing to run on testing/final-campaign")
    ensure_ancestor(implementation_base, "HEAD")
    if require_remote_match and state["remoteHead"] != state["head"]:
        raise G02BError(f"Local HEAD {state['head']} does not match origin/{spec['branch']} {state['remoteHead']}")
    if state["baseHead"] != spec["baseHead"]:
        raise G02BError(f"Unexpected base branch HEAD: {state['baseHead']}")
    if state["remoteBaseHead"] != spec["baseHead"]:
        raise G02BError(f"Unexpected remote base HEAD: {state['remoteBaseHead']}")
    java_changes = run_git(["diff", "--name-only", f"{implementation_base}..HEAD", "--", "*.java"])
    if java_changes:
        raise G02BError(f"Java changes were introduced after implementation base:\n{java_changes}")
    if require_clean and state["statusShort"]:
        raise G02BError(f"Working tree is not clean:\n{state['statusShort']}")
    return state


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_runtime_jar(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise G02BError(f"Missing runtime JAR: {rel(path)}")
    if not zipfile.is_zipfile(path):
        raise G02BError(f"Runtime JAR is not a readable ZIP/JAR: {rel(path)}")
    with zipfile.ZipFile(path, "r") as zf:
        names = set(zf.namelist())
    if not any(name.endswith("MaGaExperimentalVariant.class") for name in names):
        raise G02BError(f"Runtime JAR does not contain MaGaExperimentalVariant.class: {rel(path)}")
    return {"path": rel(path), "sha256": sha256_file(path), "sizeBytes": path.stat().st_size}


def json_load(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def json_write(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, indent=2, sort_keys=True)
        handle.write("\n")


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, rows: list[dict[str, Any]], fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({name: row.get(name, "") for name in fieldnames})


def safe_resolve_under(root: Path, relative: str) -> Path:
    candidate = Path(relative)
    if candidate.is_absolute():
        raise G02BError(f"Absolute paths are not allowed: {relative}")
    resolved_root = root.resolve()
    resolved = (resolved_root / candidate).resolve()
    if os.path.commonpath([str(resolved_root), str(resolved)]) != str(resolved_root):
        raise G02BError(f"Path escapes root: {relative}")
    return resolved


def inventory(root: Path) -> dict[str, str]:
    if not root.exists():
        raise G02BError(f"Missing directory: {root}")
    hashes: dict[str, str] = {}
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        hashes[path.relative_to(root).as_posix()] = sha256_file(path)
    return hashes


def require_ignored(path: Path) -> None:
    proc = subprocess.run(
        ["git", "-c", f"safe.directory={REPO_ROOT.as_posix()}", "check-ignore", "-q", str(path)],
        cwd=REPO_ROOT,
    )
    if proc.returncode != 0:
        raise G02BError(f"Path must be ignored before G02B writes to it: {rel(path)}")


def official_plan_rows(spec: dict[str, Any]) -> list[dict[str, str]]:
    plan_path = repo_path(spec, "officialPlan")
    if not plan_path.exists():
        raise G02BError(f"Missing official final-campaign plan: {rel(plan_path)}")
    return read_csv(plan_path)


def baseline_metrics_rows(spec: dict[str, Any]) -> list[dict[str, str]]:
    path = repo_path(spec, "baselineMetrics")
    if not path.exists():
        raise G02BError(f"Missing G02 baseline metrics: {rel(path)}")
    return read_csv(path)


def source_key(config_id: str, seed: int) -> str:
    return f"{config_id}|{seed}"


def baseline_index(spec: dict[str, Any]) -> dict[str, dict[str, str]]:
    index: dict[str, dict[str, str]] = {}
    for row in baseline_metrics_rows(spec):
        key = source_key(row.get("ConfigId", ""), int(row.get("Seed", "0")))
        index[key] = row
    return index


def source_index(spec: dict[str, Any]) -> dict[str, dict[str, str]]:
    index: dict[str, dict[str, str]] = {}
    for row in official_plan_rows(spec):
        group = row.get("group", "") or row.get("group_id", "")
        config_id = row.get("config_id", "")
        seed = row.get("seed", "")
        if group in {spec["sourceGroup"], spec.get("sourceGroupId", "")} or config_id == spec["smokeConfig"]:
            index[source_key(config_id, int(seed))] = row
    return index


def materialization_path_from_row(row: dict[str, str]) -> Path:
    for key in ("materialization_target", "target_directory", "targetDirectory", "scenario_directory"):
        if row.get(key):
            return safe_resolve_under(REPO_ROOT, row[key])
    config_id = row["config_id"]
    seed = row["seed"]
    spec = load_spec()
    return repo_path(spec, "sourceCampaignRoot") / spec["sourceGroup"] / config_id / seed


def manifest_for_source(source_dir: Path, spec: dict[str, Any]) -> dict[str, Any]:
    manifest_path = source_dir / spec["sourceManifestRelativePath"]
    if not manifest_path.exists():
        raise G02BError(f"Missing final campaign manifest: {rel(manifest_path)}")
    return json_load(manifest_path)


def runtime_config(path: Path, spec: dict[str, Any]) -> dict[str, Any]:
    config_path = path / spec["runtimeConfigRelativePath"]
    if not config_path.exists():
        raise G02BError(f"Missing runtime config: {rel(config_path)}")
    return json_load(config_path)


def row_duration_seconds(row: dict[str, str], manifest: dict[str, Any], config: dict[str, Any]) -> str:
    for key in ("duration_seconds", "durationSeconds"):
        if row.get(key):
            return row[key]
    for source in (manifest, config):
        value = source.get("durationSeconds") or source.get("duration_seconds")
        if value is not None:
            return str(value)
    return ""


def row_field(row: dict[str, str], manifest: dict[str, Any], config: dict[str, Any], *names: str) -> str:
    for name in names:
        if row.get(name):
            return row[name]
        value = manifest.get(name)
        if value is not None:
            return str(value)
        value = config.get(name)
        if value is not None:
            return str(value)
    return ""


PLAN_FIELDS = [
    "run_id",
    "plan_type",
    "config_id",
    "seed",
    "variant",
    "pairing_key",
    "source_materialization_id",
    "source_materialization",
    "source_validation_report",
    "destination_directory",
    "duration_seconds",
    "density",
    "workload",
    "ga_parameter_scaling_mode",
    "status",
    "output_path",
    "baseline_run_id",
    "baseline_summary",
    "baseline_metrics_key",
]


def generate_plans() -> dict[str, Any]:
    spec = load_spec()
    source_rows = source_index(spec)
    baselines = baseline_index(spec)
    scientific_rows: list[dict[str, str]] = []
    smoke_rows: list[dict[str, str]] = []
    source_root = repo_path(spec, "sourceCampaignRoot").resolve()
    g02b_root = repo_path(spec, "g02bScenarioRoot").resolve()
    result_root = repo_path(spec, "resultRoot").resolve()

    for config_id in spec["scientificConfigs"]:
        for seed in spec["seeds"]:
            key = source_key(config_id, seed)
            if key not in source_rows:
                raise G02BError(f"Missing source plan row for {key}")
            if key not in baselines:
                raise G02BError(f"Missing G02 baseline metrics for {key}")
            source_dir = materialization_path_from_row(source_rows[key]).resolve()
            if not source_dir.exists():
                raise G02BError(f"Missing G02 source materialization: {rel(source_dir)}")
            if os.path.commonpath([str(source_root), str(source_dir)]) != str(source_root):
                raise G02BError(f"Source materialization outside source root: {rel(source_dir)}")
            manifest = manifest_for_source(source_dir, spec)
            config = runtime_config(source_dir, spec)
            baseline = baselines[key]
            baseline_summary = (
                repo_path(spec, "baselineSummariesRoot")
                / config_id
                / f"{baseline.get('RunId', '')}-{seed}.json"
            )
            for variant in spec["scientificVariants"]:
                run_id = f"G02B-{config_id}-{seed}-{variant}"
                dest = g02b_root / "scientific" / config_id / str(seed) / variant
                output = result_root / "scientific" / config_id / str(seed) / variant
                scientific_rows.append(
                    {
                        "run_id": run_id,
                        "plan_type": "scientific",
                        "config_id": config_id,
                        "seed": str(seed),
                        "variant": variant,
                        "pairing_key": key,
                        "source_materialization_id": str(manifest.get("materializationId") or source_rows[key].get("materialization_id") or ""),
                        "source_materialization": rel(source_dir),
                        "source_validation_report": source_rows[key].get("validation_report", ""),
                        "destination_directory": rel(dest),
                        "duration_seconds": row_duration_seconds(source_rows[key], manifest, config),
                        "density": row_field(source_rows[key], manifest, config, "density", "densityProfile"),
                        "workload": row_field(source_rows[key], manifest, config, "workload", "workloadProfile"),
                        "ga_parameter_scaling_mode": row_field(
                            source_rows[key], manifest, config, "ga_parameter_scaling_mode", "gaParameterScalingMode"
                        ),
                        "status": "PLANNED",
                        "output_path": rel(output),
                        "baseline_run_id": baseline.get("RunId", ""),
                        "baseline_summary": rel(baseline_summary),
                        "baseline_metrics_key": key,
                    }
                )

    smoke_key = source_key(spec["smokeConfig"], int(spec["smokeSeed"]))
    if smoke_key not in source_rows:
        raise G02BError(f"Missing smoke source plan row for {smoke_key}")
    smoke_source = materialization_path_from_row(source_rows[smoke_key]).resolve()
    if not smoke_source.exists():
        raise G02BError(f"Missing smoke source materialization: {rel(smoke_source)}")
    smoke_manifest = manifest_for_source(smoke_source, spec)
    smoke_config = runtime_config(smoke_source, spec)
    for variant in spec["smokeVariants"]:
        run_id = f"G02B-SMOKE-{variant}"
        dest = g02b_root / "smoke" / spec["smokeConfig"] / str(spec["smokeSeed"]) / variant
        output = result_root / "smoke" / variant
        smoke_rows.append(
            {
                "run_id": run_id,
                "plan_type": "smoke",
                "config_id": spec["smokeConfig"],
                "seed": str(spec["smokeSeed"]),
                "variant": variant,
                "pairing_key": smoke_key,
                "source_materialization_id": str(smoke_manifest.get("materializationId") or source_rows[smoke_key].get("materialization_id") or ""),
                "source_materialization": rel(smoke_source),
                "source_validation_report": source_rows[smoke_key].get("validation_report", ""),
                "destination_directory": rel(dest),
                "duration_seconds": row_duration_seconds(source_rows[smoke_key], smoke_manifest, smoke_config),
                "density": row_field(source_rows[smoke_key], smoke_manifest, smoke_config, "density", "densityProfile"),
                "workload": row_field(source_rows[smoke_key], smoke_manifest, smoke_config, "workload", "workloadProfile"),
                "ga_parameter_scaling_mode": row_field(
                    source_rows[smoke_key], smoke_manifest, smoke_config, "ga_parameter_scaling_mode", "gaParameterScalingMode"
                ),
                "status": "PLANNED",
                "output_path": rel(output),
                "baseline_run_id": "",
                "baseline_summary": "",
                "baseline_metrics_key": "",
            }
        )

    write_csv(repo_path(spec, "scientificPlan"), scientific_rows, PLAN_FIELDS)
    write_csv(repo_path(spec, "smokePlan"), smoke_rows, PLAN_FIELDS)
    validate_plan_rows(scientific_rows, smoke_rows, spec)
    return {
        "scientificRows": len(scientific_rows),
        "smokeRows": len(smoke_rows),
        "scientificPlan": rel(repo_path(spec, "scientificPlan")),
        "smokePlan": rel(repo_path(spec, "smokePlan")),
    }


def load_plan(plan_type: str) -> list[dict[str, str]]:
    spec = load_spec()
    key = "scientificPlan" if plan_type == "scientific" else "smokePlan"
    path = repo_path(spec, key)
    if not path.exists():
        generate_plans()
    return read_csv(path)


def all_plan_rows() -> list[dict[str, str]]:
    return load_plan("scientific") + load_plan("smoke")


def validate_plan_rows(scientific_rows: list[dict[str, str]], smoke_rows: list[dict[str, str]], spec: dict[str, Any]) -> None:
    expected_scientific = len(spec["scientificConfigs"]) * len(spec["seeds"]) * len(spec["scientificVariants"])
    expected_smoke = len(spec["smokeVariants"])
    if len(scientific_rows) != expected_scientific:
        raise G02BError(f"Scientific plan has {len(scientific_rows)} rows, expected {expected_scientific}")
    if len(smoke_rows) != expected_smoke:
        raise G02BError(f"Smoke plan has {len(smoke_rows)} rows, expected {expected_smoke}")
    for rows in (scientific_rows, smoke_rows):
        seen_runs: set[str] = set()
        seen_destinations: set[str] = set()
        for row in rows:
            if row["run_id"] in seen_runs:
                raise G02BError(f"Duplicate run id: {row['run_id']}")
            if row["destination_directory"] in seen_destinations:
                raise G02BError(f"Duplicate destination: {row['destination_directory']}")
            seen_runs.add(row["run_id"])
            seen_destinations.add(row["destination_directory"])
            if row["variant"] not in spec["allowedVariants"]:
                raise G02BError(f"Invalid variant in plan: {row['variant']}")
            if row["config_id"] in spec["excludedConfigs"]:
                raise G02BError(f"Excluded config present in plan: {row['config_id']}")
    scientific_pairs = {row["pairing_key"] for row in scientific_rows}
    expected_pairs = {source_key(config, seed) for config in spec["scientificConfigs"] for seed in spec["seeds"]}
    if scientific_pairs != expected_pairs:
        raise G02BError("Scientific plan pairing keys do not match the 15 expected G02 baselines")


def validate_plans() -> dict[str, Any]:
    spec = load_spec()
    scientific = load_plan("scientific")
    smoke = load_plan("smoke")
    validate_plan_rows(scientific, smoke, spec)
    return {
        "scientificRows": len(scientific),
        "smokeRows": len(smoke),
        "baselinePairs": len({row["pairing_key"] for row in scientific}),
    }


def select_rows(plan_type: str, run_id: str | None) -> list[dict[str, str]]:
    rows = load_plan("scientific" if plan_type == "campaign" else "smoke")
    if run_id:
        rows = [row for row in rows if row["run_id"] == run_id]
        if not rows:
            raise G02BError(f"Unknown run id: {run_id}")
    return rows


def write_variant_runtime_config(dest: Path, spec: dict[str, Any], variant: str) -> None:
    if variant not in spec["allowedVariants"]:
        raise G02BError(f"Invalid variant: {variant}")
    config_path = dest / spec["runtimeConfigRelativePath"]
    config = json_load(config_path)
    config["experimentalVariant"] = variant
    json_write(config_path, config)


def prepare_rows(plan_type: str, run_id: str | None = None) -> dict[str, Any]:
    spec = load_spec()
    state = repo_state(require_clean=False)
    require_ignored(repo_path(spec, "g02bScenarioRoot"))
    require_ignored(repo_path(spec, "stateRoot"))
    require_ignored(repo_path(spec, "resultRoot"))
    runtime_jar = repo_path(spec, "runtimeJar")
    runtime_artifact = validate_runtime_jar(runtime_jar)
    rows = select_rows(plan_type, run_id)
    prepared: list[str] = []
    for row in rows:
        source = safe_resolve_under(REPO_ROOT, row["source_materialization"])
        dest = safe_resolve_under(REPO_ROOT, row["destination_directory"])
        source_root = repo_path(spec, "sourceCampaignRoot")
        dest_root = repo_path(spec, "g02bScenarioRoot")
        if os.path.commonpath([str(source_root), str(source.resolve())]) != str(source_root):
            raise G02BError(f"Source outside final-campaign root: {rel(source)}")
        if os.path.commonpath([str(dest_root), str(dest.resolve())]) != str(dest_root):
            raise G02BError(f"Destination outside G02B root: {rel(dest)}")
        if not source.exists():
            raise G02BError(f"Missing source materialization: {rel(source)}")
        if dest.exists():
            raise G02BError(f"Refusing to overwrite prepared scenario: {rel(dest)}")

        before_source = inventory(source)
        shutil.copytree(source, dest)
        copied_source_hashes = inventory(dest)
        write_variant_runtime_config(dest, spec, row["variant"])
        copied_hashes = inventory(dest)
        manifest = {
            "schemaVersion": spec["schemaVersion"],
            "campaignId": spec["campaignId"],
            "runId": row["run_id"],
            "planType": row["plan_type"],
            "branch": state["branch"],
            "implementationBaseCommit": spec["implementationBaseCommit"],
            "head": state["head"],
            "remoteHead": state["remoteHead"],
            "baseBranch": spec["baseBranch"],
            "baseHead": state["baseHead"],
            "variant": row["variant"],
            "configId": row["config_id"],
            "seed": int(row["seed"]),
            "pairingKey": row["pairing_key"],
            "sourceMaterializationId": row["source_materialization_id"],
            "sourceMaterialization": row["source_materialization"],
            "destinationDirectory": row["destination_directory"],
            "outputPath": row["output_path"],
            "baselineRunId": row["baseline_run_id"],
            "baselineSummary": row["baseline_summary"],
            "authorizedDifferingFiles": spec["allowedDifferingFiles"],
            "runtimeJarExpected": runtime_artifact["path"],
            "runtimeJarSha256": runtime_artifact["sha256"],
            "runtimeJarSizeBytes": runtime_artifact["sizeBytes"],
            "sourceFileHashes": before_source,
            "copiedSourceFileHashes": copied_source_hashes,
            "copiedFileHashes": copied_hashes,
            "generatedArtifactHashes": {},
            "preparedAt": timestamp(),
            "validationStatus": "PREPARED_UNVALIDATED",
            "planRow": row,
        }
        json_write(dest / spec["g02bManifestRelativePath"], manifest)
        after_source = inventory(source)
        if before_source != after_source:
            raise G02BError(f"Source materialization changed during preparation: {rel(source)}")
        prepared.append(row["run_id"])
    return {"prepared": prepared, "count": len(prepared)}


def compare_to_source(source: Path, dest: Path, allow: set[str]) -> dict[str, Any]:
    source_hashes = inventory(source)
    dest_hashes = inventory(dest)
    unexpected_changes: list[str] = []
    missing: list[str] = []
    unexpected_added: list[str] = []
    for path, digest in source_hashes.items():
        if path not in dest_hashes:
            missing.append(path)
        elif path not in allow and dest_hashes[path] != digest:
            unexpected_changes.append(path)
    for path in dest_hashes:
        if path not in source_hashes and path not in allow:
            unexpected_added.append(path)
    return {
        "sourceFileCount": len(source_hashes),
        "destinationFileCount": len(dest_hashes),
        "unexpectedChanges": unexpected_changes,
        "missingFiles": missing,
        "unexpectedAddedFiles": unexpected_added,
        "sourceHashes": source_hashes,
        "destinationHashes": dest_hashes,
    }


def validate_prepared_row(row: dict[str, str]) -> dict[str, Any]:
    spec = load_spec()
    dest = safe_resolve_under(REPO_ROOT, row["destination_directory"])
    source = safe_resolve_under(REPO_ROOT, row["source_materialization"])
    dest_root = repo_path(spec, "g02bScenarioRoot")
    if os.path.commonpath([str(dest_root), str(dest.resolve())]) != str(dest_root):
        raise G02BError(f"Destination outside G02B root: {rel(dest)}")
    manifest_path = dest / spec["g02bManifestRelativePath"]
    if not manifest_path.exists():
        raise G02BError(f"Missing G02B manifest: {rel(manifest_path)}")
    manifest = json_load(manifest_path)
    required = [
        "branch",
        "head",
        "remoteHead",
        "variant",
        "configId",
        "seed",
        "sourceMaterialization",
        "sourceFileHashes",
        "copiedFileHashes",
        "authorizedDifferingFiles",
        "runtimeJarExpected",
        "preparedAt",
    ]
    missing_required = [name for name in required if name not in manifest]
    if missing_required:
        raise G02BError(f"Incomplete manifest for {row['run_id']}: {missing_required}")
    if manifest["branch"] != spec["branch"]:
        raise G02BError(f"Manifest branch mismatch for {row['run_id']}")
    manifest_head = str(manifest["head"])
    try:
        ensure_ancestor(spec["implementationBaseCommit"], manifest_head)
    except G02BError as exc:
        raise G02BError(f"Manifest HEAD is not descended from implementation base for {row['run_id']}: {manifest_head}") from exc
    if manifest["variant"] != row["variant"] or manifest["configId"] != row["config_id"] or int(manifest["seed"]) != int(row["seed"]):
        raise G02BError(f"Manifest does not match plan row for {row['run_id']}")
    if manifest["variant"] not in spec["allowedVariants"]:
        raise G02BError(f"Invalid manifest variant for {row['run_id']}: {manifest['variant']}")
    config = runtime_config(dest, spec)
    if config.get("experimentalVariant") != row["variant"]:
        raise G02BError(
            f"Runtime config variant mismatch for {row['run_id']}: "
            f"{config.get('experimentalVariant')} != {row['variant']}"
        )
    source_config = runtime_config(source, spec)
    final_manifest = manifest_for_source(source, spec)
    for key in ("durationSeconds", "densityProfile", "workloadProfile", "gaParameterScalingMode"):
        if config.get(key) != source_config.get(key):
            raise G02BError(f"{key} changed for {row['run_id']}")
    official_sources = source_index(spec)
    official_key = source_key(row["config_id"], int(row["seed"]))
    if official_key not in official_sources:
        raise G02BError(f"Missing official source plan row for {row['run_id']}")
    official_source_row = official_sources[official_key]

    if row["duration_seconds"]:
        source_duration = numeric(
            row_duration_seconds(official_source_row, final_manifest, source_config)
        )
        planned_duration = numeric(row["duration_seconds"])
        if (
            source_duration is None
            or planned_duration is None
            or abs(source_duration - planned_duration) > 1.0e-9
        ):
            raise G02BError(f"Duration mismatch for {row['run_id']}")

    authoritative_density = row_field(
        official_source_row, final_manifest, source_config, "density", "densityProfile"
    )
    if row["density"] and row["density"].strip() != authoritative_density.strip():
        raise G02BError(f"Density mismatch for {row['run_id']}")

    authoritative_workload = row_field(
        official_source_row, final_manifest, source_config, "workload", "workloadProfile"
    )
    if row["workload"] and row["workload"].strip() != authoritative_workload.strip():
        raise G02BError(f"Workload mismatch for {row['run_id']}")

    authoritative_ga_mode = row_field(
        official_source_row,
        final_manifest,
        source_config,
        "ga_parameter_scaling_mode",
        "gaParameterScalingMode",
    )
    if (
        row["ga_parameter_scaling_mode"]
        and row["ga_parameter_scaling_mode"].strip() != authoritative_ga_mode.strip()
    ):
        raise G02BError(f"GA scaling mode mismatch for {row['run_id']}")
    current_source = inventory(source)
    if current_source != manifest["sourceFileHashes"]:
        raise G02BError(f"Source hashes changed since preparation for {row['run_id']}")
    comparison = compare_to_source(source, dest, set(manifest["authorizedDifferingFiles"]))
    failures = [
        *comparison["unexpectedChanges"],
        *comparison["missingFiles"],
        *comparison["unexpectedAddedFiles"],
    ]
    status = "PASS" if not failures else "FAIL"
    report = {
        "schemaVersion": spec["schemaVersion"],
        "campaignId": spec["campaignId"],
        "runId": row["run_id"],
        "validatedAt": timestamp(),
        "validationStatus": status,
        "variant": row["variant"],
        "configId": row["config_id"],
        "seed": int(row["seed"]),
        "runtimeConfigVariant": config.get("experimentalVariant"),
        "destinationConfined": True,
        "sourceUnchanged": True,
        "allowlist": manifest["authorizedDifferingFiles"],
        "comparison": comparison,
    }
    report_path = dest / spec["preparedValidationRelativePath"]
    json_write(report_path, report)
    manifest["validationStatus"] = "PRE_RUN_VALIDATED" if status == "PASS" else "PRE_RUN_VALIDATION_FAILED"
    manifest["validatedAt"] = report["validatedAt"]
    manifest["validationReport"] = rel(report_path)
    manifest["generatedArtifactHashes"] = {spec["preparedValidationRelativePath"]: sha256_file(report_path)}
    json_write(manifest_path, manifest)
    if status != "PASS":
        raise G02BError(f"Pre-run validation failed for {row['run_id']}: {failures}")
    return report


def validate_prepared(plan_type: str, run_id: str | None = None) -> dict[str, Any]:
    rows = select_rows(plan_type, run_id)
    reports = [validate_prepared_row(row) for row in rows]
    return {"validated": [report["runId"] for report in reports], "count": len(reports)}


def load_json_if_exists(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    return json_load(path)


def numeric(value: Any) -> float | None:
    if value is None or value == "":
        return None
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip().replace(",", ".")
    try:
        return float(text)
    except ValueError:
        return None


def nested(data: dict[str, Any], *keys: str) -> Any:
    current: Any = data
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def first_present(data: dict[str, Any], paths: Iterable[tuple[str, ...]]) -> Any:
    for path in paths:
        value = nested(data, *path)
        if value is not None:
            return value
    return None


def assignment_count(native_report: dict[str, Any], assignment_type: str) -> int | None:
    field = {
        "LOCAL": "localAssignments",
        "VEHICLE": "vehicleAssignments",
        "EDGE": "edgeAssignments",
        "CLOUD": "cloudAssignments",
    }[assignment_type]
    value = native_report.get(field)
    number = numeric(value)
    return None if number is None else int(number)


def normalize_weights(weights: dict[str, Any]) -> dict[str, float]:
    aliases = {"wT": ["wT"], "wL": ["wL"], "wM": ["wM"], "wR": ["wR"]}
    normalized: dict[str, float] = {}
    for canonical, keys in aliases.items():
        for key in keys:
            if key in weights:
                value = numeric(weights[key])
                if value is not None:
                    normalized[canonical] = value
                    break
    return normalized


def read_jsonl_required(path: Path, label: str) -> list[dict[str, Any]]:
    if not path.exists():
        raise G02BError(f"Missing required {label}: {rel(path)}")
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    if not records:
        raise G02BError(f"Required {label} is empty: {rel(path)}")
    return records


def load_json_required(path: Path, label: str) -> dict[str, Any]:
    if not path.exists():
        raise G02BError(f"Missing required {label}: {rel(path)}")
    return json_load(path)


def load_run_artifacts(run_output: Path) -> dict[str, Any]:
    spec = load_spec()
    summary_path = run_output / "live_run_summary.json"
    native_path = run_output / "live-reporting" / "live_detailed_execution_report.json"
    temporal_path = run_output / "live-reporting" / "live_temporal_step_records.jsonl"
    canonical_validator_path = run_output / spec["canonicalValidatorFileName"]
    summary = load_json_required(summary_path, "canonical summary")
    native = load_json_required(native_path, "native detailed report")
    records = read_jsonl_required(temporal_path, "temporal JSONL records")
    canonical_validator = load_json_required(canonical_validator_path, "canonical validator")
    return {
        "summary": summary,
        "native": native,
        "records": records,
        "canonicalValidator": canonical_validator,
        "summaryPath": summary_path,
        "nativePath": native_path,
        "temporalPath": temporal_path,
        "canonicalValidatorPath": canonical_validator_path,
    }


def extract_reuse_modes(records: list[dict[str, Any]]) -> list[str]:
    modes: list[str] = []
    for record in records:
        reuse = record.get("populationReuse") or {}
        mode = reuse.get("appliedReuseMode") or reuse.get("baseReuseMode") or record.get("reuseMode")
        if mode:
            modes.append(str(mode))
    return modes


CANONICAL_METRIC_FIELDS = [
    "gaJobsSubmitted",
    "gaJobsCompleted",
    "gaJobsApplied",
    "gaJobsDiscardedAsStale",
    "staleRatioPercent",
    "gaRuntimeMeanSeconds",
    "gaRuntimeMedianSeconds",
    "gaRuntimeP95Seconds",
    "gaRuntimeMaxSeconds",
    "maximumAbsoluteSnapshotLagSeconds",
    "lastAppliedStrategySimulationTimeSeconds",
    "secondsWithoutAppliedStrategyAtEnd",
    "strategyApplications",
    "localAssignments",
    "vehicleAssignments",
    "edgeAssignments",
    "cloudAssignments",
]


RUNTIME_VIOLATION_FIELDS = [
    "parallelGaViolations",
    "futureSnapshotViolations",
    "futurePoolViolations",
    "invalidPoolBandwidthViolations",
    "deltaTMaxMismatchViolations",
]


def canonical_metrics(summary: dict[str, Any]) -> dict[str, Any]:
    metrics = {field: summary.get(field) for field in CANONICAL_METRIC_FIELDS if field in summary}
    violation_values = [numeric(summary.get(field)) for field in RUNTIME_VIOLATION_FIELDS if field in summary]
    if violation_values:
        metrics["runtimeViolations"] = int(sum(value for value in violation_values if value is not None))
    return metrics


def load_run_context(run_output: Path, row: dict[str, str]) -> tuple[dict[str, Any], Path]:
    spec = load_spec()
    context_path = run_output / spec["runContextFileName"]
    if context_path.exists():
        return json_load(context_path), context_path
    raise G02BError(f"Missing run context for {row['run_id']}: {rel(context_path)}")


def context_value(context: dict[str, Any], *names: str) -> Any:
    for name in names:
        if name in context:
            return context[name]
    return None


def temporal_by_window(records: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    indexed: dict[str, dict[str, Any]] = {}
    for index, record in enumerate(records):
        window = record.get("windowIndex", record.get("window", index))
        indexed[str(window)] = record
    return indexed


def temporal_by_job(records: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    indexed: dict[str, dict[str, Any]] = {}
    for record in records:
        job_id = record.get("jobId")
        if job_id:
            indexed[str(job_id)] = record
    return indexed


def ns_to_seconds(value: Any) -> float | str:
    number = numeric(value)
    if number is None:
        return ""
    return number / 1.0e9


def classify_job_status(job: dict[str, Any]) -> str:
    status = str(job.get("finalStatus", "")).upper()
    if "APPLIED" in status:
        return "APPLIED"
    if "STALE" in status or "DISCARDED" in status:
        return "STALE"
    if "FAILED" in status or job.get("errorType") or job.get("errorMessage"):
        return "FAILED"
    return status or "OTHER"


def expected_weights_for_variant(spec: dict[str, Any], variant: str) -> dict[str, float]:
    key = "noMobilityPenalty" if variant == "NO_MOBILITY_PENALTY" else "standard"
    return {name: float(value) for name, value in spec["expectedWeights"][key].items()}


def weights_match(weights: dict[str, float], expected: dict[str, float], tolerance: float) -> bool:
    return all(name in weights and abs(weights[name] - expected[name]) <= tolerance for name in expected)


def numeric_equal(left: Any, right: Any, tolerance: float = 1.0e-9) -> bool:
    left_number = numeric(left)
    right_number = numeric(right)
    return left_number is not None and right_number is not None and abs(left_number - right_number) <= tolerance


def add_optional_counter_match(checks: list[dict[str, Any]], name: str, native: dict[str, Any], summary: dict[str, Any]) -> None:
    if name in native and name in summary:
        ok = numeric_equal(native.get(name), summary.get(name))
        checks.append({"name": f"{name}_native_summary_match", "status": "PASS" if ok else "FAIL", "details": f"{native.get(name)} vs {summary.get(name)}"})


def write_causal_job_records(output: Path, row: dict[str, str], native: dict[str, Any], records: list[dict[str, Any]]) -> Path:
    spec = load_spec()
    path = output / spec["causalJobsFileName"]
    job_records = native.get("jobRecords") or []
    indexed_by_job = temporal_by_job(records)
    indexed_temporal = temporal_by_window(records)
    fields = [
        "run_id",
        "job_id",
        "window_index",
        "trigger",
        "submission_simulation_time_seconds",
        "snapshot_time_seconds",
        "snapshot_lag_seconds",
        "ga_runtime_seconds",
        "applied_simulation_time_seconds",
        "final_status",
        "task_count",
        "candidate_count",
        "base_reuse_mode",
        "applied_reuse_mode",
        "reuse_reason",
        "global_dynamicity",
        "dynamicity_level",
        "suggested_reuse_mode",
        "current_window_seconds",
        "next_window_seconds",
        "delta_t_max_at_submission_seconds",
        "timeout_detected",
        "wait_cap_simulation_time_seconds",
        "completion_wall_clock_ns",
        "wall_clock_deadline_ns",
        "delta_t_max_mismatch_seconds",
        "error_type",
        "error_message",
        "classification",
    ]
    rows: list[dict[str, Any]] = []
    for ordinal, job in enumerate(job_records):
        job_id = str(job.get("jobId", ordinal))
        window = str(job.get("windowIndex", ordinal))
        temporal = indexed_by_job.get(job_id) or indexed_temporal.get(window, {})
        reuse = temporal.get("populationReuse") or {}
        dynamicity = temporal.get("dynamicity") or {}
        window_decision = temporal.get("adaptiveWindowDecision") or {}
        operational = temporal.get("operationalMetrics") or {}
        submission_seconds = ns_to_seconds(job.get("submissionSimulationTimeNs"))
        snapshot_seconds = job.get("snapshotTimeSeconds", temporal.get("snapshotTimeSeconds", ""))
        snapshot_lag = ""
        if numeric(submission_seconds) is not None and numeric(snapshot_seconds) is not None:
            snapshot_lag = numeric(submission_seconds) - numeric(snapshot_seconds)  # type: ignore[operator]
        rows.append(
            {
                "run_id": row["run_id"],
                "job_id": job_id,
                "window_index": window,
                "trigger": temporal.get("trigger", job.get("triggerType", "")),
                "submission_simulation_time_seconds": submission_seconds,
                "snapshot_time_seconds": snapshot_seconds,
                "snapshot_lag_seconds": snapshot_lag,
                "ga_runtime_seconds": job.get("gaRuntimeWallClockSeconds", operational.get("observedGaRuntimeSeconds", "")),
                "applied_simulation_time_seconds": ns_to_seconds(job.get("appliedAtSimulationTimeNs")),
                "final_status": job.get("finalStatus", ""),
                "task_count": job.get("taskCount", ""),
                "candidate_count": job.get("candidateCount", ""),
                "base_reuse_mode": reuse.get("baseReuseMode", ""),
                "applied_reuse_mode": reuse.get("appliedReuseMode", ""),
                "reuse_reason": reuse.get("reason", ""),
                "global_dynamicity": dynamicity.get("globalDynamicity", ""),
                "dynamicity_level": dynamicity.get("dynamicityLevel", ""),
                "suggested_reuse_mode": dynamicity.get("suggestedReuseMode", ""),
                "current_window_seconds": window_decision.get("currentWindowSeconds", ""),
                "next_window_seconds": window_decision.get("nextWindowSeconds", ""),
                "delta_t_max_at_submission_seconds": job.get("deltaTMaxAtSubmissionSeconds", ""),
                "timeout_detected": job.get("timeoutDetectedBeforeCompletion", ""),
                "wait_cap_simulation_time_seconds": ns_to_seconds(job.get("waitCapDetectedSimulationTimeNs")),
                "completion_wall_clock_ns": job.get("completionWallClockNs", ""),
                "wall_clock_deadline_ns": job.get("wallClockDeadlineNs", ""),
                "delta_t_max_mismatch_seconds": job.get("deltaTMaxMismatchSeconds", ""),
                "error_type": job.get("errorType", ""),
                "error_message": job.get("errorMessage", ""),
                "classification": classify_job_status(job),
            }
        )
    write_csv(path, rows, fields)
    return path


def validate_run_row(row: dict[str, str], run_output: Path | None = None) -> dict[str, Any]:
    spec = load_spec()
    output = run_output or safe_resolve_under(REPO_ROOT, row["output_path"])
    artifacts = load_run_artifacts(output)
    summary = artifacts["summary"]
    native = artifacts["native"]
    records = artifacts["records"]
    canonical_validator = artifacts["canonicalValidator"]
    context, context_path = load_run_context(output, row)
    prepared_manifest_path = safe_resolve_under(REPO_ROOT, row["destination_directory"]) / spec["g02bManifestRelativePath"]
    prepared_manifest = load_json_required(prepared_manifest_path, "prepared G02B manifest")
    variant = row["variant"]
    observed_variant = native.get("experimentalVariant")
    checks: list[dict[str, Any]] = []

    def add(name: str, ok: bool, details: str = "", status: str | None = None) -> None:
        checks.append({"name": name, "status": status or ("PASS" if ok else "FAIL"), "details": details})

    add("variant_declared", observed_variant is not None, str(observed_variant))
    add("variant_matches", observed_variant == variant, f"{observed_variant} vs {variant}")
    add("summary_present", True, rel(artifacts["summaryPath"]))
    add("native_report_present", True, rel(artifacts["nativePath"]))
    add("temporal_records_present", bool(records), f"{len(records)} records")
    add("canonical_validator_present", True, rel(artifacts["canonicalValidatorPath"]))
    canonical_status = canonical_validator.get("status")
    canonical_errors = canonical_validator.get("errors") or []
    canonical_warnings = canonical_validator.get("warnings") or []
    canonical_simulation_completed = canonical_validator.get("simulationCompleted")
    add("canonical_validator_passed", canonical_status == "LITERATURE_SMOKE_TEST_PASSED", str(canonical_status))
    add("canonical_simulation_completed", canonical_simulation_completed is True, str(canonical_simulation_completed))
    add("run_context_present", bool(context), rel(context_path))
    add("context_run_id_matches", context_value(context, "runId", "run_id") == row["run_id"], str(context_value(context, "runId", "run_id")))
    add("context_plan_type_matches", context_value(context, "planType", "plan_type") == row["plan_type"], str(context_value(context, "planType", "plan_type")))
    add("context_config_matches", context_value(context, "configId", "config_id") == row["config_id"], str(context_value(context, "configId", "config_id")))
    context_seed = context_value(context, "seed")
    add("context_seed_matches", numeric(context_seed) == numeric(row["seed"]), str(context_seed))
    add("context_variant_matches", context_value(context, "variant") == variant, str(context_value(context, "variant")))
    add("context_jar_hash_present", bool(context_value(context, "runtimeJarSha256")), str(context_value(context, "runtimeJarSha256", "jarSha256")))
    manifest_comparisons = {
        "manifest_run_id_matches_context": (prepared_manifest.get("runId"), context_value(context, "runId", "run_id")),
        "manifest_plan_type_matches_context": (prepared_manifest.get("planType"), context_value(context, "planType", "plan_type")),
        "manifest_config_matches_context": (prepared_manifest.get("configId"), context_value(context, "configId", "config_id")),
        "manifest_seed_matches_context": (prepared_manifest.get("seed"), context_seed),
        "manifest_variant_matches_context": (prepared_manifest.get("variant"), context_value(context, "variant")),
        "manifest_source_matches_context": (prepared_manifest.get("sourceMaterialization"), context_value(context, "sourceMaterialization")),
        "manifest_prepared_matches_context": (prepared_manifest.get("destinationDirectory"), context_value(context, "preparedScenario")),
        "manifest_jar_hash_matches_context": (prepared_manifest.get("runtimeJarSha256"), context_value(context, "runtimeJarSha256")),
        "manifest_jar_size_matches_context": (prepared_manifest.get("runtimeJarSizeBytes"), context_value(context, "runtimeJarSizeBytes")),
    }
    for check_name, (left, right) in manifest_comparisons.items():
        if "seed" in check_name or "size" in check_name:
            ok = numeric(left) == numeric(right)
        else:
            ok = left == right
        add(check_name, ok, f"{left} vs {right}")

    weights = normalize_weights(native.get("effectiveFitnessWeights") or {})
    source_description = str(native.get("optimizationSourceDescription") or "")
    policy_description = str(native.get("populationReusePolicyDescription") or "")
    reuse_modes = extract_reuse_modes(records)
    metrics = canonical_metrics(summary)
    causal_csv = write_causal_job_records(output, row, native, records)
    total_fitness_comparable = True
    expected_weights = expected_weights_for_variant(spec, variant)
    add("expected_weights_match", weights_match(weights, expected_weights, float(spec["weightTolerance"])), f"{weights} expected {expected_weights}")
    for assignment_field in ("localAssignments", "vehicleAssignments", "edgeAssignments", "cloudAssignments"):
        add_optional_counter_match(checks, assignment_field, native, summary)
    for job_field in ("gaJobsSubmitted", "gaJobsCompleted", "gaJobsApplied", "gaJobsDiscardedAsStale"):
        add_optional_counter_match(checks, job_field, native, summary)

    if variant == "FULL_MA_GA":
        add("full_maga_smoke_only", row["plan_type"] == "smoke")
    elif variant == "LOCAL_ONLY":
        for assignment_type in ("VEHICLE", "EDGE", "CLOUD"):
            count = assignment_count(native, assignment_type)
            add(f"{assignment_type.lower()}_assignments_present", count is not None, str(count))
            add(f"no_{assignment_type.lower()}_assignments", count == 0 if count is not None else False, str(count))
        add("source_description_local_only", "local-only" in source_description.lower(), source_description)
        add(
            "observed_snapshot_preservation",
            True,
            "CODE_LEVEL_INVARIANT_NOT_RUNTIME_EXPOSED",
            status="INFO",
        )
    elif variant == "NO_MOBILITY_PENALTY":
        total_fitness_comparable = False
        add("weights_wT_wL_wM_wR_present", all(key in weights for key in ("wT", "wL", "wM", "wR")), str(weights))
        add("mobility_weight_zero", "wM" in weights and abs(weights["wM"]) <= 1e-12, str(weights.get("wM")))
        add("weights_sum_to_one", bool(weights) and abs(sum(weights.values()) - 1.0) <= 1e-9, str(sum(weights.values()) if weights else None))
        add("non_mobility_weights_positive", all(weights.get(key, 0.0) > 0.0 for key in ("wT", "wL", "wR")), str(weights))
        add("non_local_only_source_description", bool(source_description) and "local-only" not in source_description.lower(), source_description)
    elif variant == "COLD_START_NO_REUSE":
        add("first_reuse_first_run", bool(reuse_modes) and reuse_modes[0] == "FIRST_RUN", ",".join(reuse_modes[:3]))
        subsequent = reuse_modes[1:]
        add("subsequent_reuse_cold_start", bool(subsequent) and all(mode == "COLD_START" for mode in subsequent), ",".join(subsequent[:10]))
        add("zero_warm_or_partial", not any(mode in {"WARM_START", "PARTIAL_RESTART"} for mode in reuse_modes), ",".join(reuse_modes[:10]))
        dynamicity_present = any("dynamicity" in record or "trigger" in record for record in records)
        add("windows_dynamicity_active", dynamicity_present)

    add("job_records_present", bool(native.get("jobRecords")), f"{len(native.get('jobRecords') or [])} job records")
    status = "PASS" if all(item["status"] in {"PASS", "INFO"} for item in checks) else "FAIL"
    validation = {
        "schemaVersion": spec["schemaVersion"],
        "campaignId": spec["campaignId"],
        "runId": row["run_id"],
        "planType": row["plan_type"],
        "variant": variant,
        "configId": row["config_id"],
        "seed": int(row["seed"]),
        "validatedAt": timestamp(),
        "validationStatus": status,
        "summaryPath": rel(artifacts["summaryPath"]),
        "nativeReportPath": rel(artifacts["nativePath"]),
        "temporalRecordsPath": rel(artifacts["temporalPath"]),
        "canonicalValidatorPath": rel(artifacts["canonicalValidatorPath"]),
        "canonicalValidatorStatus": canonical_status,
        "canonicalValidatorErrors": canonical_errors,
        "canonicalValidatorWarnings": canonical_warnings,
        "canonicalSimulationCompleted": canonical_simulation_completed,
        "runContextPath": rel(context_path),
        "checks": checks,
        "observedVariant": observed_variant,
        "fitnessWeights": weights,
        "fitnessTotalComparableWithBaseline": total_fitness_comparable,
        "sourceDescription": source_description,
        "populationReusePolicyDescription": policy_description,
        "reuseModes": reuse_modes,
        "assignments": {
            "LOCAL": assignment_count(native, "LOCAL"),
            "VEHICLE": assignment_count(native, "VEHICLE"),
            "EDGE": assignment_count(native, "EDGE"),
            "CLOUD": assignment_count(native, "CLOUD"),
        },
        "canonicalMetrics": metrics,
        "causalJobsCsv": rel(causal_csv),
    }
    output.mkdir(parents=True, exist_ok=True)
    json_write(output / spec["postRunValidationFileName"], validation)
    if status != "PASS":
        failed = [item for item in checks if item["status"] == "FAIL"]
        raise G02BError(f"Post-run validation failed for {row['run_id']}: {failed}")
    return validation


def validate_run(plan_type: str, run_id: str | None, run_output: str | None) -> dict[str, Any]:
    rows = select_rows(plan_type, run_id)
    if len(rows) != 1 and run_output:
        raise G02BError("--run-output can be used only with a single --run-id")
    reports = []
    for row in rows:
        output = safe_resolve_under(REPO_ROOT, run_output) if run_output else None
        reports.append(validate_run_row(row, output))
    return {"validated": [report["runId"] for report in reports], "count": len(reports)}


def percentile(values: list[float], p: float) -> float | None:
    if not values:
        return None
    if len(values) == 1:
        return values[0]
    index = (len(values) - 1) * p
    low = math.floor(index)
    high = math.ceil(index)
    if low == high:
        return values[low]
    return values[low] + (values[high] - values[low]) * (index - low)


def baseline_value(baseline: dict[str, str], field: str) -> Any:
    mapping = {
        "localAssignments": "LocalAssignments",
        "vehicleAssignments": "VehicleAssignments",
        "edgeAssignments": "EdgeAssignments",
        "cloudAssignments": "CloudAssignments",
        "gaRuntimeMeanSeconds": "GaRuntimeMeanSeconds",
        "gaRuntimeMedianSeconds": "GaRuntimeMedianSeconds",
        "gaRuntimeP95Seconds": "GaRuntimeP95Seconds",
        "gaRuntimeMaxSeconds": "GaRuntimeMaxSeconds",
        "gaJobsDiscardedAsStale": "StaleResults",
        "staleRatioPercent": "StaleRatioPercent",
        "gaJobsSubmitted": "GaJobsSubmitted",
        "gaJobsCompleted": "GaJobsCompleted",
        "gaJobsApplied": "GaJobsApplied",
        "maximumAbsoluteSnapshotLagSeconds": "SnapshotLagMaxSeconds",
        "runtimeViolations": "RuntimeViolations",
    }
    return baseline.get(mapping.get(field, field), "")


NOT_AVAILABLE = "NOT_AVAILABLE_IN_G02_ARCHIVE"


def plan_index_by_run_id() -> dict[str, dict[str, str]]:
    return {row["run_id"]: row for row in all_plan_rows()}


def load_baseline_summary(plan_row: dict[str, str], baseline: dict[str, str]) -> dict[str, Any]:
    summary_path_text = plan_row.get("baseline_summary", "")
    if not summary_path_text:
        return {}
    summary_path = safe_resolve_under(REPO_ROOT, summary_path_text)
    if not summary_path.exists():
        return {}
    summary = json_load(summary_path)
    if baseline:
        if summary.get("runId") != baseline.get("RunId"):
            raise G02BError(f"Baseline summary RunId mismatch: {rel(summary_path)}")
        if summary.get("configId") != baseline.get("ConfigId"):
            raise G02BError(f"Baseline summary ConfigId mismatch: {rel(summary_path)}")
        if numeric(summary.get("seed")) != numeric(baseline.get("Seed")):
            raise G02BError(f"Baseline summary Seed mismatch: {rel(summary_path)}")
    return summary


def baseline_summary_metric(summary: dict[str, Any], field: str) -> Any:
    metrics = summary.get("metrics") or {}
    assignments = metrics.get("assignments") or {}
    mapping = {
        "localAssignments": assignments.get("local"),
        "vehicleAssignments": assignments.get("vehicle"),
        "edgeAssignments": assignments.get("edge"),
        "cloudAssignments": assignments.get("cloud"),
        "gaRuntimeMeanSeconds": metrics.get("gaRuntimeMeanSeconds"),
        "gaRuntimeMedianSeconds": metrics.get("gaRuntimeMedianSeconds"),
        "gaRuntimeP95Seconds": metrics.get("gaRuntimeP95Seconds"),
        "gaRuntimeMaxSeconds": metrics.get("gaRuntimeMaxSeconds"),
        "gaJobsDiscardedAsStale": metrics.get("staleResults"),
        "staleRatioPercent": metrics.get("staleRatioPercent"),
        "gaJobsSubmitted": metrics.get("gaJobsSubmitted"),
        "gaJobsCompleted": metrics.get("gaJobsCompleted"),
        "gaJobsApplied": metrics.get("gaJobsApplied"),
        "maximumAbsoluteSnapshotLagSeconds": metrics.get("snapshotLagMaxSeconds"),
        "runtimeViolations": metrics.get("runtimeViolations"),
    }
    return mapping.get(field)


def combined_baseline_value(baseline: dict[str, str], summary: dict[str, Any], field: str) -> Any:
    csv_value = baseline_value(baseline, field)
    if csv_value not in ("", None):
        return csv_value
    summary_value = baseline_summary_metric(summary, field)
    if summary_value not in ("", None):
        return summary_value
    return NOT_AVAILABLE


def sensible_delta(field: str, variant: str) -> bool:
    if variant == "NO_MOBILITY_PENALTY" and field.lower().startswith("fitness"):
        return False
    return field in {
        "localAssignments",
        "vehicleAssignments",
        "edgeAssignments",
        "cloudAssignments",
        "gaRuntimeMeanSeconds",
        "gaRuntimeMedianSeconds",
        "gaRuntimeP95Seconds",
        "gaRuntimeMaxSeconds",
        "gaJobsDiscardedAsStale",
        "staleRatioPercent",
        "gaJobsSubmitted",
        "gaJobsCompleted",
        "gaJobsApplied",
        "strategyApplications",
        "maximumAbsoluteSnapshotLagSeconds",
        "runtimeViolations",
        "lastAppliedStrategySimulationTimeSeconds",
        "secondsWithoutAppliedStrategyAtEnd",
    }


def delta_value(g02b_value: Any, baseline_value_raw: Any, field: str, variant: str) -> Any:
    if not sensible_delta(field, variant):
        return ""
    left = numeric(g02b_value)
    right = numeric(baseline_value_raw)
    if left is None or right is None:
        return ""
    return left - right


def aggregate(input_dir: str | None = None, output_dir: str | None = None, require_complete: bool = False) -> dict[str, Any]:
    spec = load_spec()
    base_rows = baseline_index(spec)
    plans_by_run = plan_index_by_run_id()
    scan_root = safe_resolve_under(REPO_ROOT, input_dir) if input_dir else repo_path(spec, "resultRoot")
    out_root = safe_resolve_under(REPO_ROOT, output_dir) if output_dir else repo_path(spec, "resultRoot") / "aggregate"
    validations = list(scan_root.rglob(spec["postRunValidationFileName"])) if scan_root.exists() else []
    per_run: list[dict[str, Any]] = []
    for validation_path in validations:
        validation = json_load(validation_path)
        if validation.get("planType") != "scientific":
            continue
        key = source_key(validation["configId"], validation["seed"])
        baseline = base_rows.get(key, {})
        plan_row = plans_by_run.get(validation["runId"], {})
        baseline_summary = load_baseline_summary(plan_row, baseline) if baseline else {}
        weights = validation.get("fitnessWeights") or {}
        metrics = validation.get("canonicalMetrics") or {}
        assignments = validation.get("assignments") or {}
        fields = [
            "localAssignments",
            "vehicleAssignments",
            "edgeAssignments",
            "cloudAssignments",
            "gaRuntimeMeanSeconds",
            "gaRuntimeMedianSeconds",
            "gaRuntimeP95Seconds",
            "gaRuntimeMaxSeconds",
            "gaJobsDiscardedAsStale",
            "staleRatioPercent",
            "gaJobsSubmitted",
            "gaJobsCompleted",
            "gaJobsApplied",
            "strategyApplications",
            "maximumAbsoluteSnapshotLagSeconds",
            "runtimeViolations",
            "lastAppliedStrategySimulationTimeSeconds",
            "secondsWithoutAppliedStrategyAtEnd",
        ]
        row: dict[str, Any] = {
            "run_id": validation["runId"],
            "plan_type": validation.get("planType", ""),
            "config_id": validation["configId"],
            "seed": validation["seed"],
            "variant": validation["variant"],
            "baseline_run_id": baseline.get("RunId", ""),
            "baseline_key": key,
            "baseline_summary": plan_row.get("baseline_summary", ""),
            "validation_status": validation["validationStatus"],
            "fitness_total_comparable": str(validation.get("fitnessTotalComparableWithBaseline", True)).lower(),
            "wT": weights.get("wT", ""),
            "wL": weights.get("wL", ""),
            "wM": weights.get("wM", ""),
            "wR": weights.get("wR", ""),
            "validation_report": rel(validation_path),
        }
        for field in fields:
            g02b_value = assignments.get(field.replace("Assignments", "").upper()) if field.endswith("Assignments") else metrics.get(field, "")
            base_value = combined_baseline_value(baseline, baseline_summary, field) if baseline else NOT_AVAILABLE
            row[f"g02b_{field}"] = g02b_value
            row[f"baseline_{field}"] = base_value
            row[f"delta_{field}"] = delta_value(g02b_value, base_value, field, validation["variant"])
        per_run.append(row)
    per_run.sort(key=lambda row: (row["variant"], row["config_id"], int(row["seed"])))
    metric_fields = [
        "localAssignments",
        "vehicleAssignments",
        "edgeAssignments",
        "cloudAssignments",
        "gaRuntimeMeanSeconds",
        "gaRuntimeMedianSeconds",
        "gaRuntimeP95Seconds",
        "gaRuntimeMaxSeconds",
        "gaJobsDiscardedAsStale",
        "staleRatioPercent",
        "gaJobsSubmitted",
        "gaJobsCompleted",
        "gaJobsApplied",
        "strategyApplications",
        "maximumAbsoluteSnapshotLagSeconds",
        "runtimeViolations",
        "lastAppliedStrategySimulationTimeSeconds",
        "secondsWithoutAppliedStrategyAtEnd",
    ]
    per_fields = [
        "run_id",
        "plan_type",
        "config_id",
        "seed",
        "variant",
        "baseline_run_id",
        "baseline_key",
        "baseline_summary",
        "validation_status",
        "fitness_total_comparable",
        "wT",
        "wL",
        "wM",
        "wR",
        *[f"{prefix}_{field}" for field in metric_fields for prefix in ("g02b", "baseline", "delta")],
        "validation_report",
    ]
    if require_complete:
        failures = []
        if len(per_run) != 45:
            failures.append(f"expected 45 scientific validations, found {len(per_run)}")
        if sum(1 for row in per_run if row["validation_status"] == "PASS") != 45:
            failures.append("expected 45 PASS scientific validations")
        if len({row["baseline_key"] for row in per_run}) != 15:
            failures.append("expected 15 config+seed pairs")
        if any(not row["baseline_run_id"] for row in per_run):
            failures.append("missing G02 baseline for at least one scientific row")
        for variant in spec["scientificVariants"]:
            count = sum(1 for row in per_run if row["variant"] == variant)
            if count != 15:
                failures.append(f"expected 15 runs for {variant}, found {count}")
        if len({row["run_id"] for row in per_run}) != len(per_run):
            failures.append("duplicate run_id in scientific aggregation")
        if len({(row["config_id"], row["seed"], row["variant"]) for row in per_run}) != len(per_run):
            failures.append("duplicate config/seed/variant tuple in scientific aggregation")
        if failures:
            raise G02BError("; ".join(failures))
    out_root.mkdir(parents=True, exist_ok=True)
    write_csv(out_root / "g02b_paired_per_run.csv", per_run, per_fields)
    aggregate_rows: list[dict[str, Any]] = []
    for variant in sorted({row["variant"] for row in per_run}):
        for config_id in sorted({row["config_id"] for row in per_run if row["variant"] == variant}):
            subset = [row for row in per_run if row["variant"] == variant and row["config_id"] == config_id]
            for metric in metric_fields:
                g02b_values = [numeric(row[f"g02b_{metric}"]) for row in subset]
                baseline_values = [numeric(row[f"baseline_{metric}"]) for row in subset]
                delta_values = [numeric(row[f"delta_{metric}"]) for row in subset]
                g02b_values = [value for value in g02b_values if value is not None]
                baseline_values = [value for value in baseline_values if value is not None]
                delta_values = [value for value in delta_values if value is not None]
                aggregate_rows.append(
                    {
                        "variant": variant,
                        "config_id": config_id,
                        "metric": metric,
                        "runs": len(subset),
                        "validated": sum(1 for row in subset if row["validation_status"] == "PASS"),
                        "available_values": len(delta_values),
                        "g02b_mean": statistics.mean(g02b_values) if g02b_values else "",
                        "baseline_mean": statistics.mean(baseline_values) if baseline_values else "",
                        "delta_mean": statistics.mean(delta_values) if delta_values else "",
                        "delta_median": statistics.median(delta_values) if delta_values else "",
                        "delta_p95": percentile(sorted(delta_values), 0.95) if delta_values else "",
                        "delta_max": max(delta_values) if delta_values else "",
                        "fitness_total_comparable": "false" if variant == "NO_MOBILITY_PENALTY" else "true",
                    }
                )
    write_csv(
        out_root / "g02b_paired_aggregate.csv",
        aggregate_rows,
        [
            "variant",
            "config_id",
            "metric",
            "runs",
            "validated",
            "available_values",
            "g02b_mean",
            "baseline_mean",
            "delta_mean",
            "delta_median",
            "delta_p95",
            "delta_max",
            "fitness_total_comparable",
        ],
    )
    summary = {
        "generatedAt": timestamp(),
        "planType": "scientific",
        "perRunCount": len(per_run),
        "aggregateRows": aggregate_rows,
        "requireComplete": require_complete,
        "missingDataPolicy": "Missing values are emitted as empty fields and not invented.",
        "noMobilityPenalty": spec["noMobilityPenalty"],
    }
    json_write(out_root / "g02b_paired_summary.json", summary)
    lines = [
        "# G02B paired aggregation",
        "",
        f"Generated: {summary['generatedAt']}",
        "",
        "| Variant | Config | Metric | Runs | Validated | Delta mean | Fitness total comparable |",
        "| --- | --- | --- | ---: | ---: | ---: | --- |",
    ]
    for row in aggregate_rows:
        lines.append(
            f"| {row['variant']} | {row['config_id']} | {row['metric']} | {row['runs']} | "
            f"{row['validated']} | {row['delta_mean']} | {row['fitness_total_comparable']} |"
        )
    lines.extend(
        [
            "",
            "NO_MOBILITY_PENALTY changes the objective function; total fitness is therefore marked as not directly comparable.",
        ]
    )
    (out_root / "g02b_paired_summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    return {"perRunRows": len(per_run), "aggregateRows": len(aggregate_rows), "output": rel(out_root)}


def bundle(
    output_dir: str | None = None,
    result_root_override: str | None = None,
    scenario_root_override: str | None = None,
    state_root_override: str | None = None,
) -> dict[str, Any]:
    spec = load_spec()
    result_root = safe_resolve_under(REPO_ROOT, result_root_override) if result_root_override else repo_path(spec, "resultRoot")
    scenario_root = safe_resolve_under(REPO_ROOT, scenario_root_override) if scenario_root_override else repo_path(spec, "g02bScenarioRoot")
    state_root = safe_resolve_under(REPO_ROOT, state_root_override) if state_root_override else repo_path(spec, "stateRoot")
    out_dir = safe_resolve_under(REPO_ROOT, output_dir) if output_dir else repo_path(spec, "stateRoot")
    require_ignored(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    archive = out_dir / f"g02b_bundle_{dt.datetime.now(UTC).strftime('%Y%m%d_%H%M%S')}.zip"
    manifest_entries: list[dict[str, Any]] = []

    def add_file(zf: zipfile.ZipFile, path: Path, arcname: str | None = None) -> None:
        if "self-test" in {part.lower() for part in path.parts}:
            return
        name = arcname or rel(path)
        zf.write(path, name)
        manifest_entries.append({"path": name, "sizeBytes": path.stat().st_size, "sha256": sha256_file(path)})

    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in (SPEC_PATH, SCRIPT_DIR / "README.md"):
            if path.exists():
                add_file(zf, path)
        for key in ("scientificPlan", "smokePlan"):
            path = repo_path(spec, key)
            if path.exists():
                add_file(zf, path)
        registry = state_root / "g02b_run_registry.json"
        if registry.exists():
            add_file(zf, registry)
        if scenario_root.exists():
            wanted_names = {Path(spec["g02bManifestRelativePath"]).name, Path(spec["preparedValidationRelativePath"]).name}
            for path in sorted(p for p in scenario_root.rglob("*") if p.is_file() and p.name in wanted_names):
                add_file(zf, path)
        if result_root.exists():
            allowed_names = {
                spec["runContextFileName"],
                spec["postRunValidationFileName"],
                spec["causalJobsFileName"],
                "g02b_paired_per_run.csv",
                "g02b_paired_aggregate.csv",
                "g02b_paired_summary.json",
                "g02b_paired_summary.md",
            }
            for path in sorted(p for p in result_root.rglob("*") if p.is_file() and p.name in allowed_names):
                add_file(zf, path)
        git_meta = {
            "generatedAt": timestamp(),
            "branch": run_git(["branch", "--show-current"]),
            "head": run_git(["rev-parse", "HEAD"]),
            "origin": run_git(["remote", "get-url", "origin"]),
            "implementationBaseCommit": spec["implementationBaseCommit"],
            "baseBranch": spec["baseBranch"],
            "baseHead": run_git(["rev-parse", spec["baseBranch"]]),
            "remoteBaseHead": run_git(["rev-parse", f"origin/{spec['baseBranch']}"]),
        }
        git_meta_bytes = (json.dumps(git_meta, indent=2, sort_keys=True) + "\n").encode("utf-8")
        zf.writestr("G02B_GIT_METADATA.json", git_meta_bytes)
        manifest_entries.append(
            {
                "path": "G02B_GIT_METADATA.json",
                "sizeBytes": len(git_meta_bytes),
                "sha256": hashlib.sha256(git_meta_bytes).hexdigest(),
            }
        )
        bundle_manifest_bytes = (json.dumps({"entries": manifest_entries}, indent=2, sort_keys=True) + "\n").encode("utf-8")
        zf.writestr("G02B_BUNDLE_MANIFEST.json", bundle_manifest_bytes)
    return {"bundle": rel(archive), "sha256": sha256_file(archive)}


def check() -> dict[str, Any]:
    state = repo_state(require_clean=False)
    plan = validate_plans()
    spec = load_spec()
    for key in ("g02bScenarioRoot", "stateRoot", "resultRoot"):
        require_ignored(repo_path(spec, key))
    return {"repo": state, "plans": plan}


def resume_candidates(plan_type: str, registry_path: str | None = None) -> dict[str, Any]:
    rows = load_plan("scientific" if plan_type == "campaign" else "smoke")
    registry: dict[str, Any] = {}
    if registry_path:
        path = safe_resolve_under(REPO_ROOT, registry_path)
    else:
        path = repo_path(load_spec(), "stateRoot") / "g02b_run_registry.json"
    if path.exists():
        registry = json_load(path)
    completed = {
        run_id
        for run_id, entry in (registry.get("runs") or {}).items()
        if entry.get("validationStatus") == "PASS" or entry.get("status") == "VALIDATED"
    }
    pending = [row["run_id"] for row in rows if row["run_id"] not in completed]
    return {"completed": sorted(completed), "pending": pending}


def single_new_directory(before: Iterable[str], after: Iterable[str]) -> str:
    new_directories = sorted(set(after) - set(before))
    if len(new_directories) != 1:
        raise G02BError(f"Expected exactly one new MOSAIC directory, found {len(new_directories)}: {new_directories}")
    return new_directories[0]


def resume_action(entry_status: str | None, output_exists: bool, context_status: str | None) -> str:
    if entry_status == "VALIDATED":
        return "SKIP"
    if entry_status == "RUN_FAILED":
        return "STOP_FOR_MANUAL_INTERVENTION"
    if output_exists and context_status in {"COMPLETED", "VALIDATION_FAILED"}:
        return "VALIDATE_ONLY"
    return "RUN"


def classify_runner_outcome(runner_exit_code: int, new_directory_count: int, artifacts_complete: bool, simulation_completed: bool) -> str:
    if new_directory_count != 1 or not artifacts_complete or not simulation_completed:
        return "RUN_FAILED"
    return "COMPLETED"


def self_test() -> dict[str, Any]:
    spec = load_spec()
    results: list[dict[str, Any]] = []

    def test(name: str, func: Any) -> None:
        try:
            func()
            results.append({"name": name, "status": "PASS"})
        except Exception as exc:  # noqa: BLE001 - self-test should capture all failures.
            results.append({"name": name, "status": "FAIL", "error": str(exc)})

    def assert_true(condition: bool, message: str) -> None:
        if not condition:
            raise AssertionError(message)

    generated = generate_plans()
    scientific = load_plan("scientific")
    smoke = load_plan("smoke")
    test("scientific_plan_45_rows", lambda: assert_true(generated["scientificRows"] == 45, str(generated)))
    test("smoke_plan_4_rows", lambda: assert_true(generated["smokeRows"] == 4, str(generated)))
    test("no_duplicates", lambda: validate_plan_rows(scientific, smoke, spec))
    test("pairing_complete_with_15_baselines", lambda: assert_true(len({row["pairing_key"] for row in scientific}) == 15, "bad pairs"))
    test("implementation_base_descendant_accepted", lambda: ensure_ancestor(spec["implementationBaseCommit"], "HEAD"))
    test("non_descendant_commit_rejected", lambda: _expect_error(lambda: ensure_ancestor("0000000000000000000000000000000000000000", "HEAD")))

    def path_traversal() -> None:
        try:
            safe_resolve_under(Path(tempfile.gettempdir()), "../escape")
        except G02BError:
            return
        raise AssertionError("path traversal accepted")

    test("path_traversal_rejected", path_traversal)

    with tempfile.TemporaryDirectory(prefix="g02b_tooling_", dir=REPO_ROOT / "tmp") as tmp:
        tmp_root = Path(tmp)
        source = tmp_root / "source"
        dest = tmp_root / "dest"
        (source / "application").mkdir(parents=True)
        (source / "reports").mkdir()
        json_write(
            source / spec["runtimeConfigRelativePath"],
            {
                "durationSeconds": 300,
                "densityProfile": "DENSITY_NOMINAL",
                "workloadProfile": "WL-I",
                "gaParameterScalingMode": "STATIC",
            },
        )
        json_write(
            source / spec["sourceManifestRelativePath"],
            {
                "materializationId": "fixture",
                "density": "DENSITY_NOMINAL",
                "workload": "WL-I",
            },
        )
        (source / "payload.txt").write_text("unchanged\n", encoding="utf-8", newline="\n")
        before = inventory(source)
        shutil.copytree(source, dest)
        write_variant_runtime_config(dest, spec, "LOCAL_ONLY")
        manifest = {
            "runId": "fixture-local",
            "planType": "scientific",
            "branch": spec["branch"],
            "implementationBaseCommit": spec["implementationBaseCommit"],
            "head": spec["implementationBaseCommit"],
            "remoteHead": spec["implementationBaseCommit"],
            "variant": "LOCAL_ONLY",
            "configId": "CFG-N-I",
            "seed": 104729,
            "sourceMaterialization": rel(source),
            "sourceFileHashes": before,
            "copiedFileHashes": inventory(dest),
            "authorizedDifferingFiles": spec["allowedDifferingFiles"],
            "runtimeJarExpected": spec["paths"]["runtimeJar"],
            "runtimeJarSha256": "fixture-jar-hash",
            "runtimeJarSizeBytes": 123,
            "destinationDirectory": rel(dest),
            "preparedAt": timestamp(),
        }
        json_write(dest / spec["g02bManifestRelativePath"], manifest)
        fixture_row = {
            "run_id": "fixture-local",
            "plan_type": "scientific",
            "config_id": "CFG-N-I",
            "seed": "104729",
            "variant": "LOCAL_ONLY",
            "pairing_key": "CFG-N-I|104729",
            "source_materialization": rel(source),
            "destination_directory": rel(dest),
            "duration_seconds": "300",
            "density": "DENSITY_NOMINAL",
            "workload": "WL-I",
            "ga_parameter_scaling_mode": "STATIC",
            "output_path": rel(tmp_root / "out"),
            "baseline_run_id": "fixture-base",
            "baseline_summary": "",
            "source_materialization_id": "fixture",
            "source_validation_report": "",
            "baseline_metrics_key": "CFG-N-I|104729",
            "status": "PLANNED",
        }
        test("source_never_modified", lambda: assert_true(inventory(source) == before, "source changed"))
        test("copy_identical_except_allowlist", lambda: assert_true(not compare_to_source(source, dest, set(spec["allowedDifferingFiles"]))["unexpectedChanges"], "unexpected diff"))
        test(
            "duration_numeric_equivalence",
            lambda: assert_true(
                numeric_equal("300", "300.0"),
                "Equivalent numeric durations were rejected",
            ),
        )
        test("invalid_variant_rejected", lambda: _expect_error(lambda: write_variant_runtime_config(dest, spec, "BAD_VARIANT")))
        test("manifest_config_mismatch_rejected", lambda: _expect_error(lambda: _validate_with_variant(fixture_row, "NO_MOBILITY_PENALTY")))
        test("local_only_validator", lambda: _validate_run_fixture(tmp_root, fixture_row, "LOCAL_ONLY"))
        test("no_mobility_penalty_validator", lambda: _validate_run_fixture(tmp_root, {**fixture_row, "run_id": "fixture-no-m", "variant": "NO_MOBILITY_PENALTY"}, "NO_MOBILITY_PENALTY"))
        test("cold_start_validator", lambda: _validate_run_fixture(tmp_root, {**fixture_row, "run_id": "fixture-cold", "variant": "COLD_START_NO_REUSE"}, "COLD_START_NO_REUSE"))
        test("weights_wT_wL_wM_wR", lambda: assert_true(set(_validate_run_fixture(tmp_root, {**fixture_row, "run_id": "fixture-weights", "variant": "NO_MOBILITY_PENALTY"}, "NO_MOBILITY_PENALTY")["fitnessWeights"]) == {"wT", "wL", "wM", "wR"}, "bad weights"))
        test("standard_weights_exact", lambda: assert_true(_validate_run_fixture(tmp_root, {**fixture_row, "run_id": "fixture-standard-weights", "variant": "FULL_MA_GA", "plan_type": "smoke"}, "FULL_MA_GA")["fitnessWeights"] == spec["expectedWeights"]["standard"], "bad standard weights"))
        test("no_mobility_weights_exact", lambda: assert_true(_validate_run_fixture(tmp_root, {**fixture_row, "run_id": "fixture-no-mobility-weights", "variant": "NO_MOBILITY_PENALTY"}, "NO_MOBILITY_PENALTY")["fitnessWeights"] == spec["expectedWeights"]["noMobilityPenalty"], "bad no mobility weights"))
        test("missing_local_only_counter_fails", lambda: _expect_error(lambda: _validate_run_fixture(tmp_root, {**fixture_row, "run_id": "fixture-missing-counter"}, "LOCAL_ONLY", omit_counter="vehicleAssignments")))
        test("assignment_native_summary_mismatch_fails", lambda: _expect_error(lambda: _validate_run_fixture(tmp_root, {**fixture_row, "run_id": "fixture-assignment-mismatch", "variant": "FULL_MA_GA", "plan_type": "smoke"}, "FULL_MA_GA", native_overrides={"localAssignments": 6})))
        test("canonical_validator_pass", lambda: assert_true(_validate_run_fixture(tmp_root, {**fixture_row, "run_id": "fixture-canonical-pass", "variant": "FULL_MA_GA", "plan_type": "smoke"}, "FULL_MA_GA")["canonicalValidatorStatus"] == "LITERATURE_SMOKE_TEST_PASSED", "canonical pass missing"))
        test("canonical_validator_fail_validation_failed", lambda: _expect_error(lambda: _validate_run_fixture(tmp_root, {**fixture_row, "run_id": "fixture-canonical-fail", "variant": "FULL_MA_GA", "plan_type": "smoke"}, "FULL_MA_GA", canonical_status="LITERATURE_SMOKE_TEST_FAILED")))
        registry = tmp_root / "registry.json"
        json_write(registry, {"runs": {"G02B-SMOKE-FULL_MA_GA": {"validationStatus": "PASS"}}})
        test("resume_skips_only_validated", lambda: assert_true("G02B-SMOKE-FULL_MA_GA" not in resume_candidates("smoke", rel(registry))["pending"], "validated not skipped"))
        test("validation_failed_recoverable_without_rerun", lambda: assert_true(resume_action("VALIDATION_FAILED", True, "VALIDATION_FAILED") == "VALIDATE_ONLY", "bad resume action"))
        test("single_new_mosaic_directory", lambda: assert_true(single_new_directory(["a"], ["a", "b"]) == "b", "bad directory diff"))
        test("nonzero_runner_complete_not_run_failed", lambda: assert_true(classify_runner_outcome(1, 1, True, True) == "COMPLETED", "bad complete runner outcome"))
        test("nonzero_runner_incomplete_run_failed", lambda: assert_true(classify_runner_outcome(1, 1, False, True) == "RUN_FAILED", "bad incomplete runner outcome"))
        agg_input = tmp_root / "aggregate-input"
        row = {**fixture_row, "run_id": "fixture-no-m", "variant": "NO_MOBILITY_PENALTY", "output_path": rel(agg_input / "one")}
        validation = _validate_run_fixture(tmp_root, row, "NO_MOBILITY_PENALTY", output=agg_input / "one")
        causal_row = _read_single_csv_row(agg_input / "one" / spec["causalJobsFileName"])
        test("causal_csv_real_java_schema", lambda: assert_true(causal_row["job_id"] == "job-1" and causal_row["completion_wall_clock_ns"] == "16000", "bad causal schema"))
        test("causal_ns_to_seconds", lambda: assert_true(causal_row["submission_simulation_time_seconds"] == "1.0" and causal_row["applied_simulation_time_seconds"] == "2.0", "bad ns conversion"))
        test("causal_join_by_job_id", lambda: assert_true(causal_row["trigger"] == "FIRST" and causal_row["base_reuse_mode"] == "FIRST_RUN", "bad job join"))
        test("causal_nested_dynamicity", lambda: assert_true(causal_row["global_dynamicity"] == "0.0" and causal_row["dynamicity_level"] == "LOW", "bad dynamicity"))
        _validate_run_fixture(tmp_root, {**fixture_row, "run_id": "fixture-smoke", "plan_type": "smoke", "variant": "LOCAL_ONLY", "output_path": rel(agg_input / "smoke")}, "LOCAL_ONLY", output=agg_input / "smoke")
        test("aggregate_paired_fixture", lambda: assert_true(aggregate(rel(agg_input), rel(tmp_root / "agg"))["perRunRows"] == 1, "aggregate failed"))
        test("smoke_excluded_from_scientific_aggregation", lambda: assert_true(aggregate(rel(agg_input), rel(tmp_root / "agg2"))["perRunRows"] == 1, "smoke included"))
        complete_input = tmp_root / "complete-aggregate"
        _write_complete_aggregate_fixture(tmp_root, complete_input, scientific)
        test("complete_aggregation_45_45", lambda: assert_true(aggregate(rel(complete_input), rel(tmp_root / "complete-agg"), require_complete=True)["perRunRows"] == 45, "complete aggregate failed"))
        test("aggregate_all_metric_families", lambda: assert_true(_aggregate_has_metric(tmp_root / "complete-agg" / "g02b_paired_aggregate.csv", "secondsWithoutAppliedStrategyAtEnd"), "aggregate metric missing"))
        test("baseline_absent_marked_not_available", lambda: assert_true(combined_baseline_value({}, {}, "strategyApplications") == NOT_AVAILABLE, "missing baseline not marked"))
        test("unique_config_seed_variant", lambda: assert_true(len({(row["config_id"], row["seed"], row["variant"]) for row in scientific}) == 45, "bad unique tuple"))
        test("native_and_summary_combined", lambda: assert_true("nativeReportPath" in validation and "summaryPath" in validation, "missing report paths"))
        test("causal_job_fields", lambda: assert_true((agg_input / "one" / spec["causalJobsFileName"]).exists(), "missing causal csv"))
        test("bundle_with_manifest_and_context", lambda: assert_true(_bundle_contains_context(tmp_root / "bundle", spec["runContextFileName"]), "bundle missing context"))
        test("bundle_does_not_write_real_result_fixture", lambda: assert_true(not (repo_path(spec, "resultRoot") / "self-test-bundle-current").exists(), "bundle polluted result root"))
        test("manifest_without_self_hash", lambda: assert_true(spec["g02bManifestRelativePath"] not in manifest.get("copiedFileHashes", {}), "manifest self hash present"))
        test(
            "no_mobility_fitness_marked_not_comparable",
            lambda: assert_true(validation["fitnessTotalComparableWithBaseline"] is False, "fitness comparability wrong"),
        )

    failed = [item for item in results if item["status"] != "PASS"]
    report = {"generatedAt": timestamp(), "results": results}
    report_path = repo_path(spec, "stateRoot") / "g02b_tooling_self_test.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    json_write(report_path, report)
    if failed:
        raise G02BError(f"Self-test failures: {failed}")
    return {"tests": len(results), "failures": 0, "report": rel(report_path)}


def _expect_error(func: Any) -> None:
    try:
        func()
    except Exception:
        return
    raise AssertionError("expected error was not raised")


def _bundle_contains_context(output_dir: Path, context_file_name: str) -> bool:
    fixture_root = output_dir / "result-fixture"
    context_path = fixture_root / "run" / context_file_name
    json_write(context_path, {"runId": "bundle-fixture", "status": "VALIDATED"})
    result = bundle(rel(output_dir), result_root_override=rel(fixture_root), scenario_root_override=rel(output_dir / "scenario-fixture"), state_root_override=rel(output_dir / "state-fixture"))
    archive = safe_resolve_under(REPO_ROOT, result["bundle"])
    with zipfile.ZipFile(archive, "r") as zf:
        names = zf.namelist()
        manifest = json.loads(zf.read("G02B_BUNDLE_MANIFEST.json").decode("utf-8"))
    return any(name.endswith(context_file_name) for name in names) and all(entry["path"] != "G02B_BUNDLE_MANIFEST.json" for entry in manifest["entries"])


def _validate_with_variant(row: dict[str, str], variant: str) -> None:
    spec = load_spec()
    dest = safe_resolve_under(REPO_ROOT, row["destination_directory"])
    config = runtime_config(dest, spec)
    config["experimentalVariant"] = variant
    json_write(dest / spec["runtimeConfigRelativePath"], config)
    validate_prepared_row(row)


def _validate_run_fixture(
    tmp_root: Path,
    row: dict[str, str],
    variant: str,
    output: Path | None = None,
    omit_counter: str | None = None,
    native_overrides: dict[str, Any] | None = None,
    canonical_status: str = "LITERATURE_SMOKE_TEST_PASSED",
) -> dict[str, Any]:
    spec = load_spec()
    output_dir = output or (tmp_root / "run-fixtures" / row["run_id"])
    output_dir.mkdir(parents=True, exist_ok=True)
    live_reporting = output_dir / "live-reporting"
    live_reporting.mkdir(parents=True, exist_ok=True)
    prepared = safe_resolve_under(REPO_ROOT, row["destination_directory"])
    prepared.mkdir(parents=True, exist_ok=True)
    json_write(
        prepared / spec["g02bManifestRelativePath"],
        {
            "runId": row["run_id"],
            "planType": row["plan_type"],
            "configId": row["config_id"],
            "seed": int(row["seed"]),
            "variant": variant,
            "sourceMaterialization": row["source_materialization"],
            "destinationDirectory": row["destination_directory"],
            "runtimeJarSha256": "fixture-jar-hash",
            "runtimeJarSizeBytes": 123,
        },
    )
    context = {
        "runId": row["run_id"],
        "planType": row["plan_type"],
        "configId": row["config_id"],
        "seed": int(row["seed"]),
        "variant": variant,
        "branch": "experiment/g02b-ablation",
        "head": load_spec()["implementationBaseCommit"],
        "runtimeJarSha256": "fixture-jar-hash",
        "runtimeJarSizeBytes": 123,
        "sourceMaterialization": row["source_materialization"],
        "preparedScenario": row["destination_directory"],
        "startedAt": timestamp(),
        "status": "COMPLETED",
    }
    json_write(output_dir / spec["runContextFileName"], context)
    summary: dict[str, Any] = {
        "gaJobsSubmitted": 3,
        "gaJobsCompleted": 3,
        "gaJobsApplied": 2,
        "gaJobsDiscardedAsStale": 1,
        "staleRatioPercent": 33.333333,
        "gaRuntimeMeanSeconds": 0.05,
        "gaRuntimeMedianSeconds": 0.04,
        "gaRuntimeP95Seconds": 0.09,
        "gaRuntimeMaxSeconds": 0.1,
        "maximumAbsoluteSnapshotLagSeconds": 1.0,
        "lastAppliedStrategySimulationTimeSeconds": 120.0,
        "secondsWithoutAppliedStrategyAtEnd": 180.0,
        "strategyApplications": 2,
        "localAssignments": 5,
        "vehicleAssignments": 0,
        "edgeAssignments": 0,
        "cloudAssignments": 0,
        "simulationCompleted": True,
        "parallelGaViolations": 0,
        "futureSnapshotViolations": 0,
        "futurePoolViolations": 0,
        "invalidPoolBandwidthViolations": 0,
        "deltaTMaxMismatchViolations": 0,
    }
    native: dict[str, Any] = {
        "experimentalVariant": variant,
        "effectiveFitnessWeights": (
            {"wT": 0.4666666666666667, "wL": 0.3333333333333333, "wM": 0.0, "wR": 0.2}
            if variant == "NO_MOBILITY_PENALTY"
            else {"wT": 0.35, "wL": 0.25, "wM": 0.25, "wR": 0.15}
        ),
        "localAssignments": 5,
        "vehicleAssignments": 0,
        "edgeAssignments": 0,
        "cloudAssignments": 0,
        "gaJobsSubmitted": 3,
        "gaJobsCompleted": 3,
        "gaJobsApplied": 2,
        "gaJobsDiscardedAsStale": 1,
        "optimizationSourceDescription": "local-only(fixture)" if variant == "LOCAL_ONLY" else "mosaic-system-state-source",
        "populationReusePolicyDescription": "forced-cold" if variant == "COLD_START_NO_REUSE" else "standard",
        "wallClockTiming": {"startedAt": timestamp(), "completedAt": timestamp()},
        "jobRecords": [
            {
                "jobId": "job-1",
                "windowIndex": 0,
                "triggerType": "FIRST",
                "submissionSimulationTimeNs": 1_000_000_000,
                "submissionWallClockNs": 10_000,
                "snapshotId": "snapshot-1",
                "snapshotTimeSeconds": 0.5,
                "deltaTMaxAtSubmissionSeconds": 1.0,
                "wallClockDeadlineNs": 20_000,
                "timeoutDetectedBeforeCompletion": False,
                "waitCapDetectedWallClockNs": 15_000,
                "waitCapDetectedSimulationTimeNs": 1_500_000_000,
                "completionWallClockNs": 16_000,
                "gaRuntimeWallClockSeconds": 0.05,
                "deltaTMaxFromCompletedStepSeconds": 1.0,
                "deltaTMaxMismatchSeconds": 0.0,
                "finalStatus": "APPLIED",
                "appliedAtSimulationTimeNs": 2_000_000_000,
                "errorType": "",
                "errorMessage": "",
                "taskCount": 2,
                "candidateCount": 3,
            },
            {
                "jobId": "job-2",
                "windowIndex": 1,
                "triggerType": "PERIODIC",
                "submissionSimulationTimeNs": 10_000_000_000,
                "submissionWallClockNs": 30_000,
                "snapshotId": "snapshot-2",
                "snapshotTimeSeconds": 9.5,
                "deltaTMaxAtSubmissionSeconds": 1.0,
                "wallClockDeadlineNs": 50_000,
                "timeoutDetectedBeforeCompletion": False,
                "waitCapDetectedWallClockNs": 45_000,
                "waitCapDetectedSimulationTimeNs": 10_500_000_000,
                "completionWallClockNs": 46_000,
                "gaRuntimeWallClockSeconds": 0.04,
                "deltaTMaxFromCompletedStepSeconds": 1.0,
                "deltaTMaxMismatchSeconds": 0.0,
                "finalStatus": "STALE",
                "appliedAtSimulationTimeNs": 0,
                "errorType": "",
                "errorMessage": "",
                "taskCount": 2,
                "candidateCount": 3,
            },
        ],
    }
    if omit_counter:
        native.pop(omit_counter, None)
    if native_overrides:
        native.update(native_overrides)
    if variant == "NO_MOBILITY_PENALTY":
        total = sum(native["effectiveFitnessWeights"].values())
        assert abs(total - 1.0) < 1e-12
    json_write(output_dir / "live_run_summary.json", summary)
    json_write(live_reporting / "live_detailed_execution_report.json", native)
    json_write(
        output_dir / spec["canonicalValidatorFileName"],
        {
            "status": canonical_status,
            "simulationCompleted": True,
            "errors": [] if canonical_status == "LITERATURE_SMOKE_TEST_PASSED" else ["canonical fixture failure"],
            "warnings": [],
        },
    )
    records = [
        {
            "jobId": "job-1",
            "windowIndex": 0,
            "trigger": "FIRST",
            "snapshotTimeSeconds": 0.5,
            "dynamicity": {"globalDynamicity": 0.0, "dynamicityLevel": "LOW", "suggestedReuseMode": "FIRST_RUN"},
            "populationReuse": {"baseReuseMode": "FIRST_RUN", "appliedReuseMode": "FIRST_RUN", "reason": "first run"},
            "adaptiveWindowDecision": {"currentWindowSeconds": 1.0, "nextWindowSeconds": 1.0},
            "operationalMetrics": {"observedGaRuntimeSeconds": 0.05},
        },
        {
            "jobId": "job-2",
            "windowIndex": 1,
            "trigger": "PERIODIC",
            "snapshotTimeSeconds": 9.5,
            "dynamicity": {"globalDynamicity": 0.2, "dynamicityLevel": "MEDIUM", "suggestedReuseMode": "COLD_START"},
            "populationReuse": {"baseReuseMode": "COLD_START", "appliedReuseMode": "COLD_START", "reason": "forced"},
            "adaptiveWindowDecision": {"currentWindowSeconds": 1.0, "nextWindowSeconds": 2.0},
            "operationalMetrics": {"observedGaRuntimeSeconds": 0.04},
        },
    ]
    with (live_reporting / "live_temporal_step_records.jsonl").open("w", encoding="utf-8", newline="\n") as handle:
        for record in records:
            handle.write(json.dumps(record, sort_keys=True) + "\n")
    return validate_run_row(row, output_dir)


def _write_complete_aggregate_fixture(tmp_root: Path, output_root: Path, scientific_rows: list[dict[str, str]]) -> None:
    variants = ["LOCAL_ONLY", "NO_MOBILITY_PENALTY", "COLD_START_NO_REUSE"]
    rows = [row for row in scientific_rows if row["variant"] in variants]
    if len(rows) != 45:
        raise AssertionError("scientific fixture does not contain 45 rows")
    for row in rows:
        out = output_root / row["config_id"] / str(row["seed"]) / row["variant"]
        destination = tmp_root / "complete-destinations" / row["run_id"]
        destination.mkdir(parents=True, exist_ok=True)
        manifest = {
            "runId": row["run_id"],
            "planType": row["plan_type"],
            "configId": row["config_id"],
            "seed": int(row["seed"]),
            "variant": row["variant"],
            "sourceMaterialization": row["source_materialization"],
            "destinationDirectory": rel(destination),
            "runtimeJarSha256": "fixture-jar-hash",
            "runtimeJarSizeBytes": 123,
        }
        json_write(destination / load_spec()["g02bManifestRelativePath"], manifest)
        fixture_row = {**row, "output_path": rel(out), "destination_directory": rel(destination)}
        _validate_run_fixture(tmp_root, fixture_row, row["variant"], output=out)


def _read_single_csv_row(path: Path) -> dict[str, str]:
    rows = read_csv(path)
    if not rows:
        raise AssertionError(f"CSV is empty: {path}")
    return rows[0]


def _aggregate_has_metric(path: Path, metric: str) -> bool:
    return any(row.get("metric") == metric for row in read_csv(path))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="G02B isolated campaign tooling")
    parser.add_argument(
        "--mode",
        choices=[
            "check",
            "generate-plan",
            "prepare-smoke",
            "prepare-campaign",
            "validate-prepared-smoke",
            "validate-prepared-campaign",
            "validate-run-smoke",
            "validate-run-campaign",
            "resume-smoke",
            "resume-campaign",
            "aggregate",
            "bundle",
            "self-test",
        ],
        default="check",
    )
    parser.add_argument("--run-id")
    parser.add_argument("--run-output")
    parser.add_argument("--input-dir")
    parser.add_argument("--output-dir")
    parser.add_argument("--registry")
    parser.add_argument("--complete", action="store_true")
    args = parser.parse_args(argv)

    try:
        if args.mode == "check":
            result = check()
        elif args.mode == "generate-plan":
            result = generate_plans()
        elif args.mode == "prepare-smoke":
            result = prepare_rows("smoke", args.run_id)
        elif args.mode == "prepare-campaign":
            result = prepare_rows("campaign", args.run_id)
        elif args.mode == "validate-prepared-smoke":
            result = validate_prepared("smoke", args.run_id)
        elif args.mode == "validate-prepared-campaign":
            result = validate_prepared("campaign", args.run_id)
        elif args.mode == "validate-run-smoke":
            result = validate_run("smoke", args.run_id, args.run_output)
        elif args.mode == "validate-run-campaign":
            result = validate_run("campaign", args.run_id, args.run_output)
        elif args.mode == "resume-smoke":
            result = resume_candidates("smoke", args.registry)
        elif args.mode == "resume-campaign":
            result = resume_candidates("campaign", args.registry)
        elif args.mode == "aggregate":
            result = aggregate(args.input_dir, args.output_dir, args.complete)
        elif args.mode == "bundle":
            result = bundle(args.output_dir)
        elif args.mode == "self-test":
            result = self_test()
        else:
            raise G02BError(f"Unsupported mode: {args.mode}")
        json.dump({"ok": True, "result": result}, sys.stdout, indent=2, sort_keys=True)
        sys.stdout.write("\n")
        return 0
    except Exception as exc:  # noqa: BLE001 - command line entry point.
        json.dump({"ok": False, "error": str(exc)}, sys.stderr, indent=2, sort_keys=True)
        sys.stderr.write("\n")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
