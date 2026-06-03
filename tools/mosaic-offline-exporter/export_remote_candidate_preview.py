#!/usr/bin/env python3
"""Export diagnostic EDGE and CLOUD candidate previews from active access links."""

from __future__ import annotations

import argparse
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
BANDWIDTH_POLICY = "NOMINAL_ONLY_FOR_INITIAL_EXPORTER"
BANDWIDTH_SOURCE = "INFRASTRUCTURE_SNAPSHOT_GATEWAY_POOL"
PROPAGATION_DELAY_POLICY = "MAX_CELL_UPLINK_DOWNLINK_UNICAST_PLUS_NODE_BASE_DIAGNOSTIC"


class ExportError(Exception):
    """Raised when remote candidate preview cannot be exported safely."""


@dataclass(frozen=True)
class AccessLink:
    time_ns: int
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
class CandidatePreview:
    time_ns: int
    source_vehicle_id: str
    execution_node_id: str
    node_type: str
    available_cpu: Decimal
    available_bandwidth: Decimal
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
            "timeSeconds": format_seconds_from_ns(self.time_ns),
            "candidateId": self.candidate_id,
            "sourceVehicleId": self.source_vehicle_id,
            "executionNodeId": self.execution_node_id,
            "type": self.node_type,
            "availableCpu": format_decimal_plain(self.available_cpu),
            "availableBandwidth": format_decimal_plain(self.available_bandwidth),
            "propagationDelaySeconds": format_decimal_plain(self.propagation_delay_seconds),
            "regionalRadioDelaySeconds": format_decimal_plain(self.regional_radio_delay_seconds),
            "nodeBaseDelaySeconds": format_decimal_plain(self.node_base_delay_seconds),
            "bandwidthPoolId": self.bandwidth_pool_id,
            "gatewayId": self.gateway_id,
            "runtimeGatewayId": self.runtime_gateway_id,
            "cellRegionId": self.cell_region_id,
            "bandwidthPolicy": BANDWIDTH_POLICY,
            "bandwidthSource": BANDWIDTH_SOURCE,
            "propagationDelayPolicy": PROPAGATION_DELAY_POLICY,
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export diagnostic EDGE and CLOUD candidates from active gateway access links."
    )
    parser.add_argument("--access-link-file", required=True, help="Fase 10E access_link_preview.csv.")
    parser.add_argument("--infrastructure-snapshot", required=True, help="Fase 10C infrastructure_snapshot.json.")
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
        parsed = Decimal(str(value))
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
    match = re.fullmatch(r"\s*([+]?(?:\d+(?:\.\d*)?|\.\d+))\s*(ns|us|µs|μs|ms|s)\s*", value)
    if not match:
        raise ExportError(f"{source}: delay uses an unrecognized unit: {value}")
    amount = parse_non_negative_decimal(match.group(1), "delay", source)
    unit = match.group(2)
    factors = {
        "ns": Decimal("0.000000001"),
        "us": Decimal("0.000001"),
        "µs": Decimal("0.000001"),
        "μs": Decimal("0.000001"),
        "ms": Decimal("0.001"),
        "s": Decimal("1"),
    }
    return amount * factors[unit]


def get_region_id(region: dict[str, Any], index: int) -> str:
    region_id = str(region.get("regionId") or region.get("id") or "").strip()
    if not region_id:
        raise ExportError(f"infrastructure snapshot cell.regions[{index}] must contain regionId or id")
    return region_id


def read_infrastructure(path: Path) -> tuple[dict[str, Gateway], dict[str, BandwidthPool], list[ExecutionNode], dict[str, Decimal]]:
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
        nominal = parse_positive_decimal(item.get("nominalBandwidthBitsPerSecond"), "nominalBandwidthBitsPerSecond", source)
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
    for index, region in enumerate(region_items):
        if not isinstance(region, dict):
            raise ExportError(f"infrastructure snapshot cell.regions[{index}] must be an object")
        region_id = get_region_id(region, index)
        if region_id in regional_radio_delays:
            raise ExportError(f"infrastructure snapshot cell.regions[{index}]: duplicate region id {region_id}")
        source = f"infrastructure snapshot cell.regions[{region_id}]"
        uplink_delay_value = (((region.get("uplink") or {}).get("delay") or {}).get("delay"))
        downlink_delay_value = (((region.get("downlink") or {}).get("unicast") or {}).get("delay") or {}).get("delay")
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
            available_cpu = parse_positive_decimal(item.get("availableCpuCyclesPerSecond"), "availableCpuCyclesPerSecond", source)
            base_delay = parse_non_negative_decimal(item.get("basePropagationDelaySeconds"), "basePropagationDelaySeconds", source)
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
            available_cpu = parse_positive_decimal(item.get("availableCpuCyclesPerSecond"), "availableCpuCyclesPerSecond", source)
            base_delay = parse_non_negative_decimal(item.get("serverBaseDelaySeconds"), "serverBaseDelaySeconds", source)
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

    return gateways, pools, sorted(execution_nodes, key=lambda node: node.execution_node_id), regional_radio_delays


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
                parse_non_negative_decimal((row.get("timeSeconds") or "").strip(), "timeSeconds", source)
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


