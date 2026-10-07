#!/usr/bin/env python3
"""Rewrite P2 Building Elements presets to Preset Library v2."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRESET_FILES = [
    ROOT / "src/main/resources/nodecraft/graph_presets.json",
]


def node(ref: str, type_id: str, x: float, y: float, state: dict | None = None) -> dict:
    entry = {"ref": ref, "typeId": type_id, "x": x, "y": y}
    if state is not None:
        entry["state"] = state
    return entry


def conn(fr: str, fp: str, to: str, tp: str) -> dict:
    return {"fromRef": fr, "fromPort": fp, "toRef": to, "toPort": tp}


def local_origin_node(x: float = 0, y: float = 40) -> dict:
    """Local authoring origin (0,0,0) — final placement uses Move Geometry + Player Position."""
    return node("local_origin", "reference.frames.world_frame", x, y)


SHAPE_OPTIONS = "cylinder,box,frustum"


def placement_nodes(
    move_x: float,
    move_y: float,
    preview_x: float,
    voxel_x: float,
    material_x: float,
    preview_blocks_x: float,
    block_type: str,
) -> list[dict]:
    """Move + POINT→VECTOR adapters + Preview/Voxelize/Material/Preview Blocks."""
    return [
        node("move_to_pos", "transform.basic_transforms.move_geometry", move_x, move_y),
        node("move_to_pos_point_deconstruct", "reference.points.deconstruct_point", move_x - 420, move_y),
        node("move_to_pos_point_as_vector", "reference.vectors.construct_vector", move_x - 200, move_y),
        node("preview_geometry", "output.preview.preview_geometry", preview_x, 120),
        node("voxelize", "geometry.voxel.voxelize_geometry", voxel_x, 320),
        node("material", "material.basic_assignment.assign_block_type", material_x, 320),
        node("material_block_type", "input.type_selectors.block_type_selector", material_x + 40, 460, {
            "selectedBlock": block_type,
        }),
        node("preview_blocks", "output.preview.preview_blocks", preview_blocks_x, 320),
    ]


def placement_connections(from_ref: str, from_port: str = "output_geometry") -> list[dict]:
    """Player → adapters → Move translation; geometry → Preview + Voxelize → Material → Preview Blocks."""
    return [
        conn("player_pos", "output_position", "move_to_pos_point_deconstruct", "input_point"),
        conn("move_to_pos_point_deconstruct", "output_x", "move_to_pos_point_as_vector", "input_x"),
        conn("move_to_pos_point_deconstruct", "output_y", "move_to_pos_point_as_vector", "input_y"),
        conn("move_to_pos_point_deconstruct", "output_z", "move_to_pos_point_as_vector", "input_z"),
        conn("move_to_pos_point_as_vector", "output_vector", "move_to_pos", "input_translation"),
        conn(from_ref, from_port, "preview_geometry", "input_geometry"),
        conn(from_ref, from_port, "voxelize", "input_geometry"),
        conn("voxelize", "output_blocks", "material", "input_coordinates"),
        conn("material_block_type", "output_block_id", "material", "input_block_type"),
        conn("material", "output_placements", "preview_blocks", "input_block_placements"),
    ]


def block_chain(from_ref: str, from_port: str = "output_geometry") -> list[dict]:
    """Legacy Preview Geometry + Voxelize → Material → Preview Blocks (no adapters)."""
    return [
        conn(from_ref, from_port, "preview_geometry", "input_geometry"),
        conn(from_ref, from_port, "voxelize", "input_geometry"),
        conn("voxelize", "output_blocks", "material", "input_coordinates"),
        conn("material", "output_placements", "preview_blocks", "input_block_placements"),
    ]


P2_PRESETS: dict[str, dict] = {
    "building_elements.columns.classical_column": {
        "id": "building_elements.columns.classical_column",
        "displayName": "Classical Column",
        "description": (
            "Stacked Column nodes (base, shaft, capital) with Frame placement and shape "
            "dropdowns (cylinder/box/frustum) → Preview Geometry, Voxelize → Assign Block Type "
            "→ Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            local_origin_node(220, 40),
            node("base_height", "input.numeric.float", 0, 180, {"value": 0.6}),
            node("base_radius", "input.numeric.float", 0, 320, {"value": 0.75}),
            node("shaft_height", "input.numeric.float", 0, 460, {"value": 3.0}),
            node("shaft_radius", "input.numeric.float", 0, 600, {"value": 0.45}),
            node("capital_height", "input.numeric.float", 0, 740, {"value": 0.55}),
            node("capital_radius", "input.numeric.float", 0, 880, {"value": 0.65}),
            node("capital_top_scale", "input.numeric.float", 0, 1020, {"value": 1.35}),
            node("base_shape", "input.values.dropdown", 220, 180, {
                "options": SHAPE_OPTIONS, "selectedIndex": 0,
            }),
            node("shaft_shape", "input.values.dropdown", 220, 460, {
                "options": SHAPE_OPTIONS, "selectedIndex": 0,
            }),
            node("capital_shape", "input.values.dropdown", 220, 740, {
                "options": SHAPE_OPTIONS, "selectedIndex": 2,
            }),
            node("base", "geometry.architectural_primitives.column", 480, 240),
            node("shaft", "geometry.architectural_primitives.column", 720, 240),
            node("capital", "geometry.architectural_primitives.column", 960, 240),
            node("combine", "geometry.combine.geometry", 1200, 240),
            *placement_nodes(1440, 240, 1700, 1700, 1960, 2220, "minecraft:smooth_quartz"),
        ],
        "connections": [
            conn("local_origin", "output_frame", "base", "input_frame"),
            conn("base_height", "output_value", "base", "input_height"),
            conn("base_radius", "output_value", "base", "input_radius"),
            conn("base_shape", "output_value", "base", "input_shape"),
            conn("base", "output_top", "shaft", "input_base"),
            conn("shaft_height", "output_value", "shaft", "input_height"),
            conn("shaft_radius", "output_value", "shaft", "input_radius"),
            conn("shaft_shape", "output_value", "shaft", "input_shape"),
            conn("shaft", "output_top", "capital", "input_base"),
            conn("capital_height", "output_value", "capital", "input_height"),
            conn("capital_radius", "output_value", "capital", "input_radius"),
            conn("capital_top_scale", "output_value", "capital", "input_top_scale"),
            conn("capital_shape", "output_value", "capital", "input_shape"),
            conn("base", "output_geometry", "combine", "input_geometry_0"),
            conn("shaft", "output_geometry", "combine", "input_geometry_1"),
            conn("capital", "output_geometry", "combine", "input_geometry_2"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            *placement_connections("move_to_pos"),
        ],
    },
    "building_elements.doors.simple_door": {
        "id": "building_elements.doors.simple_door",
        "displayName": "Simple Door Frame",
        "description": (
            "Open-bottom door frame (jambs + head; no sill): outer box − Y-through inner "
            "opening → Preview Geometry, Voxelize → Assign Block Type → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("outer_frame", "geometry.primitives.box_from_corner_size", 0, 200, {
                "cornerX": 0.0, "cornerY": 0.0, "cornerZ": 0.0,
                "sizeX": 1.4, "sizeY": 2.4, "sizeZ": 0.25,
            }),
            node("inner_opening", "geometry.primitives.box_from_corner_size", 0, 380, {
                "cornerX": 0.2, "cornerY": -0.05, "cornerZ": -0.05,
                "sizeX": 1.0, "sizeY": 2.25, "sizeZ": 0.35,
            }),
            node("frame_cut", "geometry.boolean.difference", 320, 280),
            *placement_nodes(580, 280, 860, 860, 1120, 1380, "minecraft:oak_planks"),
        ],
        "connections": [
            conn("outer_frame", "output_geometry", "frame_cut", "input_base"),
            conn("inner_opening", "output_geometry", "frame_cut", "input_cutter"),
            conn("frame_cut", "output_geometry", "move_to_pos", "input_geometry"),
            *placement_connections("move_to_pos"),
        ],
    },
    "building_elements.windows.modern_window": {
        "id": "building_elements.windows.modern_window",
        "displayName": "Modern Window",
        "description": (
            "Facade panel → Wall With Openings − opening volumes → frame; "
            "Window Array inset glass → Combine → Preview Geometry and block preview chain."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("volume", "geometry.primitives.box_from_corner_size", 0, 200, {
                "sizeX": 2.4, "sizeY": 2.6, "sizeZ": 0.35,
            }),
            node("front_face", "reference.points.get_box_face", 280, 200, {"defaultFaceName": "front"}),
            node("opening_cols", "input.numeric.integer", 280, 380, {"value": 1}),
            node("opening_rows", "input.numeric.integer", 280, 520, {"value": 1}),
            node("opening_width", "input.numeric.float", 280, 660, {"value": 1.2}),
            node("opening_height", "input.numeric.float", 280, 800, {"value": 2.0}),
            node("opening_margin", "input.numeric.float", 280, 940, {"value": 0.5}),
            node("wall_thickness", "input.numeric.float", 280, 1080, {"value": 0.35}),
            node("glass_depth", "input.numeric.float", 280, 1220, {"value": 0.2}),
            node("wall", "geometry.architectural_primitives.wall_with_openings", 560, 200),
            node("windows", "geometry.architectural_primitives.window_array", 560, 420, {"defaultDepth": 0.2}),
            node("cut_frame", "geometry.boolean.difference", 820, 280),
            node("combine", "geometry.combine.geometry", 1080, 280),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 1340, 280),
            node("preview_geometry", "output.preview.preview_geometry", 1600, 120),
            node("voxelize", "geometry.voxel.voxelize_geometry", 1600, 320),
            node("material", "material.basic_assignment.assign_block_type", 1860, 320),
            node("preview_blocks", "output.preview.preview_blocks", 2120, 320),
        ],
        "connections": [
            conn("volume", "output_box_geometry", "front_face", "input_box_geometry"),
            conn("front_face", "output_face", "wall", "input_face"),
            conn("front_face", "output_face", "windows", "input_face"),
            conn("opening_cols", "output_value", "wall", "input_columns"),
            conn("opening_cols", "output_value", "windows", "input_columns"),
            conn("opening_rows", "output_value", "wall", "input_rows"),
            conn("opening_rows", "output_value", "windows", "input_rows"),
            conn("opening_width", "output_value", "wall", "input_opening_width"),
            conn("opening_width", "output_value", "windows", "input_window_width"),
            conn("opening_height", "output_value", "wall", "input_opening_height"),
            conn("opening_height", "output_value", "windows", "input_window_height"),
            conn("opening_margin", "output_value", "wall", "input_margin"),
            conn("opening_margin", "output_value", "windows", "input_margin"),
            conn("wall_thickness", "output_value", "wall", "input_wall_thickness"),
            conn("glass_depth", "output_value", "windows", "input_depth"),
            conn("wall", "output_geometry", "cut_frame", "input_base"),
            conn("wall", "output_openings", "cut_frame", "input_cutter"),
            conn("cut_frame", "output_geometry", "combine", "input_geometry_0"),
            conn("windows", "output_geometry", "combine", "input_geometry_1"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            *block_chain("move_to_pos"),
        ],
    },
    "building_elements.windows.arched_window": {
        "id": "building_elements.windows.arched_window",
        "displayName": "Arched Window",
        "description": (
            "Rectangle + sector profiles → extruded opening → subtract from frame box → "
            "Preview Geometry, Voxelize → Assign Block Type → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("rect_profile", "geometry.profiles.rectangle_profile", 0, 180, {
                "width": 1.0, "height": 1.2,
            }),
            node("arc_profile", "geometry.profiles.sector_profile", 0, 340, {
                "radius": 0.55, "startAngle": 0.0, "endAngle": 180.0, "segments": 24,
            }),
            node("union_profiles", "geometry.profiles.boolean_2d", 280, 240, {"operation": "UNION"}),
            node("extrude_dir", "reference.vectors.vector", 280, 420, {"x": 0.0, "y": 0.0, "z": 0.4}),
            node("extrude_opening", "geometry.solids.extrude", 560, 240),
            node("frame_box", "geometry.primitives.box_from_corner_size", 0, 520, {
                "cornerX": -0.2, "cornerY": -0.2, "cornerZ": -0.05,
                "sizeX": 1.4, "sizeY": 2.2, "sizeZ": 0.3,
            }),
            node("subtract_opening", "geometry.boolean.difference", 820, 360),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 1080, 360),
            node("preview_geometry", "output.preview.preview_geometry", 1360, 180),
            node("voxelize", "geometry.voxel.voxelize_geometry", 1360, 380),
            node("material", "material.basic_assignment.assign_block_type", 1620, 380),
            node("preview_blocks", "output.preview.preview_blocks", 1880, 380),
        ],
        "connections": [
            conn("rect_profile", "output_profile", "union_profiles", "input_profile_a"),
            conn("arc_profile", "output_profile", "union_profiles", "input_profile_b"),
            conn("union_profiles", "output_profile", "extrude_opening", "input_profile"),
            conn("extrude_dir", "output_vector", "extrude_opening", "input_direction"),
            conn("frame_box", "output_geometry", "subtract_opening", "input_base"),
            conn("extrude_opening", "output_geometry", "subtract_opening", "input_cutter"),
            conn("subtract_opening", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            *block_chain("move_to_pos"),
        ],
    },
    "building_elements.stairs.spiral_staircase": {
        "id": "building_elements.stairs.spiral_staircase",
        "displayName": "Spiral Staircase",
        "description": (
            "Path → Staircase (spiral layout) + center post → Preview Geometry, "
            "Voxelize → Assign Block Type → Preview Blocks. Procedural spiral with frozen parameters."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            local_origin_node(220, 40),
            node("tangent_vector", "reference.vectors.vector", 0, 180, {"x": 1.0, "y": 0.0, "z": 0.0}),
            node("unit_distance", "input.numeric.float", 0, 320, {"value": 2.0}),
            node("path_end", "reference.points.point_along_vector", 280, 240),
            node("point_list", "math.list.create_list", 280, 40, {"inputCount": 2}),
            node("stair_path", "geometry.curves.points_to_path", 560, 40),
            node("step_count", "input.numeric.integer", 560, 220, {"value": 18}),
            node("step_run", "input.numeric.float", 560, 360, {"value": 0.8}),
            node("step_rise", "input.numeric.float", 560, 500, {"value": 0.33}),
            node("step_width", "input.numeric.float", 560, 640, {"value": 1.0}),
            node("spiral_radius", "input.numeric.float", 560, 780, {"value": 2.5}),
            node("spiral_core_radius", "input.numeric.float", 560, 920, {"value": 0.35}),
            node("spiral_turns", "input.numeric.float", 560, 1060, {"value": 1.5}),
            node("spiral_height", "input.numeric.float", 560, 1200, {"value": 6.0}),
            node("layout", "input.values.text_input", 560, 1340, {"text": "spiral", "multiline": False}),
            node("staircase", "geometry.architectural_primitives.staircase", 860, 520),
            node("center_post", "geometry.primitives.cylinder", 860, 760, {
                "startX": 0.0, "startY": 0.0, "startZ": 0.0,
                "endX": 0.0, "endY": 6.0, "endZ": 0.0,
                "radius": 0.35,
            }),
            node("combine", "geometry.combine.geometry", 1120, 620),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 1360, 620),
            node("preview_geometry", "output.preview.preview_geometry", 1620, 460),
            node("voxelize", "geometry.voxel.voxelize_geometry", 1620, 660),
            node("material", "material.basic_assignment.assign_block_type", 1880, 660),
            node("preview_blocks", "output.preview.preview_blocks", 2140, 660),
        ],
        "connections": [
            conn("local_origin", "output_origin", "path_end", "input_point"),
            conn("tangent_vector", "output_vector", "path_end", "input_vector"),
            conn("unit_distance", "output_value", "path_end", "input_distance"),
            conn("local_origin", "output_origin", "point_list", "input_0"),
            conn("path_end", "output_point", "point_list", "input_1"),
            conn("point_list", "output_list", "stair_path", "input_points"),
            conn("stair_path", "output_path", "staircase", "input_path"),
            conn("step_count", "output_value", "staircase", "input_step_count"),
            conn("step_run", "output_value", "staircase", "input_step_run"),
            conn("step_rise", "output_value", "staircase", "input_step_rise"),
            conn("step_width", "output_value", "staircase", "input_width"),
            conn("spiral_radius", "output_value", "staircase", "input_spiral_radius"),
            conn("spiral_core_radius", "output_value", "staircase", "input_spiral_core_radius"),
            conn("spiral_turns", "output_value", "staircase", "input_spiral_turns"),
            conn("spiral_height", "output_value", "staircase", "input_spiral_height"),
            conn("layout", "output_text", "staircase", "input_layout"),
            conn("staircase", "output_geometry", "combine", "input_geometry_0"),
            conn("center_post", "output_geometry", "combine", "input_geometry_1"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            *block_chain("move_to_pos"),
        ],
    },
}


def rewrite_file(path: Path, only: set[str] | None = None) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    replaced = 0
    targets = P2_PRESETS if only is None else {k: v for k, v in P2_PRESETS.items() if k in only}
    for category in data.get("categories") or []:
        presets = category.get("presets") or []
        for i, preset in enumerate(presets):
            if not preset:
                continue
            pid = preset.get("id")
            if pid in targets:
                presets[i] = targets[pid]
                replaced += 1
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{path.name}: replaced {replaced} presets")


def main() -> None:
    parser = argparse.ArgumentParser(description="Rewrite P2 Building Elements presets in graph_presets.json")
    parser.add_argument(
        "--only",
        action="append",
        metavar="PRESET_ID",
        help="Replace only the given preset id (repeatable). Default: all P2 presets.",
    )
    args = parser.parse_args()
    only = set(args.only) if args.only else None
    if only:
        unknown = only - set(P2_PRESETS)
        if unknown:
            raise SystemExit(f"Unknown preset id(s): {', '.join(sorted(unknown))}")
    for path in PRESET_FILES:
        rewrite_file(path, only=only)


if __name__ == "__main__":
    main()
