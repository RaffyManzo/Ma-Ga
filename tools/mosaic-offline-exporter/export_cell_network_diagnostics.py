#!/usr/bin/env python3
"""Export diagnostic Cell handover and raw bandwidth streams from MOSAIC logs."""

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


CELLULAR_HANDOVER = "CELLULAR_HANDOVER"
UNIT_STATUS_UNRESOLVED = "UNRESOLVED"
REQUIRED_WARNING = (
    "Cell diagnostic outputs are not aligned with the MaGaWorkloadStudy timeline "
    "and must not be used directly for final snapshot assembly."
)


class ExportError(Exception):
    """Raised when diagnostic Cell streams cannot be exported safely."""


@dataclass(frozen=True)
class HandoverRecord:
    time_ns: int
    vehicle_id: str
    previous_region: str
    current_region: str
    event_type: str
    source_file: str

    def to_csv_row(self) -> dict[str, str]:
        return {
            "timeNs": str(self.time_ns),
            "timeSeconds": format_time_seconds(self.time_ns),
            "vehicleId": self.vehicle_id,
            "previousRegion": self.previous_region,
            "currentRegion": self.current_region,
            "eventType": self.event_type,
            "sourceFile": self.source_file,
        }


@dataclass(frozen=True)
class BandwidthRawRecord:
    source_file: str
    direction: str
    time_raw: str
    time_value: Decimal
    region_id: str
    traffic_observed_raw: str

    def to_csv_row(self) -> dict[str, str]:
        return {
            "sourceFile": self.source_file,
            "direction": self.direction,
            "timeRaw": self.time_raw,
            "regionId": self.region_id,
            "trafficObservedRaw": self.traffic_observed_raw,
            "unitStatus": UNIT_STATUS_UNRESOLVED,
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export diagnostic MOSAIC Cell handovers and raw bandwidth measurements."
    )
    parser.add_argument("--handover-output-csv", required=True, help="MOSAIC output.csv with CELLULAR_HANDOVER rows.")
    parser.add_argument("--bandwidth-measurements-dir", required=True, help="MOSAIC bandwidthMeasurements directory.")
    parser.add_argument("--infrastructure-snapshot", required=True, help="Fase 10C infrastructure snapshot JSON.")
    parser.add_argument("--handover-out-file", required=True, help="CSV file for normalized handover diagnostics.")
    parser.add_argument("--bandwidth-raw-out-file", required=True, help="CSV file for raw bandwidth diagnostics.")
    parser.add_argument("--metadata-out-file", required=True, help="JSON file for diagnostic metadata.")
    return parser.parse_args()


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


def format_time_seconds(time_ns: int) -> str:
    seconds = Decimal(time_ns) / Decimal(1_000_000_000)
    return format_decimal_plain(seconds)


