# -*- coding: utf-8 -*-
"""Generate NODE_LIBRARY.md and NODE_LIBRARY.zh-CN.md from @NodeInfo in node sources."""
import re
from pathlib import Path
from collections import defaultdict

ROOT = Path(__file__).resolve().parents[1]
NODES_DIR = ROOT / "src/main/java/com/nodecraft/nodesystem/nodes"


def collect_nodes():
    entries = []
    for file in sorted(NODES_DIR.rglob("*Node.java")):
        text = file.read_text(encoding="utf-8")
        if re.search(r"public\s+abstract\s+class\s+\w+Node\b", text):
            continue
        class_match = re.search(r"public\s+class\s+(\w+Node)\b", text)
        if not class_match:
            continue
        class_name = class_match.group(1)

        anno = re.search(
            r"@NodeInfo\s*\((.*?)\)\s*(?:@\w+(?:\s*\([^)]*\))?\s*)*public\s+class",
            text,
            re.S,
        )
        fields = {}
        if anno:
            body = anno.group(1)
            for key in ["id", "displayName", "description", "category", "order"]:
                m = re.search(rf"\b{key}\s*=\s*(\".*?\"|[^,\n\r)]+)", body, re.S)
                if m:
                    val = m.group(1).strip()
                    if val.startswith('"') and val.endswith('"'):
                        val = val[1:-1]
                    fields[key] = val

        rel = file.relative_to(NODES_DIR)
        parts = rel.parts
        inferred_cat = ".".join(parts[:2]) if len(parts) >= 3 else (parts[0] if len(parts) >= 2 else "uncategorized")

        node_id = fields.get("id") or class_name[:-4].lower()
        if (not node_id.endswith("node")) and ("." not in node_id):
            node_id = f"{node_id}_node"
        display = fields.get("displayName") or re.sub(r"(?<!^)([A-Z])", r" \1", class_name[:-4]).strip()
        desc = fields.get("description") or ""
        cat = fields.get("category") or inferred_cat
        order_raw = fields.get("order", "2147483647")
        try:
            order = int(order_raw)
        except ValueError:
            order = 2147483647

        # Keep docs aligned with runtime registry behavior:
        # deprecated output.execute.bake_* nodes are intentionally hidden from registration.
        if "@Deprecated" in text and node_id.startswith("output.execute.bake_"):
            continue

        entries.append(
            {
                "id": node_id,
                "display": display,
                "desc": desc,
                "cat": cat,
                "order": order,
                "class": class_name,
            }
        )
    return entries


def esc(s):
    return s.replace("|", "\\|").replace("\n", " ").strip()


def write_en(path, entries):
    categories = defaultdict(list)
    for n in entries:
        categories[n["cat"]].append(n)
    for cat in categories:
        categories[cat].sort(key=lambda x: (x["order"], x["display"].lower()))
    sorted_cats = sorted(categories.keys(), key=lambda x: x.lower())
    category_notes = {
        "variable": [
            "`variable` nodes read and write execution-scope state. The graph executor only guarantees order through explicit connections, so connect a Set/Remove/Clear output into downstream nodes when the write must happen first.",
            "User variable names must not start with `__nodecraft.`; that prefix is reserved for internal runtime data.",
            "`Exists` reports whether a name is present even when the stored value is `null`. Use Remove Variable to delete a name instead of storing `null` as a deletion marker.",
            "Variable List hides internal variables by default. Frame Local Variable clears its frame before writing when both Clear Frame and Write are true.",
        ],
    }

    lines = [
        "# NodeCraft Node Library",
        "",
        "- Scope: `src/main/java/com/nodecraft/nodesystem/nodes`",
        f"- Total nodes: **{len(entries)}**",
        f"- Total categories: **{len(sorted_cats)}**",
        "",
        "## Category Statistics",
        "",
        "| Category ID | Node Count |",
        "|---|---:|",
    ]
    for cat in sorted_cats:
        lines.append(f"| `{cat}` | {len(categories[cat])} |")

    for cat in sorted_cats:
        lines.extend(
            [
                "",
                f"## {cat} ({len(categories[cat])})",
                "",
            ]
        )
        if cat in category_notes:
            for note in category_notes[cat]:
                lines.append(f"- {note}")
            lines.append("")
        lines.extend(
            [
                "| Node Name | Node ID | Description | Class |",
                "|---|---|---|---|",
            ]
        )
        for n in categories[cat]:
            d = esc(n["desc"]) or "-"
            lines.append(f"| {esc(n['display'])} | `{esc(n['id'])}` | {d} | `{n['class']}` |")

    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- This document is generated from `@NodeInfo` metadata in node classes.",
            "- For nodes without `@NodeInfo`, metadata is inferred from class name and package path.",
            "- To improve node docs quality, fill `description` in each `@NodeInfo` annotation.",
            "",
        ]
    )
    path.write_text("\n".join(lines), encoding="utf-8", newline="\n")


