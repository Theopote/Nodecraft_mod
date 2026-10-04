# Node Language v2 — Data Tree Construction & Strict Lookup

**Status: PASSED / FROZEN** (Graph **V130**; V24 remains historical for Data Tree v1 foundations)

Strict List→Tree construction and lookup diagnostics for batch 1 of `math.data_tree`
(Graft List, Partition List To Tree, Tree Branch, Tree Item). Structural operations (Merge,
Entwine, Shift, Simplify, Cull Empty) are covered in
[`node-language-v2-data-tree-structural-ops.md`](./node-language-v2-data-tree-structural-ops.md)
(Graph **V131**).

Related: [`node-language-v1-data-tree.md`](./node-language-v1-data-tree.md),
[`node-language-v2-list-collection.md`](./node-language-v2-list-collection.md),
[`node-language-v2-data-tree-structural-ops.md`](./node-language-v2-data-tree-structural-ops.md),
[`node-language-v2-data-tree-inspection.md`](./node-language-v2-data-tree-inspection.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Preserved (unchanged)

- `TREE_PATH` / `TREE_PATH_LIST` structured path authority (no string fallback)
- Unique-path invariant and encounter-order merge
- `DataTree<T>` binding via shared type variable `T` + `DataTreeData.elementKind`
- Construct Tree Path — reference only in this milestone

## List → Tree construction (Graft / Partition / Group)

Preflight validation runs **before** `DataTreeData.Branch` construction:

| Check | Valid=false error |
|-------|-------------------|
| `input_list` not `List<?>` or null | `invalid_input` |
| Any list element `== null` | `null_item` |
| Constrained `T` and runtime kind mismatch | `element_kind_mismatch` |
| `branchCount` / `itemCount` / path depth over `MAX_TREE_*` | `output_budget_exceeded` |

Graft: `N` items → `N` branches, depth 1. Partition: `branchCount = ceil(items / size)` (long). Group: worst-case `N` unique keys.

Shared helper: `DataTreeNodeUtils.preflightTreeConstruction`.

Element-kind validation applies at **construction boundaries** (Graft / Partition / Group).
Skipped when resolved kind is `UNCONSTRAINED`.

On failure (transactional):

| Output | Value |
|--------|-------|
| `output_tree` | `DataTreeData.empty(resolvedKind)` |
| `output_branch_count` | `0` |
| `output_valid` | `false` |
| `output_error` | specific code above |

On success: `output_valid=true`, `output_error=""`. Empty input list is valid (zero branches).

Null items are **forbidden** at the List→Tree boundary (aligned with Group List) to avoid
`List.copyOf` NPE inside branch construction.

`DataTreeData` / `TreePathData` constructors also reject oversize, null items, and **negative path
components**. Graph Construct Tree Path fail-closes instead of throwing.

## TREE_PATH

Components are non-negative integers. Depth `<= MAX_TREE_PATH_DEPTH` (256). Negative indexes are
list-from-end language only, not branch addresses.

## Partition List To Tree — strict Size

Size uses exact `Integer` only (`StrictIntegerUtils.requireExactInteger`):

| Condition | Result |
|-----------|--------|
| Undriven / null / non-Integer (e.g. connected `2.9`) | `invalid_size` |
| `size < 1` | `invalid_size` |
| Valid size | partition loop unchanged: `[A,B,C,D,E]` size=2 → `{0}:A,B`, `{1}:C,D`, `{2}:E` |

## Tree Branch / Tree Item — Valid vs Found

Invalid upstream inputs are distinguished from “path/index not found”:

| Situation | Valid | Found |
|-----------|-------|-------|
| Invalid tree input | false | false |
| Invalid path input | false | false |
| Invalid index (Tree Item only) | false | false |
| Valid tree, path missing | true | false |
| Valid tree/path, index OOR (Tree Item) | true | false |
| Success | true | true |

Error codes: `invalid_input`, `invalid_path`, `invalid_index` (Tree Item).

Tree Item index language unchanged: exact `Integer`, negatives from end via `resolveIndex`;
out of range → Found=false (not invalid).

## Graph migration (V129→V130)

Identity migration — no wire remaps. Runtime semantics only (Valid/Error outputs, strict parse,
null guards, strict Size/Index).
