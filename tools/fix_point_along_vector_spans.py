#!/usr/bin/env python3
"""Fix Point Along Vector distance vs normalized-direction in canonical presets."""
from __future__ import annotations

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRAPH_FILE = ROOT / "src/main/resources/nodecraft/graph_presets.json"

# Direction is always unit-length; Distance is the true span.
FIXES = {
    "architectural.infrastructure.stone_bridge": {
        "vector_ref": "run_vector",
        "distance_ref": "unit_distance",
        "vector": (1.0, 0.0, 0.0),
        "distance": 16.0,
    },
    "building_elements.stairs.straight_staircase": {
        "vector_ref": "run_vector",
        "distance_ref": "unit_distance",
        "vector": (12.0, 0.0, 0.0),
        "distance": 12.0,
    },
    "building_elements.stairs.spiral_staircase": {
        "vector_ref": "tangent_vector",
        "distance_ref": "unit_distance",
        "vector": (1.0, 0.0, 0.0),
        "distance": 2.0,
    },
}


def main() -> None:
    data = json.loads(GRAPH_FILE.read_text(encoding="utf-8"))
    updated = []
    for category in data.get("categories") or []:
        for preset in category.get("presets") or []:
            pid = preset.get("id")
            spec = FIXES.get(pid)
            if not spec:
                continue
            for node in preset.get("nodes") or []:
                if not node:
                    continue
                if node.get("ref") == spec["vector_ref"]:
                    x, y, z = spec["vector"]
                    node.setdefault("state", {})
                    node["state"]["x"] = x
                    node["state"]["y"] = y
                    node["state"]["z"] = z
                if node.get("ref") == spec["distance_ref"]:
                    node.setdefault("state", {})
                    node["state"]["value"] = spec["distance"]
            updated.append(f"{pid} distance={spec['distance']} vector={spec['vector']}")
    GRAPH_FILE.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("updated:")
    for line in updated:
        print(" ", line)


if __name__ == "__main__":
    main()
