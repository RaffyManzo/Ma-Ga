#!/usr/bin/env python3
"""Export a diagnostic active gateway preview from vehicle states and infrastructure."""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import re
import sys
import tempfile
from collections import Counter
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path
from typing import Any


EARTH_RADIUS_METERS = 6_371_000.0
DISTANCE_POLICY = "HAVERSINE_FROM_LAT_LON_DIAGNOSTIC"


class ExportError(Exception):
    """Raised when access link preview cannot be exported safely."""


@dataclass(frozen=True)
class VehicleState:
    time_ns: int
    vehicle_id: str
    latitude: float
    longitude: float


@dataclass(frozen=True)
class Gateway:
    gateway_id: str
    runtime_gateway_id: str
    latitude: float
    longitude: float
    coverage_radius_meters: float
    cell_region_id: str
    bandwidth_pool_id: str


@dataclass(frozen=True)
class AccessLinkPreview:
    time_ns: int
    vehicle_id: str
    gateway_id: str
    runtime_gateway_id: str
    distance_meters: float
    coverage_radius_meters: float
    active: bool
    available: bool
    cell_region_id: str
    bandwidth_pool_id: str

    def to_csv_row(self) -> dict[str, str]:
        return {
            "timeNs": str(self.time_ns),
            "timeSeconds": format_time_seconds(self.time_ns),
            "vehicleId": self.vehicle_id,
            "gatewayId": self.gateway_id,
            "runtimeGatewayId": self.runtime_gateway_id,
            "distanceMeters": f"{self.distance_meters:.6f}",
            "coverageRadiusMeters": format_float_plain(self.coverage_radius_meters),
            "active": bool_text(self.active),
            "available": bool_text(self.available),
            "cellRegionId": self.cell_region_id,
            "bandwidthPoolId": self.bandwidth_pool_id,
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export a diagnostic access link preview using vehicle positions and gateway geometry."
    )
    parser.add_argument("--vehicle-state-file", required=True, help="Fase 10B vehicle_state_stream.csv.")
    parser.add_argument("--infrastructure-snapshot", required=True, help="Fase 10C infrastructure_snapshot.json.")
    parser.add_argument("--out-file", required=True, help="Access link preview CSV to write.")
    parser.add_argument("--cell-handover-stream", help="Optional Fase 10D diagnostic cell_handover_stream.csv.")
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


def bool_text(value: bool) -> str:
    return "true" if value else "false"


