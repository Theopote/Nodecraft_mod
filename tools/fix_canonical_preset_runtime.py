#!/usr/bin/env python3
"""Fix canonical presets: Block Type Selector + Mini Building Player→Move chain."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRAPH_FILE = ROOT / "src/main/resources/nodecraft/graph_presets.json"

BLOCK_BY_PRESET: dict[str, str] = {
    "architectural.residential.mini_building_v1": "minecraft:stone_bricks",
    "architectural.residential.simple_house": "minecraft:smooth_quartz",
    "architectural.residential.medieval_cottage": "minecraft:oak_planks",
    "architectural.infrastructure.stone_bridge": "minecraft:stone_bricks",
    "architectural.infrastructure.watchtower": "minecraft:stone_bricks",
    "styles.medieval.castle_keep": "minecraft:stone_bricks",
    "styles.fantasy.wizard_tower": "minecraft:stone_bricks",
    "styles.modern.glass_box_building": "minecraft:stone",  # unused if already wired
    "quickstart.basic_box": "minecraft:stone",
    "quickstart.basic_sphere": "minecraft:stone",
    "quickstart.garden_wall": "minecraft:cobblestone",
    "quickstart.simple_tower": "minecraft:stone_bricks",
    "building_elements.roofs.gable_roof": "minecraft:spruce_planks",
    "building_elements.stairs.straight_staircase": "minecraft:stone_bricks",
    "building_elements.stairs.spiral_staircase": "minecraft:stone_bricks",
    "building_elements.columns.classical_column": "minecraft:smooth_quartz",
    "building_elements.doors.simple_door": "minecraft:oak_planks",
    "building_elements.windows.modern_window": "minecraft:glass",
    "building_elements.windows.arched_window": "minecraft:glass",
    "decorative.fountain_circular": "minecraft:stone_bricks",
    "decorative.gazebo": "minecraft:oak_planks",
    "composite.textured_box": "minecraft:stone",
    "composite.array_transform_deform": "minecraft:stone",
    "composite.boolean_cut_bake": "minecraft:stone",
}

DEFAULT_BLOCK = "minecraft:stone"
ASSIGN_TYPE = "material.basic_assignment.assign_block_type"
SELECTOR_TYPE = "input.type_selectors.block_type_selector"


def material_refs(preset: dict) -> list[str]:
    return [
        n["ref"]
        for n in preset.get("nodes") or []
        if n and n.get("typeId") == ASSIGN_TYPE and n.get("ref")
    ]


def has_block_type_in(preset: dict, material_ref: str) -> bool:
    for c in preset.get("connections") or []:
        if c and c.get("toRef") == material_ref and c.get("toPort") == "input_block_type":
            return True
    return False


def max_xy(preset: dict) -> tuple[float, float]:
    xs, ys = [0.0], [0.0]
    for n in preset.get("nodes") or []:
        if n is None:
            continue
        xs.append(float(n.get("x") or 0))
        ys.append(float(n.get("y") or 0))
    return max(xs), max(ys)


def ensure_block_type_selectors(preset: dict) -> int:
    added = 0
    preset_id = preset.get("id") or ""
    block = BLOCK_BY_PRESET.get(preset_id, DEFAULT_BLOCK)
    nodes = preset.setdefault("nodes", [])
    connections = preset.setdefault("connections", [])
    existing_refs = {n["ref"] for n in nodes if n and n.get("ref")}

    for mat_ref in material_refs(preset):
        if has_block_type_in(preset, mat_ref):
            continue
        sel_ref = f"{mat_ref}_block_type"
        if sel_ref in existing_refs:
            # Already present but maybe not connected
            if not has_block_type_in(preset, mat_ref):
                connections.append(
                    {
                        "fromRef": sel_ref,
                        "fromPort": "output_block_id",
                        "toRef": mat_ref,
                        "toPort": "input_block_type",
                    }
                )
                added += 1
            continue

        mx, my = max_xy(preset)
        nodes.append(
            {
                "ref": sel_ref,
                "typeId": SELECTOR_TYPE,
                "x": mx + 40,
                "y": my + 80 + 40 * added,
                "state": {"selectedBlock": block},
            }
        )
        existing_refs.add(sel_ref)
        connections.append(
            {
                "fromRef": sel_ref,
                "fromPort": "output_block_id",
                "toRef": mat_ref,
                "toPort": "input_block_type",
            }
        )
        added += 1
    return added


def rewire_connection(preset: dict, from_ref: str, from_port: str, to_ref: str, to_port: str,
                      new_from_ref: str, new_from_port: str) -> bool:
    for c in preset.get("connections") or []:
        if (
            c
            and c.get("fromRef") == from_ref
            and c.get("fromPort") == from_port
            and c.get("toRef") == to_ref
            and c.get("toPort") == to_port
        ):
            c["fromRef"] = new_from_ref
            c["fromPort"] = new_from_port
            return True
    return False


def fix_mini_building(preset: dict) -> None:
    nodes = preset.setdefault("nodes", [])
    connections = preset.setdefault("connections", [])
    refs = {n["ref"] for n in nodes if n and n.get("ref")}

    preset["description"] = (
        "Floor → Wall Along Path → Difference (Window openings) → Window Frame on frames → Roof "
        "→ Move to Player → Voxelize → Material → Preview/Apply."
    )

    def add_node(ref: str, type_id: str, x: float, y: float, state: dict | None = None) -> None:
        if ref in refs:
            return
        entry = {"ref": ref, "typeId": type_id, "x": x, "y": y}
        if state is not None:
            entry["state"] = state
        nodes.append(entry)
        refs.add(ref)

    add_node("player_pos", "input.context.player_position", 0, 40)
    add_node("move_to_pos", "transform.basic_transforms.move_geometry", 1120, 240)
    add_node(
        "move_to_pos_point_deconstruct",
        "reference.points.deconstruct_point",
        820,
        280,
    )
    add_node(
        "move_to_pos_point_as_vector",
        "reference.vectors.construct_vector",
        1040,
        280,
    )

    # combine → move instead of combine → voxelize / preview_geometry
    rewire_connection(
        preset, "combine", "output_geometry", "voxelize", "input_geometry",
        "move_to_pos", "output_geometry",
    )
    rewire_connection(
        preset, "combine", "output_geometry", "preview_geometry", "input_geometry",
        "move_to_pos", "output_geometry",
    )

    def has_conn(fr: str, fp: str, tr: str, tp: str) -> bool:
        return any(
            c
            and c.get("fromRef") == fr
            and c.get("fromPort") == fp
            and c.get("toRef") == tr
            and c.get("toPort") == tp
            for c in connections
        )

    def ensure_conn(fr: str, fp: str, tr: str, tp: str) -> None:
        if not has_conn(fr, fp, tr, tp):
            connections.append(
                {"fromRef": fr, "fromPort": fp, "toRef": tr, "toPort": tp}
            )

    ensure_conn("combine", "output_geometry", "move_to_pos", "input_geometry")
    ensure_conn("player_pos", "output_position", "move_to_pos_point_deconstruct", "input_point")
    ensure_conn("move_to_pos_point_deconstruct", "output_x", "move_to_pos_point_as_vector", "input_x")
    ensure_conn("move_to_pos_point_deconstruct", "output_y", "move_to_pos_point_as_vector", "input_y")
    ensure_conn("move_to_pos_point_deconstruct", "output_z", "move_to_pos_point_as_vector", "input_z")
    ensure_conn("move_to_pos_point_as_vector", "output_vector", "move_to_pos", "input_translation")
    ensure_conn("move_to_pos", "output_geometry", "voxelize", "input_geometry")
    ensure_conn("move_to_pos", "output_geometry", "preview_geometry", "input_geometry")


def main() -> None:
    data = json.loads(GRAPH_FILE.read_text(encoding="utf-8"))
    total_selectors = 0
    mini_fixed = False

    for category in data.get("categories") or []:
        for preset in category.get("presets") or []:
            if not preset or preset.get("kind") != "composite":
                continue
            if preset.get("id") == "architectural.residential.mini_building_v1":
                fix_mini_building(preset)
                mini_fixed = True
            total_selectors += ensure_block_type_selectors(preset)

    GRAPH_FILE.write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(f"mini_building fixed: {mini_fixed}")
    print(f"block type selector connections added/ensured: {total_selectors}")


if __name__ == "__main__":
    main()
