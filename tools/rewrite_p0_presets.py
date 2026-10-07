#!/usr/bin/env python3
"""Rewrite Quickstart + Composites presets to Preset Library v2 canonical chains."""
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


def placement_tail_nodes(block_type: str, move_y: float = 280) -> list[dict]:
    return [
        node("preview_geometry", "output.preview.preview_geometry", 860, 120),
        node("voxelize", "geometry.voxel.voxelize_geometry", 860, 320),
        node("material", "material.basic_assignment.assign_block_type", 1140, 320),
        node("material_block_type", "input.type_selectors.block_type_selector", 1460, 480, {
            "selectedBlock": block_type,
        }),
        node("preview_blocks", "output.preview.preview_blocks", 1420, 320),
        node("move_to_pos_point_deconstruct", "reference.points.deconstruct_point", 160, move_y),
        node("move_to_pos_point_as_vector", "reference.vectors.construct_vector", 380, move_y),
    ]


def placement_tail_conns(from_ref: str, from_port: str = "output_geometry") -> list[dict]:
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


P0_PRESETS: dict[str, dict] = {
    "composite.textured_box": {
        "id": "composite.textured_box",
        "displayName": "Box: Geometry vs Blocks",
        "description": (
            "Box geometry with Preview Geometry plus Voxelize → Assign Block Type → Preview Blocks. "
            "Shows Geometry Preview vs Block Preview. Local-space teaching preset centered at world origin."
        ),
        "kind": "composite",
        "nodes": [
            node("box", "geometry.primitives.box", 0, 120, {"sizeX": 4.0, "sizeY": 4.0, "sizeZ": 4.0}),
            node("voxelize", "geometry.voxel.voxelize_geometry", 300, 40),
            node("material", "material.basic_assignment.assign_block_type", 560, 40),
            node("preview_blocks", "output.preview.preview_blocks", 860, 40),
            node(
                "preview_geometry",
                "output.preview.preview_geometry",
                560,
                240,
                {"showOutline": True, "showFill": False},
            ),
            node(
                "material_block_type",
                "input.type_selectors.block_type_selector",
                900.0,
                320.0,
                {"selectedBlock": "minecraft:stone"},
            ),
        ],
        "connections": [
            conn("box", "output_geometry", "preview_geometry", "input_geometry"),
            conn("box", "output_geometry", "voxelize", "input_geometry"),
            conn("voxelize", "output_blocks", "material", "input_coordinates"),
            conn("material", "output_placements", "preview_blocks", "input_block_placements"),
            conn("material_block_type", "output_block_id", "material", "input_block_type"),
        ],
    },
    "composite.array_transform": {
        "id": "composite.array_transform",
        "displayName": "Array Transform",
        "description": (
            "Box → Linear Array → Transform → Preview Geometry, "
            "plus Voxelize → Preview Blocks for the transformed result."
        ),
        "kind": "composite",
        "nodes": [
            node("box", "geometry.primitives.box", 0, 120, {"sizeX": 2.0, "sizeY": 2.0, "sizeZ": 2.0}),
            node("array", "pattern.linear.linear_array", 280, 120, {"count": 4, "distance": 3.0}),
            node(
                "transform",
                "transform.basic_transforms.transform_geometry",
                560,
                120,
                {"translationX": 0.0, "translationY": 1.0, "translationZ": 0.0, "rotationY": 25.0, "scale": 1.0},
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
    "composite.boolean_cut": {
        "id": "composite.boolean_cut",
        "displayName": "Boolean Cut",
        "description": (
            "Subtracts a centered cutter from a base box. Difference is a deferred voxel boolean "
            "evaluated on the Minecraft block grid. Preview Geometry shows the continuous base and "
            "cutter; Preview Blocks shows the cut result."
        ),
        "kind": "composite",
        "nodes": [
            node("base", "geometry.primitives.box", 0, 40, {"sizeX": 8.0, "sizeY": 4.0, "sizeZ": 6.0}),
            node("cutter", "geometry.primitives.box", 0, 260, {"sizeX": 3.0, "sizeY": 5.0, "sizeZ": 3.0}),
            node("difference", "geometry.boolean.difference", 320, 140),
            node("combine", "geometry.combine.geometry", 620, 40, {"inputCount": 2}),
            node(
                "preview_geometry",
                "output.preview.preview_geometry",
                860,
                40,
                {"showOutline": True, "showFill": False},
            ),
            node("voxelize", "geometry.voxel.voxelize_geometry", 620, 240),
            node("preview_blocks", "output.preview.preview_blocks", 920, 240),
        ],
        "connections": [
            conn("base", "output_geometry", "difference", "input_base"),
            conn("cutter", "output_geometry", "difference", "input_cutter"),
            conn("base", "output_geometry", "combine", "input_geometry_0"),
            conn("cutter", "output_geometry", "combine", "input_geometry_1"),
            conn("combine", "output_geometry", "preview_geometry", "input_geometry"),
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
            "Wall Box − through-cut Gate Box (Z extends beyond both wall faces; ground-level "
            "opening with a 1-block lintel) → Move to player → Preview Geometry, Voxelize → "
            "Assign Block Type → Preview Blocks."
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
            *placement_tail_nodes("minecraft:cobblestone"),
        ],
        "connections": [
            conn("wall_box", "output_geometry", "cut_gate", "input_base"),
            conn("gate_box", "output_geometry", "cut_gate", "input_cutter"),
            conn("cut_gate", "output_geometry", "move_to_pos", "input_geometry"),
            *placement_tail_conns("move_to_pos"),
        ],
    },
    "quickstart.simple_tower": {
        "id": "quickstart.simple_tower",
        "displayName": "Hollow Tower",
        "description": (
            "Outer Cylinder − through-cut Inner Cylinder → hollow tower shell (open top and "
            "bottom) → Move to player → Preview Geometry, Voxelize → Assign Block Type → "
            "Preview Blocks."
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
            *placement_tail_nodes("minecraft:stone_bricks"),
        ],
        "connections": [
            conn("outer", "output_geometry", "hollow", "input_base"),
            conn("inner", "output_geometry", "hollow", "input_cutter"),
            conn("hollow", "output_geometry", "move_to_pos", "input_geometry"),
            *placement_tail_conns("move_to_pos"),
        ],
    },
}


def rewrite_file(path: Path, only: set[str] | None = None) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    replaced = 0
    targets = P0_PRESETS if only is None else {k: v for k, v in P0_PRESETS.items() if k in only}
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
    parser = argparse.ArgumentParser(description="Rewrite P0 Quickstart/composite presets in graph_presets.json")
    parser.add_argument(
        "--only",
        action="append",
        metavar="PRESET_ID",
        help="Replace only the given preset id (repeatable). Default: all P0 presets.",
    )
    args = parser.parse_args()
    only = set(args.only) if args.only else None
    if only:
        unknown = only - set(P0_PRESETS)
        if unknown:
            raise SystemExit(f"Unknown preset id(s): {', '.join(sorted(unknown))}")
    for path in PRESET_FILES:
        rewrite_file(path, only=only)


if __name__ == "__main__":
    main()
