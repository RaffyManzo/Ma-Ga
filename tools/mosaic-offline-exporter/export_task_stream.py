#!/usr/bin/env python3
"""Export diagnostic MA-GA workload activations from MOSAIC application logs."""

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
from typing import Iterable


TASK_MARKER = "TASK_ACTIVATION|"
SIM_TIME_SUFFIX_MARKER = " (at simulation time "
LOG_FILE_NAME = "MaGaWorkloadDiagnosticApp.log"
NS_PER_MS = 1_000_000

CSV_COLUMNS = [
    "taskId",
    "sourceVehicleId",
    "profileId",
    "activationTimeNs",
    "activationTimeMs",
    "inputSizeBits",
    "outputSizeBits",
    "cpuCycles",
    "deadlineSeconds",
]

INTEGER_COLUMNS = [
    "activationTimeNs",
    "activationTimeMs",
    "inputSizeBits",
    "outputSizeBits",
    "cpuCycles",
]


class ExportError(Exception):
    """Raised when the diagnostic task stream cannot be exported safely."""


@dataclass(frozen=True)
class TaskRecord:
    values: dict[str, str]
    activation_time_ns: int
    activation_time_ms: int

    def sort_key(self) -> tuple[int, str, str, str]:
        return (
            self.activation_time_ns,
            self.values["sourceVehicleId"],
            self.values["profileId"],
            self.values["taskId"],
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Aggregate TASK_ACTIVATION entries emitted by "
            "MaGaWorkloadDiagnosticApp into task_stream.csv."
        )
    )
    parser.add_argument(
        "--workload-log-root",
        required=True,
        help="Root folder containing apps/*/MaGaWorkloadDiagnosticApp.log.",
    )
    parser.add_argument(
        "--out-file",
        required=True,
        help="CSV file to write, for example data/mosaic-study/task_stream.csv.",
    )
    return parser.parse_args()


def find_log_files(workload_log_root: Path) -> list[Path]:
    if not workload_log_root.exists():
        raise ExportError(f"Input folder does not exist: {workload_log_root}")
    if not workload_log_root.is_dir():
        raise ExportError(f"Input path is not a folder: {workload_log_root}")

    log_files = sorted(workload_log_root.rglob(LOG_FILE_NAME))
    if not log_files:
        raise ExportError(
            f"No {LOG_FILE_NAME} files found under: {workload_log_root}"
        )
    return log_files


def extract_payload(line: str) -> str | None:
    index = line.find(TASK_MARKER)
    if index < 0:
        return None
    payload = line[index:].strip()
    suffix_index = payload.find(SIM_TIME_SUFFIX_MARKER)
    if suffix_index >= 0:
        payload = payload[:suffix_index].strip()
    return payload


def parse_key_value_payload(payload: str, file_path: Path, line_number: int) -> dict[str, str]:
    if not payload.startswith(TASK_MARKER):
        raise malformed(file_path, line_number, "payload does not start with TASK_ACTIVATION")

    parts = payload.split("|")
    if not parts or parts[0] != "TASK_ACTIVATION":
        raise malformed(file_path, line_number, "invalid TASK_ACTIVATION marker")

    fields: dict[str, str] = {}
    for part in parts[1:]:
        if not part:
            raise malformed(file_path, line_number, "empty field segment")
        if "=" not in part:
            raise malformed(file_path, line_number, f"field segment lacks '=': {part}")
        key, value = part.split("=", 1)
        if not key:
            raise malformed(file_path, line_number, "empty field name")
        if key in fields:
            raise malformed(file_path, line_number, f"field appears twice: {key}")
        fields[key] = value.strip()

    return fields


def parse_task_record(payload: str, file_path: Path, line_number: int) -> TaskRecord:
    fields = parse_key_value_payload(payload, file_path, line_number)
    expected_source_vehicle_id = file_path.parent.name

    for column in CSV_COLUMNS:
        if column not in fields:
            raise malformed(file_path, line_number, f"missing required field: {column}")

    for text_column in ["taskId", "sourceVehicleId", "profileId"]:
        if not fields[text_column]:
            raise malformed(file_path, line_number, f"{text_column} is empty")

    parsed_ints = {
        column: parse_int_field(fields[column], column, file_path, line_number)
        for column in INTEGER_COLUMNS
    }
    deadline_seconds = parse_decimal_field(
        fields["deadlineSeconds"], "deadlineSeconds", file_path, line_number
    )

    activation_time_ns = parsed_ints["activationTimeNs"]
    activation_time_ms = parsed_ints["activationTimeMs"]
    input_size_bits = parsed_ints["inputSizeBits"]
    output_size_bits = parsed_ints["outputSizeBits"]
    cpu_cycles = parsed_ints["cpuCycles"]

    if activation_time_ns < 0:
        raise malformed(file_path, line_number, "activationTimeNs must be >= 0")
    if activation_time_ms < 0:
        raise malformed(file_path, line_number, "activationTimeMs must be >= 0")
    if input_size_bits <= 0:
        raise malformed(file_path, line_number, "inputSizeBits must be > 0")
    if output_size_bits < 0:
        raise malformed(file_path, line_number, "outputSizeBits must be >= 0")
    if cpu_cycles <= 0:
        raise malformed(file_path, line_number, "cpuCycles must be > 0")
    if deadline_seconds <= Decimal("0"):
        raise malformed(file_path, line_number, "deadlineSeconds must be > 0")

    expected_activation_time_ns = activation_time_ms * NS_PER_MS
    if activation_time_ns != expected_activation_time_ns:
        raise malformed(
            file_path,
            line_number,
            (
                "activationTimeNs must equal activationTimeMs * 1_000_000 "
                f"({activation_time_ns} != {expected_activation_time_ns})"
            ),
        )

    if fields["sourceVehicleId"] != expected_source_vehicle_id:
        raise malformed(
            file_path,
            line_number,
            (
                "sourceVehicleId must match parent folder "
                f"({fields['sourceVehicleId']} != {expected_source_vehicle_id})"
            ),
        )

    expected_task_id = (
        f"{fields['profileId']}__{fields['sourceVehicleId']}__t_{activation_time_ms}"
    )
    if fields["taskId"] != expected_task_id:
        raise malformed(
            file_path,
            line_number,
            f"taskId does not match expected format ({fields['taskId']} != {expected_task_id})",
        )

    csv_values = {column: fields[column] for column in CSV_COLUMNS}
    return TaskRecord(
        values=csv_values,
        activation_time_ns=activation_time_ns,
        activation_time_ms=activation_time_ms,
    )


