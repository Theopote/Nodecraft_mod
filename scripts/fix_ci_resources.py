#!/usr/bin/env python3
"""Fix node_recommendations.json and graph preset port wiring for CI."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECOMMENDATIONS = ROOT / "src/main/resources/nodecraft/node_recommendations.json"
PRESET_PATHS = [
    ROOT / "src/main/resources/nodecraft/graph_presets.json",
    ROOT / "src/main/resources/nodecraft/graph_presets_updated.json",
]

BROKEN_REASON_LINES = [
    ('"reason": "变换几何', '"reason": "变换几何体",'),
    ('"reason": "烘焙为方', '"reason": "烘焙为方块",'),
    ('"reason": "檐口', '"reason": "檐口梁",'),
    ('"reason": "屋脊', '"reason": "屋脊梁",'),
    ('"reason": "在梁位放置配', '"reason": "在梁位放置配件",'),
    ('"reason": "洞口作为差集刀', '"reason": "洞口作为差集刀具",'),
    ('"reason": "自定义窗框布', '"reason": "自定义窗框布局",'),
]


def fix_recommendations_text(raw: str) -> str:
    lines = []
    for line in raw.splitlines(keepends=True):
        if '"reason": "' in line and not line.rstrip().endswith('",'):
            indent = line[: line.index('"reason"')]
            for prefix, replacement in BROKEN_REASON_LINES:
                if prefix in line:
                    line = indent + replacement + "\n"
                    break
        lines.append(line)
    return "".join(lines)


def fix_recommendations() -> None:
    text = fix_recommendations_text(
        RECOMMENDATIONS.read_bytes().decode("utf-8", errors="replace")
    )
    data = json.loads(text)
    RECOMMENDATIONS.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Fixed {RECOMMENDATIONS}")


def find_node(nodes: list[dict], ref: str) -> dict | None:
    for node in nodes:
        if node.get("ref") == ref:
            return node
    return None


def remove_node(nodes: list[dict], ref: str) -> None:
    nodes[:] = [n for n in nodes if n.get("ref") != ref]


def add_point_to_vector_adapter(
    preset: dict,
    player_ref: str = "player_pos",
    move_ref: str = "move_to_pos",
) -> None:
    nodes = preset.get("nodes") or []
    connections = preset.get("connections") or []

    move_node = find_node(nodes, move_ref)
    if move_node is None:
        return

    has_bad = any(
        c.get("fromRef") == player_ref
        and c.get("fromPort") == "output_position"
        and c.get("toRef") == move_ref
        and c.get("toPort") == "input_translation"
        for c in connections
    )
    if not has_bad:
        return

    deconstruct_ref = f"{move_ref}_point_deconstruct"
    vector_ref = f"{move_ref}_point_as_vector"

    if find_node(nodes, deconstruct_ref) is None:
        move_x = float(move_node.get("x", 0))
        move_y = float(move_node.get("y", 0))
        nodes.extend(
            [
                {
                    "ref": deconstruct_ref,
                    "typeId": "reference.points.deconstruct_point",
                    "x": move_x - 420,
                    "y": move_y,
                },
                {
                    "ref": vector_ref,
                    "typeId": "reference.vectors.construct_vector",
                    "x": move_x - 200,
                    "y": move_y,
                },
            ]
        )

    new_connections = [
        c
        for c in connections
        if not (
            c.get("fromRef") == player_ref
            and c.get("fromPort") == "output_position"
            and c.get("toRef") == move_ref
            and c.get("toPort") == "input_translation"
        )
    ]

    def ensure(conn: dict) -> None:
        if conn not in new_connections:
            new_connections.append(conn)

    ensure(
        {
            "fromRef": player_ref,
            "fromPort": "output_position",
            "toRef": deconstruct_ref,
            "toPort": "input_point",
        }
    )
    ensure(
        {
            "fromRef": deconstruct_ref,
            "fromPort": "output_x",
            "toRef": vector_ref,
            "toPort": "input_x",
        }
    )
    ensure(
        {
            "fromRef": deconstruct_ref,
            "fromPort": "output_y",
            "toRef": vector_ref,
            "toPort": "input_y",
        }
    )
    ensure(
        {
            "fromRef": deconstruct_ref,
            "fromPort": "output_z",
            "toRef": vector_ref,
            "toPort": "input_z",
        }
    )
    ensure(
        {
            "fromRef": vector_ref,
            "fromPort": "output_vector",
            "toRef": move_ref,
            "toPort": "input_translation",
        }
    )
    preset["connections"] = new_connections


def fix_point_list_to_path(preset: dict) -> None:
    nodes = preset.get("nodes") or []
    connections = preset.get("connections") or []

    point_list = find_node(nodes, "point_list")
    if point_list is None or point_list.get("typeId") != "math.list.create_list":
        return

    path_ref = None
    for candidate in ("span_path", "stair_path"):
        node = find_node(nodes, candidate)
        if node is not None and node.get("typeId") == "geometry.curves.points_to_path":
            path_ref = candidate
            break
    if path_ref is None:
        return

    start_source = None
    end_source = None
    for c in connections:
        if c.get("toRef") == "point_list" and c.get("toPort") == "input_0":
            start_source = (c.get("fromRef"), c.get("fromPort"))
        if c.get("toRef") == "point_list" and c.get("toPort") == "input_1":
            end_source = (c.get("fromRef"), c.get("fromPort"))

    path_node = find_node(nodes, path_ref)
    path_node["typeId"] = "geometry.primitives.sphere_from_diameter"
    remove_node(nodes, "point_list")

    new_connections = []
    for c in connections:
        if c.get("fromRef") == "point_list" or c.get("toRef") == "point_list":
            continue
        if c.get("toRef") == path_ref and c.get("toPort") == "input_points":
            continue
        updated = dict(c)
        if updated.get("fromRef") == path_ref and updated.get("fromPort") == "output_path":
            updated["fromPort"] = "output_diameter_line"
        new_connections.append(updated)

    if start_source and start_source[0]:
        new_connections.append(
            {
                "fromRef": start_source[0],
                "fromPort": start_source[1],
                "toRef": path_ref,
                "toPort": "input_start",
            }
        )
    if end_source and end_source[0]:
        new_connections.append(
            {
                "fromRef": end_source[0],
                "fromPort": end_source[1],
                "toRef": path_ref,
                "toPort": "input_end",
            }
        )

    preset["connections"] = new_connections


def read_json_with_utf8_repairs(path: Path) -> dict:
    raw = path.read_bytes()
    raw = raw.replace(b"\xe2\x86?", b"\xe2\x86\x92")  # →
    raw = raw.replace(b"\xe2\x88?", b"\xe2\x88\x92")  # −
    raw = raw.replace(b"\xe2\x80?", b"\xe2\x80\x93")  # –
    return json.loads(raw.decode("utf-8"))


def fix_preset_file(path: Path) -> None:
    data = read_json_with_utf8_repairs(path)
    for category in data.get("categories") or []:
        for preset in category.get("presets") or []:
            if preset.get("kind") != "composite":
                continue
            fix_point_list_to_path(preset)

            move_refs = {
                c.get("toRef")
                for c in preset.get("connections") or []
                if c.get("fromRef") == "player_pos"
                and c.get("fromPort") == "output_position"
                and c.get("toPort") == "input_translation"
            }
            for move_ref in sorted(move_refs):
                add_point_to_vector_adapter(preset, move_ref=move_ref)

    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Fixed {path}")


def main() -> None:
    fix_recommendations()
    for preset_path in PRESET_PATHS:
        fix_preset_file(preset_path)


if __name__ == "__main__":
    main()
