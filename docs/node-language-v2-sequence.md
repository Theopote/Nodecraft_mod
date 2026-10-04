# Node Language v2 — Sequence

**Status: PASSED / FROZEN** (`GraphFormatVersion.CURRENT` is stamp-only; V125 remains historical residue)

Strict inputs & explicit termination for `math.sequence.*`:
connection-aware DOUBLE/INTEGER resolution, `output_valid` / `output_error` on all three nodes,
and fail-closed on non-finite inputs, Series overflow, and Range abnormal termination.

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

| Port state | Behavior |
|------------|----------|
| Undriven finite property | use property / literal default |
| Undriven non-finite property | **fail** (`invalid_input`) |
| Driven + valid exact type | use value |
| Driven + wrong type / null / non-finite DOUBLE | **fail** (`invalid_input`) |
| Driven Count `> MAX_LIST_ELEMENTS` | **fail** (no silent truncate) |
| Undriven Count property | **clamp** to `MAX_LIST_ELEMENTS` (UI safety) |

`DataSeries` Start/Step setters reject NaN/Inf (keep previous). Node state restore requires exact `Double` / `Integer`.

## Number Sequence (`math.sequence.range`)

Undriven defaults: Start=`0`, End=`10`, Step=`1`. Exact step, no endpoint swap, no forced End, `value = start + i * step`.

| Termination | Valid | Numbers | Error |
|-------------|-------|---------|-------|
| Normal (reached/passed End) | `true` | generated list | `""` |
| Direction mismatch / `step==0` | `true` | `[]` | `""` |
| Entry non-finite Start/End/Step | `false` | `[]` | `non_finite_value` |
| Mid-loop non-finite | `false` | `[]` | `non_finite_value` |
| Float precision stall before End | `false` | `[]` | `float_precision_stall` |
| Hit `MAX_LIST_ELEMENTS` before End | `false` | `[]` | `max_elements_exceeded` |

## Number Series (`math.sequence.series`)

| Termination | Valid | Series | Error |
|-------------|-------|--------|-------|
| All Count elements finite | `true` | full list | `""` |
| Count=0 | `true` | `[]` | `""` |
| Entry non-finite Start/Step | `false` | `[]` | `non_finite_value` |
| Mid-loop non-finite before Count complete | `false` | `[]` | `non_finite_value` |
| Driven Count over hard cap | `false` | `[]` | `invalid_input` |

`SequenceOps.series` still clamps huge Count as a non-graph safety net; graph nodes fail closed first.

## Repeat Item (`math.sequence.repeat`)

- Count: same graph vs property rule as Series
- Item: when driven, value must be non-null; when undriven, `null` item is allowed
- Lists are one element, never tiled
- **Reference semantics:** each list slot holds the **same object reference** (no deep copy). Canonical graph values (e.g. `VectorData`) are immutable, so this is safe. Do not emit raw mutable JOML as graph values.

## Unchanged from v1

1. Exact step — no hidden epsilon.
2. Finite list elements when `Valid=true`.
3. Hard cap `GenerationLimits.MAX_LIST_ELEMENTS`.
4. Sum removed from Number Series — use `math.list.sum_numbers`.

## Migration

No `GraphFormatVersion` bump. `CURRENT` is stamp-only.