def parse_int_field(value: str, field_name: str, file_path: Path, line_number: int) -> int:
    try:
        return int(value)
    except ValueError as exc:
        raise malformed(file_path, line_number, f"{field_name} is not a valid integer") from exc


def parse_decimal_field(value: str, field_name: str, file_path: Path, line_number: int) -> Decimal:
    try:
        return Decimal(value)
    except InvalidOperation as exc:
        raise malformed(file_path, line_number, f"{field_name} is not a valid number") from exc


def malformed(file_path: Path, line_number: int, reason: str) -> ExportError:
    return ExportError(f"Malformed TASK_ACTIVATION at {file_path}:{line_number}: {reason}")


def read_task_records(log_files: Iterable[Path]) -> tuple[list[TaskRecord], int]:
    records: list[TaskRecord] = []
    task_activation_found = 0

    for log_file in log_files:
        with log_file.open("r", encoding="utf-8", errors="replace") as handle:
            for line_number, line in enumerate(handle, start=1):
                payload = extract_payload(line)
                if payload is None:
                    continue
                task_activation_found += 1
                records.append(parse_task_record(payload, log_file, line_number))

    if task_activation_found == 0:
        raise ExportError("No TASK_ACTIVATION entries found in workload logs")

    task_id_counts = Counter(record.values["taskId"] for record in records)
    duplicate_task_ids = sorted(
        task_id for task_id, count in task_id_counts.items() if count > 1
    )
    if duplicate_task_ids:
        preview = ", ".join(duplicate_task_ids[:10])
        suffix = "" if len(duplicate_task_ids) <= 10 else ", ..."
        raise ExportError(
            f"Duplicate taskId values found: {len(duplicate_task_ids)} ({preview}{suffix})"
        )

    return records, task_activation_found


def write_csv_safely(records: list[TaskRecord], out_file: Path) -> None:
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


def natural_vehicle_key(vehicle_id: str) -> tuple[str, int | str]:
    match = re.match(r"^(.*?)(\d+)$", vehicle_id)
    if match:
        return (match.group(1), int(match.group(2)))
    return (vehicle_id, "")


def print_summary(
    *,
    files_analyzed: int,
    task_activation_found: int,
    records: list[TaskRecord],
    out_file: Path,
) -> None:
    profile_counts = Counter(record.values["profileId"] for record in records)
    source_counts = Counter(record.values["sourceVehicleId"] for record in records)
    activation_times = [record.activation_time_ns for record in records]

    print("Task stream export completed")
    print(f"filesAnalyzed={files_analyzed}")
    print(f"taskActivationFound={task_activation_found}")
    print(f"tasksExported={len(records)}")
    print("duplicates=0")
    print("profileDistribution:")
    for profile_id in sorted(profile_counts):
        print(f"  {profile_id}={profile_counts[profile_id]}")
    print("sourceVehicleDistribution:")
    for source_vehicle_id in sorted(source_counts, key=natural_vehicle_key):
        print(f"  {source_vehicle_id}={source_counts[source_vehicle_id]}")
    print(f"firstActivationTimeNs={min(activation_times)}")
    print(f"lastActivationTimeNs={max(activation_times)}")
    print(f"outFile={out_file}")


def main() -> int:
    args = parse_args()
    workload_log_root = Path(args.workload_log_root)
    out_file = Path(args.out_file)

    try:
        log_files = find_log_files(workload_log_root)
        records, task_activation_found = read_task_records(log_files)
        sorted_records = sorted(records, key=TaskRecord.sort_key)
        write_csv_safely(sorted_records, out_file)
        print_summary(
            files_analyzed=len(log_files),
            task_activation_found=task_activation_found,
            records=sorted_records,
            out_file=out_file,
        )
        return 0
    except ExportError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
