#!/usr/bin/env python3
"""Audit NodeCraft graph_presets.json for orphan nodes and broken connections."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRESET_FILE = ROOT / "src/main/resources/nodecraft/graph_presets.json"


def audit_preset(preset: dict) -> dict:
    nodes = preset.get("nodes") or []
    conns = preset.get("connections") or []
    refs = []
    dup_refs = []
    seen = set()
    for n in nodes:
        if not n:
            continue
        ref = n.get("ref")
        if ref in seen:
            dup_refs.append(ref)
        else:
            seen.add(ref)
            refs.append(ref)

    ref_set = set(refs)
    connected = set()
    bad_conns = []
    for i, c in enumerate(conns):
        if not c:
            bad_conns.append({"index": i, "reason": "null"})
            continue
        fr, to = c.get("fromRef"), c.get("toRef")
        problems = []
        if fr not in ref_set:
            problems.append(f"unknown fromRef={fr}")
        if to not in ref_set:
            problems.append(f"unknown toRef={to}")
        if fr == to:
            problems.append("self-loop")
        if not c.get("fromPort") or not c.get("toPort"):
            problems.append("missing port id")
        if problems:
            bad_conns.append({"index": i, "from": fr, "to": to, "problems": problems, "raw": c})
        else:
            connected.add(fr)
            connected.add(to)

    orphans = sorted(ref_set - connected)
    # Isolated groups: nodes with no path to others via undirected edges
    # (orphans already cover degree-0)

    components = undirected_components(ref_set, conns)
    multi_components = [sorted(c) for c in components if len(components) > 1]

    return {
        "id": preset.get("id"),
        "displayName": preset.get("displayName"),
        "kind": preset.get("kind"),
        "node_count": len(refs),
        "conn_count": len(conns),
        "orphan_refs": orphans,
        "dup_refs": dup_refs,
        "bad_conns": bad_conns,
        "component_count": len(components),
        "components": [sorted(c) for c in components],
        "has_issues": bool(orphans or dup_refs or bad_conns or len(components) > 1)
        or (preset.get("kind") == "composite" and len(refs) > 1 and len(conns) == 0),
    }


def undirected_components(refs: set[str], conns: list) -> list[set[str]]:
    adj: dict[str, set[str]] = {r: set() for r in refs}
    for c in conns:
        if not c:
            continue
        fr, to = c.get("fromRef"), c.get("toRef")
        if fr in adj and to in adj:
            adj[fr].add(to)
            adj[to].add(fr)
    seen: set[str] = set()
    comps: list[set[str]] = []
    for start in sorted(refs):
        if start in seen:
            continue
        stack = [start]
        comp: set[str] = set()
        while stack:
            cur = stack.pop()
            if cur in seen:
                continue
            seen.add(cur)
            comp.add(cur)
            stack.extend(adj[cur] - seen)
        comps.append(comp)
    return comps


def main() -> None:
    data = json.loads(PRESET_FILE.read_text(encoding="utf-8-sig"))
    reports = []
    for cat in data.get("categories") or []:
        cat_id = cat.get("id")
        for preset in cat.get("presets") or []:
            if not preset:
                continue
            r = audit_preset(preset)
            r["category"] = cat_id
            reports.append(r)

    print(f"FILE: {PRESET_FILE}")
    print(f"TOTAL PRESETS: {len(reports)}")
    print()

    issue_count = 0
    for r in reports:
        if r["kind"] != "composite":
            print(f"[SKIP placeholder] {r['category']}/{r['id']} kind={r['kind']}")
            continue
        status = "ISSUE" if r["has_issues"] else "OK"
        if r["has_issues"]:
            issue_count += 1
        print(f"[{status}] {r['category']}/{r['id']}  ({r['displayName']})")
        print(f"        nodes={r['node_count']} connections={r['conn_count']}")
        if r["orphan_refs"]:
            print(f"        ORPHANS ({len(r['orphan_refs'])}): {', '.join(r['orphan_refs'])}")
        if r.get("component_count", 1) > 1:
            print(f"        DISCONNECTED COMPONENTS ({r['component_count']}):")
            for i, comp in enumerate(r.get("components") or [], 1):
                print(f"          [{i}] {', '.join(comp)}")
        if r["dup_refs"]:
            print(f"        DUP REFS: {', '.join(r['dup_refs'])}")
        if r["bad_conns"]:
            print(f"        BAD CONNECTIONS ({len(r['bad_conns'])}):")
            for b in r["bad_conns"]:
                print(f"          - {b}")
        if r["node_count"] > 1 and r["conn_count"] == 0:
            print("        NO CONNECTIONS among multiple nodes")
        print()

    print("=" * 60)
    print(f"Composite with issues: {issue_count}")
    print(f"Composite OK: {sum(1 for r in reports if r['kind']=='composite' and not r['has_issues'])}")


if __name__ == "__main__":
    main()
