#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Parser dei report diagnostici MA-GA.

Uso:

    python parse_ma_ga_reports.py validation_reports validation_results

Input:
    cartella contenente file .txt con output completo di TemporalWindowStressTestMain

Output:
    summary.csv       una riga per report
    per_window.csv    una riga per finestra
    cause_summary.csv una riga per causa aggregata per report

Il parser è intenzionalmente indipendente dal codice Java.
Serve per validare più run senza modificare l'algoritmo.
"""

from __future__ import annotations

import csv
import re
import statistics
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


def parse_float(value: str) -> Optional[float]:
    if value is None:
        return None
    cleaned = value.strip().replace(".", "").replace(",", ".")
    # The replacement above would turn 16.239 into 16239 if decimal dot is used.
    # The MA-GA reports usually use comma decimals. Handle dot decimals separately.
    raw = value.strip()
    if "," in raw:
        cleaned = raw.replace(".", "").replace(",", ".")
    else:
        cleaned = raw
    try:
        return float(cleaned)
    except ValueError:
        return None


def parse_int(value: str) -> Optional[int]:
    if value is None:
        return None
    match = re.search(r"-?\d+", value)
    if not match:
        return None
    return int(match.group(0))


def find_first(pattern: str, text: str, flags: int = 0) -> Optional[re.Match]:
    return re.search(pattern, text, flags)


def get_section(text: str, title: str) -> str:
    pattern = rf"-{{10,}}\s*\n\d+\.\s+{re.escape(title)}\s*\n-{{10,}}\s*\n"
    match = re.search(pattern, text)
    if not match:
        return ""
    start = match.end()
    next_match = re.search(r"\n-+\n\d+\.\s+", text[start:])
    if not next_match:
        return text[start:]
    return text[start:start + next_match.start()]


def infer_scenario_and_seed(path: Path, root: Path) -> Tuple[str, str]:
    rel = path.relative_to(root)
    scenario = rel.parts[0] if len(rel.parts) > 1 else "unknown"
    seed_match = re.search(r"seed[_-]?(\d+)", path.stem, re.IGNORECASE)
    seed = seed_match.group(1) if seed_match else ""
    return scenario, seed


def parse_summary(text: str) -> Dict[str, object]:
    result: Dict[str, object] = {}

    patterns = {
        "executed_windows": r"Executed windows:\s*(\d+)",
        "critical_event_windows": r"Critical-event windows:\s*(\d+)",
        "population_reuse_windows": r"Population-reuse windows:\s*(\d+)",
        "best_final_fitness": r"Best final fitness:\s*([0-9.,-]+)",
        "task_evaluations": r"- task evaluations:\s*(\d+)",
        "deadline_violations": r"- deadline violations:\s*(\d+)\s*\(([0-9.,-]+)%\)",
        "coverage_insufficient": r"- coverage insufficient:\s*(\d+)",
        "cpu_violations": r"- CPU violations:\s*(\d+)",
        "bandwidth_violations": r"- bandwidth violations:\s*(\d+)",
        "cpu_saturated": r"- CPU saturated >=\s*[0-9.,]+%:\s*(\d+)",
        "bandwidth_saturated": r"- bandwidth saturated >=\s*[0-9.,]+%:\s*(\d+)",
    }

    for key, pattern in patterns.items():
        match = find_first(pattern, text)
        if not match:
            result[key] = None
            if key == "deadline_violations":
                result["deadline_violation_rate_percent"] = None
            continue

        if key == "best_final_fitness":
            result[key] = parse_float(match.group(1))
        elif key == "deadline_violations":
            result[key] = parse_int(match.group(1))
            result["deadline_violation_rate_percent"] = parse_float(match.group(2))
        else:
            result[key] = parse_int(match.group(1))

    # Dominant causes
    causes = [
        "LOCAL_EXECUTION_BOTTLENECK",
        "LOCAL_BRANCH_DOMINATES",
        "REMOTE_BRANCH_DOMINATES",
        "MIXED_LOCAL_REMOTE_BOTTLENECK",
        "UPLOAD_BOTTLENECK",
        "REMOTE_EXECUTION_BOTTLENECK",
        "COVERAGE_INSUFFICIENT",
    ]

    for cause in causes:
        match = re.search(rf"- {re.escape(cause)}:\s*(\d+)", text)
        result[f"cause_{cause.lower()}"] = parse_int(match.group(1)) if match else 0

    return result


def parse_per_window(text: str) -> List[Dict[str, object]]:
    rows: List[Dict[str, object]] = []

    # Decision summary
    decision_section = get_section(text, "DECISION / OFFLOADING SUMMARY")
    decision_map: Dict[int, Dict[str, object]] = {}
    for line in decision_section.splitlines():
        if "|" not in line or line.strip().startswith("idx"):
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 14:
            continue
        try:
            idx = int(parts[0])
        except ValueError:
            continue
        decision_map[idx] = {
            "local": parse_int(parts[1]),
            "edge": parse_int(parts[2]),
            "cloud": parse_int(parts[3]),
            "vehicle": parse_int(parts[4]),
            "local_exec": parse_int(parts[5]),
            "partial": parse_int(parts[6]),
            "full": parse_int(parts[7]),
            "avg_p": parse_float(parts[8]),
            "p0": parse_int(parts[9]),
            "low": parse_int(parts[10]),
            "mid_low": parse_int(parts[11]),
            "mid_high": parse_int(parts[12]),
            "high": parse_int(parts[13]),
            "p1": parse_int(parts[14]) if len(parts) > 14 else None,
        }

    # Deadline cause summary
    cause_section = get_section(text, "DEADLINE CAUSE SUMMARY")
    cause_map: Dict[int, Dict[str, object]] = {}
    for line in cause_section.splitlines():
        if "|" not in line or line.strip().startswith("idx"):
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 11:
            continue
        try:
            idx = int(parts[0])
        except ValueError:
            continue
        cause_map[idx] = {
            "violated": parse_int(parts[1]),
            "rate_percent": parse_float(parts[2].replace("%", "")),
            "local_cause": parse_int(parts[3]),
            "upload_cause": parse_int(parts[4]),
            "remote_exec_cause": parse_int(parts[5]),
            "download_cause": parse_int(parts[6]),
            "base_latency_cause": parse_int(parts[7]),
            "mixed_local_remote_cause": parse_int(parts[8]),
            "mixed_remote_cause": parse_int(parts[9]),
            "coverage_cause": parse_int(parts[10]),
            "unknown_cause": parse_int(parts[11]) if len(parts) > 11 else None,
        }

    # Resource pressure
    pressure_section = get_section(text, "RESOURCE PRESSURE SUMMARY")
    pressure_map: Dict[int, Dict[str, object]] = {}
    for line in pressure_section.splitlines():
        if "|" not in line or line.strip().startswith("idx"):
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 9:
            continue
        try:
            idx = int(parts[0])
        except ValueError:
            continue
        pressure_map[idx] = {
            "cpu_viol": parse_int(parts[1]),
            "cpu_sat": parse_int(parts[2]),
            "worst_cpu_node": parts[3],
            "worst_cpu_percent": parse_float(parts[4].replace("%", "")),
            "bw_viol": parse_int(parts[5]),
            "bw_sat": parse_int(parts[6]),
            "worst_bw_link": parts[7],
            "worst_bw_percent": parse_float(parts[8].replace("%", "")),
        }

    indexes = sorted(set(decision_map) | set(cause_map) | set(pressure_map))
    for idx in indexes:
        row: Dict[str, object] = {"window_idx": idx}
        row.update(decision_map.get(idx, {}))
        row.update(cause_map.get(idx, {}))
        row.update(pressure_map.get(idx, {}))
        rows.append(row)

    return rows


def write_csv(path: Path, rows: List[Dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        path.write_text("", encoding="utf-8")
        return

    fieldnames = sorted({key for row in rows for key in row.keys()})
    # Prefer stable leading columns
    preferred = [
        "scenario", "seed", "file", "window_idx",
        "deadline_violations", "deadline_violation_rate_percent",
        "best_final_fitness", "task_evaluations",
        "coverage_insufficient", "cpu_violations", "bandwidth_violations",
        "cpu_saturated", "bandwidth_saturated",
    ]
    ordered = [f for f in preferred if f in fieldnames] + [f for f in fieldnames if f not in preferred]

    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=ordered)
        writer.writeheader()
        writer.writerows(rows)


def main() -> int:
    if len(sys.argv) != 3:
        print("Uso: python parse_ma_ga_reports.py <reports_dir> <output_dir>")
        return 1

    reports_dir = Path(sys.argv[1]).resolve()
    output_dir = Path(sys.argv[2]).resolve()

    if not reports_dir.exists():
        print(f"Cartella non trovata: {reports_dir}")
        return 1

    summary_rows: List[Dict[str, object]] = []
    per_window_rows: List[Dict[str, object]] = []
    cause_rows: List[Dict[str, object]] = []

    txt_files = sorted(reports_dir.rglob("*.txt"))

    for path in txt_files:
        text = path.read_text(encoding="utf-8", errors="replace")
        scenario, seed = infer_scenario_and_seed(path, reports_dir)

        summary = parse_summary(text)
        summary.update({
            "scenario": scenario,
            "seed": seed,
            "file": str(path.relative_to(reports_dir)),
        })
        summary_rows.append(summary)

        for row in parse_per_window(text):
            row.update({
                "scenario": scenario,
                "seed": seed,
                "file": str(path.relative_to(reports_dir)),
            })
            per_window_rows.append(row)

        for key, value in summary.items():
            if key.startswith("cause_"):
                cause_rows.append({
                    "scenario": scenario,
                    "seed": seed,
                    "file": str(path.relative_to(reports_dir)),
                    "cause": key.replace("cause_", "").upper(),
                    "count": value,
                })

    write_csv(output_dir / "summary.csv", summary_rows)
    write_csv(output_dir / "per_window.csv", per_window_rows)
    write_csv(output_dir / "cause_summary.csv", cause_rows)

    # Optional aggregate by scenario
    aggregate_rows: List[Dict[str, object]] = []
    by_scenario: Dict[str, List[Dict[str, object]]] = {}
    for row in summary_rows:
        by_scenario.setdefault(str(row.get("scenario", "unknown")), []).append(row)

    metric_keys = [
        "deadline_violations",
        "deadline_violation_rate_percent",
        "best_final_fitness",
        "coverage_insufficient",
        "cpu_violations",
        "bandwidth_violations",
        "cpu_saturated",
        "bandwidth_saturated",
        "cause_mixed_local_remote_bottleneck",
        "cause_upload_bottleneck",
        "cause_remote_execution_bottleneck",
        "cause_local_execution_bottleneck",
    ]

    for scenario, rows in by_scenario.items():
        aggregate = {"scenario": scenario, "runs": len(rows)}
        for key in metric_keys:
            values = [r.get(key) for r in rows if isinstance(r.get(key), (int, float))]
            if not values:
                aggregate[f"{key}_mean"] = None
                aggregate[f"{key}_stdev"] = None
                aggregate[f"{key}_min"] = None
                aggregate[f"{key}_max"] = None
                continue
            aggregate[f"{key}_mean"] = statistics.mean(values)
            aggregate[f"{key}_stdev"] = statistics.stdev(values) if len(values) > 1 else 0.0
            aggregate[f"{key}_min"] = min(values)
            aggregate[f"{key}_max"] = max(values)
        aggregate_rows.append(aggregate)

    write_csv(output_dir / "aggregate_by_scenario.csv", aggregate_rows)

    print(f"Report letti: {len(txt_files)}")
    print(f"Creato: {output_dir / 'summary.csv'}")
    print(f"Creato: {output_dir / 'per_window.csv'}")
    print(f"Creato: {output_dir / 'cause_summary.csv'}")
    print(f"Creato: {output_dir / 'aggregate_by_scenario.csv'}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
