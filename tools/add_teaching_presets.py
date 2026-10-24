#!/usr/bin/env python3
"""Add teaching quickstart presets for sphere / box / arc-circle / selection / twist."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRAPH_FILE = ROOT / "src/main/resources/nodecraft/graph_presets.json"


def node(ref: str, type_id: str, x: float = 0.0, y: float = 0.0, state: dict | None = None) -> dict:
    entry = {"ref": ref, "typeId": type_id, "x": x, "y": y}
    if state is not None:
        entry["state"] = state
    return entry


def conn(fr: str, fp: str, to: str, tp: str) -> dict:
    return {"fromRef": fr, "fromPort": fp, "toRef": to, "toPort": tp}


def preview_material_tail(block_type: str = "minecraft:stone") -> tuple[list[dict], list[dict]]:
    nodes = [
        node("preview_geometry", "output.preview.preview_geometry"),
        node("voxelize", "geometry.voxel.voxelize_geometry"),
        node("material", "material.basic_assignment.assign_block_type"),
        node("preview_blocks", "output.preview.preview_blocks"),
        node("material_block_type", "input.type_selectors.block_type_selector", state={
            "selectedBlock": block_type,
        }),
    ]
    connections = [
        conn("voxelize", "output_blocks", "material", "input_coordinates"),
        conn("material_block_type", "output_block_id", "material", "input_block_type"),
        conn("material", "output_placements", "preview_blocks", "input_block_placements"),
    ]
    return nodes, connections


def move_to_player_nodes() -> tuple[list[dict], list[dict]]:
    nodes = [
        node("player_pos", "input.context.player_position"),
        node("move_to_pos", "transform.basic_transforms.move_geometry"),
        node("move_to_pos_point_deconstruct", "reference.points.deconstruct_point"),
        node("move_to_pos_point_as_vector", "reference.vectors.construct_vector"),
    ]
    connections = [
        conn("player_pos", "output_position", "move_to_pos_point_deconstruct", "input_point"),
        conn("move_to_pos_point_deconstruct", "output_x", "move_to_pos_point_as_vector", "input_x"),
        conn("move_to_pos_point_deconstruct", "output_y", "move_to_pos_point_as_vector", "input_y"),
        conn("move_to_pos_point_deconstruct", "output_z", "move_to_pos_point_as_vector", "input_z"),
        conn("move_to_pos_point_as_vector", "output_vector", "move_to_pos", "input_translation"),
    ]
    return nodes, connections


NEW_PRESETS: dict[str, dict] = {
    "quickstart.sphere_from_center": {
        "id": "quickstart.sphere_from_center",
        "displayName": "Sphere From Center",
        "description": (
            "Player Position as center → Sphere By Center Radius → Preview Geometry, "
            "plus Voxelize → Assign Block Type → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position"),
            node("sphere", "geometry.primitives.sphere", state={"radius": 5.0}),
            *preview_material_tail("minecraft:stone")[0],
        ],
        "connections": [
            conn("player_pos", "output_position", "sphere", "input_center"),
            conn("sphere", "output_geometry", "preview_geometry", "input_geometry"),
            conn("sphere", "output_geometry", "voxelize", "input_geometry"),
            *preview_material_tail()[1],
        ],
    },
    "quickstart.box_from_two_corners": {
        "id": "quickstart.box_from_two_corners",
        "displayName": "Box From Two Corners",
        "description": (
            "Two opposite corner points → Box by Two Corners → Move to player → "
            "Preview Geometry, Voxelize → Assign Block Type → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            *move_to_player_nodes()[0],
            node("corner_a_x", "input.numeric.float", state={"value": 0.0}),
            node("corner_a_y", "input.numeric.float", state={"value": 0.0}),
            node("corner_a_z", "input.numeric.float", state={"value": 0.0}),
            node("corner_a", "reference.points.construct_point"),
            node("corner_b_x", "input.numeric.float", state={"value": 6.0}),
            node("corner_b_y", "input.numeric.float", state={"value": 4.0}),
            node("corner_b_z", "input.numeric.float", state={"value": 5.0}),
            node("corner_b", "reference.points.construct_point"),
            node("box", "geometry.primitives.box_from_corners"),
            *preview_material_tail("minecraft:oak_planks")[0],
        ],
        "connections": [
            *move_to_player_nodes()[1],
            conn("corner_a_x", "output_value", "corner_a", "input_x"),
            conn("corner_a_y", "output_value", "corner_a", "input_y"),
            conn("corner_a_z", "output_value", "corner_a", "input_z"),
            conn("corner_b_x", "output_value", "corner_b", "input_x"),
            conn("corner_b_y", "output_value", "corner_b", "input_y"),
            conn("corner_b_z", "output_value", "corner_b", "input_z"),
            conn("corner_a", "output_point", "box", "input_corner_a"),
            conn("corner_b", "output_point", "box", "input_corner_b"),
            conn("box", "output_geometry", "move_to_pos", "input_geometry"),
            conn("move_to_pos", "output_geometry", "preview_geometry", "input_geometry"),
            conn("move_to_pos", "output_geometry", "voxelize", "input_geometry"),
            *preview_material_tail()[1],
        ],
    },
    "quickstart.arc_and_circle_voxel": {
        "id": "quickstart.arc_and_circle_voxel",
        "displayName": "Arc & Circle Voxelize",
        "description": (
            "Quarter Arc + full Circle profile boundary at the player → Voxelize Path "
            "(tube) → Assign Block Type → Preview Blocks. Demonstrates curve voxelization."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position"),
            node("arc_start", "input.numeric.float", state={"value": 0.0}),
            node("arc_end", "input.numeric.float", state={"value": 90.0}),
            node("arc", "geometry.curves.arc", state={
                "defaultRadius": 6.0,
                "defaultSamples": 24,
            }),
            node("circle", "geometry.profiles.circle_profile", state={
                "radius": 8.0,
                "segments": 32,
            }),
            node("voxelize_arc", "geometry.curves.voxelize_curve", state={
                "defaultRadius": 0.6,
                "fillTube": True,
                "capEnds": True,
            }),
            node("voxelize_circle", "geometry.curves.voxelize_curve", state={
                "defaultRadius": 0.6,
                "fillTube": True,
                "capEnds": True,
            }),
            node("material_arc", "material.basic_assignment.assign_block_type"),
            node("material_circle", "material.basic_assignment.assign_block_type"),
            node("block_arc", "input.type_selectors.block_type_selector", state={
                "selectedBlock": "minecraft:smooth_stone",
            }),
            node("block_circle", "input.type_selectors.block_type_selector", state={
                "selectedBlock": "minecraft:gold_block",
            }),
            node("preview_arc", "output.preview.preview_blocks"),
            node("preview_circle", "output.preview.preview_blocks"),
            node("preview_arc_geometry", "output.preview.preview_geometry"),
            node("preview_circle_geometry", "output.preview.preview_geometry"),
        ],
        "connections": [
            conn("player_pos", "output_position", "arc", "input_center"),
            conn("arc_start", "output_value", "arc", "input_start_angle"),
            conn("arc_end", "output_value", "arc", "input_end_angle"),
            conn("arc", "output_path", "voxelize_arc", "input_path"),
            conn("voxelize_arc", "output_geometry", "preview_arc_geometry", "input_geometry"),
            conn("voxelize_arc", "output_blocks", "material_arc", "input_coordinates"),
            conn("block_arc", "output_block_id", "material_arc", "input_block_type"),
            conn("material_arc", "output_placements", "preview_arc", "input_block_placements"),
            conn("player_pos", "output_position", "circle", "input_center"),
            conn("circle", "output_boundary", "voxelize_circle", "input_path"),
            conn("voxelize_circle", "output_geometry", "preview_circle_geometry", "input_geometry"),
            conn("voxelize_circle", "output_blocks", "material_circle", "input_coordinates"),
            conn("block_circle", "output_block_id", "material_circle", "input_block_type"),
            conn("material_circle", "output_placements", "preview_circle", "input_block_placements"),
        ],
    },
    "quickstart.selected_block_column": {
        "id": "quickstart.selected_block_column",
        "displayName": "Select Connected Column",
        "description": (
            "Selected Block (pick a seed) → Flood Fill same-type connected blocks "
            "(ideal for a vertical column / pillar) → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("selected_block", "world.selection.selected_block"),
            node("flood_fill", "world.query.flood_fill"),
            node("max_distance", "input.numeric.integer", state={"value": 64}),
            node("max_blocks", "input.numeric.integer", state={"value": 512}),
            node("preview_blocks", "output.preview.preview_blocks"),
        ],
        "connections": [
            conn("selected_block", "output_position", "flood_fill", "input_seed"),
            conn("max_distance", "output_value", "flood_fill", "input_max_distance"),
            conn("max_blocks", "output_value", "flood_fill", "input_max_blocks"),
            conn("flood_fill", "output_blocks", "preview_blocks", "input_blocks"),
        ],
    },
    "quickstart.twisted_cube": {
        "id": "quickstart.twisted_cube",
        "displayName": "Twisted Cube",
        "description": (
            "SDF Box at the player → Twist SDF around Y → SDF To Geometry → "
            "Preview Geometry, Voxelize → Assign Block Type → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position"),
            node("axis_up", "reference.vectors.vector", state={"x": 0.0, "y": 1.0, "z": 0.0}),
            node("sdf_box", "geometry.boolean.sdf_box", state={
                "halfX": 3.0,
                "halfY": 4.0,
                "halfZ": 3.0,
            }),
            node("twist", "transform.deformations.twist_sdf", state={
                "angleDegrees": 180.0,
                "twistLength": 8.0,
                "clampMode": "CLAMP",
                "boundsPadding": 1.0,
                "boundsSamples": 5,
            }),
            node("sdf_to_geometry", "geometry.boolean.sdf_to_geometry", state={
                "autoBounds": True,
                "boundsPadding": 1.0,
            }),
            *preview_material_tail("minecraft:purple_concrete")[0],
        ],
        "connections": [
            conn("player_pos", "output_position", "sdf_box", "input_center"),
            conn("player_pos", "output_position", "twist", "input_axis_origin"),
            conn("axis_up", "output_vector", "twist", "input_axis_direction"),
            conn("sdf_box", "output_sdf", "twist", "input_sdf"),
            conn("twist", "output_sdf", "sdf_to_geometry", "input_sdf"),
            conn("twist", "output_bounds_min", "sdf_to_geometry", "input_min"),
            conn("twist", "output_bounds_max", "sdf_to_geometry", "input_max"),
            conn("sdf_to_geometry", "output_geometry", "preview_geometry", "input_geometry"),
            conn("sdf_to_geometry", "output_geometry", "voxelize", "input_geometry"),
            *preview_material_tail()[1],
        ],
    },
}


def main() -> None:
    data = json.loads(GRAPH_FILE.read_text(encoding="utf-8-sig"))
    quickstart = next(c for c in data["categories"] if c["id"] == "quickstart")
    existing = {p["id"]: i for i, p in enumerate(quickstart["presets"])}

    added = 0
    updated = 0
    for preset_id, preset in NEW_PRESETS.items():
        if preset_id in existing:
            quickstart["presets"][existing[preset_id]] = preset
            updated += 1
            print(f"updated {preset_id}")
        else:
            quickstart["presets"].append(preset)
            added += 1
            print(f"added {preset_id}")

    GRAPH_FILE.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"done: added={added} updated={updated}")


if __name__ == "__main__":
    main()
