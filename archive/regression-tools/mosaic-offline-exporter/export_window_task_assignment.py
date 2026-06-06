#!/usr/bin/env python3
"""Assign MOSAIC workload tasks to explicit MA-GA diagnostic optimization windows."""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import tempfile
from collections import Counter
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


NS_PER_SECOND = Decimal("1000000000")
NS_PER_MS = 1_000_000
TIMELINE_POLICY = "FIXED_INTERVAL_DIAGNOSTIC"
TASK_ASSIGNMENT_POLICY = "PENDING_TASKS_AT_NEXT_OPTIMIZATION_WINDOW"
CONSUMPTION_POLICY = "REMOVE_AFTER_EXPORT_TO_WINDOW"
BOUNDARY_POLICY = "INITIAL_INTERVAL_CLOSED_THEN_LEFT_OPEN_RIGHT_CLOSED"

TASK_COLUMNS = [
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

TIMELINE_COLUMNS = [
    "windowIndex",
    "previousWindowTimeNs",
    "previousWindowTimeSeconds",
    "windowTimeNs",
    "windowTimeSeconds",
    "intervalStartPolicy",
    "intervalEndPolicy",
    "timelinePolicy",
]

ASSIGNMENT_COLUMNS = [
    "windowIndex",
    "previousWindowTimeNs",
    "previousWindowTimeSeconds",
    "windowTimeNs",
    "windowTimeSeconds",
    "assignmentDelayNs",
    "assignmentDelaySeconds",
    "taskId",
    "sourceVehicleId",
    "profileId",
    "activationTimeNs",
    "activationTimeMs",
    "inputSizeBits",
    "outputSizeBits",
    "cpuCycles",
    "deadlineSeconds",
    "taskAssignmentPolicy",
    "consumptionPolicy",
]


class AssignmentExportError(Exception):
    """Raised when task-to-window assignment cannot be exported safely."""


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


@dataclass(frozen=True)
class WindowRecord:
    window_index: int
    previous_window_time_ns: int
    window_time_ns: int
    interval_start_policy: str
    interval_end_policy: str
    timeline_policy: str

    @property
    def previous_window_time_seconds(self) -> str:
        return format_seconds_from_ns(self.previous_window_time_ns)

    @property
    def window_time_seconds(self) -> str:
        return format_seconds_from_ns(self.window_time_ns)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Assign task_stream.csv entries to explicit diagnostic optimization windows."
    )
    parser.add_argument("--task-stream-file", required=True)
    parser.add_argument("--timeline-file", required=True)
    parser.add_argument("--baseline-metadata-file", required=True)
    parser.add_argument("--expected-source-run", required=True)
    parser.add_argument("--output-file", required=True)
    parser.add_argument("--validation-out-file", required=True)
    return parser.parse_args()


def parse_int(value: str, field_name: str, source: Path) -> int:
    try:
        return int(value)
    except ValueError as exc:
        raise AssignmentExportError(f"{field_name} is not a valid integer in {source}: {value}") from exc


def parse_decimal(value: str, field_name: str, source: Path) -> Decimal:
    try:
        parsed = Decimal(value)
    except InvalidOperation as exc:
        raise AssignmentExportError(f"{field_name} is not a valid decimal in {source}: {value}") from exc
    if not parsed.is_finite():
        raise AssignmentExportError(f"{field_name} must be finite in {source}: {value}")
    return parsed


def seconds_text_to_ns(value: str, field_name: str, source: Path) -> int:
    seconds = parse_decimal(value, field_name, source)
    scaled = seconds * NS_PER_SECOND
    if scaled != scaled.to_integral_value():
        raise AssignmentExportError(f"{field_name} must be expressible as integer nanoseconds in {source}: {value}")
    return int(scaled)


def format_decimal_plain(value: Decimal) -> str:
    if value == value.to_integral_value():
        return str(int(value))
    return format(value.normalize(), "f")


def format_seconds_from_ns(value_ns: int) -> str:
    return format_decimal_plain(Decimal(value_ns) / NS_PER_SECOND)


