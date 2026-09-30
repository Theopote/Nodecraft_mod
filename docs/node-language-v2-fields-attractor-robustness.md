# Node Language v2 — Field Attractor Robustness & Numerical Consistency

**Status: PASSED / FROZEN** (Graph **V135**)

Hardens attractor / vortex / blend vector-field numerics: Volume SURFACE_PULL no longer
falls back to unverified world origin; SDF surface pull shares V134 gradient safety;
Vortex rejects non-finite / zero axis at construction; Blend uses `safeNormalize` and
`maxMagnitude > 0`. Does **not** change INVERSE/LINEAR/GAUSSIAN formulas, Path closest-point
algorithm, or V30 `FieldMath.resolve*` parameter fallbacks for Strength/Radius/Exponent.

Related: [`node-language-v1-fields.md`](./node-language-v1-fields.md),
[`node-language-v2-fields-vector-numerical.md`](./node-language-v2-fields-vector-numerical.md),
[`node-language-v2-fields-scalar-foundation.md`](./node-language-v2-fields-scalar-foundation.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Nodes in scope

| Node | Type id |
|------|---------|
| Point Attractor Field | `math.fields.point_attractor_field` |
| Path Attractor Field | `math.fields.curve_attractor_field` |
| Volume Attractor Field | `math.fields.volume_attractor_field` |
| Vortex Field | `math.fields.vortex_field` |
| Repulsor Field | `math.fields.repulsor_field` (unchanged math) |
| Blend Vector Fields | `math.fields.attractor_blend` |

## Volume — surface / center / invalid

| Surface or geometry resolved | `hasCenter` | Sample result |
|------------------------------|-------------|---------------|
| yes | * | surface/geometry pull |
| no | true | center pull |
| no | false | NaN components → sample Valid=false |

Never invents `(0,0,0)` as a center. Center input uses `resolveFinitePoint`.

Construction Valid/Error: fail when `CENTER_PULL` without center, or no geometry/SDF.

## Shared SDF surface pull

`AttractorFieldUtils.vectorToSdfSurface` calls `FieldSampleUtils.sampleSdfGradientDirection`
(same central-difference + `safeNormalize` path as Vector Field From SDF Gradient).
Returns `true` only for a finite surface-offset vector; NaN / flat / overflow → `false`.

## Distance-squared EPS

`DISTANCE_SQUARED_EPS = 1e-9` is the historical **length-squared** near-zero gate
(`lenSq <= …`). Equivalent distance ≈ `3e-5`. Linear divisor floors use private
`LENGTH_EPS` (same numeric value). Alias `EPS` retained unused. Numeric behavior
unchanged this batch.

## Vortex construction

Origin must be finite (`resolveFinitePoint`). Axis must be finite and non-zero
(`VectorUtils.isFinite` + `isNonZero` / `safeNormalize`). Else Field=null, Valid=false,
`invalid_input`.

## Blend — weights vs normalize vs max magnitude

| Rule | Behavior |
|------|----------|
| Weight participation | Exact `weight != 0.0` (V30) |
| Normalize | `VectorUtils.safeNormalize`; failure → zero (no bogus unit) |
| Max Magnitude | Enabled when `limit > 0` (including values in `(0, EPS]`) |

## GAUSSIAN Exponent UI

Property panel hides `exponent` when Falloff is GAUSSIAN (math unchanged — GAUSSIAN
still ignores Exponent).

## Graph migration (V134→V135)

Identity migration — no wire remaps.
