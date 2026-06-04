#!/usr/bin/env python3
"""Export diagnostic LOCAL and direct V2V candidate previews for Phase 10G."""

from __future__ import annotations

import argparse
import bisect
import csv
import json
import math
import os
import re
import sys
import tempfile
from collections import Counter
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


LOCAL_COLUMNS = [
    "timeNs",
    "timeSeconds",
    "candidateId",
    "sourceVehicleId",
    "executionNodeId",
    "type",
    "availableCpu",
    "cpuSource",
    "propagationDelaySeconds",
]

V2V_COLUMNS = [
    "timeNs",
    "timeSeconds",
    "candidateId",
    "sourceVehicleId",
    "targetVehicleId",
    "executionNodeId",
    "type",
    "availableCpu",
    "distanceMeters",
    "singlehopRadiusMeters",
    "propagationDelaySeconds",
    "bandwidthPoolId",
    "availableBandwidth",
    "bandwidthSource",
    "radioStateSource",
    "candidatePolicy",
    "distancePolicy",
    "propagationDelayPolicy",
]

POOL_COLUMNS = [
    "timeNs",
    "timeSeconds",
    "bandwidthPoolId",
    "vehicleA",
    "vehicleB",
    "poolType",
    "availableBandwidth",
    "bandwidthSource",
    "poolPolicy",
]

EARTH_RADIUS_METERS = 6_371_000.0
DISTANCE_POLICY = "HAVERSINE_FROM_LAT_LON_DIAGNOSTIC"
LOCAL_TYPE = "LOCAL"
VEHICLE_TYPE = "VEHICLE"
DIRECT_V2V_POOL_TYPE = "DIRECT_V2V"
ACTIVE_RADIO_MODE = "SINGLE"
INACTIVE_RADIO_MODE = "OFF"


class ExportError(Exception):
    """Raised when Phase 10G inputs cannot be exported safely."""


@dataclass(frozen=True)
class VehicleState:
    time_ns: int
    time_seconds: Decimal
    vehicle_id: str
    latitude: Decimal
    longitude: Decimal


@dataclass(frozen=True)
class RadioEvent:
    time_ns: int
    vehicle_id: str
    radio_mode: str
    source_line: int

    @property
    def active(self) -> bool:
        return self.radio_mode == ACTIVE_RADIO_MODE


@dataclass(frozen=True)
class CatalogValues:
    local_cpu: Decimal
    local_cpu_source: str
    v2v_bandwidth: Decimal
    v2v_bandwidth_source: str
    candidate_policy: str
    radio_state_source: str
    pool_policy: str
    propagation_delay_policy: str
    conservative_delay_seconds: Decimal
    radius_source: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export Phase 10G diagnostic LOCAL and direct V2V candidate previews."
    )
    parser.add_argument("--vehicle-state-file", required=True, help="Fase 10B vehicle_state_stream.csv.")
    parser.add_argument("--output-csv", required=True, help="MOSAIC output.csv used to inspect ADHOC_CONFIGURATION.")
    parser.add_argument("--resource-catalog", required=True, help="MA-GA resource catalog JSON.")
    parser.add_argument("--sns-config", required=True, help="MOSAIC SNS sns_config.json.")
    parser.add_argument("--local-out-file", required=True, help="LOCAL candidate preview CSV.")
    parser.add_argument("--v2v-out-file", required=True, help="V2V candidate preview CSV.")
    parser.add_argument("--v2v-pool-out-file", required=True, help="V2V bandwidth pool preview CSV.")
    parser.add_argument("--validation-out-file", required=True, help="Phase 10G validation JSON.")
    parser.add_argument(
        "--catalogs-updated",
        nargs="*",
        default=[],
        help="Optional list of catalog files updated for this diagnostic phase.",
    )
    return parser.parse_args()


def require_file(path: Path, label: str) -> None:
    if not path.exists():
        raise ExportError(f"{label} does not exist: {path}")
    if not path.is_file():
        raise ExportError(f"{label} is not a file: {path}")


