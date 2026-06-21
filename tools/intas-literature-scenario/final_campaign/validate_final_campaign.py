#!/usr/bin/env python3
"""Campaign-specific validator for one final-test-campaign scenario instance."""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
import sys
import xml.etree.ElementTree as ET
from hashlib import sha256 as _sha256
from pathlib import Path
from typing import Any


ROUTE_FAMILIES = (
    "DUAL_RSU_SWITCH",
    "BOTH_RSU_NO_SWITCH",
    "RSU_0_ONLY",
    "RSU_1_ONLY",
    "BACKGROUND",
)

TEMPLATE_TOKENS = ("${", "{{", "}}", "__TEMPLATE__", "__TODO__")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario-root", required=True, help="Materialized scenario instance root.")
    parser.add_argument("--spec", default=str(Path(__file__).with_name("final_campaign_spec.json")))
    parser.add_argument("--json-output", help="Optional path for the validation report JSON.")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def sha256_file(path: Path) -> str:
    digest = _sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def almost_equal(actual: Any, expected: Any, rel: float = 1.0e-9, abs_tol: float = 1.0e-6) -> bool:
    try:
        actual_f = float(actual)
        expected_f = float(expected)
    except (TypeError, ValueError):
        return actual == expected
    return abs(actual_f - expected_f) <= max(abs_tol, rel * max(abs(actual_f), abs(expected_f), 1.0))