def decimal_json_value(value: Decimal) -> int | float:
    if value == value.to_integral_value():
        return int(value)
    return float(value)


def require_columns(fieldnames: list[str] | None, required: list[str], source: Path) -> None:
    if fieldnames is None:
        raise AssignmentExportError(f"CSV has no header: {source}")
    missing = [column for column in required if column not in fieldnames]
    if missing:
        raise AssignmentExportError(f"CSV {source} is missing required columns: {', '.join(missing)}")


def read_tasks(path: Path) -> tuple[list[TaskRecord], int, int]:
    if not path.exists():
        raise AssignmentExportError(f"task stream file does not exist: {path}")
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        require_columns(reader.fieldnames, TASK_COLUMNS, path)
        rows = list(reader)

    tasks: list[TaskRecord] = []
    negative_activation_times = 0
    activation_coherence_errors = 0
    for line_number, row in enumerate(rows, start=2):
        for column in TASK_COLUMNS:
            if row.get(column, "") == "":
                raise AssignmentExportError(f"missing {column} in {path} at CSV line {line_number}")
        activation_time_ns = parse_int(row["activationTimeNs"], "activationTimeNs", path)
        activation_time_ms = parse_int(row["activationTimeMs"], "activationTimeMs", path)
        if activation_time_ns < 0:
            negative_activation_times += 1
        if activation_time_ns != activation_time_ms * NS_PER_MS:
            activation_coherence_errors += 1
        parse_int(row["inputSizeBits"], "inputSizeBits", path)
        parse_int(row["outputSizeBits"], "outputSizeBits", path)
        parse_int(row["cpuCycles"], "cpuCycles", path)
        parse_decimal(row["deadlineSeconds"], "deadlineSeconds", path)
        tasks.append(
            TaskRecord(
                values={column: row[column] for column in TASK_COLUMNS},
                activation_time_ns=activation_time_ns,
                activation_time_ms=activation_time_ms,
            )
        )
    tasks.sort(key=lambda item: item.sort_key())
    return tasks, negative_activation_times, activation_coherence_errors


def read_timeline(path: Path) -> list[WindowRecord]:
    if not path.exists():
        raise AssignmentExportError(f"timeline file does not exist: {path}")
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        require_columns(reader.fieldnames, TIMELINE_COLUMNS, path)
        rows = list(reader)
    if not rows:
        raise AssignmentExportError(f"timeline file contains no windows: {path}")

    windows: list[WindowRecord] = []
    for line_number, row in enumerate(rows, start=2):
        window_index = parse_int(row["windowIndex"], "windowIndex", path)
        previous_ns = parse_int(row["previousWindowTimeNs"], "previousWindowTimeNs", path)
        window_ns = parse_int(row["windowTimeNs"], "windowTimeNs", path)
        previous_seconds_ns = seconds_text_to_ns(row["previousWindowTimeSeconds"], "previousWindowTimeSeconds", path)
        window_seconds_ns = seconds_text_to_ns(row["windowTimeSeconds"], "windowTimeSeconds", path)
        if previous_seconds_ns != previous_ns:
            raise AssignmentExportError(f"previousWindowTimeSeconds does not match previousWindowTimeNs at line {line_number}")
        if window_seconds_ns != window_ns:
            raise AssignmentExportError(f"windowTimeSeconds does not match windowTimeNs at line {line_number}")
        windows.append(
            WindowRecord(
                window_index=window_index,
                previous_window_time_ns=previous_ns,
                window_time_ns=window_ns,
                interval_start_policy=row["intervalStartPolicy"],
                interval_end_policy=row["intervalEndPolicy"],
                timeline_policy=row["timelinePolicy"],
            )
        )
    validate_timeline(windows)
    return windows


