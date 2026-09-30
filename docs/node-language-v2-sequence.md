# Node Language v2 — Sequence

**Status: PASSED / FROZEN** (Graph **V125**; V28 remains historical v1)

Strict inputs & explicit termination remediation for `math.sequence.*`:
connection-aware DOUBLE/INTEGER resolution, `output_valid` / `output_error` on all three nodes,
and transactional fail-closed on Series overflow and Range abnormal termination.

Related: [`node-language-v1-sequence.md`](./node-language-v1-sequence.md),
[`node-language-v2-scalar-math.md`](./node-language-v2-scalar-math.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Valid / Error contract

All three sequence nodes expose:

| Port | Type | Meaning |
|------|------|---------|
| Primary output | varies | Generated list when valid |
| `output_valid` | BOOLEAN | Whether generation succeeded |
| `output_error` | STRING | Error message when invalid |

| State | Valid | Primary output |
|-------|-------|----------------|
| Success | `true` | generated list (may be empty when rules allow) |
| Failure | `false` | `[]` |

Invariant: **`Valid=false` ⇒ empty list** (never a silent partial or default-driven result).

Repeat Item also sets `output_length=0` on failure.

## Connection-aware inputs

Uses `OptionalPortDrive` / `RandomInputResolver` (same frozen rule as V120/V123):

| Port state | Behavior |
|------------|----------|
| Undriven | property / literal default |
| Connected + valid exact type | use value |
| Connected + wrong type | **fail** (`invalid_input`) |
| Connected + null | **fail** |
| Non-finite DOUBLE (Sequence/Series) | **fail** at resolution |

## Number Sequence (`math.sequence.range`)

Undriven defaults: Start=`0`, End=`10`, Step=`1`.

Core rules unchanged: exact step, no endpoint swap, no step auto-fix.

| Termination | Valid | Numbers | Error |
|-------------|-------|---------|-------|
| Normal (reached/passed End) | `true` | generated list | `""` |
| Direction mismatch / `step==0` / entry non-finite | `true` | `[]` | `""` |
| Mid-loop non-finite | `false` | `[]` | `non_finite_value` |
| Float precision stall before End | `false` | `[]` | `float_precision_stall` |
| Hit `MAX_LIST_ELEMENTS` before End | `false` | `[]` | `max_elements_exceeded` |

## Number Series (`math.sequence.series`)

Count: exact `Integer` when driven; property default when undriven (clamped).

| Termination | Valid | Series | Error |
|-------------|-------|--------|-------|
| All Count elements finite | `true` | full list | `""` |
| Count=0 after clamp | `true` | `[]` | `""` |
| Entry non-finite Start/Step | `true` | `[]` | `""` |
| Mid-loop non-finite before Count complete | `false` | `[]` | `non_finite_value` |

## Repeat Item (`math.sequence.repeat`)

- Count: connection-aware exact `Integer` (same as Series)
- Item: when driven, value must be non-null; when undriven, `null` item is allowed
- **Reference semantics:** each list slot holds the **same object reference** (no deep copy).
  Downstream must treat repeated mutable payloads (e.g. `Vector3d`) as read-only or copy explicitly.

## Unchanged from v1

1. Exact step — no hidden epsilon.
2. Finite list elements when `Valid=true`.
3. Hard cap `GenerationLimits.MAX_LIST_ELEMENTS`.
4. Sum removed from Number Series — use `math.list.sum_numbers`.

## Graph migration (V124→V125)

Identity migration — runtime-only input/termination changes; no wire remaps.