def format_time_seconds(time_ns: int) -> str:
    seconds = Decimal(time_ns) / Decimal(1_000_000_000)
    text = format(seconds, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def format_float_plain(value: float) -> str:
    if value.is_integer():
        return str(int(value))
    text = f"{value:.12f}".rstrip("0").rstrip(".")
    return text or "0"


def parse_int_value(value: str, field_name: str, source: str) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ExportError(f"{source}: {field_name} is not a valid integer") from exc
    if parsed < 0:
        raise ExportError(f"{source}: {field_name} must be >= 0")
    return parsed


def parse_float_value(value: Any, field_name: str, source: str) -> float:
    try:
        parsed = float(value)
    except (TypeError, ValueError) as exc:
        raise ExportError(f"{source}: {field_name} is not numeric") from exc
    if not math.isfinite(parsed):
        raise ExportError(f"{source}: {field_name} must be finite")
    return parsed


def validate_lat_lon(latitude: float, longitude: float, source: str) -> None:
    if latitude < -90 or latitude > 90:
        raise ExportError(f"{source}: latitude out of range")
    if longitude < -180 or longitude > 180:
        raise ExportError(f"{source}: longitude out of range")


def read_vehicle_states(path: Path) -> list[VehicleState]:
    require_file(path, "vehicle state CSV")
    required_fields = {"timeNs", "vehicleId", "latitude", "longitude"}
    states: list[VehicleState] = []
    try:
        with path.open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames is None:
                raise ExportError(f"{path}: missing CSV header")
            missing = sorted(required_fields - set(reader.fieldnames))
            if missing:
                raise ExportError(f"{path}: missing required columns: {', '.join(missing)}")
            for line_number, row in enumerate(reader, start=2):
                source = f"{path}:{line_number}"
                time_ns = parse_int_value((row.get("timeNs") or "").strip(), "timeNs", source)
                vehicle_id = (row.get("vehicleId") or "").strip()
                if not vehicle_id:
                    raise ExportError(f"{source}: vehicleId is empty")
                latitude = parse_float_value((row.get("latitude") or "").strip(), "latitude", source)
                longitude = parse_float_value((row.get("longitude") or "").strip(), "longitude", source)
                validate_lat_lon(latitude, longitude, source)
                states.append(VehicleState(time_ns=time_ns, vehicle_id=vehicle_id, latitude=latitude, longitude=longitude))
    except OSError as exc:
        raise ExportError(f"{path}: CSV is not readable") from exc
    if not states:
        raise ExportError(f"{path}: no vehicle states found")
    return sorted(states, key=lambda state: (state.time_ns, natural_key(state.vehicle_id)))


def read_infrastructure(path: Path) -> tuple[list[Gateway], set[str], set[str]]:
    infrastructure = load_json_file(path, "infrastructure snapshot")
    bandwidth_pools = infrastructure.get("bandwidthPools")
    if not isinstance(bandwidth_pools, list):
        raise ExportError("infrastructure snapshot bandwidthPools must be a list")
    pool_ids = set()
    for index, pool in enumerate(bandwidth_pools):
        if not isinstance(pool, dict):
            raise ExportError(f"infrastructure snapshot bandwidthPools[{index}] must be an object")
        pool_id = str(pool.get("poolId", "")).strip()
        if not pool_id:
            raise ExportError(f"infrastructure snapshot bandwidthPools[{index}].poolId is empty")
        pool_ids.add(pool_id)

    cell = infrastructure.get("cell")
    if not isinstance(cell, dict):
        raise ExportError("infrastructure snapshot cell must be an object")
    cell_regions = cell.get("regions")
    if not isinstance(cell_regions, list):
        raise ExportError("infrastructure snapshot cell.regions must be a list")
    region_ids = {"globalNetwork"}
    for index, region in enumerate(cell_regions):
        if not isinstance(region, dict):
            raise ExportError(f"infrastructure snapshot cell.regions[{index}] must be an object")
        region_id = str(region.get("regionId") or region.get("id") or "").strip()
        if not region_id:
            raise ExportError(f"infrastructure snapshot cell.regions[{index}] must contain regionId or id")
        region_ids.add(region_id)

    gateway_items = infrastructure.get("gateways")
    if not isinstance(gateway_items, list):
        raise ExportError("infrastructure snapshot gateways must be a list")
    gateways: list[Gateway] = []
    gateway_ids: set[str] = set()
    runtime_gateway_ids: set[str] = set()
    for index, item in enumerate(gateway_items):
        if not isinstance(item, dict):
            raise ExportError(f"infrastructure snapshot gateways[{index}] must be an object")
        source = f"infrastructure snapshot gateways[{index}]"
        gateway_id = str(item.get("gatewayId", "")).strip()
        runtime_gateway_id = str(item.get("runtimeId", "")).strip()
        bandwidth_pool_id = str(item.get("bandwidthPoolId", "")).strip()
        cell_region_id = str(item.get("cellRegionId", "")).strip()
        if not gateway_id:
            raise ExportError(f"{source}: gatewayId is empty")
        if gateway_id in gateway_ids:
            raise ExportError(f"{source}: duplicate gatewayId: {gateway_id}")
        gateway_ids.add(gateway_id)
        if not runtime_gateway_id:
            raise ExportError(f"{source}: runtimeId is empty")
        if runtime_gateway_id in runtime_gateway_ids:
            raise ExportError(f"{source}: duplicate runtimeId: {runtime_gateway_id}")
        runtime_gateway_ids.add(runtime_gateway_id)
        if not bandwidth_pool_id:
            raise ExportError(f"{source}: bandwidthPoolId is empty")
        if bandwidth_pool_id not in pool_ids:
            raise ExportError(f"{source}: unknown bandwidthPoolId: {bandwidth_pool_id}")
        if not cell_region_id:
            raise ExportError(f"{source}: cellRegionId is empty")
        if cell_region_id not in region_ids:
            raise ExportError(f"{source}: unknown cellRegionId: {cell_region_id}")
        latitude = parse_float_value(item.get("latitude"), "latitude", source)
        longitude = parse_float_value(item.get("longitude"), "longitude", source)
        validate_lat_lon(latitude, longitude, source)
        coverage_radius = parse_float_value(item.get("coverageRadiusMeters"), "coverageRadiusMeters", source)
        if coverage_radius <= 0:
            raise ExportError(f"{source}: coverageRadiusMeters must be > 0")
        gateways.append(
            Gateway(
                gateway_id=gateway_id,
                runtime_gateway_id=runtime_gateway_id,
                latitude=latitude,
                longitude=longitude,
                coverage_radius_meters=coverage_radius,
                cell_region_id=cell_region_id,
                bandwidth_pool_id=bandwidth_pool_id,
            )
        )
    if not gateways:
        raise ExportError("infrastructure snapshot contains no gateways")
    return sorted(gateways, key=lambda gateway: gateway.gateway_id), pool_ids, region_ids


def read_optional_handover_stream(path: Path, valid_regions: set[str], min_time_ns: int, max_time_ns: int) -> None:
    require_file(path, "cell handover stream CSV")
    required_fields = {"timeNs", "vehicleId", "previousRegion", "currentRegion"}
    try:
        with path.open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames is None:
                raise ExportError(f"{path}: missing CSV header")
            missing = sorted(required_fields - set(reader.fieldnames))
            if missing:
                raise ExportError(f"{path}: missing required columns: {', '.join(missing)}")
            for line_number, row in enumerate(reader, start=2):
                source = f"{path}:{line_number}"
                time_ns = parse_int_value((row.get("timeNs") or "").strip(), "timeNs", source)
                if time_ns < min_time_ns or time_ns > max_time_ns:
                    raise ExportError(f"{source}: handover time is outside the observed vehicle state interval")
                vehicle_id = (row.get("vehicleId") or "").strip()
                if not vehicle_id:
                    raise ExportError(f"{source}: vehicleId is empty")
                for field_name in ("previousRegion", "currentRegion"):
                    region_id = (row.get(field_name) or "").strip()
                    if region_id and region_id not in valid_regions:
                        raise ExportError(f"{source}: {field_name} is not a known Cell region: {region_id}")
    except OSError as exc:
        raise ExportError(f"{path}: CSV is not readable") from exc


def haversine_distance_meters(lat_a: float, lon_a: float, lat_b: float, lon_b: float) -> float:
    phi_a = math.radians(lat_a)
    phi_b = math.radians(lat_b)
    delta_phi = math.radians(lat_b - lat_a)
    delta_lambda = math.radians(lon_b - lon_a)
    sin_delta_phi = math.sin(delta_phi / 2.0)
    sin_delta_lambda = math.sin(delta_lambda / 2.0)
    a = sin_delta_phi * sin_delta_phi + math.cos(phi_a) * math.cos(phi_b) * sin_delta_lambda * sin_delta_lambda
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(max(0.0, 1.0 - a)))
    return EARTH_RADIUS_METERS * c


