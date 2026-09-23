#!/usr/bin/env python3
"""Rewrite P1 architectural + building-element presets to Preset Library v2."""
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


P1_PRESETS: dict[str, dict] = {
    "architectural.residential.mini_building_v1": {
        "id": "architectural.residential.mini_building_v1",
        "displayName": "Mini Building (Component Chain)",
        "description": (
            "Floor Slab → Wall Along Path → Window Array → Roof Base → Preview Geometry, "
            "Voxelize → Assign Block Type → Preview Blocks → Apply Changes."
        ),
        "kind": "composite",
        "nodes": [
            node("volume", "geometry.primitives.box_from_corner_size", 40, 220, {
                "sizeX": 10.0, "sizeY": 3.0, "sizeZ": 8.0,
            }),
            node("floor_face", "reference.points.get_box_face", 280, 40, {"defaultFaceName": "bottom"}),
            node("front_face", "reference.points.get_box_face", 280, 260, {"defaultFaceName": "front"}),
            node("roof_face", "reference.points.get_box_face", 280, 460, {"defaultFaceName": "top"}),
            node("perimeter", "geometry.curves.face_boundary_curve", 520, 40),
            node("floor", "geometry.architectural_primitives.floor_slab", 520, 180),
            node("walls", "geometry.architectural_primitives.wall_along_path", 760, 40),
            node("windows", "geometry.architectural_primitives.window_array", 760, 260, {"defaultDepth": 0.3}),
            node("roof", "geometry.architectural_primitives.roof_base", 760, 460),
            node("combine", "geometry.combine.geometry", 1000, 240),
            node("voxelize", "geometry.voxel.voxelize_geometry", 1240, 240),
            node("material", "material.basic_assignment.assign_block_type", 1480, 240),
            node("preview_blocks", "output.preview.preview_blocks", 1720, 180),
            node("preview_geometry", "output.preview.preview_geometry", 1720, 340),
            node("apply_changes", "output.execute.apply_changes", 1720, 500),
        ],
        "connections": [
            conn("volume", "output_box_geometry", "floor_face", "input_box_geometry"),
            conn("volume", "output_box_geometry", "front_face", "input_box_geometry"),
            conn("volume", "output_box_geometry", "roof_face", "input_box_geometry"),
            conn("floor_face", "output_face", "perimeter", "input_face"),
            conn("floor_face", "output_face", "floor", "input_face"),
            conn("perimeter", "output_polyline", "walls", "input_path"),
            conn("front_face", "output_face", "windows", "input_face"),
            conn("roof_face", "output_face", "roof", "input_face"),
            conn("floor", "output_geometry", "combine", "input_geometry_0"),
            conn("walls", "output_geometry", "combine", "input_geometry_1"),
            conn("windows", "output_geometry", "combine", "input_geometry_2"),
            conn("roof", "output_geometry", "combine", "input_geometry_3"),
            conn("combine", "output_geometry", "voxelize", "input_geometry"),
            conn("combine", "output_geometry", "preview_geometry", "input_geometry"),
            conn("voxelize", "output_blocks", "material", "input_coordinates"),
            conn("material", "output_placements", "preview_blocks", "input_block_placements"),
            conn("material", "output_placements", "apply_changes", "input_block_placements"),
        ],
    },
    "building_elements.roofs.gable_roof": {
        "id": "building_elements.roofs.gable_roof",
        "displayName": "Gable Roof",
        "description": (
            "Building volume top face → Roof Base (Gable) → Preview Geometry, "
            "Voxelize → Assign Block Type → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("volume", "geometry.primitives.box_from_corner_size", 0, 200, {
                "sizeX": 10.0, "sizeY": 4.0, "sizeZ": 8.0,
            }),
            node("roof_face", "reference.points.get_box_face", 280, 200, {"defaultFaceName": "top"}),
            node("roof_height", "input.numeric.float", 280, 380, {"value": 2.5}),
            node("roof", "geometry.architectural_primitives.roof_base", 560, 260),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 820, 260),
            node("preview_geometry", "output.preview.preview_geometry", 1100, 120),
            node("voxelize", "geometry.voxel.voxelize_geometry", 1100, 320),
            node("material", "material.basic_assignment.assign_block_type", 1360, 320),
            node("preview_blocks", "output.preview.preview_blocks", 1620, 320),
        ],
        "connections": [
            conn("volume", "output_box_geometry", "roof_face", "input_box_geometry"),
            conn("roof_face", "output_face", "roof", "input_face"),
            conn("roof_height", "output_value", "roof", "input_height"),
            conn("roof", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            conn("move_to_pos", "output_geometry", "preview_geometry", "input_geometry"),
            conn("move_to_pos", "output_geometry", "voxelize", "input_geometry"),
            conn("voxelize", "output_blocks", "material", "input_coordinates"),
            conn("material", "output_placements", "preview_blocks", "input_block_placements"),
        ],
    },
    "building_elements.stairs.straight_staircase": {
        "id": "building_elements.stairs.straight_staircase",
        "displayName": "Straight Staircase",
        "description": (
            "Player Position → Path → Staircase (straight layout) → Preview Geometry, "
            "Voxelize → Assign Block Type → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node("run_vector", "reference.vectors.vector", 0, 220, {"x": 12.0, "y": 3.0, "z": 0.0}),
            node("unit_distance", "input.numeric.float", 0, 400, {"value": 1.0}),
            node("path_end", "reference.points.point_along_vector", 280, 280, {"normalizeDirection": False}),
            node("point_list", "math.list.create_list", 280, 80, {"inputCount": 2}),
            node("stair_path", "geometry.curves.points_to_path", 560, 80),
            node("step_count", "input.numeric.integer", 560, 280, {"value": 12}),
            node("step_run", "input.numeric.float", 560, 420, {"value": 1.0}),
            node("step_rise", "input.numeric.float", 560, 560, {"value": 0.25}),
            node("step_width", "input.numeric.float", 560, 700, {"value": 1.2}),
            node("layout", "input.basic.text_input", 560, 840, {"text": "straight", "multiline": False}),
            node("staircase", "geometry.architectural_primitives.staircase", 860, 280),
            node("preview_geometry", "output.preview.preview_geometry", 1160, 120),
            node("voxelize", "geometry.voxel.voxelize_geometry", 1160, 320),
            node("material", "material.basic_assignment.assign_block_type", 1420, 320),
            node("preview_blocks", "output.preview.preview_blocks", 1680, 320),
        ],
        "connections": [
            conn("player_pos", "output_position", "path_end", "input_point"),
            conn("run_vector", "output_vector", "path_end", "input_vector"),
            conn("unit_distance", "output_value", "path_end", "input_distance"),
            conn("player_pos", "output_position", "point_list", "input_0"),
            conn("path_end", "output_point", "point_list", "input_1"),
            conn("point_list", "output_list", "stair_path", "input_points"),
            conn("stair_path", "output_path", "staircase", "input_path"),
            conn("step_count", "output_value", "staircase", "input_step_count"),
            conn("step_run", "output_value", "staircase", "input_step_run"),
            conn("step_rise", "output_value", "staircase", "input_step_rise"),
            conn("step_width", "output_value", "staircase", "input_width"),
            conn("layout", "output_text", "staircase", "input_layout"),
            conn("staircase", "output_geometry", "preview_geometry", "input_geometry"),
            conn("staircase", "output_geometry", "voxelize", "input_geometry"),
            conn("voxelize", "output_blocks", "material", "input_coordinates"),
            conn("material", "output_placements", "preview_blocks", "input_block_placements"),
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
            if pid in P1_PRESETS:
                presets[i] = P1_PRESETS[pid]
                replaced += 1
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{path.name}: replaced {replaced} presets")


def main() -> None:
    for path in PRESET_FILES:
        rewrite_file(path)


if __name__ == "__main__":
    main()
