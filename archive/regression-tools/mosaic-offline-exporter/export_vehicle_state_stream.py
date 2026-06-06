#!/usr/bin/env python3
"""Export normalized vehicle state observations from MOSAIC output.csv."""

from __future__ import annotations

import argparse
import csv
import os
import re
import sys
import tempfile
from collections import Counter
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path


VEHICLE_REGISTRATION = "VEHICLE_REGISTRATION"
VEHICLE_UPDATES = "VEHICLE_UPDATES"
NS_PER_SECOND = Decimal("1000000000")

CSV_COLUMNS = [
    "timeNs",
    "timeSeconds",
    "vehicleId",
    "latitude",
    "longitude",
    "projectedX",
    "projectedY",
    "speed",
    "heading",
    "active",
]


class ExportError(Exception):
    """Raised when the vehicle state stream cannot be exported safely."""


@dataclass(frozen=True)
class VehicleStateRecord:
    values: dict[str, str]
    time_ns: int
    vehicle_id: str

    def sort_key(self) -> tuple[int, tuple[str, int | str], str]:
        return (self.time_ns, natural_vehicle_key(self.vehicle_id), self.vehicle_id)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Normalize VEHICLE_UPDATES from MOSAIC output.csv into a vehicle state stream."
    )
    parser.add_argument(
        "--input-file",
        required=True,
        help="MOSAIC output.csv file to read.",
    )
    parser.add_argument(
        "--out-file",
        required=True,
        help="CSV file to write, for example data/mosaic-study/vehicle_state_stream.csv.",
    )
    return parser.parse_args()


def parse_int_field(value: str, field_name: str, file_path: Path, line_number: int) -> int:
    try:
        return int(value)
    except ValueError as exc:
        raise malformed(file_path, line_number, f"{field_name} is not a valid integer") from exc


def parse_decimal_field(value: str, field_name: str, file_path: Path, line_number: int) -> Decimal:
    try:
        parsed = Decimal(value)
    except InvalidOperation as exc:
        raise malformed(file_path, line_number, f"{field_name} is not numeric") from exc
    if not parsed.is_finite():
        raise malformed(file_path, line_number, f"{field_name} must be finite")
    return parsed


def parse_time_ns(value: str, file_path: Path, line_number: int) -> int:
    time_ns = parse_int_field(value, "timeNs", file_path, line_number)
    if time_ns < 0:
        raise malformed(file_path, line_number, "timeNs must be >= 0")
    return time_ns


def validate_vehicle_id(vehicle_id: str, file_path: Path, line_number: int) -> str:
    vehicle_id = vehicle_id.strip()
    if not vehicle_id:
        raise malformed(file_path, line_number, "vehicleId is empty")
    return vehicle_id


