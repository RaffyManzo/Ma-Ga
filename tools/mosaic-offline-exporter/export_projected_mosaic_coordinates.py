#!/usr/bin/env python3
"""Project MOSAIC geographic coordinates into SUMO network metric coordinates."""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
import xml.etree.ElementTree as ET
from pathlib import Path


PHASE = "10I_PRE2_SUMO_PROJECTION_ALIGNMENT"
PROJECTION_POLICY = "SUMO_NET_XML_UTM_WGS84_WITH_NET_OFFSET"
PROJECTION_UTILITY = "STANDARD_LIBRARY_UTM_WGS84_FROM_SUMO_PROJ_PARAMETER"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Project MOSAIC lat/lon streams into SUMO Cartesian coordinates."
    )
    parser.add_argument("--vehicle-state-file", required=True)
    parser.add_argument("--infrastructure-file", required=True)
    parser.add_argument("--sumo-network-file", required=True)
    parser.add_argument("--vehicle-state-out-file", required=True)
    parser.add_argument("--infrastructure-out-file", required=True)
    parser.add_argument("--validation-out-file", required=True)
    parser.add_argument(
        "--round-trip-error-threshold-meters",
        type=float,
        default=None,
        help="Optional diagnostic threshold. If omitted, round-trip errors are reported only.",
    )
    return parser.parse_args()


