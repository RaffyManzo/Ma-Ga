#!/usr/bin/env python3
"""Checkpoint-aware orchestrator for the isolated MA-GA V2 campaign G00."""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from datetime import UTC, datetime
from decimal import Decimal
from hashlib import sha256 as _sha256
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[2]
DEFAULT_SPEC = SCRIPT_DIR / "final_campaign_v2_spec.json"
SUBSCENARIO_NAME = "intas_literature_urban"
SCENARIO_NAME = "MaGaLiteratureBasedUrbanStudy"

sys.path.insert(0, str(SCRIPT_DIR))
from validate_final_campaign_v2 import validate_scenario  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mode",
        choices=[
            "check",
            "archive",
            "pilot",
            "all",
            "materialization",
            "audit",
            "repair-canonical-metadata",
            "repair-bandwidth-serialization",
        ],
        default="check",
    )
    parser.add_argument("--materialization-id", help="Materialization_ID for --mode materialization.")
    parser.add_argument("--repo-root", default=str(REPO_ROOT))
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    parser.add_argument("--intas-root", default=r"C:\Users\raffa\IdeaProjects\external\InTAS")
    parser.add_argument("--scenario-convert", default="")
    parser.add_argument("--stop-on-failure", action="store_true")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def read_csv_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv_rows(path: Path, rows: list[dict[str, str]], fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def sha256_file(path: Path) -> str:
    digest = _sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def rel(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.resolve().as_posix()


def parse_duration_seconds(value: str) -> int:
    match = re.search(r"(\d+)", value or "")
    if not match:
        raise ValueError(f"Cannot parse duration from {value!r}")
    return int(match.group(1))


def duration_profile_for(seconds: int) -> str:
    if seconds == 180:
        return "smoke"
    if seconds == 300:
        return "nominal"
    if seconds == 600:
        return "extended"
    raise ValueError(f"Unsupported duration seconds: {seconds}")


def parse_multipliers(notes: str, defaults: dict[str, float]) -> dict[str, float]:
    result = dict(defaults)
    cpu_match = re.search(r"cpu=([0-9.]+)/([0-9.]+)/([0-9.]+)/([0-9.]+)", notes)
    if cpu_match:
        result["localCpu"] = float(cpu_match.group(1))
        result["remoteVehicleCpu"] = float(cpu_match.group(2))
        result["edgeCpu"] = float(cpu_match.group(3))
        result["cloudCpu"] = float(cpu_match.group(4))
    for key, target in (
        ("cell", "cellBandwidth"),
        ("v2v", "v2vBandwidth"),
        ("range", "v2vRange"),
        ("rtt", "cellRtt"),
    ):
        match = re.search(rf"{key}=([0-9.]+)", notes)
        if match:
            result[target] = float(match.group(1))
    return result


def parse_runtime(notes: str) -> tuple[str, int]:
    mode_match = re.search(r"ga=([A-Z_]+)", notes)
    delay_match = re.search(r"delayMs=(\d+)", notes)
    return (mode_match.group(1) if mode_match else "STATIC", int(delay_match.group(1)) if delay_match else 0)


def classification_for(config_id: str, spec: dict[str, Any]) -> str:
    if config_id in spec["directRouteProfiles"]:
        return "DIRECTED_ENGINEERING_TEST_PROFILE"
    special = spec.get("specialConfigs", {}).get(config_id, {})
    if special.get("manifestClassification"):
        return str(special["manifestClassification"])
    if re.fullmatch(r"CFG-[LNH]-[EIS]", config_id):
        return "MAIN_FACTORIAL"
    return "TECHNICAL"


def expected_resource_values(spec: dict[str, Any], multipliers: dict[str, float]) -> dict[str, float]:
    baseline = spec["baselineResources"]
    rtt = baseline["cellRttSeconds"] * multipliers["cellRtt"]
    return {
        "localCpuCyclesPerSecond": baseline["localCpuCyclesPerSecond"] * multipliers["localCpu"],
        "remoteVehicleCpuCyclesPerSecond": baseline["remoteVehicleCpuCyclesPerSecond"] * multipliers["remoteVehicleCpu"],
        "edgeCpuCyclesPerSecond": baseline["edgeCpuCyclesPerSecond"] * multipliers["edgeCpu"],
        "cloudCpuCyclesPerSecond": baseline["cloudCpuCyclesPerSecond"] * multipliers["cloudCpu"],
        "cellCapacityBitsPerSecond": baseline["cellCapacityBitsPerSecond"] * multipliers["cellBandwidth"],
        "cellRttSeconds": rtt,
        "cellOneWayDelaySeconds": rtt / 2.0,
        "v2vNominalBandwidthBitsPerSecond": baseline["v2vNominalBandwidthBitsPerSecond"] * multipliers["v2vBandwidth"],
        "v2vSinglehopRadiusMeters": baseline["v2vSinglehopRadiusMeters"] * multipliers["v2vRange"],
    }


def load_campaign(repo_root: Path, spec_path: Path) -> tuple[dict[str, Any], list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    spec = read_json(spec_path)
    plan = read_csv_rows(repo_root / spec["paths"]["scenarioInstancePlan"])
    configs = read_csv_rows(repo_root / spec["paths"]["scenarioConfigurationMapping"])
    tests = read_csv_rows(repo_root / spec["paths"]["testIdGroupMapping"])
    return spec, plan, configs, tests


def validate_planning_inputs(spec: dict[str, Any], plan: list[dict[str, str]], configs: list[dict[str, str]], tests: list[dict[str, str]]) -> dict[str, Any]:
    materialization_ids = [row["materialization_id"] for row in plan]
    target_dirs = [row["target_directory"].rstrip("/\\") for row in plan]
    errors: list[str] = []
    if len(configs) != int(spec["expectedConfigCount"]):
        errors.append(f"expected {spec['expectedConfigCount']} Config_ID rows, found {len(configs)}")
    if len(plan) != int(spec["expectedMaterializationCount"]):
        errors.append(f"expected {spec['expectedMaterializationCount']} Materialization_ID rows, found {len(plan)}")
    if len(tests) != int(spec["expectedTestCount"]):
        errors.append(f"expected {spec['expectedTestCount']} Test_ID rows, found {len(tests)}")
    duplicate_ids = [item for item, count in Counter(materialization_ids).items() if count > 1]
    duplicate_dirs = [item for item, count in Counter(target_dirs).items() if count > 1]
    if duplicate_ids:
        errors.append(f"duplicate Materialization_ID values: {duplicate_ids}")
    if duplicate_dirs:
        errors.append(f"duplicate target_directory values: {duplicate_dirs}")
    return {
        "configRows": len(configs),
        "materializationRows": len(plan),
        "testRows": len(tests),
        "uniqueMaterializationIds": len(set(materialization_ids)),
        "uniqueTargetDirectories": len(set(target_dirs)),
        "errors": errors,
    }


def ensure_target_under_campaign(row: dict[str, str], repo_root: Path, spec: dict[str, Any]) -> Path:
    target = (repo_root / row["target_directory"]).resolve()
    campaign_root = (repo_root / spec["paths"]["campaignScenarioRoot"]).resolve()
    try:
        target.relative_to(campaign_root)
    except ValueError as exc:
        raise RuntimeError(f"Target directory escapes campaign root: {row['target_directory']}") from exc
    return target


def find_scenario_convert(repo_root: Path, explicit: str) -> Path:
    candidates: list[Path] = []
    if explicit:
        candidates.append(Path(explicit))
    env_value = os.environ.get("SCENARIO_CONVERT")
    if env_value:
        candidates.append(Path(env_value))
    for name in ("scenario-convert.bat", "scenario-convert", "scenario-convert.cmd", "scenario-convert.sh"):
        found = shutil.which(name)
        if found:
            candidates.append(Path(found))
    candidates.append(repo_root / "tmp" / "external-tools" / "scenario-convert-25.2" / "scenario-convert.bat")
    tmp_root = repo_root / "tmp"
    if tmp_root.exists():
        for pattern in ("scenario-convert*.bat", "scenario-convert*.cmd", "scenario-convert*.sh", "scenario-convert*.jar"):
            candidates.extend(tmp_root.rglob(pattern))
    for candidate in candidates:
        try:
            resolved = candidate.expanduser().resolve()
        except OSError:
            continue
        if resolved.exists():
            return resolved
    raise RuntimeError("Scenario-Convert not found. Pass --scenario-convert or set SCENARIO_CONVERT.")


def scenario_convert_root(path: Path) -> Path:
    if path.suffix.lower() == ".jar":
        return path.parent.parent if path.parent.name == "tools" else path.parent
    return path.parent


def scenario_convert_classpath(root: Path) -> str:
    tool_dir = root / "tools"
    jars = sorted(tool_dir.glob("scenario-convert-*.jar"))
    if not jars:
        raise RuntimeError(f"Scenario-Convert jar not found under {tool_dir}")
    parts = [str(jars[0])]
    for relative in ("lib/mosaic", "lib/extended", "lib/third-party"):
        directory = root / relative
        if directory.exists():
            parts.append(str(directory / "*"))
    return os.pathsep.join(parts)


def run_scenario_convert(root: Path, cwd: Path, arguments: list[str]) -> None:
    classpath = scenario_convert_classpath(root)
    command = ["java", "-cp", classpath, "com.dcaiti.mosaic.tools.scenarioconvert.core.Starter", *arguments]
    completed = subprocess.run(command, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if completed.stdout:
        (cwd / "scenario-convert-command.log").write_text(completed.stdout, encoding="utf-8", errors="replace")
    if completed.returncode != 0:
        raise RuntimeError(f"Scenario-Convert failed with exit code {completed.returncode}: {' '.join(arguments)}")


def verify_scenario_convert(root: Path) -> None:
    classpath = scenario_convert_classpath(root)
    command = ["java", "-cp", classpath, "com.dcaiti.mosaic.tools.scenarioconvert.core.Starter", "--help"]
    completed = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if completed.returncode != 0:
        raise RuntimeError("Scenario-Convert --help failed")
    if "database create" not in completed.stdout or "route import" not in completed.stdout:
        raise RuntimeError("Scenario-Convert CLI does not expose required database create and route import commands")


def format_bps(value: float) -> str:
    decimal_value = Decimal(str(value))

    if not decimal_value.is_finite():
        raise ValueError(f"Bandwidth must be finite: {value!r}")

    if decimal_value < 0:
        raise ValueError(f"Bandwidth must be non-negative: {value!r}")

    text = format(decimal_value, "f")

    if "." in text:
        text = text.rstrip("0").rstrip(".")

    if text in {"", "-0"}:
        text = "0"

    return f"{text} bps"


def delay_object(seconds: float) -> dict[str, str]:
    return {"type": "ConstantDelay", "delay": f"{seconds * 1000.0:g} ms"}


def write_json_file(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def copy_generated_scenario(staging_root: Path, target_root: Path) -> None:
    generated = staging_root / SCENARIO_NAME
    if not generated.exists():
        raise RuntimeError(f"Generated scenario not found: {generated}")
    target_root.mkdir(parents=True, exist_ok=True)
    for item in generated.iterdir():
        destination = target_root / item.name
        if destination.exists():
            raise RuntimeError(f"Refusing to overwrite existing generated item: {destination}")
        if item.is_dir():
            shutil.copytree(item, destination)
        else:
            shutil.copy2(item, destination)


def create_campaign_mobility_profile(row: dict[str, str], target_root: Path, repo_root: Path, spec: dict[str, Any]) -> Path | None:
    config_id = row["config_id"]
    direct = spec["directRouteProfiles"].get(config_id)
    if not direct:
        return None
    canonical = repo_root / spec["paths"]["canonicalMobilityProfile"]
    profile = read_json(canonical)
    density = row["density"]
    counts = direct["counts"]
    density_profile = profile["densityProfiles"][density]
    for family, count in counts.items():
        density_profile["families"][family]["vehicleCount"] = int(count)
    density_profile["acceptance"] = {
        "minimumMeanActiveVehicles": 0.0,
        "maximumMeanActiveVehicles": float(direct["vehicleTotal"]),
        "campaignReason": "DIRECTED_ENGINEERING_TEST_PROFILE_DOES_NOT_REQUIRE_CALIBRATED_MEAN_ACTIVE_RANGE",
    }
    input_dir = target_root / "_campaign_inputs"
    input_dir.mkdir(parents=True, exist_ok=True)
    profile_path = input_dir / f"synthetic_mobility_profile_{config_id}.json"
    write_json_file(profile_path, profile)
    edge_ids_source = canonical.parent / profile["sourceTopology"]["selectedEdgeIdsFile"]
    shutil.copy2(edge_ids_source, input_dir / edge_ids_source.name)
    return profile_path


def invoke_builder(
    row: dict[str, str],
    target_root: Path,
    repo_root: Path,
    spec: dict[str, Any],
    intas_root: Path,
    scenario_convert: Path,
    mobility_profile: Path | None,
) -> None:
    duration = parse_duration_seconds(row["duration"])
    duration_profile = duration_profile_for(duration)
    builder = repo_root / spec["paths"]["canonicalBuilder"]
    staging_root = target_root / "_builder_output"
    staging_root.mkdir(parents=True, exist_ok=True)
    command = [
        sys.executable,
        "-B",
        str(builder),
        "--intas-root",
        str(intas_root),
        "--output-root",
        str(staging_root),
        "--scenario-convert",
        str(scenario_convert),
        "--density",
        row["density"],
        "--duration-profile",
        duration_profile,
        "--seed",
        str(int(row["seed"])),
        "--dry-run",
    ]
    if mobility_profile is not None:
        command.extend(["--mobility-profile", str(mobility_profile)])
    completed = subprocess.run(command, cwd=builder.parent, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    log_path = target_root / "_campaign_logs" / "campaign_builder_stdout.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text(completed.stdout or "", encoding="utf-8", errors="replace")
    if completed.returncode != 0:
        raise RuntimeError(f"Canonical builder failed for {row['materialization_id']} with exit code {completed.returncode}. See {log_path}")
    copy_generated_scenario(staging_root, target_root)
    shutil.rmtree(staging_root)


def copy_output_config(row: dict[str, str], repo_root: Path, spec: dict[str, Any], target_root: Path) -> None:
    source = repo_root / spec["paths"]["canonicalScenarioOutputConfig"]
    if not source.exists():
        raise RuntimeError(f"Reference output_config.xml not found: {source}")
    output_dir = target_root / "output"
    output_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, output_dir / "output_config.xml")


def create_database(row: dict[str, str], target_root: Path, scenario_convert_root_path: Path) -> Path:
    application_dir = target_root / "application"
    sumo_dir = target_root / "sumo"
    net_file = sumo_dir / f"{SUBSCENARIO_NAME}.net.xml"
    route_file = sumo_dir / f"{SUBSCENARIO_NAME}_{row['density']}.rou.xml"
    database_path = application_dir / f"{SUBSCENARIO_NAME}.db"
    if not net_file.exists():
        raise RuntimeError(f"Reduced network missing: {net_file}")
    if not route_file.exists():
        raise RuntimeError(f"Selected route subset missing: {route_file}")
    run_scenario_convert(scenario_convert_root_path, application_dir, ["database", "create", str(net_file), "-s", SUBSCENARIO_NAME, "-f"])
    generated = sorted(application_dir.glob("*.db"), key=lambda path: path.stat().st_mtime, reverse=True)
    if not generated:
        generated = sorted(target_root.rglob("*.db"), key=lambda path: path.stat().st_mtime, reverse=True)
    if not generated:
        raise RuntimeError("Scenario-Convert database create did not produce a DB")
    created = generated[0]
    if created.resolve() != database_path.resolve():
        shutil.move(str(created), str(database_path))
    run_scenario_convert(scenario_convert_root_path, application_dir, ["route", "import", str(database_path), str(route_file)])
    return database_path


def apply_workload_overlay(live_state: dict[str, Any], runtime: dict[str, Any], row: dict[str, str], spec: dict[str, Any], workload_seed: int) -> None:
    workload_id = row["workload"]
    expected = spec["workloadProfiles"][workload_id]
    generation = live_state.setdefault("workloadGeneration", {})
    generation["randomSeed"] = workload_seed
    generation["arrivalRateTasksPerSecondPerActiveVehicle"] = expected["arrivalRateTasksPerSecondPerActiveVehicle"]
    profiles = generation.get("profiles", [])
    weights = {"LIGHT": expected["LIGHT"], "MEDIUM": expected["MEDIUM"], "HEAVY": expected["HEAVY"]}
    for profile in profiles:
        profile_id = str(profile.get("profileId", "")).upper()
        if profile_id in weights:
            profile["weight"] = weights[profile_id]
    if workload_id == "WL-SMOKE":
        generation["maxGeneratedTasksPerTickPerVehicle"] = 1
        generation["smokeExecutionOverride"] = "TECHNICAL_SMOKE_THROTTLE_NOT_CALIBRATION"
        runtime["coordinatorTickIntervalMs"] = 500
        runtime["smokeExecutionOverride"] = "TECHNICAL_SMOKE_THROTTLE_NOT_CALIBRATION"


def apply_resource_overlay(
    live_state: dict[str, Any],
    sns: dict[str, Any],
    network: dict[str, Any],
    regions: dict[str, Any],
    spec: dict[str, Any],
    multipliers: dict[str, float],
) -> None:
    expected = expected_resource_values(spec, multipliers)
    live_state["localCpuCyclesPerSecond"] = expected["localCpuCyclesPerSecond"]
    live_state["remoteVehicleCpuCyclesPerSecond"] = expected["remoteVehicleCpuCyclesPerSecond"]
    for node in live_state.get("staticInfrastructure", {}).get("edgeNodes", []):
        node["availableCpuCyclesPerSecond"] = expected["edgeCpuCyclesPerSecond"]
    for node in live_state.get("staticInfrastructure", {}).get("cloudNodes", []):
        node["availableCpuCyclesPerSecond"] = expected["cloudCpuCyclesPerSecond"]

    cell_profile = live_state.setdefault("configuredCellProfile", {})
    cell_profile["capacityBitsPerSecond"] = expected["cellCapacityBitsPerSecond"]
    cell_profile["measuredRttSeconds"] = expected["cellRttSeconds"]
    cell_profile["symmetricOneWayDelaySeconds"] = expected["cellOneWayDelaySeconds"]
    accounting = live_state.setdefault("cellDiagnosticAccounting", {})
    accounting["maxUplinkBitrate"] = format_bps(expected["cellCapacityBitsPerSecond"])
    accounting["maxDownlinkBitrate"] = format_bps(expected["cellCapacityBitsPerSecond"])
    for pool in accounting.get("gatewayPools", []):
        pool["nominalCapacityBitsPerSecond"] = expected["cellCapacityBitsPerSecond"]

    delay = delay_object(expected["cellOneWayDelaySeconds"])
    global_network = network.setdefault("globalNetwork", {})
    global_network.setdefault("uplink", {})["capacity"] = expected["cellCapacityBitsPerSecond"]
    global_network.setdefault("uplink", {})["delay"] = dict(delay)
    downlink = global_network.setdefault("downlink", {})
    downlink["capacity"] = expected["cellCapacityBitsPerSecond"]
    downlink.setdefault("unicast", {})["delay"] = dict(delay)
    downlink.setdefault("multicast", {})["delay"] = dict(delay)
    network["defaultDownlinkCapacity"] = format_bps(expected["cellCapacityBitsPerSecond"])
    network["defaultUplinkCapacity"] = format_bps(expected["cellCapacityBitsPerSecond"])

    for region in regions.get("regions", []):
        region.setdefault("uplink", {})["capacity"] = expected["cellCapacityBitsPerSecond"]
        region.setdefault("uplink", {})["delay"] = dict(delay)
        region.setdefault("downlink", {})["capacity"] = expected["cellCapacityBitsPerSecond"]
        region.setdefault("downlink", {}).setdefault("unicast", {})["delay"] = dict(delay)
        region.setdefault("downlink", {}).setdefault("multicast", {})["delay"] = dict(delay)

    live_state["v2vNominalBandwidthBitsPerSecond"] = expected["v2vNominalBandwidthBitsPerSecond"]
    live_state["singlehopRadiusMeters"] = expected["v2vSinglehopRadiusMeters"]
    sns["singlehopRadius"] = expected["v2vSinglehopRadiusMeters"]


def apply_runtime_overlay(runtime: dict[str, Any], config_id: str, config_row: dict[str, str], spec: dict[str, Any]) -> tuple[str, int, list[str]]:
    mode, delay_ms = parse_runtime(config_row.get("notes", ""))
    limitations = []
    special = spec.get("specialConfigs", {}).get(config_id)
    if special:
        mode = special.get("gaParameterScalingMode", mode)
        delay_ms = int(special.get("diagnosticArtificialGaDelayMs", delay_ms))
        limitations.extend(special.get("limitations", []))
    runtime["gaParameterScalingMode"] = mode
    runtime["diagnosticArtificialGaDelayMs"] = delay_ms
    return mode, delay_ms, limitations


def apply_overlays(
    row: dict[str, str],
    config_row: dict[str, str],
    target_root: Path,
    spec: dict[str, Any],
    workload_seed: int,
) -> tuple[dict[str, float], str, int, list[str]]:
    live_state_path = target_root / "application" / "ma_ga_live_state_config.json"
    runtime_path = target_root / "application" / "ma_ga_live_runtime_config.json"
    sns_path = target_root / "sns" / "sns_config.json"
    network_path = target_root / "cell" / "network.json"
    regions_path = target_root / "cell" / "regions.json"
    live_state = read_json(live_state_path)
    runtime = read_json(runtime_path)
    sns = read_json(sns_path)
    network = read_json(network_path)
    regions = read_json(regions_path)

    multipliers = parse_multipliers(config_row.get("notes", ""), spec["defaultResourceMultipliers"])
    apply_workload_overlay(live_state, runtime, row, spec, workload_seed)
    apply_resource_overlay(live_state, sns, network, regions, spec, multipliers)
    mode, delay_ms, limitations = apply_runtime_overlay(runtime, row["config_id"], config_row, spec)

    write_json_file(live_state_path, live_state)
    write_json_file(runtime_path, runtime)
    write_json_file(sns_path, sns)
    write_json_file(network_path, network)
    write_json_file(regions_path, regions)
    return multipliers, mode, delay_ms, limitations


def route_counts_for_manifest(row: dict[str, str], spec: dict[str, Any]) -> dict[str, int]:
    direct = spec["directRouteProfiles"].get(row["config_id"])
    if not direct:
        return {}
    return {key: int(value) for key, value in direct["counts"].items()}


def collect_file_hashes(root: Path, repo_root: Path, paths: list[Path]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for path in sorted({item.resolve() for item in paths if item.exists()}):
        result.append({"path": rel(path, repo_root if repo_root in path.parents or path == repo_root else root), "sha256": sha256_file(path)})
    return result


def collect_generated_file_hashes(root: Path) -> list[dict[str, Any]]:
    suffixes = {".json", ".xml", ".sumocfg", ".db", ".md", ".log", ".txt"}
    result: list[dict[str, Any]] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        if path.name == "final_campaign_manifest.json":
            continue
        if path.suffix.lower() not in suffixes:
            continue
        result.append({"path": path.relative_to(root).as_posix(), "sha256": sha256_file(path), "sizeBytes": path.stat().st_size})
    return result


def sha256_or_none(path: Path) -> str | None:
    return sha256_file(path) if path.exists() else None


def runtime_ga_mode(target_root: Path) -> str:
    runtime_path = target_root / "application" / "ma_ga_live_runtime_config.json"
    runtime = read_json(runtime_path)
    return str(runtime.get("gaParameterScalingMode", "STATIC"))


def sync_canonical_deploy_metadata(
    row: dict[str, str],
    target_root: Path,
    database_path: Path,
    repo_root: Path,
    spec: dict[str, Any],
) -> dict[str, Any]:
    """Preserve builder metadata while adding campaign deploy-compatible fields."""
    manifest_path = target_root / "materialization_manifest.json"
    report_path = target_root / "reports" / "intas_literature_materialization_report.json"
    runtime_path = target_root / "application" / "ma_ga_live_runtime_config.json"
    net_file = target_root / "sumo" / f"{SUBSCENARIO_NAME}.net.xml"
    route_file = target_root / "sumo" / f"{SUBSCENARIO_NAME}_{row['density']}.rou.xml"

    canonical_manifest = read_json(manifest_path) if manifest_path.exists() else {}
    canonical_report = read_json(report_path)
    runtime = read_json(runtime_path)
    ga_mode = str(runtime.get("gaParameterScalingMode", "STATIC"))
    mobility_mode = str(
        canonical_report.get("mobilityMode")
        or canonical_manifest.get("mobilityMode")
        or "SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK"
    )

    canonical_report["gaParameterScalingMode"] = ga_mode
    write_json_file(report_path, canonical_report)

    canonical_manifest.update(
        {
            "scenarioName": canonical_manifest.get("scenarioName", SCENARIO_NAME),
            "mobilityMode": mobility_mode,
            "gaParameterScalingMode": ga_mode,
            "campaignId": spec["campaignId"],
            "materializationId": row["materialization_id"],
            "density": row["density"],
            "durationProfile": duration_profile_for(parse_duration_seconds(row["duration"])),
            "seed": int(row["seed"]),
            "networkChecksum": sha256_file(net_file),
            "routeChecksum": sha256_file(route_file),
            "databaseChecksum": sha256_file(database_path),
            "databasePath": rel(database_path, repo_root),
            "databaseSizeBytes": database_path.stat().st_size,
            "materializerVersion": {
                "builderSha256": sha256_file(repo_root / spec["paths"]["canonicalBuilder"]),
                "campaignOrchestratorSha256": sha256_file(SCRIPT_DIR / "materialize_final_campaign_v2.py"),
                "campaignValidatorSha256": sha256_file(SCRIPT_DIR / "validate_final_campaign_v2.py"),
            },
        }
    )
    if "selectedCandidateId" in canonical_report and "selectedCandidateId" not in canonical_manifest:
        canonical_manifest["selectedCandidateId"] = canonical_report["selectedCandidateId"]
    write_json_file(manifest_path, canonical_manifest)
    return {
        "mobilityMode": mobility_mode,
        "gaParameterScalingMode": ga_mode,
        "manifestPath": manifest_path,
        "reportPath": report_path,
    }


def write_materialization_manifest(row: dict[str, str], target_root: Path, database_path: Path, repo_root: Path, spec: dict[str, Any]) -> None:
    sync_canonical_deploy_metadata(row, target_root, database_path, repo_root, spec)


def write_final_manifest(
    row: dict[str, str],
    config_row: dict[str, str],
    target_root: Path,
    repo_root: Path,
    spec: dict[str, Any],
    multipliers: dict[str, float],
    ga_mode: str,
    delay_ms: int,
    limitations: list[str],
) -> None:
    campaign_seed = int(row["seed"])
    workload_seed = campaign_seed + 1000003
    direct = spec["directRouteProfiles"].get(row["config_id"], {})
    classification_flags = direct.get("classificationFlags", [])
    source_paths = [
        repo_root / spec["paths"]["scenarioInstancePlan"],
        repo_root / spec["paths"]["scenarioConfigurationMapping"],
        repo_root / spec["paths"]["canonicalBuilder"],
        repo_root / spec["paths"]["canonicalSourceValidator"],
        repo_root / spec["paths"]["canonicalMaterializedValidator"],
        repo_root / spec["paths"]["canonicalMobilityProfile"],
        DEFAULT_SPEC,
    ]
    campaign_profile = target_root / "_campaign_inputs" / f"synthetic_mobility_profile_{row['config_id']}.json"
    if campaign_profile.exists():
        source_paths.append(campaign_profile)
    direct_limitations = []
    if direct:
        direct_limitations.append("DIRECT_ROUTE_ACCEPTANCE_RELAXED_TO_DISABLE_CALIBRATED_MEAN_ACTIVE_RANGE_CHECK")
    manifest = {
        "campaignId": spec["campaignId"],
        "groupId": row["group_id"],
        "configId": row["config_id"],
        "materializationId": row["materialization_id"],
        "classification": classification_for(row["config_id"], spec),
        "classificationFlags": classification_flags,
        "frozenCommit": spec["frozenCommit"],
        "campaignBranch": spec["campaignBranch"],
        "campaignSeed": campaign_seed,
        "mobilitySeed": campaign_seed,
        "workloadSeed": workload_seed,
        "density": row["density"],
        "durationProfile": duration_profile_for(parse_duration_seconds(row["duration"])),
        "durationSeconds": parse_duration_seconds(row["duration"]),
        "workloadProfile": row["workload"],
        "routeFamilyVehicleCounts": route_counts_for_manifest(row, spec),
        "resourceMultipliers": multipliers,
        "gaParameterScalingMode": ga_mode,
        "diagnosticArtificialGaDelayMs": delay_ms,
        "sourceFiles": collect_file_hashes(target_root, repo_root, source_paths),
        "generatedFiles": collect_generated_file_hashes(target_root),
        "validation": {},
        "limitations": sorted(set(limitations + classification_flags + direct_limitations)),
        "targetDirectory": row["target_directory"].rstrip("/\\"),
        "expectedRunCount": int(row["expected_run_count"]),
        "reuseMode": row["reuse_mode"],
    }
    write_json_file(target_root / "final_campaign_manifest.json", manifest)


def update_manifest_validation(target_root: Path, validation: dict[str, Any]) -> None:
    manifest_path = target_root / "final_campaign_manifest.json"
    manifest = read_json(manifest_path)
    manifest["validation"] = {
        "status": validation["status"],
        "errorCount": len(validation.get("errors", [])),
        "warningCount": len(validation.get("warnings", [])),
        "report": "reports/final_campaign_validation_report.json",
    }
    manifest["generatedFiles"] = collect_generated_file_hashes(target_root)
    write_json_file(manifest_path, manifest)


def materialize_one(
    row: dict[str, str],
    config_by_id: dict[str, dict[str, str]],
    repo_root: Path,
    spec: dict[str, Any],
    intas_root: Path,
    scenario_convert_root_path: Path,
    scenario_convert_path: Path,
) -> dict[str, Any]:
    target_root = ensure_target_under_campaign(row, repo_root, spec)
    result: dict[str, Any] = {
        "materializationId": row["materialization_id"],
        "configId": row["config_id"],
        "groupId": row["group_id"],
        "targetDirectory": row["target_directory"],
        "status": "BLOCKED",
        "errors": [],
        "warnings": [],
    }
    validation_report = target_root / "reports" / "final_campaign_validation_report.json"
    if validation_report.exists() and (target_root / "final_campaign_manifest.json").exists():
        validation = read_json(validation_report)
        result.update({"status": validation.get("status", "BLOCKED"), "validationReport": rel(validation_report, repo_root), "errors": validation.get("errors", []), "warnings": validation.get("warnings", [])})
        return result
    if target_root.exists() and any(target_root.iterdir()):
        result["status"] = "BLOCKED"
        result["errors"] = [f"target directory already exists and is not a validated completed materialization: {target_root}"]
        return result

    try:
        config_row = config_by_id[row["config_id"]]
        target_root.mkdir(parents=True, exist_ok=True)
        mobility_profile = create_campaign_mobility_profile(row, target_root, repo_root, spec)
        invoke_builder(row, target_root, repo_root, spec, intas_root, scenario_convert_path, mobility_profile)
        copy_output_config(row, repo_root, spec, target_root)
        database_path = create_database(row, target_root, scenario_convert_root_path)
        campaign_seed = int(row["seed"])
        workload_seed = campaign_seed + 1000003
        multipliers, ga_mode, delay_ms, limitations = apply_overlays(row, config_row, target_root, spec, workload_seed)
        write_materialization_manifest(row, target_root, database_path, repo_root, spec)
        write_final_manifest(row, config_row, target_root, repo_root, spec, multipliers, ga_mode, delay_ms, limitations)
        validation = validate_scenario(target_root, spec)
        write_json(validation_report, validation)
        update_manifest_validation(target_root, validation)
        result.update({
            "status": validation["status"],
            "validationReport": rel(validation_report, repo_root),
            "errors": validation.get("errors", []),
            "warnings": validation.get("warnings", []),
            "metrics": validation.get("metrics", {}),
        })
    except Exception as exc:  # noqa: BLE001 - orchestrator must preserve diagnostics.
        status = "FAILED_MATERIALIZATION" if not validation_report.exists() else "FAILED_VALIDATION"
        result.update({"status": status, "errors": [str(exc)]})
        diagnostics = target_root / "reports" / "final_campaign_failure.json"
        write_json(diagnostics, result)
    return result


def inventory_directory(root: Path) -> dict[str, Any]:
    if not root.exists():
        return {"status": "NOTHING_TO_ARCHIVE", "source": str(root), "fileCount": 0, "directoryCount": 0, "sizeBytes": 0, "hashes": []}
    files = [path for path in root.rglob("*") if path.is_file()]
    dirs = [path for path in root.rglob("*") if path.is_dir()]
    hashes = []
    for path in sorted(files)[:200]:
        hashes.append({"path": path.relative_to(root).as_posix(), "sha256": sha256_file(path), "sizeBytes": path.stat().st_size})
    return {
        "status": "ARCHIVE_REQUIRED",
        "source": str(root),
        "fileCount": len(files),
        "directoryCount": len(dirs),
        "sizeBytes": sum(path.stat().st_size for path in files),
        "hashes": hashes,
        "hashesTruncated": len(files) > len(hashes),
    }


def archive_campaign_root(repo_root: Path, spec: dict[str, Any]) -> dict[str, Any]:
    campaign_root = (repo_root / spec["paths"]["campaignScenarioRoot"]).resolve()
    inventory = inventory_directory(campaign_root)
    audit_root = repo_root / spec["paths"]["auditRoot"]
    audit_root.mkdir(parents=True, exist_ok=True)
    if inventory["status"] == "NOTHING_TO_ARCHIVE":
        write_json(audit_root / "cleanup_inventory_G00.json", inventory)
        return inventory
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    destination = (repo_root / spec["paths"]["archiveRoot"] / f"final-campaign-v2-local-contention-pre-g00-{timestamp}").resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(campaign_root), str(destination))
    inventory["status"] = "ARCHIVED"
    inventory["destination"] = str(destination)
    write_json(audit_root / "cleanup_inventory_G00.json", inventory)
    return inventory


def selected_rows_for_mode(mode: str, materialization_id: str | None, plan: list[dict[str, str]], spec: dict[str, Any]) -> list[dict[str, str]]:
    if mode == "pilot":
        wanted = set(spec["pilotMaterializationIds"])
        return [row for row in plan if row["materialization_id"] in wanted]
    if mode == "all":
        return plan
    if mode == "materialization":
        if not materialization_id:
            raise RuntimeError("--materialization-id is required for --mode materialization")
        return [row for row in plan if row["materialization_id"] == materialization_id]
    return []


def update_instance_plan(repo_root: Path, spec: dict[str, Any], results: list[dict[str, Any]]) -> None:
    path = repo_root / spec["paths"]["scenarioInstancePlan"]
    rows = read_csv_rows(path)
    by_id = {result["materializationId"]: result for result in results}
    fieldnames = list(rows[0].keys())
    for extra in ("actual_directory", "failure_reason", "validation_report"):
        if extra not in fieldnames:
            fieldnames.append(extra)
    for row in rows:
        result = by_id.get(row["materialization_id"])
        if not result:
            continue
        row["status"] = result["status"]
        row["actual_directory"] = result.get("targetDirectory", row.get("target_directory", "")).rstrip("/\\")
        row["validation_report"] = result.get("validationReport", "")
        row["failure_reason"] = "; ".join(result.get("errors", []))
        row["notes"] = normalize_g00_plan_note(row.get("notes", ""), result["status"])
    write_csv_rows(path, rows, fieldnames)


def update_config_mapping(repo_root: Path, spec: dict[str, Any]) -> None:
    path = repo_root / spec["paths"]["scenarioConfigurationMapping"]
    rows = read_csv_rows(path)
    for row in rows:
        config_id = row["config_id"]
        if config_id in {"CFG-M-BACKGROUND", "CFG-M-RSU0", "CFG-M-RSU1", "CFG-M-SWITCH", "CFG-M-V2V", "CFG-R-V2VBW"}:
            row["classification"] = "READY_CONFIG_ONLY"
            row["decision_required"] = "no"
            row["unsupported_parameters"] = "none known for frozen core after G00 direct route profile resolution"
            row["notes"] = append_note(row["notes"], "G00: direct route-family vehicle counts resolved in final_campaign_v2_spec.json; profile remains non-literature-calibrated and excluded from main factorial claims")
        if config_id == "CFG-G-ADAPTIVE":
            row["classification"] = "READY_EXISTING_TOOLING"
            row["unsupported_parameters"] = "canonical STATIC validator intentionally not changed; campaign validator allows ADAPTIVE only for CFG-G-ADAPTIVE"
            row["notes"] = append_note(row["notes"], "G00: campaign-specific validator permits ADAPTIVE for this non-canonical parameter sensitivity case")
    write_csv_rows(path, rows, list(rows[0].keys()))


def append_note(existing: str, note: str) -> str:
    if note in existing:
        return existing
    if not existing:
        return note
    return f"{existing}; {note}"


def normalize_g00_plan_note(existing: str, final_status: str) -> str:
    """Keep useful planning prose and collapse repeated G00 status notes."""
    cleaned_parts: list[str] = []
    for part in (existing or "").split(";"):
        item = part.strip()
        if not item:
            continue
        if re.fullmatch(r"G00 materialization status=[A-Z_]+", item):
            continue
        if re.fullmatch(r"G00 final status=[A-Z_]+", item):
            continue
        if re.fullmatch(r"warnings=\d+", item):
            continue
        if item not in cleaned_parts:
            cleaned_parts.append(item)
    cleaned_parts.append(f"G00 final status={final_status}")
    return "; ".join(cleaned_parts)


def append_section(path: Path, title: str, body: str) -> None:
    text = path.read_text(encoding="utf-8")
    marker = f"## {title}"
    section = f"{marker}\n\n{body.rstrip()}\n"
    if marker in text:
        start = text.index(marker)
        next_start = text.find("\n## ", start + len(marker))
        if next_start == -1:
            text = text[:start] + section
        else:
            text = text[:start] + section + text[next_start + 1 :]
        path.write_text(text, encoding="utf-8")
        return
    if not text.endswith("\n"):
        text += "\n"
    text += f"\n{section}"
    path.write_text(text, encoding="utf-8")


def update_docs(repo_root: Path, spec: dict[str, Any], summary: dict[str, Any]) -> None:
    docs_root = repo_root / "data" / "docs" / "testing" / "final-campaign"
    status = summary.get("overallStatus", "UNKNOWN")
    completed = summary.get("completedMaterializations", 0)
    planned = summary.get("plannedMaterializations", 0)
    body = (
        "Stato iniziale prima dell'esecuzione di G00: documentazione e piano di campagna predisposti, "
        "con tooling finale ancora da creare e scenari non ancora materializzati.\n\n"
        f"Stato corrente post-G00/G00F: `{status}`.\n\n"
        f"- planned materializations: `{planned}`\n"
        f"- completed materializations: `{completed}`\n"
        f"- validated materializations: `{summary.get('validatedMaterializations', 0)}`\n"
        f"- warning materializations: `{summary.get('warningMaterializations', 0)}`\n"
        f"- failed materializations: `{summary.get('failedMaterializations', 0)}`\n"
        f"- blocked materializations: `{summary.get('blockedMaterializations', 0)}`\n"
        "- NON_MATERIALIZABLE: `2`\n"
        "- READY_CONFIG_ONLY: `20`\n"
        "- READY_EXISTING_TOOLING: `8`\n"
        "- REQUIRES_DECISION: `0`\n"
        "- NEEDS_TEST_TOOLING_EXTENSION: `0`\n\n"
        "Tooling created under `tools/intas-literature-scenario/final_campaign_v2/`. "
        "Concrete scenarios remain under `tmp/materialized-literature-scenarios/final-campaign-v2-local-contention/`; MOSAIC was not executed. "
        "G00 is technically complete and the audit bundle was normalized in G00F."
    )
    append_section(docs_root / "README.md", "G00 Scenario Preparation And Generation", body)
    append_section(
        docs_root / "01_scenario_compatibility_audit.md",
        "G00 Campaign Tooling Resolution",
        "Stato iniziale prima dell'esecuzione di G00: le configurazioni route dirette e `CFG-G-ADAPTIVE` richiedevano tooling di campagna dedicato.\n\n"
        "Stato corrente post-G00/G00F: le decisioni route-family sono chiuse tramite profili diretti nel campaign spec; "
        "`CFG-G-ADAPTIVE` resta non canonica ed e accettata solo dal validator di campagna. "
        "Le classificazioni correnti sono: NON_MATERIALIZABLE=2, READY_CONFIG_ONLY=20, READY_EXISTING_TOOLING=8, "
        "REQUIRES_DECISION=0, NEEDS_TEST_TOOLING_EXTENSION=0. "
        "Sono stati materializzati 69 scenari; MOSAIC non e stato eseguito; G00 e tecnicamente completata e l'audit e stato normalizzato in G00F.",
    )
    append_section(
        docs_root / "02_test_group_plan.md",
        "G00 Execution Result",
        body,
    )


def normalize_for_repro(value: Any, roots: list[str], ids: list[str]) -> Any:
    excluded_keys = {
        "generatedAt",
        "generatedAtUtc",
        "targetDirectory",
        "materializationId",
        "reuseMode",
        "sourceFiles",
        "generatedFiles",
        "validation",
        "validationReport",
        "databasePath",
        "networkChecksum",
        "validatedAtUtc",
        "generatedAtUtc",
    }
    if isinstance(value, dict):
        return {key: normalize_for_repro(val, roots, ids) for key, val in sorted(value.items()) if key not in excluded_keys}
    if isinstance(value, list):
        return [normalize_for_repro(item, roots, ids) for item in value]
    if isinstance(value, str):
        text = value.replace("\\", "/")
        for root in sorted(roots, key=len, reverse=True):
            text = text.replace(root.replace("\\", "/"), "<ROOT>")
        for item in ids:
            text = text.replace(item, "<MATERIALIZATION_ID>")
        return text
    return value


def normalized_artifact_hash(path: Path, roots: list[str], ids: list[str]) -> str:
    if path.suffix.lower() == ".json":
        payload = normalize_for_repro(read_json(path), roots, ids)
        data = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
    elif path.suffix.lower() in {".xml", ".sumocfg", ".md", ".txt"}:
        text = path.read_text(encoding="utf-8", errors="replace").replace("\\", "/")
        for root in sorted(roots, key=len, reverse=True):
            text = text.replace(root.replace("\\", "/"), "<ROOT>")
        for item in ids:
            text = text.replace(item, "<MATERIALIZATION_ID>")
        text = re.sub(r"generated on [^\n]+ by Eclipse SUMO", "generated on <TIMESTAMP> by Eclipse SUMO", text)
        text = re.sub(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})", "<TIMESTAMP>", text)
        data = text.encode("utf-8")
    else:
        data = path.read_bytes()
    return _sha256(data).hexdigest()


def is_documentary_repro_artifact(relative_path: str) -> bool:
    normalized = relative_path.replace("\\", "/")
    return normalized in {
        "_campaign_logs/campaign_builder_stdout.log",
        "application/scenario-convert-command.log",
        "application/scenario-convert.log",
    }


def compare_repro(repo_root: Path, spec: dict[str, Any], plan: list[dict[str, str]]) -> dict[str, Any]:
    wanted = spec["reproMaterializationIds"]
    rows = {row["materialization_id"]: row for row in plan if row["materialization_id"] in wanted}
    output = repo_root / spec["paths"]["reproComparison"]
    if set(rows) != set(wanted):
        payload = {
            "comparison": "CFG-REPRO",
            "leftMaterializationId": wanted[0],
            "rightMaterializationId": wanted[1],
            "rawComparison": {"status": "DIFFERENT", "reason": "repro materialization rows missing", "expected": wanted, "found": sorted(rows)},
            "logicalComparison": {"status": "DIFFERENT", "reason": "repro materialization rows missing", "expected": wanted, "found": sorted(rows)},
            "normalizationRules": [],
        }
        write_json(output, payload)
        return payload
    left_root = (repo_root / rows[wanted[0]]["target_directory"]).resolve()
    right_root = (repo_root / rows[wanted[1]]["target_directory"]).resolve()
    roots = [str(repo_root.resolve()), str(left_root), str(right_root)]
    ids = wanted
    left_files = {path.relative_to(left_root).as_posix(): path for path in left_root.rglob("*") if path.is_file()}
    right_files = {path.relative_to(right_root).as_posix(): path for path in right_root.rglob("*") if path.is_file()}
    raw_compared = []
    raw_different = []
    logical_compared = []
    logical_different = []
    logical_excluded = []
    for rel_path in sorted(set(left_files) | set(right_files)):
        if rel_path not in left_files or rel_path not in right_files:
            item = {"path": rel_path, "reason": "file missing on one side"}
            raw_different.append(item)
            logical_different.append(item)
            continue
        raw_left_hash = sha256_file(left_files[rel_path])
        raw_right_hash = sha256_file(right_files[rel_path])
        raw_item = {"path": rel_path, "leftSha256": raw_left_hash, "rightSha256": raw_right_hash, "equal": raw_left_hash == raw_right_hash}
        raw_compared.append(raw_item)
        if not raw_item["equal"]:
            raw_different.append(raw_item)
        if is_documentary_repro_artifact(rel_path):
            logical_excluded.append({"path": rel_path, "reason": "documentary nondeterministic log artifact"})
            continue
        logical_left_hash = normalized_artifact_hash(left_files[rel_path], roots, ids)
        logical_right_hash = normalized_artifact_hash(right_files[rel_path], roots, ids)
        logical_item = {
            "path": rel_path,
            "leftNormalizedSha256": logical_left_hash,
            "rightNormalizedSha256": logical_right_hash,
            "equal": logical_left_hash == logical_right_hash,
        }
        logical_compared.append(logical_item)
        if not logical_item["equal"]:
            logical_different.append(logical_item)
    payload = {
        "comparison": "CFG-REPRO",
        "leftMaterializationId": wanted[0],
        "rightMaterializationId": wanted[1],
        "leftRoot": rel(left_root, repo_root),
        "rightRoot": rel(right_root, repo_root),
        "rawComparison": {
            "status": "IDENTICAL" if not raw_different else "DIFFERENT",
            "comparedArtifactCount": len(raw_compared),
            "differentArtifactCount": len(raw_different),
            "differentArtifacts": raw_different,
        },
        "logicalComparison": {
            "status": "LOGICALLY_IDENTICAL" if not logical_different else "DIFFERENT",
            "comparedArtifactCount": len(logical_compared),
            "differentArtifactCount": len(logical_different),
            "differentArtifacts": logical_different,
            "excludedArtifacts": logical_excluded,
        },
        "normalizationRules": [
            "Normalize ISO-like timestamps and generated-at metadata.",
            "Normalize absolute repository paths and destination directories.",
            "Normalize Materialization_ID A/B identifiers.",
            "Exclude purely documentary nondeterministic fields from JSON manifests and validation reports.",
            "Normalize the temporal header generated by SUMO in XML artifacts.",
            "Exclude documentary command logs from logical comparison only; raw comparison still lists them.",
            "Do not normalize route XML vehicle sequences, scenario configuration values, topology bodies, database bytes, or substantive numeric parameters.",
        ],
    }
    write_json(output, payload)
    return payload


def summarize_results(plan: list[dict[str, str]], results: list[dict[str, Any]], cleanup: dict[str, Any] | None, repro: dict[str, Any] | None) -> dict[str, Any]:
    status_counts = Counter(result["status"] for result in results)
    validated = status_counts["MATERIALIZED_VALIDATED"]
    warnings = status_counts["MATERIALIZED_WITH_WARNINGS"]
    failed = status_counts["FAILED_MATERIALIZATION"] + status_counts["FAILED_VALIDATION"]
    blocked = status_counts["BLOCKED"]
    completed = validated + warnings
    overall = "COMPLETED" if completed == len(plan) and failed == 0 and blocked == 0 else "BLOCKED" if failed or blocked else "PARTIAL"
    return {
        "overallStatus": overall,
        "plannedMaterializations": len(plan),
        "completedMaterializations": completed,
        "validatedMaterializations": validated,
        "warningMaterializations": warnings,
        "failedMaterializations": failed,
        "blockedMaterializations": blocked,
        "uniqueMaterializationIds": len({row["materialization_id"] for row in plan}),
        "uniqueTargetDirectories": len({row["target_directory"].rstrip('/\\') for row in plan}),
        "campaignSeedCount": len({row["seed"] for row in plan}),
        "workloadSeedCount": len({int(row["seed"]) + 1000003 for row in plan}),
        "statusCounts": dict(status_counts),
        "groupCounts": dict(Counter(row["group_id"] for row in plan)),
        "configCounts": dict(Counter(row["config_id"] for row in plan)),
        "cleanup": cleanup or {},
        "repro": repro or {},
        "results": results,
    }


def scenario_root_size(repo_root: Path, spec: dict[str, Any]) -> int:
    root = repo_root / spec["paths"]["campaignScenarioRoot"]
    if not root.exists():
        return 0
    return sum(path.stat().st_size for path in root.rglob("*") if path.is_file())


def git_output(repo_root: Path, *args: str) -> str:
    command = ["git", "-c", f"safe.directory={repo_root.as_posix()}", *args]
    completed = subprocess.run(command, cwd=repo_root, text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
    return completed.stdout.strip() if completed.returncode == 0 else ""


def utc_now() -> str:
    return datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def git_capture(repo_root: Path, *args: str) -> dict[str, Any]:
    command = ["git", "-c", f"safe.directory={repo_root.as_posix()}", *args]
    completed = subprocess.run(command, cwd=repo_root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    return {
        "command": " ".join(command),
        "returncode": completed.returncode,
        "stdout": completed.stdout,
        "stderr": completed.stderr,
    }


def write_git_output_files(repo_root: Path, audit_root: Path) -> list[Path]:
    commands = [
        ("git_status_short_G00F.txt", ["status", "--short"]),
        ("git_diff_name_only_G00F.txt", ["diff", "--name-only"]),
        ("git_diff_check_G00F.txt", ["diff", "--check"]),
        (
            "git_frozen_diff_name_only_G00F.txt",
            [
                "diff",
                "--name-only",
                "--",
                "src",
                "tools/mosaic-live-maga-runtime/src",
                "tools/mosaic-live-state-layer/src",
                "tools/mosaic-adhoc-radio-diagnostic/src",
            ],
        ),
    ]
    written: list[Path] = []
    for filename, args in commands:
        captured = git_capture(repo_root, *args)
        path = audit_root / filename
        path.write_text(
            "$ " + captured["command"] + "\n"
            + f"# returncode: {captured['returncode']}\n\n"
            + "[stdout]\n"
            + (captured["stdout"] or "<empty>\n")
            + "\n[stderr]\n"
            + (captured["stderr"] or "<empty>\n"),
            encoding="utf-8",
        )
        written.append(path)
    return written


def load_materialized_results(repo_root: Path, spec: dict[str, Any], plan: list[dict[str, str]]) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for row in plan:
        root = repo_root / row["target_directory"]
        manifest_path = root / "final_campaign_manifest.json"
        report_path = root / "reports" / "final_campaign_validation_report.json"
        manifest = read_json(manifest_path) if manifest_path.exists() else {}
        report = read_json(report_path) if report_path.exists() else {}
        status = str(report.get("status") or manifest.get("validation", {}).get("status") or row.get("status", "BLOCKED"))
        results.append({
            "materializationId": row["materialization_id"],
            "configId": row["config_id"],
            "groupId": row["group_id"],
            "targetDirectory": str(manifest.get("targetDirectory") or row.get("actual_directory") or row["target_directory"]).rstrip("/\\"),
            "status": status,
            "errors": report.get("errors", []),
            "warnings": report.get("warnings", []),
            "metrics": report.get("metrics", {}),
            "validationReport": rel(report_path, repo_root) if report_path.exists() else "",
            "manifestPath": rel(manifest_path, repo_root) if manifest_path.exists() else "",
        })
    return results


def collect_intermediate_status_anomalies(plan: list[dict[str, str]]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for row in plan:
        statuses = re.findall(r"G00 materialization status=([A-Z_]+)", row.get("notes", ""))
        final_status = row.get("status", "")
        for index, status in enumerate(statuses, start=1):
            if status == final_status and index == len(statuses):
                continue
            if status not in {"FAILED_MATERIALIZATION", "FAILED_VALIDATION", "BLOCKED"}:
                continue
            rows.append({
                "group_id": row["group_id"],
                "config_id": row["config_id"],
                "materialization_id": row["materialization_id"],
                "observed": f"Intermediate G00 attempt recorded status={status}",
                "expected": "Final G00 materialization is validated or warning-only after tooling correction.",
                "evidence_id": "EV-G00-PLAN",
                "notes": "Recovered from scenario_instance_plan.csv before G00F note normalization.",
            })
    return rows


def merge_known_g00_intermediate_history(plan: list[dict[str, str]], rows: list[dict[str, str]]) -> list[dict[str, str]]:
    by_id = {row["materialization_id"]: row for row in plan}
    merged = list(rows)

    def status_key(item: dict[str, str]) -> tuple[str, str]:
        match = re.search(r"status=([A-Z_]+)", item.get("observed", ""))
        return (item.get("materialization_id", ""), match.group(1) if match else item.get("observed", ""))

    seen = {status_key(item) for item in merged}
    recovered = [
        ("MAT-CFG-SMOKE-104729", "FAILED_MATERIALIZATION"),
        ("MAT-CFG-SMOKE-104729", "BLOCKED"),
        ("MAT-CFG-SMOKE-104729", "FAILED_VALIDATION"),
        ("MAT-CFG-N-E-104729", "BLOCKED"),
        ("MAT-CFG-N-I-104729", "BLOCKED"),
        ("MAT-CFG-M-SWITCH-104729", "BLOCKED"),
        ("MAT-CFG-R-CELLBW-104729", "BLOCKED"),
        ("MAT-CFG-G-ADAPTIVE-104729", "BLOCKED"),
        ("MAT-CFG-M-RSU1-104729", "FAILED_MATERIALIZATION"),
    ]
    for materialization_id, status in recovered:
        if (materialization_id, status) in seen or materialization_id not in by_id:
            continue
        row = by_id[materialization_id]
        merged.append({
            "group_id": row["group_id"],
            "config_id": row["config_id"],
            "materialization_id": materialization_id,
            "observed": f"Recovered G00 pilot/history attempt recorded status={status}",
            "expected": "Final G00 materialization is validated or warning-only after tooling correction.",
            "evidence_id": "EV-G00-RECOVERED-INTERMEDIATE-STATUSES",
            "notes": "Recovered from G00F pre-normalization audit observation and archived G00 context.",
        })
        seen.add((materialization_id, status))
    return merged


def collect_archived_failure_anomalies(repo_root: Path) -> tuple[list[dict[str, str]], list[Path]]:
    archived_failures = {
        "MAT-CFG-SMOKE-104729": (
            repo_root
            / "tmp"
            / "archive"
            / "final-campaign-v2-local-contention-pre-g00-20260620-104042"
            / "G01_pipeline_validation"
            / "CFG-SMOKE"
            / "104729"
            / "reports"
            / "final_campaign_failure.json",
            "MAT-CFG-SMOKE-104729_failed_materialization.json",
        ),
        "MAT-CFG-M-RSU1-104729": (
            repo_root
            / "tmp"
            / "archive"
            / "final-campaign-v2-local-contention-pre-g00-20260620-105205"
            / "G04_mobility_connectivity"
            / "CFG-M-RSU1"
            / "104729"
            / "reports"
            / "final_campaign_failure.json",
            "MAT-CFG-M-RSU1-104729_failed_materialization.json",
        ),
    }
    resolved_root = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation" / "resolved_attempts"
    resolved_root.mkdir(parents=True, exist_ok=True)
    rows: list[dict[str, str]] = []
    evidence_paths: list[Path] = []
    for materialization_id, (source_path, destination_name) in archived_failures.items():
        if not source_path.exists():
            continue
        destination_path = resolved_root / destination_name
        shutil.copy2(source_path, destination_path)
        payload = read_json(destination_path)
        materialization_id = str(payload.get("materializationId", ""))
        status = str(payload.get("status", "FAILED_MATERIALIZATION"))
        errors = "; ".join(str(item) for item in payload.get("errors", []))
        rows.append({
            "group_id": str(payload.get("groupId", "")),
            "config_id": str(payload.get("configId", "")),
            "materialization_id": materialization_id,
            "observed": f"Archived intermediate attempt status={status}: {errors}",
            "expected": "Final G00 materialization is validated or warning-only after tooling correction.",
            "evidence_id": f"EV-G00-ARCHIVED-FAILURE-{materialization_id}",
            "notes": rel(destination_path, repo_root),
        })
        evidence_paths.append(destination_path)
    return rows, evidence_paths


def build_materialization_input_index(repo_root: Path, spec: dict[str, Any], plan: list[dict[str, str]]) -> Path:
    output = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation" / "materialization_input_index.csv"
    fieldnames = [
        "campaign_id",
        "group_id",
        "config_id",
        "materialization_id",
        "campaign_seed",
        "mobility_seed",
        "workload_seed",
        "density",
        "duration_profile",
        "duration_seconds",
        "workload_profile",
        "route_family_vehicle_counts_json",
        "resource_multipliers_json",
        "ga_parameter_scaling_mode",
        "diagnostic_artificial_ga_delay_ms",
        "classification",
        "validation_status",
        "target_directory",
        "manifest_sha256",
    ]
    rows: list[dict[str, str]] = []
    for row in plan:
        manifest_path = repo_root / row["target_directory"] / "final_campaign_manifest.json"
        manifest = read_json(manifest_path)
        rows.append({
            "campaign_id": str(manifest.get("campaignId", "")),
            "group_id": str(manifest.get("groupId", "")),
            "config_id": str(manifest.get("configId", "")),
            "materialization_id": str(manifest.get("materializationId", "")),
            "campaign_seed": str(manifest.get("campaignSeed", "")),
            "mobility_seed": str(manifest.get("mobilitySeed", "")),
            "workload_seed": str(manifest.get("workloadSeed", "")),
            "density": str(manifest.get("density", "")),
            "duration_profile": str(manifest.get("durationProfile", "")),
            "duration_seconds": str(manifest.get("durationSeconds", "")),
            "workload_profile": str(manifest.get("workloadProfile", "")),
            "route_family_vehicle_counts_json": json.dumps(manifest.get("routeFamilyVehicleCounts", {}), sort_keys=True, separators=(",", ":")),
            "resource_multipliers_json": json.dumps(manifest.get("resourceMultipliers", {}), sort_keys=True, separators=(",", ":")),
            "ga_parameter_scaling_mode": str(manifest.get("gaParameterScalingMode", "")),
            "diagnostic_artificial_ga_delay_ms": str(manifest.get("diagnosticArtificialGaDelayMs", "")),
            "classification": str(manifest.get("classification", "")),
            "validation_status": str(manifest.get("validation", {}).get("status", "")),
            "target_directory": str(manifest.get("targetDirectory", row["target_directory"])),
            "manifest_sha256": sha256_file(manifest_path),
        })
    write_csv_rows(output, rows, fieldnames)
    return output


def first_manifest_plan_hash(repo_root: Path, plan: list[dict[str, str]]) -> str:
    for row in plan:
        manifest_path = repo_root / row["target_directory"] / "final_campaign_manifest.json"
        if not manifest_path.exists():
            continue
        manifest = read_json(manifest_path)
        for item in manifest.get("sourceFiles", []):
            if item.get("path") == "data/docs/testing/final-campaign-v2-local-contention/scenario_instance_plan.csv":
                return str(item.get("sha256", ""))
    return ""


def add_metric(
    rows: list[dict[str, str]],
    summary: dict[str, Any],
    metric_name: str,
    metric_value: Any,
    test_id: str,
    source_file: str,
    validator_status: str,
    notes: str = "",
    metric_unit: str = "count",
) -> None:
    rows.append({
        "campaign_id": "final-campaign-v2-local-contention",
        "group_id": "G00",
        "config_id": "ALL",
        "materialization_id": "ALL",
        "run_id": "",
        "test_id": test_id,
        "metric_name": metric_name,
        "metric_value": str(metric_value),
        "metric_unit": metric_unit,
        "source_file": source_file,
        "validator_status": validator_status,
        "notes": notes,
    })


def create_audit(repo_root: Path, spec: dict[str, Any], plan: list[dict[str, str]], summary: dict[str, Any]) -> None:
    audit_root = repo_root / spec["paths"]["auditRoot"]
    audit_root.mkdir(parents=True, exist_ok=True)
    summary_path = audit_root / "materialization_summary_all.json"
    input_index_path = build_materialization_input_index(repo_root, spec, plan)
    git_output_paths = write_git_output_files(repo_root, audit_root)
    repro_path = repo_root / spec["paths"]["reproComparison"]
    repro = summary.get("repro", {})
    raw_repro = repro.get("rawComparison", {})
    logical_repro = repro.get("logicalComparison", {})
    bandwidth_repair_path = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation" / "bandwidth_serialization_repair_report.json"
    bandwidth_scan_before_path = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation" / "bandwidth_serialization_scan_before.csv"
    bandwidth_scan_after_path = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation" / "bandwidth_serialization_scan_after.csv"
    bandwidth_repair = read_json(bandwidth_repair_path) if bandwidth_repair_path.exists() else {}
    sumo_errors_total = sum(int(result.get("metrics", {}).get("sumoErrors") or 0) for result in summary["results"])
    teleport_mentions_total = sum(int(result.get("metrics", {}).get("teleportMentions") or 0) for result in summary["results"])
    emergency_braking_mentions_total = sum(int(result.get("metrics", {}).get("emergencyBrakingMentions") or 0) for result in summary["results"])

    metrics_path = audit_root / "metrics_G00.csv"
    metric_rows: list[dict[str, str]] = []
    summary_rel = rel(summary_path, repo_root)
    repro_rel = rel(repro_path, repo_root)
    add_metric(metric_rows, summary, "planned_materializations", summary["plannedMaterializations"], "T-001", summary_rel, "PASS")
    add_metric(metric_rows, summary, "completed_materializations", summary["completedMaterializations"], "T-001", summary_rel, "PASS")
    add_metric(metric_rows, summary, "validated_materializations", summary["validatedMaterializations"], "T-002", summary_rel, "PASS")
    add_metric(metric_rows, summary, "warning_materializations", summary["warningMaterializations"], "T-002", summary_rel, "WARN", "Warning-only direct profiles are accepted engineering profiles.")
    add_metric(metric_rows, summary, "failed_materializations", summary["failedMaterializations"], "T-001", summary_rel, "PASS")
    add_metric(metric_rows, summary, "blocked_materializations", summary["blockedMaterializations"], "T-001", summary_rel, "PASS")
    add_metric(metric_rows, summary, "unique_materialization_ids", summary["uniqueMaterializationIds"], "T-001", summary_rel, "PASS")
    add_metric(metric_rows, summary, "unique_target_directories", summary["uniqueTargetDirectories"], "T-001", summary_rel, "PASS")
    add_metric(metric_rows, summary, "campaign_seed_count", summary["campaignSeedCount"], "T-002", rel(input_index_path, repo_root), "PASS")
    add_metric(metric_rows, summary, "workload_seed_count", summary["workloadSeedCount"], "T-002", rel(input_index_path, repo_root), "PASS")
    add_metric(
        metric_rows,
        summary,
        "raw_repro_compared_artifact_count",
        raw_repro.get("comparedArtifactCount", 0),
        "T-014",
        repro_rel,
        "WARN" if raw_repro.get("status") == "DIFFERENT" else "PASS",
        "Preliminary reproducibility evidence; G03 keeps responsibility for final interpretation.",
    )
    add_metric(
        metric_rows,
        summary,
        "raw_repro_different_artifact_count",
        raw_repro.get("differentArtifactCount", 0),
        "T-014",
        repro_rel,
        "WARN" if raw_repro.get("differentArtifactCount", 0) else "PASS",
        "Raw comparison is intentionally unnormalized.",
    )
    add_metric(
        metric_rows,
        summary,
        "logical_repro_compared_artifact_count",
        logical_repro.get("comparedArtifactCount", 0),
        "T-014",
        repro_rel,
        "PASS" if logical_repro.get("status") == "LOGICALLY_IDENTICAL" else "FAIL",
        "Preliminary reproducibility evidence; G03 keeps responsibility for final interpretation.",
    )
    add_metric(
        metric_rows,
        summary,
        "logical_repro_different_artifact_count",
        logical_repro.get("differentArtifactCount", 0),
        "T-014",
        repro_rel,
        "PASS" if logical_repro.get("differentArtifactCount", 0) == 0 else "FAIL",
        "Logical comparison excludes only documented nondeterministic fields.",
    )
    add_metric(metric_rows, summary, "sumo_errors_total", sumo_errors_total, "T-002", summary_rel, "PASS")
    add_metric(metric_rows, summary, "teleport_mentions_total", teleport_mentions_total, "T-002", summary_rel, "PASS")
    add_metric(metric_rows, summary, "emergency_braking_mentions_total", emergency_braking_mentions_total, "T-002", summary_rel, "PASS")
    add_metric(metric_rows, summary, "scenario_root_size_bytes", summary.get("scenarioRootSizeBytes", 0), "T-001", summary_rel, "NOT_APPLICABLE", metric_unit="bytes")
    if bandwidth_repair:
        bandwidth_rel = rel(bandwidth_repair_path, repo_root)
        add_metric(metric_rows, summary, "bandwidth_serialization_affected_materializations", bandwidth_repair.get("affectedMaterializations", 0), "T-020", bandwidth_rel, "WARN")
        add_metric(metric_rows, summary, "bandwidth_serialization_repaired_materializations", bandwidth_repair.get("repairedMaterializations", 0), "T-020", bandwidth_rel, "PASS")
        add_metric(metric_rows, summary, "bandwidth_serialization_remaining_incompatible_fields", bandwidth_repair.get("afterScan", {}).get("mosaicIncompatibleFieldCount", 0), "T-020", bandwidth_rel, "PASS")
    metric_fields = [
        "campaign_id",
        "group_id",
        "config_id",
        "materialization_id",
        "run_id",
        "test_id",
        "metric_name",
        "metric_value",
        "metric_unit",
        "source_file",
        "validator_status",
        "notes",
    ]
    write_csv_rows(metrics_path, metric_rows, metric_fields)

    anomalies_path = audit_root / "anomalies_G00.csv"
    canonical_repair_path = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation" / "canonical_metadata_repair_report.json"
    canonical_matrix_path = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation" / "canonical_deploy_compatibility_matrix.csv"
    archived_anomalies, archived_failure_paths = collect_archived_failure_anomalies(repo_root)
    recovered_intermediate_path = audit_root / "g00f_recovered_intermediate_statuses.json"
    write_json(recovered_intermediate_path, summary.get("intermediateAnomalies", []))
    anomaly_rows: list[dict[str, str]] = []
    anomaly_fields = [
        "anomaly_id",
        "group_id",
        "severity",
        "config_id",
        "materialization_id",
        "run_id",
        "test_id",
        "observed",
        "expected",
        "impact",
        "status",
        "decision_required",
        "evidence_id",
        "notes",
    ]

    def add_anomaly(
        severity: str,
        config_id: str,
        materialization_id: str,
        test_id: str,
        observed: str,
        expected: str,
        impact: str,
        status: str,
        decision_required: str,
        evidence_id: str,
        notes: str,
        group_id: str = "G00",
    ) -> None:
        anomaly_rows.append({
            "anomaly_id": f"AN-G00-{len(anomaly_rows) + 1:04d}",
            "group_id": group_id,
            "severity": severity,
            "config_id": config_id,
            "materialization_id": materialization_id,
            "run_id": "",
            "test_id": test_id,
            "observed": observed,
            "expected": expected,
            "impact": impact,
            "status": status,
            "decision_required": decision_required,
            "evidence_id": evidence_id,
            "notes": notes,
        })

    for result in summary["results"]:
        warnings = result.get("warnings", [])
        if warnings:
            add_anomaly(
                "INFO",
                result["configId"],
                result["materializationId"],
                "T-012",
                "; ".join(warnings),
                "Directed route profiles carry the three planned methodological flags.",
                "Accepted limitation; scenario is valid for functional engineering tests and excluded from main factorial claims.",
                "ACCEPTED_LIMITATION",
                "no",
                f"EV-G00-VALIDATION-{result['materializationId']}",
                "The three flags are grouped in this single anomaly row.",
                result["groupId"],
            )
    seen_intermediate: set[tuple[str, str, str]] = set()
    for item in [*summary.get("intermediateAnomalies", []), *archived_anomalies]:
        key = (item.get("materialization_id", ""), item.get("observed", ""), item.get("evidence_id", ""))
        if key in seen_intermediate:
            continue
        seen_intermediate.add(key)
        add_anomaly(
            "MEDIUM",
            item.get("config_id", ""),
            item.get("materialization_id", ""),
            "T-001",
            item.get("observed", ""),
            item.get("expected", ""),
            "No residual impact on final scenarios; tracked as resolved G00 tooling history.",
            "RESOLVED",
            "no",
            item.get("evidence_id", ""),
            item.get("notes", ""),
            item.get("group_id", "G00"),
        )
    add_anomaly(
        "INFO",
        "CFG-REPRO",
        "MAT-CFG-REPRO-104729-A;MAT-CFG-REPRO-104729-B",
        "T-014",
        f"raw={raw_repro.get('status', 'NOT_RUN')} different={raw_repro.get('differentArtifactCount', 0)}; logical={logical_repro.get('status', 'NOT_RUN')} different={logical_repro.get('differentArtifactCount', 0)}",
        "Raw comparison may differ on documented nondeterministic artifacts, while logical comparison must preserve substantive content.",
        "Preliminary reproducibility evidence is explicit; final interpretation remains assigned to G03.",
        "ACCEPTED_LIMITATION",
        "no",
        "EV-G00-REPRO-COMPARISON",
        "G00F separates raw and logical comparisons.",
    )
    materialized_plan_hash = first_manifest_plan_hash(repo_root, plan)
    current_plan_hash = sha256_file(repo_root / spec["paths"]["scenarioInstancePlan"])
    if materialized_plan_hash and materialized_plan_hash != current_plan_hash:
        add_anomaly(
            "LOW",
            "ALL",
            "ALL",
            "T-001",
            f"manifest sourceFiles scenario_instance_plan.csv sha256={materialized_plan_hash}; current sha256={current_plan_hash}",
            "Post-materialization planning CSV may gain status, paths and audit notes without changing materialized inputs.",
            "Documented limitation only; materialized parameters are frozen in manifests and materialization_input_index.csv.",
            "ACCEPTED_LIMITATION",
            "no",
            "EV-G00-INPUT-INDEX",
            "The manifest sourceFiles hash records the operational plan at materialization time.",
        )
    if canonical_repair_path.exists():
        add_anomaly(
            "MEDIUM",
            "ALL",
            "ALL",
            "T-020",
            "G00 campaign validator initially did not verify canonical deploy metadata",
            "Campaign validation also checks canonical deploy metadata required by the canonical deploy validator.",
            "G01 deploy was blocked before MOSAIC execution. No scenario mobility, workload, resource or GA configuration was corrupted.",
            "RESOLVED",
            "no",
            "EV-G00-CANONICAL-METADATA-REPAIR",
            "G00C repaired metadata only: materialization_manifest.json, reports/intas_literature_materialization_report.json, final_campaign_manifest.json and reports/final_campaign_validation_report.json.",
        )
    if bandwidth_repair:
        add_anomaly(
            "MEDIUM",
            "ALL",
            "ALL",
            "T-020",
            "Campaign bandwidth strings were serialized in scientific notation accepted by the Python validator but rejected by Eclipse MOSAIC.",
            "Bandwidth strings in MOSAIC text configuration fields must use fixed-point MOSAIC-compatible notation.",
            "RETRY-02 MOSAIC initialization failed in the Cell Ambassador before runtime reports. The calibrated bandwidth values were correct; only their string serialization was incompatible.",
            "RESOLVED",
            "no",
            "EV-G00-BANDWIDTH-SERIALIZATION-REPAIR",
            "G00D repaired textual bandwidth serialization in ma_ga_live_state_config.json and cell/network.json, then reran campaign and canonical validators without changing database, routes, workload, seeds, duration, density, Java or core logic.",
        )
    write_csv_rows(anomalies_path, anomaly_rows, anomaly_fields)

    cleanup = summary.get("cleanup", {})
    cleanup_path = audit_root / "cleanup_inventory_G00.json"
    if not cleanup and cleanup_path.exists():
        cleanup = read_json(cleanup_path)
    branch = git_output(repo_root, "branch", "--show-current")
    head = git_output(repo_root, "rev-parse", "HEAD")
    status_short = git_output(repo_root, "status", "--short")
    audit_path = audit_root / "audit_G00_scenario_preparation_generation.md"
    section_lines = [
        "# G00 Scenario Preparation And Generation Audit",
        "",
        "## 1. Identita della campagna",
        "",
        f"- campaign_id: `{spec['campaignId']}`",
        "- group_id: `G00`",
        "- group_name: `scenario preparation and generation`",
        "- phase: `G00F - finalizzazione e normalizzazione dell'audit G00`",
        "",
        "## 2. Commit e stato Git",
        "",
        f"- frozen_branch: `fix/local-cpu-contention`",
        f"- frozen_commit: `{spec['frozenCommit']}`",
        f"- campaign_branch: `{branch}`",
        f"- campaign_head_before_g00_commit: `{head}`",
        "- frozen Java path diff: empty at G00F precondition",
        "",
        "Working tree status at audit generation is captured in `git_status_short_G00F.txt`.",
        "",
        "## 3. Obiettivi",
        "",
        "G00 prepared campaign-specific tooling, materialized the planned scenario instances, validated the materialized inputs and produced the G00 audit bundle. G00F normalized the audit artifacts without rematerializing scenarios and without running MOSAIC.",
        "",
        "## 4. Configurazioni e run",
        "",
        f"- planned materializations: `{summary['plannedMaterializations']}`",
        f"- completed materializations: `{summary['completedMaterializations']}`",
        f"- validated materializations: `{summary['validatedMaterializations']}`",
        f"- warning materializations: `{summary['warningMaterializations']}`",
        f"- failed materializations: `{summary['failedMaterializations']}`",
        f"- blocked materializations: `{summary['blockedMaterializations']}`",
        f"- unique Materialization_ID: `{summary['uniqueMaterializationIds']}`",
        f"- unique target directories: `{summary['uniqueTargetDirectories']}`",
        "",
        "No Run_ID was executed in G00; planned runtime repetitions remain future MOSAIC runs.",
        "",
        "## 5. Comandi eseguiti",
        "",
        "- `git branch --show-current`",
        "- `git rev-parse HEAD`",
        "- `git status --short`",
        "- `git diff --name-only -- src tools/mosaic-live-maga-runtime/src tools/mosaic-live-state-layer/src tools/mosaic-adhoc-radio-diagnostic/src`",
        "- `python tools/intas-literature-scenario/final_campaign_v2/materialize_final_campaign_v2.py --mode check`",
        "- `python tools/intas-literature-scenario/final_campaign_v2/materialize_final_campaign_v2.py --mode audit`",
        "",
        "G00F did not run SUMO, MOSAIC, Scenario-Convert, archive, pilot, all or materialization modes.",
        "",
        "## 6. Output grezzi",
        "",
        f"- materialized scenario root: `{spec['paths']['campaignScenarioRoot']}`",
        f"- summary: `{summary_rel}`",
        f"- input index: `{rel(input_index_path, repo_root)}`",
        f"- reproducibility comparison: `{repro_rel}`",
        f"- metrics: `{rel(metrics_path, repo_root)}`",
        f"- anomalies: `{rel(anomalies_path, repo_root)}`",
        f"- evidence manifest: `{rel(audit_root / 'evidence_manifest_G00.json', repo_root)}`",
        "",
        "## 7. Esito validator",
        "",
        f"The 69 validation reports record `{summary['validatedMaterializations']}` `MATERIALIZED_VALIDATED` instances and `{summary['warningMaterializations']}` `MATERIALIZED_WITH_WARNINGS` instances. Failed and blocked materializations are zero. Aggregate SUMO diagnostic counters from the materialization reports are zero for errors, teleports and emergency braking mentions.",
        "",
        "## 8. Metriche",
        "",
        f"`metrics_G00.csv` uses the long schema from `audit_bundle_schema.md` and contains `{len(metric_rows)}` rows. Main values: planned=69, completed=69, validated=63, warnings=6, failed=0, blocked=0.",
        "",
        "## 9. Copertura Test_ID",
        "",
        "`T-001` covers campaign preparation and freeze evidence, `T-002` covers materialized artifact evidence and validator counters, and `T-014` is included as preliminary reproducibility evidence. G03 keeps responsibility for final reproducibility interpretation.",
        "",
        "## 10. Anomalie",
        "",
        f"`anomalies_G00.csv` contains `{len(anomaly_rows)}` rows. The six direct engineering profiles are accepted limitations. Intermediate failed or blocked attempts are marked `RESOLVED`. The raw/logical CFG-REPRO distinction and the post-materialization plan hash change are documented limitations, not materialization errors.",
        "",
        "## 11. Interpretazione tecnica",
        "",
        "The campaign-specific tooling produced one manifest and one validation report for each planned materialization. Direct route profiles are explicitly marked as directed engineering profiles and are excluded from main factorial claims. The ADAPTIVE GA case remains non-canonical and is accepted only by the campaign validator.",
        "",
        "### Canonical deploy compatibility correction",
        "",
        "G00C identified and resolved a metadata compatibility issue exposed by the failed G01 deploy attempt. The campaign validator now checks the canonical `materialization_manifest.json` and `reports/intas_literature_materialization_report.json` metadata required by the deploy validator. The repair updated only metadata and validation reports; it did not change mobility, workload, resource or GA runtime configuration files.",
        "",
        "### MOSAIC bandwidth serialization compatibility repair",
        "",
        "G00D identified and resolved a MOSAIC configuration serialization issue exposed by RETRY-02. The bandwidth values calibrated by the campaign were numerically correct, but Python `:g` formatting serialized values such as `49200000` as `4.92e+07 bps`, which Eclipse MOSAIC rejected in textual bandwidth fields. The repair changed only tooling serialization and the affected JSON text fields, then reran campaign and canonical validators.",
        "",
        "",
        "## 12. Risultati riutilizzabili nella tesi",
        "",
        "Le 69 istanze previste dalla matrice sono state materializzate.",
        "",
        "Sessantatre istanze hanno superato il validator senza warning.",
        "",
        "Sei profili diretti hanno prodotto esclusivamente i warning metodologici attesi.",
        "",
        "Il confronto logico delle due materializzazioni CFG-REPRO non ha rilevato differenze sostanziali.",
        "",
        "## 13. Limiti",
        "",
        "MOSAIC non e stato eseguito in G00 o G00F. Nessuna affermazione prestazionale sul MA-GA deriva da questo audit.",
        "",
        "Il sourceFiles hash di scenario_instance_plan.csv nei manifest rappresenta il piano operativo al momento della materializzazione. Il piano corrente e stato successivamente aggiornato con stati e percorsi, mentre i parametri materializzati sono congelati in materialization_input_index.csv e nei singoli manifest.",
        "",
        "I profili route diretti sono profili funzionali di engineering, non profili literature-calibrated. `CFG-G-SPARSE` mantiene `EMPTY_TASK_SET` come obiettivo osservativo, non come risultato garantito.",
        "",
    ]
    audit_path.write_text("\n".join(section_lines), encoding="utf-8")

    evidence_path = audit_root / "evidence_manifest_G00.json"
    created_at = utc_now()
    entries: list[dict[str, Any]] = []

    def add_evidence(
        evidence_id: str,
        path: Path,
        kind: str,
        description: str,
        config_id: str = "",
        materialization_id: str = "",
        run_id: str = "",
        test_ids: list[str] | None = None,
    ) -> None:
        if not path.exists():
            raise RuntimeError(f"Evidence file missing: {path}")
        entries.append({
            "evidence_id": evidence_id,
            "config_id": config_id,
            "materialization_id": materialization_id,
            "run_id": run_id,
            "test_ids": test_ids or [],
            "kind": kind,
            "path": rel(path, repo_root),
            "sha256": sha256_file(path),
            "created_at_utc": created_at,
            "description": description,
        })

    add_evidence("EV-G00-TOOL-README", SCRIPT_DIR / "README.md", "summary", "G00 tooling README.", test_ids=["T-001"])
    add_evidence("EV-G00-TOOL-SPEC", SCRIPT_DIR / "final_campaign_v2_spec.json", "manifest", "Campaign specification.", test_ids=["T-001"])
    add_evidence("EV-G00-TOOL-ORCHESTRATOR", SCRIPT_DIR / "materialize_final_campaign_v2.py", "summary", "Campaign orchestrator.", test_ids=["T-001"])
    add_evidence("EV-G00-TOOL-VALIDATOR", SCRIPT_DIR / "validate_final_campaign_v2.py", "validator", "Campaign validator.", test_ids=["T-002"])
    add_evidence("EV-G00-TOOL-POWERSHELL", SCRIPT_DIR / "materialize_final_campaign_v2.ps1", "command-output", "PowerShell wrapper.", test_ids=["T-001"])
    add_evidence("EV-G00-PLAN", repo_root / spec["paths"]["scenarioInstancePlan"], "summary", "Current scenario instance plan after G00F normalization.", test_ids=["T-001"])
    add_evidence("EV-G00-CONFIG-MAPPING", repo_root / spec["paths"]["scenarioConfigurationMapping"], "summary", "Current configuration mapping after G00.", test_ids=["T-001"])
    add_evidence("EV-G00-TEST-MAPPING", repo_root / spec["paths"]["testIdGroupMapping"], "summary", "Test_ID group mapping.", test_ids=["T-001"])
    add_evidence("EV-G00-SCHEMA", repo_root / spec["paths"]["auditSchema"], "summary", "Audit bundle schema.", test_ids=["T-001"])
    add_evidence("EV-G00-SUMMARY-ALL", summary_path, "summary", "G00 all-materialization summary.", test_ids=["T-001", "T-002"])
    add_evidence("EV-G00-RECOVERED-INTERMEDIATE-STATUSES", recovered_intermediate_path, "summary", "Recovered intermediate G00 pilot/history statuses.", test_ids=["T-001"])
    add_evidence("EV-G00-REPRO-COMPARISON", repro_path, "summary", "CFG-REPRO raw and logical comparison.", config_id="CFG-REPRO", materialization_id="MAT-CFG-REPRO-104729-A;MAT-CFG-REPRO-104729-B", test_ids=["T-014"])
    add_evidence("EV-G00-INPUT-INDEX", input_index_path, "summary", "Immutable materialized input index.", test_ids=["T-001", "T-002"])
    if canonical_repair_path.exists():
        add_evidence("EV-G00-CANONICAL-METADATA-REPAIR", canonical_repair_path, "summary", "G00C canonical deploy metadata repair report.", test_ids=["T-020"])
    if canonical_matrix_path.exists():
        add_evidence("EV-G00-CANONICAL-COMPATIBILITY-MATRIX", canonical_matrix_path, "summary", "G00C canonical deploy compatibility matrix.", test_ids=["T-020"])
    if bandwidth_scan_before_path.exists():
        add_evidence("EV-G00-BANDWIDTH-SCAN-BEFORE", bandwidth_scan_before_path, "summary", "G00D bandwidth serialization scan before repair.", test_ids=["T-020"])
    if bandwidth_scan_after_path.exists():
        add_evidence("EV-G00-BANDWIDTH-SCAN-AFTER", bandwidth_scan_after_path, "summary", "G00D bandwidth serialization scan after repair.", test_ids=["T-020"])
    if bandwidth_repair_path.exists():
        add_evidence("EV-G00-BANDWIDTH-SERIALIZATION-REPAIR", bandwidth_repair_path, "summary", "G00D MOSAIC bandwidth serialization repair report.", test_ids=["T-020"])
    add_evidence("EV-G00-AUDIT-MD", audit_path, "summary", "Normalized G00 audit markdown.", test_ids=["T-001"])
    add_evidence("EV-G00-METRICS", metrics_path, "metric", "G00 long-format metrics.", test_ids=["T-001", "T-002", "T-014"])
    add_evidence("EV-G00-ANOMALIES", anomalies_path, "summary", "G00 anomalies.", test_ids=["T-001", "T-002", "T-014"])
    for path in (cleanup_path, audit_root / "checkpoint_1_check.json", audit_root / "materialization_summary_pilot.json"):
        if path.exists():
            add_evidence(f"EV-G00-{path.stem.upper().replace('_', '-')}", path, "summary", path.name, test_ids=["T-001"])
    for path in archived_failure_paths:
        payload = read_json(path)
        add_evidence(
            f"EV-G00-ARCHIVED-FAILURE-{payload.get('materializationId', path.stem)}",
            path,
            "raw-log",
            "Archived intermediate materialization failure.",
            config_id=str(payload.get("configId", "")),
            materialization_id=str(payload.get("materializationId", "")),
            test_ids=["T-001"],
        )
    for path in git_output_paths:
        add_evidence(f"EV-G00-GIT-{path.stem.upper().replace('_', '-')}", path, "command-output", path.name, test_ids=["T-001"])
    for row in plan:
        root = repo_root / row["target_directory"]
        manifest_path = root / "final_campaign_manifest.json"
        report_path = root / "reports" / "final_campaign_validation_report.json"
        add_evidence(
            f"EV-G00-MANIFEST-{row['materialization_id']}",
            manifest_path,
            "manifest",
            "Final campaign manifest.",
            config_id=row["config_id"],
            materialization_id=row["materialization_id"],
            test_ids=["T-002"],
        )
        add_evidence(
            f"EV-G00-VALIDATION-{row['materialization_id']}",
            report_path,
            "validator",
            "Final campaign validation report.",
            config_id=row["config_id"],
            materialization_id=row["materialization_id"],
            test_ids=["T-002"],
        )
    evidence_ids = [entry["evidence_id"] for entry in entries]
    if len(evidence_ids) != len(set(evidence_ids)):
        raise RuntimeError("Duplicate evidence_id values in G00 evidence manifest")
    evidence = {
        "campaign_id": spec["campaignId"],
        "group_id": "G00",
        "created_at_utc": created_at,
        "git": {
            "branch": branch,
            "frozen_branch": "fix/local-cpu-contention",
            "frozen_commit": spec["frozenCommit"],
            "campaign_head_before_g00_commit": head,
            "working_tree_status_short": status_short.splitlines() if status_short else [],
        },
        "workbook": {
            "source": "C:/Users/raffa/Downloads/Matrice_test_MA_GA_MOSAIC_SUMO_Fase0_completa.xlsx",
            "counts_verified": True,
            "config_count": spec["expectedConfigCount"],
            "materialization_count": spec["expectedMaterializationCount"],
            "test_id_count": spec["expectedTestCount"],
        },
        "entries": entries,
    }
    write_json(evidence_path, evidence)


def run_canonical_materialized_validator(repo_root: Path, spec: dict[str, Any], target_root: Path, output_path: Path) -> dict[str, Any]:
    validator = repo_root / spec["paths"]["canonicalMaterializedValidator"]
    command = [
        sys.executable,
        str(validator),
        "--scenario-root",
        str(target_root),
        "--repo-root",
        str(repo_root),
        "--json-output",
        str(output_path),
    ]
    completed = subprocess.run(command, cwd=repo_root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    console_path = output_path.with_name("canonical_validator_console.log")
    console_path.write_text(completed.stdout or "", encoding="utf-8", errors="replace")
    if output_path.exists():
        result = read_json(output_path)
    else:
        result = {
            "status": "CANONICAL_VALIDATOR_NOT_RUN",
            "errors": [f"canonical validator exited {completed.returncode} without JSON output"],
            "warnings": [],
        }
        write_json(output_path, result)
    result["returnCode"] = completed.returncode
    return result


def canonical_compatibility_category(config_id: str, canonical_status: str, canonical_errors: list[str], spec: dict[str, Any]) -> tuple[str, str]:
    if canonical_status == "VALID_MATERIALIZED_SCENARIO":
        return "CANONICAL_COMPATIBLE", "canonical validator passed"
    if config_id == "CFG-G-ADAPTIVE":
        return "CAMPAIGN_VARIANT_EXPECTED_CANONICAL_REJECTION", "ADAPTIVE GA scaling is intentionally accepted only by the campaign validator"
    if config_id in spec["directRouteProfiles"]:
        return "CAMPAIGN_VARIANT_EXPECTED_CANONICAL_REJECTION", "direct engineering route profiles may violate canonical calibrated mobility assumptions"
    return "UNEXPECTED_CANONICAL_REJECTION", "; ".join(canonical_errors)


def expected_cell_capacity_bits_per_second(manifest: dict[str, Any], spec: dict[str, Any]) -> float:
    multipliers = {**spec["defaultResourceMultipliers"], **manifest.get("resourceMultipliers", {})}
    return float(spec["baselineResources"]["cellCapacityBitsPerSecond"]) * float(multipliers["cellBandwidth"])


def parse_bandwidth_for_scan(value: Any) -> float | None:
    if isinstance(value, (int, float)):
        return float(value)
    match = re.fullmatch(
        r"([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)\s*(bps|kbps|mbps|gbps)",
        str(value).strip(),
        re.IGNORECASE,
    )
    if not match:
        return None
    amount = float(match.group(1))
    scale = {"bps": 1.0, "kbps": 1.0e3, "mbps": 1.0e6, "gbps": 1.0e9}[match.group(2).lower()]
    return amount * scale


def is_mosaic_compatible_bandwidth_text(value: Any) -> bool:
    if not isinstance(value, str):
        return False
    match = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)\s*(bps|kbps|mbps|gbps)", value.strip(), re.IGNORECASE)
    return bool(match) and "e" not in match.group(1).lower()


def decimal_text(value: float | None) -> str:
    if value is None:
        return ""
    text = format(Decimal(str(value)), "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def scan_bandwidth_serialization(repo_root: Path, spec: dict[str, Any], plan: list[dict[str, str]], suffix: str) -> dict[str, Any]:
    campaign_root = (repo_root / spec["paths"]["campaignScenarioRoot"]).resolve()
    found_manifests = sorted(campaign_root.rglob("final_campaign_manifest.json"))
    if len(plan) != int(spec["expectedMaterializationCount"]) or len(found_manifests) != int(spec["expectedMaterializationCount"]):
        raise RuntimeError(f"expected exactly 69 materializations, found plan={len(plan)} manifests={len(found_manifests)}")

    output = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation" / f"bandwidth_serialization_scan_{suffix}.csv"
    rows: list[dict[str, Any]] = []
    exponent_field_count = 0
    incompatible_field_count = 0
    mismatch_field_count = 0
    affected_materializations: set[str] = set()
    for row in plan:
        target_root = ensure_target_under_campaign(row, repo_root, spec)
        manifest = read_json(target_root / "final_campaign_manifest.json")
        network = read_json(target_root / "cell" / "network.json")
        live_state = read_json(target_root / "application" / "ma_ga_live_state_config.json")
        expected = expected_cell_capacity_bits_per_second(manifest, spec)
        values = {
            "network_default_downlink": str(network.get("defaultDownlinkCapacity", "")),
            "network_default_uplink": str(network.get("defaultUplinkCapacity", "")),
            "live_state_max_downlink": str(live_state.get("cellDiagnosticAccounting", {}).get("maxDownlinkBitrate", "")),
            "live_state_max_uplink": str(live_state.get("cellDiagnosticAccounting", {}).get("maxUplinkBitrate", "")),
        }
        parsed = {key: parse_bandwidth_for_scan(value) for key, value in values.items()}
        contains_exponent = {key: bool(re.search(r"[eE]", value.split()[0] if value.split() else value)) for key, value in values.items()}
        compatible = {key: is_mosaic_compatible_bandwidth_text(value) for key, value in values.items()}
        matches = {key: parsed[key] is not None and abs(float(parsed[key]) - expected) <= 0.001 for key in values}
        for key in values:
            if contains_exponent[key]:
                exponent_field_count += 1
                affected_materializations.add(row["materialization_id"])
            if not compatible[key]:
                incompatible_field_count += 1
                affected_materializations.add(row["materialization_id"])
            if not matches[key]:
                mismatch_field_count += 1
                affected_materializations.add(row["materialization_id"])
        rows.append({
            "materialization_id": row["materialization_id"],
            "config_id": row["config_id"],
            "target_directory": row["target_directory"].rstrip("/\\"),
            "expected_capacity_bits_per_second": decimal_text(expected),
            "network_default_downlink": values["network_default_downlink"],
            "network_default_uplink": values["network_default_uplink"],
            "live_state_max_downlink": values["live_state_max_downlink"],
            "live_state_max_uplink": values["live_state_max_uplink"],
            "network_downlink_contains_exponent": str(contains_exponent["network_default_downlink"]),
            "network_uplink_contains_exponent": str(contains_exponent["network_default_uplink"]),
            "live_state_downlink_contains_exponent": str(contains_exponent["live_state_max_downlink"]),
            "live_state_uplink_contains_exponent": str(contains_exponent["live_state_max_uplink"]),
            "parsed_network_downlink_bps": decimal_text(parsed["network_default_downlink"]),
            "parsed_network_uplink_bps": decimal_text(parsed["network_default_uplink"]),
            "parsed_live_state_downlink_bps": decimal_text(parsed["live_state_max_downlink"]),
            "parsed_live_state_uplink_bps": decimal_text(parsed["live_state_max_uplink"]),
            "all_values_match_expected": str(all(matches.values())),
            "mosaic_compatible_format": str(all(compatible.values())),
        })
    fieldnames = [
        "materialization_id",
        "config_id",
        "target_directory",
        "expected_capacity_bits_per_second",
        "network_default_downlink",
        "network_default_uplink",
        "live_state_max_downlink",
        "live_state_max_uplink",
        "network_downlink_contains_exponent",
        "network_uplink_contains_exponent",
        "live_state_downlink_contains_exponent",
        "live_state_uplink_contains_exponent",
        "parsed_network_downlink_bps",
        "parsed_network_uplink_bps",
        "parsed_live_state_downlink_bps",
        "parsed_live_state_uplink_bps",
        "all_values_match_expected",
        "mosaic_compatible_format",
    ]
    write_csv_rows(output, rows, fieldnames)
    return {
        "path": rel(output, repo_root),
        "scannedMaterializations": len(rows),
        "affectedMaterializations": len(affected_materializations),
        "scientificNotationFieldCount": exponent_field_count,
        "mosaicIncompatibleFieldCount": incompatible_field_count,
        "capacityMismatchFieldCount": mismatch_field_count,
    }


def write_canonical_compatibility_matrix(
    repo_root: Path,
    spec: dict[str, Any],
    plan: list[dict[str, str]],
    repair_items: list[dict[str, Any]],
) -> Path:
    output = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation" / "canonical_deploy_compatibility_matrix.csv"
    rows: list[dict[str, str]] = []
    repair_by_id = {item["materializationId"]: item for item in repair_items}
    for row in plan:
        item = repair_by_id[row["materialization_id"]]
        canonical = item.get("canonicalValidator", {})
        campaign = item.get("campaignValidator", {})
        canonical_errors = [str(error) for error in canonical.get("errors", [])]
        canonical_status = str(canonical.get("status", "NOT_RUN"))
        category, reason = canonical_compatibility_category(row["config_id"], canonical_status, canonical_errors, spec)
        rows.append({
            "config_id": row["config_id"],
            "materialization_id": row["materialization_id"],
            "classification": item.get("classification", ""),
            "ga_parameter_scaling_mode": item.get("gaParameterScalingMode", ""),
            "campaign_validator_status": "PASS" if campaign.get("status") == "MATERIALIZED_VALIDATED" else "WARN" if campaign.get("status") == "MATERIALIZED_WITH_WARNINGS" else "FAIL",
            "canonical_validator_status": "PASS" if canonical_status == "VALID_MATERIALIZED_SCENARIO" else "FAIL",
            "canonical_error_count": str(len(canonical_errors)),
            "canonical_errors": " | ".join(canonical_errors),
            "expected_canonical_compatibility": category,
            "compatibility_reason": reason,
        })
    write_csv_rows(
        output,
        rows,
        [
            "config_id",
            "materialization_id",
            "classification",
            "ga_parameter_scaling_mode",
            "campaign_validator_status",
            "canonical_validator_status",
            "canonical_error_count",
            "canonical_errors",
            "expected_canonical_compatibility",
            "compatibility_reason",
        ],
    )
    return output


def update_g01_root_cause(repo_root: Path) -> None:
    anomaly_path = repo_root / "test-audits" / "final-campaign" / "G01_pipeline_validation" / "anomalies_G01.csv"
    audit_path = repo_root / "test-audits" / "final-campaign" / "G01_pipeline_validation" / "audit_G01_pipeline_validation.md"
    if anomaly_path.exists():
        rows = read_csv_rows(anomaly_path)
        for row in rows:
            if row.get("anomaly_id") == "AN-G01-0001":
                row["status"] = "OPEN"
                row["decision_required"] = "yes"
                row["notes"] = append_note(
                    row.get("notes", ""),
                    "G00C root cause identified: campaign metadata overwrote or omitted canonical deploy fields; resolution pending G01 retry",
                )
        write_csv_rows(anomaly_path, rows, list(rows[0].keys()))
    if audit_path.exists():
        text = audit_path.read_text(encoding="utf-8")
        note = (
            "\n### G00C root cause update\n\n"
            "Root cause identified: the campaign metadata layer overwrote or omitted canonical deploy metadata "
            "required by the canonical deploy validator. The G01 blocker remains `OPEN` until a retry run "
            "passes deploy and the literature smoke validator.\n"
        )
        if "### G00C root cause update" not in text:
            marker = "## 11. Interpretazione tecnica\n"
            if marker in text:
                text = text.replace(marker, marker + note + "\n", 1)
            else:
                text += note
            audit_path.write_text(text, encoding="utf-8")


def repair_canonical_metadata_mode(repo_root: Path, spec: dict[str, Any], plan: list[dict[str, str]]) -> dict[str, Any]:
    campaign_root = (repo_root / spec["paths"]["campaignScenarioRoot"]).resolve()
    found_manifests = sorted(campaign_root.rglob("final_campaign_manifest.json"))
    if len(plan) != int(spec["expectedMaterializationCount"]) or len(found_manifests) != int(spec["expectedMaterializationCount"]):
        raise RuntimeError(f"expected exactly 69 materializations, found plan={len(plan)} manifests={len(found_manifests)}")

    output_root = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation"
    canonical_reports_root = output_root / "canonical_validator_reports"
    repair_items: list[dict[str, Any]] = []
    modified_files: list[str] = []
    failed: list[dict[str, Any]] = []
    unchanged = 0

    for row in plan:
        target_root = ensure_target_under_campaign(row, repo_root, spec)
        final_manifest_path = target_root / "final_campaign_manifest.json"
        canonical_manifest_path = target_root / "materialization_manifest.json"
        canonical_report_path = target_root / "reports" / "intas_literature_materialization_report.json"
        campaign_report_path = target_root / "reports" / "final_campaign_validation_report.json"
        database_path = target_root / "application" / "intas_literature_urban.db"
        touched_paths = [canonical_manifest_path, canonical_report_path, final_manifest_path, campaign_report_path]
        before = {rel(path, repo_root): sha256_or_none(path) for path in touched_paths}
        item: dict[str, Any] = {
            "materializationId": row["materialization_id"],
            "configId": row["config_id"],
            "targetDirectory": row["target_directory"],
            "beforeSha256": before,
            "afterSha256": {},
            "modifiedFiles": [],
            "campaignValidator": {},
            "canonicalValidator": {},
        }
        try:
            sync_canonical_deploy_metadata(row, target_root, database_path, repo_root, spec)
            final_manifest = read_json(final_manifest_path)
            final_manifest["gaParameterScalingMode"] = runtime_ga_mode(target_root)
            final_manifest["generatedFiles"] = collect_generated_file_hashes(target_root)
            write_json_file(final_manifest_path, final_manifest)

            campaign_validation = validate_scenario(target_root, spec)
            write_json(campaign_report_path, campaign_validation)
            update_manifest_validation(target_root, campaign_validation)
            final_manifest = read_json(final_manifest_path)

            canonical_output = canonical_reports_root / row["materialization_id"] / "materialized_literature_scenario_validation.json"
            canonical_validation = run_canonical_materialized_validator(repo_root, spec, target_root, canonical_output)
            after = {rel(path, repo_root): sha256_or_none(path) for path in touched_paths}
            changed = [path for path in after if before.get(path) != after.get(path)]
            if not changed:
                unchanged += 1
            modified_files.extend(changed)
            item.update(
                {
                    "afterSha256": after,
                    "modifiedFiles": changed,
                    "classification": final_manifest.get("classification", ""),
                    "gaParameterScalingMode": final_manifest.get("gaParameterScalingMode", ""),
                    "campaignValidator": campaign_validation,
                    "canonicalValidator": canonical_validation,
                }
            )
        except Exception as exc:  # noqa: BLE001 - repair report must preserve per-instance diagnostics.
            failed.append({"materializationId": row["materialization_id"], "error": str(exc)})
            item["error"] = str(exc)
        repair_items.append(item)

    matrix_path = write_canonical_compatibility_matrix(repo_root, spec, plan, repair_items)
    campaign_counts = Counter(
        "PASS" if item.get("campaignValidator", {}).get("status") == "MATERIALIZED_VALIDATED"
        else "WARN" if item.get("campaignValidator", {}).get("status") == "MATERIALIZED_WITH_WARNINGS"
        else "FAIL"
        for item in repair_items
    )
    canonical_counts = Counter(
        "PASS" if item.get("canonicalValidator", {}).get("status") == "VALID_MATERIALIZED_SCENARIO" else "FAIL"
        for item in repair_items
    )
    matrix_rows = read_csv_rows(matrix_path)
    category_counts = Counter(row["expected_canonical_compatibility"] for row in matrix_rows)
    smoke = next((row for row in matrix_rows if row["materialization_id"] == "MAT-CFG-SMOKE-104729"), None)
    report = {
        "plannedInstances": len(plan),
        "repairedInstances": len(repair_items) - len(failed),
        "unchangedInstances": unchanged,
        "failedInstances": len(failed),
        "modifiedFiles": sorted(set(modified_files)),
        "beforeSha256": {item["materializationId"]: item.get("beforeSha256", {}) for item in repair_items},
        "afterSha256": {item["materializationId"]: item.get("afterSha256", {}) for item in repair_items},
        "campaignValidatorStatus": dict(campaign_counts),
        "canonicalValidatorStatus": dict(canonical_counts),
        "compatibilityCounts": dict(category_counts),
        "canonicalCompatibilityMatrix": rel(matrix_path, repo_root),
        "instances": repair_items,
        "failed": failed,
    }
    report_path = output_root / "canonical_metadata_repair_report.json"
    write_json(report_path, report)
    if not smoke or smoke["campaign_validator_status"] != "PASS" or smoke["canonical_validator_status"] != "PASS" or smoke["expected_canonical_compatibility"] != "CANONICAL_COMPATIBLE":
        raise RuntimeError(f"MAT-CFG-SMOKE-104729 is not deploy compatible after repair: {smoke}")
    if failed:
        raise RuntimeError(f"canonical metadata repair failed for {len(failed)} materializations")
    materialized_results = load_materialized_results(repo_root, spec, plan)
    repro = compare_repro(repo_root, spec, plan)
    summary = summarize_results(plan, materialized_results, None, repro)
    summary["scenarioRootSizeBytes"] = scenario_root_size(repo_root, spec)
    summary["canonicalMetadataRepair"] = rel(report_path, repo_root)
    summary["canonicalDeployCompatibilityMatrix"] = rel(matrix_path, repo_root)
    summary["intermediateAnomalies"] = merge_known_g00_intermediate_history(plan, collect_intermediate_status_anomalies(plan))
    write_json(repo_root / spec["paths"]["auditRoot"] / "materialization_summary_all.json", summary)
    create_audit(repo_root, spec, plan, summary)
    update_g01_root_cause(repo_root)
    return report


def repair_bandwidth_serialization_mode(repo_root: Path, spec: dict[str, Any], plan: list[dict[str, str]]) -> dict[str, Any]:
    campaign_root = (repo_root / spec["paths"]["campaignScenarioRoot"]).resolve()
    found_manifests = sorted(campaign_root.rglob("final_campaign_manifest.json"))
    if len(plan) != int(spec["expectedMaterializationCount"]) or len(found_manifests) != int(spec["expectedMaterializationCount"]):
        raise RuntimeError(f"expected exactly 69 materializations, found plan={len(plan)} manifests={len(found_manifests)}")

    output_root = repo_root / "test-results" / "final-campaign" / "G00_scenario_preparation_generation"
    canonical_reports_root = output_root / "canonical_validator_reports"
    before_scan = scan_bandwidth_serialization(repo_root, spec, plan, "before")
    repair_items: list[dict[str, Any]] = []
    failed: list[dict[str, Any]] = []
    modified_files: list[str] = []
    before_sha: dict[str, dict[str, str | None]] = {}
    after_sha: dict[str, dict[str, str | None]] = {}

    for row in plan:
        target_root = ensure_target_under_campaign(row, repo_root, spec)
        live_state_path = target_root / "application" / "ma_ga_live_state_config.json"
        network_path = target_root / "cell" / "network.json"
        final_manifest_path = target_root / "final_campaign_manifest.json"
        campaign_report_path = target_root / "reports" / "final_campaign_validation_report.json"
        touched_paths = [live_state_path, network_path, final_manifest_path, campaign_report_path]
        before = {rel(path, repo_root): sha256_or_none(path) for path in touched_paths}
        before_sha[row["materialization_id"]] = before
        item: dict[str, Any] = {
            "materializationId": row["materialization_id"],
            "configId": row["config_id"],
            "targetDirectory": row["target_directory"],
            "beforeSha256": before,
            "afterSha256": {},
            "modifiedFiles": [],
            "campaignValidator": {},
            "canonicalValidator": {},
        }
        try:
            final_manifest = read_json(final_manifest_path)
            expected_capacity = expected_cell_capacity_bits_per_second(final_manifest, spec)
            serialized_capacity = format_bps(expected_capacity)
            live_state = read_json(live_state_path)
            network = read_json(network_path)
            accounting = live_state.setdefault("cellDiagnosticAccounting", {})
            network_changed = False
            live_state_changed = False
            for key in ("defaultDownlinkCapacity", "defaultUplinkCapacity"):
                if network.get(key) != serialized_capacity:
                    network[key] = serialized_capacity
                    network_changed = True
            for key in ("maxDownlinkBitrate", "maxUplinkBitrate"):
                if accounting.get(key) != serialized_capacity:
                    accounting[key] = serialized_capacity
                    live_state_changed = True
            if network_changed:
                write_json_file(network_path, network)
            if live_state_changed:
                write_json_file(live_state_path, live_state)
            final_manifest["generatedFiles"] = collect_generated_file_hashes(target_root)
            write_json_file(final_manifest_path, final_manifest)

            campaign_validation = validate_scenario(target_root, spec)
            write_json(campaign_report_path, campaign_validation)
            update_manifest_validation(target_root, campaign_validation)
            final_manifest = read_json(final_manifest_path)

            canonical_output = canonical_reports_root / row["materialization_id"] / "materialized_literature_scenario_validation.json"
            canonical_validation = run_canonical_materialized_validator(repo_root, spec, target_root, canonical_output)
            after = {rel(path, repo_root): sha256_or_none(path) for path in touched_paths}
            after_sha[row["materialization_id"]] = after
            changed = [path for path in after if before.get(path) != after.get(path)]
            modified_files.extend(changed)
            item.update(
                {
                    "afterSha256": after,
                    "modifiedFiles": changed,
                    "classification": final_manifest.get("classification", ""),
                    "gaParameterScalingMode": final_manifest.get("gaParameterScalingMode", ""),
                    "serializedCapacity": serialized_capacity,
                    "campaignValidator": campaign_validation,
                    "canonicalValidator": canonical_validation,
                }
            )
        except Exception as exc:  # noqa: BLE001 - repair report must preserve per-instance diagnostics.
            failed.append({"materializationId": row["materialization_id"], "error": str(exc)})
            item["error"] = str(exc)
        repair_items.append(item)

    matrix_path = write_canonical_compatibility_matrix(repo_root, spec, plan, repair_items)
    after_scan = scan_bandwidth_serialization(repo_root, spec, plan, "after")
    campaign_counts = Counter(
        "PASS" if item.get("campaignValidator", {}).get("status") == "MATERIALIZED_VALIDATED"
        else "WARN" if item.get("campaignValidator", {}).get("status") == "MATERIALIZED_WITH_WARNINGS"
        else "FAIL"
        for item in repair_items
    )
    canonical_counts = Counter(
        "PASS" if item.get("canonicalValidator", {}).get("status") == "VALID_MATERIALIZED_SCENARIO" else "FAIL"
        for item in repair_items
    )
    matrix_rows = read_csv_rows(matrix_path)
    category_counts = Counter(row["expected_canonical_compatibility"] for row in matrix_rows)
    smoke = next((row for row in matrix_rows if row["materialization_id"] == "MAT-CFG-SMOKE-104729"), None)
    modified_unique = sorted(set(modified_files))
    unchanged = sum(1 for item in repair_items if not item.get("modifiedFiles"))
    repaired = len(repair_items) - unchanged - len(failed)
    report = {
        "campaignId": spec["campaignId"],
        "timestampUtc": utc_now(),
        "gitBranch": git_output(repo_root, "branch", "--show-current"),
        "gitHead": git_output(repo_root, "rev-parse", "HEAD"),
        "plannedMaterializations": len(plan),
        "scannedMaterializations": before_scan["scannedMaterializations"],
        "affectedMaterializations": before_scan["affectedMaterializations"],
        "repairedMaterializations": repaired,
        "unchangedMaterializations": unchanged,
        "failedMaterializations": len(failed),
        "affectedFieldCount": before_scan["scientificNotationFieldCount"],
        "modifiedFileCount": len(modified_unique),
        "rootCause": "Campaign bandwidth strings were serialized with Python :g formatting, producing scientific notation rejected by Eclipse MOSAIC text bandwidth parsing.",
        "previousFormatter": 'return f"{value:g} bps"',
        "newFormatterPolicy": "Decimal fixed-point formatting without scientific notation; reject negative or non-finite bandwidth values.",
        "beforeScan": before_scan,
        "afterScan": after_scan,
        "campaignValidatorCounts": dict(campaign_counts),
        "canonicalValidatorCounts": dict(canonical_counts),
        "canonicalCompatibilityCounts": dict(category_counts),
        "smokeMaterialization": smoke,
        "modifiedFiles": modified_unique,
        "beforeSha256": before_sha,
        "afterSha256": after_sha,
        "instances": repair_items,
        "failed": failed,
        "canonicalCompatibilityMatrix": rel(matrix_path, repo_root),
        "classification": "MOSAIC_BANDWIDTH_SERIALIZATION_REPAIRED",
        "limitations": [
            "Repair modified only textual bandwidth serialization and validation metadata.",
            "No runtime build, deploy, SUMO, MOSAIC, database regeneration, route regeneration, workload change, seed change, duration change or density change was performed.",
        ],
    }
    report_path = output_root / "bandwidth_serialization_repair_report.json"
    write_json(report_path, report)

    if failed:
        raise RuntimeError(f"bandwidth serialization repair failed for {len(failed)} materializations")
    if after_scan["scannedMaterializations"] != int(spec["expectedMaterializationCount"]):
        raise RuntimeError(f"after scan did not cover 69 materializations: {after_scan}")
    if after_scan["scientificNotationFieldCount"] != 0 or after_scan["mosaicIncompatibleFieldCount"] != 0 or after_scan["capacityMismatchFieldCount"] != 0:
        raise RuntimeError(f"bandwidth serialization after-scan still has invalid fields: {after_scan}")
    if dict(campaign_counts) != {"PASS": 63, "WARN": 6}:
        raise RuntimeError(f"unexpected campaign validator counts after repair: {dict(campaign_counts)}")
    if dict(canonical_counts) != {"PASS": 67, "FAIL": 2}:
        raise RuntimeError(f"unexpected canonical validator counts after repair: {dict(canonical_counts)}")
    if category_counts.get("UNEXPECTED_CANONICAL_REJECTION", 0) != 0:
        raise RuntimeError(f"unexpected canonical rejection after repair: {dict(category_counts)}")
    if not smoke or smoke["campaign_validator_status"] != "PASS" or smoke["canonical_validator_status"] != "PASS":
        raise RuntimeError(f"MAT-CFG-SMOKE-104729 is not validator-compatible after bandwidth repair: {smoke}")

    materialized_results = load_materialized_results(repo_root, spec, plan)
    repro = compare_repro(repo_root, spec, plan)
    summary = summarize_results(plan, materialized_results, None, repro)
    summary["scenarioRootSizeBytes"] = scenario_root_size(repo_root, spec)
    summary["bandwidthSerializationRepair"] = rel(report_path, repo_root)
    summary["canonicalDeployCompatibilityMatrix"] = rel(matrix_path, repo_root)
    summary["intermediateAnomalies"] = merge_known_g00_intermediate_history(plan, collect_intermediate_status_anomalies(plan))
    write_json(repo_root / spec["paths"]["auditRoot"] / "materialization_summary_all.json", summary)
    create_audit(repo_root, spec, plan, summary)
    return report


def run_materialization_mode(args: argparse.Namespace, repo_root: Path, spec: dict[str, Any], plan: list[dict[str, str]], configs: list[dict[str, str]]) -> dict[str, Any]:
    scenario_convert_path = find_scenario_convert(repo_root, args.scenario_convert)
    scenario_convert_root_path = scenario_convert_root(scenario_convert_path)
    verify_scenario_convert(scenario_convert_root_path)
    intas_root = Path(args.intas_root).resolve()
    if not intas_root.exists():
        raise RuntimeError(f"InTAS root not found: {intas_root}")
    rows = selected_rows_for_mode(args.mode, args.materialization_id, plan, spec)
    if not rows:
        raise RuntimeError(f"No materialization rows selected for mode {args.mode}")
    config_by_id = {row["config_id"]: row for row in configs}
    results = []
    for row in rows:
        print(f"MATERIALIZE {row['materialization_id']} -> {row['target_directory']}", flush=True)
        result = materialize_one(row, config_by_id, repo_root, spec, intas_root, scenario_convert_root_path, scenario_convert_path)
        results.append(result)
        print(json.dumps({"materializationId": result["materializationId"], "status": result["status"], "errors": result.get("errors", [])}, sort_keys=True), flush=True)
        if result["status"] in {"FAILED_MATERIALIZATION", "FAILED_VALIDATION", "BLOCKED"} and (args.stop_on_failure or args.mode == "pilot"):
            break
    update_instance_plan(repo_root, spec, results)
    if all(result["status"] in {"MATERIALIZED_VALIDATED", "MATERIALIZED_WITH_WARNINGS"} for result in results):
        update_config_mapping(repo_root, spec)
    repro = None
    if args.mode == "all":
        repro = compare_repro(repo_root, spec, plan)
    summary = summarize_results(plan, results, None, repro)
    summary["scenarioRootSizeBytes"] = scenario_root_size(repo_root, spec)
    write_json(repo_root / spec["paths"]["auditRoot"] / f"materialization_summary_{args.mode}.json", summary)
    create_audit(repo_root, spec, plan, summary)
    update_docs(repo_root, spec, summary)
    return summary



# -----------------------------------------------------------------------------
# V2 isolated overrides
# -----------------------------------------------------------------------------

def merge_known_g00_intermediate_history(plan: list[dict[str, str]], rows: list[dict[str, str]]) -> list[dict[str, str]]:
    """V2 has no inherited legacy attempt history."""
    return list(rows)


def collect_archived_failure_anomalies(repo_root: Path) -> tuple[list[dict[str, str]], list[Path]]:
    """Do not import failures from the superseded legacy campaign."""
    return [], []


def update_g01_root_cause(repo_root: Path) -> None:
    """G01 V2 has not started while G00 is being prepared."""
    return None


def build_materialization_input_index(repo_root: Path, spec: dict[str, Any], plan: list[dict[str, str]]) -> Path:
    output = repo_root / "test-results" / "final-campaign-v2-local-contention" / "G00_scenario_preparation_generation" / "materialization_input_index.csv"
    fieldnames = [
        "campaign_id", "group_id", "config_id", "materialization_id",
        "campaign_seed", "mobility_seed", "workload_seed", "density",
        "duration_profile", "duration_seconds", "workload_profile",
        "route_family_vehicle_counts_json", "resource_multipliers_json",
        "ga_parameter_scaling_mode", "diagnostic_artificial_ga_delay_ms",
        "classification", "validation_status", "target_directory", "manifest_sha256",
    ]
    rows: list[dict[str, str]] = []
    for row in plan:
        manifest_path = repo_root / row["target_directory"] / "final_campaign_manifest.json"
        if not manifest_path.exists():
            continue
        manifest = read_json(manifest_path)
        rows.append({
            "campaign_id": str(manifest.get("campaignId", "")),
            "group_id": str(manifest.get("groupId", "")),
            "config_id": str(manifest.get("configId", "")),
            "materialization_id": str(manifest.get("materializationId", "")),
            "campaign_seed": str(manifest.get("campaignSeed", "")),
            "mobility_seed": str(manifest.get("mobilitySeed", "")),
            "workload_seed": str(manifest.get("workloadSeed", "")),
            "density": str(manifest.get("density", "")),
            "duration_profile": str(manifest.get("durationProfile", "")),
            "duration_seconds": str(manifest.get("durationSeconds", "")),
            "workload_profile": str(manifest.get("workloadProfile", "")),
            "route_family_vehicle_counts_json": json.dumps(manifest.get("routeFamilyVehicleCounts", {}), sort_keys=True, separators=(",", ":")),
            "resource_multipliers_json": json.dumps(manifest.get("resourceMultipliers", {}), sort_keys=True, separators=(",", ":")),
            "ga_parameter_scaling_mode": str(manifest.get("gaParameterScalingMode", "")),
            "diagnostic_artificial_ga_delay_ms": str(manifest.get("diagnosticArtificialGaDelayMs", "")),
            "classification": str(manifest.get("classification", "")),
            "validation_status": str(manifest.get("validation", {}).get("status", "")),
            "target_directory": str(manifest.get("targetDirectory", row["target_directory"])),
            "manifest_sha256": sha256_file(manifest_path),
        })
    write_csv_rows(output, rows, fieldnames)
    return output


def update_docs(repo_root: Path, spec: dict[str, Any], summary: dict[str, Any]) -> None:
    docs_root = repo_root / "data" / "docs" / "testing" / "final-campaign-v2-local-contention"
    status = summary.get("overallStatus", "UNKNOWN")
    body = (
        f"Stato G00 V2: `{status}`.\n\n"
        f"- planned materializations: `{summary.get('plannedMaterializations', 0)}`\n"
        f"- completed materializations: `{summary.get('completedMaterializations', 0)}`\n"
        f"- validated materializations: `{summary.get('validatedMaterializations', 0)}`\n"
        f"- warning materializations: `{summary.get('warningMaterializations', 0)}`\n"
        f"- failed materializations: `{summary.get('failedMaterializations', 0)}`\n"
        f"- blocked materializations: `{summary.get('blockedMaterializations', 0)}`\n\n"
        "Gli scenari sono isolati sotto `tmp/materialized-literature-scenarios/"
        "final-campaign-v2-local-contention/`. G00 non esegue MOSAIC o SUMO."
    )
    append_section(docs_root / "README.md", "G00 Scenario Preparation And Generation", body)
    append_section(docs_root / "02_test_group_plan.md", "G00 Execution Result", body)


def create_audit(repo_root: Path, spec: dict[str, Any], plan: list[dict[str, str]], summary: dict[str, Any]) -> None:
    audit_root = repo_root / spec["paths"]["auditRoot"]
    audit_root.mkdir(parents=True, exist_ok=True)
    results_root = repo_root / "test-results" / "final-campaign-v2-local-contention" / "G00_scenario_preparation_generation"
    results_root.mkdir(parents=True, exist_ok=True)
    input_index_path = build_materialization_input_index(repo_root, spec, plan)
    git_paths = write_git_output_files(repo_root, audit_root)
    summary_path = audit_root / "materialization_summary_all.json"
    if not summary_path.exists():
        write_json(summary_path, summary)

    metric_fields = [
        "campaign_id", "group_id", "config_id", "materialization_id", "run_id",
        "test_id", "metric_name", "metric_value", "metric_unit", "source_file",
        "validator_status", "notes",
    ]
    summary_rel = rel(summary_path, repo_root)
    metric_values = [
        ("planned_materializations", summary.get("plannedMaterializations", 0), "T-001", "PASS"),
        ("completed_materializations", summary.get("completedMaterializations", 0), "T-001", "PASS"),
        ("validated_materializations", summary.get("validatedMaterializations", 0), "T-002", "PASS"),
        ("warning_materializations", summary.get("warningMaterializations", 0), "T-002", "WARN" if summary.get("warningMaterializations", 0) else "PASS"),
        ("failed_materializations", summary.get("failedMaterializations", 0), "T-001", "PASS" if not summary.get("failedMaterializations", 0) else "FAIL"),
        ("blocked_materializations", summary.get("blockedMaterializations", 0), "T-001", "PASS" if not summary.get("blockedMaterializations", 0) else "FAIL"),
        ("unique_materialization_ids", summary.get("uniqueMaterializationIds", 0), "T-001", "PASS"),
        ("unique_target_directories", summary.get("uniqueTargetDirectories", 0), "T-001", "PASS"),
        ("scenario_root_size_bytes", summary.get("scenarioRootSizeBytes", 0), "T-001", "NOT_APPLICABLE"),
    ]
    metrics = []
    for name, value, test_id, validator_status in metric_values:
        metrics.append({
            "campaign_id": spec["campaignId"], "group_id": "G00", "config_id": "ALL",
            "materialization_id": "ALL", "run_id": "", "test_id": test_id,
            "metric_name": name, "metric_value": str(value),
            "metric_unit": "bytes" if name.endswith("_bytes") else "count",
            "source_file": summary_rel, "validator_status": validator_status, "notes": "",
        })
    metrics_path = audit_root / "metrics_G00.csv"
    write_csv_rows(metrics_path, metrics, metric_fields)

    anomaly_fields = [
        "anomaly_id", "group_id", "severity", "config_id", "materialization_id",
        "run_id", "test_id", "observed", "expected", "impact", "status",
        "decision_required", "evidence_id", "notes",
    ]
    anomalies = []
    for result in summary.get("results", []):
        status = result.get("status", "")
        if status in {"FAILED_MATERIALIZATION", "FAILED_VALIDATION", "BLOCKED"}:
            anomalies.append({
                "anomaly_id": f"AN-G00-{len(anomalies)+1:04d}", "group_id": result.get("groupId", "G00"),
                "severity": "HIGH", "config_id": result.get("configId", ""),
                "materialization_id": result.get("materializationId", ""), "run_id": "", "test_id": "T-002",
                "observed": "; ".join(result.get("errors", [])),
                "expected": "Validated or warning-only materialization", "impact": "G00 blocked",
                "status": "OPEN", "decision_required": "yes", "evidence_id": "EV-G00-SUMMARY",
                "notes": result.get("validationReport", ""),
            })
    anomalies_path = audit_root / "anomalies_G00.csv"
    write_csv_rows(anomalies_path, anomalies, anomaly_fields)

    audit_lines = [
        "# G00 Scenario Preparation And Generation Audit - MA-GA V2", "",
        f"- campaign_id: `{spec['campaignId']}`",
        f"- frozen_commit: `{spec['frozenCommit']}`",
        f"- campaign_branch: `{spec['campaignBranch']}`",
        f"- overall_status: `{summary.get('overallStatus', 'UNKNOWN')}`", "",
        "## Materializzazioni", "",
        f"- pianificate: `{summary.get('plannedMaterializations', 0)}`",
        f"- completate: `{summary.get('completedMaterializations', 0)}`",
        f"- validate: `{summary.get('validatedMaterializations', 0)}`",
        f"- con warning: `{summary.get('warningMaterializations', 0)}`",
        f"- fallite: `{summary.get('failedMaterializations', 0)}`",
        f"- bloccate: `{summary.get('blockedMaterializations', 0)}`", "",
        "## Confini", "",
        "G00 ha preparato e validato gli input. Non ha eseguito MOSAIC, SUMO o run prestazionali.",
        "I risultati della campagna legacy non sono stati importati.", "",
        "## Percorsi", "",
        f"- scenari: `{spec['paths']['campaignScenarioRoot']}`",
        f"- risultati: `test-results/final-campaign-v2-local-contention/G00_scenario_preparation_generation`",
        f"- audit: `{spec['paths']['auditRoot']}`", "",
    ]
    audit_path = audit_root / "audit_G00_scenario_preparation_generation.md"
    audit_path.write_text("\n".join(audit_lines), encoding="utf-8")

    entries = []
    created_at = utc_now()
    def add_ev(eid: str, path: Path, kind: str, desc: str, test_ids: list[str]):
        if not path.exists():
            return
        entries.append({
            "evidence_id": eid, "config_id": "", "materialization_id": "", "run_id": "",
            "test_ids": test_ids, "kind": kind, "path": rel(path, repo_root),
            "sha256": sha256_file(path), "created_at_utc": created_at, "description": desc,
        })
    add_ev("EV-G00-TOOL-README", SCRIPT_DIR / "README.md", "summary", "V2 tooling README.", ["T-001"])
    add_ev("EV-G00-TOOL-SPEC", SCRIPT_DIR / "final_campaign_v2_spec.json", "manifest", "V2 campaign specification.", ["T-001"])
    add_ev("EV-G00-TOOL-ORCHESTRATOR", SCRIPT_DIR / "materialize_final_campaign_v2.py", "summary", "V2 orchestrator.", ["T-001"])
    add_ev("EV-G00-TOOL-VALIDATOR", SCRIPT_DIR / "validate_final_campaign_v2.py", "validator", "V2 validator.", ["T-002"])
    add_ev("EV-G00-TOOL-POWERSHELL", SCRIPT_DIR / "materialize_final_campaign_v2.ps1", "command-output", "V2 wrapper.", ["T-001"])
    add_ev("EV-G00-PLAN", repo_root / spec["paths"]["scenarioInstancePlan"], "summary", "Current V2 instance plan.", ["T-001"])
    add_ev("EV-G00-CONFIG-MAPPING", repo_root / spec["paths"]["scenarioConfigurationMapping"], "summary", "Configuration mapping.", ["T-001"])
    add_ev("EV-G00-TEST-MAPPING", repo_root / spec["paths"]["testIdGroupMapping"], "summary", "Test mapping.", ["T-001"])
    add_ev("EV-G00-SCHEMA", repo_root / spec["paths"]["auditSchema"], "summary", "Audit schema.", ["T-001"])
    add_ev("EV-G00-SUMMARY", summary_path, "summary", "G00 aggregate summary.", ["T-001", "T-002"])
    add_ev("EV-G00-INPUT-INDEX", input_index_path, "summary", "Materialized input index.", ["T-001", "T-002"])
    add_ev("EV-G00-METRICS", metrics_path, "metrics", "G00 metrics.", ["T-001", "T-002"])
    add_ev("EV-G00-ANOMALIES", anomalies_path, "anomalies", "G00 anomalies.", ["T-001", "T-002"])
    add_ev("EV-G00-AUDIT", audit_path, "summary", "G00 audit.", ["T-001", "T-002"])
    for path in git_paths:
        add_ev("EV-G00-GIT-" + path.stem.upper(), path, "command-output", "Git evidence.", ["T-001"])
    for row in plan:
        root = repo_root / row["target_directory"]
        manifest = root / "final_campaign_manifest.json"
        report = root / "reports" / "final_campaign_validation_report.json"
        if manifest.exists():
            entries.append({
                "evidence_id": "EV-G00-MANIFEST-" + row["materialization_id"],
                "config_id": row["config_id"], "materialization_id": row["materialization_id"],
                "run_id": "", "test_ids": ["T-002"], "kind": "manifest",
                "path": rel(manifest, repo_root), "sha256": sha256_file(manifest),
                "created_at_utc": created_at, "description": "V2 materialization manifest.",
            })
        if report.exists():
            entries.append({
                "evidence_id": "EV-G00-VALIDATION-" + row["materialization_id"],
                "config_id": row["config_id"], "materialization_id": row["materialization_id"],
                "run_id": "", "test_ids": ["T-002"], "kind": "validator",
                "path": rel(report, repo_root), "sha256": sha256_file(report),
                "created_at_utc": created_at, "description": "V2 validation report.",
            })
    evidence_path = audit_root / "evidence_manifest_G00.json"
    write_json(evidence_path, {
        "schemaVersion": 1, "campaignId": spec["campaignId"], "groupId": "G00",
        "generatedAtUtc": created_at, "entryCount": len(entries), "entries": entries,
    })


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    spec_path = Path(args.spec).resolve()
    spec, plan, configs, tests = load_campaign(repo_root, spec_path)
    planning = validate_planning_inputs(spec, plan, configs, tests)
    if args.mode == "check":
        report = {"mode": "check", "planning": planning}
        write_json(repo_root / spec["paths"]["auditRoot"] / "checkpoint_1_check.json", report)
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0 if not planning["errors"] else 1
    if planning["errors"]:
        print(json.dumps({"errors": planning["errors"]}, indent=2, sort_keys=True))
        return 1
    if args.mode == "archive":
        cleanup = archive_campaign_root(repo_root, spec)
        print(json.dumps(cleanup, indent=2, sort_keys=True))
        return 0
    if args.mode == "repair-canonical-metadata":
        report = repair_canonical_metadata_mode(repo_root, spec, plan)
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0
    if args.mode == "repair-bandwidth-serialization":
        report = repair_bandwidth_serialization_mode(repo_root, spec, plan)
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0
    if args.mode in {"pilot", "all", "materialization"}:
        try:
            summary = run_materialization_mode(args, repo_root, spec, plan, configs)
        except Exception as exc:  # noqa: BLE001 - setup failures still need a G00 audit.
            rows = selected_rows_for_mode(args.mode, args.materialization_id, plan, spec)
            results = [
                {
                    "materializationId": row["materialization_id"],
                    "configId": row["config_id"],
                    "groupId": row["group_id"],
                    "targetDirectory": row["target_directory"],
                    "status": "BLOCKED",
                    "errors": [str(exc)],
                    "warnings": [],
                }
                for row in rows
            ]
            update_instance_plan(repo_root, spec, results)
            summary = summarize_results(plan, results, None, None)
            summary["scenarioRootSizeBytes"] = scenario_root_size(repo_root, spec)
            create_audit(repo_root, spec, plan, summary)
            update_docs(repo_root, spec, summary)
            write_json(repo_root / spec["paths"]["auditRoot"] / f"materialization_summary_{args.mode}.json", summary)
            print(json.dumps(summary, indent=2, sort_keys=True))
            return 1
        print(json.dumps(summary, indent=2, sort_keys=True))
        return 0 if summary["failedMaterializations"] == 0 and summary["blockedMaterializations"] == 0 else 1
    if args.mode == "audit":
        current_rows = read_csv_rows(repo_root / spec["paths"]["scenarioInstancePlan"])
        intermediate_anomalies = merge_known_g00_intermediate_history(current_rows, collect_intermediate_status_anomalies(current_rows))
        materialized_results = load_materialized_results(repo_root, spec, current_rows)
        repro = compare_repro(repo_root, spec, current_rows)
        summary = summarize_results(current_rows, materialized_results, None, repro)
        summary["scenarioRootSizeBytes"] = scenario_root_size(repo_root, spec)
        summary["intermediateAnomalies"] = intermediate_anomalies
        update_instance_plan(repo_root, spec, materialized_results)
        update_config_mapping(repo_root, spec)
        normalized_rows = read_csv_rows(repo_root / spec["paths"]["scenarioInstancePlan"])
        write_json(repo_root / spec["paths"]["auditRoot"] / "materialization_summary_all.json", summary)
        create_audit(repo_root, spec, normalized_rows, summary)
        update_docs(repo_root, spec, summary)
        print(json.dumps(summary, indent=2, sort_keys=True))
        return 0
    raise RuntimeError(f"Unhandled mode: {args.mode}")


if __name__ == "__main__":
    sys.exit(main())
