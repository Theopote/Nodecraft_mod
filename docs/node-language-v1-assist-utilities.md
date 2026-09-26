# Node Language v1 — Assist Utilities

**Status: PASSED / FROZEN** (Graph **V55**)

Graph readability / flow helpers under `utilities.assist` (5 nodes). The frozen rule for this
family is: **assist nodes must not destroy the type system**.

Related: [`node-language-v1-list-collection.md`](./node-language-v1-list-collection.md) (type
variables), [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Product boundary

```text
assist = format + validate + coalesce + type-preserving relay/fork (5)
passthrough T: Relay / Fork / Validate Value / Coalesce
unbound ANY → typed: not implicit
typed → ANY: widening sink OK
String Format: ANY sinks OK; connection-aware Template / Values
Validate: BOOLEAN + OptionalPortDrive; Valid only; no Fail Hard
Coalesce: ?? among connected branches; same T
no Reroute / Tag Relay / Assert / Signal Merge ids
```

Internal stance (same category, not a split):

- **Editor-oriented runtime helpers:** Relay, Signal Fork
- **True utilities:** String Format, Validate, Coalesce

True editor-only wire waypoints / junctions are out of V55 (no SavedGraph UI entity layer yet).

## Inventory (5)

| order | Display name | Type id |
|------:|--------------|---------|
| 0 | String Format | `utilities.assist.string_format` |
| 1 | Validate | `utilities.assist.validate` |
| 2 | Coalesce | `utilities.assist.coalesce` |
| 3 | Relay | `utilities.assist.relay` |
| 4 | Signal Fork | `utilities.assist.signal_fork` |

Renames / merges at V55:

| Legacy id | New id |
|-----------|--------|
| `…reroute` + `…tag_relay` | `…relay` |
| `…assert` | `…validate` |
| `…signal_merge` | `…coalesce` |

## Scalar passthrough `T`

Relay / Fork / Validate Value / Coalesce declare `ANY` ports with `bindPassthroughType("T")`.
Effective type remaps to the concrete upstream type via `PortTypeResolver`.

```text
BLOCK_POS → Relay → effective BLOCK_POS  (cannot connect to POINT)
POINT     → Relay → effective POINT      (connects to POINT)
```

Unbound `ANY` as a **source** to a typed input is `UNSUPPORTED` in `TypeConversionRegistry`
(stops type washout through unbound relays).

## Coalesce

Prefer-Primary order among **connected** branches; first **non-null** wins (`??`).
Unconnected branches skipped. All connected-null → `Signal=null` / `Source=none` / `Valid=true`.
All branch ports share the same `T` at connect time.

## Validate

- Condition: `OptionalPortDrive.resolveOptionalBoolean` — unconnected→property; connected
  invalid/null→`Valid=false` (no Number/String truthiness).
- Value: passthrough `T`.
- Outputs: Value + Message + Valid only (no Passed, no Fail Hard / throw).

## String Format

- Template connected: String (including `""`) used as-is; null/invalid→fail.
- Template unconnected: property fallback.
- Values connected: list only (null/invalid→fail).
- Values unconnected: Value 0–2.
- Value 0–2 are ANY **sinks** (format to STRING) — not type laundering.
- Formats `VectorData` like `Vector3d`.

## Migration

`migrateV54ToV55`: remaps four legacy type ids; preserves Relay `tag`/`color`; strips
`failHard`; drops wires from Assert `output_passed`.
