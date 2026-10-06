#!/usr/bin/env python3
"""Fix canonical preset geometric scale mismatches vs node contracts."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRAPH_FILE = ROOT / "src/main/resources/nodecraft/graph_presets.json"


def find_preset(data: dict, preset_id: str) -> dict:
    for category in data.get("categories") or []:
        for preset in category.get("presets") or []:
            if preset.get("id") == preset_id:
                return preset
    raise KeyError(preset_id)


def node_by_ref(preset: dict, ref: str) -> dict:
    for node in preset.get("nodes") or []:
        if node.get("ref") == ref:
            return node
    raise KeyError(ref)


def insert_after(preset: dict, after_ref: str, new_node: dict) -> None:
    nodes = preset["nodes"]
    for index, node in enumerate(nodes):
        if node.get("ref") == after_ref:
            nodes.insert(index + 1, new_node)
            return
    nodes.append(new_node)


def replace_conn_from(preset: dict, from_ref: str, from_port: str, to_ref: str, to_port: str, new_from: str, new_from_port: str) -> None:
    for conn in preset["connections"]:
        if (
            conn.get("fromRef") == from_ref
            and conn.get("fromPort") == from_port
            and conn.get("toRef") == to_ref
            and conn.get("toPort") == to_port
        ):
            conn["fromRef"] = new_from
            conn["fromPort"] = new_from_port
            return
    raise KeyError((from_ref, from_port, to_ref, to_port))


def add_conns(preset: dict, extra: list[dict]) -> None:
    preset["connections"].extend(extra)


def patch_stone_bridge(preset: dict) -> None:
    """No-op when central-arch topology is present; legacy pier presets are rewritten via rewrite_p3_presets.py."""
    if node_by_ref(preset, "bridge_body") is not None:
        return
    preset["nodes"] = [
        n
        for n in preset["nodes"]
        if n.get("ref") not in {"local_origin", "world_origin_deconstruct"}
    ]
    insert_after(
        preset,
        "player_pos",
        {
            "ref": "span_x",
            "typeId": "input.numeric.float",
            "x": 0,
            "y": 80,
            "state": {"value": 0.0},
        },
    )
    insert_after(
        preset,
        "span_x",
        {
            "ref": "deck_elevation",
            "typeId": "input.numeric.float",
            "x": 0,
            "y": 140,
            "state": {"value": 5.0},
        },
    )
    insert_after(
        preset,
        "deck_elevation",
        {
            "ref": "span_z",
            "typeId": "input.numeric.float",
            "x": 0,
            "y": 200,
            "state": {"value": 0.0},
        },
    )
    insert_after(
        preset,
        "span_z",
        {
            "ref": "span_start",
            "typeId": "reference.points.construct_point",
            "x": 220,
            "y": 140,
        },
    )
    preset["connections"] = [
        c
        for c in preset["connections"]
        if c.get("fromRef") not in {"local_origin", "world_origin_deconstruct"}
        and c.get("toRef") not in {"local_origin", "world_origin_deconstruct"}
    ]
    add_conns(
        preset,
        [
            {"fromRef": "span_x", "fromPort": "output_value", "toRef": "span_start", "toPort": "input_x"},
            {"fromRef": "deck_elevation", "fromPort": "output_value", "toRef": "span_start", "toPort": "input_y"},
            {"fromRef": "span_z", "fromPort": "output_value", "toRef": "span_start", "toPort": "input_z"},
            {"fromRef": "span_start", "fromPort": "output_point", "toRef": "path_end", "toPort": "input_point"},
        ],
    )


def patch_watchtower(preset: dict) -> None:
    """No-op when parapet topology is present; legacy roof presets are rewritten via rewrite_p3_presets.py."""
    refs = {n.get("ref") for n in preset.get("nodes") or []}
    if "top_deck" in refs and "tower_cut" in refs:
        return
    box = node_by_ref(preset, "battlement_box")
    box["state"] = {
        "cornerX": 4.4,
        "cornerY": 14.0,
        "cornerZ": -0.4,
        "sizeX": 1.2,
        "sizeY": 0.8,
        "sizeZ": 0.8,
    }
    array_node = node_by_ref(preset, "battlement_array")
    array_node["typeId"] = "pattern.radial.polar_array"
    array_node["state"] = {"count": 12, "includeEnd": False}
    preset["nodes"] = [n for n in preset["nodes"] if n.get("ref") != "array_dir"]
    preset["connections"] = [
        c
        for c in preset["connections"]
        if not (
            c.get("fromRef") == "array_dir"
            or (c.get("toRef") == "battlement_array" and c.get("toPort") == "input_direction")
        )
    ]
    if "battlement_count" not in refs:
        insert_after(
            preset,
            "battlement_array",
            {
                "ref": "battlement_count",
                "typeId": "input.numeric.integer",
                "x": 0,
                "y": 900,
                "state": {"value": 12},
            },
        )
        add_conns(
            preset,
            [
                {
                    "fromRef": "battlement_count",
                    "fromPort": "output_value",
                    "toRef": "battlement_array",
                    "toPort": "input_count",
                }
            ],
        )
    preset["description"] = (
        "Hollow 1-block-thick cylindrical tower with a ground-level round arch door, a top deck slab, "
        "and 12 polar-array battlements. Difference booleans are deferred voxel cuts on the block grid. "
        "Preview Geometry and Preview Blocks share the same moved composite."
    )


def patch_gazebo(preset: dict) -> None:
    volume = node_by_ref(preset, "volume")
    volume["state"] = {
        "cornerX": -5.0,
        "cornerY": 0.0,
        "cornerZ": -5.0,
        "sizeX": 10.0,
        "sizeY": 3.5,
        "sizeZ": 10.0,
    }
    column = node_by_ref(preset, "column")
    column["state"] = {
        "startX": 4.0,
        "startY": 0.0,
        "startZ": 0.0,
        "endX": 4.0,
        "endY": 3.5,
        "endZ": 0.0,
        "radius": 0.25,
    }


def patch_castle_keep(preset: dict) -> None:
    keep = node_by_ref(preset, "keep_body")
    keep["state"] = {
        "cornerX": 1.0,
        "cornerY": 0.0,
        "cornerZ": 1.0,
        "sizeX": 12.0,
        "sizeY": 8.0,
        "sizeZ": 12.0,
    }


def patch_medieval_cottage(preset: dict) -> None:
    """No-op when opening-depth topology is present; legacy patches are rewritten via rewrite_p3_presets.py."""
    refs = {n.get("ref") for n in preset.get("nodes") or []}
    if "opening_depth" in refs:
        return


def patch_walls(preset: dict, height: float) -> None:
    insert_after(
        preset,
        "walls",
        {
            "ref": "wall_height",
            "typeId": "input.numeric.float",
            "x": 520,
            "y": 200,
            "state": {"value": height},
        },
    )
    add_conns(
        preset,
        [
            {
                "fromRef": "wall_height",
                "fromPort": "output_value",
                "toRef": "walls",
                "toPort": "input_height",
            }
        ],
    )


def main() -> None:
    data = json.loads(GRAPH_FILE.read_text(encoding="utf-8"))
    patch_stone_bridge(find_preset(data, "architectural.infrastructure.stone_bridge"))
    patch_watchtower(find_preset(data, "architectural.infrastructure.watchtower"))
    patch_medieval_cottage(find_preset(data, "architectural.residential.medieval_cottage"))
    patch_gazebo(find_preset(data, "decorative.gazebo"))
    patch_castle_keep(find_preset(data, "styles.medieval.castle_keep"))
    patch_walls(find_preset(data, "architectural.residential.simple_house"), 4.0)
    GRAPH_FILE.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("patched geometric scale presets")


if __name__ == "__main__":
    main()
