#!/usr/bin/env python3
"""Export integrated Cell handover and bandwidth streams for MA-GA diagnostics."""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
import tempfile
from collections import Counter
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


HANDOVER_COLUMNS = [
    "timeNs",
    "timeSeconds",
    "vehicleId",
    "previousRegion",
    "currentRegion",
    "eventType",
    "sourceFile",
]

BANDWIDTH_COLUMNS = [
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
]

UNIT_STATUS = "PROVEN_BITS_PER_SECOND"
RESIDUAL_POLICY = "NOMINAL_MINUS_OBSERVED_DIAGNOSTIC"
BUCKET_BOUNDARY_POLICY = "START_TIMESTAMP_FOR_INTERVAL"
AVAILABLE_FROM_POLICY = "SAFE_AFTER_TIMESTAMP"
NULL_VALUES = {"", "null", "NULL", "None"}


class ExportError(Exception):
    """Raised when the input data cannot be exported safely."""


def natural_key(value: str) -> list[Any]:
    return [int(part) if part.isdigit() else part for part in re.split(r"(\d+)", value)]


def require_file(path: Path, label: str) -> None:
    if not path.exists():
        raise ExportError(f"{label} does not exist: {path}")
    if not path.is_file():
        raise ExportError(f"{label} is not a file: {path}")


def require_dir(path: Path, label: str) -> None:
    if not path.exists():
        raise ExportError(f"{label} does not exist: {path}")
    if not path.is_dir():
        raise ExportError(f"{label} is not a directory: {path}")


def parse_non_negative_int(raw: str, label: str, context: str) -> int:
    try:
        value = int(raw)
    except ValueError as exc:
        raise ExportError(f"{context}: {label} is not a valid integer: {raw!r}") from exc
    if value < 0:
        raise ExportError(f"{context}: {label} must be >= 0, got {value}")
    return value


def parse_decimal(raw: str, label: str, context: str) -> Decimal:
    try:
        value = Decimal(raw.strip())
    except (InvalidOperation, AttributeError) as exc:
        raise ExportError(f"{context}: {label} is not a valid number: {raw!r}") from exc
    if not value.is_finite():
        raise ExportError(f"{context}: {label} must be finite, got {raw!r}")
    return value


def decimal_to_text(value: Decimal) -> str:
    if value == value.to_integral_value():
        return str(value.to_integral_value())
    return format(value.normalize(), "f")


def ns_to_seconds_text(time_ns: int) -> str:
    return decimal_to_text(Decimal(time_ns) / Decimal(1_000_000_000))


def normalize_nullable_region(raw: str) -> str:
    value = raw.strip()
    return "" if value in NULL_VALUES else value


def load_json(path: Path, label: str) -> dict[str, Any]:
    require_file(path, label)
    try:
        with path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
    except json.JSONDecodeError as exc:
        raise ExportError(f"{label} is not valid JSON: {path}: {exc}") from exc
    if not isinstance(data, dict):
        raise ExportError(f"{label} must be a JSON object: {path}")
    return data


def extract_region_id(region: dict[str, Any]) -> str:
    for key in ("regionId", "id", "name"):
        value = region.get(key)
        if isinstance(value, str) and value:
            return value
    raise ExportError(f"Cell region is missing regionId/id/name: {region}")


def known_regions(infrastructure: dict[str, Any]) -> set[str]:
    cell = infrastructure.get("cell")
    if not isinstance(cell, dict):
        raise ExportError("infrastructure_snapshot.json is missing object cell")
    regions = cell.get("regions")
    if not isinstance(regions, list):
        raise ExportError("infrastructure_snapshot.json is missing list cell.regions")
    region_ids = {extract_region_id(region) for region in regions if isinstance(region, dict)}
    if not region_ids:
        raise ExportError("infrastructure_snapshot.json contains no Cell regions")
    region_ids.add("globalNetwork")
    return region_ids


def bitrate_to_bits_per_second(raw: str, label: str) -> Decimal:
    match = re.fullmatch(
        r"\s*([+]?(?:\d+(?:\.\d*)?|\.\d+))\s*"
        r"(bps|bit/s|bits/s|Kbps|kbps|Mbps|mbps|Gbps|gbps|Tbps|tbps)\s*",
        raw,
    )
    if not match:
        value = parse_decimal(raw, label, "infrastructure_snapshot.json")
        if value <= 0:
            raise ExportError(f"{label} must be > 0, got {value}")
        return value
    amount = parse_decimal(match.group(1), label, "infrastructure_snapshot.json")
    unit = match.group(2).lower()
    factors = {
        "bps": Decimal(1),
        "bit/s": Decimal(1),
        "bits/s": Decimal(1),
        "kbps": Decimal(1_000),
        "mbps": Decimal(1_000_000),
        "gbps": Decimal(1_000_000_000),
        "tbps": Decimal(1_000_000_000_000),
    }
    value = amount * factors[unit]
    if value <= 0:
        raise ExportError(f"{label} must be > 0, got {value}")
    return value


