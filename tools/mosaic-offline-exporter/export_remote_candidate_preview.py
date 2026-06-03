#!/usr/bin/env python3
"""Export diagnostic EDGE and CLOUD candidate previews from active access links."""

from __future__ import annotations

import argparse
import bisect
import csv
import json
import os
import re
import sys
import tempfile
from collections import Counter
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


THROUGH_ACTIVE_GATEWAY = "THROUGH_ACTIVE_GATEWAY"
BANDWIDTH_POLICY = "MIN_RESIDUAL_CELL_UPLINK_DOWNLINK_DIAGNOSTIC"
BANDWIDTH_SOURCE = "CELL_BANDWIDTH_STREAM_RESIDUAL"
BANDWIDTH_LOOKUP_POLICY = "LATEST_SAFE_AVAILABLE_CELL_BUCKET"
PROPAGATION_DELAY_POLICY = "MAX_CELL_UPLINK_DOWNLINK_UNICAST_PLUS_NODE_BASE_DIAGNOSTIC"


CSV_COLUMNS = [
    "timeNs",
    "timeSeconds",
    "candidateId",
    "sourceVehicleId",
    "executionNodeId",
    "type",
    "availableCpu",
    "availableBandwidth",
    "propagationDelaySeconds",
    "regionalRadioDelaySeconds",
    "nodeBaseDelaySeconds",
    "bandwidthPoolId",
    "gatewayId",
    "runtimeGatewayId",
    "cellRegionId",
    "bandwidthMeasurementTimeSeconds",
    "bandwidthAgeSeconds",
    "uplinkResidualBandwidth",
    "downlinkResidualBandwidth",
    "bandwidthPolicy",
    "bandwidthSource",
    "bandwidthLookupPolicy",
    "bucketBoundaryPolicy",
    "propagationDelayPolicy",
]


class ExportError(Exception):
    """Raised when remote candidate preview cannot be exported safely."""


@dataclass(frozen=True)
class AccessLink:
    time_ns: int
    time_seconds: Decimal
    vehicle_id: str
    gateway_id: str
    runtime_gateway_id: str
    bandwidth_pool_id: str
    active: bool
    available: bool


@dataclass(frozen=True)
class Gateway:
    gateway_id: str
    runtime_id: str
    bandwidth_pool_id: str
    cell_region_id: str


@dataclass(frozen=True)
class BandwidthPool:
    pool_id: str
    nominal_bandwidth: Decimal


@dataclass(frozen=True)
class ExecutionNode:
    execution_node_id: str
    node_type: str
    available_cpu: Decimal
    base_delay_seconds: Decimal
    gateway_ids: tuple[str, ...] = ()
    access_policy: str = ""


@dataclass(frozen=True)
class CellBandwidthRecord:
    measurement_time_seconds: Decimal
    available_from_time_seconds: Decimal
    region_id: str
    direction: str
    residual_capacity: Decimal
    bucket_boundary_policy: str


@dataclass(frozen=True)
class BandwidthSelection:
    measurement_time_seconds: Decimal
    bandwidth_age_seconds: Decimal
    uplink_residual: Decimal
    downlink_residual: Decimal
    available_bandwidth: Decimal
    bucket_boundary_policy: str