def format_time_seconds(time_ns: int) -> str:
    time_seconds = Decimal(time_ns) / NS_PER_SECOND
    text = format(time_seconds, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def malformed(file_path: Path, line_number: int, reason: str) -> ExportError:
    return ExportError(f"Malformed MOSAIC vehicle event at {file_path}:{line_number}: {reason}")


def natural_vehicle_key(vehicle_id: str) -> tuple[str, int | str]:
    match = re.match(r"^(.*?)(\d+)$", vehicle_id)
    if match:
        return (match.group(1), int(match.group(2)))
    return (vehicle_id, "")


def parse_registration(
    fields: list[str],
    file_path: Path,
    line_number: int,
    registered_vehicles: set[str],
) -> tuple[int, str]:
    if len(fields) < 3:
        raise malformed(file_path, line_number, "VEHICLE_REGISTRATION has fewer than 3 fields")

    time_ns = parse_time_ns(fields[1], file_path, line_number)
    vehicle_id = validate_vehicle_id(fields[2], file_path, line_number)

    if vehicle_id in registered_vehicles:
        raise malformed(file_path, line_number, f"vehicle registered more than once: {vehicle_id}")

    registered_vehicles.add(vehicle_id)
    return time_ns, vehicle_id


def parse_vehicle_update(
    fields: list[str],
    file_path: Path,
    line_number: int,
    registered_vehicles: set[str],
    seen_states: set[tuple[str, int]],
) -> VehicleStateRecord:
    if len(fields) < 8:
        raise malformed(file_path, line_number, "VEHICLE_UPDATES has fewer than 8 fields")

    time_ns = parse_time_ns(fields[1], file_path, line_number)
    vehicle_id = validate_vehicle_id(fields[2], file_path, line_number)

    if vehicle_id not in registered_vehicles:
        raise malformed(file_path, line_number, f"VEHICLE_UPDATES before registration: {vehicle_id}")

    state_key = (vehicle_id, time_ns)
    if state_key in seen_states:
        raise malformed(
            file_path,
            line_number,
            f"vehicle has more than one state at the same timeNs: {vehicle_id} @ {time_ns}",
        )
    seen_states.add(state_key)

    speed = parse_decimal_field(fields[3], "speed", file_path, line_number)
    heading = parse_decimal_field(fields[4], "heading", file_path, line_number)
    latitude = parse_decimal_field(fields[5], "latitude", file_path, line_number)
    longitude = parse_decimal_field(fields[6], "longitude", file_path, line_number)
    parse_decimal_field(fields[7], "altitude", file_path, line_number)

    if speed < Decimal("0"):
        raise malformed(file_path, line_number, "speed must be >= 0")
    if heading < Decimal("0"):
        raise malformed(file_path, line_number, "heading must be >= 0")
    if heading >= Decimal("360"):
        raise malformed(file_path, line_number, "heading must be < 360")
    if latitude < Decimal("-90") or latitude > Decimal("90"):
        raise malformed(file_path, line_number, "latitude must be between -90 and 90")
    if longitude < Decimal("-180") or longitude > Decimal("180"):
        raise malformed(file_path, line_number, "longitude must be between -180 and 180")

    return VehicleStateRecord(
        values={
            "timeNs": str(time_ns),
            "timeSeconds": format_time_seconds(time_ns),
            "vehicleId": vehicle_id,
            "latitude": fields[5].strip(),
            "longitude": fields[6].strip(),
            "projectedX": "",
            "projectedY": "",
            "speed": fields[3].strip(),
            "heading": fields[4].strip(),
            "active": "true",
        },
        time_ns=time_ns,
        vehicle_id=vehicle_id,
    )


def read_vehicle_states(input_file: Path) -> tuple[list[VehicleStateRecord], dict[str, object]]:
    if not input_file.exists():
        raise ExportError(f"Input file does not exist: {input_file}")
    if not input_file.is_file():
        raise ExportError(f"Input path is not a file: {input_file}")

    registered_vehicles: set[str] = set()
    updated_vehicles: set[str] = set()
    seen_states: set[tuple[str, int]] = set()
    records: list[VehicleStateRecord] = []
    field_count_distribution: Counter[int] = Counter()
    registrations_found = 0
    vehicle_updates_found = 0
    last_relevant_time_ns: int | None = None

    with input_file.open("r", encoding="utf-8", errors="replace", newline="") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.rstrip("\r\n")
            fields = line.split(";")
            marker = fields[0] if fields else ""

            if marker not in {VEHICLE_REGISTRATION, VEHICLE_UPDATES}:
                continue

            if marker == VEHICLE_REGISTRATION:
                time_ns, _vehicle_id = parse_registration(
                    fields, input_file, line_number, registered_vehicles
                )
                registrations_found += 1
            else:
                field_count_distribution[len(fields)] += 1
                record = parse_vehicle_update(
                    fields, input_file, line_number, registered_vehicles, seen_states
                )
                time_ns = record.time_ns
                vehicle_updates_found += 1
                updated_vehicles.add(record.vehicle_id)
                records.append(record)

            if last_relevant_time_ns is not None and time_ns < last_relevant_time_ns:
                raise malformed(
                    input_file,
                    line_number,
                    (
                        "time of relevant events decreases during file read "
                        f"({time_ns} < {last_relevant_time_ns})"
                    ),
                )
            last_relevant_time_ns = time_ns

    if registrations_found == 0:
        raise ExportError("No VEHICLE_REGISTRATION entries found")
    if vehicle_updates_found == 0:
        raise ExportError("No VEHICLE_UPDATES entries found")

    diagnostics: dict[str, object] = {
        "registrations_found": registrations_found,
        "vehicle_updates_found": vehicle_updates_found,
        "registered_vehicles": registered_vehicles,
        "updated_vehicles": updated_vehicles,
        "field_count_distribution": field_count_distribution,
    }
    return records, diagnostics


def write_csv_safely(records: list[VehicleStateRecord], out_file: Path) -> None:
    out_file.parent.mkdir(parents=True, exist_ok=True)
    temp_path: str | None = None

    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            newline="",
            dir=out_file.parent,
            prefix=f".{out_file.name}.",
            suffix=".tmp",
            delete=False,
        ) as temp_file:
            temp_path = temp_file.name
            writer = csv.DictWriter(temp_file, fieldnames=CSV_COLUMNS, lineterminator="\n")
            writer.writeheader()
            for record in records:
                writer.writerow(record.values)

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
    input_file: Path,
    out_file: Path,
    records: list[VehicleStateRecord],
    diagnostics: dict[str, object],
) -> None:
    field_count_distribution = diagnostics["field_count_distribution"]
    assert isinstance(field_count_distribution, Counter)
    registered_vehicles = diagnostics["registered_vehicles"]
    updated_vehicles = diagnostics["updated_vehicles"]
    assert isinstance(registered_vehicles, set)
    assert isinstance(updated_vehicles, set)
    state_times = [record.time_ns for record in records]

    print("Vehicle state stream export completed")
    print(f"inputFile={input_file}")
    print(f"registrationsFound={diagnostics['registrations_found']}")
    print(f"vehicleUpdatesFound={diagnostics['vehicle_updates_found']}")
    print(f"statesExported={len(records)}")
    print(f"registeredVehicles={len(registered_vehicles)}")
    print(f"updatedVehicles={len(updated_vehicles)}")
    print("duplicateRegistrations=0")
    print("duplicateVehicleStates=0")
    print("updatesBeforeRegistration=0")
    print("projectedCoordinatesPopulated=0")
    print(f"projectedCoordinatesMissing={len(records)}")
    print("vehicleUpdateFieldCountDistribution:")
    for field_count in sorted(field_count_distribution):
        print(f"  {field_count}={field_count_distribution[field_count]}")
    print(f"firstStateTimeNs={min(state_times)}")
    print(f"lastStateTimeNs={max(state_times)}")
    print(f"outFile={out_file}")


def main() -> int:
    args = parse_args()
    input_file = Path(args.input_file)
    out_file = Path(args.out_file)

    try:
        records, diagnostics = read_vehicle_states(input_file)
        sorted_records = sorted(records, key=VehicleStateRecord.sort_key)
        write_csv_safely(sorted_records, out_file)
        print_summary(
            input_file=input_file,
            out_file=out_file,
            records=sorted_records,
            diagnostics=diagnostics,
        )
        return 0
    except ExportError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
