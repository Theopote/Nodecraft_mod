# Node Language v2 — Field Scalar Foundation & Strict Sampling

**Status: PASSED / FROZEN** (Graph **V133**; V30 remains historical for Field v1 foundations)

Hardens continuous→discrete Field sampling integrity for scalar foundation nodes and vector
batch sample twin. Does **not** change `RandomOps.valueNoise3`, `ScalarMathOps`, or global
`SpatialValueResolver.resolvePointList` filtering.

Related: [`node-language-v1-fields.md`](./node-language-v1-fields.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Nodes in scope

| Node | Type id |
|------|---------|
| Scalar Field Constant | `math.fields.scalar_constant` |
| Scalar Field Noise | `math.fields.scalar_noise` |
| Combine Scalar Fields | `math.fields.scalar_binary_op` |
| Scalar Field Sample Points | `math.fields.scalar_sample_points` |
| Vector Field Sample Points | `math.fields.vector_sample_points` |

From SDF / single-point sample ports unchanged this batch (except shared utils).

## Strict 1:1 batch sampling

`FieldSampleUtils.resolvePointListStrict` (field-local; not global resolver):

| Input | Result |
|-------|--------|
| null / not Collection | `invalid_input` |
| Empty collection | ok `[]` → Count=0, Valid=true |
| Every element finite point | ok list, size = input size |
| Any bad / non-finite element | `invalid_points` (no silent drop) |

Success invariant: **`Count == input point count`** and values/vectors align by index.

Budget: `GenerationLimits.MAX_FIELD_SAMPLE_POINTS` (= `MAX_LIST_ELEMENTS`). Exceed →
`output_budget_exceeded` before evaluation.

Non-finite field sample mid-batch still fail-closed (V30 rule retained).

## Construction Valid vs sample Valid

| Boundary | Meaning |
|----------|---------|
| Construction Valid (Constant / Noise / Combine) | Field object successfully created |
| Sample Valid (Sample Point / Points) | Evaluated numeric result(s) are finite |

A valid constructed field may still sample NaN at some locations (e.g. DIV by zero, noise overflow).

## Noise — connection-aware params

| Port | Undriven | Driven invalid |
|------|----------|----------------|
| Seed | `0` | fail (`invalid_input`) |
| Scale / Offset / Amplitude | property defaults | fail (no silent NaN→default) |

Scale may be `0` or negative (unchanged). Kernel still `valueNoise3((p+offset)*scale, seed) * amplitude`.

## Constant / Combine

- Constant: driven non-finite Value → Field=null, Valid=false, `invalid_input`
- Combine: missing A/B → Field=null, Valid=false, `invalid_field`; math still `ScalarMathOps`

## Graph migration (V132→V133)

Identity migration — no wire remaps.
