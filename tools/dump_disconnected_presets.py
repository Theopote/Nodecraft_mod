#!/usr/bin/env python3
import json
from pathlib import Path
from collections import defaultdict

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
        refs = [n["ref"] for n in p.get("nodes") or [] if n and n.get("ref")]
        adj = defaultdict(set)
        print("====", p["id"], "====")
        print("CONNECTIONS:")
        for c in p.get("connections") or []:
            print(f"  {c['fromRef']}.{c['fromPort']} -> {c['toRef']}.{c['toPort']}")
            adj[c["fromRef"]].add(c["toRef"])
            adj[c["toRef"]].add(c["fromRef"])
        seen = set()
        comps = []
        for start in refs:
            if start in seen:
                continue
            stack = [start]
            comp = set()
            while stack:
                cur = stack.pop()
                if cur in seen:
                    continue
                seen.add(cur)
                comp.add(cur)
                stack.extend(adj[cur] - seen)
            comps.append(sorted(comp))
        print(f"COMPONENTS ({len(comps)}):")
        for i, comp in enumerate(comps, 1):
            print(f"  [{i}] {comp}")
        print()
