# Node Language v2 — Logic

**Status: PASSED / FROZEN** (`GraphFormatVersion.CURRENT` is stamp-only; V122 remains historical residue)

Strict boolean & value-selection contract for `math.logic.*`:
`output_valid` on all six nodes, connection-aware strict Boolean/Integer parsing,
If/Switch generic passthrough `T`, and explicit data-vs-exec boundaries.

Related: [`node-language-v1-logic.md`](./node-language-v1-logic.md),
[`node-language-v2-compare.md`](./node-language-v2-compare.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Valid / Result contract

### Boolean algebra (AND, OR, NOT, XOR)

| Port | Type | Meaning |
|------|------|---------|
| `output_result` | BOOLEAN | Boolean algebra outcome when valid |
| `output_valid` | BOOLEAN | Whether evaluation succeeded |

No `output_error`. No Boolean coercion (`AND(1, true)` is invalid).

| State | Valid | Result |
|-------|-------|--------|
| All inputs driven + exact `Boolean` | `true` | truth table result |
| Undriven or invalid input | `false` | `false` |

Invariant: **`Valid=false` ⇒ `Result=false`.**

**NOT fix:** invalid/undriven input → `Valid=false`, `Result=false` (never `true`).

### If — value selector

Declared ANY + `bindPassthroughType("T")` on True / False / Result. Runtime still passes opaque payloads.

| Port | Type |
|------|------|
| Condition | BOOLEAN |
| True Value / False Value / Result | ANY bound to `T` |
| `output_valid` | BOOLEAN |
| `output_error` | STRING (If only) |

| Condition | Behavior |
|-----------|----------|
| undriven / invalid | `Valid=false`, `Result=null`, `Error` set |
| `true` | select True Value |
| `false` | select False Value |

Connected selected branch delivering null → `Valid=false` (fail closed). Unconnected selected branch may yield `ok(null)`.

**Data vs exec:** If selects values, not execution paths. Use `flow.control.branch` for exec branching.

### Switch — multi-way value selector

Item 0–3 / Default / Result bind passthrough `T`. Index is INTEGER (not `T`). Compare/boolean Logic keep Result+Valid only; Switch has no Error port.

| Port | Type |
|------|------|
| Index | INTEGER |
| Item 0..3 / Default / Result | ANY bound to `T` |
| `output_valid` | BOOLEAN |

| Index | Default | Valid | Result |
|-------|---------|-------|--------|
| undriven | — | `false` | `null` |
| driven + non-`Integer` / null | — | `false` | `null` |
| `0..3` | — | `true` | matching Item (unconnected item may be `null`) |
| other `Integer` (e.g. `5`, `-1`) | **undriven** | `false` | `null` |
| other `Integer` | **driven** (including explicit `null`) | `true` | Default |

No clamp/wrap/modulo. `1.9`, `"1"`, `Long` remain invalid (not truncated).

## Drive detection

A port is **driven** when connected or a value was injected (including explicit `null` via `setInput` / `containsKey`).

Boolean/Integer parsing uses `StrictBooleanUtils` / `StrictIntegerUtils` — no truthiness.

## Preserved from v1

- No Number/String/Object → Boolean coercion
- No float→int truncation on Switch index
- Value ports pass opaque payloads without conversion
- Boolean algebra truth tables unchanged for valid inputs

## Breaking changes from v1

| Scenario | V1 | Current |
|----------|----|---------|
| `NOT(null)` / invalid | `Result=true` | `Valid=false`, `Result=false` |
| `If` invalid condition | selects False Value | `Valid=false`, `Result=null` |
| `Switch` invalid index | Default silently | `Valid=false`, `Result=null` |
| `Switch` index `5`, Default undriven | Default / silent | `Valid=false` |
| `Switch` index `5`, Default driven | Default | Default, **`Valid=true`** |
| `AND` undriven input | treated as false | `Valid=false` |

## Migration

No `GraphFormatVersion` bump. `CURRENT` is stamp-only.

## Out of scope

- Lazy execution / exec-path control via If/Switch (remain PURE)
- NAND / NOR / XNOR
