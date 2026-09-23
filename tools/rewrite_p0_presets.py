#!/usr/bin/env python3
"""Rewrite Quickstart + Composites presets to Preset Library v2 canonical chains."""
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


P0_PRESETS: dict[str, dict] = {
    "composite.textured_box": {
        "id": "composite.textured_box",
        "displayName": "Textured Box",
        "description": (
            "Box geometry with Preview Geometry plus Voxelize → Assign Block Type → Preview Blocks. "
            "Shows Geometry Preview vs Block Preview."
        ),
        "kind": "composite",
        "nodes": [
            node("box", "geometry.primitives.box", 0, 120, {"sizeX": 4.0, "sizeY": 4.0, "sizeZ": 4.0}),
            node("voxelize", "geometry.voxel.voxelize_geometry", 300, 40),
            node("material", "material.basic_assignment.assign_block_type", 560, 40),
            node("preview_blocks", "output.preview.preview_blocks", 860, 40),
            node("preview_geometry", "output.preview.preview_geometry", 560, 240),
        ],
        "connections": [
            conn("box", "output_geometry", "preview_geometry", "input_geometry"),
            conn("box", "output_geometry", "voxelize", "input_geometry"),
            conn("voxelize", "output_blocks", "material", "input_coordinates"),
            conn("material", "output_placements", "preview_blocks", "input_block_placements"),
        ],
    },
    "composite.array_transform_deform": {
        "id": "composite.array_transform_deform",
        "displayName": "Array Transform",
        "description": (
            "Box → Linear Array → Transform → Preview Geometry, with optional Voxelize → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("box", "geometry.primitives.box", 0, 120, {"sizeX": 2.0, "sizeY": 2.0, "sizeZ": 2.0}),
            node("array", "pattern.linear.linear_array_geometry", 280, 120, {"count": 4, "distance": 3.0}),
            node(
                "transform",
                "transform.basic_transforms.transform_geometry",
                560,
                120,
                {"translationX": 0.0, "translationY": 0.0, "translationZ": 0.0, "rotationY": 0.0, "scale": 1.0},
            ),
            node("preview_geometry", "output.preview.preview_geometry", 860, 40),
            node("voxelize", "geometry.voxel.voxelize_geometry", 860, 220),
            node("preview_blocks", "output.preview.preview_blocks", 1140, 220),
        ],
        "connections": [
            conn("box", "output_geometry", "array", "input_geometry"),
            conn("array", "output_geometry", "transform", "input_geometry"),
            conn("transform", "output_geometry", "preview_geometry", "input_geometry"),
            conn("transform", "output_geometry", "voxelize", "input_geometry"),
            conn("voxelize", "output_blocks", "preview_blocks", "input_blocks"),
        ],
    },
    "composite.boolean_cut_bake": {
        "id": "composite.boolean_cut_bake",
        "displayName": "Boolean Cut + Voxelize",
        "description": (
            "Box A + Box B → Difference → Preview Geometry and Voxelize → Preview Blocks. "
            "Demonstrates boolean cut without legacy Bake naming."
        ),
        "kind": "composite",
        "nodes": [
            node("base", "geometry.primitives.box", 0, 40, {"sizeX": 8.0, "sizeY": 4.0, "sizeZ": 6.0}),
            node("cutter", "geometry.primitives.box", 0, 260, {"sizeX": 3.0, "sizeY": 5.0, "sizeZ": 3.0}),
            node("difference", "geometry.boolean.difference", 320, 140),
            node("preview_geometry", "output.preview.preview_geometry", 620, 40),
            node("voxelize", "geometry.voxel.voxelize_geometry", 620, 240),
            node("preview_blocks", "output.preview.preview_blocks", 920, 240),
        ],
        "connections": [
            conn("base", "output_geometry", "difference", "input_base"),
            conn("cutter", "output_geometry", "difference", "input_cutter"),
            conn("difference", "output_geometry", "preview_geometry", "input_geometry"),
            conn("difference", "output_geometry", "voxelize", "input_geometry"),
            conn("voxelize", "output_blocks", "preview_blocks", "input_blocks"),
        ],
    },
    "quickstart.basic_box": {
        "id": "quickstart.basic_box",
        "displayName": "Basic Box",
        "description": (
            "Player Position → Box → Preview Geometry, and Voxelize → Assign Block Type → Preview Blocks. "
            "Canonical NodeCraft v1 quickstart chain."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 140),
            node("box", "geometry.primitives.box", 280, 140, {"sizeX": 5.0, "sizeY": 5.0, "sizeZ": 5.0}),
            node("preview_geometry", "output.preview.preview_geometry", 580, 40),
            node("voxelize", "geometry.voxel.voxelize_geometry", 580, 240),
            node("material", "material.basic_assignment.assign_block_type", 860, 240),
            node("preview_blocks", "output.preview.preview_blocks", 1140, 240),
        ],
        "connections": [
            conn("player_pos", "output_position", "box", "input_center"),
            conn("box", "output_geometry", "preview_geometry", "input_geometry"),
            conn("box", "output_geometry", "voxelize", "input_geometry"),
            conn("voxelize", "output_blocks", "material", "input_coordinates"),
            conn("material", "output_placements", "preview_blocks", "input_block_placements"),
        ],
    },
    "quickstart.basic_sphere": {
        "id": "quickstart.basic_sphere",
        "displayName": "Basic Sphere",
        "description": (
            "Player Position → Sphere → Preview Geometry, and Voxelize → Assign Block Type → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 140),
            node("sphere", "geometry.primitives.sphere", 280, 140, {"radius": 5.0}),
            node("preview_geometry", "output.preview.preview_geometry", 580, 40),
            node("voxelize", "geometry.voxel.voxelize_geometry", 580, 240),
            node("material", "material.basic_assignment.assign_block_type", 860, 240),
            node("preview_blocks", "output.preview.preview_blocks", 1140, 240),
        ],
        "connections": [
            conn("player_pos", "output_position", "sphere", "input_center"),
            conn("sphere", "output_geometry", "preview_geometry", "input_geometry"),
            conn("sphere", "output_geometry", "voxelize", "input_geometry"),
            conn("voxelize", "output_blocks", "material", "input_coordinates"),
            conn("material", "output_placements", "preview_blocks", "input_block_placements"),
        ],
    },
    "quickstart.garden_wall": {
        "id": "quickstart.garden_wall",
        "displayName": "Garden Wall with Gate",
        "description": (
            "Wall Box − Gate Cutter → Move to player → Preview Geometry, "
            "and Voxelize → Assign Block Type → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node(
                "wall_box",
                "geometry.primitives.box_from_corner_size",
                0,
                200,
                {"sizeX": 12.0, "sizeY": 4.0, "sizeZ": 1.0, "cornerX": 0.0, "cornerY": 0.0, "cornerZ": 0.0},
            ),
            node(
                "gate_box",
                "geometry.primitives.box_from_corner_size",
                0,
                400,
                {"sizeX": 3.0, "sizeY": 3.0, "sizeZ": 2.0, "cornerX": 4.5, "cornerY": 0.0, "cornerZ": -0.5},
            ),
            node("cut_gate", "geometry.boolean.difference", 320, 280),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 580, 280),
            node("preview_geometry", "output.preview.preview_geometry", 860, 120),
            node("voxelize", "geometry.voxel.voxelize_geometry", 860, 320),
            node("material", "material.basic_assignment.assign_block_type", 1140, 320),
            node("preview_blocks", "output.preview.preview_blocks", 1420, 320),
        ],
        "connections": [
            conn("wall_box", "output_geometry", "cut_gate", "input_base"),
            conn("gate_box", "output_geometry", "cut_gate", "input_cutter"),
            conn("cut_gate", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            conn("move_to_pos", "output_geometry", "preview_geometry", "input_geometry"),
            conn("move_to_pos", "output_geometry", "voxelize", "input_geometry"),
            conn("voxelize", "output_blocks", "material", "input_coordinates"),
            conn("material", "output_placements", "preview_blocks", "input_block_placements"),
        ],
    },
    "quickstart.simple_tower": {
        "id": "quickstart.simple_tower",
        "displayName": "Simple Tower",
        "description": (
            "Outer − Inner Cylinder → Move to player → Preview Geometry, "
            "and Voxelize → Assign Block Type → Preview Blocks."
        ),
        "kind": "composite",
        "nodes": [
            node("player_pos", "input.context.player_position", 0, 40),
            node(
                "outer",
                "geometry.primitives.cylinder",
                0,
                200,
                {
                    "startX": 0.0,
                    "startY": 0.0,
                    "startZ": 0.0,
                    "endX": 0.0,
                    "endY": 12.0,
                    "endZ": 0.0,
                    "radius": 4.0,
                },
            ),
            node(
                "inner",
                "geometry.primitives.cylinder",
                0,
                400,
                {
                    "startX": 0.0,
                    "startY": 0.0,
                    "startZ": 0.0,
                    "endX": 0.0,
                    "endY": 12.0,
                    "endZ": 0.0,
                    "radius": 3.0,
                },
            ),
            node("hollow", "geometry.boolean.difference", 320, 280),
            node("move_to_pos", "transform.basic_transforms.move_geometry", 580, 280),
            node("preview_geometry", "output.preview.preview_geometry", 860, 120),
            node("voxelize", "geometry.voxel.voxelize_geometry", 860, 320),
            node("material", "material.basic_assignment.assign_block_type", 1140, 320),
            node("preview_blocks", "output.preview.preview_blocks", 1420, 320),
        ],
        "connections": [
            conn("outer", "output_geometry", "hollow", "input_base"),
            conn("inner", "output_geometry", "hollow", "input_cutter"),
            conn("hollow", "output_geometry", "move_to_pos", "input_geometry"),
            conn("player_pos", "output_position", "move_to_pos", "input_translation"),
            conn("move_to_pos", "output_geometry", "preview_geometry", "input_geometry"),
            conn("move_to_pos", "output_geometry", "voxelize", "input_geometry"),
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
            if pid in P0_PRESETS:
                presets[i] = P0_PRESETS[pid]
                replaced += 1
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{path.name}: replaced {replaced} presets")


def main() -> None:
    for path in PRESET_FILES:
        rewrite_file(path)


if __name__ == "__main__":
    main()
