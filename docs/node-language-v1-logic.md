# Node Language v1 — Logic

Freeze for `math.logic.*` boolean algebra and value-selection nodes.
Shared implementation: `com.nodecraft.nodesystem.nodes.math.logic.LogicUtils`.

Related: [`node-language-v1-compare.md`](./node-language-v1-compare.md) (no coercion),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rule

**Logic v1: port types are the sole semantics source. No truthiness. No Number/String/Object coercion.**

| Declared port | Runtime accepts |
|---------------|-----------------|
| `BOOLEAN` | `Boolean` only; null / other → treated as false |
| `INTEGER` (Switch Index) | `Integer` only; null / other → Default branch |
| `ANY` (value ports) | any object as opaque payload — not converted |

This batch does **not** bump graph format (no node/port/schema changes).

## Boolean algebra (4)

AND, OR, NOT, XOR — all ports `BOOLEAN`, zero `ANY`.

| Node | Rule |
|------|------|
| AND | both inputs are `Boolean.TRUE` |
| OR | either input is `Boolean.TRUE` |
| NOT | logical negation of boolean input |
| XOR | exactly one input is `Boolean.TRUE` |

Missing / non-Boolean inputs behave as false (fail-closed).

## If — value selector

| Port | Type |
|------|------|
| Condition | `BOOLEAN` |
| True Value / False Value / Result | `ANY` |

`Condition == Boolean.TRUE` → True Value; otherwise → False Value.

**Boundary:** `math.logic.if` is PURE value selection. Execution-path branching stays in `flow.control.branch`.

## Switch — multi-way value selector

| Port | Type |
|------|------|
| Index | `INTEGER` |
| Item 0..3 / Default / Result | `ANY` |

- `Integer` in `0..3` → corresponding Item
- Out-of-range integer or non-`Integer` index → Default
- No clamp / wrap / modulo properties

## Node inventory (6)

If, Switch, AND, OR, NOT, XOR — unchanged count and type ids.

## Out of scope

- Scalar generic `Value<T>` / typed If/Switch outputs (future)
- NAND / NOR / XNOR
- Graph format migration
