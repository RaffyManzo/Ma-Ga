#!/usr/bin/env python3
"""Export a validated static infrastructure snapshot from MOSAIC study files."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
from collections import Counter
from copy import deepcopy
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


RSU_REGISTRATION = "RSU_REGISTRATION"
SERVER_REGISTRATION = "SERVER_REGISTRATION"
THROUGH_ACTIVE_GATEWAY = "THROUGH_ACTIVE_GATEWAY"
CONFIGURED_TO_BE_CALIBRATED = "CONFIGURED_VALUE_TO_BE_CALIBRATED"


class ExportError(Exception):
    """Raised when the infrastructure snapshot cannot be exported safely."""


@dataclass(frozen=True)
class RsuRegistration:
    time_ns: int
    runtime_id: str
    profile: str
    latitude: float
    longitude: float

    def to_json(self) -> dict[str, Any]:
        return {
            "timeNs": self.time_ns,
            "runtimeId": self.runtime_id,
            "profile": self.profile,
            "latitude": self.latitude,
            "longitude": self.longitude,
        }


@dataclass(frozen=True)
class ServerRegistration:
    time_ns: int
    runtime_id: str
    profile: str

    def to_json(self) -> dict[str, Any]:
        return {
            "timeNs": self.time_ns,
            "runtimeId": self.runtime_id,
            "profile": self.profile,
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export a validated static infrastructure snapshot for MOSAIC -> MA-GA."
    )
    parser.add_argument("--output-csv", required=True, help="MOSAIC output.csv file.")
    parser.add_argument("--cell-config", required=True, help="MOSAIC Cell cell_config.json.")
    parser.add_argument("--network-config", required=True, help="MOSAIC Cell network.json.")
    parser.add_argument("--regions-config", required=True, help="MOSAIC Cell regions.json.")
    parser.add_argument("--sns-config", required=True, help="MOSAIC SNS sns_config.json.")
    parser.add_argument("--resource-catalog", required=True, help="MA-GA resource catalog JSON.")
    parser.add_argument("--out-file", required=True, help="Infrastructure snapshot JSON to write.")
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


def parse_int(value: str, field_name: str, file_path: Path, line_number: int) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise malformed_output(file_path, line_number, f"{field_name} is not a valid integer") from exc
    if parsed < 0:
        raise malformed_output(file_path, line_number, f"{field_name} must be >= 0")
    return parsed


def parse_decimal(value: Any, field_name: str) -> Decimal:
    try:
        parsed = Decimal(str(value))
    except (InvalidOperation, ValueError) as exc:
        raise ExportError(f"{field_name} is not numeric") from exc
    if not parsed.is_finite():
        raise ExportError(f"{field_name} must be finite")
    return parsed


def parse_output_decimal(value: str, field_name: str, file_path: Path, line_number: int) -> Decimal:
    try:
        parsed = Decimal(value)
    except InvalidOperation as exc:
        raise malformed_output(file_path, line_number, f"{field_name} is not numeric") from exc
    if not parsed.is_finite():
        raise malformed_output(file_path, line_number, f"{field_name} must be finite")
    return parsed


def require_text(value: Any, field_name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ExportError(f"{field_name} must be a non-empty string")
    return value.strip()


def require_positive_number(value: Any, field_name: str) -> Decimal:
    parsed = parse_decimal(value, field_name)
    if parsed <= Decimal("0"):
        raise ExportError(f"{field_name} must be > 0")
    return parsed


def require_non_negative_number(value: Any, field_name: str) -> Decimal:
    parsed = parse_decimal(value, field_name)
    if parsed < Decimal("0"):
        raise ExportError(f"{field_name} must be >= 0")
    return parsed


def malformed_output(file_path: Path, line_number: int, reason: str) -> ExportError:
    return ExportError(f"Malformed output.csv event at {file_path}:{line_number}: {reason}")


def parse_output_csv(output_csv: Path) -> tuple[list[RsuRegistration], list[ServerRegistration]]:
    require_file(output_csv, "output CSV")
    rsus: list[RsuRegistration] = []
    servers: list[ServerRegistration] = []
    seen_rsu_runtime_ids: set[str] = set()
    seen_server_runtime_ids: set[str] = set()

    with output_csv.open("r", encoding="utf-8", errors="replace", newline="") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.rstrip("\r\n")
            fields = line.split(";")
            marker = fields[0] if fields else ""

            if marker == RSU_REGISTRATION:
                if len(fields) < 7:
                    raise malformed_output(output_csv, line_number, "RSU_REGISTRATION has fewer than 7 fields")
                time_ns = parse_int(fields[1], "timeNs", output_csv, line_number)
                runtime_id = fields[2].strip()
                profile = fields[3].strip()
                if not runtime_id:
                    raise malformed_output(output_csv, line_number, "runtimeId is empty")
                if not profile:
                    raise malformed_output(output_csv, line_number, "profile is empty")
                if runtime_id in seen_rsu_runtime_ids:
                    raise malformed_output(output_csv, line_number, f"RSU runtime registered more than once: {runtime_id}")
                latitude = parse_output_decimal(fields[5], "latitude", output_csv, line_number)
                longitude = parse_output_decimal(fields[6], "longitude", output_csv, line_number)
                if latitude < Decimal("-90") or latitude > Decimal("90"):
                    raise malformed_output(output_csv, line_number, "latitude must be between -90 and 90")
                if longitude < Decimal("-180") or longitude > Decimal("180"):
                    raise malformed_output(output_csv, line_number, "longitude must be between -180 and 180")
                seen_rsu_runtime_ids.add(runtime_id)
                rsus.append(
                    RsuRegistration(
                        time_ns=time_ns,
                        runtime_id=runtime_id,
                        profile=profile,
                        latitude=float(latitude),
                        longitude=float(longitude),
                    )
                )
            elif marker == SERVER_REGISTRATION:
                if len(fields) < 4:
                    raise malformed_output(output_csv, line_number, "SERVER_REGISTRATION has fewer than 4 fields")
                time_ns = parse_int(fields[1], "timeNs", output_csv, line_number)
                runtime_id = fields[2].strip()
                profile = fields[3].strip()
                if not runtime_id:
                    raise malformed_output(output_csv, line_number, "runtimeId is empty")
                if not profile:
                    raise malformed_output(output_csv, line_number, "profile is empty")
                if runtime_id in seen_server_runtime_ids:
                    raise malformed_output(output_csv, line_number, f"server runtime registered more than once: {runtime_id}")
                seen_server_runtime_ids.add(runtime_id)
                servers.append(
                    ServerRegistration(time_ns=time_ns, runtime_id=runtime_id, profile=profile)
                )

    if not rsus:
        raise ExportError("No RSU_REGISTRATION entries found")
    if not servers:
        raise ExportError("No SERVER_REGISTRATION entries found")
    return rsus, servers


def natural_key(value: str) -> tuple[str, int | str]:
    match = re.match(r"^(.*?)(\d+)$", value)
    if match:
        return (match.group(1), int(match.group(2)))
    return (value, "")


def sorted_unique(values: list[str], field_name: str) -> None:
    duplicates = sorted(name for name, count in Counter(values).items() if count > 1)
    if duplicates:
        raise ExportError(f"{field_name} values must be unique; duplicates: {', '.join(duplicates)}")


def require_list(container: dict[str, Any], key: str) -> list[Any]:
    value = container.get(key)
    if not isinstance(value, list):
        raise ExportError(f"{key} must be an array")
    return value


def parse_capacity_to_bps(value: Any, field_name: str) -> int:
    if isinstance(value, int) and not isinstance(value, bool):
        if value <= 0:
            raise ExportError(f"{field_name} must be > 0")
        return value

    if isinstance(value, float):
        parsed = parse_decimal(value, field_name)
        if parsed <= Decimal("0") or parsed != parsed.to_integral_value():
            raise ExportError(f"{field_name} must be a positive integer bit/s value")
        return int(parsed)

    if not isinstance(value, str):
        raise ExportError(f"{field_name} must be a capacity string or numeric bit/s value")

    text = value.strip()
    match = re.match(r"^([0-9]+(?:\.[0-9]+)?)\s*(bps|Kbps|Mbps|Gbps)$", text)
    if not match:
        raise ExportError(f"{field_name} has unsupported capacity format: {value}")

    amount = Decimal(match.group(1))
    unit = match.group(2)
    multiplier = {
        "bps": Decimal("1"),
        "Kbps": Decimal("1000"),
        "Mbps": Decimal("1000000"),
        "Gbps": Decimal("1000000000"),
    }[unit]
    bits_per_second = amount * multiplier
    if bits_per_second <= Decimal("0") or bits_per_second != bits_per_second.to_integral_value():
        raise ExportError(f"{field_name} must resolve to a positive integer bit/s value")
    return int(bits_per_second)


def validate_catalog(
    catalog: dict[str, Any],
    region_ids: set[str],
    rsus_by_runtime_id: dict[str, RsuRegistration],
    servers_by_runtime_id: dict[str, ServerRegistration],
) -> None:
    policies = catalog.get("policies")
    if not isinstance(policies, dict):
        raise ExportError("catalog policies must be an object")
    gateways = require_list(catalog, "gateways")
    bandwidth_pools = require_list(catalog, "bandwidthPools")
    execution_nodes = require_list(catalog, "executionNodes")

    gateway_ids: list[str] = []
    gateway_runtime_ids: list[str] = []
    pool_ids: list[str] = []
    execution_node_ids: list[str] = []

    for index, gateway in enumerate(gateways):
        if not isinstance(gateway, dict):
            raise ExportError(f"gateways[{index}] must be an object")
        runtime_id = require_text(gateway.get("runtimeId"), f"gateways[{index}].runtimeId")
        gateway_id = require_text(gateway.get("gatewayId"), f"gateways[{index}].gatewayId")
        require_text(gateway.get("gatewayType"), f"gateways[{index}].gatewayType")
        require_positive_number(gateway.get("coverageRadiusMeters"), f"gateways[{index}].coverageRadiusMeters")
        require_text(gateway.get("bandwidthPoolId"), f"gateways[{index}].bandwidthPoolId")
        cell_region_id = require_text(gateway.get("cellRegionId"), f"gateways[{index}].cellRegionId")
        if runtime_id not in rsus_by_runtime_id:
            raise ExportError(f"gateways[{index}].runtimeId not found in RSU registrations: {runtime_id}")
        if cell_region_id not in region_ids:
            raise ExportError(f"gateways[{index}].cellRegionId not found in Cell regions: {cell_region_id}")
        gateway_runtime_ids.append(runtime_id)
        gateway_ids.append(gateway_id)

    sorted_unique(gateway_ids, "gatewayId")
    sorted_unique(gateway_runtime_ids, "gateway runtimeId")

    for index, pool in enumerate(bandwidth_pools):
        if not isinstance(pool, dict):
            raise ExportError(f"bandwidthPools[{index}] must be an object")
        pool_id = require_text(pool.get("poolId"), f"bandwidthPools[{index}].poolId")
        require_text(pool.get("poolType"), f"bandwidthPools[{index}].poolType")
        cell_region_id = require_text(pool.get("cellRegionId"), f"bandwidthPools[{index}].cellRegionId")
        require_positive_number(
            pool.get("nominalBandwidthBitsPerSecond"),
            f"bandwidthPools[{index}].nominalBandwidthBitsPerSecond",
        )
        if cell_region_id not in region_ids:
            raise ExportError(f"bandwidthPools[{index}].cellRegionId not found in Cell regions: {cell_region_id}")
        pool_ids.append(pool_id)

    sorted_unique(pool_ids, "poolId")
    pool_id_set = set(pool_ids)

    for index, gateway in enumerate(gateways):
        if gateway["bandwidthPoolId"] not in pool_id_set:
            raise ExportError(
                f"gateways[{index}].bandwidthPoolId does not reference an existing pool: {gateway['bandwidthPoolId']}"
            )

    cloud_nodes = []
    gateway_id_set = set(gateway_ids)
    for index, node in enumerate(execution_nodes):
        if not isinstance(node, dict):
            raise ExportError(f"executionNodes[{index}] must be an object")
        execution_node_id = require_text(node.get("executionNodeId"), f"executionNodes[{index}].executionNodeId")
        node_type = require_text(node.get("type"), f"executionNodes[{index}].type")
        if node_type not in {"EDGE", "CLOUD"}:
            raise ExportError(f"executionNodes[{index}].type must be EDGE or CLOUD")
        require_positive_number(
            node.get("availableCpuCyclesPerSecond"),
            f"executionNodes[{index}].availableCpuCyclesPerSecond",
        )
        execution_node_ids.append(execution_node_id)

        if node_type == "EDGE":
            gateway_refs = node.get("gatewayIds")
            if not isinstance(gateway_refs, list) or not gateway_refs:
                raise ExportError(f"EDGE executionNodes[{index}] must have at least one gatewayId")
            for gateway_ref in gateway_refs:
                gateway_ref_text = require_text(gateway_ref, f"executionNodes[{index}].gatewayIds[]")
                if gateway_ref_text not in gateway_id_set:
                    raise ExportError(f"EDGE executionNodes[{index}] references unknown gatewayId: {gateway_ref_text}")
            require_non_negative_number(
                node.get("basePropagationDelaySeconds"),
                f"executionNodes[{index}].basePropagationDelaySeconds",
            )
        else:
            cloud_nodes.append(node)
            server_runtime_id = require_text(
                node.get("mosaicServerRuntimeId"),
                f"executionNodes[{index}].mosaicServerRuntimeId",
            )
            if server_runtime_id not in servers_by_runtime_id:
                raise ExportError(
                    f"CLOUD executionNodes[{index}].mosaicServerRuntimeId not found in SERVER_REGISTRATION: {server_runtime_id}"
                )
            access_policy = require_text(node.get("accessPolicy"), f"executionNodes[{index}].accessPolicy")
            if access_policy != THROUGH_ACTIVE_GATEWAY:
                raise ExportError(
                    f"CLOUD executionNodes[{index}].accessPolicy must be {THROUGH_ACTIVE_GATEWAY}"
                )
            require_non_negative_number(
                node.get("serverBaseDelaySeconds"),
                f"executionNodes[{index}].serverBaseDelaySeconds",
            )

    sorted_unique(execution_node_ids, "executionNodeId")
    if not cloud_nodes:
        raise ExportError("At least one CLOUD execution node is required")

    cloud_access = require_text(policies.get("cloudAccess"), "policies.cloudAccess")
    if cloud_access != THROUGH_ACTIVE_GATEWAY:
        raise ExportError(f"policies.cloudAccess must be {THROUGH_ACTIVE_GATEWAY}")
    require_text(policies.get("gatewaySelection"), "policies.gatewaySelection")
    require_text(policies.get("gatewayPoolBandwidth"), "policies.gatewayPoolBandwidth")
    require_text(policies.get("bandwidthResidualPolicy"), "policies.bandwidthResidualPolicy")


def build_region_capacity_index(regions: list[Any]) -> dict[str, dict[str, int]]:
    index: dict[str, dict[str, int]] = {}
    for region_index, region in enumerate(regions):
        if not isinstance(region, dict):
            raise ExportError(f"cell regions[{region_index}] must be an object")
        region_id = require_text(region.get("id"), f"cell regions[{region_index}].id")
        uplink = region.get("uplink")
        downlink = region.get("downlink")
        if not isinstance(uplink, dict):
            raise ExportError(f"cell region {region_id} uplink must be an object")
        if not isinstance(downlink, dict):
            raise ExportError(f"cell region {region_id} downlink must be an object")
        index[region_id] = {
            "uplink": parse_capacity_to_bps(uplink.get("capacity"), f"cell region {region_id} uplink.capacity"),
            "downlink": parse_capacity_to_bps(downlink.get("capacity"), f"cell region {region_id} downlink.capacity"),
        }
    return index


def build_bandwidth_pools(
    catalog: dict[str, Any],
    region_capacity_index: dict[str, dict[str, int]],
) -> list[dict[str, Any]]:
    exported = []
    for pool in catalog["bandwidthPools"]:
        pool_copy = deepcopy(pool)
        if pool_copy.get("poolType") == "GATEWAY":
            region_id = pool_copy["cellRegionId"]
            capacities = region_capacity_index[region_id]
            expected = min(capacities["uplink"], capacities["downlink"])
            actual = int(require_positive_number(
                pool_copy.get("nominalBandwidthBitsPerSecond"),
                f"bandwidthPools[{pool_copy.get('poolId')}].nominalBandwidthBitsPerSecond",
            ))
            if actual != expected:
                raise ExportError(
                    "gateway pool nominal bandwidth mismatch: "
                    f"{pool_copy.get('poolId')} expected {expected}, found {actual}"
                )
            pool_copy["expectedNominalBandwidthBitsPerSecond"] = expected
            pool_copy["nominalBandwidthValidation"] = "PASS"
        exported.append(pool_copy)
    return sorted(exported, key=lambda item: item["poolId"])


def sorted_regions(regions: list[Any]) -> list[Any]:
    return sorted((deepcopy(region) for region in regions), key=lambda region: region.get("id", ""))


def derive_scenario_id(resource_catalog: Path) -> str:
    parts = list(resource_catalog.parts)
    if "scenarios" in parts:
        index = parts.index("scenarios")
        if index + 1 < len(parts):
            return parts[index + 1]
    return "UNKNOWN"


def path_text(path: Path) -> str:
    return path.as_posix()


def build_warnings(
    *,
    catalog: dict[str, Any],
    sns_config: dict[str, Any],
    servers_by_runtime_id: dict[str, ServerRegistration],
    scenario_id: str,
) -> list[str]:
    warnings: list[str] = []
    description = str(catalog.get("description", ""))
    if "MaGaMosaicStudy" in description and scenario_id == "MaGaWorkloadStudy":
        warnings.append(
            "catalog.description contains MaGaMosaicStudy while resource catalog is used for MaGaWorkloadStudy"
        )

    for node in catalog.get("executionNodes", []):
        if isinstance(node, dict) and node.get("type") == "CLOUD":
            server_runtime_id = node.get("mosaicServerRuntimeId")
            server = servers_by_runtime_id.get(str(server_runtime_id))
            if server and server.profile == "WeatherServer":
                warnings.append(
                    f"CLOUD node {node.get('executionNodeId')} uses runtime server {server_runtime_id} with profile WeatherServer"
                )

    v2v_policy = catalog.get("v2vPolicy")
    if isinstance(v2v_policy, dict):
        maximum_ttl = sns_config.get("maximumTtl")
        if (
            v2v_policy.get("candidatePolicy") == "DIRECT_SINGLEHOP_ONLY"
            and isinstance(maximum_ttl, int)
            and maximum_ttl > 1
        ):
            warnings.append(
                f"v2vPolicy candidatePolicy is DIRECT_SINGLEHOP_ONLY but sns.maximumTtl is {maximum_ttl}"
            )
        if v2v_policy.get("nominalBandwidthBitsPerSecond") is None:
            warnings.append("v2vPolicy.nominalBandwidthBitsPerSecond is null")
        if v2v_policy.get("bandwidthSource") == CONFIGURED_TO_BE_CALIBRATED:
            warnings.append("v2vPolicy.bandwidthSource is CONFIGURED_VALUE_TO_BE_CALIBRATED")

    vehicle_profiles = catalog.get("vehicleProfiles")
    if isinstance(vehicle_profiles, list):
        for profile in vehicle_profiles:
            if not isinstance(profile, dict):
                continue
            profile_id = profile.get("profileId", "<unknown>")
            if profile.get("localCpuCyclesPerSecond") is None:
                warnings.append(f"vehicleProfiles[{profile_id}].localCpuCyclesPerSecond is null")
            if profile.get("cpuSource") == CONFIGURED_TO_BE_CALIBRATED:
                warnings.append(
                    f"vehicleProfiles[{profile_id}].cpuSource is CONFIGURED_VALUE_TO_BE_CALIBRATED"
                )

    return sorted(set(warnings))


def build_snapshot(
    *,
    output_csv: Path,
    cell_config_path: Path,
    network_config_path: Path,
    regions_config_path: Path,
    sns_config_path: Path,
    resource_catalog_path: Path,
    rsus: list[RsuRegistration],
    servers: list[ServerRegistration],
    cell_config: dict[str, Any],
    network_config: dict[str, Any],
    regions_config: dict[str, Any],
    sns_config: dict[str, Any],
    catalog: dict[str, Any],
    warnings: list[str],
    bandwidth_pools: list[dict[str, Any]],
) -> dict[str, Any]:
    scenario_id = derive_scenario_id(resource_catalog_path)
    rsus_by_runtime_id = {rsu.runtime_id: rsu for rsu in rsus}

    gateways = []
    for gateway in catalog["gateways"]:
        gateway_copy = deepcopy(gateway)
        rsu = rsus_by_runtime_id[gateway_copy["runtimeId"]]
        gateway_copy["latitude"] = rsu.latitude
        gateway_copy["longitude"] = rsu.longitude
        gateways.append(gateway_copy)

    return {
        "schemaVersion": "0.1",
        "source": {
            "scenarioId": scenario_id,
            "outputCsv": path_text(output_csv),
            "cellConfig": path_text(cell_config_path),
            "cellNetwork": path_text(network_config_path),
            "cellRegions": path_text(regions_config_path),
            "snsConfig": path_text(sns_config_path),
            "resourceCatalog": path_text(resource_catalog_path),
        },
        "runtimeRegistrations": {
            "rsus": [
                rsu.to_json()
                for rsu in sorted(rsus, key=lambda item: natural_key(item.runtime_id))
            ],
            "servers": [
                server.to_json()
                for server in sorted(servers, key=lambda item: natural_key(item.runtime_id))
            ],
        },
        "policies": deepcopy(catalog.get("policies", {})),
        "gateways": sorted(gateways, key=lambda item: item["gatewayId"]),
        "bandwidthPools": bandwidth_pools,
        "executionNodes": sorted(
            deepcopy(catalog["executionNodes"]),
            key=lambda item: item["executionNodeId"],
        ),
        "vehicleProfiles": sorted(
            deepcopy(catalog.get("vehicleProfiles", [])),
            key=lambda item: item.get("profileId", ""),
        ),
        "v2vPolicy": deepcopy(catalog.get("v2vPolicy", {})),
        "cell": {
            "globalNetwork": deepcopy(network_config.get("globalNetwork", {})),
            "regions": sorted_regions(regions_config.get("regions", [])),
            "bandwidthMeasurements": deepcopy(cell_config.get("bandwidthMeasurements", [])),
        },
        "sns": deepcopy(sns_config),
        "validations": {
            "errors": [],
            "warnings": warnings,
        },
    }


def write_json_safely(snapshot: dict[str, Any], out_file: Path) -> None:
    out_file.parent.mkdir(parents=True, exist_ok=True)
    temp_path: str | None = None

    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            newline="\n",
            dir=out_file.parent,
            prefix=f".{out_file.name}.",
            suffix=".tmp",
            delete=False,
        ) as temp_file:
            temp_path = temp_file.name
            json.dump(snapshot, temp_file, ensure_ascii=False, indent=2)
            temp_file.write("\n")

        os.replace(temp_path, out_file)
        temp_path = None
    finally:
        if temp_path is not None:
            try:
                Path(temp_path).unlink(missing_ok=True)
            except OSError:
                pass


def print_summary(
    *,
    output_csv: Path,
    resource_catalog: Path,
    rsus: list[RsuRegistration],
    servers: list[ServerRegistration],
    snapshot: dict[str, Any],
    out_file: Path,
) -> None:
    warnings = snapshot["validations"]["warnings"]
    print("Infrastructure snapshot export completed")
    print(f"outputCsv={output_csv}")
    print(f"resourceCatalog={resource_catalog}")
    print(f"rsuRegistrationsFound={len(rsus)}")
    print(f"serverRegistrationsFound={len(servers)}")
    print(f"gatewaysExported={len(snapshot['gateways'])}")
    print(f"bandwidthPoolsExported={len(snapshot['bandwidthPools'])}")
    print(f"executionNodesExported={len(snapshot['executionNodes'])}")
    print(f"vehicleProfilesExported={len(snapshot['vehicleProfiles'])}")
    print(f"cellRegionsExported={len(snapshot['cell']['regions'])}")
    print(f"warningsCount={len(warnings)}")
    print("errorsCount=0")
    print("warnings:")
    for warning in warnings:
        print(f"  {warning}")
    print(f"outFile={out_file}")


def main() -> int:
    args = parse_args()
    output_csv = Path(args.output_csv)
    cell_config_path = Path(args.cell_config)
    network_config_path = Path(args.network_config)
    regions_config_path = Path(args.regions_config)
    sns_config_path = Path(args.sns_config)
    resource_catalog_path = Path(args.resource_catalog)
    out_file = Path(args.out_file)

    try:
        rsus, servers = parse_output_csv(output_csv)
        cell_config = load_json_file(cell_config_path, "cell config")
        network_config = load_json_file(network_config_path, "network config")
        regions_config = load_json_file(regions_config_path, "regions config")
        sns_config = load_json_file(sns_config_path, "SNS config")
        catalog = load_json_file(resource_catalog_path, "resource catalog")

        regions = require_list(regions_config, "regions")
        region_capacity_index = build_region_capacity_index(regions)
        region_ids = set(region_capacity_index)
        rsus_by_runtime_id = {rsu.runtime_id: rsu for rsu in rsus}
        servers_by_runtime_id = {server.runtime_id: server for server in servers}

        validate_catalog(catalog, region_ids, rsus_by_runtime_id, servers_by_runtime_id)
        bandwidth_pools = build_bandwidth_pools(catalog, region_capacity_index)
        scenario_id = derive_scenario_id(resource_catalog_path)
        warnings = build_warnings(
            catalog=catalog,
            sns_config=sns_config,
            servers_by_runtime_id=servers_by_runtime_id,
            scenario_id=scenario_id,
        )

        snapshot = build_snapshot(
            output_csv=output_csv,
            cell_config_path=cell_config_path,
            network_config_path=network_config_path,
            regions_config_path=regions_config_path,
            sns_config_path=sns_config_path,
            resource_catalog_path=resource_catalog_path,
            rsus=rsus,
            servers=servers,
            cell_config=cell_config,
            network_config=network_config,
            regions_config=regions_config,
            sns_config=sns_config,
            catalog=catalog,
            warnings=warnings,
            bandwidth_pools=bandwidth_pools,
        )
        write_json_safely(snapshot, out_file)
        print_summary(
            output_csv=output_csv,
            resource_catalog=resource_catalog_path,
            rsus=rsus,
            servers=servers,
            snapshot=snapshot,
            out_file=out_file,
        )
        return 0
    except ExportError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