def number_from_config(raw: Any, label: str) -> Decimal:
    if isinstance(raw, bool):
        raise ExportError(f"{label} must be numeric, got boolean")
    if isinstance(raw, str):
        return bitrate_to_bits_per_second(raw, label)
    if isinstance(raw, (int, float)):
        value = parse_decimal(str(raw), label, "infrastructure_snapshot.json")
        if value <= 0:
            raise ExportError(f"{label} must be > 0, got {value}")
        return value
    raise ExportError(f"{label} must be numeric, got {type(raw).__name__}")


def capacity_from_network(network: dict[str, Any], direction: str, label: str) -> Decimal:
    if direction == "UPLINK":
        uplink = network.get("uplink")
        if not isinstance(uplink, dict):
            raise ExportError(f"{label} is missing uplink object")
        return number_from_config(uplink.get("capacity"), f"{label}.uplink.capacity")

    downlink = network.get("downlink")
    if not isinstance(downlink, dict):
        raise ExportError(f"{label} is missing downlink object")
    if "capacity" in downlink:
        return number_from_config(downlink.get("capacity"), f"{label}.downlink.capacity")
    unicast = downlink.get("unicast")
    if isinstance(unicast, dict) and "capacity" in unicast:
        return number_from_config(unicast.get("capacity"), f"{label}.downlink.unicast.capacity")
    raise ExportError(f"{label} is missing downlink capacity")


def build_capacity_index(infrastructure: dict[str, Any]) -> dict[tuple[str, str], Decimal]:
    cell = infrastructure["cell"]
    result: dict[tuple[str, str], Decimal] = {}

    global_network = cell.get("globalNetwork")
    if not isinstance(global_network, dict):
        raise ExportError("infrastructure_snapshot.json is missing cell.globalNetwork")
    for direction in ("UPLINK", "DOWNLINK"):
        result[("globalNetwork", direction)] = capacity_from_network(
            global_network,
            direction,
            "cell.globalNetwork",
        )

    for region in cell.get("regions", []):
        if not isinstance(region, dict):
            raise ExportError("cell.regions must contain objects")
        region_id = extract_region_id(region)
        network = region.get("network")
        if not isinstance(network, dict):
            network = region
        for direction in ("UPLINK", "DOWNLINK"):
            result[(region_id, direction)] = capacity_from_network(
                network,
                direction,
                f"cell.regions[{region_id}]",
            )
    return result


def classify_handover(previous: str, current: str, context: str) -> str:
    if not previous and current:
        return "REGISTRATION"
    if previous and current and previous != current:
        return "REGION_TRANSITION"
    if previous and not current:
        return "REMOVAL"
    if not previous and not current:
        raise ExportError(f"{context}: previousRegion and currentRegion are both null")
    raise ExportError(f"{context}: previousRegion and currentRegion are equal: {previous}")


