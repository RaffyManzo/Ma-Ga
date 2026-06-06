#!/usr/bin/env python3
"""Materialize a deterministic InTAS-derived MOSAIC scenario scaffold.

The script reads an external InTAS checkout passed through ``--intas-root`` and
writes generated artifacts only to the explicit ``--output-root``. It preserves
SUMO ``vType`` and vehicle XML attributes used by selected vehicles; no InTAS
asset is vendored into this repository.
"""

from __future__ import annotations

import argparse
import copy
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
DEFAULT_CATALOG = TOOL_DIR / "config" / "literature_calibration_catalog.json"
SCENARIO_NAME = "MaGaLiteratureBasedUrbanStudy"
SUBSCENARIO_NAME = "intas_literature_urban"
SUPPORTED_PRESERVED_VEHICLE_CHILDREN = {"route", "routeDistribution", "stop", "param"}
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
class VTypeDefinition:
    type_id: str
    attributes: dict[str, str]
    children_xml: tuple[str, ...]


@dataclass(frozen=True)
class RouteVehicle:
    vehicle_id: str
    depart: float
    representative_edges: tuple[str, ...]
    all_route_edges: tuple[str, ...]
    vtype: str | None
    vclass: str
    estimated_duration: float
    attributes: dict[str, str]
    children_xml: tuple[str, ...]
    source_file: str
    preserved_child_tags: tuple[str, ...]
    unsupported_child_tags: tuple[str, ...]


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
        description="Build or dry-run a deterministic MOSAIC scenario from an external InTAS checkout."
    )
    parser.add_argument("--intas-root", required=True, help="External InTAS checkout root.")
    parser.add_argument("--output-root", required=True, help="Directory where generated scenario assets are written.")
    parser.add_argument("--scenario-convert", help="Path to scenario-convert.sh/.bat. Defaults to SCENARIO_CONVERT or PATH.")
    parser.add_argument("--targets", default=str(DEFAULT_TARGETS), help="Target JSON configuration.")
    parser.add_argument("--seeds", default=str(DEFAULT_SEEDS), help="Seed JSON configuration.")
    parser.add_argument("--catalog", default=str(DEFAULT_CATALOG), help="Literature calibration catalog JSON.")
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
        help="Duration written to scenario_config.json.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Analyze InTAS and write reports/route subsets without netconvert or scenario-convert.",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def strip_ns(tag: str) -> str:
    return tag.split("}", 1)[-1]


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


def parse_route_files(
    route_paths: list[Path],
    edge_lookup: dict[str, Edge],
    max_depart_seconds: int,
) -> tuple[dict[str, VTypeDefinition], list[RouteVehicle], list[str]]:
    vtypes: dict[str, VTypeDefinition] = {}
    vehicles: list[RouteVehicle] = []
    warnings: list[str] = []

    for route_path in route_paths:
        try:
            context = ET.iterparse(route_path, events=("end",))
            for _, element in context:
                tag = strip_ns(element.tag)
                if tag == "vType":
                    type_id = element.attrib.get("id")
                    if type_id:
                        definition = VTypeDefinition(
                            type_id=type_id,
                            attributes=dict(element.attrib),
                            children_xml=tuple(ET.tostring(child, encoding="unicode") for child in list(element)),
                        )
                        if type_id in vtypes and vtypes[type_id].attributes != definition.attributes:
                            warnings.append(f"vType {type_id} has conflicting definitions; preserving the first one.")
                        else:
                            vtypes.setdefault(type_id, definition)
                    element.clear()
                    continue
                if tag != "vehicle":
                    continue

                vehicle = build_route_vehicle(element, route_path, edge_lookup, vtypes, warnings)
                element.clear()
                if vehicle is None:
                    continue
                if vehicle.depart > max_depart_seconds:
                    continue
                if vehicle.vclass in {"pedestrian", "bicycle", "bus"}:
                    continue
                vehicles.append(vehicle)
        except ET.ParseError as exc:
            warnings.append(f"Failed to parse route file {route_path}: {exc}")

    return vtypes, vehicles, warnings


def build_route_vehicle(
    element: ET.Element,
    route_path: Path,
    edge_lookup: dict[str, Edge],
    vtypes: dict[str, VTypeDefinition],
    warnings: list[str],
) -> RouteVehicle | None:
    vehicle_id = element.attrib.get("id")
    if not vehicle_id:
        return None
    depart = parse_depart(element.attrib.get("depart", "0"))
    vtype = element.attrib.get("type")
    vclass = vtypes.get(vtype or "", VTypeDefinition("", {"vClass": "passenger"}, ())).attributes.get("vClass", "passenger")

    child_tags = tuple(strip_ns(child.tag) for child in list(element))
    unsupported = tuple(sorted({tag for tag in child_tags if tag not in SUPPORTED_PRESERVED_VEHICLE_CHILDREN}))
    if unsupported:
        warnings.append(
            f"Vehicle {vehicle_id} contains nested tags {unsupported}; XML is preserved in the route subset."
        )

    representative = choose_representative_route(element)
    if not representative:
        return None
    all_edges = collect_all_route_edges(element)
    duration = estimate_duration(representative, edge_lookup)
    return RouteVehicle(
        vehicle_id=vehicle_id,
        depart=depart,
        representative_edges=tuple(representative),
        all_route_edges=tuple(sorted(set(all_edges))),
        vtype=vtype,
        vclass=vclass,
        estimated_duration=max(duration, 1.0),
        attributes=dict(element.attrib),
        children_xml=tuple(ET.tostring(child, encoding="unicode") for child in list(element)),
        source_file=route_path.name,
        preserved_child_tags=child_tags,
        unsupported_child_tags=unsupported,
    )


def parse_depart(value: str) -> float:
    if value in {"triggered", "containerTriggered"}:
        return 0.0
    return float(value)


def route_edges_from_route_node(route: ET.Element) -> list[str]:
    return [edge for edge in route.attrib.get("edges", "").split() if edge]


def choose_representative_route(vehicle: ET.Element) -> list[str]:
    direct_routes = [child for child in list(vehicle) if strip_ns(child.tag) == "route"]
    if direct_routes:
        return route_edges_from_route_node(direct_routes[0])
    distributions = [child for child in list(vehicle) if strip_ns(child.tag) == "routeDistribution"]
    if not distributions:
        return []
    routes = [child for child in list(distributions[0]) if strip_ns(child.tag) == "route"]
    if not routes:
        return []
    last = distributions[0].attrib.get("last")
    if last is not None:
        try:
            index = int(last)
            if 0 <= index < len(routes):
                return route_edges_from_route_node(routes[index])
        except ValueError:
            pass
    chosen = max(routes, key=lambda route: float(route.attrib.get("probability", "0")))
    return route_edges_from_route_node(chosen)