def build_links(states: list[VehicleState], gateways: list[Gateway]) -> list[AccessLinkPreview]:
    links: list[AccessLinkPreview] = []
    active_counts: Counter[tuple[int, str]] = Counter()
    for state in states:
        distances: list[tuple[Gateway, float, bool]] = []
        for gateway in gateways:
            distance = haversine_distance_meters(state.latitude, state.longitude, gateway.latitude, gateway.longitude)
            if distance < 0:
                raise ExportError("distanceMeters must be >= 0")
            available = distance <= gateway.coverage_radius_meters
            distances.append((gateway, distance, available))
        available_distances = [(gateway, distance) for gateway, distance, available in distances if available]
        active_gateway_id: str | None = None
        if available_distances:
            active_gateway_id = min(available_distances, key=lambda item: (item[1], item[0].gateway_id))[0].gateway_id
        for gateway, distance, available in distances:
            active = gateway.gateway_id == active_gateway_id
            if active:
                active_counts[(state.time_ns, state.vehicle_id)] += 1
            if active and not available:
                raise ExportError("active link is not available")
            links.append(
                AccessLinkPreview(
                    time_ns=state.time_ns,
                    vehicle_id=state.vehicle_id,
                    gateway_id=gateway.gateway_id,
                    runtime_gateway_id=gateway.runtime_gateway_id,
                    distance_meters=distance,
                    coverage_radius_meters=gateway.coverage_radius_meters,
                    active=active,
                    available=available,
                    cell_region_id=gateway.cell_region_id,
                    bandwidth_pool_id=gateway.bandwidth_pool_id,
                )
            )
    for key, count in active_counts.items():
        if count > 1:
            raise ExportError(f"vehicle state has more than one active gateway: timeNs={key[0]} vehicleId={key[1]}")
    return sorted(links, key=lambda link: (link.time_ns, natural_key(link.vehicle_id), link.gateway_id))


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
            writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="raise")
            writer.writeheader()
            writer.writerows(rows)
        os.replace(temp_name, path)
    finally:
        if temp_name is not None and os.path.exists(temp_name):
            os.unlink(temp_name)


