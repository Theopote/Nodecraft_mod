#!/usr/bin/env python3
"""Rewrite P3 Showcase presets to Preset Library v2 (Array/Frames + architectural components)."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRESET_FILES = [
    ROOT / "src/main/resources/nodecraft/graph_presets.json",
    ROOT / "src/main/resources/nodecraft/graph_presets_updated.json",
]


def node(ref: str, type_id: str, x: float, y: float, state: dict | None = None) -> dict:
    entry = {"ref": ref, "typeId": type_id, "x": x, "y": y}
    if state is not None:
        entry["state"] = state
    return entry


def conn(fr: str, fp: str, to: str, tp: str) -> dict:
    return {"fromRef": fr, "fromPort": fp, "toRef": to, "toPort": tp}


def local_origin_node(x: float = 220, y: float = 40) -> dict:
    """Local authoring origin (0,0,0) — final placement uses Move Geometry + Player Position."""
    return node("local_origin", "reference.frames.world_frame", x, y)


def block_tail_nodes() -> list[dict]:
    return [
        node("preview_geometry", "output.preview.preview_geometry", 0, 0),
        node("voxelize", "geometry.voxel.voxelize_geometry", 0, 0),
        node("material", "material.basic_assignment.assign_block_type", 0, 0),
        node("preview_blocks", "output.preview.preview_blocks", 0, 0),
    ]


def block_tail_conns(from_ref: str, from_port: str = "output_geometry") -> list[dict]:
    return [
        conn(from_ref, from_port, "preview_geometry", "input_geometry"),
        conn(from_ref, from_port, "voxelize", "input_geometry"),
        conn("voxelize", "output_blocks", "material", "input_coordinates"),
        conn("material", "output_placements", "preview_blocks", "input_block_placements"),
    ]


def cottage_chain(
    preset_id: str,
    display_name: str,
    description: str,
    volume_state: dict,
    roof_type: str,
    window_cols: int,
    window_rows: int,
    include_doors: bool,
) -> dict:
    """Architectural component chain: floor / walls / windows [/ doors] / roof."""
    nodes = [
        node("player_pos", "input.context.player_position", 0, 40),
        node("volume", "geometry.primitives.box_from_corner_size", 0, 200, volume_state),
        node("floor_face", "reference.points.get_box_face", 280, 40, {"defaultFaceName": "bottom"}),
        node("front_face", "reference.points.get_box_face", 280, 260, {"defaultFaceName": "front"}),
        node("roof_face", "reference.points.get_box_face", 280, 480, {"defaultFaceName": "top"}),
        node("perimeter", "geometry.curves.face_boundary_curve", 520, 40),
        node("floor", "geometry.architectural_primitives.floor_slab", 520, 180),
        node("walls", "geometry.architectural_primitives.wall_along_path", 760, 40),
        node("windows", "geometry.architectural_primitives.window_array", 760, 260, {"defaultDepth": 0.3}),
        node("roof", "geometry.architectural_primitives.roof_base", 760, 480),
        node("roof_type", "input.basic.text_input", 520, 480, {"text": roof_type, "multiline": False}),
        node("roof_height", "input.numeric.float", 520, 620, {"value": 2.5}),
        node("win_cols", "input.numeric.integer", 760, 620, {"value": window_cols}),
        node("win_rows", "input.numeric.integer", 760, 760, {"value": window_rows}),
        node(
            "combine",
            "geometry.combine.geometry",
            1000,
            280,
            {"inputCount": 4 if include_doors else 4},
        ),
        *(
            [node("combine_final", "geometry.combine.geometry", 1240, 280, {"inputCount": 2})]
            if include_doors
            else []
        ),
        node(
            "move_to_pos",
            "transform.basic_transforms.move_geometry",
            1480 if include_doors else 1240,
            280,
        ),
        *block_tail_nodes(),
    ]
    connections = [
        conn("volume", "output_box_geometry", "floor_face", "input_box_geometry"),
        conn("volume", "output_box_geometry", "front_face", "input_box_geometry"),
        conn("volume", "output_box_geometry", "roof_face", "input_box_geometry"),
        conn("floor_face", "output_face", "perimeter", "input_face"),
        conn("floor_face", "output_face", "floor", "input_face"),
        conn("perimeter", "output_polyline", "walls", "input_path"),
        conn("front_face", "output_face", "windows", "input_face"),
        conn("roof_face", "output_face", "roof", "input_face"),
        conn("roof_type", "output_text", "roof", "input_roof_type"),
        conn("roof_height", "output_value", "roof", "input_height"),
        conn("win_cols", "output_value", "windows", "input_columns"),
        conn("win_rows", "output_value", "windows", "input_rows"),
        conn("floor", "output_geometry", "combine", "input_geometry_0"),
        conn("walls", "output_geometry", "combine", "input_geometry_1"),
        conn("windows", "output_geometry", "combine", "input_geometry_2"),
    ]
    if include_doors:
        nodes.insert(10, node("doors", "geometry.architectural_primitives.door_array", 760, 400))
        nodes.insert(10, node("door_cols", "input.numeric.integer", 760, 900, {"value": 1}))
        nodes.insert(10, node("door_rows", "input.numeric.integer", 760, 1040, {"value": 1}))
        connections.extend([
            conn("front_face", "output_face", "doors", "input_face"),
            conn("door_cols", "output_value", "doors", "input_columns"),
            conn("door_rows", "output_value", "doors", "input_rows"),
            conn("doors", "output_geometry", "combine", "input_geometry_3"),
            conn("combine", "output_geometry", "combine_final", "input_geometry_0"),
            conn("roof", "output_geometry", "combine_final", "input_geometry_1"),
        ])
    else:
        connections.append(conn("roof", "output_geometry", "combine", "input_geometry_3"))
    combine_out = "combine_final" if include_doors else "combine"
    connections.extend([
        conn(combine_out, "output_geometry", "move_to_pos", "input_geometry"),
        conn("player_pos", "output_position", "move_to_pos", "input_translation"),
        *block_tail_conns("move_to_pos"),
    ])
    return {
        "id": preset_id,
        "displayName": display_name,
        "description": description,
        "kind": "composite",
        "nodes": nodes,
        "connections": connections,
    }


P3_PRESETS: dict[str, dict] = {
    "architectural.residential.medieval_cottage": cottage_chain(
        "architectural.residential.medieval_cottage",
        "Medieval Cottage",
        "Building volume → Floor / Wall / Window / Door / Roof architectural chain → Preview + block preview.",
        {"sizeX": 12.0, "sizeY": 4.0, "sizeZ": 10.0},
        "gable",
        2,
        1,
        True,
    ),
    "architectural.residential.simple_house": cottage_chain(
        "architectural.residential.simple_house",
        "Simple Modern House",
        "Architectural component chain with flat roof and window array — modern residential showcase.",
        {"sizeX": 14.0, "sizeY": 4.0, "sizeZ": 10.0},
        "flat",
        2,
        1,
        False,
    ),
    "architectural.infrastructure.stone_bridge": {
        "id": "architectural.infrastructure.stone_bridge",
        "displayName": "Stone Arch Bridge",
        "description": (
            "Span path → Beam deck + Railing; pier boxes − Arch Opening → Combine → "
            "Preview Geometry, Voxelize → Material → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            local_origin_node(),
            node("run_vector", "reference.vectors.vector", 0, 180, {"x": 16.0, "y": 0.0, "z": 0.0}),
            node("unit_distance", "input.numeric.float", 0, 320, {"value": 1.0}),
            node("path_end", "reference.points.point_along_vector", 280, 240, {"normalizeDirection": False}),
            node("point_list", "math.list.create_list", 280, 40, {"inputCount": 2}),
            node("span_path", "geometry.curves.points_to_path", 560, 40),
            node("deck", "geometry.architectural_primitives.beam_along_path", 820, 40),
            node("railings", "geometry.architectural_primitives.railing", 820, 200),
            node("deck_width", "input.numeric.float", 560, 200, {"value": 4.0}),
            node("deck_height", "input.numeric.float", 560, 340, {"value": 0.8}),
            node("left_pier", "geometry.primitives.box_from_corner_size", 0, 480, {
                "cornerX": 0.0, "cornerY": 0.0, "cornerZ": -1.0,
                "sizeX": 2.5, "sizeY": 5.0, "sizeZ": 6.0,
            }),
            node("right_pier", "geometry.primitives.box_from_corner_size", 0, 660, {
                "cornerX": 13.5, "cornerY": 0.0, "cornerZ": -1.0,
                "sizeX": 2.5, "sizeY": 5.0, "sizeZ": 6.0,
            }),
            node("pier_face_l", "reference.points.get_box_face", 280, 480, {"defaultFaceName": "front"}),
            node("pier_face_r", "reference.points.get_box_face", 280, 660, {"defaultFaceName": "front"}),
            node("arch_l", "geometry.architectural_primitives.arch_opening", 560, 480),
            node("arch_r", "geometry.architectural_primitives.arch_opening", 560, 660),
            node("arch_type", "input.basic.text_input", 560, 820, {"text": "round", "multiline": False}),
            node("arch_width", "input.numeric.float", 560, 960, {"value": 2.0}),
            node("arch_height", "input.numeric.float", 560, 1100, {"value": 3.5}),
            node("cut_left", "geometry.boolean.difference", 820, 480),
            node("cut_right", "geometry.boolean.difference", 820, 660),
            node("combine", "geometry.combine.geometry", 1080, 360, {"inputCount": 4}),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 1320, 360),
            *block_tail_nodes(),
        ],
        "connections": [
            conn("local_origin", "output_origin", "path_end", "input_point"),
            conn("run_vector", "output_vector", "path_end", "input_vector"),
            conn("unit_distance", "output_value", "path_end", "input_distance"),
            conn("local_origin", "output_origin", "point_list", "input_0"),
            conn("path_end", "output_point", "point_list", "input_1"),
            conn("point_list", "output_list", "span_path", "input_points"),
            conn("span_path", "output_path", "deck", "input_path"),
            conn("span_path", "output_path", "railings", "input_path"),
            conn("deck_width", "output_value", "deck", "input_width"),
            conn("deck_height", "output_value", "deck", "input_height"),
            conn("left_pier", "output_box_geometry", "pier_face_l", "input_box_geometry"),
            conn("right_pier", "output_box_geometry", "pier_face_r", "input_box_geometry"),
            conn("pier_face_l", "output_face", "arch_l", "input_face"),
            conn("pier_face_r", "output_face", "arch_r", "input_face"),
            conn("arch_type", "output_text", "arch_l", "input_arch_type"),
            conn("arch_type", "output_text", "arch_r", "input_arch_type"),
            conn("arch_width", "output_value", "arch_l", "input_width"),
            conn("arch_width", "output_value", "arch_r", "input_width"),
            conn("arch_height", "output_value", "arch_l", "input_height"),
            conn("arch_height", "output_value", "arch_r", "input_height"),
            conn("left_pier", "output_geometry", "cut_left", "input_base"),
            conn("arch_l", "output_geometry", "cut_left", "input_cutter"),
            conn("right_pier", "output_geometry", "cut_right", "input_base"),
            conn("arch_r", "output_geometry", "cut_right", "input_cutter"),
            conn("deck", "output_geometry", "combine", "input_geometry_0"),
            conn("railings", "output_geometry", "combine", "input_geometry_1"),
            conn("cut_left", "output_geometry", "combine", "input_geometry_2"),
            conn("cut_right", "output_geometry", "combine", "input_geometry_3"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            *block_tail_conns("move_to_pos"),
        ],
    },
    "architectural.infrastructure.watchtower": {
        "id": "architectural.infrastructure.watchtower",
        "displayName": "Medieval Watchtower",
        "description": (
            "Hollow cylinder tower + Roof Base + Eave Railing + Linear Array battlement → "
            "Preview Geometry and block preview chain."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("outer", "geometry.primitives.cylinder", 0, 200, {
                "startX": 0.0, "startY": 0.0, "startZ": 0.0,
                "endX": 0.0, "endY": 14.0, "endZ": 0.0, "radius": 5.0,
            }),
            node("inner", "geometry.primitives.cylinder", 0, 380, {
                "startX": 0.0, "startY": 0.0, "startZ": 0.0,
                "endX": 0.0, "endY": 14.0, "endZ": 0.0, "radius": 4.0,
            }),
            node("hollow", "geometry.boolean.difference", 280, 280),
            node("roof_volume", "geometry.primitives.box_from_corner_size", 0, 560, {
                "cornerX": -5.0, "cornerY": 14.0, "cornerZ": -5.0,
                "sizeX": 10.0, "sizeY": 0.5, "sizeZ": 10.0,
            }),
            node("roof_face", "reference.points.get_box_face", 280, 560, {"defaultFaceName": "top"}),
            node("roof", "geometry.architectural_primitives.roof_base", 560, 560),
            node("roof_type", "input.basic.text_input", 280, 700, {"text": "shed", "multiline": False}),
            node("battlement_box", "geometry.primitives.box_from_corner_size", 0, 740, {
                "sizeX": 1.2, "sizeY": 0.8, "sizeZ": 0.8,
            }),
            node("battlement_array", "pattern.linear.linear_array_geometry", 280, 740, {
                "count": 4, "distance": 2.5,
            }),
            node("array_dir", "reference.vectors.vector", 0, 900, {"x": 1.0, "y": 0.0, "z": 0.0}),
            node("eave_railing", "geometry.architectural_primitives.railing", 560, 720),
            node("combine", "geometry.combine.geometry", 820, 480, {"inputCount": 4}),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 1060, 480),
            *block_tail_nodes(),
        ],
        "connections": [
            conn("outer", "output_geometry", "hollow", "input_base"),
            conn("inner", "output_geometry", "hollow", "input_cutter"),
            conn("roof_volume", "output_box_geometry", "roof_face", "input_box_geometry"),
            conn("roof_face", "output_face", "roof", "input_face"),
            conn("roof_type", "output_text", "roof", "input_roof_type"),
            conn("battlement_box", "output_geometry", "battlement_array", "input_geometry"),
            conn("array_dir", "output_vector", "battlement_array", "input_direction"),
            conn("roof", "output_eave_path", "eave_railing", "input_path"),
            conn("hollow", "output_geometry", "combine", "input_geometry_0"),
            conn("roof", "output_geometry", "combine", "input_geometry_1"),
            conn("eave_railing", "output_geometry", "combine", "input_geometry_2"),
            conn("battlement_array", "output_geometry", "combine", "input_geometry_3"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            *block_tail_conns("move_to_pos"),
        ],
    },
    "decorative.fountain_circular": {
        "id": "decorative.fountain_circular",
        "displayName": "Circular Fountain",
        "description": (
            "Outer basin − inner hollow + tier + center spout with explicit dimensions → "
            "Preview Geometry and block preview chain."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("outer_basin", "geometry.primitives.cylinder", 0, 180, {
                "startX": 0.0, "startY": 0.0, "startZ": 0.0,
                "endX": 0.0, "endY": 1.0, "endZ": 0.0, "radius": 5.0,
            }),
            node("inner_hollow", "geometry.primitives.cylinder", 0, 360, {
                "startX": 0.0, "startY": 0.0, "startZ": 0.0,
                "endX": 0.0, "endY": 1.2, "endZ": 0.0, "radius": 4.0,
            }),
            node("basin", "geometry.boolean.difference", 280, 260),
            node("inner_tier", "geometry.primitives.cylinder", 0, 540, {
                "startX": 0.0, "startY": 1.0, "startZ": 0.0,
                "endX": 0.0, "endY": 2.5, "endZ": 0.0, "radius": 2.5,
            }),
            node("center_spout", "geometry.primitives.cylinder", 0, 720, {
                "startX": 0.0, "startY": 1.0, "startZ": 0.0,
                "endX": 0.0, "endY": 4.0, "endZ": 0.0, "radius": 0.4,
            }),
            node("combine", "geometry.combine.geometry", 560, 420, {"inputCount": 3}),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 800, 420),
            *block_tail_nodes(),
        ],
        "connections": [
            conn("outer_basin", "output_geometry", "basin", "input_base"),
            conn("inner_hollow", "output_geometry", "basin", "input_cutter"),
            conn("basin", "output_geometry", "combine", "input_geometry_0"),
            conn("inner_tier", "output_geometry", "combine", "input_geometry_1"),
            conn("center_spout", "output_geometry", "combine", "input_geometry_2"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            *block_tail_conns("move_to_pos"),
        ],
    },
    "decorative.gazebo": {
        "id": "decorative.gazebo",
        "displayName": "Garden Gazebo",
        "description": (
            "Floor slab + Polar Array columns + Roof Base on host volume → "
            "Preview Geometry and block preview chain."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("volume", "geometry.primitives.box_from_corner_size", 0, 200, {
                "sizeX": 10.0, "sizeY": 3.5, "sizeZ": 10.0,
            }),
            node("floor_face", "reference.points.get_box_face", 280, 120, {"defaultFaceName": "bottom"}),
            node("roof_face", "reference.points.get_box_face", 280, 320, {"defaultFaceName": "top"}),
            node("floor", "geometry.architectural_primitives.floor_slab", 520, 120),
            node("column", "geometry.primitives.cylinder", 0, 480, {
                "startX": 4.0, "startY": 0.0, "startZ": 0.0,
                "endX": 4.0, "endY": 3.0, "endZ": 0.0, "radius": 0.25,
            }),
            node("column_count", "input.numeric.integer", 280, 480, {"value": 8}),
            node("column_span", "input.numeric.float", 280, 620, {"value": 360.0}),
            node("columns", "pattern.radial.polar_array_geometry", 560, 480, {"includeEnd": False}),
            node("roof", "geometry.architectural_primitives.roof_base", 520, 320),
            node("roof_type", "input.basic.text_input", 280, 760, {"text": "gable", "multiline": False}),
            node("combine", "geometry.combine.geometry", 800, 280, {"inputCount": 3}),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 1040, 280),
            *block_tail_nodes(),
        ],
        "connections": [
            conn("volume", "output_box_geometry", "floor_face", "input_box_geometry"),
            conn("volume", "output_box_geometry", "roof_face", "input_box_geometry"),
            conn("floor_face", "output_face", "floor", "input_face"),
            conn("column", "output_geometry", "columns", "input_geometry"),
            conn("column_count", "output_value", "columns", "input_count"),
            conn("column_span", "output_value", "columns", "input_total_angle"),
            conn("roof_face", "output_face", "roof", "input_face"),
            conn("roof_type", "output_text", "roof", "input_roof_type"),
            conn("floor", "output_geometry", "combine", "input_geometry_0"),
            conn("columns", "output_geometry", "combine", "input_geometry_1"),
            conn("roof", "output_geometry", "combine", "input_geometry_2"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            *block_tail_conns("move_to_pos"),
        ],
    },
    "styles.fantasy.wizard_tower": {
        "id": "styles.fantasy.wizard_tower",
        "displayName": "Wizard Tower",
        "description": (
            "Hollow cylinder shell + Cone roof + balcony ring with explicit dimensions → "
            "Preview Geometry and block preview chain."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("outer", "geometry.primitives.cylinder", 0, 200, {
                "startX": 0.0, "startY": 0.0, "startZ": 0.0,
                "endX": 0.0, "endY": 16.0, "endZ": 0.0, "radius": 4.0,
            }),
            node("inner", "geometry.primitives.cylinder", 0, 380, {
                "startX": 0.0, "startY": 0.0, "startZ": 0.0,
                "endX": 0.0, "endY": 16.0, "endZ": 0.0, "radius": 3.2,
            }),
            node("shell", "geometry.boolean.difference", 280, 280),
            node("roof", "geometry.primitives.cone", 0, 560, {
                "baseX": 0.0, "baseY": 16.0, "baseZ": 0.0,
                "apexX": 0.0, "apexY": 22.0, "apexZ": 0.0, "radius": 4.5,
            }),
            node("balcony_outer", "geometry.primitives.cylinder", 0, 740, {
                "startX": 0.0, "startY": 10.0, "startZ": 0.0,
                "endX": 0.0, "endY": 10.6, "endZ": 0.0, "radius": 5.0,
            }),
            node("balcony_inner", "geometry.primitives.cylinder", 0, 920, {
                "startX": 0.0, "startY": 10.0, "startZ": 0.0,
                "endX": 0.0, "endY": 10.8, "endZ": 0.0, "radius": 3.8,
            }),
            node("balcony", "geometry.boolean.difference", 280, 820),
            node("combine", "geometry.combine.geometry", 560, 520, {"inputCount": 3}),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 800, 520),
            *block_tail_nodes(),
        ],
        "connections": [
            conn("outer", "output_geometry", "shell", "input_base"),
            conn("inner", "output_geometry", "shell", "input_cutter"),
            conn("balcony_outer", "output_geometry", "balcony", "input_base"),
            conn("balcony_inner", "output_geometry", "balcony", "input_cutter"),
            conn("shell", "output_geometry", "combine", "input_geometry_0"),
            conn("roof", "output_geometry", "combine", "input_geometry_1"),
            conn("balcony", "output_geometry", "combine", "input_geometry_2"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            *block_tail_conns("move_to_pos"),
        ],
    },
    "styles.medieval.castle_keep": {
        "id": "styles.medieval.castle_keep",
        "displayName": "Castle Keep",
        "description": (
            "Keep body + Column Grid corner towers on footprint (not 4 duplicate boxes) → "
            "Preview Geometry and block preview chain."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("keep_body", "geometry.primitives.box_from_corner_size", 0, 200, {
                "sizeX": 12.0, "sizeY": 8.0, "sizeZ": 12.0,
            }),
            node("footprint", "geometry.primitives.box_from_corner_size", 0, 380, {
                "sizeX": 14.0, "sizeY": 0.2, "sizeZ": 14.0,
            }),
            node("footprint_top", "reference.points.get_box_face", 280, 380, {"defaultFaceName": "top"}),
            node("tower_grid", "geometry.architectural_primitives.column_grid", 560, 380),
            node("tower_cols", "input.numeric.integer", 280, 520, {"value": 2}),
            node("tower_rows", "input.numeric.integer", 280, 660, {"value": 2}),
            node("tower_height", "input.numeric.float", 280, 800, {"value": 10.0}),
            node("tower_radius", "input.numeric.float", 280, 940, {"value": 1.2}),
            node("combine", "geometry.combine.geometry", 820, 280, {"inputCount": 2}),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 1060, 280),
            *block_tail_nodes(),
        ],
        "connections": [
            conn("footprint", "output_box_geometry", "footprint_top", "input_box_geometry"),
            conn("footprint_top", "output_face", "tower_grid", "input_face"),
            conn("tower_cols", "output_value", "tower_grid", "input_columns"),
            conn("tower_rows", "output_value", "tower_grid", "input_rows"),
            conn("tower_height", "output_value", "tower_grid", "input_height"),
            conn("tower_radius", "output_value", "tower_grid", "input_radius"),
            conn("keep_body", "output_geometry", "combine", "input_geometry_0"),
            conn("tower_grid", "output_geometry", "combine", "input_geometry_1"),
            conn("combine", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            *block_tail_conns("move_to_pos"),
        ],
    },
    "styles.modern.glass_box_building": {
        "id": "styles.modern.glass_box_building",
        "displayName": "Modern Glass Box Building",
        "description": (
            "Glass shell − inner void; separate frame columns; dual Assign Block Type → "
            "Merge Placements → Preview Blocks (geometry semantics preserved)."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("outer_shell", "geometry.primitives.box_from_corner_size", 0, 200, {
                "sizeX": 14.0, "sizeY": 10.0, "sizeZ": 14.0,
            }),
            node("inner_void", "geometry.primitives.box_from_corner_size", 0, 380, {
                "cornerX": 0.5, "cornerY": 0.5, "cornerZ": 0.5,
                "sizeX": 13.0, "sizeY": 9.0, "sizeZ": 13.0,
            }),
            node("glass_shell", "geometry.boolean.difference", 280, 280),
            node("front_face", "reference.points.get_box_face", 280, 480, {"defaultFaceName": "front"}),
            node("frame_grid", "geometry.architectural_primitives.column_grid", 560, 480),
            node("frame_cols", "input.numeric.integer", 280, 620, {"value": 2}),
            node("frame_rows", "input.numeric.integer", 280, 760, {"value": 5}),
            node("frame_radius", "input.numeric.float", 280, 900, {"value": 0.15}),
            node("frame_height", "input.numeric.float", 280, 1040, {"value": 10.0}),
            node("frame_shape", "input.basic.text_input", 280, 1180, {"text": "box", "multiline": False}),
            node("combine_geo", "geometry.combine.geometry", 820, 360, {"inputCount": 2}),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 1060, 360),
            node("move_glass", "transform.basic_transforms.move_geometry", 1060, 520),
            node("move_frame", "transform.basic_transforms.move_geometry", 1060, 680),
            node("preview_geometry", "output.preview.preview_geometry", 1320, 240),
            node("voxelize_glass", "geometry.voxel.voxelize_geometry", 1320, 400),
            node("voxelize_frame", "geometry.voxel.voxelize_geometry", 1320, 560),
            node("glass_block", "input.type_selectors.block_state_selector", 1060, 720, {
                "blockId": "minecraft:light_blue_stained_glass",
            }),
            node("frame_block", "input.type_selectors.block_state_selector", 1060, 880, {
                "blockId": "minecraft:iron_block",
            }),
            node("material_glass", "material.basic_assignment.assign_block_type", 1580, 400),
            node("material_frame", "material.basic_assignment.assign_block_type", 1580, 560),
            node("merge_placements", "output.execute.merge_block_placements", 1840, 480, {"inputCount": 2}),
            node("preview_blocks", "output.preview.preview_blocks", 2100, 480),
        ],
        "connections": [
            conn("outer_shell", "output_geometry", "glass_shell", "input_base"),
            conn("inner_void", "output_geometry", "glass_shell", "input_cutter"),
            conn("outer_shell", "output_box_geometry", "front_face", "input_box_geometry"),
            conn("front_face", "output_face", "frame_grid", "input_face"),
            conn("frame_cols", "output_value", "frame_grid", "input_columns"),
            conn("frame_rows", "output_value", "frame_grid", "input_rows"),
            conn("frame_radius", "output_value", "frame_grid", "input_radius"),
            conn("frame_height", "output_value", "frame_grid", "input_height"),
            conn("frame_shape", "output_text", "frame_grid", "input_shape"),
            conn("glass_shell", "output_geometry", "combine_geo", "input_geometry_0"),
            conn("frame_grid", "output_geometry", "combine_geo", "input_geometry_1"),
            conn("combine_geo", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            conn("move_to_pos", "output_geometry", "preview_geometry", "input_geometry"),
            conn("glass_shell", "output_geometry", "move_glass", "input_geometry"),
            conn("frame_grid", "output_geometry", "move_frame", "input_geometry"),
            conn("player_pos", "output_position", "move_glass", "input_translation"),
            conn("player_pos", "output_position", "move_frame", "input_translation"),
            conn("move_glass", "output_geometry", "voxelize_glass", "input_geometry"),
            conn("move_frame", "output_geometry", "voxelize_frame", "input_geometry"),
            conn("voxelize_glass", "output_blocks", "material_glass", "input_coordinates"),
            conn("voxelize_frame", "output_blocks", "material_frame", "input_coordinates"),
            conn("glass_block", "output_block_id", "material_glass", "input_block_type"),
            conn("frame_block", "output_block_id", "material_frame", "input_block_type"),
            conn("material_glass", "output_placements", "merge_placements", "input_placements_0"),
            conn("material_frame", "output_placements", "merge_placements", "input_placements_1"),
            conn("merge_placements", "output_placements", "preview_blocks", "input_block_placements"),
        ],
    },
}


def rewrite_file(path: Path) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    replaced = 0
    for category in data.get("categories") or []:
        presets = category.get("presets") or []
        for i, preset in enumerate(presets):
            if not preset:
                continue
            pid = preset.get("id")
            if pid in P3_PRESETS:
                presets[i] = P3_PRESETS[pid]
                replaced += 1
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{path.name}: replaced {replaced} presets")


def main() -> None:
    for path in PRESET_FILES:
        rewrite_file(path)


if __name__ == "__main__":
    main()