def collect_all_route_edges(vehicle: ET.Element) -> list[str]:
    edges: list[str] = []
    for child in vehicle.iter():
        if strip_ns(child.tag) == "route":
            edges.extend(route_edges_from_route_node(child))
    return edges


def estimate_duration(edge_ids: Iterable[str], edge_lookup: dict[str, Edge]) -> float:
    return sum(edge_lookup[edge].length / edge_lookup[edge].speed for edge in edge_ids if edge in edge_lookup)


def compute_bounds(edges: Iterable[Edge]) -> tuple[float, float, float, float]:
    points = [point for edge in edges for point in edge.shape]
    return min(p.x for p in points), min(p.y for p in points), max(p.x for p in points), max(p.y for p in points)


def in_bounds(point: Point, bounds: tuple[float, float, float, float]) -> bool:
    min_x, min_y, max_x, max_y = bounds
    return min_x <= point.x <= max_x and min_y <= point.y <= max_y


def edge_inside(edge: Edge, bounds: tuple[float, float, float, float]) -> bool:
    return any(in_bounds(point, bounds) for point in edge.shape)


def route_intersects(route: RouteVehicle, selected_edges: set[str]) -> bool:
    return any(edge in selected_edges for edge in route.representative_edges)


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
    for edge_id in route.representative_edges:
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


def candidate_windows(edges: dict[str, Edge], targets: dict[str, Any]) -> list[tuple[float, float, float, float]]:
    min_x, min_y, max_x, max_y = compute_bounds(edges.values())
    grid_cfg = targets.get("materialization", {}).get("candidateGrid", {})
    size = float(grid_cfg.get("windowSizeMeters", 900))
    stride = float(grid_cfg.get("strideMeters", 300))
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
    return sorted(windows, key=lambda b: hashlib.sha256(repr(b).encode("ascii")).hexdigest())


def evaluate_candidates(
    edges: dict[str, Edge],
    junctions: dict[str, Junction],
    routes: list[RouteVehicle],
    targets: dict[str, Any],
    windows: list[tuple[float, float, float, float]],
) -> list[Candidate]:
    requirements = targets["subscenarioSelection"]["requirements"]
    radius = float(requirements["nominalRsuRadiusMeters"])
    duration = int(targets["durationsSeconds"]["nominal"])
    target_active = int(targets["activeVehicleTargets"]["nominal"])
    candidates: list[Candidate] = []
    for index, bounds in enumerate(windows):
        selected_edges = [edge for edge in edges.values() if edge_inside(edge, bounds)]
        selected_edge_ids = {edge.edge_id for edge in selected_edges}
        candidate_junctions = [
            junction
            for junction in junctions.values()
            if in_bounds(Point(junction.x, junction.y), bounds)
        ]
        selected_routes = [route for route in routes if route_intersects(route, selected_edge_ids)]
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
            rejection.append("NO_PASSENGER_ROUTES_INTERSECT_CANDIDATE")
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
                candidate_id=f"candidate_{index:04d}",
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


def progressive_evaluate(
    edges: dict[str, Edge],
    junctions: dict[str, Junction],
    routes: list[RouteVehicle],
    targets: dict[str, Any],
) -> tuple[list[Candidate], Candidate | None, list[dict[str, int]]]:
    all_windows = candidate_windows(edges, targets)
    initial = int(targets.get("materialization", {}).get("candidateGrid", {}).get("maxCandidates", 64))
    limits: list[int] = []
    limit = max(1, min(initial, len(all_windows)))
    while limit < len(all_windows):
        limits.append(limit)
        limit = min(limit * 2, len(all_windows))
    limits.append(len(all_windows))

    expansions: list[dict[str, int]] = []
    best_candidates: list[Candidate] = []
    for limit in dict.fromkeys(limits):
        candidates = evaluate_candidates(edges, junctions, routes, targets, all_windows[:limit])
        accepted = [candidate for candidate in candidates if not candidate.rejection_reasons]
        expansions.append({"windowsEvaluated": limit, "acceptedCandidates": len(accepted)})
        best_candidates = candidates
        if accepted:
            return candidates, accepted[0], expansions
    return best_candidates, None, expansions


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


def write_route_file(path: Path, vehicles: tuple[RouteVehicle, ...], vtypes: dict[str, VTypeDefinition]) -> dict[str, Any]:
    routes = ET.Element("routes")
    emitted_types: set[str] = set()
    missing_types: list[str] = []
    for vehicle in vehicles:
        if vehicle.vtype and vehicle.vtype not in emitted_types:
            definition = vtypes.get(vehicle.vtype)
            if definition:
                node = ET.SubElement(routes, "vType", dict(definition.attributes))
                for child_xml in definition.children_xml:
                    node.append(ET.fromstring(child_xml))
            else:
                missing_types.append(vehicle.vtype)
            emitted_types.add(vehicle.vtype)
    for vehicle in vehicles:
        node = ET.SubElement(routes, "vehicle", dict(vehicle.attributes))
        for child_xml in vehicle.children_xml:
            node.append(ET.fromstring(child_xml))
    indent(routes)
    ET.ElementTree(routes).write(path, encoding="utf-8", xml_declaration=True)
    return {
        "vTypeIdsWritten": sorted(emitted_types),
        "missingVTypeDefinitions": sorted(set(missing_types)),
        "vehicleCount": len(vehicles),
        "preservedVehicleAttributeKeys": sorted({key for vehicle in vehicles for key in vehicle.attributes}),
        "preservedNestedTags": sorted({tag for vehicle in vehicles for tag in vehicle.preserved_child_tags}),
        "unsupportedNestedTagsPreserved": sorted({tag for vehicle in vehicles for tag in vehicle.unsupported_child_tags}),
    }


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


def find_scenario_convert(arg: str | None, mosaic_root: Path | None = None) -> dict[str, Any]:
    candidates = [
        ("argument", arg),
        ("SCENARIO_CONVERT", os.environ.get("SCENARIO_CONVERT")),
        ("PATH:scenario-convert.sh", shutil.which("scenario-convert.sh")),
        ("PATH:scenario-convert.bat", shutil.which("scenario-convert.bat")),
        ("PATH:scenario-convert", shutil.which("scenario-convert")),
    ]
    if mosaic_root and mosaic_root.exists():
        for pattern in ("scenario-convert.sh", "scenario-convert.bat", "scenario-convert"):
            for match in mosaic_root.rglob(pattern):
                candidates.append((f"mosaic-root:{pattern}", str(match)))
    for source, candidate in candidates:
        if candidate:
            return {"available": True, "source": source, "path": candidate}
    return {
        "available": False,
        "source": None,
        "path": None,
        "requiredAction": "Install/configure MOSAIC Extended Scenario-Convert and pass --scenario-convert or SCENARIO_CONVERT.",
    }


