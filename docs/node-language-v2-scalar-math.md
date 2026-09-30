# Node Language v2 — Scalar Math

**Status: PASSED / FROZEN** (Graph **V120**; V25 remains historical v1)

Strict inputs & numeric boundaries remediation for `math.scalar_math.*`: connection-aware
Expression variables, Domain/Value parsing, Integer Divide overflow guard, safe smoothstep
normalization, and stable FMA lerp.

Related: [`node-language-v1-scalar-math.md`](./node-language-v1-scalar-math.md),
[`node-language-v1-numeric-domain.md`](./node-language-v1-numeric-domain.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Finite-result contract (unchanged)

| State | Numeric outputs | Valid |
|-------|-----------------|-------|
| Success | All `Double.isFinite` | `true` |
| Failure | All `Double.NaN` (Integer Divide: `0`) | `false` |

Invariant: **`Valid=true` ⇒ every numeric output is finite.**

## Integer Divide

| Rule | Behavior |
|------|----------|
| Input type | Exact `Integer` only (no `Number.intValue()` truncation) |
| Connected + invalid | `Valid=false`, quotient/remainder `0` |
| Unconnected + null | `Valid=false` |
| `MIN_VALUE / -1` | `Valid=false` (quotient not representable in `int`) |
| Divisor `0` | `Valid=false` |

## Smoothstep normalization

`ScalarMathOps.normalizeInterval` shared by `smoothstep` and `smoothstepT`:

1. Fast path when `edge1 - edge0` is finite and non-zero.
2. Overflow path when span is non-finite: endpoint compare for `t ∈ {0,1}`; interior uses midpoint normalization.
3. Non-finite `t` → invalid.

## Stable Lerp

`lerp(a, b, t)` uses FMA: `a*(1-t) + b*t` via `Math.fma(t, b, Math.fma(-t, a, a))`.

- Avoids `b - a` overflow for large opposite endpoints.
- Still fail-closed when the **result** is non-finite.
- **T outside [0,1] extrapolates** (unchanged from v1).

## Connection-aware Expression variables

Variables: `A`, `B`, `C`, `X`, `Y`, `Z`, `T`.

| Variable in expression | Port state | Behavior |
|------------------------|------------|----------|
| Not referenced | any | default `0.0` |
| Referenced + connected | port | exact finite `Double` required |
| Referenced + unconnected + null | port | default `0.0` |
| Referenced + unconnected + injected | port | exact finite `Double` required |

Uses `OptionalPortDrive` / `StrictDoubleUtils` (no math-node-specific drive layer).

## Connection-aware Domain / Value

`NumericDomainResolver.resolveOptionalDomain(node, portId, defaultStart, defaultEnd)`:

| Port state | Behavior |
|------------|----------|
| Not connected | node default domain |
| Connected + valid `NumericRangeData` | canonical copy |
| Connected + invalid | `null` → node `Valid=false` |

Applied to **Clamp**, **Remap**, **Graph Mapper** domain ports.

Graph Mapper **Value** uses `OptionalPortDrive.resolveOptionalStrictDouble` (undriven → `0.0`).

Random Number(s) still use legacy `resolveDomain(Object, …)` (out of V120 scope).

## Graph Mapper exponent correction

`safeExponent()` for Power / Ease curves:

- Negative exponents → `abs(exponent)`
- Magnitudes below `0.001` → clamp to `0.001`

Documented on the `Default Exponent` node property.

## Preserved v1 rules

- Division / Modulus: invalid only when `b == 0.0d` exactly
- Round: ties-to-even (`Math.rint`)
- Remap: directed domains preserved (reversed target allowed)
- Graph Mapper: advanced curve node (not decomposed into basic ops)

## Migration

Graph **V119→V120** is a no-op (Valid semantics; no wire remaps).

## Contract

- `ScalarMathLanguageV2ContractTest` — V120 fence, Integer Divide, smoothstep extremes, Expression/Domain strict ports, finite sweep, stable lerp.
- `ScalarMathLanguageContractTest` — V25 historical fence retained.
- `ScalarMathOpsTest` — unit cases for smoothstep span and FMA lerp.
