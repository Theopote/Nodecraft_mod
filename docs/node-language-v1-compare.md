# Node Language v1 — Compare

Freeze for `math.compare.*` comparison nodes.
Shared implementation: `com.nodecraft.nodesystem.nodes.math.compare.CompareUtils`.
Graph schema: **V27** deletes composite Compare node (see migration below).

Related: [`node-language-v1-scalar-math.md`](./node-language-v1-scalar-math.md) (no magic epsilon),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md) (DOUBLE language).

## Core rule

**Comparison v1: basic comparisons are strict, deterministic, and coercion-free. Approximate comparison must be explicit (future node).**

No hidden tolerance. No String→Number parsing. No Object→String fallback.

## Numeric ordering (`<` / `<=` / `>` / `>=`)

Ports: `DOUBLE` A, `DOUBLE` B → `BOOLEAN` Result.

| Rule | Behavior |
|------|----------|
| Comparison | Exact IEEE `double` ordering (no epsilon) |
| Non-finite input | Any non-finite operand → **`false`** |
| Examples | `0 < 5e-11` true; `0 == 5e-11` false (use Equals for equality) |

## Generic equality (`==` / `!=`)

Ports: `ANY` A, `ANY` B → `BOOLEAN` Result.

ANY means the node **accepts** many types — not that it **converts** between them.

| Case | Result |
|------|--------|
| `null`, `null` | true |
| one null | false |
| both `Number` | exact numeric equality (`1 == 1.0` true) |
| both `String` | exact string equality |
| both `Boolean` | exact boolean equality |
| same runtime class | `Objects.equals` (POINT, VECTOR, TREE_PATH, …) |
| unrelated types | false |

Forbidden:

- `"1" == 1` → false
- `"true" == true` → false
- String parsing, toString coercion

### Numeric equality details

When both operands are `Number` and finite:

- `+0.0 == -0.0` → true (`a == b`)
- `NaN == NaN` → false
- `NaN == x`, `Infinity == Infinity` → false (fail-closed)

## Node inventory (6)

| Node | Type id | Input types |
|------|---------|-------------|
| Equals | `math.compare.equals` | ANY |
| Not Equals | `math.compare.not_equals` | ANY |
| Less Than | `math.compare.less_than` | DOUBLE |
| Less Than or Equal | `math.compare.less_than_or_equal` | DOUBLE |
| Greater Than | `math.compare.greater_than` | DOUBLE |
| Greater Than or Equal | `math.compare.greater_than_or_equal` | DOUBLE |

### Removed

| Old id | Reason |
|--------|--------|
| `math.compare.compare` | Mode-driven composite; redundant with atomic nodes |

## Future: Approximately Equal (P2)

Not in v1. When added, must be explicit:

```
A, B, Tolerance (DOUBLE) → Result (BOOLEAN)
abs(A - B) <= abs(Tolerance)
```

Do not add hidden tolerance to Equals.

## Graph migration (V26→V27)

| Action | Target |
|--------|--------|
| Delete nodes | `math.compare.compare` |
| Drop wires | Any connection referencing removed node |

No remap (Mode/Result/Equal/Greater/Less cannot map to one atomic node).

## Compare vs Logic

| Category | Role |
|----------|------|
| `math.compare` | value → boolean |
| `math.logic` | boolean → boolean / value selection |
