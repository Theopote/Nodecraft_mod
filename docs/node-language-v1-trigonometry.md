# Node Language v1 — Trigonometry

Freeze for `math.trigonometry.*` nodes and Expression trig functions.
Shared implementation: `com.nodecraft.nodesystem.math.TrigMathOps`.
Graph schema: **V26** remaps Pi/E and deletes deg↔rad converters (see migration below).

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md) (§3 degrees),
[`node-language-v1-scalar-math.md`](./node-language-v1-scalar-math.md) (finite-result contract).

## Degrees-only graph language

All graph-facing trig **angle inputs and inverse-trig angle outputs** use **degrees** as `DOUBLE`.

| Operation | Input unit | Output unit |
|-----------|------------|-------------|
| Sin / Cos / Tan | degrees | unitless |
| ArcSin / ArcCos / ArcTan / Atan2 | unitless (Y,X for Atan2) | degrees |
| Sinh / Cosh / Tanh | unitless | unitless |

Expression `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, and `atan2` use the **same** rules.
There is no `deg()` / `rad()` in Expression — the graph language is degrees-only.

Constants **Pi** and **E** live under `input.numeric.pi` and `input.numeric.e`, not trigonometry.

## Finite-result contract

Inherits Scalar Math v1:

| State | Numeric outputs | Valid |
|-------|-----------------|-------|
| Success | All `Double.isFinite` | `true` |
| Failure | All `Double.NaN` | `false` |

Invariant: **`Valid=true` ⇒ every numeric output is finite.**

Forbidden: `Infinity` / `-Infinity` with any `Valid` value.

Hyperbolic overflow (`sinh`/`cosh` on large inputs) → `NaN + Valid=false`.

## Tan singularity (exact, no epsilon)

Tangent is invalid only when the angle is **exactly** congruent to 90° mod 180°:

- `Tan(90)`, `Tan(270)`, `Tan(-90)` → invalid
- `Tan(89.999999)` → valid (large finite value)

Do **not** reject angles merely because they are close to 90°.

## Inverse trig domains (fail-closed)

| Function | Valid input domain |
|----------|-------------------|
| ArcSin / ArcCos | `[-1, 1]` inclusive; no clamping |
| ArcTan | any finite value |
| Atan2 | both Y and X finite |

## Node inventory (10)

Sin, Cos, Tan, ArcSin, ArcCos, ArcTan, Atan2, Sinh, Cosh, Tanh.

### Removed (pre-release)

| Old id | Reason |
|--------|--------|
| `math.trigonometry.deg_to_rad` | Hidden unit trap — graph is degrees-only |
| `math.trigonometry.rad_to_deg` | Hidden unit trap — graph is degrees-only |

### Moved to input.numeric

| Old id | New id |
|--------|--------|
| `math.trigonometry.pi` | `input.numeric.pi` |
| `math.trigonometry.e` | `input.numeric.e` |

Port ids unchanged (`output_pi`, `output_e`).

## Graph migration (V25→V26)

| Action | Types |
|--------|-------|
| Remap node type | `math.trigonometry.pi` → `input.numeric.pi` |
| Remap node type | `math.trigonometry.e` → `input.numeric.e` |
| Delete nodes | `math.trigonometry.deg_to_rad`, `math.trigonometry.rad_to_deg` |
| Drop wires | Any connection referencing a removed node |

No value or unit conversion is attempted for deleted deg↔rad nodes (pre-release policy).
