# Node Language v2 — List Collection

**Status: PASSED / FROZEN** (Graph **V127**; V23 remains historical for collection-wide rules)

Strict grouping & seed contract remediation for six `math.list.*` collection nodes.
Code changes in V127 focus on **Group List** and **Shuffle List**; Reverse, Filter, Dispatch,
and Deduplicate algorithms are unchanged (tests/docs only).

Related: [`node-language-v2-list-numeric.md`](./node-language-v2-list-numeric.md),
[`node-language-v2-list-core.md`](./node-language-v2-list-core.md),
[`node-language-v1-list-collection.md`](./node-language-v1-list-collection.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Nodes in scope (6)

Reverse List, Shuffle List, Deduplicate, Filter List, Dispatch List, Group List.

## Group List (`math.list.group_list`)

Keys must be **same length** as List. Null items are **forbidden** at the Group List boundary
(even though generic `LIST` allows null elements elsewhere — see List Core V126).

Preflight validation runs **before** grouping / `DataTreeData` construction:

| Check | Valid=false error |
|-------|-------------------|
| `input_list` or `input_keys` not `List<?>` | `invalid_input` |
| `inputList.size() != keysList.size()` | `length_mismatch` |
| Any `inputList.get(i) == null` | `null_item` |
| Any key `== null` and `skipInvalidKeys=false` | `null_key` |
| `N` items would exceed `MAX_TREE_BRANCHES` / `MAX_TREE_ITEMS` | `output_budget_exceeded` |

Preflight uses `DataTreeNodeUtils.preflightTreeConstruction` **before** grouping maps / branches.

When `skipInvalidKeys=true` and key is null: **skip that item** (length check runs first).

On failure (transactional):

| Output | Value |
|--------|-------|
| `output_tree` | `DataTreeData.empty()` with resolved element kind |
| `output_unique_keys` | `[]` |
| `output_group_count` | `0` |
| `output_valid` | `false` |
| `output_error` | specific code above |

On success: existing grouping logic unchanged (LinkedHashMap encounter order → branches with paths `{0},{1},…`).

## Shuffle List (`math.list.shuffle_list`)

Strict INTEGER Seed via `RandomInputResolver` when the Seed port is driven:

| Port state | Seed used |
|------------|-----------|
| Undriven | property `seed` (long; `Random` accepts long) |
| Driven + exact `Integer` | port value cast to long |
| Driven + invalid type/null | **fail** |

Valid / Error outputs:

| State | `output_list` | `output_valid` | `output_error` |
|-------|---------------|----------------|----------------|
| Success | shuffled copy (or empty copy if input empty) | `true` | `""` |
| Invalid seed when driven | `[]` | `false` | `invalid_input` |
| Invalid list input (not `List<?>`) | `[]` | `false` | `invalid_input` |

Non-destructive copy preserved: `new ArrayList<>(inputList)` before shuffle.

## Unchanged nodes (V127)

| Node | V127 action |
|------|-------------|
| Reverse List | Immutability test; invalid input → empty list (Valid port deferred) |
| Filter List | Boundary tests: invalid Boolean at last mask index; null element with valid mask |
| Dispatch List | Reference V23 behavior; invalid-input Valid unchanged |
| Deduplicate | Equality = Java `equals`/`hashCode` (not Compare cross-numeric; P2 doc only) |

## DATA_TREE bridge

Group List exports grouped items as `DATA_TREE`. Null list elements are allowed in generic
`LIST` operations (V126) but **forbidden** at the Group List boundary to avoid `List.copyOf` NPE
in `DataTreeData.Branch` construction.

## Graph migration (V126→V127)

Identity migration — runtime-only grouping/seed semantics; no wire remaps.
