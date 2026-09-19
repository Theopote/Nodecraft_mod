#!/usr/bin/env python3
"""Build V0->V1 graph migration manifest from the node alias plan."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLAN = ROOT / "docs" / "nodecraft-v1-node-alias-plan.md"
OUT = ROOT / "src" / "main" / "resources" / "nodecraft" / "migration" / "v0-to-v1.json"


def load_registered_node_ids() -> set[str]:
    ids: set[str] = set()
    for path in (ROOT / "src" / "main" / "java").rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="ignore")
        for match in re.finditer(r'id\s*=\s*"([^"]+)"', text):
            ids.add(match.group(1).lower())
    return ids


def parse_node_type_aliases(plan_text: str) -> dict[str, str]:
    aliases: dict[str, str] = {}
    for match in re.finditer(r"- `([^`]+)` -> `([^`]+)`", plan_text):
        old_id = match.group(1).strip().lower()
        new_id = match.group(2).strip().lower()
        if old_id and new_id and " " not in old_id:
            aliases[old_id] = new_id

    # Flatten known chained historic ids directly to final canonical targets.
    chained = {
        "spatial.generators.sphere_by_center_radius": "geometry.primitives.sphere",
        "spatial.generators.box_center_size": "geometry.primitives.box",
        "spatial.generators.cylinder_by_axis_radius": "geometry.primitives.cylinder",
        "spatial.generators.torusblocks": "geometry.primitives.torus",
        "spatial.generators.torus_blocks": "geometry.primitives.torus",
        "visualization.debugging.panel": "output.debug.data_inspector",
        "panel": "output.debug.data_inspector",
    }
    aliases.update(chained)

    # Correct alias-plan rows that diverged from the current catalog.
    direct_overrides = {
        "inputs.basic.boolean_toggle": "input.basic.boolean_toggle",
        "inputs.sources.create_list": "math.list.create_list",
        "math.basic.math_range": "input.numeric.range",
        "math.randomness.noise": "math.random.noise",
        "world.modification.material_mapper": "material.basic_assignment.assign_block_type",
        "world.modification.remove_blocks": "world.write.remove_blocks",
        "spatial.voxel.geometry_to_blocks": "output.execute.bake_geometry_to_blocks",
        "spatial.voxel.surface_strip_to_blocks": "output.execute.bake_surface_strip_to_blocks",
        "spatial.voxel.box_geometry_voxelizer": "output.execute.bake_geometry_to_blocks",
        "spatial.voxel.sphere_geometry_voxelizer": "output.execute.bake_geometry_to_blocks",
        "spatial.voxel.cylinder_geometry_voxelizer": "output.execute.bake_geometry_to_blocks",
        "spatial.voxel.cone_geometry_voxelizer": "output.execute.bake_geometry_to_blocks",
        "spatial.voxel.ellipsoid_geometry_voxelizer": "output.execute.bake_geometry_to_blocks",
        "spatial.voxel.octahedron_geometry_voxelizer": "output.execute.bake_geometry_to_blocks",
        "spatial.voxel.tetrahedron_geometry_voxelizer": "output.execute.bake_geometry_to_blocks",
        "spatial.voxel.torus_geometry_voxelizer": "output.execute.bake_geometry_to_blocks",
        "spatial.voxel.prism_geometry_voxelizer": "output.execute.bake_geometry_to_blocks",
        "spatial.points.offset_coordinates": "transform.basic_transforms.offset_coordinates",
        "spatial.points.rotate_coordinates": "transform.basic_transforms.rotate_coordinates",
        "spatial.points.scale_coordinates": "transform.basic_transforms.scale_coordinates",
        "spatial.points.mirror_coordinates": "transform.basic_transforms.mirror_coordinates",
        "spatial.points.project_point_to_plane": "transform.orientation.project_to_plane",
        "spatial.modeling.extrude_profile": "geometry.solids.extrude_profile",
        "spatial.modeling.sweep_profile_along_path": "geometry.solids.sweep",
        "math.vector.midpoint": "reference.points.mid_point",
        "math.vector.construct_coordinate": "reference.points.construct_coordinate",
        "math.vector.deconstruct_coordinate": "reference.points.deconstruct_point",
    }
    aliases.update(direct_overrides)

    # Resolve alias chains to final targets.
    changed = True
    while changed:
        changed = False
        for old_id, target in list(aliases.items()):
            resolved = aliases.get(target, target)
            if resolved != target:
                aliases[old_id] = resolved
                changed = True
            elif target != aliases[old_id]:
                aliases[old_id] = target
                changed = True

    return aliases


def build_manifest() -> dict:
    plan_text = PLAN.read_text(encoding="utf-8")
    node_types = parse_node_type_aliases(plan_text)
    registered = load_registered_node_ids()

    missing_targets = sorted({target for target in node_types.values() if target not in registered})
    if missing_targets:
        print(f"WARNING: {len(missing_targets)} migration targets are not registered node ids")
        for target in missing_targets[:20]:
            print(f"  - {target}")

    return {
        "manifestVersion": 1,
        "fromFormatVersion": 0,
        "toFormatVersion": 1,
        "nodeTypes": dict(sorted(node_types.items())),
        "globalOutputPortAliases": {
            "geometry": "output_geometry",
            "position": "output_position",
            "blocks": "output_blocks",
            "result": "output_geometry",
            "curve": "output_curve",
            "profile": "output_profile",
            "points": "output_points",
            "instances": "output_placements",
        },
        "globalInputPortAliases": {
            "geometry": "input_geometry",
            "position": "input_translation",
            "center": "input_center",
            "translation": "input_translation",
            "blocks": "input_blocks",
            "base": "input_start",
            "curve": "input_curve",
            "profile": "input_profile",
            "path": "input_curve",
            "points": "input_points",
            "origins": "input_points",
        },
        "nodePortAliases": {
            "geometry.boolean.difference": {
                "a": "input_base",
                "b": "input_cutter",
                "result": "output_geometry",
            },
            "geometry.boolean.union": {
                "a": "input_geometry_0",
                "b": "input_geometry_1",
                "result": "output_geometry",
                "geometries": "input_geometry_0",
            },
            "geometry.primitives.cylinder": {
                "base": "input_start",
            },
            "geometry.primitives.box": {
                "center": "input_center",
            },
            "input.context.player_position": {
                "position": "output_position",
            },
            "output.preview.preview_blocks": {
                "blocks": "input_blocks",
            },
            "output.execute.bake_geometry_to_blocks": {
                "geometry": "input_geometry",
                "blocks": "output_blocks",
            },
            "transform.basic_transforms.move_geometry": {
                "geometry": "input_geometry",
                "translation": "input_translation",
            },
            "material.basic_assignment.assign_block_type": {
                "geometry": "input_geometry",
            },
            "math.scalar_math.addition": {
                "a": "input_a",
                "b": "input_b",
            },
            "input.numeric.integer": {
                "value": "output_value",
            },
            "world.write.set_block": {
                "position": "input_coordinate",
                "block": "input_block_info",
            },
            "input.type_selectors.block_type_selector": {
                "block_type": "output_block_id",
            },
        },
        "nodePropertyRenames": {
            "geometry.primitives.box": {
                "size_x": "width",
                "size_y": "height",
                "size_z": "depth",
            }
        },
        "enumValueRenames": {},
        "removedNodeReplacements": {},
    }


def main() -> None:
    if not PLAN.exists():
        raise SystemExit(f"Missing alias plan: {PLAN}")
    manifest = build_manifest()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(manifest['nodeTypes'])} node type aliases to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
