#!/usr/bin/env python3
"""Assemble final MA-GA SystemSnapshot JSON files from validated MOSAIC streams."""

from __future__ import annotations

import argparse
import bisect
import csv
import json
import math
import re
from collections import Counter, defaultdict
from pathlib import Path


PHASE = "10I_SYSTEM_SNAPSHOT_JSON_GENERATION"
SNAPSHOT_TIMELINE_POLICY = "EXPLICIT_OPTIMIZATION_WINDOW_TIMELINE"
ACTIVE_VEHICLE_SET_POLICY = "ACTIVE_VEHICLES_FROM_EXACT_LOCAL_CANDIDATES"
VEHICLE_LOOKUP_POLICY = "LATEST_AVAILABLE_STATE_AT_OR_BEFORE_WINDOW"
EXACT_POLICY = "EXACT_WINDOW_TIMESTAMP"
GATEWAY_POOL_POLICY = "LATEST_SAFE_AVAILABLE_CELL_BUCKET_PER_GATEWAY_POOL"
CELL_BANDWIDTH_LOOKUP_POLICY = "LATEST_SAFE_AVAILABLE_CELL_BUCKET"
SOURCE_RUN_FIELDS = ("sourceRun",)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export MOSAIC-derived SystemSnapshot JSON files.")
    parser.add_argument("--timeline-file", required=True)
    parser.add_argument("--window-task-assignment-file", required=True)
    parser.add_argument("--vehicle-state-file", required=True)
    parser.add_argument("--infrastructure-file", required=True)
    parser.add_argument("--cell-bandwidth-file", required=True)
    parser.add_argument("--access-link-file", required=True)
    parser.add_argument("--remote-candidate-file", required=True)
    parser.add_argument("--local-candidate-file", required=True)
    parser.add_argument("--v2v-candidate-file", required=True)
    parser.add_argument("--v2v-pool-file", required=True)
    parser.add_argument("--baseline-metadata-file", required=True)
    parser.add_argument("--phase-10g-validation-file", required=True)
    parser.add_argument("--phase-10h-validation-file", required=True)
    parser.add_argument("--phase-10i-pre-validation-file", required=True)
    parser.add_argument("--phase-10i-pre2-validation-file", required=True)
    parser.add_argument("--expected-source-run", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--manifest-out-file", required=True)
    parser.add_argument("--validation-out-file", required=True)
    parser.add_argument("--clean-output-dir", action="store_true")
    return parser.parse_args()


def require_file(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Required file not found: {path}")


def read_csv(path: Path) -> list[dict[str, str]]:
    require_file(path)
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None:
            raise ValueError(f"CSV has no header: {path}")
        return list(reader)


def read_json(path: Path) -> dict:
    require_file(path)
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, sort_keys=False)
        handle.write("\n")


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def to_int(value: str | int, field: str) -> int:
    try:
        return int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"Invalid integer {field}: {value}") from exc


def to_float(value: str | float | int, field: str) -> float:
    try:
        result = float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"Invalid float {field}: {value}") from exc
    if not math.isfinite(result):
        raise ValueError(f"Non-finite float {field}: {value}")
    return result


def parse_bool(value: str | bool, field: str) -> bool:
    if isinstance(value, bool):
        return value
    normalized = str(value).strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise ValueError(f"Invalid boolean {field}: {value}")


def natural_key(value: str) -> list[object]:
    return [int(part) if part.isdigit() else part for part in re.split(r"(\d+)", value)]


def sort_ids(values) -> list[str]:
    return sorted(values, key=natural_key)


