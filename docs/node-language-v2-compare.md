# Node Language v2 — Compare

**Status: PASSED / FROZEN** (`GraphFormatVersion.CURRENT` is stamp-only; V121 remains historical residue)

Exact numeric equality & invalid-input contract for `math.compare.*`:
`output_valid` on all six nodes, exact integer comparison without double rounding,
connection-aware undriven vs explicit null, and strict `Double` ordering inputs.

Related: [`node-language-v1-compare.md`](./node-language-v1-compare.md),
[`node-language-v2-scalar-math.md`](./node-language-v2-scalar-math.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Valid / Result contract

All six compare nodes expose:

| Port | Type | Meaning |
|------|------|---------|
| `output_result` | BOOLEAN | Comparison outcome when valid |
| `output_valid` | BOOLEAN | Whether comparison succeeded |

No `output_error` on compare nodes.

| State | Valid | Result |
|-------|-------|--------|
| Successful comparison | `true` | `true` / `false` |
| Invalid / undriven / non-finite ordering | `false` | `false` |

Invariant: **`Valid=false` ⇒ `Result=false`.**

Downstream control nodes (If, Switch, Filter) should gate on `Valid` when strict
fail-closed behavior is required.

## Drive detection

A port is **driven** when:

- it is connected, or
- a value was injected (including explicit `null` via `setInput`)

| Case | Valid | Result |
|------|-------|--------|
| both undriven | `false` | `false` |
| both driven, explicit `null`/`null` | `true` | `true` |
| one driven, one undriven | `true` | one-null rules apply |
| ordering: either undriven | `false` | `false` |

## Numeric equality (`==` / `!=`)

Equals / Not Equals stay unconstrained **ANY** (runtime opaque; no passthrough `T`).
Uses `NumericComparison` — no blanket `Number.doubleValue()`. No hidden epsilon.

| Left / Right | Rule |
|--------------|------|
| `Integer` + `Integer` | exact `int` |
| `Long` + `Long` | exact `long` |
| integral + integral | promote to `long`, exact |
| `Double` + `Double` (finite) | exact IEEE `==` |
| integral + floating (finite) | mathematical value; reject if integral loses precision in `double` |
| either non-finite (Equals) | `Valid=true`, `Result=false` |
| both non-finite (Not Equals) | `Valid=false`, `Result=false` |

Preserved from v1:

- `1 == 1.0` → true
- `+0.0 == -0.0` → true
- `"1" == 1` → false (no coercion)

Example fix: `9007199254740992L != 9007199254740993L` even though both may round to the same `double`.

## Numeric ordering (`<` / `<=` / `>` / `>=`)

Ports remain `DOUBLE` A, `DOUBLE` B.

Runtime requires exact finite `Double` instances — no `Integer`/`Long` coercion at execution.
Exact IEEE ordering (no epsilon). Non-finite or wrong runtime type → `Valid=false`, `Result=false`.

## Node inventory (unchanged)

| Node | Type id | Input types |
|------|---------|-------------|
| Equals | `math.compare.equals` | ANY |
| Not Equals | `math.compare.not_equals` | ANY |
| Less Than | `math.compare.less_than` | DOUBLE |
| Less Than or Equal | `math.compare.less_than_or_equal` | DOUBLE |
| Greater Than | `math.compare.greater_than` | DOUBLE |
| Greater Than or Equal | `math.compare.greater_than_or_equal` | DOUBLE |

## Breaking changes from v1

| Scenario | v1 | v2 |
|----------|----|----|
| `NotEquals(NaN, NaN)` | `Result=true` | `Valid=false`, `Result=false` |
| Both inputs undriven | `Result=true` (null-null) | `Valid=false`, `Result=false` |
| Ordering with `Integer` injected | may compare via `doubleValue()` | `Valid=false` |

## Migration

No `GraphFormatVersion` bump. `CURRENT` is stamp-only.

## Future: Approximately Equal

Not in v2. When added, must be explicit with a Tolerance port. Do not add hidden tolerance to Equals.
