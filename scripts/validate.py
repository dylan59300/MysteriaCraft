#!/usr/bin/env python3
"""Validate MysteriaCraft JSON data files against their schemas.

Exits with code 1 if any file fails validation, providing clear error
messages instead of silently accepting malformed data.

Usage:
    python scripts/validate.py
    python scripts/validate.py --verbose
"""

import argparse
import json
import sys
from pathlib import Path

# Map of directories to their schema files
SCHEMA_MAP = {
    "biomes": "schemas/biome.schema.json",
}

PROJECT_ROOT = Path(__file__).resolve().parent.parent


def load_json(path: Path) -> tuple[dict | list | None, str | None]:
    """Load and parse a JSON file, returning (data, error)."""
    try:
        with open(path, encoding="utf-8") as f:
            content = f.read().strip()
    except OSError as e:
        return None, f"cannot read file: {e}"

    if not content:
        return None, "file is empty"

    try:
        data = json.loads(content)
    except json.JSONDecodeError as e:
        return None, f"invalid JSON at line {e.lineno}, col {e.colno}: {e.msg}"

    return data, None


def validate_schema(data: dict, schema: dict, file_path: Path) -> list[str]:
    """Validate data against a JSON schema. Returns a list of error messages."""
    errors = []

    # Check required fields
    for field in schema.get("required", []):
        if field not in data:
            errors.append(f"missing required field '{field}'")

    properties = schema.get("properties", {})
    additional_allowed = schema.get("additionalProperties", True)

    # Check for unknown fields
    if additional_allowed is False:
        known = set(properties.keys())
        for key in data:
            if key not in known:
                errors.append(f"unknown field '{key}'")

    # Validate each field
    for field, field_schema in properties.items():
        if field not in data:
            continue

        value = data[field]
        field_type = field_schema.get("type")

        # Type check
        type_map = {
            "string": str,
            "number": (int, float),
            "integer": int,
            "boolean": bool,
            "array": list,
            "object": dict,
        }

        if field_type and field_type in type_map:
            expected = type_map[field_type]
            if not isinstance(value, expected):
                errors.append(
                    f"field '{field}' should be {field_type}, "
                    f"got {type(value).__name__}"
                )
                continue

        # String pattern check
        if field_type == "string" and "pattern" in field_schema:
            import re

            pattern = field_schema["pattern"]
            if not re.match(pattern, value):
                errors.append(
                    f"field '{field}' value '{value}' "
                    f"does not match pattern '{pattern}'"
                )

        # Enum check
        if "enum" in field_schema and value not in field_schema["enum"]:
            errors.append(
                f"field '{field}' value '{value}' not in allowed values: "
                f"{field_schema['enum']}"
            )

        # Numeric range checks
        if field_type in ("number", "integer"):
            if "minimum" in field_schema and value < field_schema["minimum"]:
                errors.append(
                    f"field '{field}' value {value} "
                    f"below minimum {field_schema['minimum']}"
                )
            if "maximum" in field_schema and value > field_schema["maximum"]:
                errors.append(
                    f"field '{field}' value {value} "
                    f"above maximum {field_schema['maximum']}"
                )

        # String length checks
        if field_type == "string":
            if "minLength" in field_schema and len(value) < field_schema["minLength"]:
                errors.append(
                    f"field '{field}' is too short "
                    f"(min {field_schema['minLength']} chars)"
                )

        # Recurse into nested objects
        if field_type == "object" and isinstance(value, dict):
            nested_errors = validate_schema(value, field_schema, file_path)
            for err in nested_errors:
                errors.append(f"in '{field}': {err}")

    return errors


def validate_file(
    json_path: Path, schema: dict, verbose: bool = False
) -> list[str]:
    """Validate a single JSON file. Returns a list of error messages."""
    rel_path = json_path.relative_to(PROJECT_ROOT)

    if verbose:
        print(f"  checking {rel_path}...")

    data, parse_error = load_json(json_path)
    if parse_error is not None:
        return [f"{rel_path}: {parse_error}"]

    if not isinstance(data, dict):
        return [f"{rel_path}: expected a JSON object, got {type(data).__name__}"]

    schema_errors = validate_schema(data, schema, json_path)
    return [f"{rel_path}: {err}" for err in schema_errors]


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate MysteriaCraft data files")
    parser.add_argument("--verbose", "-v", action="store_true", help="Show progress")
    args = parser.parse_args()

    all_errors: list[str] = []
    files_checked = 0

    for directory, schema_rel_path in SCHEMA_MAP.items():
        schema_path = PROJECT_ROOT / schema_rel_path
        schema_data, schema_err = load_json(schema_path)
        if schema_err is not None:
            print(f"ERROR: cannot load schema {schema_rel_path}: {schema_err}")
            return 1

        data_dir = PROJECT_ROOT / directory
        if not data_dir.is_dir():
            if args.verbose:
                print(f"  skipping {directory}/ (not found)")
            continue

        if args.verbose:
            print(f"Validating {directory}/ against {schema_rel_path}")

        for json_file in sorted(data_dir.glob("*.json")):
            files_checked += 1
            errors = validate_file(json_file, schema_data, args.verbose)
            all_errors.extend(errors)

    if files_checked == 0:
        print("WARNING: no data files found to validate")
        return 0

    if all_errors:
        print(f"\nValidation failed with {len(all_errors)} error(s):\n")
        for err in all_errors:
            print(f"  ✗ {err}")
        print(f"\n{files_checked} file(s) checked, {len(all_errors)} error(s) found")
        return 1

    print(f"All {files_checked} file(s) passed validation")
    return 0


if __name__ == "__main__":
    sys.exit(main())
