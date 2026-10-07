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


def player_move_adapters(move_ref: str, x: float, y: float) -> tuple[list[dict], list[dict]]:
    """Per-move POINT→VECTOR adapters so coordinate audit counts each Move as player-anchored."""
    deconstruct = f"{move_ref}_point_deconstruct"
    vector = f"{move_ref}_point_as_vector"
    nodes = [
        node(deconstruct, "reference.points.deconstruct_point", x, y),
        node(vector, "reference.vectors.construct_vector", x + 220, y),
    ]
    connections = [
        conn("player_pos", "output_position", deconstruct, "input_point"),
        conn(deconstruct, "output_x", vector, "input_x"),
        conn(deconstruct, "output_y", vector, "input_y"),
        conn(deconstruct, "output_z", vector, "input_z"),
        conn(vector, "output_vector", move_ref, "input_translation"),
    ]
    return nodes, connections


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
            "Wall Slab host → Difference (Window Array openings; depth = 2× wall thickness) "
            "+ Window Frame + glass pane on frames → Combine preview; triple material "
            "(wall / frame / glass) → Merge → Preview Blocks."
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
            node("depth_factor", "input.numeric.float", 280, 1220, {"value": 2.0}),
            node("opening_depth", "math.scalar_math.multiplication", 520, 1080),
            node("glass_thickness", "input.numeric.float", 280, 1360, {"value": 0.08}),
            node("wall", "geometry.architectural_primitives.wall_slab", 560, 60),
            node("windows", "geometry.architectural_primitives.window_array", 560, 300),
            node("window_frame", "geometry.architectural_primitives.window_frame", 560, 480),
            node("glass_pane", "geometry.primitives.box", 560, 660, {
                "sizeX": 1.2, "sizeY": 2.0, "sizeZ": 0.08,
            }),
            node("cut", "geometry.boolean.difference", 820, 60),
            node("place_frames", "transform.placement.place_geometry_on_frames", 820, 300),
            node("place_glass", "transform.placement.place_geometry_on_frames", 820, 480),
            node("combine", "geometry.combine.geometry", 1080, 240),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 1340, 120),
            node("move_wall", "transform.basic_transforms.move_geometry", 1340, 280),
            node("move_frame", "transform.basic_transforms.move_geometry", 1340, 440),
            node("move_glass", "transform.basic_transforms.move_geometry", 1340, 600),
            *player_move_adapters("move_to_pos", 1080, 40)[0],
            *player_move_adapters("move_wall", 1080, 200)[0],
            *player_move_adapters("move_frame", 1080, 360)[0],
            *player_move_adapters("move_glass", 1080, 520)[0],
            node("preview_geometry", "output.preview.preview_geometry", 1600, 80),
            node("voxelize_wall", "geometry.voxel.voxelize_geometry", 1600, 240),
            node("voxelize_frame", "geometry.voxel.voxelize_geometry", 1600, 400),
            node("voxelize_glass", "geometry.voxel.voxelize_geometry", 1600, 560),
            node("wall_block", "input.type_selectors.block_type_selector", 1600, 700, {
                "selectedBlock": "minecraft:white_concrete",
            }),
            node("frame_block", "input.type_selectors.block_type_selector", 1600, 840, {
                "selectedBlock": "minecraft:iron_block",
            }),
            node("glass_block", "input.type_selectors.block_type_selector", 1600, 980, {
                "selectedBlock": "minecraft:glass",
            }),
            node("material_wall", "material.basic_assignment.assign_block_type", 1860, 240),
            node("material_frame", "material.basic_assignment.assign_block_type", 1860, 400),
            node("material_glass", "material.basic_assignment.assign_block_type", 1860, 560),
            node("merge_placements", "output.execute.merge_block_placements", 2120, 400, {
                "inputCount": 3,
            }),
            node("preview_blocks", "output.preview.preview_blocks", 2380, 400),
        ],
        "connections": [
            conn("volume", "output_box_geometry", "front_face", "input_box_geometry"),
            conn("front_face", "output_face", "wall", "input_face"),
            conn("front_face", "output_face", "windows", "input_face"),
            conn("wall_thickness", "output_value", "wall", "input_wall_thickness"),
            conn("wall_thickness", "output_value", "opening_depth", "input_a"),
            conn("depth_factor", "output_value", "opening_depth", "input_b"),
            conn("opening_depth", "output_product", "windows", "input_depth"),
            conn("opening_cols", "output_value", "windows", "input_columns"),
            conn("opening_rows", "output_value", "windows", "input_rows"),
            conn("opening_width", "output_value", "windows", "input_window_width"),
            conn("opening_height", "output_value", "windows", "input_window_height"),
            conn("opening_margin", "output_value", "windows", "input_margin"),
            conn("opening_width", "output_value", "window_frame", "input_frame_width"),
            conn("opening_height", "output_value", "window_frame", "input_frame_height"),
            conn("opening_width", "output_value", "glass_pane", "input_size_x"),
            conn("opening_height", "output_value", "glass_pane", "input_size_y"),
            conn("glass_thickness", "output_value", "glass_pane", "input_size_z"),
            conn("wall", "output_geometry", "cut", "input_base"),
            conn("windows", "output_openings", "cut", "input_cutter"),
            conn("windows", "output_frames", "place_frames", "input_frames"),
            conn("window_frame", "output_geometry", "place_frames", "input_geometry"),
            conn("windows", "output_frames", "place_glass", "input_frames"),
            conn("glass_pane", "output_geometry", "place_glass", "input_geometry"),
            conn("cut", "output_geometry", "combine", "input_geometry_0"),
            conn("place_frames", "output_geometry", "combine", "input_geometry_1"),
            conn("place_glass", "output_geometry", "combine", "input_geometry_2"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            conn("cut", "output_geometry", "move_wall", "input_geometry"),
            conn("place_frames", "output_geometry", "move_frame", "input_geometry"),
            conn("place_glass", "output_geometry", "move_glass", "input_geometry"),
            *player_move_adapters("move_to_pos", 0, 0)[1],
            *player_move_adapters("move_wall", 0, 0)[1],
            *player_move_adapters("move_frame", 0, 0)[1],
            *player_move_adapters("move_glass", 0, 0)[1],
            conn("move_to_pos", "output_geometry", "preview_geometry", "input_geometry"),
            conn("move_wall", "output_geometry", "voxelize_wall", "input_geometry"),
            conn("move_frame", "output_geometry", "voxelize_frame", "input_geometry"),
            conn("move_glass", "output_geometry", "voxelize_glass", "input_geometry"),
            conn("voxelize_wall", "output_blocks", "material_wall", "input_coordinates"),
            conn("voxelize_frame", "output_blocks", "material_frame", "input_coordinates"),
            conn("voxelize_glass", "output_blocks", "material_glass", "input_coordinates"),
            conn("wall_block", "output_block_id", "material_wall", "input_block_type"),
            conn("frame_block", "output_block_id", "material_frame", "input_block_type"),
            conn("glass_block", "output_block_id", "material_glass", "input_block_type"),
            conn("material_wall", "output_placements", "merge_placements", "input_placements_0"),
            conn("material_frame", "output_placements", "merge_placements", "input_placements_1"),
            conn("material_glass", "output_placements", "merge_placements", "input_placements_2"),
            conn("merge_placements", "output_placements", "preview_blocks", "input_block_placements"),
        ],
    },
    "building_elements.windows.arched_window": {
        "id": "building_elements.windows.arched_window",
        "displayName": "Arched Window",
        "description": (
            "XY facade plane: rectangle + semicircle on rect top → UNION → Extrude cutter "
            "for frame Difference + short Extrude glass → Combine preview; dual material "
            "(smooth_quartz frame / glass) → Merge → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("facade_plane", "reference.planes.world_plane", 0, 120, {
                "planePreset": "XY", "originX": 0.0, "originY": 0.0, "originZ": 0.0,
            }),
            node("zero", "input.numeric.float", 0, 280, {"value": 0.0}),
            node("rect_cy", "input.numeric.float", 0, 400, {"value": 0.6}),
            node("arc_cy", "input.numeric.float", 0, 520, {"value": 1.2}),
            node("rect_center", "reference.points.construct_point", 220, 280),
            node("arc_center", "reference.points.construct_point", 220, 480),
            node("rect_profile", "geometry.profiles.rectangle_profile", 460, 200, {
                "width": 1.0, "height": 1.2,
            }),
            node("arc_profile", "geometry.profiles.sector_profile", 460, 400, {
                "radius": 0.5, "startAngle": 0.0, "endAngle": 180.0, "segments": 24,
            }),
            node("union_profiles", "geometry.profiles.boolean_2d", 720, 280, {"operation": "UNION"}),
            node("extrude_dir", "reference.vectors.vector", 720, 460, {"x": 0.0, "y": 0.0, "z": 0.4}),
            node("glass_dir", "reference.vectors.vector", 720, 600, {"x": 0.0, "y": 0.0, "z": 0.08}),
            node("extrude_opening", "geometry.solids.extrude", 980, 200),
            node("extrude_glass", "geometry.solids.extrude", 980, 400),
            node("frame_box", "geometry.primitives.box_from_corner_size", 460, 640, {
                "cornerX": -0.2, "cornerY": -0.2, "cornerZ": 0.0,
                "sizeX": 1.4, "sizeY": 2.2, "sizeZ": 0.3,
            }),
            node("subtract_opening", "geometry.boolean.difference", 1240, 280),
            node("combine", "geometry.combine.geometry", 1500, 320),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 1760, 200),
            node("move_frame", "transform.basic_transforms.move_geometry", 1760, 360),
            node("move_glass", "transform.basic_transforms.move_geometry", 1760, 520),
            *player_move_adapters("move_to_pos", 1500, 40)[0],
            *player_move_adapters("move_frame", 1500, 200)[0],
            *player_move_adapters("move_glass", 1500, 360)[0],
            node("preview_geometry", "output.preview.preview_geometry", 2020, 120),
            node("voxelize_frame", "geometry.voxel.voxelize_geometry", 2020, 320),
            node("voxelize_glass", "geometry.voxel.voxelize_geometry", 2020, 480),
            node("frame_block", "input.type_selectors.block_type_selector", 2020, 640, {
                "selectedBlock": "minecraft:smooth_quartz",
            }),
            node("glass_block", "input.type_selectors.block_type_selector", 2020, 780, {
                "selectedBlock": "minecraft:glass",
            }),
            node("material_frame", "material.basic_assignment.assign_block_type", 2280, 320),
            node("material_glass", "material.basic_assignment.assign_block_type", 2280, 480),
            node("merge_placements", "output.execute.merge_block_placements", 2540, 400, {
                "inputCount": 2,
            }),
            node("preview_blocks", "output.preview.preview_blocks", 2800, 400),
        ],
        "connections": [
            conn("zero", "output_value", "rect_center", "input_x"),
            conn("rect_cy", "output_value", "rect_center", "input_y"),
            conn("zero", "output_value", "rect_center", "input_z"),
            conn("zero", "output_value", "arc_center", "input_x"),
            conn("arc_cy", "output_value", "arc_center", "input_y"),
            conn("zero", "output_value", "arc_center", "input_z"),
            conn("facade_plane", "output_plane", "rect_profile", "input_plane"),
            conn("facade_plane", "output_plane", "arc_profile", "input_plane"),
            conn("rect_center", "output_point", "rect_profile", "input_center"),
            conn("arc_center", "output_point", "arc_profile", "input_center"),
            conn("rect_profile", "output_profile", "union_profiles", "input_profile_a"),
            conn("arc_profile", "output_profile", "union_profiles", "input_profile_b"),
            conn("union_profiles", "output_profile", "extrude_opening", "input_profile"),
            conn("extrude_dir", "output_vector", "extrude_opening", "input_direction"),
            conn("union_profiles", "output_profile", "extrude_glass", "input_profile"),
            conn("glass_dir", "output_vector", "extrude_glass", "input_direction"),
            conn("frame_box", "output_geometry", "subtract_opening", "input_base"),
            conn("extrude_opening", "output_geometry", "subtract_opening", "input_cutter"),
            conn("subtract_opening", "output_geometry", "combine", "input_geometry_0"),
            conn("extrude_glass", "output_geometry", "combine", "input_geometry_1"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            conn("subtract_opening", "output_geometry", "move_frame", "input_geometry"),
            conn("extrude_glass", "output_geometry", "move_glass", "input_geometry"),
            *player_move_adapters("move_to_pos", 0, 0)[1],
            *player_move_adapters("move_frame", 0, 0)[1],
            *player_move_adapters("move_glass", 0, 0)[1],
            conn("move_to_pos", "output_geometry", "preview_geometry", "input_geometry"),
            conn("move_frame", "output_geometry", "voxelize_frame", "input_geometry"),
            conn("move_glass", "output_geometry", "voxelize_glass", "input_geometry"),
            conn("voxelize_frame", "output_blocks", "material_frame", "input_coordinates"),
            conn("voxelize_glass", "output_blocks", "material_glass", "input_coordinates"),
            conn("frame_block", "output_block_id", "material_frame", "input_block_type"),
            conn("glass_block", "output_block_id", "material_glass", "input_block_type"),
            conn("material_frame", "output_placements", "merge_placements", "input_placements_0"),
            conn("material_glass", "output_placements", "merge_placements", "input_placements_1"),
            conn("merge_placements", "output_placements", "preview_blocks", "input_block_placements"),
        ],
    },
    "building_elements.stairs.spiral_staircase": {
        "id": "building_elements.stairs.spiral_staircase",
        "displayName": "Spiral Staircase",
        "description": (
            "Local origin tangent path → Staircase (spiral) + center post driven by the same "
            "spiral_height / spiral_core_radius → Preview Geometry, Voxelize → Assign Block Type "
            "→ Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            local_origin_node(220, 40),
            node("world_origin_deconstruct", "reference.frames.deconstruct_frame", 220, 120),
            node("tangent_vector", "reference.vectors.vector", 0, 180, {"x": 1.0, "y": 0.0, "z": 0.0}),
            node("unit_distance", "input.numeric.float", 0, 320, {"value": 2.0}),
            node("path_end", "reference.points.point_along_vector", 280, 240),
            node("up_vector", "reference.vectors.vector", 0, 460, {"x": 0.0, "y": 1.0, "z": 0.0}),
            node("post_end", "reference.points.point_along_vector", 280, 460),
            # Diameter Path is the typed two-POINT → PATH bridge (create_list is LIST, not POINT_LIST).
            node("stair_path", "geometry.primitives.sphere_from_diameter", 560, 40),
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
            node("center_post", "geometry.primitives.cylinder", 860, 760),
            node("combine", "geometry.combine.geometry", 1120, 620),
            *placement_nodes(1360, 620, 1620, 1620, 1880, 2140, "minecraft:stone_bricks"),
        ],
        "connections": [
            conn("local_origin", "output_frame", "world_origin_deconstruct", "input_frame"),
            conn("world_origin_deconstruct", "output_origin", "path_end", "input_point"),
            conn("tangent_vector", "output_vector", "path_end", "input_vector"),
            conn("unit_distance", "output_value", "path_end", "input_distance"),
            conn("world_origin_deconstruct", "output_origin", "stair_path", "input_start"),
            conn("path_end", "output_point", "stair_path", "input_end"),
            conn("stair_path", "output_diameter_path", "staircase", "input_path"),
            conn("world_origin_deconstruct", "output_origin", "post_end", "input_point"),
            conn("up_vector", "output_vector", "post_end", "input_vector"),
            conn("spiral_height", "output_value", "post_end", "input_distance"),
            conn("world_origin_deconstruct", "output_origin", "center_post", "input_start"),
            conn("post_end", "output_point", "center_post", "input_end"),
            conn("spiral_core_radius", "output_value", "center_post", "input_radius"),
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
            *placement_connections("move_to_pos"),
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
