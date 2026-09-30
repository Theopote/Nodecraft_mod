# Node Language v2 — Field Vector Numerical Stability & Strict Sampling

**Status: PASSED / FROZEN** (Graph **V134**)

Hardens vector-field construction and single-point sampling numerical integrity:
stable SDF gradient direction, finite query-point gates, and construction Valid/Error
on vector Constant / From SDF Gradient / Combine. Does **not** change ADD/SUB/
MUL_COMPONENT/CROSS formulas, introduce a hidden Step floor, or rework Batch Sample
Points (already V133 strict 1:1).

Related: [`node-language-v1-fields.md`](./node-language-v1-fields.md),
[`node-language-v2-fields-scalar-foundation.md`](./node-language-v2-fields-scalar-foundation.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Nodes in scope

| Node | Type id |
|------|---------|
| Vector Field From SDF Gradient | `math.fields.vector_from_sdf_gradient` |
| Vector Field Constant | `math.fields.vector_constant` |
| Combine Vector Fields | `math.fields.vector_binary_op` |
| Scalar Field Sample Point | `math.fields.scalar_sample_point` |
| Vector Field Sample Point | `math.fields.vector_sample_point` |

Batch Sample Points remain under V133. Attractor fields deferred.

## Shared helpers (`FieldSampleUtils`)

### `resolveFinitePoint`

Wraps `SpatialValueResolver.resolvePoint`; returns null unless x/y/z are all finite.
Does **not** change the global resolver. Used by Scalar/Vector Sample Point.

### `sampleSdfGradientDirection`

Central difference (no `/2h`; same scale as historical / `SdfGradientPointNode`):

1. Non-finite point or step, or `h <= 0` → NaN dest
2. Per-axis perturbation must be distinguishable (`p±h != p`); else NaN (not zero)
3. Six `sampleDistance` calls; any non-finite → NaN dest
4. Difference components must be finite; else NaN dest
5. `VectorUtils.safeNormalize` (hypot-based):
   - success → unit direction
   - null + finite components + `safeLength <= EPS` → **true flat** → `(0,0,0)`
   - otherwise → NaN dest (overflow / bad normalize)

Sample nodes fail-closed on non-finite vector components → gradient failures surface as
sample `Valid=false`.

## Construction Valid vs sample Valid

| Boundary | Meaning |
|----------|---------|
| Construction Valid (Constant / Gradient / Combine) | Field object successfully created |
| Sample Valid (Sample Point) | Evaluated numeric result is finite |

A valid constructed gradient field may still sample NaN at extreme locations
(unresolvable step, SDF NaN, length overflow).

## From SDF Gradient — connection-aware Step

| Port | Undriven | Driven invalid |
|------|----------|----------------|
| Step | property default (`0.25`) | fail (`invalid_input`) — no silent fallback |
| SDF missing/wrong | — | fail (`invalid_field`) |

Tiny positive Step (e.g. `1e-6`) is honored when driven — no hidden floor.

## Vector Constant / Combine

- Constant X/Y/Z: undriven → `0`; driven + finite Number → use; driven invalid →
  Field=null, Valid=false, `invalid_input`
- Combine: missing A/B → Field=null, Valid=false, `invalid_field`; math unchanged;
  non-finite combine results remain a **sample** boundary issue

## Sample Point — finite query coordinates

NaN/Inf query coordinates → Valid=false even when the field is a finite Constant.

## Graph migration (V133→V134)

Identity migration — no wire remaps.
