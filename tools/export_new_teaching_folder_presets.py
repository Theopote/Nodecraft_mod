#!/usr/bin/env python3
"""Create folder-format exports for the new teaching quickstart presets."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRAPH_FILE = ROOT / "src/main/resources/nodecraft/graph_presets.json"
PRESETS_ROOT = ROOT / "presets" / "quickstart"

META = {
    "quickstart.sphere_from_center": {
        "dir": "sphere-from-center",
        "name": "Sphere From Center",
        "name_zh": "中心点球体",
        "description": "Build a sphere from a center point and preview geometry plus blocks.",
        "description_zh": "以中心点建造球体，并同时预览几何体与方块。",
        "tags": ["sphere", "center", "quickstart", "beginner"],
    },
    "quickstart.box_from_two_corners": {
        "dir": "box-from-two-corners",
        "name": "Box From Two Corners",
        "name_zh": "两点建造盒子",
        "description": "Build an axis-aligned box from two opposite corners and preview it.",
        "description_zh": "用两个对角点建造盒子，并预览几何体与方块。",
        "tags": ["box", "corners", "quickstart", "beginner"],
    },
    "quickstart.arc_and_circle_voxel": {
        "dir": "arc-and-circle-voxel",
        "name": "Arc & Circle Voxelize",
        "name_zh": "圆弧与圆圈体素化",
        "description": "Build a quarter arc and a full circle, voxelize both paths, and preview blocks.",
        "description_zh": "建造圆弧与圆圈，体素化路径并预览方块。",
        "tags": ["arc", "circle", "voxelize", "quickstart"],
    },
    "quickstart.selected_block_column": {
        "dir": "selected-block-column",
        "name": "Select Connected Column",
        "name_zh": "整列选中相连方块",
        "description": "Pick a seed block, flood-fill the connected same-type column, and preview the selection.",
        "description_zh": "选中一个种子方块，洪水填充相连的同类型方块（适合整列/立柱），并预览选区。",
        "tags": ["selection", "flood-fill", "column", "quickstart"],
    },
    "quickstart.twisted_cube": {
        "dir": "twisted-cube",
        "name": "Twisted Cube",
        "name_zh": "扭转立方体",
        "description": "Twist an SDF box around the vertical axis and preview the deformed solid.",
        "description_zh": "让一个立方体沿竖直轴扭转，并预览变形后的几何体与方块。",
        "tags": ["twist", "sdf", "deformation", "quickstart"],
    },
}


def convert_graph(graph: dict) -> dict:
    nodes = []
    ref_to_id: dict[str, str] = {}
    for n in graph.get("nodes") or []:
        node_id = "node_" + n["ref"]
        ref_to_id[n["ref"]] = node_id
        entry = {
            "id": node_id,
            "type": n["typeId"],
            "position": {"x": float(n.get("x", 0)), "y": float(n.get("y", 0))},
            "parameters": {},
        }
        for key, value in (n.get("state") or {}).items():
            entry["parameters"][key] = {"value": value}
        nodes.append(entry)

    connections = []
    for c in graph.get("connections") or []:
        connections.append(
            {
                "from": {"node": ref_to_id[c["fromRef"]], "port": c["fromPort"]},
                "to": {"node": ref_to_id[c["toRef"]], "port": c["toPort"]},
            }
        )
    return {"nodes": nodes, "connections": connections}


def main() -> None:
    graph_data = json.loads(GRAPH_FILE.read_text(encoding="utf-8-sig"))
    by_id = {}
    for category in graph_data.get("categories") or []:
        for preset in category.get("presets") or []:
            if preset and preset.get("id"):
                by_id[preset["id"]] = preset

    for preset_id, meta in META.items():
        preset = by_id[preset_id]
        folder = {
            "preset_id": preset_id,
            "version": "1.0.0",
            "schema_version": "1.0",
            "metadata": {
                "name": meta["name"],
                "name_i18n": {"zh_CN": meta["name_zh"], "en_US": meta["name"]},
                "description": meta["description"],
                "description_i18n": {
                    "zh_CN": meta["description_zh"],
                    "en_US": meta["description"],
                },
                "author": "NodeCraft Team",
                "tags": meta["tags"],
                "category": "quickstart",
                "difficulty": "beginner",
                "estimated_build_time": "1 minute",
                "estimated_node_count": len(preset.get("nodes") or []),
            },
            "thumbnails": {"main": "", "previews": []},
            "parameters": [],
            "graph": convert_graph(preset),
            "documentation": {
                "learning_notes": meta["description"],
                "tips": [],
                "related_presets": [],
            },
        }
        out_dir = PRESETS_ROOT / meta["dir"]
        out_dir.mkdir(parents=True, exist_ok=True)
        out_path = out_dir / "preset.json"
        out_path.write_text(json.dumps(folder, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"wrote {out_path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