def require_file(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Required file not found: {path}")


def read_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    require_file(path)
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None:
            raise ValueError(f"CSV has no header: {path}")
        return list(reader.fieldnames), list(reader)


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def parse_location(network_file: Path) -> dict[str, str]:
    require_file(network_file)
    root = ET.parse(network_file).getroot()
    location = root.find("location")
    if location is None:
        raise ValueError(f"SUMO network has no <location> element: {network_file}")
    result = dict(location.attrib)
    required = ["netOffset", "convBoundary", "origBoundary", "projParameter"]
    missing = [name for name in required if name not in result or not result[name].strip()]
    if missing:
        raise ValueError(f"SUMO <location> missing required attributes: {missing}")
    return result


def parse_pair(value: str, field_name: str) -> tuple[float, float]:
    parts = [part.strip() for part in value.split(",")]
    if len(parts) != 2:
        raise ValueError(f"{field_name} must contain two comma-separated numbers.")
    return float(parts[0]), float(parts[1])


def parse_bounds(value: str, field_name: str) -> tuple[float, float, float, float]:
    parts = [part.strip() for part in value.split(",")]
    if len(parts) != 4:
        raise ValueError(f"{field_name} must contain four comma-separated numbers.")
    return float(parts[0]), float(parts[1]), float(parts[2]), float(parts[3])


def parse_proj_parameter(value: str) -> dict[str, str | bool]:
    result: dict[str, str | bool] = {}
    for token in value.split():
        token = token.strip()
        if not token:
            continue
        if not token.startswith("+"):
            raise ValueError(f"Unsupported proj token without '+': {token}")
        body = token[1:]
        if "=" in body:
            key, raw_value = body.split("=", 1)
            result[key] = raw_value
        else:
            result[body] = True
    return result


class SumoUtmProjection:
    """SUMO-compatible UTM projection for WGS84 networks with netOffset."""

    def __init__(self, location: dict[str, str]):
        self.location = location
        self.net_offset_x, self.net_offset_y = parse_pair(location["netOffset"], "netOffset")
        self.conv_boundary = parse_bounds(location["convBoundary"], "convBoundary")
        self.orig_boundary = parse_bounds(location["origBoundary"], "origBoundary")
        self.proj = parse_proj_parameter(location["projParameter"])
        self.zone = self._require_utm_wgs84_zone()
        self.a = 6378137.0
        self.f = 1.0 / 298.257223563
        self.e2 = self.f * (2.0 - self.f)
        self.ep2 = self.e2 / (1.0 - self.e2)
        self.k0 = 0.9996
        self.false_easting = 500000.0
        self.false_northing = 0.0
        self.central_meridian = math.radians(self.zone * 6 - 183)

    def _require_utm_wgs84_zone(self) -> int:
        errors: list[str] = []
        if self.proj.get("proj") != "utm":
            errors.append("proj must be utm")
        if self.proj.get("ellps") != "WGS84":
            errors.append("ellps must be WGS84")
        if self.proj.get("datum") != "WGS84":
            errors.append("datum must be WGS84")
        if self.proj.get("units") != "m":
            errors.append("units must be m")
        raw_zone = self.proj.get("zone")
        if not isinstance(raw_zone, str):
            errors.append("zone must be present")
        if errors:
            raise ValueError("Unsupported SUMO projection: " + "; ".join(errors))
        zone = int(raw_zone)
        if zone <= 0:
            raise ValueError(f"Unsupported UTM zone: {zone}")
        return zone

    def lon_lat_to_xy(self, longitude: float, latitude: float) -> tuple[float, float]:
        easting, northing = self._lon_lat_to_raw_utm(longitude, latitude)
        return easting + self.net_offset_x, northing + self.net_offset_y

    def xy_to_lon_lat(self, x: float, y: float) -> tuple[float, float]:
        return self._raw_utm_to_lon_lat(x - self.net_offset_x, y - self.net_offset_y)

    def _lon_lat_to_raw_utm(self, longitude: float, latitude: float) -> tuple[float, float]:
        lat = math.radians(latitude)
        lon = math.radians(longitude)
        sin_lat = math.sin(lat)
        cos_lat = math.cos(lat)
        tan_lat = math.tan(lat)
        n = self.a / math.sqrt(1.0 - self.e2 * sin_lat * sin_lat)
        t = tan_lat * tan_lat
        c = self.ep2 * cos_lat * cos_lat
        a = cos_lat * (lon - self.central_meridian)
        m = self._meridional_arc(lat)
        easting = self.false_easting + self.k0 * n * (
            a
            + (1.0 - t + c) * a**3 / 6.0
            + (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * self.ep2) * a**5 / 120.0
        )
        northing = self.false_northing + self.k0 * (
            m
            + n
            * tan_lat
            * (
                a * a / 2.0
                + (5.0 - t + 9.0 * c + 4.0 * c * c) * a**4 / 24.0
                + (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * self.ep2)
                * a**6
                / 720.0
            )
        )
        return easting, northing

    def _raw_utm_to_lon_lat(self, easting: float, northing: float) -> tuple[float, float]:
        x = easting - self.false_easting
        y = northing - self.false_northing
        m = y / self.k0
        mu = m / (
            self.a
            * (1.0 - self.e2 / 4.0 - 3.0 * self.e2**2 / 64.0 - 5.0 * self.e2**3 / 256.0)
        )
        e1 = (1.0 - math.sqrt(1.0 - self.e2)) / (1.0 + math.sqrt(1.0 - self.e2))
        fp = (
            mu
            + (3.0 * e1 / 2.0 - 27.0 * e1**3 / 32.0) * math.sin(2.0 * mu)
            + (21.0 * e1**2 / 16.0 - 55.0 * e1**4 / 32.0) * math.sin(4.0 * mu)
            + (151.0 * e1**3 / 96.0) * math.sin(6.0 * mu)
            + (1097.0 * e1**4 / 512.0) * math.sin(8.0 * mu)
        )
        sin_fp = math.sin(fp)
        cos_fp = math.cos(fp)
        tan_fp = math.tan(fp)
        c1 = self.ep2 * cos_fp * cos_fp
        t1 = tan_fp * tan_fp
        n1 = self.a / math.sqrt(1.0 - self.e2 * sin_fp * sin_fp)
        r1 = n1 * (1.0 - self.e2) / (1.0 - self.e2 * sin_fp * sin_fp)
        d = x / (n1 * self.k0)
        lat = fp - (n1 * tan_fp / r1) * (
            d * d / 2.0
            - (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * self.ep2)
            * d**4
            / 24.0
            + (
                61.0
                + 90.0 * t1
                + 298.0 * c1
                + 45.0 * t1 * t1
                - 252.0 * self.ep2
                - 3.0 * c1 * c1
            )
            * d**6
            / 720.0
        )
        lon = self.central_meridian + (
            d
            - (1.0 + 2.0 * t1 + c1) * d**3 / 6.0
            + (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * self.ep2 + 24.0 * t1 * t1)
            * d**5
            / 120.0
        ) / cos_fp
        return math.degrees(lon), math.degrees(lat)

    def _meridional_arc(self, lat: float) -> float:
        e4 = self.e2 * self.e2
        e6 = e4 * self.e2
        return self.a * (
            (1.0 - self.e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * lat
            - (3.0 * self.e2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0)
            * math.sin(2.0 * lat)
            + (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * math.sin(4.0 * lat)
            - (35.0 * e6 / 3072.0) * math.sin(6.0 * lat)
        )


def to_float(value: str | int | float | None, field: str) -> float:
    if value is None or str(value).strip() == "":
        raise ValueError(f"Missing numeric value for {field}")
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"Non-finite numeric value for {field}: {value}")
    return result


def format_number(value: float) -> str:
    if not math.isfinite(value):
        raise ValueError(f"Cannot format non-finite value: {value}")
    return f"{value:.6f}"


def haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6371008.8
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = phi2 - phi1
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2.0) ** 2
    return 2.0 * radius * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))


