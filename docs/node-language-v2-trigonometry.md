# Node Language v2 — Trigonometry

**Status: PASSED / FROZEN** (Graph **V124**; V26 remains historical v1)

Strict DOUBLE inputs & numerical boundary remediation for `math.trigonometry.*`:
exact finite `Double` at runtime, degree period normalization in `TrigMathOps`,
expanded boundary tests, and node↔Expression parity on shared ops.

Related: [`node-language-v1-trigonometry.md`](./node-language-v1-trigonometry.md),
[`node-language-v2-scalar-math.md`](./node-language-v2-scalar-math.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Degrees-only graph language (unchanged)

All graph-facing trig **angle inputs and inverse-trig angle outputs** use **degrees** as `DOUBLE`.

| Operation | Input unit | Output unit |
|-----------|------------|-------------|
| Sin / Cos / Tan | degrees | unitless |
| ArcSin / ArcCos / ArcTan / Atan2 | unitless (Y,X for Atan2) | degrees |
| Sinh / Cosh / Tanh | unitless | unitless |

Expression `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, and `atan2` call the same `TrigMathOps`.

## Strict DOUBLE runtime contract

All ten nodes resolve DOUBLE inputs via `StrictDoubleUtils.requireExactFiniteDouble`:

| Input at runtime | Valid | Numeric output |
|------------------|-------|----------------|
| exact finite `Double` | per function | finite when valid |
| missing / null | `false` | `NaN` |
| `Integer`, `Long`, `Float`, etc. | `false` | `NaN` |
| non-finite `Double` | `false` | `NaN` |

Wire connectability may still accept `Number` at the port layer; runtime resolution is strict.
Use explicit type conversion nodes for INTEGER→DOUBLE — do not rely on implicit coercion in nodes.

## Finite-result contract (unchanged)

| State | Numeric outputs | Valid |
|-------|-----------------|-------|
| Success | All `Double.isFinite` | `true` |
| Failure | All `Double.NaN` | `false` |

Hyperbolic overflow (`sinh`/`cosh` on large inputs) → `NaN + Valid=false`.
`tanh` on large finite inputs remains valid (saturates toward ±1).

## Tan singularity (exact, no epsilon)

Tangent is invalid only when the angle is **exactly** congruent to 90° mod 180°:

- `Tan(90)`, `Tan(270)`, `Tan(-90)`, `Tan(450)`, `Tan(-270)` → invalid
- `Tan(89.999999)` → valid (large finite value)

Do **not** reject angles merely because they are close to 90°.

## Large-angle normalization

Before `Math.toRadians`, angles are period-reduced:

| Function | Reduction |
|----------|-----------|
| Sin / Cos | `angleDeg % 360` |
| Tan | `angleDeg % 180` (after singularity check on full angle) |

This improves stability for moderately large finite angles (e.g. `Sin(360_000_090°) ≈ 1`).
It **cannot** recover phase detail already lost when the input `double` cannot distinguish
adjacent degrees (e.g. `1e16` vs `1e16 + 1`). No arbitrary angle cap is applied.

## Inverse trig domains (fail-closed, unchanged)

| Function | Valid input domain |
|----------|-------------------|
| ArcSin / ArcCos | `[-1, 1]` inclusive; no clamping |
| ArcTan | any finite value |
| Atan2 | both Y and X finite |

## Atan2(0, 0) semantics

`Atan2` follows Java `Math.atan2` (mathematical function semantics, not zero-vector geometry):

| Input | Result |
|-------|--------|
| Y=+0, X=+0 | Valid, 0° |
| Y=-0, X=-0 | Valid, per Java signed-zero rules |

For strict geometric direction of a zero vector, validate upstream (e.g. zero-vector checks).

## Node inventory (10)

Sin, Cos, Tan, ArcSin, ArcCos, ArcTan, Atan2, Sinh, Cosh, Tanh.

Implementation: `TrigScalarNode` base + shared `TrigMathOps`.

## Graph migration (V123→V124)

Identity migration — runtime-only strict input and normalization changes; no wire remaps.