def index_by_time(rows: list[dict[str, str]]) -> dict[int, list[dict[str, str]]]:
    result: dict[int, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        result[to_int(row["timeNs"], "timeNs")].append(row)
    return result


def verify_source_runs(expected: str, files: dict[str, dict]) -> list[str]:
    errors: list[str] = []
    for name, data in files.items():
        source = None
        for field in SOURCE_RUN_FIELDS:
            if field in data:
                source = data[field]
                break
        if source != expected:
            errors.append(f"{name} sourceRun mismatch: expected {expected}, found {source}")
    return errors


def prepare_output_dir(path: Path, clean: bool) -> None:
    if path.exists() and not path.is_dir():
        raise ValueError(f"Output path exists but is not a directory: {path}")
    existing_json = sorted(path.glob("*.json")) if path.exists() else []
    if existing_json and not clean:
        raise ValueError(f"Output directory contains JSON files; pass --clean-output-dir: {path}")
    path.mkdir(parents=True, exist_ok=True)
    if clean:
        for file in existing_json:
            file.unlink()


def make_snapshot_name(index: int, time_seconds: float) -> tuple[str, str]:
    if float(time_seconds).is_integer():
        time_label = f"{int(time_seconds):03d}"
    else:
        time_label = str(time_seconds).replace(".", "_")
    file_name = f"snapshot_{index:03d}_t_{time_label}.json"
    snapshot_id = f"mosaic_generated_{index:03d}_t_{time_label}"
    return file_name, snapshot_id


def build_vehicle_index(rows: list[dict[str, str]]) -> dict[str, tuple[list[int], list[dict[str, str]]]]:
    by_vehicle: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        by_vehicle[row["vehicleId"]].append(row)
    result = {}
    for vehicle_id, vehicle_rows in by_vehicle.items():
        ordered = sorted(vehicle_rows, key=lambda row: to_int(row["timeNs"], "vehicle timeNs"))
        result[vehicle_id] = ([to_int(row["timeNs"], "vehicle timeNs") for row in ordered], ordered)
    return result


def lookup_vehicle_state(index, vehicle_id: str, window_time_ns: int):
    if vehicle_id not in index:
        return None
    times, rows = index[vehicle_id]
    pos = bisect.bisect_right(times, window_time_ns) - 1
    if pos < 0:
        return None
    return rows[pos]


def build_cell_index(rows: list[dict[str, str]]):
    index: dict[str, dict[str, list[dict[str, str]]]] = defaultdict(lambda: defaultdict(list))
    for row in rows:
        index[row["regionId"]][row["direction"].upper()].append(row)
    for directions in index.values():
        for direction, direction_rows in directions.items():
            direction_rows.sort(key=lambda row: to_float(row["availableFromTimeSeconds"], "availableFromTimeSeconds"))
    return index


def latest_cell_bucket(index, region_id: str, direction: str, window_seconds: float):
    rows = index.get(region_id, {}).get(direction, [])
    latest = None
    for row in rows:
        if to_float(row["availableFromTimeSeconds"], "availableFromTimeSeconds") <= window_seconds:
            latest = row
        else:
            break
    return latest


def gateway_pool_bandwidth(cell_index, pool: dict, window_seconds: float):
    region_id = pool.get("cellRegionId")
    if not region_id:
        return None, "gateway pool has no cellRegionId"
    uplink = latest_cell_bucket(cell_index, region_id, "UPLINK", window_seconds)
    downlink = latest_cell_bucket(cell_index, region_id, "DOWNLINK", window_seconds)
    if uplink is None or downlink is None:
        return None, f"missing safe cell bucket for pool {pool.get('poolId')} region {region_id}"
    available_from = max(
        to_float(uplink["availableFromTimeSeconds"], "availableFromTimeSeconds"),
        to_float(downlink["availableFromTimeSeconds"], "availableFromTimeSeconds"),
    )
    if available_from > window_seconds:
        return None, f"future cell bucket selected for pool {pool.get('poolId')}"
    available = min(
        to_float(uplink["residualCapacityBitsPerSecond"], "uplink residual"),
        to_float(downlink["residualCapacityBitsPerSecond"], "downlink residual"),
    )
    return available, None


def count_duplicates(values: list[str]) -> int:
    counts = Counter(values)
    return sum(count - 1 for count in counts.values() if count > 1)


def collect_active_links(access_links: list[dict]) -> dict[str, list[dict]]:
    result: dict[str, list[dict]] = defaultdict(list)
    for link in access_links:
        if link["active"]:
            result[link["vehicleId"]].append(link)
    return result


def main() -> int:
    args = parse_args()
    input_file_names = [
        "timeline_file",
        "window_task_assignment_file",
        "vehicle_state_file",
        "infrastructure_file",
        "cell_bandwidth_file",
        "access_link_file",
        "remote_candidate_file",
        "local_candidate_file",
        "v2v_candidate_file",
        "v2v_pool_file",
        "baseline_metadata_file",
        "phase_10g_validation_file",
        "phase_10h_validation_file",
        "phase_10i_pre_validation_file",
        "phase_10i_pre2_validation_file",
    ]
    for name in input_file_names:
        require_file(Path(getattr(args, name)))

    output_dir = Path(args.output_dir)
    manifest_out_file = Path(args.manifest_out_file)
    validation_out_file = Path(args.validation_out_file)
    prepare_output_dir(output_dir, args.clean_output_dir)

    baseline_metadata = read_json(Path(args.baseline_metadata_file))
    phase_10g = read_json(Path(args.phase_10g_validation_file))
    phase_10h = read_json(Path(args.phase_10h_validation_file))
    phase_10i_pre = read_json(Path(args.phase_10i_pre_validation_file))
    phase_10i_pre2 = read_json(Path(args.phase_10i_pre2_validation_file))

    errors = verify_source_runs(
        args.expected_source_run,
        {
            "baselineMetadata": baseline_metadata,
            "phase10g": phase_10g,
            "phase10h": phase_10h,
            "phase10iPre": phase_10i_pre,
            "phase10iPre2": phase_10i_pre2,
        },
    )
    if phase_10i_pre2.get("phase10iPre2Status") != "COMPLETED" or phase_10i_pre2.get("readyForPhase10I") is not True:
        errors.append("phase 10I-pre2 gate is not completed and ready.")
    if phase_10i_pre.get("phase10iPreStatus") != "COMPLETED" or phase_10i_pre.get("readyForPhase10I") is not True:
        errors.append("phase 10I-pre gate is not completed and ready.")

    timeline_rows = read_csv(Path(args.timeline_file))
    assignment_rows = read_csv(Path(args.window_task_assignment_file))
    vehicle_rows = read_csv(Path(args.vehicle_state_file))
    infrastructure = read_json(Path(args.infrastructure_file))
    cell_rows = read_csv(Path(args.cell_bandwidth_file))
    access_rows = read_csv(Path(args.access_link_file))
    remote_rows = read_csv(Path(args.remote_candidate_file))
    local_rows = read_csv(Path(args.local_candidate_file))
    v2v_rows = read_csv(Path(args.v2v_candidate_file))
    v2v_pool_rows = read_csv(Path(args.v2v_pool_file))

    vehicle_index = build_vehicle_index(vehicle_rows)
    cell_index = build_cell_index(cell_rows)
    assignments_by_window: dict[int, list[dict[str, str]]] = defaultdict(list)
    for row in assignment_rows:
        assignments_by_window[to_int(row["windowIndex"], "windowIndex")].append(row)
    local_by_time = index_by_time(local_rows)
    access_by_time = index_by_time(access_rows)
    remote_by_time = index_by_time(remote_rows)
    v2v_by_time = index_by_time(v2v_rows)
    v2v_pool_by_time = index_by_time(v2v_pool_rows)

    gateways = infrastructure.get("gateways", [])
    gateway_by_id = {gateway["gatewayId"]: gateway for gateway in gateways}
    execution_nodes = {node["executionNodeId"]: node for node in infrastructure.get("executionNodes", [])}
    gateway_pools = [pool for pool in infrastructure.get("bandwidthPools", []) if pool.get("poolType") == "GATEWAY"]
    gateway_pool_ids = {pool["poolId"] for pool in gateway_pools}

    manifest_rows: list[dict[str, object]] = []
    snapshot_ids: list[str] = []
    task_ids_across: list[str] = []
    vehicle_state_exact_matches = 0
    vehicle_state_latest_fallbacks = 0
    vehicle_staleness_values: list[int] = []
    vehicles_across = 0
    min_vehicles = None
    max_vehicles = None
    local_candidates_total = 0
    v2v_candidates_total = 0
    edge_candidates_total = 0
    cloud_candidates_total = 0
    gateway_pools_total = 0
    v2v_pools_total = 0
    access_links_total = 0
    active_access_links_total = 0
    empty_task_snapshots = 0
    future_lookahead = 0
    orphan_references = 0
    duplicate_candidate_ids = 0
    duplicate_pool_ids = 0
    missing_local_candidates = 0
    invalid_v2v_pairs = 0
    unresolved_gateway_pools = 0
    unresolved_v2v_pools = 0
    multiple_active_gateway_violations = 0
    active_unavailable_link_violations = 0
    cloud_placeholder_violations = 0
    remote_candidate_pool_consistency_violations = 0
    snapshot_json_parse_failures = 0
    warnings: list[str] = []

    snapshot_files: list[str] = []
    for zero_index, window in enumerate(timeline_rows):
        window_index = to_int(window["windowIndex"], "windowIndex")
        window_time_ns = to_int(window["windowTimeNs"], "windowTimeNs")
        window_seconds = to_float(window["windowTimeSeconds"], "windowTimeSeconds")
        file_name, snapshot_id = make_snapshot_name(zero_index, window_seconds)
        snapshot_ids.append(snapshot_id)

        local_at_time = {
            row["sourceVehicleId"]: row
            for row in local_by_time.get(window_time_ns, [])
        }
        active_vehicle_ids = sort_ids(local_at_time.keys())

        vehicles = []
        for vehicle_id in active_vehicle_ids:
            state = lookup_vehicle_state(vehicle_index, vehicle_id, window_time_ns)
            if state is None:
                errors.append(f"missing vehicle state for {vehicle_id} at window {window_index}")
                continue
            state_time_ns = to_int(state["timeNs"], "vehicle state timeNs")
            if state_time_ns > window_time_ns:
                future_lookahead += 1
            staleness = window_time_ns - state_time_ns
            vehicle_staleness_values.append(staleness)
            if staleness == 0:
                vehicle_state_exact_matches += 1
            else:
                vehicle_state_latest_fallbacks += 1
            local_candidate = local_at_time.get(vehicle_id)
            local_cpu = to_float(local_candidate["availableCpu"], "local availableCpu") if local_candidate else 0.0
            if local_candidate is None:
                missing_local_candidates += 1
            vehicles.append(
                {
                    "vehicleId": vehicle_id,
                    "x": to_float(state["projectedX"], "vehicle projectedX"),
                    "y": to_float(state["projectedY"], "vehicle projectedY"),
                    "speed": to_float(state["speed"], "vehicle speed"),
                    "localCpu": local_cpu,
                }
            )

        vehicle_ids = {vehicle["vehicleId"] for vehicle in vehicles}
        vehicles_count = len(vehicles)
        vehicles_across += vehicles_count
        min_vehicles = vehicles_count if min_vehicles is None else min(min_vehicles, vehicles_count)
        max_vehicles = vehicles_count if max_vehicles is None else max(max_vehicles, vehicles_count)

        tasks = []
        for row in sorted(
            assignments_by_window.get(window_index, []),
            key=lambda item: (
                to_int(item["activationTimeNs"], "activationTimeNs"),
                natural_key(item["sourceVehicleId"]),
                item["profileId"],
                item["taskId"],
            ),
        ):
            task_ids_across.append(row["taskId"])
            if row["sourceVehicleId"] not in vehicle_ids:
                orphan_references += 1
            if to_int(row["activationTimeNs"], "activationTimeNs") > window_time_ns:
                future_lookahead += 1
            tasks.append(
                {
                    "taskId": row["taskId"],
                    "sourceVehicleId": row["sourceVehicleId"],
                    "inputSizeBits": to_float(row["inputSizeBits"], "inputSizeBits"),
                    "outputSizeBits": to_float(row["outputSizeBits"], "outputSizeBits"),
                    "cpuCycles": to_float(row["cpuCycles"], "cpuCycles"),
                    "deadlineSeconds": to_float(row["deadlineSeconds"], "deadlineSeconds"),
                }
            )
        if not tasks:
            empty_task_snapshots += 1

        access_gateways = [
            {
                "gatewayId": gateway["gatewayId"],
                "gatewayType": gateway["gatewayType"],
                "x": to_float(gateway["projectedX"], "gateway projectedX"),
                "y": to_float(gateway["projectedY"], "gateway projectedY"),
                "coverageRadiusMeters": to_float(gateway["coverageRadiusMeters"], "coverageRadiusMeters"),
                "bandwidthPoolId": gateway["bandwidthPoolId"],
            }
            for gateway in sorted(gateways, key=lambda item: natural_key(item["gatewayId"]))
        ]

        gateway_bandwidth_pools = []
        gateway_pool_bandwidth_by_id = {}
        for pool in sorted(gateway_pools, key=lambda item: natural_key(item["poolId"])):
            available, problem = gateway_pool_bandwidth(cell_index, pool, window_seconds)
            if problem:
                unresolved_gateway_pools += 1
                errors.append(problem)
                continue
            gateway_pool_bandwidth_by_id[pool["poolId"]] = available
            gateway_bandwidth_pools.append(
                {
                    "poolId": pool["poolId"],
                    "poolType": "GATEWAY",
                    "availableBandwidth": available,
                }
            )

        access_links = []
        for row in sorted(
            access_by_time.get(window_time_ns, []),
            key=lambda item: (natural_key(item["vehicleId"]), natural_key(item["gatewayId"])),
        ):
            if row["vehicleId"] not in vehicle_ids:
                continue
            if row["gatewayId"] not in gateway_by_id:
                orphan_references += 1
            active = parse_bool(row["active"], "accessLink.active")
            available = parse_bool(row["available"], "accessLink.available")
            if active and not available:
                active_unavailable_link_violations += 1
            access_links.append(
                {
                    "accessLinkId": f"access_{row['vehicleId']}_{row['gatewayId']}",
                    "vehicleId": row["vehicleId"],
                    "gatewayId": row["gatewayId"],
                    "active": active,
                    "available": available,
                }
            )
        active_links_by_vehicle = collect_active_links(access_links)
        for vehicle_id, links in active_links_by_vehicle.items():
            if len(links) > 1:
                multiple_active_gateway_violations += 1

        candidate_nodes = []
        for vehicle_id in active_vehicle_ids:
            row = local_at_time.get(vehicle_id)
            if row is None:
                missing_local_candidates += 1
                continue
            if row["executionNodeId"] != vehicle_id:
                orphan_references += 1
            candidate_nodes.append(
                {
                    "candidateId": row["candidateId"],
                    "sourceVehicleId": row["sourceVehicleId"],
                    "executionNodeId": row["executionNodeId"],
                    "type": "LOCAL",
                    "availableCpu": to_float(row["availableCpu"], "local availableCpu"),
                    "availableBandwidth": 0.0,
                    "baseLatencySeconds": to_float(row["propagationDelaySeconds"], "local latency"),
                }
            )

        referenced_v2v_pool_ids = set()
        for row in sorted(
            v2v_by_time.get(window_time_ns, []),
            key=lambda item: (
                natural_key(item["sourceVehicleId"]),
                natural_key(item["targetVehicleId"]),
                item["candidateId"],
            ),
        ):
            source = row["sourceVehicleId"]
            target = row["targetVehicleId"]
            pool_id = row["bandwidthPoolId"]
            if source not in vehicle_ids or target not in vehicle_ids:
                invalid_v2v_pairs += 1
                continue
            if source == target or row["executionNodeId"] != target:
                invalid_v2v_pairs += 1
            if to_float(row["distanceMeters"], "v2v distanceMeters") > to_float(row["singlehopRadiusMeters"], "v2v radius"):
                invalid_v2v_pairs += 1
            referenced_v2v_pool_ids.add(pool_id)
            candidate_nodes.append(
                {
                    "candidateId": row["candidateId"],
                    "sourceVehicleId": source,
                    "executionNodeId": row["executionNodeId"],
                    "type": "VEHICLE",
                    "availableCpu": to_float(row["availableCpu"], "v2v availableCpu"),
                    "availableBandwidth": to_float(row["availableBandwidth"], "v2v availableBandwidth"),
                    "baseLatencySeconds": to_float(row["propagationDelaySeconds"], "v2v latency"),
                    "bandwidthPoolId": pool_id,
                }
            )

        for row in sorted(
            remote_by_time.get(window_time_ns, []),
            key=lambda item: (
                natural_key(item["sourceVehicleId"]),
                {"EDGE": 2, "CLOUD": 3}.get(item["type"], 9),
                item["candidateId"],
            ),
        ):
            source = row["sourceVehicleId"]
            candidate_type = row["type"]
            gateway_id = row["gatewayId"]
            pool_id = row["bandwidthPoolId"]
            if source not in vehicle_ids:
                orphan_references += 1
                continue
            active_links = active_links_by_vehicle.get(source, [])
            if not active_links or active_links[0]["gatewayId"] != gateway_id:
                orphan_references += 1
            if gateway_id not in gateway_by_id:
                orphan_references += 1
                continue
            if pool_id not in gateway_pool_ids:
                unresolved_gateway_pools += 1
            else:
                expected = gateway_pool_bandwidth_by_id.get(pool_id)
                if expected is not None:
                    actual = to_float(row["availableBandwidth"], "remote availableBandwidth")
                    if abs(expected - actual) > 1e-6:
                        remote_candidate_pool_consistency_violations += 1
            candidate = {
                "candidateId": row["candidateId"],
                "sourceVehicleId": source,
                "executionNodeId": row["executionNodeId"],
                "type": candidate_type,
                "availableCpu": to_float(row["availableCpu"], "remote availableCpu"),
                "availableBandwidth": to_float(row["availableBandwidth"], "remote availableBandwidth"),
                "baseLatencySeconds": to_float(row["propagationDelaySeconds"], "remote latency"),
                "bandwidthPoolId": pool_id,
            }
            if candidate_type == "EDGE":
                gateway = gateway_by_id[gateway_id]
                candidate["nodeX"] = to_float(gateway["projectedX"], "gateway projectedX")
                candidate["nodeY"] = to_float(gateway["projectedY"], "gateway projectedY")
                candidate["coverageRadiusMeters"] = to_float(gateway["coverageRadiusMeters"], "coverageRadiusMeters")
            elif candidate_type == "CLOUD":
                if "placeholder" in row["executionNodeId"].lower():
                    cloud_placeholder_violations += 1
            else:
                orphan_references += 1
            if row["executionNodeId"] not in execution_nodes:
                orphan_references += 1
            candidate_nodes.append(candidate)

        v2v_pool_rows_by_id = {row["bandwidthPoolId"]: row for row in v2v_pool_by_time.get(window_time_ns, [])}
        v2v_bandwidth_pools = []
        for pool_id in sort_ids(referenced_v2v_pool_ids):
            row = v2v_pool_rows_by_id.get(pool_id)
            if row is None:
                unresolved_v2v_pools += 1
                continue
            if row["poolType"] != "DIRECT_V2V":
                unresolved_v2v_pools += 1
            if row["vehicleA"] not in vehicle_ids or row["vehicleB"] not in vehicle_ids:
                unresolved_v2v_pools += 1
            v2v_bandwidth_pools.append(
                {
                    "poolId": pool_id,
                    "poolType": "DIRECT_V2V",
                    "availableBandwidth": to_float(row["availableBandwidth"], "v2v pool availableBandwidth"),
                }
            )

        type_order = {"LOCAL": 0, "VEHICLE": 1, "EDGE": 2, "CLOUD": 3}
        candidate_nodes.sort(
            key=lambda item: (
                natural_key(item["sourceVehicleId"]),
                type_order.get(item["type"], 9),
                natural_key(item["candidateId"]),
            )
        )
        bandwidth_pools = gateway_bandwidth_pools + v2v_bandwidth_pools
        bandwidth_pools.sort(key=lambda item: (item["poolType"], natural_key(item["poolId"])))

        candidate_ids = [candidate["candidateId"] for candidate in candidate_nodes]
        pool_ids = [pool["poolId"] for pool in bandwidth_pools]
        duplicate_candidate_ids += count_duplicates(candidate_ids)
        duplicate_pool_ids += count_duplicates(pool_ids)

        pool_id_set = set(pool_ids)
        for gateway in access_gateways:
            if gateway["bandwidthPoolId"] not in pool_id_set:
                orphan_references += 1
        for candidate in candidate_nodes:
            source = candidate["sourceVehicleId"]
            if source not in vehicle_ids:
                orphan_references += 1
            if candidate["type"] in {"LOCAL", "VEHICLE"} and candidate["executionNodeId"] not in vehicle_ids:
                orphan_references += 1
            if candidate["type"] in {"EDGE", "CLOUD"} and candidate["executionNodeId"] not in execution_nodes:
                orphan_references += 1
            pool_id = candidate.get("bandwidthPoolId")
            if candidate["type"] != "LOCAL" and pool_id not in pool_id_set:
                orphan_references += 1
        for link in access_links:
            if link["vehicleId"] not in vehicle_ids or link["gatewayId"] not in gateway_by_id:
                orphan_references += 1

        snapshot = {
            "snapshotId": snapshot_id,
            "timeSeconds": window_seconds,
            "vehicles": sorted(vehicles, key=lambda item: natural_key(item["vehicleId"])),
            "tasks": tasks,
            "candidateNodes": candidate_nodes,
            "accessGateways": access_gateways,
            "accessLinks": sorted(
                access_links,
                key=lambda item: (natural_key(item["vehicleId"]), natural_key(item["gatewayId"]), item["accessLinkId"]),
            ),
            "bandwidthPools": bandwidth_pools,
        }

        output_path = output_dir / file_name
        write_json(output_path, snapshot)
        snapshot_files.append(str(output_path))
        try:
            json.loads(output_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            snapshot_json_parse_failures += 1

        local_count = sum(1 for candidate in candidate_nodes if candidate["type"] == "LOCAL")
        v2v_count = sum(1 for candidate in candidate_nodes if candidate["type"] == "VEHICLE")
        edge_count = sum(1 for candidate in candidate_nodes if candidate["type"] == "EDGE")
        cloud_count = sum(1 for candidate in candidate_nodes if candidate["type"] == "CLOUD")
        gateway_pool_count = sum(1 for pool in bandwidth_pools if pool["poolType"] == "GATEWAY")
        v2v_pool_count = sum(1 for pool in bandwidth_pools if pool["poolType"] == "DIRECT_V2V")
        active_link_count = sum(1 for link in access_links if link["active"])
        local_candidates_total += local_count
        v2v_candidates_total += v2v_count
        edge_candidates_total += edge_count
        cloud_candidates_total += cloud_count
        gateway_pools_total += gateway_pool_count
        v2v_pools_total += v2v_pool_count
        access_links_total += len(access_links)
        active_access_links_total += active_link_count

        manifest_rows.append(
            {
                "snapshotFile": file_name,
                "snapshotId": snapshot_id,
                "windowIndex": window_index,
                "windowTimeNs": window_time_ns,
                "windowTimeSeconds": window_seconds,
                "tasksCount": len(tasks),
                "vehiclesCount": len(vehicles),
                "candidateNodesCount": len(candidate_nodes),
                "localCandidatesCount": local_count,
                "v2vCandidatesCount": v2v_count,
                "edgeCandidatesCount": edge_count,
                "cloudCandidatesCount": cloud_count,
                "accessGatewaysCount": len(access_gateways),
                "accessLinksCount": len(access_links),
                "activeAccessLinksCount": active_link_count,
                "bandwidthPoolsCount": len(bandwidth_pools),
                "gatewayPoolsCount": gateway_pool_count,
                "v2vPoolsCount": v2v_pool_count,
                "futureLookAheadViolations": 0,
                "orphanReferenceViolations": 0,
                "duplicateCandidateIds": count_duplicates(candidate_ids),
                "loaderValidationStatus": "PENDING_JAVA_VALIDATION",
            }
        )

    duplicate_snapshot_ids = count_duplicates(snapshot_ids)
    duplicate_task_assignments = count_duplicates(task_ids_across)
    unique_tasks = len(set(task_ids_across))
    tasks_expected = int(phase_10h.get("tasksAssigned", len(assignment_rows)))
    tasks_lost = tasks_expected - unique_tasks
    tasks_before_activation = sum(
        1
        for row in assignment_rows
        if to_int(row["activationTimeNs"], "activationTimeNs") > to_int(row["windowTimeNs"], "windowTimeNs")
    )

    hard_errors = []
    if errors:
        hard_errors.extend(errors)
    if len(snapshot_files) != len(timeline_rows):
        hard_errors.append("snapshotsGenerated does not match timeline length")
    if tasks_lost != 0:
        hard_errors.append("tasks lost across snapshots")
    if duplicate_task_assignments != 0:
        hard_errors.append("duplicate task assignments across snapshots")
    if future_lookahead != 0:
        hard_errors.append("future look-ahead violations detected")
    if orphan_references != 0:
        hard_errors.append("orphan references detected")
    if duplicate_candidate_ids != 0:
        hard_errors.append("duplicate candidate ids detected")
    if duplicate_pool_ids != 0:
        hard_errors.append("duplicate pool ids detected")
    if unresolved_gateway_pools != 0:
        hard_errors.append("unresolved gateway pools detected")
    if unresolved_v2v_pools != 0:
        hard_errors.append("unresolved V2V pools detected")
    if multiple_active_gateway_violations != 0:
        hard_errors.append("multiple active gateways detected")
    if active_unavailable_link_violations != 0:
        hard_errors.append("active unavailable links detected")
    if cloud_placeholder_violations != 0:
        hard_errors.append("cloud placeholder violations detected")
    if snapshot_json_parse_failures != 0:
        hard_errors.append("snapshot JSON parse failures detected")

    status = "COMPLETED" if not hard_errors else "FAILED"
    ready = not hard_errors

    manifest_fields = [
        "snapshotFile",
        "snapshotId",
        "windowIndex",
        "windowTimeNs",
        "windowTimeSeconds",
        "tasksCount",
        "vehiclesCount",
        "candidateNodesCount",
        "localCandidatesCount",
        "v2vCandidatesCount",
        "edgeCandidatesCount",
        "cloudCandidatesCount",
        "accessGatewaysCount",
        "accessLinksCount",
        "activeAccessLinksCount",
        "bandwidthPoolsCount",
        "gatewayPoolsCount",
        "v2vPoolsCount",
        "futureLookAheadViolations",
        "orphanReferenceViolations",
        "duplicateCandidateIds",
        "loaderValidationStatus",
    ]
    write_csv(manifest_out_file, manifest_fields, manifest_rows)

    validation = {
        "sourceRun": args.expected_source_run,
        "phase": PHASE,
        "projectionPolicy": phase_10i_pre2.get("projectionPolicy"),
        "projectionSourceFile": phase_10i_pre2.get("projectionSourceFile"),
        "projectionParameters": phase_10i_pre2.get("projectionParameters"),
        "snapshotTimelinePolicy": SNAPSHOT_TIMELINE_POLICY,
        "activeVehicleSetPolicy": ACTIVE_VEHICLE_SET_POLICY,
        "vehicleLookupPolicy": VEHICLE_LOOKUP_POLICY,
        "accessLinkLookupPolicy": EXACT_POLICY,
        "localCandidateLookupPolicy": EXACT_POLICY,
        "v2vCandidateLookupPolicy": EXACT_POLICY,
        "remoteCandidateLookupPolicy": EXACT_POLICY,
        "v2vPoolLookupPolicy": EXACT_POLICY,
        "gatewayPoolAssemblyPolicy": GATEWAY_POOL_POLICY,
        "cellBandwidthLookupPolicy": CELL_BANDWIDTH_LOOKUP_POLICY,
        "snapshotsGenerated": len(snapshot_files),
        "expectedSnapshots": len(timeline_rows),
        "emptyTaskSnapshots": empty_task_snapshots,
        "totalTasksAcrossSnapshots": len(task_ids_across),
        "uniqueTasksAcrossSnapshots": unique_tasks,
        "tasksExpectedFrom10H": tasks_expected,
        "tasksLost": tasks_lost,
        "duplicateTaskAssignmentsAcrossSnapshots": duplicate_task_assignments,
        "tasksAssignedBeforeActivation": tasks_before_activation,
        "vehiclesAcrossSnapshots": vehicles_across,
        "minimumVehiclesPerSnapshot": min_vehicles if min_vehicles is not None else 0,
        "maximumVehiclesPerSnapshot": max_vehicles if max_vehicles is not None else 0,
        "vehicleStateExactMatches": vehicle_state_exact_matches,
        "vehicleStateLatestFallbacks": vehicle_state_latest_fallbacks,
        "maximumVehicleStateStalenessNs": max(vehicle_staleness_values) if vehicle_staleness_values else 0,
        "averageVehicleStateStalenessNs": (
            sum(vehicle_staleness_values) / len(vehicle_staleness_values) if vehicle_staleness_values else 0
        ),
        "localCandidatesAcrossSnapshots": local_candidates_total,
        "v2vCandidatesAcrossSnapshots": v2v_candidates_total,
        "edgeCandidatesAcrossSnapshots": edge_candidates_total,
        "cloudCandidatesAcrossSnapshots": cloud_candidates_total,
        "gatewayPoolsAcrossSnapshots": gateway_pools_total,
        "v2vPoolsAcrossSnapshots": v2v_pools_total,
        "accessLinksAcrossSnapshots": access_links_total,
        "activeAccessLinksAcrossSnapshots": active_access_links_total,
        "futureLookAheadViolations": future_lookahead,
        "orphanReferenceViolations": orphan_references,
        "duplicateSnapshotIds": duplicate_snapshot_ids,
        "duplicateCandidateIds": duplicate_candidate_ids,
        "duplicatePoolIds": duplicate_pool_ids,
        "missingLocalCandidates": missing_local_candidates,
        "invalidV2vPairs": invalid_v2v_pairs,
        "unresolvedGatewayPools": unresolved_gateway_pools,
        "unresolvedV2vPools": unresolved_v2v_pools,
        "multipleActiveGatewayViolations": multiple_active_gateway_violations,
        "activeUnavailableLinkViolations": active_unavailable_link_violations,
        "cloudPlaceholderViolations": cloud_placeholder_violations,
        "remoteCandidatePoolConsistencyViolations": remote_candidate_pool_consistency_violations,
        "snapshotJsonParseFailures": snapshot_json_parse_failures,
        "javaLoaderValidationFailures": 0,
        "javaValidatorFailures": 0,
        "manifestFile": str(manifest_out_file),
        "outputDirectory": str(output_dir),
        "warnings": warnings,
        "errors": hard_errors,
        "phase10iStatus": status,
        "readyForPhase10J": ready,
    }
    write_json(validation_out_file, validation)

    print("Phase 10I SystemSnapshot JSON export completed")
    print(f"sourceRun={args.expected_source_run}")
    print(f"snapshotTimelinePolicy={SNAPSHOT_TIMELINE_POLICY}")
    print(f"activeVehicleSetPolicy={ACTIVE_VEHICLE_SET_POLICY}")
    print(f"vehicleLookupPolicy={VEHICLE_LOOKUP_POLICY}")
    print(f"accessLinkLookupPolicy={EXACT_POLICY}")
    print(f"localCandidateLookupPolicy={EXACT_POLICY}")
    print(f"v2vCandidateLookupPolicy={EXACT_POLICY}")
    print(f"remoteCandidateLookupPolicy={EXACT_POLICY}")
    print(f"v2vPoolLookupPolicy={EXACT_POLICY}")
    print(f"gatewayPoolAssemblyPolicy={GATEWAY_POOL_POLICY}")
    print(f"snapshotsGenerated={len(snapshot_files)}")
    print(f"emptyTaskSnapshots={empty_task_snapshots}")
    print(f"totalTasksAcrossSnapshots={len(task_ids_across)}")
    print(f"vehiclesAcrossSnapshots={vehicles_across}")
    print(f"localCandidatesAcrossSnapshots={local_candidates_total}")
    print(f"v2vCandidatesAcrossSnapshots={v2v_candidates_total}")
    print(f"edgeCandidatesAcrossSnapshots={edge_candidates_total}")
    print(f"cloudCandidatesAcrossSnapshots={cloud_candidates_total}")
    print(f"gatewayPoolsAcrossSnapshots={gateway_pools_total}")
    print(f"v2vPoolsAcrossSnapshots={v2v_pools_total}")
    print(f"futureLookAheadViolations={future_lookahead}")
    print(f"orphanReferenceViolations={orphan_references}")
    print(f"duplicateCandidateIds={duplicate_candidate_ids}")
    print(f"duplicatePoolIds={duplicate_pool_ids}")
    print(f"unresolvedGatewayPools={unresolved_gateway_pools}")
    print(f"unresolvedV2vPools={unresolved_v2v_pools}")
    print(f"cloudPlaceholderViolations={cloud_placeholder_violations}")
    print(f"phase10iStatus={status}")
    print(f"readyForPhase10J={str(ready).lower()}")
    print(f"warningsCount={len(warnings)}")
    print(f"errorsCount={len(hard_errors)}")

    return 0 if ready else 1


if __name__ == "__main__":
    raise SystemExit(main())