def projected_distance(x1: float, y1: float, x2: float, y2: float) -> float:
    return math.hypot(x1 - x2, y1 - y2)


def infer_source_run(infrastructure: dict) -> str | None:
    output_csv = infrastructure.get("source", {}).get("outputCsv")
    if not output_csv:
        return None
    parts = Path(str(output_csv).replace("\\", "/")).parts
    for part in parts:
        if part.startswith("log-"):
            return part
    return None


def project_vehicle_rows(
    rows: list[dict[str, str]],
    fieldnames: list[str],
    projection: SumoUtmProjection,
    source_file: Path,
) -> tuple[list[str], list[dict[str, str]], list[tuple[float, float, float, float, float, float]]]:
    required = {"latitude", "longitude", "projectedX", "projectedY"}
    missing = sorted(required - set(fieldnames))
    if missing:
        raise ValueError(f"vehicle_state_stream.csv missing columns: {missing}")
    out_fields = list(fieldnames)
    for field in ["projectionPolicy", "projectionSource"]:
        if field not in out_fields:
            out_fields.append(field)

    projected_rows: list[dict[str, str]] = []
    points: list[tuple[float, float, float, float, float, float]] = []
    for row in rows:
        lat = to_float(row.get("latitude"), "vehicle.latitude")
        lon = to_float(row.get("longitude"), "vehicle.longitude")
        x, y = projection.lon_lat_to_xy(lon, lat)
        back_lon, back_lat = projection.xy_to_lon_lat(x, y)
        out = dict(row)
        out["projectedX"] = format_number(x)
        out["projectedY"] = format_number(y)
        out["projectionPolicy"] = PROJECTION_POLICY
        out["projectionSource"] = str(source_file)
        projected_rows.append(out)
        points.append((lat, lon, x, y, back_lat, back_lon))
    return out_fields, projected_rows, points


def project_infrastructure(
    infrastructure: dict,
    projection: SumoUtmProjection,
    source_file: Path,
) -> tuple[dict, list[tuple[float, float, float, float, float, float]]]:
    result = json.loads(json.dumps(infrastructure))
    gateways = result.get("gateways")
    if not isinstance(gateways, list):
        raise ValueError("infrastructure_snapshot.json must contain a gateways list.")
    points: list[tuple[float, float, float, float, float, float]] = []
    for gateway in gateways:
        lat = to_float(gateway.get("latitude"), "gateway.latitude")
        lon = to_float(gateway.get("longitude"), "gateway.longitude")
        x, y = projection.lon_lat_to_xy(lon, lat)
        back_lon, back_lat = projection.xy_to_lon_lat(x, y)
        gateway["projectedX"] = x
        gateway["projectedY"] = y
        gateway["projectionPolicy"] = PROJECTION_POLICY
        gateway["projectionSource"] = str(source_file)
        points.append((lat, lon, x, y, back_lat, back_lon))

    registrations = result.get("runtimeRegistrations", {}).get("rsus", [])
    if isinstance(registrations, list):
        for registration in registrations:
            if "latitude" in registration and "longitude" in registration:
                lat = to_float(registration.get("latitude"), "rsuRegistration.latitude")
                lon = to_float(registration.get("longitude"), "rsuRegistration.longitude")
                x, y = projection.lon_lat_to_xy(lon, lat)
                registration["projectedX"] = x
                registration["projectedY"] = y
                registration["projectionPolicy"] = PROJECTION_POLICY
                registration["projectionSource"] = str(source_file)

    result["projection"] = {
        "projectionPolicy": PROJECTION_POLICY,
        "projectionSourceFile": str(source_file),
        "projectionUtility": PROJECTION_UTILITY,
        "projectionUtilitySource": "SUMO net.xml <location> projParameter and netOffset; sumolib semantics verified locally",
        "projectionParameters": projection.location,
    }
    return result, points


