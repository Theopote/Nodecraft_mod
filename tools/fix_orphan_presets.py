#!/usr/bin/env python3
"""Fix orphan nodes in built-in graph presets."""
from __future__ import annotations

import json
from pathlib import Path

PRESET_FILE = Path("src/main/resources/nodecraft/graph_presets.json")


def find_preset(data: dict, preset_id: str) -> dict | None:
    for cat in data.get("categories") or []:
        for preset in cat.get("presets") or []:
            if preset and preset.get("id") == preset_id:
                return preset
    return None


def add_conn(preset: dict, from_ref: str, from_port: str, to_ref: str, to_port: str) -> None:
    conns = preset.setdefault("connections", [])
    for c in conns:
        if (
            c.get("fromRef") == from_ref
            and c.get("fromPort") == from_port
            and c.get("toRef") == to_ref
            and c.get("toPort") == to_port
        ):
            return
    conns.append(
        {
            "fromRef": from_ref,
            "fromPort": from_port,
            "toRef": to_ref,
            "toPort": to_port,
        }
    )


def rewire(preset: dict, from_ref: str, from_port: str, old_to_ref: str, new_to_ref: str, new_to_port: str) -> None:
    for c in preset.get("connections") or []:
        if c.get("fromRef") == from_ref and c.get("fromPort") == from_port and c.get("toRef") == old_to_ref:
            c["toRef"] = new_to_ref
            c["toPort"] = new_to_port


def ensure_node(preset: dict, ref: str, type_id: str, x: float, y: float) -> None:
    for n in preset.get("nodes") or []:
        if n.get("ref") == ref:
            n["typeId"] = type_id
            return
    preset.setdefault("nodes", []).append({"ref": ref, "typeId": type_id, "x": x, "y": y})


def fix_watchtower(preset: dict) -> None:
    # union defaults to 4 inputs; add a second stage for battlement_4
    ensure_node(preset, "union_with_battlement", "geometry.boolean.union", 750, 500)
    # Move material/move consumers off union_all onto the final union
    for c in list(preset.get("connections") or []):
        if c.get("fromRef") == "union_all" and c.get("toRef") in {"assign_material", "move_to_pos"}:
            c["fromRef"] = "union_with_battlement"
    add_conn(preset, "union_all", "output_geometry", "union_with_battlement", "input_geometry_0")
    add_conn(preset, "battlement_4", "output_geometry", "union_with_battlement", "input_geometry_1")


def fix_castle_keep(preset: dict) -> None:
    ensure_node(preset, "union_with_tower", "geometry.boolean.union", 750, 500)
    for c in list(preset.get("connections") or []):
        if c.get("fromRef") == "union_all" and c.get("toRef") in {"assign_material", "move_to_pos"}:
            c["fromRef"] = "union_with_tower"
    add_conn(preset, "union_all", "output_geometry", "union_with_tower", "input_geometry_0")
    add_conn(preset, "tower_4", "output_geometry", "union_with_tower", "input_geometry_1")


def fix_arched_window(preset: dict) -> None:
    # polygon_profile cannot merge profiles; use boolean_2d (UNION default)
    ensure_node(preset, "union_profiles", "geometry.profiles.boolean_2d", 450, 230)
    # Remove direct rect -> extrude; feed combined profile instead
    preset["connections"] = [
        c
        for c in (preset.get("connections") or [])
        if not (
            c.get("fromRef") == "rect_profile"
            and c.get("toRef") == "extrude_opening"
        )
    ]
    add_conn(preset, "rect_profile", "output_profile", "union_profiles", "input_profile_a")
    add_conn(preset, "arc_profile", "output_profile", "union_profiles", "input_profile_b")
    add_conn(preset, "union_profiles", "output_profile", "extrude_opening", "input_profile")


def main() -> None:
    data = json.loads(PRESET_FILE.read_text(encoding="utf-8-sig"))

    watchtower = find_preset(data, "architectural.infrastructure.watchtower")
    arched = find_preset(data, "building_elements.windows.arched_window")
    castle = find_preset(data, "styles.medieval.castle_keep")
    assert watchtower and arched and castle

    fix_watchtower(watchtower)
    fix_arched_window(arched)
    fix_castle_keep(castle)

    PRESET_FILE.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print("Patched:", PRESET_FILE)


if __name__ == "__main__":
    main()
