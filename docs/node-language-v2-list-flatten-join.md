# Node Language v2 — List Flatten & Join

**Status: PASSED / FROZEN** (Graph **V129**; completes math.list batch 4 / 24-node review)

Safe bounded flattening for nested lists and output-length preflight for string joins.

Related: [`node-language-v2-list-numeric.md`](./node-language-v2-list-numeric.md),
[`node-language-v2-list-collection.md`](./node-language-v2-list-collection.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Nodes in scope (2)

Flatten List, Join Strings.

## Flatten List (`math.list.flatten_list`)

Recursively flattens **nested `List` elements only**. Does not flatten `DATA_TREE` or unwrap Java arrays.

### Valid / Error contract

| Check | Valid=false error |
|-------|-------------------|
| `input_list` not `List<?>` (including null) | `invalid_input` |
| Driven Depth invalid / wrong type | `invalid_depth` |
| Cycle in current recursion path | `cycle_reference` |
| Traversal depth exceeds cap | `depth_exceeded` |
| Output element count exceeds budget | `element_limit` |

On failure: `output_list=[]`, `Valid=false`, specific error code.

### Depth semantics

| Depth source | Behavior |
|--------------|----------|
| Property/port `-1` | Safe **full flatten** using `MAX_FORMAT_DEPTH` (32) as cap |
| Property/port `N ≥ 0` | **Partial flatten**: at depth `N`, preserve nested lists without recursing further |
| Hard cap | Always fail when `currentDepth > MAX_FORMAT_DEPTH` |

Depth resolved via `OptionalPortDrive.resolveOptionalInteger` (exact `Integer` when driven).

### Safety guards

- **Cycle detection:** `IdentityHashMap` on active recursion path (shared non-cyclic list references allowed).
- **Element budget:** `MAX_LIST_ELEMENTS` (1_048_576).
- **No implicit wrap:** non-list runtime values are not coerced to single-element lists.
- **Legacy `preserveTypes`:** ignored at runtime; still accepted from saved graph state.

## Join Strings (`math.list.join_strings`)

Join semantics unchanged from V23: strict `STRING_LIST` elements, connection-aware separator, empty separator allowed.

### Output budget (V129)

Preflight before append using `GenerationLimits.validateJoinStringOutputBudget`:

- Sum element lengths + separator × (count − 1) with `long` arithmetic.
- Cap: `MAX_JOIN_STRING_OUTPUT_CHARS` (= `MAX_FORMAT_OUTPUT_CHARS`, 65_536).
- On exceed: `Result=""`, `Valid=false`, `Error="Joined text exceeds output limit"`.

Empty input list → `Valid=true`, `Result=""`.

## Graph migration (V128→V129)

Identity migration — runtime-only flatten/join semantics; no wire remaps.