def strip_ns(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def parse_seconds(value: Any) -> float:
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip()
    if text.endswith("ms"):
        return float(text[:-2].strip()) / 1000.0
    if text.endswith("s"):
        return float(text[:-1].strip())
    return float(text)


def parse_capacity(value: Any) -> float:
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip().lower()
    match = re.fullmatch(r"([0-9.]+)\s*([kmg]?bps)", text)
    if not match:
        return float(text)
    amount = float(match.group(1))
    scale = {"bps": 1.0, "kbps": 1.0e3, "mbps": 1.0e6, "gbps": 1.0e9}[match.group(2)]
    return amount * scale


def check_equal(errors: list[str], label: str, actual: Any, expected: Any, rel: float = 1.0e-9, abs_tol: float = 1.0e-6) -> None:
    if not almost_equal(actual, expected, rel=rel, abs_tol=abs_tol):
        errors.append(f"{label}: expected {expected!r}, found {actual!r}")


def collect_json_parse_errors(root: Path) -> list[str]:
    errors: list[str] = []
    for path in sorted(root.rglob("*.json")):
        try:
            read_json(path)
        except Exception as exc:  # noqa: BLE001 - validator reports all parse failures.
            errors.append(f"JSON parse failure in {path.relative_to(root)}: {exc}")
    return errors


def collect_template_tokens(root: Path) -> list[str]:
    hits: list[str] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in {".json", ".xml", ".sumocfg", ".properties", ".conf"}:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for token in TEMPLATE_TOKENS:
            if token in text:
                hits.append(f"{path.relative_to(root)} contains residual template token {token!r}")
                break
    return hits


def read_route_vehicle_ids(route_xml: Path) -> list[str]:
    root = ET.parse(route_xml).getroot()
    ids: list[str] = []
    for node in root.iter():
        if strip_ns(node.tag) == "vehicle":
            ids.append(node.attrib.get("id", ""))
    return ids


def sqlite_readable(path: Path) -> tuple[bool, str | None]:
    if not path.exists():
        return False, "database file is missing"
    try:
        with sqlite3.connect(path) as connection:
            connection.execute("select name from sqlite_master limit 1").fetchall()
        return True, None
    except sqlite3.Error as exc:
        return False, str(exc)


def workload_weights(config: dict[str, Any]) -> dict[str, float]:
    profiles = config.get("workloadGeneration", {}).get("profiles", [])
    result: dict[str, float] = {}
    for profile in profiles:
        key = str(profile.get("profileId", "")).upper()
        if key:
            result[key] = float(profile.get("weight", 0.0))
    return result


def validate_workload(
    errors: list[str],
    live_state: dict[str, Any],
    manifest: dict[str, Any],
    spec: dict[str, Any],
) -> None:
    workload_id = manifest["workloadProfile"]
    expected = spec["workloadProfiles"][workload_id]
    generation = live_state.get("workloadGeneration", {})
    check_equal(errors, "workloadGeneration.randomSeed", generation.get("randomSeed"), manifest["workloadSeed"], abs_tol=0)
    check_equal(
        errors,
        "workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle",
        generation.get("arrivalRateTasksPerSecondPerActiveVehicle"),
        expected["arrivalRateTasksPerSecondPerActiveVehicle"],
    )
    weights = workload_weights(live_state)
    expected_weights = {"LIGHT": expected["LIGHT"], "MEDIUM": expected["MEDIUM"], "HEAVY": expected["HEAVY"]}
    for key, value in expected_weights.items():
        check_equal(errors, f"workload weight {key}", weights.get(key), value)
    check_equal(errors, "workload weights sum", sum(weights.get(key, 0.0) for key in expected_weights), 1.0)
    if workload_id == "WL-SMOKE":
        check_equal(errors, "WL-SMOKE maxGeneratedTasksPerTickPerVehicle", generation.get("maxGeneratedTasksPerTickPerVehicle"), 1, abs_tol=0)


def expected_resource_values(manifest: dict[str, Any], spec: dict[str, Any]) -> dict[str, float]:
    baseline = spec["baselineResources"]
    multipliers = {**spec["defaultResourceMultipliers"], **manifest.get("resourceMultipliers", {})}
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


def validate_resources(
    errors: list[str],
    live_state: dict[str, Any],
    runtime: dict[str, Any],
    sns: dict[str, Any],
    network: dict[str, Any],
    regions: dict[str, Any],
    manifest: dict[str, Any],
    spec: dict[str, Any],
) -> None:
    expected = expected_resource_values(manifest, spec)
    check_equal(errors, "localCpuCyclesPerSecond", live_state.get("localCpuCyclesPerSecond"), expected["localCpuCyclesPerSecond"])
    check_equal(errors, "remoteVehicleCpuCyclesPerSecond", live_state.get("remoteVehicleCpuCyclesPerSecond"), expected["remoteVehicleCpuCyclesPerSecond"])
    for index, node in enumerate(live_state.get("staticInfrastructure", {}).get("edgeNodes", [])):
        check_equal(errors, f"edgeNodes[{index}].availableCpuCyclesPerSecond", node.get("availableCpuCyclesPerSecond"), expected["edgeCpuCyclesPerSecond"])
    for index, node in enumerate(live_state.get("staticInfrastructure", {}).get("cloudNodes", [])):
        check_equal(errors, f"cloudNodes[{index}].availableCpuCyclesPerSecond", node.get("availableCpuCyclesPerSecond"), expected["cloudCpuCyclesPerSecond"])

    profile = live_state.get("configuredCellProfile", {})
    check_equal(errors, "configuredCellProfile.capacityBitsPerSecond", profile.get("capacityBitsPerSecond"), expected["cellCapacityBitsPerSecond"])
    check_equal(errors, "configuredCellProfile.measuredRttSeconds", profile.get("measuredRttSeconds"), expected["cellRttSeconds"])
    check_equal(errors, "configuredCellProfile.symmetricOneWayDelaySeconds", profile.get("symmetricOneWayDelaySeconds"), expected["cellOneWayDelaySeconds"])
    for index, pool in enumerate(live_state.get("cellDiagnosticAccounting", {}).get("gatewayPools", [])):
        check_equal(errors, f"gatewayPools[{index}].nominalCapacityBitsPerSecond", pool.get("nominalCapacityBitsPerSecond"), expected["cellCapacityBitsPerSecond"])

    global_network = network.get("globalNetwork", {})
    check_equal(errors, "network.globalNetwork.uplink.capacity", parse_capacity(global_network.get("uplink", {}).get("capacity")), expected["cellCapacityBitsPerSecond"])
    check_equal(errors, "network.globalNetwork.downlink.capacity", parse_capacity(global_network.get("downlink", {}).get("capacity")), expected["cellCapacityBitsPerSecond"])
    for label, node in (
        ("network.globalNetwork.uplink.delay", global_network.get("uplink", {}).get("delay", {})),
        ("network.globalNetwork.downlink.unicast.delay", global_network.get("downlink", {}).get("unicast", {}).get("delay", {})),
        ("network.globalNetwork.downlink.multicast.delay", global_network.get("downlink", {}).get("multicast", {}).get("delay", {})),
    ):
        check_equal(errors, label, parse_seconds(node.get("delay")), expected["cellOneWayDelaySeconds"])

    for index, region in enumerate(regions.get("regions", [])):
        check_equal(errors, f"regions[{index}].uplink.capacity", parse_capacity(region.get("uplink", {}).get("capacity")), expected["cellCapacityBitsPerSecond"])
        check_equal(errors, f"regions[{index}].downlink.capacity", parse_capacity(region.get("downlink", {}).get("capacity")), expected["cellCapacityBitsPerSecond"])
        for label, node in (
            (f"regions[{index}].uplink.delay", region.get("uplink", {}).get("delay", {})),
            (f"regions[{index}].downlink.unicast.delay", region.get("downlink", {}).get("unicast", {}).get("delay", {})),
            (f"regions[{index}].downlink.multicast.delay", region.get("downlink", {}).get("multicast", {}).get("delay", {})),
        ):
            check_equal(errors, label, parse_seconds(node.get("delay")), expected["cellOneWayDelaySeconds"])

    check_equal(errors, "v2vNominalBandwidthBitsPerSecond", live_state.get("v2vNominalBandwidthBitsPerSecond"), expected["v2vNominalBandwidthBitsPerSecond"])
    check_equal(errors, "ma_ga_live_state_config.singlehopRadiusMeters", live_state.get("singlehopRadiusMeters"), expected["v2vSinglehopRadiusMeters"])
    check_equal(errors, "sns_config.singlehopRadius", sns.get("singlehopRadius"), expected["v2vSinglehopRadiusMeters"])

    expected_mode = manifest.get("gaParameterScalingMode", "STATIC")
    actual_mode = runtime.get("gaParameterScalingMode")
    check_equal(errors, "gaParameterScalingMode", actual_mode, expected_mode)
    if actual_mode == "ADAPTIVE" and manifest.get("configId") != "CFG-G-ADAPTIVE":
        errors.append("ADAPTIVE gaParameterScalingMode is only allowed for CFG-G-ADAPTIVE")
    check_equal(errors, "diagnosticArtificialGaDelayMs", runtime.get("diagnosticArtificialGaDelayMs"), manifest.get("diagnosticArtificialGaDelayMs", 0), abs_tol=0)


def validate_canonical_deploy_metadata(
    errors: list[str],
    manifest: dict[str, Any],
    runtime: dict[str, Any],
    canonical_report: dict[str, Any],
    canonical_manifest: dict[str, Any],
) -> None:
    expected_mobility = "SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK"
    check_equal(errors, "canonical report mobilityMode", canonical_report.get("mobilityMode"), expected_mobility)
    check_equal(errors, "canonical manifest mobilityMode", canonical_manifest.get("mobilityMode"), expected_mobility)

    expected_mode = "ADAPTIVE" if manifest.get("configId") == "CFG-G-ADAPTIVE" else "STATIC"
    runtime_mode = runtime.get("gaParameterScalingMode")
    report_mode = canonical_report.get("gaParameterScalingMode")
    canonical_manifest_mode = canonical_manifest.get("gaParameterScalingMode")
    campaign_manifest_mode = manifest.get("gaParameterScalingMode")
    check_equal(errors, "runtime config gaParameterScalingMode", runtime_mode, expected_mode)
    check_equal(errors, "campaign manifest gaParameterScalingMode", campaign_manifest_mode, expected_mode)
    check_equal(errors, "canonical report gaParameterScalingMode", report_mode, expected_mode)
    check_equal(errors, "canonical manifest gaParameterScalingMode", canonical_manifest_mode, expected_mode)
    if len({str(runtime_mode), str(report_mode), str(canonical_manifest_mode)}) != 1:
        errors.append(
            "canonical GA metadata diverges across runtime config, "
            "materialization report and materialization manifest"
        )


def validate_routes(
    errors: list[str],
    warnings: list[str],
    root: Path,
    manifest: dict[str, Any],
    report: dict[str, Any],
    spec: dict[str, Any],
) -> dict[str, Any]:
    density = manifest["density"]
    subset = report.get("routeSubsets", {}).get(density)
    metrics = {
        "sumoErrors": None,
        "teleportMentions": None,
        "emergencyBrakingMentions": None,
        "vehicleCount": None,
    }
    if subset is None:
        errors.append(f"routeSubsets.{density} missing in materialization report")
        return metrics

    route_file = Path(subset.get("routeFile", ""))
    if not route_file.is_absolute():
        route_file = root / "sumo" / route_file.name
    elif not route_file.exists():
        route_file = root / "sumo" / route_file.name
    if not route_file.exists():
        errors.append(f"route XML missing: {route_file}")
        return metrics

    try:
        vehicle_ids = read_route_vehicle_ids(route_file)
    except ET.ParseError as exc:
        errors.append(f"route XML parse failure: {exc}")
        return metrics

    metrics["vehicleCount"] = len(vehicle_ids)
    duplicate_count = len(vehicle_ids) - len(set(vehicle_ids))
    if duplicate_count:
        errors.append(f"route XML contains {duplicate_count} duplicate vehicle IDs")

    counts = {family: int(subset.get("vehicleCounts", {}).get(family, 0)) for family in ROUTE_FAMILIES}
    if int(subset.get("vehicleCount", 0)) != sum(counts.values()):
        errors.append("routeSubsets vehicleCount does not equal summed family vehicleCounts")
    if len(vehicle_ids) != int(subset.get("vehicleCount", 0)):
        errors.append("route XML vehicle count does not match materialization report")

    expected_counts = manifest.get("routeFamilyVehicleCounts") or {}
    if expected_counts:
        missing = [family for family in ROUTE_FAMILIES if family not in counts]
        if missing:
            errors.append(f"direct route report misses families: {missing}")
        for family in ROUTE_FAMILIES:
            check_equal(errors, f"route family {family}", counts.get(family), int(expected_counts.get(family, 0)), abs_tol=0)
        expected_total = int(spec["directRouteProfiles"][manifest["configId"]]["vehicleTotal"])
        check_equal(errors, "direct route vehicle total", sum(counts.values()), expected_total, abs_tol=0)
        dominant = spec["directRouteProfiles"][manifest["configId"]]["dominantFamily"]
        if counts.get(dominant, 0) != max(counts.values()):
            errors.append(f"dominant route family {dominant} is not dominant in generated counts")
        warnings.extend(manifest.get("classificationFlags", []))

    logs = subset.get("mobilityValidation", {}).get("logs", {})
    metrics["sumoErrors"] = int(logs.get("errorCount", 0))
    metrics["teleportMentions"] = int(logs.get("teleportMentions", 0))
    metrics["emergencyBrakingMentions"] = int(logs.get("emergencyBrakingMentions", 0))
    if metrics["sumoErrors"] != 0:
        errors.append("SUMO error count is not zero")
    if metrics["teleportMentions"] != 0:
        errors.append("SUMO teleport mention count is not zero")
    if metrics["emergencyBrakingMentions"] != 0:
        errors.append("SUMO emergency braking mention count is not zero")

    return metrics


def validate_manifest_identity(errors: list[str], manifest: dict[str, Any], spec: dict[str, Any], root: Path) -> None:
    check_equal(errors, "campaignId", manifest.get("campaignId"), spec["campaignId"])
    check_equal(errors, "frozenCommit", manifest.get("frozenCommit"), spec["frozenCommit"])
    check_equal(errors, "campaignBranch", manifest.get("campaignBranch"), spec["campaignBranch"])
    check_equal(errors, "campaignSeed", manifest.get("campaignSeed"), int(manifest.get("mobilitySeed", -1)), abs_tol=0)
    check_equal(errors, "mobilitySeed", manifest.get("mobilitySeed"), int(manifest.get("campaignSeed", -1)), abs_tol=0)
    check_equal(errors, "workloadSeed", manifest.get("workloadSeed"), int(manifest.get("campaignSeed", 0)) + 1000003, abs_tol=0)
    if not str(root).replace("\\", "/").endswith(str(manifest.get("targetDirectory", "")).rstrip("/").replace("\\", "/")):
        errors.append("scenario root does not match manifest targetDirectory suffix")


def validate_scenario(scenario_root: Path, spec: dict[str, Any]) -> dict[str, Any]:
    root = scenario_root.resolve()
    errors: list[str] = []
    warnings: list[str] = []
    metrics: dict[str, Any] = {}

    manifest_path = root / "final_campaign_manifest.json"
    if not root.exists():
        errors.append(f"scenario root does not exist: {root}")
        return {"status": "FAILED_VALIDATION", "errors": errors, "warnings": warnings, "metrics": metrics}
    if not manifest_path.exists():
        errors.append("final_campaign_manifest.json missing")
        return {"status": "FAILED_VALIDATION", "errors": errors, "warnings": warnings, "metrics": metrics}

    manifest = read_json(manifest_path)
    validate_manifest_identity(errors, manifest, spec, root)

    live_state_path = root / "application" / "ma_ga_live_state_config.json"
    runtime_path = root / "application" / "ma_ga_live_runtime_config.json"
    sns_path = root / "sns" / "sns_config.json"
    network_path = root / "cell" / "network.json"
    regions_path = root / "cell" / "regions.json"
    scenario_config_path = root / "scenario_config.json"
    report_path = root / "reports" / "intas_literature_materialization_report.json"
    canonical_manifest_path = root / "materialization_manifest.json"

    required_json = [live_state_path, runtime_path, sns_path, network_path, regions_path, scenario_config_path, report_path, canonical_manifest_path]
    for path in required_json:
        if not path.exists():
            errors.append(f"required JSON missing: {path.relative_to(root)}")
    if errors:
        return {"status": "FAILED_VALIDATION", "errors": errors, "warnings": warnings, "metrics": metrics, "manifest": manifest}

    live_state = read_json(live_state_path)
    runtime = read_json(runtime_path)
    sns = read_json(sns_path)
    network = read_json(network_path)
    regions = read_json(regions_path)
    scenario_config = read_json(scenario_config_path)
    report = read_json(report_path)
    canonical_manifest = read_json(canonical_manifest_path)

    check_equal(errors, "scenario_config simulation duration", parse_seconds(scenario_config.get("simulation", {}).get("duration")), manifest["durationSeconds"])
    validate_canonical_deploy_metadata(errors, manifest, runtime, report, canonical_manifest)
    validate_workload(errors, live_state, manifest, spec)
    validate_resources(errors, live_state, runtime, sns, network, regions, manifest, spec)
    metrics.update(validate_routes(errors, warnings, root, manifest, report, spec))

    db_path = root / "application" / "intas_literature_urban.db"
    db_ok, db_error = sqlite_readable(db_path)
    metrics["databasePresent"] = db_ok
    if not db_ok:
        errors.append(f"SQLite database is not readable: {db_error}")

    errors.extend(collect_json_parse_errors(root))
    errors.extend(collect_template_tokens(root))

    generated_hash_errors = []
    for item in manifest.get("generatedFiles", []):
        rel = item.get("path")
        expected_hash = item.get("sha256")
        if not rel or not expected_hash:
            continue
        file_path = root / rel
        if not file_path.exists():
            generated_hash_errors.append(f"generated file listed in manifest is missing: {rel}")
        elif sha256_file(file_path) != expected_hash:
            generated_hash_errors.append(f"generated file hash mismatch: {rel}")
    errors.extend(generated_hash_errors)

    status = "MATERIALIZED_VALIDATED"
    if errors:
        status = "FAILED_VALIDATION"
    elif warnings:
        status = "MATERIALIZED_WITH_WARNINGS"

    return {
        "status": status,
        "errors": errors,
        "warnings": sorted(set(warnings)),
        "metrics": metrics,
        "manifest": {
            "campaignId": manifest.get("campaignId"),
            "groupId": manifest.get("groupId"),
            "configId": manifest.get("configId"),
            "materializationId": manifest.get("materializationId"),
            "classification": manifest.get("classification"),
        },
    }


def main() -> int:
    args = parse_args()
    spec = read_json(Path(args.spec))
    result = validate_scenario(Path(args.scenario_root), spec)
    output = Path(args.json_output) if args.json_output else Path(args.scenario_root) / "reports" / "final_campaign_validation_report.json"
    write_json(output, result)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["status"] in {"MATERIALIZED_VALIDATED", "MATERIALIZED_WITH_WARNINGS"} else 1


if __name__ == "__main__":
    sys.exit(main())
