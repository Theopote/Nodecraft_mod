#!/usr/bin/env python3
"""Apply layered auto-layout to composite presets in graph_presets.json.

Mirrors com.nodecraft.gui.layout.GraphNodeAutoLayout (320×180 spacing,
topological columns + barycenter ordering).
"""
from __future__ import annotations

import argparse
import json
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRESET_FILE = ROOT / "src/main/resources/nodecraft/graph_presets.json"

DEFAULT_LAYER_SPACING_X = 320.0
DEFAULT_BASE_LAYER_SPACING_Y = 180.0
MIN_NODE_SEPARATION = 40.0


def resolve_layer_spacing_y(base_spacing: float, layer_node_count: int) -> float:
    if layer_node_count <= 4:
        return base_spacing
    dense = 220.0 + (layer_node_count - 5) * 12.0
    return max(base_spacing, min(320.0, dense))


def order_layers_by_barycenter(
    layer_map: dict[int, list[str]],
    neighbors: dict[str, list[str]],
    forward: bool,
) -> None:
    layers = sorted(layer_map.keys(), reverse=not forward)
    index_in_layer: dict[str, int] = {}
    for nodes in layer_map.values():
        for i, ref in enumerate(nodes):
            index_in_layer[ref] = i

    for layer in layers:
        layer_nodes = layer_map.get(layer)
        if not layer_nodes or len(layer_nodes) <= 1:
            continue
        adjacent_layer = layer - 1 if forward else layer + 1
        adjacent = layer_map.get(adjacent_layer)
        if not adjacent:
            continue

        barycenter: dict[str, float] = {}
        for ref in layer_nodes:
            related = neighbors.get(ref, [])
            indices = [index_in_layer[r] for r in related if r in index_in_layer]
            if indices:
                barycenter[ref] = sum(indices) / len(indices)
            else:
                barycenter[ref] = float(index_in_layer.get(ref, 0))

        layer_nodes.sort(key=lambda r: (barycenter.get(r, 0.0), index_in_layer.get(r, 0)))
        for i, ref in enumerate(layer_nodes):
            index_in_layer[ref] = i


def auto_layout(
    refs: list[str],
    edges: list[tuple[str, str]],
    layer_spacing_x: float = DEFAULT_LAYER_SPACING_X,
    base_layer_spacing_y: float = DEFAULT_BASE_LAYER_SPACING_Y,
) -> dict[str, tuple[float, float]]:
    if not refs:
        return {}

    node_by_ref = {r: r for r in refs}
    indegree = {r: 0 for r in refs}
    depth = {r: 0 for r in refs}
    outgoing: dict[str, list[str]] = {r: [] for r in refs}
    incoming: dict[str, list[str]] = {r: [] for r in refs}

    for src, dst in edges:
        if src not in node_by_ref or dst not in node_by_ref or src == dst:
            continue
        outgoing[src].append(dst)
        incoming[dst].append(src)
        indegree[dst] = indegree.get(dst, 0) + 1

    queue: deque[str] = deque(r for r in refs if indegree.get(r, 0) == 0)
    if not queue:
        queue.append(refs[0])

    visited: set[str] = set()
    while queue:
        current = queue.popleft()
        if current in visited:
            continue
        visited.add(current)
        current_depth = depth.get(current, 0)
        for nxt in outgoing.get(current, []):
            depth[nxt] = max(depth.get(nxt, 0), current_depth + 1)
            indegree[nxt] = indegree.get(nxt, 0) - 1
            if indegree[nxt] <= 0 and nxt not in visited:
                queue.append(nxt)

    for ref in refs:
        if ref not in visited:
            depth.setdefault(ref, 0)

    layer_map: dict[int, list[str]] = {}
    for ref in refs:
        layer = max(0, depth.get(ref, 0))
        layer_map.setdefault(layer, []).append(ref)

    author_order = {ref: i for i, ref in enumerate(refs)}
    for layer_nodes in layer_map.values():
        layer_nodes.sort(key=lambda r: author_order.get(r, 0))

    order_layers_by_barycenter(layer_map, incoming, True)
    order_layers_by_barycenter(layer_map, outgoing, False)
    order_layers_by_barycenter(layer_map, incoming, True)

    positions: dict[str, tuple[float, float]] = {}
    for layer, layer_nodes in sorted(layer_map.items()):
        spacing_y = resolve_layer_spacing_y(base_layer_spacing_y, len(layer_nodes))
        for i, ref in enumerate(layer_nodes):
            x = layer * layer_spacing_x
            y = (i - (len(layer_nodes) - 1) / 2.0) * spacing_y
            positions[ref] = (x, y)
    return positions


def normalize_positions(positions: dict[str, tuple[float, float]], min_y: float = 40.0) -> None:
    if not positions:
        return
    min_x = min(x for x, _ in positions.values())
    min_y_val = min(y for _, y in positions.values())
    shift_x = -min_x if min_x < 0 else 0.0
    shift_y = min_y - min_y_val if min_y_val < min_y else 0.0
    if shift_x == 0.0 and shift_y == 0.0:
        return
    for ref in positions:
        x, y = positions[ref]
        positions[ref] = (x + shift_x, y + shift_y)


