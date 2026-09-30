# Node Language v2 — Logic

**Status: PASSED / FROZEN** (Graph **V122**; V1 remains historical)

Strict boolean & value-selection contract remediation for `math.logic.*`:
`output_valid` on all six nodes, connection-aware strict Boolean/Integer parsing,
If/Switch invalid-input handling, and explicit data-vs-exec boundaries.

Related: [`node-language-v1-logic.md`](./node-language-v1-logic.md),
[`node-language-v2-compare.md`](./node-language-v2-compare.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Valid / Result contract

### Boolean algebra (AND, OR, NOT, XOR)

| Port | Type | Meaning |
|------|------|---------|
| `output_result` | BOOLEAN | Boolean algebra outcome when valid |
| `output_valid` | BOOLEAN | Whether evaluation succeeded |

| State | Valid | Result |
|-------|-------|--------|
| All inputs driven + exact `Boolean` | `true` | truth table result |
| Undriven or invalid input | `false` | `false` |

Invariant: **`Valid=false` ⇒ `Result=false`.**

**NOT fix:** invalid/undriven input → `Valid=false`, `Result=false` (never `true`).

### If — value selector

| Port | Type |
|------|------|
| Condition | BOOLEAN |
| True Value / False Value / Result | ANY |
| `output_valid` | BOOLEAN |
| `output_error` | STRING |

| Condition | Behavior |
|-----------|----------|
| undriven / invalid | `Valid=false`, `Result=null`, `Error` set |
| `true` | select True Value (opaque ANY passthrough) |
| `false` | select False Value |

Connected selected branch delivering null → `Valid=false` (fail closed).

**Data vs exec:** If selects values, not execution paths. Use `flow.control.branch` for exec branching.

### Switch — multi-way value selector

| Port | Type |
|------|------|
| Index | INTEGER |
| Item 0..3 / Default / Result | ANY |
| `output_valid` | BOOLEAN |

| Index | Valid | Result |
|-------|-------|--------|
| undriven | `false` | `null` |
| driven + non-`Integer` / null | `false` | `null` |
| `0..3` | `true` | matching Item |
| other `Integer` (e.g. `5`, `-1`) | `true` | Default |

No clamp/wrap/modulo. `1.9`, `"1"`, `Long` remain invalid (not truncated).

## Drive detection

A port is **driven** when connected or a value was injected (including explicit `null` via `setInput`).

Boolean/Integer parsing uses `StrictBooleanUtils` / `StrictIntegerUtils` — no truthiness.

## Preserved from v1

- No Number/String/Object → Boolean coercion (`AND(1, true)` → invalid, not true)
- No float→int truncation on Switch index
- ANY value ports pass opaque payloads without conversion
- Boolean algebra truth tables unchanged for valid inputs

## Breaking changes from v1

| Scenario | V1 | V122 |
|----------|----|------|
| `NOT(null)` / invalid | `Result=true` | `Valid=false`, `Result=false` |
| `If` invalid condition | selects False Value | `Valid=false`, `Result=null` |
| `Switch` invalid index | Default silently | `Valid=false`, `Result=null` |
| `Switch` index `5` | Default | Default, **`Valid=true`** |
| `AND` undriven input | treated as false | `Valid=false` |

## Graph migration (V121→V122)

Identity migration — no wire or node remaps. New output ports are additive.

## Out of scope

- Lazy execution / exec-path control via If/Switch (remain PURE)
- Typed generic `Select<T>` outputs (future P2 type system)
- NAND / NOR / XNOR