@dataclass(frozen=True)
class CandidatePreview:
    time_ns: int
    time_seconds: Decimal
    source_vehicle_id: str
    execution_node_id: str
    node_type: str
    available_cpu: Decimal
    bandwidth_selection: BandwidthSelection
    propagation_delay_seconds: Decimal
    regional_radio_delay_seconds: Decimal
    node_base_delay_seconds: Decimal
    bandwidth_pool_id: str
    gateway_id: str
    runtime_gateway_id: str
    cell_region_id: str

    @property
    def candidate_id(self) -> str:
        return f"{self.execution_node_id}_for_{self.source_vehicle_id}"

    def to_csv_row(self) -> dict[str, str]:
        return {
            "timeNs": str(self.time_ns),
            "timeSeconds": format_decimal_plain(self.time_seconds),
            "candidateId": self.candidate_id,
            "sourceVehicleId": self.source_vehicle_id,
            "executionNodeId": self.execution_node_id,
            "type": self.node_type,
            "availableCpu": format_decimal_plain(self.available_cpu),
            "availableBandwidth": format_decimal_plain(self.bandwidth_selection.available_bandwidth),
            "propagationDelaySeconds": format_decimal_plain(self.propagation_delay_seconds),
            "regionalRadioDelaySeconds": format_decimal_plain(self.regional_radio_delay_seconds),
            "nodeBaseDelaySeconds": format_decimal_plain(self.node_base_delay_seconds),
            "bandwidthPoolId": self.bandwidth_pool_id,
            "gatewayId": self.gateway_id,
            "runtimeGatewayId": self.runtime_gateway_id,
            "cellRegionId": self.cell_region_id,
            "bandwidthMeasurementTimeSeconds": format_decimal_plain(
                self.bandwidth_selection.measurement_time_seconds
            ),
            "bandwidthAgeSeconds": format_decimal_plain(self.bandwidth_selection.bandwidth_age_seconds),
            "uplinkResidualBandwidth": format_decimal_plain(self.bandwidth_selection.uplink_residual),
            "downlinkResidualBandwidth": format_decimal_plain(self.bandwidth_selection.downlink_residual),
            "bandwidthPolicy": BANDWIDTH_POLICY,
            "bandwidthSource": BANDWIDTH_SOURCE,
            "bandwidthLookupPolicy": BANDWIDTH_LOOKUP_POLICY,
            "bucketBoundaryPolicy": self.bandwidth_selection.bucket_boundary_policy,
            "propagationDelayPolicy": PROPAGATION_DELAY_POLICY,
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export diagnostic EDGE and CLOUD candidates from active gateway access links."
    )
    parser.add_argument("--access-link-file", required=True, help="Fase 10E access_link_preview.csv.")
    parser.add_argument("--infrastructure-snapshot", required=True, help="Fase 10C infrastructure_snapshot.json.")
    parser.add_argument("--cell-bandwidth-stream", required=True, help="Fase 10D cell_bandwidth_stream.csv.")
    parser.add_argument("--out-file", required=True, help="Remote candidate preview CSV to write.")
    return parser.parse_args()


def require_file(path: Path, label: str) -> None:
    if not path.exists():
        raise ExportError(f"{label} does not exist: {path}")
    if not path.is_file():
        raise ExportError(f"{label} is not a file: {path}")


def load_json_file(path: Path, label: str) -> dict[str, Any]:
    require_file(path, label)
    try:
        with path.open("r", encoding="utf-8") as handle:
            loaded = json.load(handle)
    except json.JSONDecodeError as exc:
        raise ExportError(f"{label} is not valid JSON: {path}:{exc.lineno}:{exc.colno}") from exc
    if not isinstance(loaded, dict):
        raise ExportError(f"{label} root must be a JSON object: {path}")
    return loaded


def natural_key(value: str) -> list[Any]:
    return [int(part) if part.isdigit() else part for part in re.split(r"(\d+)", value)]


def type_order(node_type: str) -> int:
    if node_type == "EDGE":
        return 0
    if node_type == "CLOUD":
        return 1
    return 2


def format_seconds_from_ns(time_ns: int) -> str:
    return format_decimal_plain(Decimal(time_ns) / Decimal(1_000_000_000))