def run_checked(command: list[str], cwd: Path) -> None:
    subprocess.run(command, cwd=str(cwd), check=True)


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def load_sumolib_net(net_path: Path):
    sumo_home = os.environ.get("SUMO_HOME")
    if sumo_home:
        tools_path = Path(sumo_home) / "tools"
        if tools_path.exists():
            sys.path.insert(0, str(tools_path))
    try:
        import sumolib  # type: ignore

        return sumolib.net.readNet(str(net_path)), "SUMOLIB_CONVERT_XY2LONLAT"
    except Exception as exc:  # noqa: BLE001 - report exact fallback reason.
        return None, f"SUMOLIB_UNAVAILABLE: {exc}"


def convert_xy_to_lon_lat(net: Any, point: Point) -> dict[str, float]:
    lon, lat = net.convertXY2LonLat(point.x, point.y)
    return {"longitude": float(lon), "latitude": float(lat)}


def plausible_ingolstadt_coordinate(position: dict[str, float]) -> bool:
    lat = float(position["latitude"])
    lon = float(position["longitude"])
    return 48.55 <= lat <= 48.95 and 11.0 <= lon <= 11.8


def parse_net_offset(metadata: dict[str, Any]) -> tuple[float, float] | None:
    value = metadata.get("location", {}).get("netOffset")
    if not value:
        return None
    parts = [float(part) for part in value.split(",")]
    if len(parts) != 2:
        return None
    return parts[0], parts[1]


def sumolib_geographic_boundary(bounds: tuple[float, float, float, float], net_path: Path) -> dict[str, Any]:
    net, method = load_sumolib_net(net_path)
    if net is None:
        return {"valid": False, "method": method, "error": method}
    min_x, min_y, max_x, max_y = bounds
    try:
        nw = convert_xy_to_lon_lat(net, Point(min_x, max_y))
        se = convert_xy_to_lon_lat(net, Point(max_x, min_y))
    except Exception as exc:  # noqa: BLE001 - keep exact conversion failure in reports.
        return {"valid": False, "method": method, "error": str(exc)}
    return {
        "valid": all(plausible_ingolstadt_coordinate(position) for position in (nw, se)),
        "method": method,
        "nw": {"lon": nw["longitude"], "lat": nw["latitude"]},
        "se": {"lon": se["longitude"], "lat": se["latitude"]},
    }


def derive_projection(
    selected: Candidate,
    net_path: Path,
    metadata: dict[str, Any],
) -> dict[str, Any]:
    min_x, min_y, max_x, max_y = selected.bounds
    center = Point((min_x + max_x) / 2.0, (min_y + max_y) / 2.0)
    net, method = load_sumolib_net(net_path)
    offset = parse_net_offset(metadata)
    if net is not None and offset is not None:
        try:
            center_geo = convert_xy_to_lon_lat(net, center)
            lon = center_geo["longitude"]
            lat = center_geo["latitude"]
            return {
                "method": method,
                "centerCoordinates": {"longitude": lon, "latitude": lat},
                "cartesianOffset": {"x": offset[0], "y": offset[1]},
                "fallback": None,
                "plausibleIngolstadtCoordinate": plausible_ingolstadt_coordinate(center_geo),
                "valid": all(math.isfinite(value) for value in [lon, lat, offset[0], offset[1]])
                and plausible_ingolstadt_coordinate(center_geo),
            }
        except Exception as exc:  # noqa: BLE001 - pyproj may be absent on local SUMO installs.
            method = f"{method}_FAILED: {exc}"
    fallback = geographic_boundary(selected.bounds, metadata)
    offset = offset or (0.0, 0.0)
    if fallback.get("method") == "LINEAR_INTERPOLATION_FROM_SUMO_LOCATION_BOUNDARIES":
        lon = (fallback["minLongitude"] + fallback["maxLongitude"]) / 2.0
        lat = (fallback["minLatitude"] + fallback["maxLatitude"]) / 2.0
        return {
            "method": "FALLBACK_LINEAR_BOUNDARY_INTERPOLATION",
            "centerCoordinates": {"longitude": lon, "latitude": lat},
            "cartesianOffset": {"x": offset[0], "y": offset[1]},
            "fallback": method,
            "plausibleIngolstadtCoordinate": plausible_ingolstadt_coordinate({"longitude": lon, "latitude": lat}),
            "valid": all(math.isfinite(value) for value in [lon, lat, offset[0], offset[1]]),
        }
    return {
        "method": "UNAVAILABLE",
        "centerCoordinates": None,
        "cartesianOffset": None,
        "fallback": method,
        "valid": False,
    }


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


def point_position_for_mapping(point: Point, net_path: Path, metadata: dict[str, Any]) -> dict[str, Any]:
    net, method = load_sumolib_net(net_path)
    if net is not None:
        try:
            converted = convert_xy_to_lon_lat(net, point)
            lon = converted["longitude"]
            lat = converted["latitude"]
            return {"longitude": lon, "latitude": lat, "conversionMethod": method}
        except Exception as exc:  # noqa: BLE001 - report fallback reason.
            method = f"{method}_FAILED: {exc}"
    fallback = geographic_boundary((point.x, point.y, point.x, point.y), metadata)
    if fallback.get("method") == "LINEAR_INTERPOLATION_FROM_SUMO_LOCATION_BOUNDARIES":
        return {
            "longitude": fallback["minLongitude"],
            "latitude": fallback["minLatitude"],
            "conversionMethod": f"FALLBACK_LINEAR_BOUNDARY_INTERPOLATION_AFTER_{method}",
        }
    return {
        "longitude": None,
        "latitude": None,
        "cartesianX": point.x,
        "cartesianY": point.y,
        "conversionMethod": "UNAVAILABLE",
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
        "candidateRsuPositions": [{"x": point.x, "y": point.y} for point in candidate.rsu_positions],
        "rsuPairDistanceMeters": candidate.rsu_pair_distance,
        "rsuCoverageOverlapEstimated": candidate.overlap_estimated,
        "gatewaySwitchPotential": candidate.gateway_switch_potential,
        "selectionScore": candidate.score,
        "rejectionReasons": list(candidate.rejection_reasons),
    }