def format_decimal_plain(value: Decimal) -> str:
    text = format(value, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def parse_int(value: str, field_name: str, path: Path, line_number: int) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ExportError(f"{path}:{line_number}: {field_name} is not a valid integer") from exc
    if parsed < 0:
        raise ExportError(f"{path}:{line_number}: {field_name} must be >= 0")
    return parsed


def parse_non_negative_decimal(value: str, field_name: str, path: Path, line_number: int) -> Decimal:
    try:
        parsed = Decimal(value)
    except InvalidOperation as exc:
        raise ExportError(f"{path}:{line_number}: {field_name} is not numeric") from exc
    if not parsed.is_finite():
        raise ExportError(f"{path}:{line_number}: {field_name} must be finite")
    if parsed < 0:
        raise ExportError(f"{path}:{line_number}: {field_name} must be >= 0")
    return parsed


def normalize_nullable_region(value: str) -> str:
    stripped = value.strip()
    if stripped in {"", "null", "NULL", "None"}:
        return ""
    return stripped


def load_valid_regions(infrastructure_snapshot: Path) -> set[str]:
    infrastructure = load_json_file(infrastructure_snapshot, "infrastructure snapshot")
    cell = infrastructure.get("cell")
    if not isinstance(cell, dict):
        raise ExportError("infrastructure snapshot must contain cell object")
    regions = cell.get("regions")
    if not isinstance(regions, list):
        raise ExportError("infrastructure snapshot cell.regions must be a list")
    valid_regions: set[str] = {"globalNetwork"}
    for index, region in enumerate(regions):
        if not isinstance(region, dict):
            raise ExportError(f"infrastructure snapshot cell.regions[{index}] must be an object")
        region_id = str(region.get("regionId") or region.get("id") or "").strip()
        if not region_id:
            raise ExportError(f"infrastructure snapshot cell.regions[{index}] must contain regionId or id")
        valid_regions.add(region_id)
    return valid_regions


def classify_handover(previous_region: str, current_region: str, path: Path, line_number: int) -> str:
    previous_set = bool(previous_region)
    current_set = bool(current_region)
    if not previous_set and current_set:
        return "REGISTRATION"
    if previous_set and current_set and previous_region != current_region:
        return "REGION_TRANSITION"
    if previous_set and not current_set:
        return "REMOVAL"
    if not previous_set and not current_set:
        raise ExportError(f"{path}:{line_number}: previousRegion and currentRegion are both null")
    raise ExportError(f"{path}:{line_number}: previousRegion and currentRegion are identical")


def parse_handover_output(path: Path, valid_regions: set[str]) -> list[HandoverRecord]:
    require_file(path, "handover output CSV")
    records: list[HandoverRecord] = []
    seen: set[tuple[int, str, str, str]] = set()
    last_time_ns: int | None = None
    with path.open("r", encoding="utf-8", newline="") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.rstrip("\r\n")
            if not line:
                continue
            fields = line.split(";")
            if fields[0] != CELLULAR_HANDOVER:
                continue
            if len(fields) < 5:
                raise ExportError(f"{path}:{line_number}: CELLULAR_HANDOVER has fewer than 5 fields")
            time_ns = parse_int(fields[1].strip(), "timeNs", path, line_number)
            if last_time_ns is not None and time_ns < last_time_ns:
                raise ExportError(f"{path}:{line_number}: CELLULAR_HANDOVER time decreases during read")
            last_time_ns = time_ns
            vehicle_id = fields[2].strip()
            if not vehicle_id:
                raise ExportError(f"{path}:{line_number}: vehicleId is empty")
            previous_region = normalize_nullable_region(fields[3])
            current_region = normalize_nullable_region(fields[4])
            for field_name, region_id in (("previousRegion", previous_region), ("currentRegion", current_region)):
                if region_id and region_id not in valid_regions:
                    raise ExportError(f"{path}:{line_number}: {field_name} is not a known Cell region: {region_id}")
            event_type = classify_handover(previous_region, current_region, path, line_number)
            key = (time_ns, vehicle_id, previous_region, current_region)
            if key in seen:
                raise ExportError(f"{path}:{line_number}: duplicate CELLULAR_HANDOVER event")
            seen.add(key)
            records.append(
                HandoverRecord(
                    time_ns=time_ns,
                    vehicle_id=vehicle_id,
                    previous_region=previous_region,
                    current_region=current_region,
                    event_type=event_type,
                    source_file=str(path),
                )
            )
    if not records:
        raise ExportError(f"no CELLULAR_HANDOVER rows found in {path}")
    return sorted(records, key=lambda record: (record.time_ns, natural_key(record.vehicle_id), record.previous_region, record.current_region))


def detect_delimiter(header_line: str, path: Path) -> str:
    candidates = [",", ";", "\t"]
    counts = {delimiter: len(header_line.rstrip("\r\n").split(delimiter)) for delimiter in candidates}
    delimiter, count = max(counts.items(), key=lambda item: item[1])
    if count <= 1:
        raise ExportError(f"{path}: could not detect CSV delimiter")
    return delimiter


def normalize_direction(path: Path) -> str:
    name = path.name.lower()
    if re.search(r"(^|[#_.-])(up|uplink)([#_.-]|$)", name):
        return "UPLINK"
    if re.search(r"(^|[#_.-])(dn|down|downlink)([#_.-]|$)", name):
        return "DOWNLINK"
    raise ExportError(f"cannot resolve bandwidth direction from file name: {path.name}")


def parse_bandwidth_measurements(directory: Path, valid_regions: set[str]) -> tuple[list[BandwidthRawRecord], list[str]]:
    require_dir(directory, "bandwidthMeasurements directory")
    csv_files = sorted(directory.glob("*.csv"), key=lambda path: path.name)
    if not csv_files:
        raise ExportError(f"no CSV files found under {directory}")
    records: list[BandwidthRawRecord] = []
    analyzed_files: list[str] = []
    seen: set[tuple[str, str, str, str]] = set()
    for csv_path in csv_files:
        direction = normalize_direction(csv_path)
        analyzed_files.append(str(csv_path))
        try:
            with csv_path.open("r", encoding="utf-8", newline="") as handle:
                header_line = handle.readline()
                if not header_line:
                    raise ExportError(f"{csv_path}: missing CSV header")
                delimiter = detect_delimiter(header_line, csv_path)
                handle.seek(0)
                reader = csv.reader(handle, delimiter=delimiter)
                header = next(reader)
                header = [value.strip() for value in header]
                if not header or header[0].strip().lower() != "time":
                    raise ExportError(f"{csv_path}: first CSV column must be time")
                if len(header) < 2:
                    raise ExportError(f"{csv_path}: bandwidth CSV must contain at least one region column")
                regions = header[1:]
                for region_id in regions:
                    if not region_id:
                        raise ExportError(f"{csv_path}: region column name is empty")
                    if region_id not in valid_regions:
                        raise ExportError(f"{csv_path}: unknown region column: {region_id}")
                last_time: Decimal | None = None
                for line_number, row in enumerate(reader, start=2):
                    if not row or all(not cell.strip() for cell in row):
                        continue
                    if len(row) != len(header):
                        raise ExportError(f"{csv_path}:{line_number}: row has {len(row)} columns, expected {len(header)}")
                    time_raw = row[0].strip()
                    time_value = parse_non_negative_decimal(time_raw, "time", csv_path, line_number)
                    if last_time is not None and time_value < last_time:
                        raise ExportError(f"{csv_path}:{line_number}: time decreases within file")
                    last_time = time_value
                    for index, region_id in enumerate(regions, start=1):
                        traffic_raw = row[index].strip()
                        parse_non_negative_decimal(traffic_raw, f"trafficObservedRaw[{region_id}]", csv_path, line_number)
                        key = (csv_path.name, direction, format_decimal_plain(time_value), region_id)
                        if key in seen:
                            raise ExportError(f"{csv_path}:{line_number}: duplicate bandwidth raw record")
                        seen.add(key)
                        records.append(
                            BandwidthRawRecord(
                                source_file=str(csv_path),
                                direction=direction,
                                time_raw=time_raw,
                                time_value=time_value,
                                region_id=region_id,
                                traffic_observed_raw=traffic_raw,
                            )
                        )
        except OSError as exc:
            raise ExportError(f"{csv_path}: CSV is not readable") from exc
    if not records:
        raise ExportError(f"no bandwidth data rows found under {directory}")
    records.sort(key=lambda record: (record.source_file, record.direction, record.time_value, record.region_id))
    return records, analyzed_files


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
            json.dump(payload, handle, indent=2, sort_keys=False)
            handle.write("\n")
        os.replace(temp_name, path)
    finally:
        if temp_name is not None and os.path.exists(temp_name):
            os.unlink(temp_name)


def is_mixed_source(handover_output_csv: Path, bandwidth_measurements_dir: Path) -> bool:
    try:
        handover_log_dir = handover_output_csv.resolve().parent
        bandwidth_log_dir = bandwidth_measurements_dir.resolve().parent
        return handover_log_dir != bandwidth_log_dir
    except OSError:
        return str(handover_output_csv.parent) != str(bandwidth_measurements_dir.parent)


def bool_text(value: bool) -> str:
    return "true" if value else "false"


def build_metadata(
    handover_output_csv: Path,
    bandwidth_measurements_dir: Path,
    handover_records: list[HandoverRecord],
    bandwidth_records: list[BandwidthRawRecord],
    bandwidth_files: list[str],
    warnings: list[str],
    mixed_sources: bool,
) -> dict[str, Any]:
    event_counts = Counter(record.event_type for record in handover_records)
    direction_counts = Counter(record.direction for record in bandwidth_records)
    raw_records_by_file = Counter(record.source_file for record in bandwidth_records)
    return {
        "diagnosticOnly": True,
        "mixedSources": mixed_sources,
        "handoverSource": str(handover_output_csv),
        "bandwidthSource": str(bandwidth_measurements_dir),
        "handoverRecords": len(handover_records),
        "handoverEventTypes": dict(sorted(event_counts.items())),
        "bandwidthFilesAnalyzed": bandwidth_files,
        "bandwidthRawRecords": len(bandwidth_records),
        "bandwidthRawRecordsByFile": dict(sorted(raw_records_by_file.items())),
        "bandwidthDirections": dict(sorted(direction_counts.items())),
        "unitStatus": UNIT_STATUS_UNRESOLVED,
        "finalBandwidthStreamGenerated": False,
        "warnings": sorted(warnings),
    }


def run() -> None:
    args = parse_args()
    handover_output_csv = Path(args.handover_output_csv)
    bandwidth_measurements_dir = Path(args.bandwidth_measurements_dir)
    infrastructure_snapshot = Path(args.infrastructure_snapshot)
    handover_out_file = Path(args.handover_out_file)
    bandwidth_raw_out_file = Path(args.bandwidth_raw_out_file)
    metadata_out_file = Path(args.metadata_out_file)

    valid_regions = load_valid_regions(infrastructure_snapshot)
    handover_records = parse_handover_output(handover_output_csv, valid_regions)
    bandwidth_records, bandwidth_files = parse_bandwidth_measurements(bandwidth_measurements_dir, valid_regions)

    mixed_sources = is_mixed_source(handover_output_csv, bandwidth_measurements_dir)
    warnings = [
        REQUIRED_WARNING,
        "Cell bandwidth measurements are exported as raw values because the local workspace did not provide an unambiguous unit proof.",
    ]
    if mixed_sources:
        warnings.append("Cell diagnostic handover and bandwidth sources come from different MOSAIC log directories.")

    write_csv_atomic(
        handover_out_file,
        ["timeNs", "timeSeconds", "vehicleId", "previousRegion", "currentRegion", "eventType", "sourceFile"],
        [record.to_csv_row() for record in handover_records],
    )
    write_csv_atomic(
        bandwidth_raw_out_file,
        ["sourceFile", "direction", "timeRaw", "regionId", "trafficObservedRaw", "unitStatus"],
        [record.to_csv_row() for record in bandwidth_records],
    )
    metadata = build_metadata(
        handover_output_csv,
        bandwidth_measurements_dir,
        handover_records,
        bandwidth_records,
        bandwidth_files,
        warnings,
        mixed_sources,
    )
    write_json_atomic(metadata_out_file, metadata)

    event_counts = Counter(record.event_type for record in handover_records)
    print("Cell network diagnostic export completed")
    print(f"handoverSource={handover_output_csv}")
    print(f"bandwidthSource={bandwidth_measurements_dir}")
    print(f"mixedSources={bool_text(mixed_sources)}")
    print(f"handoverRecordsExported={len(handover_records)}")
    print(f"handoverRegistrations={event_counts.get('REGISTRATION', 0)}")
    print(f"handoverRegionTransitions={event_counts.get('REGION_TRANSITION', 0)}")
    print(f"handoverRemovals={event_counts.get('REMOVAL', 0)}")
    print(f"bandwidthFilesAnalyzed={len(bandwidth_files)}")
    print(f"bandwidthRawRecordsExported={len(bandwidth_records)}")
    print(f"unitStatus={UNIT_STATUS_UNRESOLVED}")
    print("finalBandwidthStreamGenerated=false")
    print(f"warningsCount={len(warnings)}")
    print(f"handoverOutFile={handover_out_file}")
    print(f"bandwidthRawOutFile={bandwidth_raw_out_file}")
    print(f"metadataOutFile={metadata_out_file}")


def main() -> int:
    try:
        run()
    except ExportError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
