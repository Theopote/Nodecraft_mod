#!/usr/bin/env python3
"""Bulk-add NodeEffect to @NodeInfo annotations across node source files."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NODES_DIR = ROOT / "src" / "main" / "java" / "com" / "nodecraft" / "nodesystem" / "nodes"

EXCEPTIONS = {
    "output.execute.merge_block_placements": "PURE",
    "output.execute.clear_preview": "PREVIEW_WRITE",
    "world.write.peek_last_undo": "WORLD_READ",
    "output.execute.bake_status": "UI_EFFECT",
}


def infer_effect(node_id: str) -> str:
    if node_id in EXCEPTIONS:
        return EXCEPTIONS[node_id]
    if node_id.startswith("world.write."):
        return "WORLD_WRITE"
    if node_id.startswith(("world.read.", "world.query.", "world.selection.", "input.context.")):
        return "WORLD_READ"
    if node_id.startswith("output.preview."):
        return "PREVIEW_WRITE"
    if node_id.startswith("output.execute."):
        return "WORLD_WRITE"
    if node_id.startswith(("utilities.fileio.", "output.export.")):
        return "FILE_IO"
    if node_id.startswith("output.debug."):
        return "UI_EFFECT"
    return "PURE"


def extract_node_id(text: str) -> str | None:
    match = re.search(r'@NodeInfo\s*\(\s*[^)]*?\bid\s*=\s*"([^"]+)"', text, re.DOTALL)
    return match.group(1) if match else None


def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    if "@NodeInfo" not in text:
        return False
    if re.search(r"\beffect\s*=", text):
        return False

    node_id = extract_node_id(text)
    if not node_id:
        print(f"WARN: no id in {path}", file=sys.stderr)
        return False

    effect = infer_effect(node_id.lower())
    effect_line = f"    effect = NodeEffect.{effect},"

    # Insert after the @NodeInfo( line
    new_text, count = re.subn(
        r"(@NodeInfo\s*\(\s*\n)",
        r"\1" + effect_line + "\n",
        text,
        count=1,
    )
    if count == 0:
        print(f"WARN: could not insert effect in {path}", file=sys.stderr)
        return False

    if "import com.nodecraft.nodesystem.api.NodeEffect;" not in new_text:
        if "import com.nodecraft.nodesystem.api.NodeInfo;" in new_text:
            new_text = new_text.replace(
                "import com.nodecraft.nodesystem.api.NodeInfo;",
                "import com.nodecraft.nodesystem.api.NodeEffect;\nimport com.nodecraft.nodesystem.api.NodeInfo;",
            )
        else:
            package_match = re.match(r"(package [^;]+;\s*\n)", new_text)
            if package_match:
                insert_at = package_match.end()
                new_text = (
                    new_text[:insert_at]
                    + "\nimport com.nodecraft.nodesystem.api.NodeEffect;\n"
                    + new_text[insert_at:]
                )

    path.write_text(new_text, encoding="utf-8")
    return True


def main() -> int:
    updated = 0
    for path in sorted(NODES_DIR.rglob("*.java")):
        if process_file(path):
            updated += 1
            print(f"updated {path.relative_to(ROOT)}")
    print(f"Done: {updated} files updated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