def validate_timeline(windows: list[WindowRecord]) -> None:
    seen_window_times: set[int] = set()
    previous_window_time: int | None = None
    expected_index = 1
    interval_ns: int | None = None
    previous_row_window_ns: int | None = None
    for window in windows:
        if window.window_index != expected_index:
            raise AssignmentExportError(
                f"timeline windowIndex must be sequential starting at 1 ({window.window_index} != {expected_index})"
            )
        expected_start_policy = "INCLUSIVE" if window.window_index == 1 else "EXCLUSIVE"
        if window.interval_start_policy != expected_start_policy:
            raise AssignmentExportError(
                f"window {window.window_index} has intervalStartPolicy={window.interval_start_policy}, expected {expected_start_policy}"
            )
        if window.interval_end_policy != "INCLUSIVE":
            raise AssignmentExportError(f"window {window.window_index} must have intervalEndPolicy=INCLUSIVE")
        if window.timeline_policy != TIMELINE_POLICY:
            raise AssignmentExportError(f"window {window.window_index} has unsupported timelinePolicy={window.timeline_policy}")
        if window.previous_window_time_ns >= window.window_time_ns:
            raise AssignmentExportError(f"window {window.window_index} must have previousWindowTimeNs < windowTimeNs")
        if window.window_time_ns in seen_window_times:
            raise AssignmentExportError(f"duplicate windowTimeNs in timeline: {window.window_time_ns}")
        if previous_window_time is not None and window.window_time_ns <= previous_window_time:
            raise AssignmentExportError("timeline windowTimeNs values must be strictly increasing")
        if previous_row_window_ns is not None and window.previous_window_time_ns != previous_row_window_ns:
            raise AssignmentExportError(
                f"window {window.window_index} previousWindowTimeNs must equal prior windowTimeNs"
            )
        current_interval_ns = window.window_time_ns - window.previous_window_time_ns
        if interval_ns is None:
            interval_ns = current_interval_ns
        elif current_interval_ns != interval_ns:
            raise AssignmentExportError("all fixed diagnostic windows must have the same interval")
        seen_window_times.add(window.window_time_ns)
        previous_window_time = window.window_time_ns
        previous_row_window_ns = window.window_time_ns
        expected_index += 1


def read_metadata(path: Path, expected_source_run: str) -> dict[str, Any]:
    if not path.exists():
        raise AssignmentExportError(f"baseline metadata file does not exist: {path}")
    with path.open(encoding="utf-8") as handle:
        payload = json.load(handle)
    source_run = payload.get("sourceRun")
    if not source_run:
        raise AssignmentExportError(f"baseline metadata does not contain sourceRun: {path}")
    if source_run != expected_source_run:
        raise AssignmentExportError(
            f"baseline metadata sourceRun mismatch: expected {expected_source_run}, found {source_run}"
        )
    return payload


def first_valid_window(task: TaskRecord, windows: list[WindowRecord]) -> WindowRecord | None:
    for window in windows:
        start_ok = (
            task.activation_time_ns >= window.previous_window_time_ns
            if window.interval_start_policy == "INCLUSIVE"
            else task.activation_time_ns > window.previous_window_time_ns
        )
        end_ok = task.activation_time_ns <= window.window_time_ns
        if start_ok and end_ok:
            return window
    return None


def build_assignment_row(task: TaskRecord, window: WindowRecord) -> dict[str, str]:
    delay_ns = window.window_time_ns - task.activation_time_ns
    row = {
        "windowIndex": str(window.window_index),
        "previousWindowTimeNs": str(window.previous_window_time_ns),
        "previousWindowTimeSeconds": window.previous_window_time_seconds,
        "windowTimeNs": str(window.window_time_ns),
        "windowTimeSeconds": window.window_time_seconds,
        "assignmentDelayNs": str(delay_ns),
        "assignmentDelaySeconds": format_seconds_from_ns(delay_ns),
    }
    row.update(task.values)
    row["taskAssignmentPolicy"] = TASK_ASSIGNMENT_POLICY
    row["consumptionPolicy"] = CONSUMPTION_POLICY
    return row