def run() -> None:
    args = parse_args()
    vehicle_state_file = Path(args.vehicle_state_file)
    infrastructure_snapshot = Path(args.infrastructure_snapshot)
    out_file = Path(args.out_file)
    handover_stream = Path(args.cell_handover_stream) if args.cell_handover_stream else None

    states = read_vehicle_states(vehicle_state_file)
    gateways, _pool_ids, region_ids = read_infrastructure(infrastructure_snapshot)
    warnings: list[str] = []
    if handover_stream is None:
        warnings.append("cell handover stream not provided; access links are derived from geometry only")
    else:
        read_optional_handover_stream(handover_stream, region_ids, states[0].time_ns, states[-1].time_ns)
        warnings.append("regional Cell handovers are not used as physical gateway handovers")

    links = build_links(states, gateways)
    write_csv_atomic(
        out_file,
        [
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
        ],
        [link.to_csv_row() for link in links],
    )

    links_by_state: dict[tuple[int, str], list[AccessLinkPreview]] = {}
    for link in links:
        links_by_state.setdefault((link.time_ns, link.vehicle_id), []).append(link)
    states_with_active = sum(1 for state_links in links_by_state.values() if any(link.active for link in state_links))
    available_links = sum(1 for link in links if link.available)
    active_links = sum(1 for link in links if link.active)

    print("Access link preview export completed")
    print(f"vehicleStateFile={vehicle_state_file}")
    print(f"infrastructureSnapshot={infrastructure_snapshot}")
    print(f"cellHandoverStream={handover_stream if handover_stream is not None else 'NOT_PROVIDED'}")
    print(f"vehicleStatesRead={len(states)}")
    print(f"gatewaysRead={len(gateways)}")
    print(f"linksEvaluated={len(links)}")
    print(f"availableLinks={available_links}")
    print(f"activeLinks={active_links}")
    print(f"statesWithActiveGateway={states_with_active}")
    print(f"statesWithoutActiveGateway={len(states) - states_with_active}")
    print("multipleActiveGatewayViolations=0")
    print("activeUnavailableViolations=0")
    print(f"distancePolicy={DISTANCE_POLICY}")
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