def stats(values: list[float]) -> dict[str, float | int]:
    if not values:
        return {"samples": 0, "minimum": 0.0, "maximum": 0.0, "average": 0.0, "median": 0.0}
    return {
        "samples": len(values),
        "minimum": min(values),
        "maximum": max(values),
        "average": statistics.fmean(values),
        "median": statistics.median(values),
    }


def build_distance_comparisons(
    vehicle_rows: list[dict[str, str]],
    gateways: list[dict],
) -> dict[str, float | int]:
    differences: list[float] = []
    for vehicle in vehicle_rows:
        vehicle_lat = to_float(vehicle.get("latitude"), "vehicle.latitude")
        vehicle_lon = to_float(vehicle.get("longitude"), "vehicle.longitude")
        vehicle_x = to_float(vehicle.get("projectedX"), "vehicle.projectedX")
        vehicle_y = to_float(vehicle.get("projectedY"), "vehicle.projectedY")
        for gateway in gateways:
            gateway_lat = to_float(gateway.get("latitude"), "gateway.latitude")
            gateway_lon = to_float(gateway.get("longitude"), "gateway.longitude")
            gateway_x = to_float(gateway.get("projectedX"), "gateway.projectedX")
            gateway_y = to_float(gateway.get("projectedY"), "gateway.projectedY")
            projected = projected_distance(vehicle_x, vehicle_y, gateway_x, gateway_y)
            geographic = haversine_meters(vehicle_lat, vehicle_lon, gateway_lat, gateway_lon)
            differences.append(abs(projected - geographic))
    return stats(differences)


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, sort_keys=True)
        handle.write("\n")