def parse_output_csv(output_csv: Path, valid_regions: set[str]) -> tuple[list[dict[str, str]], Counter[str], int | None]:
    require_file(output_csv, "output CSV")
    rows: list[dict[str, str]] = []
    counts: Counter[str] = Counter()
    seen: set[tuple[str, ...]] = set()
    previous_relevant_time: int | None = None
    max_vehicle_update_time_ns: int | None = None

    with output_csv.open("r", encoding="utf-8", newline="") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.rstrip("\n\r")
            if not line:
                continue
            fields = line.split(";")
            marker = fields[0]

            if marker == "VEHICLE_UPDATES":
                if len(fields) >= 2:
                    time_ns = parse_non_negative_int(fields[1], "timeNs", f"{output_csv}:{line_number}")
                    max_vehicle_update_time_ns = (
                        time_ns if max_vehicle_update_time_ns is None else max(max_vehicle_update_time_ns, time_ns)
                    )
                continue

            if marker != "CELLULAR_HANDOVER":
                continue

            context = f"{output_csv}:{line_number}"
            if len(fields) < 5:
                raise ExportError(f"{context}: CELLULAR_HANDOVER has fewer than 5 fields")

            time_ns = parse_non_negative_int(fields[1], "timeNs", context)
            if previous_relevant_time is not None and time_ns < previous_relevant_time:
                raise ExportError(f"{context}: CELLULAR_HANDOVER time decreased")
            previous_relevant_time = time_ns

            vehicle_id = fields[2].strip()
            if not vehicle_id:
                raise ExportError(f"{context}: vehicleId is empty")

            previous_region = normalize_nullable_region(fields[3])
            current_region = normalize_nullable_region(fields[4])
            for label, region in (("previousRegion", previous_region), ("currentRegion", current_region)):
                if region and region not in valid_regions:
                    raise ExportError(f"{context}: {label} is unknown: {region}")

            event_type = classify_handover(previous_region, current_region, context)
            key = (str(time_ns), vehicle_id, previous_region, current_region, event_type)
            if key in seen:
                raise ExportError(f"{context}: duplicate CELLULAR_HANDOVER event: {key}")
            seen.add(key)
            counts[event_type] += 1
            rows.append(
                {
                    "timeNs": str(time_ns),
                    "timeSeconds": ns_to_seconds_text(time_ns),
                    "vehicleId": vehicle_id,
                    "previousRegion": previous_region,
                    "currentRegion": current_region,
                    "eventType": event_type,
                    "sourceFile": str(output_csv),
                }
            )

    if not rows:
        raise ExportError(f"No CELLULAR_HANDOVER rows found in {output_csv}")

    rows.sort(
        key=lambda row: (
            int(row["timeNs"]),
            natural_key(row["vehicleId"]),
            row["previousRegion"],
            row["currentRegion"],
        )
    )
    return rows, counts, max_vehicle_update_time_ns


def direction_from_file(path: Path) -> str:
    name = path.name.lower()
    if name.endswith("#up.csv") or name.endswith("up.csv"):
        return "UPLINK"
    if name.endswith("#dn.csv") or name.endswith("dn.csv") or name.endswith("#down.csv"):
        return "DOWNLINK"
    raise ExportError(f"Cannot derive Cell bandwidth direction from file name: {path.name}")


def discover_bandwidth_files(directory: Path) -> list[Path]:
    require_dir(directory, "bandwidthMeasurements directory")
    files = sorted(directory.glob("*.csv"), key=lambda path: path.name.lower())
    if not files:
        raise ExportError(f"No CSV files found in {directory}")
    required = {"ALL#ALL#ALL#Up.csv", "ALL#ALL#ALL#Dn.csv"}
    found = {path.name for path in files}
    missing = sorted(required - found)
    if missing:
        raise ExportError(f"Missing required integrated Cell bandwidth CSV files: {', '.join(missing)}")
    return [path for path in files if path.name in required]


def infer_bucket_interval(times: list[Decimal], source_file: Path) -> Decimal:
    unique_times = sorted(set(times))
    deltas = [later - earlier for earlier, later in zip(unique_times, unique_times[1:]) if later > earlier]
    if not deltas:
        raise ExportError(f"{source_file}: cannot infer bucket interval from fewer than two distinct times")
    interval = min(deltas)
    if interval <= 0:
        raise ExportError(f"{source_file}: invalid inferred bucket interval {interval}")
    return interval


