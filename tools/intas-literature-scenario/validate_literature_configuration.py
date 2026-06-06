#!/usr/bin/env python3
"""Validate generated literature-based MOSAIC textual configuration files."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


STATUS_VALID = "VALID_TEXTUAL_CONFIGURATION"
STATUS_INVALID = "INVALID_TEXTUAL_CONFIGURATION"
TOKEN_RE = re.compile(r"\$\{[^}]+}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate generated MaGaLiteratureBasedUrbanStudy text configs.")
    parser.add_argument("--scenario-root", required=True, help="Generated MaGaLiteratureBasedUrbanStudy root.")
    parser.add_argument(
        "--json-output",
        help="Optional validation JSON path. Defaults to <scenario-root>/reports/literature_configuration_validation.json.",
    )
    return parser.parse_args()


def load_json(path: Path, errors: list[str]) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        errors.append(f"Missing required JSON file: {relative(path)}")
    except json.JSONDecodeError as exc:
        errors.append(f"Invalid JSON in {relative(path)}: {exc}")
    return {}


def relative(path: Path) -> str:
    try:
        return str(path.relative_to(Path.cwd())).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


def number(value: Any) -> float | None:
    if isinstance(value, (int, float)) and math.isfinite(float(value)):
        return float(value)
    return None


def assert_equal(name: str, actual: Any, expected: Any, errors: list[str]) -> None:
    if actual != expected:
        errors.append(f"{name} expected {expected!r}, found {actual!r}")


def assert_close(name: str, actual: Any, expected: float, errors: list[str], tolerance: float = 1e-9) -> None:
    actual_number = number(actual)
    if actual_number is None or abs(actual_number - expected) > tolerance:
        errors.append(f"{name} expected {expected}, found {actual!r}")


def plausible_ingolstadt(latitude: Any, longitude: Any) -> bool:
    lat = number(latitude)
    lon = number(longitude)
    return lat is not None and lon is not None and 48.55 <= lat <= 48.95 and 11.0 <= lon <= 11.8


def scan_template_tokens(scenario_root: Path) -> list[str]:
    matches: list[str] = []
    for path in scenario_root.rglob("*"):
        if path.is_dir() or path.suffix.lower() not in {".json", ".xml", ".sumocfg"}:
            continue
        if "reports" in path.parts:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if TOKEN_RE.search(text):
            matches.append(relative(path))
    return sorted(matches)


def validate(scenario_root: Path) -> dict[str, Any]:
    errors: list[str] = []
    warnings: list[str] = []
    root = scenario_root.resolve()

    required_json = {
        "scenario": root / "scenario_config.json",
        "mapping": root / "mapping" / "mapping_config.json",
        "sns": root / "sns" / "sns_config.json",
        "cell": root / "cell" / "cell_config.json",
        "network": root / "cell" / "network.json",
        "regions": root / "cell" / "regions.json",
        "application": root / "application" / "application_config.json",
        "metadata": root / "application" / "ma_ga_calibration_metadata.json",
        "liveState": root / "application" / "ma_ga_live_state_config.json",
        "liveRuntime": root / "application" / "ma_ga_live_runtime_config.json",
        "materializationReport": root / "reports" / "intas_literature_materialization_report.json",
    }
    docs = {key: load_json(path, errors) for key, path in required_json.items()}

    token_files = scan_template_tokens(root)
    if token_files:
        errors.append(f"Unresolved template tokens remain in concrete files: {token_files}")

    scenario = docs["scenario"]
    projection = scenario.get("simulation", {}).get("projection", {})
    center = projection.get("centerCoordinates", {})
    offset = projection.get("cartesianOffset", {})
    for name, value in {
        "projection.center.latitude": center.get("latitude"),
        "projection.center.longitude": center.get("longitude"),
        "projection.offset.x": offset.get("x"),
        "projection.offset.y": offset.get("y"),
    }.items():
        if number(value) is None:
            errors.append(f"{name} must be numeric and finite.")
    if not plausible_ingolstadt(center.get("latitude"), center.get("longitude")):
        errors.append("Projection center is not plausible for Ingolstadt.")

    report = docs["materializationReport"]
    report_projection = report.get("projection", {})
    if report_projection.get("method") != "SUMOLIB_CONVERT_XY2LONLAT":
        errors.append("Materialization report projection method must be SUMOLIB_CONVERT_XY2LONLAT.")
    if report_projection.get("fallback") is not None:
        errors.append("Materialization report projection fallback must be null.")

    mapping = docs["mapping"]
    prototypes = mapping.get("prototypes", [])
    if not prototypes:
        errors.append("Mapping prototypes for SUMO vTypes are missing.")
    for prototype in prototypes:
        if not prototype.get("applications"):
            errors.append(f"Prototype {prototype.get('name')} has no applications.")
        assert_close(f"prototype {prototype.get('name')} weight", prototype.get("weight"), 1.0, errors)
    if mapping.get("vehicles") not in ([], None):
        errors.append("mapping_config.json must not create vehicles via mapping vehicles section.")

    rsus = mapping.get("rsus", [])
    assert_equal("mapping RSU count", len(rsus), 2, errors)
    for rsu in rsus:
        pos = rsu.get("position", {})
        if not plausible_ingolstadt(pos.get("latitude"), pos.get("longitude")):
            errors.append(f"RSU {rsu.get('name')} position is not plausible for Ingolstadt.")

    live_state = docs["liveState"]
    assert_close("SNS/live-state radius", live_state.get("singlehopRadiusMeters"), 250.0, errors)
    assert_close("local CPU", live_state.get("localCpuCyclesPerSecond"), 1_000_000_000.0, errors)
    assert_close("v2v pool capacity", live_state.get("v2vNominalBandwidthBitsPerSecond"), 4_700_000.0, errors)
    assert_close("V2V fixed delay", live_state.get("v2vPropagationDelaySeconds"), 0.002, errors)

    gateways = live_state.get("staticInfrastructure", {}).get("gateways", [])
    assert_equal("live-state gateway count", len(gateways), 2, errors)
    gateway_by_id = {gateway.get("runtimeId"): gateway for gateway in gateways}
    mapping_by_id = {rsu.get("name"): rsu for rsu in rsus}
    for gateway_id in ("rsu_0", "rsu_1"):
        gateway = gateway_by_id.get(gateway_id)
        mapping_rsu = mapping_by_id.get(gateway_id)
        if not gateway or not mapping_rsu:
            errors.append(f"Missing gateway or mapping RSU for {gateway_id}.")
            continue
        assert_equal(f"{gateway_id} cell region", gateway.get("cellRegionId"), "region_cell_5g_aveiro_p50", errors)
        assert_equal(f"{gateway_id} pool", gateway.get("bandwidthPoolId"), f"pool_{gateway_id}", errors)
        assert_close(f"{gateway_id} coverage", gateway.get("coverageRadiusMeters"), 250.0, errors)
        assert_close(f"{gateway_id} longitude coherence", gateway.get("longitude"), mapping_rsu["position"].get("longitude"), errors, 1e-9)
        assert_close(f"{gateway_id} latitude coherence", gateway.get("latitude"), mapping_rsu["position"].get("latitude"), errors, 1e-9)

    sns = docs["sns"]
    assert_close("sns singlehopRadius", sns.get("singlehopRadius"), 250.0, errors)

    cell_accounting = live_state.get("cellDiagnosticAccounting", {})
    pools = {pool.get("poolId"): pool for pool in cell_accounting.get("gatewayPools", [])}
    for pool_id in ("pool_rsu_0", "pool_rsu_1"):
        if pool_id not in pools:
            errors.append(f"Missing gateway pool {pool_id}.")
        else:
            assert_close(f"{pool_id} nominal capacity", pools[pool_id].get("nominalCapacityBitsPerSecond"), 49_200_000.0, errors)

    regions = docs["regions"].get("regions", [])
    assert_equal("Cell region count", len(regions), 1, errors)
    if regions:
        assert_equal("Cell region id", regions[0].get("id"), "region_cell_5g_aveiro_p50", errors)
        area = regions[0].get("area", {})
        if not plausible_ingolstadt(area.get("nw", {}).get("lat"), area.get("nw", {}).get("lon")):
            errors.append("Cell NW coordinate is not plausible for Ingolstadt.")
        if not plausible_ingolstadt(area.get("se", {}).get("lat"), area.get("se", {}).get("lon")):
            errors.append("Cell SE coordinate is not plausible for Ingolstadt.")

    network = docs["network"]
    assert_close("global uplink capacity", network.get("globalNetwork", {}).get("uplink", {}).get("capacity"), 49_200_000.0, errors)
    assert_close("global downlink capacity", network.get("globalNetwork", {}).get("downlink", {}).get("capacity"), 49_200_000.0, errors)
    for server in network.get("servers", []):
        if server.get("id") == "server_0":
            break
    else:
        errors.append("network.json must define server_0.")

    edge_nodes = live_state.get("staticInfrastructure", {}).get("edgeNodes", [])
    assert_equal("EDGE node count", len(edge_nodes), 2, errors)
    for node in edge_nodes:
        assert_close(f"{node.get('executionNodeId')} CPU", node.get("availableCpuCyclesPerSecond"), 5_000_000_000.0, errors)
    cloud_nodes = live_state.get("staticInfrastructure", {}).get("cloudNodes", [])
    assert_equal("CLOUD node count", len(cloud_nodes), 1, errors)
    if cloud_nodes:
        assert_close("CLOUD CPU", cloud_nodes[0].get("availableCpuCyclesPerSecond"), 100_000_000_000.0, errors)
        assert_close("CLOUD backhaul delay", cloud_nodes[0].get("serverBaseDelaySeconds"), 0.050, errors)

    metadata = docs["metadata"]
    marker = "TEMPORARY_COMPATIBILITY_TASK_NOT_FINAL_WORKLOAD"
    if marker not in json.dumps(metadata, sort_keys=True):
        errors.append("Metadata must mark bootstrap task as temporary compatibility workload.")
    if marker not in json.dumps(live_state.get("taskProfiles", []), sort_keys=True):
        errors.append("Live-state bootstrap task must carry temporary marker.")

    db_files = [relative(path) for path in root.rglob("*.db")]
    jar_files = [relative(path) for path in root.rglob("*.jar")]
    pyc_files = [relative(path) for path in root.rglob("*.pyc")]
    if db_files:
        errors.append(f"Generated scenario contains database files while scenario-convert is unavailable: {db_files}")
    if jar_files:
        errors.append(f"Generated scenario contains JAR files: {jar_files}")
    if pyc_files:
        errors.append(f"Generated scenario contains pyc files: {pyc_files}")

    status = STATUS_VALID if not errors else STATUS_INVALID
    return {
        "status": status,
        "scenarioRoot": str(root),
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "projectionMethod": report_projection.get("method"),
        "projectionFallback": report_projection.get("fallback"),
        "centerCoordinates": center,
        "rsuCount": len(rsus),
        "gatewayCount": len(gateways),
        "snsSinglehopRadius": sns.get("singlehopRadius"),
        "liveStateSinglehopRadiusMeters": live_state.get("singlehopRadiusMeters"),
        "cellRegionIds": [region.get("id") for region in regions],
        "gatewayPoolIds": sorted(pools),
        "dbFiles": db_files,
        "jarFiles": jar_files,
        "pycFiles": pyc_files,
        "warnings": warnings,
        "errors": errors,
    }


def main() -> int:
    args = parse_args()
    scenario_root = Path(args.scenario_root).resolve()
    output = Path(args.json_output) if args.json_output else scenario_root / "reports" / "literature_configuration_validation.json"
    result = validate(scenario_root)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["status"] == STATUS_VALID else 2


if __name__ == "__main__":
    sys.exit(main())