def main() -> int:
    args = parse_args()
    vehicle_state_file = Path(args.vehicle_state_file)
    infrastructure_file = Path(args.infrastructure_file)
    network_file = Path(args.sumo_network_file)
    vehicle_out_file = Path(args.vehicle_state_out_file)
    infrastructure_out_file = Path(args.infrastructure_out_file)
    validation_out_file = Path(args.validation_out_file)

    for path in [vehicle_state_file, infrastructure_file, network_file]:
        require_file(path)

    location = parse_location(network_file)
    projection = SumoUtmProjection(location)
    vehicle_fields, vehicle_rows = read_csv(vehicle_state_file)
    infrastructure = json.load(infrastructure_file.open(encoding="utf-8"))
    source_run = infer_source_run(infrastructure)

    errors: list[str] = []
    warnings: list[str] = []
    if args.round_trip_error_threshold_meters is not None and args.round_trip_error_threshold_meters < 0:
        errors.append("round-trip threshold must be >= 0 when provided.")

    projected_fields, projected_vehicle_rows, vehicle_points = project_vehicle_rows(
        vehicle_rows,
        vehicle_fields,
        projection,
        network_file,
    )
    projected_infrastructure, gateway_points = project_infrastructure(
        infrastructure,
        projection,
        network_file,
    )

    round_trip_errors = [
        haversine_meters(lat, lon, back_lat, back_lon)
        for lat, lon, _x, _y, back_lat, back_lon in vehicle_points + gateway_points
    ]
    if args.round_trip_error_threshold_meters is not None:
        threshold = args.round_trip_error_threshold_meters
        if round_trip_errors and max(round_trip_errors) > threshold:
            errors.append(
                f"round-trip maximum error exceeds threshold: {max(round_trip_errors)} > {threshold}"
            )

    all_x = [point[2] for point in vehicle_points + gateway_points]
    all_y = [point[3] for point in vehicle_points + gateway_points]
    non_finite = sum(
        1 for value in all_x + all_y if not math.isfinite(value)
    )
    missing = sum(
        1
        for row in projected_vehicle_rows
        if not row.get("projectedX") or not row.get("projectedY")
    )
    for gateway in projected_infrastructure.get("gateways", []):
        if "projectedX" not in gateway or "projectedY" not in gateway:
            missing += 1

    if non_finite:
        errors.append(f"non-finite projected coordinates: {non_finite}")
    if missing:
        errors.append(f"missing projected coordinates: {missing}")
    if not gateway_points:
        errors.append("no gateways projected")
    if not vehicle_points:
        errors.append("no vehicle states projected")

    distance_stats = build_distance_comparisons(
        projected_vehicle_rows,
        projected_infrastructure.get("gateways", []),
    )
    round_trip_stats = stats(round_trip_errors)

    status = "COMPLETED" if not errors else "FAILED"
    ready = not errors
    validation = {
        "sourceRun": source_run,
        "phase": PHASE,
        "projectionPolicy": PROJECTION_POLICY,
        "projectionSourceFile": str(network_file),
        "projectionUtility": PROJECTION_UTILITY,
        "projectionUtilitySource": (
            "SUMO_HOME tools/sumolib was inspected; convertLonLat2XY semantics are UTM plus "
            "netOffset, but local pyproj is unavailable. The exporter implements UTM WGS84 "
            "from the SUMO projParameter and applies the SUMO netOffset."
        ),
        "projectionParameters": {
            "netOffset": location["netOffset"],
            "convBoundary": location["convBoundary"],
            "origBoundary": location["origBoundary"],
            "projParameter": location["projParameter"],
            "parsedProjParameter": projection.proj,
        },
        "vehicleStatesRead": len(vehicle_rows),
        "vehicleStatesProjected": len(projected_vehicle_rows),
        "gatewaysRead": len(infrastructure.get("gateways", [])),
        "gatewaysProjected": len(gateway_points),
        "nonFiniteProjectedCoordinates": non_finite,
        "missingProjectedCoordinates": missing,
        "roundTripValidationSamples": round_trip_stats["samples"],
        "roundTripMaximumErrorMeters": round_trip_stats["maximum"],
        "roundTripAverageErrorMeters": round_trip_stats["average"],
        "roundTripMedianErrorMeters": round_trip_stats["median"],
        "distanceComparisonSamples": distance_stats["samples"],
        "minimumProjectedVsHaversineDifferenceMeters": distance_stats["minimum"],
        "maximumProjectedVsHaversineDifferenceMeters": distance_stats["maximum"],
        "averageProjectedVsHaversineDifferenceMeters": distance_stats["average"],
        "medianProjectedVsHaversineDifferenceMeters": distance_stats["median"],
        "minimumProjectedX": min(all_x) if all_x else None,
        "maximumProjectedX": max(all_x) if all_x else None,
        "minimumProjectedY": min(all_y) if all_y else None,
        "maximumProjectedY": max(all_y) if all_y else None,
        "warnings": warnings,
        "errors": errors,
        "phase10iPre2Status": status,
        "readyForPhase10I": ready,
    }

    write_csv(vehicle_out_file, projected_fields, projected_vehicle_rows)
    write_json(infrastructure_out_file, projected_infrastructure)
    write_json(validation_out_file, validation)

    print("Phase 10I-pre2 SUMO projection export completed")
    print(f"sourceRun={source_run}")
    print(f"projectionPolicy={PROJECTION_POLICY}")
    print(f"projectionUtility={PROJECTION_UTILITY}")
    print(f"vehicleStatesRead={len(vehicle_rows)}")
    print(f"vehicleStatesProjected={len(projected_vehicle_rows)}")
    print(f"gatewaysRead={len(infrastructure.get('gateways', []))}")
    print(f"gatewaysProjected={len(gateway_points)}")
    print(f"roundTripValidationSamples={round_trip_stats['samples']}")
    print(f"roundTripMaximumErrorMeters={round_trip_stats['maximum']}")
    print(f"distanceComparisonSamples={distance_stats['samples']}")
    print(f"maximumProjectedVsHaversineDifferenceMeters={distance_stats['maximum']}")
    print(f"phase10iPre2Status={status}")
    print(f"readyForPhase10I={str(ready).lower()}")
    print(f"warningsCount={len(warnings)}")
    print(f"errorsCount={len(errors)}")

    return 0 if ready else 1


if __name__ == "__main__":
    raise SystemExit(main())
