#!/usr/bin/env python3
"""Sync presets/**/preset.json graph sections from graph_presets.json node topology."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRAPH_FILE = ROOT / "src/main/resources/nodecraft/graph_presets.json"
PRESETS_ROOT = ROOT / "presets"


def convert_graph(graph: dict) -> dict:
    nodes = []
    ref_to_id: dict[str, str] = {}
    for node in graph.get("nodes") or []:
        node_id = "node_" + node["ref"]
        ref_to_id[node["ref"]] = node_id
        entry = {
            "id": node_id,
            "type": node["typeId"],
            "position": {"x": float(node.get("x", 0)), "y": float(node.get("y", 0))},
            "parameters": {},
        }
        state = node.get("state") or {}
        for key, value in state.items():
            entry["parameters"][key] = {"value": value}
        nodes.append(entry)

    connections = []
    for conn in graph.get("connections") or []:
        connections.append(
            {
                "from": {"node": ref_to_id[conn["fromRef"]], "port": conn["fromPort"]},
                "to": {"node": ref_to_id[conn["toRef"]], "port": conn["toPort"]},
            }
        )
    return {"nodes": nodes, "connections": connections}


def main() -> None:
    graph_data = json.loads(GRAPH_FILE.read_text(encoding="utf-8-sig"))
    by_id: dict[str, dict] = {}
    for category in graph_data.get("categories") or []:
        for preset in category.get("presets") or []:
            if preset and preset.get("kind") == "composite" and preset.get("id"):
                by_id[preset["id"]] = preset

    updated = 0
    for preset_file in sorted(PRESETS_ROOT.glob("**/preset.json")):
        folder = json.loads(preset_file.read_text(encoding="utf-8"))
        preset_id = folder.get("preset_id")
        if preset_id not in by_id:
            continue
        folder["graph"] = convert_graph(by_id[preset_id])
        folder.setdefault("metadata", {})["estimated_node_count"] = len(folder["graph"]["nodes"])
        # Avoid dangling thumbnail paths until preview assets are added.
        folder["thumbnails"] = {"main": "", "previews": []}
        preset_file.write_text(json.dumps(folder, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        updated += 1
        print(f"synced {preset_id} ({len(folder['graph']['nodes'])} nodes)")

    print(f"updated {updated} folder preset(s)")


if __name__ == "__main__":
    main()
