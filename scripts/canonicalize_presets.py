#!/usr/bin/env python3
"""Sync preset.json graph sections to canonical IDs from graph_presets.json."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRESETS_DIR = ROOT / "presets"
GRAPH_PRESETS = ROOT / "src/main/resources/nodecraft/graph_presets.json"

NODE_TYPE_ALIASES = {
    "geometry.primitives.box_by_center_and_size": "geometry.primitives.box",
    "geometry.primitives.box_by_corner_and_size": "geometry.primitives.box_from_corner_size",
    "geometry.primitives.cylinder_by_axis_and_radius": "geometry.primitives.cylinder",
    "geometry.primitives.sphere_by_center_and_radius": "geometry.primitives.sphere",
    "geometry.profiles.triangle": "geometry.profiles.polygon_profile",
    "geometry.profiles.rectangle": "geometry.profiles.rectangle_profile",
    "geometry.profiles.circle": "geometry.profiles.circle_profile",
    "geometry.profiles.arc": "geometry.profiles.sector_profile",
    "geometry.profiles.union_profiles": "geometry.profiles.polygon_profile",
    "geometry.solids.extrude": "geometry.solids.extrude_profile",
    "geometry.boolean.union_multiple": "geometry.boolean.union",
    "geometry.curves.line": "geometry.curves.points_to_path",
    "geometry.curves.circle": "geometry.profiles.circle_profile",
    "geometry.curves.divide_curve": "geometry.curves.path_to_points",
    "transform.basic.move": "transform.basic_transforms.move_geometry",
    "transform.basic.rotate": "transform.basic_transforms.rotate_geometry_axis",
    "transform.basic.scale": "transform.basic_transforms.scale_geometry_point",
    "transform.basic_transforms.translate": "transform.basic_transforms.move_geometry",
    "transform.basic_transforms.rotate": "transform.basic_transforms.rotate_geometry_axis",
    "material.gradient_mapping.height_gradient": "material.gradient_mapping.height_gradient_map",
    "output.bake.geometry_to_blocks": "output.execute.bake_geometry_to_blocks",
    "output.preview.block_preview": "output.preview.preview_blocks",
    "pattern.instances.place_instances_at_points": "pattern.linear.instance_on_points",
    "pattern.instances.orient_instances_to_frames": "pattern.linear.instance_on_points",
    "patterns.instances.instance_on_points": "pattern.linear.instance_on_points",
    "patterns.array.linear": "pattern.linear.linear_array",
    "patterns.instances.instance_geometry_to_points": "pattern.linear.instance_on_points",
}


def canonical_type(type_id: str) -> str:
    return NODE_TYPE_ALIASES.get(type_id, type_id)


def load_composites() -> dict[str, dict]:
    rules = json.loads(GRAPH_PRESETS.read_text(encoding="utf-8-sig"))
    composites: dict[str, dict] = {}
    for category in rules.get("categories", []):
        for preset in category.get("presets", []):
            preset_id = preset.get("id")
            if preset_id:
                composites[preset_id] = preset
    return composites


def composite_to_connections(composite: dict) -> list[dict]:
    connections = []
    for conn in composite.get("connections", []):
        connections.append(
            {
                "from": {"node": conn["fromRef"], "port": conn["fromPort"]},
                "to": {"node": conn["toRef"], "port": conn["toPort"]},
            }
        )
    return connections


def canonicalize_preset(path: Path, composites: dict[str, dict]) -> bool:
    data = json.loads(path.read_text(encoding="utf-8"))
    preset_id = data.get("preset_id")
    if not preset_id:
        print(f"SKIP (no preset_id): {path}")
        return False

    composite = composites.get(preset_id)
    if composite is None:
        print(f"SKIP (no composite): {preset_id} in {path}")
        return False

    graph = data.setdefault("graph", {})
    ref_to_type = {
        node["ref"]: canonical_type(node["typeId"])
        for node in composite.get("nodes", [])
        if node.get("ref") and node.get("typeId")
    }

    changed = False
    for node in graph.get("nodes", []):
        node_id = node.get("id")
        old_type = node.get("type")
        if node_id in ref_to_type:
            new_type = ref_to_type[node_id]
        elif old_type:
            new_type = canonical_type(old_type)
        else:
            continue
        if old_type != new_type:
            node["type"] = new_type
            changed = True

    new_connections = composite_to_connections(composite)
    if graph.get("connections") != new_connections:
        graph["connections"] = new_connections
        changed = True

    if changed:
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"UPDATED: {path.relative_to(ROOT)}")
    else:
        print(f"OK: {path.relative_to(ROOT)}")

    return changed


def main() -> None:
    composites = load_composites()
    preset_files = sorted({p.resolve() for p in PRESETS_DIR.rglob("preset.json")})
    updated = 0
    for path in preset_files:
        if canonicalize_preset(path, composites):
            updated += 1
    print(f"\nDone. Updated {updated}/{len(preset_files)} preset files.")


if __name__ == "__main__":
    main()
