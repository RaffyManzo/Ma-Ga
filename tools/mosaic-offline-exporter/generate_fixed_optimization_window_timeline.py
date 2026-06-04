#!/usr/bin/env python3
"""Generate an explicit fixed diagnostic optimization-window timeline."""

from __future__ import annotations

import argparse
import csv
import os
import sys
import tempfile
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path


NS_PER_SECOND = Decimal("1000000000")
TIMELINE_POLICY = "FIXED_INTERVAL_DIAGNOSTIC"

CSV_COLUMNS = [
    "windowIndex",
    "previousWindowTimeNs",
    "previousWindowTimeSeconds",
    "windowTimeNs",
    "windowTimeSeconds",
    "intervalStartPolicy",
    "intervalEndPolicy",
    "timelinePolicy",
]


class TimelineGenerationError(Exception):
    """Raised when the requested timeline cannot be generated safely."""


@dataclass(frozen=True)
class WindowRow:
    window_index: int
    previous_window_time_ns: int
    window_time_ns: int

    def to_csv_row(self) -> dict[str, str]:
        return {
            "windowIndex": str(self.window_index),
            "previousWindowTimeNs": str(self.previous_window_time_ns),
            "previousWindowTimeSeconds": format_seconds_from_ns(self.previous_window_time_ns),
            "windowTimeNs": str(self.window_time_ns),
            "windowTimeSeconds": format_seconds_from_ns(self.window_time_ns),
            "intervalStartPolicy": "INCLUSIVE" if self.window_index == 1 else "EXCLUSIVE",
            "intervalEndPolicy": "INCLUSIVE",
            "timelinePolicy": TIMELINE_POLICY,
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate a fixed diagnostic MA-GA optimization-window timeline CSV."
    )
    parser.add_argument("--simulation-start-seconds", required=True)
    parser.add_argument("--simulation-end-seconds", required=True)
    parser.add_argument("--window-interval-seconds", required=True)
    parser.add_argument("--output-file", required=True)
    return parser.parse_args()


def parse_seconds_to_ns(value: str, field_name: str) -> int:
    try:
        seconds = Decimal(value)
    except InvalidOperation as exc:
        raise TimelineGenerationError(f"{field_name} is not a valid decimal second value: {value}") from exc
    if not seconds.is_finite():
        raise TimelineGenerationError(f"{field_name} must be finite: {value}")
    scaled = seconds * NS_PER_SECOND
    if scaled != scaled.to_integral_value():
        raise TimelineGenerationError(f"{field_name} must be expressible as integer nanoseconds: {value}")
    return int(scaled)


def format_decimal_plain(value: Decimal) -> str:
    if value == value.to_integral_value():
        return str(int(value))
    return format(value.normalize(), "f")


def format_seconds_from_ns(value_ns: int) -> str:
    return format_decimal_plain(Decimal(value_ns) / NS_PER_SECOND)


def validate_parameters(start_ns: int, end_ns: int, interval_ns: int) -> None:
    if start_ns < 0:
        raise TimelineGenerationError("simulation start must be >= 0")
    if end_ns <= start_ns:
        raise TimelineGenerationError("simulation end must be > simulation start")
    if interval_ns <= 0:
        raise TimelineGenerationError("window interval must be > 0")
    duration_ns = end_ns - start_ns
    if duration_ns % interval_ns != 0:
        raise TimelineGenerationError(
            "window interval must divide the simulation duration exactly; partial windows are not generated"
        )


def build_timeline(start_ns: int, end_ns: int, interval_ns: int) -> list[WindowRow]:
    validate_parameters(start_ns, end_ns, interval_ns)
    rows: list[WindowRow] = []
    previous_ns = start_ns
    window_index = 1
    while previous_ns < end_ns:
        window_ns = previous_ns + interval_ns
        rows.append(
            WindowRow(
                window_index=window_index,
                previous_window_time_ns=previous_ns,
                window_time_ns=window_ns,
            )
        )
        previous_ns = window_ns
        window_index += 1
    validate_timeline(rows)
    return rows


def validate_timeline(rows: list[WindowRow]) -> None:
    if not rows:
        raise TimelineGenerationError("timeline generation produced no windows")
    seen_times: set[int] = set()
    previous_time: int | None = None
    for row in rows:
        if row.window_time_ns in seen_times:
            raise TimelineGenerationError(f"duplicate window timestamp: {row.window_time_ns}")
        seen_times.add(row.window_time_ns)
        if previous_time is not None and row.window_time_ns <= previous_time:
            raise TimelineGenerationError("window timestamps must be strictly increasing")
        if row.previous_window_time_ns >= row.window_time_ns:
            raise TimelineGenerationError(
                f"window {row.window_index} must have previousWindowTimeNs < windowTimeNs"
            )
        previous_time = row.window_time_ns


def write_csv_atomic(path: Path, rows: list[WindowRow]) -> None:
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
            writer = csv.DictWriter(handle, fieldnames=CSV_COLUMNS, lineterminator="\n")
            writer.writeheader()
            writer.writerows(row.to_csv_row() for row in rows)
        os.replace(temp_name, path)
        temp_name = None
    finally:
        if temp_name is not None and os.path.exists(temp_name):
            os.unlink(temp_name)


def run() -> None:
    args = parse_args()
    output_file = Path(args.output_file)
    start_ns = parse_seconds_to_ns(args.simulation_start_seconds, "simulationStartSeconds")
    end_ns = parse_seconds_to_ns(args.simulation_end_seconds, "simulationEndSeconds")
    interval_ns = parse_seconds_to_ns(args.window_interval_seconds, "windowIntervalSeconds")
    rows = build_timeline(start_ns, end_ns, interval_ns)
    write_csv_atomic(output_file, rows)

    print("Phase 10H fixed optimization window timeline generation completed")
    print(f"timelinePolicy={TIMELINE_POLICY}")
    print(f"simulationStartSeconds={format_seconds_from_ns(start_ns)}")
    print(f"simulationEndSeconds={format_seconds_from_ns(end_ns)}")
    print(f"windowIntervalSeconds={format_seconds_from_ns(interval_ns)}")
    print(f"windowsGenerated={len(rows)}")
    print(f"firstWindowTimeSeconds={format_seconds_from_ns(rows[0].window_time_ns)}")
    print(f"lastWindowTimeSeconds={format_seconds_from_ns(rows[-1].window_time_ns)}")
    print(f"outputFile={output_file}")


def main() -> int:
    try:
        run()
    except TimelineGenerationError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