def build_candidates(
    access_links: list[AccessLink],
    gateways: dict[str, Gateway],
    pools: dict[str, BandwidthPool],
    execution_nodes: list[ExecutionNode],
    regional_radio_delays: dict[str, Decimal],
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
        if link.gateway_id != gateway.gateway_id:
            raise ExportError("internal gateway mismatch")
        if link.bandwidth_pool_id not in pools:
            raise ExportError(f"pool does not exist: {link.bandwidth_pool_id}")
        pool = pools[link.bandwidth_pool_id]
        regional_radio_delay = regional_radio_delays[gateway.cell_region_id]
        for node in edge_nodes:
            if link.gateway_id not in node.gateway_ids:
                continue
            candidate = make_candidate(link, gateway, pool, node, regional_radio_delay)
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
            candidate = make_candidate(link, gateway, pool, node, regional_radio_delay)
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
) -> CandidatePreview:
    if node.node_type == "CLOUD" and not link.active:
        raise ExportError("CLOUD candidate generated without active gateway")
    candidate = CandidatePreview(
        time_ns=link.time_ns,
        source_vehicle_id=link.vehicle_id,
        execution_node_id=node.execution_node_id,
        node_type=node.node_type,
        available_cpu=node.available_cpu,
        available_bandwidth=pool.nominal_bandwidth,
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
            writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="raise")
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
    out_file = Path(args.out_file)

    gateways, pools, execution_nodes, regional_radio_delays = read_infrastructure(infrastructure_snapshot)
    access_links = read_access_links(access_link_file, gateways)
    active_links = [link for link in access_links if link.active]
    candidates = build_candidates(access_links, gateways, pools, execution_nodes, regional_radio_delays)

    write_csv_atomic(
        out_file,
        [
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
            "bandwidthPolicy",
            "bandwidthSource",
            "propagationDelayPolicy",
        ],
        [candidate.to_csv_row() for candidate in candidates],
    )

    edge_nodes = [node for node in execution_nodes if node.node_type == "EDGE"]
    cloud_nodes = [node for node in execution_nodes if node.node_type == "CLOUD"]
    edge_candidates = sum(1 for candidate in candidates if candidate.node_type == "EDGE")
    cloud_candidates = sum(1 for candidate in candidates if candidate.node_type == "CLOUD")
    states_with_candidates = {(candidate.time_ns, candidate.source_vehicle_id) for candidate in candidates}
    distinct_sources = {candidate.source_vehicle_id for candidate in candidates}
    warnings = [
        "candidate bandwidth uses nominal gateway-pool capacity because residual Cell bandwidth is unavailable for MaGaWorkloadStudy",
        "candidate propagation delay is diagnostic and uses gateway-associated Cell region plus configured node base delay",
        "Cell raw diagnostics from historical runs are not used for remote candidate generation",
    ]

    print("Remote candidate preview export completed")
    print(f"accessLinkFile={access_link_file}")
    print(f"infrastructureSnapshot={infrastructure_snapshot}")
    print(f"activeAccessLinksRead={len(active_links)}")
    print(f"edgeExecutionNodesRead={len(edge_nodes)}")
    print(f"cloudExecutionNodesRead={len(cloud_nodes)}")
    print(f"candidatesExported={len(candidates)}")
    print(f"edgeCandidates={edge_candidates}")
    print(f"cloudCandidates={cloud_candidates}")
    print(f"statesWithRemoteCandidates={len(states_with_candidates)}")
    print(f"distinctSourceVehicles={len(distinct_sources)}")
    print(f"candidateBandwidthPolicy={BANDWIDTH_POLICY}")
    print(f"candidatePropagationDelayPolicy={PROPAGATION_DELAY_POLICY}")
    print("multipleActiveGatewayViolations=0")
    print("activeUnavailableViolations=0")
    print("duplicateCandidates=0")
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