def find_overlaps(nodes: list[dict], threshold: float = MIN_NODE_SEPARATION) -> list[tuple[str, str]]:
    pairs: list[tuple[str, str]] = []
    for i, a in enumerate(nodes):
        if not a:
            continue
        ax, ay = float(a.get("x", 0)), float(a.get("y", 0))
        ra = a.get("ref", "?")
        for b in nodes[i + 1 :]:
            if not b:
                continue
            bx, by = float(b.get("x", 0)), float(b.get("y", 0))
            rb = b.get("ref", "?")
            if abs(ax - bx) < threshold and abs(ay - by) < threshold:
                pairs.append((ra, rb))
    return pairs


def layout_preset(preset: dict) -> int:
    if preset.get("kind") != "composite":
        return 0
    nodes = [n for n in (preset.get("nodes") or []) if n and n.get("ref")]
    if len(nodes) < 2:
        return 0

    refs = [n["ref"] for n in nodes]
    edges: list[tuple[str, str]] = []
    for c in preset.get("connections") or []:
        if not c:
            continue
        fr, to = c.get("fromRef"), c.get("toRef")
        if fr and to:
            edges.append((fr, to))

    positions = auto_layout(refs, edges)
    normalize_positions(positions)
    updated = 0
    for node in nodes:
        ref = node.get("ref")
        if ref in positions:
            x, y = positions[ref]
            if node.get("x") != x or node.get("y") != y:
                node["x"] = round(x, 1)
                node["y"] = round(y, 1)
                updated += 1
    return updated


def apply_auto_layout_to_data(data: dict, preset_ids: set[str]) -> tuple[int, list[str]]:
    """Layout replaced presets and print summary lines to stdout."""
    updated, warnings = layout_presets_in_data(data, only=preset_ids)
    if updated:
        print(f"  auto-layout: adjusted {updated} node positions")
    for warning in warnings:
        print(f"  layout warning: {warning}")
    return updated, warnings


def layout_presets_in_data(data: dict, only: set[str] | None = None) -> tuple[int, list[str]]:
    """Layout composite presets in parsed graph_presets data. Returns (nodes_updated, overlap_warnings)."""
    total_updated = 0
    overlap_reports: list[str] = []
    for category in data.get("categories") or []:
        for preset in category.get("presets") or []:
            if not preset:
                continue
            pid = preset.get("id")
            if only and pid not in only:
                continue
            total_updated += layout_preset(preset)
            pairs = find_overlaps(preset.get("nodes") or [])
            if pairs:
                overlap_reports.append(f"{pid}: {len(pairs)} overlap(s) — {pairs[:3]}")
    return total_updated, overlap_reports


def main() -> None:
    parser = argparse.ArgumentParser(description="Auto-layout composite preset nodes in graph_presets.json")
    parser.add_argument("--only", action="append", metavar="PRESET_ID", help="Layout only this preset id")
    parser.add_argument("--check", action="store_true", help="Report overlaps without writing")
    parser.add_argument("--dry-run", action="store_true", help="Print changes without writing")
    args = parser.parse_args()

    data = json.loads(PRESET_FILE.read_text(encoding="utf-8"))
    only = set(args.only) if args.only else None

    if args.check:
        overlap_reports: list[str] = []
        for category in data.get("categories") or []:
            for preset in category.get("presets") or []:
                if not preset:
                    continue
                pid = preset.get("id")
                if only and pid not in only:
                    continue
                pairs = find_overlaps(preset.get("nodes") or [])
                if pairs:
                    overlap_reports.append(f"{pid}: {len(pairs)} overlap(s) — {pairs[:5]}")
        if overlap_reports:
            print("Overlaps found:")
            for line in overlap_reports:
                print(f"  {line}")
            raise SystemExit(1)
        print("No overlaps detected.")
        return

    if args.dry_run:
        for category in data.get("categories") or []:
            for preset in category.get("presets") or []:
                if not preset:
                    continue
                pid = preset.get("id")
                if only and pid not in only:
                    continue
                before = {(n["ref"], n.get("x"), n.get("y")) for n in preset.get("nodes") or [] if n}
                layout_preset(preset)
                after = {(n["ref"], n.get("x"), n.get("y")) for n in preset.get("nodes") or [] if n}
                changed = len(before - after)
                if changed:
                    print(f"{pid}: would update {changed} node positions")
        return

    total_updated, overlap_reports = layout_presets_in_data(data, only=only)
    PRESET_FILE.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{PRESET_FILE.name}: updated {total_updated} node positions across composite presets")
    if overlap_reports:
        print("Warning — overlaps remain after layout:")
        for line in overlap_reports:
            print(f"  {line}")


if __name__ == "__main__":
    main()