def assign_tasks(tasks: list[TaskRecord], windows: list[WindowRecord]) -> tuple[list[dict[str, str]], dict[str, Any]]:
    assigned_rows: list[dict[str, str]] = []
    counters: Counter[str] = Counter()
    errors: list[str] = []
    simulation_start_ns = windows[0].previous_window_time_ns
    simulation_end_ns = windows[-1].window_time_ns
    boundary_times = {simulation_start_ns, *(window.window_time_ns for window in windows)}

    for task in tasks:
        if task.activation_time_ns < 0:
            counters["negativeActivationTimes"] += 1
            continue
        if task.activation_time_ns > simulation_end_ns:
            counters["tasksAfterSimulationEnd"] += 1
            continue
        if task.activation_time_ns == simulation_start_ns:
            counters["tasksAtZero"] += 1
        if task.activation_time_ns in boundary_times:
            counters["tasksAtExactBoundary"] += 1
        window = first_valid_window(task, windows)
        if window is None:
            counters["tasksLost"] += 1
            errors.append(f"Task could not be assigned to any window: {task.values['taskId']}")
            continue
        if window.window_time_ns < task.activation_time_ns:
            counters["tasksAssignedBeforeActivation"] += 1
        expected_window = first_valid_window(task, windows)
        if expected_window is None or expected_window.window_index != window.window_index:
            counters["tasksAssignedToNonEarliestWindow"] += 1
        assigned_rows.append(build_assignment_row(task, window))

    assigned_rows.sort(
        key=lambda row: (
            int(row["windowIndex"]),
            int(row["activationTimeNs"]),
            row["sourceVehicleId"],
            row["profileId"],
            row["taskId"],
        )
    )

    assigned_ids = [row["taskId"] for row in assigned_rows]
    duplicate_assignments = sum(count - 1 for count in Counter(assigned_ids).values() if count > 1)
    counters["duplicateAssignments"] = duplicate_assignments
    counters["tasksLost"] += len(tasks) - len(assigned_rows) - counters["negativeActivationTimes"] - counters["tasksAfterSimulationEnd"]
    if counters["tasksLost"] < 0:
        counters["tasksLost"] = len(tasks) - len(assigned_rows)

    return assigned_rows, {"counters": counters, "errors": errors}


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
            writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="raise", lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)
        os.replace(temp_name, path)
        temp_name = None
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
            json.dump(payload, handle, indent=2, sort_keys=True)
            handle.write("\n")
        os.replace(temp_name, path)
        temp_name = None
    finally:
        if temp_name is not None and os.path.exists(temp_name):
            os.unlink(temp_name)


