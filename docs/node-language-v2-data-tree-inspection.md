# Node Language v2 — Data Tree Inspection & Flatten Safety

**Status: PASSED / FROZEN** (Graph **V132**; V24 remains historical for Data Tree v1 foundations)

Final batch of `math.data_tree`: Flatten Tree, Tree Paths, Tree Statistics, and Tree Viewer.
Together with V130 (construction/lookup) and V131 (structural ops), this closes the 14-node
Data Tree category.

Related: [`node-language-v1-data-tree.md`](./node-language-v1-data-tree.md),
[`node-language-v2-data-tree-construction.md`](./node-language-v2-data-tree-construction.md),
[`node-language-v2-data-tree-structural-ops.md`](./node-language-v2-data-tree-structural-ops.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Category closure (V130–V132)

| Graph | Batch | Nodes |
|-------|-------|-------|
| V130 | Construction & lookup | Graft, Partition, Branch, Item (+ Construct Path unchanged) |
| V131 | Structural ops | Merge, Entwine, Shift Path, Simplify, Cull Empty |
| V132 | Inspection & flatten | Flatten, Paths, Statistics, Viewer |

## Preserved (unchanged)

- Paths / Statistics / Viewer role separation (no merged debug mega-node)
- Tree Paths emits `TREE_PATH_LIST` only (no string path list)
- Statistics formulas (branch/item/max-depth/sizes)
- Flatten does not recurse into nested list items inside branches

## Strict tree input

All four nodes use `DataTreeNodeUtils.parseTree` (not silent `requireTree` empty coercion):

| Input | Result |
|-------|--------|
| Valid empty tree | Success (empty outputs as appropriate) |
| Valid non-empty tree | Success |
| Null / wrong type | `Valid=false`, `invalid_input` |

Existing payload ports are retained; `output_valid` / `output_error` are additive.

## Path order (`DataTreeData.PATH_ORDER`)

Canonical branch order is integer lexicographic:

1. Compare path indices left-to-right with `Integer.compare`
2. On shared prefix, shorter path sorts first

Affects Flatten item order, Tree Paths, Branch Sizes, and Viewer line order.
Negative and large indices follow the same integer order (not zero-padded string keys).

## Flatten Tree — output budget

Preflight `tree.getItemCount()` against `GenerationLimits.MAX_LIST_ELEMENTS` **before** allocating
the flattened list.

| Condition | Result |
|-----------|--------|
| Item count ≤ budget | Full flatten, `Valid=true` |
| Item count > budget | Empty list, count=0, `Valid=false`, `output_budget_exceeded` |

No partial flatten / silent drop.

## Tree Viewer — preview budget

| Constant | Value |
|----------|-------|
| `MAX_TREE_VIEWER_PREVIEW_BRANCHES` | 64 |
| `MAX_TREE_VIEWER_OUTPUT_CHARS` | 8_192 |

`DataTreeData.describePreview` streams branch lines; on budget hit appends `Preview truncated.`
and sets `output_truncated=true`. Truncation is **not** an error (`Valid` remains true).

| Situation | Valid | Truncated |
|-----------|-------|-----------|
| Invalid tree | false | false |
| Within budget | true | false |
| Budget hit | true | true |

## Graph migration (V131→V132)

Identity migration — no wire remaps. Runtime semantics only.