def write_scenario_files(
    scenario_dir: Path,
    candidate: Candidate,
    targets: dict[str, Any],
    duration_profile: str,
    projection: dict[str, Any],
) -> None:
    if not projection.get("valid"):
        raise ValueError("Cannot write scenario_config.json without a valid numeric projection.")
    duration = targets["durationsSeconds"][duration_profile]
    scenario = {
        "simulation": {
            "id": SCENARIO_NAME,
            "duration": f"{duration}s",
            "randomSeed": 104729,
            "projection": {
                "centerCoordinates": {
                    "latitude": projection["centerCoordinates"]["latitude"],
                    "longitude": projection["centerCoordinates"]["longitude"],
                },
                "cartesianOffset": {
                    "x": projection["cartesianOffset"]["x"],
                    "y": projection["cartesianOffset"]["y"],
                },
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
            "cell": True,
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
    write_json(scenario_dir / "scenario_config.json", scenario)
    write_json(scenario_dir / "sumo" / "sumo_config.json", sumo_config)


def write_mapping_config(
    scenario_dir: Path,
    candidate: Candidate,
    used_vtype_ids: list[str],
    net_path: Path,
    metadata: dict[str, Any],
) -> dict[str, Any]:
    rsus = []
    for index, point in enumerate(candidate.rsu_positions):
        rsus.append(
            {
                "name": f"rsu_{index}",
                "group": "MaGaGateway",
                "position": point_position_for_mapping(point, net_path, metadata),
                "applications": [],
            }
        )
    mapping = {
        "config": {"fixedOrder": True},
        "prototypes": [
            {
                "name": vtype_id,
                "applications": VEHICLE_APPLICATIONS,
                "weight": 1.0,
            }
            for vtype_id in used_vtype_ids
        ],
        "rsus": rsus,
        "servers": [
            {
                "name": "server_0",
                "group": "server_0",
                "applications": SERVER_APPLICATIONS,
            }
        ],
        "vehicles": [],
    }
    write_json(scenario_dir / "mapping" / "mapping_config.json", mapping)
    return mapping


def write_sns_config(scenario_dir: Path, catalog: dict[str, Any]) -> dict[str, Any]:
    nominal = catalog["v2vProfiles"]["nominal"]
    payload = {
        "maximumTtl": 10,
        "singlehopRadius": nominal["singlehopRadiusMeters"],
        "adhocTransmissionModel": {"type": "SophisticatedAdhocTransmissionModel"},
        "singlehopDelay": dict(nominal["snsDelay"]),
        "singleHopTransmission": {
            "lossProbability": nominal["lossProbability"],
            "maxRetries": 0,
        },
    }
    write_json(scenario_dir / "sns" / "sns_config.json", payload)
    return payload


def write_cell_configs(scenario_dir: Path, catalog: dict[str, Any], region_boundary: dict[str, Any]) -> dict[str, Any]:
    profile = catalog["cellProfiles"]["CELL_5G_AVEIRO_P50"]
    delay_ms = profile["symmetricOneWayDelaySeconds"] * 1000.0
    capacity = profile["capacityBitsPerSecond"]
    cell_config = {
        "networkConfigurationFile": "network.json",
        "regionConfigurationFile": "regions.json",
        "bandwidthMeasurementInterval": 1,
        "bandwidthMeasurementCompression": False,
        "bandwidthMeasurements": [
            {"fromRegion": "*", "toRegion": "*", "transmissionMode": "UplinkUnicast"},
            {"fromRegion": "*", "toRegion": "*", "transmissionMode": "DownlinkUnicast"},
            {"fromRegion": "*", "toRegion": "*", "transmissionMode": "DownlinkMulticast"},
        ],
        "headerLengths": {
            "udpHeader": "8 Bytes",
            "tcpHeader": "20 Bytes",
            "ipHeader": "20 Bytes",
            "cellularHeader": "18 Bytes",
            "ethernetHeader": "18 Bytes",
        },
    }
    network = {
        "defaultDownlinkCapacity": "100 Gbps",
        "defaultUplinkCapacity": "100 Gbps",
        "globalNetwork": {
            "uplink": {
                "delay": {"type": "ConstantDelay", "delay": f"{delay_ms:g} ms"},
                "transmission": {"lossProbability": 0.0, "maxRetries": 0},
                "capacity": capacity,
            },
            "downlink": {
                "unicast": {
                    "delay": {"type": "ConstantDelay", "delay": f"{delay_ms:g} ms"},
                    "transmission": {"lossProbability": 0.0, "maxRetries": 0},
                },
                "multicast": {
                    "delay": {"type": "ConstantDelay", "delay": f"{delay_ms:g} ms"},
                    "transmission": {"lossProbability": 0.0, "maxRetries": 0},
                    "usableCapacity": 0.6,
                },
                "capacity": capacity,
            },
        },
        "servers": [
            {
                "id": "server_0",
                "uplink": {
                    "delay": {"type": "ConstantDelay", "delay": "50 ms"},
                    "transmission": {"lossProbability": 0.0, "maxRetries": 0},
                },
                "downlink": {
                    "unicast": {
                        "delay": {"type": "ConstantDelay", "delay": "50 ms"},
                        "transmission": {"lossProbability": 0.0, "maxRetries": 0},
                    }
                },
            }
        ],
    }
    regions = {
        "regions": [
            {
                "id": "region_cell_5g_aveiro_p50",
                "area": {
                    "nw": region_boundary["nw"],
                    "se": region_boundary["se"],
                },
                "uplink": {
                    "delay": {"type": "ConstantDelay", "delay": f"{delay_ms:g} ms"},
                    "transmission": {"lossProbability": 0.0, "maxRetries": 0},
                    "capacity": capacity,
                },
                "downlink": {
                    "unicast": {
                        "delay": {"type": "ConstantDelay", "delay": f"{delay_ms:g} ms"},
                        "transmission": {"lossProbability": 0.0, "maxRetries": 0},
                    },
                    "multicast": {
                        "delay": {"type": "ConstantDelay", "delay": f"{delay_ms:g} ms"},
                        "transmission": {"lossProbability": 0.0, "maxRetries": 0},
                        "usableCapacity": 0.6,
                    },
                    "capacity": capacity,
                },
            }
        ]
    }
    write_json(scenario_dir / "cell" / "cell_config.json", cell_config)
    write_json(scenario_dir / "cell" / "network.json", network)
    write_json(scenario_dir / "cell" / "regions.json", regions)
    return {"cell_config": cell_config, "network": network, "regions": regions}


def write_application_config(scenario_dir: Path) -> dict[str, Any]:
    payload = {
        "messageCacheTime": "30s",
        "encodePayloads": True,
        "eventSchedulerThreads": 1,
        "navigationConfiguration": {
            "type": "database",
            "databaseFile": f"{SUBSCENARIO_NAME}.db",
        },
        "perceptionConfiguration": {
            "vehicleIndex": {"enabled": True},
            "trafficLightIndex": {"enabled": True},
            "wallIndex": {"enabled": False},
        },
    }
    write_json(scenario_dir / "application" / "application_config.json", payload)
    return payload


def write_live_state_config(
    scenario_dir: Path,
    candidate: Candidate,
    catalog: dict[str, Any],
    mapping: dict[str, Any],
) -> dict[str, Any]:
    v2v = catalog["v2vProfiles"]["nominal"]
    cell = catalog["cellProfiles"]["CELL_5G_AVEIRO_P50"]
    compute = catalog["computeProfiles"]
    infra = catalog["infrastructure"]
    mapping_positions = {entry["name"]: entry["position"] for entry in mapping["rsus"]}
    gateways = []
    edge_nodes = []
    for index, point in enumerate(candidate.rsu_positions):
        runtime_id = f"rsu_{index}"
        gateways.append(
            {
                "runtimeId": runtime_id,
                "gatewayId": runtime_id,
                "gatewayType": "RSU",
                "projectedX": point.x,
                "projectedY": point.y,
                "longitude": mapping_positions[runtime_id]["longitude"],
                "latitude": mapping_positions[runtime_id]["latitude"],
                "coverageRadiusMeters": infra["nominalRsuCoverageRadiusMeters"],
                "cellRegionId": "region_cell_5g_aveiro_p50",
                "bandwidthPoolId": f"pool_rsu_{index}",
            }
        )
        edge_nodes.append(
            {
                "executionNodeId": f"edge_rsu_{index}",
                "gatewayIds": [runtime_id],
                "availableCpuCyclesPerSecond": compute["edgeCpuCyclesPerSecond"],
                "basePropagationDelaySeconds": infra["edgeBasePropagationDelaySeconds"],
            }
        )
    payload = {
        "tickIntervalMs": 1000,
        "singlehopRadiusMeters": v2v["singlehopRadiusMeters"],
        "localCpuCyclesPerSecond": compute["localVehicleCpuCyclesPerSecond"],
        "localCpuSource": "LITERATURE_BASED_RANGE_CHOICE",
        "remoteVehicleCpuCyclesPerSecond": compute["remoteVehicleCpuCyclesPerSecondTarget"],
        "remoteVehicleCpuSource": "LITERATURE_BASED_RANGE_CHOICE",
        "v2vNominalBandwidthBitsPerSecond": v2v["effectivePoolCapacityBitsPerSecond"],
        "v2vBandwidthSource": "LITERATURE_BASED_CALIBRATED_ABSTRACTION",
        "v2vPropagationDelaySeconds": v2v["maGaFixedDelaySeconds"],
        "configuredCellProfile": {
            "profileId": "CELL_5G_AVEIRO_P50",
            "technology": cell["technology"],
            "source": catalog["runtimeCompatibility"]["configuredCellProfileSource"],
            "classification": cell["classification"],
            "capacityBitsPerSecond": cell["capacityBitsPerSecond"],
            "measuredRttSeconds": cell["measuredRttSeconds"],
            "symmetricOneWayDelaySeconds": cell["symmetricOneWayDelaySeconds"],
        },
        "cellDiagnosticAccounting": {
            "bucketDurationMs": 1000,
            "availableFromPolicy": "SAFE_AFTER_TIMESTAMP",
            "bandwidthSource": catalog["runtimeCompatibility"]["cellDiagnosticAccountingSource"],
            "configuredCellProfileSource": catalog["runtimeCompatibility"]["configuredCellProfileSource"],
            "relationship": "CONFIGURED_CELL_PROFILE_AND_RUNTIME_ACCOUNTING_ARE_DISTINCT_CONCEPTS",
            "destinationId": "server_0",
            "requestPayloadBytes": 1000,
            "responsePayloadBytes": 500,
            "intervalMs": 1000,
            "initialDelayMs": 1000,
            "maxUplinkBitrate": "49.2 Mbps",
            "maxDownlinkBitrate": "49.2 Mbps",
            "gatewayPools": [
                {"poolId": "pool_rsu_0", "nominalCapacityBitsPerSecond": cell["capacityBitsPerSecond"]},
                {"poolId": "pool_rsu_1", "nominalCapacityBitsPerSecond": cell["capacityBitsPerSecond"]},
            ],
        },
        "taskProfiles": [],
        "workloadGeneration": catalog["workloadGeneration"],
        "staticInfrastructure": {
            "gateways": gateways,
            "edgeNodes": edge_nodes,
            "cloudNodes": [
                {
                    "executionNodeId": "cloud_regional",
                    "mosaicServerRuntimeId": "server_0",
                    "availableCpuCyclesPerSecond": compute["cloudCpuCyclesPerSecond"],
                    "serverBaseDelaySeconds": infra["cloudBackhaulExtraDelaySeconds"],
                }
            ],
        },
    }
    write_json(scenario_dir / "application" / "ma_ga_live_state_config.json", payload)
    return payload


def write_live_runtime_config(scenario_dir: Path) -> dict[str, Any]:
    payload = {
        "scenarioName": SCENARIO_NAME,
        "coordinatorTickIntervalMs": 100,
        "initialOptimizationDelayMs": 1000,
        "gaPollingIntervalMs": 50,
        "singleInFlightGaOnly": True,
        "discardLateResult": True,
        "keepLastAppliedStrategyWhileRunning": True,
        "freshReoptimizationAfterTimeout": True,
        "runtimeTraceEnabled": True,
        "diagnosticArtificialGaDelayMs": 0,
        "temporalInitialWindowSeconds": 1.0,
        "configuredGaRuntimeEstimateSeconds": 0.01,
        "configuredMaxWindowSeconds": 0.2,
        "deltaTMaxComparisonEpsilonSeconds": 1.0e-9,
        "publishedSnapshotCopyLimit": 32,
        "nativeLiveDetailedReportingEnabled": True,
        "nativeLiveDetailedReportPrintToConsole": False,
    }
    write_json(scenario_dir / "application" / "ma_ga_live_runtime_config.json", payload)
    return payload


def write_literature_runtime_configs(
    scenario_dir: Path,
    candidate: Candidate,
    used_vtype_ids: list[str],
    net_path: Path,
    metadata: dict[str, Any],
    catalog: dict[str, Any],
    region_boundary: dict[str, Any],
) -> dict[str, Any]:
    mapping = write_mapping_config(scenario_dir, candidate, used_vtype_ids, net_path, metadata)
    return {
        "mapping": mapping,
        "sns": write_sns_config(scenario_dir, catalog),
        "cell": write_cell_configs(scenario_dir, catalog, region_boundary),
        "application": write_application_config(scenario_dir),
        "liveState": write_live_state_config(scenario_dir, candidate, catalog, mapping),
        "liveRuntime": write_live_runtime_config(scenario_dir),
    }


def write_sumocfg(path: Path, net_file: str, route_file: str, step_length: float) -> None:
    configuration = ET.Element("configuration")
    input_node = ET.SubElement(configuration, "input")
    ET.SubElement(input_node, "net-file", {"value": net_file})
    ET.SubElement(input_node, "route-files", {"value": route_file})
    time_node = ET.SubElement(configuration, "time")
    ET.SubElement(time_node, "step-length", {"value": str(step_length)})
    indent(configuration)
    ET.ElementTree(configuration).write(path, encoding="utf-8", xml_declaration=True)


def validate_generated_outputs(
    scenario_dir: Path,
    report: dict[str, Any],
    selected: Candidate,
    route_outputs: dict[str, Any],
    scenario_convert: dict[str, Any],
) -> dict[str, Any]:
    errors: list[str] = []
    warnings: list[str] = []
    scenario = load_json(scenario_dir / "scenario_config.json")
    projection = scenario["simulation"]["projection"]
    values = [
        projection["centerCoordinates"]["latitude"],
        projection["centerCoordinates"]["longitude"],
        projection["cartesianOffset"]["x"],
        projection["cartesianOffset"]["y"],
    ]
    if not all(isinstance(value, (int, float)) and math.isfinite(float(value)) for value in values):
        errors.append("scenario_config.json projection contains non-numeric or empty values.")
    projection_report = report.get("projection", {})
    if projection_report.get("method") != "SUMOLIB_CONVERT_XY2LONLAT":
        errors.append("Concrete literature configuration requires SUMOLIB_CONVERT_XY2LONLAT projection.")
    if projection_report.get("fallback") is not None:
        errors.append("Concrete literature configuration must not use projection fallback.")
    if not projection_report.get("plausibleIngolstadtCoordinate"):
        errors.append("Projection center is not plausible for Ingolstadt.")

    for density, output in route_outputs.items():
        route_path = Path(output["routeFile"])
        if not route_path.exists():
            errors.append(f"Route subset missing for {density}: {route_path}")
            continue
        try:
            tree = ET.parse(route_path)
            root = tree.getroot()
            used_types = {node.attrib.get("type") for node in root.iter() if strip_ns(node.tag) == "vehicle" and node.attrib.get("type")}
            written_types = {node.attrib.get("id") for node in root.iter() if strip_ns(node.tag) == "vType"}
            if used_types - written_types:
                errors.append(f"Route subset {density} misses vType definitions: {sorted(used_types - written_types)}")
        except ET.ParseError as exc:
            errors.append(f"Route subset XML invalid for {density}: {exc}")
        target = float(output["targetMeanActiveVehicles"])
        mean = float(output["meanActiveVehicles"])
        output["targetError"] = abs(mean - target)

    if not selected.rsu_positions:
        errors.append("Selected candidate has no candidate RSU positions.")

    required_generated = [
        "mapping/mapping_config.json",
        "sns/sns_config.json",
        "cell/cell_config.json",
        "cell/network.json",
        "cell/regions.json",
        "application/application_config.json",
        "application/ma_ga_live_state_config.json",
        "application/ma_ga_live_runtime_config.json",
    ]
    for relative in required_generated:
        if not (scenario_dir / relative).exists():
            errors.append(f"Required generated configuration missing: {relative}")

    sumocfg_path = scenario_dir / "sumo" / f"{SUBSCENARIO_NAME}.sumocfg"
    if sumocfg_path.exists():
        refs = sumocfg_references(sumocfg_path)
        for ref in refs:
            ref_path = (sumocfg_path.parent / ref).resolve()
            if not ref_path.exists():
                errors.append(f"sumocfg reference does not exist: {ref}")
    else:
        warnings.append("No sumocfg produced in this mode.")

    db_files = list(scenario_dir.rglob("*.db"))
    if db_files and not scenario_convert.get("available"):
        errors.append("Database file exists although scenario-convert is unavailable.")
    jar_files = list(scenario_dir.rglob("*.jar"))
    if jar_files:
        errors.append("Generated literature scenario must not contain JAR files.")

    return {"errors": errors, "warnings": warnings}


def sumocfg_references(sumocfg_path: Path) -> list[str]:
    tree = ET.parse(sumocfg_path)
    refs: list[str] = []
    for node in tree.getroot().iter():
        tag = strip_ns(node.tag)
        if tag in {"net-file", "route-files", "additional-files"}:
            refs.extend([item.strip() for item in node.attrib.get("value", "").split(",") if item.strip()])
    return refs


def write_metadata(path: Path, report: dict[str, Any], catalog: dict[str, Any]) -> None:
    payload = {
        "scenarioName": SCENARIO_NAME,
        "materializationStatus": report["status"],
        "selectedIntasCandidate": report.get("selectedCandidateId"),
        "sourceCommit": report["source"].get("commit"),
        "projection": {
            "method": report.get("projection", {}).get("method"),
            "fallback": report.get("projection", {}).get("fallback"),
            "classification": "MODELLED_DIRECTLY",
        },
        "mobility": {
            "source": "InTAS",
            "classification": "MODELLED_DIRECTLY",
            "repository": "https://github.com/silaslobo/InTAS",
            "sourceCommitOrTag": report["source"].get("tag") or report["source"].get("commit"),
            "sourceFiles": report["source"]["sourceFiles"],
        },
        "wirelessAssumptions": [
            {"name": "ITS-G5 / IEEE 802.11p", "classification": "CALIBRATED_ABSTRACTION"},
            {"name": "Carrier frequency 5.9 GHz", "classification": "DOCUMENTATION_ONLY"},
            {"name": "Physical channel bandwidth 10 MHz", "classification": "DOCUMENTATION_ONLY"},
            {"name": "Transmit power 23 dBm", "classification": "DOCUMENTATION_ONLY"},
            {"name": "CAM payload 300 byte", "classification": "DOCUMENTATION_ONLY"},
            {"name": "PRR >= 90%", "classification": "DOCUMENTATION_ONLY"},
            {"name": "Nominal direct V2V radius 250 m", "classification": "CALIBRATED_ABSTRACTION"},
            {"name": "SNS profile", "classification": "CALIBRATED_ABSTRACTION"},
        ],
        "cellProfiles": {
            "configuredNominalProfile": "CELL_5G_AVEIRO_P50",
            "configuredProfileSource": catalog["runtimeCompatibility"]["configuredCellProfileSource"],
            "runtimeAccountingSource": catalog["runtimeCompatibility"]["cellDiagnosticAccountingSource"],
            "relationship": "configured Cell profile and diagnostic runtime accounting are distinct concepts",
            "classification": "CALIBRATED_ABSTRACTION",
            "separateProfiles": ["CELL_5G_AVEIRO_DEGRADED", "CELL_LTE_AVEIRO_STRESS"],
            "scenarioConvert": "MISSING_DATABASE_NOT_CREATED" if not report.get("scenarioConvert", {}).get("available") else "AVAILABLE",
        },
        "computeAssumptions": {
            "localVehicleCpuCyclesPerSecond": {
                "value": catalog["computeProfiles"]["localVehicleCpuCyclesPerSecond"],
                "classification": catalog["computeProfiles"]["localCpuClassification"],
            },
            "remoteVehicleCpuCyclesPerSecondTarget": {
                "value": catalog["computeProfiles"]["remoteVehicleCpuCyclesPerSecondTarget"],
                "classification": catalog["computeProfiles"]["remoteVehicleCpuClassification"],
                "status": "emitted into live-state executable config from 14C.3",
            },
            "edgeCpuCyclesPerSecond": {
                "value": catalog["computeProfiles"]["edgeCpuCyclesPerSecond"],
                "classification": catalog["computeProfiles"]["edgeCpuClassification"],
            },
            "cloudCpuCyclesPerSecond": {
                "value": catalog["computeProfiles"]["cloudCpuCyclesPerSecond"],
                "classification": catalog["computeProfiles"]["cloudCpuClassification"],
            },
            "cloudBackhaulExtraDelaySeconds": {
                "value": catalog["infrastructure"]["cloudBackhaulExtraDelaySeconds"],
                "classification": catalog["infrastructure"]["cloudBackhaulClassification"],
            },
        },
        "workloadGeneration": {
            "workloadMode": catalog["workloadGeneration"]["mode"],
            "randomSeed": catalog["workloadGeneration"]["randomSeed"],
            "arrivalRateTasksPerSecondPerActiveVehicle": catalog["workloadGeneration"]["arrivalRateTasksPerSecondPerActiveVehicle"],
            "profileWeights": {
                profile["profileId"]: profile["weight"]
                for profile in catalog["workloadGeneration"]["profiles"]
            },
            "outputSizeBits": {
                "value": 8000,
                "classification": catalog["workloadGeneration"]["outputSizeBitsClassification"],
            },
            "classification": catalog["workloadGeneration"]["classification"],
        },
        "pendingLiveStateExtensions": [
            "calibrated workload rate matrix",
            "40 replicate runner",
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
        f"- scenario-convert available: `{report['scenarioConvert']['available']}`",
        "",
    ]
    if report.get("selectedCandidate"):
        candidate = report["selectedCandidate"]
        lines.extend(
            [
                "## Selected Candidate",
                "",
                f"- traffic lights: {candidate['trafficLightCount']}",
                f"- mean active vehicles: {candidate['meanActiveVehicleCount']:.3f}",
                f"- max active vehicles: {candidate['maxActiveVehicleCount']}",
                f"- RSU positions: `{candidate['candidateRsuPositions']}`",
                "",
            ]
        )
    if report.get("routeSubsets"):
        lines.append("## Route Subsets")
        lines.append("")
        for name, subset in report["routeSubsets"].items():
            lines.append(
                f"- `{name}`: mean={subset['meanActiveVehicles']:.3f}, "
                f"max={subset['maxActiveVehicles']}, vehicles={subset['vehicleCount']}"
            )
        lines.append("")
    if report.get("warnings"):
        lines.append("## Warnings")
        lines.extend(f"- {warning}" for warning in report["warnings"])
        lines.append("")
    if report.get("errors"):
        lines.append("## Errors")
        lines.extend(f"- {error}" for error in report["errors"])
        lines.append("")
    lines.append("Generated assets are derived from an external InTAS checkout and must be reviewed for redistribution before committing.")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    targets = load_json(Path(args.targets))
    seeds = load_json(Path(args.seeds))["seeds"]
    catalog = load_json(Path(args.catalog))
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
    max_depart = max(int(value) for value in targets["durationsSeconds"].values())

    edges, junctions, metadata = parse_network(net_path)
    vtypes, routes, parse_warnings = parse_route_files(route_paths, edges, max_depart)
    candidates, selected, expansions = progressive_evaluate(edges, junctions, routes, targets)

    output_scenario = output_root / SCENARIO_NAME
    report_dir = output_scenario / "reports"
    report_dir.mkdir(parents=True, exist_ok=True)

    candidate_reports = [candidate_report(candidate, metadata, len(routes), edges) for candidate in candidates]
    selected_report = candidate_report(selected, metadata, len(routes), edges) if selected else None
    scenario_convert = find_scenario_convert(args.scenario_convert, Path("tmp/mosaic-25.2").resolve())
    projection = derive_projection(selected, net_path, metadata) if selected else {"valid": False, "method": "NO_SELECTED_CANDIDATE"}
    region_boundary = sumolib_geographic_boundary(selected.bounds, net_path) if selected else {"valid": False}

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
        "routeVehicleParseWindowSeconds": max_depart,
        "routeCountBeforeFiltering": len(routes),
        "candidateSearchExpansions": expansions,
        "candidateCount": len(candidates),
        "selectedCandidateId": selected.candidate_id if selected else None,
        "selectedCandidate": selected_report,
        "candidates": candidate_reports,
        "projection": projection,
        "cellRegionGeographicBoundary": region_boundary,
        "scenarioConvert": scenario_convert,
        "textualConfigurationStatus": "NOT_GENERATED",
        "warnings": parse_warnings,
        "errors": [],
        "status": "READY_FOR_MATERIALIZATION" if selected else "NO_CANDIDATE_SATISFIES_REQUIREMENTS",
    }

    if not selected:
        report["errors"].append("No candidate satisfies all deterministic urban subscenario requirements.")
        write_json(report_dir / "intas_literature_materialization_report.json", report)
        write_markdown_report(report_dir / "intas_literature_materialization_report.md", report)
        print(json.dumps(report, indent=2, sort_keys=True))
        return 3
    if not projection.get("valid"):
        report["errors"].append("Cannot derive a valid numeric MOSAIC projection from InTAS network metadata.")
        write_json(report_dir / "intas_literature_materialization_report.json", report)
        write_markdown_report(report_dir / "intas_literature_materialization_report.md", report)
        print(json.dumps(report, indent=2, sort_keys=True))
        return 4
    if projection.get("method") != "SUMOLIB_CONVERT_XY2LONLAT" or projection.get("fallback") is not None:
        report["errors"].append("Phase 14C.2 requires SUMOLIB_CONVERT_XY2LONLAT projection without fallback.")
        write_json(report_dir / "intas_literature_materialization_report.json", report)
        write_markdown_report(report_dir / "intas_literature_materialization_report.md", report)
        print(json.dumps(report, indent=2, sort_keys=True))
        return 4
    if not region_boundary.get("valid"):
        report["errors"].append(f"Cannot derive valid Ingolstadt Cell region boundary: {region_boundary}")
        write_json(report_dir / "intas_literature_materialization_report.json", report)
        write_markdown_report(report_dir / "intas_literature_materialization_report.md", report)
        print(json.dumps(report, indent=2, sort_keys=True))
        return 4

    for relative in ("application", "mapping", "sumo", "sns", "cell"):
        (output_scenario / relative).mkdir(parents=True, exist_ok=True)

    write_scenario_files(output_scenario, selected, targets, args.duration_profile, projection)

    density_names = ["low_density", "nominal", "high_density"] if args.density == "all" else [args.density]
    duration = int(targets["durationsSeconds"]["nominal"])
    route_outputs: dict[str, Any] = {}
    selected_subset_edges = set(selected.edge_ids)
    for density in density_names:
        target = int(targets["activeVehicleTargets"][density])
        seed = int(seeds[density_names.index(density) % len(seeds)])
        subset = select_route_subset(selected.routes, target, duration, seed)
        route_path = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}_{density}.rou.xml"
        preservation = write_route_file(route_path, subset, vtypes)
        counts = active_counts(subset, duration)
        for vehicle in subset:
            selected_subset_edges.update(vehicle.all_route_edges)
        route_outputs[density] = {
            "routeFile": str(route_path),
            "targetMeanActiveVehicles": target,
            "meanActiveVehicles": sum(counts.values()) / max(len(counts), 1),
            "maxActiveVehicles": max(counts.values()) if counts else 0,
            "vehicleCount": len(subset),
            "seed": seed,
            "xmlPreservation": preservation,
        }

    concrete_route = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}_nominal.rou.xml"
    if not concrete_route.exists():
        concrete_route = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}_{density_names[0]}.rou.xml"

    net_output = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}.net.xml"
    edge_list = report_dir / "selected_edge_ids.txt"
    edge_list.write_text("\n".join(sorted(selected_subset_edges)) + "\n", encoding="utf-8")
    used_vtype_ids = sorted(
        {
            vtype
            for output in route_outputs.values()
            for vtype in output["xmlPreservation"]["vTypeIdsWritten"]
        }
    )
    report["usedVTypeIds"] = used_vtype_ids

    netconvert = shutil.which("netconvert")

    if args.dry_run:
        if not netconvert:
            report["errors"].append("netconvert is required to produce concrete dry-run network files.")
        else:
            run_checked(
                [netconvert, "--sumo-net-file", str(net_path), "--keep-edges.input-file", str(edge_list), "--output-file", str(net_output)],
                output_scenario,
            )
        write_sumocfg(
            output_scenario / "sumo" / f"{SUBSCENARIO_NAME}.sumocfg",
            net_output.name,
            concrete_route.name,
            targets["sumoStepLengthSeconds"],
        )
        report["status"] = "DRY_RUN_COMPLETED" if scenario_convert.get("available") else "PARTIAL_EXTERNAL_TOOL_REQUIRED"
        report["dryRunStatus"] = "DRY_RUN_COMPLETED"
        report["routeSubsets"] = route_outputs
    else:
        if not netconvert:
            report["status"] = "PARTIAL_EXTERNAL_TOOL_REQUIRED"
            report["errors"].append("netconvert is required to extract the reduced SUMO network.")
        else:
            run_checked(
                [netconvert, "--sumo-net-file", str(net_path), "--keep-edges.input-file", str(edge_list), "--output-file", str(net_output)],
                output_scenario,
            )
            write_sumocfg(output_scenario / "sumo" / f"{SUBSCENARIO_NAME}.sumocfg", net_output.name, concrete_route.name, targets["sumoStepLengthSeconds"])
            report["routeSubsets"] = route_outputs
            report["reducedNetwork"] = str(net_output)
            if scenario_convert.get("available"):
                db_path = output_scenario / "application" / f"{SUBSCENARIO_NAME}.db"
                run_checked([scenario_convert["path"], "--sumo2db", "-i", str(net_output)], output_scenario)
                generated_default = output_scenario / "sumo" / f"{SUBSCENARIO_NAME}.db"
                if generated_default.exists() and generated_default != db_path:
                    shutil.move(str(generated_default), str(db_path))
                run_checked([scenario_convert["path"], "--sumo2db", "-i", str(concrete_route), "-d", str(db_path)], output_scenario)
                report["database"] = str(db_path)
                report["status"] = "COMPLETED"
            else:
                report["status"] = "PARTIAL_EXTERNAL_TOOL_REQUIRED"
                report["warnings"].append(
                    "scenario-convert not found. Use MOSAIC Extended Scenario-Convert before full materialization."
                )

    if not report["errors"]:
        report["generatedConfigurations"] = write_literature_runtime_configs(
            output_scenario,
            selected,
            used_vtype_ids,
            net_path,
            metadata,
            catalog,
            region_boundary,
        )
        report["textualConfigurationStatus"] = "GENERATED"

    write_metadata(output_scenario / "application" / "ma_ga_calibration_metadata.json", report, catalog)
    generated_validation = validate_generated_outputs(output_scenario, report, selected, route_outputs, scenario_convert)
    report["generatedOutputValidation"] = generated_validation
    report["warnings"].extend(generated_validation["warnings"])
    report["errors"].extend(generated_validation["errors"])
    if report["errors"]:
        report["status"] = "FAILED_OUTPUT_VALIDATION"

    write_json(report_dir / "intas_literature_materialization_report.json", report)
    write_markdown_report(report_dir / "intas_literature_materialization_report.md", report)
    standalone_validator = TOOL_DIR / "validate_literature_configuration.py"
    if not report["errors"] and standalone_validator.exists():
        validator_result = subprocess.run(
            [sys.executable, "-B", str(standalone_validator), "--scenario-root", str(output_scenario)],
            text=True,
            capture_output=True,
        )
        report["standaloneConfigurationValidator"] = {
            "exitCode": validator_result.returncode,
            "stdout": validator_result.stdout.strip(),
            "stderr": validator_result.stderr.strip(),
        }
        if validator_result.returncode != 0:
            report["errors"].append("Standalone literature configuration validator failed.")
            report["status"] = "FAILED_OUTPUT_VALIDATION"
        else:
            report["textualConfigurationStatus"] = "GENERATED_AND_VALIDATED"
    write_json(report_dir / "intas_literature_materialization_report.json", report)
    write_markdown_report(report_dir / "intas_literature_materialization_report.md", report)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["status"] in {"COMPLETED", "DRY_RUN_COMPLETED", "PARTIAL_EXTERNAL_TOOL_REQUIRED"} else 5


if __name__ == "__main__":
    sys.exit(main())