def parse_bandwidth_file(
    path: Path,
    valid_regions: set[str],
    capacity_index: dict[tuple[str, str], Decimal],
    max_vehicle_update_time_ns: int | None,
) -> tuple[list[dict[str, str]], int]:
    require_file(path, "Cell bandwidth CSV")
    direction = direction_from_file(path)

    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.reader(handle)
        try:
            header = next(reader)
        except StopIteration as exc:
            raise ExportError(f"{path}: CSV is empty") from exc

        if not header or header[0].strip() != "time":
            raise ExportError(f"{path}: first CSV header column must be time")
        regions = [value.strip() for value in header[1:]]
        if not regions:
            raise ExportError(f"{path}: bandwidth CSV header contains no regions")
        for region_id in regions:
            if region_id not in valid_regions:
                raise ExportError(f"{path}: unknown region in header: {region_id}")

        raw_rows: list[tuple[int, Decimal, list[str]]] = []
        previous_time: Decimal | None = None
        for line_number, fields in enumerate(reader, start=2):
            if not fields or all(not field.strip() for field in fields):
                continue
            if len(fields) != len(header):
                raise ExportError(
                    f"{path}:{line_number}: row has {len(fields)} columns, expected {len(header)}"
                )
            time_value = parse_decimal(fields[0], "time", f"{path}:{line_number}")
            if time_value < 0:
                raise ExportError(f"{path}:{line_number}: time must be >= 0, got {time_value}")
            if previous_time is not None and time_value < previous_time:
                raise ExportError(f"{path}:{line_number}: time decreased within file")
            previous_time = time_value
            raw_rows.append((line_number, time_value, fields[1:]))

    if not raw_rows:
        raise ExportError(f"{path}: bandwidth CSV has no data rows")

    bucket_interval = infer_bucket_interval([row[1] for row in raw_rows], path)
    result: list[dict[str, str]] = []
    seen: set[tuple[str, str, str, str]] = set()
    terminal_bucket_count = 0
    max_vehicle_seconds = (
        Decimal(max_vehicle_update_time_ns) / Decimal(1_000_000_000)
        if max_vehicle_update_time_ns is not None
        else None
    )

    for line_number, measurement_time, values in raw_rows:
        bucket_start = measurement_time
        bucket_end = measurement_time + bucket_interval
        available_from = bucket_end
        if max_vehicle_seconds is not None and available_from > max_vehicle_seconds:
            terminal_bucket_count += len(regions)

        for region_id, raw_value in zip(regions, values):
            traffic = parse_decimal(raw_value, "trafficObservedBitsPerSecond", f"{path}:{line_number}")
            if traffic < 0:
                raise ExportError(f"{path}:{line_number}: trafficObservedBitsPerSecond must be >= 0")
            capacity = capacity_index.get((region_id, direction))
            if capacity is None:
                raise ExportError(f"{path}:{line_number}: no nominal capacity for {region_id}/{direction}")
            residual = capacity - traffic
            if residual < 0:
                residual = Decimal(0)
            key = (decimal_to_text(measurement_time), region_id, direction, path.name)
            if key in seen:
                raise ExportError(f"{path}:{line_number}: duplicate bandwidth record {key}")
            seen.add(key)
            result.append(
                {
                    "measurementTimeSeconds": decimal_to_text(measurement_time),
                    "availableFromTimeSeconds": decimal_to_text(available_from),
                    "bucketStartSeconds": decimal_to_text(bucket_start),
                    "bucketEndSeconds": decimal_to_text(bucket_end),
                    "regionId": region_id,
                    "direction": direction,
                    "trafficObservedBitsPerSecond": decimal_to_text(traffic),
                    "nominalCapacityBitsPerSecond": decimal_to_text(capacity),
                    "residualCapacityBitsPerSecond": decimal_to_text(residual),
                    "residualPolicy": RESIDUAL_POLICY,
                    "bucketBoundaryPolicy": BUCKET_BOUNDARY_POLICY,
                    "availableFromPolicy": AVAILABLE_FROM_POLICY,
                    "sourceFile": str(path),
                }
            )

    return result, terminal_bucket_count


def parse_bandwidth(
    directory: Path,
    valid_regions: set[str],
    capacity_index: dict[tuple[str, str], Decimal],
    max_vehicle_update_time_ns: int | None,
) -> tuple[list[dict[str, str]], int, int]:
    files = discover_bandwidth_files(directory)
    rows: list[dict[str, str]] = []
    terminal_bucket_count = 0
    seen: set[tuple[str, str, str, str]] = set()

    for path in files:
        file_rows, file_terminal_count = parse_bandwidth_file(
            path,
            valid_regions,
            capacity_index,
            max_vehicle_update_time_ns,
        )
        terminal_bucket_count += file_terminal_count
        for row in file_rows:
            key = (
                row["measurementTimeSeconds"],
                row["regionId"],
                row["direction"],
                Path(row["sourceFile"]).name,
            )
            if key in seen:
                raise ExportError(f"Duplicate Cell bandwidth record across files: {key}")
            seen.add(key)
            rows.append(row)

    rows.sort(
        key=lambda row: (
            Decimal(row["measurementTimeSeconds"]),
            row["regionId"],
            row["direction"],
            Path(row["sourceFile"]).name,
        )
    )
    return rows, len(files), terminal_bucket_count


def write_csv_safely(path: Path, columns: list[str], rows: list[dict[str, str]]) -> None:
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
            writer = csv.DictWriter(handle, fieldnames=columns, lineterminator="\n")
            writer.writeheader()
            for row in rows:
                writer.writerow(row)
        os.replace(temp_name, path)
    except Exception:
        if temp_name:
            try:
                os.unlink(temp_name)
            except OSError:
                pass
        raise


def write_json_safely(path: Path, payload: dict[str, Any]) -> None:
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
    except Exception:
        if temp_name:
            try:
                os.unlink(temp_name)
            except OSError:
                pass
        raise


