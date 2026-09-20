#!/usr/bin/env python3
import json
from pathlib import Path

PRESET_FILE = Path("src/main/resources/nodecraft/graph_presets.json")
IDS = {
    "architectural.infrastructure.stone_bridge",
    "building_elements.stairs.straight_staircase",
    "decorative.gazebo",
}
data = json.loads(PRESET_FILE.read_text(encoding="utf-8-sig"))
for cat in data.get("categories") or []:
    for p in cat.get("presets") or []:
        if p.get("id") not in IDS:
            continue
        print("====", p["id"], "====")
        for n in p.get("nodes") or []:
            print(f"  {n['ref']:24} {n['typeId']}")
        print()