def build_validation(
    *,
    source_run: str,
    tasks: list[TaskRecord],
    windows: list[WindowRecord],
    assigned_rows: list[dict[str, str]],
    duplicate_task_ids: int,
    activation_coherence_errors: int,
    assignment_diagnostics: dict[str, Any],
) -> dict[str, Any]:
    counters: Counter[str] = assignment_diagnostics["counters"]
    errors: list[str] = list(assignment_diagnostics["errors"])
    if activation_coherence_errors:
        errors.append(f"activationTimeNs coherence violations: {activation_coherence_errors}")
    if duplicate_task_ids:
        errors.append(f"duplicate taskIds in input: {duplicate_task_ids}")

    tasks_per_window_counter = Counter(int(row["windowIndex"]) for row in assigned_rows)
    tasks_per_window = [
        {
            "windowIndex": window.window_index,
            "windowTimeSeconds": window.window_time_seconds,
            "tasksAssigned": tasks_per_window_counter.get(window.window_index, 0),
        }
        for window in windows
    ]
    empty_window_details = [
        item for item in tasks_per_window
        if item["tasksAssigned"] == 0
    ]

    delays = [int(row["assignmentDelayNs"]) for row in assigned_rows]
    minimum_delay = min(delays) if delays else None
    maximum_delay = max(delays) if delays else None
    average_delay = (Decimal(sum(delays)) / Decimal(len(delays))) if delays else None

    task_ids = [task.values["taskId"] for task in tasks]
    assigned_ids = [row["taskId"] for row in assigned_rows]
    unique_task_ids_read = len(set(task_ids))
    unique_task_ids_assigned = len(set(assigned_ids))
    tasks_lost = len(tasks) - len(assigned_rows)

    hard_errors = []
    if not tasks:
        hard_errors.append("No tasks were read from task_stream.csv.")
    if len(assigned_rows) != len(tasks):
        hard_errors.append("Not every task was assigned exactly once.")
    if unique_task_ids_assigned != unique_task_ids_read:
        hard_errors.append("Unique task ids assigned do not match unique task ids read.")
    if tasks_lost:
        hard_errors.append(f"Tasks lost: {tasks_lost}")
    if counters.get("tasksAssignedBeforeActivation", 0):
        hard_errors.append("Some tasks were assigned before activation.")
    if counters.get("tasksAssignedToNonEarliestWindow", 0):
        hard_errors.append("Some tasks were assigned to a non-earliest window.")
    if counters.get("tasksAfterSimulationEnd", 0):
        hard_errors.append("Some tasks are after the simulation end.")
    if counters.get("negativeActivationTimes", 0):
        hard_errors.append("Some tasks have negative activation times.")
    if counters.get("duplicateAssignments", 0):
        hard_errors.append("Duplicate assignments were generated.")
    errors.extend(hard_errors)

    phase_completed = (
        not errors
        and len(windows) > 0
        and len(tasks) > 0
        and len(assigned_rows) == len(tasks)
        and unique_task_ids_assigned == unique_task_ids_read
        and duplicate_task_ids == 0
        and counters.get("duplicateAssignments", 0) == 0
        and tasks_lost == 0
        and counters.get("tasksAssignedBeforeActivation", 0) == 0
        and counters.get("tasksAssignedToNonEarliestWindow", 0) == 0
        and counters.get("tasksAfterSimulationEnd", 0) == 0
        and counters.get("negativeActivationTimes", 0) == 0
        and activation_coherence_errors == 0
    )

    simulation_start_ns = windows[0].previous_window_time_ns
    simulation_end_ns = windows[-1].window_time_ns
    window_interval_ns = windows[0].window_time_ns - windows[0].previous_window_time_ns

    return {
        "sourceRun": source_run,
        "timelinePolicy": TIMELINE_POLICY,
        "taskAssignmentPolicy": TASK_ASSIGNMENT_POLICY,
        "consumptionPolicy": CONSUMPTION_POLICY,
        "boundaryPolicy": BOUNDARY_POLICY,
        "simulationStartSeconds": decimal_json_value(Decimal(simulation_start_ns) / NS_PER_SECOND),
        "simulationEndSeconds": decimal_json_value(Decimal(simulation_end_ns) / NS_PER_SECOND),
        "windowIntervalSeconds": decimal_json_value(Decimal(window_interval_ns) / NS_PER_SECOND),
        "windowsGenerated": len(windows),
        "tasksRead": len(tasks),
        "tasksAssigned": len(assigned_rows),
        "uniqueTaskIdsRead": unique_task_ids_read,
        "uniqueTaskIdsAssigned": unique_task_ids_assigned,
        "duplicateTaskIdsInInput": duplicate_task_ids,
        "duplicateAssignments": counters.get("duplicateAssignments", 0),
        "tasksLost": tasks_lost,
        "tasksAssignedBeforeActivation": counters.get("tasksAssignedBeforeActivation", 0),
        "tasksAssignedToNonEarliestWindow": counters.get("tasksAssignedToNonEarliestWindow", 0),
        "tasksAtExactBoundary": counters.get("tasksAtExactBoundary", 0),
        "tasksAtZero": counters.get("tasksAtZero", 0),
        "tasksAfterSimulationEnd": counters.get("tasksAfterSimulationEnd", 0),
        "negativeActivationTimes": counters.get("negativeActivationTimes", 0),
        "emptyWindows": len(empty_window_details),
        "emptyWindowDetails": empty_window_details,
        "firstTaskActivationTimeNs": min((task.activation_time_ns for task in tasks), default=None),
        "lastTaskActivationTimeNs": max((task.activation_time_ns for task in tasks), default=None),
        "minimumAssignmentDelayNs": minimum_delay,
        "maximumAssignmentDelayNs": maximum_delay,
        "averageAssignmentDelayNs": decimal_json_value(average_delay) if average_delay is not None else None,
        "tasksPerWindow": tasks_per_window,
        "activationTimeCoherenceViolations": activation_coherence_errors,
        "warnings": [],
        "errors": errors,
        "phase10hStatus": "COMPLETED" if phase_completed else "INCOMPLETE",
        "readyForPhase10I": phase_completed,
        "calibrationStatus": "TO_BE_REPLACED_OR_DRIVEN_BY_TEMPORAL_WINDOW_MANAGER",
    }


