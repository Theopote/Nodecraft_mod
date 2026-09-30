# Node Language v2 — Data Tree Structural Operations

**Status: PASSED / FROZEN** (Graph **V131**; V24 remains historical for Data Tree v1 foundations)

Safety and diagnostics for batch 2 of `math.data_tree` structural nodes: Merge Trees, Entwine,
Shift Path, Simplify Tree, and Cull Empty Branches.

Related: [`node-language-v1-data-tree.md`](./node-language-v1-data-tree.md),
[`node-language-v2-data-tree-construction.md`](./node-language-v2-data-tree-construction.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Preserved (unchanged)

- `TREE_PATH` structured path authority
- Unique-path invariant and encounter-order collision merge
- Merge vs Entwine separation (concat vs source-index prefix)
- Simplify common-prefix algorithm
- Cull Empty path identity (no renumbering)
- Empty path `{}` remains valid after positive Shift

## Tree input validity

Connection-aware resolution via `OptionalPortDrive.isConnected` and explicit `isInputPresent`:

| Port state | Behavior |
|------------|----------|
| Undriven (not connected, no value) | Skip — empty contribution (Merge/Entwine optional ports) |
| Driven + valid `DataTreeData` | Process normally |
| Driven + null / wrong type | `Valid=false`, `invalid_input` |

Unary nodes (Shift, Simplify, Cull) require a valid tree input; invalid input fails closed
(no silent empty-tree coercion).

## Merge / Entwine — runtime element kind

Runtime kind merge mirrors connect-time same-`T` rejection:

| Situation | Result |
|-----------|--------|
| Same constrained kind | ok |
| UNCONSTRAINED + constrained | adopt constrained; validate items |
| POINT + VECTOR (conflict) | `Valid=false`, `element_kind_conflict` |

On failure: empty tree, counts=0, `Valid=false`.

Entwine preflights `1 + branch.path().size()` against `MAX_TREE_PATH_DEPTH` before appending.

## Shift Path — depth budget and strict Shift

- `GenerationLimits.MAX_TREE_PATH_DEPTH = 256`
- Preflight **entire tree** before allocating output branches
- Negative shift uses `long addedLevels = -(long) shift` (no `Math.abs(int)`)
- `Integer.MIN_VALUE` and extreme negatives fail with `path_depth_exceeded` (no OOM)
- Shift port: exact `Integer` when connected (`invalid_shift` on connected non-Integer)
- Undriven Shift uses node property fallback

Positive shift greater than path length yields `{}` branches; collisions merge per V24.

## Valid / Error outputs

All five structural nodes emit `output_valid` and `output_error`:

| State | `output_valid` | `output_error` |
|-------|----------------|----------------|
| Success | `true` | `""` |
| Failure | `false` | specific code |

Error codes: `invalid_input`, `element_kind_conflict`, `element_kind_mismatch`,
`path_depth_exceeded`, `invalid_shift`.

## Graph migration (V130→V131)

Identity migration — no wire remaps. Runtime semantics only.