def write_zh(path, entries):
    COLON = "\uff1a"
    COMMA = "\uff0c"
    PERIOD = "\u3002"
    LP = "\uff08"
    RP = "\uff09"

    T = {
        "title": "\u8282\u70b9\u5e93",
        "scope": "\u7edf\u8ba1\u8303\u56f4",
        "total_nodes": "\u8282\u70b9\u603b\u6570",
        "total_cats": "\u5206\u7c7b\u603b\u6570",
        "note_title": "\u8bf4\u660e",
        "note_body": (
            "\u300c\u8282\u70b9\u540d\u79f0\u300d\u4e0e\u300c\u8bf4\u660e\u300d\u5217\u6765\u81ea\u5404\u8282\u70b9\u7c7b\u4e0a\u7684 `@NodeInfo` \uff08\u4e0e\u7f16\u8f91\u5668\u5c55\u793a\u4e00\u81f4\uff09"
            + COMMA
            + "\u82e5\u6e90\u7801\u672a\u5199\u6ce8\u89e3\u8bf4\u660e"
            + COMMA
            + "\u5219\u8be5\u5217\u4e3a `-`"
            + PERIOD
        ),
        "cat_stats": "\u5206\u7c7b\u7edf\u8ba1",
        "col_cat_id": "\u5206\u7c7b ID",
        "col_count": "\u8282\u70b9\u6570",
        "col_name": "\u8282\u70b9\u540d\u79f0",
        "col_id": "\u8282\u70b9 ID",
        "col_desc": "\u8bf4\u660e",
        "col_class": "\u7c7b\u540d",
        "gen_notes": "\u6587\u6863\u751f\u6210\u8bf4\u660e",
        "g1": "\u672c\u6587\u6863\u7531\u6e90\u7801\u4e2d\u7684 `@NodeInfo` \u5143\u6570\u636e\u81ea\u52a8\u6c47\u603b\u751f\u6210"
        + PERIOD,
        "g2": "\u82e5\u67d0\u7c7b\u8282\u70b9\u7f3a\u5c11 `@NodeInfo`"
        + COMMA
        + "\u4f1a\u6309\u5305\u8def\u5f84\u63a8\u65ad\u5206\u7c7b ID"
        + COMMA
        + "\u5e76\u6309\u7c7b\u540d\u751f\u6210\u9ed8\u8ba4\u5c55\u793a\u540d"
        + PERIOD,
        "g3": "\u9700\u8981\u66f4\u5b8c\u6574\u7684\u4e2d\u6587\u8bf4\u660e\u65f6"
        + COMMA
        + "\u53ef\u5728\u5bf9\u5e94\u8282\u70b9\u7c7b\u7684 `@NodeInfo.description` \u4e2d\u8865\u5145"
        + PERIOD,
    }

    categories = defaultdict(list)
    for n in entries:
        categories[n["cat"]].append(n)
    for cat in categories:
        categories[cat].sort(key=lambda x: (x["order"], x["display"].lower()))
    sorted_cats = sorted(categories.keys(), key=lambda x: x.lower())
    category_notes = {
        "variable": [
            "`variable` 节点会读写执行作用域状态。图执行器只通过显式连线保证顺序；当写入必须先发生时，请把 Set/Remove/Clear 的输出连接到下游节点。",
            "用户变量名不能以 `__nodecraft.` 开头；该前缀保留给内部运行时数据。",
            "`Exists` 表示名称是否存在，即使存储值为 `null` 也会为 true。要删除名称请使用 Remove Variable，不要把 `null` 当作删除标记。",
            "Variable List 默认隐藏内部变量。Frame Local Variable 在 Clear Frame 与 Write 同时为 true 时，会先清空 frame，再把 Value 写入 Name。",
        ],
    }

    lines = [
        f"# NodeCraft {T['title']}",
        "",
        f"- **{T['scope']}**{COLON}`src/main/java/com/nodecraft/nodesystem/nodes`",
        f"- **{T['total_nodes']}**{COLON}**{len(entries)}**",
        f"- **{T['total_cats']}**{COLON}**{len(sorted_cats)}**",
        f"- **{T['note_title']}**{COLON}{T['note_body']}",
        "",
        f"## {T['cat_stats']}",
        "",
        f"| {T['col_cat_id']} | {T['col_count']} |",
        "|---|---:|",
    ]
    for cat in sorted_cats:
        lines.append(f"| `{cat}` | {len(categories[cat])} |")

    for cat in sorted_cats:
        lines.extend(
            [
                "",
                f"## {cat}{LP}{len(categories[cat])}{RP}",
                "",
            ]
        )
        if cat in category_notes:
            for note in category_notes[cat]:
                lines.append(f"- {note}")
            lines.append("")
        lines.extend(
            [
                f"| {T['col_name']} | {T['col_id']} | {T['col_desc']} | {T['col_class']} |",
                "|---|---|---|---|",
            ]
        )
        for n in categories[cat]:
            d = esc(n["desc"]) or "-"
            lines.append(f"| {esc(n['display'])} | `{esc(n['id'])}` | {d} | `{n['class']}` |")

    lines.extend(["", f"## {T['gen_notes']}", "", f"- {T['g1']}", f"- {T['g2']}", f"- {T['g3']}", ""])
    path.write_text("\n".join(lines), encoding="utf-8", newline="\n")


def main():
    entries = collect_nodes()
    write_en(ROOT / "docs/NODE_LIBRARY.md", entries)
    write_zh(ROOT / "docs/NODE_LIBRARY.zh-CN.md", entries)
    print(f"Wrote docs for {len(entries)} nodes")


if __name__ == "__main__":
    main()