def run() -> None:
    args = parse_args()
    task_stream_file = Path(args.task_stream_file)
    timeline_file = Path(args.timeline_file)
    baseline_metadata_file = Path(args.baseline_metadata_file)
    output_file = Path(args.output_file)
    validation_out_file = Path(args.validation_out_file)

    metadata = read_metadata(baseline_metadata_file, args.expected_source_run)
    source_run = metadata["sourceRun"]
    tasks, _negative_activation_times, activation_coherence_errors = read_tasks(task_stream_file)
    windows = read_timeline(timeline_file)
    duplicate_task_ids = sum(count - 1 for count in Counter(task.values["taskId"] for task in tasks).values() if count > 1)
    assigned_rows, assignment_diagnostics = assign_tasks(tasks, windows)
    validation = build_validation(
        source_run=source_run,
        tasks=tasks,
        windows=windows,
        assigned_rows=assigned_rows,
        duplicate_task_ids=duplicate_task_ids,
        activation_coherence_errors=activation_coherence_errors,
        assignment_diagnostics=assignment_diagnostics,
    )

    write_csv_atomic(output_file, ASSIGNMENT_COLUMNS, assigned_rows)
    write_json_atomic(validation_out_file, validation)

    print("Phase 10H task-to-window assignment completed")
    print(f"sourceRun={source_run}")
    print(f"taskAssignmentPolicy={TASK_ASSIGNMENT_POLICY}")
    print(f"consumptionPolicy={CONSUMPTION_POLICY}")
    print(f"tasksRead={validation['tasksRead']}")
    print(f"tasksAssigned={validation['tasksAssigned']}")
    print(f"duplicateTaskIdsInInput={validation['duplicateTaskIdsInInput']}")
    print(f"duplicateAssignments={validation['duplicateAssignments']}")
    print(f"tasksLost={validation['tasksLost']}")
    print(f"tasksAssignedBeforeActivation={validation['tasksAssignedBeforeActivation']}")
    print(f"tasksAssignedToNonEarliestWindow={validation['tasksAssignedToNonEarliestWindow']}")
    print(f"tasksAtExactBoundary={validation['tasksAtExactBoundary']}")
    print(f"tasksAfterSimulationEnd={validation['tasksAfterSimulationEnd']}")
    print(f"negativeActivationTimes={validation['negativeActivationTimes']}")
    print(f"windowsGenerated={validation['windowsGenerated']}")
    print(f"emptyWindows={validation['emptyWindows']}")
    print(f"minimumAssignmentDelayNs={validation['minimumAssignmentDelayNs']}")
    print(f"maximumAssignmentDelayNs={validation['maximumAssignmentDelayNs']}")
    print(f"averageAssignmentDelayNs={validation['averageAssignmentDelayNs']}")
    print(f"phase10hStatus={validation['phase10hStatus']}")
    print(f"readyForPhase10I={str(validation['readyForPhase10I']).lower()}")
    print(f"warningsCount={len(validation['warnings'])}")
    for warning in validation["warnings"]:
        print(f"  warning={warning}")
    print(f"errorsCount={len(validation['errors'])}")
    for error in validation["errors"]:
        print(f"  error={error}")
    print(f"outputFile={output_file}")
    print(f"validationOutFile={validation_out_file}")


def main() -> int:
    try:
        run()
    except AssignmentExportError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