def format_decimal_plain(value: Decimal) -> str:
    text = format(value, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def parse_int(value: str, field_name: str, source: str) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ExportError(f"{source}: {field_name} is not a valid integer") from exc
    if parsed < 0:
        raise ExportError(f"{source}: {field_name} must be >= 0")
    return parsed


def parse_decimal(value: Any, field_name: str, source: str) -> Decimal:
    try:
        parsed = Decimal(str(value).strip())
    except (InvalidOperation, ValueError) as exc:
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


def parse_bool(value: str, field_name: str, source: str) -> bool:
    stripped = value.strip().lower()
    if stripped == "true":
        return True
    if stripped == "false":
        return False
    raise ExportError(f"{source}: {field_name} must be true or false")


def parse_delay_seconds(value: Any, source: str) -> Decimal:
    if not isinstance(value, str):
        raise ExportError(f"{source}: delay must be a string")
    match = re.fullmatch(
        r"\s*([+]?(?:\d+(?:\.\d*)?|\.\d+))\s*(ns|us|\u00b5s|\u03bcs|ms|s)\s*",
        value,
    )
    if not match:
        raise ExportError(f"{source}: delay uses an unrecognized unit: {value}")
    amount = parse_non_negative_decimal(match.group(1), "delay", source)
    unit = match.group(2)
    factors = {
        "ns": Decimal("0.000000001"),
        "us": Decimal("0.000001"),
        "\u00b5s": Decimal("0.000001"),
        "\u03bcs": Decimal("0.000001"),
        "ms": Decimal("0.001"),
        "s": Decimal("1"),
    }
    return amount * factors[unit]


def get_region_id(region: dict[str, Any], index: int) -> str:
    region_id = str(region.get("regionId") or region.get("id") or "").strip()
    if not region_id:
        raise ExportError(f"infrastructure snapshot cell.regions[{index}] must contain regionId or id")
    return region_id


def read_infrastructure(
    path: Path,
) -> tuple[dict[str, Gateway], dict[str, BandwidthPool], list[ExecutionNode], dict[str, Decimal], set[str]]:
    infrastructure = load_json_file(path, "infrastructure snapshot")

    gateway_items = infrastructure.get("gateways")
    if not isinstance(gateway_items, list):
        raise ExportError("infrastructure snapshot gateways must be a list")
    gateways: dict[str, Gateway] = {}
    runtime_gateway_ids: set[str] = set()
    for index, item in enumerate(gateway_items):
        if not isinstance(item, dict):
            raise ExportError(f"infrastructure snapshot gateways[{index}] must be an object")
        source = f"infrastructure snapshot gateways[{index}]"
        gateway_id = str(item.get("gatewayId", "")).strip()
        runtime_id = str(item.get("runtimeId", "")).strip()
        bandwidth_pool_id = str(item.get("bandwidthPoolId", "")).strip()
        cell_region_id = str(item.get("cellRegionId", "")).strip()
        if not gateway_id:
            raise ExportError(f"{source}: gatewayId is empty")
        if gateway_id in gateways:
            raise ExportError(f"{source}: duplicate gatewayId: {gateway_id}")
        if not runtime_id:
            raise ExportError(f"{source}: runtimeId is empty")
        if runtime_id in runtime_gateway_ids:
            raise ExportError(f"{source}: duplicate runtimeId: {runtime_id}")
        runtime_gateway_ids.add(runtime_id)
        if not bandwidth_pool_id:
            raise ExportError(f"{source}: bandwidthPoolId is empty")
        if not cell_region_id:
            raise ExportError(f"{source}: cellRegionId is empty")
        gateways[gateway_id] = Gateway(
            gateway_id=gateway_id,
            runtime_id=runtime_id,
            bandwidth_pool_id=bandwidth_pool_id,
            cell_region_id=cell_region_id,
        )

    pool_items = infrastructure.get("bandwidthPools")
    if not isinstance(pool_items, list):
        raise ExportError("infrastructure snapshot bandwidthPools must be a list")
    pools: dict[str, BandwidthPool] = {}
    for index, item in enumerate(pool_items):
        if not isinstance(item, dict):
            raise ExportError(f"infrastructure snapshot bandwidthPools[{index}] must be an object")
        source = f"infrastructure snapshot bandwidthPools[{index}]"
        pool_id = str(item.get("poolId", "")).strip()
        if not pool_id:
            raise ExportError(f"{source}: poolId is empty")
        if pool_id in pools:
            raise ExportError(f"{source}: duplicate poolId: {pool_id}")
        nominal = parse_positive_decimal(
            item.get("nominalBandwidthBitsPerSecond"),
            "nominalBandwidthBitsPerSecond",
            source,
        )
        pools[pool_id] = BandwidthPool(pool_id=pool_id, nominal_bandwidth=nominal)

    for gateway in gateways.values():
        if gateway.bandwidth_pool_id not in pools:
            raise ExportError(f"gateway {gateway.gateway_id}: bandwidthPoolId does not reference an existing pool")

    cell = infrastructure.get("cell")
    if not isinstance(cell, dict):
        raise ExportError("infrastructure snapshot cell must be an object")
    region_items = cell.get("regions")
    if not isinstance(region_items, list):
        raise ExportError("infrastructure snapshot cell.regions must be a list")
    regional_radio_delays: dict[str, Decimal] = {}
    cell_region_ids: set[str] = set()
    for index, region in enumerate(region_items):
        if not isinstance(region, dict):
            raise ExportError(f"infrastructure snapshot cell.regions[{index}] must be an object")
        region_id = get_region_id(region, index)
        if region_id in regional_radio_delays:
            raise ExportError(f"infrastructure snapshot cell.regions[{index}]: duplicate region id {region_id}")
        cell_region_ids.add(region_id)
        network = region.get("network") if isinstance(region.get("network"), dict) else region
        source = f"infrastructure snapshot cell.regions[{region_id}]"
        uplink_delay_value = (((network.get("uplink") or {}).get("delay") or {}).get("delay"))
        downlink_delay_value = (((network.get("downlink") or {}).get("unicast") or {}).get("delay") or {}).get("delay")
        if uplink_delay_value is None:
            raise ExportError(f"{source}: uplink.delay.delay is missing")
        if downlink_delay_value is None:
            raise ExportError(f"{source}: downlink.unicast.delay.delay is missing")
        uplink_delay = parse_delay_seconds(uplink_delay_value, f"{source}: uplink.delay.delay")
        downlink_delay = parse_delay_seconds(downlink_delay_value, f"{source}: downlink.unicast.delay.delay")
        regional_radio_delays[region_id] = max(uplink_delay, downlink_delay)

    for gateway in gateways.values():
        if gateway.cell_region_id not in regional_radio_delays:
            raise ExportError(f"gateway {gateway.gateway_id}: cellRegionId does not exist in cell.regions")

    node_items = infrastructure.get("executionNodes")
    if not isinstance(node_items, list):
        raise ExportError("infrastructure snapshot executionNodes must be a list")
    execution_nodes: list[ExecutionNode] = []
    execution_node_ids: set[str] = set()
    for index, item in enumerate(node_items):
        if not isinstance(item, dict):
            raise ExportError(f"infrastructure snapshot executionNodes[{index}] must be an object")
        source = f"infrastructure snapshot executionNodes[{index}]"
        execution_node_id = str(item.get("executionNodeId", "")).strip()
        if not execution_node_id:
            raise ExportError(f"{source}: executionNodeId is empty")
        if execution_node_id in execution_node_ids:
            raise ExportError(f"{source}: duplicate executionNodeId: {execution_node_id}")
        execution_node_ids.add(execution_node_id)
        node_type = str(item.get("type", "")).strip()
        if node_type == "EDGE":
            gateway_ids_raw = item.get("gatewayIds")
            if not isinstance(gateway_ids_raw, list) or not gateway_ids_raw:
                raise ExportError(f"{source}: EDGE node must contain at least one gatewayId")
            gateway_ids: list[str] = []
            for gateway_id_raw in gateway_ids_raw:
                gateway_id = str(gateway_id_raw).strip()
                if not gateway_id:
                    raise ExportError(f"{source}: EDGE gatewayIds contains an empty value")
                if gateway_id not in gateways:
                    raise ExportError(f"{source}: EDGE references unknown gatewayId: {gateway_id}")
                gateway_ids.append(gateway_id)
            available_cpu = parse_positive_decimal(
                item.get("availableCpuCyclesPerSecond"),
                "availableCpuCyclesPerSecond",
                source,
            )
            base_delay = parse_non_negative_decimal(
                item.get("basePropagationDelaySeconds"),
                "basePropagationDelaySeconds",
                source,
            )
            execution_nodes.append(
                ExecutionNode(
                    execution_node_id=execution_node_id,
                    node_type=node_type,
                    available_cpu=available_cpu,
                    base_delay_seconds=base_delay,
                    gateway_ids=tuple(gateway_ids),
                )
            )
        elif node_type == "CLOUD":
            access_policy = str(item.get("accessPolicy", "")).strip()
            if access_policy != THROUGH_ACTIVE_GATEWAY:
                raise ExportError(f"{source}: CLOUD accessPolicy must be {THROUGH_ACTIVE_GATEWAY}")
            available_cpu = parse_positive_decimal(
                item.get("availableCpuCyclesPerSecond"),
                "availableCpuCyclesPerSecond",
                source,
            )
            base_delay = parse_non_negative_decimal(
                item.get("serverBaseDelaySeconds"),
                "serverBaseDelaySeconds",
                source,
            )
            execution_nodes.append(
                ExecutionNode(
                    execution_node_id=execution_node_id,
                    node_type=node_type,
                    available_cpu=available_cpu,
                    base_delay_seconds=base_delay,
                    access_policy=access_policy,
                )
            )
        else:
            raise ExportError(f"{source}: unsupported execution node type for Fase 10F: {node_type}")

    cell_region_ids.add("globalNetwork")
    return (
        gateways,
        pools,
        sorted(execution_nodes, key=lambda node: node.execution_node_id),
        regional_radio_delays,
        cell_region_ids,
    )


def read_access_links(path: Path, gateways: dict[str, Gateway]) -> list[AccessLink]:
    require_file(path, "access link preview CSV")
    required_columns = {
        "timeNs",
        "timeSeconds",
        "vehicleId",
        "gatewayId",
        "runtimeGatewayId",
        "distanceMeters",
        "coverageRadiusMeters",
        "active",
        "available",
        "cellRegionId",
        "bandwidthPoolId",
    }
    links: list[AccessLink] = []
    active_by_state: set[tuple[int, str]] = set()
    try:
        with path.open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames is None:
                raise ExportError(f"{path}: missing CSV header")
            missing = sorted(required_columns - set(reader.fieldnames))
            if missing:
                raise ExportError(f"{path}: missing required columns: {', '.join(missing)}")
            for line_number, row in enumerate(reader, start=2):
                source = f"{path}:{line_number}"
                time_ns = parse_int((row.get("timeNs") or "").strip(), "timeNs", source)
                time_seconds = parse_non_negative_decimal((row.get("timeSeconds") or "").strip(), "timeSeconds", source)
                expected_time_seconds = Decimal(time_ns) / Decimal(1_000_000_000)
                if time_seconds != expected_time_seconds:
                    raise ExportError(f"{source}: timeSeconds does not match timeNs")
                vehicle_id = (row.get("vehicleId") or "").strip()
                gateway_id = (row.get("gatewayId") or "").strip()
                runtime_gateway_id = (row.get("runtimeGatewayId") or "").strip()
                bandwidth_pool_id = (row.get("bandwidthPoolId") or "").strip()
                cell_region_id = (row.get("cellRegionId") or "").strip()
                if not vehicle_id:
                    raise ExportError(f"{source}: vehicleId is empty")
                if not gateway_id:
                    raise ExportError(f"{source}: gatewayId is empty")
                if not runtime_gateway_id:
                    raise ExportError(f"{source}: runtimeGatewayId is empty")
                if not bandwidth_pool_id:
                    raise ExportError(f"{source}: bandwidthPoolId is empty")
                if gateway_id not in gateways:
                    raise ExportError(f"{source}: gatewayId does not exist in infrastructure snapshot: {gateway_id}")
                gateway = gateways[gateway_id]
                if runtime_gateway_id != gateway.runtime_id:
                    raise ExportError(f"{source}: runtimeGatewayId does not match gateway runtimeId")
                if bandwidth_pool_id != gateway.bandwidth_pool_id:
                    raise ExportError(f"{source}: bandwidthPoolId does not match gateway pool")
                if not cell_region_id:
                    raise ExportError(f"{source}: cellRegionId is empty")
                if cell_region_id != gateway.cell_region_id:
                    raise ExportError(f"{source}: cellRegionId does not match gateway cellRegionId")
                distance = parse_non_negative_decimal((row.get("distanceMeters") or "").strip(), "distanceMeters", source)
                coverage = parse_positive_decimal((row.get("coverageRadiusMeters") or "").strip(), "coverageRadiusMeters", source)
                if distance < 0 or coverage <= 0:
                    raise ExportError(f"{source}: invalid distance or coverage")
                active = parse_bool((row.get("active") or "").strip(), "active", source)
                available = parse_bool((row.get("available") or "").strip(), "available", source)
                if active and not available:
                    raise ExportError(f"{source}: active link is not available")
                if active:
                    state_key = (time_ns, vehicle_id)
                    if state_key in active_by_state:
                        raise ExportError(f"{source}: vehicle has more than one active gateway at the same timeNs")
                    active_by_state.add(state_key)
                links.append(
                    AccessLink(
                        time_ns=time_ns,
                        time_seconds=time_seconds,
                        vehicle_id=vehicle_id,
                        gateway_id=gateway_id,
                        runtime_gateway_id=runtime_gateway_id,
                        bandwidth_pool_id=bandwidth_pool_id,
                        active=active,
                        available=available,
                    )
                )
    except OSError as exc:
        raise ExportError(f"{path}: CSV is not readable") from exc
    if not links:
        raise ExportError(f"{path}: no access link rows found")
    return links


def read_cell_bandwidth_stream(
    path: Path,
    known_region_ids: set[str],
) -> dict[tuple[str, str], list[CellBandwidthRecord]]:
    require_file(path, "Cell bandwidth stream CSV")
    required_columns = {
        "measurementTimeSeconds",
        "availableFromTimeSeconds",
        "bucketStartSeconds",
        "bucketEndSeconds",
        "regionId",
        "direction",
        "trafficObservedBitsPerSecond",
        "nominalCapacityBitsPerSecond",
        "residualCapacityBitsPerSecond",
        "residualPolicy",
        "bucketBoundaryPolicy",
        "availableFromPolicy",
        "sourceFile",
    }
    index: dict[tuple[str, str], list[CellBandwidthRecord]] = {}
    seen: set[tuple[str, str, str, str]] = set()
    try:
        with path.open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames is None:
                raise ExportError(f"{path}: missing CSV header")
            missing = sorted(required_columns - set(reader.fieldnames))
            if missing:
                raise ExportError(f"{path}: missing required columns: {', '.join(missing)}")
            previous_time_by_source: dict[str, Decimal] = {}
            for line_number, row in enumerate(reader, start=2):
                source = f"{path}:{line_number}"
                measurement_time = parse_non_negative_decimal(
                    row.get("measurementTimeSeconds"),
                    "measurementTimeSeconds",
                    source,
                )
                available_from = parse_non_negative_decimal(
                    row.get("availableFromTimeSeconds"),
                    "availableFromTimeSeconds",
                    source,
                )
                bucket_start = parse_non_negative_decimal(row.get("bucketStartSeconds"), "bucketStartSeconds", source)
                bucket_end = parse_non_negative_decimal(row.get("bucketEndSeconds"), "bucketEndSeconds", source)
                if bucket_start != measurement_time:
                    raise ExportError(f"{source}: bucketStartSeconds must match measurementTimeSeconds")
                if bucket_end <= bucket_start:
                    raise ExportError(f"{source}: bucketEndSeconds must be > bucketStartSeconds")
                if available_from < bucket_end:
                    raise ExportError(f"{source}: availableFromTimeSeconds cannot precede bucketEndSeconds")
                region_id = (row.get("regionId") or "").strip()
                if not region_id:
                    raise ExportError(f"{source}: regionId is empty")
                if region_id not in known_region_ids:
                    raise ExportError(f"{source}: regionId is unknown: {region_id}")
                direction = (row.get("direction") or "").strip()
                if direction not in {"UPLINK", "DOWNLINK"}:
                    raise ExportError(f"{source}: direction is not recognized: {direction}")
                traffic = parse_non_negative_decimal(
                    row.get("trafficObservedBitsPerSecond"),
                    "trafficObservedBitsPerSecond",
                    source,
                )
                nominal = parse_positive_decimal(
                    row.get("nominalCapacityBitsPerSecond"),
                    "nominalCapacityBitsPerSecond",
                    source,
                )
                residual = parse_non_negative_decimal(
                    row.get("residualCapacityBitsPerSecond"),
                    "residualCapacityBitsPerSecond",
                    source,
                )
                expected_residual = nominal - traffic
                if expected_residual < 0:
                    expected_residual = Decimal(0)
                if residual != expected_residual:
                    raise ExportError(f"{source}: residualCapacityBitsPerSecond is inconsistent")
                residual_policy = (row.get("residualPolicy") or "").strip()
                if residual_policy != "NOMINAL_MINUS_OBSERVED_DIAGNOSTIC":
                    raise ExportError(f"{source}: unexpected residualPolicy: {residual_policy}")
                bucket_policy = (row.get("bucketBoundaryPolicy") or "").strip()
                if bucket_policy != "START_TIMESTAMP_FOR_INTERVAL":
                    raise ExportError(f"{source}: unsupported bucketBoundaryPolicy: {bucket_policy}")
                available_policy = (row.get("availableFromPolicy") or "").strip()
                if available_policy != "SAFE_AFTER_TIMESTAMP":
                    raise ExportError(f"{source}: unsupported availableFromPolicy: {available_policy}")
                source_file = (row.get("sourceFile") or "").strip()
                if not source_file:
                    raise ExportError(f"{source}: sourceFile is empty")
                previous_time = previous_time_by_source.get(source_file)
                if previous_time is not None and measurement_time < previous_time:
                    raise ExportError(f"{source}: measurement time decreased within source file")
                previous_time_by_source[source_file] = measurement_time
                duplicate_key = (format_decimal_plain(measurement_time), region_id, direction, source_file)
                if duplicate_key in seen:
                    raise ExportError(f"{source}: duplicate Cell bandwidth record {duplicate_key}")
                seen.add(duplicate_key)
                record = CellBandwidthRecord(
                    measurement_time_seconds=measurement_time,
                    available_from_time_seconds=available_from,
                    region_id=region_id,
                    direction=direction,
                    residual_capacity=residual,
                    bucket_boundary_policy=bucket_policy,
                )
                index.setdefault((region_id, direction), []).append(record)
    except OSError as exc:
        raise ExportError(f"{path}: CSV is not readable") from exc

    if not index:
        raise ExportError(f"{path}: no Cell bandwidth records found")
    for records in index.values():
        records.sort(key=lambda record: (record.available_from_time_seconds, record.measurement_time_seconds))
    return index


def latest_safe_record(
    index: dict[tuple[str, str], list[CellBandwidthRecord]],
    region_id: str,
    direction: str,
    time_seconds: Decimal,
) -> CellBandwidthRecord:
    records = index.get((region_id, direction))
    if not records:
        raise ExportError(f"no Cell bandwidth records for {region_id}/{direction}")
    available_times = [record.available_from_time_seconds for record in records]
    position = bisect.bisect_right(available_times, time_seconds) - 1
    if position < 0:
        raise ExportError(f"no safe Cell bandwidth record for {region_id}/{direction} at t={time_seconds}")
    record = records[position]
    if record.available_from_time_seconds > time_seconds:
        raise ExportError(f"future Cell bandwidth lookup detected for {region_id}/{direction}")
    return record


def select_bandwidth(
    index: dict[tuple[str, str], list[CellBandwidthRecord]],
    region_id: str,
    time_seconds: Decimal,
) -> BandwidthSelection:
    uplink = latest_safe_record(index, region_id, "UPLINK", time_seconds)
    downlink = latest_safe_record(index, region_id, "DOWNLINK", time_seconds)
    if uplink.measurement_time_seconds != downlink.measurement_time_seconds:
        raise ExportError(
            "uplink and downlink latest safe Cell bandwidth measurements do not share the same timestamp "
            f"for {region_id} at t={time_seconds}"
        )
    if uplink.bucket_boundary_policy != downlink.bucket_boundary_policy:
        raise ExportError(f"uplink and downlink bucket policies differ for {region_id} at t={time_seconds}")
    if uplink.available_from_time_seconds > time_seconds or downlink.available_from_time_seconds > time_seconds:
        raise ExportError(f"future Cell bandwidth lookup detected for {region_id} at t={time_seconds}")
    available_bandwidth = min(uplink.residual_capacity, downlink.residual_capacity)
    age = time_seconds - uplink.measurement_time_seconds
    if age < 0:
        raise ExportError(f"negative Cell bandwidth age for {region_id} at t={time_seconds}")
    return BandwidthSelection(
        measurement_time_seconds=uplink.measurement_time_seconds,
        bandwidth_age_seconds=age,
        uplink_residual=uplink.residual_capacity,
        downlink_residual=downlink.residual_capacity,
        available_bandwidth=available_bandwidth,
        bucket_boundary_policy=uplink.bucket_boundary_policy,
    )


def build_candidates(
    access_links: list[AccessLink],
    gateways: dict[str, Gateway],
    pools: dict[str, BandwidthPool],
    execution_nodes: list[ExecutionNode],
    regional_radio_delays: dict[str, Decimal],
    cell_bandwidth_index: dict[tuple[str, str], list[CellBandwidthRecord]],
) -> list[CandidatePreview]:
    edge_nodes = [node for node in execution_nodes if node.node_type == "EDGE"]
    cloud_nodes = [node for node in execution_nodes if node.node_type == "CLOUD"]
    candidates: list[CandidatePreview] = []
    seen: set[tuple[int, str]] = set()
    for link in access_links:
        if not link.active:
            continue
        if not link.available:
            raise ExportError("active link is not available")
        gateway = gateways[link.gateway_id]
        if link.bandwidth_pool_id not in pools:
            raise ExportError(f"pool does not exist: {link.bandwidth_pool_id}")
        pool = pools[link.bandwidth_pool_id]
        regional_radio_delay = regional_radio_delays[gateway.cell_region_id]
        bandwidth_selection = select_bandwidth(cell_bandwidth_index, gateway.cell_region_id, link.time_seconds)
        for node in edge_nodes:
            if link.gateway_id not in node.gateway_ids:
                continue
            candidate = make_candidate(link, gateway, pool, node, regional_radio_delay, bandwidth_selection)
            if link.gateway_id not in node.gateway_ids:
                raise ExportError("EDGE candidate generated through an unassociated gateway")
            key = (candidate.time_ns, candidate.candidate_id)
            if key in seen:
                raise ExportError(f"duplicate candidate at timeNs={candidate.time_ns}: {candidate.candidate_id}")
            seen.add(key)
            candidates.append(candidate)
        for node in cloud_nodes:
            if node.access_policy != THROUGH_ACTIVE_GATEWAY:
                raise ExportError(f"CLOUD node generated without {THROUGH_ACTIVE_GATEWAY}")
            candidate = make_candidate(link, gateway, pool, node, regional_radio_delay, bandwidth_selection)
            key = (candidate.time_ns, candidate.candidate_id)
            if key in seen:
                raise ExportError(f"duplicate candidate at timeNs={candidate.time_ns}: {candidate.candidate_id}")
            seen.add(key)
            candidates.append(candidate)
    return sorted(
        candidates,
        key=lambda candidate: (
            candidate.time_ns,
            natural_key(candidate.source_vehicle_id),
            type_order(candidate.node_type),
            candidate.execution_node_id,
            candidate.gateway_id,
            candidate.candidate_id,
        ),
    )


def make_candidate(
    link: AccessLink,
    gateway: Gateway,
    pool: BandwidthPool,
    node: ExecutionNode,
    regional_radio_delay: Decimal,
    bandwidth_selection: BandwidthSelection,
) -> CandidatePreview:
    if node.node_type == "CLOUD" and not link.active:
        raise ExportError("CLOUD candidate generated without active gateway")
    candidate = CandidatePreview(
        time_ns=link.time_ns,
        time_seconds=link.time_seconds,
        source_vehicle_id=link.vehicle_id,
        execution_node_id=node.execution_node_id,
        node_type=node.node_type,
        available_cpu=node.available_cpu,
        bandwidth_selection=bandwidth_selection,
        propagation_delay_seconds=regional_radio_delay + node.base_delay_seconds,
        regional_radio_delay_seconds=regional_radio_delay,
        node_base_delay_seconds=node.base_delay_seconds,
        bandwidth_pool_id=pool.pool_id,
        gateway_id=gateway.gateway_id,
        runtime_gateway_id=gateway.runtime_id,
        cell_region_id=gateway.cell_region_id,
    )
    if not candidate.candidate_id:
        raise ExportError("candidateId is empty")
    if candidate.bandwidth_selection.available_bandwidth < 0:
        raise ExportError(f"{candidate.candidate_id}: availableBandwidth must be >= 0")
    if candidate.bandwidth_selection.available_bandwidth != min(
        candidate.bandwidth_selection.uplink_residual,
        candidate.bandwidth_selection.downlink_residual,
    ):
        raise ExportError(f"{candidate.candidate_id}: availableBandwidth is not conservative min residual")
    return candidate


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
    finally:
        if temp_name is not None and os.path.exists(temp_name):
            os.unlink(temp_name)


def run() -> None:
    args = parse_args()
    access_link_file = Path(args.access_link_file)
    infrastructure_snapshot = Path(args.infrastructure_snapshot)
    cell_bandwidth_stream = Path(args.cell_bandwidth_stream)
    out_file = Path(args.out_file)

    gateways, pools, execution_nodes, regional_radio_delays, cell_region_ids = read_infrastructure(
        infrastructure_snapshot
    )
    access_links = read_access_links(access_link_file, gateways)
    cell_bandwidth_index = read_cell_bandwidth_stream(cell_bandwidth_stream, cell_region_ids)
    active_links = [link for link in access_links if link.active]
    candidates = build_candidates(
        access_links,
        gateways,
        pools,
        execution_nodes,
        regional_radio_delays,
        cell_bandwidth_index,
    )

    write_csv_atomic(out_file, CSV_COLUMNS, [candidate.to_csv_row() for candidate in candidates])

    edge_nodes = [node for node in execution_nodes if node.node_type == "EDGE"]
    cloud_nodes = [node for node in execution_nodes if node.node_type == "CLOUD"]
    edge_candidates = sum(1 for candidate in candidates if candidate.node_type == "EDGE")
    cloud_candidates = sum(1 for candidate in candidates if candidate.node_type == "CLOUD")
    states_with_candidates = {(candidate.time_ns, candidate.source_vehicle_id) for candidate in candidates}
    distinct_sources = {candidate.source_vehicle_id for candidate in candidates}
    warnings = [
        "candidate bandwidth uses residual Cell bandwidth from the integrated baseline only",
        "candidate propagation delay is diagnostic and uses gateway-associated Cell region plus configured node base delay",
        "Cell raw diagnostics from historical runs are not used for remote candidate generation",
        "residual Cell bandwidth is diagnostic and not a final scientific network allocation model",
    ]

    print("Remote candidate preview export completed")
    print(f"accessLinkFile={access_link_file}")
    print(f"infrastructureSnapshot={infrastructure_snapshot}")
    print(f"cellBandwidthStream={cell_bandwidth_stream}")
    print(f"activeAccessLinksRead={len(active_links)}")
    print(f"edgeExecutionNodesRead={len(edge_nodes)}")
    print(f"cloudExecutionNodesRead={len(cloud_nodes)}")
    print(f"candidatesExported={len(candidates)}")
    print(f"edgeCandidates={edge_candidates}")
    print(f"cloudCandidates={cloud_candidates}")
    print(f"statesWithRemoteCandidates={len(states_with_candidates)}")
    print(f"distinctSourceVehicles={len(distinct_sources)}")
    print(f"candidateBandwidthPolicy={BANDWIDTH_POLICY}")
    print(f"candidateBandwidthLookupPolicy={BANDWIDTH_LOOKUP_POLICY}")
    print(f"candidatePropagationDelayPolicy={PROPAGATION_DELAY_POLICY}")
    print("multipleActiveGatewayViolations=0")
    print("activeUnavailableViolations=0")
    print("duplicateCandidates=0")
    print("futureLookAheadViolations=0")
    print("candidatesMissingSafeBandwidth=0")
    print(f"warningsCount={len(warnings)}")
    print("warnings:")
    for warning in warnings:
        print(f"  {warning}")
    print(f"outFile={out_file}")


def main() -> int:
    try:
        run()
    except ExportError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