def build_metadata(
    output_csv: Path,
    bandwidth_dir: Path,
    infrastructure_snapshot: Path,
    handover_rows: list[dict[str, str]],
    bandwidth_rows: list[dict[str, str]],
    terminal_buckets: int,
    warnings: list[str],
) -> dict[str, Any]:
    return {
        "sourceRun": output_csv.parent.name,
        "outputCsv": str(output_csv),
        "bandwidthMeasurementsDir": str(bandwidth_dir),
        "infrastructureSnapshot": str(infrastructure_snapshot),
        "unitStatus": UNIT_STATUS,
        "bucketBoundaryPolicy": BUCKET_BOUNDARY_POLICY,
        "availableFromPolicy": AVAILABLE_FROM_POLICY,
        "residualPolicy": RESIDUAL_POLICY,
        "handoverRecords": len(handover_rows),
        "bandwidthRecords": len(bandwidth_rows),
        "terminalBuckets": terminal_buckets,
        "warnings": sorted(warnings),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export integrated Cell handover and residual bandwidth streams."
    )
    parser.add_argument("--output-csv", required=True)
    parser.add_argument("--bandwidth-measurements-dir", required=True)
    parser.add_argument("--infrastructure-snapshot", required=True)
    parser.add_argument("--handover-out-file", required=True)
    parser.add_argument("--bandwidth-out-file", required=True)
    parser.add_argument("--metadata-out-file", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_csv = Path(args.output_csv)
    bandwidth_dir = Path(args.bandwidth_measurements_dir)
    infrastructure_snapshot = Path(args.infrastructure_snapshot)
    handover_out_file = Path(args.handover_out_file)
    bandwidth_out_file = Path(args.bandwidth_out_file)
    metadata_out_file = Path(args.metadata_out_file)

    try:
        infrastructure = load_json(infrastructure_snapshot, "infrastructure snapshot")
        valid_regions = known_regions(infrastructure)
        capacity_index = build_capacity_index(infrastructure)

        handover_rows, handover_counts, max_vehicle_update_time_ns = parse_output_csv(
            output_csv,
            valid_regions,
        )
        bandwidth_rows, bandwidth_file_count, terminal_buckets = parse_bandwidth(
            bandwidth_dir,
            valid_regions,
            capacity_index,
            max_vehicle_update_time_ns,
        )

        warnings: list[str] = [
            "Cell residual bandwidth is diagnostic and uses nominal capacity minus observed traffic.",
            "Cell bandwidth buckets use START_TIMESTAMP_FOR_INTERVAL and are safe only after bucket end.",
        ]
        if terminal_buckets:
            warnings.append(
                "Some Cell bandwidth buckets become available after the last observed vehicle state."
            )

        write_csv_safely(handover_out_file, HANDOVER_COLUMNS, handover_rows)
        write_csv_safely(bandwidth_out_file, BANDWIDTH_COLUMNS, bandwidth_rows)
        write_json_safely(
            metadata_out_file,
            build_metadata(
                output_csv,
                bandwidth_dir,
                infrastructure_snapshot,
                handover_rows,
                bandwidth_rows,
                terminal_buckets,
                warnings,
            ),
        )

        direction_counts = Counter(row["direction"] for row in bandwidth_rows)
        print("Cell network streams export completed")
        print(f"outputCsv={output_csv}")
        print(f"bandwidthMeasurementsDir={bandwidth_dir}")
        print(f"cellularHandoversFound={len(handover_rows)}")
        print(f"handoverRegistrations={handover_counts['REGISTRATION']}")
        print(f"handoverRegionTransitions={handover_counts['REGION_TRANSITION']}")
        print(f"handoverRemovals={handover_counts['REMOVAL']}")
        print("handoverDuplicates=0")
        print(f"bandwidthFilesAnalyzed={bandwidth_file_count}")
        print(f"bandwidthRecordsExported={len(bandwidth_rows)}")
        print("bandwidthDirections:")
        for direction in sorted(direction_counts):
            print(f"  {direction}={direction_counts[direction]}")
        print(f"unitStatus={UNIT_STATUS}")
        print(f"bandwidthResidualPolicy={RESIDUAL_POLICY}")
        print(f"bucketBoundaryPolicy={BUCKET_BOUNDARY_POLICY}")
        print(f"availableFromPolicy={AVAILABLE_FROM_POLICY}")
        print(f"terminalBuckets={terminal_buckets}")
        print(f"warningsCount={len(warnings)}")
        print("warnings:")
        for warning in sorted(warnings):
            print(f"  {warning}")
        print(f"handoverOutFile={handover_out_file}")
        print(f"bandwidthOutFile={bandwidth_out_file}")
        print(f"metadataOutFile={metadata_out_file}")
        return 0
    except ExportError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
