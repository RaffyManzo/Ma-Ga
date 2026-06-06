#!/usr/bin/env python3
"""Materialize a deterministic InTAS-derived MOSAIC scenario scaffold.

The script intentionally does not bundle InTAS into this repository. It reads an
external checkout passed via --intas-root and writes generated scenario assets to
an explicit --output-root.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import random
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from validate_intas_source import detect_license, git_value, read_sumocfg_routes, validate


TOOL_DIR = Path(__file__).resolve().parent
DEFAULT_TARGETS = TOOL_DIR / "config" / "literature_scenario_targets.json"
DEFAULT_SEEDS = TOOL_DIR / "config" / "reproducibility_seeds.json"
SCENARIO_NAME = "MaGaLiteratureBasedUrbanStudy"
SUBSCENARIO_NAME = "intas_literature_urban"
NS = "http://sumo.dlr.de/xsd/routes_file.xsd"


@dataclass(frozen=True)
class Point:
    x: float
    y: float


@dataclass(frozen=True)
class Edge:
    edge_id: str
    from_node: str | None
    to_node: str | None
    speed: float
    length: float
    shape: tuple[Point, ...]


@dataclass(frozen=True)
class Junction:
    junction_id: str
    x: float
    y: float
    kind: str


@dataclass(frozen=True)
class RouteVehicle:
    vehicle_id: str
    depart: float
    route_edges: tuple[str, ...]
    vtype: str | None
    vclass: str
    estimated_duration: float


@dataclass(frozen=True)
class Candidate:
    candidate_id: str
    bounds: tuple[float, float, float, float]
    edge_ids: tuple[str, ...]
    junction_ids: tuple[str, ...]
    tls_count: int
    connected_components: int
    largest_component_share: float
    routes: tuple[RouteVehicle, ...]
    active_by_second: dict[int, int]
    mean_active: float
    max_active: int
    target_error: float
    rsu_positions: tuple[Point, Point] | tuple[()]
    rsu_pair_distance: float | None
    overlap_estimated: bool
    gateway_switch_potential: bool
    score: float
    rejection_reasons: tuple[str, ...]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a deterministic MOSAIC scenario from an external InTAS checkout."
    )
    parser.add_argument("--intas-root", required=True, help="External InTAS checkout root.")
    parser.add_argument("--output-root", required=True, help="Directory where generated scenario assets are written.")
    parser.add_argument("--scenario-convert", help="Path to scenario-convert.sh/.bat. Defaults to SCENARIO_CONVERT or PATH.")
    parser.add_argument("--targets", default=str(DEFAULT_TARGETS), help="Target JSON configuration.")
    parser.add_argument("--seeds", default=str(DEFAULT_SEEDS), help="Seed JSON configuration.")
    parser.add_argument(
        "--density",
        choices=["low_density", "nominal", "high_density", "all"],
        default="all",
        help="Route subset to materialize. 'all' writes all three route subsets.",
    )
    parser.add_argument(
        "--duration-profile",
        choices=["smoke", "nominal", "extended"],
        default="nominal",
        help="Duration written to the concrete MOSAIC scenario_config.json.",
    )
    parser.add_argument("--dry-run", action="store_true", help="Evaluate and report candidates without running netconvert or scenario-convert.")
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def split_shape(value: str) -> tuple[Point, ...]:
    points: list[Point] = []
    for raw in value.split():
        if "," not in raw:
            continue
        x, y = raw.split(",", 1)
        points.append(Point(float(x), float(y)))
    return tuple(points)


def distance(a: Point, b: Point) -> float:
    return math.hypot(a.x - b.x, a.y - b.y)


def polyline_length(shape: Iterable[Point]) -> float:
    points = list(shape)
    return sum(distance(a, b) for a, b in zip(points, points[1:]))


def parse_network(net_path: Path) -> tuple[dict[str, Edge], dict[str, Junction], dict[str, Any]]:
    tree = ET.parse(net_path)
    root = tree.getroot()
    location = root.find("location")
    metadata: dict[str, Any] = {"location": dict(location.attrib) if location is not None else {}}

    junctions: dict[str, Junction] = {}
    for node in root.findall("junction"):
        node_id = node.attrib.get("id")
        if not node_id:
            continue
        junctions[node_id] = Junction(
            junction_id=node_id,
            x=float(node.attrib.get("x", "0")),
            y=float(node.attrib.get("y", "0")),
            kind=node.attrib.get("type", ""),
        )

    edges: dict[str, Edge] = {}
    for edge in root.findall("edge"):
        edge_id = edge.attrib.get("id")
        if not edge_id or edge.attrib.get("function") == "internal":
            continue
        lanes = edge.findall("lane")
        if not lanes:
            continue
        lane = lanes[0]
        shape = split_shape(lane.attrib.get("shape", ""))
        if not shape:
            from_node = junctions.get(edge.attrib.get("from", ""))
            to_node = junctions.get(edge.attrib.get("to", ""))
            if from_node and to_node:
                shape = (Point(from_node.x, from_node.y), Point(to_node.x, to_node.y))
        if not shape:
            continue
        speed = float(lane.attrib.get("speed", "13.9"))
        length = float(lane.attrib.get("length", str(polyline_length(shape))))
        edges[edge_id] = Edge(
            edge_id=edge_id,
            from_node=edge.attrib.get("from"),
            to_node=edge.attrib.get("to"),
            speed=max(speed, 0.1),
            length=max(length, polyline_length(shape)),
            shape=shape,
        )
    return edges, junctions, metadata


def parse_route_files(route_paths: list[Path], edge_lookup: dict[str, Edge]) -> tuple[dict[str, str], list[RouteVehicle]]:
    vclasses: dict[str, str] = {}
    vehicles: list[RouteVehicle] = []
    for route_path in route_paths:
        tree = ET.parse(route_path)
        root = tree.getroot()
        for vtype in root.iter():
            if strip_ns(vtype.tag) == "vType":
                type_id = vtype.attrib.get("id")
                if type_id:
                    vclasses[type_id] = vtype.attrib.get("vClass", "passenger")
        for vehicle in root.iter():
            if strip_ns(vehicle.tag) != "vehicle":
                continue
            vehicle_id = vehicle.attrib.get("id")
            if not vehicle_id:
                continue
            vtype = vehicle.attrib.get("type")
            vclass = vclasses.get(vtype or "", "passenger")
            if vclass in {"pedestrian", "bicycle", "bus"}:
                continue
            route_edges = extract_route_edges(vehicle)
            if not route_edges:
                continue
            depart = parse_depart(vehicle.attrib.get("depart", "0"))
            duration = sum(edge_lookup[e].length / edge_lookup[e].speed for e in route_edges if e in edge_lookup)
            vehicles.append(
                RouteVehicle(
                    vehicle_id=vehicle_id,
                    depart=depart,
                    route_edges=tuple(route_edges),
                    vtype=vtype,
                    vclass=vclass,
                    estimated_duration=max(duration, 1.0),
                )
            )
    return vclasses, vehicles


def strip_ns(tag: str) -> str:
    return tag.split("}", 1)[-1]


def extract_route_edges(vehicle: ET.Element) -> list[str]:
    for child in vehicle:
        if strip_ns(child.tag) == "route":
            return [edge for edge in child.attrib.get("edges", "").split() if edge]
    value = vehicle.attrib.get("route")
    return [value] if value else []


def parse_depart(value: str) -> float:
    if value in {"triggered", "containerTriggered"}:
        return 0.0
    return float(value)


def compute_bounds(edges: Iterable[Edge]) -> tuple[float, float, float, float]:
    points = [point for edge in edges for point in edge.shape]
    return min(p.x for p in points), min(p.y for p in points), max(p.x for p in points), max(p.y for p in points)


def in_bounds(point: Point, bounds: tuple[float, float, float, float]) -> bool:
    min_x, min_y, max_x, max_y = bounds
    return min_x <= point.x <= max_x and min_y <= point.y <= max_y


def edge_inside(edge: Edge, bounds: tuple[float, float, float, float]) -> bool:
    return any(in_bounds(point, bounds) for point in edge.shape)


def route_inside(route: RouteVehicle, selected_edges: set[str]) -> bool:
    return bool(route.route_edges) and all(edge in selected_edges for edge in route.route_edges)


def connected_components(selected_edges: Iterable[Edge]) -> tuple[int, float]:
    adjacency: dict[str, set[str]] = {}
    for edge in selected_edges:
        if not edge.from_node or not edge.to_node:
            continue
        adjacency.setdefault(edge.from_node, set()).add(edge.to_node)
        adjacency.setdefault(edge.to_node, set()).add(edge.from_node)
    if not adjacency:
        return 0, 0.0
    seen: set[str] = set()
    sizes: list[int] = []
    for node in sorted(adjacency):
        if node in seen:
            continue
        stack = [node]
        seen.add(node)
        size = 0
        while stack:
            current = stack.pop()
            size += 1
            for neighbor in adjacency[current]:
                if neighbor not in seen:
                    seen.add(neighbor)
                    stack.append(neighbor)
        sizes.append(size)
    return len(sizes), max(sizes) / max(sum(sizes), 1)


def active_counts(routes: Iterable[RouteVehicle], duration_seconds: int) -> dict[int, int]:
    counts = {second: 0 for second in range(duration_seconds + 1)}
    for route in routes:
        start = int(math.floor(route.depart))
        end = int(math.ceil(route.depart + route.estimated_duration))
        for second in range(max(0, start), min(duration_seconds, end) + 1):
            counts[second] += 1
    return counts


def route_crosses_both(route: RouteVehicle, edges: dict[str, Edge], a: Point, b: Point, radius: float) -> bool:
    near_a = False
    near_b = False
    for edge_id in route.route_edges:
        edge = edges.get(edge_id)
        if not edge:
            continue
        for point in edge.shape:
            near_a = near_a or distance(point, a) <= radius
            near_b = near_b or distance(point, b) <= radius
            if near_a and near_b:
                return True
    return False


def choose_rsus(
    candidate_junctions: list[Junction],
    routes: Iterable[RouteVehicle],
    edges: dict[str, Edge],
    radius: float,
) -> tuple[tuple[Point, Point] | tuple[()], float | None, bool, bool]:
    tls = [j for j in candidate_junctions if j.kind == "traffic_light"]
    base = tls if len(tls) >= 2 else candidate_junctions
    best: tuple[tuple[Point, Point] | tuple[()], float | None, bool, bool, float] = ((), None, False, False, -1.0)
    for i, left in enumerate(base):
        for right in base[i + 1 :]:
            a = Point(left.x, left.y)
            b = Point(right.x, right.y)
            pair_distance = distance(a, b)
            partial_overlap = radius < pair_distance < (2 * radius)
            switch = any(route_crosses_both(route, edges, a, b, radius) for route in routes)
            score = (2.0 if partial_overlap else 0.0) + (3.0 if switch else 0.0) - abs(pair_distance - (1.5 * radius)) / radius
            if score > best[4]:
                best = ((a, b), pair_distance, partial_overlap, switch, score)
    return best[0], best[1], best[2], best[3]


def candidate_grid(edges: dict[str, Edge], targets: dict[str, Any]) -> list[tuple[float, float, float, float]]:
    min_x, min_y, max_x, max_y = compute_bounds(edges.values())
    grid_cfg = targets.get("materialization", {}).get("candidateGrid", {})
    size = float(grid_cfg.get("windowSizeMeters", 900))
    stride = float(grid_cfg.get("strideMeters", 300))
    max_candidates = int(grid_cfg.get("maxCandidates", 64))
    windows: list[tuple[float, float, float, float]] = []
    y = min_y
    while y + size <= max_y + 1e-9:
        x = min_x
        while x + size <= max_x + 1e-9:
            windows.append((x, y, x + size, y + size))
            x += stride
        y += stride
    if not windows:
        windows.append((min_x, min_y, max_x, max_y))
    digest_sorted = sorted(windows, key=lambda b: hashlib.sha256(repr(b).encode("ascii")).hexdigest())
    return digest_sorted[:max_candidates]


def evaluate_candidates(
    edges: dict[str, Edge],
    junctions: dict[str, Junction],
    routes: list[RouteVehicle],
    targets: dict[str, Any],
) -> list[Candidate]:
    requirements = targets["subscenarioSelection"]["requirements"]
    radius = float(requirements["nominalRsuRadiusMeters"])
    duration = int(targets["durationsSeconds"]["nominal"])
    target_active = int(targets["activeVehicleTargets"]["nominal"])
    candidates: list[Candidate] = []
    for index, bounds in enumerate(candidate_grid(edges, targets)):
        selected_edges = [edge for edge in edges.values() if edge_inside(edge, bounds)]
        selected_edge_ids = {edge.edge_id for edge in selected_edges}
        candidate_junctions = [
            junction
            for junction in junctions.values()
            if in_bounds(Point(junction.x, junction.y), bounds)
        ]
        selected_routes = [route for route in routes if route_inside(route, selected_edge_ids)]
        components, largest_share = connected_components(selected_edges)
        counts = active_counts(selected_routes, duration)
        mean_active = sum(counts.values()) / max(len(counts), 1)
        max_active = max(counts.values()) if counts else 0
        target_error = abs(mean_active - target_active)
        rsus, pair_distance, overlap, switch = choose_rsus(candidate_junctions, selected_routes, edges, radius)
        tls_count = sum(1 for junction in candidate_junctions if junction.kind == "traffic_light")
        rejection = []
        if tls_count < int(requirements["minimumTrafficLightCount"]):
            rejection.append("INSUFFICIENT_TRAFFIC_LIGHTS")
        if components == 0 or largest_share < 0.85:
            rejection.append("DRIVABLE_GRAPH_NOT_DOMINANTLY_CONNECTED")
        if len(rsus) != 2:
            rejection.append("NO_RSU_PAIR_CANDIDATE")
        if not overlap:
            rejection.append("NO_PARTIAL_RSU_COVERAGE_OVERLAP")
        if not switch:
            rejection.append("NO_GATEWAY_SWITCH_POTENTIAL")
        if not selected_routes:
            rejection.append("NO_PASSENGER_ROUTES_INSIDE_CANDIDATE")
        score = (
            (tls_count * 2.0)
            + (largest_share * 3.0)
            + (2.0 / (1.0 + target_error))
            + (2.0 if switch else 0.0)
            + (1.0 if overlap else 0.0)
            - (0.5 * len(rejection))
        )
        candidates.append(
            Candidate(
                candidate_id=f"candidate_{index:03d}",
                bounds=bounds,
                edge_ids=tuple(sorted(selected_edge_ids)),
                junction_ids=tuple(sorted(j.junction_id for j in candidate_junctions)),
                tls_count=tls_count,
                connected_components=components,
                largest_component_share=largest_share,
                routes=tuple(selected_routes),
                active_by_second=counts,
                mean_active=mean_active,
                max_active=max_active,
                target_error=target_error,
                rsu_positions=rsus,
                rsu_pair_distance=pair_distance,
                overlap_estimated=overlap,
                gateway_switch_potential=switch,
                score=score,
                rejection_reasons=tuple(rejection),
            )
        )
    return sorted(candidates, key=lambda c: (-c.score, c.candidate_id))


def select_route_subset(routes: tuple[RouteVehicle, ...], target: int, duration: int, seed: int) -> tuple[RouteVehicle, ...]:
    ordered = list(routes)
    random.Random(seed).shuffle(ordered)
    selected: list[RouteVehicle] = []
    best: list[RouteVehicle] = []
    best_error = float("inf")
    for route in ordered:
        selected.append(route)
        counts = active_counts(selected, duration)
        mean_active = sum(counts.values()) / max(len(counts), 1)
        error = abs(mean_active - target)
        if error < best_error:
            best = list(selected)
            best_error = error
        if mean_active >= target:
            break
    return tuple(sorted(best, key=lambda r: (r.depart, r.vehicle_id)))


def write_route_file(path: Path, vehicles: tuple[RouteVehicle, ...], vclasses: dict[str, str]) -> None:
    routes = ET.Element("routes")
    emitted_types: set[str] = set()
    for vehicle in vehicles:
        if vehicle.vtype and vehicle.vtype not in emitted_types:
            ET.SubElement(routes, "vType", {"id": vehicle.vtype, "vClass": vclasses.get(vehicle.vtype, "passenger")})
            emitted_types.add(vehicle.vtype)
    for vehicle in vehicles:
        attrs = {"id": vehicle.vehicle_id, "depart": f"{vehicle.depart:.2f}"}
        if vehicle.vtype:
            attrs["type"] = vehicle.vtype
        node = ET.SubElement(routes, "vehicle", attrs)
        ET.SubElement(node, "route", {"edges": " ".join(vehicle.route_edges)})
    indent(routes)
    ET.ElementTree(routes).write(path, encoding="utf-8", xml_declaration=True)


def indent(element: ET.Element, level: int = 0) -> None:
    spacer = "\n" + level * "  "
    if len(element):
        if not element.text or not element.text.strip():
            element.text = spacer + "  "
        for child in element:
            indent(child, level + 1)
        if not child.tail or not child.tail.strip():
            child.tail = spacer
    if level and (not element.tail or not element.tail.strip()):
        element.tail = spacer


def find_scenario_convert(arg: str | None) -> str | None:
    candidates = [arg, os.environ.get("SCENARIO_CONVERT"), shutil.which("scenario-convert.sh"), shutil.which("scenario-convert.bat"), shutil.which("scenario-convert")]
    return next((candidate for candidate in candidates if candidate), None)


def run_checked(command: list[str], cwd: Path) -> None:
    subprocess.run(command, cwd=str(cwd), check=True)


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def geographic_boundary(bounds: tuple[float, float, float, float], metadata: dict[str, Any]) -> dict[str, Any]:
    location = metadata.get("location", {})
    try:
        conv = [float(v) for v in location.get("convBoundary", "").split(",")]
        orig = [float(v) for v in location.get("origBoundary", "").split(",")]
        if len(conv) == 4 and len(orig) == 4:
            min_x, min_y, max_x, max_y = bounds
            lon0, lat0, lon1, lat1 = orig
            cx0, cy0, cx1, cy1 = conv
            def interp_x(x: float) -> float:
                return lon0 + ((x - cx0) / (cx1 - cx0)) * (lon1 - lon0)
            def interp_y(y: float) -> float:
                return lat0 + ((y - cy0) / (cy1 - cy0)) * (lat1 - lat0)
            return {
                "method": "LINEAR_INTERPOLATION_FROM_SUMO_LOCATION_BOUNDARIES",
                "minLongitude": interp_x(min_x),
                "minLatitude": interp_y(min_y),
                "maxLongitude": interp_x(max_x),
                "maxLatitude": interp_y(max_y),
            }
    except (ValueError, ZeroDivisionError):
        pass
    return {"method": "UNAVAILABLE_FROM_NET_LOCATION_METADATA"}


def point_position_for_mapping(point: Point, metadata: dict[str, Any]) -> dict[str, Any]:
    location = metadata.get("location", {})
    try:
        conv = [float(v) for v in location.get("convBoundary", "").split(",")]
        orig = [float(v) for v in location.get("origBoundary", "").split(",")]
        if len(conv) == 4 and len(orig) == 4:
            lon0, lat0, lon1, lat1 = orig
            cx0, cy0, cx1, cy1 = conv
            longitude = lon0 + ((point.x - cx0) / (cx1 - cx0)) * (lon1 - lon0)
            latitude = lat0 + ((point.y - cy0) / (cy1 - cy0)) * (lat1 - lat0)
            return {"longitude": longitude, "latitude": latitude}
    except (ValueError, ZeroDivisionError):
        pass
    return {
        "longitude": "<projection-unavailable-review-required>",
        "latitude": "<projection-unavailable-review-required>",
        "cartesianX": point.x,
        "cartesianY": point.y,
    }


def candidate_report(
    candidate: Candidate,
    metadata: dict[str, Any],
    all_route_count: int,
    edge_lookup: dict[str, Edge],
) -> dict[str, Any]:
    min_x, min_y, max_x, max_y = candidate.bounds
    speeds = [edge_lookup[edge_id].speed for edge_id in candidate.edge_ids if edge_id in edge_lookup]
    return {
        "candidateId": candidate.candidate_id,
        "geographicBoundary": geographic_boundary(candidate.bounds, metadata),
        "cartesianBoundary": {"minX": min_x, "minY": min_y, "maxX": max_x, "maxY": max_y},
        "areaKm2": ((max_x - min_x) * (max_y - min_y)) / 1_000_000.0,
        "drivableEdgeCount": len(candidate.edge_ids),
        "junctionCount": len(candidate.junction_ids),
        "trafficLightCount": candidate.tls_count,
        "connectedComponentCount": candidate.connected_components,
        "largestConnectedComponentShare": candidate.largest_component_share,
        "routeCountBeforeFiltering": all_route_count,
        "routeCountAfterFiltering": len(candidate.routes),
        "activeVehicleCountBySecond": candidate.active_by_second,
        "meanActiveVehicleCount": candidate.mean_active,
        "maxActiveVehicleCount": candidate.max_active,
        "vehicleCountTargetError": candidate.target_error,
        "speedSummary": {
            "samples": len(speeds),
            "minMetersPerSecond": min(speeds) if speeds else None,
            "meanMetersPerSecond": (sum(speeds) / len(speeds)) if speeds else None,
            "maxMetersPerSecond": max(speeds) if speeds else None,
        },
        "candidateRsuPositions": [
            {"x": point.x, "y": point.y} for point in candidate.rsu_positions
        ],
        "rsuPairDistanceMeters": candidate.rsu_pair_distance,
        "rsuCoverageOverlapEstimated": candidate.overlap_estimated,
        "gatewaySwitchPotential": candidate.gateway_switch_potential,
        "selectionScore": candidate.score,
        "rejectionReasons": list(candidate.rejection_reasons),
    }


def write_templates(
    scenario_dir: Path,
    candidate: Candidate,
    targets: dict[str, Any],
    duration_profile: str,
    metadata: dict[str, Any],
) -> None:
    duration = targets["durationsSeconds"][duration_profile]
    scenario = {
        "simulation": {
            "id": SCENARIO_NAME,
            "duration": f"{duration}s",
            "randomSeed": 104729,
            "projection": {
                "centerCoordinates": {"latitude": "<derived-by-scenario-convert>", "longitude": "<derived-by-scenario-convert>"},
                "cartesianOffset": {"x": "<derived-by-scenario-convert>", "y": "<derived-by-scenario-convert>"},
            },
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
        "federates": {
            "application": True,
            "cell": False,
            "environment": False,
            "sns": True,
            "ns3": False,
            "omnetpp": False,
            "output": True,
            "sumo": True,
        },
    }
    sumo_config = {
        "sumoConfigurationFile": f"{SUBSCENARIO_NAME}.sumocfg",
        "updateInterval": f"{targets['sumoStepLengthSeconds']}s",
        "visualizer": False,
    }
    mapping = {
        "config": {"fixedOrder": True},
        "prototypes": [{"name": "Car", "vehicleClass": "ElectricVehicle"}],
        "rsus": [
            {
                "name": f"rsu_{index}",
                "group": "MaGaGateway",
                "position": point_position_for_mapping(point, metadata),
                "applications": [],
            }
            for index, point in enumerate(candidate.rsu_positions)
        ],
        "servers": [{"name": "server_0", "group": "MaGaLiveStateCoordinator", "applications": []}],
        "vehicles": [],
    }
    write_json(scenario_dir / "scenario_config.json", scenario)
    write_json(scenario_dir / "sumo" / "sumo_config.json", sumo_config)
    write_json(scenario_dir / "mapping" / "mapping_config.json", mapping)


def main() -> int:
    args = parse_args()
    targets = load_json(Path(args.targets))
    seeds = load_json(Path(args.seeds))["seeds"]
    intas_root = Path(args.intas_root).resolve()
    output_root = Path(args.output_root).resolve()

    validation, validation_exit = validate(intas_root)
    if validation_exit != 0:
        print(json.dumps(validation, indent=2, sort_keys=True))
        return validation_exit

    scenario_dir = intas_root / "scenario"
    net_path = scenario_dir / "ingolstadt.net.xml"
    sumocfg_path = scenario_dir / "InTAS_buildings.sumocfg"
    route_paths = [(sumocfg_path.parent / route).resolve() for route in read_sumocfg_routes(sumocfg_path)]

    edges, junctions, metadata = parse_network(net_path)
    vclasses, routes = parse_route_files(route_paths, edges)
    candidates = evaluate_candidates(edges, junctions, routes, targets)
    accepted = [candidate for candidate in candidates if not candidate.rejection_reasons]
    selected = accepted[0] if accepted else None

    output_scenario = output_root / SCENARIO_NAME
    report_dir = output_scenario / "reports"
    report_dir.mkdir(parents=True, exist_ok=True)

    candidate_reports = [candidate_report(candidate, metadata, len(routes), edges) for candidate in candidates]
    materialization_status = "READY_FOR_MATERIALIZATION" if selected else "NO_CANDIDATE_SATISFIES_REQUIREMENTS"

    report: dict[str, Any] = {
        "scenarioName": SCENARIO_NAME,
        "source": {
            "name": "InTAS",
            "repository": "https://github.com/silaslobo/InTAS",
            "root": str(intas_root),
            "commit": git_value(intas_root, "rev-parse", "HEAD"),
            "tag": git_value(intas_root, "describe", "--tags", "--exact-match", "HEAD"),
            "license": detect_license(intas_root),
            "sourceFiles": [str(path.relative_to(intas_root)).replace("\\", "/") for path in [net_path, sumocfg_path, *route_paths]],
        },
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "targets": targets,
        "seeds": seeds,
        "candidateCount": len(candidates),
        "selectedCandidateId": selected.candidate_id if selected else None,
        "candidates": candidate_reports,
        "status": materialization_status,
        "warnings": [],
        "errors": [],
    }

    if not selected:
        report["errors"].append("No candidate satisfies all deterministic urban subscenario requirements.")
        write_json(report_dir / "intas_literature_materialization_report.json", report)
        write_markdown_report(report_dir / "intas_literature_materialization_report.md", report)
        print(json.dumps(report, indent=2, sort_keys=True))
        return 3

    (output_scenario / "application").mkdir(exist_ok=True)
    (output_scenario / "mapping").mkdir(exist_ok=True)
    (output_scenario / "sumo").mkdir(exist_ok=True)
    write_templates(output_scenario, selected, targets, args.duration_profile, metadata)

    density_names = ["low_density", "nominal", "high_density"] if args.density == "all" else [args.density]
    duration = int(targets["durationsSeconds"]["nominal"])
    route_outputs: dict[str, Any] = {}
    for density in density_names:
        target = int(targets["activeVehicleTargets"][density])
        seed = int(seeds[density_names.index(density) % len(seeds)])
        subset = select_route_subset(selected.routes, target, duration, seed)
        route_path = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}_{density}.rou.xml"
        write_route_file(route_path, subset, vclasses)
        counts = active_counts(subset, duration)
        route_outputs[density] = {
            "routeFile": str(route_path),
            "targetMeanActiveVehicles": target,
            "meanActiveVehicles": sum(counts.values()) / max(len(counts), 1),
            "maxActiveVehicles": max(counts.values()) if counts else 0,
            "vehicleCount": len(subset),
            "seed": seed,
        }

    concrete_route = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}_nominal.rou.xml"
    if not concrete_route.exists():
        concrete_route = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}_{density_names[0]}.rou.xml"

    net_output = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}.net.xml"
    edge_list = report_dir / "selected_edge_ids.txt"
    edge_list.write_text("\n".join(selected.edge_ids) + "\n", encoding="utf-8")

    scenario_convert = find_scenario_convert(args.scenario_convert)
    netconvert = shutil.which("netconvert")
    if args.dry_run:
        report["status"] = "DRY_RUN_COMPLETED"
        report["routeSubsets"] = route_outputs
    elif not netconvert:
        report["status"] = "PARTIAL_EXTERNAL_TOOL_REQUIRED"
        report["errors"].append("netconvert is required to extract the reduced SUMO network.")
    else:
        run_checked([netconvert, "--sumo-net-file", str(net_path), "--keep-edges.input-file", str(edge_list), "--output-file", str(net_output)], output_scenario)
        write_sumocfg(output_scenario / "sumo" / f"{SUBSCENARIO_NAME}.sumocfg", net_output.name, concrete_route.name, targets["sumoStepLengthSeconds"])
        report["routeSubsets"] = route_outputs
        report["reducedNetwork"] = str(net_output)
        if scenario_convert:
            db_path = output_scenario / "application" / f"{SUBSCENARIO_NAME}.db"
            run_checked([scenario_convert, "--sumo2db", "-i", str(net_output)], output_scenario)
            generated_default = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}.db"
            if generated_default.exists() and generated_default != db_path:
                shutil.move(str(generated_default), str(db_path))
            run_checked([scenario_convert, "--sumo2db", "-i", str(concrete_route), "-d", str(db_path)], output_scenario)
            report["database"] = str(db_path)
            report["status"] = "COMPLETED"
        else:
            report["status"] = "PARTIAL_EXTERNAL_TOOL_REQUIRED"
            report["warnings"].append(
                "scenario-convert not found. Run scenario-convert.sh --sumo2db after configuring MOSAIC extended tools."
            )

    write_metadata(output_scenario / "application" / "ma_ga_calibration_metadata.json", report)
    write_json(report_dir / "intas_literature_materialization_report.json", report)
    write_markdown_report(report_dir / "intas_literature_materialization_report.md", report)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["status"] in {"COMPLETED", "DRY_RUN_COMPLETED", "PARTIAL_EXTERNAL_TOOL_REQUIRED"} else 4


def write_sumocfg(path: Path, net_file: str, route_file: str, step_length: float) -> None:
    configuration = ET.Element("configuration")
    input_node = ET.SubElement(configuration, "input")
    ET.SubElement(input_node, "net-file", {"value": net_file})
    ET.SubElement(input_node, "route-files", {"value": route_file})
    time_node = ET.SubElement(configuration, "time")
    ET.SubElement(time_node, "step-length", {"value": str(step_length)})
    indent(configuration)
    ET.ElementTree(configuration).write(path, encoding="utf-8", xml_declaration=True)


def write_metadata(path: Path, report: dict[str, Any]) -> None:
    payload = {
        "scenarioName": SCENARIO_NAME,
        "materializationStatus": report["status"],
        "mobility": {
            "source": "InTAS",
            "classification": "MODELLED_DIRECTLY",
            "repository": "https://github.com/silaslobo/InTAS",
            "sourceCommitOrTag": report["source"].get("tag") or report["source"].get("commit"),
            "sourceFiles": report["source"]["sourceFiles"],
        },
        "wirelessAssumptions": [
            {"name": "ITS-G5 / IEEE 802.11p", "classification": "MODELLED_DIRECTLY"},
            {"name": "Carrier frequency 5.9 GHz", "classification": "DOCUMENTATION_ONLY"},
            {"name": "Channel bandwidth 10 MHz", "classification": "DOCUMENTATION_ONLY"},
            {"name": "Transmit power 23 dBm", "classification": "DOCUMENTATION_ONLY"},
            {"name": "CAM payload 300 byte", "classification": "DOCUMENTATION_ONLY"},
            {"name": "PRR >= 90%", "classification": "DOCUMENTATION_ONLY"},
            {"name": "Nominal direct V2V radius 250 m", "classification": "CALIBRATED_ABSTRACTION"},
        ],
        "controlledAssumptions": {
            "sumoStepLengthSeconds": {"value": 0.1, "classification": "CONTROLLED_ASSUMPTION"}
        },
    }
    write_json(path, payload)


def write_markdown_report(path: Path, report: dict[str, Any]) -> None:
    selected = report.get("selectedCandidateId") or "none"
    lines = [
        "# InTAS Literature Scenario Materialization Report",
        "",
        f"- status: `{report['status']}`",
        f"- scenario: `{SCENARIO_NAME}`",
        f"- source: {report['source']['repository']}",
        f"- selected candidate: `{selected}`",
        f"- candidates evaluated: {report['candidateCount']}",
        "",
        "## Notes",
        "",
        "Generated assets are derived from an external InTAS checkout and must be reviewed for redistribution before committing.",
        "",
    ]
    if report.get("warnings"):
        lines.append("## Warnings")
        lines.extend(f"- {warning}" for warning in report["warnings"])
        lines.append("")
    if report.get("errors"):
        lines.append("## Errors")
        lines.extend(f"- {error}" for error in report["errors"])
        lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
