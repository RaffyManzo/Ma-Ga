#!/usr/bin/env python3
"""Validate an external InTAS checkout for the MaGa literature scenario tool."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


REQUIRED_RELATIVE_PATHS = [
    "scenario/ingolstadt.net.xml",
    "scenario/InTAS_buildings.sumocfg",
    "scenario/routes",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Validate the external silaslobo/InTAS assets required by the "
            "MaGaLiteratureBasedUrbanStudy materializer."
        )
    )
    parser.add_argument("--intas-root", required=True, help="Path to an external InTAS checkout.")
    parser.add_argument(
        "--json-output",
        help="Optional path where the validation report JSON should be written.",
    )
    return parser.parse_args()


def command_path(name: str) -> str | None:
    return shutil.which(name)


def git_value(root: Path, *args: str) -> str | None:
    if not (root / ".git").exists():
        return None
    try:
        result = subprocess.run(
            ["git", "-C", str(root), *args],
            check=True,
            text=True,
            capture_output=True,
        )
    except (OSError, subprocess.CalledProcessError):
        return None
    value = result.stdout.strip()
    return value or None


def read_sumocfg_routes(sumocfg: Path) -> list[str]:
    tree = ET.parse(sumocfg)
    root = tree.getroot()
    route_values: list[str] = []
    for node in root.iter():
        tag = node.tag.split("}")[-1]
        if tag in {"route-files", "routeFiles"}:
            value = node.attrib.get("value", "")
            route_values.extend(split_sumo_list(value))
    return route_values


def split_sumo_list(value: str) -> list[str]:
    return [item.strip() for item in value.replace(";", ",").split(",") if item.strip()]


def detect_license(root: Path) -> dict[str, Any]:
    candidates = [root / "LICENSE", root / "LICENSE.txt", root / "COPYING"]
    for candidate in candidates:
        if candidate.exists():
            text = candidate.read_text(encoding="utf-8", errors="replace")
            first_line = next((line.strip() for line in text.splitlines() if line.strip()), "")
            return {
                "path": str(candidate.relative_to(root)).replace("\\", "/"),
                "firstLine": first_line,
                "containsGpl3Text": "GNU GENERAL PUBLIC LICENSE" in text and "Version 3" in text,
            }
    return {"path": None, "firstLine": None, "containsGpl3Text": False}


def validate(intas_root: Path) -> tuple[dict[str, Any], int]:
    errors: list[str] = []
    warnings: list[str] = []
    root = intas_root.resolve()

    required = []
    for relative in REQUIRED_RELATIVE_PATHS:
        path = root / relative
        exists = path.exists()
        required.append({"path": relative, "exists": exists})
        if not exists:
            errors.append(f"Missing required InTAS asset: {relative}")

    sumo_home = os.environ.get("SUMO_HOME")
    sumo = command_path("sumo")
    netconvert = command_path("netconvert")

    if not sumo_home:
        errors.append("SUMO_HOME is not set. Install SUMO and set SUMO_HOME before materialization.")
    if not sumo:
        errors.append("sumo executable not found in PATH.")
    if not netconvert:
        errors.append("netconvert executable not found in PATH.")

    route_files: list[dict[str, Any]] = []
    sumocfg = root / "scenario" / "InTAS_buildings.sumocfg"
    if sumocfg.exists():
        try:
            for route in read_sumocfg_routes(sumocfg):
                route_path = (sumocfg.parent / route).resolve()
                try:
                    relative_path = route_path.relative_to(root)
                    display_path = str(relative_path).replace("\\", "/")
                except ValueError:
                    display_path = str(route_path)
                exists = route_path.exists()
                route_files.append({"path": display_path, "exists": exists})
                if not exists:
                    errors.append(f"SUMO route file referenced by InTAS_buildings.sumocfg is missing: {display_path}")
        except ET.ParseError as exc:
            errors.append(f"Cannot parse InTAS_buildings.sumocfg: {exc}")
    else:
        warnings.append("Cannot inspect route files because InTAS_buildings.sumocfg is missing.")

    report = {
        "intasRoot": str(root),
        "repository": "https://github.com/silaslobo/InTAS",
        "requiredPaths": required,
        "sumoEnvironment": {
            "SUMO_HOME": sumo_home,
            "sumo": sumo,
            "netconvert": netconvert,
        },
        "sumocfg": "scenario/InTAS_buildings.sumocfg",
        "routeFiles": route_files,
        "license": detect_license(root) if root.exists() else None,
        "git": {
            "commit": git_value(root, "rev-parse", "HEAD"),
            "tag": git_value(root, "describe", "--tags", "--exact-match", "HEAD"),
        },
        "errors": errors,
        "warnings": warnings,
        "status": "VALID" if not errors else "INVALID",
    }
    return report, 0 if not errors else 2


def main() -> int:
    args = parse_args()
    report, exit_code = validate(Path(args.intas_root))
    text = json.dumps(report, indent=2, sort_keys=True)
    if args.json_output:
        Path(args.json_output).write_text(text + "\n", encoding="utf-8")
    print(text)
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
