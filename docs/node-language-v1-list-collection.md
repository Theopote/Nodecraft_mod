# List / Collection / Data Tree language v1

Freeze against Graph **V23**.

## Principles

- **`LIST`** = flat heterogeneous / generic collection only
- **Typed `*_LIST`** = element-kind-safe collections (`POINT_LIST`, `DOUBLE_LIST`, `BOOLEAN_LIST`, `STRING_LIST`, …)
- **typed → `LIST`**: implicit OK (safe widening)
- **`LIST` → typed**: implicit **forbidden** (future As/Validate nodes)
- **typed → typed**: same `ListElementKind` only
- Generic operators declare `LIST` but **preserve element kind** via list type variable `T` (effective type; declared type stays `LIST`)
- **Nested `List<List<?>>` is not a tree** — structure uses **`DATA_TREE`**
- No string→number coercion on typed numeric list pipes

## Typed scalar lists

| Type | Kind |
|------|------|
| `DOUBLE_LIST` | `DOUBLE` |
| `BOOLEAN_LIST` | `BOOLEAN` |
| `STRING_LIST` | `STRING` |

## List&lt;T&gt; preservation

Ports may call `BasePort.bindListType("T")` / `bindListElementType("T")`.
`PortTypeResolver.resolveEffectiveType` remaps `LIST` / `ANY` → e.g. `POINT_LIST` / `POINT`
when an inbound typed list binds `T` on the same node.

Connection checks are **order-independent**: a candidate edge that would bind `T` is only
accepted if every existing connection in that type-variable group remains legal under the
resulting effective types (`PortTypeResolver.validateTypeVariableGroup`).

`BasePort.setValue` and `BaseNode.setInput` both validate against the **effective** type.

Reference: Reverse / Sub / Filter / Dispatch / Shuffle / Deduplicate / Insert / Set / Remove.

Insert Item: `0 ≤ index ≤ size` after negative-from-end normalization; otherwise `Valid=false`
(no append fallback).

## Numeric / mask contracts

| Node | Notes |
|------|-------|
| Number Sequence / Number Series | → `DOUBLE_LIST` |
| List Statistics / Map Numbers | `DOUBLE_LIST` only |
| Sort Numbers / Sort Text | typed sort (generic Sort deleted) |
| Sum / Product / Min / Max / Average | `DOUBLE_LIST` → `DOUBLE` (generic Reduce deleted) |
| Filter / Dispatch | Mask `BOOLEAN_LIST`, equal length, `Valid` fail-closed |

## Graph migration (V22→V23)

- Drops unconstrained `LIST` → typed `*_LIST` wires
- Filter/Dispatch mask: keep only `BOOLEAN_LIST` sources
- Removes `math.list.sort_list` / `math.list.reduce` **and orphan wires** to those nodes
- Strips legacy state keys **per node type** (e.g. Shuffle `preserveInput`, Dispatch `defaultValue`) — never global key names

## Deferred (List v1.1)

- Remove Item dual mode → Remove at Index / Remove Matching
- Set Item `wrapIndex` → strict index language only
- As / Validate List bridges
- `INTEGER_LIST` / `TREE_PATH`
