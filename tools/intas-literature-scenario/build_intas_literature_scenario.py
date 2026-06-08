#!/usr/bin/env python3
"""Build a deterministic synthetic-calibrated MOSAIC scenario on an InTAS subnetwork.

The topology is extracted from the external InTAS network. Vehicle demand is
small, deterministic and generated specifically for the reduced topology. The
result keeps live SUMO mobility while avoiding fragile replay of a mid-day
InTAS state.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import os
import random
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections import Counter
from copy import deepcopy
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from validate_intas_source import detect_license, git_value, read_sumocfg_routes, validate


TOOL_DIR = Path(__file__).resolve().parent
DEFAULT_TARGETS = TOOL_DIR / "config" / "literature_scenario_targets.json"
DEFAULT_SEEDS = TOOL_DIR / "config" / "reproducibility_seeds.json"
DEFAULT_CATALOG = TOOL_DIR / "config" / "literature_calibration_catalog.json"
DEFAULT_MOBILITY_PROFILE = TOOL_DIR / "config" / "synthetic_mobility_profile.json"
SCENARIO_NAME = "MaGaLiteratureBasedUrbanStudy"
SUBSCENARIO_NAME = "intas_literature_urban"
MOBILITY_MODE = "SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK"
VEHICLE_APPLICATIONS = [
    "org.eclipse.mosaic.app.maga.adhocradio.MaGaAdHocRadioDiagnosticApp",
    "org.eclipse.mosaic.app.maga.livestate.MaGaLiveVehicleStateApp",
    "org.eclipse.mosaic.app.maga.livestate.MaGaLiveCellDiagnosticVehicleApp",
]
SERVER_APPLICATIONS = [
    "org.eclipse.mosaic.app.maga.liveruntime.MaGaLiveRuntimeCoordinatorApp",
    "org.eclipse.mosaic.app.maga.livestate.MaGaLiveCellDiagnosticServerApp",
]


@dataclass(frozen=True)
class Point:
    x: float
    y: float


@dataclass(frozen=True)
class SyntheticCandidate:
    candidate_id: str
    bounds: tuple[float, float, float, float]
    edge_ids: tuple[str, ...]
    junction_ids: tuple[str, ...]
    tls_count: int
    rsu_positions: tuple[Point, Point]


@dataclass(frozen=True)
class RouteCandidate:
    family: str
    source_edge: str
    destination_edge: str
    edge_ids: tuple[str, ...]
    route_length_meters: float
    minimum_distance_rsu_0: float
    minimum_distance_rsu_1: float
    nearest_gateway_switch: bool


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build a deterministic synthetic-calibrated MOSAIC scenario on "
            "the validated InTAS candidate_0045 subnetwork."
        )
    )
    parser.add_argument("--intas-root", required=True, help="External InTAS checkout root.")
    parser.add_argument("--output-root", required=True, help="Directory where scenario assets are written.")
    parser.add_argument("--scenario-convert", help="Optional scenario-convert path recorded in the report.")
    parser.add_argument("--seed", type=int, default=None, help="Deterministic synthetic-demand seed override.")
    parser.add_argument("--targets", default=str(DEFAULT_TARGETS), help="Scenario target JSON.")
    parser.add_argument("--seeds", default=str(DEFAULT_SEEDS), help="Seed JSON.")
    parser.add_argument("--catalog", default=str(DEFAULT_CATALOG), help="Literature catalog JSON.")
    parser.add_argument("--mobility-profile", default=str(DEFAULT_MOBILITY_PROFILE), help="Synthetic mobility JSON.")
    parser.add_argument(
        "--density",
        choices=["low_density", "nominal", "high_density", "all"],
        default="nominal",
        help="Density profile to materialize. 'all' writes all route files.",
    )
    parser.add_argument(
        "--duration-profile",
        choices=["smoke", "nominal", "extended"],
        default="nominal",
        help="MOSAIC duration profile written to scenario_config.json.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Generate text artifacts and run SUMO validation without Scenario-Convert.",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def strip_ns(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def indent(element: ET.Element) -> None:
    ET.indent(element, space="    ")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run_checked(command: list[str], cwd: Path) -> None:
    completed = subprocess.run(command, cwd=cwd, check=False)
    if completed.returncode != 0:
        raise RuntimeError(f"Command failed with exit code {completed.returncode}: {command}")


def command_path(name: str) -> str | None:
    return shutil.which(name)


def import_sumolib():
    sumo_home = os.environ.get("SUMO_HOME")
    if sumo_home:
        tools = Path(sumo_home) / "tools"
        if tools.exists():
            sys.path.insert(0, str(tools))
    try:
        import sumolib  # type: ignore
    except ImportError as exc:
        raise RuntimeError("sumolib is unavailable. Set SUMO_HOME before materialization.") from exc
    return sumolib


def find_scenario_convert(arg: str | None) -> dict[str, Any]:
    candidates: list[tuple[str, str]] = []
    if arg:
        candidates.append((arg, "argument"))
    if os.environ.get("SCENARIO_CONVERT"):
        candidates.append((os.environ["SCENARIO_CONVERT"], "SCENARIO_CONVERT"))
    for name in ("scenario-convert.bat", "scenario-convert", "scenario-convert.cmd", "scenario-convert.sh"):
        candidate = shutil.which(name)
        if candidate:
            candidates.append((candidate, "PATH"))
    for value, source in candidates:
        path = Path(value).expanduser()
        if path.exists():
            return {"available": True, "path": str(path.resolve()), "source": source}
    return {
        "available": False,
        "path": None,
        "source": None,
        "requiredAction": "Materialization PowerShell wrapper invokes Scenario-Convert after text generation.",
    }


def parse_edge_ids(path: Path) -> tuple[str, ...]:
    values = tuple(line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip())
    if not values:
        raise RuntimeError(f"Selected edge-id list is empty: {path}")
    return values


def external_net_counts(net_path: Path) -> dict[str, int]:
    root = ET.parse(net_path).getroot()
    edges = [node for node in root.iter() if strip_ns(node.tag) == "edge" and not node.attrib.get("id", "").startswith(":")]
    junctions = [
        node
        for node in root.iter()
        if strip_ns(node.tag) == "junction"
        and not node.attrib.get("id", "").startswith(":")
        and node.attrib.get("type") != "internal"
    ]
    tls = [node for node in junctions if node.attrib.get("type") == "traffic_light"]
    return {"externalEdges": len(edges), "externalJunctions": len(junctions), "trafficLights": len(tls)}


def extract_reduced_network(full_net: Path, edge_ids_file: Path, output_net: Path) -> dict[str, int]:
    netconvert = command_path("netconvert")
    if not netconvert:
        raise RuntimeError("netconvert executable not found in PATH.")
    output_net.parent.mkdir(parents=True, exist_ok=True)
    run_checked(
        [
            netconvert,
            "--sumo-net-file",
            str(full_net),
            "--keep-edges.input-file",
            str(edge_ids_file),
            "--output-file",
            str(output_net),
        ],
        output_net.parent,
    )
    return external_net_counts(output_net)


def read_location(net_path: Path) -> dict[str, Any]:
    root = ET.parse(net_path).getroot()
    location = next((node for node in root.iter() if strip_ns(node.tag) == "location"), None)
    if location is None:
        return {"netOffset": (0.0, 0.0)}
    raw_offset = location.attrib.get("netOffset", "0.0,0.0")
    parts = raw_offset.split(",")
    try:
        offset = (float(parts[0]), float(parts[1]))
    except (IndexError, ValueError):
        offset = (0.0, 0.0)
    return {**dict(location.attrib), "netOffset": offset}


def plausible_ingolstadt(position: dict[str, float]) -> bool:
    return 48.55 <= position["latitude"] <= 48.95 and 11.0 <= position["longitude"] <= 11.8


def projection_and_boundary(net: Any, net_path: Path) -> tuple[dict[str, Any], dict[str, Any]]:
    min_x, min_y, max_x, max_y = (float(value) for value in net.getBoundary())
    center_x = (min_x + max_x) / 2.0
    center_y = (min_y + max_y) / 2.0
    longitude, latitude = net.convertXY2LonLat(center_x, center_y)
    location = read_location(net_path)
    offset_x, offset_y = location["netOffset"]
    projection = {
        "valid": True,
        "method": "SUMOLIB_CONVERT_XY2LONLAT",
        "fallback": None,
        "centerCoordinates": {"latitude": latitude, "longitude": longitude},
        "cartesianOffset": {"x": offset_x, "y": offset_y},
        "plausibleIngolstadtCoordinate": plausible_ingolstadt({"latitude": latitude, "longitude": longitude}),
    }
    nw_lon, nw_lat = net.convertXY2LonLat(min_x, max_y)
    se_lon, se_lat = net.convertXY2LonLat(max_x, min_y)
    boundary = {
        "valid": True,
        "method": "SUMOLIB_CONVERT_XY2LONLAT",
        "nw": {"lat": nw_lat, "lon": nw_lon},
        "se": {"lat": se_lat, "lon": se_lon},
    }
    return projection, boundary


def mapping_position(net: Any, point: Point) -> dict[str, Any]:
    longitude, latitude = net.convertXY2LonLat(point.x, point.y)
    return {
        "latitude": latitude,
        "longitude": longitude,
        "conversionMethod": "SUMOLIB_CONVERT_XY2LONLAT",
    }


def point_to_segment_distance(px: float, py: float, ax: float, ay: float, bx: float, by: float) -> float:
    ab_x, ab_y = bx - ax, by - ay
    squared = ab_x * ab_x + ab_y * ab_y
    if squared == 0:
        return math.hypot(px - ax, py - ay)
    projection = ((px - ax) * ab_x + (py - ay) * ab_y) / squared
    projection = max(0.0, min(1.0, projection))
    closest_x = ax + projection * ab_x
    closest_y = ay + projection * ab_y
    return math.hypot(px - closest_x, py - closest_y)


def distance_to_shape(x: float, y: float, shape: Iterable[tuple[float, float]]) -> float:
    points = list(shape)
    if not points:
        return math.inf
    if len(points) == 1:
        return math.hypot(x - points[0][0], y - points[0][1])
    return min(point_to_segment_distance(x, y, a[0], a[1], b[0], b[1]) for a, b in zip(points, points[1:]))


def route_min_distance(route_edges: tuple[Any, ...], point: Point) -> float:
    return min(distance_to_shape(point.x, point.y, edge.getShape()) for edge in route_edges)


def route_has_gateway_switch(route_edges: tuple[Any, ...], rsus: tuple[Point, Point], radius: float) -> bool:
    previous_gateway: int | None = None
    for edge in route_edges:
        for x, y in edge.getShape():
            distances = [math.hypot(x - rsu.x, y - rsu.y) for rsu in rsus]
            covered = [index for index, value in enumerate(distances) if value <= radius]
            if not covered:
                previous_gateway = None
                continue
            gateway = min(covered, key=lambda index: distances[index])
            if previous_gateway is not None and gateway != previous_gateway:
                return True
            previous_gateway = gateway
    return False


def classify_route(route_edges: tuple[Any, ...], rsus: tuple[Point, Point], radius: float) -> tuple[str, float, float, bool]:
    distance_0 = route_min_distance(route_edges, rsus[0])
    distance_1 = route_min_distance(route_edges, rsus[1])
    covered_0 = distance_0 <= radius
    covered_1 = distance_1 <= radius
    switch = route_has_gateway_switch(route_edges, rsus, radius)
    if covered_0 and covered_1 and switch:
        family = "DUAL_RSU_SWITCH"
    elif covered_0 and covered_1:
        family = "BOTH_RSU_NO_SWITCH"
    elif covered_0:
        family = "RSU_0_ONLY"
    elif covered_1:
        family = "RSU_1_ONLY"
    else:
        family = "BACKGROUND"
    return family, distance_0, distance_1, switch


def generate_route_catalog(net: Any, profile: dict[str, Any], rsus: tuple[Point, Point]) -> list[RouteCandidate]:
    settings = profile["routeCatalog"]
    min_length = float(settings["minimumRouteLengthMeters"])
    max_length = float(settings["maximumRouteLengthMeters"])
    min_edges = int(settings["minimumEdgeCount"])
    radius = float(profile["rsus"][0]["coverageRadiusMeters"])
    edges = [edge for edge in net.getEdges() if not edge.getID().startswith(":") and edge.allows("passenger")]
    candidates: list[RouteCandidate] = []
    seen: set[tuple[str, ...]] = set()
    for source in edges:
        for destination in edges:
            if source.getID() == destination.getID():
                continue
            route_edges, _ = net.getShortestPath(source, destination)
            if not route_edges:
                continue
            external = tuple(edge for edge in route_edges if not edge.getID().startswith(":"))
            edge_ids = tuple(edge.getID() for edge in external)
            if len(edge_ids) < min_edges or edge_ids in seen:
                continue
            length = sum(float(edge.getLength()) for edge in external)
            if not min_length <= length <= max_length:
                continue
            seen.add(edge_ids)
            family, d0, d1, switch = classify_route(external, rsus, radius)
            candidates.append(RouteCandidate(family, source.getID(), destination.getID(), edge_ids, length, d0, d1, switch))
    family_order = {name: index for index, name in enumerate(settings["families"])}
    candidates.sort(key=lambda item: (family_order[item.family], -item.route_length_meters, item.source_edge, item.destination_edge))
    return candidates


def candidate_catalog_summary(candidates: list[RouteCandidate]) -> dict[str, Any]:
    counts = Counter(candidate.family for candidate in candidates)
    return {"candidateCount": len(candidates), "familyCounts": dict(sorted(counts.items()))}


def choose_templates(candidates: list[RouteCandidate], family: str, count: int, target_length: float) -> list[RouteCandidate]:
    ranked = sorted(
        [candidate for candidate in candidates if candidate.family == family],
        key=lambda item: (abs(item.route_length_meters - target_length), item.source_edge, item.destination_edge),
    )
    if len(ranked) < count:
        raise RuntimeError(f"Not enough route templates for {family}: required={count}, available={len(ranked)}")
    selected: list[RouteCandidate] = []
    used_sources: set[str] = set()
    used_destinations: set[str] = set()
    for item in ranked:
        if len(selected) >= count:
            break
        if item.source_edge in used_sources and item.destination_edge in used_destinations:
            continue
        selected.append(item)
        used_sources.add(item.source_edge)
        used_destinations.add(item.destination_edge)
    for item in ranked:
        if len(selected) >= count:
            break
        if item not in selected:
            selected.append(item)
    return selected


def route_paths_from_intas(intas_root: Path) -> list[Path]:
    sumocfg = intas_root / "scenario" / "InTAS_buildings.sumocfg"
    return [(sumocfg.parent / value).resolve() for value in read_sumocfg_routes(sumocfg)]


def choose_vtype(intas_root: Path, preferred_id: str = "default_001") -> ET.Element:
    fallback: ET.Element | None = None
    for route_path in route_paths_from_intas(intas_root):
        for _, element in ET.iterparse(route_path, events=("end",)):
            if strip_ns(element.tag) != "vType":
                continue
            clone = deepcopy(element)
            type_id = clone.attrib.get("id")
            element.clear()
            if fallback is None:
                fallback = clone
            if type_id == preferred_id:
                return clone
    if fallback is None:
        raise RuntimeError("No SUMO vType found in InTAS route files.")
    return fallback


def write_route_file(
    path: Path,
    candidates: list[RouteCandidate],
    density_profile: dict[str, Any],
    seed: int,
    vtype: ET.Element,
    duration: int,
    repeat_interval: float,
) -> dict[str, Any]:
    if repeat_interval <= 0:
        raise RuntimeError("Demand repeat interval must be positive.")

    root = ET.Element("routes")
    root.append(deepcopy(vtype))
    vtype_id = vtype.attrib["id"]

    selected_by_family: dict[str, list[RouteCandidate]] = {}
    route_id_by_key: dict[tuple[str, tuple[str, ...]], str] = {}

    for family, settings in density_profile["families"].items():
        templates = choose_templates(
            candidates,
            family,
            int(settings["templateCount"]),
            float(settings["targetLengthMeters"]),
        )

        selected_by_family[family] = templates

        for index, template in enumerate(templates, start=1):
            route_id = f"route_{family.lower()}_{index:02d}"

            route_id_by_key[
                (family, template.edge_ids)
            ] = route_id

            ET.SubElement(
                root,
                "route",
                {
                    "id": route_id,
                    "edges": " ".join(template.edge_ids),
                },
            )

    base_sequence: list[str] = []

    for family, settings in density_profile["families"].items():
        base_sequence.extend(
            [family] * int(settings["vehicleCount"])
        )

    random.Random(seed).shuffle(base_sequence)

    emitted = Counter()

    interval = float(
        density_profile["departureIntervalSeconds"]
    )

    repeat_cycle_count = 0
    total_vehicle_count = 0
    last_requested_departure = 0.0

    while repeat_cycle_count * repeat_interval < duration:
        cycle_offset = repeat_cycle_count * repeat_interval
        emitted_in_cycle = 0

        for vehicle_index, family in enumerate(base_sequence):
            departure = (
                cycle_offset
                + vehicle_index * interval
            )

            if departure >= duration:
                continue

            templates = selected_by_family[family]

            template = templates[
                emitted[family] % len(templates)
            ]

            route_id = route_id_by_key[
                (family, template.edge_ids)
            ]

            vehicle_id = (
                f"synthetic_{vehicle_index:03d}"
                if repeat_cycle_count == 0
                else (
                    f"synthetic_c{repeat_cycle_count:03d}_"
                    f"{vehicle_index:03d}"
                )
            )

            ET.SubElement(
                root,
                "vehicle",
                {
                    "id": vehicle_id,
                    "type": vtype_id,
                    "route": route_id,
                    "depart": f"{departure:.2f}",
                    "departLane": "best",
                    "departSpeed": "max",
                },
            )

            emitted[family] += 1
            emitted_in_cycle += 1
            total_vehicle_count += 1

            last_requested_departure = max(
                last_requested_departure,
                departure,
            )

        if emitted_in_cycle == 0:
            break

        repeat_cycle_count += 1

    indent(root)

    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    ET.ElementTree(root).write(
        path,
        encoding="utf-8",
        xml_declaration=True,
    )

    return {
        "routeFile": str(path),
        "vehicleCount": total_vehicle_count,
        "baseVehicleCount": len(base_sequence),
        "vehicleCounts": dict(sorted(emitted.items())),
        "lastRequestedDeparture": last_requested_departure,
        "seed": seed,
        "longDurationDemandExtension": {
            "mode": "PERIODIC_REPETITION_OF_CALIBRATED_BASE_DEMAND",
            "repeatIntervalSeconds": repeat_interval,
            "repeatCycleCount": repeat_cycle_count,
            "applied": repeat_cycle_count > 1,
        },
        "vTypeIdsWritten": [vtype_id],
        "routeTemplateCounts": {
            family: len(values)
            for family, values
            in sorted(selected_by_family.items())
        },
        "templates": {
            family: [
                {
                    "sourceEdge": item.source_edge,
                    "destinationEdge": item.destination_edge,
                    "routeLengthMeters": item.route_length_meters,
                    "edgeCount": len(item.edge_ids),
                }
                for item in values
            ]
            for family, values
            in sorted(selected_by_family.items())
        },
    }

def write_sumocfg(path: Path, net_file: str, route_file: str, step_length: float, duration: int) -> None:
    root = ET.Element("configuration")
    input_node = ET.SubElement(root, "input")
    ET.SubElement(input_node, "net-file", {"value": net_file})
    ET.SubElement(input_node, "route-files", {"value": route_file})
    time_node = ET.SubElement(root, "time")
    ET.SubElement(time_node, "begin", {"value": "0"})
    ET.SubElement(time_node, "end", {"value": str(duration)})
    ET.SubElement(time_node, "step-length", {"value": str(step_length)})
    indent(root)
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def analyze_fcd(path: Path, rsus: tuple[Point, Point], radius: float) -> dict[str, Any]:
    root = ET.parse(path).getroot()
    counts: list[int] = []
    timestamps: list[float] = []
    observed: set[str] = set()
    visited_0: set[str] = set()
    visited_1: set[str] = set()
    visited_both: set[str] = set()
    switches: set[str] = set()
    previous: dict[str, int | None] = {}
    switch_events = 0
    for timestep in root.iter("timestep"):
        timestamps.append(float(timestep.attrib["time"]))
        vehicles = [node for node in timestep if strip_ns(node.tag) == "vehicle"]
        counts.append(len(vehicles))
        for vehicle in vehicles:
            vehicle_id = vehicle.attrib["id"]
            observed.add(vehicle_id)
            x, y = float(vehicle.attrib["x"]), float(vehicle.attrib["y"])
            distances = [math.hypot(x - rsu.x, y - rsu.y) for rsu in rsus]
            covered = [index for index, value in enumerate(distances) if value <= radius]
            if 0 in covered:
                visited_0.add(vehicle_id)
            if 1 in covered:
                visited_1.add(vehicle_id)
            if len(covered) == 2:
                visited_both.add(vehicle_id)
            if not covered:
                previous[vehicle_id] = None
                continue
            current = min(covered, key=lambda index: distances[index])
            old = previous.get(vehicle_id)
            if old is not None and old != current:
                switch_events += 1
                switches.add(vehicle_id)
            previous[vehicle_id] = current
    return {
        "timesteps": len(timestamps),
        "firstTimestamp": min(timestamps) if timestamps else None,
        "lastTimestamp": max(timestamps) if timestamps else None,
        "meanActiveVehicles": sum(counts) / len(counts) if counts else 0.0,
        "minimumActiveVehicles": min(counts) if counts else 0,
        "maximumActiveVehicles": max(counts) if counts else 0,
        "uniqueObservedVehicles": len(observed),
        "vehiclesVisitingRsu0": len(visited_0),
        "vehiclesVisitingRsu1": len(visited_1),
        "vehiclesVisitingBothRsus": len(visited_both),
        "vehiclesWithGatewaySwitch": len(switches),
        "gatewaySwitchEvents": switch_events,
    }


def analyze_tripinfo(path: Path) -> dict[str, Any]:
    root = ET.parse(path).getroot()
    trips = [node for node in root.iter() if strip_ns(node.tag) == "tripinfo"]
    completed = [node for node in trips if float(node.attrib.get("arrival", "-1")) >= 0]
    return {"tripinfoRecords": len(trips), "completedTrips": len(completed), "unfinishedTrips": len(trips) - len(completed)}


def analyze_logs(normal_log: Path, error_log: Path) -> dict[str, Any]:
    normal = normal_log.read_text(encoding="utf-8", errors="replace") if normal_log.exists() else ""
    errors_text = error_log.read_text(encoding="utf-8", errors="replace") if error_log.exists() else ""
    merged = (normal + "\n" + errors_text).lower()
    warnings = [line for line in errors_text.splitlines() if line.startswith("Warning:")]
    errors = [line for line in errors_text.splitlines() if line.startswith("Error:")]
    return {
        "warningCount": len(warnings),
        "errorCount": len(errors),
        "teleportMentions": merged.count("teleport"),
        "emergencyBrakingMentions": merged.count("emergency braking"),
        "warnings": warnings,
        "errors": errors,
    }


def validate_mobility(route_path: Path, net_path: Path, duration: int, step_length: float, rsus: tuple[Point, Point], radius: float, settings: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    sumo = command_path("sumo")
    if not sumo:
        raise RuntimeError("sumo executable not found in PATH.")
    with tempfile.TemporaryDirectory(prefix="maga-synthetic-sumo-") as raw_dir:
        temp = Path(raw_dir)
        fcd = temp / "fcd.xml"
        tripinfo = temp / "tripinfo.xml"
        normal_log = temp / "sumo.log"
        error_log = temp / "sumo-errors.log"
        command = [
            sumo,
            "--net-file",
            str(net_path),
            "--route-files",
            str(route_path),
            "--begin",
            "0",
            "--end",
            str(duration),
            "--step-length",
            str(step_length),
            "--fcd-output",
            str(fcd),
            "--tripinfo-output",
            str(tripinfo),
            "--tripinfo-output.write-unfinished",
            "true",
            "--no-step-log",
            "true",
            "--log",
            str(normal_log),
            "--error-log",
            str(error_log),
        ]
        completed = subprocess.run(command, cwd=temp, check=False)
        if completed.returncode != 0:
            raise RuntimeError(error_log.read_text(encoding="utf-8", errors="replace") if error_log.exists() else "SUMO validation failed.")
        fcd_report = analyze_fcd(fcd, rsus, radius)
        trip_report = analyze_tripinfo(tripinfo)
        logs = analyze_logs(normal_log, error_log)
    acceptance = settings["acceptance"]
    global_acceptance = profile["validation"]
    errors: list[str] = []
    if not float(acceptance["minimumMeanActiveVehicles"]) <= float(fcd_report["meanActiveVehicles"]) <= float(acceptance["maximumMeanActiveVehicles"]):
        errors.append("Mean active vehicles is outside the configured acceptance interval.")
    if int(fcd_report["gatewaySwitchEvents"]) < int(global_acceptance["minimumGatewaySwitchEvents"]):
        errors.append("Gateway-switch events are below the configured minimum.")
    if int(logs["errorCount"]) > int(global_acceptance["maximumSumoErrors"]):
        errors.append("SUMO error count is above the configured maximum.")
    if int(logs["teleportMentions"]) > int(global_acceptance["maximumTeleportMentions"]):
        errors.append("SUMO teleport mentions are above the configured maximum.")
    if int(logs["emergencyBrakingMentions"]) > int(global_acceptance["maximumEmergencyBrakingMentions"]):
        errors.append("SUMO emergency-braking mentions are above the configured maximum.")
    return {"status": "VALID_SYNTHETIC_MOBILITY" if not errors else "INVALID_SYNTHETIC_MOBILITY", "fcd": fcd_report, "tripinfo": trip_report, "logs": logs, "errors": errors}


def write_scenario_files(scenario_dir: Path, targets: dict[str, Any], duration_profile: str, projection: dict[str, Any]) -> None:
    duration = int(targets["durationsSeconds"][duration_profile])
    scenario = {
        "simulation": {
            "id": SCENARIO_NAME,
            "duration": f"{duration}s",
            "randomSeed": 104729,
            "projection": {"centerCoordinates": projection["centerCoordinates"], "cartesianOffset": projection["cartesianOffset"]},
            "network": {
                "netMask": "255.255.0.0",
                "vehicleNet": "10.1.0.0",
                "rsuNet": "10.2.0.0",
                "tlNet": "10.3.0.0",
                "csNet": "10.4.0.0",
                "serverNet": "10.5.0.0",
                "tmcNet": "10.6.0.0",
            },
        },
        "federates": {"application": True, "cell": True, "environment": False, "sns": True, "ns3": False, "omnetpp": False, "output": True, "sumo": True},
    }
    write_json(scenario_dir / "scenario_config.json", scenario)
    write_json(scenario_dir / "sumo" / "sumo_config.json", {"sumoConfigurationFile": f"{SUBSCENARIO_NAME}.sumocfg", "updateInterval": f"{targets['sumoStepLengthSeconds']}s", "visualizer": False})


def write_mapping_config(scenario_dir: Path, rsus: tuple[Point, Point], vtype_id: str, net: Any) -> dict[str, Any]:
    mapping = {
        "config": {"fixedOrder": True},
        "prototypes": [{"name": vtype_id, "applications": VEHICLE_APPLICATIONS, "weight": 1.0}],
        "rsus": [
            {"name": f"rsu_{index}", "group": "MaGaGateway", "position": mapping_position(net, point), "applications": []}
            for index, point in enumerate(rsus)
        ],
        "servers": [{"name": "server_0", "group": "server_0", "applications": SERVER_APPLICATIONS}],
        "vehicles": [],
    }
    write_json(scenario_dir / "mapping" / "mapping_config.json", mapping)
    return mapping


def write_sns_config(scenario_dir: Path, catalog: dict[str, Any]) -> dict[str, Any]:
    nominal = catalog["v2vProfiles"]["nominal"]
    payload = {"maximumTtl": 10, "singlehopRadius": nominal["singlehopRadiusMeters"], "adhocTransmissionModel": {"type": "SophisticatedAdhocTransmissionModel"}, "singlehopDelay": dict(nominal["snsDelay"]), "singleHopTransmission": {"lossProbability": nominal["lossProbability"], "maxRetries": 0}}
    write_json(scenario_dir / "sns" / "sns_config.json", payload)
    return payload


def write_cell_configs(scenario_dir: Path, catalog: dict[str, Any], region_boundary: dict[str, Any]) -> dict[str, Any]:
    profile = catalog["cellProfiles"]["CELL_5G_AVEIRO_P50"]
    delay_ms = profile["symmetricOneWayDelaySeconds"] * 1000.0
    capacity = profile["capacityBitsPerSecond"]
    delay = {"type": "ConstantDelay", "delay": f"{delay_ms:g} ms"}
    transmission = {"lossProbability": 0.0, "maxRetries": 0}
    cell_config = {"networkConfigurationFile": "network.json", "regionConfigurationFile": "regions.json", "bandwidthMeasurementInterval": 1, "bandwidthMeasurementCompression": False, "bandwidthMeasurements": [{"fromRegion": "*", "toRegion": "*", "transmissionMode": "UplinkUnicast"}, {"fromRegion": "*", "toRegion": "*", "transmissionMode": "DownlinkUnicast"}, {"fromRegion": "*", "toRegion": "*", "transmissionMode": "DownlinkMulticast"}], "headerLengths": {"udpHeader": "8 Bytes", "tcpHeader": "20 Bytes", "ipHeader": "20 Bytes", "cellularHeader": "18 Bytes", "ethernetHeader": "18 Bytes"}}
    network = {"defaultDownlinkCapacity": "100 Gbps", "defaultUplinkCapacity": "100 Gbps", "globalNetwork": {"uplink": {"delay": delay, "transmission": transmission, "capacity": capacity}, "downlink": {"unicast": {"delay": delay, "transmission": transmission}, "multicast": {"delay": delay, "transmission": transmission, "usableCapacity": 0.6}, "capacity": capacity}}, "servers": [{"id": "server_0", "uplink": {"delay": {"type": "ConstantDelay", "delay": "50 ms"}, "transmission": transmission}, "downlink": {"unicast": {"delay": {"type": "ConstantDelay", "delay": "50 ms"}, "transmission": transmission}}}]}
    regions = {"regions": [{"id": "region_cell_5g_aveiro_p50", "area": {"nw": region_boundary["nw"], "se": region_boundary["se"]}, "uplink": {"delay": delay, "transmission": transmission, "capacity": capacity}, "downlink": {"unicast": {"delay": delay, "transmission": transmission}, "multicast": {"delay": delay, "transmission": transmission, "usableCapacity": 0.6}, "capacity": capacity}}]}
    write_json(scenario_dir / "cell" / "cell_config.json", cell_config)
    write_json(scenario_dir / "cell" / "network.json", network)
    write_json(scenario_dir / "cell" / "regions.json", regions)
    return {"cell_config": cell_config, "network": network, "regions": regions}


def write_application_config(scenario_dir: Path) -> dict[str, Any]:
    payload = {"messageCacheTime": "30s", "encodePayloads": True, "eventSchedulerThreads": 1, "navigationConfiguration": {"type": "database", "databaseFile": f"{SUBSCENARIO_NAME}.db"}, "perceptionConfiguration": {"vehicleIndex": {"enabled": True}, "trafficLightIndex": {"enabled": True}, "wallIndex": {"enabled": False}}}
    write_json(scenario_dir / "application" / "application_config.json", payload)
    return payload


def write_live_state_config(scenario_dir: Path, rsus: tuple[Point, Point], catalog: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
    v2v = catalog["v2vProfiles"]["nominal"]
    cell = catalog["cellProfiles"]["CELL_5G_AVEIRO_P50"]
    compute = catalog["computeProfiles"]
    infra = catalog["infrastructure"]
    mapping_positions = {entry["name"]: entry["position"] for entry in mapping["rsus"]}
    gateways = []
    edge_nodes = []
    for index, point in enumerate(rsus):
        runtime_id = f"rsu_{index}"
        gateways.append({"runtimeId": runtime_id, "gatewayId": runtime_id, "gatewayType": "RSU", "projectedX": point.x, "projectedY": point.y, "longitude": mapping_positions[runtime_id]["longitude"], "latitude": mapping_positions[runtime_id]["latitude"], "coverageRadiusMeters": infra["nominalRsuCoverageRadiusMeters"], "cellRegionId": "region_cell_5g_aveiro_p50", "bandwidthPoolId": f"pool_rsu_{index}"})
        edge_nodes.append({"executionNodeId": f"edge_rsu_{index}", "gatewayIds": [runtime_id], "availableCpuCyclesPerSecond": compute["edgeCpuCyclesPerSecond"], "basePropagationDelaySeconds": infra["edgeBasePropagationDelaySeconds"]})
    payload = {"tickIntervalMs": 1000, "singlehopRadiusMeters": v2v["singlehopRadiusMeters"], "localCpuCyclesPerSecond": compute["localVehicleCpuCyclesPerSecond"], "localCpuSource": "LITERATURE_BASED_RANGE_CHOICE", "remoteVehicleCpuCyclesPerSecond": compute["remoteVehicleCpuCyclesPerSecondTarget"], "remoteVehicleCpuSource": "LITERATURE_BASED_RANGE_CHOICE", "v2vNominalBandwidthBitsPerSecond": v2v["effectivePoolCapacityBitsPerSecond"], "v2vBandwidthSource": "LITERATURE_BASED_CALIBRATED_ABSTRACTION", "v2vPropagationDelaySeconds": v2v["maGaFixedDelaySeconds"], "configuredCellProfile": {"profileId": "CELL_5G_AVEIRO_P50", "technology": cell["technology"], "source": catalog["runtimeCompatibility"]["configuredCellProfileSource"], "classification": cell["classification"], "capacityBitsPerSecond": cell["capacityBitsPerSecond"], "measuredRttSeconds": cell["measuredRttSeconds"], "symmetricOneWayDelaySeconds": cell["symmetricOneWayDelaySeconds"]}, "cellDiagnosticAccounting": {"bucketDurationMs": 1000, "availableFromPolicy": "SAFE_AFTER_TIMESTAMP", "bandwidthSource": catalog["runtimeCompatibility"]["cellDiagnosticAccountingSource"], "configuredCellProfileSource": catalog["runtimeCompatibility"]["configuredCellProfileSource"], "relationship": "CONFIGURED_CELL_PROFILE_AND_RUNTIME_ACCOUNTING_ARE_DISTINCT_CONCEPTS", "destinationId": "server_0", "requestPayloadBytes": 1000, "responsePayloadBytes": 500, "intervalMs": 1000, "initialDelayMs": 1000, "maxUplinkBitrate": "49.2 Mbps", "maxDownlinkBitrate": "49.2 Mbps", "gatewayPools": [{"poolId": "pool_rsu_0", "nominalCapacityBitsPerSecond": cell["capacityBitsPerSecond"]}, {"poolId": "pool_rsu_1", "nominalCapacityBitsPerSecond": cell["capacityBitsPerSecond"]}]}, "taskProfiles": [], "workloadGeneration": catalog["workloadGeneration"], "staticInfrastructure": {"gateways": gateways, "edgeNodes": edge_nodes, "cloudNodes": [{"executionNodeId": "cloud_regional", "mosaicServerRuntimeId": "server_0", "availableCpuCyclesPerSecond": compute["cloudCpuCyclesPerSecond"], "serverBaseDelaySeconds": infra["cloudBackhaulExtraDelaySeconds"]}]}}
    write_json(scenario_dir / "application" / "ma_ga_live_state_config.json", payload)
    return payload


def write_live_runtime_config(scenario_dir: Path) -> dict[str, Any]:
    payload = {"scenarioName": SCENARIO_NAME, "coordinatorTickIntervalMs": 100, "initialOptimizationDelayMs": 1000, "gaPollingIntervalMs": 50, "singleInFlightGaOnly": True, "discardLateResult": True, "keepLastAppliedStrategyWhileRunning": True, "freshReoptimizationAfterTimeout": True, "runtimeTraceEnabled": True, "diagnosticArtificialGaDelayMs": 0, "temporalInitialWindowSeconds": 1.0, "configuredGaRuntimeEstimateSeconds": 0.01, "configuredMaxWindowSeconds": 0.2, "deltaTMaxComparisonEpsilonSeconds": 1.0e-9, "publishedSnapshotCopyLimit": 32, "nativeLiveDetailedReportingEnabled": True, "nativeLiveDetailedReportPrintToConsole": False, "gaParameterScalingMode": "STATIC"}
    write_json(scenario_dir / "application" / "ma_ga_live_runtime_config.json", payload)
    return payload


def write_runtime_configs(scenario_dir: Path, rsus: tuple[Point, Point], vtype_id: str, net: Any, catalog: dict[str, Any], region_boundary: dict[str, Any]) -> dict[str, Any]:
    mapping = write_mapping_config(scenario_dir, rsus, vtype_id, net)
    return {"mapping": mapping, "sns": write_sns_config(scenario_dir, catalog), "cell": write_cell_configs(scenario_dir, catalog, region_boundary), "application": write_application_config(scenario_dir), "liveState": write_live_state_config(scenario_dir, rsus, catalog, mapping), "liveRuntime": write_live_runtime_config(scenario_dir)}


def write_metadata(path: Path, report: dict[str, Any], catalog: dict[str, Any], mobility_profile: dict[str, Any]) -> None:
    payload = {"scenarioName": SCENARIO_NAME, "materializationStatus": report["status"], "selectedIntasCandidate": report["selectedCandidateId"], "sourceCommit": report["source"].get("commit"), "projection": {"method": report["projection"].get("method"), "fallback": report["projection"].get("fallback"), "classification": "MODELLED_DIRECTLY"}, "mobility": {"mode": MOBILITY_MODE, "topologySource": "InTAS", "topologyClassification": "MODELLED_DIRECTLY", "demandClassification": "CALIBRATED_ABSTRACTION", "repository": "https://github.com/silaslobo/InTAS", "selectedSubnetworkId": mobility_profile["sourceTopology"]["selectedSubnetworkId"], "calibrationEvidence": mobility_profile["calibrationEvidence"]}, "wirelessAssumptions": [{"name": "ITS-G5 / IEEE 802.11p", "classification": "CALIBRATED_ABSTRACTION"}, {"name": "Nominal direct V2V radius 250 m", "classification": "CALIBRATED_ABSTRACTION"}], "cellProfiles": {"configuredNominalProfile": "CELL_5G_AVEIRO_P50", "configuredProfileSource": catalog["runtimeCompatibility"]["configuredCellProfileSource"], "runtimeAccountingSource": catalog["runtimeCompatibility"]["cellDiagnosticAccountingSource"], "classification": "CALIBRATED_ABSTRACTION"}, "computeAssumptions": {"localVehicleCpuCyclesPerSecond": {"value": catalog["computeProfiles"]["localVehicleCpuCyclesPerSecond"], "classification": catalog["computeProfiles"]["localCpuClassification"]}, "remoteVehicleCpuCyclesPerSecondTarget": {"value": catalog["computeProfiles"]["remoteVehicleCpuCyclesPerSecondTarget"], "classification": catalog["computeProfiles"]["remoteVehicleCpuClassification"]}, "edgeCpuCyclesPerSecond": {"value": catalog["computeProfiles"]["edgeCpuCyclesPerSecond"], "classification": catalog["computeProfiles"]["edgeCpuClassification"]}, "cloudCpuCyclesPerSecond": {"value": catalog["computeProfiles"]["cloudCpuCyclesPerSecond"], "classification": catalog["computeProfiles"]["cloudCpuClassification"]}}, "workloadGeneration": {"workloadMode": catalog["workloadGeneration"]["mode"], "randomSeed": catalog["workloadGeneration"]["randomSeed"], "arrivalRateTasksPerSecondPerActiveVehicle": catalog["workloadGeneration"]["arrivalRateTasksPerSecondPerActiveVehicle"], "classification": catalog["workloadGeneration"]["classification"]}, "pendingLiveStateExtensions": ["calibrated workload rate matrix", "40 replicate runner"]}
    write_json(path, payload)


def write_report_markdown(path: Path, report: dict[str, Any]) -> None:
    nominal = report["routeSubsets"].get("nominal") or next(iter(report["routeSubsets"].values()))
    mobility = nominal["mobilityValidation"]
    fcd = mobility["fcd"]
    lines = ["# Synthetic-Calibrated InTAS Literature Scenario", "", f"- status: `{report['status']}`", f"- mobility mode: `{MOBILITY_MODE}`", f"- topology source: `{report['source']['repository']}`", f"- selected subnetwork: `{report['selectedCandidateId']}`", f"- external edges: `{report['reducedNetworkCounts']['externalEdges']}`", f"- external junctions: `{report['reducedNetworkCounts']['externalJunctions']}`", f"- traffic lights: `{report['reducedNetworkCounts']['trafficLights']}`", "", "## Nominal synthetic mobility validation", "", f"- generated vehicles: `{nominal['vehicleCount']}`", f"- mean active vehicles: `{fcd['meanActiveVehicles']:.2f}`", f"- maximum active vehicles: `{fcd['maximumActiveVehicles']}`", f"- vehicles visiting both RSUs: `{fcd['vehiclesVisitingBothRsus']}`", f"- gateway-switch events: `{fcd['gatewaySwitchEvents']}`", f"- SUMO errors: `{mobility['logs']['errorCount']}`", f"- teleport mentions: `{mobility['logs']['teleportMentions']}`", f"- emergency-braking mentions: `{mobility['logs']['emergencyBrakingMentions']}`", "", "## Notes", "", "The scenario keeps the validated InTAS urban topology but generates deterministic synthetic demand. It intentionally avoids replay of an intermediate InTAS save-state."]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    intas_root = Path(args.intas_root).resolve()
    output_root = Path(args.output_root).resolve()
    targets = load_json(Path(args.targets))
    seeds = load_json(Path(args.seeds))["seeds"]
    catalog = load_json(Path(args.catalog))
    mobility_profile = load_json(Path(args.mobility_profile))
    if mobility_profile.get("mobilityMode") != MOBILITY_MODE:
        raise RuntimeError(f"Unsupported mobility mode: {mobility_profile.get('mobilityMode')}")
    validation, validation_exit = validate(intas_root)
    if validation_exit != 0:
        print(json.dumps(validation, indent=2, sort_keys=True))
        return validation_exit
    sumolib = import_sumolib()
    output_scenario = output_root / SCENARIO_NAME
    for relative in ("application", "mapping", "sumo", "sns", "cell", "reports"):
        (output_scenario / relative).mkdir(parents=True, exist_ok=True)
    source_topology = mobility_profile["sourceTopology"]
    full_net = intas_root / source_topology["networkFile"]
    edge_ids_file = Path(args.mobility_profile).resolve().parent / source_topology["selectedEdgeIdsFile"]
    edge_ids = parse_edge_ids(edge_ids_file)
    reduced_net = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}.net.xml"
    net_counts = extract_reduced_network(full_net, edge_ids_file, reduced_net)
    for key, expected_key in (("externalEdges", "expectedExternalEdgeCount"), ("externalJunctions", "expectedExternalJunctionCount"), ("trafficLights", "expectedTrafficLightCount")):
        expected = int(source_topology[expected_key])
        if int(net_counts[key]) != expected:
            raise RuntimeError(f"Reduced network {key} expected {expected}, found {net_counts[key]}")
    net = sumolib.net.readNet(str(reduced_net))
    projection, boundary = projection_and_boundary(net, reduced_net)
    if not projection["plausibleIngolstadtCoordinate"]:
        raise RuntimeError("Reduced-network projection is not plausible for Ingolstadt.")
    rsus = tuple(Point(float(entry["projectedX"]), float(entry["projectedY"])) for entry in mobility_profile["rsus"])
    if len(rsus) != 2:
        raise RuntimeError("Exactly two RSUs are required by the nominal scenario.")
    rsus = (rsus[0], rsus[1])
    candidates = generate_route_catalog(net, mobility_profile, rsus)
    summary = candidate_catalog_summary(candidates)
    families = set(mobility_profile["routeCatalog"]["families"])
    if families - set(summary["familyCounts"]):
        raise RuntimeError(f"Route catalog misses families: {sorted(families - set(summary['familyCounts']))}")
    preferred_vtype = choose_vtype(intas_root)
    requested_densities = list(mobility_profile["densityProfiles"]) if args.density == "all" else [args.density]
    duration = int(targets["durationsSeconds"][args.duration_profile])
    step_length = float(targets["sumoStepLengthSeconds"])
    long_duration_extension = mobility_profile["longDurationDemandExtension"]
    repeat_interval = float(long_duration_extension["repeatIntervalSeconds"])
    route_outputs: dict[str, Any] = {}
    for index, density in enumerate(requested_densities):
        density_profile = mobility_profile["densityProfiles"][density]
        seed = int(args.seed if args.seed is not None else seeds[index % len(seeds)])
        route_file = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}_{density}.rou.xml"
        output = write_route_file(
            route_file,
            candidates,
            density_profile,
            seed,
            preferred_vtype,
            duration,
            repeat_interval,
        )
        output["targetMeanActiveVehicles"] = density_profile["targetMeanActiveVehicles"]
        output["mobilityValidation"] = validate_mobility(route_file, reduced_net, duration, step_length, rsus, float(mobility_profile["rsus"][0]["coverageRadiusMeters"]), density_profile, mobility_profile)
        if output["mobilityValidation"]["errors"]:
            raise RuntimeError(f"Synthetic mobility validation failed for {density}: {output['mobilityValidation']['errors']}")
        output["meanActiveVehicles"] = output["mobilityValidation"]["fcd"]["meanActiveVehicles"]
        output["maxActiveVehicles"] = output["mobilityValidation"]["fcd"]["maximumActiveVehicles"]
        route_outputs[density] = output
    concrete_density = "nominal" if "nominal" in route_outputs else requested_densities[0]
    concrete_route = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}_{concrete_density}.rou.xml"
    write_sumocfg(output_scenario / "sumo" / f"{SUBSCENARIO_NAME}.sumocfg", reduced_net.name, concrete_route.name, step_length, duration)
    write_scenario_files(output_scenario, targets, args.duration_profile, projection)
    generated = write_runtime_configs(output_scenario, rsus, preferred_vtype.attrib["id"], net, catalog, boundary)
    scenario_convert = find_scenario_convert(args.scenario_convert)
    candidate = SyntheticCandidate(source_topology["selectedSubnetworkId"], tuple(float(v) for v in net.getBoundary()), edge_ids, tuple(), int(net_counts["trafficLights"]), rsus)
    report = {
        "scenarioName": SCENARIO_NAME,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "status": "SYNTHETIC_CALIBRATED_SCENARIO_GENERATED",
        "textualConfigurationStatus": "GENERATED",
        "mobilityMode": MOBILITY_MODE,
        "source": {"name": "InTAS", "repository": "https://github.com/silaslobo/InTAS", "root": str(intas_root), "commit": git_value(intas_root, "rev-parse", "HEAD"), "tag": git_value(intas_root, "describe", "--tags", "--exact-match", "HEAD"), "license": detect_license(intas_root), "sourceFiles": [source_topology["networkFile"]]},
        "targets": targets,
        "seeds": seeds,
        "selectedSeed": int(args.seed if args.seed is not None else seeds[0]),
        "syntheticMobilityProfile": mobility_profile,
        "selectedCandidateId": candidate.candidate_id,
        "selectedCandidate": {"candidateId": candidate.candidate_id, "candidateRsuPositions": [{"x": point.x, "y": point.y} for point in candidate.rsu_positions], "gatewaySwitchPotential": True, "mobilityMode": MOBILITY_MODE},
        "candidateCount": 1,
        "candidates": [],
        "candidateSearchExpansions": [],
        "routeCatalog": summary,
        "routeSubsets": route_outputs,
        "usedVTypeIds": [preferred_vtype.attrib["id"]],
        "projection": projection,
        "cellRegionGeographicBoundary": boundary,
        "reducedNetwork": str(reduced_net),
        "reducedNetworkCounts": net_counts,
        "selectedEdgeIdsFile": str(edge_ids_file),
        "selectedEdgeIdsSha256": sha256(edge_ids_file),
        "scenarioConvert": scenario_convert,
        "generatedConfigurations": generated,
        "errors": [],
        "warnings": [],
    }
    write_metadata(output_scenario / "application" / "ma_ga_calibration_metadata.json", report, catalog, mobility_profile)
    write_json(output_scenario / "reports" / "intas_literature_materialization_report.json", report)
    write_report_markdown(output_scenario / "reports" / "intas_literature_materialization_report.md", report)
    standalone = subprocess.run([sys.executable, str(TOOL_DIR / "validate_literature_configuration.py"), "--scenario-root", str(output_scenario)], check=False)
    if standalone.returncode != 0:
        raise RuntimeError("Generated textual configuration validation failed.")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
