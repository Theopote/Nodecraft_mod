# Data Tree / Tree Path language v1

Freeze against Graph **V24**.

## Principles

- **`DataTree<T>`** — one `DATA_TREE` port type + shared type variable `T` + `DataTreeData.elementKind`
- No per-kind `POINT_DATA_TREE` explosion
- **`TREE_PATH` / `TREE_PATH_LIST`** — structured path authority; `"{0;1}"` is Viewer/debug only
- **Unique path invariant** — one path = one branch; collisions merge items in encounter order
- Invalid path / index → **fail-closed** (`Found=false`), never silent `{0}` / wrap / append

## Bridges (preserve T)

| From | Via | To |
|------|-----|-----|
| `List<T>` | Graft List / Partition List / Group List | `DataTree<T>` |
| `DataTree<T>` | Flatten Tree / Tree Branch | `List<T>` |
| `DataTree<T>` + `TREE_PATH` + Index | Tree Item | `T` |

## Structure

| Node | Semantics |
|------|-----------|
| Merge Trees | Same-path item concat |
| Entwine | Source-index path prefix (isolation) |
| Shift Path | Path level shift; collisions merge |
| Simplify Tree | Drop common prefix → `TREE_PATH` removed prefix |
| Cull Empty | Drop empty branches |
| Tree Paths | → `TREE_PATH_LIST` only |
| Tree Statistics | Branch/Item/MaxDepth + `INTEGER_LIST` sizes (no Paths) |
| Tree Viewer | Debug `STRING` summary (KEEP) |
| Construct Tree Path | `INTEGER_LIST` → `TREE_PATH` |

## Index language

Matches List v1: negatives from end; out of range → not found; no wrap / clamp / fallback.

## Graph migration (V23→V24)

- Strip Tree Item `allowNegativeIndex` / `wrapIndex`; Merge `preserveSourceIndex`; Partition `dropRemainder`
- Drop non-`TREE_PATH` wires into Branch/Item path ports
- Drop legacy `output_path_strings`, Statistics `output_paths`, Partition `output_remainder`