def load_json(path: Path, label: str) -> dict[str, Any]:
    require_file(path, label)
    try:
        with path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
    except json.JSONDecodeError as exc:
        raise ExportError(f"{label} is not valid JSON: {path}:{exc.lineno}:{exc.colno}") from exc
    if not isinstance(data, dict):
        raise ExportError(f"{label} root must be a JSON object: {path}")
    return data


def parse_decimal(value: Any, field_name: str, source: str) -> Decimal:
    try:
        parsed = Decimal(str(value).strip())
    except (InvalidOperation, ValueError, AttributeError) as exc:
        raise ExportError(f"{source}: {field_name} is not numeric") from exc
    if not parsed.is_finite():
        raise ExportError(f"{source}: {field_name} must be finite")
    return parsed


def parse_positive_decimal(value: Any, field_name: str, source: str) -> Decimal:
    parsed = parse_decimal(value, field_name, source)
    if parsed <= 0:
        raise ExportError(f"{source}: {field_name} must be > 0")
    return parsed


def parse_non_negative_decimal(value: Any, field_name: str, source: str) -> Decimal:
    parsed = parse_decimal(value, field_name, source)
    if parsed < 0:
        raise ExportError(f"{source}: {field_name} must be >= 0")
    return parsed


def parse_non_negative_int(value: str, field_name: str, source: str) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ExportError(f"{source}: {field_name} is not a valid integer") from exc
    if parsed < 0:
        raise ExportError(f"{source}: {field_name} must be >= 0")
    return parsed


