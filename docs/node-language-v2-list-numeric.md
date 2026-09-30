# Node Language v2 — List Numeric

**Status: PASSED / FROZEN** (Graph **V128**; V23 remains historical for collection-wide rules)

Numeric list reduction & scalar consistency remediation for nine `math.list.*` nodes:
shared finite-result reductions, Map Numbers parity with Scalar Math, and overflow fail-closed.

Related: [`node-language-v2-list-flatten-join.md`](./node-language-v2-list-flatten-join.md),
[`node-language-v2-list-collection.md`](./node-language-v2-list-collection.md),
[`node-language-v2-list-core.md`](./node-language-v2-list-core.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Nodes in scope (9)

Sort Numbers, Sort Text, Map Numbers, List Statistics, Sum Numbers, Product Numbers,
Average, Min Number, Max Number.

## Finite-result contract

When `output_valid=true`, every DOUBLE output must be **finite** (not NaN, not Infinity).

| State | Valid | Primary output |
|-------|-------|----------------|
| Success | `true` | finite numeric result |
| Failure | `false` | `NaN` (reductions) or `[]` (Map Numbers / Sort) |

## Shared reduction — `NumericListReduction`

Used by Sum, Product, Average, and List Statistics (sum/average/median):

| Reduction | Algorithm |
|-----------|-----------|
| Sum | Fold with `ScalarMathOps.add`; overflow → invalid |
| Product | Fold with `ScalarMathOps.mul`; overflow → invalid |
| Average | Stable online mean (`mean += (x - mean) / n`); avoids sum overflow when mean is representable |
| Median (even count) | `ScalarMathOps.lerp(low, high, 0.5)`; equal neighbors skip midpoint add |

**Consistency:** `List Statistics.Sum` equals `Sum Numbers.Value` for the same finite input list.

## Map Numbers ↔ Scalar Math parity

All Map Numbers operations delegate to `ScalarMathOps`:

| Operation | Scalar Math equivalent |
|-----------|------------------------|
| DIVIDE | Division — invalid only when divisor `== 0.0` (no epsilon guard) |
| ROUND | Round — `Math.rint` ties-to-even (not `Math.round`) |
| CLAMP | Clamp — auto-normalizes reversed min/max bounds |
| ADD/SUB/MUL/POW/MIN/MAX/ABS/FLOOR/CEIL/SIGN | Same ops |

### CLAMP port semantics

| Port | Undriven | Driven + invalid |
|------|----------|------------------|
| `input_min` | default `0.0` | **fail** |
| `input_max` | default `1.0` | **fail** |

Reversed min/max are normalized by `ScalarMathOps.clamp` (same as prior swap behavior).

## Sort nodes (unchanged in V128)

- **Sort Numbers:** validates finite numeric elements; copies before sort.
- **Sort Text:** requires all `String` elements; uses Java natural (`String` compareTo) order — not locale/pinyin aware.

## Min / Max Number (unchanged in V128)

Simple finite-element reduction; no overflow risk from add/mul.

## Graph migration (V127→V128)

Identity migration — runtime-only numeric semantics; no wire remaps.
