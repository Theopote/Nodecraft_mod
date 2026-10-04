# Node Language v2 — Scalar Math

**Status: PASSED / FROZEN** (`GraphFormatVersion.CURRENT` is stamp-only; V120 remains historical residue)

Strict inputs & numeric boundaries for `math.scalar_math.*`: exact finite `Double` on DOUBLE ports,
connection-aware Expression variables and Domain/Clamp, Integer Divide overflow guard, overflow-safe
smoothstep/remap normalization, and stable FMA lerp.

Related: [`node-language-v1-scalar-math.md`](./node-language-v1-scalar-math.md),
[`node-language-v1-numeric-domain.md`](./node-language-v1-numeric-domain.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Finite-result contract (unchanged)

| State | Numeric outputs | Valid |
|-------|-----------------|-------|
| Success | All `Double.isFinite` | `true` |
| Failure | All `Double.NaN` (Integer Divide: `0`) | `false` |

Invariant: **`Valid=true` ⇒ every numeric output is finite.** `ScalarResult.ok` remains a finite safety net.

## DOUBLE ports

Runtime type is exact finite `java.lang.Double` via `StrictDoubleUtils.requireExactFiniteDouble`.
`Integer` / other `Number` are not coerced (`1 + 2.0` → `Valid=false`). INTEGER ports (Integer Divide)
remain exact `Integer`.

## Optional drive (fail-closed)

A port is driven when `isConnected || isInputPresent`. Scalar math wraps this in `ScalarMathPorts`
(do not change global `OptionalPortDrive.resolveOptionalBoolean`).

| Port state | Behavior |
|------------|----------|
| Undriven optional | node property / canonical default |
| Driven exact type | use wire / injected value |
| Driven wrong type / non-finite | `Valid=false` (no property fallback) |

## Integer Divide

| Rule | Behavior |
|------|----------|
| Input type | Exact `Integer` only (no `Number.intValue()` truncation) |
| Connected + invalid | `Valid=false`, quotient/remainder `0` |
| Unconnected + null | `Valid=false` |
| `MIN_VALUE / -1` | `Valid=false` (quotient not representable in `int`) |
| Divisor `0` | `Valid=false` |

Uses `Math.floorDiv` / `Math.floorMod`.

## Smoothstep / Remap normalization

`ScalarMathOps.normalizeInterval` is shared by Smoothstep and Remap:

1. Fast path when `edge1 - edge0` is finite and non-zero.
2. Overflow path when span is non-finite: endpoint compare for `t ∈ {0,1}`; interior uses midpoint normalization.
3. Non-finite `t` or coincident edges → invalid.

Remap maps `t` onto the target with FMA `lerp` (not `target.lerp` / `source.delta`). Clamp-to-target uses
`target.lower()` / `target.upper()`. Extreme source `[-1e308, +1e308]` stays a finite-endpoint span
(`NumericDomainResolver.finiteEndpoints`); `NumericRangeData.canonical` is not used for overflow spans.

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
| Referenced + driven | port | exact finite `Double` required |
| Referenced + undriven | port | default `0.0` |

## Connection-aware Domain / Value

`NumericDomainResolver.resolveOptionalDomain(node, portId, defaultStart, defaultEnd)`:

| Port state | Behavior |
|------------|----------|
| Undriven | node default domain |
| Driven + finite endpoints | keep span (including overflow-directed) |
| Driven + invalid | `null` → node `Valid=false` |

Applied to **Clamp**, **Remap**, **Graph Mapper** domain ports.

Graph Mapper **Value**: undriven → `0.0`; driven → exact finite `Double`.

Remap **Clamp**: undriven → node property; driven non-`Boolean` → `invalid_input`.

Logarithm **Base**: undriven → `Math.E`; driven invalid → `invalid_input`.

Random Number(s) still use legacy `resolveDomain(Object, …)` (out of this freeze).

## Complex-node Error codes

Power, Logarithm, Smoothstep, Remap, Graph Mapper expose `output_error` (short codes only):

| Code | Typical cause |
|------|----------------|
| `invalid_input` | wrong runtime type / non-finite required DOUBLE / invalid Clamp |
| `invalid_domain` | driven domain not finite `NumericRangeData`, or log domain fail |
| `degenerate_domain` | coincident source/edges |
| `non_finite_result` | math produced NaN/Inf (e.g. Power) |

Binary/unary arithmetic nodes keep Valid + numeric ports without Error.

## Graph Mapper exponent correction

`safeExponent()` for Power / Ease curves:

- Negative exponents → `abs(exponent)`
- Magnitudes below `0.001` → clamp to `0.001`

Documented on the `Default Exponent` node property.

## Preserved v1 rules

- Division / Modulus: invalid only when `b == 0.0d` exactly (no epsilon)
- Round: ties-to-even (`Math.rint`); Frac: `x - floor(x)` (e.g. `frac(-1.2) = 0.8`)
- Remap: directed domains preserved (reversed target allowed)
- Graph Mapper: advanced curve node (not decomposed into basic ops)
- Clamp/Remap node state: `defaultStart`/`defaultEnd` (and Remap source/target + clamp). Deprecated InMin/InMax/OutMin/OutMax and Clamp Min/Max aliases are removed.

## Migration

No `GraphFormatVersion` bump. `CURRENT` is stamp-only (V120–V122 remain historical names in tests/docs).

## Contract

- `ScalarMathLanguageV2ContractTest` — Integer Divide, smoothstep/remap extremes, Expression/Domain/Clamp strict ports, finite sweep, stable lerp, Integer-on-DOUBLE reject.
- `ScalarMathLanguageContractTest` — historical fence retained.
- `ScalarMathOpsTest` — unit cases for smoothstep span and FMA lerp.
- `ScalarMathNodeTest` — exact-zero divide, Integer reject, Remap clamp / degenerate / extreme interval.