def require_text(value: Any, field_name: str, source: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ExportError(f"{source}: {field_name} must be a non-empty string")
    return value.strip()


def format_decimal_plain(value: Decimal) -> str:
    text = format(value, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def seconds_from_ns(time_ns: int) -> Decimal:
    return Decimal(time_ns) / Decimal(1_000_000_000)


def natural_key(value: str) -> list[Any]:
    return [int(part) if part.isdigit() else part for part in re.split(r"(\d+)", value)]


def bool_from_csv(value: str, field_name: str, source: str) -> bool:
    normalized = value.strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise ExportError(f"{source}: {field_name} must be true or false")


def read_catalog(path: Path) -> CatalogValues:
    catalog = load_json(path, "resource catalog")
    source = str(path)

    profiles = catalog.get("vehicleProfiles")
    if not isinstance(profiles, list):
        raise ExportError(f"{source}: vehicleProfiles must be an array")
    car_default = None
    for profile in profiles:
        if isinstance(profile, dict) and profile.get("profileId") == "car_default":
            car_default = profile
            break
    if car_default is None:
        raise ExportError(f"{source}: vehicleProfiles must contain profileId=car_default")

    local_cpu = parse_positive_decimal(
        car_default.get("localCpuCyclesPerSecond"),
        "vehicleProfiles[car_default].localCpuCyclesPerSecond",
        source,
    )
    local_cpu_source = require_text(
        car_default.get("cpuSource"),
        "vehicleProfiles[car_default].cpuSource",
        source,
    )

    v2v_policy = catalog.get("v2vPolicy")
    if not isinstance(v2v_policy, dict):
        raise ExportError(f"{source}: v2vPolicy must be an object")

    return CatalogValues(
        local_cpu=local_cpu,
        local_cpu_source=local_cpu_source,
        v2v_bandwidth=parse_positive_decimal(
            v2v_policy.get("nominalBandwidthBitsPerSecond"),
            "v2vPolicy.nominalBandwidthBitsPerSecond",
            source,
        ),
        v2v_bandwidth_source=require_text(
            v2v_policy.get("bandwidthSource"),
            "v2vPolicy.bandwidthSource",
            source,
        ),
        candidate_policy=require_text(
            v2v_policy.get("candidatePolicy"),
            "v2vPolicy.candidatePolicy",
            source,
        ),
        radio_state_source=require_text(
            v2v_policy.get("radioStateSource"),
            "v2vPolicy.radioStateSource",
            source,
        ),
        pool_policy=require_text(
            v2v_policy.get("poolPolicy"),
            "v2vPolicy.poolPolicy",
            source,
        ),
        propagation_delay_policy=require_text(
            v2v_policy.get("propagationDelayPolicy"),
            "v2vPolicy.propagationDelayPolicy",
            source,
        ),
        conservative_delay_seconds=parse_non_negative_decimal(
            v2v_policy.get("conservativePropagationDelaySeconds"),
            "v2vPolicy.conservativePropagationDelaySeconds",
            source,
        ),
        radius_source=require_text(
            v2v_policy.get("radiusSource"),
            "v2vPolicy.radiusSource",
            source,
        ),
    )


def validate_catalog_values(values: CatalogValues) -> None:
    expected = {
        "candidatePolicy": "DIRECT_SINGLEHOP_ONLY",
        "radioStateSource": "ADHOC_CONFIGURATION",
        "poolPolicy": "ONE_SHARED_POOL_PER_UNORDERED_PAIR",
        "propagationDelayPolicy": "SNS_SINGLEHOP_MAX_DELAY",
    }
    actual = {
        "candidatePolicy": values.candidate_policy,
        "radioStateSource": values.radio_state_source,
        "poolPolicy": values.pool_policy,
        "propagationDelayPolicy": values.propagation_delay_policy,
    }
    for field_name, expected_value in expected.items():
        if actual[field_name] != expected_value:
            raise ExportError(f"{field_name} must be {expected_value}, got {actual[field_name]}")


def read_singlehop_radius(path: Path) -> Decimal:
    sns = load_json(path, "SNS config")
    return parse_positive_decimal(sns.get("singlehopRadius"), "singlehopRadius", str(path))


def read_vehicle_states(path: Path) -> list[VehicleState]:
    require_file(path, "vehicle state CSV")
    required_columns = {"timeNs", "timeSeconds", "vehicleId", "latitude", "longitude", "active"}
    states: list[VehicleState] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None:
            raise ExportError(f"{path}: missing CSV header")
        missing = sorted(required_columns - set(reader.fieldnames))
        if missing:
            raise ExportError(f"{path}: missing required columns: {', '.join(missing)}")
        for line_number, row in enumerate(reader, start=2):
            source = f"{path}:{line_number}"
            active = bool_from_csv(row.get("active") or "", "active", source)
            if not active:
                continue
            time_ns = parse_non_negative_int((row.get("timeNs") or "").strip(), "timeNs", source)
            time_seconds = parse_non_negative_decimal((row.get("timeSeconds") or "").strip(), "timeSeconds", source)
            expected_seconds = seconds_from_ns(time_ns)
            if time_seconds != expected_seconds:
                raise ExportError(f"{source}: timeSeconds does not match timeNs")
            vehicle_id = require_text(row.get("vehicleId"), "vehicleId", source)
            latitude = parse_decimal(row.get("latitude"), "latitude", source)
            longitude = parse_decimal(row.get("longitude"), "longitude", source)
            if latitude < Decimal("-90") or latitude > Decimal("90"):
                raise ExportError(f"{source}: latitude out of range")
            if longitude < Decimal("-180") or longitude > Decimal("180"):
                raise ExportError(f"{source}: longitude out of range")
            states.append(
                VehicleState(
                    time_ns=time_ns,
                    time_seconds=time_seconds,
                    vehicle_id=vehicle_id,
                    latitude=latitude,
                    longitude=longitude,
                )
            )
    if not states:
        raise ExportError(f"{path}: no active vehicle states found")
    return sorted(states, key=lambda item: (item.time_ns, natural_key(item.vehicle_id)))


def read_radio_events(path: Path) -> tuple[list[RadioEvent], list[str]]:
    require_file(path, "MOSAIC output CSV")
    events: list[RadioEvent] = []
    warnings: list[str] = []
    with path.open("r", encoding="utf-8", errors="replace", newline="") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.rstrip("\r\n")
            if not line:
                continue
            fields = line.split(";")
            if not fields or fields[0] != "ADHOC_CONFIGURATION":
                continue
            if len(fields) < 4:
                warnings.append(
                    f"{path}:{line_number}: ADHOC_CONFIGURATION has fewer than 4 fields"
                )
                continue
            try:
                time_ns = parse_non_negative_int(fields[1].strip(), "timeNs", f"{path}:{line_number}")
                vehicle_id = require_text(fields[2], "vehicleId", f"{path}:{line_number}")
                radio_mode = require_text(fields[3], "radioMode", f"{path}:{line_number}")
            except ExportError as exc:
                warnings.append(str(exc))
                continue
            if radio_mode not in {ACTIVE_RADIO_MODE, INACTIVE_RADIO_MODE}:
                warnings.append(
                    f"{path}:{line_number}: unsupported ADHOC radio mode {radio_mode!r}"
                )
                continue
            events.append(
                RadioEvent(
                    time_ns=time_ns,
                    vehicle_id=vehicle_id,
                    radio_mode=radio_mode,
                    source_line=line_number,
                )
            )
    events.sort(key=lambda item: (item.vehicle_id, item.time_ns, item.source_line))
    return events, warnings


def build_local_rows(states: list[VehicleState], catalog: CatalogValues) -> tuple[list[dict[str, str]], int]:
    rows: list[dict[str, str]] = []
    seen: set[tuple[int, str]] = set()
    duplicate_count = 0
    for state in states:
        candidate_id = f"local_for_{state.vehicle_id}"
        key = (state.time_ns, candidate_id)
        if key in seen:
            duplicate_count += 1
        seen.add(key)
        rows.append(
            {
                "timeNs": str(state.time_ns),
                "timeSeconds": format_decimal_plain(state.time_seconds),
                "candidateId": candidate_id,
                "sourceVehicleId": state.vehicle_id,
                "executionNodeId": state.vehicle_id,
                "type": LOCAL_TYPE,
                "availableCpu": format_decimal_plain(catalog.local_cpu),
                "cpuSource": catalog.local_cpu_source,
                "propagationDelaySeconds": "0",
            }
        )
    return rows, duplicate_count


def haversine_distance_meters(a: VehicleState, b: VehicleState) -> Decimal:
    lat_a = float(a.latitude)
    lon_a = float(a.longitude)
    lat_b = float(b.latitude)
    lon_b = float(b.longitude)
    phi_a = math.radians(lat_a)
    phi_b = math.radians(lat_b)
    delta_phi = math.radians(lat_b - lat_a)
    delta_lambda = math.radians(lon_b - lon_a)
    sin_delta_phi = math.sin(delta_phi / 2.0)
    sin_delta_lambda = math.sin(delta_lambda / 2.0)
    value = (
        sin_delta_phi * sin_delta_phi
        + math.cos(phi_a) * math.cos(phi_b) * sin_delta_lambda * sin_delta_lambda
    )
    central_angle = 2.0 * math.atan2(math.sqrt(value), math.sqrt(max(0.0, 1.0 - value)))
    return Decimal(str(EARTH_RADIUS_METERS * central_angle))


def pool_id_for_pair(vehicle_a: str, vehicle_b: str) -> tuple[str, str, str]:
    ordered = sorted([vehicle_a, vehicle_b], key=natural_key)
    return f"pool_v2v_{ordered[0]}_{ordered[1]}", ordered[0], ordered[1]


def group_states_by_time(states: list[VehicleState]) -> dict[int, list[VehicleState]]:
    grouped: dict[int, list[VehicleState]] = {}
    for state in states:
        grouped.setdefault(state.time_ns, []).append(state)
    return {
        time_ns: sorted(time_states, key=lambda item: natural_key(item.vehicle_id))
        for time_ns, time_states in sorted(grouped.items())
    }


def index_radio_events(events: list[RadioEvent]) -> dict[str, list[RadioEvent]]:
    indexed: dict[str, list[RadioEvent]] = {}
    seen: set[tuple[str, int, str]] = set()
    for event in events:
        key = (event.vehicle_id, event.time_ns, event.radio_mode)
        if key in seen:
            continue
        seen.add(key)
        indexed.setdefault(event.vehicle_id, []).append(event)
    for vehicle_events in indexed.values():
        vehicle_events.sort(key=lambda item: item.time_ns)
    return indexed


def latest_radio_event(index: dict[str, list[RadioEvent]], vehicle_id: str, time_ns: int) -> RadioEvent | None:
    events = index.get(vehicle_id)
    if not events:
        return None
    times = [event.time_ns for event in events]
    position = bisect.bisect_right(times, time_ns) - 1
    if position < 0:
        return None
    return events[position]


def build_v2v_rows(
    states: list[VehicleState],
    radio_events: list[RadioEvent],
    catalog: CatalogValues,
    singlehop_radius: Decimal,
) -> tuple[list[dict[str, str]], list[dict[str, str]], dict[str, int], list[Decimal]]:
    grouped_states = group_states_by_time(states)
    radio_index = index_radio_events(radio_events)
    candidate_rows: list[dict[str, str]] = []
    pool_rows: list[dict[str, str]] = []
    distances: list[Decimal] = []
    counters: Counter[str] = Counter()
    seen_candidates: set[tuple[int, str]] = set()
    seen_pools: dict[tuple[int, str], tuple[str, str]] = {}

    for time_ns, time_states in grouped_states.items():
        if len(time_states) < 2:
            continue
        for left_index in range(len(time_states)):
            for right_index in range(left_index + 1, len(time_states)):
                left = time_states[left_index]
                right = time_states[right_index]
                counters["unorderedPairsEvaluated"] += 1
                left_radio = latest_radio_event(radio_index, left.vehicle_id, time_ns)
                right_radio = latest_radio_event(radio_index, right.vehicle_id, time_ns)
                if left_radio is None or right_radio is None:
                    counters["inactiveRadioViolations"] += 1
                    continue
                if left_radio.time_ns > time_ns or right_radio.time_ns > time_ns:
                    counters["futureLookAheadViolations"] += 1
                    continue
                if not left_radio.active or not right_radio.active:
                    counters["inactiveRadioViolations"] += 1
                    continue
                distance = haversine_distance_meters(left, right)
                if distance > singlehop_radius:
                    continue
                counters["directReachablePairs"] += 1
                pool_id, vehicle_a, vehicle_b = pool_id_for_pair(left.vehicle_id, right.vehicle_id)
                pool_key = (time_ns, pool_id)
                pair = (vehicle_a, vehicle_b)
                if pool_key in seen_pools and seen_pools[pool_key] != pair:
                    counters["ambiguousBandwidthPoolIds"] += 1
                    continue
                seen_pools[pool_key] = pair
                pool_rows.append(
                    {
                        "timeNs": str(time_ns),
                        "timeSeconds": format_decimal_plain(left.time_seconds),
                        "bandwidthPoolId": pool_id,
                        "vehicleA": vehicle_a,
                        "vehicleB": vehicle_b,
                        "poolType": DIRECT_V2V_POOL_TYPE,
                        "availableBandwidth": format_decimal_plain(catalog.v2v_bandwidth),
                        "bandwidthSource": catalog.v2v_bandwidth_source,
                        "poolPolicy": catalog.pool_policy,
                    }
                )
                for source_state, target_state in ((left, right), (right, left)):
                    if source_state.vehicle_id == target_state.vehicle_id:
                        counters["selfCandidateViolations"] += 1
                        continue
                    candidate_id = f"vehicle_{target_state.vehicle_id}_v2v_for_{source_state.vehicle_id}"
                    candidate_key = (time_ns, candidate_id)
                    if candidate_key in seen_candidates:
                        counters["duplicateV2vCandidateIds"] += 1
                        continue
                    seen_candidates.add(candidate_key)
                    if distance > singlehop_radius:
                        counters["radiusViolations"] += 1
                        continue
                    candidate_rows.append(
                        {
                            "timeNs": str(time_ns),
                            "timeSeconds": format_decimal_plain(source_state.time_seconds),
                            "candidateId": candidate_id,
                            "sourceVehicleId": source_state.vehicle_id,
                            "targetVehicleId": target_state.vehicle_id,
                            "executionNodeId": target_state.vehicle_id,
                            "type": VEHICLE_TYPE,
                            "availableCpu": format_decimal_plain(catalog.local_cpu),
                            "distanceMeters": format_decimal_plain(distance.quantize(Decimal("0.000001"))),
                            "singlehopRadiusMeters": format_decimal_plain(singlehop_radius),
                            "propagationDelaySeconds": format_decimal_plain(catalog.conservative_delay_seconds),
                            "bandwidthPoolId": pool_id,
                            "availableBandwidth": format_decimal_plain(catalog.v2v_bandwidth),
                            "bandwidthSource": catalog.v2v_bandwidth_source,
                            "radioStateSource": catalog.radio_state_source,
                            "candidatePolicy": catalog.candidate_policy,
                            "distancePolicy": DISTANCE_POLICY,
                            "propagationDelayPolicy": catalog.propagation_delay_policy,
                        }
                    )
                    distances.append(distance)

    candidate_rows.sort(
        key=lambda row: (
            int(row["timeNs"]),
            natural_key(row["sourceVehicleId"]),
            natural_key(row["targetVehicleId"]),
        )
    )
    pool_rows.sort(
        key=lambda row: (
            int(row["timeNs"]),
            natural_key(row["vehicleA"]),
            natural_key(row["vehicleB"]),
        )
    )
    return candidate_rows, pool_rows, dict(counters), distances


def write_csv_atomic(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            newline="",
            dir=path.parent,
            delete=False,
            prefix=f".{path.name}.",
            suffix=".tmp",
        ) as handle:
            temp_name = handle.name
            writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="raise", lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)
        os.replace(temp_name, path)
        temp_name = None
    finally:
        if temp_name is not None and os.path.exists(temp_name):
            os.unlink(temp_name)


def write_json_atomic(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            newline="\n",
            dir=path.parent,
            delete=False,
            prefix=f".{path.name}.",
            suffix=".tmp",
        ) as handle:
            temp_name = handle.name
            json.dump(payload, handle, indent=2, sort_keys=True)
            handle.write("\n")
        os.replace(temp_name, path)
        temp_name = None
    finally:
        if temp_name is not None and os.path.exists(temp_name):
            os.unlink(temp_name)


def distribution_by(rows: list[dict[str, str]], column: str) -> dict[str, int]:
    return dict(sorted(Counter(row[column] for row in rows).items(), key=lambda item: natural_key(item[0])))


def build_validation(
    *,
    source_run: str,
    catalog: CatalogValues,
    singlehop_radius: Decimal,
    vehicle_states: list[VehicleState],
    local_rows: list[dict[str, str]],
    v2v_rows: list[dict[str, str]],
    pool_rows: list[dict[str, str]],
    radio_events: list[RadioEvent],
    radio_warnings: list[str],
    v2v_counters: dict[str, int],
    duplicate_local_candidates: int,
    generated_files: list[Path],
    catalogs_updated: list[str],
) -> dict[str, Any]:
    warnings: list[str] = []
    errors: list[str] = []
    if radio_warnings:
        warnings.extend(radio_warnings)
    v2v_status = "COMPLETED"
    if not radio_events:
        v2v_status = "SKIPPED_MISSING_RADIO_EVENTS"
        errors.append(
            "No ADHOC_CONFIGURATION events were found in the baseline output.csv; V2V candidates were not generated."
        )
        warnings.append("LOCAL candidates were exported; direct V2V generation requires observed ad-hoc radio state.")

    times = {state.time_ns for state in vehicle_states}
    vehicles_with_radio = {event.vehicle_id for event in radio_events}

    validation = {
        "sourceRun": source_run,
        "localCpuCyclesPerSecond": decimal_json_value(catalog.local_cpu),
        "localCpuSource": catalog.local_cpu_source,
        "v2vNominalBandwidthBitsPerSecond": decimal_json_value(catalog.v2v_bandwidth),
        "v2vBandwidthSource": catalog.v2v_bandwidth_source,
        "singlehopRadiusMeters": decimal_json_value(singlehop_radius),
        "conservativePropagationDelaySeconds": decimal_json_value(catalog.conservative_delay_seconds),
        "distancePolicy": DISTANCE_POLICY,
        "radioStateSource": catalog.radio_state_source,
        "candidatePolicy": catalog.candidate_policy,
        "poolPolicy": catalog.pool_policy,
        "vehicleStatesRead": len(vehicle_states),
        "localCandidatesExported": len(local_rows),
        "v2vCandidatesExported": len(v2v_rows),
        "v2vPoolsExported": len(pool_rows),
        "futureLookAheadViolations": v2v_counters.get("futureLookAheadViolations", 0),
        "warnings": sorted(set(warnings)),
        "errors": errors,
        "v2vGenerationStatus": v2v_status,
        "radioEventsRead": len(radio_events),
        "vehiclesWithRadioEvents": len(vehicles_with_radio),
        "timestampsEvaluated": len(times) if radio_events else 0,
        "unorderedPairsEvaluated": v2v_counters.get("unorderedPairsEvaluated", 0),
        "directReachablePairs": v2v_counters.get("directReachablePairs", 0),
        "selfCandidateViolations": v2v_counters.get("selfCandidateViolations", 0),
        "radiusViolations": v2v_counters.get("radiusViolations", 0),
        "inactiveRadioViolations": v2v_counters.get("inactiveRadioViolations", 0),
        "duplicateLocalCandidateIds": duplicate_local_candidates,
        "duplicateV2vCandidateIds": v2v_counters.get("duplicateV2vCandidateIds", 0),
        "ambiguousBandwidthPoolIds": v2v_counters.get("ambiguousBandwidthPoolIds", 0),
        "poolDirectionConsistencyViolations": v2v_counters.get("poolDirectionConsistencyViolations", 0),
        "catalogsUpdated": catalogs_updated,
        "generatedFiles": [str(path) for path in generated_files],
        "localDistributionByVehicle": distribution_by(local_rows, "sourceVehicleId"),
        "v2vDistributionBySourceVehicleId": distribution_by(v2v_rows, "sourceVehicleId"),
        "v2vDistributionByTargetVehicleId": distribution_by(v2v_rows, "targetVehicleId"),
        "calibrationStatus": "TO_BE_REPLACED_DURING_RESOURCE_CALIBRATION",
    }
    return validation


def decimal_json_value(value: Decimal) -> int | float:
    if value == value.to_integral_value():
        return int(value)
    return float(value)


def run() -> None:
    args = parse_args()
    vehicle_state_file = Path(args.vehicle_state_file)
    output_csv = Path(args.output_csv)
    resource_catalog = Path(args.resource_catalog)
    sns_config = Path(args.sns_config)
    local_out_file = Path(args.local_out_file)
    v2v_out_file = Path(args.v2v_out_file)
    v2v_pool_out_file = Path(args.v2v_pool_out_file)
    validation_out_file = Path(args.validation_out_file)

    catalog = read_catalog(resource_catalog)
    validate_catalog_values(catalog)
    singlehop_radius = read_singlehop_radius(sns_config)
    vehicle_states = read_vehicle_states(vehicle_state_file)
    radio_events, radio_warnings = read_radio_events(output_csv)

    local_rows, duplicate_local_candidates = build_local_rows(vehicle_states, catalog)
    if radio_events:
        v2v_rows, pool_rows, v2v_counters, _distances = build_v2v_rows(
            vehicle_states,
            radio_events,
            catalog,
            singlehop_radius,
        )
    else:
        v2v_rows = []
        pool_rows = []
        v2v_counters = {}

    write_csv_atomic(local_out_file, LOCAL_COLUMNS, local_rows)
    write_csv_atomic(v2v_out_file, V2V_COLUMNS, v2v_rows)
    write_csv_atomic(v2v_pool_out_file, POOL_COLUMNS, pool_rows)

    generated_files = [
        local_out_file,
        v2v_out_file,
        v2v_pool_out_file,
        validation_out_file,
    ]
    source_run = output_csv.parent.name
    validation = build_validation(
        source_run=source_run,
        catalog=catalog,
        singlehop_radius=singlehop_radius,
        vehicle_states=vehicle_states,
        local_rows=local_rows,
        v2v_rows=v2v_rows,
        pool_rows=pool_rows,
        radio_events=radio_events,
        radio_warnings=radio_warnings,
        v2v_counters=v2v_counters,
        duplicate_local_candidates=duplicate_local_candidates,
        generated_files=generated_files,
        catalogs_updated=[str(Path(item)) for item in args.catalogs_updated],
    )
    write_json_atomic(validation_out_file, validation)

    print("Phase 10G local and direct V2V candidate preview export completed")
    print(f"vehicleStateFile={vehicle_state_file}")
    print(f"outputCsv={output_csv}")
    print(f"resourceCatalog={resource_catalog}")
    print(f"snsConfig={sns_config}")
    print(f"sourceRun={source_run}")
    print(f"vehicleStatesRead={len(vehicle_states)}")
    print(f"localCandidatesExported={len(local_rows)}")
    print(f"radioEventsRead={len(radio_events)}")
    print(f"vehiclesWithRadioEvents={len({event.vehicle_id for event in radio_events})}")
    print(f"v2vGenerationStatus={validation['v2vGenerationStatus']}")
    print(f"v2vCandidatesExported={len(v2v_rows)}")
    print(f"v2vPoolsExported={len(pool_rows)}")
    print(f"localCpuCyclesPerSecond={format_decimal_plain(catalog.local_cpu)}")
    print(f"localCpuSource={catalog.local_cpu_source}")
    print(f"v2vNominalBandwidthBitsPerSecond={format_decimal_plain(catalog.v2v_bandwidth)}")
    print(f"v2vBandwidthSource={catalog.v2v_bandwidth_source}")
    print(f"singlehopRadiusMeters={format_decimal_plain(singlehop_radius)}")
    print(f"conservativePropagationDelaySeconds={format_decimal_plain(catalog.conservative_delay_seconds)}")
    print(f"distancePolicy={DISTANCE_POLICY}")
    print(f"radioStateSource={catalog.radio_state_source}")
    print(f"candidatePolicy={catalog.candidate_policy}")
    print(f"poolPolicy={catalog.pool_policy}")
    print(f"futureLookAheadViolations={validation['futureLookAheadViolations']}")
    print(f"warningsCount={len(validation['warnings'])}")
    print("warnings:")
    for warning in validation["warnings"]:
        print(f"  {warning}")
    print(f"errorsCount={len(validation['errors'])}")
    print("errors:")
    for error in validation["errors"]:
        print(f"  {error}")
    print(f"localOutFile={local_out_file}")
    print(f"v2vOutFile={v2v_out_file}")
    print(f"v2vPoolOutFile={v2v_pool_out_file}")
    print(f"validationOutFile={validation_out_file}")


def main() -> int:
    try:
        run()
    except ExportError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
