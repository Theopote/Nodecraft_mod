#!/usr/bin/env python3
import json
from pathlib import Path

PRESET_FILE = Path("src/main/resources/nodecraft/graph_presets.json")
IDS = {
    "architectural.infrastructure.watchtower",
    "building_elements.windows.arched_window",
    "styles.medieval.castle_keep",
}

data = json.loads(PRESET_FILE.read_text(encoding="utf-8-sig"))
for cat in data.get("categories") or []:
    for p in cat.get("presets") or []:
        if p.get("id") not in IDS:
            continue
        print("====", p["id"], "====")
        print("NODES:")
        for n in p.get("nodes") or []:
            print(f"  {n['ref']:24} {n['typeId']}")
        print("CONNECTIONS:")
        for x in p.get("connections") or []:
            print(f"  {x['fromRef']}.{x['fromPort']} -> {x['toRef']}.{x['toPort']}")
        print()
